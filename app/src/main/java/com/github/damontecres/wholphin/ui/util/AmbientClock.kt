package com.github.damontecres.wholphin.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.delay

/**
 * One shared, slow clock for the gentle background motion (holiday trims and marks, Home's slow picture zoom).
 *
 * Speed check (Ben 2026-11-17: menus lag on the older TCL, the Server page doesn't): every moving holiday piece used
 * to run its own clock at the full 60 frames a second, so the whole screen was drawn again 60 times a second all
 * day long, posters, pictures and shadows included. Slow ambient motion looks the same at about 20 frames a second,
 * so this one clock ticks about 20 times a second for everything, and it holds still for a moment while you press
 * the remote, so moving around always gets the TV's full attention. Time doesn't jump when it carries on: it picks
 * up where it stopped. Animations stay on everywhere, lite mode too (Ben's rule).
 */
object AmbientClock {
    /** Seconds of ambient time (stops while the remote is in use and while nothing ambient shows) */
    val seconds = mutableFloatStateOf(0f)

    /** Which on-screen user drives the clock; the others just wait in case it goes away */
    private var driver: Any? = null

    /** Each tick waits this long after the last frame: about 20 frames a second on a 60 Hz TV */
    private const val TICK_MS = 34L

    /** After a key press the art holds still this long, so focus moves and scrolling run at full speed */
    private const val HOLD_MS = 450L

    /** A spare user checks this often whether it should take over driving */
    private const val SPARE_MS = 250L

    internal suspend fun attach() {
        val me = Any()
        try {
            var last = -1L
            while (true) {
                if (driver == null) driver = me
                if (driver !== me) {
                    last = -1L
                    delay(SPARE_MS)
                    continue
                }
                val now = withFrameMillis { it }
                // A long gap (the app was hidden, a page took a while) doesn't count as time passing
                val step = if (last < 0) 0L else (now - last).coerceIn(0L, 100L)
                last = now
                if (!LastRemoteKey.pressedRecently(HOLD_MS)) seconds.floatValue += step / 1000f
                delay(TICK_MS)
            }
        } finally {
            if (driver === me) driver = null
        }
    }
}

/**
 * The shared [AmbientClock], kept ticking while this is on screen. Read it only while drawing (inside a Canvas,
 * drawBehind or graphicsLayer block) so a tick only redraws that one piece and never rebuilds the screen.
 */
@Composable
fun rememberAmbientClock(): FloatState {
    LaunchedEffect(Unit) { AmbientClock.attach() }
    return AmbientClock.seconds
}
