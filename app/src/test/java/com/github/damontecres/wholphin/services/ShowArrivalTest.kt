package com.github.damontecres.wholphin.services

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShowArrivalTest {
    private val today = LocalDate.of(2026, 11, 9)

    private fun tv(text: String) = Json.parseToJsonElement(text).jsonObject

    // Chained Soldier: seasons 1 and 2 out, season 3 announced with no date
    private val chained =
        tv(
            """
            {"seasons":[
              {"seasonNumber":0,"episodeCount":2,"airDate":"2024-05-01"},
              {"seasonNumber":1,"episodeCount":12,"airDate":"2024-01-04"},
              {"seasonNumber":2,"episodeCount":12,"airDate":"2026-01-08"},
              {"seasonNumber":3,"episodeCount":0,"airDate":null}],
             "lastEpisodeToAir":{"seasonNumber":2,"episodeNumber":12,"airDate":"2026-03-26"},
             "mediaInfo":{"status":4,"seasons":[
               {"seasonNumber":1,"status":5},{"seasonNumber":2,"status":5},{"seasonNumber":3,"status":1}]}}
            """,
        )

    @Test
    fun unannouncedSeasonIsLeftOut() {
        assertEquals(mapOf(1 to 12, 2 to 12), ShowArrival.airedEpisodes(chained, today))
    }

    @Test
    fun allAiredSeasonsHereCountsAsHere() {
        val aired = ShowArrival.airedEpisodes(chained, today)
        val statuses = ShowArrival.seasonStatuses(chained, fourK = false)
        assertTrue(ShowArrival.decide(aired, aired, statuses, null, null))
    }

    @Test
    fun stillAiringSeasonCountsOnlyAiredEpisodes() {
        val airing =
            tv(
                """
                {"seasons":[{"seasonNumber":1,"episodeCount":24,"airDate":"2026-10-01"}],
                 "lastEpisodeToAir":{"seasonNumber":1,"episodeNumber":6},
                 "nextEpisodeToAir":{"seasonNumber":1,"episodeNumber":7}}
                """,
            )
        val aired = ShowArrival.airedEpisodes(airing, today)
        assertEquals(mapOf(1 to 6), aired)
        val partly = mapOf(1 to 4)
        assertTrue(ShowArrival.decide(aired, aired, partly, mapOf(1 to 6), 1))
        assertFalse(ShowArrival.decide(aired, aired, partly, mapOf(1 to 5), 1))
        // Jellyfin couldn't be asked: Seerr's "partly here" on the airing season is good enough
        assertTrue(ShowArrival.decide(aired, aired, partly, null, 1))
    }

    @Test
    fun differentSeasonSplitsCountTogether() {
        // TMDB splits 24 + 14, Jellyfin has all 38 in season 1
        val aired = mapOf(1 to 24, 2 to 14)
        val statuses = mapOf(1 to 5, 2 to 1)
        assertTrue(ShowArrival.decide(aired, aired, statuses, mapOf(1 to 38), null))
        assertFalse(ShowArrival.decide(aired, aired, statuses, mapOf(1 to 30), null))
        // Only season 2 asked for: totals don't count
        assertFalse(ShowArrival.decide(mapOf(2 to 14), aired, statuses, mapOf(1 to 38), null))
    }

    @Test
    fun missingAiredEpisodesStillMissing() {
        val aired = mapOf(1 to 12, 2 to 12)
        assertFalse(ShowArrival.decide(aired, aired, mapOf(1 to 5, 2 to 4), mapOf(1 to 12, 2 to 8), null))
    }
}
