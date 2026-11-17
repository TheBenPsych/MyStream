package com.github.damontecres.wholphin.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Video Games Day on the new navigation (Ben 2026-11-17, sheets themes/videogames-3-navigation.html and
 * videogames-4-rail-icons.html):
 * - Home is an arcade score screen: a score bar along the top (1UP score ticking up, HI-SCORE 091200 for 12 Sep,
 *   three hearts, coins) and PRESS START blinking along the bottom between LEVEL 09·12 and CREDIT 01.
 * - The rail and phone-tab icons are full-colour 8-bit pixel art, each in its door's colour (vi-pixel); nothing
 *   sits beside the names.
 * - A blinking pixel arrow points at the rail icon you're on.
 * - Start-up: each pixel icon flashes white, then settles into its colours (vi-q-start A).
 * Every letter is drawn from the little 5x7 pixel font below, so it looks the same on every TV and phone.
 * The web copy of all this is in server/web/js/holidaynav.js (the videogames entry).
 */
object VideoGamesNav {
    /** The start-up light: just off pure white, so the icons know to flash white rather than show their colours */
    val Flash = Color(0xFFFFFFFE)

    private val Pink = Color(0xFFFF5FB0)
    private val Gold = Color(0xFFFFD45A)
    private val Grey = Color(0xFFAAB0C4)
    private val Bar = Color(0xE00A0618)

    // ---------- icons: 12 x 12 pixel drawings, one letter per colour ----------

    private val PIX: Map<String, List<String>> =
        mapOf(
            HolidayIconKey.HOME to
                listOf(
                    "....RR......",
                    "...RRRR.CC..",
                    "..RRRRRR.C..",
                    ".RRRRRRRR...",
                    "RRRRRRRRRR..",
                    ".WWWWWWWW...",
                    ".WBBWWWDDW..",
                    ".WBBWWWDDW..",
                    ".WWWWWWDDW..",
                    ".WWWWWWDDW..",
                    "GGGGGGGGGGGG",
                    "............",
                ),
            "movies" to
                listOf(
                    "............",
                    ".WKWKWKWKWK.",
                    ".KWKWKWKWKW.",
                    "............",
                    ".AAAAAAAAAA.",
                    ".AKKKKKKKKA.",
                    ".AKAAAAAAKA.",
                    ".AKAAAAAAKA.",
                    ".AKKKKKKKKA.",
                    ".AAAAAAAAAA.",
                    ".AA......AA.",
                    "............",
                ),
            "shows" to
                listOf(
                    "..K......K..",
                    "...K....K...",
                    "....K..K....",
                    ".SSSSSSSSSS.",
                    ".SCCCCCCCSS.",
                    ".SCWCCCCCSY.",
                    ".SCCCCCCCSS.",
                    ".SCCCCCCCSY.",
                    ".SCCCCCCCSS.",
                    ".SSSSSSSSSS.",
                    "..SS....SS..",
                    "............",
                ),
            "live" to
                listOf(
                    "............",
                    "T....RR....T",
                    ".T...RR...T.",
                    "T.T..TT..T.T",
                    ".T...TT...T.",
                    "T...TTTT...T",
                    "....T..T....",
                    "....TTTT....",
                    "...T....T...",
                    "...TTTTTT...",
                    "..T......T..",
                    ".TT......TT.",
                ),
            "games" to
                listOf(
                    "............",
                    "............",
                    "..PPP..PPP..",
                    ".PPPPPPPPPP.",
                    "PPWPPPPPYPPP",
                    "PWWWPPPBPRPP",
                    "PPWPPPPPLPPP",
                    "PPPPPPPPPPPP",
                    "PPPP....PPPP",
                    ".PP......PP.",
                    "............",
                    "............",
                ),
            "music" to
                listOf(
                    "............",
                    "....MMMMMMMM",
                    "....MMMMMMMM",
                    "....M......M",
                    "....M......M",
                    "....M......M",
                    "....M......M",
                    "..MMM....MMM",
                    ".MMMM...MMMM",
                    ".MMMM...MMMM",
                    "..MM.....MM.",
                    "............",
                ),
            "books" to
                listOf(
                    "............",
                    ".LLLL..LLLL.",
                    "LWWWWLLWWWWL",
                    "LWKKWLLWKKWL",
                    "LWWWWLLWWWWL",
                    "LWKKWLLWKKWL",
                    "LWWWWLLWWWWL",
                    "LWKKWLLWKKWL",
                    "LWWWWLLWWWWL",
                    "LLLLLLLLLLLL",
                    ".....LL.....",
                    "............",
                ),
            HolidayIconKey.SEARCH to
                listOf(
                    "............",
                    "...WWWW.....",
                    "..W....W....",
                    ".W......W...",
                    ".W......W...",
                    ".W......W...",
                    ".W......W...",
                    "..W....W....",
                    "...WWWWWW...",
                    "........WW..",
                    ".........WW.",
                    "..........W.",
                ),
            HolidayIconKey.REQUESTS to
                listOf(
                    "............",
                    ".....YY.....",
                    ".....YY.....",
                    "..Y..YY..Y..",
                    "...Y.YY.Y...",
                    "YYYY.YY.YYYY",
                    "...Y.YY.Y...",
                    "..Y..YY..Y..",
                    ".....YY.....",
                    ".....YY.....",
                    "............",
                    "............",
                ),
            HolidayIconKey.MORE to
                listOf(
                    "............",
                    "............",
                    "............",
                    "............",
                    "............",
                    ".WW..WW..WW.",
                    ".WW..WW..WW.",
                    "............",
                    "............",
                    "............",
                    "............",
                    "............",
                ),
        )

    private val INK: Map<Char, Color> =
        mapOf(
            'R' to Color(0xFFFF5F6D),
            'W' to Color(0xFFF4F1FF),
            'B' to Color(0xFF3FE0FF),
            'D' to Color(0xFF9A5B2E),
            'C' to Color(0xFFB8B8C8),
            'G' to Color(0xFF5BD86A),
            'K' to Color(0xFF22183C),
            'A' to Color(0xFFFFB547),
            'S' to Color(0xFF7AA7FF),
            'Y' to Color(0xFFFFD45A),
            'T' to Color(0xFF3DD6C6),
            'M' to Color(0xFFB69CFF),
            'L' to Color(0xFFB9E36A),
            'P' to Color(0xFFFF6F91),
        )

    /** One pixel icon, filling its box. [color] is what the usual icon would be drawn in. */
    private fun icon(key: String): HolidayIconArt {
        val rows = PIX.getValue(key)
        return { color, _ ->
            // Grey before it lights up, white as it flashes at start-up; its own colours the rest of the time
            val one = color.alpha < .9f || color == Flash || sameRgb(color, BenflixColors.Muted)
            val cell = size.minDimension / 12f
            val x0 = (size.width - cell * 12f) / 2f
            val y0 = (size.height - cell * 12f) / 2f
            rows.forEachIndexed { y, row ->
                row.forEachIndexed { x, ch ->
                    if (ch == '.') return@forEachIndexed
                    val fill =
                        when {
                            !one -> INK[ch] ?: Color.White

                            // the dark lines (clapper stripes, book text) stay dark so the shape still reads
                            ch == 'K' -> INK.getValue('K')

                            else -> if (color == Flash) Color.White else color
                        }
                    // a hair over one cell so neighbouring pixels never show a seam
                    drawRect(fill, Offset(x0 + x * cell, y0 + y * cell), Size(cell + .6f, cell + .6f))
                }
            }
        }
    }

    private fun sameRgb(
        a: Color,
        b: Color,
    ) = kotlin.math.abs(a.red - b.red) < .02f && kotlin.math.abs(a.green - b.green) < .02f && kotlin.math.abs(a.blue - b.blue) < .02f

    val icons: Map<String, HolidayIconArt> = PIX.keys.associateWith { icon(it) }

    // ---------- the blinking arrow beside the icon you're on ----------

    private val ARROW = listOf("X.....", "XX....", "XXX...", "XXXX..", "XXXX..", "XXX...", "XX....", "X.....")

    /** Drawn in the 12 x 18 dp box at the icon's top-left: a cyan ▶ pointing at the icon, blinking like a game menu */
    val mark: HolidayArt = { seconds ->
        val on = (seconds % 1f) < .7f
        val cell = 1.25f * density
        val left = .5f * density
        val top = 16f * density
        for (pass in 0..1) {
            val c = if (pass == 0) VgLook.OnCyan else VgLook.Cyan.copy(alpha = if (on) 1f else .3f)
            val d = if (pass == 0) cell * .6f else 0f
            ARROW.forEachIndexed { y, row ->
                row.forEachIndexed { x, ch ->
                    if (ch == 'X') drawRect(c, Offset(left + x * cell + d, top + y * cell + d), Size(cell + .5f, cell + .5f))
                }
            }
        }
    }

    // ---------- the score screen trims ----------

    private val FONT: Map<Char, List<String>> =
        mapOf(
            '0' to listOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
            '1' to listOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
            '2' to listOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
            '3' to listOf("#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###."),
            '4' to listOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
            '5' to listOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
            '6' to listOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
            '7' to listOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
            '8' to listOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
            '9' to listOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
            'A' to listOf(".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
            'C' to listOf(".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."),
            'D' to listOf("####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."),
            'E' to listOf("#####", "#....", "#....", "####.", "#....", "#....", "#####"),
            'H' to listOf("#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
            'I' to listOf(".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###."),
            'L' to listOf("#....", "#....", "#....", "#....", "#....", "#....", "#####"),
            'O' to listOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
            'P' to listOf("####.", "#...#", "#...#", "####.", "#....", "#....", "#...."),
            'R' to listOf("####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"),
            'S' to listOf(".####", "#....", "#....", ".###.", "....#", "....#", "####."),
            'T' to listOf("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."),
            'U' to listOf("#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
            'V' to listOf("#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."),
            'x' to listOf(".....", ".....", "#...#", ".#.#.", "..#..", ".#.#.", "#...#"),
            '-' to listOf(".....", ".....", ".....", "#####", ".....", ".....", "....."),
            '.' to listOf(".....", ".....", ".....", "..#..", ".....", ".....", "....."),
        )
    private val HEART = listOf(".##.##.", "#######", "#######", ".#####.", "..###..", "...#...")
    private val COIN = listOf("..###..", ".#####.", "##.####", "##.####", "##.####", ".#####.", "..###..")

    /** Width of [text] in cells (6 per letter, the last without its gap) */
    private fun cells(text: String) = text.length * 6 - 1

    private fun DrawScope.pixels(
        rows: List<String>,
        x: Float,
        y: Float,
        cell: Float,
        color: Color,
    ) {
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch ->
                if (ch == '#') drawRect(color, Offset(x + c * cell, y + r * cell), Size(cell + .4f, cell + .4f))
            }
        }
    }

    private fun DrawScope.text(
        text: String,
        x: Float,
        y: Float,
        cell: Float,
        color: Color,
    ): Float {
        var at = x
        text.forEach { ch ->
            FONT[ch]?.let { pixels(it, at + cell * .5f, y + cell * .5f, cell, Color.Black.copy(alpha = .55f * color.alpha)) }
            FONT[ch]?.let { pixels(it, at, y, cell, color) }
            at += 6 * cell
        }
        return at
    }

    /** Phones keep the greeting at the top of the picture, so there the score moves down to the bottom trim */
    private fun DrawScope.narrow() = size.width < 600f * density

    private fun scoreAt(seconds: Float) = (12900 + (seconds * 125f).toInt() / 50 * 50) % 1_000_000

    /**
     * Top of Home: a slim score bar along the very top edge, above the greeting and clock's line (it stops 18 dp
     * down, where that line starts, so it never crowds them). Phones get just a dashed cyan edge.
     */
    val topTrim: HolidayArt = { seconds ->
        if (narrow()) {
            drawRect(Bar, Offset.Zero, Size(size.width, 4f * density))
            drawLine(
                VgLook.Cyan.copy(alpha = .7f),
                Offset(0f, 4f * density),
                Offset(size.width, 4f * density),
                strokeWidth = 2f * density,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f * density, 5f * density)),
            )
        } else {
            val band = (18f * density).coerceAtMost(size.height * .45f)
            drawRect(Bar, Offset.Zero, Size(size.width, band))
            drawRect(VgLook.Cyan.copy(alpha = .6f), Offset(0f, band - 2f * density), Size(size.width, 2f * density))
            val cell = (band * .62f / 7f).coerceAtMost(1.8f * density)
            val y = (band - 2f * density - cell * 7f) / 2f
            val pad = 18f * density
            // left: 1UP and the score, ticking up
            val after1 = text("1UP", pad, y, cell, Pink)
            text(scoreAt(seconds).toString().padStart(6, '0'), after1 + cell * 4, y, cell, Color.White)
            // right: coins and three hearts
            val coinsW = (7 + 3 + cells("x12")) * cell
            val heartsW = (3 * 8 - 1) * cell
            var x = size.width - pad - coinsW
            pixels(COIN, x, y, cell, Gold)
            text("x12", x + 10 * cell, y, cell, Color.White)
            x -= heartsW + 8 * cell
            for (i in 0 until 3) pixels(HEART, x + i * 8 * cell, y + cell * .5f, cell, Pink)
            // middle: HI-SCORE 091200 (12 Sep), when there's room
            val mid = (cells("HI-SCORE") + 4 + cells("091200")) * cell
            val leftEnd = after1 + cell * (4 + cells("000000"))
            if (size.width / 2 - mid / 2 > leftEnd + 24 * density && size.width / 2 + mid / 2 < x - 24 * density) {
                val a = text("HI-SCORE", size.width / 2 - mid / 2, y, cell, Gold)
                text("091200", a + cell * 4, y, cell, Color.White)
            }
        }
    }

    /**
     * Bottom of Home: PRESS START, blinking, between LEVEL 09·12 and CREDIT 01, in a 20 dp strip along the very
     * bottom so the floating Today cards never cover it. Phones: 1UP and the score on the left, PRESS START right.
     */
    val bottomTrim: HolidayArt = { seconds ->
        val h = (20f * density).coerceAtMost(size.height)
        val top = size.height - h
        drawRect(Bar, Offset(0f, top), Size(size.width, h))
        drawLine(
            VgLook.Cyan.copy(alpha = .65f),
            Offset(0f, top),
            Offset(size.width, top),
            strokeWidth = 2f * density,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f * density, 5f * density)),
        )
        val cell = (h * .6f / 7f).coerceAtMost(1.7f * density)
        val y = top + (h - cell * 7f) / 2f + density
        val pad = 16f * density
        val press = cells("PRESS START") * cell
        val blink = (seconds % 1f) < .62f
        if (narrow()) {
            val after1 = text("1UP", pad, y, cell, Pink)
            text(scoreAt(seconds).toString().padStart(6, '0'), after1 + cell * 4, y, cell, Color.White)
            if (blink) text("PRESS START", size.width - pad - press, y, cell, Gold)
        } else {
            if (blink) text("PRESS START", size.width / 2 - press / 2, y, cell, Gold)
            text("LEVEL 09.12", pad, y, cell, Grey)
            text("CREDIT 01", size.width - pad - cells("CREDIT 01") * cell, y, cell, Grey)
        }
    }

    /** The first Today card, on the day only (vq-count A) */
    fun countdown(): String? = if (VgLook.on || HolidayNavKit.preview == "videogames") "Today is Video Games Day · Player 1 ready" else null
}
