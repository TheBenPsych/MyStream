package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.theme.Glowfin

/** Anton: the tall cinema-poster numbers of the Top 10 rows (Ben's pick T1, ui-8) */
val GlowfinNumberFont = FontFamily(Font(R.font.glowfin_anton, FontWeight.Normal))

/**
 * Ben's Top 10 look ("Glow outline", 2026-11-22): a hollow Anton number outlined in the Glowfin colours running
 * left to right (violet > blue > aqua), as tall as the poster. When the poster beside it has focus ([lit]) the
 * number fills in with the same colours and a soft glow. Ben's rule for every Top 10 row: the number sits fully
 * BESIDE its poster (never tucked behind it), and every number gets the same width box. "10" is drawn slimmer so
 * it is as tall as the single numbers and fits the same box. Cheap to draw: one text layout, no blur.
 */
@Composable
fun GlowRankNumber(
    rank: Int,
    posterHeight: Dp,
    modifier: Modifier = Modifier,
    lit: Boolean = false,
) {
    val measurer = rememberTextMeasurer(cacheSize = 1)
    val density = LocalDensity.current
    val numberHeight = posterHeight * RANK_HEIGHT_SHARE
    val fontSize = with(density) { (numberHeight / ANTON_CAP_HEIGHT).toSp() }
    Spacer(
        modifier
            .size(rankBoxWidth(posterHeight), posterHeight)
            .drawWithCache {
                val layout =
                    measurer.measure(
                        text = rank.toString(),
                        style = TextStyle(fontFamily = GlowfinNumberFont, fontSize = fontSize),
                    )
                val stroke = (posterHeight * OUTLINE_SHARE).toPx().coerceAtLeast(1.5f)
                val textWidth = layout.size.width.toFloat()
                // Squeeze wide numbers ("10") sideways so every number fills the same box at the same height
                val squeeze = ((size.width - stroke) / textWidth).coerceAtMost(1f)
                val x = (size.width - textWidth) / 2f
                // Baseline on the poster's bottom edge (less half the outline, so nothing is cut off)
                val y = size.height - layout.firstBaseline - stroke / 2f
                val brush =
                    Brush.horizontalGradient(
                        0f to Glowfin.Violet,
                        0.5f to Glowfin.Blue,
                        1f to Glowfin.Aqua,
                        startX = x,
                        endX = x + textWidth,
                    )
                val glow = Shadow(Glowfin.Aqua.copy(alpha = 0.5f), Offset.Zero, 10.dp.toPx())
                val outline = Stroke(width = stroke, join = StrokeJoin.Round)
                onDrawBehind {
                    withTransform({ scale(squeeze, 1f, pivot = Offset(size.width / 2f, 0f)) }) {
                        if (lit) {
                            drawText(layout, brush, Offset(x, y), shadow = glow)
                        } else {
                            drawText(layout, brush, Offset(x, y), drawStyle = outline)
                        }
                    }
                }
            },
    )
}

/** The same width for every number's box (wide enough for "10" once squeezed), from the poster's height */
fun rankBoxWidth(posterHeight: Dp): Dp = posterHeight * RANK_BOX_SHARE

/** The number stands nearly the poster's full height */
private const val RANK_HEIGHT_SHARE = 0.92f

/** Anton's capital (and number) height, as a share of its size */
private const val ANTON_CAP_HEIGHT = 0.86f

/** Box width as a share of the poster's height (about the same room the old half-height numbers took) */
private const val RANK_BOX_SHARE = 0.5f

/** The outline's thickness as a share of the poster's height (about 2.5 dp on a TV poster) */
private const val OUTLINE_SHARE = 0.017f

/** The gap between a number and its poster */
val RankGap = 6.dp

/**
 * A Top 10 poster with its [GlowRankNumber] fully beside it, in the number's equal-width box (Ben's rule).
 * [posterHeight] is the picture's height (not counting any name under it), so the number's foot lines up
 * with the picture's bottom edge.
 */
@Composable
fun GlowRanked(
    rank: Int,
    posterHeight: Dp,
    modifier: Modifier = Modifier,
    poster: @Composable () -> Unit,
) {
    var lit by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.Top, modifier = modifier.onFocusChanged { lit = it.hasFocus }) {
        GlowRankNumber(rank = rank, posterHeight = posterHeight, lit = lit)
        Spacer(Modifier.width(RankGap))
        poster()
    }
}
