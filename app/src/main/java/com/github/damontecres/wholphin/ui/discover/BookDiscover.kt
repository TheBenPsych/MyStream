package com.github.damontecres.wholphin.ui.discover

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.BenflixBooks
import com.github.damontecres.wholphin.services.BookAbout
import com.github.damontecres.wholphin.services.BookCard
import com.github.damontecres.wholphin.services.BookDiscover
import com.github.damontecres.wholphin.services.BookGenre
import com.github.damontecres.wholphin.services.BookInSeries
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.books.BOOK_RATIO
import com.github.damontecres.wholphin.ui.books.BookChip
import com.github.damontecres.wholphin.ui.books.BookCoverImage
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.music.MusicButton
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/*
 * Requests > Books (approved design, books-req-3-summary): Book of the week (the #1 NYT Fiction book),
 * Top 10 books right now with big numbers (Hardcover), your series and authors, the NYT lists, Trending,
 * 12 genre tiles, Award winners and Audiobooks people love. OK on a cover opens its details page; hold OK
 * asks for it straight away. A genre tile opens the same kind of page for that genre.
 */

/** The rows for the main tab ([slug] null) or one genre's page */
@HiltViewModel
class BookShelvesViewModel
    @Inject
    constructor(
        private val books: BenflixBooks,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        val shelves = MutableStateFlow<BookDiscover?>(null)
        val allGenres = MutableStateFlow<List<BookGenre>?>(null)
        val problem = MutableStateFlow<String?>(null)
        val note = MutableStateFlow<String?>(null)
        private var slug: String? = null

        val isAdmin: Boolean get() = serverRepository.currentUserDto?.policy?.isAdministrator == true

        fun load(genre: String?) {
            slug = genre
            viewModelScope.launchIO {
                try {
                    if (genre == "") {
                        allGenres.value = books.allGenres()
                    } else {
                        shelves.value = if (genre == null) books.discover() else books.genre(genre)
                    }
                    problem.value = null
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Book rows")
                    problem.value = ex.message ?: "Couldn't reach Tower"
                }
            }
        }

        /** Hold OK on a cover: ask straight away (Both, or Audiobook on the Audiobooks row) */
        fun quickAsk(card: BookCard) {
            if (card.asked != null || card.have != null) {
                open(card)
                return
            }
            viewModelScope.launchIO {
                note.value =
                    try {
                        books.ask(card.toFound(), card.quickTypes).let { "${card.name}: $it" }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        ex.message ?: "Couldn't ask for it."
                    }
                load(slug)
            }
        }

        fun open(card: BookCard) {
            val have = card.have
            if (have != null) {
                navigationManager.navigateTo(Destination.BookTitle(have))
            } else {
                navigationManager.navigateTo(Destination.BookAbout(card.key, card.name, card.author, card.audioOnly))
            }
        }

        fun openGenre(g: BookGenre?) = navigationManager.navigateTo(Destination.BookGenre(g?.slug ?: "", g?.name ?: "All genres"))
    }

/** The rows, for inside the Books tab's list (under the search box) */
internal fun LazyListScope.bookShelfItems(
    d: BookDiscover?,
    problem: String?,
    admin: Boolean,
    wide: Boolean,
    onOpen: (BookCard) -> Unit,
    onAsk: (BookCard) -> Unit,
    onGenre: (BookGenre?) -> Unit,
    onRetry: () -> Unit,
    heroFocus: FocusRequester? = null,
) {
    if (d == null) {
        item(key = "bs-loading") {
            if (problem != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Couldn't load the book lists. $problem", color = BenflixColors.Soft, fontSize = BenflixType.body)
                    MusicButton("Try again", BenflixIcon.REFRESH, onClick = onRetry)
                }
            } else {
                Text("Loading the book lists…", color = BenflixColors.Muted, fontSize = BenflixType.body)
            }
        }
        return
    }
    if (admin && d.missing.isNotEmpty()) {
        item(key = "bs-missing") {
            Text(
                "Some rows are hidden until you type the " + d.missing.joinToString(" key and the ") +
                    (
                        if (d.missing.size > 1) {
                            " token"
                        } else if (d.missing.first() == "Hardcover") {
                            " token"
                        } else {
                            " key"
                        }
                    ) +
                    " on the Server page (Tools > Book lists).",
                color = BenflixColors.Warning,
                fontSize = BenflixType.small,
            )
        }
    }
    d.hero?.let { hero -> item(key = "bs-hero") { BookHero(hero, d.name, wide, onOpen, onAsk, admin, heroFocus) } }
    if (d.top10.isNotEmpty()) {
        item(key = "bs-top10") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShelfTitle(if (d.name != null) "Top 10 in ${d.name}" else "Top 10 books right now")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    itemsIndexed(d.top10, key = { _, c -> "t10-" + c.key }) { i, c ->
                        Top10Book(i + 1, c, if (wide) 124.dp else 96.dp, onClick = { onOpen(c) }, onLongClick = { onAsk(c) })
                    }
                }
            }
        }
    }
    d.rows.forEach { row ->
        if (row.genres.isNotEmpty()) {
            item(key = "bs-genres") { GenreTiles(row.title, row.genres, wide, onGenre, withAll = true) }
        } else if (row.items.isNotEmpty()) {
            item(key = "bs-" + row.key) {
                CoverRow(row.title, row.items, wide, ranked = row.ranked, onOpen = onOpen, onAsk = onAsk)
            }
        }
    }
    if (d.gatheredAt == 0L && d.top10.isEmpty() && d.rows.none { it.items.isNotEmpty() }) {
        item(key = "bs-first") {
            Text(
                "Tower is gathering the book lists for the first time. Try again in a few minutes.",
                color = BenflixColors.Muted,
                fontSize = BenflixType.body,
            )
        }
    }
}

@Composable
private fun ShelfTitle(text: String) {
    Text(
        text,
        color = BenflixColors.Text,
        fontSize = BenflixType.rowName,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** Book of the week (or a genre's pick of the week): cover, name, why, the blurb, Ask for it */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookHero(
    c: BookCard,
    genreName: String?,
    wide: Boolean,
    onOpen: (BookCard) -> Unit,
    onAsk: (BookCard) -> Unit,
    admin: Boolean,
    focus: FocusRequester?,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (wide) 28.dp else 16.dp),
        verticalAlignment = Alignment.Top,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(BenflixColors.Panel, BenflixShapes.Card)
                .padding(if (wide) 22.dp else 14.dp),
    ) {
        Surface(
            onClick = { onOpen(c) },
            onLongClick = { onAsk(c) },
            shape = ClickableSurfaceDefaults.shape(BenflixShapes.Card),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
            border = doorFocusBorder(BenflixShapes.Card),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier = Modifier.width(if (wide) 170.dp else 110.dp).aspectRatio(BOOK_RATIO),
        ) {
            BookCoverImage(c.cover, c.name, Modifier.fillMaxSize())
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            Text(
                if (genreName != null) "PICK OF THE WEEK" else "BOOK OF THE WEEK",
                color = BenflixColors.Books,
                fontSize = BenflixType.tag,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                c.name,
                color = BenflixColors.Text,
                fontSize = if (wide) BenflixType.hero else BenflixType.pageTitle,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(c.author.takeIf { it.isNotBlank() }, c.line).joinToString("  ·  "),
                color = BenflixColors.Soft,
                fontSize = BenflixType.lead,
                maxLines = 2,
            )
            c.desc?.let {
                Text(
                    it,
                    color = BenflixColors.Soft,
                    fontSize = BenflixType.body,
                    maxLines = if (wide) 3 else 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    c.have != null -> {
                        MusicButton(
                            "On Benflix",
                            BenflixIcon.CHECK,
                            primary = true,
                            onClick = { onOpen(c) },
                            modifier =
                                focus?.let { Modifier.focusRequester(it) } ?: Modifier,
                        )
                    }

                    c.asked != null -> {
                        StatePill(c.asked)
                    }

                    else -> {
                        MusicButton(
                            if (admin) "Ask for it" else "Ask Ben",
                            BenflixIcon.PLUS,
                            primary = true,
                            onClick = { onAsk(c) },
                            modifier = focus?.let { Modifier.focusRequester(it) } ?: Modifier,
                        )
                    }
                }
                MusicButton("About this book", BenflixIcon.BOOK, onClick = { onOpen(c) })
            }
        }
    }
}

/** A row of covers; OK opens the details, hold OK asks */
@Composable
internal fun CoverRow(
    title: String,
    items: List<BookCard>,
    wide: Boolean,
    ranked: Boolean = false,
    onOpen: (BookCard) -> Unit,
    onAsk: ((BookCard) -> Unit)?,
    edge: Dp = 0.dp,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.padding(start = edge)) { ShelfTitle(title) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = edge, vertical = 4.dp)) {
            items(items, key = { title + it.key }) { c ->
                AskCover(c, if (wide) 124.dp else 100.dp, ranked, onClick = { onOpen(c) }, onLongClick = onAsk?.let { { it(c) } })
            }
        }
    }
}

@Composable
private fun AskCover(
    c: BookCard,
    width: Dp,
    ranked: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.width(width)) {
        Box {
            Surface(
                onClick = onClick,
                onLongClick = onLongClick,
                shape = ClickableSurfaceDefaults.shape(BenflixShapes.Card),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
                border = doorFocusBorder(BenflixShapes.Card),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier = Modifier.width(width).aspectRatio(BOOK_RATIO),
            ) {
                BookCoverImage(c.cover, c.name, Modifier.fillMaxSize(), audio = c.audioOnly)
            }
            if (ranked && c.rank != null) {
                Text(
                    "#${c.rank}",
                    color = BenflixColors.OnBooks,
                    fontSize = BenflixType.tag,
                    fontWeight = FontWeight.ExtraBold,
                    modifier =
                        Modifier
                            .padding(6.dp)
                            .background(BenflixColors.Books, BenflixShapes.Pill)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            c.name,
            color = BenflixColors.Text,
            fontSize = BenflixType.small,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val under = c.asked ?: c.award ?: c.line ?: c.author
        Text(
            under,
            color = if (c.asked != null) BenflixColors.Books else BenflixColors.Muted,
            fontSize = BenflixType.tag,
            fontWeight = if (c.asked != null) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Top 10: a tall outlined number tucked half behind the cover (like Movies and Shows) */
@Composable
private fun Top10Book(
    rank: Int,
    c: BookCard,
    width: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val coverHeight = width / BOOK_RATIO
    val numberHeight = coverHeight * 0.9f
    val numberSize = with(LocalDensity.current) { numberHeight.toSp() }
    val pull = if (rank.toString().startsWith("1")) -numberHeight * 0.08f else 0.dp
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = rank.toString(),
            style =
                TextStyle(
                    fontSize = numberSize,
                    fontFamily = BenflixTitleFont,
                    lineHeight = numberSize,
                    letterSpacing = (-1).sp,
                    drawStyle = Stroke(width = 5f, join = StrokeJoin.Round),
                ),
            color = if (focused) BenflixColors.Text else Color(0xFF5A5E6A),
            maxLines = 1,
            modifier = Modifier.offset(x = pull, y = coverHeight * 0.12f),
        )
        Box(Modifier.offset(x = pull - width * 0.2f).onFocusChanged { focused = it.hasFocus }) {
            AskCover(c, width, ranked = false, onClick = onClick, onLongClick = onLongClick)
        }
    }
}

/** Browse by genre: colour tiles, and "All genres" at the end */
@Composable
internal fun GenreTiles(
    title: String,
    genres: List<BookGenre>,
    wide: Boolean,
    onGenre: (BookGenre?) -> Unit,
    withAll: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ShelfTitle(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(genres, key = { "g-" + it.slug }) { g -> GenreTile(g.name, colorOf(g.color), wide, onClick = { onGenre(g) }) }
            if (withAll) item(key = "g-all") { GenreTile("All genres", BenflixColors.PanelHigh, wide, onClick = { onGenre(null) }) }
        }
    }
}

@Composable
private fun GenreTile(
    name: String,
    color: Color,
    wide: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = color, focusedContainerColor = color),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.width(if (wide) 176.dp else 140.dp).height(if (wide) 96.dp else 78.dp),
    ) {
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.BottomStart) {
            Text(
                name,
                color = if (color == BenflixColors.PanelHigh) BenflixColors.Text else Color(0xFF0B0C10),
                fontSize = BenflixType.body,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
            )
        }
    }
}

private fun colorOf(hex: String): Color =
    try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (ex: IllegalArgumentException) {
        BenflixColors.Books
    }

@Composable
private fun StatePill(text: String) {
    Text(
        text = text,
        color = if (text == "Ready") BenflixColors.Ok else BenflixColors.Books,
        fontSize = BenflixType.small,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.background(BenflixColors.Raised, BenflixShapes.Pill).padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

// ---------- A genre's page (and All genres) ----------

/** A genre's page: laid out like the main tab (pick of the week, Top 10, rows), for that genre only */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookGenrePage(
    slug: String,
    name: String,
    modifier: Modifier = Modifier,
    viewModel: BookShelvesViewModel = hiltViewModel(key = "book-genre-$slug"),
) {
    LifecycleResumeEffect(slug) {
        viewModel.load(slug)
        onPauseOrDispose { }
    }
    val d by viewModel.shelves.collectAsState()
    val all by viewModel.allGenres.collectAsState()
    val problem by viewModel.problem.collectAsState()
    val note by viewModel.note.collectAsState()
    LaunchedEffect(note) {
        if (note != null) {
            delay(6_000)
            viewModel.note.value = null
        }
    }
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp
    val edge = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge
    val first = remember { FocusRequester() }
    LaunchedEffect(d?.hero?.key, all) { first.tryRequestFocus() }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(if (wide) 24.dp else 16.dp),
        contentPadding = PaddingValues(start = edge, end = edge, top = 8.dp, bottom = 48.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "title") {
            Text(name, color = BenflixColors.Text, fontSize = BenflixType.pageTitle, fontWeight = FontWeight.ExtraBold)
        }
        note?.let {
            item(
                key = "note",
            ) { Text(it, color = BenflixColors.Books, fontSize = BenflixType.body, fontWeight = FontWeight.Bold) }
        }
        if (slug.isEmpty()) {
            val list = all
            item(key = "all") {
                if (list == null) {
                    Text(problem ?: "Loading…", color = BenflixColors.Muted, fontSize = BenflixType.body)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        list.forEachIndexed { i, g ->
                            GenreTile(
                                g.name,
                                colorOf(g.color),
                                wide,
                                onClick = { viewModel.openGenre(g) },
                                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                            )
                        }
                    }
                }
            }
            return@LazyColumn
        }
        bookShelfItems(
            d = d,
            problem = problem,
            admin = viewModel.isAdmin,
            wide = wide,
            onOpen = viewModel::open,
            onAsk = viewModel::quickAsk,
            onGenre = viewModel::openGenre,
            onRetry = { viewModel.load(slug) },
            heroFocus = first,
        )
        if (d != null && d?.hero == null && d?.top10.isNullOrEmpty() && d?.rows.isNullOrEmpty()) {
            item(key = "empty") {
                Text("Nothing here yet. Tower gathers the lists once a day.", color = BenflixColors.Muted, fontSize = BenflixType.body)
            }
        }
    }
}

// ---------- One book's details page ----------

@HiltViewModel
class BookAboutViewModel
    @Inject
    constructor(
        private val books: BenflixBooks,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        val about = MutableStateFlow<BookAbout?>(null)
        val problem = MutableStateFlow<String?>(null)
        val note = MutableStateFlow<String?>(null)
        val busy = MutableStateFlow(false)
        private var args = Triple("", "", "")

        val isAdmin: Boolean get() = serverRepository.currentUserDto?.policy?.isAdministrator == true

        fun load(
            key: String,
            name: String,
            author: String,
        ) {
            args = Triple(key, name, author)
            viewModelScope.launchIO {
                try {
                    about.value = books.about(key, name, author)
                    problem.value = null
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Book details")
                    problem.value = ex.message ?: "Couldn't reach Tower"
                }
            }
        }

        fun reload() = load(args.first, args.second, args.third)

        private fun act(block: suspend () -> String) {
            busy.value = true
            viewModelScope.launchIO {
                note.value =
                    try {
                        block()
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        ex.message ?: "That didn't work. Try again."
                    } finally {
                        busy.value = false
                    }
                reload()
            }
        }

        fun ask(
            a: BookAbout,
            types: List<String>,
        ) = act { books.ask(a.card.toFound(), types) }

        /** "Ask for books 2 and 3": each missing book in the series */
        fun askMissing(missing: List<BookInSeries>) =
            act {
                missing.forEach { b ->
                    books.ask(
                        com.github.damontecres.wholphin.services.BookFound(
                            b.key,
                            b.name,
                            b.author,
                            null,
                            b.cover,
                            null,
                            null,
                            null,
                            null,
                            false,
                        ),
                        listOf("ebook", "audiobook"),
                    )
                }
                if (missing.size == 1) "Asked for ${missing[0].name}." else "Asked for ${missing.size} books."
            }

        fun toggleWant(a: BookAbout) =
            act {
                val now = books.want(a.card.key, !a.want, a.card.name, a.card.author, a.card.cover, "book")
                if (now) "Added to Want to read." else "Taken off Want to read."
            }

        fun open(card: BookCard) {
            val have = card.have
            if (have != null) {
                navigationManager.navigateTo(Destination.BookTitle(have))
            } else {
                navigationManager.navigateTo(Destination.BookAbout(card.key, card.name, card.author))
            }
        }

        fun openSeriesBook(b: BookInSeries) {
            val have = b.have
            if (have != null) {
                navigationManager.navigateTo(Destination.BookTitle(have))
            } else if (!b.isThis) {
                navigationManager.navigateTo(Destination.BookAbout(b.key, b.name, b.author))
            }
        }
    }

/**
 * A book's details page (approved bd-movie + dd-under + bk-flip): movie-page style. Cover, name, author,
 * Hardcover score and NYT rank, Ask for it with Ebook / Audiobook / Both, Want to read. Right under the
 * buttons, About this book in large text. Then the series line with ticks, the fact cards, More by this author
 * and More like this. OK on the cover turns it over to a back cover Benflix makes from the blurb and two quotes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookAboutPage(
    key: String,
    name: String,
    author: String,
    audioOnly: Boolean,
    modifier: Modifier = Modifier,
    viewModel: BookAboutViewModel = hiltViewModel(key = "book-about-$key"),
) {
    LifecycleResumeEffect(key) {
        viewModel.load(key, name, author)
        onPauseOrDispose { }
    }
    val a by viewModel.about.collectAsState()
    val problem by viewModel.problem.collectAsState()
    val note by viewModel.note.collectAsState()
    val busy by viewModel.busy.collectAsState()
    LaunchedEffect(note) {
        if (note != null) {
            delay(6_000)
            viewModel.note.value = null
        }
    }
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp
    val edge = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge
    val about = a
    if (about == null) {
        Column(Modifier.fillMaxSize().padding(edge), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(name, color = BenflixColors.Text, fontSize = BenflixType.hero, fontWeight = FontWeight.ExtraBold)
            if (problem != null) {
                Text(problem!!, color = BenflixColors.Soft, fontSize = BenflixType.body)
                MusicButton("Try again", BenflixIcon.REFRESH, onClick = viewModel::reload)
            } else {
                Text("Looking it up…", color = BenflixColors.Muted, fontSize = BenflixType.body)
            }
        }
        return
    }
    val admin = viewModel.isAdmin
    val mainFocus = remember { FocusRequester() }
    LaunchedEffect(about.card.key) { mainFocus.tryRequestFocus() }
    var flipped by remember(about.card.key) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val topFocus =
        Modifier.onFocusChanged {
            if (it.hasFocus && (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0)) {
                scope.launch { listState.animateScrollToItem(0) }
            }
        }
    val coverWidth = if (wide) 250.dp else 170.dp
    val cover: @Composable () -> Unit = {
        Surface(
            onClick = { flipped = !flipped },
            shape = ClickableSurfaceDefaults.shape(BenflixShapes.Card),
            colors = ClickableSurfaceDefaults.colors(containerColor = BenflixColors.Panel, focusedContainerColor = BenflixColors.Panel),
            border = doorFocusBorder(BenflixShapes.Card),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier = Modifier.width(coverWidth).aspectRatio(BOOK_RATIO),
        ) {
            AnimatedContent(targetState = flipped, label = "cover") { back ->
                if (back) BackCover(about) else BookCoverImage(about.card.cover, about.card.name, Modifier.fillMaxSize(), audio = audioOnly)
            }
        }
    }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(if (wide) 26.dp else 18.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "top") {
            val text: @Composable (Modifier) -> Unit = { m ->
                AboutTop(about, admin, audioOnly, note, busy, mainFocus, viewModel, m)
            }
            if (wide) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = edge).then(topFocus),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        cover()
                        Text(
                            if (flipped) {
                                "OK to turn it back"
                            } else if (isTv) {
                                "OK on the cover to turn it over"
                            } else {
                                "Tap the cover to turn it over"
                            },
                            color = BenflixColors.Muted,
                            fontSize = BenflixType.tag,
                        )
                    }
                    text(Modifier.weight(1f).widthIn(max = 980.dp))
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().then(topFocus),
                ) {
                    cover()
                    Text(
                        if (flipped) "Tap to turn it back" else "Tap the cover to turn it over",
                        color = BenflixColors.Muted,
                        fontSize = BenflixType.tag,
                    )
                    text(Modifier.fillMaxWidth().padding(horizontal = edge))
                }
            }
        }
        if (about.series.size > 1) {
            item(key = "series") { SeriesLine(about, admin, edge, wide, viewModel) }
        }
        if (about.facts.isNotEmpty()) {
            item(key = "facts") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = edge),
                ) {
                    about.facts.forEach { f ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier =
                                Modifier
                                    .background(BenflixColors.Panel, BenflixShapes.Card)
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .widthIn(min = 110.dp),
                        ) {
                            Text(f.label.uppercase(), color = BenflixColors.Muted, fontSize = BenflixType.tag, fontWeight = FontWeight.Bold)
                            Text(f.value, color = BenflixColors.Text, fontSize = BenflixType.body, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        if (about.byAuthor.isNotEmpty()) {
            item(key = "by") {
                CoverRow(
                    "More by ${about.card.author.substringBefore(",")}",
                    about.byAuthor,
                    wide,
                    onOpen = viewModel::open,
                    onAsk = null,
                    edge = edge,
                )
            }
        }
        if (about.likeThis.isNotEmpty()) {
            item(key = "like") { CoverRow("More like this", about.likeThis, wide, onOpen = viewModel::open, onAsk = null, edge = edge) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutTop(
    a: BookAbout,
    admin: Boolean,
    audioOnly: Boolean,
    note: String?,
    busy: Boolean,
    mainFocus: FocusRequester,
    viewModel: BookAboutViewModel,
    modifier: Modifier,
) {
    var pick by remember(a.card.key) { mutableStateOf(if (audioOnly) "audiobook" else "both") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        Text(
            if (audioOnly) "AUDIOBOOK" else "BOOK",
            color = BenflixColors.Books,
            fontSize = BenflixType.tag,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            a.card.name,
            color = BenflixColors.Text,
            fontSize = BenflixType.hero,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        val bits =
            listOfNotNull(
                a.card.author.takeIf {
                    it.isNotBlank()
                },
                a.card.year?.toString(),
                a.genres
                    .take(2)
                    .joinToString(", ")
                    .takeIf { it.isNotBlank() },
            )
        if (bits.isNotEmpty()) Text(bits.joinToString("  ·  "), color = BenflixColors.Soft, fontSize = BenflixType.lead, maxLines = 2)
        val scores =
            listOfNotNull(
                a.score?.let { (a.scoreFrom ?: "Score") + " " + "%.1f".format(it) + "/5" + (if (a.votes > 0) " (${a.votes})" else "") },
                a.nytRank?.let { "#$it NYT ${a.nytList ?: ""}".trim() + (if (a.nytWeeks > 1) ", ${a.nytWeeks} weeks" else "") },
                a.award ?: a.card.award,
            )
        if (scores.isNotEmpty()) {
            Text(scores.joinToString("   ·   "), color = BenflixColors.Books, fontSize = BenflixType.small, fontWeight = FontWeight.Bold)
        }
        note?.let { Text(it, color = BenflixColors.Books, fontSize = BenflixType.body, fontWeight = FontWeight.Bold) }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val focusMod = Modifier.focusRequester(mainFocus)
            when {
                a.have != null -> {
                    MusicButton(
                        "On Benflix",
                        BenflixIcon.CHECK,
                        primary = true,
                        onClick = { a.have?.let { viewModel.navigationManager.navigateTo(Destination.BookTitle(it)) } },
                        modifier = focusMod,
                    )
                }

                a.asked != null -> {
                    Box(focusMod) { StatePill(a.asked) }
                }

                else -> {
                    if (!audioOnly) {
                        BookChip("Ebook", pick == "ebook", onClick = { pick = "ebook" })
                    }
                    BookChip("Audiobook", pick == "audiobook", onClick = { pick = "audiobook" })
                    if (!audioOnly) BookChip("Both", pick == "both", onClick = { pick = "both" })
                    MusicButton(
                        if (busy) {
                            "Asking…"
                        } else if (admin) {
                            "Ask for it"
                        } else {
                            "Ask Ben"
                        },
                        BenflixIcon.PLUS,
                        primary = true,
                        onClick = {
                            if (!busy) viewModel.ask(a, if (pick == "both") listOf("ebook", "audiobook") else listOf(pick))
                        },
                        modifier = focusMod,
                    )
                }
            }
            if (a.have == null) {
                MusicButton(
                    if (a.want) "On Want to read" else "Want to read",
                    if (a.want) BenflixIcon.CHECK else BenflixIcon.PLUS,
                    onClick = { viewModel.toggleWant(a) },
                )
            }
        }
        if (a.asked != null && !admin && a.asked == "Waiting for Ben") {
            Text("Ben gets a note and says yes or no.", color = BenflixColors.Muted, fontSize = BenflixType.small)
        }
        // Ben: the description must be clear, so it's right under the buttons, in big text
        Text(
            "About this book",
            color = BenflixColors.Text,
            fontSize = BenflixType.rowName,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            a.desc.ifBlank { "No description yet." },
            color = if (a.desc.isBlank()) BenflixColors.Muted else BenflixColors.Text,
            fontSize = BenflixType.lead,
            lineHeight = BenflixType.lead * 1.45f,
        )
    }
}

/** The series line: each book with a tick (on Benflix), its pill (asked), and "Ask for books 2 and 3" */
@Composable
private fun SeriesLine(
    a: BookAbout,
    admin: Boolean,
    edge: Dp,
    wide: Boolean,
    viewModel: BookAboutViewModel,
) {
    val missing = a.series.filter { it.have == null && it.asked == null && !it.isThis }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = edge),
        ) {
            ShelfTitle("${a.seriesName ?: "The series"} · ${a.series.size} books")
            if (missing.isNotEmpty()) {
                val words =
                    when (missing.size) {
                        1 -> "book ${missing[0].pos}"
                        2 -> "books ${missing[0].pos} and ${missing[1].pos}"
                        else -> "the other ${missing.size} books"
                    }
                MusicButton(
                    (if (admin) "Ask for " else "Ask Ben for ") + words,
                    BenflixIcon.PLUS,
                    onClick = { viewModel.askMissing(missing) },
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = edge, vertical = 4.dp)) {
            items(a.series, key = { "s-" + it.key }) { b ->
                val w = if (wide) 108.dp else 88.dp
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.width(w)) {
                    Box {
                        Surface(
                            onClick = { viewModel.openSeriesBook(b) },
                            shape = ClickableSurfaceDefaults.shape(BenflixShapes.Card),
                            colors =
                                ClickableSurfaceDefaults.colors(
                                    containerColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                ),
                            border = doorFocusBorder(BenflixShapes.Card),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                            modifier = Modifier.width(w).aspectRatio(BOOK_RATIO),
                        ) {
                            BookCoverImage(b.cover, b.name, Modifier.fillMaxSize())
                        }
                        if (b.have != null) {
                            Text(
                                "✓",
                                color = BenflixColors.OnBooks,
                                fontSize = BenflixType.small,
                                fontWeight = FontWeight.ExtraBold,
                                modifier =
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(26.dp)
                                        .background(BenflixColors.Books, BenflixShapes.Pill)
                                        .padding(top = 2.dp, start = 7.dp),
                            )
                        }
                    }
                    Text(
                        "${b.pos}. ${b.name}",
                        color = if (b.isThis) BenflixColors.Books else BenflixColors.Text,
                        fontSize = BenflixType.tag,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val line =
                        when {
                            b.have != null -> "On Benflix"
                            b.asked != null -> b.asked
                            b.isThis -> "This book"
                            else -> null
                        }
                    line?.let {
                        Text(
                            it,
                            color = if (b.have != null || b.asked != null) BenflixColors.Books else BenflixColors.Muted,
                            fontSize = BenflixType.tag,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** The back of the book, made by Benflix: the blurb and two reader quotes */
@Composable
private fun BackCover(a: BookAbout) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().background(BenflixColors.PanelHigh).padding(14.dp),
    ) {
        Text(
            a.card.name.uppercase(),
            color = BenflixColors.Books,
            fontSize = BenflixType.tag,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
        )
        Text(
            a.blurb.ifBlank { "No blurb yet." },
            color = BenflixColors.Text,
            fontSize = BenflixType.tag,
            lineHeight = BenflixType.tag * 1.35f,
            maxLines = if (a.quotes.isEmpty()) 14 else 8,
            overflow = TextOverflow.Ellipsis,
        )
        a.quotes.take(2).forEach { q ->
            Text(
                "“$q”",
                color = BenflixColors.Soft,
                fontSize = BenflixType.tag,
                fontWeight = FontWeight.Bold,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
