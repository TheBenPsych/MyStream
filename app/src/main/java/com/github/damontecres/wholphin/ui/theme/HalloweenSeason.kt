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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import kotlin.math.roundToInt

/**
 * Benflix Halloween look (approved 2026-11-17, thread "Design a Halloween theme for Benflix"): on from 1 to 31
 * October on TV, phone and web, unless it's switched off in Settings > Look. Thanksgiving starts on 16 November,
 * so the two never overlap. The same dates as the web's halloween.js, so every screen matches.
 */
object HalloweenSeason {
    /** Settings > Look > Halloween look is off (set by MainActivity from the saved settings) */
    var lookOff by mutableStateOf(false)

    fun isSeason(date: LocalDate = Today.date()): Boolean = date.month == Month.OCTOBER

    fun on(date: LocalDate = Today.date()): Boolean = !lookOff && isSeason(date) && !Friday13.on(date)

    /** Halloween night: 31 October from 5 PM (the greeting gets a "!" and bats fly across the room) */
    fun isNight(now: LocalDateTime = LocalDateTime.now()): Boolean = now.month == Month.OCTOBER && now.dayOfMonth == 31 && now.hour >= 17

    /**
     * Which recording of the room plays: the pumpkins get carved as October goes on. "whole" 1 to 10 Oct,
     * "half" (two carved) 11 to 17 Oct, "carved" from 18 Oct, and "night" on 31 Oct after 5 PM.
     */
    fun stage(now: LocalDateTime = LocalDateTime.now()): String =
        when {
            isNight(now) -> "night"
            now.dayOfMonth <= 10 -> "whole"
            now.dayOfMonth <= 17 -> "half"
            else -> "carved"
        }

    /** Who's watching's big words: "Happy Halloween", and "Happy Halloween!" on Halloween night */
    fun whoGreeting(now: LocalDateTime = LocalDateTime.now()): String = if (isNight(now)) "$GREETING!" else GREETING

    /** The line under Who's watching's greeting: "14 days to Halloween", nothing on Halloween night */
    fun countdown(now: LocalDateTime = LocalDateTime.now()): String? {
        if (now.month != Month.OCTOBER || isNight(now)) return null
        return when (val days = 31 - now.dayOfMonth) {
            0 -> "Halloween is today!"
            1 -> "Halloween is tomorrow"
            else -> "$days days to Halloween"
        }
    }

    /** Home's line under "Happy Halloween, Ben": the countdown, then "Happy Halloween!" on the 31st */
    fun homeLine(now: LocalDateTime = LocalDateTime.now()): String? {
        if (now.month != Month.OCTOBER) return null
        return if (now.dayOfMonth == 31) "$GREETING!" else countdown(now)
    }

    const val GREETING = "Happy Halloween"
}

/**
 * The Halloween look for the rest of the app: glowing pumpkin orange for selected things (with dark words on it),
 * deep witch purple for other buttons, switches and panels, white words on every door, and a different spooky
 * charm on each door. Small touches each belong to one kind of thing: Play flickers like candlelight, a little
 * ghost bobs on the end of progress, switches that are on get a tiny pumpkin, loading is a bat flying round the
 * ring, and each kind of Home notice gets its own mark (moon, sweet, spider, cauldron).
 * Kids keeps Sky and Sunshine and gets the friendly charms on its doors ([HwLook.charmsOn]).
 */
object HwLook {
    /** The colours and touches (not on Kids) */
    val on: Boolean get() = HalloweenSeason.on() && !KidsLook.on

    /** The door charms: everyone, with Kids getting the friendly ones */
    val charmsOn: Boolean get() = HalloweenSeason.on()

    val Orange = Color(0xFFF07A1E)
    val OnOrange = Color(0xFF1C0E04)
    val Purple = Color(0xFF4A2A78)
    val Panel = Color(0xFF170F24)
    val PanelHigh = Color(0xFF20162F)
    val Raised = Color(0xFF1C1329)
    val Line = Color(0xFF30204A)
    val CandleGlow = Color(0xFFFFB04A)
}

/** The charm that hangs on each door: spooky for grown ups, friendly on Kids */
private fun BenflixDoor.hwCharm(kids: Boolean): Int =
    if (kids) {
        when (this) {
            BenflixDoor.MOVIES -> R.drawable.hw_kids_movies
            BenflixDoor.SHOWS -> R.drawable.hw_kids_shows
            BenflixDoor.LIVE_TV -> R.drawable.hw_kids_live
            BenflixDoor.GAMES -> R.drawable.hw_kids_games
            BenflixDoor.MUSIC -> R.drawable.hw_kids_music
            BenflixDoor.BOOKS -> R.drawable.hw_kids_books
        }
    } else {
        when (this) {
            BenflixDoor.MOVIES -> R.drawable.hw_charm_movies
            BenflixDoor.SHOWS -> R.drawable.hw_charm_shows
            BenflixDoor.LIVE_TV -> R.drawable.hw_charm_live
            BenflixDoor.GAMES -> R.drawable.hw_charm_games
            BenflixDoor.MUSIC -> R.drawable.hw_charm_music
            BenflixDoor.BOOKS -> R.drawable.hw_charm_books
        }
    }

/**
 * Halloween look on a Home door: its charm hanging from the top edge near the right (as on the approved sheet:
 * a 240 x 220 door, each charm's picture covering x 100 to 248 and y -8 to 172 of it).
 */
fun Modifier.hwDoorCharm(
    door: BenflixDoor,
    kids: Boolean = KidsLook.on,
): Modifier =
    if (!HwLook.charmsOn) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(door.hwCharm(kids))
            drawWithContent {
                drawContent()
                val k = minOf(size.width, size.height * 240f / 220f) / 240f
                val w = 148f * k
                val h = w * img.height / img.width
                hwDrawImageAt(img, size.width - 140f * k, -8f * k, w, h)
            }
        }
    }

/** Halloween look: Play glows and flickers softly, like a candle inside a jack-o'-lantern */
fun Modifier.hwCandleFlicker(corner: Dp = 10.dp): Modifier =
    if (!HwLook.on) {
        this
    } else {
        composed {
            val loop = rememberInfiniteTransition(label = "hwFlicker")
            val glow by loop.animateFloat(
                initialValue = .35f,
                targetValue = .35f,
                animationSpec =
                    infiniteRepeatable(
                        keyframes {
                            durationMillis = 2200
                            .35f at 0
                            .7f at 360
                            .45f at 700
                            .8f at 1100
                            .4f at 1460
                            .6f at 1830
                            .35f at 2200
                        },
                    ),
                label = "hwFlickerGlow",
            )
            drawBehind {
                val r = corner.toPx()
                for (i in 3 downTo 1) {
                    val grow = i * 5.dp.toPx()
                    drawRoundRect(
                        color = HwLook.CandleGlow.copy(alpha = glow * .16f),
                        topLeft = Offset(-grow, -grow),
                        size = Size(size.width + grow * 2, size.height + grow * 2),
                        cornerRadius = CornerRadius(r + grow),
                    )
                }
            }
        }
    }

/** Halloween look: a little ghost bobbing on the end of a progress line ([share] of the way along) */
fun Modifier.hwProgressGhost(
    share: Float,
    ghost: Dp = 24.dp,
): Modifier =
    if (!HwLook.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(R.drawable.hw_ghost)
            val loop = rememberInfiniteTransition(label = "hwGhost")
            val bob by loop.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
                label = "hwGhostBob",
            )
            drawWithContent {
                drawContent()
                val h = ghost.toPx()
                val w = h * img.width / img.height
                val x = (size.width * share.coerceIn(0f, 1f)) - w / 2f
                // sits on the end of the line, floating up into the picture so it isn't cut off at the card's edge
                hwDrawImageAt(img, x.coerceIn(-w * .2f, size.width - w * .8f), size.height - h * .85f - bob * 4.dp.toPx(), w, h)
            }
        }
    }

/** Halloween look: the tiny pumpkin shown next to a switch that's on */
@Composable
fun HwPumpkin(size: Dp = 22.dp) {
    Image(
        painter = painterResource(R.drawable.hw_knob),
        contentDescription = null,
        modifier = Modifier.size(size * 57f / 52f, size),
    )
}

/** Halloween look: loading is a bat flying round a pumpkin-orange ring on witch purple */
@Composable
fun HwLoadingBat(modifier: Modifier = Modifier) {
    val bat = ImageBitmap.imageResource(R.drawable.hw_bat)
    val loop = rememberInfiniteTransition(label = "hwBat")
    val turn by loop.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "hwBatTurn",
    )
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val ring = r * .16f
        val c = Offset(size.width / 2f, size.height / 2f)
        val inner = r - ring / 2f - r * .14f
        drawCircle(HwLook.Purple, radius = inner, center = c, style = Stroke(ring))
        rotate(turn, c) {
            drawArc(
                color = HwLook.Orange,
                startAngle = 180f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(c.x - inner, c.y - inner),
                size = Size(inner * 2f, inner * 2f),
                style = Stroke(ring, cap = StrokeCap.Round),
            )
            // the bat leads the orange arc round the top of the ring
            val w = r * 1.1f
            val h = w * bat.height / bat.width
            hwDrawImageAt(bat, c.x - w / 2f, c.y - inner - h / 2f, w, h)
        }
    }
}

/**
 * Halloween look: each kind of Home notice gets its own mark: a crescent moon for new episodes and new things,
 * a wrapped sweet for something ready to watch, a spider on its thread for things on the way, and a bubbling
 * cauldron for Live TV and everything else.
 */
fun hwMarkFor(kind: String): Int {
    val k = kind.lowercase()
    return when {
        k.startsWith("ready") || k.contains("is ready") -> R.drawable.hw_mark_sweet
        k.startsWith("on the way") || k.contains("arrives") -> R.drawable.hw_mark_spider
        k.startsWith("new") -> R.drawable.hw_mark_moon
        else -> R.drawable.hw_mark_cauldron
    }
}

@Composable
fun HwKindMark(
    kind: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(hwMarkFor(kind)),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

private fun DrawScope.hwDrawImageAt(
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
