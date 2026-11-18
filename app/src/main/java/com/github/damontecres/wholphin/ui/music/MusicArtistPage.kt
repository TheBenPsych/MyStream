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
import androidx.compose.foundation.layout.heightIn
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    /** Top songs with a heart (changes as you press hearts here) */
    val liked: Set<java.util.UUID> = emptySet(),
    /** Music redesign 3: their top 5 songs worldwide, the ones Benflix doesn't have greyed (empty until Tower answers) */
    val popular: List<PopularLine> = emptyList(),
    /** Your songs by them: the ones you play most */
    val yours: List<BaseItem> = emptyList(),
    /** Discography newest first: albums (and singles) on Benflix and in the catalogue */
    val discAlbums: List<DiscItem> = emptyList(),
    val discSingles: List<DiscItem> = emptyList(),
    /** Fans also like, and the artist's id on Benflix when they're here */
    val fans: List<Pair<com.github.damontecres.wholphin.services.MusicArtist, UUID?>> = emptyList(),
    /** The artist in the music catalogue (for Ask for more by them) */
    val catalogue: com.github.damontecres.wholphin.services.MusicArtist? = null,
    val noMixes: Boolean = false,
)

/** One of the Popular songs */
sealed interface PopularLine {
    data class Have(
        val song: BaseItem,
    ) : PopularLine

    data class Missing(
        val song: com.github.damontecres.wholphin.services.MusicSong,
    ) : PopularLine
}

/** One album in Discography: on Benflix, or only in the catalogue ("Not on Benflix") */
sealed interface DiscItem {
    val year: Int

    data class Here(
        val item: BaseItem,
    ) : DiscItem {
        override val year get() = item.data.productionYear ?: 0
    }

    data class Missing(
        val album: com.github.damontecres.wholphin.services.MusicAlbum,
    ) : DiscItem {
        override val year get() = album.year.toIntOrNull() ?: 0
    }
}

@HiltViewModel
class MusicArtistViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val library: MusicLibrary,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val benflixMusic: com.github.damontecres.wholphin.services.BenflixMusic,
        private val musicMe: com.github.damontecres.wholphin.services.MusicMe,
        val preview: com.github.damontecres.wholphin.services.SongPreview,
        @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    ) : ViewModel() {
        private val _state = MutableStateFlow(MusicArtistState())
        val state: StateFlow<MusicArtistState> = _state
        private var loadedFor: UUID? = null
        private var everything: List<BaseItem>? = null

        fun load(id: UUID) {
            if (loadedFor == id) return
            loadedFor = id
            viewModelScope.launchIO {
                try {
                    val artist = BaseItem(api.userLibraryApi.getItem(itemId = id).content, false)
                    _state.update { it.copy(artist = artist) }
                    val top = library.artistSongs(id, TOP)
                    val all = library.albums(artistId = id, sort = ItemSortBy.PRODUCTION_YEAR, descending = true)
                    // A "single" is an album with one or two songs
                    val (singles, albums) = all.partition { (it.data.childCount ?: 3) <= 2 }
                    _state.update {
                        it.copy(
                            topSongs = top.take(TOP),
                            albums = albums,
                            singles = singles,
                            yours = top.filter { s -> (s.data.userData?.playCount ?: 0) > 0 }.take(TOP),
                            discAlbums = albums.map { a -> DiscItem.Here(a) },
                            discSingles = singles.map { a -> DiscItem.Here(a) },
                            songTotal = library.artistSongCount(id).takeIf { n -> n > 0 } ?: top.size,
                            loaded = true,
                            liked =
                                top
                                    .take(TOP)
                                    .filter { s -> s.favorite }
                                    .map { s -> s.id }
                                    .toSet(),
                        )
                    }
                    musicMe.load()
                    _state.update { it.copy(noMixes = musicMe.isNoArtist(artist.title.orEmpty())) }
                    loadCatalogue(id, artist.title.orEmpty(), albums, singles)
                } catch (ex: kotlinx.coroutines.CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Artist page")
                    _state.update { it.copy(loaded = true) }
                }
            }
        }

        /** Popular worldwide, the whole discography and Fans also like, from the music catalogue through Tower */
        private suspend fun loadCatalogue(
            id: UUID,
            name: String,
            albums: List<BaseItem>,
            singles: List<BaseItem>,
        ) {
            if (name.isBlank()) return
            val found =
                try {
                    benflixMusic.artistFind(name)
                } catch (ex: kotlinx.coroutines.CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.d(ex, "Artist in the catalogue")
                    null
                } ?: return
            val mine = allSongs(id)
            val byKey = mine.groupBy { songKey(it.title) }
            val popular =
                found.popular.map { s ->
                    byKey[songKey(s.title)]?.firstOrNull()?.let { PopularLine.Have(it) } ?: PopularLine.Missing(s)
                }
            val here = (albums + singles).map { songKey(it.title) }.toSet()
            val missing = found.albums.filter { !it.have && songKey(it.title) !in here }
            val (missingSingles, missingAlbums) = missing.partition { it.type == "single" || it.type == "ep" || it.songs in 1..2 }
            val fans =
                kotlinx.coroutines.coroutineScope {
                    found.related
                        .take(12)
                        .map { a ->
                            async {
                                val local =
                                    try {
                                        library
                                            .search(a.name, org.jellyfin.sdk.model.api.BaseItemKind.MUSIC_ARTIST, 5)
                                            .firstOrNull { it.title.equals(a.name, true) }
                                            ?.id
                                    } catch (ex: Exception) {
                                        null
                                    }
                                a to local
                            }
                        }.awaitAll()
                }
            _state.update { st ->
                st.copy(
                    popular = popular,
                    liked = st.liked + popular.filterIsInstance<PopularLine.Have>().filter { it.song.favorite }.map { it.song.id },
                    discAlbums =
                        (
                            albums.map {
                                DiscItem.Here(
                                    it,
                                )
                            } + missingAlbums.map { DiscItem.Missing(it) }
                        ).sortedByDescending { it.year },
                    discSingles =
                        (
                            singles.map {
                                DiscItem.Here(
                                    it,
                                )
                            } + missingSingles.map { DiscItem.Missing(it) }
                        ).sortedByDescending { it.year },
                    // Artists on Benflix first
                    fans = fans.sortedBy { if (it.second != null) 0 else 1 },
                    catalogue = found.artist,
                )
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

        /** Don't play this artist in my mixes (or play them again) */
        fun toggleNoMixes() {
            val name = _state.value.artist?.title ?: return
            val on = !_state.value.noMixes
            _state.update { it.copy(noMixes = on) }
            viewModelScope.launchIO {
                musicMe.noArtist(name, on)
                com.github.damontecres.wholphin.ui.showToast(
                    context,
                    if (on) "$name won't play in your mixes any more" else "$name can play in your mixes again",
                )
            }
        }

        /** Ask on a greyed Popular song */
        fun askFor(song: com.github.damontecres.wholphin.services.MusicSong) {
            if (song.asked != null) return
            preview.stop()
            setAsked(song.id, "Asking...")
            viewModelScope.launchIO {
                val words =
                    try {
                        benflixMusic.ask("song", song.id).words.ifBlank { "Asked ✓" }
                    } catch (ex: kotlinx.coroutines.CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Ask for %s", song.title)
                        null
                    }
                setAsked(song.id, words)
            }
        }

        private fun setAsked(
            id: Long,
            words: String?,
        ) = _state.update { st ->
            st.copy(
                popular =
                    st.popular.map {
                        if (it is PopularLine.Missing &&
                            it.song.id == id
                        ) {
                            it.copy(song = it.song.copy(asked = words))
                        } else {
                            it
                        }
                    },
            )
        }

        fun likedChanged(
            id: java.util.UUID,
            liked: Boolean,
        ) = _state.update { if (liked) it.copy(liked = it.liked + id) else it.copy(liked = it.liked - id) }

        /** Everything by them, for Play top songs and Shuffle */
        suspend fun allSongs(id: UUID): List<BaseItem> = everything ?: library.artistSongs(id, 5000).also { everything = it }

        companion object {
            const val TOP = 5
        }
    }

/**
 * Benflix music: an artist (approved ar-hero, Music redesign 3 p3-artist): their big photo with the name across it
 * and how many of their songs you have. Buttons: Play, Shuffle, Follow, Artist radio, More (Don't play this artist
 * in my mixes). Then Popular (their top 5 worldwide; ones you don't have greyed with a preview and Ask), Your songs
 * by them, Discography newest first with Albums / Singles (Not on Benflix tags), and Fans also like.
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
    androidx.lifecycle.compose.LifecycleResumeEffect(viewModel.preview) { onPauseOrDispose { viewModel.preview.stop() } }
    val state by viewModel.state.collectAsState()
    val music by actions.musicService.state.collectAsState()
    // Glowfin: Easy mode is gone (Ben, ui-17)
    val easy = false
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp || config.smallestScreenWidthDp >= 600
    val overlap = LocalTopBarOverlap.current
    val side = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge
    val imageUrlService = LocalImageUrlService.current
    val density = LocalDensity.current
    val playFocus = remember { FocusRequester() }
    // Down from the top bar comes back to Play (Ben got stuck in the top bar on a Daily mix)
    com.github.damontecres.wholphin.ui.nav
        .RegisterTopBarDown(playFocus)
    var menuFor by remember { mutableStateOf<BaseItem?>(null) }
    var pageMenu by remember { mutableStateOf(false) }
    var sendTo by remember { mutableStateOf(false) }
    var singlesOn by androidx.compose.runtime.saveable
        .rememberSaveable { mutableStateOf(false) }
    val artist = state.artist
    // Only the first time: coming Back keeps the spot you were on instead of jumping up to Play
    var focusedOnce by androidx.compose.runtime.saveable
        .rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(artist != null) {
        if (artist != null && isTv && !focusedOnce) {
            delay(80)
            focusedOnce = playFocus.tryRequestFocus()
        }
    }
    val name = artist?.title.orEmpty()
    val place = remember(artist?.id) { artist?.let { musicPlaceOf(it, name) } }
    val playAll: (Boolean) -> Unit = { shuffle ->
        val songs = state.topSongs
        if (songs.isNotEmpty()) {
            actions.viewModelScopeLaunch {
                val all = viewModel.allSongs(id)
                actions.playList(all.ifEmpty { songs }, 0, shuffle, artist, name, openNow = true, place = place)
            }
        }
    }
    val counts =
        listOfNotNull(
            state.songTotal.takeIf { it > 0 }?.let { "${songCount(it)} on Glowfin" },
            if (artist?.favorite == true) "Following" else null,
        ).joinToString(" · ")
    val tint =
        rememberCoverTint(remember(artist?.id) { artist?.let { imageUrlService.getItemImageUrl(it, ImageType.PRIMARY, fillHeight = 96) } })
    val buttons: @Composable () -> Unit = {
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "play") {
                MusicButton(if (wide || easy) "Play" else null, null, filledPlay = true, primary = true, big = easy, onClick = {
                    playAll(false)
                }, modifier = Modifier.focusRequester(playFocus))
            }
            item(
                key = "shuffle",
            ) { MusicButton(if (easy) "Shuffle" else null, BenflixIcon.SHUFFLE, big = easy, onClick = { playAll(true) }) }
            item(key = "follow") {
                MusicButton(
                    if (artist?.favorite == true) "Following" else "Follow",
                    if (artist?.favorite == true) BenflixIcon.CHECK else BenflixIcon.PLUS,
                    big = easy,
                    onClick = viewModel::toggleFollow,
                )
            }
            item(key = "radio") {
                MusicButton(if (wide || easy) "Artist radio" else null, BenflixIcon.NOTE, big = easy, onClick = {
                    artist?.let { actions.startRadio(it) }
                })
            }
            item(key = "more") { MusicButton(if (easy) "More" else null, BenflixIcon.MORE, big = easy, onClick = { pageMenu = true }) }
        }
    }
    val songRow: @Composable (BaseItem, Int, List<BaseItem>, String?) -> Unit = { song, index, list, line ->
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
                liked = song.id in state.liked,
                big = easy,
                onClick = { actions.playList(list, index, false, artist, name, place = place) },
                onMore = { menuFor = song },
                modifier = Modifier.weight(1f),
            )
            if (line != null) {
                Text(line, color = BenflixColors.Muted, fontSize = BenflixType.small, modifier = Modifier.padding(horizontal = 8.dp))
            }
            if (!isTv) MusicButton(null, BenflixIcon.MORE_VERT, onClick = { menuFor = song })
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val heroHeight = if (wide) ((maxHeight - overlap) * 0.44f).coerceIn(200.dp, 320.dp) + overlap else 0.dp
        CoverWash(tint, (if (wide) heroHeight else 380.dp) + 60.dp)
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
                    // Grows taller when the words need it, so they never go up under the top bar
                    Box(Modifier.fillMaxWidth().heightIn(min = heroHeight)) {
                        Box(Modifier.align(Alignment.TopEnd).fillMaxWidth(0.6f).height(heroHeight)) {
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
                                ).padding(start = side, end = side, top = overlap + 20.dp, bottom = 12.dp)
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
                    // Phones: the big photo with the name across the bottom of it
                    val px = with(density) { 360.dp.roundToPx() }
                    val photo =
                        remember(artist?.id) {
                            artist?.let { imageUrlService.getItemImageUrl(it, ImageType.PRIMARY, fillWidth = px, fillHeight = px) }
                        }
                    Column(Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().height(300.dp)) {
                            CoverImage(photo, Modifier.fillMaxSize(), shape = RectangleShape)
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(0.4f to Color.Transparent, 1f to BenflixColors.Tray),
                                ),
                            )
                            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = side, vertical = 10.dp)) {
                                if (counts.isNotBlank()) Text(counts, color = BenflixColors.Soft, fontSize = BenflixType.small)
                                Text(
                                    name,
                                    color = BenflixColors.Text,
                                    fontSize = BenflixType.hero,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Box(Modifier.padding(start = side, end = side, top = 8.dp, bottom = 8.dp)) { buttons() }
                    }
                }
            }
            // Popular: their top 5 worldwide once Tower answers (until then, your top songs by them)
            if (state.popular.isNotEmpty()) {
                item(key = "pop-name") { MusicRowName("Popular", Modifier.padding(start = side, top = 12.dp, bottom = 6.dp)) }
                val haveList = state.popular.filterIsInstance<PopularLine.Have>().map { it.song }
                state.popular.forEachIndexed { index, line ->
                    item(key = "pop-$index") {
                        when (line) {
                            is PopularLine.Have -> {
                                songRow(line.song, haveList.indexOf(line.song).coerceAtLeast(0), haveList, null)
                            }

                            is PopularLine.Missing -> {
                                MissingSongRow(
                                    song = line.song,
                                    number = index + 1,
                                    preview = viewModel.preview,
                                    onAsk = { viewModel.askFor(line.song) },
                                    withCover = true,
                                    big = easy,
                                    modifier = Modifier.padding(horizontal = side - 10.dp).widthIn(max = if (wide) 900.dp else 2000.dp),
                                )
                            }
                        }
                    }
                }
            } else if (state.topSongs.isNotEmpty()) {
                item(key = "top-name") { MusicRowName("Top songs", Modifier.padding(start = side, top = 12.dp, bottom = 6.dp)) }
                state.topSongs.forEachIndexed { index, song ->
                    item(key = "top-$index") { songRow(song, index, state.topSongs, null) }
                }
            }
            if (state.popular.isNotEmpty() && state.yours.isNotEmpty()) {
                item(key = "yours-name") { MusicRowName("Your songs by $name", Modifier.padding(start = side, top = 20.dp, bottom = 6.dp)) }
                state.yours.forEachIndexed { index, song ->
                    item(key = "yours-$index") {
                        val n = song.data.userData?.playCount ?: 0
                        songRow(song, index, state.yours, if (n == 1) "Played once" else "Played $n times")
                    }
                }
            }
            val disc =
                if (singlesOn &&
                    state.discSingles.isNotEmpty()
                ) {
                    state.discSingles
                } else {
                    state.discAlbums.ifEmpty { state.discSingles }
                }
            if (disc.isNotEmpty()) {
                item(key = "disc") {
                    Column(Modifier.padding(top = 28.dp)) {
                        MusicRowName("Discography", Modifier.padding(start = side, bottom = 8.dp))
                        if (state.discAlbums.isNotEmpty() && state.discSingles.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(start = side, bottom = 10.dp),
                            ) {
                                MusicButton("Albums", null, onClick = { singlesOn = false }, primary = !singlesOn)
                                MusicButton("Singles", null, onClick = { singlesOn = true }, primary = singlesOn)
                            }
                        }
                        val size = if (wide) 132.dp else 116.dp
                        androidx.compose.foundation.lazy.LazyRow(
                            contentPadding = PaddingValues(horizontal = side),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(disc.size, key = { i ->
                                when (val d = disc[i]) {
                                    is DiscItem.Here -> "h-${d.item.id}"
                                    is DiscItem.Missing -> "m-${d.album.id}"
                                }
                            }) { i ->
                                when (val d = disc[i]) {
                                    is DiscItem.Here -> {
                                        MusicCard(
                                            item = d.item,
                                            size = size,
                                            line =
                                                d.item.data.productionYear
                                                    ?.toString(),
                                            onClick = { actions.navigationManager.navigateTo(d.item.destination()) },
                                        )
                                    }

                                    is DiscItem.Missing -> {
                                        CatalogueAlbumCard(d.album, size) { openInAsk(actions.navigationManager, d.album) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (state.fans.isNotEmpty()) {
                item(key = "fans") {
                    CatalogueArtistRow(
                        "Fans also like",
                        state.fans.map { it.first to (it.second != null) },
                        side,
                        if (wide) 120.dp else 100.dp,
                    ) { a, here ->
                        val localId = state.fans.firstOrNull { it.first.id == a.id }?.second
                        if (here && localId != null) {
                            actions.navigationManager.navigateTo(
                                com.github.damontecres.wholphin.ui.nav.Destination
                                    .MediaItem(localId, org.jellyfin.sdk.model.api.BaseItemKind.MUSIC_ARTIST),
                            )
                        } else {
                            openInAsk(actions.navigationManager, a)
                        }
                    }
                }
            }
            if (state.loaded && state.topSongs.isEmpty() && state.albums.isEmpty() && state.singles.isEmpty()) {
                item(key = "none") {
                    Text(
                        "No songs by $name on Glowfin yet.",
                        color = BenflixColors.Soft,
                        fontSize = BenflixType.body,
                        modifier = Modifier.padding(start = side, top = 16.dp),
                    )
                }
            }
        }
    }
    menuFor?.let { song ->
        SongMenu(
            song = song,
            liked = song.id in state.liked,
            onDismiss = { menuFor = null },
            onLikedChanged = { viewModel.likedChanged(song.id, it) },
            actions = actions,
        )
    }
    if (pageMenu) {
        val entries =
            buildList {
                add(
                    com.github.damontecres.wholphin.ui.components.DialogItem(
                        headlineContent = {
                            Text(if (state.noMixes) "Play $name in my mixes again" else "Don't play this artist in my mixes")
                        },
                        onClick = {
                            pageMenu = false
                            viewModel.toggleNoMixes()
                        },
                    ),
                )
                state.catalogue?.let { c ->
                    add(
                        com.github.damontecres.wholphin.ui.components.DialogItem(
                            headlineContent = { Text("Ask for more by $name") },
                            onClick = {
                                pageMenu = false
                                openInAsk(actions.navigationManager, c)
                            },
                        ),
                    )
                }
                add(
                    com.github.damontecres.wholphin.ui.components.DialogItem(
                        headlineContent = { Text("Send to someone") },
                        onClick = {
                            pageMenu = false
                            sendTo = true
                        },
                    ),
                )
                if (state.topSongs.isNotEmpty()) {
                    add(
                        com.github.damontecres.wholphin.ui.components.DialogItem(
                            headlineContent = { Text("Add all to Up next") },
                            onClick = {
                                pageMenu = false
                                actions.viewModelScopeLaunch { actions.addAllToUpNext(viewModel.allSongs(id)) }
                            },
                        ),
                    )
                }
            }
        com.github.damontecres.wholphin.ui.components.DialogPopup(
            showDialog = true,
            title = name,
            dialogItems = entries,
            onDismissRequest = { pageMenu = false },
            dismissOnClick = false,
        )
    }
    if (sendTo) {
        SendToDialog(
            place =
                com.github.damontecres.wholphin.services
                    .MusicPlace("artist", id.toString(), name),
            onDismiss = { sendTo = false },
        )
    }
}
