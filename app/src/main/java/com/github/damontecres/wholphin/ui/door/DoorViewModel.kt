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
@kotlinx.serialization.Serializable
enum class DoorRowStyle {
    POSTERS,
    TOP10,

    /** Titles that aren't on the server yet (Trending, not on Glowfin): request cards */
    DISCOVER,
}

/** One row inside a door. An empty row is hidden. */
@kotlinx.serialization.Serializable
data class DoorRowData(
    val key: String,
    val title: String,
    val items: List<BaseItem>,
    val style: DoorRowStyle = DoorRowStyle.POSTERS,
    /** Shows a "See all" tile at the end that opens the whole list */
    val seeAll: Boolean = false,
    /** [DoorRowStyle.DISCOVER] rows: the titles to ask for */
    val discover: List<com.github.damontecres.wholphin.data.model.DiscoverItem> = emptyList(),
) {
    val isEmpty: Boolean get() = items.isEmpty() && discover.isEmpty()
}

/** A "Browse by" tile at the bottom of a door */
@kotlinx.serialization.Serializable
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
    /** Every row this door can have, in page order */
    val order: List<String> = emptyList(),
    /** Rows still loading: they show as grey placeholder rows in their place, so nothing jumps (Ben) */
    val pending: Set<String> = emptySet(),
    /** Nothing came back and the server's libraries couldn't be read either: it's out of reach, not empty */
    val offline: Boolean = false,
)

/**
 * Benflix speed: what a door showed last time, saved in memory and on disk, so the door opens straight
 * away (even right after an update) while fresh rows load behind it.
 */
@kotlinx.serialization.Serializable
data class DoorSnapshot(
    val rows: List<DoorRowData>,
    val browse: List<DoorBrowseTile>,
    val order: List<String>,
    val pick: BaseItem? = null,
    val savedAt: Long = 0L,
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
        private val hiddenGenresStore: com.github.damontecres.wholphin.services.HiddenGenresStore,
        private val diskCache: com.github.damontecres.wholphin.services.DiskCache,
        @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(DoorPageState())
        val state: StateFlow<DoorPageState> = _state

        /** Which poster had focus, so coming back to the door lands on it again */
        var focusedRow: String? = null
        var focusedIndex: Int = 0

        /** False until you press a button or touch the page; until then the door keeps you on the first card of the top row */
        var userMoved = false

        private var door: BenflixDoor? = null

        /** Glowfin: this model is Home's rows (no door) */
        private var home = false
        private var loadJob: Job? = null
        private var loadedAt = 0L
        private var loadedFor: Pair<UUID, String>? = null

        /** The rows on screen came from last time's snapshot (fresh ones are loading behind them) */
        private var fromSnapshot = false

        /**
         * Rows that came back empty while last time's rows are on screen. A plain synchronized set:
         * ConcurrentHashMap's key set needs Android 7, and Benflix runs on 6.
         */
        private val emptied: MutableSet<String> =
            java.util.Collections
                .synchronizedSet(mutableSetOf())

        /** Whether any row came back fresh this load */
        @Volatile
        private var gotFresh = false

        /** The "Picked for you" title this load chose (shown next time if last time's is on screen now) */
        @Volatile
        private var nextPick: BaseItem? = null

        /** Fresh copies of the shuffled rows, held back while last time's are on screen */
        private val freshShuffled = java.util.concurrent.ConcurrentHashMap<String, DoorRowData>()

        /** Glowfin: starts (or refreshes) Home's rows: the same top area and rows as the doors */
        fun startHome() {
            home = true
            start(null)
        }

        /** Starts (or refreshes) the door. Coming back within a short while keeps the rows as they are. */
        fun start(door: BenflixDoor?) {
            val userId = serverRepository.currentUser?.id ?: return
            if (door == null && !home) return
            // Each profile (and Kids, Big kids, Guest) keeps its own rows; picking rows in Settings loads again
            val key = userId to "${KidsMode.level}-${PageRows.changes.intValue}"
            val fresh = System.currentTimeMillis() - loadedAt < REFRESH_MS
            if (this.door == door && loadedFor == key && (fresh || loadJob?.isActive == true)) return
            // The first start keeps a spot recalled from DoorMemory; another profile starts at the top
            if (loadedFor != null && loadedFor != key) {
                _state.value = DoorPageState()
                focusedRow = null
                focusedIndex = 0
                userMoved = false
            }
            this.door = door
            loadedFor = key
            val snapKey = snapshotKey(door, userId)
            // Benflix speed: every trip through a door used to start from nothing and wait for ~10 server
            // answers while the door colour held the screen. Last time's rows now show at once instead.
            if (_state.value.rows.isEmpty()) {
                val saved = memory[snapKey]
                if (saved != null && System.currentTimeMillis() - saved.savedAt < REFRESH_MS) {
                    // Just looked at (Home and back): nothing to ask the server again
                    show(saved)
                    loadedAt = saved.savedAt
                    return
                }
            }
            loadJob?.cancel()
            loadJob =
                viewModelScope.launchIO {
                    if (_state.value.rows.isEmpty()) {
                        val saved =
                            memory[snapKey]
                                ?: diskCache.read(snapKey, DoorSnapshot.serializer(), SNAPSHOT_MAX_AGE_MS)
                        if (saved != null && saved.rows.isNotEmpty()) show(saved)
                    }
                    when (door) {
                        BenflixDoor.MOVIES -> loadMovies(userId)
                        BenflixDoor.SHOWS -> loadShows(userId)
                        BenflixDoor.ANIME -> loadAnime(userId)
                        null -> loadHome(userId)
                        else -> Unit
                    }
                    if (fromSnapshot) {
                        if (gotFresh) {
                            _state.update { st -> st.copy(rows = st.rows.filter { it.key !in emptied }) }
                        } else {
                            // Nothing came back: keep last time's rows on screen, and try again next time
                            fromSnapshot = false
                            _state.update { it.copy(loaded = true, pending = emptySet()) }
                            return@launchIO
                        }
                    }
                    // Nothing at all and no libraries means the server couldn't be reached: say so, and try again next time
                    val offline = _state.value.rows.isEmpty() && libraries().isEmpty()
                    loadedAt = if (offline) 0L else System.currentTimeMillis()
                    _state.update { it.copy(loaded = true, offline = offline, pending = emptySet()) }
                    if (!offline) save(snapKey)
                    fromSnapshot = false
                    freshShuffled.clear()
                }
        }

        private fun show(saved: DoorSnapshot) {
            fromSnapshot = true
            freshShuffled.clear()
            emptied.clear()
            gotFresh = false
            _state.value =
                DoorPageState(
                    rows = saved.rows,
                    browse = saved.browse,
                    loaded = true,
                    pick = saved.pick,
                    order = saved.order,
                )
        }

        private fun save(snapKey: String) {
            val st = _state.value
            if (st.rows.isEmpty()) return
            val snap =
                DoorSnapshot(
                    rows =
                        st.rows.map { shown ->
                            val r = freshShuffled[shown.key] ?: shown
                            r.copy(items = r.items.take(DoorDataService.ROW))
                        },
                    browse = st.browse,
                    order = st.order,
                    // The phone's "Picked for you" card changes from one visit to the next
                    pick = nextPick ?: st.pick,
                    savedAt = System.currentTimeMillis(),
                )
            memory[snapKey] = snap
            diskCache.write(snapKey, DoorSnapshot.serializer(), snap)
        }

        private fun snapshotKey(
            door: BenflixDoor?,
            userId: UUID,
        ) = "door-${door?.name ?: "HOME"}-$userId-${KidsMode.level}-all-$SNAPSHOT_FORMAT"

        /** "Try again" on the can't-reach-the-server note */
        fun retry() {
            val door = door
            if (door == null && !home) return
            loadedAt = 0L
            loadJob?.cancel()
            loadedFor = null
            _state.update { it.copy(loaded = false, offline = false) }
            start(door)
        }

        private suspend fun libraries(): List<Library> =
            navDrawerService.state.value.allLibraries
                .takeIf { it.isNotEmpty() }
                ?: withTimeoutOrNull(LIBRARY_WAIT_MS) { navDrawerService.state.first { it.allLibraries.isNotEmpty() }.allLibraries }
                // The menu hasn't filled in yet (slow or just-started server): ask the server itself rather than
                // showing "Nothing in Movies yet" over a full library
                ?: serverRepository.currentUser?.id?.let { userId ->
                    runCatching { navDrawerService.getAllUserLibraries(userId, tvAccess = false) }.getOrNull()
                }
                ?: emptyList()

        private fun List<Library>.ofKind(vararg kinds: LibraryKind) =
            filter { LibraryKind.from(it.name, it.collectionType) in kinds }.map { it.itemId }

        /** Puts a finished row in place, keeping the page's row order */
        private fun put(
            order: List<String>,
            row: DoorRowData,
        ) {
            // Rows picked at random stay as they were on screen, so posters don't swap under you as it refreshes
            if (fromSnapshot && row.key in SHUFFLED && _state.value.rows.any { it.key == row.key }) {
                // ...and the fresh pick is what next time's snapshot shows
                freshShuffled[row.key] = row.copy(items = row.items.distinctBy { it.id })
                gotFresh = true
                _state.update { st -> st.copy(pending = st.pending - row.key) }
                return
            }
            val clean = row.copy(items = row.items.distinctBy { it.id })
            gotFresh = true
            _state.update { st ->
                val rows = (st.rows.filter { it.key != row.key } + clean).sortedBy { order.indexOf(it.key) }
                st.copy(rows = rows, pending = st.pending - row.key)
            }
        }

        private fun remove(key: String) {
            if (fromSnapshot) {
                // Over last time's rows, an empty answer might just mean the server is out of reach: only
                // take the row away at the end, once another row has really come back fresh
                emptied += key
                _state.update { st -> st.copy(pending = st.pending - key) }
                return
            }
            _state.update { st -> st.copy(rows = st.rows.filter { it.key != key }, pending = st.pending - key) }
        }

        /** Marks every row as loading, so the page draws grey placeholder rows until each one arrives */
        private fun startRows(order: List<String>) =
            _state.update {
                // Rows switched off in Settings (or gone from the page) leave straight away
                val kept = it.rows.filter { r -> r.key in order }
                // Over last time's rows, nothing turns grey: each row just swaps to its fresh copy when it's in
                if (fromSnapshot) it.copy(rows = kept, order = order) else it.copy(rows = kept, order = order, pending = order.toSet())
            }

        private fun putOrRemove(
            order: List<String>,
            row: DoorRowData,
        ) = if (row.isEmpty || row.key !in order) remove(row.key) else put(order, row)

        /** The rows this person switched on for [page] (Settings > Look > Home rows), after the ones that always stay */
        private fun picked(
            userId: UUID,
            page: PageRows.Page,
            fixed: List<String>,
        ): List<String> {
            val shown = PageRows.shown(context, userId, page).filter { it !in fixed }
            // Ben 2026-11-18: Top 10 sits right under Continue watching on Movies, Shows and Anime (when it's on)
            val top = shown.filter { it == TOP_10 || it == TOP_10_ANIME }
            return if (page == PageRows.Page.HOME || top.isEmpty()) {
                fixed + shown
            } else {
                listOf(fixed.first()) + top + fixed.drop(1) + (shown - top.toSet())
            }
        }

        /**
         * Glowfin Movies, For you (ui-7): Continue watching, Top 10 this week, Because you watched, Recently added,
         * Recently released, Top rated you haven't seen, Stand-up, Trending not on Glowfin. No anime: that has its own page now.
         */
        private suspend fun loadMovies(userId: UUID) {
            val libs = libraries()
            val main = libs.ofKind(LibraryKind.MOVIES)
            val anime = libs.ofKind(LibraryKind.ANIME_MOVIES)
            val standUp = libs.ofKind(LibraryKind.STANDUP)
            val easy = KidsMode.isActive
            val order = if (easy) listOf(KEEP, NEW) else picked(userId, PageRows.Page.MOVIES, listOf(KEEP))
            startRows(order)
            var newPick: BaseItem? = null
            var becausePick: BaseItem? = null
            coroutineScope {
                val disliked = async { data.disliked(userId) }
                val animeIds = async { data.idsIn(userId, anime, BaseItemKind.MOVIE) }
                launch {
                    val skip = animeIds.await()
                    putOrRemove(order, DoorRowData(KEEP, "Keep watching", data.resumeMovies(userId).filter { it.id !in skip }))
                }
                launch {
                    val dislikes = disliked.await()
                    val items = data.newest(userId, main + standUp, BaseItemKind.MOVIE).filter { it.id !in dislikes }
                    putOrRemove(order, DoorRowData(NEW, "Recently added", items, seeAll = true))
                    newPick = pickFrom(items)
                }
                if (!easy) {
                    launch {
                        val dislikes = disliked.await()
                        val items = data.top10(userId, BaseItemKind.MOVIE, animeIds.await(), anime = false, exclude = dislikes)
                        putOrRemove(order, DoorRowData(TOP_10, "Top 10 movies this week", items, style = DoorRowStyle.TOP10))
                    }
                    launch {
                        val dislikes = disliked.await()
                        val skip = animeIds.await()
                        val found = data.because(userId, BaseItemKind.MOVIE, dislikes) { it !in skip }
                        if (found == null) {
                            remove(BECAUSE)
                        } else {
                            putOrRemove(order, DoorRowData(BECAUSE, "Because you watched ${found.first}", found.second))
                            becausePick = pickFrom(found.second)
                        }
                    }
                    launch {
                        val dislikes = disliked.await()
                        val skip = animeIds.await()
                        val found = data.loved(userId, BaseItemKind.MOVIE, dislikes) { it !in skip }
                        if (found == null) {
                            remove(LOVED)
                        } else {
                            putOrRemove(order, DoorRowData(LOVED, "Because you loved ${found.first}", found.second))
                        }
                    }
                    launch {
                        val dislikes = disliked.await()
                        val items = data.recentlyReleased(userId, main + standUp, BaseItemKind.MOVIE).filter { it.id !in dislikes }
                        putOrRemove(order, DoorRowData(RELEASED, "Recently released", items))
                    }
                    launch {
                        val dislikes = disliked.await()
                        val items = data.topRatedUnseen(userId, main, BaseItemKind.MOVIE).filter { it.id !in dislikes }
                        putOrRemove(order, DoorRowData(TOP_RATED, "Top rated you haven't seen", items))
                    }
                    launch {
                        val items = data.fromLibrary(userId, standUp, BaseItemKind.MOVIE)
                        putOrRemove(order, DoorRowData(STAND_UP, "Stand-up", items, seeAll = true))
                    }
                    launch {
                        val missing = data.trendingMissing(com.github.damontecres.wholphin.data.model.SeerrItemType.MOVIE)
                        putOrRemove(
                            order,
                            DoorRowData(
                                TRENDING_MISSING,
                                "Trending, not on Glowfin",
                                emptyList(),
                                DoorRowStyle.DISCOVER,
                                discover = missing,
                            ),
                        )
                    }
                }
            }
            nextPick = becausePick ?: newPick
            _state.update { st -> st.copy(pick = st.pick ?: nextPick) }
        }

        /**
         * Glowfin Shows, For you (ui-7): Continue watching, Next up, New episodes (the Missing episodes tag),
         * Because you watched, Recently added, Top rated you haven't seen, Trending not on Glowfin. No anime.
         */
        private suspend fun loadShows(userId: UUID) {
            val libs = libraries()
            val main = libs.ofKind(LibraryKind.SHOWS)
            val anime = libs.ofKind(LibraryKind.ANIME_SHOWS)
            val easy = KidsMode.isActive
            val order =
                if (easy) listOf(KEEP, NEXT_UP, NEW) else picked(userId, PageRows.Page.SHOWS, listOf(KEEP, NEXT_UP))
            startRows(order)
            var newPick: BaseItem? = null
            var becausePick: BaseItem? = null
            coroutineScope {
                val disliked = async { data.disliked(userId) }
                val animeIds = async { data.idsIn(userId, anime, BaseItemKind.SERIES) }

                suspend fun notAnime(list: List<BaseItem>): List<BaseItem> {
                    val skip = animeIds.await()
                    return list.filter { (it.data.seriesId ?: it.id) !in skip }
                }
                launch {
                    val items = notAnime(data.continueEpisodes(userId))
                    putOrRemove(order, DoorRowData(KEEP, "Keep watching", items))
                }
                launch {
                    val next = notAnime(data.nextUp(userId))
                    putOrRemove(order, DoorRowData(NEXT_UP, "Up next", next))
                    if (!easy) {
                        val fresh = data.newEpisodes(userId, next.mapNotNull { it.data.seriesId }.distinct())
                        putOrRemove(order, DoorRowData(NEW_EPISODES, "New episodes", fresh))
                    }
                }
                if (!easy) {
                    launch {
                        val dislikes = disliked.await()
                        val items = data.top10(userId, BaseItemKind.SERIES, animeIds.await(), anime = false, exclude = dislikes)
                        putOrRemove(order, DoorRowData(TOP_10, "Top 10 shows this week", items, style = DoorRowStyle.TOP10))
                    }
                }
                launch {
                    val dislikes = disliked.await()
                    val items = data.newest(userId, main, BaseItemKind.SERIES).filter { it.id !in dislikes }
                    putOrRemove(order, DoorRowData(NEW, "Recently added", items, seeAll = true))
                    newPick = pickFrom(items)
                }
                if (!easy) {
                    launch {
                        val dislikes = disliked.await()
                        val skip = animeIds.await()
                        val found = data.because(userId, BaseItemKind.SERIES, dislikes) { it !in skip }
                        if (found == null) {
                            remove(BECAUSE)
                        } else {
                            putOrRemove(order, DoorRowData(BECAUSE, "Because you watched ${found.first}", found.second))
                            becausePick = pickFrom(found.second)
                        }
                    }
                    launch {
                        val dislikes = disliked.await()
                        val skip = animeIds.await()
                        val found = data.loved(userId, BaseItemKind.SERIES, dislikes) { it !in skip }
                        if (found == null) {
                            remove(LOVED)
                        } else {
                            putOrRemove(order, DoorRowData(LOVED, "Because you loved ${found.first}", found.second))
                        }
                    }
                    launch {
                        val dislikes = disliked.await()
                        val items = data.topRatedUnseen(userId, main, BaseItemKind.SERIES).filter { it.id !in dislikes }
                        putOrRemove(order, DoorRowData(TOP_RATED, "Top rated you haven't seen", items))
                    }
                    launch {
                        val missing = data.trendingMissing(com.github.damontecres.wholphin.data.model.SeerrItemType.TV)
                        putOrRemove(
                            order,
                            DoorRowData(
                                TRENDING_MISSING,
                                "Trending, not on Glowfin",
                                emptyList(),
                                DoorRowStyle.DISCOVER,
                                discover = missing,
                            ),
                        )
                    }
                }
            }
            nextPick = becausePick ?: newPick
            _state.update { st -> st.copy(pick = st.pick ?: nextPick) }
        }

        /**
         * Glowfin Anime, For you (ui-7, Ben's pick A): Continue watching, Airing this season, Top 10 anime,
         * Because you watched, Hidden gem anime, Anime movies, Recently added, Trending not on Glowfin. Anime shows and movies together.
         */
        private suspend fun loadAnime(userId: UUID) {
            val libs = libraries()
            val shows = libs.ofKind(LibraryKind.ANIME_SHOWS)
            val movies = libs.ofKind(LibraryKind.ANIME_MOVIES)
            val order = picked(userId, PageRows.Page.ANIME, listOf(KEEP))
            startRows(order)
            aniListLibraries.warm(shows + movies, userId)
            var newPick: BaseItem? = null
            var becausePick: BaseItem? = null
            coroutineScope {
                val disliked = async { data.disliked(userId) }
                val showIds = async { data.idsIn(userId, shows, BaseItemKind.SERIES) }
                val movieIds = async { data.idsIn(userId, movies, BaseItemKind.MOVIE) }
                launch {
                    val series = showIds.await()
                    val films = movieIds.await()
                    val episodes = data.continueEpisodes(userId) + data.nextUp(userId)
                    val items =
                        (
                            data.resumeMovies(userId).filter { it.id in films } +
                                episodes.filter { (it.data.seriesId ?: it.id) in series }
                        ).distinctBy { it.data.seriesId ?: it.id }
                    putOrRemove(order, DoorRowData(KEEP, "Keep watching", items))
                }
                launch {
                    val dislikes = disliked.await()
                    val items = data.airing(userId, shows).filter { it.id !in dislikes }
                    putOrRemove(order, DoorRowData(AIRING, "Airing this season", items))
                }
                launch {
                    val items = data.top10(userId, BaseItemKind.SERIES, showIds.await(), anime = true)
                    putOrRemove(order, DoorRowData(TOP_10_ANIME, "Top 10 anime", items, style = DoorRowStyle.TOP10))
                }
                launch {
                    val dislikes = disliked.await()
                    val series = showIds.await()
                    val found = data.because(userId, BaseItemKind.SERIES, dislikes) { it in series }
                    if (found == null) {
                        remove(BECAUSE)
                    } else {
                        putOrRemove(order, DoorRowData(BECAUSE, "Because you watched ${found.first}", found.second))
                        becausePick = pickFrom(found.second)
                    }
                }
                launch {
                    val dislikes = disliked.await()
                    val items = data.hiddenGems(userId, shows, BaseItemKind.SERIES).filter { it.id !in dislikes }
                    putOrRemove(order, DoorRowData(GEMS, "Hidden gem anime", items))
                }
                launch {
                    val dislikes = disliked.await()
                    val items = data.newest(userId, movies, BaseItemKind.MOVIE).filter { it.id !in dislikes }
                    putOrRemove(order, DoorRowData(ANIME_MOVIES, "Anime movies", items, seeAll = true))
                }
                launch {
                    val dislikes = disliked.await()
                    val items =
                        (data.newest(userId, shows, BaseItemKind.SERIES) + data.newest(userId, movies, BaseItemKind.MOVIE))
                            .filter { it.id !in dislikes }
                            .sortedByDescending { it.data.dateCreated }
                            .take(DoorDataService.ROW)
                    putOrRemove(order, DoorRowData(NEW, "Recently added", items))
                    newPick = pickFrom(items)
                }
                if (!KidsMode.isActive) {
                    launch {
                        val missing = data.trendingAnimeMissing()
                        putOrRemove(
                            order,
                            DoorRowData(
                                TRENDING_MISSING,
                                "Trending, not on Glowfin",
                                emptyList(),
                                DoorRowStyle.DISCOVER,
                                discover = missing,
                            ),
                        )
                    }
                }
            }
            nextPick = becausePick ?: newPick
            _state.update { st -> st.copy(pick = st.pick ?: nextPick) }
        }

        /**
         * Glowfin Home (ui-6): Continue watching and Next up, then up to the rows this person picked (Recently
         * added, Top 10, Because you watched, Trending not on Glowfin, Anime airing, Watch it again). "What's new"
         * is drawn by the Home page itself, in the spot picked for it.
         */
        private suspend fun loadHome(userId: UUID) {
            val libs = libraries()
            val animeShows = libs.ofKind(LibraryKind.ANIME_SHOWS)
            val animeMovies = libs.ofKind(LibraryKind.ANIME_MOVIES)
            val easy = KidsMode.isActive
            val order =
                if (easy) {
                    listOf(KEEP, NEXT_UP, NEW)
                } else {
                    picked(userId, PageRows.Page.HOME, listOf(KEEP, NEXT_UP))
                }
            startRows(order)
            var newPick: BaseItem? = null
            coroutineScope {
                val disliked = async { data.disliked(userId) }
                launch {
                    val items =
                        (data.resumeMovies(userId) + data.continueEpisodes(userId))
                            .sortedByDescending { it.data.userData?.lastPlayedDate }
                            .take(DoorDataService.ROW)
                    putOrRemove(order, DoorRowData(KEEP, "Keep watching", items))
                }
                launch { putOrRemove(order, DoorRowData(NEXT_UP, "Up next", data.nextUp(userId))) }
                launch {
                    val dislikes = disliked.await()
                    val items =
                        (
                            data.newest(userId, allIds(libs), BaseItemKind.MOVIE) +
                                data.newest(userId, allIds(libs), BaseItemKind.SERIES)
                        ).filter { it.id !in dislikes }
                            .sortedByDescending { it.data.dateCreated }
                            .take(DoorDataService.ROW)
                    putOrRemove(order, DoorRowData(NEW, "Recently added", items))
                    newPick = pickFrom(items)
                }
                if (!easy) {
                    launch {
                        val dislikes = disliked.await()
                        val animeIds =
                            data.idsIn(userId, animeMovies, BaseItemKind.MOVIE) + data.idsIn(userId, animeShows, BaseItemKind.SERIES)
                        val movies = data.top10(userId, BaseItemKind.MOVIE, animeIds, anime = false, exclude = dislikes)
                        val shows = data.top10(userId, BaseItemKind.SERIES, animeIds, anime = false, exclude = dislikes)
                        // Movies and shows take turns, so both are in the ten
                        val items =
                            (0 until maxOf(movies.size, shows.size))
                                .flatMap { listOfNotNull(movies.getOrNull(it), shows.getOrNull(it)) }
                                .filter { it.id !in dislikes }
                                .take(DoorDataService.TOP)
                        putOrRemove(order, DoorRowData(TOP_10, "Top 10 this week", items, style = DoorRowStyle.TOP10))
                    }
                    launch {
                        val dislikes = disliked.await()
                        val found =
                            data.because(userId, BaseItemKind.MOVIE, dislikes)
                                ?: data.because(userId, BaseItemKind.SERIES, dislikes)
                        if (found == null) {
                            remove(BECAUSE)
                        } else {
                            putOrRemove(order, DoorRowData(BECAUSE, "Because you watched ${found.first}", found.second))
                        }
                    }
                    launch {
                        val missing = data.trendingMissing(null)
                        putOrRemove(
                            order,
                            DoorRowData(
                                TRENDING_MISSING,
                                "Trending, not on Glowfin",
                                emptyList(),
                                DoorRowStyle.DISCOVER,
                                discover = missing,
                            ),
                        )
                    }
                    launch {
                        val dislikes = disliked.await()
                        val items = data.airing(userId, animeShows).filter { it.id !in dislikes }
                        putOrRemove(order, DoorRowData(AIRING, "Anime airing this season", items))
                    }
                    launch {
                        val dislikes = disliked.await()
                        val items = data.watchAgain(userId).filter { it.id !in dislikes }
                        putOrRemove(order, DoorRowData(WATCH_AGAIN, "Watch it again", items))
                    }
                }
            }
            nextPick = newPick
            _state.update { st -> st.copy(pick = st.pick ?: nextPick) }
        }

        private fun allIds(libs: List<Library>): List<UUID> = libs.map { it.itemId }

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

        /**
         * Everything behind a door, for the All movies / All shows grid. Glowfin: anime lives on its own page now,
         * so Movies and Shows leave it out, and the Anime page's Shows and Movies tabs show only anime ([movies]).
         */
        suspend fun everything(
            door: BenflixDoor,
            movies: Boolean = door == BenflixDoor.MOVIES,
        ): List<BaseItem> {
            val userId = serverRepository.currentUser?.id ?: return emptyList()
            val libs = libraries()
            val type = if (movies) BaseItemKind.MOVIE else BaseItemKind.SERIES
            val parents =
                when {
                    door == BenflixDoor.ANIME -> libs.ofKind(if (movies) LibraryKind.ANIME_MOVIES else LibraryKind.ANIME_SHOWS)
                    movies -> libs.ofKind(LibraryKind.MOVIES) + libs.ofKind(LibraryKind.STANDUP)
                    else -> libs.ofKind(LibraryKind.SHOWS)
                }
            return data.everything(userId, parents, type)
        }

        /** Movies > Collections: every box set on Tower (Harry Potter Collection and so on) */
        suspend fun boxSets(): List<BaseItem> {
            val userId = serverRepository.currentUser?.id ?: return emptyList()
            return data.boxSets(userId)
        }

        /** The anime library for the Anime page's Genres tab (shows first, else movies) */
        suspend fun animeLibrary(): Library? {
            val libs = libraries()
            return libs.firstOrNull { LibraryKind.from(it.name, it.collectionType) == LibraryKind.ANIME_SHOWS }
                ?: libs.firstOrNull { LibraryKind.from(it.name, it.collectionType) == LibraryKind.ANIME_MOVIES }
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

                HISTORY -> {
                    data.history(userId)
                }

                NEW, NEW_SHOWS -> {
                    data.newest(userId, main + (if (movies) standUp else emptyList()), type, limit = FULL)
                }

                ANIME_MOVIES -> {
                    data.newest(userId, libs.ofKind(LibraryKind.ANIME_MOVIES), BaseItemKind.MOVIE, limit = FULL)
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

        /** A "More Comedy" row for one of the genres they picked "More of this" for in setup */
        private suspend fun moreGenreRow(
            userId: UUID,
            order: List<String>,
            type: BaseItemKind,
        ) {
            val genre = hiddenGenresStore.loadMore(userId).randomOrNull()
            if (genre == null) {
                remove(MORE_GENRE)
                return
            }
            val items = data.inGenre(userId, type, genre)
            val name = genre.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
            putOrRemove(order, DoorRowData(MORE_GENRE, "More $name", items))
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
            const val LOVED = "loved"

            /** Watch history: not a row, only the full page opened from My Glowfin */
            const val HISTORY = "history"
            const val MORE_GENRE = "moregenre"
            const val ANIME = "anime"
            const val STAND_UP = "standup"
            const val NEXT_UP = "nextup"
            const val RELEASED = "released"
            const val TOP_RATED = "toprated"
            const val TRENDING_MISSING = "trendingmissing"
            const val AIRING = "airing"
            const val GEMS = "gems"
            const val ANIME_MOVIES = "animemovies"
            const val WATCH_AGAIN = "watchagain"

            const val BROWSE_GENRES = "genres"
            const val BROWSE_COLLECTIONS = "collections"
            const val BROWSE_STUDIOS = "studios"
            const val BROWSE_ALL = "all"
            const val BROWSE_ANIME_SHOWS = "animeshows"
            const val BROWSE_ANIME_MOVIES = "animemovies"

            private const val REFRESH_MS = 60_000L

            /** Last time's snapshot is shown for up to a week (fresh rows replace it as they load) */
            private const val SNAPSHOT_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
            private const val SNAPSHOT_FORMAT = 2

            /** Rows that come out different every time; a refresh keeps the copy already on screen */
            private val SHUFFLED = setOf(BECAUSE, MORE_GENRE, ANIME, STAND_UP, GEMS, WATCH_AGAIN)

            /** The doors' snapshots in memory, for this run of the app */
            private val memory = java.util.concurrent.ConcurrentHashMap<String, DoorSnapshot>()
            private const val LIBRARY_WAIT_MS = 5_000L
            private const val FULL = 300
        }
    }
