package com.github.damontecres.wholphin.ui.nav

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusRequesterModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.node.ModifierNodeElement
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.util.LastRemoteKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Glowfin speed + navigation check (Ben 2026-11-27): Back puts the remote on the card you opened.
 * Opening a page notes the card the remote was on; coming Back to that page, the card with the same key
 * takes the focus as soon as it is on screen again. Pressing any button first cancels it, and a page that
 * can't find the card lands where it always did (see [settle]).
 */
object ReturnSpot {
    /** The card the remote is on now (set by the cards themselves) */
    @Volatile var focusedKey: String? = null

    /** Page (and how deep it sits in the back stack) to the card that was left there */
    private val left = HashMap<Pair<Int, Destination>, String>()

    @Volatile private var wanted: String? = null

    @Volatile private var wantedAt = 0L

    @Volatile private var claimed = false

    /** [page] (at [depth] in the back stack) is about to be covered by [opening] */
    fun leaving(
        page: Destination?,
        depth: Int,
        opening: Destination,
    ) {
        wanted = null
        if (page == null) return
        val key = focusedKey
        // Touch screens have no cursor to put back, and after playing something the page's own landing is right
        val player = opening is Destination.Playback || opening is Destination.PlaybackList
        synchronized(left) {
            if (key != null && !player && LastRemoteKey.pressedRecently(REMOTE_MS)) {
                left[depth to page] = key
            } else {
                left.remove(depth to page)
            }
        }
    }

    /** Back was pressed and [page] (at [depth]) is showing again */
    fun backTo(
        page: Destination?,
        depth: Int,
    ) {
        val key = synchronized(left) { if (page == null) null else left.remove(depth to page) }
        claimed = false
        wantedAt = now()
        wanted = key
    }

    /** The back stack was thrown away (Home, the side menu, a new profile) */
    fun forget() {
        wanted = null
        synchronized(left) { left.clear() }
    }

    internal fun wants(key: String): Boolean = wanted == key && now() - wantedAt < FRESH_MS && LastRemoteKey.pressAt <= wantedAt

    internal fun claimedBy(key: String) {
        if (wanted == key) claimed = true
    }

    /** True while a card on the page coming back is still expected to take the focus */
    fun waiting(): Boolean = wanted?.let { !claimed && wants(it) } == true

    /**
     * For a page's own "land on the first thing" step: waits a moment for the card that was left.
     * @return true when that card took the focus, so the page should leave the focus alone
     */
    suspend fun settle(): Boolean {
        var waited = 0L
        while (waiting() && waited < SETTLE_MS) {
            delay(STEP_MS)
            waited += STEP_MS
        }
        return claimed && wanted != null
    }

    /** (plain tests have no Android clock) */
    private fun now(): Long =
        try {
            SystemClock.uptimeMillis()
        } catch (_: RuntimeException) {
            0L
        }

    private const val FRESH_MS = 4_000L
    private const val REMOTE_MS = 5_000L
    private const val SETTLE_MS = 900L
    private const val STEP_MS = 50L
}

/** Back to this page puts the remote on this card again, when it was the one opened. [key] is unique on the page */
fun Modifier.returnHere(key: String): Modifier = this then ReturnHereElement(key, null)

private class ReturnHereElement(
    val key: String,
    val item: BaseItem?,
) : ModifierNodeElement<ReturnHereNode>() {
    override fun create() = ReturnHereNode(key, item)

    override fun update(node: ReturnHereNode) {
        node.key = key
        node.item = item
    }

    override fun equals(other: Any?) = other is ReturnHereElement && other.key == key && other.item === item

    override fun hashCode() = key.hashCode()
}

/** One small node per card: it says which card the remote is on, and takes the focus back after Back */
private class ReturnHereNode(
    var key: String,
    var item: BaseItem?,
) : Modifier.Node(),
    FocusEventModifierNode,
    FocusRequesterModifierNode {
    private var has = false

    override fun onAttach() {
        if (!ReturnSpot.wants(key)) return
        coroutineScope.launch {
            // After the page's own first layout, and again if the page's own landing took the focus away
            delay(FIRST_MS)
            var tries = 0
            while (tries++ < TRIES && ReturnSpot.wants(key)) {
                // The same title can be in two rows: the first one to take it wins
                if (!has && ReturnSpot.focusedKey != key) requestFocus()
                if (has || ReturnSpot.focusedKey == key) ReturnSpot.claimedBy(key)
                delay(AGAIN_MS)
            }
        }
    }

    override fun onFocusEvent(focusState: FocusState) {
        has = focusState.hasFocus
        val item = item
        if (has) {
            ReturnSpot.focusedKey = key
            if (item != null) RemoteShortcuts.focused = item
        } else {
            if (ReturnSpot.focusedKey == key) ReturnSpot.focusedKey = null
            if (item != null && RemoteShortcuts.focused?.id == item.id) RemoteShortcuts.focused = null
        }
    }

    override fun onDetach() {
        if (has) {
            has = false
            if (ReturnSpot.focusedKey == key) ReturnSpot.focusedKey = null
        }
    }

    private companion object {
        const val FIRST_MS = 60L
        const val AGAIN_MS = 150L
        const val TRIES = 7
    }
}

/**
 * Speed + navigation check (Ben 2026-11-27): a row remembers the poster the remote was last on in it, so Up and
 * Down come back to that poster (the first one in a row not visited yet) instead of whichever poster sits under
 * the one you left. The posters report themselves, so moving along a row rebuilds nothing.
 */
class RowSpot(
    private val last: MutableIntState,
) {
    private val cards = HashMap<Int, RowSpotNode>()

    /** Puts the remote on the remembered poster. False when it isn't on screen (the remote then moves as usual) */
    fun land(): Boolean = cards[last.intValue]?.take() == true

    internal fun on(index: Int) {
        last.intValue = index
    }

    internal fun add(
        index: Int,
        node: RowSpotNode,
    ) {
        cards[index] = node
    }

    internal fun remove(
        index: Int,
        node: RowSpotNode,
    ) {
        if (cards[index] === node) cards.remove(index)
    }
}

/** [startIndex] is the poster the row opens on (a row not visited yet lands there) */
@Composable
fun rememberRowSpot(startIndex: Int = 0): RowSpot {
    val last = rememberSaveable { mutableIntStateOf(startIndex) }
    return remember { RowSpot(last) }
}

/** Poster number [index] of a row that keeps a [RowSpot] */
fun Modifier.rowSpot(
    spot: RowSpot,
    index: Int,
): Modifier = this then RowSpotElement(spot, index)

private class RowSpotElement(
    val spot: RowSpot,
    val index: Int,
) : ModifierNodeElement<RowSpotNode>() {
    override fun create() = RowSpotNode(spot, index)

    override fun update(node: RowSpotNode) = node.move(spot, index)

    override fun equals(other: Any?) = other is RowSpotElement && other.spot === spot && other.index == index

    override fun hashCode() = index
}

internal class RowSpotNode(
    private var spot: RowSpot,
    private var index: Int,
) : Modifier.Node(),
    FocusEventModifierNode,
    FocusRequesterModifierNode {
    override fun onAttach() = spot.add(index, this)

    override fun onDetach() = spot.remove(index, this)

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.hasFocus) spot.on(index)
    }

    fun move(
        spot: RowSpot,
        index: Int,
    ) {
        if (isAttached) this.spot.remove(this.index, this)
        this.spot = spot
        this.index = index
        if (isAttached) spot.add(index, this)
    }

    fun take(): Boolean = isAttached && requestFocus()
}

/** Lets the remote's Play button start [item] while this card has the focus (and Back returns to it) */
fun Modifier.playButtonPlays(item: BaseItem?): Modifier =
    if (item == null) {
        this
    } else {
        // The "where am I" line on phones names title pages from the posters on the way there
        WhereAmI.remember(item)
        this then ReturnHereElement(item.id.toString(), item)
    }
