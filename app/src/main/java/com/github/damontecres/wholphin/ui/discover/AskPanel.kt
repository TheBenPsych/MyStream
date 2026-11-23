package com.github.damontecres.wholphin.ui.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.detail.discover.AskStepsLine
import com.github.damontecres.wholphin.ui.music.MusicButton
import com.github.damontecres.wholphin.ui.preferences.GlowfinSettingRow
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
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
 * [steps] is the short "what happens next" line for books and songs.
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
    steps: (@Composable () -> Unit)? = null,
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
            steps?.invoke()
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

/**
 * Shows and anime: which seasons to ask for. Glowfin new look (Ben approved 2026-11-22): a panel that slides in
 * from the right, like Filters. All seasons, Every new season from now on (all of them now, and Tower asks for
 * each new one when it comes out), the newest season only, or tick seasons one by one.
 */
@Composable
fun SeasonAskPanel(
    pick: SeasonPick,
    onDismiss: () -> Unit,
    onAsk: (List<Int>) -> Unit,
    line: String? = null,
    /** Asks for these seasons and every season that comes out later */
    onAskEvery: ((List<Int>) -> Unit)? = null,
) {
    val isTv = rememberIsTv()
    val open = remember(pick) { pick.seasons.filter { !it.taken }.map { it.number } }
    var chosen by remember(pick) { mutableStateOf(setOf<Int>()) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val newest = pick.seasons.filter { !it.taken }.maxByOrNull { it.number }
    val first = remember { FocusRequester() }
    LaunchedEffect(shown) { if (shown) first.tryRequestFocus() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            AnimatedVisibility(visible = shown, enter = slideInHorizontally(tween(220)) { it }) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .then(if (isTv) Modifier.width(400.dp) else Modifier.fillMaxWidth(.92f))
                            .background(SEASON_PANEL)
                            .padding(start = 28.dp, end = 22.dp, top = 24.dp, bottom = 12.dp),
                ) {
                    Text(
                        text = pick.item.title.orEmpty(),
                        color = Glowfin.Text,
                        fontFamily = GlowfinDisplayFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = BenflixType.rowName,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = line ?: "Which seasons?",
                        color = Glowfin.Dim,
                        fontSize = BenflixType.small,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                    )
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (open.isNotEmpty()) {
                            item(key = "all") {
                                GlowfinSettingRow(
                                    title = if (open.size > 1) "All seasons (${open.size})" else "Ask for it",
                                    value = null,
                                    explain = if (open.size > 1) "Every season that's out now" else null,
                                    onClick = { onAsk(open) },
                                    modifier = Modifier.focusRequester(first),
                                )
                            }
                            if (onAskEvery != null) {
                                item(key = "every") {
                                    GlowfinSettingRow(
                                        title = "Every new season from now on",
                                        value = null,
                                        explain = "All of them now, and each new season when it comes out",
                                        onClick = { onAskEvery(open) },
                                    )
                                }
                            }
                            if (open.size > 1 && newest != null) {
                                item(key = "newest") {
                                    GlowfinSettingRow(
                                        title = "${newest.name} only",
                                        value = null,
                                        explain = newest.episodes?.takeIf { it > 0 }?.let { "$it episodes" },
                                        onClick = { onAsk(listOf(newest.number)) },
                                    )
                                }
                            }
                        }
                        item(key = "pick-head") {
                            Text(
                                "Or pick seasons",
                                color = Glowfin.Dim,
                                fontSize = BenflixType.small,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                            )
                        }
                        items(pick.seasons, key = { "s-${it.number}" }) { season ->
                            val picked = season.number in chosen
                            GlowfinSettingRow(
                                title = season.name,
                                value =
                                    if (season.taken) {
                                        null
                                    } else if (picked) {
                                        "✓"
                                    } else {
                                        ""
                                    },
                                explain =
                                    when {
                                        season.taken -> "Already asked for or on Glowfin"
                                        season.episodes != null && season.episodes > 0 -> "${season.episodes} episodes"
                                        else -> null
                                    },
                                onClick = { chosen = if (picked) chosen - season.number else chosen + season.number },
                                enabled = !season.taken,
                            )
                        }
                        if (chosen.isNotEmpty()) {
                            item(key = "ask-picked") {
                                GlowfinSettingRow(
                                    title = if (chosen.size == 1) "Ask for 1 season" else "Ask for ${chosen.size} seasons",
                                    value = null,
                                    explain = null,
                                    onClick = { onAsk(chosen.sorted()) },
                                    titleColor = Glowfin.Aqua,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val SEASON_PANEL =
    Brush.horizontalGradient(
        0f to Glowfin.Deep.copy(alpha = .9f),
        .2f to Glowfin.Deep.copy(alpha = .97f),
        1f to Glowfin.Deep,
    )

/** Books: Ebook and audiobook (picked first), Ebook, or Audiobook. Asking also adds it to Want to read. */
@Composable
fun BookAskPanel(
    title: String,
    author: String?,
    cover: String?,
    admin: Boolean,
    onDismiss: () -> Unit,
    onAsk: (List<String>) -> Unit,
) {
    val choices =
        listOf(
            AskChoice("Ebook and audiobook", { onAsk(listOf("ebook", "audiobook")) }, primary = true),
            AskChoice("Ebook", { onAsk(listOf("ebook")) }),
            AskChoice("Audiobook", { onAsk(listOf("audiobook")) }),
        )
    AskForItPanel(
        title = title,
        line = author,
        cover = cover,
        choices = choices,
        onDismiss = onDismiss,
        note = "Also adds it to Want to read",
        steps = { AskStepsLine(isAdmin = admin, last = "Ready to read") },
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
        steps = { AskStepsLine(isAdmin = admin, last = "Ready to play") },
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
