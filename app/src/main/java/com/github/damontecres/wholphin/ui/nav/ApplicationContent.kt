package com.github.damontecres.wholphin.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.rememberDrawerState
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.preferences.BackdropStyle
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.launchIO
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// Top scrim configuration for text readability (clock, season tabs)
const val TOP_SCRIM_ALPHA = 0.55f
const val TOP_SCRIM_END_FRACTION = 0.25f // Fraction of backdrop image height

@HiltViewModel
class ApplicationContentViewModel
    @Inject
    constructor(
        val backdropService: BackdropService,
    ) : ViewModel() {
        fun clearBackdrop() {
            viewModelScope.launchIO { backdropService.clearBackdrop() }
        }
    }

/**
 * This is generally the root composable of the of the app
 *
 * Here the navigation backstack is used and pages are rendered in the nav drawer or full screen
 */
@Composable
fun ApplicationContent(
    server: JellyfinServer,
    user: JellyfinUser,
    navigationManager: NavigationManager,
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    enableTopScrim: Boolean = true,
    viewModel: ApplicationContentViewModel = hiltViewModel(),
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    Box(
        modifier = modifier,
    ) {
        // Benflix redesign: no coloured glow anywhere (the calm dark tray); pictures only.
        // Only "none" turns the pictures off.
        val backdropStyle =
            preferences.appPreferences.interfacePreferences.backdropStyle.let {
                if (it == BackdropStyle.BACKDROP_NONE) it else BackdropStyle.BACKDROP_IMAGE_ONLY
            }
        // Benflix on phones: Home already shows its banner as a card, so the screen behind stays plain black.
        // A big picture behind it doubled the banner and made the logo and labels hard to read.
        val isTv =
            com.github.damontecres.wholphin.ui.touch
                .rememberIsTv()
        val top = navigationManager.backStack.lastOrNull()
        val onPhoneHome = !isTv && top is Destination.Home
        // Movie and show pages draw their own picture; a second one here doubled it behind the top bar
        if (!onPhoneHome && top?.drawsOwnPicture() != true) {
            Backdrop(
                drawerIsOpen = drawerState.isOpen,
                backdropStyle = backdropStyle,
                enableTopScrim = enableTopScrim,
                viewModel = viewModel,
            )
        }
        val navDrawerListState = rememberLazyListState()
        NavDisplay(
            backStack = navigationManager.backStack,
            onBack = { navigationManager.goBack() },
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider = { key ->
                key as Destination
                val contentKey = "${key}_${server?.id}_${user?.id}"
                NavEntry(key, contentKey = contentKey) {
                    if (key.fullScreen) {
                        DestinationContent(
                            destination = key,
                            preferences = preferences,
                            onClearBackdrop = viewModel::clearBackdrop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (user != null && server != null) {
                        NavDrawer(
                            destination = key,
                            preferences = preferences,
                            user = user,
                            server = server,
                            drawerState = drawerState,
                            navDrawerListState = navDrawerListState,
                            onClearBackdrop = viewModel::clearBackdrop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        ErrorMessage("Trying to go to $key without a user logged in", null)
                    }
                }
            },
        )
        // Picking a door on Home: its colour fills the screen and fades into the door's page
        com.github.damontecres.wholphin.ui.main
            .DoorWipeOverlay()
        // Live TV "Remind me" notes, anywhere in the app
        com.github.damontecres.wholphin.ui.main.ChannelReminderNote(
            navigationManager = navigationManager,
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd),
        )
    }
}
