package com.github.damontecres.wholphin.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * The little animation that plays around the Benflix logo on a special day
 */
enum class LogoEffect {
    NONE,
    FIREWORKS_GOLD,
    FIREWORKS_USA,
    HEART_FLOAT,
    GREEN_SHIMMER,
    GHOST_PEEK,
    FALLING_LEAVES,
    TWINKLE_LIGHTS,
    CONFETTI,
    MARIGOLDS,
    LANTERNS,
    DIYAS,
    EMBERS,
    STAR_PAIR,
}

/**
 * A special day: a small animation on the Benflix logo and a short line under it
 */
data class Celebration(
    val text: String,
    val emoji: String,
    val color: Long,
    val effect: LogoEffect = LogoEffect.NONE,
)

/**
 * Today's celebration for the signed-in profile, shown by the logo in the top bar
 */
object CelebrationNow {
    private val _current = MutableStateFlow<Celebration?>(null)
    val current: StateFlow<Celebration?> = _current

    fun set(celebration: Celebration?) {
        _current.value = celebration
    }
}

/**
 * Holidays, or null on an ordinary day
 */
fun holidayFor(date: LocalDate): Celebration? {
    val m = date.monthValue
    val d = date.dayOfMonth
    val thanksgiving =
        date.withDayOfMonth(1).with(TemporalAdjusters.dayOfWeekInMonth(4, java.time.DayOfWeek.THURSDAY))
    return when {
        m == 1 && d == 1 -> Celebration("Happy New Year", "🎆", 0xFFFFD54F, LogoEffect.FIREWORKS_GOLD)
        m == 2 && d == 14 -> Celebration("Happy Valentine's Day", "❤️", 0xFFFF6B9A, LogoEffect.HEART_FLOAT)
        m == 3 && d == 17 -> Celebration("Happy St. Patrick's Day", "☘️", 0xFF4CD964, LogoEffect.GREEN_SHIMMER)
        m == 7 && d == 4 -> Celebration("Happy 4th of July", "🎆", 0xFF64B5F6, LogoEffect.FIREWORKS_USA)
        m == 10 && d == 31 -> Celebration("Happy Halloween", "🎃", 0xFFFF8C1A, LogoEffect.GHOST_PEEK)
        m == 11 && date == thanksgiving -> Celebration("Happy Thanksgiving", "🦃", 0xFFE0A050, LogoEffect.FALLING_LEAVES)
        m == 12 && d == 24 -> Celebration("Merry Christmas Eve", "🎄", 0xFF66BB6A, LogoEffect.TWINKLE_LIGHTS)
        m == 12 && d == 25 -> Celebration("Merry Christmas", "🎄", 0xFF66BB6A, LogoEffect.TWINKLE_LIGHTS)
        m == 12 && d == 31 -> Celebration("Happy New Year's Eve", "🥳", 0xFFFFD54F, LogoEffect.FIREWORKS_GOLD)
        m == 11 && (d == 1 || d == 2) -> Celebration("Día de los Muertos", "🌼", 0xFFFF9F1C, LogoEffect.MARIGOLDS)
        m == 11 && d == 5 -> Celebration("Happy Bonfire Night", "🔥", 0xFFFF7A1A, LogoEffect.EMBERS)
        m == 7 && d == 7 -> Celebration("Happy Tanabata", "🎋", 0xFF9FB8FF, LogoEffect.STAR_PAIR)
        date == lunarNewYear(date.year) -> Celebration("Happy Lunar New Year", "🏮", 0xFFFF3B30, LogoEffect.LANTERNS)
        date == diwali(date.year) -> Celebration("Happy Diwali", "🪔", 0xFFFFC14D, LogoEffect.DIYAS)
        else -> null
    }
}

/** Lunar New Year moves with the moon, so its dates are listed (add more years as they come) */
fun lunarNewYear(year: Int): LocalDate? =
    when (year) {
        2026 -> LocalDate.of(2026, 2, 17)
        2027 -> LocalDate.of(2027, 2, 6)
        2028 -> LocalDate.of(2028, 1, 26)
        2029 -> LocalDate.of(2029, 2, 13)
        2030 -> LocalDate.of(2030, 2, 3)
        2031 -> LocalDate.of(2031, 1, 23)
        2032 -> LocalDate.of(2032, 2, 11)
        else -> null
    }

/** Diwali also moves with the moon each year */
fun diwali(year: Int): LocalDate? =
    when (year) {
        2026 -> LocalDate.of(2026, 11, 8)
        2027 -> LocalDate.of(2027, 10, 29)
        2028 -> LocalDate.of(2028, 10, 17)
        2029 -> LocalDate.of(2029, 11, 5)
        2030 -> LocalDate.of(2030, 10, 26)
        2031 -> LocalDate.of(2031, 11, 14)
        2032 -> LocalDate.of(2032, 11, 2)
        else -> null
    }
