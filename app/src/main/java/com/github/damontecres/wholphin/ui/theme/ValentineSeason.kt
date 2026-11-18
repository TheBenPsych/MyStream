package com.github.damontecres.wholphin.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
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
 * Benflix Valentine's Day look (approved 2026-11-17, thread "Design a Valentine's Day theme for Benflix"): on from
 * 1 to 14 February on TV, phone and web, unless it's switched off in Settings > Look. Not on Kids (Sky and Sunshine
 * stay). The same dates as the web's valentine.js, so every screen matches.
 */
object ValentineSeason {
    /** Settings > Look > Valentine's look is off (set by MainActivity from the saved settings) */
    var lookOff by mutableStateOf(false)

    const val LAST_DAY = 14

    fun isSeason(date: LocalDate = Today.date()): Boolean = date.month == Month.FEBRUARY && date.dayOfMonth <= LAST_DAY

    fun on(date: LocalDate = Today.date()): Boolean = !lookOff && isSeason(date) && !Friday13.on(date)

    /** Valentine's Day itself: rose petals fall on Who's watching all day */
    fun isDay(date: LocalDate = Today.date()): Boolean = date.month == Month.FEBRUARY && date.dayOfMonth == LAST_DAY

    const val GREETING = "Happy Valentine's Day"

    /** The big words on Who's watching: the usual question, then "Happy Valentine's Day" on the 14th */
    fun whoGreeting(date: LocalDate = Today.date()): String? = if (isDay(date)) GREETING else null

    /** The line under the greetings: "4 days to Valentine's Day", then "It's Valentine's Day" on the 14th */
    fun countdown(date: LocalDate = Today.date()): String? {
        if (!isSeason(date)) return null
        val n = LAST_DAY - date.dayOfMonth
        return when {
            n <= 0 -> "It's Valentine's Day"
            n == 1 -> "1 day to Valentine's Day"
            else -> "$n days to Valentine's Day"
        }
    }

    /** Chocolates left in the box: 13 on 1 February, one fewer each day, the last one stays for the 13th and 14th */
    fun chocolates(date: LocalDate = Today.date()): Int = (LAST_DAY - date.dayOfMonth.coerceAtMost(LAST_DAY)).coerceAtLeast(1)

    /** Today's recorded room (extras/scenes/valentine): "c13" to "c1", or "day" on the 14th */
    fun videoName(date: LocalDate = Today.date()): String = if (isDay(date)) "day" else "c${chocolates(date)}"
}

/**
 * The Valentine's look for the rest of the app: rose red for selected things, deep wine for other buttons,
 * switches and panels, white words on every door, a different love token on each door, and small touches that
 * each belong to one kind of thing (a rosy glow and a faint heart on Play, a glossy heart bead at the end of
 * progress, a heart in the knob of switches that are on, a heart that fills up while loading, and a different
 * mark for each kind of notice). Not on Kids.
 */
object ValLook {
    val on: Boolean get() = ValentineSeason.on() && !KidsLook.on

    val Rose = Color(0xFFE8405E)
    val Wine = Color(0xFF6E1F3A)
    val Panel = Color(0xFF241019)
    val PanelHigh = Color(0xFF2E1520)
    val Raised = Color(0xFF2A121D)
    val Line = Color(0xFF43202F)
    val Glow = Color(0xFFFF6E86)
}

/** The love token on each door (all pinned the same way, from the approved sheet) */
private fun BenflixDoor.valToken(): Int =
    when (this) {
        BenflixDoor.MOVIES -> R.drawable.val_charm_movies
        BenflixDoor.SHOWS -> R.drawable.val_charm_shows
        BenflixDoor.LIVE_TV -> R.drawable.val_charm_live
        BenflixDoor.GAMES -> R.drawable.val_charm_games
        BenflixDoor.MUSIC -> R.drawable.val_charm_music
        BenflixDoor.BOOKS -> R.drawable.val_charm_books
    }

/**
 * Valentine's look on a Home door: its love token (Admit Two tickets, a gold locket, a heart balloon, the ace of
 * hearts, a heart-shaped record, a love letter) near the top right, as on the approved sheet: a 240 x 220 door
 * with the token's box from x 104 to 248 and y -8 to 180.
 */
fun Modifier.valDoorCharm(door: BenflixDoor): Modifier =
    if (!ValLook.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(door.valToken())
            drawWithContent {
                drawContent()
                val k = minOf(size.width, size.height * 240f / 220f) / 240f
                val w = 144f * k
                val h = w * img.height / img.width
                // the box's right edge sits 8 units past the door's right edge, as on the sheet
                valDrawImageAt(img, size.width + 8f * k - w, -8f * k, w, h)
            }
        }
    }

/** Valentine's look: a soft rosy glow round Play, and a faint white heart in its right-hand side */
fun Modifier.valRosyGlow(): Modifier =
    if (!ValLook.on) {
        this
    } else {
        composed {
            val heart = ImageBitmap.imageResource(R.drawable.val_heart_soft)
            drawBehind {
                val r = maxOf(size.width, size.height) * .8f
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            0f to ValLook.Glow.copy(alpha = .42f),
                            .55f to ValLook.Rose.copy(alpha = .16f),
                            1f to Color.Transparent,
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = r,
                        ),
                    radius = r,
                    center = Offset(size.width / 2f, size.height / 2f),
                )
            }.drawWithContent {
                drawContent()
                val s = size.height * .5f
                valDrawImageAt(heart, size.width - s * 1.35f, (size.height - s) / 2f, s, s * heart.height / heart.width)
            }
        }
    }

/** Valentine's look: a glossy little red heart bead riding the end of a progress line ([share] of the way along) */
fun Modifier.valProgressHeart(
    share: Float,
    bead: Dp = 20.dp,
): Modifier =
    if (!ValLook.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(R.drawable.val_bead)
            drawWithContent {
                drawContent()
                val s = bead.toPx()
                val x = (size.width * share.coerceIn(0f, 1f)) - s / 2f
                // sits on the line, leaning up into the picture so it isn't cut off at the card's edge
                valDrawImageAt(img, x.coerceIn(0f, size.width - s), size.height - s * .8f, s, s)
            }
        }
    }

/** Valentine's look: the white switch knob with a red heart in it, shown next to a switch that's on */
@Composable
fun ValKnobHeart(size: Dp = 24.dp) {
    Image(
        painter = painterResource(R.drawable.val_knob),
        contentDescription = null,
        modifier = Modifier.size(size),
    )
}

/** Valentine's look: a heart that fills up from the bottom while something loads ([fill] from 0 to 1) */
@Composable
fun ValLoadingHeart(
    fill: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Image(
            painter = painterResource(R.drawable.val_load_empty),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        val full = ImageBitmap.imageResource(R.drawable.val_load_full)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .drawBehind {
                        val top = size.height * (1f - fill.coerceIn(0f, 1f))
                        clipRect(top = top) { valDrawImageAt(full, 0f, 0f, size.width, size.height) }
                    },
        )
    }
}

/** Valentine's look: each kind of notice gets its own small mark (a heart chocolate, a rose, a sealed letter...) */
fun valMarkFor(kind: String): Int {
    val k = kind.lowercase()
    return when {
        k.startsWith("new") -> R.drawable.val_mark_choc
        k.startsWith("ready") || k.contains("is ready") -> R.drawable.val_mark_rose
        k.startsWith("on the way") || k.contains("arrives") -> R.drawable.val_mark_letter
        k.contains("live") -> R.drawable.val_mark_balloon
        else -> R.drawable.val_mark_choc
    }
}

@Composable
fun ValKindMark(
    kind: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(valMarkFor(kind)),
        contentDescription = null,
        modifier = modifier.size(size * .87f, size),
    )
}

private fun DrawScope.valDrawImageAt(
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
