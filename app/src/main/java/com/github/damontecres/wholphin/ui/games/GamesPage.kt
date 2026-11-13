package com.github.damontecres.wholphin.ui.games

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.github.damontecres.wholphin.games.DevicePower
import com.github.damontecres.wholphin.games.DownloadedGame
import com.github.damontecres.wholphin.games.GameRef
import com.github.damontecres.wholphin.games.LocalConsole
import com.github.damontecres.wholphin.games.PhonePadActivity
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.ServerGame
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.door.DOOR_HOME
import com.github.damontecres.wholphin.ui.door.DoorFrame
import com.github.damontecres.wholphin.ui.door.DoorMemory
import com.github.damontecres.wholphin.ui.door.DoorSection
import com.github.damontecres.wholphin.ui.door.doorHome
import com.github.damontecres.wholphin.ui.door.rememberDoorFrameState
import com.github.damontecres.wholphin.ui.main.DoorWipe
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.preferences.StringInput
import com.github.damontecres.wholphin.ui.preferences.StringInputDialog
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.onFirstUserMove
import com.github.damontecres.wholphin.ui.util.scrollAsOneBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Games tab: quick built-in games, plus your own game collection from RomM on the server.
 * Redesign 2 (section 6): the page sits in the shared door frame; its side menu (chips on phones)
 * jumps to Movie puzzles, Arcade, Favourites, Consoles and Leaderboards further down the page.
 */
@Composable
fun GamesPage(
    modifier: Modifier = Modifier,
    viewModel: GamesViewModel = hiltViewModel(),
) {
    val focusRequester = remember { FocusRequester() }
    val pageScroll = rememberScrollState()
    // Ben: the Games door opens at the top, on the first card of the top row. Continue playing
    // loads in after the rest, so until you press a button or touch the page, go back up to it.
    // Opening the door again within 10 minutes comes back to the same part of the page
    val recalled = remember { DoorMemory.recall(BenflixDoor.GAMES) }
    var userMoved by remember { mutableStateOf(recalled != null && recalled.scroll > 0) }
    val isTv = rememberIsTv()
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp
    val library by viewModel.library.collectAsState()
    // Opening from Home: the door colour waits for the game list, then fades onto the page
    val libraryLoaded = library !is LibraryState.Loading
    LaunchedEffect(libraryLoaded) {
        if (libraryLoaded) DoorWipe.ready()
    }
    val starting by viewModel.starting.collectAsState()
    val board by viewModel.board.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshBoard() }
    var askAddress by remember { mutableStateOf(false) }
    val power = remember { DevicePower.get(context) }
    val prep by viewModel.localGames.prep.collectAsState()
    val favourites by viewModel.favourites.collectAsState()
    val towerRecent by viewModel.towerRecent.collectAsState()
    // Only consoles this device can play (some may be a bit slow)
    val ready = library as? LibraryState.Ready
    val playable =
        remember(ready) {
            ready?.consoles.orEmpty().mapNotNull { c ->
                val local = LocalConsole.forSlug(c.slug) ?: return@mapNotNull null
                val fit = power.fit(local.power)
                if (fit == DevicePower.Fit.TOO_MUCH) null else Triple(c, local, fit)
            }
        }
    val refs =
        remember(playable) {
            playable
                .flatMap { (c, _, _) ->
                    c.games.map {
                        it.id to GameRef(it.id, it.name, c.slug, c.name, it.coverUrl, it.size, kids = it.kids, bigKids = it.bigKids)
                    }
                }.toMap()
        }
    val recent =
        remember(refs, starting, towerRecent) {
            viewModel.recentPlayed().mapNotNull { (id, time) -> refs[id]?.let { it to time } }.take(10)
        }

    // The top area (TV and wide screens) describes what you're on; it waits half a second after you stop
    var focused by remember { mutableStateOf<GamePreview?>(null) }
    var shown by remember { mutableStateOf<GamePreview?>(null) }
    LaunchedEffect(focused) {
        val p = focused ?: return@LaunchedEffect
        if (shown != null) delay(500)
        shown = p
    }
    val onFocus: (GamePreview) -> Unit = { focused = it }

    fun refPreview(
        ref: GameRef,
        time: Long?,
    ): GamePreview =
        GamePreview(
            key = "game-${ref.id}",
            kicker = ref.consoleName,
            title = ref.name,
            line = time?.let { "Played ${playedAgo(it)}" },
            picture = ref.coverUrl,
            pictureToken = viewModel.gamesToken,
            playWith =
                listOfNotNull(
                    if (LocalConsole.forSlug(ref.slug)?.remoteFriendly == true) PlayWith.REMOTE else null,
                    PlayWith.CONTROLLER,
                    PlayWith.PHONE,
                ),
        )
    val recentTimes = remember(recent) { recent.associate { it.first.id to it.second } }
    val hasRecent = recent.isNotEmpty()
    // Ben: while your games are still loading, Continue playing shows as a grey row in its place
    val waitingRecent = remember(library) { library is LibraryState.Loading && viewModel.recentPlayed().isNotEmpty() }
    LaunchedEffect(hasRecent, waitingRecent) {
        if (userMoved) return@LaunchedEffect
        // Hold focus for the real Continue playing row (this wait stops as soon as it arrives)
        if (waitingRecent && !hasRecent) delay(FIRST_FOCUS_WAIT_MS)
        pageScroll.scrollTo(0)
        delay(FIRST_FOCUS_SETTLE_MS)
        if (isTv) focusRequester.tryRequestFocus()
    }
    // Redesign 2: the side menu's places, each a part of this long page
    val frame = rememberDoorFrameState(BenflixDoor.GAMES, jumps = true)
    val scope = rememberCoroutineScope()
    val anchorY = remember { mutableStateMapOf<String, Int>() }
    val anchorFocus = remember { mutableMapOf<String, FocusRequester>() }

    fun anchor(key: String): Modifier =
        Modifier
            .onGloballyPositioned { anchorY[key] = it.positionInParent().y.toInt() }
            .focusRequester(anchorFocus.getOrPut(key) { FocusRequester() })
    frame.onJump = { key ->
        scope.launch {
            pageScroll.animateScrollTo(if (key == DOOR_HOME) 0 else anchorY[key] ?: 0)
            delay(JUMP_SETTLE_MS)
            if (key == DOOR_HOME) focusRequester.tryRequestFocus() else anchorFocus[key]?.tryRequestFocus()
        }
    }
    frame.toTop = {
        scope.launch {
            pageScroll.animateScrollTo(0)
            delay(JUMP_SETTLE_MS)
            if (isTv) focusRequester.tryRequestFocus()
        }
    }
    LaunchedEffect(Unit) { snapshotFlow { pageScroll.value < TOP_SLACK_PX }.collect { frame.atTop = it } }
    DisposableEffect(Unit) {
        onDispose { DoorMemory.keep(BenflixDoor.GAMES, DoorMemory.Spot(section = frame.section, scroll = pageScroll.value)) }
    }
    LaunchedEffect(Unit) {
        val spot = recalled?.takeIf { it.scroll > 0 } ?: return@LaunchedEffect
        delay(RETURN_WAIT_MS)
        pageScroll.scrollTo(spot.scroll)
        delay(JUMP_SETTLE_MS)
        // Focus the part of the page you were in
        val at = anchorY.filter { it.value <= spot.scroll + TOP_SLACK_PX }.maxByOrNull { it.value }?.key
        if (isTv) (at?.let { anchorFocus[it] } ?: focusRequester).tryRequestFocus()
    }
    val hasFavourites = remember(refs, favourites) { favourites.any { it in refs } }
    val sections =
        remember(hasFavourites, playable.isNotEmpty(), library) {
            buildList {
                add(doorHome())
                add(DoorSection("puzzles", "Movie puzzles", BenflixIcon.FILM))
                add(DoorSection("arcade", "Arcade", BenflixIcon.PAD))
                if (hasFavourites) add(DoorSection("favourites", "Favourites", BenflixIcon.HEART))
                if (library is LibraryState.Ready && playable.isNotEmpty()) {
                    add(DoorSection("consoles", "Consoles", BenflixIcon.TV, count = playable.size))
                }
                add(DoorSection("leaderboards", "Leaderboards", BenflixIcon.STATS))
            }
        }
    // A game picked in Search opens its page as soon as the game list is here
    LaunchedEffect(refs) {
        val id = PendingGameOpen.id ?: return@LaunchedEffect
        refs[id]?.let {
            PendingGameOpen.id = null
            viewModel.choose(it)
        }
    }
    // Ben: box art comes in different shapes per console (SNES and N64 boxes are wide, NES and Genesis tall),
    // so each card takes its cover's real shape at one shared height and the whole cover always shows
    val cardHeight = if (isTv) 210.dp else 154.dp
    val cardSize = Modifier.size(width = cardHeight * BoxArt.DEFAULT_RATIO, height = cardHeight)

    @Composable
    fun Card(
        ref: GameRef,
        /** In a grid every card keeps its console's shape, so the columns line up */
        fixedShape: Boolean = false,
        onClick: () -> Unit = { viewModel.choose(ref) },
    ) {
        val ratio =
            if (fixedShape) {
                BoxArt.consoleRatio(ref.slug)
            } else {
                ref.coverUrl?.let { BoxArt.measured[it] } ?: BoxArt.consoleRatio(ref.slug)
            }
        val status =
            when {
                prep?.gameId == ref.id -> prep?.text
                starting == ref.id -> "Starting…"
                else -> null
            }
        // Redesign 2 (rc-family): game boxes keep their own shape, with the name in 2 lines and the console
        // under them on TV, phone and web
        Column(Modifier.width(cardHeight * ratio)) {
            ServerGameCard(
                game = ServerGame(id = ref.id, name = ref.name, coverUrl = ref.coverUrl, playable = true, size = ref.size),
                // Approved: the works-with icons live in the top area, not on the box art
                playWith =
                    if (wide) {
                        emptyList()
                    } else {
                        listOfNotNull(
                            if (LocalConsole.forSlug(ref.slug)?.remoteFriendly ==
                                true
                            ) {
                                PlayWith.REMOTE
                            } else {
                                null
                            },
                        )
                    },
                token = viewModel.gamesToken,
                status = status,
                progress = prep?.takeIf { it.gameId == ref.id }?.fraction,
                onClick = onClick,
                modifier =
                    Modifier
                        .size(width = cardHeight * ratio, height = cardHeight)
                        .onFocusChanged { if (it.isFocused) onFocus(refPreview(ref, recentTimes[ref.id])) },
            )
            com.github.damontecres.wholphin.ui.cards.CardNameUnder(
                name = ref.name,
                line = ref.consoleName,
            )
        }
    }

    DoorFrame(frame, sections, modifier, phoneInset = true) { inner ->
        Column(modifier = inner.fillMaxSize().onFirstUserMove { userMoved = true }) {
            if (wide) {
                val first =
                    recent.firstOrNull()?.let { refPreview(it.first, it.second) }
                GamesTopArea(
                    preview = shown ?: first,
                    height = (config.screenHeightDp * 0.36f).dp.coerceIn(170.dp, 300.dp),
                    modifier = Modifier.padding(start = if (isTv) 48.dp else 16.dp, end = if (isTv) 48.dp else 8.dp),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier =
                    Modifier
                        .weight(1f)
                        // Opens on the first game tile (a group requester hands focus to its first child)
                        .focusRequester(focusRequester)
                        .verticalScroll(pageScroll)
                        .padding(
                            start = if (isTv) 48.dp else 0.dp,
                            end = if (isTv) 48.dp else 8.dp,
                            top = if (isTv) 8.dp else 8.dp,
                            bottom = 24.dp,
                        ),
            ) {
                // Order: Continue playing, Movie puzzles, Party, Arcade, Ember Road, Favourites, Consoles, Leaderboards.
                // Phones: the last game played is one big "Keep playing" card first (like Keep watching)
                val keep = if (!wide) recent.firstOrNull() else null
                if (keep != null) {
                    val (ref, time) = keep
                    KeepPlayingCard(
                        ref = ref,
                        line = "${ref.consoleName} · Played ${playedAgo(time)}",
                        token = viewModel.gamesToken,
                        status =
                            when {
                                prep?.gameId == ref.id -> prep?.text
                                starting == ref.id -> "Starting…"
                                else -> null
                            },
                        onPlay = { viewModel.playHere(context, ref, resume = true) },
                        onOpen = { viewModel.choose(ref) },
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp),
                    )
                }
                if (recent.isEmpty() && waitingRecent) {
                    Text(
                        text = "Continue playing",
                        fontSize = if (isTv) BenflixType.rowName else BenflixType.lead,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (isTv) 16.dp else 12.dp),
                        modifier =
                            Modifier
                                .padding(vertical = 8.dp, horizontal = 8.dp)
                                .wrapContentWidth(Alignment.Start, unbounded = true),
                    ) {
                        repeat(8) {
                            Box(cardSize.background(BenflixColors.Raised, RoundedCornerShape(10.dp)))
                        }
                    }
                }
                val rowRecent = if (keep != null) recent.drop(1) else recent
                if (rowRecent.isNotEmpty()) {
                    // TV: the row name comes into view with the row
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.scrollAsOneBlock()) {
                        Text(
                            text = "Continue playing",
                            fontSize = if (isTv) BenflixType.rowName else BenflixType.lead,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(if (isTv) 16.dp else 12.dp),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                        ) {
                            items(rowRecent, key = { "recent-${it.first.id}" }) { (ref, time) ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Continue playing starts the game straight away, where you left off
                                    Card(ref, onClick = { viewModel.playHere(context, ref, resume = true) })
                                    if (!wide) {
                                        Text(
                                            text = "${ref.consoleName} · ${playedAgo(time)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.width(cardHeight * BoxArt.DEFAULT_RATIO),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = anchor("puzzles")) {
                    GameRows(viewModel = viewModel, isTv = isTv, only = setOf("puzzles", "party"), board = board, onFocus = onFocus)
                }
                // Ben: the Arcade games come before your Favourites and Consoles
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = anchor("arcade")) {
                    GameRows(viewModel = viewModel, isTv = isTv, only = setOf("arcade"), board = board, onFocus = onFocus)
                }
                EmberBanner(isTv = isTv, onClick = { viewModel.navigationManager.navigateTo(Destination.EmberRoad) }, onFocus = onFocus)

                // Your own games, from the RomM game library on Tower. They play right here on this TV or phone.
                if (library != LibraryState.Hidden) {
                    when (val state = library) {
                        LibraryState.Hidden -> {}

                        LibraryState.Loading -> {
                            InfoText("Loading your games…")
                        }

                        is LibraryState.Failed -> {
                            InfoText(state.message)
                            val retryTiles: @Composable () -> Unit = {
                                ActionTile(title = "Try again", subtitle = "Reload your games", onClick = { viewModel.reload() })
                                if (state.askAddress) {
                                    ActionTile(
                                        title = "Set server address",
                                        subtitle = "Only if it can't find it",
                                        onClick = { askAddress = true },
                                    )
                                }
                            }
                            // Two tiles side by side are wider than a phone, so on phones they go one under the other
                            if (isTv) {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { retryTiles() }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { retryTiles() }
                            }
                            if (state.downloaded.isNotEmpty()) {
                                Text(
                                    text = "Saved on this device",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                InfoText("These play without Tower. Your saves are sent to Tower next time it can be reached.")
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                                ) {
                                    items(state.downloaded, key = { "dl-${it.ref.id}" }) { Card(it.ref) }
                                }
                            }
                        }

                        is LibraryState.Ready -> {
                            if (state.consoles.isEmpty()) {
                                InfoText("Your game library is connected but has no games yet. Add some in RomM and run a scan.")
                            }
                            val favRefs = remember(refs, favourites) { favourites.mapNotNull { refs[it] }.sortedBy { it.name.lowercase() } }
                            if (favRefs.isNotEmpty()) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = anchor("favourites").scrollAsOneBlock(),
                                ) {
                                    Text(
                                        text = "Favourites",
                                        fontSize = if (isTv) BenflixType.rowName else BenflixType.lead,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
                                        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                                    ) {
                                        items(favRefs, key = { "fav-${it.id}" }) { Card(it) }
                                    }
                                }
                            }
                            var openSlug by rememberSaveable { mutableStateOf<String?>(null) }
                            val open = playable.firstOrNull { it.first.slug == openSlug }
                            BackHandler(enabled = open != null) { openSlug = null }
                            // Opening or leaving a console removes the focused tile, so focus moves on by itself
                            val consoleFocus = remember { FocusRequester() }
                            var wasOpen by remember { mutableStateOf(false) }
                            LaunchedEffect(open != null) {
                                if (open != null) {
                                    consoleFocus.tryRequestFocus()
                                } else if (wasOpen) {
                                    focusRequester.tryRequestFocus()
                                }
                                wasOpen = open != null
                            }
                            if (open == null) {
                                // Benflix "Game roulette": can't decide? Spin and land on a random game
                                var spinning by remember { mutableStateOf(false) }
                                val spinGames =
                                    remember(refs) {
                                        refs.values.filter { !KidsMode.isActive || it.kids }
                                    }
                                if (spinGames.size > 1) {
                                    ActionTile(
                                        title = "Game roulette",
                                        subtitle = "Can't decide? Spin for a random game",
                                        onClick = { spinning = true },
                                    )
                                }
                                if (spinning) {
                                    GameRouletteDialog(
                                        games = spinGames,
                                        token = viewModel.gamesToken,
                                        onPlay = {
                                            spinning = false
                                            viewModel.playHere(context, it, resume = false)
                                        },
                                        onOpen = {
                                            spinning = false
                                            viewModel.choose(it)
                                        },
                                        onDismiss = { spinning = false },
                                    )
                                }
                                // A grid of consoles: photo, name and how many games. Press one to see its games.
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = anchor("consoles").scrollAsOneBlock(),
                                ) {
                                    if (playable.isNotEmpty()) {
                                        Text(
                                            text = "Consoles",
                                            fontSize = if (isTv) BenflixType.rowName else BenflixType.lead,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                    }
                                    ConsoleGrid(
                                        playable = playable,
                                        isTv = isTv,
                                        onOpen = { openSlug = it },
                                        lastPlayed = { c -> recent.firstOrNull { it.first.slug == c.slug }?.first?.name },
                                        onFocus = onFocus,
                                    )
                                }
                            } else {
                                val (console, local, fit) = open
                                val notes =
                                    listOfNotNull(
                                        if (fit ==
                                            DevicePower.Fit.MAY_BE_SLOW
                                        ) {
                                            "may be slow on this ${if (isTv) "TV" else "phone"}"
                                        } else {
                                            null
                                        },
                                        if (isTv && local.remoteFriendly) "works with the remote" else null,
                                    )
                                // Back returns to all consoles (no extra tile, as approved)
                                Text(
                                    text =
                                        "${console.name} (${console.games.size})" +
                                            if (notes.isEmpty()) "" else "  ·  " + notes.joinToString(" · "),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                // Sort is remembered per console (A to Z to start)
                                var sort by rememberSaveable(console.slug) { mutableStateOf(GameSort.AZ) }
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.focusRequester(consoleFocus),
                                ) {
                                    // "Best score" only once RomM has ratings for this console
                                    items(
                                        GameSort.entries.filter { it != GameSort.BEST || console.games.any { g -> g.rating > 0.0 } },
                                        key = { "sort-${it.name}" },
                                    ) { mode ->
                                        SortChip(mode.label, selected = mode == sort) { sort = mode }
                                    }
                                }
                                val rows =
                                    remember(console, favourites) {
                                        consoleRows(
                                            console,
                                            viewModel.localGames.downloaded().map { it.ref.id },
                                            favourites,
                                        )
                                    }
                                rows.forEach { (title, games) ->
                                    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.scrollAsOneBlock()) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
                                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                                        ) {
                                            items(games, key = { "$title-${it.id}" }) { game -> refs[game.id]?.let { Card(it) } }
                                        }
                                    }
                                }
                                // Play time per game is a small file each, so it's only read for Most played
                                val playTimes by produceState(emptyMap<Int, Long>(), console, sort) {
                                    if (sort == GameSort.MOST) {
                                        value =
                                            withContext(Dispatchers.IO) { console.games.associate { it.id to viewModel.playTime(it.id) } }
                                    }
                                }
                                val sections =
                                    remember(console, sort, recent, playTimes) {
                                        consoleGridSections(console.games, sort, recent.map { it.first.id }, playTimes)
                                    }
                                val config = LocalConfiguration.current
                                ConsoleGameGrid(
                                    sections = sections,
                                    cardWidth = cardHeight * BoxArt.consoleRatio(console.slug),
                                    height = (config.screenHeightDp * if (isTv) 0.8f else 0.75f).dp,
                                    showLetters = sort == GameSort.AZ,
                                    card = { game -> refs[game.id]?.let { Card(it, fixedShape = true) } },
                                )
                            }
                            ActionTile(title = "Reload games", subtitle = "After adding games in RomM", onClick = { viewModel.reload() })
                        }
                    }
                }
                // Leaderboards last: every game, its top 3 and your place, all time
                val boardGames =
                    remember(board) {
                        BuiltInGame.entries.map { g ->
                            BoardGame(
                                g.name,
                                g.title,
                                { viewModel.navigationManager.navigateTo(Destination.GameInfo("arcade:${g.name}")) },
                                viewModel.best(g),
                            )
                        } +
                            if (KidsMode.isActive) {
                                emptyList()
                            } else {
                                MovieGame.entries.filter { it.boardKey != null && it.visibleNow(false) }.map { g ->
                                    BoardGame(
                                        g.boardKey!!,
                                        g.title,
                                        { viewModel.navigationManager.navigateTo(Destination.GameInfo("movie:${g.name}")) },
                                        viewModel.bestExtra(g.boardKey),
                                    )
                                }
                            }
                    }
                val myName = remember { viewModel.myBoardName() }
                Box(anchor("leaderboards")) {
                    LeaderboardRow(games = boardGames, board = board, myName = myName, isTv = isTv, onFocus = onFocus)
                }
                if (library != LibraryState.Hidden) {
                    DeviceCard(power, isTv)
                    // Refreshes after a game is downloaded or removed, not only when the library reloads
                    val downloadsVersion by viewModel.localGames.downloadsVersion.collectAsState()
                    StorageCard(
                        games = remember(library, downloadsVersion) { viewModel.visibleDownloads() },
                        isTv = isTv,
                        // Kids can't delete games from the device
                        onRemove =
                            if (KidsMode.isActive) {
                                null
                            } else {
                                { g: DownloadedGame -> viewModel.removeDownload(context, g.ref) }
                            },
                    )
                    if (!isTv) {
                        ActionTile(
                            title = "Use this phone as a controller",
                            subtitle = "For a game playing on a TV in the house",
                            onClick = { context.startActivity(Intent(context, PhonePadActivity::class.java)) },
                        )
                    }
                }
            }
        }
    }

    val choice by viewModel.choice.collectAsState()
    val info by viewModel.info.collectAsState()
    choice?.let { c ->
        GameDetailsPage(
            choice = c,
            info = info?.takeIf { it.first == c.ref.id }?.second,
            saves = remember(c) { viewModel.saveSpots(c.ref.id) },
            playTimeMs = remember(c) { viewModel.playTime(c.ref.id) },
            favourite = c.ref.id in favourites,
            token = viewModel.gamesToken,
            isTv = isTv,
            onPlay = { resume -> viewModel.playHere(context, c.ref, resume) },
            onFavourite = { viewModel.toggleFavourite(c.ref.id) },
            onKeep = { keep -> viewModel.setKeep(context, c.ref, keep) },
            onRemove = { viewModel.removeDownload(context, c.ref) },
            onDismiss = { viewModel.closeChoice() },
        )
    }

    if (askAddress) {
        StringInputDialog(
            input =
                StringInput(
                    title = "Games server address (Tower's home address, like 10.0.0.58). Leave empty for automatic.",
                    value = viewModel.gamesAddress.removePrefix("http://"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    onSubmit = {},
                ),
            onSave = {
                askAddress = false
                viewModel.setAddress(it)
            },
            onDismissRequest = { askAddress = false },
        )
    }
}

@Composable
internal fun InfoText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A simple wide button-like tile, e.g. "Connect game library" */
@Composable
internal fun ActionTile(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, BenflixColors.Focus), shape = shape),
            ),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF2A2A2A), focusedContainerColor = Color(0xFF3A3A3A)),
        modifier = Modifier.size(width = 280.dp, height = 84.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(title, fontSize = BenflixType.lead, fontWeight = FontWeight.Bold, color = Color.White)
            Text(subtitle, fontSize = BenflixType.small, color = BenflixColors.Soft)
        }
    }
}

/**
 * Phones: the last game played as one big card at the top of Games (approved "Keep playing"), with its
 * box art, name, console and a pink Play button. Play carries on where you left off; the card opens the
 * game's page.
 */
@Composable
private fun KeepPlayingCard(
    ref: GameRef,
    line: String,
    token: String,
    status: String?,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val context = LocalContext.current
    Surface(
        onClick = onOpen,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = BenflixColors.Raised, focusedContainerColor = BenflixColors.Raised),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Box(
                Modifier
                    .size(width = 100.dp, height = 140.dp)
                    .background(Color(0xFF1F1F1F), RoundedCornerShape(10.dp)),
            ) {
                if (ref.coverUrl != null) {
                    AsyncImage(
                        model =
                            remember(ref.coverUrl) {
                                ImageRequest
                                    .Builder(context)
                                    .data(ref.coverUrl)
                                    .httpHeaders(NetworkHeaders.Builder().set("X-Benflix-Token", token).build())
                                    .build()
                            },
                        contentDescription = ref.name,
                        contentScale = ContentScale.Fit,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp)),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = "KEEP PLAYING",
                    color = BenflixColors.Games,
                    fontSize = BenflixType.tag,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                )
                Text(
                    text = ref.name,
                    color = BenflixColors.Text,
                    fontSize = BenflixType.rowName,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = status ?: line,
                    color = BenflixColors.Soft,
                    fontSize = BenflixType.small,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    onClick = onPlay,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                    colors =
                        ClickableSurfaceDefaults.colors(
                            containerColor = BenflixColors.Games,
                            contentColor = BenflixColors.OnGames,
                            focusedContainerColor = BenflixColors.Games,
                            focusedContentColor = BenflixColors.OnGames,
                        ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Text(
                        text = "▶  Play",
                        fontSize = BenflixType.body,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}

/** A box art card for one of your own games */
@Composable
internal fun ServerGameCard(
    game: ServerGame,
    token: String,
    status: String?,
    onClick: () -> Unit,
    progress: Float? = null,
    playWith: List<PlayWith> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val context = LocalContext.current
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, BenflixColors.Focus), shape = shape),
            ),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1F1F1F), focusedContainerColor = Color(0xFF1F1F1F)),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize()) {
            // Redesign 2 (rc-p-noart): no box art = the dark card with the big white name and a pink line
            com.github.damontecres.wholphin.ui.cards.NoPictureCard(
                name = game.name,
                door = BenflixColors.Games,
                small = true,
            )
            if (game.coverUrl != null) {
                AsyncImage(
                    model =
                        remember(game.coverUrl) {
                            ImageRequest
                                .Builder(context)
                                .data(game.coverUrl)
                                .httpHeaders(NetworkHeaders.Builder().set("X-Benflix-Token", token).build())
                                // Redesign 2 (rc-p-load): box art fades in
                                .crossfade(com.github.damontecres.wholphin.ui.cards.CARD_FADE_MS)
                                .build()
                        },
                    contentDescription = game.name,
                    // The whole cover, never cut off; the card learns the cover's shape the first time it loads
                    contentScale = ContentScale.Fit,
                    onSuccess = { BoxArt.learn(game.coverUrl, it.painter.intrinsicSize) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // What it plays with: remote (simple games only), controller, phone
            if (playWith.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp),
                ) {
                    playWith.forEach { PlayIcon(it) }
                }
            }
            if (status != null || !game.playable) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = .65f)),
                ) {
                    if (progress != null) {
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(progress)
                                .height(6.dp)
                                .background(Color.White),
                        )
                    }
                    Text(
                        text = status ?: "Can't play here yet",
                        fontSize = BenflixType.small,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

/** "today", "yesterday", "3 days ago", "last week" or a date */
internal fun playedAgo(time: Long): String {
    val days = ((System.currentTimeMillis() - time) / 86_400_000L).toInt()
    return when {
        days <= 0 -> {
            "today"
        }

        days == 1 -> {
            "yesterday"
        }

        days < 7 -> {
            "$days days ago"
        }

        days < 14 -> {
            "last week"
        }

        else -> {
            java.time.Instant
                .ofEpochMilli(time)
                .atZone(java.time.ZoneId.systemDefault())
                .format(
                    java.time.format.DateTimeFormatter
                        .ofPattern("MMM d"),
                )
        }
    }
}

private const val FIRST_FOCUS_SETTLE_MS = 80L
private const val FIRST_FOCUS_WAIT_MS = 4_000L

/** How long a jump waits for the page to settle before focus moves */
private const val JUMP_SETTLE_MS = 90L

/** Within this far of the top counts as "at the top" (for Back) */
private const val TOP_SLACK_PX = 40

/** Coming back within 10 minutes: wait for the rows to be drawn before scrolling down to where you were */
private const val RETURN_WAIT_MS = 450L
