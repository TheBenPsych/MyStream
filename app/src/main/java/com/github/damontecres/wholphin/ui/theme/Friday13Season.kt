package com.github.damontecres.wholphin.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.isFriday13
import com.github.damontecres.wholphin.ui.main.HomeTheme
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Benflix Friday the 13th look (approved 2026-11-17, thread "Design a Friday the 13th theme for Benflix"): only on
 * a Friday the 13th, from midnight to midnight, on TV, phone and web, unless it's switched off in Settings > Look.
 * That day it takes over any season look (Christmas, Thanksgiving, New Year's), and Kids gets it too (Ben's pick).
 * The same day rule as the web's f13.js, so every screen matches.
 */
object Friday13 {
    /** Settings > Look > Friday the 13th look is off (set by MainActivity from the saved settings) */
    var lookOff by mutableStateOf(false)

    fun isDay(date: LocalDate = LocalDate.now()): Boolean = isFriday13(date)

    fun on(date: LocalDate = LocalDate.now()): Boolean = !lookOff && isDay(date)

    const val GREETING = "Happy Friday the 13th"

    /** Under the greeting on Who's watching */
    const val LINE = "Pick a profile… if you dare"

    /** The lucky line under the greeting on Home: "Lucky you: 3 new episodes and 1 request ready" */
    fun luckyLine(
        newEpisodes: Int,
        ready: Int,
    ): String {
        val parts =
            listOfNotNull(
                newEpisodes.takeIf { it > 0 }?.let { if (it == 1) "1 new episode" else "$it new episodes" },
                ready.takeIf { it > 0 }?.let { if (it == 1) "1 request ready" else "$it requests ready" },
            )
        val line = if (parts.isEmpty()) "Lucky you: the couch is a black-cat-free zone" else "Lucky you: " + parts.joinToString(" and ")
        lastLucky = line.removePrefix("Lucky you: ").replaceFirstChar { it.uppercase() }
        return line
    }

    /** The last lucky line Home worked out (without "Lucky you: "), for the first Today card (HolidayNav.kt) */
    @Volatile
    var lastLucky: String? = null

    /**
     * The spooky but fun picks at the top of Movies (Ben: only films already on Benflix, and only if at least 4 fit).
     * Monster comedies, ghost stories and mysteries, never slashers, so the row suits Kids too.
     */
    val SpookyPicks =
        HomeTheme(
            R.string.benflix_f13_row,
            nameWords = listOf("Ghostbusters", "Addams", "Goosebumps", "Scooby-Doo"),
            titles =
                listOf(
                    "Ghostbusters",
                    "Ghostbusters II",
                    "Ghostbusters: Afterlife",
                    "Beetlejuice",
                    "Beetlejuice Beetlejuice",
                    "Hocus Pocus",
                    "The Addams Family",
                    "Addams Family Values",
                    "Hotel Transylvania",
                    "Coraline",
                    "Monster House",
                    "ParaNorman",
                    "Casper",
                    "Goosebumps",
                    "Scooby-Doo",
                    "Frankenweenie",
                    "Corpse Bride",
                    "The Nightmare Before Christmas",
                    "Monsters, Inc.",
                    "Young Frankenstein",
                    "The Haunted Mansion",
                    "Haunted Mansion",
                    "Clue",
                    "Knives Out",
                    "Glass Onion",
                    "Gremlins",
                    "The Witches",
                    "Edward Scissorhands",
                    "Arsenic and Old Lace",
                    "The Others",
                    "The Sixth Sense",
                    "Shaun of the Dead",
                    "What We Do in the Shadows",
                    "Zombieland",
                    "The Burbs",
                    "Death Becomes Her",
                    "Little Shop of Horrors",
                    "Lights Out at Camp Pinecone",
                    "Wendell & Wild",
                    "Over the Garden Wall",
                    "Spirited Away",
                    "Ghost",
                ),
        )
}

/**
 * The Friday the 13th look for the rest of the app: lucky clover green for selected things with dark words, black
 * cat black with a hint of green for other buttons, switches and panels, white words on every door, a different
 * lucky or unlucky charm on each door, and small touches that each appear once (Play flickers like an old porch
 * light, a little full moon on the end of progress, "13" next to switches that are on, a loading clock with 13
 * hours, and a four-leaf clover on notices). Kids gets it too that day (it replaces Sky and Sunshine).
 */
object F13Look {
    val on: Boolean get() = Friday13.on()

    val Green = Color(0xFFA6E36A)
    val OnGreen = Color(0xFF0F1A06)
    val Black = Color(0xFF2B2440)
    val Panel = Color(0xFF15111F)
    val PanelHigh = Color(0xFF1C1729)
    val Raised = Color(0xFF1A1526)
    val Line = Color(0xFF2E2840)
}

/** The charm that hangs on each door */
private fun BenflixDoor.f13Charm(): Int =
    when (this) {
        BenflixDoor.MOVIES -> R.drawable.f13_charm_movies
        BenflixDoor.SHOWS -> R.drawable.f13_charm_shows
        BenflixDoor.LIVE_TV -> R.drawable.f13_charm_live
        BenflixDoor.GAMES -> R.drawable.f13_charm_games
        BenflixDoor.MUSIC -> R.drawable.f13_charm_music
        BenflixDoor.BOOKS -> R.drawable.f13_charm_books
    }

/**
 * Friday the 13th look on a Home door: its charm hanging from the top edge, near the right (as on the approved
 * sheet: a 240 x 220 door with the charm's box from x 116 to 244 and y -8 to 164). On Movies a black cat hangs by
 * its paws and its tail swings.
 */
fun Modifier.f13DoorCharm(door: BenflixDoor): Modifier =
    if (!F13Look.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(door.f13Charm())
            val tail = if (door == BenflixDoor.MOVIES) ImageBitmap.imageResource(R.drawable.f13_charm_movies_tail) else null
            val swing =
                if (tail != null) {
                    val t = rememberInfiniteTransition(label = "f13Tail")
                    t.animateFloat(
                        initialValue = -7f,
                        targetValue = 9f,
                        animationSpec = infiniteRepeatable(tween(1700), RepeatMode.Reverse),
                        label = "f13TailAngle",
                    )
                } else {
                    null
                }
            drawWithContent {
                drawContent()
                val k = minOf(size.width, size.height * 240f / 220f) / 240f
                val x = size.width - 124f * k
                val y = -8f * k
                val w = 128f * k
                val h = 172f * k
                if (tail != null && swing != null) {
                    rotate(swing.value, pivot = Offset(x + (198f - 116f) * k, y + (126f + 8f) * k)) {
                        drawImageAt(tail, x, y, w, h)
                    }
                }
                drawImageAt(img, x, y, w, h)
            }
        }
    }

/** Friday the 13th look: Play flickers now and then, like an old porch light on a spooky night */
fun Modifier.f13Flicker(): Modifier =
    if (!F13Look.on) {
        this
    } else {
        composed {
            val t = rememberInfiniteTransition(label = "f13Flicker")
            val a by t.animateFloat(
                initialValue = 1f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        keyframes {
                            durationMillis = 6000
                            1f at 4200
                            .55f at 4440
                            1f at 4680
                            .6f at 4920
                            1f at 5160
                        },
                    ),
                label = "f13FlickerAlpha",
            )
            this
                .drawBehind {
                    // a green glow that flashes with each flicker
                    val glow = (1f - a) * .9f
                    if (glow > 0f) {
                        val r = maxOf(size.width, size.height) * .8f
                        drawCircle(
                            brush =
                                Brush.radialGradient(
                                    0f to F13Look.Green.copy(alpha = glow),
                                    1f to Color.Transparent,
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    radius = r,
                                ),
                            radius = r,
                            center = Offset(size.width / 2f, size.height / 2f),
                        )
                    }
                }.graphicsLayer { alpha = a }
        }
    }

/** Friday the 13th look: a little full moon on the end of a progress line ([share] of the way along) */
fun Modifier.f13ProgressMoon(
    share: Float,
    moon: Dp = 20.dp,
): Modifier =
    if (!F13Look.on) {
        this
    } else {
        composed {
            val img = ImageBitmap.imageResource(R.drawable.f13_moon)
            drawWithContent {
                drawContent()
                // the picture has a soft glow around the moon, so it's drawn a little bigger than the moon itself
                val s = moon.toPx() * 64f / 26f
                val x = (size.width * share.coerceIn(0f, 1f)) - s / 2f
                // sits on the line, leaning up into the picture so it isn't cut off at the card's edge
                drawImageAt(img, x.coerceIn(-s * .3f, size.width - s * .7f), size.height - s * .62f, s, s)
            }
        }
    }

/** Friday the 13th look: the "13" shown next to a switch that's on */
@Composable
fun F13Knob(size: Dp = 26.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(size)
                .background(Color.White, CircleShape),
    ) {
        BasicText(
            text = "13",
            style = TextStyle(color = Color(0xFF16151B), fontSize = (size.value * .5f).sp, fontWeight = FontWeight.ExtraBold),
        )
    }
}

/** Friday the 13th look: a four-leaf clover on notices */
@Composable
fun F13Clover(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Image(
        painter = painterResource(R.drawable.f13_clover),
        contentDescription = null,
        modifier = modifier.size(size, size * 34f / 32f),
    )
}

/** Friday the 13th look: the loading clock with 13 hours and a hand that goes round */
@Composable
fun F13LoadingClock(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "f13Clock")
    val angle by t.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "f13ClockAngle",
    )
    Box(modifier) {
        Image(painter = painterResource(R.drawable.f13_clock), contentDescription = null, modifier = Modifier.fillMaxSize())
        Image(
            painter = painterResource(R.drawable.f13_clock_hand),
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxSize()
                    .rotate(angle),
        )
    }
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
