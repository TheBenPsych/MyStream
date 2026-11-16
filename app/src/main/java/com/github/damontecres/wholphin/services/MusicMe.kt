package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Something you played music from: an album, playlist, mix, artist, Liked songs or All songs */
data class MusicPlace(
    /** album, playlist, mix, artist, liked, songs */
    val kind: String,
    /** The Jellyfin id (album, playlist, artist) or the mix's key; empty for Liked songs and All songs */
    val id: String,
    val name: String,
    /** When you last played it (seconds), 0 for pins */
    val at: Double = 0.0,
) {
    val key get() = "$kind:$id"
}

/** Music someone else sent you (Send to a profile): it shows in your Home news */
data class MusicSent(
    val sentId: String,
    val from: String,
    val place: MusicPlace,
)

/** A profile you can send music to */
data class MusicPerson(
    val id: String,
    val name: String,
)

data class MusicSearchWords(
    val q: String,
    val at: Double,
)

data class MusicMeState(
    val recents: List<MusicPlace> = emptyList(),
    val searches: List<MusicSearchWords> = emptyList(),
    val pins: List<MusicPlace> = emptyList(),
    /** Don't play this artist in my mixes */
    val noArtists: List<String> = emptyList(),
    /** Songs asked for from Songs to add on your own playlists: playlist id to (artist, title) */
    val adds: Map<String, List<Pair<String, String>>> = emptyMap(),
    /** Music other profiles sent you (last 2 weeks) */
    val sent: List<MusicSent> = emptyList(),
    val loaded: Boolean = false,
)

/**
 * Music redesign 3 (Ben 2026-11-16): what this profile played (Jump back in, Recently played), pinned in Your
 * library and searched for. Kept per profile on Tower so the TV, the phones and the browser show the same. While
 * Tower can't be reached it keeps working from memory, and tries Tower again next time.
 */
@Singleton
class MusicMe
    @Inject
    constructor(
        private val helper: GameLibraryClient,
        private val serverRepository: ServerRepository,
    ) {
        private val _state = MutableStateFlow(MusicMeState())
        val state: StateFlow<MusicMeState> = _state
        private var loadedFor: String? = null
        private var loadedAt = 0L

        private val me get() = serverRepository.currentUser?.id?.toString()

        /** Fresh from Tower (at most once a minute unless [force]) */
        suspend fun load(force: Boolean = false) {
            val who = me ?: return
            if (who != loadedFor) {
                _state.value = MusicMeState()
                loadedAt = 0L
            }
            if (!force && who == loadedFor && System.currentTimeMillis() - loadedAt < RELOAD_MS) return
            try {
                apply(helper.getJson("/api/music/me"))
                loadedFor = who
                loadedAt = System.currentTimeMillis()
            } catch (ex: Exception) {
                Timber.w(ex, "Couldn't get Recently played from Tower")
                loadedFor = who
                _state.update { it.copy(loaded = true) }
            }
        }

        /** You started music from [place]: it goes to the top of Recently played */
        suspend fun played(place: MusicPlace) {
            val now = System.currentTimeMillis() / 1000.0
            _state.update { st ->
                st.copy(recents = listOf(place.copy(at = now)) + st.recents.filter { it.key != place.key })
            }
            post("/api/music/me/played", placeJson(place))
        }

        suspend fun searched(q: String) {
            val words = q.trim()
            if (words.length < 2) return
            val now = System.currentTimeMillis() / 1000.0
            _state.update { st ->
                st.copy(
                    searches =
                        (listOf(MusicSearchWords(words, now)) + st.searches.filter { !it.q.equals(words, true) }).take(SEARCHES),
                )
            }
            post("/api/music/me/search", JSONObject().put("q", words))
        }

        suspend fun removeSearch(q: String) {
            _state.update { st -> st.copy(searches = st.searches.filter { !it.q.equals(q, true) }) }
            post("/api/music/me/search", JSONObject().put("q", q).put("remove", true))
        }

        suspend fun clearSearches() {
            _state.update { it.copy(searches = emptyList()) }
            post("/api/music/me/search", JSONObject().put("clear", true))
        }

        fun isPinned(place: MusicPlace): Boolean = _state.value.pins.any { it.key == place.key }

        /** Pin or unpin in Your library. Returns words to show when it can't (4 pins already). */
        suspend fun pin(
            place: MusicPlace,
            on: Boolean,
        ): String? {
            if (on && _state.value.pins.size >= PINS && !isPinned(place)) return "You can pin $PINS. Unpin one first."
            _state.update { st ->
                val rest = st.pins.filter { it.key != place.key }
                st.copy(pins = if (on) rest + place.copy(at = 0.0) else rest)
            }
            post("/api/music/me/pin", placeJson(place).put("on", on))
            return null
        }

        fun isNoArtist(name: String): Boolean = _state.value.noArtists.any { it.equals(name.trim(), true) }

        /** Don't play this artist in my mixes (Made for you leaves them out from tomorrow's mixes, or now) */
        suspend fun noArtist(
            name: String,
            on: Boolean,
        ) {
            _state.update { st ->
                val rest = st.noArtists.filterNot { it.equals(name.trim(), true) }
                st.copy(noArtists = if (on) rest + name.trim() else rest)
            }
            post("/api/music/me/noartist", JSONObject().put("name", name).put("on", on))
        }

        /** A song asked for from Songs to add on your own playlist: it joins [playlistId] once it's on Benflix */
        suspend fun addLater(
            playlistId: String,
            artist: String,
            title: String,
            remove: Boolean = false,
        ) {
            _state.update { st ->
                val rest = st.adds[playlistId].orEmpty().filterNot { it.first.equals(artist, true) && it.second.equals(title, true) }
                val now = if (remove) rest else rest + (artist to title)
                st.copy(adds = if (now.isEmpty()) st.adds - playlistId else st.adds + (playlistId to now))
            }
            post(
                "/api/music/me/add",
                JSONObject()
                    .put("playlist", playlistId)
                    .put("artist", artist)
                    .put("title", title)
                    .put("remove", remove),
            )
        }

        /** Skip learning: [songId] was skipped in its first 30 seconds (3 times and it stays out of the mixes) */
        suspend fun skipped(songId: java.util.UUID) {
            try {
                helper.postJson("/api/music/me/skip", JSONObject().put("id", songId.toString()))
            } catch (ex: Exception) {
                Timber.d(ex, "Skip learning")
            }
        }

        /** The profiles you can send music to (everyone else but Kids) */
        suspend fun people(): List<MusicPerson> =
            try {
                helper
                    .getJson(
                        "/api/music/people",
                    ).optJSONArray("people")
                    .objects()
                    .map { MusicPerson(it.optString("id"), it.optString("name")) }
            } catch (ex: Exception) {
                Timber.w(ex, "People to send music to")
                emptyList()
            }

        /** Send to a profile: [place] shows in their Home news. Returns false when Tower couldn't take it. */
        suspend fun send(
            to: MusicPerson,
            place: MusicPlace,
        ): Boolean =
            try {
                helper.postJson(
                    "/api/music/me/send",
                    placeJson(place).put("to", to.id).put("from_name", serverRepository.currentUser?.name.orEmpty()),
                )
                true
            } catch (ex: Exception) {
                Timber.w(ex, "Send to %s", to.name)
                false
            }

        private suspend fun post(
            path: String,
            body: JSONObject,
        ) {
            try {
                apply(helper.postJson(path, body))
            } catch (ex: Exception) {
                // Older Tower (or away from home): it still works on this screen until the app restarts
                Timber.w(ex, "Couldn't save to Tower: %s", path)
            }
        }

        private fun apply(j: JSONObject) {
            if (!j.has("recents") && !j.has("pins")) return
            _state.value =
                MusicMeState(
                    recents = places(j.optJSONArray("recents")),
                    searches =
                        j.optJSONArray("searches").objects().mapNotNull { s ->
                            s.optString("q").takeIf { it.isNotBlank() }?.let { MusicSearchWords(it, s.optDouble("at", 0.0)) }
                        },
                    pins = places(j.optJSONArray("pins")),
                    noArtists =
                        j.optJSONArray("noartists").let { a ->
                            if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
                        },
                    adds =
                        j
                            .optJSONObject("adds")
                            ?.let { o ->
                                o.keys().asSequence().associateWith { k ->
                                    o.optJSONArray(k).objects().map { it.optString("artist") to it.optString("title") }
                                }
                            }.orEmpty(),
                    sent =
                        j.optJSONArray("sent").objects().mapNotNull { o ->
                            val kind = o.optString("kind")
                            if (kind.isBlank()) {
                                null
                            } else {
                                MusicSent(
                                    o.optString("id_sent"),
                                    o.optString("from"),
                                    MusicPlace(kind, o.optString("id"), o.optString("name"), o.optDouble("at", 0.0)),
                                )
                            }
                        },
                    loaded = true,
                )
        }

        private fun places(arr: JSONArray?): List<MusicPlace> =
            arr.objects().mapNotNull { o ->
                val kind = o.optString("kind")
                if (kind.isBlank()) {
                    null
                } else {
                    MusicPlace(kind, o.optString("id"), o.optString("name"), o.optDouble("at", 0.0))
                }
            }

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this ==
                null
            ) {
                emptyList()
            } else {
                (0 until length()).mapNotNull { optJSONObject(it) }
            }

        private fun placeJson(place: MusicPlace) = JSONObject().put("kind", place.kind).put("id", place.id).put("name", place.name)

        companion object {
            const val PINS = 4
            private const val SEARCHES = 10
            private const val RELOAD_MS = 60_000L
        }
    }
