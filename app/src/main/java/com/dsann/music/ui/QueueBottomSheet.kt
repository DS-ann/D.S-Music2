package com.dsann.music.ui

import com.dsann.music.ui.theme.DsmusicCard
import com.dsann.music.ui.theme.DsmusicSurface
import com.dsann.music.ui.theme.DsmusicButton
import com.dsann.music.ui.theme.DsmusicOutlinedButton
import com.dsann.music.ui.theme.DsmusicTextButton
import com.dsann.music.ui.theme.DsmusicFilterChip
import com.dsann.music.ui.theme.DsmusicSwitchControl
import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import com.dsann.music.ui.theme.HorizontalDivider
import com.dsann.music.ui.theme.Icon
import com.dsann.music.ui.theme.IconButton
import com.dsann.music.ui.theme.ListItem
import com.dsann.music.ui.theme.ListItemDefaults
import com.dsann.music.ui.theme.ListItemShapes
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.Surface
import com.dsann.music.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.fragment.app.FragmentActivity
import com.dsann.music.util.FavoritesManager
import coil.compose.AsyncImage
import com.dsann.music.R
import com.dsann.music.model.SongItem
import com.dsann.music.data.lyrics.LineSyncedLyricTimeline
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.delay
import com.dsann.music.ui.theme.DsmusicAppThemeFromPreferences
import com.dsann.music.viewmodel.PlayerSharedViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor


class QueueBottomSheet : BottomSheetDialogFragment() {

    private lateinit var viewModel: PlayerSharedViewModel

    override fun onStart() {
        super.onStart()
        val sheetDialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog ?: return
        val sheetContainer = sheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        // The Compose queue surface is borderless; keep Material's dialog container
        // transparent so its default drawable cannot draw a second white perimeter.
        sheetDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        sheetContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[PlayerSharedViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
                val playerBackgroundColor by viewModel.playerBackgroundColor.collectAsStateWithLifecycle()
                DsmusicAppThemeFromPreferences(
                    dynamicAlbumColor = if (currentSong != null) playerBackgroundColor else null
                ) {
                    QueueBottomSheetContent(
                        viewModel = viewModel,
                        onSongLongClick = { song ->
                            showQueueSongActions(requireContext(), song, viewModel)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueBottomSheetContent(
    viewModel: PlayerSharedViewModel,
    onSongLongClick: (SongItem) -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val songList by viewModel.songListFlow.collectAsStateWithLifecycle()
    val currentSongIndex by viewModel.currentSongIndexFlow.collectAsStateWithLifecycle()

    val displayedSongs = songList

    val lazyListState = rememberLazyListState()
    val dragDropState = rememberDragDropState(
        lazyListState = lazyListState,
        onDragFinished = { viewModel.syncQueueWithPlaybackService() }
    ) { from, to ->
        // Keep Media3's native queue untouched while the finger is moving. Rebuilding
        // its preloaded next item on every crossed row makes the target row flash.
        viewModel.reorderQueue(from, to, syncPlaybackService = false)
    }

    // Auto-scroll to currently playing track on initial load
    LaunchedEffect(currentSongIndex) {
        if (dragDropState.currentIndexOfDraggedItem != null) return@LaunchedEffect
        if (currentSongIndex in displayedSongs.indices) {
            val distance = abs(lazyListState.firstVisibleItemIndex - currentSongIndex)
            if (distance > 24) {
                lazyListState.scrollToItem(currentSongIndex)
            } else {
                lazyListState.animateScrollToItem(currentSongIndex)
            }
        }
    }

    DsmusicSurface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isLandscape) Modifier.fillMaxHeight(0.9f) else Modifier.wrapContentHeight()),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = DsmusicTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        showBorder = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 24.dp)
                .then(if (isLandscape) Modifier.padding(start = 88.dp, end = 24.dp) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag Handle
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(DsmusicTheme.colorScheme.outlineVariant)
            )

            if (!isLandscape) {
                // PORTRAIT: Standard Flow Layout
                Spacer(modifier = Modifier.height(20.dp))

                // Title & Counter Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Up Next",
                        style = DsmusicTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DsmusicTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "${displayedSongs.size} tracks",
                        style = DsmusicTheme.typography.labelMedium,
                        color = DsmusicTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }

                QueueSyncedLyricPreview(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 8.dp)
                )

                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    color = DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // Content Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                ) {
                    if (displayedSongs.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 64.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_queue_music),
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Your queue is empty",
                                style = DsmusicTheme.typography.titleMedium,
                                color = DsmusicTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxSize(),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                        ) {
                            itemsIndexed(
                                items = displayedSongs,
                                key = { _, song -> song.id },
                                contentType = { _, _ -> "queue-song" }
                            ) { index, song ->
                                val isPlaying = (index == currentSongIndex)
                                val shapes = ListItemDefaults.segmentedShapes(index = index, count = displayedSongs.size)
                                
                                val isDragging = song.id == dragDropState.draggedItemKey
                                val displacement = if (isDragging) dragDropState.elementDisplacement ?: 0f else 0f

                                Box(
                                    modifier = Modifier
                                        .then(
                                            if (dragDropState.isDragging) Modifier
                                            else Modifier.animateItem(
                                                fadeInSpec = null,
                                                fadeOutSpec = null,
                                                placementSpec = spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            )
                                        )
                                        .zIndex(if (isDragging) 1f else 0f)
                                        .graphicsLayer {
                                            translationY = displacement
                                            val liftScale = if (isDragging) 1.05f else 1f
                                            scaleX = liftScale
                                            scaleY = liftScale
                                            shadowElevation = 0f
                                            shape = RoundedCornerShape(18.dp)
                                        }
                                ) {
                                    QueueListItem(
                                        song = song,
                                        isPlaying = isPlaying,
                                        shapes = shapes,
                                        dragDropState = dragDropState,
                                        index = index,
                                        showDivider = index < displayedSongs.lastIndex,
                                        onLongClick = { onSongLongClick(song) },
                                        onSwipeRemove = { viewModel.removeFromQueue(song.id) },
                                        onClick = {
                                            viewModel.playSongAtIndex(index)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // LANDSCAPE: Split-Screen Dashboard
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    // LEFT PANE: Sidebar Selector (40%)
                    Column(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight()
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column {
                            Text(
                                text = "Audio Queue",
                                style = DsmusicTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = DsmusicTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${displayedSongs.size} tracks loaded",
                                style = DsmusicTheme.typography.bodySmall,
                                color = DsmusicTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        HorizontalDivider(color = DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        Text(
                            text = "VIEW SOURCE",
                            style = DsmusicTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                            fontWeight = FontWeight.Bold,
                            color = DsmusicTheme.colorScheme.primary
                        )

                        QueueSidebarItem(
                            label = "Up Next",
                            isSelected = true,
                            icon = { 
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow, 
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                ) 
                            },
                            onClick = {}
                        )
                    }

                    // RIGHT PANE: Native Segmented LazyColumn (60%)
                    Box(
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight()
                    ) {
                        if (displayedSongs.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_queue_music),
                                    contentDescription = null,
                                    modifier = Modifier.size(60.dp),
                                    tint = DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Queue is empty",
                                    style = DsmusicTheme.typography.titleMedium,
                                    color = DsmusicTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(end = 20.dp, bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                            ) {
                                itemsIndexed(
                                    items = displayedSongs,
                                    key = { _, song -> song.id },
                                    contentType = { _, _ -> "queue-song" }
                                ) { index, song ->
                                    val isPlaying = (index == currentSongIndex)
                                    val shapes = ListItemDefaults.segmentedShapes(index = index, count = displayedSongs.size)
                                    
                                    val isDragging = song.id == dragDropState.draggedItemKey
                                    val displacement = if (isDragging) dragDropState.elementDisplacement ?: 0f else 0f

                                    Box(
                                        modifier = Modifier
                                            .then(
                                            if (dragDropState.isDragging) Modifier
                                            else Modifier.animateItem(
                                                fadeInSpec = null,
                                                fadeOutSpec = null,
                                                placementSpec = spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            )
                                        )
                                            .zIndex(if (isDragging) 1f else 0f)
                                            .graphicsLayer {
                                                translationY = displacement
                                                val liftScale = if (isDragging) 1.05f else 1f
                                                scaleX = liftScale
                                                scaleY = liftScale
                                                shadowElevation = 0f
                                                shape = RoundedCornerShape(18.dp)
                                            }
                                    ) {
                                        QueueListItem(
                                            song = song,
                                            isPlaying = isPlaying,
                                            shapes = shapes,
                                            dragDropState = dragDropState,
                                            index = index,
                                            showDivider = index < displayedSongs.lastIndex,
                                            onLongClick = { onSongLongClick(song) },
                                            onSwipeRemove = { viewModel.removeFromQueue(song.id) },
                                            onClick = {
                                                viewModel.playSongAtIndex(index)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
internal fun QueueSyncedLyricPreview(
    viewModel: PlayerSharedViewModel,
    modifier: Modifier = Modifier
) {
    val lyrics by viewModel.syncedLyrics.collectAsStateWithLifecycle()
    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val audioService by viewModel.audioServiceState.collectAsStateWithLifecycle()
    val timeline = remember(lyrics) {
        LineSyncedLyricTimeline.build(lyrics.orEmpty())
    }
    val songKey = currentSong?.videoId?.takeIf { it.isNotBlank() }
        ?: currentSong?.path?.takeIf { it.isNotBlank() }
        ?: currentSong?.id?.toString()
    val positionProvider = remember(audioService, viewModel) {
        {
            audioService?.getCurrentPositionForLyrics()
                ?: viewModel.currentPosition.value
        }
    }
    val activeLine by produceState<String?>(
        initialValue = null,
        songKey,
        timeline,
        duration,
        isPlaying,
        positionProvider,
        currentPosition
    ) {
        while (true) {
            val positionMs = positionProvider().coerceAtLeast(0).toLong()
            val index = LineSyncedLyricTimeline.activeIndexAt(
                timeline = timeline,
                positionMs = positionMs,
                durationMs = duration.toLong().coerceAtLeast(0L)
            )
            val nextText = timeline.getOrNull(index)?.line?.content?.trim()
                ?.takeIf { it.isNotEmpty() }
            if (value != nextText) value = nextText
            delay(
                if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M) 84L
                else if (isPlaying) 48L else 84L
            )
        }
    }

    if (!activeLine.isNullOrBlank()) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(DsmusicTheme.colorScheme.primaryContainer.copy(alpha = 0.54f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lyrics),
                contentDescription = "Current lyric",
                tint = DsmusicTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = activeLine.orEmpty(),
                style = DsmusicTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 17.sp
                ),
                color = DsmusicTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueListItem(
    song: SongItem,
    isPlaying: Boolean,
    shapes: ListItemShapes,
    dragDropState: DragDropState? = null,
    index: Int = -1,
    showDivider: Boolean = true,
    onLongClick: () -> Unit = {},
    onSwipeRemove: () -> Unit = {},
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val latestIndex = rememberUpdatedState(index)
    val latestSwipeRemove = rememberUpdatedState(onSwipeRemove)
    val swipeThresholdPx = with(LocalDensity.current) { 88.dp.toPx() }
    val swipeOffset = remember(song.id) { Animatable(0f) }
    var swipeDragOffset by remember(song.id) { mutableFloatStateOf(0f) }
    val swipeAnimationScope = rememberCoroutineScope()
    val rowInteractionSource = remember(song.id) { MutableInteractionSource() }
    val taperedDividersEnabled = rememberTaperedSongDividersEnabled()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val distance = abs(swipeOffset.value + swipeDragOffset)
                translationX = swipeOffset.value + swipeDragOffset
                // A small fade and compression make the row feel like it is leaving,
                // while the horizontal translation remains the primary dismissal cue.
                val fade = (distance / (swipeThresholdPx * 3f)).coerceIn(0f, 0.65f)
                alpha = 1f - fade
                scaleY = 1f - fade * 0.06f
            }
            .pointerInput(song.id, swipeThresholdPx) {
                val dismissalWidthPx = size.width.toFloat().coerceAtLeast(swipeThresholdPx)
                var horizontalTravel = 0f
                var dismissing = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        horizontalTravel = 0f
                        if (!dismissing) swipeDragOffset = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (!dismissing) {
                            horizontalTravel += dragAmount
                            change.consume()
                            swipeDragOffset = (swipeDragOffset + dragAmount).coerceIn(
                                -dismissalWidthPx,
                                dismissalWidthPx
                            )
                        }
                    },
                    onDragEnd = {
                        val releasedOffset = swipeDragOffset
                        when {
                            !dismissing && abs(horizontalTravel) >= swipeThresholdPx -> {
                                dismissing = true
                                val direction = if (horizontalTravel < 0f) -1f else 1f
                                val targetOffset = direction * (dismissalWidthPx + swipeThresholdPx)
                                swipeAnimationScope.launch {
                                    swipeOffset.snapTo(releasedOffset)
                                    swipeDragOffset = 0f
                                    swipeOffset.animateTo(
                                        targetOffset,
                                        animationSpec = tween(durationMillis = 190)
                                    )
                                    latestSwipeRemove.value()
                                }
                            }
                            !dismissing -> {
                                swipeAnimationScope.launch {
                                    swipeOffset.snapTo(releasedOffset)
                                    swipeDragOffset = 0f
                                    swipeOffset.animateTo(
                                        0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        }
                        horizontalTravel = 0f
                    },
                    onDragCancel = {
                        horizontalTravel = 0f
                        if (!dismissing) {
                            val releasedOffset = swipeDragOffset
                            swipeAnimationScope.launch {
                                swipeOffset.snapTo(releasedOffset)
                                swipeDragOffset = 0f
                                swipeOffset.animateTo(
                                    0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        }
                    }
                )
            }
            .clip(shapes.shape)
            .background(
                if (isPlaying) DsmusicTheme.colorScheme.primary.copy(alpha = 0.18f)
                else Color.Transparent
            )
            .taperedSongDivider(
                enabled = taperedDividersEnabled && showDivider,
                color = DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)
            )
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    interactionSource = rowInteractionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val albumArtModel = remember(song.id, song.thumbnailUrl, song.albumId, song.path) {
                song.getAlbumArtUri() ?: R.drawable.default_album_art
            }
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = albumArtModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                )
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Playing",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = song.title,
                    fontWeight = if (isPlaying) FontWeight.ExtraBold else FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = if (isPlaying) DsmusicTheme.colorScheme.primary else DsmusicTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    fontSize = 13.sp,
                    color = if (isPlaying) DsmusicTheme.colorScheme.primary.copy(alpha = 0.8f)
                    else DsmusicTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // The only trailing queue control: the three-bar drag handle.
        // It has its own pointer region, so long-pressing the song body opens the song menu.
        Box(
            modifier = Modifier
                .size(48.dp)
                .pointerInput(dragDropState, song.id) {
                    val state = dragDropState ?: return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val startIndex = latestIndex.value
                            if (startIndex >= 0) {
                                state.onDragStartAtIndex(
                                    index = startIndex,
                                    handleLocalY = offset.y,
                                    handleHeightPx = size.height.toFloat()
                                )
                            }
                        },
                        onDragEnd = { state.onDragInterrupted() },
                        onDragCancel = { state.onDragInterrupted() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            state.onDrag(dragAmount)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Menu,
                contentDescription = "Drag to reorder",
                tint = DsmusicTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.size(25.dp)
            )
        }
    }
}

@Composable
fun QueueSidebarItem(
    label: String,
    isSelected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    DsmusicSurface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) DsmusicTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f) else Color.Transparent,
        contentColor = if (isSelected) DsmusicTheme.colorScheme.onSecondaryContainer else DsmusicTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        showBorder = false
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon()
            Text(
                text = label,
                style = DsmusicTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

class DragDropState(
    val lazyListState: LazyListState,
    private val scope: CoroutineScope,
    private val edgeScrollThresholdPx: Float,
    private val maxAutoScrollSpeedPxPerSecond: Float,
    private val onDragFinished: () -> Unit,
    private val onMove: (Int, Int) -> Unit
) {
    var draggedDistance by mutableFloatStateOf(0f)
    var initiallyDraggedElement by mutableStateOf<LazyListItemInfo?>(null)
    var currentIndexOfDraggedItem by mutableStateOf<Int?>(null)
    private var queueChangedDuringDrag = false
    private var pointerY = 0f
    private var pendingMoveTargetIndex: Int? = null
    private var autoScrollDirection = 0f
    private var autoScrollIntensity = 0f
    private var autoScrollJob: Job? = null

    val draggedItemKey: Any?
        get() = initiallyDraggedElement?.key

    val isDragging: Boolean
        get() = currentIndexOfDraggedItem != null

    val elementDisplacement: Float?
        get() = initiallyDraggedElement?.let { anchor ->
            lazyListState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == anchor.key }
                ?.let { item -> anchor.offset.toFloat() + draggedDistance - item.offset }
        }

    private fun beginDrag(item: LazyListItemInfo, initialIndex: Int, initialPointerY: Float) {
        stopAutoScroll()
        queueChangedDuringDrag = false
        draggedDistance = 0f
        initiallyDraggedElement = item
        currentIndexOfDraggedItem = initialIndex
        pointerY = initialPointerY
        pendingMoveTargetIndex = null
    }

    fun onDragStart(offset: Offset) {
        val item = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull {
            offset.y.toInt() in it.offset..(it.offset + it.size)
        } ?: return
        beginDrag(item, item.index, offset.y)
    }

    /** Start from the row and the exact pointer position inside its drag handle. */
    fun onDragStartAtIndex(index: Int, handleLocalY: Float, handleHeightPx: Float) {
        val item = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        val handleTopInRow = ((item.size - handleHeightPx) / 2f).coerceAtLeast(0f)
        beginDrag(
            item = item,
            initialIndex = item.index,
            initialPointerY = item.offset + handleTopInRow + handleLocalY
        )
    }

    fun onDragInterrupted() {
        val shouldSyncQueue = queueChangedDuringDrag
        queueChangedDuringDrag = false
        stopAutoScroll()
        initiallyDraggedElement = null
        currentIndexOfDraggedItem = null
        pendingMoveTargetIndex = null
        draggedDistance = 0f
        pointerY = 0f
        // Rebuild Media3's native queue once, after the visual reorder is complete.
        if (shouldSyncQueue) onDragFinished()
    }

    /**
     * Hit-test against the actual visible row centres. Neighbour placement
     * animations are disabled while dragging, so these are stable targets.
     * A pending move is acknowledged only when LazyColumn reports the dragged
     * song at its new stable-key index.
     */
    private fun moveDraggedItemToPointer() {
        val anchor = initiallyDraggedElement ?: return
        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        val draggedItem = visibleItems.firstOrNull { it.key == anchor.key } ?: return

        val pendingTarget = pendingMoveTargetIndex
        if (pendingTarget != null) {
            if (draggedItem.index != pendingTarget) return
            currentIndexOfDraggedItem = pendingTarget
            pendingMoveTargetIndex = null
        } else {
            currentIndexOfDraggedItem = draggedItem.index
        }

        val fromIndex = currentIndexOfDraggedItem ?: return
        val otherItems = visibleItems.filter { it.key != anchor.key }

        // Move through one adjacent slot per acknowledged layout update. A single
        // pointer sample can span more than one row on fast drags; applying every
        // crossed index against the pre-layout offsets briefly sends neighbours past
        // their final slot and makes them jump back on the next frame.
        val next = otherItems.firstOrNull { it.index == fromIndex + 1 }
        val previous = otherItems.firstOrNull { it.index == fromIndex - 1 }
        val targetIndex = when {
            next != null && pointerY > next.offset + next.size / 2f -> fromIndex + 1
            previous != null && pointerY < previous.offset + previous.size / 2f -> fromIndex - 1
            else -> fromIndex
        }.coerceIn(0, lazyListState.layoutInfo.totalItemsCount - 1)
        if (targetIndex != fromIndex) {
            // Update the canonical index before the next pointer/frame callback.
            // The pending acknowledgement prevents a stale LazyList layout from
            // issuing the same move twice or bouncing the item back.
            queueChangedDuringDrag = true
            currentIndexOfDraggedItem = targetIndex
            pendingMoveTargetIndex = targetIndex
            onMove(fromIndex, targetIndex)
        }
    }

    fun onDrag(dragAmount: Offset) {
        if (initiallyDraggedElement == null) return
        draggedDistance += dragAmount.y
        pointerY += dragAmount.y
        moveDraggedItemToPointer()
        updateAutoScroll()
    }

    private fun updateAutoScroll() {
        val layoutInfo = lazyListState.layoutInfo
        val viewportStart = layoutInfo.viewportStartOffset.toFloat()
        val viewportEnd = layoutInfo.viewportEndOffset.toFloat()
        val viewportHeight = (viewportEnd - viewportStart).coerceAtLeast(1f)
        val edgeSize = edgeScrollThresholdPx.coerceAtMost(viewportHeight / 3f).coerceAtLeast(1f)

        val topProximity = ((viewportStart + edgeSize - pointerY) / edgeSize).coerceIn(0f, 1f)
        val bottomProximity = ((pointerY - (viewportEnd - edgeSize)) / edgeSize).coerceIn(0f, 1f)
        val direction = when {
            topProximity > 0f -> -1f
            bottomProximity > 0f -> 1f
            else -> 0f
        }
        val intensity = when {
            topProximity > 0f -> 0.12f + 0.88f * topProximity
            bottomProximity > 0f -> 0.12f + 0.88f * bottomProximity
            else -> 0f
        }

        if (direction == 0f || currentIndexOfDraggedItem == null) {
            stopAutoScroll()
            return
        }

        autoScrollDirection = direction
        autoScrollIntensity = intensity
        if (autoScrollJob?.isActive == true) return

        autoScrollJob = scope.launch {
            var lastFrameNanos = 0L
            while (autoScrollDirection != 0f && currentIndexOfDraggedItem != null) {
                // Use display vsync directly. Decorative animation FPS caps must not
                // pause or starve a user-controlled drag scroll.
                val frameNanos = withFrameNanos { it }
                val elapsedNanos = if (lastFrameNanos > 0L && frameNanos > lastFrameNanos) {
                    (frameNanos - lastFrameNanos).coerceAtMost(50_000_000L)
                } else {
                    16_666_667L
                }
                lastFrameNanos = frameNanos

                val pixelsPerSecond = maxAutoScrollSpeedPxPerSecond * autoScrollIntensity
                val requested = autoScrollDirection * pixelsPerSecond *
                    (elapsedNanos / 1_000_000_000f)
                val consumed = lazyListState.scrollBy(requested)

                if (kotlin.math.abs(consumed) < 0.1f) {
                    // At the absolute list boundary. A new drag event can restart this
                    // loop if the user moves away from the boundary and re-enters.
                    autoScrollDirection = 0f
                    autoScrollIntensity = 0f
                    autoScrollJob = null
                    break
                }
                moveDraggedItemToPointer()
            }
        }
    }

    private fun stopAutoScroll() {
        autoScrollDirection = 0f
        autoScrollIntensity = 0f
        autoScrollJob?.cancel()
        autoScrollJob = null
    }
}

@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    onDragFinished: () -> Unit = {},
    onMove: (Int, Int) -> Unit
): DragDropState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val edgeScrollThresholdPx = with(density) { 104.dp.toPx() }
    val maxAutoScrollSpeedPxPerSecond = with(density) { 620.dp.toPx() }
    val currentOnMove = rememberUpdatedState(onMove)
    val currentOnDragFinished = rememberUpdatedState(onDragFinished)
    return remember(lazyListState, edgeScrollThresholdPx, maxAutoScrollSpeedPxPerSecond) {
        DragDropState(
            lazyListState = lazyListState,
            scope = scope,
            edgeScrollThresholdPx = edgeScrollThresholdPx,
            maxAutoScrollSpeedPxPerSecond = maxAutoScrollSpeedPxPerSecond,
            onDragFinished = { currentOnDragFinished.value() },
            onMove = { from, to -> currentOnMove.value(from, to) }
        )
    }
}

fun Modifier.dragContainer(dragDropState: DragDropState, enabled: Boolean): Modifier {
    if (!enabled) return this
    return this.then(
        Modifier.pointerInput(dragDropState) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset -> dragDropState.onDragStart(offset) },
                onDragEnd = { dragDropState.onDragInterrupted() },
                onDragCancel = { dragDropState.onDragInterrupted() },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragDropState.onDrag(dragAmount)
                }
            )
        }
    )
}


/**
 * Opens the regular song action sheet from a queue row, excluding the redundant
 * "Add to queue" action because the track is already in the active queue.
 */
fun showQueueSongActions(
    context: Context,
    song: SongItem,
    viewModel: PlayerSharedViewModel
) {
    val activity = context.findFragmentActivity() ?: return
    val fragmentManager = activity.supportFragmentManager
    if (fragmentManager.isStateSaved) return

    val sheet = SongActionsBottomSheet.newInstance(song, showAddToQueue = false, showRemoveFromQueue = true, showDelete = false, queueSongMode = true)
    sheet.setActionListener(object : SongActionsBottomSheet.ActionListener {
        override fun onPlay(song: SongItem) {
            viewModel.playSong(song)
        }

        override fun onPlayNext(song: SongItem) {
            viewModel.addToQueueNext(song)
        }

        override fun onAddToQueue(song: SongItem) = Unit
        override fun onDelete(song: SongItem) { viewModel.removeFromQueue(song.id) }
        override fun onEdit(song: SongItem) = Unit

        override fun onFavorite(song: SongItem, isFav: Boolean) {
            FavoritesManager(context).setFavorite(song.id, isFav)
        }

        override fun onShare(song: SongItem) {
            runCatching {
                val shareText = song.videoId?.takeIf { it.isNotBlank() }
                    ?.let { "https://music.youtube.com/watch?v=$it" }
                    ?: song.contentUri.toString()
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Listen to ${song.title}: $shareText")
                }
                context.startActivity(Intent.createChooser(intent, "Share Song"))
            }
        }
    })
    sheet.show(fragmentManager, "QueueSongActionsBottomSheet")
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return current as? FragmentActivity
}
