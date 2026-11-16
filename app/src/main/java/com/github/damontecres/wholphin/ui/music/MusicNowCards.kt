package com.github.damontecres.wholphin.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.AudioItem
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

@Composable
fun AboutPanel(
    about: AboutSong,
    viewModel: MusicNowPlayingViewModel,
    next: List<AudioItem>,
) {
    val first = remember { FocusRequester() }
    BasicDialog(onDismissRequest = viewModel::closeAbout) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
        ) {
            Text("About this song", fontSize = BenflixType.lead, fontWeight = FontWeight.Bold, color = BenflixColors.Text)
            if (about.loading) {
                Text("Looking it up…", fontSize = BenflixType.body, color = BenflixColors.Soft)
            }
            // About the artist
            AboutCard("About the artist") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverImage(about.artistImage, Modifier.size(64.dp), shape = CircleShape)
                    Text(
                        about.artistName.orEmpty(),
                        fontSize = BenflixType.rowName,
                        fontWeight = FontWeight.Bold,
                        color = BenflixColors.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                about.artistBio?.let {
                    Text(
                        it,
                        fontSize = BenflixType.small,
                        color = BenflixColors.Soft,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                about.artistId?.let { id ->
                    MusicButton(
                        "Go to artist",
                        BenflixIcon.PERSON,
                        onClick = { viewModel.goToArtist(id) },
                        modifier = Modifier.focusRequester(first),
                    )
                }
            }
            // Credits
            if (about.credits.isNotEmpty()) {
                AboutCard("Credits") {
                    about.credits.forEach { (role, names) ->
                        Column {
                            Text(role, fontSize = BenflixType.small, color = BenflixColors.Muted)
                            Text(names, fontSize = BenflixType.body, color = BenflixColors.Text, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            // Next in queue
            AboutCard("Next in queue") {
                if (next.isEmpty()) {
                    Text("Nothing waiting. A mix like this one starts by itself.", fontSize = BenflixType.small, color = BenflixColors.Soft)
                }
                next.forEach { song ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CoverImage(song.imageUrl, Modifier.size(40.dp), shape = RoundedCornerShape(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title.orEmpty(),
                                fontSize = BenflixType.body,
                                color = BenflixColors.Text,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                song.artistNames.orEmpty(),
                                fontSize = BenflixType.small,
                                color = BenflixColors.Soft,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                MusicButton("Open the queue", BenflixIcon.LIST, onClick = {
                    viewModel.closeAbout()
                    viewModel.openUpNext()
                }, modifier = if (about.artistId == null) Modifier.focusRequester(first) else Modifier)
            }
            // Songs like this to ask for (everyone but Kids and Guest)
            if (viewModel.canAsk) {
                AboutCard("Songs like this to ask for") {
                    Text(
                        "Songs like this one that aren't on Benflix yet. Play 30 seconds of each, then ask for the ones you like.",
                        fontSize = BenflixType.small,
                        color = BenflixColors.Soft,
                    )
                    MusicButton("Show songs like this", BenflixIcon.REQUESTS, onClick = {
                        viewModel.closeAbout()
                        viewModel.openSimilar()
                    })
                }
            }
            MusicButton("Close", BenflixIcon.CLOSE, onClick = viewModel::closeAbout, modifier = Modifier.fillMaxWidth())
        }
    }
    LaunchedEffect(about.loading) { first.tryRequestFocus() }
}

@Composable
private fun AboutCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .background(BenflixColors.Panel, RoundedCornerShape(12.dp))
                .padding(14.dp),
    ) {
        MusicKicker(title)
        content()
    }
}

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
                            "No other screen found. Open Benflix on the TV (any page), then try again.",
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
