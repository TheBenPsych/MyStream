package com.github.damontecres.wholphin.ui.books

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BookGroup
import com.github.damontecres.wholphin.services.BookRow
import com.github.damontecres.wholphin.services.BookTile
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.discover.RequestsJump
import com.github.damontecres.wholphin.ui.main.DoorWipe
import com.github.damontecres.wholphin.ui.music.MusicButton
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.focusRing
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus

/**
 * Benflix Books: inside the lime Books door (approved design). TV: the side menu is always open on the left
 * (Home, Manga, Comics, Books, Audiobooks, Series, Authors, Want to read, Finished; Manga and Comics only
 * once there are some). Phones: the same places as chips along the top. Home has On deck, New for you,
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

    // Back closes an open series or author first, then goes back to Books Home, then leaves
    BackHandler(group != null) { viewModel.group.value = null }
    BackHandler(group == null && place != BooksPlace.HOME) { viewModel.place.value = BooksPlace.HOME }

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
                BooksHomeRows(home.rows, wide, easy, home.canAsk && !viewModel.isKids, viewModel, onTile, m)
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
    if (wide) {
        Row(modifier.fillMaxSize()) {
            SideMenu(places, place, home.counts, onPick = {
                viewModel.group.value = null
                viewModel.place.value = it
            })
            content(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(modifier.fillMaxSize()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = edge, vertical = 8.dp),
            ) {
                items(places, key = { it.name }) { p ->
                    BookChip(p.label, p == place, onClick = {
                        viewModel.group.value = null
                        viewModel.place.value = p
                    })
                }
            }
            content(Modifier.weight(1f).fillMaxWidth())
        }
    }
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

/** The always-open side menu on TV, with a lime bar beside the place you're on */
@Composable
private fun SideMenu(
    places: List<BooksPlace>,
    current: BooksPlace,
    counts: Map<String, Int>,
    onPick: (BooksPlace) -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.tryRequestFocus() }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(start = BenflixSpace.tvEdge, end = 16.dp, top = 16.dp, bottom = 32.dp),
        modifier = Modifier.width(250.dp).fillMaxHeight(),
    ) {
        items(places, key = { it.name }) { p ->
            val on = p == current
            val shape = BenflixShapes.Small
            Surface(
                onClick = { onPick(p) },
                shape = ClickableSurfaceDefaults.shape(shape),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = if (on) BenflixColors.Raised else Color.Transparent,
                        contentColor = if (on) BenflixColors.Books else BenflixColors.Soft,
                        focusedContainerColor = BenflixColors.FocusRow,
                        focusedContentColor = BenflixColors.Text,
                    ),
                border = focusRing(shape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(if (on) Modifier.focusRequester(first) else Modifier),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
                    Box(
                        Modifier
                            .size(width = 4.dp, height = 20.dp)
                            .background(if (on) BenflixColors.Books else Color.Transparent, RoundedCornerShape(2.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = p.label,
                        fontSize = BenflixType.lead,
                        fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    p.kind?.let { k ->
                        counts[k]?.takeIf { it > 0 }?.let {
                            Text(text = "$it", fontSize = BenflixType.tag, color = BenflixColors.Muted)
                        }
                    }
                }
            }
        }
    }
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
    onTile: (BookTile) -> Unit,
    modifier: Modifier,
) {
    val edge = if (wide) 8.dp else BenflixSpace.phoneEdge
    val w = cardWidth(wide, easy)
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(if (wide) BenflixSpace.tvRowGap else BenflixSpace.phoneRowGap),
        contentPadding = PaddingValues(top = if (wide) 16.dp else 4.dp, bottom = 48.dp),
        modifier = modifier,
    ) {
        items(rows, key = { it.key }) { row ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = row.title,
                    color = BenflixColors.Text,
                    fontSize = BenflixType.rowName,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = edge),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(start = edge, end = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge),
                ) {
                    items(row.tiles, key = { row.key + it.id }) { tile ->
                        BookCard(tile, w, onClick = { onTile(tile) })
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
    val edge = if (wide) 8.dp else BenflixSpace.phoneEdge
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
    val edge = if (wide) 8.dp else BenflixSpace.phoneEdge
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
    val edge = if (wide) 8.dp else BenflixSpace.phoneEdge
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
