package com.github.damontecres.wholphin.ui.door

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.AniListFacts
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.MalFacts
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.main.DoorWipe
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.LocalTopBarOverlap
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixShade
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.onFirstUserMove
import com.github.damontecres.wholphin.ui.util.scrollAsOneBlock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.extensions.ticks
import kotlin.time.Duration

/**
 * Benflix redesign: the inside of the Movies and Shows doors (approved mv-preview + pv-fade).
 * TV: the top area describes the poster you're on, with its picture fading in from the right;
 * rows of posters below, then "Browse by" tiles. Phones: a "Picked for you" card, then the rows.
 * Kids and Easy read: the same, bigger, fewer rows, no scores.
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
    val easy = kids || preferences.appPreferences.benflixPreferences.easyRead
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp || config.smallestScreenWidthDp >= 600
    val onClick: (BaseItem) -> Unit = { viewModel.navigationManager.navigateTo(it.destination()) }
    val onSeeAll: (DoorRowData) -> Unit = { row ->
        viewModel.navigationManager.navigateTo(Destination.DoorRowGrid(door.name, row.key, row.title))
    }
    val onBrowse: (DoorBrowseTile) -> Unit = { viewModel.navigationManager.navigateTo(it.destination) }

    if (state.loaded && state.rows.isEmpty()) {
        EmptyDoor(door, state.browse, state.offline, onBrowse, viewModel::retry, modifier.padding(top = LocalTopBarOverlap.current))
        return
    }
    if (wide) {
        WideDoor(door, state, easy, isTv, viewModel, onClick, onSeeAll, onBrowse, modifier)
    } else {
        PhoneDoor(state, easy, onClick, onSeeAll, onBrowse, modifier)
    }
}

@Composable
private fun WideDoor(
    door: BenflixDoor,
    state: DoorPageState,
    easy: Boolean,
    isTv: Boolean,
    viewModel: DoorViewModel,
    onClick: (BaseItem) -> Unit,
    onSeeAll: (DoorRowData) -> Unit,
    onBrowse: (DoorBrowseTile) -> Unit,
    modifier: Modifier,
) {
    // The poster you're on, and the one the top area shows (it waits half a second after you stop)
    var focused by remember { mutableStateOf<BaseItem?>(null) }
    var shown by remember { mutableStateOf<BaseItem?>(null) }
    LaunchedEffect(focused) {
        val item = focused ?: return@LaunchedEffect
        if (shown == null) {
            shown = item
        } else {
            delay(PREVIEW_DELAY_MS)
            shown = item
        }
    }
    // Before anything is focused (or on a touch screen), describe the first poster
    val first =
        state.rows
            .firstOrNull()
            ?.items
            ?.firstOrNull()
    val preview = shown ?: first

    val restore = remember { FocusRequester() }
    // Ben: every door opens on the first card of the top row. Rows load in one by one, so until you
    // press a button the door keeps going back to the top row as rows arrive above it.
    val firstKey = state.rows.firstOrNull()?.key
    val savedRow = viewModel.focusedRow.takeIf { viewModel.userMoved }
    val targetRow = state.rows.firstOrNull { it.key == savedRow }?.key ?: firstKey
    val targetIndex = if (savedRow != null && targetRow == savedRow) viewModel.focusedIndex else 0
    var restored by remember { mutableStateOf(false) }
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
        rememberLazyListState(initialFirstVisibleItemIndex = state.rows.indexOfFirst { it.key == targetRow }.coerceAtLeast(0))
    // A row that loads in above the top one: go back to the very top and onto its first card
    LaunchedEffect(firstKey, restored) {
        if (!restored || viewModel.userMoved || firstKey == null) return@LaunchedEffect
        columnState.scrollToItem(0)
        if (isTv) {
            delay(FOCUS_SETTLE_MS)
            restore.tryRequestFocus()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().onFirstUserMove { viewModel.userMoved = true }) {
        // On TV the top bar sits over this page; the picture goes up behind it (Ben)
        val overlap = LocalTopBarOverlap.current
        val topHeight = ((maxHeight - overlap) * if (easy) 0.40f else 0.36f).coerceIn(170.dp, 300.dp)
        Column(Modifier.fillMaxSize()) {
            PreviewArea(item = preview, easy = easy, height = topHeight + overlap, overlap = overlap)
            LazyColumn(
                state = columnState,
                contentPadding = PaddingValues(bottom = 48.dp),
                // Ben: a little more room between rows so the next row only just peeks in
                verticalArrangement = Arrangement.spacedBy(if (isTv && !easy) 30.dp else 20.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                state.entries().forEach { entry ->
                    val row = (entry as? DoorEntry.Real)?.row
                    item(key = entry.key) {
                        if (row == null) {
                            WaitingRow(posterWidth = posterWidth, showName = !isTv)
                            return@item
                        }
                        DoorRow(
                            row = row,
                            posterWidth = posterWidth,
                            showName = !isTv,
                            startIndex = if (row.key == targetRow) targetIndex else 0,
                            focusRequester = if (row.key == targetRow) restore else null,
                            focusIndex = targetIndex,
                            onClick = onClick,
                            onSeeAll = { onSeeAll(row) },
                            onFocus = { index, item ->
                                focused = item
                                viewModel.focusedRow = row.key
                                viewModel.focusedIndex = index
                            },
                        )
                    }
                }
                if (state.browse.isNotEmpty()) {
                    item(key = "browse") {
                        BrowseRow(state.browse, easy, onBrowse, Modifier.padding(start = SIDE, end = SIDE))
                    }
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
        byKey[key]?.let { DoorEntry.Real(it) } ?: if (key in pending) DoorEntry.Waiting(key) else null
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
private fun DoorRow(
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
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex.coerceIn(0, (row.items.size - 1).coerceAtLeast(0)))
    // TV: the row name always comes into view with the posters (Ben: the Top 10 name was cut off)
    Column(modifier = modifier.fillMaxWidth().scrollAsOneBlock()) {
        Text(
            text = row.title,
            color = BenflixColors.Text,
            fontSize = BenflixType.lead,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = side, end = side, bottom = 8.dp),
        )
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(if (row.style == DoorRowStyle.TOP10) 0.dp else 12.dp),
        ) {
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
                    )
                } else {
                    DoorPoster(
                        item = item,
                        width = posterWidth,
                        showName = showName,
                        onClick = { onClick(item) },
                        onFocus = { onFocus(index, item) },
                        modifier = mod,
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

/** "Browse by": dark tiles (Genres, Collections, Studios, All movies) */
@Composable
private fun BrowseRow(
    tiles: List<DoorBrowseTile>,
    easy: Boolean,
    onBrowse: (DoorBrowseTile) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = tiles.size.coerceAtLeast(1),
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Browse by",
            color = BenflixColors.Text,
            fontSize = BenflixType.lead,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        tiles.chunked(columns).forEach { line ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
            ) {
                line.forEach { tile ->
                    BrowseTile(title = tile.title, big = easy, onClick = { onBrowse(tile) }, modifier = Modifier.weight(1f))
                }
                repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * The top area: the focused title's wide picture on the right, fading into the dark tray; the words
 * on clean dark on the left (name, info line, scores, a short story, and Resume for things started).
 */
@Composable
private fun PreviewArea(
    item: BaseItem?,
    easy: Boolean,
    height: Dp,
    overlap: Dp = 0.dp,
) {
    val imageUrlService = LocalImageUrlService.current
    val density = LocalDensity.current
    val url =
        remember(item?.id, height) {
            item?.let {
                imageUrlService.getItemImageUrl(it, ImageType.BACKDROP, fillHeight = with(density) { height.roundToPx() })
            }
        }
    Box(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(PICTURE_WIDTH)
                .fillMaxHeight(),
        ) {
            Crossfade(targetState = url, animationSpec = tween(PICTURE_FADE_MS), label = "doorPicture") { picture ->
                if (picture != null) {
                    AsyncImage(
                        model = picture,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        BenflixShade.topAreaSide,
                    ),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        BenflixShade.bottom(0.5f),
                    ),
            )
            if (overlap > 0.dp) {
                // A light shade at the very top so the bar's words stay easy to read
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(overlap * 1.6f)
                        .background(Brush.verticalGradient(0f to BenflixColors.Tray.copy(alpha = .55f), 1f to Color.Transparent)),
                )
            }
        }
        if (item != null) {
            PreviewWords(
                item = item,
                easy = easy,
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = SIDE, end = SIDE, top = overlap)
                        .widthIn(max = 560.dp),
            )
        }
    }
}

@Composable
private fun PreviewWords(
    item: BaseItem,
    easy: Boolean,
    modifier: Modifier = Modifier,
) {
    val isEpisode = item.type == BaseItemKind.EPISODE
    Column(modifier = modifier) {
        Text(
            text = item.title.orEmpty(),
            color = BenflixColors.Text,
            fontSize = if (easy) BenflixType.hero else BenflixType.pageTitle,
            lineHeight = if (easy) 40.sp else 34.sp,
            fontWeight = FontWeight.ExtraBold,
            // One line on the normal page so the shorter top area always fits the words
            maxLines = if (easy) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = remember(item) { metaLine(item) }
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                color = BenflixColors.Soft,
                fontSize = BenflixType.body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (!easy && !isEpisode) ScoreLine(item, Modifier.padding(top = 6.dp))
        val story = item.data.overview
        if (!easy && !story.isNullOrBlank()) {
            Text(
                text = story,
                color = BenflixColors.Soft,
                fontSize = BenflixType.small,
                lineHeight = BenflixType.small * 1.35f,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        val progress = item.progress()
        if (progress != null || isEpisode) ResumeLine(item, progress, Modifier.padding(top = 10.dp))
    }
}

/** "Resume · 40 min left" with a short progress line (episodes: "Play S2 E5" when not started) */
@Composable
private fun ResumeLine(
    item: BaseItem,
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    val left = remember(item) { minutesLeft(item) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        BenflixIconImage(BenflixIcon.PLAY, color = BenflixColors.Text, size = 16.dp)
        Text(
            text = if (progress != null) "Resume" else "Play",
            color = BenflixColors.Text,
            fontSize = BenflixType.body,
            fontWeight = FontWeight.Bold,
        )
        if (left != null && progress != null) {
            Text(text = "· $left", color = BenflixColors.Muted, fontSize = BenflixType.body, fontWeight = FontWeight.SemiBold)
        }
        if (progress != null) {
            Box(
                Modifier
                    .width(90.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2B2E37)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .background(BenflixColors.Text),
                )
            }
        }
    }
}

/**
 * Review scores on their own line (approved sc-line): a bold white number and the site in grey.
 * Movies and shows: IMDb and Rotten Tomatoes. Anime: AniList and MyAnimeList, falling back to IMDb.
 * Sites without a score are left out.
 */
@Composable
fun ScoreLine(
    item: BaseItem,
    modifier: Modifier = Modifier,
) {
    val aniList by AniListFacts.cards.collectAsState()
    val mal by MalFacts.scores.collectAsState()
    val facts = aniList[item.id]
    val scores =
        buildList {
            if (facts != null) {
                facts.scoreText?.let { add(it to "AniList") }
                facts.malId?.let { mal[it] }?.let { add(MalFacts.text(it) to "MAL") }
            }
            if (isEmpty()) {
                item.data.communityRating?.takeIf { it > 0f }?.let {
                    add(String.format(java.util.Locale.US, "%.1f", it) to "IMDb")
                }
                item.data.criticRating
                    ?.takeIf { it > 0f }
                    ?.let { add("${it.toInt()}%" to "Rotten Tomatoes") }
            }
        }
    if (scores.isEmpty()) return
    val text =
        buildAnnotatedString {
            scores.forEachIndexed { i, (number, site) ->
                if (i > 0) append("     ")
                withStyle(SpanStyle(color = BenflixColors.Text, fontWeight = FontWeight.ExtraBold)) { append(number) }
                withStyle(SpanStyle(color = BenflixColors.Muted, fontWeight = FontWeight.SemiBold)) { append(" $site") }
            }
        }
    Text(text = text, fontSize = BenflixType.body, maxLines = 1, modifier = modifier)
}

/** "2023 · 1h 52m · PG-13 · Drama" (episodes: "S2 E5 · Episode name") */
fun metaLine(item: BaseItem): String {
    val d = item.data
    val parts =
        if (item.type == BaseItemKind.EPISODE) {
            listOfNotNull(yearText(item), d.name)
        } else {
            listOfNotNull(
                d.productionYear?.toString(),
                d.runTimeTicks
                    ?.ticks
                    ?.takeIf { item.type == BaseItemKind.MOVIE }
                    ?.let(::lengthText),
                d.childCount?.takeIf { item.type == BaseItemKind.SERIES && it > 0 }?.let { if (it == 1) "1 season" else "$it seasons" },
                d.officialRating,
                d.genres?.firstOrNull(),
            )
        }
    return parts.filter { it.isNotBlank() }.joinToString(" · ")
}

/** "1h 52m" */
fun lengthText(length: Duration): String {
    val minutes = length.inWholeMinutes
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

private fun minutesLeft(item: BaseItem): String? {
    val total = item.data.runTimeTicks?.ticks ?: return null
    val left = total - item.playbackPosition
    val minutes = left.inWholeMinutes
    return when {
        minutes <= 0 -> null
        minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m left"
        else -> "$minutes min left"
    }
}

@Composable
private fun PhoneDoor(
    state: DoorPageState,
    easy: Boolean,
    onClick: (BaseItem) -> Unit,
    onSeeAll: (DoorRowData) -> Unit,
    onBrowse: (DoorBrowseTile) -> Unit,
    modifier: Modifier,
) {
    // Ben: open at the very top. The picked card and rows load in one by one; until you touch the
    // page, go back to the top whenever something new arrives above what's showing.
    val columnState = rememberLazyListState()
    var touched by remember { mutableStateOf(false) }
    val topKey = state.pick?.let { "pick" } ?: state.rows.firstOrNull()?.key
    LaunchedEffect(topKey) {
        if (!touched && topKey != null) columnState.scrollToItem(0)
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize().onFirstUserMove { touched = true }) {
        // About 3.5 posters across
        val posterWidth = ((maxWidth - PHONE_SIDE - 12.dp * 3) / if (easy) 2.6f else 3.5f).coerceAtLeast(72.dp)
        LazyColumn(
            state = columnState,
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            val pick = state.pick
            if (pick != null) {
                item(key = "pick") {
                    PickedCard(pick, easy, onClick = { onClick(pick) }, Modifier.padding(horizontal = PHONE_SIDE))
                }
            } else if (!state.loaded) {
                // Keeps the card's room while it's being picked, so the rows don't jump down when it arrives
                item(key = "pick") {
                    Box(
                        Modifier
                            .padding(horizontal = PHONE_SIDE)
                            .fillMaxWidth()
                            .aspectRatio(16f / 11f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(BenflixColors.Raised),
                    )
                }
            }
            state.entries().forEach { entry ->
                val row = (entry as? DoorEntry.Real)?.row
                item(key = entry.key) {
                    if (row == null) {
                        WaitingRow(posterWidth = posterWidth, showName = true, side = PHONE_SIDE)
                        return@item
                    }
                    DoorRow(
                        row = row,
                        posterWidth = posterWidth,
                        showName = true,
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
            if (state.browse.isNotEmpty()) {
                item(key = "browse") {
                    BrowseRow(state.browse, easy, onBrowse, Modifier.padding(horizontal = PHONE_SIDE), columns = 2)
                }
            }
        }
    }
}

/** Phones: one title picked for this person, with its picture and info. Tap opens its page. */
@Composable
private fun PickedCard(
    item: BaseItem,
    easy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageUrlService = LocalImageUrlService.current
    val url = remember(item.id) { imageUrlService.getItemImageUrl(item, ImageType.BACKDROP, fillWidth = 900) }
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = BenflixColors.Raised, focusedContainerColor = BenflixColors.Raised),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(16f / 11f),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (url != null) {
                AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.25f to Color.Transparent, 1f to BenflixColors.Tray.copy(alpha = .92f))),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            ) {
                Text(
                    text = "PICKED FOR YOU",
                    color = BenflixColors.Soft,
                    fontSize = BenflixType.tag,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                )
                Text(
                    text = item.title.orEmpty(),
                    color = BenflixColors.Text,
                    fontSize = BenflixType.pageTitle,
                    lineHeight = if (easy) 32.sp else 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = remember(item) { metaLine(item) }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        color = BenflixColors.Soft,
                        fontSize = BenflixType.tag,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** Nothing in this door yet: a short note and the Browse by tiles */
@Composable
private fun EmptyDoor(
    door: BenflixDoor,
    browse: List<DoorBrowseTile>,
    offline: Boolean,
    onBrowse: (DoorBrowseTile) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(offline) { if (offline) retryFocus.tryRequestFocus() }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = SIDE, vertical = 32.dp),
    ) {
        Text(
            text = if (offline) "Can't reach the server right now" else "Nothing in ${door.title} yet",
            color = BenflixColors.Text,
            fontSize = BenflixType.pageTitle,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text =
                if (offline) {
                    "Check the internet or that the server is on, then try again."
                } else {
                    "Once the server has some, they show up here."
                },
            color = BenflixColors.Muted,
            fontSize = BenflixType.body,
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
        )
        if (offline) {
            BrowseTile(
                title = "Try again",
                onClick = onRetry,
                modifier =
                    Modifier
                        .widthIn(max = 260.dp)
                        .fillMaxWidth()
                        .focusRequester(retryFocus),
            )
        } else if (browse.isNotEmpty()) {
            BrowseRow(browse, false, onBrowse)
        }
    }
}

/** TV side margin */
private val SIDE = BenflixSpace.tvEdge
private val PHONE_SIDE = BenflixSpace.phoneEdge

/** The picture takes the right 62% of the top area */
private const val PICTURE_WIDTH = 0.62f
private const val PICTURE_FADE_MS = 450
private const val PREVIEW_DELAY_MS = 500L
private const val FIRST_FOCUS_WAIT_MS = 4_000L
private const val WAITING_ROWS = 3
private const val WAITING_CARDS = 9
private const val FOCUS_SETTLE_MS = 80L
