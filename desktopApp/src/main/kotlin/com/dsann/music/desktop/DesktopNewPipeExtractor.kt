package com.dsann.music.desktop

import okhttp3.OkHttpClient
import okhttp3.Request as OkRequest
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.Locale
import kotlin.math.abs

internal data class DesktopResolvedStream(
    val url: String,
    val mimeType: String,
    val codec: String?,
    val bitrateKbps: Int,
    val durationMs: Long,
    val sizeBytes: Long?
)

internal data class DesktopVideoOption(
    val url: String,
    val label: String,
    val height: Int,
    val mimeType: String,
    val bitrateKbps: Int,
    val sizeBytes: Long?,
    val audioUrl: String? = null,
    val audioMimeType: String? = null
) {
    val usesSeparateAudio: Boolean get() = !audioUrl.isNullOrBlank()
}

internal object DesktopNewPipeExtractor {
    private val cache = ConcurrentHashMap<String, Pair<DesktopResolvedStream, Long>>()
    private val videoOptionsCache = ConcurrentHashMap<String, Pair<List<DesktopVideoOption>, Long>>()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile private var initialized = false

    @Synchronized
    private fun ensureInitialized() {
        if (initialized) return
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                val builder = OkRequest.Builder().url(request.url())
                    .header("User-Agent", USER_AGENT)
                request.headers().forEach { (key, values) ->
                    values.forEach { value -> builder.addHeader(key, value) }
                }
                val data = request.dataToSend()
                val httpRequest = builder.method(
                    request.httpMethod(),
                    data?.toRequestBody()
                ).build()

                http.newCall(httpRequest).execute().use { response ->
                    val headers = response.headers.names().associateWith { response.headers.values(it) }
                    return Response(
                        response.code,
                        response.message,
                        headers,
                        response.body?.string().orEmpty(),
                        response.request.url.toString()
                    )
                }
            }
        })
        initialized = true
    }

    suspend fun resolve(videoId: String, targetBitrateKbps: Int = 256, preferredFormat: String = "m4a"): DesktopResolvedStream {
        val cacheKey = "$videoId#${preferredFormat.lowercase(Locale.ROOT)}#$targetBitrateKbps"
        val cached = cache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.second < CACHE_TTL_MS) {
            return cached.first
        }

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            ensureInitialized()
            val info = StreamInfo.getInfo(
                ServiceList.YouTube,
                "https://www.youtube.com/watch?v=$videoId"
            )
            val audioStreams = info.audioStreams.filter { it.content.startsWith("http", true) }
            check(audioStreams.isNotEmpty()) { "YouTube did not provide an audio-only stream for this track." }

            // Prefer AAC/M4A: JavaFX Media on Windows decodes AAC/MP3 streams without an external codec install.
            val aac = audioStreams.filter { stream ->
                val mime = stream.format?.mimeType.orEmpty()
                mime.contains("audio/mp4", true) ||
                    stream.codec?.contains("mp4a", true) == true ||
                    stream.itag == 140
            }
            val requestedCandidates = when (preferredFormat.lowercase(Locale.ROOT)) {
                "m4a", "aac" -> aac
                "mp3" -> audioStreams.filter { it.format?.mimeType.orEmpty().contains("mpeg", true) || it.codec?.contains("mp3", true) == true }
                else -> audioStreams
            }
            // If the chosen container is unavailable, fall back to the Windows-native AAC-capable stream.
            val candidates = requestedCandidates.ifEmpty { aac.ifEmpty { audioStreams } }
            val selected = candidates.minByOrNull { stream ->
                val kbps = stream.averageBitrate.takeIf { it > 0 }
                    ?: (stream.bitrate / 1000).takeIf { it > 0 }
                    ?: 0
                if (kbps > 0) abs(kbps - targetBitrateKbps) else Int.MAX_VALUE
            } ?: error("No compatible audio stream was found.")

            val mime = selected.format?.mimeType?.substringBefore(';')?.trim().orEmpty()
            val bitrate = selected.averageBitrate.takeIf { it > 0 }
                ?: (selected.bitrate / 1000).takeIf { it > 0 }
                ?: 0
            val result = DesktopResolvedStream(
                url = selected.content,
                mimeType = mime,
                codec = selected.codec,
                bitrateKbps = bitrate,
                durationMs = info.duration * 1000L,
                sizeBytes = selected.itagItem?.contentLength?.takeIf { it > 0L }
            )
            cache[cacheKey] = result to System.currentTimeMillis()
            result
        }
    }

    /**
     * Returns all available muxed video+audio formats. Video-only DASH tracks are excluded
     * because the desktop player keeps one MediaPlayer for synchronized picture and sound.
     */
    suspend fun getVideoOptions(videoId: String): List<DesktopVideoOption> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val cached = videoOptionsCache[videoId]
            if (cached != null && System.currentTimeMillis() - cached.second < CACHE_TTL_MS) {
                return@withContext cached.first
            }
            ensureInitialized()
            val info = StreamInfo.getInfo(
                ServiceList.YouTube,
                "https://www.youtube.com/watch?v=$videoId"
            )

            val muxed = info.videoStreams
                .asSequence()
                .filter { !it.isVideoOnly() && it.content.startsWith("http", true) }
                .mapNotNull { stream ->
                    val resolution = stream.getResolution()
                    val height = resolution.filter { it.isDigit() }.toIntOrNull() ?: return@mapNotNull null
                    val mime = stream.format?.mimeType?.substringBefore(';')?.trim().orEmpty()
                    DesktopVideoOption(
                        url = stream.content,
                        label = "${height}p",
                        height = height,
                        mimeType = mime,
                        bitrateKbps = (stream.getBitrate() / 1000).takeIf { it > 0 } ?: 0,
                        sizeBytes = stream.itagItem?.contentLength?.takeIf { it > 0L }
                    )
                }
                .toList()

            // YouTube may publish only DASH video-only formats. Pair those with the smallest
            // AAC/M4A audio-only format so the JavaFX video surface can still play sound.
            val audioStreams = info.audioStreams.filter { it.content.startsWith("http", true) }
            val aac = audioStreams.filter {
                it.format?.mimeType.orEmpty().contains("audio/mp4", true) ||
                    it.codec?.contains("mp4a", true) == true ||
                    it.itag == 140
            }
            val audioCandidates = aac.ifEmpty { audioStreams }
            val audio = audioCandidates.minByOrNull { stream ->
                val bitrate = stream.averageBitrate.takeIf { it > 0 }
                    ?: (stream.bitrate / 1000).takeIf { it > 0 }
                    ?: Int.MAX_VALUE
                bitrate
            }

            val separate = if (audio == null) emptyList() else info.videoOnlyStreams
                .asSequence()
                .filter { it.content.startsWith("http", true) }
                .mapNotNull { stream ->
                    val resolution = stream.getResolution()
                    val height = resolution.filter { it.isDigit() }.toIntOrNull() ?: return@mapNotNull null
                    val mime = stream.format?.mimeType?.substringBefore(';')?.trim().orEmpty()
                    DesktopVideoOption(
                        url = stream.content,
                        label = "${height}p",
                        height = height,
                        mimeType = mime,
                        bitrateKbps = (stream.getBitrate() / 1000).takeIf { it > 0 } ?: 0,
                        sizeBytes = stream.itagItem?.contentLength?.takeIf { it > 0L },
                        audioUrl = audio.content,
                        audioMimeType = audio.format?.mimeType?.substringBefore(';')?.trim()
                    )
                }
                .toList()

            // One option per resolution. Prefer one muxed HTTP stream for lower request
            // overhead; otherwise use separate video + audio DASH streams.
            val all = (muxed + separate)
                .groupBy { it.height }
                .mapNotNull { (_, sameHeight) ->
                    sameHeight.firstOrNull {
                        !it.usesSeparateAudio && it.mimeType.contains("video/mp4", true)
                    }
                        ?: sameHeight.firstOrNull { !it.usesSeparateAudio }
                        ?: sameHeight.firstOrNull {
                            it.usesSeparateAudio && it.mimeType.contains("video/mp4", true)
                        }
                        ?: sameHeight.firstOrNull()
                }

            val mp4 = all.filter { it.mimeType.contains("video/mp4", true) }
            val options = (mp4.ifEmpty { all }).sortedByDescending { it.height }
            videoOptionsCache[videoId] = options to System.currentTimeMillis()
            options
        }

    /**
     * Resolve the chosen muxed format. An unspecified preference uses the lowest available
     * resolution to minimize data usage; the player exposes manual quality choices in-video.
     */
    suspend fun resolveVideo(videoId: String, targetHeight: Int? = null): DesktopResolvedStream {
        val options = getVideoOptions(videoId)
        val selected = if (targetHeight == null || targetHeight <= 0) {
            options.lastOrNull()
        } else {
            options.minByOrNull { kotlin.math.abs(it.height - targetHeight) }
        } ?: error("YouTube did not provide a muxed video+audio stream for this track.")
        val durationMs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            ensureInitialized()
            StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId").duration * 1000L
        }
        return DesktopResolvedStream(
            url = selected.url,
            mimeType = selected.mimeType,
            codec = null,
            bitrateKbps = selected.bitrateKbps,
            durationMs = durationMs,
            sizeBytes = selected.sizeBytes
        )
    }

    fun invalidate(videoId: String) {
        cache.keys.removeIf { it.startsWith("$videoId#") }
        videoOptionsCache.remove(videoId)
    }

    private const val CACHE_TTL_MS = 20 * 60 * 1000L
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
}
