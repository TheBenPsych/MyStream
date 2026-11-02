package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.github.damontecres.wholphin.BuildConfig
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.data.model.LocalTrailer
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.ServerUserConfig
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.BirthdayService
import com.github.damontecres.wholphin.services.Celebration
import com.github.damontecres.wholphin.services.CelebrationNow
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
import com.github.damontecres.wholphin.services.LogoEffect
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.NavDrawerService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.services.ServerReportService
import com.github.damontecres.wholphin.services.SpeedStats
import com.github.damontecres.wholphin.services.TrailerService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.services.holidayFor
import com.github.damontecres.wholphin.ui.HomeItemFields
import com.github.damontecres.wholphin.ui.collectLatestIn
import com.github.damontecres.wholphin.ui.combinePair
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.util.EmptyStringProvider
import com.github.damontecres.wholphin.ui.util.StringProvider
import com.github.damontecres.wholphin.ui.util.StringStringProvider
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.studiosApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.time.LocalDate
import java.time.MonthDay
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val navigationManager: NavigationManager,
        val serverRepository: ServerRepository,
        val serverReportService: ServerReportService,
        private val navDrawerService: NavDrawerService,
        private val homeSettingsService: HomeSettingsService,
        private val homeDataService: HomeDataService,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val datePlayedService: DatePlayedService,
        private val backdropService: BackdropService,
        private val userPreferencesService: UserPreferencesService,
        private val mediaManagementService: MediaManagementService,
        private val latestNextUpService: LatestNextUpService,
        private val api: ApiClient,
        private val seerrService: SeerrService,
        private val trailerService: TrailerService,
        private val birthdayService: BirthdayService,
        private val diskCache: DiskCache,
        private val detailsPrefetch: DetailsPrefetch,
    ) : ViewModel() {
        private val _state = MutableStateFlow(HomeState.EMPTY)
        val state: StateFlow<HomeState> = _state

        /**
         * A small muted player for the banner's trailer preview, created on first use
         */
        private var _previewPlayer: ExoPlayer? = null
        val previewPlayer: Player?
            get() = _previewPlayer

        private val _previewPlaying = MutableStateFlow(false)
        val previewPlaying: StateFlow<Boolean> = _previewPlaying

        private var previewJob: Job? = null

        private var dataLoadingJob: Job? = null

        /** The Anime Movies / Anime Shows libraries (any library with "anime" in its name) */
        @Volatile
        private var animeLibraryIds: List<UUID> = emptyList()

        init {
            addCloseable {
                _previewPlayer?.release()
                _previewPlayer = null
            }
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
                    if (userDto == null) {
                        Timber.d("UserDto is null")
                        _state.update { HomeState.EMPTY }
                        return@collectLatestIn
                    }
                    if (settings == HomePageResolvedSettings.EMPTY) {
                        Timber.d("Home settings are empty")
                        _state.update { HomeState.EMPTY }
                        return@collectLatestIn
                    }
                    if (userDto.id != settings.userId) {
                        Timber.d("User IDs don't match: %s vs %s", userDto?.id, settings.userId)
                        _state.update { HomeState.EMPTY }
                        return@collectLatestIn
                    }
                    if (state.value.settings.userId != settings.userId) {
                        Timber.d("User changed")
                        _state.update { HomeState.EMPTY }
                    }
                    dataLoadingJob =
                        viewModelScope.launchIO {
                            loadHomeRows(userDto, settings)
                        }
                }
        }

        fun init() {
            viewModelScope.launchIO {
                Timber.d("init HomeViewModel")
                serverRepository.currentUserDto?.let { userDto ->
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

                if (state.value.kids != KidsMode.isActive) {
                    // Switched to or from Kids: start from a clean page so no grown-up rows linger
                    _state.update { HomeState.EMPTY }
                }
                val state = state.value

                // Refreshing if a load has already occurred and the rows haven't significantly changed
                val refresh =
                    state.loadingState == LoadingState.Success && state.settings == settings &&
                        state.kids == KidsMode.isActive
                // Opening the app: show what Home looked like last time straight away, then swap
                // in fresh rows as they arrive
                val snapshot =
                    if (!refresh && state.featured.isEmpty()) {
                        readHomeSnapshot(userDto.id)?.takeIf { it.rows.size == settings.rows.size }
                    } else {
                        null
                    }
                Timber.v(
                    "refresh=%s, state.loadingState=%s, %s rows",
                    refresh,
                    state.loadingState,
                    settings.rows.size,
                )
                _state.update {
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
                if (!refresh || state.featured.isEmpty()) {
                    viewModelScope.launchIO { loadFeatured(userDto) }
                }
                viewModelScope.launchIO { loadTrending(userDto) }
                if (KidsMode.isActive) viewModelScope.launchIO { loadKidsAgain(userDto.id) }

                val semaphore = Semaphore(4)

                val deferred =
                    settings.rows
                        .map { row ->
                            viewModelScope.async(WholphinDispatchers.IO) {
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
                viewModelScope.launchIO {
                    // Wait for the top rows (or at most a couple of seconds), then load the rest
                    withTimeoutOrNull(SECOND_WAVE_MAX_WAIT_MS) { deferred.awaitAll() }
                    SpeedStats.homeTopRowsMs = System.currentTimeMillis() - loadStart
                    // Random rows are picked once per visit, so they don't reshuffle (and move the
                    // focused row) every time you come back from watching something
                    if (!refresh || state.themed == null) {
                        viewModelScope.launchIO { loadThemed(userDto) }
                    }
                    viewModelScope.launchIO { loadBecause(userDto) }
                    viewModelScope.launchIO { loadNewest(userDto) }
                    viewModelScope.launchIO { loadMyList(userDto) }
                    viewModelScope.launchIO { loadCelebration(userDto) }
                    if (!refresh || state.animeRows.isEmpty()) {
                        viewModelScope.launchIO { loadAnimeRows(userDto) }
                    }
                    delay(THIRD_WAVE_DELAY_MS)
                    if (!refresh || state.genreRows.isEmpty()) {
                        viewModelScope.launchIO { loadGenreRows(userDto) }
                    }
                    viewModelScope.launchIO { loadCollections(userDto) }
                    // Studio tiles are no longer on Home; which movies belong to which collection
                    // is worked out quietly in the background and saved for a day
                    viewModelScope.launchIO { buildCollectionIndex(userDto.id) }
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
                        _state.update { state ->
                            val newRows =
                                state.homeRows.toMutableList().apply {
                                    set(rowIndex, rowData)
                                }
                            state.copy(
                                homeRows = newRows,
                            )
                        }
                    }
                    _state.update {
                        it.copy(
                            loadingState = LoadingState.Success,
                            refreshState = LoadingState.Success,
                        )
                    }
                } else {
                    val rows = deferred.awaitAll()
                    Timber.v("Got all rows")
                    _state.update {
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
                    _state.update { it.copy(refreshState = LoadingState.Error(ex)) }
                } else {
                    _state.update {
                        it.copy(loadingState = LoadingState.Error(ex))
                    }
                }
            }
        }

        private fun homeSnapshotKey(userId: UUID) = "home-$userId-${KidsMode.isActive}-${BuildConfig.VERSION_CODE}"

        private fun readHomeSnapshot(userId: UUID): HomeSnapshot? =
            diskCache.read(homeSnapshotKey(userId), HomeSnapshot.serializer(), HOME_SNAPSHOT_MAX_AGE_MS)

        private fun saveHomeSnapshot(userId: UUID) {
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
                diskCache.write(homeSnapshotKey(userId), HomeSnapshot.serializer(), snapshot)
            } catch (ex: Exception) {
                Timber.w(ex, "Could not save home snapshot")
            }
        }

        /**
         * Load the items for the rotating banner at the top of the home page:
         * the newest movies & shows plus a few random unwatched picks
         */
        private suspend fun loadFeatured(userDto: ServerUserConfig) {
            val taste = loadTaste(userDto.id)
            val baseRequest =
                GetItemsRequest(
                    userId = userDto.id,
                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                    recursive = true,
                    imageTypes = listOf(ImageType.BACKDROP),
                    fields = HomeItemFields,
                )
            // Each source is loaded separately so one failing doesn't empty the whole banner
            val resume =
                featuredSource("resume") {
                    api.itemsApi
                        .getResumeItems(
                            GetResumeItemsRequest(
                                userId = userDto.id,
                                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                                fields = HomeItemFields,
                                enableUserData = true,
                                limit = FEATURED_RESUME,
                            ),
                        ).content.items
                        .map { FeaturedItem(BaseItem(it), FeaturedReason.RESUME) }
                }
            val because =
                featuredSource("because") {
                    val lastWatched =
                        api.itemsApi
                            .getItems(
                                GetItemsRequest(
                                    userId = userDto.id,
                                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                                    recursive = true,
                                    isPlayed = true,
                                    sortBy = listOf(ItemSortBy.DATE_PLAYED),
                                    sortOrder = listOf(SortOrder.DESCENDING),
                                    limit = 1,
                                ),
                            ).content.items
                            .firstOrNull()
                    val sourceId =
                        if (lastWatched?.type == BaseItemKind.EPISODE) lastWatched?.seriesId else lastWatched?.id
                    val sourceName =
                        if (lastWatched?.type == BaseItemKind.EPISODE) lastWatched?.seriesName else lastWatched?.name
                    if (sourceId == null || sourceName == null) {
                        emptyList()
                    } else {
                        api.libraryApi
                            .getSimilarItems(
                                GetSimilarItemsRequest(
                                    userId = userDto.id,
                                    itemId = sourceId,
                                    fields = HomeItemFields,
                                    limit = 12,
                                ),
                            ).content.items
                            .filter { !it.backdropImageTags.isNullOrEmpty() && it.userData?.played != true }
                            .shuffled()
                            .take(FEATURED_BECAUSE)
                            .map { FeaturedItem(BaseItem(it), FeaturedReason.BECAUSE, becauseOf = sourceName) }
                    }
                }
            val liked =
                featuredSource("liked") {
                    val likedItem = taste.liked
                    val name = likedItem?.name
                    if (likedItem == null || name == null) {
                        emptyList()
                    } else {
                        api.libraryApi
                            .getSimilarItems(
                                GetSimilarItemsRequest(
                                    userId = userDto.id,
                                    itemId = likedItem.id,
                                    fields = HomeItemFields,
                                    limit = 12,
                                ),
                            ).content.items
                            .filter { !it.backdropImageTags.isNullOrEmpty() && it.userData?.played != true }
                            .shuffled()
                            .take(FEATURED_BECAUSE)
                            .map { FeaturedItem(BaseItem(it), FeaturedReason.BECAUSE, becauseOf = name) }
                    }
                }
            val newest =
                featuredSource("newest") {
                    api.itemsApi
                        .getItems(
                            baseRequest.copy(
                                sortBy = listOf(ItemSortBy.DATE_CREATED),
                                sortOrder = listOf(SortOrder.DESCENDING),
                                limit = FEATURED_NEWEST,
                            ),
                        ).content.items
                        .map { FeaturedItem(BaseItem(it), FeaturedReason.NEW) }
                }
            val favorites =
                featuredSource("favorites") {
                    api.itemsApi
                        .getItems(
                            baseRequest.copy(
                                sortBy = listOf(ItemSortBy.RANDOM),
                                isFavorite = true,
                                limit = FEATURED_FAVORITES,
                            ),
                        ).content.items
                        .map { FeaturedItem(BaseItem(it), FeaturedReason.FAVORITE) }
                }
            val picks =
                featuredSource("picks") {
                    api.itemsApi
                        .getItems(
                            baseRequest.copy(
                                sortBy = listOf(ItemSortBy.RANDOM),
                                isPlayed = false,
                                limit = FEATURED_PICKS,
                            ),
                        ).content.items
                        .map { FeaturedItem(BaseItem(it), FeaturedReason.PICK) }
                }
            // From 6 PM: one well-rated movie you haven't seen leads the banner as Tonight's pick.
            // It stays the same all evening (a new one each night) and skips thumbs-down titles.
            val tonight =
                if (java.time.LocalTime
                        .now()
                        .hour >= TONIGHT_FROM_HOUR
                ) {
                    featuredSource("tonight") {
                        val candidates =
                            api.itemsApi
                                .getItems(
                                    baseRequest.copy(
                                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                                        isPlayed = false,
                                        minCommunityRating = TONIGHT_MIN_RATING,
                                        sortBy = listOf(ItemSortBy.SORT_NAME),
                                        limit = TONIGHT_POOL,
                                    ),
                                ).content.items
                                .filter { it.userData?.likes != false }
                        if (candidates.isEmpty()) {
                            emptyList()
                        } else {
                            val day =
                                java.time.LocalDate
                                    .now()
                                    .toEpochDay()
                            val index = Math.floorMod(day * 31 + userDto.id.hashCode(), candidates.size.toLong()).toInt()
                            listOf(FeaturedItem(BaseItem(candidates[index]), FeaturedReason.TONIGHT, becauseOf = userDto.name))
                        }
                    }
                } else {
                    emptyList()
                }
            // Benflix "On this day": a movie that came out on today's date, years ago
            val onThisDay = featuredSource("onThisDay") { onThisDay(userDto.id, baseRequest) }
            // Take turns between the sources so the banner mixes them
            val sources = listOf(tonight, onThisDay, resume, because, liked, newest, favorites, picks)
            val featured =
                (0 until sources.maxOf { it.size })
                    .flatMap { i -> sources.mapNotNull { it.getOrNull(i) } }
                    .filter { taste.keep(it.item) }
                    .distinctBy { it.item.id }
                    .take(FEATURED_MAX)
            _state.update { it.copy(featured = featured) }
        }

        /**
         * Movies released on today's date in an earlier year. Jellyfin can't search by day of the
         * year, so the release dates of the movies are checked once a day and remembered.
         */
        private suspend fun onThisDay(
            userId: UUID,
            baseRequest: GetItemsRequest,
        ): List<FeaturedItem> {
            val today = java.time.LocalDate.now()
            val key = "$today/$userId/${KidsMode.isActive}"
            val ids =
                onThisDayCache?.takeIf { it.first == key }?.second
                    ?: api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userId,
                                includeItemTypes = listOf(BaseItemKind.MOVIE),
                                recursive = true,
                                enableImages = false,
                                enableUserData = false,
                                limit = ON_THIS_DAY_POOL,
                            ),
                        ).content.items
                        .filter {
                            val date = it.premiereDate ?: return@filter false
                            date.monthValue == today.monthValue && date.dayOfMonth == today.dayOfMonth && date.year < today.year
                        }.map { it.id }
                        .also { onThisDayCache = key to it }
            val id = ids.randomOrNull() ?: return emptyList()
            val movie =
                api.itemsApi
                    .getItems(baseRequest.copy(ids = listOf(id), includeItemTypes = listOf(BaseItemKind.MOVIE)))
                    .content.items
                    .firstOrNull() ?: return emptyList()
            val years = today.year - (movie.premiereDate?.year ?: return emptyList())
            val label = if (years == 1) "Came out 1 year ago today" else "Came out $years years ago today"
            return listOf(FeaturedItem(BaseItem(movie), FeaturedReason.ON_THIS_DAY, becauseOf = label))
        }

        /** Benflix Kids "Watch it again": what's been watched most on Kids on this device */
        private suspend fun loadKidsAgain(userId: UUID) {
            try {
                val ids =
                    com.github.damontecres.wholphin.services.BenflixKidsLog
                        .favouriteIds(context)
                        .mapNotNull { it.toUUIDOrNull() }
                        .take(KIDS_AGAIN_MAX)
                if (ids.size < KIDS_AGAIN_MIN) return
                val byId =
                    api.itemsApi
                        .getItems(GetItemsRequest(userId = userId, ids = ids, fields = HomeItemFields))
                        .content.items
                        .associateBy { it.id }
                val items = ids.mapNotNull { byId[it] }.map { BaseItem(it) }
                if (items.size >= KIDS_AGAIN_MIN) {
                    _state.update { it.copy(kidsAgain = ThemedRow("Watch it again", items)) }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Kids: couldn't load Watch it again")
            }
        }

        private suspend fun featuredSource(
            name: String,
            block: suspend () -> List<FeaturedItem>,
        ): List<FeaturedItem> =
            try {
                block.invoke()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Error loading featured items: %s", name)
                emptyList()
            }

        /**
         * Load what's trending this week from Seerr: the top items already on the server,
         * and, for admins, trending items that are not on the server yet
         */
        private suspend fun loadTrending(userDto: ServerUserConfig) {
            try {
                if (!seerrService.active.first()) {
                    _state.update {
                        it.copy(
                            trending = loadServerTop(userDto),
                            trendingMissing = emptyList(),
                            animeTop = loadAnimeTop(userDto, emptyList()),
                        )
                    }
                    return
                }
                val discovered =
                    (1..TRENDING_PAGES)
                        .flatMap { seerrService.trending(it) }
                        .filter { it.type == SeerrItemType.MOVIE || it.type == SeerrItemType.TV }
                        .distinctBy { it.type to it.id }
                val allInLibraryIds =
                    discovered
                        .mapNotNull { it.jellyfinItemId }
                        .distinct()
                // Anime trending titles go to the Top 10 anime row instead
                val animeIds = animeIdsAmong(userDto.id, allInLibraryIds)
                val inLibraryIds = allInLibraryIds.filter { it !in animeIds }.take(TRENDING_TOP)
                val animeTrending = allInLibraryIds.filter { it in animeIds }
                val trending =
                    if (inLibraryIds.isNotEmpty()) {
                        val items =
                            api.itemsApi
                                .getItems(
                                    GetItemsRequest(
                                        userId = userDto.id,
                                        ids = inLibraryIds,
                                        fields = HomeItemFields,
                                    ),
                                ).content.items
                                .associateBy { it.id }
                        // Keep the trending order
                        inLibraryIds.mapNotNull { id -> items[id]?.let { BaseItem(it) } }
                    } else {
                        emptyList()
                    }
                val missing =
                    if (userDto.policy?.isAdministrator == true && !KidsMode.isActive) {
                        discovered
                            .filter {
                                it.jellyfinItemId == null &&
                                    it.availability != SeerrAvailability.AVAILABLE &&
                                    it.availability != SeerrAvailability.BLOCKLISTED
                            }.take(TRENDING_MISSING_MAX)
                    } else {
                        emptyList()
                    }
                // Too few trending titles on the server to fill the row, so use the server's own top titles
                val top = if (trending.size < TRENDING_MIN) loadServerTop(userDto) else trending
                _state.update { it.copy(trending = top, trendingMissing = missing) }
                val anime = loadAnimeTop(userDto, animeTrending)
                _state.update { it.copy(animeTop = anime) }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Error loading trending items")
                _state.update { it.copy(trending = loadServerTop(userDto), animeTop = loadAnimeTop(userDto, emptyList())) }
            }
        }

        /**
         * The best rated movies & shows on the server from this year and last year,
         * used for the Top 10 row when Seerr isn't set up
         */
        private suspend fun loadServerTop(userDto: ServerUserConfig): List<BaseItem> =
            try {
                val year = LocalDate.now().year
                api.itemsApi
                    .getItems(
                        GetItemsRequest(
                            userId = userDto.id,
                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            recursive = true,
                            years = listOf(year, year - 1),
                            imageTypes = listOf(ImageType.PRIMARY),
                            sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            // Extra, because anime is taken out below
                            limit = TRENDING_TOP * 4,
                            fields = HomeItemFields,
                        ),
                    ).content.items
                    .let { items ->
                        val anime = animeIdsAmong(userDto.id, items.map { it.id })
                        items.filter { it.id !in anime }
                    }.take(TRENDING_TOP)
                    .map { BaseItem(it) }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Error loading server top items")
                emptyList()
            }

        /**
         * Which of these items are in the anime libraries
         */
        private suspend fun animeIdsAmong(
            userId: UUID,
            ids: List<UUID>,
        ): Set<UUID> {
            if (animeLibraryIds.isEmpty() || ids.isEmpty()) return emptySet()
            val all = allAnimeIds(userId)
            return ids.filter { it in all }.toSet()
        }

        /** Every movie & show id in the anime libraries, remembered for a while */
        @Volatile
        private var animeIdCache: Triple<List<UUID>, Long, Set<UUID>>? = null

        /**
         * All movie & show ids in the anime libraries. Asking the server "which of these ids are in
         * this library" doesn't work (it ignores the library when ids are given), so the whole
         * list of anime ids is fetched once and checked here instead.
         */
        private suspend fun allAnimeIds(userId: UUID): Set<UUID> {
            val libs = animeLibraryIds
            animeIdCache?.let { (cachedLibs, at, ids) ->
                if (cachedLibs == libs && System.currentTimeMillis() - at < ANIME_ID_CACHE_MS) return ids
            }
            val ids =
                libs
                    .flatMap { libraryId ->
                        try {
                            api.itemsApi
                                .getItems(
                                    GetItemsRequest(
                                        userId = userId,
                                        parentId = libraryId,
                                        recursive = true,
                                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                        enableImages = false,
                                        enableUserData = false,
                                    ),
                                ).content.items
                                .map { it.id }
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            Timber.w(ex, "Error listing anime library %s", libraryId)
                            emptyList()
                        }
                    }.toSet()
            animeIdCache = Triple(libs, System.currentTimeMillis(), ids)
            return ids
        }

        /**
         * Movies & shows from both anime libraries together. Each library is asked separately
         * (the server only takes one library at a time) and the answers are merged.
         */
        private suspend fun animeItems(
            userId: UUID,
            request: GetItemsRequest,
        ): List<BaseItemDto> =
            animeLibraryIds.flatMap { libraryId ->
                try {
                    api.itemsApi
                        .getItems(
                            request.copy(
                                userId = userId,
                                parentId = libraryId,
                                recursive = true,
                                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                fields = HomeItemFields,
                            ),
                        ).content.items
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Error loading from anime library %s", libraryId)
                    emptyList()
                }
            }

        /**
         * "Top 10 anime": anime trending this week first (from Seerr), then the best rated
         * anime from this year and last year, then the best rated anime of all time
         */
        private suspend fun loadAnimeTop(
            userDto: ServerUserConfig,
            trendingIds: List<UUID>,
        ): List<BaseItem> {
            if (animeLibraryIds.isEmpty()) return emptyList()
            return try {
                val trending =
                    if (trendingIds.isNotEmpty()) {
                        val byId =
                            api.itemsApi
                                .getItems(
                                    GetItemsRequest(
                                        userId = userDto.id,
                                        ids = trendingIds.take(TRENDING_TOP),
                                        fields = HomeItemFields,
                                    ),
                                ).content.items
                                .associateBy { it.id }
                        trendingIds.mapNotNull { byId[it] }
                    } else {
                        emptyList()
                    }
                val year = LocalDate.now().year
                val bestRated =
                    GetItemsRequest(
                        imageTypes = listOf(ImageType.PRIMARY),
                        sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        limit = TRENDING_TOP,
                    )
                val recent =
                    if (trending.size < TRENDING_TOP) {
                        animeItems(userDto.id, bestRated.copy(years = listOf(year, year - 1)))
                            .sortedByDescending { it.communityRating ?: 0f }
                    } else {
                        emptyList()
                    }
                val allTime =
                    if (trending.size + recent.size < TRENDING_TOP) {
                        animeItems(userDto.id, bestRated)
                            .sortedByDescending { it.communityRating ?: 0f }
                    } else {
                        emptyList()
                    }
                (trending + recent + allTime)
                    .distinctBy { it.id }
                    .take(TRENDING_TOP)
                    .map { BaseItem(it) }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Error loading top 10 anime")
                emptyList()
            }
        }

        /**
         * Anime picks: "Because you watched <last anime>", "Hidden gem anime" (well rated,
         * not watched yet) and two anime genre rows, like "Action anime"
         */
        private suspend fun loadAnimeRows(userDto: ServerUserConfig) {
            if (animeLibraryIds.isEmpty()) {
                _state.update { it.copy(animeRows = emptyList()) }
                return
            }
            try {
                val taste = loadTaste(userDto.id)
                val rows = mutableListOf<ThemedRow>()
                val shown = mutableSetOf<UUID>()

                fun add(
                    title: String,
                    items: List<BaseItemDto>,
                ) {
                    val fresh =
                        items
                            .filter { it.id !in shown && taste.keep(it) }
                            .distinctBy { it.id }
                            .take(EXTRA_ROW_MAX)
                    if (fresh.size >= ANIME_ROW_MIN) {
                        rows.add(ThemedRow(title, fresh.map { BaseItem(it) }))
                        shown.addAll(fresh.map { it.id })
                    }
                }

                // Because you watched <anime>: the last anime watched, then similar anime
                val lastWatched =
                    animeLibraryIds
                        .flatMap { libraryId ->
                            try {
                                api.itemsApi
                                    .getItems(
                                        GetItemsRequest(
                                            userId = userDto.id,
                                            parentId = libraryId,
                                            recursive = true,
                                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                                            isPlayed = true,
                                            sortBy = listOf(ItemSortBy.DATE_PLAYED),
                                            sortOrder = listOf(SortOrder.DESCENDING),
                                            limit = 1,
                                        ),
                                    ).content.items
                            } catch (ex: CancellationException) {
                                throw ex
                            } catch (ex: Exception) {
                                Timber.w(ex, "Error loading last watched anime")
                                emptyList()
                            }
                        }.maxByOrNull { it.userData?.lastPlayedDate?.toEpochSecond(java.time.ZoneOffset.UTC) ?: 0L }
                val source =
                    lastWatched?.let {
                        if (it.type == BaseItemKind.EPISODE) {
                            it.seriesId?.let { id -> it.seriesName?.let { name -> id to name } }
                        } else {
                            it.name?.let { name -> it.id to name }
                        }
                    }
                if (source != null) {
                    val similar =
                        api.libraryApi
                            .getSimilarItems(
                                GetSimilarItemsRequest(
                                    userId = userDto.id,
                                    itemId = source.first,
                                    fields = HomeItemFields,
                                    limit = ANIME_SIMILAR_FETCH,
                                ),
                            ).content.items
                    // Keep only anime, so this row is all anime
                    val anime = animeIdsAmong(userDto.id, similar.map { it.id })
                    shown.add(source.first)
                    add(
                        context.getString(R.string.because_you_watched_row, source.second),
                        similar.filter { it.id in anime },
                    )
                }

                // Hidden gem anime: well rated, not watched yet
                add(
                    context.getString(R.string.hidden_gem_anime),
                    animeItems(
                        userDto.id,
                        GetItemsRequest(
                            isPlayed = false,
                            minCommunityRating = ANIME_GEM_MIN_RATING,
                            sortBy = listOf(ItemSortBy.RANDOM),
                            limit = EXTRA_ROW_MAX,
                        ),
                    ).shuffled(),
                )

                // Two anime genre rows, picked at random each visit
                val genres =
                    animeLibraryIds
                        .flatMap { libraryId ->
                            try {
                                api.genresApi
                                    .getGenres(
                                        userId = userDto.id,
                                        parentId = libraryId,
                                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                    ).content.items
                                    .mapNotNull { it.name }
                            } catch (ex: CancellationException) {
                                throw ex
                            } catch (ex: Exception) {
                                Timber.w(ex, "Error loading anime genres")
                                emptyList()
                            }
                        }.distinct()
                        // "Anime" itself is often a genre on anime, which would make an "Anime anime" row
                        .filterNot { it.contains("anime", ignoreCase = true) || it.contains("animation", ignoreCase = true) }
                        .shuffled()
                var genreCount = 0
                for (genre in genres) {
                    if (genreCount >= ANIME_GENRE_ROWS) break
                    val before = rows.size
                    add(
                        context.getString(R.string.anime_genre_row, genre),
                        animeItems(
                            userDto.id,
                            GetItemsRequest(
                                genres = listOf(genre),
                                sortBy = listOf(ItemSortBy.RANDOM),
                                limit = EXTRA_ROW_MAX,
                            ),
                        ).shuffled(),
                    )
                    if (rows.size > before) genreCount++
                }
                _state.update { it.copy(animeRows = rows) }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Error loading anime rows")
            }
        }

        /**
         * Load the seasonal themed row, such as horror in October.
         * Holiday rows only use titles that fit the holiday (see [HomeTheme]); if too few are on the
         * server the row falls back to the genre of the week instead of showing random picks.
         */
        private suspend fun loadThemed(userDto: ServerUserConfig) {
            try {
                val today = LocalDate.now()
                val themes = listOf(HomeTheme.forDate(today), HomeTheme.weekly(today)).distinct()
                for (theme in themes) {
                    val found = findThemeItems(userDto.id, theme)
                    Timber.d("Themed row %s found %s items", theme.title, found.size)
                    if (found.size < theme.minItems) continue
                    val items =
                        collapseIntoCollections(userDto.id, found)
                            .take(THEMED_MAX)
                            .map { BaseItem(it) }
                    if (items.isNotEmpty()) {
                        val title = context.getString(theme.title, theme.titleArg)
                        _state.update { it.copy(themed = ThemedRow(title, items)) }
                        return
                    }
                }
                _state.update { it.copy(themed = null) }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Error loading themed row")
            }
        }

        /**
         * Everything on the server that fits [theme]: hand-picked titles, tagged titles and titles
         * with a holiday word in the name come first (shuffled), then genre picks if the theme has a genre.
         */
        private suspend fun findThemeItems(
            userId: UUID,
            theme: HomeTheme,
        ): List<BaseItemDto> =
            coroutineScope {
                val types = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
                val limiter = Semaphore(THEMED_PARALLEL)

                suspend fun query(request: GetItemsRequest): List<BaseItemDto> =
                    limiter.withPermit {
                        try {
                            api.itemsApi
                                .getItems(request)
                                .content.items
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            Timber.w(ex, "Themed row lookup failed")
                            emptyList()
                        }
                    }

                fun search(term: String) =
                    GetItemsRequest(
                        userId = userId,
                        includeItemTypes = types,
                        recursive = true,
                        searchTerm = term,
                        limit = 10,
                        fields = HomeItemFields,
                    )

                // Hand-picked titles: search by name, then keep only exact name matches
                // (so "Coco" never brings in "Coco Before Chanel")
                val picked =
                    theme.titles
                        .map { it.substringBefore(':').trimEnd('.', '!', '?').trim() }
                        .distinct()
                        .map { term ->
                            async { query(search(term)).filter { theme.isPicked(it.name, it.originalTitle) } }
                        }
                // Titles with a holiday word in the name, such as "A Christmas Story"
                val named =
                    theme.nameWords.map { word ->
                        async { query(search(word)).filter { theme.hasNameWord(it.name) || theme.hasNameWord(it.originalTitle) } }
                    }
                // TMDB keywords, which Jellyfin saves as tags
                val tagged =
                    async {
                        if (theme.tags.isEmpty()) {
                            emptyList()
                        } else {
                            query(
                                GetItemsRequest(
                                    userId = userId,
                                    includeItemTypes = types,
                                    recursive = true,
                                    tags = theme.tags,
                                    sortBy = listOf(ItemSortBy.RANDOM),
                                    limit = THEMED_FETCH,
                                    fields = HomeItemFields,
                                ),
                            )
                        }
                    }
                val byGenre =
                    async {
                        if (theme.genres.isNullOrEmpty()) {
                            emptyList()
                        } else {
                            query(
                                GetItemsRequest(
                                    userId = userId,
                                    includeItemTypes = types,
                                    recursive = true,
                                    genres = theme.genres,
                                    sortBy = listOf(ItemSortBy.RANDOM),
                                    limit = THEMED_FETCH,
                                    fields = HomeItemFields,
                                ),
                            )
                        }
                    }
                val fitting = (picked.awaitAll().flatten() + named.awaitAll().flatten() + tagged.await()).shuffled()
                (fitting + byGenre.await()).distinctBy { it.id }
            }

        /** Which collection each movie is in, so a franchise (e.g. all the Saw movies) shows as one tile */
        private var collectionIndex: Pair<String, Map<UUID, BaseItemDto>>? = null
        private var collectionIndexJob: Job? = null

        private fun collectionIndexKey(userId: UUID) = "collections-$userId-${KidsMode.isActive}"

        /**
         * The saved collection index, from memory or from the saved file (up to a day old).
         * Null when it hasn't been built yet: building it asks the server about every collection,
         * so that only happens in the background ([buildCollectionIndex]).
         */
        private fun cachedCollectionIndex(userId: UUID): Map<UUID, BaseItemDto>? {
            val key = collectionIndexKey(userId)
            collectionIndex?.takeIf { it.first == key }?.let { return it.second }
            val saved = diskCache.read(key, CollectionIndexFile.serializer(), COLLECTION_INDEX_MAX_AGE_MS) ?: return null
            val byId = saved.collections.associateBy { it.id }
            val index =
                saved.byMovie
                    .mapNotNull { (movie, collection) ->
                        val col = byId[UUID.fromString(collection)] ?: return@mapNotNull null
                        UUID.fromString(movie) to col
                    }.toMap()
            collectionIndex = key to index
            return index
        }

        /** Build the collection index in the background (once) and save it for next time */
        private suspend fun buildCollectionIndex(userId: UUID) {
            if (cachedCollectionIndex(userId) != null) return
            if (collectionIndexJob?.isActive == true) return
            val job = viewModelScope.launchIO { collectionsByMovie(userId) }
            collectionIndexJob = job
            job.join()
        }

        private suspend fun collectionsByMovie(userId: UUID): Map<UUID, BaseItemDto> {
            val key = collectionIndexKey(userId)
            cachedCollectionIndex(userId)?.let { return it }
            val collections =
                api.itemsApi
                    .getItems(
                        GetItemsRequest(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.BOX_SET),
                            recursive = true,
                            limit = COLLECTION_INDEX_MAX,
                            fields = HomeItemFields,
                        ),
                    ).content.items
            val permits = Semaphore(COLLECTION_INDEX_PARALLEL)
            val index =
                collections
                    .map { collection ->
                        viewModelScope.async(WholphinDispatchers.IO) {
                            permits.withPermit {
                                try {
                                    api.itemsApi
                                        .getItems(
                                            GetItemsRequest(
                                                userId = userId,
                                                parentId = collection.id,
                                                enableImages = false,
                                                enableUserData = false,
                                                limit = 200,
                                            ),
                                        ).content.items
                                        .map { it.id to collection }
                                } catch (ex: CancellationException) {
                                    throw ex
                                } catch (ex: Exception) {
                                    Timber.w(ex, "Error reading collection %s", collection.name)
                                    emptyList()
                                }
                            }
                        }
                    }.awaitAll()
                    .flatten()
                    .toMap()
            collectionIndex = key to index
            diskCache.write(
                key,
                CollectionIndexFile.serializer(),
                CollectionIndexFile(
                    byMovie = index.map { (movie, col) -> movie.toString() to col.id.toString() }.toMap(),
                    collections = index.values.distinctBy { it.id },
                ),
            )
            return index
        }

        /**
         * Swap movies that belong to a collection for the collection itself (once), keeping the order
         */
        private suspend fun collapseIntoCollections(
            userId: UUID,
            items: List<BaseItemDto>,
        ): List<BaseItemDto> {
            // Only use the index if it's already saved: never make the row wait for it
            val byMovie = cachedCollectionIndex(userId) ?: emptyMap()
            val shown = mutableSetOf<UUID>()
            return items.mapNotNull { item ->
                val tile = if (item.type == BaseItemKind.MOVIE) byMovie[item.id] ?: item else item
                tile.takeIf { shown.add(it.id) }
            }
        }

        /**
         * Birthday banner on the profile's birthday, otherwise a holiday banner
         */
        private suspend fun loadCelebration(userDto: ServerUserConfig) {
            val today = LocalDate.now()
            val birthday = if (KidsMode.isActive) null else birthdayService.get(userDto.id)
            val celebration =
                if (birthday != null && birthday == MonthDay.from(today)) {
                    Celebration(
                        listOfNotNull("Happy Birthday", userDto.name?.takeIf { it.isNotBlank() }).joinToString(", "),
                        "🎂",
                        0xFFFF5CA8,
                        LogoEffect.CONFETTI,
                    )
                } else {
                    holidayFor(today)
                }
            CelebrationNow.set(celebration)
            _state.update { it.copy(celebration = celebration) }
        }

        /**
         * Studio tiles under the banner: the studios & networks with the most movies & shows on the server
         */
        private suspend fun loadStudios(userDto: ServerUserConfig) {
            try {
                val types = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
                val studios =
                    api.studiosApi
                        .getStudios(userId = userDto.id, includeItemTypes = types)
                        .content.items
                val tiles =
                    StudioBrands
                        .mapNotNull { brand ->
                            val ids =
                                studios
                                    .filter { studio ->
                                        val name = studio.name?.lowercase() ?: return@filter false
                                        brand.matches.any { name.contains(it) }
                                    }.map { it.id }
                            if (ids.isEmpty()) return@mapNotNull null
                            val count =
                                api.itemsApi
                                    .getItems(
                                        GetItemsRequest(
                                            userId = userDto.id,
                                            studioIds = ids,
                                            includeItemTypes = types,
                                            recursive = true,
                                            limit = 0,
                                            enableTotalRecordCount = true,
                                        ),
                                    ).content.totalRecordCount
                            StudioTile(brand, ids, count)
                        }.filter { it.count >= STUDIO_MIN_ITEMS }
                        .sortedByDescending { it.count }
                        .take(STUDIO_MAX_TILES)
                _state.update { it.copy(studios = tiles) }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Error loading studio tiles")
            }
        }

        fun openStudio(tile: StudioTile) {
            val userId = serverRepository.currentUser?.id ?: return
            navigationManager.navigateTo(
                Destination.ItemGrid(
                    title = StringStringProvider(tile.brand.name),
                    request =
                        GetItemsRequest(
                            userId = userId,
                            studioIds = tile.studioIds,
                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            recursive = true,
                            sortBy = listOf(ItemSortBy.PREMIERE_DATE),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            fields = HomeItemFields,
                        ),
                    requestHandler = GetItemsRequestHandler,
                ),
            )
        }

        /**
         * What this profile rated with thumbs up / thumbs down (Jellyfin Likes)
         */
        private data class Taste(
            val disliked: Set<UUID>,
            val liked: BaseItemDto?,
        ) {
            fun keep(item: BaseItemDto) = item.id !in disliked && item.seriesId?.let { it in disliked } != true

            fun keep(item: BaseItem) = keep(item.data)
        }

        private suspend fun loadTaste(userId: UUID): Taste {
            fun request(filter: ItemFilter) =
                GetItemsRequest(
                    userId = userId,
                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                    recursive = true,
                    filters = listOf(filter),
                    sortBy = listOf(ItemSortBy.RANDOM),
                )
            val disliked =
                try {
                    api.itemsApi
                        .getItems(request(ItemFilter.DISLIKES).copy(limit = TASTE_MAX))
                        .content.items
                        .map { it.id }
                        .toSet()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error loading dislikes")
                    emptySet()
                }
            val liked =
                try {
                    api.itemsApi
                        .getItems(request(ItemFilter.LIKES).copy(limit = 1))
                        .content.items
                        .firstOrNull()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error loading likes")
                    null
                }
            return Taste(disliked, liked)
        }

        /**
         * "Because you watched X" (or "Because you liked X"): titles similar to the last
         * movie or show watched, or to one the profile gave a thumbs up
         */
        private suspend fun loadBecause(userDto: ServerUserConfig) {
            try {
                val taste = loadTaste(userDto.id)
                // Recently watched (finished or in progress), as shows/movies
                val played =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userDto.id,
                                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                                recursive = true,
                                isPlayed = true,
                                sortBy = listOf(ItemSortBy.DATE_PLAYED),
                                sortOrder = listOf(SortOrder.DESCENDING),
                                limit = BECAUSE_LOOKBACK,
                            ),
                        ).content.items
                val resume =
                    api.itemsApi
                        .getResumeItems(
                            GetResumeItemsRequest(
                                userId = userDto.id,
                                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                                limit = 5,
                            ),
                        ).content.items
                val watched =
                    (resume + played)
                        .mapNotNull {
                            if (it.type == BaseItemKind.EPISODE) {
                                it.seriesId?.let { id -> it.seriesName?.let { name -> Triple(id, name, false) } }
                            } else {
                                it.name?.let { name -> Triple(it.id, name, false) }
                            }
                        }
                val liked = taste.liked?.let { item -> item.name?.let { Triple(item.id, it, true) } }
                // Sometimes start with something the profile gave a thumbs up
                val candidates =
                    (if (liked != null && Random.nextBoolean()) listOf(liked) + watched else watched + listOfNotNull(liked))
                        .distinctBy { it.first }
                        .take(BECAUSE_TRIES)
                for ((sourceId, sourceName, fromLiked) in candidates) {
                    val items =
                        api.libraryApi
                            .getSimilarItems(
                                GetSimilarItemsRequest(
                                    userId = userDto.id,
                                    itemId = sourceId,
                                    fields = HomeItemFields,
                                    limit = BECAUSE_MAX,
                                ),
                            ).content.items
                            .filter { taste.keep(it) }
                            .map { BaseItem(it) }
                    if (items.size >= BECAUSE_MIN) {
                        val title =
                            context.getString(
                                if (fromLiked) R.string.because_you_liked else R.string.because_you_watched_row,
                                sourceName,
                            )
                        _state.update { it.copy(because = ThemedRow(title, items)) }
                        loadMoreOfGenre(userDto, sourceId, items, taste)
                        return
                    }
                }
                _state.update { it.copy(because = null, becauseMore = null) }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Error loading because you watched row")
            }
        }

        /**
         * "New on Benflix": newest movies & shows from every library together
         */
        private suspend fun loadNewest(userDto: ServerUserConfig) {
            try {
                val taste = loadTaste(userDto.id)
                val items =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userDto.id,
                                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                recursive = true,
                                sortBy = listOf(ItemSortBy.DATE_CREATED),
                                sortOrder = listOf(SortOrder.DESCENDING),
                                limit = EXTRA_ROW_MAX,
                                fields = HomeItemFields,
                            ),
                        ).content.items
                        .filter { taste.keep(it) }
                        .map { BaseItem(it) }
                _state.update {
                    it.copy(newest = items.takeIf { it.isNotEmpty() }?.let { ThemedRow(context.getString(R.string.new_on_benflix), it) })
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Error loading new on Benflix row")
            }
        }

        /**
         * "My List": the profile's favorite movies & shows
         */
        private suspend fun loadMyList(userDto: ServerUserConfig) {
            try {
                val items =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userDto.id,
                                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                recursive = true,
                                isFavorite = true,
                                sortBy = listOf(ItemSortBy.DATE_CREATED),
                                sortOrder = listOf(SortOrder.DESCENDING),
                                limit = EXTRA_ROW_MAX,
                                fields = HomeItemFields,
                            ),
                        ).content.items
                        .map { BaseItem(it) }
                _state.update {
                    it.copy(myList = items.takeIf { it.isNotEmpty() }?.let { ThemedRow(context.getString(R.string.my_list), it) })
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Error loading my list row")
            }
        }

        /**
         * "More musicals": right under "Because you watched", more titles from the main genre of
         * whatever was watched last, so the home page reacts the same day
         */
        private suspend fun loadMoreOfGenre(
            userDto: ServerUserConfig,
            sourceId: java.util.UUID,
            becauseItems: List<BaseItem>,
            taste: Taste,
        ) {
            try {
                val genre =
                    api.userLibraryApi
                        .getItem(itemId = sourceId, userId = userDto.id)
                        .content.genres
                        ?.firstOrNull()
                if (genre == null) {
                    _state.update { it.copy(becauseMore = null) }
                    return
                }
                val skip = becauseItems.map { it.id }.toSet() + sourceId
                val items =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userDto.id,
                                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                recursive = true,
                                genres = listOf(genre),
                                sortBy = listOf(ItemSortBy.RANDOM),
                                limit = EXTRA_ROW_MAX,
                                fields = HomeItemFields,
                            ),
                        ).content.items
                        .filter { it.id !in skip && taste.keep(it) }
                        .map { BaseItem(it) }
                _state.update {
                    it.copy(
                        becauseMore =
                            if (items.size >= GENRE_MIN) {
                                ThemedRow(context.getString(R.string.more_of_genre, genre), items)
                            } else {
                                null
                            },
                    )
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Error loading more-of-genre row")
            }
        }

        /**
         * A few genre rows ("Comedy", "Action"...), picked at random each visit
         */
        private suspend fun loadGenreRows(userDto: ServerUserConfig) {
            try {
                val taste = loadTaste(userDto.id)
                val genres =
                    api.genresApi
                        .getGenres(
                            userId = userDto.id,
                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        ).content.items
                        .mapNotNull { it.name }
                        .shuffled()
                val rows = mutableListOf<ThemedRow>()
                for (genre in genres) {
                    if (rows.size >= GENRE_ROWS) break
                    val items =
                        api.itemsApi
                            .getItems(
                                GetItemsRequest(
                                    userId = userDto.id,
                                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                    recursive = true,
                                    genres = listOf(genre),
                                    sortBy = listOf(ItemSortBy.RANDOM),
                                    limit = EXTRA_ROW_MAX,
                                    fields = HomeItemFields,
                                ),
                            ).content.items
                            .filter { taste.keep(it) }
                            .map { BaseItem(it) }
                    if (items.size >= GENRE_MIN) rows.add(ThemedRow(genre, items))
                }
                _state.update { it.copy(genreRows = rows) }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Error loading genre rows")
            }
        }

        /**
         * "Top collections": movie collections like Harry Potter or Marvel,
         * the ones watched the most first
         */
        private suspend fun loadCollections(userDto: ServerUserConfig) {
            try {
                val items =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userDto.id,
                                includeItemTypes = listOf(BaseItemKind.BOX_SET),
                                recursive = true,
                                imageTypes = listOf(ImageType.PRIMARY),
                                enableUserData = true,
                                sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                                sortOrder = listOf(SortOrder.DESCENDING),
                                limit = COLLECTIONS_FETCH,
                                fields = HomeItemFields,
                            ),
                        ).content.items
                        // Most watched first: collections people have seen the most of
                        .sortedByDescending { it.userData?.playedPercentage ?: if (it.userData?.played == true) 100.0 else 0.0 }
                        .take(COLLECTIONS_MAX)
                        .map { BaseItem(it) }
                _state.update {
                    it.copy(
                        collections =
                            if (items.size >= COLLECTIONS_MIN) {
                                ThemedRow(context.getString(R.string.top_collections_row), items)
                            } else {
                                null
                            },
                    )
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Error loading collections row")
            }
        }

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
                _state.update { state ->
                    state.copy(
                        featured =
                            state.featured.map {
                                if (it.item.id == item.id) {
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
        }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
            favoriteWatchManager.setWatched(itemId, played)
            withContext(WholphinDispatchers.Main) {
                init()
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            withContext(WholphinDispatchers.Main) {
                init()
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
                        _state.update {
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
                        init()
                    }
                }
            } else {
                Timber.w("Item is not an episode %s", item.id)
            }
        }
    }

data class HomeState(
    val loadingState: LoadingState,
    val refreshState: LoadingState,
    val homeRows: List<HomeRowLoadingState>,
    val settings: HomePageResolvedSettings,
    val featured: List<FeaturedItem> = emptyList(),
    val trending: List<BaseItem> = emptyList(),
    val trendingMissing: List<DiscoverItem> = emptyList(),
    val themed: ThemedRow? = null,
    val because: ThemedRow? = null,
    /** Benflix Kids: "Watch it again", what Kids watched most */
    val kidsAgain: ThemedRow? = null,
    val becauseMore: ThemedRow? = null,
    val collections: ThemedRow? = null,
    val mood: HomeMood? = null,
    val moodRow: ThemedRow? = null,
    val celebration: Celebration? = null,
    val studios: List<StudioTile> = emptyList(),
    val newest: ThemedRow? = null,
    val myList: ThemedRow? = null,
    val genreRows: List<ThemedRow> = emptyList(),
    /** "Top 10 anime": Anime Movies and Anime Shows together */
    val animeTop: List<BaseItem> = emptyList(),
    /** Anime picks: because you watched an anime, hidden gems, anime by genre */
    val animeRows: List<ThemedRow> = emptyList(),
    /** Whether these rows were loaded for the Kids profile */
    val kids: Boolean = false,
) {
    companion object {
        val EMPTY =
            HomeState(
                LoadingState.Pending,
                LoadingState.Pending,
                emptyList(),
                HomePageResolvedSettings.EMPTY,
            )
    }
}

private const val TASTE_MAX = 300
private const val ANIME_ID_CACHE_MS = 60 * 60 * 1000L
private const val ANIME_ROW_MIN = 5
private const val ANIME_SIMILAR_FETCH = 40
private const val ANIME_GEM_MIN_RATING = 7.5
private const val ANIME_GENRE_ROWS = 2
private const val BECAUSE_LOOKBACK = 30
private const val BECAUSE_TRIES = 6
private const val EXTRA_ROW_MAX = 20
private const val GENRE_ROWS = 3
private const val GENRE_MIN = 6
private const val TRENDING_MIN = 5
private const val FEATURED_NEWEST = 3
private const val FEATURED_RESUME = 2
private const val FEATURED_BECAUSE = 2
private const val FEATURED_FAVORITES = 2
private const val FEATURED_MAX = 10
private const val KIDS_AGAIN_MIN = 3
private const val KIDS_AGAIN_MAX = 20

/** Benflix "On this day": which movies came out on today's date (worked out once a day) */
@Volatile private var onThisDayCache: Pair<String, List<UUID>>? = null
private const val ON_THIS_DAY_POOL = 3000
private const val FEATURED_PICKS = 3
private const val TRENDING_PAGES = 3
private const val TRENDING_TOP = 10
private const val TRENDING_MISSING_MAX = 20
private const val THEMED_MAX = 20
private const val THEMED_FETCH = 60

/** How many name searches the themed row runs at once */
private const val THEMED_PARALLEL = 6
private const val COLLECTION_INDEX_MAX = 400
private const val COLLECTION_INDEX_PARALLEL = 6
private const val BECAUSE_MAX = 20
private const val BECAUSE_MIN = 4
private const val COLLECTIONS_FETCH = 100
private const val COLLECTIONS_MAX = 20
private const val COLLECTIONS_MIN = 3
private const val STUDIO_MIN_ITEMS = 3
private const val STUDIO_MAX_TILES = 20
private const val TRAILER_DELAY_MS = 1_500L
private const val TONIGHT_FROM_HOUR = 18
private const val TONIGHT_MIN_RATING = 7.0
private const val TONIGHT_POOL = 200
private const val SECOND_WAVE_MAX_WAIT_MS = 2_500L
private const val THIRD_WAVE_DELAY_MS = 400L
private const val COLLECTION_INDEX_MAX_AGE_MS = 24 * 60 * 60 * 1000L
private const val HOME_SNAPSHOT_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000L
private const val SNAPSHOT_SAVE_DELAY_MS = 15_000L
private const val SNAPSHOT_ROW_MAX = 20

/** What Home looked like last time, saved so it can be shown instantly when the app opens */
@Serializable
private data class HomeSnapshot(
    val rows: List<SnapshotServerRow>,
    val featured: List<SnapshotFeatured> = emptyList(),
    val trending: List<BaseItem> = emptyList(),
    val themed: SnapshotRow? = null,
    val because: SnapshotRow? = null,
    val becauseMore: SnapshotRow? = null,
    val collections: SnapshotRow? = null,
    val newest: SnapshotRow? = null,
    val myList: SnapshotRow? = null,
    val genreRows: List<SnapshotRow> = emptyList(),
    val animeTop: List<BaseItem> = emptyList(),
    val animeRows: List<SnapshotRow> = emptyList(),
) {
    fun applyTo(state: HomeState): HomeState {
        fun SnapshotRow?.row() = this?.let { ThemedRow(it.title, it.items) }
        return state.copy(
            featured = featured.map { FeaturedItem(it.item, it.reason, it.becauseOf) },
            trending = trending,
            themed = themed.row(),
            because = because.row(),
            becauseMore = becauseMore.row(),
            collections = collections.row(),
            newest = newest.row(),
            myList = myList.row(),
            genreRows = genreRows.mapNotNull { it.row() },
            animeTop = animeTop,
            animeRows = animeRows.mapNotNull { it.row() },
        )
    }
}

@Serializable
private data class SnapshotServerRow(
    val title: StringProvider,
    val items: List<BaseItem>,
    val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    val rowType: HomeRowConfig? = null,
    val showViewMore: Boolean = true,
) {
    fun toState(): HomeRowLoadingState = HomeRowLoadingState.Success(title, items, viewOptions, rowType, showViewMore)
}

@Serializable
private data class SnapshotRow(
    val title: String,
    val items: List<BaseItem>,
)

@Serializable
private data class SnapshotFeatured(
    val item: BaseItem,
    val reason: FeaturedReason,
    val becauseOf: String? = null,
)

/** The collection index as saved on disk: movie id to collection id, plus the collections */
@Serializable
private data class CollectionIndexFile(
    val byMovie: Map<String, String>,
    val collections: List<BaseItemDto>,
)

/**
 * Whether a row is a "is watching" type
 */
private fun isWatchingRow(row: HomeRowConfig) =
    row is HomeRowConfig.ContinueWatching ||
        row is HomeRowConfig.NextUp ||
        row is HomeRowConfig.ContinueWatchingCombined
