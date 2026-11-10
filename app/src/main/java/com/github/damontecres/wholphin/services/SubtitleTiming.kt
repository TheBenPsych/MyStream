@file:OptIn(markerClass = [UnstableApi::class])

package com.github.damontecres.wholphin.services

import androidx.annotation.OptIn
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
