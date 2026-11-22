package com.github.damontecres.wholphin.util

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.core.content.edit
import timber.log.Timber

/**
 * Glowfin player stopwatch (feel check 5, Ben 2026-11-21): quietly notes three times whenever something
 * plays on this device, and keeps the last 40 of each for the Server page:
 * - Play pressed to first picture
 * - a button press to the bar being drawn
 * - how long a skip took, when the video had to load again
 *
 * It only reads the clock, so it costs nothing while watching. Nothing leaves this device.
 */
object PlayerTimer {
    @Volatile private var loadAt = 0L

    @Volatile private var barAt = 0L

    @Volatile private var seekAt = 0L

    @Volatile private var seekLoading = false

    private fun now() = runCatching { SystemClock.elapsedRealtime() }.getOrDefault(0L)

    /** A video (or a new copy of it) starts loading */
    fun loadStarted() {
        loadAt = now()
        seekAt = 0L
        seekLoading = false
    }

    /** The first picture of that video is ready */
    private fun pictureReady(context: Context) {
        val start = loadAt
        loadAt = 0L
        if (start > 0L) add(context, START, now() - start)
    }

    /** Someone skipped forward or back */
    fun seekStarted() {
        if (loadAt != 0L) return
        seekAt = now()
        seekLoading = false
    }

    /** The player went to loading (not ready) or playing (ready) */
    fun playerState(
        context: Context,
        ready: Boolean,
        loading: Boolean,
    ) {
        // The first picture of a video that was loading
        if (ready && loadAt != 0L) {
            pictureReady(context)
            return
        }
        val start = seekAt
        if (start == 0L) return
        if (loading) {
            seekLoading = true
        } else if (ready) {
            seekAt = 0L
            if (seekLoading) add(context, SKIP, now() - start)
            seekLoading = false
        }
    }

    /** A button press asked for the bar */
    fun barAsked() {
        if (barAt == 0L) barAt = now()
    }

    /** The bar has been drawn */
    fun barDrawn(context: Context) {
        val start = barAt
        barAt = 0L
        if (start > 0L) add(context, BAR, now() - start)
    }

    fun barHidden() {
        barAt = 0L
    }

    @Synchronized
    private fun add(
        context: Context,
        key: String,
        ms: Long,
    ) {
        if (ms <= 0L || ms > TOO_LONG_MS) return
        runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val list = read(prefs.getString(key, null)) + ms
            prefs.edit { putString(key, list.takeLast(KEEP).joinToString(",")) }
            Timber.i("Glowfin player timing: %s %s ms", key, ms)
        }
    }

    private fun read(text: String?): List<Long> = text?.split(",")?.mapNotNull { it.toLongOrNull() }.orEmpty()

    /**
     * For the Server page, like "Play to first picture: usually 2.1 s, slowest 6.0 s (14 plays)". Null
     * until something has been played on this device.
     */
    fun summary(context: Context): List<String>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lines =
            listOfNotNull(
                line("Play to first picture", read(prefs.getString(START, null)), "plays"),
                line("Bar appears after a press", read(prefs.getString(BAR, null)), "presses"),
                line("Skips that had to load", read(prefs.getString(SKIP, null)), "skips"),
            )
        return lines.takeIf { it.isNotEmpty() }
    }

    private fun line(
        name: String,
        times: List<Long>,
        word: String,
    ): String? {
        if (times.isEmpty()) return null
        val sorted = times.sorted()
        return "$name: usually ${nice(sorted[sorted.size / 2])}, slowest ${nice(sorted.last())} (${times.size} $word)"
    }

    private fun nice(ms: Long): String = if (ms < 1000) "$ms ms" else String.format(java.util.Locale.US, "%.1f s", ms / 1000.0)

    private const val PREFS = "glowfin_player_timing"
    private const val START = "start"
    private const val BAR = "bar"
    private const val SKIP = "skip"
    private const val KEEP = 40
    private const val TOO_LONG_MS = 120_000L
}

/** Notes when the player's bar has really been drawn after a press. Put it where the player is. */
@Composable
fun PlayerBarTiming(
    context: Context,
    barVisible: Boolean,
) {
    LaunchedEffect(barVisible) {
        if (barVisible) {
            withFrameNanos { }
            PlayerTimer.barDrawn(context)
        } else {
            PlayerTimer.barHidden()
        }
    }
}
