package com.github.damontecres.wholphin.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonBorder
import androidx.tv.material3.ButtonColors
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonGlow
import androidx.tv.material3.ButtonScale
import androidx.tv.material3.ButtonShape

/**
 * Glowfin new look, "One focus look everywhere" (Ben 2026-11-22): the button the remote is on goes solid white
 * with the aqua ring, and nothing grows, so it stays smooth on the TCL. This is the TV library's Button with
 * those defaults; every screen imports this one instead. A screen that sets its own colours, border or scale
 * still gets what it asks for.
 */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    scale: ButtonScale = ButtonDefaults.scale(focusedScale = 1f, pressedScale = .98f),
    glow: ButtonGlow = ButtonDefaults.glow(),
    shape: ButtonShape = ButtonDefaults.shape(),
    colors: ButtonColors = glowfinButtonColors(),
    tonalElevation: Dp = 0.dp,
    border: ButtonBorder = glowfinButtonBorder(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.tv.material3.Button(
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick,
        enabled = enabled,
        scale = scale,
        glow = glow,
        shape = shape,
        colors = colors,
        tonalElevation = tonalElevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/** Dark words on a white lit button */
val OnWhite: Color get() = GlowfinTheme.look.onFocusFill

/** The TV library's normal button colours, but lit = white with dark words (Kids: the Kids colour) */
@Composable
fun glowfinButtonColors(): ButtonColors =
    ButtonDefaults.colors(
        focusedContainerColor = BenflixColors.FocusFill,
        focusedContentColor = BenflixColors.OnFocusFill,
        pressedContainerColor = BenflixColors.FocusFill,
        pressedContentColor = BenflixColors.OnFocusFill,
    )

/** The aqua ring round a lit button. Pass the button's own [shape] when it isn't round. */
@Composable
fun glowfinButtonBorder(shape: Shape = CircleShape): ButtonBorder =
    ButtonDefaults.border(
        focusedBorder = Border(border = BorderStroke(FocusRingWidth, BenflixColors.Focus), shape = shape),
        pressedBorder = Border(border = BorderStroke(FocusRingWidth, BenflixColors.Focus), shape = shape),
    )
