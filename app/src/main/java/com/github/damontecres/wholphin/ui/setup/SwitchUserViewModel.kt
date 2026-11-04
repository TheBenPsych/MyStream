package com.github.damontecres.wholphin.ui.setup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.WholphinApplication
import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.JellyfinServerDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.services.BenflixFunFacts
import com.github.damontecres.wholphin.services.BenflixParentPin
import com.github.damontecres.wholphin.services.BenflixSeerrLink
import com.github.damontecres.wholphin.services.DisplayPreferencesService
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.ProfileModes
import com.github.damontecres.wholphin.services.ProfilePictureSync
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.services.SetupNavigationManager
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.displayPreferencesApi
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.ServerVersion
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.QuickConnectDto
import org.jellyfin.sdk.model.api.QuickConnectResult
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

@HiltViewModel(assistedFactory = SwitchUserViewModel.Factory::class)
class SwitchUserViewModel
    @AssistedInject
    constructor(
        @param:ApplicationContext private val context: Context,
        val jellyfin: Jellyfin,
        val serverRepository: ServerRepository,
        val serverDao: JellyfinServerDao,
        val navigationManager: NavigationManager,
        val setupNavigationManager: SetupNavigationManager,
        val imageUrlService: ImageUrlService,
        private val funFacts: BenflixFunFacts,
        private val benflixSeerrLink: BenflixSeerrLink,
        private val profilePictureSync: ProfilePictureSync,
        private val parentPin: BenflixParentPin,
        @Assisted val server: JellyfinServer,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(server: JellyfinServer): SwitchUserViewModel
        }

        private val _state = MutableStateFlow(SwitchUserState())
        val state: StateFlow<SwitchUserState> = _state

        private var quickConnectJob: Job? = null

        fun clearSwitchUserState() {
            _state.update { it.copy(switchUserState = LoadingState.Pending) }
        }

        fun resetAttempts() {
            _state.update { it.copy(loginAttempts = 0) }
        }

        fun init() {
            viewModelScope.launchDefault {
                serverRepository.switchServerOrUser()
            }
            quickConnectJob?.cancel()
            viewModelScope.launchIO {
                _state.update { SwitchUserState() }
                try {
                    val serverUsers = getUsers()
                    val (serverVersion, supported) = checkServerVersion()
                    _state.update {
                        it.copy(
                            loading = LoadingState.Success,
                            users = serverUsers,
                            serverVersion = serverVersion,
                            serverVersionSupported = supported,
                        )
                    }
                    loadPosterWall(serverUsers.map { it.user })
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching users for server $server")
                    _state.update {
                        it.copy(
                            loading = LoadingState.Error(ex),
                        )
                    }
                }
            }

            viewModelScope.launchIO {
                try {
                    val quickConnect by
                        jellyfin
                            .createApi(
                                server.url,
                                httpClientOptions =
                                    HttpClientOptions(
                                        requestTimeout = 6.seconds,
                                        connectTimeout = 6.seconds,
                                        socketTimeout = 6.seconds,
                                    ),
                            ).quickConnectApi
                            .getQuickConnectEnabled()
                    _state.update { it.copy(quickConnectEnabled = quickConnect) }
                } catch (_: CancellationException) {
                    // no-op, user may have canceled
                } catch (ex: Exception) {
                    Timber.w(ex, "Error checking quick connect for server ${server.url}")
                    _state.update { it.copy(quickConnectEnabled = false) }
                }
            }
        }

        /**
         * Posters for the sliding wall behind "Who's watching?", fetched with a saved sign-in on this device.
         * Only titles rated PG-13 or lower, so nothing scary shows up for the kids.
         */
        private suspend fun loadPosterWall(users: List<JellyfinUser>) {
            val user = users.firstOrNull { it.accessToken != null && !it.requireLogin } ?: return
            // Benflix speed: the wall from earlier today is shown straight away (its pictures are already
            // on the device), and a new random wall is only fetched once a day. A new wall every start
            // meant 40 fresh pictures the server had to make, right while Home was trying to load.
            val wallPrefs = context.getSharedPreferences("benflix_poster_wall", Context.MODE_PRIVATE)
            val wallKey = "wall_${server.id}"
            val saved =
                wallPrefs
                    .getString(wallKey, null)
                    ?.split('\n')
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
            val savedAt = wallPrefs.getLong("${wallKey}_at", 0L)
            if (saved.isNotEmpty()) _state.update { it.copy(posterWall = saved) }
            val wallFresh = saved.isNotEmpty() && System.currentTimeMillis() - savedAt in 0 until POSTER_WALL_KEEP_MS
            try {
                val api = jellyfin.createApi(server.url, accessToken = user.accessToken)
                if (!wallFresh) loadNewPosterWall(api, user, wallPrefs, wallKey)
                // Benflix: a fun fact for the bottom of the screen
                _state.update { it.copy(funFact = funFacts.pick(api, user.id)) }
                funFacts.learnMore(api, user.id)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not load posters for the profile screen")
            }
        }

        private suspend fun loadNewPosterWall(
            api: org.jellyfin.sdk.api.client.ApiClient,
            user: JellyfinUser,
            wallPrefs: android.content.SharedPreferences,
            wallKey: String,
        ) {
            val posters =
                api.itemsApi
                    .getItems(
                        GetItemsRequest(
                            userId = user.id,
                            recursive = true,
                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            imageTypes = listOf(ImageType.PRIMARY),
                            sortBy = listOf(ItemSortBy.RANDOM),
                            maxOfficialRating = "PG-13",
                            hasParentalRating = true,
                            limit = POSTER_WALL_COUNT,
                        ),
                    ).content.items
                    .map { item ->
                        api.imageApi.getItemImageUrl(
                            itemId = item.id,
                            imageType = ImageType.PRIMARY,
                            fillHeight = POSTER_WALL_HEIGHT,
                            quality = 80,
                        )
                    }
            if (posters.isEmpty()) return
            // Only shown now when there was nothing saved; otherwise it's tomorrow's wall, so the
            // wall on screen doesn't jump while someone is picking a profile
            if (state.value.posterWall.isEmpty()) _state.update { it.copy(posterWall = posters) }
            wallPrefs
                .edit()
                .putString(wallKey, posters.joinToString("\n"))
                .putLong("${wallKey}_at", System.currentTimeMillis())
                .apply()
        }

        /**
         * Benflix: switch with Kids / Guest set the way the tile asked. They're on while signing in
         * (so nothing loads unfiltered), go back to how they were if it fails, and are saved on success.
         */
        private suspend fun changeUserWithModes(
            kids: Boolean,
            big: Boolean,
            guest: Boolean,
            change: suspend () -> CurrentUser,
        ): CurrentUser {
            val wasKids = KidsMode.isActive
            val wasBig = KidsMode.isBig
            val wasGuest = GuestMode.isActive
            ProfileModes.set(kids, big, guest)
            val current =
                try {
                    change()
                } catch (ex: Throwable) {
                    ProfileModes.set(wasKids, wasBig, wasGuest)
                    throw ex
                }
            ProfileModes.save(context, current.user.id)
            // Benflix: every sign-in (picking a profile, typing a password, or Quick Connect) starts on a
            // fresh Home. Before, typing a password kept the last person's open page, so a brand-new
            // profile could land on someone else's Requests page (which errors) and skip the welcome setup.
            navigationManager.reloadHome()
            return current
        }

        /**
         * Benflix: one parent PIN, bedtime and screen-time limit for the whole TV. Before Kids starts,
         * look at every saved grown-up on this TV (not just the one whose sign-in Kids borrows), so Kids
         * uses the newest PIN and limit any of them set. Anyone who can't be reached in a few seconds is
         * skipped; the TV's own copy still holds the last one it saw.
         */
        private suspend fun adoptParentalSettings() {
            val accounts =
                state.value.users
                    .map { it.user }
                    .filter { it.accessToken != null && !it.requireLogin }
                    .filterNot { it.name?.trim().equals("kids", ignoreCase = true) }
            if (accounts.isEmpty()) return
            val found = ConcurrentHashMap<Int, Map<String, String?>>()
            withTimeoutOrNull(PARENTAL_LOOKUP_MS) {
                coroutineScope {
                    accounts.forEachIndexed { index, user ->
                        launch {
                            try {
                                val prefs =
                                    jellyfin
                                        .createApi(server.url, accessToken = user.accessToken)
                                        .displayPreferencesApi
                                        .getDisplayPreferences(
                                            displayPreferencesId = DisplayPreferencesService.DEFAULT_DISPLAY_PREF_ID,
                                            userId = user.id,
                                            client = DisplayPreferencesService.DEFAULT_CLIENT,
                                        ).content
                                        .customPrefs
                                found[index] = prefs
                            } catch (ex: CancellationException) {
                                throw ex
                            } catch (ex: Exception) {
                                Timber.w(ex, "Couldn't read parental settings for a saved grown-up")
                            }
                        }
                    }
                }
            }
            // In the order the profiles are listed, so with older PINs (saved without a time) the first one wins
            accounts.forEachIndexed { index, user -> found[index]?.let { parentPin.adoptFrom(user.id, it) } }
        }

        fun trySwitchUser(
            user: JellyfinUser,
            kids: Boolean = false,
            bigKids: Boolean = false,
            guest: Boolean = false,
        ): Deferred<SwitchUserResult> =
            viewModelScope.async(WholphinDispatchers.IO) {
                try {
                    if (kids) adoptParentalSettings()
                    val current =
                        changeUserWithModes(kids, bigKids, guest) {
                            serverRepository.changeUser(server, user)
                        }
                    setupNavigationManager.navigateTo(SetupDestination.AppContent(current))
                    SwitchUserResult.Success
                } catch (ex: InvalidStatusException) {
                    when (ex.status) {
                        401 -> {
                            Timber.w(ex, "Error switching user 401")
                            SwitchUserResult.Error(
                                context.getString(R.string.login_credentials_expired),
                                true,
                            )
                        }

                        403 -> {
                            Timber.w(ex, "Error switching user 403")
                            SwitchUserResult.Error(
                                context.getString(R.string.login_not_authorized),
                                false,
                            )
                        }

                        else -> {
                            Timber.e(ex, "Error switching user")
                            SwitchUserResult.Error(ex.localizedMessage, true)
                        }
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error switching user")
                    SwitchUserResult.Error(ex.localizedMessage, true)
                }
            }

        fun login(
            server: JellyfinServer,
            existingUser: JellyfinUser?,
            username: String,
            password: String,
        ) {
            quickConnectJob?.cancel()
            viewModelScope.launchIO {
                try {
                    val api = jellyfin.createApi(baseUrl = server.url)
                    val authenticationResult by api.userApi.authenticateUserByName(
                        username = username,
                        password = password,
                    )
                    // Benflix: keep the password in memory so Requests (Seerr) can sign them in too
                    authenticationResult.user?.id?.let { benflixSeerrLink.rememberPassword(it, password) }
                    val current =
                        changeUserWithModes(kids = false, big = false, guest = false) {
                            serverRepository.changeUser(server.url, authenticationResult, existingUser)
                        }
                    setupNavigationManager.navigateTo(SetupDestination.AppContent(current))
                } catch (ex: Exception) {
                    Timber.e(ex, "Error logging in user")
                    if (ex is InvalidStatusException && ex.status == 401) {
                        _state.update { it.copy(switchUserState = LoadingState.Error("Invalid username or password")) }
                    } else {
                        setError("Error during login", ex)
                    }
                }
            }
        }

        fun initiateQuickConnect(
            server: JellyfinServer,
            existingUser: JellyfinUser?,
        ) {
            quickConnectJob?.cancel()
            quickConnectJob =
                viewModelScope.launchIO {
                    try {
                        val api = jellyfin.createApi(server.url)
                        var quickConnectStatus =
                            api
                                .quickConnectApi
                                .initiateQuickConnect()
                                .content
                        _state.update { it.copy(quickConnectStatus = quickConnectStatus) }

                        var networkFailures = 0
                        while (!quickConnectStatus.authenticated) {
                            delay(5_000L)
                            quickConnectStatus =
                                try {
                                    api.quickConnectApi
                                        .getQuickConnectState(
                                            secret = quickConnectStatus.secret,
                                        ).content
                                        .also { networkFailures = 0 }
                                } catch (ex: CancellationException) {
                                    throw ex
                                } catch (ex: InvalidStatusException) {
                                    throw ex
                                } catch (ex: Exception) {
                                    // Phones often cut network for apps in the background while you
                                    // approve the code in another app, so keep trying for a while
                                    networkFailures++
                                    if (networkFailures > MAX_QUICK_CONNECT_NETWORK_FAILURES) throw ex
                                    Timber.w(ex, "Quick Connect check failed, retrying")
                                    continue
                                }
                            _state.update { it.copy(quickConnectStatus = quickConnectStatus) }
                        }
                        val authenticationResult by api.userApi.authenticateWithQuickConnect(
                            QuickConnectDto(secret = quickConnectStatus.secret),
                        )
                        val current =
                            changeUserWithModes(kids = false, big = false, guest = false) {
                                serverRepository.changeUser(
                                    server.url,
                                    authenticationResult,
                                    existingUser,
                                )
                            }
                        setupNavigationManager.navigateTo(SetupDestination.AppContent(current))
                    } catch (_: CancellationException) {
                        // no-op, user may have canceled
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error during quick connect")
                        if (ex is InvalidStatusException && ex.status == 401) {
                            _state.update {
                                it.copy(
                                    quickConnectEnabled = false,
                                    quickConnectStatus = null,
                                )
                            }
                        }
                        setError("Error with Quick Connect", ex)
                    }
                }
        }

        fun cancelQuickConnect() {
            quickConnectJob?.cancel()
            _state.update {
                it.copy(
                    quickConnectStatus = null,
                )
            }
        }

        /** Save a picked profile picture here and on their Jellyfin account, then show the new one */
        fun pickPicture(
            user: JellyfinUser,
            index: Int?,
        ) {
            viewModelScope.launchIO {
                if (profilePictureSync.pick(server.url, user, index)) {
                    try {
                        val serverUsers = getUsers()
                        _state.update { it.copy(users = serverUsers) }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not refresh the profiles")
                    }
                }
            }
        }

        fun removeUser(user: JellyfinUser) {
            viewModelScope.launchIO {
                serverRepository.removeUser(user)
                val serverUsers = getUsers()
                _state.update { it.copy(users = serverUsers) }
            }
        }

        private suspend fun getUsers(): List<JellyfinUserAndImage> =
            withContext(WholphinDispatchers.IO) {
                val api = jellyfin.createApi(server.url)
                val publicList =
                    try {
                        api.userApi.getPublicUsers().content
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not load public profiles")
                        emptyList()
                    }
                val publicById = publicList.associateBy { it.id }

                // Jellyfin's picture for this person; the tag changes when the picture does, so a new
                // picture (say, picked on another TV) shows up instead of the old saved copy
                fun imageUrlFor(id: UUID): String? {
                    val dto = publicById[id] ?: return api.imageApi.getUserImageUrl(id)
                    val tag = dto.primaryImageTag ?: return null
                    return api.imageApi.getUserImageUrl(id, tag = tag)
                }
                val knownUsers =
                    serverDao
                        .getServer(server.id)
                        ?.users
                        .orEmpty()
                        .map {
                            JellyfinUserAndImage(
                                user = it,
                                imageUrl = imageUrlFor(it.id),
                                known = true,
                            )
                        }
                val knownUserIds = knownUsers.map { it.user.id }
                val publicUsers =
                    publicList
                        .map {
                            JellyfinUser(
                                id = it.id,
                                name = it.name,
                                serverId = server.id,
                                accessToken = null,
                            )
                        }.filter { it.id !in knownUserIds }
                        .map {
                            JellyfinUserAndImage(
                                user = it,
                                imageUrl = imageUrlFor(it.id),
                                known = false,
                            )
                        }

                knownUsers + publicUsers
            }

        private suspend fun checkServerVersion(): Pair<ServerVersion?, ServerVersionSupported> {
            val api = jellyfin.createApi(server.url)
            val systemInfo by api.systemApi.getPublicSystemInfo()
            val serverVersion = systemInfo.version?.let { ServerVersion.fromString(it) }
            return if (serverVersion == null) {
                serverVersion to ServerVersionSupported.UNKNOWN
            } else if (serverVersion < WholphinApplication.minimumServerVersion) {
                serverVersion to ServerVersionSupported.NOT_SUPPORTED
            } else if (serverVersion < WholphinApplication.newMinimumServerVersion) {
                serverVersion to ServerVersionSupported.NOT_SUPPORTED_IN_FUTURE
            } else {
                serverVersion to ServerVersionSupported.SUPPORTED
            }
        }

        private fun setError(
            msg: String? = null,
            ex: Exception? = null,
        ) {
            _state.update {
                it.copy(
                    loginAttempts = it.loginAttempts + 1,
                    switchUserState = LoadingState.Error(msg, ex),
                )
            }
        }
    }

data class JellyfinUserAndImage(
    val user: JellyfinUser,
    val imageUrl: String?,
    val known: Boolean,
)

data class SwitchUserState(
    // LoadingState for fetching available users
    val loading: LoadingState = LoadingState.Pending,
    val quickConnectEnabled: Boolean = false,
    val quickConnectStatus: QuickConnectResult? = null,
    val users: List<JellyfinUserAndImage> = emptyList(),
    // LoadingState for while adding/switching users
    val switchUserState: LoadingState = LoadingState.Pending,
    val loginAttempts: Int = 0,
    val serverVersion: ServerVersion? = null,
    val serverVersionSupported: ServerVersionSupported = ServerVersionSupported.UNKNOWN,
    // Poster image addresses for the sliding wall behind the profiles
    val posterWall: List<String> = emptyList(),
    // A "Did you know?" fact for the bottom of the screen
    val funFact: String? = null,
)

private const val POSTER_WALL_COUNT = 12

/** Benflix speed: a new random poster wall once a day */
private const val POSTER_WALL_KEEP_MS = 24L * 60 * 60 * 1000
private const val POSTER_WALL_HEIGHT = 260

/** How long Kids waits to read the other grown-ups' parental settings before it starts anyway */
private const val PARENTAL_LOOKUP_MS = 4_000L

/**
 * Result of trying to switch users via [SwitchUserViewModel.trySwitchUser]
 */
sealed interface SwitchUserResult {
    data object Success : SwitchUserResult

    data class Error(
        val errorMessage: String?,
        val showLogin: Boolean,
    ) : SwitchUserResult
}

/** About 5 minutes of failed checks (one every 5 seconds) before Quick Connect gives up */
private const val MAX_QUICK_CONNECT_NETWORK_FAILURES = 60
