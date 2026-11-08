package com.github.damontecres.wholphin.ui.door

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.github.damontecres.wholphin.ui.main.settings.Library
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixType
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import java.util.UUID

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
    if (destination.what == DoorViewModel.BROWSE_ALL) {
        AllTitlesPage(door, viewModel, modifier)
        return
    }
    var library by remember { mutableStateOf<Library?>(null) }
    var looked by remember { mutableStateOf(false) }
    LaunchedEffect(door) {
        library = viewModel.mainLibrary(door)
        looked = true
    }
    val lib = library
    if (lib == null) {
        if (looked) {
            Text("Nothing here yet", color = BenflixColors.Muted, fontSize = BenflixType.body, modifier = modifier.padding(24.dp))
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
            DoorViewModel.BROWSE_STUDIOS -> if (movies) "Studios" else "Networks"
            else -> "Collections"
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

            else -> {
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
        }
    }
}

/** Benflix redesign: All movies / All shows, in the shared [TitleGrid] */
@Composable
private fun AllTitlesPage(
    door: BenflixDoor,
    viewModel: DoorViewModel,
    modifier: Modifier = Modifier,
) {
    val movies = door == BenflixDoor.MOVIES
    var loaded by remember { mutableStateOf<Pair<List<BaseItem>, Set<UUID>>?>(null) }
    LaunchedEffect(door) { loaded = viewModel.everything(door) }
    val (items, animeIds) = loaded ?: return LoadingPage(modifier.fillMaxSize())
    TitleGrid(
        title = if (movies) "All movies" else "All shows",
        items = items,
        animeIds = animeIds,
        movies = movies,
        rememberKey = viewModel.gridKey(door, "all"),
        sorts =
            if (movies) {
                listOf(TitleSort.NEWEST, TitleSort.A_TO_Z, TitleSort.YEAR, TitleSort.SCORE, TitleSort.LENGTH)
            } else {
                listOf(TitleSort.NEWEST_EPISODE, TitleSort.NEWEST, TitleSort.A_TO_Z, TitleSort.YEAR, TitleSort.SCORE)
            },
        onClick = { viewModel.navigationManager.navigateTo(it.destination()) },
        modifier = modifier,
    )
}

/** A door row's "See all" (and My List): every title in that row, in the shared [TitleGrid] */
@Composable
fun DoorRowGridPage(
    destination: Destination.DoorRowGrid,
    modifier: Modifier = Modifier,
    viewModel: DoorViewModel = hiltViewModel(key = "grid-${destination.door}-${destination.key}"),
) {
    val door = BenflixDoor.entries.firstOrNull { it.name == destination.door } ?: BenflixDoor.MOVIES
    val movies = door == BenflixDoor.MOVIES
    var items by remember { mutableStateOf<List<BaseItem>?>(null) }
    LaunchedEffect(destination) { items = viewModel.fullRow(door, destination.key).distinctBy { it.id } }
    val list = items ?: return LoadingPage(modifier.fillMaxSize())
    // Top 10 and "Because you watched" keep their own order first; the rest open on the person's last sort
    val sorts =
        listOf(TitleSort.AS_LISTED) +
            if (movies) {
                listOf(TitleSort.NEWEST, TitleSort.A_TO_Z, TitleSort.YEAR, TitleSort.SCORE, TitleSort.LENGTH)
            } else {
                listOf(TitleSort.NEWEST_EPISODE, TitleSort.NEWEST, TitleSort.A_TO_Z, TitleSort.YEAR, TitleSort.SCORE)
            }
    TitleGrid(
        title = destination.title,
        items = list,
        animeIds = emptySet(),
        movies = movies,
        rememberKey = viewModel.gridKey(door, destination.key),
        sorts = sorts,
        onClick = { viewModel.navigationManager.navigateTo(it.destination()) },
        modifier = modifier,
    )
}

@Composable
private fun PageTitle(title: String) {
    Text(
        text = title,
        color = BenflixColors.Text,
        fontSize = BenflixType.rowName,
        fontWeight = FontWeight.ExtraBold,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 14.dp),
    )
}
