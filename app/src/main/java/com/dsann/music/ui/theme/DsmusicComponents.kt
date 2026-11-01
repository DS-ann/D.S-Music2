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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val outline = DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
    Column(
        modifier = modifier
            .clip(shape)
            .background(DsmusicTheme.colorScheme.surfaceContainerLow)
            .border(BorderStroke(1.dp, outline.copy(alpha = 0.62f)), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content
    )
}

@Composable
fun DsmusicSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    color: Color = DsmusicTheme.colorScheme.surfaceContainer,
    contentColor: Color = DsmusicTheme.colorScheme.onSurface,
    tonalElevation: Any? = null,
    shadowElevation: Any? = null,
    border: Any? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.clip(shape).background(color)
            .border(
                BorderStroke(1.dp, DsmusicTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)),
                shape
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        CompositionLocalProvider(LocalDsmusicContentColor provides contentColor) {
            content()
        }
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
    colors: DsmusicListItemColors? = null,
    shapes: Shape? = null,
    selected: Boolean = false,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    content: (@Composable () -> Unit)? = null
) {
    val paddingValues = contentPadding ?: PaddingValues(horizontal = 13.dp, vertical = 10.dp)
    val itemShape = shapes ?: RoundedCornerShape(14.dp)
    val scheme = DsmusicTheme.colorScheme
    val itemBackground = when {
        colors == null -> scheme.surfaceContainerLow
        selected -> colors.selectedContainerColor
        else -> colors.containerColor
    }
    val itemBorder = scheme.outlineVariant.copy(alpha = 0.34f)
    Row(
        modifier = modifier.fillMaxWidth().clip(itemShape)
            .background(itemBackground)
            .border(1.dp, itemBorder, itemShape)
            .then(
                when {
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    checked != null && onCheckedChange != null ->
                        Modifier.clickable { onCheckedChange(!checked) }
                    else -> Modifier
                }
            )
            .padding(paddingValues),
        verticalAlignment = verticalAlignment,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leadingContent?.let {
            val leadingColor = when {
                colors == null -> scheme.onSurfaceVariant
                selected -> colors.selectedLeadingContentColor
                else -> colors.leadingIconColor
            }
            CompositionLocalProvider(LocalDsmusicContentColor provides leadingColor) {
                Box(contentAlignment = Alignment.Center) { it() }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            overlineContent?.let {
                CompositionLocalProvider(LocalDsmusicContentColor provides scheme.onSurfaceVariant) { it() }
            }
            CompositionLocalProvider(
                LocalDsmusicContentColor provides when {
                    colors == null -> scheme.onSurface
                    selected -> colors.selectedContentColor
                    else -> colors.headlineColor
                }
            ) {
                when {
                    headlineContent != null -> headlineContent()
                    content != null -> content()
                }
            }
            supportingContent?.let {
                CompositionLocalProvider(
                    LocalDsmusicContentColor provides (colors?.supportingContentColor ?: scheme.onSurfaceVariant)
                ) { it() }
            }
        }
        trailingContent?.let {
            val trailingColor = when {
                colors == null -> scheme.onSurfaceVariant
                selected -> colors.selectedTrailingContentColor
                else -> colors.trailingIconColor
            }
            CompositionLocalProvider(LocalDsmusicContentColor provides trailingColor) {
                Box(contentAlignment = Alignment.Center) { it() }
            }
        }
    }
}

@Composable
fun DsmusicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    colors: DsmusicButtonColors? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
    elevation: Any? = null,
    interactionSource: Any? = null,
    content: @Composable RowScope.() -> Unit
) {
    val scheme = DsmusicTheme.colorScheme
    // Resolve enabled and disabled colours from the D.S Music scheme.
    val backgroundColor = if (enabled) colors?.containerColor ?: scheme.primary
        else colors?.disabledContainerColor ?: scheme.surfaceVariant
    val foregroundColor = if (enabled) colors?.contentColor ?: scheme.onPrimary
        else colors?.disabledContentColor ?: scheme.onSurfaceVariant
    Row(
        modifier = modifier.defaultMinSize(minHeight = 40.dp).clip(shape)
            .background(backgroundColor)
            .then(
                if (border != null) Modifier.border(border, shape)
                else Modifier.border(
                    1.dp,
                    backgroundColor.copy(alpha = if (enabled) 0.92f else 0.36f),
                    shape
                )
            )
            .clickable(enabled = enabled, onClick = onClick).padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalDsmusicContentColor provides foregroundColor) {
            content()
        }
    }
}

@Composable
fun DsmusicOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    colors: DsmusicButtonColors? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    interactionSource: Any? = null,
    content: @Composable RowScope.() -> Unit
) {
    val scheme = DsmusicTheme.colorScheme
    val backgroundColor = if (enabled) colors?.containerColor ?: scheme.surfaceContainerLow
        else colors?.disabledContainerColor ?: scheme.surfaceContainerLow
    val foregroundColor = if (enabled) colors?.contentColor ?: scheme.onSurface
        else colors?.disabledContentColor ?: scheme.onSurfaceVariant
    Row(
        modifier = modifier.defaultMinSize(minHeight = 40.dp).clip(shape)
            .background(backgroundColor)
            .then(
                if (border != null) Modifier.border(border, shape)
                else Modifier.border(
                    1.dp,
                    scheme.outlineVariant.copy(alpha = if (enabled) 0.55f else 0.28f),
                    shape
                )
            )
            .clickable(enabled = enabled, onClick = onClick).padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalDsmusicContentColor provides foregroundColor) {
            content()
        }
    }
}

@Composable
fun DsmusicTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: DsmusicButtonColors? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    interactionSource: Any? = null,
    content: @Composable RowScope.() -> Unit
) {
    val contentColor = if (enabled) colors?.contentColor ?: DsmusicTheme.colorScheme.primary
        else colors?.disabledContentColor ?: DsmusicTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier.clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalDsmusicContentColor provides contentColor) { content() }
    }
}

@Composable
fun DsmusicIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: Any? = null,
    colors: Any? = null,
    shape: Shape = CircleShape,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
            .clip(shape).clickable(enabled = enabled, onClick = onClick),
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
    val scheme = DsmusicTheme.colorScheme
    Row(
        modifier = modifier.clip(shape)
            .background(if (selected) scheme.primary else scheme.surfaceContainerLow)
            .border(
                1.dp,
                if (selected) scheme.primary else scheme.outlineVariant.copy(alpha = 0.42f),
                shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(
            LocalDsmusicContentColor provides if (selected) scheme.onPrimary else scheme.onSurfaceVariant
        ) {
            leadingIcon?.invoke()
            label()
            trailingIcon?.invoke()
        }
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
    val accent = DsmusicTheme.colorScheme.primary
    Box(
        modifier = modifier.size(width = 44.dp, height = 26.dp).clip(CircleShape)
            .background(if (checked && enabled) accent else DsmusicTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled && onCheckedChange != null) { onCheckedChange?.invoke(!checked) }
            .padding(horizontal = 3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.size(if (checked) 20.dp else 18.dp).clip(CircleShape)
            .background(if (checked && enabled) DsmusicTheme.colorScheme.onPrimary else DsmusicTheme.colorScheme.onSurfaceVariant))
    }
}

/**
 * Shared D.S Launcher / Home Studio page heading used by the main tabs.
 * The larger title, accent rail and deliberate vertical rhythm replace compact
 * Material-style top bars while keeping the screen's own trailing actions.
 */
@Composable
fun DsmusicPageHeading(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    kicker: String = "D.S MUSIC"
) {
    val titleSize = when {
        title.length >= 19 -> 22.sp
        title.length >= 15 -> 26.sp
        else -> 31.sp
    }
    val titleLineHeight = when {
        title.length >= 19 -> 27.sp
        title.length >= 15 -> 32.sp
        else -> 37.sp
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(if (subtitle == null) 42.dp else 48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DsmusicTheme.colorScheme.primary)
        )
        Column(
            modifier = Modifier.padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = kicker.uppercase(),
                style = DsmusicTheme.typography.labelMedium.copy(
                    color = DsmusicTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.7.sp
                ),
                maxLines = 1
            )
            Text(
                text = title,
                style = DsmusicTheme.typography.headlineMedium.copy(
                    fontSize = titleSize,
                    lineHeight = titleLineHeight,
                    color = DsmusicTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = DsmusicTheme.typography.bodyMedium.copy(
                        color = DsmusicTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
