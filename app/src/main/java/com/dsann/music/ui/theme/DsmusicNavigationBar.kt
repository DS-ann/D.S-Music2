package com.dsann.music.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DsmusicNavigationDestination(
    val route: String,
    val label: String,
    val iconResId: Int
)

/**
 * Small, flat navigation surface matching D.S Launcher.
 * Uses Foundation layout, BasicText and drawable icons instead of Material
 * NavigationBar / NavigationBarItem so it does not create Material indicator
 * or tonal-elevation layers during each navigation update.
 */
@Composable
fun DsmusicNavigationBar(
    destinations: List<DsmusicNavigationDestination>,
    currentRoute: String?,
    labelsMode: String,
    iconSize: Dp,
    bottomInset: Dp,
    barStyle: String = "Floating",
    modifier: Modifier = Modifier,
    onNavigate: (String) -> Unit
) {
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val selectedColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    val dockShape = when (barStyle) {
        "Docked" -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
        "Minimal" -> RoundedCornerShape(0.dp)
        else -> RoundedCornerShape(26.dp)
    }
    val horizontalInset = if (barStyle == "Floating") 14.dp else 0.dp
    val verticalInset = if (barStyle == "Floating") 4.dp else 0.dp
    Row(
        modifier = modifier
            .padding(horizontal = horizontalInset, vertical = verticalInset)
            .clip(dockShape)
            .background(if (barStyle == "Minimal") Color.Transparent else surface)
            .padding(bottom = bottomInset, start = 6.dp, end = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        destinations.forEach { destination ->
            val selected = currentRoute == destination.route
            val tint = if (selected) selectedColor else inactiveColor
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onNavigate(destination.route) }
                    .padding(horizontal = 3.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 52.dp, height = 34.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected && barStyle != "Minimal") selectedColor.copy(alpha = 0.16f)
                            else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(destination.iconResId),
                        contentDescription = destination.label,
                        colorFilter = ColorFilter.tint(tint),
                        modifier = Modifier.size(iconSize)
                    )
                }
                if (labelsMode == "Always" || (labelsMode == "Selected" && selected)) {
                    BasicText(
                        text = destination.label,
                        style = TextStyle(
                            color = tint,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
