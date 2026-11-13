package com.github.damontecres.wholphin.ui.discover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.services.BenflixMusic
import com.github.damontecres.wholphin.services.MusicAlbum
import com.github.damontecres.wholphin.services.MusicArtist
import com.github.damontecres.wholphin.services.MusicDiscover
import com.github.damontecres.wholphin.services.MusicGenre
import com.github.damontecres.wholphin.services.MusicPlaylist
import com.github.damontecres.wholphin.services.MusicRequest
import com.github.damontecres.wholphin.services.MusicSearch
import com.github.damontecres.wholphin.services.MusicSong
import com.github.damontecres.wholphin.services.MusicStatus
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.search.SearchKeyboard
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** An artist's page or an album's page inside the Music tab */
sealed interface MusicPage {
    data class Artist(
        val artist: MusicArtist,
        val found: MusicSearch?,
    ) : MusicPage

    data class Album(
        val album: MusicAlbum,
        val songs: List<MusicSong>?,
    ) : MusicPage

    /** One genre's top songs, new releases, albums and artists */
    data class Genre(
        val genre: MusicGenre,
        val found: MusicDiscover?,
    ) : MusicPage

    /** A long list of songs, like the Top 100 */
    data class Songs(
        val title: String,
        val songs: List<MusicSong>,
    ) : MusicPage
}

@HiltViewModel
class MusicRequestsViewModel
    @Inject
    constructor(
        private val music: BenflixMusic,
        /** Benflix: 30 second song previews beside Ask for it */
        val preview: com.github.damontecres.wholphin.services.SongPreview,
        private val serverRepository: com.github.damontecres.wholphin.data.ServerRepository,
    ) : ViewModel() {
        /** Ben. Grown-up family profiles can ask too, but their asks wait for his Approve. */
        val isAdmin: Boolean get() = serverRepository.currentUserDto?.policy?.isAdministrator == true

        /** Ben's Approve or Decline on a family profile's music ask */
        fun answer(
            request: MusicRequest,
            yes: Boolean,
        ) {
            viewModelScope.launch {
                safely { music.answer(request, yes) }
                note.value = if (yes) "Said yes to ${request.title}. Tower starts looking now." else "Said no to ${request.title}."
                refresh()
            }
        }

        val status = MutableStateFlow<MusicStatus?>(null)
        val problem = MutableStateFlow<String?>(null)
        val query = MutableStateFlow("")
        val results = MutableStateFlow<MusicSearch?>(null)
        val page = MutableStateFlow<MusicPage?>(null)

        /** Top songs, new releases, top albums and artists, genres (null while loading) */
        val discover = MutableStateFlow<MusicDiscover?>(null)
        val discoverProblem = MutableStateFlow(false)

        /** A short line after pressing something, like "Asked for Bohemian Rhapsody" */
        val note = MutableStateFlow<String?>(null)
        private var searchJob: Job? = null

        fun refresh() {
            viewModelScope.launch {
                try {
                    status.value = music.status()
                    problem.value = null
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Music status")
                    problem.value = ex.message ?: "Couldn't reach Tower."
                }
            }
        }

        fun search(text: String) {
            query.value = text
            searchJob?.cancel()
            if (text.trim().length < 2) {
                results.value = null
                return
            }
            searchJob =
                viewModelScope.launch {
                    delay(400)
                    results.value = null
                    results.value = safely { music.search(text.trim()) } ?: MusicSearch(emptyList(), emptyList(), emptyList())
                }
        }

        fun loadDiscover() {
            viewModelScope.launch {
                try {
                    discover.value = music.discover()
                    discoverProblem.value = false
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Music discover")
                    if (discover.value == null) discoverProblem.value = true
                }
            }
        }

        fun openGenre(genre: MusicGenre) {
            page.value = MusicPage.Genre(genre, null)
            viewModelScope.launch {
                safely { music.genre(genre.id) }?.let { (g, found) -> page.value = MusicPage.Genre(g, found) }
            }
        }

        fun openSongs(
            title: String,
            songs: List<MusicSong>,
        ) {
            page.value = MusicPage.Songs(title, songs)
        }

        fun openArtist(artist: MusicArtist) {
            page.value = MusicPage.Artist(artist, null)
            viewModelScope.launch {
                safely { music.artist(artist.id) }?.let { (a, found) -> page.value = MusicPage.Artist(a, found) }
            }
        }

        fun openAlbum(album: MusicAlbum) {
            page.value = MusicPage.Album(album, null)
            viewModelScope.launch {
                safely { music.album(album.id) }?.let { (a, songs) -> page.value = MusicPage.Album(a, songs) }
            }
        }

        fun closePage() {
            page.value = null
        }

        /** [kind] is "song" or "album" */
        fun ask(
            kind: String,
            id: Long,
            name: String,
        ) {
            viewModelScope.launch {
                safely { music.ask(kind, id) }?.let {
                    note.value =
                        if (isAdmin) {
                            "Asked for $name. It shows under Your requests."
                        } else {
                            "Asked Ben for $name. He says yes before it downloads."
                        }
                    refresh()
                    // Mark it on the page you're on
                    when (val p = page.value) {
                        is MusicPage.Album -> {
                            openAlbum(p.album)
                        }

                        is MusicPage.Artist -> {
                            openArtist(p.artist)
                        }

                        is MusicPage.Genre -> {
                            safely { music.genre(p.genre.id) }?.let { (g, found) -> page.value = MusicPage.Genre(g, found) }
                        }

                        is MusicPage.Songs -> {
                            val marked = p.songs.map { s -> if (s.id == id && kind == "song") s.copy(asked = "Asked for") else s }
                            page.value = p.copy(songs = marked)
                        }

                        null -> {}
                    }
                    loadDiscover()
                    if (query.value.trim().length >= 2) results.value = safely { music.search(query.value.trim()) } ?: results.value
                }
            }
        }

        fun retry(request: MusicRequest) {
            viewModelScope.launch {
                safely { music.retry(request) }
                refresh()
            }
        }

        fun remove(request: MusicRequest) {
            viewModelScope.launch {
                safely { music.remove(request) }
                refresh()
            }
        }

        fun importPlaylist(
            link: String,
            makePlaylist: Boolean,
        ) {
            viewModelScope.launch {
                safely { music.importPlaylist(link, makePlaylist) }?.let {
                    note.value =
                        if (makePlaylist) {
                            "Bringing in ${it.name}. Songs you already have are kept, the rest are asked for."
                        } else {
                            "Getting the songs from ${it.name} (no playlist is made). Songs you already have are kept."
                        }
                    refresh()
                }
            }
        }

        fun removePlaylist(playlist: MusicPlaylist) {
            viewModelScope.launch {
                safely { music.removePlaylist(playlist) }
                refresh()
            }
        }

        fun tidy(
            mode: String,
            folder: String? = null,
            undoFile: String? = null,
        ) {
            viewModelScope.launch {
                safely { music.tidy(mode, folder, undoFile) }
                refresh()
            }
        }

        private suspend fun <T> safely(block: suspend () -> T): T? =
            try {
                block()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Music")
                note.value = ex.message ?: "Something went wrong."
                null
            }
    }

/**
 * Benflix Requests > Music (Ben, and grown-up family profiles since Redesign 2: their asks wait for Ben's Approve
 * before anything downloads): search any song, album or artist and ask for it. Lidarr does the
 * downloading on Tower behind the scenes; for a single song only that song is kept. Also: bring in a playlist,
 * the Music Drop folder, and the tidy-up for old music.
 */
@Composable
fun MusicShelf(viewModel: MusicRequestsViewModel = hiltViewModel()) {
    ProvideSongPreview(viewModel.preview) { MusicShelfContent(viewModel) }
}

@Composable
private fun MusicShelfContent(viewModel: MusicRequestsViewModel) {
    val status by viewModel.status.collectAsState()
    val problem by viewModel.problem.collectAsState()
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val page by viewModel.page.collectAsState()
    val note by viewModel.note.collectAsState()
    val discover by viewModel.discover.collectAsState()
    val discoverProblem by viewModel.discoverProblem.collectAsState()
    val isTv = rememberIsTv()
    LaunchedEffect(Unit) { if (viewModel.discover.value == null) viewModel.loadDiscover() }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var askSong by remember { mutableStateOf<MusicSong?>(null) }
    var playlistOpen by remember { mutableStateOf(false) }
    var sure by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val confirm: (String, () -> Unit) -> Unit = { question, action -> sure = question to action }
    // Tower moves requests along by itself; look again every few seconds while the tab is open
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        val job =
            scope.launch {
                while (true) {
                    viewModel.refresh()
                    delay(
                        if (viewModel.status.value
                                ?.tidy
                                ?.running == true
                        ) {
                            3_000L
                        } else {
                            10_000L
                        },
                    )
                }
            }
        onPauseOrDispose { job.cancel() }
    }
    LaunchedEffect(note) {
        if (note != null) {
            delay(6_000)
            viewModel.note.value = null
        }
    }
    askSong?.let { song ->
        SongAskPanel(
            title = song.title,
            artist = song.artist,
            album = song.album,
            seconds = song.seconds,
            cover = song.cover,
            songId = song.id,
            admin = viewModel.isAdmin,
            onDismiss = {
                askSong = null
                viewModel.preview.stop()
            },
            onSong = {
                askSong = null
                viewModel.ask("song", song.id, song.title)
            },
            onAlbum = {
                askSong = null
                viewModel.ask("album", song.albumId, song.album)
            },
        )
    }
    if (playlistOpen) {
        PlaylistDialog(
            onDismiss = { playlistOpen = false },
            onImport = { link, make ->
                playlistOpen = false
                viewModel.importPlaylist(link, make)
            },
        )
    }
    sure?.let { (question, action) ->
        SureDialog(
            question = question,
            onDismiss = { sure = null },
            onYes = {
                sure = null
                action()
            },
        )
    }
    val onSong: (MusicSong) -> Unit = { if (it.asked == null && !it.have) askSong = it }
    page?.let { p ->
        BackHandler { viewModel.closePage() }
        MusicPageView(
            page = p,
            note = note,
            onSong = onSong,
            onAlbum = viewModel::openAlbum,
            onArtist = viewModel::openArtist,
            onAskAlbum = { viewModel.ask("album", it.id, it.title) },
            onBack = viewModel::closePage,
        )
        return
    }
    if (isTv && searchOpen) {
        BackHandler { searchOpen = false }
        MusicSearchScreen(
            query = query,
            onQuery = viewModel::search,
            results = results,
            note = note,
            onSong = onSong,
            onAlbum = viewModel::openAlbum,
            onArtist = viewModel::openArtist,
        )
        return
    }
    // Redesign 2 (Requests, Ben 2026-11-10): the same side list and wall as Movies, Shows and Anime.
    // Lists: Top 10 songs, Trending, Because you have, New this week, Genres, Import a playlist (Ben only).
    val admin = viewModel.isAdmin
    val s = status
    val d = discover
    val because =
        remember(d) {
            val seen = HashSet<Long>()
            d
                ?.because
                .orEmpty()
                .flatMap { it.songs }
                .filter { seen.add(it.id) }
        }
    val lists =
        buildList {
            if (d != null && d.songs.isNotEmpty()) add(SideList("top", "Top 10 songs"))
            if (d != null && d.songs.size > 10) add(SideList("trending", "Trending"))
            if (because.isNotEmpty()) add(SideList("because", "Because you have"))
            if (d != null && d.newReleases.isNotEmpty()) add(SideList("new", "New this week"))
            if (d != null && d.genres.isNotEmpty()) add(SideList("genres", "Genres"))
            if (admin) add(SideList("playlist", "Import a playlist"))
        }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val pickedKey =
        picked?.takeIf { p -> p == PICK_YOURS || p == PICK_WAITING || lists.any { it.key == p } } ?: lists.firstOrNull()?.key ?: ""
    var ownedOpen by rememberSaveable { mutableStateOf<String?>(null) }
    val all = s?.requests.orEmpty()
    val waiting = if (admin) all.filter { it.waiting } else emptyList()
    val yours = all.filter { !it.waiting || !admin }
    val yourLines =
        yours.map { r ->
            r.toAskLine(
                showWho = admin,
                onRetry = { viewModel.retry(r) },
                onRemove = { confirm("Take ${r.title} off the list? Files already in Music stay.") { viewModel.remove(r) } },
            )
        }
    val waitingLines = waiting.map { r -> r.toAskLine(showWho = true, onAnswer = { yes -> viewModel.answer(r, yes) }) }
    val focusedLine = remember { mutableStateOf<String?>(null) }
    val wall: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            note?.let { NoteLine(it) }
            problem?.let { WarningLine(it) }
            if (admin) s?.problems?.forEach { line -> WarningLine(line) }
            when (pickedKey) {
                PICK_YOURS -> {
                    AskLinesWall(
                        lines = yourLines,
                        grouped = true,
                        empty = "Nothing asked for yet. Pick a song or album and ask for it.",
                        note = if (admin) null else "Ben says yes before anything downloads.",
                    )
                }

                PICK_WAITING -> {
                    AskLinesWall(lines = waitingLines, grouped = false, empty = "Nobody is waiting on you right now.")
                }

                "playlist" -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(if (isTv) 24.dp else 16.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (s == null) {
                            item(key = "loading") { MutedLine("Asking Tower…") }
                            return@LazyColumn
                        }
                        item(key = "playlists") {
                            PlaylistSection(
                                playlists = s.playlists,
                                onAdd = { playlistOpen = true },
                                onRemove = { p ->
                                    confirm("Take ${p.name} off the list? The playlist in Jellyfin stays.") { viewModel.removePlaylist(p) }
                                },
                            )
                        }
                        item(key = "drop") { DropSection(s) }
                        item(key = "tidy") {
                            TidySection(
                                status = s,
                                onPreview = { viewModel.tidy("preview", it) },
                                onApply = { folder ->
                                    confirm(
                                        "Tidy everything in $folder into Music/Artist/Album? Songs twice go to Music Leftovers, " +
                                            "and Undo puts it all back.",
                                    ) { viewModel.tidy("apply", folder) }
                                },
                                onUndo = { file ->
                                    confirm("Put the files from the last tidy-up back where they were?") {
                                        viewModel.tidy("undo", undoFile = file)
                                    }
                                },
                            )
                        }
                    }
                }

                else -> {
                    if (d == null) {
                        if (discoverProblem) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                                MutedLine("Couldn't load what's popular right now.", pad = false)
                                MusicButton("Try again", onClick = viewModel::loadDiscover)
                            }
                        } else {
                            MutedLine("Loading what's popular…")
                        }
                        return@Column
                    }
                    if (isTv) {
                        Text(
                            text = focusedLine.value ?: " ",
                            fontSize = BenflixType.body,
                            color = BenflixColors.Soft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                        )
                    }
                    val songs =
                        when (pickedKey) {
                            "top" -> d.songs.take(10)
                            "trending" -> d.songs.drop(10)
                            "because" -> because
                            else -> emptyList()
                        }
                    when (pickedKey) {
                        "new" -> {
                            MusicWall(
                                tiles =
                                    d.newReleases.map { a ->
                                        WallTile(
                                            key = "a-${a.id}",
                                            cover = a.cover,
                                            name = a.title,
                                            line = a.asked ?: a.artist,
                                            focusLine = albumLineWords(a),
                                            onClick = { viewModel.openAlbum(a) },
                                            onLongClick = { if (a.asked == null) viewModel.ask("album", a.id, a.title) },
                                        )
                                    },
                                listKey = pickedKey,
                                onFocusLine = { focusedLine.value = it },
                            )
                        }

                        "genres" -> {
                            MusicWall(
                                tiles =
                                    d.genres.map { g ->
                                        WallTile(
                                            key = "g-${g.id}",
                                            cover = g.picture,
                                            name = g.name,
                                            line = null,
                                            focusLine = g.name,
                                            onClick = { viewModel.openGenre(g) },
                                        )
                                    },
                                listKey = pickedKey,
                                onFocusLine = { focusedLine.value = it },
                            )
                        }

                        else -> {
                            val owned = songs.count { it.have }
                            val shown = if (ownedOpen == pickedKey) songs else songs.filter { !it.have }
                            MusicWall(
                                tiles =
                                    shown.mapIndexed { i, song ->
                                        WallTile(
                                            key = "s-${song.id}",
                                            cover = song.cover,
                                            name = song.title,
                                            line = songUnder(song),
                                            focusLine =
                                                listOfNotNull(
                                                    song.title,
                                                    song.artist,
                                                    if (song.seconds > 0) "%d:%02d".format(song.seconds / 60, song.seconds % 60) else null,
                                                    if (song.asked == null && !song.have) "OK to hear 30 seconds and ask" else null,
                                                ).joinToString(" · "),
                                            rank = if (pickedKey == "top") i + 1 else null,
                                            onClick = { onSong(song) },
                                            onLongClick = {
                                                if (song.asked == null && !song.have) viewModel.ask("song", song.id, song.title)
                                            },
                                        )
                                    },
                                listKey = pickedKey,
                                onFocusLine = { focusedLine.value = it },
                                owned = owned,
                                ownedOpen = ownedOpen == pickedKey,
                                onOwned = { ownedOpen = if (ownedOpen == pickedKey) null else pickedKey },
                            )
                        }
                    }
                }
            }
        }
    }
    RequestSideFrame(
        isTv = isTv,
        lists = lists,
        picked = pickedKey,
        onPick = { picked = it },
        yoursCount = yours.size,
        waitingCount = if (admin) waiting.size else null,
        onSearch = { searchOpen = true },
        loading = d == null,
        searchLabel = "Search any song or album",
        phoneTop = { RequestsSearchBox(query = query, onQuery = viewModel::search, placeholder = "Search any song, album or artist") },
        phoneWall = query.trim().length < 2,
        phoneAlt = { m ->
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
                modifier = m,
            ) {
                musicResults(results, onSong, viewModel::openAlbum, viewModel::openArtist)
            }
        },
        wall = wall,
    )
}

/** The line above the wall for an album: name · artist · year · kind */
private fun albumLineWords(a: MusicAlbum): String =
    listOf(a.title, a.artist, a.year, albumType(a))
        .filter { it.isNotBlank() }
        .joinToString(" · ")

/** The small line under a song's cover: its status pill words, Ready to play, or the artist */
private fun songUnder(song: MusicSong): String =
    when {
        song.asked != null -> pillFromWords(song.asked, AskKind.PLAY).words
        song.have -> "Ready to play"
        else -> song.artist
    }

/** One square cover on the music wall */
private class WallTile(
    val key: String,
    val cover: String?,
    val name: String,
    val line: String?,
    val focusLine: String,
    val onClick: () -> Unit,
    val rank: Int? = null,
    val onLongClick: (() -> Unit)? = null,
)

/** The music wall: square covers, 6 across on TV (3 on phones), the Top 10 numbered, names under every cover */
@Composable
private fun MusicWall(
    tiles: List<WallTile>,
    listKey: String,
    onFocusLine: (String) -> Unit,
    owned: Int = 0,
    ownedOpen: Boolean = false,
    onOwned: () -> Unit = {},
) {
    val isTv = rememberIsTv()
    val grid =
        androidx.compose.foundation.lazy.grid
            .rememberLazyGridState()
    LaunchedEffect(listKey) { grid.scrollToItem(0) }
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        columns =
            androidx.compose.foundation.lazy.grid.GridCells
                .Fixed(if (isTv) 6 else 3),
        state = grid,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(tiles.size, key = { "$listKey-${tiles[it].key}-$it" }) { i ->
            val t = tiles[i]
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box {
                    Surface(
                        onClick = t.onClick,
                        onLongClick = t.onLongClick,
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        colors =
                            ClickableSurfaceDefaults.colors(
                                containerColor = BenflixColors.Raised,
                                focusedContainerColor = BenflixColors.Raised,
                            ),
                        border = doorFocusBorder(RoundedCornerShape(10.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .onFocusChanged { if (it.isFocused) onFocusLine(t.focusLine) },
                    ) {
                        AsyncImage(
                            model = t.cover,
                            contentDescription = t.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    t.rank?.let { n ->
                        Text(
                            text = "No. $n",
                            fontSize = BenflixType.tag,
                            fontWeight = FontWeight.ExtraBold,
                            color = BenflixColors.Tray,
                            modifier =
                                Modifier
                                    .padding(6.dp)
                                    .background(BenflixColors.Text, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(
                    text = t.name,
                    fontSize = BenflixType.small,
                    fontWeight = FontWeight.Bold,
                    color = BenflixColors.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!t.line.isNullOrBlank()) {
                    Text(
                        text = t.line,
                        fontSize = BenflixType.tag,
                        color = BenflixColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (owned > 0) {
            item(key = "$listKey-owned", span = {
                androidx.compose.foundation.lazy.grid
                    .GridItemSpan(maxLineSpan)
            }) {
                Box(Modifier.padding(top = 8.dp)) { OwnedMoreButton(owned, ownedOpen, onOwned) }
            }
        }
    }
}

/** Discover: Top songs, New releases, Top albums, Top artists and Genres (for everything, or one genre) */
private fun LazyListScope.discoverRows(
    found: MusicDiscover?,
    failed: Boolean,
    onSong: (MusicSong) -> Unit,
    onAllSongs: (List<MusicSong>) -> Unit,
    onAlbum: (MusicAlbum) -> Unit,
    onArtist: (MusicArtist) -> Unit,
    onGenre: ((MusicGenre) -> Unit)?,
    onRetry: (() -> Unit)?,
    songsTitle: String = "Top songs right now",
    showAll: Boolean = true,
) {
    if (found == null) {
        if (failed && onRetry != null) {
            item(key = "d-failed") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                    MutedLine("Couldn't load what's popular right now.", pad = false)
                    MusicButton("Try again", onClick = onRetry)
                }
            }
        } else {
            item(key = "d-loading") { MutedLine("Loading what's popular…") }
        }
        return
    }
    if (showAll && found.songs.size > 10) {
        // The main page: Top 10, then the rest of the chart as Trending now (like Movies and Shows)
        item(key = "d-top10") {
            SongTileRow(title = "Top 10 songs right now", songs = found.songs.take(10), onSong = onSong, allLabel = null, onAll = {})
        }
        item(key = "d-trending") {
            SongTileRow(
                title = "Trending now",
                songs = found.songs.drop(10).take(30),
                onSong = onSong,
                allLabel = "See all ${found.songs.size}",
                onAll = { onAllSongs(found.songs) },
                firstNumber = 11,
            )
        }
    } else if (found.songs.isNotEmpty()) {
        item(key = "d-songs") {
            SongTileRow(
                title = songsTitle,
                songs = found.songs.take(30),
                onSong = onSong,
                allLabel = if (showAll && found.songs.size > 30) "See all ${found.songs.size}" else null,
                onAll = { onAllSongs(found.songs) },
            )
        }
    }
    if (found.newReleases.isNotEmpty()) item(key = "d-new") { AlbumRow("New releases", found.newReleases, onAlbum) }
    found.because.forEachIndexed { i, b ->
        if (b.songs.isNotEmpty()) {
            item(key = "d-because-$i") {
                SongTileRow(
                    title = "Because you have ${b.artist}",
                    songs = b.songs,
                    onSong = onSong,
                    allLabel = null,
                    onAll = {},
                    numbered = false,
                )
            }
        }
    }
    if (found.picks.isNotEmpty()) item(key = "d-picks") { AlbumRow("Editors' picks", found.picks, onAlbum) }
    if (found.albums.isNotEmpty()) item(key = "d-albums") { AlbumRow("Top albums", found.albums, onAlbum) }
    if (found.artists.isNotEmpty()) item(key = "d-artists") { ArtistRow(found.artists, onArtist, title = "Top artists") }
    if (onGenre != null && found.genres.isNotEmpty()) item(key = "d-genres") { GenreRow(found.genres, onGenre) }
}

/** Songs as square tiles with their chart number; the last tile opens the whole list */
@Composable
private fun SongTileRow(
    title: String,
    songs: List<MusicSong>,
    onSong: (MusicSong) -> Unit,
    allLabel: String?,
    onAll: () -> Unit,
    firstNumber: Int = 1,
    numbered: Boolean = true,
) {
    val isTv = rememberIsTv()
    val size = if (isTv) 150 else 120
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RowTitle(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items(songs.withIndex().toList(), key = { "ts-${it.value.id}" }) { (i, song) ->
                Tile(
                    picture = song.cover,
                    size = size,
                    shape = RoundedCornerShape(10.dp),
                    name = if (numbered) "${i + firstNumber}. ${song.title}" else song.title,
                    line = song.asked ?: if (song.have) "In your music" else song.artist,
                    onClick = { onSong(song) },
                    previewId = song.id,
                )
            }
            if (allLabel != null) {
                item(key = "ts-all") {
                    Column(Modifier.width(size.dp).height(size.dp), verticalArrangement = Arrangement.Center) {
                        MusicButton(allLabel, primary = true, modifier = Modifier.fillMaxWidth(), onClick = onAll)
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreRow(
    genres: List<MusicGenre>,
    onGenre: (MusicGenre) -> Unit,
) {
    val isTv = rememberIsTv()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RowTitle("Genres")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items(genres, key = { "g-${it.id}" }) { g ->
                Tile(
                    picture = g.picture,
                    size = if (isTv) 130 else 104,
                    shape = RoundedCornerShape(10.dp),
                    name = g.name,
                    line = null,
                    onClick = { onGenre(g) },
                )
            }
        }
    }
}

private fun LazyListScope.musicResults(
    results: MusicSearch?,
    onSong: (MusicSong) -> Unit,
    onAlbum: (MusicAlbum) -> Unit,
    onArtist: (MusicArtist) -> Unit,
) {
    if (results == null) {
        item(key = "searching") { MutedLine("Searching…") }
        return
    }
    if (results.songs.isEmpty() && results.albums.isEmpty() && results.artists.isEmpty()) {
        item(key = "nothing") { MutedLine("Nothing found. Try fewer words, or the artist's name.") }
        return
    }
    if (results.songs.isNotEmpty()) {
        item(key = "songs-title") { RowTitle("Songs") }
        items(results.songs.take(12), key = { "s-${it.id}" }) { SongRow(it, onClick = { onSong(it) }) }
    }
    if (results.albums.isNotEmpty()) {
        item(key = "albums") { AlbumRow("Albums", results.albums, onAlbum) }
    }
    if (results.artists.isNotEmpty()) {
        item(key = "artists") { ArtistRow(results.artists, onArtist) }
    }
}

/** TV: the keyboard on the left, songs, albums and artists on the right (like the main Search) */
@Composable
private fun MusicSearchScreen(
    query: String,
    onQuery: (String) -> Unit,
    results: MusicSearch?,
    note: String?,
    onSong: (MusicSong) -> Unit,
    onAlbum: (MusicAlbum) -> Unit,
    onArtist: (MusicArtist) -> Unit,
) {
    val keyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { keyFocus.tryRequestFocus() }
    Row(
        Modifier
            .fillMaxSize()
            .background(BenflixColors.Tray),
    ) {
        Box(Modifier.focusRequester(keyFocus)) {
            SearchKeyboard(
                query = query,
                onQueryChange = onQuery,
                suggestions =
                    results
                        ?.artists
                        ?.map { it.name }
                        ?.take(4)
                        .orEmpty(),
                onSuggestion = onQuery,
                big = false,
                modifier = Modifier.fillMaxHeight(),
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp, end = 24.dp),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            item(key = "typed") {
                Text(
                    text = query.ifBlank { "Type a song, album or artist" },
                    color = if (query.isBlank()) BenflixColors.Muted else BenflixColors.Text,
                    fontSize = BenflixType.pageTitle,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            note?.let { item(key = "note") { NoteLine(it) } }
            if (query.trim().length >= 2) musicResults(results, onSong, onAlbum, onArtist)
        }
    }
}

/** An artist (best songs, then albums) or an album (its songs, and Ask for the whole album) */
@Composable
private fun MusicPageView(
    page: MusicPage,
    note: String?,
    onSong: (MusicSong) -> Unit,
    onAlbum: (MusicAlbum) -> Unit,
    onArtist: (MusicArtist) -> Unit,
    onAskAlbum: (MusicAlbum) -> Unit,
    onBack: () -> Unit,
) {
    val isTv = rememberIsTv()
    val first = remember(page::class) { FocusRequester() }
    LaunchedEffect(page::class) { first.tryRequestFocus() }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "head") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                val (picture, shape) =
                    when (page) {
                        is MusicPage.Artist -> page.artist.picture to CircleShape
                        is MusicPage.Album -> page.album.cover to RoundedCornerShape(10.dp)
                        is MusicPage.Genre -> page.genre.picture to RoundedCornerShape(10.dp)
                        is MusicPage.Songs -> page.songs.firstOrNull()?.cover to RoundedCornerShape(10.dp)
                    }
                Cover(picture, if (isTv) 120 else 96, shape)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            when (page) {
                                is MusicPage.Artist -> page.artist.name
                                is MusicPage.Album -> page.album.title
                                is MusicPage.Genre -> page.genre.name
                                is MusicPage.Songs -> page.title
                            },
                        fontSize = BenflixType.pageTitle,
                        fontWeight = FontWeight.ExtraBold,
                        color = BenflixColors.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (page is MusicPage.Album) {
                        Text(
                            text =
                                listOf(
                                    page.album.artist,
                                    page.album.year,
                                    albumType(page.album),
                                ).filter { it.isNotBlank() }.joinToString(" · "),
                            fontSize = BenflixType.body,
                            color = BenflixColors.Soft,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MusicButton("Back", modifier = Modifier.focusRequester(first), onClick = onBack)
                        if (page is MusicPage.Album) {
                            val asked = page.album.asked
                            if (asked == null) {
                                MusicButton("Ask for the whole album", primary = true) { onAskAlbum(page.album) }
                            } else {
                                MutedLine(asked)
                            }
                        }
                    }
                }
            }
        }
        note?.let { item(key = "note") { NoteLine(it) } }
        when (page) {
            is MusicPage.Artist -> {
                val found = page.found
                if (found == null) {
                    item(key = "loading") { MutedLine("Loading…") }
                } else {
                    if (found.songs.isNotEmpty()) {
                        item(key = "top") { RowTitle("Best songs") }
                        items(found.songs, key = { "s-${it.id}" }) { SongRow(it, onClick = { onSong(it) }) }
                    }
                    if (found.albums.isNotEmpty()) item(key = "albums") { AlbumRow("Albums, EPs and singles", found.albums, onAlbum) }
                }
            }

            is MusicPage.Album -> {
                val songs = page.songs
                if (songs == null) {
                    item(key = "loading") { MutedLine("Loading…") }
                } else {
                    item(key = "hint") { MutedLine("Pick a song to ask for just that one.") }
                    items(songs, key = { "s-${it.id}" }) { SongRow(it, showAlbum = false, onClick = { onSong(it) }) }
                }
            }

            is MusicPage.Genre -> {
                discoverRows(
                    found = page.found,
                    failed = false,
                    onSong = onSong,
                    onAllSongs = {},
                    onAlbum = onAlbum,
                    onArtist = onArtist,
                    onGenre = null,
                    onRetry = null,
                    songsTitle = "Top ${page.genre.name} songs",
                    showAll = false,
                )
                page.found?.songs?.takeIf { it.size > 30 }?.let { songs ->
                    item(key = "more-title") { RowTitle("More ${page.genre.name} songs") }
                    items(songs.drop(30), key = { "gs-${it.id}" }) { SongRow(it, onClick = { onSong(it) }) }
                }
            }

            is MusicPage.Songs -> {
                item(key = "hint") { MutedLine("Pick a song to ask for it.") }
                items(page.songs.withIndex().toList(), key = { "s-${it.value.id}" }) { (i, song) ->
                    SongRow(song, number = i + 1, onClick = { onSong(song) })
                }
            }
        }
    }
}

private fun albumType(album: MusicAlbum) =
    when (album.type.lowercase()) {
        "single" -> "Single"
        "ep" -> "EP"
        else -> if (album.songs > 0) "${album.songs} songs" else "Album"
    }

@Composable
private fun SongRow(
    song: MusicSong,
    showAlbum: Boolean = true,
    number: Int? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val preview = LocalSongPreview.current
    // Benflix: a round play button beside the row plays 30 seconds of the song (Right on the remote reaches it)
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .padding(horizontal = 16.dp)
                .widthIn(max = 960.dp)
                .fillMaxWidth(),
    ) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(shape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = BenflixColors.Panel,
                    contentColor = BenflixColors.Text,
                    focusedContainerColor = BenflixColors.FocusRow,
                    focusedContentColor = BenflixColors.Text,
                ),
            border = doorFocusBorder(shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier = Modifier.weight(1f),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(8.dp),
            ) {
                if (number != null) {
                    Text(
                        text = "$number",
                        fontSize = BenflixType.body,
                        fontWeight = FontWeight.ExtraBold,
                        color = BenflixColors.Muted,
                        modifier = Modifier.widthIn(min = 32.dp),
                    )
                }
                Cover(song.cover, 48, RoundedCornerShape(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        fontSize = BenflixType.body,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (showAlbum) "${song.artist} · ${song.album}" else song.artist,
                        fontSize = BenflixType.small,
                        color = BenflixColors.Soft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (song.seconds > 0) {
                    Text("%d:%02d".format(song.seconds / 60, song.seconds % 60), fontSize = BenflixType.small, color = BenflixColors.Muted)
                }
                Text(
                    text = song.asked ?: if (song.have) "In your music" else "Ask for it",
                    fontSize = BenflixType.small,
                    fontWeight = FontWeight.Bold,
                    color = if (song.asked == null && !song.have) BenflixColors.Text else BenflixColors.Muted,
                    maxLines = 1,
                    modifier = Modifier.widthIn(max = 220.dp),
                )
            }
        }
        if (preview != null && song.id != 0L) {
            val state by preview.state.collectAsState()
            SongPreviewButton(state = state, songId = song.id, onClick = { preview.toggle(song.id) })
        }
    }
}

@Composable
private fun AlbumRow(
    title: String,
    albums: List<MusicAlbum>,
    onAlbum: (MusicAlbum) -> Unit,
) {
    val isTv = rememberIsTv()
    val size = if (isTv) 150 else 120
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RowTitle(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items(albums, key = { "a-${it.id}" }) { album ->
                Tile(
                    picture = album.cover,
                    size = size,
                    shape = RoundedCornerShape(10.dp),
                    name = album.title,
                    line = album.asked ?: listOf(album.year, albumType(album)).filter { it.isNotBlank() }.joinToString(" · "),
                    onClick = { onAlbum(album) },
                )
            }
        }
    }
}

@Composable
private fun ArtistRow(
    artists: List<MusicArtist>,
    onArtist: (MusicArtist) -> Unit,
    title: String = "Artists",
) {
    val isTv = rememberIsTv()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RowTitle(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items(artists, key = { "ar-${it.id}" }) { artist ->
                Tile(
                    picture = artist.picture,
                    size = if (isTv) 120 else 96,
                    shape = CircleShape,
                    name = artist.name,
                    line = null,
                    onClick = { onArtist(artist) },
                )
            }
        }
    }
}

@Composable
private fun Tile(
    picture: String?,
    size: Int,
    shape: Shape,
    name: String,
    line: String?,
    onClick: () -> Unit,
    previewId: Long? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.width(size.dp)) {
        Box {
            Surface(
                onClick = onClick,
                shape = ClickableSurfaceDefaults.shape(shape),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = BenflixColors.Raised,
                        focusedContainerColor = BenflixColors.Raised,
                    ),
                border = doorFocusBorder(shape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier = Modifier.size(size.dp),
            ) {
                AsyncImage(model = picture, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            // Benflix: on phones a small play button sits on the song's cover (on TV it's in the Ask for it pop-up)
            val preview = LocalSongPreview.current
            if (preview != null && previewId != null && previewId != 0L && !rememberIsTv()) {
                val state by preview.state.collectAsState()
                SongPreviewButton(
                    state = state,
                    songId = previewId,
                    onClick = { preview.toggle(previewId) },
                    size = 36,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                )
            }
        }
        Text(
            text = name,
            fontSize = BenflixType.small,
            fontWeight = FontWeight.Bold,
            color = BenflixColors.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!line.isNullOrBlank()) {
            Text(text = line, fontSize = BenflixType.tag, color = BenflixColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PlaylistSection(
    playlists: List<MusicPlaylist>,
    onAdd: () -> Unit,
    onRemove: (MusicPlaylist) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 900.dp)) {
        Text("Playlists", fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold, color = BenflixColors.Text)
        Text(
            "Paste a Spotify, YouTube Music or Deezer playlist link. Benflix asks for every song you don't have yet and " +
                "makes the same playlist in your music as they arrive.",
            fontSize = BenflixType.small,
            color = BenflixColors.Soft,
        )
        MusicButton("Bring in a playlist", primary = true, onClick = onAdd)
        playlists.forEach { p ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(BenflixColors.Panel)
                        .padding(10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        p.name,
                        fontSize = BenflixType.body,
                        fontWeight = FontWeight.Bold,
                        color = BenflixColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(p.words, fontSize = BenflixType.small, color = BenflixColors.Soft, maxLines = 2)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(BenflixColors.Raised),
                    ) {
                        Box(Modifier.fillMaxWidth(p.bar).height(4.dp).background(BenflixColors.Text))
                    }
                }
                MusicButton(if (p.done) "Done" else "Remove") { onRemove(p) }
            }
        }
    }
}

@Composable
private fun DropSection(status: MusicStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 900.dp)) {
        Text("Music Drop folder", fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold, color = BenflixColors.Text)
        Text(
            "Put music files in ${status.dropFolder} from your computer. Every few minutes they're sorted into " +
                "Music by artist and album, with covers. Lossless files become 320 MP3s.",
            fontSize = BenflixType.small,
            color = BenflixColors.Soft,
        )
        status.dropLines.takeLast(5).forEach { MutedLine(it, pad = false) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TidySection(
    status: MusicStatus,
    onPreview: (String) -> Unit,
    onApply: (String) -> Unit,
    onUndo: (String) -> Unit,
) {
    val tidy = status.tidy ?: return
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    val picked = folder?.takeIf { it in tidy.folders } ?: tidy.folders.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 900.dp)) {
        Text("Tidy up old music", fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold, color = BenflixColors.Text)
        Text(
            "Sorts a folder (like the music SoulSync got) into Music/Artist/Album/01 - Song, fixes names, adds covers and " +
                "puts songs you have twice in Music Leftovers. Press Check first to see what would move.",
            fontSize = BenflixType.small,
            color = BenflixColors.Soft,
        )
        if (tidy.folders.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tidy.folders.forEach { f -> MusicButton(f, primary = f == picked) { folder = f } }
            }
        }
        if (tidy.running) {
            MutedLine(if (tidy.mode == "preview") "Checking…" else "Tidying…", pad = false)
        } else if (picked != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MusicButton("Check first") { onPreview(picked) }
                MusicButton("Tidy it", primary = true) { onApply(picked) }
                tidy.undoFiles.lastOrNull()?.let { file -> MusicButton("Undo last tidy-up") { onUndo(file) } }
            }
        }
        tidy.result?.let { Text(it, fontSize = BenflixType.body, color = BenflixColors.Text) }
        if (tidy.lines.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BenflixColors.Raised)
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
            ) {
                tidy.lines.takeLast(120).forEach { Text(it, fontSize = BenflixType.tag, color = BenflixColors.Soft) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaylistDialog(
    onDismiss: () -> Unit,
    onImport: (link: String, makePlaylist: Boolean) -> Unit,
) {
    var link by remember { mutableStateOf("") }
    BasicDialog(onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(max = 560.dp).padding(22.dp)) {
            Text("Bring in a playlist", fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold, color = BenflixColors.Text)
            Text(
                "Paste the playlist's link (Share > Copy link in Spotify or YouTube Music). You can also paste songs, one " +
                    "per line, like: Queen - Bohemian Rhapsody. Tip: it's easiest on your phone.",
                fontSize = BenflixType.small,
                color = BenflixColors.Soft,
            )
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                minLines = 2,
                maxLines = 6,
                placeholder = { androidx.compose.material3.Text("https://open.spotify.com/playlist/…") },
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = BenflixColors.Line,
                        focusedContainerColor = BenflixColors.Raised,
                        unfocusedContainerColor = BenflixColors.Raised,
                    ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            // Ben: someone else's playlist can bring in just the songs, without a Benflix playlist
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MusicButton("Songs + playlist", primary = true) { if (link.isNotBlank()) onImport(link.trim(), true) }
                MusicButton("Just the songs") { if (link.isNotBlank()) onImport(link.trim(), false) }
                MusicButton("Cancel", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun SureDialog(
    question: String,
    onDismiss: () -> Unit,
    onYes: () -> Unit,
) {
    BasicDialog(onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.widthIn(max = 520.dp).padding(22.dp)) {
            Text(question, fontSize = BenflixType.body, color = BenflixColors.Text)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MusicButton("Yes", primary = true, onClick = onYes)
                MusicButton("No", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun MusicButton(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    big: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = if (big) RoundedCornerShape(10.dp) else RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor =
                    if (primary) {
                        BenflixColors.Text
                    } else if (big) {
                        BenflixColors.Panel
                    } else {
                        BenflixColors.Raised
                    },
                contentColor =
                    if (primary) {
                        BenflixColors.Tray
                    } else if (big) {
                        BenflixColors.Soft
                    } else {
                        BenflixColors.Text
                    },
                focusedContainerColor = if (big) BenflixColors.Raised else BenflixColors.Text,
                focusedContentColor = if (big) BenflixColors.Text else BenflixColors.Tray,
            ),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Text(
            text = label,
            fontSize = if (big) BenflixType.body else BenflixType.small,
            fontWeight = if (big) FontWeight.Normal else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .align(if (big) Alignment.CenterStart else Alignment.Center)
                    .padding(vertical = if (big) 14.dp else 8.dp, horizontal = if (big) 18.dp else 14.dp),
        )
    }
}

@Composable
private fun Cover(
    url: String?,
    size: Int,
    shape: Shape,
) {
    Box(
        Modifier
            .size(size.dp)
            .clip(shape)
            .background(BenflixColors.Raised),
    ) {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun RowTitle(text: String) {
    Text(
        text = text,
        fontSize = BenflixType.rowName,
        fontWeight = FontWeight.Bold,
        color = BenflixColors.Text,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun MutedLine(
    text: String,
    pad: Boolean = true,
) {
    Text(
        text = text,
        fontSize = BenflixType.small,
        color = BenflixColors.Muted,
        modifier = if (pad) Modifier.padding(horizontal = 16.dp) else Modifier,
    )
}

@Composable
private fun NoteLine(text: String) {
    Text(
        text = text,
        fontSize = BenflixType.body,
        color = BenflixColors.Text,
        modifier =
            Modifier
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(BenflixColors.Raised)
                .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun WarningLine(text: String) {
    Text(
        text = text,
        fontSize = BenflixType.small,
        color = BenflixColors.Warning,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}
