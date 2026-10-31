@file:Suppress("UNCHECKED_CAST")

package com.dsann.music.ui.theme

import android.app.Activity
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat.getInsetsController

/**
 * Observe one SharedPreferences key without keeping a listener after the
 * calling composition leaves the screen.
 */
@Composable
fun <T> SharedPreferences.observeKey(key: String, defaultValue: T): State<T> {
    val state = remember(this, key) { mutableStateOf(defaultValue) }
    DisposableEffect(this, key) {
        fun readValue(prefs: SharedPreferences): T {
            @Suppress("UNCHECKED_CAST")
            return when (defaultValue) {
                is Boolean -> prefs.getBoolean(key, defaultValue) as T
                is String -> (prefs.getString(key, defaultValue) ?: defaultValue) as T
                is Float -> prefs.getFloat(key, defaultValue) as T
                is Int -> prefs.getInt(key, defaultValue) as T
                is Long -> prefs.getLong(key, defaultValue) as T
                else -> defaultValue
            }
        }

        state.value = readValue(this@observeKey)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, changedKey ->
            if (changedKey == key) state.value = readValue(prefs)
        }
        registerOnSharedPreferenceChangeListener(listener)
        onDispose { unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

/**
 * D.S Music skin based on D.S Launcher.
 *
 * Existing user controls are intentionally retained: light/dark/system mode,
 * appearance presets, album-art accent, AMOLED black and density preferences.
 */
@Composable
fun SpatialFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicAlbumColor: Int? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
    }
    val amoledEnabled by prefs.observeKey("amoled_black", false)
    val albumArtThemeEnabled by prefs.observeKey("dynamic_album_theme", false)
    val appearancePreset by prefs.observeKey("appearance_preset", "Ocean")
    val customAccentHex by prefs.observeKey("custom_accent_color", "#7C9CFF")
    val uiDensity by prefs.observeKey("ui_density", "Comfortable")
    val uiCornerStyle by prefs.observeKey("ui_corner_style", "Soft")

    val accent = remember(appearancePreset, albumArtThemeEnabled, dynamicAlbumColor, customAccentHex) {
        if (albumArtThemeEnabled && dynamicAlbumColor != null) {
            Color(dynamicAlbumColor)
        } else {
            when (appearancePreset) {
                "Violet" -> Color(0xFFB69CFF)
                "Emerald" -> Color(0xFF62C9A2)
                "Amber" -> Color(0xFFFFC36F)
                "Rose" -> Color(0xFFFF8FA3)
                "Monochrome" -> Color(0xFFC9CDD4)
                "Crimson" -> Color(0xFFD94A65)
                "Teal" -> Color(0xFF36B9A8)
                "Cyan" -> Color(0xFF43B8D2)
                "Indigo" -> Color(0xFF6475E9)
                "Lime" -> Color(0xFF9FBF42)
                "Graphite" -> Color(0xFF8A939F)
                "Custom" -> runCatching { Color(android.graphics.Color.parseColor(customAccentHex)) }.getOrDefault(Color(0xFF7C9CFF))
                else -> Color(0xFF7C9CFF) // D.S Launcher blue
            }
        }
    }

    val colorScheme = remember(darkTheme, amoledEnabled, accent) {
        if (darkTheme) {
            val base = darkColorScheme(
                primary = accent,
                onPrimary = Color(0xFF0B1220),
                primaryContainer = accent.copy(alpha = 0.19f),
                onPrimaryContainer = Color(0xFFE6EDFF),
                secondary = Color(0xFFB8C6DE),
                onSecondary = Color(0xFF172131),
                secondaryContainer = Color(0xFF27364A),
                onSecondaryContainer = Color(0xFFDCE6F7),
                tertiary = Color(0xFF8CC9D8),
                onTertiary = Color(0xFF0B2228),
                background = Color(0xFF121620),
                onBackground = Color(0xFFE9EDF5),
                surface = Color(0xFF171E29),
                onSurface = Color(0xFFE9EDF5),
                surfaceVariant = Color(0xFF263141),
                onSurfaceVariant = Color(0xFFB7C3D5),
                surfaceTint = accent,
                outline = Color(0xFF65748A),
                outlineVariant = Color(0xFF354255),
                error = Color(0xFFFF8C91),
                onError = Color(0xFF2B080D),
                errorContainer = Color(0xFF5B2028),
                onErrorContainer = Color(0xFFFFDADD),
                surfaceContainerLowest = Color(0xFF0E131B),
                surfaceContainerLow = Color(0xFF151C26),
                surfaceContainer = Color(0xFF1B2431),
                surfaceContainerHigh = Color(0xFF222D3B),
                surfaceContainerHighest = Color(0xFF2A3747)
            )
            if (amoledEnabled) {
                base.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceContainerLowest = Color.Black,
                    surfaceContainerLow = Color.Black,
                    surfaceContainer = Color.Black,
                    surfaceContainerHigh = Color(0xFF080A0F),
                    surfaceContainerHighest = Color(0xFF111722)
                )
            } else base
        } else {
            lightColorScheme(
                primary = accent,
                onPrimary = Color(0xFF0B1220),
                primaryContainer = accent.copy(alpha = 0.18f),
                onPrimaryContainer = Color(0xFF17233A),
                secondary = Color(0xFF536783),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFDCE5F2),
                onSecondaryContainer = Color(0xFF202D40),
                tertiary = Color(0xFF397E91),
                onTertiary = Color.White,
                background = Color(0xFFF3F6FA),
                onBackground = Color(0xFF182131),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF182131),
                surfaceVariant = Color(0xFFE5EBF3),
                onSurfaceVariant = Color(0xFF536176),
                surfaceTint = accent,
                outline = Color(0xFF7B899B),
                outlineVariant = Color(0xFFD1D9E4),
                error = Color(0xFFB32635),
                onError = Color.White,
                errorContainer = Color(0xFFF9DADD),
                onErrorContainer = Color(0xFF410E16),
                surfaceContainerLowest = Color.White,
                surfaceContainerLow = Color(0xFFF7F9FC),
                surfaceContainer = Color(0xFFEFF3F8),
                surfaceContainerHigh = Color(0xFFE5EBF3),
                surfaceContainerHighest = Color(0xFFDCE4EF)
            )
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    val baseDensity = LocalDensity.current
    // UI density changes actual layout spacing and control dimensions, not just
    // text size. Keep the system font scale intact for accessibility.
    val densityFactor = when (uiDensity) {
        "Compact" -> 0.92f
        "Large" -> 1.08f
        else -> 1f
    }
    val adjustedDensity = remember(baseDensity, densityFactor) {
        object : androidx.compose.ui.unit.Density {
            override val density: Float = baseDensity.density * densityFactor
            override val fontScale: Float = baseDensity.fontScale
        }
    }

    CompositionLocalProvider(LocalDensity provides adjustedDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = remember(uiCornerStyle) {
                val (xs, sm, md, lg, xl) = when (uiCornerStyle) {
                    "Tight" -> listOf(3.dp, 5.dp, 8.dp, 11.dp, 15.dp)
                    "Round" -> listOf(9.dp, 14.dp, 20.dp, 26.dp, 32.dp)
                    else -> listOf(5.dp, 9.dp, 13.dp, 18.dp, 24.dp)
                }
                Shapes(
                    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(xs),
                    small = androidx.compose.foundation.shape.RoundedCornerShape(sm),
                    medium = androidx.compose.foundation.shape.RoundedCornerShape(md),
                    large = androidx.compose.foundation.shape.RoundedCornerShape(lg),
                    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(xl)
                )
            },
            content = content
        )
    }
}

internal tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
