package com.github.damontecres.wholphin.services

import android.util.Base64
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Benflix: Seerr (Requests) connects by itself, so Ben doesn't have to set it up on every TV.
 *
 * - When Ben (an admin) connects Seerr on any device, his own Seerr login is saved to his own Jellyfin
 *   account, and the Seerr address is saved to every account on the server.
 * - When someone signs in on a new device, Benflix connects them to Seerr with "Sign in with Jellyfin", using the
 *   password they just typed. The password is only kept in memory until then (and in the device's Seerr login,
 *   like a normal Seerr setup), never in their Jellyfin account.
 * - People who signed in with Quick Connect type their password once on the Requests tab.
 * - Best of all: if the Jellyfin Enhanced plugin is set up with Seerr on the Jellyfin server, everyone goes
 *   through it. It works away from home too (anywhere Jellyfin works) and needs no Seerr password.
 */
@Singleton
class BenflixSeerrLink
    @Inject
    constructor(
        private val api: ApiClient,
        private val displayPreferencesService: DisplayPreferencesService,
        private val serverRepository: ServerRepository,
        private val seerrServerRepository: SeerrServerRepository,
        @param:StandardOkHttpClient private val okHttpClient: OkHttpClient,
    ) {
        private val quickHttp by lazy {
            okHttpClient
                .newBuilder()
                .connectTimeout(4.seconds)
                .readTimeout(10.seconds)
                .build()
        }

        private val passwords = ConcurrentHashMap<UUID, String>()
        private val shared = ConcurrentHashMap.newKeySet<String>()

        /** The password someone typed to sign in (memory only) */
        fun rememberPassword(
            userId: UUID,
            password: String,
        ) {
            passwords[userId] = password
        }

        /** The Seerr address Ben shared with everyone, if any */
        suspend fun sharedUrl(userId: UUID): String? =
            try {
                displayPreferencesService
                    .getDisplayPreferences(userId)
                    .customPrefs[KEY_URL]
                    ?.takeIf { it.isNotBlank() }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not read the shared Seerr address")
                null
            }

        /**
         * Called when this device has no working Seerr connection for [user].
         * Returns true if it connected.
         */
        suspend fun autoConnect(user: JellyfinUser): Boolean {
            try {
                val prefs = displayPreferencesService.getDisplayPreferences(user.id).customPrefs
                // Ben's own saved login (talks to Seerr directly, so every Find shelf works)
                prefs[KEY_LOGIN]?.let { decode(it) }?.let { login ->
                    val method = SeerrAuthMethod.entries.firstOrNull { it.name == login.method } ?: return@let
                    Timber.i("Seerr: using the saved login")
                    try {
                        if (method == SeerrAuthMethod.API_KEY) {
                            seerrServerRepository.addAndChangeServer(login.url, login.secret)
                        } else {
                            seerrServerRepository.addAndChangeServer(login.url, method, login.username, login.secret)
                        }
                        return true
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        // Probably away from home: try Jellyfin Enhanced instead
                        Timber.w(ex, "Seerr saved login didn't work")
                    }
                }
                if (viaJellyfinEnhanced()) return true
                val url = prefs[KEY_URL]?.takeIf { it.isNotBlank() } ?: return false
                val password = passwords.remove(user.id) ?: return false
                val name = user.name ?: return false
                Timber.i("Seerr: signing in with Jellyfin")
                seerrServerRepository.addAndChangeServer(url, SeerrAuthMethod.JELLYFIN, name, password)
                return true
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Seerr auto connect failed")
                return false
            }
        }

        /**
         * Connect through the Jellyfin Enhanced plugin, if it is installed with Seerr turned on and this person
         * has a Seerr account. Returns true if it connected.
         */
        suspend fun viaJellyfinEnhanced(): Boolean {
            val base = api.baseUrl?.trimEnd('/') ?: return false
            val token = api.accessToken ?: return false
            val user = serverRepository.currentUser ?: return false
            return try {
                val request =
                    Request
                        .Builder()
                        .url("$base/JellyfinEnhanced/jellyseerr/user-status")
                        .header("Authorization", "MediaBrowser Client=\"Benflix\", Token=\"$token\"")
                        .build()
                val body =
                    withContext(Dispatchers.IO) {
                        quickHttp.newCall(request).execute().use { if (it.isSuccessful) it.body.string() else null }
                    } ?: return false
                val status = Json.parseToJsonElement(body).jsonObject
                val linked =
                    status["active"]?.jsonPrimitive?.booleanOrNull == true &&
                        status["userFound"]?.jsonPrimitive?.booleanOrNull == true
                val seerrId = status["jellyseerrUserId"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                if (!linked || seerrId == null) {
                    Timber.i("Jellyfin Enhanced Seerr not usable: %s", status["reason"])
                    return false
                }
                Timber.i("Seerr: using Jellyfin Enhanced")
                seerrServerRepository.connectViaJellyfinEnhanced(
                    jellyfinUrl = base,
                    token = token,
                    jellyfinUserRowId = user.rowId,
                    seerrUserId = seerrId,
                    admin = serverRepository.currentUserDto?.policy?.isAdministrator == true,
                )
                true
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Jellyfin Enhanced Seerr check failed")
                false
            }
        }

        /** Quick Connect people: connect with the password they type on the Requests tab */
        suspend fun connectWithPassword(password: String): Boolean {
            val user = serverRepository.currentUser ?: return false
            val url = sharedUrl(user.id) ?: return false
            val name = user.name ?: return false
            return try {
                seerrServerRepository.addAndChangeServer(url, SeerrAuthMethod.JELLYFIN, name, password)
                true
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Seerr connect with password failed")
                false
            }
        }

        /**
         * Called when an admin is connected to Seerr: save their login to their own account and the
         * address to everyone's (once per session)
         */
        suspend fun shareSetup(
            userId: UUID,
            current: CurrentSeerr,
        ) {
            val url = current.server.url
            val seerrUser = current.user
            val secret =
                (if (seerrUser.authMethod == SeerrAuthMethod.API_KEY) seerrUser.credential else seerrUser.password)
                    ?: return
            if (!shared.add("$userId|$url")) return
            try {
                val login =
                    encode(
                        SharedSeerrLogin(
                            url = url,
                            method = seerrUser.authMethod.name,
                            username = seerrUser.username.orEmpty(),
                            secret = secret,
                        ),
                    )
                val own = displayPreferencesService.getDisplayPreferences(userId).customPrefs
                if (own[KEY_LOGIN] != login || own[KEY_URL] != url) {
                    displayPreferencesService.updateDisplayPreferences(userId) {
                        put(KEY_LOGIN, login)
                        put(KEY_URL, url)
                    }
                }
                val users = api.userApi.getUsers().content
                var count = 0
                users.filter { it.id != userId }.forEach { other ->
                    try {
                        val prefs = displayPreferencesService.getDisplayPreferences(other.id).customPrefs
                        if (prefs[KEY_URL] != url) {
                            displayPreferencesService.updateDisplayPreferences(other.id) { put(KEY_URL, url) }
                            count++
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not share the Seerr address with %s", other.name)
                    }
                }
                Timber.i("Seerr setup shared with %s accounts", count)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                shared.remove("$userId|$url")
                Timber.w(ex, "Could not share the Seerr setup")
            }
        }

        private fun encode(login: SharedSeerrLogin): String {
            val json = Json.encodeToString(SharedSeerrLogin.serializer(), login)
            return Base64.encodeToString(json.toByteArray(), Base64.NO_WRAP)
        }

        private fun decode(text: String): SharedSeerrLogin? =
            try {
                Json.decodeFromString(SharedSeerrLogin.serializer(), String(Base64.decode(text, Base64.NO_WRAP)))
            } catch (ex: Exception) {
                null
            }

        companion object {
            private const val KEY_URL = "benflix.seerr.url"
            private const val KEY_LOGIN = "benflix.seerr.login"
        }
    }

@Serializable
private data class SharedSeerrLogin(
    val url: String,
    val method: String,
    val username: String,
    val secret: String,
)
