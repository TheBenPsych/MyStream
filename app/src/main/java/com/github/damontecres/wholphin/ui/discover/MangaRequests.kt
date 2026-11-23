package com.github.damontecres.wholphin.ui.discover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.BenflixBooks
import com.github.damontecres.wholphin.services.BookCard
import com.github.damontecres.wholphin.services.BookRequest
import com.github.damontecres.wholphin.services.BookRequests
import com.github.damontecres.wholphin.services.MangaAbout
import com.github.damontecres.wholphin.services.MangaDiscover
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.books.BOOK_RATIO
import com.github.damontecres.wholphin.ui.books.BookCoverImage
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.detail.title.ScoreBox
import com.github.damontecres.wholphin.ui.detail.title.ScorePill
import com.github.damontecres.wholphin.ui.detail.title.StripPart
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.music.MusicButton
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.ScrollToTopBringIntoViewSpec
import com.github.damontecres.wholphin.ui.util.scrollAsOneBlock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Requests > Manga (Ben 2026-11-22, Idea Box 26): popular manga from AniList, genres and a page for each one,
 * like the Anime tab. Asking goes to Ben; his yes goes to Harbor, which gets it from the manga sites and puts
 * it in Books > Manga. Kids never see Requests.
 */
@HiltViewModel
class MangaRequestsViewModel
    @Inject
    constructor(
        private val books: BenflixBooks,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        val lists = MutableStateFlow<MangaDiscover?>(null)
        val genreItems = MutableStateFlow<List<BookCard>?>(null)
        val requests = MutableStateFlow<BookRequests?>(null)
        val query = MutableStateFlow("")
        val found = MutableStateFlow<List<BookCard>?>(null)
        val note = MutableStateFlow<String?>(null)
        val problem = MutableStateFlow<String?>(null)
        private var searchJob: Job? = null

        val isAdmin: Boolean get() = serverRepository.currentUserDto?.policy?.isAdministrator == true

        private fun <T> safely(
            what: String,
            block: suspend () -> T,
            onDone: (T) -> Unit,
        ) {
            viewModelScope.launchIO {
                try {
                    onDone(block())
                    problem.value = null
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, what)
                    problem.value = ex.message ?: "Couldn't reach Tower"
                }
            }
        }

        fun load() = safely("Manga lists", { books.mangaDiscover() }) { lists.value = it }

        fun loadGenre(genre: String) {
            genreItems.value = null
            safely("Manga genre", { books.mangaGenre(genre) }) { genreItems.value = it }
        }

        suspend fun refresh() {
            try {
                requests.value = books.mangaRequests()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Manga requests")
            }
        }

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
                    try {
                        found.value = books.mangaSearch(text.trim())
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        problem.value = ex.message ?: "Couldn't search right now"
                    }
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

        fun quickAsk(c: BookCard) =
            act {
                val id = c.key.removePrefix("al:").toIntOrNull() ?: return@act
                note.value = books.mangaAsk(id)
                load()
            }

        fun answer(
            r: BookRequest,
            yes: Boolean,
        ) = act { books.mangaAnswer(r.id, yes) }

        fun retry(r: BookRequest) = act { books.mangaRetry(r.id) }

        fun open(c: BookCard) {
            val have = c.have
            if (have != null) {
                navigationManager.navigateTo(Destination.BookTitle(have))
            } else {
                c.key
                    .removePrefix("al:")
                    .toIntOrNull()
                    ?.let { navigationManager.navigateTo(Destination.MangaAbout(it, c.name)) }
            }
        }

        fun openShelf(titleId: String) = navigationManager.navigateTo(Destination.BookTitle(titleId))
    }

@Composable
private fun MangaMuted(text: String) {
    Text(text, color = BenflixColors.Muted, fontSize = BenflixType.body, modifier = Modifier.padding(16.dp))
}

/** The Manga tab: rows of covers, like the Anime tab */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaShelf(
    top: Dp = 0.dp,
    viewModel: MangaRequestsViewModel = hiltViewModel(key = "manga-requests"),
) {
    val isTv = rememberIsTv()
    val d by viewModel.lists.collectAsState()
    val r0 by viewModel.requests.collectAsState()
    val genreItems by viewModel.genreItems.collectAsState()
    val query by viewModel.query.collectAsState()
    val found by viewModel.found.collectAsState()
    val note by viewModel.note.collectAsState()
    val problem by viewModel.problem.collectAsState()
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        viewModel.load()
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
    val r = r0 ?: BookRequests(emptyList(), emptyList(), emptyList(), true, emptyList())
    var picked by rememberSaveable { mutableStateOf("r0") }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val waitingIds = r.waiting.map { it.id }.toSet()
    val yours = (if (admin) r.mine + r.others else r.mine).distinctBy { it.id }.filter { it.id !in waitingIds }
    val line: (BookRequest, Boolean) -> AskLine = { b, answer ->
        AskLine(
            key = "manga-${b.id}",
            title = b.name,
            sub = listOfNotNull(b.who.takeIf { admin && it.isNotBlank() }, "manga").joinToString(" · "),
            cover = b.cover,
            pill = bookPill(b.state, b.words),
            onOpen = b.titleId?.let { id -> { viewModel.openShelf(id) } },
            onYes = if (answer) ({ viewModel.answer(b, true) }) else null,
            onNo = if (answer) ({ viewModel.answer(b, false) }) else null,
            onRetry = if (b.canRetry) ({ viewModel.retry(b) }) else null,
            who = b.who.takeIf { admin && it.isNotBlank() },
        )
    }
    val searchList: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            if (isTv) RequestsSearchBox(query = query, onQuery = viewModel::search, placeholder = "Search any manga")
            val f = found
            when {
                query.trim().length < 2 -> MangaMuted("Type a manga's name, in English or Japanese.")
                f == null -> MangaMuted("Looking…")
                f.isEmpty() -> MangaMuted("Nothing found. Try the Japanese name, or fewer words.")
                else -> BookWall(BookList("found", "Found", f), f, viewModel::open, viewModel::quickAsk, {}, 0, false, {})
            }
        }
    }
    OnRequestsSearch { searchOpen = true }
    if (isTv && searchOpen) {
        BackHandler { searchOpen = false }
        searchList(Modifier.fillMaxSize().padding(top = top))
        return
    }
    val rows = d?.rows.orEmpty()
    // Your asks and Waiting open over the rows, like a page of their own (Back comes home to the rows)
    if (picked == PICK_YOURS || picked == PICK_WAITING) {
        BackHandler { picked = "r0" }
        Column(Modifier.fillMaxSize().padding(top = top)) {
            if (picked == PICK_YOURS) {
                AskLinesWall(
                    lines = yours.map { line(it, false) },
                    grouped = true,
                    empty =
                        if (isTv) {
                            "Nothing asked for yet. OK on a cover shows the manga; hold OK to ask for it straight away."
                        } else {
                            "Nothing asked for yet. Tap a cover to see the manga; press and hold to ask for it straight away."
                        },
                )
            } else {
                AskLinesWall(lines = r.waiting.map { line(it, true) }, grouped = false, empty = "Nobody is waiting on you right now.")
            }
        }
        return
    }
    // Glowfin new look (Ben approved 2026-11-22): Manga lands straight on a row, like Anime. Rows from Tower
    // (Trending on AniList, Trending on MyAnimeList, ...), then Genres pills with the picked genre's row under them.
    // On TV the cover you're on fills the page behind and the top describes it.
    var focused by remember { mutableStateOf<BookCard?>(null) }
    var shown by remember { mutableStateOf<BookCard?>(null) }
    LaunchedEffect(focused) {
        val target = focused ?: return@LaunchedEffect
        if (shown != null) delay(MANGA_SHOW_DELAY_MS)
        shown = target
    }
    val described = shown ?: rows.firstOrNull()?.second?.firstOrNull()
    val edge = 16.dp
    val onFocus: ((BookCard) -> Unit)? = if (isTv) ({ focused = it }) else null
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val topHeight =
            (maxHeight * 0.52f).coerceIn(top + 116.dp, (maxHeight - 250.dp).coerceAtLeast(top + 116.dp))
        if (isTv) RequestArtUrl(described?.cover)
        if (isTv) {
            described?.score?.let { score ->
                ScorePill(
                    rating = null,
                    scores = listOf(StripPart("anilist", "%.1f".format(score))),
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = top + 2.dp),
                )
            }
        }
        Column(Modifier.fillMaxSize().padding(top = if (isTv) 0.dp else top)) {
            if (!isTv) RequestsSearchBox(query = query, onQuery = viewModel::search, placeholder = "Search any manga")
            if (!isTv && query.trim().length >= 2) {
                searchList(Modifier.fillMaxWidth().weight(1f))
                return@Column
            }
            if (isTv) {
                Box(Modifier.fillMaxWidth().height(topHeight)) {
                    described?.let { c ->
                        RequestTopText(
                            title = c.name,
                            facts = listOfNotNull("Manga", c.year?.toString()).joinToString(" · "),
                            tags =
                                listOf(
                                    when {
                                        c.have != null -> RqTag(RqTagKind.HERE, "On Glowfin")
                                        c.asked != null -> RqTag(RqTagKind.WAITING, c.asked)
                                        else -> RqTag(RqTagKind.NOT_YET, "Not on Glowfin yet")
                                    },
                                ),
                            story = c.desc,
                            hint = if (c.have == null && c.asked == null) ASK_HINT else "OK opens it",
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = top + 2.dp),
                        )
                    }
                }
            }
            (note ?: problem)?.let {
                Text(
                    it,
                    color = if (note != null) BenflixColors.Books else BenflixColors.Warning,
                    fontSize = BenflixType.body,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            val defaultSpec = LocalBringIntoViewSpec.current
            val toTop = remember { ScrollToTopBringIntoViewSpec(0f) }
            CompositionLocalProvider(LocalBringIntoViewSpec provides if (isTv) toTop else defaultSpec) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(if (isTv) 18.dp else 14.dp),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 48.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    if (d == null) {
                        item(key = "m-loading") {
                            MangaMuted(if (problem != null) "Couldn't load the manga lists." else "Loading the manga lists…")
                        }
                    }
                    itemsIndexed(rows, key = { i, row -> "mrow-$i-${row.first}" }) { _, row ->
                        CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                            CoverRow(
                                row.first,
                                row.second,
                                isTv,
                                onOpen = viewModel::open,
                                onAsk = viewModel::quickAsk,
                                edge = edge,
                                onFocus = onFocus,
                                modifier = Modifier.scrollAsOneBlock(),
                            )
                        }
                    }
                    val genres = d?.genres.orEmpty()
                    if (genres.isNotEmpty()) {
                        item(key = "m-genres") {
                            CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                                RequestChipRow("Genres") {
                                    items(genres, key = { "mg-$it" }) { g ->
                                        RequestsChip(g, g == genre, {
                                            genre = g
                                            viewModel.loadGenre(g)
                                        })
                                    }
                                }
                            }
                        }
                    }
                    genre?.let { g ->
                        item(key = "m-genre-row") {
                            CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                                val items = genreItems
                                if (items == null) {
                                    MangaMuted("Loading $g…")
                                } else {
                                    CoverRow(
                                        g,
                                        items,
                                        isTv,
                                        onOpen = viewModel::open,
                                        onAsk = viewModel::quickAsk,
                                        edge = edge,
                                        onFocus = onFocus,
                                        modifier = Modifier.scrollAsOneBlock(),
                                    )
                                }
                            }
                        }
                    }
                    item(key = "m-asks") {
                        CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                            RequestChipRow("Your manga asks") {
                                item(key = "m-yours") { RequestsChip("Asked for", false, { picked = PICK_YOURS }, count = yours.size) }
                                if (admin) {
                                    item(key = "m-waiting") {
                                        RequestsChip("Waiting for you", false, { picked = PICK_WAITING }, count = r.waiting.size)
                                    }
                                }
                                if (isTv) item(key = "m-search") { RequestsChip("Search any manga", false, { searchOpen = true }) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val MANGA_SHOW_DELAY_MS = 450L

// ---------- One manga's page ----------

@HiltViewModel
class MangaAboutViewModel
    @Inject
    constructor(
        private val books: BenflixBooks,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        val about = MutableStateFlow<MangaAbout?>(null)
        val problem = MutableStateFlow<String?>(null)
        val note = MutableStateFlow<String?>(null)
        private var id = 0

        val isAdmin: Boolean get() = serverRepository.currentUserDto?.policy?.isAdministrator == true

        fun load(id: Int) {
            this.id = id
            viewModelScope.launchIO {
                try {
                    about.value = books.mangaAbout(id)
                    problem.value = null
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    problem.value = ex.message ?: "Couldn't look it up"
                }
            }
        }

        fun ask() {
            viewModelScope.launchIO {
                note.value =
                    try {
                        books.mangaAsk(id)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        ex.message ?: "Couldn't ask for it."
                    }
                load(id)
            }
        }

        fun open(c: BookCard) {
            val have = c.have
            if (have != null) {
                navigationManager.navigateTo(Destination.BookTitle(have))
            } else {
                c.key
                    .removePrefix("al:")
                    .toIntOrNull()
                    ?.let { navigationManager.navigateTo(Destination.MangaAbout(it, c.name)) }
            }
        }
    }

/** A manga's page in Requests: the cover, its names, the story, the AniList score, Ask for it, More like this */
@Composable
fun MangaAboutPage(
    id: Int,
    name: String,
    modifier: Modifier = Modifier,
    viewModel: MangaAboutViewModel = hiltViewModel(key = "manga-about-$id"),
) {
    LifecycleResumeEffect(id) {
        viewModel.load(id)
        onPauseOrDispose { }
    }
    val a by viewModel.about.collectAsState()
    val problem by viewModel.problem.collectAsState()
    val note by viewModel.note.collectAsState()
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp
    val edge = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge
    val about = a
    if (about == null) {
        Column(Modifier.fillMaxSize().padding(edge), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(name, color = BenflixColors.Text, fontSize = BenflixType.hero, fontWeight = FontWeight.ExtraBold)
            Text(problem ?: "Looking it up…", color = BenflixColors.Muted, fontSize = BenflixType.body)
        }
        return
    }
    val mainFocus = remember { FocusRequester() }
    LaunchedEffect(about.card.key) { mainFocus.tryRequestFocus() }
    val admin = viewModel.isAdmin
    val have = about.card.have
    val asked = about.card.asked
    val words: @Composable (Modifier) -> Unit = { m ->
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = m) {
            Text("MANGA", color = BenflixColors.Books, fontSize = BenflixType.tag, fontWeight = FontWeight.ExtraBold)
            Text(
                about.card.name,
                color = BenflixColors.Text,
                fontSize = BenflixType.hero,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            about.otherName?.let { Text(it, color = BenflixColors.Muted, fontSize = BenflixType.body) }
            Text(
                listOfNotNull(
                    about.author.ifBlank { null },
                    about.card.year?.toString(),
                    about.status.ifBlank { null },
                    about.chapters?.let { "$it chapters" } ?: about.volumes?.let { "$it volumes" },
                    about.genres.joinToString(" · ").ifBlank { null },
                ).joinToString("   "),
                color = BenflixColors.Soft,
                fontSize = BenflixType.body,
            )
            about.card.score?.let { ScoreBox("%.1f".format(it), "AniList", BenflixType.body) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                when {
                    have != null -> {
                        MusicButton(
                            "Read on Glowfin",
                            BenflixIcon.BOOK,
                            onClick = { viewModel.navigationManager.navigateTo(Destination.BookTitle(have)) },
                            modifier = Modifier.focusRequester(mainFocus),
                        )
                    }

                    asked != null -> {
                        MusicButton(asked, BenflixIcon.CHECK, onClick = {}, modifier = Modifier.focusRequester(mainFocus))
                    }

                    else -> {
                        MusicButton(
                            if (admin) "Ask for it" else "Ask Ben",
                            BenflixIcon.PLUS,
                            onClick = viewModel::ask,
                            modifier = Modifier.focusRequester(mainFocus),
                        )
                    }
                }
            }
            note?.let { Text(it, color = BenflixColors.Books, fontSize = BenflixType.body, fontWeight = FontWeight.Bold) }
            if (asked != null && have == null) {
                Text(
                    if (about.askState == "waiting") {
                        "Ben says yes first."
                    } else {
                        "Harbor is getting it. Chapters show in Books > Manga as they arrive."
                    },
                    color = BenflixColors.Muted,
                    fontSize = BenflixType.small,
                )
            }
            if (about.story.isNotBlank()) {
                Text(about.story, color = BenflixColors.Soft, fontSize = BenflixType.body, modifier = Modifier.widthIn(max = 900.dp))
            }
        }
    }
    val coverWidth = if (wide) 230.dp else 160.dp
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(if (wide) 26.dp else 18.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "top") {
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(36.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = edge)) {
                    BookCoverImage(about.card.cover, about.card.name, Modifier.width(coverWidth).aspectRatio(BOOK_RATIO))
                    words(Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = edge)) {
                    BookCoverImage(about.card.cover, about.card.name, Modifier.width(coverWidth).aspectRatio(BOOK_RATIO))
                    words(Modifier.fillMaxWidth())
                }
            }
        }
        if (about.more.isNotEmpty()) {
            item(key = "more") { CoverRow("More like this", about.more, wide, onOpen = viewModel::open, onAsk = null, edge = edge) }
        }
    }
}
