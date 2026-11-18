package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.ui.nav.Destination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Glowfin Help and updates (S1): "After this show" waits until the player closes, then opens Help and
 * updates and starts the install by itself. Moving on to the next episode doesn't count as closing.
 */
object UpdateAfterShow {
    /** Ben pressed "After this show" and the player hasn't closed yet */
    @Volatile
    var pending = false

    /** Help and updates should start the install as soon as it opens */
    @Volatile
    var startWhenOpened = false

    /** Called when a player screen goes away */
    fun playerClosed(navigationManager: NavigationManager) {
        if (!pending) return
        CoroutineScope(Dispatchers.Main).launch {
            // The next episode swaps one player for another; only act once no player is showing
            delay(CHECK_AFTER_MS)
            val top = navigationManager.backStack.lastOrNull()
            if (!pending || top is Destination.Playback || top is Destination.PlaybackList) return@launch
            pending = false
            startWhenOpened = true
            navigationManager.navigateTo(Destination.UpdateApp)
        }
    }

    private const val CHECK_AFTER_MS = 1_500L
}
