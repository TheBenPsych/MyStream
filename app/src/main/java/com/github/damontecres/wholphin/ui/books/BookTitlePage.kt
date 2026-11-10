package com.github.damontecres.wholphin.ui.books

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
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
import com.github.damontecres.wholphin.ui.detail.title.StripPart
import com.github.damontecres.wholphin.ui.detail.title.TitleButton
import com.github.damontecres.wholphin.ui.detail.title.TitleGrid
import com.github.damontecres.wholphin.ui.detail.title.TitleMoreItem
import com.github.damontecres.wholphin.ui.detail.title.TitleMorePanel
import com.github.damontecres.wholphin.ui.detail.title.TitleOverview
import com.github.damontecres.wholphin.ui.detail.title.TitlePage
import com.github.damontecres.wholphin.ui.detail.title.TitlePicture
import com.github.damontecres.wholphin.ui.detail.title.TitleRowName
import com.github.damontecres.wholphin.ui.detail.title.TitleTab
import com.github.damontecres.wholphin.ui.detail.title.TitleTabScope
import com.github.damontecres.wholphin.ui.music.MusicButton
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
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
    val onTile: (BookTile) -> Unit = { viewModel.navigationManager.navigateTo(Destination.BookTitle(it.id)) }
    BookOnTitleFrame(p, viewModel, note, onTile, { markVol = it }, modifier)
}

/**
 * Benflix Redesign 2: the book on the shared title page frame. The cover stands where the picture goes.
 * Buttons: Read / Listen (or Keep reading), the other way you have it, Want to read, In this series, More.
 * Tabs: Overview, In this series (manga volumes, or the other books in the series), More by the author,
 * More like this. Empty tabs are left out.
 */
@Composable
private fun BookOnTitleFrame(
    p: BookPage,
    viewModel: BookTitleViewModel,
    note: String?,
    onTile: (BookTile) -> Unit,
    onMarkVolume: (BookVolume) -> Unit,
    modifier: Modifier,
) {
    val t = p.tile
    val isTv = rememberIsTv()
    var showMore by remember { mutableStateOf(false) }
    val seriesBooks =
        remember(p) {
            if (t.series.isBlank()) {
                emptyList()
            } else {
                (p.moreBy + p.moreLike)
                    .filter {
                        it.series == t.series && it.id != t.id
                    }.distinctBy { it.id }
            }
        }
    val hasSeries = p.volumes.isNotEmpty() || seriesBooks.isNotEmpty()
    val main =
        when {
            t.isManga -> {
                val nv = p.volumes.firstOrNull { it.id == p.next }
                when {
                    nv == null -> null
                    nv.progress > 0f && !nv.done -> "Keep reading ${nv.name}"
                    else -> "Read ${nv.name}"
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
    val buttons =
        buildList {
            add(
                TitleButton(
                    label = main ?: "Read",
                    icon = if (t.isAudio) BenflixIcon.HEADPHONES else BenflixIcon.BOOK,
                    onClick = { viewModel.start(p) },
                    off =
                        when {
                            main != null -> null

                            !t.readable && !t.isAudio -> "This kind of file (${p.file.removePrefix(
                                ".",
                            ).uppercase()}) can't be opened in Benflix"

                            else -> "Nothing to open yet"
                        },
                    note = if (t.isAudio && p.secondsLeft in 1 until p.seconds) hoursMinutes(p.secondsLeft) + " left" else null,
                ),
            )
            if (p.otherId != null) {
                val audio = p.otherKind == "audiobook"
                add(
                    TitleButton(
                        if (audio) "Listen" else "Read",
                        if (audio) BenflixIcon.HEADPHONES else BenflixIcon.BOOK,
                        { viewModel.openOther(p) },
                    ),
                )
            }
            add(
                TitleButton(
                    if (t.want) "On Want to read" else "Want to read",
                    if (t.want) BenflixIcon.CHECK else BenflixIcon.PLUS,
                    { viewModel.toggleWant(p) },
                ),
            )
            add(
                TitleButton(
                    "In this series",
                    BenflixIcon.LIST,
                    {},
                    off = if (hasSeries) null else "This one isn't part of a series",
                    opensTab = if (hasSeries) SERIES_TAB else null,
                ),
            )
            add(TitleButton("More", BenflixIcon.MORE, { showMore = true }))
        }
    val strip =
        buildList {
            p.scores.forEach { s -> add(StripPart(s.site, "%.1f".format(s.score) + "/${s.outOf}")) }
            if (p.narrator.isNotBlank()) add(StripPart("Read by", p.narrator))
            if (!t.isAudio && !t.isManga && p.minutes > 0) add(StripPart("About ${hoursMinutes(p.minutes * 60)} to read"))
        }
    val tabs =
        buildList {
            add(
                TitleTab("Overview") { scope ->
                    TitleOverview(
                        scope = scope,
                        title = t.name,
                        story = p.desc.takeIf { it.isNotBlank() },
                        strip = strip,
                        extra = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (p.missing > 0) {
                                    Text(
                                        "Tower is missing ${p.missing} " + (
                                            if (p.missing ==
                                                1
                                            ) {
                                                "part"
                                            } else {
                                                "parts"
                                            }
                                        ) + " of this audiobook.",
                                        color = BenflixColors.Warning,
                                        fontSize = BenflixType.small,
                                    )
                                }
                                if (p.placeLine.isNotBlank()) Text(p.placeLine, color = BenflixColors.Muted, fontSize = BenflixType.small)
                                note?.let {
                                    Text(
                                        it,
                                        color = BenflixColors.Books,
                                        fontSize = BenflixType.small,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                if (t.isAudio && p.chapters.size > 1) {
                                    Text(
                                        "${p.chapters.size} chapters",
                                        color = BenflixColors.Text,
                                        fontSize = BenflixType.body,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    val shown = p.chapters.take(MAX_CHAPTERS_SHOWN)
                                    FlowRowOf(
                                        shown.map { c ->
                                            "${c.index + 1}. ${c.title}" +
                                                if (c.seconds > 0) " · " + hoursMinutes(c.seconds) else ""
                                        },
                                    ) { i -> viewModel.listen(p, shown[i].index) }
                                }
                            }
                        },
                    )
                },
            )
            if (hasSeries) {
                add(
                    TitleTab(SERIES_TAB) { scope ->
                        if (p.volumes.isNotEmpty()) {
                            Column {
                                TitleRowName(
                                    (if (p.volumes.size == 1) "1 volume" else "${p.volumes.size} volumes") +
                                        (if (p.totalVols > p.volumes.size) " of ${p.totalVols}" else "") +
                                        (if (isTv) " · hold OK to mark as read" else " · press and hold to mark as read"),
                                    scope,
                                )
                                BookTiles(scope, p.volumes.map { volumeTile(p, it) }, onClick = { tile ->
                                    p.volumes.firstOrNull { it.id == tile.id }?.let(viewModel::openVolume)
                                }, onLongClick = { tile -> p.volumes.firstOrNull { it.id == tile.id }?.let(onMarkVolume) })
                            }
                        } else {
                            BookTiles(scope, seriesBooks, onTile)
                        }
                    },
                )
            }
            if (p.moreBy.isNotEmpty()) {
                add(
                    TitleTab(
                        if (t.author.isNotBlank()) "More by ${t.author}" else "More by this author",
                    ) { scope -> BookTiles(scope, p.moreBy, onTile) },
                )
            }
            if (p.moreLike.isNotEmpty()) {
                add(TitleTab("More like this") { scope -> BookTiles(scope, p.moreLike, onTile) })
            }
        }
    val info =
        buildList {
            add(
                when (t.kind) {
                    "manga" -> "Manga"
                    "comic" -> "Comic"
                    "audiobook" -> "Audiobook"
                    else -> "Book"
                },
            )
            if (t.author.isNotBlank()) add(t.author)
            t.year?.let { add("$it") }
            if (t.isAudio && p.seconds > 0) add(hoursMinutes(p.seconds))
            if (t.series.isNotBlank() && !t.isManga) add(t.series + (t.seriesIndex?.let { " #" + it.toString().removeSuffix(".0") } ?: ""))
        }.joinToString("  ·  ")
    TitlePage(
        title = t.name,
        accent = BenflixColors.Books,
        picture = TitlePicture.Cover(t.cover, BOOK_RATIO, BenflixColors.Books),
        info = info,
        buttons = buttons,
        tabs = tabs,
        modifier = modifier,
    )
    if (showMore) {
        TitleMorePanel(
            title = t.name,
            onDismiss = { showMore = false },
            items =
                buildList {
                    if (!t.isManga) {
                        add(
                            TitleMoreItem(if (t.status == "done") "Mark not finished" else "Mark finished", BenflixIcon.CHECK, {
                                viewModel.mark(t.id, t.status != "done", false)
                            }),
                        )
                    }
                    if (isTv && t.kind == "book" &&
                        t.readable
                    ) {
                        add(TitleMoreItem("Send to my phone", BenflixIcon.PHONE, { viewModel.sendToPhone(p) }))
                    }
                    if (p.canPickCover) add(TitleMoreItem("Change cover", BenflixIcon.IMAGE, { viewModel.loadCovers(p) }, youOnly = true))
                },
        )
    }
}

/** Book covers inside a tab: one row on TV, 3 across on phones */
@Composable
private fun BookTiles(
    scope: TitleTabScope,
    tiles: List<BookTile>,
    onClick: (BookTile) -> Unit,
    onLongClick: ((BookTile) -> Unit)? = null,
) {
    if (scope.wide) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = scope.side)) {
            items(tiles, key = { it.id }) { t ->
                BookCard(t, 110.dp, onClick = { onClick(t) }, onLongClick = onLongClick?.let { { it(t) } })
            }
        }
    } else {
        TitleGrid(scope, tiles.size) { i, w ->
            val t = tiles[i]
            BookCard(t, w, onClick = { onClick(t) }, onLongClick = onLongClick?.let { { it(t) } })
        }
    }
}

private const val SERIES_TAB = "In this series"

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
            MusicButton(
                "Mark as read",
                BenflixIcon.CHECK,
                primary = true,
                onClick = { onMark(true, false) },
                modifier = Modifier.fillMaxWidth(),
            )
            MusicButton(
                "Mark this and every one before it",
                BenflixIcon.CHECK,
                onClick = { onMark(true, true) },
                modifier = Modifier.fillMaxWidth(),
            )
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
                            tile =
                                page.tile.copy(
                                    id = c.url,
                                    name = c.label,
                                    cover = c.thumb,
                                    line = c.edition,
                                    status = "not",
                                    isNew = false,
                                    newCount = 0,
                                ),
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
