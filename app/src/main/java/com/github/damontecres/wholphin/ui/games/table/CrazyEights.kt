package com.github.damontecres.wholphin.ui.games.table

import kotlin.random.Random

/**
 * Crazy Eights for 2 to 6. Play a card that matches the top card's suit or number. An 8 goes on anything and you
 * pick the new suit. Can't (or won't) play? Draw one; if it fits you may play it, or pass. First with no cards wins.
 */
class CrazyEights(
    override val names: List<String>,
    private val random: Random = Random.Default,
) : CardGame {
    override val seats = names.size
    private val hands = List(seats) { mutableListOf<Card>() }
    private val deck = shuffledDeck(random)
    private val pile = mutableListOf<Card>()
    private var suit = 0
    private var turnSeat = 0
    private var drew = false
    private var choosing = false
    private var winner = -1
    override var message = ""
        private set

    init {
        val each = if (seats == 2) 7 else 5
        repeat(each) { for (h in hands) h.add(deck.removeAt(deck.size - 1)) }
        hands.forEach { it.sortHand() }
        // The first card can't be an 8
        var first = deck.indexOfLast { it.rank != 8 }
        pile.add(deck.removeAt(first))
        suit = pile.last().suit
        turnSeat = random.nextInt(seats)
        message = "${names[turnSeat]} goes first"
    }

    private val top: Card get() = pile.last()

    override fun hand(seat: Int): List<Card> = hands[seat]

    private fun fits(c: Card) = c.rank == 8 || c.suit == suit || c.rank == top.rank

    private fun refill(): Boolean {
        if (deck.isNotEmpty()) return true
        if (pile.size <= 1) return false
        val keep = pile.removeAt(pile.size - 1)
        deck.addAll(pile.shuffled(random))
        pile.clear()
        pile.add(keep)
        return true
    }

    override fun turn(seat: Int): SeatTurn? {
        if (winner >= 0 || seat != turnSeat) return null
        if (choosing) {
            return SeatTurn(
                "Pick the new suit",
                buttons =
                    (0..3).map {
                        SeatButton(
                            "suit$it",
                            Card.SUITS[it] + " " +
                                SUIT_NAMES[it].replaceFirstChar { c ->
                                    c.uppercase()
                                },
                        )
                    },
            )
        }
        val h = hands[seat]
        val ok = h.indices.filter { fits(h[it]) }.toSet()
        val canDraw = !drew && (deck.isNotEmpty() || pile.size > 1)
        return SeatTurn(
            if (ok.isEmpty()) {
                if (canDraw) "Nothing fits. Draw a card." else "Nothing fits. Pass."
            } else {
                "Match ${Card.SUITS[suit]} or ${top.rankName}, or play an 8"
            },
            ok,
            listOfNotNull(
                SeatButton("play", "Play", 1),
                if (canDraw) SeatButton("draw", "Draw") else null,
                if (!canDraw) SeatButton("pass", "Pass") else null,
            ),
        )
    }

    override fun press(
        seat: Int,
        button: String,
        picked: List<Int>,
    ): Boolean {
        val t = turn(seat) ?: return false
        if (t.buttons.none { it.id == button }) return false
        when (button) {
            "play" -> {
                val i = picked.singleOrNull()?.takeIf { it in t.playable } ?: return false
                val c = hands[seat].removeAt(i)
                pile.add(c)
                suit = c.suit
                message = "${names[seat]} played ${c.label}"
                if (hands[seat].isEmpty()) {
                    winner = seat
                    message = "${names[seat]} is out of cards!"
                    return true
                }
                if (c.rank == 8) {
                    choosing = true
                    return true
                }
                next()
            }

            "draw" -> {
                if (!refill()) return false
                hands[seat].add(deck.removeAt(deck.size - 1))
                hands[seat].sortHand()
                drew = true
                message = "${names[seat]} drew a card"
            }

            "pass" -> {
                message = "${names[seat]} passed"
                next()
            }

            else -> {
                if (!button.startsWith("suit")) return false
                suit = button.removePrefix("suit").toIntOrNull()?.takeIf { it in 0..3 } ?: return false
                choosing = false
                message = "${names[seat]} changed the suit to ${Card.SUITS[suit]}"
                next()
            }
        }
        return true
    }

    private fun next() {
        drew = false
        turnSeat = (turnSeat + 1) % seats
    }

    override val waiting = false

    override fun advance() {}

    override val over: Boolean get() = winner >= 0

    override fun seatLine(seat: Int): String = "${hands[seat].size} cards"

    override fun center(): TableCenter =
        TableCenter(
            shared = listOf(top),
            deck = deck.size,
            label = if (suit != top.suit) "The suit is now ${Card.SUITS[suit]}" else "",
        )

    override fun results(): List<String> = (0 until seats).sortedBy { hands[it].size }.map { "${names[it]}: ${hands[it].size} cards left" }

    override fun winners(): Set<Int> = setOf(winner)

    override fun glowfin(seat: Int): Pair<String, List<Int>>? {
        val t = turn(seat) ?: return null
        val h = hands[seat]
        if (choosing) {
            val best = (0..3).maxBy { s -> h.count { it.suit == s } }
            return "suit$best" to emptyList()
        }
        val ok = t.playable.toList()
        if (ok.isNotEmpty()) {
            // Keep 8s for when nothing else fits; play from the suit we hold most of
            val plain = ok.filter { h[it].rank != 8 }
            val pick = plain.maxByOrNull { i -> h.count { it.suit == h[i].suit } * 20 + h[i].rank } ?: ok.first()
            return "play" to listOf(pick)
        }
        return if (t.buttons.any { it.id == "draw" }) "draw" to emptyList() else "pass" to emptyList()
    }
}
