package com.github.damontecres.wholphin.ui.games.table

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay

private val CardRed = Color(0xFFD7263D)

/** One playing card, face up or down (null [card] draws an empty spot) */
@Composable
internal fun PlayingCard(
    card: Card?,
    faceUp: Boolean,
    width: Dp,
    modifier: Modifier = Modifier,
    picked: Boolean = false,
    cursor: Boolean = false,
) {
    val shape = RoundedCornerShape(width * .1f)
    val h = width * 1.4f
    Box(
        modifier
            .size(width, h)
            .then(
                when {
                    card == null -> Modifier.border(2.dp, Color.White.copy(alpha = .28f), shape)
                    !faceUp -> Modifier.background(Brush.linearGradient(listOf(Color(0xFF1FA3B5), Color(0xFF233C8C))), shape)
                    else -> Modifier.background(if (picked) Color(0xFFFFF3C4) else Color.White, shape)
                },
            ).then(if (card != null && !faceUp) Modifier.border(2.dp, Color.White.copy(alpha = .5f), shape) else Modifier)
            .then(if (picked) Modifier.border(3.dp, TableColors.Pick, shape) else Modifier)
            .then(if (cursor) Modifier.border(4.dp, if (picked) TableColors.Pick else TableColors.P1, shape) else Modifier),
    ) {
        if (card != null && faceUp) {
            val color = if (card.red) CardRed else TableColors.Ink
            Text(
                card.rankName + card.suitMark + "︎",
                fontSize = (width.value * .27f).sp,
                lineHeight = (width.value * .3f).sp,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.padding(start = width * .07f, top = width * .02f),
            )
            Text(
                card.suitMark + "︎",
                fontSize = (width.value * .5f).sp,
                color = color,
                modifier = Modifier.align(Alignment.Center).offset(y = h * .1f),
            )
        }
    }
}

// ---------------------------------------------------------------- Solitaire

/** Where the remote's cursor is in Solitaire: the top row (0 stock, 1 turned cards, 3-6 homes) or a column card */
private data class SolCursor(
    val top: Boolean,
    val slot: Int,
    val index: Int = -1,
)

@Composable
internal fun SolitaireGame(
    again: () -> Unit,
    leave: () -> Unit,
) {
    val isTv = rememberIsTv()
    val game = remember { Solitaire() }
    var version by remember { mutableIntStateOf(0) }
    var cursor by remember { mutableStateOf(SolCursor(false, 0, 0)) }
    // What's picked up: the pile and the card's index in it
    var picked by remember { mutableStateOf<Pair<Solitaire.Pile, Int>?>(null) }
    var menu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val won = remember(version) { game.won() }
    val finishing = remember(version) { game.canFinish() }

    fun changed() {
        version++
    }

    BackHandler(enabled = !menu && !won) {
        if (picked != null) picked = null else menu = true
    }
    LaunchedEffect(menu, won) { if (!menu && !won) focus.tryRequestFocus() }
    LaunchedEffect(finishing) {
        if (finishing) {
            picked = null
            while (game.finishStep()) {
                changed()
                delay(110)
            }
            changed()
        }
    }

    fun pileAt(c: SolCursor): Pair<Solitaire.Pile, Int>? =
        when {
            c.top && c.slot == 1 -> Solitaire.Pile.Waste to -1
            c.top && c.slot >= 3 -> Solitaire.Pile.Home(c.slot - 3) to -1
            !c.top -> Solitaire.Pile.Column(c.slot) to c.index
            else -> null
        }

    fun act(c: SolCursor) {
        if (won || finishing) return
        cursor = c
        if (c.top && c.slot == 0) {
            picked = null
            game.draw()
            changed()
            return
        }
        val here = pileAt(c) ?: return
        val held = picked
        if (held == null) {
            if (game.pickUp(here.first, here.second).isNotEmpty()) picked = here
            return
        }
        // OK on the picked card again: send it home if it fits, or to the first column that takes it
        val samePile = held.first == here.first
        val done =
            if (samePile) {
                game.sendHome(held.first, held.second) ||
                    game.targets(game.pickUp(held.first, held.second)).firstOrNull { it is Solitaire.Pile.Column }?.let {
                        game.move(held.first, held.second, it)
                    } == true
            } else {
                game.move(held.first, held.second, here.first)
            }
        picked = null
        if (!done && !samePile && game.pickUp(here.first, here.second).isNotEmpty()) picked = here
        changed()
    }

    fun move(
        dx: Int,
        dy: Int,
    ) {
        val c = cursor
        cursor =
            if (c.top) {
                when {
                    dx != 0 -> {
                        var s = (c.slot + dx).coerceIn(0, 6)
                        if (s == 2) s = if (dx > 0) 3 else 1
                        c.copy(slot = s)
                    }

                    dy > 0 -> {
                        SolCursor(false, c.slot, (game.columns[c.slot].size - 1).coerceAtLeast(0))
                    }

                    else -> {
                        c
                    }
                }
            } else {
                val col = game.columns[c.slot]
                when {
                    dx != 0 -> {
                        val s = (c.slot + dx).coerceIn(0, 6)
                        SolCursor(false, s, (game.columns[s].size - 1).coerceAtLeast(0))
                    }

                    dy < 0 -> {
                        if (c.index - 1 >= game.hidden[c.slot] && c.index - 1 >= 0) {
                            c.copy(index = c.index - 1)
                        } else {
                            SolCursor(true, if (c.slot == 2) 1 else c.slot)
                        }
                    }

                    dy > 0 -> {
                        c.copy(index = (c.index + 1).coerceAtMost((col.size - 1).coerceAtLeast(0)))
                    }

                    else -> {
                        c
                    }
                }
            }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(TableColors.Felt)) {
        redrawOn(version)
        val wide = maxWidth > maxHeight
        val gap = if (wide) 12.dp else 6.dp
        val cardW = minOf((maxWidth - 32.dp - gap * 6) / 7, if (wide) (maxHeight - 60.dp) / 5.4f else 120.dp)
        val cardH = cardW * 1.4f
        val boardW = cardW * 7 + gap * 6
        val columnsH = maxHeight - cardH - 80.dp
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .tableKeys(focus, onMove = ::move, onOk = { act(cursor) }, onPlay = { act(SolCursor(true, 0)) }),
        ) {
            Row(Modifier.width(boardW), verticalAlignment = Alignment.CenterVertically) {
                TableLine("Solitaire", Color.White, bold = true)
                Spacer(Modifier.weight(1f))
                TableLine(
                    when {
                        finishing -> "Finishing by itself…"
                        picked != null -> "Now pick where it goes. OK on it again sends it up."
                        isTv -> "Play turns a card. Back opens the menu."
                        else -> "Tap a card, then where it goes."
                    },
                    BenflixColors.Soft,
                )
            }
            Spacer(Modifier.height(8.dp))
            // Top row: stock, turned cards, a gap, four home piles
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (slot in 0..6) {
                    val here = SolCursor(true, slot)
                    val isCursor = isTv && cursor.top && cursor.slot == slot && !menu
                    val tap = Modifier.onTap { act(here) }
                    when (slot) {
                        0 -> {
                            PlayingCard(
                                game.stock.lastOrNull(),
                                faceUp = false,
                                width = cardW,
                                cursor = isCursor,
                                modifier = tap,
                            )
                        }

                        1 -> {
                            PlayingCard(
                                game.waste.lastOrNull(),
                                faceUp = true,
                                width = cardW,
                                cursor = isCursor,
                                picked = picked?.first == Solitaire.Pile.Waste,
                                modifier = tap,
                            )
                        }

                        2 -> {
                            Spacer(Modifier.size(cardW, cardH))
                        }

                        else -> {
                            val home = game.homes[slot - 3]
                            PlayingCard(
                                home.lastOrNull(),
                                faceUp = true,
                                width = cardW,
                                cursor = isCursor,
                                picked = picked?.first == Solitaire.Pile.Home(slot - 3),
                                modifier = tap,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(gap + 4.dp))
            // The seven columns, squeezed together when a column gets long
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (c in 0..6) {
                    val col = game.columns[c]
                    val hidden = game.hidden[c]
                    val faceUpCount = col.size - hidden
                    val want = cardH * .11f * hidden + cardH * .26f * (faceUpCount - 1).coerceAtLeast(0)
                    val room = (columnsH - cardH).coerceAtLeast(cardH * .5f)
                    val squeeze = if (want > room && want.value > 0f) room / want else 1f
                    Box(
                        Modifier
                            .width(cardW)
                            .height(columnsH.coerceAtLeast(cardH))
                            .onTap { act(SolCursor(false, c, (col.size - 1).coerceAtLeast(0))) },
                    ) {
                        if (col.isEmpty()) {
                            PlayingCard(null, true, cardW, cursor = isTv && !cursor.top && cursor.slot == c && !menu)
                        }
                        var y = 0.dp
                        col.forEachIndexed { i, card ->
                            val up = i >= hidden
                            val held = picked?.let { (p, idx) -> p == Solitaire.Pile.Column(c) && i >= idx } == true
                            PlayingCard(
                                card,
                                faceUp = up,
                                width = cardW,
                                picked = held,
                                cursor = isTv && !cursor.top && cursor.slot == c && cursor.index == i && !menu,
                                modifier =
                                    Modifier
                                        .offset(y = y)
                                        .onTap { act(SolCursor(false, c, i)) },
                            )
                            y += (if (up) cardH * .26f else cardH * .11f) * squeeze
                        }
                    }
                }
            }
        }
        if (won) {
            TableMenu(
                "You won!",
                listOf("Every card is home in ${game.moves} moves."),
                listOf("Play again" to again, "Leave" to leave),
            )
        } else if (menu) {
            TableMenu(
                "Solitaire",
                listOf("Moves so far: ${game.moves}"),
                listOfNotNull(
                    "Keep playing" to { menu = false },
                    if (game.canUndo()) {
                        "Undo" to {
                            game.undo()
                            picked = null
                            changed()
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
    }
}

// ---------------------------------------------------------------- Five Dice

/** Pip spots on a die face, on a 3 x 3 grid */
private val PIPS =
    mapOf(
        1 to listOf(4),
        2 to listOf(0, 8),
        3 to listOf(0, 4, 8),
        4 to listOf(0, 2, 6, 8),
        5 to listOf(0, 2, 4, 6, 8),
        6 to listOf(0, 2, 3, 5, 6, 8),
    )

@Composable
private fun Die(
    value: Int,
    size: Dp,
    held: Boolean,
    cursor: Boolean,
    blank: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(size * .18f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            Modifier
                .size(size)
                .background(if (held) Color(0xFFFFF3C4) else Color.White, shape)
                .then(if (held) Modifier.border(3.dp, TableColors.Gold, shape) else Modifier)
                .then(if (cursor) Modifier.border(4.dp, TableColors.P1, shape) else Modifier)
                .alpha(if (blank) .35f else 1f)
                .padding(size * .14f),
        ) {
            if (!blank) {
                val pips = PIPS[value].orEmpty()
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    for (r in 0..2) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            for (c in 0..2) {
                                Box(
                                    Modifier
                                        .size(size * .17f)
                                        .background(if (r * 3 + c in pips) TableColors.Ink else Color.Transparent, CircleShape),
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            if (held) "Kept" else " ",
            fontSize = BenflixType.small,
            fontWeight = FontWeight.SemiBold,
            color = TableColors.Gold,
        )
    }
}

/** Cursor zones in Five Dice: the dice, the Roll button and the score sheet */
private enum class DiceZone { DICE, ROLL, SHEET }

@Composable
internal fun FiveDiceGame(
    seats: TableSeats,
    me: String,
    again: () -> Unit,
    leave: () -> Unit,
) {
    val isTv = rememberIsTv()
    val game =
        remember {
            val people = seats.people.coerceAtLeast(1)
            val players =
                List(people) { DicePlayer(if (it == 0) me else "Player ${it + 1}", false) } +
                    List(seats.glowfin) { DicePlayer(if (seats.glowfin == 1) "Glowfin" else "Glowfin ${it + 1}", true) }
            FiveDice(players)
        }
    var version by remember { mutableIntStateOf(0) }
    var zone by remember { mutableStateOf(DiceZone.ROLL) }
    var spot by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val over = remember(version) { game.over }
    val glowfinTurn = !over && game.player.glowfin
    val boxes = DiceBox.entries

    BackHandler(enabled = !menu && !over) { menu = true }
    LaunchedEffect(menu, over) { if (!menu && !over) focus.tryRequestFocus() }
    // Counts filled boxes, so Glowfin's turn starts once per turn (not on every roll it makes)
    val turnNo = remember(version) { game.players.sumOf { it.sheet.size } }
    LaunchedEffect(turnNo, menu) {
        if (glowfinTurn && !menu) {
            delay(600)
            if (game.rolls == 0) {
                game.roll()
                version++
                delay(900)
            }
            while (game.canRoll()) {
                val keep = game.glowfinHolds()
                if (keep.all { it }) break
                keep.copyInto(game.held)
                version++
                delay(700)
                game.roll()
                version++
                delay(900)
            }
            game.score(game.glowfinBox())
            version++
        }
    }

    fun act() {
        if (over || glowfinTurn) return
        when (zone) {
            DiceZone.DICE -> {
                game.toggle(spot)
            }

            DiceZone.ROLL -> {
                game.roll()
                if (!game.canRoll()) {
                    zone = DiceZone.SHEET
                    spot = boxes.indexOfFirst { game.canScore(it) }.coerceAtLeast(0)
                }
            }

            DiceZone.SHEET -> {
                if (game.score(boxes[spot])) zone = DiceZone.ROLL
            }
        }
        version++
    }

    fun move(
        dx: Int,
        dy: Int,
    ) {
        when (zone) {
            DiceZone.DICE -> {
                when {
                    dy > 0 -> {
                        zone = DiceZone.ROLL
                    }

                    dx > 0 && spot == 4 -> {
                        zone = DiceZone.SHEET
                        spot = 0
                    }

                    dx != 0 -> {
                        spot = (spot + dx).coerceIn(0, 4)
                    }
                }
            }

            DiceZone.ROLL -> {
                when {
                    dy < 0 -> {
                        zone = DiceZone.DICE
                        spot = 0
                    }

                    dx > 0 || dy > 0 -> {
                        zone = DiceZone.SHEET
                        spot = boxes.indexOfFirst { game.canScore(it) }.coerceAtLeast(0)
                    }
                }
            }

            DiceZone.SHEET -> {
                when {
                    dx < 0 -> {
                        zone = DiceZone.ROLL
                    }

                    dy != 0 -> {
                        spot = (spot + dy).coerceIn(0, boxes.size - 1)
                    }
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        redrawOn(version)
        val wide = maxWidth > maxHeight
        val rows = boxes.size + 3
        val sheetH = if (wide) maxHeight - 32.dp else maxHeight * .58f
        val rowH = (sheetH / rows).coerceAtMost(40.dp)
        val dieSize = if (wide) minOf((maxWidth * .4f) / 6, 76.dp) else minOf((maxWidth - 32.dp) / 6, 64.dp)
        val roll = @Composable {
            redrawOn(version)
            val cursorOn = isTv && zone == DiceZone.ROLL && !menu
            val can = game.canRoll() && !glowfinTurn
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .width(dieSize * 4)
                        .height(48.dp)
                        .background(if (can) Color.White else Color.White.copy(alpha = .15f), RoundedCornerShape(24.dp))
                        .then(if (cursorOn) Modifier.border(4.dp, TableColors.P1, RoundedCornerShape(24.dp)) else Modifier)
                        .onTap {
                            zone = DiceZone.ROLL
                            act()
                        },
            ) {
                Text(
                    when {
                        glowfinTurn -> "Glowfin is rolling"
                        game.rolls == 0 -> "Roll"
                        game.canRoll() -> "Roll again (${3 - game.rolls} left)"
                        else -> "Pick a box"
                    },
                    fontSize = BenflixType.body,
                    fontWeight = FontWeight.Bold,
                    color = if (can) Color.Black else Color.White,
                )
            }
        }
        val diceArea = @Composable {
            redrawOn(version)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TableTitle("Five Dice")
                TableLine(
                    if (over) {
                        "Game over"
                    } else if (glowfinTurn) {
                        "${game.player.name} is playing…"
                    } else {
                        "${game.player.name}'s turn"
                    },
                    Color.White,
                    bold = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(dieSize * .18f)) {
                    for (i in 0..4) {
                        Die(
                            game.dice[i],
                            dieSize,
                            held = game.held[i] && game.rolls > 0,
                            cursor = isTv && zone == DiceZone.DICE && spot == i && !menu,
                            blank = game.rolls == 0,
                            modifier =
                                Modifier.onTap {
                                    zone = DiceZone.DICE
                                    spot = i
                                    act()
                                },
                        )
                    }
                }
                roll()
                TableLine(
                    when {
                        glowfinTurn -> "Watch Glowfin pick its dice."
                        game.rolls == 0 -> "Roll up to 3 times. Keep the dice you like."
                        isTv -> "OK on a die keeps it. Then pick a box on the sheet."
                        else -> "Tap a die to keep it. Then tap a box on the sheet."
                    },
                    BenflixColors.Soft,
                )
            }
        }
        val sheet = @Composable {
            redrawOn(version)
            val nameW = if (wide) 128.dp else 104.dp
            val colW = if (wide) 56.dp else 44.dp
            Column(
                Modifier
                    .background(Color.White.copy(alpha = .06f), RoundedCornerShape(12.dp))
                    .padding(8.dp),
            ) {
                Row(Modifier.height(rowH), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(nameW))
                    game.players.forEachIndexed { pi, p ->
                        Text(
                            p.name,
                            fontSize = BenflixType.small,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (pi == game.current && !over) TableColors.P1 else BenflixColors.Soft,
                            modifier = Modifier.width(colW),
                        )
                    }
                }
                boxes.forEachIndexed { bi, box ->
                    val cursorOn = isTv && zone == DiceZone.SHEET && spot == bi && !menu
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .height(rowH)
                                .background(if (cursorOn) Color.White.copy(alpha = .18f) else Color.Transparent, RoundedCornerShape(6.dp))
                                .then(if (cursorOn) Modifier.border(2.dp, TableColors.P1, RoundedCornerShape(6.dp)) else Modifier)
                                .onTap {
                                    zone = DiceZone.SHEET
                                    spot = bi
                                    act()
                                },
                    ) {
                        Text(
                            box.label,
                            fontSize = BenflixType.small,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.width(nameW).padding(start = 6.dp),
                        )
                        game.players.forEachIndexed { pi, p ->
                            val filled = p.sheet[box]
                            val preview = pi == game.current && !over && !glowfinTurn && filled == null && game.rolls > 0
                            Text(
                                filled?.toString() ?: if (preview) box.score(game.dice).toString() else "",
                                fontSize = BenflixType.small,
                                fontWeight = if (filled != null) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (filled != null) Color.White else TableColors.Gold.copy(alpha = .8f),
                                modifier = Modifier.width(colW),
                            )
                        }
                    }
                    if (box == DiceBox.SIXES) {
                        Row(Modifier.height(rowH), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Bonus (63+)",
                                fontSize = BenflixType.small,
                                color = BenflixColors.Muted,
                                modifier = Modifier.width(nameW).padding(start = 6.dp),
                            )
                            game.players.forEach { p ->
                                Text(
                                    if (p.bonus > 0) "35" else "${p.upper}/63",
                                    fontSize = BenflixType.small,
                                    color = BenflixColors.Muted,
                                    modifier = Modifier.width(colW),
                                )
                            }
                        }
                    }
                }
                Row(Modifier.height(rowH), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Total",
                        fontSize = BenflixType.small,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.width(nameW).padding(start = 6.dp),
                    )
                    game.players.forEach { p ->
                        Text(
                            p.total.toString(),
                            fontSize = BenflixType.small,
                            fontWeight = FontWeight.Bold,
                            color = TableColors.Gold,
                            modifier = Modifier.width(colW),
                        )
                    }
                }
            }
        }
        val keys =
            Modifier.tableKeys(focus, onMove = ::move, onOk = ::act, onPlay = {
                zone = DiceZone.ROLL
                act()
            })
        if (wide) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp).then(keys),
            ) {
                diceArea()
                sheet()
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize().padding(12.dp).then(keys),
            ) {
                diceArea()
                sheet()
            }
        }
        if (over) {
            val win = game.winners()
            TableMenu(
                if (win.size > 1) "It's a tie!" else "${win.first().name} wins!",
                game.players.sortedByDescending { it.total }.map { "${it.name}: ${it.total}" },
                listOf("Play again" to again, "Leave" to leave),
            )
        } else if (menu) {
            TableMenu(
                "Five Dice",
                emptyList(),
                listOf("Keep playing" to { menu = false }, "New game" to again, "Leave" to leave),
            )
        }
    }
}
