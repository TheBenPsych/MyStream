package com.github.damontecres.wholphin.ui.games

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/**
 * Reel Rush: the Benflix "B" flies through gaps between strips of film. One button flaps.
 */
class ReelRush : MiniGame() {
    private class Strip(
        var x: Float,
        val gapY: Float,
        var passed: Boolean = false,
    )

    private val strips = mutableListOf<Strip>()
    private var by = 0f
    private var vy = 0f
    private var spawnIn = 0f

    private val u get() = height / 100f
    private val bx get() = width * 0.25f
    private val bSize get() = 7f * u
    private val gravity get() = 210f * u
    private val flap get() = 62f * u
    private val stripW get() = 11f * u
    private val gap get() = (34f - (score / 5f).coerceAtMost(8f)) * u
    private val speed get() = (38f + (score * 0.6f).coerceAtMost(22f)) * u

    override fun reset() {
        strips.clear()
        score = 0
        by = height / 2f
        vy = 0f
        spawnIn = 0.6f
    }

    override fun step(dt: Float) {
        vy += gravity * dt
        by += vy * dt
        spawnIn -= dt
        if (spawnIn <= 0f) {
            val margin = gap / 2f + 8f * u
            strips.add(Strip(width + stripW, margin + Random.nextFloat() * (height - 2 * margin)))
            spawnIn = (width * 0.42f) / speed
        }
        strips.forEach { it.x -= speed * dt }
        strips.removeAll { it.x < -stripW }

        val half = bSize / 2f
        if (by - half < 0f || by + half > height) {
            gameOver()
            return
        }
        strips.forEach { s ->
            if (!s.passed && s.x + stripW < bx - half) {
                s.passed = true
                score++
            }
            val overlapsX = bx + half * 0.8f > s.x && bx - half * 0.8f < s.x + stripW
            val inGap = by - half * 0.8f > s.gapY - gap / 2f && by + half * 0.8f < s.gapY + gap / 2f
            if (overlapsX && !inGap) gameOver()
        }
    }

    override fun input(
        input: GameInput,
        down: Boolean,
    ) {
        if (down && input == GameInput.ACTION) vy = -flap
    }

    override fun DrawScope.draw(textMeasurer: TextMeasurer) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0D1B2A), Color(0xFF1B263B), Color(0xFF3A1F2B))))
        strips.forEach { s ->
            drawFilm(s.x, 0f, s.gapY - gap / 2f)
            drawFilm(s.x, s.gapY + gap / 2f, height)
        }
        // The B, tilting with its speed
        val half = bSize / 2f
        val tilt = (vy / flap * 25f).coerceIn(-25f, 60f)
        rotate(tilt, Offset(bx, by)) {
            drawRoundRect(Color(0xFFE50914), Offset(bx - half, by - half), Size(bSize, bSize), CornerRadius(bSize * 0.18f))
            val layout =
                textMeasurer.measure(
                    "B",
                    TextStyle(color = Color.White, fontSize = (bSize * 0.62f / density).sp, fontWeight = FontWeight.Black),
                )
            drawText(layout, topLeft = Offset(bx - layout.size.width / 2f, by - layout.size.height / 2f))
        }
    }

    private fun DrawScope.drawFilm(
        x: Float,
        top: Float,
        bottom: Float,
    ) {
        if (bottom <= top) return
        drawRect(Color(0xFF16161A), Offset(x, top), Size(stripW, bottom - top))
        val hole = stripW * 0.14f
        var y = top + hole
        while (y < bottom - hole) {
            drawRect(Color(0xFFD9D9D9), Offset(x + hole * 0.6f, y), Size(hole, hole))
            drawRect(Color(0xFFD9D9D9), Offset(x + stripW - hole * 1.6f, y), Size(hole, hole))
            y += hole * 2.4f
        }
        drawRect(Color(0xFF2E4057), Offset(x + hole * 2.4f, top), Size(stripW - hole * 4.8f, bottom - top))
    }
}
