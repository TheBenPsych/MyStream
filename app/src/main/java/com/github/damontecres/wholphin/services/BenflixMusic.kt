package com.github.damontecres.wholphin.services

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** One song from the music catalogue (Deezer), as the Benflix helper on Tower sends it */
data class MusicSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val cover: String?,
    val seconds: Int,
    /** The words of its request when it's already been asked for, like "Downloading, 40%" */
    val asked: String?,
)

data class MusicAlbum(
    val id: Long,
    val title: String,
    val artist: String,
    val cover: String?,
    val year: String,
    /** album, single or ep */
    val type: String,
    val songs: Int,
    val asked: String?,
)

data class MusicArtist(
    val id: Long,
    val name: String,
    val picture: String?,
)

data class MusicSearch(
    val songs: List<MusicSong>,
    val albums: List<MusicAlbum>,
    val artists: List<MusicArtist>,
)

/** One music request and how far along it is, in words Tower picks */
data class MusicRequest(
    val id: String,
    /** song or album */
    val kind: String,
    val title: String,
    val artist: String,
    val album: String,
    val cover: String?,
    val words: String,
    /** How full the thin bar is, 0 to 1 */
    val bar: Float,
    /** Red: couldn't find it, or something went wrong */
    val bad: Boolean,
    val canRetry: Boolean,
    val ready: Boolean,
)

data class MusicPlaylist(
    val id: String,
    val name: String,
    val words: String,
    val bar: Float,
    val done: Boolean,
)

data class MusicTidy(
    val running: Boolean,
    val mode: String?,
    val lines: List<String>,
    val result: String?,
    val folders: List<String>,
    val undoFiles: List<String>,
)

data class MusicStatus(
    val lidarr: Boolean,
    val problems: List<String>,
    val requests: List<MusicRequest>,
    val playlists: List<MusicPlaylist>,
    val dropFolder: String,
    val musicFolder: String,
    val dropLines: List<String>,
    val tidy: MusicTidy?,
)

/**
 * Benflix music requests (only the admin, Ben): search the music catalogue, ask for a song or album, bring in
 * playlists, and tidy Tower's Music folder. All through the Benflix helper on Tower (benflix_music.py), which
 * hands the downloading to Lidarr behind the scenes.
 */
@Singleton
class BenflixMusic
    @Inject
    constructor(
        private val helper: GameLibraryClient,
    ) {
        suspend fun status(): MusicStatus = parseStatus(helper.getJson("/api/music/status"))

        suspend fun search(text: String): MusicSearch {
            val j = helper.getJson("/api/music/search?q=" + Uri.encode(text))
            return MusicSearch(
                songs = j.optJSONArray("songs").objects().map(::song),
                albums = j.optJSONArray("albums").objects().map(::album),
                artists = j.optJSONArray("artists").objects().map(::artist),
            )
        }

        /** An artist's best songs and their albums */
        suspend fun artist(id: Long): Pair<MusicArtist, MusicSearch> {
            val j = helper.getJson("/api/music/artist?id=$id")
            return artist(j.optJSONObject("artist") ?: JSONObject()) to
                MusicSearch(
                    songs = j.optJSONArray("songs").objects().map(::song),
                    albums = j.optJSONArray("albums").objects().map(::album),
                    artists = emptyList(),
                )
        }

        /** An album and its songs */
        suspend fun album(id: Long): Pair<MusicAlbum, List<MusicSong>> {
            val j = helper.getJson("/api/music/album?id=$id")
            return album(j.optJSONObject("album") ?: JSONObject()) to j.optJSONArray("songs").objects().map(::song)
        }

        /** [kind] is "song" or "album" */
        suspend fun ask(
            kind: String,
            id: Long,
        ): MusicRequest = request(helper.postJson("/api/music/request", JSONObject().put("kind", kind).put("id", id)))

        suspend fun retry(request: MusicRequest): MusicRequest = request(helper.postJson("/api/music/request/${request.id}/retry"))

        suspend fun remove(request: MusicRequest) {
            helper.postJson("/api/music/request/${request.id}/remove")
        }

        /** A Spotify, YouTube or Deezer playlist link, or lines like "Artist - Song" */
        suspend fun importPlaylist(link: String): MusicPlaylist =
            playlist(helper.postJson("/api/music/playlist", JSONObject().put("link", link)))

        suspend fun removePlaylist(playlist: MusicPlaylist) {
            helper.postJson("/api/music/playlist/${playlist.id}/remove")
        }

        /** [mode] is "preview", "apply" or "undo" */
        suspend fun tidy(
            mode: String,
            folder: String? = null,
            undoFile: String? = null,
        ): MusicTidy? =
            tidy(
                helper.postJson(
                    "/api/music/tidy",
                    JSONObject().put("mode", mode).put("folder", folder ?: "").put("undo", undoFile ?: ""),
                ),
            )

        private fun parseStatus(j: JSONObject) =
            MusicStatus(
                lidarr = j.optBoolean("lidarr"),
                problems = j.optJSONArray("problems").strings(),
                requests = j.optJSONArray("requests").objects().map(::request),
                playlists = j.optJSONArray("playlists").objects().map(::playlist),
                dropFolder = j.optString("drop_folder"),
                musicFolder = j.optString("music_folder"),
                dropLines = j.optJSONArray("drop_lines").strings(),
                tidy = j.optJSONObject("tidy")?.let(::tidy),
            )

        private fun song(j: JSONObject) =
            MusicSong(
                id = j.optLong("id"),
                title = j.optString("title"),
                artist = j.optString("artist"),
                album = j.optString("album"),
                albumId = j.optLong("album_id"),
                cover = j.text("cover"),
                seconds = j.optInt("seconds"),
                asked = j.text("asked"),
            )

        private fun album(j: JSONObject) =
            MusicAlbum(
                id = j.optLong("id"),
                title = j.optString("title"),
                artist = j.optString("artist"),
                cover = j.text("cover"),
                year = j.optString("year"),
                type = j.optString("type", "album"),
                songs = j.optInt("songs"),
                asked = j.text("asked"),
            )

        private fun artist(j: JSONObject) = MusicArtist(id = j.optLong("id"), name = j.optString("name"), picture = j.text("picture"))

        private fun request(j: JSONObject) =
            MusicRequest(
                id = j.optString("id"),
                kind = j.optString("kind", "song"),
                title = j.optString("title"),
                artist = j.optString("artist"),
                album = j.optString("album"),
                cover = j.text("cover"),
                words = j.optString("words"),
                bar = j.optDouble("bar", 0.0).toFloat().coerceIn(0f, 1f),
                bad = j.optBoolean("bad"),
                canRetry = j.optBoolean("can_retry"),
                ready = j.optString("state") == "ready",
            )

        private fun playlist(j: JSONObject) =
            MusicPlaylist(
                id = j.optString("id"),
                name = j.optString("name"),
                words = j.optString("words"),
                bar = j.optDouble("bar", 0.0).toFloat().coerceIn(0f, 1f),
                done = j.optBoolean("done"),
            )

        private fun tidy(j: JSONObject) =
            MusicTidy(
                running = j.optBoolean("running"),
                mode = j.text("mode"),
                lines = j.optJSONArray("lines").strings(),
                result = j.text("result"),
                folders = j.optJSONArray("folders").strings(),
                undoFiles = j.optJSONArray("undo_files").strings(),
            )
    }

private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }
