package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.ServerUserConfig
import com.github.damontecres.wholphin.ui.HomeRowFields
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import timber.log.Timber
import java.time.LocalDate
import java.util.UUID

/**
 * Which of these items are in the anime libraries
 */
internal suspend fun HomeViewModel.animeIdsAmong(
    userId: UUID,
    ids: List<UUID>,
): Set<UUID> {
    if (animeLibraryIds.isEmpty() || ids.isEmpty()) return emptySet()
    val all = allAnimeIds(userId)
    return ids.filter { it in all }.toSet()
}

/**
 * All movie & show ids in the anime libraries. Asking the server "which of these ids are in
 * this library" doesn't work (it ignores the library when ids are given), so the whole
 * list of anime ids is fetched once and checked here instead.
 */
internal suspend fun HomeViewModel.allAnimeIds(userId: UUID): Set<UUID> {
    val libs = animeLibraryIds
    animeIdCache?.let { (cachedLibs, at, ids) ->
        if (cachedLibs == libs && System.currentTimeMillis() - at < ANIME_ID_CACHE_MS) return ids
    }
    // Benflix: the list is saved on the device for a day, so opening the app doesn't download it again
    val fileKey = "anime-ids-$userId-${libs.joinToString(",").hashCode()}"
    diskCache.read(fileKey, AnimeIdsFile.serializer(), ANIME_ID_FILE_MAX_AGE_MS)?.let { saved ->
        val ids = saved.ids.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }.toSet()
        animeIdCache = Triple(libs, System.currentTimeMillis(), ids)
        return ids
    }
    val ids =
        libs
            .flatMap { libraryId ->
                try {
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userId,
                                parentId = libraryId,
                                recursive = true,
                                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                enableImages = false,
                                enableUserData = false,
                            ),
                        ).content.items
                        .map { it.id }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Error listing anime library %s", libraryId)
                    emptyList()
                }
            }.toSet()
    animeIdCache = Triple(libs, System.currentTimeMillis(), ids)
    if (ids.isNotEmpty()) {
        diskCache.write(fileKey, AnimeIdsFile.serializer(), AnimeIdsFile(ids.map { it.toString() }))
    }
    return ids
}

/**
 * Movies & shows from both anime libraries together. Each library is asked separately
 * (the server only takes one library at a time) and the answers are merged.
 */
internal suspend fun HomeViewModel.animeItems(
    userId: UUID,
    request: GetItemsRequest,
): List<BaseItemDto> =
    animeLibraryIds.flatMap { libraryId ->
        try {
            api.itemsApi
                .getItems(
                    request.copy(
                        userId = userId,
                        parentId = libraryId,
                        recursive = true,
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        fields = HomeRowFields,
                    ),
                ).content.items
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Timber.w(ex, "Error loading from anime library %s", libraryId)
            emptyList()
        }
    }

/**
 * "Top 10 anime": anime trending this week first (from Seerr), then the best rated
 * anime from this year and last year, then the best rated anime of all time
 */
internal suspend fun HomeViewModel.loadAnimeTop(
    userDto: ServerUserConfig,
    trendingIds: List<UUID>,
): List<BaseItem> {
    if (animeLibraryIds.isEmpty()) return emptyList()
    return try {
        val trending =
            if (trendingIds.isNotEmpty()) {
                val byId =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userDto.id,
                                ids = trendingIds.take(TRENDING_TOP),
                                fields = HomeRowFields,
                            ),
                        ).content.items
                        .associateBy { it.id }
                trendingIds.mapNotNull { byId[it] }
            } else {
                emptyList()
            }
        val year = LocalDate.now().year
        val bestRated =
            GetItemsRequest(
                imageTypes = listOf(ImageType.PRIMARY),
                sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                sortOrder = listOf(SortOrder.DESCENDING),
                limit = TRENDING_TOP,
            )
        val recent =
            if (trending.size < TRENDING_TOP) {
                animeItems(userDto.id, bestRated.copy(years = listOf(year, year - 1)))
                    .sortedByDescending { it.communityRating ?: 0f }
            } else {
                emptyList()
            }
        val allTime =
            if (trending.size + recent.size < TRENDING_TOP) {
                animeItems(userDto.id, bestRated)
                    .sortedByDescending { it.communityRating ?: 0f }
            } else {
                emptyList()
            }
        (trending + recent + allTime)
            .distinctBy { it.id }
            .take(TRENDING_TOP)
            .map { BaseItem(it) }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.e(ex, "Error loading top 10 anime")
        emptyList()
    }
}

/**
 * Anime picks: "Because you watched <last anime>", "Hidden gem anime" (well rated,
 * not watched yet) and two anime genre rows, like "Action anime"
 */
internal suspend fun HomeViewModel.loadAnimeRows(userDto: ServerUserConfig) {
    if (animeLibraryIds.isEmpty()) {
        mutableState.update { it.copy(animeRows = emptyList()) }
        return
    }
    try {
        val taste = loadTaste(userDto.id)
        val rows = mutableListOf<ThemedRow>()
        val shown = mutableSetOf<UUID>()

        fun add(
            title: String,
            items: List<BaseItemDto>,
        ) {
            val fresh =
                items
                    .filter { it.id !in shown && taste.keep(it) }
                    .distinctBy { it.id }
                    .take(EXTRA_ROW_MAX)
            if (fresh.size >= ANIME_ROW_MIN) {
                rows.add(ThemedRow(title, fresh.map { BaseItem(it) }))
                shown.addAll(fresh.map { it.id })
            }
        }

        // Because you watched <anime>: the last anime watched, then similar anime
        val lastWatched =
            animeLibraryIds
                .flatMap { libraryId ->
                    try {
                        api.itemsApi
                            .getItems(
                                GetItemsRequest(
                                    userId = userDto.id,
                                    parentId = libraryId,
                                    recursive = true,
                                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                                    isPlayed = true,
                                    sortBy = listOf(ItemSortBy.DATE_PLAYED),
                                    sortOrder = listOf(SortOrder.DESCENDING),
                                    limit = 1,
                                ),
                            ).content.items
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Error loading last watched anime")
                        emptyList()
                    }
                }.maxByOrNull { it.userData?.lastPlayedDate?.toEpochSecond(java.time.ZoneOffset.UTC) ?: 0L }
        val source =
            lastWatched?.let {
                if (it.type == BaseItemKind.EPISODE) {
                    it.seriesId?.let { id -> it.seriesName?.let { name -> id to name } }
                } else {
                    it.name?.let { name -> it.id to name }
                }
            }
        if (source != null) {
            val similar =
                api.libraryApi
                    .getSimilarItems(
                        GetSimilarItemsRequest(
                            userId = userDto.id,
                            itemId = source.first,
                            fields = HomeRowFields,
                            limit = ANIME_SIMILAR_FETCH,
                        ),
                    ).content.items
            // Keep only anime, so this row is all anime
            val anime = animeIdsAmong(userDto.id, similar.map { it.id })
            shown.add(source.first)
            add(
                context.getString(R.string.because_you_watched_row, source.second),
                similar.filter { it.id in anime },
            )
        }

        // Hidden gem anime: well rated, not watched yet
        add(
            context.getString(R.string.hidden_gem_anime),
            animeItems(
                userDto.id,
                GetItemsRequest(
                    isPlayed = false,
                    minCommunityRating = ANIME_GEM_MIN_RATING,
                    sortBy = listOf(ItemSortBy.RANDOM),
                    limit = EXTRA_ROW_MAX,
                ),
            ).shuffled(),
        )

        // Two anime genre rows, picked at random each visit
        val genres =
            animeLibraryIds
                .flatMap { libraryId ->
                    try {
                        api.genresApi
                            .getGenres(
                                userId = userDto.id,
                                parentId = libraryId,
                                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            ).content.items
                            .mapNotNull { it.name }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Error loading anime genres")
                        emptyList()
                    }
                }.distinct()
                // "Anime" itself is often a genre on anime, which would make an "Anime anime" row
                .filterNot { it.contains("anime", ignoreCase = true) || it.contains("animation", ignoreCase = true) }
                .shuffled()
        var genreCount = 0
        for (genre in genres) {
            if (genreCount >= ANIME_GENRE_ROWS) break
            val before = rows.size
            add(
                context.getString(R.string.anime_genre_row, genre),
                animeItems(
                    userDto.id,
                    GetItemsRequest(
                        genres = listOf(genre),
                        sortBy = listOf(ItemSortBy.RANDOM),
                        limit = EXTRA_ROW_MAX,
                    ),
                ).shuffled(),
            )
            if (rows.size > before) genreCount++
        }
        mutableState.update { it.copy(animeRows = rows) }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.e(ex, "Error loading anime rows")
    }
}
