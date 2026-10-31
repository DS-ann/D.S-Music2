package com.dsann.music.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContentColor
import androidx.compose.ui.unit.dp

/** Flat D.S Launcher-inspired surfaces: no tonal elevation or liquid-glass layers. */
private val DsmusicPanelShape = RoundedCornerShape(18.dp)

@Composable
fun DsmusicCard(
    modifier: Modifier = Modifier,
    shape: Shape = DsmusicPanelShape,
    colors: Any? = null,
    elevation: Any? = null,
    border: Any? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val outline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(BorderStroke(1.dp, outline), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content
    )
}

@Composable
fun DsmusicSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    tonalElevation: Any? = null,
    shadowElevation: Any? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.clip(shape).background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}

@Composable
fun DsmusicListItem(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    headlineContent: (@Composable () -> Unit)? = null,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues? = null,
    colors: Any? = null,
    shapes: Any? = null,
    content: (@Composable () -> Unit)? = null
) {
    val paddingValues = contentPadding ?: PaddingValues(horizontal = 13.dp, vertical = 10.dp)
    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(paddingValues),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leadingContent?.let { Box(contentAlignment = Alignment.Center) { it() } }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            overlineContent?.invoke()
            when {
                headlineContent != null -> headlineContent()
                content != null -> content()
            }
            supportingContent?.invoke()
        }
        trailingContent?.let { Box(contentAlignment = Alignment.Center) { it() } }
    }
}

@Composable
fun DsmusicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    colors: Any? = null,
    border: Any? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
    content: @Composable RowScope.() -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier.defaultMinSize(minHeight = 40.dp).clip(shape)
            .background(accent.copy(alpha = if (enabled) 0.16f else 0.06f))
            .border(1.dp, accent.copy(alpha = if (enabled) 0.24f else 0.08f), shape)
            .clickable(enabled = enabled, onClick = onClick).padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun DsmusicOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    colors: Any? = null,
    border: Any? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.defaultMinSize(minHeight = 40.dp).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f), shape)
            .clickable(enabled = enabled, onClick = onClick).padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun DsmusicTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: Any? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun DsmusicIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: Any? = null,
    colors: Any? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
            .clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun DsmusicFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(10.dp),
    colors: Any? = null,
    border: Any? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier.clip(shape)
            .background(if (selected) accent.copy(alpha = 0.17f) else MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, if (selected) accent.copy(alpha = 0.52f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leadingIcon?.invoke()
        CompositionLocalProvider(LocalContentColor provides if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant) { label() }
        trailingIcon?.invoke()
    }
}

@Composable
fun DsmusicSwitchControl(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: Any? = null
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier.size(width = 44.dp, height = 26.dp).clip(CircleShape)
            .background(if (checked) accent.copy(alpha = if (enabled) 0.9f else 0.35f) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled && onCheckedChange != null) { onCheckedChange?.invoke(!checked) }
            .padding(horizontal = 3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.size(if (checked) 20.dp else 18.dp).clip(CircleShape)
            .background(if (checked) Color(0xFF0B1220) else MaterialTheme.colorScheme.onSurfaceVariant))
    }
}
