package com.github.damontecres.wholphin.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.nav.RailIntro
import java.time.LocalDateTime
import java.time.Month
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The 4th of July look for the new navigation (approved by Ben 2026-11-17, sheet july4-nav-1-directions):
 * - the rail and phone-tab icons themselves in red, white and blue, taken in turn (nothing beside the names)
 * - a twinkling gold star beside the icon you're on
 * - pleated red, white and navy fan bunting hanging in swags across the top of Home, with lit sparklers fizzing
 *   along the bottom
 * - on the 4th from 9 PM bigger fireworks burst over the bunting (the same time as the Who's watching big show)
 * - "3 days to the Fourth" as the first Today card, "It's the 4th!" on the day
 * - Kids too (the pick is a Kids-safe one there, as Kids only ever see Kids titles)
 */
object July4Nav {
    private val Red = Color(0xFFE0404F)
    private val RedDark = Color(0xFF9E1E2C)
    private val Navy = Color(0xFF24366E)
    private val Cream = Color(0xFFF4F0E8)
    private val CreamShade = Color(0xFFCFC7B8)
    private val Gold = Color(0xFFFFD25A)
    private val GoldLight = Color(0xFFFFF3C4)
    private val Brass = Color(0xFFE9C46A)

    /** The icon colours, taken in turn down the rail: red, white, blue */
    private val IconRed = Color(0xFFFF5A68)
    private val IconWhite = Color(0xFFFFFFFF)
    private val IconBlue = Color(0xFF7FA0FF)

    private val ICONS =
        listOf(
            HolidayIconKey.HOME to (BenflixIcon.HOME to IconRed),
            "movies" to (BenflixIcon.FILM to IconWhite),
            "shows" to (BenflixIcon.TV to IconBlue),
            "live" to (BenflixIcon.LIVE to IconRed),
            "games" to (BenflixIcon.PAD to IconWhite),
            "music" to (BenflixIcon.NOTE to IconBlue),
            "books" to (BenflixIcon.BOOK to IconRed),
            HolidayIconKey.SEARCH to (BenflixIcon.SEARCH to IconWhite),
            HolidayIconKey.REQUESTS to (BenflixIcon.REQUESTS to IconBlue),
            HolidayIconKey.MORE to (BenflixIcon.MORE to IconWhite),
        )

    fun nav(): HolidayNav =
        HolidayNav(
            key = "july4",
            isOn = { J4Look.on },
            railTop = Color(0xFF14214A),
            railBottom = Color(0xFF0B1430),
            lights = listOf(Red, IconWhite, IconBlue),
            icons = ICONS.associate { (key, v) -> key to icon(v.first, v.second) },
            mark = { t -> star(this, t) },
            topTrim = { t -> bunting(this, t) },
            bottomTrim = { t -> sparklers(this, t) },
            moving = true,
            countdown = { countdown(LocalDateTime.now()) },
            countdownKind = "4th of July",
            pickWhy = "4th of July pick",
            pickTitles = listOf("Independence Day", "The Sandlot", "Jaws", "1776", "Yankee Doodle Dandy", "National Treasure"),
            accent = Color(0xFFFF8A93),
        )

    /** "3 days to the Fourth", then "The Fourth is tomorrow", then "It's the 4th!" on the day (Ben's pick) */
    fun countdown(now: LocalDateTime): String? {
        if (!JulySeason.isSeason(now)) return null
        return if (now.dayOfMonth == 4) "It's the 4th!" else JulySeason.countdown(now)
    }

    // ---------- icons ----------

    private val paths = HashMap<BenflixIcon, List<Path>>()

    private fun pathsOf(icon: BenflixIcon): List<Path> =
        paths.getOrPut(icon) { icon.paths.map { PathParser().parsePathString(it).toPath() } }

    /**
     * The usual line icon, in its holiday colour. While the rail lights up at start-up it takes the colours it's
     * given (grey, then the holiday's lights), so the light-up still plays.
     */
    private fun icon(
        icon: BenflixIcon,
        color: Color,
    ): HolidayIconArt =
        { given, _ ->
            val c = if (RailIntro.waiting) given else color
            val s = size.minDimension / 24f
            translate((size.width - 24f * s) / 2, (size.height - 24f * s) / 2) {
                scale(s, s, pivot = Offset.Zero) {
                    pathsOf(icon).forEach {
                        drawPath(it, c, style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }

    // ---------- the twinkling star beside the icon you're on ----------

    private fun starPath(
        cx: Float,
        cy: Float,
        r: Float,
        inner: Float = .42f,
        turn: Float = 0f,
    ): Path =
        Path().apply {
            for (k in 0 until 10) {
                val a = -PI.toFloat() / 2 + turn + k * PI.toFloat() / 5
                val rr = if (k % 2 == 1) r * inner else r
                val x = cx + cos(a) * rr
                val y = cy + sin(a) * rr
                if (k == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

    private fun star(
        d: DrawScope,
        t: Float,
    ): Unit = with(d) {
        // A bit bigger than its little box, so it reads from the couch
        val pulse = 1f + .14f * sin(t * 2.8f)
        val r = size.width * 1.0f * pulse
        val c = Offset(size.width / 2, size.height / 2)
        rotate(10f * sin(t * 1.4f), c) {
            drawCircle(Gold.copy(alpha = .22f), r * 1.25f, c)
            drawPath(starPath(c.x, c.y, r), Gold)
            drawPath(starPath(c.x, c.y, r * .5f), GoldLight)
        }
    }

    // ---------- Home: fan bunting along the top ----------

    /** A pleated half-rosette hanging down from (cx, top): red and cream pleats, a navy middle with three stars */
    private fun DrawScope.fan(
        cx: Float,
        top: Float,
        r: Float,
    ) {
        val n = 10
        for (i in 0 until n) {
            val a0 = i * PI.toFloat() / n
            val a1 = (i + 1) * PI.toFloat() / n
            val am = (a0 + a1) / 2
            val base = if (i % 2 == 0) Red else Cream
            val shade = if (i % 2 == 0) RedDark else CreamShade
            fun p(a: Float) = Offset(cx + cos(a) * r, top + sin(a) * r)
            drawPath(Path().apply { moveTo(cx, top); lineTo(p(a0).x, p(a0).y); lineTo(p(am).x, p(am).y); close() }, base)
            drawPath(Path().apply { moveTo(cx, top); lineTo(p(am).x, p(am).y); lineTo(p(a1).x, p(a1).y); close() }, shade)
        }
        val r2 = r * .45f
        drawArc(Navy, 0f, 180f, true, Offset(cx - r2, top - r2), androidx.compose.ui.geometry.Size(r2 * 2, r2 * 2))
        drawArc(
            Cream,
            0f,
            180f,
            false,
            Offset(cx - r2, top - r2),
            androidx.compose.ui.geometry.Size(r2 * 2, r2 * 2),
            style = Stroke(width = r * .07f),
        )
        listOf(-.55f to .35f, 0f to .6f, .55f to .35f).forEach { (dx, dy) ->
            drawPath(starPath(cx + dx * r2 * .8f, top + dy * r2 * .8f, r2 * .24f), Color.White)
        }
        drawRoundRect(
            Navy,
            Offset(cx - r - 1f, top - r * .08f),
            androidx.compose.ui.geometry.Size(r * 2 + 2f, r * .16f),
            androidx.compose.ui.geometry.CornerRadius(r * .08f),
        )
    }

    private fun bunting(
        d: DrawScope,
        t: Float,
    ): Unit = with(d) {
        val step = 170f * density
        val n = (size.width / step).toInt().coerceAtLeast(2)
        val w = size.width / n
        val top = 3f * density
        // The navy cord swags between the fans, with a thin white line
        for (i in 0 until n) {
            val x = i * w
            val sag = 16f * density
            val cord =
                Path().apply {
                    moveTo(x, top)
                    quadraticTo(x + w / 2, top + sag * 2, x + w, top)
                }
            drawPath(cord, Navy, style = Stroke(width = 3f * density))
            drawPath(cord, Cream.copy(alpha = .6f), style = Stroke(width = 1f * density))
        }
        for (i in 0..n) {
            // A very gentle sway, as if a summer breeze catches them
            val r = (if (i % 2 == 0) 26f else 21f) * density
            val sway = 3f * sin(t * 1.7f + i)
            rotate(sway, Offset(i * w, top)) { fan(i * w, top, r) }
            drawCircle(Brass, 2.4f * density, Offset(i * w, top + 1f * density))
        }
        if (bigShow(LocalDateTime.now())) fireworks(this, t)
    }

    /** On the 4th from 9 PM: the big show, like the Who's watching window */
    private fun bigShow(now: LocalDateTime): Boolean =
        (now.month == Month.JULY && now.dayOfMonth == 4 && now.hour >= 21)

    private fun fireworks(
        d: DrawScope,
        t: Float,
    ): Unit = with(d) {
        val bursts = listOf(Red, Color.White, IconBlue, Gold, Red)
        bursts.forEachIndexed { i, c ->
            val period = 3.6f
            val p = ((t + i * .75f) % period) / period
            if (p > .7f) return@forEachIndexed
            val grow = (p / .45f).coerceAtMost(1f)
            val fade = if (p < .45f) 1f else 1f - (p - .45f) / .25f
            val x = size.width * (.12f + i * .19f) + 40f * density * sin(i * 2.1f + (t / period).toInt())
            val y = size.height * (.9f + .25f * ((i * 7) % 3))
            val r = (34f + 8f * (i % 2)) * density * grow
            for (k in 0 until 16) {
                val a = k * PI.toFloat() / 8
                val from = Offset(x + cos(a) * r * .3f, y + sin(a) * r * .3f)
                val to = Offset(x + cos(a) * r, y + sin(a) * r)
                drawLine(c.copy(alpha = fade), from, to, strokeWidth = 2f * density, cap = StrokeCap.Round)
                drawCircle(c.copy(alpha = fade), 1.8f * density, Offset(x + cos(a) * r * 1.12f, y + sin(a) * r * 1.12f))
            }
        }
    }

    // ---------- Home: sparklers along the bottom ----------

    private fun sparklers(
        d: DrawScope,
        t: Float,
    ): Unit = with(d) {
        val step = 80f * density
        val n = (size.width / step).toInt().coerceAtLeast(3)
        val w = size.width / n
        for (i in 0 until n) {
            val x = i * w + w / 2
            val lean = (if (i % 2 == 0) -1 else 1) * 6f * density
            val tip = Offset(x + lean, size.height * .28f)
            val foot = Offset(x - lean * .2f, size.height)
            drawLine(Color(0xFF8A8F9C), foot, tip, strokeWidth = 1.6f * density)
            val handle = Offset(foot.x + (tip.x - foot.x) * .25f, foot.y + (tip.y - foot.y) * .25f)
            drawLine(Color(0xFF5A4632), foot, handle, strokeWidth = 3f * density)
            drawCircle(Gold.copy(alpha = .22f), 9f * density, tip)
            // Sparks: a fresh scatter several times a second
            val frame = (t * 9f).toInt() + i * 5
            for (k in 0 until 12) {
                val a = k * PI.toFloat() / 6 + frame * .7f
                val len = (if ((k + frame) % 2 == 0) 9f else 5.5f) * density
                val c = listOf(GoldLight, Gold, Color.White)[(k + frame) % 3]
                drawLine(c, tip, Offset(tip.x + cos(a) * len, tip.y + sin(a) * len), strokeWidth = .9f * density)
            }
            drawCircle(Color(0xFFFFF9E0), 2.4f * density, tip)
        }
    }
}
