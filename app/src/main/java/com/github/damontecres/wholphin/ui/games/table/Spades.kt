package com.github.damontecres.wholphin.ui.games.table

import kotlin.random.Random

/**
 * Spades for two teams of two (seats 1 and 3 against 2 and 4, partners sit across). Everyone says how many tricks
 * they'll win; spades always win over other suits. A team that makes its bid scores 10 a trick plus 1 for each
 * extra (10 extras cost 100). Missing the bid loses 10 a trick. Nil means "none at all": +100, or -100 if you take
 * one. First team to [target] wins.
 */
class Spades(
    override val names: List<String>,
    private val random: Random = Random.Default,
    private val target: Int = 300,
) : CardGame {
    override val seats = 4
    private val hands = List(4) { mutableListOf<Card>() }
    val teamScore = IntArray(2)
    private val bags = IntArray(2)
    private val bids = IntArray(4) { -1 }
    private val tricks = IntArray(4)
    private val trick = mutableListOf<Pair<Int, Card>>()
    private var dealer = 3
    private var turnSeat = 0
    private var broken = false

    private enum class Phase { BID, PLAY, TRICK_DONE, HAND_DONE, OVER }

    private var phase = Phase.BID
    override var message = ""
        private set

    init {
        deal()
    }

    private fun team(seat: Int) = seat % 2

    fun teamName(t: Int) = "${names[t]} and ${names[t + 2]}"

    private fun deal() {
        val deck = shuffledDeck(random)
        hands.forEachIndexed { i, h ->
            h.clear()
            h.addAll(deck.subList(i * 13, i * 13 + 13))
            h.sortHand()
        }
        bids.fill(-1)
        tricks.fill(0)
        trick.clear()
        broken = false
        dealer = (dealer + 1) % 4
        turnSeat = (dealer + 1) % 4
        phase = Phase.BID
        message = "${names[turnSeat]} bids first."
    }

    override fun hand(seat: Int): List<Card> = hands[seat]

    fun legal(seat: Int): List<Int> {
        val h = hands[seat]
        val all = h.indices.toList()
        if (trick.isEmpty()) {
            if (!broken) {
                val other = all.filter { h[it].suit != 3 }
                if (other.isNotEmpty()) return other
            }
            return all
        }
        val led = trick.first().second.suit
        return all.filter { h[it].suit == led }.ifEmpty { all }
    }

    override fun turn(seat: Int): SeatTurn? =
        when {
            seat != turnSeat -> {
                null
            }

            phase == Phase.BID -> {
                SeatTurn(
                    "How many tricks will you win?" + partnerBid(seat),
                    buttons = listOf(SeatButton("bid0", "Nil")) + (1..13).map { SeatButton("bid$it", "$it") },
                )
            }

            phase == Phase.PLAY -> {
                SeatTurn(
                    followPrompt(trick),
                    legal(seat).toSet(),
                    listOf(SeatButton("play", "Play", 1)),
                )
            }

            else -> {
                null
            }
        }

    private fun partnerBid(seat: Int): String {
        val p = (seat + 2) % 4
        return if (bids[p] >= 0) " (${names[p]} said ${bidName(bids[p])})" else ""
    }

    private fun bidName(b: Int) = if (b == 0) "Nil" else "$b"

    override fun press(
        seat: Int,
        button: String,
        picked: List<Int>,
    ): Boolean {
        val t = turn(seat) ?: return false
        if (phase == Phase.BID && button.startsWith("bid")) {
            val b = button.removePrefix("bid").toIntOrNull()?.takeIf { it in 0..13 } ?: return false
            bids[seat] = b
            message = "${names[seat]} bids ${bidName(b)}"
            turnSeat = (seat + 1) % 4
            if (bids.all { it >= 0 }) {
                phase = Phase.PLAY
                turnSeat = (dealer + 1) % 4
            }
            return true
        }
        if (phase == Phase.PLAY && button == "play") {
            val i = picked.singleOrNull() ?: return false
            if (i !in t.playable) return false
            val card = hands[seat].removeAt(i)
            if (card.suit == 3) broken = true
            trick.add(seat to card)
            if (trick.size == 4) {
                phase = Phase.TRICK_DONE
                message = "${names[trickWinner(trick, 3)]} takes the trick"
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
                val w = trickWinner(trick, 3)
                tricks[w]++
                trick.clear()
                turnSeat = w
                if (hands.all { it.isEmpty() }) {
                    scoreHand()
                    phase = Phase.HAND_DONE
                } else {
                    phase = Phase.PLAY
                    message = "${names[w]} leads"
                }
            }

            Phase.HAND_DONE -> {
                val top = teamScore.max()
                phase =
                    if ((top >= target && teamScore[0] != teamScore[1]) || teamScore.any { it <= -200 }) {
                        message = "Game over"
                        Phase.OVER
                    } else {
                        deal()
                        Phase.BID
                    }
            }

            else -> {}
        }
    }

    private fun scoreHand() {
        val lines = mutableListOf<String>()
        for (t in 0..1) {
            val players = listOf(t, t + 2)
            var gained = 0
            var bid = 0
            var won = 0
            for (p in players) {
                if (bids[p] == 0) {
                    gained += if (tricks[p] == 0) 100 else -100
                    // A nil player's tricks still count as extras for the team
                    bags[t] += tricks[p]
                    if (tricks[p] > 0) gained += tricks[p]
                } else {
                    bid += bids[p]
                    won += tricks[p]
                }
            }
            if (bid > 0) {
                if (won >= bid) {
                    gained += bid * 10 + (won - bid)
                    bags[t] += won - bid
                } else {
                    gained -= bid * 10
                }
            }
            if (bags[t] >= 10) {
                bags[t] -= 10
                gained -= 100
            }
            teamScore[t] += gained
            lines.add("${teamName(t)} ${if (gained >= 0) "+" else ""}$gained")
        }
        message = "Hand over: " + lines.joinToString(", ")
    }

    override val over: Boolean get() = phase == Phase.OVER

    override fun seatLine(seat: Int): String {
        val bid = if (bids[seat] >= 0) "bid ${bidName(bids[seat])} · won ${tricks[seat]}" else "bidding…"
        return "$bid · team ${teamScore[team(seat)]}"
    }

    override fun center(): TableCenter =
        TableCenter(
            played = trick.toMap(),
            label = "${teamName(0)}: ${teamScore[0]}   ·   ${teamName(1)}: ${teamScore[1]}   ·   first to $target",
        )

    override fun results(): List<String> = listOf(0, 1).sortedByDescending { teamScore[it] }.map { "${teamName(it)}: ${teamScore[it]}" }

    override fun winners(): Set<Int> {
        val t = if (teamScore[0] >= teamScore[1]) 0 else 1
        return setOf(t, t + 2)
    }

    // ---------- Glowfin ----------

    override fun glowfin(seat: Int): Pair<String, List<Int>>? {
        val t = turn(seat) ?: return null
        val h = hands[seat]
        if (phase == Phase.BID) {
            var est = 0.0
            for (s in 0..3) {
                val suit = h.filter { it.suit == s }
                val len = suit.size
                if (s == 3) {
                    est += suit.count { it.high >= 13 }
                    est += maxOf(0, len - 3) * 0.9
                    if (suit.any { it.high == 12 } && len >= 3) est += 0.5
                } else {
                    if (suit.any { it.high == 14 }) est += 1.0
                    if (suit.any { it.high == 13 } && len >= 2 && len <= 5) est += 0.8
                    if (len <= 1 && h.count { it.suit == 3 } >= 3) est += 0.6
                }
            }
            val bid = est.toInt().coerceIn(1, 13)
            return "bid$bid" to emptyList()
        }
        val legal = t.playable.toList()
        val me = team(seat)
        val teamBid = listOf(me, me + 2).sumOf { if (bids[it] > 0) bids[it] else 0 }
        val teamWon = listOf(me, me + 2).sumOf { tricks[it] }
        val nil = bids[seat] == 0
        val want = !nil && teamWon < teamBid

        fun power(c: Card) = c.high + if (c.suit == 3) 20 else 0
        val pick =
            if (trick.isEmpty()) {
                if (want) legal.maxBy { power(h[it]) } else legal.minBy { power(h[it]) }
            } else {
                val w = trickWinner(trick, 3)
                val winCard = trick.first { it.first == w }.second
                val led = trick.first().second.suit

                fun beats(c: Card) =
                    when {
                        c.suit == winCard.suit -> c.high > winCard.high
                        c.suit == 3 -> true
                        else -> false
                    } &&
                        (c.suit == led || c.suit == 3)
                val winners = legal.filter { beats(h[it]) }
                val partnerWinning = w == (seat + 2) % 4
                when {
                    want && !partnerWinning && winners.isNotEmpty() -> winners.minBy { power(h[it]) }
                    nil -> legal.filter { !beats(h[it]) }.maxByOrNull { power(h[it]) } ?: legal.minBy { power(h[it]) }
                    else -> legal.minBy { power(h[it]) }
                }
            }
        return "play" to listOf(pick)
    }
}
