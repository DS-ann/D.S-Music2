package com.codetrio.spatialflow.data.innertube

import android.content.Context
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.*
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.ConcurrentHashMap

/**
 * Optimized Local YouTube stream extraction using NewPipe Extractor.
 * Implements multi-level caching, request deduplication, batching, and preference caching.
 */
object NewPipeStreamExtractor {

    private const val TAG = "NewPipeExtractor"
    @Volatile private var isInitialized = false
    @Volatile private var appContext: Context? = null

    // === Multi-Level Caching ===
    // Tier 1: Fastest. Just the URL.
    private val streamUrlCache = LruCache<String, String>(500)
    typealias CachedStreamInfo = StreamInfo
    // Tier 2: Slowest to fetch, but fast to re-parse.
    private val streamInfoCache = LruCache<String, CachedStreamInfo>(200)
    // Tier 3: Parsed UI data.
    private val playerResultCache = LruCache<String, PlayerResult>(200)

    private val cacheLock = Any()

    // === Request Deduplication ===
    private val inFlightRequests = ConcurrentHashMap<String, Deferred<String?>>()
    private val inFlightPlayerResults = ConcurrentHashMap<String, Deferred<PlayerResult?>>()

    private val extractorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // === Preference Caching ===
    private data class PreferenceSnapshot(
        val audioQuality: String,
        val dataSaver: Boolean,
        val timestamp: Long
    )
    private var cachedPrefs: PreferenceSnapshot? = null
    private const val PREF_CACHE_TTL_MS = 60000L // 1 minute

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        try {
            NewPipe.init(NewPipeDownloader.getInstance())
            isInitialized = true
            Log.d(TAG, "NewPipe Extractor initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize NewPipe Extractor", e)
        }
    }

    fun getPreferencesSnapshot(): Pair<String, Boolean> {
        val now = System.currentTimeMillis()
        val cached = cachedPrefs
        
        if (cached != null && (now - cached.timestamp) < PREF_CACHE_TTL_MS) {
            return cached.audioQuality to cached.dataSaver
        }
        
        val prefs = try {
            appContext?.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        } catch (e: Exception) { null }
        
        val quality = prefs?.getString("audio_quality", "High") ?: "High"
        val dataSaver = prefs?.getBoolean("data_saver", false) ?: false
        
        cachedPrefs = PreferenceSnapshot(quality, dataSaver, now)
        return quality to dataSaver
    }

    fun clearCache() {
        streamUrlCache.evictAll()
        streamInfoCache.evictAll()
        playerResultCache.evictAll()
        cachedPrefs = null
    }

    fun clearVideoCache(videoId: String) {
        streamUrlCache.remove(videoId)
        streamInfoCache.remove(videoId)
        playerResultCache.remove(videoId)
    }

    /**
     * Internal generic fetcher that populates the streamInfoCache.
     */
    private suspend fun fetchStreamInfo(videoId: String): StreamInfo? {
        // Application startup initializes NewPipe off the UI thread. If a track is requested
        // before that worker finishes, initialize it here on the extraction dispatcher.
        if (!isInitialized) {
            withContext(Dispatchers.IO) {
                if (!isInitialized) {
                    val context = appContext ?: com.codetrio.spatialflow.SpatialFlowApplication.instance.applicationContext
                    init(context)
                }
            }
        }
        val cachedInfo = synchronized(cacheLock) { streamInfoCache.get(videoId) }
        if (cachedInfo != null) return cachedInfo

        return try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val infoStart = System.currentTimeMillis()
            val info = StreamInfo.getInfo(ServiceList.YouTube, url)
            Log.d(TAG, "StreamInfo.getInfo took ${System.currentTimeMillis() - infoStart}ms for $videoId")
            synchronized(cacheLock) { streamInfoCache.put(videoId, info) }
            info
        } catch (e: Exception) {
            Log.e(TAG, "Stream extraction failed for $videoId: ${e.message}")
            null
        }
    }

    private fun selectBestStream(audioStreams: List<AudioStream>): AudioStream? {
        val selectStart = System.currentTimeMillis()
        val (audioQuality, dataSaver) = getPreferencesSnapshot()
        
        if (audioStreams.isEmpty()) return null

        val best = when {
            audioQuality == "Data Saver" || dataSaver -> audioStreams.minByOrNull { maxOf(it.bitrate, it.averageBitrate) }
            audioQuality == "Normal" -> {
                val sorted = audioStreams.sortedBy { maxOf(it.bitrate, it.averageBitrate) }
                sorted.elementAtOrNull(sorted.size / 2)
            }
            else -> audioStreams.maxByOrNull { maxOf(it.bitrate, it.averageBitrate) }
        }
        Log.d(TAG, "selectBestStream took ${System.currentTimeMillis() - selectStart}ms")
        return best
    }

    /**
     * Extract the best audio stream URL for a YouTube video.
     */
    suspend fun getStreamUrl(videoId: String?): String? = coroutineScope {
        if (videoId.isNullOrEmpty()) return@coroutineScope null
        
        val cachedUrl = synchronized(cacheLock) { streamUrlCache.get(videoId) }
        if (cachedUrl != null) {
            Log.d(TAG, "Cache HIT (URL) for $videoId")
            return@coroutineScope cachedUrl
        }

        // Deduplication
        val deferred = synchronized(inFlightRequests) {
            inFlightRequests[videoId] ?: extractorScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                try {
                    val start = System.currentTimeMillis()
                    val info = fetchStreamInfo(videoId)
                    if (info == null) return@async null

                    var bestUrl: String? = null
                    val audioStreams = info.audioStreams
                    if (audioStreams.isEmpty()) {
                        Log.w(TAG, "No audio-only streams found for $videoId, trying muxed video/audio streams")
                        val anyMuxed = info.videoStreams.firstOrNull()
                        bestUrl = anyMuxed?.content
                    } else {
                        val best = selectBestStream(audioStreams)
                        bestUrl = best?.content
                    }

                    if (bestUrl != null) {
                        synchronized(cacheLock) { streamUrlCache.put(videoId, bestUrl) }
                    }
                    Log.d(TAG, "Stream extraction took ${System.currentTimeMillis() - start}ms for $videoId")
                    bestUrl
                } finally {
                    inFlightRequests.remove(videoId)
                }
            }.also {
                inFlightRequests[videoId] = it
                it.start()
            }
        }

        return@coroutineScope deferred.await()
    }

    /**
     * Resolve a conservative recovery stream after the preferred stream failed in Media3.
     * Prefer AAC in an MP4 container for broad decoder compatibility, especially on Android 6;
     * if YouTube offers no AAC audio-only stream, use the lowest-bitrate audio-only stream.
     * The caller invalidates the video cache before calling this method.
     */
    suspend fun getFallbackAudioStreamUrl(videoId: String?): String? = coroutineScope {
        if (videoId.isNullOrBlank()) return@coroutineScope null

        val info = fetchStreamInfo(videoId) ?: return@coroutineScope null
        val audioStreams = info.audioStreams
        if (audioStreams.isEmpty()) return@coroutineScope null

        val aacStreams = audioStreams.filter { stream ->
            val mimeType = stream.format?.mimeType.orEmpty()
            mimeType.startsWith("audio/mp4", ignoreCase = true) ||
                stream.codec?.contains("mp4a", ignoreCase = true) == true ||
                stream.itag == 140
        }
        val selected = aacStreams.maxByOrNull { maxOf(it.bitrate, it.averageBitrate) }
            ?: audioStreams.minByOrNull { maxOf(it.bitrate, it.averageBitrate) }

        val url = selected?.content?.takeIf { it.startsWith("https://", ignoreCase = true) }
        Log.i(
            TAG,
            "Selected compatible recovery format for $videoId: " +
                "itag=${selected?.itag}, mime=${selected?.format?.mimeType}, codec=${selected?.codec}"
        )
        url
    }

    /**
     * Extract full stream info including metadata lazily.
     */
    suspend fun getPlayerResult(videoId: String?): PlayerResult? = coroutineScope {
        if (videoId.isNullOrEmpty()) return@coroutineScope null
        
        val cachedResult = synchronized(cacheLock) { playerResultCache.get(videoId) }
        if (cachedResult != null) {
            Log.d(TAG, "Cache HIT (PlayerResult) for $videoId")
            return@coroutineScope cachedResult
        }

        val deferred = synchronized(inFlightPlayerResults) {
            inFlightPlayerResults[videoId] ?: extractorScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                try {
                    val info = fetchStreamInfo(videoId)
                    if (info == null) return@async null
                    
                    val audioStreams = info.audioStreams
                    if (audioStreams.isEmpty()) return@async null

                    val bestStream = selectBestStream(audioStreams) ?: return@async null
                    
                    val creationStart = System.currentTimeMillis()
                    val streams = listOf(
                        StreamData(
                            url = bestStream.content,
                            mimeType = bestStream.format?.mimeType ?: "audio/mp4",
                            bitrate = bestStream.averageBitrate,
                            contentLength = null,
                            audioQuality = when {
                                bestStream.averageBitrate >= 256 -> "AUDIO_QUALITY_HIGH"
                                bestStream.averageBitrate >= 128 -> "AUDIO_QUALITY_MEDIUM"
                                else -> "AUDIO_QUALITY_LOW"
                            }
                        )
                    )

                    val thumbStart = System.currentTimeMillis()
                    val thumbnailUrl = info.thumbnails.lastOrNull()?.url?.let {
                        InnerTubeParser.getHighResThumbnailUrl(it)
                    }
                    Log.d(TAG, "Thumbnail extraction took ${System.currentTimeMillis() - thumbStart}ms for $videoId")

                    val likesFormatted = try {
                        val likeCount = info.likeCount
                        if (likeCount > 0) formatLikesCount(likeCount) else null
                    } catch (_: Exception) {
                        null
                    }

                    val result = PlayerResult(
                        videoId = videoId,
                        title = info.name ?: "Unknown",
                        artist = info.uploaderName ?: "Unknown Artist",
                        thumbnailUrl = thumbnailUrl,
                        durationMs = info.duration * 1000,
                        streams = streams,
                        likesCount = likesFormatted
                    )
                    Log.d(TAG, "PlayerResult creation took ${System.currentTimeMillis() - creationStart}ms for $videoId")
                    synchronized(cacheLock) {
                        playerResultCache.put(videoId, result)
                        // Also seed the URL cache while we're at it!
                        streamUrlCache.put(videoId, bestStream.content)
                    }
                    
                    result
                } finally {
                    inFlightPlayerResults.remove(videoId)
                }
            }.also {
                inFlightPlayerResults[videoId] = it
                it.start()
            }
        }

        return@coroutineScope deferred.await()
    }

    /**
     * Batch extraction with controlled concurrency.
     */
    suspend fun extractBatch(videoIds: List<String>, batchSize: Int = 4): Map<String, String> = coroutineScope {
        val results = ConcurrentHashMap<String, String>()
        
        videoIds.chunked(batchSize).forEach { batch ->
            val deferreds = batch.map { id ->
                async {
                    val url = getStreamUrl(id)
                    if (url != null) results[id] = url
                }
            }
            deferreds.awaitAll()
        }
        
        return@coroutineScope results
    }

    enum class PrefetchPriority { LOW, MEDIUM }

    /**
     * Prefetch a batch of items in the background.
     */
    fun prefetchBatch(videoIds: List<String>, priority: PrefetchPriority = PrefetchPriority.LOW) {
        val size = when (priority) {
            PrefetchPriority.LOW -> 2
            PrefetchPriority.MEDIUM -> 4
        }
        extractorScope.launch {
            extractBatch(videoIds, batchSize = size)
        }
    }

    private fun formatLikesCount(count: Long): String {
        if (count <= 0) return "0"
        if (count < 1000) return count.toString()
        if (count < 1000000) {
            val k = count / 1000.0
            return if (k >= 100.0) {
                String.format(java.util.Locale.US, "%.0fK", k)
            } else {
                String.format(java.util.Locale.US, "%.1fK", k).replace(".0", "")
            }
        }
        val m = count / 1000000.0
        return if (m >= 100.0) {
            String.format(java.util.Locale.US, "%.0fM", m)
        } else {
            String.format(java.util.Locale.US, "%.1fM", m).replace(".0", "")
        }
    }
}
