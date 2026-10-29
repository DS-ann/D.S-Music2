package com.codetrio.spatialflow.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log

import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.innertube.NewPipeStreamExtractor
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.service.AudioPlaybackService
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object SongDownloader {
    private const val TAG = "SongDownloader"
    private const val CHANNEL_ID = "spatialflow_downloads"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()

    fun cleanTitle(title: String): String {
        var clean = title
        val regexes = listOf(
            "(?i)\\[official music video\\]",
            "(?i)\\(official music video\\)",
            "(?i)\\[official video\\]",
            "(?i)\\(official video\\)",
            "(?i)\\[lyrics\\]",
            "(?i)\\(lyrics\\)",
            "(?i)\\[lyric video\\]",
            "(?i)\\(lyric video\\)",
            "(?i)\\[official audio\\]",
            "(?i)\\(official audio\\)",
            "(?i)official music video",
            "(?i)official video",
            "(?i)lyric video",
            "(?i)\\[video\\]",
            "(?i)\\(video\\)",
            "(?i)\\[official release\\]",
            "(?i)\\(official release\\)"
        )
        for (regex in regexes) {
            clean = clean.replace(Regex(regex), "")
        }
        // Remove trailing dashes, slashes, and spaces
        return clean.trim().replace(Regex("\\s+"), " ").removeSuffix("-").removeSuffix("/").trim()
    }

    fun cleanArtist(artist: String): String {
        return artist.replace(Regex("(?i)\\s*-\\s*Topic"), "").trim().replace(Regex("\\s+"), " ")
    }

    fun cleanAlbum(title: String): String {
        var clean = cleanTitle(title)
        val albumRegexes = listOf(
            "(?i)\\s*\\((slowed|reverb|speed up|sped up|remix|slowed\\s*\\+\\s*reverb|slowed\\s*and\\s*reverb)\\)",
            "(?i)\\s*\\[(slowed|reverb|speed up|sped up|remix|slowed\\s*\\+\\s*reverb|slowed\\s*and\\s*reverb)\\]"
        )
        for (regex in albumRegexes) {
            clean = clean.replace(Regex(regex), "")
        }
        return clean.trim().replace(Regex("\\s+"), " ")
    }

    private class DownloadTask {
        @Volatile var job: Job? = null
        @Volatile var connection: HttpURLConnection? = null
        @Volatile var call: okhttp3.Call? = null
        @Volatile var outputFile: File? = null
        @Volatile var cancelled: Boolean = false
    }

    private data class CachedStreamCandidate(
        val cacheKey: String,
        val spans: List<CacheSpan>,
        val contentLength: Long,
        val mimeType: String?,
        val cachedBytes: Long,
        val isComplete: Boolean
    )

    private val activeDownloads = ConcurrentHashMap<String, DownloadTask>()

    fun isDownloading(videoId: String?): Boolean =
        !videoId.isNullOrBlank() && activeDownloads.containsKey(videoId)

    /**
     * Tapping Download twice for the same track toggles it off. We keep the
     * connection handle so cancellation also interrupts a blocked HTTP read.
     */
    fun downloadSong(context: Context, song: SongItem) {
        val videoId = song.videoId
        if (videoId.isNullOrBlank()) {
            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Local songs are already offline")
            return
        }

        if (activeDownloads.containsKey(videoId)) {
            cancelDownload(context, song)
            return
        }

        val task = DownloadTask()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            performDownload(context.applicationContext, song, videoId, task)
        }
        task.job = job

        val alreadyActive = activeDownloads.putIfAbsent(videoId, task)
        if (alreadyActive != null) {
            job.cancel()
            // The second tap is a cancellation request for the active task.
            cancelDownload(context, song)
            return
        }

        _downloadProgress.value += (videoId to 0)
        job.invokeOnCompletion {
            if (task.cancelled) task.outputFile?.delete()
            activeDownloads.remove(videoId, task)
            _downloadProgress.value -= videoId
        }
        job.start()
    }

    fun cancelDownload(context: Context, song: SongItem) {
        val videoId = song.videoId ?: return
        val task = activeDownloads[videoId] ?: return
        if (task.cancelled) return
        task.cancelled = true
        // HttpURLConnection.disconnect() can perform TLS socket shutdown synchronously.
        // On Android 6 this throws NetworkOnMainThreadException when cancellation is
        // triggered from a UI click, so always disconnect from an IO coroutine.
        // Both disconnect and Job.cancel can run synchronous HTTP/TLS cancellation handlers.
        // Schedule both off the UI thread so Android 6 StrictMode cannot crash the app.
        scope.launch(Dispatchers.IO) {
            runCatching { task.connection?.disconnect() }
            runCatching { task.call?.cancel() }
        }
        scope.launch(Dispatchers.IO) {
            task.job?.cancel(CancellationException("Download cancelled by user"))
        }
        cancelNotification(context.applicationContext, song.id.toInt().coerceAtLeast(1))
        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Download cancelled: ${cleanTitle(song.title)}")
    }

    private suspend fun performDownload(
        context: Context,
        song: SongItem,
        videoId: String,
        task: DownloadTask
    ) {
        val notificationId = song.id.toInt().coerceAtLeast(1)
        val cleanTitleStr = cleanTitle(song.title)
        val cleanArtistStr = cleanArtist(song.artist)
        val coroutineContext = currentCoroutineContext()

        try {
            // Do not let a stale player UI state launch a duplicate download. This check runs
            // inside the IO download job, never on the main thread.
            if (isSongDownloaded(context, song)) {
                cancelNotification(context, notificationId)
                withContext(Dispatchers.Main) {
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage(
                        "Already downloaded: $cleanTitleStr",
                        iconResId = R.drawable.ic_downloaded
                    )
                }
                return
            }

            updateNotification(context, cleanTitleStr, 0, notificationId)

            // Reuse playback cache only when playback and download are both in Auto mode
            // with the same target bitrate. Otherwise resolve a fresh stream so the download
            // format and bitrate selectors are actually applied (cached spans have no MIME
            // metadata, so they cannot safely satisfy a different format choice).
            val settings = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            val playbackFormat = NewPipeStreamExtractor.normalizeAudioFormat(
                settings.getString("audio_format", "auto")
            )
            val downloadFormat = NewPipeStreamExtractor.normalizeAudioFormat(
                settings.getString("download_audio_format", "auto")
            )
            val playbackBitrate = NewPipeStreamExtractor.normalizeAudioQuality(
                settings.getString("audio_quality", "256 kbps")
            )
            val downloadBitrate = NewPipeStreamExtractor.normalizeAudioQuality(
                settings.getString("download_bitrate", "256 kbps")
            )
            val cacheMatchesDownloadSelection =
                playbackFormat == "auto" &&
                downloadFormat == "auto" &&
                playbackBitrate == downloadBitrate
            val cachedStream = if (cacheMatchesDownloadSelection) {
                findCachedStream(context, song.id.toString())
            } else {
                null
            }
            coroutineContext.ensureActive()

            val outputFile: File
            if (cachedStream != null) {
                if (!cachedStream.isComplete) {
                    cancelNotification(context, notificationId)
                    val cacheSize = android.text.format.Formatter.formatFileSize(context, cachedStream.cachedBytes)
                    val totalSize = if (cachedStream.contentLength > 0L) {
                        android.text.format.Formatter.formatFileSize(context, cachedStream.contentLength)
                    } else {
                        "unknown total size"
                    }
                    withContext(Dispatchers.Main) {
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage(
                            "Only $cacheSize of this song is cached ($totalSize). No network download was started; play it through to cache the full track."
                        )
                    }
                    return
                }

                val extension = extensionForMimeType(cachedStream.mimeType)
                outputFile = AudioFileManager.createOutputFile(
                    context,
                    "$cleanTitleStr - $cleanArtistStr.$extension"
                )
                task.outputFile = outputFile
                copyCachedStream(cachedStream, outputFile, coroutineContext)
                _downloadProgress.value += (videoId to 90)
                updateNotification(context, cleanTitleStr, 90, notificationId)
            } else {
                // No cache entry for this track: use the standard online download path.
                val downloadStream = NewPipeStreamExtractor.getDownloadStreamData(videoId)
                coroutineContext.ensureActive()
                val streamUrl = downloadStream?.url
                if (streamUrl.isNullOrBlank()) {
                    throw IOException("Failed to extract download URL")
                }

                val extension = when {
                    downloadStream?.mimeType?.startsWith("audio/mp4", ignoreCase = true) == true -> "m4a"
                    downloadStream?.mimeType?.startsWith("audio/mpeg", ignoreCase = true) == true -> "mp3"
                    downloadStream?.mimeType?.contains("webm", ignoreCase = true) == true -> "webm"
                    downloadStream?.mimeType?.contains("ogg", ignoreCase = true) == true -> "ogg"
                    downloadStream?.mimeType?.contains("flac", ignoreCase = true) == true -> "flac"
                    downloadStream?.mimeType?.contains("aac", ignoreCase = true) == true -> "aac"
                    downloadStream?.mimeType?.contains("wav", ignoreCase = true) == true -> "wav"
                    else -> "m4a"
                }
                outputFile = AudioFileManager.createOutputFile(
                    context,
                    "$cleanTitleStr - $cleanArtistStr.$extension"
                )
                task.outputFile = outputFile

                downloadNetworkStream(
                    context = context,
                    title = cleanTitleStr,
                    streamUrl = streamUrl,
                    expectedBytes = downloadStream?.contentLength,
                    outputFile = outputFile,
                    task = task,
                    videoId = videoId,
                    notificationId = notificationId,
                    coroutineContext = coroutineContext
                )
            }

            coroutineContext.ensureActive()

            var artworkBytes: ByteArray? = null
            if (cachedStream != null) {
                // Cached downloads stay fully offline; only use artwork already saved locally.
                val cachedArtwork = AudioPlaybackService.getCachedMediaArtworkFile(context, song.id.toString())
                if (cachedArtwork.isFile && cachedArtwork.length() > 0L) {
                    artworkBytes = runCatching { cachedArtwork.readBytes() }.getOrNull()
                }
            } else {
                val thumbnailUrl = song.thumbnailUrl
                if (!thumbnailUrl.isNullOrEmpty()) {
                    try {
                        val future = Glide.with(context)
                            .asBitmap()
                            .load(thumbnailUrl)
                            .submit()
                        val originalBitmap = future.get()
                        if (originalBitmap != null) {
                            val size = originalBitmap.width.coerceAtMost(originalBitmap.height)
                            val x = (originalBitmap.width - size) / 2
                            val y = (originalBitmap.height - size) / 2
                            val croppedBitmap = Bitmap.createBitmap(originalBitmap, x, y, size, size)
                            val stream = ByteArrayOutputStream()
                            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                            artworkBytes = stream.toByteArray()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch and crop thumbnail", e)
                    }
                }
            }

            coroutineContext.ensureActive()

            val metadataEmbedded = embedMetadataSafely(
                outputFile = outputFile,
                title = cleanTitleStr,
                artist = cleanArtistStr,
                album = song.albumName,
                videoId = videoId,
                fromCache = cachedStream != null,
                artworkBytes = artworkBytes
            )

            coroutineContext.ensureActive()
            if (!AudioFileManager.scanFile(context, outputFile, cleanTitleStr, cleanArtistStr, song.albumName)) {
                throw IOException("Could not publish the downloaded audio to the music library")
            }
            showFinishedNotification(context, cleanTitleStr, cleanArtistStr, notificationId)
            _downloadProgress.value += (videoId to 100)

            withContext(Dispatchers.Main) {
                com.codetrio.spatialflow.ui.SnackbarController.showMessage(
                    when {
                        cachedStream != null && metadataEmbedded -> "Saved cached audio offline: $cleanTitleStr"
                        cachedStream != null -> "Saved cached audio offline: $cleanTitleStr (metadata tags unavailable)"
                        metadataEmbedded -> "Downloaded with embedded metadata: $cleanTitleStr"
                        else -> "Downloaded: $cleanTitleStr (this audio format could not store embedded tags)"
                    },
                    iconResId = R.drawable.ic_downloaded
                )
            }
        } catch (e: CancellationException) {
            task.cancelled = true
            task.outputFile?.delete()
            cancelNotification(context, notificationId)
            throw e
        } catch (e: Exception) {
            task.outputFile?.delete()
            Log.e(TAG, "Download failed for ${song.title}", e)
            cancelNotification(context, notificationId)
            if (!task.cancelled) {
                withContext(Dispatchers.Main) {
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Download failed: ${e.message}")
                }
            }
        } finally {
            task.connection?.disconnect()
            task.connection = null
            task.call?.cancel()
            task.call = null
            if (task.cancelled) task.outputFile?.delete()
            _downloadProgress.value -= videoId
        }
    }


    /**
     * Downloads with browser-like headers, buffered IO, exact response-length validation,
     * and bounded retries for transient read/network failures.
     */
    private suspend fun downloadNetworkStream(
        context: Context,
        title: String,
        streamUrl: String,
        expectedBytes: Long?,
        outputFile: File,
        task: DownloadTask,
        videoId: String,
        notificationId: Int,
        coroutineContext: CoroutineContext
    ) {
        var lastError: Exception? = null
        for (attempt in 1..3) {
            var call: okhttp3.Call? = null
            var response: okhttp3.Response? = null
            try {
                coroutineContext.ensureActive()
                if (outputFile.exists() && !outputFile.delete()) {
                    throw IOException("Could not clear an incomplete audio download")
                }
                outputFile.parentFile?.let { parent ->
                    if (!parent.exists() && !parent.mkdirs()) {
                        throw IOException("Could not create the download directory")
                    }
                }

                // Google video streams are fetched as 1 MiB range requests, pipelining the next
                // range while the current range is read. This avoids YouTube's playback-speed
                // throttling on a single long, open-ended GET without changing audio quality.
                call = YouTubeRangeDownloadClient.newCall(streamUrl)
                task.call = call
                response = call.execute()
                if (response.code != HttpURLConnection.HTTP_OK) {
                    throw IOException("Download server returned HTTP ${response.code}")
                }

                val declaredLength = response.body.contentLength().takeIf { it > 0L }
                val progressLength = declaredLength ?: expectedBytes?.takeIf { it > 0L }
                var downloadedBytes = 0L
                var lastProgressUpdate = 0L
                response.body.byteStream().use { input ->
                    BufferedOutputStream(FileOutputStream(outputFile), 64 * 1024).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val bytesRead = input.read(buffer)
                            if (bytesRead < 0) break
                            if (bytesRead == 0) continue
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead.toLong()

                            if (progressLength != null && progressLength > 0L) {
                                val progress = ((downloadedBytes.toDouble() / progressLength.toDouble()) * 88.0)
                                    .toInt().coerceIn(1, 88)
                                if (_downloadProgress.value[videoId] != progress) {
                                    _downloadProgress.value += (videoId to progress)
                                }
                                val now = System.currentTimeMillis()
                                if (now - lastProgressUpdate > 400L) {
                                    lastProgressUpdate = now
                                    updateNotification(context, title, progress, notificationId)
                                }
                            }
                        }
                    }
                }

                if (downloadedBytes <= 0L) {
                    throw IOException("The server returned an empty audio stream")
                }
                if (declaredLength != null && downloadedBytes != declaredLength) {
                    throw IOException(
                        "Incomplete audio download: received $downloadedBytes of $declaredLength bytes"
                    )
                }
                Log.i(TAG, "Downloaded $downloadedBytes bytes using range-enabled mode on attempt $attempt")
                return
            } catch (cancelled: CancellationException) {
                outputFile.delete()
                throw cancelled
            } catch (error: Exception) {
                lastError = error
                outputFile.delete()
                coroutineContext.ensureActive()
                Log.w(TAG, "Download attempt $attempt/3 failed for $title", error)
                if (attempt < 3) {
                    _downloadProgress.value += (videoId to 0)
                    delay(attempt * 500L)
                }
            } finally {
                response?.close()
                call?.cancel()
                if (task.call === call) task.call = null
                if (task.connection != null) {
                    runCatching { task.connection?.disconnect() }
                    task.connection = null
                }
            }
        }
        throw IOException("Audio download failed after 3 attempts: ${lastError?.message ?: "unknown network error"}", lastError)
    }

    /**
     * Tag a temporary copy and replace the original only after metadata commit succeeds.
     * Unsupported taggers therefore cannot corrupt the downloaded audio stream.
     */
    private fun embedMetadataSafely(
        outputFile: File,
        title: String,
        artist: String,
        album: String?,
        videoId: String,
        fromCache: Boolean,
        artworkBytes: ByteArray?
    ): Boolean {
        val extension = outputFile.extension
        val taggedFile = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}.tagging.$extension")
        var backupFile: File? = null
        try {
            if (taggedFile.exists() && !taggedFile.delete()) {
                throw IOException("Could not clear the metadata staging file")
            }
            outputFile.copyTo(taggedFile, overwrite = true)
            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(taggedFile)
            val tag = audioFile.tagOrCreateAndSetDefault
            tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, title)
            tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, artist)
            tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM_ARTIST, artist)
            album?.takeIf { it.isNotBlank() }?.let {
                tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM, it.trim())
            }
            tag.setField(
                org.jaudiotagger.tag.FieldKey.COMMENT,
                if (fromCache) {
                    "Exported from SpatialFlow cached audio (video ID: $videoId)"
                } else {
                    "Downloaded from YouTube Music (video ID: $videoId)"
                }
            )
            if (artworkBytes != null && artworkBytes.isNotEmpty()) {
                val artwork = org.jaudiotagger.tag.images.StandardArtwork()
                artwork.binaryData = artworkBytes
                artwork.mimeType = "image/jpeg"
                tag.deleteArtworkField()
                tag.setField(artwork)
            }
            audioFile.commit()
            if (!taggedFile.isFile || taggedFile.length() <= 0L) {
                throw IOException("Metadata write produced an empty file")
            }

            backupFile = File(outputFile.parentFile, "${outputFile.name}.raw-backup")
            if (backupFile.exists() && !backupFile.delete()) {
                throw IOException("Could not clear the audio backup file")
            }
            if (!outputFile.renameTo(backupFile)) {
                throw IOException("Could not preserve original audio before metadata writing")
            }
            if (!taggedFile.renameTo(outputFile)) {
                backupFile.renameTo(outputFile)
                throw IOException("Could not install the tagged audio file")
            }
            backupFile.delete()
            return true
        } catch (error: Exception) {
            Log.w(TAG, "Metadata embedding failed safely for ${outputFile.name}", error)
            taggedFile.delete()
            if (backupFile?.exists() == true && !outputFile.exists()) {
                backupFile.renameTo(outputFile)
            }
            return false
        }
    }

    private fun findCachedStream(context: Context, songId: String): CachedStreamCandidate? {
        val cache = try {
            AudioPlaybackService.getCache(context.applicationContext)
        } catch (e: Exception) {
            throw IOException("Unable to inspect the local audio cache; refusing to start a network download", e)
        }

        val candidates = cache.keys.mapNotNull { cacheKey ->
            val metadata = AudioPlaybackService.getCachedMediaMetadata(context, cacheKey)
                ?: return@mapNotNull null
            if (metadata.songId != songId) return@mapNotNull null

            val spans = cache.getCachedSpans(cacheKey).toList().sortedBy { it.position }
            if (spans.isEmpty()) return@mapNotNull null
            val contentMetadata = cache.getContentMetadata(cacheKey)
            val contentLength = ContentMetadata.getContentLength(contentMetadata)
            val cachedBytes = spans.sumOf { it.length }

            var coveredUntil = 0L
            var hasGap = false
            spans.forEach { span ->
                if (span.position > coveredUntil) hasGap = true
                coveredUntil = maxOf(coveredUntil, span.position + span.length)
            }
            val complete = contentLength > 0L &&
                !hasGap &&
                coveredUntil >= contentLength &&
                cache.isCached(cacheKey, 0L, contentLength)

            CachedStreamCandidate(
                cacheKey = cacheKey,
                spans = spans,
                contentLength = contentLength,
                mimeType = null, // CachedMediaMetadata stores display metadata, not MIME type.
                cachedBytes = cachedBytes,
                isComplete = complete
            )
        }
        return candidates.firstOrNull { it.isComplete } ?: candidates.maxByOrNull { it.cachedBytes }
    }

    private fun copyCachedStream(
        cached: CachedStreamCandidate,
        outputFile: File,
        coroutineContext: CoroutineContext
    ) {
        outputFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
        var expectedPosition = 0L
        FileOutputStream(outputFile).use { output ->
            val buffer = ByteArray(64 * 1024)
            for (span in cached.spans) {
                if (span.position != expectedPosition) {
                    throw IOException("Cached audio has a gap; refusing to use the network")
                }
                val spanFile = span.file ?: throw IOException("A cached audio segment is no longer available")
                FileInputStream(spanFile).use { input ->
                    var remaining = span.length
                    while (remaining > 0L) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read < 0) throw IOException("Cached audio segment ended unexpectedly")
                        output.write(buffer, 0, read)
                        remaining -= read.toLong()
                    }
                }
                expectedPosition += span.length
            }
            if (expectedPosition < cached.contentLength) {
                throw IOException("Cached audio is incomplete; refusing to use the network")
            }
        }
        if (outputFile.length() != cached.contentLength) {
            outputFile.delete()
            throw IOException("Cached audio size does not match its content length")
        }
    }

    private fun extensionForMimeType(mimeType: String?): String = when {
        mimeType?.contains("audio/mp4", ignoreCase = true) == true -> "m4a"
        mimeType?.contains("audio/mpeg", ignoreCase = true) == true -> "mp3"
        mimeType?.contains("audio/webm", ignoreCase = true) == true -> "webm"
        mimeType?.contains("audio/ogg", ignoreCase = true) == true -> "ogg"
        mimeType?.contains("audio/flac", ignoreCase = true) == true -> "flac"
        mimeType?.contains("audio/aac", ignoreCase = true) == true -> "aac"
        mimeType?.contains("audio/wav", ignoreCase = true) == true -> "wav"
        else -> "m4a"
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of active song downloads"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(context: Context, title: String, progress: Int, notificationId: Int) {
        createNotificationChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.codetrio.spatialflow.R.drawable.ic_music_note)
            .setContentTitle("Downloading Track")
            .setContentText("$title ($progress%)")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, builder.build())
    }

    private fun showFinishedNotification(context: Context, title: String, artist: String, notificationId: Int) {
        createNotificationChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.codetrio.spatialflow.R.drawable.ic_music_note)
            .setContentTitle("Download Complete")
            .setContentText("$title — $artist")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, builder.build())
    }

    private fun cancelNotification(context: Context, notificationId: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)
    }

    fun isSongDownloaded(context: Context, song: SongItem): Boolean {
        if (song.videoId.isNullOrEmpty()) return true // Local songs are already available offline

        val cleanTitleStr = cleanTitle(song.title)
        val cleanArtistStr = cleanArtist(song.artist)
        val extensions = listOf("m4a", "mp3", "webm", "ogg", "flac", "aac", "wav", "opus")
        val cleanNames = extensions.map { extension ->
            "$cleanTitleStr - $cleanArtistStr.$extension"
                .replace(Regex("[^a-zA-Z0-9._\\s()\\[\\]-]"), "_")
        }

        // 1. Check legacy direct file paths on Android versions before scoped storage.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val downloadsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "SpatialFlow"
            )
            if (cleanNames.any { File(downloadsDir, it).isFile }) return true
        }

        // 2. Match every container the downloader can create. Previously only .mp3 was
        // checked, so valid .m4a/.webm exports appeared undownloaded and the player state
        // could stay stale or offer a duplicate download.
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        for (cleanName in cleanNames) {
            try {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.Audio.Media.DISPLAY_NAME} = ?",
                    arrayOf(cleanName),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking if song is downloaded as $cleanName", e)
            }
        }
        return false
    }
}