package com.github.damontecres.wholphin.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.Month

/**
 * Benflix Christmas look: on from 1 December to 26 December on TV, phone and web, unless it's switched off
 * in Settings > Look. The same dates as the web's xmas.js, so every screen matches. It ends on the 26th so it never overlaps the New Year's look (27 Dec to 1 Jan).
 */
object ChristmasSeason {
    /** Settings > Look > Christmas look is off (set by MainActivity from the saved settings) */
    var lookOff by mutableStateOf(false)

    /** The last day of the Christmas look; New Year's runs 27 December to 1 January */
    const val LAST_DAY = 26

    fun isSeason(date: LocalDate = LocalDate.now()): Boolean = date.month == Month.DECEMBER && date.dayOfMonth <= LAST_DAY

    fun on(date: LocalDate = LocalDate.now()): Boolean = !lookOff && isSeason(date) && !Friday13.on(date)

    /**
     * Which recording of the room plays: presents pile up through December (L1, L2, L3), Santa's sleigh
     * flies past on Christmas Eve, and on Christmas Day the presents are opened.
     */
    fun variant(date: LocalDate = LocalDate.now()): String {
        if (date.month != Month.DECEMBER) return "AFTER"
        val d = date.dayOfMonth
        return when {
            d <= 8 -> "L1"
            d <= 16 -> "L2"
            d <= 23 -> "L3"
            d == 24 -> "EVE"
            d == 25 -> "DAY"
            else -> "AFTER"
        }
    }

    fun greeting(date: LocalDate = LocalDate.now()): String =
        when {
            date.dayOfMonth == 24 -> "Merry Christmas Eve"
            else -> "Merry Christmas"
        }

    /** "12 sleeps to Christmas", up to Christmas Eve */
    fun countdown(date: LocalDate = LocalDate.now()): String? {
        if (date.month != Month.DECEMBER || date.dayOfMonth > 24) return null
        // Sleeps left: on 23 December it's one more night's sleep, then Santa comes
        return when (val sleeps = 24 - date.dayOfMonth) {
            0 -> "Santa comes tonight!"
            1 -> "1 sleep to Christmas"
            else -> "$sleeps sleeps to Christmas"
        }
    }
}

/**
 * Benflix Christmas look for the rest of the app (approved round 11): red highlights, pine green buttons and
 * panels, and one decoration used one way: the doors' glass ornament hanging from a gold pin on a top edge
 * (doors, Play, notices and the focused poster). Not on Kids (Sky and Sunshine stay).
 */
object XmasLook {
    val on: Boolean get() = ChristmasSeason.on() && !KidsLook.on

    val Red = Color(0xFFE0344E)
    val Pine = Color(0xFF1E6B45)
    val PineDark = Color(0xFF0F241A)
    val PineHigh = Color(0xFF173A28)
    val PineLine = Color(0xFF1F3D2C)
    val PineRaised = Color(0xFF143524)
    val Gold = Color(0xFFE9C877)
    val GreenBall = Color(0xFF2E9E5E)
    val RedBall = Color(0xFFE23A55)
}

/**
 * Used to hang a glass ornament from a gold pin on this thing's top edge. Turned off 2026-11-17: Ben said the
 * hanging bulb "doesn't properly attach to every kind of button", so Christmas now uses the normal white focus ring
 * everywhere and its decoration lives on the rail and Home instead (ChristmasNav.kt). Kept so callers stay simple.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.xmasOrnament(
    color: Color,
    ball: Dp = 22.dp,
    string: Dp = 8.dp,
    fromEnd: Dp = 30.dp,
    band: Boolean = false,
): Modifier = this

/** The ornament itself: pin, gold thread, cap, glass ball with a soft shine and a shadow */
fun DrawScope.drawXmasOrnament(
    x: Float,
    top: Float,
    radius: Float,
    string: Float,
    color: Color,
    band: Boolean = false,
) {
    val gold = XmasLook.Gold
    val goldDark = Color(0xFF9A7426)
    val capH = radius * 0.36f
    val cy = top + string + capH + radius * 0.92f
    // shadow
    drawCircle(
        brush =
            Brush.radialGradient(
                0f to Color.Black.copy(alpha = .45f),
                1f to Color.Transparent,
                center = Offset(x + radius * .25f, cy + radius * .4f),
                radius = radius * 1.35f,
            ),
        radius = radius * 1.35f,
        center = Offset(x + radius * .25f, cy + radius * .4f),
    )
    // thread and pin
    drawLine(gold, Offset(x, top), Offset(x, top + string + capH * .5f), strokeWidth = maxOf(1.5f, radius * .09f))
    drawCircle(gold, radius = maxOf(2.5f, radius * .2f), center = Offset(x, top))
    // glass
    val light = lerp(color, Color.White, .62f)
    val dark = lerp(color, Color.Black, .55f)
    val darkest = lerp(color, Color.Black, .8f)
    drawCircle(
        brush =
            Brush.radialGradient(
                0f to light,
                .3f to color,
                .78f to dark,
                1f to darkest,
                center = Offset(x - radius * .3f, cy - radius * .4f),
                radius = radius * 1.6f,
            ),
        radius = radius,
        center = Offset(x, cy),
    )
    if (band) {
        // the doors' gold band round the middle
        val bh = radius * .18f
        drawArc(
            color = gold,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(x - radius * .99f, cy - bh * 1.6f),
            size = Size(radius * 1.98f, bh * 3.2f),
            style = Stroke(width = bh),
        )
    }
    // shine
    drawOval(
        color = Color.White.copy(alpha = .7f),
        topLeft = Offset(x - radius * .68f, cy - radius * .62f),
        size = Size(radius * .55f, radius * .3f),
    )
    // cap
    drawRoundRect(
        brush = Brush.horizontalGradient(listOf(goldDark, gold, goldDark), startX = x - radius * .28f, endX = x + radius * .28f),
        topLeft = Offset(x - radius * .28f, top + string),
        size = Size(radius * .56f, capH),
        cornerRadius = CornerRadius(radius * .08f),
    )
}

/** Christmas look on a door tile: no hanging ornament any more (see [xmasOrnament]) */
@Suppress("UNUSED_PARAMETER")
fun Modifier.xmasDoorOrnament(door: BenflixDoor): Modifier = this

/** Christmas look on the poster or card the remote is on: just the normal focus ring now (see [xmasOrnament]) */
@Suppress("UNUSED_PARAMETER")
fun Modifier.xmasFocusOrnament(focused: Boolean): Modifier = this
