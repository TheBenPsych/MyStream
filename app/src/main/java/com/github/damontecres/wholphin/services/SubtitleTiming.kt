@file:OptIn(markerClass = [UnstableApi::class])

package com.github.damontecres.wholphin.services

import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer

/**
 * Benflix: "Subtitle timing" for the normal player. The words are moved by telling the subtitle
 * part of the player that the video is a little earlier (words show later) or a little later
 * (words show sooner) than it really is. Nothing else in the player sees the change.
 */
object SubtitleTiming {
    /** How much later the words show, in microseconds. Below zero shows them sooner. */
    @Volatile
    var delayUs: Long = 0L
}

class DelayedTextRenderer(
    renderer: Renderer,
) : ForwardingRenderer(renderer) {
    override fun render(
        positionUs: Long,
        elapsedRealtimeUs: Long,
    ) {
        super.render(positionUs - SubtitleTiming.delayUs, elapsedRealtimeUs)
    }

    override fun resetPosition(
        positionUs: Long,
        sampleStreamIsResetToKeyFrame: Boolean,
    ) {
        super.resetPosition(positionUs - SubtitleTiming.delayUs, sampleStreamIsResetToKeyFrame)
    }
}

/**
 * Sound and lips don't match (Ben 2026-11-21): the picture can be shown a little later or sooner
 * than the sound, for soundbars and TVs that play the sound late (or early). Same trick as the
 * subtitle timing: only the video part of the player is told a slightly different time.
 * Remembered on this TV or phone.
 */
object LipSync {
    private const val PREFS = "benflix_audio"
    private const val KEY = "lip_sync_ms"
    const val STEP_MS = 50
    const val MAX_MS = 400

    /** How much later the picture shows, in microseconds. Below zero shows it sooner. */
    @Volatile
    var videoDelayUs: Long = 0L

    /** True while the player in use can move the picture (it was built with a nudge saved) */
    @Volatile
    var live: Boolean = false

    fun savedMs(context: android.content.Context): Int =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getInt(KEY, 0)

    fun set(
        context: android.content.Context,
        ms: Int,
    ): Int {
        val value = ms.coerceIn(-MAX_MS, MAX_MS)
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit { putInt(KEY, value) }
        videoDelayUs = value * 1000L
        return value
    }
}

class DelayedVideoRenderer(
    renderer: Renderer,
) : ForwardingRenderer(renderer) {
    override fun render(
        positionUs: Long,
        elapsedRealtimeUs: Long,
    ) {
        super.render(positionUs - LipSync.videoDelayUs, elapsedRealtimeUs)
    }
}
