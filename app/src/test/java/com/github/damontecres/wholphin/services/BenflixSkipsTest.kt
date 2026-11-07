package com.github.damontecres.wholphin.services

import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.junit.Assert.assertEquals
import org.junit.Test

class BenflixSkipsTest {
    private val item = UUID.randomUUID()

    private fun seg(
        type: MediaSegmentType,
        start: Long,
        end: Long,
    ) = MediaSegmentDto(
        id = UUID.randomUUID(),
        itemId = item,
        type = type,
        startTicks = start * 10_000_000L,
        endTicks = end * 10_000_000L,
    )

    @Test
    fun `AniSkip beats Tower's own guesses`() {
        val towerIntro = seg(MediaSegmentType.INTRO, 10, 100)
        val towerCredits = seg(MediaSegmentType.OUTRO, 1300, 1400)
        val tower = TowerSkips(ready = true, segments = listOf(towerIntro, towerCredits), unsure = emptySet(), none = emptySet())
        val aniIntro = seg(MediaSegmentType.INTRO, 12, 101)
        assertEquals(listOf(towerCredits, aniIntro), BenflixSkipsService.merge(tower, listOf(aniIntro)))
    }

    @Test
    fun `someone's fix beats AniSkip`() {
        val fixedIntro = seg(MediaSegmentType.INTRO, 20, 110)
        val tower =
            TowerSkips(
                ready = true,
                segments = listOf(fixedIntro),
                unsure = emptySet(),
                fixed = setOf(fixedIntro.id),
                none = emptySet(),
            )
        assertEquals(listOf(fixedIntro), BenflixSkipsService.merge(tower, listOf(seg(MediaSegmentType.INTRO, 12, 101))))
    }

    @Test
    fun `no intro means no intro, even from AniSkip`() {
        val tower = TowerSkips(ready = true, segments = emptyList(), unsure = emptySet(), none = setOf(MediaSegmentType.INTRO))
        assertEquals(emptyList<MediaSegmentDto>(), BenflixSkipsService.merge(tower, listOf(seg(MediaSegmentType.INTRO, 12, 101))))
    }

    @Test
    fun `kinds come from Tower's names`() {
        assertEquals(MediaSegmentType.OUTRO, BenflixSkipsService.typeOf("Outro"))
        assertEquals(MediaSegmentType.INTRO, BenflixSkipsService.typeOf("intro"))
        assertEquals(null, BenflixSkipsService.typeOf("nonsense"))
    }
}
