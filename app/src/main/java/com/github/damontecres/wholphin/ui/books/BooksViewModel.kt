package com.github.damontecres.wholphin.ui.books

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.services.BenflixBooks
import com.github.damontecres.wholphin.services.BookGroup
import com.github.damontecres.wholphin.services.BookLibrary
import com.github.damontecres.wholphin.services.BookTile
import com.github.damontecres.wholphin.services.BooksHome
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.launchIO
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject

/** The places in the Books side menu (TV) or chips (phones), in the approved order */
enum class BooksPlace(
    val label: String,
) {
    HOME("Home"),
    MANGA("Manga"),
    COMICS("Comics"),
    BOOKS("Books"),
    AUDIOBOOKS("Audiobooks"),
    SERIES("Series"),
    AUTHORS("Authors"),
    WANT("Want to read"),
    FINISHED("Finished"),
    ;

    /** The book kind a section page shows (null = not a one-kind page) */
    val kind: String?
        get() =
            when (this) {
                MANGA -> "manga"
                COMICS -> "comic"
                BOOKS -> "book"
                AUDIOBOOKS -> "audiobook"
                else -> null
            }
}

/** The chips at the top of a section page */
enum class BookFilter(
    val label: String,
) {
    ALL("All"),
    NOT("Not started"),
    READING("In progress"),
    DONE("Finished"),
}

enum class BookSort(
    val label: String,
) {
    ADDED("Recently added"),
    NAME("A to Z"),
    AUTHOR("Author"),
    READ("Last read"),
}

data class BooksState(
    val loaded: Boolean = false,
    val home: BooksHome? = null,
    val library: BookLibrary? = null,
    val series: List<BookGroup>? = null,
    val authors: List<BookGroup>? = null,
    val problem: String? = null,
)

/** The Books door: Home rows, every book (for the section pages), series and authors. Loads once, refreshes on return. */
@HiltViewModel
class BooksViewModel
    @Inject
    constructor(
        val books: BenflixBooks,
        private val serverRepository: com.github.damontecres.wholphin.data.ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(BooksState())
        val state: StateFlow<BooksState> = _state

        /** Kept here (not in the page) so Back from a book lands on the same section, chip and card */
        val place = MutableStateFlow(BooksPlace.HOME)
        val filter = MutableStateFlow(BookFilter.ALL)
        val sort = MutableStateFlow(BookSort.ADDED)

        /** The group (a series or an author) open on the Series / Authors page, if any */
        val group = MutableStateFlow<BookGroup?>(null)
        var lastKey: String? = null

        private var loadedAt = 0L

        val isKids: Boolean get() = KidsMode.isActive

        private fun memoryKey() = serverRepository.currentUser?.id?.let { "$it-${KidsMode.level}" }

        fun start() {
            // Benflix speed: last time's Books page shows at once (this run of the app) while it refreshes
            if (!_state.value.loaded) {
                memoryKey()?.let { memory[it] }?.let { (at, saved) ->
                    _state.value = saved
                    if (System.currentTimeMillis() - at < RELOAD_MS) loadedAt = at
                }
            }
            if (_state.value.loaded && System.currentTimeMillis() - loadedAt < RELOAD_MS) {
                // Places change as soon as you read, so Home's rows are always looked at again
                viewModelScope.launchIO { loadHome() }
                return
            }
            viewModelScope.launchIO { loadAll() }
        }

        fun retry() {
            loadedAt = 0L
            _state.value = BooksState()
            start()
        }

        private suspend fun loadHome() {
            safe { books.home() }?.let { h -> _state.update { it.copy(home = h) } }
            memoryKey()?.let { key -> memory[key]?.let { (at, _) -> memory[key] = at to _state.value } }
        }

        private suspend fun loadAll(): Unit =
            kotlinx.coroutines.coroutineScope {
                // Benflix speed: every book, series and author are asked for at the same time as Home's rows,
                // and the page opens as soon as Home's rows are in (it used to wait for the whole library too)
                val lib = async { safe { books.library() } }
                val series = async { safe { books.series() } }
                val authors = async { safe { books.authors() } }
                val home =
                    try {
                        books.home()
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Books: couldn't load home")
                        lib.cancel()
                        series.cancel()
                        authors.cancel()
                        // Last time's page stays up if there is one
                        _state.update {
                            if (it.home != null) {
                                it.copy(loaded = true)
                            } else {
                                it.copy(loaded = true, problem = ex.message ?: "Couldn't reach Tower")
                            }
                        }
                        return@coroutineScope
                    }
                _state.update { it.copy(home = home, problem = null, loaded = true) }
                loadedAt = System.currentTimeMillis()
                lib.await()?.let { l -> _state.update { it.copy(library = l) } }
                val s = series.await()
                val a = authors.await()
                _state.update { it.copy(series = s ?: it.series, authors = a ?: it.authors) }
                val key = memoryKey() ?: return@coroutineScope
                memory[key] = System.currentTimeMillis() to _state.value
            }

        /** The books a section page shows, with its chip and sort */
        fun sectionTiles(
            lib: BookLibrary,
            p: BooksPlace,
            f: BookFilter,
            s: BookSort,
        ): List<BookTile> {
            val base =
                when (p) {
                    BooksPlace.WANT -> lib.titles.filter { it.want }
                    BooksPlace.FINISHED -> lib.titles.filter { it.status == "done" }
                    else -> p.kind?.let { k -> lib.titles.filter { it.kind == k } } ?: lib.titles
                }
            val chipped =
                when (f) {
                    BookFilter.ALL -> base
                    BookFilter.NOT -> base.filter { it.status == "not" }
                    BookFilter.READING -> base.filter { it.status == "reading" }
                    BookFilter.DONE -> base.filter { it.status == "done" }
                }
            return when (s) {
                BookSort.ADDED -> {
                    chipped.sortedByDescending { it.added }
                }

                BookSort.NAME -> {
                    chipped.sortedBy { sortName(it.name) }
                }

                BookSort.AUTHOR -> {
                    chipped.sortedWith(
                        compareBy({ sortName(it.author) }, { it.series.lowercase() }, { it.seriesIndex ?: 0.0 }),
                    )
                }

                BookSort.READ -> {
                    chipped.sortedByDescending { it.lastAt }
                }
            }
        }

        /** The menu places that have something in them (Manga and Comics hide until some exist) */
        fun places(counts: Map<String, Int>): List<BooksPlace> =
            BooksPlace.entries.filter {
                when (it) {
                    BooksPlace.MANGA -> (counts["manga"] ?: 0) > 0
                    BooksPlace.COMICS -> (counts["comic"] ?: 0) > 0
                    else -> true
                }
            }

        fun open(tile: BookTile) {
            lastKey = tile.id
            navigationManager.navigateTo(
                com.github.damontecres.wholphin.ui.nav.Destination
                    .BookTitle(tile.id),
            )
        }

        private suspend fun <T> safe(block: suspend () -> T): T? =
            try {
                block()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Books")
                null
            }

        companion object {
            private const val RELOAD_MS = 5 * 60_000L

            /** Each profile's Books page in memory for this run of the app, with when it was loaded */
            private val memory = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, BooksState>>()
        }
    }

/** "The Hobbit" sorts under H */
fun sortName(name: String): String =
    name
        .lowercase()
        .removePrefix("the ")
        .removePrefix("a ")
        .removePrefix("an ")
