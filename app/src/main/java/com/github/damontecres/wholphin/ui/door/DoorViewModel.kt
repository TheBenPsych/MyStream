package com.github.damontecres.wholphin.ui.door

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.AniListLibraries
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.LibraryKind
import com.github.damontecres.wholphin.services.NavDrawerService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.main.settings.Library
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import java.util.UUID
import javax.inject.Inject

/** How a door row draws its items */
enum class DoorRowStyle {
    POSTERS,
    TOP10,
}

/** One row inside a door. An empty row is hidden. */
data class DoorRowData(
    val key: String,
    val title: String,
    val items: List<BaseItem>,
    val style: DoorRowStyle = DoorRowStyle.POSTERS,
    /** Shows a "See all" tile at the end that opens the whole list */
    val seeAll: Boolean = false,
)

/** A "Browse by" tile at the bottom of a door */
data class DoorBrowseTile(
    val title: String,
    val destination: Destination,
)

data class DoorPageState(
    val rows: List<DoorRowData> = emptyList(),
    val browse: List<DoorBrowseTile> = emptyList(),
    /** The first load finished (rows may still be empty) */
    val loaded: Boolean = false,
    /** Phones: the "Picked for you" card */
    val pick: BaseItem? = null,
)

/**
 * Benflix redesign: the Movies and Shows doors. Both have the same layout: a top area that describes
 * the poster you're on, a few rows, then "Browse by" tiles.
 */
@HiltViewModel
class DoorViewModel
    @Inject
    constructor(
        private val data: DoorDataService,
        private val serverRepository: ServerRepository,
        private val navDrawerService: NavDrawerService,
        private val aniListLibraries: AniListLibraries,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(DoorPageState())
        val state: StateFlow<DoorPageState> = _state

        /** Which poster had focus, so coming back to the door lands on it again */
        var focusedRow: String? = null
        var focusedIndex: Int = 0

        private var door: BenflixDoor? = null
        private var loadJob: Job? = null
        private var loadedAt = 0L
        private var loadedFor: Pair<UUID, Boolean>? = null

        /** Starts (or refreshes) the door. Coming back within a short while keeps the rows as they are. */
        fun start(door: BenflixDoor) {
            val userId = serverRepository.currentUser?.id ?: return
            val key = userId to KidsMode.isActive
            val fresh = System.currentTimeMillis() - loadedAt < REFRESH_MS
            if (this.door == door && loadedFor == key && (fresh || loadJob?.isActive == true)) return
            if (loadedFor != key) {
                _state.value = DoorPageState()
                focusedRow = null
                focusedIndex = 0
            }
            this.door = door
            loadedFor = key
            loadJob?.cancel()
            loadJob =
                viewModelScope.launchIO {
                    when (door) {
                        BenflixDoor.MOVIES -> loadMovies(userId)
                        BenflixDoor.SHOWS -> loadShows(userId)
                        else -> Unit
                    }
                    loadedAt = System.currentTimeMillis()
                    _state.update { it.copy(loaded = true) }
                }
        }

        private suspend fun libraries(): List<Library> =
            navDrawerService.state.value.allLibraries
                .takeIf { it.isNotEmpty() }
                ?: withTimeoutOrNull(LIBRARY_WAIT_MS) { navDrawerService.state.first { it.allLibraries.isNotEmpty() }.allLibraries }
                ?: emptyList()

        private fun List<Library>.ofKind(vararg kinds: LibraryKind) =
            filter { LibraryKind.from(it.name, it.collectionType) in kinds }.map { it.itemId }

        /** Puts a finished row in place, keeping the page's row order */
        private fun put(
            order: List<String>,
            row: DoorRowData,
        ) {
            val clean = row.copy(items = row.items.distinctBy { it.id })
            _state.update { st ->
                val rows = (st.rows.filter { it.key != row.key } + clean).sortedBy { order.indexOf(it.key) }
                st.copy(rows = rows)
            }
        }

        private fun remove(key: String) = _state.update { st -> st.copy(rows = st.rows.filter { it.key != key }) }

        private fun putOrRemove(
            order: List<String>,
            row: DoorRowData,
        ) = if (row.items.isEmpty()) remove(row.key) else put(order, row)

        private suspend fun loadMovies(userId: UUID) {
            val libs = libraries()
            val main = libs.ofKind(LibraryKind.MOVIES)
            val anime = libs.ofKind(LibraryKind.ANIME_MOVIES)
            val standUp = libs.ofKind(LibraryKind.STANDUP)
            val easy = KidsMode.isActive
            val order =
                if (easy) {
                    listOf(KEEP, MY_LIST, NEW)
                } else {
                    listOf(KEEP, MY_LIST, NEW, TOP_10, BECAUSE, ANIME, STAND_UP)
                }
            aniListLibraries.warm(anime, userId)
            _state.update {
                it.copy(
                    browse =
                        listOfNotNull(
                            DoorBrowseTile("Genres", Destination.DoorBrowse(BenflixDoor.MOVIES.name, BROWSE_GENRES)),
                            DoorBrowseTile("Collections", Destination.DoorBrowse(BenflixDoor.MOVIES.name, BROWSE_COLLECTIONS)),
                            DoorBrowseTile("Studios", Destination.DoorBrowse(BenflixDoor.MOVIES.name, BROWSE_STUDIOS)),
                            DoorBrowseTile("All movies", Destination.DoorBrowse(BenflixDoor.MOVIES.name, BROWSE_ALL)),
                        ),
                )
            }
            coroutineScope {
                val disliked = async { data.disliked(userId) }
                launch {
                    val items = data.resumeMovies(userId)
                    putOrRemove(order, DoorRowData(KEEP, "Keep watching", items))
                }
                launch {
                    val items = data.myList(userId, BaseItemKind.MOVIE)
                    putOrRemove(order, DoorRowData(MY_LIST, "My List", items, seeAll = items.size >= DoorDataService.ROW))
                }
                launch {
                    val dislikes = disliked.await()
                    val items = data.newest(userId, main + anime + standUp, BaseItemKind.MOVIE).filter { it.id !in dislikes }
                    putOrRemove(order, DoorRowData(NEW, "New", items, seeAll = true))
                    _state.update { st -> st.copy(pick = st.pick ?: pickFrom(items)) }
                }
                if (!easy) {
                    launch {
                        val animeIds = data.idsIn(userId, anime, BaseItemKind.MOVIE)
                        val dislikes = disliked.await()
                        val items = data.top10(userId, BaseItemKind.MOVIE, animeIds, anime = false).filter { it.id !in dislikes }
                        putOrRemove(order, DoorRowData(TOP_10, "Top 10 movies today", items, style = DoorRowStyle.TOP10))
                    }
                    launch {
                        val dislikes = disliked.await()
                        val found = data.because(userId, BaseItemKind.MOVIE, dislikes)
                        if (found == null) {
                            remove(BECAUSE)
                        } else {
                            put(order, DoorRowData(BECAUSE, "Because you watched ${found.first}", found.second))
                            _state.update { st -> st.copy(pick = pickFrom(found.second) ?: st.pick) }
                        }
                    }
                    launch {
                        val dislikes = disliked.await()
                        val items = data.fromLibrary(userId, anime, BaseItemKind.MOVIE).filter { it.id !in dislikes }
                        putOrRemove(order, DoorRowData(ANIME, "Anime movies", items, seeAll = true))
                    }
                    launch {
                        val items = data.fromLibrary(userId, standUp, BaseItemKind.MOVIE)
                        putOrRemove(order, DoorRowData(STAND_UP, "Stand-up", items, seeAll = true))
                    }
                }
            }
        }

        private suspend fun loadShows(userId: UUID) {
            val libs = libraries()
            val main = libs.ofKind(LibraryKind.SHOWS)
            val anime = libs.ofKind(LibraryKind.ANIME_SHOWS)
            val easy = KidsMode.isActive
            val order =
                if (easy) {
                    listOf(KEEP, MY_LIST, NEW_SHOWS)
                } else {
                    listOf(KEEP, MY_LIST, NEW_EPISODES, NEW_SHOWS, TOP_10, BECAUSE, ANIME, TOP_10_ANIME)
                }
            aniListLibraries.warm(anime, userId)
            _state.update {
                it.copy(
                    browse =
                        listOf(
                            DoorBrowseTile("Genres", Destination.DoorBrowse(BenflixDoor.SHOWS.name, BROWSE_GENRES)),
                            DoorBrowseTile("Networks", Destination.DoorBrowse(BenflixDoor.SHOWS.name, BROWSE_STUDIOS)),
                            DoorBrowseTile("All shows", Destination.DoorBrowse(BenflixDoor.SHOWS.name, BROWSE_ALL)),
                        ),
                )
            }
            coroutineScope {
                val disliked = async { data.disliked(userId) }
                val animeIds = async { data.idsIn(userId, anime, BaseItemKind.SERIES) }
                launch {
                    val keep = data.keepWatchingEpisodes(userId)
                    putOrRemove(order, DoorRowData(KEEP, "Keep watching", keep))
                    if (!easy) {
                        val fresh = data.newEpisodes(userId, keep.mapNotNull { it.data.seriesId }.distinct())
                        putOrRemove(order, DoorRowData(NEW_EPISODES, "New episodes", fresh))
                    }
                }
                launch {
                    val items = data.myList(userId, BaseItemKind.SERIES)
                    putOrRemove(order, DoorRowData(MY_LIST, "My List", items, seeAll = items.size >= DoorDataService.ROW))
                }
                launch {
                    val dislikes = disliked.await()
                    val items = data.newest(userId, main + anime, BaseItemKind.SERIES).filter { it.id !in dislikes }
                    putOrRemove(order, DoorRowData(NEW_SHOWS, "New shows", items, seeAll = true))
                    _state.update { st -> st.copy(pick = st.pick ?: pickFrom(items)) }
                }
                if (!easy) {
                    launch {
                        val dislikes = disliked.await()
                        val items = data.top10(userId, BaseItemKind.SERIES, animeIds.await(), anime = false).filter { it.id !in dislikes }
                        putOrRemove(order, DoorRowData(TOP_10, "Top 10 shows today", items, style = DoorRowStyle.TOP10))
                    }
                    launch {
                        val dislikes = disliked.await()
                        val found = data.because(userId, BaseItemKind.SERIES, dislikes)
                        if (found == null) {
                            remove(BECAUSE)
                        } else {
                            put(order, DoorRowData(BECAUSE, "Because you watched ${found.first}", found.second))
                            _state.update { st -> st.copy(pick = pickFrom(found.second) ?: st.pick) }
                        }
                    }
                    launch {
                        val dislikes = disliked.await()
                        val items = data.fromLibrary(userId, anime, BaseItemKind.SERIES).filter { it.id !in dislikes }
                        putOrRemove(order, DoorRowData(ANIME, "Anime", items, seeAll = true))
                    }
                    launch {
                        val items = data.top10(userId, BaseItemKind.SERIES, animeIds.await(), anime = true)
                        putOrRemove(order, DoorRowData(TOP_10_ANIME, "Top 10 anime", items, style = DoorRowStyle.TOP10))
                    }
                }
            }
        }

        /** Something unwatched with a wide picture, for the phone's "Picked for you" card */
        private fun pickFrom(items: List<BaseItem>): BaseItem? =
            items
                .filter { !it.played && !it.data.backdropImageTags.isNullOrEmpty() }
                .randomOrNull()

        /** The door's main library (not anime), for its Browse by pages */
        suspend fun mainLibrary(door: BenflixDoor): Library? {
            val kind = if (door == BenflixDoor.MOVIES) LibraryKind.MOVIES else LibraryKind.SHOWS
            val libs = libraries()
            return libs.firstOrNull { LibraryKind.from(it.name, it.collectionType) == kind }
                ?: libs.firstOrNull {
                    it.collectionType ==
                        (if (door == BenflixDoor.MOVIES) CollectionType.MOVIES else CollectionType.TVSHOWS)
                }
        }

        /** Where a grid's chips are remembered: per person, per door, per list */
        fun gridKey(
            door: BenflixDoor,
            list: String,
        ) = "${serverRepository.currentUser?.id}-${door.name}-$list"

        /** Everything behind a door, for the All movies / All shows grid; the second value is the anime ids */
        suspend fun everything(door: BenflixDoor): Pair<List<BaseItem>, Set<UUID>> {
            val userId = serverRepository.currentUser?.id ?: return emptyList<BaseItem>() to emptySet()
            val libs = libraries()
            val movies = door == BenflixDoor.MOVIES
            val type = if (movies) BaseItemKind.MOVIE else BaseItemKind.SERIES
            val main = libs.ofKind(if (movies) LibraryKind.MOVIES else LibraryKind.SHOWS)
            val anime = libs.ofKind(if (movies) LibraryKind.ANIME_MOVIES else LibraryKind.ANIME_SHOWS)
            val standUp = if (movies) libs.ofKind(LibraryKind.STANDUP) else emptyList()
            return coroutineScope {
                val all = async { data.everything(userId, main + anime + standUp, type) }
                val animeIds = async { data.idsIn(userId, anime, type) }
                all.await() to animeIds.await()
            }
        }

        /** The whole list behind a row's "See all" tile */
        suspend fun fullRow(
            door: BenflixDoor,
            key: String,
        ): List<BaseItem> {
            val userId = serverRepository.currentUser?.id ?: return emptyList()
            val libs = libraries()
            val movies = door == BenflixDoor.MOVIES
            val type = if (movies) BaseItemKind.MOVIE else BaseItemKind.SERIES
            val main = libs.ofKind(if (movies) LibraryKind.MOVIES else LibraryKind.SHOWS)
            val anime = libs.ofKind(if (movies) LibraryKind.ANIME_MOVIES else LibraryKind.ANIME_SHOWS)
            val standUp = libs.ofKind(LibraryKind.STANDUP)
            return when (key) {
                MY_LIST -> {
                    data.myList(userId, type, limit = FULL)
                }

                NEW, NEW_SHOWS -> {
                    data.newest(userId, main + anime + (if (movies) standUp else emptyList()), type, limit = FULL)
                }

                ANIME -> {
                    data.fromLibrary(userId, anime, type, sortBy = ItemSortBy.SORT_NAME, limit = FULL).sortedBy { it.sortName }
                }

                STAND_UP -> {
                    data.fromLibrary(userId, standUp, type, sortBy = ItemSortBy.SORT_NAME, limit = FULL).sortedBy { it.sortName }
                }

                else -> {
                    state.value.rows
                        .firstOrNull { it.key == key }
                        ?.items
                        .orEmpty()
                }
            }
        }

        companion object {
            const val KEEP = "keep"
            const val MY_LIST = "mylist"
            const val NEW = "new"
            const val NEW_EPISODES = "newepisodes"
            const val NEW_SHOWS = "newshows"
            const val TOP_10 = "top10"
            const val TOP_10_ANIME = "top10anime"
            const val BECAUSE = "because"
            const val ANIME = "anime"
            const val STAND_UP = "standup"

            const val BROWSE_GENRES = "genres"
            const val BROWSE_COLLECTIONS = "collections"
            const val BROWSE_STUDIOS = "studios"
            const val BROWSE_ALL = "all"

            private const val REFRESH_MS = 60_000L
            private const val LIBRARY_WAIT_MS = 5_000L
            private const val FULL = 300
        }
    }
