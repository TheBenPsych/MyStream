package com.github.damontecres.wholphin.ui.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

/** What the picture check found */
enum class PictureProblem {
    /** The picture plays smoothly (or we can't tell) */
    NONE,

    /**
     * The TV's own video chip could play this video, but the slow backup decoder (the processor
     * doing it by hand) got picked, usually because the video chip was still busy with a trailer
     */
    SLOW_DECODER,

    /** Many frames are thrown away or far fewer frames come out than the video has */
    CHOPPY,
}

/** One look at the picture counters, taken a few seconds apart */
data class PictureSnapshot(
    val positionMs: Long,
    /** Frames that really reached the screen */
    val renderedFrames: Int,
    /** Frames thrown away because they were ready too late */
    val droppedFrames: Int,
)

/**
 * Pure decision, so it can be unit tested.
 *
 * @param slowDecoderWhileChipCan a software decoder is playing a video the hardware decoder supports
 * @param videoFps frames per second the video should have, or null/0 when unknown
 */
fun decidePictureProblem(
    slowDecoderWhileChipCan: Boolean,
    before: PictureSnapshot,
    after: PictureSnapshot,
    videoFps: Float?,
): PictureProblem {
    if (slowDecoderWhileChipCan) return PictureProblem.SLOW_DECODER
    val movedMs = after.positionMs - before.positionMs
    if (movedMs < MIN_PICTURE_PROGRESS_MS) return PictureProblem.NONE
    val dropped = after.droppedFrames - before.droppedFrames
    val rendered = after.renderedFrames - before.renderedFrames
    if (dropped < 0 || rendered < 0) return PictureProblem.NONE
    val total = dropped + rendered
    if (dropped >= MIN_DROPPED && total > 0 && dropped.toFloat() / total >= DROPPED_SHARE) {
        return PictureProblem.CHOPPY
    }
    if (videoFps != null && videoFps > 1f) {
        val expected = videoFps * movedMs / 1000f
        if (expected >= MIN_DROPPED && rendered < expected * RENDERED_SHARE) return PictureProblem.CHOPPY
    }
    return PictureProblem.NONE
}

/** The video must move on at least this much to judge the picture */
internal const val MIN_PICTURE_PROGRESS_MS = 3_000L

/** At least this many thrown away frames before calling it choppy (a few at the start are normal) */
internal const val MIN_DROPPED = 12

/** Choppy when this share of frames is thrown away */
internal const val DROPPED_SHARE = 0.15f

/** Choppy when fewer than this share of the video's frames reach the screen */
internal const val RENDERED_SHARE = 0.7f

/**
 * Benflix picture watchdog. Sometimes a video starts with a low, jerky frame rate and only plays
 * smoothly after starting it again. That happens when the TV's video chip was still busy (for
 * example with the trailer that was playing on the page before), so the player fell back to the
 * much slower backup decoder. A few seconds after a video starts, this checks the picture; if it
 * is choppy or on the slow decoder, it restarts the video once at the same spot, which picks the
 * video chip again. Only for the normal player (ExoPlayer).
 */
@OptIn(UnstableApi::class)
class PictureWatchdog(
    private val context: Context,
    private val scope: CoroutineScope,
    private val player: () -> ExoPlayer?,
    private val onNotice: (String) -> Unit,
    /** Runs after the video was restarted (so the sound check can look again) */
    private val afterRestart: () -> Unit = {},
) {
    private var job: Job? = null

    /** The video the one restart was used for */
    private var restartedFor: UUID? = null
    private var itemId: UUID? = null

    /** A video started (or the server sent a new stream): check its picture in a moment */
    fun check(
        item: UUID?,
        reason: String,
    ) {
        val exo = player() ?: return
        itemId = item
        job?.cancel()
        job =
            scope.launch {
                try {
                    runCheck(exo, reason)
                } catch (ex: kotlinx.coroutines.CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Picture check failed")
                }
            }
    }

    fun cancel() {
        job?.cancel()
    }

    /** "Picture stutters" pressed in the player: start the picture again at the same spot now (false: can't here) */
    fun restartNow(item: UUID?): Boolean {
        val exo = player() ?: return false
        job?.cancel()
        itemId = item
        restartedFor = item
        lastReport = "restart asked for by hand"
        onNotice("Smoothing out the picture...")
        restart(exo)
        afterRestart()
        check(item, "picture restart (asked)")
        return true
    }

    private suspend fun waitUntilPlaying(exo: ExoPlayer): Boolean {
        var waited = 0L
        while (scope.isActive && !exo.isPlaying) {
            if (waited > MAX_WAIT_MS || exo.isReleased) return false
            delay(POLL_MS)
            waited += POLL_MS
        }
        return !exo.isReleased
    }

    private suspend fun runCheck(
        exo: ExoPlayer,
        reason: String,
    ) {
        if (!waitUntilPlaying(exo)) return
        // The first moments after a start always throw a few frames away while things settle
        delay(SETTLE_MS)
        if (!exo.isPlaying || exo.isReleased) return
        val before = snapshot(exo) ?: return
        delay(CHECK_MS)
        if (!exo.isPlaying || exo.isReleased) return
        val after = snapshot(exo) ?: return

        val format = exo.videoFormat
        val decoder = decoderName
        val slowDecoder = format != null && decoder != null && isSlowDecoderWhileChipCan(context, decoder, format)
        val fps = format?.frameRate?.takeIf { it != Format.NO_VALUE.toFloat() }
        val problem = decidePictureProblem(slowDecoder, before, after, fps)
        val alreadyRestarted = itemId != null && restartedFor == itemId
        lastReport =
            "after $reason: $problem (restarted=$alreadyRestarted), decoder=$decoder, " +
            "video=${format?.sampleMimeType} ${format?.width}x${format?.height} ${fps}fps, before=$before, after=$after"
        if (problem == PictureProblem.NONE) {
            Timber.i("Picture check ok: %s", lastReport)
            return
        }
        Timber.w("Picture check: %s", lastReport)
        if (alreadyRestarted) {
            // One restart per video, so a TV that just can't play this one smoothly isn't restarted over and over
            Timber.w("Picture still not smooth after a restart, leaving it")
            return
        }
        restartedFor = itemId
        onNotice("Smoothing out the picture...")
        restart(exo)
        afterRestart()
        check(itemId, "picture restart")
    }

    /**
     * Starts the video again at the same spot. Stopping lets go of the decoder, so preparing again
     * picks the best one (the video chip) from scratch, the same as starting the video again by hand.
     */
    private fun restart(exo: ExoPlayer) {
        val position = exo.currentPosition
        val wasPlaying = exo.playWhenReady
        exo.stop()
        exo.seekTo(position)
        exo.prepare()
        exo.playWhenReady = wasPlaying
    }

    private fun snapshot(exo: ExoPlayer): PictureSnapshot? {
        val counters = exo.videoDecoderCounters ?: return null
        counters.ensureUpdated()
        return PictureSnapshot(
            positionMs = exo.currentPosition,
            renderedFrames = counters.renderedOutputBufferCount,
            droppedFrames = counters.droppedBufferCount,
        )
    }

    /** The video decoder the player is using now, set from the player's events */
    @Volatile
    var decoderName: String? = null

    companion object {
        /** Last thing the picture check saw, added to "Report a problem" */
        @Volatile
        var lastReport: String = "no picture check yet"
            private set

        private const val POLL_MS = 500L
        private const val MAX_WAIT_MS = 30_000L
        private const val SETTLE_MS = 4_000L
        private const val CHECK_MS = 5_000L

        /**
         * True when [decoderName] is a software MediaCodec decoder but a hardware decoder on this
         * device says it can play [format]. Extension decoders (FFmpeg, AV1) aren't in the
         * MediaCodec list, so they are never flagged: those are picked on purpose.
         */
        fun isSlowDecoderWhileChipCan(
            context: Context,
            decoderName: String,
            format: Format,
        ): Boolean {
            val mime = format.sampleMimeType ?: return false
            val infos =
                try {
                    MediaCodecUtil.getDecoderInfos(mime, false, false)
                } catch (ex: Exception) {
                    return false
                }
            val current = infos.firstOrNull { it.name == decoderName } ?: return false
            if (current.hardwareAccelerated) return false
            return infos.any {
                it.hardwareAccelerated &&
                    try {
                        it.isFormatSupported(context, format)
                    } catch (ex: Exception) {
                        false
                    }
            }
        }
    }
}
