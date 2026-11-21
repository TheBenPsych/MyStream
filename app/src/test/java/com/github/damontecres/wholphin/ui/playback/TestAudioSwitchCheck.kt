package com.github.damontecres.wholphin.ui.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class TestAudioSwitchCheck {
    @Test
    fun `direct play with the picked track on is fine`() {
        assertEquals(
            AudioSwitchCheck.FINE,
            decideAudioSwitch(wanted = 2, directPlay = true, pickedTrackFound = true, pickedTrackOn = true, streamUrl = null),
        )
    }

    @Test
    fun `direct play still on the old track is caught`() {
        assertEquals(
            AudioSwitchCheck.WRONG_TRACK,
            decideAudioSwitch(wanted = 2, directPlay = true, pickedTrackFound = true, pickedTrackOn = false, streamUrl = null),
        )
    }

    @Test
    fun `direct play where the track can't be found is left alone`() {
        assertEquals(
            AudioSwitchCheck.FINE,
            decideAudioSwitch(wanted = 2, directPlay = true, pickedTrackFound = false, pickedTrackOn = false, streamUrl = null),
        )
    }

    @Test
    fun `server stream with another sound track is caught`() {
        val url = "http://tower/videos/abc/master.m3u8?DeviceId=x&AudioStreamIndex=1&SubtitleStreamIndex=3"
        assertEquals(
            AudioSwitchCheck.WRONG_TRACK,
            decideAudioSwitch(wanted = 2, directPlay = false, pickedTrackFound = false, pickedTrackOn = false, streamUrl = url),
        )
        assertEquals(
            AudioSwitchCheck.FINE,
            decideAudioSwitch(wanted = 1, directPlay = false, pickedTrackFound = false, pickedTrackOn = false, streamUrl = url),
        )
    }

    @Test
    fun `server stream that doesn't name a sound track is left alone`() {
        assertEquals(
            AudioSwitchCheck.FINE,
            decideAudioSwitch(wanted = 2, directPlay = false, pickedTrackFound = false, pickedTrackOn = false, streamUrl = "http://tower/live.ts"),
        )
    }
}
