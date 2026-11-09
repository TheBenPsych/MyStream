package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixFont

/** The four logo squares in door order: top left Movies, top right Shows, bottom left Live TV, bottom right Games */
val LogoSquareColors = BenflixDoor.entries.take(4).map { it.color }

/** Gap between the squares, as a part of the mark's width */
const val LOGO_GAP = 0.1f

/** Corner roundness of each square, as a part of the mark's width (matches the door corners) */
const val LOGO_CORNER = 0.17f

/**
 * Draws the soft 2 x 2 squares filling a square of side [side] at [topLeft].
 * [squareAlpha] lets the intro fade squares one at a time.
 */
fun DrawScope.drawLogoSquares(
    topLeft: Offset = Offset.Zero,
    side: Float = size.minDimension,
    squareAlpha: (Int) -> Float = { 1f },
) {
    val gap = side * LOGO_GAP
    val sq = (side - gap) / 2f
    val r = CornerRadius(side * LOGO_CORNER, side * LOGO_CORNER)
    LogoSquareColors.forEachIndexed { i, color ->
        val a = squareAlpha(i)
        if (a <= 0f) return@forEachIndexed
        val x = topLeft.x + if (i % 2 == 0) 0f else sq + gap
        val y = topLeft.y + if (i < 2) 0f else sq + gap
        drawRoundRect(color = color, topLeft = Offset(x, y), size = Size(sq, sq), cornerRadius = r, alpha = a)
    }
}

/** Just the four squares (app icon style), [size] wide */
@Composable
fun BenflixMark(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) { drawLogoSquares() }
}

/**
 * The Benflix logo (redesign): the four soft door-colour squares beside "Benflix" in Outfit ExtraBold.
 * [height] is the size of the word; the squares are a little smaller, like the approved drawing.
 * [word] can add a small word after it, like "kids".
 */
@Composable
fun BenflixWordLogo(
    modifier: Modifier = Modifier,
    height: Dp = 26.dp,
    wordColor: Color = BenflixColors.Text,
    extra: String? = null,
    /** Extra modifier for just the squares (the intro and the Home doors need to know where they are) */
    markModifier: Modifier = Modifier,
) {
    // The logo keeps its own size even when Easy read makes the words bigger
    val fontScale = LocalDensity.current.fontScale
    val fontSize = (height.value / fontScale).sp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(height * 0.32f),
        modifier = modifier.semantics { contentDescription = "Benflix" },
    ) {
        BenflixMark(size = height * 0.92f, modifier = markModifier)
        Text(
            text = "Benflix",
            style =
                TextStyle(
                    fontFamily = BenflixFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = fontSize,
                    letterSpacing = (-0.02).em,
                    lineHeight = fontSize,
                    color = wordColor,
                ),
            maxLines = 1,
        )
        if (extra != null) {
            Text(
                text = extra,
                style =
                    TextStyle(
                        fontFamily = BenflixFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSize * 0.62f,
                        lineHeight = fontSize,
                        color = BenflixColors.Muted,
                    ),
                maxLines = 1,
            )
        }
    }
}
