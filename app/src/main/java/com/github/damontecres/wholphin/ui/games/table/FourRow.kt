package com.github.damontecres.wholphin.ui.games.table

import kotlin.random.Random

/**
 * Four in a Row for the Table tab: 7 columns, 6 rows, discs drop to the lowest free spot, four in a line wins.
 * Cells are row by row from the top: 0 empty, 1 or 2 for the player.
 */
class FourRow private constructor(
    val cells: IntArray,
    var turn: Int,
) {
    constructor() : this(IntArray(COLS * ROWS), 1)

    fun copy(): FourRow = FourRow(cells.copyOf(), turn)

    fun canDrop(col: Int): Boolean = col in 0 until COLS && cells[col] == 0

    /** The row a disc would land in, or -1 */
    fun landing(col: Int): Int {
        if (!canDrop(col)) return -1
        for (r in ROWS - 1 downTo 0) if (cells[r * COLS + col] == 0) return r
        return -1
    }

    /** Drops a disc for whoever's turn it is; returns the cell it landed in, or -1 */
    fun drop(col: Int): Int {
        val r = landing(col)
        if (r < 0) return -1
        cells[r * COLS + col] = turn
        turn = 3 - turn
        return r * COLS + col
    }

    /** The winning four cells, if someone has four in a line */
    fun winLine(): List<Int>? {
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                val p = cells[r * COLS + c]
                if (p == 0) continue
                for ((dr, dc) in LINES) {
                    val line = (0..3).map { (r + dr * it) to (c + dc * it) }
                    if (line.all { (rr, cc) -> rr in 0 until ROWS && cc in 0 until COLS && cells[rr * COLS + cc] == p }) {
                        return line.map { (rr, cc) -> rr * COLS + cc }
                    }
                }
            }
        }
        return null
    }

    /** 0 still going, 1 or 2 the winner, 3 a draw (board full) */
    fun result(): Int {
        winLine()?.let { return cells[it.first()] }
        return if ((0 until COLS).none { canDrop(it) }) 3 else 0
    }

    /** Glowfin's column at a level (1 easy, 2 medium, 3 hard) */
    fun bestColumn(
        level: Int,
        random: Random = Random.Default,
    ): Int {
        val open = (0 until COLS).filter { canDrop(it) }
        if (open.isEmpty()) return -1
        val depth =
            when (level) {
                1 -> 2
                2 -> 4
                else -> 6
            }
        if (level <= 1 && random.nextInt(4) == 0) return open.random(random)
        var best = Int.MIN_VALUE
        val picks = mutableListOf<Int>()
        // Middle columns first, so ties lean to the centre
        for (c in open.sortedBy { kotlin.math.abs(it - 3) }) {
            val next = copy().also { it.drop(c) }
            val v = -next.search(depth - 1, -1_000_000, 1_000_000)
            if (v > best) {
                best = v
                picks.clear()
                picks.add(c)
            } else if (v == best) {
                picks.add(c)
            }
        }
        return if (level <= 1) picks.random(random) else picks.first()
    }

    private fun search(
        depth: Int,
        alphaIn: Int,
        beta: Int,
    ): Int {
        val res = result()
        // The player who just moved won
        if (res == 1 || res == 2) return -100_000 - depth
        if (res == 3) return 0
        if (depth <= 0) return score(turn)
        var alpha = alphaIn
        for (c in ORDER) {
            if (!canDrop(c)) continue
            val next = copy().also { it.drop(c) }
            val v = -next.search(depth - 1, -beta, -alpha)
            if (v >= beta) return v
            if (v > alpha) alpha = v
        }
        return alpha
    }

    private fun score(me: Int): Int {
        var v = 0
        for (r in 0 until ROWS) {
            v += (
                if (cells[r * COLS + 3] == me) {
                    4
                } else if (cells[r * COLS + 3] != 0) {
                    -4
                } else {
                    0
                }
            )
        }
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                for ((dr, dc) in LINES) {
                    var mine = 0
                    var theirs = 0
                    var ok = true
                    for (i in 0..3) {
                        val rr = r + dr * i
                        val cc = c + dc * i
                        if (rr !in 0 until ROWS || cc !in 0 until COLS) {
                            ok = false
                            break
                        }
                        val p = cells[rr * COLS + cc]
                        if (p == me) {
                            mine++
                        } else if (p != 0) {
                            theirs++
                        }
                    }
                    if (!ok || (mine > 0 && theirs > 0)) continue
                    v += WINDOW[mine] - WINDOW[theirs]
                }
            }
        }
        return v
    }

    companion object {
        const val COLS = 7
        const val ROWS = 6
        private val LINES = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        private val ORDER = intArrayOf(3, 2, 4, 1, 5, 0, 6)
        private val WINDOW = intArrayOf(0, 1, 6, 40, 0)
    }
}
