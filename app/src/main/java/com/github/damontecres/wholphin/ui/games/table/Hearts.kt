package com.github.damontecres.wholphin.ui.games.table

import kotlin.random.Random

/**
 * Hearts for four. Pass three cards (left, right, across, then keep), the 2 of clubs starts, follow suit when you
 * can. Each heart taken is 1 point and the queen of spades is 13; take them all and everyone else gets 26 instead.
 * When someone reaches [target] points the lowest score wins.
 */
class Hearts(
    override val names: List<String>,
    private val random: Random = Random.Default,
    private val target: Int = 50,
) : CardGame {
    override val seats = 4
    private val hands = List(4) { mutableListOf<Card>() }
    val scores = IntArray(4)
    private val taken = IntArray(4)
    private val tricks = IntArray(4)
    private var handNo = 0
    private val passed = arrayOfNulls<List<Card>>(4)
    private val trick = mutableListOf<Pair<Int, Card>>()
    private var leader = 0
    private var turnSeat = 0
    private var broken = false
    private var firstTrick = true

    private enum class Phase { PASS, PLAY, TRICK_DONE, HAND_DONE, OVER }

    private var phase = Phase.PASS
    override var message = ""
        private set

    init {
        deal()
    }

    private val passDir: Int get() = handNo % 4

    private fun passTo(seat: Int): Int =
        when (passDir) {
            0 -> (seat + 1) % 4
            1 -> (seat + 3) % 4
            else -> (seat + 2) % 4
        }

    private fun deal() {
        val deck = shuffledDeck(random)
        hands.forEachIndexed { i, h ->
            h.clear()
            h.addAll(deck.subList(i * 13, i * 13 + 13))
            h.sortHand()
        }
        taken.fill(0)
        tricks.fill(0)
        passed.fill(null)
        trick.clear()
        broken = false
        firstTrick = true
        if (passDir == 3) startPlay() else phase = Phase.PASS
        message =
            if (passDir == 3) {
                "No passing this hand."
            } else {
                "Everyone passes 3 cards to the " + listOf("left", "right", "player across")[passDir] + "."
            }
    }

    private fun startPlay() {
        phase = Phase.PLAY
        leader = hands.indexOfFirst { h -> h.any { it.rank == 2 && it.suit == 0 } }
        turnSeat = leader
    }

    override fun hand(seat: Int): List<Card> = hands[seat]

    /** The hand cards [seat] may play now */
    fun legal(seat: Int): List<Int> {
        val h = hands[seat]
        val all = h.indices.toList()
        if (trick.isEmpty()) {
            if (firstTrick) return all.filter { h[it].rank == 2 && h[it].suit == 0 }
            if (!broken) {
                val notHearts = all.filter { h[it].suit != 2 }
                if (notHearts.isNotEmpty()) return notHearts
            }
            return all
        }
        val led = trick.first().second.suit
        val follow = all.filter { h[it].suit == led }
        if (follow.isNotEmpty()) return follow
        if (firstTrick) {
            val clean = all.filter { !points(h[it]) }
            if (clean.isNotEmpty()) return clean
        }
        return all
    }

    private fun points(c: Card) = c.suit == 2 || (c.suit == 3 && c.rank == 12)

    private fun worth(c: Card) =
        when {
            c.suit == 2 -> 1
            c.suit == 3 && c.rank == 12 -> 13
            else -> 0
        }

    override fun turn(seat: Int): SeatTurn? =
        when (phase) {
            Phase.PASS -> {
                if (passed[seat] != null) {
                    null
                } else {
                    SeatTurn(
                        "Pick 3 cards to pass to ${names[passTo(seat)]}",
                        hands[seat].indices.toSet(),
                        listOf(SeatButton("pass", "Pass these 3", 3)),
                        pickMany = true,
                    )
                }
            }

            Phase.PLAY -> {
                if (seat != turnSeat) {
                    null
                } else {
                    SeatTurn(
                        followPrompt(trick),
                        legal(seat).toSet(),
                        listOf(SeatButton("play", "Play", 1)),
                    )
                }
            }

            else -> {
                null
            }
        }

    override fun press(
        seat: Int,
        button: String,
        picked: List<Int>,
    ): Boolean {
        val t = turn(seat) ?: return false
        if (button == "pass" && phase == Phase.PASS) {
            val idx = picked.distinct()
            if (idx.size != 3 || idx.any { it !in hands[seat].indices }) return false
            passed[seat] = idx.map { hands[seat][it] }
            hands[seat].removeAll(passed[seat]!!)
            if (passed.all { it != null }) {
                for (s in 0..3) {
                    hands[passTo(s)].addAll(passed[s]!!)
                }
                hands.forEach { it.sortHand() }
                startPlay()
                message = "Cards passed. ${names[leader]} has the 2♣ and starts."
            }
            return true
        }
        if (button == "play" && phase == Phase.PLAY) {
            val i = picked.singleOrNull() ?: return false
            if (i !in t.playable) return false
            val card = hands[seat].removeAt(i)
            if (card.suit == 2) broken = true
            trick.add(seat to card)
            if (trick.size == 4) {
                val w = trickWinner(trick)
                val pts = trick.sumOf { worth(it.second) }
                phase = Phase.TRICK_DONE
                message = "${names[w]} takes the trick" + if (pts > 0) " (+$pts)" else ""
            } else {
                turnSeat = (seat + 1) % 4
                message = "${names[seat]} played ${card.label}"
            }
            return true
        }
        return false
    }

    override val waiting: Boolean get() = phase == Phase.TRICK_DONE || phase == Phase.HAND_DONE

    override fun advance() {
        when (phase) {
            Phase.TRICK_DONE -> {
                val w = trickWinner(trick)
                taken[w] += trick.sumOf { worth(it.second) }
                tricks[w]++
                trick.clear()
                firstTrick = false
                leader = w
                turnSeat = w
                if (hands.all { it.isEmpty() }) {
                    val moon = taken.indexOfFirst { it == 26 }
                    if (moon >= 0) {
                        for (s in 0..3) if (s != moon) scores[s] += 26
                        message = "${names[moon]} shot the moon! Everyone else gets 26."
                    } else {
                        for (s in 0..3) scores[s] += taken[s]
                        message = "Hand over: " + (0..3).joinToString(", ") { "${names[it]} +${taken[it]}" }
                    }
                    phase = Phase.HAND_DONE
                } else {
                    phase = Phase.PLAY
                    message = "${names[w]} leads"
                }
            }

            Phase.HAND_DONE -> {
                if (scores.any { it >= target }) {
                    phase = Phase.OVER
                    message = "Game over"
                } else {
                    handNo++
                    deal()
                }
            }

            else -> {}
        }
    }

    override val over: Boolean get() = phase == Phase.OVER

    override fun seatLine(seat: Int): String =
        "${scores[seat]} points" + if (phase != Phase.PASS && taken[seat] > 0) " · +${taken[seat]} this hand" else ""

    override fun center(): TableCenter =
        TableCenter(
            played = trick.toMap(),
            label =
                when {
                    phase == Phase.PASS -> "Passing cards"
                    broken -> "Hearts are broken"
                    else -> "First to $target loses"
                },
        )

    override fun results(): List<String> = (0..3).sortedBy { scores[it] }.map { "${names[it]}: ${scores[it]} points" }

    override fun winners(): Set<Int> {
        val low = scores.min()
        return (0..3).filter { scores[it] == low }.toSet()
    }

    // ---------- Glowfin ----------

    override fun glowfin(seat: Int): Pair<String, List<Int>>? {
        val t = turn(seat) ?: return null
        val h = hands[seat]
        if (phase == Phase.PASS) {
            val danger =
                h.indices.sortedByDescending { i ->
                    val c = h[i]
                    when {
                        c.suit == 3 && c.rank == 12 -> 100
                        c.suit == 3 && c.high >= 13 -> 90 + c.high
                        c.suit == 2 -> 40 + c.high
                        else -> c.high
                    }
                }
            return "pass" to danger.take(3)
        }
        val legal = t.playable.toList()
        val pick =
            if (trick.isEmpty()) {
                // Lead low from a suit that isn't hearts
                legal.minBy { i -> h[i].high + if (h[i].suit == 2) 20 else 0 }
            } else {
                val led = trick.first().second.suit
                val high = trick.filter { it.second.suit == led }.maxOf { it.second.high }
                val trickPoints = trick.any { points(it.second) }
                if (h[legal.first()].suit == led) {
                    val under = legal.filter { h[it].high < high }
                    when {
                        under.isNotEmpty() -> {
                            under.maxBy { h[it].high }
                        }

                        trick.size == 3 && !trickPoints -> {
                            legal.filter { !(h[it].suit == 3 && h[it].rank == 12) }.maxByOrNull { h[it].high }
                                ?: legal.minBy { h[it].high }
                        }

                        else -> {
                            legal.minBy { h[it].high }
                        }
                    }
                } else {
                    // Can't follow: get rid of the queen, then big hearts, then big cards
                    legal.maxBy { i ->
                        val c = h[i]
                        when {
                            c.suit == 3 && c.rank == 12 -> 1000
                            c.suit == 2 -> 100 + c.high
                            c.suit == 3 && c.high > 12 -> 90 + c.high
                            else -> c.high
                        }
                    }
                }
            }
        return "play" to listOf(pick)
    }
}
