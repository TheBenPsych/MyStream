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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.LogoEffect
import kotlin.math.PI
import kotlin.math.cos
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

        else -> {}
    }
}

/**
 * Tints the logo green with a gold shine that sweeps across it every few seconds
 */
@Composable
fun Modifier.logoShimmer(effect: LogoEffect): Modifier {
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
