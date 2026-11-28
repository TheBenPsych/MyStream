package com.github.damontecres.wholphin.ui.games.table

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlin.math.cos
import kotlin.math.sin

/** A card table game (Hearts, Spades, Crazy Eights, Rummy, Go Fish, Hold'em): the seats, then the table */
@Composable
fun CardTablePage(
    game: TableGame,
    me: String,
    leave: () -> Unit,
    viewModel: CardTableViewModel = hiltViewModel(),
) {
    if (!game.kids && KidsMode.isActive) {
        TableMenu(game.title, listOf("This one isn't on Kids profiles."), listOf("Back" to leave))
        return
    }
    LaunchedEffect(game) { viewModel.setup(game, me) }
    val ui by viewModel.ui.collectAsState()
    when (ui.phase) {
        TablePhase.LOBBY -> CardLobby(ui, viewModel, leave)
        else -> CardTable(ui, viewModel, leave)
    }
}

@Composable
private fun CardLobby(
    ui: CardTableUi,
    vm: CardTableViewModel,
    leave: () -> Unit,
) {
    BackHandler { leave() }
    val seats = vm.lobbySeats(ui)
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.tryRequestFocus() }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 20.dp),
    ) {
        TableTitle(ui.game.title)
        TableLine("Who's at the table?", Color.White, bold = true)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            seats.forEach { s ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier
                            .widthIn(min = 90.dp, max = 140.dp)
                            .background(
                                if (s.kind == SeatKind.GLOWFIN) Color.White.copy(alpha = .06f) else Color.White.copy(alpha = .14f),
                                BenflixShapes.Card,
                            ).border(2.dp, if (s.kind == SeatKind.GLOWFIN) Color.Transparent else TableColors.P1, BenflixShapes.Card)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        s.name,
                        color = Color.White,
                        fontSize = BenflixType.body,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when (s.kind) {
                            SeatKind.TV -> "This TV"
                            SeatKind.PHONE -> "Phone"
                            SeatKind.GLOWFIN -> "Played by Glowfin"
                        },
                        color = BenflixColors.Muted,
                        fontSize = BenflixType.small,
                    )
                }
            }
        }
        TableLine(
            "Others join on their Android phone: open Glowfin, go to Games and tap Join a party (same Wi-Fi). " +
                "Each phone holds that person's cards. Empty seats are played by Glowfin.",
            BenflixColors.Soft,
        )
        if (ui.tvPlays &&
            ui.phones.isNotEmpty()
        ) {
            TableLine("The TV player's cards show on the TV, so everyone can see them.", BenflixColors.Muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TableButton("Start", vm::start, primary = true, modifier = Modifier.focusRequester(first))
            if (ui.game.seatsMin < ui.game.seatsMax) {
                TableButton("Fewer seats", { vm.setSeatCount(ui.seatCount - 1) })
                TableButton("More seats", { vm.setSeatCount(ui.seatCount + 1) })
            }
            TableButton(if (ui.tvPlays) "TV plays: On" else "TV plays: Off", { vm.setTvPlays(!ui.tvPlays) })
            TableButton("Back", leave)
        }
    }
}

/** The table: seats around a felt, the cards in the middle, and the TV player's hand at the bottom */
@Composable
private fun CardTable(
    ui: CardTableUi,
    vm: CardTableViewModel,
    leave: () -> Unit,
) {
    val g = vm.game ?: return
    val isTv = rememberIsTv()
    var menu by remember { mutableStateOf(false) }
    val over = ui.phase == TablePhase.OVER
    BackHandler(enabled = !menu && !over) { menu = true }
    redrawOn(ui.version)
    val tvSeat = ui.seats.indexOfFirst { it.kind == SeatKind.TV }
    val turnSeat = ui.seats.indices.firstOrNull { g.turn(it) != null }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0B0D1A))) {
        val handH = if (tvSeat >= 0) minOf(maxHeight * .36f, 200.dp) else 0.dp
        Column(Modifier.fillMaxSize()) {
            Felt(ui, g, turnSeat, Modifier.fillMaxWidth().weight(1f).padding(10.dp))
            if (tvSeat >= 0) {
                HandPanel(ui, vm, g, tvSeat, isTv, !menu && !over, Modifier.fillMaxWidth().height(handH))
            }
        }
        if (over) {
            val win = g.winners().map { ui.seats[it].name }
            TableMenu(
                if (win.size == 1) "${win.first()} wins!" else win.joinToString(" and ") + " win!",
                g.results(),
                listOf("Play again" to vm::start, "Change seats" to vm::backToLobby, "Leave" to leave),
            )
        } else if (menu) {
            TableMenu(
                ui.game.title,
                emptyList(),
                listOf(
                    "Keep playing" to { menu = false },
                    "Change seats" to {
                        menu = false
                        vm.backToLobby()
                    },
                    "Leave" to leave,
                ),
            )
        }
    }
}

@Composable
private fun Felt(
    ui: CardTableUi,
    g: CardGame,
    turnSeat: Int?,
    modifier: Modifier,
) {
    redrawOn(ui.version)
    val center = g.center()
    BoxWithConstraints(
        modifier
            .background(TableColors.Felt, RoundedCornerShape(120.dp))
            .border(6.dp, TableColors.FeltEdge, RoundedCornerShape(120.dp)),
    ) {
        val w = maxWidth
        val h = maxHeight
        val n = ui.seats.size
        val tagW = minOf(170.dp, w / 4.5f)
        val tagH = 50.dp
        val cardW = minOf(56.dp, h / 6f)

        // Seat 0 sits at the bottom, the rest go round clockwise
        fun spot(
            i: Int,
            k: Float,
        ): Pair<Dp, Dp> {
            val a = Math.PI / 2 + i * 2 * Math.PI / n
            val x = w / 2 + (w / 2 - tagW / 2 - 8.dp) * k * cos(a).toFloat()
            val y = h / 2 + (h / 2 - tagH / 2 - 6.dp) * k * sin(a).toFloat()
            return x to y
        }
        // What's in the middle: shared cards, the deck, melds, a label
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.align(Alignment.Center).widthIn(max = w * .56f),
        ) {
            if (g.message.isNotBlank()) {
                Text(
                    g.message,
                    color = Color.White,
                    fontSize = BenflixType.small,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
            if (center.shared.isNotEmpty() || center.deck >= 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (center.deck >= 0) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PlayingCard(if (center.deck > 0) Card(1, 0) else null, faceUp = false, width = cardW)
                            Text("${center.deck} left", color = BenflixColors.Soft, fontSize = BenflixType.small)
                        }
                    }
                    center.shared.forEach { PlayingCard(it, faceUp = true, width = cardW) }
                }
            }
            if (center.melds.isNotEmpty()) {
                val per = maxOf(1, ((w * .56f) / (cardW * 1.6f)).toInt())
                center.melds.chunked(per).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { m ->
                            Box(Modifier.width(cardW * .5f + cardW * .2f * (m.size - 1))) {
                                m.forEachIndexed { k, c ->
                                    PlayingCard(c, faceUp = true, width = cardW * .5f, modifier = Modifier.offset(x = cardW * .2f * k))
                                }
                            }
                        }
                    }
                }
            }
            if (center.label.isNotBlank()) {
                Text(
                    center.label,
                    color = TableColors.Gold,
                    fontSize = BenflixType.small,
                    textAlign = TextAlign.Center,
                )
            }
        }
        // A trick: each card in front of the seat that played it
        center.played.forEach { (seat, card) ->
            val (x, y) = spot(seat, .5f)
            PlayingCard(card, faceUp = true, width = cardW, modifier = Modifier.offset(x - cardW / 2, y - cardW * .7f))
        }
        ui.seats.forEachIndexed { i, s ->
            val (x, y) = spot(i, 1f)
            val turn = i == turnSeat
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.offset(x - tagW / 2, y - tagH / 2).width(tagW),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(if (turn) Color.White.copy(alpha = .2f) else Color.Black.copy(alpha = .35f), BenflixShapes.Card)
                        .border(2.dp, if (turn) TableColors.P1 else Color.Transparent, BenflixShapes.Card)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        s.name + if (s.kind == SeatKind.GLOWFIN) " ✦" else "",
                        color = Color.White,
                        fontSize = BenflixType.small,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        g.seatLine(i),
                        color = BenflixColors.Soft,
                        fontSize = BenflixType.small,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                center.reveal[i]?.let { cards ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { cards.forEach { PlayingCard(it, true, cardW * .6f) } }
                }
            }
        }
    }
}

/** The TV player's hand and buttons, for the remote or a finger */
@Composable
private fun HandPanel(
    ui: CardTableUi,
    vm: CardTableViewModel,
    g: CardGame,
    seat: Int,
    isTv: Boolean,
    active: Boolean,
    modifier: Modifier,
) {
    redrawOn(ui.version)
    val t = g.turn(seat)
    val hand = g.hand(seat)
    val picked = vm.pickedFor(seat)
    var row by remember { mutableIntStateOf(0) }
    var spot by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(active) { if (active) focus.tryRequestFocus() }
    val buttons = t?.buttons.orEmpty()
    val r =
        if (buttons.isEmpty()) {
            0
        } else if (t != null && t.playable.isEmpty()) {
            1
        } else {
            row
        }
    val s = if (r == 0) spot.coerceIn(0, (hand.size - 1).coerceAtLeast(0)) else spot.coerceIn(0, (buttons.size - 1).coerceAtLeast(0))

    fun ok() {
        if (r == 0) {
            if (s in hand.indices) vm.toggle(seat, s)
            // One card to play: picking it jumps to the buttons
            if (t != null && !t.pickMany && buttons.isNotEmpty()) {
                row = 1
                spot = 0
            }
        } else {
            buttons.getOrNull(s)?.let {
                if (vm.press(seat, it.id)) {
                    row = 0
                }
            }
        }
    }
    BoxWithConstraints(
        modifier
            .background(Color.Black.copy(alpha = .4f))
            .tableKeys(
                focus,
                onMove = { dx, dy ->
                    if (dy != 0) {
                        row = (r + dy).coerceIn(0, if (buttons.isEmpty()) 0 else 1)
                        spot = 0
                    } else {
                        spot = (s + dx).coerceIn(0, maxOf(0, (if (r == 0) hand.size else buttons.size) - 1))
                    }
                },
                onOk = ::ok,
            ).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        val cardW = minOf(74.dp, (maxHeight - 64.dp) / 1.55f)
        val step = if (hand.isEmpty()) cardW else minOf(cardW + 6.dp, (maxWidth - cardW) / maxOf(1, hand.size - 1))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TableLine(
                t?.prompt ?: ui.seats.indices
                    .firstOrNull { g.turn(it) != null }
                    ?.let { "Waiting for ${ui.seats[it].name}…" } ?: "",
                if (t != null) Color.White else BenflixColors.Soft,
                bold = t != null,
            )
            Box(Modifier.fillMaxWidth().height(cardW * 1.4f + 10.dp)) {
                hand.forEachIndexed { i, c ->
                    val can = t != null && i in t.playable
                    val up = i in picked
                    PlayingCard(
                        c,
                        faceUp = true,
                        width = cardW,
                        picked = up,
                        cursor = isTv && active && r == 0 && s == i,
                        modifier =
                            Modifier
                                .offset(x = step * i, y = if (up) 0.dp else 10.dp)
                                .alpha(if (t == null || can) 1f else .45f)
                                .onTap {
                                    row = 0
                                    spot = i
                                    vm.toggle(seat, i)
                                },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                buttons.forEachIndexed { i, b ->
                    val cursorOn = isTv && active && r == 1 && s == i
                    val ready = b.cards == 0 || (b.cards == -1 && picked.isNotEmpty()) || b.cards == picked.size
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .height(36.dp)
                                .background(if (ready) Color.White else Color.White.copy(alpha = .14f), RoundedCornerShape(18.dp))
                                .then(if (cursorOn) Modifier.border(3.dp, TableColors.P1, RoundedCornerShape(18.dp)) else Modifier)
                                .onTap { vm.press(seat, b.id) }
                                .padding(horizontal = 14.dp),
                    ) {
                        Text(
                            b.label,
                            color = if (ready) Color.Black else Color.White,
                            fontSize = BenflixType.small,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
