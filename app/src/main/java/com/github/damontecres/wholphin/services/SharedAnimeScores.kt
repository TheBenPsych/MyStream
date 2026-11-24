package com.github.damontecres.wholphin.services

import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Glowfin (Ben 2026-11-24, "AniList rate limits quickly"): anime scores shared through Tower. The first TV, phone
 * or browser that looks a title up hands its AniList match and score, and the MyAnimeList score, to Tower; every
 * other device reads them from there instead of asking AniList and Jikan again. Tower keeps them 30 days.
 * When Tower can't be reached, each device simply looks things up itself, as before.
 */
@Singleton
class SharedAnimeScores
    @Inject
    constructor(
        private val helper: GameLibraryClient,
    ) {
        /** What Tower has: AniList matches by Jellyfin id, and MyAnimeList scores by MAL id */
        data class Table(
            val matches: Map<UUID, AniListIndexEntry>,
            val mal: Map<Int, MalScoreEntry>,
        )

        suspend fun get(): Table? =
            try {
                val answer = helper.getJson(PATH)
                val matches = mutableMapOf<UUID, AniListIndexEntry>()
                answer.optJSONObject("jf")?.let { jf ->
                    jf.keys().forEach { key ->
                        val id = uuidOf(key) ?: return@forEach
                        val v = jf.optJSONObject(key) ?: return@forEach
                        val al = v.optInt("al", 0).takeIf { it > 0 } ?: return@forEach
                        val tags =
                            v.optJSONArray("t")?.let { a ->
                                (0 until a.length()).mapNotNull { a.optString(it).takeIf { t -> t.isNotBlank() } }
                            }
                        matches[id] =
                            AniListIndexEntry(
                                id = al,
                                tags = tags.orEmpty(),
                                score = if (v.isNull("s")) null else v.optInt("s"),
                                malId = if (v.isNull("mal")) null else v.optInt("mal"),
                            )
                    }
                }
                val mal = mutableMapOf<Int, MalScoreEntry>()
                answer.optJSONObject("mal")?.let { m ->
                    m.keys().forEach { key ->
                        val id = key.toIntOrNull() ?: return@forEach
                        val v = m.optJSONObject(key) ?: return@forEach
                        val at = (v.optDouble("at", 0.0) * 1000).toLong()
                        mal[id] = MalScoreEntry(if (v.isNull("s")) null else v.optDouble("s").takeIf { !it.isNaN() && it > 0 }, at)
                    }
                }
                Table(matches, mal)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.i("Tower has no shared anime scores right now: %s", ex.message)
                null
            }

        /** Hands Tower the matches and scores it didn't have yet, a few hundred at a time */
        suspend fun add(
            matches: Map<UUID, AniListIndexEntry>,
            mal: Map<Int, MalScoreEntry>,
        ) {
            val found = matches.filter { it.value.id > 0 }.entries.toList()
            val scores = mal.entries.filter { it.value.at > 0 }
            if (found.isEmpty() && scores.isEmpty()) return
            try {
                found.chunked(BATCH).forEach { batch ->
                    val jf = JSONObject()
                    batch.forEach { (id, e) ->
                        jf.put(
                            id.toString(),
                            JSONObject()
                                .put("al", e.id)
                                .put("s", e.score ?: JSONObject.NULL)
                                .put("mal", e.malId ?: JSONObject.NULL)
                                .put("t", JSONArray(e.tags.take(8))),
                        )
                    }
                    helper.postJson(PATH, JSONObject().put("jf", jf))
                }
                scores.chunked(BATCH).forEach { batch ->
                    val m = JSONObject()
                    batch.forEach { (id, e) -> m.put(id.toString(), JSONObject().put("s", e.score ?: JSONObject.NULL)) }
                    helper.postJson(PATH, JSONObject().put("mal", m))
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.i("Could not share anime scores with Tower: %s", ex.message)
            }
        }

        private fun uuidOf(key: String): UUID? {
            val hex = key.replace("-", "")
            if (hex.length != 32) return null
            return runCatching {
                UUID.fromString(
                    "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}",
                )
            }.getOrNull()
        }

        companion object {
            private const val PATH = "/api/anime/scores"
            private const val BATCH = 200
        }
    }
