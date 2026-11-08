package com.github.damontecres.wholphin.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.door.DoorPoster
import com.github.damontecres.wholphin.ui.door.POSTER_RATIO
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.door.yearText
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

/** One door column in search results */
private enum class SearchDoor(
    val title: String,
    val color: Color,
) {
    MOVIES("Movies", BenflixColors.Movies),
    SHOWS("Shows", BenflixColors.Shows),
    LIVE_TV("Live TV", BenflixColors.LiveTv),
    GAMES("Games", BenflixColors.Games),
    ASK("Ask for it", BenflixColors.Text),
}

/** What the line on top describes */
private sealed interface Focused {
    data class Item(
        val item: BaseItem,
    ) : Focused

    data class Ask(
        val item: DiscoverItem,
    ) : Focused

    data class Game(
        val game: SearchGame,
    ) : Focused
}

/**
 * Benflix redesign search results: the best match described on top (with its picture fading in
 * behind), then door columns of same-size poster cards, two by two: Movies, Shows, Live TV and a white
 * "Ask for it" door for titles that aren't here yet. People sit in a row underneath. A door with
 * nothing is shown greyed with "None"; "See all" opens that door's whole list. Phones stack the doors.
 */
@Composable
fun SearchDoorResults(
    items: List<BaseItem>,
    askFor: List<DiscoverItem>,
    focusRequester: FocusRequester,
    onClickItem: (BaseItem) -> Unit,
    onClickAsk: (DiscoverItem) -> Unit,
    modifier: Modifier = Modifier,
    didYouMean: String? = null,
    games: List<SearchGame> = emptyList(),
    onClickGame: (SearchGame) -> Unit = {},
) {
    val isTv = rememberIsTv()
    val kids = KidsMode.isActive
    val doors =
        remember(items) {
            val byDoor =
                mapOf(
                    SearchDoor.MOVIES to items.filter { it.type == BaseItemKind.MOVIE || it.type == BaseItemKind.BOX_SET },
                    SearchDoor.SHOWS to items.filter { it.type == BaseItemKind.SERIES || it.type == BaseItemKind.EPISODE },
                    SearchDoor.LIVE_TV to
                        items.filter {
                            it.type == BaseItemKind.TV_CHANNEL ||
                                it.type == BaseItemKind.LIVE_TV_PROGRAM ||
                                it.type == BaseItemKind.TV_PROGRAM
                        },
                )
            byDoor
        }
    // Only titles not already on the server can be asked for
    val ask = remember(askFor) { askFor.filter { it.availability != SeerrAvailability.AVAILABLE } }
    val people = remember(items) { items.filter { it.type == BaseItemKind.PERSON } }
    val best = remember(doors, ask) { doors.values.firstNotNullOfOrNull { it.firstOrNull() } }
    var focused by remember(items) { mutableStateOf<Focused?>(best?.let { Focused.Item(it) }) }
    var seeAll by remember { mutableStateOf<SearchDoor?>(null) }

    val openAll = seeAll
    if (openAll != null) {
        BackHandler { seeAll = null }
        SeeAllGrid(
            title = openAll.title,
            items = if (openAll == SearchDoor.ASK || openAll == SearchDoor.GAMES) emptyList() else doors[openAll].orEmpty(),
            ask = if (openAll == SearchDoor.ASK) ask else emptyList(),
            games = if (openAll == SearchDoor.GAMES) games else emptyList(),
            onClickGame = onClickGame,
            onClickItem = onClickItem,
            onClickAsk = onClickAsk,
            modifier = modifier,
        )
        return
    }

    val firstDoor =
        remember(doors, ask, games) {
            SearchDoor.entries.firstOrNull { d ->
                when (d) {
                    SearchDoor.ASK -> ask.isNotEmpty()
                    SearchDoor.GAMES -> games.isNotEmpty()
                    else -> doors[d].orEmpty().isNotEmpty()
                }
            }
        }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val cardWidth = if (isTv) ((maxWidth - 32.dp - 18.dp * 4) / 5 - 10.dp) / 2 else 104.dp
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 40.dp),
        ) {
            // Approved: a typo still finds things, and says what it found
            if (didYouMean != null) {
                Text(
                    text = "Did you mean $didYouMean?",
                    color = BenflixColors.Soft,
                    fontSize = BenflixType.body,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            FocusLine(focused = focused, isTv = isTv)
            val shownDoors = SearchDoor.entries.filter { it != SearchDoor.ASK || !kids }
            if (isTv) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxWidth()) {
                    shownDoors.forEach { door ->
                        DoorColumn(
                            door = door,
                            items = doors[door].orEmpty(),
                            ask = if (door == SearchDoor.ASK) ask else emptyList(),
                            games = if (door == SearchDoor.GAMES) games else emptyList(),
                            onClickGame = onClickGame,
                            cardWidth = cardWidth,
                            first = if (door == firstDoor) focusRequester else null,
                            onFocus = { focused = it },
                            onClickItem = onClickItem,
                            onClickAsk = onClickAsk,
                            onSeeAll = { seeAll = door },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                shownDoors.forEach { door ->
                    PhoneDoorRow(
                        door = door,
                        items = doors[door].orEmpty(),
                        ask = if (door == SearchDoor.ASK) ask else emptyList(),
                        games = if (door == SearchDoor.GAMES) games else emptyList(),
                        onClickGame = onClickGame,
                        cardWidth = cardWidth,
                        first = if (door == firstDoor) focusRequester else null,
                        onClickItem = onClickItem,
                        onClickAsk = onClickAsk,
                        onSeeAll = { seeAll = door },
                    )
                }
            }
            if (people.isNotEmpty() && !kids) {
                DoorHeading("People", BenflixColors.Soft, count = people.size)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 16.dp)) {
                    items(people, key = { "p-${it.id}" }) { person ->
                        DoorPoster(
                            item = person,
                            width = cardWidth,
                            showName = true,
                            onClick = { onClickItem(person) },
                            onFocus = { focused = Focused.Item(person) },
                        )
                    }
                }
            }
        }
    }
}

/** The line on top: what the focused card is, with its picture fading in from the right */
@Composable
private fun FocusLine(
    focused: Focused?,
    isTv: Boolean,
) {
    val imageUrlService = LocalImageUrlService.current
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(if (isTv) 150.dp else 96.dp)
                .clip(RoundedCornerShape(10.dp)),
    ) {
        val picture =
            when (focused) {
                is Focused.Item -> {
                    remember(
                        focused.item.id,
                    ) { imageUrlService.getItemImageUrl(focused.item, ImageType.BACKDROP, fillWidth = 960) }
                }

                is Focused.Ask -> {
                    focused.item.backDropUrl
                }

                // Box art from Tower needs a token, so games show words only here
                is Focused.Game -> {
                    null
                }

                null -> {
                    null
                }
            }
        Crossfade(targetState = picture, label = "search-picture", modifier = Modifier.fillMaxSize()) { url ->
            if (url != null) {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopEnd,
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .fillMaxWidth(.6f),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    0f to BenflixColors.Tray,
                                    0.45f to BenflixColors.Tray,
                                    0.8f to BenflixColors.Tray.copy(alpha = .4f),
                                    1f to BenflixColors.Tray.copy(alpha = .1f),
                                ),
                            ),
                    )
                }
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(if (isTv) .55f else .9f)
                    .padding(vertical = 8.dp),
        ) {
            val (kind, title, meta, story) =
                when (focused) {
                    is Focused.Item -> {
                        val item = focused.item
                        listOf(
                            kindOf(item.type),
                            item.name.orEmpty(),
                            yearText(item).orEmpty(),
                            item.data.overview.orEmpty(),
                        )
                    }

                    is Focused.Ask -> {
                        val item = focused.item
                        listOf(
                            "Not on Benflix yet · Ask for it",
                            item.title.orEmpty(),
                            item.releaseDate
                                ?.year
                                ?.toString()
                                .orEmpty(),
                            item.overview.orEmpty(),
                        )
                    }

                    is Focused.Game -> {
                        listOf("Game · ${focused.game.kind}", focused.game.title, "", "")
                    }

                    null -> {
                        listOf("", "Nothing found", "", "")
                    }
                }
            if (kind.isNotEmpty()) {
                Text(
                    kind.uppercase(),
                    color = BenflixColors.Muted,
                    fontSize = BenflixType.tag,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                )
            }
            Text(
                text = title,
                color = BenflixColors.Text,
                fontSize = if (isTv) BenflixType.pageTitle else BenflixType.rowName,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    color = BenflixColors.Soft,
                    fontSize = BenflixType.small,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            if (story.isNotEmpty()) {
                Text(
                    text = story,
                    color = BenflixColors.Soft,
                    fontSize = BenflixType.small,
                    maxLines = if (isTv) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun kindOf(type: BaseItemKind): String =
    when (type) {
        BaseItemKind.MOVIE -> "Movie"
        BaseItemKind.BOX_SET -> "Collection"
        BaseItemKind.SERIES -> "Show"
        BaseItemKind.EPISODE -> "Episode"
        BaseItemKind.TV_CHANNEL -> "Live channel"
        BaseItemKind.LIVE_TV_PROGRAM, BaseItemKind.TV_PROGRAM -> "On live TV"
        BaseItemKind.PERSON -> "Person"
        else -> ""
    }

@Composable
private fun DoorHeading(
    title: String,
    color: Color,
    count: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(width = 4.dp, height = 16.dp)
                .background(if (count == 0) BenflixColors.Line else color, RoundedCornerShape(6.dp)),
        )
        Text(
            text = title,
            color = if (count == 0) BenflixColors.Muted else BenflixColors.Text,
            fontSize = BenflixType.body,
            fontWeight = FontWeight.ExtraBold,
        )
        if (count > 0) Text("$count", color = BenflixColors.Muted, fontSize = BenflixType.small, fontWeight = FontWeight.Bold)
    }
}

/** A TV door column: heading, four cards two by two, then See all */
@Composable
private fun DoorColumn(
    door: SearchDoor,
    items: List<BaseItem>,
    ask: List<DiscoverItem>,
    games: List<SearchGame>,
    onClickGame: (SearchGame) -> Unit,
    cardWidth: Dp,
    first: FocusRequester?,
    onFocus: (Focused) -> Unit,
    onClickItem: (BaseItem) -> Unit,
    onClickAsk: (DiscoverItem) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val count =
        when (door) {
            SearchDoor.ASK -> ask.size
            SearchDoor.GAMES -> games.size
            else -> items.size
        }
    val askDoor = door == SearchDoor.ASK
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            modifier
                .then(
                    if (askDoor &&
                        count > 0
                    ) {
                        Modifier.background(BenflixColors.Text.copy(alpha = .06f), RoundedCornerShape(10.dp))
                    } else {
                        Modifier
                    },
                ).padding(if (askDoor) 6.dp else 0.dp),
    ) {
        DoorHeading(door.title, door.color, count)
        if (count == 0) {
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
        val cards: List<@Composable (Modifier) -> Unit> =
            if (door == SearchDoor.GAMES) {
                games.take(4).map { game ->
                    { mod: Modifier ->
                        SearchGameCard(
                            game,
                            cardWidth,
                            onClick = { onClickGame(game) },
                            onFocus = { onFocus(Focused.Game(game)) },
                            modifier = mod,
                        )
                    }
                }
            } else if (askDoor) {
                ask.take(4).map { item ->
                    { mod: Modifier ->
                        AskCard(item, cardWidth, onClick = { onClickAsk(item) }, onFocus = { onFocus(Focused.Ask(item)) }, modifier = mod)
                    }
                }
            } else {
                items.take(4).map { item ->
                    { mod: Modifier ->
                        DoorPoster(
                            item = item,
                            width = cardWidth,
                            showName = false,
                            onClick = { onClickItem(item) },
                            onFocus = { onFocus(Focused.Item(item)) },
                            modifier = mod,
                        )
                    }
                }
            }
        cards.chunked(2).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { index, card ->
                    card(if (rowIndex == 0 && index == 0 && first != null) Modifier.focusRequester(first) else Modifier)
                }
            }
        }
        if (count > 4) SeeAllChip(count, onSeeAll)
    }
}

/** Phones: each door is a colour strip with a row of posters */
@Composable
private fun PhoneDoorRow(
    door: SearchDoor,
    items: List<BaseItem>,
    ask: List<DiscoverItem>,
    games: List<SearchGame>,
    onClickGame: (SearchGame) -> Unit,
    cardWidth: Dp,
    first: FocusRequester?,
    onClickItem: (BaseItem) -> Unit,
    onClickAsk: (DiscoverItem) -> Unit,
    onSeeAll: () -> Unit,
) {
    val count =
        when (door) {
            SearchDoor.ASK -> ask.size
            SearchDoor.GAMES -> games.size
            else -> items.size
        }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DoorHeading(door.title, door.color, count)
        if (count == 0) {
            Text("None", color = BenflixColors.Muted, fontSize = BenflixType.small)
            return@Column
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 16.dp)) {
            if (door == SearchDoor.GAMES) {
                items(games.take(20), key = { "g-${it.key}" }) { game ->
                    SearchGameCard(
                        game,
                        cardWidth,
                        onClick = { onClickGame(game) },
                        onFocus = {},
                        modifier = if (first != null && game == games.first()) Modifier.focusRequester(first) else Modifier,
                    )
                }
            } else if (door == SearchDoor.ASK) {
                items(ask.take(20), key = { "a-${it.id}" }) { item ->
                    AskCard(
                        item,
                        cardWidth,
                        onClick = { onClickAsk(item) },
                        onFocus = {},
                        modifier = if (first != null && item == ask.first()) Modifier.focusRequester(first) else Modifier,
                    )
                }
            } else {
                items(items.take(20), key = { "i-${it.id}" }) { item ->
                    DoorPoster(
                        item = item,
                        width = cardWidth,
                        showName = true,
                        onClick = { onClickItem(item) },
                        modifier = if (first != null && item == items.first()) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
            if (count > 20) item { SeeAllChip(count, onSeeAll) }
        }
    }
}

@Composable
private fun SeeAllChip(
    count: Int,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.Raised,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.Text,
                focusedContentColor = BenflixColors.Tray,
            ),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = "See all $count",
            fontSize = BenflixType.small,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/** A title that isn't on Benflix yet: its poster with a small "Asked" pill when someone already asked */
@Composable
private fun AskCard(
    item: DiscoverItem,
    width: Dp,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
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
        modifier =
            modifier
                .size(width = width, height = width / POSTER_RATIO)
                .onFocusChanged { if (it.isFocused) onFocus() },
    ) {
        Box(Modifier.fillMaxSize()) {
            if (item.posterUrl != null) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = item.title.orEmpty(),
                    color = BenflixColors.Soft,
                    fontSize = BenflixType.tag,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(8.dp),
                )
            }
            val asked =
                item.availability == SeerrAvailability.PENDING ||
                    item.availability == SeerrAvailability.PROCESSING ||
                    item.availability == SeerrAvailability.PARTIALLY_AVAILABLE
            if (asked) {
                Text(
                    text = "Asked",
                    color = BenflixColors.Tray,
                    fontSize = BenflixType.tag,
                    fontWeight = FontWeight.ExtraBold,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(BenflixColors.Text, RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
    }
}

/** "See all": one door's whole list as a grid (Back returns to the doors) */
@Composable
private fun SeeAllGrid(
    title: String,
    items: List<BaseItem>,
    ask: List<DiscoverItem>,
    onClickItem: (BaseItem) -> Unit,
    onClickAsk: (DiscoverItem) -> Unit,
    games: List<SearchGame> = emptyList(),
    onClickGame: (SearchGame) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isTv = rememberIsTv()
    val first = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { first.tryRequestFocus() }
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            title,
            color = BenflixColors.Text,
            fontSize = BenflixType.rowName,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = if (isTv) 8 else ((maxWidth / 110.dp).toInt()).coerceIn(3, 8)
            val width = (maxWidth - 12.dp * (columns - 1) - 8.dp) / columns
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 40.dp, end = 8.dp),
            ) {
                if (games.isNotEmpty()) {
                    items(games, key = { "g-${it.key}" }) { game ->
                        SearchGameCard(
                            game,
                            width,
                            onClick = { onClickGame(game) },
                            onFocus = {},
                            modifier = if (game == games.first()) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                } else if (ask.isNotEmpty()) {
                    items(ask, key = { "a-${it.id}" }) { item ->
                        AskCard(
                            item,
                            width,
                            onClick = { onClickAsk(item) },
                            onFocus = {},
                            modifier = if (item == ask.first()) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                } else {
                    items(items, key = { "i-${it.id}" }) { item ->
                        DoorPoster(
                            item = item,
                            width = width,
                            showName = !isTv,
                            onClick = { onClickItem(item) },
                            modifier = if (item == items.first()) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

/** A game in the Games door: its box art, or a dark card with its colour square and name */
@Composable
private fun SearchGameCard(
    game: SearchGame,
    width: Dp,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        onClick = onClick,
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
        modifier =
            modifier
                .size(width = width, height = width / POSTER_RATIO)
                .onFocusChanged { if (it.isFocused) onFocus() },
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(8.dp),
            ) {
                if (game.colors.isNotEmpty()) {
                    com.github.damontecres.wholphin.ui.games
                        .GameIconSquare(game.colors, game.icon, size = 22.dp)
                }
                Text(
                    text = game.title,
                    color = BenflixColors.Text,
                    fontSize = BenflixType.tag,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(game.kind, color = BenflixColors.Muted, fontSize = BenflixType.tag, maxLines = 1)
            }
            if (game.coverUrl != null) {
                AsyncImage(
                    model =
                        remember(game.coverUrl) {
                            ImageRequest
                                .Builder(context)
                                .data(game.coverUrl)
                                .apply {
                                    game.token?.let { httpHeaders(NetworkHeaders.Builder().set("X-Benflix-Token", it).build()) }
                                }.build()
                        },
                    contentDescription = game.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
