package com.github.damontecres.wholphin.ui.games

import android.os.StatFs
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.games.DevicePower
import com.github.damontecres.wholphin.games.DownloadedGame
import com.github.damontecres.wholphin.games.GameRef
import com.github.damontecres.wholphin.games.LocalConsole
import com.github.damontecres.wholphin.games.LocalGames
import com.github.damontecres.wholphin.services.GameConsole
import com.github.damontecres.wholphin.services.HighScore
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.ServerGame
import com.github.damontecres.wholphin.ui.tryRequestFocus

/**
 * Leaderboards at the top of Games: "Your high scores" on one half and everyone's top scores on the other
 * (one under the other on phones). Both halves can take focus, so the TV remote can reach them.
 */
@Composable
internal fun Leaderboards(
    viewModel: GamesViewModel,
    board: Map<String, List<HighScore>>?,
    isTv: Boolean,
    firstFocus: FocusRequester,
) {
    val games =
        BuiltInGame.entries.map { it.name to it.title } +
            if (KidsMode.isActive) emptyList() else GamesViewModel.BOARD_EXTRA_GAMES.entries.map { it.key to it.value }
    val mine =
        remember(board) {
            BuiltInGame.entries.associate { it.name to viewModel.best(it) } +
                GamesViewModel.BOARD_EXTRA_GAMES.keys.associateWith { viewModel.bestExtra(it) }
        }
    val yours: @Composable (Modifier) -> Unit = { mod ->
        BoardPanel(
            title = "Your high scores",
            subtitle = "Your best in every game",
            colour = Color(0xFF7C3AED),
            modifier = mod.focusRequester(firstFocus),
        ) {
            games.forEach { (id, title) ->
                val best = mine[id] ?: 0
                BoardLine(
                    game = title,
                    ranked = GamesViewModel.BOARD_RANKED[id],
                    right = listOf(if (best > 0) "%,d".format(best) else "—"),
                    highlight = best > 0,
                )
            }
        }
    }
    val everyone: @Composable (Modifier) -> Unit = { mod ->
        BoardPanel(
            title = "🏆 Leaderboard",
            subtitle = if (board == null) "Loading everyone's scores…" else "Top players on this server",
            colour = Color(0xFFCA8A04),
            modifier = mod,
        ) {
            games.forEach { (id, title) ->
                val top = board?.get(id).orEmpty().take(3)
                BoardLine(
                    game = title,
                    ranked = GamesViewModel.BOARD_RANKED[id],
                    right =
                        if (top.isEmpty()) {
                            listOf("No scores yet")
                        } else {
                            top.mapIndexed { i, e -> "${listOf("🥇", "🥈", "🥉")[i]} ${e.name} ${shortScore(e.score)}" }
                        },
                    highlight = top.isNotEmpty(),
                )
            }
        }
    }
    if (isTv) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
            yours(Modifier.weight(1f).fillMaxHeight())
            everyone(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        yours(Modifier.fillMaxWidth())
        everyone(Modifier.fillMaxWidth())
    }
}

@Composable
internal fun BoardPanel(
    title: String,
    subtitle: String,
    colour: Color,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF16161A), focusedContainerColor = Color(0xFF1C1C22)),
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(colour.copy(alpha = .55f), Color(0xFF16161A), Color(0xFF16161A))))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = .7f), modifier = Modifier.padding(bottom = 4.dp))
            content()
        }
    }
}

/** 950, 12.3k, 1.2M: short enough that three scores fit on one line */
internal fun shortScore(score: Int): String =
    when {
        score >= 1_000_000 -> "%.1fM".format(score / 1_000_000f)
        score >= 10_000 -> "${score / 1000}k"
        score >= 1_000 -> "%.1fk".format(score / 1000f)
        else -> score.toString()
    }

/** One game on a leaderboard: name on the left (with what it ranks), scores on the right (they wrap, never cut off) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BoardLine(
    game: String,
    ranked: String?,
    right: List<String>,
    highlight: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(0.42f)) {
            Text(game, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (ranked != null) {
                Text(ranked, fontSize = 11.sp, color = Color.White.copy(alpha = .55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            modifier = Modifier.weight(0.58f),
        ) {
            right.forEachIndexed { i, text ->
                Text(
                    text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color =
                        when {
                            !highlight -> Color.White.copy(alpha = .5f)
                            i == 0 -> Color(0xFFFFD54F)
                            else -> Color.White.copy(alpha = .9f)
                        },
                    maxLines = 1,
                )
            }
        }
    }
}

/** What the Play / Resume choices for one game show */
data class GameChoice(
    val ref: GameRef,
    val resume: Boolean,
    val downloaded: Boolean,
    val pinned: Boolean,
)

/**
 * "This TV: MediaTek MT8696 · 4 cores…" and which consoles play well here. It can take focus, so the
 * remote can move onto it and the page scrolls to show it.
 */
@Composable
internal fun DeviceCard(
    power: DevicePower,
    isTv: Boolean,
) {
    val good = LocalConsole.ALL.filter { power.fit(it.power) == DevicePower.Fit.GOOD }.map { it.title }
    val slow = LocalConsole.ALL.filter { power.fit(it.power) == DevicePower.Fit.MAY_BE_SLOW }.map { it.title }
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1C1C1C), focusedContainerColor = Color(0xFF262626)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = (if (isTv) "This TV: " else "This phone: ") + power.summary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text("Plays well: ${good.joinToString(", ")}", fontSize = 14.sp, color = Color.White.copy(alpha = .8f))
            if (slow.isNotEmpty()) Text("May be slow: ${slow.joinToString(", ")}", fontSize = 14.sp, color = Color.White.copy(alpha = .8f))
        }
    }
}

/** How the games inside a console are grouped after the top rows */
enum class GameSort(
    val label: String,
) {
    AZ("A to Z"),
    GENRE("Genre"),
    YEAR("Year"),
}

/** Rows inside one console: played lately, on this device, favourites, then all games by letter, genre or year */
internal fun consoleRows(
    console: GameConsole,
    recentIds: List<Int>,
    downloadedIds: List<Int>,
    favouriteIds: Set<Int>,
    sort: GameSort,
): List<Pair<String, List<ServerGame>>> {
    val byId = console.games.associateBy { it.id }
    val rows = mutableListOf<Pair<String, List<ServerGame>>>()
    recentIds.mapNotNull { byId[it] }.takeIf { it.isNotEmpty() }?.let { rows += "Played lately" to it }
    downloadedIds.mapNotNull { byId[it] }.takeIf { it.isNotEmpty() }?.let { rows += "On this device" to it }
    console.games
        .filter { it.id in favouriteIds }
        .sortedBy { it.name.lowercase() }
        .takeIf { it.isNotEmpty() }
        ?.let { rows += "Favourites" to it }
    val sorted = console.games.sortedBy { it.name.lowercase() }
    when (sort) {
        GameSort.AZ -> {
            if (sorted.size <= 24) {
                rows += "All games" to sorted
            } else {
                sorted
                    .groupBy {
                        val c = it.name.firstOrNull()?.uppercaseChar() ?: '#'
                        if (c in 'A'..'Z') c.toString() else "#"
                    }.toSortedMap()
                    .forEach { (letter, games) -> rows += letter to games }
            }
        }

        GameSort.GENRE -> {
            val groups = sorted.groupBy { it.genres.firstOrNull() ?: "Other" }
            groups.keys
                .sortedWith(compareBy({ it == "Other" }, { -(groups[it]?.size ?: 0) }))
                .forEach { rows += it to groups.getValue(it) }
        }

        GameSort.YEAR -> {
            sorted
                .groupBy { if (it.year > 0) it.year.toString() else "Unknown year" }
                .toSortedMap(compareBy<String> { it == "Unknown year" }.thenByDescending { it })
                .forEach { (year, games) -> rows += year to games }
        }
    }
    return rows
}

/** Public domain console photos (by Evan Amos, Wikimedia Commons), by RomM console name */
internal fun consolePhoto(slug: String): String? =
    CONSOLE_PHOTOS[slug]?.let { "https://commons.wikimedia.org/wiki/Special:FilePath/$it?width=480" }

internal val CONSOLE_PHOTOS =
    mapOf(
        "nes" to "NES-Console-Set.jpg",
        "famicom" to "NES-Console-Set.jpg",
        "snes" to "SNES-Mod1-Console-Set.jpg",
        "sfam" to "SNES-Mod1-Console-Set.jpg",
        "genesis" to "Sega-Genesis-Mk2-6button.jpg",
        "genesis-slash-megadrive" to "Sega-Genesis-Mk2-6button.jpg",
        "megadrive" to "Sega-Genesis-Mk2-6button.jpg",
        "sms" to "Sega-Master-System-Set.jpg",
        "gamegear" to "Game-Gear-Handheld.jpg",
        "gb" to "Game-Boy-FL.jpg",
        "gbc" to "Nintendo-Game-Boy-Color-FL.jpg",
        "gba" to "Nintendo-Game-Boy-Advance-Purple-FL.jpg",
        "psx" to "PSX-Console-wController.jpg",
        "atari2600" to "Atari-2600-Wood-4Sw-Set.jpg",
        "atari7800" to "Atari-7800-Console-Set.jpg",
        "lynx" to "Atari-Lynx-II-Handheld-Angled.jpg",
        "tg16" to "TurboGrafx16-Console-Set.jpg",
        "turbografx16--1" to "TurboGrafx16-Console-Set.jpg",
        "ngpc" to "Neo-Geo-Pocket-Color-Blue-Left.jpg",
        "neo-geo-pocket-color" to "Neo-Geo-Pocket-Color-Blue-Left.jpg",
        "wonderswan-color" to "WonderSwan-Color-Blue-Left.jpg",
        "virtualboy" to "Virtual-Boy-Set.jpg",
        "colecovision" to "ColecoVision-wController-L.jpg",
        "n64" to "Nintendo-64-wController-L.jpg",
    )

@Composable
internal fun SortChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border =
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, if (selected) Color.White else Color.Transparent), shape = shape),
                focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
            ),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (selected) Color.White.copy(alpha = .2f) else Color(0xFF242424),
                contentColor = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

/** The console tiles at the top of "Your games" */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConsoleGrid(
    playable: List<Triple<GameConsole, LocalConsole, DevicePower.Fit>>,
    isTv: Boolean,
    onOpen: (String) -> Unit,
) {
    val tileWidth = if (isTv) 220.dp else 160.dp
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        playable.forEach { (console, local, fit) ->
            val shape = RoundedCornerShape(14.dp)
            Surface(
                onClick = { onOpen(console.slug) },
                shape = ClickableSurfaceDefaults.shape(shape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = consoleColour(console.slug).copy(alpha = .35f),
                        focusedContainerColor = consoleColour(console.slug).copy(alpha = .55f),
                    ),
                modifier = Modifier.width(tileWidth),
            ) {
                Column {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(tileWidth * 0.62f)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(consoleColour(console.slug), consoleColour(console.slug).copy(alpha = .7f)),
                                    ),
                                ),
                    ) {
                        // The name shows until (or if) the photo loads over it
                        Text(local.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        consolePhoto(console.slug)?.let { photo ->
                            AsyncImage(
                                model = photo,
                                contentDescription = console.name,
                                contentScale = ContentScale.Fit,
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                            )
                        }
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            console.name,
                            fontSize = if (isTv) 17.sp else 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text =
                                "${console.games.size} ${if (console.games.size == 1) "game" else "games"}" +
                                    if (fit == DevicePower.Fit.MAY_BE_SLOW) " · may be slow" else "",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = .75f),
                            maxLines = 1,
                        )
                        // What it plays with, the same icons as the built-in games
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            if (local.remoteFriendly) Badge(PlayWith.REMOTE, "Remote")
                            Badge(PlayWith.CONTROLLER, "Controller")
                            Badge(PlayWith.PHONE, "Phone")
                        }
                    }
                }
            }
        }
    }
}

/** How full this device is, with a bar, and a place to delete downloaded games (saves are kept) */
@Composable
internal fun StorageCard(
    games: List<DownloadedGame>,
    isTv: Boolean,
    /** Null when games can't be deleted here (Kids) */
    onRemove: ((DownloadedGame) -> Unit)?,
) {
    val context = LocalContext.current
    val stats =
        remember(games) {
            try {
                val fs = StatFs(context.filesDir.path)
                fs.totalBytes to fs.availableBytes
            } catch (_: Exception) {
                0L to 0L
            }
        }
    val (total, free) = stats
    if (total <= 0) return
    val used = (total - free).toFloat() / total
    val gamesBytes = games.sumOf { it.bytes }
    val barColor =
        when {
            used > 0.9f -> Color(0xFFE5484D)
            used > 0.75f -> Color(0xFFF5A524)
            else -> Color(0xFF46A758)
        }
    var managing by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = { if (games.isNotEmpty() && onRemove != null) managing = true },
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1C1C1C), focusedContainerColor = Color(0xFF262626)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = (if (isTv) "TV storage: " else "Phone storage: ") + "${LocalGames.mb(free)} free of ${LocalGames.mb(total)}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Color.White.copy(alpha = .15f), RoundedCornerShape(4.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(used.coerceIn(0.02f, 1f))
                        .height(8.dp)
                        .background(barColor, RoundedCornerShape(4.dp)),
                )
            }
            Text(
                text =
                    if (games.isEmpty()) {
                        "No games downloaded on this device yet"
                    } else {
                        "Games on this device: ${LocalGames.mb(gamesBytes)} (${games.size}). " +
                            (if (used > 0.9f) "Getting full! " else "") +
                            if (onRemove != null) "Select to delete some." else ""
                    },
                fontSize = 14.sp,
                color = Color.White.copy(alpha = .8f),
            )
        }
    }
    if (managing && onRemove != null) {
        val focus = remember { FocusRequester() }
        com.github.damontecres.wholphin.ui.touch.Dialog(onDismissRequest = { managing = false }) {
            LaunchedEffect(Unit) { focus.tryRequestFocus() }
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier =
                    Modifier
                        .widthIn(max = 460.dp)
                        .heightIn(max = 560.dp)
                        .background(Color(0xFF1C1C1C), RoundedCornerShape(16.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
            ) {
                Text("Games on this device", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "Deleting a game only frees up space. Your saves and saved spots are kept, and the game " +
                        "downloads again next time you play it.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = .75f),
                )
                ChoiceButton("Done", "Close this list", Modifier.focusRequester(focus)) { managing = false }
                games.forEach { g ->
                    ChoiceButton(
                        "Delete ${g.ref.name}",
                        "${g.ref.consoleName} · ${LocalGames.mb(g.bytes)}" + if (g.pinned) " · kept for trips" else "",
                    ) {
                        onRemove(g)
                        if (games.size <= 1) managing = false
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChoiceButton(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF2A2A2A), focusedContainerColor = Color(0xFF3A3A3A)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = .75f))
        }
    }
}
