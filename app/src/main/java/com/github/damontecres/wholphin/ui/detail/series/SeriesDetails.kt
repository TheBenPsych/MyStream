package com.github.damontecres.wholphin.ui.detail.series

import android.content.res.Resources
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ExtrasItem
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.data.model.studioNames
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.ShowProgress
import com.github.damontecres.wholphin.services.TrailerService
import com.github.damontecres.wholphin.services.words
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.RequestOrRestoreFocus
import com.github.damontecres.wholphin.ui.cards.ExtrasRow
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.cards.PersonRow
import com.github.damontecres.wholphin.ui.cards.SeasonCard
import com.github.damontecres.wholphin.ui.components.ConfirmDeleteDialog
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.ui.components.ContextMenu
import com.github.damontecres.wholphin.ui.components.ContextMenuActions
import com.github.damontecres.wholphin.ui.components.ContextMenuDialog
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.DislikedColor
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton
import com.github.damontecres.wholphin.ui.components.GenreText
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.LikedColor
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.LocalFlatButtons
import com.github.damontecres.wholphin.ui.components.Optional
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.PersonContextActions
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.components.SubDubTag
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.TabRow
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.components.TitleOrLogo
import com.github.damontecres.wholphin.ui.components.TrailerButton
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.detail.DetailsTrailer
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.detail.contentAlpha
import com.github.damontecres.wholphin.ui.detail.rememberTrailerCinemaState
import com.github.damontecres.wholphin.ui.detail.wakeOnInput
import com.github.damontecres.wholphin.ui.discover.DiscoverRow
import com.github.damontecres.wholphin.ui.discover.DiscoverRowData
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.util.ResStringProvider
import com.github.damontecres.wholphin.ui.util.scrollAsOneBlock
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.DiscoverRequestType
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.serializer.toUUID
import java.util.UUID
import kotlin.time.Duration

@Composable
fun SeriesDetails(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: SeriesViewModel =
        hiltViewModel<SeriesViewModel, SeriesViewModel.Factory>(
            creationCallback = {
                it.create(destination.itemId, null, SeriesPageType.DETAILS)
            },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val state by viewModel.state.collectAsState()
    val playlistState by playlistViewModel.playlistState.collectAsState()

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var showContextMenu by remember { mutableStateOf<ContextMenu?>(null) }

    var showWatchConfirmation by remember { mutableStateOf(false) }
    var showFixMatch by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }

    val contextActions =
        remember {
            ContextMenuActions(
                navigateTo = viewModel::navigateTo,
                onClickWatch = { itemId, watched ->
                    if (itemId == destination.itemId) {
                        // Confirm if marking whole series
                        showWatchConfirmation = true
                    } else {
                        viewModel.setWatched(itemId, watched, null)
                    }
                },
                onClickFavorite = { itemId, favorite ->
                    viewModel.setFavorite(itemId, favorite, null)
                },
                onClickAddPlaylist = { itemId ->
                    playlistViewModel.loadPlaylists()
                    showPlaylistDialog = Optional.present(itemId)
                },
                onSendMediaInfo = viewModel.serverReportService::sendMediaReportFor,
                onDeleteItem = viewModel::deleteItem,
                onChooseVersion = { item, source ->
                    viewModel.savePlayVersion(
                        item,
                        source.id!!.toUUID(),
                    )
                },
                onChooseTracks = { result ->
                    viewModel.saveTrackSelection(
                        result.item,
                        result.itemPlayback,
                        result.trackIndex,
                        result.streamType,
                    )
                },
                onShowOverview = { overviewDialog = ItemDetailsDialogInfo(it) },
                onClearChosenStreams = {},
            )
        }

    LifecycleResumeEffect(destination.itemId) {
        viewModel.refresh()

        onPauseOrDispose { }
    }

    when (val st = state.series) {
        is DataLoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        is DataLoadingState.Success<BaseItem> -> {
            LifecycleResumeEffect(destination.itemId) {
                viewModel.onResumePage()

                onPauseOrDispose {}
            }
            val series = st.data
            val played = series.data.userData?.played ?: false
            LaunchedEffect(state.seasons) {
                if (state.inlineSeasonIndex < 0 && state.seasons.isNotEmpty()) {
                    val firstUnwatched =
                        state.seasons.indexOfFirst { it?.data?.userData?.played == false }
                    viewModel.showSeasonEpisodes(firstUnwatched.coerceAtLeast(0))
                }
            }
            SeriesDetailsContent(
                preferences = preferences,
                series = series,
                seasons = state.seasons,
                inlineSeasonIndex = state.inlineSeasonIndex,
                inlineEpisodes = state.inlineEpisodes,
                onPickSeason = viewModel::showSeasonEpisodes,
                fanFavourites = state.fanFavourites,
                subDub = state.subDub,
                progress = state.progress,
                rewatchOnClick = viewModel::rewatch,
                isAdmin = state.isAdmin,
                fixMatchOnClick = { showFixMatch = true },
                onPlayEpisode = { viewModel.navigateTo(Destination.Playback(it)) },
                trailers = state.trailers,
                extras = state.extras,
                people = state.people,
                similar = state.similar,
                played = played,
                favorite = series.data.userData?.isFavorite ?: false,
                likes = series.data.userData?.likes,
                canDelete = state.canDeleteSeries,
                modifier = modifier,
                onClickItem = { index, item ->
                    viewModel.navigateTo(item.destination())
                },
                onClickPerson = {
                    viewModel.navigateTo(
                        Destination.MediaItem(
                            it.id,
                            BaseItemKind.PERSON,
                        ),
                    )
                },
                onLongClickItem = { index, season ->
                    showContextMenu =
                        ContextMenu.ForBaseItem(
                            fromLongClick = true,
                            item = season,
                            chosenStreams = null,
                            showGoTo = true,
                            showStreamChoices = false,
                            canDelete = viewModel.canDelete(season, preferences.appPreferences),
                            canRemoveContinueWatching = false,
                            canRemoveNextUp = false,
                            actions = contextActions,
                        )
                },
                overviewOnClick = {
                    overviewDialog = ItemDetailsDialogInfo(series)
                },
                playOnClick = { shuffle ->
                    if (shuffle) {
                        viewModel.navigateTo(
                            Destination.PlaybackList(
                                itemId = series.id,
                                shuffle = true,
                            ),
                        )
                    } else {
                        viewModel.playNextUp()
                    }
                },
                watchOnClick = { showWatchConfirmation = true },
                favoriteOnClick = {
                    val favorite = series.data.userData?.isFavorite ?: false
                    viewModel.setFavorite(series.id, !favorite, null)
                },
                rateOnClick = { viewModel.setLikes(series.id, it) },
                trailerOnClick = {
                    TrailerService.onClick(context, it, viewModel::navigateTo)
                },
                onClickExtra = { _, extra ->
                    viewModel.navigateTo(extra.destination)
                },
                discoverSeries = state.discoverSeries,
                onClickDiscoverSeries = {
                    state.discoverSeries?.let {
                        viewModel.navigateTo(Destination.DiscoveredItem(it))
                    }
                },
                discovered = state.discovered,
                onClickDiscover = { index, item ->
                    viewModel.navigateTo(item.destination)
                },
                onShowContextMenu = {
                    showContextMenu = it
                },
                actions = contextActions,
            )
            if (showFixMatch) {
                com.github.damontecres.wholphin.ui.detail.FixMatchDialog(
                    item = series,
                    onDismissRequest = { showFixMatch = false },
                    onFixed = { showFixMatch = false },
                )
            }
            if (showWatchConfirmation) {
                ConfirmDialog(
                    title = series.name ?: "",
                    body =
                        stringResource(if (played) R.string.mark_entire_series_as_unplayed else R.string.mark_entire_series_as_played),
                    onCancel = {
                        showWatchConfirmation = false
                    },
                    onConfirm = {
                        viewModel.setWatchedSeries(!played)
                        showWatchConfirmation = false
                    },
                )
            }
        }
    }
    showContextMenu?.let { contextMenu ->
        ContextMenuDialog(
            onDismissRequest = { showContextMenu = null },
            getMediaSource = viewModel.streamChoiceService::chooseSource,
            contextMenu = contextMenu,
            preferredSubtitleLanguage = null,
        )
    }
    overviewDialog?.let { info ->
        ItemDetailsDialog(
            info = info,
            showFilePath = false,
            onDismissRequest = { overviewDialog = null },
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

private const val HEADER_ROW = 0
private const val SEASONS_ROW = HEADER_ROW + 1
private const val PEOPLE_ROW = SEASONS_ROW + 1
private const val TRAILER_ROW = PEOPLE_ROW + 1
private const val EXTRAS_ROW = TRAILER_ROW + 1
private const val SIMILAR_ROW = EXTRAS_ROW + 1
private const val DISCOVER_ROW = SIMILAR_ROW + 1
private const val EPISODES_ROW = DISCOVER_ROW + 1
private const val FAVES_ROW = EPISODES_ROW + 1

@Composable
fun SeriesDetailsContent(
    preferences: UserPreferences,
    series: BaseItem,
    seasons: List<BaseItem?>,
    similar: List<BaseItem>,
    trailers: List<Trailer>,
    extras: List<ExtrasItem>,
    people: List<Person>,
    discovered: List<DiscoverItem>,
    played: Boolean,
    favorite: Boolean,
    likes: Boolean?,
    canDelete: Boolean,
    onClickItem: (Int, BaseItem) -> Unit,
    onClickPerson: (Person) -> Unit,
    onLongClickItem: (Int, BaseItem) -> Unit,
    overviewOnClick: () -> Unit,
    playOnClick: (Boolean) -> Unit,
    watchOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    rateOnClick: (Boolean?) -> Unit,
    trailerOnClick: (Trailer) -> Unit,
    onClickExtra: (Int, ExtrasItem) -> Unit,
    onShowContextMenu: (ContextMenu) -> Unit,
    actions: ContextMenuActions,
    onClickDiscover: (Int, DiscoverItem) -> Unit,
    discoverSeries: DiscoverItem?,
    onClickDiscoverSeries: () -> Unit,
    modifier: Modifier = Modifier,
    inlineSeasonIndex: Int = -1,
    inlineEpisodes: List<BaseItem> = emptyList(),
    onPickSeason: (Int) -> Unit = {},
    fanFavourites: List<BaseItem> = emptyList(),
    onPlayEpisode: (BaseItem) -> Unit = {},
    subDub: String? = null,
    progress: ShowProgress? = null,
    rewatchOnClick: () -> Unit = {},
    isAdmin: Boolean = false,
    fixMatchOnClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    var position by rememberInt()
    val focusRequesters = remember { List(FAVES_ROW + 1) { FocusRequester() } }
    // Tabs under the seasons, like the movie page: one section at a time keeps the page calm
    val detailSections =
        buildList {
            if (similar.isNotEmpty()) add(SIMILAR_ROW)
            if (people.isNotEmpty()) add(PEOPLE_ROW)
            if (extras.isNotEmpty()) add(EXTRAS_ROW)
            if (discovered.isNotEmpty()) add(DISCOVER_ROW)
        }
    var selectedSection by rememberInt(SIMILAR_ROW)
    val currentSection = if (selectedSection in detailSections) selectedSection else detailSections.firstOrNull() ?: -1
    val sectionTabs =
        remember(detailSections) {
            detailSections.map {
                TabDetails(
                    when (it) {
                        SIMILAR_ROW -> R.string.more_like_this
                        PEOPLE_ROW -> R.string.cast_and_crew
                        EXTRAS_ROW -> R.string.extras_title
                        else -> R.string.discover
                    },
                )
            }
        }
    val playFocusRequester = remember { FocusRequester() }
    RequestOrRestoreFocus(focusRequesters.getOrNull(position))

    val listState = rememberLazyListState()
    val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val cinema = rememberTrailerCinemaState()
    val contentAlpha = cinema.contentAlpha()
    Box(
        modifier = modifier.wakeOnInput(cinema),
    ) {
        DetailsTrailer(item = series, active = atTop, cinemaState = cinema)
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = contentAlpha },
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier,
            ) {
                item {
                    // Benflix: title, info and buttons scroll as one block (no jumping while moving along the buttons)
                    Column(modifier = Modifier.scrollAsOneBlock()) {
                        SeriesDetailsHeader(
                            series = series,
                            showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                            overviewOnClick = overviewOnClick,
                            bringIntoViewRequester = bringIntoViewRequester,
                            subDub = subDub,
                            progressText = remember(progress) { progress?.words() },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(bringIntoViewRequester)
                                    .padding(top = HeaderUtils.topPadding, bottom = 16.dp),
                        )
                        // Benflix look A: flat buttons. Play, Shuffle, Mark watched and Trailer stay
                        // on the page; the rest (like, favourite, delete, fix match...) are under More
                        val bringButtonsIntoView: (androidx.compose.ui.focus.FocusState) -> Unit = {
                            if (it.isFocused) {
                                scope.launch(ExceptionHandler()) {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        }
                        var showMore by remember { mutableStateOf(false) }
                        var showDeleteConfirm by remember { mutableStateOf(false) }
                        CompositionLocalProvider(LocalFlatButtons provides true) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier =
                                    Modifier
                                        .padding(start = HeaderUtils.startPadding)
                                        .focusRequester(focusRequesters[HEADER_ROW])
                                        .focusRestorer(playFocusRequester)
                                        .focusGroup()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(bottom = 16.dp, end = 16.dp),
                            ) {
                                ExpandablePlayButton(
                                    title = R.string.play,
                                    resume = Duration.ZERO,
                                    icon = Icons.Default.PlayArrow,
                                    primary = true,
                                    onClick = {
                                        position = HEADER_ROW
                                        playOnClick.invoke(false)
                                    },
                                    modifier =
                                        Modifier
                                            .focusRequester(playFocusRequester)
                                            .onFocusChanged(bringButtonsIntoView),
                                )
                                // Benflix: watched it all? Start again from episode 1
                                if (played && progress?.firstEpisodeId != null) {
                                    ExpandableFaButton(
                                        title = R.string.benflix_rewatch,
                                        iconStringRes = R.string.fa_rotate_left,
                                        onClick = {
                                            position = HEADER_ROW
                                            rewatchOnClick.invoke()
                                        },
                                        modifier = Modifier.onFocusChanged(bringButtonsIntoView),
                                    )
                                }
                                ExpandableFaButton(
                                    title = R.string.benflix_random_episodes,
                                    iconStringRes = R.string.fa_shuffle,
                                    onClick = {
                                        position = HEADER_ROW
                                        playOnClick.invoke(true)
                                    },
                                    modifier = Modifier.onFocusChanged(bringButtonsIntoView),
                                )
                                ExpandableFaButton(
                                    title = if (played) R.string.mark_unwatched else R.string.mark_watched,
                                    iconStringRes = if (played) R.string.fa_eye else R.string.fa_eye_slash,
                                    onClick = watchOnClick,
                                    modifier = Modifier.onFocusChanged(bringButtonsIntoView),
                                )
                                if (trailers.isNotEmpty()) {
                                    TrailerButton(
                                        trailers = trailers,
                                        trailerOnClick = trailerOnClick,
                                        modifier = Modifier.onFocusChanged(bringButtonsIntoView),
                                    )
                                }
                                ExpandablePlayButton(
                                    title = R.string.more,
                                    resume = Duration.ZERO,
                                    icon = Icons.Default.MoreVert,
                                    onClick = {
                                        position = HEADER_ROW
                                        showMore = true
                                    },
                                    modifier = Modifier.onFocusChanged(bringButtonsIntoView),
                                )
                            }
                        }
                        if (showMore) {
                            val moreItems =
                                buildList {
                                    add(
                                        DialogItem(
                                            text = if (likes == true) R.string.liked else R.string.like,
                                            iconStringRes = R.string.fa_thumbs_up,
                                            iconColor = if (likes == true) LikedColor else Color.Unspecified,
                                            dismissOnClick = true,
                                        ) { rateOnClick.invoke(if (likes == true) null else true) },
                                    )
                                    add(
                                        DialogItem(
                                            text = if (likes == false) R.string.disliked else R.string.dislike,
                                            iconStringRes = R.string.fa_thumbs_down,
                                            iconColor = if (likes == false) DislikedColor else Color.Unspecified,
                                            dismissOnClick = true,
                                        ) { rateOnClick.invoke(if (likes == false) null else false) },
                                    )
                                    add(
                                        DialogItem(
                                            text = if (favorite) R.string.remove_favorite else R.string.add_favorite,
                                            iconStringRes = R.string.fa_heart,
                                            iconColor = if (favorite) Color.Red else Color.Unspecified,
                                            dismissOnClick = true,
                                            onClick = favoriteOnClick,
                                        ),
                                    )
                                    if (discoverSeries != null) {
                                        add(
                                            DialogItem(
                                                text = R.string.discover,
                                                iconStringRes = R.string.fa_magnifying_glass_plus,
                                                dismissOnClick = true,
                                                onClick = onClickDiscoverSeries,
                                            ),
                                        )
                                    }
                                    // Benflix (admins): the show got matched to the wrong one on the internet
                                    if (isAdmin) {
                                        add(
                                            DialogItem(
                                                text = R.string.benflix_fix_match,
                                                iconStringRes = R.string.fa_magnifying_glass,
                                                dismissOnClick = true,
                                                onClick = fixMatchOnClick,
                                            ),
                                        )
                                    }
                                    if (canDelete) {
                                        add(
                                            DialogItem(
                                                text = context.getString(R.string.delete),
                                                icon = Icons.Default.Delete,
                                                iconColor = Color.Red.copy(alpha = .8f),
                                                dismissOnClick = true,
                                            ) { showDeleteConfirm = true },
                                        )
                                    }
                                }
                            DialogPopup(
                                showDialog = true,
                                title = series.title ?: stringResource(R.string.more),
                                dialogItems = moreItems,
                                onDismissRequest = { showMore = false },
                                dismissOnClick = true,
                                waitToLoad = false,
                            )
                        }
                        if (showDeleteConfirm) {
                            ConfirmDeleteDialog(
                                itemTitle = series.title ?: "",
                                onCancel = { showDeleteConfirm = false },
                                onConfirm = {
                                    showDeleteConfirm = false
                                    actions.onDeleteItem.invoke(series)
                                },
                            )
                        }
                    }
                }
                item {
                    PreviouslyOn(
                        seriesId = series.id,
                        modifier = Modifier.padding(start = HeaderUtils.startPadding),
                    )
                }
                // Benflix: watched it all and a newer season is out? Ask for it
                item {
                    NextSeasonAsk(
                        seriesId = series.id,
                        modifier = Modifier.padding(horizontal = HeaderUtils.startPadding),
                    )
                }
                // Netflix-style: pick a season, its episodes show right here
                if (inlineEpisodes.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (seasons.size > 1) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(horizontal = HeaderUtils.startPadding),
                                ) {
                                    items(seasons.size) { index ->
                                        val picked = index == inlineSeasonIndex
                                        TextButton(
                                            onClick = { onPickSeason.invoke(index) },
                                        ) {
                                            Text(
                                                text = seasons[index]?.name ?: "${index + 1}",
                                                fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
                                                textDecoration = if (picked) TextDecoration.Underline else null,
                                            )
                                        }
                                    }
                                }
                            }
                            ItemRow(
                                title = stringResource(R.string.episodes),
                                items = inlineEpisodes,
                                onClickItem = { index, item ->
                                    position = EPISODES_ROW
                                    onClickItem.invoke(index, item)
                                },
                                onLongClickItem = { _, _ -> },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequesters[EPISODES_ROW]),
                                cardContent = @Composable { _, item, mod, onClick, onLongClick ->
                                    SeasonCard(
                                        item = item,
                                        onClick = onClick,
                                        onLongClick = onLongClick,
                                        imageHeight = Cards.heightEpisode,
                                        imageWidth = Dp.Unspecified,
                                        aspectRatio = AspectRatios.WIDE,
                                        showImageOverlay = true,
                                        modifier = mod,
                                    )
                                },
                            )
                        }
                    }
                }
                // Benflix: fan favourite episodes; pick one and it plays
                if (fanFavourites.isNotEmpty()) {
                    item {
                        ItemRow(
                            title = stringResource(R.string.benflix_fan_favourites),
                            items = fanFavourites,
                            onClickItem = { _, item ->
                                position = FAVES_ROW
                                onPlayEpisode.invoke(item)
                            },
                            onLongClickItem = { _, _ -> },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequesters[FAVES_ROW]),
                            cardContent = @Composable { _, item, mod, onClick, onLongClick ->
                                SeasonCard(
                                    item = item,
                                    onClick = onClick,
                                    onLongClick = onLongClick,
                                    imageHeight = Cards.heightEpisode,
                                    imageWidth = Dp.Unspecified,
                                    aspectRatio = AspectRatios.WIDE,
                                    showImageOverlay = true,
                                    modifier = mod,
                                )
                            },
                        )
                    }
                }
                item {
                    ItemRow(
                        title = stringResource(R.string.tv_seasons) + " (${seasons.size})",
                        items = seasons,
                        onClickItem = { index, item ->
                            position = SEASONS_ROW
                            onClickItem.invoke(index, item)
                        },
                        onLongClickItem = { index, item ->
                            position = SEASONS_ROW
                            onLongClickItem.invoke(index, item)
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequesters[SEASONS_ROW]),
                        cardContent = @Composable { index, item, mod, onClick, onLongClick ->
                            SeasonCard(
                                item = item,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                imageHeight = Cards.height2x3,
                                imageWidth = Dp.Unspecified,
                                showImageOverlay = true,
                                modifier = mod,
                            )
                        },
                    )
                }
                // Benflix: anime movies in watch order, fan genres, characters and voices (anime only)
                item {
                    AnimeRows(series)
                }
                if (detailSections.size > 1) {
                    item {
                        TabRow(
                            selectedTabIndex = detailSections.indexOf(currentSection).coerceAtLeast(0),
                            tabs = sectionTabs,
                            onClick = { selectedSection = detailSections[it] },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                // Netflix order: More like this right after the seasons
                if (currentSection == SIMILAR_ROW && similar.isNotEmpty()) {
                    item {
                        ItemRow(
                            title = stringResource(R.string.more_like_this),
                            items = similar,
                            onClickItem = { index, item ->
                                position = SIMILAR_ROW
                                onClickItem.invoke(index, item)
                            },
                            onLongClickItem = { index, item ->
                                position = SIMILAR_ROW
                                onShowContextMenu.invoke(
                                    ContextMenu.ForBaseItem(
                                        fromLongClick = true,
                                        item = item,
                                        chosenStreams = null,
                                        showGoTo = true,
                                        showStreamChoices = false,
                                        canDelete = false,
                                        canRemoveContinueWatching = false,
                                        canRemoveNextUp = false,
                                        actions = actions,
                                    ),
                                )
                            },
                            cardContent = { index, item, mod, onClick, onLongClick ->
                                SeasonCard(
                                    item = item,
                                    onClick = onClick,
                                    onLongClick = onLongClick,
                                    modifier = mod,
                                    showImageOverlay = true,
                                    imageHeight = Cards.height2x3,
                                    imageWidth = Dp.Unspecified,
                                )
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequesters[SIMILAR_ROW]),
                        )
                    }
                }
                if (currentSection == PEOPLE_ROW && people.isNotEmpty()) {
                    item {
                        PersonRow(
                            people = people,
                            onClick = {
                                position = PEOPLE_ROW
                                onClickPerson.invoke(it)
                            },
                            onLongClick = { index, person ->
                                position = PEOPLE_ROW
                                onShowContextMenu.invoke(
                                    ContextMenu.ForPerson(
                                        fromLongClick = true,
                                        person = person,
                                        actions =
                                            PersonContextActions(
                                                navigateTo = actions.navigateTo,
                                                onClickFavorite = actions.onClickFavorite,
                                            ),
                                    ),
                                )
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequesters[PEOPLE_ROW]),
                        )
                    }
                }
                if (currentSection == EXTRAS_ROW && extras.isNotEmpty()) {
                    item {
                        ExtrasRow(
                            extras = extras,
                            onClickItem = { index, item ->
                                position = EXTRAS_ROW
                                onClickExtra.invoke(index, item)
                            },
                            onLongClickItem = { _, _ -> },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequesters[EXTRAS_ROW]),
                        )
                    }
                }
                if (currentSection == DISCOVER_ROW && discovered.isNotEmpty()) {
                    item {
                        DiscoverRow(
                            row =
                                DiscoverRowData(
                                    ResStringProvider(R.string.discover),
                                    DataLoadingState.Success(discovered),
                                    type = DiscoverRequestType.UNKNOWN,
                                ),
                            onClickItem = { index: Int, item: DiscoverItem ->
                                position = DISCOVER_ROW
                                onClickDiscover.invoke(index, item)
                            },
                            onLongClickItem = { _, _ -> },
                            onCardFocus = {},
                            focusRequester = focusRequesters[DISCOVER_ROW],
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SeriesDetailsHeader(
    series: BaseItem,
    showLogo: Boolean,
    overviewOnClick: () -> Unit,
    bringIntoViewRequester: BringIntoViewRequester,
    modifier: Modifier = Modifier,
    subDub: String? = null,
    progressText: String? = null,
) {
    val scope = rememberCoroutineScope()
    val dto = series.data
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        TitleOrLogo(
            item = series,
            showLogo = showLogo,
            modifier =
                Modifier
                    .fillMaxWidth(.75f)
                    .padding(start = HeaderUtils.startPadding),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier =
                Modifier.fillMaxWidth(
                    com.github.damontecres.wholphin.ui.touch
                        .detailsTextFraction(),
                ),
        ) {
            QuickDetails(
                series.ui.quickDetails,
                null,
                Modifier.padding(start = HeaderUtils.startPadding),
            )
            // Benflix: SUB / DUB on anime, ENDED / RETURNING, and how much is left to watch
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = HeaderUtils.startPadding),
            ) {
                subDub?.let { SubDubTag(it) }
                com.github.damontecres.wholphin.ui.components
                    .ShowStatusTag(dto.status)
                progressText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = .85f),
                    )
                }
            }
            // Benflix: AniList score, next episode countdown and how far the English dub goes (anime only)
            AnimeFacts(series, Modifier.padding(start = HeaderUtils.startPadding))
            dto.studios?.let {
                val studios = remember { series.studioNames }
                GenreText(
                    studios,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = HeaderUtils.startPadding),
                )
            }
            dto.genres?.letNotEmpty {
                GenreText(it, Modifier.padding(start = HeaderUtils.startPadding, bottom = 4.dp))
            }
            dto.overview?.let { overview ->
                OverviewText(
                    overview = overview,
                    maxLines = 3,
                    onClick = overviewOnClick,
                    textBoxHeight = Dp.Unspecified,
                    modifier =
                        Modifier.onFocusChanged {
                            if (it.isFocused) {
                                scope.launch(ExceptionHandler()) {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                )
            }
        }
    }
}

fun buildDialogForSeason(
    resources: Resources,
    s: BaseItem,
    canDelete: Boolean,
    onClickItem: (BaseItem) -> Unit,
    markPlayed: (Boolean) -> Unit,
    onClickPlay: (Boolean) -> Unit,
    onClickDelete: (BaseItem) -> Unit,
): DialogParams {
    val items =
        buildList {
            add(
                DialogItem(resources.getString(R.string.go_to), Icons.Default.PlayArrow) {
                    onClickItem.invoke(s)
                },
            )
            if (s.data.userData?.played == true) {
                add(
                    DialogItem(resources.getString(R.string.mark_unwatched), R.string.fa_eye) {
                        markPlayed.invoke(false)
                    },
                )
            } else {
                add(
                    DialogItem(resources.getString(R.string.mark_watched), R.string.fa_eye_slash) {
                        markPlayed.invoke(true)
                    },
                )
            }
            add(
                DialogItem(
                    resources.getString(R.string.play),
                    Icons.Default.PlayArrow,
                ) {
                    onClickPlay.invoke(false)
                },
            )
            add(
                DialogItem(
                    resources.getString(R.string.shuffle),
                    R.string.fa_shuffle,
                ) {
                    onClickPlay.invoke(true)
                },
            )
            if (canDelete) {
                add(
                    DialogItem(
                        resources.getString(R.string.delete),
                        Icons.Default.Delete,
                        iconColor = Color.Red.copy(alpha = .8f),
                    ) {
                        onClickDelete.invoke(s)
                    },
                )
            }
        }
    return DialogParams(
        title = s.name ?: resources.getString(R.string.tv_season),
        fromLongClick = true,
        items = items,
    )
}
