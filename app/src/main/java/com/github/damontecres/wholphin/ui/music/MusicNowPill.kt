package com.github.damontecres.wholphin.ui.music

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.NowPlayingStatus
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.focusRing
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicPillViewModel
    @Inject
    constructor(
        val musicService: MusicService,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        fun open() = navigationManager.navigateTo(Destination.NowPlaying)

        fun playPause() {
            musicService.playPause()
        }
    }

/**
 * While music plays and you browse (approved m2-elsewhere): on TV a pill left of the clock with small
 * violet bars, the song and the artist. OK opens Now playing. Nothing shows when no music is on.
 */
@Composable
fun MusicNowPill(
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    viewModel: MusicPillViewModel = hiltViewModel(),
) {
    val music by viewModel.musicService.state.collectAsState()
    val audio = music.currentAudio
    if (music.queueSize == 0 || audio == null) return
    val shape = BenflixShapes.Pill
    Surface(
        onClick = viewModel::open,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.Raised,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border = focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.widthIn(max = 320.dp * scale),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 8.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
        ) {
            CoverImage(audio.imageUrl, Modifier.size(26.dp * scale), shape = RoundedCornerShape(5.dp))
            SmallTowerLine(
                1f,
                music.status == NowPlayingStatus.PLAYING,
                Modifier.size(width = 30.dp * scale, height = 14.dp * scale),
                towers = 6,
            )
            Text(
                text = listOfNotNull(audio.title, audio.artistNames).joinToString(" · "),
                color = BenflixColors.Text,
                fontSize = BenflixType.small,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Phones: a slim bar along the bottom while music plays (approved m2-elsewhere): the cover, the song and
 * artist, and play/pause. Tap it for Now playing. A thin violet line shows how far through the song is.
 */
@Composable
fun MusicMiniBar(
    modifier: Modifier = Modifier,
    viewModel: MusicPillViewModel = hiltViewModel(),
) {
    val music by viewModel.musicService.state.collectAsState()
    val audio = music.currentAudio
    if (music.queueSize == 0 || audio == null) return
    val playing = music.status == NowPlayingStatus.PLAYING
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(audio.key) {
        while (true) {
            val p = viewModel.musicService.player
            val d = p.duration.takeIf { it > 0 } ?: audio.runtime?.inWholeMilliseconds ?: 0L
            progress = if (d > 0) (p.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }
    val shape = RoundedCornerShape(12.dp)
    // Music redesign 3: swipe the mini player left for the next song, right for the one before
    val slide =
        remember(audio.key) {
            androidx.compose.animation.core
                .Animatable(0f)
        }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val skipAt = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
    Surface(
        border =
            com.github.damontecres.wholphin.ui.theme
                .benflixFocusBorder(shape),
        onClick = viewModel::open,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                // Glowfin (M1): the raised night-blue mini player above the phone's bar
                containerColor = com.github.damontecres.wholphin.ui.theme.Glowfin.Raise,
                contentColor = BenflixColors.Text,
                focusedContainerColor = com.github.damontecres.wholphin.ui.theme.Glowfin.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .pointerInput(audio.key) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val at = slide.value
                            when {
                                at <= -skipAt -> viewModel.musicService.skipToNext()
                                at >= skipAt -> viewModel.musicService.player.seekToPrevious()
                            }
                            scope.launch { slide.animateTo(0f) }
                        },
                        onDragCancel = { scope.launch { slide.animateTo(0f) } },
                    ) { change, dx ->
                        change.consume()
                        scope.launch { slide.snapTo((slide.value + dx).coerceIn(-skipAt * 1.4f, skipAt * 1.4f)) }
                    }
                }.graphicsLayer {
                    translationX = slide.value
                    alpha = 1f - (kotlin.math.abs(slide.value) / (skipAt * 3f))
                },
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            ) {
                CoverImage(audio.imageUrl, Modifier.size(40.dp), shape = RoundedCornerShape(7.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        audio.title.orEmpty(),
                        color = BenflixColors.Text,
                        fontSize = BenflixType.small,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        audio.artistNames.orEmpty(),
                        color = BenflixColors.Muted,
                        fontSize = BenflixType.tag,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MusicButton(null, if (playing) BenflixIcon.PAUSE else null, filledPlay = !playing, onClick = viewModel::playPause)
            }
            // Music player 2026-11-17: the same tower progress line as Now playing
            SmallTowerLine(progress, playing, Modifier.fillMaxWidth().height(10.dp).padding(horizontal = 10.dp), towers = 36)
            Spacer(Modifier.height(4.dp))
        }
    }
}
