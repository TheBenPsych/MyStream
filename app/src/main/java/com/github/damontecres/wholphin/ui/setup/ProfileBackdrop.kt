package com.github.damontecres.wholphin.ui.setup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.LiteMode
import com.github.damontecres.wholphin.services.LogoEffect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The moving backgrounds behind "Who's watching?": a slow wall of the server's posters on
 * ordinary days, and a drawn holiday scene on holidays. Scenes are drawn on a 1600 x 900
 * canvas that is scaled to fill the screen.
 */

private const val VW = 1600f
private const val VH = 900f
private const val TAU = (2 * PI).toFloat()

/**
 * Seconds since the screen opened. Ticks about 30 times a second (plenty for these slow scenes and
 * half the work of every frame on older TVs); stands still in Lite mode.
 */
@Composable
private fun rememberSeconds(): State<Float> {
    val seconds = remember { mutableFloatStateOf(STILL_SECONDS) }
    val lite = LiteMode.isOn(LocalContext.current)
    if (!lite) {
        LaunchedEffect(Unit) {
            val start = withFrameMillis { it }
            var last = 0L
            while (true) {
                withFrameMillis {
                    if (it - last >= FRAME_MS) {
                        last = it
                        seconds.floatValue = STILL_SECONDS + (it - start) / 1000f
                    }
                }
            }
        }
    }
    return seconds
}

private const val FRAME_MS = 33L

/** Where a still (Lite mode) scene stops, so it isn't empty */
private const val STILL_SECONDS = 4f

private fun frac(x: Float): Float = x - floor(x)

/** A repeatable random number from 0 to 1 for a pair of numbers */
private fun h(
    a: Int,
    b: Int,
): Float {
    var x = a * 73856093 xor b * 19349663
    x = x xor (x ushr 13)
    x *= 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16777216f
}

private class Rng(
    seed: Int,
) {
    private var s = seed.toLong() and 0xffffffffL

    fun next(): Float {
        s = (s * 1664525 + 1013904223) and 0xffffffffL
        return s / 4294967296f
    }
}

private fun hex(v: Long) = Color(v)

// ---------------------------------------------------------------- poster wall

/**
 * Rows of posters sliding slowly in opposite directions, tilted and dimmed so the profiles stand out.
 *
 * Each row is drawn once and then only slid along by the graphics chip (no redrawing of the posters
 * every frame), which keeps this screen light on older TVs.
 */
@Composable
fun PosterWall(
    posters: List<String>,
    modifier: Modifier = Modifier,
) {
    if (posters.isEmpty()) return
    val time = rememberSeconds()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val posterH = maxHeight / 3.6f
        val posterW = posterH * 2f / 3f
        val gap = posterH * .06f
        val step = posterW + gap
        val perRow = max(1, posters.size / 2)
        val rowWidthPx = with(density) { (step * perRow).toPx() }
        val stepPx = with(density) { step.toPx() }
        // enough posters to cover the (tilted, enlarged) screen plus one full loop
        val count = ceil((with(density) { (maxWidth * 1.5f).toPx() } + rowWidthPx) / stepPx).toInt() + 1
        val shape = RoundedCornerShape(posterW * .06f)
        Column(
            verticalArrangement = Arrangement.spacedBy(gap),
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = -8f
                        scaleX = 1.15f
                        scaleY = 1.15f
                    }.offset(x = -posterW, y = -posterH * .9f),
        ) {
            repeat(5) { r ->
                val dir = if (r % 2 == 0) -1f else 1f
                val speedPx = stepPx / (if (r % 2 == 0) 4.4f else 5.6f)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    modifier =
                        Modifier
                            .wrapContentWidth(align = Alignment.Start, unbounded = true)
                            .graphicsLayer {
                                // Read the clock here, so only the slide changes each frame
                                val shift = frac(time.value * speedPx / rowWidthPx) * rowWidthPx
                                translationX = if (dir < 0) -shift else shift - rowWidthPx
                            },
                ) {
                    repeat(count) { k ->
                        AsyncImage(
                            model = posters[(r * 7 + k % perRow) % posters.size],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .size(posterW, posterH)
                                    .clip(shape)
                                    .background(Color(0xFF1C1C22)),
                        )
                    }
                }
            }
        }
        // Dim everything, darker toward the edges, so the profiles pop
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val brush =
                        Brush.radialGradient(
                            0f to Color.Black.copy(alpha = .5f),
                            1f to Color.Black.copy(alpha = .92f),
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.maxDimension * .62f,
                        )
                    onDrawBehind { drawRect(brush) }
                },
        )
    }
}

// ---------------------------------------------------------------- holiday scenes

/** The font for a holiday's greeting on the profile screen */
fun holidayFont(effect: LogoEffect): FontFamily? =
    when (effect) {
        LogoEffect.GHOST_PEEK -> FontFamily(Font(R.font.holiday_creepster))
        LogoEffect.TWINKLE_LIGHTS -> FontFamily(Font(R.font.holiday_mountains))
        LogoEffect.FIREWORKS_GOLD -> FontFamily(Font(R.font.holiday_monoton))
        LogoEffect.HEART_FLOAT -> FontFamily(Font(R.font.holiday_greatvibes))
        LogoEffect.GREEN_SHIMMER -> FontFamily(Font(R.font.holiday_lobster))
        LogoEffect.FIREWORKS_USA -> FontFamily(Font(R.font.holiday_bungee))
        LogoEffect.FALLING_LEAVES -> FontFamily(Font(R.font.holiday_playfair_italic, style = FontStyle.Italic))
        LogoEffect.CONFETTI -> FontFamily(Font(R.font.holiday_pacifico))
        LogoEffect.MARIGOLDS -> FontFamily(Font(R.font.holiday_sancreek))
        LogoEffect.LANTERNS -> FontFamily(Font(R.font.holiday_kaushan))
        LogoEffect.DIYAS -> FontFamily(Font(R.font.holiday_yatra))
        LogoEffect.EMBERS -> FontFamily(Font(R.font.holiday_rye))
        LogoEffect.STAR_PAIR -> FontFamily(Font(R.font.holiday_caveatbrush))
        LogoEffect.NONE -> null
    }

/**
 * A drawn, moving scene for the holiday: haunted house and bats, snowy cabin, fireworks over the city, and so on
 */
@Composable
fun HolidayScene(
    effect: LogoEffect,
    modifier: Modifier = Modifier,
) {
    val statics = remember(effect) { SceneStatics() }
    val time = rememberSeconds()
    Canvas(modifier = modifier.clipToBounds()) {
        val t = time.value
        val sc = max(size.width / VW, size.height / VH)
        val dx = (size.width - VW * sc) / 2
        val dy = (size.height - VH * sc) / 2
        withTransform({
            translate(dx, dy)
            scale(sc, sc, pivot = Offset.Zero)
        }) {
            when (effect) {
                LogoEffect.GHOST_PEEK -> {
                    halloween(t, statics)
                }

                LogoEffect.TWINKLE_LIGHTS -> {
                    christmas(t, statics)
                }

                LogoEffect.FIREWORKS_GOLD -> {
                    fireworksCity(
                        t,
                        statics,
                        listOf(hex(0xFFFFD54F), Color.White, hex(0xFFFF80AB), hex(0xFFB388FF), hex(0xFFFFE9A8)),
                        listOf(hex(0xFF030312), hex(0xFF140C2E), hex(0xFF2C1640)),
                        42,
                    )
                }

                LogoEffect.FIREWORKS_USA -> {
                    fireworksCity(
                        t,
                        statics,
                        listOf(hex(0xFFFF4D4D), Color.White, hex(0xFF4DA3FF), hex(0xFFFF8080), hex(0xFFBFE0FF)),
                        listOf(hex(0xFF02040F), hex(0xFF0B1736), hex(0xFF1C2A55)),
                        77,
                    )
                }

                LogoEffect.HEART_FLOAT -> {
                    valentine(t, statics)
                }

                LogoEffect.GREEN_SHIMMER -> {
                    stPatricks(t, statics)
                }

                LogoEffect.FALLING_LEAVES -> {
                    thanksgiving(t, statics)
                }

                LogoEffect.CONFETTI -> {
                    birthday(t)
                }

                LogoEffect.MARIGOLDS -> {
                    dayOfTheDead(t, statics)
                }

                LogoEffect.LANTERNS -> {
                    lunarNewYear(t, statics)
                }

                LogoEffect.DIYAS -> {
                    diwali(t)
                }

                LogoEffect.EMBERS -> {
                    bonfireNight(t, statics)
                }

                LogoEffect.STAR_PAIR -> {
                    tanabata(t, statics)
                }

                LogoEffect.NONE -> {}
            }
        }
        // Soft vignette so the profiles and text stay easy to read
        drawRect(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = .35f),
                .3f to Color.Transparent,
                .6f to Color.Transparent,
                1f to Color.Black.copy(alpha = .55f),
            ),
        )
        drawRect(
            Brush.radialGradient(
                .35f to Color.Transparent,
                1f to Color.Black.copy(alpha = .5f),
                center = center,
                radius = size.maxDimension * .7f,
            ),
        )
    }
}

/** Shapes that never change, built once per screen */
private class SceneStatics {
    private val hills = HashMap<String, Path>()

    fun hill(
        y: Float,
        amp: Float,
        seed: Int,
    ): Path =
        hills.getOrPut("$y-$amp-$seed") {
            val r = Rng(seed)
            Path().apply {
                moveTo(0f, y)
                var x = 0f
                while (x <= VW) {
                    quadraticTo(x + 100, y - amp * (r.next() * 1.4f - .2f), x + 200, y + (r.next() - .5f) * amp * .5f)
                    x += 200
                }
                lineTo(VW, VH)
                lineTo(0f, VH)
                close()
            }
        }

    private val skylines = HashMap<Int, Skyline>()

    fun skyline(
        seed: Int,
        base: Float,
    ): Skyline = skylines.getOrPut(seed) { Skyline(seed, base) }
}

private class Skyline(
    seed: Int,
    val base: Float,
) {
    val buildings = ArrayList<Rect>()
    val windows = ArrayList<Pair<Rect, Float>>()

    init {
        val r = Rng(seed)
        var x = -20f
        while (x < VW + 20) {
            val w = 50 + r.next() * 90
            val bh = 70 + r.next() * 200
            buildings += Rect(x, base - bh, x + w, VH)
            if (r.next() < .3f) buildings += Rect(x + w / 2 - 3, base - bh - 40, x + w / 2 + 3, base - bh)
            var wy = base - bh + 16
            while (wy < base - 10) {
                var wx = x + 10
                while (wx < x + w - 12) {
                    if (r.next() < .28f) windows += Rect(wx, wy, wx + 8, wy + 11) to (.35f + r.next() * .6f)
                    wx += 18
                }
                wy += 22
            }
            x += w + 4
        }
    }
}

private fun DrawScope.sky(vararg stops: Pair<Float, Color>) =
    drawRect(Brush.verticalGradient(*stops, startY = 0f, endY = VH), size = Size(VW, VH))

private fun DrawScope.stars(
    n: Int,
    seed: Int,
    maxY: Float,
    t: Float,
) {
    for (i in 0 until n) {
        val tw = (sin(t * TAU / (2 + h(seed, i * 4) * 3) + h(seed, i * 4 + 1) * TAU) + 1) / 2
        drawCircle(
            Color.White,
            radius = .8f + h(seed, i * 4 + 2) * 1.8f,
            center = Offset(h(seed, i * 4 + 3) * VW, h(seed + 1, i) * maxY),
            alpha = .25f + tw * .75f,
        )
    }
}

private fun DrawScope.glow(
    center: Offset,
    radius: Float,
    color: Color,
    alpha: Float,
) = drawCircle(
    Brush.radialGradient(0f to color.copy(alpha = alpha), 1f to color.copy(alpha = 0f), center = center, radius = radius),
    radius = radius,
    center = center,
)

private fun poly(vararg pts: Float): Path =
    Path().apply {
        moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) {
            lineTo(pts[i], pts[i + 1])
            i += 2
        }
        close()
    }

private fun DrawScope.oval(
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float,
    color: Color,
    alpha: Float = 1f,
) = drawOval(color, topLeft = Offset(cx - rx, cy - ry), size = Size(rx * 2, ry * 2), alpha = alpha)

private fun DrawScope.box(
    x: Float,
    y: Float,
    w: Float,
    hgt: Float,
    color: Color,
    alpha: Float = 1f,
) = drawRect(color, topLeft = Offset(x, y), size = Size(w, hgt), alpha = alpha)

// ---- Halloween: moonlit haunted house, glowing pumpkins, bats and rolling fog

private val batWing =
    Path().apply {
        moveTo(0f, 0f)
        quadraticTo(-14f, -16f, -40f, -10f)
        quadraticTo(-30f, -2f, -34f, 10f)
        quadraticTo(-22f, 2f, -14f, 10f)
        quadraticTo(-8f, 2f, 0f, 6f)
        quadraticTo(8f, 2f, 14f, 10f)
        quadraticTo(22f, 2f, 34f, 10f)
        quadraticTo(30f, -2f, 40f, -10f)
        quadraticTo(14f, -16f, 0f, 0f)
        close()
    }

private fun flicker(
    t: Float,
    seed: Float,
) = .72f + .28f * abs(sin(t * 7.3f + seed) * sin(t * 3.1f + seed * 2))

private fun DrawScope.pumpkin(
    x: Float,
    y: Float,
    s: Float,
    t: Float,
) = withTransform({
    translate(x, y)
    scale(s, s, pivot = Offset.Zero)
}) {
    box(-4f, -46f, 8f, 14f, hex(0xFF3D5A1E))
    oval(-22f, 0f, 26f, 34f, hex(0xFFC2560F))
    oval(22f, 0f, 26f, 34f, hex(0xFFC2560F))
    oval(0f, 0f, 28f, 36f, hex(0xFFE06A14))
    val a = flicker(t, s * 10)
    val face = hex(0xFFFFD36B)
    drawPath(poly(-22f, -10f, -10f, -10f, -16f, -22f), face, alpha = a)
    drawPath(poly(10f, -10f, 22f, -10f, 16f, -22f), face, alpha = a)
    drawPath(
        Path().apply {
            moveTo(-26f, 6f)
            quadraticTo(0f, 34f, 26f, 6f)
            lineTo(18f, 12f)
            lineTo(10f, 6f)
            lineTo(2f, 14f)
            lineTo(-6f, 6f)
            lineTo(-14f, 14f)
            close()
        },
        face,
        alpha = a,
    )
    glow(Offset(0f, 0f), 70f, hex(0xFFFF9A3C), .25f * a)
}

private fun DrawScope.halloween(
    t: Float,
    s: SceneStatics,
) {
    sky(0f to hex(0xFF0D0618), .6f to hex(0xFF2D0F3A), 1f to hex(0xFF6A2340))
    stars(60, 3, 380f, t)
    glow(Offset(1180f, 230f), 300f, hex(0xFFFFD98A), .55f)
    drawCircle(hex(0xFFFFF4D2), 118f, Offset(1180f, 230f))
    drawCircle(hex(0xFFEADCB0), 20f, Offset(1140f, 200f), alpha = .6f)
    drawCircle(hex(0xFFEADCB0), 14f, Offset(1215f, 265f), alpha = .5f)
    drawPath(s.hill(640f, 120f, 7), hex(0xFF2A1238))
    drawPath(s.hill(700f, 90f, 11), hex(0xFF170A22))
    // Haunted house with flickering windows
    val dark = hex(0xFF0C0612)
    box(1010f, 520f, 230f, 200f, dark)
    drawPath(poly(990f, 525f, 1125f, 420f, 1260f, 525f), dark)
    box(1180f, 430f, 60f, 120f, dark)
    drawPath(poly(1170f, 435f, 1210f, 340f, 1250f, 435f), dark)
    box(1040f, 470f, 14f, 60f, dark)
    val win = hex(0xFFFFB347)
    box(1040f, 560f, 30f, 38f, win, flicker(t, 1f))
    box(1110f, 560f, 30f, 38f, win, .6f * flicker(t, 2f))
    box(1196f, 460f, 22f, 30f, win, flicker(t, 3f))
    box(1180f, 620f, 30f, 40f, win, .85f * flicker(t, 4f))
    // Bare crooked tree
    val tree =
        Path().apply {
            moveTo(330f, 760f)
            quadraticTo(320f, 600f, 300f, 520f)
            moveTo(305f, 560f)
            quadraticTo(250f, 520f, 220f, 470f)
            moveTo(300f, 600f)
            quadraticTo(370f, 560f, 400f, 500f)
            moveTo(312f, 640f)
            quadraticTo(260f, 630f, 230f, 600f)
            moveTo(385f, 525f)
            quadraticTo(420f, 520f, 440f, 490f)
        }
    drawPath(tree, dark, style = Stroke(16f, cap = StrokeCap.Round))
    drawPath(s.hill(790f, 60f, 19), hex(0xFF07030B))
    pumpkin(200f, 800f, 1.1f, t)
    pumpkin(330f, 830f, .8f, t)
    pumpkin(1400f, 810f, 1f, t)
    pumpkin(1510f, 845f, .7f, t)
    // Fog drifting low over the hills
    for (i in 0 until 9) {
        val v = (.006f + h(9, i) * .012f) * (if (h(10, i) < .5f) 1 else -1)
        val x = (frac(h(11, i) + v * t) * 1.8f - .4f) * VW
        val y = (.6f + h(12, i) * .35f) * VH
        val rx = (.35f + h(13, i) * .35f) * VW
        val ry = (.08f + h(14, i) * .08f) * VH
        withTransform({ scale(1f, ry / rx, pivot = Offset(x, y)) }) {
            glow(Offset(x, y), rx, hex(0xFFC8B4EB), .12f + h(15, i) * .13f)
        }
    }
    // Bats flapping across the sky
    listOf(
        floatArrayOf(180f, 14f, 0f, 1f),
        floatArrayOf(260f, 18f, -6f, .7f),
        floatArrayOf(140f, 22f, -12f, .55f),
        floatArrayOf(330f, 16f, -3f, .8f),
        floatArrayOf(220f, 20f, -15f, .6f),
    ).forEach { (y, dur, delay, sz) ->
        val p = frac((t - delay) / dur)
        val bx = -200f + p * 2000f
        val by = y + 30f * sin((t - delay) * TAU / (dur / 2))
        val flap = .2f + .8f * cos(t * TAU / .32f)
        withTransform({
            translate(bx, by)
            scale(sz, sz, pivot = Offset.Zero)
        }) {
            scale(1f, flap, pivot = Offset.Zero) { drawPath(batWing, hex(0xFF0A0410)) }
            oval(0f, 2f, 6f, 9f, hex(0xFF0A0410))
        }
    }
}

// ---- Christmas: snowy hills, pine trees, a warm cabin, twinkling lights and falling snow

private fun DrawScope.pine(
    x: Float,
    b: Float,
    ht: Float,
    color: Color,
) {
    val w = ht * .42f
    box(x - ht * .03f, b - ht * .12f, ht * .06f, ht * .12f, hex(0xFF2A1A10))
    for (k in 0 until 3) {
        val top = b - ht + k * ht * .28f
        val bot = top + ht * .45f
        val ww = w * (.55f + k * .25f)
        drawPath(poly(x, top, x - ww, bot, x + ww, bot), color)
        drawPath(
            poly(
                x,
                top,
                x - ww * .35f,
                top + ht * .16f,
                x - ww * .12f,
                top + ht * .13f,
                x,
                top + ht * .17f,
                x + ww * .14f,
                top + ht * .13f,
                x + ww * .35f,
                top + ht * .16f,
            ),
            hex(0xFFF2F7FF),
        )
    }
}

private fun DrawScope.snow(
    t: Float,
    count: Int,
) {
    for (i in 0 until count) {
        val z = h(21, i)
        val y = frac(h(22, i) + t * (.03f + z * .09f)) * 1.04f - .02f
        val x = frac(h(23, i) + t * .004f * (1 + z)) * VW + sin(t * .6f + h(24, i) * 6) * 16f
        drawCircle(Color.White, (.6f + z * 2.4f) * 1.8f, Offset(x, y * VH), alpha = .35f + z * .6f)
    }
}

private fun DrawScope.christmas(
    t: Float,
    s: SceneStatics,
) {
    sky(0f to hex(0xFF050B22), .65f to hex(0xFF1C3366), 1f to hex(0xFF4A6AA0))
    stars(90, 5, 420f, t)
    drawCircle(hex(0xFFF4F1E6), 60f, Offset(300f, 170f))
    glow(Offset(300f, 170f), 160f, Color.White, .25f)
    drawPath(s.hill(560f, 110f, 2), hex(0xFF9FB7DC))
    drawPath(s.hill(640f, 70f, 4), hex(0xFFCDDCF2))
    val r = Rng(9)
    repeat(14) {
        val x = r.next() * VW
        pine(x, 640 + r.next() * 30, 120 + r.next() * 80, hex(0xFF1A4A3A))
    }
    // Cabin with a warm glow in the windows
    withTransform({ translate(1180f, 560f) }) {
        box(0f, 40f, 220f, 140f, hex(0xFF5B2E1A))
        drawPath(poly(-20f, 48f, 110f, -30f, 240f, 48f), hex(0xFFF4F8FF))
        box(160f, -20f, 30f, 50f, hex(0xFF5B2E1A))
        box(30f, 80f, 50f, 50f, hex(0xFFFFCC66), flicker(t, 5f))
        box(130f, 80f, 50f, 50f, hex(0xFFFFCC66), .8f)
        box(95f, 110f, 30f, 70f, hex(0xFF3A1A0C))
        glow(Offset(110f, 105f), 160f, hex(0xFFFFCC66), .18f)
    }
    drawPath(s.hill(760f, 60f, 8), hex(0xFFEEF4FF))
    pine(180f, 860f, 260f, hex(0xFF14402F))
    pine(1500f, 880f, 300f, hex(0xFF14402F))
    snow(t, 170)
    // String of lights along the top
    val wire =
        Path().apply {
            moveTo(-20f, 40f)
            quadraticTo(200f, 120f, 400f, 50f)
            quadraticTo(600f, -20f, 800f, 60f)
            quadraticTo(1000f, 140f, 1200f, 50f)
            quadraticTo(1400f, -40f, 1620f, 40f)
        }
    drawPath(wire, hex(0xFF1B3A22), style = Stroke(4f))
    val bulbs = listOf(hex(0xFFFF5252), hex(0xFFFFD54F), hex(0xFF69F0AE), hex(0xFF40C4FF), hex(0xFFFF80AB))
    for (i in 0 until 26) {
        val x = i * 64f + 10
        val y = 62 + abs(sin(i * .78f)) * 50
        val tw = (sin(t * TAU / 1.4f + i * .9f) + 1) / 2
        val c = bulbs[i % bulbs.size]
        drawCircle(c, 26f, Offset(x, y), alpha = .18f * (.25f + tw * .75f))
        drawCircle(c, 11f, Offset(x, y), alpha = .25f + tw * .75f)
    }
}

// ---- New Year and 4th of July: fireworks with trails bursting over a city skyline

private fun DrawScope.fireworks(
    t: Float,
    colors: List<Color>,
) {
    val slots = 6
    val period = 2.6f
    for (i in 0 until slots) {
        val shifted = t + i * period / slots
        val cycle = floor(shifted / period).toInt()
        val age = frac(shifted / period) * period
        val seed = i * 1000 + cycle
        val x = (.12f + .76f * h(seed, 1)) * VW
        val burstY = (.12f + .3f * h(seed, 2)) * VH
        val rise = .7f
        if (age < rise) {
            val p = 1 - (1 - age / rise) * (1 - age / rise)
            val y = 860f + (burstY - 860f) * p
            drawLine(hex(0xFFFFDCAA), Offset(x, y + 40), Offset(x, y), strokeWidth = 3f, alpha = .6f)
            continue
        }
        val a = age - rise
        val life = 1.6f
        if (a > life) continue
        val c1 = colors[(h(seed, 3) * colors.size).toInt() % colors.size]
        val c2 = if (h(seed, 4) < .4f) colors[(h(seed, 5) * colors.size).toInt() % colors.size] else c1
        val ring = h(seed, 6) < .3f
        val n = 70
        val sp = (.32f + .18f * h(seed, 7)) * VH
        val k = 1.386f
        val fade = (1 - a / life) * (1 - a / life)
        for (j in 0 until n) {
            val ang = j * TAU / n + h(seed, 100 + j) * .2f
            val v = sp * (if (ring) 1f else sqrt(h(seed, 200 + j)))
            val d = v * (1 - exp(-k * a)) / k
            val d0 = v * (1 - exp(-k * max(0f, a - .06f))) / k
            val g = .5f * VH * .12f * a * a
            val g0 = .5f * VH * .12f * max(0f, a - .06f) * max(0f, a - .06f)
            var alpha = fade
            if (h(seed, 300 + j) < .3f) alpha *= .4f + .6f * abs(sin(a * 30))
            drawLine(
                if (j % 2 == 0) c1 else c2,
                Offset(x + cos(ang) * d0, burstY + sin(ang) * d0 + g0),
                Offset(x + cos(ang) * d, burstY + sin(ang) * d + g),
                strokeWidth = 3.2f,
                cap = StrokeCap.Round,
                alpha = alpha,
                blendMode = BlendMode.Plus,
            )
        }
        glow(Offset(x, burstY), 120f, c1, .25f * fade)
    }
}

private fun DrawScope.fireworksCity(
    t: Float,
    s: SceneStatics,
    colors: List<Color>,
    skyColors: List<Color>,
    seed: Int,
) {
    sky(0f to skyColors[0], .7f to skyColors[1], 1f to skyColors[2])
    stars(70, seed, 500f, t)
    fireworks(t, colors)
    val city = s.skyline(seed, 805f)
    city.buildings.forEach { drawRect(hex(0xFF05060E), topLeft = it.topLeft, size = it.size) }
    city.windows.forEachIndexed { i, (r, a) ->
        val tw = if (i % 23 == 0) (sin(t * 1.3f + i) + 1) / 2 else 1f
        drawRect(hex(0xFFFFD9A0), topLeft = r.topLeft, size = r.size, alpha = a * tw)
    }
    drawRect(
        Brush.verticalGradient(0f to hex(0xFF12102A), 1f to hex(0xFF030308), startY = 805f, endY = VH),
        topLeft = Offset(0f, 805f),
        size = Size(VW, VH - 805f),
    )
}

// ---- Valentine's Day: pink sunset, a garland of swinging hearts, floating hearts and soft bokeh

private val heart =
    Path().apply {
        moveTo(0f, 30f)
        cubicTo(-40f, 0f, -40f, -30f, -18f, -34f)
        cubicTo(-6f, -36f, 0f, -26f, 0f, -20f)
        cubicTo(0f, -26f, 6f, -36f, 18f, -34f)
        cubicTo(40f, -30f, 40f, 0f, 0f, 30f)
        close()
    }

private fun DrawScope.valentine(
    t: Float,
    s: SceneStatics,
) {
    sky(0f to hex(0xFF1A0414), .55f to hex(0xFF5C1236), 1f to hex(0xFFD9577E))
    glow(Offset(800f, 720f), 420f, hex(0xFFFFC2D4), .7f)
    drawPath(s.hill(720f, 90f, 31), hex(0xFF5A1838))
    drawPath(s.hill(790f, 70f, 33), hex(0xFF3D0F27))
    // Soft bokeh drifting up
    for (i in 0 until 40) {
        val y = (1.1f - frac(h(41, i) + t * (.01f + h(42, i) * .02f)) * 1.2f) * VH
        val x = h(43, i) * VW + sin(t * .3f + i) * 20f
        val r = (.01f + h(44, i) * .035f) * VW
        val c = if (h(45, i) < .6f) hex(0xFFFF8CB4) else hex(0xFFFFE6F0)
        glow(Offset(x, y), r, c, .08f + h(46, i) * .17f)
    }
    // Floating hearts
    listOf(
        floatArrayOf(200f, 16f, 0f, .9f, 0f),
        floatArrayOf(520f, 20f, -7f, .6f, 1f),
        floatArrayOf(900f, 18f, -3f, .8f, 2f),
        floatArrayOf(1250f, 22f, -11f, .7f, 3f),
        floatArrayOf(1450f, 17f, -5f, .55f, 0f),
        floatArrayOf(700f, 24f, -15f, .5f, 1f),
    ).forEach { (x, dur, delay, sz, ci) ->
        val colors = listOf(hex(0xFFFF4F8B), hex(0xFFFF9FBF), hex(0xFFFF6F9C), hex(0xFFFFD1E0))
        val p = frac((t - delay) / dur)
        val y = 1000f - p * 1200f
        val sw = sin((t - delay) * TAU / (dur / 2.5f))
        withTransform({
            translate(x + sw * 25f, y)
            rotate(sw * 10f, pivot = Offset.Zero)
            scale(sz, sz, pivot = Offset.Zero)
        }) {
            drawPath(heart, colors[ci.toInt()], alpha = .9f)
            drawPath(
                Path().apply {
                    moveTo(-20f, -26f)
                    quadraticTo(-30f, -14f, -22f, -4f)
                },
                Color.White,
                alpha = .5f,
                style = Stroke(5f, cap = StrokeCap.Round),
            )
        }
    }
    // Garland of hearts swinging from a ribbon
    val ribbon =
        Path().apply {
            moveTo(-20f, 30f)
            quadraticTo(400f, 110f, 800f, 40f)
            quadraticTo(1200f, -30f, 1620f, 30f)
        }
    drawPath(ribbon, hex(0xFFF5C6D6), style = Stroke(3f))
    for (i in 0 until 9) {
        val x = 90f + i * 180
        val y = 55f + sin(i * .9f) * 22
        val ang = 7f * sin(t * TAU / (2 * (2.4f + (i % 3) * .5f)) + i)
        rotate(ang, pivot = Offset(x, y)) {
            drawLine(hex(0xFFF5C6D6), Offset(x, y), Offset(x, y + 40), strokeWidth = 2f)
            withTransform({
                translate(x, y + 74)
                scale(.9f, .9f, pivot = Offset.Zero)
            }) {
                drawPath(heart, if (i % 2 == 1) hex(0xFFFF4F8B) else hex(0xFFFFD1E0))
            }
        }
    }
}

// ---- St. Patrick's Day: a rainbow drawing itself over paper-cut hills, a pot of gold, rising gold sparkles

private fun DrawScope.stPatricks(
    t: Float,
    s: SceneStatics,
) {
    sky(0f to hex(0xFF04140C), .6f to hex(0xFF0C3A26), 1f to hex(0xFF2F7A4C))
    stars(30, 8, 300f, t)
    val p = frac(t / 10f)
    val grow = (p / .35f).coerceAtMost(1f)
    val eased = 1 - (1 - grow) * (1 - grow)
    val fade = if (p > .88f) 1 - (p - .88f) / .12f else 1f
    listOf(0xFFFF5252, 0xFFFFAB40, 0xFFFFEE58, 0xFF69F0AE, 0xFF40C4FF, 0xFF7C4DFF).forEachIndexed { i, c ->
        val r = 560f - i * 30
        drawArc(
            hex(c),
            startAngle = 180f,
            sweepAngle = 180f * eased,
            useCenter = false,
            topLeft = Offset(800f - r, 760f - r),
            size = Size(r * 2, r * 2),
            style = Stroke(30f),
            alpha = .85f * fade,
        )
    }
    listOf(
        Triple(650f, 110f, 51) to hex(0xFF1F6B3A),
        Triple(720f, 90f, 52) to hex(0xFF1A5A30),
        Triple(790f, 70f, 53) to hex(0xFF124A26),
        Triple(850f, 50f, 54) to hex(0xFF0B3A1C),
    ).forEach { (spec, color) ->
        val path = s.hill(spec.first, spec.second, spec.third)
        translate(0f, -8f) { drawPath(path, Color.Black, alpha = .25f) }
        drawPath(path, color)
    }
    // Pot of gold
    withTransform({ translate(1250f, 760f) }) {
        glow(Offset(0f, -10f), 130f, hex(0xFFFFD54F), .3f + .1f * sin(t * 2))
        drawPath(
            Path().apply {
                moveTo(-60f, -20f)
                quadraticTo(-70f, 50f, -40f, 60f)
                lineTo(40f, 60f)
                quadraticTo(70f, 50f, 60f, -20f)
                close()
            },
            hex(0xFF1A1A1A),
        )
        oval(0f, -20f, 62f, 16f, hex(0xFF2A2A2A))
        listOf(-30f to -30f, -8f to -38f, 16f to -32f, 36f to -26f, 0f to -26f, -20f to -22f).forEach { (cx, cy) ->
            oval(cx, cy, 16f, 7f, hex(0xFFFFD54F))
            drawOval(hex(0xFFC99A1A), Offset(cx - 16, cy - 7), Size(32f, 14f), style = Stroke(2f))
        }
    }
    // Gold sparkles rising
    for (i in 0 until 60) {
        val y = (1.05f - frac(h(61, i) + t * (.015f + h(62, i) * .025f)) * 1.1f) * VH
        val x = h(63, i) * VW
        val tw = (sin(t * 3 + h(64, i) * 6) + 1) / 2
        val r = (.4f + h(65, i) * .8f) * 10f * (.6f + tw * .6f)
        drawPath(
            Path().apply {
                moveTo(x, y - r * 2)
                quadraticTo(x, y, x + r * 2, y)
                quadraticTo(x, y, x, y + r * 2)
                quadraticTo(x, y, x - r * 2, y)
                quadraticTo(x, y, x, y - r * 2)
                close()
            },
            hex(0xFFFFD54F),
            alpha = .2f + tw * .8f,
        )
    }
}

// ---- Thanksgiving: autumn sunset, hills of orange trees, fluttering leaves

private fun DrawScope.roundTree(
    x: Float,
    b: Float,
    ht: Float,
    colors: List<Color>,
    seed: Int,
) {
    val r = Rng(seed)
    box(x - ht * .04f, b - ht * .5f, ht * .08f, ht * .5f, hex(0xFF3A1A0C))
    for (k in 0 until 5) {
        drawCircle(
            colors[k % colors.size],
            ht * (.2f + r.next() * .12f),
            Offset(x + (r.next() - .5f) * ht * .5f, b - ht * .55f - r.next() * ht * .35f),
        )
    }
}

private fun DrawScope.thanksgiving(
    t: Float,
    s: SceneStatics,
) {
    sky(0f to hex(0xFF1C0B22), .5f to hex(0xFF7A2A2A), 1f to hex(0xFFF08A3C))
    glow(Offset(800f, 610f), 420f, hex(0xFFFFD27A), .45f)
    drawCircle(hex(0xFFFFD27A), 150f, Offset(800f, 610f))
    drawPath(s.hill(620f, 120f, 61), hex(0xFF8A3A1C))
    drawPath(s.hill(700f, 80f, 63), hex(0xFF5A2010))
    val leafColors = listOf(hex(0xFFD9541E), hex(0xFFF08A24), hex(0xFFB8321A), hex(0xFFF2B134))
    val r = Rng(66)
    for (i in 0 until 12) roundTree(r.next() * VW, 720 + r.next() * 20, 140 + r.next() * 90, leafColors, 70 + i)
    drawPath(s.hill(800f, 60f, 64), hex(0xFF3A1408))
    roundTree(160f, 900f, 340f, listOf(hex(0xFFC43D18), hex(0xFFE8762A), hex(0xFFA82A14), hex(0xFFF2A830)), 3)
    roundTree(1480f, 900f, 300f, listOf(hex(0xFFE8762A), hex(0xFFC43D18), hex(0xFFF2A830), hex(0xFFA82A14)), 4)
    // Leaves tumbling down
    val fall = listOf(hex(0xFFE8762A), hex(0xFFC43D18), hex(0xFFF2B134), hex(0xFFA82A14), hex(0xFFD9541E))
    for (i in 0 until 34) {
        val sz = .6f + h(71, i) * .8f
        val y = (frac(h(72, i) + t * .05f * sz) * 1.1f - .05f) * VH
        val x = frac(h(73, i) + t * .015f) * VW + sin(t * 1.2f + h(74, i) * 6) * 60f
        val size = sz * 23f
        val flip = max(.15f, abs(cos(t * (1.5f + h(75, i) * 1.5f) + h(76, i) * 6)))
        withTransform({
            translate(x, y)
            rotate(h(77, i) * 360 + t * (h(78, i) * 4 - 2) * 57f, pivot = Offset.Zero)
            scale(1f, flip, pivot = Offset.Zero)
        }) {
            drawPath(
                Path().apply {
                    moveTo(0f, -size)
                    quadraticTo(size * .9f, -size * .2f, 0f, size)
                    quadraticTo(-size * .9f, -size * .2f, 0f, -size)
                    close()
                },
                fall[i % fall.size],
                alpha = .95f,
            )
            drawLine(hex(0x993C1405), Offset(0f, -size * .9f), Offset(0f, size * 1.2f), strokeWidth = size * .08f)
        }
    }
}

// ---- Birthday: sweeping spotlights, balloons floating up and paper confetti

private fun DrawScope.birthday(t: Float) {
    sky(0f to hex(0xFF0A0414), 1f to hex(0xFF2A0F3A))
    listOf(300f to hex(0xFFFF6FB5), 800f to hex(0xFFFFD54F), 1300f to hex(0xFF7CC8FF)).forEachIndexed { i, (x, c) ->
        val ang = 16f * sin(t * TAU / (2 * (5 + i)) - i)
        rotate(ang, pivot = Offset(x, -40f)) {
            drawPath(
                poly(x - 30, -40f, x + 30, -40f, x + 260, VH, x - 260, VH),
                Brush.verticalGradient(0f to c.copy(alpha = .5f), 1f to c.copy(alpha = 0f), startY = -40f, endY = VH),
            )
        }
    }
    oval(800f, 880f, 900f, 120f, hex(0xFF2A1240))
    listOf(
        floatArrayOf(150f, 26f, 0f, .9f, 0f),
        floatArrayOf(420f, 30f, -9f, .7f, 1f),
        floatArrayOf(1180f, 28f, -4f, .85f, 2f),
        floatArrayOf(1420f, 32f, -14f, .75f, 3f),
        floatArrayOf(1000f, 34f, -20f, .6f, 4f),
        floatArrayOf(620f, 36f, -26f, .55f, 5f),
    ).forEach { (x, dur, delay, sz, ci) ->
        val colors = listOf(0xFFFF4F8B, 0xFFFFD54F, 0xFF40C4FF, 0xFFB388FF, 0xFF69F0AE, 0xFFFF8A65)
        val c = hex(colors[ci.toInt()])
        val p = frac((t - delay) / dur)
        val y = 1000f - p * 1200f
        val sw = sin((t - delay) * TAU / (dur / 2))
        withTransform({
            translate(x + sw * 25f, y)
            rotate(sw * 6f, pivot = Offset.Zero)
            scale(sz, sz, pivot = Offset.Zero)
        }) {
            drawPath(
                Path().apply {
                    moveTo(0f, 60f)
                    quadraticTo(-10f, 100f, 6f, 140f)
                    quadraticTo(16f, 170f, 0f, 200f)
                },
                hex(0xFFDDDDDD),
                style = Stroke(2f),
            )
            oval(0f, 0f, 42f, 52f, c)
            drawPath(poly(-7f, 58f, 7f, 58f, 0f, 48f), c)
            rotate(-20f, pivot = Offset(-14f, -20f)) { oval(-14f, -20f, 9f, 16f, Color.White, .35f) }
        }
    }
    // Confetti
    val conf = listOf(0xFFFF5252, 0xFFFFEE58, 0xFF40C4FF, 0xFF69F0AE, 0xFFB388FF, 0xFFFFB74D, 0xFFFF80AB)
    for (i in 0 until 150) {
        val y = (frac(h(81, i) + t * (.06f + h(82, i) * .08f)) * 1.1f - .05f) * VH
        val x = frac(h(83, i) + t * (h(84, i) - .5f) * .06f) * VW + sin(t * 2 + i) * 16f
        val w = (5 + h(85, i) * 4) * 1.8f
        val ht = (8 + h(86, i) * 6) * 1.8f
        withTransform({
            translate(x, y)
            rotate(h(87, i) * 360 + t * (h(88, i) * 8 - 4) * 57f, pivot = Offset.Zero)
            scale(1f, cos(t * (4 + h(89, i) * 5) + h(90, i) * 6), pivot = Offset.Zero)
        }) {
            box(-w / 2, -ht / 2, w, ht, hex(conf[i % conf.size]), .95f)
        }
    }
}

// ---- Day of the Dead: twilight sky, swaying papel picado, candlelit ofrenda, falling marigold petals

private fun DrawScope.marigold(
    x: Float,
    y: Float,
    r: Float,
) {
    for (k in 0 until 10) {
        val a = k * TAU / 10
        drawCircle(hex(0xFFE8740C), r * .45f, Offset(x + cos(a) * r * .55f, y + sin(a) * r * .55f))
    }
    drawCircle(hex(0xFFFF9F1C), r * .62f, Offset(x, y))
    drawCircle(hex(0xFFFFC233), r * .32f, Offset(x, y))
}

private fun DrawScope.dayOfTheDead(
    t: Float,
    s: SceneStatics,
) {
    sky(0f to hex(0xFF1B0B33), .55f to hex(0xFF5B1B5E), 1f to hex(0xFFE86A2A))
    stars(50, 21, 380f, t)
    glow(Offset(800f, 780f), 520f, hex(0xFFFFA24D), .45f)
    drawPath(s.hill(760f, 60f, 22), hex(0xFF2A0F2E))
    // Papel picado: two strings of colored paper flags that sway in the breeze
    val flagColors = listOf(0xFFFF4F9A, 0xFFFFC233, 0xFF3DD6C6, 0xFF9B5CFF, 0xFFFF7A1A, 0xFF66D16E)
    listOf(110f to 0f, 210f to 1.7f).forEachIndexed { row, (top, phase) ->
        val n = 13
        drawPath(
            Path().apply {
                moveTo(-20f, top)
                quadraticTo(VW / 2, top + 60f, VW + 20f, top)
            },
            hex(0xFFF5E6C8),
            style = Stroke(2f),
            alpha = .7f,
        )
        for (i in 0 until n) {
            val fx = i / (n - 1f)
            val x = -20f + fx * (VW + 40f)
            val y = top + 4 * 60f * fx * (1 - fx) / 2 + 2
            val sway = sin(t * 1.6f + i * .7f + phase) * 6f
            withTransform({
                translate(x, y)
                rotate(sway, pivot = Offset.Zero)
            }) {
                val c = hex(flagColors[(i + row * 3) % flagColors.size])
                drawPath(
                    poly(-38f, 0f, 38f, 0f, 38f, 70f, 28f, 80f, 18f, 70f, 8f, 80f, -2f, 70f, -12f, 80f, -22f, 70f, -32f, 80f, -38f, 70f),
                    c,
                    alpha = .92f,
                )
                // Cut-out holes: a little flower and dots
                val hole = hex(0xFF2A0F2E)
                drawCircle(hole, 9f, Offset(0f, 34f), alpha = .55f)
                for (k in 0 until 6) {
                    val a = k * TAU / 6
                    drawCircle(hole, 5f, Offset(cos(a) * 17f, 34f + sin(a) * 17f), alpha = .55f)
                }
                drawCircle(hole, 3.5f, Offset(-26f, 12f), alpha = .55f)
                drawCircle(hole, 3.5f, Offset(26f, 12f), alpha = .55f)
            }
        }
    }
    // Ofrenda: a dark stepped altar with candles and marigolds
    box(380f, 740f, 840f, 160f, hex(0xFF1A0718))
    box(460f, 680f, 680f, 70f, hex(0xFF230A22))
    box(560f, 630f, 480f, 60f, hex(0xFF2C0E2B))
    listOf(
        420f to 740f,
        520f to 680f,
        620f to 630f,
        980f to 630f,
        1080f to 680f,
        1180f to 740f,
        700f to 740f,
        900f to 740f,
    ).forEachIndexed {
        i,
        (x, base),
        ->
        val ht = 40f + h(23, i) * 30f
        box(x - 9f, base - ht, 18f, ht, hex(0xFFF3E3C3))
        val f = flicker(t, i * 1.3f)
        glow(Offset(x, base - ht - 14f), 90f, hex(0xFFFFB347), .45f * f)
        drawPath(
            Path().apply {
                moveTo(x, base - ht - 30f * f)
                quadraticTo(x + 9f, base - ht - 6f, x, base - ht)
                quadraticTo(x - 9f, base - ht - 6f, x, base - ht - 30f * f)
                close()
            },
            hex(0xFFFFD36B),
        )
    }
    for (i in 0 until 14) {
        marigold(400f + i * 62f + h(24, i) * 20f, 735f + h(25, i) * 8f, 16f + h(26, i) * 6f)
    }
    marigold(600f, 626f, 20f)
    marigold(1000f, 626f, 20f)
    marigold(800f, 626f, 24f)
    // Petals drifting down
    for (i in 0 until 70) {
        val y = (frac(h(27, i) + t * (.02f + h(28, i) * .03f)) * 1.1f - .05f) * VH
        val x = h(29, i) * VW + sin(t * .8f + i) * 30f
        withTransform({ rotate(t * 90f * (h(30, i) - .5f) + i * 40f, pivot = Offset(x, y)) }) {
            oval(x, y, 9f, 5f, if (i % 3 == 0) hex(0xFFFFC233) else hex(0xFFFF8A1C), .85f)
        }
    }
}

// ---- Lunar New Year: deep red night, a pagoda silhouette, swaying red lanterns, gold sparks and fireworks

private fun DrawScope.lantern(
    x: Float,
    y: Float,
    s: Float,
    swing: Float,
    t: Float,
) = withTransform({
    translate(x, y)
    rotate(swing, pivot = Offset.Zero)
    scale(s, s, pivot = Offset.Zero)
}) {
    drawLine(hex(0xFFE0B04A), Offset(0f, -200f), Offset(0f, 0f), strokeWidth = 2f)
    glow(Offset(0f, 60f), 150f, hex(0xFFFF4A2E), .45f + .1f * sin(t * 2 + x))
    box(-26f, 0f, 52f, 12f, hex(0xFFE0B04A))
    oval(0f, 60f, 62f, 50f, hex(0xFFD81E1E))
    oval(0f, 60f, 40f, 50f, hex(0xFFEE3B2B))
    oval(0f, 60f, 16f, 50f, hex(0xFFFF5A3C), .8f)
    box(-26f, 108f, 52f, 12f, hex(0xFFE0B04A))
    for (k in 0 until 5) {
        drawLine(hex(0xFFE0B04A), Offset(-10f + k * 5f, 120f), Offset(-10f + k * 5f + sin(t * 3 + k) * 3f, 168f), strokeWidth = 2.5f)
    }
}

private fun DrawScope.lunarNewYear(
    t: Float,
    s: SceneStatics,
) {
    sky(0f to hex(0xFF14030A), .6f to hex(0xFF4A0A14), 1f to hex(0xFF8A1A1A))
    stars(40, 31, 420f, t)
    fireworks(t, listOf(hex(0xFFFFD54F), hex(0xFFFF4D4D), hex(0xFFFFE9A8), hex(0xFFFF8A65)))
    glow(Offset(800f, 830f), 500f, hex(0xFFFF7A3D), .35f)
    drawPath(s.hill(800f, 50f, 32), hex(0xFF1E0408))
    // Pagoda
    withTransform({ translate(1220f, 800f) }) {
        val dark = hex(0xFF12020A)
        for (tier in 0 until 4) {
            val w = 220f - tier * 40f
            val y = -tier * 90f
            box(-w * .35f, y - 70f, w * .7f, 70f, dark)
            drawPath(
                Path().apply {
                    moveTo(-w / 2 - 30f, y - 60f)
                    quadraticTo(-w / 4, y - 76f, 0f, y - 96f)
                    quadraticTo(w / 4, y - 76f, w / 2 + 30f, y - 60f)
                    quadraticTo(w / 2, y - 72f, w / 2 - 10f, y - 70f)
                    lineTo(-w / 2 + 10f, y - 70f)
                    quadraticTo(-w / 2, y - 72f, -w / 2 - 30f, y - 60f)
                    close()
                },
                dark,
            )
            for (wnd in -1..1) {
                box(wnd * w * .18f - 6f, y - 50f, 12f, 22f, hex(0xFFFFB347), .45f + .3f * flicker(t, tier * 3f + wnd))
            }
        }
        box(-3f, -420f, 6f, 70f, dark)
    }
    // A row of lanterns hanging from the top
    listOf(160f to 1f, 400f to .8f, 640f to 1.1f, 900f to .75f, 1460f to .95f).forEachIndexed { i, (x, sc) ->
        lantern(x, 40f + h(33, i) * 90f, sc, sin(t * 1.2f + i * 1.3f) * 5f, t)
    }
    // Gold sparks floating up
    for (i in 0 until 50) {
        val y = (1.05f - frac(h(34, i) + t * (.02f + h(35, i) * .03f)) * 1.1f) * VH
        val x = h(36, i) * VW + sin(t + i) * 20f
        val tw = (sin(t * 4 + i) + 1) / 2
        drawCircle(hex(0xFFFFD54F), 2f + tw * 2.5f, Offset(x, y), alpha = .3f + tw * .6f)
    }
}

// ---- Diwali: indigo night, a row of glowing diyas reflected in water, rising sky lanterns and fireworks

private fun DrawScope.diya(
    x: Float,
    y: Float,
    s: Float,
    t: Float,
    seed: Float,
) {
    val f = flicker(t, seed)
    glow(Offset(x, y - 30f * s), 120f * s, hex(0xFFFFB300), .5f * f)
    drawArc(
        hex(0xFFB5541F),
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(x - 44f * s, y - 24f * s),
        size = Size(88f * s, 48f * s),
    )
    oval(x, y, 44f * s, 8f * s, hex(0xFFD97A3A))
    drawPath(
        Path().apply {
            moveTo(x, y - 46f * s * f)
            quadraticTo(x + 13f * s, y - 10f * s, x, y - 4f * s)
            quadraticTo(x - 13f * s, y - 10f * s, x, y - 46f * s * f)
            close()
        },
        hex(0xFFFFD54F),
    )
    oval(x, y - 14f * s, 4f * s, 9f * s, Color.White, .8f)
}

private fun DrawScope.diwali(t: Float) {
    sky(0f to hex(0xFF070723), .6f to hex(0xFF1D1050), 1f to hex(0xFF4A1E6A))
    stars(80, 41, 520f, t)
    fireworks(t, listOf(hex(0xFFFFD54F), hex(0xFFFF80AB), hex(0xFF7CF5FF), hex(0xFFFFB74D), Color.White))
    // Sky lanterns drifting up
    for (i in 0 until 12) {
        val p = frac(h(42, i) + t * (.012f + h(43, i) * .01f))
        val y = 760f - p * 820f
        val x = h(44, i) * VW + sin(t * .5f + i) * 25f
        val sc = .5f + h(45, i) * .6f
        val a = (1 - p) * .9f + .1f
        glow(Offset(x, y), 70f * sc, hex(0xFFFFA040), .5f * a)
        drawPath(
            poly(x - 16f * sc, y - 24f * sc, x + 16f * sc, y - 24f * sc, x + 12f * sc, y + 22f * sc, x - 12f * sc, y + 22f * sc),
            hex(0xFFFFB55C),
            alpha = a,
        )
        box(x - 8f * sc, y + 14f * sc, 16f * sc, 6f * sc, hex(0xFFFFF0B0), a)
    }
    // Ground and water
    box(0f, 740f, VW, 40f, hex(0xFF1A0B26))
    drawRect(
        Brush.verticalGradient(0f to hex(0xFF1A1040), 1f to hex(0xFF06061A), startY = 780f, endY = VH),
        topLeft = Offset(0f, 780f),
        size = Size(VW, VH - 780f),
    )
    for (i in 0 until 9) {
        val x = 120f + i * 170f
        diya(x, 750f, 1f, t, i * 1.7f)
        // Shimmering reflection
        for (k in 0 until 4) {
            val w = 30f - k * 5f + sin(t * 3 + i + k) * 6f
            box(x - w / 2, 800f + k * 22f, w, 4f, hex(0xFFFFC14D), (.35f - k * .07f) * flicker(t, i * 1.7f))
        }
    }
    // Rangoli-style sparkles along the ground
    for (i in 0 until 40) {
        val x = h(46, i) * VW
        val y = 690f + h(47, i) * 40f
        val tw = (sin(t * 3 + i * 1.1f) + 1) / 2
        drawCircle(hex(0xFFFFE29A), 1.5f + tw * 2f, Offset(x, y), alpha = tw)
    }
}

// ---- Bonfire Night: dark hills, a big crackling bonfire, rising embers and rockets bursting overhead

private fun DrawScope.bonfireNight(
    t: Float,
    s: SceneStatics,
) {
    sky(0f to hex(0xFF04050C), .6f to hex(0xFF111426), 1f to hex(0xFF2A1A20))
    stars(70, 51, 480f, t)
    fireworks(t, listOf(hex(0xFFFFB74D), Color.White, hex(0xFF82B1FF), hex(0xFFFF5252), hex(0xFF69F0AE)))
    drawPath(s.hill(700f, 80f, 52), hex(0xFF0B0D16))
    drawPath(s.hill(790f, 50f, 53), hex(0xFF07080F))
    val cx = 800f
    val base = 820f
    glow(Offset(cx, base - 120f), 520f, hex(0xFFFF6A1A), .4f + .08f * sin(t * 6))
    // Logs
    listOf(-30f, 30f, -12f, 14f).forEachIndexed { i, ang ->
        withTransform({ rotate(ang, pivot = Offset(cx, base)) }) {
            box(cx - 10f, base - 180f + i * 10f, 20f, 190f, hex(0xFF2A160C))
        }
    }
    // Layered flames
    listOf(
        Triple(260f, 110f, hex(0xFFE0360F)),
        Triple(210f, 85f, hex(0xFFFF7A1A)),
        Triple(150f, 60f, hex(0xFFFFB13B)),
        Triple(90f, 35f, hex(0xFFFFE08A)),
    ).forEachIndexed { layer, (ht, w, c) ->
        for (k in -2..2) {
            val x = cx + k * w * .45f
            val f = flicker(t * 1.4f, layer * 5f + k * 2f)
            val tall = ht * (1f - abs(k) * .22f) * f
            val lean = sin(t * 3 + k + layer) * 18f
            drawPath(
                Path().apply {
                    moveTo(x - w * .5f, base)
                    quadraticTo(x - w * .6f, base - tall * .5f, x + lean, base - tall)
                    quadraticTo(x + w * .6f, base - tall * .5f, x + w * .5f, base)
                    close()
                },
                c,
                alpha = .9f,
            )
        }
    }
    // Embers rising and drifting
    for (i in 0 until 80) {
        val p = frac(h(54, i) + t * (.08f + h(55, i) * .1f))
        val y = base - 80f - p * 700f
        val x = cx + (h(56, i) - .5f) * 260f + sin(t * 2 + i) * 30f * p + (h(57, i) - .5f) * 300f * p
        val a = (1 - p) * (.5f + .5f * abs(sin(t * 8 + i)))
        drawCircle(if (i % 4 == 0) hex(0xFFFFE08A) else hex(0xFFFF8A2A), 2.5f + (1 - p) * 2.5f, Offset(x, y), alpha = a)
    }
    // Ground
    drawRect(
        Brush.verticalGradient(0f to hex(0xFF1A0E0A), 1f to hex(0xFF050304), startY = base, endY = VH),
        topLeft = Offset(0f, base),
        size = Size(VW, VH - base),
    )
}

// ---- Tanabata: the Milky Way, the two star lovers Vega and Altair, bamboo with wishes on paper strips, fireflies

private fun DrawScope.tanabata(
    t: Float,
    s: SceneStatics,
) {
    sky(0f to hex(0xFF030616), .6f to hex(0xFF0B1638), 1f to hex(0xFF1C2E5C))
    stars(140, 61, 700f, t)
    // Milky Way: a soft diagonal band of light and tiny stars
    withTransform({ rotate(-28f, pivot = Offset(800f, 380f)) }) {
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                .5f to hex(0xFFB9C8FF).copy(alpha = .22f),
                1f to Color.Transparent,
                startY = 300f,
                endY = 460f,
            ),
            topLeft = Offset(-300f, 300f),
            size = Size(VW + 600f, 160f),
        )
        for (i in 0 until 160) {
            val x = -300f + h(62, i) * (VW + 600f)
            val y = 380f + (h(63, i) + h(64, i) - 1f) * 70f
            drawCircle(Color.White, .6f + h(65, i) * 1.2f, Offset(x, y), alpha = .3f + .5f * h(66, i))
        }
    }
    // Vega and Altair on either side of the river of stars
    listOf(Offset(520f, 170f) to 0f, Offset(1120f, 560f) to 1.6f).forEach { (c, ph) ->
        val tw = (sin(t * 1.8f + ph) + 1) / 2
        glow(c, 90f, hex(0xFF9FB8FF), .4f + tw * .3f)
        drawCircle(Color.White, 5f + tw * 2f, c)
        val r = 26f + tw * 12f
        drawLine(Color.White, c - Offset(r, 0f), c + Offset(r, 0f), strokeWidth = 1.5f, alpha = .7f)
        drawLine(Color.White, c - Offset(0f, r), c + Offset(0f, r), strokeWidth = 1.5f, alpha = .7f)
    }
    drawPath(s.hill(800f, 50f, 67), hex(0xFF050A18))
    // Bamboo stalk with leaves and wish strips
    val bx = 240f
    for (seg in 0 until 8) {
        val y = 860f - seg * 95f
        box(bx - 10f, y - 92f, 20f, 92f, hex(0xFF1F5A3A))
        box(bx - 12f, y - 4f, 24f, 6f, hex(0xFF174A2E))
    }
    val strips = listOf(0xFFFF6B8B, 0xFFFFD166, 0xFF6BD3FF, 0xFFB28DFF, 0xFF7CF29A, 0xFFFFA05C)
    for (i in 0 until 9) {
        val side = if (i % 2 == 0) 1f else -1f
        val y = 180f + i * 70f
        val sway = sin(t * 1.3f + i * .9f) * 6f
        withTransform({ rotate(sway * side, pivot = Offset(bx, y)) }) {
            drawPath(
                Path().apply {
                    moveTo(bx, y)
                    quadraticTo(bx + side * 90f, y - 30f, bx + side * 180f, y + 10f)
                    quadraticTo(bx + side * 90f, y - 6f, bx, y)
                    close()
                },
                hex(0xFF2E7A4E),
            )
            val sx = bx + side * (60f + h(68, i) * 70f)
            withTransform({ rotate(sin(t * 2f + i) * 8f, pivot = Offset(sx, y)) }) {
                drawLine(hex(0xFFDDDDDD), Offset(sx, y - 6f), Offset(sx, y + 6f), strokeWidth = 1f)
                box(sx - 11f, y + 6f, 22f, 64f, hex(strips[i % strips.size]), .92f)
            }
        }
    }
    // Fireflies
    for (i in 0 until 30) {
        val x = h(69, i) * VW + sin(t * .7f + i) * 40f
        val y = 620f + h(70, i) * 240f + cos(t * .9f + i * 2) * 25f
        val tw = (sin(t * 2.5f + i * 1.7f) + 1) / 2
        glow(Offset(x, y), 18f, hex(0xFFD8FF7A), .6f * tw)
        drawCircle(hex(0xFFF2FFB0), 2.5f, Offset(x, y), alpha = tw)
    }
}
