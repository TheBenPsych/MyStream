package com.github.damontecres.wholphin.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
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
import com.github.damontecres.wholphin.ui.components.BenflixWordLogo
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.util.StartupTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
 * It waits until the notes are loaded and the screen is drawing, then plays every frame and every
 * note in step; a slow TV plays the whole thing a little slower instead of skipping (see [IntroPace]).
 * New animations stay on even in Lite mode (Ben's rule, Nov 2026).
 */
@Composable
fun BenflixIntro(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val logoAlpha = remember { mutableFloatStateOf(0f) }
    val screenAlpha = remember { mutableFloatStateOf(1f) }
    val glide = remember { mutableFloatStateOf(0f) }
    var logoRect by remember { mutableStateOf<Rect?>(null) }
    var markRect by remember { mutableStateOf<Rect?>(null) }
    var target by remember { mutableStateOf<Pair<Rect, Rect>?>(null) }
    LaunchedEffect(Unit) {
        BenflixIntroState.setRunning(true)
        try {
            // Get ready first (dark screen, nothing moving): the notes in memory, the first frames
            // drawn, and how fast this TV draws. A slow TV waits a moment longer and plays a little slower.
            IntroSounds.awaitReady(context)
            val pace = IntroPace.measure(context)
            val f = pace.stretch
            // The logo fades in on the first note, the second note comes as it finishes, then it holds
            var played = 0
            runIntroTimeline((FADE_MS + HOLD_MS) * f, pace) { t ->
                logoAlpha.floatValue = (t / (FADE_MS * f)).coerceIn(0f, 1f)
                if (played == 0) {
                    IntroSounds.play(context, IntroSounds.OPEN_LOW, OPEN_LOW_VOLUME)
                    played = 1
                }
                if (played == 1 && t >= SECOND_NOTE_MS * f) {
                    IntroSounds.play(context, IntroSounds.OPEN_HIGH, OPEN_HIGH_VOLUME)
                    played = 2
                }
            }
            // Wait for Who's watching to be ready and settled (a slow server goes on anyway)
            val whoMark = waitForSettled(WAIT_FOR_PAGE_MS) { BenflixIntroState.whoMark }
            val whoLogo = BenflixIntroState.whoLogo
            if (whoLogo != null && whoMark != null && logoRect != null && markRect != null) {
                target = whoLogo to whoMark
                runIntroTimeline(GLIDE_MS * f, pace) { t ->
                    val p = t / (GLIDE_MS * f)
                    glide.floatValue = FastOutSlowInEasing.transform(p)
                    screenAlpha.floatValue = 1f - p
                }
            } else {
                runIntroTimeline(FADE_OUT_MS * f, pace) { t ->
                    val p = t / (FADE_OUT_MS * f)
                    logoAlpha.floatValue = 1f - p
                    screenAlpha.floatValue = 1f - p
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
                .graphicsLayer { alpha = screenAlpha.floatValue }
                .background(BenflixColors.Tray),
        )
        BenflixWordLogo(
            height = INTRO_LOGO_HEIGHT.dp,
            markModifier = Modifier.onGloballyPositioned { if (glide.floatValue == 0f) markRect = it.boundsInWindow() },
            modifier =
                Modifier
                    .onGloballyPositioned { if (glide.floatValue == 0f) logoRect = it.boundsInWindow() }
                    .graphicsLayer {
                        alpha = logoAlpha.floatValue
                        val t = target
                        val row = logoRect
                        val mark = markRect
                        if (t != null && row != null && mark != null && mark.width > 0f) {
                            val (_, toMark) = t
                            val p = glide.floatValue
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
 * Waits (at most [timeoutMs]) until [read] gives a spot that stays the same for a few frames in a row,
 * so nothing is still sliding or loading in when an animation starts. Gives back the spot, or null.
 */
suspend fun waitForSettled(
    timeoutMs: Long,
    read: () -> Rect?,
): Rect? {
    var last: Rect? = null
    var same = 0
    val start = System.nanoTime()
    while ((System.nanoTime() - start) / 1_000_000 < timeoutMs) {
        withFrameNanos { }
        val now = read()
        if (now != null && now == last) {
            same++
            if (same >= SETTLED_FRAMES) return now
        } else {
            same = 0
        }
        last = now
    }
    return read()
}

private const val SETTLED_FRAMES = 3

/** How big the logo is in the middle of the screen */
private const val INTRO_LOGO_HEIGHT = 56

/** Quiet, about half volume, as approved (each door note is softer, as four play close together) */
private const val OPEN_LOW_VOLUME = 0.43f
private const val OPEN_HIGH_VOLUME = 0.53f
const val DOOR_NOTE_VOLUME = 0.36f

private const val FADE_MS = 300f
private const val SECOND_NOTE_MS = 250f
private const val HOLD_MS = 400f
private const val GLIDE_MS = 800f
private const val FADE_OUT_MS = 300f
private const val WAIT_FOR_PAGE_MS = 900L
