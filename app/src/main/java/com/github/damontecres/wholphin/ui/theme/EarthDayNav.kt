package com.github.damontecres.wholphin.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import com.github.damontecres.wholphin.ui.main.HomeTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * Earth Day in the new navigation (approved by Ben 2026-11-17, sheet "Earth Day, put together"): an ocean blue rail
 * whose icons turn into Earth Day icons (each door keeps its shape with its own living thing worked in: a sprout on
 * Home and Movies, a monarch on the Shows TV, a little Earth in the Live TV signal, a ladybird on the Games
 * controller, a leaf on the Music note, a pressed daisy on the Books cover), in sea-to-land colours. Home gets
 * clouds and birds along the top and a short meadow (grass, flowers and a bee) along the bottom, low enough to stay
 * under the Today cards. Everything moves gently. Kids get it too. The web's twin is the earthday entry in
 * js/holidaynav.js.
 */
object EarthDayNav {
    /** Sea to land: Home, then the doors in order (Movies, Shows, Live TV, Games, Music, Books) */
    private val HOME = Color(0xFFA8DCF7)
    private val DOORS =
        listOf(0xFF7FC8F2, 0xFF56BDEB, 0xFF7CD36B, 0xFF8FD86F, 0xFFB5E37A, 0xFFE8F5A0).map { Color(it) }

    val entry: HolidayNav
        get() =
            HolidayNav(
                key = "earthday",
                isOn = { EdLook.on && !EaLook.on },
                railTop = Color(0xFF0E2C44),
                railBottom = Color(0xFF0B1A26),
                lights = DOORS,
                icons = ICON_KEYS.associateWith { key -> icon(key) },
                topTrim = { t -> sky(t) },
                bottomTrim = { t -> meadow(t) },
                moving = true,
                countdown = {
                    EarthDaySeason.day().takeIf { it > 0 }?.let {
                        "Day $it of ${EarthDaySeason.DAYS} · Planet Earth picks on Movies and Shows"
                    }
                },
                countdownKind = "Earth week",
                pickWhy = "Earth Day pick",
                pickTitles = HomeTheme.EARTH_DAY.titles,
                accent = Color(0xFF7CD36B),
            )

    // ---------- the rail icons ----------

    private val ICON_KEYS = listOf("home", "movies", "shows", "live", "games", "music", "books")

    /** The icon's own colour: Home's, or its door's */
    private fun earthColor(key: String): Color =
        when (key) {
            "home" -> HOME
            else -> DOORS[(ICON_KEYS.indexOf(key) - 1).coerceIn(0, DOORS.size - 1)]
        }

    /** True for the dim grey the rail gives icons before they light up at start-up */
    private fun isGrey(c: Color): Boolean = abs(c.red - c.green) < .07f && abs(c.green - c.blue) < .07f && c.red < .7f

    private fun icon(key: String): HolidayIconArt {
        val parts = PARTS.getValue(key)
        val own = earthColor(key)
        return { color, _ ->
            // In the Earth Day colours, except while the rail is still dark at start-up
            val outline = if (isGrey(color)) color else own.copy(alpha = color.alpha)
            val k = size.minDimension / 24f
            scale(k, k, pivot = Offset.Zero) {
                parts.forEach { p -> drawPart(p, outline) }
            }
        }
    }

    private fun DrawScope.drawPart(
        p: EdPart,
        outline: Color,
    ) {
        val draw: DrawScope.() -> Unit = {
            p.fill?.let { drawPath(p.path, Color(it)) }
            p.stroke?.let {
                drawPath(
                    p.path,
                    if (it == ICON) outline else Color(it),
                    style = Stroke(width = p.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
        when {
            p.tx != 0f || p.ty != 0f -> translate(p.tx, p.ty) { rotate(p.rot, pivot = Offset.Zero) { draw() } }
            p.rot != 0f -> rotate(p.rot, pivot = Offset(p.px, p.py)) { draw() }
            else -> draw()
        }
    }

    /** One stroke or fill of an icon, in a 24 by 24 box (the same drawing as the web's SVG) */
    private class EdPart(
        d: String,
        val stroke: Long?,
        val fill: Long?,
        val width: Float,
        val rot: Float = 0f,
        val tx: Float = 0f,
        val ty: Float = 0f,
        val px: Float = 0f,
        val py: Float = 0f,
    ) {
        val path: Path = PathParser().parsePathString(d).toPath()
    }

    /** Stands for "the icon's own colour" in [EdPart.stroke] */
    private const val ICON = 1L

    private val PARTS: Map<String, List<EdPart>> by lazy {
        mapOf(
            "home" to
                listOf(
                    EdPart("M3 11l9-7 9 7", stroke = ICON, fill = null, width = 1.8f),
                    EdPart("M5 10v10h14V10", stroke = ICON, fill = null, width = 1.8f),
                    EdPart("M12 20v-3.75", stroke = 0xFF5DB64E, fill = null, width = 1.6f),
                    EdPart("M12 16.75c-3.25 0 -4.5 -2 -4.5 -4.25 2.75 0 4.5 1.5 4.5 4.25z", stroke = null, fill = 0xFF7CD36B, width = 1.8f),
                    EdPart("M12 16.75c3.25 0 4.5 -2 4.5 -4.25 -2.75 0 -4.5 1.5 -4.5 4.25z", stroke = null, fill = 0xFF4FA846, width = 1.8f),
                ),
            "movies" to
                listOf(
                    EdPart(
                        "M4.8 9H19.2A1.8 1.8 0 0 1 21 10.8V18.2A1.8 1.8 0 0 1 19.2 20H4.8A1.8 1.8 0 0 1 3 18.2V10.8A1.8 1.8 0 0 1 4.8 9Z",
                        stroke = ICON,
                        fill = null,
                        width = 1.8f,
                    ),
                    EdPart("M3 9l16.5 -4.2 0.8 2.9L4 12", stroke = ICON, fill = null, width = 1.8f),
                    EdPart("M8.5 7.6l2 2.6M13.5 6.3l2 2.6", stroke = ICON, fill = null, width = 1.8f),
                    EdPart("M12 18.5v-3", stroke = 0xFF5DB64E, fill = null, width = 1.6f),
                    EdPart("M12 15.9c-2.6 0 -3.6 -1.6 -3.6 -3.4 2.2 0 3.6 1.2 3.6 3.4z", stroke = null, fill = 0xFF7CD36B, width = 1.8f),
                    EdPart("M12 15.9c2.6 0 3.6 -1.6 3.6 -3.4 -2.2 0 -3.6 1.2 -3.6 3.4z", stroke = null, fill = 0xFF4FA846, width = 1.8f),
                ),
            "shows" to
                listOf(
                    EdPart(
                        "M4.5 7H17.5A2 2 0 0 1 19.5 9V17A2 2 0 0 1 17.5 19H4.5A2 2 0 0 1 2.5 17V9A2 2 0 0 1 4.5 7Z",
                        stroke = ICON,
                        fill = null,
                        width = 1.8f,
                    ),
                    EdPart("M7 22h8", stroke = ICON, fill = null, width = 1.8f),
                    EdPart(
                        "M0 0C-1 -4 -5 -5 -5.5 -2C-5.6 0 -3 1 0 0z",
                        stroke = null,
                        fill = 0xFFF08A24,
                        width = 1.8f,
                        rot = 15f,
                        tx = 18.5f,
                        ty = 6.2f,
                    ),
                    EdPart(
                        "M0 0C1 -4 5 -5 5.5 -2C5.6 0 3 1 0 0z",
                        stroke = null,
                        fill = 0xFFF08A24,
                        width = 1.8f,
                        rot = 15f,
                        tx = 18.5f,
                        ty = 6.2f,
                    ),
                    EdPart("M0 0C-1 2.5 -4 3.4 -4 1.4z", stroke = null, fill = 0xFFE06A10, width = 1.8f, rot = 15f, tx = 18.5f, ty = 6.2f),
                    EdPart("M0 0C1 2.5 4 3.4 4 1.4z", stroke = null, fill = 0xFFE06A10, width = 1.8f, rot = 15f, tx = 18.5f, ty = 6.2f),
                    EdPart("M0 -2.5V2", stroke = 0xFF2A1A10, fill = null, width = 1f, rot = 15f, tx = 18.5f, ty = 6.2f),
                ),
            "live" to
                listOf(
                    EdPart(
                        "M7.8 7.8a6 6 0 0 0 0 8.4M16.2 7.8a6 6 0 0 1 0 8.4M4.6 4.6a10.5 10.5 0 0 0 0 14.8M19.4 4.6a10.5 10.5 0 0 1 0 14.8",
                        stroke = ICON,
                        fill = null,
                        width = 1.8f,
                    ),
                    EdPart("M8.8 12A3.2 3.2 0 1 0 15.2 12A3.2 3.2 0 1 0 8.8 12Z", stroke = null, fill = 0xFF2A74AE, width = 1.8f),
                    EdPart(
                        "M10.4 10.6c1-.6 2 0 1.6 1-.5 1 .8 1.4 0.2 2.4-1 .2-2.2-.6-2.3-1.7z",
                        stroke = null,
                        fill = 0xFF7CD36B,
                        width = 1.8f,
                    ),
                ),
            "games" to
                listOf(
                    EdPart(
                        "M7.2 6.8h9.6c2.4 0 4 1.5 4.6 3.9l1 5c.5 2.6-1.1 4.2-3.1 4.2-1.4 0-2.3-.8-3-1.9l-.9-1.4H8.6l-.9 1.4c-.7 1.1-1.6 1.9-3 1.9-2 0-3.6-1.6-3.1-4.2l1-5c.6-2.4 2.2-3.9 4.6-3.9z",
                        stroke = ICON,
                        fill = null,
                        width = 1.8f,
                    ),
                    EdPart("M7 10.4v4M5 12.4h4", stroke = ICON, fill = null, width = 1.8f),
                    EdPart(
                        "M13.799999999999999 12.3A2.6 2.6 0 1 0 19 12.3A2.6 2.6 0 1 0 13.799999999999999 12.3Z",
                        stroke = null,
                        fill = 0xFFE0281E,
                        width = 1.8f,
                    ),
                    EdPart("M16.4 9.8v5", stroke = 0xFF1A1A1A, fill = null, width = 0.8f),
                    EdPart(
                        "M14.85 11.6A0.55 0.55 0 1 0 15.950000000000001 11.6A0.55 0.55 0 1 0 14.85 11.6Z",
                        stroke = null,
                        fill = 0xFF1A1A1A,
                        width = 1.8f,
                    ),
                    EdPart(
                        "M16.95 12.9A0.55 0.55 0 1 0 18.05 12.9A0.55 0.55 0 1 0 16.95 12.9Z",
                        stroke = null,
                        fill = 0xFF1A1A1A,
                        width = 1.8f,
                    ),
                    EdPart(
                        "M15.499999999999998 9.9A0.9 0.9 0 1 0 17.299999999999997 9.9A0.9 0.9 0 1 0 15.499999999999998 9.9Z",
                        stroke = null,
                        fill = 0xFF1A1A1A,
                        width = 1.8f,
                    ),
                ),
            "music" to
                listOf(
                    EdPart("M9 18V5", stroke = ICON, fill = null, width = 1.8f),
                    EdPart("M3 18A3 3 0 1 0 9 18A3 3 0 1 0 3 18Z", stroke = ICON, fill = null, width = 1.8f),
                    EdPart("M9 5c4-1.6 8-1 11 2.2-3.6 1.6-7.8 1.6-11 0z", stroke = null, fill = 0xFF7CD36B, width = 1.8f),
                    EdPart("M9.5 5.6c3 .3 6.2.9 9.6 1.6", stroke = 0xFF2F7A34, fill = null, width = 0.8f),
                ),
            "books" to
                listOf(
                    EdPart("M4 5a2 2 0 0 1 2 -2h13v16H6a2 2 0 0 0 -2 2z", stroke = ICON, fill = null, width = 1.8f),
                    EdPart("M4 21V5", stroke = ICON, fill = null, width = 1.8f),
                    EdPart(
                        "M11.1 7.6A0.9 2.3 0 1 0 12.9 7.6A0.9 2.3 0 1 0 11.1 7.6Z",
                        stroke = null,
                        fill = 0xFFFFFFFF,
                        width = 1.8f,
                        rot = 0f,
                        px = 12f,
                        py = 10.4f,
                    ),
                    EdPart(
                        "M11.1 7.6A0.9 2.3 0 1 0 12.9 7.6A0.9 2.3 0 1 0 11.1 7.6Z",
                        stroke = null,
                        fill = 0xFFFFFFFF,
                        width = 1.8f,
                        rot = 36f,
                        px = 12f,
                        py = 10.4f,
                    ),
                    EdPart(
                        "M11.1 7.6A0.9 2.3 0 1 0 12.9 7.6A0.9 2.3 0 1 0 11.1 7.6Z",
                        stroke = null,
                        fill = 0xFFFFFFFF,
                        width = 1.8f,
                        rot = 72f,
                        px = 12f,
                        py = 10.4f,
                    ),
                    EdPart(
                        "M11.1 7.6A0.9 2.3 0 1 0 12.9 7.6A0.9 2.3 0 1 0 11.1 7.6Z",
                        stroke = null,
                        fill = 0xFFFFFFFF,
                        width = 1.8f,
                        rot = 108f,
                        px = 12f,
                        py = 10.4f,
                    ),
                    EdPart(
                        "M11.1 7.6A0.9 2.3 0 1 0 12.9 7.6A0.9 2.3 0 1 0 11.1 7.6Z",
                        stroke = null,
                        fill = 0xFFFFFFFF,
                        width = 1.8f,
                        rot = 144f,
                        px = 12f,
                        py = 10.4f,
                    ),
                    EdPart(
                        "M11.1 7.6A0.9 2.3 0 1 0 12.9 7.6A0.9 2.3 0 1 0 11.1 7.6Z",
                        stroke = null,
                        fill = 0xFFFFFFFF,
                        width = 1.8f,
                        rot = 180f,
                        px = 12f,
                        py = 10.4f,
                    ),
                    EdPart(
                        "M11.1 7.6A0.9 2.3 0 1 0 12.9 7.6A0.9 2.3 0 1 0 11.1 7.6Z",
                        stroke = null,
                        fill = 0xFFFFFFFF,
                        width = 1.8f,
                        rot = 216f,
                        px = 12f,
                        py = 10.4f,
                    ),
                    EdPart(
                        "M11.1 7.6A0.9 2.3 0 1 0 12.9 7.6A0.9 2.3 0 1 0 11.1 7.6Z",
                        stroke = null,
                        fill = 0xFFFFFFFF,
                        width = 1.8f,
                        rot = 252f,
                        px = 12f,
                        py = 10.4f,
                    ),
                    EdPart(
                        "M11.1 7.6A0.9 2.3 0 1 0 12.9 7.6A0.9 2.3 0 1 0 11.1 7.6Z",
                        stroke = null,
                        fill = 0xFFFFFFFF,
                        width = 1.8f,
                        rot = 288f,
                        px = 12f,
                        py = 10.4f,
                    ),
                    EdPart(
                        "M11.1 7.6A0.9 2.3 0 1 0 12.9 7.6A0.9 2.3 0 1 0 11.1 7.6Z",
                        stroke = null,
                        fill = 0xFFFFFFFF,
                        width = 1.8f,
                        rot = 324f,
                        px = 12f,
                        py = 10.4f,
                    ),
                    EdPart("M10.7 10.4A1.3 1.3 0 1 0 13.3 10.4A1.3 1.3 0 1 0 10.7 10.4Z", stroke = null, fill = 0xFFF4C542, width = 1.8f),
                    EdPart("M12 11.7v5", stroke = 0xFF4FA846, fill = null, width = 1.1f),
                ),
        )
    }

    // ---------- Home's trims ----------

    /** A steady made-up number from 0 to 1 for [i], so the clouds, grass and flowers sit in the same places each time */
    private fun h(i: Int): Float {
        val x = sin(i * 12.9898f + 78.233f) * 43758.547f
        return x - floor(x)
    }

    private val CLOUD = Color(0xFFF2F7FB)
    private val CLOUD_SHADE = Color(0xFFC9D9E6)
    private val BIRD = Color(0xFFE8F3FF)

    /** Top of Home: whole round clouds drifting slowly, and now and then a flock of birds crossing */
    private fun DrawScope.sky(t: Float) {
        val u = size.height / 44f // one "dp" of the 44dp box
        val gap = 230f * u
        val n = (size.width / gap).toInt() + 2
        val drift = sin(t * .05f) * 30f * u
        for (i in 0 until n) {
            val s = (.8f + h(i) * .35f) * u
            val x = i * gap + h(i + 40) * 90f * u + drift - 40f * u
            val y = 8f * u + h(i + 80) * 6f * u
            cloud(x, y, s)
        }
        // the flock takes 18 seconds to cross, then waits a little before the next
        val cross = 18f
        val p = (t % (cross + 6f)) / cross
        if (p <= 1f) {
            val fx = -60f * u + p * (size.width + 120f * u)
            val fy = 26f * u - p * 10f * u
            val flock = listOf(0f to 0f, -16f to -7f, -16f to 7f, -32f to -14f, -32f to 14f)
            flock.forEachIndexed { k, (dx, dy) ->
                val flap = sin(t * 10f + k * 1.1f)
                bird(fx + dx * u, fy + dy * u * .6f, 6.5f * u, flap)
            }
        }
    }

    private fun DrawScope.cloud(
        x: Float,
        y: Float,
        s: Float,
    ) {
        // a soft shade under the bumps, then the white cloud on top: round all the way round, no flat bottom
        listOf(Triple(2f, 14f, 10f), Triple(20f, 15f, 11f), Triple(40f, 15f, 10f), Triple(56f, 13f, 8f)).forEach { (cx, cy, r) ->
            drawCircle(CLOUD_SHADE, r * s, Offset(x + cx * s, y + (cy + 2f) * s))
        }
        listOf(
            Triple(2f, 12f, 10f),
            Triple(18f, 4f, 15f),
            Triple(36f, 6f, 13f),
            Triple(20f, 14f, 10f),
            Triple(40f, 13f, 10f),
            Triple(55f, 12f, 8f),
        ).forEach { (cx, cy, r) -> drawCircle(CLOUD, r * s, Offset(x + cx * s, y + cy * s)) }
    }

    private fun DrawScope.bird(
        x: Float,
        y: Float,
        w: Float,
        flap: Float,
    ) {
        val lift = w * (.35f + flap * .45f)
        val path =
            Path().apply {
                moveTo(x - w, y)
                quadraticTo(x - w / 2, y - lift, x, y)
                quadraticTo(x + w / 2, y - lift, x + w, y)
            }
        drawPath(path, BIRD, style = Stroke(width = w * .28f, cap = StrokeCap.Round))
    }

    private val GRASS = listOf(0xFF2F7A34, 0xFF3E9440, 0xFF4FA846, 0xFF6BC35A).map { Color(it) }
    private val POPPY = Color(0xFFE8453C)
    private val CORN = Color(0xFF4C7BE0)
    private val BUTTER = Color(0xFFFFD23F)
    private val SUN = Color(0xFFF4C542)
    private val STEM = Color(0xFF2F7A34)

    /** Bottom of Home: short meadow grass swaying, with daisies, poppies, cornflowers, buttercups and a bee */
    private fun DrawScope.meadow(t: Float) {
        val u = size.height / 36f
        val bottom = size.height
        val step = 5f * u
        val n = (size.width / step).toInt() + 2
        val blade = Path()
        for (i in 0 until n) {
            val x = i * step
            val tall = (10f + h(i) * 14f) * u
            val lean = (h(i + 500) * 8f - 4f) * u + sin(t * 1.4f + i * .37f) * 2.2f * u
            blade.reset()
            blade.moveTo(x - 2.2f * u, bottom)
            blade.quadraticTo(x + lean * .3f, bottom - tall * .6f, x + lean, bottom - tall)
            blade.quadraticTo(x + 1f * u + lean * .3f, bottom - tall * .5f, x + 2.6f * u, bottom)
            blade.close()
            drawPath(blade, GRASS[(h(i + 900) * GRASS.size).toInt().coerceAtMost(GRASS.size - 1)])
        }
        // flowers stand a little above the grass, every 50 to 80 dp
        var x = 24f * u
        var i = 0
        while (x < size.width) {
            val headY = bottom - (20f + h(i + 1300) * 8f) * u
            val sway = sin(t * 1.2f + i) * 1.5f * u
            drawLine(STEM, Offset(x, bottom), Offset(x + sway, headY), strokeWidth = 1.8f * u)
            flower(i % 4, x + sway, headY, u)
            x += (50f + h(i + 1700) * 30f) * u
            i++
        }
        // the bee drifts along the meadow, bobbing between the flowers
        val p = (t % 24f) / 24f
        val bx = p * size.width
        val by = bottom - (26f + sin(t * 2.2f) * 4f) * u
        val wing = .5f + abs(sin(t * 30f)) * .5f
        drawOval(Color(0xFFE8F6FF).copy(alpha = .85f), Offset(bx - 4f * u, by - 7f * u * wing - 3f * u), Size(7f * u, 6f * u * wing))
        drawOval(SUN, Offset(bx - 6f * u, by - 4f * u), Size(12f * u, 8f * u))
        drawLine(Color(0xFF222222), Offset(bx - 1.5f * u, by - 4f * u), Offset(bx - 1.5f * u, by + 4f * u), strokeWidth = 1.8f * u)
        drawLine(Color(0xFF222222), Offset(bx + 2f * u, by - 3.6f * u), Offset(bx + 2f * u, by + 3.6f * u), strokeWidth = 1.8f * u)
    }

    private fun DrawScope.flower(
        kind: Int,
        x: Float,
        y: Float,
        u: Float,
    ) {
        val c = Offset(x, y)
        when (kind) {
            0 -> { // daisy
                for (k in 0 until 10) {
                    rotate(k * 36f, pivot = c) { drawOval(Color.White, Offset(x - 1.6f * u, y - 7.5f * u), Size(3.2f * u, 6.5f * u)) }
                }
                drawCircle(SUN, 2.8f * u, c)
            }

            1 -> { // poppy
                drawCircle(POPPY, 6.5f * u, c)
                drawCircle(Color(0xFFFF6A55), 4f * u, Offset(x - 1.5f * u, y - 2f * u))
                drawCircle(Color(0xFF2A1A1A), 2.2f * u, c)
            }

            2 -> { // cornflower
                for (k in 0 until 8) {
                    rotate(k * 45f, pivot = c) { drawLine(CORN, c, Offset(x, y - 6.5f * u), strokeWidth = 2.8f * u, cap = StrokeCap.Round) }
                }
                drawCircle(Color(0xFF2B4FA8), 2.2f * u, c)
            }

            else -> { // buttercup
                for (k in 0 until 5) {
                    val a = k * 2 * PI.toFloat() / 5
                    drawCircle(BUTTER, 3f * u, Offset(x + kotlin.math.cos(a) * 3.2f * u, y + sin(a) * 3.2f * u))
                }
                drawCircle(Color(0xFFE89A1A), 1.8f * u, c)
            }
        }
    }
}
