package com.github.damontecres.wholphin.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Benflix New Year's look (approved 2026-11-17, thread "Design a New Year's theme for Benflix"): on from
 * 27 December to 1 January on TV, phone and web, unless it's switched off in Settings > Look. Christmas ends
 * on 26 December, so the two never overlap. The same dates as the web's newyear.js, so every screen matches.
 */
object NewYearSeason {
    /** Settings > Look > New Year's look is off (set by MainActivity from the saved settings) */
    var lookOff by mutableStateOf(false)

    fun isSeason(date: LocalDate = LocalDate.now()): Boolean =
        (date.month == Month.DECEMBER && date.dayOfMonth >= 27) || (date.month == Month.JANUARY && date.dayOfMonth == 1)

    fun on(date: LocalDate = LocalDate.now()): Boolean = !lookOff && isSeason(date)

    /** The year being welcomed (on the balloons and in "Happy 2027!") */
    fun year(date: LocalDate = LocalDate.now()): Int = if (date.month == Month.DECEMBER) date.year + 1 else date.year

    /** Which recording of the room plays: "eve" 27 to 31 December (bottle corked), "day" on 1 January (opened) */
    fun variant(date: LocalDate = LocalDate.now()): String = if (date.month == Month.JANUARY) "day" else "eve"

    /** Seconds left in the 10-second countdown to midnight on 31 December, else 0 */
    fun countdownSecs(now: LocalDateTime = LocalDateTime.now()): Int {
        if (now.month != Month.DECEMBER || now.dayOfMonth != 31 || now.hour != 23 || now.minute != 59 || now.second < 50) return 0
        return 60 - now.second
    }

    /** Who's watching's big words: "Happy New Year", then 10 to 1 at midnight, then "Happy 2027!" all of New Year's Day */
    fun whoGreeting(now: LocalDateTime = LocalDateTime.now()): String {
        val c = countdownSecs(now)
        if (c > 0) return c.toString()
        return if (now.month == Month.JANUARY) "Happy ${now.year}!" else GREETING
    }

    /** The line under the greeting, up to New Year's Eve: "4 days to 2027" */
    fun countdown(now: LocalDateTime = LocalDateTime.now()): String? {
        if (now.month != Month.DECEMBER || countdownSecs(now) > 0) return null
        val days = 32 - now.dayOfMonth
        val y = year(now.toLocalDate())
        return if (days <= 1) "$y starts tonight at midnight" else "$days days to $y"
    }

    const val GREETING = "Happy New Year"
}

/**
 * The New Year's look for the rest of the app: champagne gold for selected things (with dark words on it),
 * midnight blue for other buttons, switches and panels, white words on every door, a different party charm on
 * each door, and small touches that each belong to one kind of thing (a glint sweeping across Play, a lit
 * sparkler at the end of progress, a gold star next to switches that are on, a clock face for loading, and a
 * different mark for each kind of Home notice). On Kids too (Ben 2026-11-17: "For kids too").
 */
object NyLook {
    val on: Boolean get() = NewYearSeason.on()

    val Gold = Color(0xFFE8B84A)
    val OnGold = Color(0xFF1A1406)
    val Midnight = Color(0xFF22335C)
    val Panel = Color(0xFF0F1730)
    val PanelHigh = Color(0xFF16203F)
    val Raised = Color(0xFF131C3A)
    val Line = Color(0xFF26335A)
}

/** The party charm that hangs on each door */
private fun BenflixDoor.nyCharm(): Int =
    when (this) {
        BenflixDoor.MOVIES -> R.drawable.ny_charm_movies
        BenflixDoor.SHOWS -> R.drawable.ny_charm_shows
        BenflixDoor.LIVE_TV -> R.drawable.ny_charm_live
        BenflixDoor.GAMES -> R.drawable.ny_charm_games
        BenflixDoor.MUSIC -> R.drawable.ny_charm_music
        BenflixDoor.BOOKS -> R.drawable.ny_charm_books
    }

/**
 * New Year's look on a Home door: its party charm near the top right (as on the approved sheet: a 240 x 220
 * door with the charm's picture covering x 124 to 256 and y -4 to 162, so glints poke just past the edge).
 */
fun Modifier.nyDoorCharm(door: BenflixDoor): Modifier =
    if (!NyLook.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(door.nyCharm())
            drawWithContent {
                drawContent()
                val k = minOf(size.width, size.height * 240f / 220f) / 240f
                val w = 132f * k
                val h = w * img.height / img.width
                nyDrawImageAt(img, size.width + 16f * k - w, -4f * k, w, h)
            }
        }
    }

/** New Year's look: a glint of sparkle sweeps across Play now and then, and a little star twinkles in its corner */
fun Modifier.nyPlayGlint(corner: Dp = 10.dp): Modifier =
    if (!NyLook.on) {
        this
    } else {
        composed {
            val glint = ImageBitmap.imageResource(R.drawable.ny_glint)
            val loop = rememberInfiniteTransition(label = "nyGlint")
            // the shine crosses in 0.9 s, then rests for 3 s
            val sweep by loop.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        keyframes {
                            durationMillis = 3900
                            0f at 0
                            1f at 900
                            1f at 3900
                        },
                    ),
                label = "nyGlintSweep",
            )
            val twinkle by loop.animateFloat(
                initialValue = .7f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
                label = "nyGlintTwinkle",
            )
            drawWithContent {
                drawContent()
                if (sweep < 1f) {
                    val band = size.width * .35f
                    val x = -band + (size.width + band * 2) * sweep
                    drawRoundRect(
                        cornerRadius = CornerRadius(corner.toPx()),
                        brush =
                            Brush.linearGradient(
                                0f to Color.Transparent,
                                .5f to Color.White.copy(alpha = .45f),
                                1f to Color.Transparent,
                                start = Offset(x - band / 2, 0f),
                                end = Offset(x + band / 2, size.height * .3f),
                            ),
                    )
                }
                val s = size.height * .42f * twinkle
                nyDrawImageAt(glint, size.width - size.height * .32f - s / 2, size.height * .26f - s / 2, s, s)
            }
        }
    }

/** New Year's look: a lit sparkler at the end of a progress line ([share] of the way along) */
fun Modifier.nyProgressSparkler(
    share: Float,
    spark: Dp = 22.dp,
): Modifier =
    if (!NyLook.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(R.drawable.ny_sparkler)
            drawWithContent {
                drawContent()
                val s = spark.toPx()
                val x = (size.width * share.coerceIn(0f, 1f)) - s / 2f
                // centred on the line's end, leaning up into the picture so it isn't cut off at the card's edge
                nyDrawImageAt(img, x.coerceIn(-s * .2f, size.width - s * .8f), size.height - s * .75f, s, s)
            }
        }
    }

/** New Year's look: the little gold star shown next to a switch that's on */
@Composable
fun NyStar(size: Dp = 22.dp) {
    Image(
        painter = painterResource(R.drawable.ny_star),
        contentDescription = null,
        modifier = Modifier.size(size),
    )
}

/**
 * New Year's look: the loading ring is a clock face. The gold ring fills round towards twelve while the minute
 * hand sweeps round, as if it's ticking round to midnight.
 */
@Composable
fun NyLoadingClock(modifier: Modifier = Modifier) {
    val loop = rememberInfiniteTransition(label = "nyClock")
    val turn by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "nyClockTurn",
    )
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        val ring = r * .16f
        drawCircle(NyLook.Panel, radius = r - ring / 2f, center = c)
        drawCircle(NyLook.Midnight, radius = r - ring / 2f, center = c, style = Stroke(ring))
        // the gold part of the ring grows round to twelve
        drawArc(
            color = NyLook.Gold,
            startAngle = -90f + 360f * turn,
            sweepAngle = 360f * (1f - turn).coerceAtLeast(.08f),
            useCenter = false,
            topLeft = Offset(c.x - r + ring / 2f, c.y - r + ring / 2f),
            size = Size((r - ring / 2f) * 2f, (r - ring / 2f) * 2f),
            style = Stroke(ring, cap = StrokeCap.Round),
        )
        // hour marks, twelve in gold
        for (k in 0 until 12) {
            val a = Math.toRadians(k * 30.0)
            val inner = r * .58f
            val outer = r * .7f
            drawLine(
                color = if (k == 0) NyLook.Gold else Color(0xFF8A93A8),
                start = Offset(c.x + (sin(a) * inner).toFloat(), c.y - (cos(a) * inner).toFloat()),
                end = Offset(c.x + (sin(a) * outer).toFloat(), c.y - (cos(a) * outer).toFloat()),
                strokeWidth = if (k == 0) r * .09f else r * .05f,
                cap = StrokeCap.Round,
            )
        }
        // the hour hand creeps up to twelve, the minute hand sweeps round
        val hour = Math.toRadians(-25.0 + 25.0 * turn)
        val minute = Math.toRadians(360.0 * turn)
        drawLine(
            Color.White,
            c,
            Offset(c.x + (sin(hour) * r * .34f).toFloat(), c.y - (cos(hour) * r * .34f).toFloat()),
            r * .1f,
            StrokeCap.Round,
        )
        drawLine(
            Color.White,
            c,
            Offset(c.x + (sin(minute) * r * .5f).toFloat(), c.y - (cos(minute) * r * .5f).toFloat()),
            r * .07f,
            StrokeCap.Round,
        )
        drawCircle(NyLook.Gold, radius = r * .08f, center = c)
    }
}

/**
 * New Year's look: each kind of Home notice gets its own small gold mark: a champagne glass for new episodes
 * and new music, a star for something ready to watch, a clock for things on the way, a mirror ball for Live TV,
 * and a sparkle for everything else.
 */
fun nyMarkFor(kind: String): Int {
    val k = kind.lowercase()
    return when {
        k.startsWith("new") -> R.drawable.ny_mark_glass
        k.startsWith("ready") || k.contains("is ready") -> R.drawable.ny_mark_star
        k.startsWith("on the way") || k.contains("arrives") -> R.drawable.ny_mark_clock
        k.contains("live") -> R.drawable.ny_mark_ball
        else -> R.drawable.ny_mark_spark
    }
}

@Composable
fun NyKindMark(
    kind: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(nyMarkFor(kind)),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

private fun DrawScope.nyDrawImageAt(
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
