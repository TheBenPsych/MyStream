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
 * Golden tickets in the gaps are worth +3, and after a while some strips start sliding up and down.
 *
 * Every so often a "tight squeeze" section comes along: a few strips in a row with smaller gaps
 * (drawn with a red glow so you can see it coming). The gaps in a squeeze stay close to each other
 * in height and never slide, so it's hard but always doable. Squeezes get a little longer and
 * tighter the further you get, then it goes back to normal for a breather.
 */
class ReelRush : MiniGame() {
    private class Strip(
        var x: Float,
        val baseY: Float,
        /** How far the gap slides up and down (0 = it stays still) */
        val swing: Float,
        val wobble: Float,
        /** The size of this strip's gap */
        val gap: Float,
        /** Part of a tight squeeze section */
        val tight: Boolean = false,
        var passed: Boolean = false,
        var ticket: Boolean = false,
    ) {
        var gapY = baseY
    }

    private val strips = mutableListOf<Strip>()
    private var by = 0f
    private var vy = 0f
    private var spawnIn = 0f
    private var time = 0f
    private var bonus = 0
    private var passedCount = 0
    private val trail = ArrayDeque<Offset>()

    /** How many strips are left in the current normal stretch or squeeze section */
    private var sectionLeft = 0
    private var inSqueeze = false
    private var squeezesDone = 0
    private var lastGapY = 0f

    private val u get() = height / 100f
    private val bx get() = width * 0.25f
    private val bSize get() = 7f * u
    private val gravity get() = 210f * u * pace
    private val flap get() = 62f * u * kotlin.math.sqrt(pace)
    private val stripW get() = 11f * u
    private val gap get() = (34f - (passedCount / 5f).coerceAtMost(8f) + if (easy) 8f else 0f) * u

    /** Gaps in a squeeze: starts at 25 and shrinks to 20 over the first several squeezes (Kids get more room) */
    private val squeezeGap get() = (25f - (squeezesDone * 0.7f).coerceAtMost(5f) + if (easy) 7f else 0f) * u
    private val speed get() = (38f + (passedCount * 0.6f).coerceAtMost(22f)) * u * pace

    override fun reset() {
        strips.clear()
        score = 0
        bonus = 0
        passedCount = 0
        time = 0f
        trail.clear()
        by = height / 2f
        vy = 0f
        spawnIn = 0.6f
        inSqueeze = false
        squeezesDone = 0
        sectionLeft = 10
        lastGapY = height / 2f
    }

    override fun step(dt: Float) {
        time += dt
        vy += gravity * dt
        by += vy * dt
        spawnIn -= dt
        if (spawnIn <= 0f) {
            if (sectionLeft <= 0) {
                if (inSqueeze) {
                    // Back to normal for a breather
                    inSqueeze = false
                    squeezesDone++
                    sectionLeft = 7 + Random.nextInt(5)
                } else {
                    inSqueeze = true
                    sectionLeft = 4 + (squeezesDone / 2).coerceAtMost(4) + Random.nextInt(2)
                    popup(width * 0.6f, 14f * u, "Tight squeeze!", Color(0xFFFF5252))
                }
            }
            sectionLeft--
            val strip =
                if (inSqueeze) {
                    // Tight gaps that stay near the last one, so you only need small hops
                    val g = squeezeGap
                    val margin = g / 2f + 8f * u
                    val y = (lastGapY + (Random.nextFloat() * 2f - 1f) * 14f * u).coerceIn(margin, height - margin)
                    Strip(width + stripW, y, 0f, 0f, g, tight = true)
                } else {
                    val g = gap
                    val swing = if (!easy && passedCount >= 15 && Random.nextFloat() < 0.45f) (6f + Random.nextFloat() * 8f) * u else 0f
                    val margin = g / 2f + 8f * u + swing
                    Strip(width + stripW, margin + Random.nextFloat() * (height - 2 * margin), swing, Random.nextFloat() * 6f, g)
                }
            lastGapY = strip.baseY
            strip.ticket = Random.nextFloat() < (if (strip.tight) 0.4f else 0.25f)
            strips.add(strip)
            spawnIn = (width * 0.42f) / speed
        }
        strips.forEach {
            it.x -= speed * dt
            if (it.swing > 0f) it.gapY = it.baseY + kotlin.math.sin(time * 1.6f + it.wobble) * it.swing
        }
        strips.removeAll { it.x < -stripW }
        trail.addFirst(Offset(bx, by))
        while (trail.size > 8) trail.removeLast()
        trail.forEachIndexed { i, o -> trail[i] = Offset(o.x - speed * dt, o.y) }

        val half = bSize / 2f
        if (by - half < 0f || by + half > height) {
            crash()
            return
        }
        strips.forEach { s ->
            if (!s.passed && s.x + stripW < bx - half) {
                s.passed = true
                passedCount++
                sound(Sfx.POINT)
            }
            if (s.ticket && kotlin.math.abs(s.x + stripW / 2f - bx) < half + 2f * u && kotlin.math.abs(s.gapY - by) < half + 2f * u) {
                s.ticket = false
                bonus += 3
                sound(Sfx.COIN)
                popup(bx, by - bSize, "+3")
                burst(bx, by, Color(0xFFFFD54F), count = 10, speed = 30f)
            }
            val overlapsX = bx + half * 0.8f > s.x && bx - half * 0.8f < s.x + stripW
            val inGap = by - half * 0.8f > s.gapY - s.gap / 2f && by + half * 0.8f < s.gapY + s.gap / 2f
            if (overlapsX && !inGap) {
                crash()
                return
            }
        }
        score = passedCount + bonus
    }

    private fun crash() {
        burst(bx, by, Color(0xFFE50914), count = 18, speed = 50f, size = 1.2f)
        burst(bx, by, Color.White, count = 8, speed = 35f)
        gameOver()
    }

    override fun input(
        input: GameInput,
        down: Boolean,
    ) {
        if (down && input == GameInput.ACTION) {
            vy = -flap
            sound(Sfx.FLAP)
            burst(bx - bSize / 2f, by + bSize / 3f, Color.White.copy(alpha = 0.7f), count = 3, speed = 12f, size = 0.6f)
        }
    }

    override fun setupPreview() {
        strips.add(Strip(width * 0.45f, height * 0.42f, 0f, 0f, gap))
        strips.add(Strip(width * 0.72f, height * 0.6f, 0f, 0f, gap).apply { ticket = true })
        strips.add(Strip(width * 0.99f, height * 0.35f, 0f, 0f, gap))
        by = height * 0.45f
        vy = -flap * 0.5f
        for (i in 0 until 8) trail.addLast(Offset(bx - i * 2.5f * u, by + i * i * 0.15f * u))
    }

    override fun DrawScope.draw(textMeasurer: TextMeasurer) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0D1B2A), Color(0xFF1B263B), Color(0xFF3A1F2B))))
        strips.forEach { s ->
            if (s.tight) {
                // A red glow on squeeze strips so you can see a tight section coming
                drawRect(Color(0x55E50914), Offset(s.x - 1.2f * u, 0f), Size(stripW + 2.4f * u, s.gapY - s.gap / 2f))
                drawRect(Color(0x55E50914), Offset(s.x - 1.2f * u, s.gapY + s.gap / 2f), Size(stripW + 2.4f * u, height - s.gapY - s.gap / 2f))
            }
            drawFilm(s.x, 0f, s.gapY - s.gap / 2f)
            drawFilm(s.x, s.gapY + s.gap / 2f, height)
            if (s.ticket) drawTicket(s.x + stripW / 2f, s.gapY, 2.4f * u)
        }
        // A fading red trail behind the B
        val half0 = bSize / 2f
        trail.forEachIndexed { i, o ->
            if (i == 0) return@forEachIndexed
            val k = 1f - i / trail.size.toFloat()
            val r = half0 * 0.7f * k
            drawRoundRect(Color(0xFFE50914).copy(alpha = 0.35f * k), Offset(o.x - r, o.y - r), Size(r * 2, r * 2), CornerRadius(r * 0.3f))
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
