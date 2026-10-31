package com.github.damontecres.wholphin.services

import org.jellyfin.sdk.model.UUID
import timber.log.Timber
import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Each profile's birthday, saved with that profile's settings on the Jellyfin server
 * so it carries over to every TV
 */
@Singleton
class BirthdayService
    @Inject
    constructor(
        private val displayPreferencesService: DisplayPreferencesService,
    ) {
        suspend fun get(userId: UUID): MonthDay? =
            try {
                displayPreferencesService
                    .getDisplayPreferences(userId)
                    .customPrefs[KEY]
                    ?.let { MonthDay.parse("--$it") }
            } catch (ex: Exception) {
                Timber.w(ex, "Could not read birthday")
                null
            }

        suspend fun set(
            userId: UUID,
            day: MonthDay?,
        ) = displayPreferencesService.updateDisplayPreferences(userId) {
            if (day == null) {
                remove(KEY)
            } else {
                put(KEY, "%02d-%02d".format(day.monthValue, day.dayOfMonth))
            }
        }

        companion object {
            private const val KEY = "benflix_birthday"
        }
    }

/**
 * The banner shown at the top of Home on a special day
 */
data class Celebration(
    val text: String,
    val confetti: Boolean,
)

/**
 * Holiday banners, or null on an ordinary day
 */
fun holidayFor(date: LocalDate): Celebration? {
    val m = date.monthValue
    val d = date.dayOfMonth
    val thanksgiving =
        date.withDayOfMonth(1).with(TemporalAdjusters.dayOfWeekInMonth(4, java.time.DayOfWeek.THURSDAY))
    return when {
        m == 1 && d == 1 -> Celebration("🎆 Happy New Year from Benflix! 🎆", true)
        m == 2 && d == 14 -> Celebration("❤️ Happy Valentine's Day ❤️", false)
        m == 3 && d == 17 -> Celebration("☘️ Happy St. Patrick's Day ☘️", false)
        m == 7 && d == 4 -> Celebration("🎆 Happy 4th of July! 🎆", true)
        m == 10 && d == 31 -> Celebration("🎃 Happy Halloween! 🎃", false)
        m == 11 && date == thanksgiving -> Celebration("🦃 Happy Thanksgiving! 🦃", false)
        m == 12 && d == 24 -> Celebration("🎄 Merry Christmas Eve! 🎄", false)
        m == 12 && d == 25 -> Celebration("🎄 Merry Christmas! 🎁", true)
        m == 12 && d == 31 -> Celebration("🥳 Happy New Year's Eve! 🥳", true)
        else -> null
    }
}
