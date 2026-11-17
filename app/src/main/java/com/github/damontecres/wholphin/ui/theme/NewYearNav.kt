package com.github.damontecres.wholphin.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * New Year's on the new navigation (Ben 2026-11-17, sheet newyear-4-navigation, "Midnight Fringe"):
 * - the rail and phone-tab icons ARE the New Year party charms already used on the doors (gold star for Home,
 *   balloons for Movies, pocket watch at 11:59 for Shows, mirror ball for Live TV, paper rosette for Games, bells
 *   for Music, folded gold star for Books), glowing gold on the one you're on;
 * - a gold and silver foil fringe across the top of Home, confetti settled along the bottom, both all season;
 * - at midnight on New Year's Eve the fringe flashes and fresh confetti falls for half a minute (nq-midnight A);
 * - the first carousel pick is a New Year film, and a fresh-start film from New Year's Day (nq-pick A).
 */
object NewYearNav {
    private val Gold = Color(0xFFE8B84A)
    private val GoldLight = Color(0xFFF7D98A)
    private val GoldDark = Color(0xFFB98F2E)
    private val GoldEdge = Color(0xFF7A5A16)
    private val Silver = Color(0xFFC9D1DE)
    private val SilverDark = Color(0xFF8E97A8)
    private val Ink = Color(0xFF1B1B1F)
    private val Face = Color(0xFFFBF4E2)

    /** The first half-minute of New Year's Day: the trims celebrate */
    fun bursting(now: LocalDateTime = LocalDateTime.now()): Boolean =
        now.month == Month.JANUARY && now.dayOfMonth == 1 && now.hour == 0 && now.minute == 0 && now.second < 30

    // ---------- the pick ----------

    private val EVE_TITLES =
        listOf(
            "When Harry Met Sally...",
            "The Apartment",
            "New Year's Eve",
            "Trading Places",
            "The Hudsucker Proxy",
            "Ghostbusters II",
            "200 Cigarettes",
            "Holiday Inn",
            "Happy New Year, Charlie Brown",
        )
    private val FRESH_TITLES =
        listOf(
            "About Time",
            "Groundhog Day",
            "The Secret Life of Walter Mitty",
            "Yes Man",
            "Julie & Julia",
            "Chef",
            "The Intern",
            "Soul",
            "Inside Out",
            "Zootopia",
        )

    /** New Year films up to midnight, fresh-start films on New Year's Day (read each time the picks are built) */
    val pickTitles: List<String> =
        object : AbstractList<String>() {
            private fun now() = if (LocalDate.now().month == Month.JANUARY) FRESH_TITLES else EVE_TITLES

            override val size: Int get() = now().size

            override fun get(index: Int): String = now()[index]
        }

    // ---------- icons ----------

    /** How the usual icon colour maps onto a charm: dim before it lights up, a gold glow on the one you're on */
    private fun DrawScope.charm(
        color: Color,
        draw: DrawScope.(a: Float) -> Unit,
    ) {
        val hi = max(color.red, max(color.green, color.blue))
        val lo = min(color.red, min(color.green, color.blue))
        val sat = hi - lo
        val dim = color.alpha < .7f || (sat < .12f && hi < .66f)
        val a = if (dim) .38f * color.alpha.coerceAtLeast(.5f) else 1f
        val u = size.minDimension / 24f
        val c = Offset(size.width / 2, size.height / 2)
        if (!dim && sat > .3f) {
            drawCircle(
                Brush.radialGradient(listOf(Gold.copy(alpha = .5f), Color.Transparent), c, 15f * u),
                15f * u,
                c,
            )
        }
        // Drawn a little bigger than the usual icon so the detail reads from the couch
        withTransform({
            translate(c.x - 12f * u * 1.18f, c.y - 12f * u * 1.18f)
            scale(u * 1.18f, u * 1.18f, Offset.Zero)
        }) { draw(a) }
    }

    private fun starPath(
        cx: Float,
        cy: Float,
        outer: Float,
        inner: Float,
    ): Pair<Path, List<Offset>> {
        val pts =
            (0 until 10).map { k ->
                val r = if (k % 2 == 0) outer else inner
                val ang = -PI / 2 + k * PI / 5
                Offset(cx + (r * cos(ang)).toFloat(), cy + (r * sin(ang)).toFloat())
            }
        val p =
            Path().apply {
                moveTo(pts[0].x, pts[0].y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
        return p to pts
    }

    /** A faceted gold star: light and dark halves on each point, a dark edge */
    private fun DrawScope.goldStar(
        cx: Float,
        cy: Float,
        outer: Float,
        inner: Float,
        a: Float,
    ) {
        val (p, pts) = starPath(cx, cy, outer, inner)
        drawPath(p, Gold, alpha = a)
        for (k in 0 until 10 step 2) {
            val f =
                Path().apply {
                    moveTo(cx, cy)
                    lineTo(pts[k].x, pts[k].y)
                    lineTo(pts[(k + 1) % 10].x, pts[(k + 1) % 10].y)
                    close()
                }
            drawPath(f, GoldDark, alpha = a)
            val g =
                Path().apply {
                    moveTo(cx, cy)
                    lineTo(pts[k].x, pts[k].y)
                    lineTo(pts[(k + 9) % 10].x, pts[(k + 9) % 10].y)
                    close()
                }
            drawPath(g, GoldLight, alpha = a * .7f)
        }
        drawPath(p, GoldEdge, alpha = a, style = Stroke(width = .55f))
    }

    private fun DrawScope.sparkle(
        x: Float,
        y: Float,
        r: Float,
        a: Float,
    ) {
        val p =
            Path().apply {
                moveTo(x, y - r)
                quadraticTo(x, y, x + r, y)
                quadraticTo(x, y, x, y + r)
                quadraticTo(x, y, x - r, y)
                quadraticTo(x, y, x, y - r)
                close()
            }
        drawPath(p, Color.White, alpha = a)
    }

    private val home: HolidayIconArt = { color, t ->
        charm(color) { a ->
            goldStar(12f, 13f, 10.5f, 4.4f, a)
            sparkle(20.5f, 3.8f, 2.6f, a * (.55f + .45f * sin(t * 2.2f)))
        }
    }

    private val books: HolidayIconArt = { color, t ->
        charm(color) { a ->
            val sway = sin(t * 1.3f) * 5f
            rotate(sway, Offset(12f, 1.5f)) {
                drawLine(Gold, Offset(12f, 1.5f), Offset(12f, 7f), strokeWidth = .9f, alpha = a)
                drawCircle(GoldLight, 1.5f, Offset(12f, 1.5f), alpha = a)
                drawCircle(GoldEdge, 1.5f, Offset(12f, 1.5f), alpha = a, style = Stroke(.4f))
                goldStar(12f, 15f, 8.6f, 3.7f, a)
            }
        }
    }

    private fun DrawScope.balloon(
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        fill: Color,
        edge: Color,
        a: Float,
    ) {
        drawOval(fill, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), alpha = a)
        drawOval(edge, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), alpha = a, style = Stroke(.5f))
        drawOval(Color.White, Offset(cx - rx * .62f, cy - ry * .68f), Size(rx * .5f, ry * .62f), alpha = a * .55f)
        val k =
            Path().apply {
                moveTo(cx - .9f, cy + ry + .9f)
                lineTo(cx + .9f, cy + ry + .9f)
                lineTo(cx, cy + ry - .2f)
                close()
            }
        drawPath(k, fill, alpha = a)
    }

    private val movies: HolidayIconArt = { color, t ->
        charm(color) { a ->
            val bob = sin(t * 1.6f) * .6f
            val knot = Offset(12.5f, 22.5f)
            val string = Color(0xFFEDEDED)
            drawLine(string, Offset(7.5f, 13.6f + bob), knot, strokeWidth = .45f, alpha = a)
            drawLine(string, Offset(16.8f, 12.2f - bob), knot, strokeWidth = .45f, alpha = a)
            drawLine(string, Offset(12.5f, 17f), knot, strokeWidth = .45f, alpha = a)
            balloon(7.5f, 7.6f + bob, 5.2f, 6f, Gold, GoldEdge, a)
            balloon(16.8f, 6.6f - bob, 4.7f, 5.6f, Silver, SilverDark, a)
            goldStar(12.5f, 14.2f, 3.9f, 1.7f, a)
            drawCircle(Gold, 1.3f, knot, alpha = a)
        }
    }

    private val shows: HolidayIconArt = { color, t ->
        charm(color) { a ->
            val c = Offset(12f, 14f)
            drawLine(Gold, Offset(12f, -.5f), Offset(12f, 3f), strokeWidth = .9f, alpha = a)
            drawCircle(Gold, 1.5f, Offset(12f, 2.4f), alpha = a, style = Stroke(.7f))
            drawRect(Gold, Offset(10.6f, 3.4f), Size(2.8f, 2.2f), alpha = a)
            drawCircle(GoldDark, 9.4f, c, alpha = a)
            drawCircle(Gold, 8.8f, c, alpha = a)
            drawCircle(GoldLight, 8.8f, c, alpha = a * .5f, style = Stroke(.5f))
            drawCircle(Face, 7.2f, c, alpha = a)
            for (h in 0 until 12) {
                val ang = h * PI / 6
                val o = Offset(c.x + (6.6f * sin(ang)).toFloat(), c.y - (6.6f * cos(ang)).toFloat())
                val i =
                    Offset(
                        c.x +
                            (
                                (
                                    if (h % 3 ==
                                        0
                                    ) {
                                        5.3f
                                    } else {
                                        5.9f
                                    }
                                ) * sin(ang)
                            ).toFloat(),
                        c.y - ((if (h % 3 == 0) 5.3f else 5.9f) * cos(ang)).toFloat(),
                    )
                drawLine(Ink, i, o, strokeWidth = if (h % 3 == 0) .7f else .4f, alpha = a)
            }
            // 11:59
            rotate(-2f, c) { drawLine(Ink, c, Offset(c.x, c.y - 4f), strokeWidth = 1.1f, alpha = a, cap = StrokeCap.Round) }
            rotate(-6f, c) { drawLine(Ink, c, Offset(c.x, c.y - 5.8f), strokeWidth = .7f, alpha = a, cap = StrokeCap.Round) }
            rotate((t * 6f) % 360f, c) { drawLine(Color(0xFFD7263D), c, Offset(c.x, c.y - 6f), strokeWidth = .35f, alpha = a) }
            drawCircle(Ink, .7f, c, alpha = a)
        }
    }

    private val live: HolidayIconArt = { color, t ->
        charm(color) { a ->
            val c = Offset(12f, 14f)
            drawLine(Gold, Offset(12f, -.5f), Offset(12f, 3.2f), strokeWidth = .7f, alpha = a)
            drawRect(Gold, Offset(10.5f, 3f), Size(3f, 2f), alpha = a)
            drawCircle(SilverDark, 9f, c, alpha = a)
            val ball =
                Path().apply {
                    addOval(
                        androidx.compose.ui.geometry
                            .Rect(c, 9f),
                    )
                }
            val tiles =
                listOf(Color(0xFFD7DDE8), Color(0xFFAEB7C6), Color(0xFF6E788A), Color(0xFFE9EEF5), Color(0xFF9CC3E6), Color(0xFFBFC7D4))
            clipPath(ball) {
                var row = 0
                var y = 5f
                while (y < 23f) {
                    var x = 3f + (row % 2) * .9f
                    var col = 0
                    while (x < 21f) {
                        val shade = tiles[(row * 7 + col * 3 + row * col) % tiles.size]
                        drawRect(shade, Offset(x, y), Size(1.6f, 1.6f), alpha = a)
                        x += 1.85f
                        col++
                    }
                    y += 1.85f
                    row++
                }
            }
            drawCircle(Ink, 9f, c, alpha = a * .6f, style = Stroke(.5f))
            sparkle(8.2f, 10.2f, 2.6f, a * (.5f + .5f * sin(t * 2.6f)))
            sparkle(16.4f, 17.4f, 1.8f, a * (.5f + .5f * sin(t * 2.6f + 2f)))
        }
    }

    private val games: HolidayIconArt = { color, t ->
        charm(color) { a ->
            val c = Offset(12f, 12f)
            val n = 18
            rotate(sin(t * .8f) * 4f, c) {
                for (k in 0 until n) {
                    val a0 = 2 * PI * k / n - PI / 2
                    val a1 = 2 * PI * (k + 1) / n - PI / 2
                    val r = 11.4f
                    val p =
                        Path().apply {
                            moveTo(c.x, c.y)
                            lineTo(c.x + (r * cos(a0)).toFloat(), c.y + (r * sin(a0)).toFloat())
                            lineTo(c.x + ((r - .9f) * cos((a0 + a1) / 2)).toFloat(), c.y + ((r - .9f) * sin((a0 + a1) / 2)).toFloat())
                            lineTo(c.x + (r * cos(a1)).toFloat(), c.y + (r * sin(a1)).toFloat())
                            close()
                        }
                    drawPath(p, if (k % 2 == 0) Gold else Ink, alpha = a)
                    drawPath(p, if (k % 2 == 0) GoldLight else GoldDark, alpha = a * if (k % 2 == 0) .3f else 1f, style = Stroke(.35f))
                }
            }
            drawCircle(Ink, 5f, c, alpha = a)
            drawCircle(Gold, 4.4f, c, alpha = a, style = Stroke(.9f))
            goldStar(c.x, c.y + .2f, 3f, 1.25f, a)
        }
    }

    private fun DrawScope.bell(
        cx: Float,
        cy: Float,
        tilt: Float,
        a: Float,
    ) {
        rotate(tilt, Offset(cx, cy - 5f)) {
            translate(cx, cy) {
                val p =
                    Path().apply {
                        moveTo(-1.3f, -5f)
                        cubicTo(-3.8f, -4.6f, -3.8f, -1f, -4f, 1.6f)
                        lineTo(-5.2f, 4.2f)
                        lineTo(5.2f, 4.2f)
                        lineTo(4f, 1.6f)
                        cubicTo(3.8f, -1f, 3.8f, -4.6f, 1.3f, -5f)
                        close()
                    }
                drawPath(p, Gold, alpha = a)
                drawPath(p, GoldEdge, alpha = a, style = Stroke(.45f))
                drawLine(GoldLight, Offset(-2.4f, -3f), Offset(-2.9f, 2.4f), strokeWidth = .8f, alpha = a * .8f)
                drawLine(GoldDark, Offset(-5f, 3.6f), Offset(5f, 3.6f), strokeWidth = .6f, alpha = a)
                drawCircle(GoldDark, 1.1f, Offset(0f, 5.1f), alpha = a)
            }
        }
    }

    private val music: HolidayIconArt = { color, t ->
        charm(color) { a ->
            val swing = sin(t * 2.4f) * 7f
            drawLine(Gold, Offset(12f, 4.5f), Offset(7.6f, 9f), strokeWidth = .8f, alpha = a)
            drawLine(Gold, Offset(12f, 4.5f), Offset(16.4f, 9f), strokeWidth = .8f, alpha = a)
            bell(7.3f, 14.6f, -14f + swing, a)
            bell(16.7f, 14.6f, 14f + swing, a)
            drawOval(Gold, Offset(6.8f, 1.6f), Size(5f, 3.4f), alpha = a, style = Stroke(.8f))
            drawOval(Gold, Offset(12.2f, 1.6f), Size(5f, 3.4f), alpha = a, style = Stroke(.8f))
            drawCircle(Gold, 1.3f, Offset(12f, 3.3f), alpha = a)
        }
    }

    val icons: Map<String, HolidayIconArt> =
        mapOf(
            HolidayIconKey.HOME to home,
            "movies" to movies,
            "shows" to shows,
            "live" to live,
            "games" to games,
            "music" to music,
            "books" to books,
        )

    // ---------- Home trims ----------

    /** A steady 0..1 number for piece [i], so the fringe and confetti look the same on every frame */
    private fun hash(
        i: Int,
        salt: Int,
    ): Float {
        var x = i * 374761393 + salt * 668265263
        x = (x xor (x ushr 13)) * 1274126177
        return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
    }

    /** Gold and silver foil fringe hanging across the top of Home, shimmering and swaying a little */
    val topTrim: HolidayArt = { t ->
        val d = density
        val burst = bursting()
        val flash = if (burst) .5f + .5f * sin(t * 9f) else 0f
        val colors = listOf(Gold, GoldLight, Silver, GoldDark, Color.White)
        val step = 3.2f * d
        val n = (size.width / step).toInt() + 1
        val strand = 2.3f * d
        drawRect(Gold, Offset.Zero, Size(size.width, 3f * d))
        for (i in 0..n) {
            val x = i * step + hash(i, 1) * d
            val len = size.height * (.6f + .4f * hash(i, 2)) * (1f + flash * .15f)
            val sway = sin(t * 1.3f + i * .37f) * 1.4f * d
            val col = colors[(hash(i, 3) * colors.size).toInt().coerceAtMost(colors.size - 1)]
            val shimmer = .78f + .22f * sin(t * 2.1f + i * .55f)
            val p =
                Path().apply {
                    moveTo(x, 2f * d)
                    quadraticTo(x + sway * .3f, len * .5f, x + sway, len)
                    lineTo(x + sway + strand, len - .6f * d)
                    quadraticTo(x + strand + sway * .3f, len * .5f, x + strand, 2f * d)
                    close()
                }
            drawPath(p, col, alpha = (shimmer + flash * .2f).coerceAtMost(1f))
        }
        if (flash > 0f) drawRect(Brush.verticalGradient(listOf(GoldLight.copy(alpha = .35f * flash), Color.Transparent)), size = size)
    }

    /** Confetti settled along the bottom of Home; at midnight fresh confetti falls into it */
    val bottomTrim: HolidayArt = { t ->
        val d = density
        val colors = listOf(Gold, Silver, GoldLight, Color(0xFF7FA6FF), Color.White)
        val n = (size.width / (7f * d)).toInt()

        fun piece(
            i: Int,
            x: Float,
            y: Float,
            spin: Float,
            a: Float,
        ) {
            val col = colors[i % colors.size]
            if (i % 4 == 0) {
                drawCircle(col, (1.8f + hash(i, 6)) * d, Offset(x, y), alpha = a)
            } else {
                rotate(hash(i, 7) * 180f + spin, Offset(x, y)) {
                    drawRect(col, Offset(x - 3.5f * d, y - 1.4f * d), Size((5.5f + hash(i, 8) * 2.5f) * d, 2.8f * d), alpha = a)
                }
            }
        }
        for (i in 0 until n) {
            val x = hash(i, 4) * size.width
            val y = size.height * (.35f + .6f * hash(i, 5))
            val twinkle = if (i % 9 == 0) .6f + .4f * sin(t * 2f + i) else .9f
            piece(i, x, y, 0f, twinkle)
        }
        if (bursting()) {
            for (i in 0 until n / 2) {
                val x = hash(i, 9) * size.width + sin(t * 2f + i) * 4f * d
                val y = ((hash(i, 10) + t * .5f) % 1f) * size.height
                piece(i + 1, x, y, t * 220f, 1f)
            }
        }
    }
}
