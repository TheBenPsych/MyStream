package com.github.damontecres.wholphin.ui.nav

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.LogoEffect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * A looping 0..1 clock for the logo animations
 */
@Composable
private fun loop(durationMs: Int): State<Float> =
    rememberInfiniteTransition(label = "logo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Restart),
        label = "logo",
    )

private fun frac(x: Float): Float = x - kotlin.math.floor(x)

private fun smooth(
    from: Float,
    to: Float,
    x: Float,
): Float {
    val p = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return p * p * (3 - 2 * p)
}

/**
 * Animation drawn behind the logo (fireworks, the peeking ghost, falling leaves)
 */
@Composable
fun BoxScope.LogoEffectBehind(effect: LogoEffect) {
    when (effect) {
        LogoEffect.FIREWORKS_GOLD -> {
            Fireworks(listOf(Color(0xFFFFD54F), Color.White, Color(0xFFFF80AB)))
        }

        LogoEffect.FIREWORKS_USA -> {
            Fireworks(listOf(Color(0xFFE50914), Color.White, Color(0xFF40C4FF)))
        }

        LogoEffect.GHOST_PEEK -> {
            GhostPeek()
        }

        LogoEffect.FALLING_LEAVES -> {
            FallingLeaves()
        }

        LogoEffect.MARIGOLDS -> {
            MarigoldPetals()
        }

        LogoEffect.EMBERS -> {
            RisingEmbers()
        }

        LogoEffect.BLOSSOMS -> {
            BlossomPetals()
        }

        LogoEffect.HYPERSPACE -> {
            Hyperspace()
        }

        LogoEffect.COIN -> {
            CoinPop()
        }

        LogoEffect.FIRE_TRAILS -> {
            FireTrails()
        }

        LogoEffect.SUNSET -> {
            SettingSun()
        }

        LogoEffect.GROUNDHOG -> {
            GroundhogPeek()
        }

        LogoEffect.HARVEST_MOON -> {
            HarvestMoon()
        }

        LogoEffect.CAT_TAIL -> {
            CatTail()
        }

        else -> {}
    }
}

/**
 * Animation drawn on top of the logo (heart, lights, confetti, sparkles)
 */
@Composable
fun BoxScope.LogoEffectInFront(effect: LogoEffect) {
    when (effect) {
        LogoEffect.HEART_FLOAT -> {
            FloatingHearts()
        }

        LogoEffect.GREEN_SHIMMER -> {
            GoldSparkles()
        }

        LogoEffect.TWINKLE_LIGHTS -> {
            TwinkleLights()
        }

        LogoEffect.CONFETTI -> {
            Confetti()
        }

        LogoEffect.LANTERNS -> {
            HangingLanterns()
        }

        LogoEffect.DIYAS -> {
            DiyaFlames()
        }

        LogoEffect.STAR_PAIR -> {
            StarPair()
        }

        LogoEffect.ZAPS -> {
            Zaps()
        }

        LogoEffect.FLOWER_BLOOM -> {
            FlowerBloom()
        }

        LogoEffect.SPROUTS -> {
            Sprouts()
        }

        LogoEffect.SPOOKY_CAT -> {
            WalkingCat()
        }

        LogoEffect.POPCORN -> {
            PopcornPop()
        }

        LogoEffect.GAME_BUTTONS -> {
            GameButtons()
        }

        LogoEffect.PAW_PRINTS -> {
            PawPrints()
        }

        LogoEffect.PIRATE_FLAG -> {
            PirateFlag()
        }

        else -> {}
    }
}

/** Friday the 13th: the logo flickers now and then like a bad light bulb */
@Composable
private fun Modifier.logoFlicker(): Modifier {
    val t by loop(3000)
    return graphicsLayer {
        val x = t * 2 * PI.toFloat() * 3
        alpha = if (sin(x * 7.7f) > -.3f && sin(x * 1.8f) > -.8f) 1f else .3f
    }
}

/** Pirate Day: the logo bobs gently like it's on the waves */
@Composable
private fun Modifier.logoBob(): Modifier {
    val t by loop(4000)
    return graphicsLayer {
        val x = t * 2 * PI.toFloat()
        translationY = sin(x) * 2.dp.toPx()
        rotationZ = sin(x * 2 + 1) * 1.5f
    }
}

/**
 * Tints the logo green with a gold shine that sweeps across it every few seconds
 */
@Composable
fun Modifier.logoShimmer(effect: LogoEffect): Modifier {
    if (effect == LogoEffect.SPOOKY_CAT) return logoFlicker()
    if (effect == LogoEffect.PIRATE_FLAG) return logoBob()
    if (effect != LogoEffect.GREEN_SHIMMER) return this
    val t by loop(2800)
    return this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val w = size.width
            val start = -w + smooth(0f, .6f, t) * 2.4f * w
            drawRect(
                brush =
                    Brush.linearGradient(
                        0f to Color(0xFF137A2C),
                        .4f to Color(0xFFD4FFB0),
                        .55f to Color(0xFF2FBF4F),
                        1f to Color(0xFF137A2C),
                        start = Offset(start, 0f),
                        end = Offset(start + w * .8f, size.height * .3f),
                    ),
                blendMode = BlendMode.SrcAtop,
            )
        }
}

@Composable
private fun BoxScope.Fireworks(colors: List<Color>) {
    val t by loop(2400)
    Canvas(modifier = Modifier.matchParentSize()) {
        val spots = listOf(Offset(.12f, .2f), Offset(.82f, .15f), Offset(.5f, -.1f))
        spots.forEachIndexed { i, spot ->
            val p = frac(t - i / 3f)
            if (p < .8f) {
                val grow = 1 - (1 - p / .8f) * (1 - p / .8f)
                val alpha = smooth(0f, .08f, p) * (1 - smooth(.55f, .8f, p))
                val radius = (if (i == 2) 15.dp else 12.dp).toPx() * grow
                val center = Offset(spot.x * size.width, spot.y * size.height)
                val count = if (i == 2) 12 else 10
                for (k in 0 until count) {
                    val a = k * 2 * PI / count
                    drawCircle(
                        color = colors[i % colors.size],
                        radius = 1.4.dp.toPx(),
                        center = center + Offset((cos(a) * radius).toFloat(), (sin(a) * radius).toFloat()),
                        alpha = alpha,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.FloatingHearts() {
    val t by loop(2600)
    listOf(0f to .5f, .5f to .2f).forEachIndexed { i, (delay, x) ->
        val p = frac(t - delay)
        val alpha = smooth(0f, .15f, p) * (1 - smooth(.7f, .8f, p))
        val scale =
            if (p < .15f) {
                .3f + p / .15f * .7f
            } else if (p < .7f) {
                1f
            } else {
                1f + (p - .7f) * 6f
            }
        Text(
            text = if (i == 0) "💗" else "💕",
            fontSize = if (i == 0) 13.sp else 10.sp,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (x * 100).dp)
                    .graphicsLayer {
                        translationY = -(6.dp.toPx() + 30.dp.toPx() * smooth(0f, .8f, p))
                        this.alpha = alpha
                        scaleX = scale
                        scaleY = scale
                    },
        )
    }
}

@Composable
private fun BoxScope.GoldSparkles() {
    val t by loop(1400)
    Canvas(modifier = Modifier.matchParentSize()) {
        listOf(Offset(.1f, -.15f) to 0f, Offset(.94f, .85f) to .5f).forEach { (spot, delay) ->
            val tw = (sin(frac(t + delay) * 2 * PI).toFloat() + 1) / 2
            drawSparkle(Offset(spot.x * size.width, spot.y * size.height), 3.dp.toPx() * (.75f + tw * .35f), .25f + tw * .75f)
        }
    }
}

private fun DrawScope.drawSparkle(
    center: Offset,
    r: Float,
    alpha: Float,
) {
    val path =
        Path().apply {
            moveTo(center.x, center.y - r)
            quadraticTo(center.x, center.y, center.x + r, center.y)
            quadraticTo(center.x, center.y, center.x, center.y + r)
            quadraticTo(center.x, center.y, center.x - r, center.y)
            quadraticTo(center.x, center.y, center.x, center.y - r)
            close()
        }
    drawPath(path, Color(0xFFFFD54F), alpha = alpha)
}

@Composable
private fun BoxScope.GhostPeek() {
    val t by loop(3600)
    val show = smooth(.15f, .3f, t) * (1 - smooth(.65f, .8f, t))
    val wobble = sin(t * 2 * PI * 4).toFloat() * 8f * show
    Text(
        text = "👻",
        fontSize = 16.sp,
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 14.dp, y = (-10).dp)
                .graphicsLayer {
                    translationX = -12.dp.toPx() * (1 - show)
                    translationY = 8.dp.toPx() * (1 - show)
                    rotationZ = wobble - 12f * (1 - show)
                    alpha = show
                },
    )
}

@Composable
private fun BoxScope.FallingLeaves() {
    val t by loop(5000)
    listOf("🍂", "🍁", "🍂", "🍁").forEachIndexed { i, leaf ->
        val p = frac(t - i / 4f)
        Text(
            text = leaf,
            fontSize = 10.sp,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (8 + i * 24).dp)
                    .graphicsLayer {
                        translationY = -10.dp.toPx() + 42.dp.toPx() * p
                        translationX = sin(p * 2 * PI).toFloat() * 6.dp.toPx()
                        rotationZ = p * 200f
                        alpha = smooth(0f, .1f, p) * (1 - smooth(.75f, 1f, p))
                    },
        )
    }
}

@Composable
private fun BoxScope.TwinkleLights() {
    val t by loop(1200)
    val bulbs = listOf(Color(0xFFFF5252), Color(0xFFFFD54F), Color(0xFF69F0AE), Color(0xFF40C4FF), Color(0xFFFF80AB))
    Canvas(modifier = Modifier.matchParentSize()) {
        val y = -4.dp.toPx()
        val wire =
            Path().apply {
                moveTo(-4.dp.toPx(), y)
                quadraticTo(size.width * .25f, y + 6.dp.toPx(), size.width * .5f, y + 1.dp.toPx())
                quadraticTo(size.width * .75f, y + 6.dp.toPx(), size.width + 4.dp.toPx(), y)
            }
        drawPath(wire, Color(0xFF2E7D32), style = Stroke(width = 1.dp.toPx()))
        for (k in 0 until 9) {
            val x = size.width * (.04f + k * .115f)
            val bulbY = y + 3.dp.toPx() + sin(k * 1.3f) * 1.dp.toPx()
            val tw = (sin(frac(t + k * .125f) * 2 * PI).toFloat() + 1) / 2
            drawCircle(bulbs[k % bulbs.size], radius = 2.2.dp.toPx() * (.8f + tw * .3f), center = Offset(x, bulbY), alpha = .3f + tw * .7f)
        }
    }
}

@Composable
private fun BoxScope.Confetti() {
    val t by loop(3000)
    val colors = listOf(Color(0xFFFF5252), Color(0xFFFFEE58), Color(0xFF40C4FF), Color(0xFF69F0AE), Color(0xFFB388FF), Color(0xFFFFB74D))
    Canvas(modifier = Modifier.matchParentSize()) {
        for (k in 0 until 12) {
            val p = frac(t - k / 12f)
            val x = size.width * (.04f + (k * 37 % 12) / 12f * .92f) + sin(p * 2 * PI + k).toFloat() * 3.dp.toPx()
            val y = -8.dp.toPx() + 44.dp.toPx() * p
            val alpha = smooth(0f, .1f, p) * (1 - smooth(.7f, 1f, p))
            rotate(degrees = p * 360f + k * 30f, pivot = Offset(x, y)) {
                drawRect(
                    color = colors[k % colors.size],
                    topLeft = Offset(x - 1.5.dp.toPx(), y - 2.5.dp.toPx()),
                    size = Size(3.dp.toPx(), 5.dp.toPx()),
                    alpha = alpha,
                )
            }
        }
    }
}

/** Day of the Dead: orange and gold marigold petals drifting down past the logo */
@Composable
private fun BoxScope.MarigoldPetals() {
    val t by loop(4200)
    val colors = listOf(Color(0xFFFF9F1C), Color(0xFFFFC233), Color(0xFFFF7A00), Color(0xFFFFD166))
    Canvas(modifier = Modifier.matchParentSize()) {
        for (k in 0 until 8) {
            val p = frac(t - k / 8f)
            val x = size.width * (.05f + (k * 5 % 8) / 8f * .9f) + sin(p * 2 * PI * 1.5 + k).toFloat() * 4.dp.toPx()
            val y = -8.dp.toPx() + 42.dp.toPx() * p
            val alpha = smooth(0f, .1f, p) * (1 - smooth(.72f, 1f, p))
            rotate(degrees = p * 300f + k * 45f, pivot = Offset(x, y)) {
                drawOval(
                    color = colors[k % colors.size],
                    topLeft = Offset(x - 2.dp.toPx(), y - 1.2.dp.toPx()),
                    size = Size(4.dp.toPx(), 2.4.dp.toPx()),
                    alpha = alpha,
                )
            }
        }
    }
}

/** Bonfire Night: little orange embers floating up from under the logo */
@Composable
private fun BoxScope.RisingEmbers() {
    val t by loop(2600)
    Canvas(modifier = Modifier.matchParentSize()) {
        for (k in 0 until 10) {
            val p = frac(t - k / 10f)
            val x = size.width * (.06f + (k * 7 % 10) / 10f * .88f) + sin(p * 2 * PI * 2 + k).toFloat() * 3.dp.toPx()
            val y = size.height + 6.dp.toPx() - 40.dp.toPx() * p
            val alpha = smooth(0f, .1f, p) * (1 - smooth(.5f, 1f, p))
            val c = if (k % 3 == 0) Color(0xFFFFD54F) else Color(0xFFFF7A1A)
            drawCircle(c.copy(alpha = alpha * .35f), radius = 3.dp.toPx(), center = Offset(x, y))
            drawCircle(c, radius = (1.4f - p * .6f).dp.toPx(), center = Offset(x, y), alpha = alpha)
        }
    }
}

/** Lunar New Year: two small red lanterns with gold trim swinging from the logo */
@Composable
private fun BoxScope.HangingLanterns() {
    val t by loop(2400)
    Canvas(modifier = Modifier.matchParentSize()) {
        listOf(-.02f to 0f, 1.02f to .5f).forEach { (fx, delay) ->
            val swing = sin(frac(t + delay) * 2 * PI).toFloat() * 10f
            val top = Offset(fx * size.width, -2.dp.toPx())
            rotate(degrees = swing, pivot = top) {
                val cord = 5.dp.toPx()
                drawLine(Color(0xFFFFC94D), top, top + Offset(0f, cord), strokeWidth = .8.dp.toPx())
                val w = 9.dp.toPx()
                val hgt = 10.dp.toPx()
                val c = top + Offset(0f, cord + hgt / 2)
                drawCircle(Color(0xFFFF3B30).copy(alpha = .35f), radius = 9.dp.toPx(), center = c)
                drawOval(Color(0xFFE0201B), topLeft = c - Offset(w / 2, hgt / 2), size = Size(w, hgt))
                drawOval(Color(0xFFFF5A47), topLeft = c - Offset(w / 4, hgt / 2), size = Size(w / 2, hgt), alpha = .7f)
                drawRect(Color(0xFFFFC94D), topLeft = c - Offset(w / 3, hgt / 2 + 1.dp.toPx()), size = Size(w * 2 / 3, 1.5.dp.toPx()))
                drawRect(Color(0xFFFFC94D), topLeft = c + Offset(-w / 3, hgt / 2 - .5.dp.toPx()), size = Size(w * 2 / 3, 1.5.dp.toPx()))
                drawLine(
                    Color(0xFFFFC94D),
                    c + Offset(0f, hgt / 2 + 1.dp.toPx()),
                    c + Offset(0f, hgt / 2 + 5.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
    }
}

/** Diwali: a row of tiny clay lamps with flickering flames under the logo */
@Composable
private fun BoxScope.DiyaFlames() {
    val t by loop(900)
    Canvas(modifier = Modifier.matchParentSize()) {
        val y = size.height + 3.dp.toPx()
        for (k in 0 until 5) {
            val x = size.width * (.1f + k * .2f)
            val flick = (sin((t + k * .37f) * 2 * PI).toFloat() + sin((t * 2 + k) * 2 * PI).toFloat() * .5f) * .5f
            drawCircle(Color(0xFFFFB300).copy(alpha = .3f + flick * .08f), radius = 6.dp.toPx(), center = Offset(x, y - 3.dp.toPx()))
            drawArc(
                Color(0xFFB5541F),
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(x - 4.dp.toPx(), y - 2.dp.toPx()),
                size = Size(8.dp.toPx(), 4.dp.toPx()),
            )
            val fh = (4.5f + flick * .8f).dp.toPx()
            val flame =
                Path().apply {
                    moveTo(x, y - fh)
                    quadraticTo(x + 2.dp.toPx(), y - 1.dp.toPx(), x, y)
                    quadraticTo(x - 2.dp.toPx(), y - 1.dp.toPx(), x, y - fh)
                    close()
                }
            drawPath(flame, Color(0xFFFFD54F))
        }
    }
}

/** Tanabata: two stars, Vega and Altair, twinkling across a faint Milky Way over the logo */
@Composable
private fun BoxScope.StarPair() {
    val t by loop(2200)
    Canvas(modifier = Modifier.matchParentSize()) {
        drawLine(
            Brush.linearGradient(
                0f to Color.Transparent,
                .5f to Color(0x55B9C8FF),
                1f to Color.Transparent,
                start = Offset(size.width * .2f, -6.dp.toPx()),
                end = Offset(size.width * .8f, size.height + 6.dp.toPx()),
            ),
            Offset(size.width * .2f, -6.dp.toPx()),
            Offset(size.width * .8f, size.height + 6.dp.toPx()),
            strokeWidth = 6.dp.toPx(),
        )
        listOf(Offset(.04f, -.2f) to 0f, Offset(.96f, 1.1f) to .5f).forEach { (spot, delay) ->
            val tw = (sin(frac(t + delay) * 2 * PI).toFloat() + 1) / 2
            val c = Offset(spot.x * size.width, spot.y * size.height)
            drawCircle(Color(0xFF9FB8FF).copy(alpha = .25f + tw * .2f), radius = 5.dp.toPx(), center = c)
            drawCircle(Color.White, radius = (1.2f + tw * .6f).dp.toPx(), center = c)
            val r = (3.5f + tw * 1.5f).dp.toPx()
            drawLine(Color.White.copy(alpha = .6f), c - Offset(r, 0f), c + Offset(r, 0f), strokeWidth = .6.dp.toPx())
            drawLine(Color.White.copy(alpha = .6f), c - Offset(0f, r), c + Offset(0f, r), strokeWidth = .6.dp.toPx())
        }
    }
}

/**
 * Draws in a small 300 x 90 sketch space where the logo sits in the middle (about x 75..225, y 26..70),
 * so the logo touches can be drawn the same way they were in the mockups
 */
private fun DrawScope.inLogoSpace(block: DrawScope.() -> Unit) {
    val k = size.height / 44f
    withTransform({
        translate(size.width / 2 - 150f * k, -26f * k)
        scale(k, k, pivot = Offset.Zero)
    }, block)
}

private fun DrawScope.dot(
    x: Float,
    y: Float,
    rx: Float,
    ry: Float,
    color: Color,
    alpha: Float = 1f,
) = drawOval(color, topLeft = Offset(x - rx, y - ry), size = Size(rx * 2, ry * 2), alpha = alpha)

private fun DrawScope.halo(
    x: Float,
    y: Float,
    r: Float,
    color: Color,
    alpha: Float,
) = drawCircle(
    Brush.radialGradient(0f to color.copy(alpha = alpha), 1f to color.copy(alpha = 0f), center = Offset(x, y), radius = r),
    radius = r,
    center = Offset(x, y),
)

private fun hash(
    a: Int,
    b: Int,
): Float {
    var x = a * 73856093 xor b * 19349663
    x = x xor (x ushr 13)
    x *= 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16777216f
}

/** Easter: pink blossom petals drift down past the logo */
@Composable
private fun BoxScope.BlossomPetals() {
    val t by loop(4000)
    Canvas(Modifier.matchParentSize()) {
        inLogoSpace {
            for (k in 0 until 9) {
                val p = frac(t - k / 9f)
                val x = 60f + ((k * 5) % 9) / 9f * 180f + sin(p * 2 * PI * 1.5 + k).toFloat() * 8f
                val y = -6f + p * 100f
                val a = (p * 8).coerceAtMost(1f) * (1 - ((p - .75f) * 4).coerceIn(0f, 1f))
                rotate((p * 5 + k) * 57f, Offset(x, y)) { dot(x, y, 5f, 3f, Color(0xFFFFC4D6), a) }
            }
        }
    }
}

/** Star Wars Day: lightspeed streaks fly out from behind the logo */
@Composable
private fun BoxScope.Hyperspace() {
    val t by loop(1700)
    Canvas(Modifier.matchParentSize()) {
        inLogoSpace {
            for (i in 0 until 24) {
                val a = hash(91, i) * 2 * PI.toFloat()
                val p = frac(t + hash(92, i))
                val d = 10f + p * 150f
                val x = 150f + cos(a) * d
                val y = 48f + sin(a) * d * .45f
                drawLine(
                    Color(0xFFC8E1FF),
                    Offset(x, y),
                    Offset(x + cos(a) * p * 30f, y + sin(a) * p * 14f),
                    strokeWidth = 1.5f,
                    alpha = (1 - p) * .9f,
                )
            }
        }
    }
}

/** Pokémon Day: tiny yellow lightning zaps crackle around the logo */
@Composable
private fun BoxScope.Zaps() {
    val t by loop(3300)
    Canvas(Modifier.matchParentSize()) {
        inLogoSpace {
            listOf(52f to 30f, 250f to 64f, 240f to 18f).forEachIndexed { i, (x, y) ->
                if (frac(t * 3 + i * .33f) < .35f) {
                    halo(x + 2f, y, 20f, Color(0xFFFFE63C), .5f)
                    drawPath(
                        Path().apply {
                            moveTo(x, y - 12f)
                            lineTo(x + 7f, y - 2f)
                            lineTo(x - 2f, y)
                            lineTo(x + 6f, y + 12f)
                        },
                        Color(0xFFFFDE00),
                        style = Stroke(3f),
                    )
                }
            }
        }
    }
}

/** Mario Day: a pixel gold coin pops up out of the logo and spins */
@Composable
private fun BoxScope.CoinPop() {
    val t by loop(2200)
    val spin by loop(800)
    Canvas(Modifier.matchParentSize()) {
        inLogoSpace {
            val y = 48f - sin((t * 1.6f).coerceAtMost(1f) * PI).toFloat() * 46f
            val a = if (t < .62f) 1f else (1 - (t - .62f) * 4).coerceAtLeast(0f)
            val w = abs(cos(spin * 2 * PI).toFloat()) * 14f + 3f
            drawRect(Color(0xFFFBD000), Offset(150f - w / 2, y - 10f), Size(w, 20f), alpha = a)
            drawRect(Color(0xFFFFF6A0), Offset(150f - w / 5, y - 6f), Size(max(2f, w / 3), 12f), alpha = a)
        }
    }
}

/** Back to the Future Day: two fire trails streak under the logo */
@Composable
private fun BoxScope.FireTrails() {
    val t by loop(2400)
    Canvas(Modifier.matchParentSize()) {
        inLogoSpace {
            val hx = -40f + t * 400f
            listOf(72f, 82f).forEach { y ->
                drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, 1f to Color(0xFFFFD27A), startX = hx - 160f, endX = hx),
                    topLeft = Offset(hx - 160f, y),
                    size = Size(160f, 3f),
                )
            }
        }
    }
}

/** Mother's Day: a little flower blooms at the end of the logo */
@Composable
private fun BoxScope.FlowerBloom() {
    val t by loop(4000)
    val turn by loop(20000)
    Canvas(Modifier.matchParentSize()) {
        inLogoSpace {
            val p = (t * 2.5f).coerceAtMost(1f)
            val x = 236f
            val y = 26f
            drawPath(
                Path().apply {
                    moveTo(x - 6f, y + 18f)
                    quadraticTo(x, y + 10f, x, y)
                },
                Color(0xFF4F9A3E),
                style = Stroke(2f),
            )
            val r = p * 10f
            for (k in 0 until 5) {
                val a = k * 2 * PI.toFloat() / 5 + turn * 6f
                dot(x + cos(a) * r * .7f, y + sin(a) * r * .7f, r * .5f, r * .5f, Color(0xFFFF9EC7))
            }
            dot(x, y, r * .35f, r * .35f, Color(0xFFFFE08A))
        }
    }
}

/** Father's Day: a warm sun sets slowly behind the logo */
@Composable
private fun BoxScope.SettingSun() {
    val t by loop(6000)
    Canvas(Modifier.matchParentSize()) {
        inLogoSpace {
            val y = 30f + t * 36f
            clipRect(0f, -40f, 300f, 72f) {
                halo(150f, y, 70f, Color(0xFFFFB45A), .7f)
                dot(150f, y, 22f, 22f, Color(0xFFFFB35C), .9f)
            }
            drawRect(Color(0xFFFFB45A), Offset(70f, 72f), Size(160f, 1.5f), alpha = .35f)
        }
    }
}

/** Earth Day: two small green leaves sprout from the logo and sway */
@Composable
private fun BoxScope.Sprouts() {
    val t by loop(3100)
    Canvas(Modifier.matchParentSize()) {
        inLogoSpace {
            listOf(Triple(64f, 22f, -1f), Triple(236f, 22f, 1f)).forEachIndexed { i, (x, y, side) ->
                val sw = sin((t + i * .5f) * 2 * PI).toFloat() * .25f
                rotate((side * .6f + sw) * 57f, Offset(x, y + 8f)) {
                    drawPath(
                        Path().apply {
                            moveTo(x, y + 8f)
                            quadraticTo(x + 8f * side, y + 2f, x + 16f * side, y + 8f)
                            quadraticTo(x + 8f * side, y + 14f, x, y + 8f)
                            close()
                        },
                        Color(0xFF6CC06A),
                    )
                }
            }
        }
    }
}

/** Friday the 13th: a black cat walks along the top of the logo (the logo itself flickers, see [logoShimmer]) */
@Composable
private fun BoxScope.WalkingCat() {
    val t by loop(7000)
    val step by loop(600)
    Canvas(Modifier.matchParentSize()) {
        inLogoSpace {
            val x = 40f + t * 220f
            val y = 19f
            val st = sin(step * 2 * PI).toFloat()
            val c = Color(0xFF111111)
            dot(x, y, 12f, 7f, c)
            dot(x + 12f, y - 5f, 6f, 6f, c)
            drawPath(poly(x + 8f, y - 10f, x + 10f, y - 15f, x + 12f, y - 10f), c)
            drawPath(poly(x + 14f, y - 10f, x + 16f, y - 15f, x + 18f, y - 9f), c)
            drawPath(
                Path().apply {
                    moveTo(x - 12f, y)
                    quadraticTo(x - 20f, y - 10f, x - 16f, y - 16f)
                },
                c,
                style = Stroke(2f),
            )
            drawRect(c, Offset(x - 7f + st * 2f, y + 5f), Size(2f, 7f))
            drawRect(c, Offset(x + 6f - st * 2f, y + 5f), Size(2f, 7f))
            dot(x + 14f, y - 6f, 1.5f, 1.5f, Color(0xFFFFE14D))
        }
    }
}

/** Groundhog Day: a little groundhog pops up behind the logo, looks left and right, then hides */
@Composable
private fun BoxScope.GroundhogPeek() {
    val c by loop(3500)
    Canvas(Modifier.matchParentSize()) {
        inLogoSpace {
            val up =
                when {
                    c < .15f -> c / .15f
                    c < .7f -> 1f
                    c < .8f -> 1 - (c - .7f) / .1f
                    else -> 0f
                }
            val look = if (c > .15f && c < .7f) sin((c - .15f) / .55f * 2 * PI * 1.5).toFloat() * 3f else 0f
            val x = 220f
            val y = 30f - up * 24f
            clipRect(0f, -40f, 300f, 30f) {
                dot(x, y + 14f, 13f, 16f, Color(0xFF8A5A3A))
                dot(x - 9f, y + 1f, 3f, 3f, Color(0xFF7A4A2A))
                dot(x + 9f, y + 1f, 3f, 3f, Color(0xFF7A4A2A))
                dot(x - 4f + look, y + 8f, 2f, 2.4f, Color(0xFF111111))
                dot(x + 4f + look, y + 8f, 2f, 2.4f, Color(0xFF111111))
            }
        }
    }
}

/** Mid-Autumn Festival: a round golden moon rises behind the logo, with a tiny lantern beside it */
@Composable
private fun BoxScope.HarvestMoon() {
    val t by loop(6000)
    val bob by loop(4200)
    Canvas(Modifier.matchParentSize()) {
        inLogoSpace {
            val y = 70f - (t * 1.6f).coerceAtMost(1f) * 40f
            halo(150f, y, 70f, Color(0xFFFFD278), .6f)
            dot(150f, y, 26f, 26f, Color(0xFFFFD36B))
            val ly = 40f + sin(bob * 2 * PI).toFloat() * 3f
            halo(242f, ly, 20f, Color(0xFFFF783C), .6f)
            dot(242f, ly, 7f, 6f, Color(0xFFFF5A3A))
            drawRect(Color(0xFFFFD36B), Offset(237f, ly - 7f), Size(10f, 2f))
            drawRect(Color(0xFFFFD36B), Offset(237f, ly + 5f), Size(10f, 2f))
        }
    }
}

/** Popcorn Day: little popcorn pieces pop up out of the logo and fall back down */
@Composable
private fun BoxScope.PopcornPop() {
    val t by loop(1800)
    Canvas(Modifier.matchParentSize()) {
        inLogoSpace {
            for (i in 0 until 6) {
                val p = frac(t + i / 6f)
                if (p > .95f) continue
                val x = 70f + i * 32f
                val y = 46f - sin(p * PI).toFloat() * 44f
                rotate((p * 4 + i) * 57f, Offset(x, y)) {
                    for (k in 0 until 3) dot(x + cos(k * 2.1f) * 3f, y + sin(k * 2.1f) * 3f, 4f, 3.6f, Color(0xFFFFF7E0))
                    dot(x, y, 2.4f, 2.4f, Color(0xFFFFD56B))
                }
            }
        }
    }
}

/** Video Games Day: four colored game buttons pulse under the logo */
@Composable
private fun BoxScope.GameButtons() {
    val t by loop(1600)
    Canvas(Modifier.matchParentSize()) {
        inLogoSpace {
            listOf(0xFFFF4081, 0xFFFFD740, 0xFF76FF03, 0xFF00E5FF).forEachIndexed { i, c ->
                val pu = (sin(t * 2 * PI.toFloat() - i * 1.2f) + 1) / 2
                val x = 114f + i * 24f
                halo(x, 82f, 10f + pu * 6f, Color(c), .5f * pu)
                dot(x, 82f, 5f, 5f, Color(c))
            }
        }
    }
}

/** Cat Day: a cat tail swishes out from behind the logo, and its ears peek over the top */
@Composable
private fun BoxScope.CatTail() {
    val t by loop(2900)
    Canvas(Modifier.matchParentSize()) {
        inLogoSpace {
            val c = Color(0xFFF0A35E)
            val sw = sin(t * 2 * PI).toFloat() * 10f
            drawPath(
                Path().apply {
                    moveTo(222f, 58f)
                    quadraticTo(254f, 62f, 250f + sw * .4f, 36f + sw)
                },
                c,
                style = Stroke(5f, cap = StrokeCap.Round),
            )
            drawPath(poly(82f, 27f, 88f, 13f, 96f, 27f), c)
            drawPath(poly(100f, 27f, 108f, 13f, 114f, 27f), c)
        }
    }
}

/** Dog Day: little paw prints walk across under the logo */
@Composable
private fun BoxScope.PawPrints() {
    val t by loop(3500)
    Canvas(Modifier.matchParentSize()) {
        inLogoSpace {
            val c = Color(0xFFC68642)
            for (k in 0 until 7) {
                val ph = frac(t - k / 7f)
                if (ph > .7f) continue
                val a = 1 - ph / .7f
                val x = 60f + k * 30f
                val y = if (k % 2 == 1) 80f else 86f
                dot(x, y, 4f, 3.5f, c, a)
                listOf(-4f, -1.5f, 1.5f, 4f).forEachIndexed { j, o -> dot(x + o, y - 4f - if (j % 3 != 0) 1.5f else 0f, 1.4f, 1.6f, c, a) }
            }
        }
    }
}

/** Pirate Day: a small black flag on the logo and waves underneath (the logo itself bobs, see [logoShimmer]) */
@Composable
private fun BoxScope.PirateFlag() {
    val t by loop(4000)
    val wave by loop(1000)
    Canvas(Modifier.matchParentSize()) {
        inLogoSpace {
            val b = sin(t * 2 * PI).toFloat() * 3f
            drawRect(Color(0xFFD8C9A0), Offset(222f, 10f + b), Size(2f, 18f))
            drawPath(
                Path().apply {
                    moveTo(224f, 10f + b)
                    for (k in 0..5) lineTo(224f + k * 3f, 10f + b + sin(wave * 2 * PI - k).toFloat() * 1.5f)
                    for (k in 5 downTo 0) lineTo(224f + k * 3f, 18f + b + sin(wave * 2 * PI - k).toFloat() * 1.5f)
                    close()
                },
                Color(0xFF111111),
            )
            for (k in 0 until 3) {
                drawPath(
                    Path().apply {
                        var x = 60f
                        while (x <= 240f) {
                            val y = 76f + k * 4f + sin(x * .08f + t * 12f + k) * 1.5f
                            if (x == 60f) moveTo(x, y) else lineTo(x, y)
                            x += 6f
                        }
                    },
                    Color(0xFF78B4FF),
                    alpha = .5f,
                    style = Stroke(1.5f),
                )
            }
        }
    }
}

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
