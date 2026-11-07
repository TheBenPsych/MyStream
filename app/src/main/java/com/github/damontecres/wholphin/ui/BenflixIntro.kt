package com.github.damontecres.wholphin.ui

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.annotation.RawRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.BenflixWordLogo
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.util.StartupTimer
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Shared spots the startup intro and the Home doors need (Ben's approved intro, Nov 2026):
 * the logo on Who's watching, the logo squares in the top bar, and whether the doors should grow
 * out of the logo the next time Home opens.
 */
object BenflixIntroState {
    private val _running = MutableStateFlow(false)

    /** The intro is on screen. Who's watching hides its own logo until the moving one lands on it. */
    val running: StateFlow<Boolean> = _running

    fun setRunning(value: Boolean) {
        _running.value = value
    }

    /** Where Who's watching draws its logo (the whole logo, in window pixels) */
    @Volatile
    var whoLogo: Rect? = null

    /** Where the squares of that logo are */
    @Volatile
    var whoMark: Rect? = null

    /** Where the top bar's logo squares are, so the doors can grow out of them */
    @Volatile
    var barMark: Rect? = null

    /** A profile was just picked on Who's watching: the doors grow out of the logo once */
    var doorsPending by mutableStateOf(false)
}

/**
 * Benflix startup intro (approved r2-flow): the logo fades in (0.3 s) to two soft mallet notes, holds
 * for 0.4 s, then glides up into its spot on Who's watching while the page fades in behind it.
 * About 1.5 s, no skipping. It plays on every fresh open (not when coming back from another app).
 * If Who's watching isn't on screen (a slow server, or the app went straight to Home), the logo fades away.
 * New animations stay on even in Lite mode (Ben's rule, Nov 2026).
 */
@Composable
fun BenflixIntro(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val logoAlpha = remember { Animatable(0f) }
    val screenAlpha = remember { Animatable(1f) }
    val glide = remember { Animatable(0f) }
    var logoRect by remember { mutableStateOf<Rect?>(null) }
    var markRect by remember { mutableStateOf<Rect?>(null) }
    var target by remember { mutableStateOf<Pair<Rect, Rect>?>(null) }
    LaunchedEffect(Unit) {
        BenflixIntroState.setRunning(true)
        try {
            launch { playIntroSound(context, R.raw.benflix_intro_open, INTRO_VOLUME) }
            logoAlpha.animateTo(1f, tween(FADE_MS))
            delay(HOLD_MS)
            // Wait a little for Who's watching to be ready (a slow server goes on anyway)
            var waited = 0L
            while (BenflixIntroState.whoMark == null && waited < WAIT_FOR_PAGE_MS) {
                delay(40)
                waited += 40
            }
            val whoLogo = BenflixIntroState.whoLogo
            val whoMark = BenflixIntroState.whoMark
            if (whoLogo != null && whoMark != null && logoRect != null && markRect != null) {
                target = whoLogo to whoMark
                coroutineScope {
                    launch { glide.animateTo(1f, tween(GLIDE_MS, easing = FastOutSlowInEasing)) }
                    launch { screenAlpha.animateTo(0f, tween(GLIDE_MS)) }
                }
            } else {
                coroutineScope {
                    launch { logoAlpha.animateTo(0f, tween(FADE_OUT_MS)) }
                    launch { screenAlpha.animateTo(0f, tween(FADE_OUT_MS)) }
                }
            }
        } finally {
            BenflixIntroState.setRunning(false)
            StartupTimer.mark(StartupTimer.INTRO)
            onFinished()
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = screenAlpha.value }
                .background(BenflixColors.Tray),
        )
        BenflixWordLogo(
            height = INTRO_LOGO_HEIGHT.dp,
            markModifier = Modifier.onGloballyPositioned { if (glide.value == 0f) markRect = it.boundsInWindow() },
            modifier =
                Modifier
                    .onGloballyPositioned { if (glide.value == 0f) logoRect = it.boundsInWindow() }
                    .graphicsLayer {
                        alpha = logoAlpha.value
                        val t = target
                        val row = logoRect
                        val mark = markRect
                        if (t != null && row != null && mark != null && mark.width > 0f) {
                            val (_, toMark) = t
                            val p = glide.value
                            val s = 1f + (toMark.width / mark.width - 1f) * p
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = s
                            scaleY = s
                            val endX = toMark.left - row.left - (mark.left - row.left) * (toMark.width / mark.width)
                            val endY = toMark.top - row.top - (mark.top - row.top) * (toMark.width / mark.width)
                            translationX = endX * p
                            translationY = endY * p
                        }
                    },
        )
    }
}

/**
 * Plays one of the intro sounds quietly. Phones on silent or vibrate stay quiet (TVs always play).
 */
suspend fun playIntroSound(
    context: Context,
    @RawRes sound: Int,
    volume: Float,
) {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    val isTv = context.packageManager.hasSystemFeature("android.software.leanback")
    if (!isTv && audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
    val player =
        withContext(WholphinDispatchers.IO) {
            try {
                MediaPlayer.create(context, sound)?.apply {
                    setVolume(volume, volume)
                    setOnCompletionListener { it.release() }
                }
            } catch (ex: Exception) {
                Timber.w(ex, "Could not load intro sound")
                null
            }
        } ?: return
    try {
        player.start()
    } catch (ex: Exception) {
        Timber.w(ex, "Could not play intro sound")
        player.release()
    }
}

/** How big the logo is in the middle of the screen */
private const val INTRO_LOGO_HEIGHT = 56

/** Quiet, about half volume (doors sound is a little louder) */
const val INTRO_VOLUME = 0.45f
const val DOORS_VOLUME = 0.55f

private const val FADE_MS = 300
private const val HOLD_MS = 400L
private const val GLIDE_MS = 800
private const val FADE_OUT_MS = 300
private const val WAIT_FOR_PAGE_MS = 900L
