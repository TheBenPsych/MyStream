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
    EMBERS,
    STAR_PAIR,
    BLOSSOMS,
    HYPERSPACE,
    ZAPS,
    COIN,
    FIRE_TRAILS,
    FLOWER_BLOOM,
    SUNSET,
    SPOOKY_CAT,
    GROUNDHOG,
    HARVEST_MOON,
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
        2033 -> LocalDate.of(2033, 1, 31)
        2034 -> LocalDate.of(2034, 2, 19)
        2035 -> LocalDate.of(2035, 2, 8)
        2036 -> LocalDate.of(2036, 1, 28)
        2037 -> LocalDate.of(2037, 2, 15)
        2038 -> LocalDate.of(2038, 2, 4)
        2039 -> LocalDate.of(2039, 1, 24)
        2040 -> LocalDate.of(2040, 2, 12)
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
        2033 -> LocalDate.of(2033, 9, 8)
        2034 -> LocalDate.of(2034, 9, 27)
        2035 -> LocalDate.of(2035, 9, 16)
        2036 -> LocalDate.of(2036, 10, 4)
        2037 -> LocalDate.of(2037, 9, 24)
        2038 -> LocalDate.of(2038, 9, 13)
        2039 -> LocalDate.of(2039, 10, 2)
        2040 -> LocalDate.of(2040, 9, 20)
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
