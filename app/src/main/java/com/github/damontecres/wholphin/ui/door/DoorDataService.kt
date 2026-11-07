package com.github.damontecres.wholphin.ui.door

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.LatestNextUpService
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.main.trendingCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benflix redesign: the rows inside the Movies and Shows doors. Every function returns an empty
 * list when something goes wrong, so one bad row never stops the page.
 */
@Singleton
class DoorDataService
    @Inject
    constructor(
        private val api: ApiClient,
        private val seerrService: SeerrService,
        private val latestNextUpService: LatestNextUpService,
    ) {
        private val dislikeLock = Mutex()
        private var dislikes: Triple<UUID, Long, Set<UUID>>? = null

        /** Titles this profile gave a thumbs down, kept out of every row (remembered for 10 minutes) */
        suspend fun disliked(userId: UUID): Set<UUID> =
            dislikeLock.withLock {
                dislikes?.let { (id, at, set) ->
                    if (id == userId &&
                        System.currentTimeMillis() - at < DISLIKE_CACHE_MS
                    ) {
                        return@withLock set
                    }
                }
                val set =
                    safe {
                        api.itemsApi
                            .getItems(
                                GetItemsRequest(
                                    userId = userId,
                                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                    recursive = true,
                                    filters = listOf(ItemFilter.DISLIKES),
                                    enableImages = false,
                                    limit = 500,
                                ),
                            ).content.items
                            .map { it.id }
                    }.toSet()
                dislikes = Triple(userId, System.currentTimeMillis(), set)
                set
            }

        /** Movies started but not finished, newest first */
        suspend fun resumeMovies(userId: UUID): List<BaseItem> =
            safe {
                api.itemsApi
                    .getResumeItems(
                        GetResumeItemsRequest(
                            userId = userId,
                            fields = SlimItemFields,
                            limit = ROW,
                            mediaTypes = listOf(MediaType.VIDEO),
                            includeItemTypes = listOf(BaseItemKind.MOVIE),
                            enableTotalRecordCount = false,
                        ),
                    ).content.items
                    .map { BaseItem(it) }
            }

        /**
         * Shows: half-watched episodes and the next episode of shows being watched, most recent first,
         * one per show. Each card shows the show's poster; the top area names the episode.
         */
        suspend fun keepWatchingEpisodes(userId: UUID): List<BaseItem> =
            safe {
                coroutineScope {
                    val resume =
                        async {
                            api.itemsApi
                                .getResumeItems(
                                    GetResumeItemsRequest(
                                        userId = userId,
                                        fields = SlimItemFields,
                                        limit = ROW,
                                        mediaTypes = listOf(MediaType.VIDEO),
                                        includeItemTypes = listOf(BaseItemKind.EPISODE),
                                        enableTotalRecordCount = false,
                                    ),
                                ).content.items
                                .map { BaseItem(it, true) }
                        }
                    val next =
                        async {
                            api.tvShowsApi
                                .getNextUp(
                                    GetNextUpRequest(
                                        userId = userId,
                                        fields = SlimItemFields,
                                        limit = ROW,
                                        enableResumable = false,
                                        enableUserData = true,
                                        enableRewatching = false,
                                        nextUpDateCutoff = LocalDateTime.now().minusDays(NEXT_UP_DAYS),
                                        enableTotalRecordCount = false,
                                    ),
                                ).content.items
                                .map { BaseItem(it, true) }
                        }
                    latestNextUpService
                        .buildCombined(resume.await(), next.await())
                        .distinctBy { it.data.seriesId ?: it.id }
                        .take(ROW)
                }
            }

        /** My List (favourites) of this kind, most recently added to the server first */
        suspend fun myList(
            userId: UUID,
            type: BaseItemKind,
            limit: Int = ROW,
        ): List<BaseItem> =
            items(
                GetItemsRequest(
                    userId = userId,
                    includeItemTypes = listOf(type),
                    recursive = true,
                    isFavorite = true,
                    sortBy = listOf(ItemSortBy.DATE_CREATED),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    fields = SlimItemFields,
                    enableUserData = true,
                    limit = limit,
                ),
            )

        /** Newest of this kind in these libraries */
        suspend fun newest(
            userId: UUID,
            parentIds: List<UUID>,
            type: BaseItemKind,
            limit: Int = ROW,
        ): List<BaseItem> =
            fromLibraries(parentIds) { parentId ->
                GetItemsRequest(
                    userId = userId,
                    parentId = parentId,
                    includeItemTypes = listOf(type),
                    recursive = true,
                    sortBy = listOf(ItemSortBy.DATE_CREATED),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    fields = SlimItemFields,
                    enableUserData = true,
                    limit = limit,
                )
            }.sortedByDescending { it.data.dateCreated }
                .take(limit)

        /** Of the shows being watched ([seriesIds]), the ones that got a new episode in the last 2 weeks */
        suspend fun newEpisodes(
            userId: UUID,
            seriesIds: List<UUID>,
        ): List<BaseItem> {
            if (seriesIds.isEmpty()) return emptyList()
            val since = LocalDateTime.now().minusDays(NEW_EPISODE_DAYS)
            return items(
                GetItemsRequest(
                    userId = userId,
                    ids = seriesIds,
                    fields = SlimItemFields,
                    enableUserData = true,
                ),
            ).filter { it.data.dateLastMediaAdded?.isAfter(since) == true && (it.data.userData?.unplayedItemCount ?: 0) > 0 }
                .sortedByDescending { it.data.dateLastMediaAdded }
        }

        /**
         * Top 10: what is trending now (from Seerr) that is on the server, otherwise the best rated of
         * this year and last. [anime] picks anime only; otherwise anime is left out.
         */
        suspend fun top10(
            userId: UUID,
            type: BaseItemKind,
            animeIds: Set<UUID>,
            anime: Boolean,
        ): List<BaseItem> {
            fun keep(id: UUID) = if (anime) id in animeIds else id !in animeIds
            val seerrType = if (type == BaseItemKind.MOVIE) SeerrItemType.MOVIE else SeerrItemType.TV
            val trendingIds =
                safe {
                    if (!seerrService.active.first()) return@safe emptyList()
                    trending()
                        .filter { it.type == seerrType }
                        .mapNotNull { it.jellyfinItemId }
                        .distinct()
                        .filter { keep(it) }
                        .take(TOP)
                }
            val trending =
                if (trendingIds.isEmpty()) {
                    emptyList()
                } else {
                    val found =
                        items(GetItemsRequest(userId = userId, ids = trendingIds, fields = SlimItemFields, enableUserData = true))
                            .associateBy { it.id }
                    trendingIds.mapNotNull { found[it] }
                }
            if (trending.size >= TOP_MIN) return trending
            val year = LocalDate.now().year
            val best =
                items(
                    GetItemsRequest(
                        userId = userId,
                        includeItemTypes = listOf(type),
                        recursive = true,
                        years = if (anime) null else listOf(year, year - 1),
                        imageTypes = listOf(ImageType.PRIMARY),
                        sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        fields = SlimItemFields,
                        enableUserData = true,
                        limit = TOP * 6,
                    ),
                ).filter { keep(it.id) }
            return (trending + best).distinctBy { it.id }.take(TOP)
        }

        private val trendingLock = Mutex()

        /** Seerr's trending pages, shared with Home's cache so they are asked for once in a while */
        private suspend fun trending(): List<DiscoverItem> =
            trendingLock.withLock {
                trendingCache?.takeIf { System.currentTimeMillis() - it.first < TRENDING_CACHE_MS }?.let { return@withLock it.second }
                val found =
                    coroutineScope {
                        (1..TRENDING_PAGES)
                            .map { page -> async { safe { seerrService.trending(page) } } }
                            .awaitAll()
                            .flatten()
                            .distinctBy { it.type to it.id }
                    }
                if (found.isNotEmpty()) trendingCache = System.currentTimeMillis() to found
                found
            }

        /**
         * "Because you watched X": titles like the last one of this kind watched. Returns the name of
         * that title and the row.
         */
        suspend fun because(
            userId: UUID,
            type: BaseItemKind,
            exclude: Set<UUID>,
        ): Pair<String, List<BaseItem>>? {
            val watchType = if (type == BaseItemKind.SERIES) BaseItemKind.EPISODE else BaseItemKind.MOVIE
            val played =
                safe {
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userId,
                                includeItemTypes = listOf(watchType),
                                recursive = true,
                                isPlayed = true,
                                sortBy = listOf(ItemSortBy.DATE_PLAYED),
                                sortOrder = listOf(SortOrder.DESCENDING),
                                enableImages = false,
                                limit = 30,
                            ),
                        ).content.items
                }
            val sources =
                played
                    .mapNotNull { dto ->
                        if (watchType == BaseItemKind.EPISODE) {
                            dto.seriesId?.let { id -> dto.seriesName?.let { id to it } }
                        } else {
                            dto.name?.let { dto.id to it }
                        }
                    }.distinctBy { it.first }
                    .filter { it.first !in exclude }
                    .take(BECAUSE_TRIES)
            for ((id, name) in sources) {
                val similar =
                    safe {
                        api.libraryApi
                            .getSimilarItems(
                                GetSimilarItemsRequest(
                                    userId = userId,
                                    itemId = id,
                                    fields = SlimItemFields,
                                    limit = ROW,
                                ),
                            ).content.items
                            .filter { it.type == type && it.id !in exclude }
                            .map { BaseItem(it) }
                    }
                if (similar.size >= BECAUSE_MIN) return name to similar
            }
            return null
        }

        /** A row from one library (Anime, Stand-up): unwatched first, best rated */
        suspend fun fromLibrary(
            userId: UUID,
            parentIds: List<UUID>,
            type: BaseItemKind,
            sortBy: ItemSortBy = ItemSortBy.RANDOM,
            limit: Int = ROW,
        ): List<BaseItem> =
            fromLibraries(parentIds) { parentId ->
                GetItemsRequest(
                    userId = userId,
                    parentId = parentId,
                    includeItemTypes = listOf(type),
                    recursive = true,
                    isPlayed = if (sortBy == ItemSortBy.RANDOM) false else null,
                    imageTypes = listOf(ImageType.PRIMARY),
                    sortBy = listOf(sortBy),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    fields = SlimItemFields,
                    enableUserData = true,
                    limit = limit,
                )
            }.shuffledIf(sortBy == ItemSortBy.RANDOM)
                .take(limit)

        /** Every title in these libraries for the All movies / All shows grid (no stories, to stay light) */
        suspend fun everything(
            userId: UUID,
            parentIds: List<UUID>,
            type: BaseItemKind,
        ): List<BaseItem> =
            fromLibraries(parentIds) { parentId ->
                GetItemsRequest(
                    userId = userId,
                    parentId = parentId,
                    includeItemTypes = listOf(type),
                    recursive = true,
                    sortBy = listOf(ItemSortBy.DATE_CREATED),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    fields = listOf(ItemFields.SORT_NAME, ItemFields.DATE_CREATED, ItemFields.DATE_LAST_MEDIA_ADDED),
                    enableUserData = true,
                    limit = EVERYTHING_LIMIT,
                )
            }

        /** Every id in these libraries (used to tell anime apart), remembered for an hour */
        suspend fun idsIn(
            userId: UUID,
            parentIds: List<UUID>,
            type: BaseItemKind,
        ): Set<UUID> {
            if (parentIds.isEmpty()) return emptySet()
            val key = "$userId-$type-${parentIds.sorted().joinToString()}"
            idCache[key]?.let { (at, ids) -> if (System.currentTimeMillis() - at < ID_CACHE_MS) return ids }
            val ids =
                parentIds
                    .flatMap { parentId ->
                        safe {
                            api.itemsApi
                                .getItems(
                                    GetItemsRequest(
                                        userId = userId,
                                        parentId = parentId,
                                        includeItemTypes = listOf(type),
                                        recursive = true,
                                        enableImages = false,
                                        enableUserData = false,
                                    ),
                                ).content.items
                                .map { it.id }
                        }
                    }.toSet()
            idCache[key] = System.currentTimeMillis() to ids
            return ids
        }

        private val idCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Set<UUID>>>()

        private suspend fun fromLibraries(
            parentIds: List<UUID>,
            request: (UUID) -> GetItemsRequest,
        ): List<BaseItem> =
            if (parentIds.isEmpty()) {
                emptyList()
            } else {
                coroutineScope {
                    parentIds
                        .map { id -> async { items(request(id)) } }
                        .awaitAll()
                        .flatten()
                        .distinctBy { it.id }
                }
            }

        private suspend fun items(request: GetItemsRequest): List<BaseItem> =
            safe {
                api.itemsApi
                    .getItems(request.copy(enableTotalRecordCount = false))
                    .content.items
                    .map { BaseItem(it) }
            }

        private fun <T> List<T>.shuffledIf(shuffle: Boolean) = if (shuffle) shuffled() else this

        private suspend fun <T> safe(block: suspend () -> List<T>): List<T> =
            try {
                block()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Door row failed")
                emptyList()
            }

        companion object {
            const val ROW = 20
            const val TOP = 10
            private const val EVERYTHING_LIMIT = 5000
            private const val TOP_MIN = 5
            private const val BECAUSE_TRIES = 4
            private const val BECAUSE_MIN = 5
            private const val TRENDING_PAGES = 3
            private const val TRENDING_CACHE_MS = 30L * 60 * 1000
            private const val DISLIKE_CACHE_MS = 10L * 60 * 1000
            private const val ID_CACHE_MS = 60L * 60 * 1000
            private const val NEXT_UP_DAYS = 60L
            private const val NEW_EPISODE_DAYS = 14L

            /** Kids rows ask for a little less */
            val rowLimit: Int get() = if (KidsMode.isActive) 15 else ROW
        }
    }
