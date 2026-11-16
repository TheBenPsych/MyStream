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

    fun onKey(event: KeyEvent) {
        if (event.action != KeyEvent.ACTION_DOWN) return
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

    /** True if Left/Up/Down was pressed in the last [withinMs] */
    fun movedRecently(withinMs: Long = 900L): Boolean = android.os.SystemClock.uptimeMillis() - moveAt < withinMs
}
