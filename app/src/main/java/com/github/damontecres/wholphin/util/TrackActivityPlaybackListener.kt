package com.github.damontecres.wholphin.util

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.github.damontecres.wholphin.services.BenflixWatchTime
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.playback.CurrentPlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.extensions.inWholeTicks
import timber.log.Timber
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Listens to playback and periodically saves playback activity to the server
 */
@OptIn(UnstableApi::class)
class TrackActivityPlaybackListener(
    private val api: ApiClient,
    private val player: Player,
    /** Benflix: count real watch time for the monthly and yearly stats (movies and episodes only) */
    countWatchTime: Boolean = false,
    private val getState: () -> PlaybackItemState?,
) : Player.Listener {
    private val watchUser = if (countWatchTime) BenflixWatchTime.instance?.currentUserId() else null

    /** When it last started playing (or was last counted), 0 while paused or stopped */
    private var playingSinceMs = 0L

    /** Adds the time played since last time; null = keep counting only if it was already playing */
    @Synchronized
    private fun countWatched(stillPlaying: Boolean?) {
        if (watchUser == null) return
        val now = SystemClock.elapsedRealtime()
        if (playingSinceMs > 0) {
            BenflixWatchTime.instance?.add(watchUser, (now - playingSinceMs).coerceIn(0L, MAX_STEP_MS))
        }
        playingSinceMs = if (stillPlaying ?: (playingSinceMs > 0)) now else 0L
    }

    private val coroutineScope = CoroutineScope(WholphinDispatchers.Main)
    private val task: TimerTask =
        object : TimerTask() {
            override fun run() {
                try {
                    countWatched(stillPlaying = null)
                    saveActivity(-1L)
                } catch (ex: Exception) {
                    Timber.w(ex, "Exception during track activity timer")
                }
            }
        }

    @Volatile
    private var initialized = false

    fun init() {
        launch("reportPlaybackStart") {
            getState.invoke()?.let { state ->
                Timber.v("reportPlaybackStart for ${state.itemId}")
                api.playStateApi.reportPlaybackStart(
                    PlaybackStartInfo(
                        canSeek = true,
                        itemId = state.itemId,
                        isPaused = withContext(WholphinDispatchers.Main) { !player.isPlaying },
                        playMethod = state.playMethod,
                        repeatMode = RepeatMode.REPEAT_NONE,
                        playbackOrder = PlaybackOrder.DEFAULT,
                        isMuted = false,
                        audioStreamIndex = state.audioStreamIndex,
                        subtitleStreamIndex = state.subtitleStreamIndex,
                        playSessionId = state.playSessionId,
                        liveStreamId = state.liveStreamId,
                    ),
                )

                val delay = 5.seconds.inWholeMilliseconds
                // Every x seconds, check if the video is playing
                TIMER.schedule(task, delay, delay)
                initialized = true
            }
        }
    }

    @Volatile
    private var released = false

    /**
     * Reports "stopped" once. Call it before the player itself is released, since it reads the
     * position from the player (a released player can report 0, which would wipe the resume spot)
     */
    fun release() {
//        player.removeListener(this)
        if (released) return
        released = true
        task.cancel()
        TIMER.purge()
        countWatched(false)
        BenflixWatchTime.instance?.flush(watchUser)
        val position = player.currentPosition.milliseconds
        // Benflix: a finished anime episode gets ticked off on AniList (if the person signed in)
        getState.invoke()?.let { state ->
            com.github.damontecres.wholphin.services.AniListSync.instance
                ?.watched(state.itemId, position.inWholeMilliseconds, player.duration)
        }
        val after = afterStopped
        afterStopped = null
        launch("reportPlaybackStopped") {
            try {
                getState.invoke()?.let { state ->
                    Timber.v("reportPlaybackStopped for ${state.itemId} at $position")
                    if (position < Duration.ZERO) {
                        Timber.w("Negative position when reporting playback stopped: %s", position)
                    }
                    api.playStateApi.reportPlaybackStopped(
                        PlaybackStopInfo(
                            itemId = state.itemId,
                            positionTicks = position.inWholeTicks.takeIf { it >= 0 },
                            failed = false,
                            playSessionId = state.playSessionId,
                            liveStreamId = state.liveStreamId,
                        ),
                    )
                }
            } finally {
                // Benflix: runs after "stopped" is sent, so the server doesn't undo it
                if (after != null) launch("afterStopped") { after.invoke() }
            }
        }
    }

    @Volatile
    private var afterStopped: (suspend () -> Unit)? = null

    /**
     * Benflix: run [block] once "stopped" has been sent to the server (straight away if it
     * already was). Call it on the main thread.
     */
    fun runAfterStopped(block: suspend () -> Unit) {
        if (released) {
            launch("afterStopped") { block.invoke() }
        } else {
            afterStopped = block
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        countWatched(isPlaying)
        if (initialized) {
            saveActivity(-1)
        } else if (isPlaying) {
            init()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            Timber.v("onPlaybackStateChanged STATE_ENDED")
            saveActivity(player.duration)
        }
    }

    private fun saveActivity(position: Long) {
        launch("saveActivity") {
            getState.invoke()?.let { state ->
                val calcPosition =
                    withContext(WholphinDispatchers.Main) {
                        (if (position >= 0) position else player.currentPosition)
                    }
                if (calcPosition > 0) {
                    val isPaused = withContext(WholphinDispatchers.Main) { !player.isPlaying }
                    Timber.v("saveActivity: itemId=${state.itemId}, pos=$calcPosition")
                    api.playStateApi.reportPlaybackProgress(
                        PlaybackProgressInfo(
                            itemId = state.itemId,
                            positionTicks = calcPosition.milliseconds.inWholeTicks,
                            canSeek = true,
                            isPaused = isPaused,
                            isMuted = false,
                            playMethod = state.playMethod,
                            repeatMode = RepeatMode.REPEAT_NONE,
                            playbackOrder = PlaybackOrder.DEFAULT,
                            audioStreamIndex = state.audioStreamIndex,
                            subtitleStreamIndex = state.subtitleStreamIndex,
                            playSessionId = state.playSessionId,
                            liveStreamId = state.liveStreamId,
                        ),
                    )
                }
            }
        }
    }

    private fun launch(
        name: String,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        coroutineScope.launchIO {
            try {
                block.invoke(this)
            } catch (ex: Exception) {
                Timber.w(ex, "Exception during %s", name)
            }
        }
    }

    companion object {
        private const val TAG = "TrackActivityPlaybackListener"

        private const val MAX_STEP_MS = 60_000L

        private val TIMER by lazy { Timer("$TAG-timer", true) }
    }
}

data class PlaybackItemState(
    val itemId: UUID,
    val playMethod: PlayMethod,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    val playSessionId: String? = null,
    val liveStreamId: String? = null,
) {
    constructor(
        playback: CurrentPlayback,
    ) : this(
        itemId = playback.item.id,
        playMethod = playback.playMethod,
        audioStreamIndex = playback.audioIndex.takeIf { it >= 0 },
        subtitleStreamIndex = playback.subtitleIndex.takeIf { it >= 0 },
        playSessionId = playback.playSessionId,
        liveStreamId = playback.liveStreamId,
    )
}
