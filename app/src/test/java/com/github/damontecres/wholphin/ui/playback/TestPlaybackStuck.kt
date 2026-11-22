package com.github.damontecres.wholphin.ui.playback

import org.junit.Assert
import org.junit.Test

class TestPlaybackStuck {
    private fun decide(
        loadingForMs: Long = 30_000,
        loadedAheadMs: Long = 0,
        waitingOnDecoder: Boolean = false,
        started: Boolean = false,
        live: Boolean = false,
    ) = decideStuck(loadingForMs, loadedAheadMs, waitingOnDecoder, started, live)

    @Test
    fun `A normal short load is fine`() {
        Assert.assertEquals(StuckReason.NONE, decide(loadingForMs = 6_000, loadedAheadMs = 20_000, waitingOnDecoder = true))
    }

    @Test
    fun `Waiting on the decoder for ages is a stuck decoder`() {
        Assert.assertEquals(StuckReason.DECODER, decide(waitingOnDecoder = true))
    }

    @Test
    fun `Plenty loaded but nothing on screen is a stuck decoder`() {
        Assert.assertEquals(StuckReason.DECODER, decide(loadedAheadMs = 12_000, started = true))
    }

    @Test
    fun `Slow loading with nothing loaded yet is left alone`() {
        Assert.assertEquals(StuckReason.NONE, decide(loadingForMs = 45_000, loadedAheadMs = 500))
    }

    @Test
    fun `Never starting at all is called out in the end`() {
        Assert.assertEquals(StuckReason.UNKNOWN, decide(loadingForMs = 95_000))
    }

    @Test
    fun `Live channels and videos that already played are not called out for loading alone`() {
        Assert.assertEquals(StuckReason.NONE, decide(loadingForMs = 95_000, live = true))
        Assert.assertEquals(StuckReason.NONE, decide(loadingForMs = 95_000, started = true))
    }
}
