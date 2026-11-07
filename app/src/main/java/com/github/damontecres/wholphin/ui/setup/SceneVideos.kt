package com.github.damontecres.wholphin.ui.setup

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.github.damontecres.wholphin.services.LogoEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Benflix: the holiday scenes on Who's watching as short looping videos.
 *
 * Drawing a scene live, 25 times a second, made newer Google TVs lag. The "Record holiday scenes" workflow
 * turns each scene into a 12-second loop (extras/scenes in the repo). The first time a holiday comes round,
 * its video is downloaded in the background (about 1 MB); until it's there, the scene is drawn live as before.
 */
object SceneVideos {
    private const val BASE = "https://raw.githubusercontent.com/TheBenPsych/MyStream/main/extras/scenes"
    private const val MIN_BYTES = 10_000L

    private val http by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Scenes that failed to download this time the app is open (not found yet, no internet) */
    private val failed: MutableSet<LogoEffect> =
        java.util.concurrent.ConcurrentHashMap
            .newKeySet()

    /** One download at a time, so two screens asking at once don't both write the same file */
    private val downloadLock = kotlinx.coroutines.sync.Mutex()

    /**
     * Scenes that were redrawn after their first video went out. The new video gets a new name, so devices
     * that saved the old one download the new one instead of playing the old picture forever.
     */
    private val VERSIONS = mapOf(LogoEffect.EMBERS to 2)

    /** The scene video's name, without ".mp4" (also used by the scene recorder) */
    fun videoName(effect: LogoEffect): String = VERSIONS[effect]?.let { "${effect.name}-v$it" } ?: effect.name

    private fun file(
        context: Context,
        effect: LogoEffect,
    ) = File(context.filesDir, "scenes/${videoName(effect)}.mp4")

    /** The scene's video if it's on this device */
    fun cached(
        context: Context,
        effect: LogoEffect,
    ): File? = file(context, effect).takeIf { it.length() > MIN_BYTES }

    /** Downloads the scene's video. Returns it, or null when there isn't one (yet). */
    suspend fun fetch(
        context: Context,
        effect: LogoEffect,
    ): File? =
        withContext(Dispatchers.IO) {
            cached(context, effect)?.let { return@withContext it }
            if (effect == LogoEffect.NONE || effect in failed) return@withContext null
            downloadLock.withLock { download(context, effect) }
        }

    private fun download(
        context: Context,
        effect: LogoEffect,
    ): File? {
        // Another screen may have just fetched it while this one waited its turn
        cached(context, effect)?.let { return it }
        if (effect in failed) return null
        val target = file(context, effect)
        return try {
            target.parentFile?.mkdirs()
            val tmp = File(target.path + ".part")
            http.newCall(Request.Builder().url("$BASE/${videoName(effect)}.mp4").build()).execute().use { response ->
                if (!response.isSuccessful) {
                    failed += effect
                    return null
                }
                tmp.outputStream().use { response.body.byteStream().copyTo(it) }
            }
            if (tmp.length() <= MIN_BYTES || !tmp.renameTo(target)) {
                tmp.delete()
                failed += effect
                return null
            }
            // Only the holidays coming round need to stay: keep the newest few
            target.parentFile
                ?.listFiles()
                ?.filter { it.extension == "mp4" }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(KEEP)
                ?.forEach { it.delete() }
            target
        } catch (e: Exception) {
            Timber.i("Holiday scene video not downloaded: %s", e.javaClass.simpleName)
            failed += effect
            null
        }
    }

    private const val KEEP = 4
}
