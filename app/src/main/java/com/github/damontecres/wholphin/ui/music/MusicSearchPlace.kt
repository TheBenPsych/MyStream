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
import androidx.compose.foundation.layout.padding
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
) {
    val empty get() = songs.isEmpty() && artists.isEmpty() && albums.isEmpty() && playlists.isEmpty()
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
    ) : ViewModel() {
        val query = MutableStateFlow("")
        private val _results = MutableStateFlow(MusicSearchResults())
        val results: StateFlow<MusicSearchResults> = _results
        private var job: Job? = null

        init {
            viewModelScope.launchIO { musicMe.load() }
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
                                )
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
    val side = if (wide) 0.dp else BenflixSpace.phoneEdge
    val list: LazyListScope.() -> Unit = {
        if (query.trim().length < 2) {
            recentSearches(
                searches = me.searches.map { it.q },
                onPick = viewModel::type,
                onRemove = { viewModel.remove(it) },
                onClear = { viewModel.clear() },
            )
        } else {
            searchResults(
                results = results,
                onSong = viewModel::play,
                onOpen = viewModel::open,
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
) {
    if (results.empty) {
        item(key = "nothing") {
            MusicEmptyLine(if (results.searching) "Looking…" else "Nothing on Benflix by that name. Try Ask for music to get it.")
        }
        return
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
}
