package com.github.damontecres.wholphin.ui.detail.title

import org.junit.Assert.assertEquals
import org.junit.Test

class TinyCopyUrlTest {
    @Test
    fun swapsSizeForATinyCopy() {
        assertEquals(
            "http://tower:8096/Items/abc/Images/Backdrop?tag=x1&maxWidth=48&maxHeight=48&quality=60",
            tinyCopyUrl("http://tower:8096/Items/abc/Images/Backdrop?fillHeight=1080&tag=x1&quality=90"),
        )
    }

    @Test
    fun addsSizeWhenThereIsNone() {
        assertEquals(
            "http://tower:8096/Items/abc/Images/Logo?maxWidth=48&maxHeight=48&quality=60",
            tinyCopyUrl("http://tower:8096/Items/abc/Images/Logo"),
        )
    }

    @Test
    fun leavesOtherLinksAlone() {
        val url = "http://tower:8097/art/abc.jpg?w=1920"
        assertEquals(url, tinyCopyUrl(url))
    }
}
