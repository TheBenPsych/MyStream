package com.github.damontecres.wholphin.services

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.edit

/**
 * Lite mode makes Benflix lighter for slow or older TVs: no muted trailer previews on Home and
 * no preloading of the next clip. It turns itself on for low-memory devices, the device check
 * ([BenflixDeviceCheck]) sets it to suit this device, and it can be switched in the hidden speed meter
 * (tap "Installed version" 5 times in Settings). Once someone switches it by hand, the device check
 * leaves it alone.
 */
object LiteMode {
    private const val PREFS = "benflix_speed"
    private const val KEY = "lite_mode"

    /** True when [KEY] was written by the device check, not by a person */
    private const val KEY_AUTO = "lite_mode_auto"

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

    /** A person switched it (in the speed meter): the device check won't change it after this */
    fun set(
        context: Context,
        on: Boolean,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY, on)
            putBoolean(KEY_AUTO, false)
        }
        cached = on
    }

    /** Whether a person switched it by hand (saved before the device check existed counts as by hand) */
    fun isUserSet(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.contains(KEY) && !prefs.getBoolean(KEY_AUTO, false)
    }

    /** The device check's pick. Does nothing (and returns false) when a person set it themselves */
    fun setAuto(
        context: Context,
        on: Boolean,
    ): Boolean {
        if (isUserSet(context)) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY, on)
            putBoolean(KEY_AUTO, true)
        }
        cached = on
        return true
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
