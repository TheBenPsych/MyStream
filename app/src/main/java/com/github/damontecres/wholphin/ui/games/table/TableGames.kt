package com.github.damontecres.wholphin.ui.games.table

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixFont
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Button
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * The Table tab's games (Ben 2026-11-27: "focus on tabletop and card games"). Classic games anyone may make, played
 * on the TV with the remote (pass it around) or against Glowfin. The card games with phones come in the next update.
 */
enum class TableGame(
    val title: String,
    /** "Board game", "Card game" or "Dice game" */
    val kind: String,
    val players: String,
    val minutes: String,
    val about: String,
    val steps: List<String>,
    /** Can play against Glowfin */
    val versus: Boolean,
    val colors: List<Color>,
) {
    SOLITAIRE(
        "Solitaire",
        "Card game",
        "1 player",
        "About 10 min",
        "The classic one-player card game. Build four piles from ace to king, one suit each.",
        listOf(
            "Move cards onto the next card down in the other colour.",
            "OK picks a card up, OK again puts it down. OK twice sends it home.",
            "Only a king goes in an empty space.",
        ),
        false,
        listOf(Color(0xFF7C3AED), Color(0xFF2E1065)),
    ),
    CHECKERS(
        "Checkers",
        "Board game",
        "2 players",
        "About 15 min",
        "Jump your way across the board. Pass the remote, or play Glowfin.",
        listOf(
            "Move one square forward on the diagonal.",
            "Jumping a piece takes it, and you must jump when you can.",
            "Reach the far side to become a king that moves both ways.",
        ),
        true,
        listOf(Color(0xFFDC2626), Color(0xFF450A0A)),
    ),
    CHESS(
        "Chess",
        "Board game",
        "2 players",
        "About 30 min",
        "The full game, with every rule. Pass the remote, or play Glowfin at three levels.",
        listOf(
            "Pick a piece with OK. Dots show where it can go.",
            "Trap the other king to win.",
            "Back opens the menu, where you can take a move back.",
        ),
        true,
        listOf(Color(0xFF4338CA), Color(0xFF1E1B4B)),
    ),
    FOUR_ROW(
        "Four in a Row",
        "Board game",
        "2 players",
        "About 5 min",
        "Drop discs into the frame. First to get four in a line wins.",
        listOf("Left and right pick a column.", "OK drops your disc.", "Four in a row in any direction wins."),
        true,
        listOf(Color(0xFFCA8A04), Color(0xFF422006)),
    ),
    FIVE_DICE(
        "Five Dice",
        "Dice game",
        "1 to 6 players",
        "About 15 min",
        "Roll five dice up to three times a turn, keep the ones you like, and fill your score sheet.",
        listOf(
            "Roll, then OK on a die to keep it for the next roll.",
            "After up to three rolls, pick a box on your sheet.",
            "13 turns each. The biggest total wins.",
        ),
        true,
        listOf(Color(0xFF9333EA), Color(0xFF3B0764)),
    ),
    ;

    val needsSetup: Boolean get() = this != SOLITAIRE

    companion object {
        fun of(name: String): TableGame? = entries.firstOrNull { it.name == name }

        /** The Table tab's rows */
        val cardGames: List<TableGame> get() = listOf(SOLITAIRE)
        val boardGames: List<TableGame> get() = listOf(CHECKERS, CHESS, FOUR_ROW, FIVE_DICE)
    }
}

/** Who's playing: people passing the remote and Glowfin seats */
data class TableSeats(
    val people: Int = 1,
    /** 0 = no Glowfin; otherwise how many Glowfin players */
    val glowfin: Int = 0,
    /** 1 easy, 2 medium, 3 hard */
    val level: Int = 2,
)

@HiltViewModel
class TableViewModel
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        /** The signed-in person's first name, for "Ben's move" */
        val me: String
            get() =
                serverRepository.currentUser
                    ?.name
                    ?.trim()
                    ?.substringBefore(' ')
                    ?.takeIf { it.isNotBlank() } ?: "Player 1"

        fun leave() = navigationManager.goBack()
    }

/** The colours the Table games share */
internal object TableColors {
    val Felt = Color(0xFF0F4A56)
    val FeltEdge = Color(0xFF0B3640)
    val P1 = Color(0xFF3FE0E8)
    val P2 = Color(0xFFE2607A)
    val Gold = Color(0xFFFFC94D)
    val Light = Color(0xFFC9D3EC)
    val Dark = Color(0xFF3B5A8C)
    val Ink = Color(0xFF14172B)
    val Hint = Color(0xFFFFFFFF)
    val Pick = Color(0xFFFFD84A)
}

/** Remote keys for a game board: arrows, OK and Play/Pause. Taps on phones are handled by the board itself. */
internal fun Modifier.tableKeys(
    focus: FocusRequester,
    onMove: (dx: Int, dy: Int) -> Unit,
    onOk: () -> Unit,
    onPlay: () -> Unit = onOk,
): Modifier =
    this
        .focusRequester(focus)
        .focusable()
        .onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.DirectionLeft -> {
                    onMove(-1, 0)
                    true
                }

                Key.DirectionRight -> {
                    onMove(1, 0)
                    true
                }

                Key.DirectionUp -> {
                    onMove(0, -1)
                    true
                }

                Key.DirectionDown -> {
                    onMove(0, 1)
                    true
                }

                Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA -> {
                    onOk()
                    true
                }

                Key.MediaPlayPause, Key.MediaPlay, Key.Spacebar -> {
                    onPlay()
                    true
                }

                else -> {
                    false
                }
            }
        }

/** A tap (phones and tablets) that always runs the newest [action], so it never acts on an old board */
internal fun Modifier.onTap(action: () -> Unit): Modifier =
    composed {
        val latest by rememberUpdatedState(action)
        pointerInput(Unit) { detectTapGestures { latest() } }
    }

/** A menu button in the Table games' own style */
@Composable
internal fun TableButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    sub: String? = null,
) {
    Button(
        onClick = onClick,
        colors =
            if (primary) {
                ButtonDefaults.colors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    focusedContainerColor = Color.White,
                    focusedContentColor = Color.Black,
                )
            } else {
                ButtonDefaults.colors(
                    containerColor = Color.White.copy(alpha = .1f),
                    contentColor = Color.White,
                    focusedContainerColor = Color.White,
                    focusedContentColor = Color.Black,
                )
            },
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(label, fontSize = BenflixType.lead, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (sub != null) Text(sub, fontSize = BenflixType.small, textAlign = TextAlign.Center)
        }
    }
}

/** Big words in a panel beside the board */
@Composable
internal fun TableTitle(
    text: String,
    color: Color = Color.White,
) {
    Text(text, fontFamily = BenflixFont, fontSize = BenflixType.pageTitle, fontWeight = FontWeight.Bold, color = color)
}

@Composable
internal fun TableLine(
    text: String,
    color: Color = BenflixColors.Soft,
    bold: Boolean = false,
) {
    Text(
        text,
        fontSize = BenflixType.body,
        lineHeight = BenflixType.body * 1.35f,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = color,
    )
}

/** The menu Back opens in every Table game, and the end-of-game card */
@Composable
internal fun TableMenu(
    title: String,
    lines: List<String>,
    buttons: List<Pair<String, () -> Unit>>,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.tryRequestFocus() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = .72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .widthIn(max = 420.dp)
                    .background(BenflixColors.Panel, BenflixShapes.Panel)
                    .border(1.dp, BenflixColors.Line, BenflixShapes.Panel)
                    .padding(24.dp),
        ) {
            TableTitle(title)
            lines.forEach { TableLine(it) }
            buttons.forEachIndexed { i, (label, act) ->
                TableButton(
                    label,
                    act,
                    primary = i == 0,
                    modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                )
            }
        }
    }
}

/**
 * One Table game's page: the "who's playing" step (when the game has one), then the game. Back opens the menu.
 */
@Composable
fun TablePlayPage(
    name: String,
    modifier: Modifier = Modifier,
    viewModel: TableViewModel =
        androidx.hilt.lifecycle.viewmodel.compose
            .hiltViewModel(),
) {
    val game = remember(name) { TableGame.of(name) } ?: return
    var seats by remember { mutableStateOf<TableSeats?>(if (game.needsSetup) null else TableSeats()) }
    // A new number starts a fresh game with the same players
    var round by remember { mutableIntStateOf(0) }
    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF0B0D1A)),
    ) {
        val s = seats
        if (s == null) {
            TableSetup(game, viewModel.me, onStart = { seats = it }, onLeave = viewModel::leave)
        } else {
            val again = { round++ }
            val leave = viewModel::leave
            androidx.compose.runtime.key(round) {
                when (game) {
                    TableGame.SOLITAIRE -> SolitaireGame(again, leave)
                    TableGame.CHECKERS -> CheckersGame(s, viewModel.me, again, leave)
                    TableGame.CHESS -> ChessGame(s, viewModel.me, again, leave)
                    TableGame.FOUR_ROW -> FourRowGame(s, viewModel.me, again, leave)
                    TableGame.FIVE_DICE -> FiveDiceGame(s, viewModel.me, again, leave)
                }
            }
        }
    }
}

/** "Who's playing?" before a game starts */
@Composable
private fun TableSetup(
    game: TableGame,
    me: String,
    onStart: (TableSeats) -> Unit,
    onLeave: () -> Unit,
) {
    BackHandler { onLeave() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.tryRequestFocus() }
    var people by remember { mutableIntStateOf(1) }
    var bots by remember { mutableIntStateOf(1) }
    var level by remember { mutableIntStateOf(2) }
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 36.dp),
    ) {
        Text(
            game.kind.uppercase(),
            fontSize = BenflixType.tag,
            fontWeight = FontWeight.Bold,
            color = BenflixColors.Games,
        )
        TableTitle(game.title)
        TableLine("Who's playing?")
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
        ) {
            if (game == TableGame.FIVE_DICE) {
                Stepper("People", people, 1..6, Modifier.focusRequester(first)) { v ->
                    people = v
                    if (people + bots > 6) bots = 6 - people
                }
                Stepper("Glowfin players", bots, 0..(6 - people)) { bots = it }
                if (bots > 0) LevelRow(level) { level = it }
                TableButton(
                    "Start",
                    { onStart(TableSeats(people = people, glowfin = bots, level = level)) },
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TableLine("People take turns with the remote. Glowfin plays its own turns.", BenflixColors.Muted)
            } else {
                TableButton(
                    "Play Glowfin",
                    { onStart(TableSeats(people = 1, glowfin = 1, level = level)) },
                    primary = true,
                    sub = "$me against Glowfin",
                    modifier = Modifier.fillMaxWidth().focusRequester(first),
                )
                LevelRow(level) { level = it }
                TableButton(
                    "Two people",
                    { onStart(TableSeats(people = 2, glowfin = 0)) },
                    sub = "Pass the remote after each move",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TableButton("Back", onLeave, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun LevelRow(
    level: Int,
    onPick: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("Easy", "Medium", "Hard").forEachIndexed { i, label ->
            val on = level == i + 1
            TableButton(
                (if (on) "● " else "") + label,
                { onPick(i + 1) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    range: IntRange,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, fontSize = BenflixType.lead, color = Color.White, modifier = Modifier.weight(1f))
        TableButton("−", { if (value > range.first) onChange(value - 1) }, modifier = modifier.widthIn(min = 56.dp))
        Text(
            "$value",
            fontSize = BenflixType.rowName,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 36.dp),
        )
        TableButton("+", { if (value < range.last) onChange(value + 1) }, modifier = Modifier.widthIn(min = 56.dp))
    }
}
