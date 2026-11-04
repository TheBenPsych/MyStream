package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The card's title under the tile.
 *
 * Benflix (Ben's animation picks, 2026-11-04): the title used to slide down as the card grew on focus,
 * which nudged the row's layout on every frame. It now always sits where the slide used to end,
 * just clear of the grown tile, so nothing moves. [focused] is kept so callers don't change.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SlidingCardText(
    focused: Boolean,
    modifier: Modifier = Modifier,
    spaceBetweenFocused: Dp = 4.dp,
    spaceBetweenUnfocused: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier =
            modifier
                .padding(top = spaceBetweenUnfocused - spaceBetweenFocused, bottom = spaceBetweenFocused)
                .fillMaxWidth(),
        content = content,
    )
}
