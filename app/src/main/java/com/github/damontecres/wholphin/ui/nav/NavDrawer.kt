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
 * Display the top navigation bar with [DestinationContent] below it
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
            key = "${server.id}_${user.id}", // Keyed to the server & user to ensure its reset when switching either
        ),
) {
    // Keep the highlighted tab right after Back, too
    LaunchedEffect(destination) { viewModel.updateSelectedIndex() }
    val focusRequester = remember { FocusRequester() }

    // If the user presses back while on the home page, move to the top bar, another back press will quit the app
    var topBarFocused by remember { mutableStateOf(false) }
    // (TV only: phones have no top bar, so Back on Home just leaves the app like any other phone app)
    val isTv = rememberIsTv()
    BackHandler(enabled = (isTv && !topBarFocused && destination is Destination.Home)) {
        focusRequester.tryRequestFocus()
    }
    val serviceState by viewModel.serviceState.collectAsState()
    val state by viewModel.state.collectAsState()
    // A negative index is a built-in page, >=0 is a library
    val selectedIndex = state.selectedIndex

    val userImageUrl = remember(user) { viewModel.getUserImage(user) }
    // Benflix: a red number on Requests when family requests are waiting (admins only)
    hiltViewModel<RequestsBadgeViewModel>(key = "requests-badge-${user.id}")
    val requestsWaiting by RequestsWaiting.count.collectAsState()
    val tabs =
        buildList {
            add(
                TopNavTab(
                    title = stringResource(R.string.home),
                    selected = selectedIndex == HOME_INDEX,
                    onClick = {
                        viewModel.setIndex(HOME_INDEX)
                        // Benflix: Home always starts on the banner
                        com.github.damontecres.wholphin.ui.main.HomeJump
                            .request()
                        if (destination is Destination.Home) {
                            viewModel.navigationManager.reloadHome()
                            onClearBackdrop.invoke()
                        } else {
                            viewModel.navigationManager.goToHome()
                        }
                    },
                ),
            )
            if (serviceState.nowPlayingEnabled) {
                add(
                    TopNavTab(
                        title = stringResource(R.string.now_playing),
                        selected = selectedIndex == NOW_PLAYING_INDEX,
                        onClick = {
                            viewModel.setIndex(NOW_PLAYING_INDEX)
                            viewModel.navigationManager.navigateTo(Destination.NowPlaying)
                        },
                    ),
                )
            }
            val allItems = serviceState.items + serviceState.moreItems
            allItems.forEachIndexed { index, item ->
                add(
                    TopNavTab(
                        title = topNavTitle(item),
                        selected = selectedIndex == index,
                        onClick = { viewModel.onClickDrawerItem(index, item) },
                        libraryId = (item as? ServerNavDrawerItem)?.itemId,
                    ),
                )
            }
            add(
                TopNavTab(
                    title = stringResource(R.string.games),
                    selected = selectedIndex == GAMES_INDEX,
                    onClick = {
                        viewModel.setIndex(GAMES_INDEX)
                        viewModel.navigationManager.navigateToFromDrawer(Destination.Games)
                    },
                ),
            )
        }

    val onClickChannels = {
        viewModel.setIndex(CHANNELS_INDEX)
        viewModel.navigationManager.navigateToFromDrawer(Destination.Channels)
    }
    val onClickSearch = {
        viewModel.setIndex(SEARCH_INDEX)
        viewModel.navigationManager.navigateToFromDrawer(Destination.Search())
    }
    val onClickSettings = {
        viewModel.navigationManager.navigateTo(
            Destination.Settings(
                PreferenceScreenOption.BASIC,
            ),
        )
    }
    val onClickProfile = {
        viewModel.navigateToSetup(
            SetupDestination.UserList(server),
        )
    }
    if (!isTv) {
        // Phones: logo on top, menu along the bottom.
        // The logo bar slides away while you scroll down and comes back as soon as you scroll up.
        var headerVisible by remember { mutableStateOf(true) }
        // Clips (the trailer feed) covers the page above the bottom menu until you leave it
        var clipsOpen by remember { mutableStateOf(false) }
        // Downloads (saved on this phone) also covers the page in the same way
        var downloadsOpen by remember { mutableStateOf(false) }
        LaunchedEffect(destination) {
            headerVisible = true
            clipsOpen = false
            downloadsOpen = false
        }
        val phoneTabs =
            tabs.map { tab ->
                tab.copy(
                    selected = tab.selected && !clipsOpen && !downloadsOpen,
                    onClick = {
                        clipsOpen = false
                        downloadsOpen = false
                        tab.onClick()
                    },
                )
            }
        val headerScroll =
            remember {
                object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                    override fun onPreScroll(
                        available: androidx.compose.ui.geometry.Offset,
                        source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
                    ): androidx.compose.ui.geometry.Offset {
                        if (available.y < -6f) {
                            headerVisible = false
                        } else if (available.y > 6f) {
                            headerVisible = true
                        }
                        return androidx.compose.ui.geometry.Offset.Zero
                    }
                }
            }
        Column(modifier = modifier) {
            if (downloadsOpen && !clipsOpen) {
                com.github.damontecres.wholphin.ui.downloads.DownloadsPage(
                    onClose = { downloadsOpen = false },
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                )
            }
            if (clipsOpen) {
                com.github.damontecres.wholphin.ui.main.ClipsPage(
                    onClose = { clipsOpen = false },
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                )
            }
            AnimatedVisibility(
                visible = headerVisible && !clipsOpen && !downloadsOpen,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                PhoneHeader()
            }
            if (!clipsOpen && !downloadsOpen) {
                DestinationContent(
                    destination = destination,
                    preferences = preferences,
                    onClearBackdrop = onClearBackdrop,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .nestedScroll(headerScroll)
                            .padding(start = 16.dp, end = 8.dp),
                )
            }
            PhoneNavBar(
                tabs = phoneTabs,
                // Shrinks to icons only while you scroll down (same moment the header hides)
                compact = !headerVisible && !clipsOpen && !downloadsOpen,
                clipsSelected = clipsOpen,
                onClickClips = {
                    downloadsOpen = false
                    clipsOpen = true
                },
                onClickDownloads = {
                    clipsOpen = false
                    downloadsOpen = true
                },
                channelsSelected = selectedIndex == CHANNELS_INDEX && !clipsOpen && !downloadsOpen,
                onClickChannels = {
                    clipsOpen = false
                    downloadsOpen = false
                    onClickChannels()
                },
                user = user,
                userImageUrl = userImageUrl,
                searchSelected = selectedIndex == SEARCH_INDEX && !clipsOpen && !downloadsOpen,
                onClickSearch = {
                    clipsOpen = false
                    downloadsOpen = false
                    onClickSearch()
                },
                onClickSettings = onClickSettings,
                onClickProfile = onClickProfile,
                // The same extras the TV has under My Benflix and More
                extras =
                    listOfNotNull(
                        TopNavTab(
                            title = stringResource(R.string.phone_my_page),
                            selected = selectedIndex == MY_BENFLIX_INDEX && !clipsOpen && !downloadsOpen,
                            onClick = {
                                clipsOpen = false
                                downloadsOpen = false
                                viewModel.setIndex(MY_BENFLIX_INDEX)
                                viewModel.navigationManager.navigateToFromDrawer(Destination.MyBenflix)
                            },
                        ),
                        if (KidsMode.isActive) {
                            null
                        } else {
                            TopNavTab(
                                title =
                                    stringResource(R.string.benflix_requests) +
                                        if (requestsWaiting > 0) " ($requestsWaiting)" else "",
                                selected = selectedIndex == REQUESTS_INDEX && !clipsOpen && !downloadsOpen,
                                onClick = {
                                    clipsOpen = false
                                    downloadsOpen = false
                                    viewModel.setIndex(REQUESTS_INDEX)
                                    viewModel.navigationManager.navigateToFromDrawer(Destination.Requests)
                                },
                            )
                        },
                        TopNavTab(
                            title = stringResource(R.string.mood_quiz),
                            selected = false,
                            onClick = {
                                clipsOpen = false
                                downloadsOpen = false
                                viewModel.navigationManager.navigateTo(Destination.MoodQuiz)
                            },
                        ),
                        TopNavTab(
                            title = stringResource(R.string.watch_in_order),
                            selected = false,
                            onClick = {
                                clipsOpen = false
                                downloadsOpen = false
                                viewModel.navigationManager.navigateTo(Destination.WatchOrder)
                            },
                        ),
                    ),
            )
        }
        return
    }
    // TV: one slim line. Home, Browse (every library in one list), Channels, Games, Requests and More
    // (My Benflix, Now playing and the extras like the mood quiz)
    val allItems = serviceState.items + serviceState.moreItems
    val moviesIndex = allItems.indexOfFirst { it is ServerNavDrawerItem && it.type == CollectionType.MOVIES }
    val showsIndex = allItems.indexOfFirst { it is ServerNavDrawerItem && it.type == CollectionType.TVSHOWS }
    val myListIndex = allItems.indexOfFirst { it is NavDrawerItem.Favorites }
    // tabs = Home, (Now playing), every library in order, Games
    val libraryOffset = if (serviceState.nowPlayingEnabled) 2 else 1
    val myBenflixTitle = stringResource(R.string.my_benflix)
    val libraryTabs =
        tabs.filterIndexed { i, _ -> i >= libraryOffset && i != tabs.lastIndex && i - libraryOffset != myListIndex }
    val firstLibraries =
        listOf(moviesIndex, showsIndex).filter { it >= 0 }.mapNotNull { tabs.getOrNull(libraryOffset + it) }
    val libraryArt by viewModel.libraryArt.collectAsState()
    // Fetch the Browse card pictures a few seconds after start, so they're ready when Browse opens
    val libraries = remember(allItems) { allItems.filterIsInstance<ServerNavDrawerItem>() }
    LaunchedEffect(libraries) {
        delay(4_000)
        viewModel.loadLibraryArt(libraries)
    }
    val browseTabs =
        (firstLibraries + libraryTabs.filter { tab -> firstLibraries.none { it === tab } }).map { tab ->
            val art = tab.libraryId?.let { libraryArt[it] }
            if (art == null) tab else tab.copy(imageUrl = art.imageUrl, count = art.count)
        }
    val kids by KidsMode.active.collectAsState()
    val myBenflixTab =
        TopNavTab(
            title = myBenflixTitle,
            selected = selectedIndex == MY_BENFLIX_INDEX,
            onClick = {
                viewModel.setIndex(MY_BENFLIX_INDEX)
                viewModel.navigationManager.navigateToFromDrawer(Destination.MyBenflix)
            },
            hint = stringResource(R.string.more_hint_my_benflix),
            isProfile = true,
        )
    // Home, Channels, Games, Requests (not for Kids). My Benflix is the first thing under More.
    val mainTabs =
        listOfNotNull(
            tabs.first(),
            TopNavTab(
                title = stringResource(R.string.channels),
                selected = selectedIndex == CHANNELS_INDEX,
                onClick = onClickChannels,
            ),
            tabs.last(),
            if (kids) {
                null
            } else {
                TopNavTab(
                    title = stringResource(R.string.benflix_requests),
                    selected = selectedIndex == REQUESTS_INDEX,
                    onClick = {
                        viewModel.setIndex(REQUESTS_INDEX)
                        viewModel.navigationManager.navigateToFromDrawer(Destination.Requests)
                    },
                    badge = requestsWaiting,
                )
            },
        )
    val moreTabs =
        listOf(myBenflixTab) +
            tabs
                .filterIndexed { i, _ -> i in 1 until libraryOffset }
                .map { it.copy(hint = stringResource(R.string.more_hint_now_playing), glyph = MoreGlyphs.MUSIC) } +
            listOf(
                TopNavTab(
                    title = stringResource(R.string.trailer_shuffle),
                    selected = false,
                    onClick = { viewModel.navigationManager.navigateTo(Destination.TrailerShuffle) },
                    hint = stringResource(R.string.more_hint_trailer_shuffle),
                    glyph = MoreGlyphs.SHUFFLE,
                ),
                TopNavTab(
                    title = stringResource(R.string.watch_in_order),
                    selected = false,
                    onClick = { viewModel.navigationManager.navigateTo(Destination.WatchOrder) },
                    hint = stringResource(R.string.more_hint_watch_in_order),
                    glyph = MoreGlyphs.LIST,
                ),
                TopNavTab(
                    title = stringResource(R.string.mood_quiz),
                    selected = false,
                    onClick = { viewModel.navigationManager.navigateTo(Destination.MoodQuiz) },
                    hint = stringResource(R.string.more_hint_mood_quiz),
                    glyph = MoreGlyphs.SMILE,
                ),
            )
    Column(modifier = modifier) {
        TopNavBar(
            tabs = mainTabs,
            moreTabs = moreTabs,
            browseTabs = browseTabs,
            user = user,
            userImageUrl = userImageUrl,
            showClock = preferences.appPreferences.interfacePreferences.showClock,
            searchSelected = selectedIndex == SEARCH_INDEX,
            onClickSearch = onClickSearch,
            onClickSettings = onClickSettings,
            onClickProfile = onClickProfile,
            focusRequester = focusRequester,
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

val DrawerState.isOpen: Boolean get() = this.currentValue.isOpen

val DrawerValue.isOpen: Boolean get() = this == DrawerValue.Open
