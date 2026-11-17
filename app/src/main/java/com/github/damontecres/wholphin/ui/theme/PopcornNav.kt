package com.github.damontecres.wholphin.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import java.time.LocalDateTime
import kotlin.math.floor
import kotlin.math.max

/**
 * Popcorn Day with the new navigation (approved 2026-11-17, sheet "Popcorn Day Navigation", Ben: LOVE Cinema
 * marquee): a cinema-red rail whose icons glow butter and cinema red, a popped piece of popcorn beside the icon
 * you're on, chasing marquee bulbs across the top of Home, a heap of popcorn along the bottom that grows through
 * the day, an hours-left card first in Today, and a popcorn movie as the first Home pick (a different one every few
 * hours). Kids too, like the rest of Popcorn Day.
 */
internal object PopcornNav {
    private val Butter = Color(0xFFF6C744)
    private val Red = Color(0xFFFF5A52)
    private val Cream = Color(0xFFFFF4D6)
    private val Shade = Color(0xFFE2A93A)
    private val Bulb = Color(0xFFFFF8DA)
    private val Strip = Color(0xFF9E2129)
    private val StripDark = Color(0xFF6E1016)

    /** The movies the first Home pick comes from: the Popcorn movies row's list, turned every 3 hours */
    private val TITLES =
        listOf(
            "Jurassic Park",
            "Back to the Future",
            "Raiders of the Lost Ark",
            "Ghostbusters",
            "Jaws",
            "E.T. the Extra-Terrestrial",
            "The Princess Bride",
            "Toy Story",
            "The Lion King",
            "Star Wars",
            "Top Gun: Maverick",
            "Spider-Man: Into the Spider-Verse",
            "The Avengers",
            "Finding Nemo",
            "Shrek",
            "The Incredibles",
        )

    /** Starts at a different title each 3 hours, so the first pick changes through the day (approved pq-pick A) */
    private val turningTitles: List<String> =
        object : AbstractList<String>() {
            override val size get() = TITLES.size

            override fun get(index: Int): String = TITLES[(index + LocalDateTime.now().hour / 3) % TITLES.size]
        }

    /** The usual rail and phone-tab icons, the same shapes as today */
    private val USUAL =
        mapOf(
            HolidayIconKey.HOME to BenflixIcon.HOME,
            HolidayIconKey.SEARCH to BenflixIcon.SEARCH,
            HolidayIconKey.REQUESTS to BenflixIcon.REQUESTS,
            HolidayIconKey.MORE to BenflixIcon.MORE,
            "movies" to BenflixIcon.FILM,
            "shows" to BenflixIcon.TV,
            "live" to BenflixIcon.LIVE,
            "games" to BenflixIcon.PAD,
            "music" to BenflixIcon.NOTE,
            "books" to BenflixIcon.BOOK,
        )

    /** Butter and cinema red, taken in turn down the rail */
    private val ICON_COLOUR =
        mapOf(
            HolidayIconKey.HOME to Butter,
            HolidayIconKey.SEARCH to Red,
            "movies" to Red,
            "shows" to Butter,
            "live" to Red,
            "games" to Butter,
            "music" to Red,
            "books" to Butter,
            HolidayIconKey.REQUESTS to Butter,
            HolidayIconKey.MORE to Butter,
        )

    private val paths = mutableMapOf<BenflixIcon, List<Path>>()

    private fun pathsOf(icon: BenflixIcon): List<Path> =
        paths.getOrPut(icon) { icon.paths.map { PathParser().parsePathString(it).toPath() } }

    /**
     * The usual icon drawn in butter or cinema red with a soft glow. While the rail is still dark at start-up (a
     * grey colour comes in) it stays grey, so the light-up still plays.
     */
    private fun icon(key: String): HolidayIconArt {
        val icon = USUAL.getValue(key)
        val holidayColour = ICON_COLOUR.getValue(key)
        return { given, _ ->
            val dim = given.luminance() < .25f
            val c = if (dim) given else holidayColour
            val k = size.minDimension / 24f
            scale(k, k, pivot = Offset.Zero) {
                pathsOf(icon).forEach { p ->
                    if (!dim) drawPath(p, c.copy(alpha = .28f), style = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(p, c, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }

    /** One popped piece of popcorn: cream puffs with butter shading and a little shadow */
    fun DrawScope.puff(
        c: Offset,
        r: Float,
        turn: Int = 0,
    ) {
        val lobes =
            arrayOf(
                floatArrayOf(-.42f, .12f, .55f),
                floatArrayOf(.44f, .1f, .55f),
                floatArrayOf(0f, -.36f, .6f),
                floatArrayOf(.05f, .4f, .52f),
            )
        val flip = if (turn % 2 == 0) 1f else -1f
        drawCircle(Color.Black.copy(alpha = .28f), r * .95f, Offset(c.x, c.y + r * .25f))
        lobes.forEach { (x, y, s) ->
            val o = Offset(c.x + x * r * flip, c.y + y * r)
            drawCircle(
                Brush.radialGradient(listOf(Cream, Cream, Shade), center = Offset(o.x - r * .15f, o.y - r * .2f), radius = s * r * 1.25f),
                s * r,
                o,
            )
        }
        drawCircle(Color.White.copy(alpha = .7f), r * .16f, Offset(c.x - r * .18f * flip, c.y - r * .42f))
    }

    /** A puff beside the icon you're on, a bit bigger than its box so it's seen from the couch */
    private val mark: HolidayArt = { _ ->
        val r = max(size.width, size.height * .66f) * .62f
        puff(Offset(size.width * .45f, size.height * .55f), r)
    }

    /** The cinema sign across the top of Home: a red strip with a butter edge and big bulbs chasing along it */
    private val marquee: HolidayArt = { t ->
        val h = size.height * .78f
        drawRect(Brush.verticalGradient(listOf(StripDark, Strip, StripDark), endY = h), size = size.copy(height = h))
        drawRect(Butter, topLeft = Offset(0f, h - 3f * density), size = size.copy(height = 3f * density))
        val step = 36f * density
        val n = (size.width / step).toInt() + 1
        val r = 7f * density
        val beat = floor(t / .4f).toInt()
        for (i in 0..n) {
            val x = i * step + step / 2
            val c = Offset(x, h / 2)
            val lit = (i + beat) % 3 != 0
            if (lit) drawCircle(Brush.radialGradient(listOf(Butter.copy(alpha = .75f), Color.Transparent), c, r * 2.4f), r * 2.4f, c)
            drawCircle(
                Brush.radialGradient(listOf(Bulb, Butter, Color(0xFFC48A12)), Offset(c.x - r * .3f, c.y - r * .3f), r * 1.3f),
                r,
                c,
                alpha = if (lit) 1f else .4f,
            )
        }
    }

    /** How full the heap is: a little in the morning, more after lunch, piled high in the evening (like the cart room) */
    private fun fullness(): Float =
        when (PopcornSeason.variant()) {
            "am" -> .55f
            "pm" -> .78f
            else -> 1f
        }

    /** A heap of popcorn along the bottom of Home */
    private val heap: HolidayArt = { _ ->
        val full = fullness()
        val r0 = 9f * density
        val rows = 3
        var seed = 11
        fun rnd(): Float {
            seed = (seed * 9301 + 49297) % 233280
            return seed / 233280f
        }
        for (row in 0 until rows) {
            val count = (size.width / (r0 * 2.1f) * (1.15f - row * .3f) * full).toInt()
            val y = size.height - r0 * .55f - row * r0 * 1.15f * full
            repeat(count) {
                val x = rnd() * size.width
                val r = r0 * (.8f + rnd() * .45f)
                puff(Offset(x, y - rnd() * r0 * .4f), r, (rnd() * 4).toInt())
            }
        }
    }

    /** "7 hours of Popcorn Day left" for the first Today card (approved pq-count A) */
    private fun countdown(now: LocalDateTime = LocalDateTime.now()): String? {
        if (!PopcornSeason.isSeason(now.toLocalDate())) return null
        val left = 24 - now.hour
        return if (left <= 1) "Last hour of Popcorn Day · grab a bowl" else "$left hours of Popcorn Day left · grab a bowl"
    }

    val nav: HolidayNav =
        HolidayNav(
            key = "popcorn",
            isOn = { PcLook.on },
            railTop = Color(0xFF5A1218),
            railBottom = Color(0xFF1E0C0E),
            lights = listOf(Butter, Red),
            icons = USUAL.keys.associateWith { icon(it) },
            mark = mark,
            topTrim = marquee,
            bottomTrim = heap,
            moving = true,
            countdown = { runCatching { countdown() }.getOrNull() },
            countdownKind = "Popcorn Day",
            pickWhy = "Popcorn Day pick",
            pickTitles = turningTitles,
            accent = Butter,
        )
}
