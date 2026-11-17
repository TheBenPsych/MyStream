package com.github.damontecres.wholphin.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real sentences from Wikipedia "Production" sections, sorted into fun stories and plain information */
class FunFactRulesTest {
    private val dull =
        listOf(
            // The one Ben spotted on Prometheus
            "Within 12 hours, Scott returned the script with notes for changes, and Spaihts spent the Christmas holiday redrafting.",
            "Most of the film was shot in Millbrook, Ontario.",
            "Principal photography began on April 24, 2017, and completed on June 23.",
            "The score for the film was composed and conducted by Michael Kamen.",
            "In October 2010, Lindelof submitted his rewritten screenplay to Fox.",
            "Grazer and Scott still had to pay back the studio \$3 million for a budget overrun.",
            "The Iron Giant: By the time the film entered production, Warner Bros. informed the staff that there would be a smaller budget.",
            "Fox CEO Thomas Rothman suggested Prometheus, which was confirmed as the title in January 2011.",
        )

    private val stories =
        listOf(
            "Scott did not inform Dickie about what was to occur in the scene and her screaming reaction was real.",
            "Sound effects were generated with a variety of sources including Pop Rocks—a brand of popping candy—and a parrot.",
            "To avoid the use of stunt doubles, Reed required all the actors to participate in a four-week cheerleading camp.",
            "For the safety of the stuntmen, many props were made of foam rubber.",
            "Harrison Ford turned down the role of Tom Stall.",
            "Bale improvised Bateman's moonwalk, a change from the novel that Ellis initially disliked, but grew to appreciate over time.",
        )

    @Test
    fun plainInformationIsLeftOut() {
        dull.forEach { assertFalse(it, FunFactRules.score(it) >= FunFactRules.PASS) }
    }

    @Test
    fun storiesAreKept() {
        stories.forEach { assertTrue(it, FunFactRules.score(it) >= FunFactRules.PASS) }
    }

    @Test
    fun savedFactWithTitleIsCheckedWithoutIt() {
        assertFalse(
            FunFactRules.isFun(
                "Prometheus: Within 12 hours, Scott returned the script with notes for changes, and Spaihts spent the Christmas holiday redrafting.",
                "Prometheus",
            ),
        )
        assertTrue(
            FunFactRules.isFun(
                "Prometheus: Sound effects were generated with a variety of sources including Pop Rocks and a parrot.",
                "Prometheus",
            ),
        )
    }
}
