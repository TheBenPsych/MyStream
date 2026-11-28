package com.github.damontecres.wholphin.ui.games.table

import kotlin.random.Random

/** The boxes on a Five Dice score sheet */
enum class DiceBox(
    val label: String,
    val hint: String,
    val upper: Boolean = false,
) {
    ONES("Ones", "Add up the 1s", true),
    TWOS("Twos", "Add up the 2s", true),
    THREES("Threes", "Add up the 3s", true),
    FOURS("Fours", "Add up the 4s", true),
    FIVES("Fives", "Add up the 5s", true),
    SIXES("Sixes", "Add up the 6s", true),
    THREE_KIND("Three alike", "3 the same: all dice"),
    FOUR_KIND("Four alike", "4 the same: all dice"),
    FULL_HOUSE("Full house", "3 + 2 the same: 25"),
    SMALL_RUN("Small run", "4 in a row: 30"),
    BIG_RUN("Big run", "5 in a row: 40"),
    FIVE_KIND("Five alike", "All 5 the same: 50"),
    CHANCE("Chance", "Any dice: all dice"),
    ;

    /** What these dice would score in this box */
    fun score(dice: IntArray): Int {
        val counts = IntArray(7)
        dice.forEach { counts[it]++ }
        val sum = dice.sum()
        val has = (1..6).filter { counts[it] > 0 }.toSet()

        fun run(n: Int) = (1..(7 - n)).any { s -> (s until s + n).all { it in has } }
        return when (this) {
            ONES, TWOS, THREES, FOURS, FIVES, SIXES -> {
                val face = ordinal + 1
                counts[face] * face
            }

            THREE_KIND -> {
                if (counts.any { it >= 3 }) sum else 0
            }

            FOUR_KIND -> {
                if (counts.any { it >= 4 }) sum else 0
            }

            FULL_HOUSE -> {
                if (counts.any { it == 3 } && counts.any { it == 2 }) 25 else 0
            }

            SMALL_RUN -> {
                if (run(4)) 30 else 0
            }

            BIG_RUN -> {
                if (run(5)) 40 else 0
            }

            FIVE_KIND -> {
                if (counts.any { it == 5 }) 50 else 0
            }

            CHANCE -> {
                sum
            }
        }
    }
}

/** One Five Dice player: a person passing the remote, or Glowfin */
class DicePlayer(
    val name: String,
    val glowfin: Boolean,
) {
    val sheet = mutableMapOf<DiceBox, Int>()

    val upper: Int get() = sheet.filterKeys { it.upper }.values.sum()

    /** 35 extra for 63 or more in the top half */
    val bonus: Int get() = if (upper >= 63) 35 else 0

    val total: Int get() = sheet.values.sum() + bonus

    val done: Boolean get() = sheet.size == DiceBox.entries.size
}

/**
 * Five Dice for the Table tab: roll five dice up to three times a turn, keeping any you like, then fill one box
 * on your score sheet. 13 turns each; the biggest total wins.
 */
class FiveDice(
    val players: List<DicePlayer>,
    private val random: Random = Random.Default,
) {
    val dice = IntArray(5) { 1 }
    val held = BooleanArray(5)
    var rolls = 0
        private set
    var current = 0
        private set

    val player: DicePlayer get() = players[current]

    val over: Boolean get() = players.all { it.done }

    fun canRoll(): Boolean = rolls < 3 && !over

    fun roll() {
        if (!canRoll()) return
        for (i in 0..4) if (rolls == 0 || !held[i]) dice[i] = random.nextInt(1, 7)
        rolls++
    }

    fun toggle(i: Int) {
        if (rolls in 1..2) held[i] = !held[i]
    }

    fun canScore(box: DiceBox): Boolean = rolls > 0 && box !in player.sheet

    /** Fills [box] for the player whose turn it is, then passes the dice on */
    fun score(box: DiceBox): Boolean {
        if (!canScore(box)) return false
        player.sheet[box] = box.score(dice)
        rolls = 0
        held.fill(false)
        if (!over) {
            do {
                current = (current + 1) % players.size
            } while (players[current].done)
        }
        return true
    }

    fun winners(): List<DicePlayer> {
        val top = players.maxOf { it.total }
        return players.filter { it.total == top }
    }

    // ---------- Glowfin playing ----------

    /** Which dice Glowfin keeps before its next roll */
    fun glowfinHolds(): BooleanArray {
        val counts = IntArray(7)
        dice.forEach { counts[it]++ }
        val open = DiceBox.entries.filter { it !in player.sheet }
        val keep = BooleanArray(5)
        // Going for a run when 4 different in a row are already there and a run box is open
        val has = dice.toSet()
        val runStart = (1..3).firstOrNull { s -> (s until s + 4).all { it in has } }
        if (runStart != null && (DiceBox.BIG_RUN in open || DiceBox.SMALL_RUN in open)) {
            val wanted = (runStart until runStart + 4).toMutableSet()
            for (i in 0..4) {
                if (dice[i] in wanted) {
                    keep[i] = true
                    wanted.remove(dice[i])
                }
            }
            return keep
        }
        // Otherwise keep the face there's most of (bigger faces win ties)
        val face = (6 downTo 1).maxBy { counts[it] * 10 + it }
        for (i in 0..4) keep[i] = dice[i] == face
        return keep
    }

    /** The box Glowfin fills with these dice */
    fun glowfinBox(): DiceBox {
        val open = DiceBox.entries.filter { it !in player.sheet }
        return open.maxBy { box ->
            val s = box.score(dice)
            val worth =
                when {
                    box.upper -> s * 1.6 - (box.ordinal + 1) * 2.0
                    box == DiceBox.CHANCE -> s * 0.55
                    box == DiceBox.FIVE_KIND && s == 0 -> -8.0
                    s == 0 -> -2.0 - box.ordinal * 0.1
                    else -> s.toDouble()
                }
            worth
        }
    }
}
