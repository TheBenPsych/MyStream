package com.github.damontecres.wholphin.services

import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AniSkipTest {
    // Attack on Titan as the anime list has it (TVDB 267440, TMDB 1429)
    private val map =
        AniSkipMap.parse(
            listOf(
                AniSkipMap.line("tvdb", 267440, 1, 0, "TV", 16498),
                AniSkipMap.line("tvdb", 267440, 0, 0, "OVA", 18397),
                AniSkipMap.line("tvdb", 267440, 2, 0, "TV", 25777),
                AniSkipMap.line("tvdb", 267440, 3, 0, "TV", 35760),
                AniSkipMap.line("tvdb", 267440, 3, 12, "TV", 38524),
                AniSkipMap.line("tmdb", 1429, 3, 12, "TV", 38524),
                AniSkipMap.line("movie", 714194, -1, 0, "MOVIE", 42091),
                AniSkipMap.line("anilist", 16498, -1, 0, "TV", 16498),
            ),
        )

    @Test
    fun `finds the right entry when a season is split in two`() {
        val ids = mapOf("Tvdb" to "267440")
        assertEquals(AniSkipTarget(16498, 5), map.find(ids, false, 1, 5))
        assertEquals(AniSkipTarget(35760, 12), map.find(ids, false, 3, 12))
        assertEquals(AniSkipTarget(38524, 1), map.find(ids, false, 3, 13))
    }

    @Test
    fun `falls back to TMDB, then AniList for the first season`() {
        assertEquals(AniSkipTarget(38524, 2), map.find(mapOf("Tmdb" to "1429"), false, 3, 14))
        assertEquals(AniSkipTarget(16498, 3), map.find(mapOf("AniList" to "16498"), false, 1, 3))
        assertNull(map.find(mapOf("AniList" to "16498"), false, 2, 3))
        assertEquals(AniSkipTarget(42091, 1), map.find(mapOf("Tmdb" to "714194"), true, null, null))
    }

    @Test
    fun `skips specials and unknown shows`() {
        assertNull(map.find(mapOf("Tvdb" to "267440"), false, 0, 1))
        assertNull(map.find(mapOf("Tvdb" to "1"), false, 1, 1))
    }

    private fun result(
        type: String,
        start: Double,
        end: Double,
        length: Double,
    ) = AniSkipResult(AniSkipInterval(start, end), type, UUID.randomUUID().toString(), length)

    @Test
    fun `only uses times made on a file of the same length, one of each kind`() {
        val picked =
            AniSkipMatch.pick(
                listOf(
                    result("mixed-op", 10.0, 100.0, 1420.0),
                    result("op", 60.0, 150.0, 1421.0),
                    result("op", 0.0, 90.0, 1300.0),
                    result("ed", 1300.0, 1390.0, 1419.0),
                    result("ed", 1300.0, 2000.0, 1420.0),
                ),
                1420.0,
            )
        assertEquals(listOf("op", "ed"), picked.map { it.skipType })
        assertEquals(60.0, picked.first().interval.startTime, 0.0)
    }

    @Test
    fun `replaces the server's times of the same kind only`() {
        val item = UUID.randomUUID()

        fun seg(type: MediaSegmentType) =
            MediaSegmentDto(id = UUID.randomUUID(), itemId = item, type = type, startTicks = 0L, endTicks = 10L)
        val server = listOf(seg(MediaSegmentType.INTRO), seg(MediaSegmentType.PREVIEW))
        val ani = listOf(seg(MediaSegmentType.INTRO), seg(MediaSegmentType.OUTRO))
        val merged = AniSkipMatch.merge(server, ani)
        assertEquals(listOf(server[1]) + ani, merged)
        assertEquals(server, AniSkipMatch.merge(server, emptyList()))
    }
}
