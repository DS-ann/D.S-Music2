package com.codetrio.spatialflow.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.lyrics.LyricLine
import com.codetrio.spatialflow.data.lyrics.LyricsResult
import com.codetrio.spatialflow.model.SongItem
import kotlin.math.hypot
import kotlin.math.max
import kotlinx.coroutines.delay

/**
 * Optimized circular reveal modifier utilizing a remembered path and in-place reset/rebuild.
 * Ensures zero Path or Rect allocations on every single animation drawing frame!
 */
private fun Modifier.circularRevealFrom(
    progressProvider: () -> Float,
    centerProvider: () -> Offset?
): Modifier = this.drawWithCache {
    val path = Path()
    onDrawWithContent {
        val progress = progressProvider()
        val normalizedProgress = progress.coerceIn(0f, 1f)
        if (normalizedProgress <= 0f) return@onDrawWithContent
        if (normalizedProgress >= 0.999f) {
            drawContent()
            return@onDrawWithContent
        }

        val fallbackCenter = Offset(size.width * 0.9f, size.height * 0.72f)
        val revealCenter = centerProvider() ?: fallbackCenter

        val maxRadius = max(
            max(
                hypot(revealCenter.x.toDouble(), revealCenter.y.toDouble()),
                hypot((size.width - revealCenter.x).toDouble(), revealCenter.y.toDouble())
            ),
            max(
                hypot(revealCenter.x.toDouble(), (size.height - revealCenter.y).toDouble()),
                hypot((size.width - revealCenter.x).toDouble(), (size.height - revealCenter.y).toDouble())
            )
        ).toFloat()

        val radius = maxRadius * normalizedProgress
        
        path.reset() // Reuse existing Path object
        path.addOval(
            Rect(
                left = revealCenter.x - radius,
                top = revealCenter.y - radius,
                right = revealCenter.x + radius,
                bottom = revealCenter.y + radius
            )
        )

        clipPath(path) {
            this@onDrawWithContent.drawContent()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FullScreenLyricsOverlay(
    currentSong: SongItem?,
    syncedLyrics: List<LyricLine>?,
    plainLyrics: String?,
    isLoading: Boolean,
    isPlaying: Boolean,
    lyricsError: Throwable?,
    currentPositionProvider: () -> Int,
    contentReady: Boolean,
    backgroundBrush: Brush,
    revealProgressProvider: () -> Float,
    revealCenterProvider: () -> Offset?,
    contentColor: Color,
    contentSecondary: Color,
    dynamicAccentColor: Color,
    onRetryLyrics: () -> Unit,
    onFetchLyrics: () -> Unit,
    onSeekTo: (Int) -> Unit,
    providerResults: Map<String, LyricsResult>,
    selectedProvider: String?,
    onProviderSelected: (String) -> Unit,
    onDismissLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val consumeClicks = remember { MutableInteractionSource() }
    var showProvidersSheet by remember { mutableStateOf(false) }
    var syncOffsetMs by remember(currentSong?.id) { mutableStateOf(0L) }
    var showSyncControls by remember { mutableStateOf(false) }
    var syncControlActivity by remember { mutableStateOf(0) }

    // Hide the adjustment strip after three seconds without a sync-control action.
    LaunchedEffect(showSyncControls, syncControlActivity, currentSong?.id) {
        if (showSyncControls) {
            delay(3000L)
            showSyncControls = false
        }
    }

    val effectivePositionProvider = remember(currentPositionProvider, syncOffsetMs) {
        {
            (currentPositionProvider().toLong() + syncOffsetMs)
                .coerceIn(0L, Int.MAX_VALUE.toLong())
                .toInt()
        }
    }

    fun adjustSyncOffset(deltaMs: Long) {
        syncOffsetMs = (syncOffsetMs + deltaMs).coerceIn(-10_000L, 10_000L)
        showSyncControls = true
        syncControlActivity += 1
    }

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
        }
    }

    Box(
        modifier = modifier
            .circularRevealFrom(
                progressProvider = revealProgressProvider,
                centerProvider = revealCenterProvider
            )
            .background(backgroundBrush)
            .clickable(
                interactionSource = consumeClicks,
                indication = null,
                onClick = {}
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Centered Title Header Layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismissLyrics
                        )
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismissLyrics
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    ) {
                        Text(
                            text = "LYRICS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor.copy(alpha = 0.5f),
                            letterSpacing = 1.sp
                        )

                        val activeResult = providerResults[selectedProvider] ?: bestLyricsProviderResult(providerResults.values)

                        val lyricTypeBadge = when {
                            activeResult == null -> null
                            !activeResult.hasLyrics() -> null
                            activeResult.isWordByWord -> "Karaoke Lyrics"
                            activeResult.isSynced -> "Synced Lyrics"
                            else -> "Plain Lyrics"
                        }

                        AnimatedVisibility(
                            visible = lyricTypeBadge != null,
                            enter = fadeIn(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ) + slideInHorizontally(
                                initialOffsetX = { it / 2 },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ),
                            exit = fadeOut() + slideOutHorizontally()
                        ) {
                            if (lyricTypeBadge != null) {
                                Text(
                                    text = " • $lyricTypeBadge",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor.copy(alpha = 0.4f),
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                    Text(
                        text = currentSong?.title ?: "Unknown Title",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1,
                        modifier = Modifier.basicMarqueeWithFadedEdges(edgeWidth = 8.dp)
                    )
                }

                IconButton(onClick = { showProvidersSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Change Lyrics Provider",
                        tint = contentColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when {
                    // Keep the loading state visible while an explicitly selected provider fetches.
                    // The user can still reopen/switch sources without getting a blank lyrics panel.
                    isLoading -> {
                        CircularProgressIndicator(color = dynamicAccentColor)
                    }

                    !contentReady -> Unit

                    !syncedLyrics.isNullOrEmpty() -> {
                        SyncedLyricsCompose(
                            onSeekTo = onSeekTo,
                            lyrics = syncedLyrics,
                            currentPositionProvider = effectivePositionProvider,
                            isPlaying = isPlaying,
                            contentColor = contentColor,
                            dynamicAccentColor = dynamicAccentColor,
                            currentSong = currentSong,
                            selectedProvider = selectedProvider,
                            providerResults = providerResults,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    !plainLyrics.isNullOrBlank() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 28.dp)
                        ) {
                            Text(
                                text = plainLyrics,
                                style = MaterialTheme.typography.titleLarge,
                                color = contentColor.copy(alpha = 0.9f)
                            )
                            LyricsMetadataFooter(
                                currentSong = currentSong,
                                selectedProvider = selectedProvider,
                                providerResults = providerResults,
                                contentColor = contentColor
                            )
                        }
                    }

                    lyricsError != null -> {
                        LyricsErrorState(
                            message = lyricsError.message ?: "Lyrics not found",
                            onRetry = onRetryLyrics
                        )
                    }

                    else -> {
                        LyricsErrorState(
                            message = "Lyrics are not loaded yet",
                            onRetry = onFetchLyrics
                        )
                    }
                }
            }

            // Do not tie the source selector to contentReady: provider switching temporarily
            // clears the visible lyrics while a new source is loading.
            if (showProvidersSheet) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { showProvidersSheet = false },
                    sheetState = sheetState,
                    containerColor = Color.Transparent,
                    dragHandle = null
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .background(backgroundBrush)
                            .navigationBarsPadding()
                            .padding(top = 16.dp)
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .width(32.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(contentColor.copy(alpha = 0.4f))
                                    .align(Alignment.CenterHorizontally)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            ProvidersSelector(
                                providerResults = providerResults,
                                selectedProvider = selectedProvider,
                                onProviderSelected = onProviderSelected,
                                onRefindClick = onRetryLyrics,
                                accentColor = dynamicAccentColor,
                                contentColor = contentColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        if (contentReady && !syncedLyrics.isNullOrEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AnimatedVisibility(visible = showSyncControls) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val adjustments = listOf(
                            -500L to "−0.5s",
                            -50L to "−50ms",
                            0L to "Reset",
                            50L to "+50ms",
                            500L to "+0.5s"
                        )
                        adjustments.forEach { (deltaMs, label) ->
                            TextButton(
                                onClick = {
                                    if (deltaMs == 0L) {
                                        syncOffsetMs = 0L
                                        showSyncControls = true
                                        syncControlActivity += 1
                                    } else {
                                        adjustSyncOffset(deltaMs)
                                    }
                                }
                            ) {
                                Text(
                                    text = label,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                TextButton(
                    onClick = {
                        showSyncControls = !showSyncControls
                        syncControlActivity += 1
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                ) {
                    val offsetLabel = String.format(java.util.Locale.US, "%+.1fs", syncOffsetMs / 1000f)
                    Text(
                        text = if (syncOffsetMs == 0L) "LYRICS SYNC" else "SYNC $offsetLabel",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProvidersSelector(
    providerResults: Map<String, LyricsResult>,
    selectedProvider: String?,
    onProviderSelected: (String) -> Unit,
    onRefindClick: () -> Unit,
    accentColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    // Provider order is static. Rank once per result-map update rather than once per row.
    val knownProviders = remember {
        listOf(
            "Apple Music (Better Lyrics)",
            "Apple Music",
            "Bini",
            "QQ Music",
            "NetEase Music",
            "KuGou",
            "LyricsPlus",
            "YouTube Music",
            "YouTube (Paxsenix)",
            "Spotify",
            "Musixmatch",
            "SyncLRC",
            "LRCLIB",
            "EmbeddedID3",
            "Unison",
            "Genius"
        )
    }
    val allProviders = remember(providerResults.keys) {
        (knownProviders + providerResults.keys.sorted()).distinct()
    }

    // Keep failed/no-match providers visible too, so the user can see every searched source.
    val visibleProviders = allProviders
    val bestResult = remember(providerResults) { bestLyricsProviderResult(providerResults.values) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "LYRICS PROVIDERS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            visibleProviders.forEachIndexed { index, provider ->
                val result = providerResults[provider]

                val isSelected = selectedProvider == provider ||
                    (selectedProvider == null && result != null && result == bestResult)

                val hasData = result != null && result.hasLyrics() && result.confidence >= 0f

                val displayName = when (provider) {
                    "EmbeddedID3" -> "Embedded ID3"
                    else -> provider
                }

                val supportingText = when {
                    result == null -> "Not fetched yet · tap to fetch this source"
                    result.payloadClearedForMemory -> "Lyrics payload released · tap to fetch this source"
                    !result.hasLyrics() -> "No lyrics found · tap to search this source"
                    result.isWordByWord -> "Karaoke (word-by-word)"
                    result.isSynced -> "Synced lyrics"
                    else -> "Plain text"
                }

                val shape = getSegmentedShape(index = index, count = visibleProviders.size)

                val itemBgColor = when {
                    isSelected -> accentColor.copy(alpha = 0.15f)
                    hasData || result == null -> contentColor.copy(alpha = 0.06f)
                    else -> contentColor.copy(alpha = 0.02f)
                }

                ListItem(
                    selected = isSelected,
                    onClick = {
                        // Every row targets exactly this provider. Never restart the full sweep
                        // just because its lyrics payload was released or its initial result is null.
                        onProviderSelected(provider)
                    },
                    supportingContent = {
                        Text(text = supportingText)
                    },
                    leadingContent = {
                        val iconRes = when (provider) {
                            "SyncLRC" -> R.drawable.ic_lyrics
                            "LRCLIB" -> R.drawable.ic_lyrics
                            "YouTube Music" -> R.drawable.ic_youtube_music
                            else -> R.drawable.ic_lyrics
                        }
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = provider,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    trailingContent = {
                        if (isSelected) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check),
                                contentDescription = "Selected",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = itemBgColor,
                        selectedContainerColor = itemBgColor,
                        contentColor = contentColor.copy(alpha = if (hasData || result == null) 0.9f else 0.4f),
                        selectedContentColor = accentColor,
                        leadingContentColor = contentColor.copy(alpha = if (hasData || result == null) 0.8f else 0.4f),
                        selectedLeadingContentColor = accentColor,
                        trailingContentColor = contentColor.copy(alpha = 0.8f),
                        selectedTrailingContentColor = accentColor,
                        supportingContentColor = contentColor.copy(alpha = 0.6f),
                        selectedSupportingContentColor = accentColor.copy(alpha = 0.7f),
                        disabledContainerColor = itemBgColor,
                        disabledContentColor = contentColor.copy(alpha = 0.3f),
                        disabledLeadingContentColor = contentColor.copy(alpha = 0.3f),
                        disabledTrailingContentColor = contentColor.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                ) {
                    Text(
                        text = displayName,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val buttonInteractionSource = remember { MutableInteractionSource() }
        val buttonIsPressed by buttonInteractionSource.collectIsPressedAsState()

        val buttonCornerRadius by animateDpAsState(
            targetValue = if (buttonIsPressed) 28.dp else 16.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "refind_button_corners"
        )

        androidx.compose.material3.Button(
            onClick = onRefindClick,
            interactionSource = buttonInteractionSource,
            shape = RoundedCornerShape(buttonCornerRadius),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = accentColor,
                contentColor = Color.Black
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_refresh),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Refind Lyrics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

private fun getSegmentedShape(index: Int, count: Int): androidx.compose.ui.graphics.Shape {
    val outer = 24.dp
    val inner = 4.dp
    return when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        index == count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}


/** Karaoke > line-synchronized > plain; prefer KuGou within a quality tier, then confidence. */
private fun bestLyricsProviderResult(results: Collection<LyricsResult>): LyricsResult? =
    results.asSequence()
        .filter { it.hasLyrics() && it.confidence >= 0f }
        .maxWithOrNull(
            compareBy<LyricsResult> {
                when {
                    it.isWordByWord -> 3
                    it.isSynced -> 2
                    !it.isInstrumental && it.hasLyrics() -> 1
                    else -> 0
                }
            }.thenBy { it.providerName.equals("KuGou", ignoreCase = true) }
                .thenBy { it.confidence }
        )
