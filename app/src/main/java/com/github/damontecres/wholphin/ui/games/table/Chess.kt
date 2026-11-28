package com.github.damontecres.wholphin.ui.games.table

import kotlin.math.abs
import kotlin.random.Random

/**
 * Chess for the Table tab: full rules (castling, en passant, promotion, check, checkmate, stalemate, 50 moves,
 * three times the same position, too few pieces to win) and Glowfin as the other player at 3 levels.
 *
 * Squares are 0..63 row by row from the top: row 0 is black's back row. White pieces are positive and black ones
 * negative: 1 pawn, 2 knight, 3 bishop, 4 rook, 5 queen, 6 king.
 * A move is one Int: from | to shl 6 | promotion piece shl 12 | flag shl 16 (1 en passant, 2 castle, 4 two-step pawn).
 */
class Chess private constructor(
    val board: IntArray,
    /** 1 = white to move, -1 = black */
    var side: Int,
    /** 1 white short, 2 white long, 4 black short, 8 black long */
    private var castle: Int,
    /** The square a pawn could take en passant on, or -1 */
    private var ep: Int,
    /** Half moves since the last capture or pawn move */
    private var half: Int,
    private val seen: MutableMap<String, Int>,
) {
    constructor() : this(start(), 1, 15, -1, 0, mutableMapOf()) {
        seen[key()] = 1
    }

    private class Undo(
        val move: Int,
        val moved: Int,
        val captured: Int,
        val castle: Int,
        val ep: Int,
        val half: Int,
    )

    private val undos = ArrayList<Undo>()

    /** The moves played so far, for "Last move" and taking a move back */
    val played = ArrayList<Int>()

    fun copy(): Chess = Chess(board.copyOf(), side, castle, ep, half, seen.toMutableMap())

    // ---------- moves ----------

    /** Every legal move for the side to move */
    fun legalMoves(): List<Int> {
        val out = ArrayList<Int>(48)
        pseudo(out)
        val me = side
        return out.filter { m ->
            make(m)
            val ok = !attacked(kingOf(me), -me)
            unmake()
            ok
        }
    }

    fun movesFrom(square: Int): List<Int> = legalMoves().filter { fromSq(it) == square }

    private fun pseudo(out: MutableList<Int>) {
        val s = side
        for (sq in 0 until 64) {
            val p = board[sq]
            if (p * s <= 0) continue
            val r = sq / 8
            val c = sq % 8
            when (abs(p)) {
                1 -> {
                    pawn(sq, r, c, s, out)
                }

                2 -> {
                    for ((dr, dc) in KNIGHT) add(sq, r + dr, c + dc, s, out)
                }

                3 -> {
                    slide(sq, r, c, s, DIAG, out)
                }

                4 -> {
                    slide(sq, r, c, s, STRAIGHT, out)
                }

                5 -> {
                    slide(sq, r, c, s, DIAG, out)
                    slide(sq, r, c, s, STRAIGHT, out)
                }

                6 -> {
                    for ((dr, dc) in KING) add(sq, r + dr, c + dc, s, out)
                    castles(sq, s, out)
                }
            }
        }
    }

    private fun add(
        from: Int,
        r: Int,
        c: Int,
        s: Int,
        out: MutableList<Int>,
    ) {
        if (r !in 0..7 || c !in 0..7) return
        val to = r * 8 + c
        if (board[to] * s > 0) return
        out.add(from or (to shl 6))
    }

    private fun slide(
        from: Int,
        r0: Int,
        c0: Int,
        s: Int,
        dirs: Array<IntArray>,
        out: MutableList<Int>,
    ) {
        for (d in dirs) {
            var r = r0 + d[0]
            var c = c0 + d[1]
            while (r in 0..7 && c in 0..7) {
                val to = r * 8 + c
                val t = board[to]
                if (t * s > 0) break
                out.add(from or (to shl 6))
                if (t != 0) break
                r += d[0]
                c += d[1]
            }
        }
    }

    private fun pawn(
        sq: Int,
        r: Int,
        c: Int,
        s: Int,
        out: MutableList<Int>,
    ) {
        val dir = if (s == 1) -1 else 1
        val startRow = if (s == 1) 6 else 1
        val lastRow = if (s == 1) 0 else 7
        val one = r + dir
        if (one !in 0..7) return

        fun push(
            to: Int,
            flag: Int = 0,
        ) {
            if (to / 8 == lastRow) {
                for (pr in intArrayOf(5, 2, 4, 3)) out.add(sq or (to shl 6) or (pr shl 12) or (flag shl 16))
            } else {
                out.add(sq or (to shl 6) or (flag shl 16))
            }
        }
        if (board[one * 8 + c] == 0) {
            push(one * 8 + c)
            val two = r + 2 * dir
            if (r == startRow && board[two * 8 + c] == 0) out.add(sq or ((two * 8 + c) shl 6) or (4 shl 16))
        }
        for (dc in intArrayOf(-1, 1)) {
            val cc = c + dc
            if (cc !in 0..7) continue
            val to = one * 8 + cc
            if (board[to] * s < 0) push(to)
            if (to == ep) out.add(sq or (to shl 6) or (1 shl 16))
        }
    }

    private fun castles(
        sq: Int,
        s: Int,
        out: MutableList<Int>,
    ) {
        val row = if (s == 1) 7 else 0
        if (sq != row * 8 + 4 || attacked(sq, -s)) return
        val shortBit = if (s == 1) 1 else 4
        val longBit = if (s == 1) 2 else 8
        val b = row * 8
        if (castle and shortBit != 0 && board[b + 5] == 0 && board[b + 6] == 0 && board[b + 7] == 4 * s &&
            !attacked(b + 5, -s) && !attacked(b + 6, -s)
        ) {
            out.add(sq or ((b + 6) shl 6) or (2 shl 16))
        }
        if (castle and longBit != 0 && board[b + 3] == 0 && board[b + 2] == 0 && board[b + 1] == 0 && board[b] == 4 * s &&
            !attacked(b + 3, -s) && !attacked(b + 2, -s)
        ) {
            out.add(sq or ((b + 2) shl 6) or (2 shl 16))
        }
    }

    /** Is [sq] attacked by side [by]? */
    fun attacked(
        sq: Int,
        by: Int,
    ): Boolean {
        if (sq < 0) return false
        val r = sq / 8
        val c = sq % 8
        // Pawns: a white pawn attacks upwards, so it sits one row below
        val pr = if (by == 1) r + 1 else r - 1
        if (pr in 0..7) {
            if (c > 0 && board[pr * 8 + c - 1] == by) return true
            if (c < 7 && board[pr * 8 + c + 1] == by) return true
        }
        for ((dr, dc) in KNIGHT) {
            val rr = r + dr
            val cc = c + dc
            if (rr in 0..7 && cc in 0..7 && board[rr * 8 + cc] == 2 * by) return true
        }
        for ((dr, dc) in KING) {
            val rr = r + dr
            val cc = c + dc
            if (rr in 0..7 && cc in 0..7 && board[rr * 8 + cc] == 6 * by) return true
        }
        for (d in DIAG) if (rayHits(r, c, d, by, 3)) return true
        for (d in STRAIGHT) if (rayHits(r, c, d, by, 4)) return true
        return false
    }

    private fun rayHits(
        r0: Int,
        c0: Int,
        d: IntArray,
        by: Int,
        kind: Int,
    ): Boolean {
        var r = r0 + d[0]
        var c = c0 + d[1]
        while (r in 0..7 && c in 0..7) {
            val p = board[r * 8 + c]
            if (p != 0) return p == kind * by || p == 5 * by
            r += d[0]
            c += d[1]
        }
        return false
    }

    fun kingOf(s: Int): Int = board.indexOfFirst { it == 6 * s }

    fun inCheck(): Boolean = attacked(kingOf(side), -side)

    private fun make(m: Int) {
        val f = fromSq(m)
        val t = toSq(m)
        val flag = m ushr 16
        val moved = board[f]
        var captured = board[t]
        undos.add(Undo(m, moved, captured, castle, ep, half))
        board[f] = 0
        if (flag and 1 != 0) {
            val capSq = f / 8 * 8 + t % 8
            captured = board[capSq]
            board[capSq] = 0
            undos[undos.size - 1] = Undo(m, moved, captured, castle, ep, half)
        }
        val promo = (m ushr 12) and 15
        board[t] = if (promo != 0) promo * side else moved
        if (flag and 2 != 0) {
            val row = t / 8
            if (t % 8 == 6) {
                board[row * 8 + 5] = board[row * 8 + 7]
                board[row * 8 + 7] = 0
            } else {
                board[row * 8 + 3] = board[row * 8]
                board[row * 8] = 0
            }
        }
        // Castling rights go when the king or a rook moves, or a rook is taken
        for (sq in intArrayOf(f, t)) {
            castle =
                castle and
                when (sq) {
                    60 -> 3.inv()
                    63 -> 1.inv()
                    56 -> 2.inv()
                    4 -> 12.inv()
                    7 -> 4.inv()
                    0 -> 8.inv()
                    else -> 15
                }
        }
        ep = if (flag and 4 != 0) (f + t) / 2 else -1
        half = if (abs(moved) == 1 || captured != 0) 0 else half + 1
        side = -side
    }

    private fun unmake() {
        val u = undos.removeAt(undos.size - 1)
        val m = u.move
        val f = fromSq(m)
        val t = toSq(m)
        val flag = m ushr 16
        side = -side
        board[f] = u.moved
        if (flag and 1 != 0) {
            board[t] = 0
            board[f / 8 * 8 + t % 8] = u.captured
        } else {
            board[t] = u.captured
        }
        if (flag and 2 != 0) {
            val row = t / 8
            if (t % 8 == 6) {
                board[row * 8 + 7] = board[row * 8 + 5]
                board[row * 8 + 5] = 0
            } else {
                board[row * 8] = board[row * 8 + 3]
                board[row * 8 + 3] = 0
            }
        }
        castle = u.castle
        ep = u.ep
        half = u.half
    }

    /** Plays a legal move for real (kept in the game's history) */
    fun play(m: Int) {
        make(m)
        played.add(m)
        val k = key()
        seen[k] = (seen[k] ?: 0) + 1
    }

    /** Takes the last real move back */
    fun takeBack(): Boolean {
        if (played.isEmpty() || undos.isEmpty()) return false
        val k = key()
        seen[k] = (seen[k] ?: 1) - 1
        unmake()
        played.removeAt(played.size - 1)
        return true
    }

    private fun key(): String = board.joinToString(",") + "|$side|$castle|$ep"

    /** How the game stands for the side to move */
    enum class State { PLAYING, CHECKMATE, STALEMATE, FIFTY, REPEAT, MATERIAL }

    fun state(): State {
        val moves = legalMoves()
        return when {
            moves.isEmpty() && inCheck() -> State.CHECKMATE
            moves.isEmpty() -> State.STALEMATE
            half >= 100 -> State.FIFTY
            (seen[key()] ?: 0) >= 3 -> State.REPEAT
            tooFew() -> State.MATERIAL
            else -> State.PLAYING
        }
    }

    private fun tooFew(): Boolean {
        val left = board.filter { it != 0 && abs(it) != 6 }
        return left.isEmpty() || (left.size == 1 && abs(left[0]) in 2..3)
    }

    // ---------- Glowfin's move ----------

    private var nodes = 0

    /** Glowfin's move at a level (1 easy, 2 medium, 3 hard) */
    fun bestMove(
        level: Int,
        random: Random = Random.Default,
    ): Int? {
        val all = legalMoves()
        if (all.isEmpty()) return null
        if (all.size == 1) return all.first()
        val depth =
            when (level) {
                1 -> 1
                2 -> 2
                else -> 3
            }
        nodes = 0
        val scored =
            all.shuffled(random).map { m ->
                make(m)
                val v = -search(depth - 1, -1_000_000, 1_000_000)
                unmake()
                m to v
            }
        val top = scored.maxOf { it.second }
        // Easy makes small mistakes: any move within a pawn of the best
        val slack = if (level <= 1) 110 else 0
        return scored.filter { it.second >= top - slack }.random(random).first
    }

    private fun search(
        depth: Int,
        alphaIn: Int,
        beta: Int,
    ): Int {
        nodes++
        if (depth <= 0 || nodes > NODE_CAP) return quiet(alphaIn, beta, 4)
        val list = ArrayList<Int>(48)
        pseudo(list)
        list.sortByDescending { abs(board[toSq(it)]) * 10 - abs(board[fromSq(it)]) }
        var alpha = alphaIn
        var any = false
        val me = side
        for (m in list) {
            make(m)
            if (attacked(kingOf(me), -me)) {
                unmake()
                continue
            }
            any = true
            val v = -search(depth - 1, -beta, -alpha)
            unmake()
            if (v >= beta) return v
            if (v > alpha) alpha = v
        }
        if (!any) return if (attacked(kingOf(me), -me)) -90_000 - depth else 0
        return alpha
    }

    /** Looks at captures only, so Glowfin doesn't stop thinking half way through a swap */
    private fun quiet(
        alphaIn: Int,
        beta: Int,
        left: Int,
    ): Int {
        nodes++
        val stand = eval()
        if (stand >= beta || left <= 0) return stand
        var alpha = maxOf(alphaIn, stand)
        val list = ArrayList<Int>(16)
        pseudo(list)
        val caps = list.filter { board[toSq(it)] != 0 }.sortedByDescending { abs(board[toSq(it)]) * 10 - abs(board[fromSq(it)]) }
        val me = side
        for (m in caps) {
            make(m)
            if (attacked(kingOf(me), -me)) {
                unmake()
                continue
            }
            val v = -quiet(-beta, -alpha, left - 1)
            unmake()
            if (v >= beta) return v
            if (v > alpha) alpha = v
        }
        return alpha
    }

    /** Material plus a little for good squares, from the side to move's view */
    private fun eval(): Int {
        var v = 0
        for (sq in 0 until 64) {
            val p = board[sq]
            if (p == 0) continue
            val r = sq / 8
            val c = sq % 8
            val centre = 3 - minOf(abs(2 * r - 7), abs(2 * c - 7)) / 2
            val bonus =
                when (abs(p)) {
                    1 -> (if (p > 0) 6 - r else r - 1) * 6 + (if (c in 2..5) 4 else 0)
                    2, 3 -> centre * 6
                    5 -> centre * 2
                    else -> 0
                }
            val worth = VALUE[abs(p)] + bonus
            v += if (p > 0) worth else -worth
        }
        return v * side
    }

    companion object {
        private const val NODE_CAP = 60_000
        private val VALUE = intArrayOf(0, 100, 320, 330, 500, 900, 0)
        private val KNIGHT = listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
        private val KING = listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)
        private val DIAG = arrayOf(intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(1, 1))
        private val STRAIGHT = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))

        fun fromSq(m: Int): Int = m and 63

        fun toSq(m: Int): Int = (m ushr 6) and 63

        fun promotion(m: Int): Int = (m ushr 12) and 15

        /** A position from FEN text (used by the tests) */
        fun fromFen(fen: String): Chess {
            val parts = fen.trim().split(' ')
            val b = IntArray(64)
            var sq = 0
            for (ch in parts[0]) {
                when {
                    ch == '/' -> {}

                    ch.isDigit() -> {
                        sq += ch - '0'
                    }

                    else -> {
                        val kind = "pnbrqk".indexOf(ch.lowercaseChar()) + 1
                        b[sq++] = if (ch.isUpperCase()) kind else -kind
                    }
                }
            }
            val side = if (parts.getOrNull(1) == "b") -1 else 1
            val rights = parts.getOrNull(2).orEmpty()
            var castle = 0
            if ('K' in rights) castle = castle or 1
            if ('Q' in rights) castle = castle or 2
            if ('k' in rights) castle = castle or 4
            if ('q' in rights) castle = castle or 8
            val epText = parts.getOrNull(3).orEmpty()
            val ep = if (epText.length == 2) (8 - (epText[1] - '0')) * 8 + (epText[0] - 'a') else -1
            return Chess(b, side, castle, ep, 0, mutableMapOf())
        }

        /** "e2" style name of a square */
        fun name(sq: Int): String = "${'a' + sq % 8}${8 - sq / 8}"

        private fun start(): IntArray {
            val back = intArrayOf(4, 2, 3, 5, 6, 3, 2, 4)
            return IntArray(64) { sq ->
                val r = sq / 8
                val c = sq % 8
                when (r) {
                    0 -> -back[c]
                    1 -> -1
                    6 -> 1
                    7 -> back[c]
                    else -> 0
                }
            }
        }
    }
}
