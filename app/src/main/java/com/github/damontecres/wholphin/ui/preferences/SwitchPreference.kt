package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.SwitchColors
import androidx.tv.material3.SwitchDefaults
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.ui.theme.LocalTheme

@Composable
fun SwitchPreference(
    title: String,
    value: Boolean,
    onClick: () -> Unit,
    summaryOn: String?,
    summaryOff: String?,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) = SwitchPreference(
    title = title,
    value = value,
    onClick = onClick,
    modifier = modifier,
    summary = if (value) summaryOn else summaryOff,
    onLongClick = onLongClick,
    interactionSource = interactionSource,
)

@Composable
fun SwitchPreference(
    title: String,
    value: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    ListItem(
        selected = false,
        onClick = onClick,
        onLongClick = onLongClick,
        headlineContent = {
            PreferenceTitle(title)
        },
        supportingContent = {
            PreferenceSummary(summary)
        },
        // Benflix redesign: the words On and Off instead of a switch
        trailingContent = {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement =
                    androidx.compose.foundation.layout.Arrangement
                        .spacedBy(8.dp),
            ) {
                // Thanksgiving look: a small acorn next to switches that are on
                if (value && com.github.damontecres.wholphin.ui.theme.TgLook.on) {
                    com.github.damontecres.wholphin.ui.theme
                        .TgAcorn()
                }
                // Valentine's look: a white knob with a red heart in it next to switches that are on
                if (value && com.github.damontecres.wholphin.ui.theme.ValLook.on) {
                    com.github.damontecres.wholphin.ui.theme
                        .ValKnobHeart()
                }
                // Popcorn Day look: a popped piece next to switches that are on, a kernel next to ones that are off
                if (com.github.damontecres.wholphin.ui.theme.PcLook.on) {
                    com.github.damontecres.wholphin.ui.theme
                        .PcSwitchMark(value)
                }
                // New Year's look: a little gold star next to switches that are on
                if (value && com.github.damontecres.wholphin.ui.theme.NyLook.on) {
                    com.github.damontecres.wholphin.ui.theme
                        .NyStar()
                }
                // St. Patrick's Day look: a white knob with a green shamrock next to switches that are on
                if (value && com.github.damontecres.wholphin.ui.theme.SpLook.on) {
                    com.github.damontecres.wholphin.ui.theme
                        .SpKnob()
                }
                androidx.tv.material3.Text(
                    text = if (value) "On" else "Off",
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            }
        },
        interactionSource = interactionSource,
        modifier = modifier,
        scale = ListItemDefaults.scale(focusedScale = 1f),
    )
}

@Composable
fun SwitchColors(): SwitchColors {
    val theme = LocalTheme.current
    // Christmas look: switches that are on are pine green with a white knob
    if (com.github.damontecres.wholphin.ui.theme.XmasLook.on) {
        return SwitchDefaults.colors(
            checkedThumbColor = androidx.compose.ui.graphics.Color.White,
            checkedTrackColor =
                com.github.damontecres.wholphin.ui.theme.XmasLook.Pine,
            uncheckedThumbColor = MaterialTheme.colorScheme.onPrimary,
        )
    }
    return when (theme) {
        AppThemeColors.UNRECOGNIZED,
        AppThemeColors.PURPLE,
        AppThemeColors.BLUE,
        -> {
            SwitchDefaults.colors()
        }

        AppThemeColors.GREEN,
        AppThemeColors.ORANGE,
        AppThemeColors.BOLD_BLUE,
        AppThemeColors.OLED_BLACK,
        AppThemeColors.RED,
        AppThemeColors.BROWN,
        AppThemeColors.MYSTREAM,
        -> {
            SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
