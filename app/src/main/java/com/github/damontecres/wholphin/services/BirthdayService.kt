package com.github.damontecres.wholphin.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.sdk.model.UUID
import timber.log.Timber
import java.time.MonthDay
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
