package com.github.damontecres.wholphin.ui.detail.series

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.ExtrasItem
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.cards.ExtrasRow
import com.github.damontecres.wholphin.ui.cards.PersonRow
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixPlayFilled
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.LocalFlatButtons
import com.github.damontecres.wholphin.ui.logTab
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.extensions.ticks
import kotlin.time.Duration

/**
 * Benflix look A: the season page.
 *
 * TV: the season's poster, name, progress and flat buttons on the left, the episodes as a list
 * on the right (cast and extras at the bottom of that list), season pills under the buttons.
 * Phone: the same pieces stacked in one scrolling list.
 */
@Composable
fun SeriesOverviewContent(
    preferences: UserPreferences,
    series: BaseItem,
    seasons: List<BaseItem?>,
    episodes: EpisodeList,
    seasonExtras: List<ExtrasItem>,
    chosenStreams: ChosenStreams?,
    peopleInEpisode: List<Person>,
    position: SeriesOverviewPosition,
    firstItemFocusRequester: FocusRequester,
    episodeRowFocusRequester: FocusRequester,
    castCrewRowFocusRequester: FocusRequester,
    guestStarRowFocusRequester: FocusRequester,
    extrasRowFocusRequester: FocusRequester,
    onChangeSeason: (Int) -> Unit,
    onFocusEpisode: (Int) -> Unit,
    onClick: (BaseItem) -> Unit,
    onLongClick: (BaseItem) -> Unit,
    playOnClick: (Duration) -> Unit,
    watchOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    moreOnClick: () -> Unit,
    overviewOnClick: () -> Unit,
    personOnClick: (Person) -> Unit,
    goToShowOnClick: (() -> Unit)? = null,
    seasonWatchOnClick: ((BaseItem, Boolean) -> Unit)? = null,
    shuffleSeasonOnClick: ((BaseItem) -> Unit)? = null,
    canDelete: (BaseItem) -> Boolean,
    onConfirmDelete: (BaseItem) -> Unit,
    onClickExtra: (Int, ExtrasItem) -> Unit,
    onChooseVersion: (BaseItem, MediaSourceInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTv = rememberIsTv()
    var selectedTabIndex by rememberSaveable(position.seasonTabIndex) { mutableIntStateOf(position.seasonTabIndex) }
    LaunchedEffect(selectedTabIndex) {
        logTab("series_overview", selectedTabIndex)
    }
    var requestFocusAfterSeason by remember { mutableStateOf(false) }
    val currentOnChangeSeason by rememberUpdatedState(onChangeSeason)
    val pickSeason: (Int) -> Unit =
        remember {
            {
                selectedTabIndex = it
                currentOnChangeSeason(it)
                requestFocusAfterSeason = true
            }
        }

    val season = seasons.getOrNull(position.seasonTabIndex)
    val seasonEpisodes = (episodes as? EpisodeList.Success)?.episodes
    val focusedEpisode = seasonEpisodes?.getOrNull(position.episodeRowIndex)
    val allWatched =
        seasonEpisodes?.isNotEmpty() == true &&
            seasonEpisodes.all { it?.data?.userData?.played == true }
    val upNextIndex =
        remember(seasonEpisodes) {
            seasonEpisodes?.indexOfFirst { it?.data?.userData?.played != true } ?: -1
        }

    if (requestFocusAfterSeason && episodes is EpisodeList.Success) {
        // Changing seasons: move to the first episode once the new episodes are loaded
        LaunchedEffect(Unit) {
            firstItemFocusRequester.tryRequestFocus()
            requestFocusAfterSeason = false
        }
    }

    // One list state per season, so a new season starts at its top
    val listState =
        remember(season?.id) {
            LazyListState(
                firstVisibleItemIndex = if (isTv) (position.episodeRowIndex - 1).coerceAtLeast(0) else 0,
            )
        }

    val episodeItems: LazyListScope.() -> Unit = {
        when (val eps = episodes) {
            EpisodeList.Loading -> {
                item("loading") { LoadingPage(focusEnabled = false) }
            }

            is EpisodeList.Error -> {
                item("error") { ErrorMessage(eps.message, eps.exception) }
            }

            is EpisodeList.Success -> {
                itemsIndexed(eps.episodes, key = { index, ep -> ep?.id ?: index }) { index, ep ->
                    val selected = index == position.episodeRowIndex
                    EpisodeListRow(
                        episode = ep,
                        selected = selected,
                        upNext = index == upNextIndex,
                        isTv = isTv,
                        onFocused = { onFocusEpisode.invoke(index) },
                        onClick = {
                            if (!isTv && !selected) {
                                // Phones: taps don't move focus, so the first tap picks the
                                // episode and a second tap plays it
                                onFocusEpisode.invoke(index)
                            } else if (ep != null) {
                                onClick.invoke(ep)
                            }
                        },
                        onLongClick = { if (ep != null) onLongClick.invoke(ep) },
                        onPlayKey = { if (ep != null) onClick.invoke(ep) },
                        modifier =
                            if (selected) {
                                Modifier
                                    .focusRequester(firstItemFocusRequester)
                                    .focusRequester(episodeRowFocusRequester)
                            } else {
                                Modifier
                            },
                    )
                }
            }
        }
        val (guestStars, castAndCrew) = peopleInEpisode.partition { it.type == PersonKind.GUEST_STAR }
        if (castAndCrew.isNotEmpty()) {
            item("cast") {
                PersonRow(
                    title = R.string.cast_and_crew,
                    people = castAndCrew,
                    onClick = personOnClick,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .focusRequester(castCrewRowFocusRequester),
                )
            }
        }
        if (guestStars.isNotEmpty()) {
            item("guests") {
                PersonRow(
                    title = R.string.guest_stars,
                    people = guestStars,
                    onClick = personOnClick,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(guestStarRowFocusRequester),
                )
            }
        }
        if (seasonExtras.isNotEmpty()) {
            item("extras") {
                ExtrasRow(
                    extras = seasonExtras,
                    onClickItem = onClickExtra,
                    onLongClickItem = { _, _ -> },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(extrasRowFocusRequester),
                )
            }
        }
    }

    val seasonWatchClick: (() -> Unit)? =
        if (season != null && seasonWatchOnClick != null) {
            { seasonWatchOnClick.invoke(season, !allWatched) }
        } else {
            null
        }
    val shuffleClick: (() -> Unit)? =
        if (season != null && shuffleSeasonOnClick != null) {
            { shuffleSeasonOnClick.invoke(season) }
        } else {
            null
        }

    if (isTv) {
        Box(modifier = modifier.fillMaxSize()) {
            // Darker on the left so the words stay easy to read over bright backdrops
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = .85f),
                                .5f to Color.Black.copy(alpha = .7f),
                                1f to Color.Black.copy(alpha = .35f),
                            ),
                        ),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(start = 32.dp, end = 24.dp, top = 12.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier =
                        Modifier
                            .width(430.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 24.dp),
                ) {
                    SeasonInfoBlock(
                        series = series,
                        season = season,
                        episodes = seasonEpisodes,
                        isTv = true,
                    )
                    CompositionLocalProvider(LocalFlatButtons provides true) {
                        SeasonButtons(
                            episode = focusedEpisode,
                            playOnClick = playOnClick,
                            seasonWatched = allWatched,
                            seasonWatchOnClick = seasonWatchClick,
                            shuffleSeasonOnClick = shuffleClick,
                            goToShowOnClick = goToShowOnClick,
                            moreOnClick = moreOnClick,
                        )
                    }
                    SeasonPills(
                        seasons = seasons,
                        selectedIndex = selectedTabIndex,
                        onClick = pickSeason,
                    )
                }
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp, end = 8.dp),
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .focusGroup()
                            .focusRestorer(episodeRowFocusRequester),
                    content = episodeItems,
                )
            }
        }
    } else {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            modifier = modifier.fillMaxSize(),
        ) {
            item("info") {
                SeasonInfoBlock(
                    series = series,
                    season = season,
                    episodes = seasonEpisodes,
                    isTv = false,
                )
            }
            focusedEpisode?.let { ep ->
                item("buttons") {
                    CompositionLocalProvider(LocalFlatButtons provides true) {
                        FocusedEpisodeFooter(
                            preferences = preferences,
                            ep = ep,
                            chosenStreams = chosenStreams,
                            playOnClick = playOnClick,
                            moreOnClick = moreOnClick,
                            goToShowOnClick = goToShowOnClick,
                            seasonWatched = if (seasonWatchClick != null) allWatched else null,
                            seasonWatchOnClick = seasonWatchClick,
                            shuffleSeasonOnClick = shuffleClick,
                            watchOnClick = watchOnClick,
                            favoriteOnClick = favoriteOnClick,
                            canDelete = canDelete.invoke(ep),
                            onConfirmDelete = { onConfirmDelete.invoke(ep) },
                            onChooseVersion = { onChooseVersion.invoke(ep, it) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item("pills") {
                SeasonPills(
                    seasons = seasons,
                    selectedIndex = selectedTabIndex,
                    onClick = pickSeason,
                )
            }
            episodeItems()
        }
    }
}

/**
 * Benflix look A: the season page's buttons on TV. Play (or Resume) the episode you're on,
 * Restart it, Mark season watched, Shuffle season, Go to show and More (the episode's menu).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeasonButtons(
    episode: BaseItem?,
    playOnClick: (Duration) -> Unit,
    seasonWatched: Boolean,
    seasonWatchOnClick: (() -> Unit)?,
    shuffleSeasonOnClick: (() -> Unit)?,
    goToShowOnClick: (() -> Unit)?,
    moreOnClick: () -> Unit,
) {
    val playFocus = remember { FocusRequester() }
    val resume =
        episode
            ?.data
            ?.userData
            ?.playbackPositionTicks
            ?.ticks ?: Duration.ZERO
    val number = episode?.data?.indexNumber
    val playLabel =
        buildString {
            append(if (resume > Duration.ZERO) "Resume" else "Play")
            if (number != null) append(" E$number")
        }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier
                .focusGroup()
                .focusRestorer(playFocus),
    ) {
        ExpandablePlayButton(
            title = if (resume > Duration.ZERO) R.string.resume else R.string.play,
            label = playLabel,
            resume = resume,
            icon = BenflixPlayFilled,
            primary = true,
            enabled = episode != null,
            onClick = playOnClick,
            modifier = Modifier.focusRequester(playFocus),
        )
        if (resume > Duration.ZERO) {
            ExpandablePlayButton(
                title = R.string.restart,
                resume = Duration.ZERO,
                icon = BenflixIcon.REFRESH.vector,
                mirrorIcon = true,
                onClick = playOnClick,
            )
        }
        if (seasonWatchOnClick != null) {
            ExpandableFaButton(
                title = if (seasonWatched) R.string.benflix_mark_season_unwatched else R.string.benflix_mark_season_watched,
                iconStringRes = R.string.fa_check,
                onClick = seasonWatchOnClick,
            )
        }
        if (shuffleSeasonOnClick != null) {
            ExpandableFaButton(
                title = R.string.benflix_shuffle_season,
                iconStringRes = R.string.fa_shuffle,
                onClick = shuffleSeasonOnClick,
            )
        }
        if (goToShowOnClick != null) {
            ExpandableFaButton(
                title = R.string.go_to_show,
                iconStringRes = R.string.fa_tv,
                onClick = goToShowOnClick,
            )
        }
        ExpandablePlayButton(
            title = R.string.more,
            resume = Duration.ZERO,
            icon = BenflixIcon.MORE_VERT.vector,
            enabled = episode != null,
            onClick = { moreOnClick.invoke() },
        )
    }
}
