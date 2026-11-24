package com.github.damontecres.wholphin.ui.search

import android.content.Context
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.updateSearchPreferences
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.KeyValueService
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.LiveTvService
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.NavDrawerService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.services.ServerReportService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.services.tvAccess
import com.github.damontecres.wholphin.ui.ProgramItemFields
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.components.ContextMenuProvider
import com.github.damontecres.wholphin.ui.components.VoiceInputManager
import com.github.damontecres.wholphin.ui.components.baseItemKinds
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.detail.livetv.ProgramDialogState
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.FuzzyTitles
import com.github.damontecres.wholphin.util.SearchRelevance
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.artistsApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.personsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val api: ApiClient,
        val navigationManager: NavigationManager,
        val appPreferences: DataStore<AppPreferences>,
        private val seerrService: SeerrService,
        val voiceInputManager: VoiceInputManager,
        val userPreferencesService: UserPreferencesService,
        private val serverRepository: ServerRepository,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val mediaManagementService: MediaManagementService,
        private val serverReportService: ServerReportService,
        private val liveTvService: LiveTvService,
        private val keyValueService: KeyValueService,
        private val navDrawerService: NavDrawerService,
        private val doorData: com.github.damontecres.wholphin.ui.door.DoorDataService,
        private val gameLibrary: com.github.damontecres.wholphin.services.GameLibraryClient,
        private val music: com.github.damontecres.wholphin.services.BenflixMusic,
        private val bookShelf: com.github.damontecres.wholphin.services.BenflixBooks,
    ) : ViewModel(),
        ContextMenuProvider {
        /** Benflix: recent searches are kept per account */
        val recentAccount: String? get() = serverRepository.currentUser?.id?.toString()

        // Benflix: never offer requests on the Kids profile
        val seerrActive =
            seerrService.active.combine(KidsMode.active) { active, kids -> active && !kids }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )

        private val _popular = MutableStateFlow<List<BaseItem>>(emptyList())

        /** Before you type: the Top 10 movies and shows, mixed (approved Popular row) */
        val popular: StateFlow<List<BaseItem>> = _popular

        private val _books = MutableStateFlow<List<com.github.damontecres.wholphin.services.BookTile>>(emptyList())

        /** Redesign 2 search: books and audiobooks on Tower are their own door (never on Kids) */
        val books: StateFlow<List<com.github.damontecres.wholphin.services.BookTile>> = _books
        private var bookLibrary: List<com.github.damontecres.wholphin.services.BookTile>? = null

        private fun searchBooks(query: String?) {
            val q = searchPlain(query)
            if (q.isEmpty() || KidsMode.isActive) {
                _books.value = emptyList()
                return
            }
            viewModelScope.launch(WholphinDispatchers.IO) {
                val all =
                    bookLibrary ?: try {
                        bookShelf.library().titles.also { bookLibrary = it }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.i("Search: no books (%s)", ex.message)
                        emptyList()
                    }
                val found =
                    all
                        .filter { !it.notHere }
                        .filter { t -> listOf(t.name, t.author, t.series).any { searchPlain(it).contains(q) } }
                        .sortedWith(
                            compareBy({ !searchPlain(it.name).startsWith(q) }, { !searchPlain(it.name).contains(q) }, { it.name.length }),
                        ).take(SEARCH_LIMIT)
                if (searchPlain(currentQuery) == q) _books.value = found
            }
        }

        private val _suggestions = MutableStateFlow<List<String>>(emptyList())

        /**
         * Redesign 2 search: whole names that start with (or have) what was typed. On TV they're the left column,
         * on phones and the web a row of chips. They only change with the typed words, not while you try one.
         */
        val suggestions: StateFlow<List<String>> = _suggestions
        private var typedQuery: String? = null

        private val _games = MutableStateFlow<List<SearchGame>>(emptyList())

        /** Approved search: games are their own door (Benflix games and your own games on Tower) */
        val games: StateFlow<List<SearchGame>> = _games
        private var romGames: List<SearchGame>? = null

        private suspend fun romGames(): List<SearchGame> {
            romGames?.let { return it }
            val power =
                com.github.damontecres.wholphin.games.DevicePower
                    .get(context)
            val kids = KidsMode.isActive
            val list =
                gameLibrary
                    .savedLibrary()
                    .orEmpty()
                    .flatMap { c ->
                        val local =
                            com.github.damontecres.wholphin.games.LocalConsole
                                .forSlug(c.slug)
                        if (local == null ||
                            power.fit(local.power) == com.github.damontecres.wholphin.games.DevicePower.Fit.TOO_MUCH
                        ) {
                            emptyList()
                        } else {
                            c.games
                                .filter {
                                    !kids ||
                                        com.github.damontecres.wholphin.services.KidsMode
                                            .allowsGame(it.kids, it.bigKids)
                                }.map { g ->
                                    SearchGame(
                                        key = "rom-${g.id}",
                                        title = g.name,
                                        kind = c.name,
                                        coverUrl = g.coverUrl,
                                        token = gameLibrary.token,
                                        romId = g.id,
                                    )
                                }
                        }
                    }
            romGames = list
            return list
        }

        private val _musicAsk = MutableStateFlow<List<SearchMusicAsk>>(emptyList())

        /** Benflix: songs and albums that aren't on Benflix yet, for the Ask for it door (every profile but Kids and Guest) */
        val musicAsk: StateFlow<List<SearchMusicAsk>> = _musicAsk

        /** A short line after asking for music, shown as a toast */
        val musicNote = MutableStateFlow<String?>(null)

        // Ben 2026-11-15: every account but Kids can ask (family asks wait for Ben's yes). Guest borrows the owner's
        // sign-in, so it would skip Ben's yes: no asking there.
        private fun canAskForMusic(): Boolean =
            !KidsMode.isActive &&
                !com.github.damontecres.wholphin.services.GuestMode.isActive

        private val musicAdmin: Boolean get() = serverRepository.currentUserDto?.policy?.isAdministrator == true

        private fun searchMusicAsk(query: String?) {
            val q = query?.trim().orEmpty()
            if (q.length < 2 || !canAskForMusic()) {
                _musicAsk.value = emptyList()
                return
            }
            searchJobs +=
                viewModelScope.launchIO {
                    val found =
                        try {
                            music.search(q)
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            // Music requests aren't set up on Tower (or Tower is away): just no music to ask for
                            if (!ex.isCancellation()) Timber.w(ex, "Search: music catalogue")
                            null
                        }
                    if (currentQuery?.trim() != q) return@launchIO
                    _musicAsk.value =
                        found
                            ?.let { f ->
                                f.songs.filter { !it.have }.take(MUSIC_ASK_SONGS).map {
                                    SearchMusicAsk("song", it.id, it.title, it.artist, it.cover, it.asked, it.albumId, it.album)
                                } +
                                    f.albums.take(MUSIC_ASK_ALBUMS).map {
                                        SearchMusicAsk("album", it.id, it.title, it.artist, it.cover, it.asked, it.id, it.title)
                                    }
                            }.orEmpty()
                            .filter { it.asked != "In your music" }
                }
        }

        /** [kind] is "song" or "album" */
        fun askForMusic(
            item: SearchMusicAsk,
            kind: String = item.kind,
        ) {
            viewModelScope.launchIO {
                try {
                    val id = if (kind == "album") item.albumId else item.id
                    music.ask(kind, id)
                    val name = if (kind == "album") item.albumTitle else item.title
                    musicNote.value =
                        if (musicAdmin) "Asked for $name. Tower is getting it." else "Asked Ben for $name. He says yes before it downloads."
                    _musicAsk.value =
                        _musicAsk.value.map {
                            if (it.key == item.key || (kind == "album" && it.albumId == item.albumId)) it.copy(asked = "Asked") else it
                        }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Search: ask for music")
                    musicNote.value = ex.message ?: "Couldn't ask Tower for it."
                }
            }
        }

        private fun searchGames(query: String?) {
            val q = query?.trim()?.lowercase().orEmpty()
            if (q.isEmpty()) {
                _games.value = emptyList()
                return
            }
            viewModelScope.launch(WholphinDispatchers.IO) {
                val kids = KidsMode.isActive
                val benflix =
                    com.github.damontecres.wholphin.ui.games.MovieGame.entries
                        .filter { it.visibleNow(kids) }
                        .map {
                            SearchGame(
                                key = "movie-${it.name}",
                                title = it.title,
                                kind = "Glowfin game",
                                destination = Destination.GameInfo("movie:${it.name}"),
                                colors = it.colors,
                                icon = it.icon,
                            )
                        } +
                        com.github.damontecres.wholphin.ui.games.BuiltInGame.entries.map {
                            SearchGame(
                                key = "arcade-${it.name}",
                                title = it.title,
                                kind = "Arcade",
                                destination = Destination.GameInfo("arcade:${it.name}"),
                                colors = it.colors,
                            )
                        } +
                        SearchGame(
                            key = "ember",
                            title = "Ember Road",
                            kind = "Adventure",
                            destination = Destination.EmberRoad,
                        )
                val all =
                    try {
                        benflix + romGames()
                    } catch (e: Exception) {
                        Timber.w(e, "Search: couldn't read the game list")
                        benflix
                    }
                val found =
                    all
                        .filter { it.title.lowercase().contains(q) }
                        .sortedWith(compareBy({ !it.title.lowercase().startsWith(q) }, { it.title.length }))
                if (currentQuery?.trim()?.lowercase() == q) _games.value = found
            }
        }

        private val _state = MutableStateFlow(SearchState())
        val state: StateFlow<SearchState> = _state
        val position = MutableStateFlow(RowColumn(0, 0))

        private var currentQuery: String? = null
        private var combinedMode = false

        /** Benflix speed: the searches still running, stopped when the words change */
        private val searchJobs: MutableList<kotlinx.coroutines.Job> = java.util.Collections.synchronizedList(mutableListOf())

        /** Search speed: answers from the last couple of minutes, so going back to words already searched is instant */
        private val libraryAnswers = RecentAnswers<LibraryAnswer>()
        private val askAnswers = RecentAnswers<List<DiscoverItem>>()

        private val _programDialogState = MutableStateFlow(ProgramDialogState())
        val programDialogState: StateFlow<ProgramDialogState> = _programDialogState

        private var userLibraryTypes: Set<BaseItemKind> = emptySet()

        init {
            // Glowfin new look (Ben 2026-11-22): Search opens nearly empty, so nothing (not even Popular) loads
            init()
        }

        /** Glowfin new look: the result opened last, so Back from its page lands on it again */
        var lastOpened: String? = null

        /** Glowfin new look: the letters were tucked away (you were in the results) when you left */
        var resultsWereFocused = false

        private fun init() {
            viewModelScope.launchDefault {
                userLibraryTypes =
                    navDrawerService.state.value.allLibraries
                        .flatMap { it.collectionType.baseItemKinds }
                        .toSet()

                val excludedSearchableTypes =
                    serverRepository.currentUser?.id?.let { userId ->
                        try {
                            keyValueService
                                .get<List<BaseItemKind>>(
                                    userId,
                                    EXCLUDED_SEARCHABLE_TYPES_KEY,
                                    emptyList(),
                                ).firstOrNull()
                        } catch (ex: Exception) {
                            Timber.e(ex, "Error occurred fetching excluded search types")
                            emptyList()
                        }
                    } ?: emptyList()
                val discoverEnabled =
                    serverRepository.currentUser?.id?.let { userId ->
                        try {
                            keyValueService
                                .get(
                                    userId,
                                    INCLUDE_DISCOVER_KEY,
                                    true,
                                ).firstOrNull()
                        } catch (ex: Exception) {
                            Timber.e(ex, "Error occurred fetching excluded search types")
                            true
                        }
                    } ?: true
                val searchableTypes = determineSearchableTypes(excludedSearchableTypes)
                val possibleSearchableTypes = determineSearchableTypes(emptyList())
                _state.update {
                    it.copy(
                        discoverEnabled = discoverEnabled,
                        includedSearchableTypes = searchableTypes,
                        possibleSearchableTypes = possibleSearchableTypes,
                        excludedSearchableTypes = excludedSearchableTypes,
                        results =
                            SnapshotStateMap<BaseItemKind, SearchResult>().apply {
                                searchableTypes.forEach { put(it, SearchResult.NoQuery) }
                            },
                    )
                }
            }
        }

        private fun determineSearchableTypes(excludedSearchableTypes: List<BaseItemKind>): List<BaseItemKind> {
            val tvAccess = serverRepository.currentUserDto?.tvAccess == true
            val searchableTypes =
                allSearchableTypes.filter {
                    val excluded = excludedSearchableTypes.contains(it)
                    // Only include if there's a relevant, accessible library
                    val hasLibrary = userLibraryTypes.contains(it)
                    when (it) {
                        // No library type for person
                        BaseItemKind.PERSON -> !excluded

                        // Remove live tv search if user doesn't have access
                        BaseItemKind.TV_CHANNEL,
                        BaseItemKind.LIVE_TV_PROGRAM,
                        BaseItemKind.TV_PROGRAM,
                        BaseItemKind.PROGRAM,
                        -> tvAccess && !excluded

                        else -> hasLibrary && !excluded
                    }
                }
            return searchableTypes
        }

        fun search(
            query: String?,
            combined: Boolean = false,
            force: Boolean = false,
            /** Trying a suggestion (landing on it): the suggestions stay as they are */
            preview: Boolean = false,
            /**
             * Search speed (Ben 2026-11-21): while typing, what was found for the letters before stays on screen
             * until the new answer is in, so the page doesn't blink to "Searching" on every letter
             */
            keepShowing: Boolean = false,
        ) {
            if (!preview) {
                if (typedQuery != query) _suggestions.value = _suggestions.value.takeIf { query.isNotNullOrBlank() }.orEmpty()
                typedQuery = query
            }
            if (currentQuery == query && combinedMode == combined && !force) {
                return
            }
            currentQuery = query
            combinedMode = combined
            if (force) {
                libraryAnswers.clear()
                askAnswers.clear()
            }
            searchGames(query)
            searchBooks(query)
            // Benflix speed: stop searching for what was typed before, so the server only works on
            // the newest words (and an older, slower answer can't replace the new one)
            synchronized(searchJobs) {
                searchJobs.forEach { it.cancel() }
                searchJobs.clear()
            }
            if (query.isNotNullOrBlank()) {
                // Searched a moment ago (a letter deleted, or a suggestion tried again): it's shown straight away
                val known = if (combined) libraryAnswers.get(query) else null
                _state.update {
                    it.copy(
                        results =
                            SnapshotStateMap<BaseItemKind, SearchResult>().apply {
                                it.includedSearchableTypes.forEach {
                                    put(it, SearchResult.Searching)
                                }
                            },
                        combinedResults =
                            when {
                                known != null -> SearchResult.Success(known.items)
                                keepShowing && it.combinedResults is SearchResult.Success -> it.combinedResults
                                else -> SearchResult.Searching
                            },
                        didYouMean = if (known != null) known.didYouMean else it.didYouMean,
                        plainWords = if (known != null) known.plainWords else it.plainWords,
                    )
                }
                if (known != null) {
                    if (query == typedQuery) _suggestions.value = suggestionsFor(query, known.names + _books.value.map { it.name })
                } else if (combined) {
                    searchCombined(query)
                } else {
                    state.value.includedSearchableTypes.forEach { type ->
                        searchType(query, type)
                    }
                }
                searchSeerr(query, keepShowing)
                searchMusicAsk(query)
            } else {
                _musicAsk.value = emptyList()
                _state.update {
                    it.copy(
                        results =
                            SnapshotStateMap<BaseItemKind, SearchResult>().apply {
                                it.includedSearchableTypes.forEach {
                                    put(it, SearchResult.NoQuery)
                                }
                            },
                        combinedResults = SearchResult.NoQuery,
                    )
                }
            }
        }

        private fun searchType(
            query: String,
            type: BaseItemKind,
        ) {
            searchJobs +=
                viewModelScope.launchIO {
                    try {
                        val items: List<BaseItem> =
                            when (type) {
                                BaseItemKind.LIVE_TV_PROGRAM -> {
                                    val request =
                                        GetItemsRequest(
                                            searchTerm = query,
                                            recursive = true,
                                            includeItemTypes = listOf(type),
                                            fields = ProgramItemFields,
                                            limit = SEARCH_LIMIT,
                                            enableTotalRecordCount = false,
                                        )
                                    api.itemsApi.getItems(request).toBaseItems(api, false)
                                }

                                BaseItemKind.MUSIC_ARTIST -> {
                                    api.artistsApi
                                        .getArtists(
                                            searchTerm = query,
                                            fields = SlimItemFields,
                                            limit = SEARCH_LIMIT,
                                            enableTotalRecordCount = false,
                                        ).toBaseItems(api, false)
                                }

                                BaseItemKind.PERSON -> {
                                    api.personsApi
                                        .getPersons(
                                            searchTerm = query,
                                            fields = SlimItemFields,
                                            limit = SEARCH_LIMIT,
                                        ).toBaseItems(api, false)
                                }

                                else -> {
                                    val request =
                                        GetItemsRequest(
                                            searchTerm = query,
                                            recursive = true,
                                            includeItemTypes = listOf(type),
                                            fields = SlimItemFields,
                                            limit = SEARCH_LIMIT,
                                            enableTotalRecordCount = false,
                                        )
                                    withCloseMatches(
                                        query,
                                        listOf(type),
                                        api.itemsApi.getItems(request).toBaseItems(api, false),
                                    )
                                }
                            }
                        val sorted =
                            items.sortedWith(
                                compareBy<BaseItem> { SearchRelevance.score(it, query) },
                            )
                        Timber.v("Search finished for %s, %s results", type, sorted.size)
                        _state.value.results[type] = SearchResult.Success(sorted)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        if (ex.isCancellation()) return@launchIO
                        Timber.e(ex, "Exception searching for $type")
                        _state.value.results[type] = SearchResult.Error(ex)
                    }
                }
        }

        private fun searchCombined(query: String) {
            searchJobs +=
                viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
                    try {
                        Timber.v("Starting searchCombined")
                        // Search speed (Ben 2026-11-21): everything is asked for at the same time, and movies and
                        // shows are shown the moment Tower answers. Before, they waited for episodes, live TV,
                        // people, music and the plain-words search to all finish first.
                        // Music is looked up on its own (Ben, 2026-11-09): in one shared list of 50, a word like
                        // "star" filled every spot with movies, shows and episodes, so Music always said "Nothing".
                        val types = state.value.includedSearchableTypes
                        val musicTypes = types.filter { it in COMBINED_MUSIC_TYPES }
                        val titleTypes = types.filter { it in FUZZY_TYPES }
                        // Only kinds that have a row (videos and photos were searched but never shown)
                        val otherTypes = types.filter { it in COMBINED_OTHER_TYPES }
                        val musicJob =
                            async {
                                if (musicTypes.isEmpty() || KidsMode.isActive) return@async emptyList()
                                quietly("music") { find(query, musicTypes, SlimItemFields) }
                            }
                        val otherJob =
                            async {
                                if (otherTypes.isEmpty()) return@async emptyList()
                                quietly("episodes, live TV and people") { find(query, otherTypes, SearchRowFields) }
                            }
                        // Benflix "Search in plain words": "funny 90s movie under 2 hours" becomes filters
                        val words = PlainWordsQuery.parse(query)
                        val wordsJob =
                            async {
                                if (!words.understood) return@async emptyList()
                                quietly("plain words") { PlainWordsQuery.search(api, serverRepository.currentUser?.id, words) }
                            }
                        val byScore = compareBy<BaseItem> { SearchRelevance.score(it, query) }
                        val titles = if (titleTypes.isEmpty()) emptyList() else find(query, titleTypes, SearchRowFields)
                        if (titles.isNotEmpty()) {
                            // First look: the movies and shows, in the same order they will end up in
                            val first = titles.sortedWith(byScore)
                            if (query == typedQuery) {
                                _suggestions.value = suggestionsFor(query, first.mapNotNull { it.name } + _books.value.map { it.name })
                            }
                            _state.update {
                                it.copy(combinedResults = SearchResult.Success(first), didYouMean = null, plainWords = null)
                            }
                        }
                        val items = withCloseMatches(query, titleTypes, titles) + otherJob.await() + musicJob.await()
                        val sorted = items.sortedWith(byScore)
                        Timber.v("searchCombined complete %s results", sorted.size)
                        // Nothing has what was typed in its name: the results are close matches for a typo
                        val plain = query.trim().lowercase()
                        val didYouMean =
                            sorted
                                .takeIf { list -> plain.length >= 3 && list.none { it.title?.lowercase()?.contains(plain) == true } }
                                ?.firstOrNull { it.type == BaseItemKind.MOVIE || it.type == BaseItemKind.SERIES }
                                ?.title
                        val understood = wordsJob.await()
                        val combined =
                            when {
                                understood.isEmpty() -> sorted

                                // Few title matches: what the words describe comes first
                                sorted.size < 3 -> (understood + sorted).distinctBy { it.id }

                                else -> (sorted + understood).distinctBy { it.id }
                            }
                        val plainWords = words.label.takeIf { understood.isNotEmpty() && it.isNotBlank() }
                        val names = sorted.mapNotNull { it.name }
                        if (query == typedQuery) {
                            _suggestions.value = suggestionsFor(query, names + _books.value.map { it.name })
                        }
                        val meant = didYouMean.takeIf { plainWords == null }
                        libraryAnswers.put(query, LibraryAnswer(combined, meant, plainWords, names))
                        _state.update {
                            it.copy(
                                combinedResults = SearchResult.Success(combined),
                                didYouMean = meant,
                                plainWords = plainWords,
                            )
                        }
                    } catch (ex: CancellationException) {
                        // A newer search (the next letter typed) replaced this one: stay quiet
                        throw ex
                    } catch (ex: Exception) {
                        if (ex.isCancellation()) return@launch
                        Timber.e(ex, "Exception in combined search")
                        _state.update { it.copy(combinedResults = SearchResult.Error(ex)) }
                    }
                }
        }

        /** One search of Tower for these kinds, without the slow "how many are there in total" count */
        private suspend fun find(
            query: String,
            types: List<BaseItemKind>,
            fields: List<org.jellyfin.sdk.model.api.ItemFields>,
        ): List<BaseItem> =
            api.itemsApi
                .getItems(
                    GetItemsRequest(
                        searchTerm = query,
                        recursive = true,
                        includeItemTypes = types,
                        fields = fields,
                        limit = SEARCH_LIMIT,
                        enableTotalRecordCount = false,
                    ),
                ).content.items
                .map { BaseItem(it, false) }

        /** A side search that fails just finds nothing; the movies and shows still show */
        private suspend fun quietly(
            what: String,
            block: suspend () -> List<BaseItem>,
        ): List<BaseItem> =
            try {
                block()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                if (!ex.isCancellation()) Timber.w(ex, "Search: %s", what)
                emptyList()
            }

        /**
         * Benflix typo-forgiving search: when the server finds fewer than 3 movies or shows,
         * add titles that are only a letter or two off ("avengrs" finds Avengers)
         */
        private suspend fun withCloseMatches(
            query: String,
            types: List<BaseItemKind>,
            items: List<BaseItem>,
        ): List<BaseItem> {
            val wanted = types.filter { it in FUZZY_TYPES }
            if (wanted.isEmpty() || items.count { it.type in wanted } >= 3) return items
            return try {
                val have = items.map { it.id }.toSet()
                val ids =
                    FuzzyTitles
                        .closeMatches(api, serverRepository.currentUser?.id, query, wanted)
                        .filter { it !in have }
                if (ids.isEmpty()) {
                    items
                } else {
                    val extra =
                        api.itemsApi
                            .getItems(
                                GetItemsRequest(
                                    userId = serverRepository.currentUser?.id,
                                    ids = ids,
                                    fields = SearchRowFields,
                                ),
                            ).toBaseItems(api, false)
                    items + extra.sortedBy { ids.indexOf(it.id) }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Typo-forgiving search failed")
                items
            }
        }

        fun setCombinedResults(enabled: Boolean) {
            viewModelScope.launchIO {
                appPreferences.updateData {
                    it.updateSearchPreferences {
                        combinedSearchResults = enabled
                    }
                }
            }
        }

        fun setVoiceSearchButtonVisible(visible: Boolean) {
            viewModelScope.launchIO {
                appPreferences.updateData {
                    it.updateSearchPreferences {
                        showVoiceSearchButton = visible
                    }
                }
            }
        }

        private fun searchSeerr(
            query: String,
            keepShowing: Boolean = false,
        ) {
            askAnswers.get(query)?.let { known ->
                _state.update { it.copy(seerrResults = SearchResult.SuccessSeerr(known)) }
                return
            }
            searchJobs +=
                viewModelScope.launchIO {
                    if (seerrActive.first() && state.value.discoverEnabled) {
                        _state.update {
                            // While typing, the Ask for it row stays until the new one is in
                            val keep = keepShowing && it.seerrResults is SearchResult.SuccessSeerr
                            if (keep) it else it.copy(seerrResults = SearchResult.Searching)
                        }
                        // Benflix "Ask for it": only things that are not on Benflix yet
                        val results =
                            try {
                                seerrService
                                    .search(query)
                                    .map { seerrService.createDiscoverItem(it) }
                                    .filter { it.type == SeerrItemType.MOVIE || it.type == SeerrItemType.TV }
                                    .filter { it.availability != SeerrAvailability.AVAILABLE }
                                    .also { askAnswers.put(query, it) }
                            } catch (ex: CancellationException) {
                                throw ex
                            } catch (ex: Exception) {
                                if (ex.isCancellation()) return@launchIO
                                Timber.w(ex, "Search: ask for it")
                                emptyList()
                            }
                        if (currentQuery != query) return@launchIO
                        _state.update { it.copy(seerrResults = SearchResult.SuccessSeerr(results)) }
                    }
                }
        }

        init {
            addCloseable(voiceInputManager)
            // Search speed: the list of names used for typo-forgiving search is fetched quietly once the page is
            // open, so the first misspelled search doesn't wait for it
            viewModelScope.launchIO {
                kotlinx.coroutines.delay(FUZZY_WARM_DELAY_MS)
                FuzzyTitles.warm(api, serverRepository.currentUser?.id)
            }
        }

        override fun navigateTo(destination: Destination) {
            navigationManager.navigateTo(destination)
        }

        override fun canDelete(
            item: BaseItem,
            appPreferences: AppPreferences,
        ): Boolean = mediaManagementService.canDelete(item, appPreferences)

        override fun deleteItem(
            index: Int,
            item: BaseItem,
        ) {
            deleteItem(context, mediaManagementService, item) {
                viewModelScope.launchDefault {
                    refreshItem(item.id)
                }
            }
        }

        private suspend fun refreshItem(itemId: UUID) {
            try {
                val position = position.value
                val searchResult =
                    if (combinedMode) {
                        state.value.combinedResults
                    } else {
                        state.value.includedSearchableTypes.getOrNull(position.row)?.let {
                            state.value.results[it]
                        }
                    } ?: return
                val items = (searchResult as? SearchResult.Success)?.items ?: return

                Timber.v("Item refresh: position=%s", position)
                val item = items.getOrNull(position.column)
                // Exact item deleted (eg a movie) or deleted item was within the series
                if (item != null && item.id == itemId) {
                    val newItem =
                        api.userLibraryApi
                            .getItem(item.id)
                            .content
                            .let { BaseItem(it) }
                    val newList =
                        SearchResult.Success(
                            items.toMutableList().apply {
                                set(position.column, newItem)
                            },
                        )
                    if (combinedMode) {
                        _state.update {
                            it.copy(
                                combinedResults = newList,
                            )
                        }
                    } else {
                        state.value.includedSearchableTypes.getOrNull(position.row)?.let { type ->
                            state.value.results[type] = newList
                        }
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Error refreshing item %s", itemId)
                showToast(context, "This page didn't refresh. Go back and open it again.")
            }
        }

        override fun setWatched(
            position: Int,
            itemId: UUID,
            played: Boolean,
        ) {
            viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
                favoriteWatchManager.setWatched(itemId, played)
                refreshItem(itemId)
            }
        }

        override fun setFavorite(
            position: Int,
            itemId: UUID,
            favorite: Boolean,
        ) {
            viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
                favoriteWatchManager.setFavorite(itemId, favorite)
                refreshItem(itemId)
            }
        }

        override fun isAdministrator(): Boolean = serverRepository.currentUserDto?.policy?.isAdministrator == true

        override fun sendReportFor(itemId: UUID) = serverReportService.sendMediaReportFor(itemId)

        fun fetchProgramForDialog(programId: UUID) {
            _programDialogState.update { it.copy(loading = DataLoadingState.Loading) }
            viewModelScope.launchDefault {
                try {
                    val result = liveTvService.fetchProgramForDialog(programId)
                    _programDialogState.update { it.copy(loading = DataLoadingState.Success(result)) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching program $programId")
                    _programDialogState.update { it.copy(loading = DataLoadingState.Error(ex)) }
                }
            }
        }

        fun cancelRecording(
            series: Boolean,
            timerId: String?,
        ) {
            viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                try {
                    val result = liveTvService.cancelRecording(series, timerId)
                    // TODO update program card?
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error canceling timer %s, series=%s", timerId, series)
                    showToast(context, "Couldn't cancel that recording. Try again.")
                }
            }
        }

        fun record(
            programId: UUID,
            series: Boolean,
        ) {
            viewModelScope.launchIO {
                try {
                    liveTvService.record(programId, series)
                    // TODO update program card?
                    refreshItem(programId)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error recording %s, series=%s", programId, series)
                    showToast(context, "Couldn't start that recording. Try again.")
                }
            }
        }

        fun onClickExcludeSearchableType(type: BaseItemKind) {
            viewModelScope.launchIO {
                try {
                    val state = state.value
                    val updateSearch: Boolean
                    val newExcludes =
                        state.excludedSearchableTypes.toMutableList().apply {
                            if (type in this) {
                                updateSearch = true
                                remove(type)
                            } else {
                                // If combined, need to update, otherwise just hide the newly excluded results
                                updateSearch = combinedMode
                                add(type)
                            }
                        }
                    Timber.v("newExcludes=%s", newExcludes)
                    serverRepository.currentUser?.id?.let { userId ->
                        keyValueService.save(userId, EXCLUDED_SEARCHABLE_TYPES_KEY, newExcludes)
                    }
                    val searchableTypes = determineSearchableTypes(newExcludes)
                    _state.update {
                        it.copy(
                            includedSearchableTypes = searchableTypes,
                            excludedSearchableTypes = newExcludes,
                        )
                    }
                    if (updateSearch && currentQuery.isNotNullOrBlank()) {
                        Timber.d("Need to update search")
                        search(currentQuery, combinedMode, true)
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception toggling %s", type)
                    showToast(context, "Couldn't search right now. Check your connection and try again.")
                }
            }
        }

        fun onClickExcludeDiscover() {
            viewModelScope.launchIO {
                try {
                    val newIncludeDiscover = state.value.discoverEnabled.not()
                    Timber.v("newIncludeDiscover=%s", newIncludeDiscover)
                    serverRepository.currentUser?.id?.let { userId ->
                        keyValueService.save(userId, INCLUDE_DISCOVER_KEY, newIncludeDiscover)
                    }
                    _state.update {
                        it.copy(
                            discoverEnabled = newIncludeDiscover,
                        )
                    }
                    if (newIncludeDiscover) {
                        currentQuery?.takeIf { it.isNotNullOrBlank() }?.let { query ->
                            searchSeerr(query)
                        }
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception discover")
                    showToast(context, "Couldn't search right now. Check your connection and try again.")
                }
            }
        }

        companion object {
            private const val EXCLUDED_SEARCHABLE_TYPES_KEY = "excludedSearchableTypes"
            private const val INCLUDE_DISCOVER_KEY = "searchIncludeDiscover"
        }
    }

sealed interface SearchResult {
    data object NoQuery : SearchResult

    data object Searching : SearchResult

    data class Error(
        val ex: Exception,
    ) : SearchResult

    data class Success(
        val items: List<BaseItem?>,
    ) : SearchResult

    data class SuccessSeerr(
        val items: List<DiscoverItem>,
    ) : SearchResult
}

data class SearchState(
    val results: SnapshotStateMap<BaseItemKind, SearchResult> = SnapshotStateMap(),
    val seerrResults: SearchResult = SearchResult.NoQuery,
    val combinedResults: SearchResult = SearchResult.NoQuery,
    val possibleSearchableTypes: List<BaseItemKind> = emptyList(),
    val includedSearchableTypes: List<BaseItemKind> = emptyList(),
    val excludedSearchableTypes: List<BaseItemKind> = emptyList(),
    val discoverEnabled: Boolean = true,
    /** Benflix: the closest name, when nothing matched what was typed exactly */
    val didYouMean: String? = null,
    /** Benflix: what a plain-words search understood, like "Comedy · Movies · 1990s" */
    val plainWords: String? = null,
)

private val allSearchableTypes =
    listOf(
        BaseItemKind.MOVIE,
        BaseItemKind.SERIES,
        BaseItemKind.EPISODE,
        BaseItemKind.BOX_SET,
        BaseItemKind.PERSON,
        BaseItemKind.TV_CHANNEL,
        BaseItemKind.LIVE_TV_PROGRAM,
        BaseItemKind.MUSIC_ALBUM,
        BaseItemKind.MUSIC_ARTIST,
        BaseItemKind.AUDIO,
        BaseItemKind.MUSIC_VIDEO,
        BaseItemKind.PLAYLIST,
        BaseItemKind.VIDEO,
        BaseItemKind.PHOTO,
        BaseItemKind.PHOTO_ALBUM,
    )

private const val SEARCH_LIMIT = 50

/** Search rows also need where a title lives and its genres, to put anime in its own row */
private val SearchRowFields = SlimItemFields + org.jellyfin.sdk.model.api.ItemFields.PATH + org.jellyfin.sdk.model.api.ItemFields.GENRES

/** Searched on their own in the combined search, so movies and shows can't crowd them out */
private val COMBINED_MUSIC_TYPES =
    setOf(BaseItemKind.AUDIO, BaseItemKind.MUSIC_ALBUM, BaseItemKind.MUSIC_ARTIST, BaseItemKind.PLAYLIST)
private val FUZZY_TYPES = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.BOX_SET)

/** Searched beside the movies and shows, and added to the rows when they answer */
private val COMBINED_OTHER_TYPES =
    setOf(BaseItemKind.EPISODE, BaseItemKind.PERSON, BaseItemKind.TV_CHANNEL, BaseItemKind.LIVE_TV_PROGRAM)

private const val FUZZY_WARM_DELAY_MS = 1500L

/** What one search of the library found, kept for a short while */
private class LibraryAnswer(
    val items: List<BaseItem>,
    val didYouMean: String?,
    val plainWords: String?,
    /** Names for the whole-name suggestions */
    val names: List<String>,
)

/** A small memory of the newest answers; each is forgotten after [keepMs] */
internal class RecentAnswers<T>(
    private val keepMs: Long = 120_000L,
    private val max: Int = 24,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val answers = LinkedHashMap<String, Pair<Long, T>>()

    @Synchronized
    fun get(key: String): T? {
        val (at, answer) = answers[key] ?: return null
        if (now() - at > keepMs) {
            answers.remove(key)
            return null
        }
        return answer
    }

    @Synchronized
    fun put(
        key: String,
        answer: T,
    ) {
        answers.remove(key)
        answers[key] = now() to answer
        while (answers.size > max) answers.remove(answers.keys.first())
    }

    @Synchronized
    fun clear() = answers.clear()
}

private val MARKS = Regex("\\p{M}")
private val NOT_PLAIN = Regex("[^a-z0-9]+")

/** For matching names: no capitals, accents or punctuation */
internal fun searchPlain(text: String?): String =
    java.text.Normalizer
        .normalize(text.orEmpty(), java.text.Normalizer.Form.NFKD)
        .replace(MARKS, "")
        .lowercase()
        .replace(NOT_PLAIN, " ")
        .trim()

/**
 * Up to 6 whole names for what was typed: the exact name first, then names starting with it, then names that have it.
 * Ben 2026-11-19: typing "Mulan" only offered "Mulan II", because the name that matched exactly was left out.
 */
internal fun suggestionsFor(
    query: String,
    names: List<String>,
): List<String> {
    val q = searchPlain(query)
    if (q.isEmpty()) return emptyList()
    val seen = mutableSetOf<String>()
    return names
        .map { it.trim() }
        .filter { it.isNotEmpty() && searchPlain(it).contains(q) && seen.add(searchPlain(it)) }
        .withIndex()
        .sortedBy { (i, n) -> suggestionRank(searchPlain(n), q) * 10_000 + i }
        .map { it.value }
        .take(6)
}

private fun suggestionRank(
    name: String,
    q: String,
): Int =
    when {
        name == q -> 0
        name.startsWith(q) -> 1
        else -> 2
    }

/** A song or album from the music catalogue that can be asked for from search */
data class SearchMusicAsk(
    /** song or album */
    val kind: String,
    val id: Long,
    val title: String,
    val artist: String,
    val cover: String?,
    /** Words when it's already been asked for, like "Downloading, 40%" */
    val asked: String?,
    val albumId: Long,
    val albumTitle: String,
) {
    val key: String get() = "$kind-$id"
}

private const val MUSIC_ASK_SONGS = 12
private const val MUSIC_ASK_ALBUMS = 6

/** A game in the search results' Games door */
data class SearchGame(
    val key: String,
    val title: String,
    /** "NES", "Arcade", "Benflix game" */
    val kind: String,
    /** Where a Benflix game opens; null for your own games (they open on the Games page) */
    val destination: Destination? = null,
    val coverUrl: String? = null,
    val token: String? = null,
    val colors: List<androidx.compose.ui.graphics.Color> = emptyList(),
    val icon: Int? = null,
    /** Your own game on Tower */
    val romId: Int? = null,
)

/** True when this failure only happened because the search was stopped (often wrapped by the network library) */
internal fun Throwable.isCancellation(): Boolean {
    var e: Throwable? = this
    var depth = 0
    while (e != null && depth < 8) {
        if (e is CancellationException) return true
        e = e.cause
        depth++
    }
    return false
}
