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
    /** Already in the Music folder */
    val have: Boolean = false,
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
    /** Already in the Music folder (artist pages' Discography) */
    val have: Boolean = false,
)

/** An artist in the music catalogue, for the artist page (Music redesign 3) */
data class MusicArtistFind(
    val artist: MusicArtist,
    /** Their top 5 songs worldwide */
    val popular: List<MusicSong>,
    /** Every album and single, newest first */
    val albums: List<MusicAlbum>,
    /** Fans also like */
    val related: List<MusicArtist>,
)

data class MusicArtist(
    val id: Long,
    val name: String,
    val picture: String?,
)

data class MusicGenre(
    val id: Long,
    val name: String,
    val picture: String?,
)

/** What's popular now: for everything, or for one genre */
data class MusicDiscover(
    val songs: List<MusicSong>,
    val newReleases: List<MusicAlbum>,
    val albums: List<MusicAlbum>,
    val artists: List<MusicArtist>,
    val genres: List<MusicGenre>,
    /** Albums the catalogue's editors are picking right now */
    val picks: List<MusicAlbum> = emptyList(),
    /** "Because you have <song>": songs like songs already in your music (new picks every time the page opens) */
    val because: List<MusicBecause> = emptyList(),
    /** "Trending in <genre>": each genre's top songs, none of them in the Top 100 */
    val trending: List<MusicTrending> = emptyList(),
)

data class MusicBecause(
    val artist: String,
    val songs: List<MusicSong>,
    /** The song in your music this list starts from (blank from an older Tower: then it's the artist) */
    val song: String = "",
)

data class MusicTrending(
    val genreId: Long,
    val genre: String,
    val songs: List<MusicSong>,
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
    /** Redesign 2: asked, getting, looking, cant or ready (blank from older Tower helpers) */
    val pill: String = "",
    val pct: Int = 0,
    /** Who asked (Ben sees everyone's) */
    val who: String = "",
    /** A family profile's ask, waiting for Ben's Approve */
    val waiting: Boolean = false,
    /** Days since it was asked for (or approved) */
    val days: Int = 0,
    /** Why it couldn't be found, like "Lidarr's search sites had no copies" (blank from older Tower helpers) */
    val why: String = "",
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
    /** Tower answered for a family profile (no playlists, Drop folder or tidy-up) */
    val family: Boolean = false,
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

        /** How many songs the whole album has in the music catalogue (0 when unknown). Anyone but Kids. */
        suspend fun albumSize(
            artist: String,
            album: String,
        ): Int = helper.getJson("/api/music/albumsize?artist=" + Uri.encode(artist) + "&album=" + Uri.encode(album)).optInt("songs")

        /** The whole album in the music catalogue: how many songs it has and its id (for Ask for the rest) */
        suspend fun albumFind(
            artist: String,
            album: String,
        ): Pair<Int, Long?> {
            val j = helper.getJson("/api/music/albumsize?artist=" + Uri.encode(artist) + "&album=" + Uri.encode(album))
            return j.optInt("songs") to j.optLong("id").takeIf { it > 0 }
        }

        /** Not for this playlist: the song leaves this made-for-you or themed playlist for this profile */
        suspend fun hideFromMix(
            key: String,
            songId: String,
        ) {
            helper.postJson("/api/music/mixes/hide", JSONObject().put("key", key).put("id", songId))
        }

        suspend fun search(text: String): MusicSearch {
            val j = helper.getJson("/api/music/search?q=" + Uri.encode(text))
            return MusicSearch(
                songs = j.optJSONArray("songs").objects().map(::song),
                albums = j.optJSONArray("albums").objects().map(::album),
                artists = j.optJSONArray("artists").objects().map(::artist),
            )
        }

        /** Top 100 songs, new releases, top albums and artists, and the genres */
        suspend fun discover(): MusicDiscover = discover(helper.getJson("/api/music/discover"))

        /** The same for one genre */
        suspend fun genre(id: Long): Pair<MusicGenre, MusicDiscover> {
            val j = helper.getJson("/api/music/genre?id=$id")
            return genre(j.optJSONObject("genre") ?: JSONObject()) to discover(j)
        }

        private fun discover(j: JSONObject) =
            MusicDiscover(
                songs = j.optJSONArray("songs").objects().map(::song),
                newReleases = j.optJSONArray("new").objects().map(::album),
                albums = j.optJSONArray("albums").objects().map(::album),
                artists = j.optJSONArray("artists").objects().map(::artist),
                genres = j.optJSONArray("genres").objects().map(::genre),
                picks = j.optJSONArray("picks").objects().map(::album),
                because =
                    j.optJSONArray("because").objects().map { b ->
                        MusicBecause(
                            artist = b.optString("artist"),
                            songs = b.optJSONArray("songs").objects().map(::song),
                            song = b.optString("song"),
                        )
                    },
                trending =
                    j.optJSONArray("trending").objects().map { t ->
                        MusicTrending(
                            genreId = t.optLong("id"),
                            genre = t.optString("genre"),
                            songs = t.optJSONArray("songs").objects().map(::song),
                        )
                    },
            )

        /** A few songs like this one (from your music) to ask for, for Now playing. Songs already in Music are left out. */
        suspend fun similar(
            artist: String,
            title: String,
        ): List<MusicSong> {
            val j = helper.getJson("/api/music/similar?artist=${Uri.encode(artist)}&title=${Uri.encode(title)}")
            return j.optJSONArray("songs").objects().map(::song)
        }

        private fun genre(j: JSONObject) = MusicGenre(id = j.optLong("id"), name = j.optString("name"), picture = j.text("picture"))

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

        /** The artist page's catalogue parts: Popular worldwide, every album, Fans also like (null when not found) */
        suspend fun artistFind(name: String): MusicArtistFind? {
            val j = helper.getJson("/api/music/artistfind?name=" + Uri.encode(name))
            val a = j.optJSONObject("artist") ?: return null
            return MusicArtistFind(
                artist = artist(a),
                popular = j.optJSONArray("songs").objects().map(::song),
                albums = j.optJSONArray("albums").objects().map(::album),
                related = j.optJSONArray("related").objects().map(::artist),
            )
        }

        /** Songs on Benflix whose words have these words: (artist, title, the line that matched) */
        suspend fun lyricsSearch(words: String): List<Triple<String, String, String>> =
            helper
                .getJson("/api/music/lyricsearch?q=" + Uri.encode(words))
                .optJSONArray("songs")
                .objects()
                .map { Triple(it.optString("artist"), it.optString("title"), it.optString("line")) }

        /** An album and its songs */
        suspend fun album(id: Long): Pair<MusicAlbum, List<MusicSong>> {
            val j = helper.getJson("/api/music/album?id=$id")
            return album(j.optJSONObject("album") ?: JSONObject()) to j.optJSONArray("songs").objects().map(::song)
        }

        /** [kind] is "song" or "album". [theme] = asked from a themed playlist's Songs to add: it joins that playlist */
        suspend fun ask(
            kind: String,
            id: Long,
            theme: String? = null,
        ): MusicRequest =
            request(
                helper.postJson(
                    "/api/music/request",
                    JSONObject().put("kind", kind).put("id", id).apply { if (theme != null) put("theme", theme) },
                ),
            )

        /** Songs to add for one themed playlist (Ben's profile): catalogue songs that fit and aren't in Music yet */
        suspend fun themeAdds(key: String): List<MusicSong> =
            helper
                .getJson("/api/music/theme?key=${Uri.encode(key)}")
                .optJSONArray("songs")
                .objects()
                .map(::song)

        /**
         * Ben's "Delete and find again" on a song: Tower deletes the file, makes sure the same copy is never taken
         * again, and asks for the song again. Returns Tower's words, like "Deleted Song. Tower is finding a new copy."
         */
        suspend fun deleteAndFindAgain(itemId: java.util.UUID): String =
            helper.postJson("/api/music/redo", JSONObject().put("id", itemId.toString())).optString("words").ifBlank {
                "Deleted. Tower is finding a new copy."
            }

        /**
         * Ben's "Delete song" (a song he just doesn't want): Tower deletes the file, Jellyfin drops it, and Lidarr
         * stops watching its album so it never comes back. Returns Tower's words.
         */
        suspend fun deleteSong(itemId: java.util.UUID): String =
            helper.postJson("/api/music/delete", JSONObject().put("id", itemId.toString())).optString("words").ifBlank {
                "Deleted. It won't be downloaded again."
            }

        suspend fun retry(request: MusicRequest): MusicRequest = request(helper.postJson("/api/music/request/${request.id}/retry"))

        suspend fun remove(request: MusicRequest) {
            helper.postJson("/api/music/request/${request.id}/remove")
        }

        /** A Spotify, YouTube or Deezer playlist link, or lines like "Artist - Song" */
        suspend fun importPlaylist(
            link: String,
            makePlaylist: Boolean = true,
        ): MusicPlaylist = playlist(helper.postJson("/api/music/playlist", JSONObject().put("link", link).put("playlist", makePlaylist)))

        /** Ben's answer to a family profile's music ask: yes starts the download, no takes it off */
        suspend fun answer(
            request: MusicRequest,
            yes: Boolean,
        ) {
            helper.postJson("/api/music/request/${request.id}/${if (yes) "approve" else "decline"}")
        }

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
                family = j.optBoolean("family"),
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
                have = j.optBoolean("have"),
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
                have = j.optBoolean("have"),
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
                pill = j.optString("pill"),
                pct = j.optInt("pct"),
                who = j.optString("who"),
                waiting = j.optBoolean("waiting"),
                days = j.optInt("days"),
                why = j.optString("why"),
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
