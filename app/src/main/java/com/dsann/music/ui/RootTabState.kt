package com.dsann.music.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True only for the visible root tab. Cached tabs stay composed to preserve screen state,
 * but hidden tabs must not run decorative frame-by-frame animations.
 */
internal val LocalRootTabActive = staticCompositionLocalOf { true }
