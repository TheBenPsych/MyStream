package com.github.damontecres.wholphin.games

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Something from Jellyfin that can play next to the game (or a show to pick an episode from) */
data class SideShowItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val startMs: Long,
    /** "Movie", "Episode" or "Series" */
    val type: String = "Episode",
    val seriesId: String? = null,
    val image: String? = null,
    val anime: Boolean = false,
    /** 0 to 1, how far it's been watched */
    val progress: Float = 0f,
)

/** The tabs of the show picker */
enum class ShowTab(
    val label: String,
) {
    RESUME("Continue watching"),
    NEXT("Next up"),
    MY_LIST("My List"),
    MOVIES("Movies"),
    SHOWS("Shows"),
    SEARCH("Search"),
}

/**
 * "Watch a show on the side": a small Jellyfin video playing next to the game (both in the game player).
 * Jellyfin turns it into a light 720p stream, so the TV only has to show it, not work hard on it.
 */
class SideShow(
    private val context: Context,
    private val server: String,
    private val userId: String,
    private val token: String,
) {
    private val http =
        OkHttpClient
            .Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    private var player: ExoPlayer? = null
    private var playing: SideShowItem? = null
    private var session = ""

    /** Goes up on every stop, so a show that was still getting ready doesn't start after it */
    private var generation = 0

    val available: Boolean get() = server.isNotBlank() && userId.isNotBlank() && token.isNotBlank()

    private fun get(path: String): JSONObject? =
        try {
            http
                .newCall(
                    Request
                        .Builder()
                        .url(server.trimEnd('/') + path)
                        .header("Authorization", "MediaBrowser Token=\"$token\"")
                        .build(),
                ).execute()
                .use { r -> if (r.isSuccessful) JSONObject(r.body.string()) else null }
        } catch (e: Exception) {
            Timber.i("Side show list failed: ${e.message}")
            null
        }

    private val sending = mutableListOf<Thread>()

    /** Waits (up to [ms]) for progress reports still being sent, so quitting doesn't cut them off */
    fun awaitReports(ms: Long) {
        val until = System.currentTimeMillis() + ms
        synchronized(sending) { sending.toList() }.forEach { t ->
            t.join((until - System.currentTimeMillis()).coerceAtLeast(1))
        }
    }

    private fun post(
        path: String,
        body: JSONObject,
    ) {
        lateinit var thread: Thread
        thread =
            Thread {
                try {
                    http
                        .newCall(
                            Request
                                .Builder()
                                .url(server.trimEnd('/') + path)
                                .header("Authorization", "MediaBrowser Token=\"$token\"")
                                .post(body.toString().toRequestBody("application/json".toMediaType()))
                                .build(),
                        ).execute()
                        .close()
                } catch (e: Exception) {
                    Timber.i("Side show report failed: ${e.message}")
                } finally {
                    synchronized(sending) { sending.remove(thread) }
                }
            }
        synchronized(sending) { sending.add(thread) }
        thread.start()
    }

    private val main = Handler(Looper.getMainLooper())

    private fun enc(text: String) = URLEncoder.encode(text, "UTF-8")

    private fun image(id: String) = server.trimEnd('/') + "/Items/$id/Images/Primary?maxHeight=360&quality=85&ApiKey=$token"

    private fun toItem(j: JSONObject): SideShowItem? {
        val id = j.optString("Id").ifBlank { return null }
        val type = j.optString("Type")
        val series = j.optString("SeriesName")
        val name = j.optString("Name")
        val season = j.optInt("ParentIndexNumber", -1)
        val episode = j.optInt("IndexNumber", -1)
        val data = j.optJSONObject("UserData")
        val ticks = data?.optLong("PlaybackPositionTicks") ?: 0L
        val runtime = j.optLong("RunTimeTicks")
        val seriesId = j.optString("SeriesId").ifBlank { null }
        val genres = j.optJSONArray("Genres")
        val anime =
            j.optString("Path").contains("anime", ignoreCase = true) ||
                (0 until (genres?.length() ?: 0)).any { genres!!.optString(it).equals("Anime", ignoreCase = true) }
        val year = j.optInt("ProductionYear", 0)
        return SideShowItem(
            id = id,
            title = if (type == "Episode") series.ifBlank { name } else name,
            subtitle =
                when {
                    type == "Episode" && season >= 0 && episode >= 0 -> "S$season E$episode · $name"
                    type == "Episode" -> name
                    year > 0 -> year.toString()
                    else -> ""
                },
            startMs = ticks / 10_000,
            type = type,
            seriesId = seriesId,
            image = image(if (type == "Episode" && seriesId != null) seriesId else id),
            anime = anime,
            progress = if (runtime > 0) (ticks.toFloat() / runtime).coerceIn(0f, 1f) else 0f,
        )
    }

    private fun items(path: String): List<SideShowItem> {
        val page = get(path) ?: return emptyList()
        val arr = page.optJSONArray("Items") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { toItem(arr.getJSONObject(it)) }
    }

    /** One tab of the picker. Call off the main thread. */
    fun list(
        tab: ShowTab,
        query: String = "",
    ): List<SideShowItem> {
        val f = "&fields=UserData,Path,Genres,ProductionYear&enableImages=false"
        val u = "/Users/$userId/Items?Recursive=true$f"
        return when (tab) {
            ShowTab.RESUME -> {
                items("/UserItems/Resume?userId=$userId&limit=30&mediaTypes=Video$f")
                    .ifEmpty { items("/Users/$userId/Items/Resume?limit=30&mediaTypes=Video$f") }
            }

            ShowTab.NEXT -> {
                items("/Shows/NextUp?userId=$userId&limit=30$f")
            }

            ShowTab.MY_LIST -> {
                items("$u&Filters=IsFavorite&IncludeItemTypes=Movie,Series&SortBy=DateCreated&SortOrder=Descending&Limit=60")
            }

            ShowTab.MOVIES -> {
                items("$u&IncludeItemTypes=Movie&SortBy=DateCreated&SortOrder=Descending&Limit=60")
            }

            ShowTab.SHOWS -> {
                items("$u&IncludeItemTypes=Series&SortBy=DateLastContentAdded&SortOrder=Descending&Limit=60")
            }

            ShowTab.SEARCH -> {
                if (query.isBlank()) {
                    emptyList()
                } else {
                    items("$u&searchTerm=${enc(query)}&IncludeItemTypes=Movie,Series,Episode&Limit=40")
                }
            }
        }
    }

    /** A show's seasons: (id, name). Call off the main thread. */
    fun seasons(seriesId: String): List<Pair<String, String>> {
        val arr = get("/Shows/$seriesId/Seasons?userId=$userId")?.optJSONArray("Items") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val j = arr.getJSONObject(i)
            j.optString("Id").ifBlank { null }?.let { it to j.optString("Name", "Season") }
        }
    }

    /** A season's episodes. Call off the main thread. */
    fun episodes(
        seriesId: String,
        seasonId: String,
    ): List<SideShowItem> = items("/Shows/$seriesId/Episodes?userId=$userId&seasonId=$seasonId&fields=UserData,Path,Genres")

    /** The episode after [item], if there is one. Call off the main thread. */
    fun nextEpisode(item: SideShowItem): SideShowItem? {
        val series = item.seriesId ?: return null
        if (item.type != "Episode") return null
        return items("/Shows/$series/Episodes?userId=$userId&startItemId=${item.id}&limit=2&fields=UserData,Path,Genres")
            .getOrNull(1)
            ?.copy(startMs = 0)
    }

    // Subtitles and intro skipping for the show that's on
    private var subtitleUrl: String? = null
    private var subbed = false
    private var muted = false
    private var introStart = -1L
    private var introEnd = -1L

    /** Shown under the small show (the game player draws them) */
    var onCues: (List<Cue>) -> Unit = {}

    /**
     * Subtitles: on when the show's sound is off. Anime with Japanese sound (subbed) always has them;
     * dubbed anime only when muted, like everything else.
     */
    fun setMuted(value: Boolean) {
        muted = value
        applySubtitles()
    }

    private fun applySubtitles() {
        val p = player ?: return
        val on = subtitleUrl != null && (subbed || muted)
        p.trackSelectionParameters =
            p.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !on)
                .setPreferredTextLanguage("en")
                .build()
        if (!on) onCues(emptyList())
    }

    /** Skips the intro when the show reaches it. Call about once a second. */
    fun tick() {
        val p = player ?: return
        if (introEnd <= 0) return
        val pos = p.currentPosition
        if (pos >= introStart && pos < introEnd - 1500) p.seekTo(introEnd)
    }

    /** Looks up the show's sound language, subtitles and intro, then starts it. */
    fun play(
        item: SideShowItem,
        view: TextureView,
        onShape: (Float) -> Unit,
        onEnded: () -> Unit,
    ) {
        stop()
        val mine = ++generation
        Thread {
            var sub: String? = null
            var isSubbed = false
            var iStart = -1L
            var iEnd = -1L
            get("/Users/$userId/Items/${item.id}?fields=MediaStreams")?.optJSONArray("MediaStreams")?.let { streams ->
                val all = (0 until streams.length()).map { streams.getJSONObject(it) }
                val audio = all.filter { it.optString("Type") == "Audio" }
                val mainAudio = audio.firstOrNull { it.optBoolean("IsDefault") } ?: audio.firstOrNull()
                val lang = mainAudio?.optString("Language").orEmpty().lowercase()
                isSubbed = item.anime && lang.isNotBlank() && lang !in setOf("en", "eng", "english")
                val texts = all.filter { it.optString("Type") == "Subtitle" && it.optBoolean("IsTextSubtitleStream") }
                val pick =
                    texts.firstOrNull { it.optString("Language").lowercase() in setOf("en", "eng") && !it.optBoolean("IsForced") }
                        ?: texts.firstOrNull { !it.optBoolean("IsForced") }
                        ?: texts.firstOrNull()
                pick?.let {
                    sub = server.trimEnd('/') + "/Videos/${item.id}/${item.id}/Subtitles/${it.optInt("Index")}/0/Stream.vtt?ApiKey=$token"
                }
            }
            get("/MediaSegments/${item.id}?includeSegmentTypes=Intro")?.optJSONArray("Items")?.let { segs ->
                if (segs.length() > 0) {
                    val seg = segs.getJSONObject(0)
                    iStart = seg.optLong("StartTicks") / 10_000
                    iEnd = seg.optLong("EndTicks") / 10_000
                }
            }
            main.post {
                if (mine != generation) return@post
                subtitleUrl = sub
                subbed = isSubbed
                introStart = iStart
                introEnd = iEnd
                start(item, view, onShape, onEnded)
            }
        }.start()
    }

    private fun start(
        item: SideShowItem,
        view: TextureView,
        onShape: (Float) -> Unit,
        onEnded: () -> Unit,
    ) {
        session = UUID.randomUUID().toString().replace("-", "")
        val url =
            server.trimEnd('/') + "/Videos/${item.id}/master.m3u8?MediaSourceId=${item.id}&ApiKey=$token" +
                "&PlaySessionId=$session&DeviceId=benflix-side-show&VideoCodec=h264&AudioCodec=aac" +
                "&MaxWidth=1280&MaxHeight=720&VideoBitrate=3000000&AudioBitrate=160000&TranscodingMaxAudioChannels=2" +
                "&SegmentContainer=ts&MinSegments=1&BreakOnNonKeyFrames=true"
        val p = ExoPlayer.Builder(context).build()
        p.setVideoTextureView(view)
        p.addListener(
            object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        onShape(videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) onEnded()
                }

                override fun onCues(cueGroup: CueGroup) {
                    onCues(cueGroup.cues)
                }
            },
        )
        val media =
            MediaItem
                .Builder()
                .setUri(url)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
        subtitleUrl?.let {
            media.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration
                        .Builder(Uri.parse(it))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage("en")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build(),
                ),
            )
        }
        p.setMediaItem(media.build())
        p.setAudioAttributes(
            androidx.media3.common.AudioAttributes
                .Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            false,
        )
        if (item.startMs > 0) p.seekTo(item.startMs)
        p.prepare()
        p.playWhenReady = true
        player = p
        playing = item
        applySubtitles()
        post("/Sessions/Playing", report(item, item.startMs))
    }

    private fun report(
        item: SideShowItem,
        positionMs: Long,
    ) = JSONObject()
        .put("ItemId", item.id)
        .put("MediaSourceId", item.id)
        .put("PlaySessionId", session)
        .put("PositionTicks", positionMs * 10_000)
        .put("CanSeek", true)
        .put("PlayMethod", "Transcode")

    /** Tells Jellyfin how far you got, so Continue watching stays right. Call every so often. */
    fun reportProgress() {
        val p = player ?: return
        val item = playing ?: return
        post("/Sessions/Playing/Progress", report(item, p.currentPosition).put("IsPaused", !p.isPlaying))
    }

    var volume: Float
        get() = player?.volume ?: 1f
        set(value) {
            player?.volume = value
        }

    val isOn: Boolean get() = player != null

    fun pause() {
        player?.pause()
    }

    fun resume() {
        player?.play()
    }

    fun stop() {
        generation++
        val p = player ?: return
        val item = playing
        if (item != null) post("/Sessions/Playing/Stopped", report(item, p.currentPosition))
        p.release()
        player = null
        playing = null
        onCues(emptyList())
    }
}
