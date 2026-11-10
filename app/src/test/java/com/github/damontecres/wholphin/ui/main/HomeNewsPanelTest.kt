package com.github.damontecres.wholphin.ui.main

import com.github.damontecres.wholphin.services.BookNews
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeNewsPanelTest {
    private fun row(
        id: String,
        news: Boolean = true,
    ) = HomeRow(id = id, kind = "k", title = id, kindColor = HomeKind.Other, image = null, destination = null, news = news)

    private val facts = listOf(row("fact-1", false), row("fact-2", false))

    @Test
    fun quietDayFillsWithOnTheWayAndAFact() {
        val list = arrangeHomeRows(HomeParts(listOf(row("ep-1"), row("req-1")), listOf(row("way-1"), row("way-2")), facts), 6)
        assertEquals(listOf("ep-1", "req-1", "way-1", "way-2", "fact-1"), list.rows.map { it.id })
        assertEquals(0, list.more)
    }

    @Test
    fun veryQuietDayGetsTwoFacts() {
        val list = arrangeHomeRows(HomeParts(listOf(row("ep-1")), emptyList(), facts), 6)
        assertEquals(listOf("ep-1", "fact-1", "fact-2"), list.rows.map { it.id })
    }

    @Test
    fun busyDayKeepsTheLastLineForSeeAll() {
        val news = (1..9).map { row("ep-$it") }
        val list = arrangeHomeRows(HomeParts(news, listOf(row("way-1")), facts), 6)
        assertEquals(5, list.rows.size)
        assertEquals(5, list.more)
        assertEquals("ep-1", list.rows.first().id)
    }

    @Test
    fun exactlyFullHasNoFact() {
        val news = (1..6).map { row("ep-$it") }
        val list = arrangeHomeRows(HomeParts(news, emptyList(), facts), 6)
        assertEquals(6, list.rows.size)
        assertEquals(0, list.more)
        assertEquals("ep-6", list.rows.last().id)
    }

    @Test
    fun onTheWayLinesGetShort() {
        assertEquals("On the way · 20 min left", onTheWayKind("On the way, about 20 minutes left"))
        assertEquals("On the way · 3h left", onTheWayKind("On the way, about 3 hours left"))
        assertEquals("On the way · 1h left", onTheWayKind("On the way, about an hour left"))
        assertEquals("On the way · searching", onTheWayKind("On the way, looking for a copy"))
        assertEquals("On the way · waiting for a yes", onTheWayKind("Asked for, waiting for a yes"))
        assertEquals("On the way · 42%", onTheWayKind("On the way, 42% downloaded"))
        assertEquals("On the way", onTheWayKind("On the way, downloading"))
    }

    @Test
    fun bookNewsWords() {
        assertEquals(
            "New volume" to "One Piece · Vol. 109",
            bookWords(BookNews("bk-1", "x", "t", name = "One Piece", line = "New: Vol. 109 is here")),
        )
        assertEquals(
            "Ready to read" to "Dune",
            bookWords(BookNews("bkr-1", "x", "t", name = "Dune", line = "The book you asked for is ready")),
        )
    }
}
