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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Benflix National Popcorn Day look (approved 2026-11-17, thread "Design a Popcorn Day theme for Benflix"): just
 * 19 January, midnight to midnight, on TV, phone and web, unless it's switched off in Settings > Look. Kids gets
 * it too (Ben). The same date as the web's popcorn.js, so every screen matches. It replaces the old Popcorn Day
 * scene.
 */
object PopcornSeason {
    /** Settings > Look > Popcorn Day look is off (set by MainActivity from the saved settings) */
    var lookOff by mutableStateOf(false)

    fun isSeason(date: LocalDate = LocalDate.now()): Boolean = date.month == Month.JANUARY && date.dayOfMonth == 19

    fun on(date: LocalDate = LocalDate.now()): Boolean = !lookOff && isSeason(date)

    const val GREETING = "Happy Popcorn Day"

    /** Leads the line under the Home greeting, and sits under the Who's watching greeting */
    const val LINE = "It’s National Popcorn Day"
    const val WHO_LINE = "It’s National Popcorn Day · grab a bowl"

    /** How full the popcorn cart is on Who's watching: a little in the morning, half after lunch, overflowing in the evening */
    fun variant(now: LocalTime = LocalTime.now()): String =
        when {
            now.hour < 12 -> "am"
            now.hour < 17 -> "pm"
            else -> "eve"
        }
}

/**
 * The Popcorn Day look for the rest of the app: warm butter yellow (with dark words) for selected things, deep
 * cinema-seat red for other buttons, switches and panels, white words on every door, a different movie snack
 * standing in the bottom-right corner of each door, and small touches that each fit their kind of thing:
 * popcorn popping out of Play, a piece of popcorn on the end of progress, switches that are on show a popped
 * piece and off a kernel, loading is a ring of kernels popping one after another, and each kind of notice gets
 * its own mark (ticket, bucket, kernel, film reel). Kids too, so it wins over Sky and Sunshine that day.
 */
object PcLook {
    val on: Boolean get() = PopcornSeason.on()

    val Butter = Color(0xFFF6C744)
    val OnButter = Color(0xFF2A1A05)
    val Red = Color(0xFF9E2129)
    val Panel = Color(0xFF1E0C0E)
    val PanelHigh = Color(0xFF2A1215)
    val Raised = Color(0xFF24100F)
    val Line = Color(0xFF3E1A1D)
}

/** The snack that stands on each door */
private fun BenflixDoor.snack(): Int =
    when (this) {
        BenflixDoor.MOVIES -> R.drawable.pc_snack_movies
        BenflixDoor.SHOWS -> R.drawable.pc_snack_shows
        BenflixDoor.LIVE_TV -> R.drawable.pc_snack_live
        BenflixDoor.GAMES -> R.drawable.pc_snack_games
        BenflixDoor.MUSIC -> R.drawable.pc_snack_music
        BenflixDoor.BOOKS -> R.drawable.pc_snack_books
    }

/**
 * Popcorn Day look on a Home door: its snack standing on the bottom edge in the bottom-right corner, like it's
 * on a shelf (as on the approved sheet: a 240 x 220 door with the snack centred at x 194, standing at y 208).
 */
fun Modifier.pcDoorSnack(door: BenflixDoor): Modifier =
    if (!PcLook.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(door.snack())
            drawWithContent {
                drawContent()
                val k = minOf(size.width / 240f, size.height / 220f)
                // the picture is 116 x 122 drawing units, with the floor at 118
                val w = 116f * 1.02f * k
                val h = w * img.height / img.width
                val cx = size.width - 46f * k
                val floor = size.height - 12f * k
                clipRect { pcDrawImageAt(img, cx - w / 2f, floor - h * 118f / 122f, w, h) }
            }
        }
    }

/**
 * Popcorn Day look: every few seconds a piece of popcorn pops out of the top of Play and drops back in, with
 * little lines showing it jump (on the approved sheet it sits near the right end of the button).
 */
fun Modifier.pcPlayPop(): Modifier =
    if (!PcLook.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(R.drawable.pc_puff)
            val t by rememberInfiniteTransition(label = "pcPop").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
                label = "pcPopT",
            )
            drawWithContent {
                drawContent()
                // rests inside for the first half, then jumps up and falls back in
                val j = ((t - .5f) / .5f)
                if (j <= 0f) return@drawWithContent
                val hgt = 4f * j * (1f - j)
                val s = size.height * .7f
                val cx = size.width - size.height * .62f
                val cy = s * .2f - hgt * size.height * .95f
                if (hgt > .55f) {
                    val a = (hgt - .55f) / .45f * .6f
                    val c = Color.White.copy(alpha = a)
                    val sw = size.height * .05f
                    drawLine(c, Offset(cx - s * .38f, cy + s * .55f), Offset(cx - s * .44f, cy + s * .9f), sw, StrokeCap.Round)
                    drawLine(c, Offset(cx + s * .36f, cy + s * .55f), Offset(cx + s * .46f, cy + s * .88f), sw, StrokeCap.Round)
                }
                pcDrawImageAt(img, cx - s / 2f, cy - s / 2f, s, s)
            }
        }
    }

/** Popcorn Day look: a piece of popcorn on the end of a progress line ([share] of the way along) */
fun Modifier.pcProgressPuff(
    share: Float,
    puff: Dp = 22.dp,
): Modifier =
    if (!PcLook.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(R.drawable.pc_puff)
            drawWithContent {
                drawContent()
                val s = puff.toPx()
                val x = (size.width * share.coerceIn(0f, 1f)) - s / 2f
                // sits on the line, leaning up into the picture so it isn't cut off at the card's edge
                pcDrawImageAt(img, x.coerceIn(0f, size.width - s), size.height - s * .8f, s, s)
            }
        }
    }

/** Popcorn Day look: next to a switch, a popped piece when it's on and a kernel that hasn't popped yet when it's off */
@Composable
fun PcSwitchMark(
    on: Boolean,
    size: Dp = 24.dp,
) {
    Image(
        painter = painterResource(if (on) R.drawable.pc_puff else R.drawable.pc_kernel),
        contentDescription = null,
        modifier = Modifier.size(if (on) size else size * .8f),
    )
}

/** Popcorn Day look: the loading ring, a ring of kernels popping one after another, then starting again */
@Composable
fun PcLoadingRing(modifier: Modifier = Modifier) {
    val kernel = ImageBitmap.imageResource(R.drawable.pc_kernel)
    val puff = ImageBitmap.imageResource(R.drawable.pc_puff)
    val t by rememberInfiniteTransition(label = "pcRing").animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "pcRingT",
    )
    Canvas(modifier) {
        val r = size.minDimension * .36f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(PcLook.Red, r, c, style = Stroke(size.minDimension * .07f))
        // 8 kernels; one more pops every 0.4 s, and after a short rest they all go back to kernels
        val popped = t.coerceAtMost(8.99f)
        for (k in 0 until 8) {
            val a = (k / 8f) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val x = c.x + cos(a) * r
            val y = c.y + sin(a) * r
            if (t < 9f && k < popped.toInt()) {
                val s = size.minDimension * .3f
                pcDrawImageAt(puff, x - s / 2f, y - s / 2f, s, s)
            } else if (k == popped.toInt() && t < 8f) {
                // the one about to pop grows and becomes a piece of popcorn
                val p = popped - k
                val s = size.minDimension * (.15f + .15f * p)
                pcDrawImageAt(if (p > .6f) puff else kernel, x - s / 2f, y - s / 2f, s, s)
            } else {
                val s = size.minDimension * .15f
                pcDrawImageAt(kernel, x - s / 2f, y - s / 2f, s, s)
            }
        }
    }
}

/** The mark for each kind of notice: a ticket for new episodes, a full bucket for ready, a kernel for on the way, a film reel for Live TV */
fun pcMarkFor(kind: String): Int {
    val k = kind.lowercase()
    return when {
        k.startsWith("new") -> R.drawable.pc_ticket
        k.startsWith("ready") || k.contains("is ready") -> R.drawable.pc_bucket
        k.startsWith("on the way") || k.contains("arrives") -> R.drawable.pc_kernel
        k.contains("live") -> R.drawable.pc_reel
        else -> R.drawable.pc_puff_hull
    }
}

/** Popcorn Day look: a notice's own small mark */
@Composable
fun PcKindMark(
    kind: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(pcMarkFor(kind)),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

private fun DrawScope.pcDrawImageAt(
    img: ImageBitmap,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
) {
    drawImage(
        image = img,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
        dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
        filterQuality = FilterQuality.Medium,
    )
}
