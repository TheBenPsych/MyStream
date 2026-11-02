package com.github.damontecres.wholphin.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.api.seerr.RequestApi
import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.TabbedPage
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.preferences.PreferenceScreenOption
import com.github.damontecres.wholphin.ui.util.StringStringProvider
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
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
)

@HiltViewModel
class BenflixRequestsViewModel
    @Inject
    constructor(
        private val seerrService: SeerrService,
        private val seerrServerRepository: SeerrServerRepository,
        val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        val active = seerrService.active

        val isAdmin: Boolean get() = serverRepository.currentUserDto?.policy?.isAdministrator == true

        private val _rows = MutableStateFlow<Map<String, List<FindRow>>>(emptyMap())
        val rows: StateFlow<Map<String, List<FindRow>>> = _rows

        private val _requests = MutableStateFlow<List<RequestEntry>?>(null)
        val requests: StateFlow<List<RequestEntry>?> = _requests

        fun key(
            section: FindSection,
            genre: FindGenre?,
        ) = "${section.name}-${genre?.label}"

        /** Loads the shelves for one section and genre (kept, so switching back is instant) */
        fun loadSection(
            section: FindSection,
            genre: FindGenre?,
        ) {
            val key = key(section, genre)
            if (_rows.value.containsKey(key)) return
            viewModelScope.launch(WholphinDispatchers.IO) {
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
                            sortBy = sortBy,
                            primaryReleaseDateGte = from,
                            voteCountGte = minVotes,
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
                            sortBy = sortBy,
                            firstAirDateGte = from,
                            voteCountGte = minVotes,
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
                    }
                _rows.update { it + (key to rows) }
            }
        }

        /** Everyone's requests (admin) or just your own */
        fun loadRequests(mineOnly: Boolean) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    val myId = if (mineOnly) seerrServerRepository.currentUserId.first() else null
                    val results =
                        seerrService.api.requestApi
                            .requestGet(
                                take = 60,
                                skip = 0,
                                requestedBy = myId,
                            ).results
                            .orEmpty()
                            .filter { !mineOnly || it.requestedBy?.id == myId }
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
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not load requests")
                    _requests.value = emptyList()
                }
            }
        }

        private suspend fun toEntry(request: MediaRequest): RequestEntry {
            val tmdbId = request.media?.tmdbId
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
                            seerrService.createDiscoverItem(seerrService.api.tvApi.tvTvIdGet(tvId = tmdbId))
                        }
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not look up request %s", request.id)
                    null
                }
            val user = request.requestedBy
            return RequestEntry(
                request = request,
                item = item,
                who = user?.username ?: user?.email ?: "Someone",
                status = RequestStatus.from(request.status),
                available =
                    request.media?.status == SeerrAvailability.AVAILABLE.status ||
                        item?.availability == SeerrAvailability.AVAILABLE,
            )
        }

        /** Admin: say yes or no to a request */
        fun answer(
            entry: RequestEntry,
            approve: Boolean,
        ) {
            val id = entry.request.id ?: return
            viewModelScope.launch(WholphinDispatchers.IO) {
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
                    val newStatus = if (approve) RequestStatus.APPROVED else RequestStatus.DECLINED
                    _requests.update { list ->
                        list?.map { if (it.request.id == id) it.copy(status = newStatus) else it }
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not answer request %s", id)
                }
            }
        }

        companion object {
            /** TMDB's Animation genre and its "anime" keyword, which together mean anime */
            const val ANIMATION_GENRE = "16"
            const val ANIME_KEYWORD = "210024"
        }
    }

/**
 * Benflix Requests tab.
 * Admins: Find new stuff (Movies, Shows, Anime with Trending, Popular, Coming soon and Top rated, plus genre
 * buttons and "Hide what's on Benflix"), and everyone's requests with Approve / Decline.
 * Everyone else: what they asked for, and what has arrived on Benflix.
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
                listOf("Movies", "Shows", "Anime", "Requests")
            } else {
                listOf("My requests", "Now on Benflix")
            }.map { TabDetails(StringStringProvider(it)) }
        }
    TabbedPage(
        itemId = if (admin) "benflix-requests-admin" else "benflix-requests",
        tabs = tabs,
        modifier = modifier,
    ) { index, _ ->
        if (admin) {
            when (index) {
                0 -> FindShelf(FindSection.MOVIES, viewModel)
                1 -> FindShelf(FindSection.SHOWS, viewModel)
                2 -> FindShelf(FindSection.ANIME, viewModel)
                else -> RequestList(viewModel, mineOnly = false, arrivedOnly = null)
            }
        } else {
            RequestList(viewModel, mineOnly = true, arrivedOnly = index == 1)
        }
    }
}

@Composable
private fun NotConnected(
    viewModel: BenflixRequestsViewModel,
    modifier: Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.padding(32.dp),
    ) {
        Text(
            text = "Requests aren't connected on this device yet",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text =
                if (viewModel.isAdmin) {
                    "Connect Seerr in Settings > Advanced and this tab fills up with things to add."
                } else {
                    "Ask Ben to turn on requests for your account, then you can ask for movies and shows here."
                },
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (viewModel.isAdmin) {
            Button(onClick = { viewModel.navigationManager.navigateTo(Destination.Settings(PreferenceScreenOption.ADVANCED)) }) {
                Text("Open Settings")
            }
        }
    }
}

@Composable
private fun FindShelf(
    section: FindSection,
    viewModel: BenflixRequestsViewModel,
) {
    var genre by remember(section) { mutableStateOf<FindGenre?>(null) }
    var hideOwned by rememberSaveable { mutableStateOf(true) }
    val key = viewModel.key(section, genre)
    LaunchedEffect(key) { viewModel.loadSection(section, genre) }
    val allRows by viewModel.rows.collectAsState()
    val rows = allRows[key]
    val genres =
        remember(section) {
            FindGenres.filter { if (section == FindSection.MOVIES) it.movieId != null else it.tvId != null }
        }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 48.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                item {
                    Chip("Hide what's on Benflix", hideOwned) { hideOwned = !hideOwned }
                }
                item {
                    Chip("All", genre == null) { genre = null }
                }
                items(genres, key = { it.label }) { g ->
                    Chip(g.label, genre == g) { genre = if (genre == g) null else g }
                }
            }
        }
        if (rows == null) {
            item { LoadingPage(Modifier.height(240.dp)) }
        } else {
            items(rows, key = { "${key}-${it.title}" }) { row ->
                val shown =
                    if (hideOwned) {
                        row.items.filter { it.availability != SeerrAvailability.AVAILABLE }
                    } else {
                        row.items
                    }
                if (shown.isNotEmpty()) {
                    DiscoverItemRow(
                        title = row.title,
                        items = shown,
                        onClickItem = { _, item -> viewModel.navigationManager.navigateTo(item.destination) },
                        onLongClickItem = { _, _ -> },
                        onCardFocus = {},
                        enableViewMore = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shape = ButtonDefaults.shape(RoundedCornerShape(20.dp)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        colors =
            ButtonDefaults.colors(
                containerColor = if (selected) Color.White else Color(0xFF2C2C33),
                contentColor = if (selected) Color.Black else Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        modifier = Modifier.height(40.dp),
    ) {
        Text(text = label, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

/**
 * A list of requests.
 * [arrivedOnly] true = only ones now on Benflix, false = only ones still on the way, null = all
 */
@Composable
private fun RequestList(
    viewModel: BenflixRequestsViewModel,
    mineOnly: Boolean,
    arrivedOnly: Boolean?,
) {
    LaunchedEffect(mineOnly) { viewModel.loadRequests(mineOnly) }
    val all by viewModel.requests.collectAsState()
    val list =
        all?.filter {
            when (arrivedOnly) {
                null -> true
                true -> it.available
                false -> !it.available
            }
        }
    when {
        list == null -> {
            LoadingPage(Modifier.fillMaxSize())
        }

        list.isEmpty() -> {
            Text(
                text =
                    when (arrivedOnly) {
                        true -> "Nothing you asked for has arrived yet."
                        false -> "You haven't asked for anything yet. Search for a movie or show and press Ask for it."
                        null -> "No requests yet."
                    },
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
            )
        }

        else -> {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list, key = { it.request.id ?: it.hashCode() }) { entry ->
                    RequestRow(
                        entry = entry,
                        showWho = !mineOnly,
                        canAnswer = !mineOnly && entry.status == RequestStatus.PENDING,
                        onOpen = { entry.item?.let { viewModel.navigationManager.navigateTo(it.destination) } },
                        onAnswer = { viewModel.answer(entry, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestRow(
    entry: RequestEntry,
    showWho: Boolean,
    canAnswer: Boolean,
    onOpen: () -> Unit,
    onAnswer: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF18181C))
                .padding(10.dp),
    ) {
        AsyncImage(
            model = entry.item?.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(width = 54.dp, height = 80.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF24242A)),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = entry.item?.title ?: "Unknown title",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val whenAsked = remember(entry.request.createdAt) { ago(entry.request.createdAt) }
            Text(
                text = listOfNotNull(if (showWho) "Asked by ${entry.who}" else "You asked", whenAsked).joinToString(" · "),
                fontSize = 14.sp,
                color = Color(0xFFA1A1AA),
                maxLines = 1,
            )
            StatusTag(entry)
        }
        if (canAnswer) {
            Button(onClick = { onAnswer(true) }) { Text("Approve") }
            Button(onClick = { onAnswer(false) }) { Text("Decline") }
        } else if (entry.item != null) {
            Button(onClick = onOpen) { Text(if (entry.available) "Watch" else "Details") }
        }
    }
}

@Composable
private fun StatusTag(entry: RequestEntry) {
    val (text, color) =
        when {
            entry.available -> "On Benflix" to Color(0xFF3DD68C)
            entry.status == RequestStatus.PENDING -> "Waiting for Ben" to Color(0xFFF5A524)
            entry.status == RequestStatus.DECLINED -> "Declined" to Color(0xFFE5484D)
            entry.status == RequestStatus.FAILURE -> "Something went wrong" to Color(0xFFE5484D)
            else -> "On the way" to Color(0xFF7AA7FF)
        }
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = .18f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
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
