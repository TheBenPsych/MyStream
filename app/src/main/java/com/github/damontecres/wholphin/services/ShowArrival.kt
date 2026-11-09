package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.LocationType
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benflix (Ben, 2026-11-09): is a requested show really all here?
 *
 * Seerr only calls a show "Available" when every season TMDB lists is in, and a season only when it has every
 * episode TMDB lists. That goes wrong in a few ways, so a show Ben already has kept showing as missing:
 * - an announced season with no date yet (Chained Soldier season 3), which "all seasons" requests include
 * - a season that's still airing: TMDB counts the episodes that haven't aired yet
 * - specials (season 0), which Sonarr usually doesn't fetch
 * - anime filed with different season splits (Jellyfin / Sonarr put 38 episodes in season 1, TMDB splits 24 + 14)
 * - Seerr's own scan of Jellyfin running behind
 *
 * So a show counts as here once every asked-for episode that has actually aired is in Jellyfin.
 */
@Singleton
class ShowArrival
    @Inject
    constructor(
        private val api: ApiClient,
        private val seerrService: SeerrService,
    ) {
        /** Looks up the show (unless [details], Seerr's /tv/{id} answer, is given) and checks it */
        suspend fun arrived(
            request: MediaRequest,
            details: JsonObject? = null,
        ): Boolean =
            withContext(WholphinDispatchers.IO) {
                if (request.media?.status == SeerrAvailability.AVAILABLE.status) return@withContext true
                if (SeerrItemType.fromString(request.type) == SeerrItemType.MOVIE) return@withContext false
                val tmdbId = request.media?.tmdbId ?: return@withContext false
                try {
                    val tv =
                        details
                            ?: seerrService.api.getRaw("/tv/$tmdbId")?.let { Json.parseToJsonElement(it).jsonObject }
                            ?: return@withContext false
                    val fourK = request.is4k == true
                    val aired = airedEpisodes(tv)
                    val wanted = wantedAired(request, aired)
                    if (wanted.isEmpty()) return@withContext false
                    val statuses = seasonStatuses(tv, fourK)
                    // Quick answer from Seerr when every asked-for aired season is fully in
                    if (wanted.keys.all { statuses[it] == SeerrAvailability.AVAILABLE.status }) return@withContext true
                    val media = tv["mediaInfo"] as? JsonObject
                    val plainId = media.text("jellyfinMediaId")
                    val idText = if (fourK) media.text("jellyfinMediaId4k") ?: plainId else plainId
                    val jellyfinId = idText?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    val have = jellyfinId?.let { jellyfinEpisodes(it) }
                    decide(wanted, aired, statuses, have, currentlyAiring(tv))
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not check whether %s is all here", tmdbId)
                    false
                }
            }

        /** Episodes in Jellyfin per season (real files only, specials left out) */
        private suspend fun jellyfinEpisodes(seriesId: UUID): Map<Int, Int>? =
            try {
                api.itemsApi
                    .getItems(
                        GetItemsRequest(
                            parentId = seriesId,
                            recursive = true,
                            includeItemTypes = listOf(BaseItemKind.EPISODE),
                            isMissing = false,
                            excludeLocationTypes = listOf(LocationType.VIRTUAL),
                            enableImages = false,
                            enableUserData = false,
                            enableTotalRecordCount = false,
                        ),
                    ).content
                    .items
                    .filter { it.locationType != LocationType.VIRTUAL }
                    .mapNotNull { ep ->
                        val season = ep.parentIndexNumber ?: 1
                        if (season < 1) return@mapNotNull null
                        val first = ep.indexNumber ?: return@mapNotNull null
                        // A file holding two episodes ("E01-E02") counts as both
                        val last = (ep.indexNumberEnd ?: first).coerceAtLeast(first)
                        season to (first..last)
                    }.groupBy({ it.first }, { it.second })
                    .mapValues { (_, ranges) -> ranges.flatMap { it.toList() }.toSet().size }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not count the episodes of %s", seriesId)
                null
            }

        companion object {
            /**
             * Episodes aired so far per season (season 1 up, specials left out). Seasons not aired yet, or with
             * no date at all, aren't in it.
             */
            fun airedEpisodes(
                tv: JsonObject,
                today: LocalDate = LocalDate.now(),
            ): Map<Int, Int> {
                val last = tv["lastEpisodeToAir"] as? JsonObject
                val lastSeason = last.int("seasonNumber")?.takeIf { it >= 1 }
                // (a special aired last says nothing about the real seasons)
                val lastEpisode = last.int("episodeNumber")
                return (tv["seasons"] as? JsonArray)
                    .orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .mapNotNull { s ->
                        val number = s.int("seasonNumber") ?: return@mapNotNull null
                        if (number < 1) return@mapNotNull null
                        val listed = s.int("episodeCount") ?: 0
                        val count =
                            if (lastSeason != null) {
                                when {
                                    number < lastSeason -> listed

                                    // The newest episode out tells how far this season has got
                                    number == lastSeason -> lastEpisode ?: listed

                                    else -> 0
                                }
                            } else {
                                val premiere = day(s.text("airDate"))
                                if (premiere != null && !premiere.isAfter(today)) listed else 0
                            }
                        if (count > 0) number to count else null
                    }.toMap()
            }

            /** The aired seasons this request is for (an "all seasons" or season-less request means every one) */
            fun wantedAired(
                request: MediaRequest,
                aired: Map<Int, Int>,
            ): Map<Int, Int> {
                val asked =
                    request.seasons
                        .orEmpty()
                        .mapNotNull { it.seasonNumber }
                        .filter { it > 0 }
                return if (asked.isEmpty()) aired else aired.filterKeys { it in asked }
            }

            /**
             * [wanted]: asked-for aired seasons with their aired episode count. [have]: Jellyfin's episodes per
             * season, or null when Jellyfin couldn't be asked.
             */
            fun decide(
                wanted: Map<Int, Int>,
                aired: Map<Int, Int>,
                statuses: Map<Int, Int>,
                have: Map<Int, Int>?,
                airingSeason: Int?,
            ): Boolean {
                if (wanted.isEmpty()) return false
                val seasonOk =
                    wanted.all { (season, count) ->
                        statuses[season] == SeerrAvailability.AVAILABLE.status ||
                            (have != null && (have[season] ?: 0) >= count) ||
                            // Without Jellyfin: Seerr's "partly here" on a season still airing is as good as it gets
                            (
                                have == null &&
                                    season == airingSeason &&
                                    statuses[season] == SeerrAvailability.PARTIALLY_AVAILABLE.status
                            )
                    }
                if (seasonOk) return true
                // Different season splits (anime): when the whole show was asked for, count every episode together
                if (have != null && wanted.keys.containsAll(aired.keys)) {
                    return have.values.sum() >= aired.values.sum()
                }
                return false
            }

            /** Seerr's status per season from the /tv/{id} answer */
            fun seasonStatuses(
                tv: JsonObject,
                fourK: Boolean,
            ): Map<Int, Int> =
                ((tv["mediaInfo"] as? JsonObject)?.get("seasons") as? JsonArray)
                    .orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .mapNotNull { s ->
                        val number = s.int("seasonNumber") ?: return@mapNotNull null
                        val status = s.int(if (fourK) "status4k" else "status") ?: return@mapNotNull null
                        number to status
                    }.toMap()

            /** The season with an episode coming up next, when one is scheduled */
            fun currentlyAiring(tv: JsonObject): Int? = (tv["nextEpisodeToAir"] as? JsonObject).int("seasonNumber")

            private fun JsonObject?.int(name: String): Int? = (this?.get(name) as? JsonPrimitive)?.intOrNull

            private fun JsonObject?.text(name: String): String? = (this?.get(name) as? JsonPrimitive)?.contentOrNull

            private fun day(text: String?): LocalDate? =
                text
                    ?.takeIf { it.length >= 10 }
                    ?.let { runCatching { LocalDate.parse(it.substring(0, 10)) }.getOrNull() }
        }
    }
