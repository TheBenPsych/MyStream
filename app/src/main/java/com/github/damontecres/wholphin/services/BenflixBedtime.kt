package com.github.damontecres.wholphin.services

import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.UUID
import timber.log.Timber
import java.security.MessageDigest
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benflix parent PIN. It's optional: a family can set it in the welcome setup or on My Benflix.
 * It's saved (scrambled, never the real numbers) in the person's own settings on the server,
 * so it works on every TV. The Kids profile uses the same sign-in, so it can check it too.
 */
@Singleton
class BenflixParentPin
    @Inject
    constructor(
        private val displayPreferencesService: DisplayPreferencesService,
    ) {
        suspend fun hasPin(userId: UUID): Boolean = savedHash(userId) != null

        suspend fun check(
            userId: UUID,
            pin: String,
        ): Boolean = savedHash(userId)?.let { it == hash(userId, pin) } ?: false

        /** Sets a new parent PIN, or turns it off with null */
        suspend fun set(
            userId: UUID,
            pin: String?,
        ) {
            displayPreferencesService.updateDisplayPreferences(userId) {
                if (pin.isNullOrBlank()) remove(KEY) else put(KEY, hash(userId, pin))
            }
            BenflixBedtime.pinSet = !pin.isNullOrBlank()
        }

        private suspend fun savedHash(userId: UUID): String? =
            try {
                displayPreferencesService.getDisplayPreferences(userId).customPrefs[KEY]?.takeIf { it.isNotBlank() }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Couldn't read the parent PIN")
                null
            }

        private fun hash(
            userId: UUID,
            pin: String,
        ): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest("benflix:$userId:$pin".toByteArray())
                .joinToString("") { "%02x".format(it) }

        companion object {
            private const val KEY = "benflix.parentPin"
        }
    }

/**
 * Benflix bedtime for the Kids profile: from 9 PM until 6 AM, Kids is locked, but only when the
 * family has set a parent PIN. With no PIN, Kids stays unlocked. Whatever is playing gets to
 * finish first; the next episode won't start. A grown-up's PIN unlocks 30 more minutes.
 */
object BenflixBedtime {
    val BEDTIME: LocalTime = LocalTime.of(21, 0)
    val WAKE_UP: LocalTime = LocalTime.of(6, 0)
    const val MORE_TIME_MS = 30 * 60_000L

    /** Whether this person has a parent PIN (loaded when Benflix starts or the person changes) */
    @Volatile var pinSet = false

    /** A grown-up gave more time until this moment */
    @Volatile var moreTimeUntil = 0L

    fun isBedtime(now: LocalTime = LocalTime.now()): Boolean = !now.isBefore(BEDTIME) || now.isBefore(WAKE_UP)

    /** True when Kids should be put to bed now */
    fun lockedNow(): Boolean = KidsMode.isActive && pinSet && isBedtime() && System.currentTimeMillis() > moreTimeUntil

    fun giveMoreTime() {
        moreTimeUntil = System.currentTimeMillis() + MORE_TIME_MS
    }
}
