package com.github.damontecres.wholphin.ui.games.ember

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import timber.log.Timber
import kotlin.math.PI
import kotlin.math.sin

/**
 * Ember Road's chiptune music, made by code (old console style: two square waves, a triangle
 * bass and a noise drum). Each tune is worked out from a few notes, so there are no sound files.
 */
object EmberSynth {
    const val RATE = 22050

    data class Tune(
        val bpm: Int,
        /** Scale steps from the root note */
        val scale: IntArray,
        /** Chord roots, as scale degrees, one per bar */
        val chords: IntArray,
        val root: Int,
        /** 0 = none, 1 = soft, 2 = full drums */
        val drums: Int,
        val seed: Int,
        /** Square wave width: 0.125 thin, 0.5 round */
        val duty: Float = 0.25f,
    )

    private val MAJOR = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    private val MINOR = intArrayOf(0, 2, 3, 5, 7, 8, 10)
    private val DORIAN = intArrayOf(0, 2, 3, 5, 7, 9, 10)
    private val LYDIAN = intArrayOf(0, 2, 4, 6, 7, 9, 11)

    /** Music numbers from the story: 0 home, 1 open road, 2 danger, 3 strange, 4 the peak, 5 town, 6 sky */
    fun tune(id: String): Tune =
        when (id) {
            "0" -> Tune(112, MAJOR, intArrayOf(0, 3, 4, 0), 60, 1, 11)
            "1" -> Tune(124, MAJOR, intArrayOf(0, 5, 3, 4), 62, 2, 23)
            "2" -> Tune(136, MINOR, intArrayOf(0, 5, 6, 4), 57, 2, 37, 0.125f)
            "3" -> Tune(96, DORIAN, intArrayOf(0, 3, 0, 6), 62, 1, 41, 0.5f)
            "4" -> Tune(148, MINOR, intArrayOf(0, 6, 5, 4), 52, 2, 53, 0.125f)
            "5" -> Tune(118, MAJOR, intArrayOf(0, 4, 5, 3), 65, 2, 67)
            "6" -> Tune(104, LYDIAN, intArrayOf(0, 1, 0, 4), 67, 1, 71, 0.5f)
            "camp" -> Tune(76, MAJOR, intArrayOf(0, 5, 3, 4), 57, 0, 83, 0.5f)
            "title" -> Tune(108, MINOR, intArrayOf(0, 5, 2, 6), 57, 1, 97)
            "end" -> Tune(92, MAJOR, intArrayOf(0, 3, 5, 4), 60, 1, 101, 0.5f)
            else -> Tune(70, MINOR, intArrayOf(0, 5, 3, 4), 50, 0, 113, 0.5f)
        }

    private fun freq(midi: Int) = 440.0 * Math.pow(2.0, (midi - 69) / 12.0)

    private fun note(
        t: Tune,
        degree: Int,
        octave: Int,
    ): Int {
        val n = t.scale.size
        val d = ((degree % n) + n) % n
        val o = Math.floorDiv(degree, n)
        return t.root + t.scale[d] + (o + octave) * 12
    }

    /** Renders 4 bars (one loop) of the tune as 16-bit mono samples */
    fun render(t: Tune): ShortArray {
        val stepLen = RATE * 60 / t.bpm / 4 // sixteenth notes
        val bars = t.chords.size * 2
        val steps = bars * 16
        val out = FloatArray(steps * stepLen)
        var rng = t.seed * 7919 + 17

        fun rand(n: Int): Int {
            rng = rng * 1103515245 + 12345
            return ((rng ushr 16) and 0x7FFF) % n
        }

        // A melody made of short phrases: walk up and down the scale, landing on chord notes
        val melody = IntArray(steps) { -999 }
        var deg = 4
        for (bar in 0 until bars) {
            val chord = t.chords[bar % t.chords.size]
            val repeatOf = if (bar % 2 == 1 && rand(3) > 0) bar - 1 else -1
            for (s in 0 until 16) {
                val i = bar * 16 + s
                if (repeatOf >= 0 && s < 12) {
                    melody[i] = melody[repeatOf * 16 + s]
                    continue
                }
                val strong = s % 4 == 0
                val play = strong || rand(if (t.bpm < 90) 5 else 3) == 0
                if (!play) continue
                deg += listOf(-2, -1, -1, 0, 1, 1, 2)[rand(7)]
                if (strong) {
                    // nudge to a chord note on the beat
                    val chordNotes = listOf(chord, chord + 2, chord + 4)
                    deg = chordNotes.minByOrNull { kotlin.math.abs(it - deg) } ?: deg
                }
                deg = deg.coerceIn(0, 11)
                melody[i] = deg
            }
        }

        var phase1 = 0.0
        var phase2 = 0.0
        var phase3 = 0.0
        var noise = 1
        for (step in 0 until steps) {
            val bar = step / 16
            val s = step % 16
            val chord = t.chords[bar % t.chords.size]
            val mel = melody[step]
            // how long the melody note rings: until the next note
            val melFreq = if (mel > -999) freq(note(t, mel, 1)) else 0.0
            val arp = intArrayOf(chord, chord + 2, chord + 4, chord + 2)[s % 4]
            val arpFreq = freq(note(t, arp, 0))
            val bassFreq = freq(note(t, chord, -1))
            val kick = t.drums > 0 && (s == 0 || s == 8 || (t.drums > 1 && s == 10))
            val hat = t.drums > 1 && s % 2 == 0
            val snare = t.drums > 1 && (s == 4 || s == 12)
            for (k in 0 until stepLen) {
                val i = step * stepLen + k
                val env = 1f - k.toFloat() / stepLen
                var v = 0f
                if (melFreq > 0) {
                    phase1 += melFreq / RATE
                    val sq = if ((phase1 % 1.0) < t.duty) 1f else -1f
                    v += sq * 0.16f * (0.4f + 0.6f * env)
                }
                phase2 += arpFreq / RATE
                v += (if ((phase2 % 1.0) < 0.5) 1f else -1f) * 0.05f * env
                phase3 += bassFreq / RATE
                val tri = (2.0 * kotlin.math.abs(2.0 * (phase3 % 1.0) - 1.0) - 1.0).toFloat()
                v += tri * 0.22f
                if (kick && k < stepLen / 2) {
                    val kf = 120.0 * (1.0 - k.toDouble() / stepLen)
                    v += sin(2 * PI * kf * k / RATE).toFloat() * 0.3f * (1f - 2f * k / stepLen)
                }
                if ((hat && k < stepLen / 6) || (snare && k < stepLen / 2)) {
                    noise = noise xor (noise shl 13)
                    noise = noise xor (noise ushr 17)
                    noise = noise xor (noise shl 5)
                    val n = (noise and 0xFFFF) / 32768f - 1f
                    v += n * (if (snare) 0.12f * (1f - 2f * k / stepLen) else 0.04f)
                }
                out[i] = v
            }
        }
        return ShortArray(out.size) { (out[it].coerceIn(-1f, 1f) * 32767 * 0.7f).toInt().toShort() }
    }

    /** A short "blip" for the typewriter text and buttons */
    fun blip(pitch: Int = 76): ShortArray {
        val len = RATE / 60
        val f = freq(pitch)
        return ShortArray(len) { i ->
            val sq = if ((i * f / RATE) % 1.0 < 0.5) 1f else -1f
            (sq * 0.12f * (1f - i.toFloat() / len) * 32767).toInt().toShort()
        }
    }
}

/** Plays the music on the TV or phone. Call [play] with a tune, [stop] when leaving. */
class EmberMusic {
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    @Volatile private var current: String? = null

    @Volatile private var wanted: String? = null

    @Volatile private var running = false
    private val cache = HashMap<String, ShortArray>()

    var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                wanted = null
            }
        }

    fun play(tune: String) {
        if (!enabled) return
        wanted = tune
        if (!running) start()
    }

    private fun start() {
        try {
            val min = AudioTrack.getMinBufferSize(EmberSynth.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val t =
                AudioTrack
                    .Builder()
                    .setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    ).setAudioFormat(
                        AudioFormat
                            .Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(EmberSynth.RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    ).setBufferSizeInBytes(maxOf(min * 2, EmberSynth.RATE / 4))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            track = t
            t.setVolume(0.6f)
            t.play()
            running = true
            thread =
                Thread {
                    val chunk = 1024
                    var pos = 0
                    var fade = 0f
                    while (running) {
                        val w = wanted
                        if (w != current) {
                            // fade out the old tune, then switch
                            if (fade > 0f && current != null) {
                                fade -= 0.08f
                            } else {
                                current = w
                                pos = 0
                            }
                        } else if (fade < 1f) {
                            fade = (fade + 0.04f).coerceAtMost(1f)
                        }
                        val cur = current
                        val buf = ShortArray(chunk)
                        if (cur != null) {
                            val loop = cache.getOrPut(cur) { EmberSynth.render(EmberSynth.tune(cur)) }
                            for (i in 0 until chunk) {
                                buf[i] = (loop[pos] * fade).toInt().toShort()
                                pos = (pos + 1) % loop.size
                            }
                        }
                        try {
                            t.write(buf, 0, chunk)
                        } catch (ex: Exception) {
                            Timber.w(ex, "Ember music write failed")
                            break
                        }
                    }
                }.apply {
                    name = "ember-music"
                    isDaemon = true
                    start()
                }
        } catch (ex: Exception) {
            Timber.w(ex, "Ember music couldn't start")
            running = false
        }
    }

    /** Quiet (when the game is paused or music turned off), keeps the player ready */
    fun quiet() {
        wanted = null
    }

    fun stop() {
        running = false
        wanted = null
        try {
            thread?.join(300)
        } catch (_: InterruptedException) {
        }
        thread = null
        try {
            track?.pause()
            track?.flush()
            track?.release()
        } catch (ex: Exception) {
            Timber.w(ex, "Ember music stop")
        }
        track = null
        current = null
    }
}
