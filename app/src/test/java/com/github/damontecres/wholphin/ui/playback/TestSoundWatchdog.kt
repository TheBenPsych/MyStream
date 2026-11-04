package com.github.damontecres.wholphin.ui.playback

import org.junit.Assert
import org.junit.Test

class TestSoundWatchdog {
    private fun decide(
        videoHasSound: Boolean = true,
        playerHasVideo: Boolean = true,
        playerHasPlayableSound: Boolean = true,
        soundSelected: Boolean = true,
        before: SoundSnapshot = SoundSnapshot(10_000, 100),
        after: SoundSnapshot = SoundSnapshot(13_000, 200),
    ) = decideSoundProblem(videoHasSound, playerHasVideo, playerHasPlayableSound, soundSelected, before, after)

    @Test
    fun `Sound playing is fine`() {
        Assert.assertEquals(SoundProblem.NONE, decide())
    }

    @Test
    fun `Picture moves on but no sound goes out`() {
        Assert.assertEquals(SoundProblem.SOUND_STUCK, decide(after = SoundSnapshot(13_000, 100)))
    }

    @Test
    fun `Paused or stuck picture is not a sound problem`() {
        Assert.assertEquals(SoundProblem.NONE, decide(after = SoundSnapshot(10_500, 100)))
    }

    @Test
    fun `Player found no sound it can play`() {
        Assert.assertEquals(SoundProblem.NO_SOUND_TRACK, decide(playerHasPlayableSound = false))
    }

    @Test
    fun `Video without sound is left alone`() {
        Assert.assertEquals(SoundProblem.NONE, decide(videoHasSound = false, playerHasPlayableSound = false))
    }

    @Test
    fun `Tracks not loaded yet is left alone`() {
        Assert.assertEquals(SoundProblem.NONE, decide(playerHasVideo = false, playerHasPlayableSound = false))
    }

    @Test
    fun `Sound switched off on purpose is left alone`() {
        Assert.assertEquals(SoundProblem.NONE, decide(soundSelected = false, after = SoundSnapshot(13_000, 100)))
    }

    @Test
    fun `No counters means we can't tell`() {
        Assert.assertEquals(SoundProblem.NONE, decide(before = SoundSnapshot(10_000, null), after = SoundSnapshot(13_000, null)))
    }

    @Test
    fun `Sound thrown away after a skip counts as no sound`() {
        // Before the fix, thrown away pieces were added in and this looked fine
        Assert.assertEquals(
            SoundProblem.SOUND_STUCK,
            decide(before = SoundSnapshot(10_000, 100, 0), after = SoundSnapshot(13_000, 100, 150)),
        )
    }
}
