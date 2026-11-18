package com.github.damontecres.wholphin.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import java.time.LocalDate
import java.time.Month
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Benflix Video Games Day (approved 2026-11-17, thread "Design a Video Games Day theme for Benflix"): on
 * 12 September only, on TV, phone and web, for every profile (Kids too), unless it's switched off in
 * Settings > Look. The same date as the web's arcade.js, so every screen matches.
 */
object VideoGamesDay {
    /** Settings > Look > Video Games Day look is off (set by MainActivity from the saved settings) */
    var lookOff by mutableStateOf(false)

    fun isDay(date: LocalDate = Today.date()): Boolean = date.month == Month.SEPTEMBER && date.dayOfMonth == 12

    fun on(date: LocalDate = Today.date()): Boolean = !lookOff && isDay(date)

    const val GREETING = "Happy Video Games Day"

    /** The line under the greeting, before the usual news */
    const val LINE = "Player 1 ready"
}

/**
 * The Video Games Day look for the rest of the app: arcade cyan (with dark words) for selected things, deep
 * purple for other buttons, switches and panels, white words on every door, a different game keepsake on each
 * door, and small game touches that differ for each kind of thing (an 8-bit Play button, a block XP bar with a
 * pixel coin, an arcade button by switches that are on, eight pixel blocks for loading, and a pixel picture for
 * each kind of news). Kids gets it too: the Kids colours rest for the day.
 */
object VgLook {
    val on: Boolean get() = VideoGamesDay.on()

    val Cyan = Color(0xFF3FE0FF)
    val OnCyan = Color(0xFF06222B)
    val Purple = Color(0xFF4A2A86)
    val Panel = Color(0xFF170E2C)
    val PanelHigh = Color(0xFF221640)
    val Raised = Color(0xFF1E1338)
    val Line = Color(0xFF33205C)
}

/** The words under a door's name: on Video Games Day the Games door nudges you to play something */
val BenflixDoor.blurb: String
    get() = if (this == BenflixDoor.GAMES && VgLook.on) "Play something today" else description

/** The pixel capitals (Press Start 2P, OFL) used for the Video Games Day greeting */
val PixelFont = FontFamily(Font(R.font.holiday_pressstart))

/** The game keepsake on each door, each hung or fixed the way it really would be */
private fun BenflixDoor.keepsake(): Int =
    when (this) {
        BenflixDoor.MOVIES -> R.drawable.vg_charm_movies
        BenflixDoor.SHOWS -> R.drawable.vg_charm_shows
        BenflixDoor.LIVE_TV -> R.drawable.vg_charm_live
        BenflixDoor.GAMES -> R.drawable.vg_charm_games
        BenflixDoor.MUSIC -> R.drawable.vg_charm_music
        BenflixDoor.BOOKS -> R.drawable.vg_charm_books
    }

/**
 * Video Games Day look on a Home door: its keepsake near the top right (as on the approved sheet: a 240 x 220
 * door with the keepsake's box from x 104 to 240 and y -12 to 150).
 */
fun Modifier.vgDoorKeepsake(door: BenflixDoor): Modifier =
    if (!VgLook.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(door.keepsake())
            drawWithContent {
                drawContent()
                val k = minOf(size.width, size.height * 240f / 220f) / 240f
                val w = 136f * k
                val h = w * img.height / img.width
                drawImageAt(img, size.width - w, -12f * k, w, h)
            }
        }
    }

/** A box with stepped 8-bit corners, like a button in an old game menu */
fun pixelShape(step: Dp = 6.dp): Shape =
    object : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: androidx.compose.ui.unit.LayoutDirection,
            density: androidx.compose.ui.unit.Density,
        ): androidx.compose.ui.graphics.Outline =
            androidx.compose.ui.graphics.Outline
                .Generic(steppedPath(size, with(density) { step.toPx() } * (size.height / 60f).coerceIn(.5f, 1.6f)))
    }

/** Video Games Day look: Play gets a hard black pixel shadow and white corner brackets */
fun Modifier.vgPlayPixel(): Modifier =
    if (!VgLook.on) {
        this
    } else {
        drawWithContent {
            val sh = 5.dp.toPx()
            val step = 6.dp.toPx() * (size.height / 60f).coerceIn(.5f, 1.6f)
            translate(sh, sh) { drawPath(steppedPath(size, step), Color.Black) }
            drawContent()
            val g = 6.dp.toPx()
            val l = 9.dp.toPx()
            val st = Stroke(width = 3.dp.toPx())
            val w = size.width
            val h = size.height
            listOf(
                Triple(-g, -g, 1f to 1f),
                Triple(w + g, -g, -1f to 1f),
                Triple(w + g, h + g, -1f to -1f),
                Triple(-g, h + g, 1f to -1f),
            ).forEach { (x, y, d) ->
                val p =
                    Path().apply {
                        moveTo(x, y + d.second * l)
                        lineTo(x, y)
                        lineTo(x + d.first * l, y)
                    }
                drawPath(p, Color.White, style = st)
            }
        }
    }

private fun steppedPath(
    size: Size,
    s: Float,
): Path =
    Path().apply {
        val w = size.width
        val h = size.height
        moveTo(s, 0f)
        lineTo(w - s, 0f)
        lineTo(w - s, s)
        lineTo(w, s)
        lineTo(w, h - s)
        lineTo(w - s, h - s)
        lineTo(w - s, h)
        lineTo(s, h)
        lineTo(s, h - s)
        lineTo(0f, h - s)
        lineTo(0f, s)
        lineTo(s, s)
        close()
    }

/**
 * Video Games Day look: the progress line becomes an XP bar made of blocks ([share] of them lit cyan, the rest
 * purple), with a pixel coin where you are. Draws over the whole line, so put it on a box the line's size.
 */
fun Modifier.vgProgressBlocks(
    share: Float,
    coin: Dp = 16.dp,
): Modifier =
    if (!VgLook.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(R.drawable.vg_coin)
            drawWithContent {
                val f = share.coerceIn(0f, 1f)
                val gap = 2.dp.toPx()
                val n = (size.width / 14.dp.toPx()).roundToInt().coerceAtLeast(6)
                val bw = (size.width - gap * (n - 1)) / n
                for (k in 0 until n) {
                    val lit = (k + 1f) / n <= f + .001f
                    drawRect(if (lit) VgLook.Cyan else VgLook.Purple, Offset(k * (bw + gap), 0f), Size(bw, size.height))
                }
                val c = coin.toPx()
                val x = (size.width * f - c / 2f).coerceIn(0f, size.width - c)
                // sits on the bar, leaning up into the picture so it isn't cut off at the card's edge
                drawImageAt(img, x, size.height - c * .8f, c, c, FilterQuality.None)
            }
        }
    }

/** Video Games Day look: the shiny round arcade button shown next to a switch that's on */
@Composable
fun VgKnob(size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        drawCircle(Color(0xFF1C6F80), r * .92f, Offset(r, r + r * .14f))
        drawCircle(VgLook.Cyan, r * .9f, Offset(r, r))
        drawOval(Color.White.copy(alpha = .7f), Offset(r * .45f, r * .38f), Size(r * .7f, r * .42f))
    }
}

/** Video Games Day look: eight pixel blocks in a ring, lighting up one after another (the loading ring) */
@Composable
fun VgLoadingBlocks(modifier: Modifier = Modifier) {
    val turn = rememberInfiniteTransition(label = "vgLoad")
    val t by turn.animateFloat(0f, 8f, infiniteRepeatable(tween(960, easing = LinearEasing)), label = "vgLoadStep")
    Canvas(modifier) {
        val r = size.minDimension * .38f
        val b = size.minDimension * .17f
        val head = t.toInt() % 8
        for (k in 0 until 8) {
            val a = k / 8.0 * Math.PI * 2
            val x = center.x + sin(a).toFloat() * r
            val y = center.y - cos(a).toFloat() * r
            val behind = (head - k + 8) % 8
            val lvl =
                when (behind) {
                    0 -> 1f
                    1 -> .6f
                    2 -> .35f
                    else -> 0f
                }
            drawRect(
                if (lvl > 0f) VgLook.Cyan.copy(alpha = lvl) else VgLook.Purple,
                Offset(x - b / 2f, y - b / 2f),
                Size(b, b),
            )
        }
    }
}

/** The kinds of news that get their own pixel picture on Video Games Day */
enum class VgKind(
    val res: Int,
) {
    /** a little TV, for new episodes */
    EPISODE(R.drawable.vg_mark_ep),

    /** a star, for things that are ready */
    READY(R.drawable.vg_mark_ready),

    /** an hourglass, for things on the way */
    ON_THE_WAY(R.drawable.vg_mark_way),

    /** a joystick, for Live TV */
    LIVE(R.drawable.vg_mark_live),
}

/** Video Games Day look: the pixel picture for one kind of news */
@Composable
fun VgKindMark(
    kind: VgKind,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    // crisp squares, not blurred, when the little picture is made bigger
    Image(
        painter =
            androidx.compose.ui.graphics.painter
                .BitmapPainter(ImageBitmap.imageResource(kind.res), filterQuality = FilterQuality.None),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

/** Video Games Day look: a cyan edge down the left of a notice, like a lit-up game menu */
fun Modifier.vgNoticeEdge(): Modifier =
    if (!VgLook.on) {
        this
    } else {
        drawBehind {
            drawRect(VgLook.Cyan, Offset.Zero, Size(5.dp.toPx(), size.height))
        }
    }

private fun DrawScope.drawImageAt(
    img: ImageBitmap,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    quality: FilterQuality = FilterQuality.Medium,
) {
    drawImage(
        image = img,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
        dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
        filterQuality = quality,
    )
}
