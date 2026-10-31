package com.github.damontecres.wholphin.ui.detail

import android.content.Context
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Netflix-style: after a few seconds on a movie or show page, its local trailer plays muted
 * behind the title. Stops when [active] turns false (scrolled down or left the page).
 */
@Composable
fun BoxScope.DetailsTrailer(
    item: BaseItem?,
    active: Boolean,
    viewModel: DetailsTrailerViewModel = hiltViewModel(),
) {
    val playing by viewModel.playing.collectAsState()
    // Also stops while the app is in the background or another screen is on top
    LifecycleResumeEffect(item?.id, active) {
        viewModel.preview(if (active) item else null)
        onPauseOrDispose { viewModel.preview(null) }
    }
    if (active && playing) {
        HeroTrailer(
            player = viewModel.player ?: return,
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
