package com.github.damontecres.wholphin.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.games.GameRef
import com.github.damontecres.wholphin.services.ServerGame
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.Dialog
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay

/**
 * Benflix "Game roulette": can't decide? A slot-machine roll through your games that slows down
 * and lands on one. Play it, look at it, or spin again. Kids only land on their games.
 */
@Composable
fun GameRouletteDialog(
    games: List<GameRef>,
    token: String,
    onPlay: (GameRef) -> Unit,
    onOpen: (GameRef) -> Unit,
    onDismiss: () -> Unit,
) {
    if (games.isEmpty()) return
    var spin by remember { mutableIntStateOf(0) }
    var showing by remember { mutableStateOf(games.random()) }
    var landed by remember { mutableStateOf(false) }
    val playButton = remember { FocusRequester() }
    val spinButton = remember { FocusRequester() }
    LaunchedEffect(spin) {
        landed = false
        spinButton.tryRequestFocus()
        // Fast at first, then slower and slower, like a slot machine
        var wait = 50L
        repeat(ROLLS) { i ->
            showing = if (games.size > 1) games.filter { it != showing }.random() else games.first()
            delay(wait)
            if (i > ROLLS / 2) wait = (wait * 1.18).toLong()
        }
        landed = true
        delay(50)
        playButton.tryRequestFocus()
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .background(BenflixColors.Panel, RoundedCornerShape(16.dp))
                    .padding(24.dp),
        ) {
            Text(
                text = if (landed) "Tonight you're playing..." else "Spinning...",
                color = BenflixColors.Muted,
                fontSize = BenflixType.small,
                fontWeight = FontWeight.Bold,
            )
            Box(Modifier.size(width = 200.dp, height = 200.dp), contentAlignment = Alignment.Center) {
                ServerGameCard(
                    game =
                        ServerGame(
                            id = showing.id,
                            name = showing.name,
                            coverUrl = showing.coverUrl,
                            playable = true,
                            size = showing.size,
                        ),
                    token = token,
                    status = null,
                    onClick = { if (landed) onOpen(showing) },
                    modifier = Modifier.size(width = 160.dp, height = 200.dp),
                )
            }
            Text(
                text = showing.name,
                color = BenflixColors.Text,
                fontSize = BenflixType.lead,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = showing.consoleName, color = BenflixColors.Soft, fontSize = BenflixType.small)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onPlay(showing) },
                    enabled = landed,
                    modifier = Modifier.focusRequester(playButton),
                ) { Text("Play") }
                Button(onClick = { onOpen(showing) }, enabled = landed) { Text("Look") }
                Button(onClick = { spin++ }, modifier = Modifier.focusRequester(spinButton)) { Text("Spin again") }
            }
        }
    }
}

/** How many games roll past before it lands */
private const val ROLLS = 28
