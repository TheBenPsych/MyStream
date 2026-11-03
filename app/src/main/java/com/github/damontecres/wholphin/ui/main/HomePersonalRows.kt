package com.github.damontecres.wholphin.ui.main

import android.content.Context
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.ServerUserConfig
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.HomeRowFields
import com.github.damontecres.wholphin.ui.main.HomeViewModel.Taste
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SeriesStatus
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import timber.log.Timber
import java.util.UUID
import kotlin.random.Random

/**
 * The profile's thumbs up/down. Most Home rows need it, so it's asked for once and shared
 * for a few seconds instead of every row asking the server again.
 */
internal suspend fun HomeViewModel.loadTaste(userId: UUID): Taste =
    tasteLock.withLock {
        tasteCache?.let { (id, at, taste) ->
            if (id == userId && System.currentTimeMillis() - at < TASTE_CACHE_MS) return@withLock taste
        }
        fetchTaste(userId).also { tasteCache = Triple(userId, System.currentTimeMillis(), it) }
    }

internal suspend fun HomeViewModel.fetchTaste(userId: UUID): Taste {
    fun request(filter: ItemFilter) =
        GetItemsRequest(
            userId = userId,
            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
            recursive = true,
            filters = listOf(filter),
            sortBy = listOf(ItemSortBy.RANDOM),
        )
    val disliked =
        try {
            api.itemsApi
                .getItems(request(ItemFilter.DISLIKES).copy(limit = TASTE_MAX, sortBy = null, enableImages = false))
                .content.items
                .map { it.id }
                .toSet()
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Timber.e(ex, "Error loading dislikes")
            emptySet()
        }
    val liked =
        try {
            api.itemsApi
                .getItems(request(ItemFilter.LIKES).copy(limit = 1))
                .content.items
                .firstOrNull()
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Timber.e(ex, "Error loading likes")
            null
        }
    return Taste(disliked, liked)
}

/**
 * "Because you watched X" (or "Because you liked X"): titles similar to the last
 * movie or show watched, or to one the profile gave a thumbs up
 */
internal suspend fun HomeViewModel.loadBecause(userDto: ServerUserConfig) {
    try {
        val taste = loadTaste(userDto.id)
        // Recently watched (finished or in progress), as shows/movies
        val played =
            api.itemsApi
                .getItems(
                    GetItemsRequest(
                        userId = userDto.id,
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                        recursive = true,
                        isPlayed = true,
                        sortBy = listOf(ItemSortBy.DATE_PLAYED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        limit = BECAUSE_LOOKBACK,
                    ),
                ).content.items
        val resume =
            api.itemsApi
                .getResumeItems(
                    GetResumeItemsRequest(
                        userId = userDto.id,
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                        limit = 5,
                    ),
                ).content.items
        val watched =
            (resume + played)
                .mapNotNull {
                    if (it.type == BaseItemKind.EPISODE) {
                        it.seriesId?.let { id -> it.seriesName?.let { name -> Triple(id, name, false) } }
                    } else {
                        it.name?.let { name -> Triple(it.id, name, false) }
                    }
                }
        val liked = taste.liked?.let { item -> item.name?.let { Triple(item.id, it, true) } }
        // Sometimes start with something the profile gave a thumbs up
        val candidates =
            (if (liked != null && Random.nextBoolean()) listOf(liked) + watched else watched + listOfNotNull(liked))
                .distinctBy { it.first }
                .take(BECAUSE_TRIES)
        for ((sourceId, sourceName, fromLiked) in candidates) {
            val items =
                api.libraryApi
                    .getSimilarItems(
                        GetSimilarItemsRequest(
                            userId = userDto.id,
                            itemId = sourceId,
                            fields = HomeRowFields,
                            limit = BECAUSE_MAX,
                        ),
                    ).content.items
                    .filter { taste.keep(it) }
                    .map { BaseItem(it) }
            if (items.size >= BECAUSE_MIN) {
                val title =
                    context.getString(
                        if (fromLiked) R.string.because_you_liked else R.string.because_you_watched_row,
                        sourceName,
                    )
                mutableState.update { it.copy(because = ThemedRow(title, items)) }
                loadMoreOfGenre(userDto, sourceId, items, taste)
                return
            }
        }
        mutableState.update { it.copy(because = null, becauseMore = null) }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.e(ex, "Error loading because you watched row")
    }
}

/**
 * "New on Benflix": newest movies & shows from every library together
 */
internal suspend fun HomeViewModel.loadNewest(userDto: ServerUserConfig) {
    try {
        val taste = loadTaste(userDto.id)
        val items =
            api.itemsApi
                .getItems(
                    GetItemsRequest(
                        userId = userDto.id,
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        recursive = true,
                        sortBy = listOf(ItemSortBy.DATE_CREATED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        limit = EXTRA_ROW_MAX,
                        fields = HomeRowFields,
                    ),
                ).content.items
                .filter { taste.keep(it) }
                .map { BaseItem(it) }
        mutableState.update {
            it.copy(newest = items.takeIf { it.isNotEmpty() }?.let { ThemedRow(context.getString(R.string.new_on_benflix), it) })
        }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.e(ex, "Error loading new on Benflix row")
    }
}

/**
 * "While you were away": after two weeks or more without opening Benflix, what was
 * added in the meantime. Shows once (the next visit counts as recent again).
 */
internal suspend fun HomeViewModel.loadWelcomeBack(userDto: ServerUserConfig) {
    if (welcomeChecked) return
    welcomeChecked = true
    try {
        val prefs = context.getSharedPreferences(HOME_VISITS, Context.MODE_PRIVATE)
        val key = "lastSeen-${userDto.id}-${KidsMode.isActive}"
        val lastSeen = prefs.getLong(key, 0L)
        val now = System.currentTimeMillis()
        prefs.edit().putLong(key, now).apply()
        if (lastSeen <= 0L || now - lastSeen < WELCOME_BACK_AFTER_MS) return
        val since =
            java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(lastSeen),
                java.time.ZoneId.systemDefault(),
            )
        val items =
            api.itemsApi
                .getItems(
                    GetItemsRequest(
                        userId = userDto.id,
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        recursive = true,
                        sortBy = listOf(ItemSortBy.DATE_CREATED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        limit = EXTRA_ROW_MAX * 3,
                        fields = HomeRowFields,
                    ),
                ).content.items
                .filter { it.dateCreated?.isAfter(since) == true }
                .take(EXTRA_ROW_MAX)
                .map { BaseItem(it) }
        if (items.size >= 3) {
            mutableState.update { it.copy(welcomeBack = ThemedRow("While you were away", items)) }
        }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.e(ex, "Error loading while you were away row")
    }
}

/** "Around the world": two countries with enough movies on Benflix, picked at random each visit */
internal suspend fun HomeViewModel.loadWorld(userDto: ServerUserConfig) {
    try {
        val anime = if (animeLibraryIds.isEmpty()) emptySet() else allAnimeIds(userDto.id)
        val movies =
            api.itemsApi
                .getItems(
                    GetItemsRequest(
                        userId = userDto.id,
                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                        recursive = true,
                        sortBy = listOf(ItemSortBy.RANDOM),
                        limit = WORLD_POOL,
                        fields = HomeRowFields + listOf(ItemFields.PRODUCTION_LOCATIONS),
                    ),
                ).content.items
                .filter { it.id !in anime }
        val byCountry =
            movies
                .groupBy {
                    it.productionLocations
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                }.filterKeys { it.isNotBlank() && it.lowercase() !in HOME_COUNTRIES }
                .filterValues { it.size >= WORLD_MIN }
        val rows =
            byCountry.keys
                .shuffled()
                .take(2)
                .map { country ->
                    val items =
                        byCountry
                            .getValue(country)
                            .sortedByDescending { it.communityRating ?: 0f }
                            .take(EXTRA_ROW_MAX)
                            .map { BaseItem(it) }
                    ThemedRow("Around the world: $country", items)
                }
        mutableState.update { it.copy(world = rows) }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.e(ex, "Error loading around the world rows")
    }
}

/** "Anime airing this season": anime shows still running that got new episodes lately */
internal suspend fun HomeViewModel.loadAiring(userDto: ServerUserConfig) {
    if (animeLibraryIds.isEmpty()) return
    try {
        val cutoff =
            java.time.LocalDateTime
                .now()
                .minusDays(AIRING_DAYS)
        val shows =
            animeItems(
                userDto.id,
                GetItemsRequest(
                    seriesStatus = listOf(SeriesStatus.CONTINUING),
                    sortBy = listOf(ItemSortBy.DATE_LAST_CONTENT_ADDED),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    limit = EXTRA_ROW_MAX,
                ),
            ).filter { it.type == BaseItemKind.SERIES && it.dateLastMediaAdded?.isAfter(cutoff) != false }
                .sortedByDescending { it.dateLastMediaAdded }
                .take(EXTRA_ROW_MAX)
                .map { BaseItem(it) }
        mutableState.update {
            it.copy(airing = shows.takeIf { list -> list.size >= 3 }?.let { list -> ThemedRow("Anime airing this season", list) })
        }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.e(ex, "Error loading anime airing row")
    }
}

/**
 * "Come back to it": movies stopped partway and shows started but not finished, that
 * haven't been touched for a few weeks
 */
internal suspend fun HomeViewModel.loadComeBack(userDto: ServerUserConfig) {
    try {
        val cutoff =
            java.time.LocalDateTime
                .now()
                .minusDays(COME_BACK_DAYS)

        fun request(types: List<BaseItemKind>) =
            GetItemsRequest(
                userId = userDto.id,
                includeItemTypes = types,
                recursive = true,
                sortBy = listOf(ItemSortBy.DATE_PLAYED),
                sortOrder = listOf(SortOrder.DESCENDING),
                limit = COME_BACK_POOL,
                fields = HomeRowFields,
            )
        val movies =
            api.itemsApi
                .getItems(request(listOf(BaseItemKind.MOVIE)).copy(filters = listOf(ItemFilter.IS_RESUMABLE)))
                .content.items
        val shows =
            api.itemsApi
                .getItems(request(listOf(BaseItemKind.SERIES)).copy(isPlayed = false))
                .content.items
                .filter { (it.userData?.playedPercentage ?: 0.0) > 0.0 }
        val items =
            (movies + shows)
                .filter { dto -> dto.userData?.lastPlayedDate?.isBefore(cutoff) == true }
                .sortedByDescending { it.userData?.lastPlayedDate }
                .take(EXTRA_ROW_MAX)
                .map { BaseItem(it) }
        mutableState.update {
            it.copy(comeBack = items.takeIf { it.size >= 3 }?.let { ThemedRow("Come back to it", it) })
        }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.e(ex, "Error loading come back to it row")
    }
}

/**
 * "My List": the profile's favorite movies & shows
 */
internal suspend fun HomeViewModel.loadMyList(userDto: ServerUserConfig) {
    try {
        val items =
            api.itemsApi
                .getItems(
                    GetItemsRequest(
                        userId = userDto.id,
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        recursive = true,
                        isFavorite = true,
                        sortBy = listOf(ItemSortBy.DATE_CREATED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        limit = EXTRA_ROW_MAX,
                        fields = HomeRowFields,
                    ),
                ).content.items
                .map { BaseItem(it) }
        mutableState.update {
            it.copy(myList = items.takeIf { it.isNotEmpty() }?.let { ThemedRow(context.getString(R.string.my_list), it) })
        }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.e(ex, "Error loading my list row")
    }
}

/**
 * "More musicals": right under "Because you watched", more titles from the main genre of
 * whatever was watched last, so the home page reacts the same day
 */
internal suspend fun HomeViewModel.loadMoreOfGenre(
    userDto: ServerUserConfig,
    sourceId: java.util.UUID,
    becauseItems: List<BaseItem>,
    taste: Taste,
) {
    try {
        val genre =
            api.userLibraryApi
                .getItem(itemId = sourceId, userId = userDto.id)
                .content.genres
                ?.firstOrNull()
        if (genre == null) {
            mutableState.update { it.copy(becauseMore = null) }
            return
        }
        val skip = becauseItems.map { it.id }.toSet() + sourceId
        val items =
            api.itemsApi
                .getItems(
                    GetItemsRequest(
                        userId = userDto.id,
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        recursive = true,
                        genres = listOf(genre),
                        sortBy = listOf(ItemSortBy.RANDOM),
                        limit = EXTRA_ROW_MAX,
                        fields = HomeRowFields,
                    ),
                ).content.items
                .filter { it.id !in skip && taste.keep(it) }
                .map { BaseItem(it) }
        mutableState.update {
            it.copy(
                becauseMore =
                    if (items.size >= GENRE_MIN) {
                        ThemedRow(context.getString(R.string.more_of_genre, genre), items)
                    } else {
                        null
                    },
            )
        }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.e(ex, "Error loading more-of-genre row")
    }
}

/**
 * A few genre rows ("Comedy", "Action"...), picked at random each visit
 */
internal suspend fun HomeViewModel.loadGenreRows(userDto: ServerUserConfig) {
    try {
        val taste = loadTaste(userDto.id)
        val genres =
            api.genresApi
                .getGenres(
                    userId = userDto.id,
                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                ).content.items
                .mapNotNull { it.name }
                .shuffled()
        val rows = mutableListOf<ThemedRow>()
        for (genre in genres) {
            if (rows.size >= GENRE_ROWS) break
            val items =
                api.itemsApi
                    .getItems(
                        GetItemsRequest(
                            userId = userDto.id,
                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            recursive = true,
                            genres = listOf(genre),
                            sortBy = listOf(ItemSortBy.RANDOM),
                            limit = EXTRA_ROW_MAX,
                            fields = HomeRowFields,
                        ),
                    ).content.items
                    .filter { taste.keep(it) }
                    .map { BaseItem(it) }
            if (items.size >= GENRE_MIN) rows.add(ThemedRow(genre, items))
        }
        mutableState.update { it.copy(genreRows = rows) }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.e(ex, "Error loading genre rows")
    }
}

/**
 * "Top collections": movie collections like Harry Potter or Marvel,
 * the ones watched the most first
 */
internal suspend fun HomeViewModel.loadCollections(userDto: ServerUserConfig) {
    try {
        val items =
            api.itemsApi
                .getItems(
                    GetItemsRequest(
                        userId = userDto.id,
                        includeItemTypes = listOf(BaseItemKind.BOX_SET),
                        recursive = true,
                        imageTypes = listOf(ImageType.PRIMARY),
                        enableUserData = true,
                        sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        limit = COLLECTIONS_FETCH,
                        fields = HomeRowFields,
                    ),
                ).content.items
                // Most watched first: collections people have seen the most of
                .sortedByDescending { it.userData?.playedPercentage ?: if (it.userData?.played == true) 100.0 else 0.0 }
                .take(COLLECTIONS_MAX)
                .map { BaseItem(it) }
        mutableState.update {
            it.copy(
                collections =
                    if (items.size >= COLLECTIONS_MIN) {
                        ThemedRow(context.getString(R.string.top_collections_row), items)
                    } else {
                        null
                    },
            )
        }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.e(ex, "Error loading collections row")
    }
}
