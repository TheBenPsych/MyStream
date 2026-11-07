package com.github.damontecres.wholphin.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.ChannelPrefs
import com.github.damontecres.wholphin.services.ChannelSession
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay

/**
 * Benflix redesign (rm-early): a minute before a show you asked to be reminded about, a small note
 * shows in the corner anywhere in the app, even during a movie: "Orbit starts in 0:45" with
 * Go to channel and Dismiss.
 */
@Composable
fun ChannelReminderNote(
    navigationManager: NavigationManager,
    modifier: Modifier = Modifier,
) {
    val reminders by ChannelPrefs.reminders.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(reminders.isNotEmpty()) {
        while (reminders.isNotEmpty()) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val due = reminders.firstOrNull { it.startMs - now <= NOTICE_MS && now - it.startMs < LATE_MS }
    AnimatedVisibility(visible = due != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val reminder = due ?: return@AnimatedVisibility
        val first = remember(reminder) { FocusRequester() }
        LaunchedEffect(reminder) { first.tryRequestFocus() }
        val shape = RoundedCornerShape(14.dp)
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier =
                Modifier
                    .padding(24.dp)
                    .widthIn(max = 380.dp)
                    .background(BenflixColors.Panel, shape)
                    .border(1.dp, BenflixColors.Line, shape)
                    .padding(16.dp),
        ) {
            val left = ((reminder.startMs - now) / 1000L).coerceAtLeast(0L)
            Text(
                text =
                    if (left >
                        0
                    ) {
                        "${reminder.title} starts in ${left / 60}:${"%02d".format(left % 60)}"
                    } else {
                        "${reminder.title} is on now"
                    },
                color = BenflixColors.Text,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val channel = ChannelSession.lineup.firstOrNull { it.key == reminder.key }
            channel?.let { Text("${it.number}  ${it.title}", color = BenflixColors.Muted, fontSize = 13.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NoteButton("Go to channel", primary = true, modifier = Modifier.focusRequester(first)) {
                    ChannelPrefs.toggleReminder(reminder)
                    if (channel != null) {
                        ChannelSession.tune(navigationManager, channel)
                    } else {
                        navigationManager.navigateTo(Destination.Channels)
                    }
                }
                NoteButton("Dismiss") { ChannelPrefs.toggleReminder(reminder) }
            }
        }
    }
}

@Composable
private fun NoteButton(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (primary) BenflixColors.Text else BenflixColors.Raised,
                contentColor = if (primary) BenflixColors.Tray else BenflixColors.Text,
                focusedContainerColor = BenflixColors.Text,
                focusedContentColor = BenflixColors.Tray,
            ),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

private const val NOTICE_MS = 60_000L
private const val LATE_MS = 2 * 60_000L
