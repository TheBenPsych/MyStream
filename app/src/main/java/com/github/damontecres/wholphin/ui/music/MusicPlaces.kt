package com.github.damontecres.wholphin.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.NowPlayingStatus
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.door.DOOR_HOME
import com.github.damontecres.wholphin.ui.door.DoorChip
import com.github.damontecres.wholphin.ui.door.DoorFrameState
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.focusRing
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import javax.inject.Inject

/** Music redesign 3: the four places (Home is the door's own Home) */
const val MUSIC_SEARCH = "search"
const val MUSIC_LIBRARY = "library"
const val MUSIC_ASK = "ask"

/** The teal Requests colour, for Ask for music */
val MusicAskColor = Color(0xFF4FD1C5)

/**
 * Where the phone tabs (and anything else outside the Music door) tell the door which place to show. The door
 * registers its [frame] while it's open and keeps [place] up to date, so the tabs can light up the right one.
 */
object MusicPlaceNav {
    var frame: DoorFrameState? = null

    /** A place picked from outside the door, waiting for the door to show it */
    var pending by mutableStateOf<String?>(null)

    var place by mutableStateOf(DOOR_HOME)
}

/** A page that belongs to Music: the door, its lists, Up next, and album, artist, playlist and song pages */
fun Destination.isMusicPage(): Boolean =
    this is Destination.MusicDoor ||
        this is Destination.MusicList ||
        this is Destination.MusicUpNext ||
        (
            this is Destination.MediaItem &&
                (
                    type == BaseItemKind.MUSIC_ALBUM ||
                        type == BaseItemKind.MUSIC_ARTIST ||
                        type == BaseItemKind.PLAYLIST ||
                        type == BaseItemKind.AUDIO
                )
        )

@HiltViewModel
class MusicPlacesViewModel
    @Inject
    constructor(
        val musicService: MusicService,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        /** A phone tab: switch place inside the door, or go back to the door (opening it if it isn't open) */
        fun go(
            key: String,
            onDoor: Boolean,
        ) {
            val frame = MusicPlaceNav.frame
            if (onDoor && frame != null) {
                if (frame.section == key && key == DOOR_HOME) frame.toTop() else frame.pick(key)
                return
            }
            MusicPlaceNav.pending = key
            val stack = navigationManager.backStack
            val at = stack.indexOfLast { it is Destination.MusicDoor }
            if (at >= 0) navigationManager.goBackTo(at) else navigationManager.navigateTo(Destination.MusicDoor)
        }

        fun open() = navigationManager.navigateTo(Destination.NowPlaying)

        fun upNext() = navigationManager.navigateTo(Destination.MusicUpNext)

        fun playPause() {
            val p = musicService.player
            if (p.isPlaying) p.pause() else p.play()
        }

        fun next() = musicService.player.seekToNext()

        fun previous() = musicService.player.seekToPrevious()

        fun like() {
            val st = musicService.state.value
            val id = st.currentAudio?.id ?: return
            viewModelScope.launchIO { musicService.setLiked(id, !st.liked) }
        }
    }

private data class MusicTab(
    val key: String,
    val label: String,
    val icon: BenflixIcon,
)

private val TABS =
    listOf(
        MusicTab(DOOR_HOME, "Home", BenflixIcon.HOME),
        MusicTab(MUSIC_SEARCH, "Search", BenflixIcon.SEARCH),
        MusicTab(MUSIC_LIBRARY, "Library", BenflixIcon.LIST),
        MusicTab(MUSIC_ASK, "Ask", BenflixIcon.PLUS),
    )

/**
 * Phones (approved g2-tabs-b): big tabs along the bottom of every Music page, under the mini player. The place
 * you're on gets a soft violet bubble behind its icon. Ask for music is teal, like Requests.
 */
@Composable
fun MusicBottomTabs(
    destination: Destination,
    modifier: Modifier = Modifier,
    viewModel: MusicPlacesViewModel = hiltViewModel(),
) {
    val onDoor = destination is Destination.MusicDoor
    val current = if (onDoor) MusicPlaceNav.place else null
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(BenflixColors.Tray)
                .padding(top = 6.dp, bottom = 8.dp),
    ) {
        TABS.forEach { tab ->
            val on = tab.key == current
            val ask = tab.key == MUSIC_ASK
            Surface(
                onClick = { viewModel.go(tab.key, onDoor) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = BenflixColors.FocusRow,
                        pressedContainerColor = BenflixColors.FocusRow,
                    ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier = Modifier.weight(1f).height(64.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .width(60.dp)
                                .height(32.dp)
                                .background(
                                    if (on) BenflixColors.Music.copy(alpha = .28f) else Color.Transparent,
                                    RoundedCornerShape(50),
                                ),
                    ) {
                        BenflixIconImage(
                            tab.icon,
                            color =
                                when {
                                    on -> BenflixColors.Music
                                    ask -> MusicAskColor
                                    else -> BenflixColors.Soft
                                },
                            size = 26.dp,
                            contentDescription = null,
                        )
                    }
                    Text(
                        text = tab.label,
                        color =
                            when {
                                on -> BenflixColors.Text
                                ask -> MusicAskColor
                                else -> BenflixColors.Muted
                            },
                        fontSize = BenflixType.small,
                        fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}

/**
 * TV (approved r3-q-tvbar): on Music pages a slim player strip along the bottom while music plays, in place of
 * the pill by the clock: the song (OK opens Now playing), how far through it is, and Previous, Play/Pause, Next,
 * the heart and Up next.
 */
@Composable
fun MusicStrip(
    modifier: Modifier = Modifier,
    viewModel: MusicPlacesViewModel = hiltViewModel(),
) {
    val music by viewModel.musicService.state.collectAsState()
    val audio = music.currentAudio
    if (music.queueSize == 0 || audio == null) return
    val playing = music.status == NowPlayingStatus.PLAYING
    var progress by remember { mutableFloatStateOf(0f) }
    var now by remember { mutableStateOf("") }
    var length by remember { mutableStateOf("") }
    LaunchedEffect(audio.key) {
        while (true) {
            val p = viewModel.musicService.player
            val d = p.duration.takeIf { it > 0 } ?: audio.runtime?.inWholeMilliseconds ?: 0L
            progress = if (d > 0) (p.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            now = clockOf(p.currentPosition)
            length = if (d > 0) clockOf(d) else ""
            delay(500)
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .background(Color(0xFF14121E))
                .padding(start = 40.dp, end = 32.dp, top = 8.dp, bottom = 8.dp),
    ) {
        val shape = RoundedCornerShape(10.dp)
        Surface(
            onClick = viewModel::open,
            shape = ClickableSurfaceDefaults.shape(shape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = BenflixColors.FocusRow,
                ),
            border = focusRing(shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier = Modifier.width(300.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(6.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CoverImage(audio.imageUrl, Modifier.size(44.dp), shape = RoundedCornerShape(6.dp))
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PlayingBars(playing, size = 12.dp)
                        Text(
                            audio.title.orEmpty(),
                            color = BenflixColors.Text,
                            fontSize = BenflixType.small,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        audio.artistNames.orEmpty(),
                        color = BenflixColors.Muted,
                        fontSize = BenflixType.tag,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(now, color = BenflixColors.Muted, fontSize = BenflixType.tag, fontWeight = FontWeight.SemiBold)
        Box(
            Modifier
                .weight(1f)
                .height(4.dp)
                .background(BenflixColors.Line, RoundedCornerShape(50)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(BenflixColors.Music, RoundedCornerShape(50)),
            )
        }
        Text(length, color = BenflixColors.Muted, fontSize = BenflixType.tag, fontWeight = FontWeight.SemiBold)
        MusicButton(null, BenflixIcon.PREVIOUS, onClick = viewModel::previous)
        MusicButton(null, if (playing) BenflixIcon.PAUSE else null, filledPlay = !playing, primary = true, onClick = viewModel::playPause)
        MusicButton(null, BenflixIcon.NEXT, onClick = viewModel::next)
        MusicButton(null, BenflixIcon.HEART, filledHeart = music.liked, onClick = viewModel::like)
        MusicButton("Up next", BenflixIcon.LIST, onClick = viewModel::upNext)
    }
}

private fun clockOf(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** The chips at the top of Music Home (approved x-filter): pick one and the page shows only that, as a grid */
private val HOME_CHIPS =
    listOf(
        "all" to "All",
        "mixes" to "Mixes",
        "playlists" to "Playlists",
        "albums" to "Albums",
        "artists" to "Artists",
        "new" to "New to you",
    )

@Composable
fun MusicHomeChips(
    chip: String,
    onChip: (String) -> Unit,
    start: Dp,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = start),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(HOME_CHIPS, key = { it.first }) { (key, label) ->
            DoorChip(
                label = label,
                door = BenflixDoor.MUSIC,
                selected = key == chip,
                onClick = { onChip(key) },
            )
        }
    }
}

/** What a Home chip shows: the mixes and playlists from the page, or a grid of albums, artists or new music */
@Composable
fun MusicChipGrid(
    chip: String,
    state: MusicDoorState,
    wide: Boolean,
    onTile: (MusicTile) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (chip) {
        "albums" -> {
            MusicListPage(Destination.MusicList("albums", title = "Albums"), modifier, showTitle = false)
        }

        "artists" -> {
            MusicListPage(Destination.MusicList("artists", title = "Artists"), modifier, showTitle = false)
        }

        "new" -> {
            MusicListPage(Destination.MusicList("new", title = "New to you"), modifier, showTitle = false)
        }

        else -> {
            val tiles =
                remember(chip, state.rows, state.allMixes) {
                    if (chip == "playlists") {
                        state.rows
                            .firstOrNull { it.key == "playlists" }
                            ?.tiles
                            .orEmpty()
                    } else {
                        state.allMixes.ifEmpty {
                            state.rows.filter { it.key == "mixes" || it.key.startsWith("theme-") }.flatMap { it.tiles }
                        }
                    }
                }
            val size = if (wide) 132.dp else 104.dp
            if (tiles.isEmpty()) {
                Text("Nothing here yet.", color = BenflixColors.Soft, fontSize = BenflixType.body, modifier = modifier)
                return
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(size + 14.dp),
                contentPadding = PaddingValues(bottom = 48.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = modifier,
            ) {
                items(tiles, key = { it.key }) { tile ->
                    MusicTileCard(tile = tile, size = size, onClick = { onTile(tile) })
                }
            }
        }
    }
}

/**
 * The 8 quick picks at the top of Music Home (approved r3-quick / g2-home): wide short tiles, 4 across on TV
 * and 2 across on phones, with a small cover on the left and the name beside it. Liked songs is always first.
 */
@Composable
fun QuickPicks(
    tiles: List<MusicTile>,
    across: Int,
    big: Boolean,
    onTile: (MusicTile) -> Unit,
    onFocus: (Int) -> Unit,
    focusFor: (Int) -> Modifier,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier.fillMaxWidth()) {
        tiles.chunked(across).forEachIndexed { r, line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                line.forEachIndexed { c, tile ->
                    val i = r * across + c
                    QuickPick(
                        tile = tile,
                        big = big,
                        onClick = { onTile(tile) },
                        modifier =
                            focusFor(i)
                                .weight(1f)
                                .onFocusChanged { if (it.isFocused) onFocus(i) },
                    )
                }
                repeat(across - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun QuickPick(
    tile: MusicTile,
    big: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val height = if (big) 72.dp else 56.dp
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.PanelHigh,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border = focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.height(height),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
            TileCover(tile, height, Modifier.size(height))
            Text(
                text = tileName(tile),
                color = BenflixColors.Text,
                fontSize = if (big) BenflixType.lead else BenflixType.small,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp).weight(1f),
            )
        }
    }
}

fun tileName(tile: MusicTile): String =
    when (tile) {
        is MusicTile.Mix -> tile.mix.name
        is MusicTile.Item -> tile.item.title.orEmpty()
        is MusicTile.Liked -> "Liked songs"
        MusicTile.Ask -> "Ask for music"
        is MusicTile.SeeAll -> tile.title
        is MusicTile.Browse -> tile.title
        MusicTile.AllSongs -> "All songs"
    }

/** A small square picture for any tile (quick picks, Your library's list) */
@Composable
fun TileCover(
    tile: MusicTile,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val imageUrlService = LocalImageUrlService.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    when (tile) {
        is MusicTile.Item -> {
            val url = remember(tile.item.id, px) { imageUrlService.musicCover(tile.item, px) }
            CoverImage(
                url,
                modifier,
                shape =
                    if (tile.item.type ==
                        BaseItemKind.MUSIC_ARTIST
                    ) {
                        androidx.compose.foundation.shape.CircleShape
                    } else {
                        BenflixShapes.Card
                    },
            )
        }

        is MusicTile.Mix -> {
            val urls = remember(tile.mix.key, px) { tile.mix.coverAlbumIds.map { imageUrlService.albumCover(it, px / 2) } }
            MixCover(tile.mix.name, urls, modifier, size)
        }

        is MusicTile.Browse -> {
            BrowseCover(tile, size, modifier)
        }

        is MusicTile.Liked -> {
            LikedCover(modifier)
        }

        MusicTile.AllSongs -> {
            ActionCover("All songs", BenflixIcon.HEADPHONES, modifier)
        }

        MusicTile.Ask -> {
            ActionCover("Ask for music", BenflixIcon.PLUS, modifier)
        }

        is MusicTile.SeeAll -> {
            ActionCover(tile.title, BenflixIcon.ARROW, modifier)
        }
    }
}

/** A Browse tile (Decades, Genres, Moods, Throwbacks): its playlists' covers under a coloured band with its name */
@Composable
fun BrowseCover(
    tile: MusicTile.Browse,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val imageUrlService = LocalImageUrlService.current
    val px = with(LocalDensity.current) { (size / 2).roundToPx() }
    val urls =
        remember(tile.key, px) {
            tile.coverAlbumIds.mapNotNull { it.toUUIDOrNull() }.map { imageUrlService.albumCover(it, px) }
        }
    MixCover(tile.title, urls, modifier, size)
}

/** Words for a Browse tile under its picture */
fun browseLine(group: String): String =
    when (group) {
        "decade" -> "Every decade you have"
        "genre" -> "Your music by style"
        "mood" -> "For any moment"
        "throwback" -> "Songs from years ago"
        else -> ""
    }

/** A centred line of soft words (empty places) */
@Composable
fun MusicEmptyLine(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = BenflixColors.Soft,
        fontSize = BenflixType.body,
        textAlign = TextAlign.Start,
        modifier = modifier.padding(vertical = 12.dp),
    )
}
