package com.github.damontecres.wholphin.ui.detail.episode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.orBenflixSubtitleLanguage
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.BenflixIcon
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
import com.github.damontecres.wholphin.ui.detail.series.EpisodeCard
import com.github.damontecres.wholphin.ui.detail.title.TitleButton
import com.github.damontecres.wholphin.ui.detail.title.TitleCastRow
import com.github.damontecres.wholphin.ui.detail.title.TitleOverview
import com.github.damontecres.wholphin.ui.detail.title.TitlePage
import com.github.damontecres.wholphin.ui.detail.title.TitlePicture
import com.github.damontecres.wholphin.ui.detail.title.TitleTab
import com.github.damontecres.wholphin.ui.detail.title.TitleTextLink
import com.github.damontecres.wholphin.ui.detail.title.rememberScoreParts
import com.github.damontecres.wholphin.ui.door.lengthText
import com.github.damontecres.wholphin.ui.formatDate
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.premiereDay
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.util.DataLoadingState
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUID
import java.util.UUID
import kotlin.time.Duration

@Composable
fun EpisodeDetails(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: EpisodeViewModel =
        hiltViewModel<EpisodeViewModel, EpisodeViewModel.Factory>(
            creationCallback = { it.create(destination.itemId) },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    LifecycleResumeEffect(Unit) {
        viewModel.init()
        onPauseOrDispose { }
    }
    val state by viewModel.state.collectAsState()

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var showContextMenu by remember { mutableStateOf<ContextMenu?>(null) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.collectAsState()
    val canDelete by viewModel.canDelete.collectAsState()

    val userDto by viewModel.serverRepository.currentUserDtoFlow.collectAsState(null)
    val preferredSubtitleLanguage = userDto?.configuration?.subtitleLanguagePreference.orBenflixSubtitleLanguage()

    val contextActions =
        remember {
            ContextMenuActions(
                navigateTo = viewModel::navigateTo,
                onClickWatch = viewModel::setWatched,
                onClickFavorite = viewModel::setFavorite,
                onClickAddPlaylist = { itemId ->
                    playlistViewModel.loadPlaylists()
                    showPlaylistDialog.makePresent(itemId)
                },
                onSendMediaInfo = viewModel.serverReportService::sendMediaReportFor,
                onDeleteItem = viewModel::deleteItem,
                onShowOverview = { overviewDialog = ItemDetailsDialogInfo(it) },
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
                onClearChosenStreams = { viewModel.clearChosenStreams(it) },
            )
        }

    when (val st = state.episode) {
        is DataLoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        is DataLoadingState.Success<BaseItem> -> {
            val ep = st.data
            EpisodeDetailsContent(
                preferences = preferences,
                ep = ep,
                chosenStreams = state.chosenStreams,
                playOnClick = {
                    viewModel.navigateTo(
                        Destination.Playback(
                            ep.id,
                            it.inWholeMilliseconds,
                        ),
                    )
                },
                overviewOnClick = {
                    overviewDialog =
                        ItemDetailsDialogInfo(ep)
                },
                goToShowOnClick =
                    ep.data.seriesId?.let { seriesId ->
                        {
                            viewModel.navigateTo(
                                Destination.MediaItem(seriesId, BaseItemKind.SERIES, null),
                            )
                        }
                    },
                moreOnClick = {
                    showContextMenu =
                        ContextMenu.ForBaseItem(
                            fromLongClick = false,
                            item = ep,
                            chosenStreams = state.chosenStreams,
                            showGoTo = false,
                            showStreamChoices = true,
                            canDelete = canDelete,
                            canRemoveContinueWatching = false,
                            canRemoveNextUp = false,
                            actions = contextActions,
                        )
                },
                watchOnClick = {
                    viewModel.setWatched(ep.id, !ep.played)
                },
                favoriteOnClick = {
                    viewModel.setFavorite(ep.id, !ep.favorite)
                },
                canDelete = canDelete,
                onConfirmDelete = { viewModel.deleteItem(ep) },
                onChooseVersion = { contextActions.onChooseVersion.invoke(ep, it) },
                nextEpisodes = state.nextEpisodes,
                people = state.people,
                openEpisode = { viewModel.navigateTo(Destination.MediaItem(it.id, BaseItemKind.EPISODE)) },
                openPerson = { viewModel.navigateTo(Destination.MediaItem(it.id, BaseItemKind.PERSON)) },
                modifier = modifier,
            )
        }
    }
    overviewDialog?.let { info ->
        ItemDetailsDialog(
            info = info,
            showFilePath =
                userDto
                    ?.policy
                    ?.isAdministrator == true,
            onDismissRequest = { overviewDialog = null },
        )
    }
    showContextMenu?.let { contextMenu ->
        ContextMenuDialog(
            onDismissRequest = { showContextMenu = null },
            getMediaSource = viewModel.streamChoiceService::chooseSource,
            contextMenu = contextMenu,
            preferredSubtitleLanguage = preferredSubtitleLanguage,
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
 * Benflix Redesign 2: an episode's own page on the shared title page frame. Buttons: Play (or Resume),
 * Start over (greyed out until you've started it), Watched, More. Tabs: Overview, Next episodes, Cast.
 * The show's name in the info line goes to the show.
 */
@Composable
fun EpisodeDetailsContent(
    preferences: UserPreferences,
    ep: BaseItem,
    chosenStreams: ChosenStreams?,
    playOnClick: (Duration) -> Unit,
    overviewOnClick: () -> Unit,
    watchOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    moreOnClick: () -> Unit,
    canDelete: Boolean,
    onConfirmDelete: () -> Unit,
    onChooseVersion: (MediaSourceInfo) -> Unit,
    modifier: Modifier = Modifier,
    goToShowOnClick: (() -> Unit)? = null,
    nextEpisodes: List<BaseItem> = emptyList(),
    people: List<Person> = emptyList(),
    openEpisode: (BaseItem) -> Unit = {},
    openPerson: (Person) -> Unit = {},
) {
    val dto = ep.data
    val resume = dto.userData?.playbackPositionTicks?.ticks ?: Duration.ZERO
    val canResume = resume.inWholeSeconds > 30 && !ep.played
    val left =
        if (canResume) {
            dto.runTimeTicks
                ?.ticks
                ?.minus(resume)
                ?.takeIf { it.isPositive() }
        } else {
            null
        }
    val imageUrlService = LocalImageUrlService.current
    val width = LocalConfiguration.current.screenWidthDp
    val picture =
        remember(ep.id) {
            // The episode's own still, else the show's wide picture
            val type = if (ep.data.imageTags?.containsKey(ImageType.PRIMARY) == true) ImageType.PRIMARY else ImageType.BACKDROP
            imageUrlService.getItemImageUrl(ep, type, fillWidth = (width * 2).coerceAtMost(1920), useSeriesForPrimary = false)
        }
    val number =
        listOfNotNull(
            dto.parentIndexNumber?.let { "S$it" },
            dto.indexNumber?.let { "E$it" },
        ).joinToString(" ")
    val info =
        listOfNotNull(
            number.takeIf { it.isNotBlank() },
            dto.runTimeTicks?.ticks?.let(::lengthText),
            dto.premiereDay?.let { "Aired ${formatDate(it)}" },
        ).joinToString(" · ")
    val buttons =
        listOf(
            TitleButton(
                label = if (canResume) "Resume" else "Play",
                icon = BenflixIcon.PLAY,
                onClick = { playOnClick(if (canResume) resume else Duration.ZERO) },
                note = left?.let { "${lengthText(it)} left" },
            ),
            TitleButton(
                label = "Start over",
                icon = BenflixIcon.REFRESH,
                onClick = { playOnClick(Duration.ZERO) },
                off = if (canResume) null else "You haven't started this one yet",
            ),
            TitleButton(
                label = if (ep.played) "Watched" else "Mark watched",
                icon = BenflixIcon.CHECK,
                onClick = watchOnClick,
                note = if (ep.played) "Press again to mark it as not watched" else null,
            ),
            TitleButton(label = "More", icon = BenflixIcon.MORE, onClick = moreOnClick),
        )
    val scores = rememberScoreParts(ep)
    val tabs =
        buildList {
            add(
                TitleTab("Overview") { scope ->
                    TitleOverview(scope = scope, title = ep.title.orEmpty(), story = dto.overview, strip = scores)
                },
            )
            if (nextEpisodes.isNotEmpty()) {
                add(
                    TitleTab("Next episodes") { scope ->
                        val list = remember(nextEpisodes) { nextEpisodes.distinctBy { it.id } }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = scope.side),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(list, key = { it.id.toString() }) { next ->
                                EpisodeCard(
                                    episode = next,
                                    number = next.data.indexNumber?.let { "E$it" },
                                    width = if (scope.wide) 210.dp else 200.dp,
                                    onClick = { openEpisode(next) },
                                    onLongClick = null,
                                    onFocus = {},
                                )
                            }
                        }
                    },
                )
            }
            if (people.isNotEmpty()) {
                add(TitleTab("Cast") { scope -> TitleCastRow(scope, people, openPerson) })
            }
        }
    TitlePage(
        title = ep.name.orEmpty(),
        accent = BenflixColors.Shows,
        picture = TitlePicture.Wide(picture),
        info = info,
        rating = dto.officialRating,
        infoExtra =
            dto.seriesName?.let { show ->
                goToShowOnClick?.let { go -> { TitleTextLink(show, go) } }
            },
        buttons = buttons,
        tabs = tabs,
        modifier = modifier,
    )
}
