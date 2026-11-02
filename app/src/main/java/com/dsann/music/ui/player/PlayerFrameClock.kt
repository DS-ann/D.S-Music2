package com.dsann.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos

/**
 * Publishes the timestamp of each frame Compose can actually render.
 *
 * Consumers share this state so the scrubber, waveform and visualizer use the
 * same display cadence instead of independent delay-based timers.
 */
@Composable
internal fun rememberPlayerFrameTimeNanos(active: Boolean): State<Long> {
    val frameTimeNanos = remember { mutableLongStateOf(0L) }

    LaunchedEffect(active) {
        if (!active) {
            frameTimeNanos.longValue = 0L
            return@LaunchedEffect
        }

        while (true) {
            withFrameNanos { frameTimeNanos.longValue = it }
        }
    }

    return frameTimeNanos
}
