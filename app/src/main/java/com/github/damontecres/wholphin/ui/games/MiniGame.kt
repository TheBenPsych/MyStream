package com.github.damontecres.wholphin.ui.games

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/**
 * READY = the start card, COUNTDOWN = 3-2-1 before play, PAUSED = Back was pressed mid-game, OVER = the end card
 */
enum class GamePhase { READY, COUNTDOWN, PLAYING, PAUSED, OVER }

enum class GameInput { LEFT, RIGHT, UP, DOWN, ACTION }

/** Short sound effects. Each one is a little file in res/raw (made by make_game_sounds.py). */
enum class Sfx { POP, BOOST, FLAP, POINT, COIN, CRASH, THUD, FLIP, TICK, GO, MISS }

/**
 * A small built-in Benflix game: it moves forward a little every frame and draws itself onto a canvas.
 *
 * The shared parts live here: the 3-2-1 countdown, pausing, sounds, the little bursts of crumbs,
 * the "+5" pop-ups and the screen shake.
 */
abstract class MiniGame {
    /** Bumped every frame so the canvas redraws */
    var frame by mutableLongStateOf(0L)
    var phase by mutableStateOf(GamePhase.READY)
    var score by mutableIntStateOf(0)

    /** Kids profiles get a slower, friendlier version */
    var easy = false

    /** Seconds left on the 3-2-1 countdown */
    var countdown = 0f
        private set

    /** Seconds since the game ended, so a last button mash doesn't instantly restart it */
    var sinceOver = 0f
        private set

    /** Up on the remote works as the main button (flap / drop), unless a game uses it to move */
    open val upIsAction = true

    /** On a phone, the game wants to know exactly where the screen was tapped */
    open val usesTaps = false

    var width = 0f
        private set
    var height = 0f
        private set

    /** Sounds waiting to be played; the game page takes them every frame */
    val sounds = mutableListOf<Sfx>()

    /** Set when something should give a little buzz on phones */
    var buzz = false

    fun setSize(
        w: Float,
        h: Float,
    ) {
        if (w != width || h != height) {
            width = w
            height = h
            reset()
        }
    }

    fun start() {
        reset()
        clearEffects()
        countdown = 3f
        phase = GamePhase.COUNTDOWN
        sound(Sfx.TICK)
    }

    fun pause() {
        if (phase == GamePhase.PLAYING || phase == GamePhase.COUNTDOWN) {
            phase = GamePhase.PAUSED
            releaseAll()
        }
    }

    /** Back to the game after a pause, with a short countdown so nobody gets caught off guard */
    fun resume() {
        if (phase == GamePhase.PAUSED) {
            countdown = 1.99f
            phase = GamePhase.COUNTDOWN
            sound(Sfx.TICK)
        }
    }

    fun update(dt: Float) {
        when (phase) {
            GamePhase.COUNTDOWN -> {
                val before = kotlin.math.ceil(countdown).toInt()
                countdown -= dt
                val after = kotlin.math.ceil(countdown).toInt()
                if (countdown <= 0f) {
                    phase = GamePhase.PLAYING
                    sound(Sfx.GO)
                } else if (after != before) {
                    sound(Sfx.TICK)
                }
            }

            GamePhase.PLAYING -> {
                if (width > 0f) step(dt)
            }

            GamePhase.OVER -> {
                sinceOver += dt
            }

            else -> {}
        }
        if (phase != GamePhase.PAUSED) stepEffects(dt)
        frame++
    }

    protected fun gameOver() {
        phase = GamePhase.OVER
        sinceOver = 0f
        releaseAll()
        sound(Sfx.CRASH)
        buzz = true
        shake(2.2f)
    }

    protected fun sound(sfx: Sfx) {
        sounds.add(sfx)
    }

    /** How fast things move: a bit slower on Kids profiles */
    protected val pace get() = if (easy) 0.78f else 1f

    /** Put everything back to the start */
    abstract fun reset()

    /** Move the game forward by [dt] seconds */
    protected abstract fun step(dt: Float)

    /** A button was pressed (down = true) or let go (down = false) while playing */
    abstract fun input(
        input: GameInput,
        down: Boolean,
    )

    /** Let go of every held button (used when pausing) */
    protected open fun releaseAll() {}

    /** The screen was tapped at x, y while playing (only for games with [usesTaps]) */
    open fun tap(
        x: Float,
        y: Float,
    ) {}

    abstract fun DrawScope.draw(textMeasurer: TextMeasurer)

    // ---- Little effects shared by every game ----

    private class Bit(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        val maxLife: Float,
        val size: Float,
        val color: Color,
    )

    private class Popup(
        val text: String,
        var x: Float,
        var y: Float,
        var life: Float,
        val color: Color,
    )

    private val bits = mutableListOf<Bit>()
    private val popups = mutableListOf<Popup>()
    private var shakeLeft = 0f
    private var shakePower = 0f

    private fun clearEffects() {
        bits.clear()
        popups.clear()
        shakeLeft = 0f
    }

    /** A little burst of crumbs or sparks */
    protected fun burst(
        x: Float,
        y: Float,
        color: Color,
        count: Int = 8,
        speed: Float = 30f,
        size: Float = 0.9f,
    ) {
        val u = height / 100f
        repeat(count) {
            val a = Random.nextFloat() * 2f * Math.PI.toFloat()
            val s = (0.4f + Random.nextFloat() * 0.6f) * speed * u
            val life = 0.35f + Random.nextFloat() * 0.35f
            bits.add(
                Bit(
                    x,
                    y,
                    kotlin.math.cos(a) * s,
                    kotlin.math.sin(a) * s - 10f * u,
                    life,
                    life,
                    size * u * (0.6f + Random.nextFloat() * 0.8f),
                    color,
                ),
            )
        }
    }

    /** Floating text like "+5" that drifts up and fades */
    protected fun popup(
        x: Float,
        y: Float,
        text: String,
        color: Color = Color(0xFFFFD54F),
    ) {
        popups.add(Popup(text, x, y, 0.9f, color))
    }

    /** Shake the screen; [power] is in percent of the screen height */
    protected fun shake(power: Float) {
        shakePower = power
        shakeLeft = 0.35f
    }

    /** When the camera scrolls, move the effects with the world */
    protected fun shiftEffects(dy: Float) {
        bits.forEach { it.y += dy }
        popups.forEach { it.y += dy }
    }

    private fun stepEffects(dt: Float) {
        val u = height / 100f
        bits.forEach {
            it.vy += 80f * u * dt
            it.x += it.vx * dt
            it.y += it.vy * dt
            it.life -= dt
        }
        bits.removeAll { it.life <= 0f }
        popups.forEach {
            it.y -= 12f * u * dt
            it.life -= dt
        }
        popups.removeAll { it.life <= 0f }
        if (shakeLeft > 0f) shakeLeft -= dt
    }

    /** How far to nudge the whole picture this frame for the screen shake */
    val shakeOffset: Offset
        get() {
            if (shakeLeft <= 0f) return Offset.Zero
            val p = shakePower * height / 100f * (shakeLeft / 0.35f)
            return Offset((Random.nextFloat() * 2f - 1f) * p, (Random.nextFloat() * 2f - 1f) * p)
        }

    fun DrawScope.drawEffects(textMeasurer: TextMeasurer) {
        bits.forEach {
            val a = (it.life / it.maxLife).coerceIn(0f, 1f)
            drawRect(it.color.copy(alpha = a), Offset(it.x - it.size / 2f, it.y - it.size / 2f), Size(it.size, it.size))
        }
        val u = height / 100f
        popups.forEach {
            val layout =
                textMeasurer.measure(
                    it.text,
                    TextStyle(
                        color = it.color.copy(alpha = (it.life / 0.9f).coerceIn(0f, 1f)),
                        fontSize = (5f * u / density).sp,
                        fontWeight = FontWeight.Black,
                    ),
                )
            drawText(layout, topLeft = Offset(it.x - layout.size.width / 2f, it.y - layout.size.height / 2f))
        }
    }
}
