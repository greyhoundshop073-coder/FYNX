package com.fynx.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

internal data class FynxBottomBarItem(val key: String, val label: String, val icon: ImageVector)

/**
 * Height of the floating bottom bar area, including its margins and the system navigation inset.
 * Primary tabs scroll under the bar, so each one adds this to the end of its own list; it is zero
 * whenever the bar is hidden or the screen is not a primary tab.
 */
internal val LocalFynxBottomBarSpace = compositionLocalOf { 0.dp }

/** The bar space without the system inset, for controls that already add navigationBarsPadding(). */
@Composable
internal fun fynxBottomBarSpaceAboveInset(): Dp =
    (LocalFynxBottomBarSpace.current - WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        .coerceAtLeast(0.dp)

/**
 * Floating glass tab bar: a translucent capsule with a bright rim and a soft top highlight, and a
 * glass pill that slides to the selected tab. Colors follow the app's light or dark surface.
 */
@Composable
internal fun FynxGlassBottomBar(
    items: List<FynxBottomBarItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val barShape = RoundedCornerShape(32.dp)
    val pillShape = RoundedCornerShape(26.dp)
    val density = LocalDensity.current
    val barHeight = 64.dp * density.fontScale.coerceIn(1f, 1.3f)
    val selectedIndex = items.indexOfFirst { it.key == selectedKey }
    val indicatorPosition by animateFloatAsState(
        targetValue = selectedIndex.coerceAtLeast(0).toFloat(),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "FYNX glass tab indicator"
    )
    val haptics = LocalHapticFeedback.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp

    val barFill = if (isLight) {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.90f), Color(0xFFEAF0F7).copy(alpha = 0.82f)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF28384F).copy(alpha = 0.90f), Color(0xFF0F1A2D).copy(alpha = 0.94f)))
    }
    val barRim = if (isLight) {
        Brush.linearGradient(listOf(Color.White, Color(0x2264748B), Color.White.copy(alpha = 0.75f)))
    } else {
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.24f)))
    }
    val topHighlight = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = if (isLight) 0.60f else 0.15f), Color.Transparent)
    )
    val pillFill = if (isLight) {
        Brush.verticalGradient(listOf(FynxDesign.AzureBlue.copy(alpha = 0.20f), FynxDesign.AzureBlue.copy(alpha = 0.10f)))
    } else {
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.10f)))
    }
    val pillRim = if (isLight) {
        BorderStroke(1.dp, FynxDesign.AzureBlue.copy(alpha = 0.38f))
    } else {
        BorderStroke(1.dp, Color.White.copy(alpha = 0.40f))
    }
    val selectedColor = if (isLight) Color(0xFF0060C7) else Color(0xFF7DBBFF)
    val idleColor = if (isLight) Color(0xFF475569) else Color(0xFFB9C6D8)

    Box(
        modifier = modifier
            .height(barHeight)
            .shadow(
                elevation = 14.dp,
                shape = barShape,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.35f)
            )
            .clip(barShape)
            .background(barFill)
            .border(1.dp, barRim, barShape)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .align(Alignment.TopCenter)
                .background(topHighlight)
        )
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 4.dp)) {
            val itemWidth = maxWidth / items.size
            val itemWidthPx = constraints.maxWidth / items.size.toFloat()
            Box(
                Modifier
                    .offset { IntOffset((indicatorPosition * itemWidthPx).roundToInt(), 0) }
                    .width(itemWidth)
                    .fillMaxHeight()
                    .alpha(if (selectedIndex >= 0) 1f else 0f)
                    .clip(pillShape)
                    .background(pillFill)
                    .border(pillRim, pillShape)
            )
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
                items.forEach { item ->
                    val selected = item.key == selectedKey
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val tint by animateColorAsState(if (selected) selectedColor else idleColor, label = "FYNX tab tint")
                    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "FYNX tab press")
                    Column(
                        modifier = Modifier
                            .width(itemWidth)
                            .fillMaxHeight()
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .selectable(
                                selected = selected,
                                interactionSource = interaction,
                                indication = null,
                                role = Role.Tab,
                                onClick = {
                                    if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(item.key)
                                }
                            )
                            .semantics(mergeDescendants = true) { contentDescription = item.label },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(23.dp))
                        Text(
                            fynxBottomNavLabel(item.label, screenWidthDp, density.fontScale),
                            color = tint,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
