package com.github.damontecres.wholphin.ui

import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.util.StartupTimer
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Benflix startup animation. The logo shows still while the app loads behind it (so nothing
 * lags), then the "ta-dum" sound plays as a soft light sweeps across the logo, it settles, and
 * everything fades away (Ben's Look Book pick: light sweep, no zoom). It plays every fresh launch.
 */
@Composable
fun BenflixIntro(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sweep = remember { Animatable(0f) }
    val markAlpha = remember { Animatable(0f) }
    val wordAlpha = remember { Animatable(0f) }
    val screenAlpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        // Get the sound ready while the still logo is showing. Benflix speed: this used to happen
        // before the logo could even fade in, which held the whole intro up on slower TVs
        val playerAsync =
            async(WholphinDispatchers.IO) {
                try {
                    MediaPlayer.create(context, R.raw.benflix_intro)?.apply {
                        setVolume(.7f, .7f)
                        setOnCompletionListener { it.release() }
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not load intro sound")
                    null
                }
            }
        var player: MediaPlayer? = null
        var started = false
        try {
            // 1) Still logo for about 2 seconds while the app loads in the background
            coroutineScope {
                launch { markAlpha.animateTo(1f, tween(300)) }
                launch { wordAlpha.animateTo(1f, tween(300)) }
            }
            // Benflix speed: stop holding as soon as the app is ready behind the logo
            delay(INTRO_MIN_HOLD_MS)
            withTimeoutOrNull(INTRO_MAX_HOLD_MS - INTRO_MIN_HOLD_MS) { StartupTimer.signedIn.first { it } }
            // 2) Sound + a soft light sweeps across the logo
            player = playerAsync.await()
            try {
                player?.start()
                started = player != null
            } catch (ex: Exception) {
                Timber.w(ex, "Could not play intro sound")
            }
            sweep.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
            delay(350)
            // 3) Settle and fade away
            screenAlpha.animateTo(0f, tween(500))
        } finally {
            if (!started) player?.release()
            StartupTimer.mark(StartupTimer.INTRO)
            onFinished()
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer { alpha = screenAlpha.value }
                .background(Color.Black),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(260.dp)
                        .graphicsLayer {
                            alpha = markAlpha.value
                            compositingStrategy = CompositingStrategy.Offscreen
                        }.lightSweep { sweep.value },
            )
            Image(
                painter = painterResource(R.drawable.benflix_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .height(36.dp)
                        .graphicsLayer {
                            alpha = wordAlpha.value
                            compositingStrategy = CompositingStrategy.Offscreen
                        }.lightSweep { sweep.value },
            )
        }
    }
}

/**
 * A soft white band that slides across whatever it is on (only over the drawn pixels).
 * [progress] goes 0 to 1; at 0 and 1 the band is off the edges, so nothing shows.
 */
private fun Modifier.lightSweep(progress: () -> Float): Modifier =
    drawWithContent {
        drawContent()
        val p = progress()
        if (p <= 0f || p >= 1f) return@drawWithContent
        val band = size.width * 0.35f
        val x = -band + (size.width + band * 2) * p
        drawRect(
            brush =
                Brush.linearGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.75f),
                    1f to Color.Transparent,
                    start = Offset(x - band, 0f),
                    end = Offset(x + band, size.height * 0.4f),
                ),
            blendMode = BlendMode.SrcAtop,
        )
    }

/** The still logo shows at least this long, and at most the longer one while the app loads */
private const val INTRO_MIN_HOLD_MS = 600L
private const val INTRO_MAX_HOLD_MS = 1_700L
