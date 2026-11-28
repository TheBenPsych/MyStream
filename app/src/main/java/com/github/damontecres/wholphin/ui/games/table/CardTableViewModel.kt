package com.github.damontecres.wholphin.ui.games.table

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.ui.games.ember.EmberLinkHost
import com.github.damontecres.wholphin.ui.games.ember.Joined
import com.github.damontecres.wholphin.ui.games.ember.PhoneAction
import com.github.damontecres.wholphin.ui.games.ember.PhoneView
import com.github.damontecres.wholphin.ui.games.party.PartyViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.random.Random

/** Who sits in a seat at the card table */
enum class SeatKind { TV, PHONE, GLOWFIN }

data class TableSeat(
    val kind: SeatKind,
    val name: String,
    val phoneId: String? = null,
)

enum class TablePhase { LOBBY, PLAYING, OVER }

data class CardTableUi(
    val game: TableGame = TableGame.HEARTS,
    val phase: TablePhase = TablePhase.LOBBY,
    val seatCount: Int = 4,
    /** Someone plays from the TV with the remote (their cards show on the TV) */
    val tvPlays: Boolean = true,
    /** Phones that joined, in the order they came */
    val phones: List<Joined> = emptyList(),
    /** Seats once the game starts */
    val seats: List<TableSeat> = emptyList(),
    /** Goes up whenever the cards change, so the table redraws */
    val version: Int = 0,
)

/**
 * The card table: the TV is the table and each phone holds one player's hand, using the same phone link as the
 * party games (phones tap Join a party). Any seat nobody takes is played by Glowfin.
 */
@HiltViewModel
class CardTableViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _ui = MutableStateFlow(CardTableUi())
        val ui: StateFlow<CardTableUi> = _ui
        private var host: EmberLinkHost? = null
        private var loop: Job? = null
        private var me = "Player 1"

        /** The game being played (plain object; [CardTableUi.version] says when it changed) */
        var game: CardGame? = null
            private set

        /** Cards picked so far, per seat */
        private val picked = mutableMapOf<Int, List<Int>>()

        /** Phones read the game from another thread, so changes and reads take turns */
        private val lock = Any()

        fun setup(
            kind: TableGame,
            myName: String,
        ) {
            if (host != null) return
            me = myName
            _ui.value = CardTableUi(game = kind, seatCount = kind.seatsStart)
            host =
                EmberLinkHost(
                    context = context.applicationContext,
                    onJoin = { j ->
                        _ui.update { u -> if (u.phones.any { it.id == j.id }) u else u.copy(phones = u.phones + j) }
                        push()
                    },
                    onLeave = { j ->
                        _ui.update { u ->
                            if (u.phase == TablePhase.LOBBY) u.copy(phones = u.phones.filter { it.id != j.id }) else u
                        }
                        push()
                    },
                    onDo = { j, a -> viewModelScope.launch { onPhone(j, a) } },
                    onType = { _, _ -> },
                    serviceType = PartyViewModel.SERVICE_TYPE,
                    label = PartyViewModel.LABEL,
                ).also {
                    it.viewFor = ::viewFor
                    it.start()
                }
        }

        fun setSeatCount(n: Int) {
            val g = _ui.value.game
            _ui.update { it.copy(seatCount = n.coerceIn(g.seatsMin, g.seatsMax)) }
            push()
        }

        fun setTvPlays(on: Boolean) {
            _ui.update { it.copy(tvPlays = on) }
            push()
        }

        /** The seats as they'd be if the game started now */
        fun lobbySeats(u: CardTableUi = _ui.value): List<TableSeat> {
            val out = mutableListOf<TableSeat>()
            if (u.tvPlays) out.add(TableSeat(SeatKind.TV, me))
            u.phones.forEach { if (out.size < u.seatCount) out.add(TableSeat(SeatKind.PHONE, it.name, it.id)) }
            var g = 1
            while (out.size < u.seatCount) {
                out.add(TableSeat(SeatKind.GLOWFIN, if (u.seatCount - out.size == 1 && g == 1) "Glowfin" else "Glowfin $g"))
                g++
            }
            // Names must be different so the table can tell them apart
            val seen = mutableMapOf<String, Int>()
            return out.map { s ->
                val n = (seen[s.name] ?: 0) + 1
                seen[s.name] = n
                if (n > 1) s.copy(name = "${s.name} $n") else s
            }
        }

        fun start() {
            val u = _ui.value
            val seats = lobbySeats(u)
            val names = seats.map { it.name }
            val r = Random(System.nanoTime())
            game =
                when (u.game) {
                    TableGame.HEARTS -> Hearts(names, r)
                    TableGame.SPADES -> Spades(names, r)
                    TableGame.CRAZY_EIGHTS -> CrazyEights(names, r)
                    TableGame.RUMMY -> Rummy(names, r)
                    TableGame.GO_FISH -> GoFish(names, r)
                    else -> Holdem(names, r)
                }
            picked.clear()
            _ui.update { it.copy(phase = TablePhase.PLAYING, seats = seats, version = it.version + 1) }
            push()
            loop?.cancel()
            loop = viewModelScope.launch { runTable() }
        }

        fun backToLobby() {
            loop?.cancel()
            game = null
            _ui.update { it.copy(phase = TablePhase.LOBBY, version = it.version + 1) }
            push()
        }

        private fun changed() {
            _ui.update { it.copy(version = it.version + 1) }
            push()
        }

        private fun push() = host?.push()

        /** Glowfin's seats, pauses after tricks, and the end of the game */
        private suspend fun runTable() {
            while (viewModelScope.isActive) {
                val g = game ?: return
                if (g.over) {
                    _ui.update { it.copy(phase = TablePhase.OVER, version = it.version + 1) }
                    push()
                    return
                }
                if (g.waiting) {
                    delay(1600)
                    synchronized(lock) {
                        g.advance()
                        picked.clear()
                    }
                    changed()
                    continue
                }
                val seats = _ui.value.seats
                val bot = seats.indices.firstOrNull { seats[it].kind == SeatKind.GLOWFIN && g.turn(it) != null }
                if (bot != null) {
                    delay(if (g is Holdem) 900 else 750)
                    val choice = withContext(Dispatchers.Default) { g.glowfin(bot) }
                    if (choice != null && game === g && synchronized(lock) { g.press(bot, choice.first, choice.second) }) {
                        picked.remove(bot)
                        changed()
                    }
                    continue
                }
                delay(200)
            }
        }

        // ---------- a seat acting (phone, or the TV's remote) ----------

        fun pickedFor(seat: Int): List<Int> = picked[seat].orEmpty()

        /** Taps a card in [seat]'s hand: picks it (or adds it to a group), or un-picks it */
        fun toggle(
            seat: Int,
            index: Int,
        ) {
            val g = game ?: return
            val t = g.turn(seat) ?: return
            if (index !in t.playable) return
            synchronized(lock) {
                val now = picked[seat].orEmpty()
                picked[seat] =
                    when {
                        index in now -> now - index
                        t.pickMany -> now + index
                        else -> listOf(index)
                    }
            }
            changed()
        }

        fun press(
            seat: Int,
            button: String,
        ): Boolean {
            val g = game ?: return false
            val ok =
                synchronized(lock) {
                    g.press(seat, button, picked[seat].orEmpty().sorted()).also { if (it) picked.remove(seat) }
                }
            if (ok) changed()
            return ok
        }

        private fun seatOf(j: Joined): Int = _ui.value.seats.indexOfFirst { it.phoneId == j.id }

        private fun onPhone(
            j: Joined,
            action: String,
        ) {
            if (_ui.value.phase != TablePhase.PLAYING) return
            val seat = seatOf(j).takeIf { it >= 0 } ?: return
            if (action.startsWith("card:")) {
                action.removePrefix("card:").toIntOrNull()?.let { toggle(seat, it) }
            } else {
                press(seat, action)
            }
        }

        /** What a phone shows: its hand, what's on the table and its buttons */
        private fun viewFor(j: Joined): PhoneView = synchronized(lock) { buildView(j) }

        private fun buildView(j: Joined): PhoneView {
            val u = _ui.value
            val title = u.game.title
            val g = game
            if (u.phase == TablePhase.LOBBY || g == null) {
                val seats = lobbySeats(u)
                val mine = seats.any { it.phoneId == j.id }
                return PhoneView(
                    title = "You're at the table!",
                    text =
                        if (mine) {
                            "Waiting for the TV to start $title. Seats: " + seats.joinToString(", ") { it.name }
                        } else {
                            "The table is full, so you'll watch this game from the TV."
                        },
                    status = title,
                )
            }
            val seat = seatOf(j)
            if (seat < 0) return PhoneView(title = title, text = "Watching this game on the TV. " + g.message, status = title)
            val t = g.turn(seat)
            val center = g.center()
            val table =
                buildList {
                    if (center.played.isNotEmpty()) {
                        add("On the table: " + center.played.entries.joinToString(", ") { (s, c) -> "${u.seats[s].name} ${c.label}" })
                    }
                    if (center.shared.isNotEmpty()) {
                        add((if (g is Holdem) "Shared cards: " else "Top card: ") + center.shared.joinToString(" ") { it.label })
                    }
                    if (center.label.isNotBlank()) add(center.label)
                }
            val waitingFor = u.seats.indices.firstOrNull { g.turn(it) != null }
            val pickedNow = picked[seat].orEmpty()
            return PhoneView(
                title =
                    if (u.phase == TablePhase.OVER) {
                        "Game over"
                    } else if (t != null) {
                        "Your turn"
                    } else {
                        title
                    },
                text =
                    listOfNotNull(
                        g.message.takeIf { it.isNotBlank() },
                        table.joinToString("\n").takeIf { it.isNotBlank() },
                        when {
                            u.phase == TablePhase.OVER -> g.results().joinToString("\n")
                            t != null -> t.prompt
                            waitingFor != null -> "Waiting for ${u.seats[waitingFor].name}…"
                            else -> null
                        },
                    ).joinToString("\n\n"),
                status = "${u.seats[seat].name} · ${g.seatLine(seat)}",
                hand = g.hand(seat).map { it.code },
                playable = t?.playable?.toList().orEmpty(),
                picked = pickedNow,
                actions =
                    t?.buttons.orEmpty().map { b ->
                        PhoneAction(
                            b.id,
                            b.label,
                            when (b.cards) {
                                0 -> ""
                                -1 -> "Pick the cards first"
                                1 -> "Pick a card first"
                                else -> "Pick ${b.cards} cards first"
                            },
                        )
                    },
                round = u.version,
            )
        }

        override fun onCleared() {
            loop?.cancel()
            host?.stop()
            host = null
            super.onCleared()
        }
    }
