package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.JellyfinServerDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.BenflixAccent
import com.github.damontecres.wholphin.preferences.SubtitleModePreference
import com.github.damontecres.wholphin.preferences.updateBenflixPreferences
import com.github.damontecres.wholphin.preferences.updateSubtitlePreferences
import com.github.damontecres.wholphin.services.BenflixBedtime
import com.github.damontecres.wholphin.services.BenflixDeviceCheck
import com.github.damontecres.wholphin.services.BenflixSettingsSync
import com.github.damontecres.wholphin.services.DeviceChoices
import com.github.damontecres.wholphin.services.DeviceProfileService
import com.github.damontecres.wholphin.services.DeviceReport
import com.github.damontecres.wholphin.services.DisplayPreferencesService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.LiteMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.ProfilePictureSync
import com.github.damontecres.wholphin.ui.components.SearchEditTextBox
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.setup.AvatarChoices
import com.github.damontecres.wholphin.ui.setup.BenflixAvatars
import com.github.damontecres.wholphin.ui.setup.PosterWall
import com.github.damontecres.wholphin.ui.setup.ReadableTextShadow
import com.github.damontecres.wholphin.ui.setup.benflixAvatarFor
import com.github.damontecres.wholphin.ui.setup.rememberIdColor
import com.github.damontecres.wholphin.ui.theme.BenflixRed
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.ui.theme.ProfileColours
import com.github.damontecres.wholphin.ui.theme.ProfileThemeChoices
import com.github.damontecres.wholphin.ui.theme.toColor
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.scrollAsOneBlock
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import javax.inject.Inject

/**
 * The steps of the welcome setup, in order. Every step must be answered (only the parent PIN can be
 * turned down with "No thanks"). [FINISH] is the "You're all set" screen, not a question.
 */
enum class WelcomeStep { HELLO, DEVICE_CHECK, PICTURE, SUBTITLES, EASY_READ, PARENT_PIN, FAVORITES, HIDE_GENRES, FINISH }

/** The steps that get a dot at the top (everything but the finish screen) */
private val DottedSteps = WelcomeStep.entries.filter { it != WelcomeStep.FINISH }

/** The steps that are questions (Hello, the device check and the finish screen aren't) */
private val QuestionCount =
    WelcomeStep.entries.count { it != WelcomeStep.HELLO && it != WelcomeStep.DEVICE_CHECK && it != WelcomeStep.FINISH }

/** A poster on the "Pick a few things you love" wall */
data class WelcomePoster(
    val id: UUID,
    val name: String,
    val imageUrl: String?,
)

/** Subtitle sizes offered in the welcome setup (the same font size as Settings > Subtitles) */
private enum class SubtitleSize(
    val label: String,
    val fontSize: Int,
) {
    SMALL("Small", 20),
    MEDIUM("Medium", 24),
    LARGE("Large", 32),
    ;

    companion object {
        fun nearest(fontSize: Int): SubtitleSize = entries.minBy { kotlin.math.abs(it.fontSize - fontSize) }
    }
}

/**
 * Benflix welcome setup: the first time a person uses Benflix, a check of this device and a few quick
 * questions (picture and colour, subtitles, text size, parent PIN, favourites, genres to hide). It happens
 * once per person, not per device: it's remembered on the server, so a new TV just skips it.
 * If the app closes halfway, it opens again at the same step (saved on this device).
 */
@HiltViewModel
class WelcomeViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val serverDao: JellyfinServerDao,
        private val preferences: DataStore<AppPreferences>,
        private val displayPreferencesService: DisplayPreferencesService,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val imageUrlService: ImageUrlService,
        private val profilePictureSync: ProfilePictureSync,
        private val deviceProfileService: DeviceProfileService,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        /** Save the picked picture here and on their Jellyfin account, so it follows them */
        fun pickPicture(index: Int) {
            val current = serverRepository.current.value ?: return
            AvatarChoices.set(current.user.id, index)
            viewModelScope.launch(WholphinDispatchers.IO) {
                profilePictureSync.pick(current.server.url, current.user, index)
            }
        }

        private val stepPrefs = context.getSharedPreferences(STEP_PREFS, Context.MODE_PRIVATE)

        private val _step = MutableStateFlow(savedStep())
        val step: StateFlow<WelcomeStep> = _step

        private val _posters = MutableStateFlow<List<WelcomePoster>>(emptyList())
        val posters: StateFlow<List<WelcomePoster>> = _posters

        /** True once the poster wall has loaded (or failed to) */
        private val _postersLoaded = MutableStateFlow(false)
        val postersLoaded: StateFlow<Boolean> = _postersLoaded

        /** Picked favourites, in the order they were picked */
        private val _picked = MutableStateFlow<Map<UUID, WelcomePoster>>(emptyMap())
        val picked: StateFlow<Map<UUID, WelcomePoster>> = _picked

        /** Likes already saved on the server (when they left the favourites step) */
        private var savedLikes = emptySet<UUID>()

        private val _subtitles = MutableStateFlow<SubtitleModePreference?>(null)
        val subtitles: StateFlow<SubtitleModePreference?> = _subtitles

        /** A real still from one of their movies, for the subtitle sample */
        private val _still = MutableStateFlow<String?>(null)
        val still: StateFlow<String?> = _still

        private val _profileImage = MutableStateFlow<String?>(null)
        val profileImage: StateFlow<String?> = _profileImage

        private val _searchQuery = MutableStateFlow("")
        val searchQuery: StateFlow<String> = _searchQuery

        private val _searchResults = MutableStateFlow<List<WelcomePoster>>(emptyList())
        val searchResults: StateFlow<List<WelcomePoster>> = _searchResults
        private var searchJob: Job? = null

        /** The device check: what it found and what it set (null while it's checking) */
        private val _device = MutableStateFlow<Pair<DeviceReport, DeviceChoices>?>(null)
        val device: StateFlow<Pair<DeviceReport, DeviceChoices>?> = _device
        private val _deviceFailed = MutableStateFlow(false)
        val deviceFailed: StateFlow<Boolean> = _deviceFailed

        val accent = preferences.data.map { it.benflixPreferences.accent }
        val easyRead = preferences.data.map { it.benflixPreferences.easyRead }
        val subtitleFontSize = preferences.data.map { it.interfacePreferences.subtitlesPreferences.fontSize }

        val userName: String get() = serverRepository.currentUser?.name.orEmpty()
        val userId: UUID? get() = serverRepository.currentUser?.id

        init {
            viewModelScope.launch {
                try {
                    _posters.value = withContext(WholphinDispatchers.IO) { loadPosters() }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Welcome: couldn't load posters")
                }
                _postersLoaded.value = true
            }
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    _still.value = loadStill()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Welcome: couldn't load a still for the subtitle sample")
                }
            }
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    val id = userId ?: return@launch
                    val tag =
                        api.userApi
                            .getCurrentUser()
                            .content.primaryImageTag ?: return@launch
                    _profileImage.value = api.imageApi.getUserImageUrl(id, tag = tag)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Welcome: couldn't load the profile picture")
                }
            }
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    val report = BenflixDeviceCheck.detect(context, deviceProfileService.mediaCodecCapabilitiesTest)
                    _device.value = report to BenflixDeviceCheck.apply(context, preferences, report)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Welcome: device check failed")
                    _deviceFailed.value = true
                }
            }
        }

        private fun stepKey(): String? = userId?.toString()

        private fun savedStep(): WelcomeStep {
            val key = stepKey() ?: return WelcomeStep.HELLO
            val name = stepPrefs.getString(key, null) ?: return WelcomeStep.HELLO
            return WelcomeStep.entries.firstOrNull { it.name == name } ?: WelcomeStep.HELLO
        }

        private fun goTo(step: WelcomeStep) {
            _step.value = step
            stepKey()?.let { key -> stepPrefs.edit { putString(key, step.name) } }
        }

        fun next() {
            val steps = WelcomeStep.entries
            val current = _step.value
            if (current == WelcomeStep.FAVORITES) saveLikes()
            val index = steps.indexOf(current)
            if (index < steps.lastIndex) {
                goTo(steps[index + 1])
            } else {
                finish()
            }
        }

        /** Back one step. On the first step it does nothing (the setup can't be left halfway) */
        fun back(): Boolean {
            val index = WelcomeStep.entries.indexOf(_step.value)
            if (index <= 0) return false
            goTo(WelcomeStep.entries[index - 1])
            return true
        }

        fun setAccent(accent: BenflixAccent) {
            userId?.let { ProfileColours.set(context, it, accent) }
            viewModelScope.launch { preferences.updateData { it.updateBenflixPreferences { this.accent = accent } } }
        }

        fun setEasyRead(on: Boolean) {
            viewModelScope.launch { preferences.updateData { it.updateBenflixPreferences { easyRead = on } } }
        }

        fun setSubtitleSize(size: Int) {
            viewModelScope.launch { preferences.updateData { it.updateSubtitlePreferences { fontSize = size } } }
        }

        fun setSubtitles(mode: SubtitleModePreference) {
            _subtitles.value = mode
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    val user = serverRepository.currentUserFlow.first { it != null }!!
                    serverDao.updateUser(user.copy(appPreferences = user.appPreferences.copy(subtitleMode = mode)))
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Welcome: couldn't save the subtitle choice")
                }
            }
        }

        fun togglePick(poster: WelcomePoster) {
            val now = _picked.value
            _picked.value = if (poster.id in now) now - poster.id else now + (poster.id to poster)
        }

        /** Look up a title they love (Jellyfin search), a moment after they stop typing */
        fun search(query: String) {
            _searchQuery.value = query
            searchJob?.cancel()
            if (query.isBlank()) {
                _searchResults.value = emptyList()
                return
            }
            searchJob =
                viewModelScope.launch {
                    delay(SEARCH_DELAY_MS)
                    try {
                        _searchResults.value =
                            withContext(WholphinDispatchers.IO) {
                                api.itemsApi
                                    .getItems(
                                        GetItemsRequest(
                                            userId = userId,
                                            searchTerm = query.trim(),
                                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                            recursive = true,
                                            limit = SEARCH_LIMIT,
                                        ),
                                    ).content.items
                                    .map { it.toPoster() }
                            }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Welcome: search failed")
                    }
                }
        }

        /** Save the 👍 likes for their picks (and take back any they unpicked after going back) */
        private fun saveLikes() {
            val now = _picked.value.keys
            val added = now - savedLikes
            val removed = savedLikes - now
            savedLikes = now
            if (added.isEmpty() && removed.isEmpty()) return
            viewModelScope.launch(WholphinDispatchers.IO) {
                added.forEach { setLike(it, true) }
                removed.forEach { setLike(it, null) }
            }
        }

        private suspend fun setLike(
            itemId: UUID,
            likes: Boolean?,
        ) {
            try {
                favoriteWatchManager.setLikes(itemId, likes)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Welcome: couldn't save a like")
            }
        }

        /** Go to Home (from the finish screen): remember on the server that it's done, and go Home */
        fun finish() {
            val id = userId
            saveLikes()
            stepKey()?.let { key -> stepPrefs.edit { remove(key) } }
            viewModelScope.launch(WholphinDispatchers.IO) {
                if (id != null) {
                    try {
                        markWelcomed(displayPreferencesService, id)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Welcome: couldn't save that it's done")
                    }
                }
                withContext(WholphinDispatchers.Main) { navigationManager.reloadHome() }
            }
        }

        private fun BaseItemDto.toPoster() =
            WelcomePoster(
                id = id,
                name = name.orEmpty(),
                imageUrl = imageUrlService.getItemImageUrl(itemId = id, imageType = ImageType.PRIMARY, maxWidth = 300),
            )

        private suspend fun fetch(
            types: List<BaseItemKind>,
            parentId: UUID? = null,
            genres: List<String>? = null,
        ): List<BaseItemDto> =
            try {
                api.itemsApi
                    .getItems(
                        GetItemsRequest(
                            userId = userId,
                            parentId = parentId,
                            includeItemTypes = types,
                            genres = genres,
                            recursive = true,
                            imageTypes = listOf(ImageType.PRIMARY),
                            sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            limit = POOL_EACH,
                        ),
                    ).content.items
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Welcome: couldn't load some posters")
                emptyList()
            }

        /** A mix of movies, shows and anime (from Anime libraries, or the Anime genre), taking turns */
        private suspend fun loadPosters(): List<WelcomePoster> {
            val animeLibraries =
                try {
                    api.userViewsApi
                        .getUserViews(userId = userId)
                        .content.items
                        .filter { it.name.orEmpty().contains("anime", ignoreCase = true) }
                        .map { it.id }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Welcome: couldn't list libraries")
                    emptyList()
                }
            val animeTypes = listOf(BaseItemKind.SERIES, BaseItemKind.MOVIE)
            val anime =
                if (animeLibraries.isNotEmpty()) {
                    animeLibraries.flatMap { fetch(animeTypes, parentId = it) }
                } else {
                    fetch(animeTypes, genres = listOf("Anime"))
                }
            val animeIds = anime.map { it.id }.toSet()
            val movies = fetch(listOf(BaseItemKind.MOVIE)).filter { it.id !in animeIds }
            val shows = fetch(listOf(BaseItemKind.SERIES)).filter { it.id !in animeIds }
            val lists = listOf(movies.shuffled(), shows.shuffled(), anime.shuffled())
            val mixed = mutableListOf<BaseItemDto>()
            val seen = mutableSetOf<UUID>()
            var i = 0
            while (mixed.size < POSTERS_SHOWN && lists.any { i < it.size }) {
                lists.forEach { list ->
                    val item = list.getOrNull(i)
                    if (item != null && mixed.size < POSTERS_SHOWN && seen.add(item.id)) mixed += item
                }
                i++
            }
            return mixed.map { it.toPoster() }
        }

        private suspend fun loadStill(): String? {
            val movie =
                api.itemsApi
                    .getItems(
                        GetItemsRequest(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.MOVIE),
                            recursive = true,
                            imageTypes = listOf(ImageType.BACKDROP),
                            sortBy = listOf(ItemSortBy.RANDOM),
                            limit = 1,
                        ),
                    ).content.items
                    .firstOrNull() ?: return null
            return imageUrlService.getItemImageUrl(itemId = movie.id, imageType = ImageType.BACKDROP, maxWidth = 1280)
        }

        companion object {
            private const val POOL_EACH = 30
            private const val POSTERS_SHOWN = 24
            private const val SEARCH_LIMIT = 18
            private const val SEARCH_DELAY_MS = 350L
            private const val STEP_PREFS = "benflix_welcome_step"

            /** At least this many favourites before Next (fewer only when the library is smaller) */
            const val MIN_PICKS = 3

            // Benflix: "2" so everyone, including people from before the welcome setup existed, gets it once
            // (the first version skipped anyone who had already watched something)
            const val KEY = "benflix.welcomed3"

            suspend fun markWelcomed(
                displayPreferencesService: DisplayPreferencesService,
                userId: UUID,
            ) = displayPreferencesService.updateDisplayPreferences(userId) { put(KEY, "1") }
        }
    }

/**
 * Decides once per sign-in whether to open the welcome setup. Every grown-up account gets it once,
 * new or old; after they finish it's remembered on the server, so it never shows again on any device.
 */
@HiltViewModel
class WelcomeGateViewModel
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val displayPreferencesService: DisplayPreferencesService,
        private val settingsSync: BenflixSettingsSync,
        private val navigationManager: NavigationManager,
    ) : ViewModel() {
        fun check() {
            if (KidsMode.isActive || com.github.damontecres.wholphin.services.GuestMode.isActive) return
            val userId = serverRepository.currentUser?.id ?: return
            if (userId in checked) return
            checked += userId
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    // Wait for this person's saved settings to load first
                    withTimeoutOrNull(SYNC_WAIT_MS) { settingsSync.ready.first { it == userId } }
                    val prefs = displayPreferencesService.getDisplayPreferences(userId).customPrefs
                    if (prefs[WelcomeViewModel.KEY] != null) return@launch
                    withContext(WholphinDispatchers.Main) { navigationManager.navigateTo(Destination.Welcome) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Welcome: couldn't check")
                }
            }
        }

        companion object {
            private const val SYNC_WAIT_MS = 8_000L

            /** People already checked since the app started */
            private val checked = mutableSetOf<UUID>()
        }
    }

/** Put on Home: opens the welcome setup the first time a person uses Benflix */
@Composable
fun WelcomeGate(viewModel: WelcomeGateViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.check() }
}

@Composable
fun WelcomePage(
    modifier: Modifier = Modifier,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val step by viewModel.step.collectAsState()
    val posters by viewModel.posters.collectAsState()
    val device by viewModel.device.collectAsState()
    val accent by viewModel.accent.collectAsState(BenflixAccent.ACCENT_RED)
    val isTv = rememberIsTv()
    val context = LocalContext.current
    // The device check may switch Lite mode on, so look again once it's done
    val lite = remember(device) { LiteMode.isOn(context) }
    val wall = remember(posters) { posters.mapNotNull { it.imageUrl } }
    // Back goes back a step; on the first step it does nothing (the setup is only done once it's finished)
    BackHandler { viewModel.back() }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color(0xFF0B0B0D)),
    ) {
        // The dimmed sliding poster wall from Who's watching (a plain dark background in Lite mode),
        // with a dark scrim on top so the text stays easy to read
        if (!lite) {
            PosterWall(wall, Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .45f)),
            )
        }
        // Softly tinted with their colour, like profile themes tint Home
        if (accent.tintsPage()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(accent.toColor().copy(alpha = PAGE_TINT)),
            )
        }
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (isTv) 48.dp else 16.dp, vertical = if (isTv) 32.dp else 16.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("BENFLIX", fontFamily = BenflixTitleFont, fontSize = 30.sp, color = BenflixRed, modifier = Modifier.weight(1f))
                    if (step != WelcomeStep.FINISH) StepDots(step)
                }
                when (step) {
                    WelcomeStep.HELLO -> HelloStep(viewModel)
                    WelcomeStep.DEVICE_CHECK -> DeviceCheckStep(viewModel)
                    WelcomeStep.PICTURE -> PictureStep(viewModel, isTv)
                    WelcomeStep.SUBTITLES -> SubtitlesStep(viewModel)
                    WelcomeStep.EASY_READ -> EasyReadStep(viewModel)
                    WelcomeStep.PARENT_PIN -> ParentPinStep(viewModel)
                    WelcomeStep.FAVORITES -> FavoritesStep(viewModel, isTv)
                    WelcomeStep.HIDE_GENRES -> HideGenresStep(viewModel)
                    WelcomeStep.FINISH -> FinishStep(viewModel, isTv)
                }
            }
        }
    }
}

/** Benflix red keeps the classic black look and Mono is all greys, so only the others tint (like profile themes) */
private fun BenflixAccent.tintsPage(): Boolean =
    this != BenflixAccent.ACCENT_RED && this != BenflixAccent.ACCENT_MONO && this != BenflixAccent.UNRECOGNIZED

private const val PAGE_TINT = 0.10f

/** Small dots for the steps: the current one wider and filled, done ones dimmer */
@Composable
private fun StepDots(step: WelcomeStep) {
    val index = DottedSteps.indexOf(step)
    val description = "Step ${index + 1} of ${DottedSteps.size}"
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        DottedSteps.forEachIndexed { i, _ ->
            val color =
                when {
                    i == index -> Color.White
                    i < index -> Color.White.copy(alpha = .45f)
                    else -> Color.White.copy(alpha = .18f)
                }
            Box(
                Modifier
                    .size(width = if (i == index) 22.dp else 8.dp, height = 8.dp)
                    .background(color, CircleShape),
            )
        }
    }
}

@Composable
private fun StepTitle(
    title: String,
    subtitle: String? = null,
) {
    Text(title, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
    if (subtitle != null) {
        Text(subtitle, fontSize = 18.sp, color = Color.White.copy(alpha = .85f), textAlign = TextAlign.Center)
    }
}

/** Back (after the first step) and the main button, at the bottom of each step. There's no Skip: every step counts */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepButtons(
    viewModel: WelcomeViewModel,
    nextLabel: String = "Next",
    focus: FocusRequester? = null,
    enabled: Boolean = true,
    onNext: () -> Unit = viewModel::next,
    extra: @Composable () -> Unit = {},
) {
    val step by viewModel.step.collectAsState()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        if (step != WelcomeStep.HELLO) {
            OutlinedButton(onClick = { viewModel.back() }) { Text("Back") }
        }
        Button(
            onClick = onNext,
            enabled = enabled,
            modifier = if (focus != null) Modifier.focusRequester(focus) else Modifier,
        ) {
            Text(nextLabel)
        }
        extra()
    }
}

/** Their profile tile, like on Who's watching: their picture in a rounded square with their name under it */
@Composable
private fun ProfileTile(
    userId: UUID,
    name: String,
    imageUrl: String?,
    accent: BenflixAccent,
    size: Dp,
) {
    AvatarChoices.init(LocalContext.current)
    val chosen = AvatarChoices.get(userId)?.let { BenflixAvatars.getOrNull(it) }
    val shape = RoundedCornerShape(size * .09f)
    var imageError by remember(imageUrl) { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(size)
                    .clip(shape)
                    .background(rememberIdColor(userId))
                    .border(BorderStroke(3.dp, accent.toColor()), shape),
        ) {
            if (chosen == null && !imageUrl.isNullOrBlank() && !imageError) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    onError = { imageError = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Image(
                    painter = painterResource(chosen ?: benflixAvatarFor(userId, name)),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge.copy(shadow = ReadableTextShadow),
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = size + 40.dp),
        )
    }
}

@Composable
private fun HelloStep(viewModel: WelcomeViewModel) {
    val focus = remember { FocusRequester() }
    val accent by viewModel.accent.collectAsState(BenflixAccent.ACCENT_RED)
    val image by viewModel.profileImage.collectAsState()
    val isTv = rememberIsTv()
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    viewModel.userId?.let { id ->
        ProfileTile(id, viewModel.userName, image, accent, size = 110.dp)
    }
    Text(
        "Hi ${viewModel.userName.ifBlank { "there" }}! Let's make Benflix yours.",
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = TextAlign.Center,
    )
    Text("Takes about a minute", fontSize = 18.sp, color = Color.White.copy(alpha = .7f), textAlign = TextAlign.Center)
    Text(
        "A quick check of this ${if (isTv) "TV" else "phone"}, then $QuestionCount quick questions. " +
            "You can change any of it later in Settings.",
        fontSize = 18.sp,
        color = Color.White.copy(alpha = .85f),
        textAlign = TextAlign.Center,
    )
    StepButtons(viewModel, nextLabel = "Let's go", focus = focus)
}

/** Looks at this device, sets Lite mode and the screensaver to suit it, and says what it found */
@Composable
private fun DeviceCheckStep(viewModel: WelcomeViewModel) {
    val device by viewModel.device.collectAsState()
    val failed by viewModel.deviceFailed.collectAsState()
    val isTv = rememberIsTv()
    val word = if (isTv) "TV" else "phone"
    val focus = remember { FocusRequester() }
    val done = device != null || failed
    LaunchedEffect(done) { if (done) focus.tryRequestFocus() }
    StepTitle("Let's check this $word", "So Benflix runs as smoothly as it can here.")
    val panel =
        Modifier
            .fillMaxWidth()
            .background(Color(0xCC16161A), RoundedCornerShape(12.dp))
            .padding(16.dp)
    val result = device
    when {
        result != null -> {
            val (report, choices) = result
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = panel) {
                Text(
                    "This ${report.deviceWord}: ${report.hardwareLine()}.",
                    fontSize = 18.sp,
                    color = Color.White,
                )
                Text(report.playsLine(), fontSize = 18.sp, color = Color.White.copy(alpha = .85f))
                Text("We set Benflix to:", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("• " + liteLine(choices, report.deviceWord), fontSize = 17.sp, color = Color.White.copy(alpha = .9f))
                Text("• " + motionLine(choices), fontSize = 17.sp, color = Color.White.copy(alpha = .9f))
            }
            Text(
                if (report.weak) {
                    if (report.isTv) {
                        "This TV is on the older side, so Benflix keeps things light. For the best Benflix, a newer " +
                            "streaming device helps: a Google TV Streamer, a Chromecast with Google TV (4K) or an " +
                            "Nvidia Shield plays everything your server has in full quality."
                    } else {
                        "This phone is on the older side, so Benflix keeps things light. A newer phone would play " +
                            "everything your server has in full quality."
                    }
                } else {
                    "This ${report.deviceWord} is a great fit for Benflix. It plays nearly everything in full quality."
                },
                fontSize = 17.sp,
                color = if (report.weak) Color(0xFFFFD27A) else Color(0xFF9BE59B),
                textAlign = TextAlign.Center,
            )
            Text(
                "You can change these later in Settings: Lite mode is in the speed meter (tap Installed version " +
                    "5 times), and screensaver motion is under Screensaver.",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = .7f),
                textAlign = TextAlign.Center,
            )
        }

        failed -> {
            Text(
                "We couldn't check this $word, and that's okay. Benflix will still work.",
                fontSize = 18.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }

        else -> {
            Text("Checking this $word…", fontSize = 18.sp, color = Color.White, textAlign = TextAlign.Center)
        }
    }
    StepButtons(viewModel, focus = focus, enabled = done)
}

private fun liteLine(
    choices: DeviceChoices,
    word: String,
): String {
    val base =
        if (choices.lite) {
            "Lite mode on (smoother on this $word: no trailer previews on Home, still backgrounds)"
        } else {
            "Lite mode off (trailer previews play on Home)"
        }
    return if (choices.liteByYou) "$base. You picked this before, so we kept it." else base
}

private fun motionLine(choices: DeviceChoices): String {
    val base =
        if (choices.screensaverMotion) "Screensaver motion on" else "Screensaver motion off (a still slideshow, lighter work)"
    return if (choices.screensaverMotionByYou) "$base. You picked this before, so we kept it." else base
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PictureStep(
    viewModel: WelcomeViewModel,
    isTv: Boolean,
) {
    val userId = viewModel.userId ?: return
    AvatarChoices.init(LocalContext.current)
    val chosen = AvatarChoices.get(userId)
    val accent by viewModel.accent.collectAsState(BenflixAccent.ACCENT_RED)
    val image by viewModel.profileImage.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    StepTitle("Pick your picture and colour")
    val preview: @Composable () -> Unit = {
        // A live preview of their Who's watching tile
        ProfileTile(userId, viewModel.userName, image, accent, size = if (isTv) 150.dp else 120.dp)
    }
    val pickers: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your picture", fontSize = 18.sp, color = Color.White)
            // A flowing row, so the pictures wrap on a phone instead of running off the edge
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                maxItemsInEachRow = AVATARS_PER_ROW,
            ) {
                BenflixAvatars.forEachIndexed { index, res ->
                    Surface(
                        onClick = { viewModel.pickPicture(index) },
                        shape = ClickableSurfaceDefaults.shape(CircleShape),
                        border =
                            ClickableSurfaceDefaults.border(
                                border = Border(BorderStroke(3.dp, if (index == chosen) accent.toColor() else Color.Transparent)),
                                focusedBorder = Border(BorderStroke(3.dp, Color.White)),
                            ),
                        modifier =
                            Modifier
                                .size(60.dp)
                                .then(if (index == 0) Modifier.focusRequester(focus) else Modifier),
                    ) {
                        Image(
                            painter = painterResource(res),
                            contentDescription = "Picture ${index + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    }
                }
            }
            Text("Your colour", fontSize = 18.sp, color = Color.White, modifier = Modifier.padding(top = 4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ProfileThemeChoices.forEach { color ->
                    Surface(
                        onClick = { viewModel.setAccent(color) },
                        shape = ClickableSurfaceDefaults.shape(CircleShape),
                        colors = ClickableSurfaceDefaults.colors(containerColor = color.toColor(), focusedContainerColor = color.toColor()),
                        border =
                            ClickableSurfaceDefaults.border(
                                border = Border(BorderStroke(3.dp, if (color == accent) Color.White else Color.Transparent)),
                                focusedBorder = Border(BorderStroke(3.dp, Color.White)),
                            ),
                        modifier = Modifier.size(44.dp),
                    ) {}
                }
            }
        }
    }
    if (isTv) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            preview()
            pickers()
        }
    } else {
        preview()
        pickers()
    }
    StepButtons(viewModel)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitlesStep(viewModel: WelcomeViewModel) {
    val chosen by viewModel.subtitles.collectAsState()
    val still by viewModel.still.collectAsState()
    val fontSize by viewModel.subtitleFontSize.collectAsState(SubtitleSize.MEDIUM.fontSize)
    val size = SubtitleSize.nearest(if (fontSize > 0) fontSize else SubtitleSize.MEDIUM.fontSize)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    StepTitle("Do you like subtitles?", "This is for movies and shows in your own language. Anime always gets English subtitles.")
    val options =
        listOf(
            "Off" to SubtitleModePreference.NONE,
            "Always on" to SubtitleModePreference.ALWAYS,
            "Only for other languages" to SubtitleModePreference.SMART,
        )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        options.forEachIndexed { i, (label, mode) ->
            val selected = chosen == mode
            Button(
                onClick = { viewModel.setSubtitles(mode) },
                modifier = if (i == 0) Modifier.focusRequester(focus) else Modifier,
            ) {
                Text(if (selected) "✓ $label" else label)
            }
        }
    }
    Text(
        "Only for other languages: subtitles turn on for things like a Japanese anime or a French film.",
        fontSize = 15.sp,
        color = Color.White.copy(alpha = .75f),
        textAlign = TextAlign.Center,
    )
    // A sample of how subtitles look, over a still from one of their own movies
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier =
            Modifier
                .fillMaxWidth(.7f)
                .aspectRatio(16f / 7f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF26323C)),
    ) {
        if (still != null) {
            AsyncImage(
                model = still,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = "This is how your subtitles will look.",
            fontSize = (size.fontSize * SAMPLE_SCALE).sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = .55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
    Text("Subtitle size", fontSize = 18.sp, color = Color.White)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SubtitleSize.entries.forEach { option ->
            OutlinedButton(onClick = { viewModel.setSubtitleSize(option.fontSize) }) {
                Text(if (option == size) "✓ ${option.label}" else option.label)
            }
        }
    }
    StepButtons(viewModel, enabled = chosen != null)
    if (chosen == null) {
        Text("Pick one of the three to go on.", fontSize = 14.sp, color = Color.White.copy(alpha = .6f))
    }
}

/** The subtitle sample is smaller than a full screen, so its text is too */
private const val SAMPLE_SCALE = .9f

@Composable
private fun EasyReadStep(viewModel: WelcomeViewModel) {
    val easyRead by viewModel.easyRead.collectAsState(false)
    val posters by viewModel.posters.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    StepTitle("Is this easy to read?", if (easyRead) "This is the bigger text. You can switch back if you like." else null)
    // A small sample of what Home looks like, with their own posters once they've loaded
    val details = listOf("32 min left", "S1:E4", "1 hr 10 min left")
    val progress = listOf(.7f, .35f, .45f)
    val samples =
        posters
            .take(3)
            .takeIf { it.size == 3 }
            ?.mapIndexed { i, p -> Triple(p.name, details[i], p.imageUrl) }
            ?: listOf(Triple("Heat", details[0], null), Triple("Severance", details[1], null), Triple("Up", details[2], null))
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .fillMaxWidth(.8f)
                .background(Color(0xE616161A), RoundedCornerShape(12.dp))
                .padding(16.dp),
    ) {
        Text("Continue watching", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            samples.forEachIndexed { i, (name, detail, image) ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .weight(1f)
                            .background(Color(0xFF2A2A30), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                ) {
                    if (image != null) {
                        AsyncImage(
                            model = image,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .width(40.dp)
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF1C1C22)),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color.White.copy(alpha = .2f), CircleShape),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(progress[i])
                                    .fillMaxHeight()
                                    .background(BenflixRed, CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
    StepButtons(
        viewModel,
        nextLabel = if (easyRead) "Keep it bigger" else "Looks good",
        focus = focus,
    ) {
        OutlinedButton(onClick = { viewModel.setEasyRead(!easyRead) }) {
            Text(if (easyRead) "Use normal text" else "Make it bigger")
        }
    }
}

/**
 * Families can set a parent PIN (one for the whole TV), which puts the Kids profile to bed at the bedtime
 * they pick. The only step that can be turned down ("No thanks").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ParentPinStep(
    viewModel: WelcomeViewModel,
    bedtimeViewModel: BedtimeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val hasPin by bedtimeViewModel.hasPin.collectAsState()
    var checked by remember { mutableStateOf(false) }
    // Whether this TV had a PIN before this step (then it isn't asked for again)
    var hadPin by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var bedtime by remember { mutableIntStateOf(BenflixBedtime.bedtimeHour) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        hadPin = bedtimeViewModel.loadPin() == true
        BenflixBedtime.load(context)
        bedtime = BenflixBedtime.bedtimeHour
        checked = true
    }
    LaunchedEffect(checked, hasPin) { if (checked) focus.tryRequestFocus() }
    val explanation =
        "Kids get their own profile on every TV, set up for them automatically. With a parent PIN, Kids locks " +
            "at bedtime, and only a grown-up can unlock it with the PIN. You pick the bedtime: 8, 9 or 10 PM."
    when {
        !checked -> {
            StepTitle("Got kids?", explanation)
            Text("Checking for a parent PIN…", fontSize = 18.sp, color = Color.White)
            StepButtons(viewModel, enabled = false)
        }

        hasPin -> {
            StepTitle(
                if (hadPin) "This TV already has a parent PIN" else "Parent PIN saved",
                "When should Kids go to bed? Kids locks at bedtime until 6 AM, and the PIN gives 30 more minutes.",
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BenflixBedtime.BEDTIME_HOURS.forEach { hour ->
                    OutlinedButton(onClick = {
                        BenflixBedtime.setBedtimeHour(context, hour)
                        bedtime = hour
                    }) {
                        val label = BenflixBedtime.label(hour)
                        Text(if (hour == bedtime) "✓ $label" else label)
                    }
                }
            }
            StepButtons(viewModel, focus = focus)
        }

        else -> {
            StepTitle("Got kids? Set a parent PIN", explanation)
            StepButtons(
                viewModel,
                nextLabel = "Set a parent PIN",
                focus = focus,
                onNext = { showDialog = true },
            ) {
                OutlinedButton(onClick = viewModel::next) { Text("No thanks") }
            }
        }
    }
    if (showDialog) {
        ParentPinDialog(
            hasPin = hasPin,
            checkPin = bedtimeViewModel::checkPin,
            setPin = bedtimeViewModel::setPin,
            onDone = { showDialog = false },
        )
    }
}

/** Last question: genres this person never wants to see (they can change it later in Settings > Profile) */
@Composable
private fun HideGenresStep(viewModel: WelcomeViewModel) {
    StepTitle(
        title = "Anything else? Hide genres you never want to see",
        subtitle = "Press a genre to hide it from your Home and library pages. Search still finds it. Hiding nothing is fine too.",
    )
    HiddenGenresPicker(modifier = Modifier.padding(vertical = 8.dp))
    StepButtons(viewModel, nextLabel = "Done")
}

@Composable
private fun FavoritesStep(
    viewModel: WelcomeViewModel,
    isTv: Boolean,
) {
    val posters by viewModel.posters.collectAsState()
    val loaded by viewModel.postersLoaded.collectAsState()
    val picked by viewModel.picked.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    // Start on the search box (TV), so the cursor doesn't jump down into the posters. Press down to reach them.
    // On a phone nothing is focused, so the keyboard doesn't pop up by itself.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (isTv) focus.tryRequestFocus() }
    // 3 picks, or fewer only when the library itself has fewer than 3 things
    val needed = if (loaded) minOf(WelcomeViewModel.MIN_PICKS, posters.size) else WelcomeViewModel.MIN_PICKS
    StepTitle(
        "Pick a few things you love",
        if (picked.isEmpty()) "Pick 3 or more and your Home fills up with things like them." else "${picked.size} picked",
    )
    WelcomeSearchBox(query = query, onQueryChange = viewModel::search, isTv = isTv, focus = focus)
    val showing = if (query.isBlank()) posters else results
    if (!loaded) {
        Text("Loading posters…", color = Color.White)
    } else if (query.isNotBlank() && results.isEmpty()) {
        Text("Nothing found yet. Try another title.", color = Color.White.copy(alpha = .8f))
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (isTv) 120.dp else 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        // Benflix (TV): the poster wall scrolls inside itself, and the page around it only ever asks to
        // show the whole wall. Before, each press made both scroll, so the page jumped up and down.
        // 360dp keeps the wall short enough to always fit on screen as one block.
        modifier =
            Modifier
                .fillMaxWidth()
                .scrollAsOneBlock()
                .height(if (isTv) 360.dp else 460.dp),
    ) {
        items(showing, key = { it.id }) { poster ->
            PosterTile(
                poster = poster,
                selected = poster.id in picked,
                onClick = { viewModel.togglePick(poster) },
            )
        }
    }
    StepButtons(
        viewModel,
        nextLabel = if (picked.isEmpty()) "Next" else "Next (${picked.size} picked)",
        enabled = loaded && picked.size >= needed,
    )
    if (loaded && picked.size < needed) {
        Text(
            "Pick ${needed - picked.size} more to go on.",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = .6f),
        )
    }
}

/** The search box above the poster wall. On a TV it only takes typing once it's pressed (so the D-pad can pass it) */
@Composable
private fun WelcomeSearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    isTv: Boolean,
    focus: FocusRequester,
) {
    var active by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    Text("Or find a title you love", fontSize = 16.sp, color = Color.White.copy(alpha = .8f))
    SearchEditTextBox(
        value = query,
        onValueChange = {
            active = true
            onQueryChange(it)
        },
        onSearchClick = { onQueryChange(query) },
        readOnly = isTv && !active,
        modifier =
            Modifier
                .fillMaxWidth(if (isTv) .5f else 1f)
                .focusRequester(focus)
                .onFocusChanged { if (!it.isFocused) active = false }
                .onPreviewKeyEvent { event ->
                    val activation = event.key == Key.DirectionCenter || event.key == Key.Enter
                    if (event.type == KeyEventType.KeyUp && activation && !active) {
                        active = true
                        keyboard?.show()
                        true
                    } else {
                        false
                    }
                },
    )
}

@Composable
private fun PosterTile(
    poster: WelcomePoster,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border =
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(3.dp, if (selected) BenflixRed else Color.Transparent)),
                focusedBorder = Border(BorderStroke(3.dp, Color.White)),
            ),
        modifier =
            modifier
                .aspectRatio(2f / 3f)
                .semantics { contentDescription = "Poster: ${poster.name}" + if (selected) " (picked)" else "" },
    ) {
        Box(Modifier.fillMaxSize()) {
            Text(
                poster.name,
                fontSize = 13.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(6.dp),
            )
            AsyncImage(
                model = poster.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (selected) {
                Text(
                    "✓",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(BenflixRed, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** "You're all set": a peek at their new Home, then Go to Home (only now is the setup marked done) */
@Composable
private fun FinishStep(
    viewModel: WelcomeViewModel,
    isTv: Boolean,
) {
    val picked by viewModel.picked.collectAsState()
    val posters by viewModel.posters.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    val count = if (isTv) 6 else 4
    val peek = (picked.values.toList() + posters.filter { it.id !in picked }).take(count)
    StepTitle("You're all set", "Here's a peek at your new Home. It fills up more as you watch.")
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier
                .background(Color(0xE616161A), RoundedCornerShape(12.dp))
                .padding(16.dp),
    ) {
        Text(
            picked.values.firstOrNull()?.let { "Because you love ${it.name}" } ?: "Picked for you",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            peek.forEach { poster ->
                Box(
                    Modifier
                        .width(if (isTv) 110.dp else 86.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF2A2A30)),
                ) {
                    Text(
                        poster.name,
                        fontSize = 12.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(4.dp),
                    )
                    AsyncImage(
                        model = poster.imageUrl,
                        contentDescription = poster.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
    StepButtons(viewModel, nextLabel = "Go to Home", focus = focus, onNext = viewModel::finish)
}

private const val AVATARS_PER_ROW = 4
