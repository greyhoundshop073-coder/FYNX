package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared FYNX control primitives.
 *
 * SAFE: Adding this file does not change any existing screen.
 * Screens keep their current buttons until you intentionally
 * replace a local Button with one of these helpers later.
 *
 * Uses existing FynxDesign.ControlShape and MaterialTheme colours
 * so the look stays consistent with the official FYNX design system.
 */
object FynxSpacing {
    /** Standard horizontal page padding used across major FYNX screens. */
    val PageHorizontal: Dp = 16.dp

    /** Slightly tighter padding for dense lists and chat-like surfaces. */
    val PageHorizontalCompact: Dp = 12.dp

    /** Vertical gap between major sections on a screen. */
    val SectionGap: Dp = 16.dp

    /** Vertical gap between related items inside a section. */
    val ItemGap: Dp = 8.dp

    /** Gap between a primary and secondary action in a row. */
    val ActionGap: Dp = 12.dp

    /** Minimum recommended touch target size (accessibility). */
    val MinTouchTarget: Dp = 48.dp

    /** Standard content padding inside primary/secondary buttons. */
    val ButtonContentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
}

@Composable
fun FynxPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fullWidth: Boolean = true,
    contentDescription: String? = null
) {
    val desc = contentDescription ?: text
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .defaultMinSize(minHeight = FynxSpacing.MinTouchTarget)
            .heightIn(min = FynxSpacing.MinTouchTarget)
            .semantics { this.contentDescription = desc },
        shape = FynxDesign.ControlShape,
        contentPadding = FynxSpacing.ButtonContentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.60f)
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun FynxSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fullWidth: Boolean = true,
    contentDescription: String? = null
) {
    val desc = contentDescription ?: text
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .defaultMinSize(minHeight = FynxSpacing.MinTouchTarget)
            .heightIn(min = FynxSpacing.MinTouchTarget)
            .semantics { this.contentDescription = desc },
        shape = FynxDesign.ControlShape,
        contentPadding = FynxSpacing.ButtonContentPadding,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun FynxTertiaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    val desc = contentDescription ?: text
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minHeight = FynxSpacing.MinTouchTarget)
            .semantics { this.contentDescription = desc },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}

@Composable
fun FynxDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fullWidth: Boolean = true,
    contentDescription: String? = null
) {
    val desc = contentDescription ?: text
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .defaultMinSize(minHeight = FynxSpacing.MinTouchTarget)
            .heightIn(min = FynxSpacing.MinTouchTarget)
            .semantics { this.contentDescription = desc },
        shape = FynxDesign.ControlShape,
        contentPadding = FynxSpacing.ButtonContentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
            disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.38f),
            disabledContentColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.60f)
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
