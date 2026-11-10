package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** What the song preview is doing: which song (by its catalogue id), and whether it's still starting */
data class SongPreviewState(
    val songId: Long? = null,
    val loading: Boolean = false,
    /** The song that had no preview, for a short "No preview" on its button */
    val missingId: Long? = null,
)

/**
 * Benflix: a 30 second taste of a song on Requests > Music and in Search's Ask for it, like Apple Music's previews.
 * Tower's helper hands back a fresh free preview link (Deezer, or Apple's iTunes when Deezer has none) and it plays
 * here. Only one plays at a time; pressing the same song again stops it. Benflix music that was playing pauses
 * for the preview and carries on after.
 */
@Singleton
class SongPreview
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val helper: GameLibraryClient,
        private val musicService: MusicService,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val _state = MutableStateFlow(SongPreviewState())
        val state: StateFlow<SongPreviewState> = _state

        private var player: ExoPlayer? = null
        private var job: Job? = null
        private var pausedMusic = false

        /** Plays [songId]'s preview, or stops it when it's the one playing */
        fun toggle(songId: Long) {
            if (_state.value.songId == songId) stop() else play(songId)
        }

        fun play(songId: Long) {
            stopPlayer()
            job?.cancel()
            _state.value = SongPreviewState(songId = songId, loading = true)
            job =
                scope.launch {
                    val url =
                        try {
                            lookUp(songId)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.w(e, "No preview for %s", songId)
                            null
                        }
                    if (url.isNullOrBlank()) {
                        noPreview(songId)
                        return@launch
                    }
                    start(songId, url)
                }
        }

        /** Stops the preview (leaving the page does this too) and lets paused Benflix music carry on */
        fun stop() {
            job?.cancel()
            job = null
            stopPlayer()
            if (_state.value.songId != null || _state.value.loading) _state.value = SongPreviewState()
            resumeMusic()
        }

        private suspend fun lookUp(songId: Long): String? {
            val j: JSONObject = helper.getJson("/api/music/preview?id=$songId")
            return j.optString("url").takeIf { it.startsWith("http") }
        }

        private fun start(
            songId: Long,
            url: String,
        ) {
            if (musicService.state.value.status == NowPlayingStatus.PLAYING) {
                try {
                    musicService.player.pause()
                    pausedMusic = true
                } catch (e: Exception) {
                    Timber.w(e, "Couldn't pause music for a preview")
                }
            }
            val p =
                ExoPlayer.Builder(context).build().apply {
                    setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                            .build(),
                        false,
                    )
                    addListener(
                        object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_READY && _state.value.songId == songId) {
                                    _state.value = _state.value.copy(loading = false)
                                }
                                if (playbackState == Player.STATE_ENDED && _state.value.songId == songId) stop()
                            }

                            override fun onPlayerError(error: PlaybackException) {
                                Timber.w(error, "Preview couldn't play")
                                if (_state.value.songId == songId) noPreview(songId)
                            }
                        },
                    )
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                    play()
                }
            player = p
        }

        private fun noPreview(songId: Long) {
            stopPlayer()
            resumeMusic()
            _state.value = SongPreviewState(missingId = songId)
            scope.launch {
                delay(3_000)
                if (_state.value.missingId == songId) _state.value = _state.value.copy(missingId = null)
            }
        }

        private fun stopPlayer() {
            player?.let {
                try {
                    it.stop()
                    it.release()
                } catch (e: Exception) {
                    Timber.w(e, "Couldn't stop the preview")
                }
            }
            player = null
        }

        private fun resumeMusic() {
            if (!pausedMusic) return
            pausedMusic = false
            try {
                musicService.player.play()
            } catch (e: Exception) {
                Timber.w(e, "Couldn't carry on the music after a preview")
            }
        }
    }

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface SongPreviewEntryPoint {
    fun songPreview(): SongPreview
}

/** The app's song preview player, for screens without their own view model */
fun songPreview(context: Context): SongPreview =
    dagger.hilt.android.EntryPointAccessors
        .fromApplication(context.applicationContext, SongPreviewEntryPoint::class.java)
        .songPreview()
