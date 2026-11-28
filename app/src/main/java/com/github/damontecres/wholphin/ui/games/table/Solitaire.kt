package com.github.damontecres.wholphin.ui.games.table

import kotlin.random.Random

/** A playing card: rank 1 (ace) to 13 (king), suit 0 clubs, 1 diamonds, 2 hearts, 3 spades */
data class Card(
    val rank: Int,
    val suit: Int,
) {
    val red: Boolean get() = suit == 1 || suit == 2

    val rankName: String
        get() =
            when (rank) {
                1 -> "A"
                11 -> "J"
                12 -> "Q"
                13 -> "K"
                else -> rank.toString()
            }

    val suitMark: String get() = SUITS[suit]

    override fun toString(): String = rankName + suitMark

    companion object {
        val SUITS = listOf("♣", "♦", "♥", "♠")

        fun deck(): List<Card> = (0..3).flatMap { s -> (1..13).map { Card(it, s) } }
    }
}

/**
 * Solitaire (Klondike, turn one card at a time) for the Table tab. Piles: the stock, the face-up waste, four home
 * piles (aces up) and seven columns (kings down, colours taking turns).
 */
class Solitaire(
    seed: Long = Random.nextLong(),
) {
    val stock = ArrayList<Card>()
    val waste = ArrayList<Card>()
    val homes = List(4) { ArrayList<Card>() }
    val columns = List(7) { ArrayList<Card>() }

    /** How many cards at the top of each column are still face down */
    val hidden = IntArray(7)
    var moves = 0
        private set

    private val history = ArrayList<Snapshot>()

    private class Snapshot(
        val stock: List<Card>,
        val waste: List<Card>,
        val homes: List<List<Card>>,
        val columns: List<List<Card>>,
        val hidden: IntArray,
    )

    init {
        val deck = Card.deck().shuffled(Random(seed)).toMutableList()
        for (c in 0 until 7) {
            for (i in 0..c) columns[c].add(deck.removeAt(deck.size - 1))
            hidden[c] = c
        }
        stock.addAll(deck)
    }

    /** Where a card or pile can be */
    sealed interface Pile {
        data object Waste : Pile

        data class Home(
            val index: Int,
        ) : Pile

        data class Column(
            val index: Int,
        ) : Pile
    }

    private fun save() {
        history.add(
            Snapshot(stock.toList(), waste.toList(), homes.map { it.toList() }, columns.map { it.toList() }, hidden.copyOf()),
        )
        if (history.size > 200) history.removeAt(0)
    }

    fun canUndo(): Boolean = history.isNotEmpty()

    fun undo() {
        val s = history.removeLastOrNull() ?: return
        stock.clear()
        stock.addAll(s.stock)
        waste.clear()
        waste.addAll(s.waste)
        homes.forEachIndexed { i, h ->
            h.clear()
            h.addAll(s.homes[i])
        }
        columns.forEachIndexed { i, c ->
            c.clear()
            c.addAll(s.columns[i])
        }
        s.hidden.copyInto(hidden)
        moves++
    }

    /** Turns the next card from the stock (or turns the waste back over when the stock is empty) */
    fun draw() {
        if (stock.isEmpty() && waste.isEmpty()) return
        save()
        if (stock.isEmpty()) {
            stock.addAll(waste.reversed())
            waste.clear()
        } else {
            waste.add(stock.removeAt(stock.size - 1))
        }
        moves++
    }

    /** The cards that would move if you pick up [from] at [index] (columns can move a run) */
    fun pickUp(
        from: Pile,
        index: Int = -1,
    ): List<Card> =
        when (from) {
            Pile.Waste -> {
                listOfNotNull(waste.lastOrNull())
            }

            is Pile.Home -> {
                listOfNotNull(homes[from.index].lastOrNull())
            }

            is Pile.Column -> {
                val col = columns[from.index]
                val i = if (index < 0) col.size - 1 else index
                if (i < hidden[from.index] || i !in col.indices) emptyList() else col.subList(i, col.size).toList()
            }
        }

    fun canPlace(
        cards: List<Card>,
        to: Pile,
    ): Boolean {
        val first = cards.firstOrNull() ?: return false
        return when (to) {
            Pile.Waste -> {
                false
            }

            is Pile.Home -> {
                if (cards.size != 1) return false
                val top = homes[to.index].lastOrNull()
                if (top == null) first.rank == 1 else top.suit == first.suit && first.rank == top.rank + 1
            }

            is Pile.Column -> {
                val top = columns[to.index].lastOrNull()
                if (top == null) first.rank == 13 else top.red != first.red && first.rank == top.rank - 1
            }
        }
    }

    /** Moves the cards picked up at [from] / [index] onto [to]; false if that's not allowed */
    fun move(
        from: Pile,
        index: Int,
        to: Pile,
    ): Boolean {
        if (from == to) return false
        val cards = pickUp(from, index)
        if (!canPlace(cards, to)) return false
        save()
        when (from) {
            Pile.Waste -> {
                waste.removeAt(waste.size - 1)
            }

            is Pile.Home -> {
                homes[from.index].removeAt(homes[from.index].size - 1)
            }

            is Pile.Column -> {
                val col = columns[from.index]
                repeat(cards.size) { col.removeAt(col.size - 1) }
                if (hidden[from.index] >= col.size && col.isNotEmpty()) hidden[from.index] = col.size - 1
                if (col.isEmpty()) hidden[from.index] = 0
            }
        }
        when (to) {
            is Pile.Home -> {
                homes[to.index].addAll(cards)
            }

            is Pile.Column -> {
                columns[to.index].addAll(cards)
            }

            Pile.Waste -> {}
        }
        moves++
        return true
    }

    /** Sends one card straight to a home pile if it fits (OK twice on a card) */
    fun sendHome(
        from: Pile,
        index: Int = -1,
    ): Boolean {
        val cards = pickUp(from, index)
        if (cards.size != 1) return false
        for (h in 0..3) if (move(from, index, Pile.Home(h))) return true
        return false
    }

    /** Where the cards could go (homes first), so the remote can jump straight there */
    fun targets(cards: List<Card>): List<Pile> =
        (0..3).map { Pile.Home(it) }.filter { canPlace(cards, it) } +
            (0..6).map { Pile.Column(it) }.filter { canPlace(cards, it) }

    fun won(): Boolean = homes.all { it.size == 13 }

    /** Every card is face up and the stock is used up, so the rest can play itself */
    fun canFinish(): Boolean = !won() && stock.isEmpty() && waste.isEmpty() && hidden.all { it == 0 }

    /** One step of finishing by itself; false when nothing moved */
    fun finishStep(): Boolean {
        for (c in 0..6) if (columns[c].isNotEmpty() && sendHome(Pile.Column(c))) return true
        return false
    }
}
