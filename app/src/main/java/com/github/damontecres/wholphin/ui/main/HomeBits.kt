package com.github.damontecres.wholphin.ui.main

import androidx.compose.ui.focus.FocusRequester
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay

/** Puts the remote on the first thing on Home */
suspend fun focusPlay(focus: FocusRequester) {
    repeat(FOCUS_TRIES) {
        if (focus.tryRequestFocus()) return
        delay(FOCUS_RETRY_MS)
    }
}

private const val FOCUS_TRIES = 10
private const val FOCUS_RETRY_MS = 60L
