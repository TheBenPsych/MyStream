package com.github.damontecres.wholphin.ui.detail.discover

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.discover.ActiveFilterChip
import com.github.damontecres.wholphin.ui.discover.RequestsChip
import com.github.damontecres.wholphin.ui.nav.Destination
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

/** Popular AniList themes to add on the genre page, besides the genres themselves */
private val THEMES =
    listOf(
        "Isekai",
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
        "Harem",
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

/** Genres offered on the genre page (Ecchi left out of the quick list on purpose) */
private val GENRE_CHOICES = AniListService.ANILIST_GENRES.filter { it != "Ecchi" }.sorted()

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
            fontSize = 12.sp,
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
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = if (quiet) FontWeight.Normal else FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

private const val COLLAPSED = 6
private val LABEL_GREY = Color(0xFFAEB3BD)
private val ANIME_PURPLE = Color(0xFF7C5CFF)

// ---------------------------------------------------------------------------------------------
// The genre page: every anime with all the picked genres
// ---------------------------------------------------------------------------------------------

@HiltViewModel
class AnimeGenrePageViewModel
    @Inject
    constructor(
        private val seerrService: SeerrService,
        private val aniList: AniListService,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _picked = MutableStateFlow<List<String>>(emptyList())
        val picked: StateFlow<List<String>> = _picked
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
            loadMore()
        }

        /** The next 30, when you get near the bottom */
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
                        val next = aniList.browse(_picked.value, _sort.value, _kind.value, page + 1)
                        page++
                        hasMore = next.hasMore
                        val now = _items.value.orEmpty()
                        _items.value = (now + next.items).distinctBy { it.id }
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

        private suspend fun findInRequests(item: AniListBrowseItem): com.github.damontecres.wholphin.data.model.DiscoverItem? {
            val wantedType = if (item.isMovie) SeerrItemType.MOVIE else SeerrItemType.TV
            val names = item.titles.filter { name -> name.any { it.isLetterOrDigit() && it.code < 0x2E80 } }.take(3)
            val wanted =
                item.titles
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
                    return item.year == null || kotlin.math.abs(y - item.year) <= slack
                }

                fun nameOf(d: com.github.damontecres.wholphin.data.model.DiscoverItem) = AniListService.clean(d.title ?: "")
                results.firstOrNull { nameOf(it) in wanted && yearOk(it, 1) }?.let { return it }
                // A show AniList splits in seasons ("Show Season 2") is one show in Requests
                results
                    .firstOrNull { d ->
                        val n = nameOf(d)
                        n.length >= 4 && wanted.any { w -> w.startsWith(n) || n.startsWith(w) } && yearOk(d, 2)
                    }?.let { return it }
                results.firstOrNull { nameOf(it) in wanted }?.let { return it }
            }
            return null
        }
    }

/** Benflix: every anime with all of the picked genres, with more genres, sort and shows/movies to narrow it */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimeGenrePage(
    initial: List<String>,
    modifier: Modifier = Modifier,
    viewModel: AnimeGenrePageViewModel = hiltViewModel(key = "anime-genre-page-${initial.joinToString("+")}"),
) {
    LaunchedEffect(initial) { viewModel.start(initial) }
    val picked by viewModel.picked.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val kind by viewModel.kind.collectAsState()
    val items by viewModel.items.collectAsState()
    val failed by viewModel.failed.collectAsState()
    val opening by viewModel.opening.collectAsState()
    val isTv = rememberIsTv()
    val context = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
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
        AddGenreDialog(
            picked = picked,
            onPick = {
                showAdd = false
                viewModel.add(it)
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
                Text("Sort and type", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                    text = if (picked.isEmpty()) "Anime genres" else picked.joinToString(" + "),
                    color = Color.White,
                    fontSize = if (isTv) 30.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Anime with every genre picked. ${sort.label} · ${kind.label}",
                    color = LABEL_GREY,
                    fontSize = 14.sp,
                )
            }
        }
        item(key = "chips", span = { GridItemSpan(maxLineSpan) }) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                picked.forEach { genre -> ActiveFilterChip(genre) { viewModel.remove(genre) } }
                RequestsChip("+ Add a genre", false, { showAdd = true })
                RequestsChip("Sort: ${sort.label}", false, { showSort = true })
            }
        }
        val list = items
        when {
            list == null -> {
                item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                    Text("Loading…", color = LABEL_GREY, fontSize = 16.sp, modifier = Modifier.padding(vertical = 24.dp))
                }
            }

            list.isEmpty() -> {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 24.dp)) {
                        Text(
                            text =
                                when {
                                    failed -> "AniList didn't answer. Try again in a minute."
                                    picked.isEmpty() -> "Add a genre to see anime."
                                    else -> "No anime has all of these. Take one away to see more."
                                },
                            color = Color.White,
                            fontSize = 16.sp,
                        )
                        if (failed) Button(onClick = { viewModel.reload() }) { Text("Try again") }
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
                                Toast
                                    .makeText(context, "Couldn't find ${anime.title} in Requests", Toast.LENGTH_SHORT)
                                    .show()
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
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
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
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .background(Color.Black.copy(alpha = .7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (busy) {
                    Text(
                        text = "Opening…",
                        color = Color.White,
                        fontSize = 13.sp,
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
            fontSize = 14.sp,
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
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

/** Pick another genre or theme to add */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddGenreDialog(
    picked: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BasicDialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
        ) {
            Text("Add a genre", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Only anime with every picked genre stay on the page.", fontSize = 14.sp, color = LABEL_GREY)
            listOf("Genres" to GENRE_CHOICES, "Themes" to THEMES).forEach { (title, choices) ->
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = LABEL_GREY)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.filter { it !in picked }.forEach { choice ->
                        RequestsChip(choice, false, { onPick(choice) })
                    }
                }
            }
            Row(Modifier.height(4.dp)) {}
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    }
}
