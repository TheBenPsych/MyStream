package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.theme.Glowfin

/** Anton: the tall cinema-poster numbers of the Top 10 rows (Ben's pick T1, ui-8) */
val GlowfinNumberFont = FontFamily(Font(R.font.glowfin_anton, FontWeight.Normal))

/**
 * Ben's Top 10 look (T1, 2026-11-18): a tall Anton number filled with the Glowfin violet > blue > aqua glow.
 * Ben's rule for every Top 10 row: the number sits fully BESIDE its poster (never tucked behind it), and every
 * number gets the same width box, so the thin "1" is as easy to see as "10". Drawn once, it never moves.
 * Used by Movies, Shows, Anime, Home, and later the Requests "Top 10 to ask for" row.
 */
@Composable
fun GlowRankNumber(
    rank: Int,
    posterHeight: Dp,
    modifier: Modifier = Modifier,
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
                        style =
                            TextStyle(
                                fontFamily = GlowfinNumberFont,
                                fontSize = fontSize,
                                // "10" squeezes in a touch so it fits the same box as the single numbers
                                letterSpacing = if (rank >= 10) (-0.03).em else 0.em,
                            ),
                    )
                val x = (size.width - layout.size.width) / 2f
                val top = size.height - numberHeight.toPx()
                // Baseline on the poster's bottom edge
                val y = size.height - layout.firstBaseline
                val brush =
                    Brush.linearGradient(
                        0f to Glowfin.Violet,
                        0.6f to Glowfin.Blue,
                        1f to Glowfin.Aqua,
                        start = Offset(x, top),
                        end = Offset(x + layout.size.width * 0.5f, size.height),
                    )
                val shadow = Shadow(Glowfin.Violet.copy(alpha = 0.45f), Offset.Zero, 6.dp.toPx())
                onDrawBehind { drawText(layout, brush, Offset(x, y), shadow = shadow) }
            },
    )
}

/** The same width for every number's box (wide enough for "10"), from the poster's height */
fun rankBoxWidth(posterHeight: Dp): Dp = posterHeight * RANK_HEIGHT_SHARE * RANK_BOX_SHARE

/** The number stands about half the poster's height */
private const val RANK_HEIGHT_SHARE = 0.52f

/** Anton's capital (and number) height, as a share of its size */
private const val ANTON_CAP_HEIGHT = 0.86f

/** "10" in Anton is a little less wide than tall: the box is just wider than that */
private const val RANK_BOX_SHARE = 1.0f

/** The gap between a number and its poster */
val RankGap = 6.dp
