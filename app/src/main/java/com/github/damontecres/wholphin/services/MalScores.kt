package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** One saved MyAnimeList score: 0 to 10 (null = MAL has no score yet), and when it was looked up */
@Serializable
data class MalScoreEntry(
    val score: Double? = null,
    val at: Long = 0,
)

/** Benflix: MyAnimeList scores already looked up, by MAL id, so info lines can show them */
object MalFacts {
    private val _scores = MutableStateFlow<Map<Int, Double>>(emptyMap())
    val scores: StateFlow<Map<Int, Double>> = _scores

    internal fun add(found: Map<Int, Double>) {
        if (found.isNotEmpty()) _scores.update { it + found }
    }

    /** "7.1", like the star rating */
    fun text(score: Double): String = String.format(java.util.Locale.US, "%.1f", score)
}

/**
 * Benflix: MyAnimeList fan scores, from Jikan (api.jikan.moe), a free MyAnimeList reader that needs
 * no account. AniList already tells us each show's MyAnimeList number, so no matching is needed.
 *
 * Jikan allows about 60 requests a minute, so requests go one at a time, a little over a second
 * apart. Scores are saved on the device and looked up again after a week.
 */
@Singleton
class MalScores
    @Inject
    constructor(
        @param:StandardOkHttpClient private val http: OkHttpClient,
        private val diskCache: DiskCache,
    ) {
        private val json = Json { ignoreUnknownKeys = true }
        private val gate = Mutex()
        private val saved = ConcurrentHashMap<Int, MalScoreEntry>()

        @Volatile private var loaded = false

        @Volatile private var lastCall = 0L

        /** Looks up the scores not saved yet (or older than a week), and shows every one it has */
        suspend fun fetch(malIds: Collection<Int>) {
            val ids = malIds.filter { it > 0 }.distinct()
            if (ids.isEmpty()) return
            withContext(WholphinDispatchers.IO) { load() }
            gate.withLock {
                withContext(WholphinDispatchers.IO) {
                    val now = System.currentTimeMillis()
                    val todo = ids.filter { id -> saved[id]?.let { now - it.at > WEEK_MS } ?: true }
                    var sinceSave = 0
                    for (id in todo) {
                        val entry = lookUp(id) ?: continue
                        saved[id] = entry
                        entry.score?.let { MalFacts.add(mapOf(id to it)) }
                        if (++sinceSave >= SAVE_EVERY) {
                            save()
                            sinceSave = 0
                        }
                    }
                    if (sinceSave > 0) save()
                    MalFacts.add(ids.mapNotNull { id -> saved[id]?.score?.let { id to it } }.toMap())
                }
            }
        }

        @Synchronized
        private fun load() {
            if (loaded) return
            loaded = true
            diskCache
                .read(CACHE, MapSerializer(String.serializer(), MalScoreEntry.serializer()), Long.MAX_VALUE)
                ?.forEach { (k, v) -> k.toIntOrNull()?.let { saved[it] = v } }
            MalFacts.add(saved.mapNotNull { (id, e) -> e.score?.let { id to it } }.toMap())
        }

        private fun save() {
            diskCache.write(
                CACHE,
                MapSerializer(String.serializer(), MalScoreEntry.serializer()),
                saved.mapKeys { it.key.toString() },
            )
        }

        /** One show's score from Jikan; null when Jikan couldn't answer right now (tried again next time) */
        private suspend fun lookUp(malId: Int): MalScoreEntry? {
            repeat(MAX_TRIES) {
                val wait = lastCall + GAP_MS - System.currentTimeMillis()
                if (wait > 0) delay(wait)
                lastCall = System.currentTimeMillis()
                try {
                    val request =
                        Request
                            .Builder()
                            .url("$URL/anime/$malId")
                            .header("Accept", "application/json")
                            .build()
                    http.newCall(request).execute().use { response ->
                        when {
                            response.code == 429 -> {
                                // Too many questions: wait a bit, then try again
                                Timber.i("Jikan asked to slow down")
                                lastCall = System.currentTimeMillis() + SLOW_DOWN_MS
                            }

                            response.code == 404 -> {
                                return MalScoreEntry(null, System.currentTimeMillis())
                            }

                            response.isSuccessful -> {
                                val data = (json.parseToJsonElement(response.body.string()) as? JsonObject)?.get("data") as? JsonObject
                                val score = (data?.get("score") as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 }
                                return MalScoreEntry(score, System.currentTimeMillis())
                            }

                            else -> {
                                Timber.w("Jikan answered %s for %s", response.code, malId)
                                return null
                            }
                        }
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not get the MyAnimeList score of %s", malId)
                    return null
                }
            }
            return null
        }

        companion object {
            private const val URL = "https://api.jikan.moe/v4"
            private const val CACHE = "mal-scores"
            private const val GAP_MS = 1_200L
            private const val SLOW_DOWN_MS = 5_000L
            private const val MAX_TRIES = 3
            private const val SAVE_EVERY = 20
            private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
        }
    }
