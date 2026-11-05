package com.github.damontecres.wholphin.ui.playback

import org.junit.Assert
import org.junit.Test

class TestSurroundPassthrough {
    @Test
    fun `TCL TVs start with passthrough off`() {
        Assert.assertTrue(SurroundPassthrough.badPassthroughDevice("TCL", "TCL"))
        Assert.assertTrue(SurroundPassthrough.badPassthroughDevice("tcl", "Google"))
    }

    @Test
    fun `Other devices keep passthrough`() {
        Assert.assertFalse(SurroundPassthrough.badPassthroughDevice("Amazon", "Amazon"))
        Assert.assertFalse(SurroundPassthrough.badPassthroughDevice("Google", "google"))
    }
}
