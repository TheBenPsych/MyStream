package com.github.damontecres.wholphin.ui.nav

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavDrawerService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.services.SetupNavigationManager
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.preferences.PreferenceScreenOption
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class NavDrawerViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val navDrawerService: NavDrawerService,
        val navigationManager: NavigationManager,
        val setupNavigationManager: SetupNavigationManager,
        val backdropService: BackdropService,
        private val musicService: MusicService,
        private val imageUrlService: ImageUrlService,
    ) : ViewModel() {
        val serviceState = navDrawerService.state

        /** Browse menu cards: a backdrop from one of each library's titles, and how many titles it has */
        private val _libraryArt = MutableStateFlow<Map<UUID, LibraryArt>>(emptyMap())
        val libraryArt: StateFlow<Map<UUID, LibraryArt>> = _libraryArt
        private var libraryArtJob: Job? = null

        /** Benflix: Surprise me (in the More menu): opens a random unwatched movie or show */
        fun surpriseMe(context: android.content.Context) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                val item =
                    try {
                        api.itemsApi
                            .getItems(
                                GetItemsRequest(
                                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                    recursive = true,
                                    isPlayed = false,
                                    sortBy = listOf(ItemSortBy.RANDOM),
                                    limit = 1,
                                ),
                            ).content.items
                            .firstOrNull()
                    } catch (e: Exception) {
                        timber.log.Timber.w(e, "Surprise me failed")
                        null
                    }
                if (item != null) {
                    kotlinx.coroutines.withContext(WholphinDispatchers.Main) {
                        navigationManager.navigateTo(
                            com.github.damontecres.wholphin.data.model
                                .BaseItem(item)
                                .destination(),
                        )
                    }
                } else {
                    com.github.damontecres.wholphin.ui
                        .showToast(context, "Nothing unwatched to pick from")
                }
            }
        }

        fun loadLibraryArt(libraries: List<ServerNavDrawerItem>) {
            val missing = libraries.filter { it.itemId !in _libraryArt.value }
            if (missing.isEmpty() || libraryArtJob?.isActive == true) return
            val kids = KidsMode.isActive
            libraryArtJob =
                viewModelScope.launch(WholphinDispatchers.IO) {
                    missing.forEach { library ->
                        val art =
                            try {
                                val types =
                                    when (library.type) {
                                        CollectionType.MOVIES -> listOf(BaseItemKind.MOVIE)
                                        CollectionType.TVSHOWS -> listOf(BaseItemKind.SERIES)
                                        CollectionType.MUSIC -> listOf(BaseItemKind.MUSIC_ALBUM)
                                        else -> listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
                                    }
                                val count =
                                    api.itemsApi
                                        .getItems(
                                            GetItemsRequest(
                                                parentId = library.itemId,
                                                recursive = true,
                                                includeItemTypes = types,
                                                limit = 0,
                                                enableTotalRecordCount = true,
                                            ),
                                        ).content.totalRecordCount
                                // Kids get the plain coloured card, so no grown-up artwork sneaks in
                                val pick =
                                    if (kids) {
                                        null
                                    } else {
                                        api.itemsApi
                                            .getItems(
                                                GetItemsRequest(
                                                    parentId = library.itemId,
                                                    recursive = true,
                                                    includeItemTypes = types,
                                                    imageTypes = listOf(ImageType.BACKDROP),
                                                    sortBy = listOf(ItemSortBy.RANDOM),
                                                    limit = 1,
                                                    enableTotalRecordCount = false,
                                                ),
                                            ).content.items
                                            .firstOrNull()
                                    }
                                val url =
                                    when {
                                        pick != null -> {
                                            imageUrlService.getItemImageUrl(
                                                itemId = pick.id,
                                                imageType = ImageType.BACKDROP,
                                                fillWidth = 400,
                                                fillHeight = 225,
                                            )
                                        }

                                        kids -> {
                                            null
                                        }

                                        else -> {
                                            imageUrlService.getItemImageUrl(
                                                itemId = library.itemId,
                                                imageType = ImageType.PRIMARY,
                                                fillWidth = 400,
                                                fillHeight = 225,
                                            )
                                        }
                                    }
                                LibraryArt(url, count)
                            } catch (ex: CancellationException) {
                                throw ex
                            } catch (ex: Exception) {
                                Timber.w(ex, "Browse art for %s", library.name)
                                LibraryArt(null, null)
                            }
                        _libraryArt.update { it + (library.itemId to art) }
                    }
                }
        }

        private val _state = MutableStateFlow(NavDrawerState())
        val state: StateFlow<NavDrawerState> = _state

        fun onClickDrawerItem(
            index: Int,
            item: NavDrawerItem,
        ) {
            when (item) {
                NavDrawerItem.Favorites -> {
                    setIndex(index)
                    navigationManager.navigateToFromDrawer(
                        Destination.Favorites,
                    )
                }

                NavDrawerItem.More -> {
                    setShowMore(!state.value.moreExpanded)
                }

                is ServerNavDrawerItem -> {
                    setIndex(index)
                    navigationManager.navigateToFromDrawer(item.destination)
                }
            }
        }

        fun setIndex(index: Int) {
            _state.update { it.copy(selectedIndex = index) }
        }

        fun setShowMore(value: Boolean) {
            _state.update { it.copy(moreExpanded = value) }
        }

        fun getUserImage(user: JellyfinUser): String = api.imageApi.getUserImageUrl(user.id)

        /**
         * Determine which nav drawer item should be highlighted as currently selected
         */
        fun updateSelectedIndex() {
            viewModelScope.launchDefault {
                val asDestinations =
                    buildList {
                        addAll(serviceState.value.items)
                        addAll(serviceState.value.moreItems)
                    }.map {
                        when (it) {
                            is ServerNavDrawerItem -> it.destination
                            is NavDrawerItem.Favorites -> Destination.Favorites
                            else -> null
                        }
                    }

                val backstack = navigationManager.backStack.toList().reversed()
                for (i in 0..<backstack.size) {
                    val key = backstack[i]
                    val index =
                        if (key is Destination.Home) {
                            HOME_INDEX
                        } else if (key is Destination.Search) {
                            SEARCH_INDEX
                        } else if (key is Destination.NowPlaying) {
                            NOW_PLAYING_INDEX
                        } else if (key is Destination.Games) {
                            GAMES_INDEX
                        } else if (key is Destination.MyBenflix) {
                            MY_BENFLIX_INDEX
                        } else if (key is Destination.Channels) {
                            CHANNELS_INDEX
                        } else if (key is Destination.Requests) {
                            REQUESTS_INDEX
                        } else {
                            val idx = asDestinations.indexOf(key)
                            if (idx >= 0) {
                                idx
                            } else {
                                null
                            }
                        }
                    Timber.v("Found $index => $key")
                    if (index != null) {
                        _state.update { it.copy(selectedIndex = index) }
                        break
                    }
                }
            }
        }

        fun navigateToSetup(userList: SetupDestination) {
            viewModelScope.launchDefault {
                musicService.stop()
                setupNavigationManager.navigateTo(userList)
            }
        }
    }

data class NavDrawerState(
    val selectedIndex: Int = -1,
    val moreExpanded: Boolean = false,
)

/**
 * An item that can be shown in the nav drawer
 *
 * Some are built-in such as Favorites. Others are created dynamically for libraries.
 */
sealed interface NavDrawerItem {
    val id: String

    fun name(context: Context): String

    object Favorites : NavDrawerItem {
        override val id: String
            get() = "a_favorites"

        override fun name(context: Context): String = context.getString(R.string.favorites)
    }

    object More : NavDrawerItem {
        override val id: String
            get() = "a_more"

        override fun name(context: Context): String = context.getString(R.string.more)
    }
}

/** A Browse card's picture and how many titles the library has */
data class LibraryArt(
    val imageUrl: String?,
    val count: Int?,
)

/**
 * A server provided nav drawer item, typically a library
 */
data class ServerNavDrawerItem(
    val itemId: UUID,
    val name: String,
    val destination: Destination,
    val type: CollectionType,
) : NavDrawerItem {
    override val id: String = getId(itemId)

    override fun name(context: Context): String = name

    companion object {
        fun getId(itemId: UUID) = "s_" + itemId.toServerString()
    }
}

private const val HOME_INDEX = -1
private const val SEARCH_INDEX = -2
private const val NOW_PLAYING_INDEX = -3
private const val GAMES_INDEX = -4
private const val MY_BENFLIX_INDEX = -5
private const val CHANNELS_INDEX = -6
private const val REQUESTS_INDEX = -7

/**
 * Display the top bar with [DestinationContent] below it.
 *
 * Benflix redesign: there are no tabs any more. Home has four doors (Movies, Shows, Live TV, Games),
 * and the bar only has the logo (plus the door's name inside a door), a big clock, search and your
 * picture, which opens the profile menu. Phones get the same header and no bottom menu.
 */
@Composable
fun NavDrawer(
    destination: Destination,
    preferences: UserPreferences,
    user: JellyfinUser,
    server: JellyfinServer,
    drawerState: DrawerState,
    navDrawerListState: LazyListState,
    onClearBackdrop: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NavDrawerViewModel =
        hiltViewModel(
            LocalView.current.findViewTreeViewModelStoreOwner()!!,
            // Keyed to the server, user & Kids level to ensure its reset when switching any of them
            key = "${server.id}_${user.id}_${KidsMode.level}",
        ),
    menuViewModel: ProfileMenuViewModel = hiltViewModel(),
) {
    val focusRequester = remember { FocusRequester() }

    // If the user presses back while on the home page, move to the top bar, another back press will quit the app
    var topBarFocused by remember { mutableStateOf(false) }
    // (TV only: phones have no top bar, so Back on Home just leaves the app like any other phone app)
    val isTv = rememberIsTv()
    BackHandler(enabled = (isTv && !topBarFocused && destination is Destination.Home)) {
        focusRequester.tryRequestFocus()
    }
    val userImageUrl = remember(user) { viewModel.getUserImage(user) }
    // Benflix: requests waiting for the main profile to say yes or no
    hiltViewModel<RequestsBadgeViewModel>(key = "requests-badge-${user.id}")
    val requestsWaiting by RequestsWaiting.count.collectAsState()
    val kids by KidsMode.active.collectAsState()
    val guest by com.github.damontecres.wholphin.services.GuestMode.active
        .collectAsState()
    val easy = kids || guest || preferences.appPreferences.benflixPreferences.easyRead
    val updateAlert =
        com.github.damontecres.wholphin.ui.components
            .rememberUpdateAlert()
    val serverAlert =
        com.github.damontecres.wholphin.ui.components
            .rememberServerAlert()
    val readyCount =
        com.github.damontecres.wholphin.ui.components
            .rememberRequestReadyCount()
    // A bigger white dot on your picture when something needs you
    val badge = !kids && !guest && (updateAlert || serverAlert || readyCount > 0 || requestsWaiting > 0)

    var showMenu by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    val reportViewModel: com.github.damontecres.wholphin.services.ReportProblemViewModel = hiltViewModel()
    // Phones: Downloads (saved on this phone) covers the page until closed
    var downloadsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(destination) { downloadsOpen = false }

    val onClickSearch = {
        viewModel.navigationManager.navigateTo(Destination.Search())
    }
    val onClickProfile = {
        if (easy) {
            // Kids, Guest and Easy mode: the picture goes straight to Who's watching
            viewModel.navigateToSetup(SetupDestination.UserList(server))
        } else {
            menuViewModel.load()
            showMenu = true
        }
    }
    val menuItems =
        buildList {
            fun go(d: Destination) {
                showMenu = false
                viewModel.navigationManager.navigateTo(d)
            }
            add(
                ProfileMenuItem(
                    icon = com.github.damontecres.wholphin.ui.components.BenflixIcon.REQUESTS,
                    title = stringResource(R.string.benflix_requests),
                    onClick = { go(Destination.Requests) },
                    note =
                        when {
                            requestsWaiting > 0 -> "$requestsWaiting waiting"
                            readyCount > 0 -> "$readyCount ready"
                            else -> null
                        },
                ),
            )
            add(
                ProfileMenuItem(
                    icon = com.github.damontecres.wholphin.ui.components.BenflixIcon.STATS,
                    title = "My watching",
                    onClick = { go(Destination.MyBenflix) },
                ),
            )
            if (!isTv) {
                add(
                    ProfileMenuItem(
                        icon = com.github.damontecres.wholphin.ui.components.BenflixIcon.DOWNLOAD,
                        title = "Downloads",
                        onClick = {
                            showMenu = false
                            downloadsOpen = true
                        },
                    ),
                )
            }
            add(
                ProfileMenuItem(
                    icon = com.github.damontecres.wholphin.ui.components.BenflixIcon.GEAR,
                    title = stringResource(R.string.settings),
                    onClick = { go(Destination.Settings(PreferenceScreenOption.BASIC)) },
                ),
            )
            if (menuViewModel.isAdmin) {
                add(
                    ProfileMenuItem(
                        icon = com.github.damontecres.wholphin.ui.components.BenflixIcon.SERVER,
                        title = "Server",
                        onClick = { go(Destination.ServerStatus) },
                        note = if (serverAlert) "Check" else null,
                    ),
                )
            }
            add(
                ProfileMenuItem(
                    icon = com.github.damontecres.wholphin.ui.components.BenflixIcon.HELP,
                    title = "Help and updates",
                    onClick = { go(Destination.UpdateApp) },
                    note = if (updateAlert) "Update ready" else null,
                ),
            )
            add(
                ProfileMenuItem(
                    icon = com.github.damontecres.wholphin.ui.components.BenflixIcon.FLAG,
                    title = "Report a problem",
                    onClick = {
                        showMenu = false
                        showReport = true
                    },
                ),
            )
            add(
                ProfileMenuItem(
                    icon = com.github.damontecres.wholphin.ui.components.BenflixIcon.SIGN_OUT,
                    title = "Sign out",
                    onClick = {
                        showMenu = false
                        confirmSignOut = true
                    },
                    apart = true,
                ),
            )
        }
    if (showMenu) {
        val minutes by menuViewModel.minutes.collectAsState()
        ProfileMenu(
            user = user,
            userImageUrl = userImageUrl,
            minutesWatched = minutes,
            items = menuItems,
            onSwitchProfile = {
                showMenu = false
                viewModel.navigateToSetup(SetupDestination.UserList(server))
            },
            onDismissRequest = { showMenu = false },
        )
    }
    if (showReport) {
        com.github.damontecres.wholphin.ui.components.DialogPopup(
            showDialog = true,
            title = "Report a problem",
            dialogItems =
                APP_PROBLEMS.map { problem ->
                    com.github.damontecres.wholphin.ui.components.DialogItem(
                        headlineContent = { androidx.tv.material3.Text(problem) },
                        onClick = {
                            showReport = false
                            reportViewModel.report(null, "Benflix app", problem)
                        },
                    )
                },
            onDismissRequest = { showReport = false },
            waitToLoad = false,
        )
    }
    if (confirmSignOut) {
        com.github.damontecres.wholphin.ui.components.ConfirmDialog(
            title = "Sign out of Benflix?",
            body = "You'll need your password to sign in again on this ${if (isTv) "TV" else "phone"}.",
            onCancel = { confirmSignOut = false },
            onConfirm = {
                confirmSignOut = false
                menuViewModel.signOut()
            },
        )
    }

    if (!isTv) {
        Column(modifier = modifier) {
            if (downloadsOpen) {
                com.github.damontecres.wholphin.ui.downloads.DownloadsPage(
                    onClose = { downloadsOpen = false },
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                )
            } else {
                BenflixPhoneHeader(
                    door = destination.door(),
                    user = user,
                    userImageUrl = userImageUrl,
                    badge = badge,
                    onClickSearch = onClickSearch,
                    onClickProfile = onClickProfile,
                )
                DestinationContent(
                    destination = destination,
                    preferences = preferences,
                    onClearBackdrop = onClearBackdrop,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp),
                )
            }
        }
        return
    }
    Column(
        modifier =
            modifier.onPreviewKeyEvent { event ->
                // Holding the remote's Menu button also opens the profile menu
                val native = event.nativeKeyEvent
                if (native.keyCode == android.view.KeyEvent.KEYCODE_MENU &&
                    native.action == android.view.KeyEvent.ACTION_DOWN &&
                    native.repeatCount == MENU_HOLD_REPEATS
                ) {
                    onClickProfile()
                    true
                } else {
                    false
                }
            },
    ) {
        BenflixTopBar(
            door = destination.door(),
            user = user,
            userImageUrl = userImageUrl,
            showClock = preferences.appPreferences.interfacePreferences.showClock,
            badge = badge,
            searchSelected = destination is Destination.Search,
            onClickSearch = onClickSearch,
            onClickProfile = onClickProfile,
            profileFocusRequester = focusRequester,
            big = easy,
            modifier = Modifier.onFocusChanged { topBarFocused = it.hasFocus },
        )
        DestinationContent(
            destination = destination,
            preferences = preferences,
            onClearBackdrop = onClearBackdrop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = 40.dp, end = 16.dp),
        )
    }
}

/** "Report a problem" from the profile menu, about the app itself */
private val APP_PROBLEMS =
    listOf(
        "Something won't play",
        "The app is slow",
        "The app closed by itself",
        "Something looks wrong",
        "Something else",
    )

/** Holding Menu: Android repeats the key while held; this many repeats is about half a second */
private const val MENU_HOLD_REPEATS = 10

val DrawerState.isOpen: Boolean get() = this.currentValue.isOpen

val DrawerValue.isOpen: Boolean get() = this == DrawerValue.Open
