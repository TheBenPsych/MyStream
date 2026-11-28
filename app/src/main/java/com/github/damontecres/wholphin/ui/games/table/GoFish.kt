package com.github.damontecres.wholphin.ui.games.table

import kotlin.random.Random

/**
 * Go Fish for 2 to 5. Pick a card you hold and ask someone for that number. If they have any they hand them all
 * over and you go again. If not, "Go fish!": draw one (and if it's the number you asked for, go again). Four of a
 * number make a book. When all 13 books are made, the most books wins.
 */
class GoFish(
    override val names: List<String>,
    private val random: Random = Random.Default,
) : CardGame {
    override val seats = names.size
    private val hands = List(seats) { mutableListOf<Card>() }
    private val deck = shuffledDeck(random)
    private val books = List(seats) { mutableListOf<Int>() }
    private var turnSeat = 0

    /** What each seat has asked for (so Glowfin remembers who holds what) */
    private val asked = List(seats) { mutableSetOf<Int>() }
    override var message = ""
        private set

    init {
        val each = if (seats <= 3) 7 else 5
        repeat(each) { for (h in hands) h.add(deck.removeAt(deck.size - 1)) }
        for (s in 0 until seats) makeBooks(s)
        hands.forEach { it.sortByRank() }
        turnSeat = random.nextInt(seats)
        refillIfEmpty(turnSeat)
        message = "${names[turnSeat]} asks first"
    }

    private fun MutableList<Card>.sortByRank() = sortWith(compareBy({ it.rank }, { it.suit }))

    private fun makeBooks(seat: Int): List<Int> {
        val made =
            hands[seat]
                .groupBy { it.rank }
                .filter { it.value.size == 4 }
                .keys
                .toList()
        for (r in made) {
            hands[seat].removeAll { it.rank == r }
            books[seat].add(r)
            asked.forEach { it.remove(r) }
        }
        return made
    }

    private fun rankWord(r: Int) =
        when (r) {
            1 -> "aces"
            11 -> "jacks"
            12 -> "queens"
            13 -> "kings"
            6 -> "sixes"
            else -> "${r}s"
        }

    private fun refillIfEmpty(seat: Int) {
        if (hands[seat].isEmpty() && deck.isNotEmpty()) hands[seat].add(deck.removeAt(deck.size - 1))
    }

    override fun hand(seat: Int): List<Card> = hands[seat]

    private fun others(seat: Int) = (0 until seats).filter { it != seat && hands[it].isNotEmpty() }

    override fun turn(seat: Int): SeatTurn? {
        if (over || seat != turnSeat) return null
        val targets = others(seat)
        if (hands[seat].isEmpty() ||
            targets.isEmpty()
        ) {
            return SeatTurn("Nothing to ask for. Pass.", buttons = listOf(SeatButton("pass", "Pass")))
        }
        return SeatTurn(
            "Pick a card, then who to ask",
            hands[seat].indices.toSet(),
            targets.map { SeatButton("ask$it", "Ask ${names[it]}", 1) },
        )
    }

    override fun press(
        seat: Int,
        button: String,
        picked: List<Int>,
    ): Boolean {
        val t = turn(seat) ?: return false
        if (button == "pass" && t.buttons.any { it.id == "pass" }) {
            message = "${names[seat]} has nothing to ask for"
            nextTurn()
            return true
        }
        if (!button.startsWith("ask")) return false
        val who = button.removePrefix("ask").toIntOrNull()?.takeIf { it in others(seat) } ?: return false
        val i = picked.singleOrNull()?.takeIf { it in hands[seat].indices } ?: return false
        val rank = hands[seat][i].rank
        asked[seat].add(rank)
        val got = hands[who].filter { it.rank == rank }
        if (got.isNotEmpty()) {
            hands[who].removeAll(got)
            asked[who].remove(rank)
            hands[seat].addAll(got)
            message = "${names[who]} gave ${names[seat]} ${got.size} ${if (got.size == 1) rankWord(rank).dropLast(1) else rankWord(rank)}"
            afterGain(seat)
            refillIfEmpty(who)
            if (!over) {
                refillIfEmpty(seat)
                if (hands[seat].isEmpty()) nextTurn()
            }
            return true
        }
        if (deck.isEmpty()) {
            message = "${names[who]}: \"Go fish!\" The pond is empty."
            nextTurn()
            return true
        }
        val drawn = deck.removeAt(deck.size - 1)
        hands[seat].add(drawn)
        if (drawn.rank == rank) {
            message = "Go fish! ${names[seat]} caught the one they wanted and goes again"
            afterGain(seat)
            if (!over) {
                refillIfEmpty(seat)
                if (hands[seat].isEmpty()) nextTurn()
            }
        } else {
            message = "${names[who]}: \"Go fish!\""
            afterGain(seat)
            nextTurn()
        }
        return true
    }

    private fun afterGain(seat: Int) {
        val made = makeBooks(seat)
        hands[seat].sortByRank()
        if (made.isNotEmpty()) message += ". ${names[seat]} made a book of ${made.joinToString(" and ") { rankWord(it) }}!"
    }

    private fun nextTurn() {
        if (over) return
        repeat(seats) {
            turnSeat = (turnSeat + 1) % seats
            refillIfEmpty(turnSeat)
            if (hands[turnSeat].isNotEmpty()) return
        }
    }

    override val waiting = false

    override fun advance() {}

    override val over: Boolean get() = books.sumOf { it.size } == 13

    override fun seatLine(seat: Int): String = "${hands[seat].size} cards · ${books[seat].size} books"

    override fun center(): TableCenter = TableCenter(deck = deck.size, label = "The pond")

    /** The books in front of a seat */
    fun booksOf(seat: Int): List<Int> = books[seat]

    override fun results(): List<String> =
        (0 until seats).sortedByDescending { books[it].size }.map { "${names[it]}: ${books[it].size} books" }

    override fun winners(): Set<Int> {
        val top = books.maxOf { it.size }
        return (0 until seats).filter { books[it].size == top }.toSet()
    }

    override fun glowfin(seat: Int): Pair<String, List<Int>>? {
        val t = turn(seat) ?: return null
        if (t.buttons.any { it.id == "pass" }) return "pass" to emptyList()
        val h = hands[seat]
        val targets = others(seat)
        // Someone asked for a number we hold: they have it
        for (i in h.indices) {
            val holder = targets.firstOrNull { h[i].rank in asked[it] }
            if (holder != null) return "ask$holder" to listOf(i)
        }
        val rank = h.groupBy { it.rank }.maxBy { it.value.size + random.nextDouble() }.key
        return "ask${targets.random(random)}" to listOf(h.indexOfFirst { it.rank == rank })
    }
}
