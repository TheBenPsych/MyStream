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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import java.time.Month
import kotlin.math.roundToInt

/**
 * Benflix St. Patrick's Day look (approved 2026-11-17, thread "Design a St. Patrick's Day theme for Benflix"): on
 * from 10 to 17 March on TV, phone and web, unless it's switched off in Settings > Look. The same dates as the
 * web's stpatricks.js, so every screen matches. Kids gets it too (Ben picked that on the sheet).
 */
object StPatricksSeason {
    /** Settings > Look > St. Patrick's Day look is off (set by MainActivity from the saved settings) */
    var lookOff by mutableStateOf(false)

    fun isSeason(date: LocalDate = LocalDate.now()): Boolean = date.month == Month.MARCH && date.dayOfMonth in 10..17

    fun on(date: LocalDate = LocalDate.now()): Boolean = !lookOff && isSeason(date)

    /** Which recording of the room plays: "week" 10 to 16 March (rain), "day" on 17 March (rainbow and falling shamrocks) */
    fun variant(date: LocalDate = LocalDate.now()): String = if (date.month == Month.MARCH && date.dayOfMonth == 17) "day" else "week"

    /** Who's watching's big words: "Who's watching?" all week, "Happy St. Patrick's Day!" on the day */
    fun whoGreeting(date: LocalDate = LocalDate.now()): String = if (variant(date) == "day") DAY_GREETING else WHO

    private const val DAY_GREETING = "Happy St. Patrick\u2019s Day!"
    private const val WHO = "Who\u2019s watching?"

    /** The line under the greeting in the week before: "3 days to St. Patrick's Day" (none on the day) */
    fun countdown(date: LocalDate = LocalDate.now()): String? {
        if (!isSeason(date) || variant(date) == "day") return null
        val days = 17 - date.dayOfMonth
        return if (days == 1) "St. Patrick’s Day is tomorrow" else "$days days to St. Patrick’s Day"
    }

    const val GREETING = "Happy St. Patrick’s Day"
}

/**
 * The St. Patrick's Day look for the rest of the app: bright clover green for selected things (with dark words on
 * it), deep emerald for other buttons, switches and panels, white words on every door, a different lucky charm
 * hanging on each door, and small touches that each belong to one kind of thing (a gold four-leaf clover on Play's
 * corner with a soft sheen sweeping across, a gold coin at the end of progress, a shamrock in the knob of switches
 * that are on, a rainbow loader that sweeps round into a pot of gold, and a different mark for each kind of Home
 * notice). On Kids too.
 */
object SpLook {
    val on: Boolean get() = StPatricksSeason.on()

    val Clover = Color(0xFF3DBE6C)
    val OnClover = Color(0xFF04210F)
    val Emerald = Color(0xFF16603A)
    val Panel = Color(0xFF0B2219)
    val PanelHigh = Color(0xFF113021)
    val Raised = Color(0xFF0F2A1E)
    val Line = Color(0xFF1E4A34)

    /** The rainbow in the loader and the "on the way" mark */
    val Rainbow =
        listOf(
            Color(0xFFE5484D),
            Color(0xFFF28C28),
            Color(0xFFF2C94C),
            Color(0xFF3CC46A),
            Color(0xFF4A90E2),
            Color(0xFF8E5BD8),
        )
}

/** The lucky charm that hangs on each door */
private fun BenflixDoor.spCharm(): Int =
    when (this) {
        BenflixDoor.MOVIES -> R.drawable.sp_charm_movies
        BenflixDoor.SHOWS -> R.drawable.sp_charm_shows
        BenflixDoor.LIVE_TV -> R.drawable.sp_charm_live
        BenflixDoor.GAMES -> R.drawable.sp_charm_games
        BenflixDoor.MUSIC -> R.drawable.sp_charm_music
        BenflixDoor.BOOKS -> R.drawable.sp_charm_books
    }

/**
 * St. Patrick's Day look on a Home door: its lucky charm in the top right, as on the approved sheet (a 240 x 220
 * door with the charm's picture covering x 116 to 248 and y -6 to 154, so the hanging pin pokes just past the edge).
 */
fun Modifier.spDoorCharm(door: BenflixDoor): Modifier =
    if (!SpLook.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(door.spCharm())
            drawWithContent {
                drawContent()
                val k = minOf(size.width, size.height * 240f / 220f) / 240f
                val w = 132f * k
                val h = w * img.height / img.width
                spDrawImageAt(img, size.width + 8f * k - w, -6f * k, w, h)
            }
        }
    }

/** St. Patrick's Day look: a gold four-leaf clover tucked on Play's corner, and a soft sheen sweeping across now and then */
fun Modifier.spPlayClover(corner: Dp = 10.dp): Modifier =
    if (!SpLook.on) {
        this
    } else {
        composed {
            val clover = ImageBitmap.imageResource(R.drawable.sp_play_clover)
            val loop = rememberInfiniteTransition(label = "spSheen")
            // the sheen crosses in 1 s, then rests for 3.5 s
            val sweep by loop.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        keyframes {
                            durationMillis = 4500
                            0f at 0
                            1f at 1000
                            1f at 4500
                        },
                    ),
                label = "spSheenSweep",
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
                                .5f to Color.White.copy(alpha = .4f),
                                1f to Color.Transparent,
                                start = Offset(x - band / 2, 0f),
                                end = Offset(x + band / 2, size.height * .3f),
                            ),
                    )
                }
                // sits over the top right corner, like on the sheet
                val s = size.height * .62f
                spDrawImageAt(clover, size.width - s * .62f, -s * .38f, s, s)
            }
        }
    }

/** St. Patrick's Day look: a gold coin at the end of a progress line ([share] of the way along) */
fun Modifier.spProgressCoin(
    share: Float,
    coin: Dp = 20.dp,
): Modifier =
    if (!SpLook.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(R.drawable.sp_coin)
            drawWithContent {
                drawContent()
                val s = coin.toPx()
                val x = (size.width * share.coerceIn(0f, 1f)) - s / 2f
                // centred on the line's end, leaning up so it isn't cut off at the card's edge
                spDrawImageAt(img, x.coerceIn(-s * .2f, size.width - s * .8f), size.height - s * .75f, s, s)
            }
        }
    }

/** St. Patrick's Day look: a white knob with a green shamrock, shown next to a switch that's on */
@Composable
fun SpKnob(size: Dp = 22.dp) {
    Image(
        painter = painterResource(R.drawable.sp_knob),
        contentDescription = null,
        modifier = Modifier.size(size),
    )
}

/** St. Patrick's Day look: loading is a rainbow sweeping round the ring into a pot of gold in the middle */
@Composable
fun SpLoadingRainbow(modifier: Modifier = Modifier) {
    val loop = rememberInfiniteTransition(label = "spRainbow")
    val turn by loop.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "spRainbowTurn",
    )
    val bob by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "spRainbowBob",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            val band = r * .3f
            drawCircle(SpLook.Emerald, radius = r - band / 2f, center = c, style = Stroke(band))
            // six thin coloured bands, a quarter of the ring long, sweeping round
            val w = band / 6f
            SpLook.Rainbow.forEachIndexed { i, col ->
                val rr = r - w * (i + .5f)
                drawArc(
                    color = col,
                    startAngle = turn - 90f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(c.x - rr, c.y - rr),
                    size = Size(rr * 2f, rr * 2f),
                    style = Stroke(w + .6f, cap = StrokeCap.Butt),
                )
            }
        }
        Image(
            painter = painterResource(R.drawable.sp_mark_pot),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(.5f + .04f * bob),
        )
    }
}

/**
 * St. Patrick's Day look: each kind of Home notice gets its own small mark: a horseshoe for new episodes and
 * new things, a pot of gold for something ready to watch, a rainbow for things on the way, and a top hat for
 * Live TV and everything else.
 */
fun spMarkFor(kind: String): Int {
    val k = kind.lowercase()
    return when {
        k.startsWith("ready") || k.contains("is ready") -> R.drawable.sp_mark_pot
        k.startsWith("on the way") || k.contains("arrives") -> R.drawable.sp_mark_rainbow
        k.startsWith("new") -> R.drawable.sp_mark_horseshoe
        else -> R.drawable.sp_mark_hat
    }
}

@Composable
fun SpKindMark(
    kind: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(spMarkFor(kind)),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

private fun DrawScope.spDrawImageAt(
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
