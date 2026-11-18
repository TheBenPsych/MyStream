package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
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
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.discover.cantFindKey
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.successValue
import org.jellyfin.sdk.model.api.BaseItemKind

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
