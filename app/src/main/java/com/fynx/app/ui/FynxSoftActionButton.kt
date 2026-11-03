package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * Shared raised secondary action. It uses the light blue glass-like surface on light
 * appearances and a deep blue surface in dark/AMOLED appearances. Explicit caller
 * colors, borders, shapes, padding and interaction sources are preserved.
 */
@Composable
fun FynxSoftActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = FynxDesign.ControlShape,
    colors: ButtonColors? = null,
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    danger: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val lightSurface = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val containerColor = when {
        lightSurface && danger -> FynxDesign.SoftActionDangerLightContainer
        lightSurface -> FynxDesign.SoftActionLightContainer
        danger -> FynxDesign.SoftActionDangerDarkContainer
        else -> FynxDesign.SoftActionDarkContainer
    }
    val foregroundColor = when {
        lightSurface && danger -> FynxDesign.SoftActionDangerLightForeground
        lightSurface -> FynxDesign.SoftActionLightForeground
        danger -> FynxDesign.SoftActionDangerDarkForeground
        else -> FynxDesign.SoftActionDarkForeground
    }
    val outlineColor = when {
        lightSurface && danger -> FynxDesign.SoftActionDangerLightOutline
        lightSurface -> FynxDesign.SoftActionLightOutline
        danger -> FynxDesign.SoftActionDangerDarkOutline
        else -> FynxDesign.SoftActionDarkOutline
    }

    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = colors ?: ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = foregroundColor,
            disabledContainerColor = containerColor.copy(alpha = 0.48f),
            disabledContentColor = foregroundColor.copy(alpha = 0.56f)
        ),
        elevation = elevation ?: ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp,
            focusedElevation = 2.dp,
            hoveredElevation = 3.dp,
            disabledElevation = 0.dp
        ),
        border = border ?: BorderStroke(1.dp, outlineColor),
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}
