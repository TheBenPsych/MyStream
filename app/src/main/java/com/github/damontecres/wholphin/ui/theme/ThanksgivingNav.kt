package com.github.damontecres.wholphin.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Thanksgiving on the new navigation (approved 2026-11-17, thread "Fit Thanksgiving theme to new navigation",
 * sheet nav-13b, pick g2-swag): a harvest-brown rail whose icons are drawn in gold, pumpkin orange and cranberry
 * (Ben: only the rail icons, coloured, nothing beside the names), a short leaf garland swagging across the top of
 * Home with an acorn at each low point and now and then a leaf drifting down, and only two pumpkins, an acorn and a
 * few leaves in Home's bottom-right corner. Countdown card first; "Happy Thanksgiving" on the day. Not on Kids.
 * The web draws the same shapes (js/holidaynav.js).
 */
object ThanksgivingNav {
    private val Gold = Color(0xFFE8B04A)
    private val Orange = Color(0xFFF07E36)
    private val Cranberry = Color(0xFFD9473A)

    /** Thanksgiving: the fourth Thursday of November */
    fun day(year: Int): LocalDate = LocalDate.of(year, 11, 1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY))

    /** The first Today card: "9 days to Thanksgiving", "Happy Thanksgiving" on the day, nothing after it */
    fun countdown(today: LocalDate = LocalDate.now()): String? {
        val left = ChronoUnit.DAYS.between(today, day(today.year))
        return when {
            left > 1 -> "$left days to Thanksgiving"
            left == 1L -> "Thanksgiving is tomorrow"
            left == 0L -> "Happy Thanksgiving"
            else -> null
        }
    }

    val entry: HolidayNav by lazy {
        HolidayNav(
            key = "thanksgiving",
            isOn = { TgLook.on },
            railTop = Color(0xFF2E1E14),
            railBottom = Color(0xFF1C120B),
            lights = listOf(Color(0xFFE2702A), Gold, Color(0xFFB83A1E)),
            icons =
                mapOf(
                    HolidayIconKey.HOME to icon(BenflixIcon.HOME, Gold),
                    "movies" to icon(BenflixIcon.FILM, Orange),
                    "shows" to icon(BenflixIcon.TV, Cranberry),
                    "live" to icon(BenflixIcon.LIVE, Cranberry),
                    "games" to icon(BenflixIcon.PAD, Gold),
                    "music" to icon(BenflixIcon.NOTE, Orange),
                    "books" to icon(BenflixIcon.BOOK, Gold),
                ),
            topTrim = { t -> garland(t) },
            bottomTrim = { corner() },
            moving = true,
            countdown = { runCatching { countdown() }.getOrNull() },
            countdownKind = "Thanksgiving",
            pickWhy = "Thanksgiving pick",
            pickTitles =
                listOf(
                    "Planes, Trains and Automobiles",
                    "Home for the Holidays",
                    "Pieces of April",
                    "Free Birds",
                    "A Charlie Brown Thanksgiving",
                    "Addams Family Values",
                    "The Ice Storm",
                    "Hannah and Her Sisters",
                    "Dutch",
                    "Krisha",
                ),
            accent = Color(0xFFFFB067),
        )
    }

    // ---------- icons: the usual line icons, in harvest colours ----------

    private val iconPaths = HashMap<BenflixIcon, List<Path>>()

    private fun paths(icon: BenflixIcon): List<Path> =
        iconPaths.getOrPut(icon) { icon.paths.map { PathParser().parsePathString(it).toPath() } }

    /**
     * The usual icon drawn in [harvest]. It follows the colour it's given: still grey while the start-up light-up
     * waits, the harvest colour normally, and with a warm candle glow when it's lit (the door you're in, start-up).
     */
    private fun icon(
        icon: BenflixIcon,
        harvest: Color,
    ): HolidayIconArt =
        { given, _ ->
            val hi = max(given.red, max(given.green, given.blue))
            val lo = min(given.red, min(given.green, given.blue))
            val lit = hi - lo > .25f
            val white = lo > .7f
            val ink = if (lit || white) harvest else given
            val k = size.minDimension / 24f
            if (lit) {
                drawCircle(
                    Brush.radialGradient(listOf(Color(0x66FFA850), Color.Transparent), center = center, radius = size.minDimension * .75f),
                    radius = size.minDimension * .75f,
                )
            }
            scale(k, k, pivot = Offset.Zero) {
                paths(icon).forEach {
                    drawPath(it, ink, style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }

    // ---------- art ----------

    private val maple by lazy {
        PathParser()
            .parsePathString(
                "M50,0 L57,14 L66,9 L63,30 L80,17 L77,26 L95,24 L88,38 L97,44 L72,62 L76,73 L54,69 L54,96 L46,96 L46,69 " +
                    "L24,73 L28,62 L3,44 L12,38 L5,24 L23,26 L20,17 L37,30 L34,9 L43,14 Z",
            ).toPath()
    }
    private val oak by lazy {
        PathParser()
            .parsePathString(
                "M50,2 C60,2 62,12 58,16 C68,12 74,20 66,28 C78,26 82,38 70,42 C82,44 82,58 68,58 C76,64 72,74 60,70 " +
                    "L54,80 L54,98 L46,98 L46,80 L40,70 C28,74 24,64 32,58 C18,58 18,44 30,42 C18,38 22,26 34,28 " +
                    "C26,20 32,12 42,16 C38,12 40,2 50,2 Z",
            ).toPath()
    }
    private val mapleVeins by lazy {
        PathParser().parsePathString("M50,92 L50,8 M50,66 L84,30 M50,66 L16,30 M50,70 L78,58 M50,70 L22,58").toPath()
    }
    private val oakVeins by lazy {
        PathParser().parsePathString("M50,96 L50,8 M50,40 L68,30 M50,40 L32,30 M50,58 L70,50 M50,58 L30,50").toPath()
    }
    private val nut by lazy { PathParser().parsePathString("M-10,6 C-10,20 -4,28 0,31 C4,28 10,20 10,6 Z").toPath() }
    private val cup by lazy { PathParser().parsePathString("M-12.5,7 C-12.5,-4 12.5,-4 12.5,7 C8,9.5 -8,9.5 -12.5,7 Z").toPath() }

    /** Red, orange, gold, cranberry and rust leaves (light edge, dark edge) */
    private val leafColors =
        listOf(
            Color(0xFFF0603A) to Color(0xFF9C2414),
            Color(0xFFFFA040) to Color(0xFFC2561F),
            Color(0xFFFFD76A) to Color(0xFFC88A26),
            Color(0xFFC8384E) to Color(0xFF6E1626),
            Color(0xFFD8742E) to Color(0xFF7A3A14),
        )

    /** A leaf whose stem sits at [at], pointing [deg] degrees from straight up, [px] pixels long */
    private fun DrawScope.leaf(
        at: Offset,
        deg: Float,
        px: Float,
        colorIndex: Int,
        isOak: Boolean,
    ) {
        val (light, dark) = leafColors[colorIndex.mod(leafColors.size)]
        val brush = Brush.linearGradient(listOf(light, dark), start = Offset.Zero, end = Offset(100f, 100f))
        val k = px / 100f
        translate(at.x, at.y) {
            rotate(deg, pivot = Offset.Zero) {
                scale(k, k, pivot = Offset.Zero) {
                    translate(-50f, -98f) {
                        val shape = if (isOak) oak else maple
                        drawPath(shape, brush)
                        drawPath(shape, brush, style = Stroke(width = 5f, join = StrokeJoin.Round))
                        drawPath(
                            if (isOak) oakVeins else mapleVeins,
                            Color(0x73FFECBE),
                            style = Stroke(width = 2.6f, cap = StrokeCap.Round),
                        )
                    }
                }
            }
        }
    }

    /** An acorn hanging from [at], [s] = pixels per design unit (it's about 30 units tall) */
    private fun DrawScope.acorn(
        at: Offset,
        s: Float,
        deg: Float = 0f,
    ) {
        translate(at.x, at.y) {
            rotate(deg, pivot = Offset.Zero) {
                scale(s, s, pivot = Offset.Zero) {
                    drawLine(Color(0xFF4A2D16), Offset(0f, -4f), Offset(1f, -11f), strokeWidth = 2.6f, cap = StrokeCap.Round)
                    drawPath(
                        nut,
                        Brush.horizontalGradient(
                            listOf(Color(0xFFE0A35C), Color(0xFFB06D32), Color(0xFF6E4020)),
                            startX = -10f,
                            endX = 10f,
                        ),
                    )
                    drawOval(Color(0x73FFEBC8), topLeft = Offset(-6.4f, 7f), size = Size(4.8f, 12f))
                    drawPath(cup, Brush.verticalGradient(listOf(Color(0xFF8A5A32), Color(0xFF4A2D16)), startY = -4f, endY = 9f))
                }
            }
        }
    }

    private fun DrawScope.tack(
        at: Offset,
        r: Float,
    ) {
        drawCircle(Color(0x59000000), r, at + Offset(0f, r * .2f))
        drawCircle(
            Brush.radialGradient(
                0f to Color(0xFFFFF0B0),
                .5f to Color(0xFFD9A940),
                1f to Color(0xFF7A5418),
                center = at - Offset(r * .3f, r * .3f),
                radius = r * 1.3f,
            ),
            r,
            at,
        )
    }

    private fun DrawScope.twine(
        path: Path,
        w: Float,
    ) {
        drawPath(path, Color(0xFF7A5A32), style = Stroke(width = w * 1.5f, cap = StrokeCap.Round))
        drawPath(path, Color(0xFFD2AD72), style = Stroke(width = w, cap = StrokeCap.Round))
        drawPath(
            path,
            Color(0xFF8A6A3E),
            style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * .9f, w * 1.3f))),
        )
    }

    private fun DrawScope.pumpkin(
        cx: Float,
        base: Float,
        w: Float,
        h: Float,
        white: Boolean,
    ) {
        val fill =
            if (white) {
                listOf(Color(0xFFFBF4E4), Color(0xFFE3D6BC), Color(0xFFB5A586))
            } else {
                listOf(Color(0xFFFFA24A), Color(0xFFE2702A), Color(0xFFA5441A))
            }
        drawOval(Color(0x66000000), topLeft = Offset(cx - w * .52f, base - h * .1f), size = Size(w * 1.04f, h * .2f))
        listOf(-.36f to .62f, .36f to .62f, -.18f to .82f, .18f to .82f, 0f to 1f).forEach { (o, k) ->
            val rx = w * .3f * k
            val tl = Offset(cx + o * w - rx, base - h)
            val brush = Brush.linearGradient(fill, start = Offset(cx - w / 2, base - h), end = Offset(cx + w / 2, base))
            drawOval(brush, topLeft = tl, size = Size(rx * 2, h))
            drawOval(Color(0x59501E05), topLeft = tl, size = Size(rx * 2, h), style = Stroke(width = max(1f, w * .015f)))
        }
        drawOval(Color(0x59FFF0D2), topLeft = Offset(cx - w * .19f, base - h * .86f), size = Size(w * .14f, h * .36f))
        val stem =
            Path().apply {
                moveTo(cx - w * .02f, base - h + 2f)
                cubicTo(cx - w * .04f, base - h * 1.28f, cx + w * .05f, base - h * 1.36f, cx + w * .16f, base - h * 1.3f)
            }
        drawPath(stem, Color(0xFF5A4A22), style = Stroke(width = max(3f, w * .08f), cap = StrokeCap.Round))
    }

    /** A point on a swag from (x0, y) to (x1, y) that sags [dip] in the middle */
    private fun swag(
        x0: Float,
        x1: Float,
        y: Float,
        dip: Float,
        t: Float,
    ): Offset {
        val mx = (x0 + x1) / 2
        return Offset((1 - t) * (1 - t) * x0 + 2 * (1 - t) * t * mx + t * t * x1, y + 2 * (1 - t) * t * dip * 2)
    }

    /** A fixed "random" number from 0 to 1 for leaf [i], so the garland looks the same every time */
    private fun r(i: Int): Float = ((sin(i * 12.9898f + 78.233f) * 43758.547f).let { it - kotlin.math.floor(it) })

    /** Top of Home: the short leaf garland (sheet: dip 24 of 170, leaves 54, 9 per swag on TV; 6 on phones) */
    private fun DrawScope.garland(seconds: Float) {
        val dp = density
        val phone = size.width < 600f * dp
        val swags = max(2, (size.width / ((if (phone) 205f else 260f) * dp)).toInt())
        val seg = size.width / swags
        val dip = (if (phone) 16f else 15f) * dp
        val leafPx = (if (phone) 32f else 34f) * dp
        val per = if (phone) 6 else 9
        val y = 6f * dp
        for (i in 0 until swags) {
            val x0 = i * seg + 4f * dp
            val x1 = (i + 1) * seg - 4f * dp
            val mx = (x0 + x1) / 2
            twine(
                Path().apply {
                    moveTo(x0, y)
                    quadraticTo(mx, y + dip * 2, x1, y)
                },
                2.6f * dp,
            )
            for (j in 1 until per) {
                val n = i * 31 + j
                val at = swag(x0, x1, y, dip, j / per.toFloat())
                val sway = 5f * sin((seconds / 4.5f + r(n + 7) * 5f) * PI.toFloat())
                leaf(at, 180f + (r(n) * 44f - 22f) + sway, leafPx * (.88f + r(n + 3) * .24f), (r(n + 5) * 5).toInt(), j % 3 == 1)
            }
            acorn(swag(x0, x1, y, dip, .5f) + Offset(0f, 3f * dp), .75f * dp, 5f * sin((seconds / 4.5f + i) * PI.toFloat()))
            tack(Offset(x0, y), 3.6f * dp)
            if (i == swags - 1) tack(Offset(x1, y), 3.6f * dp)
        }
        // Now and then one leaf drifts down from the garland (every 13 seconds, over the picture)
        val p = (seconds % 13f) / 13f
        val fall = (if (phone) 180f else 240f) * dp
        val alpha =
            when {
                p < .05f -> p / .05f
                p > .9f -> (1f - p) / .1f
                else -> 1f
            }
        val x = size.width * .36f + 30f * dp * sin(p * 3f * PI.toFloat())
        val leafY = y + dip + 20f * dp + fall * p
        if (alpha > 0f) {
            drawContext.canvas.saveLayer(
                androidx.compose.ui.geometry
                    .Rect(Offset.Zero, Size(size.width, leafY + leafPx * 2)),
                androidx.compose.ui.graphics
                    .Paint()
                    .apply { this.alpha = alpha.coerceIn(0f, 1f) },
            )
            leaf(Offset(x, leafY), 180f + 330f * p, leafPx, 1, false)
            drawContext.canvas.restore()
        }
    }

    /** Bottom of Home: only the bottom-right corner (two pumpkins, an acorn and a few leaves), resting on the edge */
    private fun DrawScope.corner() {
        val dp = density
        val phone = size.width < 600f * dp
        val s = if (phone) .85f else 1f
        val base = size.height - 2f * dp
        val right = size.width - (if (phone) 12f else 24f) * dp
        listOf(Triple(-96f, -55f, 0), Triple(-70f, 30f, 2), Triple(-4f, 70f, 1), Triple(-112f, -20f, 3)).forEachIndexed { i, (dx, deg, c) ->
            leaf(Offset(right + dx * s * dp, base), deg, 24f * s * dp, c, i % 2 == 1)
        }
        pumpkin(right - 36f * s * dp, base, 54f * s * dp, 37f * s * dp, white = false)
        pumpkin(right - 78f * s * dp, base, 32f * s * dp, 23f * s * dp, white = true)
        acorn(Offset(right - 100f * s * dp, base - 12f * s * dp), .62f * s * dp, 70f)
    }
}
