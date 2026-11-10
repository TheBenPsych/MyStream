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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.door.yearText
import com.github.damontecres.wholphin.ui.music.CoverImage
import com.github.damontecres.wholphin.ui.music.MusicButton
import com.github.damontecres.wholphin.ui.music.musicCardLine
import com.github.damontecres.wholphin.ui.music.musicCover
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

/** One door in search results */
private enum class SearchDoor(
    val title: String,
    val color: Color,
    val forKids: Boolean = true,
) {
    MOVIES("Movies", BenflixColors.Movies),
    SHOWS("Shows", BenflixColors.Shows),
    LIVE_TV("Live TV", BenflixColors.LiveTv),
    GAMES("Games", BenflixColors.Games),
    MUSIC("Music", BenflixColors.Music, forKids = false),
    ASK("Ask for it", BenflixColors.Text, forKids = false),
    PEOPLE("People", BenflixColors.Soft, forKids = false),
}

/** Something in the Ask for it door: a movie or show (Seerr), or a song or album (Tower's music requests) */
private sealed interface AskEntry {
    val key: String
    val title: String

    data class Title(
        val item: DiscoverItem,
    ) : AskEntry {
        override val key get() = "a-${item.id}"
        override val title get() = item.title.orEmpty()
    }

    data class Music(
        val item: SearchMusicAsk,
    ) : AskEntry {
        override val key get() = "m-${item.key}"
        override val title get() = item.title
    }
}

/** What the line on top describes */
private sealed interface Focused {
    data class Item(
        val item: BaseItem,
    ) : Focused

    data class Ask(
        val item: DiscoverItem,
    ) : Focused

    data class MusicAsk(
        val item: SearchMusicAsk,
    ) : Focused

    data class Game(
        val game: SearchGame,
    ) : Focused
}

private val MUSIC_KINDS = setOf(BaseItemKind.AUDIO, BaseItemKind.MUSIC_ALBUM, BaseItemKind.MUSIC_ARTIST, BaseItemKind.PLAYLIST)

/** For matching names: no capitals, accents, brackets or punctuation */
private fun plain(text: String?): String =
    java.text.Normalizer
        .normalize(text.orEmpty(), java.text.Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}"), "")
        .lowercase()
        .replace(Regex("[(\\[].*?[)\\]]"), " ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

/** 0 = the name starts with the words typed, 1 = has them, 2 = neither (closer matches go first) */
private fun closeness(
    title: String,
    query: String,
): Int {
    val t = plain(title)
    val q = plain(query)
    return when {
        q.isEmpty() -> 2
        t.startsWith(q) -> 0
        t.contains(q) -> 1
        else -> 2
    }
}

/**
 * Benflix search results (Ben, 2026-11-09): the best match described on top, then the doors side by side like
 * on Home: Movies, Shows, Live TV, Games, Music, a white "Ask for it" door (movies, shows, songs and albums
 * that aren't here yet) and People. Each door is a list you scroll down, one line per thing: its picture with
 * the name and year beside it. Doors with nothing are left out and named in one small line. Phones stack the
 * doors, five lines each, with "See all" for the rest.
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
    plainWords: String? = null,
    musicAsk: List<SearchMusicAsk> = emptyList(),
    onAskMusic: (SearchMusicAsk, String) -> Unit = { _, _ -> },
    query: String = "",
) {
    val isTv = rememberIsTv()
    val kids = KidsMode.isActive
    val library =
        remember(items) {
            mapOf(
                SearchDoor.MOVIES to items.filter { it.type == BaseItemKind.MOVIE || it.type == BaseItemKind.BOX_SET },
                SearchDoor.SHOWS to items.filter { it.type == BaseItemKind.SERIES || it.type == BaseItemKind.EPISODE },
                SearchDoor.LIVE_TV to
                    items.filter {
                        it.type == BaseItemKind.TV_CHANNEL ||
                            it.type == BaseItemKind.LIVE_TV_PROGRAM ||
                            it.type == BaseItemKind.TV_PROGRAM
                    },
                // Approved: no music on the Kids profile
                SearchDoor.MUSIC to if (kids) emptyList() else items.filter { it.type in MUSIC_KINDS },
                SearchDoor.PEOPLE to if (kids) emptyList() else items.filter { it.type == BaseItemKind.PERSON },
            )
        }
    // Only things not already on the server can be asked for. Songs and albums are checked against the
    // music found here too, so a song you already have never shows up to ask for.
    val ask =
        remember(askFor, musicAsk, library, query) {
            if (kids) return@remember emptyList<AskEntry>()
            val have =
                library[SearchDoor.MUSIC]
                    .orEmpty()
                    .map { plain(it.title) + "|" + plain(it.data.albumArtist ?: it.data.artists?.firstOrNull()) }
                    .toSet()
            val titles = askFor.filter { it.availability != SeerrAvailability.AVAILABLE }.map { AskEntry.Title(it) }
            val music =
                musicAsk
                    .filter { (plain(it.title) + "|" + plain(it.artist)) !in have }
                    .map { AskEntry.Music(it) }
            // Closest names first; movies and shows win a tie
            (titles + music).withIndex().sortedBy { (i, e) -> closeness(e.title, query) * 10_000 + i }.map { it.value }
        }
    val lines =
        remember(library, ask, games) {
            SearchDoor.entries.associateWith { door ->
                when (door) {
                    SearchDoor.GAMES -> games.map { ResultLine.Game(it) }
                    SearchDoor.ASK -> ask.map { ResultLine.Ask(it) }
                    else -> library[door].orEmpty().map { ResultLine.Item(it) }
                }
            }
        }
    val shown = SearchDoor.entries.filter { lines[it].orEmpty().isNotEmpty() }
    val empty = SearchDoor.entries.filter { lines[it].orEmpty().isEmpty() && (!kids || it.forKids) }
    val best = remember(lines) { shown.firstOrNull()?.let { lines[it]?.firstOrNull() } }
    var focused by remember(items) { mutableStateOf(best) }
    var seeAll by remember { mutableStateOf<SearchDoor?>(null) }
    var asking by remember { mutableStateOf<SearchMusicAsk?>(null) }
    // Holding OK (or a long press) on a song opens its More menu (Ben also gets "Delete and find again" there)
    var songMenu by remember { mutableStateOf<BaseItem?>(null) }
    // Only songs: everything else keeps its usual press, even when OK is held
    val more: (ResultLine) -> (() -> Unit)? = { line ->
        if (line is ResultLine.Item && line.item.type == BaseItemKind.AUDIO) ({ songMenu = line.item }) else null
    }
    songMenu?.let { song ->
        com.github.damontecres.wholphin.ui.music.SongMenu(
            song = song,
            liked = song.data.userData?.isFavorite == true,
            onDismiss = { songMenu = null },
        )
    }
    val open: (ResultLine) -> Unit = { line ->
        when (line) {
            is ResultLine.Item -> {
                onClickItem(line.item)
            }

            is ResultLine.Game -> {
                onClickGame(line.game)
            }

            is ResultLine.Ask -> {
                when (val e = line.entry) {
                    is AskEntry.Title -> onClickAsk(e.item)
                    is AskEntry.Music -> asking = e.item
                }
            }
        }
    }
    asking?.let { a ->
        MusicAskDialog(
            item = a,
            onAsk = { kind ->
                onAskMusic(a, kind)
                asking = null
            },
            onDismiss = { asking = null },
        )
    }

    val openAll = seeAll
    if (openAll != null) {
        BackHandler { seeAll = null }
        SeeAllList(openAll, lines[openAll].orEmpty(), onClick = open, onLongClick = more, modifier = modifier)
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .fillMaxSize()
                .then(if (isTv) Modifier else Modifier.verticalScroll(rememberScrollState()))
                .padding(start = 16.dp, end = 16.dp, bottom = if (isTv) 0.dp else 40.dp),
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
        // Benflix "Search in plain words": what Benflix understood from the words
        if (plainWords != null) {
            Text(
                text = "Showing: $plainWords",
                color = BenflixColors.Soft,
                fontSize = BenflixType.body,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        FocusLine(focused = (focused ?: best)?.toFocused(), isTv = isTv)
        // TV: the doors that sit past the right edge, named so nobody misses them (Ben, 2026-11-09)
        val doorsRow = rememberLazyListState()
        val offRight by remember(shown) {
            derivedStateOf {
                val info = doorsRow.layoutInfo
                val lastWhole =
                    info.visibleItemsInfo
                        .lastOrNull { it.offset + it.size <= info.viewportEndOffset + 1 }
                        ?.index ?: -1
                if (info.visibleItemsInfo.isEmpty()) emptyList() else shown.drop(lastWhole + 1)
            }
        }
        // Doors with nothing take one small line instead of a whole door
        val showEmpty = empty.isNotEmpty() && shown.isNotEmpty()
        val showMore = isTv && offRight.isNotEmpty()
        if (showEmpty || showMore) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (showEmpty) {
                    Text("Nothing in", color = BenflixColors.Muted, fontSize = BenflixType.small, fontWeight = FontWeight.Bold)
                    empty.forEach { door ->
                        Box(Modifier.size(8.dp).background(BenflixColors.Line, RoundedCornerShape(2.dp)))
                        Text(door.title, color = BenflixColors.Muted, fontSize = BenflixType.small)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (showMore) {
                    Text("More to the right:", color = BenflixColors.Soft, fontSize = BenflixType.small, fontWeight = FontWeight.Bold)
                    offRight.forEach { door ->
                        Box(Modifier.size(8.dp).background(door.color, RoundedCornerShape(2.dp)))
                        Text(door.title, color = BenflixColors.Text, fontSize = BenflixType.small, fontWeight = FontWeight.Bold)
                    }
                    Text("›", color = BenflixColors.Text, fontSize = BenflixType.body, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (isTv) {
            // Doors share the width (narrower than before, now the keyboard is tucked away, so five fit). With
            // more doors than fit, the next one peeks in at the right edge and the line above names the rest.
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val gap = 14.dp
                val count = shown.size.coerceAtLeast(1)
                val fits = ((maxWidth + gap) / (MIN_DOOR_WIDTH + gap)).toInt().coerceAtLeast(1)
                val doorWidth =
                    if (count <= fits) {
                        (maxWidth - gap * (count - 1)) / count
                    } else if (fits < 2) {
                        maxWidth * 0.8f
                    } else {
                        // (fits - 1) whole doors and a good slice of the next one
                        (maxWidth - gap * (fits - 1)) / (fits - 1 + DOOR_PEEK)
                    }
                LazyRow(
                    state = doorsRow,
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(shown, key = { _, d -> d.name }) { i, door ->
                        val list = lines[door].orEmpty()
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier =
                                Modifier
                                    .width(doorWidth)
                                    .fillParentMaxHeight()
                                    .then(
                                        if (door == SearchDoor.ASK) {
                                            Modifier
                                                .background(
                                                    BenflixColors.Text.copy(alpha = .06f),
                                                    RoundedCornerShape(10.dp),
                                                ).padding(6.dp)
                                        } else {
                                            Modifier
                                        },
                                    ),
                        ) {
                            DoorHeading(door.title, door.color, list.size)
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(bottom = 40.dp, top = 2.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                itemsIndexed(list, key = { _, l -> l.key }) { index, line ->
                                    ResultLineCard(
                                        line = line,
                                        onClick = { open(line) },
                                        onLongClick = more(line),
                                        onFocus = { focused = line },
                                        modifier = if (i == 0 && index == 0) Modifier.focusRequester(focusRequester) else Modifier,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            shown.forEachIndexed { i, door ->
                val list = lines[door].orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DoorHeading(door.title, door.color, list.size)
                    list.take(PHONE_LINES).forEachIndexed { index, line ->
                        ResultLineCard(
                            line = line,
                            onClick = { open(line) },
                            onLongClick = more(line),
                            onFocus = { focused = line },
                            modifier = if (i == 0 && index == 0) Modifier.focusRequester(focusRequester) else Modifier,
                        )
                    }
                    if (list.size > PHONE_LINES) SeeAllChip(list.size) { seeAll = door }
                }
            }
        }
    }
}

private const val PHONE_LINES = 5
private val MIN_DOOR_WIDTH = 168.dp

/** How much of the next door shows at the right edge when not all doors fit */
private const val DOOR_PEEK = 0.45f

/** One line in a door: something on Benflix, a game, or something to ask for */
private sealed interface ResultLine {
    val key: String

    data class Item(
        val item: BaseItem,
    ) : ResultLine {
        override val key get() = "i-${item.id}"
    }

    data class Game(
        val game: SearchGame,
    ) : ResultLine {
        override val key get() = "g-${game.key}"
    }

    data class Ask(
        val entry: AskEntry,
    ) : ResultLine {
        override val key get() = entry.key
    }
}

private fun ResultLine.toFocused(): Focused =
    when (this) {
        is ResultLine.Item -> {
            Focused.Item(item)
        }

        is ResultLine.Game -> {
            Focused.Game(game)
        }

        is ResultLine.Ask -> {
            when (val e = entry) {
                is AskEntry.Title -> Focused.Ask(e.item)
                is AskEntry.Music -> Focused.MusicAsk(e.item)
            }
        }
    }

/** The small words under a name: "Movie · 2014", "Song · Ariana Grande", "NES" */
private fun subLine(line: ResultLine): String =
    when (line) {
        is ResultLine.Item -> {
            val item = line.item
            if (item.type in MUSIC_KINDS) {
                listOfNotNull(kindOf(item.type), musicCardLine(item)).joinToString(" · ")
            } else {
                listOf(kindOf(item.type), yearText(item).orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
            }
        }

        is ResultLine.Game -> {
            line.game.kind
        }

        is ResultLine.Ask -> {
            when (val e = line.entry) {
                is AskEntry.Title -> {
                    listOfNotNull(
                        if (e.item.type == SeerrItemType.TV) "Show" else "Movie",
                        e.item.releaseDate
                            ?.year
                            ?.toString(),
                    ).joinToString(" · ")
                }

                is AskEntry.Music -> {
                    (if (e.item.kind == "album") "Album" else "Song") + " · " + e.item.artist
                }
            }
        }
    }

/** One line: the picture (a poster, or a square cover for music) with the name and year beside it */
@Composable
private fun ResultLineCard(
    line: ResultLine,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    val imageUrlService = LocalImageUrlService.current
    val density = LocalDensity.current
    val square =
        when (line) {
            is ResultLine.Item -> line.item.type in MUSIC_KINDS || line.item.type == BaseItemKind.TV_CHANNEL
            is ResultLine.Ask -> line.entry is AskEntry.Music
            is ResultLine.Game -> false
        }
    val picWidth = if (square) 52.dp else 40.dp
    val picHeight = if (square) 52.dp else 60.dp
    val px = with(density) { picHeight.roundToPx() }
    val name: String
    val url: String?
    var asked = false
    when (line) {
        is ResultLine.Item -> {
            name = line.item.name.orEmpty()
            url =
                remember(line.item.id) {
                    if (line.item.type in MUSIC_KINDS) {
                        imageUrlService.musicCover(line.item, px)
                    } else {
                        imageUrlService.getItemImageUrl(line.item, ImageType.PRIMARY, fillHeight = px, useSeriesForPrimary = true)
                    }
                }
        }

        is ResultLine.Game -> {
            name = line.game.title
            url = null
        }

        is ResultLine.Ask -> {
            when (val e = line.entry) {
                is AskEntry.Title -> {
                    name = e.item.title.orEmpty()
                    url = e.item.posterUrl
                    asked = e.item.availability == SeerrAvailability.PENDING ||
                        e.item.availability == SeerrAvailability.PROCESSING ||
                        e.item.availability == SeerrAvailability.PARTIALLY_AVAILABLE
                }

                is AskEntry.Music -> {
                    name = e.item.title
                    url = e.item.cover
                    asked = e.item.asked != null
                }
            }
        }
    }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.Raised,
                focusedContentColor = BenflixColors.Text,
            ),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) onFocus() },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(5.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(width = picWidth, height = picHeight)
                        .clip(RoundedCornerShape(6.dp))
                        .background(BenflixColors.Raised),
            ) {
                if (line is ResultLine.Game) {
                    GamePicture(line.game)
                } else if (url != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = BenflixColors.Text,
                    fontSize = BenflixType.small,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = subLine(line)
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        color = BenflixColors.Muted,
                        fontSize = BenflixType.tag,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (asked) {
                    Text(
                        text = "Asked",
                        color = BenflixColors.Tray,
                        fontSize = BenflixType.tag,
                        fontWeight = FontWeight.ExtraBold,
                        modifier =
                            Modifier
                                .background(BenflixColors.Text, RoundedCornerShape(6.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

/** A game's box art (Tower's needs a token), or its colour square */
@Composable
private fun GamePicture(game: SearchGame) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
    } else if (game.colors.isNotEmpty()) {
        com.github.damontecres.wholphin.ui.games
            .GameIconSquare(game.colors, game.icon, size = 26.dp)
    }
}

/** "See all" on phones: one door's whole list (Back returns to the doors) */
@Composable
private fun SeeAllList(
    door: SearchDoor,
    lines: List<ResultLine>,
    onClick: (ResultLine) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (ResultLine) -> (() -> Unit)? = { null },
) {
    val first = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { first.tryRequestFocus() }
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        DoorHeading(door.title, door.color, lines.size)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp),
        ) {
            itemsIndexed(lines, key = { _, l -> l.key }) { index, line ->
                ResultLineCard(
                    line = line,
                    onClick = { onClick(line) },
                    onLongClick = onLongClick(line),
                    onFocus = {},
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                )
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

                is Focused.MusicAsk -> {
                    null
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
                            (if (item.type in MUSIC_KINDS) musicCardLine(item) else yearText(item)).orEmpty(),
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

                    is Focused.MusicAsk -> {
                        val item = focused.item
                        listOf(
                            "Not on Benflix yet · Ask for it",
                            item.title,
                            (if (item.kind == "album") "Album" else "Song") + " · " + item.artist,
                            "",
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
        BaseItemKind.AUDIO -> "Song"
        BaseItemKind.MUSIC_ALBUM -> "Album"
        BaseItemKind.MUSIC_ARTIST -> "Artist"
        BaseItemKind.PLAYLIST -> "Playlist"
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

/** Pressing a song or album in Ask for it: ask for just the song, or the whole album */
@Composable
private fun MusicAskDialog(
    item: SearchMusicAsk,
    onAsk: (kind: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    // Benflix: the touch Dialog, so phone taps reach the buttons (a plain Dialog is its own window)
    com.github.damontecres.wholphin.ui.touch.Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .background(BenflixColors.Tray, RoundedCornerShape(16.dp))
                    .padding(20.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverImage(url = item.cover, modifier = Modifier.size(72.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "NOT ON BENFLIX YET",
                        color = BenflixColors.Muted,
                        fontSize = BenflixType.tag,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        item.title,
                        color = BenflixColors.Text,
                        fontSize = BenflixType.body,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(item.artist, color = BenflixColors.Soft, fontSize = BenflixType.small, maxLines = 1)
                    if (item.kind == "song" && item.albumTitle.isNotBlank() && plain(item.albumTitle) != plain(item.title)) {
                        Text(
                            "Album: ${item.albumTitle}",
                            color = BenflixColors.Muted,
                            fontSize = BenflixType.small,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (item.asked != null) {
                Text("Already asked for: ${item.asked}", color = BenflixColors.Soft, fontSize = BenflixType.small)
            }
            if (item.kind == "song") {
                MusicButton(
                    label = "Ask for this song",
                    icon = null,
                    primary = true,
                    onClick = { onAsk("song") },
                    modifier = Modifier.fillMaxWidth().focusRequester(first),
                )
                if (item.albumId != 0L && item.albumTitle.isNotBlank() && plain(item.albumTitle) != plain(item.title)) {
                    MusicButton(
                        label = "Ask for the whole album",
                        icon = null,
                        onClick = { onAsk("album") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                MusicButton(
                    label = "Ask for this album",
                    icon = null,
                    primary = true,
                    onClick = { onAsk("album") },
                    modifier = Modifier.fillMaxWidth().focusRequester(first),
                )
            }
            MusicButton(label = "Cancel", icon = null, onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { first.tryRequestFocus() }
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
