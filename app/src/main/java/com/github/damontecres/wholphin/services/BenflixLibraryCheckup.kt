package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.core.content.edit
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.LocationType
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/** One thing the checkup found, like "Breaking Bad is missing 3 episodes" */
data class CheckupItem(
    val key: String,
    val itemId: UUID,
    val type: BaseItemKind,
    val title: String,
    val detail: String,
)

data class CheckupSection(
    val title: String,
    val items: List<CheckupItem>,
)

/**
 * Benflix "Library checkup" (admin only): finds missing episodes, movies with no trailer,
 * anime with no subtitles and low quality movies. Anything can be hidden so it stops showing.
 */
@Singleton
class BenflixLibraryCheckup
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
    ) {
        suspend fun run(userId: UUID): List<CheckupSection> =
            withContext(WholphinDispatchers.IO) {
                val hidden = hidden()
                val missing = async { safe { missingEpisodes(userId) } }
                val noTrailer = async { safe { moviesWithoutTrailer(userId) } }
                val noSubs = async { safe { animeWithoutSubtitles(userId) } }
                val lowQuality = async { safe { lowQualityMovies(userId) } }
                // Benflix: the bigger checkup
                val pictures = async { safe { missingPictures(userId) } }
                val doubles = async { safe { twoCopies(userId) } }
                val unmatched = async { safe { notMatched(userId) } }
                val unnumbered = async { safe { unnumberedEpisodes(userId) } }
                listOf(
                    CheckupSection("Missing episodes", missing.await().orEmpty()),
                    CheckupSection("Movies with no trailer", noTrailer.await().orEmpty()),
                    CheckupSection("Anime with no subtitles", noSubs.await().orEmpty()),
                    CheckupSection("Low quality movies (below 720p)", lowQuality.await().orEmpty()),
                    CheckupSection("Missing poster or logo", pictures.await().orEmpty()),
                    CheckupSection("Two copies of the same movie", doubles.await().orEmpty()),
                    CheckupSection("Not matched to anything online", unmatched.await().orEmpty()),
                    CheckupSection("Episodes Jellyfin couldn't number", unnumbered.await().orEmpty()),
                ).map { section -> section.copy(items = section.items.filterNot { it.key in hidden }) }
            }

        fun hide(item: CheckupItem) {
            prefs().edit { putStringSet(HIDDEN, hidden() + item.key) }
        }

        fun unhideAll() {
            prefs().edit { remove(HIDDEN) }
        }

        fun hiddenCount() = hidden().size

        private fun hidden(): Set<String> = prefs().getStringSet(HIDDEN, null).orEmpty()

        private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private suspend fun items(request: GetItemsRequest): List<BaseItemDto> =
            api.itemsApi
                .getItems(request)
                .content.items

        private suspend fun missingEpisodes(userId: UUID): List<CheckupItem> {
            val now = LocalDateTime.now(ZoneOffset.UTC)
            return items(
                GetItemsRequest(
                    userId = userId,
                    includeItemTypes = listOf(BaseItemKind.EPISODE),
                    locationTypes = listOf(LocationType.VIRTUAL),
                    recursive = true,
                    fields = listOf(ItemFields.SERIES_PRIMARY_IMAGE),
                    sortBy = listOf(ItemSortBy.SERIES_SORT_NAME),
                    limit = SCAN_LIMIT,
                ),
            ).filter { ep -> ep.premiereDate?.isBefore(now) == true && (ep.parentIndexNumber ?: 0) > 0 }
                .groupBy { it.seriesId }
                .mapNotNull { (seriesId, eps) ->
                    seriesId ?: return@mapNotNull null
                    val codes =
                        eps
                            .sortedWith(compareBy({ it.parentIndexNumber }, { it.indexNumber }))
                            .map { "S${it.parentIndexNumber}E${it.indexNumber}" }
                    CheckupItem(
                        key = "missing:$seriesId:${eps.size}",
                        itemId = seriesId,
                        type = BaseItemKind.SERIES,
                        title = eps.first().seriesName ?: "A show",
                        detail =
                            "${eps.size} missing: " + codes.take(SHOW_CODES).joinToString(", ") +
                                if (codes.size > SHOW_CODES) "…" else "",
                    )
                }.sortedByDescending { it.detail.substringBefore(' ').toIntOrNull() ?: 0 }
                .take(LIST_LIMIT)
        }

        private suspend fun moviesWithoutTrailer(userId: UUID): List<CheckupItem> =
            items(
                GetItemsRequest(
                    userId = userId,
                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                    hasTrailer = false,
                    recursive = true,
                    sortBy = listOf(ItemSortBy.DATE_CREATED),
                    limit = LIST_LIMIT,
                ),
            ).map { it.toCheckup("trailer", "No trailer yet (Trailarr may still find one)") }

        private suspend fun lowQualityMovies(userId: UUID): List<CheckupItem> =
            items(
                GetItemsRequest(
                    userId = userId,
                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                    maxHeight = LOW_QUALITY_HEIGHT,
                    recursive = true,
                    fields = listOf(ItemFields.WIDTH, ItemFields.HEIGHT),
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                    limit = LIST_LIMIT,
                ),
            ).filter {
                // Benflix: a wide-screen 720p movie can be only 536 tall, so a known width of 1200+ isn't low quality
                // (an unknown width counts as 0, so it still shows)
                (it.width ?: 0) < LOW_QUALITY_WIDTH
            }.map { it.toCheckup("quality", it.height?.let { h -> "${h}p" } ?: "Low quality") }

        private suspend fun animeWithoutSubtitles(userId: UUID): List<CheckupItem> {
            val animeLibraries =
                api.userViewsApi
                    .getUserViews(userId = userId)
                    .content.items
                    .filter { it.name?.contains("anime", ignoreCase = true) == true }
            return animeLibraries
                .flatMap { library ->
                    items(
                        GetItemsRequest(
                            userId = userId,
                            parentId = library.id,
                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                            hasSubtitles = false,
                            recursive = true,
                            limit = SCAN_LIMIT,
                        ),
                    )
                }.groupBy { it.seriesId ?: it.id }
                .map { (id, list) ->
                    val first = list.first()
                    if (first.type == BaseItemKind.EPISODE) {
                        CheckupItem(
                            key = "subs:$id:${list.size}",
                            itemId = id,
                            type = BaseItemKind.SERIES,
                            title = first.seriesName ?: "An anime",
                            detail = "${list.size} episode(s) with no subtitles",
                        )
                    } else {
                        first.toCheckup("subs", "No subtitles")
                    }
                }.take(LIST_LIMIT)
        }

        private suspend fun moviesAndShows(
            userId: UUID,
            fields: List<ItemFields>,
        ): List<BaseItemDto> =
            items(
                GetItemsRequest(
                    userId = userId,
                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                    recursive = true,
                    fields = fields,
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                    enableUserData = false,
                    limit = SCAN_LIMIT,
                ),
            )

        private suspend fun missingPictures(userId: UUID): List<CheckupItem> =
            moviesAndShows(userId, emptyList())
                .mapNotNull { item ->
                    val tags = item.imageTags.orEmpty()
                    val missing =
                        listOfNotNull(
                            if (tags[ImageType.PRIMARY] == null) "poster" else null,
                            if (tags[ImageType.LOGO] == null) "logo" else null,
                        )
                    if (missing.isEmpty()) {
                        null
                    } else {
                        item.toCheckup("pictures", "No " + missing.joinToString(" or ") + ". Try Refresh info and pictures.")
                    }
                }.take(LIST_LIMIT)

        private suspend fun twoCopies(userId: UUID): List<CheckupItem> =
            items(
                GetItemsRequest(
                    userId = userId,
                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                    recursive = true,
                    fields = listOf(ItemFields.PROVIDER_IDS),
                    enableUserData = false,
                    limit = SCAN_LIMIT,
                ),
            ).groupBy { movie ->
                movie.providerIds
                    ?.entries
                    ?.firstOrNull { it.key.equals("Tmdb", true) }
                    ?.value
                    ?: (movie.name.orEmpty().lowercase() + "|" + movie.productionYear)
            }.filter { it.value.size > 1 }
                .flatMap { (_, copies) -> copies.map { it.toCheckup("double", "${copies.size} copies on the server") } }
                .take(LIST_LIMIT)

        private suspend fun notMatched(userId: UUID): List<CheckupItem> =
            moviesAndShows(userId, listOf(ItemFields.PROVIDER_IDS))
                .filter {
                    it.providerIds
                        .orEmpty()
                        .values
                        .none { id -> !id.isNullOrBlank() }
                }.map { it.toCheckup("unmatched", "No match. Use Fix match on its page.") }
                .take(LIST_LIMIT)

        private suspend fun unnumberedEpisodes(userId: UUID): List<CheckupItem> =
            items(
                GetItemsRequest(
                    userId = userId,
                    includeItemTypes = listOf(BaseItemKind.EPISODE),
                    recursive = true,
                    enableUserData = false,
                    limit = SCAN_LIMIT * 5,
                ),
            ).filter { it.indexNumber == null }
                .groupBy { it.seriesId ?: it.id }
                .map { (id, eps) ->
                    CheckupItem(
                        key = "unnumbered:$id:${eps.size}",
                        itemId = id,
                        type = if (eps.first().seriesId != null) BaseItemKind.SERIES else BaseItemKind.EPISODE,
                        title = eps.first().seriesName ?: eps.first().name.orEmpty(),
                        detail = "${eps.size} episode(s) with no number. Check the file names (S01E01).",
                    )
                }.take(LIST_LIMIT)

        /**
         * Benflix "What takes the most space": the biggest movies and shows, and things nobody
         * has watched in a year or more. Reads file sizes, so it can take a minute on a big library.
         */
        suspend fun space(userId: UUID): List<CheckupSection> =
            withContext(WholphinDispatchers.IO) {
                val biggest = async { safe { biggestMovies(userId) } }
                val biggestShows = async { safe { biggestShows(userId) } }
                val unwatched = async { safe { nobodyWatched(userId) } }
                listOf(
                    CheckupSection("Biggest movies", biggest.await().orEmpty()),
                    CheckupSection("Biggest shows", biggestShows.await().orEmpty()),
                    CheckupSection("Nobody watched in a year", unwatched.await().orEmpty()),
                )
            }

        private fun BaseItemDto.fileBytes(): Long = mediaSources.orEmpty().sumOf { it.size ?: 0L }

        private suspend fun biggestMovies(userId: UUID): List<CheckupItem> =
            items(
                GetItemsRequest(
                    userId = userId,
                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                    recursive = true,
                    fields = listOf(ItemFields.MEDIA_SOURCES),
                    enableImages = false,
                    enableUserData = false,
                    limit = SCAN_LIMIT * 2,
                ),
            ).sortedByDescending { it.fileBytes() }
                .take(BIGGEST)
                .map { it.toCheckup("big", gbText(it.fileBytes())) }

        private suspend fun biggestShows(userId: UUID): List<CheckupItem> {
            val sizes = HashMap<UUID, Long>()
            val names = HashMap<UUID, String>()
            var start = 0
            while (start < MAX_EPISODES) {
                val page =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userId,
                                includeItemTypes = listOf(BaseItemKind.EPISODE),
                                recursive = true,
                                fields = listOf(ItemFields.MEDIA_SOURCES),
                                enableImages = false,
                                enableUserData = false,
                                enableTotalRecordCount = false,
                                startIndex = start,
                                limit = PAGE,
                            ),
                        ).content.items
                page.forEach { ep ->
                    val id = ep.seriesId ?: return@forEach
                    sizes[id] = (sizes[id] ?: 0L) + ep.fileBytes()
                    ep.seriesName?.let { names[id] = it }
                }
                if (page.size < PAGE) break
                start += PAGE
            }
            return sizes.entries
                .sortedByDescending { it.value }
                .take(BIGGEST)
                .map { (id, bytes) ->
                    CheckupItem(
                        key = "bigshow:$id",
                        itemId = id,
                        type = BaseItemKind.SERIES,
                        title = names[id] ?: "A show",
                        detail = gbText(bytes),
                    )
                }
        }

        /** Movies and shows added over a year ago that no one on the server has played */
        private suspend fun nobodyWatched(userId: UUID): List<CheckupItem> {
            val played = HashSet<UUID>()
            api.userApi.getUsers().content.forEach { user ->
                safe {
                    items(
                        GetItemsRequest(
                            userId = user.id,
                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                            recursive = true,
                            isPlayed = true,
                            enableImages = false,
                            limit = MAX_EPISODES,
                        ),
                    ).forEach { played += it.seriesId ?: it.id }
                }
                safe {
                    // Half-watched counts too
                    items(
                        GetItemsRequest(
                            userId = user.id,
                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                            recursive = true,
                            filters = listOf(org.jellyfin.sdk.model.api.ItemFilter.IS_RESUMABLE),
                            enableImages = false,
                            limit = SCAN_LIMIT,
                        ),
                    ).forEach { played += it.seriesId ?: it.id }
                }
            }
            val yearAgo = LocalDateTime.now(ZoneOffset.UTC).minusYears(1)
            return moviesAndShows(userId, listOf(ItemFields.DATE_CREATED))
                .filter { it.id !in played && it.dateCreated?.isBefore(yearAgo) == true }
                .sortedBy { it.dateCreated }
                .take(LIST_LIMIT * 2)
                .map { it.toCheckup("unwatched", "Added ${it.dateCreated?.toLocalDate()}, nobody has watched it") }
        }

        private fun gbText(bytes: Long): String {
            val gb = bytes / 1_000_000_000.0
            return if (gb >= 1000) "%.1f TB".format(gb / 1000) else "%.0f GB".format(gb)
        }

        private fun BaseItemDto.toCheckup(
            kind: String,
            detail: String,
        ) = CheckupItem(
            key = "$kind:$id",
            itemId = id,
            type = type,
            title = name.orEmpty() + (productionYear?.let { " ($it)" } ?: ""),
            detail = detail,
        )

        private suspend fun <T> safe(block: suspend () -> T): T? =
            try {
                block()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Library checkup: a check could not run")
                null
            }

        companion object {
            private const val PREFS = "benflix_checkup"
            private const val HIDDEN = "hidden"
            private const val SCAN_LIMIT = 2_000
            private const val LIST_LIMIT = 30
            private const val SHOW_CODES = 4
            private const val LOW_QUALITY_HEIGHT = 576
            private const val LOW_QUALITY_WIDTH = 1200
            private const val BIGGEST = 15
            private const val PAGE = 500
            private const val MAX_EPISODES = 40_000
        }
    }
