package com.github.damontecres.wholphin.ui.setup

import androidx.compose.ui.focus.FocusRequester
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay

/**
 * Focus a button that may not be on screen yet (for example inside a pop-up that is still opening).
 * Tries for about half a second, so the first OK press on the remote presses the button instead of only selecting it.
 */
suspend fun FocusRequester.requestFocusWhenReady() {
    repeat(10) {
        if (tryRequestFocus()) return
        delay(50)
    }
}
