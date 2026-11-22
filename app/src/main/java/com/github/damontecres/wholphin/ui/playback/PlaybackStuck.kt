package com.github.damontecres.wholphin.ui.playback

import android.os.SystemClock
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.VideoRange
import timber.log.Timber
import java.util.UUID

/**
 * Glowfin: "it just loads forever" (Ben's older TCL, Android 9, 2026-11-21).
 *
 * The TV's own video decoder had stopped answering. The player asked it to start, never got a reply
 * and never got an error either, so the loading circle spun for ever, for everything, even after
 * Glowfin was closed and opened again. Network trouble can't do this (it times out and reports an
 * error after a few seconds), so loading this long means the picture or sound part of the TV is stuck.
 *
 * This keeps an eye on the loading circle. When it has been going far too long it starts the video
 * again once with a brand new player, and if that loads for ever too it says so in plain words
 * (see [StuckPlayerException]) instead of leaving the circle spinning.
 */
enum class StuckReason {
    /** Loading normally (or playing) */
    NONE,

    /** Video is loaded and waiting, or the player is waiting on the TV's decoder, and nothing comes out */
    DECODER,

    /** Never started and we can't tell why */
    UNKNOWN,
}

/** The video loaded for ever. Shown as a plain-words page by ErrorMessage */
class StuckPlayerException(
    val reason: StuckReason,
) : Exception("The player kept loading and never started ($reason)")

/**
 * Pure decision, so it can be unit tested.
 *
 * @param loadingForMs how long the loading circle has been showing without a break
 * @param loadedAheadMs how much video is already loaded and waiting to be shown
 * @param waitingOnDecoder the player has been found waiting inside the TV's decoder on every recent look
 * @param started this video has shown a picture before
 * @param live a live TV channel (these may take long to start, and never have much loaded ahead)
 */
fun decideStuck(
    loadingForMs: Long,
    loadedAheadMs: Long,
    waitingOnDecoder: Boolean,
    started: Boolean,
    live: Boolean,
): StuckReason =
    when {
        loadingForMs < STUCK_AFTER_MS -> StuckReason.NONE
        waitingOnDecoder -> StuckReason.DECODER
        loadedAheadMs >= STUCK_LOADED_AHEAD_MS -> StuckReason.DECODER
        !started && !live && loadingForMs >= STUCK_NEVER_STARTED_MS -> StuckReason.UNKNOWN
        else -> StuckReason.NONE
    }

/** Where the player's own worker is right now (it is the one that talks to the TV's decoder) */
@OptIn(UnstableApi::class)
private fun playbackThreadStack(player: ExoPlayer): Array<StackTraceElement> =
    runCatching { player.playbackLooper.thread.stackTrace }.getOrDefault(emptyArray())

private fun inDecoder(stack: Array<StackTraceElement>): Boolean =
    stack.take(DECODER_FRAMES).any {
        it.className.startsWith("android.media.")
    }

/** Runs for as long as this player lives (it is one of the view model's jobs) */
@OptIn(UnstableApi::class)
internal fun PlaybackViewModel.watchForStuckPlayer(): Job =
    viewModelScope.launch(WholphinDispatchers.Main + ExceptionHandler()) {
        val exo = player as? ExoPlayer ?: return@launch
        var loadingSince = 0L
        var watchedItem: UUID? = null
        var decoderLooks = 0
        while (isActive) {
            delay(STUCK_LOOK_EVERY_MS)
            if (exo.isReleased || player !== exo || stuckGaveUp) return@launch
            val playback = state.value.currentPlayback
            if (playback == null ||
                state.value.loading != LoadingState.Success ||
                exo.playbackState != Player.STATE_BUFFERING
            ) {
                loadingSince = 0L
                decoderLooks = 0
                continue
            }
            val now = SystemClock.elapsedRealtime()
            if (playback.item.id != watchedItem) {
                watchedItem = playback.item.id
                loadingSince = 0L
                decoderLooks = 0
            }
            if (loadingSince == 0L) loadingSince = now
            // Skipping around starts the wait again
            if (lastSeekAt > loadingSince) loadingSince = lastSeekAt
            val stack = playbackThreadStack(exo)
            decoderLooks = if (inDecoder(stack)) decoderLooks + 1 else 0
            val reason =
                decideStuck(
                    loadingForMs = now - loadingSince,
                    loadedAheadMs = exo.totalBufferedDuration,
                    waitingOnDecoder = decoderLooks >= DECODER_LOOKS,
                    started = firstReadyAt != 0L,
                    live = playback.item.type == BaseItemKind.TV_CHANNEL,
                )
            if (reason == StuckReason.NONE) continue
            Timber.w(
                "Player stuck loading (%s): item=%s, play=%s, loading for %s ms, loaded ahead %s ms, started=%s, player is at:\n%s",
                reason,
                playback.item.id,
                playback.playMethod,
                now - loadingSince,
                exo.totalBufferedDuration,
                firstReadyAt != 0L,
                stack.take(STACK_LINES).joinToString("\n") { "    at $it" },
            )
            onStuckPlayer(exo, playback, reason)
            return@launch
        }
    }

@OptIn(UnstableApi::class)
private fun PlaybackViewModel.onStuckPlayer(
    exo: ExoPlayer,
    playback: CurrentPlayback,
    reason: StuckReason,
) {
    val itemNow = playback.item.id
    if (stuckRetryFor == itemNow) {
        // A brand new player got stuck the same way: say so instead of loading for ever
        Timber.w("Still stuck after starting again, giving up on %s", itemNow)
        stuckGaveUp = true
        runCatching { exo.stop() }
        mutableState.update { it.copy(loading = LoadingState.Error(null, StuckPlayerException(reason))) }
        return
    }
    stuckRetryFor = itemNow
    // Its own job: making the new player cancels the job this was called from
    viewModelScope.launch(WholphinDispatchers.Main + ExceptionHandler()) {
        showToast(context, "Still loading, so Glowfin is starting it again", Toast.LENGTH_SHORT)
        val positionMs = exo.currentPosition.coerceAtLeast(0L)
        val video = playback.mediaSourceInfo.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }
        withContext(WholphinDispatchers.IO) {
            createPlayer(
                isHdr = video?.videoRange == VideoRange.HDR,
                is4k = (video?.width ?: 0) > 2560,
                force = true,
            )
        }
        if (state.value.currentPlayback
                ?.item
                ?.id != itemNow
        ) {
            return@launch
        }
        mutableState.update { it.copy(currentPlayback = null) }
        changeStreams(
            item = currentItem.item,
            sourceId = playback.mediaSourceInfo.id,
            audioIndex = playback.audioIndex,
            subtitleIndex = playback.subtitleIndex,
            positionMs = positionMs,
            enableDirectPlay = playback.playMethod == PlayMethod.DIRECT_PLAY,
            enableDirectStream = playback.playMethod != PlayMethod.TRANSCODE,
        )
        player.prepare()
        player.play()
    }
}

/** Loading this long without a break is never normal: a slow connection gives an error well before */
internal const val STUCK_AFTER_MS = 20_000L

/** This much video loaded and still nothing on screen means the TV isn't showing what it is given */
internal const val STUCK_LOADED_AHEAD_MS = 8_000L

/** Never started at all, and nothing above explains it */
internal const val STUCK_NEVER_STARTED_MS = 90_000L
private const val STUCK_LOOK_EVERY_MS = 3_000L

/** Looks in a row that must find the player waiting on the TV's decoder (it normally answers at once) */
private const val DECODER_LOOKS = 4
private const val DECODER_FRAMES = 6
private const val STACK_LINES = 12
