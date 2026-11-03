package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.UUID
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
)

/**
 * Benflix: "Couldn't find it". When an approved request can't be found anywhere (often old or rare stuff),
 * Ben marks it, and everyone sees it on their request card and on the title's page. The people who asked
 * get a pop-up once.
 *
 * Seerr has no place for this, so the list is kept in every Benflix user's Jellyfin display settings
 * (the admin can write to everyone's, like the Home notes in [BenflixAdminTools]). Each device reads its
 * own user's copy. Radarr / Sonarr keep searching, and once the title arrives the label just stops showing.
 */
@Singleton
class BenflixCantFind
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val displayPreferencesService: DisplayPreferencesService,
    ) {
        private val _entries = MutableStateFlow<Map<String, CantFindEntry>>(emptyMap())

        /** By key ("MOVIE-123" / "TV-456") */
        val entries: StateFlow<Map<String, CantFindEntry>> = _entries

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
                        _entries.value = emptyMap()
                        sharedWithEveryone = false
                    }
                    try {
                        val list = read(userId)
                        _entries.value = list.associateBy { it.key }
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
        ): Boolean =
            change { list ->
                val old = list.firstOrNull { it.key == key }
                val entry =
                    CantFindEntry(
                        key = key,
                        title = title ?: old?.title,
                        note = note?.trim()?.take(MAX_NOTE)?.takeIf { it.isNotEmpty() },
                        date = LocalDate.now().toString(),
                        askedBy = (old?.askedBy.orEmpty() + askedBy).distinct(),
                    )
                (list.filter { it.key != key } + entry).takeLast(MAX_ENTRIES)
            }

        /** Admin: take the label off again (found it after all, or marked by mistake) */
        suspend fun undo(key: String): Boolean = change { list -> list.filter { it.key != key } }

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
                        _entries.value = updated.associateBy { it.key }
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
