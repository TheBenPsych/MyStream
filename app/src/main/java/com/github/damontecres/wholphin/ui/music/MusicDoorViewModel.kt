package com.github.damontecres.wholphin.ui.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.MusicLibrary
import com.github.damontecres.wholphin.services.MusicMix
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.launchIO
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.model.api.ItemSortBy
import timber.log.Timber
import javax.inject.Inject

/** One tile in a row on the Music page */
sealed interface MusicTile {
    val key: String

    data class Mix(
        val mix: MusicMix,
    ) : MusicTile {
        override val key get() = "mix-${mix.key}"
    }

    data class Item(
        val item: BaseItem,
    ) : MusicTile {
        override val key get() = item.id.toString()
    }

    data class Liked(
        val count: Int,
    ) : MusicTile {
        override val key get() = "liked"
    }

    /** Ben's "Ask for music" tile (Requests > Music) */
    data object Ask : MusicTile {
        override val key get() = "ask"
    }

    /** The end of a row: opens the whole list */
    data class SeeAll(
        val kind: String,
        val title: String,
    ) : MusicTile {
        override val key get() = "see-all-$kind"
    }
}

data class MusicRow(
    val key: String,
    val title: String,
    val tiles: List<MusicTile>,
)

data class MusicDoorState(
    val rows: List<MusicRow> = emptyList(),
    val loaded: Boolean = false,
    /** No music library on the server */
    val noMusic: Boolean = false,
    val offline: Boolean = false,
)

/**
 * The Music door (approved m2-first): Made for you, Your playlists (Liked songs first, Ask for music at the
 * end on Ben's profile), Recently added, Most played albums, Artists, then Browse by.
 */
@HiltViewModel
class MusicDoorViewModel
    @Inject
    constructor(
        private val library: MusicLibrary,
        private val serverRepository: ServerRepository,
        val musicService: MusicService,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(MusicDoorState())
        val state: StateFlow<MusicDoorState> = _state
        private var loadedAt = 0L

        val isAdmin: Boolean get() = serverRepository.currentUserDto?.policy?.isAdministrator == true

        fun start() {
            // The mixes change each morning, the rest whenever something is added; a few minutes is fresh enough
            if (_state.value.loaded && System.currentTimeMillis() - loadedAt < RELOAD_MS) return
            viewModelScope.launchIO { load() }
        }

        fun retry() {
            loadedAt = 0L
            _state.update { MusicDoorState() }
            start()
        }

        private suspend fun load() {
            try {
                val libraries = library.libraries()
                if (libraries.isEmpty()) {
                    _state.update { it.copy(loaded = true, noMusic = true, rows = emptyList()) }
                    loadedAt = System.currentTimeMillis()
                    return
                }
                kotlinx.coroutines.coroutineScope {
                    val mixes = async { library.mixes() }
                    val playlists = async { safe { library.playlists() } }
                    val liked = async { safe { library.likedCount() } ?: 0 }
                    val recent = async { safe { library.albums(ItemSortBy.DATE_CREATED, descending = true, limit = ROW) } }
                    val top = async { safe { library.mostPlayedSongs(300) } }

                    val rows = mutableListOf<MusicRow>()

                    fun publish() = _state.update { it.copy(rows = rows.toList()) }

                    val mixTiles = mixes.await().map { MusicTile.Mix(it) }
                    if (mixTiles.isNotEmpty()) rows += MusicRow("mixes", "Made for you", mixTiles)
                    publish()

                    val playlistTiles =
                        buildList {
                            add(MusicTile.Liked(liked.await()))
                            playlists.await().orEmpty().forEach { add(MusicTile.Item(it)) }
                            if (isAdmin) add(MusicTile.Ask)
                        }
                    rows += MusicRow("playlists", "Your playlists", playlistTiles)
                    publish()

                    recent.await()?.takeIf { it.isNotEmpty() }?.let { albums ->
                        rows +=
                            MusicRow(
                                "recent",
                                "Recently added",
                                albums.map { MusicTile.Item(it) } + MusicTile.SeeAll("recent", "Recently added"),
                            )
                        publish()
                    }

                    val topSongs = top.await().orEmpty()
                    val albumIds = topSongs.mapNotNull { it.data.albumId }.distinct().take(ROW)
                    if (albumIds.isNotEmpty()) {
                        safe { library.albums(ids = albumIds) }?.takeIf { it.isNotEmpty() }?.let { albums ->
                            rows += MusicRow("played", "Most played albums", albums.map { MusicTile.Item(it) })
                            publish()
                        }
                    }

                    // Artists you play the most first, then everyone else by name
                    val artistIds =
                        topSongs
                            .flatMap { s ->
                                s.data.albumArtists
                                    .orEmpty()
                                    .map { it.id }
                            }.distinct()
                            .take(ROW)
                    val artists =
                        (if (artistIds.isNotEmpty()) safe { library.artists(ids = artistIds) }.orEmpty() else emptyList())
                            .ifEmpty { safe { library.artists(limit = ROW) }.orEmpty() }
                    if (artists.isNotEmpty()) {
                        rows +=
                            MusicRow("artists", "Artists", artists.map { MusicTile.Item(it) } + MusicTile.SeeAll("artists", "All artists"))
                        publish()
                    }
                }
                loadedAt = System.currentTimeMillis()
                _state.update { it.copy(loaded = true, noMusic = false, offline = false) }
            } catch (ex: Exception) {
                Timber.w(ex, "Couldn't load the Music page")
                _state.update { it.copy(loaded = true, offline = it.rows.isEmpty()) }
            }
        }

        private suspend fun <T> safe(block: suspend () -> T): T? =
            try {
                block()
            } catch (ex: Exception) {
                Timber.w(ex, "A Music row didn't load")
                null
            }

        companion object {
            const val ROW = 20
            private const val RELOAD_MS = 5 * 60_000L
        }
    }
