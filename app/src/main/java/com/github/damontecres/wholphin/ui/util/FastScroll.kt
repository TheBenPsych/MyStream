package com.github.damontecres.wholphin.ui.util

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Benflix: knows when an arrow on the remote is being held down to fly along a row.
 *
 * While it's held, the background picture and its glow stay as they are instead of changing on every
 * poster, so fast scrolling on slower TVs doesn't hiccup. Everything catches up a moment after the
 * arrow is let go.
 */
object FastScroll {
    private val _held = MutableStateFlow(false)
    val held: StateFlow<Boolean> = _held

    /** Called for every key the app gets (from MainActivity) */
    fun onKey(event: KeyEvent) {
        if (event.keyCode !in ARROWS) return
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount > 0 && !_held.value) _held.value = true
            KeyEvent.ACTION_UP -> if (_held.value) _held.value = false
        }
    }

    /** Waits until no arrow is held. Returns true if it had to wait. */
    suspend fun awaitReleased(): Boolean {
        if (!_held.value) return false
        // A missed key-up (like the app going to the background mid-hold) never leaves things stuck
        if (withTimeoutOrNull(MAX_HOLD_MS) { held.first { !it } } == null) _held.value = false
        return true
    }

    private const val MAX_HOLD_MS = 4_000L

    private val ARROWS =
        setOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
        )
}
