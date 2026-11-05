package com.github.damontecres.wholphin.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** MyAnimeList said the sign-in is no longer good (and it couldn't be renewed) */
class MalSignedOutException : Exception("MyAnimeList sign-in expired")

/**
 * Benflix: the person's MyAnimeList account, so finished anime episodes are ticked off on their
 * MyAnimeList list too (works with or without AniList; [AniListSync] does the ticking for both).
 *
 * Signing in happens once, on a phone (MyAnimeList's sign-in page needs a browser). The sign-in is
 * saved with the person's Jellyfin settings, so their TVs pick it up too. MyAnimeList sign-ins last
 * about a month and are renewed by themselves.
 */
@Singleton
class MalSync
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:StandardOkHttpClient private val http: OkHttpClient,
        private val serverRepository: ServerRepository,
        private val displayPreferencesService: DisplayPreferencesService,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + WholphinDispatchers.IO)
        private val json = Json { ignoreUnknownKeys = true }
        private val tokenLock = Mutex()
        private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private val _name = MutableStateFlow<String?>(null)

        /** The MyAnimeList name of whoever is signed in on this profile, or null */
        val name: StateFlow<String?> = _name

        private data class Tokens(
            val access: String,
            val refresh: String?,
            val expiresAt: Long,
        )

        @Volatile private var tokens: Tokens? = null

        @Volatile private var tokensUser: UUID? = null

        /** MyAnimeList's sign-in page; it comes back to Benflix (benflix://mal-signin?code=...) */
        fun signInIntent(): Intent {
            val verifier = randomText(VERIFIER_LENGTH)
            val state = randomText(STATE_LENGTH)
            prefs.edit {
                putString(PENDING_VERIFIER, verifier)
                putString(PENDING_STATE, state)
            }
            val url =
                Uri
                    .parse(AUTHORIZE_URL)
                    .buildUpon()
                    .appendQueryParameter("response_type", "code")
                    .appendQueryParameter("client_id", CLIENT_ID)
                    .appendQueryParameter("code_challenge", verifier)
                    .appendQueryParameter("code_challenge_method", "plain")
                    .appendQueryParameter("state", state)
                    .appendQueryParameter("redirect_uri", REDIRECT)
                    .build()
            return Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        fun isSignInReturn(intent: Intent?): Boolean = intent?.data?.let { it.scheme == "benflix" && it.host == REDIRECT_HOST } == true

        /** Swaps the code MyAnimeList sent back for a sign-in, saved for the profile that's open */
        fun finishSignIn(intent: Intent) {
            val code = intent.data?.getQueryParameter("code")
            val state = intent.data?.getQueryParameter("state")
            val verifier = prefs.getString(PENDING_VERIFIER, null)
            val expected = prefs.getString(PENDING_STATE, null)
            scope.launch {
                val userId =
                    serverRepository.currentUser?.id
                        ?: withTimeoutOrNull(60_000) { serverRepository.currentUserDtoFlow.first { it != null } }
                            ?.let { serverRepository.currentUser?.id }
                if (code.isNullOrBlank() || verifier == null || state != expected || userId == null) {
                    showToast(context, "MyAnimeList sign-in didn't finish. Please try again.")
                    return@launch
                }
                try {
                    val got =
                        tokenCall(
                            FormBody
                                .Builder()
                                .add("client_id", CLIENT_ID)
                                .add("grant_type", "authorization_code")
                                .add("code", code)
                                .add("code_verifier", verifier)
                                .add("redirect_uri", REDIRECT)
                                .build(),
                        )
                    val viewer = viewerName(got.access)
                    saveTokens(userId, got, viewer)
                    prefs.edit {
                        remove(PENDING_VERIFIER)
                        remove(PENDING_STATE)
                    }
                    showToast(context, "Signed in to MyAnimeList as $viewer. Finished anime episodes will be ticked off there.")
                    AniListSync.instance?.sendQueue()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "MyAnimeList sign-in failed")
                    showToast(context, "Couldn't sign in to MyAnimeList. Please try again.")
                }
            }
        }

        /** Looks up whether this profile is signed in to MyAnimeList (on any of its devices) */
        suspend fun refresh() {
            val userId = serverRepository.currentUser?.id ?: return
            if (tokensUser == userId && tokens != null && _name.value != null) return
            try {
                load(userId)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not check the MyAnimeList sign-in")
            }
        }

        suspend fun signOut() {
            val userId = serverRepository.currentUser?.id ?: return
            try {
                displayPreferencesService.updateDisplayPreferences(userId) {
                    listOf(KEY, KEY_REFRESH, KEY_EXPIRES, KEY_NAME).forEach { remove(it) }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not remove the MyAnimeList sign-in")
            }
            signOutLocal(userId)
        }

        private fun signOutLocal(userId: UUID) {
            if (tokensUser == userId) tokens = null
            _name.value = null
        }

        /** True if this profile is signed in to MyAnimeList */
        suspend fun isSignedIn(userId: UUID): Boolean =
            try {
                tokensFor(userId) != null
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                false
            }

        private suspend fun load(userId: UUID): Tokens? {
            val saved = displayPreferencesService.getDisplayPreferences(userId).customPrefs
            val access = saved[KEY]?.takeIf { it.isNotBlank() }
            val found =
                access?.let {
                    Tokens(it, saved[KEY_REFRESH]?.takeIf { r -> r.isNotBlank() }, saved[KEY_EXPIRES]?.toLongOrNull() ?: 0L)
                }
            tokens = found
            tokensUser = userId
            _name.value = found?.let { saved[KEY_NAME]?.takeIf { n -> n.isNotBlank() } ?: "signed in" }
            return found
        }

        /** The saved sign-in; when there's none yet, asks Jellyfin again (the phone may have signed in since) */
        private suspend fun tokensFor(userId: UUID): Tokens? = tokens?.takeIf { tokensUser == userId } ?: load(userId)

        private suspend fun saveTokens(
            userId: UUID,
            got: Tokens,
            viewer: String?,
        ) {
            displayPreferencesService.updateDisplayPreferences(userId) {
                put(KEY, got.access)
                put(KEY_REFRESH, got.refresh)
                put(KEY_EXPIRES, got.expiresAt.toString())
                if (viewer != null) put(KEY_NAME, viewer)
            }
            tokens = got
            tokensUser = userId
            if (viewer != null) _name.value = viewer
        }

        /** A sign-in that's good for at least a day, renewed if needed; null if not signed in */
        private suspend fun accessToken(
            userId: UUID,
            force: Boolean = false,
        ): String? =
            tokenLock.withLock {
                val now = tokensFor(userId) ?: return@withLock null
                if (!force && now.expiresAt - System.currentTimeMillis() > DAY_MS) return@withLock now.access
                val refresh = now.refresh ?: throw MalSignedOutException()
                val renewed =
                    tokenCall(
                        FormBody
                            .Builder()
                            .add("client_id", CLIENT_ID)
                            .add("grant_type", "refresh_token")
                            .add("refresh_token", refresh)
                            .build(),
                    )
                saveTokens(userId, renewed.copy(refresh = renewed.refresh ?: refresh), null)
                renewed.access
            }

        private suspend fun tokenCall(body: FormBody): Tokens =
            withContext(WholphinDispatchers.IO) {
                val request =
                    Request
                        .Builder()
                        .url(TOKEN_URL)
                        .post(body)
                        .build()
                http.newCall(request).execute().use { response ->
                    val text = response.body.string()
                    if (response.code == 400 || response.code == 401) throw MalSignedOutException()
                    if (!response.isSuccessful) throw IllegalStateException("MyAnimeList answered ${response.code}")
                    val o = json.parseToJsonElement(text) as JsonObject
                    val access = (o["access_token"] as? JsonPrimitive)?.contentOrNull ?: throw IllegalStateException("No sign-in")
                    val seconds = (o["expires_in"] as? JsonPrimitive)?.longOrNull ?: MONTH_SECONDS
                    Tokens(
                        access = access,
                        refresh = (o["refresh_token"] as? JsonPrimitive)?.contentOrNull,
                        expiresAt = System.currentTimeMillis() + seconds * 1000,
                    )
                }
            }

        private suspend fun viewerName(access: String): String? =
            try {
                val o = call(access, Request.Builder().url("$API_URL/users/@me"))
                (o?.get("name") as? JsonPrimitive)?.contentOrNull
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not get the MyAnimeList name")
                null
            }

        /** One call to MyAnimeList's API; null for 404 */
        private suspend fun call(
            access: String,
            builder: Request.Builder,
        ): JsonObject? =
            withContext(WholphinDispatchers.IO) {
                http.newCall(builder.header("Authorization", "Bearer $access").build()).execute().use { response ->
                    val text = response.body.string()
                    when {
                        response.code == 401 -> throw MalSignedOutException()
                        response.code == 404 -> null
                        !response.isSuccessful -> throw IllegalStateException("MyAnimeList answered ${response.code}")
                        else -> json.parseToJsonElement(text) as? JsonObject
                    }
                }
            }

        /**
         * Moves the person's MyAnimeList entry forward to [progress] episodes (never back).
         * Returns true if something changed. Throws [MalSignedOutException] if they need to sign in again.
         */
        suspend fun save(
            userId: UUID,
            malId: Int,
            progress: Int,
        ): Boolean {
            var access = accessToken(userId) ?: return false
            val current =
                try {
                    call(access, Request.Builder().url("$API_URL/anime/$malId?fields=num_episodes,my_list_status"))
                } catch (ex: MalSignedOutException) {
                    // Maybe just expired early: renew once
                    access = accessToken(userId, force = true) ?: return false
                    call(access, Request.Builder().url("$API_URL/anime/$malId?fields=num_episodes,my_list_status"))
                } ?: return false
            val episodes = (current["num_episodes"] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 }
            val mine = current["my_list_status"] as? JsonObject
            val had = (mine?.get("num_episodes_watched") as? JsonPrimitive)?.intOrNull ?: 0
            val hadStatus = (mine?.get("status") as? JsonPrimitive)?.contentOrNull
            if (had >= progress && hadStatus != null && hadStatus != "plan_to_watch") return false
            val status = if (episodes != null && progress >= episodes) "completed" else "watching"
            call(
                access,
                Request
                    .Builder()
                    .url("$API_URL/anime/$malId/my_list_status")
                    .patch(
                        FormBody
                            .Builder()
                            .add("status", status)
                            .add("num_watched_episodes", progress.toString())
                            .build(),
                    ),
            )
            Timber.i("MyAnimeList: %s episode %s (%s)", malId, progress, status)
            return true
        }

        /** Called when MyAnimeList refused the sign-in for good */
        fun signedOut(userId: UUID) = signOutLocal(userId)

        private fun randomText(length: Int): String {
            val random = SecureRandom()
            return buildString { repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
        }

        companion object {
            /** Benflix's MyAnimeList app (made by Ben); public, sign-in still needs the person's own password */
            private const val CLIENT_ID = "dc676979ad14050187f6e7b7d779b9c6"
            private const val REDIRECT_HOST = "mal-signin"
            private const val REDIRECT = "benflix://$REDIRECT_HOST"
            private const val AUTHORIZE_URL = "https://myanimelist.net/v1/oauth2/authorize"
            private const val TOKEN_URL = "https://myanimelist.net/v1/oauth2/token"
            private const val API_URL = "https://api.myanimelist.net/v2"
            private const val PREFS = "benflix_mal"
            private const val PENDING_VERIFIER = "pending_verifier"
            private const val PENDING_STATE = "pending_state"
            private const val KEY = "benflix_mal_token"
            private const val KEY_REFRESH = "benflix_mal_refresh"
            private const val KEY_EXPIRES = "benflix_mal_expires"
            private const val KEY_NAME = "benflix_mal_name"
            private const val VERIFIER_LENGTH = 64
            private const val STATE_LENGTH = 16
            private const val DAY_MS = 24L * 60 * 60 * 1000
            private const val MONTH_SECONDS = 31L * 24 * 60 * 60
            private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        }
    }
