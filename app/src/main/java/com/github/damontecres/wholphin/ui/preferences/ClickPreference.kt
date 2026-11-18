package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Glowfin (S1): a setting you press. A short line under it is its current value (aqua, on the right);
 * a longer one explains it and shows while you're on it. Pass [value] to say which is which.
 */
@Composable
fun ClickPreference(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    value: String? = null,
) {
    val (shortValue, explain) = if (value != null) value to summary else splitSummary(summary)
    GlowfinSettingRow(
        title = title,
        value = shortValue,
        explain = explain,
        onClick = onClick,
        onLongClick = onLongClick,
        interactionSource = interactionSource,
        modifier = modifier,
    )
}
