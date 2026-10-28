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
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
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
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import android.media.AudioAttributes as LegacyAudioAttributes

@OptIn(UnstableApi::class)
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

        private const val PREFS_NAME = "AppSettings"
        private const val KEY_CROSSFADE_DURATION = "crossfade_duration"
        private const val KEY_AUDIO_FOCUS = "audio_focus"

        val CMD_TOGGLE_LOOP = SessionCommand(ACTION_TOGGLE_LOOP, Bundle.EMPTY)
        val CMD_CYCLE_PLAYBACK_MODE = SessionCommand(ACTION_CYCLE_PLAYBACK_MODE, Bundle.EMPTY)
        val CMD_TOGGLE_FAV = SessionCommand(ACTION_TOGGLE_FAV, Bundle.EMPTY)

        private var cache: SimpleCache? = null

        @Synchronized
        fun getCache(context: Context): SimpleCache {
            if (cache == null) {
                val cacheDir = File(context.cacheDir, "media_cache")
                val evictor = LeastRecentlyUsedCacheEvictor(200 * 1024 * 1024) // 200MB
                val databaseProvider = StandaloneDatabaseProvider(context)
                cache = SimpleCache(cacheDir, evictor, databaseProvider)
            }
            return cache!!
        }
    }

    private val binder: IBinder = LocalBinder()
    private val serviceScope = MainScope()
    internal lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private var viewModel: PlayerSharedViewModel? = null
    private lateinit var handler: Handler
    private lateinit var progressUpdateRunnable: Runnable

    private var currentSourceUri: Uri? = null
    private var originalSourceUri: Uri? = null
    private var isNextSongPreCached = false
    private var currentSongName = "SpatialFlow"
    private var currentAlbumArt: Bitmap? = null
    private var shouldAutoPlay = false
    private var pendingSeekPos: Long = -1
    
    // Instant cancellation of stale stream resolutions — prevents race conditions on rapid clicks
    private var resolveJob: Job? = null
    // Track the unique song ID that the current resolution is targeting
    private var resolveTargetSongId: String? = null
    private var lastPlaybackRecoveryVideoId: String? = null
    private var failedStreamVideoId: String? = null
    private var forceInnerTubeOnNextResolveVideoId: String? = null
    private var isRecoveringPlaybackError = false
    private var isFetchingRelatedProactively = false

    private lateinit var telemetryManager: com.codetrio.spatialflow.data.innertube.YouTubeTelemetryManager

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isPausedByFocusLoss = false
    private var audioFocusEnabled = true

    private var mainProcessor = HapticsAudioProcessor()
    private var nextProcessor: HapticsAudioProcessor? = null

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
            val isPlayingState = player.playWhenReady && state == Player.STATE_READY
            viewModel?.postIsPlaying(isPlayingState)

            if (state == Player.STATE_READY) {
                viewModel?.setPlaybackReady(true)
                if (pendingSeekPos >= 0) {
                    player.seekTo(pendingSeekPos)
                    pendingSeekPos = -1
                }
                ensureHapticManager()
            }
            
            if (state == Player.STATE_ENDED) {
                handlePlaybackCompleted()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "ExoPlayer error: ${error.message}", error)
            viewModel?.setIsPlaying(false)

            val song = viewModel?.currentSong?.value
            val videoId = song?.videoId?.takeIf { it.isNotBlank() }
            if (videoId != null && lastPlaybackRecoveryVideoId != videoId) {
                lastPlaybackRecoveryVideoId = videoId
                val resumePosition = player.currentPosition.coerceAtLeast(0L)
                Log.w(TAG, "Retrying failed stream once with a fresh URL for $videoId")
                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Retrying song stream…")
                handler.postDelayed({
                    if (viewModel?.currentSong?.value?.videoId == videoId) {
                        YouTubeMusic.invalidateStreamUrl(videoId)
                        forceInnerTubeOnNextResolveVideoId = videoId
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
        
        initializePlayer()
        setupMediaSession()
        loadAudioPreferences()
        setupPreferenceListener()
        setupProgressTracking()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY -> play()
                ACTION_PAUSE -> pause()
                ACTION_PREVIOUS -> playPrevious()
                ACTION_NEXT -> playNext()
                ACTION_TOGGLE_LOOP -> {
                    viewModel?.toggleLoopMode()
                    updateWidgetState(player.isPlaying)
                }
                ACTION_CYCLE_PLAYBACK_MODE -> {
                    cycleNotificationPlaybackMode()
                }
                ACTION_TOGGLE_FAV -> {
                    viewModel?.toggleFavorite()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
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
        handler.removeCallbacks(progressUpdateRunnable)
        releaseHapticManager()
        abandonAudioFocus()
        
        viewModel?.cleanupBgScope()
        player.removeListener(mainPlayerListener)
        serviceScope.cancel()
        player.release()
        nextPlayer?.release()
        mediaSession?.release()
    }

    private fun initializePlayer() {
        player = buildExoPlayer(mainProcessor)
        val attrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        
        player.setAudioAttributes(attrs, false) 
        player.setWakeMode(C.WAKE_MODE_NETWORK) 
        player.skipSilenceEnabled = false
        player.addListener(mainPlayerListener)
    }

    private fun buildExoPlayer(processor: HapticsAudioProcessor): ExoPlayer {
        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioRenderers(
                context: Context, extensionRendererMode: Int, mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
                enableDecoderFallback: Boolean, audioSink: androidx.media3.exoplayer.audio.AudioSink, eventHandler: Handler,
                eventListener: androidx.media3.exoplayer.audio.AudioRendererEventListener, out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
            ) {
                val customAudioSink = androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(
                        processor
                    )).build()
                super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback, customAudioSink, eventHandler, eventListener, out)
            }
        }
        
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0") // CRITICAL: Prevent ExoPlayer 403 blocks
            .setConnectTimeoutMs(5000)
            .setReadTimeoutMs(5000)
            .setAllowCrossProtocolRedirects(true)
            
        val upstreamFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpDataSourceFactory)
        
        // ULTIMATE FIX: Directly feed upstream into cache, ABSOLUTELY NO blocking dynamic resolver architecture inside pipeline
        val cacheDataSourceFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(getCache(this))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
            .setDataSourceFactory(cacheDataSourceFactory)

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                50000,  // minBufferMs (Aggressively buffer 50s to prevent starvation)
                60000,  // maxBufferMs (Allow up to 60s in memory)
                250,    // bufferForPlaybackMs (Extreme Reactivity - start playing after 250ms)
                1500    // bufferForPlaybackAfterRebufferMs (Recover quickly with 1.5s buffer)
            )
            .setPrioritizeTimeOverSizeThresholds(true) // Ensures playback starts instantly on low buffer
            .build()

        return ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
    }

    fun setViewModel(vm: PlayerSharedViewModel) {
        val oldVm = this.viewModel
        if (oldVm != null && oldVm !== vm) {
            // Memory Leak Extermination: 
            // The old ViewModel instance became a zombie when the Activity was destroyed.
            // Before discarding it, copy its queue and playback state to the new UI ViewModel.
            vm.restoreStateFrom(oldVm)
            oldVm.cleanupBgScope()
        }
        this.viewModel = vm
        
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
        shouldAutoPlay = false
        pendingSeekPos = startPosition
        resolveAndLoad(uri)
    }

    @JvmOverloads
    fun loadAndPlay(uri: Uri, startPosition: Long = -1) {
        // A user-initiated track load permits one new recovery attempt. The automatic
        // recovery itself must not reset its guard, or a broken URL can retry forever.
        if (!isRecoveringPlaybackError) lastPlaybackRecoveryVideoId = null
        shouldAutoPlay = true
        pendingSeekPos = startPosition
        resolveAndLoad(uri)
    }

    /**
     * Centralized execution gate resolving dynamic identifiers OUTSIDE the buffer pipeline
     * prior to injecting raw HTTP/File resource pointers into ExoPlayer.
     */
    private fun resolveAndLoad(uri: Uri) {
        originalSourceUri = uri
        if (uri.scheme == "innertube") {
            val videoId = uri.host ?: return
            val useInnerTubeRetry = forceInnerTubeOnNextResolveVideoId == videoId
            if (useInnerTubeRetry) forceInnerTubeOnNextResolveVideoId = null
            
            // INSTANT CANCELLATION: Kill any in-flight resolution immediately
            resolveJob?.cancel()
            resolveTargetSongId = videoId
            
            resolveJob = serviceScope.launch {
                Log.d(TAG, "Pre-resolving stream ahead of injector injection: $videoId")
                
                // Fetch stream URL only, don't wait for position yet
                var streamUrl = async(Dispatchers.IO) {
                    if (useInnerTubeRetry) {
                        YouTubeMusic.getStreamUrlFromInnerTube(videoId).getOrNull()
                    } else {
                        YouTubeMusic.getStreamUrl(videoId).getOrNull()
                    }
                }.await()

                if (streamUrl.isNullOrEmpty()) {
                    if (useInnerTubeRetry) {
                        Log.w(TAG, "InnerTube recovery URL unavailable for $videoId; trying a fresh NewPipe extraction")
                        streamUrl = async(Dispatchers.IO) {
                            YouTubeMusic.getStreamUrl(videoId).getOrNull()
                        }.await()
                    } else {
                        Log.w(TAG, "Initial stream resolution failed for $videoId; invalidating caches and retrying once")
                        withContext(Dispatchers.IO) { YouTubeMusic.invalidateStreamUrl(videoId) }
                        delay(250L)
                        streamUrl = async(Dispatchers.IO) {
                            YouTubeMusic.getStreamUrl(videoId).getOrNull()
                        }.await()
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
                        nextProcessor = HapticsAudioProcessor()
                        nextPlayer = buildExoPlayer(nextProcessor!!)
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
        serviceScope.launch(Dispatchers.IO) {
            try {
                val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
                    .setConnectTimeoutMs(5000)
                    .setReadTimeoutMs(5000)
                    .setAllowCrossProtocolRedirects(true)
                
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
        if (!player.isPlaying && requestAudioFocus()) {
            player.play()
            if (isCrossfading) {
                nextPlayer?.play()
                crossfadeAnimator?.resume()
            }
            isPausedByFocusLoss = false
        }
    }

    fun pause() {
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
        if (player.shuffleModeEnabled != enabled) {
            player.shuffleModeEnabled = enabled
        }
    }

    fun stop() {
        player.stop()
        if (isCrossfading) {
            nextPlayer?.stop()
            crossfadeAnimator?.cancel()
            crossfadeAnimator = null
        }
        abandonAudioFocus()
    }

    fun seekTo(position: Int) {
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
        
        // We only compress current cover for the active track to conserve heap space
        if (title == currentSongName) {
            currentAlbumArt?.let {
                val stream = java.io.ByteArrayOutputStream()
                it.compress(Bitmap.CompressFormat.PNG, 100, stream)
                metadataBuilder.setArtworkData(stream.toByteArray(), androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
        }
        
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(songId) // Stable ID for shuffle-mode resync
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    fun setSongMetadata(songName: String?, albumArt: Bitmap?) {
        this.currentSongName = songName ?: "SpatialFlow"
        this.currentAlbumArt = albumArt?.let { 
            val size = min(it.width, it.height)
            Bitmap.createBitmap(it, (it.width - size) / 2, (it.height - size) / 2, size, size).scale(512, 512, true)
        }
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
            nextProcessor = null
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
                if (requestAudioFocus()) {
                    player.play()
                    isPausedByFocusLoss = false
                }
                shouldAutoPlay = false
            }

            // Proactively stack the next song for gapless transition
            handler.postDelayed({
                preloadNextSongIntoQueue()
            }, 200)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load audio: ${e.message}")
        }
    }

    private fun ensureHapticManager() {
        viewModel?.let { vm ->
            if (vm.hapticManager == null) {
                vm.hapticManager = com.codetrio.spatialflow.util.PlayerHapticManager(applicationContext)
            }
        }
    }

    private fun releaseHapticManager() {
        viewModel?.let { vm ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                vm.hapticManager?.release()
            }
            vm.hapticManager = null
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

                    if (player.isPlaying && pos % 200 < 100) {
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
                handler.postDelayed(this, 250)
            }
        }
        handler.post(progressUpdateRunnable)
    }

    private fun updateWidgetProgress(pos: Long, dur: Long) {
        var lyricText = "..."
        viewModel?.syncedLyrics?.value?.let { lyrics ->
            if (lyrics.isNotEmpty()) {
                lyricText = SpatialFlowWidgetProvider.findCurrentLyricLine(lyrics, pos.toInt()) ?: "..."
            }
        }
        SpatialFlowWidgetProvider.updateAllWidgetsPartial(this, pos.toInt(), dur.toInt(), lyricText)
    }

    fun updateWidgetState(isPlaying: Boolean) {
        viewModel?.let { vm ->
            val song = vm.currentSong.value
            val pos = player.currentPosition.toInt()
            val dur = player.duration.toInt()
            var lyricText = "..."
            vm.syncedLyrics.value?.let { lyrics ->
                if (lyrics.isNotEmpty()) {
                    lyricText = SpatialFlowWidgetProvider.findCurrentLyricLine(lyrics, pos) ?: "..."
                }
            }
            SpatialFlowWidgetProvider.updateAllWidgets(this, song, isPlaying, pos, dur, lyricText)
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
        mainProcessor = nextProcessor!!
        

        // Stop old player and release it
        oldPlayer.stop()
        oldPlayer.removeListener(mainPlayerListener)
        oldPlayer.release()

        // Re-initialize a fresh nextPlayer for the future
        nextProcessor = HapticsAudioProcessor()
        nextPlayer = buildExoPlayer(nextProcessor!!)

        // Apply listeners to the new primary player
        player.addListener(mainPlayerListener)
        mediaSession?.player = player

        // Re-attach hardware audio effects to the new audio session ID!
        ensureHapticManager()

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

    inner class HapticsAudioProcessor : androidx.media3.common.audio.BaseAudioProcessor() {

        // Optional haptics analysis state. Audio output itself remains byte-for-byte pass-through.
        private var subBassFilterState = 0f
        private var bassFilterState = 0f
        private var midFilterState = 0f

        override fun onConfigure(inputAudioFormat: androidx.media3.common.audio.AudioProcessor.AudioFormat): androidx.media3.common.audio.AudioProcessor.AudioFormat {
            // Keep the decoder's format unchanged. Rewriting mono/multichannel streams to stereo
            // can overflow buffers or fail decoding on unusual local files and stream formats.
            return inputAudioFormat
        }

        @OptIn(UnstableApi::class)
        override fun queueInput(inputBuffer: ByteBuffer) {
            val format = inputAudioFormat
            val is16Bit = format.encoding == C.ENCODING_PCM_16BIT
            val isFloat = format.encoding == C.ENCODING_PCM_FLOAT
            val isMonoOrStereo = format.channelCount in 1..2
            if ((is16Bit || isFloat) && isMonoOrStereo) {
                val bytesPerSample = if (is16Bit) 2 else 4
                val frameSize = bytesPerSample * format.channelCount
                val frameCount = inputBuffer.remaining() / frameSize
                analyzePcmForHaptics(inputBuffer, is16Bit, format.channelCount == 2, frameCount)
            }

            val outBuffer = replaceOutputBuffer(inputBuffer.remaining())
            outBuffer.put(inputBuffer)
            outBuffer.flip()
        }

        @OptIn(UnstableApi::class)
        private fun analyzePcmForHaptics(buffer: ByteBuffer, is16Bit: Boolean, isStereo: Boolean, frameCount: Int) {
            val haptic = viewModel?.hapticManager
            if (haptic == null || !haptic.isHapticsEnabled) return

            var sumSubBass = 0f
            var sumBass = 0f
            var sumMid = 0f
            var sumHigh = 0f
            var count = 0

            // Downsample by processing every 8th frame to save CPU
            val step = 8
            val bytesPerSample = if (is16Bit) 2 else 4
            val bytesPerFrame = bytesPerSample * (if (isStereo) 2 else 1)
            val startPos = buffer.position()

            for (i in 0 until frameCount step step) {
                val bytePos = startPos + i * bytesPerFrame
                if (bytePos + bytesPerFrame > buffer.limit()) break

                val sampleVal = if (is16Bit) {
                    val left = buffer.getShort(bytePos).toFloat()
                    val right = if (isStereo) buffer.getShort(bytePos + 2).toFloat() else left
                    (left + right) / (2f * 32768f)
                } else {
                    val left = buffer.getFloat(bytePos)
                    val right = if (isStereo) buffer.getFloat(bytePos + 4) else left
                    (left + right) / 2f
                }

                // Apply running DSP crossover filters (1st order IIR)
                subBassFilterState = 0.007f * sampleVal + 0.993f * subBassFilterState
                bassFilterState = 0.028f * sampleVal + 0.972f * bassFilterState
                midFilterState = 0.42f * sampleVal + 0.58f * midFilterState

                val subBassSample = subBassFilterState
                val bassSample = bassFilterState - subBassFilterState
                val midSample = midFilterState - bassFilterState
                val highSample = sampleVal - midFilterState

                sumSubBass += kotlin.math.abs(subBassSample)
                sumBass += kotlin.math.abs(bassSample)
                sumMid += kotlin.math.abs(midSample)
                sumHigh += kotlin.math.abs(highSample)
                count++
            }

            if (count > 0) {
                // Map the accumulated band absolute averages to normalized visualizer-equivalent inputs (0 to 1 range)
                val subBassEnergy = (sumSubBass / count) * 4f
                val bassEnergy = (sumBass / count) * 4f
                val midEnergy = (sumMid / count) * 4f
                val highEnergy = (sumHigh / count) * 4f

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    haptic.processPcmHaptics(
                        subBassEnergy.coerceIn(0f, 1f),
                        bassEnergy.coerceIn(0f, 1f),
                        midEnergy.coerceIn(0f, 1f),
                        highEnergy.coerceIn(0f, 1f)
                    )
                }
            }
        }

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
