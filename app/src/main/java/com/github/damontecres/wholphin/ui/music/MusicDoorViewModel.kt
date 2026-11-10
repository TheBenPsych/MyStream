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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.jellyfin.sdk.model.api.ItemSortBy
import timber.log.Timber
import javax.inject.Inject

/** One tile in a row on the Music page */
@Serializable
sealed interface MusicTile {
    val key: String

    @Serializable
    data class Mix(
        val mix: MusicMix,
    ) : MusicTile {
        override val key get() = "mix-${mix.key}"
    }

    @Serializable
    data class Item(
        val item: BaseItem,
    ) : MusicTile {
        override val key get() = item.id.toString()
    }

    @Serializable
    data class Liked(
        val count: Int,
    ) : MusicTile {
        override val key get() = "liked"
    }

    /** Ben's "Ask for music" tile (Requests > Music) */
    @Serializable
    data object Ask : MusicTile {
        override val key get() = "ask"
    }

    /** The end of a row: opens the whole list */
    @Serializable
    data class SeeAll(
        val kind: String,
        val title: String,
    ) : MusicTile {
        override val key get() = "see-all-$kind"
    }
}

@Serializable
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
        private val diskCache: com.github.damontecres.wholphin.services.DiskCache,
        val musicService: MusicService,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(MusicDoorState())
        val state: StateFlow<MusicDoorState> = _state
        private var loadedAt = 0L

        /** The card you were on, so coming Back to Music lands on it again (not the first mix) */
        var lastRow = 0
        var lastIndex = 0

        val isAdmin: Boolean get() = serverRepository.currentUserDto?.policy?.isAdministrator == true

        /** Last time's rows are on screen while fresh ones load behind them */
        @Volatile
        private var fromSnapshot = false

        private fun snapshotKey() = serverRepository.currentUser?.id?.let { "music-door-$it-$SNAPSHOT_FORMAT" }

        fun start() {
            // Benflix speed: Music used to start from nothing every time, waiting on Tower's mixes before
            // anything showed. Last time's page now shows at once and the fresh one replaces it when it's in.
            val key = snapshotKey()
            if (!_state.value.loaded && key != null) {
                memory[key]?.let { (at, rows) ->
                    _state.value = MusicDoorState(rows = rows, loaded = true)
                    fromSnapshot = true
                    if (System.currentTimeMillis() - at < RELOAD_MS) loadedAt = at
                }
            }
            // The mixes change each morning, the rest whenever something is added; a few minutes is fresh enough
            if (_state.value.loaded && System.currentTimeMillis() - loadedAt < RELOAD_MS) {
                // The Liked songs count changes as soon as you press a heart, so it is always fresh
                viewModelScope.launchIO { refreshLiked() }
                return
            }
            viewModelScope.launchIO {
                if (!_state.value.loaded && key != null) {
                    val saved = diskCache.read(key, ListSerializer(MusicRow.serializer()), SNAPSHOT_MAX_AGE_MS)
                    if (!saved.isNullOrEmpty() && !_state.value.loaded) {
                        fromSnapshot = true
                        _state.value = MusicDoorState(rows = saved, loaded = true)
                    }
                }
                load()
            }
        }

        fun retry() {
            loadedAt = 0L
            _state.update { MusicDoorState() }
            start()
        }

        private suspend fun refreshLiked() {
            val count = safe { library.likedCount() } ?: return
            _state.update { st ->
                st.copy(
                    rows =
                        st.rows.map { row ->
                            row.copy(tiles = row.tiles.map { if (it is MusicTile.Liked) MusicTile.Liked(count) else it })
                        },
                )
            }
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

                    // Over last time's page, everything swaps in at once at the end (nothing shrinks while it loads)
                    fun publish() {
                        if (!fromSnapshot) _state.update { it.copy(rows = rows.toList()) }
                    }

                    // Tower's mixes can be slow (or out of reach away from home): the rest of the page doesn't wait
                    // for them, and Made for you slots in at the top when they arrive
                    val earlyMixes = if (fromSnapshot) mixes.await() else withTimeoutOrNull(MIX_WAIT_MS) { mixes.await() }
                    val mixTiles = earlyMixes.orEmpty().map { MusicTile.Mix(it) }
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
                    if (earlyMixes == null) {
                        val late = mixes.await().map { MusicTile.Mix(it) }
                        if (late.isNotEmpty()) rows.add(0, MusicRow("mixes", "Made for you", late))
                    }
                    if (fromSnapshot) {
                        // Only one row (playlists, always there) means nothing came back: keep last time's page
                        if (rows.size > 1) _state.update { it.copy(rows = rows.toList()) }
                        fromSnapshot = false
                    } else {
                        _state.update { it.copy(rows = rows.toList()) }
                    }
                    if (rows.size > 1) {
                        snapshotKey()?.let { key ->
                            memory[key] = System.currentTimeMillis() to rows.toList()
                            diskCache.write(key, ListSerializer(MusicRow.serializer()), rows.toList())
                        }
                    }
                }
                loadedAt = System.currentTimeMillis()
                _state.update { it.copy(loaded = true, noMusic = false, offline = false) }
            } catch (ex: Exception) {
                Timber.w(ex, "Couldn't load the Music page")
                fromSnapshot = false
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
            private const val MIX_WAIT_MS = 2_500L
            private const val SNAPSHOT_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
            private const val SNAPSHOT_FORMAT = 1

            /** Each profile's Music page in memory for this run of the app, with when it was saved */
            private val memory = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<MusicRow>>>()
        }
    }
