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
    BLOSSOMS,
    HYPERSPACE,
    ZAPS,
    COIN,
    FIRE_TRAILS,
    FLOWER_BLOOM,
    SUNSET,
    SPROUTS,
    SPOOKY_CAT,
    GROUNDHOG,
    HARVEST_MOON,
    POPCORN,
    GAME_BUTTONS,
    CAT_TAIL,
    PAW_PRINTS,
    PIRATE_FLAG,
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
        date == easter(date.year) -> Celebration("Happy Easter", "🐣", 0xFFFFB3C7, LogoEffect.BLOSSOMS)
        date == midAutumn(date.year) -> Celebration("Happy Mid-Autumn Festival", "🥮", 0xFFFFC94D, LogoEffect.HARVEST_MOON)
        date == nthSunday(date, 5, 2) -> Celebration("Happy Mother's Day", "💐", 0xFFFF9EC7, LogoEffect.FLOWER_BLOOM)
        date == nthSunday(date, 6, 3) -> Celebration("Happy Father's Day", "🎣", 0xFFE0A050, LogoEffect.SUNSET)
        m == 1 && d == 19 -> Celebration("Happy National Popcorn Day", "🍿", 0xFFFFCF4D, LogoEffect.POPCORN)
        m == 2 && d == 2 -> Celebration("Happy Groundhog Day", "🦫", 0xFFC79A72, LogoEffect.GROUNDHOG)
        m == 2 && d == 27 -> Celebration("Happy Pokémon Day", "⚡", 0xFFFFDE00, LogoEffect.ZAPS)
        m == 3 && d == 10 -> Celebration("Happy Mario Day", "🍄", 0xFFFBD000, LogoEffect.COIN)
        m == 4 && d == 22 -> Celebration("Happy Earth Day", "🌍", 0xFF6CC06A, LogoEffect.SPROUTS)
        m == 5 && d == 4 -> Celebration("May the 4th be with you", "✨", 0xFFFFD54F, LogoEffect.HYPERSPACE)
        m == 8 && d == 8 -> Celebration("Happy International Cat Day", "🐈", 0xFFF0A35E, LogoEffect.CAT_TAIL)
        m == 8 && d == 26 -> Celebration("Happy International Dog Day", "🐕", 0xFFC68642, LogoEffect.PAW_PRINTS)
        m == 9 && d == 12 -> Celebration("Happy Video Games Day", "🎮", 0xFF7C4DFF, LogoEffect.GAME_BUTTONS)
        m == 9 && d == 19 -> Celebration("Ahoy! Talk Like a Pirate Day", "🏴‍☠️", 0xFFC9A227, LogoEffect.PIRATE_FLAG)
        m == 10 && d == 21 -> Celebration("Back to the Future Day", "⚡", 0xFFFF6A00, LogoEffect.FIRE_TRAILS)
        d == 13 && date.dayOfWeek == java.time.DayOfWeek.FRIDAY -> Celebration("Friday the 13th", "🔪", 0xFFB71C1C, LogoEffect.SPOOKY_CAT)
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

/** Easter Sunday (Western), worked out with the usual Gregorian formula */
fun easter(year: Int): LocalDate {
    val a = year % 19
    val b = year / 100
    val c = year % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val month = (h + l - 7 * m + 114) / 31
    val day = ((h + l - 7 * m + 114) % 31) + 1
    return LocalDate.of(year, month, day)
}

/** The Mid-Autumn Festival follows the moon, so its dates are listed */
fun midAutumn(year: Int): LocalDate? =
    when (year) {
        2026 -> LocalDate.of(2026, 9, 25)
        2027 -> LocalDate.of(2027, 9, 15)
        2028 -> LocalDate.of(2028, 10, 3)
        2029 -> LocalDate.of(2029, 9, 22)
        2030 -> LocalDate.of(2030, 9, 12)
        2031 -> LocalDate.of(2031, 10, 1)
        2032 -> LocalDate.of(2032, 9, 19)
        else -> null
    }

/** The [n]th Sunday of [month] in [date]'s year (Mother's Day = 2nd Sunday of May) */
fun nthSunday(
    date: LocalDate,
    month: Int,
    n: Int,
): LocalDate =
    LocalDate
        .of(date.year, month, 1)
        .with(TemporalAdjusters.dayOfWeekInMonth(n, java.time.DayOfWeek.SUNDAY))

/** True on a Friday the 13th */
fun isFriday13(date: LocalDate): Boolean = date.dayOfMonth == 13 && date.dayOfWeek == java.time.DayOfWeek.FRIDAY
