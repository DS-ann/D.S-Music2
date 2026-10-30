package com.dsann.music.desktop

import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine

/**
 * First desktop playback backend.
 *
 * This deliberately uses the Java Sound SPI rather than Android Media3. MP3 and
 * OGG decoding are supplied by MP3SPI/VorbisSPI; PCM WAV/AIFF/AU are provided by
 * the JDK. The backend is isolated so it can be replaced by a wider-format
 * FFmpeg-based engine without changing the Compose UI.
 */
internal class DesktopAudioPlayer : AutoCloseable {
    private val generation = AtomicLong(0L)
    private val gate = Object()

    @Volatile
    private var outputLine: SourceDataLine? = null

    @Volatile
    private var worker: Thread? = null

    @Volatile
    private var paused = true

    @Volatile
    var currentFile: File? = null
        private set

    @Volatile
    var positionMillis: Long = 0L
        private set

    @Volatile
    var durationMillis: Long = 0L
        private set

    @Volatile
    var isPlaying: Boolean = false
        private set

    @Volatile
    var isPaused: Boolean = true
        private set

    @Volatile
    var errorMessage: String? = null
        private set

    fun play(file: File, startPositionMs: Long = 0L) {
        stop()
        val token = generation.incrementAndGet()

        currentFile = file
        positionMillis = startPositionMs.coerceAtLeast(0L)
        durationMillis = readDuration(file)
        errorMessage = null
        paused = false
        isPaused = false
        isPlaying = true

        worker = Thread(
            { runPlayback(file, startPositionMs.coerceAtLeast(0L), token) },
            "SpatialFlow-Audio"
        ).apply {
            isDaemon = true
            start()
        }
    }

    fun pause() {
        if (!isPlaying || paused) return
        paused = true
        isPaused = true
        try {
            outputLine?.let {
                it.stop()
                it.flush()
            }
        } catch (_: Exception) {
            // The playback worker owns final line cleanup.
        }
    }

    fun resume() {
        if (!isPlaying || !paused) return
        paused = false
        isPaused = false
        try {
            outputLine?.start()
        } catch (_: Exception) {
            // The worker will publish any terminal playback error.
        }
        synchronized(gate) {
            gate.notifyAll()
        }
    }

    fun togglePlayback() {
        if (paused) resume() else pause()
    }

    fun seekTo(positionMs: Long) {
        val file = currentFile ?: return
        play(file, positionMs.coerceAtLeast(0L))
    }

    fun stop() {
        generation.incrementAndGet()
        paused = true
        isPaused = true
        synchronized(gate) {
            gate.notifyAll()
        }

        val line = outputLine
        outputLine = null
        try {
            line?.stop()
            line?.flush()
            line?.close()
        } catch (_: Exception) {
            // Already closed by the playback worker.
        }

        worker?.interrupt()
        worker = null
        isPlaying = false
        positionMillis = 0L
    }

    private fun runPlayback(file: File, startPositionMs: Long, token: Long) {
        var localLine: SourceDataLine? = null
        try {
            AudioSystem.getAudioInputStream(file).use { encoded ->
                val sourceFormat = encoded.format
                val sampleRate = sourceFormat.sampleRate
                    .takeIf { it.isFinite() && it > 0f } ?: 44_100f
                val channels = sourceFormat.channels
                    .takeIf { it in 1..2 }
                    ?: throw IOException("Only mono and stereo audio are supported by this desktop backend.")

                val targetFormat = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate,
                    16,
                    channels,
                    channels * 2,
                    sampleRate,
                    false
                )

                val alreadyPcm = sourceFormat.encoding == AudioFormat.Encoding.PCM_SIGNED &&
                    sourceFormat.sampleSizeInBits == 16 &&
                    sourceFormat.channels == channels &&
                    sourceFormat.frameSize == channels * 2 &&
                    !sourceFormat.isBigEndian

                val decoded = if (alreadyPcm) {
                    encoded
                } else {
                    AudioSystem.getAudioInputStream(targetFormat, encoded)
                }

                decoded.use { pcm ->
                    val bytesPerSecond = targetFormat.frameRate.toDouble() * targetFormat.frameSize
                    val bytesToSkip = (startPositionMs.toDouble() * bytesPerSecond / 1000.0).toLong()
                    skipFully(pcm, bytesToSkip)

                    if (token != generation.get()) return

                    val info = DataLine.Info(SourceDataLine::class.java, targetFormat)
                    localLine = AudioSystem.getLine(info) as SourceDataLine
                    localLine!!.open(targetFormat, 64 * 1024)

                    if (token != generation.get()) return
                    outputLine = localLine
                    localLine!!.start()

                    val buffer = ByteArray(16 * 1024)
                    var bytesWritten = 0L

                    while (token == generation.get()) {
                        awaitResume(token)
                        if (token != generation.get()) break

                        val count = pcm.read(buffer)
                        if (count < 0) break

                        var offset = 0
                        while (offset < count && token == generation.get()) {
                            awaitResume(token)
                            if (token != generation.get()) break
                            val written = localLine!!.write(buffer, offset, count - offset)
                            if (written <= 0) break
                            offset += written
                            bytesWritten += written
                            if (bytesPerSecond > 0.0) {
                                positionMillis = startPositionMs +
                                    (bytesWritten * 1000.0 / bytesPerSecond).toLong()
                            }
                        }
                    }

                    if (token == generation.get()) {
                        localLine!!.drain()
                        if (durationMillis > 0L) positionMillis = durationMillis
                    }
                }
            }
        } catch (error: Exception) {
            if (token == generation.get()) {
                errorMessage = error.message ?: "Unable to play this audio file."
            }
        } finally {
            try {
                localLine?.stop()
                localLine?.close()
            } catch (_: Exception) {
                // Ignore line cleanup errors.
            }
            if (outputLine === localLine) outputLine = null
            if (token == generation.get()) {
                isPlaying = false
                paused = true
                isPaused = true
            }
        }
    }

    private fun awaitResume(token: Long) {
        synchronized(gate) {
            while (paused && token == generation.get()) {
                try {
                    gate.wait()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }
        }
    }

    private fun skipFully(stream: java.io.InputStream, requestedBytes: Long) {
        var remaining = requestedBytes.coerceAtLeast(0L)
        val scratch = ByteArray(8 * 1024)
        while (remaining > 0L) {
            val skipped = stream.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                val read = stream.read(scratch, 0, minOf(scratch.size.toLong(), remaining).toInt())
                if (read < 0) break
                remaining -= read
            }
        }
    }

    private fun readDuration(file: File): Long {
        return try {
            val audioFormat = AudioSystem.getAudioFileFormat(file)
            val frames = audioFormat.frameLength
            val frameRate = audioFormat.format.frameRate
            if (frames > 0 && frameRate.isFinite() && frameRate > 0f) {
                (frames * 1000.0 / frameRate).toLong()
            } else {
                0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    override fun close() {
        stop()
    }
}
