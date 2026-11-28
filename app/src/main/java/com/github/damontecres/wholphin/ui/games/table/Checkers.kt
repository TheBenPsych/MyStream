package com.github.damontecres.wholphin.ui.games.table

import kotlin.random.Random

/**
 * Checkers (American rules) for the Table tab: 8 x 8, pieces on the dark squares, jumps are a must and keep going
 * while they can, a piece that reaches the far row becomes a king (and its turn ends there).
 *
 * Squares are 0..63, row by row from the top. Player 1 (aqua) starts at the bottom and moves first, player 2 (red)
 * starts at the top. Board values: 1 = player 1 piece, 2 = player 1 king, -1 / -2 = the same for player 2.
 */
class Checkers private constructor(
    val board: IntArray,
    /** 1 or 2 */
    var turn: Int,
    /** Moves in a row with no jump, so a game of two lone kings ends as a draw */
    var quiet: Int,
) {
    constructor() : this(start(), 1, 0)

    /** One move: the squares it passes through (start first) and the pieces it jumps */
    data class Move(
        val path: List<Int>,
        val jumped: List<Int>,
    ) {
        val from: Int get() = path.first()
        val to: Int get() = path.last()
    }

    fun copy(): Checkers = Checkers(board.copyOf(), turn, quiet)

    private fun sign(player: Int) = if (player == 1) 1 else -1

    fun owner(square: Int): Int =
        when {
            board[square] > 0 -> 1
            board[square] < 0 -> 2
            else -> 0
        }

    fun isKing(square: Int): Boolean = kotlin.math.abs(board[square]) == 2

    /** Every legal move for whoever's turn it is (only jumps when any jump exists) */
    fun moves(player: Int = turn): List<Move> {
        val s = sign(player)
        val jumps = mutableListOf<Move>()
        val steps = mutableListOf<Move>()
        for (sq in 0 until 64) {
            if (board[sq] * s <= 0) continue
            jumpsFrom(sq, board[sq], board.copyOf(), listOf(sq), emptyList(), jumps)
            for ((dr, dc) in dirs(board[sq])) {
                val r = sq / 8 + dr
                val c = sq % 8 + dc
                if (r in 0..7 && c in 0..7 && board[r * 8 + c] == 0) steps.add(Move(listOf(sq, r * 8 + c), emptyList()))
            }
        }
        return jumps.ifEmpty { steps }
    }

    private fun dirs(piece: Int): List<Pair<Int, Int>> =
        when (piece) {
            1 -> listOf(-1 to -1, -1 to 1)
            -1 -> listOf(1 to -1, 1 to 1)
            else -> listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        }

    private fun jumpsFrom(
        sq: Int,
        piece: Int,
        b: IntArray,
        path: List<Int>,
        jumped: List<Int>,
        out: MutableList<Move>,
    ) {
        var any = false
        for ((dr, dc) in dirs(piece)) {
            val r = sq / 8
            val c = sq % 8
            val mr = r + dr
            val mc = c + dc
            val lr = r + 2 * dr
            val lc = c + 2 * dc
            if (lr !in 0..7 || lc !in 0..7) continue
            val mid = mr * 8 + mc
            val land = lr * 8 + lc
            if (b[mid] * piece >= 0 || b[land] != 0) continue
            any = true
            val nb = b.copyOf()
            nb[sq] = 0
            nb[mid] = 0
            val crowned = (piece == 1 && lr == 0) || (piece == -1 && lr == 7)
            nb[land] = if (crowned) piece * 2 else piece
            if (crowned) {
                out.add(Move(path + land, jumped + mid))
            } else {
                jumpsFrom(land, piece, nb, path + land, jumped + mid, out)
            }
        }
        if (!any && jumped.isNotEmpty()) out.add(Move(path, jumped))
    }

    fun play(m: Move) {
        val piece = board[m.from]
        board[m.from] = 0
        m.jumped.forEach { board[it] = 0 }
        val row = m.to / 8
        board[m.to] = if ((piece == 1 && row == 0) || (piece == -1 && row == 7)) piece * 2 else piece
        quiet = if (m.jumped.isEmpty()) quiet + 1 else 0
        turn = 3 - turn
    }

    /** 0 = still going, 1 or 2 = that player won, 3 = draw */
    fun result(): Int =
        when {
            moves(turn).isEmpty() -> 3 - turn
            quiet >= 80 -> 3
            else -> 0
        }

    fun count(player: Int): Int = board.count { it * sign(player) > 0 }

    private fun score(player: Int): Int {
        var v = 0
        for (sq in 0 until 64) {
            val p = board[sq]
            if (p == 0) continue
            val row = sq / 8
            val worth =
                when (p) {
                    1 -> 100 + (7 - row) * 3
                    -1 -> 100 + row * 3
                    else -> 165
                }
            v += if (p > 0) worth else -worth
        }
        return if (player == 1) v else -v
    }

    /** Glowfin's move at a level (1 easy, 2 medium, 3 hard) */
    fun bestMove(
        level: Int,
        random: Random = Random.Default,
    ): Move? {
        val all = moves()
        if (all.isEmpty()) return null
        if (all.size == 1) return all.first()
        if (level <= 1 && random.nextInt(3) == 0) return all.random(random)
        val depth =
            when (level) {
                1 -> 2
                2 -> 4
                else -> 6
            }
        var best = Int.MIN_VALUE
        val picks = mutableListOf<Move>()
        for (m in all.shuffled(random)) {
            val next = copy().also { it.play(m) }
            val v = -next.search(depth - 1, -1_000_000, 1_000_000)
            if (v > best) {
                best = v
                picks.clear()
                picks.add(m)
            } else if (v == best) {
                picks.add(m)
            }
        }
        return picks.firstOrNull() ?: all.first()
    }

    private fun search(
        depth: Int,
        alphaIn: Int,
        beta: Int,
    ): Int {
        val all = moves()
        if (all.isEmpty()) return -100_000 - depth
        if (depth <= 0) return score(turn)
        var alpha = alphaIn
        for (m in all) {
            val next = copy().also { it.play(m) }
            val v = -next.search(depth - 1, -beta, -alpha)
            if (v >= beta) return v
            if (v > alpha) alpha = v
        }
        return alpha
    }

    companion object {
        fun dark(square: Int): Boolean = (square / 8 + square % 8) % 2 == 1

        private fun start(): IntArray =
            IntArray(64) { sq ->
                when {
                    !dark(sq) -> 0
                    sq / 8 <= 2 -> -1
                    sq / 8 >= 5 -> 1
                    else -> 0
                }
            }
    }
}
