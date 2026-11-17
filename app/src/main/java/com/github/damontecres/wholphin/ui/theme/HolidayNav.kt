package com.github.damontecres.wholphin.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
     * True when the art above moves (uses its seconds): the icons, mark and trims then redraw every frame on TV and
     * phone, lite mode included. Leave false for still art so old TVs don't redraw for nothing.
     */
    val moving: Boolean = false,
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
            holiday(
                "earthday",
                { EdLook.on && !EaLook.on },
                0xFF12261A,
                0xFF0A160F,
                listOf(0xFF5BD18B, 0xFF3FA7D6, 0xFF8BC34A),
                "Earth Day pick",
            ),
            holiday(
                "halloween",
                { HwLook.charmsOn },
                0xFF24140A,
                0xFF120A05,
                listOf(0xFFFF8A1F, 0xFF8E44AD, 0xFF7CFC00),
                "Halloween pick",
                countdown = { HalloweenSeason.countdown(LocalDateTime.now()) },
            ),
            holiday("popcorn", { PcLook.on }, 0xFF2A220E, 0xFF17130A, listOf(0xFFF5D76E, 0xFFD7263D, 0xFFFFFFFF), "Popcorn Day pick"),
            holiday("starwars", { SwLook.on }, 0xFF0B0B14, 0xFF05050A, listOf(0xFFFFE81F, 0xFF4FC3F7, 0xFFE53935), "Star Wars Day pick"),
            holiday(
                "stpatricks",
                { SpLook.on },
                0xFF0F2A17,
                0xFF08170D,
                listOf(0xFF2E9E5B, 0xFF7BD389, 0xFFE8C15A),
                "St. Patrick's pick",
                countdown = { StPatricksSeason.countdown(LocalDate.now()) },
            ),
            holiday(
                "july4",
                { J4Look.on },
                0xFF101A33,
                0xFF0A0F1F,
                listOf(0xFFD7263D, 0xFFFFFFFF, 0xFF3F6FD8),
                "4th of July pick",
                countdown = { JulySeason.countdown(LocalDateTime.now()) },
            ),
            holiday(
                "newyear",
                { NyLook.on },
                0xFF141A2E,
                0xFF0B0E1A,
                listOf(0xFFE8C15A, 0xFFC9D0E0),
                "New Year's pick",
                countdown = { NewYearSeason.countdown(LocalDateTime.now()) },
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
            holiday(
                "easter",
                { EaLook.on },
                0xFF1F2433,
                0xFF121522,
                listOf(0xFFF7B2D9, 0xFFB8E0D2, 0xFFFFE29A, 0xFFC3B1E1),
                "Easter pick",
                countdown = { EasterSeason.countdown(LocalDate.now()) },
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

/** Draws a [HolidayArt] into [modifier]'s box, moving when the holiday's art moves */
@Composable
fun HolidayArtBox(
    art: HolidayArt,
    modifier: Modifier,
) {
    val clock = holidayClock()
    Canvas(modifier) { art(clock.floatValue) }
}

/**
 * Seconds since the art showed, ticking every frame only while the holiday's art moves ([HolidayNav.moving]). It's
 * read while drawing, so a tick only redraws the art and never rebuilds the screen.
 */
@Composable
fun holidayClock(): FloatState {
    val clock = remember { mutableFloatStateOf(0f) }
    val moving = remember { HolidayNavKit.current()?.moving == true }
    if (moving) {
        LaunchedEffect(Unit) {
            val start = withFrameMillis { it }
            while (true) withFrameMillis { clock.floatValue = (it - start) / 1000f }
        }
    }
    return clock
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
        val clock = holidayClock()
        Canvas(modifier.then(Modifier.size(size))) { art(color, clock.floatValue) }
    } else {
        com.github.damontecres.wholphin.ui.components
            .BenflixIconImage(icon, color = color, size = size, contentDescription = null, modifier = modifier)
    }
}
