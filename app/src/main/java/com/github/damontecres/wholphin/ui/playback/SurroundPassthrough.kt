package com.github.damontecres.wholphin.ui.playback

import android.content.Context
import androidx.core.content.edit

/**
 * Benflix: whether Dolby/DTS sound (AC3, EAC3, DTS) is sent to the soundbar or receiver as-is
 * ("passthrough", real 5.1) or turned into plain sound by the TV first.
 *
 * Off by default on every device (Ben, 2026-11-18: "I prefer consistency always"). With
 * passthrough, many soundbars drop out of Dolby mode after a pause or a skip and the sound goes
 * silent for a while or for good. Jellyfin, Emby and Plex users report the same, and turning
 * passthrough off is the fix that works. Anyone who wants real 5.1 can turn it on in the player's
 * Settings ("Surround to soundbar"). Remembered on this device.
 */
object SurroundPassthrough {
    private const val PREFS = "benflix_audio"

    /**
     * A new name, so everyone starts from the new default (off), whatever they or the old automatic
     * choice had before ("surround_passthrough")
     */
    private const val KEY = "surround_passthrough_v2"

    fun isOn(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun toggle(context: Context) {
        val on = !isOn(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY, on) }
    }
}
