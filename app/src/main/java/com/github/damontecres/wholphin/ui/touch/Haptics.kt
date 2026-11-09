package com.github.damontecres.wholphin.ui.touch

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView

/**
 * Benflix on phones: a light buzz when you tap a button, and a softer tick as the Home carousel
 * moves. Can be turned off in Settings > Phone. TVs never buzz.
 */
object Haptics {
    /** Set from the person's settings (Settings > Phone > Light buzz on taps) */
    @Volatile var enabled = true

    fun tap(view: View) {
        if (!enabled || isTvDevice(view.context)) return
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /** A firmer buzz when holding something opens its menu */
    fun hold(view: View) {
        if (!enabled || isTvDevice(view.context)) return
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun tick(view: View) {
        if (!enabled || isTvDevice(view.context)) return
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    class Buzzer(
        private val view: View,
    ) {
        fun tap() = tap(view)

        fun tick() = tick(view)
    }

    @Composable
    fun remember(): Buzzer {
        val view = LocalView.current
        return androidx.compose.runtime.remember(view) { Buzzer(view) }
    }
}
