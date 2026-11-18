package com.github.damontecres.wholphin.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import java.time.LocalDate
import java.time.Month
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Benflix Star Wars Day (approved 2026-11-17, thread "Design a Star Wars Day theme for Benflix"): on 4 May only,
 * on TV, phone and web, Kids too, unless it's switched off in Settings > Look. The same day as the web's
 * starwars.js, so every screen matches. All the drawings are our own: no film logos, lettering or designs.
 */
object StarWarsDay {
    /** Settings > Look > Star Wars Day look is off (set by MainActivity from the saved settings) */
    var lookOff by mutableStateOf(false)

    fun isDay(date: LocalDate = Today.date()): Boolean = date.month == Month.MAY && date.dayOfMonth == 4

    fun on(date: LocalDate = Today.date()): Boolean = !lookOff && isDay(date)

    const val GREETING = "May the 4th be with you"

    /** The yellow of the opening text crawl, used for the greeting */
    val CrawlYellow = Color(0xFFFFE81F)
}

/** The crawl-style lettering (News Cycle, OFL) used for the greeting and the opening crawl */
val StarWarsFont = FontFamily(Font(R.font.holiday_newscycle, FontWeight.Bold))

/**
 * The Star Wars Day look for the rest of the app (Ben picked "dark side red and black"): laser red for selected
 * things with white words, glossy black for other buttons, switches and panels, white words on every door, a
 * different galaxy moment in each door's corner, and small touches: a red glow and light-speed streaks on Play,
 * a laser sword for progress lines, a glowing knob by switches that are on, light speed inside the loading
 * ring, and a little laser sword on notices. The laser swords glow in the colour of the side each profile
 * picked ([SwSides]). Kids gets it too that day.
 */
object SwLook {
    val on: Boolean get() = StarWarsDay.on()

    val Red = Color(0xFFFF2A2A)
    val Button = Color(0xFF2A2A2E)
    val Panel = Color(0xFF0E0E10)
    val PanelHigh = Color(0xFF18181B)
    val Raised = Color(0xFF151518)
    val Line = Color(0xFF26262A)
}

/**
 * Each profile picks a side for Star Wars Day: the light side (blue laser swords) or the dark side (red).
 * Kept on this TV or phone, like the picture picked in Benflix ("benflix_sw_sides": user id -> true for dark).
 */
object SwSides {
    val LightBlade = Color(0xFF4FB8FF)
    val DarkBlade = Color(0xFFFF2A2A)

    private val dark = mutableStateMapOf<UUID, Boolean>()
    private var prefs: android.content.SharedPreferences? = null

    @Synchronized
    fun init(context: android.content.Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("benflix_sw_sides", android.content.Context.MODE_PRIVATE)
        prefs = p
        p.all.forEach { (key, value) ->
            val id = runCatching { UUID.fromString(key) }.getOrNull()
            if (id != null && value is Boolean) dark[id] = value
        }
    }

    fun isDark(id: UUID?): Boolean = id != null && dark[id] == true

    fun set(
        id: UUID?,
        isDark: Boolean,
    ) {
        if (id == null) return
        dark[id] = isDark
        prefs?.edit()?.putBoolean(id.toString(), isDark)?.apply()
    }

    fun blade(id: UUID?): Color = if (isDark(id)) DarkBlade else LightBlade

    /** The profile that's watching now */
    fun currentUser(): UUID? =
        com.github.damontecres.wholphin.services.BenflixWatchTime.instance
            ?.currentUserId()

    /** The blade colour of the profile that's watching now */
    val currentBlade: Color get() = blade(currentUser())
}

/** The galaxy moment drawn in each door's top-right corner */
private fun BenflixDoor.moment(): Int =
    when (this) {
        BenflixDoor.MOVIES -> R.drawable.sw_moment_movies
        BenflixDoor.SHOWS -> R.drawable.sw_moment_shows
        BenflixDoor.LIVE_TV -> R.drawable.sw_moment_live
        BenflixDoor.GAMES -> R.drawable.sw_moment_games
        BenflixDoor.MUSIC -> R.drawable.sw_moment_music
        BenflixDoor.BOOKS -> R.drawable.sw_moment_books
    }

/**
 * Star Wars Day on a Home door: its galaxy moment in the top-right corner, behind the icon and words (the
 * yellow text crawl on Movies, twin suns over a desert home on Shows, a blue hologram on Live TV, crossed
 * blue and red laser swords on Games, light speed on Music, a glowing cube on Books). The picture is a whole
 * 240 x 220 door from the approved sheet, see-through except its corner, so it's pinned to the top right.
 */
fun Modifier.swDoorMoment(door: BenflixDoor): Modifier =
    if (!SwLook.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(door.moment())
            drawBehind {
                val k = minOf(size.width / 240f, size.height / 220f)
                val w = 240f * k
                val h = 220f * k
                drawImageAt(img, size.width - w, 0f, w, h)
            }
        }
    }

/** Star Wars Day: a laser-red glow behind Play, and three light-speed streaks rushing in from its left */
fun Modifier.swPlayGlow(): Modifier =
    if (!SwLook.on) {
        this
    } else {
        drawBehind {
            val s = size.height / 84f
            for (k in 3 downTo 1) {
                val g = k * 5f * s
                drawRoundRect(
                    color = SwLook.Red.copy(alpha = .16f),
                    topLeft = Offset(-g, -g),
                    size = Size(size.width + g * 2, size.height + g * 2),
                    cornerRadius = CornerRadius(20f * s + g),
                )
            }
            for (k in 0..2) {
                val y = (22f + k * 20f) * s
                drawLine(
                    color = Color.White.copy(alpha = .8f - k * .15f),
                    start = Offset((-60f + k * 14f) * s, y),
                    end = Offset(-16f * s, y),
                    strokeWidth = (5f - k) * s,
                    cap = StrokeCap.Round,
                )
            }
        }
    }

/**
 * Draws a laser sword lying flat: a metal hilt with grip ridges from [x] (left), then a glowing blade to [end]
 * with a white-hot core. [bw] is the blade's thickness. The same shape as the approved sheet's laser sword.
 */
fun DrawScope.drawLaserSword(
    x: Float,
    y: Float,
    hilt: Float,
    end: Float,
    bw: Float,
    color: Color,
    glow: Float = 1f,
    withHilt: Boolean = true,
) {
    val b0 = x + hilt - 2f
    val len = end - b0
    if (len > 1f) {
        // the glow: soft layers getting thinner and brighter
        for ((t, a) in listOf(3.4f to .10f, 2.6f to .16f, 1.9f to .26f)) {
            val h = bw * t
            drawRoundRect(
                color.copy(alpha = a * glow),
                Offset(b0 - (h - bw) / 2f, y - h / 2f),
                Size(len + (h - bw), h),
                CornerRadius(h / 2f),
            )
        }
        val c = bw * 1.5f
        drawRoundRect(color, Offset(b0, y - c / 2f), Size(len, c), CornerRadius(c / 2f))
        val w = bw * .8f
        drawRoundRect(Color.White, Offset(b0, y - w / 2f), Size((len - 2f).coerceAtLeast(1f), w), CornerRadius(w / 2f))
    }
    if (!withHilt) return
    // the hilt
    val hh = bw * 2.5f
    drawRoundRect(
        brush =
            Brush.verticalGradient(
                listOf(Color(0xFF3A404C), Color(0xFFE4E9F0), Color(0xFF9AA4B4), Color(0xFF4E5868)),
                startY = y - hh / 2f,
                endY = y + hh / 2f,
            ),
        topLeft = Offset(x, y - hh / 2f),
        size = Size(hilt, hh),
        cornerRadius = CornerRadius(2f),
    )
    for (k in 0 until 5) {
        drawRect(Color(0xFF1A1E26), Offset(x + 4f + k * hilt * .11f, y - hh / 2f), Size(hilt * .05f, hh))
    }
    drawRoundRect(Color(0xFFC0302A), Offset(x + hilt * .66f, y - bw * 1.55f), Size(hilt * .14f, bw * .5f), CornerRadius(1f))
    drawRoundRect(Color(0xFFC9D2DE), Offset(x + hilt - 5f, y - bw * 1.45f), Size(5f, bw * 2.9f), CornerRadius(1f))
}

/**
 * Star Wars Day: the progress line is a laser sword: the hilt sits at the start and the blade reaches as far as
 * you've watched ([share] of the way), in the colour of your side.
 */
fun Modifier.swProgressSaber(
    share: Float,
    blade: Color,
): Modifier =
    if (!SwLook.on) {
        this
    } else {
        drawWithContent {
            drawContent()
            val bw = (size.height * .9f).coerceAtLeast(2.5f)
            val hilt = bw * 7f
            val end = (size.width * share.coerceIn(0f, 1f)).coerceAtLeast(hilt + 2f)
            drawLaserSword(0f, size.height / 2f, hilt, end, bw, blade)
        }
    }

/** Star Wars Day: a small laser sword lies along the top of a notice, in the colour of your side */
fun Modifier.swNoticeSaber(): Modifier =
    if (!SwLook.on) {
        this
    } else {
        composed {
            val blade = SwSides.currentBlade
            drawWithContent {
                drawContent()
                val bw = 3.dp.toPx()
                val hilt = 22.dp.toPx()
                val x = 18.dp.toPx()
                drawLaserSword(x, 0f, hilt, x + hilt + 70.dp.toPx(), bw, blade)
            }
        }
    }

/** Star Wars Day: the glowing knob shown next to a switch that's on, in the colour of your side */
@Composable
fun SwSwitchGlow(size: Dp = 22.dp) {
    val blade = SwSides.currentBlade
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2f
        drawCircle(blade.copy(alpha = .22f), r)
        drawCircle(blade.copy(alpha = .45f), r * .72f)
        drawCircle(Color.White, r * .42f)
    }
}

/** Star Wars Day: light-speed streaks inside the loading ring */
@Composable
fun SwLoadingStreaks(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.sw_hyperspace),
        contentDescription = null,
        modifier = modifier,
    )
}

private fun DrawScope.drawImageAt(
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
