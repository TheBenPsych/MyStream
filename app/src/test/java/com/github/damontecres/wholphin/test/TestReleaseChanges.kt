package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.services.Release
import com.github.damontecres.wholphin.services.parseReleaseChanges
import com.github.damontecres.wholphin.util.Version
import org.junit.Assert
import org.junit.Test

class TestReleaseChanges {
    private val body =
        """
        Newest Benflix build (version 1.0.103)
        <!-- app-sha:abc -->
        <!-- app-note:Third -->
        <!-- app-change:1.0.103|2026-11-03T10:00:00Z|Third -->
        <!-- app-change:1.0.102|2026-11-02T10:00:00Z|Second A -->
        <!-- app-change:1.0.102|2026-11-02T10:00:00Z|Second B -->
        <!-- app-change:1.0.101|2026-11-01T10:00:00Z|First -->
        """.trimIndent()

    private fun release() =
        Release(
            version = Version.fromString("v1.0.103"),
            downloadUrl = null,
            publishedAt = null,
            body = body,
            notes = listOf("Third"),
            changes = parseReleaseChanges(body),
        )

    @Test
    fun `Only shows versions newer than installed`() {
        val groups = release().changesSince(Version.fromString("v1.0.101"))
        Assert.assertEquals(listOf("v1.0.103", "v1.0.102"), groups.map { it.version.toString() })
        Assert.assertEquals(listOf("Second A", "Second B"), groups[1].titles)
        Assert.assertEquals("2026-11-02T10:00:00Z", groups[1].publishedAt)
    }

    @Test
    fun `One version behind shows just that patch`() {
        val groups = release().changesSince(Version.fromString("v1.0.102"))
        Assert.assertEquals(1, groups.size)
        Assert.assertEquals(listOf("Third"), groups[0].titles)
    }

    @Test
    fun `Old releases without history fall back to notes`() {
        val r = release().copy(changes = emptyList())
        val groups = r.changesSince(Version.fromString("v1.0.50"))
        Assert.assertEquals(listOf("Third"), groups.single().titles)
    }
}
