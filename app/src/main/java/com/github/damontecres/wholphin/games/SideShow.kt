package com.github.damontecres.wholphin.games

import android.content.Context
import android.view.TextureView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Something from Jellyfin that can play next to the game */
data class SideShowItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val startMs: Long,
)

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

    val available: Boolean get() = server.isNotBlank() && userId.isNotBlank() && token.isNotBlank()

    private fun get(path: String): JSONObject? =
        try {
            http
                .newCall(
                    Request
                        .Builder()
                        .url(server.trimEnd('/') + path)
                        .header("X-Emby-Token", token)
                        .build(),
                ).execute()
                .use { r -> if (r.isSuccessful) JSONObject(r.body.string()) else null }
        } catch (e: Exception) {
            Timber.i("Side show list failed: ${e.message}")
            null
        }

    private fun post(
        path: String,
        body: JSONObject,
    ) {
        Thread {
            try {
                http
                    .newCall(
                        Request
                            .Builder()
                            .url(server.trimEnd('/') + path)
                            .header("X-Emby-Token", token)
                            .post(body.toString().toRequestBody("application/json".toMediaType()))
                            .build(),
                    ).execute()
                    .close()
            } catch (e: Exception) {
                Timber.i("Side show report failed: ${e.message}")
            }
        }.start()
    }

    /** Continue watching, then next up. Call off the main thread. */
    fun choices(): List<SideShowItem> {
        val out = mutableListOf<SideShowItem>()
        val fields = "&fields=UserData&enableImages=false"
        val resume =
            get("/UserItems/Resume?userId=$userId&limit=10&mediaTypes=Video$fields")
                ?: get("/Users/$userId/Items/Resume?limit=10&mediaTypes=Video$fields")
        val next = get("/Shows/NextUp?userId=$userId&limit=10$fields")
        listOfNotNull(resume, next).forEach { page ->
            val items = page.optJSONArray("Items") ?: return@forEach
            for (i in 0 until items.length()) {
                val it = items.getJSONObject(i)
                val id = it.optString("Id")
                if (id.isBlank() || out.any { o -> o.id == id }) continue
                val series = it.optString("SeriesName")
                val name = it.optString("Name")
                val season = it.optInt("ParentIndexNumber", -1)
                val episode = it.optInt("IndexNumber", -1)
                val ticks = it.optJSONObject("UserData")?.optLong("PlaybackPositionTicks") ?: 0L
                out +=
                    SideShowItem(
                        id = id,
                        title = series.ifBlank { name },
                        subtitle =
                            if (series.isNotBlank() && season >= 0 && episode >= 0) "S$season E$episode · $name" else "",
                        startMs = ticks / 10_000,
                    )
            }
        }
        return out
    }

    /** Starts [item] on [view]. [onShape] gets the video's width / height once known. */
    fun play(
        item: SideShowItem,
        view: TextureView,
        onShape: (Float) -> Unit,
        onEnded: () -> Unit,
    ) {
        stop()
        session = UUID.randomUUID().toString().replace("-", "")
        val url =
            server.trimEnd('/') + "/Videos/${item.id}/master.m3u8?MediaSourceId=${item.id}&api_key=$token" +
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
            },
        )
        p.setMediaItem(
            MediaItem
                .Builder()
                .setUri(url)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build(),
        )
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
        val p = player ?: return
        val item = playing
        if (item != null) post("/Sessions/Playing/Stopped", report(item, p.currentPosition))
        p.release()
        player = null
        playing = null
    }
}
