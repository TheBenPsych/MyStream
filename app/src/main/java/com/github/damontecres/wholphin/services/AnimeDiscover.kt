package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Benflix (anime Requests): one anime on a poster wall, with what the poster shows */
@Serializable
data class AnimeCard(
    /** AniList's number (below 0 = it came from MyAnimeList, the number is MyAnimeList's) */
    val id: Int,
    val idMal: Int? = null,
    val titles: List<String>,
    val format: String? = null,
    val year: Int? = null,
    /** AniList's fan score, 0 to 100 */
    val score: Int? = null,
    /** How many AniList members have it on a list */
    val popularity: Int? = null,
    /** Episodes in all, when known */
    val episodes: Int? = null,
    /** Airing now: the episode coming out next */
    val nextEpisode: Int? = null,
    val status: String? = null,
    val cover: String? = null,
    val genres: List<String> = emptyList(),
    /** A second (third...) season of a show */
    val later: Boolean = false,
) {
    val title: String get() = titles.firstOrNull() ?: ""
    val isMovie: Boolean get() = format == "MOVIE"
    val malId: Int? get() = idMal ?: if (id < 0) -id else null

    /** "Ep 6 of 12" while it's airing */
    val airingLine: String?
        get() {
            if (status != "RELEASING") return null
            val out = (nextEpisode ?: return null) - 1
            if (out < 1) return null
            return if (episodes != null && episodes > 0) "Ep $out of $episodes" else "Ep $out out"
        }

    /** "8.4", like the star rating */
    val scoreText: String? get() = score?.let { String.format(java.util.Locale.US, "%.1f", it / 10.0) }
}

/** Benflix (anime Requests): one new episode on the This week calendar */
@Serializable
data class AnimeAiringCard(
    val card: AnimeCard,
    val episode: Int,
    /** Seconds since 1970 */
    val airingAt: Long,
)

/** The four anime seasons, in AniList's names */
enum class AnimeQuarter(
    val label: String,
) {
    WINTER("Winter"),
    SPRING("Spring"),
    SUMMER("Summer"),
    FALL("Fall"),
    ;

    companion object {
        fun of(date: LocalDate): AnimeQuarter =
            when (date.monthValue) {
                12, 1, 2 -> WINTER
                in 3..5 -> SPRING
                in 6..8 -> SUMMER
                else -> FALL
            }

        /** The season [date] is in, and its year (December counts as next year's Winter) */
        fun yearOf(date: LocalDate): Int = if (date.monthValue == 12) date.year + 1 else date.year
    }
}

/** A season and its year, like "Fall 2026" */
data class AnimeSeasonPick(
    val season: AnimeQuarter,
    val year: Int,
) {
    val label: String get() = "${season.label} $year"

    fun next(): AnimeSeasonPick =
        if (season == AnimeQuarter.FALL) {
            AnimeSeasonPick(AnimeQuarter.WINTER, year + 1)
        } else {
            AnimeSeasonPick(AnimeQuarter.entries[season.ordinal + 1], year)
        }

    fun previous(): AnimeSeasonPick =
        if (season == AnimeQuarter.WINTER) {
            AnimeSeasonPick(AnimeQuarter.FALL, year - 1)
        } else {
            AnimeSeasonPick(AnimeQuarter.entries[season.ordinal - 1], year)
        }

    companion object {
        fun now(today: LocalDate = LocalDate.now()) = AnimeSeasonPick(AnimeQuarter.of(today), AnimeQuarter.yearOf(today))
    }
}

/**
 * Benflix (anime Requests): a hand-picked "feel", made of AniList genres and tags. Every anime must have
 * all of [genres] and [tags], and none of [noGenres] or [noTags]. Ecchi is never inside a feel (Ben).
 */
data class AnimeFeel(
    val name: String,
    val line: String,
    /** Tile colour, as 0xRRGGBB */
    val color: Long,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val noGenres: List<String> = emptyList(),
    val noTags: List<String> = emptyList(),
) {
    /** "Slice of Life + Iyashikei · no Horror, no Gore" */
    val recipe: String
        get() {
            val must = (genres + tags).joinToString(" + ")
            val not = (noGenres + noTags).filter { it != "Ecchi" }
            return if (not.isEmpty()) must else "$must · " + not.joinToString(", ") { "no $it" }
        }
}

/** The feels on the Find by feel wall (Ben approved these in the anime Requests sheet) */
val ANIME_FEELS =
    listOf(
        AnimeFeel("Action", "Fights, powers", 0xFF6F91, genres = listOf("Action")),
        AnimeFeel("Isekai", "Sent to another world", 0xB69CFF, tags = listOf("Isekai")),
        AnimeFeel(
            "Cosy",
            "Calm, slice of life",
            0xB9E36A,
            genres = listOf("Slice of Life"),
            tags = listOf("Iyashikei"),
            noGenres = listOf("Horror"),
            noTags = listOf("Gore"),
        ),
        AnimeFeel("Romance", "Love stories", 0xFF8FB1, genres = listOf("Romance")),
        AnimeFeel("Sports", "Teams and matches", 0xFFB547, genres = listOf("Sports")),
        AnimeFeel("Mecha", "Giant robots", 0x7AA7FF, genres = listOf("Mecha")),
        AnimeFeel("Mystery", "Puzzles, twists", 0x3DD6C6, genres = listOf("Mystery")),
        AnimeFeel("Horror", "Creepy, dark", 0x9B7B7B, genres = listOf("Horror")),
        AnimeFeel("Comedy", "Just funny", 0xF2D65B, genres = listOf("Comedy")),
        AnimeFeel("Fantasy", "Magic, quests", 0x8FB8FF, genres = listOf("Fantasy")),
    )

/**
 * Benflix: the lists on the Requests > Anime tab (This season, Top 10, Hidden gems, Finished and dubbed,
 * Movies, Coming next season, each feel, and the This week calendar). AniList first; when AniList doesn't
 * answer, MyAnimeList (through Jikan) fills in where it can. First seasons only (a request page offers the
 * rest), adult titles never. Each list is saved on the device for a few hours.
 *
 * Dubs come from the MAL-Dubs project's public list of anime with an English dub (by MyAnimeList number).
 */
@Singleton
class AnimeDiscover
    @Inject
    constructor(
        private val aniList: AniListService,
        private val malBrowse: MalBrowse,
        private val diskCache: DiskCache,
        @param:StandardOkHttpClient private val http: OkHttpClient,
    ) {
        private val json = Json { ignoreUnknownKeys = true }
        private val dubLock = Mutex()

        @Volatile private var dubs: Set<Int>? = null

        /** What one list asks AniList for */
        data class Ask(
            val key: String,
            val sort: String = "POPULARITY_DESC",
            val season: AnimeSeasonPick? = null,
            val status: String? = null,
            val formats: List<String>? = null,
            val genres: List<String> = emptyList(),
            val tags: List<String> = emptyList(),
            val noGenres: List<String> = emptyList(),
            val noTags: List<String> = emptyList(),
            val scoreOver: Int? = null,
            val popularityUnder: Int? = null,
            val popularityOver: Int? = null,
            val pages: Int = 2,
            /** Keep later seasons too (only This season does, so a new season of a show still shows) */
            val keepLater: Boolean = false,
            /** MyAnimeList list to use when AniList doesn't answer (path, extra settings) */
            val backup: Pair<String, Map<String, String>>? = null,
        )

        fun seasonAsk(pick: AnimeSeasonPick) =
            Ask(
                key = "season-${pick.season}-${pick.year}",
                season = pick,
                formats = SHOW_FORMATS,
                backup = "seasons/${pick.year}/${pick.season.name.lowercase()}" to emptyMap(),
            )

        val topAsk =
            Ask(
                key = "top-week",
                sort = "TRENDING_DESC",
                formats = SHOW_FORMATS + "MOVIE",
                pages = 1,
                backup = "top/anime" to mapOf("filter" to "airing"),
            )

        /** Great scores (80 and up) that few people know (under 50,000 AniList members) */
        val gemsAsk =
            Ask(
                key = "gems",
                sort = "SCORE_DESC",
                formats = SHOW_FORMATS + "MOVIE",
                scoreOver = 79,
                popularityUnder = 50_000,
                popularityOver = 2_000,
            )

        val finishedAsk =
            Ask(key = "finished", status = "FINISHED", formats = SHOW_FORMATS, pages = 4)

        val moviesAsk =
            Ask(
                key = "movies",
                formats = listOf("MOVIE"),
                backup = "top/anime" to mapOf("type" to "movie", "filter" to "bypopularity"),
            )

        fun nextSeasonAsk() =
            AnimeSeasonPick.now().next().let { pick ->
                Ask(
                    key = "next-${pick.season}-${pick.year}",
                    season = pick,
                    formats = SHOW_FORMATS + "MOVIE",
                    backup = "seasons/upcoming" to emptyMap(),
                )
            }

        fun feelAsk(
            feel: AnimeFeel,
            catalog: List<AniListTagChoice>,
        ): Ask? {
            // Tags AniList no longer has are dropped; a feel that needs one is hidden (lesson from Harem)
            val tags = feel.tags.map { AniListService.resolveName(it, catalog) ?: if (catalog.isEmpty()) it else return null }
            val noTags = feel.noTags.mapNotNull { AniListService.resolveName(it, catalog) ?: it.takeIf { catalog.isEmpty() } }
            return Ask(
                key = "feel-${feel.name}",
                genres = feel.genres,
                tags = tags,
                noGenres = (feel.noGenres + "Ecchi").distinct(),
                noTags = noTags,
                formats = SHOW_FORMATS + "MOVIE",
            )
        }

        /** One list, from the device when it was saved in the last few hours */
        suspend fun list(ask: Ask): List<AnimeCard> {
            val cacheName = "anime-req-${ask.key}"
            val serializer = ListSerializer(AnimeCard.serializer())
            diskCache.read(cacheName, serializer, LIST_CACHE_MS)?.let { return it }
            val found =
                try {
                    fromAniList(ask)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "AniList list %s failed, trying MyAnimeList", ask.key)
                    fromMal(ask) ?: throw ex
                }
            if (found.isNotEmpty()) diskCache.write(cacheName, serializer, found)
            return found
        }

        private suspend fun fromAniList(ask: Ask): List<AnimeCard> {
            val out = mutableListOf<AnimeCard>()
            for (page in 1..ask.pages) {
                val data =
                    aniList
                        .ask(
                            DISCOVER_QUERY,
                            vars {
                                put("page", page)
                                put("sort", strings(listOf(ask.sort)))
                                ask.season?.let {
                                    put("season", it.season.name)
                                    put("seasonYear", it.year)
                                }
                                ask.status?.let { put("status", it) }
                                ask.formats?.let { put("formats", strings(it)) }
                                if (ask.genres.isNotEmpty()) put("genres", strings(ask.genres))
                                if (ask.tags.isNotEmpty()) put("tags", strings(ask.tags))
                                put("noGenres", strings((ask.noGenres + "Hentai").distinct()))
                                if (ask.noTags.isNotEmpty()) put("noTags", strings(ask.noTags))
                                ask.scoreOver?.let { put("scoreOver", it) }
                                ask.popularityUnder?.let { put("popUnder", it) }
                                ask.popularityOver?.let { put("popOver", it) }
                            },
                        )?.obj("Page") ?: throw IllegalStateException("AniList had no answer")
                out +=
                    data
                        .arr("media")
                        .orEmpty()
                        .mapNotNull { (it as? JsonObject)?.let(::cardOf) }
                        .filter { ask.keepLater || !it.later }
                if (data.obj("pageInfo")?.bool("hasNextPage") != true) break
            }
            return out.distinctBy { it.id }
        }

        private suspend fun fromMal(ask: Ask): List<AnimeCard>? {
            val page =
                if (ask.backup != null) {
                    malBrowse.list(ask.backup.first, ask.backup.second)
                } else if (ask.genres.isNotEmpty() || ask.tags.isNotEmpty()) {
                    malBrowse.browse(
                        ask.genres + ask.tags,
                        ask.noGenres + ask.noTags,
                        AniListSort.POPULAR,
                        AniListKind.ALL,
                        1,
                    )
                } else {
                    null
                }
            return page?.items?.map { b ->
                AnimeCard(id = b.id, titles = b.titles, format = b.format, year = b.year, score = b.score, cover = b.cover)
            }
        }

        /** New episodes from now to 7 days ahead, across all anime (saved for 3 hours) */
        suspend fun thisWeek(): List<AnimeAiringCard> {
            val cacheName = "anime-req-week"
            val serializer = ListSerializer(AnimeAiringCard.serializer())
            diskCache.read(cacheName, serializer, WEEK_CACHE_MS)?.let { return it }
            val now = System.currentTimeMillis() / 1000
            val out = mutableListOf<AnimeAiringCard>()
            for (page in 1..MAX_WEEK_PAGES) {
                val data =
                    aniList
                        .ask(
                            WEEK_QUERY,
                            vars {
                                put("page", page)
                                put("from", now - 12 * 3600)
                                put("to", now + 7 * 24 * 3600)
                            },
                        )?.obj("Page") ?: if (page == 1) throw IllegalStateException("AniList had no answer") else break
                data.arr("airingSchedules").orEmpty().forEach { s ->
                    val o = s as? JsonObject ?: return@forEach
                    val card = o.obj("media")?.let(::cardOf) ?: return@forEach
                    out +=
                        AnimeAiringCard(
                            card = card,
                            episode = o.int("episode") ?: return@forEach,
                            airingAt = o.long("airingAt") ?: return@forEach,
                        )
                }
                if (data.obj("pageInfo")?.bool("hasNextPage") != true) break
            }
            if (out.isNotEmpty()) diskCache.write(cacheName, serializer, out)
            return out
        }

        /** MyAnimeList numbers of anime with an English dub (MAL-Dubs list, saved a day); empty if it can't be had */
        suspend fun dubbed(): Set<Int> =
            dubLock.withLock {
                dubs?.let { return@withLock it }
                val serializer = ListSerializer(Int.serializer())
                diskCache.read(DUB_CACHE, serializer, DAY_MS)?.let { saved ->
                    return@withLock saved.toSet().also { dubs = it }
                }
                val fetched =
                    try {
                        withContext(WholphinDispatchers.IO) {
                            http
                                .newCall(Request.Builder().url(DUB_URL).build())
                                .execute()
                                .use { r ->
                                    if (!r.isSuccessful) return@use null
                                    (json.parseToJsonElement(r.body.string()) as? JsonObject)
                                        ?.arr("dubbed")
                                        ?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
                                }
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not get the English dub list")
                        null
                    }
                if (!fetched.isNullOrEmpty()) {
                    diskCache.write(DUB_CACHE, serializer, fetched)
                    return@withLock fetched.toSet().also { dubs = it }
                }
                // Couldn't fetch: an older saved list is better than nothing
                diskCache.read(DUB_CACHE, serializer, Long.MAX_VALUE)?.toSet().orEmpty()
            }

        private fun cardOf(m: JsonObject): AnimeCard? {
            if (m.bool("isAdult") == true) return null
            val genres = m.arr("genres").orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            if ("Hentai" in genres) return null
            val format = m.str("format")
            return AnimeCard(
                id = m.int("id") ?: return null,
                idMal = m.int("idMal"),
                titles = AniListService.titlesOf(m),
                format = format,
                year = m.obj("startDate")?.int("year") ?: m.int("seasonYear"),
                score = m.int("averageScore") ?: m.int("meanScore"),
                popularity = m.int("popularity"),
                episodes = m.int("episodes"),
                nextEpisode = m.obj("nextAiringEpisode")?.int("episode"),
                status = m.str("status"),
                cover = m.obj("coverImage")?.let { it.str("large") ?: it.str("medium") },
                genres = genres,
                later =
                    format in SHOW_FORMATS &&
                        m
                            .obj("relations")
                            ?.arr("edges")
                            .orEmpty()
                            .mapNotNull { it as? JsonObject }
                            .any { e -> e.str("relationType") == "PREQUEL" && e.obj("node")?.str("format") in SHOW_FORMATS },
            )
        }

        private fun vars(build: JsonObjectBuilder.() -> Unit) = buildJsonObject(build)

        private fun strings(list: List<String>) = JsonArray(list.map { JsonPrimitive(it) })

        private fun JsonObject.obj(key: String) = (this[key] as? JsonObject)

        private fun JsonObject.arr(key: String) = (this[key] as? JsonArray)

        private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull

        private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull

        private fun JsonObject.long(key: String) = (this[key] as? JsonPrimitive)?.longOrNull

        private fun JsonObject.bool(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

        companion object {
            val SHOW_FORMATS = listOf("TV", "TV_SHORT", "ONA")
            private const val DAY_MS = 24L * 60 * 60 * 1000
            private const val LIST_CACHE_MS = 6L * 60 * 60 * 1000
            private const val WEEK_CACHE_MS = 3L * 60 * 60 * 1000
            private const val MAX_WEEK_PAGES = 8
            private const val DUB_CACHE = "anime-dub-list"
            private const val DUB_URL = "https://raw.githubusercontent.com/MAL-Dubs/MAL-Dubs/main/data/dubInfo.json"

            private const val CARD =
                "id idMal format status episodes popularity averageScore meanScore isAdult genres seasonYear " +
                    "title { english romaji native } synonyms startDate { year } coverImage { large medium } " +
                    "nextAiringEpisode { episode } relations { edges { relationType node { id format } } }"

            private val DISCOVER_QUERY =
                """
                query (${'$'}page: Int, ${'$'}sort: [MediaSort], ${'$'}season: MediaSeason, ${'$'}seasonYear: Int,
                  ${'$'}status: MediaStatus, ${'$'}formats: [MediaFormat], ${'$'}genres: [String], ${'$'}tags: [String],
                  ${'$'}noGenres: [String], ${'$'}noTags: [String], ${'$'}scoreOver: Int, ${'$'}popUnder: Int, ${'$'}popOver: Int) {
                  Page(page: ${'$'}page, perPage: 50) {
                    pageInfo { hasNextPage }
                    media(type: ANIME, isAdult: false, sort: ${'$'}sort, season: ${'$'}season, seasonYear: ${'$'}seasonYear,
                      status: ${'$'}status, format_in: ${'$'}formats, genre_in: ${'$'}genres, genre_not_in: ${'$'}noGenres,
                      tag_in: ${'$'}tags, tag_not_in: ${'$'}noTags, minimumTagRank: 50, averageScore_greater: ${'$'}scoreOver,
                      popularity_lesser: ${'$'}popUnder, popularity_greater: ${'$'}popOver) {
                      $CARD
                    }
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
                      media { $CARD }
                    }
                  }
                }
                """.trimIndent()
        }
    }
