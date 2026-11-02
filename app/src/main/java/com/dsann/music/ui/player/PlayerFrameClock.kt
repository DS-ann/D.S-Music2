package com.dsann.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos

/**
 * Publishes the platform-vsync frame timestamp supplied by Compose.
 *
 * withFrameNanos follows the display's actual refresh cadence; this deliberately
 * has no fixed 30/60 FPS timer or app-side cap. 60 Hz, 90 Hz, 120 Hz and older
 * 60 Hz Android 6 displays all use the frames the device can really present.
 *
 * Share one returned State with related drawing components so the scrubber,
 * waveform and visualizer stay phase-aligned instead of running timer loops.
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
