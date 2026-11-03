package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.ServerUserConfig
import com.github.damontecres.wholphin.services.Celebration
import com.github.damontecres.wholphin.services.CelebrationNow
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.LogoEffect
import com.github.damontecres.wholphin.services.holidayFor
import com.github.damontecres.wholphin.ui.HomeItemFields
import com.github.damontecres.wholphin.ui.HomeRowFields
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.time.LocalDate
import java.time.MonthDay
import java.util.UUID
import kotlin.random.Random

/**
 * Load the items for the rotating banner at the top of the home page:
 * the newest movies & shows plus a few random unwatched picks
 */
internal suspend fun HomeViewModel.loadFeatured(userDto: ServerUserConfig) {
    coroutineScope {
        // Benflix: every source loads at the same time, and slow ones are skipped this time
        // instead of holding up the whole banner
        val tasteAsync = async { loadTaste(userDto.id) }
        val baseRequest =
            GetItemsRequest(
                userId = userDto.id,
                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                recursive = true,
                imageTypes = listOf(ImageType.BACKDROP),
                fields = HomeRowFields,
            )
        // Each source is loaded separately so one failing doesn't empty the whole banner
        val resume =
            featuredSourceAsync("resume") {
                api.itemsApi
                    .getResumeItems(
                        GetResumeItemsRequest(
                            userId = userDto.id,
                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                            fields = HomeRowFields,
                            enableUserData = true,
                            limit = FEATURED_RESUME,
                        ),
                    ).content.items
                    .map { FeaturedItem(BaseItem(it), FeaturedReason.RESUME) }
            }
        val because =
            featuredSourceAsync("because") {
                val lastWatched =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userDto.id,
                                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                                recursive = true,
                                isPlayed = true,
                                sortBy = listOf(ItemSortBy.DATE_PLAYED),
                                sortOrder = listOf(SortOrder.DESCENDING),
                                limit = 1,
                            ),
                        ).content.items
                        .firstOrNull()
                val sourceId =
                    if (lastWatched?.type == BaseItemKind.EPISODE) lastWatched?.seriesId else lastWatched?.id
                val sourceName =
                    if (lastWatched?.type == BaseItemKind.EPISODE) lastWatched?.seriesName else lastWatched?.name
                if (sourceId == null || sourceName == null) {
                    emptyList()
                } else {
                    api.libraryApi
                        .getSimilarItems(
                            GetSimilarItemsRequest(
                                userId = userDto.id,
                                itemId = sourceId,
                                fields = HomeRowFields,
                                limit = 12,
                            ),
                        ).content.items
                        .filter { !it.backdropImageTags.isNullOrEmpty() && it.userData?.played != true }
                        .shuffled()
                        .take(FEATURED_BECAUSE)
                        .map { FeaturedItem(BaseItem(it), FeaturedReason.BECAUSE, becauseOf = sourceName) }
                }
            }
        val liked =
            featuredSourceAsync("liked") {
                val likedItem = tasteAsync.await().liked
                val name = likedItem?.name
                if (likedItem == null || name == null) {
                    emptyList()
                } else {
                    api.libraryApi
                        .getSimilarItems(
                            GetSimilarItemsRequest(
                                userId = userDto.id,
                                itemId = likedItem.id,
                                fields = HomeRowFields,
                                limit = 12,
                            ),
                        ).content.items
                        .filter { !it.backdropImageTags.isNullOrEmpty() && it.userData?.played != true }
                        .shuffled()
                        .take(FEATURED_BECAUSE)
                        .map { FeaturedItem(BaseItem(it), FeaturedReason.BECAUSE, becauseOf = name) }
                }
            }
        val newest =
            featuredSourceAsync("newest") {
                api.itemsApi
                    .getItems(
                        baseRequest.copy(
                            sortBy = listOf(ItemSortBy.DATE_CREATED),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            limit = FEATURED_NEWEST,
                        ),
                    ).content.items
                    .map { FeaturedItem(BaseItem(it), FeaturedReason.NEW) }
            }
        val favorites =
            featuredSourceAsync("favorites") {
                api.itemsApi
                    .getItems(
                        baseRequest.copy(
                            sortBy = listOf(ItemSortBy.RANDOM),
                            isFavorite = true,
                            limit = FEATURED_FAVORITES,
                        ),
                    ).content.items
                    .map { FeaturedItem(BaseItem(it), FeaturedReason.FAVORITE) }
            }
        val picks =
            featuredSourceAsync("picks") {
                api.itemsApi
                    .getItems(
                        baseRequest.copy(
                            sortBy = listOf(ItemSortBy.RANDOM),
                            isPlayed = false,
                            limit = FEATURED_PICKS,
                        ),
                    ).content.items
                    .map { FeaturedItem(BaseItem(it), FeaturedReason.PICK) }
            }
        // From 6 PM: one well-rated movie you haven't seen leads the banner as Tonight's pick.
        // It stays the same all evening (a new one each night) and skips thumbs-down titles.
        val tonight =
            if (java.time.LocalTime
                    .now()
                    .hour >= TONIGHT_FROM_HOUR
            ) {
                featuredSourceAsync("tonight") {
                    // Benflix speed: count the fitting movies first, then fetch only the
                    // few around tonight's spot instead of 200 of them
                    val tonightRequest =
                        baseRequest.copy(
                            includeItemTypes = listOf(BaseItemKind.MOVIE),
                            isPlayed = false,
                            minCommunityRating = TONIGHT_MIN_RATING,
                            sortBy = listOf(ItemSortBy.SORT_NAME),
                        )
                    val total =
                        api.itemsApi
                            .getItems(tonightRequest.copy(limit = 0, enableTotalRecordCount = true))
                            .content.totalRecordCount
                            .coerceAtMost(TONIGHT_POOL)
                    if (total <= 0) {
                        emptyList()
                    } else {
                        val day =
                            java.time.LocalDate
                                .now()
                                .toEpochDay()
                        val index = Math.floorMod(day * 31 + userDto.id.hashCode(), total.toLong()).toInt()
                        // A few extra, in case tonight's one got a thumbs down
                        api.itemsApi
                            .getItems(tonightRequest.copy(startIndex = index, limit = TONIGHT_FETCH))
                            .content.items
                            .firstOrNull { it.userData?.likes != false }
                            ?.let { listOf(FeaturedItem(BaseItem(it), FeaturedReason.TONIGHT, becauseOf = userDto.name)) }
                            .orEmpty()
                    }
                }
            } else {
                null
            }
        // Benflix "On this day": a movie that came out on today's date, years ago
        val onThisDay = featuredSourceAsync("onThisDay") { onThisDay(userDto.id, baseRequest) }
        // Take turns between the sources so the banner mixes them
        val taste = tasteAsync.await()
        val sources =
            listOf(tonight, onThisDay, resume, because, liked, newest, favorites, picks)
                .map { it?.await().orEmpty() }
        val featured =
            (0 until sources.maxOf { it.size })
                .flatMap { i -> sources.mapNotNull { it.getOrNull(i) } }
                .filter { taste.keep(it.item) }
                .distinctBy { it.item.id }
                .take(FEATURED_MAX)
        mutableState.update { it.copy(featured = featured) }
        // Benflix speed: keep the small banner file up to date as soon as the banner loads,
        // so next time it can show straight away
        if (featured.isNotEmpty() && state.value.kids == KidsMode.isActive) {
            try {
                diskCache.write(
                    homeBannerKey(userDto.id),
                    ListSerializer(SnapshotFeatured.serializer()),
                    featured.map { SnapshotFeatured(it.item, it.reason, it.becauseOf) },
                )
            } catch (ex: Exception) {
                Timber.w(ex, "Could not save home banner")
            }
        }
    }
}

/**
 * Movies released on today's date in an earlier year. Jellyfin can't search by day of the
 * year, so the release dates of the movies are checked once a day and remembered.
 */
internal suspend fun HomeViewModel.onThisDay(
    userId: UUID,
    baseRequest: GetItemsRequest,
): List<FeaturedItem> {
    val today = java.time.LocalDate.now()
    val key = "$today/$userId/${KidsMode.isActive}"
    // Benflix: the day's list is also saved on the device, so opening the app again the
    // same day doesn't download every movie's details again
    val fileKey = "on-this-day-$key"
    val ids =
        onThisDayCache?.takeIf { it.first == key }?.second
            ?: diskCache
                .read(fileKey, OnThisDayFile.serializer(), ON_THIS_DAY_FILE_MAX_AGE_MS)
                ?.ids
                ?.mapNotNull { it.toUUIDOrNull() }
                ?.also { onThisDayCache = key to it }
            ?: api.itemsApi
                .getItems(
                    GetItemsRequest(
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                        recursive = true,
                        enableImages = false,
                        enableUserData = false,
                        limit = ON_THIS_DAY_POOL,
                    ),
                ).content.items
                .filter {
                    val date = it.premiereDate ?: return@filter false
                    date.monthValue == today.monthValue && date.dayOfMonth == today.dayOfMonth && date.year < today.year
                }.map { it.id }
                .also {
                    onThisDayCache = key to it
                    diskCache.write(fileKey, OnThisDayFile.serializer(), OnThisDayFile(it.map { id -> id.toString() }))
                }
    val id = ids.randomOrNull() ?: return emptyList()
    val movie =
        api.itemsApi
            .getItems(baseRequest.copy(ids = listOf(id), includeItemTypes = listOf(BaseItemKind.MOVIE)))
            .content.items
            .firstOrNull() ?: return emptyList()
    val years = today.year - (movie.premiereDate?.year ?: return emptyList())
    val label = if (years == 1) "Came out 1 year ago today" else "Came out $years years ago today"
    return listOf(FeaturedItem(BaseItem(movie), FeaturedReason.ON_THIS_DAY, becauseOf = label))
}

/** Benflix Kids "Watch it again": what's been watched most on Kids on this device */
internal suspend fun HomeViewModel.loadKidsAgain(userId: UUID) {
    try {
        val ids =
            com.github.damontecres.wholphin.services.BenflixKidsLog
                .favouriteIds(context)
                .mapNotNull { it.toUUIDOrNull() }
                .take(KIDS_AGAIN_MAX)
        if (ids.size < KIDS_AGAIN_MIN) return
        val byId =
            api.itemsApi
                .getItems(GetItemsRequest(userId = userId, ids = ids, fields = HomeItemFields))
                .content.items
                .associateBy { it.id }
        val items = ids.mapNotNull { byId[it] }.map { BaseItem(it) }
        if (items.size >= KIDS_AGAIN_MIN) {
            mutableState.update { it.copy(kidsAgain = ThemedRow("Watch it again", items)) }
        }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.w(ex, "Kids: couldn't load Watch it again")
    }
}

internal suspend fun HomeViewModel.featuredSource(
    name: String,
    block: suspend () -> List<FeaturedItem>,
): List<FeaturedItem> =
    try {
        block.invoke()
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.e(ex, "Error loading featured items: %s", name)
        emptyList()
    }

/**
 * Load what's trending this week from Seerr: the top items already on the server,
 * and, for admins, trending items that are not on the server yet
 */
internal suspend fun HomeViewModel.loadTrending(userDto: ServerUserConfig) {
    try {
        if (!seerrService.active.first()) {
            mutableState.update {
                it.copy(
                    trending = loadServerTop(userDto),
                    trendingMissing = emptyList(),
                    animeTop = loadAnimeTop(userDto, emptyList()),
                )
            }
            return
        }
        // Benflix: the trending pages are asked for all at once, and kept for a while, so
        // coming back to Home doesn't wait on Seerr again
        val cached = trendingCache?.takeIf { System.currentTimeMillis() - it.first < TRENDING_CACHE_MS }?.second
        val discovered = cached ?: fetchTrending()
        val allInLibraryIds =
            discovered
                .mapNotNull { it.jellyfinItemId }
                .distinct()
        // Anime trending titles go to the Top 10 anime row instead
        val animeIds = animeIdsAmong(userDto.id, allInLibraryIds)
        val inLibraryIds = allInLibraryIds.filter { it !in animeIds }.take(TRENDING_TOP)
        val animeTrending = allInLibraryIds.filter { it in animeIds }
        val trending =
            if (inLibraryIds.isNotEmpty()) {
                val items =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userDto.id,
                                ids = inLibraryIds,
                                fields = HomeRowFields,
                            ),
                        ).content.items
                        .associateBy { it.id }
                // Keep the trending order
                inLibraryIds.mapNotNull { id -> items[id]?.let { BaseItem(it) } }
            } else {
                emptyList()
            }
        // Benflix: "Trending, not on your server" is no longer on Home (Ben asked 2026-11-02).
        // The Requests tab covers it.
        val missing = emptyList<DiscoverItem>()
        // Too few trending titles on the server to fill the row, so use the server's own top titles
        val top = if (trending.size < TRENDING_MIN) loadServerTop(userDto) else trending
        mutableState.update { it.copy(trending = top, trendingMissing = missing) }
        val anime = loadAnimeTop(userDto, animeTrending)
        mutableState.update { it.copy(animeTop = anime) }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.e(ex, "Error loading trending items")
        mutableState.update { it.copy(trending = loadServerTop(userDto), animeTop = loadAnimeTop(userDto, emptyList())) }
    }
}

/** Benflix: all of Seerr's trending pages at the same time, remembered for a while */
internal suspend fun HomeViewModel.fetchTrending(): List<DiscoverItem> {
    val items =
        coroutineScope {
            (1..TRENDING_PAGES)
                .map { page -> async { seerrService.trending(page) } }
                .awaitAll()
                .flatten()
                .filter { it.type == SeerrItemType.MOVIE || it.type == SeerrItemType.TV }
                .distinctBy { it.type to it.id }
        }
    if (items.isNotEmpty()) trendingCache = System.currentTimeMillis() to items
    return items
}

/**
 * The best rated movies & shows on the server from this year and last year,
 * used for the Top 10 row when Seerr isn't set up
 */
internal suspend fun HomeViewModel.loadServerTop(userDto: ServerUserConfig): List<BaseItem> =
    try {
        val year = LocalDate.now().year
        api.itemsApi
            .getItems(
                GetItemsRequest(
                    userId = userDto.id,
                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                    recursive = true,
                    years = listOf(year, year - 1),
                    imageTypes = listOf(ImageType.PRIMARY),
                    sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    // Extra, because anime is taken out below
                    limit = TRENDING_TOP * 4,
                    fields = HomeRowFields,
                ),
            ).content.items
            .let { items ->
                val anime = animeIdsAmong(userDto.id, items.map { it.id })
                items.filter { it.id !in anime }
            }.take(TRENDING_TOP)
            .map { BaseItem(it) }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.e(ex, "Error loading server top items")
        emptyList()
    }

/**
 * Birthday banner on the profile's birthday, otherwise a holiday banner
 */
internal suspend fun HomeViewModel.loadCelebration(userDto: ServerUserConfig) {
    val today = LocalDate.now()
    val birthday = if (KidsMode.isActive) null else birthdayService.get(userDto.id)
    val celebration =
        if (birthday != null && birthday == MonthDay.from(today)) {
            Celebration(
                listOfNotNull("Happy Birthday", userDto.name?.takeIf { it.isNotBlank() }).joinToString(", "),
                "🎂",
                0xFFFF5CA8,
                LogoEffect.CONFETTI,
            )
        } else {
            holidayFor(today)
        }
    CelebrationNow.set(celebration)
    mutableState.update { it.copy(celebration = celebration) }
}

/**
 * Person spotlight: an actor from what this profile watched lately, with their other
 * movies and shows on Benflix (not watched yet first)
 */
internal suspend fun HomeViewModel.loadSpotlight(userDto: ServerUserConfig) {
    try {
        val taste = loadTaste(userDto.id)
        val recent =
            api.itemsApi
                .getItems(
                    GetItemsRequest(
                        userId = userDto.id,
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        recursive = true,
                        isPlayed = true,
                        sortBy = listOf(ItemSortBy.DATE_PLAYED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        limit = SPOTLIGHT_POOL,
                        fields = listOf(ItemFields.PEOPLE),
                        enableImages = false,
                    ),
                ).content.items
        val actors =
            recent
                .flatMap { dto ->
                    dto.people
                        .orEmpty()
                        .filter { it.type == PersonKind.ACTOR }
                        .take(SPOTLIGHT_TOP_BILLED)
                }.groupBy { it.id }
                .filterValues { it.size >= 2 }
                .entries
                .sortedByDescending { it.value.size }
                .take(SPOTLIGHT_CHOICES)
        if (actors.isEmpty()) return
        val pick = actors[Random(LocalDate.now().toEpochDay()).nextInt(actors.size)]
        val name = pick.value.first().name ?: return
        val items =
            api.itemsApi
                .getItems(
                    GetItemsRequest(
                        userId = userDto.id,
                        personIds = listOf(pick.key),
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        recursive = true,
                        sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        limit = EXTRA_ROW_MAX,
                        fields = HomeRowFields,
                    ),
                ).content.items
                .filter { taste.keep(it) }
                .sortedBy { it.userData?.played == true }
                .map { BaseItem(it) }
        mutableState.update {
            it.copy(spotlight = items.takeIf { it.size >= 4 }?.let { ThemedRow("Spotlight: $name", it) })
        }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Timber.e(ex, "Error loading person spotlight row")
    }
}
