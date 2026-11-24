package com.github.damontecres.wholphin.ui.books

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BookGroup
import com.github.damontecres.wholphin.services.BookRow
import com.github.damontecres.wholphin.services.BookTile
import com.github.damontecres.wholphin.ui.cards.LocalCardNamesOff
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.EmptyPage
import com.github.damontecres.wholphin.ui.discover.RequestsJump
import com.github.damontecres.wholphin.ui.door.CoverDoorTop
import com.github.damontecres.wholphin.ui.door.CoverTopWords
import com.github.damontecres.wholphin.ui.door.DOOR_HOME
import com.github.damontecres.wholphin.ui.door.DoorFrame
import com.github.damontecres.wholphin.ui.door.DoorFrameState
import com.github.damontecres.wholphin.ui.door.DoorMemory
import com.github.damontecres.wholphin.ui.door.DoorSection
import com.github.damontecres.wholphin.ui.door.coverTopHeight
import com.github.damontecres.wholphin.ui.door.rememberDoorFrameState
import com.github.damontecres.wholphin.ui.main.DoorWipe
import com.github.damontecres.wholphin.ui.music.CoverGlow
import com.github.damontecres.wholphin.ui.music.MusicButton
import com.github.damontecres.wholphin.ui.music.MusicRowName
import com.github.damontecres.wholphin.ui.music.rememberCoverGlow
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.LocalTopBarOverlap
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.ScrollToTopBringIntoViewSpec
import com.github.damontecres.wholphin.ui.util.fitTo
import com.github.damontecres.wholphin.ui.util.scrollAsOneBlock
import com.github.damontecres.wholphin.ui.util.tvRowCardFit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Glowfin Books (Ben's B1 pick, Nov 2026): the same word tabs as Movies and Music along the top: For you, Books,
 * Audiobooks, Series, Want to read. For you: on TV and wide screens a top area about the book you're on ("Keep
 * reading · 64%", its name big, the author and series), its cover fading in on the right; then Keep going (books
 * and audiobooks together), New for you, the top book lists and a few more rows. The other tabs are grids with
 * Not started / In progress / Finished chips and a sort. Phones: the same tabs and rows. Manga is back (Ben
 * 2026-11-21): its own tab between Audiobooks and Series, with Keep reading and New chapters rows over the grid.
 * Kids profiles never get the Manga tab.
 */
@Composable
fun BooksDoorPage(
    preferences: UserPreferences,
    onClearBackdrop: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BooksViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { onClearBackdrop() }
    LifecycleStartEffect(Unit) {
        viewModel.start()
        onStopOrDispose { }
    }
    val state by viewModel.state.collectAsState()
    val place by viewModel.place.collectAsState()
    val group by viewModel.group.collectAsState()
    LaunchedEffect(state.loaded) { if (state.loaded) DoorWipe.ready() }
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp || config.smallestScreenWidthDp >= 600
    val home = state.home
    val edge = side(wide)
    val frame = rememberDoorFrameState(BenflixDoor.BOOKS)
    // The tabs pick the place (opening Books again within 10 minutes starts on the one you were on)
    LaunchedEffect(frame.section) {
        viewModel.place.value = BooksPlace.entries.firstOrNull { placeKey(it) == frame.section } ?: BooksPlace.HOME
    }
    // Picking a tab (even the one you're on) closes an open series
    LaunchedEffect(frame.picks) { if (frame.picks > 0) viewModel.group.value = null }
    DisposableEffect(Unit) { onDispose { DoorMemory.keep(BenflixDoor.BOOKS, DoorMemory.Spot(section = frame.section)) } }
    // Back closes an open series first; the frame then goes back to For you, then leaves
    BackHandler(group != null) { viewModel.group.value = null }

    val overlap = LocalTopBarOverlap.current
    if (!state.loaded) {
        Box(modifier.fillMaxSize())
        return
    }
    if (home == null) {
        // The remote starts on Try again (not up on the top bar)
        val retryFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { retryFocus.tryRequestFocus() }
        EmptyPage(
            icon = BenflixIcon.WARNING,
            title = "Couldn't reach the Books helper on Tower",
            line = "If Books is new, Tower's Glowfin helper needs its setup run once more (the same one-line paste as before).",
            more = plainProblem(state.problem),
            modifier = modifier.padding(top = overlap),
        ) {
            MusicButton(
                "Try again",
                BenflixIcon.REFRESH,
                onClick = viewModel::retry,
                primary = true,
                modifier = Modifier.focusRequester(retryFocus),
            )
        }
        return
    }
    val onTile: (BookTile) -> Unit = { viewModel.open(it) }
    val sections =
        remember(home.counts, viewModel.isKids) {
            BooksPlace.entries.filter { it != BooksPlace.MANGA || !viewModel.isKids }.map { p ->
                DoorSection(
                    key = placeKey(p),
                    label = p.label,
                    icon = placeIcon(p),
                    count = p.kind?.let { home.counts[it] },
                )
            }
        }
    DoorFrame(frame, sections, modifier, ready = state.loaded, tabs = true, tabsInHeader = true) { inner ->
        // TV and wide screens: the tabs sit over the top of the page, so the grids start under them
        val top = if (wide) LocalTopBarOverlap.current else 0.dp
        when {
            home.empty -> {
                EmptyBooks(home.folders, home.canAsk && !viewModel.isKids, viewModel, inner.padding(top = top, start = edge))
            }

            group != null -> {
                GroupPage(group!!, wide, onTile, onBack = { viewModel.group.value = null }, modifier = inner.padding(top = top))
            }

            place == BooksPlace.HOME -> {
                BooksForYou(home.rows, wide, home.canAsk && !viewModel.isKids, viewModel, frame, onTile, inner)
            }

            place == BooksPlace.SERIES -> {
                GroupGrid(state.series, wide, onGroup = { viewModel.group.value = it }, modifier = inner.padding(top = top))
            }

            else -> {
                SectionGrid(viewModel, place, wide, onTile, inner.padding(top = top))
            }
        }
    }
}

/** The tab's key (For you is the frame's own Home) */
private fun placeKey(place: BooksPlace): String = if (place == BooksPlace.HOME) DOOR_HOME else place.name

private fun placeIcon(place: BooksPlace): BenflixIcon =
    when (place) {
        BooksPlace.HOME -> BenflixIcon.HOME
        BooksPlace.BOOKS -> BenflixIcon.BOOK
        BooksPlace.AUDIOBOOKS -> BenflixIcon.HEADPHONES
        BooksPlace.MANGA -> BenflixIcon.BOOK
        BooksPlace.SERIES -> BenflixIcon.LIST
        BooksPlace.WANT -> BenflixIcon.STAR
    }

private fun side(wide: Boolean): Dp = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge

/**
 * For you's rows (B1): Keep going first (books and audiobooks together), then New for you, then the top book
 * lists and the rest in Tower's order. Want to read has its own tab, so it isn't a row here.
 */
fun forYouRows(rows: List<BookRow>): List<BookRow> {
    val kept =
        rows
            .filter { it.key != "want" && it.tiles.isNotEmpty() }
            .map { if (it.key == "deck") it.copy(title = "Keep going") else it }
    return kept.sortedBy {
        when (it.key) {
            "deck" -> 0
            "new" -> 1
            else -> 2
        }
    }
}

/** How wide a card is: TV and tablets fit about 7 across, phones 3 */
private fun cardWidth(wide: Boolean): Dp = if (wide) 140.dp else 108.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BooksForYou(
    rows: List<BookRow>,
    wide: Boolean,
    canAsk: Boolean,
    viewModel: BooksViewModel,
    frame: DoorFrameState,
    onTile: (BookTile) -> Unit,
    modifier: Modifier,
) {
    val edge = side(wide)
    val isTv = rememberIsTv()
    // Ben 2026-11-18 (approved games-books-top-1): 160 tall on TV, so the first row shows whole under the top area
    // Ben 2026-11-20: smaller TVs have less room, so there the books get only as big as the room under the top
    // area allows (the row you're on always shows whole, and the next one peeks in). Big TVs stay at 106 wide.
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val overlap = LocalTopBarOverlap.current
    val w =
        if (isTv) {
            val fit =
                tvRowCardFit(
                    rowsHeight = screenHeight - booksTopHeight(overlap, screenHeight) - 4.dp,
                    rowName = BenflixType.lead,
                    around = 8.dp,
                )
            (106.dp / BOOK_RATIO).fitTo(fit) * BOOK_RATIO
        } else {
            cardWidth(wide)
        }
    val shownRows = remember(rows) { forYouRows(rows) }
    val listState = rememberLazyListState()
    val first = remember { FocusRequester() }
    // The book the remote is on: only the top area reads it, so moving along a row doesn't rebuild the page
    val lit = remember { mutableStateOf<BookTile?>(null) }
    var focusedRow by remember { mutableIntStateOf(0) }
    var topRequest by remember { mutableIntStateOf(0) }
    // True while focus is somewhere in the rows (not the tabs or the menu)
    var rowsFocused by remember { mutableStateOf(false) }
    LaunchedEffect(shownRows.isNotEmpty(), topRequest) {
        if (shownRows.isEmpty()) return@LaunchedEffect
        if (topRequest > 0) listState.scrollToItem(0)
        // TV: Books opens on the first book of Keep going. The cards can take a moment to appear (and the menu
        // may grab focus first), so keep asking for a little while until a card has it.
        if (!isTv) return@LaunchedEffect
        var steady = 0
        repeat(40) {
            delay(60)
            if (rowsFocused) {
                if (++steady >= 4) return@LaunchedEffect
            } else {
                steady = 0
                first.tryRequestFocus()
            }
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 && focusedRow == 0 }.collect { frame.atTop = it }
    }
    frame.toTop = {
        focusedRow = 0
        topRequest++
    }
    Column(modifier.fillMaxSize().onFocusChanged { rowsFocused = it.hasFocus }) {
        if (isTv) {
            BooksTopArea(lit, shownRows.firstOrNull()?.tiles?.firstOrNull(), overlap)
        } else if (wide) {
            // Glowfin new look: tablets and sideways phones have no top either, the rows start right under the
            // header and tabs (which sit over the page there)
            Spacer(Modifier.height(overlap))
        }
        // Ben 2026-11-18: like Movies and Shows on TV, the row you move to jumps up whole (name and all) right
        // under the top area, and rows sit the same distance apart. The rows inside still slide sideways as before.
        val defaultSpec = LocalBringIntoViewSpec.current
        val toTop = remember { ScrollToTopBringIntoViewSpec(0f) }
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides if (isTv) toTop else defaultSpec,
            // Glowfin new look: cover art only in the rows (the top says what the book is)
            LocalCardNamesOff provides true,
        ) {
            LazyColumn(
                state = listState,
                verticalArrangement =
                    Arrangement.spacedBy(
                        if (isTv) {
                            14.dp
                        } else if (wide) {
                            26.dp
                        } else {
                            20.dp
                        },
                    ),
                contentPadding = PaddingValues(top = if (wide) 4.dp else 10.dp, bottom = 48.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                itemsIndexed(shownRows, key = { _, row -> row.key }) { rowIndex, row ->
                    CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                        // TV: the row name always comes into view with the books
                        Column(Modifier.scrollAsOneBlock()) {
                            MusicRowName(row.title, Modifier.padding(start = edge, bottom = 8.dp))
                            val rowState = rememberLazyListState()
                            if (rowIndex == 0) {
                                LaunchedEffect(topRequest) { if (topRequest > 0) rowState.scrollToItem(0) }
                            }
                            LazyRow(
                                state = rowState,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(horizontal = edge),
                            ) {
                                itemsIndexed(row.tiles, key = { _, it -> row.key + it.id }) { i, tile ->
                                    BookCard(
                                        tile,
                                        w,
                                        onClick = { onTile(tile) },
                                        onFocus = {
                                            lit.value = tile
                                            focusedRow = rowIndex
                                        },
                                        modifier = if (rowIndex == 0 && i == 0) Modifier.focusRequester(first) else Modifier,
                                    )
                                }
                            }
                        }
                    }
                }
                if (canAsk) {
                    item(key = "ask") {
                        MusicButton(
                            label = "Ask for a book",
                            icon = BenflixIcon.PLUS,
                            onClick = {
                                RequestsJump.books = true
                                viewModel.navigationManager.navigateTo(Destination.Requests)
                            },
                            modifier = Modifier.padding(start = edge),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Glowfin new look (part 3, Ben approved 2026-11-22) on TV and wide screens: the tall cover of the book you're on
 * stands at the top right with its own colours glowing behind the page, and the words on the left say what it is
 * (name, author, facts, how far you are in the tag), like Home and Movies. It waits half a second after you stop
 * moving, so passing along a row stays smooth on older TVs. Phones have no top: the rows start under the tabs.
 */
@Composable
private fun BooksTopArea(
    lit: State<BookTile?>,
    first: BookTile?,
    overlap: Dp,
) {
    var shown by remember { mutableStateOf<BookTile?>(null) }
    LaunchedEffect(Unit) {
        snapshotFlow { lit.value }.collectLatest { tile ->
            if (tile == null) return@collectLatest
            if (shown != null) delay(TOP_AREA_WAIT_MS)
            shown = tile
        }
    }
    val tile = shown ?: first ?: return
    val glow = rememberCoverGlow(tile.cover, BenflixColors.Books)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val words = remember(tile) { bookTopWords(tile) }
    CoverDoorTop(
        shown = tile,
        words = words,
        glow = glow,
        height = booksTopHeight(overlap, screenHeight),
        overlap = overlap,
        coverRatio = BOOK_RATIO,
        edge = side(true),
        showCover = false,
    ) { t, m ->
        Box(m) {
            BookCoverImage(t.cover, t.name, Modifier.fillMaxSize(), audio = t.isAudio)
            if (t.status == "reading" && t.progress > 0.005f) {
                ProgressStrip(t.progress, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

private const val TOP_AREA_WAIT_MS = 500L

/** How tall the Books top area is on TV and wide screens, counting the header it sits under */
private fun booksTopHeight(
    overlap: Dp,
    screenHeight: Dp,
): Dp = coverTopHeight(screenHeight, overlap)

/** Name, author, "Book · 2019 · Book 2 of The Drift · ebook and audiobook", and how far you are in the tag */
private fun bookTopWords(t: BookTile): CoverTopWords =
    CoverTopWords(
        title = t.name,
        by = t.author.takeIf { it.isNotBlank() },
        facts =
            listOfNotNull(
                when {
                    t.isManga -> "Manga"
                    t.isAudio -> "Audiobook"
                    else -> "Book"
                },
                t.year?.takeIf { it > 0 }?.toString(),
                t.series.takeIf { it.isNotBlank() }?.let { s ->
                    t.seriesIndex?.let { "Book " + it.toString().removeSuffix(".0") + " of " + s } ?: s
                },
                t.genres.firstOrNull(),
                if (t.linked != null) "ebook and audiobook" else null,
            ).joinToString(" · "),
        tag = bookTag(t),
    )

/** "64% read" (or listened), "Finished", "+2 new", "New for you" */
private fun bookTag(t: BookTile): String? {
    val pct = (t.progress * 100).toInt()
    return when {
        t.notHere -> "Not on Glowfin yet"
        t.newCount > 0 -> "+${t.newCount} new"
        t.status == "reading" && t.isManga -> listOfNotNull(t.line.takeIf { it.isNotBlank() }, "Keep reading").joinToString(" · ")
        t.status == "reading" && pct > 0 -> if (t.isAudio) "$pct% listened" else "$pct% read"
        t.status == "reading" -> if (t.isAudio) "Keep listening" else "Keep reading"
        t.status == "done" -> "Finished"
        t.isNew -> "New for you"
        else -> null
    }
}

@Composable
private fun SectionGrid(
    viewModel: BooksViewModel,
    place: BooksPlace,
    wide: Boolean,
    onTile: (BookTile) -> Unit,
    modifier: Modifier,
) {
    val state by viewModel.state.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val lib = state.library
    val tiles = remember(lib, place, filter, sort) { lib?.let { viewModel.sectionTiles(it, place, filter, sort) }.orEmpty() }
    val edge = side(wide)
    val w = cardWidth(wide)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(w),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(start = edge, end = edge, top = 12.dp, bottom = 48.dp),
        modifier = modifier,
    ) {
        item(key = "chips", span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(BookFilter.entries, key = { it.name }) { f ->
                    BookChip(f.label, f == filter, onClick = { viewModel.filter.value = f })
                }
                item(key = "sort") {
                    BookChip("Sort: ${sort.label}", false, onClick = {
                        viewModel.sort.value = BookSort.entries[(sort.ordinal + 1) % BookSort.entries.size]
                    })
                }
            }
        }
        // Manga: Keep reading and New chapters sit above the grid of every series
        if (place == BooksPlace.MANGA && filter == BookFilter.ALL && lib != null) {
            val keep = tiles.filter { it.status == "reading" || (it.status != "done" && it.lastAt > 0) }.sortedByDescending { it.lastAt }
            val fresh = tiles.filter { it.newCount > 0 }.sortedByDescending { it.added }
            listOf("Keep reading" to keep, "New chapters" to fresh).filter { it.second.isNotEmpty() }.forEach { (name, row) ->
                item(key = "row-$name", span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        MusicRowName(name, Modifier.padding(bottom = 8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(row.take(30), key = { name + it.id }) { tile ->
                                BookCard(tile, w, onClick = { onTile(tile) })
                            }
                        }
                    }
                }
            }
            if (keep.isNotEmpty() || fresh.isNotEmpty()) {
                item(key = "all-name", span = { GridItemSpan(maxLineSpan) }) { MusicRowName("All manga") }
            }
        }
        if (lib == null) {
            item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                Text("Loading…", color = BenflixColors.Muted, fontSize = BenflixType.body)
            }
        } else if (tiles.isEmpty()) {
            item(key = "none", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text =
                        if (place == BooksPlace.MANGA && filter == BookFilter.ALL) {
                            "No manga on Glowfin yet. Manga that Harbor downloads shows up here within 10 minutes."
                        } else if (place == BooksPlace.WANT && filter == BookFilter.ALL) {
                            "Nothing in Want to read yet. Press Want to read on any book's page."
                        } else {
                            "Nothing here with this chip."
                        },
                    color = BenflixColors.Muted,
                    fontSize = BenflixType.body,
                )
            }
        }
        items(tiles, key = { it.id }) { tile ->
            BookCard(tile, w, onClick = { onTile(tile) })
        }
    }
}

/** Series: one card each (the first book's cover), opening a grid of its books */
@Composable
private fun GroupGrid(
    groups: List<BookGroup>?,
    wide: Boolean,
    onGroup: (BookGroup) -> Unit,
    modifier: Modifier,
) {
    val edge = side(wide)
    val w = cardWidth(wide)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(w),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(start = edge, end = edge, top = 12.dp, bottom = 48.dp),
        modifier = modifier,
    ) {
        when {
            groups == null -> {
                item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                    Text("Loading…", color = BenflixColors.Muted, fontSize = BenflixType.body)
                }
            }

            groups.isEmpty() -> {
                item(key = "none", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Books in a series show up here.",
                        color = BenflixColors.Muted,
                        fontSize = BenflixType.body,
                    )
                }
            }

            else -> {
                items(groups, key = { "series" + it.name + it.kind }) { g ->
                    BookCard(
                        tile =
                            BookTile(
                                id = g.name,
                                kind = g.kind,
                                name = g.name,
                                author = "",
                                series = "",
                                seriesIndex = null,
                                year = null,
                                cover = g.cover,
                                added = 0,
                                status = "not",
                                progress = 0f,
                                line = g.line,
                                isNew = false,
                                newCount = 0,
                                want = false,
                                linked = null,
                                readable = true,
                                genres = emptyList(),
                                lastAt = 0,
                            ),
                        width = w,
                        onClick = { onGroup(g) },
                    )
                }
            }
        }
    }
}

/** One series: its books in order */
@Composable
private fun GroupPage(
    group: BookGroup,
    wide: Boolean,
    onTile: (BookTile) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val edge = side(wide)
    val w = cardWidth(wide)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(w),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(start = edge, end = edge, top = 12.dp, bottom = 48.dp),
        modifier = modifier,
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MusicButton(label = null, icon = BenflixIcon.BACK_ARROW, onClick = onBack)
                Column {
                    Text(
                        text = group.name,
                        color = BenflixColors.Text,
                        fontFamily = GlowfinDisplayFont,
                        fontSize = BenflixType.pageTitle,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (group.line.isNotBlank()) Text(group.line, color = BenflixColors.Muted, fontSize = BenflixType.small)
                }
            }
        }
        items(group.titles, key = { it.id }) { tile ->
            BookCard(tile, w, onClick = { onTile(tile) })
        }
    }
}

/** No books on Tower yet: where to put them, and Ask for a book */
@Composable
private fun EmptyBooks(
    folders: List<String>,
    canAsk: Boolean,
    viewModel: BooksViewModel,
    modifier: Modifier,
) {
    EmptyPage(
        icon = BenflixIcon.BOOK,
        title = "No books on Glowfin yet",
        line =
            if (folders.isEmpty()) {
                "Put books or audiobooks in Tower's Media folder (Books, Audiobooks), and they show up here within 10 minutes."
            } else {
                "Put them in these folders on Tower, and they show up here within 10 minutes: " + folders.joinToString(", ")
            },
        modifier = modifier,
        buttons =
            if (canAsk) {
                {
                    MusicButton(
                        label = "Ask for a book",
                        icon = BenflixIcon.PLUS,
                        primary = true,
                        onClick = {
                            RequestsJump.books = true
                            viewModel.navigationManager.navigateTo(Destination.Requests)
                        },
                    )
                }
            } else {
                null
            },
    )
}

/** Ground rule: plain words. Programmer messages (like "Value <!doctype ... JSONObject") become one plain line. */
private fun plainProblem(problem: String?): String {
    val p = problem?.trim().orEmpty()
    val techy = Regex("""java\.|kotlin\.|exception|jsonobject|<!doctype|<html|stacktrace|\bnull\b""", RegexOption.IGNORE_CASE)
    return when {
        p.isEmpty() -> ""
        techy.containsMatchIn(p) -> "Tower answered, but not with Books (the helper may be old or not running)."
        else -> p
    }
}
