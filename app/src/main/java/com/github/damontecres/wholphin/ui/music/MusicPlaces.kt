package com.github.damontecres.wholphin.ui.music

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.door.DOOR_HOME
import com.github.damontecres.wholphin.ui.door.DoorFrameState
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixType
import dagger.hilt.android.lifecycle.HiltViewModel
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import javax.inject.Inject

const val MUSIC_ASK = "ask"

/** Glowfin (M1): the Music tabs after For you */
const val MUSIC_ALBUMS = "albums"
const val MUSIC_ARTISTS = "artists"
const val MUSIC_PLAYLISTS = "playlists"
const val MUSIC_SONGS = "songs"

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

    /**
     * Glowfin (M1): Ask for music opens over the Music page instead of being a place of its own ("ask", or
     * "import" for Import a playlist). Back closes it.
     */
    var ask by mutableStateOf<String?>(null)

    fun openAsk(import: Boolean = false) {
        if (import) MusicAskNav.importJump = true
        ask = if (import) "import" else MUSIC_ASK
    }
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
            if (key == MUSIC_ASK) MusicPlaceNav.openAsk()
            val frame = MusicPlaceNav.frame
            if (key == MUSIC_ASK && onDoor) return
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
        "lofi" -> "Hip hop, house, jazz and more"
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
