package com.github.damontecres.wholphin.ui.discover

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.services.ANIME_FEELS
import com.github.damontecres.wholphin.services.AniListLibraries
import com.github.damontecres.wholphin.services.AniListService
import com.github.damontecres.wholphin.services.AniListTagChoice
import com.github.damontecres.wholphin.services.AnimeAiringCard
import com.github.damontecres.wholphin.services.AnimeCard
import com.github.damontecres.wholphin.services.AnimeDiscover
import com.github.damontecres.wholphin.services.AnimeFeel
import com.github.damontecres.wholphin.services.AnimeSeasonPick
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.detail.discover.findAnimeInRequests
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/** One list on the anime tab: null items while it loads */
data class AnimeListState(
    val items: List<AnimeCard>? = null,
    val failed: Boolean = false,
)

/** What's already on Benflix, from the anime libraries (their AniList matches and their names) */
data class OwnedAnime(
    val ids: Set<Int>,
    val malIds: Set<Int>,
    val names: Set<String>,
) {
    fun has(card: AnimeCard): Boolean {
        if (card.id > 0 && card.id in ids) return true
        if (card.malId?.let { it in malIds } == true) return true
        val cleaned = card.titles.map { AniListService.clean(it) }.filter { it.isNotEmpty() }
        if (cleaned.any { it in names }) return true
        // A new season of a show that's here ("Show Season 2") counts as here
        return card.later && cleaned.any { t -> names.any { n -> n.length >= 5 && t.startsWith(n) } }
    }
}

/** The lists in the anime side list, in order */
enum class AnimePick(
    val label: String,
    val chip: String,
) {
    SEASON("This season", "This season"),
    TOP("Top 10 this week", "Top 10"),
    WEEK("This week", "This week"),
    FEEL("Find by feel", "By feel"),
    TAGS("Mix and match tags", "Tags"),
    GEMS("Hidden gems", "Gems"),
    DUBBED("Finished and dubbed", "Dubbed"),
    MOVIES("Movies", "Movies"),
    NEXT("Coming next season", "Next season"),
}

@HiltViewModel
class AnimeRequestsViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val discover: AnimeDiscover,
        private val aniList: AniListService,
        private val aniListLibraries: AniListLibraries,
        private val seerrService: SeerrService,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _lists = MutableStateFlow<Map<String, AnimeListState>>(emptyMap())
        val lists: StateFlow<Map<String, AnimeListState>> = _lists

        private val _week = MutableStateFlow<List<AnimeAiringCard>?>(null)
        val week: StateFlow<List<AnimeAiringCard>?> = _week
        private val _weekFailed = MutableStateFlow(false)
        val weekFailed: StateFlow<Boolean> = _weekFailed

        private val _dubs = MutableStateFlow<Set<Int>?>(null)

        /** MyAnimeList numbers with an English dub (null while it loads, empty if it couldn't be had) */
        val dubs: StateFlow<Set<Int>?> = _dubs

        private val _owned = MutableStateFlow(OwnedAnime(emptySet(), emptySet(), emptySet()))
        val owned: StateFlow<OwnedAnime> = _owned

        private val _catalog = MutableStateFlow<List<AniListTagChoice>>(emptyList())

        /** AniList's live tag list, so feels and tag buttons AniList no longer has are hidden */
        val catalog: StateFlow<List<AniListTagChoice>> = _catalog

        private val _opening = MutableStateFlow<Int?>(null)
        val opening: StateFlow<Int?> = _opening

        init {
            viewModelScope.launch(WholphinDispatchers.IO) { _dubs.value = discover.dubbed() }
            viewModelScope.launch(WholphinDispatchers.IO) { _catalog.value = aniList.tagCatalog() }
            viewModelScope.launch(WholphinDispatchers.IO) { loadOwned() }
        }

        private suspend fun loadOwned() {
            try {
                val userId = serverRepository.currentUser?.id ?: return
                val libraries =
                    api.userViewsApi
                        .getUserViews(userId = userId)
                        .content.items
                        .filter { it.name?.contains("anime", ignoreCase = true) == true }
                        .map { it.id }
                val ids = mutableSetOf<Int>()
                val malIds = mutableSetOf<Int>()
                val names = mutableSetOf<String>()
                libraries.forEach { lib ->
                    aniList.knownIndex(lib).values.forEach { e ->
                        if (e.id > 0) ids += e.id
                        e.malId?.takeIf { it > 0 }?.let { malIds += it }
                    }
                    aniListLibraries.titlesIn(lib, userId).forEach { dto ->
                        listOfNotNull(dto.name, dto.originalTitle)
                            .map { AniListService.clean(it) }
                            .filter { it.isNotEmpty() }
                            .forEach { names += it }
                        dto.providerIds
                            ?.get("AniList")
                            ?.toIntOrNull()
                            ?.let { ids += it }
                        dto.providerIds
                            ?.get("MyAnimeList")
                            ?.toIntOrNull()
                            ?.let { malIds += it }
                    }
                }
                _owned.value = OwnedAnime(ids, malIds, names)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Anime Requests: couldn't list what's already on Benflix")
            }
        }

        fun load(
            ask: AnimeDiscover.Ask,
            again: Boolean = false,
        ) {
            val now = _lists.value[ask.key]
            // Already here (or on its way); one that failed is tried again
            if (now != null && (now.items == null || (!now.failed && !again))) return
            _lists.update { it + (ask.key to AnimeListState()) }
            viewModelScope.launch(WholphinDispatchers.IO) {
                val state =
                    try {
                        AnimeListState(discover.list(ask))
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Anime list %s didn't load", ask.key)
                        AnimeListState(emptyList(), failed = true)
                    }
                _lists.update { it + (ask.key to state) }
            }
        }

        fun loadWeek(again: Boolean = false) {
            if (_week.value != null && !again) return
            _week.value = null
            _weekFailed.value = false
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    _week.value = discover.thisWeek()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Anime calendar didn't load")
                    _weekFailed.value = true
                    _week.value = emptyList()
                }
            }
        }

        fun retryDubs() {
            _dubs.value = null
            viewModelScope.launch(WholphinDispatchers.IO) { _dubs.value = discover.dubbed() }
        }

        fun seasonAsk(pick: AnimeSeasonPick) = discover.seasonAsk(pick)

        val topAsk get() = discover.topAsk
        val gemsAsk get() = discover.gemsAsk
        val finishedAsk get() = discover.finishedAsk
        val moviesAsk get() = discover.moviesAsk

        fun nextAsk() = discover.nextSeasonAsk()

        fun feelAsk(feel: AnimeFeel) = discover.feelAsk(feel, _catalog.value)

        /**
         * Finds [card] in Requests (AniList and Requests don't share numbers), then opens its request page,
         * or hands it to [then] (Ask, or the hold-down menu).
         */
        fun open(
            card: AnimeCard,
            then: ((DiscoverItem) -> Unit)? = null,
        ) {
            if (_opening.value != null) return
            _opening.value = card.id
            viewModelScope.launch(WholphinDispatchers.IO) {
                val found =
                    try {
                        findAnimeInRequests(seerrService, card.titles, card.year, card.isMovie)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not find %s in Requests", card.title)
                        null
                    }
                if (found == null) showToast(context, "Couldn't find ${card.title} in Requests yet. Try Search anime.")
                withContext(WholphinDispatchers.Main) {
                    _opening.value = null
                    if (found != null) {
                        if (then != null) then(found) else navigationManager.navigateTo(found.destination)
                    }
                }
            }
        }
    }

/**
 * Benflix: the Requests > Anime tab (Ben's design, 2026-11-12). Requests layout C: a list on the left (chips on
 * phones) and a poster wall on the right, filled with anime ways in: This season (opens here), Top 10,
 * This week's calendar, Find by feel, Mix and match tags, Hidden gems, Finished and dubbed, Movies and
 * Coming next season. Shows already on Benflix are left out, with a small note saying how many.
 */
@Composable
internal fun AnimeShelf(
    isTv: Boolean,
    isAdmin: Boolean,
    mineCount: Int,
    waitingCount: Int,
    onSearch: () -> Unit,
    onList: (waiting: Boolean) -> Unit,
    onAsk: (DiscoverItem) -> Unit,
    onMenu: (DiscoverItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnimeRequestsViewModel = hiltViewModel(),
) {
    var pick by rememberSaveable { mutableStateOf(AnimePick.SEASON) }
    val nowSeason = remember { AnimeSeasonPick.now() }
    val seasons = remember(nowSeason) { listOf(nowSeason, nowSeason.previous(), nowSeason.previous().previous()) }
    var seasonIndex by rememberSaveable { mutableStateOf(0) }
    var feelName by rememberSaveable { mutableStateOf<String?>(null) }
    val catalog by viewModel.catalog.collectAsState()
    val feels =
        remember(catalog) {
            ANIME_FEELS.filter { f -> catalog.isEmpty() || f.tags.all { AniListService.resolveName(it, catalog) != null } }
        }
    val feel = feels.firstOrNull { it.name == feelName }
    val ask: AnimeDiscover.Ask? =
        when (pick) {
            AnimePick.SEASON -> viewModel.seasonAsk(seasons[seasonIndex.coerceIn(0, seasons.lastIndex)])
            AnimePick.TOP -> viewModel.topAsk
            AnimePick.GEMS -> viewModel.gemsAsk
            AnimePick.DUBBED -> viewModel.finishedAsk
            AnimePick.MOVIES -> viewModel.moviesAsk
            AnimePick.NEXT -> viewModel.nextAsk()
            AnimePick.FEEL -> feel?.let { viewModel.feelAsk(it) }
            AnimePick.WEEK, AnimePick.TAGS -> null
        }
    // Moving down the TV list picks as you go, so wait a moment before asking AniList
    LaunchedEffect(ask?.key, pick) {
        delay(if (isTv) 300 else 0)
        if (ask != null) viewModel.load(ask)
        if (pick == AnimePick.WEEK) viewModel.loadWeek()
    }
    val wall: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            when (pick) {
                AnimePick.WEEK -> {
                    AnimeCalendar(isTv, viewModel, onAsk)
                }

                AnimePick.TAGS -> {
                    AnimeTagWall(catalog, viewModel)
                }

                AnimePick.FEEL -> {
                    if (feel == null) {
                        FeelWall(feels, isTv) { feelName = it.name }
                    } else {
                        AnimeWall(
                            title = feel.name,
                            note = feel.recipe,
                            ask = ask,
                            ranked = false,
                            isTv = isTv,
                            viewModel = viewModel,
                            onMenu = onMenu,
                            top = { RequestsChip("All feels", false, { feelName = null }) },
                        )
                    }
                }

                AnimePick.SEASON -> {
                    val s = seasons[seasonIndex.coerceIn(0, seasons.lastIndex)]
                    AnimeWall(
                        title = s.label,
                        note = if (seasonIndex == 0) "Airing now" else "Last season".takeIf { seasonIndex == 1 },
                        ask = ask,
                        ranked = false,
                        isTv = isTv,
                        viewModel = viewModel,
                        onMenu = onMenu,
                        top = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                seasons.forEachIndexed { i, season ->
                                    RequestsChip(season.label, i == seasonIndex, { seasonIndex = i })
                                }
                            }
                        },
                    )
                }

                AnimePick.DUBBED -> {
                    val dubState by viewModel.dubs.collectAsState()
                    val dubs = dubState
                    when {
                        dubs == null -> {
                            LoadingPage(Modifier.height(240.dp), focusEnabled = false)
                        }

                        dubs.isEmpty() -> {
                            LoadFailed("Couldn't get the list of dubbed anime.", onRetry = viewModel::retryDubs)
                        }

                        else -> {
                            AnimeWall(
                                title = "Finished and dubbed",
                                note = "Every episode out, with English voices",
                                ask = ask,
                                ranked = false,
                                isTv = isTv,
                                viewModel = viewModel,
                                onMenu = onMenu,
                                keep = { c -> c.malId?.let { it in dubs } == true },
                            )
                        }
                    }
                }

                else -> {
                    AnimeWall(
                        title = pick.label,
                        note =
                            when (pick) {
                                AnimePick.TOP -> "What anime fans are watching most"
                                AnimePick.GEMS -> "Scores of 8 and up that few people have found"
                                AnimePick.NEXT -> AnimeSeasonPick.now().next().label
                                else -> null
                            },
                        ask = ask,
                        ranked = pick == AnimePick.TOP,
                        isTv = isTv,
                        viewModel = viewModel,
                        onMenu = onMenu,
                    )
                }
            }
        }
    }
    if (isTv) {
        Row(modifier.fillMaxSize()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 32.dp),
                modifier = Modifier.width(240.dp).fillMaxHeight(),
            ) {
                item(key = "an-search") { SideEntry("Search anime", selected = false, onClick = onSearch) }
                items(AnimePick.entries, key = { "an-${it.name}" }) { p ->
                    SideEntry(
                        p.label,
                        selected = p == pick,
                        onPick = {
                            if (p != pick && p == AnimePick.FEEL) feelName = null
                            pick = p
                        },
                    )
                }
                item(key = "an-yours") { SideHeading("Yours") }
                item(key = "an-mine") { SideEntry("Your requests $mineCount", selected = false, onClick = { onList(false) }) }
                if (isAdmin) {
                    item(key = "an-waiting") {
                        SideEntry("Waiting for you $waitingCount", selected = false, dot = waitingCount > 0, onClick = { onList(true) })
                    }
                }
            }
            wall(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(modifier.fillMaxSize()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            ) {
                items(AnimePick.entries, key = { "anc-${it.name}" }) { p ->
                    RequestsChip(p.chip, p == pick, {
                        if (p == AnimePick.FEEL) feelName = null
                        pick = p
                    })
                }
                item(key = "anc-mine") { RequestsChip("Your requests $mineCount", false, { onList(false) }) }
                if (isAdmin) {
                    item(key = "anc-waiting") {
                        RequestsChip(
                            if (waitingCount ==
                                0
                            ) {
                                "Waiting for you 0"
                            } else {
                                "Waiting for you $waitingCount  ●"
                            },
                            false,
                            { onList(true) },
                        )
                    }
                }
            }
            wall(Modifier.fillMaxWidth().weight(1f))
        }
    }
}

/** One poster wall: a title line (with how many are already on Benflix), anything on [top], then the posters */
@Composable
private fun AnimeWall(
    title: String,
    note: String?,
    ask: AnimeDiscover.Ask?,
    ranked: Boolean,
    isTv: Boolean,
    viewModel: AnimeRequestsViewModel,
    onMenu: (DiscoverItem) -> Unit,
    keep: (AnimeCard) -> Boolean = { true },
    top: (@Composable () -> Unit)? = null,
) {
    val lists by viewModel.lists.collectAsState()
    val owned by viewModel.owned.collectAsState()
    val dubs by viewModel.dubs.collectAsState()
    val opening by viewModel.opening.collectAsState()
    val state = ask?.let { lists[it.key] }
    val all = state?.items?.filter(keep)
    val shown = remember(all, owned) { all?.filterNot { owned.has(it) } }
    val here = (all?.size ?: 0) - (shown?.size ?: 0)
    val line = listOfNotNull(note, if (here > 0) "$here already on Benflix" else null).joinToString(" · ")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gap = 12.dp
        val side = 16.dp
        val posterWidth = if (isTv) 120.dp else ((maxWidth - side * 2 - gap * 2) / 3)
        val gridState = rememberLazyGridState()
        LaunchedEffect(ask?.key) { gridState.scrollToItem(0) }
        LazyVerticalGrid(
            columns = if (isTv) GridCells.Adaptive(posterWidth + 4.dp) else GridCells.Fixed(3),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            contentPadding = PaddingValues(start = side, end = side, top = 8.dp, bottom = 48.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "head", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    top?.invoke()
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(title, fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold, color = BenflixColors.Text)
                        if (line.isNotEmpty()) {
                            Text(
                                line,
                                fontSize = BenflixType.small,
                                color = BenflixColors.Muted,
                                maxLines = 2,
                                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
                            )
                        }
                    }
                }
            }
            when {
                shown == null -> {
                    item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                        LoadingPage(Modifier.height(240.dp), focusEnabled = false)
                    }
                }

                state?.failed == true && shown.isEmpty() -> {
                    item(key = "failed", span = { GridItemSpan(maxLineSpan) }) {
                        LoadFailed("These didn't load. AniList may be busy.", onRetry = { ask?.let { viewModel.load(it, again = true) } })
                    }
                }

                shown.isEmpty() -> {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            if (here > 0) "You already have all of these on Benflix." else "Nothing here right now.",
                            fontSize = BenflixType.lead,
                            color = BenflixColors.Muted,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }

                else -> {
                    itemsIndexed(shown, key = { _, c -> "an-${c.id}" }) { index, card ->
                        AnimeCardPoster(
                            card = card,
                            width = posterWidth,
                            rank = if (ranked && index < 10) index + 1 else null,
                            dub = dubWord(card, dubs),
                            busy = opening == card.id,
                            onClick = { viewModel.open(card) },
                            onLongClick = { viewModel.open(card, onMenu) },
                        )
                    }
                }
            }
        }
    }
}

/** "Sub" or "Sub+Dub" (English dub), once the dub list is in */
private fun dubWord(
    card: AnimeCard,
    dubs: Set<Int>?,
): String? {
    if (dubs.isNullOrEmpty()) return null
    return if (card.malId?.let { it in dubs } == true) "Sub+Dub" else "Sub"
}

/** An anime poster: AniList score top right, Sub / Sub+Dub bottom left, "Ep 6 of 12" under the name while airing */
@Composable
private fun AnimeCardPoster(
    card: AnimeCard,
    width: Dp,
    rank: Int?,
    dub: String?,
    busy: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.width(width)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            shape = ClickableSurfaceDefaults.shape(shape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = BenflixColors.Raised,
                    focusedContainerColor = BenflixColors.Raised,
                    pressedContainerColor = BenflixColors.Raised,
                ),
            border =
                com.github.damontecres.wholphin.ui.door
                    .doorFocusBorder(shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier = Modifier.size(width, width * 1.5f),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (card.cover != null) {
                    AsyncImage(
                        model = card.cover,
                        contentDescription = card.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        card.title,
                        color = BenflixColors.Text,
                        fontSize = BenflixType.body,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.Center).padding(10.dp),
                    )
                }
                card.scoreText?.let { Badge(it, Modifier.align(Alignment.TopEnd).padding(6.dp)) }
                if (rank != null) {
                    Badge("No. $rank", Modifier.align(Alignment.TopStart).padding(6.dp), light = true)
                } else if (card.isMovie) {
                    Badge("Movie", Modifier.align(Alignment.TopStart).padding(6.dp))
                }
                dub?.let { Badge(it, Modifier.align(Alignment.BottomStart).padding(6.dp)) }
                if (busy) {
                    Text(
                        "Opening…",
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
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .background(BenflixColors.Panel, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Text(
                card.title,
                color = BenflixColors.Text,
                fontSize = BenflixType.tag,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            (card.airingLine ?: card.year?.toString())?.let {
                Text(it, color = BenflixColors.Muted, fontSize = BenflixType.tag, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    light: Boolean = false,
) {
    Text(
        text = text,
        color = if (light) BenflixColors.Tray else Color.White,
        fontSize = BenflixType.tag,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier =
            modifier
                .background(if (light) BenflixColors.Text else Color.Black.copy(alpha = .75f), RoundedCornerShape(6.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** Find by feel: big colour tiles, one per feel, each with a short plain line */
@Composable
private fun FeelWall(
    feels: List<AnimeFeel>,
    isTv: Boolean,
    onPick: (AnimeFeel) -> Unit,
) {
    LazyVerticalGrid(
        columns = if (isTv) GridCells.Fixed(5) else GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "feel-head", span = { GridItemSpan(maxLineSpan) }) {
            Text("What are you in the mood for?", fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold, color = BenflixColors.Text)
        }
        items(feels, key = { "feel-${it.name}" }) { f ->
            val c = Color(0xFF000000 or f.color)
            val shape = RoundedCornerShape(12.dp)
            Surface(
                onClick = { onPick(f) },
                shape = ClickableSurfaceDefaults.shape(shape),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
                border =
                    com.github.damontecres.wholphin.ui.door
                        .doorFocusBorder(shape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier = Modifier.fillMaxWidth().height(if (isTv) 96.dp else 84.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(Brush.linearGradient(listOf(c, lerpDark(c)))),
                ) {
                    Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                        Text(f.name, color = Color.White, fontSize = BenflixType.lead, fontWeight = FontWeight.ExtraBold)
                        Text(f.line, color = Color.White.copy(alpha = .85f), fontSize = BenflixType.tag, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun lerpDark(c: Color) =
    Color(
        red = c.red * .35f + .04f * .65f,
        green = c.green * .35f + .05f * .65f,
        blue =
            c.blue * .35f + .06f * .65f,
    )

/** Mix and match tags: the deep search (Must have / Leave out), and quick picks that open it with one tag */
@Composable
private fun AnimeTagWall(
    catalog: List<AniListTagChoice>,
    viewModel: AnimeRequestsViewModel,
) {
    // Each button keeps its everyday name ("Harem") but opens AniList's real tag; ones AniList doesn't have are hidden
    val picks =
        remember(catalog) {
            ANIME_QUICK_PICKS.mapNotNull { label -> AniListService.resolveName(label, catalog)?.let { label to it } }
        }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(170.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "tags-head", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Mix and match", fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold, color = BenflixColors.Text)
                Text(
                    "Pick tags an anime must have, and ones to leave out. Every anime with all of them shows.",
                    fontSize = BenflixType.small,
                    color = BenflixColors.Muted,
                )
            }
        }
        item(key = "tags-open", span = { GridItemSpan(maxLineSpan) }) {
            RequestsChip(
                "Open mix and match: every genre and tag",
                true,
                { viewModel.navigationManager.navigateTo(Destination.AnimeGenres(emptyList())) },
            )
        }
        items(picks, key = { "tag-${it.first}" }) { (label, name) ->
            RequestsChip(
                label,
                false,
                { viewModel.navigationManager.navigateTo(Destination.AnimeGenres(listOf(name))) },
                Modifier.fillMaxWidth(),
            )
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

/**
 * This week: new episodes airing each day for shows not on Benflix, so you can ask while it's airing.
 * TV: one column per day. Phone: day chips and a list with Ask buttons.
 */
@Composable
private fun AnimeCalendar(
    isTv: Boolean,
    viewModel: AnimeRequestsViewModel,
    onAsk: (DiscoverItem) -> Unit,
) {
    val week by viewModel.week.collectAsState()
    val failed by viewModel.weekFailed.collectAsState()
    val owned by viewModel.owned.collectAsState()
    val opening by viewModel.opening.collectAsState()
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val days = remember(today) { (0..6).map { today.plusDays(it.toLong()) } }
    val byDay =
        remember(week, owned) {
            week
                .orEmpty()
                .filterNot { owned.has(it.card) }
                .map { Instant.ofEpochSecond(it.airingAt).atZone(zone).toLocalDate() to it }
                .filter { it.first in days }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, l) -> l.distinctBy { it.card.id }.sortedBy { it.airingAt } }
        }
    val hidden =
        remember(week, owned) {
            week
                .orEmpty()
                .filter { owned.has(it.card) }
                .distinctBy { it.card.id }
                .size
        }

    fun dayName(d: LocalDate) =
        when (d) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US) + " " + d.dayOfMonth
        }

    fun timeOf(a: AnimeAiringCard) =
        Instant
            .ofEpochSecond(a.airingAt)
            .atZone(zone)
            .format(TIME)
            .lowercase()
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("This week in anime", fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold, color = BenflixColors.Text)
            Text(
                listOfNotNull("New episodes by day, for shows you don't have yet", if (hidden > 0) "$hidden already on Benflix" else null)
                    .joinToString(" · "),
                fontSize = BenflixType.small,
                color = BenflixColors.Muted,
                maxLines = 2,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
            )
        }
        when {
            week == null -> {
                LoadingPage(Modifier.height(240.dp), focusEnabled = false)
            }

            failed -> {
                LoadFailed("The calendar didn't load. AniList may be busy.", onRetry = { viewModel.loadWeek(again = true) })
            }

            isTv -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(days, key = { "day-$it" }) { d ->
                        Column(Modifier.width(176.dp).fillParentMaxHeight()) {
                            Text(
                                dayName(d),
                                fontSize = BenflixType.body,
                                fontWeight = FontWeight.Bold,
                                color = if (d == today) BenflixColors.Text else BenflixColors.Soft,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                            val list = byDay[d].orEmpty()
                            if (list.isEmpty()) {
                                Text("Nothing new", fontSize = BenflixType.small, color = BenflixColors.Muted)
                            }
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                                items(list, key = { "ep-$d-${it.card.id}-${it.episode}" }) { a ->
                                    CalendarCard(a, timeOf(a), opening == a.card.id, onClick = { viewModel.open(a.card) })
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                var day by rememberSaveable { mutableStateOf(0) }
                val d = days[day.coerceIn(0, days.lastIndex)]
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    items(days.size, key = { "dchip-$it" }) { i ->
                        RequestsChip(dayName(days[i]), i == day, { day = i })
                    }
                }
                val list = byDay[d].orEmpty()
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (list.isEmpty()) {
                        item(key = "none") { Text("Nothing new that day.", fontSize = BenflixType.lead, color = BenflixColors.Muted) }
                    }
                    items(list, key = { "pep-${it.card.id}-${it.episode}" }) { a ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.weight(1f)) {
                                CalendarCard(a, timeOf(a), opening == a.card.id, onClick = { viewModel.open(a.card) })
                            }
                            Button(
                                onClick = { viewModel.open(a.card, onAsk) },
                                scale = ButtonDefaults.scale(focusedScale = 1f),
                                colors = ButtonDefaults.colors(containerColor = Color.White, contentColor = Color.Black),
                            ) { Text("Ask", maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
}

/** One new episode: a small poster, the name, and "Ep 7 · 9:30 pm" */
@Composable
private fun CalendarCard(
    a: AnimeAiringCard,
    time: String,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.Panel,
                focusedContainerColor = BenflixColors.Text,
                contentColor = BenflixColors.Text,
                focusedContentColor = BenflixColors.Tray,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(6.dp)) {
            AsyncImage(
                model = a.card.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp, 60.dp).clip(RoundedCornerShape(6.dp)).background(BenflixColors.Raised),
            )
            Column(Modifier.padding(start = 8.dp)) {
                Text(a.card.title, fontSize = BenflixType.tag, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (busy) "Opening…" else "Ep ${a.episode} · $time",
                    fontSize = BenflixType.tag,
                    maxLines = 1,
                )
                a.card.scoreText?.let { Text("AniList $it", fontSize = BenflixType.tag, maxLines = 1) }
            }
        }
    }
}
