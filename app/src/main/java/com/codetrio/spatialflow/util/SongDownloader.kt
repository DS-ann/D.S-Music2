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
import kotlinx.coroutines.ensureActive

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
            activeDownloads.remove(videoId, task)
            _downloadProgress.value -= videoId
            if (task.cancelled) task.outputFile?.delete()
        }
        job.start()
    }

    fun cancelDownload(context: Context, song: SongItem) {
        val videoId = song.videoId ?: return
        val task = activeDownloads.remove(videoId) ?: return
        task.cancelled = true
        task.connection?.disconnect()
        task.job?.cancel(CancellationException("Download cancelled by user"))
        _downloadProgress.value -= videoId
        cancelNotification(context.applicationContext, song.id.toInt().coerceAtLeast(1))
        task.outputFile?.delete()
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
            updateNotification(context, cleanTitleStr, 0, notificationId)

            // Use local cached audio when available. Never fetch a stream or remote
            // artwork for a track that already has cache data.
            val cachedStream = findCachedStream(context, song.id.toString())
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

                val connection = URL(streamUrl).openConnection() as HttpURLConnection
                task.connection = connection
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("Download server error: HTTP ${connection.responseCode}")
                }

                val totalBytes = connection.contentLength
                connection.inputStream.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(8192)
                        var downloadedBytes = 0L
                        var lastProgressUpdate = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val bytesRead = input.read(buffer)
                            if (bytesRead == -1) break
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            if (totalBytes > 0) {
                                val progress = ((downloadedBytes.toDouble() / totalBytes) * 88.0).toInt().coerceIn(0, 88)
                                if (_downloadProgress.value[videoId] != progress) {
                                    _downloadProgress.value += (videoId to progress)
                                }
                                val now = System.currentTimeMillis()
                                if (now - lastProgressUpdate > 400) {
                                    lastProgressUpdate = now
                                    updateNotification(context, cleanTitleStr, progress, notificationId)
                                }
                            }
                        }
                    }
                }
                connection.disconnect()
                task.connection = null
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

            var metadataEmbedded = false
            try {
                val audioFile = org.jaudiotagger.audio.AudioFileIO.read(outputFile)
                val tag = audioFile.tagOrCreateAndSetDefault
                tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, cleanTitleStr)
                tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, cleanArtistStr)
                tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM_ARTIST, cleanArtistStr)
                song.albumName?.takeIf { it.isNotBlank() }?.let {
                    tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM, it.trim())
                }
                tag.setField(
                    org.jaudiotagger.tag.FieldKey.COMMENT,
                    if (cachedStream != null) {
                        "Exported from SpatialFlow cached audio (video ID: $videoId)"
                    } else {
                        "Downloaded from YouTube Music (video ID: $videoId)"
                    }
                )

                if (artworkBytes != null) {
                    val artwork = org.jaudiotagger.tag.images.StandardArtwork()
                    artwork.binaryData = artworkBytes
                    artwork.mimeType = "image/jpeg"
                    tag.deleteArtworkField()
                    tag.setField(artwork)
                }

                audioFile.commit()
                metadataEmbedded = true
            } catch (e: Exception) {
                Log.w(TAG, "Embedded metadata is not supported for ${outputFile.extension}", e)
            }

            coroutineContext.ensureActive()
            AudioFileManager.scanFile(context, outputFile, cleanTitleStr, cleanArtistStr, song.albumName)
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
            if (task.cancelled) task.outputFile?.delete()
            _downloadProgress.value -= videoId
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
                mimeType = ContentMetadata.getContentType(contentMetadata),
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
            val buffer = ByteArray(8192)
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
        if (song.videoId.isNullOrEmpty()) return true // Local songs are always offline
        
        val cleanTitleStr = cleanTitle(song.title)
        val cleanArtistStr = cleanArtist(song.artist)
        val fileName = "$cleanTitleStr - $cleanArtistStr.mp3"
        val cleanName = fileName.replace(Regex("[^a-zA-Z0-9._\\s()\\[\\]-]"), "_")

        // 1. Check legacy direct file path if pre-Q
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SpatialFlow")
            val file = File(downloadsDir, cleanName)
            if (file.exists()) return true
        }

        // 2. Query MediaStore
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(cleanName)
        
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.count > 0) return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if song is downloaded", e)
        }

        return false
    }
}
