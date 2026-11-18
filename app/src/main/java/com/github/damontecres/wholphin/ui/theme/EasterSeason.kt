package com.github.damontecres.wholphin.ui.theme

import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Benflix Easter look (approved 2026-11-17, thread "Design an Easter theme for Benflix"): on from the Monday
 * before Easter to Easter Sunday, worked out for each year, on TV, phone and web, unless it's switched off in
 * Settings > Look. It never starts before 18 March (St Patrick's comes first) and stops by 21 April (Earth Day
 * keeps 22 April). Kids gets it too (Ben: "Kids get easter too"). The same dates as the web's easter.js.
 */
object EasterSeason {
    /** Settings > Look > Easter look is off (set by MainActivity from the saved settings) */
    var lookOff by mutableStateOf(false)

    /** Easter Sunday in [year] (the Western date, worked out the usual way) */
    fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val n = h + l - 7 * m + 114
        return LocalDate.of(year, n / 31, (n % 31) + 1)
    }

    fun isSeason(date: LocalDate = Today.date()): Boolean = seasonAnswer(date)

    // Working out Easter's date for every colour on screen added up, so the answer is kept for the day
    private val seasonAnswer =
        DayAnswer { date ->
            val sunday = easterSunday(date.year)
            val start = maxOf(sunday.minusDays(6), LocalDate.of(date.year, 3, 18))
            val end = minOf(sunday, LocalDate.of(date.year, 4, 21))
            !date.isBefore(start) && !date.isAfter(end)
        }

    fun on(date: LocalDate = Today.date()): Boolean = !lookOff && isSeason(date) && !Friday13.on(date)

    fun isSunday(date: LocalDate = Today.date()): Boolean = date == easterSunday(date.year)

    const val GREETING = "Happy Easter"

    /** The line under the greeting: "5 days to Easter", or on the day, how the egg hunt is going */
    fun countdown(date: LocalDate = Today.date()): String? {
        if (isSunday(date)) {
            val found = EasterHunt.found.size
            return when {
                found >= EasterHunt.ALL -> "The golden egg is open. Happy Easter!"
                found > 0 -> "$found of 6 eggs found"
                else -> "Six eggs are hidden around Benflix today"
            }
        }
        return when (val days = ChronoUnit.DAYS.between(date, easterSunday(date.year))) {
            1L -> "1 day to Easter"
            in 2L..6L -> "$days days to Easter"
            else -> null
        }
    }
}

/**
 * Easter Sunday egg hunt (approved e1-q-day): one egg is hidden on each of the six door pages. Find all six and
 * the golden egg cracks open on Who's watching. Kept on this device for the year, so the whole house shares it.
 */
object EasterHunt {
    const val ALL = 6
    private const val PREFS = "benflix_easter_hunt"

    /** The doors whose egg has been found this year */
    var found by mutableStateOf(emptySet<String>())
        private set

    private var loadedYear = -1
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        val year = LocalDate.now().year
        if (prefs != null && loadedYear == year) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        loadedYear = year
        found = p.getStringSet("found-$year", emptySet()).orEmpty().toSet()
    }

    /** Today is Easter Sunday, the Easter look is on, and this door's egg is still hidden */
    fun hiddenOn(door: BenflixDoor): Boolean = EaLook.on && EasterSeason.isSunday() && door.name !in found

    /** Finds a door's egg. Returns how many have been found now. */
    fun find(door: BenflixDoor): Int {
        val now = found + door.name
        found = now
        prefs?.edit()?.putStringSet("found-$loadedYear", now)?.apply()
        return now.size
    }

    val all: Boolean get() = found.size >= ALL

    /** The golden egg cracks open once on Who's watching (a short clip), then just stays open */
    fun cracked(): Boolean = prefs?.getBoolean("open-$loadedYear", false) ?: false

    fun setCracked() {
        prefs?.edit()?.putBoolean("open-$loadedYear", true)?.apply()
    }

    fun message(
        door: BenflixDoor,
        count: Int,
    ): String =
        if (count >= ALL) {
            "You found all six! The golden egg is opening on Who's watching"
        } else {
            "You found the ${door.title} egg! $count of 6"
        }
}

/**
 * The Easter look for the rest of the app: soft lilac for selected things (dark words on it), meadow green for
 * other buttons, switches and panels, white words on every door, a different Easter tree charm on each door (on
 * a satin ribbon from a pearl pin), and small Easter touches that each appear once: bunny ears peeking over Play,
 * a painted egg as the progress knob, a rocking cracked egg in the loading ring, a daisy next to switches that
 * are on, and a small mark on notices (egg, hatching chick, paw prints, tulip). Kids too: while it's on,
 * [KidsLook.palette] steps aside so Sky and Sunshine rest for the week (Kids keeps its round letters).
 */
object EaLook {
    val on: Boolean get() = EasterSeason.on()

    val Lilac = Color(0xFFC9B2F5)
    val OnLilac = Color(0xFF1E1236)
    val Green = Color(0xFF3D7A4F)
    val Panel = Color(0xFF122019)
    val PanelHigh = Color(0xFF1A2B22)
    val Raised = Color(0xFF16261D)
    val Line = Color(0xFF264232)
}

/** The Easter tree charm that hangs on each door */
private fun BenflixDoor.eaCharm(): Int =
    when (this) {
        BenflixDoor.MOVIES -> R.drawable.ea_charm_movies
        BenflixDoor.SHOWS -> R.drawable.ea_charm_shows
        BenflixDoor.LIVE_TV -> R.drawable.ea_charm_live
        BenflixDoor.GAMES -> R.drawable.ea_charm_games
        BenflixDoor.MUSIC -> R.drawable.ea_charm_music
        BenflixDoor.BOOKS -> R.drawable.ea_charm_books
    }

/** The hidden egg on each door page (Easter Sunday), a different pattern each */
fun BenflixDoor.eaHuntEgg(): Int =
    when (this) {
        BenflixDoor.MOVIES -> R.drawable.ea_hunt_movies
        BenflixDoor.SHOWS -> R.drawable.ea_hunt_shows
        BenflixDoor.LIVE_TV -> R.drawable.ea_hunt_live
        BenflixDoor.GAMES -> R.drawable.ea_hunt_games
        BenflixDoor.MUSIC -> R.drawable.ea_hunt_music
        BenflixDoor.BOOKS -> R.drawable.ea_hunt_books
    }

/**
 * Easter look on a Home door: its charm hanging from a pearl pin on the top edge, near the right (as on the
 * approved sheet: a 240 x 220 door with the charm's box from x 124 to 244 and y 0 to 156).
 */
fun Modifier.eaDoorCharm(door: BenflixDoor): Modifier =
    if (!EaLook.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(door.eaCharm())
            drawWithContent {
                drawContent()
                val k = minOf(size.width, size.height * 240f / 220f) / 240f
                val w = 120f * k
                val h = w * img.height / img.width
                eaDrawImageAt(img, size.width - 116f * k, 0f, w, h)
            }
        }
    }

/** Easter look: a bunny hiding behind Play, only its ears showing over the top edge (near the right) */
fun Modifier.eaPlayEars(): Modifier =
    if (!EaLook.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(R.drawable.ea_ears)
            drawBehind {
                // the approved sheet: 80-unit ears on an 84-tall Play button, 56 in from its right edge
                val k = size.height / 84f
                val w = 120f * k
                val h = 100f * k
                eaDrawImageAt(img, size.width - 56f * k - w / 2f, 8f * k - h, w, h)
            }
        }
    }

/** Easter look: a painted egg rolling along as the knob at the end of a progress line ([share] of the way along) */
fun Modifier.eaProgressEgg(
    share: Float,
    egg: Dp = 22.dp,
): Modifier =
    if (!EaLook.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(R.drawable.ea_knob)
            drawWithContent {
                drawContent()
                val s = egg.toPx()
                val x = (size.width * share.coerceIn(0f, 1f)) - s / 2f
                // sits on the line, leaning up into the picture so it isn't cut off at the card's edge
                eaDrawImageAt(img, x.coerceIn(0f, size.width - s), size.height - s * .85f, s, s)
            }
        }
    }

/** Easter look: the small daisy shown next to a switch that's on */
@Composable
fun EaDaisy(size: Dp = 22.dp) {
    Image(
        painter = painterResource(R.drawable.ea_daisy),
        contentDescription = null,
        modifier = Modifier.size(size),
    )
}

/** Easter look: the cracked painted egg that rocks inside the loading ring */
@Composable
fun EaLoadingEgg(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ea_loader),
        contentDescription = null,
        modifier = modifier,
    )
}

/** Easter look: which small mark a notice gets, by what it's about */
fun eaMarkFor(kind: String): Int {
    val k = kind.lowercase()
    return when {
        k.contains("ready") -> R.drawable.ea_mark_ready
        k.startsWith("on the way") || k.contains("arrives") -> R.drawable.ea_mark_way
        k.contains("live") -> R.drawable.ea_mark_live
        else -> R.drawable.ea_mark_ep
    }
}

/** Easter look: the notice's own small mark (a painted egg, a hatching chick, paw prints or a tulip) */
@Composable
fun EaKindMark(
    kind: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(eaMarkFor(kind)),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

private fun DrawScope.eaDrawImageAt(
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
