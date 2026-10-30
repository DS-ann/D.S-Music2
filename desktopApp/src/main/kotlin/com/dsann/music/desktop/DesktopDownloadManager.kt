package com.dsann.music.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

internal object DesktopDownloadManager {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun download(
        song: com.dsann.music.data.innertube.OnlineSong,
        targetBitrateKbps: Int = 256,
        preferredFormat: String = "m4a",
        onProgress: (Int) -> Unit
    ): DesktopSavedSong = withContext(Dispatchers.IO) {
        val stream = DesktopNewPipeExtractor.resolve(song.videoId, targetBitrateKbps, preferredFormat)
        val extension = extensionFor(stream.mimeType)
        val base = safeFileName("${song.artist} - ${song.title}").ifBlank { song.videoId }
        val target = File(DesktopLibraryStore.downloadsDirectory, "$base.$extension")
        val temporary = File(target.parentFile, target.name + ".part")
        val request = Request.Builder()
            .url(stream.url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/140 Safari/537.36")
            .header("Accept-Encoding", "identity")
            .build()

        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Download request returned HTTP ${response.code}." }
            val body = response.body ?: error("The audio server returned an empty response.")
            val expected = body.contentLength().takeIf { it > 0L } ?: stream.sizeBytes ?: -1L
            var received = 0L
            body.byteStream().use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        received += count
                        if (expected > 0L) onProgress(((received * 100L) / expected).toInt().coerceIn(0, 99))
                    }
                    output.fd.sync()
                }
            }
            check(temporary.length() > 0L) { "The downloaded audio file is empty." }
            if (target.exists()) target.delete()
            check(temporary.renameTo(target)) { "Could not finalize the downloaded audio file." }
            onProgress(100)
        }

        val cover = song.thumbnailUrl?.let { url ->
            runCatching {
                val coverFile = File(DesktopLibraryStore.downloadsDirectory, "$base.jpg")
                val coverRequest = Request.Builder().url(url).build()
                http.newCall(coverRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body ?: return@use null
                        body.byteStream().use { input -> coverFile.outputStream().use { input.copyTo(it) } }
                        coverFile.takeIf { it.length() > 0L }
                    } else null
                }
            }.getOrNull()
        }

        val lyricData = DesktopLyricsRepository.fetch(
            title = song.title,
            artist = song.artist,
            durationMs = song.durationMs,
            videoId = song.videoId,
            album = song.albumName
        )
        val lyricsFile = lyricData?.let { data ->
            runCatching {
                File(DesktopLibraryStore.downloadsDirectory, "$base.lrc").apply {
                    writeText(data.raw)
                }.takeIf { it.length() > 0L }
            }.getOrNull()
        }

        val saved = DesktopSavedSong(
            videoId = song.videoId,
            title = song.title,
            artist = song.artist,
            album = song.albumName.orEmpty(),
            durationMs = song.durationMs.takeIf { it > 0L } ?: stream.durationMs,
            thumbnailUrl = song.thumbnailUrl,
            audioPath = target.absolutePath,
            coverPath = cover?.absolutePath,
            lyricsPath = lyricsFile?.absolutePath
        )
        DesktopLibraryStore.recordDownload(saved, cached = cover != null && lyricsFile != null)
        saved
    }

    private fun extensionFor(mimeType: String): String = when {
        mimeType.contains("mp4", true) || mimeType.contains("aac", true) -> "m4a"
        mimeType.contains("mpeg", true) || mimeType.contains("mp3", true) -> "mp3"
        mimeType.contains("ogg", true) -> "ogg"
        mimeType.contains("webm", true) -> "webm"
        else -> "audio"
    }

    private fun safeFileName(value: String): String = value
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(140)
}
