package com.github.damontecres.wholphin.ui.music

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.detail.title.TitleButton
import com.github.damontecres.wholphin.ui.detail.title.TitlePage
import com.github.damontecres.wholphin.ui.detail.title.TitlePicture
import com.github.damontecres.wholphin.ui.detail.title.TitleTab
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType

/**
 * Benflix Redesign 2: an album on the shared title page frame. The square cover stands where the
 * picture goes. Buttons: Play, Shuffle, Like, More. Tabs: Songs (it opens here), More by this artist,
 * Similar albums. Playlists and mixes keep their own page, since they can be very long.
 */
@Composable
fun AlbumTitlePage(
    state: MusicCollectionState,
    currentId: java.util.UUID?,
    playing: Boolean,
    easy: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onLike: () -> Unit,
    onMore: () -> Unit,
    onSong: (Int) -> Unit,
    onSongMore: (Int) -> Unit,
    onAlbum: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = state.item ?: return
    val songs = state.songs
    val imageUrlService = LocalImageUrlService.current
    val cover = remember(item.id) { imageUrlService.musicCover(item, 800) }
    val artist = item.data.albumArtist ?: item.data.artists?.firstOrNull()
    val discs = remember(songs) { songs.mapNotNull { it.data.parentIndexNumber }.distinct().size > 1 }
    val info =
        listOfNotNull(
            if (item.data.childCount == 1 || songs.size == 1) "Single" else "Album",
            artist,
            item.data.productionYear?.toString(),
            state.albumTotal?.let { "${songs.size} of $it songs" } ?: songs.takeIf { it.isNotEmpty() }?.let { songCount(it.size) },
            if (songs.isNotEmpty()) totalLength(songs) else null,
        ).joinToString("  ·  ")
    val none = if (songs.isEmpty()) "No songs here yet" else null
    val buttons =
        listOf(
            TitleButton("Play", BenflixIcon.PLAY, onPlay, off = none),
            TitleButton("Shuffle", BenflixIcon.SHUFFLE, onShuffle, off = none),
            TitleButton(if (item.favorite) "Liked" else "Like", BenflixIcon.HEART, onLike),
            TitleButton("More", BenflixIcon.MORE, onMore),
        )
    val tabs =
        buildList {
            add(
                TitleTab("Songs") { scope ->
                    Column(Modifier.fillMaxWidth()) {
                        if (songs.isEmpty()) {
                            Text(
                                "No songs here yet.",
                                color = BenflixColors.Soft,
                                fontSize = BenflixType.body,
                                modifier = Modifier.padding(start = scope.side),
                            )
                        }
                        var lastDisc: Int? = null
                        songs.forEachIndexed { index, song ->
                            val disc = song.data.parentIndexNumber
                            if (discs && disc != lastDisc) {
                                lastDisc = disc
                                Text(
                                    text = "Disc ${disc ?: 1}",
                                    color = BenflixColors.Soft,
                                    fontSize = BenflixType.small,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(start = scope.side + 10.dp, top = 10.dp, bottom = 4.dp),
                                )
                            }
                            SongRow(
                                song = song,
                                number = song.data.indexNumber ?: (index + 1),
                                withCover = false,
                                isCurrent = song.id == currentId,
                                isPlaying = playing,
                                liked = song.id in state.liked,
                                big = easy,
                                onClick = { onSong(index) },
                                onMore = { onSongMore(index) },
                                modifier =
                                    Modifier
                                        .padding(horizontal = scope.side - 10.dp)
                                        .then(if (scope.wide) Modifier.widthIn(max = 980.dp) else Modifier.fillMaxWidth()),
                            )
                        }
                    }
                },
            )
            if (state.moreByArtist.isNotEmpty()) {
                add(
                    TitleTab("More by this artist") { scope ->
                        AlbumRow(
                            title = "More by ${artist ?: "this artist"}",
                            albums = state.moreByArtist,
                            side = scope.side,
                            size = if (scope.wide) 120.dp else 108.dp,
                            onClick = onAlbum,
                        )
                    },
                )
            }
            if (state.similar.isNotEmpty()) {
                add(
                    TitleTab("Similar albums") { scope ->
                        AlbumRow(
                            title = "Similar albums on Benflix",
                            albums = state.similar,
                            side = scope.side,
                            size = if (scope.wide) 120.dp else 108.dp,
                            onClick = onAlbum,
                        )
                    },
                )
            }
        }
    TitlePage(
        title = state.title,
        accent = BenflixColors.Music,
        picture = TitlePicture.Cover(cover, 1f, BenflixColors.Music),
        info = info,
        buttons = buttons,
        tabs = tabs,
        openTab = "Songs",
        big = easy,
        modifier = modifier,
    )
}
