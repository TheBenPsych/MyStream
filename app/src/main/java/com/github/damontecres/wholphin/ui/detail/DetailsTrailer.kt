package com.github.damontecres.wholphin.ui.detail

import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.LocalTrailer
import com.github.damontecres.wholphin.services.TrailerService
import com.github.damontecres.wholphin.ui.main.HeroTrailer
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.videosApi
import javax.inject.Inject

/**
 * Lets the trailer take over, Netflix-style: a few seconds after the trailer starts (and nothing
 * was pressed), the page text fades away and the trailer's sound gently fades in.
 * Any remote button brings everything back.
 */
@Stable
class TrailerCinemaState {
    var playing by mutableStateOf(false)
    var cinema by mutableStateOf(false)
    var lastInput by mutableLongStateOf(0L)
}

@Composable
fun rememberTrailerCinemaState(): TrailerCinemaState {
    val state = remember { TrailerCinemaState() }
    LaunchedEffect(state.playing, state.lastInput) {
        state.cinema = false
        if (state.playing) {
            delay(CINEMA_DELAY_MS)
            state.cinema = true
        }
    }
    return state
}

/** Put on the page's outer Box so any button press (or, on a phone, any touch) wakes the page back up */
fun Modifier.wakeOnInput(state: TrailerCinemaState): Modifier =
    onPreviewKeyEvent {
        state.lastInput = SystemClock.uptimeMillis()
        false
    }.pointerInput(state) {
        // Phones: a tap or scroll anywhere brings the page text back. Only watches, never takes the touch
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                state.lastInput = SystemClock.uptimeMillis()
            }
        }
    }

/** How visible the page text should be (fades out while the trailer is full screen) */
@Composable
fun TrailerCinemaState.contentAlpha(): Float {
    val alpha by animateFloatAsState(if (cinema) 0f else 1f, tween(CINEMA_FADE_MS), label = "cinemaContent")
    return alpha
}

/**
 * Netflix-style: after a few seconds on a movie or show page, its local trailer plays muted
 * behind the title. Stops when [active] turns false (scrolled down or left the page).
 */
@Composable
fun BoxScope.DetailsTrailer(
    item: BaseItem?,
    active: Boolean,
    cinemaState: TrailerCinemaState? = null,
    viewModel: DetailsTrailerViewModel = hiltViewModel(),
) {
    val playing by viewModel.playing.collectAsState()
    // Also stops while the app is in the background or another screen is on top
    LifecycleResumeEffect(item?.id, active) {
        viewModel.preview(if (active) item else null)
        onPauseOrDispose { viewModel.preview(null) }
    }
    val showing = active && playing
    LaunchedEffect(showing) { cinemaState?.playing = showing }
    // Sound: once the page text has faded away, the trailer's sound slowly rises to a gentle
    // level (no jumpscare). Any button press brings the page back and quickly fades it out again.
    val cinema = cinemaState?.cinema == true
    LaunchedEffect(cinema, showing) {
        val p = viewModel.player ?: return@LaunchedEffect
        if (!showing) {
            p.volume = 0f
            return@LaunchedEffect
        }
        animate(
            initialValue = p.volume,
            targetValue = if (cinema) TRAILER_VOLUME else 0f,
            animationSpec = tween(if (cinema) VOLUME_FADE_IN_MS else VOLUME_FADE_OUT_MS),
        ) { value, _ -> p.volume = value }
    }
    // The dark shading behind the title & description fades away together with the text
    val clear by animateFloatAsState(if (cinema) 1f else 0f, tween(CINEMA_FADE_MS), label = "cinemaShade")
    if (showing) {
        HeroTrailer(
            player = viewModel.player ?: return,
            clear = clear,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@HiltViewModel
class DetailsTrailerViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val trailerService: TrailerService,
    ) : ViewModel() {
        private val _playing = MutableStateFlow(false)
        val playing: StateFlow<Boolean> = _playing
        var player: ExoPlayer? = null
            private set
        private var job: Job? = null

        fun preview(item: BaseItem?) {
            job?.cancel()
            _playing.value = false
            player?.stop()
            if (item == null) return
            job =
                viewModelScope.launch(ExceptionHandler()) {
                    delay(DELAY_MS)
                    val trailer =
                        withContext(WholphinDispatchers.IO) {
                            trailerService
                                .getLocalTrailers(item)
                                .filterIsInstance<LocalTrailer>()
                                .firstOrNull()
                        } ?: return@launch
                    val url = api.videosApi.getVideoStreamUrl(itemId = trailer.baseItem.id, static = true)
                    val p =
                        player ?: ExoPlayer.Builder(context).build().also { newPlayer ->
                            newPlayer.volume = 0f
                            newPlayer.addListener(
                                object : Player.Listener {
                                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                                        if (isPlaying) _playing.value = true
                                    }

                                    override fun onPlaybackStateChanged(playbackState: Int) {
                                        if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                                            _playing.value = false
                                        }
                                    }
                                },
                            )
                            player = newPlayer
                        }
                    p.setMediaItem(MediaItem.fromUri(url))
                    p.prepare()
                    p.playWhenReady = true
                }
        }

        override fun onCleared() {
            job?.cancel()
            player?.release()
            player = null
        }
    }

private const val DELAY_MS = 4_000L
private const val CINEMA_DELAY_MS = 3_000L
private const val CINEMA_FADE_MS = 700
private const val TRAILER_VOLUME = .5f
private const val VOLUME_FADE_IN_MS = 4_000
private const val VOLUME_FADE_OUT_MS = 400
