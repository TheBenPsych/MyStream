package com.github.damontecres.wholphin.ui.touch

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastFirstOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/** How far a finger has to pull down (at the top of the page) to close it */
private val CLOSE_PULL = 140.dp

/**
 * Benflix on phones: on a details page, pull down when you're already at the top to close it,
 * like closing a card. The page follows the finger a little so it feels like it's being pulled.
 * Does nothing on TV.
 */
fun Modifier.swipeDownToClose(): Modifier =
    composed {
        val context = LocalContext.current
        if (isTvDevice(context)) return@composed this
        val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        val view = LocalView.current
        val threshold = with(LocalDensity.current) { CLOSE_PULL.toPx() }
        val pulled = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val connection =
            remember(dispatcher, threshold) {
                object : NestedScrollConnection {
                    var closed = false

                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        // Pushing back up while pulled: take the pull away first
                        if (available.y < 0 && pulled.value > 0f) {
                            val used = maxOf(available.y, -pulled.value)
                            scope.launch { pulled.snapTo(pulled.value + used) }
                            return Offset(0f, used)
                        }
                        return Offset.Zero
                    }

                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        if (source != NestedScrollSource.UserInput || available.y <= 0f || closed) return Offset.Zero
                        val next = pulled.value + available.y
                        scope.launch { pulled.snapTo(next) }
                        if (next >= threshold) {
                            closed = true
                            Haptics.tap(view)
                            dispatcher?.onBackPressed()
                        }
                        return Offset(0f, available.y)
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        closed = false
                        if (pulled.value > 0f) pulled.animateTo(0f)
                        return Velocity.Zero
                    }
                }
            }
        this
            .nestedScroll(connection)
            .graphicsLayer {
                val p = pulled.value
                translationY = p * .45f
                alpha = 1f - (p / threshold).coerceIn(0f, 1f) * .25f
            }
    }

/**
 * Benflix on phones: swipe down on the video to close the player. Only counts a clear, mostly
 * straight downward swipe, so taps and double-taps still work. Does nothing on TV.
 */
fun Modifier.swipeDownToClosePlayer(onClose: () -> Unit): Modifier =
    composed {
        val context = LocalContext.current
        if (isTvDevice(context)) return@composed this
        val close by rememberUpdatedState(onClose)
        val view = LocalView.current
        val threshold = with(LocalDensity.current) { CLOSE_PULL.toPx() }
        pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // Leave the bottom of the screen alone, where the seek bar is
                if (down.position.y > size.height * .7f) return@awaitEachGesture
                var done = false
                while (!done) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.size > 1) return@awaitEachGesture
                    val change = event.changes.fastFirstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    val dy = change.position.y - down.position.y
                    val dx = abs(change.position.x - down.position.x)
                    if (dy >= threshold && dx < dy / 2f) {
                        done = true
                        Haptics.tap(view)
                        close()
                    }
                    if (event.changes.fastAll { !it.pressed }) return@awaitEachGesture
                }
            }
        }
    }

/**
 * Benflix on phones: pull down at the top of Home to load it again. On TV it just shows [content].
 * [refreshing] is true while Home is loading, and [onRefresh] starts it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhonePullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    if (isTvDevice(context)) {
        Box(modifier, content = content)
        return
    }
    val view = LocalView.current
    var pulled by remember { mutableStateOf(false) }
    val loadingNow by rememberUpdatedState(refreshing)
    LaunchedEffect(pulled) {
        if (pulled) {
            // Give the load a moment to start, then wait for it to finish (or give up after 20 s)
            delay(700)
            withTimeoutOrNull(20_000L) {
                while (loadingNow) delay(200)
            }
            pulled = false
        }
    }
    PullToRefreshBox(
        isRefreshing = pulled,
        onRefresh = {
            Haptics.tap(view)
            pulled = true
            onRefresh()
        },
        modifier = modifier.fillMaxSize(),
        content = content,
    )
}
