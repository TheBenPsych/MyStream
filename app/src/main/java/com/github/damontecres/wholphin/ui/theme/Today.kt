package com.github.damontecres.wholphin.ui.theme

import java.time.LocalDate
import java.time.ZoneId

/**
 * Today's date, worked out once and then kept for up to a minute (and never past midnight).
 *
 * Speed check (Ben 2026-11-18: menus lag even on his new TV): every app colour like [BenflixColors.Raised] asks each
 * holiday "are you on today?", and each of those asked Android for the date and time zone all over again. A row of
 * posters asked thousands of times while drawing. The date only changes at midnight, so the holidays now share this.
 */
object Today {
    private class Day(
        val date: LocalDate,
        val from: Long,
        val until: Long,
    )

    @Volatile
    private var day: Day? = null

    /** Checked again at least this often, so a changed clock or time zone shows within a minute */
    private const val KEEP_MS = 60_000L

    fun date(): LocalDate {
        val now = System.currentTimeMillis()
        val d = day
        if (d != null && now >= d.from && now < d.until) return d.date
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone)
        val midnight =
            date
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        day = Day(date, now, minOf(midnight, now + KEEP_MS))
        return date
    }
}

/**
 * Remembers the answer for the last date asked, for holiday checks that take some working out (Easter's date,
 * Earth week). Safe to share between threads: at worst it works the answer out twice.
 */
class DayAnswer(
    private val work: (LocalDate) -> Boolean,
) {
    private class Last(
        val date: LocalDate,
        val answer: Boolean,
    )

    @Volatile
    private var last: Last? = null

    operator fun invoke(date: LocalDate): Boolean {
        val l = last
        if (l != null && l.date == date) return l.answer
        val answer = work(date)
        last = Last(date, answer)
        return answer
    }
}
