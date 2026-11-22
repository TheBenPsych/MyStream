package com.github.damontecres.wholphin.ui.games.puzzles

import android.content.Context
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** An actor in a title, with their photo when Tower has one */
data class PuzzlePerson(
    val id: UUID,
    val name: String,
    val photo: String?,
    /** The character they play in this title ("Captain Ada Voss"), when Tower knows it */
    val role: String? = null,
)

/**
 * A movie or show from Tower, with everything the puzzle games need: poster, still pictures
 * (chapter pictures from the movie file first, then its backgrounds), actors, year, genres, studios.
 */
data class PuzzleTitle(
    val id: UUID,
    val name: String,
    val year: Int?,
    val isSeries: Boolean,
    val anime: Boolean,
    val poster: String?,
    /** Still pictures, hardest first (scenes from inside the movie), easiest last (the main background) */
    val stills: List<String>,
    val overview: String,
    val genres: List<String>,
    val studios: List<String>,
    val actors: List<PuzzlePerson>,
    val runtimeMin: Int?,
    /** IMDb rating in tenths (7.4 = 74), 0 when there is none */
    val rating: Int,
    val episodes: Int?,
) {
    val kind: BaseItemKind get() = if (isSeries) BaseItemKind.SERIES else BaseItemKind.MOVIE

    /** The name has a year in it ("Halloween 2018", "2012"), which gives the Timeline answer away */
    val nameHasYear: Boolean get() = YEAR_IN_NAME.containsMatchIn(name)
    val label: String get() = name + (year?.let { " ($it)" } ?: "")
}

private val YEAR_IN_NAME = Regex("\\b(18|19|20)\\d{2}\\b")

/** Jellyfin sometimes keeps the year on the end of a name ("Doctor Who (2005)"): the games show it without */
internal fun cleanTitleName(name: String): String = name.replace(Regex("\\s*[(\\[](18|19|20)\\d{2}[)\\]]\\s*$"), "").trim().ifEmpty { name }

/**
 * Wrong answers that look just as likely as the right one (Ben 2026-11-22: "harder to guess, no
 * process of elimination"): same kind (movie or show), sharing genres, made around the same time.
 * A little randomness keeps them from always being the same few.
 */
fun <T> lookAlikes(
    answer: T,
    from: List<T>,
    n: Int,
    name: (T) -> String,
    genres: (T) -> Collection<String>,
    year: (T) -> Int?,
    series: (T) -> Boolean = { false },
): List<T> {
    val a = name(answer)
    val g = genres(answer).toSet()
    val y = year(answer)
    val s = series(answer)
    return from
        .asSequence()
        .filter { name(it) != a }
        .distinctBy { name(it) }
        .map { t ->
            var score = Math.random() * 3
            score += genres(t).count { it in g } * 3
            val ty = year(t)
            if (y != null && ty != null) score += (6 - kotlin.math.abs(y - ty) / 2).coerceAtLeast(0)
            if (series(t) == s) score += 4
            t to score
        }.sortedByDescending { it.second }
        .take(n * 3)
        .map { it.first }
        .toList()
        .shuffled()
        .take(n)
}

/** The anime switch on every movie game (Ben asked for it on the older games too) */
enum class AnimeMode(
    val label: String,
) {
    MIXED("Anime: mixed in"),
    ONLY("Anime only"),
    NONE("No anime"),
    ;

    fun next(): AnimeMode = entries[(ordinal + 1) % entries.size]

    fun keep(anime: Boolean): Boolean =
        when (this) {
            MIXED -> true
            ONLY -> anime
            NONE -> !anime
        }
}

/**
 * The movies & shows the puzzle and party games pick from (everything on Tower, Ben's pick).
 * Loaded once and kept for 30 minutes so going from game to game is instant. On a Kids profile the
 * Kids filter already removes anything not rated for kids from every list the server sends.
 */
@Singleton
class PuzzlePool
    @Inject
    constructor(
        @param:ApplicationContext context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val imageUrlService: ImageUrlService,
    ) {
        private val prefs = context.getSharedPreferences("benflix_games", Context.MODE_PRIVATE)
        private val mutex = Mutex()

        /** One pool per profile and anime mix, kept 30 minutes */
        private val cache = HashMap<String, Pair<Long, List<PuzzleTitle>>>()
        private var animeCache: Pair<String, Pair<List<UUID>, Set<UUID>>>? = null

        private val _anime = MutableStateFlow(readAnime())
        val anime: StateFlow<AnimeMode> = _anime

        private fun readAnime(): AnimeMode =
            runCatching { AnimeMode.valueOf(prefs.getString(ANIME_KEY, null) ?: "") }.getOrDefault(AnimeMode.MIXED)

        fun setAnime(mode: AnimeMode) {
            prefs.edit().putString(ANIME_KEY, mode.name).apply()
            _anime.value = mode
        }

        /** Who the saved best scores belong to: this account, Kids or a guest */
        fun profileKey(): String =
            when {
                GuestMode.isActive -> "guest"
                KidsMode.isActive -> "kids"
                else -> serverRepository.currentUser?.id?.toString() ?: "guest"
            }

        /**
         * [list] shuffled, with titles this profile saw lately in [game] moved to the back, so the same
         * ones don't keep coming up (Ben 2026-11-22: "we see a lot of the same stuff").
         */
        fun <T> freshFirst(
            game: String,
            list: List<T>,
            id: (T) -> Any,
        ): List<T> {
            val seen = seen(game)
            val (old, new) = list.shuffled().partition { id(it).toString() in seen }
            // The ones seen longest ago come back first
            return new + old.sortedBy { seen.indexOf(id(it).toString()) }
        }

        /** Remembers that [id] was shown in [game] (the last $SEEN_KEEP are kept) */
        fun saw(
            game: String,
            id: Any,
        ) {
            val key = id.toString()
            val list = (seen(game) - key + key).takeLast(SEEN_KEEP)
            prefs.edit().putString(seenKey(game), list.joinToString(",")).apply()
        }

        private fun seen(game: String): List<String> =
            prefs
                .getString(seenKey(game), null)
                ?.split(",")
                ?.filter { it.isNotBlank() }
                .orEmpty()

        private fun seenKey(game: String) = "${profileKey()}_seen_$game"

        fun best(boardKey: String): Int = prefs.getInt("${profileKey()}_$boardKey", 0)

        /** Saves a new best and says whether it was one */
        fun submit(
            boardKey: String,
            score: Int,
        ): Boolean {
            if (score <= best(boardKey)) return false
            prefs.edit().putInt("${profileKey()}_$boardKey", score).apply()
            return true
        }

        /**
         * The anime mix in use. Kids profiles don't see the switch, so they always get everything
         * they're allowed (a grown-up's "Anime only" used to leave Kids with nothing to play).
         */
        fun mode(): AnimeMode = if (KidsMode.isActive) AnimeMode.MIXED else anime.value

        /**
         * Every title, with the anime switch applied. "Anime only" loads straight from the anime
         * libraries (it used to pick 500 random titles from everything and then keep only the anime,
         * which left too few to play), and "No anime" tops up until it has a full pool.
         */
        suspend fun titles(mode: AnimeMode = mode()): List<PuzzleTitle> =
            mutex.withLock {
                val who = profileKey() + KidsMode.level + "_" + mode.name
                cache[who]?.let { (at, list) ->
                    if (System.currentTimeMillis() - at < CACHE_MS && list.isNotEmpty()) return@withLock list
                }
                val list = withContext(WholphinDispatchers.IO) { load(mode) }
                cache[who] = System.currentTimeMillis() to list
                list
            }

        suspend fun all(): List<PuzzleTitle> = titles(AnimeMode.MIXED)

        /** For the older movie games: "Anime only" also lets anime shows in */
        fun animeTypes(): List<BaseItemKind> =
            if (mode() == AnimeMode.ONLY) listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES) else listOf(BaseItemKind.MOVIE)

        /** For the older movie games: the anime libraries to load from on "Anime only" (null = load from everything) */
        suspend fun animeParents(): List<UUID>? = if (mode() == AnimeMode.ONLY) animeLibraries().first.ifEmpty { null } else null

        /** For the older movie games: says if an item passes the anime switch */
        suspend fun animeKeeper(): (UUID) -> Boolean {
            val mode = mode()
            if (mode == AnimeMode.MIXED) return { true }
            val ids = animeIds()
            return { mode.keep(it in ids) }
        }

        /** Ids of everything in the libraries with "anime" in their name. Used by the older games too. */
        suspend fun animeIds(): Set<UUID> = animeLibraries().second

        /** The anime libraries, and the ids of everything in them */
        private suspend fun animeLibraries(): Pair<List<UUID>, Set<UUID>> {
            val userId = serverRepository.currentUser?.id ?: return emptyList<UUID>() to emptySet()
            val who = userId.toString() + KidsMode.level
            animeCache?.let { (k, found) -> if (k == who) return found }
            val found =
                withContext(WholphinDispatchers.IO) {
                    try {
                        val libs =
                            api.userViewsApi
                                .getUserViews(userId = userId)
                                .content.items
                                .filter { it.name.orEmpty().contains("anime", ignoreCase = true) }
                                .map { it.id }
                        val ids =
                            libs
                                .flatMap { lib ->
                                    api.itemsApi
                                        .getItems(
                                            GetItemsRequest(
                                                userId = userId,
                                                parentId = lib,
                                                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                                recursive = true,
                                                enableImages = false,
                                                enableUserData = false,
                                                limit = 5000,
                                            ),
                                        ).content.items
                                        .map { it.id }
                                }.toSet()
                        libs to ids
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Puzzles: couldn't list anime libraries")
                        null
                    }
                }
            // A failed look-up isn't kept, so the next game tries again
            if (found == null) return emptyList<UUID>() to emptySet()
            animeCache = who to found
            return found
        }

        private fun request(
            parentId: UUID?,
            limit: Int,
        ) = GetItemsRequest(
            userId = serverRepository.currentUser?.id,
            parentId = parentId,
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
            recursive = true,
            imageTypes = listOf(ImageType.PRIMARY),
            sortBy = listOf(ItemSortBy.RANDOM),
            fields =
                listOf(
                    ItemFields.OVERVIEW,
                    ItemFields.PEOPLE,
                    ItemFields.GENRES,
                    ItemFields.STUDIOS,
                    ItemFields.CHAPTERS,
                ),
            enableUserData = false,
            limit = limit,
        )

        private suspend fun load(mode: AnimeMode): List<PuzzleTitle> {
            val (animeLibs, anime) = animeLibraries()
            val items =
                when {
                    mode == AnimeMode.ONLY && animeLibs.isNotEmpty() -> {
                        val each = (POOL_SIZE / animeLibs.size).coerceAtLeast(150)
                        animeLibs.flatMap { lib ->
                            api.itemsApi
                                .getItems(request(lib, each))
                                .content.items
                        }
                    }

                    mode == AnimeMode.NONE -> {
                        // Ask for extra, so a full pool is left once the anime is taken out
                        api.itemsApi
                            .getItems(request(null, POOL_SIZE + anime.size.coerceAtMost(POOL_SIZE)))
                            .content.items
                            .filter { it.id !in anime }
                            .take(POOL_SIZE)
                    }

                    else -> {
                        api.itemsApi
                            .getItems(request(null, POOL_SIZE))
                            .content.items
                    }
                }
            return items
                .mapNotNull { toTitle(it, it.id in anime) }
                .filter { mode.keep(it.anime) }
                .distinctBy { it.name.lowercase() }
        }

        private fun toTitle(
            dto: BaseItemDto,
            anime: Boolean,
        ): PuzzleTitle? {
            val name = dto.name?.takeIf { it.isNotBlank() }?.let(::cleanTitleName) ?: return null
            val chapterStills =
                dto.chapters
                    .orEmpty()
                    .withIndex()
                    .filter { it.value.imageTag != null }
                    // Skip the very start and the end credits: those pictures are often black or just text
                    .let { list -> if (list.size > 4) list.drop(1).dropLast(1) else list }
                    .shuffled()
                    .take(4)
                    .mapNotNull { (i, ch) ->
                        imageUrlService.getItemImageUrl(
                            itemId = dto.id,
                            imageType = ImageType.CHAPTER,
                            maxWidth = STILL_WIDTH,
                            tag = ch.imageTag,
                            imageIndex = i,
                        )
                    }
            val backdrops =
                dto.backdropImageTags
                    .orEmpty()
                    .withIndex()
                    .reversed()
                    .take(3)
                    .mapNotNull { (i, tag) ->
                        imageUrlService.getItemImageUrl(
                            itemId = dto.id,
                            imageType = ImageType.BACKDROP,
                            maxWidth = STILL_WIDTH,
                            tag = tag,
                            imageIndex = i,
                        )
                    }
            return PuzzleTitle(
                id = dto.id,
                name = name,
                year = dto.productionYear,
                isSeries = dto.type == BaseItemKind.SERIES,
                anime = anime,
                poster =
                    imageUrlService.getItemImageUrl(
                        itemId = dto.id,
                        imageType = ImageType.PRIMARY,
                        maxWidth = POSTER_WIDTH,
                        tag = dto.imageTags?.get(ImageType.PRIMARY),
                    ),
                stills = (chapterStills + backdrops).distinct(),
                overview = dto.overview.orEmpty(),
                genres = dto.genres.orEmpty(),
                studios = dto.studios.orEmpty().mapNotNull { it.name },
                actors =
                    dto.people
                        .orEmpty()
                        .filter { it.type == PersonKind.ACTOR && !it.name.isNullOrBlank() }
                        .take(12)
                        .map { p ->
                            PuzzlePerson(
                                id = p.id,
                                name = p.name!!,
                                role = p.role?.trim()?.takeIf { it.isNotEmpty() },
                                photo =
                                    p.primaryImageTag?.let { tag ->
                                        imageUrlService.getItemImageUrl(
                                            itemId = p.id,
                                            imageType = ImageType.PRIMARY,
                                            maxWidth = PHOTO_WIDTH,
                                            tag = tag,
                                        )
                                    },
                            )
                        },
                runtimeMin = dto.runTimeTicks?.let { (it / 600_000_000L).toInt() }?.takeIf { it > 0 },
                rating = dto.communityRating?.let { Math.round(it * 10) } ?: 0,
                episodes = dto.recursiveItemCount?.takeIf { dto.type == BaseItemKind.SERIES && it > 0 },
            )
        }

        companion object {
            private const val ANIME_KEY = "puzzle_anime_mode"

            // A fresh random set every 15 minutes, so long sessions don't keep showing the same titles
            private const val CACHE_MS = 15 * 60_000L
            private const val SEEN_KEEP = 80
            private const val POOL_SIZE = 500
            private const val STILL_WIDTH = 1280
            private const val POSTER_WIDTH = 420
            private const val PHOTO_WIDTH = 600
        }
    }
