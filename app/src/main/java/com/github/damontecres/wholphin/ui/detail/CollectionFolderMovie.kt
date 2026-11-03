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
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.LibraryKind
import com.github.damontecres.wholphin.ui.components.CollectionFolderView
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.GenreCardGrid
import com.github.damontecres.wholphin.ui.components.LibraryShelfTab
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.RecommendedMovie
import com.github.damontecres.wholphin.ui.components.SHELVES_DECADES
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.TabbedPage
import com.github.damontecres.wholphin.ui.components.ViewOptionsPoster
import com.github.damontecres.wholphin.ui.data.MovieSortOptions
import com.github.damontecres.wholphin.ui.data.VideoSortOptions
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.preferences.PreferencesViewModel
import com.github.damontecres.wholphin.ui.util.StringStringProvider
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType

/** Benflix: the tabs a movies library can have */
private enum class MovieTab(
    val title: String,
) {
    HOME("Home"),
    ALL("All"),
    DUBBED("Dubbed"),
    SUBBED("Subbed"),
    DECADES("Decades"),
    COLLECTIONS("Collections"),
    GENRES("Genres"),
}

@Composable
fun CollectionFolderMovie(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    preferencesViewModel: PreferencesViewModel = hiltViewModel(),
) {
    // Benflix: each kind of movie library gets its own tabs; wait the moment it takes to tell
    val kind = rememberLibraryKind(destination.itemId)
    if (kind == null) {
        LoadingPage(modifier)
        return
    }
    val tabIds =
        remember(kind) {
            when (kind) {
                LibraryKind.ANIME_MOVIES -> {
                    listOf(MovieTab.HOME, MovieTab.ALL, MovieTab.DUBBED, MovieTab.SUBBED, MovieTab.COLLECTIONS, MovieTab.GENRES)
                }

                LibraryKind.STANDUP -> {
                    listOf(MovieTab.HOME, MovieTab.ALL, MovieTab.GENRES)
                }

                else -> {
                    listOf(MovieTab.HOME, MovieTab.ALL, MovieTab.DECADES, MovieTab.COLLECTIONS, MovieTab.GENRES)
                }
            }
        }
    val tabs = remember(tabIds) { tabIds.map { TabDetails(StringStringProvider(it.title)) } }

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
            MovieTab.HOME -> {
                RecommendedMovie(
                    preferences = preferences,
                    parentId = destination.itemId,
                    onFocusPosition = { pos ->
                        showHeader = pos.row < 1
                    },
                    modifier = contentModifier,
                )
            }

            MovieTab.ALL -> {
                CollectionFolderView(
                    preferences = preferences,
                    onClickItem = { _, item ->
                        preferencesViewModel.navigationManager.navigateTo(item.destination())
                    },
                    itemId = destination.itemId,
                    viewModelKey = "${destination.itemId}_library",
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = MovieSortOptions,
                    defaultViewOptions = ViewOptionsPoster,
                    modifier = contentModifier,
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = true,
                    focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                )
            }

            MovieTab.DUBBED, MovieTab.SUBBED -> {
                AnimeDubTab(
                    parentId = destination.itemId,
                    kind = kind,
                    dubbed = tabIds[tabIndex] == MovieTab.DUBBED,
                    modifier = contentModifier,
                )
            }

            MovieTab.DECADES -> {
                LibraryShelfTab(
                    preferences = preferences,
                    parentId = destination.itemId,
                    shelfSet = SHELVES_DECADES,
                    suggestionsType = BaseItemKind.MOVIE,
                    onFocusPosition = { pos -> showHeader = pos.row < 1 },
                    modifier = contentModifier,
                )
            }

            MovieTab.COLLECTIONS -> {
                CollectionFolderView(
                    preferences = preferences,
                    onClickItem = { _, item ->
                        preferencesViewModel.navigationManager.navigateTo(item.destination())
                    },
                    itemId = destination.itemId,
                    viewModelKey = "${destination.itemId}_collection",
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    includeItemTypes = listOf(BaseItemKind.BOX_SET),
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = VideoSortOptions,
                    defaultViewOptions = ViewOptionsPoster,
                    modifier = contentModifier,
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = false,
                    focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                )
            }

            MovieTab.GENRES -> {
                GenreCardGrid(
                    itemId = destination.itemId,
                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                    collectionType = CollectionType.MOVIES,
                    modifier = contentModifier,
                )
            }

            null -> {
                ErrorMessage("Invalid tab index $tabIndex", null)
            }
        }
    }
}
