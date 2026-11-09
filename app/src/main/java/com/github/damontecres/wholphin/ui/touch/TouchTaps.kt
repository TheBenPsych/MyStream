package com.github.damontecres.wholphin.ui.touch

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.window.DialogProperties
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.window.Dialog as ComposeDialog

/**
 * Benflix on phones: the TV buttons only listen to the remote, so a finger tap does nothing.
 * This watches for taps, finds the button under the finger and presses it (hold = long press).
 * Does nothing on TVs.
 */
fun Modifier.touchTaps(): Modifier =
    composed {
        val context = LocalContext.current
        val isTv = remember { isTvDevice(context) }
        if (isTv) return@composed this
        val view = LocalView.current
        val slop = LocalViewConfiguration.current.touchSlop
        val longPressMs = LocalViewConfiguration.current.longPressTimeoutMillis
        val origin = remember { FloatArray(2) }
        this
            .onGloballyPositioned {
                val p = it.positionInWindow()
                origin[0] = p.x
                origin[1] = p.y
            }.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                    val at = Offset(down.position.x + origin[0], down.position.y + origin[1])
                    var moved = false
                    var tapped = false
                    // Watch the finger until it lifts, moves (a scroll) or has been held long enough
                    val ended =
                        withTimeoutOrNull(longPressMs) {
                            var up = false
                            while (!up && !moved) {
                                val change =
                                    awaitPointerEvent(PointerEventPass.Final)
                                        .changes
                                        .firstOrNull { it.id == down.id }
                                if (change == null) {
                                    up = true
                                } else {
                                    if ((change.position - down.position).getDistance() > slop) moved = true
                                    if (!change.pressed) {
                                        up = true
                                        tapped = !moved && !change.isConsumed
                                    }
                                }
                            }
                            up
                        }
                    if (tapped) {
                        pressAt(view, at, long = false)
                        return@awaitEachGesture
                    }
                    // Benflix: a hold opens the menu right away, while the finger is still down
                    if (ended == null && !moved) pressAt(view, at, long = true)
                    if (ended != true) {
                        // Wait for the finger to lift so letting go doesn't press anything else
                        while (true) {
                            val change =
                                awaitPointerEvent(PointerEventPass.Final)
                                    .changes
                                    .firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                        }
                    }
                }
            }
    }

private fun pressAt(
    view: android.view.View,
    at: Offset,
    long: Boolean,
) {
    val root = (view as? RootForTest)?.semanticsOwner?.unmergedRootSemanticsNode ?: return
    val node = findTarget(root, at) ?: return
    if (long) {
        // The poster's hold action can sit on the card around the part under the finger
        var holder: SemanticsNode? = node
        while (holder != null) {
            val longAction = holder.config.getOrNull(SemanticsActions.OnLongClick)?.action
            if (longAction != null) {
                Haptics.hold(view)
                longAction.invoke()
                return
            }
            holder = holder.parent
        }
    }
    // Benflix: a light buzz, so a tap feels like it did something
    Haptics.tap(view)
    node.config
        .getOrNull(SemanticsActions.OnClick)
        ?.action
        ?.invoke()
}

private fun findTarget(
    node: SemanticsNode,
    at: Offset,
): SemanticsNode? {
    if (!node.boundsInWindow.contains(at)) return null
    for (child in node.children.asReversed()) {
        findTarget(child, at)?.let { return it }
    }
    val clickable = node.config.getOrNull(SemanticsActions.OnClick) != null
    val disabled = node.config.getOrNull(SemanticsProperties.Disabled) != null
    return if (clickable && !disabled) node else null
}

/**
 * Same as Compose's Dialog, plus finger taps work on phones, and on TV a held OK can't press
 * anything inside it (see [ignoreOkHeldFromBefore]).
 */
@Composable
fun Dialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) = ComposeDialog(onDismissRequest = onDismissRequest, properties = properties) {
    Box(
        Modifier
            .ignoreOkHeldFromBefore()
            .touchTaps(),
    ) { content() }
}

/**
 * Benflix: holding OK on a card opens its menu, but the remote keeps sending "OK still held" and
 * then "OK let go" to whatever is now selected, which is the menu's first row (often Play). So
 * letting go pressed Play instead of leaving the menu open.
 *
 * This ignores every OK (and Enter) signal inside a pop-up until OK is pressed fresh in it, so the
 * press that opened the menu can never press something in it. Used by every pop-up in the app.
 */
fun Modifier.ignoreOkHeldFromBefore(): Modifier =
    composed {
        val pressStartedHere = remember { BooleanArray(1) }
        onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            if (native.keyCode !in OK_KEYS) return@onPreviewKeyEvent false
            when (native.action) {
                AndroidKeyEvent.ACTION_DOWN -> {
                    if (native.repeatCount == 0) {
                        pressStartedHere[0] = true
                        false
                    } else {
                        !pressStartedHere[0]
                    }
                }

                AndroidKeyEvent.ACTION_UP -> {
                    val ignore = !pressStartedHere[0]
                    pressStartedHere[0] = false
                    ignore
                }

                else -> {
                    false
                }
            }
        }
    }

private val OK_KEYS =
    setOf(
        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
        AndroidKeyEvent.KEYCODE_ENTER,
        AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
        AndroidKeyEvent.KEYCODE_BUTTON_A,
    )
