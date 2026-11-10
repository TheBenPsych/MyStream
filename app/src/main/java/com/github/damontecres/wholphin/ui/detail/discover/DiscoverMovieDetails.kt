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
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.data.model.canRequestMovies
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.CantFindBlocked
import com.github.damontecres.wholphin.services.SeerrUserConfig
import com.github.damontecres.wholphin.services.TrailerService
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.discover.cantFindKey
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.util.DataLoadingState
import org.jellyfin.sdk.model.api.BaseItemKind
import kotlin.time.Duration.Companion.minutes

@Composable
fun DiscoverMovieDetails(
    preferences: UserPreferences,
    destination: Destination.DiscoveredItem,
    modifier: Modifier = Modifier,
    viewModel: DiscoverMovieViewModel =
        hiltViewModel<DiscoverMovieViewModel, DiscoverMovieViewModel.Factory>(
            creationCallback = { it.create(destination.item) },
        ),
) {
    val resources = LocalResources.current
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        viewModel.init()
        onPauseOrDispose { }
    }
    val state by viewModel.state.collectAsState()
    val userConfig by viewModel.userConfig.collectAsState(null)
    val backdrop by viewModel.backdrop.collectAsState()
    val requestEnabled by viewModel.requestEnabled.collectAsState(false)
    val request4kEnabled by viewModel.request4kEnabled.collectAsState(false)

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var showRequestDialog by remember { mutableStateOf(false) }

    when (val st = state.movie) {
        is DataLoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        is DataLoadingState.Success<MovieDetails> -> {
            val movie = st.data
            DiscoverMovieDetailsContent(
                isAdmin =
                    viewModel.serverRepository.currentUserDto
                        ?.policy
                        ?.isAdministrator == true,
                preferences = preferences,
                movie = movie,
                userConfig = userConfig,
                rating = state.rating,
                canCancel = state.canCancelRequest,
                people = state.people,
                trailers = state.trailers,
                similar = state.similar,
                recommended = state.recommended,
                requestOnClick = {
                    viewModel.requestOnClick()
                    showRequestDialog = true
                },
                cancelOnClick = {
                    movie.id?.let { viewModel.cancelRequest(it) }
                },
                onClickItem = { index, item ->
                    viewModel.navigateTo(Destination.DiscoveredItem(item))
                },
                onClickPerson = { item ->
                    viewModel.navigateTo(Destination.DiscoveredItem(item))
                },
                overviewOnClick = {
                    overviewDialog =
                        ItemDetailsDialogInfo(
                            title = movie.title ?: resources.getString(R.string.unknown),
                            overview = movie.overview,
                            genres = movie.genres?.mapNotNull { it.name }.orEmpty(),
                            files = listOf(),
                        )
                },
                goToOnClick = {
                    viewModel.goTo(movie.mediaInfo, BaseItemKind.MOVIE)
                },
                moreOnClick = {},
                onLongClickPerson = { index, person -> },
                onLongClickSimilar = { index, similar ->
                },
                trailerOnClick = {
                    TrailerService.onClick(context, it, viewModel::navigateTo)
                },
                backdropUrl = backdrop,
                modifier = modifier,
            )
            if (showRequestDialog) {
                RequestMovieDialog(
                    loading = state.profileLoading,
                    data = state.requestData,
                    requestEnabled = requestEnabled,
                    request4kEnabled = request4kEnabled,
                    movie = movie,
                    onSubmit = {
                        viewModel.request(it)
                        showRequestDialog = false
                    },
                    onDismissRequest = { showRequestDialog = false },
                )
            }
        }
    }
    overviewDialog?.let { info ->
        ItemDetailsDialog(
            info = info,
            showFilePath = false,
            onDismissRequest = { overviewDialog = null },
        )
    }
}

private const val HEADER_ROW = 0
private const val PEOPLE_ROW = HEADER_ROW + 1
private const val CHAPTER_ROW = PEOPLE_ROW + 1
private const val EXTRAS_ROW = CHAPTER_ROW + 1
private const val SIMILAR_ROW = EXTRAS_ROW + 1
private const val RECOMMENDED_ROW = SIMILAR_ROW + 1

@Composable
fun DiscoverMovieDetailsContent(
    isAdmin: Boolean,
    preferences: UserPreferences,
    userConfig: SeerrUserConfig?,
    movie: MovieDetails,
    rating: DiscoverRating?,
    canCancel: Boolean,
    people: List<DiscoverItem>,
    trailers: List<Trailer>,
    similar: List<DiscoverItem>,
    recommended: List<DiscoverItem>,
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
    val cantFindNow = movie.id?.let { cantFindKey(true, it) }?.let { it in cantFindKeys } == true
    val resources = LocalResources.current
    val info =
        remember(movie, resources) {
            listOfNotNull(
                movie.releaseDate?.take(4)?.takeIf { it.isNotBlank() },
                movie.runtime
                    ?.toDouble()
                    ?.minutes
                    ?.roundMinutes
                    ?.let { resources.formatDuration(it) },
                movie.genres
                    ?.mapNotNull { it.name }
                    ?.take(2)
                    ?.takeIf { it.isNotEmpty() }
                    ?.joinToString(" · "),
            ).joinToString(" · ")
        }
    RequestTitlePage(
        isMovie = true,
        tmdbId = movie.id,
        title = movie.title.orEmpty(),
        story = movie.overview,
        info = info,
        backdropUrl = backdropUrl,
        availability = SeerrAvailability.from(movie.mediaInfo?.status) ?: SeerrAvailability.UNKNOWN,
        isAdmin = isAdmin,
        canRequest = userConfig.canRequestMovies,
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
