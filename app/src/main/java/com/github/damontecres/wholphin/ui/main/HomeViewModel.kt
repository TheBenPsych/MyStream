package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.LocalTrailer
import com.github.damontecres.wholphin.data.model.ServerUserConfig
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.BirthdayService
import com.github.damontecres.wholphin.services.DatePlayedService
import com.github.damontecres.wholphin.services.DetailsPrefetch
import com.github.damontecres.wholphin.services.DiskCache
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.HomeDataService
import com.github.damontecres.wholphin.services.HomePageResolvedSettings
import com.github.damontecres.wholphin.services.HomeSettingsService
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.LatestNextUpService
import com.github.damontecres.wholphin.services.LiteMode
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.NavDrawerService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PreviewPlayers
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.services.ServerReportService
import com.github.damontecres.wholphin.services.SpeedStats
import com.github.damontecres.wholphin.services.TrailerService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.ui.collectLatestIn
import com.github.damontecres.wholphin.ui.combinePair
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.util.EmptyStringProvider
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.StartupTimer
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        @param:ApplicationContext internal val context: Context,
        val navigationManager: NavigationManager,
        val serverRepository: ServerRepository,
        val serverReportService: ServerReportService,
        internal val navDrawerService: NavDrawerService,
        internal val homeSettingsService: HomeSettingsService,
        internal val homeDataService: HomeDataService,
        internal val favoriteWatchManager: FavoriteWatchManager,
        internal val datePlayedService: DatePlayedService,
        internal val backdropService: BackdropService,
        internal val userPreferencesService: UserPreferencesService,
        internal val mediaManagementService: MediaManagementService,
        internal val latestNextUpService: LatestNextUpService,
        internal val api: ApiClient,
        internal val seerrService: SeerrService,
        internal val trailerService: TrailerService,
        internal val birthdayService: BirthdayService,
        internal val diskCache: DiskCache,
        internal val detailsPrefetch: DetailsPrefetch,
        private val aniListLibraries: com.github.damontecres.wholphin.services.AniListLibraries,
    ) : ViewModel() {
        internal val mutableState = MutableStateFlow(HomeState.EMPTY)
        val state: StateFlow<HomeState> = mutableState

        /**
         * A small muted player for the banner's trailer preview, created on first use
         */
        private var _previewPlayer: ExoPlayer? = null
        val previewPlayer: Player?
            get() = _previewPlayer

        private val _previewPlaying = MutableStateFlow(false)
        val previewPlaying: StateFlow<Boolean> = _previewPlaying

        internal var previewJob: Job? = null

        internal var dataLoadingJob: Job? = null

        /** The current load's background rows (banner, extra rows), cancelled when a new load starts */
        @Volatile
        internal var backgroundLoadJob: Job? = null

        /** The banner's own load finished (it wasn't cut short by a newer load) */
        @Volatile
        internal var featuredLoaded = false

        /** Benflix: when the rows that rarely change were last loaded */
        @Volatile
        internal var slowRowsLoadedAt = 0L

        /** The Anime Movies / Anime Shows libraries (any library with "anime" in its name) */
        @Volatile
        internal var animeLibraryIds: List<UUID> = emptyList()

        init {
            addCloseable {
                _previewPlayer?.release()
                _previewPlayer = null
            }
            // Benflix: let go of the video decoder when a movie starts, so it doesn't play slowly
            addCloseable(
                PreviewPlayers
                    .register {
                        previewJob?.cancel()
                        _previewPlaying.value = false
                        _previewPlayer?.release()
                        _previewPlayer = null
                    }.let { unregister -> AutoCloseable { unregister() } },
            )
            datePlayedService.invalidateAll()
            serverRepository.currentUserDtoFlow
                .combinePair(homeSettingsService.currentSettings)
                .collectLatestIn(viewModelScope) { (userDto, settings) ->
                    Timber.v(
                        "Got new userDto & settings, userId=%s, settings?=%s",
                        userDto?.id,
                        settings != HomePageResolvedSettings.EMPTY,
                    )
//                    Timber.v("userDto=%s", userDto)
                    dataLoadingJob?.cancel()
                    backgroundLoadJob?.cancel()
                    if (userDto == null) {
                        Timber.d("UserDto is null")
                        mutableState.update { HomeState.EMPTY }
                        return@collectLatestIn
                    }
                    if (settings == HomePageResolvedSettings.EMPTY) {
                        Timber.d("Home settings are empty")
                        mutableState.update { HomeState.EMPTY }
                        return@collectLatestIn
                    }
                    if (userDto.id != settings.userId) {
                        Timber.d("User IDs don't match: %s vs %s", userDto?.id, settings.userId)
                        mutableState.update { HomeState.EMPTY }
                        return@collectLatestIn
                    }
                    if (state.value.settings.userId != settings.userId) {
                        Timber.d("User changed")
                        mutableState.update { HomeState.EMPTY }
                    }
                    dataLoadingJob =
                        viewModelScope.launchIO {
                            loadHomeRows(userDto, settings)
                        }
                }
        }

        /** Benflix: the banner on screen came from last time's snapshot, the rest of it isn't applied yet */
        internal var bannerFromSnapshot = false

        /**
         * Load Home again. [afterChange]: something was just changed (watched, favourite...), so if
         * a load is already running, load once more after it instead of skipping.
         */
        fun init(afterChange: Boolean = false) {
            viewModelScope.launchIO {
                Timber.d("init HomeViewModel")
                StartupTimer.mark(StartupTimer.HOME_OPEN)
                serverRepository.currentUserDto?.let { userDto ->
                    // Benflix: put last time's banner up straight away, before anything else loads,
                    // so the page can start on it. It has its own small file, because reading the
                    // whole saved Home took seconds on older TVs.
                    if (state.value.featured.isEmpty()) {
                        // Only the small banner file here: falling back to the whole saved Home
                        // meant reading it twice at once, which took over 3 s on an older TV
                        readHomeBanner(userDto.id)?.takeIf { it.isNotEmpty() }?.let { saved ->
                            StartupTimer.mark(StartupTimer.BANNER_SAVED)
                            bannerFromSnapshot = true
                            mutableState.update { st ->
                                if (st.featured.isEmpty()) {
                                    st.copy(featured = saved.map { FeaturedItem(it.item, it.reason, it.becauseOf) })
                                } else {
                                    st
                                }
                            }
                        }
                    }
                    if (dataLoadingJob?.isActive == false) {
                        val settings =
                            homeSettingsService.currentSettings.first { it != HomePageResolvedSettings.EMPTY }
                        if (userDto.id == settings.userId) {
                            dataLoadingJob =
                                viewModelScope.launchIO {
                                    loadHomeRows(userDto, settings)
                                }
                        } else {
                            Timber.d(
                                "Init user IDs don't match: %s vs %s",
                                userDto.id,
                                settings.userId,
                            )
                        }
                    } else {
                        Timber.v("Data loading job is active")
                        if (afterChange) {
                            // Load again once this load ends, so the change shows up
                            dataLoadingJob?.join()
                            init()
                        }
                    }
                }
            }
        }

        suspend fun loadHomeRows(
            userDto: ServerUserConfig,
            allSettings: HomePageResolvedSettings,
        ) {
            Timber.i("Starting loadHomeRows")
            // Benflix Home keeps only Continue watching / Next up from the server's home sections.
            // "Recently added" rows for each library live on that library's own page.
            val settings = allSettings.copy(rows = allSettings.rows.filter { isWatchingRow(it.config) })
            // The extra rows load in the background under their own job, which is cancelled when the
            // next load starts, so an old load can't overwrite a newer one
            backgroundLoadJob?.cancel()
            val loadJob = SupervisorJob(viewModelScope.coroutineContext[Job])
            backgroundLoadJob = loadJob
            val loadScope = CoroutineScope(viewModelScope.coroutineContext + loadJob)
            try {
                val preferences = userPreferencesService.getCurrent()
                val prefs = preferences.appPreferences.homePagePreferences

                val libraries =
                    navDrawerService.getAllUserLibraries(userDto.id, userDto.tvAccess)
                // Anime Movies & Anime Shows get their own Top 10 and picks, and stay out of the main Top 10
                animeLibraryIds =
                    libraries
                        .filter { it.name.contains("anime", ignoreCase = true) }
                        .map { it.itemId }
                // AniList score and fan genres for every anime, for cards and info lines
                aniListLibraries.warm(animeLibraryIds, userDto.id)

                if (state.value.kids != KidsMode.isActive) {
                    // Switched to or from Kids: start from a clean page so no grown-up rows linger
                    mutableState.update { HomeState.EMPTY }
                }
                val state = state.value

                // Refreshing if a load has already occurred and the rows haven't significantly changed
                val refresh =
                    state.loadingState == LoadingState.Success && state.settings == settings &&
                        state.kids == KidsMode.isActive
                // Opening the app: show what Home looked like last time straight away, then swap
                // in fresh rows as they arrive
                val snapshot =
                    if (!refresh && (state.featured.isEmpty() || bannerFromSnapshot)) {
                        readHomeSnapshot(userDto.id)?.takeIf { it.rows.size == settings.rows.size }
                    } else {
                        null
                    }
                if (snapshot != null) StartupTimer.mark(StartupTimer.HOME_SAVED)
                Timber.v(
                    "refresh=%s, state.loadingState=%s, %s rows",
                    refresh,
                    state.loadingState,
                    settings.rows.size,
                )
                mutableState.update {
                    val updated =
                        it.copy(
                            loadingState =
                                if (refresh || snapshot != null) LoadingState.Success else LoadingState.Loading,
                            refreshState = LoadingState.Loading,
                            settings = settings,
                            kids = KidsMode.isActive,
                            homeRows =
                                if (refresh) {
                                    it.homeRows
                                } else {
                                    snapshot?.rows?.map { row -> row.toState() }
                                        ?: List(settings.rows.size) {
                                            HomeRowLoadingState.Pending(EmptyStringProvider)
                                        }
                                },
                        )
                    snapshot?.applyTo(updated) ?: updated
                }

                // The top of the screen loads first: the banner, Top 10 and Continue watching.
                // Rows further down start a moment later, so they don't slow down what you see first.
                bannerFromSnapshot = false
                if (!refresh || state.featured.isEmpty() || !featuredLoaded) {
                    featuredLoaded = false
                    loadScope.launchIO {
                        loadFeatured(userDto)
                        featuredLoaded = true
                    }
                } else {
                    // Keep the banner's order, but drop or update "Pick up where you left off" slides
                    loadScope.launchIO { refreshFeaturedResume(userDto) }
                }
                loadScope.launchIO { loadTrending(userDto) }
                if (KidsMode.isActive) loadScope.launchIO { loadKidsAgain(userDto.id) }

                val semaphore = Semaphore(4)

                val deferred =
                    settings.rows
                        .map { row ->
                            loadScope.async(WholphinDispatchers.IO) {
                                semaphore.withPermit {
                                    Timber.v("Fetching row: %s", row)
                                    try {
                                        homeDataService.fetchDataForRow(
                                            row = row.config,
                                            scope = viewModelScope,
                                            prefs = prefs,
                                            userDto = userDto,
                                            libraries = libraries,
                                            limit = prefs.maxItemsPerRow,
                                            isRefresh = refresh,
                                        )
                                    } catch (ex: InvalidStatusException) {
                                        if (ex.status == 404) {
                                            Timber.w(ex, "404 on row %s", row)
                                            HomeRowLoadingState.Success(
                                                row.title,
                                                emptyList(),
                                            )
                                        } else {
                                            Timber.e(
                                                ex,
                                                "Error %s on row %s",
                                                ex.status,
                                                row,
                                            )
                                            HomeRowLoadingState.Error(
                                                row.title,
                                                exception = ex,
                                            )
                                        }
                                    } catch (ex: Exception) {
                                        Timber.e(ex, "Error on row %s", row)
                                        HomeRowLoadingState.Error(
                                            row.title,
                                            exception = ex,
                                        )
                                    }
                                }
                            }
                        }

                val loadStart = System.currentTimeMillis()
                SpeedStats.homeFromSaved = snapshot != null
                loadScope.launchIO {
                    // Wait for the top rows (or at most a couple of seconds), then load the rest
                    withTimeoutOrNull(SECOND_WAVE_MAX_WAIT_MS) { deferred.awaitAll() }
                    SpeedStats.homeTopRowsMs = System.currentTimeMillis() - loadStart
                    // Random rows are picked once per visit, so they don't reshuffle (and move the
                    // focused row) every time you come back from watching something
                    if (!refresh || state.themed == null) {
                        loadScope.launchIO { loadThemed(userDto) }
                    }
                    if (!refresh || state.monthly.isEmpty()) {
                        loadScope.launchIO { loadMonthly(userDto) }
                    }
                    // Benflix: coming back to Home within a few minutes keeps rows that rarely change
                    // (New on Benflix, holiday picks, Top collections) instead of asking for them again
                    val now = System.currentTimeMillis()
                    val reloadSlowRows = !refresh || now - slowRowsLoadedAt > SLOW_ROWS_REFRESH_MS
                    // Only counted as loaded once they finish, so a load cut short by a newer one
                    // doesn't leave them missing for the next few minutes
                    val slowJobs = mutableListOf<Job>()
                    loadScope.launchIO { loadBecause(userDto) }
                    if (reloadSlowRows) slowJobs += loadScope.launchIO { loadNewest(userDto) }
                    loadScope.launchIO { loadMyList(userDto) }
                    if (reloadSlowRows) slowJobs += loadScope.launchIO { loadCelebration(userDto) }
                    loadScope.launchIO { loadComeBack(userDto) }
                    loadScope.launchIO { loadWelcomeBack(userDto) }
                    if (!refresh || state.world.isEmpty()) loadScope.launchIO { loadWorld(userDto) }
                    if (!refresh || state.airing == null) loadScope.launchIO { loadAiring(userDto) }
                    if (!refresh || state.spotlight == null) {
                        loadScope.launchIO { loadSpotlight(userDto) }
                    }
                    if (!refresh || state.animeRows.isEmpty()) {
                        loadScope.launchIO { loadAnimeRows(userDto) }
                    }
                    delay(THIRD_WAVE_DELAY_MS)
                    if (!refresh || state.genreRows.isEmpty()) {
                        loadScope.launchIO { loadGenreRows(userDto) }
                    }
                    if (reloadSlowRows) {
                        slowJobs += loadScope.launchIO { loadCollections(userDto) }
                        loadScope.launchIO {
                            slowJobs.joinAll()
                            slowRowsLoadedAt = now
                        }
                    }
                    // Studio tiles are no longer on Home; which movies belong to which collection
                    // is worked out quietly in the background and saved for a day
                    loadScope.launchIO { buildCollectionIndex(userDto.id) }
                    // Once everything has had time to load, remember this Home for next time
                    delay(SNAPSHOT_SAVE_DELAY_MS)
                    saveHomeSnapshot(userDto.id)
                }

                if (refresh || snapshot != null) {
                    // Replace rows as they complete
                    val remaining = deferred.withIndex().toMutableList()
                    while (remaining.isNotEmpty()) {
                        val (rowIndex, rowData) =
                            select {
                                // "Return" the first remaining that is completed
                                remaining
                                    .forEach { (rowIndex, deferred) ->
                                        deferred.onAwait { rowIndex to it }
                                    }
                            }
                        Timber.v("Got row data index=%s", rowIndex)
                        remaining.removeIf { it.index == rowIndex }
                        mutableState.update { state ->
                            val newRows =
                                state.homeRows.toMutableList().apply {
                                    set(rowIndex, rowData)
                                }
                            state.copy(
                                homeRows = newRows,
                            )
                        }
                    }
                    mutableState.update {
                        it.copy(
                            loadingState = LoadingState.Success,
                            refreshState = LoadingState.Success,
                        )
                    }
                } else {
                    val rows = deferred.awaitAll()
                    Timber.v("Got all rows")
                    mutableState.update {
                        it.copy(
                            loadingState = LoadingState.Success,
                            refreshState = LoadingState.Success,
                            homeRows = rows,
                        )
                    }
                }
                Timber.d("Home page load complete")
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Exception during home page loading")
                if (state.value.loadingState == LoadingState.Success) {
                    showToast(context, "Error refreshing home: ${ex.localizedMessage}")
                    mutableState.update { it.copy(refreshState = LoadingState.Error(ex)) }
                } else {
                    mutableState.update {
                        it.copy(loadingState = LoadingState.Error(ex))
                    }
                }
            }
        }

        // Benflix speed: the saved Home used to be thrown away by every app update (and Benflix
        // updates often), so the first start after an update waited for the banner. Only bump
        // HOME_SNAPSHOT_FORMAT when the saved format really changes.
        internal fun homeSnapshotKey(userId: UUID) = "home-$userId-${KidsMode.level}-$HOME_SNAPSHOT_FORMAT"

        internal fun homeBannerKey(userId: UUID) = "home-banner-$userId-${KidsMode.level}-$HOME_SNAPSHOT_FORMAT"

        internal fun readHomeBanner(userId: UUID): List<SnapshotFeatured>? =
            diskCache.read(homeBannerKey(userId), ListSerializer(SnapshotFeatured.serializer()), HOME_SNAPSHOT_MAX_AGE_MS)

        internal fun readHomeSnapshot(userId: UUID): HomeSnapshot? =
            diskCache.read(homeSnapshotKey(userId), HomeSnapshot.serializer(), HOME_SNAPSHOT_MAX_AGE_MS)

        internal fun saveHomeSnapshot(userId: UUID) {
            val current = state.value
            if (current.kids != KidsMode.isActive) return

            fun ThemedRow?.snap() = this?.let { SnapshotRow(it.title, it.items.take(SNAPSHOT_ROW_MAX)) }
            val rows =
                current.homeRows.map { row ->
                    when (row) {
                        is HomeRowLoadingState.Success -> {
                            SnapshotServerRow(
                                title = row.title,
                                items = row.items.take(SNAPSHOT_ROW_MAX).filterNotNull(),
                                viewOptions = row.viewOptions,
                                rowType = row.rowType,
                                showViewMore = row.showViewMore,
                            )
                        }

                        else -> {
                            SnapshotServerRow(title = row.title, items = emptyList())
                        }
                    }
                }
            val snapshot =
                HomeSnapshot(
                    rows = rows,
                    featured = current.featured.map { SnapshotFeatured(it.item, it.reason, it.becauseOf) },
                    trending = current.trending.take(SNAPSHOT_ROW_MAX),
                    themed = current.themed.snap(),
                    monthly = current.monthly.mapNotNull { it.snap() },
                    because = current.because.snap(),
                    becauseMore = current.becauseMore.snap(),
                    collections = current.collections.snap(),
                    newest = current.newest.snap(),
                    myList = current.myList.snap(),
                    genreRows = current.genreRows.mapNotNull { it.snap() },
                    animeTop = current.animeTop.take(SNAPSHOT_ROW_MAX),
                    animeRows = current.animeRows.mapNotNull { it.snap() },
                )
            try {
                diskCache.write(homeBannerKey(userId), ListSerializer(SnapshotFeatured.serializer()), snapshot.featured)
                diskCache.write(homeSnapshotKey(userId), HomeSnapshot.serializer(), snapshot)
            } catch (ex: Exception) {
                Timber.w(ex, "Could not save home snapshot")
            }
        }

        /** [featuredSource] started right away; gives up (empty) if it takes too long */
        internal fun kotlinx.coroutines.CoroutineScope.featuredSourceAsync(
            name: String,
            block: suspend () -> List<FeaturedItem>,
        ) = async {
            withTimeoutOrNull(FEATURED_SOURCE_TIMEOUT_MS) { featuredSource(name, block) }.orEmpty()
        }

        /** Every movie & show id in the anime libraries, remembered for a while */
        @Volatile
        internal var animeIdCache: Triple<List<UUID>, Long, Set<UUID>>? = null

        /** Which collection each movie is in, so a franchise (e.g. all the Saw movies) shows as one tile */
        internal var collectionIndex: Pair<String, Map<UUID, BaseItemDto>>? = null
        internal var collectionIndexJob: Job? = null

        /**
         * What this profile rated with thumbs up / thumbs down (Jellyfin Likes)
         */
        internal data class Taste(
            val disliked: Set<UUID>,
            val liked: BaseItemDto?,
        ) {
            fun keep(item: BaseItemDto) = item.id !in disliked && item.seriesId?.let { it in disliked } != true

            fun keep(item: BaseItem) = keep(item.data)
        }

        /** Benflix: the thumbs up/down lists are shared by every row of one Home load */
        internal val tasteLock = Mutex()

        @Volatile
        internal var tasteCache: Triple<UUID, Long, Taste>? = null

        /** Benflix: only worked out once each time Benflix opens */
        internal var welcomeChecked = false

        /** Opens a full page of picks for the mood, like the studio tiles do */
        fun pickMood(mood: HomeMood) {
            navigationManager.navigateTo(Destination.Mood(mood.name))
        }

        /**
         * Play the item's local trailer muted behind the banner after a short delay,
         * or stop the preview if [item] is null
         */
        fun previewTrailer(item: BaseItem?) {
            previewJob?.cancel()
            _previewPlaying.value = false
            _previewPlayer?.stop()
            if (item == null || LiteMode.isOn(context)) return
            previewJob =
                viewModelScope.launch(ExceptionHandler()) {
                    // Look up the trailer while waiting, so it can start the moment the wait is over
                    val lookup =
                        async(WholphinDispatchers.IO) {
                            trailerService
                                .getLocalTrailers(item)
                                .filterIsInstance<LocalTrailer>()
                                .firstOrNull()
                        }
                    delay(TRAILER_DELAY_MS)
                    val trailer = lookup.await() ?: return@launch
                    val url = api.videosApi.getVideoStreamUrl(itemId = trailer.baseItem.id, static = true)
                    val player =
                        _previewPlayer ?: ExoPlayer
                            .Builder(context)
                            // Start after half a second of video instead of the usual 2.5 seconds
                            .setLoadControl(
                                DefaultLoadControl
                                    .Builder()
                                    .setBufferDurationsMs(8_000, 30_000, 500, 1_500)
                                    .build(),
                            ).build()
                            .also { newPlayer ->
                                newPlayer.volume = 0f
                                newPlayer.addListener(
                                    object : Player.Listener {
                                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                                            if (isPlaying) _previewPlaying.value = true
                                        }

                                        override fun onPlaybackStateChanged(playbackState: Int) {
                                            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                                                _previewPlaying.value = false
                                            }
                                        }
                                    },
                                )
                                _previewPlayer = newPlayer
                            }
                    player.setMediaItem(MediaItem.fromUri(url))
                    player.prepare()
                    player.playWhenReady = true
                }
        }

        fun updateBackdrop(item: DiscoverItem) {
            viewModelScope.launchIO {
                backdropService.submit(item)
            }
        }

        /**
         * Navigate to a random unwatched movie or show
         */
        fun surpriseMe() {
            viewModelScope.launch(ExceptionHandler(autoToast = true) + WholphinDispatchers.IO) {
                val userId = serverRepository.currentUser?.id ?: return@launch
                val item =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userId,
                                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                recursive = true,
                                isPlayed = false,
                                sortBy = listOf(ItemSortBy.RANDOM),
                                limit = 1,
                            ),
                        ).content.items
                        .firstOrNull()
                if (item != null) {
                    withContext(WholphinDispatchers.Main) {
                        navigationManager.navigateTo(BaseItem(item).destination())
                    }
                } else {
                    showToast(context, "Nothing unwatched to pick from")
                }
            }
        }

        fun toggleFeaturedFavorite(item: BaseItem) {
            viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
                val favorite = !item.favorite
                favoriteWatchManager.setFavorite(item.id, favorite)
                markFeaturedFavorite(item.id, favorite)
                // The My List row changes too, like favouriting from a row's menu
                withContext(WholphinDispatchers.Main) {
                    init(afterChange = true)
                }
            }
        }

        /** Keep the banner's My List heart in step with a favourite change */
        private fun markFeaturedFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) {
            mutableState.update { state ->
                state.copy(
                    featured =
                        state.featured.map {
                            if (it.item.id == itemId) {
                                val data = it.item.data
                                it.copy(
                                    item =
                                        it.item.copy(
                                            data = data.copy(userData = data.userData?.copy(isFavorite = favorite)),
                                        ),
                                )
                            } else {
                                it
                            }
                        },
                )
            }
        }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
            favoriteWatchManager.setWatched(itemId, played)
            withContext(WholphinDispatchers.Main) {
                init(afterChange = true)
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            markFeaturedFavorite(itemId, favorite)
            withContext(WholphinDispatchers.Main) {
                init(afterChange = true)
            }
        }

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchIO {
                backdropService.submit(item)
            }
            // Resting on a poster: get its details ready in case OK is pressed
            detailsPrefetch.prefetch(viewModelScope, item.id, item.type)
        }

        fun deleteItem(
            position: RowColumn,
            item: BaseItem,
        ) {
            deleteItem(context, mediaManagementService, item) {
                viewModelScope.launchDefault {
                    val row = state.value.homeRows.getOrNull(position.row)
                    if (row is HomeRowLoadingState.Success) {
                        mutableState.update {
                            val newRow =
                                row.items.toMutableList().apply {
                                    removeAt(position.column)
                                }
                            it.copy(
                                homeRows =
                                    it.homeRows.toMutableList().apply {
                                        set(position.row, row.copy(items = newRow))
                                    },
                            )
                        }
                    }
                }
            }
        }

        fun canDelete(
            item: BaseItem,
            appPreferences: AppPreferences,
        ): Boolean = mediaManagementService.canDelete(item, appPreferences)

        fun removeFromNextUp(item: BaseItem) {
            if (item.type == BaseItemKind.EPISODE) {
                viewModelScope.launchDefault {
                    serverRepository.currentUser?.id?.let { userId ->
                        latestNextUpService.removeFromNextUp(userId, item)
                        init(afterChange = true)
                    }
                }
            } else {
                Timber.w("Item is not an episode %s", item.id)
            }
        }
    }

/** Benflix "Tonight's pick": the day's pick (day-user-Kids level key, item id), so it stays put all evening */
@Volatile internal var tonightCache: Pair<String, UUID>? = null

/** Benflix "On this day": which movies came out on today's date (worked out once a day) */
@Volatile internal var onThisDayCache: Pair<String, List<UUID>>? = null

/** Benflix: Seerr's trending list and when it was fetched */
@Volatile internal var trendingCache: Pair<Long, List<DiscoverItem>>? = null

/**
 * Whether a row is a "is watching" type
 */
internal fun isWatchingRow(row: HomeRowConfig) =
    row is HomeRowConfig.ContinueWatching ||
        row is HomeRowConfig.NextUp ||
        row is HomeRowConfig.ContinueWatchingCombined
