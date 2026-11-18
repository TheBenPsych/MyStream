package com.github.damontecres.wholphin.ui.theme

import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.WholphinApplication
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * St. Patrick's Day on the new navigation (sheet "St. Patrick's Day with the new navigation", 2026-11-17):
 * - the rail's door icons become the six lucky charms Ben loved (top hat, horseshoe, gold coin medal, dice, harp,
 *   four-leaf clover); Home turns clover green and Search gold. Nothing sits beside the names.
 * - Home: green, white and gold cloth bunting across the top (each flag swaying on its own, a shamrock printed on the
 *   white ones) and a patch of shamrocks with white clover flowers along the bottom, gently moving (sp-bunting).
 * The same drawings as the web's js/holidaynav.js entry.
 */
object StPatricksNav {
    private val Gold = Color(0xFFE2B33C)
    private val GoldDark = Color(0xFF8A6414)
    private val Cream = Color(0xFFF3F0E4)
    private val Clover = Color(0xFF3DBE6C)
    private val CloverLight = Color(0xFF5FD889)
    private val Emerald = Color(0xFF16603A)
    private val EmeraldDark = Color(0xFF0B3A22)
    private val BackLeaf = Color(0xFF1F6E3E)
    private val Twine = Color(0xFFD9CFB2)

    // ---------- rail and phone-tab icons ----------

    private val iconIds =
        mapOf(
            "movies" to R.drawable.sp_icon_movies,
            "shows" to R.drawable.sp_icon_shows,
            "live" to R.drawable.sp_icon_live,
            "games" to R.drawable.sp_icon_games,
            "music" to R.drawable.sp_icon_music,
            "books" to R.drawable.sp_icon_books,
        )

    private val bitmaps = HashMap<String, ImageBitmap?>()

    private fun bitmap(key: String): ImageBitmap? =
        bitmaps.getOrPut(key) {
            runCatching {
                BitmapFactory.decodeResource(WholphinApplication.instance.resources, iconIds.getValue(key))?.asImageBitmap()
            }.getOrNull()
        }

    /** A charm filling the icon's box (a little bigger than a line icon, as it's a picture), dimmed like the usual icon */
    private fun charm(key: String): HolidayIconArt =
        { color, _ ->
            val img = bitmap(key)
            if (img != null) {
                val side = (size.minDimension * 1.25f).roundToInt()
                drawImage(
                    img,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(img.width, img.height),
                    dstOffset = IntOffset(((size.width - side) / 2).roundToInt(), ((size.height - side) / 2).roundToInt()),
                    dstSize = IntSize(side, side),
                    alpha = color.alpha.coerceIn(0f, 1f),
                )
            }
        }

    /** Home: the usual house shape, in clover green */
    private val home: HolidayIconArt = { color, _ ->
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 1.9f * u, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        val tint = Clover.copy(alpha = color.alpha)
        val roof =
            Path().apply {
                moveTo(3 * u, 11 * u)
                lineTo(12 * u, 4 * u)
                lineTo(21 * u, 11 * u)
            }
        val walls =
            Path().apply {
                moveTo(5 * u, 10 * u)
                lineTo(5 * u, 20 * u)
                lineTo(19 * u, 20 * u)
                lineTo(19 * u, 10 * u)
            }
        val door =
            Path().apply {
                moveTo(10 * u, 20 * u)
                lineTo(10 * u, 15 * u)
                lineTo(14 * u, 15 * u)
                lineTo(14 * u, 20 * u)
            }
        drawPath(roof, tint, style = stroke)
        drawPath(walls, tint, style = stroke)
        drawPath(door, Gold.copy(alpha = color.alpha), style = stroke)
    }

    /** Search: the usual magnifying glass, in gold */
    private val search: HolidayIconArt = { color, _ ->
        val u = size.minDimension / 24f
        val tint = Gold.copy(alpha = color.alpha)
        drawCircle(tint, 6 * u, Offset(11 * u, 11 * u), style = Stroke(1.9f * u))
        drawLine(tint, Offset(15.5f * u, 15.5f * u), Offset(20 * u, 20 * u), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
    }

    val icons: Map<String, HolidayIconArt> =
        iconIds.keys.associateWith { charm(it) } + mapOf(HolidayIconKey.HOME to home, HolidayIconKey.SEARCH to search)

    // ---------- shamrocks ----------

    /** One heart-shaped leaf, tip at 0,0 pointing up, 1 unit = the sheet's 1 */
    private val leaf by lazy {
        Path().apply {
            moveTo(0f, 0f)
            cubicTo(-7f, -4f, -11f, -12f, -6f, -16.5f)
            cubicTo(-2.5f, -19f, 0f, -16f, 0f, -13.5f)
            cubicTo(0f, -16f, 2.5f, -19f, 6f, -16.5f)
            cubicTo(11f, -12f, 7f, -4f, 0f, 0f)
            close()
        }
    }
    private val stem by lazy {
        Path().apply {
            moveTo(0f, 0f)
            cubicTo(2f, -8f, -1f, -14f, 0f, -20f)
        }
    }

    /** A shamrock whose stem meets the ground at [at], [s] units big, leaning [lean] degrees */
    private fun DrawScope.shamrock(
        at: Offset,
        s: Float,
        lean: Float,
        fill: Color,
        light: Color,
        edge: Color,
        veins: Boolean,
    ) {
        translate(at.x, at.y) {
            rotate(lean, pivot = Offset.Zero) {
                scale(s, s, pivot = Offset.Zero) {
                    drawPath(stem, Emerald, style = Stroke(2f, cap = StrokeCap.Round))
                    translate(0f, -20f) {
                        for (a in 0 until 3) {
                            rotate(a * 120f, pivot = Offset.Zero) {
                                if (fill == light) {
                                    drawPath(leaf, fill)
                                } else {
                                    drawPath(leaf, Brush.verticalGradient(listOf(fill, light), startY = -17f, endY = 0f))
                                }
                                drawPath(leaf, edge, style = Stroke(.9f))
                                if (veins) {
                                    drawLine(Color.White.copy(alpha = .35f), Offset(0f, -1.5f), Offset(0f, -12f), .8f, StrokeCap.Round)
                                    drawLine(Color.White.copy(alpha = .45f), Offset(-3.5f, -14f), Offset(-5.5f, -10f), .9f, StrokeCap.Round)
                                }
                            }
                        }
                        drawCircle(Emerald, 1.6f, Offset.Zero)
                    }
                }
            }
        }
    }

    /** A white clover flower on a thin stem */
    private fun DrawScope.flower(
        at: Offset,
        s: Float,
        lean: Float,
    ) {
        translate(at.x, at.y) {
            rotate(lean, pivot = Offset.Zero) {
                scale(s, s, pivot = Offset.Zero) {
                    drawPath(
                        Path().apply {
                            moveTo(0f, 0f)
                            cubicTo(2f, -10f, -1f, -20f, 0f, -28f)
                        },
                        Emerald,
                        style = Stroke(1.6f),
                    )
                    translate(0f, -31f) {
                        drawCircle(Color(0xFFC9C3B0), 5.6f, Offset.Zero)
                        for (i in 0 until 22) {
                            val a = i * 2.4f
                            val r = (i % 3 + 2) * 1.3f
                            val p = Offset(kotlin.math.cos(a) * r, sin(a) * r * .9f)
                            rotate(a * 57f, pivot = p) {
                                drawOval(
                                    if (i % 5 == 0) Color(0xFFF2D9DF) else Color(0xFFF7F5EE),
                                    topLeft = Offset(p.x - 1.5f, p.y - 2.6f),
                                    size = Size(3f, 5.2f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** The same random numbers every time, so the patch never jumps about */
    private class Seeded(
        private var s: Long,
    ) {
        fun next(): Float {
            s = (s * 16807L) % 2147483647L
            return s / 2147483647f
        }
    }

    // ---------- Home: bunting along the top ----------

    private val flagColors = listOf(Emerald, Cream, Gold)
    private val flag by lazy {
        Path().apply {
            moveTo(-13f, 0f)
            lineTo(13f, 0f)
            lineTo(0f, 34f)
            close()
        }
    }
    private val flagShade by lazy {
        Brush.horizontalGradient(
            0f to Color.White.copy(alpha = .18f),
            .5f to Color.Transparent,
            1f to Color.Black.copy(alpha = .28f),
            startX = -13f,
            endX = 13f,
        )
    }
    private val stitch by lazy { PathEffect.dashPathEffect(floatArrayOf(2f, 1.6f)) }

    /**
     * Bunting in swags across the top (the sheet's 60 units = the trim's height). It hangs just below Home's greeting
     * and clock line (64dp down on TV, 52dp on phones) so it never crowds them, like Thanksgiving's garland.
     */
    val bunting: HolidayArt = { t ->
        val u = size.height / 60f
        val w = size.width / u
        val swags = max(2, (w / 367f).roundToInt())
        val sw = w / swags
        val drop = (if (size.width < 600.dp.toPx()) 52.dp else 64.dp).toPx()
        translate(0f, drop) {
            scale(u, u, pivot = Offset.Zero) {
                var k = 0
                for (s in 0 until swags) {
                    val x0 = s * sw
                    val sag = 18f
                    drawPath(
                        Path().apply {
                            moveTo(x0, 5f)
                            quadraticTo(x0 + sw / 2, 5f + sag * 2, x0 + sw, 5f)
                        },
                        Twine,
                        style = Stroke(1.6f),
                    )
                    val n = (sw / 32f).roundToInt()
                    for (i in 0 until n) {
                        val f = (i + .5f) / n
                        val x = x0 + f * sw
                        val y = 5f + sag * 4 * f * (1 - f)
                        val slope = (atan2(sag * 4 * (1 - 2 * f), sw) * 180 / PI).toFloat()
                        val swing = 3f * sin((t / 3.6f) * 2 * PI.toFloat() + i * .65f + s * 1.7f)
                        val c = flagColors[k++ % 3]
                        val white = c == Cream
                        translate(x, y) {
                            rotate(slope + swing, pivot = Offset.Zero) {
                                drawPath(flag, c)
                                drawPath(flag, flagShade)
                                drawRect(Color.Black.copy(alpha = .22f), Offset(-13f, 0f), Size(26f, 3.5f))
                                drawLine(
                                    if (white) Color(0xFFB9AE8C) else Color.White.copy(alpha = .4f),
                                    Offset(-10.5f, 6f),
                                    Offset(10.5f, 6f),
                                    .8f,
                                    pathEffect = stitch,
                                )
                                if (white) {
                                    shamrock(Offset(0f, 21f), .4f, 0f, Clover, CloverLight, EmeraldDark, veins = false)
                                }
                            }
                        }
                    }
                    drawCircle(Gold, 3f, Offset(x0, 5f))
                    drawCircle(GoldDark, 3f, Offset(x0, 5f), style = Stroke(1f))
                }
                drawCircle(Gold, 3f, Offset(w, 5f))
                drawCircle(GoldDark, 3f, Offset(w, 5f), style = Stroke(1f))
            }
        }
    }

    // ---------- Home: shamrock patch along the bottom ----------

    /**
     * Shamrocks growing along the bottom edge, back ones darker, with a few white clover flowers (64 units tall). Kept
     * low so the news cards floating just above never hide their tops.
     */
    val patch: HolidayArt = { t ->
        val u = size.height / 64f
        val w = size.width / u
        val sway = { phase: Float -> 2.5f * sin((t / 4.4f) * 2 * PI.toFloat() + phase) }
        scale(u, u, pivot = Offset.Zero) {
            val r = Seeded(7)
            var x = 4f
            while (x < w) {
                val s = .45f + r.next() * .35f
                val lean = -25f + r.next() * 50f
                shamrock(Offset(x, 64f), s * .9f, lean, BackLeaf, BackLeaf, EmeraldDark, false)
                x += 16f + r.next() * 10f
            }
            x = 40f
            while (x < w) {
                flower(Offset(x, 66f), .5f + r.next() * .25f, sway(x))
                x += 90f + r.next() * 80f
            }
            x = 10f
            while (x < w) {
                val s = .5f + r.next() * .4f
                val lean = -20f + r.next() * 40f
                shamrock(Offset(x, 66f), s, lean + sway(r.next() * 6f), Emerald, CloverLight, EmeraldDark, true)
                x += 20f + r.next() * 14f
            }
        }
    }

    /** Irish films for the first Home pick, on top of the Irish favourites list (Kids only ever see Kids titles) */
    val pickTitles =
        listOf(
            "Brooklyn",
            "The Quiet Man",
            "The Commitments",
            "Sing Street",
            "Once",
            "The Secret of Kells",
            "Song of the Sea",
            "Wolfwalkers",
            "Darby O'Gill and the Little People",
        )
}
