@file:Suppress("DEPRECATION")

package com.codetrio.spatialflow.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.innertube.YouTubeMusic
import com.codetrio.spatialflow.ui.widget.SpatialFlowWidgetProvider
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.TreeSet
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import android.media.AudioAttributes as LegacyAudioAttributes

@OptIn(UnstableApi::class)
data class CachedMediaMetadata(
    val title: String,
    val artist: String,
    val songId: String,
    val durationMs: Long = 0L,
    val videoId: String = "",
    val mimeType: String = ""
)

data class CachedAudioFacts(
    val cachedBytes: Long,
    val totalBytes: Long?,
    val mimeType: String?,
    val isComplete: Boolean
)

data class CurrentAudioFormatDetails(
    val bitrateBps: Int?,
    val sampleRateHz: Int?,
    val mimeType: String? = null,
    val durationMs: Long? = null,
    val albumTitle: String? = null
)

@AndroidEntryPoint
class AudioPlaybackService : MediaSessionService() {

    companion object {
        private const val TAG = "AudioPlaybackService"
        
        const val ACTION_PLAY = "com.codetrio.spatialflow.ACTION_PLAY"
        const val ACTION_PAUSE = "com.codetrio.spatialflow.ACTION_PAUSE"
        const val ACTION_PREVIOUS = "com.codetrio.spatialflow.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.codetrio.spatialflow.ACTION_NEXT"
        const val ACTION_TOGGLE_LOOP = "com.codetrio.spatialflow.ACTION_TOGGLE_LOOP"
        const val ACTION_CYCLE_PLAYBACK_MODE = "com.codetrio.spatialflow.ACTION_CYCLE_PLAYBACK_MODE"
        const val ACTION_TOGGLE_FAV = "com.codetrio.spatialflow.ACTION_TOGGLE_FAV"
        const val ACTION_WIDGET_FETCH_LYRICS = "com.codetrio.spatialflow.ACTION_WIDGET_FETCH_LYRICS"

        private const val PREFS_NAME = "AppSettings"
        private const val KEY_CROSSFADE_DURATION = "crossfade_duration"
        private const val KEY_AUDIO_FOCUS = "audio_focus"

        val CMD_TOGGLE_LOOP = SessionCommand(ACTION_TOGGLE_LOOP, Bundle.EMPTY)
        val CMD_CYCLE_PLAYBACK_MODE = SessionCommand(ACTION_CYCLE_PLAYBACK_MODE, Bundle.EMPTY)
        val CMD_TOGGLE_FAV = SessionCommand(ACTION_TOGGLE_FAV, Bundle.EMPTY)

        private const val KEY_MEDIA_CACHE_ENABLED = "media_cache_enabled"
        private const val KEY_MEDIA_CACHE_LIMIT_GB = "media_cache_limit_gb"
        private const val DEFAULT_MEDIA_CACHE_LIMIT_GB = 1
        private const val BYTES_PER_GB = 1024L * 1024L * 1024L
        private const val PREF_MEDIA_CACHE_METADATA = "media_cache_song_metadata"
        private const val CACHE_METADATA_ENTRY_PREFIX = "entry_"

        private var cache: SimpleCache? = null
        private var cacheEvictor: AdjustableCacheEvictor? = null

        @Synchronized
        fun getCache(context: Context): SimpleCache {
            cache?.let { return it }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean(KEY_MEDIA_CACHE_ENABLED, true)
            val limitBytes = prefs.getInt(KEY_MEDIA_CACHE_LIMIT_GB, DEFAULT_MEDIA_CACHE_LIMIT_GB)
                .coerceIn(1, 10).toLong() * BYTES_PER_GB
            val evictor = AdjustableCacheEvictor(enabled, limitBytes)
            val createdCache = SimpleCache(
                File(context.cacheDir, "media_cache"),
                evictor,
                StandaloneDatabaseProvider(context)
            )
            try {
                // SimpleCache already reports its loaded spans to the evictor during construction.
                // Configure limits without rescanning every cache key a second time.
                evictor.configure(createdCache, enabled, limitBytes)
                cacheEvictor = evictor
                cache = createdCache
                return createdCache
            } catch (error: Exception) {
                try {
                    createdCache.release()
                } catch (releaseError: Exception) {
                    Log.w(TAG, "Failed to release an incompletely initialized media cache", releaseError)
                }
                throw error
            }
        }

        @Synchronized
        fun updateMediaCacheSettings(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean(KEY_MEDIA_CACHE_ENABLED, true)
            val limitBytes = prefs.getInt(KEY_MEDIA_CACHE_LIMIT_GB, DEFAULT_MEDIA_CACHE_LIMIT_GB)
                .coerceIn(1, 10).toLong() * BYTES_PER_GB
            cache?.let { cacheEvictor?.configure(it, enabled, limitBytes) }
        }

        @Synchronized
        fun getMediaCacheSnapshot(context: Context): Pair<Long, List<Pair<String, Long>>> {
            val currentCache = getCache(context)
            val entries = currentCache.keys.map { key ->
                key to currentCache.getCachedSpans(key).map { it.length }.sum()
            }.filter { it.second > 0L }.sortedByDescending { it.second }
            return currentCache.cacheSpace to entries
        }

        /**
         * Finds a fully cached stream for a track without consulting the user's selected
         * format/bitrate. Returning the exact original URL preserves Media3's cache key, and
         * requiring the full content length prevents an offline tap from silently reaching
         * the network for missing byte ranges.
         */
        @JvmStatic
        @Synchronized
        fun getCachedPlaybackUri(
            context: Context,
            songId: String,
            videoId: String? = null,
            requireComplete: Boolean = true
        ): Uri? {
            val currentCache = getCache(context.applicationContext)
            val candidates = currentCache.keys.mapNotNull { key ->
                val metadata = getCachedMediaMetadata(context, key) ?: return@mapNotNull null
                val matchesSong = songId.isNotBlank() && metadata.songId == songId
                val matchesVideo = !videoId.isNullOrBlank() && metadata.videoId == videoId
                if (!matchesSong && !matchesVideo) return@mapNotNull null

                val contentMetadata = currentCache.getContentMetadata(key)
                val contentLength = androidx.media3.datasource.cache.ContentMetadata
                    .getContentLength(contentMetadata)
                val fullyCached = contentLength > 0L &&
                    currentCache.isCached(key, 0L, contentLength)
                if (requireComplete && !fullyCached) return@mapNotNull null

                val uri = Uri.parse(key)
                if (uri.scheme != "http" && uri.scheme != "https") return@mapNotNull null
                val cachedBytes = currentCache.getCachedSpans(key).sumOf { it.length }
                if (cachedBytes <= 0L) return@mapNotNull null
                Triple(key, cachedBytes, fullyCached)
            }
            // Prefer a completely cached version over a larger partial copy at another quality.
            val completeCandidates = candidates.filter { it.third }
            val preferredCandidates = completeCandidates.ifEmpty { candidates }
            return preferredCandidates.maxByOrNull { it.second }?.first?.let { Uri.parse(it) }
        }

        /** Returns cached byte count and detected format for the current track. */
        @JvmStatic
        @Synchronized
        fun getCachedAudioFacts(
            context: Context,
            songId: String,
            videoId: String? = null,
            streamUri: String? = null
        ): CachedAudioFacts? {
            val currentCache = getCache(context.applicationContext)
            val candidates = currentCache.keys.mapNotNull { key ->
                val metadata = getCachedMediaMetadata(context, key)
                val isExactUri = !streamUri.isNullOrBlank() && key == streamUri
                val matchesMetadata = metadata != null && (
                    (songId.isNotBlank() && metadata.songId == songId) ||
                    (!videoId.isNullOrBlank() && metadata.videoId == videoId)
                )
                if (!matchesMetadata && !isExactUri) return@mapNotNull null

                val spans = currentCache.getCachedSpans(key)
                val cachedBytes = spans.sumOf { it.length }
                if (cachedBytes <= 0L) return@mapNotNull null
                val contentLength = androidx.media3.datasource.cache.ContentMetadata
                    .getContentLength(currentCache.getContentMetadata(key))
                    .takeIf { it > 0L }
                val complete = contentLength != null &&
                    currentCache.isCached(key, 0L, contentLength)
                CachedAudioFacts(
                    cachedBytes = cachedBytes,
                    totalBytes = contentLength,
                    mimeType = metadata?.mimeType?.takeIf { it.isNotBlank() },
                    isComplete = complete
                ) to isExactUri
            }
            // The currently playing cache key is authoritative. Otherwise prefer complete
            // variants, then the largest cached byte range as a best-effort fallback.
            val exactCandidates = candidates.filter { it.second }
            if (exactCandidates.isNotEmpty()) {
                return exactCandidates.maxByOrNull { it.first.cachedBytes }?.first
            }
            val completeCandidates = candidates.filter { it.first.isComplete }
            return completeCandidates.ifEmpty { candidates }
                .maxByOrNull { it.first.totalBytes ?: it.first.cachedBytes }?.first
        }

        /**
         * Associates the opaque Media3 cache key (typically a signed stream URL) with safe display
         * metadata. Only a SHA-256 digest is used as the preference key; signed URLs are not stored
         * in the metadata preference file.
         */
        @Synchronized
        fun rememberMediaCacheMetadata(
            context: Context,
            cacheKey: String,
            title: String,
            artist: String,
            songId: String = "",
            durationMs: Long = 0L,
            videoId: String = "",
            mimeType: String = ""
        ) {
            if (cacheKey.isBlank() || title.isBlank()) return
            val digestKey = cacheMetadataEntryKey(cacheKey)
            // Keep the video ID when a cache playlist later reopens a track using its direct
            // cached URL (that offline queue intentionally has no online resolver ID).
            val previousMetadata = getCachedMediaMetadata(context, cacheKey)
            val payload = org.json.JSONObject()
                .put("title", title)
                .put("artist", artist)
                .put("songId", songId)
                .put("durationMs", durationMs)
                .put("videoId", videoId.ifBlank { previousMetadata?.videoId.orEmpty() })
                .put("mimeType", mimeType.ifBlank { previousMetadata?.mimeType.orEmpty() })
                .toString()
            context.getSharedPreferences(PREF_MEDIA_CACHE_METADATA, Context.MODE_PRIVATE)
                .edit().putString(digestKey, payload).apply()
        }

        @JvmStatic
        fun getCachedMediaMetadata(context: Context, cacheKey: String): CachedMediaMetadata? {
            val stored = context.getSharedPreferences(PREF_MEDIA_CACHE_METADATA, Context.MODE_PRIVATE)
                .getString(cacheMetadataEntryKey(cacheKey), null) ?: return null
            return try {
                val json = org.json.JSONObject(stored)
                val title = json.optString("title", "").trim()
                if (title.isEmpty()) return null
                CachedMediaMetadata(
                    title = title,
                    artist = json.optString("artist", "").trim(),
                    songId = json.optString("songId", ""),
                    durationMs = json.optLong("durationMs", 0L),
                    videoId = json.optString("videoId", ""),
                    mimeType = json.optString("mimeType", "")
                )
            } catch (_: Exception) {
                null
            }
        }

        @JvmStatic
        fun getCachedMediaArtworkFile(context: Context, songId: String): File {
            val directory = File(context.cacheDir, "media_artwork")
            return File(directory, cacheMetadataEntryKey("artwork:" + songId) + ".jpg")
        }

        @JvmStatic
        fun storeCachedMediaArtwork(context: Context, songId: String, bitmap: Bitmap): Boolean {
            if (songId.isBlank()) return false
            val file = getCachedMediaArtworkFile(context, songId)
            val directory = file.parentFile ?: return false
            if (!directory.exists() && !directory.mkdirs()) return false
            return try {
                val written = java.io.FileOutputStream(file).use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                }
                if (written && file.isFile && file.length() > 0L) true
                else {
                    file.delete()
                    false
                }
            } catch (error: Exception) {
                Log.w(TAG, "Unable to persist cached artwork", error)
                false
            }
        }

        private fun cacheMetadataEntryKey(cacheKey: String): String {
            val bytes = java.security.MessageDigest.getInstance("SHA-256")
                .digest(cacheKey.toByteArray(Charsets.UTF_8))
            val hex = "0123456789abcdef"
            val out = StringBuilder(bytes.size * 2)
            bytes.forEach { value ->
                val unsigned = value.toInt() and 0xff
                out.append(hex[unsigned ushr 4]).append(hex[unsigned and 0x0f])
            }
            return CACHE_METADATA_ENTRY_PREFIX + out.toString()
        }

        @Synchronized
        fun clearMediaCache(context: Context) {
            context.getSharedPreferences(PREF_MEDIA_CACHE_METADATA, Context.MODE_PRIVATE).edit().clear().apply()
            File(context.cacheDir, "media_artwork").deleteRecursively()
            val currentCache = cache
            if (currentCache == null) {
                File(context.cacheDir, "media_cache").deleteRecursively()
                return
            }
            currentCache.keys.toList().forEach { key ->
                try { currentCache.removeResource(key) }
                catch (error: Exception) { Log.w(TAG, "Could not clear one cached resource", error) }
            }
            cacheEvictor?.refresh(currentCache)
        }
    }

    private val binder: IBinder = LocalBinder()
    private val serviceScope = MainScope()
    internal lateinit var player: ExoPlayer

    // Service callbacks must return quickly on older devices. Disk-cache initialization is
    // performed on IO before ExoPlayer is built; local UI binding remains available meanwhile.
    private var isPlayerInitialized = false
    private val playerReadyCallbacks = mutableListOf<() -> Unit>()
    private var pendingViewModel: PlayerSharedViewModel? = null
    private val pendingServiceActions = mutableListOf<String>()
    private var pendingPlaybackRequest: PendingPlaybackRequest? = null
    private var pendingSeekToPosition: Long? = null

    private data class PendingPlaybackRequest(
        val uri: Uri,
        val startPosition: Long,
        val autoPlay: Boolean
    )
    private var mediaSession: MediaSession? = null
    private var viewModel: PlayerSharedViewModel? = null
    private var widgetLyricsObserverJob: Job? = null
    private lateinit var handler: Handler
    private lateinit var progressUpdateRunnable: Runnable

    private var currentSourceUri: Uri? = null
    private var originalSourceUri: Uri? = null
    private var isNextSongPreCached = false
    private var currentSongName = "SpatialFlow"
    private var currentAlbumArt: Bitmap? = null
    private var lastWidgetProgressUpdateAtMs: Long = 0L
    private var shouldAutoPlay = false
    private var pendingSeekPos: Long = -1
    
    // Instant cancellation of stale stream resolutions — prevents race conditions on rapid clicks
    private var resolveJob: Job? = null
    // Track the unique song ID that the current resolution is targeting
    private var resolveTargetSongId: String? = null
    private var lastPlaybackRecoveryVideoId: String? = null
    private var failedStreamVideoId: String? = null
    private var forceWebRemixOnNextResolveVideoId: String? = null
    private var forceCompatibleFallbackOnNextResolveVideoId: String? = null
    private var playbackRecoveryStage = 0
    private var isRecoveringPlaybackError = false
    private var isFetchingRelatedProactively = false

    private lateinit var telemetryManager: com.codetrio.spatialflow.data.innertube.YouTubeTelemetryManager

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isPausedByFocusLoss = false
    private var audioFocusEnabled = true

    private val streamHttpClient: okhttp3.OkHttpClient by lazy {
        com.codetrio.spatialflow.data.innertube.InnerTubeClient.httpClient.newBuilder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(com.codetrio.spatialflow.data.stream.YouTubeStreamInterceptor())
            .build()
    }


    private var crossfadeDurationMs = 0
    private var isCrossfading = false
    private var crossfadeNextSongStarted = false
    private var nextPlayer: ExoPlayer? = null
    
    private var currentBaseVolume = 1.0f

    private var prefListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    private var crossfadeAnimator: android.animation.ValueAnimator? = null

    private val mainPlayerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            viewModel?.postIsPlaying(isPlaying)
            telemetryManager.onPlaybackStateChanged(isPlaying)
            if (isPlaying) {
                handler.removeCallbacks(progressUpdateRunnable)
                handler.post(progressUpdateRunnable)
            } else {
                handler.removeCallbacks(progressUpdateRunnable)
            }
            updateWidgetState(isPlaying)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            viewModel?.let { vm ->
                if (vm.isShuffleEnabled.value != shuffleModeEnabled) {
                    vm.setShuffleEnabled(shuffleModeEnabled)
                }
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onPlaybackStateChanged(state: Int) {
            Log.d(TAG, "Player state=${state} ready=${state == Player.STATE_READY} playWhenReady=${player.playWhenReady} uri=${player.currentMediaItem?.localConfiguration?.uri}")
            val isPlayingState = player.playWhenReady && state == Player.STATE_READY
            viewModel?.postIsPlaying(isPlayingState)

            if (state == Player.STATE_BUFFERING) viewModel?.setLoading(true)
            if (state == Player.STATE_READY) {
                viewModel?.setLoading(false)
                viewModel?.setPlaybackReady(true)
                persistCurrentCacheMimeType()
                if (pendingSeekPos >= 0) {
                    player.seekTo(pendingSeekPos)
                    pendingSeekPos = -1
                }
            }
            
            if (state == Player.STATE_ENDED) {
                handlePlaybackCompleted()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val sourceUri = player.currentMediaItem?.localConfiguration?.uri ?: currentSourceUri
            Log.e(
                TAG,
                "Player failure: code=${error.errorCodeName} type=${error.javaClass.simpleName} " +
                    "source=$sourceUri cause=${error.cause?.javaClass?.name}: ${error.cause?.message}",
                error
            )
            viewModel?.setIsPlaying(false)

            val song = viewModel?.currentSong?.value
            val videoId = song?.videoId?.takeIf { it.isNotBlank() }
            viewModel?.setLoading(videoId != null && playbackRecoveryStage < 2)
            if (videoId != null && playbackRecoveryStage < 2) {
                if (lastPlaybackRecoveryVideoId != videoId) {
                    lastPlaybackRecoveryVideoId = videoId
                    playbackRecoveryStage = 1
                    forceWebRemixOnNextResolveVideoId = videoId
                    Log.w(TAG, "Playback recovery 1/2: retrying $videoId with a fresh WEB_REMIX URL")
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Trying an alternate song stream…")
                } else {
                    playbackRecoveryStage = 2
                    forceCompatibleFallbackOnNextResolveVideoId = videoId
                    Log.w(TAG, "Playback recovery 2/2: retrying $videoId with an AAC-compatible stream")
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Trying a more compatible audio stream…")
                }

                val resumePosition = player.currentPosition.coerceAtLeast(0L)
                handler.postDelayed({
                    if (viewModel?.currentSong?.value?.videoId == videoId) {
                        // Invalidate both application-level and NewPipe URL/player caches before
                        // asking the next resolver. Media3 gets a new URI instead of reusing the
                        // URL that just failed.
                        YouTubeMusic.invalidateStreamUrl(videoId)
                        isRecoveringPlaybackError = true
                        try {
                            loadAndPlay("innertube://$videoId".toUri(), resumePosition)
                        } finally {
                            isRecoveringPlaybackError = false
                        }
                    }
                }, 250)
                return
            }

            viewModel?.setLoading(false)
            val message = if (videoId != null) {
                "Playback failed after retry. Check your connection and try again."
            } else {
                "This audio file could not be played. Try another file."
            }
            com.codetrio.spatialflow.ui.SnackbarController.showMessage(message)
            // Keep the failed item selected so the user can retry or inspect the failure.
            // Silently skipping every errored track made a complete stream failure look like
            // the Play button was broken.
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            viewModel?.setCurrentPosition(
                newPosition.positionMs.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            )
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                Log.d(TAG, "Auto-transition detected natively")
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                Log.d(TAG, "⚡ NATIVE GAPLESS TRANSITION COMPLETED! Resynchronizing UI.")
                
                // Sleep Timer Interception flags
                var interceptForSleepTimer = false
                viewModel?.let { vm ->
                    if (vm.sleepTimerMode.value == PlayerSharedViewModel.SleepTimerMode.END_OF_SONG) {
                        interceptForSleepTimer = true
                    }
                    if (vm.sleepTimerMode.value == PlayerSharedViewModel.SleepTimerMode.END_OF_QUEUE) {
                        if (vm.currentSongIndex.value == vm.songList.value.size - 1) {
                            interceptForSleepTimer = true
                        }
                    }
                }

                // Maintain a 2-item sliding window queue: 
                // Safely excise the deprecated previous item to purge expired URL reference and free heap memory.
                if (player.mediaItemCount > 1) {
                    player.removeMediaItem(0)
                }

                // Use mediaId-based resync for accuracy in Shuffle Mode
                val transitionedMediaId = mediaItem?.mediaId
                viewModel?.let { vm ->
                    val songs = vm.songList.value
                    
                    // Find exact match by mediaId (stable song identifier)
                    val matchedIdx = if (!transitionedMediaId.isNullOrEmpty()) {
                        songs.indexOfFirst { it.id.toString() == transitionedMediaId }
                    } else {
                        -1
                    }
                    
                    val nextIdx = if (matchedIdx >= 0) matchedIdx else vm.getNextSongIndex()
                    if (nextIdx >= 0) {
                        vm.updateSongIndexOnly(nextIdx)
                        
                        songs.getOrNull(nextIdx)?.let { song ->
                            currentSongName = song.title
                            if (song.thumbnailUrl != null) {
                                setSongMetadataByUrl(song.title, song.thumbnailUrl)
                            } else {
                                setSongMetadataById(song.title, song.albumId)
                            }
                            if (!song.videoId.isNullOrEmpty()) {
                                telemetryManager.onSongChanged(song.videoId!!, song.duration)
                            }
                        }
                        
                        if (interceptForSleepTimer) {
                            pause()
                            vm.setSleepTimerMode(PlayerSharedViewModel.SleepTimerMode.OFF)
                        }

                        handler.postDelayed({ preloadNextSongIntoQueue() }, 300)
                    }
                }
            }
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                player.volume = currentBaseVolume
                if (isPausedByFocusLoss) {
                    play()
                    isPausedByFocusLoss = false
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                pause()
                isPausedByFocusLoss = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (player.isPlaying) {
                    pause()
                    isPausedByFocusLoss = true
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (player.isPlaying) player.volume = currentBaseVolume * 0.3f
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // No-op for behavior in older Media3 or if unresolved
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        handler = Handler(Looper.getMainLooper())

        telemetryManager = com.codetrio.spatialflow.data.innertube.YouTubeTelemetryManager(
            httpClient = com.codetrio.spatialflow.data.innertube.InnerTubeClient.httpClient,
            clientProvider = { com.codetrio.spatialflow.data.innertube.InnerTubeClient }
        )
        
        // SimpleCache construction scans disk and can block for seconds on Android 6 storage.
        // Do not do that work inside Service.onCreate(), whose startup timeout is strict.
        serviceScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    getCache(applicationContext)
                }

                initializePlayer()
                setupMediaSession()
                loadAudioPreferences()
                setupPreferenceListener()
                setupProgressTracking()
                isPlayerInitialized = true

                pendingViewModel?.let { vm ->
                    pendingViewModel = null
                    setViewModel(vm)
                }

                val readyCallbacks = playerReadyCallbacks.toList()
                playerReadyCallbacks.clear()
                readyCallbacks.forEach { callback ->
                    try {
                        callback()
                    } catch (error: Exception) {
                        Log.e(TAG, "Player-ready callback failed", error)
                    }
                }

                pendingPlaybackRequest?.let { request ->
                    pendingPlaybackRequest = null
                    if (request.autoPlay) {
                        loadAndPlay(request.uri, request.startPosition)
                    } else {
                        loadAudio(request.uri, request.startPosition)
                    }
                }
                pendingSeekToPosition?.let { position ->
                    pendingSeekToPosition = null
                    player.seekTo(position)
                }

                val actions = pendingServiceActions.toList()
                pendingServiceActions.clear()
                actions.forEach(::handleServiceAction)
                Log.i(TAG, "Playback service initialized asynchronously")
            } catch (error: Exception) {
                Log.e(TAG, "Asynchronous playback service initialization failed", error)
            }
        }
    }

    /**
     * Run code that needs ExoPlayer only after its disk cache and player are ready.
     * Callbacks run on the service main thread.
     */
    fun runWhenPlayerReady(callback: () -> Unit) {
        if (isPlayerInitialized) {
            callback()
        } else {
            playerReadyCallbacks.add(callback)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            if (isPlayerInitialized) {
                handleServiceAction(action)
            } else {
                // Android can deliver media-button/notification actions while disk setup is running.
                pendingServiceActions.add(action)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleServiceAction(action: String) {
        when (action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_NEXT -> playNext()
            ACTION_TOGGLE_LOOP -> {
                viewModel?.toggleLoopMode()
                if (isPlayerInitialized) updateWidgetState(player.isPlaying)
            }
            ACTION_CYCLE_PLAYBACK_MODE -> cycleNotificationPlaybackMode()
            ACTION_TOGGLE_FAV -> viewModel?.toggleFavorite()
            ACTION_WIDGET_FETCH_LYRICS -> {
                val vm = viewModel
                val song = vm?.currentSong?.value
                if (song != null) {
                    if (vm.syncedLyrics.value.isNullOrEmpty() && vm.plainLyrics.value.isNullOrBlank()) {
                        vm.fetchLyricsForCurrentSong()
                    }
                    updateWidgetState(player.isPlaying)
                } else {
                    Log.w(TAG, "Widget requested lyrics before a current song was available")
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return if (intent?.action == SERVICE_INTERFACE) super.onBind(intent) else binder
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (mediaSession?.player?.playWhenReady == false) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    fun dismissService() {
        player.stop()
        player.clearMediaItems()
        abandonAudioFocus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        prefListener?.let {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(it)
        }
        if (::progressUpdateRunnable.isInitialized) {
            handler.removeCallbacks(progressUpdateRunnable)
        }
        abandonAudioFocus()

        if (isPlayerInitialized) {
            viewModel?.cleanupBgScope()
            player.removeListener(mainPlayerListener)
            player.release()
            nextPlayer?.release()
        }
        mediaSession?.release()
        playerReadyCallbacks.clear()
        serviceScope.cancel()
    }

    private fun configurePlayerAudio(target: ExoPlayer) {
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        target.setAudioAttributes(attrs, audioFocusEnabled)
        target.setWakeMode(C.WAKE_MODE_NETWORK)
        target.skipSilenceEnabled = false
    }

    private fun initializePlayer() {
        player = buildExoPlayer()
        configurePlayerAudio(player)
        player.addListener(mainPlayerListener)
    }

    /**
     * Use Media3's stock audio renderer/sink for maximum decoder and output compatibility.
     * Custom PCM processing is deliberately kept out of the critical audio path: a failure
     * in a visualizer/haptics processor must never prevent both local and remote tracks.
     */
    private fun buildExoPlayer(): ExoPlayer {
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)

        val httpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(streamHttpClient)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")

        // DefaultDataSource handles content:// and file:// locally; only HTTP(S) traffic
        // reaches OkHttp and the YouTube stream interceptor.
        val upstreamFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpDataSourceFactory)
        val cacheDataSourceFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(getCache(this))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
            .setDataSourceFactory(cacheDataSourceFactory)

        // Leave load control at Media3's tested defaults instead of forcing unusually long
        // buffers for local files and short tracks.
        return ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }

    fun setViewModel(vm: PlayerSharedViewModel) {
        if (!isPlayerInitialized) {
            pendingViewModel = vm
            return
        }
        val oldVm = this.viewModel
        if (oldVm != null && oldVm !== vm) {
            // Memory Leak Extermination: 
            // The old ViewModel instance became a zombie when the Activity was destroyed.
            // Before discarding it, copy its queue and playback state to the new UI ViewModel.
            vm.restoreStateFrom(oldVm)
            oldVm.cleanupBgScope()
        }
        this.viewModel = vm

        // Keep widget lyrics live after the activity has closed. Tapping the widget's
        // lyrics page starts the normal provider pipeline, and each result updates RemoteViews.
        widgetLyricsObserverJob?.cancel()
        widgetLyricsObserverJob = serviceScope.launch {
            vm.syncedLyrics.collect { lyrics ->
                val position = player.currentPosition.coerceAtLeast(0L)
                val duration = player.duration.coerceAtLeast(0L)
                val currentText = currentWidgetLyricText(position)
                if (currentText != "...") {
                    SpatialFlowWidgetProvider.updateAllWidgetsPartial(
                        this@AudioPlaybackService,
                        position.toInt(),
                        duration.toInt(),
                        currentText
                    )
                }
            }
        }
        serviceScope.launch {
            vm.plainLyrics.collect { plain ->
                if (vm.syncedLyrics.value.isNullOrEmpty() && !plain.isNullOrBlank()) {
                    val position = player.currentPosition.coerceAtLeast(0L)
                    val duration = player.duration.coerceAtLeast(0L)
                    SpatialFlowWidgetProvider.updateAllWidgetsPartial(
                        this@AudioPlaybackService,
                        position.toInt(),
                        duration.toInt(),
                        currentWidgetLyricText(position)
                    )
                }
            }
        }

        player.shuffleModeEnabled = vm.isShuffleEnabled.value

        // Dynamic observations to trigger custom layout updates whenever loop/fav changes
        serviceScope.launch {
            vm.repeatMode.collect {
                updateMediaSessionCustomLayout()
            }
        }
        serviceScope.launch {
            vm.isCurrentSongFavorite.collect {
                updateMediaSessionCustomLayout()
            }
        }
        serviceScope.launch {
            vm.isShuffleEnabled.collect { enabled ->
                if (player.shuffleModeEnabled != enabled) {
                    player.shuffleModeEnabled = enabled
                }
                updateMediaSessionCustomLayout()
            }
        }
    }

    @JvmOverloads
    fun loadAudio(uri: Uri, startPosition: Long = -1) {
        if (!isPlayerInitialized) {
            pendingPlaybackRequest = PendingPlaybackRequest(uri, startPosition, autoPlay = false)
            return
        }
        shouldAutoPlay = false
        pendingSeekPos = startPosition
        resolveAndLoad(uri)
    }

    @JvmOverloads
    fun loadAndPlay(uri: Uri, startPosition: Long = -1) {
        if (!isPlayerInitialized) {
            pendingPlaybackRequest = PendingPlaybackRequest(uri, startPosition, autoPlay = true)
            viewModel?.setLoading(true)
            return
        }
        // A user-initiated track load permits one new recovery attempt. The automatic
        // recovery itself must not reset its guard, or a broken URL can retry forever.
        if (!isRecoveringPlaybackError) {
            lastPlaybackRecoveryVideoId = null
            playbackRecoveryStage = 0
            forceWebRemixOnNextResolveVideoId = null
            forceCompatibleFallbackOnNextResolveVideoId = null
        }
        shouldAutoPlay = true
        pendingSeekPos = startPosition
        resolveAndLoad(uri)
    }

    /**
     * Centralized execution gate resolving dynamic identifiers OUTSIDE the buffer pipeline
     * prior to injecting raw HTTP/File resource pointers into ExoPlayer.
     */
    private fun resolveAndLoad(uri: Uri) {
        viewModel?.setLoading(true)
        originalSourceUri = uri
        if (uri.scheme == "innertube") {
            val videoId = uri.host
            if (videoId.isNullOrBlank()) {
                viewModel?.setLoading(false)
                return
            }
            val useWebRemixRetry = forceWebRemixOnNextResolveVideoId == videoId
            val useCompatibleFallbackRetry = forceCompatibleFallbackOnNextResolveVideoId == videoId
            if (useWebRemixRetry) forceWebRemixOnNextResolveVideoId = null
            if (useCompatibleFallbackRetry) forceCompatibleFallbackOnNextResolveVideoId = null
            
            // INSTANT CANCELLATION: Kill any in-flight resolution immediately
            resolveJob?.cancel()
            resolveTargetSongId = videoId
            
            resolveJob = serviceScope.launch {
                Log.d(TAG, "Pre-resolving stream ahead of injector injection: $videoId")
                val activeSong = viewModel?.currentSong?.value?.takeIf { it.videoId == videoId }

                // A fully cached stream wins over format/bitrate preferences. Play it through
                // its exact cache key, otherwise an explicit setting can force a network fetch
                // even though another quality/container is already available offline.
                if (!useWebRemixRetry && !useCompatibleFallbackRetry) {

                    // A real downloaded audio file is the highest-priority offline source.
                    // Resolve it by video ID first so even non-default format/bitrate downloads
                    // are used rather than extracting a fresh URL for another online stream.
                    val downloadedUri = activeSong?.let { song ->
                        withContext(Dispatchers.IO) {
                            com.codetrio.spatialflow.util.SongDownloader.findDownloadedUri(
                                applicationContext,
                                videoId,
                                song.title,
                                song.artist
                            )
                        }
                    }
                    if (downloadedUri != null) {
                        ensureActive()
                        if (resolveTargetSongId != videoId) return@launch
                        viewModel?.refreshCurrentSongSource(
                            activeSong.withPlaybackSource(
                                downloadedUri,
                                cached = false,
                                downloaded = true
                            )
                        )
                        Log.i(TAG, "Using downloaded offline audio for $videoId: $downloadedUri")
                        originalSourceUri = downloadedUri
                        loadAudioInternal(downloadedUri, keepPlayingUntilReady = true)
                        return@launch
                    }

                    val cacheSongId = activeSong?.id?.toString()
                        ?: videoId.hashCode().toLong().toString()
                    val cachedUri = withContext(Dispatchers.IO) {
                        getCachedPlaybackUri(
                            applicationContext,
                            cacheSongId,
                            videoId,
                            requireComplete = false
                        )
                    }
                    if (cachedUri != null) {
                        ensureActive()
                        if (resolveTargetSongId != videoId) return@launch
                        activeSong?.let { song ->
                            viewModel?.refreshCurrentSongSource(
                                song.withPlaybackSource(
                                    cachedUri,
                                    cached = true,
                                    downloaded = false
                                )
                            )
                        }
                        Log.i(TAG, "Using cached stream for $videoId regardless of selected format/bitrate")
                        originalSourceUri = cachedUri
                        loadAudioInternal(cachedUri, keepPlayingUntilReady = true)
                        return@launch
                    }
                }

                // Fetch stream URL only, don't wait for position yet. Cancelling a format-choice
                // dialog is a user decision, not an extraction error: don't auto-switch formats
                // or immediately show the same request again.
                var formatChoiceCancelled = false
                var streamUrl = async(Dispatchers.IO) {
                    when {
                        useWebRemixRetry -> YouTubeMusic.getStreamUrlFromWebRemix(videoId).getOrNull()
                        useCompatibleFallbackRetry -> YouTubeMusic.getCompatibleFallbackStreamUrl(videoId).getOrNull()
                        else -> {
                            val result = YouTubeMusic.getStreamUrl(videoId, promptForFormatChoice = true)
                            formatChoiceCancelled =
                                result.exceptionOrNull() is com.codetrio.spatialflow.data.innertube.AudioFormatSelectionCancelledException
                            result.getOrNull()
                        }
                    }
                }.await()

                if (formatChoiceCancelled) {
                    ensureActive()
                    if (resolveTargetSongId == videoId) {
                        shouldAutoPlay = false
                        viewModel?.setIsPlaying(false)
                        viewModel?.setLoading(false)
                    }
                    return@launch
                }

                if (streamUrl.isNullOrEmpty()) {
                    when {
                        useWebRemixRetry -> {
                            Log.w(TAG, "WEB_REMIX recovery URL unavailable for $videoId; trying Android/iOS InnerTube")
                            streamUrl = async(Dispatchers.IO) {
                                YouTubeMusic.getStreamUrlFromInnerTube(videoId).getOrNull()
                            }.await()
                            if (streamUrl.isNullOrEmpty()) {
                                streamUrl = async(Dispatchers.IO) {
                                    YouTubeMusic.getStreamUrl(videoId).getOrNull()
                                }.await()
                            }
                        }
                        useCompatibleFallbackRetry -> {
                            Log.e(TAG, "Compatible fallback stream resolution failed for $videoId")
                        }
                        else -> {
                            Log.w(TAG, "Initial stream resolution failed for $videoId; invalidating caches and retrying once")
                            withContext(Dispatchers.IO) { YouTubeMusic.invalidateStreamUrl(videoId) }
                            delay(250L)
                            streamUrl = async(Dispatchers.IO) {
                                YouTubeMusic.getStreamUrl(videoId).getOrNull()
                            }.await()
                        }
                    }
                }

                // Guard: If user clicked another song while we were resolving, abort silently
                ensureActive()
                if (resolveTargetSongId != videoId) {
                    Log.d(TAG, "Stale resolution for $videoId discarded (target changed to $resolveTargetSongId)")
                    return@launch
                }
                
                if (!streamUrl.isNullOrEmpty()) {
                    failedStreamVideoId = null
                    activeSong?.let { song ->
                        val resolverUri = "innertube://$videoId".toUri()
                        val sourceChanged = song.isCachedTrack ||
                            song.isDownloadedOffline ||
                            song.contentUri != resolverUri
                        if (sourceChanged) {
                            viewModel?.refreshCurrentSongSource(
                                song.withPlaybackSource(
                                    resolverUri,
                                    cached = false,
                                    downloaded = false
                                )
                            )
                        }
                    }
                    loadAudioInternal(streamUrl.toUri(), keepPlayingUntilReady = true)
                    
                    // Asynchronously fetch and apply resume position with a 2-second timeout
                    if (pendingSeekPos <= 0) {
                        launch(Dispatchers.IO) {
                            val position = kotlinx.coroutines.withTimeoutOrNull(2000L.milliseconds) {
                                YouTubeMusic.getPlaybackPosition(videoId).getOrNull() ?: 0L
                            } ?: return@launch
                            
                            if (position > 2000L) {
                                withContext(Dispatchers.Main) {
                                    Log.d(TAG, "Synced cross-device playback position: ${position}ms asynchronously")
                                    pendingSeekPos = position
                                    // If player already started playing from 0, seek it now
                                    if (player.playbackState == Player.STATE_READY) {
                                        player.seekTo(position)
                                        pendingSeekPos = -1
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Failed resolving stream after retry for: $videoId")
                    failedStreamVideoId = videoId
                    shouldAutoPlay = false
                    viewModel?.setIsPlaying(false)
                    viewModel?.setLoading(false)
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage(
                        "Couldn't load this song. Check your connection and tap Play to retry."
                    )
                }
            }
        } else {
            // Local or pre-determined resource, injection is instant
            resolveJob?.cancel()
            resolveTargetSongId = null
            loadAudioInternal(uri)
        }
    }

    /**
     * The Ultimate Solution: Proactively builds the native ExoPlayer queue by resolving the NEXT track
     * in background and adding it to the pipeline BEFORE the current track completes.
     */
    private fun preloadNextSongIntoQueue() {
        val vm = viewModel ?: return
        val songs = vm.songList.value
        if (songs.isEmpty()) return
        
        // Shuffle-aware next index prediction
        val nextIndex = vm.getNextSongIndex() 
        if (nextIndex < 0 || nextIndex >= songs.size) {
            // Queue is ending! Proactively fetch related songs to enable infinite loop if current is online!
            val currentSong = vm.currentSong.value
            if (currentSong != null && !currentSong.videoId.isNullOrEmpty()) {
                fetchAndAppendRelatedSongsProactively(currentSong.videoId!!)
            }
            return
        }
        
        val nextSong = songs[nextIndex]
        Log.d(TAG, "Initializing proactive queue preloading for native transition: ${nextSong.title}")
        
        serviceScope.launch {
            var nextUri = nextSong.contentUri
            
            // 1. Check dynamic cache & resolve if online source
            val targetVideoId = nextSong.videoId
            if (!targetVideoId.isNullOrEmpty()) {
                val resolvedUrl = withContext(Dispatchers.IO) {
                    YouTubeMusic.getStreamUrl(targetVideoId).getOrNull()
                }
                if (!resolvedUrl.isNullOrEmpty()) {
                    nextUri = resolvedUrl.toUri()
                    Log.d(TAG, "Next track stream URL pre-resolved successfully")
                    
                    // First-chunk byte pre-caching: download first 512KB into SimpleCache
                    preCacheFirstChunk(resolvedUrl)
                }
            }
            
            // 2. Warm-inject into ExoPlayer Natively with mediaId for accurate resync
            withContext(Dispatchers.Main) {
                if (crossfadeDurationMs > 0) {
                    if (nextPlayer == null) {
                        nextPlayer = buildExoPlayer()
                    configurePlayerAudio(nextPlayer!!)
                    }
                    val nextItem = buildMediaItem(nextUri, nextSong.title, nextSong.artist, nextSong.id.toString())
                    nextPlayer?.setMediaItem(nextItem)
                    nextPlayer?.prepare()
                    nextPlayer?.playWhenReady = false
                    nextPlayer?.volume = 0f
                    Log.d(TAG, "⚡ SUCCESSFULLY HOT-QUEUED NEXT SONG INTO CROSSFADE PLAYER! READY FOR OVERLAP TRANSITION.")
                } else {
                    if (player.mediaItemCount < 2) {
                        val nextItem = buildMediaItem(nextUri, nextSong.title, nextSong.artist, nextSong.id.toString())
                        player.addMediaItem(nextItem)
                        Log.d(TAG, "⚡ SUCCESSFULLY HOT-QUEUED NEXT SONG INTO NATIVE PIPELINE! READY FOR ZERO-GAP TRANSITION.")
                    }
                }
            }
        }
    }

    fun refreshNativeQueue() {
        // Clear out any currently preloaded next song
        if (crossfadeDurationMs > 0) {
            nextPlayer?.stop()
            nextPlayer?.clearMediaItems()
            nextPlayer = null
        } else {
            // Keep the currently playing item (index 0) but remove the next ones
            while (player.mediaItemCount > 1) {
                player.removeMediaItem(1)
            }
        }
        // Force a fresh proactive preload with the newly updated queue
        preloadNextSongIntoQueue()
    }

    private fun fetchAndAppendRelatedSongsProactively(videoId: String) {
        if (isFetchingRelatedProactively) return
        isFetchingRelatedProactively = true
        
        serviceScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Native queue is ending. Proactively fetching related songs for autoplay loop from YouTube Music...")
                val result = YouTubeMusic.relatedSongs(videoId)
                result.onSuccess { related ->
                    if (related.isNotEmpty()) {
                        serviceScope.launch(Dispatchers.Main) {
                            viewModel?.let { vm ->
                                val existingIds = vm.songList.value.map { it.id }.toSet()
                                val newSongs = related.map { onlineSong ->
                                    com.codetrio.spatialflow.model.SongItem.createOnlineSong(
                                        onlineSong.videoId,
                                        onlineSong.title,
                                        onlineSong.artist,
                                        "", // streamUrl placeholder
                                        onlineSong.durationMs,
                                        onlineSong.thumbnailUrl,
                                        onlineSong.artistId
                                    )
                                }.filter { it.id !in existingIds }
                                
                                if (newSongs.isNotEmpty()) {
                                    val newList = ArrayList(vm.songList.value)
                                    newList.addAll(newSongs)
                                    vm.setSongList(newList)
                                    Log.d(TAG, "Successfully pre-appended ${newSongs.size} related songs to autoplay queue.")
                                    
                                    // Re-trigger preloading so the newly appended song gets hot-queued immediately!
                                    preloadNextSongIntoQueue()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to proactively fetch related songs for loop", e)
            } finally {
                isFetchingRelatedProactively = false
            }
        }
    }
    
    /**
     * Pre-cache the first 512KB of the audio stream into SimpleCache for instant playback start.
     */
    private fun preCacheFirstChunk(streamUrl: String) {
        if (!getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_MEDIA_CACHE_ENABLED, true)) {
            Log.d(TAG, "Skipping first-chunk pre-cache because audio caching is disabled")
            return
        }
        serviceScope.launch(Dispatchers.IO) {
            try {
                val httpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(streamHttpClient)
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")

                val upstreamFactory = androidx.media3.datasource.DefaultDataSource.Factory(applicationContext, httpDataSourceFactory)
                val cacheDataSourceFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
                    .setCache(getCache(applicationContext))
                    .setUpstreamDataSourceFactory(upstreamFactory)
                    .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                
                val dataSpec = DataSpec.Builder()
                    .setUri(streamUrl)
                    .setLength(512 * 1024) // 512KB first chunk
                    .build()
                
                val cacheWriter = CacheWriter(
                    cacheDataSourceFactory.createDataSource(),
                    dataSpec,
                    null, // no progress listener
                    null  // no cancellation
                )
                cacheWriter.cache()
                Log.d(TAG, "⚡ First-chunk pre-cached (512KB) for instant start")
            } catch (e: Exception) {
                // Non-critical: pre-caching failure just means normal buffering on play
                Log.w(TAG, "First-chunk pre-cache failed (non-critical): ${e.message}")
            }
        }
    }

    fun play() {
        if (!isPlayerInitialized) {
            pendingServiceActions.add(ACTION_PLAY)
            return
        }
        val activeSong = viewModel?.currentSong?.value
        val failedVideoId = failedStreamVideoId
        if (!failedVideoId.isNullOrBlank() && activeSong?.videoId == failedVideoId) {
            loadAndPlay("innertube://$failedVideoId".toUri(), player.currentPosition.coerceAtLeast(0L))
            return
        }

        if (player.playerError != null) {
            val retryUri = viewModel?.songUri?.value ?: originalSourceUri ?: currentSourceUri
            if (retryUri != null) {
                loadAndPlay(retryUri, player.currentPosition.coerceAtLeast(0L))
                return
            }
        }

        if (player.currentMediaItem == null) {
            val uri = viewModel?.songUri?.value
            if (uri != null) {
                val pos = viewModel?.currentPosition?.value ?: 0
                loadAndPlay(uri, pos.toLong())
                return
            }
        }
        if (!player.isPlaying) {
            player.play()
            if (isCrossfading) {
                nextPlayer?.play()
                crossfadeAnimator?.resume()
            }
            isPausedByFocusLoss = false
        }
    }

    fun pause() {
        if (!isPlayerInitialized) {
            pendingServiceActions.add(ACTION_PAUSE)
            return
        }
        if (player.isPlaying) {
            player.pause()
            if (isCrossfading) {
                nextPlayer?.pause()
                crossfadeAnimator?.pause()
            }
            com.codetrio.spatialflow.util.PlaybackStateManager.savePosition(applicationContext, player.currentPosition)
        }
    }

    fun setShuffleModeEnabled(enabled: Boolean) {
        if (!isPlayerInitialized) return
        if (player.shuffleModeEnabled != enabled) {
            player.shuffleModeEnabled = enabled
        }
    }

    fun stop() {
        if (!isPlayerInitialized) {
            pendingPlaybackRequest = null
            pendingServiceActions.clear()
            return
        }
        player.stop()
        if (isCrossfading) {
            nextPlayer?.stop()
            crossfadeAnimator?.cancel()
            crossfadeAnimator = null
        }
        abandonAudioFocus()
    }

    fun seekTo(position: Int) {
        if (!isPlayerInitialized) {
            pendingSeekToPosition = position.toLong()
            return
        }
        player.seekTo(position.toLong())
    }

    fun playPrevious() = viewModel?.playPreviousSong()
    fun playNext() = viewModel?.playNextSong()

    private fun buildMediaItem(uri: Uri): MediaItem {
        val songId = viewModel?.currentSong?.value?.id?.toString() ?: ""
        return buildMediaItem(uri, currentSongName, viewModel?.currentSong?.value?.artist ?: "Unknown Artist", songId)
    }

    private fun buildMediaItem(uri: Uri, title: String, artist: String, songId: String = ""): MediaItem {
        val metadataBuilder = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
        val activeSong = viewModel?.currentSong?.value?.takeIf { it.id.toString() == songId }
        activeSong?.albumName?.takeIf { it.isNotBlank() }?.let { metadataBuilder.setAlbumTitle(it) }
        
        // We only compress current cover for the active track to conserve heap space
        if (title == currentSongName) {
            currentAlbumArt?.let {
                val stream = java.io.ByteArrayOutputStream()
                it.compress(Bitmap.CompressFormat.PNG, 100, stream)
                metadataBuilder.setArtworkData(stream.toByteArray(), androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
        }
        
        if (uri.scheme == "http" || uri.scheme == "https") {
            val cachedSong = viewModel?.songList?.value?.firstOrNull { it.id.toString() == songId }
                ?: viewModel?.currentSong?.value?.takeIf { it.id.toString() == songId }
            rememberMediaCacheMetadata(
                this,
                uri.toString(),
                title,
                artist,
                songId,
                cachedSong?.duration ?: 0L,
                cachedSong?.videoId.orEmpty()
            )
        }

        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(songId) // Stable ID for shuffle-mode resync
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    /** Save the real container MIME after ExoPlayer has identified the active audio track. */
    private fun persistCurrentCacheMimeType() {
        if (!isPlayerInitialized) return
        val item = player.currentMediaItem ?: return
        val uri = item.localConfiguration?.uri ?: return
        if (uri.scheme != "http" && uri.scheme != "https") return

        val audioFormat = player.currentTracks.groups
            .flatMap { group -> (0 until group.length).map { group.getTrackFormat(it) } }
            .firstOrNull { it.sampleMimeType?.startsWith("audio/") == true }
        val mimeType = listOf(audioFormat?.containerMimeType, audioFormat?.sampleMimeType)
            .firstOrNull { candidate ->
                !candidate.isNullOrBlank() && listOf("webm", "mp4", "mpeg", "ogg", "flac", "wav")
                    .any { candidate.contains(it, ignoreCase = true) }
            } ?: audioFormat?.sampleMimeType ?: return

        val activeSong = viewModel?.currentSong?.value
        val durationMs = player.duration.takeIf {
            it > 0L && it != androidx.media3.common.C.TIME_UNSET
        } ?: activeSong?.duration ?: 0L
        rememberMediaCacheMetadata(
            applicationContext,
            uri.toString(),
            item.mediaMetadata.title?.toString() ?: activeSong?.title.orEmpty(),
            item.mediaMetadata.artist?.toString() ?: activeSong?.artist.orEmpty(),
            item.mediaId.ifBlank { activeSong?.id?.toString().orEmpty() },
            durationMs,
            activeSong?.videoId.orEmpty(),
            mimeType
        )
    }

    private fun persistCurrentSongArtwork(bitmap: Bitmap?) {
        val artwork = bitmap ?: return
        val mediaItem = if (isPlayerInitialized) player.currentMediaItem else null
        val activeSong = viewModel?.currentSong?.value
        val mediaUri = mediaItem?.localConfiguration?.uri
        val isRemoteTrack = !activeSong?.videoId.isNullOrBlank() ||
            mediaUri?.scheme == "http" || mediaUri?.scheme == "https"
        if (!isRemoteTrack) return

        val songId = mediaItem?.mediaId?.takeIf { it.isNotBlank() }
            ?: activeSong?.id?.toString()?.takeIf { it.isNotBlank() }
            ?: return
        serviceScope.launch(Dispatchers.IO) {
            storeCachedMediaArtwork(applicationContext, songId, artwork)
        }
    }

    fun getCurrentAudioFormatDetails(songId: String): CurrentAudioFormatDetails? {
        if (!isPlayerInitialized) return null
        return try {
            val activeItem = player.currentMediaItem ?: return null
            if (activeItem.mediaId != songId) return null
            val format = player.currentTracks.groups
                .flatMap { group -> (0 until group.length).map { group.getTrackFormat(it) } }
                .firstOrNull { it.sampleMimeType?.startsWith("audio/") == true }
            val durationMs = player.duration.takeIf {
                it > 0L && it != androidx.media3.common.C.TIME_UNSET
            }
            val details = CurrentAudioFormatDetails(
                bitrateBps = format?.bitrate?.takeIf { it > 0 },
                sampleRateHz = format?.sampleRate?.takeIf { it > 0 },
                mimeType = format?.sampleMimeType ?: format?.containerMimeType,
                durationMs = durationMs,
                albumTitle = activeItem.mediaMetadata.albumTitle?.toString()
                    ?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
            )
            // Duration or descriptive media metadata is still valuable before the audio
            // renderer has reported a complete Format object.
            details.takeIf {
                it.bitrateBps != null || it.sampleRateHz != null || it.mimeType != null ||
                    it.durationMs != null || it.albumTitle != null
            }
        } catch (error: Exception) {
            Log.d(TAG, "Current audio format is not available yet", error)
            null
        }
    }

    fun setSongMetadata(songName: String?, albumArt: Bitmap?) {
        this.currentSongName = songName ?: "SpatialFlow"
        this.currentAlbumArt = albumArt?.let { 
            val size = min(it.width, it.height)
            Bitmap.createBitmap(it, (it.width - size) / 2, (it.height - size) / 2, size, size).scale(512, 512, true)
        }
        persistCurrentSongArtwork(this.currentAlbumArt)
        val mediaItem = player.currentMediaItem
        if (mediaItem != null) {
            val metadataBuilder = mediaItem.mediaMetadata.buildUpon()
                .setTitle(currentSongName)
                .setArtist(viewModel?.currentSong?.value?.artist ?: "Unknown Artist")
            currentAlbumArt?.let {
                val stream = java.io.ByteArrayOutputStream()
                it.compress(Bitmap.CompressFormat.PNG, 100, stream)
                metadataBuilder.setArtworkData(stream.toByteArray(), androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
            val newItem = mediaItem.buildUpon().setMediaMetadata(metadataBuilder.build()).build()
            player.replaceMediaItem(0, newItem)
        }
    }

    fun setSongMetadataById(songName: String?, albumId: Long) {
        serviceScope.launch(Dispatchers.IO) {
            var albumArt: Bitmap? = null
            if (albumId > 0) {
                try {
                    val artUri = "content://media/external/audio/albumart/$albumId".toUri()
                    albumArt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentResolver.loadThumbnail(artUri, Size(512, 512), null)
                    } else {
                        @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(contentResolver, artUri)
                    }
                } catch (_: Exception) {}
            }
            withContext(Dispatchers.Main) {
                setSongMetadata(songName, albumArt)
            }
        }
    }

    fun setSongMetadataByUrl(songName: String?, url: String?) {
        if (url.isNullOrEmpty()) {
            setSongMetadata(songName, null)
            return
        }
        com.bumptech.glide.Glide.with(applicationContext)
            .asBitmap()
            .load(url)
            .override(512, 512)
            .into(object : com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: com.bumptech.glide.request.transition.Transition<in Bitmap>?) {
                    setSongMetadata(songName, resource)
                }
                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                    setSongMetadata(songName, null)
                }
            })
    }

    fun isPlaying(): Boolean = player.isPlaying
    fun getCurrentSourceUri(): Uri? = originalSourceUri ?: currentSourceUri

    private fun loadAudioInternal(uri: Uri, keepPlayingUntilReady: Boolean = false) {
        if (!keepPlayingUntilReady) {
            player.pause()
            player.stop()
            player.clearMediaItems()
        }

        if (isCrossfading) {
            nextPlayer?.stop()
            nextPlayer?.release()
            nextPlayer = null
            crossfadeAnimator?.cancel()
            crossfadeAnimator = null
            isCrossfading = false
            crossfadeNextSongStarted = false
            player.volume = currentBaseVolume
        }

        currentSourceUri = uri
        isNextSongPreCached = false
        
        try {
            val scheme = uri.scheme
            if (scheme == "http" || scheme == "https" || scheme == "innertube") {
                Log.d(TAG, "Loading remote stream: ${uri.toString().take(80)}...")
            }
            
            val currentSong = viewModel?.currentSong?.value
            if (currentSong != null && !currentSong.videoId.isNullOrEmpty()) {
                telemetryManager.onSongChanged(currentSong.videoId!!, currentSong.duration)
            }
            
            player.setMediaItem(buildMediaItem(uri))
            
            // Seek BEFORE prepare to avoid double-buffering pass
            if (pendingSeekPos >= 0) {
                player.seekTo(pendingSeekPos)
                pendingSeekPos = -1
            }
            
            // Effects/normalization were removed; every newly loaded track must start
            // with normal playback gain rather than inherit a stale crossfade volume.
            player.volume = currentBaseVolume.coerceIn(0f, 1f)
            player.prepare()

            if (shouldAutoPlay) {
                // Media3 manages focus from AudioAttributes. Do not gate playback on a
                // separate focus request that can be denied/delayed before service promotion.
                player.play()
                isPausedByFocusLoss = false
                shouldAutoPlay = false
            }

            // Proactively stack the next song for gapless transition
            handler.postDelayed({
                preloadNextSongIntoQueue()
            }, 200)
        } catch (e: Exception) {
            viewModel?.setLoading(false)
            Log.e(TAG, "Failed to load audio: ${e.message}")
        }
    }

    private fun setupProgressTracking() {
        progressUpdateRunnable = object : Runnable {
            override fun run() {
                if (player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED) {
                    val pos = player.currentPosition
                    val dur = player.duration
                    viewModel?.setCurrentPosition(pos.toInt())
                    if (dur > 0) viewModel?.setDuration(dur.toInt())
                    
                    telemetryManager.updateProgress(pos, dur)
                    
                    // Secure persistence periodically (approx every 5 seconds at 250ms tick rate = every 20 cycles)
                    // We use static modulo counter logic.
                    if (player.isPlaying && (pos / 250) % 20L == 0L) {
                        com.codetrio.spatialflow.util.PlaybackStateManager.savePosition(applicationContext, pos)
                    }

                    val lyricsModeActive = viewModel?.isLyricsModeEnabled?.value == true
                    val nowMs = System.currentTimeMillis()
                    val shouldUpdateWidget = if (lyricsModeActive) {
                        nowMs - lastWidgetProgressUpdateAtMs >= 1_000L
                    } else {
                        pos % 200L < 100L
                    }
                    if (player.isPlaying && shouldUpdateWidget) {
                        lastWidgetProgressUpdateAtMs = nowMs
                        updateWidgetProgress(pos, dur)
                    }

                    if (player.isPlaying && crossfadeDurationMs > 0 && dur > 0 && (dur - pos) <= crossfadeDurationMs && !isCrossfading) {
                        startCrossfadeOut()
                    }

                    // Predictive Pre-Caching at 80% playback progression (shuffle-aware)
                    if (player.isPlaying && dur > 0 && !isNextSongPreCached && (pos.toFloat() / dur.toFloat() >= 0.8f)) {
                        isNextSongPreCached = true
                        viewModel?.let { vm ->
                            val songs = vm.songList.value
                            val nextIndex = vm.getNextSongIndex()
                            if (nextIndex >= 0 && nextIndex < songs.size) {
                                val nextSong = songs[nextIndex]
                                YouTubeMusic.preCacheNextSong(nextSong)
                            }
                        }
                    }
                }
                val progressIntervalMs = if (viewModel?.isLyricsModeEnabled?.value == true && player.isPlaying) 80L else 250L
                handler.postDelayed(this, progressIntervalMs)
            }
        }
        handler.post(progressUpdateRunnable)
    }

    /**
     * Prefer time-synchronised lyrics; when only plain lyrics exist, show several readable
     * lines instead of replacing the widget content with "..." on every progress tick.
     */
    private fun currentWidgetLyricText(positionMs: Long): String {
        val vm = viewModel ?: return "..."
        val synced = vm.syncedLyrics.value
        if (!synced.isNullOrEmpty()) {
            return SpatialFlowWidgetProvider.findCurrentLyricLine(synced, positionMs.toInt())
                ?.takeIf { it.isNotBlank() } ?: "..."
        }
        val plain = vm.plainLyrics.value.orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(4)
            .joinToString("\n")
        return plain.ifBlank { "..." }
    }

    private fun updateWidgetProgress(pos: Long, dur: Long) {
        SpatialFlowWidgetProvider.updateAllWidgetsPartial(
            this,
            pos.toInt(),
            dur.toInt(),
            currentWidgetLyricText(pos)
        )
    }

    fun updateWidgetState(isPlaying: Boolean) {
        viewModel?.let { vm ->
            val song = vm.currentSong.value
            val pos = player.currentPosition
            val dur = player.duration
            SpatialFlowWidgetProvider.updateAllWidgets(
                this,
                song,
                isPlaying,
                pos.toInt(),
                dur.toInt(),
                currentWidgetLyricText(pos)
            )
        }
    }

    private fun setupMediaSession() {
        createNotificationChannel()
        val intent = Intent(this, com.codetrio.spatialflow.MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Force standard commands to be available so buttons show in System UI
        val forwardingPlayer = object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_PREVIOUS)
                    .add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(COMMAND_SET_SHUFFLE_MODE)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    COMMAND_SEEK_TO_NEXT,
                    COMMAND_SEEK_TO_PREVIOUS,
                    COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    COMMAND_SET_SHUFFLE_MODE -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
                super.setShuffleModeEnabled(shuffleModeEnabled)
                viewModel?.let { vm ->
                    if (vm.isShuffleEnabled.value != shuffleModeEnabled) {
                        vm.setShuffleEnabled(shuffleModeEnabled)
                    }
                }
            }

            override fun seekToNext() { playNext() }
            override fun seekToPrevious() { playPrevious() }
            override fun seekToNextMediaItem() { playNext() }
            override fun seekToPreviousMediaItem() { playPrevious() }
        }

        val session = MediaSession.Builder(this, forwardingPlayer)
            .setId(TAG)
            .setCallback(mediaSessionCallback)
            .setSessionActivity(pendingIntent)
            .build()
            
        mediaSession = session
        addSession(session)

        setMediaNotificationProvider(CustomMediaNotificationProvider(this))
        updateMediaSessionCustomLayout()
    }

    private enum class NotificationPlaybackMode {
        SHUFFLE,
        LOOP_ONE,
        LOOP_ALL
    }

    private fun currentNotificationPlaybackMode(vm: PlayerSharedViewModel?): NotificationPlaybackMode {
        val repeatMode = vm?.repeatMode?.value ?: PlayerSharedViewModel.REPEAT_OFF
        val shuffleEnabled = vm?.isShuffleEnabled?.value == true
        return when {
            shuffleEnabled -> NotificationPlaybackMode.SHUFFLE
            repeatMode == PlayerSharedViewModel.REPEAT_ONE -> NotificationPlaybackMode.LOOP_ONE
            repeatMode == PlayerSharedViewModel.REPEAT_ALL -> NotificationPlaybackMode.LOOP_ALL
            else -> NotificationPlaybackMode.SHUFFLE
        }
    }

    private fun notificationPlaybackModeIcon(mode: NotificationPlaybackMode): Int {
        return when (mode) {
            NotificationPlaybackMode.SHUFFLE -> R.drawable.ic_shuffle_noti
            NotificationPlaybackMode.LOOP_ONE -> R.drawable.ic_repeat_one_noti
            NotificationPlaybackMode.LOOP_ALL -> R.drawable.ic_repeat_noti
        }
    }

    private fun cycleNotificationPlaybackMode() {
        val vm = viewModel ?: return
        val repeatMode = vm.repeatMode.value
        val shuffleEnabled = vm.isShuffleEnabled.value
        when {
            !shuffleEnabled && repeatMode == PlayerSharedViewModel.REPEAT_OFF -> {
                vm.setRepeatMode(PlayerSharedViewModel.REPEAT_OFF)
                vm.setShuffleEnabled(true)
            }
            shuffleEnabled -> {
                vm.setShuffleEnabled(false)
                vm.setRepeatMode(PlayerSharedViewModel.REPEAT_ONE)
            }
            repeatMode == PlayerSharedViewModel.REPEAT_ONE -> {
                vm.setShuffleEnabled(false)
                vm.setRepeatMode(PlayerSharedViewModel.REPEAT_ALL)
            }
            else -> {
                vm.setShuffleEnabled(false)
                vm.setRepeatMode(PlayerSharedViewModel.REPEAT_OFF)
            }
        }
        updateMediaSessionCustomLayout()
        updateWidgetState(player.isPlaying)
    }

    fun updateMediaSessionCustomLayout() {
        val session = mediaSession ?: return
        val vm = viewModel

        val mode = currentNotificationPlaybackMode(vm)
        val modeButton = CommandButton.Builder()
            .setDisplayName("Playback Mode")
            .setSessionCommand(CMD_CYCLE_PLAYBACK_MODE)
            .setIconResId(notificationPlaybackModeIcon(mode))
            .setEnabled(true)
            .build()
            
        val favIcon = if (vm?.isCurrentSongFavorite?.value == true) R.drawable.ic_favorite else R.drawable.ic_favorite_border
        val favButton = CommandButton.Builder()
            .setDisplayName("Favorite")
            .setSessionCommand(CMD_TOGGLE_FAV)
            .setIconResId(favIcon)
            .setEnabled(true)
            .build()
            
        session.setCustomLayout(listOf(modeButton, favButton))
    }

    private val mediaSessionCallback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .build()

            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(CMD_TOGGLE_LOOP)
                .add(CMD_CYCLE_PLAYBACK_MODE)
                .add(CMD_TOGGLE_FAV)
                .build()

            return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_TOGGLE_LOOP -> {
                    viewModel?.toggleLoopMode()
                    updateWidgetState(player.isPlaying)
                }
                ACTION_CYCLE_PLAYBACK_MODE -> {
                    cycleNotificationPlaybackMode()
                }
                ACTION_TOGGLE_FAV -> viewModel?.toggleFavorite()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private fun getActionIntent(action: String, requestCode: Int): android.app.PendingIntent {
        val intent = Intent(this, AudioPlaybackService::class.java).apply { this.action = action }
        return android.app.PendingIntent.getService(
            this, requestCode, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "audio_playback_channel", "Audio Playback", android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows currently playing audio"
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                enableVibration(false)
                setSound(null, null)
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }
    }




    private fun requestAudioFocus(): Boolean {
        if (!audioFocusEnabled) return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttrs = LegacyAudioAttributes.Builder()
                .setUsage(LegacyAudioAttributes.USAGE_MEDIA)
                .setContentType(LegacyAudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttrs)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            audioManager?.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private fun startCrossfadeOut() {
        if (isCrossfading || nextPlayer == null) return
        isCrossfading = true
        crossfadeNextSongStarted = true

        val incomingPlayer = nextPlayer!!
        incomingPlayer.volume = 0f
        incomingPlayer.play()

        crossfadeAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = crossfadeDurationMs.toLong()
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                player.volume = currentBaseVolume * (1f - fraction)
                incomingPlayer.volume = currentBaseVolume * fraction
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    completeCrossfadeHandoff()
                }
            })
            start()
        }
    }

    private fun completeCrossfadeHandoff() {
        crossfadeAnimator = null
        val oldPlayer = player
        val newPlayer = nextPlayer ?: return

        player = newPlayer
        

        // Stop old player and release it
        oldPlayer.stop()
        oldPlayer.removeListener(mainPlayerListener)
        oldPlayer.release()

        // Re-initialize a fresh nextPlayer for the future
        nextPlayer = buildExoPlayer()
                    configurePlayerAudio(nextPlayer!!)

        // Apply listeners to the new primary player
        player.addListener(mainPlayerListener)
        mediaSession?.player = player

        // Re-attach hardware audio effects to the new audio session ID!

        isCrossfading = false
        crossfadeNextSongStarted = false

        // Inform the view model that a transition has occurred
        viewModel?.let { vm ->
            val nextIdx = vm.getNextSongIndex()
            if (nextIdx >= 0) {
                vm.updateSongIndexOnly(nextIdx)
                vm.songList.value.getOrNull(nextIdx)?.let { song ->
                    currentSongName = song.title
                    if (song.thumbnailUrl != null) {
                        setSongMetadataByUrl(song.title, song.thumbnailUrl)
                    } else {
                        setSongMetadataById(song.title, song.albumId)
                    }
                    if (!song.videoId.isNullOrEmpty()) {
                        telemetryManager.onSongChanged(song.videoId!!, song.duration)
                    }
                }
                // Preload the next song into the newly initialized nextPlayer
                handler.postDelayed({ preloadNextSongIntoQueue() }, 300)
            }
        }
    }

    private fun handlePlaybackCompleted() {
        if (isCrossfading && crossfadeNextSongStarted) return
        viewModel?.let { vm ->
            vm.setCurrentPosition(0)
            
            handler.post { vm.playNextSong() }
        }
    }

    private fun loadAudioPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        crossfadeDurationMs = (prefs.getFloat(KEY_CROSSFADE_DURATION, 0f) * 1000).toInt()
        audioFocusEnabled = prefs.getBoolean(KEY_AUDIO_FOCUS, true)
        if (::player.isInitialized) configurePlayerAudio(player)
        nextPlayer?.let(::configurePlayerAudio)
    }

    private fun setupPreferenceListener() {
        prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (KEY_CROSSFADE_DURATION == key || KEY_AUDIO_FOCUS == key) loadAudioPreferences()
            if (com.codetrio.spatialflow.ui.SettingsViewModel.KEY_VOLUME_NORMALIZATION_ENABLED == key || 
                com.codetrio.spatialflow.ui.SettingsViewModel.KEY_TARGET_LUFS == key) {
                }
        }
    }

    fun getPlayerInstance(): ExoPlayer = player

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlaybackService = this@AudioPlaybackService
    }

    @OptIn(UnstableApi::class)
    private inner class CustomMediaNotificationProvider(context: Context) : 
        DefaultMediaNotificationProvider(context) {
        
        override fun addNotificationActions(
            mediaSession: MediaSession,
            mediaButtons: com.google.common.collect.ImmutableList<CommandButton>,
            notificationBuilder: androidx.core.app.NotificationCompat.Builder,
            actionFactory: androidx.media3.session.MediaNotification.ActionFactory
        ): IntArray {
            val isPlaying = player.isPlaying
            Log.d(TAG, "Notification update: isPlaying=$isPlaying, song=$currentSongName")
            
            // Explicitly set channel and properties to match Java example exactly
            notificationBuilder.setSmallIcon(R.drawable.ic_music_note)
            notificationBuilder.setContentTitle(currentSongName)
            notificationBuilder.setContentText("Now Playing")
            notificationBuilder.setSubText("SpatialFlow")
            notificationBuilder.setChannelId("audio_playback_channel")
            notificationBuilder.setOngoing(isPlaying)
            notificationBuilder.setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            notificationBuilder.setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
            notificationBuilder.setOnlyAlertOnce(true)
            notificationBuilder.setShowWhen(false)
            notificationBuilder.setCategory(androidx.core.app.NotificationCompat.CATEGORY_TRANSPORT)
            
            currentAlbumArt?.let {
                notificationBuilder.setLargeIcon(it)
            }

            // Order: Mode | Prev | Play | Next | Fav
            notificationBuilder.clearActions()
            
            // 0: Playback mode cycle: Shuffle -> Loop One -> Loop
            val mode = currentNotificationPlaybackMode(viewModel)
            notificationBuilder.addAction(
                notificationPlaybackModeIcon(mode),
                "Mode",
                getActionIntent(ACTION_CYCLE_PLAYBACK_MODE, 10)
            )
            
            // 1: Previous
            notificationBuilder.addAction(android.R.drawable.ic_media_previous, "Previous", getActionIntent(ACTION_PREVIOUS, 11))
            
            // 2: Play/Pause
            notificationBuilder.addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, 
                if (isPlaying) "Pause" else "Play", 
                if (isPlaying) getActionIntent(ACTION_PAUSE, 12) else getActionIntent(ACTION_PLAY, 13)
            )
            
            // 3: Next
            notificationBuilder.addAction(android.R.drawable.ic_media_next, "Next", getActionIntent(ACTION_NEXT, 14))
            
            // 4: Favorite
            val favIcon = if (viewModel?.isCurrentSongFavorite?.value == true) R.drawable.ic_favorite else R.drawable.ic_favorite_border
            notificationBuilder.addAction(favIcon, "Favorite", getActionIntent(ACTION_TOGGLE_FAV, 15))
            
            // Compact view indices (Prev, Play, Next)
            val style = androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(mediaSession)
                .setShowActionsInCompactView(1, 2, 3)
            notificationBuilder.setStyle(style)

            return intArrayOf(1, 2, 3)
        }
    }

}


@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class AdjustableCacheEvictor(initialEnabled: Boolean, initialMaxBytes: Long) : CacheEvictor {
    private val lock = Any()
    private val spans = TreeSet<CacheSpan>(Comparator { a, b ->
        val touched = a.lastTouchTimestamp.compareTo(b.lastTouchTimestamp)
        if (touched != 0) touched else {
            val keyOrder = a.key.compareTo(b.key)
            if (keyOrder != 0) keyOrder else a.position.compareTo(b.position)
        }
    })
    @Volatile private var enabled = initialEnabled
    @Volatile private var maxBytes = initialMaxBytes
    @Volatile private var initialized = false
    @Volatile private var activeCache: Cache? = null

    override fun requiresCacheSpanTouches(): Boolean = true
    override fun onCacheInitialized() {
        initialized = true
        activeCache?.let { evictIfNeeded(it) }
    }
    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        activeCache = cache
        if (initialized) evictIfNeeded(cache, length.coerceAtLeast(0L))
    }
    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        activeCache = cache
        synchronized(lock) { spans.add(span) }
        if (initialized) evictIfNeeded(cache)
    }
    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        activeCache = cache
        synchronized(lock) { spans.remove(span) }
    }
    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        activeCache = cache
        synchronized(lock) { spans.remove(oldSpan); spans.add(newSpan) }
        if (initialized) evictIfNeeded(cache)
    }
    fun configure(cache: Cache, enabled: Boolean, maxBytes: Long) {
        this.enabled = enabled
        this.maxBytes = maxBytes.coerceAtLeast(1L)
        activeCache = cache
        // Spans are tracked by onSpanAdded/onSpanRemoved callbacks from SimpleCache. Avoid
        // cache.keys/getCachedSpans scans here: they duplicate SimpleCache's startup index scan.
        if (initialized) evictIfNeeded(cache)
    }
    fun refresh(cache: Cache) {
        activeCache = cache
        synchronized(lock) {
            spans.clear()
            cache.keys.forEach { key -> spans.addAll(cache.getCachedSpans(key)) }
        }
        if (initialized) evictIfNeeded(cache)
    }
    private fun evictIfNeeded(cache: Cache, incomingBytes: Long = 0L) {
        while (true) {
            val removeOne = synchronized(lock) { !enabled || cache.cacheSpace + incomingBytes > maxBytes }
            if (!removeOne) return
            val oldest = synchronized(lock) { if (spans.isEmpty()) null else spans.pollFirst() } ?: return
            try { cache.removeSpan(oldest) }
            catch (error: Exception) { android.util.Log.w("SpatialFlowCache", "Unable to evict cached audio", error) }
        }
    }
}
