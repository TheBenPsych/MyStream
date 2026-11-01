package com.github.damontecres.wholphin.ui.games

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import kotlin.math.sin
import kotlin.random.Random

/**
 * Usher Dash: an usher with a flashlight runs down the theater aisle.
 * Press the button to jump over popcorn spills, lost shoes and stacks of film cans; hold it to jump higher.
 * Tickets floating in the air are worth +5.
 */
class UsherDash : MiniGame() {
    private enum class Kind { SPILL, SHOE, CANS }

    private class Obstacle(
        var x: Float,
        val kind: Kind,
        val w: Float,
        val h: Float,
    )

    private class Ticket(
        var x: Float,
        val y: Float,
    )

    private val obstacles = mutableListOf<Obstacle>()
    private val tickets = mutableListOf<Ticket>()
    private var uy = 0f
    private var vy = 0f
    private var onGround = true
    private var holding = false
    private var distance = 0f
    private var bonus = 0
    private var spawnIn = 0f
    private var runTime = 0f
    private var scroll = 0f

    private val u get() = height / 100f
    private val ground get() = height * 0.82f
    private val ux get() = width * 0.2f
    private val bodyW get() = 6f * u
    private val bodyH get() = 15f * u
    private val gravity get() = 330f * u
    private val jump get() = 105f * u
    private val speed get() = (55f + (distance / (u * 40f)).coerceAtMost(45f)) * u * pace

    override fun reset() {
        obstacles.clear()
        tickets.clear()
        score = 0
        bonus = 0
        distance = 0f
        runTime = 0f
        uy = ground
        vy = 0f
        onGround = true
        holding = false
        spawnIn = 1.2f
    }

    override fun step(dt: Float) {
        val move = speed * dt
        distance += move
        scroll += move
        runTime += dt

        // Jumping: falling is a bit faster than rising, and letting go early makes a short hop
        val g = if (vy < 0f && holding) gravity * 0.55f else gravity
        vy += g * dt
        uy += vy * dt
        if (uy >= ground) {
            if (!onGround) burst(ux, ground, Color(0xFFB0A090), count = 4, speed = 12f, size = 0.6f)
            uy = ground
            vy = 0f
            onGround = true
        }

        spawnIn -= dt
        if (spawnIn <= 0f) {
            val roll = Random.nextFloat()
            val kind =
                when {
                    !easy && distance > 60f * u * 10f && roll < 0.25f -> Kind.CANS
                    roll < 0.6f -> Kind.SPILL
                    else -> Kind.SHOE
                }
            val o =
                when (kind) {
                    Kind.SPILL -> Obstacle(width + 10f * u, kind, (8f + Random.nextFloat() * 5f) * u, 2.5f * u)
                    Kind.SHOE -> Obstacle(width + 10f * u, kind, 5f * u, 4.5f * u)
                    Kind.CANS -> Obstacle(width + 10f * u, kind, 6f * u, 10f * u)
                }
            obstacles.add(o)
            if (Random.nextFloat() < 0.3f) tickets.add(Ticket(o.x + o.w / 2f, ground - bodyH - (12f + Random.nextFloat() * 10f) * u))
            // Gaps get a bit shorter as you speed up, but always leave room to land and jump again
            val minGap = 0.75f + 50f * u / speed
            spawnIn = (minGap + Random.nextFloat() * 0.9f) / pace
        }
        obstacles.forEach { it.x -= move }
        obstacles.removeAll { it.x + it.w < -5f * u }
        tickets.forEach { it.x -= move }
        tickets.removeAll { it.x < -5f * u }

        // Bumps: a slightly forgiving box around the usher
        val left = ux - bodyW * 0.35f
        val right = ux + bodyW * 0.35f
        val feet = uy
        obstacles.forEach { o ->
            if (right > o.x + o.w * 0.1f && left < o.x + o.w * 0.9f && feet > ground - o.h * 0.8f) {
                burst(ux, uy - bodyH / 2f, Color(0xFFE50914), count = 16, speed = 45f, size = 1.1f)
                gameOver()
                return
            }
        }
        tickets.removeAll { t ->
            val hit = kotlin.math.abs(t.x - ux) < bodyW && t.y > uy - bodyH - 3f * u && t.y < uy + 2f * u
            if (hit) {
                bonus += 5
                sound(Sfx.COIN)
                popup(t.x, t.y - 4f * u, "+5")
                burst(t.x, t.y, Color(0xFFFFD54F), count = 10, speed = 30f)
            }
            hit
        }
        score = (distance / (u * 12f)).toInt() + bonus
    }

    override fun releaseAll() {
        holding = false
    }

    override fun input(
        input: GameInput,
        down: Boolean,
    ) {
        if (input != GameInput.ACTION) return
        holding = down
        if (down && onGround) {
            vy = -jump
            onGround = false
            sound(Sfx.FLAP)
        }
    }

    override fun DrawScope.draw(textMeasurer: TextMeasurer) {
        // Dark auditorium with the movie screen glowing far away
        drawRect(Brush.verticalGradient(listOf(Color(0xFF101828), Color(0xFF0A0A12))))
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFF9FC5FF), Color(0xFF4F6FA8))),
            Offset(width * 0.25f, 6f * u),
            Size(width * 0.5f, 22f * u),
            CornerRadius(u),
            alpha = 0.35f,
        )
        // Seat rows in the back drift by slowly
        val back = 14f * u
        var x = -(scroll * 0.3f % back)
        while (x < width) {
            drawRoundRect(Color(0xFF3A0F1C), Offset(x, ground - 30f * u), Size(back * 0.85f, 14f * u), CornerRadius(2.5f * u))
            x += back
        }
        val front = 20f * u
        x = -(scroll * 0.6f % front)
        while (x < width) {
            drawRoundRect(Color(0xFF5A1020), Offset(x, ground - 20f * u), Size(front * 0.85f, 18f * u), CornerRadius(3f * u))
            x += front
        }
        // The aisle carpet with little step lights
        drawRect(Color(0xFF2A1A12), Offset(0f, ground), Size(width, height - ground))
        val lights = 18f * u
        x = -(scroll % lights)
        while (x < width) {
            drawCircle(Color(0xFFFFC94D), 0.7f * u, Offset(x, ground + 2f * u))
            drawCircle(Color(0x33FFC94D), 2.2f * u, Offset(x, ground + 2f * u))
            x += lights
        }

        obstacles.forEach { drawObstacle(it) }
        tickets.forEach { drawTicket(it.x, it.y + sin(runTime * 4f + it.x) * u, 2.4f * u) }
        drawUsher()
    }

    private fun DrawScope.drawObstacle(o: Obstacle) {
        val top = ground - o.h
        when (o.kind) {
            Kind.SPILL -> {
                drawOval(Color(0xFFE8C15A), Offset(o.x, ground - o.h * 0.6f), Size(o.w, o.h))
                val r = 1.3f * u
                var px = o.x + r
                while (px < o.x + o.w - r) {
                    drawCircle(Color(0xFFFFF4D6), r, Offset(px, top + r * 0.6f + (px * 7f % 3f) * 0.3f * u))
                    px += r * 1.7f
                }
            }

            Kind.SHOE -> {
                drawRoundRect(Color(0xFF6D4C41), Offset(o.x, top + o.h * 0.35f), Size(o.w, o.h * 0.65f), CornerRadius(o.h * 0.25f))
                drawRoundRect(Color(0xFF6D4C41), Offset(o.x, top), Size(o.w * 0.45f, o.h * 0.6f), CornerRadius(o.h * 0.15f))
                drawRect(Color(0xFFEEEEEE), Offset(o.x, ground - o.h * 0.15f), Size(o.w, o.h * 0.15f))
            }

            Kind.CANS -> {
                // A stack of three film cans
                val canH = o.h / 3f
                for (i in 0 until 3) {
                    val y = ground - (i + 1) * canH
                    drawRoundRect(Color(0xFF9EA4AE), Offset(o.x, y), Size(o.w, canH * 0.92f), CornerRadius(canH * 0.3f))
                    drawRect(Color(0xFF5E636B), Offset(o.x, y + canH * 0.4f), Size(o.w, canH * 0.12f))
                }
            }
        }
    }

    private fun DrawScope.drawUsher() {
        val feet = uy
        val hipY = feet - bodyH * 0.38f
        val shoulderY = feet - bodyH * 0.78f
        val headR = bodyH * 0.11f
        // Flashlight beam pointing ahead and down at the aisle
        val beam =
            Path().apply {
                moveTo(ux + bodyW * 0.5f, shoulderY + bodyH * 0.12f)
                lineTo(ux + 34f * u, ground - 1f * u)
                lineTo(ux + 16f * u, ground + 2f * u)
                close()
            }
        drawPath(beam, Color(0x40FFF59D))
        // Legs: swing while running, tucked while jumping
        val swing = if (onGround) sin(runTime * 18f) * bodyW * 0.45f else bodyW * 0.25f
        drawLine(Color(0xFF111111), Offset(ux, hipY), Offset(ux + swing, feet), strokeWidth = bodyW * 0.3f, cap = StrokeCap.Round)
        drawLine(Color(0xFF111111), Offset(ux, hipY), Offset(ux - swing, feet), strokeWidth = bodyW * 0.3f, cap = StrokeCap.Round)
        // Red usher jacket with gold buttons
        drawRoundRect(
            Color(0xFFE50914),
            Offset(ux - bodyW / 2f, shoulderY),
            Size(bodyW, hipY - shoulderY + bodyH * 0.05f),
            CornerRadius(bodyW * 0.25f),
        )
        drawCircle(Color(0xFFFFD54F), bodyW * 0.08f, Offset(ux + bodyW * 0.15f, shoulderY + bodyH * 0.12f))
        drawCircle(Color(0xFFFFD54F), bodyW * 0.08f, Offset(ux + bodyW * 0.15f, shoulderY + bodyH * 0.24f))
        // Arm holding the flashlight
        drawLine(
            Color(0xFFB0070F),
            Offset(ux, shoulderY + bodyH * 0.08f),
            Offset(ux + bodyW * 0.55f, shoulderY + bodyH * 0.14f),
            strokeWidth =
                bodyW * 0.25f,
            cap = StrokeCap.Round,
        )
        drawRoundRect(
            Color(0xFF444444),
            Offset(ux + bodyW * 0.45f, shoulderY + bodyH * 0.09f),
            Size(bodyW * 0.45f, bodyW * 0.22f),
            CornerRadius(bodyW * 0.08f),
        )
        // Head and cap
        val headC = Offset(ux, shoulderY - headR * 1.05f)
        drawCircle(Color(0xFFF1C27D), headR, headC)
        drawRoundRect(
            Color(0xFFB0070F),
            Offset(headC.x - headR, headC.y - headR * 1.15f),
            Size(headR * 2f, headR * 0.8f),
            CornerRadius(headR * 0.3f),
        )
        drawRect(Color(0xFF111111), Offset(headC.x, headC.y - headR * 0.45f), Size(headR * 1.3f, headR * 0.22f))
    }
}
