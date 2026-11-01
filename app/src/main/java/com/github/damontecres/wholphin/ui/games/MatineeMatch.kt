package com.github.damontecres.wholphin.ui.games

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.github.damontecres.wholphin.ui.FontAwesome
import kotlin.math.abs
import kotlin.math.min

/**
 * Matinee Match: flip cards two at a time to find the matching pairs of movie icons before the clock runs out.
 * Every pair adds a few seconds; clear the board for a bonus and a bigger board.
 */
class MatineeMatch : MiniGame() {
    /** One movie icon: a FontAwesome symbol and its color */
    private class Icon(
        val glyph: String,
        val color: Color,
    )

    private class Card(
        val icon: Int,
        var shown: Boolean = false,
        var matched: Boolean = false,
        /** 0 = face down, 1 = face up; animates the flip */
        var flip: Float = 0f,
    )

    private val icons =
        listOf(
            Icon("", Color(0xFF455A64)), // film strip
            Icon("", Color(0xFFFF8F00)), // ticket
            Icon("", Color(0xFFFFC107)), // star
            Icon("", Color(0xFF212121)), // clapperboard
            Icon("", Color(0xFF8E24AA)), // theater masks
            Icon("", Color(0xFF5C6BC0)), // ghost
            Icon("", Color(0xFFE53935)), // rocket
            Icon("", Color(0xFFF9A825)), // crown
            Icon("", Color(0xFF00897B)), // robot
            Icon("", Color(0xFF43A047)), // dragon
        )

    override val upIsAction = false
    override val usesTaps = true

    private val cards = mutableListOf<Card>()
    private var cols = 4
    private var rows = 3
    private var cursor = 0
    private var firstPick = -1
    private var secondPick = -1
    private var hideIn = 0f
    private var timeLeft = 0f
    private var board = 0

    private val u get() = height / 100f
    private val totalTime get() = if (easy) 75f else 45f

    override fun reset() {
        score = 0
        board = 0
        timeLeft = totalTime
        deal()
    }

    private fun deal() {
        // First board is 4 x 3; after that 5 x 4 (Kids keep the smaller board)
        if (board == 0 || easy) {
            cols = 4
            rows = 3
        } else {
            cols = 5
            rows = 4
        }
        val pairs = cols * rows / 2
        val picks = icons.indices.shuffled().take(pairs)
        cards.clear()
        (picks + picks).shuffled().forEach { cards.add(Card(it)) }
        cursor = 0
        firstPick = -1
        secondPick = -1
        hideIn = 0f
    }

    override fun step(dt: Float) {
        timeLeft -= dt * pace
        cards.forEach {
            val target = if (it.shown || it.matched) 1f else 0f
            it.flip += (target - it.flip).coerceIn(-dt * 6f, dt * 6f)
        }
        if (hideIn > 0f) {
            hideIn -= dt
            if (hideIn <= 0f) {
                cards[firstPick].shown = false
                cards[secondPick].shown = false
                firstPick = -1
                secondPick = -1
            }
        }
        if (timeLeft <= 0f) {
            timeLeft = 0f
            gameOver()
        }
    }

    private fun pick(index: Int) {
        if (index !in cards.indices) return
        val card = cards[index]
        if (card.matched || card.shown) return
        // Two wrong cards are still showing: put them back right away
        if (hideIn > 0f) {
            hideIn = 0f
            cards[firstPick].shown = false
            cards[secondPick].shown = false
            firstPick = -1
            secondPick = -1
        }
        card.shown = true
        sound(Sfx.FLIP)
        if (firstPick < 0) {
            firstPick = index
            return
        }
        secondPick = index
        val a = cards[firstPick]
        if (a.icon == card.icon) {
            a.matched = true
            card.matched = true
            score += 10
            timeLeft = min(timeLeft + 3f, totalTime)
            sound(Sfx.COIN)
            val c = center(index)
            popup(c.x, c.y, "+10")
            burst(c.x, c.y, icons[card.icon].color, count = 12, speed = 35f)
            burst(center(firstPick).x, center(firstPick).y, icons[card.icon].color, count = 12, speed = 35f)
            firstPick = -1
            secondPick = -1
            if (cards.all { it.matched }) {
                score += 25
                board++
                timeLeft = min(timeLeft + 10f, totalTime)
                popup(width / 2f, height / 2f, "Board clear! +25", Color.White)
                sound(Sfx.BOOST)
                deal()
            }
        } else {
            hideIn = 0.8f
            sound(Sfx.MISS)
        }
    }

    override fun input(
        input: GameInput,
        down: Boolean,
    ) {
        if (!down) return
        val r = cursor / cols
        val c = cursor % cols
        when (input) {
            GameInput.LEFT -> cursor = r * cols + (c - 1 + cols) % cols
            GameInput.RIGHT -> cursor = r * cols + (c + 1) % cols
            GameInput.UP -> cursor = ((r - 1 + rows) % rows) * cols + c
            GameInput.DOWN -> cursor = ((r + 1) % rows) * cols + c
            GameInput.ACTION -> pick(cursor)
        }
    }

    override fun tap(
        x: Float,
        y: Float,
    ) {
        val hit =
            cards.indices.firstOrNull { i ->
                val c = center(i)
                abs(x - c.x) < cardW / 2f && abs(y - c.y) < cardH / 2f
            } ?: return
        cursor = hit
        pick(hit)
    }

    // Card layout: fit the grid in the space under the timer bar
    private val gap get() = 2f * u
    private val areaTop get() = 14f * u
    private val cardH get() = min((height - areaTop - 4f * u - gap * (rows - 1)) / rows, ((width * 0.9f - gap * (cols - 1)) / cols) * 1.35f)
    private val cardW get() = cardH / 1.35f

    private fun center(i: Int): Offset {
        val gridW = cols * cardW + (cols - 1) * gap
        val left = (width - gridW) / 2f
        val r = i / cols
        val c = i % cols
        return Offset(left + c * (cardW + gap) + cardW / 2f, areaTop + r * (cardH + gap) + cardH / 2f)
    }

    override fun setupPreview() {
        // A few cards already matched and one pair being checked
        val first = cards.indices.first()
        cards.indices.filter { cards[it].icon == cards[first].icon }.forEach {
            cards[it].matched = true
            cards[it].flip = 1f
        }
        cards.indices.filter { !cards[it].matched }.take(2).forEach {
            cards[it].shown = true
            cards[it].flip = 1f
        }
    }

    override fun DrawScope.draw(textMeasurer: TextMeasurer) {
        // Red theater curtains on both sides
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1A0B12), Color(0xFF0B0B10))))
        val curtain = width * 0.08f
        drawRect(Brush.horizontalGradient(listOf(Color(0xFF7A0A12), Color(0xFF3A0508)), 0f, curtain), Offset.Zero, Size(curtain, height))
        drawRect(
            Brush.horizontalGradient(listOf(Color(0xFF3A0508), Color(0xFF7A0A12)), width - curtain, width),
            Offset(width - curtain, 0f),
            Size(curtain, height),
        )

        // Time left: a bar along the top that turns red near the end
        val barW = width * 0.6f
        val frac = (timeLeft / totalTime).coerceIn(0f, 1f)
        drawRoundRect(Color(0x33FFFFFF), Offset((width - barW) / 2f, 9f * u), Size(barW, 1.6f * u), CornerRadius(u))
        drawRoundRect(
            if (frac <
                0.25f
            ) {
                Color(0xFFE50914)
            } else {
                Color(0xFFFFD54F)
            },
            Offset((width - barW) / 2f, 9f * u),
            Size(barW * frac, 1.6f * u),
            CornerRadius(u),
        )

        val w = cardW
        val h = cardH
        cards.forEachIndexed { i, card ->
            val c = center(i)
            // Flipping: squash sideways to nothing, then open up showing the other side
            val sx = abs(card.flip * 2f - 1f).coerceAtLeast(0.02f)
            val faceUp = card.flip > 0.5f
            scale(sx, 1f, c) {
                val topLeft = Offset(c.x - w / 2f, c.y - h / 2f)
                if (faceUp) {
                    drawRoundRect(if (card.matched) Color(0xFFFFF8E1) else Color.White, topLeft, Size(w, h), CornerRadius(w * 0.1f))
                    val icon = icons[card.icon]
                    val layout =
                        textMeasurer.measure(
                            icon.glyph,
                            TextStyle(color = icon.color, fontFamily = FontAwesome, fontSize = (w * 0.5f / density).sp),
                        )
                    drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
                } else {
                    drawRoundRect(Color(0xFFE50914), topLeft, Size(w, h), CornerRadius(w * 0.1f))
                    drawRoundRect(
                        Color(0xFFB0070F),
                        topLeft + Offset(w * 0.08f, w * 0.08f),
                        Size(w * 0.84f, h - w * 0.16f),
                        CornerRadius(w * 0.06f),
                        style = Stroke(w * 0.03f),
                    )
                    val layout =
                        textMeasurer.measure(
                            "B",
                            TextStyle(color = Color.White, fontSize = (w * 0.42f / density).sp, fontWeight = FontWeight.Black),
                        )
                    drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
                }
            }
            if (card.matched) drawRoundRect(Color(0x55000000), Offset(c.x - w / 2f, c.y - h / 2f), Size(w, h), CornerRadius(w * 0.1f))
        }
        // The cursor for the remote / controller
        if (phase == GamePhase.PLAYING || phase == GamePhase.COUNTDOWN) {
            val c = center(cursor)
            val pad = 0.8f * u
            drawRoundRect(
                Color.White,
                Offset(c.x - w / 2f - pad, c.y - h / 2f - pad),
                Size(w + 2 * pad, h + 2 * pad),
                CornerRadius(w * 0.12f),
                style = Stroke(0.7f * u),
            )
        }
    }
}
