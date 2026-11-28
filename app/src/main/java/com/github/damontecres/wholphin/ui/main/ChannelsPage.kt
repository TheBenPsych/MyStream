package com.github.damontecres.wholphin.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.BenflixTeams
import com.github.damontecres.wholphin.services.ChannelPrefs
import com.github.damontecres.wholphin.services.ChannelProgram
import com.github.damontecres.wholphin.services.ChannelReminder
import com.github.damontecres.wholphin.services.ChannelSchedule
import com.github.damontecres.wholphin.services.ChannelSession
import com.github.damontecres.wholphin.services.FAVOURITES_GROUP
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.group
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.BenflixChoice
import com.github.damontecres.wholphin.ui.components.BenflixChoiceDialog
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.LoadingShape
import com.github.damontecres.wholphin.ui.components.ProblemPage
import com.github.damontecres.wholphin.ui.door.DOOR_HOME
import com.github.damontecres.wholphin.ui.door.DoorFrame
import com.github.damontecres.wholphin.ui.door.DoorFrameState
import com.github.damontecres.wholphin.ui.door.DoorMemory
import com.github.damontecres.wholphin.ui.door.DoorSection
import com.github.damontecres.wholphin.ui.door.rememberDoorFrameState
import com.github.damontecres.wholphin.ui.nav.LocalTopBarOverlap
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixMotion
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.scrollAsOneBlock
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class ChannelsViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        /** null while loading */
        private val _channels = MutableStateFlow<List<ChannelSchedule>?>(null)
        val channels: StateFlow<List<ChannelSchedule>?> = _channels

        init {
            reload()
        }

        fun reload() {
            _channels.value = null
            viewModelScope.launch(WholphinDispatchers.IO) {
                _channels.value =
                    try {
                        ChannelSession.load(
                            api,
                            serverRepository.currentUser?.id,
                            KidsMode.isActive,
                            // The guide shows up as soon as the first channels are ready
                            onProgress = { if (it.isNotEmpty()) _channels.value = it },
                        )
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.e(ex, "Channels failed")
                        emptyList()
                    }
            }
        }

        fun watch(schedule: ChannelSchedule) = ChannelSession.tune(navigationManager, schedule)
    }

/** What a program is called in the guide: the show's name for an episode */
internal fun ChannelProgram.name(): String =
    if (noGuide) {
        "Live"
    } else if (item.type == BaseItemKind.EPISODE) {
        item.data.seriesName ?: item.title.orEmpty()
    } else {
        item.title.orEmpty()
    }

/** A row of the guide: a group heading, or a channel */
private sealed interface GuideEntry {
    data class Heading(
        val name: String,
    ) : GuideEntry

    data class Channel(
        val schedule: ChannelSchedule,
        /** A copy of a favourite at the top of the guide (it still shows in its own type too) */
        val favourite: Boolean = false,
    ) : GuideEntry
}

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

internal fun clock(ms: Long): String = timeFormat.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/**
 * Live TV (Glowfin L1): the top area tells you what's on the channel you're on, then Recently watched,
 * then the full 2-hour guide of the server's free live channels, favourites first, grouped by type.
 * Real live channels only (Ben, Nov 2026): the old home-made channels and "Make a channel" are gone.
 * Pick what's on now to jump in where the channel is; in the player, Up/Down changes channel.
 */
@Composable
fun ChannelsPage(
    modifier: Modifier = Modifier,
    viewModel: ChannelsViewModel = hiltViewModel(),
) {
    val channels by viewModel.channels.collectAsState()
    // Back in the guide means you're not flipping channels any more
    LaunchedEffect(Unit) { ChannelSession.stopWatching() }
    val list = channels
    // Opening from Home: the door colour waits for the channels, then fades onto the page
    LaunchedEffect(list != null) { if (list != null) DoorWipe.ready() }
    if (list == null) {
        LoadingPage(modifier.fillMaxSize(), shape = LoadingShape.GUIDE)
        return
    }
    if (list.isEmpty()) {
        // Design check (Ben 2026-11-24): the same friendly empty page as everywhere else, not a plain line of text
        ProblemPage(
            title = if (KidsMode.isActive) "Live TV isn't on for Kids" else "No live channels yet",
            line =
                if (KidsMode.isActive) {
                    "Ask a grown-up to switch profiles to watch Live TV."
                } else {
                    "Tower adds them when Live TV is set up. If it already is, try again in a moment."
                },
            icon = BenflixIcon.LIVE,
            modifier = modifier,
            onClick = if (KidsMode.isActive) null else viewModel::reload,
        )
        return
    }
    // Glowfin L1: one page. The top area describes what's focused; Recently watched comes
    // first, then the full guide. Ben (2026-11-16): no separate Favourites row, because the guide
    // already has it as its first group.
    // Hidden channel types ("Edit types") are left out of the guide.
    val hidden by ChannelPrefs.hidden.collectAsState()
    val shown = remember(list, hidden) { list.filter { it.group() !in hidden } }
    // Ben 2026-11-18: no slide-out menu any more. Live TV's places are word tabs along the top, like Movies:
    // Guide (the whole page, as before), Favourites (only your favourite channels) and Recently watched
    // (only the channels you watched lately, once there are some).
    val frame = rememberDoorFrameState(BenflixDoor.LIVE_TV)
    val recentKeys by ChannelPrefs.recent.collectAsState()
    val hasRecent = remember(recentKeys, list) { recentKeys.any { k -> list.any { it.key == k } } }
    val sections =
        remember(hasRecent) {
            listOfNotNull(
                DoorSection(DOOR_HOME, "Guide", BenflixIcon.CALENDAR),
                DoorSection(LIVE_FAVOURITES, "Favourites", BenflixIcon.STAR),
                // Idea Box 28 (Ben 2026-11-24): follow your teams, then what's on the sports channels now
                DoorSection(LIVE_SPORTS, "Sports", BenflixIcon.FLAG),
                if (hasRecent) DoorSection(LIVE_RECENT, "Recently watched", BenflixIcon.REFRESH) else null,
            )
        }
    val isTv = rememberIsTv()
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp || config.smallestScreenWidthDp >= 600
    // New look (Part 4): the art of the show on now (or the one you're on) fills the page behind everything
    val artPick = remember { mutableStateOf<LivePick?>(null) }
    val deep = remember { mutableStateOf(false) }
    DoorFrame(frame, sections, modifier, tabs = true, tabsInHeader = true) { inner ->
        // TV and wide screens: the tabs sit over the top of the page, so the page starts under them.
        // Phones: the usual side margins (the tabs already sit above the page)
        val placed =
            if (wide) {
                Modifier.padding(top = LocalTopBarOverlap.current)
            } else {
                Modifier.padding(start = BenflixSpace.phoneEdge, end = 8.dp)
            }
        BoxWithConstraints(modifier = inner) {
            val pageHeight = maxHeight
            if (wide) LiveArt(pick = { artPick.value }, deep = { deep.value }, pageHeight = pageHeight)
            // Each tab is its own page, so switching starts it at the top
            androidx.compose.runtime.key(frame.section) {
                if (frame.section == LIVE_SPORTS) {
                    SportsTab(
                        channels = list,
                        onWatch = { viewModel.watch(it) },
                        navigationManager = viewModel.navigationManager,
                        modifier = placed.padding(start = if (wide) liveEdges(isTv) else 0.dp),
                    )
                } else {
                    BoxWithConstraints(modifier = placed) {
                        // Two hours across on a TV, an hour and a half on a phone (less the page's own side margins)
                        val halfHours = if (maxWidth < 600.dp) 3L else GUIDE_HOURS * 2
                        val dpm = ((maxWidth - CHANNEL_COL - 24.dp - liveEdges(isTv)).value / (halfHours * 30f)).coerceAtLeast(1f)
                        GuideGrid(
                            all = list,
                            list = shown,
                            halfHours = halfHours,
                            dpm = dpm,
                            viewModel = viewModel,
                            frame = frame,
                            // Glowfin L1: an upright phone gets a simple Now / Next list instead of the grid
                            nowNext = maxWidth < 600.dp,
                            view = frame.section,
                            wideLook = wide && maxWidth >= 600.dp,
                            pageHeight = pageHeight,
                            artPick = artPick,
                            deep = deep,
                        )
                    }
                }
            }
        }
    }
}

/** Live TV's tabs besides Guide (the frame's own Home) */
private const val LIVE_FAVOURITES = "favourites"
private const val LIVE_RECENT = "recent"
internal const val LIVE_SPORTS = "sports"

/** The page's own left and right margins inside the door frame (TV: right next to the side menu, like Movies) */
private fun liveEdges(isTv: Boolean): Dp = if (isTv) BenflixSpace.tvEdge + 16.dp else 8.dp

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GuideGrid(
    all: List<ChannelSchedule>,
    list: List<ChannelSchedule>,
    halfHours: Long,
    dpm: Float,
    viewModel: ChannelsViewModel,
    frame: DoorFrameState,
    nowNext: Boolean = false,
    /** The tab: Guide (DOOR_HOME, the whole page), Favourites or Recently watched */
    view: String = DOOR_HOME,
    /** New look (TV and wide screens): what's on now big at the top, the art behind, a one-line top in the guide */
    wideLook: Boolean = false,
    pageHeight: Dp = 540.dp,
    artPick: androidx.compose.runtime.MutableState<LivePick?> = remember { mutableStateOf<LivePick?>(null) },
    deep: androidx.compose.runtime.MutableState<Boolean> = remember { mutableStateOf(false) },
) {
    val onlyFavourites = view == LIVE_FAVOURITES
    val onlyRecent = view == LIVE_RECENT
    // Picking a tab keeps the remote on the tabs (like Movies); only the first open lands in the guide
    val firstOpen = remember { frame.picks == 0 }

    fun widthFor(ms: Long): Dp = (ms / 60_000f * dpm).dp

    // The guide starts at the last half hour and shows the next few hours. The clock ticks each
    // minute, so "on now" and the red line keep up while the guide stays open
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            now = System.currentTimeMillis()
        }
    }
    val windowStart = now - now % HALF_HOUR
    val windowEnd = windowStart + halfHours * HALF_HOUR
    var focused by remember { mutableStateOf<Pair<ChannelSchedule, ChannelProgram>?>(null) }
    // Phones have no remote focus: the first tap on a program picks it (shown up top with a white
    // border) and a second tap on the same one plays or opens it
    val isTv = rememberIsTv()
    var selected by remember { mutableStateOf<Pair<ChannelSchedule, ChannelProgram>?>(null) }
    val firstFocus = remember { FocusRequester() }
    // Headings between the types (only when more than one shows, like in Favourites)
    val severalTypes = remember(list) { list.map { it.group() }.distinct().size > 1 }
    val favourites by ChannelPrefs.favourites.collectAsState()
    val listState = rememberLazyListState()
    val byKey = remember(all) { all.associateBy { it.key } }
    val favouriteChannels = remember(favourites, byKey) { favourites.mapNotNull { byKey[it] } }
    val recentKeys by ChannelPrefs.recent.collectAsState()
    val recentChannels = remember(recentKeys, byKey) { recentKeys.mapNotNull { byKey[it] } }
    // Favourites are the guide's first group, then every type as before.
    // The Favourites and Recently watched tabs show only those channels, with no headings.
    val entries =
        remember(list, favouriteChannels, recentChannels, view) {
            buildList<GuideEntry> {
                if (onlyFavourites) {
                    favouriteChannels.forEach { add(GuideEntry.Channel(it, favourite = true)) }
                    return@buildList
                }
                if (onlyRecent) {
                    recentChannels.forEach { add(GuideEntry.Channel(it)) }
                    return@buildList
                }
                if (favouriteChannels.isNotEmpty()) {
                    add(GuideEntry.Heading(FAVOURITES_GROUP))
                    favouriteChannels.forEach { add(GuideEntry.Channel(it, favourite = true)) }
                }
                var last: String? = null
                list.forEach { schedule ->
                    val group = schedule.group()
                    if ((severalTypes || favouriteChannels.isNotEmpty()) && group != last) add(GuideEntry.Heading(group))
                    last = group
                    add(GuideEntry.Channel(schedule))
                }
            }
        }
    val firstEntry = remember(entries) { entries.firstOrNull { it is GuideEntry.Channel } }
    // Glowfin L1: the type chips beside "Guide" jump to that type's first channel
    val groupStarts =
        remember(entries) {
            entries
                .mapIndexedNotNull { i, e -> if (e is GuideEntry.Heading) e.name to i else null }
                .mapNotNull { (name, i) -> (entries.getOrNull(i + 1) as? GuideEntry.Channel)?.let { Triple(name, i, it) } }
        }
    val groupFocus = remember(groupStarts) { groupStarts.associate { (_, _, entry) -> entry to FocusRequester() } }
    var pickedGroup by remember { mutableStateOf<String?>(null) }
    val onRowFocus = { schedule: ChannelSchedule ->
        schedule.programAt(System.currentTimeMillis())?.let { focused = schedule to it }
    }
    var digits by remember { mutableStateOf("") }
    var showTypes by remember { mutableStateOf(false) }
    var addChannels by remember { mutableStateOf(false) }
    var favouriteMenu by remember { mutableStateOf<ChannelSchedule?>(null) }
    var laterMenu by remember { mutableStateOf<Pair<ChannelSchedule, ChannelProgram>?>(null) }
    var channelPage by remember { mutableStateOf<ChannelSchedule?>(null) }
    val reminders by ChannelPrefs.reminders.collectAsState()
    // Number buttons anywhere on the page: wait a moment for more digits, then turn that channel on
    LaunchedEffect(digits) {
        if (digits.isEmpty()) return@LaunchedEffect
        delay(NUMBER_WAIT_MS)
        val number = digits.toIntOrNull()
        digits = ""
        list.firstOrNull { it.number == number }?.let(viewModel::watch)
    }
    val rowFocus = remember { FocusRequester() }
    var declinedNow by remember { mutableStateOf(emptySet<String>()) }
    val suggested =
        remember(recentKeys, favourites, byKey, declinedNow) {
            ChannelPrefs.suggestion(recentKeys)?.takeIf { it !in declinedNow }?.let { byKey[it] }
        }
    // New look: the Guide tab opens on what's on now, big (Ben: big events and sports first). It's worked out
    // again each minute, and stays put while you're on it.
    val heroShown = wideLook && view == DOOR_HOME
    val featured =
        remember(heroShown, list, favourites, now) {
            if (heroShown) pickLiveNow(list, favourites, recentKeys, now) else null
        }
    val heroFocus = remember { FocusRequester() }
    val heroIndex = if (heroShown && featured != null) 0 else -1
    // The Recently watched row only shows on the Guide tab (its own tab lists those channels instead). In the new
    // look the guide peeks right under what's on now, so it's left to its own tab there.
    val showRecent = view == DOOR_HOME && recentChannels.isNotEmpty() && heroIndex < 0
    val addFocus = remember { FocusRequester() }
    val recentIndex = if (showRecent) heroIndex + 1 else heroIndex
    // The guide's heading comes after the Recently watched row (when there is one), then its time ruler
    val titleIndex = recentIndex + 1
    val scope = rememberCoroutineScope()

    fun focusFirst() {
        when {
            heroIndex == 0 && heroFocus.tryRequestFocus() -> Unit
            onlyFavourites && favouriteChannels.isEmpty() -> addFocus.tryRequestFocus()
            favouriteChannels.isNotEmpty() || onlyRecent -> firstFocus.tryRequestFocus()
            !rowFocus.tryRequestFocus() -> firstFocus.tryRequestFocus()
        }
    }
    frame.toTop = {
        scope.launch {
            listState.animateScrollToItem(0)
            delay(JUMP_SETTLE_MS)
            focusFirst()
        }
    }
    LaunchedEffect(Unit) { snapshotFlow { listState.firstVisibleItemIndex == 0 }.collect { frame.atTop = it } }
    DisposableEffect(Unit) {
        onDispose {
            DoorMemory.keep(
                BenflixDoor.LIVE_TV,
                DoorMemory.Spot(
                    section = view,
                    scroll = listState.firstVisibleItemIndex,
                    scrollOffset = listState.firstVisibleItemScrollOffset,
                ),
            )
        }
    }
    LaunchedEffect(Unit) {
        // A tab picked from the tabs keeps the remote on the tabs (Down goes into the page)
        if (!firstOpen) return@LaunchedEffect
        delay(80)
        // Opening Live TV again within 10 minutes comes back to the same tab and part of the page
        val spot = DoorMemory.recall(BenflixDoor.LIVE_TV)?.takeIf { it.scroll > 0 && it.section == view }
        if (spot != null) {
            listState.scrollToItem(spot.scroll, spot.scrollOffset)
            delay(JUMP_SETTLE_MS)
            // Deep in the guide, keep the spot: the remote goes to the first channel on screen
            if (spot.scroll <= titleIndex) focusFirst()
            return@LaunchedEffect
        }
        // Ben: opens on the first favourite (the top of the guide), else the first recent
        // channel, else the first channel in the guide
        listState.scrollToItem(0)
        focusFirst()
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier
                .padding(top = 12.dp, start = if (isTv) BenflixSpace.tvEdge else 8.dp, end = if (isTv) 16.dp else 0.dp)
                .onPreviewKeyEvent { event ->
                    val digit = digitOf(event) ?: return@onPreviewKeyEvent false
                    if (addChannels) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyUp && digits.length < 4) digits += digit
                    true
                },
    ) {
        // What the focused channel or program is. Read only inside, so moving around redraws just this part
        // Before a channel has had focus (or on "+ Add channels"), show what's on the first
        // favourite (or the first channel) instead of an empty grey box
        val fallback = {
            (favouriteChannels.firstOrNull() ?: list.firstOrNull())?.let { s -> s.programAt(now)?.let { s to it } }
        }
        // New look: on what's on now there's no top (the big words are the list's first part); in the full guide the
        // top is one short line, and the art behind is turned down
        val atHero by remember(heroIndex) {
            androidx.compose.runtime.derivedStateOf { heroIndex == 0 && listState.firstVisibleItemIndex == 0 }
        }
        LaunchedEffect(wideLook, atHero, featured, focused, selected) {
            if (!wideLook) return@LaunchedEffect
            artPick.value = if (atHero) featured else (if (isTv) focused else selected ?: focused) ?: fallback()
            deep.value = !atHero
        }
        if (!wideLook) {
            GuideHeader(focused = { (if (isTv) focused else selected ?: focused) ?: fallback() }, now = now)
        } else if (!atHero) {
            LiveMiniLine(pick = { (if (isTv) focused else selected ?: focused) ?: fallback() }, now = now)
        }
        // Down (or Full guide) from what's on now: the full guide, on its first channel
        val openGuide = {
            scope.launch {
                listState.animateScrollToItem(titleIndex)
                delay(JUMP_SETTLE_MS)
                if (favouriteChannels.isNotEmpty() || !rowFocus.tryRequestFocus()) firstFocus.tryRequestFocus()
            }
            Unit
        }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // New look: what's on now, big, with the guide peeking under it
            if (heroIndex == 0 && featured != null) {
                item(key = "live-now") {
                    LiveNowHero(
                        pick = featured,
                        now = now,
                        starred = featured.first.key in favourites,
                        // Tall enough that the guide's first row or so peeks at the bottom
                        height = (pageHeight - LocalTopBarOverlap.current) * HERO_SHARE,
                        onWatch = { viewModel.watch(featured.first) },
                        onGuide = openGuide,
                        onFavourite = { ChannelPrefs.toggleFavourite(featured.first.key) },
                        onFocus = { focused = featured },
                        watchFocus = heroFocus,
                    )
                }
            }
            // Recently watched, then the full guide (favourites first)
            if (showRecent) {
                item(key = "row-recent") {
                    Box {
                        ChannelTileRow(title = "Recently watched") {
                            items(recentChannels, key = { "r-${it.key}" }) { schedule ->
                                RecentCard(
                                    schedule = schedule,
                                    width = if (isTv) 176.dp else 132.dp,
                                    onClick = { viewModel.watch(schedule) },
                                    onLongClick = { ChannelPrefs.toggleFavourite(schedule.key) },
                                    modifier =
                                        Modifier
                                            .then(if (schedule == recentChannels.first()) Modifier.focusRequester(rowFocus) else Modifier)
                                            .onFocusChanged { if (it.isFocused) onRowFocus(schedule) },
                                )
                            }
                        }
                    }
                }
            }
            // The full guide: "Guide", a chip for each type (favourites first), Edit types and Add channels,
            // then the time ruler that stays at the top
            item(key = "guide-title") {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        // Phones: the chips slide sideways rather than squash
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp).horizontalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text =
                                when {
                                    onlyFavourites -> "Favourites"
                                    onlyRecent -> "Recently watched"
                                    else -> "Guide"
                                },
                            fontFamily = GlowfinDisplayFont,
                            fontSize = BenflixType.rowName,
                            fontWeight = FontWeight.Bold,
                            color = Glowfin.Text,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        if (view == DOOR_HOME) {
                            groupStarts.forEach { (name, index, entry) ->
                                LiveChip(
                                    label = groupLabel(name),
                                    selected = pickedGroup == name,
                                    onClick = {
                                        pickedGroup = name
                                        scope.launch {
                                            listState.animateScrollToItem(titleIndex + 2 + index)
                                            delay(JUMP_SETTLE_MS)
                                            groupFocus[entry]?.tryRequestFocus()
                                        }
                                    },
                                )
                            }
                        }
                        if (view == DOOR_HOME) {
                            LiveChip("Edit types", selected = false, onClick = { showTypes = true })
                        }
                        LiveChip(
                            "+  Add channels",
                            selected = false,
                            onClick = { addChannels = true },
                            modifier = Modifier.focusRequester(addFocus),
                        )
                    }
                    // Watched a channel 3 times and it isn't a favourite yet: offer to add it
                    suggested?.let { schedule ->
                        Box(Modifier.padding(top = 8.dp)) {
                            SuggestFavouriteTile(
                                name = schedule.title,
                                onAdd = { ChannelPrefs.toggleFavourite(schedule.key) },
                                onNo = {
                                    ChannelPrefs.declineSuggestion(schedule.key)
                                    declinedNow = declinedNow + schedule.key
                                },
                            )
                        }
                    }
                }
            }
            if (entries.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text =
                            if (onlyFavourites) {
                                "No favourites yet. Hold OK on a channel (or press and hold on a phone) to add it, or pick + Add channels."
                            } else {
                                "No channels here yet."
                            },
                        fontSize = BenflixType.body,
                        color = Glowfin.Dim,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            stickyHeader(key = "ruler") {
                if (nowNext) {
                    NowNextHeader()
                    return@stickyHeader
                }
                Row(Modifier.background(BenflixColors.Tray).padding(vertical = 2.dp)) {
                    Box(Modifier.width(CHANNEL_COL))
                    var t = windowStart
                    while (t < windowEnd) {
                        Text(
                            text = clock(t),
                            fontSize = BenflixType.tag,
                            color = Glowfin.Dim,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(widthFor(HALF_HOUR)),
                        )
                        t += HALF_HOUR
                    }
                }
            }
            items(
                entries,
                key = {
                    when (it) {
                        is GuideEntry.Heading -> {
                            "h-${it.name}"
                        }

                        is GuideEntry.Channel -> {
                            (if (it.favourite) "fav-" else "") +
                                it.schedule.live.id
                                    .toString()
                        }
                    }
                },
            ) { entry ->
                if (entry is GuideEntry.Heading) {
                    GroupHeading(entry.name)
                    return@items
                }
                val schedule = (entry as GuideEntry.Channel).schedule
                val entryFocus =
                    Modifier
                        .then(if (entry === firstEntry) Modifier.focusRequester(firstFocus) else Modifier)
                        .then(groupFocus[entry]?.let { Modifier.focusRequester(it) } ?: Modifier)
                if (nowNext) {
                    NowNextRow(
                        schedule = schedule,
                        now = now,
                        starred = schedule.key in favourites,
                        onClick = { viewModel.watch(schedule) },
                        onLongClick = { channelPage = schedule },
                        modifier = entryFocus.onFocusChanged { if (it.isFocused) onRowFocus(schedule) },
                    )
                    return@items
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(ROW_HEIGHT),
                ) {
                    // Channel number, logo or badge, and the name. Press it for the channel's own page; hold to star it
                    val starred = schedule.key in favourites
                    Surface(
                        onClick = { channelPage = schedule },
                        onLongClick = { ChannelPrefs.toggleFavourite(schedule.key) },
                        shape = ClickableSurfaceDefaults.shape(BenflixShapes.Small),
                        colors =
                            ClickableSurfaceDefaults.colors(
                                containerColor = Color.Transparent,
                                contentColor = Glowfin.Dim,
                                focusedContainerColor = Glowfin.FocusRow,
                                focusedContentColor = Glowfin.Text,
                            ),
                        border =
                            com.github.damontecres.wholphin.ui.nav
                                .focusRing(BenflixShapes.Small),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        modifier = Modifier.width(CHANNEL_COL).fillMaxHeight(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                        ) {
                            // Glowfin L1: an aqua star for favourites, the number, the logo, the name
                            Text(
                                text = if (starred) "★" else " ",
                                fontSize = BenflixType.small,
                                color = StarGold,
                                modifier = Modifier.width(12.dp),
                            )
                            Text(
                                text = "${schedule.number}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = BenflixType.small,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(36.dp),
                            )
                            ChannelLogo(schedule, size = 38.dp, wide = true)
                            Text(
                                text = schedule.title,
                                fontSize = BenflixType.tag,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = BenflixType.tag * 1.3f,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Box(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.width(widthFor(windowEnd - windowStart)).fillMaxHeight()) {
                        val shownPrograms = remember(schedule, windowStart) { schedule.between(windowStart, windowEnd) }
                        shownPrograms.forEach { program ->
                            val from = maxOf(program.startMs, windowStart)
                            val to = minOf(program.endMs, windowEnd)
                            val live = now >= program.startMs && now < program.endMs
                            val isFirst = live
                            val isSelected =
                                !isTv &&
                                    selected?.let { (s, p) -> s.number == schedule.number && p.startMs == program.startMs } == true
                            Surface(
                                // Hold OK (or press and hold on a phone) to star the channel
                                onLongClick = { ChannelPrefs.toggleFavourite(schedule.key) },
                                onClick = {
                                    if (!isTv && !isSelected) {
                                        selected = schedule to program
                                    } else if (live) {
                                        viewModel.watch(schedule)
                                    } else {
                                        // A later show: Remind me, watch the channel now, or star it
                                        laterMenu = schedule to program
                                    }
                                },
                                shape = ClickableSurfaceDefaults.shape(BenflixShapes.Small),
                                // Glowfin L1: on now sits on the raised colour, later ones on the deep one;
                                // the remote's show turns white with an aqua ring
                                colors =
                                    ClickableSurfaceDefaults.colors(
                                        containerColor = if (live) Glowfin.Raise else Glowfin.Deep,
                                        contentColor = if (live) Glowfin.Text else Glowfin.Dim,
                                        focusedContainerColor = Color.White,
                                        focusedContentColor = Glowfin.Ink,
                                    ),
                                border =
                                    ClickableSurfaceDefaults.border(
                                        border =
                                            if (isSelected) {
                                                Border(BorderStroke(2.dp, Color.White), shape = BenflixShapes.Small)
                                            } else if (BenflixTeams.isMine(program.name())) {
                                                // Follow your teams: a game with one of your teams gets a gold edge
                                                Border(BorderStroke(1.5.dp, TEAM_GOLD), shape = BenflixShapes.Small)
                                            } else {
                                                Border.None
                                            },
                                        focusedBorder = Border(BorderStroke(2.5.dp, Glowfin.Aqua), shape = BenflixShapes.Small),
                                    ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                modifier =
                                    Modifier
                                        .offset(x = widthFor(from - windowStart))
                                        .width(widthFor(to - from) - 3.dp)
                                        .fillMaxHeight()
                                        .then(if (isFirst) entryFocus else Modifier)
                                        .onFocusChanged { if (it.isFocused) focused = schedule to program },
                            ) {
                                // On now: a thin glow line along the bottom up to where the show is
                                if (live && to > from) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxHeight()
                                                .wrapContentHeight(Alignment.Bottom)
                                                .fillMaxWidth(((now - from).toFloat() / (to - from)).coerceIn(0f, 1f))
                                                .height(3.dp)
                                                .background(Glowfin.GlowAcross),
                                    )
                                }
                                // A show that's nearly over is only a sliver: no squashed "Ki…" and cut-off time in it
                                if (widthFor(to - from) >= 52.dp) {
                                    Column(
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                    ) {
                                        val bell = reminders.any { it.key == schedule.key && it.startMs == program.startMs }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (bell) {
                                                com.github.damontecres.wholphin.ui.components.BenflixIconImage(
                                                    com.github.damontecres.wholphin.ui.components.BenflixIcon.BELL,
                                                    color = LocalContentColor.current,
                                                    size = 13.dp,
                                                    modifier = Modifier.padding(end = 4.dp),
                                                )
                                            }
                                            Text(
                                                text = program.name(),
                                                fontSize = BenflixType.small,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (!program.noGuide) {
                                            Text(
                                                text = clock(program.startMs),
                                                fontSize = BenflixType.tag,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // The "now" line (aqua)
                        Box(
                            modifier =
                                Modifier
                                    .offset(x = widthFor(now - windowStart))
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(Glowfin.Aqua),
                        )
                    }
                }
            }
        }
    }
    if (showTypes) {
        val groups = remember(all) { all.map { it.group() }.distinct() }
        val hiddenNow by ChannelPrefs.hidden.collectAsState()
        HideTypesDialog(groups = groups, hidden = hiddenNow) { showTypes = false }
    }
    if (addChannels) {
        AddChannelsDialog(list = all, favourites = favourites, onDismiss = { addChannels = false })
    }
    // Ben (2026-11-08): no "Pick your channels" pop-up when Live TV opens. Favourites are added
    // from a channel or the Add channels box instead.
    laterMenu?.let { (schedule, program) ->
        val reminded = reminders.any { it.key == schedule.key && it.startMs == program.startMs }
        val starred = schedule.key in favourites
        BenflixChoiceDialog(
            title = program.name(),
            subtitle = "${schedule.number}  ${schedule.title}  ·  ${clock(program.startMs)} – ${clock(program.endMs)}",
            choices =
                listOfNotNull(
                    if (KidsMode.isActive) {
                        null
                    } else {
                        BenflixChoice(
                            label = if (reminded) "Cancel the reminder" else "Remind me",
                            note = if (reminded) null else "A small note shows a minute before it starts",
                            onClick = { ChannelPrefs.toggleReminder(ChannelReminder(schedule.key, program.startMs, program.name())) },
                        )
                    },
                    if (KidsMode.isActive || program.noGuide) {
                        null
                    } else {
                        val every = ChannelPrefs.isEveryTime(schedule.key, program.name())
                        BenflixChoice(
                            label = if (every) "Stop reminding me every time" else "Remind me every time",
                            note = if (every) null else "Every time ${program.name()} is on this channel",
                            onClick = {
                                ChannelPrefs.toggleEveryTime(schedule.key, program.name())
                                ChannelPrefs.addEveryTime(ChannelSession.lineup)
                            },
                        )
                    },
                    BenflixChoice("Watch this channel now", { viewModel.watch(schedule) }),
                    BenflixChoice(
                        if (starred) "Remove from favourites" else "Add to favourites",
                        { ChannelPrefs.toggleFavourite(schedule.key) },
                    ),
                ),
            onDismissRequest = { laterMenu = null },
        )
    }
    channelPage?.let { schedule ->
        ChannelTitleSheet(
            schedule = schedule,
            now = now,
            onWatch = {
                channelPage = null
                viewModel.watch(schedule)
            },
            onDismiss = { channelPage = null },
        )
    }
    favouriteMenu?.let { schedule ->
        BenflixChoiceDialog(
            title = schedule.title,
            choices =
                listOf(
                    BenflixChoice("Move left", { ChannelPrefs.moveFavourite(schedule.key, -1) }),
                    BenflixChoice("Move right", { ChannelPrefs.moveFavourite(schedule.key, 1) }),
                    BenflixChoice("Remove from favourites", { ChannelPrefs.toggleFavourite(schedule.key) }),
                ),
            onDismissRequest = { favouriteMenu = null },
        )
    }
}

/** A titled row of channel tiles on the Live TV page */
@Composable
private fun ChannelTileRow(
    title: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp).scrollAsOneBlock()) {
        Text(
            text = title,
            fontSize = BenflixType.small,
            fontWeight = FontWeight.SemiBold,
            color = BenflixColors.Soft,
        )
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(end = 32.dp),
            content = content,
        )
    }
}

/** "Add <channel>?" with Add and No thanks, after a channel was watched 3 times */
@Composable
private fun SuggestFavouriteTile(
    name: String,
    onAdd: () -> Unit,
    onNo: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .height(64.dp)
                .background(Glowfin.Raise, BenflixShapes.Card)
                .padding(horizontal = 12.dp),
    ) {
        Text(
            "Add $name?",
            fontSize = BenflixType.small,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LiveChip("Add", selected = true, onClick = onAdd)
        LiveChip("No thanks", selected = false, onClick = onNo)
    }
}

/** Type a channel's name or number to add it to (or take it off) Favourites */
@Composable
private fun AddChannelsDialog(
    list: List<ChannelSchedule>,
    favourites: List<String>,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, list) { searchChannels(list, query).take(30) }
    com.github.damontecres.wholphin.ui.touch.Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .widthIn(max = 520.dp)
                    .background(BenflixColors.Panel, BenflixShapes.Panel)
                    .padding(18.dp),
        ) {
            Text(
                "Add channels",
                fontSize = BenflixType.rowName,
                fontWeight = FontWeight.ExtraBold,
                color = BenflixColors.Text,
            )
            Text(
                "Type a channel's name or number, then pick it.",
                fontSize = BenflixType.small,
                color = BenflixColors.Muted,
            )
            val box = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                delay(80)
                box.tryRequestFocus()
            }
            com.github.damontecres.wholphin.ui.components.SearchEditTextBox(
                value = query,
                onValueChange = { query = it },
                onSearchClick = {},
                modifier = Modifier.fillMaxWidth().focusRequester(box),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.height(320.dp),
            ) {
                items(results, key = { it.key }) { schedule ->
                    val starred = schedule.key in favourites
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChannelRow(
                            schedule,
                            onClick = { ChannelPrefs.toggleFavourite(schedule.key) },
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (starred) "★" else "☆",
                            fontSize = BenflixType.lead,
                            color = if (starred) StarGold else BenflixColors.Muted,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Glowfin L1: the aqua LIVE pill */
@Composable
internal fun LivePill(modifier: Modifier = Modifier) {
    Text(
        text = "● LIVE",
        fontSize = BenflixType.tag,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = .8.sp,
        color = Glowfin.Ink,
        modifier =
            modifier
                .background(Glowfin.Aqua, RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 1.dp),
    )
}

/**
 * Glowfin L1 top area: what's on the channel (or show) the remote is on. The words sit on the left;
 * the show's picture (or the station's logo) fills the right and fades into the page.
 */
@Composable
private fun GuideHeader(
    focused: () -> Pair<ChannelSchedule, ChannelProgram>?,
    now: Long,
) {
    val imageUrlService = LocalImageUrlService.current
    // Phones get a smaller picture and title
    val isTv = rememberIsTv()
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(if (isTv) HEADER_HEIGHT else PHONE_HEADER_HEIGHT),
    ) {
        val pair = focused()
        if (pair != null) {
            val (schedule, program) = pair
            // The guide loads its shows without picture info (to stay fast), so ask the server
            // for the show's own picture by its id, else the station's logo
            val urls =
                remember(program.item.id) {
                    fun url(itemId: java.util.UUID) =
                        imageUrlService.getItemImageUrl(
                            itemId = itemId,
                            imageType = ImageType.PRIMARY,
                            fillWidth = 480,
                            fillHeight = 270,
                        )
                    listOfNotNull(if (program.noGuide) null else url(program.item.id), url(schedule.live.id))
                }
            var attempt by remember(program.item.id) { mutableIntStateOf(0) }
            val isLogo = attempt == urls.lastIndex
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .aspectRatio(16f / 9f),
            ) {
                AsyncImage(
                    model = urls.getOrNull(attempt),
                    contentDescription = null,
                    contentScale = if (isLogo) ContentScale.Fit else ContentScale.Crop,
                    onError = { if (attempt < urls.lastIndex) attempt++ },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(if (isLogo) 28.dp else 0.dp),
                )
                // The fade into the page on the left and along the bottom
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(0f to BenflixColors.Tray, .55f to Color.Transparent)),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(.6f to Color.Transparent, 1f to BenflixColors.Tray)),
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(if (isTv) .56f else .62f),
        ) {
            if (pair == null) return@Column
            val (schedule, program) = pair
            val live = now >= program.startMs && now < program.endMs
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (live) LivePill()
                if (BenflixTeams.isMine(program.name())) YourTeamTag()
                Text(
                    text = "${schedule.number}  ·  ${schedule.title}",
                    fontSize = BenflixType.tag,
                    fontWeight = FontWeight.SemiBold,
                    color = Glowfin.Dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = program.name(),
                fontFamily = GlowfinDisplayFont,
                fontWeight = FontWeight.Bold,
                fontSize = if (isTv) BenflixType.pageTitle else BenflixType.lead,
                lineHeight = if (isTv) 32.sp else 22.sp,
                color = Glowfin.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!program.noGuide) {
                val next = schedule.programs.firstOrNull { it.startMs >= program.endMs && !it.noGuide }
                Text(
                    text =
                        "${clock(program.startMs)} – ${clock(program.endMs)}" +
                            (next?.let { "  ·  Next: ${it.name()}" } ?: ""),
                    fontSize = BenflixType.small,
                    color = BenflixColors.Soft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (program.item.type == BaseItemKind.EPISODE) {
                val season = program.item.data.parentIndexNumber
                val episode = program.item.data.indexNumber
                Text(
                    text =
                        listOfNotNull(
                            if (season != null && episode != null) "S$season E$episode" else null,
                            program.item.name,
                        ).joinToString("  ·  "),
                    fontSize = BenflixType.small,
                    fontWeight = FontWeight.SemiBold,
                    color = Glowfin.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            program.item.data.overview?.let {
                Text(
                    text = it,
                    fontSize = BenflixType.small,
                    color = BenflixColors.Soft,
                    maxLines = if (isTv) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (live && !program.noGuide) {
                val progress = ((now - program.startMs).toFloat() / (program.endMs - program.startMs)).coerceIn(0f, 1f)
                Box(
                    modifier =
                        Modifier
                            .padding(top = 4.dp)
                            .width(220.dp)
                            .height(4.dp)
                            .background(Color.White.copy(alpha = .14f), BenflixShapes.Small),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(Glowfin.GlowAcross, BenflixShapes.Small),
                    )
                }
            }
        }
    }
}

/** Glowfin L1 phones: the Now / Next list's column names (stays at the top while scrolling) */
@Composable
private fun NowNextHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(BenflixColors.Tray).padding(vertical = 4.dp),
    ) {
        Text("Channel", fontSize = BenflixType.tag, color = Glowfin.Dim, modifier = Modifier.width(NOW_NEXT_CHANNEL))
        Text("Now", fontSize = BenflixType.tag, color = Glowfin.Dim, modifier = Modifier.weight(1.3f))
        Text("Next", fontSize = BenflixType.tag, color = Glowfin.Dim, modifier = Modifier.weight(1f))
    }
}

/** Glowfin L1 phones: one channel in the Now / Next list. Tap to watch, hold for the channel's page */
@Composable
private fun NowNextRow(
    schedule: ChannelSchedule,
    now: Long,
    starred: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val on = schedule.programAt(now)
    val next = on?.let { current -> schedule.programs.firstOrNull { it.startMs >= current.endMs && !it.noGuide } }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = Glowfin.Text,
                focusedContainerColor = Glowfin.FocusRow,
                focusedContentColor = Glowfin.Text,
            ),
        border =
            com.github.damontecres.wholphin.ui.nav
                .focusRing(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.width(NOW_NEXT_CHANNEL),
            ) {
                ChannelLogo(schedule, size = 36.dp, wide = true)
                Text(
                    text = (if (starred) "★ " else "") + schedule.number,
                    fontSize = BenflixType.tag,
                    color = if (starred) StarGold else Glowfin.Dim,
                    maxLines = 1,
                )
            }
            Text(
                text = on?.name().orEmpty(),
                fontSize = BenflixType.small,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.3f).padding(end = 6.dp),
            )
            Text(
                text = next?.name().orEmpty(),
                fontSize = BenflixType.small,
                color = Glowfin.Dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 6.dp),
            )
        }
    }
}

private val NOW_NEXT_CHANNEL = 116.dp

/**
 * Glowfin L1 watching banner along the bottom after you change channel: the logo and number, the LIVE
 * pill, the channel's name (an aqua star if it's a favourite), what's on with a thin glow line, and
 * what's next. While the channel starts it says "Tuning in…"; it hides 5 seconds after the picture starts.
 */
@Composable
fun ChannelBanner(
    playing: Boolean,
    playingItemId: java.util.UUID?,
    modifier: Modifier = Modifier,
) {
    val rawBanner by ChannelSession.banner.collectAsState()
    // Only while the player is showing the channel that was tuned in. A banner left over from
    // Live TV must never show over a movie or episode started from somewhere else.
    val watching = ChannelSession.isWatching(playingItemId)
    // (Hidden, not cleared: while flipping channels the old player can still be on screen for a moment)
    val banner = rawBanner?.takeIf { watching }
    LaunchedEffect(banner, playing) {
        if (banner != null) {
            // If the channel never starts, don't leave "Tuning in…" up forever
            delay(if (playing) BANNER_MS else 20_000L)
            ChannelSession.clearBanner()
        }
    }
    val favourites by ChannelPrefs.favourites.collectAsState()
    val imageUrlService = LocalImageUrlService.current
    AnimatedVisibility(
        visible = banner != null,
        enter = fadeIn(BenflixMotion.enter()),
        exit = fadeOut(BenflixMotion.exit()),
        modifier = modifier,
    ) {
        val info = banner ?: return@AnimatedVisibility
        val now = remember(info) { System.currentTimeMillis() }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Glowfin.Ink.copy(alpha = .92f))))
                    .padding(start = BenflixSpace.tvOuterEdge, end = BenflixSpace.tvOuterEdge, top = 40.dp, bottom = 28.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val logo =
                    remember(info.channelId) {
                        imageUrlService.getItemImageUrl(itemId = info.channelId, imageType = ImageType.PRIMARY, maxWidth = 300)
                    }
                LogoPlate(
                    url = logo,
                    modifier = Modifier.width(108.dp).height(72.dp),
                    corner = 8.dp,
                    inset = 6.dp,
                )
                Text(info.number.toString(), fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold, color = Glowfin.Text)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LivePill()
                    Text(
                        info.name,
                        fontSize = BenflixType.small,
                        color = Glowfin.Dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (info.key in favourites) Text("★", fontSize = BenflixType.body, color = StarGold)
                }
                if (!playing) {
                    Text("Tuning in…", fontSize = BenflixType.lead, color = BenflixColors.Soft)
                } else if (info.now != null) {
                    val left = ((info.endMs - now) / 60_000L).coerceAtLeast(0L)
                    Text(
                        text = info.now,
                        fontFamily = GlowfinDisplayFont,
                        fontSize = BenflixType.pageTitle,
                        fontWeight = FontWeight.Bold,
                        color = Glowfin.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val length = (info.endMs - info.startMs).coerceAtLeast(1L)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(clock(info.startMs), fontSize = BenflixType.small, color = BenflixColors.Soft)
                        Box(
                            Modifier
                                .widthIn(max = 420.dp)
                                .weight(1f, fill = false)
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(BenflixShapes.Small)
                                .background(Color.White.copy(alpha = .2f)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(((now - info.startMs).toFloat() / length).coerceIn(0f, 1f))
                                    .height(4.dp)
                                    .background(Glowfin.GlowAcross),
                            )
                        }
                        Text(clock(info.endMs), fontSize = BenflixType.small, color = BenflixColors.Soft)
                        Text(
                            text = (if (left >= 60) "${left / 60} h ${left % 60} min" else "$left min") + " left",
                            fontSize = BenflixType.small,
                            color = Glowfin.Dim,
                            maxLines = 1,
                        )
                    }
                    info.next?.let { next ->
                        Text(
                            "Next at ${clock(info.nextStartMs)}  ·  $next",
                            fontSize = BenflixType.small,
                            color = Glowfin.Dim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private const val HALF_HOUR = 30L * 60_000L
private const val GUIDE_HOURS = 2L
private const val NUMBER_WAIT_MS = 1_500L
private const val BANNER_MS = 5_000L
private val CHANNEL_COL = 232.dp
private val HEADER_HEIGHT = 170.dp

/** New look: how much of the page (under the header) what's on now takes; the guide peeks under it */
private const val HERO_SHARE = .64f

/** On phones the header (and so its 16:9 picture) is shorter, leaving room for the title */
private val PHONE_HEADER_HEIGHT = 110.dp
private val BenflixRed get() = BenflixColors.LiveTv

private val ROW_HEIGHT = 56.dp

/** Glowfin L1: a type's name between the groups in the guide (an aqua star for Favourites) */
@Composable
private fun GroupHeading(name: String) {
    Text(
        text = if (name == FAVOURITES_GROUP) "★  Favourites" else name,
        fontSize = BenflixType.small,
        fontWeight = FontWeight.SemiBold,
        color = if (name == FAVOURITES_GROUP) StarGold else BenflixColors.Soft,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

/** How long a side-menu jump waits for the list to settle before focus moves */
private const val JUMP_SETTLE_MS = 90L
