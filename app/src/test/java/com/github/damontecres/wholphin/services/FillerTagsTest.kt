package com.github.damontecres.wholphin.services

import org.junit.Assert.assertEquals
import org.junit.Test

class FillerTagsTest {
    private val html =
        """
        <div class="filler"><span class="Label">Filler Episodes:</span>
        <span class="Episodes"><a>26</a>, <a>97</a>, <a>101-106</a></span></div>
        <div class="mixed_canon/filler"><span class="Label">Mixed Canon/Filler Episodes:</span>
        <span class="Episodes"><a>7</a></span></div>
        <table class="EpisodeList"><tbody>
        <tr class="manga_canon odd"><td class="Number">1</td><td class="Title"><a href="/x">Enter: Naruto Uzumaki!</a></td>
        <td class="Type"><span>Manga Canon</span></td><td class="Date">2002-10-03</td></tr>
        <tr class="filler even"><td class="Number">26</td><td class="Title"><a href="/y">Special Report: Live from the Forest of Death!</a></td>
        <td class="Type"><span>Filler</span></td><td class="Date">2003-04-02</td></tr>
        </tbody></table>
        """.trimIndent()

    @Test
    fun parsesQuickList() {
        val kinds = FillerService.parse(html)
        assertEquals(FillerKind.FILLER, kinds[26])
        assertEquals(FillerKind.FILLER, kinds[103])
        assertEquals(FillerKind.MIXED, kinds[7])
        assertEquals(null, kinds[1])
    }

    @Test
    fun parsesEpisodeTitles() {
        val titles = FillerService.parseTitles(html)
        assertEquals("Enter: Naruto Uzumaki!", titles[1])
        assertEquals("Special Report: Live from the Forest of Death!", titles[26])
    }

    @Test
    fun cleansTitlesForMatching() {
        assertEquals(
            FillerService.cleanTitle("Special Report: Live from the Forest of Death!"),
            FillerService.cleanTitle("Special Report - Live From The Forest Of Death"),
        )
    }
}
