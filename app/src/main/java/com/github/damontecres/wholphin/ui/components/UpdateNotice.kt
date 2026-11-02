package com.github.damontecres.wholphin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.KidsMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Benflix: a quiet "there's an update" notice instead of a pop-up.
 * - A small red "!" sits on the settings cogwheel (TV) or on My Benflix (phone), on Settings in the
 *   My Benflix menu (phone), and on Help in Settings, so you can follow the "!" to the update.
 * - When an update is first found, a tip bubble says where to find it for a few seconds, then goes away.
 * Kids never see any of it.
 */
object UpdateNotice {
    private val _version = MutableStateFlow<String?>(null)

    /** The newer version that's ready to install, or null when Benflix is up to date */
    val version: StateFlow<String?> = _version

    private val _tip = MutableStateFlow(false)

    /** Show the "New update ready" tip bubble right now */
    val tip: StateFlow<Boolean> = _tip

    fun set(
        newVersion: String?,
        showTip: Boolean,
    ) {
        _version.value = newVersion
        _tip.value = newVersion != null && showTip
    }

    fun hideTip() {
        _tip.value = false
    }

    /** How long the tip bubble stays before it goes away by itself */
    const val TIP_MS = 9_000L
}

/** True when the red "!" should show (an update is ready, and this isn't Kids) */
@Composable
fun rememberUpdateAlert(): Boolean {
    val version by UpdateNotice.version.collectAsState()
    val kids by KidsMode.active.collectAsState()
    return version != null && !kids
}

/** The little red circle with a white "!" */
@Composable
fun UpdateBang(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(size)
                .background(UPDATE_RED, CircleShape),
    ) {
        Text(
            text = "!",
            color = Color.White,
            fontSize = (size.value * .72f).sp,
            lineHeight = (size.value * .72f).sp,
            fontWeight = FontWeight.Black,
        )
    }
}

/**
 * The tip bubble, pointing at whatever it's placed in (the cogwheel on TV, My Benflix on phones).
 * Put it inside a Box around that button. [above] puts it over the button instead of under it.
 * It never takes focus, so the remote keeps working normally while it shows.
 */
@Composable
fun UpdateTip(
    text: String,
    above: Boolean,
) {
    val show by UpdateNotice.tip.collectAsState()
    val kids by KidsMode.active.collectAsState()
    if (kids) return
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(show) {
        if (show) {
            visible = true
            delay(UpdateNotice.TIP_MS)
            visible = false
            delay(400)
            UpdateNotice.hideTip()
        } else {
            visible = false
        }
    }
    if (!show) return
    val gap = with(androidx.compose.ui.platform.LocalDensity.current) { 6.dp.roundToPx() }
    val edge = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.roundToPx() }
    Popup(
        popupPositionProvider = remember(above, gap, edge) { TipPosition(above, gap, edge) },
        properties = PopupProperties(focusable = false),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { if (above) it / 4 else -it / 4 },
            exit = fadeOut() + slideOutVertically { if (above) it / 4 else -it / 4 },
        ) {
            Column(horizontalAlignment = Alignment.End) {
                if (!above) Pointer(up = true)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .widthIn(max = 300.dp)
                            .background(TIP_BG, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    UpdateBang(size = 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    )
                }
                if (above) Pointer(up = false)
            }
        }
    }
}

/** The little triangle that points from the bubble to the button */
@Composable
private fun Pointer(up: Boolean) {
    Canvas(
        modifier =
            Modifier
                .padding(end = 14.dp)
                .size(width = 14.dp, height = 7.dp),
    ) {
        val path =
            Path().apply {
                if (up) {
                    moveTo(0f, size.height)
                    lineTo(size.width / 2, 0f)
                    lineTo(size.width, size.height)
                } else {
                    moveTo(0f, 0f)
                    lineTo(size.width / 2, size.height)
                    lineTo(size.width, 0f)
                }
                close()
            }
        drawPath(path, TIP_BG)
    }
}

/** Lines the bubble's right edge up with the button's right edge, just under (or over) it, kept on screen */
private class TipPosition(
    private val above: Boolean,
    private val gap: Int,
    private val edge: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // Point the triangle (21dp from the bubble's right edge) at the middle of the button
        val pointerFromRight = edge * 21 / 8
        val x =
            (anchorBounds.center.x + pointerFromRight - popupContentSize.width)
                .coerceIn(edge, (windowSize.width - popupContentSize.width - edge).coerceAtLeast(edge))
        val y =
            if (above) {
                anchorBounds.top - popupContentSize.height - gap
            } else {
                anchorBounds.bottom + gap
            }
        return IntOffset(x, y.coerceAtLeast(0))
    }
}

private val UPDATE_RED = Color(0xFFE50914)
private val TIP_BG = Color(0xF2232328)
