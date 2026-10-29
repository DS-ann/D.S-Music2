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
import kotlin.math.abs

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
        val playbackFormat: String,
        val downloadFormat: String,
        val downloadBitrate: String,
        val timestamp: Long
    )
    private var cachedPrefs: PreferenceSnapshot? = null
    private const val PREF_CACHE_TTL_MS = 60000L // 1 minute

    private val supportedBitratesKbps = listOf(48, 64, 96, 128, 160, 192, 256, 320)

    fun normalizeAudioQuality(quality: String?): String {
        val raw = quality?.trim().orEmpty()
        val parsed = when (raw) {
            "High" -> 256
            "Normal" -> 128
            "Data Saver" -> 64
            else -> raw.removeSuffix("kbps").trim().toIntOrNull()
                ?: raw.removeSuffix(" kbps").trim().toIntOrNull()
        }
        return "${parsed?.takeIf { it in supportedBitratesKbps } ?: 256} kbps"
    }

    fun normalizeAudioFormat(format: String?): String = when (
        format?.trim()?.lowercase()?.replace('_', '-')?.replace(' ', '-')
    ) {
        "m4a", "aac", "m4a/aac", "mp4" -> "m4a"
        "webm" -> "webm"
        "opus", "ogg/opus" -> "opus"
        "mp3", "mpeg" -> "mp3"
        "ogg", "vorbis" -> "ogg"
        else -> "auto"
    }

    private fun getPreferenceSnapshot(): PreferenceSnapshot {
        val now = System.currentTimeMillis()
        val cached = cachedPrefs
        if (cached != null && now - cached.timestamp < PREF_CACHE_TTL_MS) return cached

        val prefs = try {
            appContext?.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        }
        val snapshot = PreferenceSnapshot(
            audioQuality = normalizeAudioQuality(prefs?.getString("audio_quality", "256 kbps")),
            dataSaver = prefs?.getBoolean("data_saver", false) ?: false,
            playbackFormat = normalizeAudioFormat(prefs?.getString("audio_format", "auto")),
            downloadFormat = normalizeAudioFormat(prefs?.getString("download_audio_format", "auto")),
            downloadBitrate = normalizeAudioQuality(prefs?.getString("download_bitrate", "256 kbps")),
            timestamp = now
        )
        cachedPrefs = snapshot
        return snapshot
    }

    fun getPreferencesSnapshot(): Pair<String, Boolean> {
        val snapshot = getPreferenceSnapshot()
        return snapshot.audioQuality to snapshot.dataSaver
    }

    fun getPlaybackAudioFormat(): String = getPreferenceSnapshot().playbackFormat

    fun getDownloadAudioFormat(): String = getPreferenceSnapshot().downloadFormat

    fun getDownloadTargetBitrateKbps(): Int =
        getPreferenceSnapshot().downloadBitrate.substringBefore(' ').toIntOrNull() ?: 256

    fun getTargetBitrateKbps(quality: String? = null): Int =
        normalizeAudioQuality(quality ?: getPreferencesSnapshot().first)
            .substringBefore(' ')
            .toIntOrNull()
            ?: 256

    fun getTargetBitrateBps(): Int = getTargetBitrateKbps() * 1000

    fun matchesAudioFormat(
        mimeType: String?,
        codec: String? = null,
        preferredFormat: String = getPlaybackAudioFormat()
    ): Boolean {
        val format = normalizeAudioFormat(preferredFormat)
        if (format == "auto") return true

        val mime = mimeType.orEmpty().lowercase()
        val codecName = codec.orEmpty().lowercase()
        return when (format) {
            "m4a" -> mime.contains("audio/mp4") || mime.contains("mp4a") || codecName.contains("mp4a")
            "webm" -> mime.contains("webm")
            "opus" -> mime.contains("opus") || codecName.contains("opus")
            "mp3" -> mime.contains("audio/mpeg") || mime.contains("mp3") || codecName.contains("mp3")
            "ogg" -> mime.contains("audio/ogg") || mime.contains("ogg") || codecName.contains("vorbis")
            else -> true
        }
    }

    private fun isM4aAudio(stream: AudioStream): Boolean {
        val mime = stream.format?.mimeType.orEmpty()
        return mime.startsWith("audio/mp4", ignoreCase = true) ||
            stream.codec?.contains("mp4a", ignoreCase = true) == true ||
            stream.itag == 140
    }

    private fun candidatesForFormat(
        audioStreams: List<AudioStream>,
        preferredFormat: String
    ): List<AudioStream> {
        if (audioStreams.isEmpty()) return emptyList()
        val normalizedFormat = normalizeAudioFormat(preferredFormat)
        return when (normalizedFormat) {
            "auto" -> audioStreams.filter(::isM4aAudio).ifEmpty { audioStreams }
            else -> audioStreams.filter { stream ->
                matchesAudioFormat(stream.format?.mimeType, stream.codec, normalizedFormat)
            }
        }
    }

    private fun streamBitrateKbps(stream: AudioStream): Int {
        // NewPipe average bitrate is in kbps; precise itag bitrate is bits/sec.
        val averageKbps = stream.averageBitrate
        if (averageKbps > 0) return averageKbps
        val rawBitrate = stream.bitrate
        return when {
            rawBitrate > 1000 -> (rawBitrate + 500) / 1000
            rawBitrate > 0 -> rawBitrate
            else -> 0
        }
    }

    private fun qualityLabelForKbps(bitrateKbps: Int): String = when {
        bitrateKbps >= 192 -> "AUDIO_QUALITY_HIGH"
        bitrateKbps >= 96 -> "AUDIO_QUALITY_MEDIUM"
        else -> "AUDIO_QUALITY_LOW"
    }

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

    private fun selectBestStream(
        audioStreams: List<AudioStream>,
        preferredFormat: String = getPlaybackAudioFormat(),
        targetKbps: Int = getTargetBitrateKbps()
    ): AudioStream? {
        val selectStart = System.currentTimeMillis()
        if (audioStreams.isEmpty()) return null

        val candidates = candidatesForFormat(audioStreams, preferredFormat)
        val best = candidates.minByOrNull { stream ->
            val candidateKbps = streamBitrateKbps(stream)
            if (candidateKbps > 0) abs(candidateKbps - targetKbps) else Int.MAX_VALUE
        }
        Log.d(
            TAG,
            "selectBestStream format=$preferredFormat target=${targetKbps}kbps " +
                "took ${System.currentTimeMillis() - selectStart}ms"
        )
        return best
    }

    private fun choiceOption(stream: AudioStream, index: Int): AvailableAudioFormat {
        val mimeType = stream.format?.mimeType?.substringBefore(';')?.trim().orEmpty()
        val bitrateKbps = streamBitrateKbps(stream)
        return AvailableAudioFormat(
            id = "newpipe:${stream.itag}:${mimeType}:${bitrateKbps}:$index",
            url = stream.content,
            mimeType = mimeType,
            codec = stream.codec,
            bitrateKbps = bitrateKbps,
            contentLength = stream.itagItem?.contentLength?.takeIf { it > 0L },
            formatLabel = audioFormatDisplayLabel(mimeType, stream.codec)
        )
    }

    private suspend fun selectStreamForOperation(
        videoId: String,
        title: String,
        audioStreams: List<AudioStream>,
        preferredFormat: String,
        targetKbps: Int,
        purpose: AudioFormatPurpose,
        allowFormatChoice: Boolean
    ): AudioStream? {
        val requestedFormat = normalizeAudioFormat(preferredFormat)
        val candidates = candidatesForFormat(audioStreams, requestedFormat)
            .filter { it.content.startsWith("https://", ignoreCase = true) }
        if (candidates.isNotEmpty()) {
            return candidates.minByOrNull { stream ->
                val bitrate = streamBitrateKbps(stream)
                if (bitrate > 0) kotlin.math.abs(bitrate - targetKbps) else Int.MAX_VALUE
            }
        }
        if (requestedFormat == "auto") return null
        if (!allowFormatChoice) throw RequestedAudioFormatUnavailableException(requestedFormat)

        // A missing requested format is a user-choice case, not a silent fallback.
        // Only display formats for which extraction returned a usable stream URL.
        val availableStreams = audioStreams.filter {
            it.content.startsWith("https://", ignoreCase = true)
        }
        if (availableStreams.isEmpty()) {
            throw RequestedAudioFormatUnavailableException(requestedFormat)
        }
        Log.w(
            TAG,
            "Requested $purpose format '$requestedFormat' unavailable for $videoId; " +
                "asking the user to choose from ${availableStreams.size} available streams"
        )
        val optionPairs = availableStreams.mapIndexed { index, stream -> stream to choiceOption(stream, index) }
            .sortedWith(compareByDescending<Pair<AudioStream, AvailableAudioFormat>> { it.second.bitrateKbps }
                .thenBy { it.second.formatLabel })
        val selected = AudioFormatSelectionCoordinator.choose(
            songTitle = title,
            purpose = purpose,
            requestedFormat = requestedFormat,
            targetBitrateKbps = targetKbps,
            options = optionPairs.map { it.second }
        )
        return optionPairs.firstOrNull { it.second.id == selected.id }?.first
    }

    /**
     * Extract the best audio stream URL for a YouTube video. If the requested format is missing,
     * interactive playback can select from formats that this track actually exposes.
     */
    suspend fun getStreamUrl(videoId: String?, allowFormatChoice: Boolean = false): String? = coroutineScope {
        if (videoId.isNullOrEmpty()) return@coroutineScope null

        val requestKey = "$videoId#format-choice=$allowFormatChoice"
        val requestedFormat = getPlaybackAudioFormat()
        // A URL-only cache cannot prove that the URL matches an explicitly requested format.
        // Interactive resolution must inspect the current format list instead of silently using
        // a URL selected earlier by prefetch or another resolver.
        val canUseCachedUrl = !allowFormatChoice || requestedFormat == "auto"
        val cachedUrl = if (canUseCachedUrl) {
            synchronized(cacheLock) { streamUrlCache.get(videoId) }
        } else null
        if (cachedUrl != null) {
            Log.d(TAG, "Cache HIT (URL) for $videoId")
            return@coroutineScope cachedUrl
        }

        // Deduplication
        val deferred = synchronized(inFlightRequests) {
            inFlightRequests[requestKey] ?: extractorScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                try {
                    val start = System.currentTimeMillis()
                    val info = fetchStreamInfo(videoId)
                    if (info == null) return@async null

                    var bestUrl: String? = null
                    val audioStreams = info.audioStreams
                    if (audioStreams.isEmpty()) {
                        val preferred = getPlaybackAudioFormat()
                        if (preferred == "auto") {
                            Log.w(TAG, "No audio-only streams found for $videoId, trying muxed video/audio streams")
                            bestUrl = info.videoStreams.firstOrNull()?.content
                        } else {
                            // Leave this unresolved so YouTubeMusic can inspect the independent
                            // InnerTube player response and offer its available audio formats.
                            Log.w(TAG, "No audio-only NewPipe streams for $videoId; checking InnerTube formats")
                        }
                    } else {
                        val best = selectStreamForOperation(
                            videoId = videoId,
                            title = info.name ?: videoId,
                            audioStreams = audioStreams,
                            preferredFormat = getPlaybackAudioFormat(),
                            targetKbps = getTargetBitrateKbps(),
                            purpose = AudioFormatPurpose.PLAYBACK,
                            allowFormatChoice = allowFormatChoice
                        )
                        bestUrl = best?.content
                    }

                    if (bestUrl != null) {
                        synchronized(cacheLock) { streamUrlCache.put(videoId, bestUrl) }
                    }
                    Log.d(TAG, "Stream extraction took ${System.currentTimeMillis() - start}ms for $videoId")
                    bestUrl
                } finally {
                    inFlightRequests.remove(requestKey)
                }
            }.also {
                inFlightRequests[requestKey] = it
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
        val selected = aacStreams.maxByOrNull { streamBitrateKbps(it) }
            ?: audioStreams.minByOrNull { streamBitrateKbps(it) }

        val url = selected?.content?.takeIf { it.startsWith("https://", ignoreCase = true) }
        Log.i(
            TAG,
            "Selected compatible recovery format for $videoId: " +
                "itag=${selected?.itag}, mime=${selected?.format?.mimeType}, codec=${selected?.codec}"
        )
        url
    }

    private suspend fun selectInnerTubeDownloadStream(
        videoId: String,
        title: String,
        allowFormatChoice: Boolean
    ): StreamData? {
        val requestedFormat = getDownloadAudioFormat()
        val targetKbps = getDownloadTargetBitrateKbps()
        val parsed = runCatching {
            InnerTubeParser.parsePlayerResponse(
                json = InnerTubeClient.player(videoId),
                preferredAudioFormatOverride = requestedFormat,
                targetBitrateKbpsOverride = targetKbps
            )
        }.getOrNull() ?: return null
        val streams = parsed.streams.filter { it.mimeType.contains("audio", ignoreCase = true) && it.url.isNotBlank() }
        if (streams.isEmpty()) return null

        val matching = streams.filter {
            matchesAudioFormat(it.mimeType, preferredFormat = requestedFormat)
        }
        val candidates = when {
            requestedFormat == "auto" -> streams.filter { it.mimeType.contains("audio/mp4", ignoreCase = true) }.ifEmpty { streams }
            matching.isNotEmpty() -> matching
            else -> emptyList()
        }

        val selected: StreamData = if (candidates.isNotEmpty()) {
            candidates.minByOrNull { stream ->
                val kbps = if (stream.bitrate > 1000) (stream.bitrate + 500) / 1000 else stream.bitrate
                if (kbps > 0) kotlin.math.abs(kbps - targetKbps) else Int.MAX_VALUE
            } ?: return null
        } else {
            if (requestedFormat == "auto") return null
            if (!allowFormatChoice) throw RequestedAudioFormatUnavailableException(requestedFormat)
            val optionPairs = streams.mapIndexed { index, stream ->
                val kbps = if (stream.bitrate > 1000) (stream.bitrate + 500) / 1000 else stream.bitrate
                stream to AvailableAudioFormat(
                    id = "innertube-download:$videoId:$index",
                    url = stream.url,
                    mimeType = stream.mimeType.substringBefore(';').trim(),
                    codec = null,
                    bitrateKbps = kbps,
                    contentLength = stream.contentLength,
                    formatLabel = audioFormatDisplayLabel(stream.mimeType)
                )
            }.sortedWith(
                compareByDescending<Pair<StreamData, AvailableAudioFormat>> { it.second.bitrateKbps }
                    .thenBy { it.second.formatLabel }
            )
            val choice = AudioFormatSelectionCoordinator.choose(
                songTitle = title,
                purpose = AudioFormatPurpose.DOWNLOAD,
                requestedFormat = requestedFormat,
                targetBitrateKbps = targetKbps,
                options = optionPairs.map { it.second }
            )
            val original = optionPairs.firstOrNull { it.second.id == choice.id }?.first ?: return null
            val bitrateKbps = if (original.bitrate > 1000) (original.bitrate + 500) / 1000 else original.bitrate
            original.copy(
                url = choice.url,
                mimeType = choice.mimeType,
                bitrate = if (bitrateKbps > 0) bitrateKbps * 1000 else choice.bitrateKbps * 1000,
                contentLength = choice.contentLength,
                audioQuality = original.audioQuality ?: qualityLabelForKbps(choice.bitrateKbps)
            )
        }

        val bitrateKbps = if (selected.bitrate > 1000) (selected.bitrate + 500) / 1000 else selected.bitrate
        return selected.copy(bitrate = bitrateKbps * 1000)
    }

    /**
     * Select a download stream with embedded tagging in mind. Prefer AAC/MP4,
     * which can be stored as M4A and tagged with jaudiotagger on Android 6+.
     * If unavailable, keep the selected source stream's reported container.
     */
    suspend fun getDownloadStreamData(videoId: String?, allowFormatChoice: Boolean = true): StreamData? = coroutineScope {
        if (videoId.isNullOrBlank()) return@coroutineScope null
        val info = fetchStreamInfo(videoId)
        if (info == null || info.audioStreams.isEmpty()) {
            return@coroutineScope selectInnerTubeDownloadStream(
                videoId = videoId,
                title = videoId,
                allowFormatChoice = allowFormatChoice
            )
        }
        val audioStreams = info.audioStreams

        val downloadFormat = getDownloadAudioFormat()
        val targetKbps = getDownloadTargetBitrateKbps()
        val selected = selectStreamForOperation(
            videoId = videoId,
            title = info.name ?: videoId,
            audioStreams = audioStreams,
            preferredFormat = downloadFormat,
            targetKbps = targetKbps,
            purpose = AudioFormatPurpose.DOWNLOAD,
            allowFormatChoice = allowFormatChoice
        ) ?: return@coroutineScope null

        val reportedMime = selected.format?.mimeType?.substringBefore(';')?.trim().orEmpty()
        val mime = when {
            reportedMime.isNotBlank() -> reportedMime
            selected.codec?.contains("mp4a", ignoreCase = true) == true -> "audio/mp4"
            selected.codec?.contains("opus", ignoreCase = true) == true -> "audio/webm"
            selected.codec?.contains("vorbis", ignoreCase = true) == true -> "audio/ogg"
            selected.codec?.contains("mp3", ignoreCase = true) == true -> "audio/mpeg"
            else -> "application/octet-stream"
        }
        val bitrateKbps = streamBitrateKbps(selected)
        Log.i(
            TAG,
            "Selected download stream for $videoId: format=$downloadFormat target=${targetKbps}kbps " +
                "actual=${bitrateKbps}kbps mime=$mime itag=${selected.itag}"
        )
        StreamData(
            url = selected.content,
            mimeType = mime,
            bitrate = bitrateKbps * 1000,
            contentLength = selected.itagItem?.contentLength?.takeIf { it > 0L },
            audioQuality = qualityLabelForKbps(bitrateKbps)
        )
    }

    /**
     * Extract full stream info including metadata lazily.
     */
    suspend fun getPlayerResult(
        videoId: String?,
        metadataOnly: Boolean = false
    ): PlayerResult? = coroutineScope {
        if (videoId.isNullOrEmpty()) return@coroutineScope null

        // Metadata requests have a separate identity and deliberately bypass the strict playback
        // cache, so an unavailable selected codec never suppresses the song's title/artist/duration.
        val requestKey = "$videoId#metadata=$metadataOnly"
        if (!metadataOnly) {
            val cachedResult = synchronized(cacheLock) { playerResultCache.get(videoId) }
            if (cachedResult != null) {
                Log.d(TAG, "Cache HIT (PlayerResult) for $videoId")
                return@coroutineScope cachedResult
            }
        }

        val deferred = synchronized(inFlightPlayerResults) {
            inFlightPlayerResults[requestKey] ?: extractorScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                try {
                    val info = fetchStreamInfo(videoId) ?: return@async null
                    val audioStreams = info.audioStreams
                    if (audioStreams.isEmpty() && !metadataOnly) return@async null

                    val requestedMatch = selectBestStream(audioStreams)
                    // Information screens may use a source-supported stream to report bitrate/MIME
                    // even when the chosen playback format is unavailable. It is never added to
                    // the playback URL cache and therefore cannot silently change playback format.
                    val bestStream = requestedMatch ?: if (metadataOnly) {
                        audioStreams.maxByOrNull { streamBitrateKbps(it) }
                    } else {
                        null
                    }
                    if (bestStream == null && !metadataOnly) return@async null

                    val streams = bestStream?.let { stream ->
                        val bitrateKbps = streamBitrateKbps(stream)
                        listOf(
                            StreamData(
                                url = stream.content,
                                mimeType = stream.format?.mimeType ?: "audio/mp4",
                                bitrate = bitrateKbps * 1000,
                                contentLength = stream.itagItem?.contentLength?.takeIf { it > 0L },
                                audioQuality = qualityLabelForKbps(bitrateKbps)
                            )
                        )
                    } ?: emptyList()

                    val thumbnailUrl = info.thumbnails.lastOrNull()?.url?.let {
                        InnerTubeParser.getHighResThumbnailUrl(it)
                    }
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
                    if (!metadataOnly) {
                        synchronized(cacheLock) {
                            playerResultCache.put(videoId, result)
                            // Only this strict, preference-matching path can seed playback URLs.
                            bestStream?.let { streamUrlCache.put(videoId, it.content) }
                        }
                    }
                    result
                } finally {
                    inFlightPlayerResults.remove(requestKey)
                }
            }.also {
                inFlightPlayerResults[requestKey] = it
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
