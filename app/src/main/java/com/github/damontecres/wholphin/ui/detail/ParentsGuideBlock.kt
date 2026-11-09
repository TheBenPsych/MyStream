package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.ParentsGuideLine
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType

/**
 * Benflix "Parents' guide" on the movie and show pages: a short list like "Violence: A little",
 * with up to three dots for how much. Nothing shows when there is no guide.
 */
@Composable
fun ParentsGuideBlock(
    lines: List<ParentsGuideLine>,
    modifier: Modifier = Modifier,
) {
    if (lines.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Parents' guide",
            color = BenflixColors.Text,
            fontSize = BenflixType.lead,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "From IMDb's parents guide",
            color = BenflixColors.Muted,
            fontSize = BenflixType.tag,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        lines.forEach { line ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 3.dp),
            ) {
                Text(
                    text = line.label,
                    color = BenflixColors.Muted,
                    fontSize = BenflixType.small,
                    modifier = Modifier.width(170.dp),
                )
                repeat(3) { i ->
                    Box(
                        Modifier
                            .padding(end = 4.dp)
                            .size(8.dp)
                            .background(if (i < line.rank) levelColour(line.rank) else BenflixColors.Line, CircleShape),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(text = line.level, color = BenflixColors.Soft, fontSize = BenflixType.small)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun levelColour(rank: Int): Color =
    when (rank) {
        1 -> BenflixColors.Ok
        2 -> BenflixColors.Check
        else -> BenflixColors.Problem
    }
