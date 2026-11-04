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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** The person's AniList sign-in no longer works */
class AniListSignedOutException : Exception("AniList sign-in expired")

/** A voice actor, from AniList */
@Serializable
data class AniListVoice(
    val name: String,
    val image: String? = null,
)

/** A character with who voices them in Japanese and in English */
@Serializable
data class AniListCharacter(
    val name: String,
    val image: String? = null,
    val main: Boolean = false,
    val japanese: AniListVoice? = null,
    val english: AniListVoice? = null,
)

/** A movie or show linked to this one on AniList (sequel, movie, side story) */
@Serializable
data class AniListRelated(
    val id: Int,
    val relation: String,
    val format: String? = null,
    val titles: List<String> = emptyList(),
    val year: Int? = null,
    val month: Int? = null,
)

/** What Benflix shows from AniList on an anime's page */
@Serializable
data class AniListInfo(
    val id: Int,
    val idMal: Int? = null,
    /** AniList's average fan score, 0 to 100 */
    val score: Int? = null,
    val tags: List<String> = emptyList(),
    val characters: List<AniListCharacter> = emptyList(),
    /** The next episode coming out (from this entry or one of its sequels) */
    val nextEpisode: Int? = null,
    /** When it comes out, in seconds since 1970 */
    val nextAiringAt: Long? = null,
    /** The sequel the next episode belongs to, when it isn't this entry */
    val nextTitle: String? = null,
    val related: List<AniListRelated> = emptyList(),
    val year: Int? = null,
    val month: Int? = null,
)

/** One upcoming episode of a show on Benflix, from AniList's airing calendar */
@Serializable
data class AniListAiring(
    val mediaId: Int,
    val episode: Int,
    val airingAt: Long,
    val titles: List<String>,
)

/** One show's place in a library's AniList index: its AniList id (0 = not found) and fan genres */
@Serializable
data class AniListIndexEntry(
    val id: Int,
    val tags: List<String> = emptyList(),
    val score: Int? = null,
    /** When a show that wasn't found was last looked for, so it's tried again the next day */
    val checkedAt: Long = 0,
)

/** The bits of AniList a card or info line shows: the fan score (0 to 100) and fan genres */
data class AniListCardFacts(
    val score: Int?,
    val tags: List<String>,
) {
    /** "7.4", like the star rating */
    val scoreText: String? get() = score?.let { String.format(java.util.Locale.US, "%.1f", it / 10.0) }

    /** Up to [count] anime-only genres, like Isekai or Shounen, before plain ones like Comedy */
    fun cardTags(count: Int = 2): List<String> {
        val (plain, special) = tags.partition { it in PLAIN_GENRES }
        return (special + plain).take(count)
    }

    companion object {
        /** AniList genres every kind of show has; anime-only ones (Mecha, Slice of Life...) aren't here */
        private val PLAIN_GENRES =
            setOf(
                "Action",
                "Adventure",
                "Comedy",
                "Drama",
                "Fantasy",
                "Horror",
                "Music",
                "Mystery",
                "Psychological",
                "Romance",
                "Sci-Fi",
                "Sports",
                "Supernatural",
                "Thriller",
                "Ecchi",
            )
    }
}

/** Benflix: AniList facts already looked up, so pages can show them without waiting */
object AniListFacts {
    private val _info = MutableStateFlow<Map<UUID, AniListInfo>>(emptyMap())
    val info: StateFlow<Map<UUID, AniListInfo>> = _info

    /** Score and fan genres for every anime matched so far (from its page or its library), by Jellyfin id */
    private val _cards = MutableStateFlow<Map<UUID, AniListCardFacts>>(emptyMap())
    val cards: StateFlow<Map<UUID, AniListCardFacts>> = _cards

    internal fun add(
        id: UUID,
        info: AniListInfo,
    ) {
        _info.update { it + (id to info) }
        _cards.update { it + (id to AniListCardFacts(info.score, info.tags)) }
    }

    internal fun addIndex(entries: Map<UUID, AniListIndexEntry>) {
        val found =
            entries
                .filter { (_, e) -> e.id > 0 }
                .mapValues { (_, e) -> AniListCardFacts(e.score, e.tags) }
        if (found.isEmpty()) return
        // A show's own page (fuller facts) wins over its library's index
        _cards.update { now -> now + found.filterKeys { it !in _info.value } }
    }
}

/**
 * Benflix: anime facts from AniList (anilist.co), which is free and needs no account.
 *
 * AniList allows about 90 requests a minute (sometimes only 30), so requests wait their turn,
 * at most one a second, and back off when AniList says to slow down. Answers are saved
 * on the device so pages don't ask again for days. Whole libraries are matched 10 shows per
 * request, which also gives each show its fan genres (Shonen, Isekai...).
 */
@Singleton
class AniListService
    @Inject
    constructor(
        @param:StandardOkHttpClient private val http: OkHttpClient,
        private val diskCache: DiskCache,
    ) {
        private val gate = Mutex()

        @Volatile private var lastCall = 0L
        private val memory = ConcurrentHashMap<UUID, AniListInfo>()

        /** Jellyfin id -> AniList match, from every library index loaded so far */
        private val matches = ConcurrentHashMap<UUID, AniListIndexEntry>()
        private val indexes = ConcurrentHashMap<UUID, Map<UUID, AniListIndexEntry>>()
        private val indexLock = Mutex()
        private val json = Json { ignoreUnknownKeys = true }

        /** AniList's facts for an anime show or movie, or null if it can't be found */
        suspend fun infoFor(dto: BaseItemDto): AniListInfo? {
            memory[dto.id]?.let { return it }
            val cacheName = "anilist-${dto.id}"
            diskCache.read(cacheName, AniListInfo.serializer(), WEEK_MS)?.let { saved ->
                // Airing shows are checked again once their next episode is out
                val stale = saved.nextAiringAt != null && saved.nextAiringAt * 1000 < System.currentTimeMillis()
                if (!stale) {
                    remember(dto.id, saved)
                    return saved
                }
            }
            val found =
                try {
                    lookUp(dto)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not look up %s on AniList", dto.name)
                    return null
                } ?: return null
            remember(dto.id, found)
            diskCache.write(cacheName, AniListInfo.serializer(), found)
            return found
        }

        private fun remember(
            id: UUID,
            info: AniListInfo,
        ) {
            memory[id] = info
            AniListFacts.add(id, info)
        }

        private suspend fun lookUp(dto: BaseItemDto): AniListInfo? {
            val anilistId = providerId(dto, "AniList")
            val malId = providerId(dto, "MyAnimeList") ?: providerId(dto, "Mal")
            val variables =
                when {
                    anilistId != null -> {
                        vars { put("id", anilistId) }
                    }

                    malId != null -> {
                        vars { put("idMal", malId) }
                    }

                    else -> {
                        // The library index may have missed it (a odd name); search by every name it has
                        val id = matches[dto.id]?.id?.takeIf { it > 0 } ?: searchOne(dto) ?: return null
                        if (id <= 0) return null
                        vars { put("id", id) }
                    }
                }
            val media = query(MEDIA_QUERY, variables)?.obj("Media") ?: return null
            return parse(media)
        }

        private fun providerId(
            dto: BaseItemDto,
            key: String,
        ) = dto.providerIds
            ?.entries
            ?.firstOrNull { it.key.equals(key, true) }
            ?.value
            ?.toIntOrNull()

        private suspend fun searchOne(dto: BaseItemDto): Int? {
            val names = namesOf(dto)
            if (names.isEmpty()) return null
            val data = query(searchQuery(names.size), vars { names.forEachIndexed { i, n -> put("q$i", n) } }) ?: return null
            return pick(
                dto,
                names.indices.map {
                    data
                        .obj("s$it")
                        ?.obj("Page")
                        ?.arr("media")
                        .orEmpty()
                },
            )?.int("id")
        }

        private fun namesOf(dto: BaseItemDto) =
            listOfNotNull(dto.name, dto.originalTitle)
                .map { searchable(it) }
                .filter { it.isNotBlank() }
                .distinct()
                .take(2)

        /**
         * The right AniList entry among search results: same kind (movie or show), and the same
         * name, or a close name from the same year. Better no match than the wrong one.
         */
        private fun pick(
            dto: BaseItemDto,
            resultsPerName: List<List<JsonElement>>,
        ): JsonObject? {
            val movie = dto.type == BaseItemKind.MOVIE
            val year = dto.productionYear ?: dto.premiereDate?.year
            val wanted = namesOf(dto).map { clean(it) }.toSet()
            val results = resultsPerName.flatten().mapNotNull { it as? JsonObject }

            fun kindOk(m: JsonObject) = if (movie) m.str("format") == "MOVIE" else m.str("format") != "MOVIE"

            fun yearOk(m: JsonObject): Boolean {
                val y = m.obj("startDate")?.int("year") ?: return year == null
                return year == null || kotlin.math.abs(y - year) <= 1
            }

            fun names(m: JsonObject) = titlesOf(m).map { clean(it) }
            return results.firstOrNull { m -> kindOk(m) && yearOk(m) && names(m).any { it in wanted } }
                ?: results.firstOrNull { m ->
                    kindOk(m) && year != null && yearOk(m) &&
                        names(m).any { t -> wanted.any { w -> w.length >= 4 && (t.startsWith(w) || w.startsWith(t)) } }
                }
                // Same name, but Jellyfin's year is a bit off (a show that started late in the year): the closest one
                ?: results
                    .filter { m -> kindOk(m) && names(m).any { it in wanted } }
                    .minByOrNull { m ->
                        val y = m.obj("startDate")?.int("year")
                        if (year == null || y == null) 99 else kotlin.math.abs(y - year)
                    }?.takeIf { m ->
                        val y = m.obj("startDate")?.int("year")
                        year == null || y == null || kotlin.math.abs(y - year) <= 3
                    }
        }

        /**
         * Matches every show (or movie) in an anime library to AniList, 10 per request, and keeps
         * each one's fan genres. Saved for a week; only new titles are looked up after that.
         */
        suspend fun index(
            parentId: UUID,
            items: List<BaseItemDto>,
        ): Map<UUID, AniListIndexEntry> =
            indexLock.withLock {
                val cacheName = "anilist-index-$parentId"
                val saved =
                    indexes[parentId]
                        ?: diskCache
                            .read(cacheName, MapSerializer(String.serializer(), AniListIndexEntry.serializer()), WEEK_MS)
                            ?.mapNotNull { (k, v) -> runCatching { UUID.fromString(k) }.getOrNull()?.let { it to v } }
                            ?.toMap()
                            .orEmpty()
                val now = System.currentTimeMillis()
                val out = saved.toMutableMap()
                // New titles, plus ones not found before (tried again once a day: names and AniList change)
                val todo =
                    items.filter { item ->
                        val had = out[item.id]
                        had == null || (had.id <= 0 && now - had.checkedAt > DAY_MS)
                    }
                var changed = false
                try {
                    // 1. Shows Jellyfin already knows the AniList or MyAnimeList number of: asked for by number
                    val byAniList = todo.mapNotNull { dto -> providerId(dto, "AniList")?.let { dto to it } }
                    val byMal =
                        todo
                            .filter { dto -> byAniList.none { it.first.id == dto.id } }
                            .mapNotNull { dto -> (providerId(dto, "MyAnimeList") ?: providerId(dto, "Mal"))?.let { dto to it } }
                    for ((list, key) in listOf(byAniList to "id", byMal to "idMal")) {
                        list.chunked(ID_BATCH).forEach { batch ->
                            val media =
                                query(idsQuery(key), vars { put("ids", JsonArray(batch.map { JsonPrimitive(it.second) })) })
                                    ?.obj("Page")
                                    ?.arr("media")
                                    .orEmpty()
                                    .mapNotNull { it as? JsonObject }
                            batch.forEach { (dto, number) ->
                                media.firstOrNull { it.int(key) == number }?.let {
                                    out[dto.id] = entryOf(it)
                                    changed = true
                                }
                            }
                        }
                    }
                    // 2. The rest by name, 10 per request; then the ones still missing by their other name
                    val byName = todo.filter { (out[it.id]?.id ?: 0) <= 0 }
                    val missing = searchBatches(byName, out) { namesOf(it).firstOrNull() }
                    val (twoNames, oneName) = missing.partition { namesOf(it).size > 1 }
                    val stillMissing = searchBatches(twoNames, out) { dto -> namesOf(dto).getOrNull(1) }
                    (oneName + stillMissing).forEach { dto ->
                        out[dto.id] = AniListIndexEntry(0, checkedAt = now)
                    }
                    changed = changed || todo.isNotEmpty()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    // Keep what was found; the rest is tried next time
                    Timber.w(ex, "AniList index of %s stopped early", parentId)
                    changed = true
                }
                indexes[parentId] = out
                matches.putAll(out)
                AniListFacts.addIndex(out)
                if (changed) {
                    diskCache.write(
                        cacheName,
                        MapSerializer(String.serializer(), AniListIndexEntry.serializer()),
                        out.mapKeys { it.key.toString() },
                    )
                    Timber.i("AniList index of %s: %s titles, %s matched", parentId, out.size, out.count { it.value.id > 0 })
                }
                out
            }

        /**
         * Searches AniList for [items] by the name [nameOf] gives, 10 per request, saving matches in [out].
         * Returns the ones with that name that weren't found (titles without that name are left out).
         * A title AniList gave no answer for is left alone so it's tried next time.
         */
        private suspend fun searchBatches(
            items: List<BaseItemDto>,
            out: MutableMap<UUID, AniListIndexEntry>,
            nameOf: (BaseItemDto) -> String?,
        ): List<BaseItemDto> {
            val notFound = mutableListOf<BaseItemDto>()
            items
                .mapNotNull { dto -> nameOf(dto)?.let { dto to it } }
                .chunked(BATCH)
                .forEach { batch ->
                    val data =
                        query(searchQuery(batch.size), vars { batch.forEachIndexed { i, (_, n) -> put("q$i", n) } })
                            ?: return@forEach
                    batch.forEachIndexed { i, (dto, _) ->
                        val results = data.obj("s$i")?.obj("Page")?.arr("media") ?: return@forEachIndexed
                        val m = pick(dto, listOf(results))
                        if (m == null) notFound += dto else out[dto.id] = entryOf(m)
                    }
                }
            return notFound
        }

        private fun entryOf(m: JsonObject) =
            AniListIndexEntry(
                id = m.int("id") ?: 0,
                tags = tagsOf(m),
                score = m.int("averageScore") ?: m.int("meanScore"),
            )

        /** The index already built for a library this app run, if any */
        fun savedIndex(parentId: UUID): Map<UUID, AniListIndexEntry>? = indexes[parentId]

        private fun tagsOf(m: JsonObject): List<String> =
            (
                m.arr("genres").orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull } +
                    m
                        .arr("tags")
                        .orEmpty()
                        .mapNotNull { it as? JsonObject }
                        .filter {
                            (it.int("rank") ?: 0) >= MIN_TAG_RANK &&
                                it.bool("isMediaSpoiler") != true &&
                                it.bool("isGeneralSpoiler") != true &&
                                it.bool("isAdult") != true
                        }.mapNotNull { it.str("name") }
            ).filter { it !in HIDDEN_TAGS }
                .distinct()

        private fun parse(m: JsonObject): AniListInfo {
            val characters =
                m
                    .obj("characters")
                    ?.arr("edges")
                    .orEmpty()
                    .mapNotNull { e ->
                        val edge = e as? JsonObject ?: return@mapNotNull null
                        val node = edge.obj("node") ?: return@mapNotNull null
                        val name = node.obj("name")?.str("full") ?: return@mapNotNull null

                        fun voice(key: String) =
                            (edge.arr(key)?.firstOrNull() as? JsonObject)?.let { va ->
                                va.obj("name")?.str("full")?.let { AniListVoice(it, va.obj("image")?.str("medium")) }
                            }
                        AniListCharacter(
                            name = name,
                            image = node.obj("image")?.str("medium"),
                            main = edge.str("role") == "MAIN",
                            japanese = voice("japanese"),
                            english = voice("english"),
                        )
                    }
            // The next episode may belong to a later season, which AniList keeps as a sequel
            val airing = nextAiring(m, depth = 0)
            return AniListInfo(
                id = m.int("id") ?: 0,
                idMal = m.int("idMal"),
                score = m.int("averageScore") ?: m.int("meanScore"),
                tags = tagsOf(m),
                characters = characters,
                nextEpisode = airing?.second?.int("episode"),
                nextAiringAt = airing?.second?.long("airingAt"),
                nextTitle = airing?.first?.takeIf { it !== m }?.let { titlesOf(it).firstOrNull() },
                related = relations(m),
                year = m.obj("startDate")?.int("year"),
                month = m.obj("startDate")?.int("month"),
            )
        }

        private fun nextAiring(
            m: JsonObject,
            depth: Int,
        ): Pair<JsonObject, JsonObject>? {
            m.obj("nextAiringEpisode")?.let { return m to it }
            if (depth >= 2) return null
            return m
                .obj("relations")
                ?.arr("edges")
                .orEmpty()
                .mapNotNull { it as? JsonObject }
                .filter { it.str("relationType") == "SEQUEL" }
                .firstNotNullOfOrNull { e -> e.obj("node")?.let { nextAiring(it, depth + 1) } }
        }

        private fun relations(m: JsonObject): List<AniListRelated> =
            m
                .obj("relations")
                ?.arr("edges")
                .orEmpty()
                .mapNotNull { e ->
                    val edge = e as? JsonObject ?: return@mapNotNull null
                    val node = edge.obj("node") ?: return@mapNotNull null
                    if (node.str("type") != "ANIME") return@mapNotNull null
                    AniListRelated(
                        id = node.int("id") ?: return@mapNotNull null,
                        relation = edge.str("relationType") ?: "OTHER",
                        format = node.str("format"),
                        titles = titlesOf(node),
                        year = node.obj("startDate")?.int("year"),
                        month = node.obj("startDate")?.int("month"),
                    )
                }

        /** Episodes coming out from a few hours ago to 7 days from now, across all anime (saved 3 hours) */
        suspend fun thisWeek(): List<AniListAiring> {
            val cacheName = "anilist-week"
            diskCache.read(cacheName, ListSerializer(AniListAiring.serializer()), WEEK_CACHE_MS)?.let { return it }
            val now = System.currentTimeMillis() / 1000
            val out = mutableListOf<AniListAiring>()
            try {
                for (page in 1..MAX_WEEK_PAGES) {
                    val data =
                        query(
                            WEEK_QUERY,
                            vars {
                                put("page", page)
                                put("from", now - 6 * 3600)
                                put("to", now + 7 * 24 * 3600)
                            },
                        )?.obj("Page") ?: break
                    data.arr("airingSchedules").orEmpty().forEach { s ->
                        val o = s as? JsonObject ?: return@forEach
                        val media = o.obj("media") ?: return@forEach
                        if (media.bool("isAdult") == true) return@forEach
                        out +=
                            AniListAiring(
                                mediaId = media.int("id") ?: return@forEach,
                                episode = o.int("episode") ?: return@forEach,
                                airingAt = o.long("airingAt") ?: return@forEach,
                                titles = titlesOf(media),
                            )
                    }
                    if (data.obj("pageInfo")?.bool("hasNextPage") != true) break
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not get this week's anime from AniList")
            }
            if (out.isNotEmpty()) diskCache.write(cacheName, ListSerializer(AniListAiring.serializer()), out)
            return out
        }

        /** AniList's next season of a show (its TV sequel) and how many episodes an entry has */
        internal suspend fun sequelOf(id: Int): Pair<Int?, Int?> {
            val cacheName = "anilist-sequel-$id"
            diskCache.read(cacheName, ListSerializer(Int.serializer()), WEEK_MS)?.let { saved ->
                return saved.getOrNull(0)?.takeIf { it > 0 } to saved.getOrNull(1)?.takeIf { it > 0 }
            }
            val m = query(SEQUEL_QUERY, vars { put("id", id) })?.obj("Media") ?: return null to null
            val episodes = m.int("episodes")
            val sequel =
                m
                    .obj("relations")
                    ?.arr("edges")
                    .orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .firstOrNull { e ->
                        e.str("relationType") == "SEQUEL" && e.obj("node")?.str("format") in SEASON_FORMATS
                    }?.obj("node")
                    ?.int("id")
            // Still-airing shows can get their episode count later, so only save finished ones for long
            if (episodes != null) diskCache.write(cacheName, ListSerializer(Int.serializer()), listOf(sequel ?: 0, episodes))
            return sequel to episodes
        }

        // --- talking to AniList ---

        private fun vars(build: JsonObjectBuilder.() -> Unit) = buildJsonObject(build)

        /** A question that needs the person's AniList sign-in (their list) */
        internal suspend fun signedInQuery(
            text: String,
            variables: JsonObject,
            token: String,
        ): JsonObject? = query(text, variables, token)

        /** Sends one question to AniList, waiting its turn; null if AniList had no answer */
        private suspend fun query(
            text: String,
            variables: JsonObject,
            token: String? = null,
        ): JsonObject? =
            gate.withLock {
                withContext(WholphinDispatchers.IO) {
                    var result: JsonObject? = null
                    var tries = 0
                    while (result == null && tries < MAX_TRIES) {
                        tries++
                        val wait = lastCall + MIN_GAP_MS - System.currentTimeMillis()
                        if (wait > 0) delay(wait)
                        lastCall = System.currentTimeMillis()
                        val body =
                            buildJsonObject {
                                put("query", text)
                                put("variables", variables)
                            }.toString().toRequestBody("application/json".toMediaType())
                        val request =
                            Request
                                .Builder()
                                .url(URL)
                                .header("Accept", "application/json")
                                .apply { if (token != null) header("Authorization", "Bearer $token") }
                                .post(body)
                                .build()
                        val throttled =
                            http.newCall(request).execute().use { response ->
                                if (response.code == 429) {
                                    // Too many questions: wait as long as AniList asks, then try again
                                    val seconds = (response.header("Retry-After")?.toLongOrNull() ?: 60).coerceIn(1, 90)
                                    Timber.i("AniList asked to slow down for %ss", seconds)
                                    lastCall = System.currentTimeMillis() + seconds * 1000 - MIN_GAP_MS
                                    true
                                } else {
                                    val answer = response.body.string()
                                    if (response.code == 401 || (response.code == 400 && token != null && "Invalid token" in answer)) {
                                        throw AniListSignedOutException()
                                    }
                                    if (!response.isSuccessful && response.code != 404) {
                                        throw IllegalStateException("AniList answered ${response.code}")
                                    }
                                    result =
                                        (json.parseToJsonElement(answer) as? JsonObject)?.obj("data") ?: JsonObject(emptyMap())
                                    false
                                }
                            }
                        if (!throttled) break
                    }
                    result
                }
            }

        companion object {
            private const val URL = "https://graphql.anilist.co"
            private const val MIN_GAP_MS = 1_000L
            private const val MAX_TRIES = 3
            private const val BATCH = 10
            private const val ID_BATCH = 50

            /** AniList genres that say nothing on an anime page */
            private val HIDDEN_TAGS =
                setOf("Anime Influenced", "Primarily Adult Cast", "Primarily Teen Cast", "Male Protagonist", "Female Protagonist")
            private const val MIN_TAG_RANK = 60
            private const val MAX_WEEK_PAGES = 6
            private const val DAY_MS = 24L * 60 * 60 * 1000
            private const val WEEK_MS = 7 * DAY_MS
            private const val WEEK_CACHE_MS = 3L * 60 * 60 * 1000

            private const val TITLE = "title { english romaji native } synonyms"

            private val MEDIA_QUERY =
                """
                query (${'$'}id: Int, ${'$'}idMal: Int) {
                  Media(id: ${'$'}id, idMal: ${'$'}idMal, type: ANIME) {
                    id idMal format averageScore meanScore status genres $TITLE
                    startDate { year month }
                    tags { name rank isMediaSpoiler isGeneralSpoiler isAdult }
                    nextAiringEpisode { episode airingAt }
                    characters(sort: [ROLE, RELEVANCE, ID], perPage: 16) {
                      edges {
                        role
                        node { name { full } image { medium } }
                        japanese: voiceActors(language: JAPANESE, sort: [RELEVANCE, ID]) { name { full } image { medium } }
                        english: voiceActors(language: ENGLISH, sort: [RELEVANCE, ID]) { name { full } image { medium } }
                      }
                    }
                    relations {
                      edges {
                        relationType
                        node {
                          id type format status $TITLE startDate { year month }
                          nextAiringEpisode { episode airingAt }
                          relations {
                            edges { relationType node { id type format $TITLE nextAiringEpisode { episode airingAt } } }
                          }
                        }
                      }
                    }
                  }
                }
                """.trimIndent()

            /** Up to 10 searches in one request: s0, s1... each with its own name q0, q1... */
            private fun searchQuery(count: Int): String {
                val params = (0 until count).joinToString(", ") { "${'$'}q$it: String" }
                val parts =
                    (0 until count).joinToString("\n") {
                        "s$it: Page(perPage: 5) { media(search: ${'$'}q$it, type: ANIME, isAdult: false, sort: SEARCH_MATCH) " +
                            "{ id format averageScore meanScore genres $TITLE startDate { year } " +
                            "tags { name rank isMediaSpoiler isGeneralSpoiler isAdult } } }"
                    }
                return "query ($params) {\n$parts\n}"
            }

            /** Up to 50 shows by their AniList ("id") or MyAnimeList ("idMal") numbers */
            private fun idsQuery(key: String): String =
                "query (${'$'}ids: [Int]) { Page(perPage: $ID_BATCH) { media(${key}_in: ${'$'}ids, type: ANIME) " +
                    "{ id idMal format averageScore meanScore genres $TITLE startDate { year } " +
                    "tags { name rank isMediaSpoiler isGeneralSpoiler isAdult } } } }"

            /** A name AniList's search understands: curly quotes made plain, extra spaces gone */
            internal fun searchable(name: String): String =
                name
                    .replace('\u2019', '\'')
                    .replace('\u2018', '\'')
                    .replace('\u201C', '"')
                    .replace('\u201D', '"')
                    .replace(Regex("\\s+"), " ")
                    .trim()

            private val SEASON_FORMATS = setOf("TV", "TV_SHORT", "ONA")

            private val SEQUEL_QUERY =
                """
                query (${'$'}id: Int) {
                  Media(id: ${'$'}id, type: ANIME) {
                    id episodes
                    relations { edges { relationType node { id format } } }
                  }
                }
                """.trimIndent()

            private val WEEK_QUERY =
                """
                query (${'$'}page: Int, ${'$'}from: Int, ${'$'}to: Int) {
                  Page(page: ${'$'}page, perPage: 50) {
                    pageInfo { hasNextPage }
                    airingSchedules(airingAt_greater: ${'$'}from, airingAt_lesser: ${'$'}to, sort: TIME) {
                      episode airingAt
                      media { id isAdult $TITLE }
                    }
                  }
                }
                """.trimIndent()

            /** All the names AniList knows a show by */
            internal fun titlesOf(m: JsonObject): List<String> =
                (
                    listOfNotNull(
                        m.obj("title")?.str("english"),
                        m.obj("title")?.str("romaji"),
                        m.obj("title")?.str("native"),
                    ) + m.arr("synonyms").orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ).filter { it.isNotBlank() }
                    .distinct()

            /** A name with only letters and numbers, for comparing */
            fun clean(name: String): String = name.lowercase().replace("&", "and").filter { it.isLetterOrDigit() }

            private fun JsonObject.obj(key: String) = (this[key] as? JsonObject)

            private fun JsonObject.arr(key: String) = (this[key] as? JsonArray)

            private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull

            private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull

            private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.longOrNull

            private fun JsonObject.bool(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
        }
    }
