package com.github.damontecres.wholphin.ui.detail.music

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.ExtrasItem
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.ExtrasService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.ServerReportService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.ItemRowFields
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.components.GenreText
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
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
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import java.util.UUID

@HiltViewModel(assistedFactory = AlbumViewModel.Factory::class)
class AlbumViewModel
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
        private val extrasService: ExtrasService,
        @Assisted("itemId") itemId: UUID,
        @Assisted("initialSongId") private val initialSongId: UUID?,
    ) : MusicViewModel(itemId, context, api, musicService, navigationManager, mediaManagementService) {
        @AssistedFactory
        interface Factory {
            fun create(
                @Assisted("itemId") itemId: UUID,
                @Assisted("initialSongId") initialSongId: UUID?,
            ): AlbumViewModel
        }

        private val _state = MutableStateFlow(AlbumState.EMPTY)
        val state: StateFlow<AlbumState> = _state

        val currentMusic = musicService.state

        init {
            init()
            viewModelScope.launchDefault {
                mediaManagementService.collectCanDelete(state.map { it.album }) { canDelete ->
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
                    val songsDeferred = async { getPagerForAlbum(api, itemId) }
                    val album = itemDeferred.await()
                    val songs = songsDeferred.await()
                    val initialSongIndex =
                        initialSongId?.let { id ->
                            songs.indexOfBlocking { it?.id == id }.takeIf { it >= 0 }
                        }
                    val imageUrl = imageUrlService.getItemImageUrl(album, ImageType.PRIMARY)
                    val allArtists =
                        album.data.artists.orEmpty() +
                            album.data.albumArtists
                                ?.map { it.name }
                                .orEmpty()
                    val isVariousArtists =
                        allArtists.firstOrNull { it?.lowercase() == "various artists" } != null ||
                            album.data.artists
                                .orEmpty()
                                .size > 1
                    _state.update {
                        it.copy(
                            album = album,
                            isVariousArtists = isVariousArtists,
                            imageUrl = imageUrl,
                            songs = songs,
                            initialSongIndex = initialSongIndex,
                            loading = LoadingState.Success,
                        )
                    }
                    updateBackDrop()
                    if (state.value.similar.isEmpty()) {
                        viewModelScope.launchIO {
                            val similar =
                                api.libraryApi
                                    .getSimilarItems(
                                        GetSimilarItemsRequest(
                                            userId = serverRepository.currentUser?.id,
                                            itemId = itemId,
                                            excludeArtistIds = album.data.albumArtists?.map { it.id },
                                            fields = SlimItemFields,
                                            limit = 25,
                                        ),
                                    ).content.items
                                    .map { BaseItem.from(it, api) }
                            _state.update { it.copy(similar = similar) }
                        }
                    }
                    viewModelScope.launchIO {
                        val request =
                            GetItemsRequest(
                                userId = serverRepository.currentUser?.id,
                                albumIds = listOf(itemId),
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
                    viewModelScope.launchIO {
                        val extras = extrasService.getExtras(itemId)
                        if (extras.isNotEmpty()) {
                            _state.update {
                                it.copy(extras = extras)
                            }
                        }
                    }
                } catch (ex: Exception) {
                    _state.update { it.copy(loading = LoadingState.Error(ex)) }
                }
            }
        }

        fun updateBackDrop() {
            state.value.album?.let { album ->
                viewModelScope.launchDefault {
                    getBackdropItemForAlbum(api, album)?.let {
                        backdropService.submit(it)
                    }
                }
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            val album =
                api.userLibraryApi
                    .getItem(itemId = itemId)
                    .content
                    .let { BaseItem(it, false) }
            _state.update { it.copy(album = album) }
        }

        fun play(
            shuffled: Boolean,
            startIndex: Int = 0,
        ) {
            val songs = state.value.songs as ApiRequestPager<*>
            play(songs, startIndex, shuffled)
        }
    }

suspend fun getBackdropItemForAlbum(
    api: ApiClient,
    album: BaseItem,
): BaseItem? {
    if (album.data.backdropImageTags?.isNotEmpty() == true) {
        return album
    } else {
        val artistIds =
            album.data.albumArtists
                ?.shuffled()
                ?.take(50)
                ?.map { it.id }
        return api.itemsApi
            .getItems(
                ids = artistIds,
                imageTypes = listOf(ImageType.BACKDROP),
            ).content.items
            .firstOrNull()
            ?.let { BaseItem(it, false) }
    }
}

data class AlbumState(
    val album: BaseItem?,
    val isVariousArtists: Boolean,
    val imageUrl: String?,
    val songs: List<BaseItem?>,
    val similar: List<BaseItem>,
    val loading: LoadingState,
    val initialSongIndex: Int? = null,
    val musicVideos: List<BaseItem?> = emptyList(),
    val extras: List<ExtrasItem> = emptyList(),
    val canDelete: Boolean = false,
) {
    companion object {
        val EMPTY = AlbumState(null, false, null, emptyList(), emptyList(), LoadingState.Pending)
    }
}

private const val HEADER_ROW = 0
private const val SONG_ROW = HEADER_ROW + 1
private const val MUSIC_VIDEO_ROW = SONG_ROW + 1
private const val EXTRAS_ROW = SONG_ROW + 1
private const val SIMILAR_ROW = EXTRAS_ROW + 1

@Composable
fun AlbumHeader(
    album: BaseItem,
    imageUrl: String?,
    overviewOnClick: () -> Unit,
    bringIntoViewRequester: BringIntoViewRequester,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.padding(top = 32.dp),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier =
                Modifier
                    .fillMaxWidth(.20f)
                    .clip(BenflixShapes.Panel),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Artist
            Text(
                text = album.artistsString ?: "",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth(.75f)
                        .padding(start = 8.dp),
            )
            Text(
                text = album.name ?: "",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth(.75f)
                        .padding(start = 8.dp),
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(.60f),
            ) {
                QuickDetails(
                    album.ui.quickDetails,
                    null,
                    Modifier.padding(start = 8.dp),
                )

                album.data.genres?.letNotEmpty {
                    GenreText(it, Modifier.padding(start = 8.dp))
                }

                // Description
                album.data.overview?.let { overview ->
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
}
