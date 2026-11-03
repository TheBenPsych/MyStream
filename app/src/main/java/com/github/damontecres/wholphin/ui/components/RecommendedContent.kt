package com.github.damontecres.wholphin.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.LibraryHomeService
import com.github.damontecres.wholphin.services.LibraryKind
import com.github.damontecres.wholphin.services.LibraryShelf
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.ServerReportService
import com.github.damontecres.wholphin.services.SuggestionService
import com.github.damontecres.wholphin.services.SuggestionsResource
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.detail.music.addToQueue
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.main.FeaturedItem
import com.github.damontecres.wholphin.ui.main.HomePageContent
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.playback.playable
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.ui.util.ResStringProvider
import com.github.damontecres.wholphin.ui.util.StringStringProvider
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.RequestHandler
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID

@HiltViewModel(assistedFactory = RecommendedViewModel.Factory::class)
class RecommendedViewModel
    @AssistedInject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
        private val userPreferencesService: UserPreferencesService,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val musicService: MusicService,
        private val backdropService: BackdropService,
        private val mediaManagementService: MediaManagementService,
        private val suggestionService: SuggestionService,
        val serverReportService: ServerReportService,
        private val libraryHome: LibraryHomeService,
        @Assisted private val parentId: UUID,
        @Assisted private val suggestionsType: BaseItemKind,
        @Assisted private val recommendedRows: List<RecommendedRow<*>>,
        @Assisted private val viewOptions: HomeRowViewOptions,
        @Assisted private val shelfSet: String,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                parentId: UUID,
                suggestionsType: BaseItemKind,
                recommendedRows: List<RecommendedRow<*>>,
                viewOptions: HomeRowViewOptions,
                shelfSet: String,
            ): RecommendedViewModel
        }

        private val _state = MutableStateFlow(RecommendedState())
        val state: StateFlow<RecommendedState> = _state

        /** Benflix: the library's own rows (mini Home), and whether the Suggestions row comes last */
        private var shelves: List<LibraryShelf> = emptyList()
        private val withSuggestions: Boolean get() = shelfSet == SHELVES_HOME
        private var kind: LibraryKind = LibraryKind.OTHER

        fun init() {
            viewModelScope.launchDefault {
                val limit =
                    userPreferencesService.flow
                        .first()
                        .appPreferences.homePagePreferences.maxItemsPerRow
                kind =
                    try {
                        libraryHome.kindOf(parentId)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not tell what kind of library %s is", parentId)
                        LibraryKind.OTHER
                    }
                shelves =
                    when (shelfSet) {
                        SHELVES_HOME -> libraryHome.homeShelves(parentId, kind)
                        SHELVES_SEASONS -> libraryHome.seasonShelves(parentId)
                        SHELVES_DECADES -> libraryHome.decadeShelves(parentId)
                        SHELVES_WEEK -> libraryHome.weekShelves(parentId)
                        else -> emptyList()
                    }
                if (shelfSet == SHELVES_HOME && kind != LibraryKind.OTHER) loadFeatured()
                _state.update {
                    it.copy(
                        loading = LoadingState.Loading,
                        rows =
                            recommendedRows.map { HomeRowLoadingState.Loading(ResStringProvider(it.title)) } +
                                shelves.map { HomeRowLoadingState.Loading(StringStringProvider(it.title)) } +
                                if (withSuggestions) {
                                    listOf(
                                        HomeRowLoadingState.Loading(ResStringProvider(R.string.suggestions)),
                                    )
                                } else {
                                    emptyList()
                                },
                    )
                }
                val jobs =
                    recommendedRows.mapIndexed { index, row ->
                        val title = ResStringProvider(row.title)
                        viewModelScope.launchIO {
                            val result =
                                try {
                                    val items = execute(row, limit)
                                    HomeRowLoadingState.Success(
                                        title,
                                        items,
                                        viewOptions,
                                        showViewMore = items.size >= limit,
                                    )
                                } catch (ex: Exception) {
                                    Timber.e(ex, "Exception fetching %s", title)
                                    HomeRowLoadingState.Error(title, null, ex)
                                }
                            _state.update {
                                it.copy(
                                    rows =
                                        it.rows.toMutableList().apply {
                                            set(index, result)
                                        },
                                )
                            }
                        }
                    } +
                        shelves.mapIndexed { shelfIndex, shelf ->
                            val index = recommendedRows.size + shelfIndex
                            viewModelScope.launchIO {
                                val items =
                                    try {
                                        shelf.load(shelf)
                                    } catch (ex: CancellationException) {
                                        throw ex
                                    } catch (ex: Exception) {
                                        // A row that can't load is left out instead of showing an error
                                        Timber.w(ex, "Could not load the %s row", shelf.title)
                                        emptyList()
                                    }
                                val result =
                                    HomeRowLoadingState.Success(
                                        StringStringProvider(shelf.title),
                                        items,
                                        shelf.viewOptions,
                                        showViewMore = items.size >= LibraryHomeService.ROW,
                                    )
                                _state.update {
                                    it.copy(
                                        rows =
                                            it.rows.toMutableList().apply {
                                                set(index, result)
                                            },
                                    )
                                }
                            }
                        }
                if (withSuggestions) fetchSuggestions()
                jobs.forEachIndexed { index, job ->
                    job.join()
                    val row = state.value.rows[index]
                    if (row is HomeRowLoadingState.Success && row.items.isNotEmpty()) {
                        // Report loading success once the first non-empty row is ready
                        _state.update { it.copy(loading = LoadingState.Success) }
                    }
                }
                // Benflix: everything is in, even if every row came back empty
                _state.update { it.copy(loading = LoadingState.Success) }
            }
        }

        /**
         * Benflix: coming back (e.g. from the player), reload just the rows that depend on what was
         * watched (Continue watching, Next up, Finish the season, Played lately). Random rows stay put.
         */
        fun refreshProgressRows() {
            if (state.value.loading != LoadingState.Success) return
            viewModelScope.launchIO {
                val limit =
                    userPreferencesService.flow
                        .first()
                        .appPreferences.homePagePreferences.maxItemsPerRow
                recommendedRows.forEachIndexed { index, row ->
                    if (row.title !in PROGRESS_ROW_TITLES) return@forEachIndexed
                    launchIO {
                        val title = ResStringProvider(row.title)
                        val items =
                            try {
                                execute(row, limit)
                            } catch (ex: CancellationException) {
                                throw ex
                            } catch (ex: Exception) {
                                Timber.w(ex, "Could not refresh %s", title)
                                return@launchIO
                            }
                        setRow(index, HomeRowLoadingState.Success(title, items, viewOptions, showViewMore = items.size >= limit))
                    }
                }
                shelves.forEachIndexed { shelfIndex, shelf ->
                    if (shelf.title !in PROGRESS_SHELF_TITLES) return@forEachIndexed
                    launchIO {
                        val items =
                            try {
                                shelf.load(shelf)
                            } catch (ex: CancellationException) {
                                throw ex
                            } catch (ex: Exception) {
                                Timber.w(ex, "Could not refresh the %s row", shelf.title)
                                return@launchIO
                            }
                        setRow(
                            recommendedRows.size + shelfIndex,
                            HomeRowLoadingState.Success(
                                StringStringProvider(shelf.title),
                                items,
                                shelf.viewOptions,
                                showViewMore = items.size >= LibraryHomeService.ROW,
                            ),
                        )
                    }
                }
            }
        }

        private fun setRow(
            index: Int,
            row: HomeRowLoadingState,
        ) {
            _state.update {
                if (index !in it.rows.indices) {
                    it
                } else {
                    it.copy(rows = it.rows.toMutableList().apply { set(index, row) })
                }
            }
        }

        /** Benflix: the banner at the top of the mini Home */
        private fun loadFeatured() {
            viewModelScope.launchIO {
                try {
                    val featured = libraryHome.featured(parentId, kind)
                    _state.update { it.copy(featured = featured) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not load the banner for %s", parentId)
                }
            }
        }

        /** Opens a tile, or for the music rows that start music (mixes, decades), starts it */
        fun clickItem(
            position: RowColumn,
            item: BaseItem,
        ) {
            val shelf = shelves.getOrNull(position.row - recommendedRows.size)
            val action = shelf?.onClick
            if (action == null) {
                navigationManager.navigateTo(item.destination())
                return
            }
            viewModelScope.launchIO {
                try {
                    action(item)
                    withContext(WholphinDispatchers.Main) { navigationManager.navigateTo(Destination.NowPlaying) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not start %s", item.name)
                    showToast(context, "Couldn't start that music")
                }
            }
        }

        /** The banner's play button: artists get their songs shuffled, everything else plays or opens */
        fun playFeatured(item: BaseItem) {
            if (item.type == BaseItemKind.MUSIC_ARTIST) {
                viewModelScope.launchIO {
                    try {
                        libraryHome.shuffleArtist(item)
                        withContext(WholphinDispatchers.Main) { navigationManager.navigateTo(Destination.NowPlaying) }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not shuffle %s", item.name)
                        showToast(context, "Couldn't start that music")
                    }
                }
            } else if (item.type.playable) {
                navigationManager.navigateTo(Destination.Playback(item))
            } else {
                navigationManager.navigateTo(item.destination())
            }
        }

        fun surpriseMe() {
            viewModelScope.launchIO {
                try {
                    val item = libraryHome.surprise(parentId, kind)
                    if (item != null) {
                        withContext(WholphinDispatchers.Main) { navigationManager.navigateTo(item.destination()) }
                    } else {
                        withContext(WholphinDispatchers.Main) { showToast(context, "Nothing unwatched to pick from") }
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Surprise me failed")
                }
            }
        }

        fun toggleFeaturedFavorite(item: BaseItem) {
            viewModelScope.launchIO {
                val favorite = !item.favorite
                favoriteWatchManager.setFavorite(item.id, favorite)
                _state.update { state ->
                    state.copy(
                        featured =
                            state.featured.map {
                                if (it.item.id == item.id) {
                                    val data = it.item.data
                                    it.copy(item = it.item.copy(data = data.copy(userData = data.userData?.copy(isFavorite = favorite))))
                                } else {
                                    it
                                }
                            },
                    )
                }
            }
        }

        private suspend fun <T> execute(
            row: RecommendedRow<T>,
            limit: Int?,
        ): List<BaseItem?> =
            if (limit != null) {
                val request = row.handler.prepare(row.request, 0, limit, false)
                row.handler
                    .execute(api, request)
                    .toBaseItems(api, true)
            } else {
                ApiRequestPager(
                    api,
                    row.request,
                    row.handler,
                    viewModelScope,
                    useSeriesForPrimary = true,
                ).init()
            }

        private fun fetchSuggestions() {
            viewModelScope.launch(WholphinDispatchers.IO) {
                val limit =
                    userPreferencesService
                        .getCurrent()
                        .appPreferences.homePagePreferences.maxItemsPerRow
                val title = ResStringProvider(R.string.suggestions)
                try {
                    suggestionService
                        .getSuggestionsFlow(parentId, suggestionsType)
                        .collect { resource ->
                            val result =
                                when (resource) {
                                    is SuggestionsResource.Loading -> {
                                        HomeRowLoadingState.Loading(title)
                                    }

                                    is SuggestionsResource.Success -> {
                                        HomeRowLoadingState.Success(
                                            title,
                                            resource.items,
                                            viewOptions,
                                            showViewMore = resource.items.size >= limit,
                                        )
                                    }

                                    is SuggestionsResource.Empty -> {
                                        HomeRowLoadingState.Success(
                                            title,
                                            emptyList(),
                                            viewOptions,
                                            showViewMore = false,
                                        )
                                    }
                                }
                            _state.update {
                                it.copy(
                                    rows =
                                        it.rows.toMutableList().apply {
                                            set(lastIndex, result)
                                        },
                                )
                            }
                        }
                } catch (_: CancellationException) {
                    // no-op
                } catch (ex: Exception) {
                    Timber.e(ex, "Failed to fetch suggestions")
                    _state.update {
                        it.copy(
                            rows =
                                it.rows.toMutableList().apply {
                                    set(lastIndex, HomeRowLoadingState.Error(title, null, ex))
                                },
                        )
                    }
                }
            }
        }

        fun refreshItem(
            position: RowColumn,
            itemId: UUID,
        ) {
            viewModelScope.launchIO {
                val row = state.value.rows.getOrNull(position.row)
                if (row is HomeRowLoadingState.Success) {
                    (row.items as? ApiRequestPager<*>)?.refreshItem(position.column, itemId)
                }
            }
        }

        fun setWatched(
            position: RowColumn,
            itemId: UUID,
            watched: Boolean,
        ) {
            viewModelScope.launchIO {
                favoriteWatchManager.setWatched(itemId, watched)
                refreshItem(position, itemId)
            }
        }

        fun setFavorite(
            position: RowColumn,
            itemId: UUID,
            watched: Boolean,
        ) {
            viewModelScope.launchIO {
                favoriteWatchManager.setFavorite(itemId, watched)
                refreshItem(position, itemId)
            }
        }

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchIO {
                backdropService.submit(item)
            }
        }

        fun deleteItem(
            position: RowColumn,
            item: BaseItem,
        ) {
            deleteItem(context, mediaManagementService, item) {
                viewModelScope.launchDefault {
                    val row = state.value.rows.getOrNull(position.row)
                    if (row is HomeRowLoadingState.Success) {
                        (row.items as? ApiRequestPager<*>)?.refreshPagesAfter(position.column)
                    }
                }
            }
        }

        fun canDelete(
            item: BaseItem,
            appPreferences: AppPreferences,
        ): Boolean = mediaManagementService.canDelete(item, appPreferences)

        fun addToQueue(
            item: BaseItem,
            index: Int,
        ) = addToQueue(api, musicService, item, index)

        fun onClickViewMore(
            position: RowColumn,
            row: HomeRowLoadingState.Success,
        ) {
            if (position.row in recommendedRows.indices) {
                val recommendedRow = recommendedRows[position.row] as RecommendedRow<Any>
                navigationManager.navigateTo(
                    Destination.ItemGrid(
                        title = row.title,
                        request = recommendedRow.request,
                        requestHandler = recommendedRow.handler,
                        initialPosition = row.items.size,
                        viewOptions =
                            ViewOptions(
                                aspectRatio = viewOptions.aspectRatio,
                            ),
                    ),
                )
            } else {
                // Suggestions
                navigationManager.navigateTo(
                    Destination.ItemGrid(
                        title = row.title,
                        request =
                            GetItemsRequest(
                                ids = row.items.mapNotNull { it?.id },
                            ),
                        requestHandler = GetItemsRequestHandler,
                        initialPosition = row.items.size,
                        viewOptions =
                            ViewOptions(
                                aspectRatio = viewOptions.aspectRatio,
                            ),
                    ),
                )
            }
        }
    }

data class RecommendedState(
    val loading: LoadingState = LoadingState.Pending,
    val rows: List<HomeRowLoadingState> = emptyList(),
    /** Benflix: the mini Home banner */
    val featured: List<FeaturedItem> = emptyList(),
)

/** Benflix: which rows a [RecommendedViewModel] shows besides its fixed ones */
const val SHELVES_HOME = "home"
const val SHELVES_SEASONS = "seasons"
const val SHELVES_DECADES = "decades"
const val SHELVES_WEEK = "week"

/** Benflix: rows that change after watching something, reloaded when coming back to the page */
private val PROGRESS_ROW_TITLES = setOf(R.string.continue_watching, R.string.next_up)
private val PROGRESS_SHELF_TITLES = setOf("Played lately", "Finish the season")

data class RecommendedRow<T>(
    val title: Int,
    val handler: RequestHandler<T>,
    val request: T,
)

@Composable
fun RecommendedContent(
    preferences: UserPreferences,
    viewModel: RecommendedViewModel,
    modifier: Modifier = Modifier,
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
    onFocusPosition: ((RowColumn) -> Unit)? = null,
) {
    var showContextMenu by remember { mutableStateOf<ContextMenu?>(null) }
    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.collectAsState()

    OneTimeLaunchedEffect {
        viewModel.init()
    }
    // Benflix: back from the player, Continue watching & co. show the new progress (like Home)
    LifecycleStartEffect(Unit) {
        viewModel.refreshProgressRows()
        onStopOrDispose { }
    }
    val state by viewModel.state.collectAsState()

    when (val st = state.loading) {
        is LoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        LoadingState.Success -> {
            var position by rememberPosition()
            val contextActions =
                remember {
                    ContextMenuActions(
                        navigateTo = viewModel.navigationManager::navigateTo,
                        onClickWatch = { itemId, watched ->
                            viewModel.setWatched(position, itemId, watched)
                        },
                        onClickFavorite = { itemId, favorite ->
                            viewModel.setFavorite(position, itemId, favorite)
                        },
                        onClickAddPlaylist = { itemId ->
                            playlistViewModel.loadPlaylists()
                            showPlaylistDialog.makePresent(itemId)
                        },
                        onSendMediaInfo = viewModel.serverReportService::sendMediaReportFor,
                        onDeleteItem = { viewModel.deleteItem(position, it) },
                        onShowOverview = { overviewDialog = ItemDetailsDialogInfo(it) },
                        onChooseVersion = { _, _ ->
                            // Not supported on this page
                        },
                        onChooseTracks = { result ->
                            // Not supported on this page
                        },
                        onClearChosenStreams = {
                            // Not supported on this page
                        },
                    )
                }

            HomePageContent(
                homeRows = state.rows,
                position = position,
                onClickItem = viewModel::clickItem,
                onLongClickItem = { position, item ->
                    showContextMenu =
                        ContextMenu.ForBaseItem(
                            fromLongClick = true,
                            item = item,
                            chosenStreams = null,
                            showGoTo = true,
                            showStreamChoices = false,
                            canDelete = viewModel.canDelete(item, preferences.appPreferences),
                            canRemoveContinueWatching = false,
                            canRemoveNextUp = false,
                            actions = contextActions,
                        )
                },
                onClickPlay = { _, item ->
                    viewModel.navigationManager.navigateTo(Destination.Playback(item))
                },
                onFocusPosition = {
                    position = it
                    // Benflix: the banner reports row -1, and rows can still be loading, so keep the index in range
                    val nonEmptyRowBefore =
                        state.rows
                            .subList(0, it.row.coerceIn(0, state.rows.size))
                            .count {
                                it is HomeRowLoadingState.Success && it.items.isEmpty()
                            }
                    onFocusPosition?.invoke(
                        RowColumn(
                            if (it.row < 0) it.row else it.row - nonEmptyRowBefore,
                            it.column,
                        ),
                    )
                },
                showClock = preferences.appPreferences.interfacePreferences.showClock,
                onUpdateBackdrop = viewModel::updateBackdrop,
                showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                showViewMore = true,
                onClickViewMore = viewModel::onClickViewMore,
                modifier = modifier,
                // Benflix mini Home: a banner like Home's, for this library
                featured = state.featured,
                onClickFeaturedPlay = viewModel::playFeatured,
                onClickFeaturedInfo = { item -> viewModel.navigationManager.navigateTo(item.destination()) },
                onClickFeaturedFavorite = viewModel::toggleFeaturedFavorite,
                onClickSurprise = viewModel::surpriseMe,
            )
        }
    }
    overviewDialog?.let { info ->
        ItemDetailsDialog(
            info = info,
            showFilePath =
                viewModel.serverRepository.currentUserDto
                    ?.policy
                    ?.isAdministrator == true,
            onDismissRequest = { overviewDialog = null },
        )
    }
    showContextMenu?.let { contextMenu ->
        ContextMenuDialog(
            onDismissRequest = { showContextMenu = null },
            getMediaSource = null,
            contextMenu = contextMenu,
            preferredSubtitleLanguage = null,
        )
    }
    showPlaylistDialog.compose { itemId ->
        PlaylistDialog(
            title = stringResource(R.string.add_to_playlist),
            state = playlistState,
            onDismissRequest = { showPlaylistDialog.makeAbsent() },
            onClick = {
                playlistViewModel.addToPlaylist(it.id, itemId)
                showPlaylistDialog.makeAbsent()
            },
            createEnabled = true,
            onCreatePlaylist = {
                playlistViewModel.createPlaylistAndAddItem(it, itemId)
                showPlaylistDialog.makeAbsent()
            },
            onSearch = playlistViewModel::loadPlaylists,
            elevation = 3.dp,
        )
    }
}

/**
 * Benflix: a tab made only of a library's own rows, such as Seasons (anime) or Decades (movies)
 */
@Composable
fun LibraryShelfTab(
    preferences: UserPreferences,
    parentId: UUID,
    shelfSet: String,
    suggestionsType: BaseItemKind,
    onFocusPosition: (RowColumn) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecommendedViewModel =
        hiltViewModel<RecommendedViewModel, RecommendedViewModel.Factory>(
            key = "$parentId-$shelfSet",
            creationCallback = {
                it.create(
                    parentId = parentId,
                    suggestionsType = suggestionsType,
                    recommendedRows = emptyList(),
                    viewOptions = HomeRowViewOptions(),
                    shelfSet = shelfSet,
                )
            },
        ),
) {
    RecommendedContent(
        preferences = preferences,
        viewModel = viewModel,
        onFocusPosition = onFocusPosition,
        modifier = modifier,
    )
}
