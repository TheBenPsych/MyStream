package com.github.damontecres.wholphin.ui.theme

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Christmas on the new navigation (approved 2026-11-17, "Classic big bulbs", sheet nav-13-christmas):
 * - the rail's door icons become small full-colour drawings (a clapperboard wrapped like a present, a TV in a Santa
 *   hat, a signal with a star on top and little lights, a candy-cane controller, two gold bells with a bow, a green
 *   book with a red ribbon and holly)
 * - a small gold snowflake beside the icon you're on (no hanging bulb: Ben, it doesn't attach to every button)
 * - big old-fashioned glass bulbs hanging across the top of Home, soft snow drifts along the bottom
 * Same drawings on the web: server/web/js/holidaynav.js (christmas entry).
 */
object ChristmasNav {
    private val Red = Color(0xFFC8102E)
    private val RedDark = Color(0xFF9E0B23)
    private val RedBright = Color(0xFFE3223F)
    private val Gold = Color(0xFFF2C14E)
    private val GoldLight = Color(0xFFFFE08A)
    private val Pine = Color(0xFF1F7A4D)
    private val PineDark = Color(0xFF145234)
    private val Holly = Color(0xFF3DBB6E)

    /** The big glass bulbs' colours, in turn */
    private val Bulbs = listOf(Color(0xFFFF3B3B), Color(0xFF2FD06B), Color(0xFFFFC93C), Color(0xFF3FA9FF), Color(0xFFFF8A2B))

    private val paths = HashMap<String, Path>()

    /** An SVG path, read once */
    private fun p(d: String): Path = paths.getOrPut(d) { PathParser().parsePathString(d).toPath() }

    /**
     * Draws a picture made on a 48 x 48 grid into the whole box. During the start-up light-up the icon is greyed
     * (the usual icon's colour is the muted grey then), so it still "lights up" with the others.
     */
    private fun icon48(block: DrawScope.() -> Unit): HolidayIconArt =
        { color ->
            val s = size.minDimension / 48f
            val dim = color == BenflixColors.Muted
            if (dim) drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), Paint().apply { alpha = .35f }) }
            translate((size.width - 48f * s) / 2f, (size.height - 48f * s) / 2f) {
                scale(s, s, pivot = Offset.Zero) {
                    block()
                }
            }
            if (dim) drawIntoCanvas { it.restore() }
        }

    private fun DrawScope.fill(
        d: String,
        c: Color,
    ) = drawPath(p(d), c)

    private fun DrawScope.line(
        d: String,
        c: Color,
        w: Float,
    ) = drawPath(p(d), c, style = Stroke(width = w, cap = StrokeCap.Round))

    private fun DrawScope.box(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        r: Float,
        c: Color,
    ) = drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(r))

    private fun DrawScope.dot(
        x: Float,
        y: Float,
        r: Float,
        c: Color,
    ) = drawCircle(c, r, Offset(x, y))

    private const val PAD = "M7.2 4.8h9.6c2.4 0 4 1.5 4.6 3.9l1.3 6.6c.5 2.6-1.1 4.4-3.1 4.4-1.4 0-2.3-.8-3-1.9l-1.1-1.8H8.5l-1.1 " +
        "1.8c-.7 1.1-1.6 1.9-3 1.9-2 0-3.6-1.8-3.1-4.4l1.3-6.6c.6-2.4 2.2-3.9 4.6-3.9z"
    private const val BELL = "M0 0c-6.5 0-8.5 5.5-8.5 12l-3 6.5h23l-3-6.5c0-6.5-2-12-8.5-12z"

    /** Movies: a clapperboard wrapped like a present */
    private val movies =
        icon48 {
            rotate(-12f, Offset(8f, 16f)) {
                box(6f, 8f, 36f, 7.5f, 1.6f, Color(0xFF1B1B1F))
                line("M12 8l5 7.5M20 8l5 7.5M28 8l5 7.5M36 8l5 7.5", Color(0xFFF4F4F4), 2.8f)
            }
            box(7f, 18f, 34f, 24f, 3f, Red)
            box(7f, 35f, 34f, 7f, 3f, RedDark)
            box(21f, 18f, 6f, 24f, 0f, Gold)
            box(7f, 27f, 34f, 5f, 0f, Gold)
            dot(24f, 29.5f, 3.4f, GoldLight)
        }

    /** Shows: a TV wearing a Santa hat, a snowflake on its screen */
    private val shows =
        icon48 {
            line("M16 40l-3 5M32 40l3 5", PineDark, 3.2f)
            box(5f, 14f, 38f, 26f, 4f, Pine)
            box(9f, 18f, 30f, 18f, 2f, Color(0xFF0D2A1B))
            line("M24 20.5v13M18.4 23.7l11.2 6.6M18.4 30.3l11.2-6.6", Color(0xFFE8F4FF), 1.9f)
            fill("M27 15c2-9 10-12 15-8-3 1-5 4-5 8z", Red)
            box(25f, 12.5f, 15f, 5f, 2.5f, Color.White)
            dot(43f, 7.5f, 3.2f, Color.White)
        }

    /** Live TV: the signal with a star on top, strung with little lights */
    private val live =
        icon48 {
            line("M24 19v8", Gold, 2.6f)
            dot(24f, 29f, 3.8f, Gold)
            line("M17 22a10 10 0 000 14M31 22a10 10 0 010 14M11.5 16.5a18 18 0 000 25M36.5 16.5a18 18 0 010 25", Color(0xFF2FA36B), 3.4f)
            dot(9.3f, 29f, 2.4f, Bulbs[0])
            dot(38.7f, 29f, 2.4f, Bulbs[3])
            dot(14.4f, 29f, 2f, Bulbs[2])
            dot(33.6f, 29f, 2f, Bulbs[0])
            fill("M24 2.5l2.7 5.7 6.2.7-4.6 4.2 1.3 6.1-5.6-3.1-5.6 3.1 1.3-6.1-4.6-4.2 6.2-.7z", Color(0xFFFFD447))
        }

    /** Games: the controller, striped like a candy cane */
    private val games =
        icon48 {
            scale(2f, 2f, pivot = Offset.Zero) {
                clipPath(p(PAD)) {
                    drawRect(Color.White, Offset.Zero, Size(24f, 24f))
                    line("M-4 15L11-4M1 18L18-4M7 22L24 1M13 25L29 5M19 27L31 13", Color(0xFFD7263D), 2.25f)
                }
                drawPath(p(PAD), Color(0xFF8E0E22), style = Stroke(width = .45f))
            }
            box(11.5f, 17f, 5f, 12f, 1.2f, Pine)
            box(8f, 20.5f, 12f, 5f, 1.2f, Pine)
            dot(33f, 19.5f, 2.5f, Pine)
            dot(37f, 24.8f, 2.5f, Pine)
        }

    /** Music: two gold bells with a red bow */
    private val music =
        icon48 {
            listOf(Triple(16f, -16f, Color(0xFFE9B53B)), Triple(32f, 16f, Gold)).forEach { (x, deg, c) ->
                rotate(deg, Offset(x, 18f)) {
                    translate(x, 12f) {
                        fill(BELL, c)
                        line("M-6 12c0-5 1.5-9 5-10", Color(0xFFFFF0BE), 2f)
                        dot(0f, 20.5f, 3f, Color(0xFFB8862A))
                    }
                }
            }
            fill("M24 9c-6-7-12-3-7 2zM24 9c6-7 12-3 7 2z", Red)
            line("M24 9l-4 9M24 9l4 9", Color(0xFFA50C25), 2.4f)
            dot(24f, 9.5f, 2.8f, RedBright)
        }

    /** Books: a green book with a red ribbon and a sprig of holly */
    private val books =
        icon48 {
            fill("M9 8a3 3 0 013-3h26v34H12a3 3 0 00-3 3z", Pine)
            fill("M9 42a3 3 0 013-3h26v4H12z", Color(0xFFF3EBDD))
            line("M9 42V8", PineDark, 2f)
            fill("M31 5v22l3.5-3 3.5 3V5z", Red)
            fill(
                "M14 19c3-5 8-5 10-1-3.5 0-5 2.2-5 4.5-1.8-2-3.6-3-5-3.5zM26 19c-1-5 2.5-8 7-6.5-2.6 1.6-3 4-2.2 6-2-.6-3.6-.2-4.8.5z",
                Holly,
            )
            dot(23.5f, 23f, 2.4f, RedBright)
            dot(27f, 24.5f, 2.4f, Red)
            dot(24.6f, 26.8f, 2.2f, RedBright)
        }

    val icons: Map<String, HolidayIconArt> =
        mapOf(
            "movies" to movies,
            "shows" to shows,
            "live" to live,
            "games" to games,
            "music" to music,
            "books" to books,
        )

    /** A small gold snowflake beside the icon you're on */
    val mark: HolidayArt = {
        val c = Offset(size.width / 2f, size.height - size.width / 2f)
        val r = size.width * .48f
        val w = size.width * .11f
        val gold = Color(0xFFFFD976)
        drawCircle(Brush.radialGradient(listOf(gold.copy(alpha = .5f), Color.Transparent), c, r * 1.5f), r * 1.5f, c)
        for (i in 0 until 6) {
            val a = (i * 60f - 90f) * PI.toFloat() / 180f
            val tip = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
            drawLine(gold, c, tip, w, StrokeCap.Round)
            val mid = Offset(c.x + cos(a) * r * .6f, c.y + sin(a) * r * .6f)
            for (side in listOf(-1f, 1f)) {
                val b = a + side * .7f
                drawLine(gold, mid, Offset(mid.x + cos(b) * r * .32f, mid.y + sin(b) * r * .32f), w * .8f, StrokeCap.Round)
            }
        }
    }

    /** Big glass bulbs hanging from a dark wire across the top of Home's picture */
    val topTrim: HolidayArt = {
        val d = density
        val swag = 150f * d
        val n = (size.width / swag).toInt().coerceAtLeast(1)
        val step = size.width / n
        val top = 3f * d
        val sag = 10f * d
        val wire = Color(0xFF16251B)
        fun at(
            a: Float,
            t: Float,
        ): Offset {
            // a gentle curve between two hooks
            val x = a + step * t
            val y = top + sag * 4f * t * (1f - t)
            return Offset(x, y)
        }
        for (i in 0 until n) {
            val a = i * step
            val path = Path()
            path.moveTo(a, top)
            path.quadraticTo(a + step / 2f, top + sag * 2f, a + step, top)
            drawPath(path, wire, style = Stroke(width = 1.8f * d))
            listOf(.17f, .5f, .83f).forEachIndexed { j, t ->
                val k = i * 3 + j
                val o = at(a, t)
                drawBulb(o.x, o.y - d, Bulbs[k % Bulbs.size], d)
            }
        }
        for (i in 0..n) drawCircle(Color(0xFF2A3B2F), 2.2f * d, Offset(i * step, top))
    }

    /** One old-fashioned glass bulb: soft glow, dark socket, pointed glass, a shine */
    private fun DrawScope.drawBulb(
        x: Float,
        y: Float,
        c: Color,
        d: Float,
    ) {
        val glowC = Offset(x, y + 15f * d)
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = .5f), Color.Transparent), glowC, 14f * d), 14f * d, glowC)
        drawRoundRect(Color(0xFF1C3524), Offset(x - 2.6f * d, y), Size(5.2f * d, 6.4f * d), CornerRadius(1.2f * d))
        val glass = Path()
        glass.moveTo(x, y + 5f * d)
        glass.cubicTo(x - 5.4f * d, y + 8f * d, x - 6.1f * d, y + 16f * d, x, y + 23.5f * d)
        glass.cubicTo(x + 6.1f * d, y + 16f * d, x + 5.4f * d, y + 8f * d, x, y + 5f * d)
        glass.close()
        drawPath(
            glass,
            Brush.radialGradient(
                listOf(androidx.compose.ui.graphics.lerp(c, Color.White, .45f), c, androidx.compose.ui.graphics.lerp(c, Color.Black, .35f)),
                Offset(x - 2f * d, y + 11f * d),
                10f * d,
            ),
        )
        drawOval(Color.White.copy(alpha = .55f), Offset(x - 3f * d, y + 8.5f * d), Size(1.8f * d, 5f * d))
    }

    /** Soft snow drifts along the bottom of Home, with a few sparkles */
    val bottomTrim: HolidayArt = {
        val w = size.width
        val h = size.height
        fun drift(
            base: Float,
            amp: Float,
            seed: Float,
        ): Path {
            val path = Path()
            path.moveTo(0f, h)
            val n = 24
            var px = 0f
            var py = 0f
            for (i in 0..n) {
                val x = w * i / n
                val y = base - amp * (.55f + .45f * sin(x / w * PI.toFloat() * 3f + seed)) - amp * .35f * sin(x / w * PI.toFloat() * 9f + seed * 2f)
                if (i == 0) {
                    path.lineTo(x, y)
                } else {
                    path.quadraticTo((px + x) / 2f, minOf(py, y) - amp * .25f, x, y)
                }
                px = x
                py = y
            }
            path.lineTo(w, h)
            path.close()
            return path
        }
        drawPath(drift(h * .55f, h * .22f, 1.3f), Brush.verticalGradient(listOf(Color(0xFFDFEAF6), Color(0xFFA9C2DC))), alpha = .9f)
        drawPath(drift(h * .78f, h * .2f, .2f), Brush.verticalGradient(listOf(Color.White, Color(0xFFE9F2FB), Color(0xFFBCD3EA))))
        val r = 1.2f * density
        for (i in 0 until 30) {
            val x = ((i * 137) % 997) / 997f * w
            val y = h * (.62f + ((i * 61) % 31) / 31f * .33f)
            drawCircle(Color.White.copy(alpha = .8f), r, Offset(x, y))
        }
    }

    /** Christmas films to try first for Home's Christmas pick */
    val pickTitles =
        listOf(
            "Elf",
            "Home Alone",
            "The Polar Express",
            "A Christmas Story",
            "How the Grinch Stole Christmas",
            "The Santa Clause",
            "Klaus",
            "National Lampoon's Christmas Vacation",
            "The Muppet Christmas Carol",
            "Arthur Christmas",
        )
}
