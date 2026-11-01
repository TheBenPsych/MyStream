package com.github.damontecres.wholphin.ui.games

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import kotlin.math.abs

/**
 * Film Can Stack: a film can slides back and forth above the tower. Press the button to drop it.
 * Whatever hangs over the edge gets cut off, so the cans get narrower. Drop it perfectly for a bonus
 * (and three perfect drops in a row make the can a little wider again).
 */
class FilmCanStack : MiniGame() {
    private class Can(
        val x: Float,
        val w: Float,
        val color: Color,
    )

    private class Chip(
        var x: Float,
        var y: Float,
        val w: Float,
        var vy: Float,
        val color: Color,
    )

    private val tower = mutableListOf<Can>()
    private val chips = mutableListOf<Chip>()
    private var movingX = 0f
    private var movingW = 0f
    private var dir = 1f
    private var camera = 0f
    private var perfectRun = 0
    private var flash = 0f

    private val u get() = height / 100f
    private val canH get() = 7f * u
    private val baseY get() = height - 8f * u
    private val startW get() = width * 0.32f
    private val speed get() = (60f + tower.size * 3.5f).coerceAtMost(150f) * u * pace

    private val labels =
        listOf(
            Color(0xFFE50914),
            Color(0xFFFFC107),
            Color(0xFF29B6F6),
            Color(0xFF66BB6A),
            Color(0xFFAB47BC),
            Color(0xFFFF7043),
        )

    override fun reset() {
        tower.clear()
        chips.clear()
        score = 0
        camera = 0f
        perfectRun = 0
        flash = 0f
        // The first can sits on the projector table
        tower.add(Can(width / 2f - startW / 2f, startW, labels[0]))
        nextCan()
    }

    private fun nextCan() {
        movingW = tower.last().w
        dir = if (tower.size % 2 == 0) 1f else -1f
        movingX = if (dir > 0f) -movingW else width
    }

    /** Where a tower level is drawn on screen (level 0 = the bottom can) */
    private fun levelY(level: Int) = baseY - (level + 1) * canH + camera

    override fun step(dt: Float) {
        // Slide back and forth across the screen
        movingX += dir * speed * dt
        if (movingX > width - movingW * 0.2f) {
            movingX = width - movingW * 0.2f
            dir = -1f
        }
        if (movingX < -movingW * 0.8f) {
            movingX = -movingW * 0.8f
            dir = 1f
        }
        // Keep the top of the tower around the middle of the screen
        val target = ((tower.size - 7) * canH).coerceAtLeast(0f)
        val before = camera
        camera += (target - camera) * (dt * 5f).coerceAtMost(1f)
        shiftEffects(camera - before)
        chips.forEach {
            it.vy += 160f * u * dt
            it.y += it.vy * dt + (camera - before)
        }
        chips.removeAll { it.y > height + 10f * u }
        if (flash > 0f) flash -= dt
    }

    private fun drop() {
        val top = tower.last()
        val color = labels[tower.size % labels.size]
        val y = levelY(tower.size)
        val left = maxOf(movingX, top.x)
        val right = minOf(movingX + movingW, top.x + top.w)
        val overlap = right - left
        if (overlap <= 0f) {
            // Missed the tower completely: the can tumbles away
            chips.add(Chip(movingX, y, movingW, 0f, color))
            gameOver()
            return
        }
        val perfectZone = if (easy) 3f * u else 1.6f * u
        if (abs(movingX - top.x) <= perfectZone) {
            perfectRun++
            val grow = if (perfectRun % 3 == 0) minOf(4f * u, startW - top.w) else 0f
            tower.add(Can(top.x - grow / 2f, top.w + grow, color))
            score += 2
            flash = 0.3f
            sound(Sfx.COIN)
            popup(top.x + top.w / 2f, y - 3f * u, if (grow > 0f) "Perfect! Wider!" else "Perfect! +2")
            burst(top.x + top.w / 2f, y + canH / 2f, Color.White, count = 14, speed = 35f)
        } else {
            perfectRun = 0
            // Cut off the bit hanging over the edge; it falls away
            if (movingX < top.x) {
                chips.add(Chip(movingX, y, top.x - movingX, 0f, color))
            } else {
                chips.add(Chip(right, y, movingX + movingW - right, 0f, color))
            }
            tower.add(Can(left, overlap, color))
            score += 1
            sound(Sfx.THUD)
            burst(left + overlap / 2f, y + canH, Color(0xFFB0B6C0), count = 5, speed = 15f, size = 0.6f)
        }
        nextCan()
    }

    override fun input(
        input: GameInput,
        down: Boolean,
    ) {
        if (down && input == GameInput.ACTION) drop()
    }

    override fun setupPreview() {
        // A tower of five cans that gets a little narrower, and the next one sliding in
        var x = tower.last().x
        var w = tower.last().w
        for (i in 1..5) {
            val cut = if (i % 2 == 0) 0f else 3f * u
            x += if (i % 3 == 0) 0f else cut
            w -= cut
            tower.add(Can(x, w, labels[i % labels.size]))
        }
        movingW = w
        movingX = width * 0.62f
    }

    override fun DrawScope.draw(textMeasurer: TextMeasurer) {
        // A projection booth: warm dark walls, and a light beam from the projector
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1C1424), Color(0xFF0E0A12))))
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, Color(0x22FFF3C4), Color.Transparent)),
            Offset(width * 0.65f, 0f),
            Size(width * 0.35f, height),
        )
        if (flash > 0f) drawRect(Color.White.copy(alpha = flash * 0.25f))
        // The table the tower stands on
        drawRect(Color(0xFF4E342E), Offset(width * 0.2f, baseY + camera), Size(width * 0.6f, 3f * u))
        drawRect(Color(0xFF3E2723), Offset(width * 0.25f, baseY + camera + 3f * u), Size(2f * u, height))
        drawRect(Color(0xFF3E2723), Offset(width * 0.75f - 2f * u, baseY + camera + 3f * u), Size(2f * u, height))

        tower.forEachIndexed { i, can ->
            val y = levelY(i)
            if (y < height && y > -canH) drawCan(can.x, y, can.w, can.color)
        }
        chips.forEach { drawCan(it.x, it.y, it.w, it.color) }
        if (phase != GamePhase.OVER) {
            drawCan(movingX, levelY(tower.size) - canH * 0.4f, movingW, labels[tower.size % labels.size])
        }
    }

    /** A film can seen from the side: a metal tin with a colored label band */
    private fun DrawScope.drawCan(
        x: Float,
        y: Float,
        w: Float,
        label: Color,
    ) {
        if (w <= 0f) return
        val h = canH * 0.94f
        drawRoundRect(Color(0xFFB0B6C0), Offset(x, y), Size(w, h), CornerRadius(h * 0.25f))
        drawRect(Color(0xFF8A9099), Offset(x, y + h * 0.18f), Size(w, h * 0.08f))
        drawRect(label, Offset(x, y + h * 0.38f), Size(w, h * 0.3f))
        drawRect(Color.White.copy(alpha = 0.25f), Offset(x, y + h * 0.04f), Size(w, h * 0.08f))
    }
}
