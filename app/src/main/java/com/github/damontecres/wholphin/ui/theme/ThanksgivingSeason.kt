package com.github.damontecres.wholphin.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import java.time.LocalDate
import java.time.Month
import kotlin.math.roundToInt

/**
 * Benflix Thanksgiving look (approved 2026-11-16, thread "Design a Thanksgiving theme for Benflix"): on from
 * 16 to 30 November on TV, phone and web, unless it's switched off in Settings > Look. Christmas takes over on
 * 1 December. The same dates as the web's feast.js, so every screen matches.
 */
object ThanksgivingSeason {
    /** Settings > Look > Thanksgiving look is off (set by MainActivity from the saved settings) */
    var lookOff by mutableStateOf(false)

    fun isSeason(date: LocalDate = Today.date()): Boolean = date.month == Month.NOVEMBER && date.dayOfMonth >= 16

    fun on(date: LocalDate = Today.date()): Boolean = !lookOff && isSeason(date) && !Friday13.on(date)

    const val GREETING = "Happy Thanksgiving"
}

/**
 * The Thanksgiving look for the rest of the app: pumpkin orange for selected things, harvest brown for other
 * buttons, switches and panels, white words on every door, a different harvest charm on each door, and small
 * harvest touches that each appear once (candle glow behind Play, a gold leaf at the end of progress, a red
 * leaf turning in the loading ring, an acorn next to switches that are on, and a brass tack on notices).
 * Not on Kids (Sky and Sunshine stay).
 */
object TgLook {
    val on: Boolean get() = ThanksgivingSeason.on() && !KidsLook.on

    val Orange = Color(0xFFE2702A)
    val Brown = Color(0xFF7A4A24)
    val Panel = Color(0xFF24170F)
    val PanelHigh = Color(0xFF2E1E14)
    val Raised = Color(0xFF2A1B11)
    val Line = Color(0xFF3A2618)
    val CandleGlow = Color(0xFFFFA850)
}

/** The curly holiday font (Berkshire Swash, OFL) used for holiday greetings */
val HolidayFont = FontFamily(Font(R.font.holiday_berkshireswash))

/** The harvest charm that hangs on each door (all on the same twine and brass tack) */
private fun BenflixDoor.charm(): Int =
    when (this) {
        BenflixDoor.MOVIES -> R.drawable.tg_charm_movies
        BenflixDoor.SHOWS -> R.drawable.tg_charm_shows
        BenflixDoor.LIVE_TV -> R.drawable.tg_charm_live
        BenflixDoor.GAMES -> R.drawable.tg_charm_games
        BenflixDoor.MUSIC -> R.drawable.tg_charm_music
        BenflixDoor.BOOKS -> R.drawable.tg_charm_books
    }

/**
 * Thanksgiving look on a Home door: its harvest charm hanging from a brass tack on the top edge, near the
 * right (as on the approved sheet: a 240 x 220 door with the charm's box from x 116 to 236 and y -6 to 126).
 */
fun Modifier.tgDoorCharm(door: BenflixDoor): Modifier =
    if (!TgLook.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(door.charm())
            drawWithContent {
                drawContent()
                val k = minOf(size.width, size.height * 240f / 220f) / 240f
                val w = 120f * k
                val h = w * img.height / img.width
                drawImageAt(img, size.width - 4f * k - w, -6f * k, w, h)
            }
        }
    }

/** Thanksgiving look: a soft warm candle glow behind Play */
fun Modifier.tgCandleGlow(): Modifier =
    if (!TgLook.on) {
        this
    } else {
        drawBehind {
            val r = maxOf(size.width, size.height) * .85f
            drawCircle(
                brush =
                    Brush.radialGradient(
                        0f to TgLook.CandleGlow.copy(alpha = .38f),
                        .55f to TgLook.CandleGlow.copy(alpha = .14f),
                        1f to Color.Transparent,
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = r,
                    ),
                radius = r,
                center = Offset(size.width / 2f, size.height / 2f),
            )
        }
    }

/** Thanksgiving look: notices are pinned with a brass tack on their top-left corner */
fun Modifier.tgTack(size: Dp = 22.dp): Modifier =
    if (!TgLook.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(R.drawable.tg_tack)
            drawWithContent {
                drawContent()
                val s = size.toPx()
                drawImageAt(img, -s * .25f, -s * .25f, s, s)
            }
        }
    }

/** Thanksgiving look: a gold maple leaf riding the end of a progress line ([share] of the way along) */
fun Modifier.tgProgressLeaf(
    share: Float,
    leaf: Dp = 22.dp,
): Modifier =
    if (!TgLook.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(R.drawable.tg_leaf_gold)
            drawWithContent {
                drawContent()
                val s = leaf.toPx()
                val x = (size.width * share.coerceIn(0f, 1f)) - s / 2f
                // sits on the line, leaning up into the picture so it isn't cut off at the card's edge
                drawImageAt(img, x.coerceIn(0f, size.width - s), size.height - s * .8f, s, s)
            }
        }
    }

/** Thanksgiving look: the small acorn shown next to a switch that's on */
@Composable
fun TgAcorn(size: Dp = 22.dp) {
    Image(
        painter = painterResource(R.drawable.tg_acorn),
        contentDescription = null,
        modifier = Modifier.size(size * .8f, size),
    )
}

/** Thanksgiving look: the red maple leaf that turns slowly inside the loading ring */
@Composable
fun TgLoadingLeaf(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.tg_leaf_red),
        contentDescription = null,
        modifier = modifier,
    )
}

private fun DrawScope.drawImageAt(
    img: ImageBitmap,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
) {
    drawImage(
        image = img,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
        dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
        filterQuality = FilterQuality.Medium,
    )
}
