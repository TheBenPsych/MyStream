package com.github.damontecres.wholphin.ui.games

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import kotlin.random.Random

/**
 * Snack Catch: slide a popcorn bucket left and right to catch the snacks falling from the balcony.
 * Popcorn +1, candy +2, golden tickets +5. Spilled sodas cost a heart, and so does letting a snack hit the floor.
 */
class SnackCatch : MiniGame() {
    private enum class Kind { POPCORN, CANDY, TICKET, SODA }

    private class Drop(
        var x: Float,
        var y: Float,
        val kind: Kind,
        val speed: Float,
        val spin: Float,
    )

    private val drops = mutableListOf<Drop>()
    private var bucketX = 0f
    private var steer = 0
    private var lives = 3
    private var spawnIn = 0f
    private var time = 0f
    private var hurt = 0f

    private val u get() = height / 100f
    private val bucketW get() = (if (easy) 17f else 14f) * u
    private val bucketH get() = 11f * u
    private val bucketTop get() = height - bucketH - 4f * u
    private val steerSpeed get() = 95f * u

    /** Things fall faster and more often the longer you last */
    private val level get() = (time / 20f).coerceAtMost(4f)

    override fun reset() {
        drops.clear()
        score = 0
        lives = 3
        steer = 0
        time = 0f
        hurt = 0f
        spawnIn = 0.5f
        bucketX = width / 2f
    }

    override fun step(dt: Float) {
        time += dt
        if (hurt > 0f) hurt -= dt
        bucketX = (bucketX + steer * steerSpeed * dt).coerceIn(bucketW / 2f, width - bucketW / 2f)

        spawnIn -= dt
        if (spawnIn <= 0f) {
            val roll = Random.nextFloat()
            val sodaChance = if (easy) 0.12f else 0.16f + level * 0.04f
            val kind =
                when {
                    roll < 0.05f -> Kind.TICKET
                    roll < 0.05f + sodaChance -> Kind.SODA
                    roll < 0.35f + sodaChance -> Kind.CANDY
                    else -> Kind.POPCORN
                }
            val speed = (28f + level * 9f + Random.nextFloat() * 10f) * u * pace
            drops.add(Drop(4f * u + Random.nextFloat() * (width - 8f * u), -4f * u, kind, speed, (Random.nextFloat() - 0.5f) * 300f))
            spawnIn = ((1.0f - level * 0.14f) / pace).coerceAtLeast(0.35f) * (0.7f + Random.nextFloat() * 0.6f)
        }

        val left = bucketX - bucketW / 2f
        val right = bucketX + bucketW / 2f
        val iterator = drops.iterator()
        while (iterator.hasNext()) {
            val d = iterator.next()
            val oldY = d.y
            d.y += d.speed * dt
            // Caught: it crossed the bucket's rim while over the bucket
            if (oldY < bucketTop && d.y >= bucketTop && d.x in left..right) {
                iterator.remove()
                when (d.kind) {
                    Kind.SODA -> {
                        loseHeart(d.x, bucketTop, Color(0xFF8D5524))
                    }

                    else -> {
                        val points =
                            when (d.kind) {
                                Kind.TICKET -> 5
                                Kind.CANDY -> 2
                                else -> 1
                            }
                        score += points
                        sound(if (d.kind == Kind.TICKET) Sfx.COIN else Sfx.POP)
                        popup(d.x, bucketTop - 4f * u, "+$points")
                        burst(d.x, bucketTop, colorOf(d.kind), count = 6, speed = 20f, size = 0.7f)
                    }
                }
                if (lives <= 0) return
                continue
            }
            if (d.y > height + 4f * u) {
                iterator.remove()
                // A dropped snack costs a heart; a dropped soda is fine (you dodged it!)
                if (d.kind != Kind.SODA) {
                    loseHeart(d.x, height - 2f * u, colorOf(d.kind))
                    if (lives <= 0) return
                }
            }
        }
    }

    private fun loseHeart(
        x: Float,
        y: Float,
        color: Color,
    ) {
        lives--
        hurt = 0.5f
        burst(x, y, color, count = 12, speed = 35f)
        if (lives <= 0) {
            gameOver()
        } else {
            sound(Sfx.MISS)
            buzz = true
            shake(1.2f)
        }
    }

    private fun colorOf(kind: Kind) =
        when (kind) {
            Kind.POPCORN -> Color(0xFFFFF4D6)
            Kind.CANDY -> Color(0xFFFF4FA3)
            Kind.TICKET -> Color(0xFFFFC107)
            Kind.SODA -> Color(0xFF8D5524)
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
        // Theater seats in the dark, with the screen's glow on top
        drawRect(Brush.verticalGradient(listOf(Color(0xFF2B1036), Color(0xFF12081A), Color(0xFF07070A))))
        drawRect(Brush.verticalGradient(listOf(Color(0x55B3E5FC), Color.Transparent)), size = Size(width, height * 0.3f))
        // Rows of seat backs along the bottom
        val seatW = 9f * u
        var sx = -seatW / 2f
        while (sx < width) {
            drawRoundRect(Color(0xFF5A1020), Offset(sx, height - 6f * u), Size(seatW * 0.9f, 8f * u), CornerRadius(2f * u))
            sx += seatW
        }
        if (hurt > 0f) drawRect(Color(0xFFE50914).copy(alpha = hurt * 0.4f))

        drops.forEach { d ->
            rotate(d.spin * d.y / height, Offset(d.x, d.y)) { drawDrop(d) }
        }
        drawBucket()

        // Hearts in the top-left corner
        for (i in 0 until 3) {
            drawHeart(Offset(5f * u + i * 7f * u, 6f * u), 2.6f * u, if (i < lives) Color(0xFFE50914) else Color(0x44FFFFFF))
        }
    }

    private fun DrawScope.drawDrop(d: Drop) {
        val x = d.x
        val y = d.y
        when (d.kind) {
            Kind.POPCORN -> {
                val r = 1.8f * u
                drawCircle(Color(0xFFFFF4D6), r, Offset(x, y))
                drawCircle(Color(0xFFFFF4D6), r * 0.8f, Offset(x - r * 0.8f, y - r * 0.4f))
                drawCircle(Color(0xFFFFF4D6), r * 0.8f, Offset(x + r * 0.8f, y - r * 0.3f))
                drawCircle(Color(0xFFFFC94D), r * 0.45f, Offset(x, y + r * 0.5f))
            }

            Kind.CANDY -> {
                // A wrapped candy: round middle with twisted ends
                val r = 1.8f * u
                val left =
                    Path().apply {
                        moveTo(x - r, y)
                        lineTo(x - r * 2.2f, y - r)
                        lineTo(x - r * 2.2f, y + r)
                        close()
                    }
                val right =
                    Path().apply {
                        moveTo(x + r, y)
                        lineTo(x + r * 2.2f, y - r)
                        lineTo(x + r * 2.2f, y + r)
                        close()
                    }
                drawPath(left, Color(0xFFFF8AC6))
                drawPath(right, Color(0xFFFF8AC6))
                drawCircle(Color(0xFFFF4FA3), r, Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.6f), r * 0.3f, Offset(x - r * 0.35f, y - r * 0.35f))
            }

            Kind.TICKET -> {
                drawTicket(x, y, 2.4f * u)
            }

            Kind.SODA -> {
                // A tipped-over soda with a splash of fizz
                val w = 4f * u
                val h = 6f * u
                drawRoundRect(Color(0xFF1E88E5), Offset(x - w / 2f, y - h / 2f), Size(w, h), CornerRadius(w * 0.2f))
                drawRect(Color.White, Offset(x - w / 2f, y - h * 0.1f), Size(w, h * 0.2f))
                drawRect(Color(0xFFEEEEEE), Offset(x - w * 0.1f, y - h / 2f - 2.5f * u), Size(w * 0.2f, 2.5f * u))
                drawCircle(Color(0xAA8D5524), 1.1f * u, Offset(x - w * 0.7f, y + h * 0.55f))
                drawCircle(Color(0xAA8D5524), 0.8f * u, Offset(x + w * 0.7f, y + h * 0.6f))
            }
        }
    }

    private fun DrawScope.drawBucket() {
        val w = bucketW
        val h = bucketH
        val top = bucketTop
        val left = bucketX - w / 2f
        // Popcorn piled on top
        val r = 2f * u
        var px = left + r
        while (px < left + w - r * 0.5f) {
            drawCircle(Color(0xFFFFF4D6), r, Offset(px, top + r * 0.3f))
            px += r * 1.4f
        }
        // The striped bucket, a little narrower at the bottom
        val inset = w * 0.12f
        val body =
            Path().apply {
                moveTo(left, top + r * 0.5f)
                lineTo(left + w, top + r * 0.5f)
                lineTo(left + w - inset, top + h)
                lineTo(left + inset, top + h)
                close()
            }
        drawPath(body, Color.White)
        val stripes = 5
        for (i in 0 until stripes step 2) {
            val a = i / stripes.toFloat()
            val b = (i + 1) / stripes.toFloat()
            val stripe =
                Path().apply {
                    moveTo(left + w * a, top + r * 0.5f)
                    lineTo(left + w * b, top + r * 0.5f)
                    lineTo(left + inset + (w - 2 * inset) * b, top + h)
                    lineTo(left + inset + (w - 2 * inset) * a, top + h)
                    close()
                }
            drawPath(stripe, Color(0xFFE50914))
        }
    }

    private fun DrawScope.drawHeart(
        c: Offset,
        r: Float,
        color: Color,
    ) {
        drawCircle(color, r * 0.55f, Offset(c.x - r * 0.5f, c.y - r * 0.2f))
        drawCircle(color, r * 0.55f, Offset(c.x + r * 0.5f, c.y - r * 0.2f))
        val p =
            Path().apply {
                moveTo(c.x - r * 1.03f, c.y)
                lineTo(c.x + r * 1.03f, c.y)
                lineTo(c.x, c.y + r * 1.1f)
                close()
            }
        drawPath(p, color)
    }
}
