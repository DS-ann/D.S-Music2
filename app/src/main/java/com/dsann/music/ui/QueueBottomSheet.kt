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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.dsann.music.ui.theme.DsmusicAppTheme
import com.dsann.music.ui.player.rememberAppAnimationFrameTimeNanos
import com.dsann.music.viewmodel.PlayerSharedViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs


class QueueBottomSheet : BottomSheetDialogFragment() {

    private lateinit var viewModel: PlayerSharedViewModel

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
                DsmusicAppTheme(darkTheme = isSystemInDarkTheme()) {
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
    val dragDropState = rememberDragDropState(lazyListState = lazyListState) { from, to ->
        viewModel.reorderQueue(from, to)
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
        tonalElevation = 6.dp
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

                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
                                
                                val isDragging = index == dragDropState.currentIndexOfDraggedItem
                                val displacement = if (isDragging) dragDropState.elementDisplacement ?: 0f else 0f

                                Box(
                                    modifier = Modifier
                                        .animateItem()
                                        .zIndex(if (isDragging) 1f else 0f)
                                        .graphicsLayer {
                                            translationY = displacement
                                            val liftScale = if (isDragging) 1.05f else 1f
                                            scaleX = liftScale
                                            scaleY = liftScale
                                            shadowElevation = if (isDragging) 14.dp.toPx() else 0f
                                            shape = RoundedCornerShape(18.dp)
                                        }
                                ) {
                                    QueueListItem(
                                        song = song,
                                        isPlaying = isPlaying,
                                        shapes = shapes,
                                        dragDropState = dragDropState,
                                        index = index,
                                        onLongClick = { onSongLongClick(song) },
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
                                    
                                    val isDragging = index == dragDropState.currentIndexOfDraggedItem
                                    val displacement = if (isDragging) dragDropState.elementDisplacement ?: 0f else 0f

                                    Box(
                                        modifier = Modifier
                                            .animateItem()
                                            .zIndex(if (isDragging) 1f else 0f)
                                            .graphicsLayer {
                                                translationY = displacement
                                                val liftScale = if (isDragging) 1.05f else 1f
                                                scaleX = liftScale
                                                scaleY = liftScale
                                                shadowElevation = if (isDragging) 14.dp.toPx() else 0f
                                                shape = RoundedCornerShape(18.dp)
                                            }
                                    ) {
                                        QueueListItem(
                                            song = song,
                                            isPlaying = isPlaying,
                                            shapes = shapes,
                                            dragDropState = dragDropState,
                                            index = index,
                                            onLongClick = { onSongLongClick(song) },
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


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueListItem(
    song: SongItem,
    isPlaying: Boolean,
    shapes: ListItemShapes,
    dragDropState: DragDropState? = null,
    index: Int = -1,
    onLongClick: () -> Unit = {},
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // The row may move to another index while this pointerInput remains active.
    // Keep the latest index without restarting the gesture detector.
    val latestIndex = rememberUpdatedState(index)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.shape)
            .background(
                if (isPlaying) DsmusicTheme.colorScheme.primary.copy(alpha = 0.18f)
                else Color.Transparent
            )
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
                        onDragStart = {
                            val startIndex = latestIndex.value
                            if (startIndex >= 0) state.onDragStartAtIndex(startIndex)
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
        modifier = Modifier.fillMaxWidth()
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
    private val frameTimeNanos: State<Long>,
    private val onFrameLoopActiveChanged: (Boolean) -> Unit,
    private val onMove: (Int, Int) -> Unit
) {
    var draggedDistance by mutableFloatStateOf(0f)
    var initiallyDraggedElement by mutableStateOf<LazyListItemInfo?>(null)
    var currentIndexOfDraggedItem by mutableStateOf<Int?>(null)
    private var autoScrollJob: Job? = null
    private var autoScrollDeltaPx: Float = 0f

    val elementDisplacement: Float?
        get() = currentIndexOfDraggedItem?.let { currentIndex ->
            lazyListState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == currentIndex }
                ?.let { item ->
                    (initiallyDraggedElement?.offset ?: 0).toFloat() + draggedDistance - item.offset
                }
        }

    fun onDragStart(offset: Offset) {
        stopAutoScroll()
        draggedDistance = 0f
        initiallyDraggedElement = null
        currentIndexOfDraggedItem = null
        lazyListState.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
            ?.also {
                initiallyDraggedElement = it
                currentIndexOfDraggedItem = it.index
            }
    }

    /** Start from the exact LazyColumn row that owns the drag handle. */
    fun onDragStartAtIndex(index: Int) {
        stopAutoScroll()
        draggedDistance = 0f
        val item = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        initiallyDraggedElement = item
        currentIndexOfDraggedItem = item?.index
    }

    fun onDragInterrupted() {
        initiallyDraggedElement = null
        currentIndexOfDraggedItem = null
        draggedDistance = 0f
        stopAutoScroll()
    }

    /**
     * Keep the original grabbed row as the drag anchor for the entire gesture.
     * Re-anchoring after each move discarded the finger's remaining displacement,
     * which made long drags stop after roughly one item.
     */
    private fun moveDraggedItemToPointer() {
        val anchor = initiallyDraggedElement ?: return
        val fromIndex = currentIndexOfDraggedItem ?: return
        val currentCenter = anchor.offset + draggedDistance + anchor.size / 2f
        val targetItem = lazyListState.layoutInfo.visibleItemsInfo.minByOrNull { item ->
            kotlin.math.abs((item.offset + item.size / 2f) - currentCenter)
        } ?: return

        if (targetItem.index != fromIndex) {
            onMove(fromIndex, targetItem.index)
            currentIndexOfDraggedItem = targetItem.index
        }
    }

    fun onDrag(dragAmount: Offset) {
        draggedDistance += dragAmount.y
        val anchor = initiallyDraggedElement ?: return
        val currentOffset = anchor.offset + draggedDistance
        val size = anchor.size

        // At either viewport edge, let the list move first. Reordering against the
        // currently visible edge row before scrolling makes the dragged song jump
        // between rows instead of following the finger into the next viewport.
        val viewportStart = lazyListState.layoutInfo.viewportStartOffset.toFloat()
        val viewportEnd = lazyListState.layoutInfo.viewportEndOffset.toFloat()
        val overscrollThreshold = 80f
        val scrollDelta = when {
            currentOffset < viewportStart + overscrollThreshold -> -20f
            currentOffset + size > viewportEnd - overscrollThreshold -> 20f
            else -> 0f
        }

        if (scrollDelta != 0f) {
            updateAutoScroll(scrollDelta)
        } else {
            stopAutoScroll()
            moveDraggedItemToPointer()
        }
    }

    private fun updateAutoScroll(scrollDeltaPx: Float) {
        if (scrollDeltaPx == 0f) {
            stopAutoScroll()
            return
        }
        autoScrollDeltaPx = scrollDeltaPx
        onFrameLoopActiveChanged(true)
        if (autoScrollJob?.isActive == true) return

        autoScrollJob = scope.launch {
            var lastFrameNanos = 0L
            // Sample the same configurable frame cadence as the player's other continuous
            // visuals. Scale the scroll delta by elapsed time so 20 FPS does not slow the drag.
            snapshotFlow { frameTimeNanos.value }.collect { frameNanos ->
                if (currentIndexOfDraggedItem == null || autoScrollDeltaPx == 0f || frameNanos <= 0L) {
                    return@collect
                }
                val elapsedNanos = if (lastFrameNanos > 0L && frameNanos > lastFrameNanos) {
                    (frameNanos - lastFrameNanos).coerceAtMost(100_000_000L)
                } else {
                    16_666_667L
                }
                lastFrameNanos = frameNanos
                val frameScale = elapsedNanos.toFloat() / 16_666_667f
                val scrolledPx = lazyListState.scrollBy(autoScrollDeltaPx * frameScale)
                // Commit the new target only after scrollBy has moved the viewport.
                // If the list is already at its absolute end, there is no further
                // scroll to wait for, so the visible rows remain valid drop targets.
                if (kotlin.math.abs(scrolledPx) > 0.5f || autoScrollDeltaPx != 0f) {
                    moveDraggedItemToPointer()
                }
            }
        }
    }

    private fun stopAutoScroll() {
        autoScrollDeltaPx = 0f
        autoScrollJob?.cancel()
        autoScrollJob = null
        onFrameLoopActiveChanged(false)
    }
}

@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    onMove: (Int, Int) -> Unit
): DragDropState {
    val scope = rememberCoroutineScope()
    val frameClockActive = remember(lazyListState) { mutableStateOf(false) }
    val frameTimeNanos = rememberAppAnimationFrameTimeNanos(active = frameClockActive.value)
    // Keep the active move callback fresh while preserving drag state across recompositions.
    // Capturing onMove inside remember(lazyListState) can retain an obsolete callback.
    val currentOnMove = rememberUpdatedState(onMove)
    return remember(lazyListState, frameTimeNanos) {
        DragDropState(
            lazyListState,
            scope,
            frameTimeNanos,
            { active -> frameClockActive.value = active }
        ) { from, to ->
            currentOnMove.value(from, to)
        }
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

    val sheet = SongActionsBottomSheet.newInstance(song, showAddToQueue = false)
    sheet.setActionListener(object : SongActionsBottomSheet.ActionListener {
        override fun onPlay(song: SongItem) {
            viewModel.playSong(song)
        }

        override fun onPlayNext(song: SongItem) {
            viewModel.addToQueueNext(song)
        }

        override fun onAddToQueue(song: SongItem) = Unit
        override fun onDelete(song: SongItem) = Unit
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
