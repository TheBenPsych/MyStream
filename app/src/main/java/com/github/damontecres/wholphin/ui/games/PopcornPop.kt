package com.github.damontecres.wholphin.ui.games

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextMeasurer
import kotlin.random.Random

/**
 * Popcorn Pop: a popcorn kernel bounces up a tower of soda cups and film reels.
 * Steer left and right; the kernel bounces by itself. Reels slide sideways, butter pats launch you higher,
 * glowing phones (someone's texting in the theater!) drop away after one bounce, and tickets are worth +5.
 */
class PopcornPop : MiniGame() {
    private enum class Kind { CUP, REEL, BUTTER, PHONE }

    private class Ledge(
        var x: Float,
        var y: Float,
        val kind: Kind,
        var vx: Float,
        var falling: Boolean = false,
    )

    private class Ticket(
        var x: Float,
        var y: Float,
    )

    private val ledges = mutableListOf<Ledge>()
    private val tickets = mutableListOf<Ticket>()
    private var bonus = 0
    private var squash = 0f
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
    private val gravity get() = 140f * u * pace * pace
    private val bounce get() = 78f * u * pace
    private val steerSpeed get() = 70f * u * pace

    override fun reset() {
        ledges.clear()
        tickets.clear()
        score = 0
        bonus = 0
        squash = 0f
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
                !easy && score > 50 && roll < 0.07f + (0.04f + score / 1500f).coerceAtMost(0.15f) -> Kind.PHONE
                roll < 0.07f + (0.1f + score / 400f).coerceAtMost(if (easy) 0.2f else 0.35f) -> Kind.REEL
                else -> Kind.CUP
            }
        val vx = if (kind == Kind.REEL) (if (Random.nextBoolean()) 1 else -1) * (18f + Random.nextFloat() * 18f) * u * pace else 0f
        val x = Random.nextFloat() * (width - ledgeW)
        ledges.add(Ledge(x, highestLedge, kind, vx))
        // Now and then a ticket floats above a still ledge
        if (kind != Kind.REEL && Random.nextFloat() < 0.12f) tickets.add(Ticket(x + ledgeW / 2f, highestLedge - 9f * u))
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

        if (squash > 0f) squash -= dt
        ledges.forEach { l ->
            if (l.falling) {
                l.y += 60f * u * dt
            } else if (l.vx != 0f) {
                l.x += l.vx * dt
                if (l.x < 0f || l.x > width - ledgeW) l.vx = -l.vx
            }
        }

        // Land on a ledge only while falling
        if (vy > 0f) {
            ledges
                .firstOrNull { l ->
                    !l.falling && oldBottom <= l.y && newBottom >= l.y && px in (l.x - radius * 0.6f)..(l.x + ledgeW + radius * 0.6f)
                }?.let { l ->
                    py = l.y - radius
                    squash = 0.12f
                    if (l.kind == Kind.BUTTER) {
                        vy = -bounce * 1.6f
                        sound(Sfx.BOOST)
                        burst(px, l.y, Color(0xFFFFE082), count = 14, speed = 40f)
                    } else {
                        vy = -bounce
                        sound(Sfx.POP)
                        burst(px, l.y, Color(0xFFFFF4D6), count = 6, speed = 22f, size = 0.7f)
                    }
                    if (l.kind == Kind.PHONE) l.falling = true
                }
        }

        // Grab tickets for bonus points
        tickets.removeAll { t ->
            val hit = kotlin.math.abs(t.x - px) < radius + 3f * u && kotlin.math.abs(t.y - py) < radius + 2.5f * u
            if (hit) {
                bonus += 5
                sound(Sfx.COIN)
                popup(t.x, t.y, "+5")
                burst(t.x, t.y, Color(0xFFFFD54F), count = 10, speed = 30f)
            }
            hit
        }

        // Move the camera up when the kernel climbs past the top part of the screen
        val line = height * 0.4f
        if (py < line) {
            val shift = line - py
            py = line
            ledges.forEach { it.y += shift }
            tickets.forEach { it.y += shift }
            shiftEffects(shift)
            highestLedge += shift
            climbed += shift
        }
        score = (climbed / (10f * u)).toInt() + bonus
        ledges.removeAll { it.y > height + ledgeH }
        tickets.removeAll { it.y > height + ledgeH }
        while (highestLedge > -height * 0.2f) addLedge()

        if (py - radius > height) gameOver()
    }

    override fun releaseAll() {
        steer = 0
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

            else -> {}
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
        tickets.forEach { t -> drawTicket(t.x, t.y, 2.6f * u) }
        // The kernel: a fluffy popped piece that squashes a little when it lands
        val c = Offset(px, py)
        val sq = (squash / 0.12f).coerceIn(0f, 1f) * 0.25f
        scale(1f + sq, 1f - sq, c) {
            drawCircle(Color(0xFFFFF4D6), radius, c)
            drawCircle(Color(0xFFFFF4D6), radius * 0.7f, c + Offset(-radius * 0.7f, -radius * 0.3f))
            drawCircle(Color(0xFFFFF4D6), radius * 0.7f, c + Offset(radius * 0.7f, -radius * 0.4f))
            drawCircle(Color(0xFFFFC94D), radius * 0.45f, c + Offset(0f, radius * 0.45f))
        }
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

            Kind.PHONE -> {
                // A phone lit up in the dark theater
                drawRoundRect(Color(0xFF22252B), Offset(l.x, l.y), Size(w, h), CornerRadius(h / 3f))
                drawRoundRect(
                    Color(0xFF7FD3FF),
                    Offset(l.x + h * 0.4f, l.y + h * 0.25f),
                    Size(w - h * 0.8f, h * 0.5f),
                    CornerRadius(h / 5f),
                )
                drawCircle(Color(0x337FD3FF), w * 0.6f, Offset(l.x + w / 2f, l.y + h / 2f))
            }

            Kind.BUTTER -> {
                drawRoundRect(Color(0xFFFFD54F), Offset(l.x, l.y), Size(w, h), CornerRadius(h / 2f))
                drawRoundRect(Color(0xFFFFF59D), Offset(l.x + w * 0.15f, l.y + h * 0.2f), Size(w * 0.5f, h * 0.3f), CornerRadius(h / 4f))
            }
        }
    }
}

/** A little golden movie ticket, centered on x, y; [s] is about half its height */
internal fun DrawScope.drawTicket(
    x: Float,
    y: Float,
    s: Float,
) {
    val w = s * 3f
    val h = s * 1.8f
    drawRoundRect(Color(0xFFFFC107), Offset(x - w / 2f, y - h / 2f), Size(w, h), CornerRadius(s * 0.3f))
    drawCircle(Color(0xFF07070A), s * 0.35f, Offset(x - w / 2f, y))
    drawCircle(Color(0xFF07070A), s * 0.35f, Offset(x + w / 2f, y))
    drawRect(Color(0xFFFF8F00), Offset(x - w * 0.2f, y - h * 0.3f), Size(w * 0.04f, h * 0.6f))
    drawRect(Color(0xFFFFF8E1), Offset(x - w * 0.05f, y - h * 0.15f), Size(w * 0.3f, h * 0.1f))
    drawRect(Color(0xFFFFF8E1), Offset(x - w * 0.05f, y + h * 0.05f), Size(w * 0.22f, h * 0.1f))
}
