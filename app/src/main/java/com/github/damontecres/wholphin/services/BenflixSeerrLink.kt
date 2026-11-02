package com.github.damontecres.wholphin.services

import android.util.Base64
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.data.model.SeerrAuthMethod
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benflix: Seerr (Requests) connects by itself, so Ben doesn't have to set it up on every TV.
 *
 * - When Ben (an admin) connects Seerr on any device, his own Seerr login is saved to his own Jellyfin
 *   account, and the Seerr address is saved to every account on the server.
 * - When someone signs in on a new device, Benflix connects them to Seerr with "Sign in with Jellyfin", using the
 *   password they just typed. The password is only kept in memory until then (and in the device's Seerr login,
 *   like a normal Seerr setup), never in their Jellyfin account.
 * - People who signed in with Quick Connect type their password once on the Requests tab.
 */
@Singleton
class BenflixSeerrLink
    @Inject
    constructor(
        private val api: ApiClient,
        private val displayPreferencesService: DisplayPreferencesService,
        private val serverRepository: ServerRepository,
        private val seerrServerRepository: SeerrServerRepository,
    ) {
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
                // Ben's own saved login
                prefs[KEY_LOGIN]?.let { decode(it) }?.let { login ->
                    val method = SeerrAuthMethod.entries.firstOrNull { it.name == login.method } ?: return@let
                    Timber.i("Seerr: using the saved login")
                    if (method == SeerrAuthMethod.API_KEY) {
                        seerrServerRepository.addAndChangeServer(login.url, login.secret)
                    } else {
                        seerrServerRepository.addAndChangeServer(login.url, method, login.username, login.secret)
                    }
                    return true
                }
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
