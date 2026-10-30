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
import kotlin.math.abs

internal data class DesktopResolvedStream(
    val url: String,
    val mimeType: String,
    val codec: String?,
    val bitrateKbps: Int,
    val durationMs: Long,
    val sizeBytes: Long?
)

internal object DesktopNewPipeExtractor {
    private val cache = ConcurrentHashMap<String, Pair<DesktopResolvedStream, Long>>()
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

    suspend fun resolve(videoId: String, targetBitrateKbps: Int = 256): DesktopResolvedStream {
        val cached = cache[videoId]
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
            val candidates = aac.ifEmpty { audioStreams }
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
            cache[videoId] = result to System.currentTimeMillis()
            result
        }
    }

    fun invalidate(videoId: String) {
        cache.remove(videoId)
    }

    private const val CACHE_TTL_MS = 20 * 60 * 1000L
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
}
