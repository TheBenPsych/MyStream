package com.github.damontecres.wholphin.ui.discover

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.BenflixBooks
import com.github.damontecres.wholphin.services.BookFound
import com.github.damontecres.wholphin.services.BookRequest
import com.github.damontecres.wholphin.services.BookRequests
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.books.BOOK_RATIO
import com.github.damontecres.wholphin.ui.books.BookChip
import com.github.damontecres.wholphin.ui.books.BookCoverImage
import com.github.damontecres.wholphin.ui.components.BenflixIcon
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
 * Requests > Books (approved): the rows (bookShelfItems in BookDiscover.kt), and search any book or manga. Books ask for Ebook, Audiobook or Both (Both is
 * picked first); manga go on Ben's "Manga to add" list. Everyone else's asks wait for Ben's yes. Below the
 * search: what you asked for with its status, and (Ben) what's waiting, everyone else's and problems.
 */
@Composable
fun BooksShelf(
    viewModel: BookRequestsViewModel = hiltViewModel(),
    shelvesViewModel: BookShelvesViewModel = hiltViewModel(key = "book-shelves-main"),
) {
    val shelves by shelvesViewModel.shelves.collectAsState()
    val shelvesProblem by shelvesViewModel.problem.collectAsState()
    val shelvesNote by shelvesViewModel.note.collectAsState()
    // Coming back from a book's details page: the rows pick up anything just asked for
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
    val config = LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp
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
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "search") {
            RequestsSearchBox(query = query, onQuery = viewModel::search, placeholder = "Search any book, author or manga")
        }
        note?.let {
            item(
                key = "note",
            ) { Text(it, color = BenflixColors.Books, fontSize = BenflixType.body, fontWeight = FontWeight.Bold) }
        }
        shelvesNote?.let {
            item(key = "shelves-note") { Text(it, color = BenflixColors.Books, fontSize = BenflixType.body, fontWeight = FontWeight.Bold) }
        }
        problem?.let { item(key = "problem") { Text(it, color = BenflixColors.Warning, fontSize = BenflixType.small) } }
        requests?.problems?.forEach { line ->
            item(key = "p-$line") { Text(line, color = BenflixColors.Warning, fontSize = BenflixType.small) }
        }
        if (requests?.connected == false && admin) {
            item(key = "not-connected") {
                Text(
                    "Tower's helper can't reach Chaptarr yet, so asks wait here. Run the helper setup on Tower once more and it finds Chaptarr by itself.",
                    color = BenflixColors.Warning,
                    fontSize = BenflixType.small,
                )
            }
        }
        if (query.trim().length >= 2) {
            val f = found
            when {
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
                        items(f.first, key = { "b" + it.key }) { FoundRow(it, viewModel) }
                    }
                    if (f.second.isNotEmpty()) {
                        item(key = "manga-title") { Heading("Manga") }
                        items(f.second, key = { "m" + it.key }) { FoundRow(it, viewModel) }
                    }
                }
            }
            return@LazyColumn
        }
        val r = requests ?: BookRequests(emptyList(), emptyList(), emptyList(), emptyList(), true, emptyList())
        if (admin && r.waiting.isNotEmpty()) {
            section("Waiting for you", r.waiting) { RequestRow(it, viewModel, admin, answer = true) }
        }
        // The approved rows: Book of the week, Top 10, your series and authors, NYT, Trending, genres, awards, audiobooks
        bookShelfItems(
            d = shelves,
            problem = shelvesProblem,
            admin = admin,
            wide = wide,
            onOpen = shelvesViewModel::open,
            onAsk = shelvesViewModel::quickAsk,
            onGenre = shelvesViewModel::openGenre,
            onRetry = { shelvesViewModel.load(null) },
        )
        section(if (admin) "What you asked for" else "My books", r.mine) { RequestRow(it, viewModel, admin) }
        if (r.mine.isEmpty()) {
            item(key = "mine-none") {
                Muted(
                    if (isTv) {
                        "OK on a cover shows the book. Hold OK to ask for it straight away. You'll get a note on Home when it's here."
                    } else {
                        "Tap a cover to see the book. Press and hold to ask for it straight away. You'll get a note on Home when it's here."
                    },
                )
            }
        }
        if (admin) {
            section("Manga to add", r.mangaToAdd) { RequestRow(it, viewModel, admin) }
            section("Everyone else's", r.others) { RequestRow(it, viewModel, admin) }
        }
    }
}

private fun LazyListScope.section(
    title: String,
    list: List<BookRequest>,
    row: @Composable (BookRequest) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "t-$title") { Heading(title) }
    items(list, key = { title + it.id }) { row(it) }
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

/** One search result: Ebook / Audiobook / Both and Ask (books), or Ask (manga) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoundRow(
    f: BookFound,
    viewModel: BookRequestsViewModel,
) {
    var pick by remember(f.key) { mutableStateOf("both") }
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
                    MusicButton("On Benflix", BenflixIcon.CHECK, onClick = { viewModel.open(f.have) })
                }

                f.asked != null -> {
                    Pill(f.asked, BenflixColors.Soft)
                }

                f.manga -> {
                    MusicButton("Ask for it", BenflixIcon.PLUS, primary = true, onClick = { viewModel.ask(f, emptyList()) })
                }

                else -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BookChip("Ebook", pick == "ebook", onClick = { pick = "ebook" })
                        BookChip("Audiobook", pick == "audiobook", onClick = { pick = "audiobook" })
                        BookChip("Both", pick == "both", onClick = { pick = "both" })
                        MusicButton(
                            "Ask for it",
                            BenflixIcon.PLUS,
                            primary = true,
                            onClick = {
                                viewModel.ask(f, if (pick == "both") listOf("ebook", "audiobook") else listOf(pick))
                            },
                        )
                    }
                }
            }
        }
    }
}

/** One request: cover, name, what kind, a status pill, and the buttons that fit */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RequestRow(
    r: BookRequest,
    viewModel: BookRequestsViewModel,
    admin: Boolean,
    answer: Boolean = false,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        BookCoverImage(r.cover, r.name, Modifier.width(52.dp).aspectRatio(BOOK_RATIO))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f).widthIn(max = 900.dp)) {
            Text(
                r.name,
                color = BenflixColors.Text,
                fontSize = BenflixType.body,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val kinds =
                when {
                    r.state == "manga" -> "Manga"
                    r.types.containsAll(listOf("ebook", "audiobook")) -> "Ebook + audiobook"
                    r.types.contains("audiobook") -> "Audiobook"
                    else -> "Ebook"
                }
            Text(
                listOfNotNull(
                    r.author.takeIf {
                        it.isNotBlank()
                    },
                    kinds,
                    r.who.takeIf { admin && it.isNotBlank() }?.let { "asked by $it" },
                ).joinToString(" · "),
                color = BenflixColors.Muted,
                fontSize = BenflixType.small,
                maxLines = 1,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(r.words.ifBlank { r.state }, stateColor(r.state))
                if (answer) {
                    MusicButton("Yes, get it", BenflixIcon.CHECK, primary = true, onClick = { viewModel.answer(r, true) })
                    MusicButton("No", BenflixIcon.CLOSE, onClick = { viewModel.answer(r, false) })
                }
                if (r.state == "ready" && r.titleId != null) {
                    MusicButton("Open", BenflixIcon.BOOK, onClick = { viewModel.open(r.titleId) })
                }
                if (r.canRetry) MusicButton("Look again", BenflixIcon.REFRESH, onClick = { viewModel.retry(r) })
                if (!answer) MusicButton(null, BenflixIcon.TRASH, onClick = { viewModel.forget(r) })
            }
            r.problem?.let { Text(it, color = BenflixColors.Warning, fontSize = BenflixType.tag) }
        }
    }
}

private fun stateColor(state: String) =
    when (state) {
        "ready" -> BenflixColors.Ok
        "downloading", "searching" -> BenflixColors.Books
        "cant", "problem", "no" -> BenflixColors.CantFind
        else -> BenflixColors.Soft
    }

@Composable
private fun Pill(
    text: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Text(
        text = text,
        color = color,
        fontSize = BenflixType.small,
        fontWeight = FontWeight.Bold,
        modifier =
            Modifier
                .background(BenflixColors.Raised, BenflixShapes.Pill)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
