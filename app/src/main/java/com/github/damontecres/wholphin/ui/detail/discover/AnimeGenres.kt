package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.services.AniListBrowseItem
import com.github.damontecres.wholphin.services.AniListCardFacts
import com.github.damontecres.wholphin.services.AniListKind
import com.github.damontecres.wholphin.services.AniListService
import com.github.damontecres.wholphin.services.AniListSort
import com.github.damontecres.wholphin.services.AniListTagChoice
import com.github.damontecres.wholphin.services.MalBrowse
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.discover.ActiveFilterChip
import com.github.damontecres.wholphin.ui.discover.RequestsChip
import com.github.damontecres.wholphin.ui.discover.RequestsSearchBox
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import timber.log.Timber
import javax.inject.Inject

/*
 * Benflix: anime genres on a request's details page (Isekai, Slice of Life...), from AniList, and a page
 * listing every anime with the genres you pick. Pick one genre to start, then add more to narrow it down.
 */

/** TMDB's "anime" keyword, and its Animation genre */
private const val ANIME_KEYWORD_ID = 210024
private const val ANIMATION_GENRE_ID = 16

/** True when Seerr's movie or show details look like anime */
internal fun isAnime(details: JsonObject): Boolean {
    val keywords = (details["keywords"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    if (keywords.any { it["id"].str() == ANIME_KEYWORD_ID.toString() || it["name"].str().equals("anime", true) }) return true
    val animation =
        (details["genres"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.any {
            it["id"].str() == ANIMATION_GENRE_ID.toString()
        }
    val japanese = (details["originalLanguage"].str() ?: details["original_language"].str()) == "ja"
    return animation && japanese
}

/** Popular AniList tags, shown first when adding to a deep search and as quick picks on the Requests Anime tab */
internal val POPULAR_ANIME_TAGS =
    listOf(
        "Isekai",
        "Harem",
        "Reverse Harem",
        "Mixed Gender Harem",
        "Shounen",
        "Seinen",
        "Shoujo",
        "Josei",
        "Iyashikei",
        "Super Power",
        "Magic",
        "Martial Arts",
        "School",
        "Military",
        "Space",
        "Survival",
        "Revenge",
        "Reincarnation",
        "Time Manipulation",
        "Video Games",
        "Demons",
        "Vampire",
        "Samurai",
        "Ninja",
        "Swordplay",
        "Idol",
        "Food",
        "Tragedy",
        "Coming of Age",
        "Found Family",
        "Time Travel",
        "Love Triangle",
        "Post-Apocalyptic",
        "Cyberpunk",
        "Mythology",
        "Detective",
        "Gods",
        "Zombie",
        "Work",
        "Workplace",
        "Band",
        "Cute Girls Doing Cute Things",
        "Romantic Subtext",
        "Overpowered Main Characters",
    )

/** AniList's genres (Ecchi is in: Ben said it isn't adult; real adult anime is never shown) */
private val GENRE_CHOICES = AniListService.ANILIST_GENRES.sorted()

// ---------------------------------------------------------------------------------------------
// Details page: the anime genre chips
// ---------------------------------------------------------------------------------------------

@HiltViewModel
class AnimeGenresViewModel
    @Inject
    constructor(
        private val seerrService: SeerrService,
        private val aniList: AniListService,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _genres = MutableStateFlow<List<String>>(emptyList())

        /** Anime-only genres first (Isekai, Shounen...), then plain ones (Action, Comedy...) */
        val genres: StateFlow<List<String>> = _genres
        private var loadedFor: String? = null

        fun load(
            isMovie: Boolean,
            tmdbId: Int,
        ) {
            val key = "${if (isMovie) "MOVIE" else "TV"}-$tmdbId"
            if (loadedFor == key) return
            loadedFor = key
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    val details =
                        seerrService.api
                            .getRaw(if (isMovie) "/movie/$tmdbId" else "/tv/$tmdbId")
                            ?.let { Json.parseToJsonElement(it).jsonObject } ?: return@launch
                    if (!isAnime(details)) return@launch
                    val names =
                        listOfNotNull(
                            details["title"].str() ?: details["name"].str(),
                            details["originalTitle"].str() ?: details["originalName"].str(),
                        )
                    val year =
                        (details["releaseDate"].str() ?: details["firstAirDate"].str())
                            ?.take(4)
                            ?.toIntOrNull()
                    val found = aniList.forRequest(key, names, year, isMovie) ?: return@launch
                    _genres.value = AniListCardFacts(found.score, found.tags).cardTags(found.tags.size)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "No anime genres for %s", key)
                }
            }
        }
    }

/**
 * Benflix: "ANIME GENRES" chips on an anime's request page. Pressing one opens every anime with that genre.
 * Shows 6, and "+N more" for the rest. Nothing shows for titles that aren't anime.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimeGenresBlock(
    isMovie: Boolean,
    tmdbId: Int,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    viewModel: AnimeGenresViewModel = hiltViewModel(key = "anime-genres-$isMovie-$tmdbId"),
) {
    LaunchedEffect(isMovie, tmdbId) { viewModel.load(isMovie, tmdbId) }
    val genres by viewModel.genres.collectAsState()
    if (genres.isEmpty()) return
    var expanded by rememberSaveable(isMovie, tmdbId) { mutableStateOf(false) }
    val shown = if (expanded) genres else genres.take(COLLAPSED)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Text(
            text = "ANIME GENRES",
            color = LABEL_GREY,
            fontSize = BenflixType.tag,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(end = 4.dp),
        )
        shown.forEach { genre ->
            GenrePill(
                text = genre,
                onClick = { viewModel.navigationManager.navigateTo(Destination.AnimeGenres(listOf(genre))) },
                onFocused = onFocused,
            )
        }
        if (genres.size > COLLAPSED) {
            GenrePill(
                text = if (expanded) "Show less" else "+${genres.size - COLLAPSED} more",
                onClick = { expanded = !expanded },
                onFocused = onFocused,
                quiet = true,
            )
        }
    }
}

@Composable
private fun GenrePill(
    text: String,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    quiet: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (quiet) Color.Transparent else ANIME_PURPLE.copy(alpha = .35f),
                contentColor = if (quiet) LABEL_GREY else Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        modifier = Modifier.onFocusChanged { if (it.isFocused) onFocused() },
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = text,
            fontSize = BenflixType.small,
            fontWeight = if (quiet) FontWeight.Normal else FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

private const val COLLAPSED = 6
private val LABEL_GREY = Color(0xFFAEB3BD)
private val ANIME_PURPLE = Color(0xFF7C5CFF)

/** AniList's whole tag list, so quick picks only offer tags AniList really has (saved on the device for a week) */
@HiltViewModel
class AnimeTagCatalogViewModel
    @Inject
    constructor(
        aniList: AniListService,
    ) : ViewModel() {
        private val _catalog = MutableStateFlow<List<AniListTagChoice>>(emptyList())
        val catalog: StateFlow<List<AniListTagChoice>> = _catalog

        init {
            viewModelScope.launch(WholphinDispatchers.IO) { _catalog.value = aniList.tagCatalog() }
        }
    }

// ---------------------------------------------------------------------------------------------
// The genre page: every anime with all the picked genres
// ---------------------------------------------------------------------------------------------

@HiltViewModel
class AnimeGenrePageViewModel
    @Inject
    constructor(
        private val seerrService: SeerrService,
        private val aniList: AniListService,
        private val malBrowse: MalBrowse,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _picked = MutableStateFlow<List<String>>(emptyList())
        val picked: StateFlow<List<String>> = _picked

        /** Genres and tags the anime must NOT have ("Isekai, but no Harem") */
        private val _leftOut = MutableStateFlow<List<String>>(emptyList())
        val leftOut: StateFlow<List<String>> = _leftOut

        /** How many anime match in all, when the source says */
        private val _total = MutableStateFlow<Int?>(null)
        val total: StateFlow<Int?> = _total

        /** True when AniList didn't answer and the list comes from MyAnimeList instead */
        private val _fromMal = MutableStateFlow(false)
        val fromMal: StateFlow<Boolean> = _fromMal

        /** Picked names AniList has no tag for (shown instead of an empty list) */
        private val _unknown = MutableStateFlow<List<String>>(emptyList())
        val unknown: StateFlow<List<String>> = _unknown

        /** Every AniList tag, by group, for the picker (empty until loaded, or if AniList can't be reached) */
        private val _catalog = MutableStateFlow<List<AniListTagChoice>>(emptyList())
        val catalog: StateFlow<List<AniListTagChoice>> = _catalog
        private var catalogAsked = false

        fun loadCatalog() {
            if (catalogAsked) return
            catalogAsked = true
            viewModelScope.launch(WholphinDispatchers.IO) {
                val found = aniList.tagCatalog()
                _catalog.value = found
                if (found.isEmpty()) catalogAsked = false
            }
        }

        private val _sort = MutableStateFlow(AniListSort.POPULAR)
        val sort: StateFlow<AniListSort> = _sort
        private val _kind = MutableStateFlow(AniListKind.ALL)
        val kind: StateFlow<AniListKind> = _kind

        /** null while the first page loads */
        private val _items = MutableStateFlow<List<AniListBrowseItem>?>(null)
        val items: StateFlow<List<AniListBrowseItem>?> = _items
        private val _failed = MutableStateFlow(false)
        val failed: StateFlow<Boolean> = _failed

        /** The title being looked up in Requests after it was pressed */
        private val _opening = MutableStateFlow<Int?>(null)
        val opening: StateFlow<Int?> = _opening

        private var page = 0
        private var hasMore = true
        private var job: Job? = null
        private var started = false

        fun start(initial: List<String>) {
            if (started) return
            started = true
            _picked.value = initial.distinct()
            loadCatalog()
            reload()
        }

        /** Sets everything picked at once (from the picker), then starts over from the top */
        fun setAll(
            must: List<String>,
            without: List<String>,
        ) {
            val m = must.distinct()
            val w = without.distinct().filter { it !in m }
            if (m == _picked.value && w == _leftOut.value) return
            _picked.value = m
            _leftOut.value = w
            reload()
        }

        fun removeLeftOut(name: String) {
            _leftOut.value = _leftOut.value - name
            reload()
        }

        fun clearAll() {
            _picked.value = emptyList()
            _leftOut.value = emptyList()
            reload()
        }

        fun add(genre: String) {
            if (genre in _picked.value) return
            _picked.value = _picked.value + genre
            reload()
        }

        fun remove(genre: String) {
            _picked.value = _picked.value - genre
            reload()
        }

        fun setSort(value: AniListSort) {
            if (_sort.value == value) return
            _sort.value = value
            reload()
        }

        fun setKind(value: AniListKind) {
            if (_kind.value == value) return
            _kind.value = value
            reload()
        }

        fun reload() {
            job?.cancel()
            page = 0
            hasMore = true
            _items.value = null
            _failed.value = false
            _total.value = null
            _fromMal.value = false
            _unknown.value = emptyList()
            loadMore()
        }

        /** The next page (50 from AniList, 25 from MyAnimeList), when you get near the bottom; it goes on to the very last one */
        fun loadMore() {
            if (!hasMore || job?.isActive == true) return
            if (_picked.value.isEmpty()) {
                _items.value = emptyList()
                hasMore = false
                return
            }
            job =
                viewModelScope.launch(WholphinDispatchers.IO) {
                    try {
                        // Use AniList's real names ("Harem" is "Female Harem" there); the tag list is saved on the device
                        if (_catalog.value.isEmpty()) _catalog.value = aniList.tagCatalog()
                        val catalog = _catalog.value
                        if (page == 0) {
                            val resolved = _picked.value.map { it to AniListService.resolveName(it, catalog) }
                            _unknown.value = resolved.filter { it.second == null }.map { it.first }
                            _picked.value = resolved.map { it.second ?: it.first }.distinct()
                            _leftOut.value = _leftOut.value.mapNotNull { AniListService.resolveName(it, catalog) }.distinct()
                            if (_unknown.value.isNotEmpty()) {
                                hasMore = false
                                _items.value = emptyList()
                                return@launch
                            }
                        }
                        val picked = _picked.value
                        val without = _leftOut.value
                        val next =
                            if (_fromMal.value) {
                                malBrowse.browse(picked, without, _sort.value, _kind.value, page + 1)
                                    ?: throw IllegalStateException("MyAnimeList didn't answer")
                            } else {
                                try {
                                    aniList.browse(picked, _sort.value, _kind.value, page + 1, without)
                                } catch (ex: CancellationException) {
                                    throw ex
                                } catch (ex: Exception) {
                                    // AniList is down or busy: start again from MyAnimeList, if it knows all the names
                                    if (page > 0) throw ex
                                    Timber.w(ex, "AniList deep search failed, trying MyAnimeList")
                                    malBrowse.browse(picked, without, _sort.value, _kind.value, 1)?.also { _fromMal.value = true }
                                        ?: throw ex
                                }
                            }
                        page++
                        hasMore = next.hasMore
                        if (page == 1) _total.value = next.total
                        val now = _items.value.orEmpty()
                        _items.value = (now + next.items).distinctBy { it.id }
                        // Later seasons are folded away, so a page can come back nearly empty: fetch on
                        if (hasMore && next.items.size < 8) {
                            job = null
                            loadMore()
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Anime genre page failed")
                        if (_items.value == null) _failed.value = true
                        hasMore = false
                        if (_items.value == null) _items.value = emptyList()
                    }
                }
        }

        /**
         * Opens a title's request page. AniList and Requests (TMDB) don't share numbers, so it's found in
         * Requests by its names and year.
         */
        fun open(
            item: AniListBrowseItem,
            notFound: () -> Unit,
        ) {
            if (_opening.value != null) return
            _opening.value = item.id
            viewModelScope.launch(WholphinDispatchers.IO) {
                val found =
                    try {
                        findInRequests(item)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not find %s in Requests", item.title)
                        null
                    }
                withContext(WholphinDispatchers.Main) {
                    _opening.value = null
                    if (found != null) navigationManager.navigateTo(found.destination) else notFound()
                }
            }
        }

        private suspend fun findInRequests(item: AniListBrowseItem) =
            findAnimeInRequests(seerrService, item.titles, item.year, item.isMovie)
    }

/**
 * Benflix: finds an AniList anime in Requests. AniList and Requests (TMDB) don't share numbers, so it's
 * looked up by its names and year. A show AniList splits in seasons is one show in Requests.
 */
internal suspend fun findAnimeInRequests(
    seerrService: SeerrService,
    titles: List<String>,
    year: Int?,
    isMovie: Boolean,
): com.github.damontecres.wholphin.data.model.DiscoverItem? {
    val wantedType = if (isMovie) SeerrItemType.MOVIE else SeerrItemType.TV
    val names = titles.filter { name -> name.any { it.isLetterOrDigit() && it.code < 0x2E80 } }.take(3)
    val wanted =
        titles
            .map { AniListService.clean(it) }
            .filter { it.isNotEmpty() }
            .toSet()
    for (name in names) {
        val results =
            seerrService
                .search(AniListService.searchable(name))
                .map { seerrService.createDiscoverItem(it) }
                .filter { it.type == wantedType }
        if (results.isEmpty()) continue

        fun yearOk(
            d: com.github.damontecres.wholphin.data.model.DiscoverItem,
            slack: Int,
        ): Boolean {
            val y = d.releaseDate?.year ?: return true
            return year == null || kotlin.math.abs(y - year) <= slack
        }

        fun nameOf(d: com.github.damontecres.wholphin.data.model.DiscoverItem) = AniListService.clean(d.title ?: "")
        results.firstOrNull { nameOf(it) in wanted && yearOk(it, 1) }?.let { return it }
        results
            .firstOrNull { d ->
                val n = nameOf(d)
                n.length >= 4 && wanted.any { w -> w.startsWith(n) || n.startsWith(w) } && yearOk(d, 2)
            }?.let { return it }
        results.firstOrNull { nameOf(it) in wanted }?.let { return it }
    }
    return null
}

/**
 * Benflix: the anime deep search. Every anime with all of the picked genres and tags (and none of the
 * left-out ones), page after page to the very last one, with sort and shows/movies to narrow it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimeGenrePage(
    initial: List<String>,
    modifier: Modifier = Modifier,
    viewModel: AnimeGenrePageViewModel = hiltViewModel(key = "anime-genre-page-${initial.joinToString("+")}"),
) {
    LaunchedEffect(initial) { viewModel.start(initial) }
    val picked by viewModel.picked.collectAsState()
    val leftOut by viewModel.leftOut.collectAsState()
    val total by viewModel.total.collectAsState()
    val fromMal by viewModel.fromMal.collectAsState()
    val catalog by viewModel.catalog.collectAsState()
    val unknown by viewModel.unknown.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val kind by viewModel.kind.collectAsState()
    val items by viewModel.items.collectAsState()
    val failed by viewModel.failed.collectAsState()
    val opening by viewModel.opening.collectAsState()
    val isTv = rememberIsTv()
    val context = LocalContext.current
    // Opened from "Deep search" with nothing picked yet: start on the picker
    var showAdd by rememberSaveable { mutableStateOf(initial.isEmpty()) }
    var showSort by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    // Load the next 30 when you're near the end
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 8
        }.distinctUntilChanged().collect { nearEnd -> if (nearEnd) viewModel.loadMore() }
    }

    if (showAdd) {
        LaunchedEffect(Unit) { viewModel.loadCatalog() }
        AddGenreDialog(
            picked = picked,
            leftOut = leftOut,
            catalog = catalog,
            onApply = { must, without ->
                showAdd = false
                viewModel.setAll(must, without)
            },
            onDismiss = { showAdd = false },
        )
    }
    if (showSort) {
        BasicDialog(onDismissRequest = { showSort = false }) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                Text("Sort and type", fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold, color = Color.White)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AniListSort.entries.forEach { s ->
                        RequestsChip(s.label, sort == s, {
                            viewModel.setSort(s)
                            showSort = false
                        })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AniListKind.entries.forEach { k ->
                        RequestsChip(k.label, kind == k, {
                            viewModel.setKind(k)
                            showSort = false
                        })
                    }
                }
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (isTv) 150.dp else 104.dp),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 16.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(if (isTv) 18.dp else 14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp, end = if (isTv) 24.dp else 8.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (picked.isEmpty()) "Anime deep search" else picked.joinToString(" + "),
                    color = Color.White,
                    fontSize = if (isTv) BenflixType.pageTitle else BenflixType.rowName,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        listOfNotNull(
                            if (picked.isEmpty()) "Pick genres and tags to see every anime that has them." else "Anime with all of these",
                            total?.takeIf { picked.isNotEmpty() }?.let {
                                String.format(java.util.Locale.US, "%,d found", it)
                            },
                            sort.label,
                            kind.label,
                        ).joinToString(" · "),
                    color = LABEL_GREY,
                    fontSize = BenflixType.small,
                )
                if (fromMal) {
                    Text(
                        text = "AniList didn't answer, so this list is from MyAnimeList.",
                        color = LABEL_GREY,
                        fontSize = BenflixType.small,
                    )
                }
            }
        }
        item(key = "chips", span = { GridItemSpan(maxLineSpan) }) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                picked.forEach { genre -> ActiveFilterChip(genre) { viewModel.remove(genre) } }
                leftOut.forEach { name -> ActiveFilterChip("Not $name") { viewModel.removeLeftOut(name) } }
                RequestsChip("+ Genres and tags", picked.isEmpty(), { showAdd = true })
                RequestsChip("Sort: ${sort.label}", false, { showSort = true })
                if (picked.size + leftOut.size > 1) RequestsChip("Clear all", false, { viewModel.clearAll() })
            }
        }
        val list = items
        when {
            list == null -> {
                item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                    Text("Loading…", color = LABEL_GREY, fontSize = BenflixType.body, modifier = Modifier.padding(vertical = 24.dp))
                }
            }

            list.isEmpty() -> {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 24.dp)) {
                        Text(
                            text =
                                when {
                                    failed -> {
                                        "AniList didn't answer. Try again in a minute."
                                    }

                                    unknown.isNotEmpty() -> {
                                        "AniList has no tag called ${unknown.joinToString(
                                            ", ",
                                        )}. Take it away and pick one from \"+ Genres and tags\"."
                                    }

                                    picked.isEmpty() -> {
                                        "Press \"+ Genres and tags\" to pick what you're looking for."
                                    }

                                    else -> {
                                        "No anime has all of these. Take one away to see more."
                                    }
                                },
                            color = Color.White,
                            fontSize = BenflixType.body,
                        )
                        if (failed) {
                            Button(
                                onClick = { viewModel.reload() },
                                scale = ButtonDefaults.scale(focusedScale = 1f),
                            ) { Text("Try again") }
                        }
                    }
                }
            }

            else -> {
                itemsIndexed(list, key = { _, it -> "a-${it.id}" }) { _, anime ->
                    AnimePoster(
                        anime = anime,
                        busy = opening == anime.id,
                        onClick = {
                            viewModel.open(anime) {
                                com.github.damontecres.wholphin.ui.components.BenflixNotices.pill(
                                    "Couldn't find ${anime.title} in Requests",
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimePoster(
    anime: AniListBrowseItem,
    busy: Boolean,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF26262C), focusedContainerColor = Color.White),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = anime.cover,
                    contentDescription = anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                )
                if (anime.isMovie) {
                    Text(
                        text = "MOVIE",
                        color = Color.White,
                        fontSize = BenflixType.tag,
                        fontWeight = FontWeight.Bold,
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .background(Color.Black.copy(alpha = .7f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (busy) {
                    Text(
                        text = "Opening…",
                        color = Color.White,
                        fontSize = BenflixType.small,
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .background(Color.Black.copy(alpha = .75f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
        Text(
            text = anime.title,
            color = Color.White,
            fontSize = BenflixType.small,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text =
                listOfNotNull(
                    anime.year?.toString(),
                    anime.score?.let { "AniList " + String.format(java.util.Locale.US, "%.1f", it / 10.0) },
                ).joinToString(" · "),
            color = LABEL_GREY,
            fontSize = BenflixType.tag,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Pick genres and tags for the deep search: all of AniList's genres, its popular tags first, then every
 * other tag in its group (a few hundred). "Must have" picks narrow the list; "Leave out" picks remove anime
 * with that genre or tag. A search box finds a tag by name.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddGenreDialog(
    picked: List<String>,
    leftOut: List<String>,
    catalog: List<AniListTagChoice>,
    onApply: (List<String>, List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var must by remember { mutableStateOf(picked) }
    var without by remember { mutableStateOf(leftOut) }
    var leaving by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val maxHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.88f).dp
    val sections =
        remember(catalog, query) {
            val popular = POPULAR_ANIME_TAGS.mapNotNull { AniListService.resolveName(it, catalog) }.distinct()
            val q = query.trim()
            if (q.isNotEmpty()) {
                val all = (GENRE_CHOICES + popular + catalog.map { it.name }).distinct()
                listOf("Matches for \"$q\"" to all.filter { it.contains(q, ignoreCase = true) }.sorted())
            } else {
                listOf("Genres" to GENRE_CHOICES, "Popular tags" to popular) +
                    catalog
                        .filter { it.name !in popular }
                        .groupBy { it.category }
                        .toSortedMap()
                        .map { (category, tags) -> category.replace("-", " · ") to tags.map { it.name }.sorted() }
            }
        }

    fun toggle(name: String) {
        when {
            name in must -> must = must - name
            name in without -> without = without - name
            leaving -> without = without + name
            else -> must = must + name
        }
    }
    BasicDialog(onDismissRequest = onDismiss) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(24.dp),
            modifier =
                Modifier
                    .widthIn(max = 820.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
        ) {
            item(key = "head") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Genres and tags", fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        text =
                            if (leaving) {
                                "Leave out: anime with these are taken off the list."
                            } else {
                                "Must have: only anime with every one of these stay on the list."
                            },
                        fontSize = BenflixType.small,
                        color = LABEL_GREY,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onApply(must, without) }, scale = ButtonDefaults.scale(focusedScale = 1f)) {
                            Text(if (must.isEmpty()) "Done" else "Show anime (${must.size + without.size})")
                        }
                        RequestsChip("Must have", !leaving, { leaving = false })
                        RequestsChip("Leave out", leaving, { leaving = true })
                        if (must.isNotEmpty() || without.isNotEmpty()) {
                            RequestsChip("Clear", false, {
                                must = emptyList()
                                without = emptyList()
                            })
                        }
                        Button(onClick = onDismiss, scale = ButtonDefaults.scale(focusedScale = 1f)) { Text("Cancel") }
                    }
                    RequestsSearchBox(
                        query = query,
                        onQuery = { query = it },
                        placeholder = "Find a tag, like Isekai or Time Travel",
                        modifier = Modifier.padding(start = 0.dp),
                    )
                    if (catalog.isEmpty()) {
                        Text(
                            text = "Loading every AniList tag… the popular ones are here already.",
                            fontSize = BenflixType.small,
                            color = LABEL_GREY,
                        )
                    }
                }
            }
            sections.forEach { (title, choices) ->
                if (choices.isEmpty()) return@forEach
                item(key = "s-$title") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(title, fontSize = BenflixType.body, fontWeight = FontWeight.Bold, color = LABEL_GREY)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            choices.forEach { choice ->
                                val label =
                                    when (choice) {
                                        in must -> "✓ $choice"
                                        in without -> "Not $choice"
                                        else -> choice
                                    }
                                RequestsChip(label, choice in must || choice in without, { toggle(choice) })
                            }
                        }
                    }
                }
            }
            if (sections.all { it.second.isEmpty() }) {
                item(key = "none") { Text("No tag has that name.", fontSize = BenflixType.body, color = Color.White) }
            }
        }
    }
}
