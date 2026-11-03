package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.api.seerr.model.Season
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.UUID
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** One title Ben looked for but couldn't find */
@Serializable
data class CantFindEntry(
    /** Same as DiscoverItem.requestKey(): "MOVIE-123" or "TV-456" (TMDB id) */
    val key: String,
    val title: String? = null,
    /** Ben's short note, like "Too old, no copies online" */
    val note: String? = null,
    /** yyyy-mm-dd */
    val date: String,
    /** Seerr user ids of the people who asked for it, so they get told */
    val askedBy: List<Int> = emptyList(),
    /** Marked by itself after [BenflixCantFind.AUTO_DAYS] days with nothing found (not by Ben's hand) */
    val auto: Boolean = false,
    /** Ben pressed Undo: keep looking, and don't mark it by itself again */
    val keepLooking: Boolean = false,
    /** Poster, so the card still shows after Seerr has dropped the request */
    val posterUrl: String? = null,
)

/** Benflix: keys of titles marked "Couldn't find it", for the Ask-button guard in SeerrApiClient and card strips */
object CantFindBlocked {
    val keys = MutableStateFlow<Set<String>>(emptySet())
}

/** What happened in Seerr / Radarr / Sonarr after marking */
enum class CantFindOutcome {
    /** Saved, taken out of Radarr/Sonarr and blocked in Seerr */
    STOPPED,

    /** Saved, but a show with some episodes already on Benflix was left in Sonarr */
    KEPT_PARTIAL,

    /** Saved, but Seerr wouldn't take it out of Radarr/Sonarr */
    SAVED_ONLY,

    FAILED,
}

/**
 * Benflix: "Couldn't find it". When an approved request can't be found anywhere (often old or rare stuff),
 * Ben marks it, and everyone sees it on their request card and on the title's page. The people who asked
 * get a pop-up once.
 *
 * Seerr has no place for this, so the list is kept in every Benflix user's Jellyfin display settings
 * (the admin can write to everyone's, like the Home notes in [BenflixAdminTools]). Each device reads its
 * own user's copy.
 *
 * The 7 days only start once the title is out plus a few weeks ([SearchClock]), so new and upcoming
 * releases are never marked by themselves.
 *
 * Ben's pick (2026-11-03): once marked, it's taken out of Radarr / Sonarr (through Seerr) so they stop searching,
 * and blocked in Seerr so nobody can ask for it again. The card stays for good. "Try again" undoes the block.
 * A show with some episodes already on Benflix is never taken out of Sonarr, since that would delete them.
 */
@Singleton
class BenflixCantFind
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val displayPreferencesService: DisplayPreferencesService,
        private val seerrService: SeerrService,
    ) {
        private val _entries = MutableStateFlow<Map<String, CantFindEntry>>(emptyMap())

        /** What shows as "Couldn't find it", by key ("MOVIE-123" / "TV-456") */
        val entries: StateFlow<Map<String, CantFindEntry>> = _entries

        /** Everything saved, including ones Ben said to keep looking for */
        private var all: List<CantFindEntry> = emptyList()

        private fun show(list: List<CantFindEntry>) {
            all = list
            _entries.value = list.filter { !it.keepLooking }.associateBy { it.key }
            CantFindBlocked.keys.value = _entries.value.keys
        }

        private val mutex = Mutex()
        private var loadedFor: UUID? = null
        private var loadedAt = 0L
        private var sharedWithEveryone = false

        private val isAdmin: Boolean get() = serverRepository.currentUserDto?.policy?.isAdministrator == true

        /** Reads the list for whoever is signed in. Cheap to call often: it only asks the server every few minutes. */
        suspend fun refresh(force: Boolean = false) =
            withContext(WholphinDispatchers.IO) {
                val userId = serverRepository.currentUser?.id ?: return@withContext
                mutex.withLock {
                    val fresh = loadedFor == userId && System.currentTimeMillis() - loadedAt < REFRESH_MS
                    if (fresh && !force) return@withLock
                    if (loadedFor != userId) {
                        show(emptyList())
                        sharedWithEveryone = false
                    }
                    try {
                        val list = read(userId)
                        show(list)
                        loadedFor = userId
                        loadedAt = System.currentTimeMillis()
                        // Benflix: people added after Ben marked something get the list too (once per app run)
                        if (isAdmin && !sharedWithEveryone && list.isNotEmpty()) {
                            sharedWithEveryone = true
                            writeToEveryone(list, onlyIfDifferent = true)
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not read the can't-find list")
                    }
                }
            }

        /** Admin: mark a title as "Couldn't find it" for everyone */
        suspend fun mark(
            key: String,
            title: String?,
            note: String?,
            askedBy: List<Int>,
            posterUrl: String? = null,
        ): CantFindOutcome {
            val saved =
                change { list ->
                    val old = list.firstOrNull { it.key == key }
                    val entry =
                        CantFindEntry(
                            key = key,
                            title = title ?: old?.title,
                            note = note?.trim()?.take(MAX_NOTE)?.takeIf { it.isNotEmpty() },
                            date = LocalDate.now().toString(),
                            askedBy = (old?.askedBy.orEmpty() + askedBy).distinct(),
                            posterUrl = posterUrl ?: old?.posterUrl,
                        )
                    (list.filter { it.key != key } + entry).takeLast(MAX_ENTRIES)
                }
            if (!saved) return CantFindOutcome.FAILED
            return stopSearching(key, title)
        }

        /** Takes it out of Radarr / Sonarr and blocks it in Seerr */
        private suspend fun stopSearching(
            key: String,
            title: String?,
        ): CantFindOutcome =
            withContext(WholphinDispatchers.IO) {
                val isMovie = key.startsWith("MOVIE")
                val tmdbId = key.substringAfter('-').toIntOrNull() ?: return@withContext CantFindOutcome.SAVED_ONLY
                try {
                    val seerr = seerrService.api
                    val raw = seerr.getRaw(if (isMovie) "/movie/$tmdbId" else "/tv/$tmdbId")?.let { Json.parseToJsonElement(it).jsonObject }
                    val media = raw?.get("mediaInfo") as? JsonObject
                    val status = (media?.get("status") as? JsonPrimitive)?.intOrNull
                    if (status == SeerrAvailability.PARTIALLY_AVAILABLE.status || status == SeerrAvailability.AVAILABLE.status) {
                        return@withContext CantFindOutcome.KEPT_PARTIAL
                    }
                    val mediaId = (media?.get("id") as? JsonPrimitive)?.contentOrNull
                    // Out of Radarr / Sonarr (nothing was downloaded, so there are no files to lose)
                    val removed = mediaId == null || seerr.sendRaw("DELETE", "/media/$mediaId/file") in 200..299
                    // Blocked in Seerr, so it can't be asked for again
                    val name = title ?: ((raw?.get("title") ?: raw?.get("name")) as? JsonPrimitive)?.contentOrNull
                    val body =
                        buildJsonObject {
                            put("tmdbId", tmdbId)
                            put("mediaType", if (isMovie) "movie" else "tv")
                            name?.let { put("title", it) }
                        }.toString()
                    val blocked = seerr.sendRaw("POST", "/blacklist", body) in 200..299
                    Timber.i("Couldn't find %s: removed=%s blocked=%s", key, removed, blocked)
                    if (removed && blocked) CantFindOutcome.STOPPED else CantFindOutcome.SAVED_ONLY
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not stop the search for %s", key)
                    CantFindOutcome.SAVED_ONLY
                }
            }

        /** Admin: take the label off again. It keeps searching and won't be marked by itself again. */
        suspend fun undo(key: String): Boolean {
            val saved =
                change { list ->
                    val old = list.firstOrNull { it.key == key }
                    val kept =
                        old?.copy(keepLooking = true)
                            ?: CantFindEntry(key = key, date = LocalDate.now().toString(), keepLooking = true)
                    (list.filter { it.key != key } + kept).takeLast(MAX_ENTRIES)
                }
            // Take the Seerr block off so it can be asked for again
            key.substringAfter('-').toIntOrNull()?.let { tmdbId ->
                withContext(WholphinDispatchers.IO) {
                    try {
                        seerrService.api.sendRaw("DELETE", "/blacklist/$tmdbId")
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not unblock %s", key)
                    }
                }
            }
            return saved
        }

        /**
         * Admin: approved requests with nothing found after [AUTO_DAYS] days become "Couldn't find it" by themselves
         * (Ben's pick: a week of "Still looking", then this). Pass the request list if you have it, or it's fetched.
         */
        suspend fun autoCheck(requests: List<MediaRequest>? = null) =
            withContext(WholphinDispatchers.IO) {
                if (!isAdmin || !seerrService.active.first()) return@withContext
                try {
                    refresh()
                    val list = requests ?: seerrService.allRequests()
                    clearArrived(list)
                    val known = all.map { it.key }.toSet()
                    val old =
                        list.filter { request ->
                            RequestStatus.from(request.status) == RequestStatus.APPROVED &&
                                request.media?.status != SeerrAvailability.AVAILABLE.status &&
                                // Benflix: some of the show is already here (maybe the very seasons asked for)
                                request.media?.status != SeerrAvailability.PARTIALLY_AVAILABLE.status &&
                                (daysSince(request.updatedAt ?: request.createdAt) ?: 0) >= AUTO_DAYS
                        }
                    val byKey = old.groupBy { it.cantFindKey() }.filterKeys { it != null && it !in known }
                    val toMark =
                        byKey.mapNotNull { (key, asks) ->
                            val first = asks.first()
                            val kind = if (SeerrItemType.fromString(first.type) == SeerrItemType.MOVIE) "movie" else "tv"
                            val raw =
                                try {
                                    seerrService.api.getRaw("/$kind/${first.media?.tmdbId}")?.let { Json.parseToJsonElement(it).jsonObject }
                                } catch (ex: CancellationException) {
                                    throw ex
                                } catch (ex: Exception) {
                                    null
                                } ?: return@mapNotNull null
                            // Ben's rule: not out yet (or out less than a few weeks) doesn't count
                            val looking = asks.mapNotNull { SearchClock.of(it, raw).daysLooking }.maxOrNull()
                            if (looking == null || looking < AUTO_DAYS) return@mapNotNull null
                            val media = raw["mediaInfo"] as? JsonObject
                            // Something is downloading, so it was found after all
                            if ((media?.get("downloadStatus") as? JsonArray).orEmpty().isNotEmpty()) return@mapNotNull null
                            val status = (media?.get("status") as? JsonPrimitive)?.intOrNull
                            if (status == SeerrAvailability.AVAILABLE.status ||
                                status == SeerrAvailability.PARTIALLY_AVAILABLE.status
                            ) {
                                return@mapNotNull null
                            }
                            val title = ((raw["title"] ?: raw["name"]) as? JsonPrimitive)?.contentOrNull
                            val poster = (raw["posterPath"] as? JsonPrimitive)?.contentOrNull?.let { TMDB_POSTER + it }
                            CantFindEntry(
                                posterUrl = poster,
                                key = key!!,
                                title = title,
                                date = LocalDate.now().toString(),
                                askedBy = asks.mapNotNull { it.requestedBy?.id }.distinct(),
                                auto = true,
                            )
                        }
                    if (toMark.isEmpty()) return@withContext
                    // Benflix: only the label. Nothing is taken out of Radarr / Sonarr or blocked in Seerr by itself:
                    // "nothing downloading and not on Benflix yet" is also what it looks like in the gap after
                    // Radarr / Sonarr grabbed the file but before Jellyfin (and Seerr) saw it, and taking it out then
                    // would delete that file. They keep searching, and the label clears itself when it turns up.
                    // Ben can still stop the search by hand.
                    change { current ->
                        val have = current.map { it.key }.toSet()
                        (current + toMark.filter { it.key !in have }).takeLast(MAX_ENTRIES)
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not check for requests that weren't found")
                }
            }

        /**
         * Benflix: labels come off things that have since arrived. A show counts once the seasons asked for are in,
         * even if other seasons are still missing.
         */
        private suspend fun clearArrived(list: List<MediaRequest>) {
            val marked = _entries.value.keys
            if (marked.isEmpty()) return
            val seasonsByShow = mutableMapOf<Int, List<Season>?>()

            suspend fun seasonsOf(tmdbId: Int): List<Season>? =
                seasonsByShow.getOrPut(tmdbId) {
                    try {
                        seerrService.api.tvApi
                            .tvTvIdGet(tvId = tmdbId)
                            .mediaInfo
                            ?.seasons
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not look up the seasons of %s", tmdbId)
                        null
                    }
                }

            val arrived =
                list
                    .filter { RequestStatus.from(it.status) != RequestStatus.DECLINED }
                    .groupBy { it.cantFindKey() }
                    .filterKeys { it != null && it in marked }
                    .filter { (_, asks) ->
                        asks.all { request ->
                            val tmdbId = request.media?.tmdbId
                            when {
                                request.media?.status == SeerrAvailability.AVAILABLE.status -> {
                                    true
                                }

                                request.media?.status == SeerrAvailability.PARTIALLY_AVAILABLE.status && tmdbId != null -> {
                                    request.requestedArrived(seasonsOf(tmdbId))
                                }

                                else -> {
                                    false
                                }
                            }
                        }
                    }.keys
                    .filterNotNull()
            if (arrived.isNotEmpty()) forget(arrived)
        }

        /** Admin: drop several at once, like titles that have since arrived */
        suspend fun forget(keys: Collection<String>) {
            if (keys.none { it in _entries.value }) return
            change { list -> list.filter { it.key !in keys } }
        }

        private suspend fun change(block: (List<CantFindEntry>) -> List<CantFindEntry>): Boolean =
            withContext(WholphinDispatchers.IO) {
                val userId = serverRepository.currentUser?.id ?: return@withContext false
                if (!isAdmin) return@withContext false
                mutex.withLock {
                    try {
                        val updated = block(read(userId))
                        // Show it straight away on this device
                        show(updated)
                        loadedFor = userId
                        loadedAt = System.currentTimeMillis()
                        writeToEveryone(updated, onlyIfDifferent = false)
                        true
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not save the can't-find list")
                        false
                    }
                }
            }

        private suspend fun read(userId: UUID): List<CantFindEntry> =
            displayPreferencesService
                .getDisplayPreferences(userId, PREF_ID)
                .customPrefs[KEY]
                ?.let { decode(it) }
                .orEmpty()

        private suspend fun writeToEveryone(
            list: List<CantFindEntry>,
            onlyIfDifferent: Boolean,
        ) {
            val encoded = json.encodeToString(list)
            val me = serverRepository.currentUser?.id
            val users =
                try {
                    api.userApi.getUsers().content
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not list users for the can't-find list")
                    emptyList()
                }
            // Ben's own copy first, so it's saved even if listing users failed
            val ids = listOfNotNull(me) + users.map { it.id }.filter { it != me }
            ids.forEach { id ->
                try {
                    if (onlyIfDifferent &&
                        displayPreferencesService.getDisplayPreferences(id, PREF_ID).customPrefs[KEY] == encoded
                    ) {
                        return@forEach
                    }
                    displayPreferencesService.updateDisplayPreferences(id, PREF_ID) {
                        if (list.isEmpty()) remove(KEY) else put(KEY, encoded)
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not share the can't-find list with %s", id)
                }
            }
        }

        private fun decode(text: String): List<CantFindEntry>? =
            try {
                json.decodeFromString<List<CantFindEntry>>(text)
            } catch (ex: Exception) {
                Timber.w(ex, "Can't-find list unreadable")
                null
            }

        companion object {
            const val PREF_ID = "benflix-shared"
            const val KEY = "benflix.cantfind.v1"
            const val MAX_NOTE = 140
            const val MAX_ENTRIES = 300
            private const val TMDB_POSTER = "https://image.tmdb.org/t/p/w342"

            /** Days of "Still looking" before a request turns into "Couldn't find it" by itself */
            const val AUTO_DAYS = 7L

            /** Calendar days (here, not UTC) since Seerr's date text */
            fun daysSince(date: String?): Long? =
                SearchClock.localDay(date)?.let { ChronoUnit.DAYS.between(it, LocalDate.now()) }

            /** "MOVIE-123" / "TV-456" for a Seerr request */
            fun MediaRequest.cantFindKey(): String? {
                val tmdbId = media?.tmdbId ?: return null
                return (if (SeerrItemType.fromString(type) == SeerrItemType.MOVIE) "MOVIE" else "TV") + "-$tmdbId"
            }

            private const val REFRESH_MS = 3 * 60_000L
            private val json = Json { ignoreUnknownKeys = true }

            /** Ready-made notes Ben can pick instead of typing on a TV remote */
            val QUICK_NOTES =
                listOf(
                    "Too old, no copies online",
                    "Only bad quality copies",
                    "Not out yet on disc or streaming",
                    "Hard to find in English",
                )

            /** "Mar 4" from yyyy-mm-dd */
            fun niceDate(date: String): String? =
                try {
                    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
                } catch (ex: Exception) {
                    null
                }
        }
    }
