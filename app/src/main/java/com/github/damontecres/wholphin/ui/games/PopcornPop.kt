package com.github.damontecres.wholphin.ui.games

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import kotlin.random.Random

/**
 * Popcorn Pop: a popcorn kernel bounces up a tower of soda cups and film reels.
 * Steer left and right; the kernel bounces by itself. Reels slide sideways, butter pats launch you higher.
 */
class PopcornPop : MiniGame() {
    private enum class Kind { CUP, REEL, BUTTER }

    private class Ledge(
        var x: Float,
        var y: Float,
        val kind: Kind,
        var vx: Float,
    )

    private val ledges = mutableListOf<Ledge>()
    private var px = 0f
    private var py = 0f
    private var vy = 0f
    private var steer = 0
    private var climbed = 0f
    private var highestLedge = 0f

    // Sizes scale with the screen height so it plays the same on every TV and phone
    private val u get() = height / 100f
    private val ledgeW get() = 16f * u
    private val ledgeH get() = 3f * u
    private val radius get() = 3.2f * u
    private val gravity get() = 140f * u
    private val bounce get() = 78f * u
    private val steerSpeed get() = 70f * u

    override fun reset() {
        ledges.clear()
        score = 0
        climbed = 0f
        steer = 0
        px = width / 2f
        py = height * 0.8f
        vy = -bounce
        // A starting cup right under the kernel, then a tower above it
        ledges.add(Ledge(width / 2f - ledgeW / 2f, height * 0.9f, Kind.CUP, 0f))
        highestLedge = height * 0.9f
        while (highestLedge > -height) addLedge()
    }

    private fun addLedge() {
        val gap = (9f + Random.nextFloat() * (8f + (score / 40f).coerceAtMost(10f))) * u
        highestLedge -= gap
        val roll = Random.nextFloat()
        val kind =
            when {
                roll < 0.07f -> Kind.BUTTER
                roll < 0.07f + (0.1f + score / 400f).coerceAtMost(0.35f) -> Kind.REEL
                else -> Kind.CUP
            }
        val vx = if (kind == Kind.REEL) (if (Random.nextBoolean()) 1 else -1) * (18f + Random.nextFloat() * 18f) * u else 0f
        ledges.add(Ledge(Random.nextFloat() * (width - ledgeW), highestLedge, kind, vx))
    }

    override fun step(dt: Float) {
        // Sideways, wrapping around the screen edges
        px += steer * steerSpeed * dt
        if (px < -radius) px += width + 2 * radius
        if (px > width + radius) px -= width + 2 * radius

        val oldBottom = py + radius
        vy += gravity * dt
        py += vy * dt
        val newBottom = py + radius

        ledges.forEach { l ->
            if (l.vx != 0f) {
                l.x += l.vx * dt
                if (l.x < 0f || l.x > width - ledgeW) l.vx = -l.vx
            }
        }

        // Land on a ledge only while falling
        if (vy > 0f) {
            ledges
                .firstOrNull { l ->
                    oldBottom <= l.y && newBottom >= l.y && px in (l.x - radius * 0.6f)..(l.x + ledgeW + radius * 0.6f)
                }?.let { l ->
                    py = l.y - radius
                    vy = if (l.kind == Kind.BUTTER) -bounce * 1.6f else -bounce
                }
        }

        // Move the camera up when the kernel climbs past the top part of the screen
        val line = height * 0.4f
        if (py < line) {
            val shift = line - py
            py = line
            ledges.forEach { it.y += shift }
            highestLedge += shift
            climbed += shift
            score = (climbed / (10f * u)).toInt()
        }
        ledges.removeAll { it.y > height + ledgeH }
        while (highestLedge > -height * 0.2f) addLedge()

        if (py - radius > height) gameOver()
    }

    override fun input(
        input: GameInput,
        down: Boolean,
    ) {
        when (input) {
            GameInput.LEFT -> {
                steer =
                    if (down) {
                        -1
                    } else if (steer == -1) {
                        0
                    } else {
                        steer
                    }
            }

            GameInput.RIGHT -> {
                steer =
                    if (down) {
                        1
                    } else if (steer == 1) {
                        0
                    } else {
                        steer
                    }
            }

            GameInput.ACTION -> {}
        }
    }

    override fun DrawScope.draw(textMeasurer: TextMeasurer) {
        // Dark theater with a soft spotlight
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1A0B12), Color(0xFF07070A))))
        drawCircle(
            Brush.radialGradient(listOf(Color(0x33FFD27A), Color.Transparent), center = Offset(width / 2f, 0f), radius = height),
            radius = height,
            center = Offset(width / 2f, 0f),
        )
        ledges.forEach { l -> drawLedge(l) }
        // The kernel: a fluffy popped piece
        val c = Offset(px, py)
        drawCircle(Color(0xFFFFF4D6), radius, c)
        drawCircle(Color(0xFFFFF4D6), radius * 0.7f, c + Offset(-radius * 0.7f, -radius * 0.3f))
        drawCircle(Color(0xFFFFF4D6), radius * 0.7f, c + Offset(radius * 0.7f, -radius * 0.4f))
        drawCircle(Color(0xFFFFC94D), radius * 0.45f, c + Offset(0f, radius * 0.45f))
    }

    private fun DrawScope.drawLedge(l: Ledge) {
        val w = ledgeW
        val h = ledgeH
        when (l.kind) {
            Kind.CUP -> {
                drawRoundRect(Color(0xFFE50914), Offset(l.x, l.y), Size(w, h), CornerRadius(h / 3f))
                val stripe = w / 8f
                for (i in 1 until 8 step 2) {
                    drawRect(Color.White, Offset(l.x + i * stripe, l.y), Size(stripe * 0.6f, h))
                }
            }

            Kind.REEL -> {
                drawRoundRect(Color(0xFF8A8F99), Offset(l.x, l.y), Size(w, h), CornerRadius(h / 2f))
                drawCircle(Color(0xFF3A3D44), h * 0.9f, Offset(l.x + h, l.y + h / 2f))
                drawCircle(Color(0xFF3A3D44), h * 0.9f, Offset(l.x + w - h, l.y + h / 2f))
                drawCircle(Color(0xFFB8BCC4), h * 0.3f, Offset(l.x + h, l.y + h / 2f))
                drawCircle(Color(0xFFB8BCC4), h * 0.3f, Offset(l.x + w - h, l.y + h / 2f))
            }

            Kind.BUTTER -> {
                drawRoundRect(Color(0xFFFFD54F), Offset(l.x, l.y), Size(w, h), CornerRadius(h / 2f))
                drawRoundRect(Color(0xFFFFF59D), Offset(l.x + w * 0.15f, l.y + h * 0.2f), Size(w * 0.5f, h * 0.3f), CornerRadius(h / 4f))
            }
        }
    }
}
