package com.github.damontecres.wholphin.ui.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.components.BenflixChoice
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixPanelDialog
import com.github.damontecres.wholphin.ui.components.ChoiceRow
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.tryRequestFocus

/** One line in the player's side panel: a small heading, or a row you can press */
sealed interface PanelLine {
    data class Heading(
        val text: String,
    ) : PanelLine

    data class Row(
        val label: String,
        val onClick: () -> Unit,
        /** What it's set to now, in grey on the right ("English · 5.1") */
        val value: String? = null,
        /** A small grey line under the name */
        val note: String? = null,
        val selected: Boolean = false,
        val icon: BenflixIcon? = null,
        /** False for rows that open another panel or change something on the spot and keep this one open */
        val closes: Boolean = true,
    ) : PanelLine
}

/**
 * Redesign 2 players (approved pl-family): every video player (movies, episodes, Live TV, trailers) uses
 * this one side panel, in words. On TV it slides in from the right over the video; on phones it comes up
 * from the bottom. The focus starts on the picked row (the subtitles you have on, say), else the first row.
 * The video behind is only dimmed a little, so you can still see what you're fixing.
 */
@Composable
fun PlayerPanel(
    title: String,
    lines: List<PanelLine>,
    onDismissRequest: () -> Unit,
    subtitle: String? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    BenflixPanelDialog(onDismissRequest = onDismissRequest) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0.25f)
        val rows = remember(lines) { lines.filterIsInstance<PanelLine.Row>() }
        val startAt = remember(rows) { rows.indexOfFirst { it.selected }.takeIf { it >= 0 } ?: 0 }
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { first.tryRequestFocus() }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = title,
                color = BenflixColors.Text,
                fontSize = BenflixType.rowName,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = if (subtitle == null) 8.dp else 2.dp),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = BenflixColors.Muted,
                    fontSize = BenflixType.small,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                )
            }
            var rowIndex = 0
            lines.forEach { line ->
                when (line) {
                    is PanelLine.Heading -> {
                        Text(
                            text = line.text,
                            color = BenflixColors.Muted,
                            fontSize = BenflixType.small,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 2.dp),
                        )
                    }

                    is PanelLine.Row -> {
                        val here = rowIndex++
                        ChoiceRow(
                            choice =
                                BenflixChoice(
                                    label = line.label,
                                    onClick = line.onClick,
                                    icon = line.icon,
                                    note = line.note,
                                    selected = line.selected,
                                    value = line.value,
                                ),
                            onClick = {
                                if (line.closes) onDismissRequest()
                                line.onClick()
                            },
                            modifier = if (here == startAt) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
            }
            footer?.invoke()
        }
    }
}
