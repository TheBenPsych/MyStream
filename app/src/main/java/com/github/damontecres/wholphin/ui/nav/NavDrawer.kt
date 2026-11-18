package com.github.damontecres.wholphin.ui.nav

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
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
import com.github.damontecres.wholphin.ui.music.isMusicPage
import com.github.damontecres.wholphin.ui.preferences.PreferenceScreenOption
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
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
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
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
        private val musicLibrary: com.github.damontecres.wholphin.services.MusicLibrary,
    ) : ViewModel() {
        /** A short Play/Pause on a door (no poster picked): plays or pauses the music that's waiting */
        fun musicTap() {
            if (musicService.state.value.queueSize == 0) return
            val p = musicService.player
            if (p.isPlaying) p.pause() else p.play()
        }

        /**
         * Music redesign 3: holding Play/Pause on a TV door. Paused music carries on; with no music waiting, the
         * top quick pick (Liked songs, shuffled) starts. Never on Kids, which has no music.
         */
        fun musicHold(kids: Boolean) {
            if (kids) return
            viewModelScope.launch(WholphinDispatchers.IO) {
                if (musicService.state.value.queueSize > 0) {
                    com.github.damontecres.wholphin.ui
                        .onMain { musicService.player.play() }
                    com.github.damontecres.wholphin.ui.components.BenflixNotices
                        .pill("Music carries on")
                    return@launch
                }
                val liked =
                    try {
                        musicLibrary.likedSongs()
                    } catch (ex: Exception) {
                        Timber.w(ex, "Hold Play: Liked songs")
                        emptyList()
                    }
                if (liked.isEmpty()) {
                    com.github.damontecres.wholphin.ui.components.BenflixNotices
                        .pill("Like a few songs in Music first, then hold Play to start them")
                    return@launch
                }
                musicService.playList(liked, 0, true, null, "Liked songs")
                com.github.damontecres.wholphin.ui.components.BenflixNotices
                    .pill("Playing your Liked songs")
            }
        }

        val serviceState = navDrawerService.state

        /** Browse menu cards: a backdrop from one of each library's titles, and how many titles it has */
        private val _libraryArt = MutableStateFlow<Map<UUID, LibraryArt>>(emptyMap())
        val libraryArt: StateFlow<Map<UUID, LibraryArt>> = _libraryArt
        private var libraryArtJob: Job? = null

        /**
         * Benflix: the remote's Play button on a poster. Movies and episodes start where you left off; a show
         * starts its next episode (or the first one); anything else opens its page like OK does.
         */
        fun quickPlay(item: com.github.damontecres.wholphin.data.model.BaseItem) {
            if (item.type in com.github.damontecres.wholphin.util.supportedPlayableTypes) {
                navigationManager.navigateTo(Destination.Playback(item))
                return
            }
            if (item.type != BaseItemKind.SERIES) {
                navigationManager.navigateTo(item.destination())
                return
            }
            viewModelScope.launch(WholphinDispatchers.IO) {
                val next =
                    try {
                        com.github.damontecres.wholphin.services.NextEpisode
                            .find(api, item.id)
                            ?: api.tvShowsApi
                                .getNextUp(
                                    org.jellyfin.sdk.model.api.request.GetNextUpRequest(
                                        seriesId = item.id,
                                        limit = 1,
                                        enableResumable = true,
                                    ),
                                ).content.items
                                .firstOrNull()
                            ?: api.tvShowsApi
                                .getEpisodes(
                                    org.jellyfin.sdk.model.api.request.GetEpisodesRequest(
                                        seriesId = item.id,
                                        limit = 1,
                                        enableImages = false,
                                    ),
                                ).content.items
                                .firstOrNull()
                    } catch (e: Exception) {
                        Timber.w(e, "Play button: no episode for %s", item.id)
                        null
                    }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (next != null) {
                        val ticks = next.userData?.playbackPositionTicks ?: 0L
                        navigationManager.navigateTo(Destination.Playback(next.id, ticks / 10_000L))
                    } else {
                        navigationManager.navigateTo(item.destination())
                    }
                }
            }
        }

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

    // Ben: Back on Home (TV) says "Press Back again to leave Benflix"; a second Back within 3 seconds leaves.
    // (It used to jump the focus up to your picture, which felt random.)
    var topBarFocused by remember { mutableStateOf(false) }
    // (TV only: phones leave on Back like any other phone app)
    val isTv = rememberIsTv()
    var leaveArmed by remember { mutableStateOf(false) }
    LaunchedEffect(leaveArmed) {
        if (leaveArmed) {
            delay(LEAVE_AGAIN_MS)
            leaveArmed = false
        }
    }
    BackHandler(enabled = (isTv && !leaveArmed && destination is Destination.Home)) {
        leaveArmed = true
        com.github.damontecres.wholphin.ui.components.BenflixNotices
            .pill("Press Back again to leave Glowfin")
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
    // Notices redesign (2026-11-10): the white dot on your picture only means "news on Home". Updates are a Home
    // row, server problems live on the Server page, and the menu still says "Update ready", "3 waiting" and so on.
    val homeNews by com.github.damontecres.wholphin.ui.components.HomeNewsDot.on
        .collectAsState()
    val badge = !kids && !guest && homeNews

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
            // Requests has its own spot on the rail and in the phone's More sheet, so it's not repeated here
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
            if (badge) {
                add(
                    ProfileMenuItem(
                        icon = com.github.damontecres.wholphin.ui.components.BenflixIcon.CHECK,
                        title = "Mark all as seen",
                        onClick = {
                            showMenu = false
                            menuViewModel.markAllSeen()
                            com.github.damontecres.wholphin.ui.components.HomeNewsDot
                                .set(false)
                        },
                    ),
                )
            }
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
                            reportViewModel.report(null, "Glowfin app", problem)
                        },
                    )
                },
            onDismissRequest = { showReport = false },
            waitToLoad = false,
        )
    }
    if (confirmSignOut) {
        com.github.damontecres.wholphin.ui.components.ConfirmDialog(
            title = "Sign out of Glowfin?",
            body = "You'll need your password to sign in again on this ${if (isTv) "TV" else "phone"}.",
            onCancel = { confirmSignOut = false },
            onConfirm = {
                confirmSignOut = false
                menuViewModel.signOut()
            },
        )
    }

    // Navigation redesign (Nov 2026): the doors the rail and the phone's bar show for this profile
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        com.github.damontecres.wholphin.ui.door.DoorPlaces
            .load(context)
    }
    val railDoors =
        remember(kids, guest) {
            when {
                kids -> listOf(BenflixDoor.MOVIES, BenflixDoor.SHOWS, BenflixDoor.GAMES)
                guest -> listOf(BenflixDoor.MOVIES, BenflixDoor.SHOWS, BenflixDoor.LIVE_TV, BenflixDoor.GAMES)
                else -> BenflixDoor.entries.toList()
            }
        }
    val nav = viewModel.navigationManager
    val stack = nav.backStack.toList()
    val goPlace: (BenflixDoor, String) -> Unit = { door, key ->
        val frame =
            com.github.damontecres.wholphin.ui.door.DoorPlaces
                .frame(door)
        if (frame != null && destination.isDoorRoot() && destination.door() == door) {
            if (frame.section == key && key == com.github.damontecres.wholphin.ui.door.DOOR_HOME) frame.toTop() else frame.pick(key)
        } else {
            com.github.damontecres.wholphin.ui.main.DoorUse
                .opened(context, user.id, door)
            if (key != com.github.damontecres.wholphin.ui.door.DOOR_HOME) {
                com.github.damontecres.wholphin.ui.door.DoorPlaces
                    .openLater(door, key)
            }
            nav.navigateToFromDrawer(door.destination())
        }
    }
    val profileItems = if (easy) null else menuItems
    val minutes by menuViewModel.minutes.collectAsState()
    val onProfileTap = {
        if (easy) {
            viewModel.navigateToSetup(SetupDestination.UserList(server))
        } else {
            menuViewModel.load()
        }
    }
    val onSwitchProfile = { viewModel.navigateToSetup(SetupDestination.UserList(server)) }
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    // Phones turned sideways and tablets use the TV layout, with the rail always open (approved ix-sideways)
    val wide = isTv || config.screenWidthDp > config.screenHeightDp || config.smallestScreenWidthDp >= 600

    if (!wide) {
        var moreOpen by remember { mutableStateOf(false) }
        var placesFor by remember { mutableStateOf<BenflixDoor?>(null) }
        val tabs = remember(railDoors) { phoneTabs(railDoors) }
        val placesDoor = destination.door()?.takeIf { destination.isDoorRoot() }
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
                BenflixPhonePageHeader(
                    destination = destination,
                    stack = stack,
                    onBack = { nav.goBack() },
                    onPick = { nav.goBackTo(it) },
                    onPlaces =
                        placesDoor
                            ?.takeIf {
                                com.github.damontecres.wholphin.ui.door.DoorPlaces
                                    .of(it)
                                    .size > 1
                            }?.let { door -> { placesFor = door } },
                )
                DestinationContent(
                    destination = destination,
                    preferences = preferences,
                    onClearBackdrop = onClearBackdrop,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(
                                start = if (destination.fullBleed()) 0.dp else 16.dp,
                                end = if (destination.fullBleed()) 0.dp else 8.dp,
                            ),
                )
                // Benflix music: a slim bar along the bottom while music plays
                if (destination !is Destination.NowPlaying) {
                    com.github.damontecres.wholphin.ui.music
                        .MusicMiniBar()
                }
                // Benflix Books: the same slim bar while an audiobook plays
                if (destination !is Destination.AudiobookPlayer) {
                    com.github.damontecres.wholphin.ui.books
                        .AudiobookMiniBar()
                }
                // Navigation redesign: Home, Movies, Shows, Music and More along the bottom (not over the players)
                if (destination !is Destination.NowPlaying && destination !is Destination.AudiobookPlayer) {
                    PhoneBottomBar(
                        tabs = tabs,
                        selected = phoneTabFor(destination, tabs),
                        moreDot = badge,
                        onTab = { tab ->
                            when {
                                tab.key == TAB_HOME -> {
                                    nav.goToHome()
                                }

                                tab.key == TAB_MORE -> {
                                    menuViewModel.load()
                                    moreOpen = true
                                }

                                tab.door != null && tab.door == placesDoor -> {
                                    // Tapping the door's tab again slides up its places (approved rx-phonepanel)
                                    placesFor = tab.door
                                }

                                tab.door != null -> {
                                    goPlace(tab.door, com.github.damontecres.wholphin.ui.door.DOOR_HOME)
                                }
                            }
                        },
                    )
                }
            }
        }
        if (moreOpen) {
            val tabDoors = tabs.mapNotNull { it.door }
            PhoneMoreSheet(
                doors = railDoors.filter { it !in tabDoors },
                showSearch = !kids,
                showRequests = !kids,
                requestsNote =
                    when {
                        requestsWaiting > 0 -> "$requestsWaiting waiting"
                        readyCount > 0 -> "$readyCount ready"
                        else -> null
                    },
                user = user,
                userImageUrl = if (kids) null else userImageUrl,
                userName =
                    when {
                        kids -> KidsMode.displayName
                        guest -> "Guest"
                        else -> user.name.orEmpty()
                    },
                minutesWatched = if (easy) null else minutes,
                profileItems =
                    profileItems?.map { item ->
                        item.copy(
                            onClick = {
                                moreOpen = false
                                item.onClick()
                            },
                        )
                    },
                onDoor = { door ->
                    moreOpen = false
                    goPlace(door, com.github.damontecres.wholphin.ui.door.DOOR_HOME)
                },
                onSearch = {
                    moreOpen = false
                    onClickSearch()
                },
                onRequests = {
                    moreOpen = false
                    nav.navigateTo(Destination.Requests)
                },
                onMyList = {
                    moreOpen = false
                    nav.navigateTo(Destination.Favorites)
                },
                onSwitchProfile = {
                    moreOpen = false
                    onSwitchProfile()
                },
                onDismiss = { moreOpen = false },
            )
        }
        placesFor?.let { door ->
            PhonePlacesSheet(
                door = door,
                places =
                    com.github.damontecres.wholphin.ui.door.DoorPlaces
                        .of(door),
                onPlace = { key ->
                    placesFor = null
                    goPlace(door, key)
                },
                onDismiss = { placesFor = null },
            )
        }
        return
    }
    val underBar = destination.underTopBar()
    // TV Music pages show the player strip at the bottom instead of the pill by the clock
    val musicStrip = destination.isMusicPage()
    // Benflix remote shortcuts (hold Back for Home, Play plays, Search, page jumps)
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val shortcutScope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(destination) { RemoteShortcuts.focused = null }
    // The header's own height: 18 above, 6 below, and the row itself
    val barHeight = 24.dp + TopNavBarHeight * (if (easy) 1.2f else 1f)
    val alwaysOpen = !isTv
    val railWidth = if (alwaysOpen) railOpenWidth(easy) else railTuckedWidth(easy)
    val contentFocus = remember { FocusRequester() }
    val railControl = remember { RailControl() }
    // ☰ Menu: a short press opens the rail, holding it opens your profile panel (approved ix-holdmenu)
    var menuHeld by remember { mutableStateOf(false) }
    val railModel =
        RailModel(
            destination = destination,
            doors = railDoors,
            showSearch = !kids,
            showRequests = !kids,
            requestsCount = if (requestsWaiting > 0) requestsWaiting else readyCount,
            user = user,
            userImageUrl = userImageUrl,
            badge = badge,
            big = easy,
            alwaysOpen = alwaysOpen,
            profileItems = profileItems,
            minutesWatched = minutes,
            onHome = { nav.goToHome() },
            onSearch = onClickSearch,
            onRequests = { nav.navigateTo(Destination.Requests) },
            onPlace = goPlace,
            onItem = { nav.navigateTo(it.destination()) },
            onProfile = onProfileTap,
            onSwitchProfile = onSwitchProfile,
        )
    val header: @Composable () -> Unit = {
        BenflixPageHeader(
            destination = destination,
            stack = stack,
            onBack = { nav.goBack() },
            onPick = { nav.goBackTo(it) },
            showClock = preferences.appPreferences.interfacePreferences.showClock,
            big = easy,
            overPicture = underBar,
            downTarget = if (underBar) LocalTopBarDown.current else null,
            hideMusicPill = musicStrip,
            modifier = Modifier.onFocusChanged { topBarFocused = it.hasFocus },
        )
    }
    Box(
        modifier =
            modifier.onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (RemoteShortcuts.onKey(
                        native = native,
                        destination = destination,
                        kids = kids,
                        focusManager = focusManager,
                        scope = shortcutScope,
                        goHome = { nav.goToHome() },
                        openSearch = onClickSearch,
                        play = { viewModel.quickPlay(it) },
                        musicHold = { viewModel.musicHold(kids) },
                        musicTap = { viewModel.musicTap() },
                    )
                ) {
                    true
                } else if (native.keyCode == android.view.KeyEvent.KEYCODE_MENU) {
                    if (native.action == android.view.KeyEvent.ACTION_DOWN) {
                        if (native.repeatCount == 0) menuHeld = false
                        if (native.repeatCount == MENU_HOLD_REPEATS) {
                            menuHeld = true
                            railControl.openProfile()
                        }
                    } else if (native.action == android.view.KeyEvent.ACTION_UP && !menuHeld) {
                        railControl.open()
                    }
                    true
                } else {
                    false
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = railWidth)
                .focusRequester(contentFocus)
                .focusRestorer(),
        ) {
            if (downloadsOpen) {
                com.github.damontecres.wholphin.ui.downloads.DownloadsPage(
                    onClose = { downloadsOpen = false },
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (underBar) {
                // Door pages: the picture goes up behind the header, so the header sits on top of the page
                val topBarDown = remember(destination) { TopBarDown() }
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalTopBarOverlap provides barHeight, LocalTopBarDown provides topBarDown) {
                        Column(Modifier.fillMaxSize()) {
                            DestinationContent(
                                destination = destination,
                                preferences = preferences,
                                onClearBackdrop = onClearBackdrop,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(
                                            start = if (destination.fullBleed()) 0.dp else PAGE_START,
                                            end = if (destination.fullBleed()) 0.dp else 16.dp,
                                        ),
                            )
                            // Music redesign 3 (approved r3-q-tvbar): the slim player strip along the bottom of Music pages
                            if (musicStrip) {
                                com.github.damontecres.wholphin.ui.music
                                    .MusicStrip()
                            }
                        }
                        header()
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    header()
                    DestinationContent(
                        destination = destination,
                        preferences = preferences,
                        onClearBackdrop = onClearBackdrop,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(
                                    start = if (destination.fullBleed()) 0.dp else PAGE_START,
                                    end = if (destination.fullBleed()) 0.dp else 16.dp,
                                ),
                    )
                    if (musicStrip) {
                        com.github.damontecres.wholphin.ui.music
                            .MusicStrip()
                    }
                }
            }
        }
        BenflixRail(
            model = railModel,
            contentFocus = contentFocus,
            control = railControl,
        )
    }
}

/** "Report a problem" from the profile menu, about the app itself */
internal val APP_PROBLEMS =
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

private const val LEAVE_AGAIN_MS = 3_000L

/** Pages that don't draw edge to edge start this far right of the rail */
private val PAGE_START = 24.dp
