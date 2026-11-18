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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
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
import java.time.LocalDateTime
import java.time.Month
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Benflix 4th of July look (approved 2026-11-17, thread "Design a 4th of July theme for Benflix"): on from
 * 1 to 4 July on TV, phone and web, unless it's switched off in Settings > Look. No other theme is on then.
 * The same dates as the web's july4.js, so every screen matches.
 */
object JulySeason {
    /** Settings > Look > 4th of July look is off (set by MainActivity from the saved settings) */
    var lookOff by mutableStateOf(false)

    fun isSeason(now: LocalDateTime = LocalDateTime.now()): Boolean = now.month == Month.JULY && now.dayOfMonth <= 4

    // Only the month and day matter here, so the shared date is enough (no clock read on every colour)
    fun on(now: LocalDateTime = Today.date().atStartOfDay()): Boolean = !lookOff && isSeason(now)

    /** Which recording of the room plays: "night" on the 4th from 9 PM (the big fireworks show), else "day" */
    fun variant(now: LocalDateTime = LocalDateTime.now()): String =
        if (now.month == Month.JULY && now.dayOfMonth == 4 &&
            now.hour >= 21
        ) {
            "night"
        } else {
            "day"
        }

    /** The line under the greeting: "3 days to the Fourth", then "Fireworks tonight at 9", then "The fireworks are on!" */
    fun countdown(now: LocalDateTime = LocalDateTime.now()): String? {
        if (!isSeason(now)) return null
        return when (val days = 4 - now.dayOfMonth) {
            0 -> if (now.hour >= 21) "The fireworks are on!" else "Fireworks tonight at 9"
            1 -> "The Fourth is tomorrow"
            else -> "$days days to the Fourth"
        }
    }

    const val GREETING = "Happy 4th of July"
}

/**
 * The 4th of July look for the rest of the app: flag red for selected things (white words on it), deep navy for
 * other buttons, switches and panels, white words on every door, a different summer thing on each door, and small
 * touches that each belong to one kind of thing (three stars on Play, red and white stripes with a big star on
 * progress, a striped switch with a navy star knob for switches that are on, a firework blooming for loading, and
 * a different mark for each kind of Home notice). On Kids too (Ben 2026-11-17), so it wins over Sky and Sunshine.
 */
object J4Look {
    val on: Boolean get() = JulySeason.on()

    val Red = Color(0xFFE0404F)
    val Navy = Color(0xFF24366E)
    val Panel = Color(0xFF0E1631)
    val PanelHigh = Color(0xFF15204A)
    val Raised = Color(0xFF121C3E)
    val Line = Color(0xFF25386A)
    val Blue = Color(0xFF4A7FE6)
}

/** The summer thing that hangs on each door */
private fun BenflixDoor.j4Charm(): Int =
    when (this) {
        BenflixDoor.MOVIES -> R.drawable.j4_charm_movies
        BenflixDoor.SHOWS -> R.drawable.j4_charm_shows
        BenflixDoor.LIVE_TV -> R.drawable.j4_charm_live
        BenflixDoor.GAMES -> R.drawable.j4_charm_games
        BenflixDoor.MUSIC -> R.drawable.j4_charm_music
        BenflixDoor.BOOKS -> R.drawable.j4_charm_books
    }

/**
 * 4th of July look on a Home door: its summer thing near the top right (as on the approved sheet: a 240 x 220
 * door with the picture covering x 108 to 240 and y -4 to 162): fan bunting on Movies, a hanging flag on Shows,
 * star balloons on Live TV, a beach ball in a net on Games, a parade drum on Music and a pinwheel on Books.
 */
fun Modifier.j4DoorCharm(door: BenflixDoor): Modifier =
    if (!J4Look.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(door.j4Charm())
            drawWithContent {
                drawContent()
                val k = minOf(size.width, size.height * 240f / 220f) / 240f
                val w = 132f * k
                val h = w * img.height / img.width
                j4DrawImageAt(img, size.width - w, -4f * k, w, h)
            }
        }
    }

/** A five-pointed star centred on [c] */
private fun starPath(
    c: Offset,
    r: Float,
    inner: Float = .42f,
): Path =
    Path().apply {
        for (k in 0 until 10) {
            val a = -Math.PI / 2 + k * Math.PI / 5
            val rr = if (k % 2 == 1) r * inner else r
            val x = c.x + (cos(a) * rr).toFloat()
            val y = c.y + (sin(a) * rr).toFloat()
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

/** 4th of July look: three big white stars fanned across the right end of Play */
fun Modifier.j4PlayStars(): Modifier =
    if (!J4Look.on) {
        this
    } else {
        drawWithContent {
            drawContent()
            // placed as on the sheet's 260 x 84 Play button, scaled to this one's height
            val k = size.height / 84f
            listOf(Triple(44f, 30f, 15f), Triple(74f, 54f, 11f), Triple(38f, 64f, 8f)).forEach { (dx, y, r) ->
                drawPath(starPath(Offset(size.width - dx * k, y * k), r * k), Color.White)
            }
        }
    }

/** 4th of July look: the watched part of a progress line is red and white stripes, with a big white star at its end */
fun Modifier.j4ProgressStripes(): Modifier =
    if (!J4Look.on) {
        this
    } else {
        drawWithContent {
            drawRect(J4Look.Red)
            val step = maxOf(size.height, 4f)
            clipRect {
                var x = -size.height
                while (x < size.width + size.height) {
                    val p =
                        Path().apply {
                            moveTo(x, size.height)
                            lineTo(x + step / 2f, size.height)
                            lineTo(x + step / 2f + size.height * .7f, 0f)
                            lineTo(x + size.height * .7f, 0f)
                            close()
                        }
                    drawPath(p, Color.White)
                    x += step * 1.2f
                }
            }
        }
    }

/** 4th of July look: the big white star at the end of a progress line ([share] of the way along) */
fun Modifier.j4ProgressStar(
    share: Float,
    star: Dp = 20.dp,
): Modifier =
    if (!J4Look.on) {
        this
    } else {
        drawWithContent {
            drawContent()
            val r = star.toPx() / 2f
            val x = (size.width * share.coerceIn(0f, 1f)).coerceIn(r, size.width - r)
            // sits on the line, leaning up into the picture so it isn't cut off at the card's edge
            val c = Offset(x, size.height - r * .7f)
            val p = starPath(c, r)
            drawPath(p, Color.White)
            drawPath(p, Color.Black.copy(alpha = .35f), style = Stroke(1.5f))
        }
    }

/** 4th of July look: shown next to a switch that's on, a little red and white striped switch with a navy star knob */
@Composable
fun J4Switch(size: Dp = 22.dp) {
    Canvas(Modifier.size(size * 1.85f, size).clipToBounds()) {
        val h = this.size.height
        val w = this.size.width
        drawRoundRect(J4Look.Red, cornerRadius = CornerRadius(h / 2f))
        clipRect {
            for (k in 0 until 3) {
                val y = h * (8f + k * 14f) / 52f
                drawRect(Color.White.copy(alpha = .9f), topLeft = Offset(h * .3f, y), size = Size(w - h * .6f, h * 6f / 52f))
            }
        }
        val knob = Offset(w - h / 2f, h / 2f)
        drawCircle(J4Look.Navy, radius = h * .42f, center = knob)
        drawCircle(Color.White, radius = h * .42f, center = knob, style = Stroke(h * .06f))
        drawPath(starPath(knob, h * .26f), Color.White)
    }
}

/**
 * 4th of July look: the loading ring is a firework blooming round the circle: red, white and blue sparks that get
 * bigger and brighter as they go round, turning steadily.
 */
@Composable
fun J4LoadingBurst(modifier: Modifier = Modifier) {
    val loop = rememberInfiniteTransition(label = "j4Burst")
    val turn by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "j4BurstTurn",
    )
    val cols = listOf(J4Look.Red, Color.White, J4Look.Blue)
    Canvas(modifier) {
        val r = size.minDimension / 2f * .92f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color.White, radius = r * .16f, center = c)
        val n = 16
        // the brightest spark leads round the circle
        val lead = (turn * n).toInt()
        for (k in 0 until n) {
            val a = Math.toRadians(k * 360.0 / n - 90.0)
            val t = ((k - lead - 1 + n * 2) % n) / (n - 1f)
            val len = r * (.35f + .65f * t)
            val sw = r * (.06f + t * .07f)
            val x1 = c.x + (cos(a) * r * .3f).toFloat()
            val y1 = c.y + (sin(a) * r * .3f).toFloat()
            val x2 = c.x + (cos(a) * len).toFloat()
            val y2 = c.y + (sin(a) * len).toFloat()
            drawLine(cols[k % 3].copy(alpha = .25f + t * .75f), Offset(x1, y1), Offset(x2, y2), sw, StrokeCap.Round)
            drawCircle(Color.White.copy(alpha = .3f + t * .7f), radius = r * (.05f + t * .08f), center = Offset(x2, y2))
        }
    }
}

/**
 * 4th of July look: each kind of Home notice gets its own small mark: a little flag for new episodes and new
 * music, a star for something ready to watch, a rocket for things on the way, and a firework for everything else.
 */
fun j4MarkFor(kind: String): Int {
    val k = kind.lowercase()
    return when {
        k.startsWith("new") -> R.drawable.j4_mark_flag
        k.startsWith("ready") || k.contains("is ready") -> R.drawable.j4_mark_star
        k.startsWith("on the way") || k.contains("arrives") -> R.drawable.j4_mark_rocket
        else -> R.drawable.j4_mark_burst
    }
}

@Composable
fun J4KindMark(
    kind: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(j4MarkFor(kind)),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

private fun DrawScope.j4DrawImageAt(
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
