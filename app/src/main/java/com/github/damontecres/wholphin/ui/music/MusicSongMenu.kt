package com.github.damontecres.wholphin.ui.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.onMain
import dagger.hilt.android.lifecycle.HiltViewModel
import org.jellyfin.sdk.model.api.BaseItemKind
import javax.inject.Inject

/** What the music pages can do with a song (the More menu, the heart, playing lists) */
@HiltViewModel
class MusicActionsViewModel
    @Inject
    constructor(
        val musicService: MusicService,
        val navigationManager: NavigationManager,
        private val serverRepository: ServerRepository,
    ) : ViewModel() {
        val isAdmin: Boolean get() = serverRepository.currentUserDto?.policy?.isAdministrator == true

        fun playList(
            songs: List<BaseItem>,
            startIndex: Int,
            shuffled: Boolean,
            source: BaseItem?,
            sourceName: String?,
        ) {
            viewModelScope.launchIO {
                musicService.playList(songs, startIndex, shuffled, source?.id ?: songs.getOrNull(startIndex)?.id, sourceName)
            }
        }

        /** Runs [block] in the background (for things that load songs first, like Play top songs) */
        fun viewModelScopeLaunch(block: suspend () -> Unit) = viewModelScope.launchIO { block() }

        fun playNext(song: BaseItem) = viewModelScope.launchIO { musicService.playNextSong(song) }

        fun addToUpNext(song: BaseItem) = viewModelScope.launchIO { musicService.addToUpNext(song) }

        fun setLiked(
            song: BaseItem,
            liked: Boolean,
            then: () -> Unit = {},
        ) = viewModelScope.launchIO {
            musicService.setLiked(song.id, liked)
            onMain { then() }
        }

        fun goToArtist(song: BaseItem) {
            val artist = song.data.albumArtists?.firstOrNull() ?: song.data.artistItems?.firstOrNull() ?: return
            navigationManager.navigateTo(Destination.MediaItem(artist.id, BaseItemKind.MUSIC_ARTIST))
        }

        fun goToAlbum(song: BaseItem) {
            val album = song.data.albumId ?: return
            navigationManager.navigateTo(Destination.MediaItem(album, BaseItemKind.MUSIC_ALBUM))
        }

        fun songInfo(song: BaseItem) = navigationManager.navigateTo(Destination.MediaItem(song.id, BaseItemKind.AUDIO))
    }

/**
 * A song's More menu (approved m3-more): Play next, Add to Up next, Add to a playlist, Like, Go to artist,
 * Go to album, and Song info (only on Ben's profile). Own playlists also get "Remove from this playlist".
 */
@Composable
fun SongMenu(
    song: BaseItem,
    liked: Boolean,
    onDismiss: () -> Unit,
    onLikedChanged: (Boolean) -> Unit = {},
    onRemoveFromPlaylist: (() -> Unit)? = null,
    actions: MusicActionsViewModel = hiltViewModel(),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    var pickPlaylist by remember { mutableStateOf(false) }
    if (pickPlaylist) {
        val playlistState by playlistViewModel.playlistState.collectAsState()
        PlaylistDialog(
            title = "Add to a playlist",
            state = playlistState,
            onDismissRequest = {
                pickPlaylist = false
                onDismiss()
            },
            onClick = {
                playlistViewModel.addToPlaylist(it.id, song.id)
                pickPlaylist = false
                onDismiss()
            },
            onSearch = playlistViewModel::loadPlaylists,
            createEnabled = true,
            onCreatePlaylist = {
                playlistViewModel.createPlaylistAndAddItem(it, song.id)
                pickPlaylist = false
                onDismiss()
            },
        )
        return
    }
    val items =
        buildList {
            add(
                DialogItem(headlineContent = { Text("Play next") }, onClick = {
                    actions.playNext(song)
                    onDismiss()
                }),
            )
            add(
                DialogItem(headlineContent = { Text("Add to Up next") }, onClick = {
                    actions.addToUpNext(song)
                    onDismiss()
                }),
            )
            add(
                DialogItem(
                    headlineContent = { Text("Add to a playlist") },
                    onClick = {
                        playlistViewModel.loadPlaylists()
                        pickPlaylist = true
                    },
                ),
            )
            add(
                DialogItem(
                    headlineContent = { Text(if (liked) "Unlike" else "Like") },
                    onClick = {
                        actions.setLiked(song, !liked) { onLikedChanged(!liked) }
                        onDismiss()
                    },
                ),
            )
            if (onRemoveFromPlaylist != null) {
                add(
                    DialogItem(headlineContent = { Text("Remove from this playlist") }, onClick = {
                        onRemoveFromPlaylist()
                        onDismiss()
                    }),
                )
            }
            if (!song.data.albumArtists.isNullOrEmpty() || !song.data.artistItems.isNullOrEmpty()) {
                add(
                    DialogItem(headlineContent = { Text("Go to artist") }, onClick = {
                        actions.goToArtist(song)
                        onDismiss()
                    }),
                )
            }
            if (song.data.albumId != null) {
                add(
                    DialogItem(headlineContent = { Text("Go to album") }, onClick = {
                        actions.goToAlbum(song)
                        onDismiss()
                    }),
                )
            }
            if (actions.isAdmin) {
                add(
                    DialogItem(headlineContent = { Text("Song info") }, onClick = {
                        actions.songInfo(song)
                        onDismiss()
                    }),
                )
            }
        }
    DialogPopup(
        showDialog = true,
        title = song.title.orEmpty(),
        dialogItems = items,
        onDismissRequest = onDismiss,
        // "Add to a playlist" opens the playlist picker in place of this menu
        dismissOnClick = false,
    )
}
