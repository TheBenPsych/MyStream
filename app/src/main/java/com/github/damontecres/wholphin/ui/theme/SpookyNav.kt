package com.github.damontecres.wholphin.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.WholphinApplication
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Halloween and Friday the 13th on the new navigation (approved 2026-11-17, sheets spooky-nav-1 and spooky-nav-2):
 *
 * Halloween ("The charms come along"): the approved door charms become the rail and phone-tab icons (jack-o'-lantern,
 * bat, spider, witch hat, ghost, skeleton key; Kids get the friendly ones), a little spider on its silk bobs beside
 * the icon you're on, cobwebs in the top corners of Home with a string of glowing pumpkin lights across the top, and
 * carved jack-o'-lanterns with autumn leaves along the bottom.
 *
 * Friday the 13th ("Crows on the wire"): the approved lucky charms become the icons (black cat, cracked mirror,
 * horseshoe, dice, moon chime, 13 key), a crow perches beside the icon you're on, a big full moon sits behind a
 * sagging power line with five crows across the top of Home (one hops, one turns round), and a crooked old fence
 * stands in pale mist along the bottom. Night purple and black with green only as a highlight (Ben: no clovers,
 * it looked like St. Patrick's Day).
 *
 * The same drawings are on the web in server/web/js/holidaynav.js. The icon pictures are cut from the door charms
 * with a thin pale outline (Ben: glowing icons were hard to see), see res/drawable-nodpi/hwnav_* and f13nav_*.
 */

private val pathCache = HashMap<String, Path>()

/** An SVG path, read once */
private fun svgPath(d: String): Path = pathCache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

private val bitmapCache = HashMap<Int, ImageBitmap>()

/** One of Benflix's own pictures, loaded once */
private fun bitmap(id: Int): ImageBitmap? =
    bitmapCache[id] ?: runCatching {
        ImageBitmap.imageResource(WholphinApplication.instance.resources, id).also { bitmapCache[id] = it }
    }.getOrNull()

/**
 * A rail icon from a picture: drawn a little bigger than the usual line icon so it reads from the couch, and faded
 * while the start-up light-up hasn't reached it yet (the usual icon's colour is the muted grey then)
 */
private fun pictureIcon(pick: () -> Int): HolidayIconArt =
    { color, _ ->
        val img = bitmap(pick())
        if (img != null) {
            val side = size.minDimension * 1.3f
            val left = (size.width - side) / 2f
            val top = (size.height - side) / 2f
            val dim = color == BenflixColors.Muted
            if (dim) drawIntoCanvas { it.saveLayer(Rect(Offset(left, top), Size(side, side)), Paint().apply { alpha = .35f }) }
            drawImage(
                img,
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(side.roundToInt(), side.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
            if (dim) drawIntoCanvas { it.restore() }
        }
    }

/** A soft bob, 0 to 1 and back, [period] seconds long */
private fun wave(
    t: Float,
    period: Float,
    shift: Float = 0f,
): Float = (sin((t / period + shift) * 2 * PI).toFloat() + 1f) / 2f

object HalloweenNav {
    private val Orange = Color(0xFFF07A1E)
    private val PumpkinLight = Color(0xFFF59A3E)
    private val PumpkinDark = Color(0xFFC85A10)
    private val Stem = Color(0xFF3E6B2A)
    private val Glow = Color(0xFFFFE07A)
    private val Web = Color(0xE6E6E6F0)

    private fun door(
        key: String,
        grown: Int,
        kids: Int,
    ): Pair<String, HolidayIconArt> = key to pictureIcon { if (KidsLook.on) kids else grown }

    /** The approved charms as the rail and phone-tab icons; Kids get the friendly set (approved hn-q-kids A) */
    val icons: Map<String, HolidayIconArt> =
        mapOf(
            door("movies", R.drawable.hwnav_movies, R.drawable.hwnav_kids_movies),
            door("shows", R.drawable.hwnav_shows, R.drawable.hwnav_kids_shows),
            door("live", R.drawable.hwnav_live, R.drawable.hwnav_kids_live),
            door("games", R.drawable.hwnav_games, R.drawable.hwnav_kids_games),
            door("music", R.drawable.hwnav_music, R.drawable.hwnav_kids_music),
            door("books", R.drawable.hwnav_books, R.drawable.hwnav_kids_books),
        )

    /** A little spider on its silk beside the icon you're on, bobbing gently (box 12 x 18) */
    val mark: HolidayArt = { t ->
        val u = size.width / 12f
        val drop = wave(t, 3f) * 4f * u
        val cx = 6f * u
        val cy = 10f * u + drop
        drawLine(Web, Offset(cx, -14f * u), Offset(cx, cy - 2.5f * u), strokeWidth = .5f * u)
        val leg = Color(0xFF1A1420)
        for (s in listOf(-1f, 1f)) {
            for (k in 0..3) {
                val a = cy - 1.5f * u + k * 1.3f * u
                drawLine(
                    leg,
                    Offset(cx + s * 1.5f * u, a),
                    Offset(cx + s * 4.6f * u, a - 1.2f * u + k * .9f * u),
                    strokeWidth = .9f * u,
                    cap = StrokeCap.Round,
                )
            }
        }
        drawOval(leg, Offset(cx - 2.3f * u, cy - 2f * u), Size(4.6f * u, 5.4f * u))
        drawCircle(leg, 1.5f * u, Offset(cx, cy - 2.6f * u))
        drawCircle(Color(0xFFFF8A2A), .45f * u, Offset(cx - .6f * u, cy - 2.8f * u))
        drawCircle(Color(0xFFFF8A2A), .45f * u, Offset(cx + .6f * u, cy - 2.8f * u))
        drawCircle(Color(0xFFC0392B), .7f * u, Offset(cx, cy + .6f * u))
    }

    /** A carved jack-o'-lantern [w] wide centred on [c]; [lit] 0 to 1 is how bright the candle inside is */
    private fun DrawScope.jack(
        c: Offset,
        w: Float,
        lit: Float,
    ) {
        val h = w * .78f
        if (lit > 0f) {
            drawCircle(
                Brush.radialGradient(listOf(Orange.copy(alpha = .45f * lit), Color.Transparent), c, w * .95f),
                w * .95f,
                c,
            )
        }
        drawOval(PumpkinDark, Offset(c.x - w / 2, c.y - h / 2), Size(w, h))
        drawOval(Orange, Offset(c.x - w * .36f, c.y - h * .48f), Size(w * .44f, h * .96f))
        drawOval(PumpkinLight, Offset(c.x - w * .08f, c.y - h * .48f), Size(w * .44f, h * .96f))
        drawOval(Orange, Offset(c.x - w * .14f, c.y - h * .5f), Size(w * .28f, h))
        drawLine(Stem, Offset(c.x, c.y - h * .46f), Offset(c.x + w * .08f, c.y - h * .68f), strokeWidth = w * .09f, cap = StrokeCap.Round)
        val face = Glow.copy(alpha = .55f + .45f * lit)
        val eye = Path()
        for (s in listOf(-1f, 1f)) {
            val ex = c.x + s * w * .2f
            eye.moveTo(ex - w * .09f, c.y - h * .02f)
            eye.lineTo(ex + w * .09f, c.y - h * .02f)
            eye.lineTo(ex, c.y - h * .22f)
            eye.close()
        }
        drawPath(eye, face)
        val mouth = Path()
        mouth.moveTo(c.x - w * .3f, c.y + h * .1f)
        mouth.quadraticTo(c.x, c.y + h * .42f, c.x + w * .3f, c.y + h * .1f)
        mouth.lineTo(c.x + w * .18f, c.y + h * .16f)
        mouth.lineTo(c.x + w * .08f, c.y + h * .1f)
        mouth.lineTo(c.x - w * .02f, c.y + h * .2f)
        mouth.lineTo(c.x - w * .12f, c.y + h * .1f)
        mouth.lineTo(c.x - w * .2f, c.y + h * .18f)
        mouth.close()
        drawPath(mouth, face)
    }

    /** A cobweb in the top-left corner, [r] across (mirror it for the right) */
    private fun DrawScope.cobweb(r: Float) {
        val sw = 1.2f * density
        val spokes = 6
        for (i in 0..spokes) {
            val a = i * (PI / 2) / spokes
            drawLine(
                Web.copy(alpha = .55f),
                Offset.Zero,
                Offset((r * kotlin.math.cos(a)).toFloat(), (r * sin(a)).toFloat()),
                strokeWidth = sw,
            )
        }
        for (ring in 1..3) {
            val rr = r * ring / 3.4f
            val p = Path()
            for (i in 0..spokes) {
                val a = i * (PI / 2) / spokes
                val x = (rr * kotlin.math.cos(a)).toFloat()
                val y = (rr * sin(a)).toFloat()
                if (i == 0) {
                    p.moveTo(x, y)
                } else {
                    val m = (i - .5) * (PI / 2) / spokes
                    p.quadraticTo((rr * .82f * kotlin.math.cos(m)).toFloat(), (rr * .82f * sin(m)).toFloat(), x, y)
                }
            }
            drawPath(p, Web.copy(alpha = .5f), style = Stroke(sw))
        }
    }

    /** A string of glowing pumpkin lights across Home just under the greeting and clock, small cobwebs at each end */
    val topTrim: HolidayArt = { t -> translate(0f, underBar()) { hwTop(t) } }

    private fun DrawScope.hwTop(t: Float) {
        val step = 110f * density
        val n = (size.width / step).roundToInt().coerceAtLeast(2)
        val gap = size.width / n
        val top = 2f * density
        val sag = 12f * density
        val wire = Path()
        wire.moveTo(0f, top)
        for (i in 0 until n) wire.quadraticTo((i + .5f) * gap, top + sag * 2, (i + 1) * gap, top)
        drawPath(wire, Color(0xFF2A1D14), style = Stroke(2f * density))
        val pw = 22f * density
        for (i in 0 until n) {
            val x = (i + .5f) * gap
            val lit = .65f + .35f * wave(t, 2.2f, i * .37f)
            drawLine(Color(0xFF2A1D14), Offset(x, top + sag), Offset(x, top + sag + 4f * density), strokeWidth = 1.5f * density)
            jack(Offset(x, top + sag + 4f * density + pw * .4f), pw, lit)
        }
        val r = 40f * density
        cobweb(r)
        scale(-1f, 1f, pivot = Offset(size.width / 2, 0f)) { cobweb(r) }
    }

    /** One maple leaf, [s] across */
    private fun DrawScope.leaf(
        c: Offset,
        s: Float,
        color: Color,
        turn: Float,
    ) {
        rotate(turn, c) {
            translate(c.x - s / 2, c.y - s / 2) {
                scale(s / 20f, s / 20f, pivot = Offset.Zero) {
                    drawPath(svgPath("M10 1l2 5 4-2-1 5 5 1-4 3 2 4-5-1-1 4-2-3-2 3-1-4-5 1 2-4-4-3 5-1-1-5 4 2z"), color)
                    drawLine(Color.Black.copy(alpha = .3f), Offset(10f, 7f), Offset(10f, 19f), strokeWidth = .8f)
                }
            }
        }
    }

    /** Carved jack-o'-lanterns with autumn leaves along the bottom of Home, a soft purple glow behind */
    val bottomTrim: HolidayArt = { t ->
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x8C4A2A78)), 0f, size.height))
        val colours = listOf(Color(0xFFC2410C), Color(0xFFE8A13A), Color(0xFF8C2F14), Color(0xFFF07A1E), Color(0xFFB45309))
        val ls = minOf(18f * density, bottomRoom() * .9f)
        val count = (size.width / (60f * density)).toInt().coerceAtLeast(3)
        for (i in 0 until count) {
            val x = (i + .5f) * size.width / count + (if (i % 2 == 0) -8f else 8f) * density
            leaf(Offset(x, size.height - ls * .55f), ls, colours[i % colours.size], i * 47f)
        }
        val jw = minOf(34f * density, bottomRoom() * 1.05f)
        val jacks = (size.width / (300f * density)).roundToInt().coerceIn(2, 5)
        for (i in 0 until jacks) {
            val x = (i + .5f) * size.width / jacks + 20f * density
            jack(Offset(x, size.height - jw * .42f), jw, .7f + .3f * wave(t, 1.7f, i * .8f))
        }
    }
}

/** Just under the greeting and clock line (it sits at the very top since the calmer Home) */
private fun DrawScope.underBar(): Float = (if (size.width < 600f * density) 52f else 64f) * density

/** TV: the bottom art stays in the gap under the Today cards; phones have room below them */
private fun DrawScope.bottomRoom(): Float = if (size.width < 600f * density) size.height else minOf(size.height, 20f * density)

object Friday13Nav {
    private val Crow = Color(0xFF16121E)
    private val CrowEdge = Color(0xFFD6CCF0)
    private val Eye = Color(0xFFA6E36A)
    private val Moon = Color(0xFFF4E7B8)
    private val MoonDark = Color(0xFFE2D29C)

    /** The approved lucky charms as the rail and phone-tab icons (Kids too, like the rest of the day's look) */
    val icons: Map<String, HolidayIconArt> =
        mapOf(
            "movies" to pictureIcon { R.drawable.f13nav_movies },
            "shows" to pictureIcon { R.drawable.f13nav_shows },
            "live" to pictureIcon { R.drawable.f13nav_live },
            "games" to pictureIcon { R.drawable.f13nav_games },
            "music" to pictureIcon { R.drawable.f13nav_music },
            "books" to pictureIcon { R.drawable.f13nav_books },
        )

    private const val CROW_PATH =
        "M4 17q3-6 11-7 4-5 9-4.5 4 .8 5.5 4l6.5 1.2-6.6 1.8q-.4 5-5.4 8l-2.6 3.4h-2.2l.8-3q-6 .8-10.6-1.6L1.5 23l3.6-5.6z"

    /** A crow sitting with its feet at [feet], [w] wide, facing right (or left when [flip]) */
    private fun DrawScope.crow(
        feet: Offset,
        w: Float,
        flip: Boolean,
        hop: Float = 0f,
    ) {
        val s = w / 40f
        translate(feet.x - 20f * s, feet.y - 30f * s - hop) {
            scale(s, s, pivot = Offset.Zero) {
                scale(if (flip) -1f else 1f, 1f, pivot = Offset(20f, 0f)) {
                    // a pale edge so the crow reads on the dark sky (Ben: the crows were hard to see)
                    drawPath(svgPath(CROW_PATH), Color(0x4DB9A6FF), style = Stroke(3.2f))
                    drawPath(svgPath(CROW_PATH), Crow)
                    drawPath(svgPath(CROW_PATH), CrowEdge, style = Stroke(1.1f))
                    drawLine(Color(0xFF2C2636), Offset(8f, 17f), Offset(20f, 18f), strokeWidth = 1.2f)
                    drawCircle(Eye, 1.1f, Offset(27.4f, 8.6f))
                    drawLine(Color(0xFF8A80A0), Offset(19.5f, 25.5f), Offset(19.5f, 30f), strokeWidth = 1f)
                    drawLine(Color(0xFF8A80A0), Offset(22.5f, 25.5f), Offset(22.5f, 30f), strokeWidth = 1f)
                }
            }
        }
    }

    /** A crow perched beside the icon you're on, hopping now and then (box 12 x 18, drawn a little past it) */
    val mark: HolidayArt = { t ->
        val u = size.width / 12f
        val phase = (t % 5f) / 5f
        val hop = if (phase in .7f..0.8f) sin(((phase - .7f) / .1f) * PI).toFloat() * 3f * u else 0f
        crow(Offset(size.width * .4f, size.height * .75f), 17f * u, flip = false, hop = hop)
    }

    /** A full moon behind a sagging power line with five crows across the top of Home (one hops, one turns round) */
    val topTrim: HolidayArt = { t -> translate(0f, underBar()) { f13Top(t) } }

    private fun DrawScope.f13Top(t: Float) {
        val mr = 20f * density
        val mc = Offset(size.width * .66f, 22f * density)
        drawCircle(Brush.radialGradient(listOf(Moon.copy(alpha = .45f), Color.Transparent), mc, mr * 2f), mr * 2f, mc)
        drawCircle(Moon, mr, mc)
        drawCircle(MoonDark, mr * .2f, mc + Offset(-mr * .38f, -mr * .22f))
        drawCircle(MoonDark, mr * .28f, mc + Offset(mr * .28f, mr * .3f))
        drawCircle(MoonDark, mr * .11f, mc + Offset(mr * .34f, -mr * .5f))
        // low enough that no crow is cut off at the top (Ben)
        val top = size.height * .78f
        val sag = size.height * .15f

        fun wireY(f: Float) = top + sag * 4f * f * (1f - f)
        val wire = Path()
        wire.moveTo(0f, top)
        wire.quadraticTo(size.width / 2, top + sag * 2, size.width, top)
        drawPath(wire, Color(0xFF0D0B10), style = Stroke(3f * density))
        drawPath(wire, Color(0xFF8A80A0), style = Stroke(1f * density))
        val cw = 46f * density
        val spots = listOf(.1f, .26f, .44f, .62f, .84f)
        spots.forEachIndexed { i, f ->
            val hop =
                if (i ==
                    1
                ) {
                    (if ((t % 5f) / 5f in .7f..0.8f) sin((((t % 5f) / 5f - .7f) / .1f) * PI).toFloat() * 8f * density else 0f)
                } else {
                    0f
                }
            val turned = if (i == 3) (t % 6f) > 3.6f else i % 2 == 1
            crow(Offset(size.width * f, wireY(f) + 1f * density), cw, turned, hop)
        }
    }

    /** A crooked old wooden fence standing in pale mist along the bottom of Home */
    val bottomTrim: HolidayArt = { t ->
        val drift = (wave(t, 12f) - .5f) * 30f * density
        for ((x, a) in listOf(.2f to .35f, .75f to .3f)) {
            val c = Offset(size.width * x + drift, size.height)
            drawOval(
                Brush.radialGradient(listOf(Color(0xFFC8C3DC).copy(alpha = a), Color.Transparent), c, size.width * .3f),
                Offset(c.x - size.width * .3f, size.height * .1f),
                Size(size.width * .6f, size.height * 1.8f),
            )
        }
        val pw = 12f * density
        val n = (size.width / pw).toInt() + 1
        val rail = Color(0xFF3A3342)
        val room = bottomRoom()
        val sky = size.height - room
        drawRect(rail, Offset(0f, sky + room * .42f), Size(size.width, 2.5f * density))
        drawRect(rail, Offset(0f, sky + room * .78f), Size(size.width, 2.5f * density))
        for (i in 0 until n) {
            val h = room * (.55f + ((i * 7) % 11) / 11f * .4f)
            val tilt = (((i * 13) % 9) - 4).toFloat()
            val x = i * pw + pw / 2
            rotate(tilt, Offset(x, size.height)) {
                val p = Path()
                p.moveTo(x - pw * .42f, size.height)
                p.lineTo(x - pw * .42f, size.height - h)
                p.lineTo(x, size.height - h - pw * .25f)
                p.lineTo(x + pw * .42f, size.height - h)
                p.lineTo(x + pw * .42f, size.height)
                p.close()
                drawPath(p, Color(0xFF2F2838))
                drawPath(p, Color(0xFF7A6F8C), style = Stroke(.8f * density))
            }
        }
    }

    /** Extra hand-picked titles for the first carousel pick: the Spooky but fun list (no slashers, Kids too) */
    val pickTitles: List<String> get() = Friday13.SpookyPicks.titles
}
