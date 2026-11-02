package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.ui.main.ServerDisk
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.activityLogApi
import org.jellyfin.sdk.model.api.LogLevel
import org.json.JSONObject
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benflix server checks for the admin: how full the disks are (Server page) and the short
 * "heads-up" line on the admin's Home when something needs attention.
 */
@Singleton
class BenflixServerHealth
    @Inject
    constructor(
        private val api: ApiClient,
        @param:StandardOkHttpClient private val http: OkHttpClient,
    ) {
        /** The heads-up, worked out once per app start (null = all good) */
        @Volatile private var checked: Pair<String?, String?>? = null

        /** Benflix: the last disk answer, so the Server page can show it straight away */
        @Volatile var lastDisks: List<ServerDisk>? = null
            private set

        /**
         * Asks Jellyfin (10.10 or newer) how full the disks with each library are. Libraries on
         * the same disk show the same numbers, so they're shown together.
         */
        suspend fun readDisks(): List<ServerDisk> =
            withContext(WholphinDispatchers.IO) {
                val base = api.baseUrl?.trimEnd('/') ?: return@withContext emptyList()
                val request =
                    Request
                        .Builder()
                        .url("$base/System/Info/Storage")
                        .header("Authorization", "MediaBrowser Token=\"${api.accessToken}\"")
                        .build()
                val json =
                    http.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@withContext emptyList()
                        JSONObject(response.body.string())
                    }
                val libraries = json.optJSONArray("Libraries") ?: return@withContext emptyList()
                val byDisk = LinkedHashMap<Pair<Long, Long>, MutableList<String>>()
                for (i in 0 until libraries.length()) {
                    val library = libraries.optJSONObject(i) ?: continue
                    val name = library.optString("Name")
                    if (name.isBlank()) continue
                    val folders = library.optJSONArray("Folders") ?: continue
                    for (j in 0 until folders.length()) {
                        val folder = folders.optJSONObject(j) ?: continue
                        val used = folder.optLong("UsedSpace", -1)
                        val free = folder.optLong("FreeSpace", -1)
                        if (used < 0 || free < 0) continue
                        val names = byDisk.getOrPut(used to free) { mutableListOf() }
                        if (name !in names) names += name
                    }
                }
                byDisk
                    .map { (space, names) -> ServerDisk(names.joinToString(", "), space.first, space.second) }
                    .also { lastDisks = it }
            }

        /** One short line for the admin's Home, or null when everything is fine */
        suspend fun headsUp(): String? {
            checked?.let { return it.first }
            val disk =
                safe {
                    readDisks()
                        .filter { it.nearlyFull }
                        .maxByOrNull { it.usedShare }
                        ?.let { "The disk with ${it.name} is ${(it.usedShare * 100).toInt()}% full" }
                }
            val problems =
                safe {
                    withContext(WholphinDispatchers.IO) {
                        api.activityLogApi
                            .getLogEntries(0, LOG_LIMIT, LocalDateTime.now(ZoneOffset.UTC).minusDays(1))
                            .content.items
                            .count { it.severity == LogLevel.ERROR || it.severity == LogLevel.CRITICAL }
                    }
                } ?: 0
            val text =
                disk ?: if (problems >= MIN_PROBLEMS) {
                    "$problems problems on the server in the last day"
                } else {
                    null
                }
            checked = text to null
            return text
        }

        private suspend fun <T> safe(block: suspend () -> T): T? =
            try {
                block()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Server check skipped")
                null
            }

        companion object {
            private const val LOG_LIMIT = 40
            private const val MIN_PROBLEMS = 3
        }
    }
