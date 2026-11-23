package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.theme.KidsLook
import com.github.damontecres.wholphin.ui.touch.rememberIsTv

/**
 * Glowfin new look "Friendly empty and error pages" (Ben 2026-11-22): a soft little picture for pages that have
 * nothing to show or hit a problem, instead of grey text. Calm night water with the logo's glow and a fin, drawn
 * once per size from a few shapes (no pictures to load, no blur), so it costs a slow TV nothing. Kids gets its
 * own colours.
 */
fun Modifier.softArt(): Modifier =
    drawWithCache {
        val kids = KidsLook.palette
        val sky = kids?.panel ?: Glowfin.Deep
        val glowA = kids?.accent ?: Glowfin.Violet
        val glowB = kids?.accent?.copy(alpha = .6f) ?: Glowfin.Blue
        val water = kids?.accent ?: Glowfin.Aqua
        val w = size.width
        val h = size.height
        val back =
            Brush.radialGradient(
                0f to glowA.copy(alpha = .55f),
                0.45f to glowB.copy(alpha = .28f),
                1f to sky,
                center = Offset(w * .72f, h * .32f),
                radius = maxOf(w, h) * .85f,
            )
        val sea = Brush.verticalGradient(0f to Color.Transparent, 1f to Glowfin.Ink.copy(alpha = .7f), startY = h * .55f, endY = h)
        val waves =
            (0 until 3).map { i ->
                val y = h * (.66f + i * .1f)
                val amp = h * .025f
                Path().apply {
                    moveTo(w * (.08f + i * .06f), y)
                    var x = w * (.08f + i * .06f)
                    val step = w * .1f
                    var up = true
                    while (x < w * (.92f - i * .06f)) {
                        quadraticTo(x + step / 2f, if (up) y - amp else y + amp, x + step, y)
                        x += step
                        up = !up
                    }
                }
            }
        val fin =
            Path().apply {
                val baseY = h * .64f
                moveTo(w * .54f, baseY)
                quadraticTo(w * .60f, h * .38f, w * .70f, h * .30f)
                quadraticTo(w * .67f, h * .48f, w * .74f, baseY)
                close()
            }
        val finBrush =
            Brush.linearGradient(
                0f to glowA,
                .6f to glowB,
                1f to water,
                start = Offset(w * .54f, h * .64f),
                end =
                    Offset(w * .74f, h * .3f),
            )
        val stroke = maxOf(2f, h * .018f)
        onDrawBehind {
            drawRect(sky)
            drawRect(back)
            drawPath(fin, finBrush, alpha = .85f)
            drawRect(sea)
            waves.forEachIndexed { i, p ->
                drawPath(p, water.copy(alpha = .55f - i * .15f), style = Stroke(width = stroke, cap = StrokeCap.Round))
            }
        }
    }

/** The soft picture as a rounded tile, with [content] (usually the page's icon) over it, up and to the left */
@Composable
fun SoftArtTile(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    content: @Composable () -> Unit = {},
) {
    val isTv = rememberIsTv()
    val w = width ?: if (isTv) 240.dp else 180.dp
    val shape = RoundedCornerShape(if (isTv) 22.dp else 18.dp)
    Box(
        // The icon sits up and to the left, so it doesn't land on the fin
        contentAlignment = BiasAlignment(-.42f, -.3f),
        modifier =
            modifier
                .size(width = w, height = w * .62f)
                .clip(shape)
                .softArt(),
    ) {
        content()
    }
}
