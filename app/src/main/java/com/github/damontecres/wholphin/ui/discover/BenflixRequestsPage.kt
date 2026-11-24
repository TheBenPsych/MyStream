package com.github.damontecres.wholphin.ui.discover

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.api.seerr.RequestApi
import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.api.seerr.model.RequestPostRequest
import com.github.damontecres.wholphin.api.seerr.model.Season
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.services.AniListService
import com.github.damontecres.wholphin.services.BenflixCantFind
import com.github.damontecres.wholphin.services.BenflixSeerrLink
import com.github.damontecres.wholphin.services.CantFindBlocked
import com.github.damontecres.wholphin.services.CantFindEntry
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SearchClock
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.services.requestedArrived
import com.github.damontecres.wholphin.ui.cards.AdultContentBanner
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.BenflixChoice
import com.github.damontecres.wholphin.ui.components.BenflixChoiceDialog
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.DelayedLoadingPage
import com.github.damontecres.wholphin.ui.components.EmptyPage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.TabViewModel
import com.github.damontecres.wholphin.ui.components.UNSET
import com.github.damontecres.wholphin.ui.detail.discover.AdultContentFlags
import com.github.damontecres.wholphin.ui.detail.discover.AnimeTagCatalogViewModel
import com.github.damontecres.wholphin.ui.detail.discover.isAdultContent
import com.github.damontecres.wholphin.ui.detail.discover.isGrownUpTag
import com.github.damontecres.wholphin.ui.detail.discover.movieAgeRating
import com.github.damontecres.wholphin.ui.detail.discover.str
import com.github.damontecres.wholphin.ui.detail.discover.tags
import com.github.damontecres.wholphin.ui.detail.discover.tvAgeRating
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.LocalTopBarOverlap
import com.github.damontecres.wholphin.ui.nav.RequestsWaiting
import com.github.damontecres.wholphin.ui.setup.seerr.SeerrDialog
import com.github.damontecres.wholphin.ui.setup.seerr.SeerrDialogMode
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Button
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.util.ScrollToTopBringIntoViewSpec
import com.github.damontecres.wholphin.ui.util.scrollAsOneBlock
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/** Which shelf of the admin "Find new stuff" page */
enum class FindSection {
    MOVIES,
    SHOWS,
    ANIME,
}

/** A genre button: the TMDB genre number for movies and for shows (TMDB uses different ones) */
data class FindGenre(
    val label: String,
    val movieId: Int?,
    val tvId: Int?,
)

val FindGenres =
    listOf(
        FindGenre("Action", 28, 10759),
        FindGenre("Comedy", 35, 35),
        FindGenre("Drama", 18, 18),
        FindGenre("Family", 10751, 10751),
        FindGenre("Sci-fi", 878, 10765),
        FindGenre("Horror", 27, null),
        FindGenre("Crime", 80, 80),
        FindGenre("Romance", 10749, null),
        FindGenre("Mystery", 9648, 9648),
        FindGenre("Documentary", 99, 99),
    )

data class FindRow(
    val title: String,
    val items: List<DiscoverItem>,
)

/** One request, with what was asked for and who asked */
data class RequestEntry(
    val request: MediaRequest,
    val item: DiscoverItem?,
    val who: String,
    val status: RequestStatus,
    val available: Boolean,
    // Benflix: being downloaded right now, and how far along (when the server knows)
    val downloading: Boolean = false,
    val downloadPercent: Int? = null,
    // Benflix: when the 7-day "Still looking" clock starts (waits for release + a few weeks)
    val clock: SearchClock? = null,
)

@HiltViewModel
class BenflixRequestsViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val seerrService: SeerrService,
        private val seerrServerRepository: SeerrServerRepository,
        val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
        private val seerrLink: BenflixSeerrLink,
        private val cantFindList: BenflixCantFind,
        private val requestReady: com.github.damontecres.wholphin.ui.main.RequestReadyService,
        private val showArrival: com.github.damontecres.wholphin.services.ShowArrival,
        private val libraryTmdb: com.github.damontecres.wholphin.services.BenflixLibraryTmdb,
        private val helper: com.github.damontecres.wholphin.services.GameLibraryClient,
    ) : ViewModel() {
        val active = seerrService.active

        /** Benflix: titles Ben marked "Couldn't find it", by request key */
        val cantFind = cantFindList.entries

        /** Admin: mark a request "Couldn't find it", so whoever asked is told */
        fun markCantFind(
            entry: RequestEntry,
            note: String?,
        ) {
            val key = entry.cantFindKey() ?: return
            viewModelScope.launch(WholphinDispatchers.IO) {
                val askedBy =
                    _requests.value
                        .orEmpty()
                        .filter { it.cantFindKey() == key }
                        .mapNotNull { it.request.requestedBy?.id }
                showToast(context, cantFindList.mark(key, entry.item?.title, note, askedBy, entry.item?.posterUrl).message())
            }
        }

        /** Seerr id of whoever is signed in, for their own "Couldn't find it" cards */
        val mySeerrId = seerrServerRepository.currentUserId

        /** Admin: take "Couldn't find it" off again */
        fun undoCantFind(entry: RequestEntry) {
            undoCantFind(entry.cantFindKey() ?: return)
        }

        /** Admin: take "Couldn't find it" off again, and unblock it in Seerr */
        fun undoCantFind(key: String) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                showToast(
                    context,
                    if (cantFindList.undo(key)) "Unblocked. Ask for it again to start a new search." else "Couldn't save that. Try again.",
                )
            }
        }

        /** Ben shared Seerr, so this person can connect by typing their password once */
        val canConnect = MutableStateFlow(false)

        /** null = idle, true = connecting, false = last try failed */
        val connecting = MutableStateFlow<Boolean?>(null)

        init {
            viewModelScope.launch(WholphinDispatchers.IO) {
                // Not connected yet: Jellyfin Enhanced on the server may be able to connect us right away
                if (!seerrService.active.first() && seerrLink.viaJellyfinEnhanced()) return@launch
                val userId = serverRepository.currentUser?.id ?: return@launch
                canConnect.value = seerrLink.sharedUrl(userId) != null
            }
        }

        fun connect(password: String) {
            connecting.value = true
            viewModelScope.launch(WholphinDispatchers.IO) {
                connecting.value = if (seerrLink.connectWithPassword(password)) null else false
            }
        }

        val seerrConnection = seerrServerRepository.connection

        /** Try Jellyfin Enhanced again (for the "Try again" button) */
        fun retry() {
            connecting.value = true
            viewModelScope.launch(WholphinDispatchers.IO) {
                connecting.value = if (seerrLink.viaJellyfinEnhanced()) null else false
            }
        }

        val isAdmin: Boolean get() = serverRepository.currentUserDto?.policy?.isAdministrator == true

        /** Who is signed in, for things remembered per person on this device */
        val userKey: String get() = serverRepository.currentUser?.id?.toString() ?: "none"

        /** Connected through the Jellyfin Enhanced plugin, which passes fewer filters */
        val viaJellyfin: Boolean get() = seerrService.api.viaJellyfin

        private val _rows = MutableStateFlow<Map<String, List<FindRow>>>(emptyMap())

        /** Titles someone asked for that aren't here yet, as "MOVIE-123" / "TV-456" */
        private val requestedIds = MutableStateFlow<Set<String>>(emptySet())

        /**
         * Benflix: the shelves, with anything already asked for marked as requested. Seerr doesn't always say so
         * itself (a pending show can still read as "unknown"), and the shelves are kept from before you asked.
         */
        val rows: StateFlow<Map<String, List<FindRow>>> =
            combine(_rows, requestedIds) { all, requested ->
                if (requested.isEmpty()) {
                    all
                } else {
                    all.mapValues { (_, shelves) ->
                        shelves.map { row -> row.copy(items = row.items.map { markRequested(it, requested) }) }
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

        private fun markRequested(
            item: DiscoverItem,
            requested: Set<String>,
        ): DiscoverItem =
            if (item.availability == SeerrAvailability.UNKNOWN && "${item.type.name}-${item.id}" in requested) {
                item.copy(availability = SeerrAvailability.PENDING)
            } else {
                item
            }

        /** Looks up what has been asked for (everyone's for the admin, your own otherwise) */
        fun refreshRequested() {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    val myId = if (isAdmin) null else seerrServerRepository.currentUserId.first()
                    requestedIds.value =
                        seerrService
                            .allRequests(requestedBy = myId)
                            .filter {
                                it.media?.status != SeerrAvailability.AVAILABLE.status &&
                                    RequestStatus.from(it.status) != RequestStatus.DECLINED
                            }.mapNotNull { request ->
                                val tmdbId = request.media?.tmdbId ?: return@mapNotNull null
                                "${SeerrItemType.fromString(request.type).name}-$tmdbId"
                            }.toSet()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not look up requested titles")
                }
            }
        }

        private val _requests = MutableStateFlow<List<RequestEntry>?>(null)
        val requests: StateFlow<List<RequestEntry>?> = _requests

        private val _requestsFailed = MutableStateFlow(false)

        /** Benflix: the list couldn't be loaded at all (nothing to show yet), so the page offers Try again */
        val requestsFailed: StateFlow<Boolean> = _requestsFailed

        fun key(
            section: FindSection,
            filters: FindFilters,
        ) = "${section.name}-$filters"

        /**
         * Through Jellyfin Enhanced the "at least N votes" filter is dropped, so "Top rated" would fill up with
         * unknown titles with one perfect vote. There it becomes "popular titles rated 7 or more" instead.
         */
        private fun topRatedSort(
            sortBy: String,
            minVotes: Int?,
        ): String = if (minVotes != null && seerrService.api.viaJellyfin) "popularity.desc" else sortBy

        private fun topRatedFloor(minVotes: Int?): Int? = if (minVotes != null && seerrService.api.viaJellyfin) 7 else null

        /** Loads the shelves for one section and genre (kept, so switching back is instant) */
        fun loadSection(
            section: FindSection,
            filters: FindFilters,
        ) {
            val key = key(section, filters)
            // Benflix: shelves that failed are tried again, not kept empty for good
            if (_rows.value.containsKey(key) && key !in _failedRows.value) return
            if (!loadingRows.add(key)) return
            val genre = filters.genre
            val failed = AtomicBoolean(false)

            // Benflix: Japanese anime belongs on the Anime tab, not mixed into Movies and Shows
            fun clean(row: FindRow): FindRow =
                if (section ==
                    FindSection.ANIME
                ) {
                    row
                } else {
                    row.copy(items = row.items.filterNot { it.isAnime })
                }

            fun done(rows: List<FindRow>) {
                _rows.update { it + (key to rows) }
                _failedRows.update { if (failed.get()) it + key else it - key }
                loadingRows.remove(key)
            }
            viewModelScope
                .launch(WholphinDispatchers.IO) {
                    if (filters.narrowed) {
                        // Benflix: several filters together make one list of results, a few pages long
                        val pages =
                            coroutineScope {
                                (1..3).map { page -> async { narrowedPage(section, filters, page) } }.awaitAll()
                            }
                        if (pages.any { it == null }) failed.set(true)
                        val titles = listOf("Results", "More results", "Even more")
                        done(pages.mapIndexed { i, items -> FindRow(titles[i], items.orEmpty()) })
                        return@launch
                    }
                    val today = LocalDate.now().toString()
                    val movieGenre = genre?.movieId?.toString()
                    val tvGenre = genre?.tvId?.toString()

                    suspend fun safe(block: suspend () -> List<DiscoverItem>): List<DiscoverItem> =
                        try {
                            block()
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            Timber.w(ex, "Find new stuff row failed")
                            failed.set(true)
                            emptyList()
                        }

                    suspend fun movies(
                        sortBy: String,
                        genres: String? = movieGenre,
                        keywords: String? = null,
                        from: String? = null,
                        minVotes: Int? = null,
                    ) = safe {
                        seerrService.api.searchApi
                            .discoverMoviesGet(
                                page = 1,
                                genre = genres,
                                keywords = keywords,
                                sortBy = topRatedSort(sortBy, minVotes),
                                primaryReleaseDateGte = from,
                                voteCountGte = minVotes,
                                voteAverageGte = topRatedFloor(minVotes),
                            ).results
                            ?.map { seerrService.createDiscoverItem(it) }
                            .orEmpty()
                    }

                    suspend fun shows(
                        sortBy: String,
                        genres: String? = tvGenre,
                        keywords: String? = null,
                        from: String? = null,
                        minVotes: Int? = null,
                    ) = safe {
                        seerrService.api.searchApi
                            .discoverTvGet(
                                page = 1,
                                genre = genres,
                                keywords = keywords,
                                sortBy = topRatedSort(sortBy, minVotes),
                                firstAirDateGte = from,
                                voteCountGte = minVotes,
                                voteAverageGte = topRatedFloor(minVotes),
                            ).results
                            ?.map { seerrService.createDiscoverItem(it) }
                            .orEmpty()
                    }

                    val rows =
                        coroutineScope {
                            when (section) {
                                FindSection.MOVIES -> {
                                    listOf(
                                        async {
                                            FindRow(
                                                "Trending now",
                                                if (genre == null) {
                                                    safe { seerrService.trending().filter { it.type == SeerrItemType.MOVIE } }
                                                } else {
                                                    movies("popularity.desc", from = LocalDate.now().minusMonths(4).toString())
                                                },
                                            )
                                        },
                                        async { FindRow("Popular", movies("popularity.desc")) },
                                        async {
                                            FindRow(
                                                "Coming soon",
                                                if (genre == null) {
                                                    safe { seerrService.upcomingMovies() }
                                                } else {
                                                    movies("popularity.desc", from = today)
                                                },
                                            )
                                        },
                                        async { FindRow("Top rated", movies("vote_average.desc", minVotes = 1000)) },
                                    )
                                }

                                FindSection.SHOWS -> {
                                    listOf(
                                        async {
                                            FindRow(
                                                "Trending now",
                                                if (genre == null) {
                                                    safe { seerrService.trending().filter { it.type == SeerrItemType.TV } }
                                                } else {
                                                    shows("popularity.desc", from = LocalDate.now().minusMonths(4).toString())
                                                },
                                            )
                                        },
                                        async { FindRow("Popular", shows("popularity.desc")) },
                                        async {
                                            FindRow(
                                                "Coming soon",
                                                if (genre == null) {
                                                    safe { seerrService.upcomingTv() }
                                                } else {
                                                    shows("popularity.desc", from = today)
                                                },
                                            )
                                        },
                                        async { FindRow("Top rated", shows("vote_average.desc", minVotes = 500)) },
                                    )
                                }

                                FindSection.ANIME -> {
                                    val showGenres = listOfNotNull(ANIMATION_GENRE, tvGenre).joinToString(",")
                                    val movieGenres = listOfNotNull(ANIMATION_GENRE, movieGenre).joinToString(",")
                                    listOf(
                                        async {
                                            FindRow(
                                                "Trending anime shows",
                                                shows(
                                                    "popularity.desc",
                                                    genres = showGenres,
                                                    keywords = ANIME_KEYWORD,
                                                    from = LocalDate.now().minusMonths(6).toString(),
                                                ),
                                            )
                                        },
                                        async {
                                            FindRow(
                                                "Popular anime shows",
                                                shows("popularity.desc", genres = showGenres, keywords = ANIME_KEYWORD),
                                            )
                                        },
                                        async {
                                            FindRow(
                                                "Anime movies",
                                                movies("popularity.desc", genres = movieGenres, keywords = ANIME_KEYWORD),
                                            )
                                        },
                                        async {
                                            FindRow(
                                                "Coming soon",
                                                shows("popularity.desc", genres = showGenres, keywords = ANIME_KEYWORD, from = today),
                                            )
                                        },
                                        async {
                                            FindRow(
                                                "Top rated anime",
                                                shows("vote_average.desc", genres = showGenres, keywords = ANIME_KEYWORD, minVotes = 200),
                                            )
                                        },
                                    )
                                }
                            }.also { jobs ->
                                // Ben 2026-11-23: Requests loaded very slowly, because nothing showed until the
                                // slowest row had come back. Each row shows as soon as it and the rows above it
                                // are in (rows only ever add at the bottom, so nothing jumps under the remote).
                                val ready = arrayOfNulls<FindRow>(jobs.size)
                                jobs.forEachIndexed { i, job ->
                                    launch {
                                        val row = clean(job.await())
                                        val shown =
                                            synchronized(ready) {
                                                ready[i] = row
                                                ready.takeWhile { it != null }.filterNotNull()
                                            }
                                        if (shown.isNotEmpty() && shown.size < jobs.size) _rows.update { it + (key to shown) }
                                    }
                                }
                            }.awaitAll()
                        }.map { clean(it) }
                    done(rows)
                }.invokeOnCompletion {
                    loadingRows.remove(key)
                    // Stopped part way: the half-loaded shelf is loaded again next time
                    if (it != null) _rows.update { rows -> rows - key }
                }
        }

        private val _failedRows = MutableStateFlow<Set<String>>(emptySet())

        /** Benflix: shelves (by [key]) where something didn't load, so the page can offer Try again */
        val failedRows: StateFlow<Set<String>> = _failedRows

        /** Keys being loaded right now, so a section isn't fetched twice at once */
        private val loadingRows = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

        /** One page of results for the Filters panel */
        private suspend fun narrowedPage(
            section: FindSection,
            filters: FindFilters,
            page: Int,
        ): List<DiscoverItem>? =
            try {
                val sort = filters.sort ?: FindSort.POPULAR
                val minVotes = if (sort == FindSort.RATED) 300 else null
                val from = filters.decade?.from
                val to = filters.decade?.to ?: if (sort == FindSort.NEWEST) LocalDate.now().toString() else null
                val score = filters.minScore ?: topRatedFloor(minVotes)
                val language = filters.language?.code?.takeIf { !seerrService.api.viaJellyfin }
                if (section == FindSection.MOVIES) {
                    seerrService.api.searchApi
                        .discoverMoviesGet(
                            page = page,
                            genre = filters.genre?.movieId?.toString(),
                            sortBy = topRatedSort(sort.movie, minVotes),
                            primaryReleaseDateGte = from,
                            primaryReleaseDateLte = to,
                            voteCountGte = minVotes,
                            voteAverageGte = score,
                            language = language,
                        ).results
                        ?.map { seerrService.createDiscoverItem(it) }
                        ?.filterNot { it.isAnime }
                        .orEmpty()
                } else {
                    val anime = section == FindSection.ANIME
                    val genres =
                        if (anime) {
                            listOfNotNull(ANIMATION_GENRE, filters.genre?.tvId?.toString()).joinToString(",")
                        } else {
                            filters.genre?.tvId?.toString()
                        }
                    seerrService.api.searchApi
                        .discoverTvGet(
                            page = page,
                            genre = genres,
                            keywords = if (anime) ANIME_KEYWORD else null,
                            sortBy = topRatedSort(sort.tv, minVotes),
                            firstAirDateGte = from,
                            firstAirDateLte = to,
                            voteCountGte = minVotes,
                            voteAverageGte = score,
                            language = language,
                        ).results
                        ?.map { seerrService.createDiscoverItem(it) }
                        ?.filter { anime || !it.isAnime }
                        .orEmpty()
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Filtered results failed")
                null
            }

        private val _special = MutableStateFlow<Map<FindSection, List<FindRow>>>(emptyMap())

        /** Benflix: "Because you watched ..." and "Finish the collection" rows for the Movies and Shows tabs */
        val special: StateFlow<Map<FindSection, List<FindRow>>> =
            combine(_special, requestedIds) { all, requested ->
                all.mapValues { (_, shelves) ->
                    shelves.map { row -> row.copy(items = row.items.map { markRequested(it, requested) }) }
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

        fun loadSpecial(section: FindSection) {
            if (section == FindSection.ANIME || _special.value.containsKey(section)) return
            _special.update { it + (section to emptyList()) }
            viewModelScope.launch(WholphinDispatchers.IO) {
                val userId = serverRepository.currentUser?.id ?: return@launch
                val movies = section == FindSection.MOVIES
                val rows =
                    coroutineScope {
                        val because = async { becauseRows(userId, movies) }
                        val collection = async { if (movies) finishCollectionRow(userId) else null }
                        val newSeasons = async { if (movies) null else newSeasonsRow(userId) }
                        because.await() + listOfNotNull(collection.await(), newSeasons.await())
                    }
                _special.update { it + (section to rows) }
            }
        }

        /** Things like the last two movies or shows you watched, that aren't on Benflix */
        private suspend fun becauseRows(
            userId: UUID,
            movies: Boolean,
        ): List<FindRow> =
            try {
                val sources: List<Pair<String, String>> =
                    if (movies) {
                        api.itemsApi
                            .getItems(
                                GetItemsRequest(
                                    userId = userId,
                                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                                    recursive = true,
                                    isPlayed = true,
                                    sortBy = listOf(ItemSortBy.DATE_PLAYED),
                                    sortOrder = listOf(SortOrder.DESCENDING),
                                    fields = listOf(ItemFields.PROVIDER_IDS),
                                    limit = 2,
                                ),
                            ).content.items
                            .mapNotNull { dto -> dto.providerIds?.get("Tmdb")?.let { (dto.name ?: "") to it } }
                    } else {
                        val seriesIds =
                            api.itemsApi
                                .getItems(
                                    GetItemsRequest(
                                        userId = userId,
                                        includeItemTypes = listOf(BaseItemKind.EPISODE),
                                        recursive = true,
                                        isPlayed = true,
                                        sortBy = listOf(ItemSortBy.DATE_PLAYED),
                                        sortOrder = listOf(SortOrder.DESCENDING),
                                        limit = 30,
                                    ),
                                ).content.items
                                .mapNotNull { it.seriesId }
                                .distinct()
                                .take(2)
                        if (seriesIds.isEmpty()) {
                            emptyList()
                        } else {
                            api.itemsApi
                                .getItems(
                                    GetItemsRequest(
                                        userId = userId,
                                        ids = seriesIds,
                                        fields = listOf(ItemFields.PROVIDER_IDS),
                                    ),
                                ).content.items
                                .mapNotNull { dto -> dto.providerIds?.get("Tmdb")?.let { (dto.name ?: "") to it } }
                        }
                    }
                val kind = if (movies) "movie" else "tv"
                val fallback = if (movies) SeerrItemType.MOVIE else SeerrItemType.TV
                sources.mapNotNull { (name, tmdbId) ->
                    val items =
                        rawResults("/$kind/$tmdbId/recommendations", "results", fallback)
                            .filter { it.availability != SeerrAvailability.AVAILABLE }
                            .take(20)
                    if (items.isEmpty() || name.isBlank()) null else FindRow("Because you watched $name", items)
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Because you watched rows failed")
                emptyList()
            }

        /**
         * Glowfin new look (Ben approved 2026-11-22): shows you've been watching that have a newer season out than
         * Glowfin has, and nobody has asked for it yet. Like the "Season 4 is out" button on a show's page.
         */
        private suspend fun newSeasonsRow(userId: UUID): FindRow? =
            try {
                val seriesIds =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userId,
                                includeItemTypes = listOf(BaseItemKind.EPISODE),
                                recursive = true,
                                isPlayed = true,
                                sortBy = listOf(ItemSortBy.DATE_PLAYED),
                                sortOrder = listOf(SortOrder.DESCENDING),
                                limit = 300,
                            ),
                        ).content.items
                        .mapNotNull { it.seriesId }
                        .distinct()
                        .take(NEW_SEASON_SHOWS)
                if (seriesIds.isEmpty()) {
                    null
                } else {
                    val shows =
                        api.itemsApi
                            .getItems(
                                GetItemsRequest(userId = userId, ids = seriesIds, fields = listOf(ItemFields.PROVIDER_IDS)),
                            ).content.items
                    val today = LocalDate.now()
                    val semaphore = Semaphore(4)
                    val found =
                        coroutineScope {
                            shows
                                .map { dto ->
                                    async { semaphore.withPermit { newerSeasonOf(dto.id, dto.providerIds?.get("Tmdb"), userId, today) } }
                                }.mapNotNull { it.await() }
                        }
                    if (found.isEmpty()) null else FindRow(NEW_SEASONS_ROW, found)
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "New seasons row failed")
                null
            }

        /** The show, with "Season 4 is out" on it, when a season newer than Glowfin's has aired and isn't asked for */
        private suspend fun newerSeasonOf(
            seriesId: UUID,
            tmdb: String?,
            userId: UUID,
            today: LocalDate,
        ): DiscoverItem? {
            if (tmdb?.toIntOrNull() == null) return null
            return try {
                val json = seerrService.api.getRaw("/tv/$tmdb")?.let { Json.parseToJsonElement(it).jsonObject } ?: return null
                val lastHere =
                    api.tvShowsApi
                        .getSeasons(seriesId = seriesId, userId = userId)
                        .content.items
                        .mapNotNull { it.indexNumber }
                        .filter { it > 0 }
                        .maxOrNull() ?: return null
                // Seerr status per season: 1 unknown, 2 asked for, 3 on its way, 4 some, 5 here
                val statusBySeason =
                    ((json["mediaInfo"] as? JsonObject)?.get("seasons") as? JsonArray)
                        .orEmpty()
                        .mapNotNull { it as? JsonObject }
                        .mapNotNull { o ->
                            val n = o["seasonNumber"].str()?.toIntOrNull() ?: return@mapNotNull null
                            n to (o["status"].str()?.toIntOrNull() ?: 1)
                        }.toMap()
                val newer =
                    (json["seasons"] as? JsonArray)
                        .orEmpty()
                        .mapNotNull { it as? JsonObject }
                        .mapNotNull { o ->
                            val n = o["seasonNumber"].str()?.toIntOrNull() ?: return@mapNotNull null
                            val aired =
                                o["airDate"]
                                    .str()
                                    ?.take(10)
                                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                                    ?.let { !it.isAfter(today) } ?: false
                            if (n > lastHere && aired && (statusBySeason[n] ?: 1) <= 1) n else null
                        }.sorted()
                if (newer.isEmpty()) return null
                val words = if (newer.size == 1) "Season ${newer.first()} is out" else "Seasons ${newer.first()} to ${newer.last()} are out"
                itemFromJson(json, SeerrItemType.TV)?.copy(type = SeerrItemType.TV, subtitle = words)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Couldn't check new seasons of %s", tmdb)
                null
            }
        }

        /** Movies missing from collections you partly have, like the one Alien movie you don't have yet */
        private suspend fun finishCollectionRow(userId: UUID): FindRow? =
            try {
                val collectionIds =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userId,
                                includeItemTypes = listOf(BaseItemKind.BOX_SET),
                                recursive = true,
                                sortBy = listOf(ItemSortBy.RANDOM),
                                fields = listOf(ItemFields.PROVIDER_IDS),
                                limit = 25,
                            ),
                        ).content.items
                        .mapNotNull { it.providerIds?.get("Tmdb") ?: it.providerIds?.get("TmdbCollection") }
                        .distinct()
                val semaphore = Semaphore(4)
                val today = LocalDate.now()
                val missing =
                    coroutineScope {
                        collectionIds
                            .map { id ->
                                async {
                                    semaphore.withPermit {
                                        val parts = rawResults("/collection/$id", "parts", SeerrItemType.MOVIE)
                                        val haveSome = parts.any { it.availability == SeerrAvailability.AVAILABLE }
                                        if (!haveSome) {
                                            emptyList()
                                        } else {
                                            parts.filter {
                                                it.availability != SeerrAvailability.AVAILABLE &&
                                                    it.releaseDate?.isAfter(today) == false
                                            }
                                        }
                                    }
                                }
                            }.awaitAll()
                    }.flatten()
                        .distinctBy { it.id }
                        .take(30)
                if (missing.isEmpty()) null else FindRow("Finish your sets", missing)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Finish the collection row failed")
                null
            }

        /** Reads a list of titles straight from Seerr's JSON (for lists the generated client doesn't cover) */
        private suspend fun rawResults(
            path: String,
            field: String,
            fallback: SeerrItemType,
        ): List<DiscoverItem> =
            try {
                val json = seerrService.api.getRaw(path)?.let { Json.parseToJsonElement(it).jsonObject }
                (json?.get(field) as? JsonArray)
                    ?.mapNotNull { (it as? JsonObject)?.let { o -> itemFromJson(o, fallback) } }
                    .orEmpty()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not read %s", path)
                emptyList()
            }

        private suspend fun itemFromJson(
            o: JsonObject,
            fallback: SeerrItemType,
        ): DiscoverItem? {
            val id = o["id"].str()?.toIntOrNull() ?: return null
            val mediaInfo = o["mediaInfo"] as? JsonObject
            val status = mediaInfo?.get("status").str()?.toIntOrNull()
            val jellyfinId =
                (mediaInfo?.get("jellyfinMediaId4k").str() ?: mediaInfo?.get("jellyfinMediaId").str())?.toUUIDOrNull()
            val date = o["releaseDate"].str() ?: o["firstAirDate"].str()
            return DiscoverItem(
                id = id,
                type = SeerrItemType.fromString(o["mediaType"].str(), fallback),
                title = o["title"].str() ?: o["name"].str(),
                subtitle = null,
                overview = o["overview"].str(),
                availability = SeerrAvailability.from(status) ?: SeerrAvailability.UNKNOWN,
                releaseDate = date?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() },
                posterUrl = seerrService.createImageUrl(ImageType.PRIMARY, o["posterPath"].str(), null),
                backDropUrl = seerrService.createImageUrl(ImageType.BACKDROP, o["backdropPath"].str(), null),
                logoUrl = null,
                jellyfinItemId = jellyfinId,
                score = o["voteAverage"].str()?.toDoubleOrNull()?.takeIf { it > 0 },
            )
        }

        private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private val _hidden = MutableStateFlow(prefs().getStringSet(HIDDEN_KEY, emptySet()).orEmpty().toSet())

        /** Titles hidden with "Hide this", saved on this device */
        val hidden: StateFlow<Set<String>> = _hidden

        fun hide(item: DiscoverItem) {
            val next = _hidden.value + item.requestKey()
            _hidden.value = next
            prefs().edit().putStringSet(HIDDEN_KEY, next).apply()
        }

        private val _libraryKeys = MutableStateFlow<Set<String>>(emptySet())

        /**
         * Benflix (Ben, 2026-11-16): what's really on Benflix, straight from Jellyfin (Seerr's own "available"
         * often runs behind or is wrong), as "MOVIE-123" / "TV-456" keys to the Jellyfin id.
         */
        private val library =
            viewModelScope.async(WholphinDispatchers.IO) {
                libraryTmdb.keys().also { _libraryKeys.value = it.keys }
            }

        /** Titles found in Jellyfin, so they stay hidden while the show is still being checked */
        val libraryKeys: StateFlow<Set<String>> = _libraryKeys

        private val _onBenflix = MutableStateFlow<Map<String, Boolean>>(emptyMap())

        /** By request key: true = all here (hidden), false = a show here with aired episodes missing */
        val onBenflix: StateFlow<Map<String, Boolean>> = _onBenflix

        private val _ratings = MutableStateFlow<Map<String, ItemRating>>(emptyMap())

        /** Age ratings for the Family-friendly switch, looked up as needed */
        val ratings: StateFlow<Map<String, ItemRating>> = _ratings
        private val ratingsAsked = mutableSetOf<String>()

        fun loadRatings(items: List<DiscoverItem>) {
            val todo =
                synchronized(ratingsAsked) {
                    items.filter {
                        (it.type == SeerrItemType.MOVIE || it.type == SeerrItemType.TV) && ratingsAsked.add(it.requestKey())
                    }
                }
            if (todo.isEmpty()) return
            viewModelScope.launch(WholphinDispatchers.IO) {
                val semaphore = Semaphore(6)
                coroutineScope {
                    todo
                        .map { item ->
                            async {
                                semaphore.withPermit {
                                    val rating =
                                        try {
                                            val movie = item.type == SeerrItemType.MOVIE
                                            val raw = seerrService.api.getRaw(if (movie) "/movie/${item.id}" else "/tv/${item.id}")
                                            val o = raw?.let { Json.parseToJsonElement(it).jsonObject }
                                            if (o == null) {
                                                ItemRating(null, false)
                                            } else {
                                                val key = item.requestKey()
                                                val inJellyfin = library.await()[key]
                                                val here =
                                                    if (movie) {
                                                        val media = o["mediaInfo"] as? JsonObject
                                                        val status = (media?.get("status") as? JsonPrimitive)?.intOrNull
                                                        val seerrHas = status == SeerrAvailability.AVAILABLE.status
                                                        if (inJellyfin != null || seerrHas) true else null
                                                    } else {
                                                        showArrival.wholeShowHere(o, inJellyfin)
                                                    }
                                                if (here != null) _onBenflix.update { it + (key to here) }
                                                val age = if (movie) movieAgeRating(o) else tvAgeRating(o)
                                                if (isAdultContent(o, age)) AdultContentFlags.mark(item.requestKey())
                                                ItemRating(
                                                    age = age,
                                                    adultTags = o["adult"].str() == "true" || tags(o).any(::isGrownUpTag),
                                                )
                                            }
                                        } catch (ex: CancellationException) {
                                            throw ex
                                        } catch (ex: Exception) {
                                            ItemRating(null, false)
                                        }
                                    _ratings.update { it + (item.requestKey() to rating) }
                                }
                            }
                        }.awaitAll()
                }
            }
        }

        private val _scores = MutableStateFlow<Map<String, String>>(emptyMap())

        /** "IMDb 7.8 · 🍅 92%" for the card you're on, looked up when you land on it */
        val scores: StateFlow<Map<String, String>> = _scores
        private val scoresAsked = mutableSetOf<String>()

        fun loadScore(item: DiscoverItem) {
            if (item.type != SeerrItemType.MOVIE && item.type != SeerrItemType.TV) return
            val key = item.requestKey()
            if (!synchronized(scoresAsked) { scoresAsked.add(key) }) return
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    val movie = item.type == SeerrItemType.MOVIE
                    val raw = seerrService.api.getRaw(if (movie) "/movie/${item.id}/ratingscombined" else "/tv/${item.id}/ratings")
                    val o = raw?.let { Json.parseToJsonElement(it).jsonObject } ?: return@launch
                    val rt = if (movie) o["rt"] as? JsonObject else o
                    val imdb =
                        (o["imdb"] as? JsonObject)
                            ?.get("criticsScore")
                            .str()
                            ?.toDoubleOrNull()
                            ?.takeIf { it > 0 }
                    val critics =
                        rt
                            ?.get("criticsScore")
                            .str()
                            ?.toDoubleOrNull()
                            ?.toInt()
                            ?.takeIf { it > 0 }
                    val text =
                        listOfNotNull(
                            imdb?.let { "IMDb ${"%.1f".format(it)}" },
                            critics?.let { "🍅 $it%" },
                        ).joinToString(" · ")
                    if (text.isNotBlank()) _scores.update { it + (key to text) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.v(ex, "No scores for %s", key)
                }
            }
        }

        /** What's typed in the search box */
        val searchQuery = MutableStateFlow("")

        /** Which search results are anime (by their TMDB id) */
        val searchAnime = MutableStateFlow<Set<Int>>(emptySet())

        @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
        private val searchRaw: StateFlow<List<DiscoverItem>?> =
            searchQuery
                .debounce(450)
                .map { it.trim() }
                .distinctUntilChanged()
                .mapLatest { query ->
                    if (query.length < 2) {
                        null
                    } else {
                        try {
                            val raw = seerrService.search(query)
                            // Benflix redesign: search splits Movies, Shows and Anime (Japanese animation)
                            searchAnime.value =
                                raw
                                    .filter { it.genreIds?.contains(16) == true && it.originalLanguage == "ja" }
                                    .map { it.id }
                                    .toSet()
                            raw.map { seerrService.createDiscoverItem(it) }
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            Timber.w(ex, "Search failed")
                            emptyList()
                        }
                    }
                }.flowOn(WholphinDispatchers.IO)
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        /** Search results, with what's already asked for marked */
        val searchResults: StateFlow<List<DiscoverItem>?> =
            combine(searchRaw, requestedIds) { list, requested -> list?.map { markRequested(it, requested) } }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        private val _seasonPick = MutableStateFlow<SeasonPick?>(null)

        /** A show waiting for its seasons to be picked before asking */
        val seasonPick: StateFlow<SeasonPick?> = _seasonPick

        fun closeSeasonPick() {
            _seasonPick.value = null
        }

        /** Benflix: Ben couldn't find it, so it can't be asked for again */
        private suspend fun blocked(item: DiscoverItem): Boolean {
            if (item.requestKey() !in cantFindList.entries.value) return false
            showToast(context, "Ben couldn't find this one, so it can't be asked for again.")
            return true
        }

        /** Ask for a title. Movies go straight away; shows open the season picker first (Ben's pick) */
        fun ask(item: DiscoverItem) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                if (blocked(item)) return@launch
                try {
                    if (item.type == SeerrItemType.TV) {
                        val tv = seerrService.api.tvApi.tvTvIdGet(tvId = item.id)
                        // Seasons already on Benflix, downloading or asked for can't be picked again
                        val taken = mutableSetOf<Int>()
                        tv.mediaInfo?.seasons?.forEach { season ->
                            val availability = SeerrAvailability.from(season.status) ?: SeerrAvailability.UNKNOWN
                            if (availability != SeerrAvailability.UNKNOWN && availability != SeerrAvailability.DELETED) {
                                season.seasonNumber?.let { taken += it }
                            }
                        }
                        tv.mediaInfo
                            ?.requests
                            ?.filter {
                                it.is4k != true &&
                                    it.status != RequestStatus.DECLINED.status &&
                                    it.status != RequestStatus.FAILURE.status
                            }?.forEach { request -> request.seasons?.forEach { s -> s.seasonNumber?.let { taken += it } } }
                        val seasons =
                            tv.seasons
                                .orEmpty()
                                .mapNotNull { season ->
                                    val number = season.seasonNumber ?: return@mapNotNull null
                                    if (number <= 0) return@mapNotNull null
                                    PickableSeason(
                                        number = number,
                                        name = season.name?.takeIf { it.isNotBlank() } ?: "Season $number",
                                        episodes = season.episodeCount,
                                        taken = number in taken,
                                    )
                                }.sortedBy { it.number }
                        if (seasons.isEmpty() || seasons.all { it.taken }) {
                            showToast(context, "Every season is already asked for or on Glowfin")
                            return@launch
                        }
                        _seasonPick.value = SeasonPick(item, seasons)
                    } else {
                        seerrService.api.requestApi.requestPost(
                            RequestPostRequest(
                                mediaId = item.id,
                                mediaType = RequestPostRequest.MediaType.MOVIE,
                                is4k = false,
                                tags = emptyList(),
                            ),
                        )
                        asked(item)
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Quick ask failed for %s", item.id)
                    showToast(context, "Couldn't ask for that one. Try from its page.")
                }
            }
        }

        /** Ask for the seasons picked in the season picker */
        fun askSeasons(
            item: DiscoverItem,
            seasons: List<Int>,
            /** Glowfin new look: Tower also asks for every season that comes out later */
            everyNew: Boolean = false,
        ) {
            _seasonPick.value = null
            if (seasons.isEmpty()) return
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    seerrService.api.requestApi.requestPost(
                        RequestPostRequest(
                            mediaId = item.id,
                            mediaType = RequestPostRequest.MediaType.TV,
                            seasons = seasons,
                            is4k = false,
                            tags = emptyList(),
                        ),
                    )
                    asked(item)
                    if (everyNew) followNewSeasons(item)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Asking for seasons failed for %s", item.id)
                    showToast(context, "Couldn't ask for that one. Try from its page.")
                }
            }
        }

        /** Every new season from now on: Tower remembers the show and has Sonarr look for each new season */
        private suspend fun followNewSeasons(item: DiscoverItem) {
            try {
                val answer =
                    helper.postJson(
                        "/api/follow-show",
                        org.json
                            .JSONObject()
                            .put("tmdb", item.id)
                            .put("name", item.title.orEmpty()),
                    )
                answer.optString("message").takeIf { it.isNotBlank() }?.let { showToast(context, it) }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Couldn't follow new seasons of %s", item.id)
                showToast(context, "Asked for it, but Tower couldn't remember to get new seasons. Try again from its page.")
            }
        }

        private suspend fun asked(item: DiscoverItem) {
            requestedIds.update { it + item.requestKey() }
            showToast(context, "Asked for ${item.title ?: "it"}")
            // Benflix: show it in the request list straight away
            if (_requests.value != null) fetchRequests(lastMineOnly)
        }

        /** Everyone's requests (admin) or just your own */
        fun loadRequests(mineOnly: Boolean) {
            lastMineOnly = mineOnly
            _requestsFailed.value = false
            viewModelScope.launch(WholphinDispatchers.IO) { fetchRequests(mineOnly) }
        }

        /** Which list was last shown, so it can be fetched again after Approve / Decline */
        @Volatile
        private var lastMineOnly = false

        private suspend fun fetchRequests(mineOnly: Boolean) {
            run {
                try {
                    val myId = if (mineOnly) seerrServerRepository.currentUserId.first() else null
                    val results =
                        if (mineOnly) {
                            seerrService
                                .allRequests(requestedBy = myId, max = MINE_MAX)
                                .filter { it.requestedBy?.id == myId }
                        } else {
                            // Benflix: every waiting request (so they all show and Approve all covers them),
                            // plus the newest of the rest. Each one is looked up, so not all 500 of those.
                            val pending = seerrService.allRequests(filter = RequestApi.FilterRequestGet.PENDING)
                            val recent = seerrService.allRequests(max = RECENT_MAX)
                            val have = recent.mapNotNull { it.id }.toSet()
                            recent + pending.filter { it.id !in have }
                        }
                    val semaphore = Semaphore(4)
                    val entries =
                        coroutineScope {
                            results
                                .map { request ->
                                    async {
                                        semaphore.withPermit { toEntry(request) }
                                    }
                                }.awaitAll()
                        }
                    _requests.value = entries
                    _requestsFailed.value = false
                    if (!mineOnly) {
                        // Benflix: you're looking at them now, so they no longer light up the dot on your picture
                        RequestsWaiting.setWaiting(
                            entries.filter { it.status == RequestStatus.PENDING }.mapNotNull { it.request.id },
                        )
                        RequestsWaiting.markSeen()
                    }
                    // Benflix: arrived requests count as seen once you've opened Requests too
                    requestReady.refresh()
                    requestReady.markAllSeen()
                    // Benflix: "Couldn't find it" labels; ones that have since arrived are cleared
                    cantFindList.refresh()
                    if (isAdmin) {
                        val arrived = entries.filter { it.available }.mapNotNull { it.cantFindKey() }
                        val stillMissing = entries.filter { !it.available }.mapNotNull { it.cantFindKey() }.toSet()
                        cantFindList.forget(arrived.filter { it !in stillMissing })
                        // A week of "Still looking" with nothing found turns into "Couldn't find it" by itself
                        // (it fetches every request itself, not just the ones shown here)
                        if (!mineOnly) {
                            cantFindList.autoCheck()
                            // Benflix: the two-week "Look for it yourself" ones are on screen now, so no dot for them
                            RequestsWaiting.markSeen()
                        }
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not load requests")
                    // Keep what's showing if a refresh fails; with nothing showing yet, say so (not "No requests yet")
                    if (_requests.value == null) _requestsFailed.value = true
                }
            }
        }

        private suspend fun toEntry(request: MediaRequest): RequestEntry {
            val tmdbId = request.media?.tmdbId
            // Benflix: a show's seasons, to tell whether the seasons asked for are in
            var tvSeasons: List<Season>? = null
            val item =
                try {
                    when {
                        tmdbId == null -> {
                            null
                        }

                        SeerrItemType.fromString(request.type) == SeerrItemType.MOVIE -> {
                            seerrService.createDiscoverItem(seerrService.api.moviesApi.movieMovieIdGet(movieId = tmdbId))
                        }

                        else -> {
                            val tv = seerrService.api.tvApi.tvTvIdGet(tvId = tmdbId)
                            tvSeasons = tv.mediaInfo?.seasons
                            seerrService.createDiscoverItem(tv)
                        }
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not look up request %s", request.id)
                    null
                }
            val user = request.requestedBy
            // Benflix: a show waiting for a yes can still read as "unknown" in Seerr, so go by the request itself
            val status = RequestStatus.from(request.status)
            val shownItem =
                if (item != null && item.availability == SeerrAvailability.UNKNOWN && status != RequestStatus.DECLINED) {
                    item.copy(
                        availability =
                            if (status == RequestStatus.APPROVED) SeerrAvailability.PROCESSING else SeerrAvailability.PENDING,
                    )
                } else {
                    item
                }
            // Benflix: a show asked for by season is here once those seasons are, even if others are still missing
            val isMovie = SeerrItemType.fromString(request.type) == SeerrItemType.MOVIE
            var details: JsonObject? = null
            var available =
                request.media?.status == SeerrAvailability.AVAILABLE.status ||
                    item?.availability == SeerrAvailability.AVAILABLE ||
                    request.requestedArrived(tvSeasons ?: request.media?.seasons)
            // Benflix: a show is here once every asked-for episode that has aired is in, even when Seerr still
            // waits on an unannounced season, unaired episodes or different season splits
            if (!available && !isMovie && tmdbId != null && status != RequestStatus.DECLINED) {
                details = rawDetails("tv", tmdbId)
                available = showArrival.arrived(request, details)
            }
            // Benflix: how far the download is, for the progress steps
            var percent: Int? = null
            var downloading = false
            var clock: SearchClock? = null
            if (!available && status == RequestStatus.APPROVED && tmdbId != null) {
                try {
                    if (details == null) details = rawDetails(if (isMovie) "movie" else "tv", tmdbId)
                    clock = SearchClock.of(request, details)
                    val media = details?.get("mediaInfo") as? JsonObject
                    val downloads = (media?.get("downloadStatus") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                    if (downloads.isNotEmpty()) {
                        downloading = true
                        val size = downloads.sumOf { it["size"].str()?.toDoubleOrNull() ?: 0.0 }
                        val left = downloads.sumOf { it["sizeLeft"].str()?.toDoubleOrNull() ?: 0.0 }
                        if (size > 0) percent = (((size - left) / size) * 100).toInt().coerceIn(0, 99)
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.v(ex, "No download progress for %s", request.id)
                }
            }
            return RequestEntry(
                request = request,
                item = shownItem,
                who = user?.username ?: user?.email ?: "Someone",
                status = status,
                available = available,
                downloading = downloading,
                downloadPercent = percent,
                clock = clock,
            )
        }

        private fun rawDetails(
            kind: String,
            tmdbId: Int,
        ): JsonObject? =
            try {
                seerrService.api.getRaw("/$kind/$tmdbId")?.let { Json.parseToJsonElement(it).jsonObject }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.v(ex, "Could not look up %s %s", kind, tmdbId)
                null
            }

        /** Admin: say yes or no to a request */
        fun answer(
            entry: RequestEntry,
            approve: Boolean,
        ) {
            viewModelScope.launch(WholphinDispatchers.IO) { answerNow(entry, approve) }
        }

        /** Admin: yes to everything waiting */
        fun approveAll() {
            val waiting = _requests.value?.filter { it.status == RequestStatus.PENDING }.orEmpty()
            viewModelScope.launch(WholphinDispatchers.IO) {
                waiting.forEach { answerNow(it, true, refresh = false) }
                showToast(context, "Approved ${waiting.size}")
                fetchRequests(lastMineOnly)
            }
        }

        /** Admin, Manage: the same answer for several at once. A no asks for one reason for all of them. */
        fun answerMany(
            entries: List<RequestEntry>,
            approve: Boolean,
        ) {
            if (entries.isEmpty()) return
            viewModelScope.launch(WholphinDispatchers.IO) {
                entries.forEach { answerNow(it, approve, refresh = false) }
                showToast(context, if (approve) "Said yes to ${entries.size}" else "Said no to ${entries.size}")
                fetchRequests(lastMineOnly)
                if (!approve) {
                    val ids = entries.mapNotNull { it.request.id }
                    ids.firstOrNull()?.let { DeclineWhy.ask(it, ids.drop(1)) }
                }
            }
        }

        private suspend fun answerNow(
            entry: RequestEntry,
            approve: Boolean,
            refresh: Boolean = true,
        ) {
            val id = entry.request.id ?: return
            // Benflix: move it right away so the list doesn't look stuck, then check with Seerr
            val newStatus = if (approve) RequestStatus.APPROVED else RequestStatus.DECLINED
            _requests.update { list ->
                list?.map { if (it.request.id == id) it.copy(status = newStatus) else it }
            }
            RequestsWaiting.set(_requests.value?.count { it.status == RequestStatus.PENDING } ?: 0)
            try {
                seerrService.api.requestApi.requestRequestIdStatusPost(
                    requestId = id.toString(),
                    status =
                        if (approve) {
                            RequestApi.StatusRequestRequestIdStatusPost.APPROVE
                        } else {
                            RequestApi.StatusRequestRequestIdStatusPost.DECLINE
                        },
                )
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                // Jellyfin Enhanced answers in its own shape, which can land here even when it worked,
                // so the fresh list below decides whether it really went through
                Timber.w(ex, "Answer for request %s came back odd", id)
            }
            if (refresh) {
                fetchRequests(lastMineOnly)
                val still = _requests.value?.firstOrNull { it.request.id == id }
                if (still?.status == RequestStatus.PENDING) {
                    showToast(context, "Couldn't ${if (approve) "approve" else "decline"} that one. Try again.")
                } else if (!approve) {
                    DeclineWhy.ask(id)
                }
            }
        }

        companion object {
            /** Benflix: how many requests the list shows (all the waiting ones come on top of these) */
            private const val RECENT_MAX = 150
            private const val MINE_MAX = 200

            /** TMDB's Animation genre and its "anime" keyword, which together mean anime */
            const val ANIMATION_GENRE = "16"
            const val ANIME_KEYWORD = "210024"
            private const val PREFS = "benflix_requests"
            private const val HIDDEN_KEY = "hidden"
        }
    }

/** Benflix: set before opening Requests to land on the Music tab (Ben's "Ask for music" tile) */
object RequestsJump {
    @Volatile
    var music = false

    /** Benflix Books: "Ask for a book" on the Books door lands on the Books tab */
    @Volatile
    var books = false
}

/**
 * Benflix Requests tab.
 * Admins: Find new stuff (Movies, Shows, Anime with Trending, Popular, Coming soon and Top rated, plus genre
 * buttons and "Hide what's on Benflix"), and everyone's requests with Approve / Decline.
 * Everyone else: the same Movies, Shows and Anime tabs, and their own requests (no Approve / Decline).
 */

@Composable
fun BenflixRequestsPage(
    modifier: Modifier = Modifier,
    viewModel: BenflixRequestsViewModel = hiltViewModel(),
) {
    val active by viewModel.active.collectAsState(false)
    // Loads why Ben said no (shown on declined requests), and asks Ben "Tell them why?" after a decline
    DeclineWhyHost()
    if (!active) {
        NotConnected(viewModel, modifier)
        return
    }
    val admin = viewModel.isAdmin
    // Redesign 2 (Ben 2026-11-10): the same tabs for everyone who can ask (Kids and Guest never see Requests).
    // Grown-up family profiles see Music too; their song and album asks wait for Ben's Approve.
    val tabs =
        remember {
            // Ben's last tab is his Manage board (2026-11-21); everyone else has Your asks
            REQUEST_TABS.map { if (admin && it == "Your asks") "Manage" else it }
        }
    val waitingCount = rememberWaitingCount(viewModel, admin)
    val tabsId = "benflix-requests"
    val tabViewModel =
        hiltViewModel<TabViewModel, TabViewModel.Factory>(
            key = "$tabsId-${tabs.size}",
            creationCallback = { it.create(tabsId, tabs.size) },
        )
    // Requests opens on the tab you used last (always, whatever the "remember tabs" setting says).
    // "Ask for music" on the Music door and "Ask for a book" on the Books door still jump to their tab.
    val context = LocalContext.current
    val tabState by tabViewModel.state.collectAsState()
    var startPicked by remember { mutableStateOf(false) }
    LaunchedEffect(tabState) {
        // Wait for the tab page to be ready, then pick the starting tab once
        if (tabState == UNSET) return@LaunchedEffect
        if (!startPicked) {
            startPicked = true
            val jump =
                when {
                    RequestsJump.music -> 3
                    RequestsJump.books -> 4
                    else -> null
                }
            RequestsJump.music = false
            RequestsJump.books = false
            val start = (jump ?: RequestsLastTab.get(context, viewModel.userKey))?.coerceIn(0, tabs.lastIndex)
            if (start != null && start != tabState) {
                tabViewModel.updateSelectedTabIndex(start)
                return@LaunchedEffect
            }
        }
        if (tabState in tabs.indices) RequestsLastTab.set(context, viewModel.userKey, tabState)
    }
    // Glowfin new look (Ben approved 2026-11-22): the tabs are see-through pills, then the round Search and
    // Filters pills and Ben's yellow Waiting pill. TV: in the header's own row, with the page (and its art)
    // going up behind them. Phones: one sideways row of pills under the header.
    val isTv = rememberIsTv()
    val selected = tabState
    val showSearch = isTv && selected in 0 until tabs.lastIndex
    val showFilters = selected == 0 || selected == 1
    val waiting = if (admin) waitingCount else null
    val onWaiting = { tabViewModel.updateSelectedTabIndex(tabs.lastIndex) }
    if (isTv) {
        val overlap = LocalTopBarOverlap.current
        Box(modifier.fillMaxSize()) {
            if (selected in tabs.indices) {
                RequestTab(selected, viewModel, admin, top = overlap)
            } else {
                DelayedLoadingPage(focusEnabled = false)
            }
            RequestsPillBar(
                tabs = tabs,
                selected = selected,
                onTab = tabViewModel::updateSelectedTabIndex,
                showSearch = showSearch,
                showFilters = showFilters,
                waitingCount = waiting,
                onWaiting = onWaiting,
                wide = true,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(top = PILLS_TOP, end = CLOCK_ROOM)
                        .height((overlap - PILLS_GAP).coerceAtLeast(40.dp)),
            )
        }
    } else {
        Column(modifier.fillMaxSize()) {
            RequestsPillBar(
                tabs = tabs,
                selected = selected,
                onTab = tabViewModel::updateSelectedTabIndex,
                showSearch = false,
                showFilters = showFilters,
                waitingCount = waiting,
                onWaiting = onWaiting,
                wide = false,
                modifier = Modifier.padding(vertical = 8.dp).height(40.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (selected in tabs.indices) {
                    RequestTab(selected, viewModel, admin, top = 0.dp)
                } else {
                    DelayedLoadingPage(focusEnabled = false)
                }
            }
        }
    }
}

/** The TV header's row: 18dp from the top, with 6dp under it (see BenflixPageHeader) */
private val PILLS_TOP = 18.dp
private val PILLS_GAP = 24.dp

/** Room kept on the right of the pills for the big clock */
private val CLOCK_ROOM = 184.dp

/**
 * Glowfin R1: how many asks are waiting for Ben's Yes or No, across movies, shows, anime, music and books.
 * Everyone else gets 0 (only Ben approves).
 */
@Composable
private fun rememberWaitingCount(
    viewModel: BenflixRequestsViewModel,
    admin: Boolean,
): Int {
    if (!admin) return 0
    // The same lists Your requests uses, so they're only fetched once
    val musicVm: MusicRequestsViewModel = hiltViewModel(key = "requests-yours-music")
    val bookVm: BookRequestsViewModel = hiltViewModel(key = "requests-yours-books")
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        viewModel.loadRequests(mineOnly = false)
        musicVm.refresh()
        val job = scope.launch { bookVm.refresh() }
        onPauseOrDispose { job.cancel() }
    }
    val seerr by viewModel.requests.collectAsState()
    val music by musicVm.status.collectAsState()
    val books by bookVm.requests.collectAsState()
    return seerr.orEmpty().count { it.status == RequestStatus.PENDING && !it.available } +
        music?.requests.orEmpty().count { it.waiting } +
        books?.waiting.orEmpty().size
}

@Composable
private fun RequestTab(
    index: Int,
    viewModel: BenflixRequestsViewModel,
    admin: Boolean,
    /** TV: how far down the header (and the pills in it) reach; Movies and Shows draw their art up behind it */
    top: Dp,
) {
    when (index) {
        0 -> {
            FindShelf(FindSection.MOVIES, viewModel, top)
        }

        1 -> {
            FindShelf(FindSection.SHOWS, viewModel, top)
        }

        // Glowfin new look: Anime's art fills the page too, up behind the pills
        2 -> {
            FindShelf(FindSection.ANIME, viewModel, top)
        }

        // Glowfin new look: Music and Books draw their cover glow up behind the pills too
        3 -> {
            MusicShelf(top = top)
        }

        4 -> {
            BooksShelf(top = top)
        }

        5 -> {
            MangaShelf(top = top)
        }

        else -> {
            Box(Modifier.fillMaxSize().padding(top = top)) {
                when (index) {
                    // Ben: everyone's requests with Approve / Decline. Everyone else: what they asked for.
                    else -> RequestList(viewModel, mineOnly = !admin, arrivedOnly = null)
                }
            }
        }
    }
}

/** Requests tabs, in the order Ben kept (Movies, Shows, Anime, Music, Books), Manga (2026-11-22), then Your asks (Manage for Ben) */
private val REQUEST_TABS = listOf("Movies", "Shows", "Anime", "Music", "Books", "Manga", "Your asks")

/** The Requests tab each person used last, on this device */
internal object RequestsLastTab {
    private const val PREFS = "benflix_requests_tab"

    fun get(
        context: Context,
        user: String,
    ): Int? =
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(user, -1)
            .takeIf { it >= 0 }

    fun set(
        context: Context,
        user: String,
        tab: Int,
    ) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(user, tab)
            .apply()
    }
}

@Composable
private fun NotConnected(
    viewModel: BenflixRequestsViewModel,
    modifier: Modifier,
) {
    val canConnect by viewModel.canConnect.collectAsState()
    val connecting by viewModel.connecting.collectAsState()
    var askPassword by remember { mutableStateOf(false) }
    // Benflix: Ben signs in to Seerr right here in a pop-up, no trip to Settings > Advanced
    var showSignIn by remember { mutableStateOf(false) }
    val seerrConnection by viewModel.seerrConnection.collectAsState()
    if (showSignIn) {
        SeerrDialog(
            seerrDialogMode = SeerrDialogMode.Add,
            seerrConnection = seerrConnection,
            onDismissRequest = { showSignIn = false },
        )
    }
    if (askPassword) {
        PasswordDialog(
            onDismiss = { askPassword = false },
            onConnect = {
                askPassword = false
                viewModel.connect(it)
            },
        )
    }
    if (canConnect && !viewModel.isAdmin) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier.padding(32.dp),
        ) {
            Text(
                text = "Ask for movies and shows",
                fontSize = BenflixType.rowName,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text =
                    when (connecting) {
                        true -> "Connecting…"
                        false -> "That password didn't work. Try again, or ask Ben to check your account."
                        null -> "Type your Glowfin password once on this device and you can ask Ben for anything that's missing."
                    },
                fontSize = BenflixType.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { askPassword = true }, scale = ButtonDefaults.scale(focusedScale = 1f)) {
                Text("Type my password")
            }
        }
        return
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.padding(32.dp),
    ) {
        Text(
            text = "Requests aren't connected on this device yet",
            fontSize = BenflixType.rowName,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text =
                if (viewModel.isAdmin) {
                    "Sign in to Seerr here, or turn on Seerr in the Jellyfin Enhanced plugin on your Jellyfin " +
                        "server, and this tab fills up with things to add."
                } else {
                    "Ask Ben to turn on requests for your account, then you can ask for movies and shows here."
                },
            fontSize = BenflixType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (viewModel.isAdmin) {
            Button(onClick = { showSignIn = true }, scale = ButtonDefaults.scale(focusedScale = 1f)) {
                Text("Sign in to Seerr")
            }
        } else {
            Button(onClick = { viewModel.retry() }, scale = ButtonDefaults.scale(focusedScale = 1f)) {
                Text(if (connecting == true) "Connecting…" else "Try again")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FindShelf(
    section: FindSection,
    viewModel: BenflixRequestsViewModel,
    /** TV: how far down the header reaches (the art goes up behind it, the words start under it) */
    top: Dp,
) {
    var filters by remember(section) { mutableStateOf(FindFilters()) }
    // Redesign 2: titles already on Benflix never show in the lists; "N more are already on Benflix" at the
    // end of a list opens them for that list only
    var ownedOpen by rememberSaveable(section) { mutableStateOf<String?>(null) }
    var hideAsked by rememberSaveable { mutableStateOf(false) }
    // Glowfin new look: "Hide what's already on Glowfin" is a switch in the Filters panel (on by default)
    var hideOwned by rememberSaveable { mutableStateOf(true) }
    // Benflix: family accounts start with Family-friendly only on (like their search), and can turn it off
    var familyOnly by rememberSaveable { mutableStateOf(!viewModel.isAdmin) }
    var showFilters by remember { mutableStateOf(false) }
    var menuItem by remember { mutableStateOf<DiscoverItem?>(null) }
    val key = viewModel.key(section, filters)
    // Benflix: the Anime tab has its own lists from AniList (AnimeShelf), not TMDB's
    LaunchedEffect(key) { if (section != FindSection.ANIME) viewModel.loadSection(section, filters) }
    LaunchedEffect(section) { viewModel.loadSpecial(section) }
    // Coming back from a details page: pick up anything just asked for
    LifecycleResumeEffect(Unit) {
        viewModel.refreshRequested()
        onPauseOrDispose { }
    }
    val allRows by viewModel.rows.collectAsState()
    val failedRows by viewModel.failedRows.collectAsState()
    val special by viewModel.special.collectAsState()
    val hidden by viewModel.hidden.collectAsState()
    val ratings by viewModel.ratings.collectAsState()
    val scores by viewModel.scores.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchAnime by viewModel.searchAnime.collectAsState()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    // Glowfin R1: Search on the top line opens this tab's search
    OnRequestsSearch { searchOpen = true }
    // Glowfin new look: the round Filters pill opens the right-hand Filters panel
    OnRequestsFilters { showFilters = true }
    val baseRows = allRows[key]
    // Benflix: "Because you watched" goes after the first row, "Finish the collection" after Popular
    val rows =
        remember(baseRows, special, section, filters) {
            if (baseRows == null || filters.narrowed || filters.genre != null) {
                baseRows
            } else {
                val extra = special[section].orEmpty()
                val because = extra.filter { it.title.startsWith("Because") }
                val collection = extra.filter { !it.title.startsWith("Because") }
                // Approved order: Trending, Coming soon, Because you watched, Finish the collection, Top rated
                orderRequestRows(baseRows + because + collection)
            }
        }
    // Benflix (Ben, 2026-11-16): anything already on Benflix is hidden (checked against Jellyfin itself, not
    // only Seerr). A show that's here but missing aired episodes stays, marked "MISSING EPISODES".
    val onBenflix by viewModel.onBenflix.collectAsState()
    val libraryKeys by viewModel.libraryKeys.collectAsState()
    val visibleWith: (List<DiscoverItem>, Boolean) -> List<DiscoverItem> = { items, withOwned ->
        items.map { item -> markOnBenflix(item, onBenflix[item.requestKey()]) }.filter { item ->
            val here = onBenflix[item.requestKey()]
            // Found in Jellyfin but the show isn't checked yet: keep it out until we know it's missing episodes
            val stillChecking = here == null && item.requestKey() in libraryKeys
            (withOwned || !hideOwned || (item.availability != SeerrAvailability.AVAILABLE && !stillChecking)) &&
                (
                    !hideAsked ||
                        (item.availability != SeerrAvailability.PENDING && item.availability != SeerrAvailability.PROCESSING)
                ) &&
                item.requestKey() !in hidden &&
                (!familyOnly || item.type == SeerrItemType.PERSON || ratings[item.requestKey()]?.familyFriendly(item.type) == true)
        }
    }
    val visible: (List<DiscoverItem>) -> List<DiscoverItem> = { items -> visibleWith(items, false) }
    // Benflix: always look up details, for the Family-friendly switch and the "Adult content" banner
    LaunchedEffect(rows, searchResults) {
        viewModel.loadRatings(rows.orEmpty().flatMap { it.items } + searchResults.orEmpty())
    }
    val openItem: (DiscoverItem) -> Unit = { viewModel.navigationManager.navigateTo(it.destination) }
    val scoreLine: (DiscoverItem) -> String? = { item ->
        listOfNotNull(
            item.releaseDate?.year?.toString(),
            scores[item.requestKey()] ?: item.score?.let { "★ ${"%.1f".format(it)}" },
        ).joinToString(" · ").ifBlank { null }
    }
    if (showFilters) {
        RequestFiltersPanel(
            section = section,
            state = RequestFilterState(filters, hideOwned = hideOwned, hideAsked = hideAsked, familyOnly = familyOnly),
            isAdmin = viewModel.isAdmin,
            showLanguage = !viewModel.viaJellyfin,
            onChange = {
                filters = it.filters
                hideOwned = it.hideOwned
                hideAsked = it.hideAsked
                familyOnly = it.familyOnly
            },
            onDismiss = { showFilters = false },
        )
    }
    // Who asked and when ("Mia 2 days ago"), for the status tag at the top
    LaunchedEffect(Unit) { if (viewModel.requests.value == null) viewModel.loadRequests(mineOnly = !viewModel.isAdmin) }
    val requests by viewModel.requests.collectAsState()
    val askedBy =
        remember(requests) {
            requests
                .orEmpty()
                .mapNotNull { e ->
                    e.item?.let {
                        it.requestKey() to
                            listOfNotNull(
                                (if (viewModel.isAdmin) e.who else "you").takeIf { w -> w.isNotBlank() },
                                ago(e.request.createdAt),
                            ).joinToString(" ")
                    }
                }.filter { it.second.isNotBlank() }
                .toMap()
        }
    val cantFindKeys by CantFindBlocked.keys.collectAsState()
    val seasonPick by viewModel.seasonPick.collectAsState()
    seasonPick?.let { pick ->
        SeasonAskPanel(
            pick = pick,
            onDismiss = viewModel::closeSeasonPick,
            onAsk = { viewModel.askSeasons(pick.item, it) },
            onAskEvery = { viewModel.askSeasons(pick.item, it, everyNew = true) },
        )
    }
    menuItem?.let { item ->
        QuickMenuDialog(
            item = item,
            onDismiss = { menuItem = null },
            onAsk = {
                menuItem = null
                viewModel.ask(item)
            },
            onInfo = {
                menuItem = null
                openItem(item)
            },
            onHide = {
                menuItem = null
                viewModel.hide(item)
            },
        )
    }
    val searching = query.trim().length >= 2
    val isTv = rememberIsTv()
    // Benflix redesign: on TV a Movies-door top area describes the poster you're on. Only the top area
    // reads this, so moving along a row doesn't redraw the whole page.
    val focusedItem = remember(section) { mutableStateOf<DiscoverItem?>(null) }
    val onFocusPoster: (DiscoverItem) -> Unit =
        remember(section, isTv) {
            { item ->
                if (isTv) focusedItem.value = item
                viewModel.loadScore(item)
            }
        }
    if (isTv && searchOpen) {
        RequestSearchScreen(
            query = query,
            onQuery = { viewModel.searchQuery.value = it },
            results = searchResults,
            anime = searchAnime,
            visible = visible,
            onOpen = openItem,
            onMenu = { if (it.type != SeerrItemType.PERSON) menuItem = it },
            onFocus = { viewModel.loadScore(it) },
            onClose = {
                searchOpen = false
                viewModel.searchQuery.value = ""
            },
        )
        return
    }
    // Benflix (anime Requests design, Ben 2026-11-12): the Anime tab has its own side list of anime ways in
    if (section == FindSection.ANIME) {
        val onMenu: (DiscoverItem) -> Unit = { menuItem = it }
        if (isTv) {
            AnimeShelf(
                isTv = true,
                onAsk = viewModel::ask,
                onMenu = onMenu,
                top = top,
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                RequestsSearchBox(
                    query = query,
                    onQuery = { viewModel.searchQuery.value = it },
                    placeholder = "Search anime to ask for",
                )
                if (searching) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        searchShelves(
                            results = searchResults,
                            anime = searchAnime,
                            visible = visible,
                            scoreLine = scoreLine,
                            onOpen = openItem,
                            onMenu = { menuItem = it },
                            onFocusItem = onFocusPoster,
                            isTv = false,
                        )
                    }
                } else {
                    AnimeShelf(
                        isTv = false,
                        onAsk = viewModel::ask,
                        onMenu = onMenu,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        return
    }
    // The lists that have something to show (Redesign 2 names and order). Trending is split into a ranked
    // Top 10 and the rest; Popular joins the end of Trending so the wall stays long.
    val shownRows =
        remember(rows, section, filters, hidden, ratings, hideAsked, familyOnly, ownedOpen, hideOwned) {
            val base = rows.orEmpty()
            val popular = base.firstOrNull { it.title == "Popular" || it.title == "Popular anime shows" }?.items.orEmpty()
            val out = mutableListOf<ShownRow>()

            fun add(
                title: String,
                items: List<DiscoverItem>,
                ranked: Boolean = false,
                comingSoon: Boolean = false,
            ) {
                val seen = HashSet<String>()
                val unique = items.filter { seen.add(it.requestKey()) }
                // (New seasons for shows you watch: those shows are on Glowfin, so they always stay)
                val shown = visibleWith(unique, ownedOpen == title || title == NEW_SEASONS_ROW)
                val owned = visibleWith(unique, true).count { it.availability == SeerrAvailability.AVAILABLE }
                if (shown.isNotEmpty() ||
                    owned > 0
                ) {
                    out += ShownRow(title, shown, comingSoon, ranked, ownedCount = if (hideOwned && title != NEW_SEASONS_ROW) owned else 0)
                }
            }
            base.forEach { row ->
                val t = row.title
                when {
                    t.startsWith("Trending") -> {
                        val topName =
                            when {
                                filters.genre != null -> "Top 10 in ${filters.genre?.label}"
                                section == FindSection.ANIME -> "Top 10 anime"
                                else -> "Top 10 to ask for this week"
                            }
                        val top = visibleWith(row.items, false).take(10)
                        add(topName, top, ranked = true)
                        val more = if (section == FindSection.ANIME) emptyList() else popular
                        val rest = (row.items + more).filter { r -> top.none { it.requestKey() == r.requestKey() } }
                        add(if (section == FindSection.ANIME) "This season" else "Trending", rest)
                    }

                    t == "Popular anime shows" -> {
                        add("Popular", row.items)
                    }

                    t == "Popular" -> {}

                    section == FindSection.ANIME && (t.startsWith("Coming soon") || t.startsWith("Top rated")) -> {}

                    t.startsWith("Coming soon") -> {
                        add(
                            t,
                            row.items
                                .filter { it.releaseDate == null || !it.releaseDate.isBefore(LocalDate.now()) }
                                .sortedBy { it.releaseDate ?: LocalDate.MAX },
                            comingSoon = true,
                        )
                    }

                    else -> {
                        add(t, row.items)
                    }
                }
            }
            // Glowfin R1 order: Top 10 to ask for, Coming soon, Trending, then the rest
            out.sortedBy { r ->
                when {
                    r.title == NEW_SEASONS_ROW -> -1
                    r.ranked -> 0
                    r.comingSoon -> 1
                    r.title == "Trending" || r.title == "This season" -> 2
                    else -> 3
                }
            }
        }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Glowfin new look (Ben approved 2026-11-22): TV lands straight on the first row. The art of the cover
        // you're on fills the whole page (up behind the tabs), and the top only describes that cover. About half
        // the screen, like Home and Movies, but never so tall that the first row doesn't fit whole under it.
        val topHeight = (maxHeight * TOP_SHARE).coerceIn(top + TOP_AREA_MIN, (maxHeight - TV_ROW_ROOM).coerceAtLeast(top + TOP_AREA_MIN))
        val shown =
            if (isTv) rememberShownCover(focusedItem, shownRows.firstOrNull()?.items?.firstOrNull()) else null
        if (isTv) {
            RequestArt(shown)
        }
        Column(Modifier.fillMaxSize()) {
            if (!isTv) RequestsSearchBox(query = query, onQuery = { viewModel.searchQuery.value = it })
            if (isTv) {
                Box(Modifier.fillMaxWidth().height(topHeight)) {
                    if (shown != null) {
                        RequestTopWords(
                            item = shown,
                            tag = shown.rqTag(viewModel.isAdmin, shown.requestKey() in cantFindKeys, askedBy[shown.requestKey()]),
                            age = ratings[shown.requestKey()]?.age,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = top + 2.dp),
                            scores = scores[shown.requestKey()],
                        )
                    }
                }
            }
            if (filters.narrowed || filters.genre != null) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    filters.genre?.let { g -> item { ActiveFilterChip(g.label) { filters = filters.copy(genre = null) } } }
                    filters.decade?.let { d -> item { ActiveFilterChip(d.label) { filters = filters.copy(decade = null) } } }
                    filters.minScore?.let { m -> item { ActiveFilterChip("Score $m+") { filters = filters.copy(minScore = null) } } }
                    filters.sort?.let { o -> item { ActiveFilterChip(o.label) { filters = filters.copy(sort = null) } } }
                    filters.language?.let { l -> item { ActiveFilterChip(l.label) { filters = filters.copy(language = null) } } }
                }
            }
            // Like Movies and Shows: the row you move to jumps up whole (name and all) right under the top area
            val defaultSpec = LocalBringIntoViewSpec.current
            val toTop = remember { ScrollToTopBringIntoViewSpec(0f) }
            CompositionLocalProvider(LocalBringIntoViewSpec provides if (isTv) toTop else defaultSpec) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(if (isTv) 18.dp else 14.dp),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 48.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    if (!isTv && searching) {
                        searchShelves(
                            results = searchResults,
                            anime = searchAnime,
                            visible = visible,
                            scoreLine = scoreLine,
                            onOpen = openItem,
                            onMenu = { menuItem = it },
                            onFocusItem = onFocusPoster,
                            isTv = false,
                        )
                        return@LazyColumn
                    }
                    when {
                        rows == null -> {
                            item(key = "loading") { LoadingPage(Modifier.height(240.dp), focusEnabled = false) }
                        }

                        shownRows.isEmpty() -> {
                            item(key = "empty") {
                                if (key in failedRows) {
                                    LoadFailed(text = "These didn't load.", onRetry = { viewModel.loadSection(section, filters) })
                                } else {
                                    Text(
                                        text = "Nothing matches. Try fewer filters.",
                                        fontSize = BenflixType.lead,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                                    )
                                }
                            }
                        }

                        else -> {
                            items(shownRows, key = { "row-${it.title}" }) { row ->
                                CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                                    RequestDoorRow(
                                        title = row.title,
                                        items = row.items,
                                        isTv = isTv,
                                        onClick = openItem,
                                        onLongClick = { item ->
                                            // Hold OK asks straight away: movies need no choice, shows pick seasons
                                            if (item.type == SeerrItemType.PERSON) openItem(item) else viewModel.ask(item)
                                        },
                                        onFocus = onFocusPoster,
                                        cornerNoteFor = { if (row.comingSoon) comingSoonNote(it) else null },
                                        subtitleFor = scoreLine,
                                        ranked = row.ranked,
                                        trailing =
                                            if (row.ownedCount > 0) {
                                                {
                                                    item(key = "owned") {
                                                        OwnedMoreButton(row.ownedCount, ownedOpen == row.title, onClick = {
                                                            ownedOpen = if (ownedOpen == row.title) null else row.title
                                                        })
                                                    }
                                                }
                                            } else {
                                                null
                                            },
                                    )
                                }
                            }
                        }
                    }
                    // (Glowfin new look: Genres, Hide what's asked for and Family-friendly moved into the Filters panel)
                }
            }
        }
    }
}

/** A row of chips with a name over it, lined up with the poster rows */
@Composable
internal fun RequestChipRow(
    title: String,
    content: LazyListScope.() -> Unit,
) {
    // (TV: the name scrolls into view with its chips)
    Column(Modifier.fillMaxWidth().scrollAsOneBlock()) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont,
            fontSize = BenflixType.lead,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            content = content,
        )
    }
}

/** Glowfin new look: the Shows tab's first row */
internal const val NEW_SEASONS_ROW = "New seasons for shows you watch"

/** How many of the shows you watched last are checked for a newer season */
private const val NEW_SEASON_SHOWS = 20

/** The TV top area's share of the screen, like Home and Movies (Glowfin new look) */
private const val TOP_SHARE = 0.52f

/** The least the top area needs for its name, info line and two lines of story */
private val TOP_AREA_MIN = 116.dp

/** One whole TV row under the top area: its name, a 180 tall poster with its glow ring, and the gap above */
private val TV_ROW_ROOM = 236.dp

private class ShownRow(
    val title: String,
    val items: List<DiscoverItem>,
    val comingSoon: Boolean,
    val ranked: Boolean = false,
    /** Titles in this list already on Benflix (hidden unless opened) */
    val ownedCount: Int = 0,
)

/** Benflix: quick picks on the Requests Anime tab; each opens the anime deep search with it picked */
internal val ANIME_QUICK_PICKS =
    listOf(
        "Isekai",
        "Fantasy",
        "Harem",
        "Reverse Harem",
        "Mixed Gender Harem",
        "Action",
        "Romance",
        "Comedy",
        "Slice of Life",
        "Shounen",
        "Seinen",
        "Shoujo",
        "Reincarnation",
        "Overpowered Main Characters",
        "Magic",
        "Super Power",
        "Martial Arts",
        "School",
        "Mecha",
        "Sci-Fi",
        "Psychological",
        "Horror",
        "Mystery",
        "Sports",
        "Iyashikei",
        "Ecchi",
    )

/** Benflix: something didn't load, with a Try again button (instead of looking empty) */
@Composable
internal fun LoadFailed(
    text: String,
    onRetry: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            text = text,
            fontSize = BenflixType.lead,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRetry, scale = ButtonDefaults.scale(focusedScale = 1f)) {
            Text("Try again", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Search results as rows: Movies, Shows and People */
private fun LazyListScope.searchShelves(
    results: List<DiscoverItem>?,
    anime: Set<Int> = emptySet(),
    visible: (List<DiscoverItem>) -> List<DiscoverItem>,
    scoreLine: (DiscoverItem) -> String?,
    onOpen: (DiscoverItem) -> Unit,
    onMenu: (DiscoverItem) -> Unit,
    onFocusItem: (DiscoverItem) -> Unit,
    isTv: Boolean,
) {
    if (results == null) {
        item { LoadingPage(Modifier.height(200.dp), focusEnabled = false) }
        return
    }
    // Benflix redesign: Movies, Shows and Anime kept apart; titles already on Benflix are left out
    val (doors, people) = splitRequestResults(visible(results), anime)
    val groups =
        (doors.map { (door, list) -> door.title to list } + ("People" to people))
            .filter { it.second.isNotEmpty() }
    if (groups.isEmpty()) {
        item {
            Text(
                text = "No matches. Check the spelling, or try a shorter name.",
                fontSize = BenflixType.lead,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            )
        }
        return
    }
    items(groups, key = { "search-${it.first}" }) { (title, list) ->
        RequestDoorRow(
            title = title,
            items = list,
            isTv = isTv,
            onClick = onOpen,
            onLongClick = { if (it.type != SeerrItemType.PERSON) onMenu(it) },
            onFocus = onFocusItem,
            subtitleFor = { if (it.type == SeerrItemType.PERSON) null else scoreLine(it) },
        )
    }
}

/**
 * A list of requests, grouped by where they're at.
 * [arrivedOnly] true = only ones now on Benflix, false = only ones still on the way, null = all
 */
@Composable
private fun RequestList(
    viewModel: BenflixRequestsViewModel,
    mineOnly: Boolean,
    arrivedOnly: Boolean?,
    showSearch: Boolean = false,
) {
    val isTv = rememberIsTv()
    LifecycleResumeEffect(mineOnly) {
        viewModel.loadRequests(mineOnly)
        onPauseOrDispose { }
    }
    val all by viewModel.requests.collectAsState()
    val requestsFailed by viewModel.requestsFailed.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val ratings by viewModel.ratings.collectAsState()
    val cantFind by viewModel.cantFind.collectAsState()
    val myId by viewModel.mySeerrId.collectAsState(initial = null)
    // Redesign 2: Your requests holds every kind (movies, shows, songs, albums, books), grouped by status
    val musicVm: MusicRequestsViewModel = hiltViewModel(key = "requests-yours-music")
    val bookVm: BookRequestsViewModel = hiltViewModel(key = "requests-yours-books")
    val musicStatus by musicVm.status.collectAsState()
    val bookRequests by bookVm.requests.collectAsState()
    val otherScope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        musicVm.refresh()
        val job = otherScope.launch { bookVm.refresh() }
        onPauseOrDispose { job.cancel() }
    }
    val otherLines =
        remember(musicStatus, bookRequests, mineOnly) {
            val music =
                musicStatus?.requests.orEmpty().map { r ->
                    r.toAskLine(
                        showWho = !mineOnly,
                        onAnswer = if (!mineOnly && r.waiting) ({ yes -> musicVm.answer(r, yes) }) else null,
                        onRetry = if (!mineOnly) ({ musicVm.retry(r) }) else null,
                        onRemove = if (!mineOnly && viewModel.isAdmin && !r.waiting) ({ musicVm.remove(r) }) else null,
                    )
                }
            val b = bookRequests
            val books =
                if (b == null) {
                    emptyList()
                } else {
                    val waitingIds = b.waiting.map { it.id }.toSet()
                    (if (mineOnly) b.mine else b.waiting + b.mine + b.others)
                        .distinctBy { it.id }
                        .map { r ->
                            r.toAskLine(
                                showWho = !mineOnly,
                                onOpen = r.titleId?.let { id -> { bookVm.open(id) } },
                                onAnswer = if (!mineOnly && r.id in waitingIds) ({ yes -> bookVm.answer(r, yes) }) else null,
                                onRetry = { bookVm.retry(r) },
                            )
                        }
                }
            music + books
        }
    var confirmAll by remember { mutableStateOf(false) }
    var autoYes by remember { mutableStateOf(false) }
    if (autoYes) AutoYesDialog(onDismiss = { autoYes = false })
    // Manage (Ben 2026-11-21): Ben's list is a board. One box picked, filters by person and kind, several at once.
    val manage = !mineOnly && viewModel.isAdmin
    val manageVm: ManageViewModel = hiltViewModel()
    val holds by manageVm.holds.collectAsState()
    var lane by rememberSaveable { mutableStateOf(ManageLane.NEEDS) }
    var who by rememberSaveable { mutableStateOf<String?>(null) }
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var ticks by remember { mutableStateOf(setOf<String>()) }
    var stopFor by remember { mutableStateOf<RequestEntry?>(null) }
    stopFor?.let { entry ->
        val id = entry.request.id
        val name = entry.item?.title ?: "this one"
        BenflixChoiceDialog(
            title = "Stop $name?",
            subtitle = "Tower stops looking and ${entry.who} sees it as a no. You can give a reason next.",
            choices =
                listOf(
                    BenflixChoice(
                        label = "Stop it",
                        note = "Anything that already arrived stays on Glowfin",
                        onClick = { if (id != null) manageVm.stop(id, false) { viewModel.loadRequests(mineOnly) } },
                    ),
                    BenflixChoice(
                        label = "Stop it and delete what arrived",
                        onClick = { if (id != null) manageVm.stop(id, true) { viewModel.loadRequests(mineOnly) } },
                    ),
                    BenflixChoice(label = "Keep looking", onClick = {}),
                ),
            onDismissRequest = { stopFor = null },
        )
    }
    var cantFindFor by remember { mutableStateOf<RequestEntry?>(null) }
    cantFindFor?.let { entry ->
        CantFindDialog(
            title = entry.item?.title,
            onDismiss = { cantFindFor = null },
            onMark = { note ->
                cantFindFor = null
                viewModel.markCantFind(entry, note)
            },
        )
    }
    var menuItem by remember { mutableStateOf<DiscoverItem?>(null) }
    // Benflix: family search shows family-friendly titles only (Ben's pick)
    val familySearch = !viewModel.isAdmin
    LaunchedEffect(searchResults) {
        viewModel.loadRatings(searchResults.orEmpty())
    }
    LaunchedEffect(all) {
        viewModel.loadRatings(all.orEmpty().mapNotNull { it.item })
    }
    val list =
        all?.filter {
            when (arrivedOnly) {
                null -> true
                true -> it.available
                false -> !it.available
            }
        }
    val waiting = list.orEmpty().count { it.status == RequestStatus.PENDING && !it.available }
    if (confirmAll) {
        ApproveAllDialog(
            count = waiting,
            onDismiss = { confirmAll = false },
            onConfirm = {
                confirmAll = false
                viewModel.approveAll()
            },
        )
    }
    val seasonPick by viewModel.seasonPick.collectAsState()
    seasonPick?.let { pick ->
        SeasonAskPanel(
            pick = pick,
            onDismiss = viewModel::closeSeasonPick,
            onAsk = { viewModel.askSeasons(pick.item, it) },
            onAskEvery = { viewModel.askSeasons(pick.item, it, everyNew = true) },
        )
    }
    menuItem?.let { item ->
        QuickMenuDialog(
            item = item,
            onDismiss = { menuItem = null },
            onAsk = {
                menuItem = null
                viewModel.ask(item)
            },
            onInfo = {
                menuItem = null
                viewModel.navigationManager.navigateTo(item.destination)
            },
            onHide = {
                menuItem = null
                viewModel.hide(item)
            },
        )
    }
    val familyVisible: (List<DiscoverItem>) -> List<DiscoverItem> = { items ->
        if (familySearch) {
            items.filter {
                it.type == SeerrItemType.PERSON || ratings[it.requestKey()]?.familyFriendly(it.type) == true
            }
        } else {
            items
        }
    }
    // Benflix polish: on TV the family list searches with the same full-screen Benflix keyboard page
    // as the main Requests page, instead of the old typing box
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val searchAnime by viewModel.searchAnime.collectAsState()
    if (isTv && showSearch && searchOpen) {
        RequestSearchScreen(
            query = query,
            onQuery = { viewModel.searchQuery.value = it },
            results = searchResults,
            anime = searchAnime,
            visible = familyVisible,
            onOpen = { viewModel.navigationManager.navigateTo(it.destination) },
            onMenu = { if (it.type != SeerrItemType.PERSON) menuItem = it },
            onFocus = {},
            onClose = {
                searchOpen = false
                viewModel.searchQuery.value = ""
            },
        )
        return
    }
    // Benflix: "Couldn't find it" cards stay for good, even after Seerr has dropped the request
    val extras =
        if (list == null || arrivedOnly == true) {
            emptyList()
        } else {
            val shown = all.orEmpty().mapNotNull { it.cantFindKey() }.toSet()
            cantFind.values.filter { entry ->
                entry.key !in shown && (!mineOnly || (myId != null && myId in entry.askedBy))
            }
        }

    fun RequestEntry.isNotFound() = !available && cantFindKey()?.let { it in cantFind } == true
    val boardEntries =
        if (manage) {
            list.orEmpty().filter {
                (who == null || it.who == who) && (kind == null || it.manageKind() == kind)
            }
        } else {
            emptyList()
        }
    val boardLines =
        if (manage) {
            otherLines.filter {
                (who == null || (it.who ?: "Ben") == who) &&
                    (kind == null || it.manageKind() == kind)
            }
        } else {
            emptyList()
        }
    // Glowfin new look (Ben approved 2026-11-22): on a TV the request you're on fills the right side
    var focusKey by remember { mutableStateOf<String?>(null) }
    val declineReasons by DeclineWhy.reasons.collectAsState()
    val laneRows = if (manage) boardEntries.filter { it.manageLane(it.isNotFound()) == lane } else emptyList()
    val laneLines = if (manage) boardLines.filter { it.manageLane() == lane } else emptyList()
    val shown =
        if (!manage || !isTv) {
            null
        } else {
            val onLine = laneLines.firstOrNull { it.key == focusKey }
            val onRow = laneRows.firstOrNull { "seerr-${it.request.id}" == focusKey } ?: laneRows.firstOrNull()
            val entry = onRow.takeIf { onLine == null }
            if (entry != null) {
                entry.manageShown(
                    lane = lane,
                    cantFind = if (entry.available) null else entry.cantFindKey()?.let { cantFind[it] },
                    hold =
                        entry.request.media
                            ?.tmdbId
                            ?.let { holds["${if (entry.item?.type == SeerrItemType.MOVIE) "movie" else "tv"}:$it"] },
                    declined = entry.request.id?.let { declineReasons[DeclineWhy.key(it)] },
                )
            } else {
                (onLine ?: laneLines.firstOrNull())?.manageShown(lane)
            }
        }
    // Glowfin new look: family members see Your asks as rows of covers (Ben's board stays the list above)
    if (mineOnly && arrivedOnly == null && !showSearch && list != null && (list.isNotEmpty() || otherLines.isNotEmpty())) {
        FamilyAskRows(
            entries = list,
            others = otherLines,
            cantFind = cantFind,
            reasons = declineReasons,
            isTv = isTv,
            onOpen = { viewModel.navigationManager.navigateTo(it.destination) },
        )
        return
    }
    ManageFrame(on = manage && isTv, art = shown?.art, side = { ManageSide(shown) }) { listModifier ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
            modifier = listModifier,
        ) {
            if (showSearch) {
                item {
                    if (isTv) {
                        RequestSearchButton(onClick = { searchOpen = true })
                    } else {
                        RequestsSearchBox(query = query, onQuery = { viewModel.searchQuery.value = it })
                    }
                }
                if (!isTv && query.trim().length >= 2) {
                    searchShelves(
                        results = searchResults,
                        visible = familyVisible,
                        scoreLine = { it.releaseDate?.year?.toString() },
                        onOpen = { viewModel.navigationManager.navigateTo(it.destination) },
                        onMenu = { menuItem = it },
                        onFocusItem = {},
                        isTv = isTv,
                    )
                    return@LazyColumn
                }
            }
            if (manage && list != null) {
                item(key = "manage-tiles") {
                    val counts =
                        ManageLane.entries.associateWith { l ->
                            boardEntries.count { it.manageLane(it.isNotFound()) == l } + boardLines.count { it.manageLane() == l } +
                                if (l == ManageLane.STUCK && who == null && kind == null) extras.size else 0
                        }
                    ManageTiles(
                        counts = counts,
                        lane = lane,
                        onLane = {
                            lane = it
                            picking = false
                            ticks = emptySet()
                        },
                    )
                }
                item(key = "manage-filters") {
                    val open = setOf(ManageLane.NEEDS, ManageLane.WAY, ManageLane.STUCK)
                    val names = (list.map { it.who } + otherLines.map { it.who ?: "Ben" }).distinct().sorted()
                    val people =
                        names.map { n ->
                            n to (
                                list.count { it.who == n && it.manageLane(it.isNotFound()) in open } +
                                    otherLines.count { (it.who ?: "Ben") == n && it.manageLane() in open }
                            )
                        }
                    val kindsHere =
                        MANAGE_KINDS.filter { k ->
                            list.any { it.manageKind() == k } || otherLines.any { it.manageKind() == k }
                        }
                    ManageFilters(
                        people = people,
                        who = who,
                        onWho = { who = it },
                        kinds = kindsHere,
                        kind = kind,
                        onKind = { kind = it },
                        onAutoYes = { autoYes = true },
                    )
                }
            }
            when {
                list == null && requestsFailed -> {
                    item {
                        LoadFailed(
                            text = "Couldn't load the requests. Check the connection and try again.",
                            onRetry = { viewModel.loadRequests(mineOnly) },
                        )
                    }
                }

                list == null -> {
                    item { LoadingPage(Modifier.height(240.dp), focusEnabled = false) }
                }

                manage -> {
                    val rows = laneRows
                    val lines = laneLines
                    val needsEntries = rows.filter { it.status == RequestStatus.PENDING }
                    if (lane == ManageLane.NEEDS && rows.size + lines.size > 1) {
                        item(key = "manage-bulk") {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            ) {
                                if (picking) {
                                    val n = ticks.size
                                    val picked = { needsEntries.filter { "seerr-${it.request.id}" in ticks } }
                                    val pickedLines = { lines.filter { it.key in ticks } }
                                    ManageButton(
                                        label = if (n > 0) "Yes to $n" else "Yes",
                                        enabled = n > 0,
                                        onClick = {
                                            pickedLines().forEach { it.onYes?.invoke() }
                                            viewModel.answerMany(picked(), true)
                                            ticks = emptySet()
                                            picking = false
                                        },
                                    )
                                    ManageButton(
                                        label = if (n > 0) "No to $n" else "No",
                                        enabled = n > 0,
                                        onClick = {
                                            pickedLines().forEach { it.onNo?.invoke() }
                                            viewModel.answerMany(picked(), false)
                                            ticks = emptySet()
                                            picking = false
                                        },
                                    )
                                    ManageButton(
                                        label = "Done picking",
                                        onClick = {
                                            ticks = emptySet()
                                            picking = false
                                        },
                                    )
                                } else {
                                    ManageButton(
                                        label = "Yes to all ${rows.size + lines.size}",
                                        onClick = {
                                            lines.forEach { it.onYes?.invoke() }
                                            viewModel.answerMany(needsEntries, true)
                                        },
                                    )
                                    ManageButton(label = "Pick several", onClick = { picking = true })
                                }
                            }
                        }
                    }
                    // "Couldn't find it" cards whose request Seerr has dropped stay here for good
                    val oldCards = if (lane == ManageLane.STUCK && who == null && kind == null) extras else emptyList()
                    if (rows.isEmpty() && lines.isEmpty() && oldCards.isEmpty()) {
                        item(key = "manage-empty") {
                            Text(
                                text = if (who != null || kind != null) "Nothing here with these filters." else lane.empty,
                                fontSize = BenflixType.body,
                                color = com.github.damontecres.wholphin.ui.theme.Glowfin.Dim,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                            )
                        }
                    }
                    items(rows, key = { "manage-${it.request.id ?: it.hashCode()}" }) { entry ->
                        val tickKey = "seerr-${entry.request.id}"
                        val notFound = if (entry.available) null else entry.cantFindKey()?.let { cantFind[it] }
                        val approved = !entry.available && entry.status == RequestStatus.APPROVED
                        val tmdb = entry.request.media?.tmdbId
                        val holdKind = if (entry.item?.type == SeerrItemType.MOVIE) "movie" else "tv"
                        val requestId = entry.request.id
                        val changedMind: (() -> Unit)? =
                            if (entry.status == RequestStatus.DECLINED && requestId != null) {
                                ({ manageVm.undo(requestId) { viewModel.loadRequests(mineOnly) } })
                            } else {
                                null
                            }
                        if (lane == ManageLane.STUCK && approved && tmdb != null) {
                            LaunchedEffect(tmdb) { manageVm.loadHold(holdKind, tmdb) }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier =
                                Modifier
                                    .padding(horizontal = 16.dp)
                                    .onFocusChanged { if (it.hasFocus) focusKey = tickKey },
                        ) {
                            if (picking && entry.status == RequestStatus.PENDING) {
                                ManageTick(on = tickKey in ticks, onClick = {
                                    ticks =
                                        if (tickKey in
                                            ticks
                                        ) {
                                            ticks - tickKey
                                        } else {
                                            ticks + tickKey
                                        }
                                })
                            }
                            Box(Modifier.weight(1f)) {
                                RequestRow(
                                    entry = entry,
                                    lookYourself = false,
                                    showWho = true,
                                    canAnswer = entry.status == RequestStatus.PENDING && !entry.available,
                                    onOpen = { entry.item?.let { viewModel.navigationManager.navigateTo(it.destination) } },
                                    onAnswer = { viewModel.answer(entry, it) },
                                    cantFind = notFound,
                                    isAdminList = true,
                                    onCantFind =
                                        if (lane == ManageLane.STUCK && approved &&
                                            notFound == null
                                        ) {
                                            ({ cantFindFor = entry })
                                        } else {
                                            null
                                        },
                                    onUndoCantFind =
                                        if (!entry.available && notFound != null) {
                                            (
                                                {
                                                    viewModel.undoCantFind(
                                                        entry,
                                                    )
                                                }
                                            )
                                        } else {
                                            null
                                        },
                                    holdLine =
                                        if (lane == ManageLane.STUCK &&
                                            tmdb != null
                                        ) {
                                            holds["$holdKind:$tmdb"]?.takeIf { it.isNotBlank() }
                                        } else {
                                            null
                                        },
                                    onLookAgain =
                                        if (lane == ManageLane.STUCK && approved &&
                                            tmdb != null
                                        ) {
                                            ({ manageVm.again(holdKind, tmdb) })
                                        } else {
                                            null
                                        },
                                    onStop = if (approved) ({ stopFor = entry }) else null,
                                    onChangedMind = changedMind,
                                    slim = isTv,
                                )
                            }
                        }
                    }
                    items(oldCards, key = { "manage-cant-find-${it.key}" }) { entry ->
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            CantFindOnlyRow(
                                entry = entry,
                                onOpen = { viewModel.navigationManager.navigateTo(entry.discoverItem().destination) },
                                onTryAgain = { viewModel.undoCantFind(entry.key) },
                            )
                        }
                    }
                    items(lines, key = { "manage-other-${it.key}" }) { line ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier =
                                Modifier
                                    .padding(horizontal = 16.dp)
                                    .onFocusChanged { if (it.hasFocus) focusKey = line.key },
                        ) {
                            if (picking && line.onYes != null) {
                                ManageTick(on = line.key in ticks, onClick = {
                                    ticks =
                                        if (line.key in ticks) ticks - line.key else ticks + line.key
                                })
                            }
                            Box(Modifier.weight(1f)) { AskLineRow(line) }
                        }
                    }
                }

                list.isEmpty() && extras.isEmpty() && otherLines.isEmpty() -> {
                    item {
                        EmptyPage(
                            icon = BenflixIcon.REQUESTS,
                            title = if (arrivedOnly == true) "Nothing has arrived yet" else "No requests yet",
                            line =
                                when (arrivedOnly) {
                                    true -> "Things you asked for show up here as soon as they are ready to watch."
                                    false -> "You haven't asked for anything yet. Search above for a movie or show and ask for it."
                                    null -> if (mineOnly) NOTHING_ASKED_YET else "When someone asks for something, it shows up here."
                                },
                        )
                    }
                }

                else -> {
                    if (!mineOnly && waiting > 1) {
                        item {
                            Button(
                                onClick = { confirmAll = true },
                                modifier = Modifier.padding(horizontal = 16.dp),
                                scale = ButtonDefaults.scale(focusedScale = 1f),
                            ) { Text("Yes to all $waiting") }
                        }
                    }

                    fun RequestEntry.notFound() = !available && cantFindKey()?.let { it in cantFind } == true

                    // Benflix (Ben, 2026-11-08): two weeks of searching with nothing found, so Ben starts looking himself.
                    // Only on his list, and not ones he already stopped by hand.
                    fun RequestEntry.lookYourself() = !mineOnly && viewModel.isAdmin && needsLookingFor(cantFindKey()?.let { cantFind[it] })
                    val stillAsking = { it: RequestEntry -> !it.notFound() }
                    // Redesign 2: grouped by the five status pills (Ready first). Ben's "Waiting for you" stays on top.
                    val waitingTitle = "Waiting for you"

                    fun RequestEntry.groupTitle(): String =
                        when {
                            !mineOnly && !available && status == RequestStatus.PENDING && stillAsking(this) -> waitingTitle
                            lookYourself() -> AskState.LOOKING.group
                            else -> askPill(if (available) null else cantFindKey()?.let { cantFind[it] }).state.group
                        }
                    val byGroup = list.groupBy { it.groupTitle() }
                    val othersByGroup = otherLines.groupBy { if (it.onYes != null) waitingTitle else it.pill.state.group }
                    val order = listOf(waitingTitle) + AskState.entries.map { it.group }
                    val groups =
                        order
                            .map { it to byGroup[it].orEmpty() }
                            .filter {
                                it.second.isNotEmpty() || othersByGroup[it.first].orEmpty().isNotEmpty() ||
                                    (it.first == AskState.CANT.group && extras.isNotEmpty())
                            }
                    groups.forEach { (title, entries) ->
                        val extraCount = if (title == AskState.CANT.group) extras.size else 0
                        val others = othersByGroup[title].orEmpty()
                        item(key = "group-$title") {
                            Box(Modifier.padding(horizontal = 16.dp)) { RequestGroupTitle(title, entries.size + extraCount + others.size) }
                        }
                        items(others, key = { "other-$title-${it.key}" }) { line ->
                            Box(Modifier.padding(horizontal = 16.dp)) { AskLineRow(line) }
                        }
                        items(entries, key = { it.request.id ?: it.hashCode() }) { entry ->
                            Box(Modifier.padding(horizontal = 16.dp)) {
                                val notFound = if (entry.available) null else entry.cantFindKey()?.let { cantFind[it] }
                                // Benflix: approved but not downloaded, so Ben can mark it "Couldn't find it" (or try again)
                                val isBen = !mineOnly && viewModel.isAdmin && !entry.available
                                val canMark = isBen && entry.status == RequestStatus.APPROVED
                                RequestRow(
                                    entry = entry,
                                    lookYourself = entry.lookYourself(),
                                    showWho = !mineOnly,
                                    canAnswer = !mineOnly && entry.status == RequestStatus.PENDING && !entry.available,
                                    onOpen = { entry.item?.let { viewModel.navigationManager.navigateTo(it.destination) } },
                                    onAnswer = { viewModel.answer(entry, it) },
                                    cantFind = notFound,
                                    isAdminList = !mineOnly,
                                    onCantFind = if (canMark && notFound == null) ({ cantFindFor = entry }) else null,
                                    onUndoCantFind = if (isBen && notFound != null) ({ viewModel.undoCantFind(entry) }) else null,
                                )
                            }
                        }
                        if (extraCount > 0) {
                            items(extras, key = { "cant-find-${it.key}" }) { entry ->
                                Box(Modifier.padding(horizontal = 16.dp)) {
                                    CantFindOnlyRow(
                                        entry = entry,
                                        onOpen = { viewModel.navigationManager.navigateTo(entry.discoverItem().destination) },
                                        onTryAgain = if (viewModel.isAdmin && !mineOnly) ({ viewModel.undoCantFind(entry.key) }) else null,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestRow(
    entry: RequestEntry,
    showWho: Boolean,
    lookYourself: Boolean = false,
    canAnswer: Boolean,
    onOpen: () -> Unit,
    onAnswer: (Boolean) -> Unit,
    cantFind: CantFindEntry? = null,
    isAdminList: Boolean = false,
    onCantFind: (() -> Unit)? = null,
    onUndoCantFind: (() -> Unit)? = null,
    // Manage (Ben 2026-11-21): why a stuck one is stuck, and Look again / Stop / Changed my mind
    holdLine: String? = null,
    onLookAgain: (() -> Unit)? = null,
    onStop: (() -> Unit)? = null,
    onChangedMind: (() -> Unit)? = null,
    // Glowfin new look: a slim see-through row on the TV Manage board; the details sit on the right side instead
    slim: Boolean = false,
) {
    // Benflix: on phones the buttons go on their own line under the text, so the title and steps get the full width
    val isTv = rememberIsTv()
    val declineReasons by DeclineWhy.reasons.collectAsState()
    val buttons: @Composable () -> Unit = {
        if (canAnswer) {
            Button(
                onClick = { onAnswer(true) },
                scale = ButtonDefaults.scale(focusedScale = 1f),
            ) { Text("Yes", maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }
            Button(
                onClick = { onAnswer(false) },
                scale = ButtonDefaults.scale(focusedScale = 1f),
            ) { Text("No", maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }
        } else if (entry.item != null) {
            Button(onClick = onOpen, scale = ButtonDefaults.scale(focusedScale = 1f)) {
                Text(if (entry.available) "Watch" else "Details", maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
        }
        onCantFind?.let {
            Button(onClick = it, scale = ButtonDefaults.scale(focusedScale = 1f)) {
                Text("Can't find", maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
        }
        onUndoCantFind?.let {
            Button(onClick = it, scale = ButtonDefaults.scale(focusedScale = 1f)) {
                Text("Try again", maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
        }
        onLookAgain?.let {
            Button(onClick = it, scale = ButtonDefaults.scale(focusedScale = 1f)) {
                Text("Look again", maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
        }
        onStop?.let {
            Button(onClick = it, scale = ButtonDefaults.scale(focusedScale = 1f)) {
                Text("Stop", maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
        }
        onChangedMind?.let {
            Button(onClick = it, scale = ButtonDefaults.scale(focusedScale = 1f)) {
                Text("Changed my mind", maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
        }
    }
    val daysWaiting =
        remember(entry.request.updatedAt, entry.request.createdAt) {
            BenflixCantFind.daysSince(entry.request.updatedAt ?: entry.request.createdAt)
        }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(if (slim) 18.dp else 10.dp))
                .background(if (slim) SLIM_ROW else com.github.damontecres.wholphin.ui.theme.Glowfin.Deep)
                .padding(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val adultFlags by AdultContentFlags.flagged.collectAsState()
            Box(
                modifier =
                    Modifier
                        .size(width = if (slim) 44.dp else 54.dp, height = if (slim) 64.dp else 80.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(com.github.damontecres.wholphin.ui.theme.Glowfin.Raise),
            ) {
                AsyncImage(
                    model = entry.item?.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Benflix: red "ADULT" strip for porn, hentai, softcore or erotica
                if (entry.item?.let { it.requestKey() in adultFlags } == true) {
                    AdultContentBanner(short = true, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = entry.item?.title ?: "Unknown title",
                    fontSize = BenflixType.lead,
                    fontWeight = FontWeight.Bold,
                    color = com.github.damontecres.wholphin.ui.theme.Glowfin.Text,
                    maxLines = if (isTv) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val whenAsked = remember(entry.request.createdAt) { ago(entry.request.createdAt) }
                if (canAnswer && showWho) {
                    // Glowfin R1 Waiting for you: who asked, with their sea friend
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WhoAskedFace(entry.who, 26.dp)
                        Text(
                            text = listOfNotNull("${entry.who} asked", whenAsked).joinToString(" · "),
                            fontSize = BenflixType.small,
                            fontWeight = FontWeight.SemiBold,
                            color = com.github.damontecres.wholphin.ui.theme.Glowfin.Aqua,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Text(
                        text = listOfNotNull(if (showWho) "Asked by ${entry.who}" else "You asked", whenAsked).joinToString(" · "),
                        fontSize = BenflixType.small,
                        color = com.github.damontecres.wholphin.ui.theme.Glowfin.Dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!slim) StatusTag(entry, cantFind = cantFind, daysWaiting = daysWaiting, lookYourself = lookYourself)
                if (!slim && entry.status == RequestStatus.DECLINED) {
                    val why = entry.request.id?.let { declineReasons[DeclineWhy.key(it)] }
                    if (why != null) {
                        Text(
                            text = "Why: $why",
                            fontSize = BenflixType.small,
                            color = com.github.damontecres.wholphin.ui.theme.Glowfin.Dim,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!slim && holdLine != null) {
                    Text(
                        text = holdLine,
                        fontSize = BenflixType.small,
                        color = com.github.damontecres.wholphin.ui.theme.Glowfin.Dim,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!slim && cantFind != null) CantFindNoteLine(cantFind)
                if (!slim && cantFind == null) {
                    RequestSteps(
                        approved = entry.status == RequestStatus.APPROVED || entry.status == RequestStatus.COMPLETED,
                        downloading = entry.downloading,
                        percent = entry.downloadPercent,
                        ready = entry.available,
                        declined = entry.status == RequestStatus.DECLINED || entry.status == RequestStatus.FAILURE,
                    )
                }
            }
            if (isTv) buttons()
        }
        if (!isTv) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth(),
            ) { buttons() }
        }
    }
}

/** Redesign 2: the one status pill (Asked, Getting it, Still looking, Couldn't find it, Ready to watch) */
@Composable
private fun StatusTag(
    entry: RequestEntry,
    cantFind: CantFindEntry? = null,
    daysWaiting: Long? = null,
    lookYourself: Boolean = false,
) {
    val pill = entry.askPill(cantFind, daysWaiting)
    AskPillChip(if (lookYourself) pill.copy(state = AskState.LOOKING, words = pill.words + " · look for it yourself") else pill)
}

/**
 * Benflix: approved, nothing here or downloading, and searched [RequestsWaiting.LOOK_YOURSELF_DAYS] days or more.
 * Not ones Ben stopped by hand ([cantFind] marked by him, which takes them out of Radarr / Sonarr).
 */
fun RequestEntry.needsLookingFor(cantFind: CantFindEntry?): Boolean =
    status == RequestStatus.APPROVED &&
        !available &&
        !downloading &&
        (cantFind == null || cantFind.auto) &&
        (clock?.daysLooking ?: 0) >= RequestsWaiting.LOOK_YOURSELF_DAYS

/** Benflix: the "Couldn't find it" key for this request ("MOVIE-123" / "TV-456") */
fun RequestEntry.cantFindKey(): String? {
    val tmdbId = request.media?.tmdbId ?: return null
    return cantFindKey(SeerrItemType.fromString(request.type) == SeerrItemType.MOVIE, tmdbId)
}

/** "3 days ago" from Seerr's date text */
internal fun ago(createdAt: String?): String? =
    try {
        createdAt?.let {
            val then = OffsetDateTime.parse(it)
            val days = ChronoUnit.DAYS.between(then, OffsetDateTime.now())
            when {
                days < 1 -> "today"
                days == 1L -> "yesterday"
                days < 30 -> "$days days ago"
                else -> "${days / 30} months ago"
            }
        }
    } catch (ex: Exception) {
        null
    }

/** Asks for the person's Benflix password (hidden while typing) */
@Composable
private fun PasswordDialog(
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    BasicDialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .widthIn(max = 420.dp)
                    .padding(24.dp),
        ) {
            Text("Your Glowfin password", fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold, color = Color.White)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (password.isNotEmpty()) onConnect(password) }),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { if (password.isNotEmpty()) onConnect(password) },
                    scale = ButtonDefaults.scale(focusedScale = 1f),
                ) { Text("Connect") }
                Button(onClick = onDismiss, scale = ButtonDefaults.scale(focusedScale = 1f)) { Text("Cancel") }
            }
        }
    }
}

private const val NOTHING_ASKED_YET =
    "You haven't asked for anything yet. Find something on the Movies, Shows or Anime tab and ask for it."

/**
 * Benflix (2026-11-16): [here] from the Jellyfin check: true = all on Benflix, false = a show missing aired
 * episodes. Shown as the card's corner badge ("ON BENFLIX" / "MISSING EPISODES"); a request already made keeps
 * its "REQUESTED" badge.
 */
fun markOnBenflix(
    item: DiscoverItem,
    here: Boolean?,
): DiscoverItem =
    when {
        here == null -> {
            item
        }

        item.availability == SeerrAvailability.PENDING || item.availability == SeerrAvailability.PROCESSING -> {
            item
        }

        here -> {
            if (item.availability == SeerrAvailability.AVAILABLE) item else item.copy(availability = SeerrAvailability.AVAILABLE)
        }

        else -> {
            if (item.availability ==
                SeerrAvailability.PARTIALLY_AVAILABLE
            ) {
                item
            } else {
                item.copy(availability = SeerrAvailability.PARTIALLY_AVAILABLE)
            }
        }
    }

/** Glowfin new look: the slim see-through rows on the TV Manage board */
private val SLIM_ROW = Color(0x6B0B0D1A)

/** What the TV Manage board's right side says about a movie or show request */
internal fun RequestEntry.manageShown(
    lane: ManageLane,
    cantFind: CantFindEntry?,
    hold: String?,
    declined: String?,
): ManageShown {
    val asked = listOfNotNull("$who asked", ago(request.createdAt)).joinToString(" ")
    val days = BenflixCantFind.daysSince(request.updatedAt ?: request.createdAt) ?: 0L
    val tag =
        when (lane) {
            ManageLane.NEEDS -> RqTag(RqTagKind.WAITING, asked)
            ManageLane.WAY -> RqTag(RqTagKind.ON_WAY, downloadPercent?.let { "Downloading · $it%" } ?: "On its way")
            ManageLane.STUCK -> RqTag(RqTagKind.PROBLEM, if (cantFind != null) "Couldn't find it" else "Stuck $days days")
            ManageLane.READY -> RqTag(RqTagKind.HERE, "Here now")
            ManageLane.NO -> RqTag(RqTagKind.PROBLEM, "You said no")
        }
    val why =
        when (lane) {
            ManageLane.STUCK -> {
                "Why: " + (
                    cantFind?.note ?: hold?.takeIf { it.isNotBlank() }
                        ?: if (cantFind != null) "Ben looked but couldn't find a copy" else "no copies found yet. Tower keeps looking."
                )
            }

            ManageLane.NO -> {
                declined?.let { "Your reason: $it" }
            }

            else -> {
                null
            }
        }
    val whoLine =
        if (lane == ManageLane.NEEDS) {
            null
        } else {
            asked + if (status == RequestStatus.APPROVED || status == RequestStatus.COMPLETED || available) " · you said yes" else ""
        }
    val facts =
        listOfNotNull(
            manageKind().let {
                if (it == "Movies") {
                    "Movie"
                } else if (it == "Shows") {
                    "Show"
                } else {
                    it
                }
            },
            item?.releaseDate?.year?.toString(),
        ).joinToString(" · ")
    return ManageShown(
        key = "seerr-${request.id}",
        art = item?.backDropUrl ?: item?.posterUrl,
        title = item?.title ?: "Unknown title",
        facts = facts,
        tag = tag,
        story = item?.overview,
        lines = listOfNotNull(why, whoLine),
    )
}

/** The same for a song, album or book ask */
internal fun AskLine.manageShown(lane: ManageLane): ManageShown {
    val kind =
        when {
            lane == ManageLane.NEEDS -> RqTagKind.WAITING
            pill.state == AskState.READY -> RqTagKind.HERE
            pill.state == AskState.GETTING -> RqTagKind.ON_WAY
            pill.state == AskState.CANT -> RqTagKind.PROBLEM
            else -> RqTagKind.WAITING
        }
    return ManageShown(
        key = key,
        art = cover,
        title = title,
        facts = sub,
        tag = RqTag(kind, if (lane == ManageLane.NEEDS) "Waiting for you" else pill.words),
        story = null,
        lines = listOfNotNull(why.takeIf { it.isNotBlank() }?.let { "Why: $it" }),
    )
}
