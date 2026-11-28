package com.github.damontecres.wholphin.ui.util

import android.view.KeyEvent

/**
 * When the person last pressed Left, Up or Down on the remote (MainActivity feeds every key in here).
 * Ben: a door page's side menu sometimes grabbed the focus on its own a few seconds after the page opened
 * (rows swapping in took away the card that had focus). The side menu uses this to tell "you moved here"
 * from "focus fell here", and hands focus back to the page in the second case.
 */
object LastRemoteKey {
    @Volatile
    var moveAt: Long = 0L
        private set

    /** When Left was last pressed (the only arrow that means "open the side menu") */
    @Volatile
    var leftAt: Long = 0L
        private set

    /** When any arrow, OK or Back was last pressed (the ambient art holds still for a moment while you move) */
    @Volatile
    var pressAt: Long = 0L
        private set

    /** When Up or Down was last pressed (rows put the remote back on the poster it was on in them) */
    @Volatile
    var upDownAt: Long = 0L
        private set

    fun onKey(event: KeyEvent) {
        if (event.action != KeyEvent.ACTION_DOWN) return
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            upDownAt = android.os.SystemClock.uptimeMillis()
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_MENU,
            -> pressAt = android.os.SystemClock.uptimeMillis()
        }
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_UP_LEFT ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN_LEFT
        ) {
            leftAt = android.os.SystemClock.uptimeMillis()
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_UP_LEFT,
            KeyEvent.KEYCODE_DPAD_DOWN_LEFT,
            KeyEvent.KEYCODE_TAB,
            -> moveAt = android.os.SystemClock.uptimeMillis()
        }
    }

    /** True if any arrow, OK, Back or Menu was pressed in the last [withinMs] */
    fun pressedRecently(withinMs: Long): Boolean = android.os.SystemClock.uptimeMillis() - pressAt < withinMs

    /** True if Left/Up/Down was pressed in the last [withinMs] */
    fun movedRecently(withinMs: Long = 900L): Boolean = android.os.SystemClock.uptimeMillis() - moveAt < withinMs

    /** True if Up or Down was pressed just now (this very press is still being handled) */
    fun upDownJustNow(withinMs: Long = 250L): Boolean = android.os.SystemClock.uptimeMillis() - upDownAt < withinMs
}
