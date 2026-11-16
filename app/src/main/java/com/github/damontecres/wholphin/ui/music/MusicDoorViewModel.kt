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
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
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

    /** Music redesign 3: a tile in the Browse row (Decades, Genres, Moods, Throwbacks), opens that group */
    @Serializable
    data class Browse(
        val group: String,
        val title: String,
        val coverAlbumIds: List<String> = emptyList(),
    ) : MusicTile {
        override val key get() = "browse-$group"
    }

    /** All songs, A to Z (Your library) */
    @Serializable
    data object AllSongs : MusicTile {
        override val key get() = "all-songs"
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
    /** Music redesign 3: the 8 quick picks at the top of Home (Liked songs first) */
    val quick: List<MusicTile> = emptyList(),
    /** Every Made for you mix and themed playlist (the Mixes chip) */
    val allMixes: List<MusicTile> = emptyList(),
    /** "Good evening, Ben" */
    val greeting: String = "",
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
        val musicMe: com.github.damontecres.wholphin.services.MusicMe,
    ) : ViewModel() {
        private val _state = MutableStateFlow(MusicDoorState())
        val state: StateFlow<MusicDoorState> = _state
        private var loadedAt = 0L

        /** The card you were on, so coming Back to Music lands on it again (-1 = the quick picks, Liked songs first) */
        var lastRow = -1
        var lastIndex = 0

        /** The Home chip you had picked (All, Mixes, Playlists, Albums, Artists, New to you) */
        var lastChip = "all"

        val isAdmin: Boolean get() = serverRepository.currentUserDto?.policy?.isAdministrator == true

        /** Last time's rows are on screen while fresh ones load behind them */
        @Volatile
        private var fromSnapshot = false

        private fun snapshotKey() =
            serverRepository.currentUser?.id?.let {
                "music-door-$it-${com.github.damontecres.wholphin.services.KidsMode.level}-$SNAPSHOT_FORMAT"
            }

        fun start() {
            // Benflix speed: Music used to start from nothing every time, waiting on Tower's mixes before
            // anything showed. Last time's page now shows at once and the fresh one replaces it when it's in.
            val key = snapshotKey()
            if (!_state.value.loaded && key != null) {
                memory[key]?.let { (at, rows) ->
                    _state.value = _state.value.copy(rows = rows, loaded = true, quick = fallbackQuick(rows))
                    fromSnapshot = true
                    if (System.currentTimeMillis() - at < RELOAD_MS) loadedAt = at
                }
            }
            // The mixes change each morning, the rest whenever something is added; a few minutes is fresh enough
            val name = serverRepository.currentUser?.name.orEmpty()
            _state.update {
                it.copy(
                    greeting =
                        com.github.damontecres.wholphin.ui.main
                            .greetingFor(name),
                )
            }
            if (_state.value.loaded && System.currentTimeMillis() - loadedAt < RELOAD_MS) {
                // The Liked songs count changes as soon as you press a heart, so it is always fresh; what you
                // just played moves to the front of the quick picks and Jump back in
                viewModelScope.launchIO {
                    refreshLiked()
                    refreshRecents()
                }
                return
            }
            viewModelScope.launchIO {
                if (!_state.value.loaded && key != null) {
                    val saved = diskCache.read(key, ListSerializer(MusicRow.serializer()), SNAPSHOT_MAX_AGE_MS)
                    if (!saved.isNullOrEmpty() && !_state.value.loaded) {
                        fromSnapshot = true
                        _state.value = _state.value.copy(rows = saved, loaded = true, quick = fallbackQuick(saved))
                    }
                }
                load()
            }
        }

        fun retry() {
            loadedAt = 0L
            _state.update { MusicDoorState(greeting = it.greeting) }
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
                    quick = st.quick.map { if (it is MusicTile.Liked) MusicTile.Liked(count) else it },
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
                    val themes = async { library.themes() }
                    val playlists = async { safe { library.playlists() } }
                    val liked = async { safe { library.likedCount() } ?: 0 }
                    val recent = async { safe { library.albums(ItemSortBy.DATE_CREATED, descending = true, limit = ROW) } }
                    val top = async { safe { library.mostPlayedSongs(300) } }
                    val me = async { safe { musicMe.load() } }

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
                    // Themed playlists (Ben 2026-11-16): holidays and seasons right under Made for you, the
                    // decades, genres, moods and throwbacks lower down, before Artists. They wait as long as the mixes.
                    val earlyThemes =
                        if (fromSnapshot) themes.await() else withTimeoutOrNull(MIX_WAIT_MS) { themes.await() }

                    fun themeRow(
                        found: com.github.damontecres.wholphin.services.MusicThemes,
                        group: String,
                    ): MusicRow? {
                        val tiles = found.themes.filter { it.group == group }.map { MusicTile.Mix(it) }
                        if (tiles.isEmpty()) return null
                        return MusicRow("theme-$group", found.rowTitles[group] ?: THEME_TITLES[group] ?: "Playlists", tiles)
                    }
                    earlyThemes?.let { found -> themeRow(found, "season")?.let { rows += it } }
                    publish()

                    val playlistTiles =
                        buildList {
                            add(MusicTile.Liked(liked.await()))
                            playlists.await().orEmpty().forEach { add(MusicTile.Item(it)) }
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
                    val lateThemes = earlyThemes ?: themes.await()
                    knownThemes = lateThemes.themes
                    // Music redesign 3 (Ben: shorter Home + one Browse row): Decades, Genres, Moods and Throwbacks
                    // are tiles in one Browse row at the bottom, each opening its playlists
                    val browse =
                        LOWER_THEMES.mapNotNull { group ->
                            val list = lateThemes.themes.filter { it.group == group }
                            if (list.isEmpty()) {
                                null
                            } else {
                                MusicTile.Browse(
                                    group,
                                    lateThemes.rowTitles[group] ?: THEME_TITLES[group] ?: "Playlists",
                                    list
                                        .flatMap { it.coverAlbumIds }
                                        .distinct()
                                        .take(4)
                                        .map { it.toString() },
                                )
                            }
                        }
                    if (earlyThemes == null) {
                        themeRow(lateThemes, "season")?.let { season ->
                            val at = rows.indexOfFirst { it.key == "playlists" }.takeIf { it >= 0 } ?: 0
                            rows.add(at, season)
                        }
                    }
                    publish()
                    val artists =
                        (if (artistIds.isNotEmpty()) safe { library.artists(ids = artistIds) }.orEmpty() else emptyList())
                            .ifEmpty { safe { library.artists(limit = ROW) }.orEmpty() }
                    if (artists.isNotEmpty()) {
                        rows +=
                            MusicRow("artists", "Artists", artists.map { MusicTile.Item(it) } + MusicTile.SeeAll("artists", "All artists"))
                        publish()
                    }
                    if (browse.isNotEmpty()) rows += MusicRow("browse", "Browse", browse)
                    if (earlyMixes == null) {
                        val late = mixes.await().map { MusicTile.Mix(it) }
                        if (late.isNotEmpty()) rows.add(0, MusicRow("mixes", "Made for you", late))
                    }
                    knownMixes = mixes.await()
                    me.await()
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
                refreshRecents()
            } catch (ex: Exception) {
                Timber.w(ex, "Couldn't load the Music page")
                fromSnapshot = false
                _state.update { it.copy(loaded = true, offline = it.rows.isEmpty()) }
            }
        }

        /** Today's mixes and themes, so a mix in Jump back in can show its cover */
        @Volatile
        private var knownMixes: List<MusicMix> = emptyList()

        @Volatile
        private var knownThemes: List<MusicMix> = emptyList()

        /** Before Tower says what you played: Liked songs, what's in season, the mixes, then your playlists */
        private fun fallbackQuick(rows: List<MusicRow>): List<MusicTile> {
            val liked =
                rows
                    .firstOrNull { it.key == "playlists" }
                    ?.tiles
                    ?.filterIsInstance<MusicTile.Liked>()
                    ?.firstOrNull()
            val rest =
                listOf("theme-season", "mixes", "playlists")
                    .flatMap { k -> rows.firstOrNull { it.key == k }?.tiles.orEmpty() }
                    .filter { it is MusicTile.Mix || it is MusicTile.Item }
            return (listOfNotNull(liked ?: MusicTile.Liked(0)) + rest).distinctBy { it.key }.take(QUICK)
        }

        /**
         * Music redesign 3 (approved g2-home, x-recent): the 8 quick picks (Liked songs first, then what you
         * played in the last few days, then what's in season) and the Jump back in row, from what Tower says you
         * played on any screen.
         */
        private suspend fun refreshRecents() {
            try {
                musicMe.load()
                val recents = musicMe.state.value.recents
                val mixesByKey = (knownMixes + knownThemes).associateBy { it.key }
                val ids =
                    recents
                        .filter { it.kind == "album" || it.kind == "playlist" || it.kind == "artist" }
                        .mapNotNull { it.id.toUUIDOrNull() }
                        .distinct()
                        .take(ROW * 2)
                val items = if (ids.isEmpty()) emptyMap() else safe { library.byIds(ids) }.orEmpty().associateBy { it.id.toString() }
                val current = _state.value.rows
                val likedCount =
                    current
                        .firstOrNull { it.key == "playlists" }
                        ?.tiles
                        ?.filterIsInstance<MusicTile.Liked>()
                        ?.firstOrNull()
                        ?.count ?: 0
                val played: List<Pair<MusicTile, com.github.damontecres.wholphin.services.MusicPlace>> =
                    recents.mapNotNull { r ->
                        val tile: MusicTile? =
                            when (r.kind) {
                                "mix" -> mixesByKey[r.id]?.let { MusicTile.Mix(it) }
                                "liked" -> MusicTile.Liked(likedCount)
                                "songs" -> MusicTile.AllSongs
                                else -> items[r.id]?.let { MusicTile.Item(it) }
                            }
                        tile?.let { it to r }
                    }
                val nowSec = System.currentTimeMillis() / 1000.0
                val lately = played.filter { nowSec - it.second.at < QUICK_DAYS * 86_400 }.map { it.first }
                val season = current.firstOrNull { it.key == "theme-season" }?.tiles.orEmpty()
                val quick =
                    (listOf(MusicTile.Liked(likedCount)) + lately + season + fallbackQuick(current))
                        .filter { it !is MusicTile.AllSongs }
                        .distinctBy { it.key }
                        .take(QUICK)
                val jump = played.map { it.first }.distinctBy { it.key }.take(ROW)
                _state.update { st ->
                    val rows = st.rows.filter { it.key != "jump" }.toMutableList()
                    if (jump.size >= 2) {
                        val at = rows.indexOfFirst { it.key == "mixes" }.let { if (it >= 0) it + 1 else 0 }
                        rows.add(at, MusicRow("jump", "Jump back in", jump))
                    }
                    st.copy(
                        rows = rows,
                        quick = quick,
                        allMixes = (knownMixes + knownThemes).map { MusicTile.Mix(it) },
                    )
                }
            } catch (ex: Exception) {
                Timber.w(ex, "Couldn't make the quick picks")
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
            private const val SNAPSHOT_FORMAT = 3
            private const val QUICK = 8
            private const val QUICK_DAYS = 5

            /** The theme rows lower down the page, in this order */
            private val LOWER_THEMES = listOf("decade", "genre", "mood", "throwback")
            private val THEME_TITLES =
                mapOf(
                    "season" to "In season",
                    "decade" to "Decades",
                    "genre" to "Genres",
                    "mood" to "Moods and moments",
                    "throwback" to "Throwbacks",
                )

            /** Each profile's Music page in memory for this run of the app, with when it was saved */
            private val memory = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<MusicRow>>>()
        }
    }
