package com.github.damontecres.wholphin.ui.games.table

import kotlin.random.Random

/**
 * Rummy for 2 to 4. On your turn take the top card of the deck or the discard pile, lay down any sets (3 or 4 of
 * a number) or runs (3 or more in a row in one suit, aces low), add cards to what's already down, then discard one.
 * First to get rid of every card wins, and scores what's left in everyone else's hands (faces 10, aces 1).
 */
class Rummy(
    override val names: List<String>,
    private val random: Random = Random.Default,
) : CardGame {
    override val seats = names.size
    private val hands = List(seats) { mutableListOf<Card>() }
    private val deck = shuffledDeck(random)
    private val pile = mutableListOf<Card>()
    val melds = mutableListOf<MutableList<Card>>()
    private val laid = IntArray(seats)
    private var turnSeat = 0
    private var drawn = false

    /** The card taken from the discard pile this turn (it can't go straight back) */
    private var tookUp: Card? = null
    private var winner = -1
    private var reshuffles = 0
    override var message = ""
        private set

    init {
        val each = if (seats == 2) 10 else 7
        repeat(each) { for (h in hands) h.add(deck.removeAt(deck.size - 1)) }
        hands.forEach { it.sortRun() }
        pile.add(deck.removeAt(deck.size - 1))
        turnSeat = random.nextInt(seats)
        message = "${names[turnSeat]} goes first"
    }

    private fun MutableList<Card>.sortRun() {
        val order = intArrayOf(0, 1, 3, 2)
        sortWith(compareBy({ order[it.suit] }, { it.rank }))
    }

    override fun hand(seat: Int): List<Card> = hands[seat]

    override fun turn(seat: Int): SeatTurn? {
        if (winner >= 0 || seat != turnSeat) return null
        val h = hands[seat]
        if (!drawn) {
            return SeatTurn(
                "Take a card from the deck or the discard pile",
                buttons =
                    listOfNotNull(
                        SeatButton("deck", "Take from the deck"),
                        pile.lastOrNull()?.let { SeatButton("pile", "Take the ${it.label}") },
                    ),
            )
        }
        return SeatTurn(
            "Lay down sets or runs, add to the table, then discard one",
            h.indices.toSet(),
            listOf(
                SeatButton("lay", "Lay down", -1),
                SeatButton("add", "Add to the table", 1),
                SeatButton("discard", "Discard", 1),
            ),
            pickMany = true,
        )
    }

    override fun press(
        seat: Int,
        button: String,
        picked: List<Int>,
    ): Boolean {
        turn(seat) ?: return false
        val h = hands[seat]
        when (button) {
            "deck" -> {
                if (drawn) return false
                if (deck.isEmpty()) {
                    // After the deck has run out three times, the fewest points left wins
                    if (pile.size <= 1 || ++reshuffles > 3) {
                        winner = (0 until seats).minBy { s -> hands[s].sumOf { value(it) } }
                        message = "The deck ran out. ${names[winner]} has the fewest points left and wins!"
                        return true
                    }
                    val keep = pile.removeAt(pile.size - 1)
                    deck.addAll(pile.reversed())
                    pile.clear()
                    pile.add(keep)
                }
                h.add(deck.removeAt(deck.size - 1))
                h.sortRun()
                drawn = true
                tookUp = null
                message = "${names[seat]} took from the deck"
            }

            "pile" -> {
                if (drawn || pile.isEmpty()) return false
                val c = pile.removeAt(pile.size - 1)
                h.add(c)
                h.sortRun()
                drawn = true
                tookUp = c
                message = "${names[seat]} took the ${c.label}"
            }

            "lay" -> {
                if (!drawn) return false
                val cards = picked.distinct().mapNotNull { h.getOrNull(it) }
                if (cards.size < 3 || !isMeld(cards)) return false
                // Keep at least one card to discard, unless this empties the hand
                h.removeAll(cards)
                melds.add(cards.sortedBy { it.rank }.toMutableList())
                laid[seat] += cards.size
                message = "${names[seat]} laid down ${cards.sortedBy { it.rank }.joinToString(" ") { it.label }}"
                checkOut(seat)
            }

            "add" -> {
                if (!drawn) return false
                val c = picked.singleOrNull()?.let { h.getOrNull(it) } ?: return false
                val m = melds.firstOrNull { isMeld(it + c) } ?: return false
                h.remove(c)
                m.add(c)
                m.sortBy { it.rank }
                laid[seat]++
                message = "${names[seat]} added the ${c.label}"
                checkOut(seat)
            }

            "discard" -> {
                if (!drawn) return false
                val c = picked.singleOrNull()?.let { h.getOrNull(it) } ?: return false
                if (c == tookUp && h.size > 1) return false
                h.remove(c)
                pile.add(c)
                message = "${names[seat]} discarded the ${c.label}"
                if (!checkOut(seat)) {
                    drawn = false
                    tookUp = null
                    turnSeat = (turnSeat + 1) % seats
                }
            }

            else -> {
                return false
            }
        }
        return true
    }

    private fun checkOut(seat: Int): Boolean {
        if (hands[seat].isNotEmpty()) return false
        winner = seat
        message = "${names[seat]} is out and scores ${points(seat)}!"
        return true
    }

    /** What the winner scores: everyone else's cards */
    fun points(seat: Int): Int = (0 until seats).filter { it != seat }.sumOf { s -> hands[s].sumOf { value(it) } }

    private fun value(c: Card) = if (c.rank >= 10) 10 else c.rank

    override val waiting = false

    override fun advance() {}

    override val over: Boolean get() = winner >= 0

    override fun seatLine(seat: Int): String = "${hands[seat].size} cards" + if (laid[seat] > 0) " · ${laid[seat]} laid down" else ""

    override fun center(): TableCenter =
        TableCenter(
            shared = listOfNotNull(pile.lastOrNull()),
            deck = deck.size,
            melds = melds.map { it.toList() },
        )

    override fun results(): List<String> =
        listOf("${names[winner]} wins ${points(winner)} points") +
            (0 until seats).filter { it != winner }.map { s ->
                "${names[s]}: ${hands[s].size} cards left (${hands[s].sumOf { value(it) }})"
            }

    override fun winners(): Set<Int> = setOf(winner)

    // ---------- Glowfin ----------

    override fun glowfin(seat: Int): Pair<String, List<Int>>? {
        turn(seat) ?: return null
        val h = hands[seat]
        if (!drawn) {
            val top = pile.lastOrNull()
            val useful = top != null && (findMeld(h + top)?.contains(top) == true || melds.any { isMeld(it + top) })
            return (if (useful) "pile" else "deck") to emptyList()
        }
        findMeld(h)?.let { m -> return "lay" to m.map { c -> h.indexOf(c) } }
        if (h.size > 1) {
            for (i in h.indices) if (melds.any { isMeld(it + h[i]) }) return "add" to listOf(i)
        }

        // Discard the highest card that isn't close to a set or a run
        fun close(c: Card) = h.any { it != c && (it.rank == c.rank || (it.suit == c.suit && kotlin.math.abs(it.rank - c.rank) <= 2)) }
        val choices = h.indices.filter { h[it] != tookUp || h.size == 1 }
        val pick = choices.maxBy { i -> value(h[i]) + (if (close(h[i])) -20 else 0) + random.nextDouble() }
        return "discard" to listOf(pick)
    }

    companion object {
        fun isMeld(cards: List<Card>): Boolean {
            if (cards.size < 3 || cards.distinct().size != cards.size) return false
            if (cards.all { it.rank == cards[0].rank }) return cards.size <= 4
            if (cards.any { it.suit != cards[0].suit }) return false
            val r = cards.map { it.rank }.sorted()
            return r.zipWithNext().all { (a, b) -> b == a + 1 }
        }

        /** A meld somewhere in these cards: the longest run first, then a set */
        fun findMeld(cards: List<Card>): List<Card>? {
            var best: List<Card>? = null
            for (s in 0..3) {
                val ranks =
                    cards
                        .filter { it.suit == s }
                        .map { it.rank }
                        .toSortedSet()
                        .toList()
                var i = 0
                while (i < ranks.size) {
                    var j = i
                    while (j + 1 < ranks.size && ranks[j + 1] == ranks[j] + 1) j++
                    if (j - i + 1 >= 3 && (best == null || j - i + 1 > best.size)) {
                        best = (ranks[i]..ranks[j]).map { r -> cards.first { it.suit == s && it.rank == r } }
                    }
                    i = j + 1
                }
            }
            if (best != null) return best
            return cards.groupBy { it.rank }.values.firstOrNull { it.size >= 3 }
        }
    }
}
