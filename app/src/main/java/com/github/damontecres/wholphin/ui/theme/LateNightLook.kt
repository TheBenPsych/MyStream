package com.github.damontecres.wholphin.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.BenflixPreferences
import com.github.damontecres.wholphin.ui.nav.Destination
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * Benflix Late-night look: when switched on in Settings (it's off unless you turn it on),
 * the menus get a soft dark layer late at night so bright posters don't blast a dark room.
 * It never covers a movie, show, game or trailer that is playing. Ends at 6 AM.
 */
@Composable
fun LateNightLookLayer(
    prefs: BenflixPreferences,
    currentDestination: Destination?,
) {
    if (!prefs.lateNightLook) return
    val startHour = prefs.lateNightStartHour.takeIf { it > 0 } ?: AppPreference.LATE_NIGHT_DEFAULT_HOUR
    var night by remember(startHour) { mutableStateOf(isLateNight(startHour)) }
    LaunchedEffect(startHour) {
        while (true) {
            night = isLateNight(startHour)
            delay(60_000)
        }
    }
    val playing =
        currentDestination is Destination.Playback ||
            currentDestination is Destination.PlaybackList ||
            currentDestination is Destination.PlayGame ||
            currentDestination == Destination.TrailerShuffle ||
            currentDestination is Destination.Slideshow
    if (night && !playing) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = LATE_NIGHT_DIM)),
        )
    }
}

private const val LATE_NIGHT_DIM = 0.32f
private const val LATE_NIGHT_END_HOUR = 6

/** True between the start hour (24 = midnight) and 6 AM */
fun isLateNight(
    startHour: Int,
    now: LocalTime = LocalTime.now(),
): Boolean {
    val hour = now.hour
    return if (startHour >= 24) {
        hour < LATE_NIGHT_END_HOUR
    } else {
        hour >= startHour || hour < LATE_NIGHT_END_HOUR
    }
}
