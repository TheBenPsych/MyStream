package com.github.damontecres.wholphin.ui.detail.title

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.theme.BenflixType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Glowfin scores as icons (Ben 2026-11-24, picked "Bare icons in a row" under the year and time): the age rating in
 * a thin white outline, then each score as a small icon and a white number, no chips and no % signs.
 * Gold star = IMDb, red tomato (60 and up) or green splat (under 60) = Rotten Tomatoes, M = Metacritic,
 * AL = AniList, MAL = MyAnimeList. Drawn with plain shapes, nothing to download.
 */

/** One row of bare score icons. Nothing is drawn when there's no age rating and no score. */
@Composable
fun ScoreIcons(
    rating: String?,
    scores: List<StripPart>,
    modifier: Modifier = Modifier,
) {
    val shown = scores.filter { !it.value.isNullOrBlank() }.take(3)
    if (rating.isNullOrBlank() && shown.isEmpty()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier,
    ) {
        if (!rating.isNullOrBlank()) {
            Text(
                text = rating,
                color = Color.White,
                fontSize = BenflixType.tag,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                modifier =
                    Modifier
                        .background(AGE_BACK, AGE_SHAPE)
                        .border(1.5.dp, Color.White.copy(alpha = .9f), AGE_SHAPE)
                        .padding(horizontal = 6.dp),
            )
        }
        shown.forEach { part ->
            val number =
                part.value
                    .orEmpty()
                    .removeSuffix("%")
                    .trim()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                ScoreIcon(label = part.label, number = number, size = ICON_SIZE)
                Text(
                    text = number,
                    color = Color.White,
                    fontSize = BenflixType.body,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    style = TextStyle(shadow = NUMBER_EDGE),
                )
            }
        }
    }
}

/** The small icon for one score site */
@Composable
fun ScoreIcon(
    label: String,
    number: String,
    size: Dp,
) {
    val l = label.lowercase()
    when {
        l == "imdb" -> {
            Canvas(Modifier.size(size)) { drawStar(GOLD) }
        }

        "rotten" in l || l == "critics" -> {
            val fresh = (number.toFloatOrNull() ?: 0f) >= FRESH_FROM
            Canvas(Modifier.size(size)) { if (fresh) drawTomato() else drawSplat() }
        }

        "metacritic" in l -> {
            Canvas(Modifier.size(size)) { drawMetacritic() }
        }

        l == "anilist" -> {
            LetterBox(size = size, back = Color(0xFF152232)) {
                buildAnnotatedString {
                    withStyle(
                        androidx.compose.ui.text
                            .SpanStyle(color = Color.White),
                    ) { append("A") }
                    withStyle(
                        androidx.compose.ui.text
                            .SpanStyle(color = Color(0xFF02A9FF)),
                    ) { append("L") }
                }
            }
        }

        l == "mal" || "myanimelist" in l -> {
            LetterBox(size = size, back = Color(0xFF2E51A2), wide = true) {
                buildAnnotatedString {
                    withStyle(
                        androidx.compose.ui.text
                            .SpanStyle(color = Color.White),
                    ) { append("MAL") }
                }
            }
        }

        else -> {
            Text(text = label, color = Color.White, fontSize = BenflixType.tag, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun LetterBox(
    size: Dp,
    back: Color,
    wide: Boolean = false,
    text: () -> androidx.compose.ui.text.AnnotatedString,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(height = size, width = if (wide) size * 1.5f else size)
                .background(back, RoundedCornerShape(size * .22f)),
    ) {
        Text(
            text = text(),
            fontSize = (size.value * (if (wide) .42f else .5f)).sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

private fun DrawScope.drawStar(color: Color) {
    val c = Offset(size.width / 2f, size.height * .54f)
    val outer = size.minDimension * .5f
    val inner = outer * .45f
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val a = -PI / 2 + i * PI / 5
        val x = c.x + (r * cos(a)).toFloat()
        val y = c.y + (r * sin(a)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawTomato() {
    val r = size.minDimension * .42f
    drawCircle(TOMATO_RED, radius = r, center = Offset(size.width / 2f, size.height * .56f))
    // the green leaf on top
    drawOval(
        LEAF_GREEN,
        topLeft = Offset(size.width * .28f, size.height * .06f),
        size = Size(size.width * .44f, size.height * .2f),
    )
}

private fun DrawScope.drawSplat() {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension * .3f
    drawCircle(SPLAT_GREEN, radius = r, center = c)
    // a few blobs around the middle make the splat shape
    for (i in 0 until 7) {
        val a = i * 2 * PI / 7 + .3
        val d = r * (if (i % 2 == 0) 1.1f else .9f)
        drawCircle(
            SPLAT_GREEN,
            radius = r * (if (i % 2 == 0) .38f else .3f),
            center = Offset(c.x + (d * cos(a)).toFloat(), c.y + (d * sin(a)).toFloat()),
        )
    }
}

private fun DrawScope.drawMetacritic() {
    val r = size.minDimension / 2f
    val c = Offset(size.width / 2f, size.height / 2f)
    drawCircle(Color.Black, radius = r, center = c)
    drawCircle(MC_YELLOW, radius = r - 1.dp.toPx(), center = c, style = Stroke(width = 1.6.dp.toPx()))
    val w = size.width
    val h = size.height
    val m =
        Path().apply {
            moveTo(w * .3f, h * .7f)
            lineTo(w * .3f, h * .32f)
            lineTo(w * .45f, h * .52f)
            lineTo(w * .58f, h * .32f)
            lineTo(w * .58f, h * .7f)
            moveTo(w * .58f, h * .32f)
            lineTo(w * .74f, h * .7f)
        }
    drawPath(m, Color.White, style = Stroke(width = 1.8.dp.toPx()))
}

private val ICON_SIZE = 16.dp
private val AGE_SHAPE = RoundedCornerShape(5.dp)
private val AGE_BACK = Color(0x730B0D1A)
private val NUMBER_EDGE = Shadow(color = Color(0xB3000000), offset = Offset(0f, 1f), blurRadius = 6f)
private val GOLD = Color(0xFFF5C518)
private val TOMATO_RED = Color(0xFFFA320A)
private val LEAF_GREEN = Color(0xFF00912D)
private val SPLAT_GREEN = Color(0xFF65B32E)
private val MC_YELLOW = Color(0xFFFFCC34)

/** Rotten Tomatoes counts 60 and up as fresh */
private const val FRESH_FROM = 60f
