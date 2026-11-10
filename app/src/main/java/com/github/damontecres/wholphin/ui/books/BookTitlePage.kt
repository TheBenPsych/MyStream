package com.github.damontecres.wholphin.ui.books

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.BookPage
import com.github.damontecres.wholphin.services.BookTile
import com.github.damontecres.wholphin.services.BookVolume
import com.github.damontecres.wholphin.services.CoverChoice
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.music.MusicButton
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay

/**
 * A book's own page (approved design): movie-style, the big cover on the right. Manga get a row of volumes
 * with ticks (OK opens the reader; hold OK marks it read, and everything before it if you like). Ebooks get
 * Read and "Send to my phone"; audiobooks show the narrator and time left. A book you have both ways gets
 * Read and Listen buttons and a line saying whether your places match. Then More by the author and More like this.
 */
@Composable
fun BookTitlePage(
    id: String,
    modifier: Modifier = Modifier,
    viewModel: BookTitleViewModel = hiltViewModel(key = "book-$id"),
) {
    // Coming back from the reader or the player: look again, so the place and ticks are fresh
    LifecycleResumeEffect(id) {
        viewModel.load(id)
        onPauseOrDispose { }
    }
    val page by viewModel.page.collectAsState()
    val problem by viewModel.problem.collectAsState()
    val note by viewModel.note.collectAsState()
    val covers by viewModel.covers.collectAsState()
    LaunchedEffect(note) {
        if (note != null) {
            delay(5_000)
            viewModel.note.value = null
        }
    }
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp
    val edge = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge
    val p = page
    if (p == null) {
        Column(Modifier.fillMaxSize().padding(edge), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (problem != null) {
                Text(problem!!, color = BenflixColors.Soft, fontSize = BenflixType.body)
                MusicButton("Try again", BenflixIcon.REFRESH, onClick = viewModel::reload)
            }
        }
        return
    }
    var markVol by remember { mutableStateOf<BookVolume?>(null) }
    markVol?.let { v ->
        MarkDialog(
            v = v,
            onDismiss = { markVol = null },
            onMark = { done, before ->
                markVol = null
                viewModel.mark(v.id, done, before)
            },
        )
    }
    covers?.let { list ->
        CoverDialog(
            page = p,
            choices = list,
            onDismiss = { viewModel.covers.value = null },
            onPick = { c, all -> viewModel.pickCover(p, c, all) },
            onReset = { viewModel.resetCover(p) },
        )
    }
    val mainFocus = remember { FocusRequester() }
    LaunchedEffect(p.tile.id) { mainFocus.tryRequestFocus() }
    val onTile: (BookTile) -> Unit = { viewModel.navigationManager.navigateTo(Destination.BookTitle(it.id)) }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(if (wide) 26.dp else 18.dp),
        contentPadding = PaddingValues(bottom = 48.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "hero") {
            if (wide) {
                Box(Modifier.fillMaxWidth()) {
                    // The cover on the right, fading into the page
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = edge, top = 8.dp)
                            .width(260.dp)
                            .aspectRatio(BOOK_RATIO),
                    ) {
                        BookCoverImage(p.tile.cover, p.tile.name, Modifier.fillMaxSize(), audio = p.tile.isAudio)
                    }
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(Brush.horizontalGradient(listOf(BenflixColors.Tray, BenflixColors.Tray, Color.Transparent))),
                    )
                    HeroText(p, viewModel, note, mainFocus, Modifier.padding(start = edge, end = 340.dp, top = 8.dp))
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    BookCoverImage(
                        p.tile.cover,
                        p.tile.name,
                        Modifier.width(180.dp).aspectRatio(BOOK_RATIO),
                        audio = p.tile.isAudio,
                    )
                    HeroText(p, viewModel, note, mainFocus, Modifier.padding(horizontal = edge, vertical = 12.dp))
                }
            }
        }
        if (p.volumes.isNotEmpty()) {
            item(key = "vols") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RowName(
                        (if (p.volumes.size == 1) "1 volume" else "${p.volumes.size} volumes") +
                            (if (p.totalVols > p.volumes.size) " of ${p.totalVols}" else "") +
                            (if (isTv) " · hold OK to mark as read" else " · press and hold to mark as read"),
                        edge,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = edge),
                    ) {
                        items(p.volumes, key = { it.id }) { v ->
                            BookCard(
                                tile = volumeTile(p, v),
                                width = if (wide) 120.dp else 96.dp,
                                onClick = { viewModel.openVolume(v) },
                                onLongClick = { markVol = v },
                            )
                        }
                    }
                }
            }
        }
        if (p.tile.isAudio && p.chapters.size > 1) {
            item(key = "chapters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = edge)) {
                    RowName("${p.chapters.size} chapters", 0.dp)
                    val shown = p.chapters.take(MAX_CHAPTERS_SHOWN)
                    FlowRowOf(shown.map { c -> "${c.index + 1}. ${c.title}" + (if (c.seconds > 0) " · " + hoursMinutes(c.seconds) else "") }) { i ->
                        viewModel.listen(p, shown[i].index)
                    }
                }
            }
        }
        if (p.moreBy.isNotEmpty()) {
            item(key = "by") { TileRow("More by ${p.tile.author}", p.moreBy, wide, edge, onTile) }
        }
        if (p.moreLike.isNotEmpty()) {
            item(key = "like") { TileRow("More like this", p.moreLike, wide, edge, onTile) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroText(
    p: BookPage,
    viewModel: BookTitleViewModel,
    note: String?,
    mainFocus: FocusRequester,
    modifier: Modifier,
) {
    val t = p.tile
    val isTv = rememberIsTv()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        Text(
            text =
                when (t.kind) {
                    "manga" -> "MANGA"
                    "comic" -> "COMIC"
                    "audiobook" -> "AUDIOBOOK"
                    else -> "BOOK"
                },
            color = BenflixColors.Books,
            fontSize = BenflixType.tag,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = t.name,
            color = BenflixColors.Text,
            fontSize = BenflixType.hero,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        val bits =
            buildList {
                if (t.author.isNotBlank()) add(t.author)
                if (p.narrator.isNotBlank()) add("Read by ${p.narrator}")
                t.year?.let { add("$it") }
                if (t.isAudio && p.seconds > 0) {
                    add(if (p.secondsLeft in 1 until p.seconds) hoursMinutes(p.secondsLeft) + " left" else hoursMinutes(p.seconds))
                }
                if (!t.isAudio && !t.isManga && p.minutes > 0) add("About ${hoursMinutes(p.minutes * 60)} to read")
                if (t.series.isNotBlank() && !t.isManga) add(t.series + (t.seriesIndex?.let { " #" + it.toString().removeSuffix(".0") } ?: ""))
            }
        Text(bits.joinToString("  ·  "), color = BenflixColors.Soft, fontSize = BenflixType.lead, maxLines = 2)
        if (p.scores.isNotEmpty()) {
            Text(
                p.scores.joinToString("   ") { s -> "${s.site} " + "%.1f".format(s.score) + "/${s.outOf}" },
                color = BenflixColors.Books,
                fontSize = BenflixType.small,
                fontWeight = FontWeight.Bold,
            )
        }
        if (p.desc.isNotBlank()) {
            Text(p.desc, color = BenflixColors.Soft, fontSize = BenflixType.body, maxLines = if (isTv) 4 else 6, overflow = TextOverflow.Ellipsis)
        }
        if (p.missing > 0) {
            Text(
                "Tower is missing ${p.missing} " + (if (p.missing == 1) "part" else "parts") + " of this audiobook.",
                color = BenflixColors.Warning,
                fontSize = BenflixType.small,
            )
        }
        if (p.placeLine.isNotBlank()) Text(p.placeLine, color = BenflixColors.Muted, fontSize = BenflixType.small)
        if (!t.readable && !t.isAudio) {
            Text(
                "This kind of file (${p.file.removePrefix(".").uppercase()}) can't be opened in Benflix. EPUB and PDF can.",
                color = BenflixColors.Warning,
                fontSize = BenflixType.small,
            )
        }
        note?.let { Text(it, color = BenflixColors.Books, fontSize = BenflixType.small, fontWeight = FontWeight.Bold) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val main =
                when {
                    t.isManga -> {
                        val nv = p.volumes.firstOrNull { it.id == p.next }
                        if (nv == null) {
                            null
                        } else if (nv.progress > 0f && !nv.done) {
                            "Keep reading ${nv.name}"
                        } else {
                            "Read ${nv.name}"
                        }
                    }

                    t.isAudio -> {
                        if (p.placePos > 0) "Keep listening" else "Listen"
                    }

                    !t.readable -> {
                        null
                    }

                    else -> {
                        if (p.placePct > 0.005f && t.status != "done") "Keep reading ${(p.placePct * 100).toInt()}%" else "Read"
                    }
                }
            if (main != null) {
                MusicButton(
                    label = main,
                    icon = if (t.isAudio) BenflixIcon.HEADPHONES else BenflixIcon.BOOK,
                    primary = true,
                    onClick = { viewModel.start(p) },
                    modifier = Modifier.focusRequester(mainFocus),
                )
            }
            if (p.otherId != null) {
                MusicButton(
                    label = if (p.otherKind == "audiobook") "Listen" else "Read",
                    icon = if (p.otherKind == "audiobook") BenflixIcon.HEADPHONES else BenflixIcon.BOOK,
                    onClick = { viewModel.openOther(p) },
                )
            }
            MusicButton(
                label = if (t.want) "On Want to read" else "Want to read",
                icon = if (t.want) BenflixIcon.CHECK else BenflixIcon.PLUS,
                onClick = { viewModel.toggleWant(p) },
            )
            if (!t.isManga) {
                MusicButton(
                    label = if (t.status == "done") "Finished" else "Mark finished",
                    icon = BenflixIcon.CHECK,
                    onClick = { viewModel.mark(t.id, t.status != "done", false) },
                )
            }
            if (isTv && t.kind == "book" && t.readable) {
                MusicButton("Send to my phone", BenflixIcon.PHONE, onClick = { viewModel.sendToPhone(p) })
            }
            if (p.canPickCover) {
                MusicButton("Change cover", BenflixIcon.IMAGE, onClick = { viewModel.loadCovers(p) })
            }
        }
    }
}

/** A volume as a card: its cover, a tick when read, a bar when part read */
private fun volumeTile(
    p: BookPage,
    v: BookVolume,
) = p.tile.copy(
    id = v.id,
    name = v.name,
    cover = v.cover ?: p.tile.cover,
    status =
        when {
            v.done -> "done"
            v.progress > 0f -> "reading"
            else -> "not"
        },
    progress = v.progress,
    line = if (v.current && !v.done) "Reading now" else "",
    isNew = v.isNew,
    newCount = 0,
    sent = false,
    notHere = false,
)

@Composable
private fun RowName(
    text: String,
    edge: androidx.compose.ui.unit.Dp,
) {
    Text(
        text = text,
        color = BenflixColors.Text,
        fontSize = BenflixType.rowName,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = edge),
    )
}

@Composable
private fun TileRow(
    title: String,
    tiles: List<BookTile>,
    wide: Boolean,
    edge: androidx.compose.ui.unit.Dp,
    onTile: (BookTile) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RowName(title, edge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = edge)) {
            items(tiles, key = { it.id }) { t ->
                BookCard(t, if (wide) 130.dp else 104.dp, onClick = { onTile(t) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowOf(
    labels: List<String>,
    onPick: (Int) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
            BookChip(label, false, onClick = { onPick(i) })
        }
    }
}

/** Hold OK on a volume: read, read with everything before it, or not read */
@Composable
private fun MarkDialog(
    v: BookVolume,
    onDismiss: () -> Unit,
    onMark: (done: Boolean, before: Boolean) -> Unit,
) {
    BasicDialog(onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(max = 520.dp).padding(22.dp)) {
            Text(v.name, fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold, color = BenflixColors.Text)
            MusicButton("Mark as read", BenflixIcon.CHECK, primary = true, onClick = { onMark(true, false) }, modifier = Modifier.fillMaxWidth())
            MusicButton("Mark this and every one before it", BenflixIcon.CHECK, onClick = { onMark(true, true) }, modifier = Modifier.fillMaxWidth())
            if (v.done || v.progress > 0f) {
                MusicButton("Mark as not read", BenflixIcon.CLOSE, onClick = { onMark(false, false) }, modifier = Modifier.fillMaxWidth())
            }
            MusicButton("Cancel", null, onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Ben's cover picker: pick a cover, then "Just this book" or "All volumes in this edition" */
@Composable
private fun CoverDialog(
    page: BookPage,
    choices: List<CoverChoice>,
    onDismiss: () -> Unit,
    onPick: (CoverChoice, Boolean) -> Unit,
    onReset: () -> Unit,
) {
    var picked by remember { mutableStateOf<CoverChoice?>(null) }
    BasicDialog(onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(max = 760.dp).padding(22.dp)) {
            Text("Pick a cover", fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold, color = BenflixColors.Text)
            val chosen = picked
            if (chosen == null) {
                if (choices.isEmpty()) {
                    Text("Looking for covers…", color = BenflixColors.Muted, fontSize = BenflixType.body)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(choices, key = { it.url }) { c ->
                        BookCard(
                            tile = page.tile.copy(id = c.url, name = c.label, cover = c.thumb, line = c.edition, status = "not", isNew = false, newCount = 0),
                            width = 110.dp,
                            onClick = { picked = c },
                        )
                    }
                }
                MusicButton("Use the first cover again", BenflixIcon.REFRESH, onClick = onReset)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    BookCoverImage(chosen.thumb, chosen.label, Modifier.width(110.dp).aspectRatio(BOOK_RATIO))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MusicButton("Just this book", null, primary = true, onClick = { onPick(chosen, false) })
                        if (page.tile.isManga || page.tile.series.isNotBlank()) {
                            MusicButton("All volumes in this edition", null, onClick = { onPick(chosen, true) })
                        }
                        MusicButton("Pick another", null, onClick = { picked = null })
                    }
                }
            }
            MusicButton("Cancel", null, onClick = onDismiss)
        }
    }
}

private const val MAX_CHAPTERS_SHOWN = 60
