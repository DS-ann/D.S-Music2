package com.dsann.music.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Reads embedded tags from D.S Music downloads with Android's own media extractor.
 *
 * MediaStore's album-art index is not reliably populated for files inserted directly into
 * MediaStore on every Android release. Saving the extracted cover in app cache gives D.S Music
 * a direct image URI even when ALBUM_ID is zero or the system album-art URI is empty.
 */
internal data class EmbeddedDownloadedAudioMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val artworkUri: String?
)

internal object DownloadedAudioMetadataReader {
    private const val TAG = "DownloadedAudioMetadata"
    private const val MAX_COVER_BYTES = 8 * 1024 * 1024

    fun read(
        context: Context,
        filePath: String?,
        mediaUri: Uri,
        cacheKey: String
    ): EmbeddedDownloadedAudioMetadata? {
        val retriever = MediaMetadataRetriever()
        var sourceConfigured = false
        try {
            val file = filePath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
            if (file != null) {
                sourceConfigured = runCatching {
                    retriever.setDataSource(file.absolutePath)
                    true
                }.getOrDefault(false)
            }
            if (!sourceConfigured) {
                sourceConfigured = runCatching {
                    retriever.setDataSource(context.applicationContext, mediaUri)
                    true
                }.getOrDefault(false)
            }
            if (!sourceConfigured) return null

            val title = usable(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE))
            val artist = usable(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
            val album = usable(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM))
            val artworkUri = readOrCacheArtwork(context, retriever, cacheKey, file)
            return EmbeddedDownloadedAudioMetadata(title, artist, album, artworkUri)
        } catch (error: Exception) {
            Log.w(TAG, "Could not read embedded metadata for $cacheKey", error)
            return null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Preserve metadata already extracted if release fails.
            }
        }
    }

    private fun usable(value: String?): String? {
        val normalized = value?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
        if (normalized.isBlank()) return null
        if (normalized.equals("<unknown>", ignoreCase = true) ||
            normalized.equals("unknown", ignoreCase = true) ||
            normalized.equals("unknown title", ignoreCase = true) ||
            normalized.equals("unknown artist", ignoreCase = true) ||
            normalized.equals("unknown album", ignoreCase = true)
        ) return null
        return normalized
    }

    private fun readOrCacheArtwork(
        context: Context,
        retriever: MediaMetadataRetriever,
        cacheKey: String,
        sourceFile: File?
    ): String? {
        val signature = sourceFile?.let { "${it.length()}-${it.lastModified()}" } ?: "uri"
        val safeKey = cacheKey.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val directory = File(context.cacheDir, "downloaded-embedded-art")
        if (!directory.exists() && !directory.mkdirs()) return null

        // A file signature avoids re-extracting and rewriting artwork during every library scan.
        val baseName = "${safeKey}-${signature}"
        val existingJpeg = File(directory, "$baseName.jpg")
        if (existingJpeg.isFile && existingJpeg.length() > 0L) return Uri.fromFile(existingJpeg).toString()
        val existingPng = File(directory, "$baseName.png")
        if (existingPng.isFile && existingPng.length() > 0L) return Uri.fromFile(existingPng).toString()

        val bytes = try {
            retriever.embeddedPicture
        } catch (error: Exception) {
            Log.d(TAG, "No embedded artwork exposed by the media extractor for $cacheKey: ${error.message}")
            null
        } ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_COVER_BYTES) {
            Log.w(TAG, "Ignoring invalid or oversized embedded cover for $cacheKey (${bytes.size} bytes)")
            return null
        }

        val extension = when {
            bytes.size >= 3 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte() -> "jpg"
            bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte() -> "png"
            else -> {
                Log.w(TAG, "Embedded cover has an unsupported image signature for $cacheKey")
                return null
            }
        }

        val target = File(directory, "$baseName.$extension")
        val temporary = File(directory, "$baseName.$extension.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            if (!temporary.renameTo(target)) {
                if (target.exists() && !target.delete()) {
                    throw java.io.IOException("Could not replace cached embedded cover")
                }
                if (!temporary.renameTo(target)) {
                    throw java.io.IOException("Could not install cached embedded cover")
                }
            }
            return Uri.fromFile(target).toString()
        } catch (error: Exception) {
            temporary.delete()
            Log.w(TAG, "Could not cache embedded cover for $cacheKey", error)
            return null
        }
    }
}
