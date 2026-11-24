package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.util.LocalClock

/**
 * Displays the [LocalClock] in the upper right corner of the parent [androidx.compose.foundation.layout.Box]
 */
@Composable
fun BoxScope.TimeDisplay(modifier: Modifier = Modifier) {
    val timeString by LocalClock.current.timeString
    Text(
        text = timeString,
        fontSize = BenflixType.lead,
        color = MaterialTheme.colorScheme.onSurface,
        // Approved design: no pill behind the clock, a soft shadow keeps it readable on bright pictures
        style =
            MaterialTheme.typography.bodyLarge.copy(
                shadow = Shadow(Color.Black.copy(alpha = .6f), Offset(0f, 2f), 10f),
            ),
        modifier =
            modifier
                .align(Alignment.TopEnd)
                .padding(vertical = 18.dp, horizontal = 36.dp),
    )
}
