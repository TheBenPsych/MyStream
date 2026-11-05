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
    val label: String get() = name + (year?.let { " ($it)" } ?: "")
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
        private var cache: Triple<String, Long, List<PuzzleTitle>>? = null
        private var animeCache: Pair<String, Set<UUID>>? = null

        private val _anime = MutableStateFlow(readAnime())
        val anime: StateFlow<AnimeMode> = _anime

        private fun readAnime(): AnimeMode = runCatching { AnimeMode.valueOf(prefs.getString(ANIME_KEY, null) ?: "") }.getOrDefault(AnimeMode.MIXED)

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

        /** Every title, with the anime switch applied */
        suspend fun titles(mode: AnimeMode = anime.value): List<PuzzleTitle> = all().filter { mode.keep(it.anime) }

        suspend fun all(): List<PuzzleTitle> =
            mutex.withLock {
                val who = profileKey() + KidsMode.level
                cache?.let { (k, at, list) ->
                    if (k == who && System.currentTimeMillis() - at < CACHE_MS && list.isNotEmpty()) return@withLock list
                }
                val list = withContext(WholphinDispatchers.IO) { load() }
                cache = Triple(who, System.currentTimeMillis(), list)
                list
            }

        /** For the older movie games: "Anime only" also lets anime shows in */
        fun animeTypes(): List<BaseItemKind> =
            if (anime.value == AnimeMode.ONLY) listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES) else listOf(BaseItemKind.MOVIE)

        /** For the older movie games: says if an item passes the anime switch */
        suspend fun animeKeeper(): (UUID) -> Boolean {
            val mode = anime.value
            if (mode == AnimeMode.MIXED) return { true }
            val ids = animeIds()
            return { mode.keep(it in ids) }
        }

        /** Ids of everything in the libraries with "anime" in their name. Used by the older games too. */
        suspend fun animeIds(): Set<UUID> {
            val userId = serverRepository.currentUser?.id ?: return emptySet()
            val who = userId.toString() + KidsMode.level
            animeCache?.let { (k, ids) -> if (k == who) return ids }
            val ids =
                withContext(WholphinDispatchers.IO) {
                    try {
                        api.userViewsApi
                            .getUserViews(userId = userId)
                            .content.items
                            .filter { it.name.orEmpty().contains("anime", ignoreCase = true) }
                            .flatMap { lib ->
                                api.itemsApi
                                    .getItems(
                                        GetItemsRequest(
                                            userId = userId,
                                            parentId = lib.id,
                                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                            recursive = true,
                                            enableImages = false,
                                            enableUserData = false,
                                            limit = 3000,
                                        ),
                                    ).content.items
                                    .map { it.id }
                            }.toSet()
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Puzzles: couldn't list anime libraries")
                        emptySet()
                    }
                }
            animeCache = who to ids
            return ids
        }

        private suspend fun load(): List<PuzzleTitle> {
            val anime = animeIds()
            val items =
                api.itemsApi
                    .getItems(
                        GetItemsRequest(
                            userId = serverRepository.currentUser?.id,
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
                            limit = POOL_SIZE,
                        ),
                    ).content.items
            return items
                .mapNotNull { toTitle(it, it.id in anime) }
                .distinctBy { it.name.lowercase() }
        }

        private fun toTitle(
            dto: BaseItemDto,
            anime: Boolean,
        ): PuzzleTitle? {
            val name = dto.name?.takeIf { it.isNotBlank() } ?: return null
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
            private const val CACHE_MS = 30 * 60_000L
            private const val POOL_SIZE = 500
            private const val STILL_WIDTH = 1280
            private const val POSTER_WIDTH = 420
            private const val PHOTO_WIDTH = 600
        }
    }
