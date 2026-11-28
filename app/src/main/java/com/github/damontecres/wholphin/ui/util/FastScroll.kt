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

    /** When the last arrow move was let through (the key's own clock) */
    private var movedAt = 0L

    /**
     * Speed + navigation check (Ben 2026-11-27): a held arrow sends about 20 moves a second, more than a weak TV
     * can draw, so the moves queued up and the cursor kept going after the arrow was let go. While it is held,
     * moves closer together than [PACE_MS] are dropped (about 10 a second). Single presses are never touched.
     * @return true when this repeat should be dropped
     */
    fun tooSoon(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.keyCode !in ARROWS) return false
        if (event.repeatCount > 0 && event.eventTime - movedAt < PACE_MS) return true
        movedAt = event.eventTime
        return false
    }

    /** Waits until no arrow is held. Returns true if it had to wait. */
    suspend fun awaitReleased(): Boolean {
        if (!_held.value) return false
        // A missed key-up (like the app going to the background mid-hold) never leaves things stuck
        if (withTimeoutOrNull(MAX_HOLD_MS) { held.first { !it } } == null) _held.value = false
        return true
    }

    private const val MAX_HOLD_MS = 4_000L
    private const val PACE_MS = 90L

    private val ARROWS =
        setOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
        )
}
