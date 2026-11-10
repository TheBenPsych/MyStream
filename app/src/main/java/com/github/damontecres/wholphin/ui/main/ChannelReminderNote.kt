package com.github.damontecres.wholphin.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.github.damontecres.wholphin.services.ChannelPrefs
import com.github.damontecres.wholphin.services.ChannelSession
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.components.BenflixNotices
import com.github.damontecres.wholphin.ui.nav.Destination
import kotlinx.coroutines.delay

/**
 * Benflix (rm-early, notices redesign 2026-11-10): a minute before a show you asked to be reminded about, a
 * 7-second corner card shows anywhere in the app, even during a movie: "Orbit starts in 1 minute", OK to switch.
 * The card never takes the remote's focus. The reminder is used up once its card has shown.
 */
@Composable
fun ChannelReminderNote(
    navigationManager: NavigationManager,
    @Suppress("UNUSED_PARAMETER") modifier: Modifier = Modifier,
) {
    val reminders by ChannelPrefs.reminders.collectAsState()
    // "Remind me every time": keep adding the next showings from today's line-up
    val everyTime by ChannelPrefs.everyTime.collectAsState()
    LaunchedEffect(everyTime.isNotEmpty()) {
        while (everyTime.isNotEmpty()) {
            ChannelPrefs.addEveryTime(ChannelSession.lineup)
            delay(EVERY_CHECK_MS)
        }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(reminders.isNotEmpty()) {
        while (reminders.isNotEmpty()) {
            now = System.currentTimeMillis()
            // Tick every second only near a reminder; otherwise check twice a minute
            val soonest = reminders.filter { now - it.startMs < LATE_MS }.minOfOrNull { it.startMs - NOTICE_MS - now } ?: 30_000L
            delay(if (soonest > 1_000L) soonest.coerceAtMost(30_000L) else 1_000L)
        }
    }
    val due = reminders.firstOrNull { it.startMs - now <= NOTICE_MS && now - it.startMs < LATE_MS }
    LaunchedEffect(due) {
        val reminder = due ?: return@LaunchedEffect
        val channel = ChannelSession.lineup.firstOrNull { it.key == reminder.key }
        val left = ((reminder.startMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
        val usedUp: () -> Unit = { if (ChannelPrefs.reminders.value.contains(reminder)) ChannelPrefs.toggleReminder(reminder) }
        BenflixNotices.card(
            BenflixNotices.Card(
                id = "remind-${reminder.key}-${reminder.startMs}",
                title = if (left > 0) "${reminder.title} starts in 1 minute" else "${reminder.title} is on now",
                line = channel?.let { "${it.number}  ${it.title}" } ?: "Live TV",
                whilePlaying = true,
                onOpen = {
                    usedUp()
                    if (channel != null) {
                        ChannelSession.tune(navigationManager, channel)
                    } else {
                        navigationManager.navigateTo(Destination.Channels)
                    }
                },
                onTimeout = usedUp,
            ),
        )
    }
}

private const val NOTICE_MS = 60_000L
private const val LATE_MS = 2 * 60_000L

/** How often the "every time" shows are looked for in the line-up */
private const val EVERY_CHECK_MS = 5 * 60_000L
