package com.github.damontecres.wholphin.services

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.UUID
import timber.log.Timber
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** How long one profile really watched: seconds per month, and since when Benflix has been counting */
data class WatchTime(
    /** When Benflix first started counting for this profile (on any TV or phone), in ms, or null if never */
    val sinceMs: Long?,
    val secondsByMonth: Map<YearMonth, Long>,
) {
    fun seconds(month: YearMonth) = secondsByMonth[month] ?: 0L

    fun seconds(year: Int) = secondsByMonth.filterKeys { it.year == year }.values.sum()
}

/**
 * Benflix real watch time, for "Your November so far" and the year-end review.
 *
 * While a movie or episode is actually playing (not paused, not on a menu), the player adds the
 * time here. It's kept on the TV or phone first (a tiny save, no internet), and sent to the
 * server at most every 5 minutes and when playback stops, so it costs about one small message
 * per show. Each TV or phone saves its own line in the person's Jellyfin settings, and the stats
 * add up every device's line, so time on the TV and on the phone both count.
 */
@Singleton
class BenflixWatchTime
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val serverRepository: ServerRepository,
        private val displayPreferencesService: DisplayPreferencesService,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + WholphinDispatchers.IO)
        private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /** userId -> this device's counts, loaded on first use */
        private val local = mutableMapOf<UUID, Counts>()
        private val lastSentMs = mutableMapOf<UUID, Long>()

        init {
            instance = this
        }

        fun currentUserId(): UUID? = serverRepository.currentUser?.id

        /** Adds some real watching time for this profile; cheap enough to call every few seconds */
        fun add(
            userId: UUID?,
            ms: Long,
        ) {
            if (userId == null || ms <= 0) return
            val send =
                synchronized(this) {
                    val counts = counts(userId)
                    val now = System.currentTimeMillis()
                    if (counts.sinceMs == null) counts.sinceMs = now
                    counts.ms[YearMonth.now().toString()] = (counts.ms[YearMonth.now().toString()] ?: 0L) + ms
                    prefs.edit { putString(userId.toString(), counts.encode()) }
                    val last = lastSentMs[userId] ?: 0L
                    val tick = SystemClock.elapsedRealtime()
                    if (last == 0L || tick - last >= SEND_EVERY_MS) {
                        lastSentMs[userId] = tick
                        true
                    } else {
                        false
                    }
                }
            if (send) send(userId)
        }

        /** Playback stopped: send the latest count to the server now */
        fun flush(userId: UUID?) {
            if (userId == null) return
            synchronized(this) { lastSentMs[userId] = SystemClock.elapsedRealtime() }
            send(userId)
        }

        private fun send(userId: UUID) {
            val encoded = synchronized(this) { counts(userId).encode() }
            scope.launch {
                try {
                    displayPreferencesService.updateDisplayPreferences(userId, PREF_ID) {
                        put(KEY_PREFIX + deviceKey(), encoded)
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    // Kept on the device, so it's sent next time
                    Timber.w(ex, "Could not save watch time")
                }
            }
        }

        /**
         * Every device's watch time for this profile added up. If the server can't be reached,
         * this device's own count is still used.
         */
        suspend fun read(userId: UUID): WatchTime {
            val mine = synchronized(this) { counts(userId).snapshot() }
            val all =
                try {
                    displayPreferencesService
                        .getDisplayPreferences(userId, PREF_ID)
                        .customPrefs
                        .filterKeys { it.startsWith(KEY_PREFIX) && it != KEY_PREFIX + deviceKey() }
                        .values
                        .mapNotNull { it?.let(Counts::decode) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not read watch time")
                    emptyList()
                } + mine
            val since = all.mapNotNull { it.sinceMs }.minOrNull()
            val byMonth = mutableMapOf<YearMonth, Long>()
            all.forEach { counts ->
                counts.ms.forEach { (month, ms) ->
                    runCatching { YearMonth.parse(month) }.getOrNull()?.let { byMonth[it] = (byMonth[it] ?: 0L) + ms / 1000L }
                }
            }
            return WatchTime(since, byMonth)
        }

        private fun counts(userId: UUID): Counts =
            local.getOrPut(userId) {
                prefs.getString(userId.toString(), null)?.let(Counts::decode) ?: Counts(null, mutableMapOf())
            }

        private fun deviceKey(): String =
            prefs.getString(KEY_DEVICE, null) ?: java.util.UUID
                .randomUUID()
                .toString()
                .take(8)
                .also { prefs.edit { putString(KEY_DEVICE, it) } }

        /** One device's count: "since=<ms>;2026-11=<ms watched>;2026-10=..." */
        private class Counts(
            var sinceMs: Long?,
            val ms: MutableMap<String, Long>,
        ) {
            fun snapshot() = Counts(sinceMs, ms.toMutableMap())

            fun encode(): String = (listOfNotNull(sinceMs?.let { "since=$it" }) + ms.map { "${it.key}=${it.value}" }).joinToString(";")

            companion object {
                fun decode(value: String): Counts {
                    var since: Long? = null
                    val ms = mutableMapOf<String, Long>()
                    value.split(';').forEach { part ->
                        val key = part.substringBefore('=', "")
                        val number = part.substringAfter('=').toLongOrNull() ?: return@forEach
                        if (key == "since") since = number else if (key.isNotBlank()) ms[key] = number
                    }
                    return Counts(since, ms)
                }
            }
        }

        companion object {
            @Volatile var instance: BenflixWatchTime? = null

            private const val PREFS = "benflix_watch_time"
            private const val PREF_ID = "benflix-watch-time"
            private const val KEY_PREFIX = "device-"
            private const val KEY_DEVICE = "device"
            private const val SEND_EVERY_MS = 5 * 60_000L
        }
    }
