package com.github.damontecres.wholphin.ui.games.table

import kotlin.random.Random

/*
 * The card games played at the Table (Hearts, Spades, Crazy Eights, Rummy, Go Fish, Hold'em) all speak this
 * shape, so the TV table, the phones and Glowfin's players work the same way for every game.
 *
 * Each seat holds a hand. When it's a seat's turn the game says what it can do: which cards it may pick and which
 * buttons it may press (Play, Draw, Pass 3, Bid 4…). The seat picks cards (one or several), then presses a button.
 */

/** A button a seat can press now. [cards] is how many picked cards it needs: 0 none, a number, or -1 for 1 or more */
data class SeatButton(
    val id: String,
    val label: String,
    val cards: Int = 0,
)

/** What a seat can do right now */
data class SeatTurn(
    /** Plain words for the seat: "Your turn: play a card" */
    val prompt: String,
    /** The hand cards (by index) that can be picked */
    val playable: Set<Int> = emptySet(),
    val buttons: List<SeatButton> = emptyList(),
    /** Picking adds to a group (pass 3, lay down a set) instead of swapping the one picked card */
    val pickMany: Boolean = false,
)

/** What lies in the middle of the table */
data class TableCenter(
    /** Cards played in front of each seat (a trick) */
    val played: Map<Int, Card> = emptyMap(),
    /** Shared cards face up: the discard pile's top, or the five community cards */
    val shared: List<Card> = emptyList(),
    /** Cards left to draw, or -1 when there's no deck on the table */
    val deck: Int = -1,
    /** Rummy: the sets and runs laid down */
    val melds: List<List<Card>> = emptyList(),
    /** A short line, like "Suit is now ♥" or "Pot 340" */
    val label: String = "",
    /** Cards turned face up in front of seats (Hold'em at the showdown) */
    val reveal: Map<Int, List<Card>> = emptyMap(),
)

/** One card game being played at the table */
interface CardGame {
    val seats: Int
    val names: List<String>

    fun hand(seat: Int): List<Card>

    /** What [seat] can do now, or null when it's not their move */
    fun turn(seat: Int): SeatTurn?

    /** [seat] presses [button] with these hand cards picked; false when that isn't allowed */
    fun press(
        seat: Int,
        button: String,
        picked: List<Int>,
    ): Boolean

    /** Glowfin playing [seat]: the button and cards it picks */
    fun glowfin(seat: Int): Pair<String, List<Int>>?

    /** A trick or a hand result is showing; the table calls [advance] after a short pause */
    val waiting: Boolean

    fun advance()

    val over: Boolean

    /** Under a seat's name: "8 cards · 12 points" */
    fun seatLine(seat: Int): String

    fun center(): TableCenter

    /** What just happened, in plain words */
    val message: String

    /** The end-of-game lines, best first */
    fun results(): List<String>

    fun winners(): Set<Int>
}

/** Aces count high in trick games and poker */
val Card.high: Int get() = if (rank == 1) 14 else rank

/** Short text for a card, like "Q♠" */
val Card.label: String get() = rankName + suitMark

/** How a card travels to a phone: "12-3" */
val Card.code: String get() = "$rank-$suit"

fun cardFromCode(code: String): Card? {
    val (r, s) = code.split('-').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 2 } ?: return null
    return if (r in 1..13 && s in 0..3) Card(r, s) else null
}

fun shuffledDeck(random: Random): MutableList<Card> = Card.deck().shuffled(random).toMutableList()

/** Hands sorted by suit (clubs, diamonds, spades, hearts so colours take turns), then rank with aces high */
fun MutableList<Card>.sortHand() {
    val order = intArrayOf(0, 1, 3, 2)
    sortWith(compareBy({ order[it.suit] }, { it.high }))
}

val SUIT_NAMES = listOf("clubs", "diamonds", "hearts", "spades")

/** Who won a trick: the highest card of the suit led, or the highest trump */
fun trickWinner(
    trick: List<Pair<Int, Card>>,
    trump: Int = -1,
): Int {
    val led = trick.first().second.suit
    return trick
        .maxBy { (_, c) ->
            when (c.suit) {
                trump -> 200 + c.high
                led -> 100 + c.high
                else -> c.high
            }
        }.first
}

/** "Your lead: play a card", or which suit to follow */
fun followPrompt(trick: List<Pair<Int, Card>>): String =
    if (trick.isEmpty()) "Your lead: play a card" else "Your turn: follow ${SUIT_NAMES[trick.first().second.suit]} if you can"
