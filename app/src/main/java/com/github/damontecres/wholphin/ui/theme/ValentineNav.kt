package com.github.damontecres.wholphin.ui.theme

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import java.time.LocalDate
import kotlin.math.sin

/**
 * Valentine's Day for the new navigation (Ben 2026-11-17, sheet valentines-3-navigation, "Love tokens"): the six
 * love tokens from the old door tiles become the rail and phone-tab icons (Admit Two ticket, gold heart locket, red
 * foil heart balloon, ace of hearts, heart-shaped record, love letter with a wax seal). Home, Search, Requests and
 * More keep their usual shapes in rose. Home gets paper hearts on twine across the top (gently swaying) and a red
 * satin ribbon tied in a bow along the bottom. Every drawing is the same as the web's (js/holidaynav.js).
 */
object ValentineNav {
    private val ROSE = Color(0xFFE8405E)
    private val BLUSH = Color(0xFFF7A3BA)
    private val PALE = Color(0xFFFFE6EA)
    private val DEEP = Color(0xFFC2264A)
    private val GOLD_HI = Color(0xFFFFE9A3)
    private val GOLD = Color(0xFFE8C15A)
    private val GOLD_LO = Color(0xFF9C6E1E)

    private fun p(d: String): Path = PathParser().parsePathString(d).toPath()

    /** A heart in a 24 box (the same heart as the web's) */
    private val HEART by lazy {
        p("M12 21.3C6.2 17.1 2.5 13.6 2.5 9.3 2.5 6.3 4.8 4 7.6 4c1.8 0 3.4.9 4.4 2.4C13 4.9 14.6 4 16.4 4c2.8 0 5.1 2.3 5.1 5.3 0 4.3-3.7 7.8-9.5 12z")
    }

    /** Draws a heart [s] wide with its box's top-left at ([x], [y]) */
    private fun DrawScope.heart(
        x: Float,
        y: Float,
        s: Float,
        fill: Brush?,
        stroke: Color? = null,
        strokeW: Float = 1f,
    ) {
        withTransform({
            translate(x, y)
            scale(s / 24f, s / 24f, Offset.Zero)
        }) {
            fill?.let { drawPath(HEART, it) }
            stroke?.let { drawPath(HEART, it, style = Stroke(strokeW * 24f / s)) }
        }
    }

    private fun solid(c: Color) = Brush.linearGradient(listOf(c, c))

    private fun gold(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
    ) = Brush.linearGradient(listOf(GOLD_HI, GOLD, GOLD_LO), Offset(x0, y0), Offset(x1, y1))

    /** Runs [block] in a 32-unit box centred on the canvas, drawn a little bigger than the usual icon */
    private fun DrawScope.box32(block: DrawScope.() -> Unit) {
        val k = size.minDimension * 1.2f / 32f
        withTransform({
            translate((size.width - 32f * k) / 2f, (size.height - 32f * k) / 2f)
            scale(k, k, Offset.Zero)
        }) { block() }
    }

    // ---------- the six love tokens (32-unit drawings) ----------

    private val TICKET by lazy { p("M3 9h26v4.3a2.7 2.7 0 0 0 0 5.4V23H3v-4.3a2.7 2.7 0 0 0 0-5.4z") }
    private val SEAL by lazy { p("M16 12.2c2.7 0 5 1.8 5 4.6s-2.3 5-5 5-5.2-2-5-5 2.3-4.6 5-4.6z") }
    private val STRING by lazy { p("M16 24c-2 2 2 3 0 5s1.5 2 .5 3") }
    private val KNOT by lazy { p("M15.2 22.6l.8 1.4.8-1.4z") }
    private val ACE by lazy { p("M9.4 9 L11 3.6 L12.6 9 M10 7.2 H12 M22.6 23.4 L21 28.8 L19.4 23.4 M22 25.2H20") }
    private val CHAIN by lazy { p("M6 1.5 16 12 26 1.5") }
    private val SHINE by lazy { p("M7 9.5c1.5-2.5 4-3 6-1.5") }
    private val FLAP by lazy { p("M2.8 7.6 16 17.5 29.2 7.6") }
    private val FOLDS by lazy { p("M2.8 24.4 12.5 15M29.2 24.4 19.5 15") }

    private val movies: HolidayIconArt = { _, _ ->
        box32 {
            rotate(-14f, Offset(16f, 16f)) {
                drawPath(TICKET, ROSE)
                drawPath(TICKET, Color(0xFFFFD5DC), style = Stroke(.8f))
                drawLine(Color(0xFFFFE3E8), Offset(21.5f, 10f), Offset(21.5f, 22f), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.4f, 1.2f)))
                heart(7.2f, 10.6f, 10f, solid(Color.White))
                for (y in listOf(13.5f, 16f, 18.5f)) drawLine(Color(0xFFFFE3E8), Offset(23.5f, y), Offset(27f, y), 1f)
            }
        }
    }

    private val shows: HolidayIconArt = { _, _ ->
        box32 {
            drawPath(CHAIN, gold(6f, 0f, 26f, 12f), style = Stroke(1.4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.6f, .9f))))
            heart(4f, 9f, 24f, gold(4f, 9f, 28f, 30f), Color(0xFF8A5D12), .9f)
            drawLine(Color(0xFF8A5D12).copy(alpha = .7f), Offset(16f, 15f), Offset(16f, 28f), .7f)
            heart(9.5f, 14f, 13f, null, Color(0xFFFFF2C4).copy(alpha = .9f), 1.4f)
            drawCircle(gold(14f, 11f, 18f, 14f), 1.6f, Offset(16f, 12.6f))
        }
    }

    private val live: HolidayIconArt = { _, t ->
        box32 {
            // the balloon bobs gently on its string
            val bob = sin(t * 1.6f) * .7f
            withTransform({ translate(0f, bob) }) {
                heart(
                    3f,
                    0f,
                    26f,
                    Brush.radialGradient(
                        listOf(Color(0xFFFFC2CC), Color(0xFFF0506B), Color(0xFFB11F3F), ROSE),
                        Offset(10f, 6f),
                        24f,
                    ),
                    Color(0xFF8E0F2E),
                    .6f,
                )
                rotate(-30f, Offset(10.5f, 7.5f)) {
                    drawOval(Color.White.copy(alpha = .55f), Offset(7.9f, 3.9f), Size(5.2f, 7.2f))
                }
                drawPath(KNOT, Color(0xFFB11F3F))
            }
            drawPath(STRING, Color(0xFFF4E9D8), style = Stroke(.9f))
        }
    }

    private val games: HolidayIconArt = { _, _ ->
        box32 {
            rotate(10f, Offset(16f, 16f)) {
                drawRoundRect(Color(0xFFFFFDF7), Offset(7f, 2.5f), Size(18f, 26f), CornerRadius(2.4f))
                drawRoundRect(Color(0xFFD9CFC2), Offset(7f, 2.5f), Size(18f, 26f), CornerRadius(2.4f), style = Stroke(.8f))
                heart(9.5f, 9.5f, 13f, solid(ROSE))
                drawPath(ACE, ROSE, style = Stroke(1.1f, cap = StrokeCap.Round))
            }
        }
    }

    private val music: HolidayIconArt = { _, _ ->
        box32 {
            heart(1f, 1.5f, 30f, solid(Color(0xFFB5173A)))
            heart(5f, 5.5f, 22f, null, Color(0xFF7A0E28), .8f)
            heart(8.5f, 9f, 15f, null, Color(0xFF7A0E28), .8f)
            drawPath(SHINE, Color.White.copy(alpha = .45f), style = Stroke(1.1f))
            drawCircle(Color(0xFFFFE3E8), 4f, Offset(16f, 15.5f))
            drawCircle(Color(0xFF2A0A14), 1f, Offset(16f, 15.5f))
        }
    }

    private val books: HolidayIconArt = { _, _ ->
        box32 {
            rotate(-8f, Offset(16f, 16f)) {
                drawRoundRect(Color(0xFFF6EBDD), Offset(2.5f, 7f), Size(27f, 18f), CornerRadius(1.6f))
                drawRoundRect(Color(0xFFCDBBA5), Offset(2.5f, 7f), Size(27f, 18f), CornerRadius(1.6f), style = Stroke(.7f))
                drawPath(FLAP, Color(0xFFCDBBA5), style = Stroke(.9f))
                drawPath(FOLDS, Color(0xFFE0D2BF), style = Stroke(.7f))
                drawPath(SEAL, Color(0xFFB11F3F))
                heart(12.6f, 13.6f, 6.8f, solid(Color(0xFF7E0F2B)))
            }
        }
    }

    // ---------- Home, Search, Requests, More: the usual shapes, in rose (24-unit line drawings) ----------

    private val HOME by lazy { p("M3 11l9-7 9 7M5 10v10h14V10M10 20v-5h4v5") }
    private val SEARCH by lazy { p("M17 11a6 6 0 1 1-12 0 6 6 0 0 1 12 0zM20 20l-4.5-4.5") }
    private val REQ by lazy { p("M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5L18 18M6 18l2.5-2.5M15.5 8.5L18 6") }

    private fun line(path: () -> Path): HolidayIconArt =
        { color, _ ->
            val k = size.minDimension / 24f
            withTransform({ scale(k, k, Offset.Zero) }) {
                drawPath(path(), lerp(color, ROSE, .65f), style = Stroke(1.8f, cap = StrokeCap.Round))
            }
        }

    private val more: HolidayIconArt = { color, _ ->
        val k = size.minDimension / 24f
        for (x in listOf(5f, 12f, 19f)) drawCircle(lerp(color, ROSE, .65f), 1.4f * k, Offset(x * k, 12f * k))
    }

    val icons: Map<String, HolidayIconArt> by lazy {
        mapOf(
            "movies" to movies,
            "shows" to shows,
            "live" to live,
            "games" to games,
            "music" to music,
            "books" to books,
            HolidayIconKey.HOME to line { HOME },
            HolidayIconKey.SEARCH to line { SEARCH },
            HolidayIconKey.REQUESTS to line { REQ },
            HolidayIconKey.MORE to more,
        )
    }

    // ---------- Home's trims ----------

    /** Paper hearts on twine across the top of Home's picture, swaying a little */
    val topTrim: HolidayArt = { t ->
        val d = density
        val span = 220f * d
        val sag = 18f * d
        val top = 3f * d
        val hs = 24f * d
        val twine = Color(0xFFC9A47A)
        val spans = (size.width / span).toInt() + 1
        val path = Path().apply { moveTo(0f, top) }
        for (i in 0 until spans) path.quadraticTo(i * span + span / 2, top + sag * 2, (i + 1) * span, top)
        drawPath(path, twine, style = Stroke(1.6f * d))
        val colors = listOf(ROSE, PALE, BLUSH, DEEP)
        var k = 0
        for (i in 0 until spans) {
            for (j in 1..5) {
                val f = j / 6f
                val x = i * span + span * f
                val y = top + 2 * (1 - f) * f * sag * 2
                val c = colors[k % 4]
                val tilt = ((k * 37) % 15 - 7) + sin(t * 1.3f + k * .8f) * 4f
                k++
                if (x > size.width + hs) continue
                rotate(tilt, Offset(x, y)) {
                    drawLine(twine, Offset(x, y), Offset(x, y + 4f * d), 1.2f * d)
                    heart(x - hs / 2, y + 2f * d, hs, solid(c), Color.Black.copy(alpha = .18f), .8f * d)
                }
            }
        }
    }

    private val LOOP_L by lazy { p("M0 0c-30-26-62-18-58 4s34 18 58-4z") }
    private val LOOP_R by lazy { p("M0 0c30-26 62-18 58 4s-34 18-58-4z") }
    private val TAIL_L by lazy { p("M-4 4l-22 30 10-2 4 8 16-30z") }
    private val TAIL_R by lazy { p("M4 4l20 30-10-2-4 8-14-30z") }

    /** A red satin ribbon along the bottom of Home, tied in a bow near the right */
    val bottomTrim: HolidayArt = { _ ->
        val d = density
        val bandH = 8f * d
        val cy = size.height * .4f
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFFFF7A90), ROSE, Color(0xFF9E1838)), cy - bandH / 2, cy + bandH / 2),
            Offset(0f, cy - bandH / 2),
            Size(size.width, bandH),
        )
        drawLine(Color(0xFFFFB0BD).copy(alpha = .6f), Offset(0f, cy - bandH / 4), Offset(size.width, cy - bandH / 4), 1f * d)
        val k = .42f * d
        val satin = Brush.verticalGradient(listOf(Color(0xFFFF7A90), ROSE, Color(0xFF9E1838)), -26f, 18f)
        withTransform({
            translate(size.width - 80f * d, cy)
            scale(k, k, Offset.Zero)
        }) {
            drawPath(TAIL_L, DEEP)
            drawPath(TAIL_R, DEEP)
            for (loop in listOf(LOOP_L, LOOP_R)) {
                drawPath(loop, satin)
                drawPath(loop, Color(0xFF8E0F2E), style = Stroke(1f / k * d))
            }
            drawRoundRect(ROSE, Offset(-9f, -9f), Size(18f, 18f), CornerRadius(5f))
            drawRoundRect(Color(0xFF8E0F2E), Offset(-9f, -9f), Size(18f, 18f), CornerRadius(5f), style = Stroke(1f / k * d))
        }
    }

    /** The first Today card: the countdown, then "Happy Valentine's Day" on the 14th */
    fun countdown(date: LocalDate = LocalDate.now()): String? =
        if (ValentineSeason.isDay(date)) ValentineSeason.GREETING else ValentineSeason.countdown(date)

    /** Hand-picked romances for the Valentine's pick, added to the Date night list */
    val pickTitles =
        listOf(
            "The Princess Bride",
            "When Harry Met Sally...",
            "Notting Hill",
            "Pride & Prejudice",
            "La La Land",
            "Crazy, Stupid, Love.",
            "10 Things I Hate About You",
            "About Time",
        )
}
