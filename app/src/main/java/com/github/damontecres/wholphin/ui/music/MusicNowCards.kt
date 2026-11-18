package com.github.damontecres.wholphin.ui.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixNotices
import com.github.damontecres.wholphin.ui.components.PlayOnTvViewModel
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.launch

/*
 * Music redesign 3 (np cards): behind About on Now playing, four cards about the playing song: About the
 * artist, Credits, Next in queue, and Songs like this to ask for. TV, phone and browser.
 */

/**
 * Music redesign 3: Play on. Pick another Benflix (a TV or phone that's open on Benflix) and the music moves
 * there straight away, from the same spot, with the rest of the queue. It stops here.
 */
@Composable
fun MusicPlayOnDialog(
    viewModel: MusicNowPlayingViewModel,
    onDismiss: () -> Unit,
    screensModel: PlayOnTvViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { screensModel.look() }
    val screens by screensModel.screens.collectAsState()
    val first = remember { FocusRequester() }
    BasicDialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.widthIn(min = 280.dp, max = 460.dp).fillMaxWidth(),
        ) {
            Text("Play on", fontSize = BenflixType.lead, fontWeight = FontWeight.Bold, color = BenflixColors.Text)
            when (val list = screens) {
                null -> {
                    Text("Looking for screens…", fontSize = BenflixType.body, color = BenflixColors.Soft)
                }

                else -> {
                    if (list.isEmpty()) {
                        Text(
                            "No other screen found. Open Glowfin on the TV (any page), then try again.",
                            fontSize = BenflixType.body,
                            color = BenflixColors.Soft,
                        )
                    }
                    list.forEachIndexed { i, screen ->
                        MusicButton(
                            screen.name,
                            BenflixIcon.TV,
                            onClick = {
                                val ids = viewModel.queueFromHere()
                                val at = viewModel.player.currentPosition
                                screensModel.viewModelScope.launch {
                                    val sent = ids.isNotEmpty() && screensModel.playSongs(screen, ids, at)
                                    if (sent) viewModel.player.pause()
                                    BenflixNotices.pill(if (sent) "Playing on ${screen.name}" else "Couldn't reach ${screen.name}")
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                        )
                    }
                }
            }
            MusicButton(
                "Close",
                BenflixIcon.CLOSE,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().then(if (screens.isNullOrEmpty()) Modifier.focusRequester(first) else Modifier),
            )
        }
    }
    LaunchedEffect(screens) { first.tryRequestFocus() }
}
