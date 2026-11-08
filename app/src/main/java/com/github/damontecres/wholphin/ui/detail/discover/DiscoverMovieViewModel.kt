package com.github.damontecres.wholphin.ui.detail.discover

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.api.seerr.model.MediaInfo
import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.api.seerr.model.RelatedVideo
import com.github.damontecres.wholphin.api.seerr.model.RequestPostRequest
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.data.model.RemoteTrailer
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.SeerrPermission
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.data.model.hasPermission
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.services.SeerrUserConfig
import com.github.damontecres.wholphin.ui.equalsNotNull
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.successValue
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel(assistedFactory = DiscoverMovieViewModel.Factory::class)
class DiscoverMovieViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        @param:ApplicationContext private val context: Context,
        private val navigationManager: NavigationManager,
        private val backdropService: BackdropService,
        val serverRepository: ServerRepository,
        val seerrService: SeerrService,
        private val seerrServerRepository: SeerrServerRepository,
        @Assisted val item: DiscoverItem,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(item: DiscoverItem): DiscoverMovieViewModel
        }

        /** The big picture behind the page */
        val backdrop = kotlinx.coroutines.flow.MutableStateFlow(item.backDropUrl)

        private val _state = MutableStateFlow(DiscoverMovieState())
        val state: StateFlow<DiscoverMovieState> = _state

        val userConfig = seerrServerRepository.current.map { it?.config }
        val requestEnabled =
            seerrServerRepository.current.map { it?.requestMovieEnabled ?: false }
        val request4kEnabled =
            seerrServerRepository.current.map { it?.request4kMovieEnabled ?: false }

        init {
            init()
            // Benflix: an admin answered a request on this page (Who asked), so the buttons catch up
            viewModelScope.launchIO {
                RequestAnswered.events.collect { key ->
                    if (key == RequestAnswered.key(true, item.id)) {
                        fetchAndSetItem().await()
                        updateCanCancel()
                    }
                }
            }
        }

        private fun fetchAndSetItem(): Deferred<MovieDetails?> =
            viewModelScope.async(WholphinDispatchers.IO) {
                try {
                    val movie = seerrService.api.moviesApi.movieMovieIdGet(movieId = item.id)
                    _state.update { it.copy(movie = DataLoadingState.Success(movie)) }
                    movie
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error updating movie details")
                    null
                }
            }

        fun init(): Job =
            viewModelScope.launchIO {
                Timber.v("Init for movie %s", item.id)
                try {
                    // Benflix: profiles without Requests (Seerr) linked get a plain message, not a "baseUrl" error
                    if (seerrService.api.baseUrl.isBlank()) {
                        _state.update {
                            it.copy(
                                movie = DataLoadingState.Error("Requests aren't set up for this profile yet, so this page can't open."),
                            )
                        }
                        return@launchIO
                    }
                    val movie = seerrService.api.moviesApi.movieMovieIdGet(movieId = item.id)
                    _state.update { it.copy(movie = DataLoadingState.Success(movie)) }
                    val discoveredItem = seerrService.createDiscoverItem(movie)
                    // Benflix redesign: the page draws its own big picture (like the Movies page)
                    backdropService.clearBackdrop()
                    backdrop.value = discoveredItem.backDropUrl ?: backdrop.value

                    updateCanCancel()

                    viewModelScope.launchIO {
                        val rating =
                            getDiscoverRating(item.id) {
                                DiscoverRating(
                                    seerrService.api.moviesApi.movieMovieIdRatingsGet(movieId = item.id),
                                )
                            }
                        _state.update { it.copy(rating = rating) }
                    }
                    // Benflix: no Seerr "More like this" / "Recommended" rows in Kids (they aren't rating-checked)
                    if (state.value.similar.isEmpty() && !KidsMode.isActive) {
                        viewModelScope.launchIO {
                            val result =
                                seerrService.api.moviesApi
                                    .movieMovieIdSimilarGet(movieId = item.id, page = 1)
                                    .results
                                    ?.map { seerrService.createDiscoverItem(it) }
                                    .orEmpty()
                            _state.update { it.copy(similar = result) }
                        }
                        viewModelScope.launchIO {
                            val result =
                                seerrService.api.moviesApi
                                    .movieMovieIdRecommendationsGet(movieId = item.id, page = 1)
                                    .results
                                    ?.map { seerrService.createDiscoverItem(it) }
                                    .orEmpty()
                            _state.update { it.copy(recommended = result) }
                        }
                    }
                    val people =
                        movie.credits
                            ?.cast
                            ?.map { seerrService.createDiscoverItem(it) }
                            .orEmpty() +
                            movie.credits
                                ?.crew
                                ?.map { seerrService.createDiscoverItem(it) }
                                .orEmpty()
                    _state.update { it.copy(people = people) }
                    val trailers =
                        movie.relatedVideos
                            ?.filter { it.type == RelatedVideo.Type.TRAILER }
                            ?.filter { it.name.isNotNullOrBlank() && it.url.isNotNullOrBlank() }
                            ?.map {
                                RemoteTrailer(it.name!!, it.url!!, it.site)
                            }.orEmpty()
                    _state.update { it.copy(trailers = trailers) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error updating movie details")
                    _state.update { it.copy(movie = DataLoadingState.Error(ex)) }
                }
            }

        private suspend fun updateCanCancel() {
            val user = userConfig.firstOrNull()
            // Benflix: Jellyfin Enhanced can't delete requests, so no trash button there
            val canCancel =
                !seerrService.api.viaJellyfin &&
                    canUserCancelRequest(
                        user,
                        state.value.movie.successValue
                            ?.mediaInfo
                            ?.requests,
                    )
            _state.update { it.copy(canCancelRequest = canCancel) }
        }

        fun navigateTo(destination: Destination) {
            navigationManager.navigateTo(destination)
        }

        fun goTo(
            mediaInfo: MediaInfo?,
            type: BaseItemKind,
        ) {
            viewModelScope.launchDefault {
                goToButtonDiscover(mediaInfo, type, context, navigationManager)
            }
        }

        fun request(request: MovieRequest) {
            viewModelScope.launchIO {
                if (request.movieId == null) {
                    Timber.w("Null movie ID")
                    return@launchIO
                }
                try {
                    val postRequest =
                        RequestPostRequest(
                            is4k = request.is4k,
                            mediaId = request.movieId,
                            mediaType = RequestPostRequest.MediaType.MOVIE,
                            serverId =
                                when {
                                    request.profileId == null && request.folder == null -> null
                                    request.is4k -> request.data.server4kId
                                    else -> request.data.serverId
                                },
                            profileId = request.profileId,
                            rootFolder = request.folder,
                            tags = emptyList(),
                        )
                    Timber.v("postRequest=%s", postRequest)
                    seerrService.api.requestApi.requestPost(postRequest)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error requesting %s", request.movieId)
                    showToast(context, "An error occurred")
                }
                fetchAndSetItem().await()
                updateCanCancel()
            }
        }

        fun cancelRequest(id: Int) {
            viewModelScope.launchIO {
                // Benflix: only ever the signed-in user's own request, never someone else's
                ownCancellableRequest(
                    userConfig.firstOrNull(),
                    state.value.movie.successValue
                        ?.mediaInfo
                        ?.requests,
                )?.let {
                    try {
                        seerrService.api.requestApi.requestRequestIdDelete(it.id.toString())
                    } catch (ex: kotlinx.coroutines.CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error requesting %s", id)
                        showToast(context, "An error occurred")
                    }
                    fetchAndSetItem().await()
                    updateCanCancel()
                }
            }
        }

        fun requestOnClick() {
            viewModelScope.launchIO {
                try {
                    val data = seerrService.getProfilesAndFolders(SeerrItemType.MOVIE)
                    _state.update {
                        it.copy(
                            profileLoading = LoadingState.Success,
                            requestData = data,
                        )
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error getting profiles & folders")
                    showToast(context, "Error getting profiles & folders: ${ex.localizedMessage}")
                    _state.update {
                        it.copy(
                            profileLoading = LoadingState.Success,
                        )
                    }
                }
            }
        }
    }

/**
 * Benflix: the signed-in user's own request for this title, which Cancel deletes, or null.
 * Cancel never touches someone else's request: admins can cancel their own at any stage,
 * everyone else only before it's approved.
 */
fun ownCancellableRequest(
    user: SeerrUserConfig?,
    requests: List<MediaRequest>?,
): MediaRequest? {
    val mine = requests.orEmpty().filter { equalsNotNull(it.requestedBy?.id, user?.id) }
    return when {
        user.hasPermission(SeerrPermission.MANAGE_REQUESTS) -> mine.firstOrNull()
        user.hasPermission(SeerrPermission.REQUEST) -> mine.firstOrNull { RequestStatus.from(it.status) < RequestStatus.APPROVED }
        else -> null
    }
}

fun canUserCancelRequest(
    user: SeerrUserConfig?,
    requests: List<MediaRequest>?,
) = ownCancellableRequest(user, requests) != null

data class DiscoverMovieState(
    val movie: DataLoadingState<MovieDetails> = DataLoadingState.Pending,
    val rating: DiscoverRating? = null,
    val seasons: List<RequestSeason> = emptyList(),
    val trailers: List<Trailer> = emptyList(),
    val people: List<DiscoverItem> = emptyList(),
    val similar: List<DiscoverItem> = emptyList(),
    val recommended: List<DiscoverItem> = emptyList(),
    val canCancelRequest: Boolean = false,
    val profileLoading: LoadingState = LoadingState.Pending,
    val requestData: SeerrRequestData = SeerrRequestData(),
)
