package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate

/**
 * Benflix Kids daily screen time. A parent picks 1, 2 or 3 hours of watching a day for Kids
 * (in Parental controls). Like bedtime, it only locks when the family has a parent PIN, so a
 * grown-up can always add 30 more minutes. Whatever is playing gets to finish first.
 *
 * The daily limit is saved in the person's own settings on the server (same on every TV).
 * The minutes watched are counted on each TV or phone, and start again at midnight.
 */
object BenflixScreenTime {
    val CHOICES = listOf(0, 60, 120, 180)
    const val MORE_TIME_MINUTES = 30
    const val PREF_KEY = "benflix.kidsDailyMinutes"
    private const val FILE = "benflix_screen_time"

    /** Minutes allowed a day, 0 = no limit (loaded when Benflix starts or the person changes) */
    @Volatile var limitMinutes = 0

    /** True while Kids is watching something; set by the bedtime layer */
    @Volatile var watching = false

    @Volatile private var day: LocalDate = LocalDate.MIN

    @Volatile private var watchedMs = 0L

    @Volatile private var extraMinutes = 0

    @Volatile private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        day = prefs.getString("day", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.MIN
        watchedMs = prefs.getLong("watchedMs", 0L)
        extraMinutes = prefs.getInt("extraMinutes", 0)
        loaded = true
        rollOver(context)
    }

    /** A new day starts with nothing watched */
    private fun rollOver(context: Context) {
        val today = LocalDate.now()
        if (day != today) {
            day = today
            watchedMs = 0L
            extraMinutes = 0
            save(context)
        }
    }

    @Volatile private var lastTickMs = 0L

    /**
     * Called every few seconds. Adds the time since the last call while Kids is watching.
     * Safe to call from more than one place: the time is only counted once.
     */
    @Synchronized
    fun tick(
        context: Context,
        maxStepMs: Long,
    ) {
        load(context)
        rollOver(context)
        val now = System.currentTimeMillis()
        val step = if (lastTickMs == 0L) 0L else (now - lastTickMs).coerceIn(0L, maxStepMs)
        lastTickMs = now
        if (watching && KidsMode.isActive && step > 0) {
            watchedMs += step
            save(context)
        }
    }

    fun watchedMinutesToday(): Int = if (day == LocalDate.now()) (watchedMs / 60_000L).toInt() else 0

    /** True when Kids has used up today's time (and a parent PIN is set) */
    fun lockedNow(): Boolean =
        KidsMode.isActive &&
            BenflixBedtime.pinSet &&
            limitMinutes > 0 &&
            watchedMinutesToday() >= limitMinutes + (if (day == LocalDate.now()) extraMinutes else 0)

    /** Benflix "Kids clock": minutes of watching left today, or null when there's no daily limit */
    fun minutesLeftToday(): Int? {
        if (limitMinutes <= 0) return null
        val extra = if (day == LocalDate.now()) extraMinutes else 0
        return (limitMinutes + extra - watchedMinutesToday()).coerceAtLeast(0)
    }

    fun giveMoreTime(context: Context) {
        load(context)
        rollOver(context)
        // Counted from now, so 30 minutes means 30 more minutes of watching
        extraMinutes = (watchedMinutesToday() - limitMinutes).coerceAtLeast(0) + MORE_TIME_MINUTES
        save(context)
    }

    fun label(minutes: Int): String =
        when (minutes) {
            0 -> "Off"
            60 -> "1 hour a day"
            else -> "${minutes / 60} hours a day"
        }

    private fun save(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit {
            putString("day", day.toString())
            putLong("watchedMs", watchedMs)
            putInt("extraMinutes", extraMinutes)
        }
    }
}
