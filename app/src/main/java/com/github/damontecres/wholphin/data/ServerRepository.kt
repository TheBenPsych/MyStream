package com.github.damontecres.wholphin.data

import android.content.Context
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.data.model.ServerUserConfig
import com.github.damontecres.wholphin.services.BENFLIX_SUBTITLE_LANGUAGE
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.ProfileModes
import com.github.damontecres.wholphin.services.hilt.IoDispatcher
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles managing the current server & user as well as adding & removing new ones
 */
@Singleton
class ServerRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val jellyfin: Jellyfin,
        val serverDao: JellyfinServerDao,
        val apiClient: ApiClient,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val mostRecentServerProvider: MostRecentServerProvider,
    ) {
        private var _current = MutableStateFlow<CurrentUser?>(null)
        val current: StateFlow<CurrentUser?> = _current

        private var _currentUserDto = MutableStateFlow<ServerUserConfig?>(null)
        val currentUserDto: ServerUserConfig? get() = _currentUserDto.value
        val currentUserDtoFlow: StateFlow<ServerUserConfig?> get() = _currentUserDto

        val currentServer: JellyfinServer? get() = _current.value?.server
        val currentServerFlow: Flow<JellyfinServer?> get() = _current.map { it?.server }
        val currentUser: JellyfinUser? get() = _current.value?.user

        @OptIn(ExperimentalCoroutinesApi::class)
        val currentUserFlow: Flow<JellyfinUser?>
            get() =
                _current
                    .flatMapLatest { user ->
                        if (user?.user != null) {
                            serverDao
                                .getUserFlow(user.user.serverId, user.user.id)
                                .distinctUntilChanged()
                        } else {
                            flow { emit(null) }
                        }
                    }

        val serverPluginInstalled = MutableStateFlow<Boolean>(false)

        /**
         * Adds a server to the app database and updated the [ApiClient] to the server's URL
         *
         * The current user is removed
         */
        suspend fun addAndChangeServer(server: JellyfinServer) {
            withContext(ioDispatcher) {
                serverDao.addOrUpdateServer(server)
            }
            apiClient.update(baseUrl = server.url, accessToken = null)
            _current.value = null
            mostRecentServerProvider.clearUser()
        }

        /**
         * Saves the server & user to the app database and updates the [ApiClient] to use this server & user
         */
        suspend fun changeUser(
            server: JellyfinServer,
            user: JellyfinUser,
        ): CurrentUser =
            withContext(ioDispatcher) {
                // Benflix "Request for": a new profile always starts asking for itself
                com.github.damontecres.wholphin.services.RequestFor
                    .clear()
                if (server.id != user.serverId) {
                    throw IllegalStateException("User is not part of the server")
                }
                Timber.v("Changing user to ${user.name} on ${server.url}")
                apiClient.update(baseUrl = server.url, accessToken = user.accessToken)
                val userDto by apiClient.userApi.getCurrentUser()
                // Benflix: a kids account on Jellyfin (a max rating set, or named "Kids") always gets the Kids
                // profile, like on the web. Before, signing in to it directly showed the grown-up app.
                val maxRating = userDto.policy?.maxParentalRating
                if ((maxRating != null || userDto.name?.trim().equals("kids", ignoreCase = true)) && !KidsMode.isActive) {
                    KidsMode.set(true, big = (maxRating ?: 0) >= 13)
                    ProfileModes.save(context, userDto.id)
                }
                val updatedServer =
                    try {
                        val sysInfo by apiClient.systemApi.getPublicSystemInfo()
                        server.copy(name = sysInfo.serverName, version = sysInfo.version)
                    } catch (ex: Exception) {
                        Timber.w(ex, "Exception fetching public system info")
                        server
                    }
                var updatedUser =
                    user.copy(
                        id = userDto.id,
                        name = userDto.name,
                    )
                serverDao.addOrUpdateServer(updatedServer)
                updatedUser = serverDao.addOrUpdateUser(updatedUser)

                val currentUser = CurrentUser(updatedServer, updatedUser)
                mostRecentServerProvider.save(currentUser)
                _current.value = currentUser
                _currentUserDto.value = ServerUserConfig(userDto)
                saveEnglishSubtitlesIfUnset(userDto)
                rememberWorking(updatedServer)
                return@withContext currentUser
            }

        /**
         * Try to restore the most recent session
         *
         * Does not check if auto sign-in is enabled, but does check if the user profile is protected
         */
        suspend fun restoreLastSession(): RestoredSession {
            val recentServer = mostRecentServerProvider.get()
            Timber.d("restoreLastSession: recentServer=%s", recentServer)
            val result =
                when (recentServer) {
                    MostRecentServer.None -> {
                        RestoredSession.None
                    }

                    is MostRecentServer.Server -> {
                        val server = serverDao.getServer(recentServer.serverId)
                        if (server != null) {
                            RestoredSession.ServerOnly(findMovedServer(server.server))
                        } else {
                            RestoredSession.None
                        }
                    }

                    is MostRecentServer.ServerAndUser -> {
                        val currentUser = tryChangeUser(recentServer.serverId, recentServer.userId)
                        if (currentUser != null) {
                            if (currentUser.user.isProtected) {
                                RestoredSession.ServerOnly(currentUser.server)
                            } else {
                                RestoredSession.Success(currentUser)
                            }
                        } else {
                            getMostRecentServerInternal(recentServer)
                        }
                    }
                }
            return result
        }

        /**
         * Get the most recently used server, if any
         */
        suspend fun getMostRecentServer(): RestoredSession = getMostRecentServerInternal(mostRecentServerProvider.get())

        suspend fun getMostRecentServerInternal(recentServer: MostRecentServer): RestoredSession {
            val recentServer = recentServer ?: mostRecentServerProvider.get()
            val serverId =
                when (recentServer) {
                    MostRecentServer.None -> null
                    is MostRecentServer.Server -> recentServer.serverId
                    is MostRecentServer.ServerAndUser -> recentServer.serverId
                }
            val server = serverId?.let { serverDao.getServer(serverId)?.server }?.let { findMovedServer(it) }
            return if (server != null) {
                RestoredSession.ServerOnly(server)
            } else {
                RestoredSession.None
            }
        }

        /**
         * Try to change to the given server & user. If the user's profile is protected, returns null
         *
         * @return the server/user or null if the session could not be restored
         */
        suspend fun tryChangeUser(
            serverId: UUID?,
            userId: UUID?,
        ): CurrentUser? =
            withContext(ioDispatcher) {
                return@withContext if (serverId == null || userId == null) {
                    _current.value = null
                    null
                } else {
                    val serverAndUsers = serverDao.getServer(serverId)
                    if (serverAndUsers != null) {
                        val user = serverAndUsers.users.firstOrNull { it.id == userId }
                        if (user != null && !user.isProtected) {
                            // Benflix: bring back Kids / Guest first (they're only in memory otherwise),
                            // so a child doesn't come back on the full grown-up sign-in
                            ProfileModes.restore(context, user.id)
                            changeUser(findMovedServer(serverAndUsers.server), user)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
            }

        /**
         * Benflix: when Tower got a new home address (a move or a new router), find it again and save the new
         * address, so TVs signed in with the old one keep working. See [ServerMoved].
         */
        private suspend fun findMovedServer(server: JellyfinServer): JellyfinServer =
            withContext(ioDispatcher) {
                // One search at a time (opening the app, a network change and the nap screen can all ask at once)
                findLock.withLock {
                    val latest = currentServer?.takeIf { it.id == server.id } ?: server
                    val found =
                        try {
                            ServerMoved.findAgain(context, latest)
                        } catch (e: Exception) {
                            Timber.w(e, "Couldn't look for a moved server")
                            latest
                        }
                    if (found.url != latest.url) {
                        serverDao.addOrUpdateServer(found)
                        ServerAddressBook.remember(context, found.id, found.url, first = true)
                        if (currentServer?.id == found.id) {
                            apiClient.update(baseUrl = found.url)
                            _current.update { it?.copy(server = found) }
                            _current.value?.let { mostRecentServerProvider.save(it) }
                            _addressChanged.tryEmit(found.url)
                        }
                    }
                    found
                }
            }

        private val findLock = kotlinx.coroutines.sync.Mutex()

        private val _addressChanged =
            kotlinx.coroutines.flow.MutableSharedFlow<String>(
                extraBufferCapacity = 4,
                onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
            )

        /** Benflix: the server address in use changed (for things that keep their own copy, like Seerr) */
        val addressChanged: kotlinx.coroutines.flow.SharedFlow<String> = _addressChanged

        /**
         * Benflix: checks the signed-in server still answers at the address in use, and switches to the best other
         * known address when it doesn't (or when a home address works and we're on the home Wi-Fi). Used when the
         * app comes back from the background and by the "taking a nap" screen. Returns true when the server answers.
         */
        suspend fun reconnect(): Boolean {
            val server = currentServer ?: return false
            val found = findMovedServer(server)
            return withContext(ioDispatcher) { ServerAddressBook.serverIdAt(found.url) == found.id }
        }

        private var networkCheck: kotlinx.coroutines.Job? = null

        /**
         * Benflix: when the phone or TV changes network (leaves home, joins the Wi-Fi, switches to mobile data),
         * check the server still answers at the address in use, and if not, switch to another known one
         * straight away, without waiting for the next app start.
         */
        private fun watchNetwork() {
            try {
                val cm =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                        ?: return
                val request =
                    android.net.NetworkRequest
                        .Builder()
                        .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()
                cm.registerNetworkCallback(
                    request,
                    object : android.net.ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: android.net.Network) = checkSoon()

                        override fun onLost(network: android.net.Network) = checkSoon()
                    },
                )
            } catch (e: Exception) {
                Timber.w(e, "Couldn't watch for network changes")
            }
        }

        private fun checkSoon() {
            networkCheck?.cancel()
            networkCheck =
                backgroundScope.launch {
                    // Let the new network settle first
                    kotlinx.coroutines.delay(4000)
                    if (currentServer == null) return@launch
                    reconnect()
                }
        }

        /** Benflix: remembers the address that just worked and asks Tower/GitHub for any others, in the background */
        private fun rememberWorking(server: JellyfinServer) {
            try {
                ServerAddressBook.remember(context, server.id, server.url, first = true)
            } catch (e: Exception) {
                Timber.w(e, "Couldn't remember the server address")
                return
            }
            backgroundScope.launch {
                try {
                    ServerAddressBook.refresh(context, server.id, server.url)
                } catch (e: Exception) {
                    Timber.w(e, "Couldn't refresh the address book")
                }
            }
        }

        /**
         * Given a successful [AuthenticationResult], switch to the user that just authenticated
         */
        suspend fun changeUser(
            serverUrl: String,
            authenticationResult: AuthenticationResult,
            existingUser: JellyfinUser?,
        ) = withContext(ioDispatcher) {
            com.github.damontecres.wholphin.services.RequestFor
                .clear()
            val accessToken = authenticationResult.accessToken
            if (accessToken != null) {
                val authedUser = authenticationResult.user
                val server =
                    authenticationResult.serverId?.toUUIDOrNull()?.let {
                        JellyfinServer(
                            id = it,
                            name = authedUser?.serverName,
                            url = serverUrl,
                            null,
                        )
                    }
                if (server != null) {
                    val user =
                        authedUser?.let {
                            if (existingUser != null) {
                                Timber.d("Re-using existing user")
                                existingUser.copy(
                                    // If the server authenticated via the server, always remove the PIN
                                    pin = null,
                                    accessToken = accessToken,
                                    lastUsed = ZonedDateTime.now(),
                                )
                            } else {
                                Timber.d("Creating new user")
                                JellyfinUser(
                                    id = it.id,
                                    name = it.name,
                                    serverId = server.id,
                                    accessToken = accessToken,
                                    lastUsed = ZonedDateTime.now(),
                                )
                            }
                        }
                    if (user != null) {
                        return@withContext changeUser(server, user)
                    } else {
                        throw IllegalArgumentException("Authentication result's user was null")
                    }
                } else {
                    throw IllegalArgumentException("Authentication result's serverId not valid: ${authenticationResult.serverId}")
                }
            } else {
                throw IllegalArgumentException("Authentication result's access token was null")
            }
        }

        suspend fun removeUser(user: JellyfinUser) {
            if (current.value?.user?.id == user.id) {
                withContext(WholphinDispatchers.Main) {
                    _current.value = null
                }
                mostRecentServerProvider.clearUser()
                apiClient.update(accessToken = null)
            }
            withContext(ioDispatcher) {
                serverDao.deleteUser(user.serverId, user.id)
            }
        }

        suspend fun removeServer(server: JellyfinServer) {
            if (current.value?.server?.id == server.id) {
                withContext(WholphinDispatchers.Main) {
                    _current.value = null
                }
                mostRecentServerProvider.clear()
                apiClient.update(baseUrl = null, accessToken = null)
            }
            withContext(ioDispatcher) {
                serverDao.deleteServer(server.id)
            }
        }

        suspend fun switchServerOrUser() {
            // Forget who was watching, but keep the server so the next launch opens straight to "Who's watching?"
            mostRecentServerProvider.clearUser()
        }

        suspend fun updateUserAuth(
            user: JellyfinUser,
            pin: String?,
            requireLogin: Boolean,
        ) {
            val newUser = user.copy(pin = pin, requireLogin = requireLogin)
            val updatedUser = serverDao.addOrUpdateUser(newUser)
            val cur = current.value
            if (cur?.user?.id == updatedUser.id && cur.server?.id == user.serverId) {
                // Updating current user, so push out the change
                current.value?.let {
                    val newCurrent = it.copy(user = updatedUser)
                    _current.value = newCurrent
                }
            }
        }

        suspend fun authorizeQuickConnect(code: String): Boolean =
            withContext(ioDispatcher) {
                val userId = current.value?.user?.id
                if (userId == null) {
                    Timber.e("No user logged in for Quick Connect authorization")
                    throw IllegalStateException("Must be logged in to authorize Quick Connect")
                }
                val response = apiClient.quickConnectApi.authorizeQuickConnect(code, userId)
                response.content
            }

        private val backgroundScope = CoroutineScope(SupervisorJob() + ioDispatcher)

        init {
            watchNetwork()
            // Benflix: the server stopped answering (the "taking a nap" screen)? Maybe it's just the address: look
            // for it at every other known address straight away, instead of retrying the same one forever.
            backgroundScope.launch {
                com.github.damontecres.wholphin.services.ServerNap.down.collect { down ->
                    if (down && currentServer != null) {
                        Timber.i("Server stopped answering, looking at other addresses")
                        try {
                            reconnect()
                        } catch (e: Exception) {
                            Timber.w(e, "Couldn't look for another address")
                        }
                    }
                }
            }
        }

        /**
         * Benflix: subtitles are English unless someone picked another language. A profile that never
         * picked one gets English saved on the server too, so the web player and every app agree.
         */
        private fun saveEnglishSubtitlesIfUnset(userDto: UserDto) {
            val config = userDto.configuration ?: return
            if (!config.subtitleLanguagePreference.isNullOrBlank()) return
            backgroundScope.launch {
                try {
                    val updated = config.copy(subtitleLanguagePreference = BENFLIX_SUBTITLE_LANGUAGE)
                    apiClient.userApi.updateUserConfiguration(userDto.id, updated)
                    updateUserDto(userDto.copy(configuration = updated))
                } catch (ex: Exception) {
                    Timber.w(ex, "Couldn't save English subtitles on the profile")
                }
            }
        }

        /**
         * Update [currentUserDto] by querying the server
         */
        suspend fun updateUserDto() {
            // Glowfin: nobody is signed in yet (the app is still starting), so there is nothing to refresh
            if (apiClient.baseUrl == null) return
            val userDto by apiClient.userApi.getCurrentUser()
            updateUserDto(userDto)
        }

        /**
         * Update [currentUserDto] with the specified [UserDto]
         *
         * This will only update if the [UserDto] is for the [currentUser]
         */
        fun updateUserDto(userDto: UserDto) {
            _currentUserDto.update {
                if (currentUser?.id == userDto.id) ServerUserConfig(userDto) else it
            }
        }
    }

@Serializable
data class CurrentUser(
    val server: JellyfinServer,
    val user: JellyfinUser,
)
