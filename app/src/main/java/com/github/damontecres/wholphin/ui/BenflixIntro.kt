package com.github.damontecres.wholphin.ui

import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Benflix startup animation. The logo shows still for about 2 seconds while the app loads
 * behind it (so nothing lags), then the "ta-dum" sound plays, the B pops, and it zooms
 * toward the screen as everything fades away. It plays every time Benflix is opened fresh.
 */
@Composable
fun BenflixIntro(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val markScale = remember { Animatable(1f) }
    val markAlpha = remember { Animatable(0f) }
    val wordAlpha = remember { Animatable(0f) }
    val screenAlpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        // Get the sound ready while the still logo is showing
        val player =
            try {
                MediaPlayer.create(context, R.raw.benflix_intro)?.apply {
                    setVolume(.7f, .7f)
                    setOnCompletionListener { it.release() }
                }
            } catch (ex: Exception) {
                Timber.w(ex, "Could not load intro sound")
                null
            }
        var started = false
        try {
            // 1) Still logo for about 2 seconds while the app loads in the background
            coroutineScope {
                launch { markAlpha.animateTo(1f, tween(300)) }
                launch { wordAlpha.animateTo(1f, tween(300)) }
            }
            delay(1700)
            // 2) Sound + the B pops
            try {
                player?.start()
                started = player != null
            } catch (ex: Exception) {
                Timber.w(ex, "Could not play intro sound")
            }
            markScale.animateTo(1.12f, tween(450, easing = FastOutSlowInEasing))
            markScale.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
            delay(500)
            // 3) Zoom toward the screen and fade away
            coroutineScope {
                launch { wordAlpha.animateTo(0f, tween(250)) }
                launch { markScale.animateTo(9f, tween(850, easing = FastOutLinearInEasing)) }
                launch {
                    delay(350)
                    screenAlpha.animateTo(0f, tween(500))
                }
            }
        } finally {
            if (!started) player?.release()
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
                            scaleX = markScale.value
                            scaleY = markScale.value
                            alpha = markAlpha.value
                        },
            )
            Image(
                painter = painterResource(R.drawable.benflix_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .height(36.dp)
                        .graphicsLayer { alpha = wordAlpha.value },
            )
        }
    }
}
