package com.github.damontecres.wholphin.ui.games.table

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The board games' shared layout: on a TV (or a sideways phone) the words sit in a panel on the left and the board
 * fills the height; on an upright phone the board is on top and the words under it.
 */
@Composable
internal fun BoardLayout(
    menuOpen: Boolean,
    onBack: () -> Unit,
    side: @Composable ColumnScope.() -> Unit,
    overlay: @Composable () -> Unit,
    board: @Composable (Dp) -> Unit,
) {
    BackHandler(enabled = !menuOpen) { onBack() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        if (wide) {
            val size = minOf(maxHeight - 40.dp, maxWidth * 0.62f)
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 20.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                    content = side,
                )
                board(size)
            }
        } else {
            val size = minOf(maxWidth - 24.dp, maxHeight * 0.6f)
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxSize().padding(12.dp),
            ) {
                board(size)
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    content = side,
                )
            }
        }
        overlay()
    }
}

/** A player's name tag beside the board: their colour, and a frame when it's their turn */
@Composable
internal fun PlayerTag(
    name: String,
    color: Color,
    turn: Boolean,
    extra: String = "",
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (turn) Color.White.copy(alpha = .1f) else Color.Transparent, BenflixShapes.Card)
                .border(if (turn) 2.dp else 0.dp, if (turn) color else Color.Transparent, BenflixShapes.Card)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(Modifier.size(18.dp).background(color, CircleShape))
        Column(Modifier.weight(1f)) {
            TableLine(name, Color.White, bold = true)
            if (extra.isNotBlank()) TableLine(extra, BenflixColors.Muted)
        }
    }
}

/**
 * The games keep their pieces in plain objects, not Compose state, and bump a version number after every move.
 * Reading that number here makes this part of the screen redraw after each move.
 */
@Suppress("UNUSED_PARAMETER")
internal fun redrawOn(version: Int) = Unit

/** Runs Glowfin's move off the main thread after a short pause, so it feels like it's thinking */
private suspend fun <T> glowfinThinks(work: () -> T): T {
    delay(450)
    return withContext(Dispatchers.Default) { work() }
}

/** One square of an 8 x 8 board, with the cursor, a pick and a "can go here" dot */
@Composable
private fun Square(
    cell: Dp,
    color: Color,
    cursor: Boolean,
    picked: Boolean,
    dot: Boolean,
    lastMove: Boolean,
    onTap: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(cell)
                .background(color)
                .then(if (lastMove) Modifier.background(TableColors.Pick.copy(alpha = .28f)) else Modifier)
                .then(if (picked) Modifier.background(TableColors.Pick.copy(alpha = .55f)) else Modifier)
                .onTap { onTap() },
    ) {
        content()
        if (dot) Box(Modifier.size(cell * .26f).background(Color.Black.copy(alpha = .35f), CircleShape))
        if (cursor) Box(Modifier.fillMaxSize().border(4.dp, Color.White))
    }
}

/** Moves a cursor on an 8 x 8 board */
private fun step(
    sq: Int,
    dx: Int,
    dy: Int,
): Int {
    val r = (sq / 8 + dy).coerceIn(0, 7)
    val c = (sq % 8 + dx).coerceIn(0, 7)
    return r * 8 + c
}

// ---------------------------------------------------------------- Checkers

@Composable
internal fun CheckersGame(
    seats: TableSeats,
    me: String,
    again: () -> Unit,
    leave: () -> Unit,
) {
    val isTv = rememberIsTv()
    val game = remember { Checkers() }
    val history = remember { ArrayList<Checkers>() }
    var version by remember { mutableIntStateOf(0) }
    var cursor by remember { mutableIntStateOf(5 * 8) }
    var picked by remember { mutableIntStateOf(-1) }
    var last by remember { mutableStateOf<Checkers.Move?>(null) }
    var menu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val vs = seats.glowfin > 0
    val names = listOf(me, if (vs) "Glowfin" else "Player 2")
    val moves = remember(version) { game.moves() }
    val result = remember(version) { game.result() }
    val glowfinTurn = vs && game.turn == 2 && result == 0

    fun play(m: Checkers.Move) {
        history.add(game.copy())
        game.play(m)
        last = m
        picked = -1
        version++
    }

    LaunchedEffect(version) {
        if (glowfinTurn) {
            val m = glowfinThinks { game.copy().bestMove(seats.level) }
            if (m != null) play(m)
        }
    }
    LaunchedEffect(menu, result) { if (!menu && result == 0) focus.tryRequestFocus() }

    fun act(sq: Int) {
        if (glowfinTurn || result != 0) return
        cursor = sq
        val mine = moves.filter { it.from == sq }
        when {
            mine.isNotEmpty() -> {
                picked = if (picked == sq) -1 else sq
            }

            picked >= 0 -> {
                moves.firstOrNull { it.from == picked && it.to == sq }?.let { play(it) } ?: run { picked = -1 }
            }
        }
    }

    val targets = if (picked >= 0) moves.filter { it.from == picked }.map { it.to }.toSet() else emptySet()
    val movable = moves.map { it.from }.toSet()
    BoardLayout(
        menuOpen = menu,
        onBack = { menu = true },
        side = {
            redrawOn(version)
            TableTitle("Checkers")
            PlayerTag(names[0], TableColors.P1, game.turn == 1 && result == 0, "${game.count(1)} pieces")
            PlayerTag(names[1], TableColors.P2, game.turn == 2 && result == 0, "${game.count(2)} pieces")
            val jumps = moves.any { it.jumped.isNotEmpty() }
            TableLine(
                when {
                    glowfinTurn -> "Glowfin is thinking…"
                    picked >= 0 -> "Now pick where it goes."
                    jumps -> "${names[game.turn - 1]}: you must jump."
                    else -> "${names[game.turn - 1]}'s move. Pick a piece."
                },
                Color.White,
            )
            TableLine(if (isTv) "Back opens the menu." else "Back opens the menu. Tap a piece, then a square.", BenflixColors.Muted)
        },
        overlay = {
            redrawOn(version)
            if (result != 0) {
                TableMenu(
                    if (result == 3) "It's a draw" else "${names[result - 1]} wins!",
                    listOf(if (result == 3) "Too many moves with no jumps." else "No moves left for ${names[2 - result]}."),
                    listOf("Play again" to again, "Leave" to leave),
                )
            } else if (menu) {
                TableMenu(
                    "Checkers",
                    emptyList(),
                    listOfNotNull(
                        "Keep playing" to { menu = false },
                        if (history.isNotEmpty()) {
                            "Take back a move" to {
                                // Against Glowfin, go back to your own last move
                                var back = 1
                                if (vs && history.size >= 2) back = 2
                                repeat(back) { history.removeLastOrNull()?.let { s -> copyCheckers(s, game) } }
                                last = null
                                picked = -1
                                version++
                                menu = false
                            }
                        } else {
                            null
                        },
                        "New game" to again,
                        "Leave" to leave,
                    ),
                )
            }
        },
    ) { size ->
        redrawOn(version)
        val cell = size / 8
        Column(
            Modifier
                .size(size)
                .border(6.dp, TableColors.FeltEdge)
                .tableKeys(focus, onMove = { dx, dy -> cursor = step(cursor, dx, dy) }, onOk = { act(cursor) }),
        ) {
            for (r in 0..7) {
                Row {
                    for (c in 0..7) {
                        val sq = r * 8 + c
                        val p = game.board[sq]
                        Square(
                            cell = cell,
                            color = if (Checkers.dark(sq)) TableColors.Dark else TableColors.Light,
                            cursor = isTv && cursor == sq && result == 0 && !menu,
                            picked = picked == sq,
                            dot = sq in targets,
                            lastMove = last?.path?.contains(sq) == true,
                            onTap = { act(sq) },
                        ) {
                            if (p != 0) {
                                val color = if (p > 0) TableColors.P1 else TableColors.P2
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier =
                                        Modifier
                                            .size(cell * .78f)
                                            .background(color, CircleShape)
                                            .border(3.dp, Color.White.copy(alpha = .55f), CircleShape)
                                            .alpha(if (picked < 0 && sq !in movable && game.owner(sq) == game.turn) .85f else 1f),
                                ) {
                                    if (game.isKing(sq)) {
                                        Text("♚︎", fontSize = (cell.value * .42f).sp, color = TableColors.Ink)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun copyCheckers(
    from: Checkers,
    to: Checkers,
) {
    from.board.copyInto(to.board)
    to.turn = from.turn
    to.quiet = from.quiet
}

// ---------------------------------------------------------------- Chess

private const val CHESS_GLYPHS = " ♟♞♝♜♛♚"

@Composable
internal fun ChessGame(
    seats: TableSeats,
    me: String,
    again: () -> Unit,
    leave: () -> Unit,
) {
    val isTv = rememberIsTv()
    val game = remember { Chess() }
    var version by remember { mutableIntStateOf(0) }
    var cursor by remember { mutableIntStateOf(6 * 8 + 4) }
    var picked by remember { mutableIntStateOf(-1) }
    var menu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val vs = seats.glowfin > 0
    val names = listOf(me, if (vs) "Glowfin" else "Player 2")
    val moves = remember(version) { game.legalMoves() }
    val state = remember(version) { game.state() }
    val check = remember(version) { game.inCheck() }
    val playing = state == Chess.State.PLAYING
    val turnIndex = if (game.side == 1) 0 else 1
    val glowfinTurn = vs && game.side == -1 && playing

    fun play(m: Int) {
        game.play(m)
        picked = -1
        version++
    }

    LaunchedEffect(version) {
        if (glowfinTurn) {
            val m = glowfinThinks { game.copy().bestMove(seats.level) }
            if (m != null) play(m)
        }
    }
    LaunchedEffect(menu, playing) { if (!menu && playing) focus.tryRequestFocus() }

    fun act(sq: Int) {
        if (glowfinTurn || !playing) return
        cursor = sq
        val mine = moves.filter { Chess.fromSq(it) == sq }
        when {
            mine.isNotEmpty() -> {
                picked = if (picked == sq) -1 else sq
            }

            picked >= 0 -> {
                // A pawn reaching the end always becomes a queen
                moves
                    .filter { Chess.fromSq(it) == picked && Chess.toSq(it) == sq }
                    .maxByOrNull { Chess.promotion(it) }
                    ?.let { play(it) } ?: run { picked = -1 }
            }
        }
    }

    val targets = if (picked >= 0) moves.filter { Chess.fromSq(it) == picked }.map { Chess.toSq(it) }.toSet() else emptySet()
    val lastMove = game.played.lastOrNull()
    val lastSquares = lastMove?.let { setOf(Chess.fromSq(it), Chess.toSq(it)) } ?: emptySet()
    BoardLayout(
        menuOpen = menu,
        onBack = { menu = true },
        side = {
            redrawOn(version)
            TableTitle("Chess")
            PlayerTag(names[1], Color(0xFF1E2238), turnIndex == 1 && playing, "Black")
            PlayerTag(names[0], Color.White, turnIndex == 0 && playing, "White")
            TableLine(
                when {
                    glowfinTurn -> "Glowfin is thinking…"
                    check -> "${names[turnIndex]}: your king is in check!"
                    picked >= 0 -> "Now pick where it goes."
                    else -> "${names[turnIndex]}'s move."
                },
                if (check) TableColors.P2 else Color.White,
                bold = check,
            )
            lastMove?.let { TableLine("Last move: ${Chess.name(Chess.fromSq(it))} → ${Chess.name(Chess.toSq(it))}", BenflixColors.Muted) }
            TableLine(if (isTv) "Back opens the menu." else "Tap a piece, then a square. Back opens the menu.", BenflixColors.Muted)
        },
        overlay = {
            redrawOn(version)
            if (!playing) {
                val winner = if (state == Chess.State.CHECKMATE) names[1 - turnIndex] else null
                TableMenu(
                    if (winner != null) "Checkmate! $winner wins" else "It's a draw",
                    listOf(
                        when (state) {
                            Chess.State.STALEMATE -> "No legal moves, but no check: stalemate."
                            Chess.State.FIFTY -> "50 moves each with no capture or pawn move."
                            Chess.State.REPEAT -> "The same position came up three times."
                            Chess.State.MATERIAL -> "Not enough pieces left to checkmate."
                            else -> "Well played."
                        },
                    ),
                    listOf("Play again" to again, "Leave" to leave),
                )
            } else if (menu) {
                TableMenu(
                    "Chess",
                    emptyList(),
                    listOfNotNull(
                        "Keep playing" to { menu = false },
                        if (game.played.isNotEmpty()) {
                            "Take back a move" to {
                                game.takeBack()
                                if (vs && game.side == -1) game.takeBack()
                                picked = -1
                                version++
                                menu = false
                            }
                        } else {
                            null
                        },
                        "New game" to again,
                        "Leave" to leave,
                    ),
                )
            }
        },
    ) { size ->
        redrawOn(version)
        val cell = size / 8
        val king = if (check) game.kingOf(game.side) else -1
        Column(
            Modifier
                .size(size)
                .border(6.dp, Color(0xFF1E2238))
                .tableKeys(focus, onMove = { dx, dy -> cursor = step(cursor, dx, dy) }, onOk = { act(cursor) }),
        ) {
            for (r in 0..7) {
                Row {
                    for (c in 0..7) {
                        val sq = r * 8 + c
                        val p = game.board[sq]
                        Square(
                            cell = cell,
                            color =
                                if (sq == king) {
                                    TableColors.P2
                                } else if ((r + c) % 2 == 1) {
                                    TableColors.Dark
                                } else {
                                    TableColors.Light
                                },
                            cursor = isTv && cursor == sq && playing && !menu,
                            picked = picked == sq,
                            dot = sq in targets,
                            lastMove = sq in lastSquares,
                            onTap = { act(sq) },
                        ) {
                            if (p != 0) {
                                Text(
                                    CHESS_GLYPHS[kotlin.math.abs(p)] + "︎",
                                    style =
                                        TextStyle(
                                            fontSize = (cell.value * .7f).sp,
                                            color = if (p > 0) Color.White else TableColors.Ink,
                                            shadow =
                                                if (p > 0) {
                                                    Shadow(Color.Black, blurRadius = 6f)
                                                } else {
                                                    Shadow(Color.White.copy(alpha = .5f), blurRadius = 3f)
                                                },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Four in a Row

@Composable
internal fun FourRowGame(
    seats: TableSeats,
    me: String,
    again: () -> Unit,
    leave: () -> Unit,
) {
    val game = remember { FourRow() }
    val history = remember { ArrayList<FourRow>() }
    var version by remember { mutableIntStateOf(0) }
    var column by remember { mutableIntStateOf(3) }
    var menu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val vs = seats.glowfin > 0
    val names = listOf(me, if (vs) "Glowfin" else "Player 2")
    val result = remember(version) { game.result() }
    val line = remember(version) { game.winLine()?.toSet() ?: emptySet() }
    val glowfinTurn = vs && game.turn == 2 && result == 0
    val colors = listOf(TableColors.P1, TableColors.Gold)

    fun drop(c: Int) {
        if (!game.canDrop(c)) return
        history.add(game.copy())
        game.drop(c)
        version++
    }

    LaunchedEffect(version) {
        if (glowfinTurn) {
            val c = glowfinThinks { game.copy().bestColumn(seats.level) }
            if (c >= 0) drop(c)
        }
    }
    LaunchedEffect(menu, result) { if (!menu && result == 0) focus.tryRequestFocus() }

    fun act(c: Int) {
        if (glowfinTurn || result != 0) return
        column = c
        drop(c)
    }

    BoardLayout(
        menuOpen = menu,
        onBack = { menu = true },
        side = {
            redrawOn(version)
            TableTitle("Four in a Row")
            PlayerTag(names[0], colors[0], game.turn == 1 && result == 0)
            PlayerTag(names[1], colors[1], game.turn == 2 && result == 0)
            TableLine(
                if (glowfinTurn) "Glowfin is thinking…" else "${names[game.turn - 1]}: pick a column and press OK.",
                Color.White,
            )
            TableLine("Back opens the menu.", BenflixColors.Muted)
        },
        overlay = {
            redrawOn(version)
            if (result != 0) {
                TableMenu(
                    if (result == 3) "It's a draw" else "${names[result - 1]} wins!",
                    listOf(if (result == 3) "The frame is full." else "Four in a row."),
                    listOf("Play again" to again, "Leave" to leave),
                )
            } else if (menu) {
                TableMenu(
                    "Four in a Row",
                    emptyList(),
                    listOfNotNull(
                        "Keep playing" to { menu = false },
                        if (history.isNotEmpty()) {
                            "Take back a move" to {
                                val back = if (vs && history.size >= 2) 2 else 1
                                repeat(back) {
                                    history.removeLastOrNull()?.let { s ->
                                        s.cells.copyInto(game.cells)
                                        game.turn = s.turn
                                    }
                                }
                                version++
                                menu = false
                            }
                        } else {
                            null
                        },
                        "New game" to again,
                        "Leave" to leave,
                    ),
                )
            }
        },
    ) { size ->
        redrawOn(version)
        // 7 wide and 6 tall, plus a row on top for the disc you're about to drop
        val cell = minOf(size / 7, size / 7f * 1f)
        Column(
            Modifier
                .width(cell * 7)
                .tableKeys(focus, onMove = { dx, _ -> column = (column + dx).coerceIn(0, 6) }, onOk = { act(column) }),
        ) {
            Row {
                for (c in 0 until FourRow.COLS) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(cell).onTap { act(c) },
                    ) {
                        if (c == column && result == 0 && !glowfinTurn) {
                            Box(Modifier.size(cell * .7f).background(colors[game.turn - 1], CircleShape))
                        }
                    }
                }
            }
            Column(
                Modifier
                    .background(Color(0xFF2346A8), BenflixShapes.Card)
                    .padding(cell * .05f),
            ) {
                for (r in 0 until FourRow.ROWS) {
                    Row {
                        for (c in 0 until FourRow.COLS) {
                            val v = game.cells[r * FourRow.COLS + c]
                            val idx = r * FourRow.COLS + c
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(cell * .9f).onTap { act(c) },
                            ) {
                                Box(
                                    Modifier
                                        .size(cell * .74f)
                                        .background(if (v == 0) Color(0xFF0B0D1A) else colors[v - 1], CircleShape)
                                        .then(if (idx in line) Modifier.border(4.dp, Color.White, CircleShape) else Modifier),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
