package com.github.damontecres.wholphin.ui.nav

import android.view.KeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import com.github.damontecres.wholphin.data.model.BaseItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Benflix Idea Box 24, "Getting around": remote buttons that did nothing outside the player.
 * - Hold Back (about a second) from anywhere: straight to Home.
 * - Play/Pause on a poster or episode: starts it right away, from where you left off.
 * - The remote's Search button: Benflix search (not on Kids, which has no search).
 * - Channel Up/Down (or Page Up/Down): a screen of rows up or down. Rewind/Fast-forward: 7 posters along a row.
 * The player, games and Live TV are full-screen pages or keep their own meaning for these buttons.
 */
object RemoteShortcuts {
    /** The poster or episode the remote is on now (set by the cards themselves) */
    @Volatile var focused: BaseItem? = null

    /** After "hold Back" went Home, the rest of that press (more repeats, letting go) is ignored */
    @Volatile private var swallowBack = false

    private var jumping = false

    /**
     * @return true when the button was used here
     */
    fun onKey(
        native: KeyEvent,
        destination: Destination,
        kids: Boolean,
        focusManager: FocusManager,
        scope: CoroutineScope,
        goHome: () -> Unit,
        openSearch: () -> Unit,
        play: (BaseItem) -> Unit,
    ): Boolean {
        val down = native.action == KeyEvent.ACTION_DOWN
        if (native.keyCode == KeyEvent.KEYCODE_BACK) {
            if (swallowBack) {
                if (native.action == KeyEvent.ACTION_UP) swallowBack = false
                return true
            }
            if (down && native.repeatCount == BACK_HOLD_REPEATS && destination !is Destination.Home) {
                swallowBack = true
                goHome()
                return true
            }
            return false
        }
        if (!down) return false
        val liveTv = destination is Destination.Channels
        return when (native.keyCode) {
            KeyEvent.KEYCODE_SEARCH -> {
                if (kids) return false
                if (native.repeatCount == 0 && destination !is Destination.Search) openSearch()
                true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY -> {
                val item = focused ?: return false
                if (native.repeatCount > 0) return true
                play(item)
                true
            }

            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                if (liveTv) return false
                jump(focusManager, scope, FocusDirection.Down, ROWS_PER_PAGE)
                true
            }

            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                if (liveTv) return false
                jump(focusManager, scope, FocusDirection.Up, ROWS_PER_PAGE)
                true
            }

            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                jump(focusManager, scope, FocusDirection.Right, POSTERS_PER_PAGE)
                true
            }

            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                jump(focusManager, scope, FocusDirection.Left, POSTERS_PER_PAGE)
                true
            }

            else -> {
                false
            }
        }
    }

    /** Moves one step at a time (so long rows have time to load the next posters), stopping at the end */
    private fun jump(
        focusManager: FocusManager,
        scope: CoroutineScope,
        direction: FocusDirection,
        steps: Int,
    ) {
        if (jumping) return
        jumping = true
        scope.launch {
            try {
                for (i in 0 until steps) {
                    if (!focusManager.moveFocus(direction)) break
                    delay(STEP_MS)
                }
            } finally {
                jumping = false
            }
        }
    }

    /** Key repeats come about every 50 ms after the first 0.4 s, so this is a little over a second */
    private const val BACK_HOLD_REPEATS = 14
    private const val ROWS_PER_PAGE = 3
    private const val POSTERS_PER_PAGE = 7
    private const val STEP_MS = 45L
}

/** Lets the remote's Play button start [item] while this card has the focus */
fun Modifier.playButtonPlays(item: BaseItem?): Modifier =
    if (item == null) {
        this
    } else {
        // The "where am I" line on phones names title pages from the posters on the way there
        WhereAmI.remember(item)
        this.onFocusChanged {
            if (it.hasFocus) {
                RemoteShortcuts.focused = item
            } else if (RemoteShortcuts.focused?.id == item.id) {
                RemoteShortcuts.focused = null
            }
        }
    }
