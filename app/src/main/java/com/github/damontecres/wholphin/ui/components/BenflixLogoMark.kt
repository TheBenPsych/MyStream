package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor

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

/** The Glowfin fin mark (approved 2026-11-18, "F2": the fin above the water with its glowing reflection), [size] wide */
@Composable
fun BenflixMark(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size),
    ) {
        Image(
            painter = painterResource(R.drawable.glowfin_mark),
            contentDescription = null,
            modifier = Modifier.size(size),
        )
    }
}

val GlowfinPurple = Color(0xFFA55BE0)
val GlowfinBlue = Color(0xFF1FA8E6)
val GlowfinCyan = Color(0xFF3FE0E8)

/** The purple-to-cyan fade used on "fin" and the Kids tag */
val GlowfinFade: Brush get() = Brush.horizontalGradient(0f to GlowfinPurple, 0.6f to GlowfinBlue, 1f to GlowfinCyan)

private val SoraFont =
    FontFamily(
        Font(R.font.glowfin_sora_light, FontWeight.Light),
        Font(R.font.glowfin_sora_semibold, FontWeight.SemiBold),
    )

/**
 * The word "Glowfin": "Glow" light and white, "fin" bolder in the purple-to-cyan fade.
 * In Kids mode a small "kids" tag sits beside it, its bottom edge on the bottom line of the letters
 * (Ben's pick A, 2026-11-18).
 */
@Composable
fun GlowfinWord(
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    wordColor: Color = BenflixColors.Text,
    kidsTag: Boolean = KidsMode.active.collectAsState().value,
) {
    val style = TextStyle(fontFamily = SoraFont, fontSize = fontSize, lineHeight = fontSize)
    Row(modifier = modifier) {
        Text(
            text = "Glow",
            style = style.copy(fontWeight = FontWeight.Light, color = wordColor),
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = "fin",
            style = style.copy(fontWeight = FontWeight.SemiBold, brush = GlowfinFade),
            maxLines = 1,
            modifier = Modifier.alignByBaseline(),
        )
        if (kidsTag) {
            val tagSize = fontSize * 0.36f
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .alignBy { it.measuredHeight }
                        .padding(start = with(LocalDensity.current) { (fontSize * 0.22f).toDp() })
                        .background(GlowfinFade, CircleShape)
                        .padding(horizontal = with(LocalDensity.current) { (fontSize * 0.2f).toDp() }, vertical = 1.dp),
            ) {
                Text(
                    text = "kids",
                    style =
                        TextStyle(
                            fontFamily = SoraFont,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = tagSize,
                            lineHeight = tagSize * 1.25f,
                            color = Color(0xFF0B0C12),
                        ),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The Glowfin logo: the fin mark beside the word "Glowfin".
 * [height] is the size of the word; the fin is a little taller, like the approved drawing.
 * [extra] can add a small word after it.
 */
@Composable
fun BenflixWordLogo(
    modifier: Modifier = Modifier,
    height: Dp = 26.dp,
    wordColor: Color = BenflixColors.Text,
    extra: String? = null,
    /** Extra modifier for just the fin (the intro and the Home doors need to know where it is) */
    markModifier: Modifier = Modifier,
) {
    // The logo keeps its own size even when Easy read makes the words bigger
    val fontScale = LocalDensity.current.fontScale
    val fontSize = (height.value / fontScale).sp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(height * 0.3f),
        modifier = modifier.semantics { contentDescription = "Glowfin" },
    ) {
        BenflixMark(size = height * 1.3f, modifier = markModifier)
        GlowfinWord(fontSize = fontSize, wordColor = wordColor)
        if (extra != null) {
            Text(
                text = extra,
                style =
                    TextStyle(
                        fontFamily = SoraFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = fontSize * 0.62f,
                        lineHeight = fontSize,
                        color = BenflixColors.Muted,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
