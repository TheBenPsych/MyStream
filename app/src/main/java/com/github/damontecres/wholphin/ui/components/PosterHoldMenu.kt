package com.github.damontecres.wholphin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.ServerReportService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Benflix: what holding a poster opens when the page didn't give it a menu of its own (the Movies
 * and Shows doors, search, More like this). Same menu as everywhere else: play, watched, My List,
 * playlists, go to, and for the main profile Change poster, Refresh and Fix match.
 * Deleting stays on the title's own page.
 */
@HiltViewModel
class PosterHoldMenuViewModel
    @Inject
    constructor(
        private val navigationManager: NavigationManager,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val serverRepository: ServerRepository,
        private val serverReportService: ServerReportService,
        userPreferencesService: UserPreferencesService,
    ) : ViewModel(),
        ContextMenuProvider {
        val preferences: StateFlow<UserPreferences?> =
            userPreferencesService.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        override fun isAdministrator(): Boolean = serverRepository.currentUserDto?.policy?.isAdministrator == true

        override fun navigateTo(destination: Destination) {
            navigationManager.navigateTo(destination)
        }

        override fun canDelete(
            item: BaseItem,
            appPreferences: AppPreferences,
        ): Boolean = false

        override fun deleteItem(
            index: Int,
            item: BaseItem,
        ) {
            // Not offered from a held poster
        }

        override fun setWatched(
            position: Int,
            itemId: UUID,
            played: Boolean,
        ) {
            viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
                favoriteWatchManager.setWatched(itemId, played)
            }
        }

        override fun setFavorite(
            position: Int,
            itemId: UUID,
            favorite: Boolean,
        ) {
            viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
                favoriteWatchManager.setFavorite(itemId, favorite)
            }
        }

        override fun sendReportFor(itemId: UUID) = serverReportService.sendMediaReportFor(itemId)
    }

/**
 * Shows the hold menu for [item] each time [timesHeld] goes up. Only composed once a poster has been
 * held, so posters cost nothing extra until then.
 */
@Composable
fun PosterHoldMenu(
    item: BaseItem,
    timesHeld: Int,
    viewModel: PosterHoldMenuViewModel = hiltViewModel(),
) {
    val preferences by viewModel.preferences.collectAsState()
    val prefs = preferences ?: return
    val menu = rememberContextMenu(prefs, viewModel)
    LaunchedEffect(timesHeld) {
        if (timesHeld > 0) menu.showContextMenu(0, item)
    }
    menu.Compose()
}
