package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benflix: the backup for the anime deep search. When AniList doesn't answer, the same genres and tags are
 * looked up on MyAnimeList (through Jikan, api.jikan.moe, free and no account), page by page.
 *
 * MyAnimeList has fewer tags than AniList (about 80 genres and themes), so a search with a tag MyAnimeList
 * doesn't know can't use the backup.
 */
@Singleton
class MalBrowse
    @Inject
    constructor(
        @param:StandardOkHttpClient private val http: OkHttpClient,
        private val diskCache: DiskCache,
    ) {
        private val json = Json { ignoreUnknownKeys = true }
        private val gate = Mutex()

        @Volatile private var lastCall = 0L

        @Volatile private var genreIds: Map<String, Int>? = null

        /**
         * One page of anime with every one of [picked] and none of [leftOut], or null when MyAnimeList
         * doesn't know one of those names (or couldn't be reached).
         */
        suspend fun browse(
            picked: List<String>,
            leftOut: List<String>,
            sort: AniListSort,
            kind: AniListKind,
            page: Int,
        ): AniListBrowsePage? {
            val ids = genres() ?: return null
            val want = picked.map { idFor(ids, it) ?: return null }
            val skip = leftOut.mapNotNull { idFor(ids, it) }
            val url =
                "$URL/anime"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("page", page.toString())
                    .addQueryParameter("limit", "25")
                    .addQueryParameter("sfw", "true")
                    .addQueryParameter("genres", want.joinToString(","))
                    .apply { if (skip.isNotEmpty()) addQueryParameter("genres_exclude", skip.joinToString(",")) }
                    .addQueryParameter(
                        "order_by",
                        when (sort) {
                            AniListSort.TOP -> "score"
                            AniListSort.NEWEST -> "start_date"
                            else -> "members"
                        },
                    ).addQueryParameter("sort", "desc")
                    .apply {
                        when (kind) {
                            AniListKind.SHOWS -> {
                                addQueryParameter("type", "tv")
                            }

                            AniListKind.MOVIES -> {
                                addQueryParameter("type", "movie")
                            }

                            AniListKind.ALL -> {}
                        }
                    }.build()
                    .toString()
            val body = get(url) ?: return null
            return pageOf(body)
        }

        /**
         * Benflix (anime Requests backup): one page of any Jikan list, like "seasons/now" or "top/anime",
         * with [params] added (sfw is always on, so nothing adult comes back).
         */
        suspend fun list(
            path: String,
            params: Map<String, String> = emptyMap(),
        ): AniListBrowsePage? {
            val url =
                "$URL/$path"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("sfw", "true")
                    .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
                    .build()
                    .toString()
            return pageOf(get(url) ?: return null)
        }

        private fun pageOf(body: JsonObject): AniListBrowsePage {
            val pagination = body["pagination"] as? JsonObject
            val items =
                (body["data"] as? JsonArray)
                    .orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .filter { m -> m.str("type") != "Music" && m.str("type") != "CM" && m.str("type") != "PV" }
                    .mapNotNull { m ->
                        val id = m.int("mal_id") ?: return@mapNotNull null
                        val titles =
                            (
                                listOfNotNull(m.str("title_english"), m.str("title"), m.str("title_japanese")) +
                                    (m["title_synonyms"] as? JsonArray).orEmpty().mapNotNull {
                                        (it as? JsonPrimitive)?.contentOrNull
                                    }
                            ).filter { it.isNotBlank() }.distinct()
                        val year =
                            m.int("year")
                                ?: ((m["aired"] as? JsonObject)?.get("prop") as? JsonObject)
                                    ?.let { (it["from"] as? JsonObject)?.int("year") }
                        AniListBrowseItem(
                            // Kept apart from AniList's numbers
                            id = -id,
                            titles = titles,
                            format = if (m.str("type") == "Movie") "MOVIE" else "TV",
                            year = year,
                            score = ((m["score"] as? JsonPrimitive)?.doubleOrNull)?.let { (it * 10).toInt() },
                            cover =
                                ((m["images"] as? JsonObject)?.get("jpg") as? JsonObject)?.let {
                                    it.str("large_image_url") ?: it.str("image_url")
                                },
                        )
                    }
            return AniListBrowsePage(
                items = items,
                hasMore = (pagination?.get("has_next_page") as? JsonPrimitive)?.contentOrNull == "true",
                total = ((pagination?.get("items") as? JsonObject)?.int("total")),
            )
        }

        /** MyAnimeList's genre, theme and audience names (lowercase) and their numbers; saved for a week */
        private suspend fun genres(): Map<String, Int>? {
            genreIds?.let { return it }
            val serializer = MapSerializer(String.serializer(), Int.serializer())
            diskCache.read(CACHE, serializer, WEEK_MS)?.let {
                genreIds = it
                return it
            }
            val found = mutableMapOf<String, Int>()
            // Explicit (grown-up) genres are left out on purpose
            for (filter in listOf("genres", "themes", "demographics")) {
                val body = get("$URL/genres/anime?filter=$filter") ?: continue
                (body["data"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.forEach { g ->
                    val name = g.str("name") ?: return@forEach
                    val id = g.int("mal_id") ?: return@forEach
                    found[name.lowercase()] = id
                }
            }
            if (found.isEmpty()) return diskCache.read(CACHE, serializer, Long.MAX_VALUE)?.also { genreIds = it }
            diskCache.write(CACHE, serializer, found)
            genreIds = found
            return found
        }

        private fun idFor(
            ids: Map<String, Int>,
            aniListName: String,
        ): Int? {
            val name = aniListName.lowercase()
            return ids[name] ?: ALIASES[name]?.let { ids[it] }
        }

        private suspend fun get(url: String): JsonObject? =
            gate.withLock {
                withContext(WholphinDispatchers.IO) {
                    repeat(MAX_TRIES) {
                        val wait = lastCall + GAP_MS - System.currentTimeMillis()
                        if (wait > 0) delay(wait)
                        lastCall = System.currentTimeMillis()
                        try {
                            val request =
                                Request
                                    .Builder()
                                    .url(url)
                                    .header("Accept", "application/json")
                                    .build()
                            http.newCall(request).execute().use { response ->
                                if (response.code == 429) {
                                    lastCall = System.currentTimeMillis() + SLOW_DOWN_MS
                                } else if (response.isSuccessful) {
                                    return@withContext json.parseToJsonElement(response.body.string()) as? JsonObject
                                } else {
                                    return@withContext null
                                }
                            }
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            Timber.w(ex, "MyAnimeList (Jikan) didn't answer")
                        }
                    }
                    null
                }
            }

        private companion object {
            const val URL = "https://api.jikan.moe/v4"
            const val CACHE = "mal-genre-ids"
            const val GAP_MS = 1_200L
            const val SLOW_DOWN_MS = 5_000L
            const val MAX_TRIES = 3
            const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

            /** AniList names that MyAnimeList spells differently (lowercase) */
            val ALIASES =
                mapOf(
                    "video games" to "video game",
                    "female harem" to "harem",
                    "male harem" to "reverse harem",
                    "mixed gender harem" to "harem",
                    "thriller" to "suspense",
                    "food" to "gourmet",
                    "idol" to "idols (female)",
                    "cute girls doing cute things" to "cgdct",
                    "gods" to "mythology",
                    "time manipulation" to "time travel",
                    "band" to "music",
                )

            fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull

            fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull
        }
    }
