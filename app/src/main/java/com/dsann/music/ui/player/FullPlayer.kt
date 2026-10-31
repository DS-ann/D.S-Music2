package com.dsann.music.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsann.music.MainActivity
import com.dsann.music.SpatialFlowApplication
import com.dsann.music.ui.SettingsViewModel
import com.dsann.music.R
import com.dsann.music.data.lyrics.LyricLine
import com.dsann.music.data.lyrics.LyricsResult
import com.dsann.music.model.SongItem
import com.dsann.music.viewmodel.PlayerSharedViewModel
import com.dsann.music.data.innertube.YouTubeMusic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

@Composable
private fun PlayerVisualizer(
    style: String,
    isPlaying: Boolean,
    color: Color,
    colorMode: String = "Accent",
    isVisible: Boolean
) {
    val phase = remember { Animatable(0f) }
    val lowRamProfile = SpatialFlowApplication.isLowRamDevice
    LaunchedEffect(style, isPlaying, isVisible) {
        if (!isVisible || !isPlaying || style == "Off") {
            phase.snapTo(0f)
        } else {
            while (true) {
                phase.animateTo(1f, animationSpec = tween(900, easing = LinearEasing))
                phase.snapTo(0f)
            }
        }
    }

    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxWidth().height(
            when (style) {
                "Ring" -> 36.dp
                "Bars" -> 24.dp
                else -> 30.dp
            }
        )
    ) {
        val phaseValue = phase.value
        val phaseAngle = phaseValue * 6.2831855f
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val centerY = center.y

        fun spectrumColor(position: Float): Color {
            if (colorMode != "Spectrum") return color
            val hue = (position.coerceIn(0f, 1f) * 300f + phaseValue * 70f) % 360f
            return Color.hsv(hue, 0.78f, 0.96f)
        }

        when (style) {
            // Keep the compact bar style as-is; it is already the strongest visual.
            "Bars" -> {
                val count = if (lowRamProfile) 20 else 32
                val gap = 3.dp.toPx()
                val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
                repeat(count) { index ->
                    val wave = kotlin.math.abs(kotlin.math.sin(index * 0.72f + phaseValue * 6.28f))
                    val height = size.height * (0.18f + wave * if (isPlaying) 0.78f else 0.16f)
                    val x = index * (barWidth + gap)
                    val barColor = if (colorMode == "Spectrum") {
                        Color.hsv((index * 360f / count + phaseValue * 140f) % 360f, 0.78f, 0.96f)
                    } else color
                    drawRoundRect(
                        barColor.copy(alpha = 0.35f + wave * 0.55f),
                        topLeft = androidx.compose.ui.geometry.Offset(x, centerY - height / 2f),
                        size = androidx.compose.ui.geometry.Size(barWidth, height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                    )
                }
            }

            // Layered harmonics and a changing amplitude envelope make the trace
            // feel closer to a recorded waveform than a single travelling sine.
            "Wave" -> {
                drawLine(
                    color.copy(alpha = 0.12f),
                    start = androidx.compose.ui.geometry.Offset(0f, centerY),
                    end = androidx.compose.ui.geometry.Offset(size.width, centerY),
                    strokeWidth = 1.dp.toPx()
                )

                val points = if (lowRamProfile) 32 else 64
                repeat(if (lowRamProfile) 1 else 2) { layer ->
                    val path = androidx.compose.ui.graphics.Path()
                    repeat(points + 1) { index ->
                        val fraction = index / points.toFloat()
                        val x = size.width * fraction
                        val envelope = 0.22f + 0.78f * kotlin.math.abs(
                            kotlin.math.sin(fraction * 10.5f + layer * 0.9f)
                        )
                        val fundamental = kotlin.math.sin(
                            fraction * 18f + phaseAngle * (0.9f + layer * 0.16f)
                        )
                        val harmonic = kotlin.math.sin(
                            fraction * 37f - phaseAngle * 0.7f + layer * 0.4f
                        )
                        val detail = kotlin.math.sin(fraction * 71f + phaseAngle * 0.45f)
                        val signal = fundamental * 0.56f + harmonic * 0.29f + detail * 0.15f
                        val activity = if (isPlaying) 0.42f else 0.07f
                        val layerScale = if (layer == 0) 1f else 0.62f
                        val y = centerY + signal * envelope * size.height * activity * layerScale
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(
                            colors = List(if (lowRamProfile) 7 else 13) { index ->
                                spectrumColor(index / 12f).copy(
                                    alpha = if (layer == 0) 0.92f else 0.30f
                                )
                            }
                        ),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = if (layer == 0) 1.8.dp.toPx() else 1.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                }
            }

            // A compact circular spectrum: fine radial ticks plus two rotating arcs.
            "Ring" -> {
                val outerRadius = size.height * 0.43f
                drawCircle(
                    color.copy(alpha = 0.12f),
                    radius = outerRadius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )

                val tickCount = if (lowRamProfile) 22 else 36
                repeat(tickCount) { index ->
                    val angle = index * (6.2831855f / tickCount) - 1.5707963f
                    val level = kotlin.math.abs(
                        kotlin.math.sin(index * 0.52f + phaseAngle * 1.35f)
                    )
                    val activity = if (isPlaying) 0.25f + level * 0.75f else 0.18f
                    val innerRadius = size.height * 0.34f
                    val tickLength = size.height * (0.035f + activity * 0.075f)
                    val start = androidx.compose.ui.geometry.Offset(
                        center.x + kotlin.math.cos(angle) * innerRadius,
                        center.y + kotlin.math.sin(angle) * innerRadius
                    )
                    val end = androidx.compose.ui.geometry.Offset(
                        center.x + kotlin.math.cos(angle) * (innerRadius + tickLength),
                        center.y + kotlin.math.sin(angle) * (innerRadius + tickLength)
                    )
                    drawLine(
                        spectrumColor(index / (tickCount - 1).toFloat()).copy(alpha = 0.32f + activity * 0.55f),
                        start = start,
                        end = end,
                        strokeWidth = 1.2.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }

                val arcRadius = size.height * 0.23f
                val arcSize = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f)
                val arcTopLeft = androidx.compose.ui.geometry.Offset(
                    center.x - arcRadius,
                    center.y - arcRadius
                )
                drawArc(
                    color = spectrumColor(0.28f).copy(alpha = if (isPlaying) 0.82f else 0.30f),
                    startAngle = -105f + phaseValue * 82f,
                    sweepAngle = 126f + kotlin.math.sin(phaseAngle) * 16f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
                drawArc(
                    color = spectrumColor(0.76f).copy(alpha = 0.38f),
                    startAngle = 72f - phaseValue * 58f,
                    sweepAngle = 68f + kotlin.math.sin(phaseAngle + 1.2f) * 12f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.3.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
                drawCircle(
                    color.copy(alpha = if (isPlaying) 0.95f else 0.45f),
                    radius = 1.7.dp.toPx(),
                    center = center
                )
            }

            // Nested, expanding contours give Pulse a clear centre-out motion
            // without using the old single rectangle that simply grew to the edge.
            "Pulse" -> {
                repeat(3) { index ->
                    val wave = if (isPlaying) {
                        (kotlin.math.sin(phaseAngle - index * 0.92f) + 1f) * 0.5f
                    } else {
                        0.18f
                    }
                    val width = size.width * (0.28f + index * 0.16f + wave * 0.07f)
                    val height = size.height * (0.24f + index * 0.22f)
                    drawRoundRect(
                        color = spectrumColor(index / 2f).copy(
                            alpha = if (isPlaying) 0.18f + wave * 0.22f else 0.10f
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(
                            center.x - width / 2f,
                            center.y - height / 2f
                        ),
                        size = androidx.compose.ui.geometry.Size(width, height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(height / 2f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = (1.8f - index * 0.25f).dp.toPx()
                        )
                    )
                }
                drawCircle(
                    spectrumColor(0.5f).copy(alpha = if (isPlaying) 0.92f else 0.38f),
                    radius = 1.8.dp.toPx(),
                    center = center
                )
            }

            // Several soft light fields drift independently instead of showing
            // one hard-edged breathing circle.
            "Glow" -> {
                drawLine(
                    color.copy(alpha = 0.10f),
                    start = androidx.compose.ui.geometry.Offset(0f, centerY),
                    end = androidx.compose.ui.geometry.Offset(size.width, centerY),
                    strokeWidth = 1.dp.toPx()
                )
                val anchors = floatArrayOf(0.22f, 0.50f, 0.78f)
                anchors.forEachIndexed { index, anchor ->
                    val drift = if (isPlaying) {
                        kotlin.math.sin(phaseAngle * 0.72f + index * 2.1f) * size.width * 0.055f
                    } else {
                        0f
                    }
                    val glowCenter = androidx.compose.ui.geometry.Offset(
                        size.width * anchor + drift,
                        centerY
                    )
                    val radius = size.width * (0.16f + if (isPlaying) {
                        0.018f * kotlin.math.sin(phaseAngle + index).let { kotlin.math.abs(it) }
                    } else {
                        0f
                    })
                    val intensity = if (isPlaying) 0.30f else 0.10f
                    val glowColor = spectrumColor(anchor)
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowColor.copy(alpha = intensity),
                                glowColor.copy(alpha = intensity * 0.38f),
                                Color.Transparent
                            ),
                            center = glowCenter,
                            radius = radius
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(
                            glowCenter.x - radius,
                            centerY - size.height * 0.52f
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            radius * 2f,
                            size.height * 1.04f
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun LandscapePlayerDetailsPane(
    modifier: Modifier = Modifier,
    syncedLyrics: List<LyricLine>?,
    plainLyrics: String?,
    isLyricsLoading: Boolean,
    lyricsError: Throwable?,
    currentSong: SongItem?,
    songList: List<SongItem>,
    currentSongIndex: Int,
    currentPositionProvider: () -> Int,
    isPlaying: Boolean,
    contentColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    selectedProvider: String?,
    providerResults: Map<String, LyricsResult>,
    onSeekTo: (Int) -> Unit,
    onFetchLyrics: () -> Unit,
    onPlayQueueItem: (Int) -> Unit
) {
    var showQueue by remember { mutableStateOf(false) }
    var lastAutoRequestedLyricsSongId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(showQueue, currentSong?.id) {
        val songId = currentSong?.id ?: return@LaunchedEffect
        if (!showQueue && lastAutoRequestedLyricsSongId != songId &&
            syncedLyrics.isNullOrEmpty() && plainLyrics.isNullOrBlank() && !isLyricsLoading
        ) {
            // Request at most once automatically per track; the visible retry button handles failures.
            lastAutoRequestedLyricsSongId = songId
            onFetchLyrics()
        }
    }
    val panelShape = RoundedCornerShape(24.dp)

    Column(
        modifier = modifier
            .background(contentColor.copy(alpha = 0.035f), panelShape)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("Lyrics", "Up next").forEachIndexed { index, label ->
                val selected = showQueue == (index == 1)
                Text(
                    text = label,
                    color = if (selected) accentColor else secondaryColor,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .background(
                            if (selected) accentColor.copy(alpha = 0.14f) else Color.Transparent,
                            RoundedCornerShape(18.dp)
                        )
                        .clickable { showQueue = index == 1 }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = if (showQueue) songList.size.toString() + " tracks" else currentSong?.artist.orEmpty(),
                color = secondaryColor,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }

        Spacer(Modifier.height(10.dp))

        if (!showQueue) {
            when {
                !syncedLyrics.isNullOrEmpty() -> SyncedLyricsCompose(
                    onSeekTo = onSeekTo,
                    lyrics = syncedLyrics,
                    currentPositionProvider = currentPositionProvider,
                    isPlaying = isPlaying,
                    contentColor = contentColor,
                    dynamicAccentColor = accentColor,
                    currentSong = currentSong,
                    selectedProvider = selectedProvider,
                    providerResults = providerResults,
                    modifier = Modifier.fillMaxSize()
                )

                !plainLyrics.isNullOrBlank() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                    ) {
                        plainLyrics.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                            Text(
                                text = line,
                                color = contentColor.copy(alpha = 0.88f),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(vertical = 5.dp)
                            )
                        }
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isLyricsLoading) "Finding lyrics…" else "Lyrics",
                            color = contentColor,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = when {
                                isLyricsLoading -> "Looking for a synchronized lyrics source."
                                lyricsError != null -> "Lyrics could not be loaded."
                                else -> "Lyrics will appear here when available."
                            },
                            color = secondaryColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!isLyricsLoading) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "Load lyrics",
                                color = accentColor,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                                    .clickable(onClick = onFetchLyrics)
                                    .padding(horizontal = 18.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        } else {
            if (songList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("The queue is empty", color = secondaryColor)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(songList) { index, song ->
                        val isCurrent = index == currentSongIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isCurrent) accentColor.copy(alpha = 0.13f) else Color.Transparent,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { onPlayQueueItem(index) }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    color = if (isCurrent) accentColor else contentColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                                Text(
                                    text = song.artist,
                                    color = secondaryColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                            if (isCurrent) {
                                Text(
                                    text = "PLAYING",
                                    color = accentColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Stateful wrapper for the FullPlayer UI component.
 * Decouples the UI component from ViewModel, Activity, and direct permission handling.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    activity: MainActivity,
    viewModel: PlayerSharedViewModel,
    uiState: PlayerUiState,
    songList: List<SongItem>,
    accentColor: Color,
    context: Context,
    onCollapse: () -> Unit,
    dragModifier: Modifier = Modifier,
    isPlayerVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val isLyricsModeEnabled by viewModel.isLyricsModeEnabled.collectAsStateWithLifecycle()
    val syncedLyrics by viewModel.syncedLyrics.collectAsStateWithLifecycle()
    val plainLyrics by viewModel.plainLyrics.collectAsStateWithLifecycle()
    val isLyricsLoading by viewModel.isLyricsLoading.collectAsStateWithLifecycle()
    val lyricsError by viewModel.lyricsError.collectAsStateWithLifecycle()
    val providerResults by viewModel.providerResults.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    // Do not collect playback position at the player root. This screen remains composed
    // while collapsed; a root subscription used to recompose the entire hidden player every tick.
    val currentPositionProvider: () -> Int = remember(viewModel) {
        { viewModel.currentPosition.value }
    }
    val customizeViewModel: SettingsViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(viewModelStoreOwner = activity)
    val playerLayout by customizeViewModel.playerLayout.collectAsStateWithLifecycle()
    val playerButtons by customizeViewModel.playerButtons.collectAsStateWithLifecycle()
    val playerTitleAlign by customizeViewModel.playerTitleAlign.collectAsStateWithLifecycle()
    val playerTitleSize by customizeViewModel.playerTitleSize.collectAsStateWithLifecycle()
    val playerTitleWeight by customizeViewModel.playerTitleWeight.collectAsStateWithLifecycle()
    val playerTitleScroll by customizeViewModel.playerTitleScroll.collectAsStateWithLifecycle()
    val playerTimes by customizeViewModel.playerTimes.collectAsStateWithLifecycle()
    val scrubberStyle by customizeViewModel.scrubberStyle.collectAsStateWithLifecycle()
    val scrubberColor by customizeViewModel.scrubberColor.collectAsStateWithLifecycle()
    val visualizerStyle by customizeViewModel.visualizerStyle.collectAsStateWithLifecycle()
    val playerBackgroundStyle by customizeViewModel.playerBackgroundStyle.collectAsStateWithLifecycle()
    val playerButtonStyle by customizeViewModel.playerButtonStyle.collectAsStateWithLifecycle()
    val scrubberThickness by customizeViewModel.scrubberThickness.collectAsStateWithLifecycle()
    val scrubberThumbStyle by customizeViewModel.scrubberThumbStyle.collectAsStateWithLifecycle()
    val visualizerColor by customizeViewModel.visualizerColor.collectAsStateWithLifecycle()

    // Handle back button when lyrics mode is enabled
    BackHandler(enabled = isLyricsModeEnabled) {
        viewModel.setLyricsModeEnabled(false)
    }

    FullPlayer(
        viewModel = viewModel,
        uiState = uiState,
        songList = songList,
        accentColor = accentColor,
        isLyricsModeEnabled = isLyricsModeEnabled,
        syncedLyrics = syncedLyrics,
        plainLyrics = plainLyrics,
        isLyricsLoading = isLyricsLoading,
        lyricsError = lyricsError,
        providerResults = providerResults,
        selectedProvider = selectedProvider,
        playerLayout = playerLayout,
        playerButtons = playerButtons,
        playerTitleAlign = playerTitleAlign,
        playerTitleSize = playerTitleSize,
        playerTitleWeight = playerTitleWeight,
        playerTitleScroll = playerTitleScroll,
        playerTimes = playerTimes,
        scrubberStyle = scrubberStyle,
        scrubberColor = scrubberColor,
        visualizerStyle = visualizerStyle,
        playerBackgroundStyle = playerBackgroundStyle,
        playerButtonStyle = playerButtonStyle,
        scrubberThickness = scrubberThickness,
        scrubberThumbStyle = scrubberThumbStyle,
        visualizerColor = visualizerColor,
        onProviderSelected = { viewModel.selectLyricsProvider(it) },
        currentPositionProvider = currentPositionProvider,
        currentPositionFlow = viewModel.currentPosition,
        isPlayerVisible = isPlayerVisible,
        onCollapse = onCollapse,
        onPlayPauseClick = {
            if (uiState.isPlaying) viewModel.pauseAudio() else viewModel.playAudio()
        },
        onPreviousClick = {
            viewModel.playPreviousSong()
        },
        onNextClick = {
            viewModel.playNextSong(force = true)
        },
        onLyricsModeChanged = { enabled ->
            viewModel.setLyricsModeEnabled(enabled)
        },
        onFetchLyrics = {
            viewModel.fetchLyricsForCurrentSong()
        },
        onRetryLyrics = {
            viewModel.retryLyrics()
        },
        onSeekTo = { position ->
            viewModel.seekTo(position)
        },
        onFavoriteClick = {
            viewModel.toggleFavorite()
        },
        onDislikeClick = {
            viewModel.toggleDislike()
        },
        onArtistClick = { artistId, artistName ->
            onCollapse()
            activity.showArtistPage(artistId, artistName)
        },
        dragModifier = dragModifier,
        modifier = modifier
    )
}

/**
 * Purely stateless UI representation of the Full Player.
 * Does not depend on ViewModels or Activities, ensuring great previewability and testability.
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun FullPlayer(
    viewModel: PlayerSharedViewModel,
    uiState: PlayerUiState,
    songList: List<SongItem>,
    accentColor: Color,
    isLyricsModeEnabled: Boolean,
    syncedLyrics: List<LyricLine>?,
    plainLyrics: String?,
    isLyricsLoading: Boolean,
    lyricsError: Throwable?,
    currentPositionProvider: () -> Int,
    currentPositionFlow: StateFlow<Int>,
    isPlayerVisible: Boolean,
    onCollapse: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onLyricsModeChanged: (Boolean) -> Unit,
    onFetchLyrics: () -> Unit,
    onRetryLyrics: () -> Unit,
    onSeekTo: (Int) -> Unit,
    onFavoriteClick: () -> Unit,
    onDislikeClick: () -> Unit,
    providerResults: Map<String, LyricsResult>,
    selectedProvider: String?,
    playerLayout: String = "Standard",
    playerButtons: List<String> = listOf("lyrics", "video", "queue", "share", "download"),
    playerTitleAlign: String = "Left",
    playerTitleSize: String = "Medium",
    playerTitleWeight: String = "Bold",
    playerTitleScroll: Boolean = true,
    playerTimes: String = "Both",
    scrubberStyle: String = "Waveform",
    scrubberColor: String = "Accent",
    visualizerStyle: String = "Off",
    playerBackgroundStyle: String = "Artwork",
    playerButtonStyle: String = "Pill",
    scrubberThickness: Float = 1f,
    scrubberThumbStyle: String = "Auto",
    visualizerColor: String = "Accent",
    onProviderSelected: (String) -> Unit,
    onArtistClick: (String?, String) -> Unit = { _, _ -> },
    dragModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val contentColor = if (isDark) Color.White else Color(0xFF1C1B1F)
    val contentSecondary = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF1C1B1F).copy(alpha = 0.6f)

    val dynamicAccentColor = remember(accentColor, isDark) {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(accentColor.toArgb(), hsl)
        if (hsl[1] < 0.08f) {
            // Monochromatic / Grayscale
            if (isDark) Color.White else Color(0xFF1C1B1F)
        } else {
            if (isDark) {
                accentColor
            } else {
                hsl[2] = hsl[2].coerceAtMost(0.45f)
                hsl[1] = hsl[1].coerceAtLeast(0.6f)
                Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
            }
        }
    }

    val themePlayerBackground = MaterialTheme.colorScheme.background
    val backgroundBrush = remember(
        uiState.playerBackgroundColor,
        isDark,
        playerBackgroundStyle,
        themePlayerBackground
    ) {
        val playerColor = Color(uiState.playerBackgroundColor)
        val finalColor = deriveArtworkSurfaceColor(
            sourceColor = playerColor,
            isDark = isDark,
            darkLightness = 0.155f,
            lightLightness = 0.835f,
            darkSaturationRange = 0.32f..0.54f,
            lightSaturationRange = 0.30f..0.48f
        )
        when (playerBackgroundStyle) {
            "Theme" -> androidx.compose.ui.graphics.SolidColor(themePlayerBackground)
            "Black" -> androidx.compose.ui.graphics.SolidColor(Color.Black)
            "Gradient" -> Brush.linearGradient(
                listOf(
                    finalColor,
                    finalColor.copy(alpha = if (isDark) 0.55f else 0.72f),
                    themePlayerBackground
                )
            )
            else -> androidx.compose.ui.graphics.SolidColor(finalColor)
        }
    }

    val hasLyrics = !syncedLyrics.isNullOrEmpty() || !plainLyrics.isNullOrBlank()
    val currentSongId = uiState.currentSong?.id
    val audioService by viewModel.audioServiceState.collectAsStateWithLifecycle()
    val isVideoMode by viewModel.isVideoMode.collectAsStateWithLifecycle()
    val videoPlaybackMetadata by viewModel.videoPlaybackMetadata.collectAsStateWithLifecycle()
    var showSongInfoDialog by remember { mutableStateOf(false) }
    var lastLyricsFetchSongId by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    
    // Sliding Queue Drawer State
    val isQueueExpanded by viewModel.isQueueExpanded.collectAsStateWithLifecycle()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val currentSongIndex by viewModel.currentSongIndex.collectAsStateWithLifecycle()

    // Unify BackHandler to collapse the sliding Queue drawer first
    BackHandler(enabled = isLyricsModeEnabled || isQueueExpanded) {
        if (isLyricsModeEnabled) {
            onLyricsModeChanged(false)
        } else if (isQueueExpanded) {
            viewModel.setQueueExpanded(false)
        }
    }

    // Sleep Timer State & Controller Dialog state
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    val sleepTimerMode by viewModel.sleepTimerMode.collectAsStateWithLifecycle()
    val sleepTimerEndTime by viewModel.sleepTimerEndTime.collectAsStateWithLifecycle()
    
    val lyricsBackgroundBrush = remember(uiState.playerBackgroundColor, isDark) {
        val playerColor = Color(uiState.playerBackgroundColor)
        val finalColor = deriveArtworkSurfaceColor(
            sourceColor = playerColor,
            isDark = isDark,
            darkLightness = 0.145f,
            lightLightness = 0.825f,
            darkSaturationRange = 0.32f..0.54f,
            lightSaturationRange = 0.30f..0.48f
        )
        androidx.compose.ui.graphics.SolidColor(finalColor)
    }

    // Trigger lyrics load when enabled
    LaunchedEffect(isLyricsModeEnabled, currentSongId) {
        if (!isLyricsModeEnabled) return@LaunchedEffect
        if (currentSongId == null) {
            lastLyricsFetchSongId = null
            return@LaunchedEffect
        }
        if (lastLyricsFetchSongId != currentSongId) {
            lastLyricsFetchSongId = currentSongId
            onFetchLyrics()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .then(if (isQueueExpanded || isLyricsModeEnabled) Modifier else dragModifier) // Drag down anywhere to collapse
    ) {
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp
        val screenHeight = configuration.screenHeightDp.dp
        val artScale = when (playerLayout) {
            "Cover" -> 0.98f
            "Minimal" -> 0.68f
            "Vinyl", "Cassette" -> 0.82f
            else -> 0.9f
        }
        val useWidePlayer = configuration.screenWidthDp >= 840 ||
            (configuration.screenWidthDp >= 600 && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        val compactLandscape =
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val albumArtSize = if (compactLandscape) {
            minOf(screenHeight * 0.28f, screenWidth * 0.30f) * (artScale / 0.9f)
        } else if (useWidePlayer) {
            minOf(screenHeight * 0.50f, screenWidth * 0.42f) * (artScale / 0.9f)
        } else {
            screenWidth * artScale
        }

        val density = androidx.compose.ui.platform.LocalDensity.current
        val statusBarTopDp = with(density) { androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(this).toDp() }
        val minTopOffset = statusBarTopDp + 68.dp
        val headerHeight = if (compactLandscape) 40.dp else 56.dp

        // Calculate top offset to perfectly match yEndPx in PlayerBottomSheetCompose
        val topOffset = if (useWidePlayer) minTopOffset
            else ((screenHeight - albumArtSize) / 2f - 220.dp).coerceAtLeast(minTopOffset)

        var lyricsButtonCenterInRoot by remember { mutableStateOf<Offset?>(null) }
        val lyricsRevealProgress by animateFloatAsState(
            targetValue = if (isLyricsModeEnabled) 1f else 0f,
            animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
            label = "LyricsCircularReveal"
        )

        // Tie the visibility/readiness directly to the lyricsRevealProgress animation state
        val lyricsContentReady = lyricsRevealProgress > 0.8f

        val playerControlsContent: @Composable ColumnScope.() -> Unit = {
            // Header Row (Nav controls + collapse) - Symmetric centering
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                        contentDescription = "Collapse Player",
                        tint = contentColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "NOW PLAYING",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentSecondary
                )

                Spacer(modifier = Modifier.size(48.dp))
            }

            // Parent adds status-bar inset and 12dp vertical padding before this column.
            // Align the placeholder with the shared artwork layer's root-space Y position.
            Spacer(
                modifier = Modifier.height(
                    (topOffset - (statusBarTopDp + 12.dp + headerHeight)).coerceAtLeast(0.dp)
                )
            )

            // Album Art Container Placeholder (ArtworkPager is rendered at this absolute position)
            Box(
                modifier = Modifier
                    .size(albumArtSize)
            )

            Spacer(modifier = Modifier.height(if (compactLandscape) 6.dp else 12.dp))

            // Metadata row: title/artist
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = if (playerTitleAlign == "Centered") Alignment.CenterHorizontally else Alignment.Start
                ) {
                    val titleSize = when (playerTitleSize) {
                        "Small" -> 23.sp
                        "Large" -> 34.sp
                        else -> 29.sp
                    }
                    val titleWeight = when (playerTitleWeight) {
                        "Light" -> FontWeight.Light
                        "Regular" -> FontWeight.Normal
                        else -> FontWeight.Bold
                    }
                    Text(
                        text = if (isVideoMode) {
                            videoPlaybackMetadata?.title?.takeIf { it.isNotBlank() }
                                ?: uiState.currentSong?.title ?: "Unknown Title"
                        } else {
                            uiState.currentSong?.title ?: "Unknown Title"
                        },
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                        fontSize = titleSize,
                        fontWeight = titleWeight,
                        color = contentColor,
                        maxLines = 1,
                        textAlign = if (playerTitleAlign == "Centered") androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
                        modifier = if (playerTitleScroll && isPlayerVisible) {
                            Modifier.basicMarqueeWithFadedEdges()
                        } else Modifier
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isVideoMode) {
                            videoPlaybackMetadata?.uploader?.takeIf { it.isNotBlank() }
                                ?: uiState.currentSong?.artist ?: "Unknown Artist"
                        } else {
                            uiState.currentSong?.artist ?: "Unknown Artist"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentSecondary,
                        maxLines = 1,
                        textAlign = if (playerTitleAlign == "Centered") androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
                        modifier = (if (playerTitleScroll && isPlayerVisible) {
                            Modifier.basicMarqueeWithFadedEdges()
                        } else Modifier).clickable {
                                val song = uiState.currentSong
                                if (song != null) {
                                    onArtistClick(song.artistId, song.artist)
                                }
                            }
                    )
                }
                IconButton(
                    onClick = { showSongInfoDialog = true },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Song information",
                        tint = contentSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (compactLandscape) 8.dp else 16.dp))

            if (visualizerStyle != "Off") {
                PlayerVisualizer(
                    style = visualizerStyle,
                    isPlaying = uiState.isPlaying,
                    color = when (visualizerColor) {
                        "White" -> Color.White
                        "Cover" -> Color(uiState.playerBackgroundColor)
                        else -> dynamicAccentColor
                    },
                    colorMode = visualizerColor,
                    isVisible = isPlayerVisible
                )
                Spacer(modifier = Modifier.height(if (compactLandscape) 4.dp else 10.dp))
            }

            // Reorderable action chips. The selected order is saved by Customize.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val pad = 20.dp.roundToPx()
                        val placeable = measurable.measure(
                            constraints.copy(maxWidth = constraints.maxWidth + 2 * pad)
                        )
                        layout(placeable.width - 2 * pad, placeable.height) {
                            placeable.place(-pad, 0)
                        }
                    }
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(12.dp))

                val songForActions = uiState.currentSong
                val realDownloaded = uiState.isCurrentSongDownloaded
                val realDownloadProgress = uiState.currentSongDownloadProgress
                val isDownloading = realDownloadProgress != null ||
                    com.dsann.music.util.SongDownloader.isDownloading(songForActions?.videoId)

                val orderedButtons = if (playerLayout == "Minimal") playerButtons.take(3) else playerButtons
                // Video is a playback-mode control. Keep it visible while video is active,
                // and keep it available in Minimal mode even when it is outside the first three
                // user-selected actions.
                val showVideoSwitch = isVideoMode || !songForActions?.videoId.isNullOrBlank()
                val playerButtonsWithVideo = if (
                    showVideoSwitch && "video" !in orderedButtons
                ) orderedButtons + "video" else orderedButtons
                playerButtonsWithVideo.forEach { action ->
                    when (action) {
                        "lyrics" -> PillChip(
                            style = playerButtonStyle,
                            icon = painterResource(id = R.drawable.ic_lyrics),
                            label = "Lyrics",
                            isSelected = isLyricsModeEnabled,
                            onClick = {
                                onLyricsModeChanged(true)
                                if (currentSongId != null && lastLyricsFetchSongId != currentSongId && !hasLyrics && !isLyricsLoading) {
                                    lastLyricsFetchSongId = currentSongId
                                    onFetchLyrics()
                                }
                            },
                            contentColor = contentColor,
                            accentColor = dynamicAccentColor,
                            isDark = isDark,
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                val position = coordinates.positionInRoot()
                                lyricsButtonCenterInRoot = Offset(
                                    x = position.x + coordinates.size.width / 2f,
                                    y = position.y + coordinates.size.height / 2f
                                )
                            }
                        )
                        "video" -> if (showVideoSwitch) {
                            PillChip(
                            style = playerButtonStyle,
                                icon = painterResource(id = R.drawable.ic_youtube),
                                label = if (isVideoMode) "Audio" else "Video",
                                isSelected = isVideoMode,
                                onClick = { audioService?.setVideoMode(!isVideoMode) },
                                contentColor = contentColor,
                                accentColor = dynamicAccentColor,
                                isDark = isDark
                            )
                        }
                        "queue" -> PillChip(
                            style = playerButtonStyle,
                            icon = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                            label = "Up next",
                            isSelected = isQueueExpanded,
                            onClick = { viewModel.setQueueExpanded(true) },
                            contentColor = contentColor,
                            accentColor = dynamicAccentColor,
                            isDark = isDark
                        )
                        "share" -> PillChip(
                            style = playerButtonStyle,
                            icon = painterResource(id = R.drawable.ic_share),
                            label = "Share",
                            onClick = {
                                val shareIntent = Intent().apply {
                                    this.action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Listening on D.S Music. Check out: https://music.youtube.com/watch?v=${songForActions?.videoId.orEmpty()}")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Track"))
                            },
                            contentColor = contentColor,
                            accentColor = dynamicAccentColor,
                            isDark = isDark
                        )
                        "download" -> {
                            val downloadLabel = when {
                                isDownloading -> "Cancel download"
                                realDownloaded -> "Downloaded"
                                else -> "Download"
                            }
                            val downloadIcon: Any = when {
                                isDownloading -> painterResource(id = R.drawable.ic_close)
                                realDownloaded -> painterResource(id = R.drawable.ic_downloaded)
                                else -> painterResource(id = R.drawable.ic_download)
                            }
                            PillChip(
                            style = playerButtonStyle,
                                icon = downloadIcon,
                                label = downloadLabel,
                                isSelected = realDownloaded || isDownloading,
                                progress = if (isDownloading) (realDownloadProgress ?: 0) / 100f else null,
                                onClick = {
                                    val currentSong = songForActions
                                    if (currentSong != null) {
                                        if (com.dsann.music.util.SongDownloader.isDownloading(currentSong.videoId)) {
                                            com.dsann.music.util.SongDownloader.cancelDownload(context, currentSong)
                                        } else {
                                            com.dsann.music.util.SongDownloader.downloadSong(context, currentSong)
                                        }
                                    }
                                },
                                contentColor = contentColor,
                                accentColor = dynamicAccentColor,
                                isDark = isDark
                            )
                        }
                        "like" -> PillChip(
                            style = playerButtonStyle,
                            icon = if (uiState.isCurrentSongFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            label = "Like",
                            isSelected = uiState.isCurrentSongFavorite,
                            onClick = onFavoriteClick,
                            contentColor = contentColor,
                            accentColor = dynamicAccentColor,
                            isDark = isDark
                        )
                        "dislike" -> PillChip(
                            style = playerButtonStyle,
                            icon = Icons.Default.ThumbDown,
                            label = "Not for me",
                            isSelected = uiState.isCurrentSongDisliked,
                            onClick = onDislikeClick,
                            contentColor = contentColor,
                            accentColor = dynamicAccentColor,
                            isDark = isDark
                        )
                        "sleep" -> PillChip(
                            style = playerButtonStyle,
                            icon = Icons.Default.NightsStay,
                            label = "Sleep timer",
                            isSelected = sleepTimerEndTime != null,
                            onClick = { showSleepTimerDialog = true },
                            contentColor = contentColor,
                            accentColor = dynamicAccentColor,
                            isDark = isDark
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))
            }

            Spacer(modifier = Modifier.height(if (compactLandscape) 8.dp else 24.dp))

            // Premium Wavy Seek Bar (Isolated)
            WavySliderWithLabels(
                currentPositionProvider = currentPositionProvider,
                currentPositionFlow = currentPositionFlow,
                isPlayerVisible = isPlayerVisible,
                duration = uiState.duration,
                isPlaying = uiState.isPlaying,
                onSeekTo = onSeekTo,
                dynamicAccentColor = dynamicAccentColor,
                contentColor = contentColor,
                contentSecondary = contentSecondary,
                isDark = isDark,
                scrubberStyle = scrubberStyle,
                scrubberColor = scrubberColor,
                playerTimes = playerTimes,
                scrubberThickness = scrubberThickness,
                scrubberThumbStyle = scrubberThumbStyle,
                coverDerivedColor = Color(uiState.playerBackgroundColor)
            )

            Spacer(modifier = Modifier.height(if (compactLandscape) 6.dp else 16.dp))

            val minimalTransport = playerButtonStyle == "Minimal"
            val transportShape = when (playerButtonStyle) {
                "Pill" -> CircleShape
                "Rounded" -> RoundedCornerShape(18.dp)
                else -> CircleShape
            }
            val sideControlColor = if (minimalTransport) Color.Transparent
                else contentColor.copy(alpha = if (isDark) 0.08f else 0.06f)
            val sideIconColor = if (minimalTransport) dynamicAccentColor else contentColor
            val centerControlColor = if (minimalTransport) Color.Transparent else dynamicAccentColor
            val centerIconColor = if (minimalTransport) dynamicAccentColor
                else if (isDark) Color(0xFF1C1B1F) else Color.White

            // D.S Launcher-style transport controls: flat rounded touch targets with
            // independent sizing and no Material ButtonGroup indicator/elevation layers.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (compactLandscape) 52.dp else 76.dp)
                        .clip(transportShape)
                        .background(sideControlColor)
                        .clickable(onClick = onPreviousClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_skip_previous),
                        contentDescription = "Previous Song",
                        modifier = Modifier.size(34.dp),
                        tint = sideIconColor
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .height(if (compactLandscape) 52.dp else 76.dp)
                        .clip(transportShape)
                        .background(centerControlColor)
                        .clickable(enabled = !uiState.isLoading) { onPlayPauseClick() },
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isLoading) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(30.dp),
                            color = centerIconColor,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            painter = painterResource(
                                id = if (uiState.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                            ),
                            contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(40.dp),
                            tint = centerIconColor
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (compactLandscape) 52.dp else 76.dp)
                        .clip(transportShape)
                        .background(sideControlColor)
                        .clickable(onClick = onNextClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_skip_next),
                        contentDescription = "Next Song",
                        modifier = Modifier.size(34.dp),
                        tint = sideIconColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (compactLandscape) 4.dp else 16.dp))

            // Swipe Up / Click Chevron Up Indicator to expand Queue
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compactLandscape) 24.dp else 48.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -10f && !isQueueExpanded && !isLyricsModeEnabled) {
                                viewModel.setQueueExpanded(true)
                            }
                        }
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        viewModel.setQueueExpanded(true)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                    contentDescription = "Open Queue",
                    tint = contentColor.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(if (compactLandscape) 24.dp else 32.dp)
                        .graphicsLayer { rotationZ = 180f }
                )
            }
        }

        if (useWidePlayer) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.54f)
                        .fillMaxHeight()
                        .clipToBounds(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = playerControlsContent
                )
                if (isPlayerVisible) {
                    LandscapePlayerDetailsPane(
                        modifier = Modifier.weight(0.46f).fillMaxHeight(),
                        syncedLyrics = syncedLyrics,
                        plainLyrics = plainLyrics,
                        isLyricsLoading = isLyricsLoading,
                        lyricsError = lyricsError,
                        currentSong = uiState.currentSong,
                        songList = songList,
                        currentSongIndex = currentSongIndex,
                        currentPositionProvider = currentPositionProvider,
                        isPlaying = uiState.isPlaying,
                        contentColor = contentColor,
                        secondaryColor = contentSecondary,
                        accentColor = dynamicAccentColor,
                        selectedProvider = selectedProvider,
                        providerResults = providerResults,
                        onSeekTo = onSeekTo,
                        onFetchLyrics = onFetchLyrics,
                        onPlayQueueItem = { index -> viewModel.playSongAtIndex(index) }
                    )
                } else {
                    Spacer(Modifier.weight(0.46f).fillMaxHeight())
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = playerControlsContent
            )
        }
        if (lyricsRevealProgress > 0f) {
            FullScreenLyricsOverlay(
                currentSong = uiState.currentSong,
                syncedLyrics = syncedLyrics,
                plainLyrics = plainLyrics,
                isLoading = isLyricsLoading,
                isPlaying = uiState.isPlaying,
                lyricsError = lyricsError,
                currentPositionProvider = currentPositionProvider,
                contentReady = lyricsContentReady,
                backgroundBrush = lyricsBackgroundBrush,
                revealProgressProvider = { lyricsRevealProgress },
                revealCenterProvider = { lyricsButtonCenterInRoot },
                contentColor = contentColor,
                contentSecondary = contentSecondary,
                dynamicAccentColor = dynamicAccentColor,
                onRetryLyrics = onRetryLyrics,
                onFetchLyrics = onFetchLyrics,
                onSeekTo = onSeekTo,
                providerResults = providerResults,
                selectedProvider = selectedProvider,
                onProviderSelected = onProviderSelected,
                onDismissLyrics = { onLyricsModeChanged(false) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // --- CUSTOM EMBEDDED SLIDING PLAY QUEUE ---
        SlidingQueueDrawer(
            isQueueExpanded = isQueueExpanded,
            onQueueExpandedChange = { viewModel.setQueueExpanded(it) },
            songList = songList,
            currentSongIndex = currentSongIndex,
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            sleepTimerMode = sleepTimerMode,
            onReorderQueue = { from, to -> viewModel.reorderQueue(from, to) },
            onPlaySongAtIndex = { index -> viewModel.playSongAtIndex(index) },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onToggleLoopMode = { viewModel.toggleLoopMode() },
            onShowSleepTimerDialog = { showSleepTimerDialog = true },
            playerBackgroundColor = uiState.playerBackgroundColor,
            dynamicAccentColor = dynamicAccentColor,
            isDark = isDark
        )

        // --- Standalone Sleep Timer Bottom Sheet ---
        if (showSleepTimerDialog) {
            SleepTimerBottomSheet(
                onDismissRequest = { showSleepTimerDialog = false },
                sleepTimerEndTime = sleepTimerEndTime,
                sleepTimerMode = sleepTimerMode,
                onStartTimer = { mins ->
                    viewModel.startCustomSleepTimer(mins)
                },
                onCancelTimer = {
                    viewModel.cancelSleepTimer()
                },
                onSetEndOfSong = { enable ->
                    if (enable) viewModel.setSleepTimerMode(PlayerSharedViewModel.SleepTimerMode.END_OF_SONG)
                    else viewModel.cancelSleepTimer()
                }
            )
        }

        if (showSongInfoDialog) {
            val song = uiState.currentSong
            if (song != null) {
                val songFileMetadata by produceState(
                    initialValue = SongFileMetadata(isRemote = SongFileMetadataReader.isRemoteSong(song)),
                    key1 = song.id,
                    key2 = song.path,
                    key3 = song.data
                ) {
                    value = withContext(Dispatchers.IO) {
                        SongFileMetadataReader.read(context.applicationContext, song)
                    }
                }
                val streamFormat by produceState(
                    initialValue = audioService?.getCurrentAudioFormatDetails(song.id.toString()),
                    key1 = audioService,
                    key2 = song.id
                ) {
                    // Track formats and media duration may arrive shortly after opening the dialog.
                    repeat(20) {
                        val latest = audioService?.getCurrentAudioFormatDetails(song.id.toString())
                        if (latest != null) value = latest
                        if (latest?.bitrateBps != null && latest.durationMs != null) return@produceState
                        delay(250L)
                    }
                }
                val onlineStreamMetadata by produceState<OnlineStreamMetadata?>(
                    initialValue = null,
                    key1 = song.videoId,
                    key2 = isVideoMode
                ) {
                    value = if (
                        !isVideoMode &&
                        !song.videoId.isNullOrBlank() &&
                        !song.isCachedTrack &&
                        !song.isDownloadedOffline
                    ) {
                        withContext(Dispatchers.IO) {
                            OnlineStreamMetadataReader.read(song.videoId!!)
                        }
                    } else null
                }
                val streamBitrateBps = if (isVideoMode) null else {
                    streamFormat?.bitrateBps?.takeIf { it > 0 } ?: onlineStreamMetadata?.bitrateBps
                }
                val durationMs = when {
                    isVideoMode && (videoPlaybackMetadata?.durationMs ?: 0L) > 0L ->
                        videoPlaybackMetadata?.durationMs ?: 0L
                    song.duration > 0L -> song.duration
                    (streamFormat?.durationMs ?: 0L) > 0L -> streamFormat?.durationMs ?: 0L
                    (onlineStreamMetadata?.durationMs ?: 0L) > 0L -> onlineStreamMetadata?.durationMs ?: 0L
                    (songFileMetadata.durationMs ?: 0L) > 0L -> songFileMetadata.durationMs ?: 0L
                    else -> 0L
                }
                val estimatedStreamSizeBytes = if (
                    songFileMetadata.isRemote && streamBitrateBps != null && durationMs > 0L
                ) {
                    (streamBitrateBps.toDouble() * durationMs.toDouble() / 8000.0)
                        .takeIf { it > 0.0 && it < Long.MAX_VALUE.toDouble() }
                        ?.toLong()
                } else null
                val reportedOnlineStreamBytes = onlineStreamMetadata?.contentLengthBytes
                val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
                val durationText = if (durationMs > 0L) {
                    if (totalSeconds >= 3600L) {
                        java.lang.String.format(java.util.Locale.getDefault(), "%d:%02d:%02d",
                            totalSeconds / 3600L, (totalSeconds % 3600L) / 60L, totalSeconds % 60L)
                    } else {
                        java.lang.String.format(java.util.Locale.getDefault(), "%d:%02d",
                            totalSeconds / 60L, totalSeconds % 60L)
                    }
                } else "Duration unavailable until the stream finishes preparing"
                val fileSizeBytes = songFileMetadata.sizeBytes
                val fileBitrateBps = songFileMetadata.bitrateBps
                val estimatedFileBitrateBps = songFileMetadata.estimatedBitrateBps
                val sizeText = if (isVideoMode) {
                    "Video stream size is not exposed by the selected source"
                } else when {
                    fileSizeBytes != null ->
                        android.text.format.Formatter.formatFileSize(context, fileSizeBytes)
                    reportedOnlineStreamBytes != null ->
                        android.text.format.Formatter.formatFileSize(context, reportedOnlineStreamBytes) + " (reported audio stream length)"
                    estimatedStreamSizeBytes != null ->
                        "About " + android.text.format.Formatter.formatFileSize(context, estimatedStreamSizeBytes) +
                            " (estimated from stream bitrate and duration)"
                    songFileMetadata.isRemote ->
                        "Streaming source; full-file size varies with the selected audio stream"
                    else -> "File provider does not expose the byte length"
                }
                val bitrateText = if (isVideoMode) {
                    "Video bitrate is not exposed by the selected stream metadata"
                } else when {
                    fileBitrateBps != null ->
                        "${(fileBitrateBps + 500L) / 1000L} kb/s (file metadata)"
                    streamBitrateBps != null ->
                        "${(streamBitrateBps + 500) / 1000} kb/s (active stream format)"
                    estimatedFileBitrateBps != null ->
                        "${(estimatedFileBitrateBps + 500L) / 1000L} kb/s (estimated from file size and duration)"
                    songFileMetadata.isRemote -> "Stream bitrate is not exposed by the current audio format"
                    else -> "Bitrate metadata is absent; no estimate can be calculated"
                }
                val formatText = if (isVideoMode) {
                    videoPlaybackMetadata?.mimeType?.takeIf { it.isNotBlank() }
                        ?: "Video format not exposed by selected stream"
                } else {
                    songFileMetadata.mimeType
                        ?: streamFormat?.mimeType
                        ?: onlineStreamMetadata?.mimeType
                        ?: songFileMetadata.fileName?.substringAfterLast('.', "")
                            ?.takeIf { it.isNotBlank() }
                            ?.uppercase(java.util.Locale.ROOT)
                            ?.plus(" (file extension)")
                        ?: if (songFileMetadata.isRemote) "Streaming audio (codec not identified yet)"
                           else "Audio format not exposed by file provider"
                }
                val sourceText = when {
                    song.isCachedTrack -> "D.S Music cache (cached audio stream)"
                    song.isDownloadedOffline -> "Downloaded audio file (offline)"
                    !song.videoId.isNullOrBlank() && songFileMetadata.isRemote -> "YouTube Music stream"
                    songFileMetadata.isRemote -> "Remote audio stream"
                    else -> "Device audio file"
                }
                val displayTitle = (
                    if (isVideoMode) videoPlaybackMetadata?.title?.takeIf { it.isNotBlank() }
                    else null
                ) ?: song.title.takeIf { it.isNotBlank() && !it.equals("Unknown Title", true) }
                    ?: onlineStreamMetadata?.title
                    ?: "Title not supplied by source"
                val displayArtist = (
                    if (isVideoMode) videoPlaybackMetadata?.uploader?.takeIf { it.isNotBlank() }
                    else null
                ) ?: song.artist.takeIf { it.isNotBlank() && !it.equals("Unknown Artist", true) }
                    ?: onlineStreamMetadata?.artist
                    ?: "Artist not supplied by source"
                val details = mutableListOf(
                    "Title" to displayTitle,
                    (if (isVideoMode) "Uploader" else "Artist") to displayArtist,
                    "Album" to if (isVideoMode) {
                        "Not supplied by the video source"
                    } else {
                        song.albumName?.takeIf { it.isNotBlank() && !it.equals("<unknown>", true) }
                            ?: streamFormat?.albumTitle
                            ?: songFileMetadata.album
                            ?: if (song.albumId > 0L) "Album ID ${song.albumId} (title unavailable)" else "Album title unavailable in source metadata"
                    },
                    "Duration" to durationText,
                    "Source" to if (isVideoMode) "YouTube video stream" else sourceText,
                    (if (isVideoMode) "Video size" else "Song size") to sizeText,
                    (if (isVideoMode) "Video bitrate" else "Bitrate") to bitrateText,
                    (if (isVideoMode) "Video format" else "Format") to formatText
                )
                if (isVideoMode) {
                    videoPlaybackMetadata?.resolution?.takeIf { it.isNotBlank() }?.let {
                        details.add("Resolution" to it)
                    }
                    videoPlaybackMetadata?.viewCount?.takeIf { it > 0L }?.let {
                        details.add("Views" to java.text.NumberFormat.getIntegerInstance()
                            .format(it) + " views")
                    }
                    videoPlaybackMetadata?.likeCount?.takeIf { it > 0L }?.let {
                        details.add("Likes" to java.text.NumberFormat.getIntegerInstance().format(it))
                    }
                    videoPlaybackMetadata?.uploadDate?.takeIf { it.isNotBlank() }?.let {
                        details.add("Uploaded" to it)
                    }
                    videoPlaybackMetadata?.description?.takeIf { it.isNotBlank() }?.let {
                        details.add("Description" to it)
                    }
                    videoPlaybackMetadata?.uploaderUrl?.takeIf { it.isNotBlank() }?.let {
                        details.add("Uploader URL" to it)
                    }
                    song.videoId?.takeIf { it.isNotBlank() }?.let {
                        details.add("Video URL" to "https://www.youtube.com/watch?v=$it")
                    }
                } else {
                    onlineStreamMetadata?.audioQuality?.let { details.add("Source audio quality" to it) }
                    streamFormat?.sampleRateHz?.takeIf { it > 0 }?.let {
                        details.add("Sample rate" to "${it} Hz")
                    }
                }
                details.add((if (isVideoMode) "Queue track ID" else "Song ID") to song.id.toString())
                if (!isVideoMode) {
                    if (song.albumId > 0L) details.add("Album ID" to song.albumId.toString())
                    songFileMetadata.fileName?.let { details.add("File name" to it) }
                    songFileMetadata.filePath?.let { details.add("File path" to it) }
                    if (!songFileMetadata.isRemote && song.dateAdded > 0L) {
                        details.add("Added to device library" to java.text.DateFormat.getDateTimeInstance()
                            .format(java.util.Date(song.dateAdded * 1000L)))
                    }
                }
                if (!song.videoId.isNullOrBlank()) details.add("Video ID" to song.videoId!!)
                if (!isVideoMode) {
                    if (!song.artistId.isNullOrBlank()) details.add("Artist ID" to song.artistId!!)
                    song.lufs?.let { details.add("LUFS" to it.toString()) }
                        ?: details.add("Loudness" to "Not analyzed")
                }
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showSongInfoDialog = false },
                    title = { Text(if (isVideoMode) "Video information" else "Song information", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                        ) {
                            details.forEach { (label, value) ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                    Text(label, modifier = Modifier.width(100.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(value, modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { showSongInfoDialog = false }) { Text("Close") }
                    }
                )
            }
        }
    }
}
