package com.github.damontecres.wholphin.ui.games

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer

enum class GamePhase { READY, PLAYING, OVER }

enum class GameInput { LEFT, RIGHT, ACTION }

/**
 * A small built-in Benflix game: it moves forward a little every frame and draws itself onto a canvas
 */
abstract class MiniGame {
    /** Bumped every frame so the canvas redraws */
    var frame by mutableLongStateOf(0L)
    var phase by mutableStateOf(GamePhase.READY)
    var score by mutableIntStateOf(0)

    var width = 0f
        private set
    var height = 0f
        private set

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
        phase = GamePhase.PLAYING
    }

    fun update(dt: Float) {
        if (width > 0f && phase == GamePhase.PLAYING) step(dt)
        frame++
    }

    protected fun gameOver() {
        phase = GamePhase.OVER
    }

    /** Put everything back to the start */
    abstract fun reset()

    /** Move the game forward by [dt] seconds */
    protected abstract fun step(dt: Float)

    /** A button was pressed (down = true) or let go (down = false) while playing */
    abstract fun input(
        input: GameInput,
        down: Boolean,
    )

    abstract fun DrawScope.draw(textMeasurer: TextMeasurer)
}
