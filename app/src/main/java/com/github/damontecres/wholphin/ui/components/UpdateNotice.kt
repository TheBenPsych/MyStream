package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.window.PopupPositionProvider
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
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

    private val _daysBehind = MutableStateFlow(0)

    /** How many days this TV or phone has been missing updates (0 when up to date) */
    val daysBehind: StateFlow<Int> = _daysBehind

    fun set(
        newVersion: String?,
        showTip: Boolean,
        daysBehind: Int,
    ) {
        _version.value = newVersion
        _tip.value = newVersion != null && showTip
        _daysBehind.value = if (newVersion == null) 0 else daysBehind
    }

    fun hideTip() {
        _tip.value = false
    }

    /** How long the tip bubble stays before it goes away by itself */
    const val TIP_MS = 9_000L

    private val _dotHiddenFor = MutableStateFlow<String?>(null)

    /**
     * The update version "Mark all as seen" hid from the dot on your picture. "Update ready" still shows
     * in the menu; the dot comes back only when an even newer version is out.
     */
    val dotHiddenFor: StateFlow<String?> = _dotHiddenFor

    private fun dotPrefs(context: android.content.Context) =
        context.applicationContext.getSharedPreferences("benflix_dot", android.content.Context.MODE_PRIVATE)

    fun loadDot(context: android.content.Context) {
        _dotHiddenFor.value = dotPrefs(context).getString("update-hidden", null)
    }

    fun hideDot(context: android.content.Context) {
        val v = _version.value ?: return
        dotPrefs(context).edit().putString("update-hidden", v).apply()
        _dotHiddenFor.value = v
    }
}

/** True when the update should light up the dot on your picture (not hidden by "Mark all as seen") */
@Composable
fun rememberUpdateDot(): Boolean {
    val alert = rememberUpdateAlert()
    val version by UpdateNotice.version.collectAsState()
    val hidden by UpdateNotice.dotHiddenFor.collectAsState()
    return alert && version != hidden
}

/** True when the red "!" should show (an update is ready, and this isn't Kids) */
@Composable
fun rememberUpdateAlert(): Boolean {
    val version by UpdateNotice.version.collectAsState()
    val kids by KidsMode.active.collectAsState()
    // Guest can't install updates either, so no "!" for them
    val guest by GuestMode.active.collectAsState()
    return version != null && !kids && !guest
}

/** The little red circle with a white "!" (or a small number, for requests that are here) */
@Composable
fun UpdateBang(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    text: String = "!",
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(size)
                .background(UPDATE_RED, CircleShape),
    ) {
        Text(
            text = text,
            color = Color(0xFF0B0C10),
            fontSize = (size.value * (if (text.length > 1) .56f else .72f)).sp,
            lineHeight = (size.value * .72f).sp,
            fontWeight = FontWeight.Black,
        )
    }
}

/**
 * Benflix: the admin's "something needs you" alert on Home (a new problem report, a nearly full disk).
 * It looks like the update notice: a red "!" on the cogwheel (TV) or My Benflix (phone) and a tip bubble
 * for a few seconds. It only shows while Home is on screen, and never for Kids.
 */
object ServerAlertNotice {
    private val _text = MutableStateFlow<String?>(null)

    /** What the alert says (null = nothing new) */
    val text: StateFlow<String?> = _text

    private val _tip = MutableStateFlow(false)
    val tip: StateFlow<Boolean> = _tip

    fun show(text: String) {
        _text.value = text
        _tip.value = true
    }

    fun hideTip() {
        _tip.value = false
    }

    /** Home went away, or the admin opened the Server page */
    fun clear() {
        _text.value = null
        _tip.value = false
    }
}

/** True when the admin's red "!" for the Server page should show */
@Composable
fun rememberServerAlert(): Boolean {
    val text by ServerAlertNotice.text.collectAsState()
    val kids by KidsMode.active.collectAsState()
    val guest by GuestMode.active.collectAsState()
    return text != null && !kids && !guest
}

/**
 * Benflix: "Your request is here!" (notices redesign, 2026-11-10): each new arrival gets its own 7-second corner
 * card (one after another, oldest first), then a Ready to watch row on Home. No red number any more.
 */
object RequestReadyNotice {
    data class Tip(
        val requestId: Int,
        val title: String,
        val posterUrl: String?,
        /** Its title page (for OK on the corner card) */
        val destination: com.github.damontecres.wholphin.ui.nav.Destination? = null,
    )

    private val _count = MutableStateFlow(0)

    /** How many arrived requests you haven't looked at in My Benflix yet */
    val count: StateFlow<Int> = _count

    private val _tips = MutableStateFlow<List<Tip>>(emptyList())

    /** Bubbles still to show, in order. The first one is on screen. */
    val tips: StateFlow<List<Tip>> = _tips

    /** Called once a bubble has had its turn */
    var onTipShown: (Tip) -> Unit = {}

    fun setCount(count: Int) {
        _count.value = count
    }

    fun addTips(tips: List<Tip>) {
        _tips.value = (_tips.value + tips.filter { t -> _tips.value.none { it.requestId == t.requestId } })
    }

    fun clear() {
        _count.value = 0
        _tips.value = emptyList()
    }

    internal fun tipDone(tip: Tip) {
        _tips.value = _tips.value.filter { it.requestId != tip.requestId }
        onTipShown(tip)
    }

    /** Each bubble fades away by itself after this long */
    const val TIP_MS = 7_000L
}

/** How many request arrivals to show on the red number (0 = none, and always 0 for Kids and Guest) */
@Composable
fun rememberRequestReadyCount(): Int {
    val count by RequestReadyNotice.count.collectAsState()
    val kids by KidsMode.active.collectAsState()
    val guest by GuestMode.active.collectAsState()
    return if (kids || guest) 0 else count
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

// Benflix redesign: the update dot is white with dark words, not red
private val UPDATE_RED = Color(0xFFF2F2F4)
private val TIP_BG = Color(0xF2232328)
