package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.github.damontecres.wholphin.data.filter.DefaultTvFilterOptions
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.LibraryKind
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.components.CollectionFolderView
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.GenreCardGrid
import com.github.damontecres.wholphin.ui.components.LibraryShelfTab
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.RecommendedTvShow
import com.github.damontecres.wholphin.ui.components.SHELVES_SEASONS
import com.github.damontecres.wholphin.ui.components.StudioCardGrid
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.TabbedPage
import com.github.damontecres.wholphin.ui.components.ViewOptionsPoster
import com.github.damontecres.wholphin.ui.data.SeriesSortOptions
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.util.StringStringProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import javax.inject.Inject

@HiltViewModel
class CollectionFolderTvViewModel
    @Inject
    constructor(
        val navigationManager: NavigationManager,
    ) : ViewModel()

/** Benflix: the tabs a shows library can have */
private enum class TvTab(
    val title: String,
) {
    HOME("Home"),
    ALL("All"),
    DUBBED("Dubbed"),
    SUBBED("Subbed"),
    SEASONS("Seasons"),
    GENRES("Genres"),
    STUDIOS("Studios"),
}

@Composable
fun CollectionFolderTv(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: CollectionFolderTvViewModel = hiltViewModel(),
) {
    // Benflix: anime gets Dubbed, Subbed and Seasons tabs; wait the moment it takes to tell
    val kind = rememberLibraryKind(destination.itemId)
    if (kind == null) {
        LoadingPage(modifier)
        return
    }
    val tabIds =
        remember(kind) {
            if (kind == LibraryKind.ANIME_SHOWS) {
                listOf(TvTab.HOME, TvTab.ALL, TvTab.DUBBED, TvTab.SUBBED, TvTab.SEASONS, TvTab.GENRES, TvTab.STUDIOS)
            } else {
                listOf(TvTab.HOME, TvTab.ALL, TvTab.GENRES, TvTab.STUDIOS)
            }
        }
    val tabs = remember(tabIds) { tabIds.map { TabDetails(StringStringProvider(it.title)) } }
    val onClickItem =
        remember {
            { position: Int, item: BaseItem ->
                viewModel.navigationManager.navigateTo(item.destination())
            }
        }

    var showHeader by rememberSaveable { mutableStateOf(true) }

    TabbedPage(
        itemId = destination.itemId.toString(),
        tabs = tabs,
        modifier = modifier,
        showTabs = showHeader,
    ) { tabIndex, tabDetails ->
        val contentModifier =
            Modifier
                .fillMaxSize()
                .focusRequester(tabDetails.contentFocusRequester)
        when (tabIds.getOrNull(tabIndex)) {
            TvTab.HOME -> {
                RecommendedTvShow(
                    preferences = preferences,
                    parentId = destination.itemId,
                    onFocusPosition = { pos ->
                        showHeader = pos.row < 1
                    },
                    modifier = contentModifier,
                )
            }

            TvTab.ALL -> {
                CollectionFolderView(
                    preferences = preferences,
                    itemId = destination.itemId,
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    includeItemTypes = listOf(BaseItemKind.SERIES),
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = SeriesSortOptions,
                    filterOptions = DefaultTvFilterOptions,
                    defaultViewOptions = ViewOptionsPoster,
                    modifier = contentModifier,
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    onClickItem = onClickItem,
                    playEnabled = false,
                    focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                )
            }

            TvTab.DUBBED, TvTab.SUBBED -> {
                AnimeDubTab(
                    parentId = destination.itemId,
                    kind = kind,
                    dubbed = tabIds[tabIndex] == TvTab.DUBBED,
                    modifier = contentModifier,
                )
            }

            TvTab.SEASONS -> {
                LibraryShelfTab(
                    preferences = preferences,
                    parentId = destination.itemId,
                    shelfSet = SHELVES_SEASONS,
                    suggestionsType = BaseItemKind.SERIES,
                    onFocusPosition = { pos -> showHeader = pos.row < 1 },
                    modifier = contentModifier,
                )
            }

            TvTab.GENRES -> {
                GenreCardGrid(
                    itemId = destination.itemId,
                    includeItemTypes = listOf(BaseItemKind.SERIES),
                    collectionType = CollectionType.TVSHOWS,
                    modifier = contentModifier,
                )
            }

            TvTab.STUDIOS -> {
                StudioCardGrid(
                    itemId = destination.itemId,
                    includeItemTypes = listOf(BaseItemKind.SERIES),
                    modifier = contentModifier,
                )
            }

            null -> {
                ErrorMessage("Invalid tab index $tabIndex", null)
            }
        }
    }
}
