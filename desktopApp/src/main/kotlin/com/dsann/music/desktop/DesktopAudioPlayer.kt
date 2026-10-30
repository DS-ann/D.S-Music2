package com.dsann.music.desktop

import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

/**
 * Local audio continues to use Java Sound (MP3SPI/VorbisSPI/JDK PCM codecs).
 * Online AAC/MP4 streams are played by the bundled JavaFX Media runtime, which
 * uses Windows native media decoders. The backend stays behind this class so
 * desktop UI does not depend on an audio implementation.
 */
internal class DesktopAudioPlayer : AutoCloseable {
    private val generation = AtomicLong(0L)
    private val gate = Object()

    @Volatile private var outputLine: SourceDataLine? = null
    @Volatile private var worker: Thread? = null
    @Volatile private var paused = true
    @Volatile private var remoteMode = false
    @Volatile private var remotePlayer: MediaPlayer? = null
    @Volatile private var remoteAudioPlayer: MediaPlayer? = null
    @Volatile private var onPlaybackCompleted: (() -> Unit)? = null

    fun setOnPlaybackCompleted(callback: (() -> Unit)?) {
        onPlaybackCompleted = callback
    }

    fun getRemoteMediaPlayer(): MediaPlayer? = remotePlayer

    /**
     * Snapshot the actual JavaFX media clock immediately before replacing the source.
     * The Compose progress state is polled, so it must not be used as a resume timestamp.
     */
    fun currentPositionSnapshot(): Long {
        if (!remoteMode) return positionMillis
        val activePlayer = remotePlayer ?: return positionMillis
        if (Platform.isFxApplicationThread()) {
            return activePlayer.currentTime.toMillis().toLong().coerceAtLeast(0L)
        }

        val snapshot = CompletableFuture<Long>()
        try {
            Platform.runLater {
                val current = if (remoteMode && remotePlayer === activePlayer) {
                    activePlayer.currentTime.toMillis().toLong()
                } else {
                    positionMillis
                }
                snapshot.complete(current.coerceAtLeast(0L))
            }
            return snapshot.get(900L, TimeUnit.MILLISECONDS).coerceAtLeast(0L)
        } catch (_: Exception) {
            return positionMillis.coerceAtLeast(0L)
        }
    }

    @Volatile var currentFile: File? = null
        private set
    @Volatile var currentUrl: String? = null
        private set
    @Volatile var positionMillis: Long = 0L
        private set
    @Volatile var durationMillis: Long = 0L
        private set
    @Volatile var isPlaying: Boolean = false
        private set
    @Volatile var isPaused: Boolean = true
        private set
    @Volatile var errorMessage: String? = null
        private set

    fun play(file: File, startPositionMs: Long = 0L, knownDurationMs: Long = 0L) {
        val extension = file.extension.lowercase()
        if (extension in setOf("m4a", "aac", "mp4")) {
            playUrl(file.toURI().toString(), knownDurationMs, startPositionMs)
            currentFile = file
            return
        }
        stop()
        val token = generation.incrementAndGet()
        currentFile = file
        currentUrl = null
        remoteMode = false
        positionMillis = startPositionMs.coerceAtLeast(0L)
        durationMillis = knownDurationMs.takeIf { it > 0L } ?: readDuration(file)
        errorMessage = null
        paused = false
        isPaused = false
        isPlaying = true

        worker = Thread(
            { runPlayback(file, startPositionMs.coerceAtLeast(0L), token) },
            "SpatialFlow-Audio"
        ).apply { isDaemon = true; start() }
    }

    /**
     * Plays a video-only DASH stream in the MediaView and its paired audio-only stream
     * through a second JavaFX MediaPlayer. Both players seek and start together.
     */
    fun playVideoAndAudio(
        videoUrl: String,
        audioUrl: String,
        knownDurationMs: Long = 0L,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = true
    ) {
        require(videoUrl.isNotBlank()) { "Video stream URL is empty." }
        require(audioUrl.isNotBlank()) { "Audio stream URL is empty." }
        stop()
        val token = generation.incrementAndGet()
        currentFile = null
        currentUrl = videoUrl
        remoteMode = true
        positionMillis = startPositionMs.coerceAtLeast(0L)
        durationMillis = knownDurationMs.coerceAtLeast(0L)
        errorMessage = null
        paused = !autoPlay
        isPaused = !autoPlay
        isPlaying = true

        ensureJavaFx()
        Platform.runLater {
            if (token != generation.get()) return@runLater
            var video: MediaPlayer? = null
            var audio: MediaPlayer? = null
            var videoReady = false
            var audioReady = false
            var started = false

            fun fail(message: String) {
                if (token != generation.get()) return
                errorMessage = message
                isPlaying = false
                paused = true
                isPaused = true
                runCatching { video?.stop() }
                runCatching { audio?.stop() }
            }

            fun startWhenReady() {
                if (!videoReady || !audioReady || started || token != generation.get()) return
                val videoPlayer = video ?: return
                val audioPlayer = audio ?: return
                started = true
                val duration = videoPlayer.totalDuration
                if (duration != null && duration.toMillis().isFinite() && duration.toMillis() > 0.0) {
                    durationMillis = duration.toMillis().toLong()
                }
                val target = startPositionMs.coerceAtLeast(0L)
                if (target > 0L) {
                    videoPlayer.seek(Duration.millis(target.toDouble()))
                    audioPlayer.seek(Duration.millis(target.toDouble()))
                }
                if (autoPlay) {
                    videoPlayer.play()
                    audioPlayer.play()
                    paused = false
                    isPaused = false
                } else {
                    videoPlayer.pause()
                    audioPlayer.pause()
                    paused = true
                    isPaused = true
                }
            }

            try {
                video = MediaPlayer(Media(videoUrl))
                audio = MediaPlayer(Media(audioUrl))
                remotePlayer = video
                remoteAudioPlayer = audio

                // The video-only stream has no soundtrack; the paired audio player supplies it.
                video.volume = 0.0
                audio.volume = 1.0

                video.currentTimeProperty().addListener { _, _, newTime ->
                    if (token == generation.get() && newTime != null && newTime.toMillis().isFinite()) {
                        positionMillis = newTime.toMillis().toLong().coerceAtLeast(0L)
                    }
                }
                video.setOnReady {
                    if (token == generation.get()) {
                        videoReady = true
                        startWhenReady()
                    }
                }
                audio.setOnReady {
                    if (token == generation.get()) {
                        audioReady = true
                        startWhenReady()
                    }
                }
                video.setOnError {
                    fail(video.error?.message ?: "Windows could not decode the selected video stream.")
                }
                audio.setOnError {
                    fail(audio.error?.message ?: "Windows could not decode the paired audio stream.")
                }
                video.setOnEndOfMedia {
                    if (token == generation.get()) {
                        runCatching { audio.stop() }
                        positionMillis = durationMillis
                        isPlaying = false
                        paused = true
                        isPaused = true
                        onPlaybackCompleted?.invoke()
                    }
                }
            } catch (error: Exception) {
                fail(error.message ?: "Unable to open the paired video and audio streams.")
            }
        }
    }

    fun playUrl(url: String, knownDurationMs: Long = 0L, startPositionMs: Long = 0L, autoPlay: Boolean = true) {
        stop()
        val token = generation.incrementAndGet()
        currentFile = null
        currentUrl = url
        remoteMode = true
        positionMillis = startPositionMs.coerceAtLeast(0L)
        durationMillis = knownDurationMs.coerceAtLeast(0L)
        errorMessage = null
        paused = false
        isPaused = false
        isPlaying = true

        ensureJavaFx()
        Platform.runLater {
            if (token != generation.get()) return@runLater
            try {
                val media = Media(url)
                val created = MediaPlayer(media)
                remotePlayer = created
                created.currentTimeProperty().addListener { _, _, newTime ->
                    if (token == generation.get() && newTime != null && newTime.toMillis().isFinite()) {
                        positionMillis = newTime.toMillis().toLong().coerceAtLeast(0L)
                    }
                }
                created.setOnReady {
                    if (token != generation.get()) return@setOnReady
                    val mediaDuration = created.totalDuration
                    if (mediaDuration != null && mediaDuration.toMillis().isFinite() && mediaDuration.toMillis() > 0.0) {
                        durationMillis = mediaDuration.toMillis().toLong()
                    }
                    if (startPositionMs > 0L) created.seek(Duration.millis(startPositionMs.toDouble()))
                    if (autoPlay) {
                        created.play()
                        paused = false
                        isPaused = false
                    } else {
                        created.pause()
                        paused = true
                        isPaused = true
                    }
                }
                created.setOnError {
                    if (token == generation.get()) {
                        errorMessage = created.error?.message ?: media.error?.message ?: "Windows media decoder could not play this stream."
                        isPlaying = false
                        paused = true
                        isPaused = true
                    }
                }
                created.setOnEndOfMedia {
                    if (token == generation.get()) {
                        positionMillis = durationMillis
                        isPlaying = false
                        paused = true
                        isPaused = true
                        onPlaybackCompleted?.invoke()
                    }
                }
            } catch (error: Exception) {
                if (token == generation.get()) {
                    errorMessage = error.message ?: "Unable to open the online audio stream."
                    isPlaying = false
                    paused = true
                    isPaused = true
                }
            }
        }
    }

    fun pause() {
        if (!isPlaying || paused) return
        paused = true
        isPaused = true
        if (remoteMode) {
            Platform.runLater {
                remotePlayer?.pause()
                remoteAudioPlayer?.pause()
            }
        } else {
            try {
                outputLine?.let { it.stop(); it.flush() }
            } catch (_: Exception) { }
        }
    }

    fun resume() {
        if (!isPlaying || !paused) return
        paused = false
        isPaused = false
        if (remoteMode) {
            Platform.runLater {
                remotePlayer?.play()
                remoteAudioPlayer?.play()
            }
        } else {
            try { outputLine?.start() } catch (_: Exception) { }
            synchronized(gate) { gate.notifyAll() }
        }
    }

    fun togglePlayback() {
        if (paused) resume() else pause()
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        if (remoteMode) {
            Platform.runLater {
                remotePlayer?.seek(Duration.millis(target.toDouble()))
                remoteAudioPlayer?.seek(Duration.millis(target.toDouble()))
                positionMillis = target
            }
        } else {
            currentFile?.let { play(it, target) }
        }
    }

    fun stop() {
        generation.incrementAndGet()
        paused = true
        isPaused = true
        synchronized(gate) { gate.notifyAll() }

        if (remoteMode) {
            val old = remotePlayer
            val oldAudio = remoteAudioPlayer
            remotePlayer = null
            remoteAudioPlayer = null
            try {
                Platform.runLater {
                    old?.stop()
                    old?.dispose()
                    oldAudio?.stop()
                    oldAudio?.dispose()
                }
            } catch (_: Exception) { }
        }
        remoteMode = false
        val line = outputLine
        outputLine = null
        try { line?.stop(); line?.flush(); line?.close() } catch (_: Exception) { }
        worker?.interrupt()
        worker = null
        isPlaying = false
        positionMillis = 0L
        currentUrl = null
    }

    private fun ensureJavaFx() {
        try {
            Platform.startup { Platform.setImplicitExit(false) }
        } catch (_: IllegalStateException) {
            Platform.setImplicitExit(false)
        }
    }

    private fun runPlayback(file: File, startPositionMs: Long, token: Long) {
        var localLine: SourceDataLine? = null
        var reachedEnd = false
        try {
            AudioSystem.getAudioInputStream(file).use { encoded ->
                val sourceFormat = encoded.format
                val sampleRate = sourceFormat.sampleRate.takeIf { it.isFinite() && it > 0f } ?: 44_100f
                val channels = sourceFormat.channels.takeIf { it in 1..2 }
                    ?: throw IOException("Only mono and stereo audio are supported by this local playback backend.")
                val targetFormat = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16, channels, channels * 2, sampleRate, false)
                val alreadyPcm = sourceFormat.encoding == AudioFormat.Encoding.PCM_SIGNED &&
                    sourceFormat.sampleSizeInBits == 16 && sourceFormat.channels == channels &&
                    sourceFormat.frameSize == channels * 2 && !sourceFormat.isBigEndian
                val decoded = if (alreadyPcm) encoded else AudioSystem.getAudioInputStream(targetFormat, encoded)
                decoded.use { pcm ->
                    val bytesPerSecond = targetFormat.frameRate.toDouble() * targetFormat.frameSize
                    skipFully(pcm, (startPositionMs.toDouble() * bytesPerSecond / 1000.0).toLong())
                    if (token != generation.get()) return
                    localLine = AudioSystem.getLine(DataLine.Info(SourceDataLine::class.java, targetFormat)) as SourceDataLine
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
                        if (count < 0) {
                            reachedEnd = true
                            break
                        }
                        var offset = 0
                        while (offset < count && token == generation.get()) {
                            awaitResume(token)
                            if (token != generation.get()) break
                            val written = localLine!!.write(buffer, offset, count - offset)
                            if (written <= 0) break
                            offset += written
                            bytesWritten += written
                            if (bytesPerSecond > 0.0) {
                                positionMillis = startPositionMs + (bytesWritten * 1000.0 / bytesPerSecond).toLong()
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
            if (token == generation.get()) errorMessage = error.message ?: "Unable to play this audio file."
        } finally {
            try { localLine?.stop(); localLine?.close() } catch (_: Exception) { }
            if (outputLine === localLine) outputLine = null
            if (token == generation.get()) {
                isPlaying = false
                paused = true
                isPaused = true
                if (reachedEnd) onPlaybackCompleted?.invoke()
            }
        }
    }

    private fun awaitResume(token: Long) {
        synchronized(gate) {
            while (paused && token == generation.get()) {
                try { gate.wait() } catch (_: InterruptedException) {
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
            if (skipped > 0L) remaining -= skipped
            else {
                val read = stream.read(scratch, 0, minOf(scratch.size.toLong(), remaining).toInt())
                if (read < 0) break
                remaining -= read
            }
        }
    }

    private fun readDuration(file: File): Long = try {
        val audioFormat = AudioSystem.getAudioFileFormat(file)
        val frames = audioFormat.frameLength
        val frameRate = audioFormat.format.frameRate
        if (frames > 0 && frameRate.isFinite() && frameRate > 0f) (frames * 1000.0 / frameRate).toLong() else 0L
    } catch (_: Exception) { 0L }

    override fun close() { stop() }
}
