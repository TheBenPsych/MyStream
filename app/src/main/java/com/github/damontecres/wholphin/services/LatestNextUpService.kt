@file:UseSerializers(
    UUIDSerializer::class,
    LocalDateTimeSerializer::class,
)

package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.util.LocalDateTimeSerializer
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import timber.log.Timber
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Get continue watching and next up items for users
 */
@Singleton
class LatestNextUpService
    @Inject
    constructor(
        private val api: ApiClient,
        private val datePlayedService: DatePlayedService,
        private val displayPreferencesService: DisplayPreferencesService,
        private val favoriteWatchManager: FavoriteWatchManager,
    ) {
        /**
         * Get resume (continue watching) items for a user
         */
        suspend fun getResume(
            userId: UUID,
            limit: Int,
            includeEpisodes: Boolean,
            useSeriesForPrimary: Boolean = true,
        ): List<BaseItem> {
            val request =
                GetResumeItemsRequest(
                    userId = userId,
                    fields = SlimItemFields,
                    limit = limit,
                    mediaTypes = listOf(MediaType.VIDEO),
                    excludeItemTypes = if (!includeEpisodes) listOf(BaseItemKind.EPISODE) else null,
                    enableTotalRecordCount = false,
                )
            val items =
                api.itemsApi
                    .getResumeItems(request)
                    .content
                    .items
                    .map { BaseItem(it, useSeriesForPrimary) }
            return items
        }

        /**
         * Benflix "Your show is back": the first episode of a new season added in the last week,
         * for shows the person had already finished (everything before that season watched).
         * Also remembers them in [NewSeasonBack] so their cards get a NEW SEASON tag.
         */
        suspend fun getNewSeasonsBack(
            userId: UUID,
            useSeriesForPrimary: Boolean = true,
        ): List<BaseItem> {
            if (KidsMode.isActive) return emptyList()
            return try {
                val firstEpisodes =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userId,
                                includeItemTypes = listOf(BaseItemKind.EPISODE),
                                recursive = true,
                                isPlayed = false,
                                minDateCreated = LocalDateTime.now().minusDays(NewSeasonBack.DAYS),
                                sortBy = listOf(ItemSortBy.DATE_CREATED),
                                sortOrder = listOf(SortOrder.DESCENDING),
                                fields = SlimItemFields,
                                enableUserData = true,
                                limit = 200,
                                enableTotalRecordCount = false,
                            ),
                        ).content.items
                        .filter { it.indexNumber == 1 && (it.parentIndexNumber ?: 0) > 1 && it.seriesId != null }
                        .distinctBy { it.seriesId }
                val back =
                    firstEpisodes.filter { ep ->
                        val season = ep.parentIndexNumber ?: return@filter false
                        val seriesId = ep.seriesId ?: return@filter false
                        val unwatched =
                            api.itemsApi
                                .getItems(
                                    GetItemsRequest(
                                        userId = userId,
                                        parentId = seriesId,
                                        includeItemTypes = listOf(BaseItemKind.EPISODE),
                                        recursive = true,
                                        isPlayed = false,
                                        enableTotalRecordCount = false,
                                    ),
                                ).content.items
                        // Finished the whole show before this season: nothing older is left to watch...
                        val olderLeft = unwatched.any { (it.parentIndexNumber ?: 0) in 1 until season }
                        // ...and they really watched it (not just a show nobody started)
                        val watchedSome =
                            api.itemsApi
                                .getItems(
                                    GetItemsRequest(
                                        userId = userId,
                                        parentId = seriesId,
                                        includeItemTypes = listOf(BaseItemKind.EPISODE),
                                        recursive = true,
                                        isPlayed = true,
                                        limit = 1,
                                        enableTotalRecordCount = false,
                                    ),
                                ).content.items
                                .isNotEmpty()
                        !olderLeft && watchedSome
                    }
                NewSeasonBack.ids = back.flatMap { listOfNotNull(it.id, it.seriesId) }.toSet()
                back.map { BaseItem(it, useSeriesForPrimary) }
            } catch (ex: kotlinx.coroutines.CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Couldn't look for new seasons")
                emptyList()
            }
        }

        /**
         * Get next up items for a user
         */
        suspend fun getNextUp(
            userId: UUID,
            limit: Int,
            enableRewatching: Boolean,
            enableResumable: Boolean,
            maxDays: Int,
            useSeriesForPrimary: Boolean = true,
        ): List<BaseItem> {
            val removedSeries = getRemovedFromNextUp(userId)
            val nextUpDateCutoff =
                maxDays.takeIf { it > 0 }?.let { LocalDateTime.now().minusDays(it.toLong()) }
            val request =
                GetNextUpRequest(
                    userId = userId,
                    fields = SlimItemFields,
                    imageTypeLimit = 1,
                    parentId = null,
                    limit = limit,
                    enableResumable = enableResumable,
                    enableUserData = true,
                    enableRewatching = enableRewatching,
                    nextUpDateCutoff = nextUpDateCutoff,
                    enableTotalRecordCount = false,
                )
            val nextUp =
                api.tvShowsApi
                    .getNextUp(request)
                    .content
                    .items
                    .map { BaseItem(it, useSeriesForPrimary) }
                    .filter {
                        val seriesId = it.data.seriesId
                        if (seriesId != null && seriesId in removedSeries) {
                            // User has previously removed the series
                            val lastPlayedDate = it.data.userData?.lastPlayedDate
                            if (lastPlayedDate != null) {
                                // If item played it after it was removed, should include it
                                lastPlayedDate > removedSeries[seriesId]
                            } else {
                                // If unknown last played, filter out
                                false
                            }
                        } else {
                            true
                        }
                    }

            return nextUp
        }

        /**
         * Create the combined Continue Watching & Next Up items
         *
         * @see [DatePlayedService]
         */
        suspend fun buildCombined(
            resume: List<BaseItem>,
            nextUp: List<BaseItem>,
        ): List<BaseItem> =
            withContext(WholphinDispatchers.IO) {
                val start = System.currentTimeMillis()
                val semaphore = Semaphore(3)
                val deferred =
                    nextUp
                        .filter { it.data.seriesId != null }
                        .map { item ->
                            async(WholphinDispatchers.IO) {
                                try {
                                    semaphore.withPermit {
                                        datePlayedService.getLastPlayed(item)
                                    }
                                } catch (ex: Exception) {
                                    Timber.e(ex, "Error fetching %s", item.id)
                                    null
                                }
                            }
                        }

                val nextUpLastPlayed = deferred.awaitAll()
                val timestamps = mutableMapOf<UUID, LocalDateTime?>()
                nextUp.map { it.id }.zip(nextUpLastPlayed).toMap(timestamps)
                resume.forEach { timestamps[it.id] = it.data.userData?.lastPlayedDate }
                val result = (resume + nextUp).sortedByDescending { timestamps[it.id] }
                val duration = (System.currentTimeMillis() - start).milliseconds
                Timber.v("buildCombined took %s", duration)
                return@withContext result
            }

        /**
         * Remove a series from next up
         */
        suspend fun removeFromNextUp(
            userId: UUID,
            episode: BaseItem,
        ) {
            favoriteWatchManager.setWatched(episode.id, false)
            episode.data.seriesId?.let { seriesId ->
                displayPreferencesService.updateDisplayPreferences(userId) {
                    val removedIds =
                        get(REMOVED_KEY)
                            ?.let {
                                Json.decodeFromString<RemovedSeriesIds>(it).value
                            }.orEmpty()
                            .toMutableMap()
                    removedIds[seriesId] = LocalDateTime.now()
                    put(
                        REMOVED_KEY,
                        Json.encodeToString(RemovedSeriesIds(removedIds)),
                    )
                }
            }
        }

        /**
         * Get when series were removed from next up
         */
        suspend fun getRemovedFromNextUp(userId: UUID): Map<UUID, LocalDateTime> =
            displayPreferencesService
                .getDisplayPreferences(userId)
                .customPrefs[REMOVED_KEY]
                ?.let {
                    Json.decodeFromString<RemovedSeriesIds>(it).value
                }.orEmpty()

        suspend fun allowSeriesRemovedFromNextUp(
            userId: UUID,
            seriesId: UUID,
        ) {
            displayPreferencesService.updateDisplayPreferences(userId) {
                val ids =
                    get(REMOVED_KEY)
                        ?.let {
                            Json.decodeFromString<RemovedSeriesIds>(it).value
                        }.orEmpty()
                        .toMutableMap()
                ids.remove(seriesId)
                put(
                    REMOVED_KEY,
                    Json.encodeToString(RemovedSeriesIds(ids)),
                )
            }
        }

        /**
         * Check if user has watched a series since removing it
         */
        suspend fun updateRemovedFromNextUp(userId: UUID) {
            val removed = getRemovedFromNextUp(userId)
            val newRemoved = removed.toMutableMap()
            var changed = false
            removed.forEach { (seriesId, timestamp) ->
                val item =
                    api.itemsApi
                        .getItems(
                            userId = userId,
                            parentId = seriesId,
                            recursive = true,
                            includeItemTypes = listOf(BaseItemKind.EPISODE),
                            sortBy = listOf(ItemSortBy.DATE_PLAYED),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            limit = 1,
                        ).content.items
                        .firstOrNull()
                if (item != null) {
                    val lastPlayed = item.userData?.lastPlayedDate
                    if (lastPlayed != null && lastPlayed > timestamp) {
                        Timber.v("Updating removed next up for series %s", seriesId)
                        newRemoved.remove(seriesId)
                        changed = true
                    }
                } else {
                    // Series doesn't exist anymore
                    Timber.v("Updating removed next up for missing series %s", seriesId)
                    newRemoved.remove(seriesId)
                    changed = true
                }
            }
            if (changed) {
                displayPreferencesService.updateDisplayPreferences(userId) {
                    put(
                        REMOVED_KEY,
                        Json.encodeToString(RemovedSeriesIds(newRemoved)),
                    )
                }
            }
        }

        companion object {
            const val REMOVED_KEY = "removeNextUp"
        }
    }

@Serializable
data class RemovedSeriesIds(
    val value: Map<UUID, LocalDateTime>,
)

/** Benflix: shows that just got a new season, for people who had finished them (see getNewSeasonsBack) */
object NewSeasonBack {
    const val DAYS = 7L

    /** Episode and show ids that get the NEW SEASON tag */
    @Volatile var ids: Set<UUID> = emptySet()

    fun has(item: BaseItem): Boolean = item.id in ids || item.data.seriesId?.let { it in ids } == true
}
