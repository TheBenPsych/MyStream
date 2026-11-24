package com.github.damontecres.wholphin.ui.door

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.EmptyPage
import com.github.damontecres.wholphin.ui.main.DoorWipe
import com.github.damontecres.wholphin.ui.music.MusicButton
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.LocalTopBarOverlap
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.FocusStripesRoom
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.ScrollToTopBringIntoViewSpec
import com.github.damontecres.wholphin.ui.util.onFirstUserMove
import com.github.damontecres.wholphin.ui.util.scrollAsOneBlock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * Benflix redesign: the inside of the Movies and Shows doors (approved mv-preview + pv-fade).
 * TV: the top area describes the poster you're on, with its picture fading in from the right;
 * rows of posters below. Phones: a "Picked for you" card, then the rows.
 * Redesign 2 (section 6): the door sits in the shared [DoorFrame]. Its side menu has every section
 * (All movies, Genres, Collections, Studios, My List, Anime…), so the old "Browse by" tiles are gone.
 * Kids and Easy read: the same, bigger, fewer rows and sections, no scores.
 */
@Composable
fun DoorPage(
    door: BenflixDoor,
    preferences: UserPreferences,
    onClearBackdrop: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DoorViewModel = hiltViewModel(key = "door-${door.name}"),
) {
    LaunchedEffect(Unit) { onClearBackdrop() }
    LifecycleStartEffect(door) {
        viewModel.start(door)
        onStopOrDispose { }
    }
    val state by viewModel.state.collectAsState()
    // Opening from Home: the door colour waits for the top row (or the empty message), then fades onto the page
    val firstReady = state.loaded || state.entries().firstOrNull() is DoorEntry.Real
    LaunchedEffect(firstReady) {
        if (firstReady) DoorWipe.ready()
    }
    val kids by KidsMode.active.collectAsState()
    val easy = kids
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp || config.smallestScreenWidthDp >= 600
    val onClick: (BaseItem) -> Unit = { viewModel.navigationManager.navigateTo(it.destination()) }
    val onSeeAll: (DoorRowData) -> Unit = { row ->
        viewModel.navigationManager.navigateTo(Destination.DoorRowGrid(door.name, row.key, row.title))
    }
    val frame = rememberDoorFrameState(door)
    // Ben: a door opened again within 10 minutes comes back on the row and card you were on
    remember(viewModel) {
        if (!viewModel.userMoved) {
            DoorMemory.recall(door)?.row?.let { row ->
                viewModel.focusedRow = row
                viewModel.focusedIndex = DoorMemory.recall(door)?.index ?: 0
                viewModel.userMoved = true
            }
        }
        true
    }
    DisposableEffect(door) {
        onDispose {
            DoorMemory.keep(
                door,
                DoorMemory.Spot(section = frame.section, row = viewModel.focusedRow, index = viewModel.focusedIndex),
            )
        }
    }
    val sections = remember(door, easy) { doorSections(door, easy) }

    if (state.loaded && state.rows.isEmpty()) {
        EmptyDoor(door.title, state.offline, viewModel::retry, modifier.padding(top = LocalTopBarOverlap.current))
        return
    }
    DoorFrame(frame, sections, modifier, ready = state.loaded, tabs = true, tabsInHeader = true) { inner ->
        when {
            frame.section != DOOR_HOME -> {
                DoorSectionPage(door, frame.section, preferences, wide, inner)
            }

            // Glowfin new look (Ben 2026-11-22): the big top is for the TV remote only. Phones, tablets and phones
            // turned sideways all land on the rows, right under the tabs (a tap opens the title's own page anyway).
            isTv -> {
                WideDoor(state, easy, isTv, viewModel, frame, onClick, onSeeAll, inner)
            }

            else -> {
                PhoneDoor(state, easy, frame, onClick, onSeeAll, { viewModel.navigationManager.navigateTo(it) }, inner)
            }
        }
    }
}

/** Section keys in the Movies, Shows and Anime tabs */
private const val SEC_ALL = "all"
private const val SEC_GENRES = "genres"
private const val SEC_COLLECTIONS = "collections"
private const val SEC_MY_LIST = "mylist"

/** Glowfin Famous lists (Idea Box 28): IMDb Top 250, Oscars, Emmys. Not on Kids. */
private const val SEC_FAMOUS = "famous"

/** The Anime page's Shows and Movies tabs */
private const val SEC_ANIME_SHOWS = "animeshows"
private const val SEC_ANIME_MOVIES = "animemovies"

/**
 * Glowfin's tabs (ui-7, Wholphin-like). Movies: For you, All movies, Collections, Genres, My List.
 * Shows: For you, All shows, Genres, My List (no Networks). Anime: For you, Shows, Movies, Genres and tags.
 * Kids and Easy read: no Collections.
 */
internal fun doorSections(
    door: BenflixDoor,
    easy: Boolean,
): List<DoorSection> {
    val forYou = DoorSection(DOOR_HOME, "For you", BenflixIcon.HOME)
    return when (door) {
        BenflixDoor.ANIME -> {
            listOf(
                forYou,
                DoorSection(SEC_ANIME_SHOWS, "Shows", BenflixIcon.TV),
                DoorSection(SEC_ANIME_MOVIES, "Movies", BenflixIcon.FILM),
                DoorSection(SEC_GENRES, "Genres and tags", BenflixIcon.LIST),
            )
        }

        BenflixDoor.MOVIES -> {
            listOfNotNull(
                forYou,
                DoorSection(SEC_ALL, "All movies", BenflixIcon.FILM),
                if (easy) null else DoorSection(SEC_COLLECTIONS, "Collections", BenflixIcon.IMAGE),
                DoorSection(SEC_GENRES, "Genres", BenflixIcon.LIST),
                if (easy) null else DoorSection(SEC_FAMOUS, "Famous lists", BenflixIcon.STAR),
                DoorSection(SEC_MY_LIST, "My List", BenflixIcon.PLUS),
            )
        }

        else -> {
            listOfNotNull(
                forYou,
                DoorSection(SEC_ALL, "All shows", BenflixIcon.TV),
                DoorSection(SEC_GENRES, "Genres", BenflixIcon.LIST),
                if (easy) null else DoorSection(SEC_FAMOUS, "Famous lists", BenflixIcon.STAR),
                DoorSection(SEC_MY_LIST, "My List", BenflixIcon.PLUS),
            )
        }
    }
}

/** A section from the menu, in place of the door's Home rows (the same pages "Browse by" used to open) */
@Composable
private fun DoorSectionPage(
    door: BenflixDoor,
    key: String,
    preferences: UserPreferences,
    wide: Boolean,
    modifier: Modifier,
) {
    val inner =
        modifier.padding(
            top = if (wide) LocalTopBarOverlap.current else 0.dp,
            start = if (wide) doorSectionStart() else PHONE_SIDE,
            end = if (wide) 16.dp else 8.dp,
        )
    val browse = { what: String -> Destination.DoorBrowse(door.name, what) }
    when (key) {
        SEC_ALL -> {
            DoorBrowsePage(browse(DoorViewModel.BROWSE_ALL), preferences, inner)
        }

        SEC_GENRES -> {
            DoorBrowsePage(browse(DoorViewModel.BROWSE_GENRES), preferences, inner)
        }

        SEC_COLLECTIONS -> {
            DoorBrowsePage(browse(DoorViewModel.BROWSE_COLLECTIONS), preferences, inner)
        }

        SEC_FAMOUS -> {
            FamousListsPage(shows = door != BenflixDoor.MOVIES, modifier = inner)
        }

        SEC_MY_LIST -> {
            DoorRowGridPage(Destination.DoorRowGrid(door.name, DoorViewModel.MY_LIST, "My List"), inner)
        }

        SEC_ANIME_SHOWS -> {
            DoorBrowsePage(browse(DoorViewModel.BROWSE_ANIME_SHOWS), preferences, inner)
        }

        SEC_ANIME_MOVIES -> {
            DoorBrowsePage(browse(DoorViewModel.BROWSE_ANIME_MOVIES), preferences, inner)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WideDoor(
    state: DoorPageState,
    easy: Boolean,
    isTv: Boolean,
    viewModel: DoorViewModel,
    frame: DoorFrameState,
    onClick: (BaseItem) -> Unit,
    onSeeAll: (DoorRowData) -> Unit,
    modifier: Modifier,
) {
    val onOpen: (Destination) -> Unit = { viewModel.navigationManager.navigateTo(it) }
    // The poster you're on, and the one the top area shows (it waits half a second after you stop)
    // Speed check (Ben 2026-11-18): the poster you're on is only watched here, never read while building the page,
    // so moving along a row no longer rebuilds the whole door on every press. Only the top area changes, once you stop.
    val focused = remember { mutableStateOf<BaseItem?>(null) }
    var shown by remember { mutableStateOf<BaseItem?>(null) }
    LaunchedEffect(Unit) {
        snapshotFlow { focused.value }.collectLatest { item ->
            if (item == null) return@collectLatest
            if (shown == null) {
                shown = item
            } else {
                delay(PREVIEW_DELAY_MS)
                shown = item
            }
        }
    }
    // Before anything is focused (or on a touch screen), describe the first poster
    val first = state.rows.firstNotNullOfOrNull { it.items.firstOrNull() }
    val preview = shown ?: first

    val restore = remember { FocusRequester() }
    // Ben: every door opens on the first card of the top row. Rows load in one by one, so until you
    // press a button the door keeps going back to the top row as rows arrive above it.
    val firstKey = state.rows.firstOrNull()?.key
    val savedRow = viewModel.focusedRow.takeIf { viewModel.userMoved }
    val targetRow = state.rows.firstOrNull { it.key == savedRow }?.key ?: firstKey
    val targetIndex = if (savedRow != null && targetRow == savedRow) viewModel.focusedIndex else 0
    var restored by remember { mutableStateOf(false) }
    // Back (approved dr-p-back): first back up to the top row's first card, then out of the door
    var focusedKey by remember { mutableStateOf(savedRow) }
    var topRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        // Wait until the top row has really loaded (not a grey placeholder), so focus starts on its first card
        withTimeoutOrNull(FIRST_FOCUS_WAIT_MS) {
            snapshotFlow {
                val st = viewModel.state.value
                st.loaded || st.entries().firstOrNull() is DoorEntry.Real
            }.first { it }
        }
        snapshotFlow {
            viewModel.state.value.rows
                .isNotEmpty()
        }.first { it }
        delay(FOCUS_SETTLE_MS)
        if (isTv) restore.tryRequestFocus()
        restored = true
    }
    // Ben (TV): cards were too small to read; 120 wide with a slightly shorter top area still shows the next row
    val posterWidth = if (easy) 128.dp else 120.dp
    val columnState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = state.rows.indexOfFirst { it.key == targetRow }.coerceAtLeast(0),
        )
    LaunchedEffect(firstKey) {
        snapshotFlow { columnState.firstVisibleItemIndex == 0 && (focusedKey == null || focusedKey == firstKey) }
            .collect { frame.atTop = it }
    }
    frame.toTop = {
        viewModel.focusedRow = firstKey
        viewModel.focusedIndex = 0
        focusedKey = firstKey
        topRequest++
    }
    LaunchedEffect(topRequest) {
        if (topRequest == 0) return@LaunchedEffect
        columnState.scrollToItem(0)
        delay(FOCUS_SETTLE_MS * 2)
        if (isTv) restore.tryRequestFocus()
    }
    // A row that loads in above the top one: go back to the very top and onto its first card
    LaunchedEffect(firstKey, restored) {
        if (!restored || viewModel.userMoved || firstKey == null) return@LaunchedEffect
        columnState.scrollToItem(0)
        if (isTv) {
            delay(FOCUS_SETTLE_MS)
            restore.tryRequestFocus()
        }
    }

    // Ben (robot find): coming Back after finishing a movie, Keep watching drops that card while it has the
    // focus, and the focus fell onto the side menu (which opened over the page). When a row change takes the
    // focus away from the rows, put it back on the same row, on the card now in that spot.
    var rowsHaveFocus by remember { mutableStateOf(false) }
    var rowsLostFocusAt by remember { mutableStateOf(0L) }
    // (which cards, not just how many: a fresh row with the same count can still drop the card you were on)
    val rowsSignature = state.rows.map { r -> r.key to r.items.map { it.id } }
    LaunchedEffect(rowsSignature) {
        val changedAt = System.currentTimeMillis()
        if (!isTv || !restored) return@LaunchedEffect
        delay(FOCUS_SETTLE_MS)
        if (!rowsHaveFocus && rowsLostFocusAt >= changedAt - FOCUS_SETTLE_MS) restore.tryRequestFocus()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().onFirstUserMove { viewModel.userMoved = true }) {
        // On TV the top bar sits over this page; the picture goes up behind it (Ben)
        val overlap = LocalTopBarOverlap.current
        // Glowfin new look (part 2): the top describes the card you're on, then the first row. The top stays
        // locked while the rows move, so the page never jumps. About half the screen, like the approved design.
        val topHeight = (maxHeight * TOP_SHARE).coerceIn(overlap + 180.dp, overlap + 230.dp)
        var brightArt by remember { mutableStateOf(false) }
        // The art of the card you're on fills the whole page, behind the tabs and the rows too
        DoorArt(item = preview, pageHeight = maxHeight, onBright = { brightArt = it })
        if (preview != null && !easy) {
            // The score pill at the top right, under the clock (same spot as on the details pages)
            // Glowfin "94% for you" (Idea Box 28) sits under it in the empty space, laid over the page so
            // nothing moves
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.TopEnd).padding(end = SIDE, top = overlap + 2.dp),
            ) {
                DoorScorePill(item = preview)
                MatchPill(
                    itemId =
                        preview.id.takeIf {
                            preview.type == org.jellyfin.sdk.model.api.BaseItemKind.MOVIE ||
                                preview.type == org.jellyfin.sdk.model.api.BaseItemKind.SERIES
                        },
                )
            }
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(topHeight)) {
                if (preview != null) {
                    DoorInfo(
                        item = preview,
                        easy = easy,
                        brightArt = brightArt,
                        modifier = Modifier.padding(start = SIDE, end = SIDE, top = overlap + 2.dp).widthIn(max = 560.dp),
                    )
                }
            }
            // Like Wholphin: the row you move to jumps up whole (name and all) right under the locked top area
            val defaultSpec = LocalBringIntoViewSpec.current
            val toTop = remember { ScrollToTopBringIntoViewSpec(0f) }
            CompositionLocalProvider(LocalBringIntoViewSpec provides if (isTv) toTop else defaultSpec) {
                LazyColumn(
                    state = columnState,
                    contentPadding = PaddingValues(bottom = 48.dp),
                    // Ben: a little more room between rows so the next row only just peeks in
                    verticalArrangement = Arrangement.spacedBy(if (isTv && !easy) 14.dp else 20.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .onFocusChanged {
                                if (rowsHaveFocus && !it.hasFocus) rowsLostFocusAt = System.currentTimeMillis()
                                rowsHaveFocus = it.hasFocus
                            },
                ) {
                    state.entries().forEach { entry ->
                        val row = (entry as? DoorEntry.Real)?.row
                        item(key = entry.key) {
                            CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                                if (row == null) {
                                    WaitingRow(posterWidth = posterWidth, showName = !isTv)
                                    return@CompositionLocalProvider
                                }
                                DoorRow(
                                    row = row,
                                    posterWidth = posterWidth,
                                    showName = !isTv,
                                    onOpen = onOpen,
                                    startIndex = if (row.key == targetRow) targetIndex else 0,
                                    focusRequester = if (row.key == targetRow) restore else null,
                                    focusIndex = targetIndex,
                                    scrollKey = if (row.key == targetRow) topRequest else 0,
                                    onClick = onClick,
                                    onSeeAll = { onSeeAll(row) },
                                    onFocus = { index, item ->
                                        focused.value = item
                                        focusedKey = row.key
                                        viewModel.focusedRow = row.key
                                        viewModel.focusedIndex = index
                                    },
                                )
                            }
                        }
                    }
                    // (Ben 2026-11-18: the "Can't decide?" pickers are gone everywhere)
                }
            }
        }
    }
}

/** A row on the page: one that has loaded, or a grey placeholder for one still loading */
private sealed interface DoorEntry {
    val key: String

    data class Real(
        val row: DoorRowData,
    ) : DoorEntry {
        override val key get() = row.key
    }

    data class Waiting(
        override val key: String,
    ) : DoorEntry
}

/**
 * Ben: grey placeholder rows hold each row's place until it loads, so rows don't pop in above
 * each other. A row that turns out empty just goes away. Same key as the real row, so it swaps in place.
 */
private fun DoorPageState.entries(): List<DoorEntry> {
    if (order.isEmpty()) {
        // Before the door knows its rows (it's still finding the libraries): a few grey rows
        return if (!loaded && rows.isEmpty()) List(WAITING_ROWS) { DoorEntry.Waiting("waiting-$it") } else rows.map { DoorEntry.Real(it) }
    }
    val byKey = rows.associateBy { it.key }
    return order.mapNotNull { key ->
        byKey[key]?.let { DoorEntry.Real(it) }
            ?: if (key in pending) DoorEntry.Waiting(key) else null
    }
}

/** A grey row shaped like a row of posters: a short bar for the name, then grey cards. Not focusable. */
@Composable
private fun WaitingRow(
    posterWidth: Dp,
    showName: Boolean,
    side: Dp = SIDE,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .padding(start = side, bottom = 8.dp)
                .width(140.dp)
                .height(18.dp)
                .background(BenflixColors.PanelHigh, RoundedCornerShape(4.dp)),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            // Wider than the screen on purpose, like a real row; the cards past the edge just aren't seen
            modifier = Modifier.padding(start = side).wrapContentWidth(Alignment.Start, unbounded = true),
        ) {
            repeat(WAITING_CARDS) {
                Column {
                    Box(
                        Modifier
                            .width(posterWidth)
                            .height(posterWidth / POSTER_RATIO)
                            .background(BenflixColors.Raised, shape),
                    )
                    if (showName) {
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier
                                .width(posterWidth * 0.7f)
                                .height(12.dp)
                                .background(BenflixColors.Raised, RoundedCornerShape(4.dp)),
                        )
                    }
                }
            }
        }
    }
}

/** One row: medium white name, then posters, ending in "See all" when there is more */
@Composable
internal fun DoorRow(
    row: DoorRowData,
    posterWidth: Dp,
    showName: Boolean,
    startIndex: Int,
    focusRequester: FocusRequester?,
    focusIndex: Int,
    onClick: (BaseItem) -> Unit,
    onSeeAll: () -> Unit,
    onFocus: (Int, BaseItem) -> Unit,
    modifier: Modifier = Modifier,
    side: Dp = SIDE,
    /** Changes when Back sends you to the top: the row scrolls back to the card focus goes to */
    scrollKey: Int = 0,
    /** Request cards (Trending, not on Glowfin) open their own page */
    onOpen: (Destination) -> Unit = {},
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex.coerceIn(0, (row.items.size - 1).coerceAtLeast(0)))
    LaunchedEffect(scrollKey) {
        if (scrollKey > 0) listState.scrollToItem(focusIndex.coerceIn(0, (row.items.size - 1).coerceAtLeast(0)))
    }
    // TV: the row name always comes into view with the posters (Ben: the Top 10 name was cut off)
    Column(modifier = modifier.fillMaxWidth().scrollAsOneBlock()) {
        Text(
            text =
                if (row.note == null) {
                    androidx.compose.ui.text
                        .AnnotatedString(row.title)
                } else {
                    androidx.compose.ui.text.buildAnnotatedString {
                        append(row.title)
                        // Famous lists: the count in the small grey style, after the same row name every row has
                        withStyle(
                            androidx.compose.ui.text.SpanStyle(
                                color = BenflixColors.Muted,
                                fontSize = BenflixType.small,
                                fontWeight = FontWeight.Medium,
                            ),
                        ) { append("   ${row.note}") }
                    }
                },
            color = BenflixColors.Text,
            fontSize = BenflixType.lead,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = side, end = side, bottom = 8.dp),
        )
        // TV: the row keeps a little room under its posters for the logo stripes. That room sits in the gap that was
        // already between rows (the row still reports its old height), so no row moved.
        val stripes = !showName
        val room = if (stripes) FocusStripesRoom else 0.dp
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(start = side, end = side, bottom = room),
            modifier =
                Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, (placeable.height - room.roundToPx()).coerceAtLeast(0)) { placeable.place(0, 0) }
                },
            // Glowfin T1: each Top 10 number has its own box beside its poster, so the posters keep a gap too
            horizontalArrangement = Arrangement.spacedBy(if (row.style == DoorRowStyle.TOP10) 14.dp else 12.dp),
        ) {
            if (row.style == DoorRowStyle.DISCOVER) {
                itemsIndexed(row.discover, key = { _, it -> "d-${it.type}-${it.id}" }) { index, item ->
                    com.github.damontecres.wholphin.ui.cards.DiscoverItemCard(
                        item = item,
                        onClick = { onOpen(item.destination) },
                        onLongClick = {},
                        width = posterWidth,
                        cornerNote = if (row.askNote && item.jellyfinItemId == null) "Ask for it" else null,
                        modifier =
                            if (focusRequester != null && index == focusIndex.coerceAtMost(row.discover.size - 1)) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            },
                    )
                }
            }
            itemsIndexed(row.items, key = { _, it -> it.id.toString() }) { index, item ->
                val mod =
                    if (focusRequester != null && index == focusIndex.coerceAtMost(row.items.size - 1)) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    }
                if (row.style == DoorRowStyle.TOP10) {
                    DoorTop10Poster(
                        rank = index + 1,
                        item = item,
                        width = posterWidth,
                        showName = showName,
                        onClick = { onClick(item) },
                        onFocus = { onFocus(index, item) },
                        modifier = mod,
                        stripes = stripes,
                    )
                } else {
                    DoorPoster(
                        item = item,
                        width = posterWidth,
                        showName = showName,
                        onClick = { onClick(item) },
                        onFocus = { onFocus(index, item) },
                        modifier = mod,
                        stripes = stripes,
                    )
                }
            }
            if (row.seeAll) {
                item(key = "see-all") {
                    SeeAllTile(width = posterWidth, onClick = onSeeAll)
                }
            }
        }
    }
}

/** "1h 52m" */
fun lengthText(length: Duration): String {
    val minutes = length.inWholeMinutes
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

@Composable
internal fun PhoneDoor(
    state: DoorPageState,
    easy: Boolean,
    frame: DoorFrameState,
    onClick: (BaseItem) -> Unit,
    onSeeAll: (DoorRowData) -> Unit,
    onOpen: (Destination) -> Unit,
    modifier: Modifier,
) {
    // Ben: open at the very top. The picked card and rows load in one by one; until you touch the
    // page, go back to the top whenever something new arrives above what's showing.
    val columnState = rememberLazyListState()
    var touched by remember { mutableStateOf(false) }
    val topKey = state.rows.firstOrNull()?.key
    LaunchedEffect(topKey) {
        if (!touched && topKey != null) columnState.scrollToItem(0)
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { snapshotFlow { columnState.firstVisibleItemIndex == 0 }.collect { frame.atTop = it } }
    frame.toTop = { scope.launch { columnState.animateScrollToItem(0) } }
    BoxWithConstraints(modifier = modifier.fillMaxSize().onFirstUserMove { touched = true }) {
        // About 3.5 posters across on a phone; tablets and sideways phones fit more across instead of bigger ones
        val across =
            if (easy) {
                maxOf(2.6f, maxWidth / PHONE_EASY_POSTER)
            } else {
                maxOf(3.5f, maxWidth / PHONE_POSTER)
            }
        val posterWidth = ((maxWidth - PHONE_SIDE - 12.dp * 3) / across).coerceAtLeast(72.dp)
        // Tablets and sideways phones: the header (and the tabs) sit over the page, so the rows start under them.
        // 0 on phones held upright, where the tabs are laid out above the page.
        val underHeader = LocalTopBarOverlap.current
        // Glowfin new look (Ben 2026-11-22): no picture at the top on phones and tablets held upright. The rows
        // start right under the tabs (a tap opens the title's own page, with its big art, anyway).
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                state = columnState,
                contentPadding = PaddingValues(top = underHeader + 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                state.entries().forEach { entry ->
                    val row = (entry as? DoorEntry.Real)?.row
                    item(key = entry.key) {
                        if (row == null) {
                            WaitingRow(posterWidth = posterWidth, showName = false, side = PHONE_SIDE)
                            return@item
                        }
                        DoorRow(
                            row = row,
                            posterWidth = posterWidth,
                            // Cover art only, no names under the posters (Ben 2026-11-22)
                            showName = false,
                            onOpen = onOpen,
                            startIndex = 0,
                            focusRequester = null,
                            focusIndex = 0,
                            onClick = onClick,
                            onSeeAll = { onSeeAll(row) },
                            onFocus = { _, _ -> },
                            side = PHONE_SIDE,
                        )
                    }
                }
            }
        }
    }
}

/** Nothing in this door yet: a short note (and Try again when the server can't be reached) */
@Composable
internal fun EmptyDoor(
    name: String,
    offline: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(offline) { if (offline) retryFocus.tryRequestFocus() }
    EmptyPage(
        icon = if (offline) BenflixIcon.WARNING else BenflixIcon.FILM,
        title = if (offline) "Can't reach Tower right now" else "Nothing in $name yet",
        line =
            if (offline) {
                "Check the internet or that Tower is on, then try again."
            } else {
                "Once Tower has some, they show up here."
            },
        modifier = modifier,
    ) {
        MusicButton(
            label = "Try again",
            icon = BenflixIcon.REFRESH,
            onClick = onRetry,
            primary = true,
            modifier = Modifier.focusRequester(retryFocus),
        )
    }
}

/** TV side margin. Ben 2026-11-18: the page starts right next to the side menu (was 48dp, a big empty gap) */
private val SIDE = 16.dp

private val PHONE_SIDE = BenflixSpace.phoneEdge

/** About how wide a poster is on a tablet or a phone turned sideways (more fit across, none get huge) */
private val PHONE_POSTER = 132.dp
private val PHONE_EASY_POSTER = 170.dp

/** How much of the screen the top (the words about the card you're on) takes, from the very top */
private const val TOP_SHARE = 0.52f
private const val PREVIEW_DELAY_MS = 500L
private const val FIRST_FOCUS_WAIT_MS = 4_000L
private const val WAITING_ROWS = 3
private const val WAITING_CARDS = 9
private const val FOCUS_SETTLE_MS = 80L
