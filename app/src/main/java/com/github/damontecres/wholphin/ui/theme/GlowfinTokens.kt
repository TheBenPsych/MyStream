package com.github.damontecres.wholphin.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Glowfin's base look (Ben's "Still Water" pick, Nov 2026): one deep night-blue background, the logo's
 * violet > blue > aqua glow, and glow only on the thing the remote is on. Every screen picks from these.
 */
object Glowfin {
    /** The background everything sits on */
    val Ink = Color(0xFF0B0D1A)

    /** Menus, panels and the open side menu */
    val Deep = Color(0xFF12152A)

    /** Raised things: chips, cards without a picture, sheets */
    val Raise = Color(0xFF1A1E38)

    /** Thin lines and panel edges */
    val Line = Color(0xFF262A45)

    /** The row the remote is on in a list */
    val FocusRow = Color(0xFF222645)

    /** Main words */
    val Text = Color(0xFFECEEFB)

    /** Quiet words */
    val Dim = Color(0xFF9AA0C3)

    /** The logo's three colours */
    val Violet = Color(0xFFA55BE0)
    val Blue = Color(0xFF1FA8E6)
    val Aqua = Color(0xFF3FE0E8)

    /** A single colour for places that can't take the glow (filled buttons): the glow's violet-blue middle */
    val FillMid = Color(0xFF6C6FE8)

    /** The glow itself, corner to corner, for buttons, the menu pill and the picked menu circle */
    val Glow: Brush
        get() = Brush.linearGradient(0f to Violet, 0.6f to Blue, 1f to Aqua)

    /** The glow left to right (the open menu's pill, progress lines) */
    val GlowAcross: Brush
        get() = Brush.horizontalGradient(0f to Violet, 0.6f to Blue, 1f to Aqua)
}

/**
 * Ben's pick "Glow ring" for cards: a thin aqua ring (the card's border) with a soft violet glow around it,
 * and nothing grows. The glow is a few see-through outlines drawn once per size, so it costs almost nothing
 * on older TVs (no blur, nothing that keeps running). Put it on the card's modifier, before its size.
 */
fun Modifier.glowFocus(
    shape: Shape = BenflixShapes.Card,
    spread: Dp = 12.dp,
): Modifier =
    composed {
        var focused by remember { mutableStateOf(false) }
        this
            .onFocusChanged { focused = it.hasFocus }
            .drawWithCache {
                val steps = GLOW_STEPS
                val spreadPx = spread.toPx()
                val stroke = spreadPx / steps + 1f
                val outlines: List<Pair<Float, Outline>> =
                    (1..steps).map { i ->
                        val out = spreadPx * i / steps
                        out to shape.createOutline(Size(size.width + out * 2, size.height + out * 2), layoutDirection, this)
                    }
                onDrawBehind {
                    if (focused) {
                        // Glowfin for Kids (K1): the ring glows mint instead of violet
                        val glow = KidsLook.palette?.accent ?: Glowfin.Violet
                        outlines.forEachIndexed { index, (out, outline) ->
                            val a = GLOW_ALPHA * (1f - index.toFloat() / steps)
                            translate(-out, -out) {
                                drawOutline(outline, glow.copy(alpha = a), style = Stroke(width = stroke))
                            }
                        }
                    }
                }
            }
    }

private const val GLOW_STEPS = 5
private const val GLOW_ALPHA = .34f

/** Where the glow's gradient runs for a box of [size] (bottom-left to top-right, like the logo) */
fun glowBrushFor(size: Size): Brush =
    Brush.linearGradient(
        0f to Glowfin.Violet,
        0.6f to Glowfin.Blue,
        1f to Glowfin.Aqua,
        start = Offset(0f, size.height),
        end = Offset(size.width, 0f),
    )
