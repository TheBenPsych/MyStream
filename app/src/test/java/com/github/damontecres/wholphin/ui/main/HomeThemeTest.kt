package com.github.damontecres.wholphin.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HomeThemeTest {
    @Test
    fun dayOfTheDeadOnlyPicksFittingTitles() {
        val theme = HomeTheme.forDate(LocalDate.of(2026, 11, 1))
        assertEquals(HomeTheme.DAY_OF_THE_DEAD, theme)
        assertTrue(theme.isStrict)
        assertTrue(theme.isPicked("Coco"))
        assertTrue(theme.isPicked("Book of Life"))
        assertTrue(theme.isPicked("Dia de Muertos"))
        assertFalse(theme.isPicked("Stuart Little"))
        assertFalse(theme.isPicked("Coco Before Chanel"))
    }

    @Test
    fun nameWordsAreWholeWords() {
        assertTrue(HomeTheme.CHRISTMAS.hasNameWord("A Christmas Story"))
        assertTrue(HomeTheme.CHRISTMAS.hasNameWord("How the Grinch Stole Christmas!"))
        assertFalse(HomeTheme.CHRISTMAS.hasNameWord("Santana"))
        assertTrue(HomeTheme.FOURTH_OF_JULY.hasNameWord("Independence Day: Resurgence"))
        assertFalse(HomeTheme.FOURTH_OF_JULY.hasNameWord("American Psycho"))
    }

    @Test
    fun normalizeIgnoresPunctuationAccentsAndThe() {
        assertEquals("your name", HomeTheme.normalizeTitle("Your Name."))
        assertEquals("book of life", HomeTheme.normalizeTitle("The Book of Life"))
        assertEquals("dia de muertos", HomeTheme.normalizeTitle("Día de Muertos"))
        assertEquals("big fish and begonia", HomeTheme.normalizeTitle("Big Fish & Begonia"))
    }

    @Test
    fun holidayDates() {
        assertEquals(HomeTheme.HALLOWEEN, HomeTheme.forDate(LocalDate.of(2026, 10, 15)))
        assertEquals(HomeTheme.BONFIRE_NIGHT, HomeTheme.forDate(LocalDate.of(2026, 11, 5)))
        assertEquals(HomeTheme.DIWALI, HomeTheme.forDate(LocalDate.of(2026, 11, 8)))
        // Thanksgiving 2026 is Nov 26
        assertEquals(HomeTheme.THANKSGIVING, HomeTheme.forDate(LocalDate.of(2026, 11, 26)))
        assertEquals(HomeTheme.CHRISTMAS, HomeTheme.forDate(LocalDate.of(2026, 11, 28)))
        assertEquals(HomeTheme.FOURTH_OF_JULY, HomeTheme.forDate(LocalDate.of(2026, 7, 4)))
        assertEquals(HomeTheme.TANABATA, HomeTheme.forDate(LocalDate.of(2026, 7, 7)))
        assertEquals(HomeTheme.ST_PATRICKS, HomeTheme.forDate(LocalDate.of(2026, 3, 17)))
        assertEquals(HomeTheme.LUNAR_NEW_YEAR, HomeTheme.forDate(LocalDate.of(2026, 2, 17)))
    }

    @Test
    fun monthRows() {
        assertEquals(HomeTheme.BLACK_HISTORY_MONTH, HomeTheme.forMonth(LocalDate.of(2027, 2, 1)))
        assertEquals(HomeTheme.BLACK_HISTORY_MONTH, HomeTheme.forMonth(LocalDate.of(2027, 2, 28)))
        assertEquals(HomeTheme.PRIDE_MONTH, HomeTheme.forMonth(LocalDate.of(2027, 6, 15)))
        assertEquals(null, HomeTheme.forMonth(LocalDate.of(2027, 3, 1)))
        // The holiday row still works in those months
        assertEquals(HomeTheme.VALENTINES, HomeTheme.forDate(LocalDate.of(2027, 2, 14)))
        assertTrue(HomeTheme.PRIDE_MONTH.isPicked("Love, Simon"))
        assertTrue(HomeTheme.BLACK_HISTORY_MONTH.isPicked("One Night in Miami..."))
    }
}
