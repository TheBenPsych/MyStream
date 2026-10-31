package com.github.damontecres.wholphin.ui.touch

import android.content.pm.PackageManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.window.Dialog as ComposeDialog

/**
 * Benflix on phones: the TV buttons only listen to the remote, so a finger tap does nothing.
 * This watches for taps, finds the button under the finger and presses it (hold = long press).
 * Does nothing on TVs.
 */
fun Modifier.touchTaps(): Modifier =
    composed {
        val context = LocalContext.current
        val isTv =
            remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
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
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if ((change.position - down.position).getDistance() > slop) moved = true
                        if (!change.pressed) {
                            if (!moved && !change.isConsumed) {
                                val held = change.uptimeMillis - down.uptimeMillis
                                val at = Offset(down.position.x + origin[0], down.position.y + origin[1])
                                pressAt(view, at, held >= longPressMs)
                            }
                            break
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
    val config = node.config
    if (long) {
        val longAction = config.getOrNull(SemanticsActions.OnLongClick)?.action
        if (longAction != null) {
            longAction.invoke()
            return
        }
    }
    config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
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

/** Same as Compose's Dialog, plus finger taps work on phones. */
@Composable
fun Dialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) = ComposeDialog(onDismissRequest = onDismissRequest, properties = properties) {
    Box(Modifier.touchTaps()) { content() }
}
