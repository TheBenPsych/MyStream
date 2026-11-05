package com.github.damontecres.wholphin.ui.playback

import android.content.Context
import android.os.Build
import androidx.core.content.edit

/**
 * Benflix: whether Dolby/DTS sound (AC3, EAC3, DTS) is sent to the TV's sound system as-is
 * ("passthrough") or turned into plain sound by Benflix first.
 *
 * Passthrough only matters for a soundbar or receiver that does surround. On TCL TVs (Realtek chip)
 * the TV's own passthrough goes silent after a pause, a skip or turning subtitles on, while the
 * picture keeps playing (Ben, 2026-11-04/05). So on TCL it is off unless turned on, everywhere
 * else it is on unless turned off. Remembered on this device.
 */
object SurroundPassthrough {
    private const val PREFS = "benflix_audio"
    private const val KEY = "surround_passthrough"

    /** TVs whose own passthrough loses the sound */
    internal fun badPassthroughDevice(
        manufacturer: String = Build.MANUFACTURER,
        brand: String = Build.BRAND,
    ): Boolean = manufacturer.equals("TCL", ignoreCase = true) || brand.equals("TCL", ignoreCase = true)

    fun isOn(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY)) prefs.getBoolean(KEY, true) else !badPassthroughDevice()
    }

    fun toggle(context: Context) {
        val on = !isOn(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY, on) }
    }
}
