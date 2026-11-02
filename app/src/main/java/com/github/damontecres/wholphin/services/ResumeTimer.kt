package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences

/**
 * Benflix: "come back where you left off".
 *
 * When someone leaves the app (home button, switching apps, TV turned off) and comes back sooner than
 * the time picked in Settings (default 30 minutes), they go straight back to the page they were on.
 * Only after that time has passed does Who's watching show again.
 *
 * The time the app was left is kept on disk, so it also works if Android closed the app in the
 * background to free up memory.
 */
object ResumeTimer {
    private const val PREFS = "benflix_resume"
    private const val KEY_LEFT_AT = "leftAt"

    /** Decided when the app comes back to the screen: true = skip Who's watching */
    @Volatile
    var resumeAllowed: Boolean = false

    /** The app just went to the background */
    fun markLeft(context: Context) {
        try {
            context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LEFT_AT, System.currentTimeMillis())
                .apply()
        } catch (_: Exception) {
            // Best effort only
        }
    }

    /** A brand new launch (not a return), so forget the last time the app was left */
    fun clear(context: Context) {
        resumeAllowed = false
        try {
            context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LEFT_AT)
                .apply()
        } catch (_: Exception) {
        }
    }

    /** Minutes away allowed before Who's watching shows again; negative = always ask */
    fun minutes(prefs: AppPreferences?): Int =
        prefs
            ?.benflixPreferences
            ?.resumeMinutes
            ?.takeIf { it != 0 } ?: AppPreference.RESUME_DEFAULT_MINUTES

    /** True if the person came back soon enough to skip Who's watching */
    fun withinTime(
        context: Context,
        minutes: Int,
    ): Boolean {
        if (minutes <= 0) return false
        val leftAt =
            try {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LEFT_AT, 0L)
            } catch (_: Exception) {
                0L
            }
        if (leftAt <= 0L) return false
        val away = System.currentTimeMillis() - leftAt
        return away in 0..(minutes * 60_000L)
    }
}
