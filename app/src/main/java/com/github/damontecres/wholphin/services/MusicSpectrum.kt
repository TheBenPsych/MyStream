package com.github.damontecres.wholphin.services

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Benflix music bars: listens to the sound inside Benflix's own music player (no microphone, so no
 * permission pop-up) and splits it into [BANDS] loudness levels, low sounds first. The now playing
 * towers read [levels] about 30 times a second.
 *
 * The sound passes through here on its way to the speakers. Nothing is changed. The sums only run while
 * a screen with towers is showing ([watching] above zero), so it costs nothing the rest of the time.
 */
@OptIn(UnstableApi::class)
object MusicSpectrum : TeeAudioProcessor.AudioBufferSink {
    const val BANDS = 32

    /** How many screens are showing towers right now */
    @Volatile
    var watching = 0

    /** 0 to 1 for each band, low to high. Read from the screen, written from the sound thread. */
    val levels = FloatArray(BANDS)

    /** When the sound last came through, so the towers can settle when nothing is playing */
    @Volatile
    var lastSoundAt = 0L
        private set

    private const val SIZE = 1024
    private val window = FloatArray(SIZE) { (0.5 - 0.5 * cos(2 * PI * it / (SIZE - 1))).toFloat() }
    private val mono = FloatArray(SIZE)
    private var filled = 0
    private val re = FloatArray(SIZE)
    private val im = FloatArray(SIZE)
    private var sampleRate = 44100
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT
    private var lastSum = 0L

    /** Which FFT bins belong to each band (log spaced, about 40 Hz to 16 kHz) */
    private var edges = IntArray(BANDS + 1)

    override fun flush(
        sampleRateHz: Int,
        channelCount: Int,
        encoding: Int,
    ) {
        sampleRate = sampleRateHz.coerceAtLeast(8000)
        channels = channelCount.coerceAtLeast(1)
        this.encoding = encoding
        filled = 0
        val lo = ln(40.0)
        val hi = ln(min(16000.0, sampleRate / 2.0 - 1))
        edges =
            IntArray(BANDS + 1) { i ->
                val f = kotlin.math.exp(lo + (hi - lo) * i / BANDS)
                (f * SIZE / sampleRate).toInt().coerceIn(1, SIZE / 2 - 1)
            }
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (watching <= 0) return
        lastSoundAt = System.currentTimeMillis()
        val b = buffer.duplicate().order(ByteOrder.nativeOrder())
        when (encoding) {
            C.ENCODING_PCM_16BIT -> {
                val frames = b.remaining() / (2 * channels)
                repeat(frames) {
                    var sum = 0f
                    repeat(channels) { sum += b.getShort().toFloat() / 32768f }
                    add(sum / channels)
                }
            }

            C.ENCODING_PCM_FLOAT -> {
                val frames = b.remaining() / (4 * channels)
                repeat(frames) {
                    var sum = 0f
                    repeat(channels) { sum += b.getFloat() }
                    add(sum / channels)
                }
            }

            else -> {
                Unit
            }
        }
    }

    private fun add(sample: Float) {
        mono[filled++] = sample
        if (filled < SIZE) return
        filled = 0
        // About 30 times a second is plenty for the towers
        val now = System.nanoTime()
        if (now - lastSum < 30_000_000L) return
        lastSum = now
        for (i in 0 until SIZE) {
            re[i] = mono[i] * window[i]
            im[i] = 0f
        }
        fft(re, im)
        for (band in 0 until BANDS) {
            val from = edges[band]
            val to = max(from + 1, edges[band + 1])
            var peak = 0f
            for (k in from until to) {
                val m = sqrt(re[k] * re[k] + im[k] * im[k])
                if (m > peak) peak = m
            }
            // Loudness in decibels, -60 dB = empty, 0 dB = full. Highs are lifted a little, since music has less up there.
            val db = 20f * log10(peak / (SIZE / 4f) + 1e-6f) + band * 0.35f
            levels[band] = ((db + 60f) / 60f).coerceIn(0f, 1f).pow(1.4f)
        }
    }

    /** Plain radix-2 FFT, in place */
    private fun fft(
        re: FloatArray,
        im: FloatArray,
    ) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]
                re[i] = re[j]
                re[j] = t
                t = im[i]
                im[i] = im[j]
                im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f
                var ci = 0f
                for (k in 0 until len / 2) {
                    val ar = re[i + k]
                    val ai = im[i + k]
                    val br = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val bi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ar + br
                    im[i + k] = ai + bi
                    re[i + k + len / 2] = ar - br
                    im[i + k + len / 2] = ai - bi
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
