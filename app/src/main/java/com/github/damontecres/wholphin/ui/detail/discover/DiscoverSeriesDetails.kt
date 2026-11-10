package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.api.seerr.model.TvDetails
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.data.model.canRequestTv
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.CantFindBlocked
import com.github.damontecres.wholphin.services.SeerrUserConfig
import com.github.damontecres.wholphin.services.TrailerService
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.QuickDetailsText
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.discover.cantFindKey
import com.github.damontecres.wholphin.ui.listToDotString
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.successValue
import org.jellyfin.sdk.model.api.BaseItemKind
import kotlin.math.roundToInt

@Composable
fun DiscoverSeriesDetails(
    preferences: UserPreferences,
    destination: Destination.DiscoveredItem,
    modifier: Modifier = Modifier,
    viewModel: DiscoverSeriesViewModel =
        hiltViewModel<DiscoverSeriesViewModel, DiscoverSeriesViewModel.Factory>(
            creationCallback = { it.create(destination.item) },
        ),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val state by viewModel.state.collectAsState()
    val requestEnabled by viewModel.requestEnabled.collectAsState()
    val request4kEnabled by viewModel.request4kEnabled.collectAsState()

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var seasonDialog by remember { mutableStateOf<DialogParams?>(null) }
    var moreDialog by remember { mutableStateOf<DialogParams?>(null) }
    var showRequestSeasonDialog by remember { mutableStateOf(false) }

    when (val st = state.tvSeries) {
        is DataLoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        is DataLoadingState.Success<TvDetails> -> {
            val item = st.data
            val userConfig by viewModel.userConfig.collectAsState(null)
            val backdrop by viewModel.backdrop.collectAsState()
            DiscoverSeriesDetailsContent(
                isAdmin =
                    viewModel.serverRepository.currentUserDto
                        ?.policy
                        ?.isAdministrator == true,
                preferences = preferences,
                series = item,
                availability = state.availability,
                userConfig = userConfig,
                rating = state.rating,
                canCancel = state.canCancelRequest,
                seasons = state.seasons,
                people = state.people,
                similar = state.similar,
                recommended = state.recommended,
                modifier = modifier,
                backdropUrl = backdrop,
                onClickItem = { index, item ->
                    viewModel.navigateTo(Destination.DiscoveredItem(item))
                },
                onClickPerson = {
                    viewModel.navigateTo(Destination.DiscoveredItem(it))
                },
                goToOnClick = {
                    viewModel.goTo(item.mediaInfo, BaseItemKind.SERIES)
                },
                overviewOnClick = {
                    overviewDialog =
                        ItemDetailsDialogInfo(
                            title = item.name ?: resources.getString(R.string.unknown),
                            overview = item.overview,
                            genres = item.genres?.mapNotNull { it.name }.orEmpty(),
                            files = listOf(),
                        )
                },
                trailerOnClick = {
                    TrailerService.onClick(context, it, viewModel::navigateTo)
                },
                trailers = state.trailers,
                requestOnClick = {
                    viewModel.requestOnClick()
                    showRequestSeasonDialog = true
                },
                cancelOnClick = {
                    item.id?.let { viewModel.cancelRequest(it) }
                },
                moreOnClick = {
                },
                onLongClickPerson = { _, _ -> },
                onLongClickSimilar = { _, _ -> },
            )
        }
    }
    overviewDialog?.let { info ->
        ItemDetailsDialog(
            info = info,
            showFilePath = false,
            onDismissRequest = { overviewDialog = null },
        )
    }
    seasonDialog?.let { params ->
        DialogPopup(
            showDialog = true,
            title = params.title,
            dialogItems = params.items,
            waitToLoad = params.fromLongClick,
            onDismissRequest = { seasonDialog = null },
        )
    }
    moreDialog?.let { params ->
        DialogPopup(
            showDialog = true,
            title = params.title,
            dialogItems = params.items,
            onDismissRequest = { moreDialog = null },
            dismissOnClick = true,
            waitToLoad = params.fromLongClick,
        )
    }
    if (showRequestSeasonDialog) {
        RequestSeasonsDialog(
            id = state.tvSeries.successValue?.id ?: -1,
            title = state.tvSeries.successValue?.name ?: "",
            seasons = state.seasons,
            seasons4k = state.seasons4k,
            requestEnabled = requestEnabled,
            request4kEnabled = request4kEnabled,
            onSubmit = {
                showRequestSeasonDialog = false
                viewModel.request(it)
            },
            loading = state.profileLoading,
            data = state.requestData,
            onDismissRequest = { showRequestSeasonDialog = false },
        )
    }
}

private const val HEADER_ROW = 0
private const val SEASONS_ROW = HEADER_ROW + 1
private const val PEOPLE_ROW = SEASONS_ROW + 1
private const val EXTRAS_ROW = PEOPLE_ROW + 1
private const val SIMILAR_ROW = EXTRAS_ROW + 1
private const val RECOMMENDED_ROW = SIMILAR_ROW + 1

@Composable
fun DiscoverSeriesDetailsContent(
    isAdmin: Boolean,
    preferences: UserPreferences,
    userConfig: SeerrUserConfig?,
    series: TvDetails,
    availability: SeerrAvailability,
    rating: DiscoverRating?,
    canCancel: Boolean,
    seasons: List<RequestSeason>,
    similar: List<DiscoverItem>,
    recommended: List<DiscoverItem>,
    trailers: List<Trailer>,
    people: List<DiscoverItem>,
    requestOnClick: () -> Unit,
    cancelOnClick: () -> Unit,
    trailerOnClick: (Trailer) -> Unit,
    overviewOnClick: () -> Unit,
    goToOnClick: () -> Unit,
    moreOnClick: () -> Unit,
    onClickItem: (Int, DiscoverItem) -> Unit,
    onClickPerson: (DiscoverItem) -> Unit,
    onLongClickPerson: (Int, DiscoverItem) -> Unit,
    onLongClickSimilar: (Int, DiscoverItem) -> Unit,
    modifier: Modifier = Modifier,
    backdropUrl: String? = null,
) {
    // Benflix: Ben couldn't find it, so the Ask button becomes a "Couldn't find it" label
    val cantFindKeys by CantFindBlocked.keys.collectAsState()
    val cantFindNow = series.id?.let { cantFindKey(false, it) }?.let { it in cantFindKeys } == true
    val info =
        remember(series) {
            listOfNotNull(
                series.firstAirDate?.take(4)?.takeIf { it.isNotBlank() },
                series.seasons
                    ?.count { (it.seasonNumber ?: 0) > 0 }
                    ?.takeIf { it > 0 }
                    ?.let { if (it == 1) "1 season" else "$it seasons" },
                series.genres
                    ?.mapNotNull { it.name }
                    ?.take(2)
                    ?.takeIf { it.isNotEmpty() }
                    ?.joinToString(" · "),
            ).joinToString(" · ")
        }
    RequestTitlePage(
        isMovie = false,
        tmdbId = series.id,
        title = series.name.orEmpty(),
        story = series.overview,
        info = info,
        backdropUrl = backdropUrl,
        availability = availability,
        isAdmin = isAdmin,
        canRequest = userConfig.canRequestTv,
        canCancel = canCancel,
        cantFind = cantFindNow,
        trailers = trailers,
        rating = rating,
        similar = similar,
        recommended = recommended,
        people = people,
        requestOnClick = requestOnClick,
        cancelOnClick = cancelOnClick,
        goToOnClick = goToOnClick,
        trailerOnClick = trailerOnClick,
        onClickItem = { onClickItem(0, it) },
        onClickPerson = onClickPerson,
        modifier = modifier,
    )
}

@Composable
fun DiscoverSeriesDetailsHeader(
    series: TvDetails,
    rating: DiscoverRating?,
    overviewOnClick: () -> Unit,
    showLogo: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        // Benflix redesign: the big white name, like the Movies page
        androidx.tv.material3.Text(
            text = series.name.orEmpty(),
            color = com.github.damontecres.wholphin.ui.theme.BenflixColors.Text,
            fontSize = BenflixType.hero,
            lineHeight = BenflixType.hero * 1.1f,
            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
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
            val resources = LocalResources.current
            // Benflix (Ben's pick "A · Fact cards"): one short line, then the scores and facts boxes
            val details =
                remember(series, resources) {
                    buildList {
                        series.firstAirDate
                            ?.take(4)
                            ?.takeIf { it.isNotBlank() }
                            ?.let(::add)
                        series.seasons
                            ?.count { (it.seasonNumber ?: 0) > 0 }
                            ?.takeIf { it > 0 }
                            ?.let { add(if (it == 1) "1 season" else "$it seasons") }
                        series.genres
                            ?.mapNotNull { it.name }
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { add(it.joinToString(", ")) }
                    }.let { listToDotString(it, null, null) }
                }

            QuickDetailsText(details, Modifier.padding(start = HeaderUtils.startPadding, bottom = 6.dp))
            series.id?.let { tmdbId ->
                SeerrExtrasBlock(
                    isMovie = false,
                    tmdbId = tmdbId,
                    fallbackCritic = rating?.criticRating,
                    fallbackAudience = rating?.audienceRating?.let { (it * 10).roundToInt() },
                    modifier = Modifier.padding(start = HeaderUtils.startPadding, bottom = 6.dp),
                )
                // Benflix (admins): who already asked for this, with Approve / Decline
                val onBenflix = series.mediaInfo?.status == com.github.damontecres.wholphin.data.model.SeerrAvailability.AVAILABLE.status
                WhoAskedBlock(
                    isMovie = false,
                    tmdbId = tmdbId,
                    title = series.name,
                    available = onBenflix,
                    modifier = Modifier.padding(start = HeaderUtils.startPadding, bottom = 4.dp),
                )
                // Benflix: "Ben looked, but couldn't find this yet" (everyone sees it)
                com.github.damontecres.wholphin.ui.discover.CantFindBlock(
                    isMovie = false,
                    tmdbId = tmdbId,
                    available = onBenflix,
                    modifier = Modifier.padding(start = HeaderUtils.startPadding, bottom = 4.dp),
                )
            }
            series.overview?.let { overview ->
                OverviewText(
                    overview = overview,
                    // Benflix: 2 lines on TV so the buttons fit on the first screen (press it to read it all)
                    maxLines =
                        if (com.github.damontecres.wholphin.ui.touch
                                .rememberIsTv()
                        ) {
                            2
                        } else {
                            3
                        },
                    onClick = overviewOnClick,
                    textBoxHeight = Dp.Unspecified,
                )
            }
            // Benflix: 3 tags, and "+N more" for the rest
            series.id?.let { tmdbId ->
                SeerrTagsBlock(
                    isMovie = false,
                    tmdbId = tmdbId,
                    modifier = Modifier.padding(start = HeaderUtils.startPadding, top = 6.dp),
                )
                // Benflix: anime genres from AniList (Isekai, Slice of Life...); press one to see them all
                AnimeGenresBlock(
                    isMovie = false,
                    tmdbId = tmdbId,
                    modifier = Modifier.padding(start = HeaderUtils.startPadding, top = 8.dp),
                )
            }
        }
    }
}
