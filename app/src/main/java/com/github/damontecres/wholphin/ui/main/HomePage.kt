package com.github.damontecres.wholphin.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.Player
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.data.model.QuickDetailsData
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.cards.BannerCard
import com.github.damontecres.wholphin.ui.cards.BannerCardWithTitle
import com.github.damontecres.wholphin.ui.cards.GenreCard
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.cards.StudioCard
import com.github.damontecres.wholphin.ui.cards.ViewMoreCard
import com.github.damontecres.wholphin.ui.cards.canGrow
import com.github.damontecres.wholphin.ui.cards.rememberGrowOnFocus
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.ContextMenu
import com.github.damontecres.wholphin.ui.components.ContextMenuActions
import com.github.damontecres.wholphin.ui.components.ContextMenuDialog
import com.github.damontecres.wholphin.ui.components.EpisodeName
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.FocusableItemRow
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.components.TitleOrLogo
import com.github.damontecres.wholphin.ui.components.rememberLogoUrl
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.indexOfFirstOrNull
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.playback.isPlayKeyUp
import com.github.damontecres.wholphin.ui.playback.playable
import com.github.damontecres.wholphin.ui.playback.scale
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.ScrollToTopBringIntoViewSpec
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration

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
    val state by viewModel.state.collectAsState()
    val featured = state.featured
    val trending = state.trending
    val trendingMissing = state.trendingMissing
    val themed = state.themed
    val because = state.because
    val becauseMore = state.becauseMore
    val collections = state.collections
    val mood = state.mood
    val moodRow = state.moodRow
    val studios = state.studios
    val newest = state.newest
    val myList = state.myList
    val genreRows = state.genreRows
    val previewPlaying by viewModel.previewPlaying.collectAsState()
    LifecycleStartEffect(Unit) {
        onStopOrDispose { viewModel.previewTrailer(null) }
    }
    val loading = state.loadingState
    val refreshing = state.refreshState
    val homeRows = state.homeRows

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

            val playlistState by playlistViewModel.playlistState.collectAsState()
            var position by rememberPosition()

            val onFocusPosition = remember { { it: RowColumn -> position = it } }
            val currentHomePrefs by rememberUpdatedState(preferences.appPreferences.homePagePreferences)
            val onClickItem =
                remember {
                    { clickedPosition: RowColumn, item: BaseItem ->
                        position = clickedPosition
                        if (currentHomePrefs.clickToPlay &&
                            homeRows.getOrNull(clickedPosition.row)?.isContinueWatchingNextUp == true
                        ) {
                            viewModel.navigationManager.navigateTo(Destination.Playback(item))
                        } else {
                            viewModel.navigationManager.navigateTo(item.destination())
                        }
                    }
                }
            val onLongClickItem =
                remember {
                    { clickedPosition: RowColumn, item: BaseItem ->
                        position = clickedPosition
                        val row =
                            (homeRows.getOrNull(clickedPosition.row) as? HomeRowLoadingState.Success)
                        val canRemoveContinueWatching =
                            row?.rowType is HomeRowConfig.ContinueWatching || row?.rowType is HomeRowConfig.ContinueWatchingCombined
                        val canRemoveNextUp =
                            row?.rowType is HomeRowConfig.NextUp || row?.rowType is HomeRowConfig.ContinueWatchingCombined
                        showContextMenu =
                            ContextMenu.ForBaseItem(
                                fromLongClick = true,
                                item = item,
                                chosenStreams = null,
                                showGoTo = true,
                                showStreamChoices = false,
                                canDelete =
                                    viewModel.canDelete(
                                        item,
                                        preferences.appPreferences,
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
                                            viewModel.deleteItem(position, it)
                                        },
                                        onChooseVersion = { _, _ ->
                                            // Not supported on this page
                                        },
                                        onChooseTracks = {
                                            // Not supported on this page
                                        },
                                        onShowOverview = {
                                            overviewDialog = ItemDetailsDialogInfo(it)
                                        },
                                        onClearChosenStreams = {},
                                        onClickRemoveFromNextUp = viewModel::removeFromNextUp,
                                    ),
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
                trendingMissing = trendingMissing,
                onClickDiscover = { item ->
                    viewModel.navigationManager.navigateTo(item.destination)
                },
                onUpdateDiscoverBackdrop = viewModel::updateBackdrop,
                themed = themed,
                because = because,
                becauseMore = becauseMore,
                collections = collections,
                mood = mood,
                moodRow = moodRow,
                onClickMood = viewModel::pickMood,
                studios = studios,
                newest = newest,
                myList = myList,
                genreRows = genreRows,
                onClickStudio = viewModel::openStudio,
                previewPlayer = viewModel.previewPlayer,
                previewPlaying = previewPlaying,
                onHeroItemShown = viewModel::previewTrailer,
                modifier = modifier,
            )
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

private const val HERO_ROTATE_MS = 8_000L

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
    trendingMissing: List<DiscoverItem> = emptyList(),
    themed: ThemedRow? = null,
    because: ThemedRow? = null,
    becauseMore: ThemedRow? = null,
    collections: ThemedRow? = null,
    mood: HomeMood? = null,
    moodRow: ThemedRow? = null,
    onClickMood: ((HomeMood) -> Unit)? = null,
    studios: List<StudioTile> = emptyList(),
    newest: ThemedRow? = null,
    myList: ThemedRow? = null,
    genreRows: List<ThemedRow> = emptyList(),
    onClickStudio: (StudioTile) -> Unit = {},
    previewPlayer: Player? = null,
    previewPlaying: Boolean = false,
    onHeroItemShown: (BaseItem?) -> Unit = {},
    onClickDiscover: (DiscoverItem) -> Unit = {},
    onUpdateDiscoverBackdrop: (DiscoverItem) -> Unit = {},
) {
    // An item focused in the trending rows, which aren't part of homeRows
    var extraFocus by remember { mutableStateOf<Any?>(null) }
    var heroIndex by remember { mutableIntStateOf(0) }
    var heroFocusedTv by remember { mutableStateOf(false) }
    val isTv = rememberIsTv()
    // Phones have no focus, so there the banner counts as "selected" while it is still on screen
    val heroOnScreen by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex <= 1 } }
    val heroFocused = if (isTv) heroFocusedTv else featured.isNotEmpty() && heroOnScreen
    val heroFocusRequester = remember { FocusRequester() }
    val showHero = featured.isNotEmpty()
    // Rows start after the banner, and on phones also after the header that scrolls with the page
    val heroOffset = (if (showHero) 1 else 0) + (if (isTv) 0 else 1)
    val currentHeroItem = if (heroFocused) featured.getOrNull(heroIndex)?.item else null
    LaunchedEffect(currentHeroItem?.id) {
        onHeroItemShown(currentHeroItem)
    }
    LaunchedEffect(featured.size, heroFocused, previewPlaying) {
        // Rotate the banner while it is focused, but let a trailer finish first
        if (heroFocused && featured.size > 1 && !previewPlaying) {
            while (true) {
                delay(HERO_ROTATE_MS)
                heroIndex = (heroIndex + 1) % featured.size
            }
        }
    }
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

    if (takeFocus) {
        LaunchedEffect(homeRows, showHero) {
            if (!firstFocused && showHero && position.row < 0) {
                // Start on the banner, like other streaming apps
                heroFocusRequester.tryRequestFocus()
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
    // Benflix: deep in the page, Back first jumps to the top. Pressing it again goes to the top menu
    var contentHasFocus by remember { mutableStateOf(false) }
    val backScope = rememberCoroutineScope()
    val atTop = if (showHero) heroFocusedTv else position.row <= 0
    BackHandler(enabled = isTv && contentHasFocus && !atTop) {
        backScope.launch {
            listState.scrollToItem(0)
            if (showHero) {
                heroFocusRequester.tryRequestFocus()
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
                            rowFocusRequesters.getOrNull(currentPosition.row)?.tryRequestFocus()
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
                                // Phones: the banner is a rounded card with the whole wide picture
                                PhoneHeroCard(
                                    featured = phoneFeatured,
                                    onClick = { onClickFeaturedInfo(phoneFeatured.item) },
                                )
                            } else {
                                Column { pageHeader() }
                            }
                        }
                    }
                    if (showHero) {
                        item(key = "hero") {
                            HomeHero(
                                featured = featured,
                                index = heroIndex.coerceIn(0, featured.lastIndex),
                                onClickPlay = onClickFeaturedPlay,
                                onClickInfo = onClickFeaturedInfo,
                                onClickFavorite = onClickFeaturedFavorite,
                                onClickSurprise = onClickSurprise,
                                onFocusChanged = { heroFocusedTv = it },
                                focusRequester = heroFocusRequester,
                            )
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
                    // Benflix rows, Netflix style, after Continue watching
                    if (trending.isNotEmpty()) {
                        item(key = "top10") {
                            Top10Row(
                                items = trending,
                                onClickItem = onClickTrending,
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
                    if (because != null) {
                        item(key = "because") {
                            PosterRow(
                                title = because.title,
                                items = because.items,
                                onClickItem = onClickTrending,
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
                    if (becauseMore != null) {
                        item(key = "becauseMore") {
                            PosterRow(
                                title = becauseMore.title,
                                items = becauseMore.items,
                                onClickItem = onClickTrending,
                                onFocusItem = { extraFocus = it },
                                modifier =
                                    Modifier
                                        .padding(bottom = 8.dp)
                                        .onFocusChanged {
                                            if (!it.hasFocus && extraFocus in becauseMore.items) extraFocus = null
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
                    if (collections != null) {
                        item(key = "collections") {
                            PosterRow(
                                title = collections.title,
                                items = collections.items,
                                onClickItem = onClickTrending,
                                onFocusItem = { extraFocus = it },
                                modifier =
                                    Modifier
                                        .padding(bottom = 8.dp)
                                        .onFocusChanged {
                                            if (!it.hasFocus && extraFocus in collections.items) extraFocus = null
                                        },
                            )
                        }
                    }
                    genreRows.forEachIndexed { index, row ->
                        item(key = "genre$index") {
                            PosterRow(
                                title = row.title,
                                items = row.items,
                                onClickItem = onClickTrending,
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
                    // Studios sit under all of the library rows
                    if (studios.isNotEmpty()) {
                        item(key = "studios") {
                            StudioRow(
                                tiles = studios,
                                onClick = onClickStudio,
                                modifier = Modifier.padding(bottom = 8.dp),
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
    }
}

@Composable
fun HomePageHeader(
    item: BaseItem?,
    showLogo: Boolean,
    modifier: Modifier = Modifier,
) {
    val isEpisode = item?.type == BaseItemKind.EPISODE
    val title =
        remember(item?.id) {
            when (item?.type) {
                BaseItemKind.EPISODE,
                BaseItemKind.SEASON,
                -> item.data.seriesName

                else -> item?.title
            }
        }
    val subtitle =
        remember(item?.id) {
            when (item?.type) {
                BaseItemKind.SEASON,
                BaseItemKind.EPISODE,
                -> item.name

                else -> null
            }
        }
    HomePageHeader(
        title = title,
        subtitle = subtitle,
        overview = item?.data?.overview,
        overviewTwoLines = isEpisode,
        quickDetails = item?.ui?.quickDetails,
        timeRemaining = item?.timeRemainingOrRuntime,
        endsAt = item?.data?.endDate,
        showLogo = showLogo,
        logoImageUrl = rememberLogoUrl(item),
        modifier = modifier,
    )
}

@Composable
fun HomePageHeader(
    title: String?,
    subtitle: String?,
    overview: String?,
    overviewTwoLines: Boolean,
    quickDetails: QuickDetailsData?,
    timeRemaining: Duration?,
    endsAt: DateTime?,
    showLogo: Boolean,
    logoImageUrl: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        // Benflix: a big title logo on the banner, like Netflix's billboard
        TitleOrLogo(
            title = title,
            logoImageUrl = logoImageUrl,
            showLogo = showLogo,
            logoHeight = if (rememberIsTv()) 100.dp else 72.dp,
            logoMaxWidth = 420.dp,
            modifier = Modifier.fillMaxWidth(.75f),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth(.6f),
        ) {
            if (subtitle != null) {
                EpisodeName(subtitle)
            }
            QuickDetails(quickDetails, timeRemaining, endsAt = endsAt)
            val overviewModifier =
                Modifier
                    .padding(0.dp)
                    .height(48.dp + if (!overviewTwoLines) 12.dp else 0.dp)
                    .width(400.dp)
            if (overview.isNotNullOrBlank()) {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (overviewTwoLines) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = overviewModifier,
                )
            } else {
                Spacer(overviewModifier)
            }
        }
    }
}

@Composable
fun HomePageCardContent(
    index: Int,
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    viewOptions: HomeRowViewOptions,
    modifier: Modifier,
) {
    when (item?.type) {
        BaseItemKind.GENRE -> {
            GenreCard(
                genreId = item.id,
                name = item.name,
                imageUrl = item.imageUrlOverride,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier.height(viewOptions.heightDp.dp),
            )
        }

        BaseItemKind.STUDIO -> {
            StudioCard(
                studioId = item.id,
                name = item.name,
                imageUrl = item.imageUrlOverride,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier.height(viewOptions.heightDp.dp),
            )
        }

        else -> {
            val imageType =
                remember(item, viewOptions) {
                    if (item?.type == BaseItemKind.EPISODE) {
                        viewOptions.episodeImageType.imageType
                    } else {
                        viewOptions.imageType.imageType
                    }
                }
            val ratio =
                remember(item, viewOptions) {
                    if (item?.type == BaseItemKind.EPISODE) {
                        viewOptions.episodeAspectRatio.ratio
                    } else {
                        viewOptions.aspectRatio.ratio
                    }
                }
            val scale =
                remember(item, viewOptions) {
                    if (item?.type == BaseItemKind.EPISODE) {
                        viewOptions.episodeContentScale.scale
                    } else {
                        viewOptions.contentScale.scale
                    }
                }
            if (viewOptions.showTitles) {
                BannerCardWithTitle(
                    title = item?.title,
                    subtitle = item?.subtitle,
                    item = item,
                    aspectRatio = ratio,
                    imageType = imageType,
                    imageContentScale = scale,
                    cornerText = null,
                    played = item?.data?.userData?.played ?: false,
                    favorite = item?.favorite ?: false,
                    playPercent =
                        item?.data?.userData?.playedPercentage
                            ?: 0.0,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    modifier = modifier,
                    cardHeight = viewOptions.heightDp.dp,
                    useSeriesForPrimary = viewOptions.useSeries,
                    titleOnlyWhenFocused = true,
                    growOnFocus = true,
                )
            } else {
                val interactionSource = remember { MutableInteractionSource() }
                val grown by rememberGrowOnFocus(interactionSource, canGrow(item, ratio))
                BannerCard(
                    name = item?.data?.seriesName ?: item?.name,
                    item = item,
                    aspectRatio = ratio,
                    imageType = imageType,
                    imageContentScale = scale,
                    cornerText = null,
                    played = item?.data?.userData?.played ?: false,
                    favorite = item?.favorite ?: false,
                    playPercent =
                        item?.data?.userData?.playedPercentage
                            ?: 0.0,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    modifier = modifier,
                    interactionSource = interactionSource,
                    cardHeight = viewOptions.heightDp.dp,
                    useSeriesForPrimary = viewOptions.useSeries,
                    grown = grown,
                )
            }
        }
    }
}

@Composable
fun HomePageViewMoreCard(
    isEpisode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    viewOptions: HomeRowViewOptions,
    modifier: Modifier,
) {
    val aspectRatio =
        remember(isEpisode, viewOptions) {
            if (isEpisode) {
                viewOptions.episodeAspectRatio
            } else {
                viewOptions.aspectRatio
            }
        }
    ViewMoreCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        aspectRatio = aspectRatio,
        size = DpSize(height = viewOptions.heightDp.dp, width = Dp.Unspecified),
        showTitle = viewOptions.showTitles,
    )
}

/**
 * A shorter header than other pages, since the top bar already gives space above it,
 * so the next row of cards peeks in below the focused row
 */
private val HomeHeaderModifier =
    Modifier
        .padding(top = 4.dp, bottom = 12.dp, start = HeaderUtils.startPadding)
        .height(150.dp)
