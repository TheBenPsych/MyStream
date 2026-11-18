package com.github.damontecres.wholphin.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.MusicLibrary
import com.github.damontecres.wholphin.services.MusicMe
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.focusRing
import com.github.damontecres.wholphin.ui.search.SearchKeyboard
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import javax.inject.Inject

data class MusicSearchResults(
    val songs: List<BaseItem> = emptyList(),
    val artists: List<BaseItem> = emptyList(),
    val albums: List<BaseItem> = emptyList(),
    val playlists: List<BaseItem> = emptyList(),
    val searching: Boolean = false,
    /** Not on Benflix yet: catalogue songs for these words that aren't in Music (preview and Ask) */
    val notHere: List<com.github.damontecres.wholphin.services.MusicSong> = emptyList(),
    /** The words these results are for */
    val term: String = "",
    /** Music redesign 3 (lyrics search): songs on Benflix whose words have these words, with the line that matched */
    val byWords: List<Pair<BaseItem, String>> = emptyList(),
) {
    val empty get() = songs.isEmpty() && artists.isEmpty() && albums.isEmpty() && playlists.isEmpty()

    /** The one big Top result: an artist whose name fits, else the first song, album or playlist */
    val top: BaseItem?
        get() =
            artists.firstOrNull { it.title.orEmpty().startsWith(term.trim(), ignoreCase = true) }
                ?: songs.firstOrNull { it.title.orEmpty().startsWith(term.trim(), ignoreCase = true) }
                ?: artists.firstOrNull()
                ?: songs.firstOrNull()
                ?: albums.firstOrNull()
                ?: playlists.firstOrNull()
}

/** Music redesign 3: Search inside Music (Recent searches before you type, approved x-recentsearch) */
@HiltViewModel
class MusicSearchViewModel
    @Inject
    constructor(
        private val library: MusicLibrary,
        val musicMe: MusicMe,
        private val musicService: MusicService,
        val navigationManager: NavigationManager,
        private val benflixMusic: com.github.damontecres.wholphin.services.BenflixMusic,
        val preview: com.github.damontecres.wholphin.services.SongPreview,
    ) : ViewModel() {
        /** Browse all: the themed playlists (In season, decades, genres, moods) as colour tiles */
        val browse = MutableStateFlow<List<com.github.damontecres.wholphin.services.MusicMix>>(emptyList())
        val query = MutableStateFlow("")
        private val _results = MutableStateFlow(MusicSearchResults())
        val results: StateFlow<MusicSearchResults> = _results
        private var job: Job? = null

        init {
            viewModelScope.launchIO { musicMe.load() }
            viewModelScope.launchIO {
                val t = library.themes()
                val order = listOf("season", "lofi", "mood", "genre", "decade", "throwback")
                browse.value = t.themes.sortedBy { order.indexOf(it.group).let { i -> if (i < 0) 99 else i } }
            }
        }

        fun type(words: String) {
            query.value = words
            job?.cancel()
            val term = words.trim()
            if (term.length < 2) {
                _results.value = MusicSearchResults()
                return
            }
            job =
                viewModelScope.launchIO {
                    delay(TYPING_MS)
                    _results.value = _results.value.copy(searching = true)
                    try {
                        coroutineScope {
                            val songs = async { safe { library.search(term, BaseItemKind.AUDIO, 12) } }
                            val artists = async { safe { library.search(term, BaseItemKind.MUSIC_ARTIST, 10) } }
                            val albums = async { safe { library.search(term, BaseItemKind.MUSIC_ALBUM, 12) } }
                            val playlists = async { safe { library.search(term, BaseItemKind.PLAYLIST, 8) } }
                            _results.value =
                                MusicSearchResults(
                                    songs = songs.await().orEmpty(),
                                    artists = artists.await().orEmpty(),
                                    albums = albums.await().orEmpty(),
                                    playlists = playlists.await().orEmpty(),
                                    term = term,
                                    searching = true,
                                )
                        }
                        // Not on Benflix yet (from the music catalogue through Tower; it can take a moment)
                        val outside =
                            safe { benflixMusic.search(term).songs }
                                .orEmpty()
                                .filter { !it.have }
                                .take(NOT_HERE)
                        _results.value = _results.value.copy(notHere = outside, searching = false)
                        // Lyrics search: a line you remember ("is this the real life") finds the song
                        if (term.trim().split(Regex("\\s+")).size >= 2) {
                            val have =
                                _results.value.songs
                                    .map { it.id }
                                    .toSet()
                            val found =
                                safe { benflixMusic.lyricsSearch(term) }.orEmpty().take(LYRIC_HITS).mapNotNull { (artist, title, line) ->
                                    safe { library.search(title, BaseItemKind.AUDIO, 5) }
                                        ?.firstOrNull { song ->
                                            song.title.equals(title, ignoreCase = true) &&
                                                (
                                                    artist.isBlank() ||
                                                        song.data.artists.orEmpty().any {
                                                            it.contains(artist, true) ||
                                                                artist.contains(it, true)
                                                        } ||
                                                        song.data.albumArtist?.contains(artist, true) == true
                                                )
                                        }?.takeIf { it.id !in have }
                                        ?.let { it to line }
                                }
                            if (_results.value.term == term) _results.value = _results.value.copy(byWords = found)
                        }
                    } catch (ex: kotlinx.coroutines.CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Music search")
                        _results.value = MusicSearchResults()
                    }
                }
        }

        /** You opened or played something from the results: the words go to the top of Recent searches */
        fun keep() {
            val words = query.value
            viewModelScope.launchIO { musicMe.searched(words) }
        }

        fun open(item: BaseItem) {
            keep()
            navigationManager.navigateTo(item.destination())
        }

        fun play(
            songs: List<BaseItem>,
            index: Int,
        ) {
            keep()
            viewModelScope.launchIO { musicService.playList(songs, index, false, songs.getOrNull(index)?.id, "Search") }
        }

        /** Ask on a song that isn't on Benflix yet */
        fun ask(song: com.github.damontecres.wholphin.services.MusicSong) {
            if (song.asked != null) return
            keep()
            preview.stop()
            setAsked(song.id, "Asking...")
            viewModelScope.launchIO {
                val words = safe { benflixMusic.ask("song", song.id).words.ifBlank { "Asked ✓" } }
                setAsked(song.id, words)
            }
        }

        private fun setAsked(
            id: Long,
            words: String?,
        ) {
            _results.value = _results.value.copy(notHere = _results.value.notHere.map { if (it.id == id) it.copy(asked = words) else it })
        }

        fun openMix(mix: com.github.damontecres.wholphin.services.MusicMix) =
            navigationManager.navigateTo(
                com.github.damontecres.wholphin.ui.nav.Destination
                    .MusicList("mix", mix.key, mix.name),
            )

        fun remove(words: String) = viewModelScope.launchIO { musicMe.removeSearch(words) }

        fun clear() = viewModelScope.launchIO { musicMe.clearSearches() }

        private suspend fun <T> safe(block: suspend () -> T): T? =
            try {
                block()
            } catch (ex: kotlinx.coroutines.CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Music search part")
                null
            }

        companion object {
            private const val TYPING_MS = 350L
            private const val NOT_HERE = 6
            private const val LYRIC_HITS = 6
        }
    }

@Composable
fun MusicSearchPlace(
    wide: Boolean,
    modifier: Modifier = Modifier,
    viewModel: MusicSearchViewModel = hiltViewModel(),
) {
    val isTv = rememberIsTv()
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val me by viewModel.musicMe.state.collectAsState()
    val browse by viewModel.browse.collectAsState()
    androidx.lifecycle.compose.LifecycleResumeEffect(viewModel.preview) { onPauseOrDispose { viewModel.preview.stop() } }
    val side = if (wide) 0.dp else BenflixSpace.phoneEdge
    val list: LazyListScope.() -> Unit = {
        if (query.trim().length < 2) {
            recentSearches(
                searches = me.searches.map { it.q },
                onPick = viewModel::type,
                onRemove = { viewModel.remove(it) },
                onClear = { viewModel.clear() },
            )
            browseAll(browse, if (wide) 4 else 2, viewModel::openMix)
        } else {
            searchResults(
                results = results,
                onSong = viewModel::play,
                onOpen = viewModel::open,
                preview = viewModel.preview,
                onAsk = viewModel::ask,
            )
        }
    }
    if (isTv) {
        val keys = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(80)
            keys.tryRequestFocus()
        }
        Row(modifier.fillMaxSize()) {
            Box(Modifier.focusRequester(keys)) {
                SearchKeyboard(
                    query = query,
                    onQueryChange = viewModel::type,
                    suggestions = results.artists.mapNotNull { it.title }.take(4),
                    onSuggestion = viewModel::type,
                    big = false,
                    modifier = Modifier.fillMaxHeight(),
                )
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp, start = 16.dp, end = 16.dp),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                item(key = "typed") {
                    Text(
                        text = query.ifBlank { "Songs, artists, albums or playlists" },
                        color = if (query.isBlank()) BenflixColors.Muted else BenflixColors.Text,
                        fontSize = BenflixType.pageTitle,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                list()
            }
        }
        return
    }
    Column(modifier.fillMaxSize()) {
        MusicSearchBox(
            value = query,
            onValueChange = viewModel::type,
            onDone = viewModel::keep,
            modifier = Modifier.fillMaxWidth().padding(start = side, end = side, top = 8.dp),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(start = side, end = 8.dp, top = 12.dp, bottom = 48.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { list() }
    }
}

/** Phones: a big round box to type in */
@Composable
private fun MusicSearchBox(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = BenflixColors.Text, fontSize = BenflixType.body, fontWeight = FontWeight.SemiBold),
        cursorBrush = SolidColor(BenflixColors.Text),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onDone() }),
        modifier =
            modifier
                .onFocusChanged { focused = it.isFocused }
                .background(BenflixColors.Raised, shape)
                .border(if (focused) 2.dp else 1.dp, if (focused) BenflixColors.Music else BenflixColors.Line, shape),
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                BenflixIconImage(BenflixIcon.SEARCH, color = BenflixColors.Muted, size = 20.dp, contentDescription = null)
                Box(Modifier.padding(start = 10.dp).weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            "What do you want to listen to?",
                            color = BenflixColors.Muted,
                            fontSize = BenflixType.body,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            }
        },
    )
}

/** Recent searches (approved x-recentsearch): your last 10, X to remove one, Clear all */
private fun LazyListScope.recentSearches(
    searches: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (searches.isEmpty()) {
        item(key = "none") { MusicEmptyLine("Your recent searches show here.") }
        return
    }
    item(key = "recent-title") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Recent searches",
                color = BenflixColors.Text,
                fontSize = BenflixType.rowName,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f),
            )
            MusicButton("Clear all", null, onClick = onClear)
        }
    }
    items(searches, key = { "q-$it" }) { q ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchLine(q, onClick = { onPick(q) }, modifier = Modifier.weight(1f))
            MusicButton(null, BenflixIcon.CLOSE, onClick = { onRemove(q) })
        }
    }
}

@Composable
private fun SearchLine(
    words: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border = focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            BenflixIconImage(BenflixIcon.SEARCH, color = BenflixColors.Muted, size = 18.dp, contentDescription = null)
            Text(words, fontSize = BenflixType.body, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Songs first, then artists, albums and playlists on Benflix */
private fun LazyListScope.searchResults(
    results: MusicSearchResults,
    onSong: (List<BaseItem>, Int) -> Unit,
    onOpen: (BaseItem) -> Unit,
    preview: com.github.damontecres.wholphin.services.SongPreview,
    onAsk: (com.github.damontecres.wholphin.services.MusicSong) -> Unit,
) {
    if (results.empty && results.byWords.isEmpty()) {
        item(key = "nothing") {
            MusicEmptyLine(
                if (results.searching) {
                    "Looking…"
                } else if (results.notHere.isNotEmpty()) {
                    "Nothing on Glowfin by that name yet."
                } else {
                    "Nothing on Glowfin by that name. Try Ask for music to get it."
                },
            )
        }
        notHere(results, preview, onAsk)
        return
    }
    results.top?.let { top ->
        item(key = "top-title") { MusicRowName("Top result", Modifier.padding(top = 4.dp, bottom = 4.dp)) }
        item(key = "top-card") {
            TopResult(top, onClick = {
                if (top.type == BaseItemKind.AUDIO) onSong(listOf(top), 0) else onOpen(top)
            })
        }
    }
    if (results.songs.isNotEmpty()) {
        item(key = "songs-title") { MusicRowName("Songs", Modifier.padding(top = 8.dp, bottom = 4.dp)) }
        itemsIndexed(results.songs, key = { _, s -> "s-${s.id}" }) { i, song ->
            SongRow(
                song = song,
                number = i + 1,
                withCover = true,
                isCurrent = false,
                isPlaying = false,
                liked = song.data.userData?.isFavorite == true,
                onClick = { onSong(results.songs, i) },
                onMore = { onOpen(song) },
            )
        }
    }
    listOf("Artists" to results.artists, "Albums" to results.albums, "Playlists" to results.playlists).forEach { (title, list) ->
        if (list.isEmpty()) return@forEach
        item(key = "$title-title") { MusicRowName(title, Modifier.padding(top = 14.dp, bottom = 4.dp)) }
        item(key = "$title-row") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(list, key = { it.id.toString() }) { item ->
                    MusicCard(item = item, size = 120.dp, onClick = { onOpen(item) })
                }
            }
        }
    }
    byWords(results, onSong)
    notHere(results, preview, onAsk)
}

/** Music redesign 3 (lyrics search): songs whose words have what you typed, with the line under the name */
private fun LazyListScope.byWords(
    results: MusicSearchResults,
    onSong: (List<BaseItem>, Int) -> Unit,
) {
    if (results.byWords.isEmpty()) return
    item(key = "words-title") { MusicRowName("Songs with these words", Modifier.padding(top = 14.dp, bottom = 4.dp)) }
    val list = results.byWords.map { it.first }
    itemsIndexed(results.byWords, key = { _, w -> "w-${w.first.id}" }) { i, (song, line) ->
        androidx.compose.foundation.layout.Column {
            SongRow(
                song = song,
                number = i + 1,
                withCover = true,
                isCurrent = false,
                isPlaying = false,
                liked = song.data.userData?.isFavorite == true,
                onClick = { onSong(list, i) },
                onMore = { onSong(list, i) },
            )
            Text(
                "\u201C$line\u201D",
                color = BenflixColors.Muted,
                fontSize = BenflixType.small,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 64.dp, bottom = 4.dp),
            )
        }
    }
}

/** Not on Benflix yet: songs from the catalogue for these words, greyed, with a preview and Ask */
private fun LazyListScope.notHere(
    results: MusicSearchResults,
    preview: com.github.damontecres.wholphin.services.SongPreview,
    onAsk: (com.github.damontecres.wholphin.services.MusicSong) -> Unit,
) {
    if (results.notHere.isEmpty()) return
    item(key = "nothere-title") {
        Text(
            "Not on Glowfin yet",
            color = MusicAskColor,
            fontSize = BenflixType.rowName,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
        )
    }
    items(results.notHere, key = { "nh-${it.id}" }) { song ->
        MissingSongRow(song = song, number = null, preview = preview, onAsk = { onAsk(song) }, withCover = true)
    }
}

/** The one big Top result card */
@Composable
private fun TopResult(
    item: BaseItem,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val imageUrlService = com.github.damontecres.wholphin.ui.LocalImageUrlService.current
    val url = remember(item.id) { imageUrlService.musicCover(item, 200) }
    val round = item.type == BaseItemKind.MUSIC_ARTIST
    val kind =
        when (item.type) {
            BaseItemKind.MUSIC_ARTIST -> {
                "Artist"
            }

            BaseItemKind.MUSIC_ALBUM -> {
                "Album · " + (item.data.albumArtist ?: "")
            }

            BaseItemKind.PLAYLIST -> {
                "Playlist"
            }

            else -> {
                "Song · " +
                    item.data.artists
                        ?.joinToString(", ")
                        .orEmpty()
            }
        }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.Raised,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border = focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(14.dp),
        ) {
            CoverImage(
                url,
                Modifier.size(88.dp),
                shape = if (round) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(8.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    item.title.orEmpty(),
                    fontSize = BenflixType.pageTitle,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    kind.trimEnd(' ', '·'),
                    color = BenflixColors.Soft,
                    fontSize = BenflixType.small,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Colours for the Browse all tiles (each theme keeps the same colour) */
private val BROWSE_COLOURS =
    listOf(
        Color(0xFFB83B5E),
        Color(0xFF2E86AB),
        Color(0xFFE07A1F),
        Color(0xFF6A4C93),
        Color(0xFF1B998B),
        Color(0xFFC0392B),
        Color(0xFF3D5A80),
        Color(0xFF8E7D2B),
        Color(0xFF9B2C6E),
        Color(0xFF2D6A4F),
    )

/** Browse all (before you type): In season, moods, genres and decades as colour tiles */
private fun LazyListScope.browseAll(
    themes: List<com.github.damontecres.wholphin.services.MusicMix>,
    across: Int,
    onOpen: (com.github.damontecres.wholphin.services.MusicMix) -> Unit,
) {
    if (themes.isEmpty()) return
    item(key = "browse-title") { MusicRowName("Browse all", Modifier.padding(top = 18.dp, bottom = 6.dp)) }
    themes.chunked(across).forEachIndexed { r, row ->
        item(key = "browse-$r") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                row.forEach { mix ->
                    BrowseTile(
                        mix,
                        BROWSE_COLOURS[(mix.key.hashCode() and 0x7fffffff) % BROWSE_COLOURS.size],
                        Modifier.weight(1f),
                    ) { onOpen(mix) }
                }
                repeat(across - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BrowseTile(
    mix: com.github.damontecres.wholphin.services.MusicMix,
    colour: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = colour,
                contentColor = Color.White,
                focusedContainerColor = colour,
                focusedContentColor = Color.White,
            ),
        border = focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.height(84.dp),
    ) {
        Text(
            mix.name,
            fontSize = BenflixType.lead,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(12.dp),
        )
    }
}
