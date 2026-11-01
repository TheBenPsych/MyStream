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
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
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
                listOf(
                    CheckupSection("Missing episodes", missing.await().orEmpty()),
                    CheckupSection("Movies with no trailer", noTrailer.await().orEmpty()),
                    CheckupSection("Anime with no subtitles", noSubs.await().orEmpty()),
                    CheckupSection("Low quality movies (below 720p)", lowQuality.await().orEmpty()),
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
            ).map { it.toCheckup("quality", it.height?.let { h -> "${h}p" } ?: "Low quality") }

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
        }
    }
