package com.github.damontecres.wholphin.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.music.MusicButton
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.tryRequestFocus

/*
 * Redesign 2, Requests: ONE "Ask for it" panel for every kind (right side on TV, bottom sheet on phones, like
 * every Benflix pop-up). It only asks what that kind needs: shows and anime pick seasons, books pick Ebook,
 * Audiobook or both, songs can be heard for 30 seconds first. Movies don't need a choice, so they're asked for
 * straight away and never open this.
 */

/** One button in the Ask for it panel */
data class AskChoice(
    val label: String,
    val onPick: () -> Unit,
    val primary: Boolean = false,
    val icon: BenflixIcon? = BenflixIcon.PLUS,
)

/**
 * The Ask for it panel. [line] is the small line under the name (year, rating, length or artist).
 * [note] is a short grey line above the choices, like "Also adds it to Want to read".
 * [extra] sits between the note and the choices (the 30 second preview button for songs).
 */
@Composable
fun AskForItPanel(
    title: String,
    line: String?,
    cover: String?,
    choices: List<AskChoice>,
    onDismiss: () -> Unit,
    note: String? = null,
    squareCover: Boolean = false,
    extra: (@Composable () -> Unit)? = null,
) {
    val first = remember { FocusRequester() }
    BasicDialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (cover != null) {
                    Box(
                        Modifier
                            .size(width = 72.dp, height = if (squareCover) 72.dp else 108.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BenflixColors.Raised),
                    ) {
                        AsyncImage(
                            model = cover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = BenflixType.rowName,
                        fontWeight = FontWeight.Bold,
                        color = BenflixColors.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!line.isNullOrBlank()) {
                        Text(line, fontSize = BenflixType.small, color = BenflixColors.Soft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Text(
                text = "Ask for $title",
                fontSize = BenflixType.lead,
                fontWeight = FontWeight.Bold,
                color = BenflixColors.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!note.isNullOrBlank()) Text(note, fontSize = BenflixType.small, color = BenflixColors.Muted)
            extra?.invoke()
            choices.forEachIndexed { i, c ->
                MusicButton(
                    label = c.label,
                    icon = c.icon,
                    primary = c.primary,
                    onClick = c.onPick,
                    modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                )
            }
            MusicButton(label = "Not now", icon = BenflixIcon.CLOSE, onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
    LaunchedEffect(Unit) { if (extra == null) first.tryRequestFocus() }
}

/** Shows and anime: All seasons, the newest season only, or pick seasons one by one */
@Composable
fun SeasonAskPanel(
    pick: SeasonPick,
    onDismiss: () -> Unit,
    onAsk: (List<Int>) -> Unit,
    line: String? = null,
) {
    val open = remember(pick) { pick.seasons.filter { !it.taken }.map { it.number } }
    var picking by remember(pick) { mutableStateOf(false) }
    if (picking || open.isEmpty()) {
        SeasonPickDialog(pick = pick, onDismiss = onDismiss, onAsk = onAsk)
        return
    }
    val newest = pick.seasons.filter { !it.taken }.maxByOrNull { it.number }
    val choices =
        buildList {
            add(AskChoice(if (open.size > 1) "All seasons (${open.size})" else "Ask for it", { onAsk(open) }, primary = true))
            if (open.size > 1 && newest != null) add(AskChoice("${newest.name} only", { onAsk(listOf(newest.number)) }))
            if (open.size > 1) add(AskChoice("Pick seasons…", { picking = true }, icon = null))
        }
    AskForItPanel(
        title = pick.item.title.orEmpty(),
        line = line,
        cover = pick.item.posterUrl,
        choices = choices,
        onDismiss = onDismiss,
    )
}

/** Books: Ebook and audiobook (picked first), Ebook, or Audiobook. Asking also adds it to Want to read. */
@Composable
fun BookAskPanel(
    title: String,
    author: String?,
    cover: String?,
    admin: Boolean,
    onDismiss: () -> Unit,
    onAsk: (List<String>) -> Unit,
    manga: Boolean = false,
) {
    val choices =
        if (manga) {
            listOf(AskChoice(if (admin) "Ask for it" else "Ask Ben", { onAsk(emptyList()) }, primary = true))
        } else {
            listOf(
                AskChoice("Ebook and audiobook", { onAsk(listOf("ebook", "audiobook")) }, primary = true),
                AskChoice("Ebook", { onAsk(listOf("ebook")) }),
                AskChoice("Audiobook", { onAsk(listOf("audiobook")) }),
            )
        }
    AskForItPanel(
        title = title,
        line = author,
        cover = cover,
        choices = choices,
        onDismiss = onDismiss,
        note = if (admin) "Also adds it to Want to read" else "Also adds it to Want to read. Ben says yes first.",
    )
}

/**
 * Songs: hear 30 seconds, then ask for the song or the whole album. Family profiles ask Ben (he says yes
 * before anything downloads).
 */
@Composable
fun SongAskPanel(
    title: String,
    artist: String,
    album: String,
    seconds: Int,
    cover: String?,
    songId: Long,
    admin: Boolean,
    onDismiss: () -> Unit,
    onSong: () -> Unit,
    onAlbum: () -> Unit,
) {
    val len = if (seconds > 0) "%d:%02d".format(seconds / 60, seconds % 60) else null
    AskForItPanel(
        title = title,
        line = listOfNotNull(artist.ifBlank { null }, len, album.ifBlank { null }).joinToString(" · "),
        cover = cover,
        squareCover = true,
        note = if (admin) null else "Ben says yes before it downloads",
        choices =
            if (admin) {
                listOf(AskChoice("Ask for this song", onSong, primary = true), AskChoice("Ask for the whole album", onAlbum))
            } else {
                listOf(AskChoice("Ask Ben for this song", onSong, primary = true), AskChoice("Ask Ben for the whole album", onAlbum))
            },
        onDismiss = onDismiss,
        extra = {
            LocalSongPreview.current?.let { preview ->
                val state by preview.state.collectAsState()
                MusicButton(
                    label = previewLabel(state, songId),
                    icon = if (state.songId == songId && !state.loading) BenflixIcon.STOP else BenflixIcon.PLAY,
                    onClick = { preview.toggle(songId) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 0.dp),
                )
            }
        },
    )
}
