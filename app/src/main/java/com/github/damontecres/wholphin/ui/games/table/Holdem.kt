package com.github.damontecres.wholphin.ui.games.table

import kotlin.random.Random

/**
 * Texas Hold'em with pretend chips only (no money anywhere). Everyone starts with 1,000 chips. Two cards each,
 * then five shared cards come out in three steps with betting in between. Best five-card hand wins the pot.
 * The game ends when one player has every chip (or whenever you leave the table).
 */
class Holdem(
    override val names: List<String>,
    private val random: Random = Random.Default,
    start: Int = 1000,
) : CardGame {
    override val seats = names.size
    val chips = IntArray(seats) { start }
    private val holes = List(seats) { mutableListOf<Card>() }
    private val board = mutableListOf<Card>()
    private var deck = mutableListOf<Card>()
    private val inHand = BooleanArray(seats)
    private val folded = BooleanArray(seats)
    private val bet = IntArray(seats)
    private val put = IntArray(seats)
    private val needs = BooleanArray(seats)
    private var dealer = -1
    private var toAct = 0
    private var currentBet = 0
    private var minRaise = 0
    private var handNo = 0
    private var shown = emptyMap<Int, List<Card>>()

    private enum class Phase { BET, SHOWDOWN, HAND_DONE, OVER }

    private var phase = Phase.BET
    override var message = ""
        private set

    val bigBlind: Int get() = 20 shl minOf(4, handNo / 10)

    init {
        startHand()
    }

    private fun active() = (0 until seats).filter { inHand[it] && !folded[it] }

    private fun next(
        from: Int,
        test: (Int) -> Boolean,
    ): Int {
        for (k in 1..seats) {
            val s = (from + k) % seats
            if (test(s)) return s
        }
        return from
    }

    private fun allIn(s: Int) = inHand[s] && !folded[s] && chips[s] == 0

    private fun startHand() {
        val alive = (0 until seats).filter { chips[it] > 0 }
        if (alive.size <= 1) {
            phase = Phase.OVER
            message = "${names[alive.firstOrNull() ?: 0]} has every chip!"
            return
        }
        deck = shuffledDeck(random)
        board.clear()
        shown = emptyMap()
        for (s in 0 until seats) {
            holes[s].clear()
            inHand[s] = chips[s] > 0
            folded[s] = false
            bet[s] = 0
            put[s] = 0
        }
        dealer = next(if (dealer < 0) seats - 1 else dealer) { inHand[it] }
        repeat(2) { for (s in 0 until seats) if (inHand[s]) holes[s].add(deck.removeAt(deck.size - 1)) }
        holes.forEach { it.sortByDescending { c -> c.high } }
        val headsUp = alive.size == 2
        val sb = if (headsUp) dealer else next(dealer) { inHand[it] }
        val bb = next(sb) { inHand[it] }
        pay(sb, bigBlind / 2)
        pay(bb, bigBlind)
        currentBet = bigBlind
        minRaise = bigBlind
        for (s in 0 until seats) needs[s] = inHand[s] && chips[s] > 0
        toAct = next(bb) { needs[it] }
        phase = Phase.BET
        message = "New hand. Blinds ${bigBlind / 2} and $bigBlind."
        handNo++
        settleIfNoBetting()
    }

    private fun pay(
        s: Int,
        amount: Int,
    ) {
        val a = minOf(amount, chips[s])
        chips[s] -= a
        bet[s] += a
        put[s] += a
    }

    val pot: Int get() = put.sum()

    override fun hand(seat: Int): List<Card> = holes[seat]

    override fun turn(seat: Int): SeatTurn? {
        if (phase != Phase.BET || seat != toAct || !needs[seat]) return null
        val owe = currentBet - bet[seat]
        val buttons = mutableListOf(SeatButton("fold", "Fold"))
        if (owe <= 0) buttons.add(SeatButton("check", "Check")) else buttons.add(SeatButton("call", "Call ${minOf(owe, chips[seat])}"))
        val most = bet[seat] + chips[seat]
        val minTo = currentBet + minRaise
        if (most > minTo) {
            buttons.add(SeatButton("raise", "Raise to $minTo"))
            val potTo = currentBet + pot + maxOf(owe, 0)
            if (potTo > minTo && potTo < most) buttons.add(SeatButton("raisepot", "Raise to $potTo"))
        }
        if (chips[seat] > maxOf(owe, 0)) buttons.add(SeatButton("allin", "All in ($most)"))
        return SeatTurn(
            if (owe > 0) "$owe to call. Pot $pot." else "Your move. Pot $pot.",
            buttons = buttons,
        )
    }

    override fun press(
        seat: Int,
        button: String,
        picked: List<Int>,
    ): Boolean {
        val t = turn(seat) ?: return false
        if (t.buttons.none { it.id == button }) return false
        val owe = currentBet - bet[seat]
        when (button) {
            "fold" -> {
                folded[seat] = true
                message = "${names[seat]} folds"
            }

            "check" -> {
                message = "${names[seat]} checks"
            }

            "call" -> {
                pay(seat, owe)
                message = "${names[seat]} calls" + if (chips[seat] == 0) " and is all in" else ""
            }

            else -> {
                val to =
                    when (button) {
                        "raise" -> currentBet + minRaise
                        "raisepot" -> currentBet + pot + maxOf(owe, 0)
                        else -> bet[seat] + chips[seat]
                    }
                pay(seat, to - bet[seat])
                if (bet[seat] > currentBet) {
                    minRaise = maxOf(minRaise, bet[seat] - currentBet)
                    currentBet = bet[seat]
                    for (s in 0 until seats) if (s != seat && inHand[s] && !folded[s] && chips[s] > 0) needs[s] = true
                }
                message = if (chips[seat] == 0) "${names[seat]} is all in with ${bet[seat]}" else "${names[seat]} raises to ${bet[seat]}"
            }
        }
        needs[seat] = false
        afterAction(seat)
        return true
    }

    private fun afterAction(seat: Int) {
        val left = active()
        if (left.size == 1) {
            val w = left.first()
            val won = pot
            chips[w] += won
            put.fill(0)
            message = "${names[w]} wins $won"
            phase = Phase.HAND_DONE
            return
        }
        if ((0 until seats).any { needs[it] }) {
            toAct = next(seat) { needs[it] }
            return
        }
        nextStreet()
    }

    private fun nextStreet() {
        for (s in 0 until seats) bet[s] = 0
        currentBet = 0
        minRaise = bigBlind
        when (board.size) {
            0 -> repeat(3) { board.add(deck.removeAt(deck.size - 1)) }
            3, 4 -> board.add(deck.removeAt(deck.size - 1))
            else -> return showdown()
        }
        for (s in 0 until seats) needs[s] = inHand[s] && !folded[s] && chips[s] > 0
        toAct = next(dealer) { needs[it] }
        settleIfNoBetting()
    }

    /** With one or nobody left who can bet, the rest of the cards just come out */
    private fun settleIfNoBetting() {
        val canBet = (0 until seats).count { needs[it] }
        val owed = (0 until seats).any { needs[it] && bet[it] < currentBet }
        if (canBet == 0 || (canBet == 1 && !owed)) {
            needs.fill(false)
            nextStreet()
        }
    }

    private fun showdown() {
        val left = active()
        shown = left.associateWith { holes[it].toList() }
        val score = left.associateWith { best(holes[it] + board) }
        val levels = put.filter { it > 0 }.distinct().sorted()
        var prev = 0
        val won = IntArray(seats)
        for (level in levels) {
            val amount = (0 until seats).sumOf { minOf(put[it], level) - minOf(put[it], prev) }
            val eligible = left.filter { put[it] >= level }
            if (eligible.isNotEmpty()) {
                val top = eligible.maxOf { score.getValue(it) }
                val w = eligible.filter { score.getValue(it) == top }
                w.forEach { won[it] += amount / w.size }
                won[w.first()] += amount % w.size
            } else {
                // Everyone who put this much in folded: it goes back to the biggest bettor still in
                won[left.maxBy { put[it] }] += amount
            }
            prev = level
        }
        for (s in 0 until seats) chips[s] += won[s]
        put.fill(0)
        val winnersNow = (0 until seats).filter { won[it] > 0 }
        message = winnersNow.joinToString(", ") { "${names[it]} wins ${won[it]} with ${handName(score.getValue(it))}" }
        phase = Phase.SHOWDOWN
    }

    override val waiting: Boolean get() = phase == Phase.SHOWDOWN || phase == Phase.HAND_DONE

    override fun advance() {
        if (waiting) startHand()
    }

    override val over: Boolean get() = phase == Phase.OVER

    override fun seatLine(seat: Int): String =
        when {
            !inHand[seat] && chips[seat] == 0 -> "out of chips"
            folded[seat] -> "${chips[seat]} chips · folded"
            allIn(seat) -> "all in (${put[seat]})"
            else -> "${chips[seat]} chips" + if (bet[seat] > 0) " · bet ${bet[seat]}" else ""
        } + if (seat == dealer) " · dealer" else ""

    override fun center(): TableCenter =
        TableCenter(
            shared = board.toList(),
            label = "Pot $pot  ·  blinds ${bigBlind / 2}/$bigBlind",
            reveal = shown,
        )

    override fun results(): List<String> = (0 until seats).sortedByDescending { chips[it] }.map { "${names[it]}: ${chips[it]} chips" }

    override fun winners(): Set<Int> {
        val top = chips.max()
        return (0 until seats).filter { chips[it] == top }.toSet()
    }

    // ---------- Glowfin ----------

    override fun glowfin(seat: Int): Pair<String, List<Int>>? {
        val t = turn(seat) ?: return null
        val ids = t.buttons.map { it.id }
        val opponents = active().size - 1
        val p = equity(seat, opponents, 160)
        val owe = currentBet - bet[seat]
        val potOdds = if (owe <= 0) 0.0 else owe.toDouble() / (pot + owe)
        val mood = random.nextDouble() * 0.08
        val choice =
            when {
                p + mood > 0.82 && "raisepot" in ids -> "raisepot"
                p + mood > 0.82 && "allin" in ids && chips[seat] < bigBlind * 8 -> "allin"
                p + mood > 0.68 && "raise" in ids -> "raise"
                owe <= 0 -> "check"
                p + mood >= potOdds + 0.05 -> if ("call" in ids) "call" else "allin"
                else -> "fold"
            }
        return choice to emptyList()
    }

    /** How often this hand wins against [opponents] random hands (a quick sample) */
    fun equity(
        seat: Int,
        opponents: Int,
        tries: Int,
    ): Double {
        if (opponents <= 0) return 1.0
        val known = holes[seat] + board
        val rest = Card.deck().filter { it !in known }
        var win = 0.0
        repeat(tries) {
            val d = rest.shuffled(random)
            var k = 5 - board.size
            val full = board + d.subList(0, k)
            val mine = best(holes[seat] + full)
            var beaten = false
            var ties = 0
            repeat(opponents) {
                val theirs = best(listOf(d[k], d[k + 1]) + full)
                k += 2
                if (theirs > mine) {
                    beaten = true
                } else if (theirs == mine) {
                    ties++
                }
            }
            if (!beaten) win += 1.0 / (ties + 1)
        }
        return win / tries
    }

    companion object {
        /** The best five-card hand in these cards, as one comparable number */
        fun best(cards: List<Card>): Int {
            val n = cards.size
            if (n <= 5) return five(cards)
            if (n == 6) return (0 until 6).maxOf { i -> five(cards.filterIndexed { j, _ -> j != i }) }
            var top = 0
            for (a in 0 until n) {
                for (b in a + 1 until n) top = maxOf(top, five(cards.filterIndexed { i, _ -> i != a && i != b }))
            }
            return top
        }

        private fun five(c: List<Card>): Int {
            val r = c.map { it.high }.sortedDescending()
            val flush = c.all { it.suit == c[0].suit }
            val distinct = r.distinct()
            var straightTop = 0
            if (distinct.size == 5) {
                if (r[0] - r[4] == 4) {
                    straightTop = r[0]
                } else if (r == listOf(14, 5, 4, 3, 2)) {
                    straightTop = 5
                }
            }
            val groups =
                r
                    .groupBy {
                        it
                    }.entries
                    .sortedWith(compareByDescending<Map.Entry<Int, List<Int>>> { it.value.size }.thenByDescending { it.key })
            val counts = groups.map { it.value.size }
            val order = groups.flatMap { g -> g.value }
            val cat =
                when {
                    straightTop > 0 && flush -> 8
                    counts[0] == 4 -> 7
                    counts[0] == 3 && counts.getOrNull(1) == 2 -> 6
                    flush -> 5
                    straightTop > 0 -> 4
                    counts[0] == 3 -> 3
                    counts[0] == 2 && counts.getOrNull(1) == 2 -> 2
                    counts[0] == 2 -> 1
                    else -> 0
                }
            val kick = if (straightTop > 0 && (cat == 8 || cat == 4)) listOf(straightTop, 0, 0, 0, 0) else order
            return kick.fold(cat) { acc, v -> acc * 16 + v }
        }

        fun handName(score: Int): String {
            var cat = score
            repeat(5) { cat /= 16 }
            return listOf(
                "a high card",
                "a pair",
                "two pair",
                "three of a kind",
                "a straight",
                "a flush",
                "a full house",
                "four of a kind",
                "a straight flush",
            )[cat]
        }
    }
}
