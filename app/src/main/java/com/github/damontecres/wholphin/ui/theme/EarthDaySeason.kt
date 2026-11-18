package com.github.damontecres.wholphin.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.easter
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Benflix Earth Day look (approved 2026-11-17, thread "Design an Earth Day theme for Benflix"): Earth week, seven
 * days from 22 April, on TV, phone and web, unless it's switched off in Settings > Look. When Easter week (the six
 * days before Easter Sunday and the day itself) touches 22 to 28 April, Earth week starts the day after Easter
 * Sunday instead, so the two never overlap (next in 2038). The same dates as the web's earthday.js.
 */
object EarthDaySeason {
    /** Settings > Look > Earth Day look is off (set by MainActivity from the saved settings) */
    var lookOff by mutableStateOf(false)

    const val DAYS = 7

    /** The first day of Earth week in [year] */
    fun start(year: Int): LocalDate {
        val earthDay = LocalDate.of(year, 4, 22)
        val easterDay = easter(year)
        val clash = !easterDay.isBefore(earthDay) && !easterDay.minusDays(6).isAfter(earthDay.plusDays(DAYS - 1L))
        return if (clash) easterDay.plusDays(1) else earthDay
    }

    /** Which day of Earth week [date] is (1 to 7), or 0 outside it */
    fun day(date: LocalDate = Today.date()): Int {
        val n = ChronoUnit.DAYS.between(start(date.year), date) + 1
        return if (n in 1..DAYS) n.toInt() else 0
    }

    fun isSeason(date: LocalDate = Today.date()): Boolean = seasonAnswer(date)

    // Kept for the day: it works out Easter's date too, and every colour on screen asks
    private val seasonAnswer = DayAnswer { day(it) > 0 }

    fun on(date: LocalDate = Today.date()): Boolean = !lookOff && isSeason(date)

    const val GREETING = "Happy Earth Day"

    /** The line under the greeting: "Earth week, day 3" */
    fun line(date: LocalDate = Today.date()): String? = day(date).takeIf { it > 0 }?.let { "Earth week, day $it" }
}

/**
 * The Earth Day look for the rest of the app: leaf green with dark words for selected things, ocean blue for other
 * buttons, switches and panels, white words on every door, a different living thing on each door, and small
 * touches that each appear once (a sprout growing out of Play, leaves and a flower on progress lines, a little
 * Earth next to switches that are on, the Earth turning in the loading ring, and a sprout, flower, bud or Earth
 * beside each kind of notice). Kids gets it too (Ben), so it wins over the Sky and Sunshine colours that week.
 */
object EdLook {
    val on: Boolean get() = EarthDaySeason.on()

    val Leaf = Color(0xFF7CD36B)
    val OnLeaf = Color(0xFF0B2410)
    val Ocean = Color(0xFF1E5A86)
    val Panel = Color(0xFF0B1A26)
    val PanelHigh = Color(0xFF10243A)
    val Raised = Color(0xFF0F2232)
    val Line = Color(0xFF1E3A50)

    /**
     * Where each door's living thing sits on a 240 x 220 door (x, y, width, height), as printed by
     * `node tools/earthday/pictures.js`. The same numbers are in the web's html.ed .door::after rules.
     */
    val CHARM_BOX: Map<BenflixDoor, FloatArray> =
        mapOf(
            BenflixDoor.MOVIES to floatArrayOf(148f, -7f, 80f, 169f),
            BenflixDoor.SHOWS to floatArrayOf(106f, 5f, 147f, 125f),
            BenflixDoor.LIVE_TV to floatArrayOf(136f, -7f, 100f, 159f),
            BenflixDoor.GAMES to floatArrayOf(95f, 1f, 133f, 146f),
            BenflixDoor.MUSIC to floatArrayOf(124f, 30f, 120f, 91f),
            BenflixDoor.BOOKS to floatArrayOf(140f, 4f, 112f, 147f),
        )
}

/** The living thing on each door: a sprout in a hanging pot, a monarch butterfly, a terrarium, a ladybird, a robin, a pressed daisy */
private fun BenflixDoor.life(): Int =
    when (this) {
        BenflixDoor.MOVIES -> R.drawable.ed_life_movies
        BenflixDoor.SHOWS -> R.drawable.ed_life_shows
        BenflixDoor.LIVE_TV -> R.drawable.ed_life_live
        BenflixDoor.GAMES -> R.drawable.ed_life_games
        BenflixDoor.MUSIC -> R.drawable.ed_life_music
        BenflixDoor.BOOKS -> R.drawable.ed_life_books
    }

/** Earth Day look on a Home door: its living thing, where the approved sheet put it */
fun Modifier.edDoorLife(door: BenflixDoor): Modifier =
    if (!EdLook.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(door.life())
            val box = EdLook.CHARM_BOX.getValue(door)
            drawWithContent {
                drawContent()
                // the doors keep the sheet's 240 x 220 shape; on other shapes the picture keeps to the top right
                val k = minOf(size.width, size.height * 240f / 220f) / 240f
                val x = size.width - (240f - box[0]) * k
                drawImageAt(img, x, box[1] * k, box[2] * k, box[3] * k)
            }
        }
    }

/**
 * Earth Day look: a two-leaf sprout grows up out of Play's top edge, near the right. [tall] fixes the sprout's
 * height (for notices); otherwise it's sized to the button, as on the sheet.
 */
fun Modifier.edPlaySprout(tall: Dp? = null): Modifier =
    if (!EdLook.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(R.drawable.ed_sprout)
            drawWithContent {
                drawContent()
                // sized as on the sheet: an 84-tall Play with a 46-tall sprout, its stem 34 in from the right edge
                val k = tall?.toPx()?.div(46f) ?: (size.height / 84f)
                drawImageAt(img, size.width - 34f * k - 46f * k, 6f * k - 70f * k, 92f * k, 72f * k)
            }
        }
    }

/** Earth Day look: green leaves along the filled part of a progress line and a flower opening at its end */
fun Modifier.edProgressBloom(
    share: Float,
    flower: Dp = 22.dp,
    leaves: Boolean = true,
): Modifier =
    if (!EdLook.on) {
        this
    } else {
        composed {
            val bloom = ImageBitmap.imageResource(R.drawable.ed_bloom)
            val leaf = ImageBitmap.imageResource(R.drawable.ed_leaf)
            drawWithContent {
                drawContent()
                val s = flower.toPx()
                val end = size.width * share.coerceIn(0f, 1f)
                val cy = size.height / 2f
                // a leaf every so often along the filled part, alternating up and down
                val lw = s * .9f
                val lh = lw * leaf.height / leaf.width
                var x = s * 1.6f
                var k = 0
                while (leaves && x < end - s) {
                    // every other leaf sits a little lower, like a vine
                    drawImageAt(leaf, x, cy - lh * (if (k % 2 == 1) .25f else .85f), lw, lh)
                    x += s * 2.6f
                    k++
                }
                drawImageAt(bloom, (end - s / 2f).coerceIn(0f, size.width - s), cy - s / 2f, s, s)
            }
        }
    }

/** Earth Day look: the little Earth shown next to a switch that's on */
@Composable
fun EdGlobe(size: Dp = 22.dp) {
    Image(
        painter = painterResource(R.drawable.ed_globe),
        contentDescription = null,
        modifier = Modifier.size(size),
    )
}

/** Earth Day look: the Earth turning inside the loading ring ([turn] goes 0 to 1 for one full turn) */
@Composable
fun EdLoadingGlobe(
    turn: () -> Float,
    modifier: Modifier = Modifier,
) {
    val sea = ImageBitmap.imageResource(R.drawable.ed_globe_sea)
    val land = ImageBitmap.imageResource(R.drawable.ed_globe_land)
    val shade = ImageBitmap.imageResource(R.drawable.ed_globe_shade)
    Canvas(modifier) {
        val d = minOf(size.width, size.height)
        val left = (size.width - d) / 2f
        val top = (size.height - d) / 2f
        // the pictures are 104 units across for a ball of radius 50; the land tile is 100 units (one turn)
        val unit = d / 104f
        drawImageAt(sea, left, top, d, d)
        val ball =
            Path().apply {
                addOval(
                    androidx.compose.ui.geometry
                        .Rect(left + 2f * unit, top + 2f * unit, left + 102f * unit, top + 102f * unit),
                )
            }
        clipPath(ball) {
            val tile = 100f * unit
            val shift = (turn() % 1f) * tile
            for (n in -1..1) drawImageAt(land, left + 2f * unit + shift + n * tile, top + 2f * unit, tile, tile)
        }
        drawImageAt(shade, left, top, d, d)
    }
}

/** Earth Day look: the mark beside each kind of Home notice (sprout = new, flower = ready, bud = on the way, Earth = the rest) */
@Composable
fun EdKindMark(
    kind: String,
    size: Dp,
) {
    val k = kind.lowercase()
    val res =
        when {
            "ready" in k || "fixed" in k -> R.drawable.ed_bloom
            "way" in k || "waiting" in k || "search" in k || "almost" in k || "coming" in k -> R.drawable.ed_bud
            "new" in k || "sent" in k || "from " in k -> R.drawable.ed_sprout
            else -> R.drawable.ed_globe
        }
    Image(painter = painterResource(res), contentDescription = null, modifier = Modifier.size(size))
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
