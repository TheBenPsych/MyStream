package com.github.damontecres.wholphin.ui.discover

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
import com.github.damontecres.wholphin.services.AniListBrowseItem
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
import com.github.damontecres.wholphin.ui.components.GlowRanked
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.detail.discover.findAnimeInRequests
import com.github.damontecres.wholphin.ui.detail.discover.searchNameFor
import com.github.damontecres.wholphin.ui.detail.title.ScorePill
import com.github.damontecres.wholphin.ui.detail.title.StripPart
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont
import com.github.damontecres.wholphin.ui.util.ScrollToTopBringIntoViewSpec
import com.github.damontecres.wholphin.ui.util.scrollAsOneBlock
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import javax.inject.Singleton

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
    /** Benflix (2026-11-18): each show here, by "al:<AniList id>", "mal:<id>" and "n:<clean name>" */
    val shows: Map<String, OwnedShow> = emptyMap(),
) {
    fun has(card: AnimeCard): Boolean {
        if (card.id > 0 && card.id in ids) return true
        if (card.malId?.let { it in malIds } == true) return true
        val cleaned = card.titles.map { AniListService.clean(it) }.filter { it.isNotEmpty() }
        if (cleaned.any { it in names }) return true
        if (baseNames(card.titles).any { it in names }) return true
        // A new season of a show that's here ("Show Season 2") counts as here
        return card.later && cleaned.any { t -> names.any { n -> n.length >= 5 && t.startsWith(n) } }
    }

    /** The same check for a Mix and match tags poster (later seasons are already folded into their first) */
    fun has(item: AniListBrowseItem): Boolean {
        if (item.id > 0 && item.id in ids) return true
        if (item.titles.map { AniListService.clean(it) }.any { it.isNotEmpty() && it in names }) return true
        // "Show Season 2" when "Show" is here (Ben 2026-11-18: Slime Season 2 showed up though Slime is here)
        return baseNames(item.titles).any { it in names }
    }

    /** The show on Benflix this poster belongs to, when it's here */
    fun showFor(item: AniListBrowseItem): OwnedShow? {
        if (item.id > 0) shows["al:${item.id}"]?.let { return it }
        item.titles
            .map { AniListService.clean(it) }
            .firstNotNullOfOrNull { n -> n.takeIf { it.isNotEmpty() }?.let { shows["n:$it"] } }
            ?.let { return it }
        return baseNames(item.titles).firstNotNullOfOrNull { shows["n:$it"] }
    }

    private fun baseNames(titles: List<String>) =
        titles
            .mapNotNull { AniListService.seasonBase(it) }
            .map { AniListService.clean(it) }
            .filter { it.length >= 3 }
}

/** One show on Benflix: its Jellyfin id and TMDB number (null when Jellyfin doesn't know it) */
data class OwnedShow(
    val jellyfinId: java.util.UUID,
    val tmdb: Int?,
    val isSeries: Boolean,
)

/**
 * Benflix (Ben, 2026-11-16): what anime is already on Benflix, shared by the Anime tab and the Mix and match
 * tags page so neither shows what you already have. Looked up again after 10 minutes.
 */
@Singleton
class OwnedAnimeIndex
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val aniList: AniListService,
        private val aniListLibraries: AniListLibraries,
    ) {
        private val lock = Mutex()
        private var cached: Pair<Long, OwnedAnime>? = null

        suspend fun get(): OwnedAnime =
            lock.withLock {
                cached?.takeIf { System.currentTimeMillis() - it.first < 10 * 60_000 }?.let { return@withLock it.second }
                val found = load() ?: return@withLock cached?.second ?: OwnedAnime(emptySet(), emptySet(), emptySet())
                cached = System.currentTimeMillis() to found
                found
            }

        private suspend fun load(): OwnedAnime? =
            try {
                val userId = serverRepository.currentUser?.id ?: return null
                val libraries =
                    api.userViewsApi
                        .getUserViews(userId = userId)
                        .content.items
                        .filter { it.name?.contains("anime", ignoreCase = true) == true }
                        .map { it.id }
                val ids = mutableSetOf<Int>()
                val malIds = mutableSetOf<Int>()
                val names = mutableSetOf<String>()
                val shows = mutableMapOf<String, OwnedShow>()
                libraries.forEach { lib ->
                    val dtos = aniListLibraries.titlesIn(lib, userId)
                    val showOf =
                        dtos.associate { dto ->
                            dto.id to
                                OwnedShow(
                                    jellyfinId = dto.id,
                                    tmdb = dto.providerIds?.get("Tmdb")?.toIntOrNull(),
                                    isSeries = dto.type == org.jellyfin.sdk.model.api.BaseItemKind.SERIES,
                                )
                        }
                    aniList.knownIndex(lib).forEach { (jellyfinId, e) ->
                        if (e.id > 0) {
                            ids += e.id
                            showOf[jellyfinId]?.let { shows.putIfAbsent("al:${e.id}", it) }
                        }
                        e.malId?.takeIf { it > 0 }?.let { malIds += it }
                    }
                    dtos.forEach { dto ->
                        val show = showOf.getValue(dto.id)
                        listOfNotNull(dto.name, dto.originalTitle)
                            .map { AniListService.clean(it) }
                            .filter { it.isNotEmpty() }
                            .forEach {
                                names += it
                                shows.putIfAbsent("n:$it", show)
                            }
                        dto.providerIds
                            ?.get("AniList")
                            ?.toIntOrNull()
                            ?.let {
                                ids += it
                                shows.putIfAbsent("al:$it", show)
                            }
                        dto.providerIds
                            ?.get("MyAnimeList")
                            ?.toIntOrNull()
                            ?.let { malIds += it }
                    }
                }
                OwnedAnime(ids, malIds, names, shows)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Anime Requests: couldn't list what's already on Benflix")
                null
            }
    }

@HiltViewModel
class AnimeRequestsViewModel
    @Inject
    constructor(
        private val discover: AnimeDiscover,
        private val aniList: AniListService,
        private val ownedIndex: OwnedAnimeIndex,
        private val seerrService: SeerrService,
        private val helper: com.github.damontecres.wholphin.services.GameLibraryClient,
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
            _owned.value = ownedIndex.get()
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
        val trendingAsk get() = discover.trendingAsk
        val malTrendingAsk get() = discover.malTrendingAsk
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
                        findAnimeInRequests(
                            seerrService,
                            card.titles,
                            card.year,
                            card.isMovie,
                            anilistId = card.id.takeIf { it > 0 },
                            malId = card.idMal ?: (-card.id).takeIf { it > 0 },
                            helper = helper,
                        )
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not find %s in Requests", card.title)
                        null
                    }
                withContext(WholphinDispatchers.Main) {
                    _opening.value = null
                    if (found != null) {
                        if (then != null) then(found) else navigationManager.navigateTo(found.destination)
                    } else {
                        // Benflix (Ben, 2026-11-18): never a dead end. Search shows it with everything to ask for
                        navigationManager.navigateTo(Destination.Search(searchNameFor(card.titles)))
                    }
                }
            }
        }
    }

/** One row on the Anime tab */
private class AnimeRowSpec(
    val key: String,
    val title: String,
    val note: String?,
    val ask: AnimeDiscover.Ask,
    val ranked: Boolean = false,
    val keep: ((AnimeCard) -> Boolean)? = null,
) {
    fun copyKeep(keep: (AnimeCard) -> Boolean) = AnimeRowSpec(key, title, note, ask, ranked, keep)
}

/**
 * Benflix: the Requests > Anime tab. Glowfin new look (Ben approved 2026-11-22): it lands straight on a row, like
 * Movies and Shows. On TV the art of the anime you're on (its AniList banner) fills the page and the top describes
 * it. Rows: This season, Top 10 this week, This week in anime, Trending on AniList, Trending on MyAnimeList (Ben
 * asked for both), a Moods pill row with the picked mood's row under it, Mix and match tags, Finished and dubbed,
 * Hidden gems, Anime movies, Coming next season and Last season. Shows already on Glowfin are left out.
 */
@Composable
internal fun AnimeShelf(
    isTv: Boolean,
    onAsk: (DiscoverItem) -> Unit,
    onMenu: (DiscoverItem) -> Unit,
    modifier: Modifier = Modifier,
    top: Dp = 0.dp,
    viewModel: AnimeRequestsViewModel = hiltViewModel(),
) {
    val nowSeason = remember { AnimeSeasonPick.now() }
    val catalog by viewModel.catalog.collectAsState()
    val dubState by viewModel.dubs.collectAsState()
    val week by viewModel.week.collectAsState()
    val owned by viewModel.owned.collectAsState()
    val lists by viewModel.lists.collectAsState()
    val feels =
        remember(catalog) {
            ANIME_FEELS.filter { f -> catalog.isEmpty() || f.tags.all { AniListService.resolveName(it, catalog) != null } }
        }
    var feelName by rememberSaveable { mutableStateOf<String?>(null) }
    val feel = feels.firstOrNull { it.name == feelName } ?: feels.firstOrNull()
    val quickPicks =
        remember(catalog) {
            ANIME_QUICK_PICKS.mapNotNull { label -> AniListService.resolveName(label, catalog)?.let { label to it } }
        }
    LaunchedEffect(Unit) { viewModel.loadWeek() }
    val dubs = dubState
    val firstRows =
        remember(nowSeason) {
            listOf(
                AnimeRowSpec("season", "This season", "${nowSeason.label} · airing now", viewModel.seasonAsk(nowSeason)),
                AnimeRowSpec("top", "Top 10 this week", "What anime fans are watching most", viewModel.topAsk, ranked = true),
            )
        }
    val trendRows =
        remember {
            listOf(
                AnimeRowSpec("tr-al", "Trending on AniList", "Right after the Top 10", viewModel.trendingAsk),
                AnimeRowSpec("tr-mal", "Trending on MyAnimeList", "Top anime airing now", viewModel.malTrendingAsk),
            )
        }
    val laterRows =
        remember(nowSeason, dubs) {
            listOfNotNull(
                if (!dubs.isNullOrEmpty()) {
                    AnimeRowSpec(
                        "dubbed",
                        "Finished and dubbed",
                        "Every episode out, with English voices",
                        viewModel.finishedAsk,
                        keep = { c -> c.malId?.let { it in dubs } == true },
                    )
                } else {
                    null
                },
                AnimeRowSpec("gems", "Hidden gems", "Scores of 8 and up that few people have found", viewModel.gemsAsk),
                AnimeRowSpec("movies", "Anime movies", null, viewModel.moviesAsk),
                AnimeRowSpec("next", "Coming next season", nowSeason.next().label, viewModel.nextAsk()),
                AnimeRowSpec("last", "Last season", nowSeason.previous().label, viewModel.seasonAsk(nowSeason.previous())),
            )
        }
    val feelRow = feel?.let { AnimeRowSpec("feel-${it.name}", "Mood: ${it.name}", it.recipe, viewModel.feelAsk(it)) }

    // The anime the top describes: follows focus once you've stopped on a cover for a moment
    var focused by remember { mutableStateOf<AnimeCard?>(null) }
    var shown by remember { mutableStateOf<AnimeCard?>(null) }
    LaunchedEffect(focused) {
        val target = focused ?: return@LaunchedEffect
        if (shown != null) delay(SHOW_DELAY_MS)
        shown = target
    }
    val firstCard =
        lists[firstRows.first().ask.key]
            ?.items
            ?.firstOrNull { !owned.has(it) }
    val described = shown ?: firstCard
    // Trending on AniList leaves out what the Top 10 already shows
    val topIds =
        remember(lists, owned) {
            lists[firstRows[1].ask.key]
                ?.items
                ?.filterNot { owned.has(it) }
                ?.take(10)
                ?.map { it.id }
                ?.toSet()
                .orEmpty()
        }
    val onFocus: (AnimeCard) -> Unit = { focused = it }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val topHeight =
            (maxHeight * ANIME_TOP_SHARE).coerceIn(
                top + ANIME_TOP_MIN,
                (maxHeight - ANIME_ROW_ROOM).coerceAtLeast(top + ANIME_TOP_MIN),
            )
        if (isTv) {
            RequestArtUrl(described?.let { it.banner ?: it.cover })
            described?.score?.let { score ->
                ScorePill(
                    rating = null,
                    scores = listOf(StripPart("anilist", "$score%")),
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = top + 2.dp),
                )
            }
        }
        Column(Modifier.fillMaxSize().padding(top = if (isTv) 0.dp else top)) {
            if (isTv) {
                Box(Modifier.fillMaxWidth().height(topHeight)) {
                    described?.let { card ->
                        RequestTopText(
                            title = card.title,
                            facts =
                                listOfNotNull(
                                    if (card.isMovie) "Anime movie" else "Anime",
                                    card.year?.toString(),
                                    card.episodes?.takeIf { it > 0 && !card.isMovie }?.let { "$it episodes" },
                                    dubWord(card, dubs),
                                ).joinToString(" · "),
                            tags =
                                listOfNotNull(
                                    RqTag(RqTagKind.NOT_YET, "Not on Glowfin yet"),
                                    card.airingLine?.let { RqTag(RqTagKind.ON_WAY, "Airing now · $it") },
                                ),
                            story = card.story,
                            hint = ASK_HINT,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = top + 2.dp),
                        )
                    }
                }
            }
            val defaultSpec = LocalBringIntoViewSpec.current
            val toTop = remember { ScrollToTopBringIntoViewSpec(0f) }
            CompositionLocalProvider(LocalBringIntoViewSpec provides if (isTv) toTop else defaultSpec) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(if (isTv) 18.dp else 14.dp),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 48.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    fun rows(list: List<AnimeRowSpec>) {
                        items(list, key = { "anrow-${it.key}" }) { spec ->
                            CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                                AnimeRow(spec, isTv, viewModel, onMenu, onFocus)
                            }
                        }
                    }
                    rows(firstRows)
                    item(key = "anrow-week") {
                        CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                            AnimeWeekRow(week, isTv, viewModel, onAsk, onFocus)
                        }
                    }
                    val skipTop: (AnimeCard) -> Boolean = { it.id !in topIds }
                    rows(listOf(trendRows[0].copyKeep(skipTop), trendRows[1]))
                    if (feels.isNotEmpty()) {
                        item(key = "anrow-moods") {
                            CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                                RequestChipRow("Moods") {
                                    items(feels, key = { "mood-${it.name}" }) { f ->
                                        RequestsChip(f.name, f.name == feel?.name, { feelName = f.name })
                                    }
                                }
                            }
                        }
                    }
                    feelRow?.let { rows(listOf(it)) }
                    item(key = "anrow-tags") {
                        CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                            RequestChipRow("Mix and match tags") {
                                item(key = "tags-all") {
                                    RequestsChip(
                                        "Every genre and tag ›",
                                        true,
                                        { viewModel.navigationManager.navigateTo(Destination.AnimeGenres(emptyList())) },
                                    )
                                }
                                items(quickPicks, key = { "tag-${it.first}" }) { (label, name) ->
                                    RequestsChip(
                                        label,
                                        false,
                                        { viewModel.navigationManager.navigateTo(Destination.AnimeGenres(listOf(name))) },
                                    )
                                }
                            }
                        }
                    }
                    rows(laterRows)
                }
            }
        }
    }
}

/** One anime row: its name (with how many are already on Glowfin) and the covers, loaded when it comes on screen */
@Composable
private fun AnimeRow(
    spec: AnimeRowSpec,
    isTv: Boolean,
    viewModel: AnimeRequestsViewModel,
    onMenu: (DiscoverItem) -> Unit,
    onFocus: (AnimeCard) -> Unit,
) {
    LaunchedEffect(spec.ask.key) { viewModel.load(spec.ask) }
    val lists by viewModel.lists.collectAsState()
    val owned by viewModel.owned.collectAsState()
    val dubs by viewModel.dubs.collectAsState()
    val opening by viewModel.opening.collectAsState()
    val state = lists[spec.ask.key]
    val all = state?.items?.let { l -> spec.keep?.let { l.filter(it) } ?: l }
    val shown = remember(all, owned) { all?.filterNot { owned.has(it) } }
    val here = (all?.size ?: 0) - (shown?.size ?: 0)
    val line = listOfNotNull(spec.note, if (here > 0) "$here already on Glowfin" else null).joinToString(" · ")
    val width = if (isTv) 120.dp else 104.dp
    Column(Modifier.fillMaxWidth().scrollAsOneBlock()) {
        AnimeRowName(spec.title, line)
        when {
            shown == null -> {
                Box(Modifier.fillMaxWidth().height(width * 1.5f + 40.dp)) {
                    LoadingPage(Modifier.fillMaxSize(), focusEnabled = false)
                }
            }

            state?.failed == true && shown.isEmpty() -> {
                LoadFailed("These didn't load. AniList may be busy.", onRetry = { viewModel.load(spec.ask, again = true) })
            }

            shown.isEmpty() -> {
                Text(
                    if (here > 0) "You already have all of these on Glowfin." else "Nothing here right now.",
                    fontSize = BenflixType.body,
                    color = BenflixColors.Muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            else -> {
                val list = shown.distinctBy { it.id }.let { if (spec.ranked) it.take(10) else it }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    itemsIndexed(list, key = { _, c -> "an-${spec.key}-${c.id}" }) { index, card ->
                        val poster: @Composable () -> Unit = {
                            AnimeCardPoster(
                                card = card,
                                width = width,
                                rank = null,
                                dub = dubWord(card, dubs),
                                busy = opening == card.id,
                                onClick = { viewModel.open(card) },
                                onLongClick = { viewModel.open(card, onMenu) },
                                onFocus = { onFocus(card) },
                            )
                        }
                        // Glowfin Top 10 rule: each number fully beside its poster
                        if (spec.ranked) GlowRanked(rank = index + 1, posterHeight = width * 1.5f) { poster() } else poster()
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeRowName(
    title: String,
    line: String,
) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        Text(
            title,
            fontFamily = GlowfinDisplayFont,
            fontSize = BenflixType.lead,
            fontWeight = FontWeight.Bold,
            color = BenflixColors.Text,
            maxLines = 1,
        )
        if (line.isNotEmpty()) {
            Text(
                line,
                fontSize = BenflixType.small,
                color = BenflixColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
            )
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

/**
 * This week in anime: the next new episodes for shows not on Glowfin, in the order they air, each cover saying
 * "Today 9:30 pm · Ep 7". Phones get a small Ask under each.
 */
@Composable
private fun AnimeWeekRow(
    week: List<AnimeAiringCard>?,
    isTv: Boolean,
    viewModel: AnimeRequestsViewModel,
    onAsk: (DiscoverItem) -> Unit,
    onFocus: (AnimeCard) -> Unit,
) {
    val failed by viewModel.weekFailed.collectAsState()
    val owned by viewModel.owned.collectAsState()
    val dubs by viewModel.dubs.collectAsState()
    val opening by viewModel.opening.collectAsState()
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val now = remember { Instant.now().epochSecond }
    val list =
        remember(week, owned) {
            week
                .orEmpty()
                .filterNot { owned.has(it.card) }
                .filter { it.airingAt >= now - 6 * 3600 }
                .sortedBy { it.airingAt }
                .distinctBy { it.card.id }
        }

    fun whenOf(a: AnimeAiringCard): String {
        val at = Instant.ofEpochSecond(a.airingAt).atZone(zone)
        val d = at.toLocalDate()
        val day =
            when (d) {
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                else -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
            }
        return "$day ${at.format(TIME).lowercase()} · Ep ${a.episode}"
    }
    val width = if (isTv) 120.dp else 104.dp
    Column(Modifier.fillMaxWidth().scrollAsOneBlock()) {
        AnimeRowName("This week in anime", "New episodes, for shows you don't have yet")
        when {
            week == null -> {
                Box(Modifier.fillMaxWidth().height(width * 1.5f + 40.dp)) {
                    LoadingPage(Modifier.fillMaxSize(), focusEnabled = false)
                }
            }

            failed -> {
                LoadFailed("The calendar didn't load. AniList may be busy.", onRetry = { viewModel.loadWeek(again = true) })
            }

            list.isEmpty() -> {
                Text(
                    "Nothing new this week.",
                    fontSize = BenflixType.body,
                    color = BenflixColors.Muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            else -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    items(list, key = { "wk-${it.card.id}-${it.episode}" }) { a ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AnimeCardPoster(
                                card = a.card,
                                width = width,
                                rank = null,
                                dub = dubWord(a.card, dubs),
                                busy = opening == a.card.id,
                                onClick = { viewModel.open(a.card) },
                                onLongClick = { viewModel.open(a.card, onAsk) },
                                onFocus = { onFocus(a.card) },
                                caption = whenOf(a),
                            )
                            if (!isTv) {
                                Button(
                                    onClick = { viewModel.open(a.card, onAsk) },
                                    scale = ButtonDefaults.scale(focusedScale = 1f),
                                    colors = ButtonDefaults.colors(containerColor = Color.White, contentColor = Color.Black),
                                    modifier = Modifier.width(width),
                                ) { Text("Ask", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The TV top area's share of the screen, like Movies and Shows */
private const val ANIME_TOP_SHARE = 0.52f
private val ANIME_TOP_MIN = 116.dp
private val ANIME_ROW_ROOM = 236.dp
private const val SHOW_DELAY_MS = 450L

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
    onFocus: (() -> Unit)? = null,
    /** Words under the name in place of the year ("Today 9:30 pm · Ep 7") */
    caption: String? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.width(width).then(if (onFocus != null) Modifier.onFocusChanged { if (it.hasFocus) onFocus() } else Modifier)) {
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
            (caption ?: card.airingLine ?: card.year?.toString())?.let {
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
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .background(if (light) BenflixColors.Text else Color.Black.copy(alpha = .75f), RoundedCornerShape(6.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
