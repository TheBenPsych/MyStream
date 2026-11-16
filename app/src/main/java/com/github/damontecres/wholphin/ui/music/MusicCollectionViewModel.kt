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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

    /** Browse by > Songs: every song on Benflix, A to Z */
    SONGS,
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
    /** A themed playlist (holiday, decade, genre, mood...) rather than a Made for you mix */
    val themed: Boolean = false,
    /** Ben's Songs to add under a themed playlist, and whether they're still coming */
    val adds: List<com.github.damontecres.wholphin.services.MusicSong> = emptyList(),
    val addsLoading: Boolean = false,
    /** Not Ben's profile: an ask waits for Ben to okay it */
    val askNeedsOk: Boolean = false,
    val liked: Set<UUID> = emptySet(),
    val loaded: Boolean = false,
    val failed: Boolean = false,
    /** A playlist the person made themselves (it gets Edit) */
    val canEdit: Boolean = false,
    /** Not a music playlist after all (a movie playlist): the old playlist page shows instead */
    val notMusic: Boolean = false,
    /** All songs: how many songs there are while the rest of the list is still coming in (null once it's all here) */
    val stillLoadingTotal: Int? = null,
    /** Album pages: how many songs the whole album has, when Benflix only has some of them ("3 of 12 songs") */
    val albumTotal: Int? = null,
    /** The catalogue's id for that album, so Ask for the rest can ask for it (null when unknown) */
    val albumAskId: Long? = null,
    /** Words after Ask for the rest was pressed, like "Asked for the rest" */
    val albumAsked: String? = null,
    /**
     * Music redesign 3: the whole album in its real order, the songs Benflix has and the ones it doesn't (greyed,
     * with a preview and Ask). Empty when Benflix has the whole album (or the catalogue can't be reached).
     */
    val albumLines: List<AlbumLine> = emptyList(),
    /** Your own playlists: songs asked for from Songs to add that will join it once they're in */
    val waitingAdds: Int = 0,
)

/** One line of an album's song list */
sealed interface AlbumLine {
    /** A song Benflix has ([index] in the page's songs) */
    data class Have(
        val index: Int,
    ) : AlbumLine

    /** A song Benflix doesn't have yet */
    data class Missing(
        val song: com.github.damontecres.wholphin.services.MusicSong,
        val number: Int,
    ) : AlbumLine
}

/** The album's songs in the catalogue's order, matched up with the ones Benflix has */
fun albumLines(
    songs: List<BaseItem>,
    catalogue: List<com.github.damontecres.wholphin.services.MusicSong>,
): List<AlbumLine> {
    if (catalogue.isEmpty()) return emptyList()
    val byKey = HashMap<String, MutableList<Int>>()
    songs.forEachIndexed { i, s -> byKey.getOrPut(songKey(s.title)) { mutableListOf() }.add(i) }
    val used = HashSet<Int>()
    val out = mutableListOf<AlbumLine>()
    catalogue.forEachIndexed { n, c ->
        val idx = byKey[songKey(c.title)]?.firstOrNull { it !in used }
        when {
            idx != null -> {
                used.add(idx)
                out.add(AlbumLine.Have(idx))
            }

            c.have -> {}

            // In Music under another name: it shows with the songs Benflix has, below

            else -> {
                out.add(AlbumLine.Missing(c, n + 1))
            }
        }
    }
    songs.indices.filter { it !in used }.forEach { out.add(AlbumLine.Have(it)) }
    return if (out.none { it is AlbumLine.Missing }) emptyList() else out
}

@HiltViewModel
class MusicCollectionViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val library: MusicLibrary,
        private val serverRepository: ServerRepository,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val benflixMusic: com.github.damontecres.wholphin.services.BenflixMusic,
        /** 30-second tastes of Songs to add (Ben 2026-11-16) */
        val preview: com.github.damontecres.wholphin.services.SongPreview,
        private val musicMe: com.github.damontecres.wholphin.services.MusicMe,
    ) : ViewModel() {
        private val _state = MutableStateFlow(MusicCollectionState())
        val state: StateFlow<MusicCollectionState> = _state
        private var loadedFor: String? = null
        private var loadJob: Job? = null

        fun load(
            kind: CollectionKind,
            id: String?,
            title: String,
        ) {
            val key = "$kind-$id"
            // Liked songs always loads again, so a song you just gave a heart is there straight away
            if (loadedFor == key && kind != CollectionKind.LIKED) return
            loadedFor = key
            _state.value = MusicCollectionState(kind = kind, title = title)
            // A page still filling in (All songs) stops before the new one starts
            loadJob?.cancel()
            loadJob =
                viewModelScope.launchIO {
                    try {
                        when (kind) {
                            CollectionKind.ALBUM -> loadAlbum(UUID.fromString(id))
                            CollectionKind.PLAYLIST -> loadPlaylist(UUID.fromString(id))
                            CollectionKind.LIKED -> loadLiked()
                            CollectionKind.MIX -> loadMix(id.orEmpty(), title)
                            CollectionKind.SONGS -> loadAllSongs()
                        }
                    } catch (ex: CancellationException) {
                        throw ex
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
            // Only some songs of the album (asked for one by one): "3 of 12 songs"
            val albumArtist = album.data.albumArtist ?: album.data.artists?.firstOrNull()
            if (!albumArtist.isNullOrBlank() && !album.title.isNullOrBlank() && songs.isNotEmpty()) {
                try {
                    val (total, askId) = benflixMusic.albumFind(albumArtist, album.title.orEmpty())
                    if (total > songs.size) _state.update { it.copy(albumTotal = total, albumAskId = askId) }
                    // Music redesign 3: the songs it doesn't have show greyed in their place, with a preview and Ask
                    if (total > songs.size && askId != null) {
                        val catalogue = benflixMusic.album(askId).second
                        _state.update { it.copy(albumLines = albumLines(it.songs, catalogue)) }
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.d(ex, "Album size")
                }
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
            if (canEdit && userId != null) {
                joinArrivedAdds(id, userId)
                ownPlaylistAdds()
            }
        }

        /** Songs asked for from this playlist's Songs to add that are on Benflix now join it (Music redesign 3) */
        private suspend fun joinArrivedAdds(
            id: UUID,
            userId: UUID,
        ) {
            try {
                musicMe.load(force = true)
                val key = id.toString().replace("-", "")
                val waiting =
                    musicMe.state.value.adds[key]
                        .orEmpty()
                var joined = 0
                for ((artist, title) in waiting) {
                    val found =
                        library
                            .search(title, org.jellyfin.sdk.model.api.BaseItemKind.AUDIO, 10)
                            .firstOrNull { s ->
                                songKey(s.title) == songKey(title) &&
                                    (
                                        artist.isBlank() ||
                                            s.data.artists
                                                .orEmpty()
                                                .any { it.contains(artist, true) || artist.contains(it, true) }
                                    )
                            } ?: continue
                    if (_state.value.songs.none { it.id == found.id }) {
                        api.playlistsApi.addItemToPlaylist(id, listOf(found.id), userId)
                        joined++
                    }
                    musicMe.addLater(key, artist, title, remove = true)
                }
                if (joined > 0) {
                    val songs = library.playlistSongs(id)
                    _state.update { it.copy(songs = songs, liked = likedOf(songs)) }
                }
                _state.update {
                    it.copy(
                        waitingAdds =
                            musicMe.state.value.adds[key]
                                .orEmpty()
                                .size,
                    )
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Songs to add that came in")
            }
        }

        /** Songs to add under your own playlist: songs like a few of its songs, not on Benflix yet */
        private suspend fun ownPlaylistAdds() {
            val seeds =
                _state.value.songs
                    .shuffled()
                    .take(3)
            if (seeds.isEmpty()) return
            _state.update { it.copy(addsLoading = true, askNeedsOk = serverRepository.currentUserDto?.policy?.isAdministrator != true) }
            val found = LinkedHashMap<Long, com.github.damontecres.wholphin.services.MusicSong>()
            for (s in seeds) {
                val artist = s.data.albumArtist ?: s.data.artists?.firstOrNull() ?: continue
                try {
                    benflixMusic.similar(artist, s.title.orEmpty()).filter { !it.have }.forEach { found.putIfAbsent(it.id, it) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.d(ex, "Songs to add")
                }
            }
            _state.update { it.copy(adds = found.values.take(10), addsLoading = false) }
        }

        /** Songs to add on your own playlist: ask, and it joins the playlist once it's in */
        private fun askToAddHere(song: com.github.damontecres.wholphin.services.MusicSong) {
            val playlist = _state.value.item ?: return
            _state.update { st -> st.copy(adds = st.adds.map { if (it.id == song.id) it.copy(asked = "Asking...") else it }) }
            viewModelScope.launchIO {
                val words =
                    try {
                        val r = benflixMusic.ask("song", song.id)
                        musicMe.addLater(playlist.id.toString().replace("-", ""), song.artist, song.title)
                        _state.update { it.copy(waitingAdds = it.waitingAdds + 1) }
                        r.words.ifBlank { "Asked ✓" }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Couldn't ask for %s", song.title)
                        null
                    }
                _state.update { st -> st.copy(adds = st.adds.map { if (it.id == song.id) it.copy(asked = words) else it }) }
            }
        }

        private suspend fun loadLiked() {
            val songs = library.likedSongs()
            _state.update {
                it.copy(title = "Liked songs", songs = songs, liked = songs.map { s -> s.id }.toSet(), loaded = true)
            }
        }

        private suspend fun loadAllSongs() {
            // The first songs show straight away; the rest come in behind them in big pages
            // (before, all 1,235 songs and their full playback info came in one go: about 20 seconds)
            val seen = SameSongFilter()
            val (first, total) = library.allSongsPage(0, FIRST_PAGE)
            val firstSongs = first.filter { seen.isNew(it) }
            val covers =
                firstSongs
                    .mapNotNull { it.data.albumId }
                    .distinct()
                    .shuffled()
                    .take(4)
            var fetched = first.size
            _state.update {
                it.copy(
                    title = "All songs",
                    songs = firstSongs,
                    liked = likedOf(firstSongs),
                    collage = covers,
                    loaded = true,
                    stillLoadingTotal = total.takeIf { fetched < total },
                )
            }
            try {
                while (fetched < total) {
                    val (page, _) = library.allSongsPage(fetched, NEXT_PAGE)
                    if (page.isEmpty()) break
                    fetched += page.size
                    val more = page.filter { seen.isNew(it) }
                    _state.update {
                        it.copy(
                            songs = it.songs + more,
                            liked = it.liked + likedOf(more),
                            stillLoadingTotal = total.takeIf { fetched < total },
                        )
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                // Keep the songs that did come in rather than showing "Couldn't load this"
                Timber.w(ex, "Couldn't load the rest of All songs")
            }
            _state.update { it.copy(stillLoadingTotal = null) }
        }

        private suspend fun loadMix(
            key: String,
            title: String,
        ) {
            val mix = library.mixOrTheme(key)
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
                    themed = mix.group.isNotBlank(),
                    addsLoading = mix.adds,
                    askNeedsOk = serverRepository.currentUserDto?.policy?.isAdministrator != true,
                    loaded = true,
                )
            }
            if (mix.adds) {
                val adds =
                    try {
                        benflixMusic.themeAdds(key)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Couldn't get Songs to add for %s", key)
                        emptyList()
                    }
                _state.update { it.copy(adds = adds, addsLoading = false) }
            }
        }

        /** Ben 2026-11-16: an album Benflix only has some songs of asks for the whole album (songs you have are kept) */
        fun askForRest() {
            val id = _state.value.albumAskId ?: return
            if (_state.value.albumAsked != null) return
            _state.update { it.copy(albumAsked = "Asking...") }
            viewModelScope.launchIO {
                val words =
                    try {
                        benflixMusic.ask("album", id)
                        if (serverRepository.currentUserDto?.policy?.isAdministrator ==
                            true
                        ) {
                            "Asked for the rest"
                        } else {
                            "Asked Ben for the rest"
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Ask for the rest")
                        null
                    }
                _state.update { st ->
                    st.copy(
                        albumAsked = words,
                        albumLines =
                            if (words == null) {
                                st.albumLines
                            } else {
                                st.albumLines.map { l ->
                                    if (l is AlbumLine.Missing && l.song.asked == null) l.copy(song = l.song.copy(asked = "Asked ✓")) else l
                                }
                            },
                    )
                }
            }
        }

        /** Ask on one greyed song of an album (Music redesign 3) */
        fun askForSong(song: com.github.damontecres.wholphin.services.MusicSong) {
            if (song.asked != null) return
            preview.stop()
            setAlbumAsked(song.id, "Asking...")
            viewModelScope.launchIO {
                val words =
                    try {
                        benflixMusic.ask("song", song.id).words.ifBlank { "Asked ✓" }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Ask for %s", song.title)
                        null
                    }
                setAlbumAsked(song.id, words)
            }
        }

        private fun setAlbumAsked(
            id: Long,
            words: String?,
        ) = _state.update { st ->
            st.copy(
                albumLines =
                    st.albumLines.map { l ->
                        if (l is AlbumLine.Missing && l.song.id == id) l.copy(song = l.song.copy(asked = words)) else l
                    },
            )
        }

        /** Not for this playlist (Ben 2026-11-16): the song leaves this made-for-you or themed playlist for good */
        fun notForThisPlaylist(index: Int) {
            if (_state.value.kind != CollectionKind.MIX) return
            val key = loadedFor?.substringAfter("-") ?: return
            val song = _state.value.songs.getOrNull(index) ?: return
            _state.update { it.copy(songs = it.songs.filterNot { s -> s.id == song.id }) }
            viewModelScope.launchIO {
                try {
                    benflixMusic.hideFromMix(key, song.id.toString().replace("-", ""))
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Not for this playlist")
                }
            }
        }

        /** Songs to add: asks for the song; once it's downloaded it joins this themed playlist by itself */
        fun askToAdd(song: com.github.damontecres.wholphin.services.MusicSong) {
            if (song.asked != null || song.have) return
            preview.stop()
            if (_state.value.kind == CollectionKind.PLAYLIST) return askToAddHere(song)
            val key = loadedFor?.substringAfter("-") ?: return
            _state.update { st -> st.copy(adds = st.adds.map { if (it.id == song.id) it.copy(asked = "Asking...") else it }) }
            viewModelScope.launchIO {
                val words =
                    try {
                        benflixMusic.ask("song", song.id, theme = key).words.ifBlank { "Asked for" }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Couldn't ask for %s", song.title)
                        null
                    }
                _state.update { st -> st.copy(adds = st.adds.map { if (it.id == song.id) it.copy(asked = words) else it }) }
            }
        }

        companion object {
            private const val FIRST_PAGE = 150
            private const val NEXT_PAGE = 500
        }

        /**
         * The same song saved twice on the server (like "...Baby One More Time" and "... Baby One More Time"
         * from two copies of the album) shows once in All songs: same artist, same name ignoring spaces
         * and punctuation, and the same length to within 3 seconds. Two different songs called "Intro"
         * by the same artist have different lengths, so both still show.
         */
        class SameSongFilter {
            private val lengths = HashMap<String, MutableList<Long>>()

            fun isNew(song: BaseItem): Boolean {
                val name = (song.data.name ?: "").lowercase().filter { it.isLetterOrDigit() }
                if (name.isEmpty()) return true
                val artist =
                    (song.data.albumArtist ?: song.data.artists?.firstOrNull() ?: "")
                        .lowercase()
                        .filter { it.isLetterOrDigit() }
                val seconds = (song.data.runTimeTicks ?: 0L) / 10_000_000L
                val known = lengths.getOrPut("$artist|$name") { mutableListOf() }
                if (known.any { kotlin.math.abs(it - seconds) <= 3 }) return false
                known.add(seconds)
                return true
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
                    _state.update { it.copy(songs = it.songs.filterNot { s -> s.data.playlistItemId == entryId }) }
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
