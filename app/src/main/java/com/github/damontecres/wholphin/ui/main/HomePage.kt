package com.github.damontecres.wholphin.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.Player
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.overviewCache
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.components.BenflixTip
import com.github.damontecres.wholphin.ui.components.BenflixTipId
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.ContextMenu
import com.github.damontecres.wholphin.ui.components.ContextMenuActions
import com.github.damontecres.wholphin.ui.components.ContextMenuDialog
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.FocusableItemRow
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.indexOfFirstOrNull
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.playback.isPlayKeyUp
import com.github.damontecres.wholphin.ui.playback.playable
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.ScrollToTopBringIntoViewSpec
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.StartupTimer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.time.LocalDate
import java.util.UUID

@Composable
fun HomePage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LifecycleStartEffect(Unit) {
        viewModel.init()
        onStopOrDispose { }
    }
    // Benflix: the admin's alert (new problem report, disk nearly full) only shows while Home is on screen
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            com.github.damontecres.wholphin.ui.components.ServerAlertNotice
                .clear()
        }
    }
    // Benflix: the welcome setup, the first time a person uses Benflix
    WelcomeGate()
    val state by viewModel.state.collectAsState()
    val featured = state.featured
    val trending = state.trending
    val trendingMissing = state.trendingMissing
    val themed = state.themed
    val monthly = state.monthly
    val because = state.because
    val kidsAgain = state.kidsAgain
    val becauseMore = state.becauseMore
    val collections = state.collections
    val mood = state.mood
    val moodRow = state.moodRow
    val newest = state.newest
    val myList = state.myList
    val comeBack = state.comeBack
    val welcomeBack = state.welcomeBack
    val world = state.world
    val airing = state.airing
    val spotlight = state.spotlight
    val genreRows = state.genreRows
    val animeTop = state.animeTop
    val animeRows = state.animeRows
    val previewPlaying by viewModel.previewPlaying.collectAsState()
    LifecycleStartEffect(Unit) {
        onStopOrDispose { viewModel.previewTrailer(null) }
    }
    val loading = state.loadingState
    val refreshing = state.refreshState
    val homeRows = state.homeRows
    // Benflix start-up stopwatch
    LaunchedEffect(loading) {
        if (loading == LoadingState.Success) StartupTimer.mark(StartupTimer.HOME_ROWS)
    }
    LaunchedEffect(featured.isNotEmpty()) {
        if (featured.isNotEmpty()) StartupTimer.finish(context)
    }

    when (val state = loading) {
        is LoadingState.Error -> {
            ErrorMessage(state, modifier)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        LoadingState.Success -> {
            var showContextMenu by remember { mutableStateOf<ContextMenu?>(null) }
            var showPlaylistDialog by remember { mutableStateOf<UUID?>(null) }
            var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
            val scope = rememberCoroutineScope()
            val overviewContext = LocalContext.current
            val overviewCache = remember { overviewCache(overviewContext) }

            val playlistState by playlistViewModel.playlistState.collectAsState()
            var position by rememberPosition()

            val onFocusPosition = remember { { it: RowColumn -> position = it } }
            val currentHomePrefs by rememberUpdatedState(preferences.appPreferences.homePagePreferences)
            // The handlers below are remembered once, so they read the rows & settings as they are now
            val currentHomeRows by rememberUpdatedState(homeRows)
            val currentAppPrefs by rememberUpdatedState(preferences.appPreferences)
            val onClickItem =
                remember {
                    { clickedPosition: RowColumn, item: BaseItem ->
                        position = clickedPosition
                        if (currentHomePrefs.clickToPlay &&
                            currentHomeRows.getOrNull(clickedPosition.row)?.isContinueWatchingNextUp == true
                        ) {
                            viewModel.navigationManager.navigateTo(Destination.Playback(item))
                        } else {
                            viewModel.navigationManager.navigateTo(item.destination())
                        }
                    }
                }
            // The long-press menu for a Jellyfin item on any Home row. Only Continue watching & Next up
            // offer to remove it from that row.
            fun itemMenu(
                item: BaseItem,
                canRemoveContinueWatching: Boolean,
                canRemoveNextUp: Boolean,
                deletePosition: RowColumn?,
            ): ContextMenu =
                ContextMenu.ForBaseItem(
                    fromLongClick = true,
                    item = item,
                    chosenStreams = null,
                    showGoTo = true,
                    showStreamChoices = false,
                    canDelete =
                        viewModel.canDelete(
                            item,
                            currentAppPrefs,
                        ),
                    canRemoveContinueWatching = canRemoveContinueWatching,
                    canRemoveNextUp = canRemoveNextUp,
                    actions =
                        ContextMenuActions(
                            navigateTo = viewModel.navigationManager::navigateTo,
                            onClickWatch = viewModel::setWatched,
                            onClickFavorite = viewModel::setFavorite,
                            onClickAddPlaylist = { itemId ->
                                playlistViewModel.loadPlaylists()
                                showPlaylistDialog = itemId
                            },
                            onSendMediaInfo = viewModel.serverReportService::sendMediaReportFor,
                            onDeleteItem = {
                                viewModel.deleteItem(deletePosition ?: position, it)
                            },
                            onChooseVersion = { _, _ ->
                                // Not supported on this page
                            },
                            onChooseTracks = {
                                // Not supported on this page
                            },
                            onShowOverview = {
                                overviewDialog = ItemDetailsDialogInfo(it)
                                if (it.data.overview == null) {
                                    // Home rows come without descriptions, so fetch this one
                                    val itemId = it.id
                                    scope.launch {
                                        val text = overviewCache.get(itemId)
                                        overviewDialog = overviewDialog?.copy(overview = text)
                                    }
                                }
                            },
                            onClearChosenStreams = {},
                            onClickRemoveFromNextUp = viewModel::removeFromNextUp,
                        ),
                )
            val onLongClickItem =
                remember {
                    { clickedPosition: RowColumn, item: BaseItem ->
                        position = clickedPosition
                        val row =
                            (currentHomeRows.getOrNull(clickedPosition.row) as? HomeRowLoadingState.Success)
                        val canRemoveContinueWatching =
                            row?.rowType is HomeRowConfig.ContinueWatching || row?.rowType is HomeRowConfig.ContinueWatchingCombined
                        val canRemoveNextUp =
                            row?.rowType is HomeRowConfig.NextUp || row?.rowType is HomeRowConfig.ContinueWatchingCombined
                        showContextMenu =
                            itemMenu(
                                item = item,
                                canRemoveContinueWatching = canRemoveContinueWatching,
                                canRemoveNextUp = canRemoveNextUp,
                                deletePosition = null,
                            )
                    }
                }
            // Benflix: the same menu on the extra poster rows (Top 10, Because you watched, My List...),
            // which aren't part of the main rows, so nothing to remove them from
            val onLongClickExtraItem =
                remember {
                    { item: BaseItem ->
                        showContextMenu =
                            itemMenu(
                                item = item,
                                canRemoveContinueWatching = false,
                                canRemoveNextUp = false,
                                deletePosition = RowColumn(-1, -1),
                            )
                    }
                }
            val onClickPlay =
                remember {
                    { _: RowColumn, item: BaseItem ->
                        viewModel.navigationManager.navigateTo(Destination.Playback(item))
                    }
                }
            val onClickViewMore =
                remember {
                    { _: RowColumn, row: HomeRowLoadingState.Success ->
                        viewModel.navigationManager.navigateTo(
                            Destination.MoreHomeRow(row.title, row.rowType!!, row.items.size),
                        )
                    }
                }

            // Benflix on phones: pull down at the top to load Home again
            com.github.damontecres.wholphin.ui.touch.PhonePullToRefresh(
                refreshing = refreshing is LoadingState.Loading,
                onRefresh = { viewModel.init() },
                modifier = modifier,
            ) {
                HomePageContent(
                    homeRows = homeRows,
                    position = position,
                    onFocusPosition = onFocusPosition,
                    onClickItem = onClickItem,
                    onLongClickItem = onLongClickItem,
                    onClickPlay = onClickPlay,
                    loadingState = refreshing,
                    showClock = preferences.appPreferences.interfacePreferences.showClock,
                    onUpdateBackdrop = viewModel::updateBackdrop,
                    showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                    showViewMore = true,
                    onClickViewMore = onClickViewMore,
                    featured = featured,
                    onClickFeaturedPlay = { item ->
                        if (item.type.playable) {
                            viewModel.navigationManager.navigateTo(Destination.Playback(item))
                        } else {
                            viewModel.navigationManager.navigateTo(item.destination())
                        }
                    },
                    onClickFeaturedInfo = { item ->
                        viewModel.navigationManager.navigateTo(item.destination())
                    },
                    onClickFeaturedFavorite = viewModel::toggleFeaturedFavorite,
                    onClickSurprise = viewModel::surpriseMe,
                    trending = trending,
                    onClickTrending = { item ->
                        viewModel.navigationManager.navigateTo(item.destination())
                    },
                    onLongClickTrending = onLongClickExtraItem,
                    trendingMissing = trendingMissing,
                    onClickDiscover = { item ->
                        viewModel.navigationManager.navigateTo(item.destination)
                    },
                    onUpdateDiscoverBackdrop = viewModel::updateBackdrop,
                    themed = themed,
                    monthly = monthly,
                    because = because,
                    kidsAgain = kidsAgain,
                    becauseMore = becauseMore,
                    collections = collections,
                    mood = mood,
                    moodRow = moodRow,
                    onClickMood = viewModel::pickMood,
                    newest = newest,
                    myList = myList,
                    comeBack = comeBack,
                    welcomeBack = welcomeBack,
                    world = world,
                    airing = airing,
                    spotlight = spotlight,
                    genreRows = genreRows,
                    animeTop = animeTop,
                    animeRows = animeRows,
                    previewPlayer = viewModel.previewPlayer,
                    previewPlaying = previewPlaying,
                    onHeroItemShown = viewModel::previewTrailer,
                    showNote = true,
                )
            }
            overviewDialog?.let { info ->
                ItemDetailsDialog(
                    info = info,
                    showFilePath = false,
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
            showPlaylistDialog?.let { itemId ->
                PlaylistDialog(
                    title = stringResource(R.string.add_to_playlist),
                    state = playlistState,
                    onDismissRequest = { showPlaylistDialog = null },
                    onClick = {
                        playlistViewModel.addToPlaylist(it.id, itemId)
                        showPlaylistDialog = null
                    },
                    createEnabled = true,
                    onCreatePlaylist = {
                        playlistViewModel.createPlaylistAndAddItem(it, itemId)
                        showPlaylistDialog = null
                    },
                    onSearch = playlistViewModel::loadPlaylists,
                    elevation = 3.dp,
                )
            }
        }
    }
}

private const val HERO_ROTATE_MS = 7_000L

val HomeRowLoadingState?.isContinueWatchingNextUp: Boolean
    get() =
        (this as? HomeRowLoadingState.Success).let { row ->
            row?.rowType is HomeRowConfig.ContinueWatching ||
                row?.rowType is HomeRowConfig.NextUp ||
                row?.rowType is HomeRowConfig.ContinueWatchingCombined
        }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomePageContent(
    homeRows: List<HomeRowLoadingState>,
    position: RowColumn,
    onFocusPosition: (RowColumn) -> Unit,
    onClickItem: (RowColumn, BaseItem) -> Unit,
    onLongClickItem: (RowColumn, BaseItem) -> Unit,
    onClickPlay: (RowColumn, BaseItem) -> Unit,
    showClock: Boolean,
    onUpdateBackdrop: (BaseItem) -> Unit,
    showLogo: Boolean,
    showViewMore: Boolean,
    modifier: Modifier = Modifier,
    loadingState: LoadingState? = null,
    listState: LazyListState = rememberLazyListState(),
    takeFocus: Boolean = true,
    showEmptyRows: Boolean = false,
    headerComposable: @Composable (focusedItem: BaseItem?) -> Unit = { focusedItem ->
        HomePageHeader(
            item = focusedItem,
            showLogo = showLogo,
            modifier = HomeHeaderModifier,
        )
    },
    onClickViewMore: (RowColumn, HomeRowLoadingState.Success) -> Unit = { _, _ -> },
    featured: List<FeaturedItem> = emptyList(),
    onClickFeaturedPlay: (BaseItem) -> Unit = {},
    onClickFeaturedInfo: (BaseItem) -> Unit = {},
    onClickFeaturedFavorite: (BaseItem) -> Unit = {},
    onClickSurprise: () -> Unit = {},
    trending: List<BaseItem> = emptyList(),
    onClickTrending: (BaseItem) -> Unit = {},
    onLongClickTrending: (BaseItem) -> Unit = {},
    trendingMissing: List<DiscoverItem> = emptyList(),
    themed: ThemedRow? = null,
    monthly: List<ThemedRow> = emptyList(),
    because: ThemedRow? = null,
    kidsAgain: ThemedRow? = null,
    becauseMore: ThemedRow? = null,
    collections: ThemedRow? = null,
    mood: HomeMood? = null,
    moodRow: ThemedRow? = null,
    onClickMood: ((HomeMood) -> Unit)? = null,
    newest: ThemedRow? = null,
    myList: ThemedRow? = null,
    comeBack: ThemedRow? = null,
    welcomeBack: ThemedRow? = null,
    world: List<ThemedRow> = emptyList(),
    airing: ThemedRow? = null,
    spotlight: ThemedRow? = null,
    genreRows: List<ThemedRow> = emptyList(),
    animeTop: List<BaseItem> = emptyList(),
    animeRows: List<ThemedRow> = emptyList(),
    previewPlayer: Player? = null,
    previewPlaying: Boolean = false,
    onHeroItemShown: (BaseItem?) -> Unit = {},
    onClickDiscover: (DiscoverItem) -> Unit = {},
    onUpdateDiscoverBackdrop: (DiscoverItem) -> Unit = {},
    showNote: Boolean = false,
) {
    // An item focused in the trending rows, which aren't part of homeRows
    var extraFocus by remember { mutableStateOf<Any?>(null) }
    var heroIndex by remember { mutableIntStateOf(0) }
    var heroFocusedTv by remember { mutableStateOf(false) }
    val isTv = rememberIsTv()
    // Phones have no focus, so there the banner counts as "selected" while it is still on screen
    val heroOnScreen by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex <= 1 } }
    val showHero = featured.isNotEmpty()
    // Benflix (TV): the banner stays "on" (logo, description, turning slides) while nothing below it has been
    // picked, so moving up into the top menu doesn't blank or freeze it
    val heroFocused =
        if (isTv) {
            showHero && (heroFocusedTv || (position.row < 0 && extraFocus == null))
        } else {
            showHero && heroOnScreen
        }
    val heroFocusRequester = remember { FocusRequester() }
    val currentHeroFocusedTv by rememberUpdatedState(heroFocusedTv)

    // Benflix: asking for focus right after the banner appears can fail because it isn't on screen yet,
    // so keep asking for a moment until it really has focus
    suspend fun focusHero() {
        repeat(30) {
            if (currentHeroFocusedTv) return
            heroFocusRequester.tryRequestFocus()
            delay(100)
        }
    }
    // Rows start after the banner, and on phones also after the header that scrolls with the page
    val heroOffset = (if (showHero) 1 else 0) + (if (isTv) 0 else 1)
    val currentHeroItem = if (heroFocused) featured.getOrNull(heroIndex)?.item else null
    LaunchedEffect(currentHeroItem?.id) {
        onHeroItemShown(currentHeroItem)
    }
    LaunchedEffect(featured.size, heroFocused, previewPlaying, heroIndex) {
        // Rotate the banner while it is focused, but let a trailer finish first.
        // Keyed on heroIndex, so picking a slide yourself starts its 7 seconds over.
        if (heroFocused && featured.size > 1 && !previewPlaying) {
            delay(HERO_ROTATE_MS)
            heroIndex = (heroIndex + 1) % featured.size
        }
    }
    val autoAdvanceMs = if (heroFocused && featured.size > 1 && !previewPlaying) HERO_ROTATE_MS else null
    val onSelectSlide: (Int) -> Unit = { heroIndex = it.coerceIn(0, (featured.size - 1).coerceAtLeast(0)) }
    val focusedItem =
        remember(homeRows, position, heroFocused, heroIndex, featured, extraFocus) {
            if (heroFocused && showHero) {
                featured.getOrNull(heroIndex.coerceIn(0, featured.lastIndex))?.item
            } else if (extraFocus != null) {
                extraFocus as? BaseItem
            } else {
                (homeRows.getOrNull(position.row) as? HomeRowLoadingState.Success)?.items?.getOrNull(
                    position.column,
                )
            }
        }

    val rowFocusRequesters = remember(homeRows.size) { List(homeRows.size) { FocusRequester() } }
    var firstFocused by remember { mutableStateOf(false) }

    val currentPosition by rememberUpdatedState(position)
    val currentOnFocusPosition by rememberUpdatedState(onFocusPosition)
    val currentOnClickPlay by rememberUpdatedState(onClickPlay)

    // Benflix: the first row was picked only because the banner wasn't ready yet
    var autoPickedRow by remember { mutableStateOf<RowColumn?>(null) }
    if (takeFocus) {
        LaunchedEffect(homeRows, showHero) {
            val waitingRow = autoPickedRow
            val untouched =
                waitingRow != null &&
                    (currentPosition.row < 0 || currentPosition == waitingRow)
            if (showHero && untouched) {
                // The banner arrived after the rows and nobody has moved yet: start on the banner after all
                autoPickedRow = null
                onFocusPosition(RowColumn(-1, -1))
                listState.scrollToItem(0)
                focusHero()
                firstFocused = true
            } else if (!firstFocused && showHero && position.row < 0) {
                // Start on the banner, like other streaming apps
                listState.scrollToItem(0)
                focusHero()
                firstFocused = true
            } else if (!firstFocused && homeRows.isNotEmpty()) {
                if (position.row >= 0) {
                    val index = position.row.coerceIn(0, rowFocusRequesters.lastIndex)
                    rowFocusRequesters.getOrNull(index)?.tryRequestFocus()
                    firstFocused = true
                } else {
                    // Waiting for the first home row to load, then focus on it
                    homeRows
                        .indexOfFirstOrNull { it is HomeRowLoadingState.Success && it.items.isNotEmpty() }
                        ?.let {
                            rowFocusRequesters[it].tryRequestFocus()
                            firstFocused = true
                            if (!showHero) autoPickedRow = RowColumn(it, 0)
                            delay(50)
                            listState.scrollToItem(
                                it + heroOffset,
                            )
                        }
                }
            }
        }
    }
    LaunchedEffect(onUpdateBackdrop, focusedItem) {
        // On phones the banner card shows its own picture, so the page background stays dark
        if (isTv || !(showHero && heroFocused)) {
            focusedItem?.let { onUpdateBackdrop.invoke(it) }
        }
    }
    val focusedDiscover = if (heroFocused) null else extraFocus as? DiscoverItem
    LaunchedEffect(focusedDiscover) {
        focusedDiscover?.let { onUpdateDiscoverBackdrop.invoke(it) }
    }
    // Benflix: pressing Home (or coming back after a long time away) starts on the banner
    val jumpRequests by HomeJump.requests.collectAsState()
    LaunchedEffect(jumpRequests, showHero) {
        if (showHero && HomeJump.consume()) {
            onFocusPosition(RowColumn(-1, -1))
            heroIndex = 0
            listState.scrollToItem(0)
            if (isTv) focusHero()
        }
    }
    // Benflix: deep in the page, Back first jumps to the top. Pressing it again goes to the top menu
    var contentHasFocus by remember { mutableStateOf(false) }
    val backScope = rememberCoroutineScope()
    val atTop = if (showHero) heroFocusedTv else position.row <= 0
    BackHandler(enabled = isTv && contentHasFocus && !atTop) {
        backScope.launch {
            listState.scrollToItem(0)
            if (showHero) {
                onFocusPosition(RowColumn(-1, -1))
                focusHero()
            } else {
                rowFocusRequesters.firstOrNull()?.tryRequestFocus()
            }
        }
    }
    Box(modifier = modifier.onFocusChanged { contentHasFocus = it.hasFocus }) {
        if (heroFocused && previewPlaying && previewPlayer != null) {
            HeroTrailer(
                player = previewPlayer,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        Column(
            modifier =
                Modifier
                    .focusProperties {
                        onEnter = {
                            // Coming down from the top menu: back to the banner if that's where you were
                            if (showHero && currentPosition.row < 0) {
                                heroFocusRequester.tryRequestFocus()
                            } else {
                                rowFocusRequesters.getOrNull(currentPosition.row)?.tryRequestFocus()
                            }
                        }
                    }.fillMaxSize(),
        ) {
            val pageHeader: @Composable () -> Unit = {
                if (focusedDiscover != null) {
                    HomePageHeader(
                        title = focusedDiscover.title,
                        subtitle = null,
                        overview = focusedDiscover.overview,
                        overviewTwoLines = false,
                        quickDetails = null,
                        timeRemaining = null,
                        endsAt = null,
                        showLogo = showLogo,
                        logoImageUrl = focusedDiscover.logoUrl,
                        modifier = HomeHeaderModifier,
                    )
                } else {
                    val currentFeatured = if (heroFocused) featured.getOrNull(heroIndex) else null
                    if (currentFeatured != null) {
                        FeaturedReasonLabel(
                            featured = currentFeatured,
                            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                        )
                    }
                    headerComposable.invoke(focusedItem)
                }
            }
            // On TV the header stays put above the rows; on phones it scrolls away with the rest of the page
            if (isTv) pageHeader()

            // Benflix Home trim: the extra rows (more because-you-watched, collections, anime picks,
            // genre rows) take turns by day; only a couple show until you scroll near the bottom
            val extraRowMap =
                buildMap {
                    becauseMore?.let { put("becauseMore", it) }
                    collections?.let { put("collections", it) }
                    comeBack?.let { put("comeBack", it) }
                    airing?.let { put("airing", it) }
                    world.forEachIndexed { index, row -> put("world$index", row) }
                    spotlight?.let { put("spotlight", it) }
                    animeRows.forEachIndexed { index, row -> put("anime$index", row) }
                    genreRows.forEachIndexed { index, row -> put("genre$index", row) }
                }
            val extraRowFor: (String) -> ThemedRow? = { extraRowMap[it] }
            // Each row's place depends only on its name and today's date, so rows that load later
            // don't shuffle the ones already on screen
            val today = remember { LocalDate.now().toEpochDay() }
            val extraKeys = extraRowMap.keys.sortedBy { "$it-$today".hashCode() }
            var showAllRows by remember { mutableStateOf(false) }
            val nearBottom by remember(listState) {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                    info.totalItemsCount > 0 && last >= info.totalItemsCount - 2
                }
            }
            LaunchedEffect(nearBottom) { if (nearBottom) showAllRows = true }

            val density = LocalDensity.current
            val spaceAbovePx =
                remember(density) {
                    with(density) {
                        // The size of the row titles & spacing
                        50.dp.toPx()
                    }
                }
            val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
            CompositionLocalProvider(
                LocalBringIntoViewSpec provides ScrollToTopBringIntoViewSpec(spaceAbovePx),
            ) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    contentPadding =
                        PaddingValues(
                            bottom = Cards.height2x3,
                        ),
                    modifier =
                        Modifier
                            .focusRestorer(),
                ) {
                    if (!isTv) {
                        val phoneFeatured = featured.getOrNull(heroIndex.coerceIn(0, (featured.size - 1).coerceAtLeast(0)))
                        item(key = "header") {
                            if (showHero && phoneFeatured != null) {
                                // Phones: the banner is a big poster carousel you swipe (Home look C)
                                Column {
                                    PhonePosterCarousel(
                                        featured = featured,
                                        index = heroIndex.coerceIn(0, featured.lastIndex),
                                        onIndexChange = onSelectSlide,
                                        onClick = { onClickFeaturedInfo(it.item) },
                                    )
                                    if (featured.size > 1) {
                                        SlideDots(
                                            count = featured.size,
                                            index = heroIndex.coerceIn(0, featured.lastIndex),
                                            onSelect = onSelectSlide,
                                            autoAdvanceMs = autoAdvanceMs,
                                            modifier =
                                                Modifier
                                                    .align(Alignment.CenterHorizontally)
                                                    .padding(top = 8.dp),
                                        )
                                    }
                                    if (showNote) HomeNoteCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                                }
                            } else {
                                Column {
                                    pageHeader()
                                    if (showNote) HomeNoteCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                                }
                            }
                        }
                    }
                    if (showHero) {
                        item(key = "hero") {
                            Column {
                                HomeHero(
                                    featured = featured,
                                    index = heroIndex.coerceIn(0, featured.lastIndex),
                                    onSelectSlide = onSelectSlide,
                                    autoAdvanceMs = autoAdvanceMs,
                                    showDots = isTv,
                                    onClickPlay = onClickFeaturedPlay,
                                    onClickInfo = onClickFeaturedInfo,
                                    onClickFavorite = onClickFeaturedFavorite,
                                    onClickSurprise = onClickSurprise,
                                    onFocusChanged = {
                                        heroFocusedTv = it
                                        // Back on the banner: nothing below is picked any more
                                        if (it) currentOnFocusPosition(RowColumn(-1, -1))
                                    },
                                    focusRequester = heroFocusRequester,
                                )
                                if (showNote && isTv) {
                                    HomeNoteCard(Modifier.padding(start = 48.dp, end = 48.dp, bottom = 12.dp))
                                }
                            }
                        }
                    }
                    homeRows.forEachIndexed { rowIndex, row ->
                        item {
                            val rowModifier =
                                Modifier
                                    .animateItem(placementSpec = null)
                                    .padding(bottom = 8.dp)
                            CompositionLocalProvider(
                                LocalBringIntoViewSpec provides defaultBringIntoViewSpec,
                            ) {
                                when (val r = row) {
                                    is HomeRowLoadingState.Loading,
                                    is HomeRowLoadingState.Pending,
                                    -> {
                                        FocusableItemRow(
                                            title = r.title.getString(),
                                            subtitle = stringResource(R.string.loading),
                                            modifier = rowModifier,
                                        )
                                    }

                                    is HomeRowLoadingState.Error -> {
                                        FocusableItemRow(
                                            title = r.title.getString(),
                                            subtitle = r.localizedMessage,
                                            isError = true,
                                            modifier = rowModifier,
                                        )
                                    }

                                    is HomeRowLoadingState.Success -> {
                                        if (row.items.isNotEmpty()) {
                                            val viewOptions = row.viewOptions
                                            ItemRow(
                                                title = row.title.getString(),
                                                items = row.items,
                                                onClickItem =
                                                    remember(rowIndex, onClickItem) {
                                                        { index, item ->
                                                            onClickItem.invoke(
                                                                RowColumn(
                                                                    rowIndex,
                                                                    index,
                                                                ),
                                                                item,
                                                            )
                                                        }
                                                    },
                                                onLongClickItem =
                                                    remember(rowIndex, onLongClickItem) {
                                                        { index, item ->
                                                            onLongClickItem.invoke(
                                                                RowColumn(rowIndex, index),
                                                                item,
                                                            )
                                                        }
                                                    },
                                                modifier =
                                                    rowModifier
                                                        .fillMaxWidth()
                                                        .focusGroup()
                                                        .focusRequester(rowFocusRequesters[rowIndex]),
                                                horizontalPadding = viewOptions.spacing.dp,
                                                cardContent = { index, item, cardModifier, onClick, onLongClick ->
                                                    val onFocus =
                                                        remember(rowIndex, index) {
                                                            { isFocused: Boolean ->
                                                                if (isFocused) {
                                                                    currentOnFocusPosition(
                                                                        RowColumn(
                                                                            rowIndex,
                                                                            index,
                                                                        ),
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    val onKey =
                                                        remember(item) {
                                                            { event: KeyEvent ->
                                                                if (isPlayKeyUp(event) && item?.type?.playable == true) {
                                                                    Timber.v("Clicked play on ${item.id}")
                                                                    currentOnClickPlay(
                                                                        currentPosition,
                                                                        item,
                                                                    )
                                                                    true
                                                                } else {
                                                                    false
                                                                }
                                                            }
                                                        }
                                                    HomePageCardContent(
                                                        index = index,
                                                        item = item,
                                                        onClick = onClick,
                                                        onLongClick = onLongClick,
                                                        viewOptions = viewOptions,
                                                        modifier =
                                                            cardModifier
                                                                .onFocusChanged { onFocus(it.isFocused) }
                                                                .onKeyEvent { onKey(it) },
                                                    )
                                                },
                                                showViewMore = showViewMore && row.showViewMore,
                                                viewMoreCardContent = { mod ->
                                                    HomePageViewMoreCard(
                                                        isEpisode = row.items.last()?.type == BaseItemKind.EPISODE,
                                                        onClick = {
                                                            onClickViewMore.invoke(
                                                                RowColumn(
                                                                    rowIndex,
                                                                    r.items.size,
                                                                ),
                                                                r,
                                                            )
                                                        },
                                                        onLongClick = {},
                                                        viewOptions = viewOptions,
                                                        modifier =
                                                            mod.onFocusChanged {
                                                                if (it.isFocused) {
                                                                    currentOnFocusPosition.invoke(
                                                                        RowColumn(
                                                                            rowIndex,
                                                                            r.items.size,
                                                                        ),
                                                                    )
                                                                }
                                                            },
                                                    )
                                                },
                                            )
                                        } else if (showEmptyRows) {
                                            FocusableItemRow(
                                                title = r.title.getString(),
                                                subtitle = stringResource(R.string.no_results),
                                                modifier = rowModifier,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Benflix Kids: their favourites first
                    if (kidsAgain != null) {
                        item(key = "kidsAgain") {
                            PosterRow(
                                title = kidsAgain.title,
                                items = kidsAgain.items,
                                onClickItem = onClickTrending,
                                onLongClickItem = onLongClickTrending,
                                onFocusItem = { extraFocus = it },
                                modifier =
                                    Modifier
                                        .padding(bottom = 8.dp)
                                        .onFocusChanged {
                                            if (!it.hasFocus && extraFocus in kidsAgain.items) extraFocus = null
                                        },
                            )
                        }
                    }
                    // Benflix: back after two weeks or more? What was added while you were away
                    if (welcomeBack != null) {
                        item(key = "welcomeBack") {
                            PosterRow(
                                title = welcomeBack.title,
                                items = welcomeBack.items,
                                onClickItem = onClickTrending,
                                onLongClickItem = onLongClickTrending,
                                onFocusItem = { extraFocus = it },
                                modifier =
                                    Modifier
                                        .padding(bottom = 8.dp)
                                        .onFocusChanged {
                                            if (!it.hasFocus && extraFocus in welcomeBack.items) extraFocus = null
                                        },
                            )
                        }
                    }
                    // Benflix rows, Netflix style, after Continue watching
                    if (trending.isNotEmpty()) {
                        item(key = "top10") {
                            Top10Row(
                                items = trending,
                                onClickItem = onClickTrending,
                                onLongClickItem = onLongClickTrending,
                                onFocusItem = { extraFocus = it },
                                modifier =
                                    Modifier
                                        .padding(bottom = 8.dp)
                                        .onFocusChanged {
                                            if (!it.hasFocus && extraFocus in trending) extraFocus = null
                                        },
                            )
                        }
                    }
                    // Top 10 anime: the Anime Movies and Anime Shows libraries together
                    if (animeTop.isNotEmpty()) {
                        item(key = "animeTop10") {
                            Top10Row(
                                title = stringResource(R.string.top_10_anime),
                                items = animeTop,
                                onClickItem = onClickTrending,
                                onLongClickItem = onLongClickTrending,
                                onFocusItem = { extraFocus = it },
                                modifier =
                                    Modifier
                                        .padding(bottom = 8.dp)
                                        .onFocusChanged {
                                            if (!it.hasFocus && extraFocus in animeTop) extraFocus = null
                                        },
                            )
                        }
                    }
                    if (trendingMissing.isNotEmpty()) {
                        item(key = "trendingMissing") {
                            TrendingMissingRow(
                                items = trendingMissing,
                                onClickItem = onClickDiscover,
                                onFocusItem = { extraFocus = it },
                                modifier =
                                    Modifier
                                        .padding(bottom = 8.dp)
                                        .onFocusChanged {
                                            if (!it.hasFocus && extraFocus in trendingMissing) extraFocus = null
                                        },
                            )
                        }
                    }
                    if (themed != null) {
                        item(key = "themed") {
                            PosterRow(
                                title = themed.title,
                                items = themed.items,
                                onClickItem = onClickTrending,
                                onLongClickItem = onLongClickTrending,
                                onFocusItem = { extraFocus = it },
                                modifier =
                                    Modifier
                                        .padding(bottom = 8.dp)
                                        .onFocusChanged {
                                            if (!it.hasFocus && extraFocus in themed.items) extraFocus = null
                                        },
                            )
                        }
                    }
                    monthly.forEachIndexed { index, row ->
                        item(key = "monthly$index") {
                            PosterRow(
                                title = row.title,
                                items = row.items,
                                onClickItem = onClickTrending,
                                onLongClickItem = onLongClickTrending,
                                onFocusItem = { extraFocus = it },
                                modifier =
                                    Modifier
                                        .padding(bottom = 8.dp)
                                        .onFocusChanged {
                                            if (!it.hasFocus && extraFocus in row.items) extraFocus = null
                                        },
                            )
                        }
                    }
                    if (because != null) {
                        item(key = "because") {
                            PosterRow(
                                title = because.title,
                                items = because.items,
                                onClickItem = onClickTrending,
                                onLongClickItem = onLongClickTrending,
                                onFocusItem = { extraFocus = it },
                                modifier =
                                    Modifier
                                        .padding(bottom = 8.dp)
                                        .onFocusChanged {
                                            if (!it.hasFocus && extraFocus in because.items) extraFocus = null
                                        },
                            )
                        }
                    }
                    if (onClickMood != null) {
                        item(key = "moods") {
                            MoodRow(
                                selected = mood,
                                onClickMood = onClickMood,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        if (moodRow != null) {
                            item(key = "moodRow") {
                                PosterRow(
                                    title = moodRow.title,
                                    items = moodRow.items,
                                    onClickItem = onClickTrending,
                                    onLongClickItem = onLongClickTrending,
                                onLongClickItem = onLongClickTrending,
                                    onFocusItem = { extraFocus = it },
                                    modifier =
                                        Modifier
                                            .padding(bottom = 8.dp)
                                            .onFocusChanged {
                                                if (!it.hasFocus && extraFocus in moodRow.items) extraFocus = null
                                            },
                                )
                            }
                        }
                    }
                    newest?.let { row ->
                        item(key = "newest") {
                            PosterRow(
                                title = row.title,
                                items = row.items,
                                onClickItem = onClickTrending,
                                onLongClickItem = onLongClickTrending,
                                onFocusItem = { extraFocus = it },
                                modifier =
                                    Modifier
                                        .padding(bottom = 8.dp)
                                        .onFocusChanged {
                                            if (!it.hasFocus && extraFocus in row.items) extraFocus = null
                                        },
                            )
                        }
                    }
                    myList?.let { row ->
                        item(key = "myList") {
                            PosterRow(
                                title = row.title,
                                items = row.items,
                                onClickItem = onClickTrending,
                                onLongClickItem = onLongClickTrending,
                                onFocusItem = { extraFocus = it },
                                modifier =
                                    Modifier
                                        .padding(bottom = 8.dp)
                                        .onFocusChanged {
                                            if (!it.hasFocus && extraFocus in row.items) extraFocus = null
                                        },
                            )
                        }
                    }
                    // Benflix: Home keeps about 12 rows. The extra rows take turns by day,
                    // and the rest load in as you get near the bottom
                    extraKeys.take(if (showAllRows) extraKeys.size else EXTRA_ROWS_FIRST).forEach { key ->
                        val row = extraRowFor(key) ?: return@forEach
                        item(key = key) {
                            PosterRow(
                                title = row.title,
                                items = row.items,
                                onClickItem = onClickTrending,
                                onLongClickItem = onLongClickTrending,
                                onFocusItem = { extraFocus = it },
                                modifier =
                                    Modifier
                                        .padding(bottom = 8.dp)
                                        .onFocusChanged {
                                            if (!it.hasFocus && extraFocus in row.items) extraFocus = null
                                        },
                            )
                        }
                    }
                }
            }
        }
        when (loadingState) {
            LoadingState.Pending,
            LoadingState.Loading,
            -> {
                Box(
                    modifier =
                        Modifier
                            .padding(if (showClock) 40.dp else 20.dp)
                            .size(40.dp)
                            .align(Alignment.TopEnd),
                ) {
                    CircularProgress(Modifier.fillMaxSize())
                }
            }

            else -> {}
        }
        // Benflix first-time tip (only on the real Home page)
        if (showNote) {
            BenflixTip(tip = if (isTv) BenflixTipId.HOME_TV else BenflixTipId.HOME_PHONE)
        }
    }
}

/**
 * A shorter header than other pages, since the top bar already gives space above it,
 * so the next row of cards peeks in below the focused row
 */
private val HomeHeaderModifier =
    Modifier
        .padding(top = 4.dp, bottom = 12.dp, start = HeaderUtils.startPadding)
        .height(150.dp)

/** How many of the extra Home rows show before you scroll near the bottom */
private const val EXTRA_ROWS_FIRST = 2
