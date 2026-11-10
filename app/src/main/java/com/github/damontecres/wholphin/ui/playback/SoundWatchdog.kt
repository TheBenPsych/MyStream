package com.github.damontecres.wholphin.ui.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/** What the sound check found */
enum class SoundProblem {
    /** Sound is fine (or we can't tell) */
    NONE,

    /** The video has sound, but the player got no sound track it can play */
    NO_SOUND_TRACK,

    /** A sound track is on and the video moves on, but no sound reaches the speakers */
    SOUND_STUCK,
}

/** One look at the player, taken a few seconds apart */
data class SoundSnapshot(
    val positionMs: Long,
    /** Sound pieces that really went out to the speakers */
    val audioBuffers: Int?,
    /** Sound pieces the player threw away (for example because their time stamp was before the picture) */
    val skippedBuffers: Int? = null,
)

/**
 * Pure decision, so it can be unit tested.
 *
 * @param videoHasSound the server says this video has at least one sound track
 * @param playerHasVideo the player has found the video track (so tracks are loaded)
 * @param playerHasPlayableSound the player found a sound track it can play
 * @param soundSelected a sound track is switched on in the player
 */
fun decideSoundProblem(
    videoHasSound: Boolean,
    playerHasVideo: Boolean,
    playerHasPlayableSound: Boolean,
    soundSelected: Boolean,
    before: SoundSnapshot,
    after: SoundSnapshot,
): SoundProblem {
    if (!videoHasSound || !playerHasVideo) return SoundProblem.NONE
    if (!playerHasPlayableSound) return SoundProblem.NO_SOUND_TRACK
    if (!soundSelected) return SoundProblem.NONE
    val moved = after.positionMs - before.positionMs
    val buffersBefore = before.audioBuffers ?: return SoundProblem.NONE
    val buffersAfter = after.audioBuffers ?: return SoundProblem.NONE
    return if (moved >= MIN_PROGRESS_MS && buffersAfter == buffersBefore) {
        SoundProblem.SOUND_STUCK
    } else {
        SoundProblem.NONE
    }
}

/** The video must move on at least this much while no sound goes out, to count as stuck */
internal const val MIN_PROGRESS_MS = 2_000L

/**
 * Benflix sound watchdog. After a skip, a jump, a sound/subtitle switch or a new video, it
 * checks for a few seconds that sound is really coming out. If the picture keeps going but no
 * sound does, it brings the sound back:
 *  1. first by restarting the sound part of the player (quick, the picture keeps playing),
 *  2. then by asking the server to convert just the sound to a simple format,
 *  3. and last by letting the server convert the whole video.
 *
 * Only for the normal player (ExoPlayer); MPV handles sound itself.
 */
@OptIn(UnstableApi::class)
class SoundWatchdog(
    private val scope: CoroutineScope,
    private val player: () -> Player?,
    private val videoHasSound: () -> Boolean,
    private val onConvertSound: (fullConvert: Boolean) -> Unit,
    private val onNotice: (String) -> Unit,
    /**
     * True when the server is converting or repackaging the video (not playing the file as is).
     * Then restarting the sound on the TV doesn't help, because the sound the server sent after a
     * jump is what's broken, so it goes straight to asking the server for a fresh stream from here.
     */
    private val fromServerStream: () -> Boolean = { false },
    /** Extra facts for the log (how it plays, which sound, Night mode) */
    private val describePlayback: () -> String = { "" },
) {
    private var job: Job? = null
    private var fixesTried = 0
    private var kicking = false

    /** A new video started: forget earlier fixes */
    fun reset() {
        job?.cancel()
        fixesTried = 0
    }

    /** Something happened that can upset the sound, so check it again in a moment */
    fun check(reason: String) {
        if (kicking) return
        val exo = player() as? ExoPlayer ?: return
        job?.cancel()
        job =
            scope.launch {
                try {
                    runCheck(exo, reason)
                } catch (ex: kotlinx.coroutines.CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Sound check failed")
                }
            }
    }

    /**
     * "No sound" pressed in the player's Fix a problem list: try the next fix straight away, without
     * first measuring for a few seconds (the person can already hear there's nothing).
     */
    fun fixNow() {
        job?.cancel()
        job =
            scope.launch {
                try {
                    onNotice("No sound? Bringing it back...")
                    val exo = player() as? ExoPlayer
                    Timber.i("Sound fix asked for by hand (fixes tried $fixesTried)")
                    if (exo != null && !exo.isReleased && fixesTried == 0 && !fromServerStream()) {
                        fixesTried++
                        restartSound(exo)
                        check("sound restart (asked)")
                    } else {
                        val fullConvert = fixesTried >= MAX_FIXES - 1
                        fixesTried = (maxOf(fixesTried, 1) + 1).coerceAtMost(MAX_FIXES)
                        onConvertSound(fullConvert)
                    }
                } catch (ex: kotlinx.coroutines.CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Sound fix failed")
                }
            }
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
        // The very end of a video can have the sound finish a little before the picture
        val duration = exo.duration
        if (duration != C.TIME_UNSET && duration - exo.currentPosition < NEAR_END_MS) return
        delay(SETTLE_MS)
        if (!exo.isPlaying || exo.isReleased) return check(reason)
        val before = snapshot(exo)
        delay(CHECK_MS)
        if (!exo.isPlaying || exo.isReleased) return check(reason)
        val after = snapshot(exo)

        val groups = exo.currentTracks.groups
        val problem =
            decideSoundProblem(
                videoHasSound = videoHasSound(),
                playerHasVideo = groups.any { it.type == C.TRACK_TYPE_VIDEO },
                playerHasPlayableSound = groups.any { it.type == C.TRACK_TYPE_AUDIO && it.isSupported },
                soundSelected = groups.any { it.type == C.TRACK_TYPE_AUDIO && it.isSelected },
                before = before,
                after = after,
            )
        lastReport = "after $reason: $problem (fixes tried $fixesTried), before=$before, after=$after, ${describe(exo)}"
        if (problem == SoundProblem.NONE) {
            Timber.i("Sound check ok: %s", lastReport)
            // Sound is fine again, so a later drop-out (the next skip) gets every fix again
            fixesTried = 0
            return
        }
        Timber.w("Sound check: %s", lastReport)

        when {
            problem == SoundProblem.SOUND_STUCK && fixesTried == 0 && !fromServerStream() -> {
                fixesTried++
                onNotice("No sound? Bringing it back...")
                restartSound(exo)
                check("sound restart")
            }

            fixesTried < MAX_FIXES -> {
                // Restarting didn't help, or the player can't play this sound at all
                val fullConvert = fixesTried >= MAX_FIXES - 1
                fixesTried = maxOf(fixesTried, 1) + 1
                onNotice("No sound? Bringing it back...")
                onConvertSound(fullConvert)
            }

            else -> {
                Timber.w("Sound still missing after all fixes")
            }
        }
    }

    /** Switches the sound off and on again, which rebuilds the sound output */
    private suspend fun restartSound(exo: ExoPlayer) {
        kicking = true
        try {
            val params = exo.trackSelectionParameters
            exo.trackSelectionParameters =
                params.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
            delay(RESTART_GAP_MS)
            if (!exo.isReleased) exo.trackSelectionParameters = params
        } finally {
            kicking = false
        }
    }

    private fun snapshot(exo: ExoPlayer): SoundSnapshot {
        val counters = exo.audioDecoderCounters?.also { it.ensureUpdated() }
        return SoundSnapshot(
            positionMs = exo.currentPosition,
            // Only pieces that really went out count. Thrown away pieces used to count too, which
            // hid the "sound after a skip has the wrong time stamps and is all thrown away" case.
            audioBuffers = counters?.renderedOutputBufferCount,
            skippedBuffers = counters?.let { it.skippedOutputBufferCount + it.droppedBufferCount },
        )
    }

    private fun describe(exo: ExoPlayer): String {
        val format = exo.audioFormat
        val sound =
            format?.let { "sound=${it.sampleMimeType} ${it.channelCount}ch ${it.sampleRate}Hz" } ?: "sound=none"
        val extra =
            try {
                describePlayback()
            } catch (ex: Exception) {
                ""
            }
        return "$sound, $extra"
    }

    companion object {
        /** Last thing the sound check saw, added to "Report a problem" so Ben's log shows it */
        @Volatile
        var lastReport: String = "no sound check yet"
            private set

        private const val POLL_MS = 500L
        private const val MAX_WAIT_MS = 30_000L
        private const val SETTLE_MS = 2_500L
        private const val CHECK_MS = 3_000L
        private const val RESTART_GAP_MS = 300L
        private const val NEAR_END_MS = 30_000L

        /** restart sound, convert sound, convert everything */
        private const val MAX_FIXES = 3
    }
}
