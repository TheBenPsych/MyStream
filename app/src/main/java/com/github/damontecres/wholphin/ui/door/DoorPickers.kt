package com.github.damontecres.wholphin.ui.door

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixType

/** The doors that get the "Can't decide?" games at the bottom (Ben: Movies picks movies, Shows picks shows) */
fun BenflixDoor.hasPickers(): Boolean = this == BenflixDoor.MOVIES || this == BenflixDoor.SHOWS

/**
 * The very bottom of the Movies and Shows doors: "Can't decide?" with This or that and the Mood quiz.
 * Both games only pick from this door. Phones show the two side by side as smaller cards.
 */
@Composable
fun PickerRow(
    door: BenflixDoor,
    easy: Boolean,
    compact: Boolean,
    onOpen: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kind = if (door == BenflixDoor.SHOWS) "shows" else "movies"
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Can't decide?",
            color = BenflixColors.Text,
            fontSize = BenflixType.lead,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            PickerTile(
                door = door,
                mark = "VS",
                title = "This or that",
                line = if (compact) "Pick one of two, 8 rounds" else "Two $kind, pick one. 8 rounds and the winner is tonight's pick.",
                easy = easy,
                compact = compact,
                onClick = { onOpen(Destination.ThisOrThat(door.name)) },
                modifier = Modifier.weight(1f),
            )
            PickerTile(
                door = door,
                mark = ":)",
                title = "Mood quiz",
                line = if (compact) "Moods and time" else "Everyone picks a mood and how long, then see what fits.",
                easy = easy,
                compact = compact,
                onClick = { onOpen(Destination.MoodQuiz(door.name)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PickerTile(
    door: BenflixDoor,
    mark: String,
    title: String,
    line: String,
    easy: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.Raised,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier.height(
                if (compact) {
                    112.dp
                } else if (easy) {
                    104.dp
                } else {
                    88.dp
                },
            ),
    ) {
        val badge: @Composable () -> Unit = {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(if (compact) 30.dp else 40.dp)
                        .background(door.color, RoundedCornerShape(8.dp)),
            ) {
                Text(text = mark, color = door.onColor, fontSize = BenflixType.small, fontWeight = FontWeight.ExtraBold)
            }
        }
        if (compact) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize().padding(12.dp),
            ) {
                badge()
                Text(
                    text = title,
                    fontSize = BenflixType.body,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = line, fontSize = BenflixType.small, color = BenflixColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            ) {
                badge()
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = if (easy) BenflixType.rowName else BenflixType.lead,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!easy) {
                        Text(
                            text = line,
                            fontSize = BenflixType.small,
                            color = BenflixColors.Muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
