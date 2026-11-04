package com.github.damontecres.wholphin.ui.games

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
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
import com.github.damontecres.wholphin.R
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
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus

/**
 * The Games tab: quick built-in games, plus your own game collection from RomM on the server
 */
@Composable
fun GamesPage(
    modifier: Modifier = Modifier,
    viewModel: GamesViewModel = hiltViewModel(),
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    val isTv = rememberIsTv()
    val context = LocalContext.current
    val library by viewModel.library.collectAsState()
    val starting by viewModel.starting.collectAsState()
    var askAddress by remember { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = if (isTv) 48.dp else 0.dp,
                    end = if (isTv) 48.dp else 8.dp,
                    top = if (isTv) 24.dp else 8.dp,
                    bottom = 24.dp,
                ),
    ) {
        Text(
            text = "Benflix Games",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        // One "works with" line for the whole page, instead of chips on every tile
        WorksWith(
            text = if (isTv) "Quick games you can play with your TV remote." else "Quick games you can play by tapping.",
            isTv = isTv,
        )
        // Leaderboards first: your bests and everyone's top scores, each card can take focus so the remote reaches them
        val board by viewModel.board.collectAsState()
        LaunchedEffect(Unit) { viewModel.refreshBoard() }
        Leaderboards(viewModel = viewModel, board = board, isTv = isTv, firstFocus = focusRequester)
        ArcadeWall(viewModel = viewModel, isTv = isTv, focusRequester = remember { FocusRequester() })

        // Benflix "Ember Road": the pixel adventure, for everyone (it has a Kids version built in)
        MovieGameTile(
            title = "Ember Road",
            subtitle =
                "A pixel adventure: carry a dragon egg across a wild magical land with your family. " +
                    "Every journey is different. Phones can join in.",
            icon = R.string.fa_dragon,
            colors = listOf(Color(0xFFB8281E), Color(0xFF2B1B4D)),
            onClick = { viewModel.navigationManager.navigateTo(Destination.EmberRoad) },
            isTv = isTv,
            modifier = Modifier.fillMaxWidth(),
        )

        // Benflix "Guess the movie" and the daily riddle, made from your own movies
        val movieGames =
            listOf<@Composable (Modifier) -> Unit>(
                { mod ->
                    MovieGameTile(
                        title = "Guess the movie",
                        subtitle = "A blurry picture gets sharper. Guess it fast for more points!",
                        icon = R.string.fa_eye,
                        colors = listOf(Color(0xFF0E7490), Color(0xFF1E3A8A)),
                        onClick = { viewModel.navigationManager.navigateTo(Destination.GuessTheMovie) },
                        isTv = isTv,
                        modifier = mod,
                    )
                },
                { mod ->
                    MovieGameTile(
                        title = "Movie riddle",
                        subtitle = "Guess the movie from its clues. Five choices, and it keeps going until you miss!",
                        icon = R.string.fa_clock,
                        colors = listOf(Color(0xFFC2410C), Color(0xFF6D28D9)),
                        onClick = { viewModel.navigationManager.navigateTo(Destination.DailyRiddle) },
                        isTv = isTv,
                        modifier = mod,
                    )
                },
                { mod ->
                    MovieGameTile(
                        title = "Higher or Lower",
                        subtitle = "Two posters. Did critics score the second one higher or lower? Keep your streak going!",
                        icon = R.string.fa_arrow_up_long,
                        colors = listOf(Color(0xFF15803D), Color(0xFF7F1D1D)),
                        onClick = { viewModel.navigationManager.navigateTo(Destination.HigherLower) },
                        isTv = isTv,
                        modifier = mod,
                    )
                },
            )
        // Benflix: the movie games show posters from the whole library, so they're not on the Kids profile
        if (!KidsMode.isActive && isTv) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.height(IntrinsicSize.Min),
            ) {
                movieGames.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
            }
        } else if (!KidsMode.isActive) {
            movieGames.forEach { it(Modifier.fillMaxWidth()) }
        }

        // Your own games, from the RomM game library on Tower. They play right here on this TV or phone.
        if (library != LibraryState.Hidden) {
            Text(
                text = "Your games",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp),
            )
            val power = remember { DevicePower.get(context) }
            val prep by viewModel.localGames.prep.collectAsState()
            val cardSize =
                Modifier.size(
                    width = if (isTv) 150.dp else 110.dp,
                    height = if (isTv) 210.dp else 154.dp,
                )

            @Composable
            fun Card(ref: GameRef) {
                val status =
                    when {
                        prep?.gameId == ref.id -> prep?.text
                        starting == ref.id -> "Starting…"
                        else -> null
                    }
                ServerGameCard(
                    game = ServerGame(id = ref.id, name = ref.name, coverUrl = ref.coverUrl, playable = true, size = ref.size),
                    playWith =
                        remember(ref.slug) {
                            val local = LocalConsole.forSlug(ref.slug)
                            listOfNotNull(
                                if (local?.remoteFriendly == true) PlayWith.REMOTE else null,
                                PlayWith.CONTROLLER,
                                PlayWith.PHONE,
                            )
                        },
                    token = viewModel.gamesToken,
                    status = status,
                    progress = prep?.takeIf { it.gameId == ref.id }?.fraction,
                    onClick = { viewModel.choose(ref) },
                    modifier = cardSize,
                )
            }
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
                            ActionTile(title = "Set server address", subtitle = "Only if it can't find it", onClick = { askAddress = true })
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
                    // Only consoles this device can play (some may be a bit slow)
                    val playable =
                        remember(state) {
                            state.consoles.mapNotNull { c ->
                                val local = LocalConsole.forSlug(c.slug) ?: return@mapNotNull null
                                val fit = power.fit(local.power)
                                if (fit == DevicePower.Fit.TOO_MUCH) null else Triple(c, local, fit)
                            }
                        }
                    if (playable.isNotEmpty()) {
                        InfoText(
                            if (isTv) {
                                "Press a game to play it on this TV. Any Bluetooth controller works. Back opens the game menu."
                            } else {
                                "Tap a game to play it on this phone with on-screen buttons, or a Bluetooth controller."
                            },
                        )
                    }
                    val refs =
                        remember(state) {
                            playable
                                .flatMap { (c, _, _) ->
                                    c.games.map {
                                        it.id to GameRef(it.id, it.name, c.slug, c.name, it.coverUrl, it.size, kids = it.kids)
                                    }
                                }.toMap()
                        }
                    val towerRecent by viewModel.towerRecent.collectAsState()
                    val recent =
                        remember(state, starting, towerRecent) {
                            viewModel.recentPlayed().mapNotNull { (id, time) -> refs[id]?.let { it to time } }
                        }
                    val favourites by viewModel.favourites.collectAsState()
                    val favRefs = remember(refs, favourites) { favourites.mapNotNull { refs[it] }.sortedBy { it.name.lowercase() } }
                    if (favRefs.isNotEmpty()) {
                        Text(
                            text = "Favourite games",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                        ) {
                            items(favRefs, key = { "fav-${it.id}" }) { Card(it) }
                        }
                    }
                    if (recent.isNotEmpty()) {
                        Text(
                            text = "Continue playing",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                        ) {
                            items(recent, key = { "recent-${it.first.id}" }) { (ref, time) ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Card(ref)
                                    Text(
                                        text = "${ref.consoleName} · ${playedAgo(time)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        modifier = Modifier.width(if (isTv) 150.dp else 110.dp),
                                    )
                                }
                            }
                        }
                    }
                    var openSlug by rememberSaveable { mutableStateOf<String?>(null) }
                    val open = playable.firstOrNull { it.first.slug == openSlug }
                    BackHandler(enabled = open != null) { openSlug = null }
                    if (open == null) {
                        // A grid of consoles: photo, name and how many games. Press one to see its games.
                        if (playable.isNotEmpty()) {
                            Text(
                                text = "Consoles",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        ConsoleGrid(playable = playable, isTv = isTv, onOpen = { openSlug = it })
                    } else {
                        val (console, local, fit) = open
                        val notes =
                            listOfNotNull(
                                if (fit == DevicePower.Fit.MAY_BE_SLOW) "may be slow on this ${if (isTv) "TV" else "phone"}" else null,
                                if (isTv && local.remoteFriendly) "works with the remote" else null,
                            )
                        ActionTile(title = "← All consoles", subtitle = console.name, onClick = { openSlug = null })
                        Text(
                            text =
                                "${console.name} (${console.games.size})" +
                                    if (notes.isEmpty()) "" else "  ·  " + notes.joinToString(" · "),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        var sort by rememberSaveable { mutableStateOf(GameSort.AZ) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GameSort.entries.forEach { mode ->
                                SortChip(mode.label, selected = mode == sort) { sort = mode }
                            }
                        }
                        val rows =
                            remember(console, recent, favourites, sort) {
                                consoleRows(
                                    console,
                                    recent.map { it.first.id },
                                    viewModel.localGames.downloaded().map { it.ref.id },
                                    favourites,
                                    sort,
                                )
                            }
                        rows.forEach { (title, games) ->
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
                    ActionTile(title = "Reload games", subtitle = "After adding games in RomM", onClick = { viewModel.reload() })
                }
            }
        }
    }

    val choice by viewModel.choice.collectAsState()
    val info by viewModel.info.collectAsState()
    val favourites by viewModel.favourites.collectAsState()
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
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
            ),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF2A2A2A), focusedContainerColor = Color(0xFF3A3A3A)),
        modifier = Modifier.size(width = 280.dp, height = 84.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = .8f))
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
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
            ),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1F1F1F), focusedContainerColor = Color(0xFF1F1F1F)),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize()) {
            // The name sits underneath, so it shows when there's no box art
            Text(
                text = game.name,
                fontSize = 14.sp,
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
                                .background(Color(0xFFE50914)),
                        )
                    }
                    Text(
                        text = status ?: "Can't play here yet",
                        fontSize = 14.sp,
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
