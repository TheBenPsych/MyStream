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

/** A door's "Browse by" page: Genres, Collections, Studios / Networks, or everything */
@Composable
fun DoorBrowsePage(
    destination: Destination.DoorBrowse,
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: DoorViewModel = hiltViewModel(key = "browse-${destination.door}-${destination.what}"),
) {
    val door = BenflixDoor.entries.firstOrNull { it.name == destination.door } ?: BenflixDoor.MOVIES
    val anime = door == BenflixDoor.ANIME
    val movies = door == BenflixDoor.MOVIES
    when (destination.what) {
        DoorViewModel.BROWSE_ALL -> {
            AllTitlesPage(door, movies, viewModel, modifier)
            return
        }

        // Glowfin Anime page: its Shows and Movies tabs
        DoorViewModel.BROWSE_ANIME_SHOWS -> {
            AllTitlesPage(door, false, viewModel, modifier)
            return
        }

        DoorViewModel.BROWSE_ANIME_MOVIES -> {
            AllTitlesPage(door, true, viewModel, modifier)
            return
        }

        // Ben 2026-11-18: every movie box set, one card each (opens to its movies)
        DoorViewModel.BROWSE_COLLECTIONS -> {
            BoxSetsPage(door, viewModel, modifier)
            return
        }
    }
    var library by remember { mutableStateOf<Library?>(null) }
    var looked by remember { mutableStateOf(false) }
    LaunchedEffect(door) {
        library = if (anime) viewModel.animeLibrary() else viewModel.mainLibrary(door)
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
            DoorViewModel.BROWSE_GENRES -> if (anime) "Genres and tags" else "Genres"
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
                    includeItemTypes = if (anime) listOf(BaseItemKind.SERIES, BaseItemKind.MOVIE) else listOf(type),
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

/**
 * Benflix redesign: All movies / All shows, in the shared [TitleGrid] (Sort, Filter and the letter jump).
 * Glowfin: the Anime page uses the same grid for its Shows and Movies tabs.
 */
@Composable
private fun AllTitlesPage(
    door: BenflixDoor,
    movies: Boolean,
    viewModel: DoorViewModel,
    modifier: Modifier = Modifier,
) {
    var loaded by remember { mutableStateOf<List<BaseItem>?>(null) }
    LaunchedEffect(door, movies) { loaded = viewModel.everything(door, movies) }
    val items = loaded ?: return LoadingPage(modifier.fillMaxSize())
    val anime = door == BenflixDoor.ANIME
    TitleGrid(
        title =
            when {
                anime && movies -> "Anime movies"
                anime -> "Anime shows"
                movies -> "All movies"
                else -> "All shows"
            },
        items = items,
        animeIds = emptySet(),
        movies = movies,
        rememberKey = viewModel.gridKey(door, if (anime && movies) "allmovies" else "all"),
        sorts =
            if (movies) {
                listOf(TitleSort.NEWEST, TitleSort.A_TO_Z, TitleSort.YEAR, TitleSort.SCORE, TitleSort.LENGTH)
            } else {
                listOf(TitleSort.NEWEST_EPISODE, TitleSort.NEWEST, TitleSort.A_TO_Z, TitleSort.YEAR, TitleSort.SCORE)
            },
        onClick = { viewModel.navigationManager.navigateTo(it.destination()) },
        modifier = modifier,
        // Ben 2026-11-18: Anime's Shows and Movies tabs use the same big cards as its For you rows
        bigCards = anime,
    )
}

/** Movies > Collections: all of Tower's box sets in the shared [TitleGrid], one card per box set */
@Composable
private fun BoxSetsPage(
    door: BenflixDoor,
    viewModel: DoorViewModel,
    modifier: Modifier = Modifier,
) {
    var loaded by remember { mutableStateOf<List<BaseItem>?>(null) }
    LaunchedEffect(door) { loaded = viewModel.boxSets() }
    val items = loaded ?: return LoadingPage(modifier.fillMaxSize())
    TitleGrid(
        title = "Collections",
        items = items,
        animeIds = emptySet(),
        movies = true,
        rememberKey = viewModel.gridKey(door, "collections"),
        sorts = listOf(TitleSort.A_TO_Z, TitleSort.NEWEST, TitleSort.YEAR),
        onClick = { viewModel.navigationManager.navigateTo(it.destination()) },
        modifier = modifier,
        noun = "collection" to "collections",
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
    val movies = door == BenflixDoor.MOVIES || destination.key == DoorViewModel.ANIME_MOVIES
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
