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
}
