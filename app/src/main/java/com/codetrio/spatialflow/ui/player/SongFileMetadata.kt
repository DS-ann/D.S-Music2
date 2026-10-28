package com.codetrio.spatialflow.ui.player

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.codetrio.spatialflow.model.SongItem
import java.io.File

/**
 * File facts read from the local media source. Bitrate from embedded metadata is kept
 * separate from an estimate calculated from file bytes and duration.
 */
internal data class SongFileMetadata(
    val isRemote: Boolean,
    val sizeBytes: Long? = null,
    val durationMs: Long? = null,
    val bitrateBps: Long? = null,
    val estimatedBitrateBps: Long? = null,
    val mimeType: String? = null,
    val album: String? = null,
    val fileName: String? = null,
    val filePath: String? = null
)

internal object SongFileMetadataReader {

    fun isRemoteSong(song: SongItem): Boolean {
        val path = song.path.orEmpty()
        val uri = runCatching { song.contentUri }.getOrNull()
        return !song.videoId.isNullOrBlank() ||
            path.startsWith("http://", ignoreCase = true) ||
            path.startsWith("https://", ignoreCase = true) ||
            uri?.scheme.equals("http", ignoreCase = true) ||
            uri?.scheme.equals("https", ignoreCase = true)
    }

    fun read(context: Context, song: SongItem): SongFileMetadata {
        if (isRemoteSong(song)) return SongFileMetadata(isRemote = true)

        val localFile = sequenceOf(song.path, song.data)
            .filterNotNull()
            .map { it.trim() }
            .filter {
                it.isNotEmpty() &&
                    !it.startsWith("content://", true) &&
                    !it.startsWith("http://", true) &&
                    !it.startsWith("https://", true)
            }
            .map { File(it) }
            .firstOrNull { it.isFile }

        val uri = runCatching { song.contentUri }.getOrNull()
        val sizeBytes = localFile?.length()?.takeIf { it > 0L }
            ?: uri?.let { querySize(context, it) }
        var durationMs: Long? = null
        var bitrateBps: Long? = null
        var mimeType: String? = null
        var album: String? = song.albumName?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }

        val retriever = MediaMetadataRetriever()
        try {
            when {
                localFile != null -> retriever.setDataSource(localFile.absolutePath)
                uri?.scheme == "content" || uri?.scheme == "file" -> retriever.setDataSource(context, uri)
            }
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
            bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
            mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                ?.takeIf { it.isNotBlank() }
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
        } catch (_: Exception) {
            // Some MediaStore providers expose a readable URI but no retrievable file metadata.
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Metadata already collected remains valid if release fails.
            }
        }

        if (mimeType == null && uri != null) {
            mimeType = try {
                context.contentResolver.getType(uri)?.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }

        val fileName = localFile?.name ?: uri?.let { queryDisplayName(context, it) }
        val estimate = if (bitrateBps == null && sizeBytes != null && sizeBytes > 0L && song.duration > 0L) {
            // bits/sec = bytes * 8 / (durationMillis / 1000). Tags make this approximate.
            (sizeBytes.toDouble() * 8000.0 / song.duration.toDouble())
                .takeIf { it.isFinite() && it > 0.0 && it < Long.MAX_VALUE.toDouble() }
                ?.toLong()
        } else {
            null
        }

        return SongFileMetadata(
            isRemote = false,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            bitrateBps = bitrateBps,
            estimatedBitrateBps = estimate,
            mimeType = mimeType,
            album = album,
            fileName = fileName?.takeIf { it.isNotBlank() },
            filePath = localFile?.absolutePath
        )
    }

    private fun querySize(context: Context, uri: Uri): Long? {
        for (column in listOf(android.provider.MediaStore.MediaColumns.SIZE, OpenableColumns.SIZE).distinct()) {
            val fromProvider = try {
                context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(column)
                    if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                        cursor.getLong(index).takeIf { it > 0L }
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                null
            }
            if (fromProvider != null) return fromProvider
        }

        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.length.takeIf { it > 0L }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        for (column in listOf(
            android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
            OpenableColumns.DISPLAY_NAME
        ).distinct()) {
            val displayName = try {
                context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(column)
                    if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                        cursor.getString(index)?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                null
            }
            if (displayName != null) return displayName
        }
        return null
    }
}
