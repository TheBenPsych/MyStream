package com.github.damontecres.wholphin.ui.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.AudioItem
import com.github.damontecres.wholphin.services.NowPlayingStatus
import com.github.damontecres.wholphin.services.rememberQueue
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay

/**
 * Benflix music: Up next, its own page (approved q-full): what's playing now, the songs you added yourself,
 * then "Then plays" for the rest of the album, playlist or mix. Each waiting song can be played now, moved
 * up or down, or taken out.
 */
@Composable
fun MusicUpNextPage(
    modifier: Modifier = Modifier,
    viewModel: MusicNowPlayingViewModel = hiltViewModel(),
) {
    val service = viewModel.musicService
    val music by service.state.collectAsState()
    val queue = rememberQueue(viewModel.player, music.queueVersion, music.queueSize)
    val now = music.currentIndex
    val isTv = rememberIsTv()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        if (isTv) first.tryRequestFocus()
    }
    val upcoming = remember(music.queueVersion, music.queueSize, now) { (now + 1 until queue.size).map { it to queue[it] } }
    val added = upcoming.filter { service.isAdded(it.second) }
    val then = upcoming.filterNot { service.isAdded(it.second) }
    val current = queue.getOrNull(now)
    var focusGiven = false
    LazyColumn(
        contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxSize().widthIn(max = 1000.dp),
    ) {
        item(key = "title") {
            Text(
                "Up next",
                color = BenflixColors.Text,
                fontSize = BenflixType.pageTitle,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (current != null) {
            item(key = "now-name") { MusicKicker("Playing now", Modifier.padding(top = 8.dp, bottom = 4.dp)) }
            item(key = "now") {
                QueueRow(current, isCurrent = true, playing = music.status == NowPlayingStatus.PLAYING, onPlay = {
                }, onUp = null, onDown = null, onRemove = null)
            }
        }
        if (added.isNotEmpty()) {
            item(key = "added-name") {
                MusicKicker("Up next · songs you added", Modifier.padding(top = 18.dp, bottom = 4.dp), color = BenflixColors.Soft)
            }
            added.forEach { (index, audio) ->
                val focus = !focusGiven
                focusGiven = true
                item(key = "q-${audio.key}") {
                    QueueRow(
                        audio,
                        isCurrent = false,
                        playing = false,
                        onPlay = { viewModel.viewModelScope.launchIO { service.playIndex(index) } },
                        onUp = { viewModel.viewModelScope.launchIO { service.moveQueue(index, index - 1) } }.takeIf { index > now + 1 },
                        onDown =
                            { viewModel.viewModelScope.launchIO { service.moveQueue(index, index + 1) } }.takeIf {
                                index <
                                    queue.size - 1
                            },
                        onRemove = { viewModel.viewModelScope.launchIO { service.removeFromQueue(index) } },
                        modifier = if (focus) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }
        if (then.isNotEmpty()) {
            item(key = "then-name") {
                MusicKicker(
                    "Then plays" + (music.thenTitle?.let { ": $it" } ?: ""),
                    Modifier.padding(top = 18.dp, bottom = 4.dp),
                    color = BenflixColors.Soft,
                )
            }
            then.take(MAX_SHOWN).forEach { (index, audio) ->
                val focus = !focusGiven
                focusGiven = true
                item(key = "q-${audio.key}") {
                    QueueRow(
                        audio,
                        isCurrent = false,
                        playing = false,
                        onPlay = { viewModel.viewModelScope.launchIO { service.playIndex(index) } },
                        onUp = null,
                        onDown = null,
                        onRemove = { viewModel.viewModelScope.launchIO { service.removeFromQueue(index) } },
                        modifier = if (focus) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }
        if (upcoming.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Nothing waiting. When this song ends, a mix like it starts by itself.",
                    color = BenflixColors.Soft,
                    fontSize = BenflixType.body,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun QueueRow(
    audio: AudioItem,
    isCurrent: Boolean,
    playing: Boolean,
    onPlay: () -> Unit,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.tv.material3.Surface(
            onClick = onPlay,
            shape =
                androidx.tv.material3.ClickableSurfaceDefaults
                    .shape(RoundedCornerShape(8.dp)),
            colors =
                androidx.tv.material3.ClickableSurfaceDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = BenflixColors.FocusRow,
                ),
            border =
                androidx.tv.material3.ClickableSurfaceDefaults.border(
                    focusedBorder =
                        androidx.tv.material3.Border(
                            androidx.compose.foundation.BorderStroke(2.dp, BenflixColors.Text),
                            shape = RoundedCornerShape(8.dp),
                        ),
                ),
            scale =
                androidx.tv.material3.ClickableSurfaceDefaults
                    .scale(focusedScale = 1f),
            modifier = modifier.weight(1f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                    CoverImage(audio.imageUrl, Modifier.size(40.dp), shape = RoundedCornerShape(6.dp))
                    if (isCurrent) PlayingBars(playing)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        audio.title.orEmpty(),
                        color = if (isCurrent) BenflixColors.Music else BenflixColors.Text,
                        fontSize = BenflixType.body,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        audio.artistNames.orEmpty(),
                        color = BenflixColors.Muted,
                        fontSize = BenflixType.small,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(songLength(audio.runtime), color = BenflixColors.Muted, fontSize = BenflixType.small)
            }
        }
        if (onUp != null) MusicButton(null, BenflixIcon.DOWN, onClick = onUp, modifier = Modifier.rotate(180f))
        if (onDown != null) MusicButton(null, BenflixIcon.DOWN, onClick = onDown)
        if (onRemove != null) MusicButton(null, BenflixIcon.CLOSE, onClick = onRemove)
    }
}

private const val MAX_SHOWN = 100
