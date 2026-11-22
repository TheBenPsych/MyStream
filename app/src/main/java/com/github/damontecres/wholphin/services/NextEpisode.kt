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
 * - find the last watched episode (short specials left out; movie-length ones count, in story order)
 * - if an episode after it is part way through, resume that one
 * - otherwise play the first unwatched episode after it
 * Nothing watched yet: the first episode. Everything watched: null (callers keep Jellyfin's answer).
 */
object NextEpisode {
    /** 40 minutes: shorter specials are extras, longer ones are movies */
    private const val MOVIE_TICKS = 40L * 60L * 10_000_000L

    /** A special as long as a movie (anime movies filed in the show's Specials, Ben 2026-11-22) */
    fun isMovieSpecial(item: BaseItemDto): Boolean =
        item.parentIndexNumber == 0 && (item.runTimeTicks ?: 0L) >= MOVIE_TICKS

    /**
     * The whole show in story order: regular episodes by season and number, with each special slotted
     * where TheTVDB says it airs ("airs after Season 1", "airs before S2 E5"), else where its date falls.
     */
    fun storyOrder(episodes: List<BaseItemDto>): List<BaseItemDto> {
        val out =
            episodes
                .filter { (it.parentIndexNumber ?: 1) > 0 }
                .sortedWith(compareBy({ it.parentIndexNumber ?: 1 }, { it.indexNumber ?: 0 }))
                .toMutableList()
        val specials = episodes.filter { it.parentIndexNumber == 0 }.sortedWith(compareBy({ it.premiereDate }, { it.indexNumber ?: 0 }))
        specials.forEach { sp ->
            val after = sp.airsAfterSeasonNumber
            val beforeSeason = sp.airsBeforeSeasonNumber
            val beforeEp = sp.airsBeforeEpisodeNumber
            val at =
                when {
                    after != null -> {
                        val last = out.indexOfLast { (it.parentIndexNumber ?: 0) in 1..after }
                        // after the season's last episode (and any specials already placed after it)
                        var i = last + 1
                        while (i < out.size && out[i].parentIndexNumber == 0) i++
                        i
                    }

                    beforeSeason != null -> {
                        out
                            .indexOfFirst {
                                val s = it.parentIndexNumber ?: 0
                                s > beforeSeason || (s == beforeSeason && (it.indexNumber ?: 0) >= (beforeEp ?: 1))
                            }.let { if (it < 0) out.size else it }
                    }

                    sp.premiereDate != null -> {
                        out
                            .indexOfFirst { (it.parentIndexNumber ?: 0) > 0 && it.premiereDate?.isAfter(sp.premiereDate) == true }
                            .let { if (it < 0) out.size else it }
                    }

                    else -> {
                        out.size
                    }
                }
            out.add(at.coerceIn(0, out.size), sp)
        }
        return out
    }

    /**
     * The special right before [episodeId] in story order when it's a movie you haven't watched
     * ("Watch Mugen Train first?"), else null
     */
    fun movieBefore(
        episodes: List<BaseItemDto>,
        episodeId: java.util.UUID,
    ): BaseItemDto? {
        val order = storyOrder(episodes.filter { it.locationType != LocationType.VIRTUAL })
        val i = order.indexOfFirst { it.id == episodeId }
        if (i <= 0) return null
        val before = order[i - 1]
        return before.takeIf { isMovieSpecial(it) && it.userData?.played != true && (it.userData?.playbackPositionTicks ?: 0L) == 0L }
    }

    /** Pure pick from a show's episodes (any order); movie-length specials count, in story order */
    fun choose(episodes: List<BaseItemDto>): BaseItemDto? {
        val eps =
            storyOrder(
                episodes.filter {
                    it.locationType != LocationType.VIRTUAL && ((it.parentIndexNumber ?: 1) > 0 || isMovieSpecial(it))
                },
            )
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
                            fields = (fields.orEmpty() + ItemFields.SPECIAL_EPISODE_NUMBERS).distinct(),
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
