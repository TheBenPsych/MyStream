package com.github.damontecres.wholphin.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.util.lerp
import com.github.damontecres.wholphin.ui.theme.BenflixMotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Approved polish idea "Opening a door feels like walking in": when you pick a door on Home, its flat
 * colour grows from the door to fill the screen, the door's page opens underneath, and the colour fades
 * away. Only a flat colour, so it stays quick on older TVs.
 *
 * Ben: the colour used to fade straight away while Home was still cross-fading out, so Home's doors
 * showed through and the page's rows popped in after. Now the page swaps in instantly under the colour
 * (no cross-fade, see ApplicationContent), and the colour stays until the page says its first rows are
 * on screen ([ready]), or [MAX_HOLD_MS] at most, then fades straight onto the finished page.
 * On for everyone, Lite mode or not (Ben's rule for the new animations).
 */
object DoorWipe {
    data class Wipe(
        val color: Color,
        /** The door's box on screen (window coordinates), or null to grow from the middle */
        val from: Rect?,
        val then: () -> Unit,
        val id: Long = System.nanoTime(),
    )

    private val _wipe = MutableStateFlow<Wipe?>(null)
    val wipe: StateFlow<Wipe?> = _wipe

    private val _ready = MutableStateFlow(false)

    /** True while the colour covers the screen: the page change underneath shouldn't animate */
    val covering: Boolean get() = _wipe.value != null

    /** Called by a door's page once its first rows (or its empty message) are on screen */
    fun ready() {
        _ready.value = true
    }

    internal suspend fun awaitReady() {
        _ready.first { it }
    }

    /** Fills the screen with [color] from [from], runs [then] (open the page), then fades out */
    fun start(
        color: Color,
        from: Rect?,
        then: () -> Unit,
    ) {
        if (_wipe.value != null) return
        _ready.value = false
        _wipe.value = Wipe(color, from, then)
    }

    internal fun done(id: Long) {
        if (_wipe.value?.id == id) _wipe.value = null
    }
}

/** Drawn once over the whole app (see ApplicationContent) */
@Composable
fun DoorWipeOverlay(modifier: Modifier = Modifier) {
    val wipe by DoorWipe.wipe.collectAsState()
    val w = wipe ?: return
    val grow = remember(w.id) { Animatable(0f) }
    val fade = remember(w.id) { Animatable(1f) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(w.id) {
        grow.animateTo(1f, tween(BenflixMotion.IN_MS, easing = BenflixMotion.Ease))
        w.then()
        // Hold the colour until the page has its first rows, so it fades onto the finished page
        withTimeoutOrNull(MAX_HOLD_MS) { DoorWipe.awaitReady() }
        // Let the page draw a frame before the colour starts to go
        withFrameNanos { }
        withFrameNanos { }
        fade.animateTo(0f, tween(FADE_MS, easing = BenflixMotion.Ease))
        DoorWipe.done(w.id)
    }
    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.positionInWindow() },
    ) {
        val p = grow.value
        val start = w.from?.translate(-origin.x, -origin.y) ?: Rect(center, 0f)
        val left = lerp(start.left, 0f, p)
        val top = lerp(start.top, 0f, p)
        val right = lerp(start.right, size.width, p)
        val bottom = lerp(start.bottom, size.height, p)
        drawRect(
            color = w.color,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            alpha = fade.value,
        )
    }
}

private const val FADE_MS = 260

/** Slow server or TV: never hold the colour longer than this; the page's grey rows show instead */
private const val MAX_HOLD_MS = 1500L
