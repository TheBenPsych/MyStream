package com.github.damontecres.wholphin.ui

import android.content.Context
import androidx.compose.runtime.withFrameNanos
import com.github.damontecres.wholphin.services.LiteMode
import timber.log.Timber

/**
 * How fast this TV draws, measured over a few real frames, and how much to stretch the intro so it
 * looks smooth: a fast TV plays it as designed, a slow one plays the same whole animation a little
 * slower (up to half again as long) so every step is still seen.
 */
class IntroPace(
    /** Typical time between frames, in milliseconds */
    val frameMs: Float,
    /** 1.0 = as designed; bigger = slower */
    val stretch: Float,
) {
    /**
     * The longest step time takes in one frame. Overhaul 2026-11-20: generous (three frames), so a slow TV
     * keeps to the clock instead of playing the logo screen at half speed (it took 4.5 s on the test TV).
     */
    val maxStepMs: Float get() = maxOf(100f, frameMs * 3f)

    companion object {
        /** Watches [frames] frames and works out the pace (the median, so one hiccup doesn't count) */
        suspend fun measure(
            context: Context,
            frames: Int = 8,
        ): IntroPace {
            val gaps = mutableListOf<Float>()
            var last = -1L
            repeat(frames + 1) {
                withFrameNanos { now ->
                    if (last >= 0) gaps.add((now - last) / 1_000_000f)
                    last = now
                }
            }
            val frameMs = gaps.sorted().getOrNull(gaps.size / 2)?.coerceIn(8f, 100f) ?: 16.7f
            // 60 frames a second plays as designed; 30 a second plays about a third slower
            var stretch = 1f + ((frameMs - 17f) / 16f).coerceIn(0f, 1f) * 0.35f
            // Slow devices (Lite mode) start a touch slower too
            if (LiteMode.isOn(context)) stretch = maxOf(stretch, 1.15f)
            // A busy first frame or two is common; more than that is a slow TV
            if (gaps.count { it > 40f } >= frames / 3) stretch = maxOf(stretch, 1.3f)
            // Overhaul 2026-11-20: a still logo needs little extra time, so slow TVs wait at most 15% longer
            stretch = stretch.coerceIn(1f, 1.15f)
            Timber.i("Intro pace: frame %.1f ms, stretch %.2f", frameMs, stretch)
            return IntroPace(frameMs, stretch)
        }
    }
}

/**
 * Runs [durationMs] of animation one real frame at a time. [onFrame] gets the time so far (0 to
 * [durationMs]) and draws from it, so sounds started inside it land on the same frame as the picture.
 * A slow frame only moves time on by [IntroPace.maxStepMs], so the whole animation is always shown.
 * [byTheClock] is for parts where nothing moves (a still logo): real time counts, so slow frames while the
 * app is still loading don't make the wait longer.
 */
suspend fun runIntroTimeline(
    durationMs: Float,
    pace: IntroPace,
    byTheClock: Boolean = false,
    onFrame: (elapsedMs: Float) -> Unit,
) {
    var last = -1L
    var elapsed = 0f
    onFrame(0f)
    while (elapsed < durationMs) {
        withFrameNanos { now ->
            if (last >= 0) {
                val step = (now - last) / 1_000_000f
                elapsed += if (byTheClock) step else step.coerceAtMost(pace.maxStepMs)
            }
            last = now
            onFrame(elapsed.coerceAtMost(durationMs))
        }
    }
}
