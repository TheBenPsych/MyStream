package com.github.damontecres.wholphin.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.github.damontecres.wholphin.services.BenflixBedtime
import com.github.damontecres.wholphin.services.KidsMode
import kotlinx.coroutines.delay
import java.time.LocalTime

/** The colours one Kids look uses (see [KidsLook]) */
data class KidsPalette(
    val tray: Color,
    val text: Color,
    val soft: Color,
    val muted: Color,
    val raised: Color,
    val panel: Color,
    val panelHigh: Color,
    val line: Color,
    val focusRow: Color,
    /** The "you are here" ring, the big Play button, progress lines and the KIDS badge */
    val accent: Color,
    /** Words on top of [accent] */
    val onAccent: Color,
    val movies: Color,
    val onMovies: Color,
    val shows: Color,
    val onShows: Color,
    val games: Color,
    val onGames: Color,
)

/**
 * The Kids look (Ben's pick kc-sky, 13 Nov 2026): "Sky and Sunshine". While Kids or Big kids is on, the
 * whole app turns sky blue with sunny yellow for the "you are here" ring, the Play button and progress lines,
 * and uses round friendly letters (Fredoka). It goes everywhere Kids goes (Ben: kc-q-where).
 * From 1 hour before bedtime until 6 AM it switches to the calmer "Night Light" colours (Ben: kc-q-night).
 *
 * Every Benflix colour ([BenflixColors]) and the font ([BenflixFont]) ask this, so nothing else has to know.
 * It's Compose state, so screens redraw by themselves when Kids is switched on or off, or night comes.
 */
object KidsLook {
    /** Kids or Big kids is on */
    var on by mutableStateOf(false)
        private set

    /** The Night Light colours are showing */
    var night by mutableStateOf(false)
        private set

    /** The colours to use right now, or null for the normal grown-up look */
    val palette: KidsPalette? get() =
        if (!on) {
            null
        } else if (night) {
            NightLight
        } else {
            SkyAndSunshine
        }

    /** Called whenever Kids is switched on or off, and once a minute (for the Night Light) */
    fun refresh(
        kids: Boolean = KidsMode.isActive,
        now: LocalTime = LocalTime.now(),
    ) {
        if (on != kids) on = kids
        val nightNow = kids && isNightLight(now)
        if (night != nightNow) night = nightNow
    }

    /** From 1 hour before bedtime until 6 AM */
    fun isNightLight(now: LocalTime): Boolean {
        val start = BenflixBedtime.BEDTIME.minusHours(1)
        return !now.isBefore(start) || now.isBefore(BenflixBedtime.WAKE_UP)
    }

    val SkyAndSunshine =
        KidsPalette(
            tray = Color(0xFF1655CF),
            text = Color(0xFFFFFFFF),
            soft = Color(0xFFEAF2FF),
            muted = Color(0xFFD3E3FD),
            raised = Color(0xFF2A68DA),
            panel = Color(0xFF1F5ED6),
            panelHigh = Color(0xFF2D6DE0),
            line = Color(0xFF4A82E4),
            focusRow = Color(0xFF3B78E4),
            accent = Color(0xFFFFD21F),
            onAccent = Color(0xFF231A00),
            movies = Color(0xFFFFD21F),
            onMovies = Color(0xFF2A1E00),
            shows = Color(0xFFFFFFFF),
            onShows = Color(0xFF1450C8),
            games = Color(0xFFFF6F91),
            onGames = Color(0xFF2A0612),
        )

    val NightLight =
        KidsPalette(
            tray = Color(0xFF1E2257),
            text = Color(0xFFFFFFFF),
            soft = Color(0xFFE2E5F8),
            muted = Color(0xFFC3C8EC),
            raised = Color(0xFF2B3075),
            panel = Color(0xFF2A2F72),
            panelHigh = Color(0xFF353B85),
            line = Color(0xFF464D99),
            focusRow = Color(0xFF3F4694),
            accent = Color(0xFFFFE9A8),
            onAccent = Color(0xFF2A2000),
            movies = Color(0xFFFFD6A5),
            onMovies = Color(0xFF2A1700),
            shows = Color(0xFFBDE0FE),
            onShows = Color(0xFF06213A),
            games = Color(0xFFFFC8DD),
            onGames = Color(0xFF2E0A19),
        )
}

/** Keeps [KidsLook] in step with Kids mode and the clock. Put once near the top of each screen. */
@Composable
fun KidsLookWatcher() {
    val kids by KidsMode.active.collectAsState()
    LaunchedEffect(kids) {
        while (true) {
            KidsLook.refresh(kids)
            delay(60_000)
        }
    }
}
