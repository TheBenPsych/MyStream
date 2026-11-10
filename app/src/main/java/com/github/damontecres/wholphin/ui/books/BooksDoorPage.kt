package com.github.damontecres.wholphin.ui.books

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
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
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.discover.RequestsJump
import com.github.damontecres.wholphin.ui.door.DOOR_HOME
import com.github.damontecres.wholphin.ui.door.DoorFrame
import com.github.damontecres.wholphin.ui.door.DoorFrameState
import com.github.damontecres.wholphin.ui.door.DoorMemory
import com.github.damontecres.wholphin.ui.door.DoorSection
import com.github.damontecres.wholphin.ui.door.rememberDoorFrameState
import com.github.damontecres.wholphin.ui.main.DoorWipe
import com.github.damontecres.wholphin.ui.music.MusicButton
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay

/**
 * Benflix Books: inside the lime Books door (approved design). Redesign 2 (section 6): the side menu moved
 * into the shared [DoorFrame] rail every door has (Home, Manga, Comics, Books, Audiobooks, Series, Authors,
 * Want to read, Finished; Manga and Comics only once there are some). Phones: the same places as chips. Home has On deck, New for you,
 * Want to read and Recently added; each section page is a grid with Not started / In progress / Finished.
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
    val easy = preferences.appPreferences.benflixPreferences.easyRead
    val home = state.home
    val places = remember(home?.counts) { viewModel.places(home?.counts.orEmpty()) }
    val edge = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge
    val frame = rememberDoorFrameState(BenflixDoor.BOOKS)
    // The rail picks the place (opening the door again within 10 minutes starts on the one you were on)
    LaunchedEffect(frame.section) {
        viewModel.place.value = BooksPlace.entries.firstOrNull { placeKey(it) == frame.section } ?: BooksPlace.HOME
    }
    // Picking a place (even the one you're on) closes an open series or author
    LaunchedEffect(frame.picks) { if (frame.picks > 0) viewModel.group.value = null }
    DisposableEffect(Unit) { onDispose { DoorMemory.keep(BenflixDoor.BOOKS, DoorMemory.Spot(section = frame.section)) } }
    // Back closes an open series or author first; the frame then goes back to Books Home, then leaves
    BackHandler(group != null) { viewModel.group.value = null }

    if (!state.loaded) {
        Box(modifier.fillMaxSize())
        return
    }
    if (home == null) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxSize().padding(horizontal = edge, vertical = 32.dp),
        ) {
            Title("Couldn't reach the Books helper on Tower")
            Text(
                text = state.problem ?: "",
                color = BenflixColors.Muted,
                fontSize = BenflixType.body,
            )
            Text(
                text = "If Books is new, Tower's Benflix helper needs its setup run once more (the same one-line paste as before).",
                color = BenflixColors.Soft,
                fontSize = BenflixType.body,
            )
            MusicButton("Try again", BenflixIcon.REFRESH, onClick = viewModel::retry, primary = true)
        }
        return
    }
    val onTile: (BookTile) -> Unit = { viewModel.open(it) }
    val content: @Composable (Modifier) -> Unit = { m ->
        when {
            home.empty -> {
                EmptyBooks(home.folders, home.canAsk && !viewModel.isKids, viewModel, m)
            }

            group != null -> {
                GroupPage(group!!, wide, easy, onTile, onBack = { viewModel.group.value = null }, modifier = m)
            }

            place == BooksPlace.HOME -> {
                BooksHomeRows(home.rows, wide, easy, home.canAsk && !viewModel.isKids, viewModel, frame, onTile, m)
            }

            place == BooksPlace.SERIES -> {
                GroupGrid(
                    state.series,
                    "series",
                    wide,
                    easy,
                    onGroup = { viewModel.group.value = it },
                    modifier = m,
                )
            }

            place == BooksPlace.AUTHORS -> {
                GroupGrid(
                    state.authors,
                    "authors",
                    wide,
                    easy,
                    onGroup = { viewModel.group.value = it },
                    modifier = m,
                )
            }

            else -> {
                SectionGrid(viewModel, place, wide, easy, onTile, m)
            }
        }
    }
    val sections =
        remember(places, home.counts) {
            places.map { p ->
                DoorSection(
                    key = placeKey(p),
                    label = p.label,
                    icon = placeIcon(p),
                    count = p.kind?.let { home.counts[it] },
                )
            }
        }
    DoorFrame(frame, sections, modifier, ready = state.loaded) { inner -> content(inner) }
}

/** The rail's key for a place (Home is the frame's own Home) */
private fun placeKey(place: BooksPlace): String = if (place == BooksPlace.HOME) DOOR_HOME else place.name

private fun placeIcon(place: BooksPlace): BenflixIcon =
    when (place) {
        BooksPlace.HOME -> BenflixIcon.HOME
        BooksPlace.MANGA, BooksPlace.COMICS, BooksPlace.BOOKS -> BenflixIcon.BOOK
        BooksPlace.AUDIOBOOKS -> BenflixIcon.HEADPHONES
        BooksPlace.SERIES -> BenflixIcon.LIST
        BooksPlace.AUTHORS -> BenflixIcon.PERSON
        BooksPlace.WANT -> BenflixIcon.STAR
        BooksPlace.FINISHED -> BenflixIcon.CHECK
    }

@Composable
private fun Title(text: String) {
    Text(
        text = text,
        color = BenflixColors.Text,
        fontSize = BenflixType.pageTitle,
        fontWeight = FontWeight.ExtraBold,
    )
}

/** How wide a card is: TV and tablets fit about 7 across, phones 3 */
private fun cardWidth(
    wide: Boolean,
    easy: Boolean,
): Dp =
    when {
        wide && easy -> 170.dp
        wide -> 140.dp
        easy -> 130.dp
        else -> 108.dp
    }

@Composable
private fun BooksHomeRows(
    rows: List<BookRow>,
    wide: Boolean,
    easy: Boolean,
    canAsk: Boolean,
    viewModel: BooksViewModel,
    frame: DoorFrameState,
    onTile: (BookTile) -> Unit,
    modifier: Modifier,
) {
    val edge = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge
    val w = cardWidth(wide, easy)
    val isTv = rememberIsTv()
    // Ben (dr-p-empty): a row with nothing in it is left out
    val shownRows = remember(rows) { rows.filter { it.tiles.isNotEmpty() } }
    val listState = rememberLazyListState()
    val first = remember { FocusRequester() }
    // Redesign 2: the top area describes the book you're on (TV and wide screens)
    var focused by remember { mutableStateOf<BookTile?>(null) }
    var focusedRow by remember { mutableIntStateOf(0) }
    var topRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(shownRows.isNotEmpty(), topRequest) {
        if (shownRows.isEmpty()) return@LaunchedEffect
        if (topRequest > 0) listState.scrollToItem(0)
        delay(120)
        // TV: the door opens on the first card of the top row, with the menu tucked away
        if (isTv) first.tryRequestFocus()
    }
    LaunchedEffect(Unit) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 && focusedRow == 0 }.collect { frame.atTop = it }
    }
    frame.toTop = {
        focusedRow = 0
        topRequest++
    }
    Column(modifier) {
        if (wide && !easy) {
            BooksTopArea(focused ?: shownRows.firstOrNull()?.tiles?.firstOrNull(), edge)
        }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(if (wide) BenflixSpace.tvRowGap else BenflixSpace.phoneRowGap),
            contentPadding = PaddingValues(top = if (wide) 12.dp else 4.dp, bottom = 48.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            itemsIndexed(shownRows, key = { _, row -> row.key }) { rowIndex, row ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = row.title,
                        color = BenflixColors.Text,
                        fontSize = BenflixType.rowName,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = edge),
                    )
                    val rowState = rememberLazyListState()
                    if (rowIndex == 0) {
                        LaunchedEffect(topRequest) { if (topRequest > 0) rowState.scrollToItem(0) }
                    }
                    LazyRow(
                        state = rowState,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(start = edge, end = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge),
                    ) {
                        itemsIndexed(row.tiles, key = { _, it -> row.key + it.id }) { i, tile ->
                            BookCard(
                                tile,
                                w,
                                onClick = { onTile(tile) },
                                onFocus = {
                                    focused = tile
                                    focusedRow = rowIndex
                                },
                                modifier = if (rowIndex == 0 && i == 0) Modifier.focusRequester(first) else Modifier,
                            )
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

/**
 * Redesign 2 (section 6): the Books top area, like every door's: the book you're on, its cover on the
 * right fading into the dark, and its name, author and where you are in it on the left.
 */
@Composable
private fun BooksTopArea(
    tile: BookTile?,
    edge: Dp,
) {
    val config = LocalConfiguration.current
    val height = (config.screenHeightDp * 0.30f).dp.coerceIn(150.dp, 260.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (tile != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight()
                    .aspectRatio(1.6f),
            ) {
                BookCoverImage(tile.cover, tile.name, Modifier.fillMaxSize(), audio = tile.isAudio)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to BenflixColors.Tray,
                                0.55f to BenflixColors.Tray.copy(alpha = .55f),
                                1f to BenflixColors.Tray.copy(alpha = .1f),
                            ),
                        ),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to BenflixColors.Tray)),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = edge, end = edge)
                        .widthIn(max = 600.dp),
            ) {
                Text(
                    text = kindName(tile.kind).uppercase(),
                    color = BenflixColors.Books,
                    fontSize = BenflixType.tag,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = tile.name,
                    color = BenflixColors.Text,
                    fontSize = BenflixType.pageTitle,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val line = listOf(tile.author, tile.series, tile.line).filter { it.isNotBlank() }.distinct().joinToString(" · ")
                if (line.isNotEmpty()) {
                    Text(
                        text = line,
                        color = BenflixColors.Soft,
                        fontSize = BenflixType.body,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (tile.genres.isNotEmpty()) {
                    Text(
                        text = tile.genres.take(3).joinToString(" · "),
                        color = BenflixColors.Muted,
                        fontSize = BenflixType.small,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun kindName(kind: String): String =
    when (kind) {
        "manga" -> "Manga"
        "comic" -> "Comic"
        "audiobook" -> "Audiobook"
        else -> "Book"
    }

@Composable
private fun SectionGrid(
    viewModel: BooksViewModel,
    place: BooksPlace,
    wide: Boolean,
    easy: Boolean,
    onTile: (BookTile) -> Unit,
    modifier: Modifier,
) {
    val state by viewModel.state.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val lib = state.library
    val tiles = remember(lib, place, filter, sort) { lib?.let { viewModel.sectionTiles(it, place, filter, sort) }.orEmpty() }
    val edge = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge
    val w = cardWidth(wide, easy)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(w),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding =
            PaddingValues(
                start = edge,
                end = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge,
                top = 12.dp,
                bottom = 48.dp,
            ),
        modifier = modifier,
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (wide) Title(place.label)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (place != BooksPlace.FINISHED) {
                        items(BookFilter.entries, key = { it.name }) { f ->
                            BookChip(f.label, f == filter, onClick = { viewModel.filter.value = f })
                        }
                    }
                    item(key = "sort") {
                        BookChip("Sort: ${sort.label}", false, onClick = {
                            viewModel.sort.value = BookSort.entries[(sort.ordinal + 1) % BookSort.entries.size]
                        })
                    }
                }
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
                        when (place) {
                            BooksPlace.WANT -> "Nothing in Want to read yet. Press Want to read on any book's page."
                            BooksPlace.FINISHED -> "Books you finish show up here."
                            else -> "Nothing here with this chip."
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

/** Series or Authors: one card each (the first book's cover), opening a grid of their books */
@Composable
private fun GroupGrid(
    groups: List<BookGroup>?,
    what: String,
    wide: Boolean,
    easy: Boolean,
    onGroup: (BookGroup) -> Unit,
    modifier: Modifier,
) {
    val edge = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge
    val w = cardWidth(wide, easy)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(w),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding =
            PaddingValues(
                start = edge,
                end = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge,
                top = 12.dp,
                bottom = 48.dp,
            ),
        modifier = modifier,
    ) {
        if (wide) {
            item(key = "title", span = { GridItemSpan(maxLineSpan) }) { Title(if (what == "series") "Series" else "Authors") }
        }
        when {
            groups == null -> {
                item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                    Text("Loading…", color = BenflixColors.Muted, fontSize = BenflixType.body)
                }
            }

            groups.isEmpty() -> {
                item(key = "none", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = if (what == "series") "Books in a series show up here." else "No authors yet.",
                        color = BenflixColors.Muted,
                        fontSize = BenflixType.body,
                    )
                }
            }

            else -> {
                items(groups, key = { what + it.name + it.kind }) { g ->
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
                                vols = 0,
                            ),
                        width = w,
                        onClick = { onGroup(g) },
                    )
                }
            }
        }
    }
}

/** One series or author: their books in order */
@Composable
private fun GroupPage(
    group: BookGroup,
    wide: Boolean,
    easy: Boolean,
    onTile: (BookTile) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val edge = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge
    val w = cardWidth(wide, easy)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(w),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding =
            PaddingValues(
                start = edge,
                end = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge,
                top = 12.dp,
                bottom = 48.dp,
            ),
        modifier = modifier,
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MusicButton(label = null, icon = BenflixIcon.BACK_ARROW, onClick = onBack)
                Column {
                    Text(
                        text = group.name,
                        color = BenflixColors.Text,
                        fontSize = BenflixType.pageTitle,
                        fontWeight = FontWeight.ExtraBold,
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
    Box(modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Title("No books on Benflix yet")
            Text(
                text =
                    if (folders.isEmpty()) {
                        "Put books, audiobooks or manga in Tower's Media folder (Books, Audiobooks, Manga), and they show up here within 10 minutes."
                    } else {
                        "Put them in these folders on Tower, and they show up here within 10 minutes: " + folders.joinToString(", ")
                    },
                color = BenflixColors.Soft,
                fontSize = BenflixType.body,
            )
            if (canAsk) {
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
        }
    }
}
