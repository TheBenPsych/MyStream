package com.github.damontecres.wholphin.ui.main

import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.ServerUserConfig
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.HomeRowFields
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.LocalDate
import java.util.UUID

/**
 * Load the seasonal themed row, such as horror in October.
 * Holiday rows only use titles that fit the holiday (see [HomeTheme]); if too few are on the
 * server the row falls back to the genre of the week instead of showing random picks.
 */
internal suspend fun HomeViewModel.loadThemed(userDto: ServerUserConfig) {
    try {
        val today = LocalDate.now()
        val themes = listOf(HomeTheme.forDate(today), HomeTheme.weekly(today)).distinct()
        for (theme in themes) {
            val found = findThemeItems(userDto.id, theme)
            Timber.d("Themed row %s found %s items", theme.title, found.size)
            if (found.size < theme.minItems) continue
            val items =
                collapseIntoCollections(userDto.id, found)
                    .take(THEMED_MAX)
                    .map { BaseItem(it) }
            if (items.isNotEmpty()) {
                val title = context.getString(theme.title, theme.titleArg)
                mutableState.update { it.copy(themed = ThemedRow(title, items)) }
                return
            }
        }
        mutableState.update { it.copy(themed = null) }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.e(ex, "Error loading themed row")
    }
}

/**
 * Load the month-long rows (Black History Month, Pride Month and so on). Like holiday rows it only shows
 * hand-picked or tagged titles, and it is hidden when fewer than 4 are on the server.
 */
internal suspend fun HomeViewModel.loadMonthly(userDto: ServerUserConfig) {
    try {
        val rows =
            HomeTheme.forMonth(LocalDate.now()).mapNotNull { theme ->
                val found = findThemeItems(userDto.id, theme)
                Timber.d("Month row %s found %s items", theme.title, found.size)
                if (found.size < theme.minItems) return@mapNotNull null
                val items =
                    collapseIntoCollections(userDto.id, found)
                        .take(THEMED_MAX)
                        .map { BaseItem(it) }
                items.takeIf { it.isNotEmpty() }?.let { ThemedRow(context.getString(theme.title), it) }
            }
        mutableState.update { it.copy(monthly = rows) }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.e(ex, "Error loading month rows")
    }
}

/**
 * Everything on the server that fits [theme]: hand-picked titles, tagged titles and titles
 * with a holiday word in the name come first (shuffled), then genre picks if the theme has a genre.
 */
internal suspend fun HomeViewModel.findThemeItems(
    userId: UUID,
    theme: HomeTheme,
): List<BaseItemDto> =
    coroutineScope {
        val types = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
        val limiter = Semaphore(THEMED_PARALLEL)

        suspend fun query(request: GetItemsRequest): List<BaseItemDto> =
            limiter.withPermit {
                try {
                    api.itemsApi
                        .getItems(request)
                        .content.items
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Themed row lookup failed")
                    emptyList()
                }
            }

        fun search(term: String) =
            GetItemsRequest(
                userId = userId,
                includeItemTypes = types,
                recursive = true,
                searchTerm = term,
                limit = 10,
                fields = HomeRowFields,
            )

        // Hand-picked titles: search by name, then keep only exact name matches
        // (so "Coco" never brings in "Coco Before Chanel")
        val picked =
            theme.titles
                .map { it.substringBefore(':').trimEnd('.', '!', '?').trim() }
                .distinct()
                .map { term ->
                    async { query(search(term)).filter { theme.isPicked(it.name, it.originalTitle) } }
                }
        // Titles with a holiday word in the name, such as "A Christmas Story"
        val named =
            theme.nameWords.map { word ->
                async { query(search(word)).filter { theme.hasNameWord(it.name) || theme.hasNameWord(it.originalTitle) } }
            }
        // TMDB keywords, which Jellyfin saves as tags
        val tagged =
            async {
                if (theme.tags.isEmpty()) {
                    emptyList()
                } else {
                    query(
                        GetItemsRequest(
                            userId = userId,
                            includeItemTypes = types,
                            recursive = true,
                            tags = theme.tags,
                            sortBy = listOf(ItemSortBy.RANDOM),
                            limit = THEMED_FETCH,
                            fields = HomeRowFields,
                        ),
                    )
                }
            }
        val byGenre =
            async {
                if (theme.genres.isNullOrEmpty()) {
                    emptyList()
                } else {
                    query(
                        GetItemsRequest(
                            userId = userId,
                            includeItemTypes = types,
                            recursive = true,
                            genres = theme.genres,
                            sortBy = listOf(ItemSortBy.RANDOM),
                            limit = THEMED_FETCH,
                            fields = HomeRowFields,
                        ),
                    )
                }
            }
        val fitting = (picked.awaitAll().flatten() + named.awaitAll().flatten() + tagged.await()).shuffled()
        (fitting + byGenre.await()).distinctBy { it.id }
    }

internal fun HomeViewModel.collectionIndexKey(userId: UUID) = "collections-$userId-${KidsMode.level}"

/**
 * The saved collection index, from memory or from the saved file (up to a day old).
 * Null when it hasn't been built yet: building it asks the server about every collection,
 * so that only happens in the background ([buildCollectionIndex]).
 */
internal fun HomeViewModel.cachedCollectionIndex(userId: UUID): Map<UUID, BaseItemDto>? {
    val key = collectionIndexKey(userId)
    collectionIndex?.takeIf { it.first == key }?.let { return it.second }
    val saved = diskCache.read(key, CollectionIndexFile.serializer(), COLLECTION_INDEX_MAX_AGE_MS) ?: return null
    val byId = saved.collections.associateBy { it.id }
    val index =
        saved.byMovie
            .mapNotNull { (movie, collection) ->
                val col = byId[UUID.fromString(collection)] ?: return@mapNotNull null
                UUID.fromString(movie) to col
            }.toMap()
    collectionIndex = key to index
    return index
}

/** Build the collection index in the background (once) and save it for next time */
internal suspend fun HomeViewModel.buildCollectionIndex(userId: UUID) {
    if (cachedCollectionIndex(userId) != null) return
    if (collectionIndexJob?.isActive == true) return
    val job = viewModelScope.launchIO { collectionsByMovie(userId) }
    collectionIndexJob = job
    job.join()
}

internal suspend fun HomeViewModel.collectionsByMovie(userId: UUID): Map<UUID, BaseItemDto> {
    val key = collectionIndexKey(userId)
    cachedCollectionIndex(userId)?.let { return it }
    val collections =
        api.itemsApi
            .getItems(
                GetItemsRequest(
                    userId = userId,
                    includeItemTypes = listOf(BaseItemKind.BOX_SET),
                    recursive = true,
                    limit = COLLECTION_INDEX_MAX,
                    fields = HomeRowFields,
                ),
            ).content.items
    val permits = Semaphore(COLLECTION_INDEX_PARALLEL)
    val index =
        collections
            .map { collection ->
                viewModelScope.async(WholphinDispatchers.IO) {
                    permits.withPermit {
                        try {
                            api.itemsApi
                                .getItems(
                                    GetItemsRequest(
                                        userId = userId,
                                        parentId = collection.id,
                                        enableImages = false,
                                        enableUserData = false,
                                        limit = 200,
                                    ),
                                ).content.items
                                .map { it.id to collection }
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            Timber.w(ex, "Error reading collection %s", collection.name)
                            emptyList()
                        }
                    }
                }
            }.awaitAll()
            .flatten()
            .toMap()
    collectionIndex = key to index
    diskCache.write(
        key,
        CollectionIndexFile.serializer(),
        CollectionIndexFile(
            byMovie = index.map { (movie, col) -> movie.toString() to col.id.toString() }.toMap(),
            collections = index.values.distinctBy { it.id },
        ),
    )
    return index
}

/**
 * Swap movies that belong to a collection for the collection itself (once), keeping the order
 */
internal suspend fun HomeViewModel.collapseIntoCollections(
    userId: UUID,
    items: List<BaseItemDto>,
): List<BaseItemDto> {
    // Only use the index if it's already saved: never make the row wait for it
    val byMovie = cachedCollectionIndex(userId) ?: emptyMap()
    val shown = mutableSetOf<UUID>()
    return items.mapNotNull { item ->
        val tile = if (item.type == BaseItemKind.MOVIE) byMovie[item.id] ?: item else item
        tile.takeIf { shown.add(it.id) }
    }
}
