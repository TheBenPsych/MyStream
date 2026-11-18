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

/** Glowfin for Kids' three colours (K1) */
val KidsLilac = Color(0xFFC48BFF)
val KidsSky = Color(0xFF5CC8FF)
val KidsMint = Color(0xFF7FF5E8)

/**
 * The Kids look: "Glowfin for Kids" (Ben's pick K1, 18 Nov 2026; it replaced kc-sky). While Kids or Big kids
 * is on, the whole app turns deep navy with lilac, sky blue and mint, a white-and-mint ring on the thing the
 * remote is on, and round friendly letters (Fredoka). It goes everywhere Kids goes (Ben: kc-q-where).
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
            BrightGlowfin
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

    /**
     * Glowfin for Kids (Ben's pick K1, ui-17, 18 Nov 2026): Glowfin's own colours made brighter and softer,
     * lilac, sky blue and mint on deep navy, with a white-and-mint glow ring.
     */
    val BrightGlowfin =
        KidsPalette(
            tray = Color(0xFF141A3A),
            text = Color(0xFFFFFFFF),
            soft = Color(0xFFE8EBFF),
            muted = Color(0xFFB9C2FF),
            raised = Color(0xFF1F2650),
            panel = Color(0xFF10142E),
            panelHigh = Color(0xFF242C5C),
            line = Color(0xFF323C78),
            focusRow = Color(0xFF2A3368),
            accent = KidsMint,
            onAccent = Color(0xFF0B0D1A),
            movies = KidsLilac,
            onMovies = Color(0xFF0B0D1A),
            shows = KidsSky,
            onShows = Color(0xFF0B0D1A),
            games = KidsMint,
            onGames = Color(0xFF0B0D1A),
        )

    /** The calmer Night Light: the same three colours, softer, on a darker navy */
    val NightLight =
        KidsPalette(
            tray = Color(0xFF0E1230),
            text = Color(0xFFF2F3FF),
            soft = Color(0xFFDCE0F8),
            muted = Color(0xFFA9B1E6),
            raised = Color(0xFF191F46),
            panel = Color(0xFF0B0F28),
            panelHigh = Color(0xFF1E2552),
            line = Color(0xFF2A336A),
            focusRow = Color(0xFF232B5C),
            accent = Color(0xFFB8F2EA),
            onAccent = Color(0xFF0B0D1A),
            movies = Color(0xFFD9BFFF),
            onMovies = Color(0xFF0B0D1A),
            shows = Color(0xFFA9DCF5),
            onShows = Color(0xFF0B0D1A),
            games = Color(0xFFB8F2EA),
            onGames = Color(0xFF0B0D1A),
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
