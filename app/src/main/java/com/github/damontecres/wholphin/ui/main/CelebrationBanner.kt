package com.github.damontecres.wholphin.ui.main

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.Celebration
import kotlin.random.Random

/**
 * "Happy Birthday" or holiday banner at the top of Home, with falling confetti
 */
@Composable
fun CelebrationBanner(
    celebration: Celebration,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(shape)
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFFE50914), Color(0xFF8E0AC8), Color(0xFFE50914))),
                ),
    ) {
        if (celebration.confetti) {
            Confetti(Modifier.matchParentSize())
        }
        Text(
            text = celebration.text,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

private val ConfettiColors =
    listOf(Color(0xFFFFC400), Color(0xFF00E5FF), Color(0xFF76FF03), Color.White, Color(0xFFFF4081))

private class Piece(
    val x: Float,
    val speed: Float,
    val phase: Float,
    val size: Float,
    val color: Color,
    val spin: Float,
)

@Composable
private fun Confetti(modifier: Modifier = Modifier) {
    val pieces =
        remember {
            val random = Random(7)
            List(60) {
                Piece(
                    x = random.nextFloat(),
                    speed = .5f + random.nextFloat(),
                    phase = random.nextFloat(),
                    size = 4f + random.nextFloat() * 6f,
                    color = ConfettiColors[random.nextInt(ConfettiColors.size)],
                    spin = random.nextFloat() * 720f,
                )
            }
        }
    val time by rememberInfiniteTransition(label = "confetti").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = LinearEasing)),
        label = "confetti_time",
    )
    Canvas(modifier) {
        pieces.forEach { p ->
            val progress = (time * p.speed + p.phase) % 1f
            val y = progress * (size.height + 20f) - 10f
            val x = p.x * size.width + kotlin.math.sin((progress + p.phase) * 6.28f) * 12f
            rotate(degrees = p.spin * progress, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color,
                    topLeft = Offset(x, y),
                    size = Size(p.size, p.size * .6f),
                )
            }
        }
    }
}
