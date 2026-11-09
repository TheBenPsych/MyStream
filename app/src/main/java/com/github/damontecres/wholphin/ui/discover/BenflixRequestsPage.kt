package com.github.damontecres.wholphin.ui.discover

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
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
import com.github.damontecres.wholphin.services.CantFindEntry
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SearchClock
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.services.requestedArrived
import com.github.damontecres.wholphin.ui.cards.AdultContentBanner
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.TabViewModel
import com.github.damontecres.wholphin.ui.components.TabbedPage
import com.github.damontecres.wholphin.ui.detail.discover.AdultContentFlags
import com.github.damontecres.wholphin.ui.detail.discover.AnimeTagCatalogViewModel
import com.github.damontecres.wholphin.ui.detail.discover.isAdultContent
import com.github.damontecres.wholphin.ui.detail.discover.isGrownUpTag
import com.github.damontecres.wholphin.ui.detail.discover.movieAgeRating
import com.github.damontecres.wholphin.ui.detail.discover.str
import com.github.damontecres.wholphin.ui.detail.discover.tags
import com.github.damontecres.wholphin.ui.detail.discover.tvAgeRating
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.RequestsWaiting
import com.github.damontecres.wholphin.ui.setup.seerr.SeerrDialog
import com.github.damontecres.wholphin.ui.setup.seerr.SeerrDialogMode
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.util.StringStringProvider
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
import kotlinx.serialization.json.jsonObject
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
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
                            }.awaitAll()
                        }.let { all ->
                            // Benflix: Japanese anime belongs on the Anime tab, not mixed into Movies and Shows
                            if (section == FindSection.ANIME) {
                                all
                            } else {
                                all.map { row -> row.copy(items = row.items.filterNot { it.isAnime }) }
                            }
                        }
                    done(rows)
                }.invokeOnCompletion { loadingRows.remove(key) }
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
                        because.await() + listOfNotNull(collection.await())
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
                if (missing.isEmpty()) null else FindRow("Finish the collection", missing)
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
                            showToast(context, "Every season is already asked for or on Benflix")
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
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Asking for seasons failed for %s", item.id)
                    showToast(context, "Couldn't ask for that one. Try from its page.")
                }
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
            val available =
                request.media?.status == SeerrAvailability.AVAILABLE.status ||
                    item?.availability == SeerrAvailability.AVAILABLE ||
                    request.requestedArrived(tvSeasons ?: request.media?.seasons)
            // Benflix: how far the download is, for the progress steps
            var percent: Int? = null
            var downloading = false
            var clock: SearchClock? = null
            if (!available && status == RequestStatus.APPROVED && tmdbId != null) {
                try {
                    val kind = if (SeerrItemType.fromString(request.type) == SeerrItemType.MOVIE) "movie" else "tv"
                    val details =
                        seerrService.api
                            .getRaw("/$kind/$tmdbId")
                            ?.let { Json.parseToJsonElement(it).jsonObject }
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
    if (!active) {
        NotConnected(viewModel, modifier)
        return
    }
    val admin = viewModel.isAdmin
    val tabs =
        remember(admin) {
            if (admin) {
                // Benflix: Music is only for Ben (he picked "only me" for music requests)
                listOf("Movies", "Shows", "Anime", "Music", "Requests")
            } else {
                // Benflix: everyone gets the same browse tabs as Ben (Ben asked), plus their own requests
                listOf("Movies", "Shows", "Anime", "My requests")
            }.map { TabDetails(StringStringProvider(it)) }
        }
    val tabsId = if (admin) "benflix-requests-admin" else "benflix-requests-family"
    val tabViewModel =
        hiltViewModel<TabViewModel, TabViewModel.Factory>(
            key = "$tabsId-${tabs.size}",
            creationCallback = { it.create(tabsId, tabs.size) },
        )
    // Benflix music: "Ask for music" on the Music page opens straight on the Music tab
    LaunchedEffect(admin) {
        if (admin && RequestsJump.music) tabViewModel.updateSelectedTabIndex(3)
        RequestsJump.music = false
    }
    TabbedPage(
        itemId = tabsId,
        tabs = tabs,
        modifier = modifier,
        viewModel = tabViewModel,
    ) { index, _ ->
        // Last tab: everyone's requests with Approve / Decline (admin), or what you asked for and what arrived
        when (index) {
            0 -> FindShelf(FindSection.MOVIES, viewModel)
            1 -> FindShelf(FindSection.SHOWS, viewModel)
            2 -> FindShelf(FindSection.ANIME, viewModel)
            3 -> if (admin) MusicShelf() else RequestList(viewModel, mineOnly = true, arrivedOnly = null)
            else -> RequestList(viewModel, mineOnly = !admin, arrivedOnly = null)
        }
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
                        null -> "Type your Benflix password once on this device and you can ask Ben for anything that's missing."
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

@Composable
private fun FindShelf(
    section: FindSection,
    viewModel: BenflixRequestsViewModel,
) {
    var filters by remember(section) { mutableStateOf(FindFilters()) }
    var hideOwned by rememberSaveable { mutableStateOf(true) }
    var hideAsked by rememberSaveable { mutableStateOf(false) }
    // Benflix: family accounts start with Family-friendly only on (like their search), and can turn it off
    var familyOnly by rememberSaveable { mutableStateOf(!viewModel.isAdmin) }
    var showFilters by remember { mutableStateOf(false) }
    var menuItem by remember { mutableStateOf<DiscoverItem?>(null) }
    val key = viewModel.key(section, filters)
    LaunchedEffect(key) { viewModel.loadSection(section, filters) }
    LaunchedEffect(section) { viewModel.loadSpecial(section) }
    // Coming back from a details page: pick up anything just asked for
    LifecycleResumeEffect(Unit) {
        viewModel.refreshRequested()
        // Benflix redesign: "Waiting for you" and "Your requests" rows sit at the top of each tab
        viewModel.loadRequests(mineOnly = !viewModel.isAdmin)
        onPauseOrDispose { }
    }
    val requestEntries by viewModel.requests.collectAsState()
    val mySeerrId by viewModel.mySeerrId.collectAsState(initial = null)
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
    val visible: (List<DiscoverItem>) -> List<DiscoverItem> = { items ->
        items.filter { item ->
            (!hideOwned || item.availability != SeerrAvailability.AVAILABLE) &&
                (
                    !hideAsked ||
                        (item.availability != SeerrAvailability.PENDING && item.availability != SeerrAvailability.PROCESSING)
                ) &&
                item.requestKey() !in hidden &&
                (!familyOnly || item.type == SeerrItemType.PERSON || ratings[item.requestKey()]?.familyFriendly(item.type) == true)
        }
    }
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
        FiltersDialog(
            section = section,
            current = filters,
            onDismiss = { showFilters = false },
            onApply = {
                filters = it
                showFilters = false
            },
            showLanguage = !viewModel.viaJellyfin,
        )
    }
    val seasonPick by viewModel.seasonPick.collectAsState()
    seasonPick?.let { pick ->
        SeasonPickDialog(
            pick = pick,
            onDismiss = viewModel::closeSeasonPick,
            onAsk = { viewModel.askSeasons(pick.item, it) },
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val previewHeight = (maxHeight * 0.34f).coerceIn(150.dp, 260.dp)
        Column(Modifier.fillMaxSize()) {
            if (isTv) {
                DelayedRequestPreview(
                    focused = focusedItem,
                    fallback = rows?.firstNotNullOfOrNull { visible(it.items).firstOrNull() },
                    scores = scores,
                    height = previewHeight,
                )
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(if (isTv) 24.dp else 16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                // Benflix: no big banner here (Ben asked), the page starts with the search box like the other tabs
                item(key = "search") {
                    if (isTv) {
                        RequestSearchButton(onClick = { searchOpen = true })
                    } else {
                        RequestsSearchBox(query = query, onQuery = { viewModel.searchQuery.value = it })
                    }
                }
                if (searching && !isTv) {
                    searchShelves(
                        results = searchResults,
                        anime = searchAnime,
                        visible = visible,
                        scoreLine = scoreLine,
                        onOpen = openItem,
                        onMenu = { menuItem = it },
                        onFocusItem = onFocusPoster,
                        isTv = isTv,
                    )
                    return@LazyColumn
                }
                // Movies tab shows movie requests, Shows and Anime show show requests (anime movies sit with Movies)
                val tabType = if (section == FindSection.MOVIES) SeerrItemType.MOVIE else SeerrItemType.TV
                val tabRequests = requestEntries.orEmpty().filter { it.item?.type == tabType }
                if (viewModel.isAdmin) {
                    val waitingForYou = tabRequests.filter { it.status == RequestStatus.PENDING && !it.available }
                    item(key = "rows-waiting") {
                        RequestCardRow(
                            title = "Waiting for you",
                            entries = waitingForYou,
                            waiting = true,
                            onOpen = { e -> e.item?.let { openItem(it) } },
                            onAnswer = viewModel::answer,
                        )
                    }
                }
                val mine =
                    tabRequests.filter { e ->
                        (!viewModel.isAdmin || (mySeerrId != null && e.request.requestedBy?.id == mySeerrId)) &&
                            !(e.available && BenflixCantFind.daysSince(e.request.updatedAt ?: e.request.createdAt)?.let { it > 7 } == true)
                    }
                item(key = "rows-mine") {
                    RequestCardRow(
                        title = "Your requests",
                        entries = mine,
                        waiting = false,
                        onOpen = { e -> e.item?.let { openItem(it) } },
                        onAnswer = viewModel::answer,
                    )
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        item { RequestsChip("Filters ☰", filters.narrowed, { showFilters = true }) }
                        item { RequestsChip("Hide what's on Benflix", hideOwned, { hideOwned = !hideOwned }) }
                        item { RequestsChip("Hide what's asked for", hideAsked, { hideAsked = !hideAsked }) }
                        item { RequestsChip("Family-friendly only", familyOnly, { familyOnly = !familyOnly }) }
                    }
                }
                if (section == FindSection.ANIME) {
                    // Benflix: anime genres and tags from AniList (Isekai, Harem...). Each opens the deep search, which
                    // lists every anime that has it, page after page; "Deep search" starts on the full picker.
                    item(key = "anime-deep") {
                        val tagCatalog: AnimeTagCatalogViewModel = hiltViewModel()
                        val catalog by tagCatalog.catalog.collectAsState()
                        // Each chip keeps its everyday name ("Harem") but opens AniList's real tag ("Female Harem");
                        // ones AniList doesn't have are hidden
                        val picks =
                            remember(catalog) {
                                ANIME_QUICK_PICKS.mapNotNull { label -> AniListService.resolveName(label, catalog)?.let { label to it } }
                            }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            item {
                                RequestsChip("Deep search: every genre and tag", true, {
                                    viewModel.navigationManager.navigateTo(Destination.AnimeGenres(emptyList()))
                                })
                            }
                            items(picks, key = { "aq-${it.first}" }) { (label, name) ->
                                RequestsChip(label, false, {
                                    viewModel.navigationManager.navigateTo(Destination.AnimeGenres(listOf(name)))
                                })
                            }
                        }
                    }
                }
                if (section != FindSection.ANIME) {
                    item {
                        val genres =
                            remember(section) {
                                FindGenres.filter { if (section == FindSection.MOVIES) it.movieId != null else it.tvId != null }
                            }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            item { RequestsChip("All", filters.genre == null, { filters = filters.copy(genre = null) }) }
                            items(genres, key = { it.label }) { g ->
                                RequestsChip(g.label, filters.genre == g, {
                                    filters =
                                        filters.copy(genre = if (filters.genre == g) null else g)
                                })
                            }
                        }
                    }
                }
                if (filters.narrowed) {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                        ) {
                            filters.decade?.let { d ->
                                item { ActiveFilterChip(d.label) { filters = filters.copy(decade = null) } }
                            }
                            filters.minScore?.let { m ->
                                item { ActiveFilterChip("Score $m+") { filters = filters.copy(minScore = null) } }
                            }
                            filters.sort?.let { o ->
                                item { ActiveFilterChip(o.label) { filters = filters.copy(sort = null) } }
                            }
                            filters.language?.let { l ->
                                item { ActiveFilterChip(l.label) { filters = filters.copy(language = null) } }
                            }
                        }
                    }
                }
                if (rows == null) {
                    item { LoadingPage(Modifier.height(240.dp), focusEnabled = false) }
                } else {
                    if (key in failedRows) {
                        item(key = "load-failed") {
                            LoadFailed(
                                text = "Some of these didn't load.",
                                onRetry = { viewModel.loadSection(section, filters) },
                            )
                        }
                    }
                    items(rows, key = { "$key-${it.title}" }) { row ->
                        val comingSoon = row.title.startsWith("Coming soon")
                        val shown =
                            visible(row.items).let { list ->
                                if (comingSoon) {
                                    list
                                        .filter { it.releaseDate == null || !it.releaseDate.isBefore(LocalDate.now()) }
                                        .sortedBy { it.releaseDate ?: LocalDate.MAX }
                                } else {
                                    list
                                }
                            }
                        if (shown.isNotEmpty()) {
                            RequestDoorRow(
                                title = row.title,
                                items = shown,
                                isTv = isTv,
                                onClick = openItem,
                                onLongClick = { menuItem = it },
                                onFocus = onFocusPoster,
                                cornerNoteFor = { if (comingSoon) comingSoonNote(it) else null },
                                subtitleFor = scoreLine,
                            )
                        }
                    }
                    if (rows.all { visible(it.items).isEmpty() } && key !in failedRows) {
                        item {
                            Text(
                                text = "Nothing matches. Try fewer filters.",
                                fontSize = BenflixType.lead,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Benflix: quick picks on the Requests Anime tab; each opens the anime deep search with it picked */
private val ANIME_QUICK_PICKS =
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
private fun LoadFailed(
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
        Button(onClick = onRetry, scale = ButtonDefaults.scale(focusedScale = 1f)) { Text("Try again", maxLines = 1) }
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
    var confirmAll by remember { mutableStateOf(false) }
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
        SeasonPickDialog(
            pick = pick,
            onDismiss = viewModel::closeSeasonPick,
            onAsk = { viewModel.askSeasons(pick.item, it) },
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
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
        modifier = Modifier.fillMaxSize(),
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

            list.isEmpty() && extras.isEmpty() -> {
                item {
                    Text(
                        text =
                            when (arrivedOnly) {
                                true -> "Nothing you asked for has arrived yet."
                                false -> "You haven't asked for anything yet. Search above for a movie or show and ask for it."
                                null -> if (mineOnly) NOTHING_ASKED_YET else "No requests yet."
                            },
                        fontSize = BenflixType.lead,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp),
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
                        ) { Text("Approve all $waiting") }
                    }
                }

                fun RequestEntry.notFound() = !available && cantFindKey()?.let { it in cantFind } == true

                // Benflix (Ben, 2026-11-08): two weeks of searching with nothing found, so Ben starts looking himself.
                // Only on his list, and not ones he already stopped by hand.
                fun RequestEntry.lookYourself() = !mineOnly && viewModel.isAdmin && needsLookingFor(cantFindKey()?.let { cantFind[it] })
                val stillAsking = { it: RequestEntry -> !it.notFound() }
                val groups =
                    listOf(
                        (if (mineOnly) "Waiting for Ben" else "Waiting for you") to
                            list.filter { !it.available && it.status == RequestStatus.PENDING && stillAsking(it) },
                        LOOK_YOURSELF_GROUP to list.filter { it.lookYourself() },
                        "On the way" to
                            list.filter {
                                !it.available && it.status != RequestStatus.PENDING &&
                                    it.status != RequestStatus.DECLINED && it.status != RequestStatus.FAILURE &&
                                    !it.notFound() && !it.lookYourself()
                            },
                        "Couldn't find" to list.filter { it.notFound() && !it.lookYourself() },
                        "Ready to watch" to list.filter { it.available },
                        "Declined" to
                            list.filter {
                                !it.available && (it.status == RequestStatus.DECLINED || it.status == RequestStatus.FAILURE) &&
                                    stillAsking(it)
                            },
                    ).filter { it.second.isNotEmpty() || (it.first == "Couldn't find" && extras.isNotEmpty()) }
                groups.forEach { (title, entries) ->
                    val extraCount = if (title == "Couldn't find") extras.size else 0
                    item(key = "group-$title") {
                        Box(Modifier.padding(horizontal = 16.dp)) { RequestGroupTitle(title, entries.size + extraCount) }
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
) {
    // Benflix: on phones the buttons go on their own line under the text, so the title and steps get the full width
    val isTv = rememberIsTv()
    val buttons: @Composable () -> Unit = {
        if (canAnswer) {
            Button(
                onClick = { onAnswer(true) },
                scale = ButtonDefaults.scale(focusedScale = 1f),
            ) { Text("Approve", maxLines = 1, softWrap = false) }
            Button(
                onClick = { onAnswer(false) },
                scale = ButtonDefaults.scale(focusedScale = 1f),
            ) { Text("Decline", maxLines = 1, softWrap = false) }
        } else if (entry.item != null) {
            Button(onClick = onOpen, scale = ButtonDefaults.scale(focusedScale = 1f)) {
                Text(if (entry.available) "Watch" else "Details", maxLines = 1, softWrap = false)
            }
        }
        onCantFind?.let {
            Button(onClick = it, scale = ButtonDefaults.scale(focusedScale = 1f)) { Text("Can't find", maxLines = 1, softWrap = false) }
        }
        onUndoCantFind?.let {
            Button(onClick = it, scale = ButtonDefaults.scale(focusedScale = 1f)) { Text("Try again", maxLines = 1, softWrap = false) }
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
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF18181C))
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
                        .size(width = 54.dp, height = 80.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF24242A)),
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
                    color = Color.White,
                    maxLines = if (isTv) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val whenAsked = remember(entry.request.createdAt) { ago(entry.request.createdAt) }
                Text(
                    text = listOfNotNull(if (showWho) "Asked by ${entry.who}" else "You asked", whenAsked).joinToString(" · "),
                    fontSize = BenflixType.small,
                    color = Color(0xFFA1A1AA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusTag(entry, cantFind = cantFind, daysWaiting = daysWaiting, isAdminList = isAdminList, lookYourself = lookYourself)
                if (cantFind != null) CantFindNoteLine(cantFind)
                if (cantFind == null) {
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

@Composable
private fun StatusTag(
    entry: RequestEntry,
    cantFind: CantFindEntry? = null,
    daysWaiting: Long? = null,
    isAdminList: Boolean = false,
    lookYourself: Boolean = false,
) {
    // Benflix: approved and nothing downloading yet. After a week it turns into "Couldn't find it" by itself,
    // but the week only starts once it's out plus a few weeks (Ben's rule)
    val clock = entry.clock
    val waiting = entry.status == RequestStatus.APPROVED && !entry.downloading
    val looking = if (clock != null) clock.daysLooking else daysWaiting
    val stillLooking = waiting && looking != null
    val (text, color) =
        when {
            entry.available -> {
                "On Benflix" to Color(0xFF3DD68C)
            }

            lookYourself -> {
                "Look for it yourself · day ${(looking ?: 0) + 1}" to BenflixColors.Movies
            }

            cantFind != null -> {
                "Couldn't find it" to CANT_FIND_COLOR
            }

            entry.status == RequestStatus.PENDING -> {
                "Waiting for Ben" to BenflixColors.Check
            }

            entry.status == RequestStatus.DECLINED -> {
                "Declined" to BenflixColors.Problem
            }

            entry.status == RequestStatus.FAILURE -> {
                "Something went wrong" to BenflixColors.Problem
            }

            waiting && clock?.notOut == true -> {
                val out = clock?.outOn?.let { BenflixCantFind.niceDate(it.toString()) }
                (if (out != null) "Not out yet · out $out" else "Not out yet") to Color(0xFF7AA7FF)
            }

            waiting && clock?.justOut == true -> {
                "Just came out · searching" to Color(0xFF7AA7FF)
            }

            stillLooking -> {
                val days = looking ?: 0
                (if (days < 1) "Still looking · started today" else "Still looking · day ${days + 1}") to Color(0xFF7AA7FF)
            }

            else -> {
                "On the way" to Color(0xFF7AA7FF)
            }
        }
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = .18f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text = text, fontSize = BenflixType.small, fontWeight = FontWeight.Bold, color = color, maxLines = 1, softWrap = false)
    }
}

/** Benflix: the group on Ben's Requests list for ones searched two weeks with nothing found */
private const val LOOK_YOURSELF_GROUP = "Look for these yourself"

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
private fun ago(createdAt: String?): String? =
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
            Text("Your Benflix password", fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold, color = Color.White)
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
