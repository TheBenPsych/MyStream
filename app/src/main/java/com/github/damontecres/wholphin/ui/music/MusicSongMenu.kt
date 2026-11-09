package com.github.damontecres.wholphin.ui.music

import android.content.Context
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
import com.github.damontecres.wholphin.services.BenflixMusic
import com.github.damontecres.wholphin.services.DownloadService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.onMain
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import javax.inject.Inject

/** What the music pages can do with a song (the More menu, the heart, playing lists) */
@HiltViewModel
class MusicActionsViewModel
    @Inject
    constructor(
        val musicService: MusicService,
        val navigationManager: NavigationManager,
        private val serverRepository: ServerRepository,
        val downloads: DownloadService,
        private val benflixMusic: BenflixMusic,
        @param:ApplicationContext private val context: Context,
    ) : ViewModel() {
        /**
         * Ben only: deletes this song's file on Tower and has Lidarr (or Soulseek) find a better copy. The bad
         * copy is never picked again. Shows Tower's answer as a short message; [then] runs once it's deleted.
         */
        fun deleteAndFindAgain(
            song: BaseItem,
            then: () -> Unit = {},
        ) = viewModelScope.launchIO {
            try {
                val words = benflixMusic.deleteAndFindAgain(song.id)
                showToast(context, words)
                onMain { then() }
            } catch (ex: Exception) {
                Timber.w(ex, "Delete and find again")
                showToast(context, ex.message ?: "Couldn't delete that song. Try again in a minute.")
            }
        }

        /** Benflix on phones: save songs for listening with no internet (Wi-Fi only, like movies) */
        fun download(songs: List<BaseItem>) = viewModelScope.launchIO { downloads.startAll(songs) }

        fun removeDownload(song: BaseItem) = downloads.delete(song.id)

        val isAdmin: Boolean get() = serverRepository.currentUserDto?.policy?.isAdministrator == true

        fun playList(
            songs: List<BaseItem>,
            startIndex: Int,
            shuffled: Boolean,
            source: BaseItem?,
            sourceName: String?,
            openNow: Boolean = false,
        ) {
            viewModelScope.launchIO {
                musicService.playList(songs, startIndex, shuffled, source?.id ?: songs.getOrNull(startIndex)?.id, sourceName)
                // Ben: Play or Shuffle goes straight to Now playing (once the music has started)
                if (openNow) onMain { navigationManager.navigateTo(Destination.NowPlaying) }
            }
        }

        /** Runs [block] in the background (for things that load songs first, like Play top songs) */
        fun viewModelScopeLaunch(block: suspend () -> Unit) = viewModelScope.launchIO { block() }

        fun playNext(song: BaseItem) = viewModelScope.launchIO { musicService.playNextSong(song) }

        fun addToUpNext(song: BaseItem) = viewModelScope.launchIO { musicService.addToUpNext(song) }

        fun addAllToUpNext(songs: List<BaseItem>) = viewModelScope.launchIO { musicService.addAllToUpNext(songs) }

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

        fun openNowPlaying() = navigationManager.navigateTo(Destination.NowPlaying)

        fun songInfo(song: BaseItem) = navigationManager.navigateTo(Destination.MediaItem(song.id, BaseItemKind.AUDIO))
    }

/**
 * A song's More menu (approved m3-more): Play next, Add to Up next, Add to a playlist, Like, Go to artist,
 * Go to album, and Song info and "Delete and find again" (only on Ben's profile). Own playlists also get
 * "Remove from this playlist". [onDeleted] runs once Tower has deleted a bad copy (so the page can drop the song).
 */
@Composable
fun SongMenu(
    song: BaseItem,
    liked: Boolean,
    onDismiss: () -> Unit,
    onLikedChanged: (Boolean) -> Unit = {},
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onDeleted: () -> Unit = {},
    actions: MusicActionsViewModel = hiltViewModel(),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    var pickPlaylist by remember { mutableStateOf(false) }
    var confirmRedo by remember { mutableStateOf(false) }
    val isTv = rememberIsTv()
    if (confirmRedo) {
        ConfirmDialog(
            title = "Delete and find again?",
            body =
                "This deletes \"${song.title.orEmpty()}\" from Tower and finds a new copy. The bad copy won't be " +
                    "picked again. It shows up again in a few minutes (Requests > Music shows how it's going).",
            onCancel = {
                confirmRedo = false
                onDismiss()
            },
            onConfirm = {
                actions.deleteAndFindAgain(song, onDeleted)
                confirmRedo = false
                onDismiss()
            },
            confirmStringRes = R.string.delete,
        )
        return
    }
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
            if (!isTv) {
                val saved = actions.downloads.find(song.id) != null
                add(
                    DialogItem(headlineContent = { Text(if (saved) "Remove from this phone" else "Download to this phone") }, onClick = {
                        if (saved) actions.removeDownload(song) else actions.download(listOf(song))
                        onDismiss()
                    }),
                )
            }
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
                // A bad copy (wrong song, cut off, bad sound): Tower deletes it and finds another one
                add(DialogItem(headlineContent = { Text("Delete and find again") }, onClick = { confirmRedo = true }))
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
