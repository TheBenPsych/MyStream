package com.github.damontecres.wholphin.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.github.damontecres.wholphin.services.BookTile
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.BenflixIcon
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
    /** The six door cards (3 by 2); People and Ask for it sit one Down below them */
    val inGrid: Boolean = true,
) {
    MOVIES("Movies", BenflixColors.Movies),
    SHOWS("Shows", BenflixColors.Shows),
    MUSIC("Music", BenflixColors.Music, forKids = false),
    LIVE_TV("Live TV", BenflixColors.LiveTv),
    GAMES("Games", BenflixColors.Games),
    BOOKS("Books", BenflixColors.Books, forKids = false),
    ASK("Ask for it", BenflixColors.Text, forKids = false, inGrid = false),
    PEOPLE("People", BenflixColors.Soft, forKids = false, inGrid = false),
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
 * Benflix search results, Redesign 2 (Ben, 2026-11-10, "door cards"): each door is a card like a small Home
 * door, with a thin line of the door's colour on top, its name and count, then its best few results with bigger
 * pictures in their own shape (posters, square covers, book covers) and See all for the rest. Six cards sit in a
 * neat 3 by 2 grid (Movies, Shows, Music, Live TV, Games, Books); People and the white Ask for it door are one
 * Down below. Doors with nothing are left out and named in one small line. Phones stack the cards.
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
    books: List<BookTile> = emptyList(),
    onClickBook: (BookTile) -> Unit = {},
) {
    val isTv = rememberIsTv()
    val kids = KidsMode.isActive
    val library =
        remember(items) {
            mapOf(
                SearchDoor.MOVIES to items.filter { it.type == BaseItemKind.MOVIE || it.type == BaseItemKind.BOX_SET },
                SearchDoor.SHOWS to items.filter { it.type == BaseItemKind.SERIES || it.type == BaseItemKind.EPISODE },
                // Kids search only ever finds kids titles (kd-p-search): live programmes have no kids rating, so none
                SearchDoor.LIVE_TV to
                    if (kids) {
                        emptyList()
                    } else {
                        items.filter {
                            it.type == BaseItemKind.TV_CHANNEL ||
                                it.type == BaseItemKind.LIVE_TV_PROGRAM ||
                                it.type == BaseItemKind.TV_PROGRAM
                        }
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
        remember(library, ask, games, books) {
            SearchDoor.entries.associateWith { door ->
                when (door) {
                    SearchDoor.GAMES -> games.map { ResultLine.Game(it) }
                    SearchDoor.BOOKS -> if (kids) emptyList() else books.map { ResultLine.Book(it) }
                    SearchDoor.ASK -> ask.map { ResultLine.Ask(it) }
                    else -> library[door].orEmpty().map { ResultLine.Item(it) }
                }
            }
        }
    val shown = SearchDoor.entries.filter { lines[it].orEmpty().isNotEmpty() }
    val empty = SearchDoor.entries.filter { lines[it].orEmpty().isEmpty() && (!kids || it.forKids) }
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

            is ResultLine.Book -> {
                onClickBook(line.book)
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

    val grid = shown.filter { it.inGrid }
    val below = shown.filter { !it.inGrid }
    // Approved: 2 results in each door card on TV (bigger pictures, nothing cut), 3 on phones. On TV the wide
    // Ask for it card shows 4 (two across) and People 2, so the two cards line up
    val cardLines = if (isTv) TV_CARD_LINES else PHONE_LINES
    val belowLines: (SearchDoor) -> Int = {
        if (!isTv) {
            PHONE_LINES
        } else if (it == SearchDoor.ASK) {
            TV_ASK_LINES
        } else {
            TV_CARD_LINES
        }
    }
    val firstDoor = (grid + below).firstOrNull()
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = if (isTv) 4.dp else 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
    ) {
        // Approved: a typo still finds things, and says what it found
        if (didYouMean != null) {
            Text(
                text = "Did you mean $didYouMean?",
                color = BenflixColors.Soft,
                fontSize = BenflixType.body,
                fontWeight = FontWeight.Bold,
            )
        }
        // Benflix "Search in plain words": what Benflix understood from the words
        if (plainWords != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "SEARCHING BY IDEA",
                    color = BenflixColors.Tray,
                    fontSize = BenflixType.tag,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    modifier =
                        Modifier
                            .background(BenflixColors.Text, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Text(
                    text = plainWords,
                    color = BenflixColors.Soft,
                    fontSize = BenflixType.body,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Doors with nothing take one small line instead of a whole card
        if (empty.isNotEmpty() && shown.isNotEmpty()) {
            Text(
                text = "Nothing in " + empty.joinToString(" · ") { it.title },
                color = BenflixColors.Muted,
                fontSize = BenflixType.small,
                fontWeight = FontWeight.SemiBold,
            )
        }
        val perRow = if (isTv) 3 else 1
        grid.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { door ->
                    DoorCard(
                        door = door,
                        lines = lines[door].orEmpty(),
                        max = cardLines,
                        onOpen = open,
                        onMore = more,
                        onSeeAll = { seeAll = door },
                        firstFocus = if (door == firstDoor) focusRequester else null,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (below.isNotEmpty()) {
            if (isTv && grid.isNotEmpty()) {
                Text(
                    text = "Down for " + below.reversed().joinToString(" and ") { it.title } + " ›",
                    color = BenflixColors.Muted,
                    fontSize = BenflixType.small,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                // Ask for it first and wider, People beside it (stacked on phones)
                val order = below.sortedBy { if (it == SearchDoor.ASK) 0 else 1 }
                if (isTv) {
                    order.forEach { door ->
                        DoorCard(
                            door = door,
                            lines = lines[door].orEmpty(),
                            max = belowLines(door),
                            onOpen = open,
                            onMore = more,
                            onSeeAll = { seeAll = door },
                            firstFocus = if (door == firstDoor) focusRequester else null,
                            modifier = Modifier.weight(if (door == SearchDoor.ASK && order.size > 1) 2f else 1f),
                        )
                    }
                    if (order.size == 1) Spacer(Modifier.weight(if (order[0] == SearchDoor.ASK) 1f else 2f))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                        order.forEach { door ->
                            DoorCard(
                                door = door,
                                lines = lines[door].orEmpty(),
                                max = belowLines(door),
                                onOpen = open,
                                onMore = more,
                                onSeeAll = { seeAll = door },
                                firstFocus = if (door == firstDoor) focusRequester else null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One door's card: thin door-colour line on top, name, count and See all, then the best few results */
@Composable
private fun DoorCard(
    door: SearchDoor,
    lines: List<ResultLine>,
    max: Int,
    onOpen: (ResultLine) -> Unit,
    onMore: (ResultLine) -> (() -> Unit)?,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    val ask = door == SearchDoor.ASK
    Column(
        modifier =
            modifier
                .clip(shape)
                .background(if (ask) BenflixColors.Text.copy(alpha = .08f) else BenflixColors.Panel),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(door.color),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = door.title,
                    color = BenflixColors.Text,
                    fontSize = BenflixType.body,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text("${lines.size}", color = BenflixColors.Muted, fontSize = BenflixType.small, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (lines.size > max) SeeAllChip(onClick = onSeeAll)
            }
            val few = lines.take(max)
            if (door == SearchDoor.ASK && max > PHONE_LINES && rememberIsTv()) {
                // TV: the wide Ask for it card shows its results two across
                few.chunked(2).forEachIndexed { r, pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEachIndexed { c, line ->
                            ResultLineCard(
                                line = line,
                                onClick = { onOpen(line) },
                                onLongClick = onMore(line),
                                modifier =
                                    Modifier.weight(1f).then(
                                        if (r == 0 && c == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
                                    ),
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                few.forEachIndexed { index, line ->
                    ResultLineCard(
                        line = line,
                        onClick = { onOpen(line) },
                        onLongClick = onMore(line),
                        modifier = if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
            }
        }
    }
}

/** Door cards on TV show 2 results each (approved), phones 3 */
private const val TV_CARD_LINES = 2
private const val TV_ASK_LINES = 4
private const val PHONE_LINES = 3

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

    data class Book(
        val book: BookTile,
    ) : ResultLine {
        override val key get() = "b-${book.id}"
    }

    data class Ask(
        val entry: AskEntry,
    ) : ResultLine {
        override val key get() = entry.key
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

        is ResultLine.Book -> {
            val b = line.book
            listOf(
                when (b.kind) {
                    "audiobook" -> "Audiobook"
                    "manga" -> "Manga"
                    "comic" -> "Comic"
                    else -> "Book"
                },
                b.author,
            ).filter { it.isNotBlank() }.joinToString(" · ")
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
            is ResultLine.Game, is ResultLine.Book -> false
        }
    // Redesign 2: bigger pictures, each in its kind's shape (posters and book covers tall, music and channels square)
    val picWidth = if (square) 64.dp else 46.dp
    val picHeight = if (square) 64.dp else 69.dp
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

        is ResultLine.Book -> {
            name = line.book.name
            url = line.book.cover
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
        modifier = modifier.fillMaxWidth(),
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
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val preview =
        remember {
            com.github.damontecres.wholphin.services
                .songPreview(context)
        }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { preview.stop() } }
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
            if (item.kind == "song" && item.id != 0L) {
                // Benflix: hear 30 seconds of the song before asking for it
                val previewState by preview.state.collectAsState()
                MusicButton(
                    label =
                        com.github.damontecres.wholphin.ui.discover
                            .previewLabel(previewState, item.id),
                    icon = if (previewState.songId == item.id) BenflixIcon.STOP else BenflixIcon.PLAY,
                    onClick = { preview.toggle(item.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
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
private fun SeeAllChip(onClick: () -> Unit) {
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
            text = "See all ›",
            fontSize = BenflixType.small,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}
