package com.github.damontecres.wholphin.util

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File

/**
 * Benflix: if Benflix keeps crashing right after it opens, it fixes itself instead of someone having to
 * "clear app data" on the TV.
 *
 * A crash in the first few seconds after opening is counted. After two of those in a row, the next start
 * throws away Benflix's saved copies of screens and lists (the cache: Home snapshot, rows, pictures), which
 * are the usual reason an app crashes on every open. Sign-ins, profiles, settings and game saves are kept.
 * Running normally for a little while sets the count back to zero.
 */
object CrashLoopGuard {
    private const val PREFS = "benflix_crash_guard"
    private const val KEY = "early_crashes"

    /** A crash this soon after opening counts as "crashes on open" */
    private const val EARLY_MS = 20_000L

    @Volatile
    private var startedAt = 0L

    /** Call first thing when the app starts */
    fun onStart(context: Context) {
        startedAt = SystemClock.elapsedRealtime()
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val crashes = prefs.getInt(KEY, 0)
            if (crashes >= 2) {
                Log.w("Benflix", "Benflix crashed $crashes times right after opening; clearing its saved copies to start fresh")
                context.cacheDir?.listFiles()?.forEach { deleteQuietly(it) }
                prefs.edit().putInt(KEY, 0).commit()
            }
            // Opened fine for a while: not a crash loop
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                prefs.edit().putInt(KEY, 0).apply()
            }, EARLY_MS)
        } catch (e: Exception) {
            Log.w("Benflix", "Crash guard couldn't start", e)
        }
    }

    /** Call from the crash handler, before the app closes */
    fun onCrash(context: Context) {
        try {
            if (startedAt == 0L || SystemClock.elapsedRealtime() - startedAt > EARLY_MS) return
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY, prefs.getInt(KEY, 0) + 1).commit()
        } catch (_: Throwable) {
        }
    }

    private fun deleteQuietly(file: File) {
        try {
            file.deleteRecursively()
        } catch (_: Exception) {
        }
    }
}
