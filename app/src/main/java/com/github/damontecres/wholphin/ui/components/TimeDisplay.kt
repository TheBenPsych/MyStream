package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Glowfin
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
        style = MaterialTheme.typography.bodyLarge,
        modifier =
            modifier
                .align(Alignment.TopEnd)
                .padding(vertical = 16.dp, horizontal = 24.dp)
                // New look: the clock's faint see-through pill, so it reads on bright pictures
                .background(Glowfin.Ink.copy(alpha = .35f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 2.dp),
    )
}
