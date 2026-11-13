package com.github.damontecres.wholphin.ui.discover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.BenflixBooks
import com.github.damontecres.wholphin.services.BookCard
import com.github.damontecres.wholphin.services.BookFound
import com.github.damontecres.wholphin.services.BookGenre
import com.github.damontecres.wholphin.services.BookRequest
import com.github.damontecres.wholphin.services.BookRequests
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.books.BOOK_RATIO
import com.github.damontecres.wholphin.ui.books.BookCoverImage
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.music.MusicButton
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class BookRequestsViewModel
    @Inject
    constructor(
        private val books: BenflixBooks,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        val query = MutableStateFlow("")
        val found = MutableStateFlow<Pair<List<BookFound>, List<BookFound>>?>(null)
        val searching = MutableStateFlow(false)
        val requests = MutableStateFlow<BookRequests?>(null)
        val note = MutableStateFlow<String?>(null)
        val problem = MutableStateFlow<String?>(null)
        private var searchJob: Job? = null

        val isAdmin: Boolean get() = serverRepository.currentUserDto?.policy?.isAdministrator == true

        fun search(text: String) {
            query.value = text
            searchJob?.cancel()
            if (text.trim().length < 2) {
                found.value = null
                return
            }
            searchJob =
                viewModelScope.launchIO {
                    delay(450)
                    searching.value = true
                    try {
                        found.value = books.search(text.trim())
                        problem.value = null
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Book search")
                        problem.value = ex.message ?: "Couldn't search right now"
                    } finally {
                        searching.value = false
                    }
                }
        }

        suspend fun refresh() {
            try {
                requests.value = books.requests()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Book requests")
                if (requests.value == null) problem.value = ex.message ?: "Couldn't reach Tower"
            }
        }

        fun ask(
            f: BookFound,
            types: List<String>,
        ) {
            viewModelScope.launchIO {
                note.value =
                    try {
                        books.ask(f, types)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        ex.message ?: "Couldn't ask for it."
                    }
                refresh()
                if (query.value.isNotBlank()) search(query.value)
            }
        }

        private fun act(block: suspend () -> Unit) {
            viewModelScope.launchIO {
                try {
                    block()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    note.value = ex.message ?: "That didn't work. Try again."
                }
                refresh()
            }
        }

        fun answer(
            r: BookRequest,
            yes: Boolean,
        ) = act { books.answer(r.id, yes) }

        fun retry(r: BookRequest) = act { books.retry(r.id) }

        fun forget(r: BookRequest) = act { books.forget(r.id) }

        fun open(titleId: String) = navigationManager.navigateTo(Destination.BookTitle(titleId))
    }

/**
 * Requests > Books (Redesign 2, Ben 2026-11-10): the same side list and wall as every other Requests tab.
 * Lists: Book of the week (its big cover in the line above the wall, the NYT Fiction list below it), Top 10,
 * Next in your series, New from authors you read, NYT Fiction, NYT Nonfiction, Trending, Genres, Award winners,
 * Audiobooks people love. OK on a cover opens the book's page; hold OK asks for it straight away. Everyone
 * else's asks wait for Ben's yes (Waiting for you).
 */
@Composable
fun BooksShelf(
    viewModel: BookRequestsViewModel = hiltViewModel(),
    shelvesViewModel: BookShelvesViewModel = hiltViewModel(key = "book-shelves-main"),
) {
    val shelves by shelvesViewModel.shelves.collectAsState()
    val shelvesProblem by shelvesViewModel.problem.collectAsState()
    val shelvesNote by shelvesViewModel.note.collectAsState()
    // Coming back from a book's details page: the lists pick up anything just asked for
    LifecycleResumeEffect(Unit) {
        shelvesViewModel.load(null)
        onPauseOrDispose { }
    }
    LaunchedEffect(shelvesNote) {
        if (shelvesNote != null) {
            delay(6_000)
            shelvesViewModel.note.value = null
        }
    }
    val isTv = rememberIsTv()
    val query by viewModel.query.collectAsState()
    val found by viewModel.found.collectAsState()
    val searching by viewModel.searching.collectAsState()
    val requests by viewModel.requests.collectAsState()
    val note by viewModel.note.collectAsState()
    val problem by viewModel.problem.collectAsState()
    val scope = rememberCoroutineScope()
    // Tower moves requests along by itself; look again every 10 seconds while the tab is open
    LifecycleResumeEffect(Unit) {
        val job =
            scope.launch {
                while (true) {
                    viewModel.refresh()
                    delay(10_000)
                }
            }
        onPauseOrDispose { job.cancel() }
    }
    LaunchedEffect(note) {
        if (note != null) {
            delay(6_000)
            viewModel.note.value = null
        }
    }
    val admin = viewModel.isAdmin
    var askFor by remember { mutableStateOf<BookFound?>(null) }
    askFor?.let { f ->
        BookAskPanel(
            title = f.name,
            author = f.author,
            cover = f.cover,
            admin = admin,
            manga = f.manga,
            onDismiss = { askFor = null },
            onAsk = { types ->
                askFor = null
                viewModel.ask(f, types)
            },
        )
    }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val searchList: @Composable (Modifier) -> Unit = { m ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
            modifier = m,
        ) {
            if (isTv) {
                item(key = "search") {
                    RequestsSearchBox(query = query, onQuery = viewModel::search, placeholder = "Search any book, author or manga")
                }
            }
            val f = found
            when {
                query.trim().length < 2 -> {
                    item(key = "type") { Muted("Type a book, an author or a manga.") }
                }

                f == null && searching -> {
                    item(key = "searching") { Muted("Looking…") }
                }

                f == null -> {}

                f.first.isEmpty() && f.second.isEmpty() -> {
                    item(key = "nothing") { Muted("Nothing found. Try the author's name, or fewer words.") }
                }

                else -> {
                    if (f.first.isNotEmpty()) {
                        item(key = "books-title") { Heading("Books") }
                        items(f.first, key = { "b" + it.key }) { FoundRow(it, viewModel, onAsk = { askFor = it }) }
                    }
                    if (f.second.isNotEmpty()) {
                        item(key = "manga-title") { Heading("Manga") }
                        items(f.second, key = { "m" + it.key }) { FoundRow(it, viewModel, onAsk = { askFor = it }) }
                    }
                }
            }
        }
    }
    if (isTv && searchOpen) {
        BackHandler { searchOpen = false }
        searchList(Modifier.fillMaxSize())
        return
    }
    val d = shelves
    val r = requests ?: BookRequests(emptyList(), emptyList(), emptyList(), emptyList(), true, emptyList())
    // The lists, in the approved order. Book of the week shows the NYT Fiction list (it's that list's #1).
    val nytFiction = d?.rows?.firstOrNull { it.items.isNotEmpty() && it.title.contains("Fiction") && !it.title.contains("Nonfiction") }
    val bookLists =
        remember(d) {
            buildList {
                if (d == null) return@buildList
                if (d.hero != null) add(BookList("botw", "Book of the week", nytFiction?.items ?: d.top10, ranked = true))
                if (d.top10.isNotEmpty()) add(BookList("top10", "Top 10", d.top10, ranked = true))
                d.rows.forEach { row ->
                    if (row.genres.isNotEmpty()) {
                        add(BookList("genres", "Genres", emptyList(), genres = row.genres))
                    } else if (row.items.isNotEmpty()) {
                        add(BookList(row.key, row.title, row.items, ranked = row.ranked))
                    }
                }
            }
        }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val pickedKey =
        picked?.takeIf { p -> p == PICK_YOURS || p == PICK_WAITING || bookLists.any { it.key == p } } ?: bookLists.firstOrNull()?.key ?: ""
    var ownedOpen by rememberSaveable { mutableStateOf<String?>(null) }
    val waitingIds = r.waiting.map { it.id }.toSet()
    val yours = (if (admin) r.mine + r.mangaToAdd + r.others else r.mine).distinctBy { it.id }.filter { it.id !in waitingIds }
    val yourLines =
        yours.map { b ->
            b.toAskLine(
                showWho = admin,
                onOpen = b.titleId?.let { id -> { viewModel.open(id) } },
                onRetry = { viewModel.retry(b) },
            )
        }
    val waitingLines =
        r.waiting.map { b ->
            b.toAskLine(showWho = true, onOpen = null, onAnswer = { yes -> viewModel.answer(b, yes) })
        }
    val focusedLine = remember { mutableStateOf<String?>(null) }
    val wall: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            (note ?: shelvesNote)?.let {
                Text(
                    it,
                    color = BenflixColors.Books,
                    fontSize = BenflixType.body,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            problem?.let {
                Text(it, color = BenflixColors.Warning, fontSize = BenflixType.small, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (admin) {
                r.problems.forEach { line ->
                    Text(line, color = BenflixColors.Warning, fontSize = BenflixType.small, modifier = Modifier.padding(horizontal = 16.dp))
                }
                if (requests?.connected == false) {
                    Text(
                        "Tower's helper can't reach Chaptarr yet, so asks wait here. Run the helper setup on Tower once more " +
                            "and it finds Chaptarr by itself.",
                        color = BenflixColors.Warning,
                        fontSize = BenflixType.small,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (d != null && d.missing.isNotEmpty()) {
                    Text(
                        "Some lists are hidden until you type the " + d.missing.joinToString(" and the ") +
                            " key on the Server page (Tools > Book lists).",
                        color = BenflixColors.Warning,
                        fontSize = BenflixType.small,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            val list = bookLists.firstOrNull { it.key == pickedKey }
            when {
                pickedKey == PICK_YOURS -> {
                    AskLinesWall(
                        lines = yourLines,
                        grouped = true,
                        empty =
                            if (isTv) {
                                "Nothing asked for yet. OK on a cover shows the book; hold OK to ask for it straight away."
                            } else {
                                "Nothing asked for yet. Tap a cover to see the book; press and hold to ask for it straight away."
                            },
                    )
                }

                pickedKey == PICK_WAITING -> {
                    AskLinesWall(lines = waitingLines, grouped = false, empty = "Nobody is waiting on you right now.")
                }

                d == null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                        if (shelvesProblem != null) {
                            Text("Couldn't load the book lists. $shelvesProblem", color = BenflixColors.Soft, fontSize = BenflixType.body)
                            MusicButton("Try again", BenflixIcon.REFRESH, onClick = { shelvesViewModel.load(null) })
                        } else {
                            Muted("Loading the book lists…")
                        }
                    }
                }

                list == null -> {
                    Muted(
                        if (d.gatheredAt == 0L) {
                            "Tower is gathering the book lists for the first time. Try again in a few minutes."
                        } else {
                            "Nothing to show right now."
                        },
                    )
                }

                list.genres.isNotEmpty() -> {
                    GenreTileWall(list.genres, onGenre = shelvesViewModel::openGenre)
                }

                else -> {
                    if (isTv || list.key == "botw") {
                        BookLine(
                            hero = if (list.key == "botw") d.hero else null,
                            focused = focusedLine.value,
                            onOpen = shelvesViewModel::open,
                        )
                    }
                    val owned = list.items.count { it.have != null }
                    val shown = if (ownedOpen == list.key) list.items else list.items.filter { it.have == null }
                    BookWall(
                        list = list,
                        books = shown,
                        onOpen = shelvesViewModel::open,
                        onAsk = shelvesViewModel::quickAsk,
                        onFocus = { c -> focusedLine.value = bookLineWords(c) },
                        owned = owned,
                        ownedOpen = ownedOpen == list.key,
                        onOwned = { ownedOpen = if (ownedOpen == list.key) null else list.key },
                    )
                }
            }
        }
    }
    RequestSideFrame(
        isTv = isTv,
        lists = bookLists.map { SideList(it.key, it.title) },
        picked = pickedKey,
        onPick = { picked = it },
        yoursCount = yours.size,
        waitingCount = if (admin) r.waiting.size else null,
        onSearch = { searchOpen = true },
        loading = d == null,
        searchLabel = "Search any book or manga",
        phoneTop = { RequestsSearchBox(query = query, onQuery = viewModel::search, placeholder = "Search any book, author or manga") },
        phoneWall = query.trim().length < 2,
        phoneAlt = searchList,
        wall = wall,
    )
}

/** The line above the wall for one book: name · author · year · series */
private fun bookLineWords(c: BookCard): String =
    listOfNotNull(c.name, c.author.ifBlank { null }, c.year?.toString(), c.line).joinToString(" · ")

/** One list on the Books tab */
internal class BookList(
    val key: String,
    val title: String,
    val items: List<BookCard>,
    val ranked: Boolean = false,
    val genres: List<BookGenre> = emptyList(),
)

/** The line above the wall: Book of the week with its cover on the first list, or the book you're on */
@Composable
private fun BookLine(
    hero: BookCard?,
    focused: String?,
    onOpen: (BookCard) -> Unit,
) {
    if (hero == null) {
        Text(
            text = focused ?: " ",
            fontSize = BenflixType.body,
            color = BenflixColors.Soft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        )
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    ) {
        BookCoverImage(hero.cover, hero.name, Modifier.width(52.dp).aspectRatio(BOOK_RATIO))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                "BOOK OF THE WEEK · #1 NYT FICTION",
                color = BenflixColors.Books,
                fontSize = BenflixType.tag,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                listOfNotNull(hero.name, hero.author.ifBlank { null }, hero.year?.toString(), hero.line).joinToString(" · "),
                color = BenflixColors.Text,
                fontSize = BenflixType.body,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MusicButton("About this book", BenflixIcon.BOOK, onClick = { onOpen(hero) })
    }
}

/** The Books wall: covers 7 across on TV (3 on phones), names under every cover, the Top 10 numbered */
@Composable
private fun BookWall(
    list: BookList,
    books: List<BookCard>,
    onOpen: (BookCard) -> Unit,
    onAsk: (BookCard) -> Unit,
    onFocus: (BookCard) -> Unit,
    owned: Int,
    ownedOpen: Boolean,
    onOwned: () -> Unit,
) {
    val isTv = rememberIsTv()
    val grid = rememberLazyGridState()
    LaunchedEffect(list.key) { grid.scrollToItem(0) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isTv) 7 else 3),
        state = grid,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(books.size, key = { "${list.key}-$it-${books[it].key}" }) { i ->
            val c = books[i]
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box {
                    Surface(
                        onClick = { onOpen(c) },
                        onLongClick = { onAsk(c) },
                        shape = ClickableSurfaceDefaults.shape(BenflixShapes.Card),
                        colors =
                            ClickableSurfaceDefaults.colors(
                                containerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                            ),
                        border = doorFocusBorder(BenflixShapes.Card),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(BOOK_RATIO)
                                .onFocusChanged { if (it.isFocused) onFocus(c) },
                    ) {
                        BookCoverImage(c.cover, c.name, Modifier.fillMaxSize(), audio = c.audioOnly)
                    }
                    if (list.ranked && i < 10) {
                        Text(
                            "No. ${i + 1}",
                            color = BenflixColors.Tray,
                            fontSize = BenflixType.tag,
                            fontWeight = FontWeight.ExtraBold,
                            modifier =
                                Modifier
                                    .padding(6.dp)
                                    .background(BenflixColors.Text, BenflixShapes.Pill)
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
                val asked = c.asked?.let { bookPill("", it) }
                Text(
                    asked?.words ?: if (c.have != null) "Ready to read" else c.award ?: c.author,
                    color = asked?.color ?: BenflixColors.Muted,
                    fontSize = BenflixType.tag,
                    fontWeight = if (asked != null) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (owned > 0) {
            item(key = "${list.key}-owned", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.padding(top = 8.dp)) { OwnedMoreButton(owned, ownedOpen, onOwned) }
            }
        }
    }
}

/** Genres: the 12 colour tiles and All genres, as a wall */
@Composable
private fun GenreTileWall(
    genres: List<BookGenre>,
    onGenre: (BookGenre?) -> Unit,
) {
    val isTv = rememberIsTv()
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isTv) 4 else 2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(genres.size, key = { "genre-$it-${genres[it].slug}" }) { i ->
            val g = genres[i]
            RequestsChip(g.name, false, { onGenre(g) }, Modifier.fillMaxWidth())
        }
        item(key = "genre-all") { RequestsChip("All genres", false, { onGenre(null) }, Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        color = BenflixColors.Text,
        fontSize = BenflixType.rowName,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun Muted(text: String) {
    Text(text, color = BenflixColors.Muted, fontSize = BenflixType.body)
}

/** One search result: Ask for it opens the Ask for it panel (Ebook and audiobook, Ebook, or Audiobook) */
@Composable
private fun FoundRow(
    f: BookFound,
    viewModel: BookRequestsViewModel,
    onAsk: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        BookCoverImage(f.cover, f.name, Modifier.width(64.dp).aspectRatio(BOOK_RATIO))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            Text(
                f.name,
                color = BenflixColors.Text,
                fontSize = BenflixType.lead,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val bits =
                listOfNotNull(
                    f.author.takeIf { it.isNotBlank() },
                    f.year?.toString(),
                    f.volumes?.let { "$it volumes" },
                    f.score?.let { (if (f.manga) "AniList " else "Open Library ") + "%.1f".format(it) },
                )
            if (bits.isNotEmpty()) Text(bits.joinToString(" · "), color = BenflixColors.Muted, fontSize = BenflixType.small, maxLines = 1)
            when {
                f.have != null -> {
                    MusicButton("Ready to read", BenflixIcon.CHECK, onClick = { viewModel.open(f.have) })
                }

                f.asked != null -> {
                    AskPillChip(bookPill("", f.asked), big = true)
                }

                else -> {
                    MusicButton("Ask for it", BenflixIcon.PLUS, primary = true, onClick = onAsk)
                }
            }
        }
    }
}
