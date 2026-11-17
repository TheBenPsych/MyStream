package com.github.damontecres.wholphin.ui.setup

import androidx.annotation.OptIn
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
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
import com.github.damontecres.wholphin.ui.theme.SwSides
import com.github.damontecres.wholphin.ui.theme.drawLaserSword
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin

/**
 * Benflix Star Wars Day: the desert home behind Who's watching (4 May only). Drawn by the web version
 * (server/web/js/starwars.js) and recorded as 27-second loops by tools/starwars/record.js, with only the
 * laser sword's hilt: the glowing blade is drawn here on top, in the colour of the side picked by the
 * profile you're on (blue for the light side, red for the dark side).
 */
@OptIn(UnstableApi::class)
@Composable
fun StarWarsRoom(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    SwSides.init(context)
    BoxWithConstraints(
        modifier =
            modifier
                .clipToBounds()
                .background(Brush.verticalGradient(listOf(Color(0xFF22160F), Color(0xFF120C0A)))),
    ) {
        val tall = maxWidth < maxHeight * 0.8f
        val name = if (tall) "tall" else "wide"
        var file by remember(name) { mutableStateOf(SwVideos.cached(context, name)) }
        LaunchedEffect(name) { if (file == null) file = SwVideos.fetch(context, name) }
        val video = file ?: return@BoxWithConstraints

        val player =
            remember(video) {
                ExoPlayer.Builder(context).build().apply {
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_ONE
                    setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(video)))
                    prepare()
                    playWhenReady = true
                }
            }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(player, lifecycle) {
            val observer =
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> {
                            player.play()
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
        // Fill the screen and crop, keeping the floor (the laser sword and the hologram) at the bottom
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
        }
        // The blade, over the hilt in the video: in the colour of the profile you're on
        val blade = SwSides.blade(SwRoomFocus.user ?: SwSides.currentUser())
        val hum by rememberInfiniteTransition(label = "swBlade").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
            label = "swBladeHum",
        )
        val boxW = maxWidth.value
        val boxH = maxHeight.value
        Canvas(Modifier.fillMaxSize()) {
            val px = density
            // where the video's picture sits on screen (in dp), then the blade from the room's layout
            val left = (boxW - vw * scale) / 2f
            val top = boxH - vh * scale
            // the room's drawing units to video pixels (see LAYOUTS in starwars.js)
            val (k, kx) = if (tall) 1170f / 844f to -0.3f else 1280f / 1920f to 0f
            val b = if (tall) TALL_BLADE else WIDE_BLADE

            fun x(v: Float) = (left + (v * k + kx) * scale) * px

            fun y(v: Float) = (top + v * k * scale) * px
            val bw = b[3] * k * scale * px
            val hilt = 2f
            val glow = .9f + .1f * sin(hum * 2f * PI.toFloat() * 3f) * sin(hum * 2f * PI.toFloat())
            // the hilt is already in the video, so the blade starts right at its emitter
            drawLaserSword(x(b[0]) - hilt + 2f, y(b[2]), hilt, x(b[1]), bw, blade, glow = glow, withHilt = false)
        }
    }
}

/** Where the blade is in the room's drawing (x from, x to, y, thickness): the same as starwars.js LAYOUTS */
private val WIDE_BLADE = floatArrayOf(184f, 330f, 786f, 7f)
private val TALL_BLADE = floatArrayOf(122.8f, 188.5f, 770.6f, 3.15f)

/** The profile the remote is on in Who's watching, so the room's laser sword matches that profile's side */
object SwRoomFocus {
    var user by mutableStateOf<UUID?>(null)
}

/** Downloads and keeps the Star Wars Day room videos (extras/scenes/starwars in the repo: wide and tall) */
object SwVideos : RoomVideos("starwars", version = 1)
