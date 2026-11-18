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
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onLike: () -> Unit,
    onMore: () -> Unit,
    onSong: (Int) -> Unit,
    onAskRest: () -> Unit = {},
    onSongMore: (Int) -> Unit,
    preview: com.github.damontecres.wholphin.services.SongPreview? = null,
    onAskSong: (com.github.damontecres.wholphin.services.MusicSong) -> Unit = {},
    onAlbum: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = state.item ?: return
    val songs = state.songs
    val imageUrlService = LocalImageUrlService.current
    val cover = remember(item.id) { imageUrlService.musicCover(item, 800) }
    // Music redesign 3: a little of the cover's own colour at the top of the page
    val tint = rememberCoverTint(remember(item.id) { imageUrlService.musicCover(item, 96) })
    val missing = state.albumLines.count { it is AlbumLine.Missing }
    val artist = item.data.albumArtist ?: item.data.artists?.firstOrNull()
    val discs = remember(songs) { songs.mapNotNull { it.data.parentIndexNumber }.distinct().size > 1 }
    val info =
        listOfNotNull(
            if (item.data.childCount == 1 || songs.size == 1) "Single" else "Album",
            artist,
            item.data.productionYear?.toString(),
            state.albumTotal?.let { "You have ${songs.size} of $it songs" } ?: songs.takeIf { it.isNotEmpty() }?.let { songCount(it.size) },
            if (songs.isNotEmpty()) totalLength(songs) else null,
        ).joinToString("  ·  ")
    val none = if (songs.isEmpty()) "No songs here yet" else null
    val buttons =
        buildList {
            add(TitleButton("Play", BenflixIcon.PLAY, onPlay, off = none))
            add(TitleButton("Shuffle", BenflixIcon.SHUFFLE, onShuffle, off = none))
            // Ben 2026-11-16: only some songs of the album are here: ask for the rest of it
            if (state.albumAskId != null && state.albumTotal != null) {
                val rest = if (missing > 0) "Ask for the other $missing" else "Ask for the rest"
                add(TitleButton(state.albumAsked ?: rest, BenflixIcon.PLUS, onAskRest, off = state.albumAsked))
            }
            add(TitleButton(if (item.favorite) "Liked" else "Like", BenflixIcon.HEART, onLike))
            add(TitleButton("More", BenflixIcon.MORE, onMore))
        }
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
                        val rowModifier =
                            Modifier
                                .padding(horizontal = scope.side - 10.dp)
                                .then(if (scope.wide) Modifier.widthIn(max = 980.dp) else Modifier.fillMaxWidth())
                        // The whole album in order: songs Benflix doesn't have are greyed, with a preview and Ask.
                        // Play skips them without a sound (they aren't in the list it plays).
                        state.albumLines.forEach { line ->
                            when (line) {
                                is AlbumLine.Have -> {
                                    val song = songs.getOrNull(line.index)
                                    if (song != null) {
                                        SongRow(
                                            song = song,
                                            number = song.data.indexNumber ?: (line.index + 1),
                                            withCover = false,
                                            isCurrent = song.id == currentId,
                                            isPlaying = playing,
                                            liked = song.id in state.liked,
                                            onClick = { onSong(line.index) },
                                            onMore = { onSongMore(line.index) },
                                            modifier = rowModifier,
                                        )
                                    }
                                }

                                is AlbumLine.Missing -> {
                                    MissingSongRow(
                                        song = line.song,
                                        number = line.number,
                                        preview = preview,
                                        onAsk = { onAskSong(line.song) },
                                        modifier = rowModifier,
                                    )
                                }
                            }
                        }
                        var lastDisc: Int? = null
                        if (state.albumLines.isEmpty()) {
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
                                    onClick = { onSong(index) },
                                    onMore = { onSongMore(index) },
                                    modifier =
                                        Modifier
                                            .padding(horizontal = scope.side - 10.dp)
                                            .then(if (scope.wide) Modifier.widthIn(max = 980.dp) else Modifier.fillMaxWidth()),
                                )
                            }
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
                            title = "Similar albums on Glowfin",
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
        picture = TitlePicture.Cover(cover, 1f, tint?.forPageTop() ?: BenflixColors.Music),
        info = info,
        buttons = buttons,
        tabs = tabs,
        openTab = "Songs",
        modifier = modifier,
    )
}
