package com.github.damontecres.wholphin.services

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.edit

/**
 * Lite mode makes Benflix lighter for slow or older TVs: no muted trailer previews on Home and
 * no preloading of the next clip. It turns itself on for low-memory devices, and can be switched
 * in the hidden speed meter (tap "Installed version" 5 times in Settings).
 */
object LiteMode {
    private const val PREFS = "benflix_speed"
    private const val KEY = "lite_mode"

    @Volatile private var cached: Boolean? = null

    fun isOn(context: Context): Boolean {
        cached?.let { return it }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val value =
            if (prefs.contains(KEY)) {
                prefs.getBoolean(KEY, false)
            } else {
                isSlowDevice(context)
            }
        cached = value
        return value
    }

    fun set(
        context: Context,
        on: Boolean,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY, on) }
        cached = on
    }

    /** Low-memory devices (under about 1.5 GB) start with Lite mode on */
    private fun isSlowDevice(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        if (am.isLowRamDevice) return true
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem in 1 until 1_500L * 1024 * 1024
    }
}
