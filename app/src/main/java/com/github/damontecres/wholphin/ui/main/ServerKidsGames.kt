package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.GameLibraryClient
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

/** One game on the Kids games list */
data class KidsGame(
    val id: Int,
    val name: String,
    val console: String,
    /** e.g. "E10+" or "No rating" */
    val rating: String,
    /** "everyone", "teen", "grown" or "none" */
    val level: String,
    val kids: Boolean,
    val big: Boolean,
    /** Switched by hand (not just from its age rating) */
    val chosen: Boolean,
)

/**
 * Benflix Server page (People > Kids games), Idea Box 24: every game with a switch for Kids and Big kids. Games rated
 * for everyone start on for Kids, E10+ and Teen ones for Big kids. Games with no rating start off, so they're listed
 * first: switch on the ones that are fine. Tower keeps the choices, so every TV, phone and browser follows them.
 */
@HiltViewModel
class KidsGamesViewModel
    @Inject
    constructor(
        private val client: GameLibraryClient,
    ) : ViewModel() {
        private val _games = MutableStateFlow<List<KidsGame>?>(null)

        /** null while looking */
        val games: StateFlow<List<KidsGame>?> = _games

        private val _problem = MutableStateFlow<String?>(null)
        val problem: StateFlow<String?> = _problem

        fun load() {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    val arr = client.getJson("/api/games/kids").optJSONArray("games")
                    _games.value =
                        (0 until (arr?.length() ?: 0)).map { i ->
                            val g = arr!!.getJSONObject(i)
                            KidsGame(
                                id = g.optInt("id"),
                                name = g.optString("name"),
                                console = g.optString("console"),
                                rating = g.optString("rating", "No rating"),
                                level = g.optString("level", "none"),
                                kids = g.optBoolean("kids"),
                                big = g.optBoolean("big"),
                                chosen = g.optBoolean("chosen"),
                            )
                        }
                    _problem.value = null
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Kids games")
                    _problem.value = ex.message ?: "Couldn't ask Tower."
                }
            }
        }

        /** [kids] / [big]: the new switches; [reset] goes back to what the age rating says */
        fun set(
            game: KidsGame,
            kids: Boolean?,
            big: Boolean?,
            reset: Boolean,
            onDone: (String) -> Unit,
        ) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                val words =
                    try {
                        val body = JSONObject().put("id", game.id).put("reset", reset)
                        kids?.let { body.put("kids", it) }
                        big?.let { body.put("big", it) }
                        client.postJson("/api/games/kids", body)
                        load()
                        when {
                            reset -> "${game.name} follows its age rating again."
                            kids == true -> "${game.name} is on for Kids and Big kids."
                            big == true -> "${game.name} is on for Big kids."
                            else -> "${game.name} is off for Kids."
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Kids games: save")
                        "Couldn't save that. Try again."
                    }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDone(words) }
            }
        }
    }

internal fun LazyListScope.kidsGamesItems() {
    item { SectionTitle("Kids games") }
    item(key = "kids-games") { KidsGamesRows() }
}

private fun KidsGame.who(): String =
    when {
        kids -> "Kids and Big kids"
        big -> "Big kids only"
        else -> "Not on Kids"
    }

@Composable
private fun KidsGamesRows(viewModel: KidsGamesViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.load() }
    val games by viewModel.games.collectAsState()
    val problem by viewModel.problem.collectAsState()
    var picking by remember { mutableStateOf<KidsGame?>(null) }
    var showAll by remember { mutableStateOf(false) }
    val list = games
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            list == null && problem != null -> {
                ServerActionRow(
                    headline = "Couldn't get the games list",
                    supporting = problem,
                    leading = { Dot(Light.UNKNOWN) },
                    onClick = { viewModel.load() },
                    actions = listOf("Try again" to { viewModel.load() }),
                )
            }

            list == null -> {
                LoadingBox("Getting the games…")
            }

            list.isEmpty() -> {
                ServerActionRow(
                    headline = "No games yet",
                    supporting = "Games you add to RomM show up here.",
                    leading = { Dot(Light.UNKNOWN) },
                )
            }

            else -> {
                val unrated = list.count { it.level == "none" && !it.chosen }
                ServerActionRow(
                    headline =
                        "${list.count { it.kids }} for Kids, ${list.count { it.big }} for Big kids" +
                            if (unrated > 0) " · $unrated with no rating" else "",
                    supporting =
                        "Games rated E start on for Kids, E10+ and Teen for Big kids. Games with no rating start off, " +
                            "so they're listed first. Pick a game to switch it on or off.",
                    leading = { Dot(if (unrated > 0) Light.WARN else Light.GOOD) },
                )
                val shown = if (showAll) list else list.take(SHOW_FIRST)
                shown.forEach { game ->
                    ServerActionRow(
                        headline = game.name,
                        supporting =
                            listOf(
                                game.console,
                                game.rating,
                                game.who() + if (game.chosen) " (your pick)" else "",
                            ).joinToString(" · "),
                        onClick = { picking = game },
                        actions = listOf("Change" to { picking = game }),
                    )
                }
                if (!showAll && list.size > SHOW_FIRST) {
                    ServerActionRow(
                        headline = "Show all ${list.size}",
                        onClick = { showAll = true },
                        actions = listOf("Show all" to { showAll = true }),
                    )
                }
            }
        }
    }
    picking?.let { game ->
        BasicDialog(onDismissRequest = { picking = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(8.dp)) {
                Text(
                    text = game.name,
                    color = BenflixColors.Text,
                    fontSize = BenflixType.rowName,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${game.console} · ${game.rating}. Now: ${game.who()}.",
                    color = BenflixColors.Soft,
                    fontSize = BenflixType.body,
                )
                val done: (String) -> Unit = { words ->
                    com.github.damontecres.wholphin.ui.components.BenflixNotices
                        .pill(words)
                }
                val choices =
                    buildList {
                        add("Kids and Big kids" to { viewModel.set(game, kids = true, big = true, reset = false, onDone = done) })
                        add("Big kids only" to { viewModel.set(game, kids = false, big = true, reset = false, onDone = done) })
                        add("Not on Kids" to { viewModel.set(game, kids = false, big = false, reset = false, onDone = done) })
                        if (game.chosen) add("Follow its age rating" to { viewModel.set(game, null, null, reset = true, onDone = done) })
                    }
                choices.forEach { (label, action) ->
                    Button(
                        onClick = {
                            picking = null
                            action()
                        },
                        scale = ButtonDefaults.scale(focusedScale = 1f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** The first few show straight away; the rest behind "Show all" */
private const val SHOW_FIRST = 8

/** One game a TV, phone or browser said didn't start */
data class GameProblem(
    val id: Int,
    val name: String,
    val console: String,
    val what: String,
    val device: String,
    val who: String,
    val at: Long,
)

/**
 * Benflix Server page (Playback fixes > Games that didn't start), Idea Box 24: games that showed only a black
 * screen, couldn't load, or closed by themselves in their first half minute, on any TV, phone or browser.
 */
@HiltViewModel
class GameProblemsViewModel
    @Inject
    constructor(
        private val client: GameLibraryClient,
    ) : ViewModel() {
        private val _problems = MutableStateFlow<List<GameProblem>?>(null)
        val problems: StateFlow<List<GameProblem>?> = _problems

        private val _failed = MutableStateFlow<String?>(null)
        val failed: StateFlow<String?> = _failed

        fun load() {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    val arr = client.getJson("/api/games/problems").optJSONArray("problems")
                    _problems.value =
                        (0 until (arr?.length() ?: 0)).map { i ->
                            val p = arr!!.getJSONObject(i)
                            GameProblem(
                                id = p.optInt("id"),
                                name = p.optString("name"),
                                console = p.optString("console"),
                                what = p.optString("what"),
                                device = p.optString("device"),
                                who = p.optString("who"),
                                at = p.optLong("at"),
                            )
                        }
                    _failed.value = null
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Game problems")
                    _failed.value = ex.message ?: "Couldn't ask Tower."
                }
            }
        }

        /** Takes one game (or, with null, every game) off the list */
        fun clear(id: Int?) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    client.deleteJson("/api/games/problems" + (id?.let { "?id=$it" } ?: ""))
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Game problems: clear")
                }
                load()
            }
        }
    }

internal fun LazyListScope.gameProblemItems() {
    item { SectionTitle("Games that didn't start") }
    item(key = "game-problems") { GameProblemRows() }
}

private fun daysAgo(at: Long): String {
    val days = ((System.currentTimeMillis() / 1000 - at) / 86_400).toInt()
    return when {
        days <= 0 -> "Today"
        days == 1 -> "Yesterday"
        else -> "$days days ago"
    }
}

@Composable
private fun GameProblemRows(viewModel: GameProblemsViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.load() }
    val problems by viewModel.problems.collectAsState()
    val failed by viewModel.failed.collectAsState()
    val list = problems
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            list == null && failed != null -> {
                ServerActionRow(
                    headline = "Couldn't get the list",
                    supporting = failed,
                    leading = { Dot(Light.UNKNOWN) },
                    onClick = { viewModel.load() },
                    actions = listOf("Try again" to { viewModel.load() }),
                )
            }

            list == null -> {
                LoadingBox("Checking…")
            }

            list.isEmpty() -> {
                ServerActionRow(
                    headline = "Every game started fine",
                    supporting = "If a game shows only a black screen, can't load, or closes by itself, it shows up here.",
                    leading = { Dot(Light.GOOD) },
                )
            }

            else -> {
                ServerActionRow(
                    headline = if (list.size == 1) "1 game didn't start" else "${list.size} games didn't start",
                    supporting =
                        "Usually the copy is damaged (replace it in RomM) or the console needs a BIOS file in Games/bios. " +
                            "Pick Fixed once it's sorted.",
                    leading = { Dot(Light.WARN) },
                    actions = listOf("Clear the list" to { viewModel.clear(null) }),
                )
                list.forEach { p ->
                    ServerActionRow(
                        headline = p.name.ifBlank { "Game ${p.id}" },
                        supporting =
                            listOf(
                                p.what,
                                p.console,
                                p.device,
                                p.who,
                                daysAgo(p.at),
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                        actions = listOf("Fixed" to { viewModel.clear(p.id) }),
                    )
                }
            }
        }
    }
}
