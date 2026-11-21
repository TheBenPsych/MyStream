package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.SwitchColors
import androidx.tv.material3.SwitchDefaults

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
    // Glowfin (S1): the words On and Off in aqua on the right; the explaining line shows while you're on it
    GlowfinSettingRow(
        title = title,
        value = if (value) "On" else "Off",
        explain = summary?.takeIf { it.isNotBlank() },
        onClick = onClick,
        onLongClick = onLongClick,
        interactionSource = interactionSource,
        modifier = modifier,
    )
}

@Composable
fun SwitchColors(): SwitchColors =
    SwitchDefaults.colors(
        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
        uncheckedThumbColor = MaterialTheme.colorScheme.onPrimary,
    )
