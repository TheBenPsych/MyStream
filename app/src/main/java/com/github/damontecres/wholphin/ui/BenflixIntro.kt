package com.github.damontecres.wholphin.ui

import android.content.Context
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
import androidx.core.content.edit
import com.github.damontecres.wholphin.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate

/**
 * Benflix startup animation: the red B grows in with a "ta-dum" sound, the name fades in,
 * then the B zooms toward the screen and everything fades away. About 4 seconds.
 *
 * It only plays on the first launch of each day, so opening Benflix again later is instant.
 */
object BenflixIntroSchedule {
    private const val PREFS = "benflix_intro"
    private const val KEY_LAST_DAY = "last_day"

    /** True the first time this is asked on a given day (and remembers that it was asked) */
    fun shouldShowToday(context: Context): Boolean {
        val today = LocalDate.now().toString()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_DAY, null) == today) return false
        prefs.edit { putString(KEY_LAST_DAY, today) }
        return true
    }
}

@Composable
fun BenflixIntro(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val markScale = remember { Animatable(0.55f) }
    val markAlpha = remember { Animatable(0f) }
    val wordAlpha = remember { Animatable(0f) }
    val screenAlpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        try {
            MediaPlayer.create(context, R.raw.benflix_intro)?.apply {
                setVolume(.7f, .7f)
                setOnCompletionListener { it.release() }
                start()
            }
        } catch (ex: Exception) {
            Timber.w(ex, "Could not play intro sound")
        }
        try {
            coroutineScope {
                launch { markAlpha.animateTo(1f, tween(600)) }
                markScale.animateTo(1f, tween(1300, easing = FastOutSlowInEasing))
            }
            wordAlpha.animateTo(1f, tween(600))
            delay(1300)
            coroutineScope {
                launch { wordAlpha.animateTo(0f, tween(250)) }
                launch { markScale.animateTo(9f, tween(850, easing = FastOutLinearInEasing)) }
                launch {
                    delay(350)
                    screenAlpha.animateTo(0f, tween(500))
                }
            }
        } finally {
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
