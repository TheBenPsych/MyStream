package com.github.damontecres.wholphin.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.services.MusicSong
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.discover.SongAskPanel
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.tryRequestFocus

/*
 * Ben 2026-11-15: from Now playing, a few songs like the one playing that you can ask for. Behind the
 * "Similar songs" button (TV, phone and browser), so the player itself stays the same. Songs already in
 * your music are left out by Tower. No 30 second preview here, because it would play over the song.
 */

/** What the Similar songs panel shows */
sealed interface SimilarState {
    data object Loading : SimilarState

    data class Songs(
        val playing: String,
        val songs: List<MusicSong>,
    ) : SimilarState

    data class Problem(
        val words: String,
    ) : SimilarState
}

/** The list of similar songs; OK on one opens the usual Ask for it panel (this song, or the whole album) */
@Composable
fun SimilarSongsPanel(
    state: SimilarState,
    admin: Boolean,
    picked: MusicSong?,
    onPick: (MusicSong?) -> Unit,
    onAsk: (kind: String, id: Long, name: String) -> Unit,
    onDismiss: () -> Unit,
    note: String?,
) {
    val preview = com.github.damontecres.wholphin.ui.discover.LocalSongPreview.current
    if (picked != null) {
        SongAskPanel(
            title = picked.title,
            artist = picked.artist,
            album = picked.album,
            seconds = picked.seconds,
            cover = picked.cover,
            songId = picked.id,
            admin = admin,
            onDismiss = {
                preview?.stop()
                onPick(null)
            },
            onSong = { onAsk("song", picked.id, picked.title) },
            onAlbum = { onAsk("album", picked.albumId, picked.album) },
        )
        return
    }
    val first = remember { FocusRequester() }
    BasicDialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier =
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
        ) {
            Text("Songs like this", fontSize = BenflixType.lead, fontWeight = FontWeight.Bold, color = BenflixColors.Text)
            when (state) {
                SimilarState.Loading -> {
                    Text("Looking for songs like this one…", fontSize = BenflixType.body, color = BenflixColors.Soft)
                }

                is SimilarState.Problem -> {
                    Text(state.words, fontSize = BenflixType.body, color = BenflixColors.Soft)
                }

                is SimilarState.Songs -> {
                    Text(
                        text =
                            "Like ${state.playing}, and not on Benflix yet. " +
                                "The round button plays 30 seconds. Pick a song to ask for it.",
                        fontSize = BenflixType.small,
                        color = BenflixColors.Muted,
                    )
                    if (!note.isNullOrBlank()) Text(note, fontSize = BenflixType.small, color = BenflixColors.Music)
                    if (state.songs.isEmpty()) {
                        Text("Nothing new like this one right now.", fontSize = BenflixType.body, color = BenflixColors.Soft)
                    }
                    state.songs.forEachIndexed { i, song ->
                        SimilarLine(
                            song = song,
                            onClick = { if (song.asked == null && !song.have) onPick(song) },
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
            }
            MusicButton(
                label = "Close",
                icon = BenflixIcon.CLOSE,
                onClick = onDismiss,
                modifier =
                    Modifier.fillMaxWidth().then(
                        if (state !is SimilarState.Songs ||
                            state.songs.isEmpty()
                        ) {
                            Modifier.focusRequester(first)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
    LaunchedEffect(state) { first.tryRequestFocus() }
}

@Composable
private fun SimilarLine(
    song: MusicSong,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Ben 2026-11-16: a round play button beside each song plays 30 seconds of it (Right on the remote reaches it)
    val preview = com.github.damontecres.wholphin.ui.discover.LocalSongPreview.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        SimilarSurface(song, onClick, modifier.weight(1f))
        if (preview != null) {
            val state by preview.state.collectAsState()
            com.github.damontecres.wholphin.ui.discover.SongPreviewButton(
                state = state,
                songId = song.id,
                onClick = { preview.toggle(song.id) },
            )
        }
    }
}

@Composable
private fun SimilarSurface(
    song: MusicSong,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.Panel,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusFill,
                focusedContentColor = BenflixColors.OnFocusFill,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp),
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(BenflixColors.Raised),
            ) {
                AsyncImage(
                    model = song.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(song.title, fontSize = BenflixType.body, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = song.asked ?: song.artist,
                    fontSize = BenflixType.small,
                    color =
                        androidx.tv.material3.LocalContentColor.current
                            .copy(alpha = .75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
