package com.github.damontecres.wholphin.ui.playback

import org.junit.Assert
import org.junit.Test

class TestPictureWatchdog {
    private fun decide(
        slow: Boolean = false,
        before: PictureSnapshot = PictureSnapshot(10_000, 100, 2),
        after: PictureSnapshot = PictureSnapshot(15_000, 220, 3),
        fps: Float? = 23.976f,
    ) = decidePictureProblem(slow, before, after, fps)

    @Test
    fun `Smooth picture is fine`() {
        Assert.assertEquals(PictureProblem.NONE, decide())
    }

    @Test
    fun `Slow backup decoder is flagged`() {
        Assert.assertEquals(PictureProblem.SLOW_DECODER, decide(slow = true))
    }

    @Test
    fun `Many thrown away frames is choppy`() {
        Assert.assertEquals(PictureProblem.CHOPPY, decide(after = PictureSnapshot(15_000, 180, 40)))
    }

    @Test
    fun `Too few frames reach the screen is choppy`() {
        // 5 seconds of 24 fps video should be about 120 frames, only 50 came out
        Assert.assertEquals(PictureProblem.CHOPPY, decide(after = PictureSnapshot(15_000, 150, 2)))
    }

    @Test
    fun `A few dropped frames are normal`() {
        Assert.assertEquals(PictureProblem.NONE, decide(after = PictureSnapshot(15_000, 215, 8)))
    }

    @Test
    fun `Unknown frame rate only uses dropped frames`() {
        Assert.assertEquals(PictureProblem.NONE, decide(after = PictureSnapshot(15_000, 150, 2), fps = null))
    }

    @Test
    fun `Paused picture is not judged`() {
        Assert.assertEquals(PictureProblem.NONE, decide(after = PictureSnapshot(10_500, 100, 30)))
    }

    @Test
    fun `Skip intro while counting is a jump`() {
        // 3 seconds on the clock, but the video moved 90 seconds (the intro was skipped)
        Assert.assertTrue(jumpedDuringCheck(movedMs = 93_000, wallMs = 3_000))
        Assert.assertTrue(jumpedDuringCheck(movedMs = -20_000, wallMs = 3_000))
    }

    @Test
    fun `Normal and fast playback are not jumps`() {
        Assert.assertFalse(jumpedDuringCheck(movedMs = 3_050, wallMs = 3_000))
        Assert.assertFalse(jumpedDuringCheck(movedMs = 6_000, wallMs = 3_000, speed = 2f))
    }
}
