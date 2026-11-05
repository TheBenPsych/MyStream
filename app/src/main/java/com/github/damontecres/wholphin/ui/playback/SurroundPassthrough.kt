package com.github.damontecres.wholphin.ui.playback

import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

/**
 * Benflix: whether Dolby/DTS sound (AC3, EAC3, DTS) is sent to the TV's sound system as-is
 * ("passthrough") or turned into plain sound by Benflix first.
 *
 * Passthrough only matters for a soundbar or receiver that does surround. On TCL TVs (Realtek chip)
 * the TV's own passthrough goes silent after a pause, a skip or turning subtitles on, while the
 * picture keeps playing (Ben, 2026-11-04/05). So on TCL it is off unless turned on, everywhere
 * else it is on unless turned off. When it is turned on on a TCL, the player gives the sound a
 * fresh start (a jump to the same spot) after each pause and subtitle switch (see needsKick).
 * Remembered on this device.
 */
@OptIn(UnstableApi::class)
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

    /** Dolby/DTS formats that go to the sound chip as-is when passthrough is on */
    private val passthroughFormats =
        setOf(
            MimeTypes.AUDIO_AC3,
            MimeTypes.AUDIO_E_AC3,
            MimeTypes.AUDIO_E_AC3_JOC,
            MimeTypes.AUDIO_AC4,
            MimeTypes.AUDIO_DTS,
            MimeTypes.AUDIO_DTS_HD,
            MimeTypes.AUDIO_DTS_EXPRESS,
            MimeTypes.AUDIO_TRUEHD,
        )

    /** True when this TV's passthrough needs a fresh start after a pause or subtitle switch */
    fun needsKick(
        context: Context,
        soundMimeType: String?,
    ): Boolean = soundMimeType in passthroughFormats && badPassthroughDevice() && isOn(context)

    fun toggle(context: Context) {
        val on = !isOn(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY, on) }
    }
}
