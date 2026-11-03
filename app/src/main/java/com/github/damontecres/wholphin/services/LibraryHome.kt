package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.data.model.createStudioDestination
import com.github.damontecres.wholphin.ui.AspectRatio
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.components.ViewOptionImageType
import com.github.damontecres.wholphin.ui.main.FeaturedItem
import com.github.damontecres.wholphin.ui.main.FeaturedReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.studiosApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.SeriesStatus
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetGenresRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetStudiosRequest
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Benflix: what kind of library a landing page belongs to, from its type and name */
enum class LibraryKind {
    MOVIES,
    SHOWS,
    ANIME_SHOWS,
    ANIME_MOVIES,
    STANDUP,
    OTHER,
    ;

    val isAnime: Boolean get() = this == ANIME_SHOWS || this == ANIME_MOVIES

    val itemType: BaseItemKind get() = if (this == SHOWS || this == ANIME_SHOWS) BaseItemKind.SERIES else BaseItemKind.MOVIE

    companion object {
        fun from(
            name: String?,
            collectionType: CollectionType?,
        ): LibraryKind {
            val lower = name.orEmpty().lowercase()
            return when {
                "anime" in lower && collectionType == CollectionType.TVSHOWS -> ANIME_SHOWS
                "anime" in lower -> ANIME_MOVIES
                "stand" in lower || "comed" in lower -> STANDUP
                collectionType == CollectionType.MOVIES -> MOVIES
                collectionType == CollectionType.TVSHOWS -> SHOWS
                else -> OTHER
            }
        }
    }
}

/**
 * One row on a library's mini Home. [load] returns the row's items (an empty list hides the row)
 * and may change the row's [title], for rows that pick their subject when they load.
 */
class LibraryShelf(
    initialTitle: String,
    val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    val load: suspend (LibraryShelf) -> List<BaseItem>,
) {
    @Volatile var title: String = initialTitle
}

/**
 * Benflix landing pages: each Browse category (Movies, Shows, Anime, Stand up) opens on its own
 * mini Home with a banner and rows made for that kind of library.
 */
@Singleton
class LibraryHomeService
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val dubIndex: AnimeDubIndex,
    ) {
        private val kinds = ConcurrentHashMap<UUID, LibraryKind>()
        private val names = ConcurrentHashMap<UUID, String>()

        private val userId: UUID? get() = serverRepository.currentUser?.id

        suspend fun kindOf(parentId: UUID): LibraryKind {
            kinds[parentId]?.let { return it }
            val lib = api.userLibraryApi.getItem(parentId).content
            val kind = LibraryKind.from(lib.name, lib.collectionType)
            kinds[parentId] = kind
            lib.name?.let { names[parentId] = it }
            return kind
        }

        /** The rotating banner: unwatched picks, and for shows also ones with new episodes */
        suspend fun featured(
            parentId: UUID,
            kind: LibraryKind,
        ): List<FeaturedItem> =
            coroutineScope {
                val base =
                    GetItemsRequest(
                        userId = userId,
                        parentId = parentId,
                        includeItemTypes = listOf(kind.itemType),
                        recursive = true,
                        imageTypes = listOf(ImageType.BACKDROP),
                        fields = SlimItemFields,
                        enableUserData = true,
                    )
                val fresh =
                    async {
                        if (kind.itemType != BaseItemKind.SERIES) return@async emptyList()
                        safe {
                            items(
                                base.copy(
                                    sortBy = listOf(ItemSortBy.DATE_LAST_CONTENT_ADDED),
                                    sortOrder = listOf(SortOrder.DESCENDING),
                                    limit = 2,
                                ),
                            ).map { FeaturedItem(BaseItem(it), FeaturedReason.NEW) }
                        }
                    }
                val picks =
                    async {
                        safe {
                            val rated =
                                items(
                                    base.copy(
                                        isPlayed = false,
                                        minCommunityRating = 6.5,
                                        sortBy = listOf(ItemSortBy.RANDOM),
                                        limit = FEATURED_COUNT,
                                    ),
                                )
                            val any =
                                if (rated.size >= 3) {
                                    rated
                                } else {
                                    rated +
                                        items(
                                            base.copy(sortBy = listOf(ItemSortBy.RANDOM), limit = FEATURED_COUNT),
                                        )
                                }
                            any.map { FeaturedItem(BaseItem(it), FeaturedReason.PICK) }
                        }
                    }
                (fresh.await() + picks.await()).distinctBy { it.item.id }.take(FEATURED_COUNT)
            }

        /** Something random from this library that hasn't been watched, for Surprise me */
        suspend fun surprise(
            parentId: UUID,
            kind: LibraryKind,
        ): BaseItem? =
            items(
                GetItemsRequest(
                    userId = userId,
                    parentId = parentId,
                    includeItemTypes = listOf(kind.itemType),
                    recursive = true,
                    isPlayed = false,
                    sortBy = listOf(ItemSortBy.RANDOM),
                    limit = 1,
                ),
            ).firstOrNull()?.let { BaseItem(it) }

        /** The rows of a library's mini Home, after Continue watching and before Suggestions */
        fun homeShelves(
            parentId: UUID,
            kind: LibraryKind,
        ): List<LibraryShelf> =
            when (kind) {
                LibraryKind.MOVIES -> movieShelves(parentId)
                LibraryKind.SHOWS -> showShelves(parentId)
                LibraryKind.ANIME_SHOWS -> animeShowShelves(parentId)
                LibraryKind.ANIME_MOVIES -> animeMovieShelves(parentId)
                LibraryKind.STANDUP -> standUpShelves(parentId)
                LibraryKind.OTHER -> emptyList()
            }

        // ---------- Movies ----------

        private fun movieShelves(parentId: UUID): List<LibraryShelf> {
            val decade = DECADES[(LocalDate.now().toEpochDay() % DECADES.size).toInt()]
            return listOf(
                LibraryShelf("Under 90 minutes") { shortOnes(parentId, BaseItemKind.MOVIE, 90) },
                decadeShelf(parentId, decade),
                LibraryShelf("Best Picture winners") { awardWinners(parentId) },
                LibraryShelf("Finish the collection") { unfinishedCollections() },
                LibraryShelf("Directors", PEOPLE_ROW) { topPeople(parentId, PersonKind.DIRECTOR, minTitles = 2) },
            )
        }

        /** Rows for the Decades tab: the best of each decade, newest first */
        fun decadeShelves(parentId: UUID): List<LibraryShelf> = DECADES.map { decadeShelf(parentId, it) }

        private fun decadeShelf(
            parentId: UUID,
            start: Int,
        ) = LibraryShelf(if (start < 1970) "Before the 70s" else "Best of the ${decadeName(start)}") {
            val from = if (start < 1970) 1900 else start
            val to = if (start < 1970) 1969 else start + 9
            items(
                rowRequest(parentId, BaseItemKind.MOVIE).copy(
                    years = (from..to).toList(),
                    sortBy = listOf(ItemSortBy.COMMUNITY_RATING, ItemSortBy.RANDOM),
                    sortOrder = listOf(SortOrder.DESCENDING, SortOrder.ASCENDING),
                    limit = ROW,
                ),
            ).map { BaseItem(it) }
        }

        private fun decadeName(start: Int) = if (start >= 2000) "${start}s" else "${start % 100}s"

        private suspend fun shortOnes(
            parentId: UUID,
            type: BaseItemKind,
            maxMinutes: Int,
        ): List<BaseItem> =
            items(
                rowRequest(parentId, type).copy(
                    isPlayed = false,
                    minCommunityRating = 6.0,
                    sortBy = listOf(ItemSortBy.RANDOM),
                    limit = 200,
                ),
            ).filter { dto -> dto.runTimeTicks?.ticks?.let { it in 20.minutes..maxMinutes.minutes } == true }
                .take(ROW)
                .map { BaseItem(it) }

        private suspend fun awardWinners(parentId: UUID): List<BaseItem> {
            val all =
                items(
                    GetItemsRequest(
                        userId = userId,
                        parentId = parentId,
                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                        recursive = true,
                        enableImages = false,
                        enableUserData = false,
                    ),
                )
            val ids =
                all
                    .filter { dto ->
                        val year = dto.productionYear ?: return@filter false
                        BEST_PICTURE.any { (title, winYear) -> clean(dto.name) == clean(title) && kotlin.math.abs(year - winYear) <= 1 }
                    }.map { it.id }
            if (ids.isEmpty()) return emptyList()
            return items(
                GetItemsRequest(
                    userId = userId,
                    ids = ids,
                    fields = SlimItemFields,
                    enableUserData = true,
                    sortBy = listOf(ItemSortBy.PREMIERE_DATE),
                    sortOrder = listOf(SortOrder.DESCENDING),
                ),
            ).map { BaseItem(it) }
        }

        private fun clean(name: String?) = name.orEmpty().lowercase().replace(Regex("[^a-z0-9]"), "")

        /** Box sets you've seen part of, but not all */
        private suspend fun unfinishedCollections(): List<BaseItem> =
            items(
                GetItemsRequest(
                    userId = userId,
                    includeItemTypes = listOf(BaseItemKind.BOX_SET),
                    recursive = true,
                    fields = SlimItemFields,
                    enableUserData = true,
                    sortBy = listOf(ItemSortBy.DATE_PLAYED, ItemSortBy.SORT_NAME),
                    sortOrder = listOf(SortOrder.DESCENDING, SortOrder.ASCENDING),
                    limit = 300,
                ),
            ).filter { dto ->
                val pct = dto.userData?.playedPercentage ?: 0.0
                dto.userData?.played != true && pct > 0.0 && pct < 100.0
            }.take(ROW)
                .map { BaseItem(it) }

        /**
         * The people (directors, comedians) with the most titles in this library, as tiles that
         * open their page
         */
        private suspend fun topPeople(
            parentId: UUID,
            kind: PersonKind,
            minTitles: Int,
            firstOnly: Boolean = false,
        ): List<BaseItem> {
            val titles =
                items(
                    GetItemsRequest(
                        userId = userId,
                        parentId = parentId,
                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                        recursive = true,
                        fields = listOf(ItemFields.PEOPLE),
                        enableImages = false,
                        enableUserData = false,
                        sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        limit = 600,
                    ),
                )
            val counts =
                titles
                    .flatMap { dto ->
                        val people = dto.people.orEmpty().filter { it.type == kind }
                        (if (firstOnly) people.take(1) else people).map { it.id }.distinct()
                    }.groupingBy { it }
                    .eachCount()
                    .filter { it.value >= minTitles }
                    .entries
                    .sortedByDescending { it.value }
                    .take(PEOPLE_MAX)
                    .map { it.key }
            return peopleItems(counts)
        }

        private suspend fun peopleItems(ids: List<UUID>): List<BaseItem> =
            coroutineScope {
                ids
                    .map { id ->
                        async {
                            try {
                                api.userLibraryApi
                                    .getItem(id, userId)
                                    .content
                                    .takeIf { !it.imageTags.isNullOrEmpty() }
                                    ?.let { BaseItem(it) }
                            } catch (ex: CancellationException) {
                                throw ex
                            } catch (ex: Exception) {
                                null
                            }
                        }
                    }.awaitAll()
                    .filterNotNull()
            }

        // ---------- Shows ----------

        private fun showShelves(parentId: UUID): List<LibraryShelf> =
            listOf(
                LibraryShelf("Finish the season", WIDE_ROW) { finishTheSeason(parentId) },
                LibraryShelf("Complete series") {
                    items(
                        rowRequest(parentId, BaseItemKind.SERIES).copy(
                            seriesStatus = listOf(SeriesStatus.ENDED),
                            isPlayed = false,
                            minCommunityRating = 7.0,
                            sortBy = listOf(ItemSortBy.RANDOM),
                            limit = ROW,
                        ),
                    ).map { BaseItem(it) }
                },
                LibraryShelf("Watch in a weekend") { weekendShows(parentId) },
                LibraryShelf("By network", STUDIO_ROW) { networks(parentId, BaseItemKind.SERIES, NETWORKS) },
                LibraryShelf("Easy watching") { easyWatching(parentId) },
            )

        /** The next episode of shows where only 1 to 3 episodes are left in the season */
        private suspend fun finishTheSeason(parentId: UUID): List<BaseItem> {
            val nextUp =
                api.tvShowsApi
                    .getNextUp(
                        GetNextUpRequest(
                            userId = userId,
                            parentId = parentId,
                            fields = SlimItemFields,
                            enableUserData = true,
                            enableResumable = true,
                            limit = 60,
                        ),
                    ).content.items
            val seasonIds = nextUp.mapNotNull { it.seasonId }.distinct()
            if (seasonIds.isEmpty()) return emptyList()
            val almostDone =
                items(GetItemsRequest(userId = userId, ids = seasonIds, enableUserData = true, enableImages = false))
                    .filter { (it.userData?.unplayedItemCount ?: 0) in 1..3 }
                    .map { it.id }
                    .toSet()
            return nextUp.filter { ep -> ep.seasonId?.let { it in almostDone } == true }.take(ROW).map { BaseItem(it) }
        }

        private suspend fun weekendShows(parentId: UUID): List<BaseItem> =
            items(
                rowRequest(parentId, BaseItemKind.SERIES).copy(
                    isPlayed = false,
                    minCommunityRating = 7.0,
                    fields = SlimItemFields + listOf(ItemFields.CHILD_COUNT, ItemFields.RECURSIVE_ITEM_COUNT),
                    sortBy = listOf(ItemSortBy.RANDOM),
                    limit = 200,
                ),
            ).filter { dto ->
                val episodes = dto.recursiveItemCount ?: return@filter false
                val runtime = dto.runTimeTicks?.ticks ?: return@filter false
                episodes in 2..40 && (dto.childCount ?: 1) <= 2 && runtime * episodes <= 10.hours
            }.take(ROW)
                .map { BaseItem(it) }

        /** Sitcoms and cartoons with short episodes */
        private suspend fun easyWatching(parentId: UUID): List<BaseItem> =
            items(
                rowRequest(parentId, BaseItemKind.SERIES).copy(
                    genres = listOf("Comedy", "Animation", "Family"),
                    sortBy = listOf(ItemSortBy.RANDOM),
                    limit = 150,
                ),
            ).filter { dto -> dto.runTimeTicks?.ticks?.let { it <= 32.minutes } == true }
                .take(ROW)
                .map { BaseItem(it) }

        /**
         * Tiles for networks or studios in this library. The ones in [preferred] come first in that
         * order, then the others with the most titles.
         */
        private suspend fun networks(
            parentId: UUID,
            type: BaseItemKind,
            preferred: List<String>,
            max: Int = 16,
        ): List<BaseItem> {
            val studios =
                api.studiosApi
                    .getStudios(
                        GetStudiosRequest(
                            userId = userId,
                            parentId = parentId,
                            includeItemTypes = listOf(type),
                            enableImages = true,
                        ),
                    ).content.items
            val ranked =
                studios
                    .mapNotNull { studio ->
                        val name = clean(studio.name)
                        val rank = preferred.indexOfFirst { clean(it) == name }
                        if (rank >= 0) rank to studio else null
                    }.sortedBy { it.first }
                    .map { it.second }
                    .take(max)
            val libraryName = names[parentId]
            return ranked.map { studio ->
                BaseItem(
                    data = studio,
                    destinationOverride =
                        createStudioDestination(
                            studioId = studio.id,
                            name = studio.name.orEmpty(),
                            parentId = parentId,
                            parentName = libraryName,
                            includeItemTypes = listOf(type),
                        ),
                )
            }
        }

        // ---------- Anime shows ----------

        private fun animeShowShelves(parentId: UUID): List<LibraryShelf> {
            val season = AnimeSeason.current()
            return listOf(
                LibraryShelf("New episodes") {
                    items(
                        rowRequest(parentId, BaseItemKind.SERIES).copy(
                            sortBy = listOf(ItemSortBy.DATE_LAST_CONTENT_ADDED),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            limit = ROW,
                        ),
                    ).map { BaseItem(it) }
                },
                LibraryShelf("Airing this season: ${season.title}") { airingIn(parentId, season) },
                LibraryShelf("Fan favourites") { fanFavourites(parentId, BaseItemKind.SERIES) },
                LibraryShelf("New to anime? Start here") { startHere(parentId) },
            ) + genreShelves(parentId, BaseItemKind.SERIES, "anime")
        }

        /** Shows with episodes that came out during [season] */
        private suspend fun airingIn(
            parentId: UUID,
            season: AnimeSeason,
        ): List<BaseItem> {
            val episodes =
                items(
                    GetItemsRequest(
                        userId = userId,
                        parentId = parentId,
                        includeItemTypes = listOf(BaseItemKind.EPISODE),
                        recursive = true,
                        minPremiereDate = season.start.atStartOfDay(),
                        maxPremiereDate = minOf(season.end.atStartOfDay(), LocalDateTime.now()),
                        enableImages = false,
                        enableUserData = false,
                        limit = 400,
                    ),
                )
            val seriesIds = episodes.mapNotNull { it.seriesId }.distinct()
            if (seriesIds.isEmpty()) return emptyList()
            return items(
                GetItemsRequest(
                    userId = userId,
                    ids = seriesIds,
                    fields = SlimItemFields,
                    enableUserData = true,
                    sortBy = listOf(ItemSortBy.DATE_LAST_CONTENT_ADDED),
                    sortOrder = listOf(SortOrder.DESCENDING),
                ),
            ).map { BaseItem(it) }
        }

        /** Rows for the Seasons tab: shows that started in each of the last few anime seasons */
        fun seasonShelves(parentId: UUID): List<LibraryShelf> =
            AnimeSeason.recent(SEASONS_SHOWN).map { season ->
                LibraryShelf(season.title) {
                    items(
                        rowRequest(parentId, BaseItemKind.SERIES).copy(
                            minPremiereDate = season.start.atStartOfDay(),
                            maxPremiereDate = season.end.atStartOfDay(),
                            sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            limit = ROW * 2,
                        ),
                    ).map { BaseItem(it) }
                }
            }

        private suspend fun fanFavourites(
            parentId: UUID,
            type: BaseItemKind,
        ): List<BaseItem> =
            items(
                rowRequest(parentId, type).copy(
                    minCommunityRating = 7.5,
                    sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    limit = ROW,
                ),
            ).map { BaseItem(it) }

        /** Popular, not too long, and with English voices where possible */
        private suspend fun startHere(parentId: UUID): List<BaseItem> {
            val dubs = dubIndex.index(parentId, userId, shows = true)
            val shows =
                items(
                    rowRequest(parentId, BaseItemKind.SERIES).copy(
                        minCommunityRating = 7.5,
                        fields = SlimItemFields + listOf(ItemFields.CHILD_COUNT, ItemFields.RECURSIVE_ITEM_COUNT),
                        sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        limit = 150,
                    ),
                ).filter { (it.recursiveItemCount ?: 0) in 1..64 }
            return shows
                .sortedByDescending { dubs[it.id]?.dub == true }
                .take(ROW)
                .map { BaseItem(it) }
        }

        // ---------- Anime movies ----------

        private fun animeMovieShelves(parentId: UUID): List<LibraryShelf> =
            listOf(
                LibraryShelf("Fan favourites") { fanFavourites(parentId, BaseItemKind.MOVIE) },
                LibraryShelf("Goes with your shows") { goesWithYourShows(parentId) },
                LibraryShelf("No show needed") { standalone(parentId) },
                LibraryShelf("With English voices") {
                    val dubs = dubIndex.index(parentId, userId, shows = false)
                    val ids =
                        dubs
                            .filterValues { it.dub }
                            .keys
                            .shuffled()
                            .take(ROW)
                    if (ids.isEmpty()) {
                        emptyList()
                    } else {
                        items(
                            GetItemsRequest(userId = userId, ids = ids, fields = SlimItemFields),
                        ).map { BaseItem(it) }
                    }
                },
                LibraryShelf("By studio", STUDIO_ROW) { networks(parentId, BaseItemKind.MOVIE, ANIME_STUDIOS) },
            ) + studioShelves(parentId)

        /** One row each for the big anime studios that have a few movies here */
        private fun studioShelves(parentId: UUID): List<LibraryShelf> =
            listOf("Studio Ghibli", "Kyoto Animation", "Ufotable").map { studio ->
                LibraryShelf(studio) {
                    val match =
                        api.studiosApi
                            .getStudios(GetStudiosRequest(userId = userId, parentId = parentId, searchTerm = studio, limit = 5))
                            .content.items
                            .firstOrNull { clean(it.name) == clean(studio) } ?: return@LibraryShelf emptyList()
                    items(
                        rowRequest(parentId, BaseItemKind.MOVIE).copy(
                            studioIds = listOf(match.id),
                            sortBy = listOf(ItemSortBy.PREMIERE_DATE),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            limit = ROW,
                        ),
                    ).takeIf { it.size >= 3 }
                        .orEmpty()
                        .map { BaseItem(it) }
                }
            }

        /** Names of the anime shows on the server, and which of them this person has watched */
        private suspend fun animeShows(): List<BaseItemDto> {
            val libs =
                api.userViewsApi
                    .getUserViews(userId = userId)
                    .content.items
                    .filter { it.collectionType == CollectionType.TVSHOWS && it.name?.contains("anime", true) == true }
            return libs.flatMap { lib ->
                items(
                    GetItemsRequest(
                        userId = userId,
                        parentId = lib.id,
                        includeItemTypes = listOf(BaseItemKind.SERIES),
                        recursive = true,
                        enableImages = false,
                        enableUserData = true,
                    ),
                )
            }
        }

        /** The first part of a show or movie name, before any ":" or " - ", for matching */
        private fun franchise(name: String?): String = clean(name.orEmpty().split(":", " - ", " – ").first())

        private suspend fun goesWithYourShows(parentId: UUID): List<BaseItem> {
            val watched =
                animeShows()
                    .filter { (it.userData?.playedPercentage ?: 0.0) > 0.0 || it.userData?.played == true }
                    .map { franchise(it.name) }
                    .filter { it.length >= MIN_FRANCHISE }
                    .toSet()
            if (watched.isEmpty()) return emptyList()
            return items(
                rowRequest(parentId, BaseItemKind.MOVIE).copy(
                    sortBy = listOf(ItemSortBy.PREMIERE_DATE),
                    sortOrder = listOf(SortOrder.ASCENDING),
                    limit = 600,
                ),
            ).filter { movie -> watched.any { show -> franchise(movie.name).startsWith(show) || clean(movie.name).startsWith(show) } }
                .take(ROW)
                .map { BaseItem(it) }
        }

        /** Well-rated movies that don't belong to any show on the server */
        private suspend fun standalone(parentId: UUID): List<BaseItem> {
            val shows =
                animeShows()
                    .map { franchise(it.name) }
                    .filter { it.length >= MIN_FRANCHISE }
                    .toSet()
            return items(
                rowRequest(parentId, BaseItemKind.MOVIE).copy(
                    minCommunityRating = 7.0,
                    sortBy = listOf(ItemSortBy.RANDOM),
                    limit = 200,
                ),
            ).filter { movie -> shows.none { franchise(movie.name).startsWith(it) || clean(movie.name).startsWith(it) } }
                .take(ROW)
                .map { BaseItem(it) }
        }

        /** Anime shows or movies with an English dub (true) or Japanese voices with subtitles (false) */
        suspend fun dubbedOrSubbed(
            parentId: UUID,
            kind: LibraryKind,
            dubbed: Boolean,
        ): List<UUID> =
            dubIndex
                .index(parentId, userId, shows = kind == LibraryKind.ANIME_SHOWS)
                .filterValues { if (dubbed) it.dub else it.sub }
                .keys
                .toList()

        // ---------- Stand up ----------

        private fun standUpShelves(parentId: UUID): List<LibraryShelf> =
            listOf(
                LibraryShelf("Comedians", PEOPLE_ROW) { topPeople(parentId, PersonKind.ACTOR, minTitles = 1, firstOnly = true) },
                LibraryShelf("Newest specials") {
                    items(
                        rowRequest(parentId, BaseItemKind.MOVIE).copy(
                            sortBy = listOf(ItemSortBy.PREMIERE_DATE),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            maxPremiereDate = LocalDateTime.now(),
                            limit = ROW,
                        ),
                    ).map { BaseItem(it) }
                },
                LibraryShelf("Under an hour") { shortOnes(parentId, BaseItemKind.MOVIE, 60) },
                LibraryShelf("More from comedians you watched") { moreFromComedians(parentId) },
                LibraryShelf("Family friendly") {
                    items(
                        rowRequest(parentId, BaseItemKind.MOVIE).copy(
                            maxOfficialRating = "TV-14",
                            hasParentalRating = true,
                            sortBy = listOf(ItemSortBy.RANDOM),
                            limit = ROW,
                        ),
                    ).map { BaseItem(it) }
                },
            )

        private suspend fun moreFromComedians(parentId: UUID): List<BaseItem> {
            val watched =
                items(
                    GetItemsRequest(
                        userId = userId,
                        parentId = parentId,
                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                        recursive = true,
                        isPlayed = true,
                        fields = listOf(ItemFields.PEOPLE),
                        enableImages = false,
                        sortBy = listOf(ItemSortBy.DATE_PLAYED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        limit = 30,
                    ),
                )
            val comedians =
                watched
                    .mapNotNull { dto ->
                        dto.people
                            .orEmpty()
                            .firstOrNull { it.type == PersonKind.ACTOR }
                            ?.id
                    }.distinct()
                    .take(10)
            if (comedians.isEmpty()) return emptyList()
            return items(
                rowRequest(parentId, BaseItemKind.MOVIE).copy(
                    personIds = comedians,
                    isPlayed = false,
                    sortBy = listOf(ItemSortBy.RANDOM),
                    limit = ROW,
                ),
            ).map { BaseItem(it) }
        }

        // ---------- Shared ----------

        private val genreCache = ConcurrentHashMap<UUID, List<String>>()

        private suspend fun genresFor(
            parentId: UUID,
            type: BaseItemKind,
        ): List<String> =
            genreCache[parentId] ?: api.genresApi
                .getGenres(GetGenresRequest(parentId = parentId, userId = userId, includeItemTypes = listOf(type)))
                .content.items
                .mapNotNull { it.name }
                .filter { name -> listOf("anime", "animation").none { name.equals(it, ignoreCase = true) } }
                .sorted()
                .also { genreCache[parentId] = it }

        /** Two genre rows that change each day, like "Action anime" */
        private fun genreShelves(
            parentId: UUID,
            type: BaseItemKind,
            suffix: String,
        ): List<LibraryShelf> =
            (0 until 2).map { slot ->
                LibraryShelf("More $suffix") { shelf ->
                    val genres = genresFor(parentId, type)
                    if (slot >= genres.size) return@LibraryShelf emptyList()
                    val day = LocalDate.now().toEpochDay().toInt()
                    val genre = genres[(day * 2 + slot) % genres.size]
                    shelf.title = "$genre $suffix"
                    items(
                        rowRequest(parentId, type).copy(
                            genres = listOf(genre),
                            sortBy = listOf(ItemSortBy.RANDOM),
                            limit = ROW,
                        ),
                    ).map { BaseItem(it) }
                }
            }

        private fun rowRequest(
            parentId: UUID,
            type: BaseItemKind,
        ) = GetItemsRequest(
            userId = userId,
            parentId = parentId,
            includeItemTypes = listOf(type),
            recursive = true,
            fields = SlimItemFields,
            enableUserData = true,
        )

        private suspend fun items(request: GetItemsRequest): List<BaseItemDto> =
            api.itemsApi
                .getItems(request)
                .content.items

        private suspend fun <T> safe(block: suspend () -> List<T>): List<T> =
            try {
                block()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Library home: could not load part of the banner")
                emptyList()
            }

        companion object {
            const val ROW = 20
            private const val FEATURED_COUNT = 6
            private const val PEOPLE_MAX = 15
            private const val SEASONS_SHOWN = 8
            private const val MIN_FRANCHISE = 4

            /** The decade rows, newest first. 1960 stands for everything before the 70s */
            val DECADES = listOf(2020, 2010, 2000, 1990, 1980, 1970, 1960)

            val PEOPLE_ROW = HomeRowViewOptions(showTitles = true)

            val WIDE_ROW = HomeRowViewOptions.continueWatchingDefault

            val STUDIO_ROW =
                HomeRowViewOptions(
                    heightDp = 110,
                    aspectRatio = AspectRatio.WIDE,
                    imageType = ViewOptionImageType.THUMB,
                    showTitles = true,
                    useSeries = false,
                )

            val NETWORKS =
                listOf(
                    "HBO",
                    "Netflix",
                    "Apple TV+",
                    "FX",
                    "AMC",
                    "Hulu",
                    "Prime Video",
                    "Amazon",
                    "Disney+",
                    "Showtime",
                    "Starz",
                    "Peacock",
                    "Paramount+",
                    "BBC One",
                    "BBC Two",
                    "Channel 4",
                    "NBC",
                    "ABC",
                    "CBS",
                    "FOX",
                    "The CW",
                    "Adult Swim",
                    "Comedy Central",
                    "Cartoon Network",
                    "Nickelodeon",
                    "Disney Channel",
                    "History",
                    "Discovery",
                    "National Geographic",
                    "PBS",
                )

            val ANIME_STUDIOS =
                listOf(
                    "Studio Ghibli",
                    "Kyoto Animation",
                    "MAPPA",
                    "Ufotable",
                    "CoMix Wave Films",
                    "Production I.G",
                    "Madhouse",
                    "Bones",
                    "Toei Animation",
                    "Sunrise",
                    "Studio Pierrot",
                    "Wit Studio",
                    "TMS Entertainment",
                    "Studio Chizu",
                    "Science SARU",
                    "Studio Ponoc",
                    "A-1 Pictures",
                    "CloverWorks",
                    "Trigger",
                    "OLM",
                    "Shaft",
                )

            /** Best Picture winners: title and the year the film came out */
            val BEST_PICTURE =
                listOf(
                    "Gone with the Wind" to 1939,
                    "Casablanca" to 1942,
                    "All About Eve" to 1950,
                    "On the Waterfront" to 1954,
                    "The Bridge on the River Kwai" to 1957,
                    "Ben-Hur" to 1959,
                    "West Side Story" to 1961,
                    "Lawrence of Arabia" to 1962,
                    "My Fair Lady" to 1964,
                    "The Sound of Music" to 1965,
                    "Oliver!" to 1968,
                    "Midnight Cowboy" to 1969,
                    "Patton" to 1970,
                    "The French Connection" to 1971,
                    "The Godfather" to 1972,
                    "The Sting" to 1973,
                    "The Godfather Part II" to 1974,
                    "One Flew Over the Cuckoo's Nest" to 1975,
                    "Rocky" to 1976,
                    "Annie Hall" to 1977,
                    "The Deer Hunter" to 1978,
                    "Kramer vs. Kramer" to 1979,
                    "Ordinary People" to 1980,
                    "Chariots of Fire" to 1981,
                    "Gandhi" to 1982,
                    "Terms of Endearment" to 1983,
                    "Amadeus" to 1984,
                    "Out of Africa" to 1985,
                    "Platoon" to 1986,
                    "The Last Emperor" to 1987,
                    "Rain Man" to 1988,
                    "Driving Miss Daisy" to 1989,
                    "Dances with Wolves" to 1990,
                    "The Silence of the Lambs" to 1991,
                    "Unforgiven" to 1992,
                    "Schindler's List" to 1993,
                    "Forrest Gump" to 1994,
                    "Braveheart" to 1995,
                    "The English Patient" to 1996,
                    "Titanic" to 1997,
                    "Shakespeare in Love" to 1998,
                    "American Beauty" to 1999,
                    "Gladiator" to 2000,
                    "A Beautiful Mind" to 2001,
                    "Chicago" to 2002,
                    "The Lord of the Rings: The Return of the King" to 2003,
                    "Million Dollar Baby" to 2004,
                    "Crash" to 2005,
                    "The Departed" to 2006,
                    "No Country for Old Men" to 2007,
                    "Slumdog Millionaire" to 2008,
                    "The Hurt Locker" to 2009,
                    "The King's Speech" to 2010,
                    "The Artist" to 2011,
                    "Argo" to 2012,
                    "12 Years a Slave" to 2013,
                    "Birdman" to 2014,
                    "Birdman or (The Unexpected Virtue of Ignorance)" to 2014,
                    "Spotlight" to 2015,
                    "Moonlight" to 2016,
                    "The Shape of Water" to 2017,
                    "Green Book" to 2018,
                    "Parasite" to 2019,
                    "Nomadland" to 2020,
                    "CODA" to 2021,
                    "Everything Everywhere All at Once" to 2022,
                    "Oppenheimer" to 2023,
                    "Anora" to 2024,
                )
        }
    }

/** An anime season: Winter (Jan-Mar), Spring (Apr-Jun), Summer (Jul-Sep) and Fall (Oct-Dec) */
data class AnimeSeason(
    val year: Int,
    val quarter: Int,
) {
    val title: String get() = "${NAMES[quarter]} $year"
    val start: LocalDate get() = LocalDate.of(year, quarter * 3 + 1, 1)
    val end: LocalDate get() = start.plusMonths(3)

    fun previous() = if (quarter == 0) AnimeSeason(year - 1, 3) else AnimeSeason(year, quarter - 1)

    companion object {
        private val NAMES = listOf("Winter", "Spring", "Summer", "Fall")

        fun current(today: LocalDate = LocalDate.now()) = AnimeSeason(today.year, (today.monthValue - 1) / 3)

        fun recent(count: Int): List<AnimeSeason> = generateSequence(current()) { it.previous() }.take(count).toList()
    }
}
