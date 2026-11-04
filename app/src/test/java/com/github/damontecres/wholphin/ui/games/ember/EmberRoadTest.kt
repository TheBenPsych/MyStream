package com.github.damontecres.wholphin.ui.games.ember

import com.github.damontecres.wholphin.services.BenflixHighScores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class EmberRoadTest {
    private val story = EmberSimulator.loadStory()

    @Test
    fun storyHasEverything() {
        assertTrue(story.events.size >= 150)
        assertTrue(story.endings.size >= 10)
        story.lands.forEach { assertTrue("Scene ${it.scene} has art", EmberArt.hasScene(it.scene)) }
        story.stops.forEach { assertTrue("Scene ${it.scene} has art", EmberArt.hasScene(it.scene)) }
        story.endings.forEach { assertTrue("Scene ${it.scene} has art", EmberArt.hasScene(it.scene)) }
        story.events.mapNotNull { it.scene }.forEach { assertTrue("Scene $it has art", EmberArt.hasScene(it)) }
    }

    @Test
    fun runsNeverGetStuck() {
        for (length in listOf("short", "medium", "long")) {
            for ((difficulty, kids) in listOf("easy" to false, "normal" to false, "hard" to false, "normal" to true)) {
                repeat(40) { i ->
                    val r = EmberSimulator.play(story, i * 31L + length.length, length, difficulty, kids)
                    assertTrue(r.ending.isNotBlank())
                    if (kids) assertTrue("Kids runs never fail", r.ending != "turned_back")
                }
            }
        }
    }

    @Test
    fun savesComeBackTheSame() {
        val members = listOf(Member("Ben", "human", listOf("brave"), leader = true), Member("Amy", "elf", listOf("lucky")))
        val e = EmberEngine.newRun(story, RunSetup(members, "healer", "owl", seed = 42))
        repeat(5) { e.travel() }
        val json = EmberJson.encodeToString(RunState.serializer(), e.state)
        val back = EmberJson.decodeFromString(RunState.serializer(), json)
        assertEquals(e.state, back)
    }

    @Test
    fun sameNamesGetDifferentCodes() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val c = UUID.randomUUID()
        val codes = BenflixHighScores.uniqueCodes(listOf(a to "Ben", b to "Benny", c to "Amy"))
        assertEquals("BEN", codes[a])
        assertEquals("BE2", codes[b])
        assertEquals("AMY", codes[c])
    }
}
