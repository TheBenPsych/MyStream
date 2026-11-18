package com.github.damontecres.wholphin.ui.music

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.nav.focusRing
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import org.jellyfin.sdk.model.api.BaseItemKind

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
