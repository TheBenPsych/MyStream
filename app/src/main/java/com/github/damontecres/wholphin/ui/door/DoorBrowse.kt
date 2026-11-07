package com.github.damontecres.wholphin.ui.door

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.CollectionFolderView
import com.github.damontecres.wholphin.ui.components.GenreCardGrid
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.StudioCardGrid
import com.github.damontecres.wholphin.ui.components.ViewOptionsPoster
import com.github.damontecres.wholphin.ui.data.BoxSetSortOptions
import com.github.damontecres.wholphin.ui.data.MovieSortOptions
import com.github.damontecres.wholphin.ui.data.SeriesSortOptions
import com.github.damontecres.wholphin.ui.main.settings.Library
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType

/** A door's "Browse by" page: Genres, Collections, Studios / Networks, or everything */
@Composable
fun DoorBrowsePage(
    destination: Destination.DoorBrowse,
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: DoorViewModel = hiltViewModel(key = "browse-${destination.door}-${destination.what}"),
) {
    val door = BenflixDoor.entries.firstOrNull { it.name == destination.door } ?: BenflixDoor.MOVIES
    val movies = door == BenflixDoor.MOVIES
    var library by remember { mutableStateOf<Library?>(null) }
    var looked by remember { mutableStateOf(false) }
    LaunchedEffect(door) {
        library = viewModel.mainLibrary(door)
        looked = true
    }
    val lib = library
    if (lib == null) {
        if (looked) {
            Text("Nothing here yet", color = BenflixColors.Muted, fontSize = 16.sp, modifier = modifier.padding(24.dp))
        } else {
            LoadingPage(modifier)
        }
        return
    }
    val type = if (movies) BaseItemKind.MOVIE else BaseItemKind.SERIES
    val collectionType = if (movies) CollectionType.MOVIES else CollectionType.TVSHOWS
    val title =
        when (destination.what) {
            DoorViewModel.BROWSE_GENRES -> "Genres"
            DoorViewModel.BROWSE_COLLECTIONS -> "Collections"
            DoorViewModel.BROWSE_STUDIOS -> if (movies) "Studios" else "Networks"
            else -> if (movies) "All movies" else "All shows"
        }
    Column(modifier = modifier.fillMaxSize()) {
        PageTitle(title)
        val content = Modifier.fillMaxSize()
        when (destination.what) {
            DoorViewModel.BROWSE_GENRES -> {
                GenreCardGrid(
                    itemId = lib.itemId,
                    includeItemTypes = listOf(type),
                    collectionType = collectionType,
                    modifier = content,
                )
            }

            DoorViewModel.BROWSE_STUDIOS -> {
                StudioCardGrid(itemId = lib.itemId, includeItemTypes = listOf(type), modifier = content)
            }

            DoorViewModel.BROWSE_COLLECTIONS -> {
                CollectionFolderView(
                    preferences = preferences,
                    onClickItem = { _, item -> viewModel.navigationManager.navigateTo(item.destination()) },
                    itemId = lib.itemId,
                    viewModelKey = "${lib.itemId}_door_collections",
                    initialFilter = CollectionFolderFilter(filter = GetItemsFilter(includeItemTypes = listOf(BaseItemKind.BOX_SET))),
                    showTitle = false,
                    recursive = true,
                    sortOptions = BoxSetSortOptions,
                    defaultViewOptions = ViewOptionsPoster,
                    playEnabled = false,
                    modifier = content,
                )
            }

            else -> {
                CollectionFolderView(
                    preferences = preferences,
                    onClickItem = { _, item -> viewModel.navigationManager.navigateTo(item.destination()) },
                    itemId = lib.itemId,
                    viewModelKey = "${lib.itemId}_door_all",
                    initialFilter = CollectionFolderFilter(filter = GetItemsFilter(includeItemTypes = listOf(type))),
                    showTitle = false,
                    recursive = true,
                    sortOptions = if (movies) MovieSortOptions else SeriesSortOptions,
                    defaultViewOptions = ViewOptionsPoster,
                    playEnabled = movies,
                    modifier = content,
                )
            }
        }
    }
}

/** A door row's "See all": every title in that row as a grid of posters */
@Composable
fun DoorRowGridPage(
    destination: Destination.DoorRowGrid,
    modifier: Modifier = Modifier,
    viewModel: DoorViewModel = hiltViewModel(key = "grid-${destination.door}-${destination.key}"),
) {
    val door = BenflixDoor.entries.firstOrNull { it.name == destination.door } ?: BenflixDoor.MOVIES
    var items by remember { mutableStateOf<List<BaseItem>?>(null) }
    LaunchedEffect(destination) { items = viewModel.fullRow(door, destination.key).distinctBy { it.id } }
    val isTv = rememberIsTv()
    val first = remember { FocusRequester() }
    val list = items
    Column(modifier = modifier.fillMaxSize()) {
        PageTitle(destination.title)
        when {
            list == null -> {
                LoadingPage(Modifier.fillMaxSize())
            }

            list.isEmpty() -> {
                Text("Nothing here yet", color = BenflixColors.Muted, fontSize = 16.sp, modifier = Modifier.padding(8.dp))
            }

            else -> {
                LaunchedEffect(Unit) { if (isTv) first.tryRequestFocus() }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val columns = if (isTv) 8 else ((maxWidth / 110.dp).toInt()).coerceIn(3, 8)
                    val width = (maxWidth - 12.dp * (columns - 1) - 8.dp) / columns
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 40.dp, end = 8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(list, key = { _, it -> it.id.toString() }) { index, item ->
                            Box(contentAlignment = Alignment.TopCenter) {
                                DoorPoster(
                                    item = item,
                                    width = width,
                                    showName = !isTv,
                                    onClick = { viewModel.navigationManager.navigateTo(item.destination()) },
                                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageTitle(title: String) {
    Text(
        text = title,
        color = BenflixColors.Text,
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 14.dp),
    )
}
