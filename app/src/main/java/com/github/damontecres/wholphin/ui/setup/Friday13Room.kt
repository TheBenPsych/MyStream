package com.github.damontecres.wholphin.ui.setup

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import java.io.File

/**
 * Benflix Friday the 13th: the room behind Who's watching (only on a Friday the 13th). A dim plum room with
 * flickering globe bulbs, a crystal ball, a dripping candle and a black cat asleep on a cushion, and a moonlit
 * lake through the window with people walking past.
 *
 * Drawn by the web version (server/web/js/f13.js) and recorded as a 30-second loop by tools/f13/record.js. When
 * someone picks a profile ([waking]), a short clip of just the cat opening its eyes and yawning plays on top of the
 * cat, so the rest of the room keeps moving underneath.
 */
@OptIn(UnstableApi::class)
@Composable
fun Friday13Room(
    waking: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    BoxWithConstraints(
        modifier =
            modifier
                .clipToBounds()
                .background(Brush.verticalGradient(listOf(Color(0xFF0E0B16), Color(0xFF0A0807)))),
    ) {
        val tall = maxWidth < maxHeight * 0.8f
        val shape = if (tall) "tall" else "wide"
        var room by remember(shape) { mutableStateOf(F13Videos.cached(context, shape)) }
        var wake by remember(shape) { mutableStateOf(F13Videos.cached(context, "wake-$shape")) }
        LaunchedEffect(shape) {
            if (room == null) room = F13Videos.fetch(context, shape)
            if (wake == null) wake = F13Videos.fetch(context, "wake-$shape")
        }
        val video = room ?: return@BoxWithConstraints

        val player = rememberRoomPlayer(context, video, loop = true)
        // The cat clip waits, ready on its first picture, until a profile is picked
        val catPlayer = wake?.let { rememberRoomPlayer(context, it, loop = false) }
        LaunchedEffect(waking, catPlayer) {
            if (waking) {
                catPlayer?.seekTo(0)
                catPlayer?.play()
            }
        }

        // Fill the screen and crop, keeping the floor (the table and the cat) at the bottom
        val (vw, vh) = if (tall) 540f to 1170f else 1280f to 720f
        val scale = maxOf(maxWidth.value / vw, maxHeight.value / vh)
        Box(Modifier.fillMaxSize()) {
            PlayerSurface(
                player = player,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .requiredSize((vw * scale).dp, (vh * scale).dp),
            )
            if (catPlayer != null) {
                val clip = F13Videos.catClip(tall)
                val left = (maxWidth.value - vw * scale) / 2f
                val top = maxHeight.value - vh * scale
                PlayerSurface(
                    player = catPlayer,
                    surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .offset((left + clip.x * scale).dp, (top + clip.y * scale).dp)
                            .requiredSize((clip.w * scale).dp, (clip.h * scale).dp)
                            .graphicsLayer { alpha = if (waking) 1f else 0f },
                )
            }
        }
    }
}

/** A muted player for one of the room's videos that pauses while the app is in the background */
@Composable
private fun rememberRoomPlayer(
    context: Context,
    video: File,
    loop: Boolean,
): ExoPlayer {
    val player =
        remember(video) {
            ExoPlayer.Builder(context).build().apply {
                volume = 0f
                repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(video)))
                prepare()
                playWhenReady = loop
            }
        }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player, lifecycle) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        if (loop) player.play()
                    }

                    Lifecycle.Event.ON_STOP -> {
                        player.pause()
                    }

                    else -> {}
                }
            }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.release()
        }
    }
    return player
}

/**
 * Downloads and keeps the Friday the 13th room videos (extras/scenes/f13 in the repo: wide, tall, and the cat
 * clips wake-wide and wake-tall).
 */
object F13Videos : RoomVideos("f13", version = 1) {
    /** Where the cat clip sits in the room video, in the video's own pixels (printed by tools/f13/record.js) */
    data class Clip(
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
    )

    private val WIDE = Clip(298f, 424f, 150f, 168f)
    private val TALL = Clip(292f, 992f, 158f, 178f)

    fun catClip(tall: Boolean) = if (tall) TALL else WIDE

    /** How long the app waits after a profile is picked, so the cat can wake up and yawn */
    const val WAKE_MS = 1500L
}
