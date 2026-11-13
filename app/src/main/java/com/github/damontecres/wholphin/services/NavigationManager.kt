package com.github.damontecres.wholphin.services

import androidx.navigation3.runtime.NavBackStack
import com.github.damontecres.wholphin.ui.nav.Destination
import org.acra.ACRA
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages navigating between pages and manages the app's back stack
 */
@Singleton
class NavigationManager
    @Inject
    constructor() {
        var backStack: MutableList<Destination> = NavBackStack(Destination.Home())

        /**
         * Go to the specified [com.github.damontecres.wholphin.ui.nav.Destination]
         */
        fun navigateTo(destination: Destination) {
            backStack.add(destination)
            log()
        }

        /**
         * Go to the specified [Destination], but reset the back stack to Home first
         */
        fun navigateToFromDrawer(destination: Destination) {
            goToHome()
            backStack.add(destination)
            log()
        }

        /**
         * Go to the previous page
         */
        fun goBack() {
            synchronized(this) {
                if (backStack.size > 1) {
                    backStack.removeLastOrNull()
                }
            }
            log()
        }

        /** Benflix "where am I" line: back to the page at [index] in the back stack */
        fun goBackTo(index: Int) {
            synchronized(this) {
                while (backStack.size > index + 1 && backStack.size > 1) {
                    backStack.removeLastOrNull()
                }
            }
            log()
        }

        /**
         * Go all the way back to the home page
         */
        fun goToHome() {
            while (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
            if (backStack[0] !is Destination.Home) {
                backStack[0] = Destination.Home()
            }
            log()
        }

        /**
         * Go all the way back to the home page, and reload it from scratch
         */
        fun reloadHome() {
            goToHome()
            val id = (backStack[0] as Destination.Home).id + 1
            backStack[0] = Destination.Home(id)
            log()
        }

        /**
         * Resets the backstack to the specified destination
         */
        fun replace(destination: Destination) {
            while (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
            if (backStack.isEmpty()) {
                backStack.add(0, destination)
            } else {
                backStack[0] = destination
            }
            log()
        }

        /**
         * Benflix: swap the page right under [top] (when it's still on the back stack) for what
         * [change] returns, e.g. so the show page under the player shows the episode on now.
         * Nothing happens when [change] returns null or the same page.
         */
        fun replaceBelow(
            top: Destination,
            change: (Destination) -> Destination?,
        ) {
            synchronized(this) {
                val topIndex = backStack.lastIndexOf(top)
                if (topIndex < 1) return
                val below = backStack[topIndex - 1]
                val replacement = change(below) ?: return
                if (replacement == below) return
                Timber.i("Page under the player now: %s", replacement)
                backStack[topIndex - 1] = replacement
            }
        }

        private fun log() {
            val dest = backStack.lastOrNull().toString()
            Timber.i("Current Destination: %s", dest)
            ACRA.errorReporter.putCustomData("destination", dest)
        }
    }
