package com.github.damontecres.wholphin.ui.detail.series

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.TrailerService
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.ui.components.ContextMenu
import com.github.damontecres.wholphin.ui.components.ContextMenuActions
import com.github.damontecres.wholphin.ui.components.ContextMenuDialog
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.Optional
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.DataLoadingState
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.serializer.toUUID
import java.util.UUID

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
            val kids by KidsMode.active.collectAsState()
            val easy = kids || preferences.appPreferences.benflixPreferences.easyRead
            SeriesHeroContent(
                series = series,
                state = state,
                easy = easy,
                modifier = modifier,
                actions =
                    SeriesHeroActions(
                        playNext = viewModel::playNextUp,
                        shuffle = { viewModel.navigateTo(Destination.PlaybackList(itemId = series.id, shuffle = true)) },
                        rewatch = if (played && state.progress?.firstEpisodeId != null) viewModel::rewatch else null,
                        playEpisode = { episode, fromStart ->
                            viewModel.navigateTo(Destination.Playback(episode.id, if (fromStart) 0L else episode.resumeMs))
                        },
                        setEpisodeWatched = { episode, watched -> viewModel.setWatched(episode.id, watched, null) },
                        trailer = { TrailerService.onClick(context, it, viewModel::navigateTo) },
                        setMyList = { viewModel.setFavorite(series.id, it, null) },
                        rate = { viewModel.setLikes(series.id, it) },
                        markShowWatched = { showWatchConfirmation = true },
                        pickSeason = viewModel::showSeasonEpisodes,
                        openItem = { viewModel.navigateTo(it.destination()) },
                        openPerson = { viewModel.navigateTo(Destination.MediaItem(it.id, BaseItemKind.PERSON)) },
                        fixMatch = if (state.isAdmin) ({ showFixMatch = true }) else null,
                        request = state.discoverSeries?.let { d -> { viewModel.navigateTo(Destination.DiscoveredItem(d)) } },
                    ),
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
