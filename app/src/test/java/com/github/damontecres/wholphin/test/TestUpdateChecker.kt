package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.services.NotEnoughRoomException
import com.github.damontecres.wholphin.services.getDownloadUrl
import com.github.damontecres.wholphin.services.isOutOfSpace
import com.github.damontecres.wholphin.services.updateRoomNeeded
import com.github.damontecres.wholphin.ui.setup.installFailedNote
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.readText

class TestUpdateChecker {
    lateinit var releaseJson: JsonObject
    val assetsJson: JsonArray by lazy { releaseJson["assets"]!!.jsonArray }

    lateinit var releaseV108Json: JsonObject
    val assetsV108Json: JsonArray by lazy { releaseV108Json["assets"]!!.jsonArray }

    private fun read(filename: String): JsonObject {
        val resource = javaClass.classLoader?.getResource(filename)
        Assert.assertNotNull(resource)
        val fileContents = Paths.get(resource!!.toURI()).readText()
        return Json.parseToJsonElement(fileContents).jsonObject
    }

    @Before
    fun setup() {
        releaseJson = read("release_develop.json")
    }

    @Test
    fun `Release chooses release`() {
        val url = getDownloadUrl(assetsJson, false, listOf())
        Assert.assertEquals("https://github.com/damontecres/Wholphin/releases/download/develop/Benflix-release.apk", url)
    }

    @Test
    fun `Choose abi`() {
        val url = getDownloadUrl(assetsJson, false, listOf("arm64-v8a"))
        Assert.assertEquals("https://github.com/damontecres/Wholphin/releases/download/develop/Benflix-release-arm64-v8a.apk", url)
    }

    @Test
    fun `Choose unknown abi`() {
        val url = getDownloadUrl(assetsJson, false, listOf("unknown"))
        Assert.assertEquals("https://github.com/damontecres/Wholphin/releases/download/develop/Benflix-release.apk", url)
    }

    @Test
    fun `Debug chooses debug`() {
        val url = getDownloadUrl(assetsJson, true, listOf())
        Assert.assertEquals("https://github.com/damontecres/Wholphin/releases/download/develop/Benflix-debug.apk", url)
    }

    @Test
    fun `Choose debug abi`() {
        val url = getDownloadUrl(assetsJson, true, listOf("arm64-v8a"))
        Assert.assertEquals("https://github.com/damontecres/Wholphin/releases/download/develop/Benflix-debug-arm64-v8a.apk", url)
    }

    @Test
    fun `Test regular release`() {
        releaseV108Json = read("release_v108.json")
        getDownloadUrl(assetsV108Json, false, listOf("arm64-v8a")).let { url ->
            Assert.assertEquals(
                "https://github.com/damontecres/Wholphin/releases/download/v1.0.8/Benflix-arm64-v8a.apk",
                url,
            )
        }
        getDownloadUrl(assetsV108Json, false, listOf("armeabi-v7a")).let { url ->
            Assert.assertEquals(
                "https://github.com/damontecres/Wholphin/releases/download/v1.0.8/Benflix-armeabi-v7a.apk",
                url,
            )
        }
        getDownloadUrl(assetsV108Json, false, emptyList()).let { url ->
            Assert.assertEquals(
                "https://github.com/damontecres/Wholphin/releases/download/v1.0.8/Benflix.apk",
                url,
            )
        }
        getDownloadUrl(assetsV108Json, true, listOf("arm64-v8a")).let { url ->
            Assert.assertNull(url)
        }
    }

    @Test
    fun `Update needs room for the download, the copy and the unpacked app`() {
        val mb = 1024L * 1024
        Assert.assertEquals(132 * mb, updateRoomNeeded(44 * mb))
        // Size unknown: assume a typical update
        Assert.assertEquals(150 * mb, updateRoomNeeded(-1L))
    }

    @Test
    fun `Full storage is recognised`() {
        Assert.assertTrue(isOutOfSpace(java.io.IOException("write failed: ENOSPC (No space left on device)")))
        Assert.assertTrue(isOutOfSpace(RuntimeException("wrapper", java.io.IOException("No space left on device"))))
        Assert.assertFalse(isOutOfSpace(java.io.IOException("Connection reset")))
        Assert.assertFalse(isOutOfSpace(NotEnoughRoomException(1, 2)))
    }

    @Test
    fun `Failed install note names the likely cause`() {
        val low = installFailedNote(120, false)
        Assert.assertTrue(low.contains("only 120 MB is free"))
        Assert.assertFalse(low.contains("fresh copy"))
        val roomy = installFailedNote(3000, true)
        Assert.assertTrue(roomy.contains("room looks fine"))
        Assert.assertTrue(roomy.contains("fresh copy"))
        Assert.assertFalse(installFailedNote(-1, false).contains("MB"))
    }
}
