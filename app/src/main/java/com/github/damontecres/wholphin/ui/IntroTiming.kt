package com.github.damontecres.wholphin.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.annotation.RawRes
import androidx.compose.runtime.withFrameNanos
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.LiteMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * The intro's soft mallet notes, loaded into memory once when the app opens (a [SoundPool]), so a note
 * plays the instant it's asked for instead of a beat late. Each note is its own short sound, so the
 * intro can play every note on the exact frame its part of the animation starts, however fast or slow
 * the TV is.
 */
object IntroSounds {
    @RawRes val OPEN_LOW = R.raw.intro_note_g3

    @RawRes val OPEN_HIGH = R.raw.intro_note_d4

    /** One rising note for each door, in order: Movies, Shows, Live TV, Games */
    val DOORS = listOf(R.raw.intro_note_d4, R.raw.intro_note_fs4, R.raw.intro_note_a4, R.raw.intro_note_d5)

    /** The Music door's own note, a little louder, as it grows out of the centre of the logo */
    @RawRes val MUSIC_DOOR = R.raw.intro_note_fs5

    /** Benflix Books: the lime Books door's note (A5), right after Music's */
    @RawRes val BOOKS_DOOR = R.raw.intro_note_a5

    private val ALL = listOf(OPEN_LOW, OPEN_HIGH) + DOORS + MUSIC_DOOR + BOOKS_DOOR

    private var pool: SoundPool? = null
    private val soundIds = mutableMapOf<Int, Int>()
    private val loadedIds = mutableSetOf<Int>()
    private var finished = 0
    private val ready = MutableStateFlow(false)

    /** Starts loading the notes (safe to call many times; only the first call does anything) */
    @Synchronized
    fun preload(context: Context) {
        if (pool != null) return
        try {
            val attrs =
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            val p =
                SoundPool
                    .Builder()
                    .setMaxStreams(6)
                    .setAudioAttributes(attrs)
                    .build()
            pool = p
            p.setOnLoadCompleteListener { sp, sampleId, status ->
                synchronized(this) {
                    if (status == 0) loadedIds.add(sampleId)
                    finished++
                    if (finished == soundIds.size) {
                        // Wake up the sound output with a silent note, so the first real note isn't late
                        try {
                            sp.play(sampleId, 0f, 0f, 0, 0, 1f)
                        } catch (ex: Exception) {
                            Timber.w(ex, "Intro sound warm up")
                        }
                        ready.value = true
                    }
                }
            }
            val app = context.applicationContext
            ALL.distinct().forEach { res -> soundIds[res] = p.load(app, res, 1) }
        } catch (ex: Exception) {
            Timber.w(ex, "Could not load intro sounds")
            ready.value = true
        }
    }

    /** Waits (at most [timeoutMs]) until every note is in memory */
    suspend fun awaitReady(
        context: Context,
        timeoutMs: Long = 2500L,
    ) {
        preload(context)
        withTimeoutOrNull(timeoutMs) { ready.first { it } }
    }

    /** Plays a silent note, so the sound output is awake for the real ones that follow */
    fun warmUp(context: Context) {
        if (!soundAllowed(context)) return
        val p = pool ?: return
        val id = synchronized(this) { loadedIds.firstOrNull() } ?: return
        try {
            p.play(id, 0f, 0f, 0, 0, 1f)
        } catch (ex: Exception) {
            Timber.w(ex, "Intro sound warm up")
        }
    }

    /** Whether this is a TV, looked up once (it was asked of the system on every note, mid-animation) */
    @Volatile
    private var isTv: Boolean? = null

    /** Plays one note now. Phones on silent or vibrate stay quiet (TVs always play). */
    fun play(
        context: Context,
        @RawRes sound: Int,
        volume: Float,
    ) {
        if (!soundAllowed(context)) return
        val p = pool ?: return
        val id = synchronized(this) { soundIds[sound]?.takeIf { it in loadedIds } } ?: return
        try {
            p.play(id, volume, volume, 1, 0, 1f)
        } catch (ex: Exception) {
            Timber.w(ex, "Could not play intro note")
        }
    }

    private fun soundAllowed(context: Context): Boolean {
        val tv = isTv ?: context.packageManager.hasSystemFeature("android.software.leanback").also { isTv = it }
        if (tv) return true
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        return audio == null || audio.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }
}

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
    /** The longest step time takes in one frame: a hiccup pauses the animation instead of skipping part of it */
    val maxStepMs: Float get() = maxOf(34f, frameMs * 1.5f)

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
            stretch = stretch.coerceIn(1f, 1.5f)
            Timber.i("Intro pace: frame %.1f ms, stretch %.2f", frameMs, stretch)
            return IntroPace(frameMs, stretch)
        }
    }
}

/**
 * Runs [durationMs] of animation one real frame at a time. [onFrame] gets the time so far (0 to
 * [durationMs]) and draws from it, so sounds started inside it land on the same frame as the picture.
 * A slow frame only moves time on by [IntroPace.maxStepMs], so the whole animation is always shown.
 */
suspend fun runIntroTimeline(
    durationMs: Float,
    pace: IntroPace,
    onFrame: (elapsedMs: Float) -> Unit,
) {
    var last = -1L
    var elapsed = 0f
    onFrame(0f)
    while (elapsed < durationMs) {
        withFrameNanos { now ->
            if (last >= 0) elapsed += ((now - last) / 1_000_000f).coerceAtMost(pace.maxStepMs)
            last = now
            onFrame(elapsed.coerceAtMost(durationMs))
        }
    }
}
