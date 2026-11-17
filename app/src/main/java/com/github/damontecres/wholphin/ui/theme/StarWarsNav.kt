package com.github.damontecres.wholphin.ui.theme

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import java.time.LocalTime

/**
 * Star Wars Day (4 May) on the new navigation, approved by Ben 2026-11-17 (sheet starwars-7-icons, rc-saga + rq-blade A
 * + rq-home A):
 * - the rail and phone-tab icons become the saga drawings, each in its own colour: a sand dome home, the yellow
 *   crawl, orange twin suns, a blue hologram globe, a blue and a red laser sword crossed, white light speed and a red
 *   cube, each with a soft glow (brighter on the one you're on)
 * - a short lit laser sword, in the colour of the side the profile picked, marks the icon you're on
 * - Home stays plain (no trims): the yellow greeting, the May the 4th pick first and a "today" card
 * - at start-up the icons ignite one by one in the profile's blade colour
 */
object StarWarsNav {
    private class Part(
        val path: Path,
        val color: Color,
    )

    private fun parts(vararg p: Pair<String, Long>): List<Part> =
        p.map { (d, c) -> Part(PathParser().parsePathString(d).toPath(), Color(c)) }

    private const val SAND = 0xFFE2C296
    private const val CRAWL = 0xFFFFE81F
    private const val SUNS = 0xFFFF9A3D
    private const val HOLO = 0xFF6CCBFF
    private const val LIGHT_BLADE = 0xFF4FB8FF
    private const val RED = 0xFFFF3B3B
    private const val STARS = 0xFFDCEBFF
    private const val HILT = 0xFFB8BEC8

    /** The drawings, 24 x 24 like the usual icons (the same paths as the web's js/holidaynav.js) */
    private val drawings: Map<String, List<Part>> by lazy {
        mapOf(
            // A dome home in the desert: ground line, dome, round door, a little window
            HolidayIconKey.HOME to parts("M3 19.5h18M5 19.5v-5a7 7 0 0 1 14 0v5M10 19.5v-3a2 2 0 0 1 4 0v3M7.5 11.5h2" to SAND),
            // The opening crawl: lines sliding away into the distance
            "movies" to parts("M5 20.5L9 3.5h6l4 17zM7.4 15.5h9.2M8.3 11.5h7.4M9.1 7.5h5.8" to CRAWL),
            // Twin suns setting over a dune with a dome on it
            "shows" to
                parts(
                    "M5 8a3 3 0 1 0 6 0a3 3 0 1 0-6 0zM13.5 6a2 2 0 1 0 4 0a2 2 0 1 0-4 0z" to SUNS,
                    "M2 19c5-5.5 11-5.5 20-2.5M14 16.4a2.4 2.4 0 0 1 4.8 0" to SUNS,
                ),
            // A hologram globe on its little projector
            "live" to
                parts(
                    "M6 9.5a6 6 0 1 0 12 0a6 6 0 1 0-12 0zM9.4 9.5a2.6 6 0 1 0 5.2 0a2.6 6 0 1 0-5.2 0z" to HOLO,
                    "M6 9.5h12M8 21h8M10 21l.8-3.5h2.4L14 21" to HOLO,
                ),
            // Two laser swords crossed: one blue, one red, with grey hilts
            "games" to
                parts(
                    "M6.5 17.5L18.5 5.5" to LIGHT_BLADE,
                    "M17.5 17.5L5.5 5.5" to RED,
                    "M3.5 20.5l3-3M20.5 20.5l-3-3" to HILT,
                ),
            // Light speed: streaks rushing out from a bright point
            "music" to parts("M12 12L4 6M12 12L3 13.5M12 12l-6 8M12 12l8-7M12 12l9 2M12 12l5 8M12 12V3M12 12h.01" to STARS),
            // A glowing cube
            "books" to parts("M12 3l7 4v8l-7 4-7-4V7zM5 7l7 4 7-4M12 11v8" to RED),
        )
    }

    private fun Color.sameAs(other: Color) = red == other.red && green == other.green && blue == other.blue

    private fun icon(parts: List<Part>): HolidayIconArt =
        { given, _ ->
            // The rail hands us the colour the usual icon would have. Grey (not lit yet at start-up) and the
            // profile's blade colour (lighting up) are drawn as given; otherwise each drawing keeps its own colours,
            // glowing brighter when it isn't the plain soft white (the place you're on, or focus)
            val startUp = given.sameAs(BenflixColors.Muted) || given.sameAs(SwSides.currentBlade)
            val plain = given.sameAs(BenflixColors.Soft)
            val k = size.minDimension / 24f
            withTransform({
                translate((size.width - 24f * k) / 2f, (size.height - 24f * k) / 2f)
                scale(k, k, pivot = Offset.Zero)
            }) {
                for (p in parts) {
                    val c = if (startUp) given else p.color.copy(alpha = given.alpha)
                    if (!startUp || given.sameAs(SwSides.currentBlade)) {
                        drawPath(
                            p.path,
                            c.copy(alpha = c.alpha * if (plain) .22f else .42f),
                            style = Stroke(if (plain) 4.6f else 6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    }
                    drawPath(p.path, c, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }

    /** A short lit laser sword standing beside the icon you're on (box 12 x 18): hilt at the bottom, blade up */
    private val blade: HolidayArt = { _ ->
        val c = SwSides.currentBlade
        val w = size.width
        val h = size.height
        val hiltH = h * .3f
        val bladeW = w * .3f
        val x = (w - bladeW) / 2f
        // glow, blade, white core
        drawRoundRect(c.copy(alpha = .35f), Offset(x - bladeW * .7f, 0f), Size(bladeW * 2.4f, h - hiltH), CornerRadius(bladeW))
        drawRoundRect(c, Offset(x, h * .04f), Size(bladeW, h - hiltH - h * .04f), CornerRadius(bladeW / 2f))
        drawRoundRect(Color.White, Offset(x + bladeW * .3f, h * .08f), Size(bladeW * .4f, h - hiltH - h * .1f), CornerRadius(bladeW / 4f))
        // the hilt: silver with dark grips
        val hx = (w - w * .46f) / 2f
        translate(hx, h - hiltH) {
            drawRoundRect(Color(HILT), Offset.Zero, Size(w * .46f, hiltH), CornerRadius(w * .06f))
            for (i in 1..2) {
                drawRect(Color(0xFF3A3D44), Offset(0f, hiltH * i / 3.2f), Size(w * .46f, hiltH * .14f))
            }
        }
    }

    /** At start-up every icon ignites in the profile's blade colour (a list that always reads the side now) */
    private val lights: List<Color> =
        object : AbstractList<Color>() {
            override val size: Int = 1

            override fun get(index: Int): Color = SwSides.currentBlade
        }

    val entry: HolidayNav by lazy {
        HolidayNav(
            key = "starwars",
            isOn = { SwLook.on },
            railTop = Color(0xFF0B0B14),
            railBottom = Color(0xFF05050A),
            lights = lights,
            icons = drawings.mapValues { (_, parts) -> icon(parts) },
            mark = blade,
            topTrim = null,
            bottomTrim = null,
            countdown = {
                val left = 24 - LocalTime.now().hour
                "Today only · $left ${if (left == 1) "hour" else "hours"} left"
            },
            countdownKind = "May the 4th",
            pickWhy = "May the 4th pick",
            accent = StarWarsDay.CrawlYellow,
        )
    }
}
