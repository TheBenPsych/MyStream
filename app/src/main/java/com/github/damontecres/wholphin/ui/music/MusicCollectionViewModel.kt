package com.github.damontecres.wholphin.ui.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.MusicLibrary
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.launchIO
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.playlistsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/** What an album, playlist, made-for-you mix or Liked songs page shows */
enum class CollectionKind {
    ALBUM,
    PLAYLIST,
    MIX,
    LIKED,
}

data class MusicCollectionState(
    val kind: CollectionKind = CollectionKind.ALBUM,
    val item: BaseItem? = null,
    val title: String = "",
    val songs: List<BaseItem> = emptyList(),
    /** Album pages: the other albums by the same artist, then similar albums on Benflix */
    val moreByArtist: List<BaseItem> = emptyList(),
    val similar: List<BaseItem> = emptyList(),
    /** Up to four album ids for a playlist or mix collage */
    val collage: List<UUID> = emptyList(),
    /** The words under a mix name */
    val mixLine: String = "",
    val liked: Set<UUID> = emptySet(),
    val loaded: Boolean = false,
    val failed: Boolean = false,
    /** A playlist the person made themselves (it gets Edit) */
    val canEdit: Boolean = false,
    /** Not a music playlist after all (a movie playlist): the old playlist page shows instead */
    val notMusic: Boolean = false,
)

@HiltViewModel
class MusicCollectionViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val library: MusicLibrary,
        private val serverRepository: ServerRepository,
        private val favoriteWatchManager: FavoriteWatchManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(MusicCollectionState())
        val state: StateFlow<MusicCollectionState> = _state
        private var loadedFor: String? = null

        fun load(
            kind: CollectionKind,
            id: String?,
            title: String,
        ) {
            val key = "$kind-$id"
            if (loadedFor == key) return
            loadedFor = key
            _state.value = MusicCollectionState(kind = kind, title = title)
            viewModelScope.launchIO {
                try {
                    when (kind) {
                        CollectionKind.ALBUM -> loadAlbum(UUID.fromString(id))
                        CollectionKind.PLAYLIST -> loadPlaylist(UUID.fromString(id))
                        CollectionKind.LIKED -> loadLiked()
                        CollectionKind.MIX -> loadMix(id.orEmpty(), title)
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "Couldn't load music %s %s", kind, id)
                    _state.update { it.copy(loaded = true, failed = true) }
                }
            }
        }

        fun reload() {
            val s = _state.value
            val id =
                when (s.kind) {
                    CollectionKind.MIX -> loadedFor?.substringAfter("-")
                    else -> s.item?.id?.toString()
                }
            loadedFor = null
            load(s.kind, id, s.title)
        }

        private fun likedOf(songs: List<BaseItem>) = songs.filter { it.favorite }.map { it.id }.toSet()

        private suspend fun loadAlbum(id: UUID) {
            val album = BaseItem(api.userLibraryApi.getItem(itemId = id).content, false)
            val songs = library.albumSongs(id)
            _state.update {
                it.copy(item = album, title = album.title.orEmpty(), songs = songs, liked = likedOf(songs), loaded = true)
            }
            val artistId =
                album.data.albumArtists
                    ?.firstOrNull()
                    ?.id
            if (artistId != null) {
                try {
                    val more =
                        library
                            .albums(
                                artistId = artistId,
                                sort = org.jellyfin.sdk.model.api.ItemSortBy.PRODUCTION_YEAR,
                                descending = true,
                            ).filter {
                                it.id !=
                                    id
                            }
                    _state.update { it.copy(moreByArtist = more) }
                } catch (ex: Exception) {
                    Timber.w(ex, "More by artist")
                }
            }
            try {
                val similar =
                    api.libraryApi
                        .getSimilarItems(
                            GetSimilarItemsRequest(
                                userId = serverRepository.currentUser?.id,
                                itemId = id,
                                excludeArtistIds = album.data.albumArtists?.map { it.id },
                                fields = SlimItemFields,
                                limit = 20,
                            ),
                        ).content.items
                        .map { BaseItem(it, false) }
                _state.update { it.copy(similar = similar) }
            } catch (ex: Exception) {
                Timber.w(ex, "Similar albums")
            }
        }

        private suspend fun loadPlaylist(id: UUID) {
            val playlist = BaseItem(api.userLibraryApi.getItem(itemId = id).content, false)
            if (playlist.data.mediaType == MediaType.VIDEO) {
                _state.update { it.copy(item = playlist, notMusic = true, loaded = true) }
                return
            }
            val songs = library.playlistSongs(id)
            val userId = serverRepository.currentUser?.id
            val canEdit =
                try {
                    userId != null &&
                        api.playlistsApi
                            .getPlaylistUser(id, userId)
                            .content.canEdit
                } catch (ex: Exception) {
                    false
                }
            _state.update {
                it.copy(
                    item = playlist,
                    title = playlist.title.orEmpty(),
                    songs = songs,
                    liked = likedOf(songs),
                    collage = songs.mapNotNull { s -> s.data.albumId }.distinct().take(4),
                    canEdit = canEdit,
                    loaded = true,
                )
            }
        }

        private suspend fun loadLiked() {
            val songs = library.likedSongs()
            _state.update {
                it.copy(title = "Liked songs", songs = songs, liked = songs.map { s -> s.id }.toSet(), loaded = true)
            }
        }

        private suspend fun loadMix(
            key: String,
            title: String,
        ) {
            val mix = library.mixes().firstOrNull { it.key == key }
            if (mix == null) {
                _state.update { it.copy(loaded = true, failed = true) }
                return
            }
            val songs = library.songs(mix.songIds)
            _state.update {
                it.copy(
                    title = mix.name.ifBlank { title },
                    songs = songs,
                    liked = likedOf(songs),
                    collage = mix.coverAlbumIds,
                    mixLine = mix.line,
                    loaded = true,
                )
            }
        }

        fun likedChanged(
            id: UUID,
            liked: Boolean,
        ) {
            _state.update { if (liked) it.copy(liked = it.liked + id) else it.copy(liked = it.liked - id) }
        }

        /** The heart on an album page: the album in your favourites */
        fun toggleAlbumFavourite() {
            val album = _state.value.item ?: return
            viewModelScope.launchIO {
                try {
                    val data = favoriteWatchManager.setFavorite(album.id, !album.favorite)
                    _state.update { it.copy(item = album.copy(data = album.data.copy(userData = data))) }
                } catch (ex: Exception) {
                    Timber.w(ex, "Album favourite")
                }
            }
        }

        // ---------- Editing your own playlist ----------

        fun removeFromPlaylist(index: Int) {
            val playlist = _state.value.item ?: return
            val song = _state.value.songs.getOrNull(index) ?: return
            val entryId = song.data.playlistItemId ?: return
            viewModelScope.launchIO {
                try {
                    api.playlistsApi.removeItemFromPlaylist(playlist.id.toString(), listOf(entryId))
                    _state.update { it.copy(songs = it.songs.filterIndexed { i, _ -> i != index }) }
                } catch (ex: Exception) {
                    Timber.w(ex, "Remove from playlist")
                }
            }
        }

        fun move(
            index: Int,
            by: Int,
        ) {
            val playlist = _state.value.item ?: return
            val songs = _state.value.songs
            val to = index + by
            if (to !in songs.indices) return
            val entryId = songs[index].data.playlistItemId ?: return
            _state.update {
                val list = it.songs.toMutableList()
                val s = list.removeAt(index)
                list.add(to, s)
                it.copy(songs = list)
            }
            viewModelScope.launchIO {
                try {
                    api.playlistsApi.moveItem(playlist.id.toString(), entryId, to)
                } catch (ex: Exception) {
                    Timber.w(ex, "Move in playlist")
                }
            }
        }

        fun rename(name: String) {
            val playlist = _state.value.item ?: return
            if (name.isBlank()) return
            viewModelScope.launchIO {
                try {
                    api.playlistsApi.updatePlaylist(
                        playlist.id,
                        org.jellyfin.sdk.model.api
                            .UpdatePlaylistDto(name = name.trim()),
                    )
                    _state.update { it.copy(title = name.trim()) }
                } catch (ex: Exception) {
                    Timber.w(ex, "Rename playlist")
                }
            }
        }
    }
