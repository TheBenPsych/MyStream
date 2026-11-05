package com.github.damontecres.wholphin.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benflix: ticks off anime episodes on the person's AniList list as they finish them.
 *
 * Signing in happens once, on a phone (AniList's sign-in page needs a browser). The sign-in is
 * saved with the person's Jellyfin settings, so their TVs pick it up too. Finished episodes wait
 * in a queue on the device and are sent one at a time; if AniList is busy or the internet is
 * down they're tried again later, so nothing is lost.
 */
@Singleton
class AniListSync
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val displayPreferencesService: DisplayPreferencesService,
        private val aniList: AniListService,
        private val mal: MalSync,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + WholphinDispatchers.IO)
        private val queueLock = Mutex()
        private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private val _name = MutableStateFlow<String?>(null)

        /** The AniList name of whoever is signed in to AniList on this profile, or null */
        val name: StateFlow<String?> = _name

        @Volatile private var token: String? = null

        @Volatile private var tokenUser: UUID? = null

        init {
            instance = this
        }

        /** The AniList sign-in page; it comes back to Benflix with the sign-in */
        fun signInIntent(): Intent =
            Intent(Intent.ACTION_VIEW, Uri.parse("https://anilist.co/api/v2/oauth/authorize?client_id=$CLIENT_ID&response_type=token"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** True if this is AniList coming back after signing in (benflix://anilist#access_token=...) */
        fun isSignInReturn(intent: Intent?): Boolean = intent?.data?.let { it.scheme == "benflix" && it.host == "anilist" } == true

        /** Saves the sign-in AniList sent back, for the profile that's open */
        fun finishSignIn(intent: Intent) {
            val fragment = intent.data?.fragment.orEmpty()
            val values =
                fragment
                    .split("&")
                    .mapNotNull { part ->
                        part.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to Uri.decode(it[1]) }
                    }.toMap()
            val newToken = values["access_token"]
            scope.launch {
                // If Benflix was closed, wait for the profile to open again
                val userId =
                    serverRepository.currentUser?.id
                        ?: withTimeoutOrNull(60_000) { serverRepository.currentUserDtoFlow.first { it != null } }
                            ?.let { serverRepository.currentUser?.id }
                if (newToken.isNullOrBlank() || userId == null) {
                    showToast(context, "AniList sign-in didn't finish. Please try again.")
                    return@launch
                }
                try {
                    val viewer = viewerName(newToken)
                    displayPreferencesService.updateDisplayPreferences(userId) {
                        put(KEY, newToken)
                        put(KEY_NAME, viewer)
                    }
                    token = newToken
                    tokenUser = userId
                    _name.value = viewer
                    showToast(context, "Signed in to AniList as $viewer. Finished anime episodes will be ticked off there.")
                    sendQueue()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "AniList sign-in failed")
                    showToast(context, "Couldn't sign in to AniList. Please try again.")
                }
            }
        }

        /** Looks up whether this profile is signed in to AniList (on any of its devices); quick, no AniList call */
        suspend fun refresh() {
            val userId = serverRepository.currentUser?.id ?: return
            if (tokenUser == userId && token != null && _name.value != null) return
            try {
                val prefs = displayPreferencesService.getDisplayPreferences(userId).customPrefs
                val saved = prefs[KEY]?.takeIf { it.isNotBlank() }
                token = saved
                tokenUser = userId
                _name.value = saved?.let { prefs[KEY_NAME]?.takeIf { n -> n.isNotBlank() } ?: "signed in" }
                // Older sign-ins didn't save the name: look it up once, in the background
                if (saved != null && prefs[KEY_NAME].isNullOrBlank()) {
                    scope.launch {
                        try {
                            val viewer = viewerName(saved)
                            _name.value = viewer
                            displayPreferencesService.updateDisplayPreferences(userId) { put(KEY_NAME, viewer) }
                        } catch (ex: AniListSignedOutException) {
                            signOutLocal(userId)
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            Timber.w(ex, "Could not get the AniList name")
                        }
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not check the AniList sign-in")
            }
        }

        suspend fun signOut() {
            val userId = serverRepository.currentUser?.id ?: return
            try {
                displayPreferencesService.updateDisplayPreferences(userId) {
                    remove(KEY)
                    remove(KEY_NAME)
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not remove the AniList sign-in")
            }
            signOutLocal(userId)
        }

        private fun signOutLocal(userId: UUID) {
            if (tokenUser == userId) token = null
            _name.value = null
        }

        private suspend fun currentToken(userId: UUID): String? {
            if (tokenUser == userId) return token
            return try {
                displayPreferencesService
                    .getDisplayPreferences(userId)
                    .customPrefs[KEY]
                    ?.takeIf { it.isNotBlank() }
                    .also {
                        token = it
                        tokenUser = userId
                    }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                null
            }
        }

        private suspend fun viewerName(token: String): String {
            val data = aniList.signedInQuery("query { Viewer { name } }", JsonObject(emptyMap()), token)
            return ((data?.get("Viewer") as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull ?: "you"
        }

        /** Called when playback stops: an anime episode watched to (nearly) the end goes in the queue */
        fun watched(
            itemId: UUID,
            positionMs: Long,
            durationMs: Long,
        ) {
            if (durationMs <= 0 || positionMs < durationMs * DONE_FRACTION) return
            if (KidsMode.active.value || GuestMode.isActive) return
            val userId = serverRepository.currentUser?.id ?: return
            scope.launch {
                if (currentToken(userId) == null && !mal.isSignedIn(userId)) return@launch
                queueLock.withLock {
                    val queue = prefs.getStringSet(QUEUE, emptySet()).orEmpty()
                    prefs.edit { putStringSet(QUEUE, queue + "$userId|$itemId") }
                }
                sendQueue()
            }
        }

        /** Marked as watched by hand (or "watched up to here"): counts like finishing it */
        fun markedWatched(itemId: UUID) = watched(itemId, 1, 1)

        /** Sends what's waiting, oldest first. Stops at the first problem and tries again next time. */
        fun sendQueue() {
            scope.launch {
                queueLock.withLock {
                    val queue = prefs.getStringSet(QUEUE, emptySet()).orEmpty()
                    for (entry in queue) {
                        val (user, item) = entry.split("|").takeIf { it.size == 2 } ?: continue
                        val userId = runCatching { UUID.fromString(user) }.getOrNull()
                        val itemId = runCatching { UUID.fromString(item) }.getOrNull()
                        val done =
                            if (userId == null || itemId == null || userId != serverRepository.currentUser?.id) {
                                // Another profile's episode waits until that profile is open
                                userId == null || itemId == null
                            } else {
                                try {
                                    tick(userId, itemId)
                                    true
                                } catch (ex: AniListSignedOutException) {
                                    signOutLocal(userId)
                                    showToast(context, "AniList signed you out. Sign in again on My Benflix to keep ticking off episodes.")
                                    true
                                } catch (ex: MalSignedOutException) {
                                    mal.signedOut(userId)
                                    showToast(context, "MyAnimeList signed you out. Sign in again on My Benflix to keep ticking off episodes.")
                                    true
                                } catch (ex: CancellationException) {
                                    throw ex
                                } catch (ex: Exception) {
                                    Timber.w(ex, "AniList update failed, will try again later")
                                    break
                                }
                            }
                        if (done) prefs.edit { putStringSet(QUEUE, prefs.getStringSet(QUEUE, emptySet()).orEmpty() - entry) }
                    }
                }
            }
        }

        /**
         * Works out which AniList entry and episode this is, and moves the person's AniList and/or
         * MyAnimeList list forward (never back)
         */
        private suspend fun tick(
            userId: UUID,
            itemId: UUID,
        ) {
            val token = currentToken(userId)
            val withMal = mal.isSignedIn(userId)
            if (token == null && !withMal) return
            val episode = api.userLibraryApi.getItem(itemId, userId).content
            if (episode.type != BaseItemKind.EPISODE || (episode.parentIndexNumber ?: 1) == 0) return
            val seriesId = episode.seriesId ?: return
            val series = api.userLibraryApi.getItem(seriesId, userId).content
            if (!BenflixAnimeTracks.isAnime(series) && !BenflixAnimeTracks.isAnime(episode)) return
            val base = aniList.infoFor(series)?.id?.takeIf { it > 0 } ?: return
            // The episode's number counted from the very first one, then walk AniList's seasons
            val episodes =
                api.tvShowsApi
                    .getEpisodes(GetEpisodesRequest(seriesId = seriesId, userId = userId, enableImages = false, enableUserData = false))
                    .content.items
                    .filter { (it.parentIndexNumber ?: 1) > 0 && it.indexNumber != null }
            var number = AnimeEpisodeNumbers.workOut(episodes)[itemId] ?: episode.indexNumberEnd ?: episode.indexNumber ?: return
            var mediaId = base
            repeat(MAX_SEASONS) {
                val (sequel, count) = aniList.sequelOf(mediaId)
                if (count == null || number <= count || sequel == null) {
                    val sites = mutableListOf<String>()
                    if (token != null && save(token, mediaId, number, count)) sites += "AniList"
                    if (withMal) {
                        val malId = aniList.malIdOf(mediaId)
                        if (malId != null && mal.save(userId, malId, number)) sites += "MyAnimeList"
                    }
                    if (sites.isNotEmpty()) {
                        showToast(context, "Ticked off on ${sites.joinToString(" and ")}: ${series.name} episode $number")
                    }
                    return
                }
                number -= count
                mediaId = sequel
            }
        }

        private suspend fun save(
            token: String,
            mediaId: Int,
            progress: Int,
            episodes: Int?,
        ): Boolean {
            val current =
                aniList
                    .signedInQuery(
                        "query (\$id: Int) { Media(id: \$id) { mediaListEntry { progress status } } }",
                        buildJsonObject { put("id", mediaId) },
                        token,
                    )?.let { (it["Media"] as? JsonObject)?.get("mediaListEntry") as? JsonObject }
            val had = (current?.get("progress") as? JsonPrimitive)?.intOrNull ?: 0
            val hadStatus = (current?.get("status") as? JsonPrimitive)?.contentOrNull
            if (had >= progress && hadStatus != "PLANNING") return false
            val status = if (episodes != null && progress >= episodes) "COMPLETED" else "CURRENT"
            aniList.signedInQuery(
                "mutation (\$id: Int, \$p: Int, \$s: MediaListStatus) { SaveMediaListEntry(mediaId: \$id, progress: \$p, status: \$s) { id } }",
                buildJsonObject {
                    put("id", mediaId)
                    put("p", progress)
                    put("s", status)
                },
                token,
            )
            Timber.i("AniList: %s episode %s (%s)", mediaId, progress, status)
            return true
        }

        companion object {
            /** Benflix's AniList app (made by Ben); public, sign-in still needs the person's own password */
            private const val CLIENT_ID = 55837
            private const val PREFS = "benflix_anilist"
            private const val QUEUE = "queue"
            private const val KEY = "benflix_anilist_token"
            private const val KEY_NAME = "benflix_anilist_name"
            private const val DONE_FRACTION = 0.8
            private const val MAX_SEASONS = 12

            /** Set when the app starts, for the player to report finished episodes */
            @Volatile var instance: AniListSync? = null
                private set
        }
    }
