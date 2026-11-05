package com.dsann.music.util

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Mp4MetadataRemuxerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun writesNativeIlstTagsAndPreservesMediaPayloadAndChunkOffsets() {
        val ftyp = box("ftyp", "M4A \u0000\u0000\u0000\u0000M4A ".toByteArray(Charsets.ISO_8859_1))
        val mediaPayload = "unchanged AAC sample payload".toByteArray(Charsets.UTF_8)
        val mdat = box("mdat", mediaPayload)
        val placeholderMoov = buildMoov(0L)
        val oldChunkOffset = (ftyp.size + placeholderMoov.size + 8).toLong()
        val moov = buildMoov(oldChunkOffset)

        val source = File(temporaryFolder.root, "source.m4a")
        val output = File(temporaryFolder.root, "tagged.m4a")
        source.writeBytes(ftyp + moov + mdat)

        val cover = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)
        Mp4MetadataRemuxer.writeTaggedMp4(
            inputFile = source,
            outputFile = output,
            title = "Example title",
            artist = "Example artist",
            album = "Example album",
            comment = "Saved by D.S Music",
            artworkBytes = cover
        )

        val metadata = Mp4MetadataRemuxer.readMetadata(output)!!
        assertEquals("Example title", metadata.title)
        assertEquals("Example artist", metadata.artist)
        assertEquals("Example album", metadata.album)
        assertArrayEquals(cover, metadata.artwork)

        val sourceMdat = findBoxPayload(source.readBytes(), "mdat")
        val outputBytes = output.readBytes()
        val outputMdat = findBoxPayload(outputBytes, "mdat")
        assertArrayEquals(sourceMdat, outputMdat)

        val newChunkOffset = findChunkOffset(outputBytes)
        val delta = findBoxSize(outputBytes, "moov") - moov.size.toLong()
        assertEquals(oldChunkOffset + delta, newChunkOffset)
        assertTrue(newChunkOffset > oldChunkOffset)
    }

    private fun buildMoov(chunkOffset: Long): ByteArray {
        val stcoPayload = ByteArrayOutputStream().apply {
            write(int32(0L)) // version + flags
            write(int32(1L)) // entry count
            write(int32(chunkOffset))
        }.toByteArray()
        val stco = box("stco", stcoPayload)
        val stbl = box("stbl", stco)
        val minf = box("minf", stbl)
        val mdia = box("mdia", minf)
        val trak = box("trak", mdia)
        return box("moov", trak)
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.ISO_8859_1)
        return int32(payload.size.toLong() + 8L) + typeBytes + payload
    }

    private fun int32(value: Long): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte()
    )

    private fun findBoxPayload(bytes: ByteArray, type: String): ByteArray {
        val position = findBoxType(bytes, type)
        val size = readUInt32(bytes, position - 4).toInt()
        return bytes.copyOfRange(position + 4, position - 4 + size)
    }

    private fun findBoxSize(bytes: ByteArray, type: String): Long {
        val position = findBoxType(bytes, type)
        return readUInt32(bytes, position - 4)
    }

    private fun findChunkOffset(bytes: ByteArray): Long {
        val typePosition = findBoxType(bytes, "stco")
        return readUInt32(bytes, typePosition + 12)
    }

    private fun findBoxType(bytes: ByteArray, type: String): Int {
        val wanted = type.toByteArray(Charsets.ISO_8859_1)
        for (index in 4..bytes.size - wanted.size) {
            if (wanted.indices.all { bytes[index + it] == wanted[it] }) return index
        }
        throw AssertionError("MP4 atom $type was not found")
    }

    private fun readUInt32(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xFFL) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 8) or
            (bytes[offset + 3].toLong() and 0xFFL)
}