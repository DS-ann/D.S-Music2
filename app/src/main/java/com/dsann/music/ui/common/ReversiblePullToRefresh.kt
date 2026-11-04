package com.dsann.music.ui.common

import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect

/**
 * Wraps a Material 3 pull-to-refresh callback with a reversible-gesture guard.
 *
 * A refresh is committed only by PullToRefreshBox on release. Once the pull has
 * crossed its threshold, a deliberate upward reversal cancels that pull gesture,
 * even when the finger is still slightly beyond the threshold at release.
 */
@Composable
fun rememberReversiblePullToRefreshCallback(
    state: PullToRefreshState,
    onRefresh: () -> Unit
): () -> Unit {
    var crossedThreshold by remember(state) { mutableStateOf(false) }
    var reversedAfterThreshold by remember(state) { mutableStateOf(false) }
    val latestOnRefresh by rememberUpdatedState(onRefresh)

    LaunchedEffect(state) {
        var peakFractionAfterThreshold = 0f
        snapshotFlow { state.distanceFraction to state.isAnimating }.collect { (fraction, animating) ->
            val current = fraction.coerceAtLeast(0f)

            if (!animating) {
                if (current > 1f) {
                    crossedThreshold = true
                    peakFractionAfterThreshold = maxOf(peakFractionAfterThreshold, current)
                } else if (crossedThreshold) {
                    peakFractionAfterThreshold = maxOf(peakFractionAfterThreshold, current)
                }

                // Ignore tiny finger jitter; a purposeful pull-back of 0.12 threshold
                // lengths cancels the gesture until the release animation resets it.
                if (crossedThreshold && peakFractionAfterThreshold - current >= 0.12f) {
                    reversedAfterThreshold = true
                }
            } else if (current <= 0.05f) {
                crossedThreshold = false
                reversedAfterThreshold = false
                peakFractionAfterThreshold = 0f
            }
        }
    }

    return remember(state) {
        {
            val wasCancelled = reversedAfterThreshold
            crossedThreshold = false
            reversedAfterThreshold = false
            if (!wasCancelled) latestOnRefresh()
        }
    }
}
