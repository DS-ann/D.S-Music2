package com.dsann.music.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsann.music.MainActivity
import com.dsann.music.R
import com.dsann.music.model.PlayerGestureAction
import com.dsann.music.model.SongItem
import com.dsann.music.ui.player.ArtworkPager
import com.dsann.music.ui.player.DsmCoverShapes
import com.dsann.music.ui.player.FullPlayerScreen
import com.dsann.music.ui.player.PlayerUiState
import com.dsann.music.ui.theme.observeKey
import com.dsann.music.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

// ==========================================
// 1. STEP 1: CORE STATE MACHINE & INTERFACES
// ==========================================
enum class PlayerSheetState {
    COLLAPSED,
    EXPANDED
}

private enum class MiniDismissDragPhase {
    IDLE,
    TENSION,
    SNAPPING,
    FREE_DRAG
}

// ==========================================
// 2. STEP 2: DYNAMIC SHAPE & SQUIRCLE MORPHING
// ==========================================
class PlayerSheetDynamicShape(
    private val topRadiusProvider: () -> Dp,
    private val bottomRadiusProvider: () -> Dp,
    private val virtualHeightProvider: () -> Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val topRadius = topRadiusProvider().nonNegative()
        val bottomRadius = bottomRadiusProvider().nonNegative()

        // Fall back to standard high-performance rounded corner shape
        val topRadiusPx = with(density) { topRadius.toPx() }
        val bottomRadiusPx = with(density) { bottomRadius.toPx() }
        val virtualHeight = virtualHeightProvider()
        return Outline.Rounded(
            RoundRect(
                rect = Rect(0f, 0f, size.width, virtualHeight),
                topLeft = CornerRadius(topRadiusPx, topRadiusPx),
                topRight = CornerRadius(topRadiusPx, topRadiusPx),
                bottomRight = CornerRadius(bottomRadiusPx, bottomRadiusPx),
                bottomLeft = CornerRadius(bottomRadiusPx, bottomRadiusPx)
            )
        )
    }
}

private fun Dp.nonNegative(): Dp = takeIf { it.value.isFinite() && it.value > 0f } ?: 0.dp

// ==========================================
// 3. STEP 3: DRAW-PHASE & LAYOUT-PHASE PROVIDERS
// ==========================================
val MiniPlayerHeight = 80.dp // High-fidelity capsule height matching controls breathing room

data class SheetVisualState(
    val currentBottomPadding: Dp,
    val playerContentAreaHeightPxProvider: () -> Float,
    val visualSheetTranslationYProvider: () -> Float,
    val overallSheetTopCornerRadiusProvider: () -> Dp,
    val playerContentActualBottomRadiusProvider: () -> Dp,
    val currentHorizontalPaddingStartPxProvider: () -> Float,
    val currentHorizontalPaddingEndPxProvider: () -> Float
)

@Composable
fun rememberSheetVisualState(
    showPlayerContentArea: Boolean,
    collapsedStateHorizontalPadding: Dp,
    predictiveBackCollapseProgress: Float,
    playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    containerHeight: Dp,
    currentSheetTranslationY: Animatable<Float, AnimationVector1D>,
    sheetCollapsedTargetYProvider: () -> Float,
    isNavBarHiddenProvider: () -> Boolean,
    navBarCornerRadiusDp: Dp,
    miniPlayerHeight: Dp = MiniPlayerHeight
): SheetVisualState {
    
    val currentBottomPadding by remember(showPlayerContentArea, collapsedStateHorizontalPadding, predictiveBackCollapseProgress) {
        derivedStateOf {
            if (predictiveBackCollapseProgress > 0f && showPlayerContentArea) {
                lerp(0.dp, collapsedStateHorizontalPadding, predictiveBackCollapseProgress)
            } else {
                0.dp
            }
        }
    }

    val density = LocalDensity.current
    val miniHeightPx = remember(miniPlayerHeight, density) { with(density) { miniPlayerHeight.toPx() } }
    val containerHeightPx = remember(containerHeight, density) { with(density) { containerHeight.toPx() } }
    
    // Dynamic height provider (Layout-phase)
    val playerContentAreaHeightPxProvider: () -> Float = remember(showPlayerContentArea, playerContentExpansionFraction, miniHeightPx, containerHeightPx) {
        {
            if (showPlayerContentArea) {
                androidx.compose.ui.util.lerp(miniHeightPx, containerHeightPx, playerContentExpansionFraction.value)
            } else {
                0f
            }
        }
    }

    val predictiveBackCollapseProgressState = rememberUpdatedState(predictiveBackCollapseProgress)
    val visualSheetTranslationYProvider: () -> Float = remember(currentSheetTranslationY, sheetCollapsedTargetYProvider) {
        {
            val progress = predictiveBackCollapseProgressState.value
            val collapsedY = sheetCollapsedTargetYProvider()
            currentSheetTranslationY.value * (1f - progress) + (collapsedY * progress)
        }
    }

    // Dynamic top corners logic (lerps from pill corner to 0.dp)
    val overallSheetTopCornerRadiusProvider: () -> Dp = remember(showPlayerContentArea, playerContentExpansionFraction, navBarCornerRadiusDp, isNavBarHiddenProvider, miniPlayerHeight) {
        {
            if (showPlayerContentArea) {
                val collapsedCornerTarget = miniPlayerHeight / 2f
                val fraction = playerContentExpansionFraction.value
                lerp(collapsedCornerTarget, 0.dp, fraction)
            } else {
                miniPlayerHeight / 2f
            }
        }
    }

    // Dynamic bottom corners logic (morphs down to 0.dp)
    val playerContentActualBottomRadiusProvider: () -> Dp = remember(showPlayerContentArea, playerContentExpansionFraction, isNavBarHiddenProvider, navBarCornerRadiusDp, miniPlayerHeight) {
        {
            val fraction = playerContentExpansionFraction.value
            val collapsedRadius = miniPlayerHeight / 2f
            
            // Morphs outward to 26.dp in first 20% drag to form curved floating card, then goes flat
            if (fraction < 0.2f) {
                lerp(collapsedRadius, 26.dp, (fraction / 0.2f).coerceIn(0f, 1f))
            } else {
                lerp(26.dp, 0.dp, ((fraction - 0.2f) / 0.8f).coerceIn(0f, 1f))
            }
        }
    }

    val collapsedStateHorizontalPaddingPx = remember(collapsedStateHorizontalPadding, density) {
        with(density) { collapsedStateHorizontalPadding.toPx() }
    }

    // Draw-phase padding providers
    val currentHorizontalPaddingStartPxProvider: () -> Float = remember(showPlayerContentArea, playerContentExpansionFraction, collapsedStateHorizontalPaddingPx) {
        {
            androidx.compose.ui.util.lerp(collapsedStateHorizontalPaddingPx, 0f, playerContentExpansionFraction.value)
        }
    }
    
    val currentHorizontalPaddingEndPxProvider: () -> Float = remember(showPlayerContentArea, playerContentExpansionFraction, collapsedStateHorizontalPaddingPx) {
        {
            androidx.compose.ui.util.lerp(collapsedStateHorizontalPaddingPx, 0f, playerContentExpansionFraction.value)
        }
    }

    return remember(
        currentBottomPadding,
        playerContentAreaHeightPxProvider,
        visualSheetTranslationYProvider,
        overallSheetTopCornerRadiusProvider,
        playerContentActualBottomRadiusProvider,
        currentHorizontalPaddingStartPxProvider,
        currentHorizontalPaddingEndPxProvider
    ) {
        SheetVisualState(
            currentBottomPadding = currentBottomPadding,
            playerContentAreaHeightPxProvider = playerContentAreaHeightPxProvider,
            visualSheetTranslationYProvider = visualSheetTranslationYProvider,
            overallSheetTopCornerRadiusProvider = overallSheetTopCornerRadiusProvider,
            playerContentActualBottomRadiusProvider = playerContentActualBottomRadiusProvider,
            currentHorizontalPaddingStartPxProvider = currentHorizontalPaddingStartPxProvider,
            currentHorizontalPaddingEndPxProvider = currentHorizontalPaddingEndPxProvider
        )
    }
}

// ==========================================
// 4. STEP 4: STACKING & CROSS-FADING
// ==========================================
@Composable
fun BoxScope.UnifiedPlayerMiniAndFullLayers(
    activity: MainActivity,
    viewModel: PlayerSharedViewModel,
    uiState: PlayerUiState,
    songList: List<SongItem>,
    baseAccentColor: Color,
    context: Context,
    playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    containerHeight: Dp,
    onCollapse: () -> Unit,
    onExpand: () -> Unit,
    dragModifier: Modifier,
    miniPlayerHeight: Dp
) {
    val currentSong = uiState.currentSong ?: return
    val isPlaying = uiState.isPlaying
    val fullPlayerStateHolder = rememberSaveableStateHolder()
    val isLyricsModeEnabled by viewModel.isLyricsModeEnabled.collectAsStateWithLifecycle()
    val isQueueExpanded by viewModel.isQueueExpanded.collectAsStateWithLifecycle()
    val isFullPlayerVisible by remember(playerContentExpansionFraction) {
        derivedStateOf { playerContentExpansionFraction.value >= 0.5f }
    }
    // Mini-player gesture mappings can open Lyrics or Queue directly. Keep those states
    // functional even when the sheet has not crossed the normal full-player threshold.
    val shouldComposeFullPlayer = isFullPlayerVisible || isLyricsModeEnabled || isQueueExpanded

    val miniPlayerZIndex by remember {
        derivedStateOf { if (playerContentExpansionFraction.value < 0.5f) 1f else 0f }
    }
    val fullPlayerZIndex by remember {
        derivedStateOf { if (playerContentExpansionFraction.value >= 0.5f) 1f else 0f }
    }

    // Mini Player Container
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(miniPlayerHeight)
            .graphicsLayer {
                // Cross-fades fully to 0.0 alpha at 50% expansion
                alpha = (1f - playerContentExpansionFraction.value * 2f).coerceIn(0f, 1f)
            }
            .zIndex(miniPlayerZIndex)
    ) {
        MiniPlayerContentInternal(
            viewModel = viewModel,
            currentSong = currentSong,
            isPlaying = isPlaying,
            accentColor = baseAccentColor,
            onClick = onExpand,
            modifier = Modifier.fillMaxSize()
        )
    }

    // Mount the expensive full-player tree only after the sheet is actually open.
    // Its saveable state is retained while collapsed, without keeping lyrics, visualizers,
    // and hidden layout work alive behind the mini player.
    if (shouldComposeFullPlayer) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(containerHeight)
                .graphicsLayer {
                    val fraction = playerContentExpansionFraction.value
                    alpha = ((fraction - 0.5f) * 2f).coerceIn(0f, 1f)
                    val scale = androidx.compose.ui.util.lerp(0.972f, 1f, fraction)
                    scaleX = scale
                    scaleY = scale
                    translationY = androidx.compose.ui.util.lerp(100f, 0f, fraction)
                }
                .zIndex(fullPlayerZIndex)
        ) {
            fullPlayerStateHolder.SaveableStateProvider("full-player") {
                FullPlayerScreen(
                    activity = activity,
                    viewModel = viewModel,
                    uiState = uiState,
                    songList = songList,
                    accentColor = baseAccentColor,
                    context = context,
                    onCollapse = onCollapse,
                    dragModifier = dragModifier,
                    isPlayerVisible = isFullPlayerVisible,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ==========================================
// 5. STEP 5: VERTICAL GESTURE HANDLER & MATH
// ==========================================
data class SheetVerticalDragFrame(
    val translationY: Float,
    val expansionFraction: Float
)

private data class SheetAnimationRequest(
    val targetExpanded: Boolean,
    val animationSpec: AnimationSpec<Float>?,
    val initialVelocity: Float
)

fun computeSheetVerticalDragFrame(
    accumulatedDragY: Float,
    expandedY: Float,
    collapsedY: Float,
    miniHeightPx: Float,
    initialFractionOnDragStart: Float,
    initialYOnDragStart: Float
): SheetVerticalDragFrame {
    // Clamped strictly to expandedY to prevent vertical rubber-band/upward drift when fully expanded
    val newY = (initialYOnDragStart + accumulatedDragY)
        .coerceIn(
            expandedY,
            collapsedY + miniHeightPx * 0.2f
        )
    val denominator = (collapsedY - expandedY).coerceAtLeast(1f)
    val dragRatio = (initialYOnDragStart - newY) / denominator
    val newFraction = (initialFractionOnDragStart + dragRatio).coerceIn(0f, 1f)
    return SheetVerticalDragFrame(translationY = newY, expansionFraction = newFraction)
}

fun resolveVerticalSheetTargetState(
    currentSheetContentState: PlayerSheetState,
    accumulatedDragY: Float,
    minDragThresholdPx: Float,
    verticalVelocity: Float,
    velocityThreshold: Float,
    currentFraction: Float
): PlayerSheetState {
    return when {
        currentSheetContentState == PlayerSheetState.EXPANDED && accumulatedDragY <= 0f -> 
            PlayerSheetState.EXPANDED

        abs(accumulatedDragY) > minDragThresholdPx ->
            if (accumulatedDragY < 0f) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED

        abs(verticalVelocity) > velocityThreshold ->
            if (verticalVelocity < 0f) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED

        else ->
            if (currentFraction > 0.5f) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
    }
}

fun collapseSpringDampingForFraction(currentFraction: Float): Float {
    return androidx.compose.ui.util.lerp(Spring.DampingRatioNoBouncy, Spring.DampingRatioLowBouncy, currentFraction)
}

fun collapseInitialSquashForFraction(currentFraction: Float): Float {
    return androidx.compose.ui.util.lerp(1.0f, 0.97f, currentFraction)
}

class SheetVerticalDragGestureHandler(
    private val scope: CoroutineScope,
    private val velocityTracker: VelocityTracker,
    private val densityProvider: () -> Density,
    private val playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    private val currentSheetTranslationY: Animatable<Float, AnimationVector1D>,
    private val expandedYProvider: () -> Float,
    private val collapsedYProvider: () -> Float,
    private val miniHeightPxProvider: () -> Float,
    private val currentSheetStateProvider: () -> PlayerSheetState,
    private val visualOvershootScaleY: Animatable<Float, AnimationVector1D>,
    private val onAnimateSheet: suspend (targetExpanded: Boolean, animationSpec: AnimationSpec<Float>?, initialVelocity: Float) -> Unit,
    private val onExpandSheetState: () -> Unit,
    private val onCollapseSheetState: () -> Unit,
    private val onDragStateChange: (Boolean) -> Unit
) {
    private var initialFractionOnDragStart = 0f
    private var initialYOnDragStart = 0f
    private var accumulatedDragYSinceStart = 0f
    private var dragJob: Job? = null
    private var isActivelyDragging = false

    fun onDragStart() {
        isActivelyDragging = true
        onDragStateChange(true)
        dragJob?.cancel()
        velocityTracker.resetTracking()
        initialFractionOnDragStart = playerContentExpansionFraction.value
        initialYOnDragStart = currentSheetTranslationY.value
        accumulatedDragYSinceStart = 0f
    }

    fun onVerticalDrag(uptimeMillis: Long, position: Offset, dragAmount: Float) {
        if (currentSheetStateProvider() == PlayerSheetState.EXPANDED && dragAmount < 0) {
            // Dragging UP when fully expanded: Ignore vertical sheet movement completely
            return
        }
        accumulatedDragYSinceStart += dragAmount
        val dragFrame = computeSheetVerticalDragFrame(
            accumulatedDragY = accumulatedDragYSinceStart,
            expandedY = expandedYProvider(),
            collapsedY = collapsedYProvider(),
            miniHeightPx = miniHeightPxProvider(),
            initialFractionOnDragStart = initialFractionOnDragStart,
            initialYOnDragStart = initialYOnDragStart
        )
        dragJob?.cancel()
        dragJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            currentSheetTranslationY.snapTo(dragFrame.translationY)
            playerContentExpansionFraction.snapTo(dragFrame.expansionFraction)
        }
        velocityTracker.addPosition(uptimeMillis, position)
    }

    fun onDragEnd() {
        if (!isActivelyDragging) return
        isActivelyDragging = false
        onDragStateChange(false)
        dragJob?.cancel()
        val verticalVelocity = velocityTracker.calculateVelocity().y
        val currentFraction = playerContentExpansionFraction.value
        val minDragThresholdPx = with(densityProvider()) { 5.dp.toPx() }
        
        val targetState = resolveVerticalSheetTargetState(
            currentSheetContentState = currentSheetStateProvider(),
            accumulatedDragY = accumulatedDragYSinceStart,
            minDragThresholdPx = minDragThresholdPx,
            verticalVelocity = verticalVelocity,
            velocityThreshold = 150f,
            currentFraction = currentFraction
        )

        scope.launch {
            if (targetState == PlayerSheetState.EXPANDED) {
                // Request the transition before changing the state. The composable owns the
                // single animation job so the gesture and state observer cannot race each other.
                onAnimateSheet(true, null, 0f)
                onExpandSheetState()
            } else {
                val dynamicDamping = collapseSpringDampingForFraction(currentFraction)

                // Gelatinous Squash & Stretch Bounce Trigger
                launch {
                    val initialSquash = collapseInitialSquashForFraction(currentFraction)
                    visualOvershootScaleY.snapTo(initialSquash)
                    visualOvershootScaleY.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessVeryLow
                        )
                    )
                }

                onAnimateSheet(
                    false,
                    spring(dampingRatio = dynamicDamping, stiffness = Spring.StiffnessLow),
                    verticalVelocity
                )
                onCollapseSheetState()
            }
        }
    }
}

fun Modifier.playerSheetVerticalDragGesture(
    enabled: Boolean,
    handler: SheetVerticalDragGestureHandler
): Modifier {
    if (!enabled) return this
    return this.pointerInput(true, handler) {
        detectVerticalDragGestures(
            onDragStart = { handler.onDragStart() },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                handler.onVerticalDrag(change.uptimeMillis, change.position, dragAmount)
            },
            onDragEnd = { handler.onDragEnd() },
            onDragCancel = { handler.onDragEnd() }
        )
    }
}

// ==========================================
// 6. STEP 6: HORIZONTAL SWIPE-TO-DISMISS TENSION
// ==========================================
class MiniPlayerDismissGestureHandler(
    private val scope: CoroutineScope,
    private val density: Density,
    private val offsetAnimatable: Animatable<Float, AnimationVector1D>,
    private val screenWidthPx: Float,
    private val onDismissQueue: () -> Unit
) {
    private var dragPhase = MiniDismissDragPhase.IDLE
    private var accumulatedDragX = 0f
    private var offsetJob: Job? = null

    fun onDragStart() {
        dragPhase = MiniDismissDragPhase.TENSION
        accumulatedDragX = 0f
        offsetJob?.cancel()
        offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            offsetAnimatable.stop()
        }
    }

    fun onHorizontalDrag(dragAmount: Float) {
        accumulatedDragX += dragAmount

        when (dragPhase) {
            MiniDismissDragPhase.TENSION -> {
                val snapThresholdPx = 100f * density.density
                
                // Pulls back with structural resistance under the 100dp threshold
                if (abs(accumulatedDragX) < snapThresholdPx) {
                    val maxTensionOffsetPx = 30f * density.density
                    val dragFraction = (abs(accumulatedDragX) / snapThresholdPx).coerceIn(0f, 1f)
                    val tensionOffset = androidx.compose.ui.util.lerp(0f, maxTensionOffsetPx, dragFraction)
                    offsetJob?.cancel()
                    offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        offsetAnimatable.snapTo(tensionOffset * accumulatedDragX.sign)
                    }
                } else {
                    dragPhase = MiniDismissDragPhase.SNAPPING
                }
            }

            MiniDismissDragPhase.SNAPPING -> {
                offsetJob?.cancel()
                offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    offsetAnimatable.animateTo(
                        targetValue = accumulatedDragX,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
                    )
                }
                dragPhase = MiniDismissDragPhase.FREE_DRAG
            }

            MiniDismissDragPhase.FREE_DRAG -> {
                offsetJob?.cancel()
                offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    offsetAnimatable.animateTo(
                        targetValue = accumulatedDragX,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
                    )
                }
            }
            else -> Unit
        }
    }

    fun onDragEnd() {
        dragPhase = MiniDismissDragPhase.IDLE
        offsetJob?.cancel()
        val dismissThreshold = screenWidthPx * 0.4f
        
        if (abs(accumulatedDragX) > dismissThreshold) {
            val targetDismissOffset = if (accumulatedDragX < 0) -screenWidthPx else screenWidthPx
            offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                offsetAnimatable.animateTo(
                    targetValue = targetDismissOffset,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                )
                onDismissQueue()
                offsetAnimatable.snapTo(0f)
            }
        } else {
            // Springs back dynamically to center
            offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                offsetAnimatable.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                )
            }
        }
    }
}

@Composable
fun rememberMiniPlayerDismissGestureHandler(
    scope: CoroutineScope,
    density: Density,
    offsetAnimatable: Animatable<Float, AnimationVector1D>,
    screenWidthPx: Float,
    onDismissQueue: () -> Unit
): MiniPlayerDismissGestureHandler {
    val onDismissQueueState = rememberUpdatedState(onDismissQueue)
    return remember(scope, density, offsetAnimatable, screenWidthPx) {
        MiniPlayerDismissGestureHandler(
            scope = scope,
            density = density,
            offsetAnimatable = offsetAnimatable,
            screenWidthPx = screenWidthPx,
            onDismissQueue = { onDismissQueueState.value() }
        )
    }
}

fun Modifier.miniPlayerDismissHorizontalGesture(
    enabled: Boolean,
    handler: MiniPlayerDismissGestureHandler
): Modifier {
    if (!enabled) return this
    return this.pointerInput(true, handler) {
        detectHorizontalDragGestures(
            onDragStart = { handler.onDragStart() },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                handler.onHorizontalDrag(dragAmount)
            },
            onDragEnd = { handler.onDragEnd() }
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.VideoOverlayControls(
    qualityLabel: String?,
    qualityEnabled: Boolean,
    onOpenQuality: () -> Unit,
    onToggleFullscreen: () -> Unit,
    fullscreen: Boolean
) {
    Row(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.62f)
        ) {
            IconButton(onClick = onOpenQuality, enabled = qualityEnabled) {
                Text(
                    text = "⚙",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.62f)
        ) {
            IconButton(onClick = onToggleFullscreen) {
                Text(
                    text = if (fullscreen) "⛶" else "⛶",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ==========================================
// 7. STEP 7: THE MASTER ASSEMBLY CONTAINER
// ==========================================
@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerBottomSheetCompose(
    activity: MainActivity,
    viewModel: PlayerSharedViewModel,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val configuration = LocalConfiguration.current
        val useNavigationRail = configuration.screenWidthDp >= 600
        val useWidePlayer = configuration.screenWidthDp >= 840 ||
            (useNavigationRail && configuration.screenWidthDp > configuration.screenHeightDp)
        val miniPlayerHeight = if (
            configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        ) 64.dp else MiniPlayerHeight
        val scope = rememberCoroutineScope()
        val playerPreferences = remember(context) {
            context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        }
        val playerBackgroundStyle by playerPreferences.observeKey("player_background_style", "Artwork")
        val playerLayout by playerPreferences.observeKey("player_layout", "Standard")
        val playerArtworkShapeName by playerPreferences.observeKey("player_artwork_shape", "Rounded")
        val playerArtworkShape = remember(playerLayout, playerArtworkShapeName) {
            DsmCoverShapes.forPlayerLayout(playerLayout, playerArtworkShapeName)
        }
        val miniPlayerStyle by playerPreferences.observeKey("mini_player_style", "Card")
        
        // Playback state observers combined into stable UI model
        val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
        val isVideoMode by viewModel.isVideoMode.collectAsStateWithLifecycle()
        val videoPlaybackMetadata by viewModel.videoPlaybackMetadata.collectAsStateWithLifecycle()
        val audioService by viewModel.audioServiceState.collectAsStateWithLifecycle()
        val gestureSettings by viewModel.playerGestureSettings.collectAsStateWithLifecycle()
        var videoQualityOptions by remember {
            mutableStateOf<List<com.dsann.music.data.innertube.VideoStreamOption>>(emptyList())
        }
        var selectedVideoQuality by remember { mutableStateOf<String?>(null) }
        var videoQualityLoading by remember { mutableStateOf(false) }
        var showVideoQualityDialog by remember { mutableStateOf(false) }
        var isVideoFullscreen by remember { mutableStateOf(false) }

        LaunchedEffect(isVideoMode, currentSong?.videoId) {
            val videoId = currentSong?.videoId
            if (!isVideoMode || videoId.isNullOrBlank()) {
                videoQualityOptions = emptyList()
                selectedVideoQuality = null
                showVideoQualityDialog = false
                isVideoFullscreen = false
            } else {
                videoQualityLoading = true
                try {
                    val options = com.dsann.music.data.innertube.NewPipeStreamExtractor
                        .getVideoStreamOptions(videoId)
                    videoQualityOptions = options
                    val defaultVideoOption = com.dsann.music.data.innertube.NewPipeStreamExtractor
                        .dataSaverVideoOption(options)
                    selectedVideoQuality = defaultVideoOption?.label
                    if (isVideoMode && defaultVideoOption != null) {
                        viewModel.setVideoPlaybackMetadata(
                            com.dsann.music.viewmodel.VideoPlaybackMetadata(
                                title = defaultVideoOption.videoTitle?.takeIf { it.isNotBlank() }
                                    ?: currentSong?.title.orEmpty(),
                                uploader = defaultVideoOption.videoUploader?.takeIf { it.isNotBlank() }
                                    ?: currentSong?.artist.orEmpty(),
                                durationMs = defaultVideoOption.durationMs,
                                resolution = defaultVideoOption.label,
                                mimeType = defaultVideoOption.mimeType,
                                description = defaultVideoOption.videoDescription,
                                viewCount = defaultVideoOption.videoViewCount,
                                likeCount = defaultVideoOption.videoLikeCount,
                                uploadDate = defaultVideoOption.videoUploadDate,
                                thumbnailUrl = defaultVideoOption.videoThumbnailUrl,
                                uploaderUrl = defaultVideoOption.videoUploaderUrl
                            )
                        )
                    }
                } catch (_: Exception) {
                    videoQualityOptions = emptyList()
                } finally {
                    videoQualityLoading = false
                }
            }
        }

        val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        val duration by viewModel.duration.collectAsStateWithLifecycle()
        val currentSongIndex by viewModel.currentSongIndex.collectAsStateWithLifecycle()
        val miniPlayerBlendColor by viewModel.miniPlayerBlendColor.collectAsStateWithLifecycle()
        val isCurrentSongFavorite by viewModel.isCurrentSongFavoriteFlow.collectAsStateWithLifecycle()
        val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
        val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsStateWithLifecycle()
        val playerBackgroundColor by viewModel.playerBackgroundColor.collectAsStateWithLifecycle()
        val likesCount by viewModel.likesCountFlow.collectAsStateWithLifecycle()
        val isDark = isSystemInDarkTheme()
        val themePlayerSurface = MaterialTheme.colorScheme.surface
        val dynamicBgColor = remember(playerBackgroundColor, isDark, playerBackgroundStyle, themePlayerSurface) {
            when (playerBackgroundStyle) {
                "Theme" -> themePlayerSurface
                "Black" -> Color.Black
                "Gradient" -> {
                    val base = Color(playerBackgroundColor)
                    if (isDark) androidx.compose.ui.graphics.lerp(base, Color.Black, 0.26f)
                    else androidx.compose.ui.graphics.lerp(base, themePlayerSurface, 0.28f)
                }
                else -> {
                    val baseColor = Color(playerBackgroundColor)
                    val hsl = FloatArray(3)
                    androidx.core.graphics.ColorUtils.colorToHSL(baseColor.toArgb(), hsl)
                    val isMonochrome = hsl[1] < 0.06f
                    if (isDark) {
                        hsl[2] = 0.155f
                        hsl[1] = if (isMonochrome) 0f else hsl[1].coerceIn(0.32f, 0.54f)
                        Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
                    } else {
                        hsl[2] = 0.835f
                        hsl[1] = if (isMonochrome) 0f else hsl[1].coerceIn(0.30f, 0.48f)
                        Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
                    }
                }
            }
        }
        val isCurrentSongDisliked by viewModel.isCurrentSongDisliked.collectAsStateWithLifecycle()
        val isCurrentSongDownloaded by viewModel.isCurrentSongDownloaded.collectAsStateWithLifecycle()
        val currentSongDownloadProgress by viewModel.currentSongDownloadProgress.collectAsStateWithLifecycle()
        val isLyricsModeEnabled by viewModel.isLyricsModeEnabled.collectAsStateWithLifecycle()
        val showSignInDialog by viewModel.showSignInDialog.collectAsStateWithLifecycle()
        if (showSignInDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissSignInDialog() },
                title = {
                    Text(
                        text = "Sign in Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "To like or dislike songs on YouTube Music, please connect your account in Settings. This enables full synchronization of liked tracks and personalization algorithms.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.dismissSignInDialog()
                            activity.navigateToSettings()
                        }
                    ) {
                        Text(text = "Go to Settings", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissSignInDialog() }) {
                        Text(text = "Cancel")
                    }
                }
            )
        }
        val lyricsArtworkProgress by animateFloatAsState(
            targetValue = if (isLyricsModeEnabled) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
            label = "LyricsArtworkSharedElement"
        )
 
        // While video mode is active, show the extracted video's own title/uploader rather
        // than leaving the player header stuck on the audio-track metadata.
        val displaySong = remember(currentSong, isVideoMode, videoPlaybackMetadata) {
            if (isVideoMode && videoPlaybackMetadata != null && currentSong != null) {
                currentSong!!.copy(
                    title = videoPlaybackMetadata!!.title,
                    artist = videoPlaybackMetadata!!.uploader
                )
            } else currentSong
        }

        val uiState = remember(
            displaySong, isPlaying, isLoading, duration, currentSongIndex,
            miniPlayerBlendColor, isCurrentSongFavorite, isCurrentSongDisliked, repeatMode, isShuffleEnabled,
            playerBackgroundColor, likesCount, isCurrentSongDownloaded, currentSongDownloadProgress
        ) {
            PlayerUiState(
                currentSong = displaySong,
                isPlaying = isPlaying,
                isLoading = isLoading,
                duration = duration,
                currentSongIndex = currentSongIndex,
                miniPlayerBlendColor = miniPlayerBlendColor,
                isCurrentSongFavorite = isCurrentSongFavorite,
                isCurrentSongDisliked = isCurrentSongDisliked,
                repeatMode = repeatMode,
                isShuffleEnabled = isShuffleEnabled,
                playerBackgroundColor = playerBackgroundColor,
                likesCount = likesCount,
                isCurrentSongDownloaded = isCurrentSongDownloaded,
                currentSongDownloadProgress = currentSongDownloadProgress
            )
        }

        // List is separate from quick state changes
        val songList by viewModel.songList.collectAsStateWithLifecycle()
        val isQueueExpanded by viewModel.isQueueExpanded.collectAsStateWithLifecycle()
        val artworkAlpha by animateFloatAsState(
            targetValue = if (isQueueExpanded) 0f else 1f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f),
            label = "ArtworkAlpha"
        )

        // Sliding Layout States
        val containerHeight = with(density) { maxHeight.toPx().toDp() }
        val containerWidthDp = maxWidth
        val screenHeightPx = with(density) { containerHeight.toPx() }
        val systemNavigationBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
        // Use the actual nav visibility boolean rather than publishing every animation frame
        // through the shared player ViewModel. It changes only when the user hides/shows the bar.
        val bottomNavVisible = activity.isBottomNavVisible
        val dynamicBottomNavHeightState = viewModel.bottomNavHeight.collectAsStateWithLifecycle()
        
        // Zero-Recomposition logic: calculating layout targets inside derivedStateOf
        // This ensures the composable body doesn't re-run for every pixel of nav bar scroll
        val sheetCollapsedTargetYState = remember(
            screenHeightPx, density, useNavigationRail, miniPlayerHeight, bottomNavVisible
        ) {
            derivedStateOf {
                val bottomNavHeightPx = if (useNavigationRail) {
                    0f
                } else if (dynamicBottomNavHeightState.value > 0f) {
                    dynamicBottomNavHeightState.value
                } else {
                    with(density) { 75.dp.toPx() }
                }
                val miniPlayerHeightPx = with(density) { miniPlayerHeight.toPx() }
                
                // Dynamically adjust gap: 12dp when BottomNav is visible, 8dp when hidden
                val bottomNavVisibilityFraction = if (useNavigationRail || !bottomNavVisible) 1f else 0f
                val currentBottomGapPx = with(density) {
                    if (useNavigationRail) 8.dp.toPx()
                    else lerp(12.dp, 8.dp, bottomNavVisibilityFraction).toPx()
                }
                
                // Ensure the mini player stops at the bottom margin and doesn't follow the nav bar into the abyss
                val effectiveBottomNavHeight = if (useNavigationRail || !bottomNavVisible) {
                    0f
                } else {
                    bottomNavHeightPx
                }
                screenHeightPx - miniPlayerHeightPx - effectiveBottomNavHeight - currentBottomGapPx -
                    (if (useNavigationRail) systemNavigationBottomPx else 0f)
            }
        }

        // Restore the expanded player from the Activity-scoped ViewModel after rotation or
        // a window-size change. The media service keeps playing; the sheet must keep its state.
        val restoredPlayerExpanded = viewModel.isPlayerExpanded.value ||
            viewModel.playerExpansionFraction.value >= 0.5f
        val restoredExpansionFraction = if (restoredPlayerExpanded) {
            1f
        } else {
            viewModel.playerExpansionFraction.value.coerceIn(0f, 1f)
        }

        // Animation States
        val playerContentExpansionFraction = remember(viewModel) {
            Animatable(restoredExpansionFraction)
        }

        // Observe the animation through a threshold, not as a raw Float in composition.
        // The old direct reads recomposed the player subtree on every animation frame.
        val isPlayerFullyExpanded by remember(playerContentExpansionFraction) {
            derivedStateOf { playerContentExpansionFraction.value > 0.95f }
        }
        val isPlaybackReady by viewModel.isPlaybackReady.collectAsStateWithLifecycle()

        var currentSheetContentState by remember(viewModel) {
            mutableStateOf(
                if (restoredPlayerExpanded) PlayerSheetState.EXPANDED
                else PlayerSheetState.COLLAPSED
            )
        }

        fun performPlayerGesture(action: PlayerGestureAction, touchedLeftSide: Boolean = false) {
            when (action) {
                PlayerGestureAction.NEXT -> viewModel.playNextSong()
                PlayerGestureAction.PREVIOUS -> viewModel.playPreviousSong()
                PlayerGestureAction.SEEK_BACK -> viewModel.seekTo((viewModel.currentPosition.value - 10_000).coerceAtLeast(0))
                PlayerGestureAction.SEEK_AHEAD -> viewModel.seekTo(viewModel.currentPosition.value + 10_000)
                PlayerGestureAction.SEEK_SIDES -> {
                    val delta = gestureSettings.doubleTapSeekSeconds * 1000
                    viewModel.seekTo(if (touchedLeftSide) {
                        (viewModel.currentPosition.value - delta).coerceAtLeast(0)
                    } else {
                        viewModel.currentPosition.value + delta
                    })
                }
                PlayerGestureAction.PLAY_PAUSE -> {
                    if (isPlaying) viewModel.pauseAudio() else viewModel.playAudio()
                }
                PlayerGestureAction.LIKE -> viewModel.toggleFavorite()
                PlayerGestureAction.QUEUE -> viewModel.setQueueExpanded(true)
                PlayerGestureAction.LYRICS -> viewModel.setLyricsModeEnabled(true)
                PlayerGestureAction.VIDEO -> audioService?.setVideoMode(!isVideoMode)
                PlayerGestureAction.STOP -> viewModel.dismissPlayer()
                PlayerGestureAction.OPEN -> currentSheetContentState = PlayerSheetState.EXPANDED
                PlayerGestureAction.NONE -> Unit
            }
        }

        fun handleCoverTap() {
            when {
                isLyricsModeEnabled -> viewModel.setLyricsModeEnabled(false)
                currentSheetContentState != PlayerSheetState.EXPANDED ||
                    playerContentExpansionFraction.value < 0.95f -> {
                    currentSheetContentState = PlayerSheetState.EXPANDED
                }
                playerContentExpansionFraction.value >= 0.98f && !isQueueExpanded -> {
                    viewModel.setLyricsModeEnabled(true)
                }
            }
        }

        fun handleMiniSwipeDown() {
            if (gestureSettings.miniSwipeDown == PlayerGestureAction.STOP) {
                viewModel.dismissPlayer()
            } else {
                performPlayerGesture(gestureSettings.miniSwipeDown)
            }
        }

        // Keep one animation owner for gesture, tap and Back transitions. The old drag handler
        // and the state observer both animated the same Animatables, sometimes cancelling a
        // collapse before the mini player reached its bottom position.
        var pendingSheetAnimation by remember { mutableStateOf<SheetAnimationRequest?>(null) }
        var sheetAnimationRequestId by remember { mutableStateOf(0) }

        val currentSheetTranslationY = remember(viewModel) {
            Animatable(sheetCollapsedTargetYState.value * (1f - restoredExpansionFraction))
        }

        // A rotation changes the sheet's coordinate space. Recalculate its Y position using
        // the new height while retaining the previously saved expansion fraction.
        LaunchedEffect(screenHeightPx, containerWidthDp, useNavigationRail) {
            val fraction = if (viewModel.isPlayerExpanded.value) {
                1f
            } else {
                viewModel.playerExpansionFraction.value.coerceIn(0f, 1f)
            }
            playerContentExpansionFraction.snapTo(fraction)
            currentSheetTranslationY.snapTo(sheetCollapsedTargetYState.value * (1f - fraction))
        }
        val visualOvershootScaleY = remember { Animatable(1f) }
        val offsetAnimatable = remember { Animatable(0f) }

        val screenWidthPx = with(density) { maxWidth.toPx() }
        
        var isDragging by remember { mutableStateOf(false) }

        val showPlayerContentArea = currentSong != null

        LaunchedEffect(currentSheetContentState) {
            viewModel.setPlayerExpanded(currentSheetContentState == PlayerSheetState.EXPANDED)
        }

        // Synchronize dynamic scrolling Bottom Nav visibility changes
        LaunchedEffect(Unit) {
            snapshotFlow { sheetCollapsedTargetYState.value }.collect { targetY ->
                if (currentSheetContentState == PlayerSheetState.COLLAPSED &&
                    pendingSheetAnimation == null &&
                    !isDragging &&
                    !currentSheetTranslationY.isRunning &&
                    currentSheetTranslationY.value != screenHeightPx
                ) {
                    currentSheetTranslationY.snapTo(targetY)
                }
            }
        }

        val sheetBackProgress = 0f

        androidx.activity.compose.BackHandler(
            enabled = currentSheetContentState == PlayerSheetState.EXPANDED &&
                !isLyricsModeEnabled && !isQueueExpanded
        ) {
            collapsePlayerFully()
        }

        // Auto-expand/collapse sheet when currentSong is changed / ready
        LaunchedEffect(currentSong, isPlaybackReady) {
            if (currentSong != null && isPlaybackReady) {
                if (currentSheetTranslationY.value == screenHeightPx) {
                    currentSheetTranslationY.animateTo(
                        targetValue = sheetCollapsedTargetYState.value,
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                    )
                    currentSheetContentState = PlayerSheetState.COLLAPSED
                }
            }
        }

        // Dynamic Blend Colors from Artwork
        val baseAccentColor = remember(miniPlayerBlendColor, isDark) {
            val rawAccent = if (miniPlayerBlendColor != 0) Color(miniPlayerBlendColor) else Color(0xFF8338EC)
            val hsl = FloatArray(3)
            androidx.core.graphics.ColorUtils.colorToHSL(rawAccent.toArgb(), hsl)
            if (hsl[1] < 0.05f) {
                // Monochromatic / Grayscale
                if (isDark) Color.White else Color(0xFF1C1B1F)
            } else {
                rawAccent
            }
        }

        // Resolves sheet animations
        suspend fun animatePlayerSheet(
            targetExpanded: Boolean,
            animationSpec: AnimationSpec<Float>? = null,
            initialVelocity: Float = 0f
        ) {
            val expectedBottomNavHeight = if (useNavigationRail || !bottomNavVisible) {
                0f
            } else if (dynamicBottomNavHeightState.value > 0f) {
                dynamicBottomNavHeightState.value
            } else {
                with(density) { 75.dp.toPx() }
            }
            val expectedBottomGapPx = with(density) {
                (if (useNavigationRail || !bottomNavVisible) 8.dp else 12.dp).toPx()
            }
            val miniPlayerHeightPx = with(density) { miniPlayerHeight.toPx() }
            val destY = if (targetExpanded) {
                0f
            } else {
                screenHeightPx - miniPlayerHeightPx - expectedBottomNavHeight - expectedBottomGapPx -
                    (if (useNavigationRail) systemNavigationBottomPx else 0f)
            }
            val destFraction = if (targetExpanded) 1f else 0f

            val collapsedY = screenHeightPx - miniPlayerHeightPx - expectedBottomNavHeight - expectedBottomGapPx -
                (if (useNavigationRail) systemNavigationBottomPx else 0f)
            val totalDistance = collapsedY
            val initialFractionVelocity = if (totalDistance > 0f) {
                -initialVelocity / totalDistance
            } else 0f
            val resolvedAnimationSpec = animationSpec
                ?: spring(dampingRatio = 0.85f, stiffness = 400f)

            coroutineScope {
                launch {
                    currentSheetTranslationY.animateTo(
                        targetValue = destY,
                        animationSpec = resolvedAnimationSpec,
                        initialVelocity = initialVelocity
                    )
                }
                launch {
                    playerContentExpansionFraction.animateTo(
                        targetValue = destFraction,
                        animationSpec = resolvedAnimationSpec,
                        initialVelocity = initialFractionVelocity
                    )
                }
            }
        }

        // Route every close gesture/button through the same animation owner. Incrementing
        // the request id also handles a stale "collapsed" state after a partial gesture,
        // while clearing nested modes prevents Lyrics/Queue from keeping the full tree mounted.
        fun collapsePlayerFully() {
            viewModel.setLyricsModeEnabled(false)
            viewModel.setQueueExpanded(false)
            pendingSheetAnimation = SheetAnimationRequest(
                targetExpanded = false,
                animationSpec = spring(
                    dampingRatio = 0.92f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialVelocity = 0f
            )
            sheetAnimationRequestId += 1
            currentSheetContentState = PlayerSheetState.COLLAPSED
        }

        // This is the only place that animates the sheet. Taps and Back use the default spring;
        // drag gestures can supply their velocity/damping without starting a competing animation.
        LaunchedEffect(currentSheetContentState, sheetAnimationRequestId) {
            val targetExpanded = currentSheetContentState == PlayerSheetState.EXPANDED
            val request = pendingSheetAnimation
                ?.takeIf { it.targetExpanded == targetExpanded }
            pendingSheetAnimation = null

            if (request != null) {
                animatePlayerSheet(
                    targetExpanded = request.targetExpanded,
                    animationSpec = request.animationSpec,
                    initialVelocity = request.initialVelocity
                )
            } else {
                animatePlayerSheet(targetExpanded = targetExpanded)
            }
        }

        LaunchedEffect(playerContentExpansionFraction) {
            snapshotFlow { playerContentExpansionFraction.value }
                .collect { viewModel.setPlayerExpansionFraction(it) }
        }

        val sheetVisualState = rememberSheetVisualState(
            showPlayerContentArea = showPlayerContentArea,
            collapsedStateHorizontalPadding = 12.dp,
            predictiveBackCollapseProgress = sheetBackProgress,
            playerContentExpansionFraction = playerContentExpansionFraction,
            containerHeight = containerHeight,
            currentSheetTranslationY = currentSheetTranslationY,
            sheetCollapsedTargetYProvider = { sheetCollapsedTargetYState.value },
            isNavBarHiddenProvider = {
                if (useNavigationRail) playerContentExpansionFraction.value > 0.5f
                else !bottomNavVisible
            },
            navBarCornerRadiusDp = 32.dp,
            miniPlayerHeight = miniPlayerHeight
        )

        // Dynamic Shape instantiation
        val playerShadowShape = remember(sheetVisualState.overallSheetTopCornerRadiusProvider, sheetVisualState.playerContentActualBottomRadiusProvider, sheetVisualState.playerContentAreaHeightPxProvider) {
            PlayerSheetDynamicShape(
                topRadiusProvider = sheetVisualState.overallSheetTopCornerRadiusProvider,
                bottomRadiusProvider = sheetVisualState.playerContentActualBottomRadiusProvider,
                virtualHeightProvider = sheetVisualState.playerContentAreaHeightPxProvider
            )
        }
        val playerShellShape: Shape = if (currentSheetContentState == PlayerSheetState.COLLAPSED) {
            when (miniPlayerStyle) {
                "Flat" -> RoundedCornerShape(0.dp)
                "Compact" -> RoundedCornerShape(18.dp)
                "Pill" -> CircleShape
                // Card is a distinct floating rounded rectangle, not the dynamic capsule.
                else -> RoundedCornerShape(22.dp)
            }
        } else playerShadowShape
        val playerShellElevation = if (
            currentSheetContentState == PlayerSheetState.COLLAPSED &&
            miniPlayerStyle != "Card"
        ) 0.dp else if (currentSheetContentState == PlayerSheetState.COLLAPSED) 6.dp else 0.dp

        val velocityTracker = remember { VelocityTracker() }
        
        val sheetVerticalDragGestureHandler = remember(density) {
            val miniHeightPx = with(density) { miniPlayerHeight.toPx() }
            SheetVerticalDragGestureHandler(
                scope = scope,
                velocityTracker = velocityTracker,
                densityProvider = { density },
                playerContentExpansionFraction = playerContentExpansionFraction,
                currentSheetTranslationY = currentSheetTranslationY,
                expandedYProvider = { 0f },
                collapsedYProvider = { sheetCollapsedTargetYState.value },
                miniHeightPxProvider = { miniHeightPx },
                currentSheetStateProvider = { currentSheetContentState },
                visualOvershootScaleY = visualOvershootScaleY,
                onAnimateSheet = { target, animationSpec, velocity ->
                    pendingSheetAnimation = SheetAnimationRequest(target, animationSpec, velocity)
                    sheetAnimationRequestId += 1
                },
                onExpandSheetState = { currentSheetContentState = PlayerSheetState.EXPANDED },
                onCollapseSheetState = { currentSheetContentState = PlayerSheetState.COLLAPSED },
                onDragStateChange = { dragging -> isDragging = dragging }
            )
        }

        val miniDismissGestureHandler = rememberMiniPlayerDismissGestureHandler(
            scope = scope,
            density = density,
            offsetAnimatable = offsetAnimatable,
            screenWidthPx = screenWidthPx,
            onDismissQueue = {
                scope.launch {
                    currentSheetTranslationY.snapTo(screenHeightPx)
                }
                viewModel.dismissPlayer()
            }
        )

        if (showPlayerContentArea) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, sheetVisualState.visualSheetTranslationYProvider().roundToInt()) }
                    .height(containerHeight),
                color = Color.Transparent
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = sheetVisualState.currentBottomPadding.coerceAtLeast(0.dp))) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (isQueueExpanded) 5f else 2f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                // Phase-Optimized Layout measuring (Zero-Recomposition)
                                .layout { measurable, constraints ->
                                    val targetHeightPx = sheetVisualState.playerContentAreaHeightPxProvider().toInt()
                                    val startPaddingPx = sheetVisualState.currentHorizontalPaddingStartPxProvider().toInt()
                                    val endPaddingPx = sheetVisualState.currentHorizontalPaddingEndPxProvider().toInt()
                                    val innerWidth = (constraints.maxWidth - startPaddingPx - endPaddingPx)
                                    
                                    val placeable = measurable.measure(
                                        constraints.copy(
                                            minWidth = innerWidth,
                                            maxWidth = innerWidth,
                                            minHeight = targetHeightPx,
                                            maxHeight = targetHeightPx
                                        )
                                    )
                                    layout(constraints.maxWidth, targetHeightPx) {
                                        placeable.placeRelative(startPaddingPx, 0)
                                    }
                                }
                                .then(
                                    if (currentSheetContentState == PlayerSheetState.COLLAPSED) {
                                        Modifier.pointerInput(gestureSettings, isLyricsModeEnabled, isQueueExpanded) {
                                            var dragX = 0f
                                            var dragY = 0f
                                            detectDragGestures(
                                                onDragStart = {
                                                    dragX = 0f
                                                    dragY = 0f
                                                },
                                                onDrag = { change, amount ->
                                                    dragX += amount.x
                                                    dragY += amount.y
                                                    change.consume()
                                                },
                                                onDragEnd = {
                                                    if (isLyricsModeEnabled || isQueueExpanded) return@detectDragGestures
                                                    if (kotlin.math.max(kotlin.math.abs(dragX), kotlin.math.abs(dragY)) < 24.dp.toPx()) return@detectDragGestures
                                                    when {
                                                        kotlin.math.abs(dragX) >= kotlin.math.abs(dragY) && dragX < 0 ->
                                                            performPlayerGesture(gestureSettings.miniSwipeLeft)
                                                        kotlin.math.abs(dragX) >= kotlin.math.abs(dragY) && dragX > 0 ->
                                                            performPlayerGesture(gestureSettings.miniSwipeRight)
                                                        dragY < 0 ->
                                                            performPlayerGesture(gestureSettings.miniSwipeUp)
                                                        else -> handleMiniSwipeDown()
                                                    }
                                                }
                                            )
                                        }
                                    } else Modifier
                                )
                                .graphicsLayer {
                                    translationX = offsetAnimatable.value
                                    scaleY = visualOvershootScaleY.value
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                }
                                .shadow(
                                    elevation = playerShellElevation,
                                    shape = playerShellShape
                                )
                                .background(
                                    color = dynamicBgColor,
                                    shape = playerShellShape
                                )
                                .playerSheetVerticalDragGesture(enabled = !isLyricsModeEnabled && !isQueueExpanded, handler = sheetVerticalDragGestureHandler)
                                .pointerInput(currentSheetContentState, isLyricsModeEnabled) {
                                    detectTapGestures(
                                        onTap = {
                                            if (!isLyricsModeEnabled) {
                                                currentSheetContentState = if (currentSheetContentState == PlayerSheetState.COLLAPSED) {
                                                    PlayerSheetState.EXPANDED
                                                } else {
                                                    PlayerSheetState.COLLAPSED
                                                }
                                            }
                                        }
                                    )
                                }
                        ) {
                            val verticalDragModifier = Modifier.playerSheetVerticalDragGesture(
                                enabled = currentSheetContentState == PlayerSheetState.EXPANDED && !isLyricsModeEnabled && !isQueueExpanded,
                                handler = sheetVerticalDragGestureHandler
                            )

                            UnifiedPlayerMiniAndFullLayers(
                                activity = activity,
                                viewModel = viewModel,
                                uiState = uiState,
                                songList = songList,
                                baseAccentColor = baseAccentColor,
                                context = context,
                                playerContentExpansionFraction = playerContentExpansionFraction,
                                containerHeight = containerHeight,
                                onCollapse = { collapsePlayerFully() },
                                onExpand = { currentSheetContentState = PlayerSheetState.EXPANDED },
                                dragModifier = verticalDragModifier,
                                miniPlayerHeight = miniPlayerHeight
                            )
                        }
                    }

                    // Integrated shared artwork layer. In lyrics mode the same ArtworkPager
                    // moves into the app bar as a compact thumbnail instead of being hidden.
                    val screenWidth = configuration.screenWidthDp
                    val albumArtScale = when (playerLayout) {
                        "Cover" -> 0.98f
                        "Minimal" -> 0.68f
                        "Vinyl", "Cassette" -> 0.82f
                        else -> 0.9f
                    }
                    val albumArtSizeDp = if (
                        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    ) {
                        minOf(containerHeight * 0.28f, screenWidth.dp * 0.30f) * (albumArtScale / 0.9f)
                    } else if (useWidePlayer) {
                        minOf(containerHeight * 0.50f, screenWidth.dp * 0.42f) * (albumArtScale / 0.9f)
                    } else {
                        (screenWidth * albumArtScale).dp
                    }
                    val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()

                    Box(
                        modifier = Modifier
                            .size(albumArtSizeDp)
                            .graphicsLayer {
                                val t = playerContentExpansionFraction.value
                                val lyricsT = lyricsArtworkProgress

                                val miniSizePx = 48.dp.toPx()
                                val fullSizePx = albumArtSizeDp.toPx()
                                val normalScale = androidx.compose.ui.util.lerp(miniSizePx / fullSizePx, 1f, t)

                                // Account for the horizontal padding applied in the parent layout block
                                val startPaddingPx = sheetVisualState.currentHorizontalPaddingStartPxProvider()

                                // Position relative to the Surface top-left
                                val xStartPx = startPaddingPx +
                                    (if (configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                                        10.dp
                                    } else {
                                        16.dp
                                    }).toPx()
                                val xEndPx = if (useWidePlayer) {
                                    with(density) {
                                        (20.dp + (containerWidthDp - 60.dp) * 0.54f / 2f - albumArtSizeDp / 2f).toPx()
                                    }
                                } else {
                                    (with(density) { screenWidth.dp.toPx() } - fullSizePx) / 2f
                                }

                                val miniPlayerArtworkStartInset = with(density) {
                                    ((miniPlayerHeight - 48.dp) / 2f).toPx()
                                }
                                val yStartPx = miniPlayerArtworkStartInset
                                val minTopOffsetDp = with(density) { statusBarTopPx.toDp() } +
                                    if (configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) 52.dp else 68.dp
                                val topOffsetDp = if (useWidePlayer) {
                                    minTopOffsetDp
                                } else {
                                    ((containerHeight - albumArtSizeDp) / 2f - 220.dp).coerceAtLeast(minTopOffsetDp)
                                }
                                val yEndPx = with(density) { topOffsetDp.toPx() }

                                val normalX = androidx.compose.ui.util.lerp(xStartPx, xEndPx, t)
                                val normalY = androidx.compose.ui.util.lerp(yStartPx, yEndPx, t)
                                val normalShadow = androidx.compose.ui.util.lerp(0f, 16.dp.toPx(), t)

                                val appBarThumbSizePx = 44.dp.toPx()
                                val appBarScale = appBarThumbSizePx / fullSizePx
                                val appBarX = 22.dp.toPx()
                                val appBarY = statusBarTopPx + 18.dp.toPx()

                                val dismissOffsetPx = offsetAnimatable.value * (1f - t)

                                scaleX = androidx.compose.ui.util.lerp(normalScale, appBarScale, lyricsT)
                                scaleY = androidx.compose.ui.util.lerp(normalScale, appBarScale, lyricsT)
                                translationX = androidx.compose.ui.util.lerp(normalX, appBarX, lyricsT) + dismissOffsetPx
                                translationY = androidx.compose.ui.util.lerp(normalY, appBarY, lyricsT)
                                transformOrigin = TransformOrigin(0f, 0f)

                                // Keep the selected artwork outline on the shared cover;
                                // a second forced rounded clip made custom shapes disagree
                                // with the visible mini-player progress.
                                shape = if (isVideoMode) androidx.compose.ui.graphics.RectangleShape else playerArtworkShape
                                clip = true
                                shadowElevation = androidx.compose.ui.util.lerp(normalShadow, 6.dp.toPx(), lyricsT)
                                alpha = artworkAlpha
                            }
                            // Put the click target inside the transformed layer. With clickable
                            // outside graphicsLayer, taps on the old 90%-screen layout bounds could
                            // open lyrics even when the visible cover was elsewhere/smaller.
                            .pointerInput(
                                gestureSettings,
                                currentSheetContentState,
                                isLyricsModeEnabled,
                                isQueueExpanded,
                                isVideoMode
                            ) {
                                detectTapGestures(
                                    onTap = {
                                        if (!isVideoMode || playerContentExpansionFraction.value <= 0.95f) {
                                            handleCoverTap()
                                        }
                                    },
                                    onDoubleTap = { position ->
                                        if (currentSheetContentState == PlayerSheetState.EXPANDED &&
                                            playerContentExpansionFraction.value >= 0.95f &&
                                            !isLyricsModeEnabled && !isQueueExpanded
                                        ) {
                                            performPlayerGesture(
                                                gestureSettings.coverDoubleTap,
                                                touchedLeftSide = position.x < size.width / 2f
                                            )
                                        }
                                    },
                                    onLongPress = {
                                        if (currentSheetContentState == PlayerSheetState.EXPANDED &&
                                            playerContentExpansionFraction.value >= 0.95f
                                        ) {
                                            performPlayerGesture(gestureSettings.coverLongPress)
                                        }
                                    }
                                )
                            }
                            .pointerInput(
                                gestureSettings,
                                currentSheetContentState,
                                isLyricsModeEnabled,
                                isQueueExpanded,
                                isVideoMode
                            ) {
                                var dragX = 0f
                                var dragY = 0f
                                detectDragGestures(
                                    onDragStart = {
                                        dragX = 0f
                                        dragY = 0f
                                    },
                                    onDrag = { change, amount ->
                                        dragX += amount.x
                                        dragY += amount.y
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        if (currentSheetContentState != PlayerSheetState.EXPANDED ||
                                            playerContentExpansionFraction.value < 0.95f ||
                                            isLyricsModeEnabled || isQueueExpanded || isVideoMode
                                        ) return@detectDragGestures

                                        val threshold = 34.dp.toPx()
                                        if (kotlin.math.max(kotlin.math.abs(dragX), kotlin.math.abs(dragY)) < threshold) return@detectDragGestures
                                        when {
                                            kotlin.math.abs(dragX) >= kotlin.math.abs(dragY) && dragX < 0 ->
                                                performPlayerGesture(gestureSettings.coverSwipeLeft)
                                            kotlin.math.abs(dragX) >= kotlin.math.abs(dragY) && dragX > 0 ->
                                                performPlayerGesture(gestureSettings.coverSwipeRight)
                                            dragY < 0 ->
                                                performPlayerGesture(gestureSettings.coverSwipeUp)
                                            else -> currentSheetContentState = PlayerSheetState.COLLAPSED
                                        }
                                    }
                                )
                            }
                            .zIndex(if (isQueueExpanded) 1f else if (isLyricsModeEnabled || lyricsArtworkProgress > 0f) 6f else 3f)
                    ) {
                        if (
                            isVideoMode &&
                            audioService != null &&
                            isPlayerFullyExpanded &&
                            !isLyricsModeEnabled &&
                            !isQueueExpanded
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                if (!isVideoFullscreen) {
                                    BoxWithConstraints(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val squareVideoSize = minOf(maxWidth, maxHeight)
                                        androidx.compose.ui.viewinterop.AndroidView(
                                        factory = { viewContext ->
                                            (android.view.LayoutInflater.from(viewContext)
                                                .inflate(com.dsann.music.R.layout.view_video_player, null)
                                                as androidx.media3.ui.PlayerView).apply {
                                                player = audioService?.getPlayerInstance()
                                                useController = true
                                                controllerShowTimeoutMs = 3000
                                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                                keepScreenOn = true
                                            }
                                        },
                                        update = { playerView ->
                                            playerView.player = audioService?.getPlayerInstance()
                                        },
                                        modifier = Modifier.size(squareVideoSize)
                                    )
                                    }
                                }
                                VideoOverlayControls(
                                    qualityLabel = selectedVideoQuality,
                                    qualityEnabled = videoQualityOptions.isNotEmpty() && !videoQualityLoading,
                                    onOpenQuality = { showVideoQualityDialog = true },
                                    onToggleFullscreen = { isVideoFullscreen = !isVideoFullscreen },
                                    fullscreen = false
                                )
                            }

                            if (isVideoFullscreen) {
                                Dialog(
                                    onDismissRequest = { isVideoFullscreen = false },
                                    properties = DialogProperties(usePlatformDefaultWidth = false)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black)
                                    ) {
                                        BoxWithConstraints(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val squareVideoSize = minOf(maxWidth, maxHeight)
                                        androidx.compose.ui.viewinterop.AndroidView(
                                            factory = { viewContext ->
                                                (android.view.LayoutInflater.from(viewContext)
                                                .inflate(com.dsann.music.R.layout.view_video_player, null)
                                                as androidx.media3.ui.PlayerView).apply {
                                                player = audioService?.getPlayerInstance()
                                                useController = true
                                                controllerShowTimeoutMs = 3000
                                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                                keepScreenOn = true
                                            }
                                            },
                                            update = { playerView ->
                                                playerView.player = audioService?.getPlayerInstance()
                                            },
                                            modifier = Modifier.size(squareVideoSize)
                                        )
                                    }
                                        VideoOverlayControls(
                                            qualityLabel = selectedVideoQuality,
                                            qualityEnabled = videoQualityOptions.isNotEmpty() && !videoQualityLoading,
                                            onOpenQuality = { showVideoQualityDialog = true },
                                            onToggleFullscreen = { isVideoFullscreen = false },
                                            fullscreen = true
                                        )
                                    }
                                }
                            }

                            if (showVideoQualityDialog) {
                                AlertDialog(
                                    onDismissRequest = { showVideoQualityDialog = false },
                                    title = { Text("Video quality") },
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (videoQualityLoading) {
                                                Text("Loading available qualities…")
                                            } else if (videoQualityOptions.isEmpty()) {
                                                Text("No alternative muxed video qualities are available for this track.")
                                            } else {
                                                videoQualityOptions.forEach { option ->
                                                    TextButton(
                                                        onClick = {
                                                            audioService?.setVideoStreamQuality(
                                                                streamUrl = option.url,
                                                                audioUrl = option.audioUrl,
                                                                videoMimeType = option.mimeType,
                                                                audioMimeType = option.audioMimeType
                                                            )
                                                            selectedVideoQuality = option.label
                                                            viewModel.setVideoPlaybackMetadata(
                                                                com.dsann.music.viewmodel.VideoPlaybackMetadata(
                                                                    title = option.videoTitle?.takeIf { it.isNotBlank() }
                                                                        ?: videoPlaybackMetadata?.title
                                                                        ?: currentSong?.title.orEmpty(),
                                                                    uploader = option.videoUploader?.takeIf { it.isNotBlank() }
                                                                        ?: videoPlaybackMetadata?.uploader
                                                                        ?: currentSong?.artist.orEmpty(),
                                                                    durationMs = option.durationMs.takeIf { it > 0L }
                                                                        ?: videoPlaybackMetadata?.durationMs ?: 0L,
                                                                    resolution = option.label,
                                                                    mimeType = option.mimeType,
                                                                    description = option.videoDescription ?: videoPlaybackMetadata?.description,
                                                                    viewCount = option.videoViewCount ?: videoPlaybackMetadata?.viewCount,
                                                                    likeCount = option.videoLikeCount ?: videoPlaybackMetadata?.likeCount,
                                                                    uploadDate = option.videoUploadDate ?: videoPlaybackMetadata?.uploadDate,
                                                                    thumbnailUrl = option.videoThumbnailUrl ?: videoPlaybackMetadata?.thumbnailUrl,
                                                                    uploaderUrl = option.videoUploaderUrl ?: videoPlaybackMetadata?.uploaderUrl
                                                                )
                                                            )
                                                            showVideoQualityDialog = false
                                                        }
                                                    ) {
                                                        Text(
                                                            if (selectedVideoQuality == option.label) {
                                                                "✓  ${option.label}"
                                                            } else {
                                                                option.label
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(onClick = { showVideoQualityDialog = false }) {
                                            Text("Close")
                                        }
                                    }
                                )
                            }
                        } else {
                            ArtworkPager(
                                viewModel = viewModel,
                                currentSong = currentSong!!,
                                songList = songList,
                                currentSongIndex = uiState.currentSongIndex,
                                context = context,
                                userScrollEnabled = isPlayerFullyExpanded && !isLyricsModeEnabled && !isQueueExpanded,
                                modifier = Modifier.fillMaxSize()
                            )
                            MiniPlayerArtworkProgressOverlay(
                                viewModel = viewModel,
                                accentColor = baseAccentColor,
                                expansionFraction = playerContentExpansionFraction,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // Global Dynamic Snackbar
        val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
        
        LaunchedEffect(Unit) {
            com.dsann.music.ui.SnackbarController.events.collect { event ->
                snackbarHostState.showSnackbar(
                    com.dsann.music.ui.CustomSnackbarVisuals(
                        message = event.message,
                        duration = event.duration,
                        iconResId = event.iconResId,
                        iconVector = event.iconVector
                    )
                )
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(100f) // Keep Snackbar always on top
        ) {
            androidx.compose.material3.SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset {
                        val bottomNavHeightPx = if (dynamicBottomNavHeightState.value > 0f) {
                            dynamicBottomNavHeightState.value
                        } else {
                            with(density) { 75.dp.toPx() }
                        }
                        val bottomNavVisibilityFraction = if (useNavigationRail || !bottomNavVisible) 1f else 0f
                        val currentBottomGapPx = with(density) { 
                            androidx.compose.ui.unit.lerp(12.dp, 8.dp, bottomNavVisibilityFraction).toPx() 
                        }
                        
                        val effectiveBottomNavHeight = if (useNavigationRail || !bottomNavVisible) 0f else bottomNavHeightPx
                        
                        var bottomOffsetPx = effectiveBottomNavHeight + with(density) { 16.dp.toPx() }
                        
                        if (showPlayerContentArea) {
                            if (currentSheetContentState == PlayerSheetState.EXPANDED) {
                                // The expanded sheet starts at (or very near) the top of the screen.
                                // Anchoring the snackbar to that top edge can move it off-screen.
                                // Keep download/playback messages inside the visible player instead.
                                bottomOffsetPx = with(density) { 96.dp.toPx() }
                            } else {
                                val miniPlayerHeightPx = with(density) { miniPlayerHeight.toPx() }
                                bottomOffsetPx = effectiveBottomNavHeight + currentBottomGapPx + miniPlayerHeightPx + with(density) { 8.dp.toPx() }

                                val topOfPlayerSheetY = sheetVisualState.visualSheetTranslationYProvider()
                                bottomOffsetPx = screenHeightPx - topOfPlayerSheetY + with(density) { 8.dp.toPx() }
                            }
                        }
                        
                        IntOffset(0, -bottomOffsetPx.roundToInt())
                    }
            ) { data ->
                androidx.compose.material3.Snackbar(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    val customVisuals = data.visuals as? com.dsann.music.ui.CustomSnackbarVisuals
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        if (customVisuals?.iconVector != null) {
                            Icon(
                                imageVector = customVisuals.iconVector,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.inversePrimary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        } else if (customVisuals?.iconResId != null) {
                            Icon(
                                painter = painterResource(id = customVisuals.iconResId),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.inversePrimary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = data.visuals.message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}


// ==========================================
// 8. MINI PLAYER ROW INTERNAL CONTENT
// ==========================================
@Composable
private fun MiniPlayerContentInternal(
    viewModel: PlayerSharedViewModel,
    currentSong: SongItem,
    isPlaying: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val miniPlayerPreferences = remember(context) {
        context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
    }
    val controlShapeName by miniPlayerPreferences.observeKey("mini_player_control_shape", "Circle")
    val controlIconSet by miniPlayerPreferences.observeKey("mini_player_control_icons", "Classic")
    val controlShape: Shape = when (controlShapeName) {
        "Rounded" -> RoundedCornerShape(14.dp)
        "Pill" -> RoundedCornerShape(percent = 50)
        else -> CircleShape
    }
    val miniControlBackgroundColor = if (isDark) Color(0x1AFFFFFF) else Color(0x0D000000)
    val controlBackground: Modifier = if (controlShapeName == "None") {
        Modifier
    } else {
        Modifier.background(miniControlBackgroundColor, controlShape)
    }
    val sideControlWidth = when (controlShapeName) {
        "Pill" -> 64.dp
        "Rounded" -> 52.dp
        else -> 48.dp
    }
    val centerControlWidth = when (controlShapeName) {
        "Pill" -> 68.dp
        "Rounded" -> 56.dp
        else -> 48.dp
    }
    fun controlModifier(width: Dp): Modifier =
        Modifier.size(width = width, height = 48.dp).then(controlBackground)
    val compactLandscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val artworkSize = if (compactLandscape) 48.dp else 60.dp
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val contentColor = if (isDark) Color.White else Color(0xFF1C1B1F)
    val contentSecondary = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF1C1B1F).copy(alpha = 0.6f)
    Row(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
            .padding(
                start = 10.dp,
                end = 8.dp,
                top = if (compactLandscape) 4.dp else 10.dp,
                bottom = if (compactLandscape) 4.dp else 10.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Reserves the same 60dp artwork slot used by the shared transformed cover.
        // Progress is painted above that cover so both always share coordinates and clipping.
        Box(modifier = Modifier.size(artworkSize))

        Spacer(modifier = Modifier.width(if (compactLandscape) 12.dp else 20.dp))

        // Song Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currentSong.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = currentSong.artist,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = contentSecondary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Keep a visible gap so adjacent rounded backgrounds never merge into one shape.
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.playPreviousSong() },
                modifier = controlModifier(sideControlWidth)
            ) {
                if (controlIconSet == "Classic") {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_previous),
                        contentDescription = "Previous",
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (controlIconSet == "Seek") Icons.Filled.FastRewind else Icons.Filled.ChevronLeft,
                        contentDescription = "Previous",
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            IconButton(
                onClick = {
                    if (!isLoading) {
                        if (isPlaying) viewModel.pauseAudio() else viewModel.playAudio()
                    }
                },
                modifier = controlModifier(centerControlWidth)
            ) {
                if (isLoading) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(20.dp), color = contentColor, strokeWidth = 2.dp
                    )
                } else if (controlIconSet == "Classic") {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = "Play/Pause",
                        tint = contentColor,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = contentColor,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }

            IconButton(
                onClick = { viewModel.playNextSong() },
                modifier = controlModifier(sideControlWidth)
            ) {
                if (controlIconSet == "Classic") {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_next),
                        contentDescription = "Next",
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (controlIconSet == "Seek") Icons.Filled.FastForward else Icons.Filled.ChevronRight,
                        contentDescription = "Next",
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}


@Composable
private fun MiniPlayerArtworkProgressOverlay(
    viewModel: PlayerSharedViewModel,
    accentColor: Color,
    expansionFraction: Animatable<Float, AnimationVector1D>,
    modifier: Modifier = Modifier
) {
    val isMiniLayerVisible by remember(expansionFraction) {
        derivedStateOf { expansionFraction.value < 0.5f }
    }
    if (!isMiniLayerVisible) return

    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
    }
    val style by preferences.observeKey("mini_player_progress", "Ring")
    if (style == "None") return

    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val targetProgress = if (duration > 0) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progressState = animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 350, easing = LinearEasing),
        label = "MiniArtworkProgress"
    )
    androidx.compose.foundation.Canvas(
        modifier = modifier.graphicsLayer {
            alpha = (1f - expansionFraction.value * 2f).coerceIn(0f, 1f)
        }
    ) {
        val side = size.minDimension
        if (side <= 0f) return@Canvas
        // Animate the canvas without recomposing the always-visible mini-player layer.
        val progress = progressState.value

        when (style) {
            "Ring" -> {
                val strokeWidth = (side * 0.052f).coerceAtLeast(1f)
                val inset = strokeWidth / 2f + side * 0.012f
                val ringSize = (side - inset * 2f).coerceAtLeast(1f)
                val ringTopLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                val ringDimensions = androidx.compose.ui.geometry.Size(ringSize, ringSize)
                val ringStroke = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                drawArc(
                    color = accentColor.copy(alpha = 0.28f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = ringTopLeft,
                    size = ringDimensions,
                    style = ringStroke
                )
                if (progress > 0f) {
                    drawArc(
                        color = accentColor,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = ringTopLeft,
                        size = ringDimensions,
                        style = ringStroke
                    )
                }
            }
            "Line" -> {
                val startX = side * 0.08f
                val endX = size.width - startX
                val y = size.height * 0.91f
                val thickness = (side * 0.028f).coerceAtLeast(1f)
                drawLine(
                    color = accentColor.copy(alpha = 0.28f),
                    start = androidx.compose.ui.geometry.Offset(startX, y),
                    end = androidx.compose.ui.geometry.Offset(endX, y),
                    strokeWidth = thickness,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                if (progress > 0f) {
                    drawLine(
                        color = accentColor,
                        start = androidx.compose.ui.geometry.Offset(startX, y),
                        end = androidx.compose.ui.geometry.Offset(startX + (endX - startX) * progress, y),
                        strokeWidth = thickness,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
            "Fill" -> {
                val fillHeight = size.height * progress
                if (fillHeight > 0f) {
                    val top = size.height - fillHeight
                    drawRect(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.10f),
                                accentColor.copy(alpha = 0.48f)
                            ),
                            startY = top,
                            endY = size.height
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, top),
                        size = androidx.compose.ui.geometry.Size(size.width, fillHeight)
                    )
                    drawLine(
                        color = accentColor.copy(alpha = 0.9f),
                        start = androidx.compose.ui.geometry.Offset(0f, top),
                        end = androidx.compose.ui.geometry.Offset(size.width, top),
                        strokeWidth = (side * 0.018f).coerceAtLeast(1f)
                    )
                }
            }
        }
    }
}
