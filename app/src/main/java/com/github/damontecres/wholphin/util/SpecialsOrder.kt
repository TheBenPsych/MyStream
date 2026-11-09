package com.github.damontecres.wholphin.util

import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import timber.log.Timber
import java.util.UUID

/**
 * Benflix "Specials in the right place": show specials (season 0) slotted where they aired.
 *
 * Jellyfin already puts specials inside seasons when its metadata says where they aired
 * ("airs before season 2, episode 5"). Lots of specials have no such note though (TMDB never gives
 * one), so those were left at the end. For those, Benflix uses the date the special aired and puts
 * it just before the first episode that aired after it.
 */
object SpecialsOrder {
    /** True for a special with no "airs before/after" note but with an air date */
    fun needsPlacing(ep: BaseItemDto): Boolean =
        ep.parentIndexNumber == 0 &&
            ep.premiereDate != null &&
            ep.airsBeforeSeasonNumber == null &&
            ep.airsAfterSeasonNumber == null &&
            ep.airsBeforeEpisodeNumber == null

    /**
     * The whole show's episodes (in the server's order) with the unplaced specials moved to where
     * they aired. Regular episodes keep their order.
     */
    fun arrange(episodes: List<BaseItemDto>): List<BaseItemDto> {
        val loose = episodes.filter(::needsPlacing)
        if (loose.isEmpty()) return episodes
        val out = episodes.filterNot(::needsPlacing).toMutableList()
        // Only regular episodes with dates are used as markers
        if (out.none { (it.parentIndexNumber ?: 0) > 0 && it.premiereDate != null }) return episodes
        loose.sortedBy { it.premiereDate }.forEach { special ->
            val date = special.premiereDate!!
            val at =
                out.indexOfFirst { ep ->
                    (ep.parentIndexNumber ?: 0) > 0 && ep.premiereDate?.isAfter(date) == true
                }
            if (at < 0) out.add(special) else out.add(at, special)
        }
        return out
    }

    /**
     * The episodes for one season out of an [arrange]d list: the season's own episodes plus the
     * specials that aired during it or before the next season started (and, for the first season,
     * the ones before it).
     */
    fun seasonSlice(
        arranged: List<BaseItemDto>,
        seasonNumber: Int,
    ): List<BaseItemDto> {
        val firstOfSeason = arranged.indexOfFirst { it.parentIndexNumber == seasonNumber }
        if (firstOfSeason < 0) return emptyList()
        val firstRegularSeason = arranged.firstOrNull { (it.parentIndexNumber ?: 0) > 0 }?.parentIndexNumber
        var start = firstOfSeason
        if (seasonNumber == firstRegularSeason) {
            while (start > 0 && arranged[start - 1].parentIndexNumber == 0) start--
        }
        var end = arranged.indexOfLast { it.parentIndexNumber == seasonNumber }
        while (end + 1 < arranged.size && arranged[end + 1].parentIndexNumber == 0) end++
        return arranged.subList(start, end + 1).filter { it.parentIndexNumber == seasonNumber || it.parentIndexNumber == 0 }
    }

    /**
     * Season [seasonNumber] of a show with any unplaced specials added where they aired, or null
     * if there is nothing to add (then the server's own list is used as it is).
     */
    suspend fun seasonWithSpecials(
        api: ApiClient,
        seriesId: UUID,
        seasonNumber: Int,
        fields: List<ItemFields>,
    ): List<BaseItemDto>? {
        if (seasonNumber <= 0) return null
        return try {
            val specials =
                api.tvShowsApi
                    .getEpisodes(
                        GetEpisodesRequest(
                            seriesId = seriesId,
                            season = 0,
                            fields = listOf(ItemFields.SPECIAL_EPISODE_NUMBERS),
                            enableImages = false,
                            enableUserData = false,
                        ),
                    ).content.items
            if (specials.none(::needsPlacing)) return null
            val all =
                api.tvShowsApi
                    .getEpisodes(
                        GetEpisodesRequest(
                            seriesId = seriesId,
                            fields = (fields + ItemFields.SPECIAL_EPISODE_NUMBERS).distinct(),
                        ),
                    ).content.items
            seasonSlice(arrange(all), seasonNumber).takeIf { slice -> slice.any { it.parentIndexNumber == 0 } }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Timber.w(ex, "Could not place specials for %s", seriesId)
            null
        }
    }

    /**
     * For "Watch next" / autoplay: the show's episodes from [startId] on, with unplaced specials
     * slotted in where they aired. [fromStart] is the server's list starting at [startId].
     */
    suspend fun withSpecials(
        api: ApiClient,
        seriesId: UUID,
        fromStart: List<BaseItemDto>,
        fields: List<ItemFields>,
    ): List<BaseItemDto> =
        try {
            val have = fromStart.map { it.id }.toSet()
            val specials =
                api.tvShowsApi
                    .getEpisodes(
                        GetEpisodesRequest(
                            seriesId = seriesId,
                            season = 0,
                            fields = (fields + ItemFields.SPECIAL_EPISODE_NUMBERS).distinct(),
                        ),
                    ).content.items
            val missing = specials.filter { needsPlacing(it) && it.id !in have }
            // Specials already in the list but with no note are also moved to where they aired
            if (missing.isEmpty() && fromStart.none(::needsPlacing)) {
                fromStart
            } else {
                val first = fromStart.firstOrNull()
                val arranged = arrange(fromStart + missing)
                // Keep the list starting where it started: drop specials that aired before it
                val startAt = first?.let { f -> arranged.indexOfFirst { it.id == f.id } } ?: 0
                arranged.subList(startAt.coerceAtLeast(0), arranged.size)
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Timber.w(ex, "Could not place specials for %s", seriesId)
            fromStart
        }
}
