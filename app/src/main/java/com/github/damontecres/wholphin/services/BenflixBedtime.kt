package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.UUID
import timber.log.Timber
import java.security.MessageDigest
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benflix parent PIN and Kids daily screen time. Both are optional: a family can set them in the
 * welcome setup or in Parental controls. The PIN is saved scrambled, never the real numbers.
 *
 * One PIN and one limit for the whole TV: whoever sets them, they apply to Kids (and to Parental
 * controls) on this TV, whichever grown-up's sign-in Kids borrows.
 * - Each grown-up's choice is saved in their own settings on the server, with the time it was set
 *   (so it follows them to other TVs and phones).
 * - This TV also keeps its own copy, which always holds the newest one it has seen: the one set here,
 *   or a newer one read from any saved grown-up on this TV (read when Kids starts, and whenever the
 *   signed-in person's settings are read). So Kids still finds it when the server can't be reached.
 * - PINs and limits saved before this (no time with them) still count: the first one found is kept,
 *   and anything set from now on wins over them.
 */
@Singleton
class BenflixParentPin
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val displayPreferencesService: DisplayPreferencesService,
    ) {
        /** A saved PIN: [hash] was made with [hashUserId]; a null [hash] = turned off. [at] 0 = saved before times were kept */
        private data class SavedPin(
            val hash: String?,
            val hashUserId: String,
            val at: Long,
        )

        private data class SavedMinutes(
            val minutes: Int,
            val at: Long,
        )

        suspend fun hasPin(userId: UUID): Boolean = pinState(userId) == true

        /** Whether a parent PIN is set, or null if it couldn't be read (so callers don't take that as "no PIN") */
        suspend fun pinState(userId: UUID): Boolean? {
            val prefs = readAccount(userId)
            if (prefs != null) adoptFrom(userId, prefs)
            val device = devicePin()
            if (prefs == null && device == null) return null
            return device?.hash != null
        }

        suspend fun check(
            userId: UUID,
            pin: String,
        ): Boolean {
            readAccount(userId)?.let { adoptFrom(userId, it) }
            val saved = devicePin() ?: return false
            return saved.hash != null && saved.hash == hash(saved.hashUserId, pin)
        }

        /** Sets a new parent PIN for this TV, or turns it off with null */
        suspend fun set(
            userId: UUID,
            pin: String?,
        ) {
            val now = System.currentTimeMillis()
            val newHash = if (pin.isNullOrBlank()) null else hash(userId.toString(), pin)
            displayPreferencesService.updateDisplayPreferences(userId) {
                if (newHash == null) remove(KEY) else put(KEY, newHash)
                put(PIN_AT_KEY, now.toString())
            }
            saveDevicePin(SavedPin(newHash, userId.toString(), now))
            BenflixBedtime.pinSet = newHash != null
        }

        /** Kids daily screen time in minutes (0 = no limit) for this TV */
        suspend fun kidsDailyMinutes(userId: UUID): Int {
            readAccount(userId)?.let { adoptFrom(userId, it) }
            return deviceMinutes()?.minutes ?: 0
        }

        suspend fun setKidsDailyMinutes(
            userId: UUID,
            minutes: Int,
        ) {
            val now = System.currentTimeMillis()
            val value = minutes.coerceAtLeast(0)
            displayPreferencesService.updateDisplayPreferences(userId) {
                if (value <= 0) remove(BenflixScreenTime.PREF_KEY) else put(BenflixScreenTime.PREF_KEY, value.toString())
                put(MINUTES_AT_KEY, now.toString())
            }
            saveDeviceMinutes(SavedMinutes(value, now))
            BenflixScreenTime.limitMinutes = value
        }

        /**
         * Looks at one grown-up's saved settings (from the server) and keeps their PIN and limit as this
         * TV's copy when they're newer than what the TV has. Used when Kids starts, for every saved grown-up.
         */
        @Synchronized
        fun adoptFrom(
            userId: UUID,
            customPrefs: Map<String, String?>,
        ) {
            val pinAt = customPrefs[PIN_AT_KEY]?.toLongOrNull() ?: 0L
            val pinHash = customPrefs[KEY]?.takeIf { it.isNotBlank() }
            if (pinHash != null || pinAt > 0) {
                val device = devicePin()
                if (device == null || pinAt > device.at) saveDevicePin(SavedPin(pinHash, userId.toString(), pinAt))
            }
            val minutesAt = customPrefs[MINUTES_AT_KEY]?.toLongOrNull() ?: 0L
            val minutes = customPrefs[BenflixScreenTime.PREF_KEY]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            if (minutes > 0 || minutesAt > 0) {
                val device = deviceMinutes()
                if (device == null || minutesAt > device.at) saveDeviceMinutes(SavedMinutes(minutes, minutesAt))
            }
        }

        private suspend fun readAccount(userId: UUID): Map<String, String?>? =
            try {
                displayPreferencesService.getDisplayPreferences(userId).customPrefs
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Couldn't read the parent PIN and screen time")
                null
            }

        private fun device() = context.getSharedPreferences(DEVICE_FILE, Context.MODE_PRIVATE)

        private fun devicePin(): SavedPin? {
            val prefs = device()
            if (!prefs.contains(D_PIN_AT)) return null
            val hashUserId = prefs.getString(D_PIN_USER, null) ?: return null
            return SavedPin(prefs.getString(D_PIN_HASH, null), hashUserId, prefs.getLong(D_PIN_AT, 0L))
        }

        private fun saveDevicePin(pin: SavedPin) {
            device().edit {
                putString(D_PIN_HASH, pin.hash)
                putString(D_PIN_USER, pin.hashUserId)
                putLong(D_PIN_AT, pin.at)
            }
        }

        private fun deviceMinutes(): SavedMinutes? {
            val prefs = device()
            if (!prefs.contains(D_MINUTES_AT)) return null
            return SavedMinutes(prefs.getInt(D_MINUTES, 0), prefs.getLong(D_MINUTES_AT, 0L))
        }

        private fun saveDeviceMinutes(minutes: SavedMinutes) {
            device().edit {
                putInt(D_MINUTES, minutes.minutes)
                putLong(D_MINUTES_AT, minutes.at)
            }
        }

        private fun hash(
            userId: String,
            pin: String,
        ): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest("benflix:$userId:$pin".toByteArray())
                .joinToString("") { "%02x".format(it) }

        companion object {
            private const val KEY = "benflix.parentPin"
            private const val PIN_AT_KEY = "benflix.parentPinAt"
            private const val MINUTES_AT_KEY = "benflix.kidsDailyMinutesAt"

            /** This TV's own copy (never synced) */
            private const val DEVICE_FILE = "benflix_parental_device"
            private const val D_PIN_HASH = "pinHash"
            private const val D_PIN_USER = "pinUser"
            private const val D_PIN_AT = "pinAt"
            private const val D_MINUTES = "minutes"
            private const val D_MINUTES_AT = "minutesAt"
        }
    }

/**
 * Benflix bedtime for the Kids profile: from bedtime (8, 9 or 10 PM, 9 PM unless the family picked
 * another) until 6 AM, Kids is locked, but only when the family has set a parent PIN. With no PIN,
 * Kids stays unlocked. Whatever is playing gets to finish first; the next episode won't start.
 * A grown-up's PIN unlocks 30 more minutes.
 *
 * Like the parent PIN, the bedtime belongs to the whole TV: it's kept on this device only.
 */
object BenflixBedtime {
    const val DEFAULT_BEDTIME_HOUR = 21

    /** The bedtimes a family can pick (24-hour clock) */
    val BEDTIME_HOURS = listOf(20, 21, 22)

    private const val DEVICE_FILE = "benflix_parental_device"
    private const val BEDTIME_HOUR_KEY = "bedtimeHour"

    /** This TV's bedtime hour (loaded with [load]) */
    @Volatile var bedtimeHour = DEFAULT_BEDTIME_HOUR

    val BEDTIME: LocalTime get() = LocalTime.of(bedtimeHour, 0)

    /** Reads this TV's bedtime */
    fun load(context: Context) {
        val hour =
            context
                .getSharedPreferences(DEVICE_FILE, Context.MODE_PRIVATE)
                .getInt(BEDTIME_HOUR_KEY, DEFAULT_BEDTIME_HOUR)
        bedtimeHour = if (hour in BEDTIME_HOURS) hour else DEFAULT_BEDTIME_HOUR
    }

    /** Sets this TV's bedtime: one of [BEDTIME_HOURS] */
    fun setBedtimeHour(
        context: Context,
        hour: Int,
    ) {
        val value = if (hour in BEDTIME_HOURS) hour else DEFAULT_BEDTIME_HOUR
        context.getSharedPreferences(DEVICE_FILE, Context.MODE_PRIVATE).edit { putInt(BEDTIME_HOUR_KEY, value) }
        bedtimeHour = value
    }

    /** "9 PM" */
    fun label(hour: Int = bedtimeHour): String =
        when {
            hour == 0 -> "12 AM"
            hour < 12 -> "$hour AM"
            hour == 12 -> "12 PM"
            else -> "${hour - 12} PM"
        }

    val WAKE_UP: LocalTime = LocalTime.of(6, 0)
    const val MORE_TIME_MS = 30 * 60_000L

    /** Whether this person has a parent PIN (loaded when Benflix starts or the person changes) */
    @Volatile var pinSet = false

    /** A grown-up gave more time until this moment */
    @Volatile var moreTimeUntil = 0L

    fun isBedtime(now: LocalTime = LocalTime.now()): Boolean = !now.isBefore(BEDTIME) || now.isBefore(WAKE_UP)

    /** True when Kids should be put to bed now */
    fun lockedNow(): Boolean = KidsMode.isActive && pinSet && isBedtime() && System.currentTimeMillis() > moreTimeUntil

    /** How long before bedtime the "15 minutes until bedtime" note shows */
    const val WIND_DOWN_MINUTES = 15L

    /** The day the wind-down note was last shown, so it shows once a night */
    @Volatile var windDownShownOn: java.time.LocalDate? = null

    /** True in the 15 minutes before bedtime, on Kids with a parent PIN, once a night */
    fun windDownNow(now: LocalTime = LocalTime.now()): Boolean =
        KidsMode.isActive &&
            pinSet &&
            !now.isBefore(BEDTIME.minusMinutes(WIND_DOWN_MINUTES)) &&
            now.isBefore(BEDTIME) &&
            windDownShownOn != java.time.LocalDate.now()

    fun giveMoreTime() {
        moreTimeUntil = System.currentTimeMillis() + MORE_TIME_MS
    }
}
