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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import com.github.damontecres.wholphin.games.DevicePower
import com.github.damontecres.wholphin.games.DownloadedGame
import com.github.damontecres.wholphin.games.GameRef
import com.github.damontecres.wholphin.games.LocalConsole
import com.github.damontecres.wholphin.games.PhonePadActivity
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.ServerGame
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.preferences.StringInput
import com.github.damontecres.wholphin.ui.preferences.StringInputDialog
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.onFirstUserMove
import com.github.damontecres.wholphin.ui.util.scrollAsOneBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The Games tab: quick built-in games, plus your own game collection from RomM on the server
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
    var userMoved by remember { mutableStateOf(false) }
    val isTv = rememberIsTv()
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp
    val library by viewModel.library.collectAsState()
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
                        it.id to GameRef(it.id, it.name, c.slug, c.name, it.coverUrl, it.size, kids = it.kids)
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
    // A game picked in Search opens its page as soon as the game list is here
    LaunchedEffect(refs) {
        val id = PendingGameOpen.id ?: return@LaunchedEffect
        refs[id]?.let {
            PendingGameOpen.id = null
            viewModel.choose(it)
        }
    }
    val cardSize =
        Modifier.size(
            width = if (isTv) 150.dp else 110.dp,
            height = if (isTv) 210.dp else 154.dp,
        )

    @Composable
    fun Card(
        ref: GameRef,
        onClick: () -> Unit = { viewModel.choose(ref) },
    ) {
        val status =
            when {
                prep?.gameId == ref.id -> prep?.text
                starting == ref.id -> "Starting…"
                else -> null
            }
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
            modifier = cardSize.onFocusChanged { if (it.isFocused) onFocus(refPreview(ref, recentTimes[ref.id])) },
        )
    }

    Column(modifier = modifier.fillMaxSize().onFirstUserMove { userMoved = true }) {
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
            // Approved order: Continue playing, Movie puzzles, Party, Ember Road, Consoles, Arcade, Leaderboards
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
            if (recent.isNotEmpty()) {
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
                        items(recent, key = { "recent-${it.first.id}" }) { (ref, time) ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Continue playing starts the game straight away, where you left off
                                Card(ref, onClick = { viewModel.playHere(context, ref, resume = true) })
                                if (!wide) {
                                    Text(
                                        text = "${ref.consoleName} · ${playedAgo(time)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        modifier = Modifier.width(110.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            GameRows(viewModel = viewModel, isTv = isTv, only = setOf("puzzles", "party"), board = board, onFocus = onFocus)
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
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.scrollAsOneBlock()) {
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
                            // A grid of consoles: photo, name and how many games. Press one to see its games.
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.scrollAsOneBlock()) {
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
                                    if (fit == DevicePower.Fit.MAY_BE_SLOW) "may be slow on this ${if (isTv) "TV" else "phone"}" else null,
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
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.focusRequester(consoleFocus)) {
                                items(GameSort.entries.toList(), key = { "sort-${it.name}" }) { mode ->
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
                                cardWidth = if (isTv) 150.dp else 110.dp,
                                height = (config.screenHeightDp * if (isTv) 0.8f else 0.75f).dp,
                                showLetters = sort == GameSort.AZ,
                                card = { game -> refs[game.id]?.let { Card(it) } },
                            )
                        }
                        ActionTile(title = "Reload games", subtitle = "After adding games in RomM", onClick = { viewModel.reload() })
                    }
                }
            }
            GameRows(viewModel = viewModel, isTv = isTv, only = setOf("arcade"), board = board, onFocus = onFocus)
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
            LeaderboardRow(games = boardGames, board = board, myName = myName, isTv = isTv, onFocus = onFocus)
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
                    title = "Games server address (for example 10.0.0.58). Leave empty for automatic.",
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
                focusedBorder = Border(BorderStroke(3.dp, BenflixColors.Text), shape = shape),
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
                focusedBorder = Border(BorderStroke(3.dp, BenflixColors.Text), shape = shape),
            ),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1F1F1F), focusedContainerColor = Color(0xFF1F1F1F)),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize()) {
            // The name sits underneath, so it shows when there's no box art
            Text(
                text = game.name,
                fontSize = BenflixType.small,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(10.dp),
            )
            if (game.coverUrl != null) {
                AsyncImage(
                    model =
                        remember(game.coverUrl) {
                            ImageRequest
                                .Builder(context)
                                .data(game.coverUrl)
                                .httpHeaders(NetworkHeaders.Builder().set("X-Benflix-Token", token).build())
                                .build()
                        },
                    contentDescription = game.name,
                    contentScale = ContentScale.Crop,
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
