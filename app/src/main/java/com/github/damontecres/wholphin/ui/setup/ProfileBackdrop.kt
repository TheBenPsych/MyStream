package com.github.damontecres.wholphin.ui.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import com.github.damontecres.wholphin.ui.theme.Glowfin

/*
 * The background behind "Who's watching?" and the sign-in screens: Glowfin's "night sea" (Ben's pick,
 * 2026-11-18, sheet benflix/login-background/login-bg-1-design.html). A dark night sky with a few faint
 * stars, a violet glow on the horizon, calm water that catches that glow, and one Glowfin fin on the
 * horizon with its mirror image fading into the water.
 *
 * Ben: NO animations (it slowed things down) and no lines on the water. It is one still picture, drawn
 * once and kept in its own layer, so focus moving between profiles never redraws it. Nothing is loaded
 * from the Tower, so it shows instantly even when the Tower is off.
 */
@Composable
fun NightSea(modifier: Modifier = Modifier) {
    Box(
        modifier
            // Own layer: the picture is drawn once and reused while the profiles above it change
            .graphicsLayer {}
            .drawWithCache {
                val w = size.width
                val h = size.height
                val wide = w > h
                val horizon = h * (if (wide) 0.74f else 0.78f)
                val sky =
                    Brush.verticalGradient(
                        0f to SKY_TOP,
                        0.55f to SKY_MID,
                        1f to SKY_LOW,
                        startY = 0f,
                        endY = horizon,
                    )
                val water = Brush.verticalGradient(0f to WATER_TOP, 1f to WATER_LOW, startY = horizon, endY = h)
                val haze =
                    Brush.radialGradient(
                        0f to Glowfin.Violet.copy(alpha = .30f),
                        0.45f to Glowfin.Blue.copy(alpha = .12f),
                        1f to Color.Transparent,
                        center = Offset(w / 2, horizon),
                        radius = w * .30f,
                    )
                val pool =
                    Brush.radialGradient(
                        0f to Glowfin.Violet.copy(alpha = .22f),
                        0.55f to Glowfin.Blue.copy(alpha = .08f),
                        1f to Color.Transparent,
                        center = Offset(w / 2, horizon),
                        radius = w * .40f,
                    )
                val line =
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.3f to Glowfin.Aqua.copy(alpha = .55f),
                        0.7f to Glowfin.Violet.copy(alpha = .55f),
                        1f to Color.Transparent,
                    )
                val starDots = STARS.map { (x, y, r) -> Triple(Offset(x * w, y * (horizon * .88f)), r, .3f + r * .15f) }
                val dotScale = (minOf(w, h) / 720f).coerceAtLeast(1f)

                // The fin: the logo's own shape (a 100 x 100 box, waterline at 64)
                val finW = w * (if (wide) 0.09f else 0.20f)
                val unit = finW / FIN_BOX_W
                val finLeft = w - w * (if (wide) 0.08f else 0.10f) - finW
                val fin = finPath()
                val finGlow =
                    Brush.radialGradient(
                        0f to Glowfin.Blue.copy(alpha = .35f),
                        1f to Color.Transparent,
                        center = Offset(finLeft + finW / 2, horizon),
                        radius = finW * .55f,
                    )
                val reflectionFade =
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = .75f),
                        0.45f to Color.White.copy(alpha = .30f),
                        1f to Color.Transparent,
                        startY = horizon,
                        endY = horizon + 44f * unit,
                    )
                val layerPaint = Paint()

                onDrawBehind {
                    drawRect(sky, size = size.copy(height = horizon))
                    starDots.forEach { (c, r, a) -> drawCircle(Color.White.copy(alpha = a), radius = r * dotScale, center = c) }
                    // The violet glow on the horizon and its soft pool on the water, both squashed flat
                    squashed(horizon, .45f) { drawRect(haze, topLeft = Offset(0f, horizon - w * .3f), size = size.copy(height = w * .3f)) }
                    drawRect(water, topLeft = Offset(0f, horizon), size = size.copy(height = h - horizon))
                    squashed(horizon, .35f) { drawRect(pool, topLeft = Offset(0f, horizon), size = size.copy(height = w * .4f)) }
                    drawRect(line, topLeft = Offset(0f, horizon), size = size.copy(height = dotScale))

                    // Glow where the fin meets the water
                    squashed(horizon, .2f) {
                        drawRect(
                            finGlow,
                            topLeft = Offset(finLeft - finW, horizon - finW),
                            size =
                                size.copy(
                                    width = finW * 3,
                                    height =
                                        finW * 2,
                                ),
                        )
                    }
                    // Mirror image: the fin flipped (a little shorter) and faded out going down
                    val area = Rect(finLeft - finW, horizon, finLeft + finW * 2, horizon + 50f * unit)
                    drawContext.canvas.saveLayer(area, layerPaint)
                    withTransform({
                        translate(finLeft, horizon)
                        scale(unit, -unit * .85f, pivot = Offset.Zero)
                        translate(0f, -FIN_WATERLINE)
                    }) {
                        drawPath(fin, finBrushLocal())
                    }
                    drawRect(reflectionFade, topLeft = area.topLeft, size = area.size, blendMode = BlendMode.DstIn)
                    drawContext.canvas.restore()
                    // The fin itself
                    withTransform({
                        translate(finLeft, horizon)
                        scale(unit, unit, pivot = Offset.Zero)
                        translate(0f, -FIN_WATERLINE)
                    }) {
                        drawPath(fin, finBrushLocal())
                    }
                }
            },
    )
}

/** Draws [block] squashed up and down around the line at [y], to turn round glows into flat ones */
private inline fun DrawScope.squashed(
    y: Float,
    amount: Float,
    crossinline block: DrawScope.() -> Unit,
) {
    withTransform({ scale(1f, amount, pivot = Offset(size.width / 2, y)) }) { block() }
}

/** The logo's violet > blue > aqua, in the fin's own 100 x 100 box */
private fun finBrushLocal(): Brush =
    Brush.linearGradient(
        0f to Glowfin.Violet,
        0.6f to Glowfin.Blue,
        1f to Glowfin.Aqua,
        start = Offset(17f, 64f),
        end = Offset(81f, 13f),
    )

/** The Glowfin fin (same curve as the logo): M17 64 C29 45 46 24 79 13 C72 31 70 48 81 64 Z */
private fun finPath(): Path =
    Path().apply {
        moveTo(17f, 64f)
        cubicTo(29f, 45f, 46f, 24f, 79f, 13f)
        cubicTo(72f, 31f, 70f, 48f, 81f, 64f)
        close()
    }

private const val FIN_BOX_W = 100f
private const val FIN_WATERLINE = 64f

private val SKY_TOP = Color(0xFF0B0D1A)
private val SKY_MID = Color(0xFF10132A)
private val SKY_LOW = Color(0xFF1B1C40)
private val WATER_TOP = Color(0xFF0A0C1C)
private val WATER_LOW = Color(0xFF070811)

/** A few faint stars: x and y as parts of the sky, and a size */
private val STARS =
    listOf(
        Triple(.12f, .18f, 1f),
        Triple(.28f, .40f, .9f),
        Triple(.47f, .12f, 1.2f),
        Triple(.66f, .30f, .9f),
        Triple(.83f, .15f, 1.3f),
        Triple(.91f, .44f, .9f),
        Triple(.06f, .52f, .9f),
        Triple(.57f, .55f, .8f),
        Triple(.36f, .08f, .8f),
        Triple(.74f, .62f, .8f),
    )
