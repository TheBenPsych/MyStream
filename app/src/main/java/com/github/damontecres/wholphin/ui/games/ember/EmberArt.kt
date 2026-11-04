package com.github.damontecres.wholphin.ui.games.ember

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Ember Road's pixel art, all drawn with code (no picture files).
 *
 * Everything is drawn onto a tiny 160 x 90 "screen" of big pixels, which the game then blows up
 * to fill the TV or phone, keeping the pixels sharp (16-bit console style).
 * No Android code in here, so the art can be previewed on a computer.
 */

/** Where the art is drawn: one filled rectangle at a time, in art pixels */
interface PixelSink {
    fun rect(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        argb: Int,
    )
}

/** Things that change how a scene looks */
data class SceneOpts(
    val weather: String = "clear",
    /** Show the cart and party walking (travel) */
    val cart: Boolean = false,
    val party: Int = 4,
    val pet: Boolean = true,
    val cartLook: String = "wagon",
    /** A creature to draw in the middle of the scene */
    val creature: String? = null,
    val egg: Boolean = true,
    val hatched: Boolean = false,
    /** Seasons tint the grass */
    val season: String = "spring",
    /** Old TV scanlines */
    val crt: Boolean = false,
)

object EmberArt {
    const val W = 160
    const val H = 90

    // -------------------------------------------------------------------------------------------
    // Little helpers

    private fun c(rgb: Int) = (0xFF shl 24) or rgb

    /** Mixes two colours, t = 0 (a) to 1 (b) */
    fun mix(
        a: Int,
        b: Int,
        t: Float,
    ): Int {
        val tt = t.coerceIn(0f, 1f)

        fun ch(s: Int) = (((a shr s) and 0xFF) * (1 - tt) + ((b shr s) and 0xFF) * tt).toInt() and 0xFF
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** A steady "random" number from a few ints, so trees stay in the same place every frame */
    private fun hash(vararg n: Int): Int {
        var h = 0x2F6B
        for (x in n) {
            h = h xor x
            h *= 0x45D9F3B
            h = h xor (h ushr 16)
        }
        return h and 0x7FFFFFFF
    }

    private class P(
        val s: PixelSink,
    ) {
        fun r(
            x: Int,
            y: Int,
            w: Int,
            h: Int,
            col: Int,
        ) {
            if (w <= 0 || h <= 0) return
            val x0 = max(0, x)
            val y0 = max(0, y)
            val x1 = min(W, x + w)
            val y1 = min(H, y + h)
            if (x1 > x0 && y1 > y0) s.rect(x0, y0, x1 - x0, y1 - y0, col)
        }

        fun px(
            x: Int,
            y: Int,
            col: Int,
        ) = r(x, y, 1, 1, col)

        /** A filled triangle-ish mountain: peak at (cx, top), base width */
        fun peak(
            cx: Int,
            top: Int,
            base: Int,
            halfW: Int,
            col: Int,
            cap: Int? = null,
        ) {
            val h = base - top
            for (row in 0 until h) {
                val half = halfW * row / max(1, h)
                r(cx - half, top + row, half * 2 + 1, 1, col)
                if (cap != null && row < h / 4) r(cx - half, top + row, half * 2 + 1, 1, cap)
            }
        }

        fun blob(
            cx: Int,
            cy: Int,
            rx: Int,
            ry: Int,
            col: Int,
        ) {
            for (dy in -ry..ry) {
                val f = 1f - (dy * dy).toFloat() / (ry * ry).coerceAtLeast(1)
                val half = (rx * kotlin.math.sqrt(f.coerceAtLeast(0f))).toInt()
                r(cx - half, cy + dy, half * 2 + 1, 1, col)
            }
        }

        /** A sprite from text rows: each letter is a colour from [pal], '.' is see-through */
        fun sprite(
            x: Int,
            y: Int,
            rows: List<String>,
            pal: Map<Char, Int>,
            flip: Boolean = false,
            scale: Int = 1,
        ) {
            for ((j, row) in rows.withIndex()) {
                for (i in row.indices) {
                    val ch = row[if (flip) row.length - 1 - i else i]
                    val col = pal[ch] ?: continue
                    r(x + i * scale, y + j * scale, scale, scale, col)
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------------
    // Scene recipes

    private enum class Sky { DAY, DUSK, NIGHT, GREY, SNOW, FIRE, SWAMP, MAGIC, PINK, CAVE, DAWN }

    private enum class Far { NONE, HILLS, MOUNTAINS, CASTLE, VOLCANO, ISLANDS, SEA, CLIFFS, BROWNHILLS }

    private enum class Mid {
        NONE,
        ROUND_TREES,
        PINES,
        WHITE_TREES,
        DEAD_TREES,
        MUSHROOMS,
        REEDS,
        BUSHES,
        ROCKS,
        HOUSES,
        SNOWPINES,
        FLOWERS,
    }

    private enum class Ground { GRASS, DARKGRASS, SNOW, ROCK, SWAMP, ASH, RIVER, SAND, CLOUD, SHELL, LAKE, PURPLE, FLOOR, ELFGRASS }

    private data class Recipe(
        val sky: Sky,
        val far: Far,
        val mid: Mid,
        val ground: Ground,
        val piece: String? = null,
    )

    private val lands =
        mapOf(
            "fields" to Recipe(Sky.DAY, Far.HILLS, Mid.ROUND_TREES, Ground.GRASS),
            "castle" to Recipe(Sky.DAY, Far.CASTLE, Mid.BUSHES, Ground.GRASS),
            "turtle" to Recipe(Sky.DAY, Far.SEA, Mid.HOUSES, Ground.SHELL),
            "lake" to Recipe(Sky.DAWN, Far.HILLS, Mid.REEDS, Ground.LAKE),
            "elfwood" to Recipe(Sky.MAGIC, Far.HILLS, Mid.WHITE_TREES, Ground.ELFGRASS),
            "darkwood" to Recipe(Sky.GREY, Far.NONE, Mid.DEAD_TREES, Ground.DARKGRASS),
            "snow" to Recipe(Sky.SNOW, Far.CLIFFS, Mid.SNOWPINES, Ground.SNOW),
            "hills" to Recipe(Sky.DAY, Far.BROWNHILLS, Mid.ROCKS, Ground.SAND),
            "mountain" to Recipe(Sky.DAY, Far.MOUNTAINS, Mid.PINES, Ground.ROCK),
            "mushroom" to Recipe(Sky.PINK, Far.NONE, Mid.MUSHROOMS, Ground.PURPLE),
            "swamp" to Recipe(Sky.SWAMP, Far.NONE, Mid.REEDS, Ground.SWAMP),
            "sky" to Recipe(Sky.DAY, Far.ISLANDS, Mid.NONE, Ground.CLOUD),
            "ashen" to Recipe(Sky.FIRE, Far.VOLCANO, Mid.ROCKS, Ground.ASH),
        )

    private val pieces =
        mapOf(
            "mill" to Recipe(Sky.DAY, Far.HILLS, Mid.NONE, Ground.GRASS, "mill"),
            "town" to Recipe(Sky.DAY, Far.CASTLE, Mid.HOUSES, Ground.GRASS, "town"),
            "turtlecity" to Recipe(Sky.DUSK, Far.SEA, Mid.NONE, Ground.SHELL, "turtlecity"),
            "inn" to Recipe(Sky.DUSK, Far.HILLS, Mid.REEDS, Ground.LAKE, "inn"),
            "elfhall" to Recipe(Sky.MAGIC, Far.NONE, Mid.WHITE_TREES, Ground.ELFGRASS, "elfhall"),
            "hut" to Recipe(Sky.GREY, Far.NONE, Mid.DEAD_TREES, Ground.DARKGRASS, "hut"),
            "giant" to Recipe(Sky.SNOW, Far.CLIFFS, Mid.NONE, Ground.SNOW, "giant"),
            "goblin" to Recipe(Sky.CAVE, Far.NONE, Mid.NONE, Ground.FLOOR, "goblin"),
            "dwarfgate" to Recipe(Sky.DAY, Far.MOUNTAINS, Mid.NONE, Ground.ROCK, "dwarfgate"),
            "spritering" to Recipe(Sky.PINK, Far.NONE, Mid.MUSHROOMS, Ground.PURPLE, "spritering"),
            "tower" to Recipe(Sky.SWAMP, Far.NONE, Mid.REEDS, Ground.SWAMP, "tower"),
            "starbridge" to Recipe(Sky.NIGHT, Far.ISLANDS, Mid.NONE, Ground.CLOUD, "starbridge"),
            "peak" to Recipe(Sky.FIRE, Far.NONE, Mid.NONE, Ground.ASH, "nest"),
            // event scenes
            "dragonsky" to Recipe(Sky.DAY, Far.HILLS, Mid.ROUND_TREES, Ground.GRASS, "bigdragon"),
            "dragonnet" to Recipe(Sky.DUSK, Far.HILLS, Mid.NONE, Ground.GRASS, "net"),
            "riders" to Recipe(Sky.GREY, Far.HILLS, Mid.NONE, Ground.GRASS, "riders"),
            "night" to Recipe(Sky.NIGHT, Far.HILLS, Mid.PINES, Ground.DARKGRASS, "campfire"),
            "camp" to Recipe(Sky.NIGHT, Far.HILLS, Mid.ROUND_TREES, Ground.GRASS, "campfire"),
            "road" to Recipe(Sky.DAY, Far.HILLS, Mid.BUSHES, Ground.GRASS),
            "forest" to Recipe(Sky.DAY, Far.NONE, Mid.ROUND_TREES, Ground.GRASS),
            "river" to Recipe(Sky.DAY, Far.HILLS, Mid.REEDS, Ground.RIVER),
            "gate" to Recipe(Sky.DAY, Far.CASTLE, Mid.NONE, Ground.GRASS, "gate"),
            "thorns" to Recipe(Sky.MAGIC, Far.NONE, Mid.WHITE_TREES, Ground.ELFGRASS, "thorns"),
            "bridge" to Recipe(Sky.GREY, Far.NONE, Mid.DEAD_TREES, Ground.RIVER, "bridge"),
            "snowpass" to Recipe(Sky.SNOW, Far.CLIFFS, Mid.NONE, Ground.SNOW, "snowshelf"),
            "goblins" to Recipe(Sky.DAY, Far.BROWNHILLS, Mid.NONE, Ground.SAND, "junkwall"),
            "ropebridge" to Recipe(Sky.DAY, Far.MOUNTAINS, Mid.NONE, Ground.ROCK, "ropebridge"),
            "spores" to Recipe(Sky.PINK, Far.NONE, Mid.MUSHROOMS, Ground.PURPLE, "spores"),
            "wisps" to Recipe(Sky.NIGHT, Far.NONE, Mid.REEDS, Ground.SWAMP, "wisps"),
            "lava" to Recipe(Sky.FIRE, Far.VOLCANO, Mid.NONE, Ground.ASH, "lavariver"),
            "rain" to Recipe(Sky.GREY, Far.HILLS, Mid.ROUND_TREES, Ground.GRASS),
            "fog" to Recipe(Sky.GREY, Far.NONE, Mid.PINES, Ground.GRASS),
            "village" to Recipe(Sky.DAY, Far.HILLS, Mid.HOUSES, Ground.GRASS),
            "magic" to Recipe(Sky.MAGIC, Far.HILLS, Mid.NONE, Ground.GRASS, "sparkles"),
            "cave" to Recipe(Sky.DAY, Far.MOUNTAINS, Mid.ROCKS, Ground.ROCK, "cavemouth"),
            "meadow" to Recipe(Sky.DAY, Far.HILLS, Mid.FLOWERS, Ground.GRASS),
            "bandits" to Recipe(Sky.DAY, Far.BROWNHILLS, Mid.ROCKS, Ground.SAND),
            "fields_night" to Recipe(Sky.NIGHT, Far.HILLS, Mid.ROUND_TREES, Ground.GRASS),
            // endings
            "hoard" to Recipe(Sky.CAVE, Far.NONE, Mid.NONE, Ground.FLOOR, "hoard"),
            "crown" to Recipe(Sky.FIRE, Far.NONE, Mid.NONE, Ground.ASH, "crownend"),
            "gold" to Recipe(Sky.DUSK, Far.CASTLE, Mid.HOUSES, Ground.GRASS, "goldpile"),
            "home" to Recipe(Sky.DUSK, Far.HILLS, Mid.ROUND_TREES, Ground.GRASS, "mill"),
            "emptynest" to Recipe(Sky.NIGHT, Far.NONE, Mid.NONE, Ground.ASH, "emptynest"),
            "baby" to Recipe(Sky.DAWN, Far.NONE, Mid.NONE, Ground.ASH, "babynest"),
            "coldegg" to Recipe(Sky.NIGHT, Far.NONE, Mid.NONE, Ground.ASH, "coldnest"),
            "legend" to Recipe(Sky.DAWN, Far.HILLS, Mid.ROUND_TREES, Ground.GRASS, "bigdragon"),
            "wings" to Recipe(Sky.DUSK, Far.HILLS, Mid.ROUND_TREES, Ground.GRASS, "bigdragon"),
            "lone" to Recipe(Sky.DUSK, Far.NONE, Mid.NONE, Ground.ASH, "nest"),
            "wrath" to Recipe(Sky.FIRE, Far.VOLCANO, Mid.NONE, Ground.ASH, "wrath"),
            "title" to Recipe(Sky.DUSK, Far.VOLCANO, Mid.PINES, Ground.GRASS),
        )

    val allScenes: Set<String> get() = lands.keys + pieces.keys

    /** The scene that goes with a land (for travel) */
    fun hasScene(key: String) = key in lands || key in pieces

    /** Draws a whole scene. [t] is the animation frame (about 12 a second). */
    fun scene(
        sink: PixelSink,
        key: String,
        t: Int,
        o: SceneOpts = SceneOpts(),
    ) {
        val p = P(sink)
        val rc = lands[key] ?: pieces[key] ?: lands.getValue("fields")
        val night = rc.sky == Sky.NIGHT || rc.sky == Sky.CAVE
        sky(p, rc.sky, t)
        far(p, rc.far, rc.sky, t)
        ground(p, rc.ground, t, o.season)
        mid(p, rc.mid, rc.ground, key.hashCode(), t, o.season, night)
        rc.piece?.let { piece(p, it, t, o) }
        if (o.cart) cart(p, t, o)
        o.creature?.let { creature(p, it, 104, 40, t) }
        weather(p, o.weather, t, rc.sky)
        if (o.crt) for (y in 0 until H step 2) p.r(0, y, W, 1, 0x22000000)
    }

    // -------------------------------------------------------------------------------------------
    // Sky

    private fun skyColours(s: Sky): List<Int> =
        when (s) {
            Sky.DAY -> listOf(0x3B7DD8, 0x5C9BE6, 0x7FB6EE, 0xA6D0F2, 0xCDE6F5)
            Sky.DAWN -> listOf(0x4A5BA8, 0x8C74B8, 0xD99AB0, 0xF2B78C, 0xF8D7A0)
            Sky.DUSK -> listOf(0x2B1B4D, 0x5A2A6A, 0xA8435A, 0xE07A3F, 0xF2B45A)
            Sky.NIGHT -> listOf(0x0A0B24, 0x111538, 0x1A1F4A, 0x232A5C, 0x2C356A)
            Sky.GREY -> listOf(0x4A5058, 0x5C636C, 0x6E7680, 0x828A93, 0x969DA5)
            Sky.SNOW -> listOf(0x8FA9C8, 0xA9BFD8, 0xC2D3E5, 0xD9E4EF, 0xEDF2F7)
            Sky.FIRE -> listOf(0x1A0A0E, 0x3A0F14, 0x6A1A1A, 0xA8321E, 0xE0662A)
            Sky.SWAMP -> listOf(0x3A4A3E, 0x4A5C4C, 0x5C6E58, 0x6E8064, 0x809270)
            Sky.MAGIC -> listOf(0x4C6FA8, 0x6E8CC4, 0x92ABD8, 0xB9CDE8, 0xE0EAF5)
            Sky.PINK -> listOf(0x1E0F38, 0x341656, 0x4E2070, 0x6A2C86, 0x86389A)
            Sky.CAVE -> listOf(0x120E12, 0x1A1418, 0x221A20, 0x2A2026, 0x32262C)
        }

    private fun sky(
        p: P,
        s: Sky,
        t: Int,
    ) {
        val cols = skyColours(s).map { c(it) }
        for (i in cols.indices) p.r(0, i * 9, W, 9, cols[i])
        // soft dithered edges between bands
        for (i in 1 until cols.size) for (x in 0 until W step 2) p.px(x + (i % 2), i * 9, cols[i - 1])
        when (s) {
            Sky.DAY, Sky.MAGIC -> {
                p.blob(132, 12, 6, 6, c(0xFFF2B0))
                p.blob(132, 12, 4, 4, c(0xFFFBE0))
                cloud(p, ((t / 3) % 220) - 40, 10, c(0xF4F8FC))
                cloud(p, ((t / 5 + 120) % 220) - 40, 22, c(0xE6EEF6))
            }

            Sky.DAWN, Sky.DUSK -> {
                p.blob(30, 34, 8, 8, c(if (s == Sky.DUSK) 0xFFD27A else 0xFFE6A8))
                cloud(p, ((t / 4) % 220) - 40, 14, c(if (s == Sky.DUSK) 0xC4566A else 0xF0C0C8))
            }

            Sky.NIGHT, Sky.PINK -> {
                for (i in 0 until 26) {
                    val x = hash(i, 7) % W
                    val y = hash(i, 13) % 34
                    val tw = (t / 4 + i) % 7 == 0
                    p.px(x, y, if (tw) c(0x6A5AA0) else c(0xE8E4FF))
                }
                if (s == Sky.NIGHT) {
                    p.blob(24, 10, 5, 5, c(0xF3EFD6))
                    p.r(25, 8, 2, 2, c(0xD8D2B0))
                }
            }

            Sky.FIRE -> {
                for (i in 0 until 14) {
                    val x = (hash(i, 3) % W + t / 2) % W
                    val y = 40 - ((t + hash(i, 5) % 40) % 40)
                    p.px(x, y, c(0xFFB040))
                }
            }

            Sky.GREY, Sky.SWAMP -> {
                cloud(p, ((t / 6) % 220) - 40, 8, c(if (s == Sky.GREY) 0x3E444C else 0x4A5A48))
                cloud(p, ((t / 4 + 90) % 220) - 40, 18, c(if (s == Sky.GREY) 0x50565E else 0x56684E))
            }

            Sky.SNOW -> {
                cloud(p, ((t / 5) % 220) - 40, 12, c(0xF2F6FA))
            }

            Sky.CAVE -> {
                for (i in 0 until 10) {
                    val x = hash(i, 11) % W
                    p.peak(x, 0, 0, 0, c(0x0E0A0E))
                    p.r(x, 0, 3, 4 + hash(i, 2) % 8, c(0x0E0A0E))
                    p.r(x + 1, 4 + hash(i, 2) % 8, 1, 2, c(0x0E0A0E))
                }
            }
        }
    }

    private fun cloud(
        p: P,
        x: Int,
        y: Int,
        col: Int,
    ) {
        p.r(x + 4, y, 14, 3, col)
        p.r(x, y + 2, 26, 4, col)
        p.r(x + 8, y - 2, 8, 3, col)
    }

    // -------------------------------------------------------------------------------------------
    // Far layer

    private fun far(
        p: P,
        f: Far,
        sky: Sky,
        t: Int,
    ) {
        val dark = sky == Sky.NIGHT || sky == Sky.DUSK || sky == Sky.PINK
        when (f) {
            Far.NONE -> {}

            Far.HILLS, Far.BROWNHILLS -> {
                val a =
                    if (f == Far.BROWNHILLS) {
                        0xA9805A
                    } else if (dark) {
                        0x2A2350
                    } else {
                        0x7FA6C8
                    }
                val b =
                    if (f == Far.BROWNHILLS) {
                        0x8C6644
                    } else if (dark) {
                        0x1F1A3E
                    } else {
                        0x6C93B5
                    }
                for (x in 0 until W) {
                    val h1 = (sin(x / 14.0) * 4 + sin(x / 5.3) * 1.5).toInt()
                    p.r(x, 30 + h1, 1, 12, c(a))
                    val h2 = (sin((x + 40) / 9.0) * 3 + sin(x / 3.7)).toInt()
                    p.r(x, 35 + h2, 1, 8, c(b))
                }
            }

            Far.MOUNTAINS -> {
                val col = if (dark) c(0x2A2350) else c(0x6C7FA0)
                val cap = if (dark) c(0x8A8AB0) else c(0xF2F6FA)
                p.peak(28, 10, 42, 26, col, cap)
                p.peak(70, 4, 42, 30, c(if (dark) 0x231E46 else 0x5E7090), cap)
                p.peak(118, 12, 42, 28, col, cap)
                p.peak(150, 18, 42, 18, c(if (dark) 0x231E46 else 0x5E7090), cap)
            }

            Far.CLIFFS -> {
                for (i in 0 until 8) {
                    val x = i * 22 - 4
                    val h = 14 + hash(i, 4) % 14
                    p.r(x, 42 - h, 18, h, c(0xB8CCE0))
                    p.r(x, 42 - h, 4, h, c(0xD8E6F2))
                    p.r(x + 14, 42 - h, 4, h, c(0x9AB0C8))
                }
            }

            Far.CASTLE -> {
                far(p, Far.HILLS, sky, t)
                val col = c(if (dark) 0x2A2340 else 0xD8D4CC)
                val sh = c(if (dark) 0x1E1A30 else 0xB0AAA0)
                p.r(96, 18, 34, 18, col)
                p.r(92, 10, 8, 26, col)
                p.r(126, 10, 8, 26, col)
                p.r(108, 6, 10, 30, col)
                for (x in listOf(92, 96, 126, 130, 108, 112, 116)) p.r(x, if (x in 108..117) 4 else 8, 2, 2, col)
                p.r(110, 2, 1, 4, c(0x4A3A2A))
                p.r(111, 2, 4, 2, c(0xE50914))
                p.r(92, 10, 2, 26, sh)
                p.r(110, 26, 6, 10, c(0x3A2A20))
                p.r(100, 22, 2, 3, c(0x3A4A6A))
                p.r(122, 22, 2, 3, c(0x3A4A6A))
            }

            Far.VOLCANO -> {
                p.peak(110, 6, 44, 46, c(0x2A1416))
                p.r(104, 6, 13, 3, c(0xE0662A))
                p.r(106, 5, 9, 1, c(0xFFB040))
                for (i in 0 until 4) {
                    val y = 4 - ((t / 2 + i * 5) % 20)
                    p.r(108 + (i * 3) % 6, y, 3, 3, c(0x3A2A2A))
                }
                p.r(108, 9, 2, 12, c(0xC03A1A))
                p.r(113, 9, 1, 8, c(0xC03A1A))
            }

            Far.ISLANDS -> {
                for (i in 0 until 4) {
                    val x = 10 + i * 40 + (hash(i, 9) % 10)
                    val y = 14 + (hash(i, 6) % 12) + (sin((t + i * 30) / 20.0) * 1.5).toInt()
                    val w = 16 + hash(i, 3) % 10
                    p.r(x, y, w, 3, c(0x5E9E4E))
                    for (row in 0 until 7) p.r(x + row, y + 3 + row, w - row * 2, 1, c(0x7A6A5A))
                    p.r(x + w / 2, y - 4, 2, 4, c(0x3E6E3E))
                }
            }

            Far.SEA -> {
                for (y in 32 until 44) p.r(0, y, W, 1, c(if ((y + t / 6) % 3 == 0) 0x4A8AC0 else 0x3A78B0))
            }
        }
    }

    // -------------------------------------------------------------------------------------------
    // Ground

    private fun grassFor(season: String) =
        when (season) {
            "summer" -> Pair(0x5A9A3A, 0x4A8A30)
            "autumn" -> Pair(0x9A8A3A, 0x8A6E2E)
            "winter" -> Pair(0xD8E2EA, 0xC2CED8)
            else -> Pair(0x4FA040, 0x3E8A36)
        }

    private fun ground(
        p: P,
        g: Ground,
        t: Int,
        season: String,
    ) {
        val top = 44
        when (g) {
            Ground.GRASS, Ground.DARKGRASS, Ground.ELFGRASS, Ground.PURPLE -> {
                val (a, b) =
                    when (g) {
                        Ground.DARKGRASS -> Pair(0x2E4A2E, 0x253E26)
                        Ground.ELFGRASS -> Pair(0x8AC07A, 0x76AE68)
                        Ground.PURPLE -> Pair(0x4A2E6A, 0x3E2458)
                        else -> grassFor(season)
                    }
                for (y in top until H) p.r(0, y, W, 1, c(if (y % 2 == 0) a else b))
                for (i in 0 until 40) p.px(hash(i, 21) % W, top + hash(i, 22) % (H - top), c(b))
                road(p, if (g == Ground.PURPLE) 0x6A4A8A else 0xB89A6A, if (g == Ground.PURPLE) 0x5A3E78 else 0xA0845A)
            }

            Ground.SNOW -> {
                for (y in top until H) p.r(0, y, W, 1, c(if (y % 3 == 0) 0xE6EEF4 else 0xF4F8FB))
                road(p, 0xCAD6E0, 0xB8C6D2)
            }

            Ground.ROCK -> {
                for (y in top until H) p.r(0, y, W, 1, c(if (y % 2 == 0) 0x7A7268 else 0x6C645A))
                for (i in 0 until 30) p.r(hash(i, 1) % W, top + hash(i, 2) % (H - top), 2, 1, c(0x5A544C))
                road(p, 0x9A8E7E, 0x8A7E6E)
            }

            Ground.SAND -> {
                for (y in top until H) p.r(0, y, W, 1, c(if (y % 2 == 0) 0xC8A46A else 0xBA965E))
                road(p, 0xDAB880, 0xCCA872)
            }

            Ground.ASH -> {
                for (y in top until H) p.r(0, y, W, 1, c(if (y % 2 == 0) 0x3A2E2E else 0x2E2424))
                for (i in 0 until 16) {
                    val glow = (t / 3 + i) % 6 < 3
                    p.r(hash(i, 4) % W, top + 4 + hash(i, 5) % (H - top - 4), 3, 1, c(if (glow) 0xE0662A else 0x8A2A1A))
                }
                road(p, 0x4A3C3A, 0x403432)
            }

            Ground.SWAMP -> {
                for (y in top until H) p.r(0, y, W, 1, c(if (y % 2 == 0) 0x3E5A3A else 0x34503A))
                for (i in 0 until 6) {
                    val x = hash(i, 8) % W
                    val y = top + 6 + hash(i, 9) % 30
                    p.r(x, y, 18, 3, c(0x2E4A4A))
                    p.r(x + 2 + (t / 4 + i) % 12, y + 1, 2, 1, c(0x6A9A8A))
                }
                road(p, 0x6A5A3A, 0x5A4A30)
            }

            Ground.RIVER -> {
                ground(p, Ground.GRASS, t, season)
                for (y in 56 until 72) {
                    p.r(0, y, W, 1, c(if (y % 2 == 0) 0x2D6C9A else 0x26608A))
                    for (x in 0 until W) if ((x + y * 3 + t) % 23 == 0) p.r(x, y, 3, 1, c(0x9FD3E8))
                }
            }

            Ground.LAKE -> {
                ground(p, Ground.GRASS, t, season)
                for (y in top until 64) {
                    val w = 70 + (y - top) * 3
                    p.r(0, y, w, 1, c(if (y % 2 == 0) 0x6A9AC8 else 0x5C8CBA))
                    if ((y + t / 3) % 5 == 0) p.r((t + y * 7) % max(1, w), y, 4, 1, c(0xD8EAF8))
                }
            }

            Ground.CLOUD -> {
                for (y in top + 4 until H) p.r(0, y, W, 1, c(if (y % 2 == 0) 0xF2F6FA else 0xE2EAF2))
                for (i in 0 until 9) p.blob(i * 20 + (t / 4) % 20, top + 6, 10, 4, c(0xF8FBFD))
                p.r(0, 60, W, 12, c(0x8A7A62))
                p.r(0, 60, W, 2, c(0x5E9E4E))
                p.r(0, 72, W, 1, c(0x6A5A48))
            }

            Ground.SHELL -> {
                for (y in top until H) p.r(0, y, W, 1, c(if (y % 2 == 0) 0x6A8A4A else 0x5E7E42))
                for (i in 0 until 8) {
                    val x = i * 22 - 6
                    p.r(x, 50, 20, 1, c(0x4E6A36))
                    p.r(x, 50, 1, 14, c(0x4E6A36))
                }
                road(p, 0xB0A080, 0xA09070)
            }

            Ground.FLOOR -> {
                for (y in top until H) p.r(0, y, W, 1, c(if (y % 2 == 0) 0x3A2E26 else 0x32281F))
                for (i in 0 until 12) p.r(hash(i, 3) % W, top + hash(i, 4) % 40, 4, 1, c(0x2A2018))
            }
        }
    }

    private fun road(
        p: P,
        a: Int,
        b: Int,
    ) {
        for (y in 62 until 76) p.r(0, y, W, 1, c(if (y % 3 == 0) b else a))
        p.r(0, 62, W, 1, c(b))
        p.r(0, 75, W, 1, c(b))
    }

    // -------------------------------------------------------------------------------------------
    // Middle layer: trees, houses, reeds

    private fun mid(
        p: P,
        m: Mid,
        g: Ground,
        seed: Int,
        t: Int,
        season: String,
        night: Boolean,
    ) {
        val xs = (0 until 9).map { i -> (i * 19 + hash(seed, i) % 12) % (W + 10) - 6 }
        when (m) {
            Mid.NONE -> {}

            Mid.ROUND_TREES -> {
                val leaf =
                    when (season) {
                        "autumn" -> 0xC86A2A
                        "winter" -> 0x8A9AA8
                        "summer" -> 0x2E7A2E
                        else -> 0x3A8A3A
                    }
                for ((i, x) in xs.withIndex()) {
                    val y = 36 + hash(seed, i, 2) % 10
                    p.r(x + 4, y + 8, 2, 8, c(0x5A3A22))
                    p.blob(x + 5, y + 4, 6, 6, c(EmberArt.mix(c(leaf), c(0x000000), if (night) 0.5f else 0f)))
                    p.blob(x + 3, y + 2, 3, 3, c(EmberArt.mix(c(leaf), c(0xFFFFFF), if (night) 0f else 0.25f)))
                }
            }

            Mid.PINES, Mid.SNOWPINES -> {
                for ((i, x) in xs.withIndex()) {
                    val y = 30 + hash(seed, i, 2) % 12
                    p.r(x + 4, y + 14, 2, 4, c(0x4A3020))
                    p.peak(x + 5, y, y + 15, 6, c(if (night) 0x163A26 else 0x2A5E3A))
                    if (m == Mid.SNOWPINES) {
                        p.peak(x + 5, y, y + 4, 2, c(0xF2F6FA))
                        p.r(x + 1, y + 9, 9, 1, c(0xF2F6FA))
                    }
                }
            }

            Mid.WHITE_TREES -> {
                for ((i, x) in xs.withIndex()) {
                    val y = 24 + hash(seed, i, 2) % 10
                    p.r(x + 4, y + 6, 2, 22, c(0xE8E8F0))
                    p.r(x + 5, y + 6, 1, 22, c(0xC8C8D8))
                    p.blob(x + 5, y + 4, 7, 5, c(0xC8E0F0))
                    if ((t / 6 + i) % 4 == 0) p.px(x + 2 + (t / 3) % 6, y + 10 + (t / 2) % 20, c(0xFFFFFF))
                }
            }

            Mid.DEAD_TREES -> {
                for ((i, x) in xs.withIndex()) {
                    val y = 22 + hash(seed, i, 2) % 10
                    val col = c(0x1E1A1E)
                    p.r(x + 4, y, 3, 30, col)
                    p.r(x, y + 6, 5, 1, col)
                    p.r(x + 6, y + 10, 6, 1, col)
                    p.r(x, y + 5, 1, 2, col)
                    p.r(x + 11, y + 8, 1, 3, col)
                    if (hash(seed, i) % 3 == 0) {
                        p.px(x + 4, y + 12, c(0xE8E030))
                        p.px(x + 6, y + 12, c(0xE8E030))
                    }
                }
            }

            Mid.MUSHROOMS -> {
                val caps = listOf(0xC44A8A, 0x4A8AC4, 0x8A4AC4, 0x44B4A4)
                for ((i, x) in xs.withIndex()) {
                    val y = 24 + hash(seed, i, 2) % 14
                    val h = 18 + hash(seed, i, 3) % 10
                    val cap = caps[i % caps.size]
                    p.r(x + 4, y + 4, 3, h, c(0xE0D4C0))
                    p.blob(x + 5, y + 3, 9, 4, c(cap))
                    p.r(x - 3, y + 5, 17, 1, c(EmberArt.mix(c(cap), c(0x000000), 0.3f)))
                    if ((t / 5 + i) % 3 == 0) p.px(x + 2, y + 1, c(0xFFFFFF))
                    p.px(x + 8, y + 2, c(0xFFE0F0))
                }
            }

            Mid.REEDS -> {
                for (i in 0 until 26) {
                    val x = hash(seed, i) % W
                    val h = 6 + hash(seed, i, 1) % 8
                    val sway = if ((t / 6 + i) % 4 == 0) 1 else 0
                    p.r(x + sway, 46 - h, 1, h, c(0x6A7A3A))
                    p.r(x + sway, 46 - h, 1, 2, c(0x7A4A2A))
                }
            }

            Mid.BUSHES -> {
                for ((i, x) in xs.withIndex()) p.blob(x + 6, 44, 6, 3, c(if (i % 2 == 0) 0x3A7A34 else 0x46883E))
            }

            Mid.FLOWERS -> {
                mid(p, Mid.BUSHES, g, seed, t, season, night)
                for (i in 0 until 40) {
                    val cols = listOf(0xF2E05A, 0xE04A6A, 0xFFFFFF, 0x8A6AE0)
                    p.px(hash(seed, i) % W, 46 + hash(seed, i, 2) % 14, c(cols[i % 4]))
                }
            }

            Mid.ROCKS -> {
                for ((i, x) in xs.withIndex()) {
                    val y = 40 + hash(seed, i, 2) % 6
                    p.blob(x + 6, y, 6, 4, c(0x7A6E64))
                    p.r(x + 2, y - 2, 6, 2, c(0x9A8E82))
                }
            }

            Mid.HOUSES -> {
                for ((i, x) in xs.withIndex()) {
                    if (i % 2 == 1) continue
                    val y = 34 + hash(seed, i, 2) % 4
                    val wall = listOf(0xE8DCC0, 0xD8C8A8, 0xC8D8E0)[i % 3]
                    val roof = listOf(0xA8432A, 0x3A6A9A, 0x6A4A8A)[i % 3]
                    p.r(x, y + 4, 14, 10, c(wall))
                    for (row in 0 until 5) p.r(x - 1 + row, y + row - 1, 16 - row * 2, 1, c(roof))
                    p.r(x + 5, y + 8, 3, 6, c(0x5A3A22))
                    p.r(x + 10, y + 6, 2, 2, c(if (night) 0xFFD27A else 0x5A7A9A))
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------------
    // Set pieces

    private fun piece(
        p: P,
        key: String,
        t: Int,
        o: SceneOpts,
    ) {
        when (key) {
            "mill" -> {
                p.r(96, 22, 24, 30, c(0xE0D2B0))
                p.r(96, 22, 4, 30, c(0xC8B898))
                for (row in 0 until 8) p.r(94 + row, 14 + row, 28 - row * 2, 1, c(0x8A3A2A))
                p.r(105, 40, 6, 12, c(0x5A3A22))
                // the wheel turns
                val a = (t / 3) % 4
                p.blob(126, 40, 8, 8, c(0x6A4A2A))
                p.blob(126, 40, 6, 6, c(0x8A6A42))
                for (k in 0 until 4) {
                    val dx = listOf(0, 7, 0, -7)[(k + a) % 4]
                    val dy = listOf(-7, 0, 7, 0)[(k + a) % 4]
                    p.r(126 + dx - 1, 40 + dy - 1, 3, 3, c(0x5A3A22))
                }
                p.blob(70, 50, 6, 3, c(0x9A9A92))
                p.r(64, 48, 12, 2, c(0xB0B0A8))
                p.r(80, 46, 5, 6, c(0xE8DCB8))
                p.r(86, 47, 5, 5, c(0xE0D2B0))
            }

            "town" -> {
                p.r(60, 44, 40, 2, c(0x8A7A6A))
                p.r(76, 26, 8, 18, c(0xC8C4BC))
                p.blob(80, 24, 5, 4, c(0xC8C4BC))
                p.r(79, 18, 3, 6, c(0xB0AAA0))
                // market stalls
                for (i in 0 until 3) {
                    val x = 20 + i * 18
                    p.r(x, 46, 14, 8, c(0x8A5A3A))
                    for (k in 0 until 7) p.r(x + k * 2, 42, 2, 4, c(if (k % 2 == 0) 0xE50914 else 0xF2F2F2))
                }
                p.r(130, 14, 8, 34, c(0x6A5A8A))
                for (row in 0 until 8) p.r(128 + row / 2 + row % 2, 6 + row, 12 - row, 1, c(0x3A2A6A))
                p.r(133, 24, 2, 3, c(0xFFE08A))
            }

            "turtlecity" -> {
                p.blob(80, 48, 70, 14, c(0x5E7E42))
                for (i in 0 until 7) {
                    val x = 26 + i * 16
                    val y = 30 + abs(i - 3) * 3
                    p.r(x, y, 10, 10, c(listOf(0xE8DCC0, 0xC8D8E0, 0xE0C8A8)[i % 3]))
                    for (row in 0 until 4) p.r(x - 1 + row, y - 4 + row, 12 - row * 2, 1, c(listOf(0x3A6A9A, 0xA8432A, 0x2A8A7A)[i % 3]))
                    p.r(x + 4, y + 4, 2, 2, c(0xFFD27A))
                }
                p.blob(150, 46, 10, 7, c(0x6A8A4A))
                val blink = (t / 4) % 30 == 0
                p.r(148, 44, 5, if (blink) 1 else 3, c(0x1A1A10))
                p.r(149, 44, 1, 1, c(0xF2F2E0))
            }

            "inn" -> {
                for (k in 0 until 5) p.r(96 + k * 8, 46, 2, 14, c(0x5A3A22))
                p.r(92, 30, 40, 16, c(0x9A6A42))
                for (row in 0 until 6) p.r(90 + row, 24 + row, 44 - row * 2, 1, c(0x5A3A2A))
                p.r(100, 36, 4, 4, c(0xFFD27A))
                p.r(116, 36, 4, 4, c(0xFFD27A))
                val sw = if ((t / 6) % 2 == 0) 0 else 1
                p.r(132, 30 + sw, 2, 3, c(0xFFC040))
                p.r(40, 54, 18, 3, c(0x7A4A2A))
                p.r(48, 46, 1, 8, c(0x5A3A22))
                p.blob(70, 38, 5, 3, c(0x6A7A5A))
                if ((t / 3) % 3 == 0) p.px(70, 34, c(0xFFF2A0))
            }

            "elfhall" -> {
                p.r(40, 10, 6, 36, c(0xF2F2F8))
                p.r(114, 10, 6, 36, c(0xF2F2F8))
                for (i in 0 until 40) {
                    val y = 10 - (i * i) / 60
                    p.r(46 + i * 17 / 10, y, 2, 1, c(0xE0E8F0))
                }
                p.r(74, 32, 12, 14, c(0x8A7A5A))
                p.r(76, 28, 8, 4, c(0xA8987A))
                p.blob(108, 50, 8, 2, c(0x92B8E0))
                p.px(106 + (t / 4) % 6, 50, c(0xFFFFFF))
                p.r(56, 36, 2, 10, c(0xD8D8E0))
                p.r(58, 34, 6, 1, c(0xD8D8E0))
                for (k in 0 until 3) p.r(58 + k * 2, 35, 1, 10, c(0xC0C8D8))
            }

            "hut" -> {
                p.r(84, 42, 2, 10, c(0xC8A060))
                p.r(96, 42, 2, 10, c(0xC8A060))
                p.r(82, 50, 4, 2, c(0xC8A060))
                p.r(96, 50, 4, 2, c(0xC8A060))
                val bob = if ((t / 8) % 2 == 0) 0 else 1
                p.r(78, 26 + bob, 26, 16, c(0x5A4A3A))
                for (row in 0 until 8) p.r(76 + row, 18 + row + bob, 30 - row * 2, 1, c(0x3A5A3A))
                p.r(88, 32 + bob, 6, 10, c(0x2A1E16))
                p.r(80, 30 + bob, 4, 4, c(0xE0D040))
                p.r(97, 14 + bob, 4, 8, c(0x3A2E2A))
                for (k in 0 until 3) p.r(98 + k, 12 - k * 3 - (t / 3) % 3 + bob, 2, 2, c(0x8A8A8A))
                p.r(64, 50, 4, 3, c(0x101010))
                p.r(65, 48, 1, 2, c(0x101010))
                p.r(67, 48, 1, 2, c(0x101010))
            }

            "giant" -> {
                p.blob(80, 50, 46, 12, c(0x9AB0C8))
                p.blob(52, 42, 14, 10, c(0xA8C0D8))
                p.blob(50, 40, 8, 6, c(0xE8D8C8))
                p.r(44, 40, 3, 1, c(0x2A3A4A))
                p.r(52, 40, 3, 1, c(0x2A3A4A))
                p.blob(46, 48, 12, 6, c(0xF2F6FA))
                p.blob(80, 46, 40, 8, c(0xF4F8FB))
                val z = (t / 6) % 3
                for (k in 0..z) p.r(60 + k * 4, 26 - k * 4, 3, 1, c(0x4A5A7A))
                p.blob(128, 50, 10, 5, c(0xE0B040))
                p.r(124, 44, 8, 4, c(0x2A2A2A))
                p.r(140, 30, 18, 20, c(0x3A4A5A))
            }

            "goblin" -> {
                for (i in 0 until 6) {
                    val x = 10 + i * 26
                    p.r(x, 0, 1, 10 + i % 3 * 4, c(0x4A3A2A))
                    val sw = if ((t / 5 + i) % 2 == 0) 0 else 1
                    p.r(x - 2 + sw, 10 + i % 3 * 4, 5, 5, c(0xFFB040))
                    p.r(x - 1 + sw, 11 + i % 3 * 4, 3, 3, c(0xFFE08A))
                }
                for (i in 0 until 4) {
                    val x = 14 + i * 38
                    p.r(x, 40, 20, 12, c(0x6A4A2A))
                    p.r(x - 2, 38, 24, 3, c(listOf(0x6A2A6A, 0x2A6A4A, 0x8A6A2A, 0x2A4A8A)[i]))
                    p.r(x + 3, 36, 3, 2, c(0xC0C040))
                    p.r(x + 10, 35, 4, 3, c(0x8A4A4A))
                }
                creature(p, "goblin", 70, 46, t)
            }

            "dwarfgate" -> {
                p.r(30, 14, 100, 34, c(0x6A6258))
                p.r(66, 22, 28, 26, c(0x3A2E26))
                for (row in 0 until 6) p.r(66 + row, 16 + row, 28 - row * 2, 1, c(0x3A2E26))
                p.r(79, 22, 2, 26, c(0x6A5038))
                statue(p, 40, 18)
                statue(p, 104, 18)
                val glow = (t / 4) % 4
                p.r(70, 42, 20, 6, c(if (glow < 2) 0xE07A2A else 0xC0601A))
            }

            "spritering" -> {
                for (k in 0 until 12) {
                    val a = k * Math.PI * 2 / 12
                    val x = 80 + (kotlin.math.cos(a) * 34).toInt()
                    val y = 56 + (sin(a) * 9).toInt()
                    p.r(x, y, 2, 3, c(0xE0D4C0))
                    p.r(x - 1, y - 1, 4, 2, c(0x5ADCE8))
                }
                p.r(76, 52, 8, 5, c(0x7A5A3A))
                p.r(79, 49, 2, 3, c(0xFFD040))
                for (k in 0 until 8) {
                    val x = 60 + (hash(k, 1) % 40) + (sin((t + k * 9) / 4.0) * 6).toInt()
                    val y = 36 + (hash(k, 2) % 14) + (sin((t + k * 5) / 3.0) * 3).toInt()
                    p.px(x, y, c(0xFFF2A0))
                    p.px(x - 1, y, c(0xA0E8FF))
                    p.px(x + 1, y, c(0xA0E8FF))
                }
            }

            "tower" -> {
                p.r(96, 8, 20, 48, c(0x6A6A70))
                p.r(96, 8, 4, 48, c(0x5A5A60))
                for (row in 0 until 10) p.r(94 + row, row - 2, 24 - row * 2, 1, c(0x3A3A6A))
                p.r(104, 20, 5, 7, c(0x2A2A30))
                p.r(103, 36, 6, 8, c(0x3A2A20))
                p.r(80, 54, 52, 4, c(0x2E4A4A))
                p.blob(60, 54, 10, 6, c(0x5A7A3A))
                p.blob(56, 50, 4, 3, c(0x6A8A4A))
                p.r(54, 49, 2, 2, c(0xE8E030))
                p.r(58, 49, 2, 2, c(0xE8E030))
                wisp(p, 120, 24, t, 0x6AE0FF)
                wisp(p, 88, 30, t + 7, 0x8AFF8A)
            }

            "starbridge" -> {
                for (x in 20 until 140) {
                    val y = 46 - (sin((x - 20) * Math.PI / 120) * 16).toInt()
                    p.px(x, y, c(if ((x + t) % 9 == 0) 0xFFFFFF else 0xC8D8FF))
                    p.px(x, y + 1, c(0x8AA8F0))
                }
                p.r(10, 30, 14, 26, c(0x6A7A6A))
                p.r(13, 26, 8, 4, c(0x7A8A7A))
                p.r(14, 34, 2, 2, c(0x6AC8FF))
                p.r(18, 34, 2, 2, c(0x6AC8FF))
                p.r(130, 10, 6, 30, c(0x7A6A5A))
                p.blob(133, 10, 6, 3, c(0x8A7A5A))
                creature(p, "griffin", 128, 0, t)
            }

            "nest", "emptynest", "babynest", "coldnest" -> {
                p.blob(80, 56, 30, 8, c(0x4A3A34))
                p.blob(80, 54, 26, 6, c(0x6A3A2A))
                for (k in 0 until 10) p.r(56 + k * 5, 52 + (k % 2), 3, 2, c(if ((t / 3 + k) % 3 == 0) 0xFFB040 else 0xE0662A))
                when (key) {
                    "nest" -> {
                        if (o.egg) egg(p, 80, 46, t, false)
                    }

                    "coldnest" -> {
                        egg(p, 80, 46, t, true)
                    }

                    "babynest" -> {
                        creature(p, "babydragon", 72, 38, t)
                    }

                    else -> {}
                }
                p.r(126, 26, 22, 24, c(0x1E1618))
                p.r(130, 32, 3, 4, c(0x3A1E1E))
                p.r(136, 32, 3, 4, c(0x3A1E1E))
                p.r(142, 32, 3, 4, c(0x3A1E1E))
            }

            "hoard" -> {
                p.blob(80, 58, 50, 10, c(0xE0B040))
                p.blob(80, 56, 40, 8, c(0xF2CC5A))
                for (k in 0 until 20) {
                    val x = 40 + hash(k, 3) % 80
                    val y = 50 + hash(k, 4) % 12
                    if ((t / 3 + k) % 5 == 0) p.px(x, y, c(0xFFFFFF)) else p.px(x, y, c(listOf(0xE04A4A, 0x4AE0A0, 0x4A8AE0)[k % 3]))
                }
                p.r(74, 40, 12, 4, c(0xFF8A2A))
                for (k in 0 until 4) p.r(74 + k * 3, 36 - (t / 2 + k) % 3, 2, 4, c(0xFFC040))
            }

            "crownend" -> {
                piece(p, "nest", t, o)
                p.r(40, 34, 12, 4, c(0x2A2A2A))
                for (k in 0 until 4) p.r(40 + k * 3, 31, 2, 3, c(0x2A2A2A))
                p.px(45, 35, c(0xE01A1A))
            }

            "goldpile" -> {
                p.blob(80, 58, 24, 6, c(0xE0B040))
                p.blob(80, 56, 18, 5, c(0xF2CC5A))
                p.r(66, 44, 28, 10, c(0x6A4A2A))
                p.r(66, 44, 28, 2, c(0x8A6A3A))
            }

            "bigdragon" -> {
                val x = ((t * 1.2).toInt() % 240) - 60
                val y = 10 + (sin(t / 10.0) * 4).toInt()
                bigDragon(p, x, y, t)
            }

            "wrath" -> {
                bigDragon(p, 90, 8, t)
                for (k in 0 until 20) p.r(70 - k * 2, 22 + k + (hash(k, t / 2) % 3), 4, 3, c(if (k % 2 == 0) 0xFF8A2A else 0xFFD040))
            }

            "net" -> {
                bigDragon(p, 50, 26, t, still = true)
                for (k in 0 until 14) {
                    p.r(46 + k * 5, 24, 1, 26, c(0x6A6A70))
                    p.r(44, 24 + k * 2, 70, 1, c(0x6A6A70))
                }
                p.r(126, 40, 16, 10, c(0x8A7A6A))
                for (row in 0 until 6) p.r(124 + row, 34 + row, 20 - row * 2, 1, c(0x6A6A5A))
                p.r(134, 50, 4, 4, c(0xFF8A2A))
            }

            "riders" -> {
                for (k in 0 until 4) rider(p, 70 + k * 18, 46, t + k * 3)
            }

            "campfire" -> {
                p.r(74, 56, 12, 2, c(0x5A3A22))
                p.r(76, 54, 8, 2, c(0x6A4A2A))
                val f = (t / 2) % 3
                p.r(77, 48 - f, 6, 6 + f, c(0xE0662A))
                p.r(78, 46 - f, 4, 6, c(0xFFB040))
                p.r(79, 44 - (t % 2), 2, 4, c(0xFFF2A0))
                for (k in 0 until 3) p.px(78 + (t + k * 3) % 5, 40 - (t / 2 + k * 4) % 12, c(0xFFC040))
                // logs to sit on, with a warm glow
                p.r(54, 58, 14, 3, c(0x6A4A2A))
                p.r(92, 58, 14, 3, c(0x6A4A2A))
                p.blob(80, 56, 22, 4, 0x33FFB040)
            }

            "gate" -> {
                p.r(50, 26, 10, 30, c(0xB0AAA0))
                p.r(100, 26, 10, 30, c(0xB0AAA0))
                for (k in 0 until 9) p.r(60 + k * 5, 30, 2, 26, c(0x4A4A50))
                p.r(60, 30, 40, 2, c(0x4A4A50))
                p.r(54, 20, 2, 6, c(0x4A3A2A))
                p.r(56, 20, 6, 3, c(0xE50914))
            }

            "thorns" -> {
                for (k in 0 until 50) {
                    val x = 40 + hash(k, 1) % 80
                    val y = 30 + hash(k, 2) % 30
                    p.r(x, y, 6, 1, c(0xB8C8D8))
                    p.px(x + 2, y - 1, c(0xE0E8F0))
                }
                if ((t / 4) % 2 == 0) p.px(80, 40, c(0xFFFFFF))
            }

            "bridge" -> {
                p.r(30, 52, 100, 4, c(0x7A7268))
                for (k in 0 until 6) p.r(34 + k * 18, 56, 4, 12, c(0x6A6258))
                p.r(30, 50, 100, 2, c(0x8A8278))
                creature(p, "troll", 70, 54, t)
            }

            "snowshelf" -> {
                p.r(0, 10, W, 8, c(0xF4F8FB))
                for (k in 0 until 20) p.r(k * 8, 18, 4, 2 + hash(k, 1) % 3, c(0xE0EAF2))
                if ((t / 5) % 6 == 0) p.px(hash(t, 2) % W, 20 + (t % 10), c(0xFFFFFF))
            }

            "junkwall" -> {
                p.r(50, 44, 70, 14, c(0x6A4A2A))
                p.blob(64, 42, 8, 4, c(0xD8D8D8))
                p.r(84, 36, 10, 8, c(0x8A6A3A))
                p.r(100, 40, 14, 6, c(0x5A3A22))
                for (k in 0 until 4) creature(p, "goblin", 54 + k * 18, 32 + (if ((t / 4 + k) % 2 == 0) 0 else 2), t)
            }

            "ropebridge" -> {
                p.r(0, 50, 30, 40, c(0x6C645A))
                p.r(130, 50, 30, 40, c(0x6C645A))
                p.r(30, 50, 100, 40, c(0x1A1A2A))
                for (x in 30 until 130) {
                    val y = 50 + (sin((x - 30) * Math.PI / 100) * 6).toInt()
                    if (x % 4 != 0 || x in 60..70) p.px(x, y, c(0x8A6A42))
                    p.px(x, y - 4, c(0xC8A870))
                }
            }

            "spores" -> {
                for (k in 0 until 40) {
                    val x = (hash(k, 1) % W + t) % W
                    val y = 20 + (hash(k, 2) % 40) + (sin((t + k) / 5.0) * 3).toInt()
                    p.px(x, y, c(listOf(0xFFB0F0, 0xB0F0FF, 0xFFF2A0)[k % 3]))
                }
            }

            "wisps" -> {
                wisp(p, 50, 34, t, 0x8AFF8A)
                wisp(p, 80, 28, t + 5, 0x6AC8FF)
                wisp(p, 110, 36, t + 9, 0xFFD040)
            }

            "lavariver" -> {
                for (y in 56 until 68) {
                    p.r(0, y, W, 1, c(if ((y + t / 3) % 3 == 0) 0xFFB040 else 0xE0662A))
                }
                p.r(60, 52, 40, 4, c(0x4A3C3A))
                p.r(76, 52, 4, 4, c(0x1A1212))
                creature(p, "salamander", 30, 58, t)
            }

            "sparkles" -> {
                for (k in 0 until 24) {
                    val x = hash(k, 1) % W
                    val y = 10 + hash(k, 2) % 50
                    if ((t / 2 + k) % 4 == 0) {
                        p.px(x, y, c(0xFFFFFF))
                        p.px(x - 1, y, c(0xC8A0FF))
                        p.px(x + 1, y, c(0xC8A0FF))
                        p.px(x, y - 1, c(0xC8A0FF))
                        p.px(x, y + 1, c(0xC8A0FF))
                    }
                }
            }

            "cavemouth" -> {
                p.blob(80, 46, 24, 18, c(0x5A544C))
                p.blob(80, 50, 14, 12, c(0x1A1614))
            }
        }
    }

    private fun statue(
        p: P,
        x: Int,
        y: Int,
    ) {
        val s = c(0x8A8278)
        p.r(x + 4, y, 8, 8, s)
        p.r(x + 2, y + 8, 12, 18, s)
        p.r(x + 4, y + 6, 8, 10, c(0x9A9288))
        p.r(x, y + 26, 16, 4, c(0x7A7268))
    }

    private fun wisp(
        p: P,
        x: Int,
        y: Int,
        t: Int,
        col: Int,
    ) {
        val yy = y + (sin(t / 4.0) * 3).toInt()
        val xx = x + (sin(t / 7.0) * 4).toInt()
        p.blob(xx, yy, 3, 3, c(EmberArt.mix(c(col), c(0x000000), 0.4f)))
        p.blob(xx, yy, 2, 2, c(col))
        p.px(xx, yy, c(0xFFFFFF))
    }

    private fun egg(
        p: P,
        x: Int,
        y: Int,
        t: Int,
        cold: Boolean,
    ) {
        val base = if (cold) 0x6A8AB0 else 0xC83A2A
        p.blob(x, y, 5, 7, c(base))
        p.blob(x - 1, y - 2, 2, 3, c(if (cold) 0xA0C0E0 else 0xF28A4A))
        if (!cold && (t / 6) % 4 == 0) p.px(x + 2, y + 2, c(0xFFD040))
        p.r(x - 3, y + 1, 6, 1, c(if (cold) 0x4A6A90 else 0xA82A1A))
    }

    private fun rider(
        p: P,
        x: Int,
        y: Int,
        t: Int,
    ) {
        val step = (t / 3) % 2
        p.r(x, y, 12, 5, c(0x4A3A30))
        p.r(x + 10, y - 3, 4, 4, c(0x4A3A30))
        p.r(x + 1, y + 5, 2, 4 - step, c(0x3A2A20))
        p.r(x + 9, y + 5, 2, 3 + step, c(0x3A2A20))
        p.r(x + 4, y - 7, 5, 8, c(0x8A8A92))
        p.r(x + 5, y - 10, 3, 3, c(0xC8A88A))
        p.r(x + 4, y - 11, 5, 2, c(0x6A6A72))
    }

    // -------------------------------------------------------------------------------------------
    // The cart and party

    private fun cart(
        p: P,
        t: Int,
        o: SceneOpts,
    ) {
        val bob = (t / 3) % 2
        val x = 30
        val y = 52 + bob
        when (o.cartLook) {
            "snail" -> {
                p.blob(x + 12, y + 2, 12, 9, c(0xC88A4A))
                p.blob(x + 12, y + 2, 7, 6, c(0xE0A860))
                p.blob(x + 12, y + 2, 3, 3, c(0xA86A3A))
                p.r(x - 4, y + 8, 34, 4, c(0x8ABA6A))
                p.r(x + 26, y + 2, 4, 8, c(0x8ABA6A))
                p.r(x + 27, y - 2, 1, 4, c(0x8ABA6A))
                p.r(x + 29, y - 2, 1, 4, c(0x8ABA6A))
            }

            else -> {
                val cover = if (o.cartLook == "royal") 0xE0C060 else 0xE9E1C7
                p.r(x, y + 2, 26, 8, c(0x7A5230))
                p.r(x, y + 2, 26, 1, c(0x9A6A40))
                p.r(x + 1, y - 6, 24, 8, c(cover))
                for (k in 0 until 4) p.r(x + 1 + k * 6, y - 7, 5, 1, c(cover))
                p.r(x + 1, y - 6, 24, 1, c(EmberArt.mix(c(cover), c(0x000000), 0.15f)))
                if (o.egg && !o.hatched) p.blob(x + 13, y - 1, 2, 3, c(0xC83A2A))
                val spin = (t / 2) % 2
                for (wx in listOf(x + 3, x + 19)) {
                    p.blob(wx + 3, y + 12, 3, 3, c(0x3A2A1A))
                    p.r(wx + 2 + spin, y + 11, 2, 2, c(0x9A7A4A))
                }
                // horse
                p.r(x + 34, y + 2, 12, 6, c(0x8A5A3A))
                p.r(x + 44, y - 2, 4, 6, c(0x8A5A3A))
                p.r(x + 46, y - 3, 2, 2, c(0x5A3A22))
                val leg = (t / 2) % 2
                p.r(x + 35, y + 8, 2, 5 - leg, c(0x6A4026))
                p.r(x + 43, y + 8, 2, 4 + leg, c(0x6A4026))
                p.r(x + 26, y + 6, 8, 1, c(0x3A2A1A))
            }
        }
        // party walking behind
        val skin = listOf(0xF1C9A0, 0xC9905A, 0xE0B890, 0x8A5A3A)
        val clothes = listOf(0xE50914, 0x3D6EA8, 0x2F7A3A, 0x6B3FA0, 0xC08A2A, 0x2A8A8A)
        for (k in 0 until min(o.party, 6)) {
            val px = x - 8 - k * 7
            val step = ((t / 3) + k) % 2
            p.r(px, y + 1, 4, 6, c(clothes[k % clothes.size]))
            p.r(px, y - 2, 4, 3, c(skin[k % skin.size]))
            p.r(px, y - 3, 4, 1, c(0x3A2A1A))
            p.r(px, y + 7, 1, 3 + step, c(0x2A2A2A))
            p.r(px + 3, y + 7, 1, 4 - step, c(0x2A2A2A))
        }
        if (o.pet) {
            val px = x - 8 - min(o.party, 6) * 7
            p.r(px, y + 6, 5, 3, c(0xC88A4A))
            p.r(px + 4, y + 4, 2, 3, c(0xC88A4A))
            p.r(px, y + 9, 1, 1 + (t / 2) % 2, c(0x8A5A2A))
        }
        if (o.hatched) creature(p, "babydragon", x + 6, y - 18 + (sin(t / 3.0) * 2).toInt(), t)
    }

    // -------------------------------------------------------------------------------------------
    // Creatures (also drawn in the creature book)

    fun creature(
        sink: PixelSink,
        art: String,
        x: Int,
        y: Int,
        t: Int,
    ) = creature(P(sink), art, x, y, t)

    private fun creature(
        p: P,
        art: String,
        x: Int,
        y: Int,
        t: Int,
    ) {
        val b = (t / 4) % 2
        when (art) {
            "dragon" -> {
                bigDragon(p, x - 40, y - 10, t)
            }

            "babydragon" -> {
                p.sprite(
                    x,
                    y + b,
                    listOf(
                        "..w.....",
                        ".wrr....",
                        "wrrrr.rr",
                        ".rrrrrrk",
                        "..rrrryy",
                        "..r..r..",
                    ),
                    mapOf('r' to c(0xD8402A), 'w' to c(0xF28A4A), 'k' to c(0x1A1A1A), 'y' to c(0xF2C05A)),
                    scale = 2,
                )
            }

            "troll" -> {
                p.sprite(
                    x,
                    y - 20 + b,
                    listOf(
                        "...gggg...",
                        "..gggggg..",
                        "..gwgwgg..",
                        "..gggggg..",
                        "...gttg...",
                        ".gggggggg.",
                        "gggggggggg",
                        "g.gggggg.g",
                        "..gg..gg..",
                        "..bb..bb..",
                    ),
                    mapOf('g' to c(0x6A8A5A), 'w' to c(0xF2F2C0), 't' to c(0xF2F2F2), 'b' to c(0x4A3A2A)),
                    scale = 2,
                )
            }

            "wisp" -> {
                wisp(p, x + 6, y + 6, t, 0x6AC8FF)
            }

            "goblin" -> {
                p.sprite(
                    x,
                    y - 12 + b,
                    listOf(
                        "g.gggg.g",
                        "gggggggg",
                        ".gyggyg.",
                        "..gggg..",
                        ".brrrrb.",
                        ".brrrrb.",
                        "..g..g..",
                    ),
                    mapOf('g' to c(0x7AA040), 'y' to c(0xF2E05A), 'r' to c(0x8A3A3A), 'b' to c(0x6A4A2A)),
                    scale = 2,
                )
            }

            "giant" -> {
                p.sprite(
                    x,
                    y - 26,
                    listOf(
                        "...wwww...",
                        "..wssssw..",
                        "..sbsbss..",
                        "..wwwwww..",
                        ".wwwwwwww.",
                        "bbbbbbbbbb",
                        "bbbbbbbbbb",
                        ".bb....bb.",
                        ".dd....dd.",
                    ),
                    mapOf('w' to c(0xF2F6FA), 's' to c(0xA8C0D8), 'b' to c(0x5A7A9A), 'd' to c(0x3A4A5A)),
                    scale = 3,
                )
            }

            "griffin" -> {
                val flap = (t / 3) % 2 == 0
                p.sprite(
                    x,
                    y + b,
                    listOf(
                        if (flap) "ww......ww" else "..........",
                        if (flap) ".ww....ww." else "ww......ww",
                        "..wwhhww..",
                        "...hhhhy..",
                        "..ggggg...",
                        "..g...g...",
                    ),
                    mapOf('w' to c(0xE0D0B0), 'h' to c(0xF2F2E8), 'y' to c(0xF2C040), 'g' to c(0xC89A4A)),
                    scale = 2,
                )
            }

            "sprite" -> {
                p.px(x + 4, y + 4 + b, c(0xFFF2A0))
                p.r(x + 3, y + 5 + b, 3, 3, c(0x8AE0A0))
                p.px(x + 1, y + 4, c(0xA0E8FF))
                p.px(x + 7, y + 4, c(0xA0E8FF))
                p.r(x, y + 3, 2, 2, c(0xC0F0FF))
                p.r(x + 7, y + 3, 2, 2, c(0xC0F0FF))
            }

            "frostfox" -> {
                p.sprite(
                    x,
                    y + b,
                    listOf(
                        "w...w...",
                        "ww.ww...",
                        "wkwkw...",
                        ".www.www",
                        ".wwwwwww",
                        ".w.w.ww.",
                    ),
                    mapOf('w' to c(0xF2F6FA), 'k' to c(0x2A2A3A)),
                    scale = 2,
                )
            }

            "toad" -> {
                p.sprite(
                    x,
                    y + b,
                    listOf(
                        ".yy..yy.",
                        "gkggggkg",
                        "gggggggg",
                        "gggppggg",
                        ".gggggg.",
                        "gg....gg",
                    ),
                    mapOf('g' to c(0x5A7A3A), 'y' to c(0xE8E030), 'k' to c(0x1A1A10), 'p' to c(0xC87A8A)),
                    scale = 3,
                )
            }

            "wolf" -> {
                p.sprite(
                    x,
                    y + b,
                    listOf(
                        "g.g.......",
                        "ggg.......",
                        "gyggggggg.",
                        "ggggggggggg",
                        ".gggggggg.",
                        ".g.g..g.g.",
                    ),
                    mapOf('g' to c(0x4A4A58), 'y' to c(0xE8E030)),
                    scale = 2,
                )
            }

            "serpent" -> {
                for (k in 0 until 10) {
                    val yy = y + 8 + (sin((t + k * 4) / 4.0) * 3).toInt()
                    p.r(x + k * 2, yy, 3, 3, c(0xA8B8C8))
                }
                p.r(x + 20, y + 4, 4, 4, c(0xB8C8D8))
                p.px(x + 22, y + 5, c(0xE01A1A))
            }

            "crab" -> {
                p.blob(x + 8, y + 6, 7, 4, c(0x8A7E70))
                p.r(x, y + 2 - b, 3, 3, c(0xA84A3A))
                p.r(x + 14, y + 2 - b, 3, 3, c(0xA84A3A))
                p.px(x + 6, y + 3, c(0x1A1A1A))
                p.px(x + 10, y + 3, c(0x1A1A1A))
                for (k in 0 until 3) {
                    p.px(x + 3 + k * 2, y + 10, c(0x6A5E50))
                    p.px(x + 10 + k * 2, y + 10, c(0x6A5E50))
                }
            }

            "whale" -> {
                p.blob(x + 12, y + 6, 14, 5, c(0x6A8AB8))
                p.blob(x + 12, y + 8, 12, 3, c(0xC8D8E8))
                p.r(x - 4, y + 2, 4, 4, c(0x6A8AB8))
                p.px(x + 20, y + 5, c(0x1A1A2A))
                p.r(x + 8, y, 6, 2, c(0x5E9E4E))
            }

            "golem" -> {
                p.sprite(
                    x,
                    y - 16,
                    listOf(
                        "..ssss..",
                        "..sbbs..",
                        "ssssssss",
                        "smssssms",
                        "s.ssss.s",
                        "..s..s..",
                        ".ss..ss.",
                    ),
                    mapOf('s' to c(0x7A8A7A), 'b' to c(0x6AC8FF), 'm' to c(0x5E8E4E)),
                    scale = 3,
                )
            }

            "moth" -> {
                val f = (t / 2) % 2 == 0
                p.r(x + 3, y + 2, 2, 5, c(0x6A5A4A))
                p.r(x, y + (if (f) 1 else 3), 3, 3, c(0xFFE08A))
                p.r(x + 5, y + (if (f) 1 else 3), 3, 3, c(0xFFE08A))
            }

            "thornling" -> {
                p.r(x + 3, y, 2, 8, c(0x5A4A2A))
                p.r(x, y + 3, 8, 1, c(0x5A4A2A))
                p.px(x + 3, y + 1, c(0xE8E030))
                p.r(x + 2, y + 8, 1, 2, c(0x5A4A2A))
                p.r(x + 5, y + 8, 1, 2 - b, c(0x5A4A2A))
                p.px(x + 7, y + 2, c(0x8ABA4A))
            }

            "ghost" -> {
                p.blob(x + 5, y + 4 + b, 5, 5, c(0xD8E0F0))
                p.r(x, y + 6 + b, 11, 5, c(0xD8E0F0))
                for (k in 0 until 3) p.px(x + 1 + k * 4, y + 11 + b, c(0xD8E0F0))
                p.px(x + 3, y + 3 + b, c(0x2A2A3A))
                p.px(x + 7, y + 3 + b, c(0x2A2A3A))
            }

            "salamander" -> {
                p.r(x, y, 10, 3, c(0xFF8A2A))
                p.r(x + 10, y - 1, 3, 3, c(0xFF8A2A))
                p.px(x + 11, y - 1, c(0x1A1A1A))
                p.r(x - 4, y + 1, 4, 1, c(0xE0662A))
                p.px(x + 2, y + 3, c(0xE0662A))
                p.px(x + 8, y + 3, c(0xE0662A))
            }

            "mushroomfolk" -> {
                p.blob(x + 5, y + 2, 6, 3, c(0xC44A4A))
                p.px(x + 3, y + 1, c(0xFFFFFF))
                p.px(x + 7, y + 2, c(0xFFFFFF))
                p.r(x + 3, y + 4, 5, 6, c(0xE8DCC8))
                p.px(x + 4, y + 6, c(0x1A1A1A))
                p.px(x + 6, y + 6, c(0x1A1A1A))
                p.r(x + 3, y + 10, 1, 2 + b, c(0xC8BCA8))
                p.r(x + 7, y + 10, 1, 3 - b, c(0xC8BCA8))
            }

            "hunter" -> {
                rider(p, x, y + 6, t)
            }

            else -> {
                p.blob(x + 5, y + 5, 5, 5, c(0xA0A0A0))
            }
        }
    }

    private fun bigDragon(
        p: P,
        x: Int,
        y: Int,
        t: Int,
        still: Boolean = false,
    ) {
        val up = !still && (t / 4) % 2 == 0
        val red = c(0xB8281E)
        val dark = c(0x7A1814)
        val belly = c(0xF2A84A)
        // body
        p.r(x + 10, y + 14, 30, 8, red)
        p.r(x + 12, y + 20, 26, 3, belly)
        // neck and head
        p.r(x + 38, y + 10, 6, 8, red)
        p.r(x + 42, y + 6, 12, 7, red)
        p.r(x + 52, y + 9, 4, 3, red)
        p.px(x + 47, y + 8, c(0xFFE040))
        p.r(x + 44, y + 3, 2, 3, dark)
        p.r(x + 48, y + 3, 2, 3, dark)
        // tail
        for (k in 0 until 12) p.r(x + 10 - k, y + 16 + k / 3, 2, 3, red)
        p.r(x - 4, y + 18, 3, 3, dark)
        // wings
        if (up) {
            for (row in 0 until 12) p.r(x + 16 + row / 2, y + 2 + row - 12 + 12, 18 - row, 1, dark)
            for (row in 0 until 12) p.r(x + 16 + row, y + 2 - row + 10, 14 - row, 1, red)
        } else {
            for (row in 0 until 12) p.r(x + 16 + row / 2, y + 20 + row, 18 - row, 1, dark)
        }
        // legs
        p.r(x + 14, y + 22, 3, 4, dark)
        p.r(x + 32, y + 22, 3, 4, dark)
    }

    // -------------------------------------------------------------------------------------------
    // Weather

    private fun weather(
        p: P,
        w: String,
        t: Int,
        sky: Sky,
    ) {
        when (w) {
            "rain", "storm" -> {
                for (k in 0 until 50) {
                    val x = (hash(k, 1) % W + t * 2) % W
                    val y = (hash(k, 2) % H + t * 5) % H
                    p.r(x, y, 1, 3, 0xAA9FC8E8.toInt())
                }
                if (w == "storm" && t % 40 < 2) p.r(0, 0, W, H, 0x55FFFFFF)
            }

            "snow" -> {
                for (k in 0 until 40) {
                    val x = (hash(k, 1) % W + (sin((t + k) / 6.0) * 3).toInt() + W) % W
                    val y = (hash(k, 2) % H + t) % H
                    p.px(x, y, c(0xFFFFFF))
                }
            }

            "fog" -> {
                for (k in 0 until 4) {
                    val y = 30 + k * 12
                    p.r(0, y, W, 6, 0x55D8DEE4)
                    p.r((t + k * 40) % W, y + 2, 40, 3, 0x44E8ECF0)
                }
            }

            "hot" -> {
                if (sky != Sky.FIRE) p.r(0, 0, W, H, 0x18FFB040)
            }
        }
    }

    // -------------------------------------------------------------------------------------------
    // Portraits for the party (16 x 16)

    fun portrait(
        sink: PixelSink,
        folk: String,
        face: Int,
        x: Int,
        y: Int,
        hurt: Boolean = false,
    ) {
        val p = P(sink)
        val skins = listOf(0xF1C9A0, 0xE0B08A, 0xC9905A, 0x8A5A3A, 0xF4D8C0, 0xB07A50)
        val hairs = listOf(0x3A2A1A, 0xE0C060, 0xA8432A, 0x1A1A1A, 0x8A6A4A, 0xD8D8E0, 0x6B3FA0, 0x2A4A8A)
        val skin = c(if (folk == "fox") 0xD8803A else skins[face % skins.size])
        val hair = c(if (folk == "elf") listOf(0xF2E8C0, 0xD8D8E0, 0x2A2A2A, 0xE0A060)[face % 4] else hairs[(face * 3 + 1) % hairs.size])
        val shirt = c(listOf(0xE50914, 0x3D6EA8, 0x2F7A3A, 0x6B3FA0, 0xC08A2A, 0x2A8A8A, 0x8A3A5A, 0x4A4A5A)[face % 8])
        p.r(x, y, 16, 16, c(0x231C2C))
        p.r(x + 2, y + 12, 12, 4, shirt)
        p.r(x + 4, y + 3, 8, 9, skin)
        p.r(x + 4, y + 2, 8, 2, hair)
        p.r(x + 3, y + 3, 1, 5, hair)
        p.r(x + 12, y + 3, 1, 5, hair)
        if (face % 3 == 0 && folk != "dwarf") p.r(x + 3, y + 3, 1, 8, hair)
        p.px(x + 6, y + 6, c(0x1A1A1A))
        p.px(x + 9, y + 6, c(0x1A1A1A))
        p.r(x + 7, y + 9, 2, 1, c(if (hurt) 0x6A3A3A else 0xA84A4A))
        when (folk) {
            "elf" -> {
                p.r(x + 2, y + 5, 2, 1, skin)
                p.px(x + 1, y + 4, skin)
                p.r(x + 12, y + 5, 2, 1, skin)
                p.px(x + 14, y + 4, skin)
            }

            "dwarf" -> {
                val beard = c(listOf(0xA8432A, 0x8A6A4A, 0xE0C060, 0xD8D8E0)[face % 4])
                p.r(x + 4, y + 9, 8, 5, beard)
                p.r(x + 5, y + 14, 6, 1, beard)
                p.r(x + 7, y + 9, 2, 1, c(0xA84A4A))
                p.r(x + 3, y + 1, 10, 2, c(0x8A8A92))
            }

            "fox" -> {
                p.r(x + 3, y, 3, 3, skin)
                p.r(x + 10, y, 3, 3, skin)
                p.px(x + 4, y + 1, c(0xF2E0D0))
                p.px(x + 11, y + 1, c(0xF2E0D0))
                p.r(x + 6, y + 8, 4, 3, c(0xF2E8DC))
                p.px(x + 7, y + 8, c(0x1A1A1A))
            }
        }
        if (hurt) p.r(x + 10, y + 3, 3, 1, c(0xF2F2F2))
    }

    fun petPortrait(
        sink: PixelSink,
        id: String,
        x: Int,
        y: Int,
    ) {
        val p = P(sink)
        p.r(x, y, 16, 16, c(0x231C2C))
        when (id) {
            "owl" -> {
                p.blob(x + 8, y + 9, 5, 6, c(0x8A6A4A))
                p.blob(x + 6, y + 7, 2, 2, c(0xF2F2E0))
                p.blob(x + 10, y + 7, 2, 2, c(0xF2F2E0))
                p.px(x + 6, y + 7, c(0x1A1A1A))
                p.px(x + 10, y + 7, c(0x1A1A1A))
                p.px(x + 8, y + 9, c(0xF2C040))
                p.r(x + 4, y + 2, 2, 3, c(0x8A6A4A))
                p.r(x + 11, y + 2, 2, 3, c(0x8A6A4A))
            }

            "mushdog" -> {
                p.blob(x + 8, y + 5, 7, 3, c(0xC44A4A))
                p.px(x + 5, y + 4, c(0xFFFFFF))
                p.px(x + 10, y + 3, c(0xFFFFFF))
                p.r(x + 4, y + 8, 9, 6, c(0xC8A070))
                p.px(x + 6, y + 10, c(0x1A1A1A))
                p.px(x + 10, y + 10, c(0x1A1A1A))
                p.r(x + 7, y + 12, 3, 1, c(0x3A2A1A))
            }

            "cat" -> {
                p.r(x + 3, y + 4, 10, 9, c(0xE08A3A))
                p.r(x + 3, y + 2, 3, 2, c(0xE08A3A))
                p.r(x + 10, y + 2, 3, 2, c(0xE08A3A))
                p.r(x + 5, y + 7, 2, 1, c(0x3A8A3A))
                p.r(x + 9, y + 7, 2, 1, c(0x3A8A3A))
                p.px(x + 8, y + 9, c(0xE07A8A))
                p.r(x + 4, y + 6, 8, 1, c(0xC86A2A))
            }

            "toad" -> {
                p.blob(x + 8, y + 10, 6, 4, c(0x5A8A3A))
                p.r(x + 4, y + 3, 8, 3, c(0x9A9AA8))
                p.r(x + 5, y + 6, 2, 2, c(0xE8E030))
                p.r(x + 9, y + 6, 2, 2, c(0xE8E030))
                p.r(x + 6, y + 12, 4, 1, c(0x2A3A1A))
            }
        }
    }

    /** The big pixel title "EMBER ROAD" is drawn by the UI font; this draws the little egg logo */
    fun eggLogo(
        sink: PixelSink,
        x: Int,
        y: Int,
        t: Int,
    ) = egg(P(sink), x, y, t, false)
}
