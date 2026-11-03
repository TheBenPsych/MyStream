package com.github.damontecres.wholphin.ui.setup

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import com.github.damontecres.wholphin.services.LogoEffect
import kotlinx.coroutines.Dispatchers
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
    private val failed = mutableSetOf<LogoEffect>()

    private fun file(
        context: Context,
        effect: LogoEffect,
    ) = File(context.filesDir, "scenes/${effect.name}.mp4")

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
            val target = file(context, effect)
            try {
                target.parentFile?.mkdirs()
                val tmp = File(target.path + ".part")
                http.newCall(Request.Builder().url("$BASE/${effect.name}.mp4").build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        failed += effect
                        return@withContext null
                    }
                    tmp.outputStream().use { response.body.byteStream().copyTo(it) }
                }
                if (tmp.length() <= MIN_BYTES || !tmp.renameTo(target)) {
                    tmp.delete()
                    failed += effect
                    return@withContext null
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

/** The scene's video, once it's on this device (downloads it the first time) */
@Composable
internal fun rememberSceneVideo(effect: LogoEffect): File? {
    val context = LocalContext.current
    var video by remember(effect) { mutableStateOf(SceneVideos.cached(context, effect)) }
    LaunchedEffect(effect) {
        // Downloaded now, shown from the next time Who's watching opens (no jump mid-scene)
        if (video == null) SceneVideos.fetch(context, effect)
    }
    return video
}

/** Plays the scene's video on a loop, silently, filling the screen */
@OptIn(UnstableApi::class)
@Composable
internal fun SceneVideo(
    video: File,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player =
        remember(video) {
            ExoPlayer
                .Builder(context)
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(video)))
                    repeatMode = Player.REPEAT_MODE_ONE
                    volume = 0f
                    videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    playWhenReady = true
                    prepare()
                }
        }
    DisposableEffect(player) { onDispose { player.release() } }
    val presentation = rememberPresentationState(player)
    Box(modifier = modifier.clipToBounds()) {
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            modifier = Modifier.fillMaxSize().resizeWithContentScale(ContentScale.Crop, presentation.videoSizeDp),
        )
    }
}
