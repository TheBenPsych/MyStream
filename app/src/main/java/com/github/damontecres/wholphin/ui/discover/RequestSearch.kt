package com.github.damontecres.wholphin.ui.discover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.ui.door.POSTER_RATIO
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.search.SearchKeyboard
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.tryRequestFocus

/** The three Requests search columns, in the main Search look */
internal enum class RequestSearchDoor(
    val title: String,
    val color: Color,
) {
    MOVIES("Movies", BenflixColors.Movies),
    SHOWS("Shows", BenflixColors.Shows),
    ANIME("Anime", BenflixColors.Shows),
}

/** Splits search results into Movies, Shows and Anime, plus People. Titles already on Benflix are left out. */
internal fun splitRequestResults(
    results: List<DiscoverItem>,
    anime: Set<Int>,
): Pair<Map<RequestSearchDoor, List<DiscoverItem>>, List<DiscoverItem>> {
    val askable = results.filter { it.type == SeerrItemType.PERSON || it.availability != SeerrAvailability.AVAILABLE }
    val doors =
        mapOf(
            RequestSearchDoor.MOVIES to askable.filter { it.type == SeerrItemType.MOVIE && it.id !in anime },
            RequestSearchDoor.SHOWS to askable.filter { it.type == SeerrItemType.TV && it.id !in anime },
            RequestSearchDoor.ANIME to
                askable.filter { (it.type == SeerrItemType.MOVIE || it.type == SeerrItemType.TV) && it.id in anime },
        )
    return doors to askable.filter { it.type == SeerrItemType.PERSON }
}

/** TV: the box at the top of Requests that opens the search page */
@Composable
internal fun RequestSearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.Panel,
                contentColor = BenflixColors.Soft,
                focusedContainerColor = BenflixColors.Raised,
                focusedContentColor = BenflixColors.Text,
            ),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            if (compact) {
                modifier.height(40.dp).width(300.dp)
            } else {
                modifier
                    .padding(horizontal = 16.dp)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
            },
    ) {
        Text(
            text = "Search any movie, show or anime to ask for",
            fontSize = if (compact) BenflixType.small else BenflixType.body,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = if (compact) 10.dp else 14.dp),
        )
    }
}

/**
 * Benflix redesign (Requests search, approved): works like the main Search. TV keyboard on the left,
 * the typed words and the best match on top, then three columns, Movies, Shows and Anime, each with
 * four posters two by two and See all. People sit underneath. Only titles not on Benflix show.
 * Back closes See all first, then the search page.
 */
@Composable
internal fun RequestSearchScreen(
    query: String,
    onQuery: (String) -> Unit,
    results: List<DiscoverItem>?,
    anime: Set<Int>,
    visible: (List<DiscoverItem>) -> List<DiscoverItem>,
    onOpen: (DiscoverItem) -> Unit,
    onMenu: (DiscoverItem) -> Unit,
    onFocus: (DiscoverItem) -> Unit,
    onClose: () -> Unit,
) {
    var seeAll by remember { mutableStateOf<RequestSearchDoor?>(null) }
    var focused by remember { mutableStateOf<DiscoverItem?>(null) }
    BackHandler {
        if (seeAll != null) seeAll = null else onClose()
    }
    val keyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { keyFocus.tryRequestFocus() }
    val split = remember(results, anime) { results?.let { splitRequestResults(visible(it), anime) } }
    Row(
        Modifier
            .fillMaxSize()
            .background(BenflixColors.Tray),
    ) {
        Box(Modifier.focusRequester(keyFocus)) {
            SearchKeyboard(
                query = query,
                onQueryChange = onQuery,
                suggestions =
                    split
                        ?.first
                        ?.values
                        ?.flatten()
                        ?.mapNotNull { it.title }
                        ?.distinct()
                        ?.take(5)
                        .orEmpty(),
                onSuggestion = onQuery,
                big = false,
                modifier = Modifier.fillMaxHeight(),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 16.dp, end = 32.dp, top = 12.dp),
        ) {
            Text(
                text = query.ifBlank { "Type a name" },
                color = if (query.isBlank()) BenflixColors.Muted else BenflixColors.Text,
                fontSize = BenflixType.pageTitle,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val line =
                focused?.let { item ->
                    listOfNotNull(item.title, item.releaseDate?.year?.toString(), item.statusLine()).joinToString(" · ")
                } ?: "Only titles that aren't on Benflix yet show here"
            Text(
                text = line,
                color = BenflixColors.Soft,
                fontSize = BenflixType.small,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )
            when {
                query.trim().length < 2 -> {}

                split == null -> {
                    Text("Searching…", color = BenflixColors.Muted, fontSize = BenflixType.body)
                }

                seeAll != null -> {
                    val door = seeAll!!
                    RequestSeeAllGrid(
                        door = door,
                        items = split.first[door].orEmpty(),
                        onOpen = onOpen,
                        onMenu = onMenu,
                        onFocus = {
                            focused = it
                            onFocus(it)
                        },
                    )
                }

                else -> {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val gap = 24.dp
                        val column = (maxWidth - gap * 2) / 3
                        val cardWidth = ((column - 10.dp) / 2).coerceAtMost(130.dp)
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                                RequestSearchDoor.entries.forEach { door ->
                                    RequestDoorColumn(
                                        door = door,
                                        items = split.first[door].orEmpty(),
                                        cardWidth = cardWidth,
                                        onOpen = onOpen,
                                        onMenu = onMenu,
                                        onFocus = {
                                            focused = it
                                            onFocus(it)
                                        },
                                        onSeeAll = { seeAll = door },
                                        modifier = Modifier.width(column),
                                    )
                                }
                            }
                            if (split.second.isNotEmpty()) {
                                RequestDoorRow(
                                    title = "People",
                                    items = split.second,
                                    isTv = true,
                                    onClick = onOpen,
                                    onLongClick = {},
                                    onFocus = { focused = it },
                                    modifier = Modifier.padding(top = 24.dp, bottom = 32.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun DiscoverItem.statusLine(): String? =
    when (availability) {
        SeerrAvailability.PENDING, SeerrAvailability.PROCESSING -> "Already asked for"
        SeerrAvailability.PARTIALLY_AVAILABLE -> "Some of it is on Benflix"
        else -> if (type == SeerrItemType.PERSON) null else "Ask for it"
    }

@Composable
private fun RequestDoorHeading(
    door: RequestSearchDoor,
    count: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(width = 4.dp, height = 16.dp)
                .background(if (count == 0) BenflixColors.Line else door.color, RoundedCornerShape(6.dp)),
        )
        Text(
            text = door.title,
            color = if (count == 0) BenflixColors.Muted else BenflixColors.Text,
            fontSize = BenflixType.body,
            fontWeight = FontWeight.ExtraBold,
        )
        if (count > 0) Text("$count", color = BenflixColors.Muted, fontSize = BenflixType.small, fontWeight = FontWeight.Bold)
    }
}

/** One column: heading, four posters two by two, then See all */
@Composable
private fun RequestDoorColumn(
    door: RequestSearchDoor,
    items: List<DiscoverItem>,
    cardWidth: Dp,
    onOpen: (DiscoverItem) -> Unit,
    onMenu: (DiscoverItem) -> Unit,
    onFocus: (DiscoverItem) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        RequestDoorHeading(door, items.size)
        if (items.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(cardWidth / POSTER_RATIO)
                        .background(BenflixColors.Raised.copy(alpha = .5f), RoundedCornerShape(10.dp)),
            ) {
                Text("None", color = BenflixColors.Muted, fontSize = BenflixType.small, fontWeight = FontWeight.Bold)
            }
            return@Column
        }
        items.take(4).chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { item ->
                    RequestPoster(
                        item = item,
                        width = cardWidth,
                        showName = false,
                        subtitle = null,
                        cornerNote = null,
                        onClick = { onOpen(item) },
                        onLongClick = { onMenu(item) },
                        onFocus = { onFocus(item) },
                    )
                }
            }
        }
        if (items.size > 4) {
            val shape = RoundedCornerShape(10.dp)
            Surface(
                onClick = onSeeAll,
                shape = ClickableSurfaceDefaults.shape(shape),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = BenflixColors.Raised,
                        contentColor = BenflixColors.Text,
                        focusedContainerColor = BenflixColors.Raised,
                        focusedContentColor = BenflixColors.Text,
                    ),
                border = doorFocusBorder(shape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Text(
                    text = "See all ${items.size}",
                    fontSize = BenflixType.small,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** See all: one column's whole list as a grid */
@Composable
private fun RequestSeeAllGrid(
    door: RequestSearchDoor,
    items: List<DiscoverItem>,
    onOpen: (DiscoverItem) -> Unit,
    onMenu: (DiscoverItem) -> Unit,
    onFocus: (DiscoverItem) -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(door) { first.tryRequestFocus() }
    Column {
        RequestDoorHeading(door, items.size)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(116.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(top = 10.dp),
        ) {
            items(items, key = { it.requestKey() }) { item ->
                RequestPoster(
                    item = item,
                    width = 116.dp,
                    showName = true,
                    subtitle = item.releaseDate?.year?.toString(),
                    cornerNote = null,
                    onClick = { onOpen(item) },
                    onLongClick = { onMenu(item) },
                    onFocus = { onFocus(item) },
                    modifier = if (item == items.firstOrNull()) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
}
