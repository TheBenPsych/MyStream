package com.github.damontecres.wholphin.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import com.github.damontecres.wholphin.ui.util.rememberAmbientClock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A small holiday picture, drawn into whatever box it's given (a few dp for a door's item, the whole width for
 * Home's trims). Each holiday thread swaps the placeholder art below for its real drawing. It's given the seconds
 * since it first showed, so it can move (bulbs twinkling, snow falling); set [HolidayNav.moving] so it redraws.
 * Animations stay on everywhere, lite mode too (Ben's rule).
 */
typealias HolidayArt = DrawScope.(seconds: Float) -> Unit

/**
 * A holiday's own icon, drawn in place of a usual rail or phone-tab icon. It's given the colour the usual icon would
 * have (soft white, the door's colour when you're in it, or the start-up light), so it behaves like the normal one,
 * and the seconds since it showed, like [HolidayArt].
 */
typealias HolidayIconArt = DrawScope.(color: Color, seconds: Float) -> Unit

/** The names for [HolidayNav.icons]: the same on the web (js/holidaynav.js) */
object HolidayIconKey {
    const val HOME = "home"
    const val SEARCH = "search"
    const val REQUESTS = "requests"

    /** The phone's More tab */
    const val MORE = "more"
}

/** A door's name for [HolidayNav.icons], the same as on the web: movies, shows, live, games, music, books */
val BenflixDoor.holidayKey: String
    get() =
        when (this) {
            BenflixDoor.LIVE_TV -> "live"
            else -> name.lowercase()
        }

/**
 * Holiday marks for the new navigation (Ben 2026-11-17: "LOVE all 3", so a holiday dresses the rail, each door's
 * item and Home together). One entry per holiday in [HolidayNavKit]; the rail, the phone's bottom bar, Home and
 * the start-up light-up all read the one that's on, so holiday threads only ever touch their own entry.
 */
data class HolidayNav(
    /** Short name, the same as the web kit's (js/holidaynav.js) */
    val key: String,
    /** True while this holiday's look is on (the season's own rules: dates, Kids, Settings switch) */
    val isOn: () -> Boolean,
    /** The rail's colours, top to bottom */
    val railTop: Color,
    val railBottom: Color,
    /** Start-up: the rail's icons light up in these colours instead of the doors' own (taken in turn) */
    val lights: List<Color>,
    /**
     * The main holiday touch on the rail (Ben 2026-11-17: "only rail icons ... we can color them"): the holiday's
     * own icons, drawn in its own colours, in place of the usual ones in the rail and the phone's bottom bar. Keyed
     * by [HolidayIconKey] or a door's [holidayKey]; any key left out keeps its usual icon. Nothing else sits beside
     * the names.
     */
    val icons: Map<String, HolidayIconArt> = emptyMap(),
    /** Optional: a small decoration hanging beside the rail icon you're on (phones: from the chosen tab) */
    val mark: HolidayArt? = null,
    /** Along the top edge of Home's big picture (Christmas: lights hanging across) */
    val topTrim: HolidayArt? = null,
    /** Along the bottom of Home (Christmas: snow) */
    val bottomTrim: HolidayArt? = null,
    /**
     * True when the mark and trims move (use their seconds): they then redraw about 20 times a second on TV and
     * phone ([com.github.damontecres.wholphin.ui.util.AmbientClock]), lite mode included. Leave false for still art
     * so old TVs don't redraw for nothing. Icons only move with [iconsMove] too.
     */
    val moving: Boolean = false,
    /**
     * True when the icons themselves move (use their seconds). Off by default: the icons sit in the rail on every
     * page, so moving ones keep the whole rail drawing. The mark and trims still follow [moving].
     */
    val iconsMove: Boolean = false,
    /** The first Today card on Home ("8 days to Christmas"); null on the day itself or when there's none */
    val countdown: () -> String? = { null },
    /** The small coloured line on that card */
    val countdownKind: String = "Countdown",
    /** The reason line on the holiday's first carousel pick ("Christmas pick") */
    val pickWhy: String,
    /** Extra hand-picked titles for that pick, added to the holiday's own Home row list */
    val pickTitles: List<String> = emptyList(),
    /** The colour of the countdown card's line and the pick's reason */
    val accent: Color = lights.first(),
) {
    /** The holiday's own icon for [key], or null to keep the usual one */
    fun icon(key: String): HolidayIconArt? = icons[key]

    /** The colour icon number [i] lights up in at start-up */
    fun light(i: Int): Color = lights[i.mod(lights.size)]
}

object HolidayNavKit {
    /**
     * Every holiday, most important first: when two are on at once the first wins (Friday the 13th takes over any
     * season look, Earth Day comes after Easter week).
     */
    val all: List<HolidayNav> =
        listOf(
            holiday("friday13", { F13Look.on }, 0xFF141414, 0xFF0A0A0A, listOf(0xFF9BE15D, 0xFFC9D0E0), "Friday the 13th pick"),
            holiday(
                "videogames",
                { VgLook.on },
                0xFF1A1030,
                0xFF0E0819,
                listOf(0xFFFF6F91, 0xFF3DD6C6, 0xFFFFE14D, 0xFF7AA7FF),
                "Game Day pick",
            ),
            EarthDayNav.entry,
            holiday(
                "halloween",
                { HwLook.charmsOn },
                0xFF24140A,
                0xFF120A05,
                listOf(0xFFFF8A1F, 0xFF8E44AD, 0xFF7CFC00),
                "Halloween pick",
                countdown = { HalloweenSeason.countdown(LocalDateTime.now()) },
            ),
            PopcornNav.nav,
            // Star Wars Day: saga icons, a laser sword beside the place you're on, plain Home (ui/theme/StarWarsNav.kt)
            StarWarsNav.entry,
            holiday(
                "stpatricks",
                { SpLook.on },
                0xFF0F2A17,
                0xFF08170D,
                listOf(0xFF2E9E5B, 0xFF7BD389, 0xFFE8C15A),
                "St. Patrick's pick",
                countdown = { StPatricksSeason.countdown(LocalDate.now()) },
            ),
            // 4th of July: its own icons, star, bunting and sparklers (ui/theme/July4Nav.kt)
            July4Nav.nav(),
            holiday(
                "newyear",
                { NyLook.on },
                0xFF16224A,
                0xFF0B1230,
                listOf(0xFFE8B84A, 0xFFC9D1DE),
                "New Year pick",
                countdown = { NewYearSeason.countdown(LocalDateTime.now()) },
            ).copy(
                // Ben 2026-11-17 (Midnight Fringe): the rail icons are the New Year charms, foil fringe and confetti
                icons = NewYearNav.icons,
                topTrim = NewYearNav.topTrim,
                bottomTrim = NewYearNav.bottomTrim,
                moving = true,
                // the charms twinkle and sway themselves (on the shared slow clock)
                iconsMove = true,
                pickTitles = NewYearNav.pickTitles,
                accent = Color(0xFFE8B84A),
            ),
            // Thanksgiving: harvest icons, short leaf garland, pumpkin corner (art in ThanksgivingNav.kt)
            ThanksgivingNav.entry,
            holiday(
                "valentine",
                { ValLook.on },
                0xFF2A0F1A,
                0xFF170810,
                listOf(0xFFFF5C8A, 0xFFD7263D, 0xFFFFB3C6),
                "Valentine's pick",
                countdown = { ValentineSeason.countdown(LocalDate.now()) },
            ),
            // Easter: direction A "Easter tree ribbon" (Ben 2026-11-17), drawings in EasterNav.kt
            HolidayNav(
                key = "easter",
                isOn = { EaLook.on },
                railTop = EaLook.Panel,
                railBottom = Color(0xFF0B150F),
                lights = EasterNav.Lights,
                icons = EasterNav.icons,
                mark = EasterNav.mark,
                topTrim = EasterNav.topTrim,
                bottomTrim = EasterNav.bottomTrim,
                moving = true,
                countdown = { runCatching { EasterSeason.countdown(LocalDate.now()) }.getOrNull() },
                pickWhy = "Easter pick",
                pickTitles = EasterNav.PickTitles,
                accent = EaLook.Lilac,
            ),
            // Christmas: Classic big bulbs (approved 2026-11-17), art in ChristmasNav.kt
            holiday(
                "christmas",
                { XmasLook.on },
                0xFF0F2A1D,
                0xFF0B1912,
                listOf(0xFFD7263D, 0xFF2E9E5B, 0xFFE8C15A),
                "Christmas pick",
                // On the day itself the card says "Merry Christmas!" (approved xq-day A)
                countdown = {
                    val today = LocalDate.now()
                    if (today.monthValue == 12 && today.dayOfMonth == 25) "Merry Christmas!" else ChristmasSeason.countdown(today)
                },
            ).copy(
                icons = ChristmasNav.icons,
                mark = ChristmasNav.mark,
                topTrim = ChristmasNav.topTrim,
                bottomTrim = ChristmasNav.bottomTrim,
                pickTitles = ChristmasNav.pickTitles,
                // the bulbs and the snowflake twinkle
                moving = true,
            ),
        )

    /** For trying a look on any day (holiday threads and the robot): the key of the holiday to show, or null */
    @Volatile
    var preview: String? = null

    /** The holiday that's on right now, or null on an ordinary day */
    fun current(): HolidayNav? {
        preview?.let { key -> all.firstOrNull { it.key == key }?.let { return it } }
        return all.firstOrNull { runCatching { it.isOn() }.getOrDefault(false) }
    }

    // ---------- placeholder art (each holiday thread replaces its own) ----------

    private fun holiday(
        key: String,
        isOn: () -> Boolean,
        top: Long,
        bottom: Long,
        lights: List<Long>,
        pickWhy: String,
        countdown: () -> String? = { null },
    ): HolidayNav {
        val colors = lights.map { Color(it) }
        return HolidayNav(
            key = key,
            isOn = isOn,
            railTop = Color(top),
            railBottom = Color(bottom),
            lights = colors,
            topTrim = placeholderTrim(colors, hanging = true),
            bottomTrim = placeholderTrim(colors, hanging = false),
            countdown = { runCatching(countdown).getOrNull() },
            pickWhy = pickWhy,
        )
    }

    /** A bead on a short string, hanging from the top of its box */
    fun placeholderMark(color: Color): HolidayArt =
        {
            val r = size.minDimension * .32f
            val c = Offset(size.width / 2, size.height - r)
            drawLine(color.copy(alpha = .7f), Offset(size.width / 2, 0f), Offset(c.x, c.y - r), strokeWidth = size.minDimension * .08f)
            drawCircle(color, r, c)
            drawCircle(Color.White.copy(alpha = .45f), r * .32f, Offset(c.x - r * .35f, c.y - r * .35f))
        }

    /** A row of beads across the whole width: hanging on a gentle string (top) or resting along the edge (bottom) */
    fun placeholderTrim(
        colors: List<Color>,
        hanging: Boolean,
    ): HolidayArt =
        {
            val step = 46f * density
            val n = (size.width / step).toInt().coerceAtLeast(1)
            val r = 5f * density
            for (i in 0..n) {
                val x = i * step + step / 2
                val sag = if (hanging) kotlin.math.sin(i * .9f) * 3f * density else 0f
                val y = if (hanging) size.height * .45f + sag else size.height - r * 1.6f
                drawCircle(colors[i.mod(colors.size)].copy(alpha = if (hanging) 1f else .7f), r, Offset(x, y))
            }
            if (hanging) {
                drawLine(
                    Color.White.copy(alpha = .25f),
                    Offset(0f, size.height * .4f),
                    Offset(size.width, size.height * .4f),
                    strokeWidth = density,
                )
            }
        }
}

/**
 * Draws a [HolidayArt] into [modifier]'s box, moving when the holiday's art moves. It sits on its own drawing layer,
 * so when it moves only the art itself is drawn again, never the posters and pictures around it.
 */
@Composable
fun HolidayArtBox(
    art: HolidayArt,
    modifier: Modifier,
) {
    val clock = holidayClock()
    Canvas(modifier.graphicsLayer()) { art(clock.seconds()) }
}

/** Seconds since a piece of holiday art showed (see [holidayClock]) */
class HolidayClock internal constructor(
    private val ambient: FloatState?,
    private val start: Float,
) {
    /** Read only while drawing */
    fun seconds(): Float = ambient?.let { it.floatValue - start } ?: 0f
}

/**
 * Seconds since the art showed, ticking only while the holiday's art moves ([HolidayNav.moving]). It comes from the
 * one shared [com.github.damontecres.wholphin.ui.util.AmbientClock] (about 20 frames a second, holding still while
 * you press the remote), and it's read while drawing, so a tick only redraws the art and never rebuilds the screen.
 */
@Composable
fun holidayClock(moving: Boolean = remember { HolidayNavKit.current()?.moving == true }): HolidayClock {
    if (!moving) return remember { HolidayClock(null, 0f) }
    val ambient = rememberAmbientClock()
    return remember(ambient) { HolidayClock(ambient, Snapshot.withoutReadObservation { ambient.floatValue }) }
}

/**
 * A rail or phone-tab icon: the holiday's own one for [key] when it has one (see [HolidayNav.icons]), otherwise
 * the usual [icon]
 */
@Composable
fun HolidayOrUsualIcon(
    icon: com.github.damontecres.wholphin.ui.components.BenflixIcon,
    key: String?,
    color: Color,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val holiday = remember { HolidayNavKit.current() }
    val art = key?.let { holiday?.icon(it) }
    if (art != null) {
        // Holiday icons are still unless the holiday says they move ([HolidayNav.iconsMove]): a rail full of icons
        // ticking used to redraw every page all day long
        val clock = holidayClock(moving = remember { holiday?.moving == true && holiday.iconsMove })
        Canvas(modifier.then(Modifier.size(size)).graphicsLayer()) { art(color, clock.seconds()) }
    } else {
        com.github.damontecres.wholphin.ui.components
            .BenflixIconImage(icon, color = color, size = size, contentDescription = null, modifier = modifier)
    }
}
