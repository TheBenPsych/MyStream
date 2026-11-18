package com.github.damontecres.wholphin.ui.detail.music

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.ServerReportService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.ItemRowFields
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import java.util.UUID

@HiltViewModel(assistedFactory = ArtistViewModel.Factory::class)
class ArtistViewModel
    @AssistedInject
    constructor(
        @ApplicationContext context: Context,
        api: ApiClient,
        musicService: MusicService,
        navigationManager: NavigationManager,
        mediaManagementService: MediaManagementService,
        val serverRepository: ServerRepository,
        val serverReportService: ServerReportService,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val userPreferencesService: UserPreferencesService,
        private val backdropService: BackdropService,
        private val imageUrlService: ImageUrlService,
        @Assisted itemId: UUID,
    ) : MusicViewModel(itemId, context, api, musicService, navigationManager, mediaManagementService) {
        @AssistedFactory
        interface Factory {
            fun create(itemId: UUID): ArtistViewModel
        }

        private val _state = MutableStateFlow(ArtistState.EMPTY)
        val state: StateFlow<ArtistState> = _state

        val currentMusic = musicService.state

        init {
            init()
            viewModelScope.launchDefault {
                mediaManagementService.collectCanDelete(state.map { it.artist }) { canDelete ->
                    _state.update { it.copy(canDelete = canDelete) }
                }
            }
        }

        override fun init() {
            viewModelScope.launchIO {
                try {
                    val itemDeferred =
                        async {
                            api.userLibraryApi
                                .getItem(itemId = itemId)
                                .content
                                .let { BaseItem(it, false) }
                        }
                    val albumsDeferred =
                        async {
                            val request =
                                GetItemsRequest(
                                    albumArtistIds = listOf(itemId),
                                    recursive = true,
                                    fields = SlimItemFields,
                                    includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                                    sortBy =
                                        listOf(
                                            ItemSortBy.PREMIERE_DATE,
                                            ItemSortBy.SORT_NAME,
                                        ),
                                    sortOrder = listOf(SortOrder.DESCENDING, SortOrder.ASCENDING),
                                )
                            ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope).init()
                        }
                    val appearsOnDeferred =
                        async {
                            val request =
                                GetItemsRequest(
                                    contributingArtistIds = listOf(itemId),
                                    recursive = true,
                                    fields = SlimItemFields,
                                    includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                                    sortBy =
                                        listOf(
                                            ItemSortBy.PREMIERE_DATE,
                                            ItemSortBy.SORT_NAME,
                                        ),
                                    sortOrder = listOf(SortOrder.DESCENDING, SortOrder.ASCENDING),
                                )
                            ApiRequestPager(
                                api,
                                request,
                                GetItemsRequestHandler,
                                viewModelScope,
                            ).init()
                        }
                    val artist = itemDeferred.await()
                    val albums = albumsDeferred.await()
                    val appearsOn = appearsOnDeferred.await()
                    val imageUrl = imageUrlService.getItemImageUrl(artist, ImageType.PRIMARY)
                    _state.update {
                        it.copy(
                            artist = artist,
                            imageUrl = imageUrl,
                            albums = albums,
                            appearsOnAlbums = appearsOn,
                            loading = LoadingState.Success,
                        )
                    }

                    viewModelScope.launchIO {
                        val request =
                            GetItemsRequest(
                                artistIds = listOf(itemId),
                                fields = SlimItemFields,
                                recursive = true,
                                includeItemTypes = listOf(BaseItemKind.AUDIO),
                                minCommunityRating = 1.0,
                                sortBy =
                                    listOf(
                                        ItemSortBy.COMMUNITY_RATING,
                                    ),
                                sortOrder = listOf(SortOrder.DESCENDING),
                                limit = 10,
                            )
                        val topSongs =
                            GetItemsRequestHandler.execute(api, request).toBaseItems(api, false)
                        if (topSongs.isNotEmpty()) {
                            _state.update {
                                it.copy(topSongs = topSongs)
                            }
                        }
                    }
                    if (state.value.similar.isEmpty()) {
                        viewModelScope.launchIO {
                            val similar =
                                api.libraryApi
                                    .getSimilarItems(
                                        GetSimilarItemsRequest(
                                            userId = serverRepository.currentUser?.id,
                                            itemId = itemId,
                                            excludeArtistIds = listOf(itemId),
                                            fields = SlimItemFields,
                                            limit = 25,
                                        ),
                                    ).content.items
                                    .map { BaseItem.from(it, api) }
                            _state.update { it.copy(similar = similar) }
                        }
                    }
                    updateMusicVideos()
                } catch (ex: Exception) {
                    _state.update { it.copy(loading = LoadingState.Error(ex)) }
                }
            }
        }

        fun refresh() {
            viewModelScope.launchDefault {
                state.value.artist?.let {
                    backdropService.submit(it)
                }
            }
        }

        private fun updateMusicVideos() {
            viewModelScope.launchIO {
                val request =
                    GetItemsRequest(
                        userId = serverRepository.currentUser?.id,
                        artistIds = listOf(itemId),
                        parentId = null,
                        fields = ItemRowFields,
                        recursive = true,
                        includeItemTypes = listOf(BaseItemKind.MUSIC_VIDEO),
                    )
                val musicVideos =
                    GetItemsRequestHandler.execute(api, request).toBaseItems(api, false)
                if (musicVideos.isNotEmpty()) {
                    _state.update {
                        it.copy(musicVideos = musicVideos)
                    }
                }
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            // Refresh if artist, otherwise only could be a music video
            if (itemId == this@ArtistViewModel.itemId) {
                val artist =
                    api.userLibraryApi
                        .getItem(itemId = itemId)
                        .content
                        .let { BaseItem(it, false) }
                _state.update { it.copy(artist = artist) }
            } else {
                updateMusicVideos()
            }
        }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
            favoriteWatchManager.setWatched(itemId, played)
            // Only applies to music videos
            updateMusicVideos()
        }
    }

data class ArtistState(
    val artist: BaseItem?,
    val imageUrl: String?,
    val topSongs: List<BaseItem?>,
    val albums: List<BaseItem?>,
    val appearsOnAlbums: List<BaseItem?>,
    val similar: List<BaseItem>,
    val loading: LoadingState,
    val musicVideos: List<BaseItem?>,
    val canDelete: Boolean,
) {
    companion object {
        val EMPTY =
            ArtistState(
                null,
                null,
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                LoadingState.Pending,
                emptyList(),
                false,
            )
    }
}

private const val HEADER_ROW = 0
private const val SONG_ROW = HEADER_ROW + 1
private const val APPEARS_ON_ROW = SONG_ROW + 1
private const val MUSIC_VIDEO_ROW = APPEARS_ON_ROW + 1
private const val SIMILAR_ROW = MUSIC_VIDEO_ROW + 1
