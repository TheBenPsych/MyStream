package com.github.damontecres.wholphin.util

import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * Benflix start-up stopwatch: notes how long each step of opening the app takes (app ready,
 * screen ready, signed in, Home rows, banner), writes it to the log (so "Report a problem"
 * includes it) and keeps the last one for the Server page.
 */
object StartupTimer {
    // Lazy and guarded, so unit tests (where Android's clock isn't available) don't crash
    private val processStart: Long by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Process.getStartElapsedRealtime()
            } else {
                SystemClock.elapsedRealtime()
            }
        }.getOrDefault(0L)
    }
    private val steps = LinkedHashMap<String, Long>()
    private var finished = false

    private val _signedIn = MutableStateFlow(false)

    /** True once the app knows where to go (Who's watching, Home or the server list) */
    val signedIn: StateFlow<Boolean> = _signedIn

    @Synchronized
    fun mark(step: String) {
        if (finished || step in steps) return
        val now = runCatching { SystemClock.elapsedRealtime() }.getOrNull() ?: return
        val ms = now - processStart
        steps[step] = ms
        Timber.i("Benflix start-up: %s after %s ms", step, ms)
    }

    fun markSignedIn() {
        mark(SIGNED_IN)
        _signedIn.value = true
    }

    /** The banner is up: the app is fully open, so save the summary */
    @Synchronized
    fun finish(context: Context) {
        if (finished) return
        mark(BANNER)
        finished = true
        val text = summary()
        Timber.i("Benflix start-up summary: %s", text)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, text) }
    }

    /** Like "Opened in 4.2 s (app 0.3, screen 0.6, signed in 1.4, Home rows 3.1, banner 4.2)" */
    private fun summary(): String {
        val total = steps.values.maxOrNull() ?: 0L
        return "Opened in ${seconds(total)} s (" +
            steps.entries.joinToString(", ") { "${it.key} ${seconds(it.value)}" } + ")"
    }

    /** The last start-up on this device, for the Server page */
    fun last(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    private fun seconds(ms: Long) = String.format(java.util.Locale.US, "%.1f", ms / 1000.0)

    const val APP = "app"
    const val SCREEN = "screen"
    const val SIGNED_IN = "signed in"
    const val HOME_ROWS = "Home rows"
    const val BANNER = "banner"
    private const val PREFS = "benflix_startup"
    private const val KEY = "last"
}
