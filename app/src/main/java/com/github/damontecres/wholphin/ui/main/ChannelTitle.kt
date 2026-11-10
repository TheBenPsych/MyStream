package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.ChannelPrefs
import com.github.damontecres.wholphin.services.ChannelProgram
import com.github.damontecres.wholphin.services.ChannelReminder
import com.github.damontecres.wholphin.services.ChannelSchedule
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.group
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.detail.title.TitleButton
import com.github.damontecres.wholphin.ui.detail.title.TitleEmpty
import com.github.damontecres.wholphin.ui.detail.title.TitlePage
import com.github.damontecres.wholphin.ui.detail.title.TitlePicture
import com.github.damontecres.wholphin.ui.detail.title.TitleTab
import com.github.damontecres.wholphin.ui.detail.title.TitleTabScope
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.Dialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Benflix Redesign 2: a live channel's own page, on the shared title page frame. It opens from the
 * channel's name in the guide. Buttons: Watch, Favourite, Remind me (for the next show), More is left
 * out because everything fits. Tabs: On now, Up next (press a show to be reminded), About.
 */
@Composable
fun ChannelTitleSheet(
    schedule: ChannelSchedule,
    now: Long,
    onWatch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val favourites by ChannelPrefs.favourites.collectAsState()
    val reminders by ChannelPrefs.reminders.collectAsState()
    val kids = KidsMode.isActive
    val onNow = remember(schedule, now) { schedule.programAt(now) }
    val upNext =
        remember(schedule, now) {
            schedule.programs
                .filter { it.startMs >= now }
                .sortedBy { it.startMs }
                .take(12)
        }
    val next = upNext.firstOrNull { !it.noGuide }
    val starred = schedule.key in favourites
    val reminded = next != null && reminders.any { it.key == schedule.key && it.startMs == next.startMs }
    val buttons =
        listOf(
            TitleButton("Watch", BenflixIcon.PLAY, onWatch, note = onNow?.let { "${it.name()} · until ${timeOf(it.endMs)}" }),
            TitleButton(
                if (starred) "Favourite" else "Add to favourites",
                BenflixIcon.STAR,
                { ChannelPrefs.toggleFavourite(schedule.key) },
            ),
            TitleButton(
                if (reminded) "Reminder set" else "Remind me",
                BenflixIcon.BELL,
                { next?.let { ChannelPrefs.toggleReminder(ChannelReminder(schedule.key, it.startMs, it.name())) } },
                off =
                    when {
                        kids -> "Ask a grown-up to set reminders"
                        next == null -> "Nothing in the guide after this yet"
                        else -> null
                    },
                note = next?.let { "${it.name()} at ${timeOf(it.startMs)}" },
            ),
        )
    val tabs =
        listOf(
            TitleTab("On now") { scope ->
                if (onNow == null) {
                    TitleEmpty("Nothing in the guide right now.", scope)
                } else {
                    ProgramInfo(scope, onNow, showWhen = true)
                }
            },
            TitleTab("Up next") { scope ->
                if (upNext.isEmpty()) {
                    TitleEmpty("Nothing in the guide after this yet.", scope)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = scope.side)) {
                        upNext.forEach { p ->
                            val bell = reminders.any { it.key == schedule.key && it.startMs == p.startMs }
                            ProgramLine(p, bell) {
                                if (!kids && !p.noGuide) ChannelPrefs.toggleReminder(ChannelReminder(schedule.key, p.startMs, p.name()))
                            }
                        }
                        if (!kids) {
                            Text(
                                "Press a show to be reminded a minute before it starts.",
                                color = BenflixColors.Muted,
                                fontSize = BenflixType.tag,
                            )
                        }
                    }
                }
            },
            TitleTab("About") { scope ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = scope.side)) {
                    Text(
                        "Channel ${schedule.number}",
                        color = BenflixColors.Text,
                        fontSize = BenflixType.body,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when {
                            schedule.custom != null -> {
                                "A channel you made from Benflix movies and shows. No ads."
                            }

                            schedule.live == null -> {
                                "A Benflix channel made from the movies and shows on Tower. " +
                                    "No ads, always something on."
                            }

                            else -> {
                                "A free live channel (${schedule.group()}). It may have ads."
                            }
                        },
                        color = BenflixColors.Soft,
                        fontSize = BenflixType.body,
                    )
                }
            },
        )
    val picture = onNow?.item ?: schedule.live?.item
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(BenflixColors.Tray)) {
            TitlePage(
                title = schedule.title,
                accent = BenflixColors.LiveTv,
                picture = if (picture != null) TitlePicture.Backdrop(picture) else TitlePicture.Wide(null),
                info = listOfNotNull("Channel ${schedule.number}", schedule.group().takeIf { it.isNotBlank() }).joinToString("  ·  "),
                buttons = buttons,
                tabs = tabs,
            )
        }
    }
}

@Composable
private fun ProgramInfo(
    scope: TitleTabScope,
    p: ChannelProgram,
    showWhen: Boolean,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .padding(
                    horizontal = scope.side,
                ).then(if (scope.wide) Modifier.widthIn(max = 760.dp) else Modifier.fillMaxWidth()),
    ) {
        Text(p.name(), color = BenflixColors.Text, fontSize = BenflixType.lead, fontWeight = FontWeight.Bold)
        val episode = p.item.title?.takeIf { it != p.name() }
        if (showWhen && !p.noGuide) {
            Text(
                listOfNotNull(episode, "${timeOf(p.startMs)} – ${timeOf(p.endMs)}").joinToString("  ·  "),
                color = BenflixColors.Soft,
                fontSize = BenflixType.small,
            )
        }
        p.item.data.overview?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = BenflixColors.Soft, fontSize = BenflixType.body, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProgramLine(
    p: ChannelProgram,
    bell: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.Raised,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.Text,
                focusedContentColor = BenflixColors.Tray,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(timeOf(p.startMs), fontSize = BenflixType.small, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 70.dp))
            Text(p.name(), fontSize = BenflixType.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (bell) BenflixIconImage(BenflixIcon.BELL, color = BenflixColors.LiveTv, size = 16.dp)
        }
    }
}

private val channelTime = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private fun timeOf(ms: Long): String = channelTime.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
