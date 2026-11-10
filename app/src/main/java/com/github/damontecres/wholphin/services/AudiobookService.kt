package com.github.damontecres.wholphin.services

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.github.damontecres.wholphin.services.hilt.DefaultCoroutineScope
import com.github.damontecres.wholphin.ui.onMain
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/** The audiobook that's playing, for the player page, the top bar pill and the phone's mini bar */
data class AudiobookNow(
    val id: String,
    val name: String,
    val author: String,
    val narrator: String,
    val cover: String?,
    val chapters: List<BookChapter>,
    /** Where each file starts in the whole book, in seconds */
    val fileStarts: List<Double>,
    val total: Double,
    val playing: Boolean = false,
    val loading: Boolean = true,
    /** Seconds into the whole book */
    val position: Double = 0.0,
    val chapter: Int = 0,
    val speed: Float = 1f,
    /** When the sleep timer ends (clock time), or null */
    val sleepAt: Long? = null,
    val sleepEndOfChapter: Boolean = false,
    /** The sound is fading out for the sleep timer: a shake (phones) adds 15 minutes */
    val fading: Boolean = false,
    val problem: String? = null,
) {
    val chapterTitle: String get() = chapters.getOrNull(chapter)?.title.orEmpty()
    val chapterStart: Double get() = chapters.getOrNull(chapter)?.at ?: 0.0
    val chapterLength: Double get() = chapters.getOrNull(chapter)?.seconds?.toDouble() ?: total
}

/**
 * Benflix Books: plays audiobooks (approved design). Files come straight from Tower's helper; your place is
 * saved every 15 seconds (per profile, so it follows you to the TV, the phone and the browser). Back 15 s,
 * ahead 30 s, speed 0.75 to 3x, a sleep timer (15/30/45/60 minutes or end of chapter, then a slow fade; shake
 * the phone for 15 more), lock screen controls. Starting a movie, a show or music stops it (place saved).
 */
@OptIn(UnstableApi::class)
@Singleton
class AudiobookService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:DefaultCoroutineScope private val scope: CoroutineScope,
        private val books: BenflixBooks,
        private val musicService: dagger.Lazy<MusicService>,
    ) {
        private val _now = MutableStateFlow<AudiobookNow?>(null)
        val now: StateFlow<AudiobookNow?> = _now

        private var player: ExoPlayer? = null
        var mediaSession: MediaSession? = null
            private set
        private var ticker: Job? = null
        private var lastSaveAt = 0L
        private var listenedSinceSave = 0.0
        private var shake: SensorEventListener? = null

        init {
            current = this
        }

        private fun newPlayer(): ExoPlayer =
            ExoPlayer
                .Builder(context)
                .setSeekBackIncrementMs(BACK_MS)
                .setSeekForwardIncrementMs(AHEAD_MS)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .build(),
                    true,
                ).setHandleAudioBecomingNoisy(true)
                .build()
                .also { p ->
                    p.addListener(
                        object : Player.Listener {
                            override fun onIsPlayingChanged(isPlaying: Boolean) {
                                _now.update { it?.copy(playing = isPlaying, loading = false) }
                                if (!isPlaying) scope.launch { save() }
                            }

                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_ENDED) {
                                    scope.launch {
                                        save(finished = true)
                                        stop()
                                    }
                                }
                            }

                            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                Timber.w(error, "Audiobook couldn't play")
                                _now.update { it?.copy(problem = "Couldn't play this part of the book. Tower may be busy; try again.", loading = false) }
                            }
                        },
                    )
                }

        /** Starts [page] (an audiobook) where you left off, or at chapter [chapter] */
        suspend fun play(
            page: BookPage,
            chapter: Int? = null,
        ) {
            if (_now.value?.id == page.tile.id && chapter == null) {
                onMain { player?.play() }
                return
            }
            // One thing plays at a time: music stops (Back on the player keeps the audiobook going)
            try {
                musicService.get().stop()
            } catch (ex: Exception) {
                Timber.w(ex, "Couldn't stop music")
            }
            stop()
            val files = page.fileSeconds.ifEmpty { listOf(page.seconds.toDouble()) }
            val starts = files.runningFold(0.0) { a, b -> a + b }.dropLast(1)
            val now =
                AudiobookNow(
                    id = page.tile.id,
                    name = page.tile.name,
                    author = page.tile.author,
                    narrator = page.narrator,
                    cover = page.tile.cover,
                    chapters = page.chapters,
                    fileStarts = starts,
                    total = page.seconds.toDouble(),
                    speed = speedFor(page.tile.id),
                )
            _now.value = now
            // Where to start: a picked chapter, else your place (file + spot in it), else the start
            val (file, cpos) =
                if (chapter != null) {
                    val c = page.chapters.getOrNull(chapter)
                    (c?.file ?: 0) to (c?.start ?: 0.0)
                } else if (page.placePos > 0) {
                    val f = page.placeFile.coerceIn(0, (files.size - 1).coerceAtLeast(0))
                    // A few seconds back, so you hear where you were
                    f to (if (page.placeCpos > 0) page.placeCpos else page.placePos - (starts.getOrNull(f) ?: 0.0)).minus(REWIND_ON_START).coerceAtLeast(0.0)
                } else {
                    0 to 0.0
                }
            onMain {
                val p = player ?: newPlayer().also { player = it }
                val items =
                    files.indices.map { i ->
                        MediaItem
                            .Builder()
                            .setUri(books.fileUrl(page.tile.id, i))
                            .setMediaId("${page.tile.id}#$i")
                            .setMediaMetadata(
                                MediaMetadata
                                    .Builder()
                                    .setTitle(page.chapters.firstOrNull { it.file == i }?.title ?: page.tile.name)
                                    .setArtist(page.tile.author)
                                    .setAlbumTitle(page.tile.name)
                                    .setArtworkUri(page.tile.cover?.let { Uri.parse(it) })
                                    .build(),
                            ).build()
                    }
                p.setMediaItems(items, file, (cpos * 1000).toLong())
                p.playbackParameters = PlaybackParameters(now.speed)
                p.volume = 1f
                p.prepare()
                p.play()
                if (mediaSession == null) {
                    mediaSession = MediaSession.Builder(context, p).setId("benflix-books").build()
                }
                BenflixAudiobookPlaybackService.start(context)
            }
            lastSaveAt = System.currentTimeMillis()
            listenedSinceSave = 0.0
            startTicker()
            try {
                books.opened(page.tile.id)
            } catch (ex: Exception) {
                Timber.d(ex, "opened")
            }
        }

        private fun startTicker() {
            ticker?.cancel()
            ticker =
                scope.launch {
                    while (isActive) {
                        delay(TICK_MS)
                        tick()
                    }
                }
        }

        private suspend fun tick() {
            val p = player ?: return
            val st = _now.value ?: return
            val (index, ms, playing) = onMain { Triple(p.currentMediaItemIndex, p.currentPosition, p.isPlaying) }
            val pos = (st.fileStarts.getOrNull(index) ?: 0.0) + ms / 1000.0
            val chapter = st.chapters.indexOfLast { it.at <= pos + 0.5 }.coerceAtLeast(0)
            _now.update { it?.copy(position = pos, chapter = chapter, playing = playing) }
            if (playing) listenedSinceSave += TICK_MS / 1000.0
            // Sleep timer: the end of the chapter, or the clock
            val sleepAt = st.sleepAt
            val endOfChapter = st.sleepEndOfChapter && chapter > (sleepChapter ?: chapter)
            if (playing && ((sleepAt != null && System.currentTimeMillis() >= sleepAt) || endOfChapter)) {
                if (!st.fading) fadeOut()
            }
            if (playing && System.currentTimeMillis() - lastSaveAt >= SAVE_MS) save()
        }

        private var sleepChapter: Int? = null
        private var fadeJob: Job? = null

        /** The sleep timer: [minutes] from now, or 0 = end of this chapter, or null = off */
        fun sleep(minutes: Int?) {
            fadeJob?.cancel()
            scope.launch { onMain { player?.volume = 1f } }
            when (minutes) {
                null -> {
                    sleepChapter = null
                    _now.update { it?.copy(sleepAt = null, sleepEndOfChapter = false, fading = false) }
                    stopShake()
                }

                0 -> {
                    sleepChapter = _now.value?.chapter
                    _now.update { it?.copy(sleepAt = null, sleepEndOfChapter = true, fading = false) }
                    startShake()
                }

                else -> {
                    sleepChapter = null
                    _now.update { it?.copy(sleepAt = System.currentTimeMillis() + minutes * 60_000L, sleepEndOfChapter = false, fading = false) }
                    startShake()
                }
            }
        }

        private fun fadeOut() {
            _now.update { it?.copy(fading = true) }
            fadeJob =
                scope.launch {
                    for (step in FADE_STEPS downTo 0) {
                        onMain { player?.volume = step / FADE_STEPS.toFloat() }
                        delay(FADE_MS / FADE_STEPS)
                    }
                    onMain {
                        player?.pause()
                        player?.volume = 1f
                    }
                    sleepChapter = null
                    _now.update { it?.copy(sleepAt = null, sleepEndOfChapter = false, fading = false) }
                    stopShake()
                    save()
                }
        }

        /** Shake the phone while the sleep timer is on: 15 more minutes (and the sound comes back up) */
        private fun startShake() {
            if (shake != null) return
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
            val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            var last = 0L
            val l =
                object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
                        val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                        val t = System.currentTimeMillis()
                        if (g > SHAKE_G && t - last > 1500) {
                            last = t
                            Timber.i("Audiobook: shaken, 15 more minutes")
                            sleep(15)
                            scope.launch { onMain { player?.play() } }
                        }
                    }

                    override fun onAccuracyChanged(
                        sensor: Sensor?,
                        accuracy: Int,
                    ) {}
                }
            sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
            shake = l
        }

        private fun stopShake() {
            val l = shake ?: return
            (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.unregisterListener(l)
            shake = null
        }

        fun playPause() {
            scope.launch {
                onMain {
                    val p = player ?: return@onMain
                    if (p.isPlaying) p.pause() else p.play()
                }
            }
        }

        fun back() {
            scope.launch { onMain { player?.seekBack() } }
        }

        fun ahead() {
            scope.launch { onMain { player?.seekForward() } }
        }

        /** Jumps to [seconds] into the whole book */
        fun seekTo(seconds: Double) {
            val st = _now.value ?: return
            val file = st.fileStarts.indexOfLast { it <= seconds }.coerceAtLeast(0)
            val inFile = seconds - (st.fileStarts.getOrNull(file) ?: 0.0)
            scope.launch { onMain { player?.seekTo(file, (inFile * 1000).toLong()) } }
            _now.update { it?.copy(position = seconds) }
        }

        fun chapter(index: Int) {
            val c = _now.value?.chapters?.getOrNull(index) ?: return
            scope.launch {
                onMain {
                    player?.seekTo(c.file, (c.start * 1000).toLong())
                    player?.play()
                }
            }
        }

        fun setSpeed(speed: Float) {
            val s = speed.coerceIn(MIN_SPEED, MAX_SPEED)
            _now.update { it?.copy(speed = s) }
            _now.value?.id?.let { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat("speed-$it", s).apply() }
            scope.launch { onMain { player?.playbackParameters = PlaybackParameters(s) } }
        }

        private fun speedFor(id: String): Float = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat("speed-$id", 1f)

        /** Saves your place on Tower (per profile) */
        suspend fun save(finished: Boolean = false) {
            val p = player ?: return
            val st = _now.value ?: return
            val (index, ms) = onMain { p.currentMediaItemIndex to p.currentPosition }
            val pos = (st.fileStarts.getOrNull(index) ?: 0.0) + ms / 1000.0
            val listened = listenedSinceSave
            listenedSinceSave = 0.0
            lastSaveAt = System.currentTimeMillis()
            val chapter = st.chapters.indexOfLast { it.at <= pos + 0.5 }.coerceAtLeast(0)
            try {
                withContext(NonCancellable) {
                    books.savePlace(
                        st.id,
                        JSONObject()
                            .put("pos", pos)
                            .put("file", index)
                            .put("cpos", ms / 1000.0)
                            .put("chapter", chapter)
                            .put("pct", if (st.total > 0) (pos / st.total).coerceIn(0.0, 1.0) else 0.0)
                            .put("seconds", listened.toInt())
                            .put("finished", finished),
                    )
                }
            } catch (ex: Exception) {
                Timber.w(ex, "Couldn't save the audiobook place")
            }
        }

        /** Stops from a button (finishes even if the page closes) */
        fun stopSoon() {
            scope.launch { stop() }
        }

        /** Stops the audiobook (saving your place first) */
        suspend fun stop() {
            if (_now.value == null && player == null) return
            ticker?.cancel()
            ticker = null
            fadeJob?.cancel()
            stopShake()
            save()
            _now.value = null
            onMain {
                BenflixAudiobookPlaybackService.stop(context)
                mediaSession?.release()
                mediaSession = null
                player?.release()
                player = null
            }
        }

        companion object {
            /** For MusicService and the video player, which stop audiobooks without a Hilt cycle */
            @Volatile
            var current: AudiobookService? = null
                private set

            /** Starting music, a movie or a show: stop the audiobook (place saved) */
            fun stopForOther() {
                val s = current ?: return
                if (s.now.value == null) return
                s.scope.launch { s.stop() }
            }

            const val MIN_SPEED = 0.75f
            const val MAX_SPEED = 3f
            val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
            private const val BACK_MS = 15_000L
            private const val AHEAD_MS = 30_000L
            private const val TICK_MS = 1_000L
            private const val SAVE_MS = 15_000L
            private const val REWIND_ON_START = 3.0
            private const val FADE_MS = 10_000L
            private const val FADE_STEPS = 20
            private const val SHAKE_G = 2.3f
            private const val PREFS = "benflix_audiobooks"
        }
    }
