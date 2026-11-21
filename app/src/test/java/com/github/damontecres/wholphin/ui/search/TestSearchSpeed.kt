package com.github.damontecres.wholphin.ui.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TestSearchSpeed {
    @Test
    fun `answers are remembered for a short while`() {
        var clock = 0L
        val answers = RecentAnswers<String>(keepMs = 1000, max = 2, now = { clock })
        answers.put("mulan", "Mulan")
        assertEquals("Mulan", answers.get("mulan"))
        clock = 1001
        assertNull(answers.get("mulan"))
    }

    @Test
    fun `only the newest answers are kept`() {
        val answers = RecentAnswers<String>(max = 2, now = { 0L })
        answers.put("a", "1")
        answers.put("b", "2")
        answers.put("c", "3")
        assertNull(answers.get("a"))
        assertEquals("2", answers.get("b"))
        assertEquals("3", answers.get("c"))
        answers.clear()
        assertNull(answers.get("c"))
    }

    @Test
    fun `the exact name is still the first suggestion`() {
        assertEquals(listOf("Mulan", "Mulan II"), suggestionsFor("Mulan", listOf("Mulan II", "Mulan")))
    }
}
