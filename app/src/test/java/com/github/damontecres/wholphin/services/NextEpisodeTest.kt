package com.github.damontecres.wholphin.services

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class NextEpisodeTest {
    private fun ep(
        season: Int,
        number: Int,
        played: Boolean = false,
        ticks: Long = 0L,
    ) = BaseItemDto(
        id = UUID.randomUUID(),
        type = BaseItemKind.EPISODE,
        name = "S${season}E$number",
        parentIndexNumber = season,
        indexNumber = number,
        userData =
            UserItemDataDto(
                playbackPositionTicks = ticks,
                playCount = if (played) 1 else 0,
                isFavorite = false,
                played = played,
                key = "k",
                itemId = UUID.randomUUID(),
            ),
    )

    @Test
    fun oldPartlyWatchedEpisodeDoesNotWin() {
        // Ben's Big Bang Theory: S6 E7 has old saved progress, but E1 to E23 are all watched
        val eps =
            (1..24).map { n ->
                when {
                    n == 7 -> ep(6, n, played = true, ticks = 5_000_000_000L)
                    n <= 23 -> ep(6, n, played = true)
                    else -> ep(6, n)
                }
            }
        assertEquals("S6E24", NextEpisode.choose(eps)?.name)
    }

    @Test
    fun episodeInProgressAfterTheLastWatchedIsResumed() {
        val eps = listOf(ep(1, 1, played = true), ep(1, 2, ticks = 100L), ep(1, 3))
        assertEquals("S1E2", NextEpisode.choose(eps)?.name)
    }

    @Test
    fun nextSeasonAndSpecialsLeftOut() {
        val eps = listOf(ep(0, 1), ep(2, 1), ep(1, 2, played = true), ep(1, 1, played = true))
        assertEquals("S2E1", NextEpisode.choose(eps)?.name)
    }

    @Test
    fun nothingWatchedStartsAtTheBeginning() {
        assertEquals("S1E1", NextEpisode.choose(listOf(ep(1, 2), ep(1, 1)))?.name)
    }

    @Test
    fun allWatchedGivesNothing() {
        assertNull(NextEpisode.choose(listOf(ep(1, 1, played = true), ep(1, 2, played = true))))
    }
}
