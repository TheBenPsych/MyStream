package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.data.model.SeerrItemType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Benflix: when the 7-day "Still looking" clock starts for a request.
 *
 * Ben's rule (2026-11-03): things that aren't out yet don't count. A movie needs its home release
 * (digital, disc or TV; or about 3 months after cinemas when nobody lists one), and a show needs the asked-for
 * season to have aired (to its latest episode while it's still airing). Then there's [GRACE_DAYS] of grace
 * before the clock starts, so copies have time to turn up.
 */
data class SearchClock(
    /** Not released yet (or no date known), so it never turns into "Couldn't find it" */
    val notOut: Boolean,
    /** The release day the clock waits on, when known */
    val outOn: LocalDate?,
    /** The day the 7-day clock starts: the later of when it was approved and [outOn] + grace */
    val startsOn: LocalDate?,
) {
    /** Days the clock has run, or null while it hasn't started */
    val daysLooking: Long?
        get() {
            val start = startsOn ?: return null
            if (notOut) return null
            val today = LocalDate.now()
            return if (today.isBefore(start)) null else ChronoUnit.DAYS.between(start, today)
        }

    /** Out, but still inside the few weeks of grace */
    val justOut: Boolean get() = !notOut && startsOn != null && LocalDate.now().isBefore(startsOn)

    companion object {
        /** A few weeks after release before the clock starts (Ben: "a few weeks at least") */
        const val GRACE_DAYS = 21L

        /** Cinema-only movies: guess the home release this long after */
        private const val CINEMA_TO_HOME_DAYS = 90L

        /** TMDB release types: 4 = digital, 5 = disc, 6 = TV */
        private val HOME_RELEASE_TYPES = setOf(4, 5, 6)

        /** [details] is Seerr's /movie/{id} or /tv/{id} answer for the request's title */
        fun of(
            request: MediaRequest,
            details: JsonObject?,
        ): SearchClock {
            val asked = day(request.updatedAt ?: request.createdAt)
            // Couldn't look it up: go by when it was asked for, like before
            if (details == null) return SearchClock(notOut = false, outOn = null, startsOn = asked)
            val isMovie = SeerrItemType.fromString(request.type) == SeerrItemType.MOVIE
            val (known, outOn) =
                if (isMovie) {
                    movieOut(details)
                } else {
                    showOut(details, request.seasons?.mapNotNull { it.seasonNumber }.orEmpty())
                }
            val today = LocalDate.now()
            val notOut = !known || (outOn != null && outOn.isAfter(today))
            val graceEnds = outOn?.plusDays(GRACE_DAYS)
            val startsOn =
                when {
                    notOut -> null
                    graceEnds == null -> asked
                    asked == null -> graceEnds
                    else -> maxOf(asked, graceEnds)
                }
            return SearchClock(notOut = notOut, outOn = outOn, startsOn = startsOn)
        }

        /** (release known, home release day) */
        private fun movieOut(details: JsonObject): Pair<Boolean, LocalDate?> {
            val home =
                (details.obj("releases")?.get("results") as? JsonArray)
                    .orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .flatMap { (it["release_dates"] as? JsonArray).orEmpty().mapNotNull { d -> d as? JsonObject } }
                    .filter { (it["type"] as? JsonPrimitive)?.intOrNull in HOME_RELEASE_TYPES }
                    .mapNotNull { day(it.text("release_date")) }
                    .minOrNull()
            if (home != null) return true to home
            val cinema = day(details.text("releaseDate"))
            if (cinema != null) return true to cinema.plusDays(CINEMA_TO_HOME_DAYS)
            // No dates at all: only count it if TMDB says it's out
            return (details.text("status") == "Released") to null
        }

        /** (release known, day the asked-for seasons finished airing so far) */
        private fun showOut(
            details: JsonObject,
            seasons: List<Int>,
        ): Pair<Boolean, LocalDate?> {
            val next = details.obj("nextEpisodeToAir")
            val last = details.obj("lastEpisodeToAir")
            val lastSeason = (last?.get("seasonNumber") as? JsonPrimitive)?.intOrNull
            val lastAired = day(last?.text("airDate"))
            val wanted = seasons.maxOrNull()
            if (wanted == null) {
                // Whole show: still airing waits on the newest episode, otherwise its last air date
                val day = if (next != null) lastAired else day(details.text("lastAirDate")) ?: lastAired
                val first = day(details.text("firstAirDate"))
                return (first != null) to (day ?: first)
            }
            val premiere =
                (details["seasons"] as? JsonArray)
                    .orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .firstOrNull { (it["seasonNumber"] as? JsonPrimitive)?.intOrNull == wanted }
                    ?.let { day(it.text("airDate")) }
                    ?: return false to null
            // That season is airing right now (or just finished): wait for its newest episode
            val newest = if (lastSeason == wanted && lastAired != null) maxOf(premiere, lastAired) else premiere
            return true to newest
        }

        private fun JsonObject.obj(name: String): JsonObject? = this[name] as? JsonObject

        private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

        private fun day(text: String?): LocalDate? =
            text?.takeIf { it.length >= 10 }?.let {
                try {
                    LocalDate.parse(it.substring(0, 10))
                } catch (ex: Exception) {
                    null
                }
            }
    }
}
