package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text

/**
 * Glowfin (S1): a setting with a few choices. The current one shows in aqua on the right; pressing it opens
 * the small list on the right, and picking one saves it straight away.
 */
@Composable
fun <T> ChoicePreference(
    title: String,
    summary: String?,
    possibleValues: List<T>,
    selectedIndex: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    @Suppress("UNUSED_PARAMETER")
    valueDisplay: @Composable (index: Int, item: T) -> Unit = { _, item -> Text(item.toString()) },
    subtitleDisplay: (index: Int, item: T) -> @Composable (() -> Unit)? = { _, _ -> null },
    /** The words for each choice (the list and the aqua value); the items' own text when left out */
    labels: List<String>? = null,
    subtitles: List<String?>? = null,
) {
    var open by remember { mutableStateOf(false) }
    val words = labels ?: possibleValues.map { it.toString() }
    val current = words.getOrNull(selectedIndex)
    GlowfinSettingRow(
        title = title,
        value = current ?: summary,
        // The summary explains the setting when it isn't just the current choice again
        explain = summary?.takeIf { it.isNotBlank() && it != current && current != null },
        onClick = { open = true },
        interactionSource = interactionSource,
        modifier = modifier,
    )
    if (open) {
        GlowfinChoiceSheet(
            title = title,
            options = words,
            subtitles = subtitles,
            selectedIndex = selectedIndex,
            onPick = { index ->
                open = false
                if (index != selectedIndex) onValueChange(index)
            },
            onDismiss = { open = false },
        )
    }
}
