package com.github.damontecres.wholphin.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AniListCardFactsTest {
    @Test
    fun animeGenresComeBeforePlainOnes() {
        val facts = AniListCardFacts(74, listOf("Adventure", "Comedy", "Fantasy", "Isekai", "Slice of Life"))
        assertEquals(listOf("Isekai", "Slice of Life"), facts.cardTags(2))
        assertEquals(listOf("Isekai", "Slice of Life", "Adventure"), facts.cardTags(3))
    }

    @Test
    fun plainGenresWhenThatsAllThereIs() {
        assertEquals(listOf("Action", "Comedy"), AniListCardFacts(null, listOf("Action", "Comedy", "Drama")).cardTags(2))
    }

    @Test
    fun scoreReadsLikeTheStars() {
        assertEquals("7.4", AniListCardFacts(74, emptyList()).scoreText)
        assertEquals("8.0", AniListCardFacts(80, emptyList()).scoreText)
        assertNull(AniListCardFacts(null, emptyList()).scoreText)
    }

    @Test
    fun curlyQuotesMadePlainForSearch() {
        assertEquals(
            "A Gatherer's Adventure in Isekai",
            AniListService.searchable("  A Gatherer’s  Adventure in Isekai "),
        )
    }

    @Test
    fun malScoreReadsLikeTheStars() {
        assertEquals("7.1", MalFacts.text(7.08))
        assertEquals("8.0", MalFacts.text(8.0))
    }
}
