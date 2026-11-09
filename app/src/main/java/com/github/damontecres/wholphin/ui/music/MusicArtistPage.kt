package com.github.damontecres.wholphin.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.MusicLibrary
import com.github.damontecres.wholphin.services.NowPlayingStatus
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.LocalTopBarOverlap
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

data class MusicArtistState(
    val artist: BaseItem? = null,
    val topSongs: List<BaseItem> = emptyList(),
    val albums: List<BaseItem> = emptyList(),
    val singles: List<BaseItem> = emptyList(),
    val songTotal: Int = 0,
    val loaded: Boolean = false,
)

@HiltViewModel
class MusicArtistViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val library: MusicLibrary,
        private val favoriteWatchManager: FavoriteWatchManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(MusicArtistState())
        val state: StateFlow<MusicArtistState> = _state
        private var loadedFor: UUID? = null

        fun load(id: UUID) {
            if (loadedFor == id) return
            loadedFor = id
            viewModelScope.launchIO {
                try {
                    val artist = BaseItem(api.userLibraryApi.getItem(itemId = id).content, false)
                    _state.update { it.copy(artist = artist) }
                    val top = library.artistSongs(id, 200)
                    val all = library.albums(artistId = id, sort = ItemSortBy.PRODUCTION_YEAR, descending = true)
                    // A "single" is an album with one or two songs
                    val (singles, albums) = all.partition { (it.data.childCount ?: 3) <= 2 }
                    _state.update {
                        it.copy(topSongs = top.take(TOP), albums = albums, singles = singles, songTotal = top.size, loaded = true)
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "Artist page")
                    _state.update { it.copy(loaded = true) }
                }
            }
        }

        /** Follow: new music from them shows as a news pill on Home */
        fun toggleFollow() {
            val artist = _state.value.artist ?: return
            viewModelScope.launchIO {
                try {
                    val data = favoriteWatchManager.setFavorite(artist.id, !artist.favorite)
                    _state.update { it.copy(artist = artist.copy(data = artist.data.copy(userData = data))) }
                } catch (ex: Exception) {
                    Timber.w(ex, "Follow artist")
                }
            }
        }

        /** Everything by them, for Play top songs and Shuffle */
        suspend fun allSongs(id: UUID): List<BaseItem> = library.artistSongs(id, 500)

        companion object {
            const val TOP = 5
        }
    }

/**
 * Benflix music: an artist (approved ar-hero): their big photo on the right, Play top songs, Shuffle and
 * Follow on the left, then Top songs, Albums and Singles.
 */
@Composable
fun MusicArtistPage(
    id: UUID,
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: MusicArtistViewModel = hiltViewModel(key = "artist-$id"),
    actions: MusicActionsViewModel = hiltViewModel(),
) {
    LaunchedEffect(id) { viewModel.load(id) }
    val state by viewModel.state.collectAsState()
    val music by actions.musicService.state.collectAsState()
    val easy = preferences.appPreferences.benflixPreferences.easyRead
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp || config.smallestScreenWidthDp >= 600
    val overlap = LocalTopBarOverlap.current
    val side = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge
    val imageUrlService = LocalImageUrlService.current
    val density = LocalDensity.current
    val playFocus = remember { FocusRequester() }
    var menuFor by remember { mutableStateOf<Int?>(null) }
    val artist = state.artist
    LaunchedEffect(artist != null) {
        if (artist != null && isTv) {
            delay(80)
            playFocus.tryRequestFocus()
        }
    }
    val name = artist?.title.orEmpty()
    val playAll: (Boolean) -> Unit = { shuffle ->
        val songs = state.topSongs
        if (songs.isNotEmpty()) {
            actions.viewModelScopeLaunch {
                val all = viewModel.allSongs(id)
                actions.playList(all.ifEmpty { songs }, 0, shuffle, artist, name)
            }
        }
    }
    val counts =
        listOfNotNull(
            state.albums.size
                .takeIf { it > 0 }
                ?.let { if (it == 1) "1 album" else "$it albums" },
            state.singles.size
                .takeIf { it > 0 }
                ?.let { if (it == 1) "1 single" else "$it singles" },
            state.songTotal.takeIf { it > 0 }?.let { "${songCount(it)} on Benflix" },
        ).joinToString(" · ")
    val buttons: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MusicButton("Play top songs", null, filledPlay = true, primary = true, big = easy, onClick = {
                playAll(false)
            }, modifier = Modifier.focusRequester(playFocus))
            MusicButton(if (easy) "Shuffle" else null, BenflixIcon.SHUFFLE, big = easy, onClick = { playAll(true) })
            MusicButton(
                if (artist?.favorite ==
                    true
                ) {
                    "Following"
                } else {
                    "Follow"
                },
                if (artist?.favorite ==
                    true
                ) {
                    BenflixIcon.CHECK
                } else {
                    BenflixIcon.PLUS
                },
                big = easy,
                onClick = viewModel::toggleFollow,
            )
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val heroHeight = if (wide) ((maxHeight - overlap) * 0.44f).coerceIn(200.dp, 320.dp) + overlap else 0.dp
        LazyColumn(contentPadding = PaddingValues(bottom = 48.dp), modifier = Modifier.fillMaxSize()) {
            item(key = "hero") {
                if (wide) {
                    val px = with(density) { heroHeight.roundToPx() }
                    val photo =
                        remember(artist?.id, px) {
                            artist?.let {
                                imageUrlService.getItemImageUrl(it, ImageType.BACKDROP, fillHeight = px)
                                    ?: imageUrlService.getItemImageUrl(it, ImageType.PRIMARY, fillHeight = px)
                            }
                        }
                    Box(Modifier.fillMaxWidth().height(heroHeight)) {
                        Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(0.6f).fillMaxHeight()) {
                            CoverImage(photo, Modifier.fillMaxSize(), shape = RectangleShape)
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            0f to BenflixColors.Tray,
                                            0.45f to BenflixColors.Tray.copy(alpha = .4f),
                                            1f to Color.Transparent,
                                        ),
                                    ),
                            )
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(
                                        0.55f to Color.Transparent,
                                        1f to BenflixColors.Tray,
                                    ),
                                ),
                            )
                        }
                        Column(
                            Modifier
                                .align(
                                    Alignment.BottomStart,
                                ).padding(start = side, end = side, top = overlap, bottom = 12.dp)
                                .widthIn(max = 640.dp),
                        ) {
                            MusicKicker("Artist", color = BenflixColors.Soft)
                            Text(
                                name,
                                color = BenflixColors.Text,
                                fontSize = BenflixType.hero,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (counts.isNotBlank()) {
                                Text(
                                    counts,
                                    color = BenflixColors.Soft,
                                    fontSize = BenflixType.lead,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            Box(Modifier.padding(top = 16.dp)) { buttons() }
                        }
                    }
                } else {
                    val px = with(density) { 200.dp.roundToPx() }
                    val photo =
                        remember(
                            artist?.id,
                        ) { artist?.let { imageUrlService.getItemImageUrl(it, ImageType.PRIMARY, fillWidth = px, fillHeight = px) } }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = side, vertical = 8.dp),
                    ) {
                        CoverImage(photo, Modifier.size(200.dp), shape = CircleShape)
                        MusicKicker("Artist", Modifier.padding(top = 12.dp), color = BenflixColors.Soft)
                        Text(name, color = BenflixColors.Text, fontSize = BenflixType.hero, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                        if (counts.isNotBlank()) Text(counts, color = BenflixColors.Soft, fontSize = BenflixType.small)
                        Box(Modifier.padding(top = 12.dp, bottom = 8.dp)) { buttons() }
                    }
                }
            }
            if (state.topSongs.isNotEmpty()) {
                item(key = "top-name") { MusicRowName("Top songs", Modifier.padding(start = side, top = 12.dp, bottom = 6.dp)) }
                state.topSongs.forEachIndexed { index, song ->
                    item(key = "top-$index") {
                        Row(
                            Modifier.padding(horizontal = side - 10.dp).widthIn(max = if (wide) 900.dp else 2000.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SongRow(
                                song = song,
                                number = index + 1,
                                withCover = true,
                                isCurrent = music.currentAudio?.id == song.id,
                                isPlaying = music.status == NowPlayingStatus.PLAYING,
                                liked = song.favorite || actions.musicService.isLikedNow(song.id),
                                big = easy,
                                onClick = { actions.playList(state.topSongs, index, false, artist, name) },
                                onMore = { menuFor = index },
                                modifier = Modifier.weight(1f),
                            )
                            if (!isTv) MusicButton(null, BenflixIcon.MORE_VERT, onClick = { menuFor = index })
                        }
                    }
                }
            }
            if (state.albums.isNotEmpty()) {
                item(key = "albums") {
                    AlbumRow(
                        "Albums",
                        state.albums,
                        side,
                        if (wide) 132.dp else 116.dp,
                    ) { actions.navigationManager.navigateTo(it.destination()) }
                }
            }
            if (state.singles.isNotEmpty()) {
                item(key = "singles") {
                    AlbumRow(
                        "Singles",
                        state.singles,
                        side,
                        if (wide) 120.dp else 104.dp,
                    ) { actions.navigationManager.navigateTo(it.destination()) }
                }
            }
            if (state.loaded && state.topSongs.isEmpty() && state.albums.isEmpty() && state.singles.isEmpty()) {
                item(key = "none") {
                    Text(
                        "No songs by $name on Benflix yet.",
                        color = BenflixColors.Soft,
                        fontSize = BenflixType.body,
                        modifier = Modifier.padding(start = side, top = 16.dp),
                    )
                }
            }
        }
    }
    menuFor?.let { index ->
        state.topSongs.getOrNull(index)?.let { song ->
            SongMenu(
                song = song,
                liked = song.favorite || actions.musicService.isLikedNow(song.id),
                onDismiss = { menuFor = null },
                actions = actions,
            )
        }
    }
}
