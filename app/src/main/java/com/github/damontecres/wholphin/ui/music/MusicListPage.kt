package com.github.damontecres.wholphin.ui.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.MusicLibrary
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.door.BrowseTile
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import javax.inject.Inject

data class MusicListState(
    val items: List<BaseItem> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class MusicListViewModel
    @Inject
    constructor(
        private val library: MusicLibrary,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(MusicListState())
        val state: StateFlow<MusicListState> = _state
        private var loadedFor: String? = null

        fun load(
            kind: String,
            id: String?,
        ) {
            val key = "$kind-$id"
            if (loadedFor == key) return
            loadedFor = key
            viewModelScope.launchIO {
                val items =
                    try {
                        when (kind) {
                            "recent" -> library.albums(ItemSortBy.DATE_CREATED, descending = true, limit = MAX)
                            "artists" -> library.artists(limit = MAX)
                            "genres" -> library.genres()
                            "genre" -> library.albums(genreId = id?.toUUIDOrNull(), limit = MAX)
                            "playlists" -> library.playlists()
                            else -> library.albums(limit = MAX)
                        }
                    } catch (ex: Exception) {
                        Timber.w(ex, "Music list %s", kind)
                        emptyList()
                    }
                _state.value = MusicListState(items, loaded = true)
            }
        }

        companion object {
            private const val MAX = 3000
        }
    }

/** Benflix music: all albums, artists, playlists or genres as a grid ("See all" and "Browse by") */
@Composable
fun MusicListPage(
    destination: Destination.MusicList,
    modifier: Modifier = Modifier,
    viewModel: MusicListViewModel = hiltViewModel(key = "music-list-${destination.kind}-${destination.id}"),
) {
    LaunchedEffect(destination) { viewModel.load(destination.kind, destination.id) }
    val state by viewModel.state.collectAsState()
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp
    val first = remember { FocusRequester() }
    LaunchedEffect(state.loaded) {
        if (state.loaded && isTv && state.items.isNotEmpty()) {
            delay(80)
            first.tryRequestFocus()
        }
    }
    val genres = destination.kind == "genres"
    Column(modifier = modifier.fillMaxSize().padding(top = 8.dp)) {
        Text(
            text = destination.title.ifBlank { "Music" },
            color = BenflixColors.Text,
            fontSize = BenflixType.pageTitle,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        if (state.loaded && state.items.isEmpty()) {
            Text("Nothing here yet.", color = BenflixColors.Soft, fontSize = BenflixType.body)
            return@Column
        }
        val size = if (wide) 132.dp else 104.dp
        LazyVerticalGrid(
            columns = if (genres) GridCells.Adaptive(if (wide) 220.dp else 150.dp) else GridCells.Adaptive(size + 14.dp),
            contentPadding = PaddingValues(bottom = 48.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            itemsIndexed(state.items, key = { _, it -> it.id.toString() }) { i, item ->
                val mod = if (i == 0) Modifier.focusRequester(first) else Modifier
                if (genres) {
                    BrowseTile(
                        title = item.title.orEmpty(),
                        onClick = {
                            viewModel.navigationManager.navigateTo(
                                Destination.MusicList("genre", item.id.toString(), item.title.orEmpty()),
                            )
                        },
                        modifier = mod,
                    )
                } else {
                    MusicCard(
                        item = item,
                        size = size,
                        onClick = { viewModel.navigationManager.navigateTo(item.destination()) },
                        modifier = mod,
                    )
                }
            }
        }
    }
}
