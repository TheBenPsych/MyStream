package com.github.damontecres.wholphin.services

import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.LocationType
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import timber.log.Timber
import java.util.UUID

/**
 * Benflix: which episode a show's Play / Resume button (and Continue Watching) should open.
 *
 * Jellyfin's own "next up" goes by the date an episode was last touched, so an old episode that
 * still has a little saved progress (or was opened again by mistake) can win over the real next
 * one ("Resume S6 E7" when E7 to E23 are all watched). This goes by episode order instead:
 * - find the last watched episode (specials left out)
 * - if an episode after it is part way through, resume that one
 * - otherwise play the first unwatched episode after it
 * Nothing watched yet: the first episode. Everything watched: null (callers keep Jellyfin's answer).
 */
object NextEpisode {
    /** Pure pick from a show's episodes (any order) */
    fun choose(episodes: List<BaseItemDto>): BaseItemDto? {
        val eps =
            episodes
                .filter { (it.parentIndexNumber ?: 1) > 0 && it.locationType != LocationType.VIRTUAL }
                .sortedWith(compareBy({ it.parentIndexNumber ?: 1 }, { it.indexNumber ?: 0 }))
        if (eps.isEmpty()) return null
        val lastWatched = eps.indexOfLast { it.userData?.played == true }
        val after = eps.drop(lastWatched + 1)
        if (after.isEmpty()) return null
        return after.lastOrNull { (it.userData?.playbackPositionTicks ?: 0L) > 0L } ?: after.first()
    }

    /** An episode marked watched that Jellyfin still offers as "resume" or "next up" */
    fun isWatchedEpisode(item: BaseItemDto): Boolean = item.type == BaseItemKind.EPISODE && item.userData?.played == true

    /** Fetch the show's episodes and [choose]; null when it can't tell (callers fall back) */
    suspend fun find(
        api: ApiClient,
        seriesId: UUID,
        userId: UUID? = null,
        fields: List<ItemFields>? = null,
    ): BaseItemDto? =
        try {
            choose(
                api.tvShowsApi
                    .getEpisodes(
                        GetEpisodesRequest(
                            seriesId = seriesId,
                            userId = userId,
                            fields = fields,
                            isMissing = false,
                            enableImages = fields != null,
                            imageTypeLimit = if (fields != null) 1 else null,
                            enableUserData = true,
                        ),
                    ).content.items,
            )
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Timber.w(ex, "NextEpisode: could not list episodes of %s", seriesId)
            null
        }

    /**
     * Swap any already-watched episode in a "next up" list for the show's real next episode.
     * Only touches shows that have that problem, so normal lists cost nothing extra.
     */
    suspend fun fixNextUp(
        api: ApiClient,
        items: List<BaseItemDto>,
        userId: UUID? = null,
        fields: List<ItemFields>? = null,
    ): List<BaseItemDto> {
        if (items.none { isWatchedEpisode(it) }) return items
        return items
            .mapNotNull { item ->
                val seriesId = item.seriesId
                if (!isWatchedEpisode(item) || seriesId == null) {
                    item
                } else {
                    // Everything watched: drop it instead of offering a finished episode
                    find(api, seriesId, userId, fields)
                }
            }.distinctBy { it.id }
    }
}
