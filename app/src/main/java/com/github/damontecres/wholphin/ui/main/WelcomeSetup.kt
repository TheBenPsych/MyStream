package com.github.damontecres.wholphin.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.github.damontecres.wholphin.services.BenflixSettingsSync
import com.github.damontecres.wholphin.services.DisplayPreferencesService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.setup.AvatarChoices
import com.github.damontecres.wholphin.ui.setup.BenflixAvatars
import com.github.damontecres.wholphin.ui.theme.BenflixRed
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.ui.theme.toColor
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import javax.inject.Inject

/** The steps of the welcome setup, in order */
enum class WelcomeStep { HELLO, PICTURE, SUBTITLES, EASY_READ, FAVORITES }

/** A poster on the "Pick a few things you love" wall */
data class WelcomePoster(
    val id: UUID,
    val name: String,
    val imageUrl: String?,
)

/**
 * Benflix welcome setup: the first time a person uses Benflix, a few quick questions
 * (picture and colour, subtitles, text size, a few favourites). It happens once per person, not
 * per device: it's remembered on the server, so a new TV just skips it.
 */
@HiltViewModel
class WelcomeViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val serverDao: JellyfinServerDao,
        private val preferences: DataStore<AppPreferences>,
        private val displayPreferencesService: DisplayPreferencesService,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val imageUrlService: ImageUrlService,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _step = MutableStateFlow(WelcomeStep.HELLO)
        val step: StateFlow<WelcomeStep> = _step

        private val _posters = MutableStateFlow<List<WelcomePoster>>(emptyList())
        val posters: StateFlow<List<WelcomePoster>> = _posters

        private val _picked = MutableStateFlow<Set<UUID>>(emptySet())
        val picked: StateFlow<Set<UUID>> = _picked

        private val _subtitles = MutableStateFlow<SubtitleModePreference?>(null)
        val subtitles: StateFlow<SubtitleModePreference?> = _subtitles

        val accent = preferences.data.map { it.benflixPreferences.accent }
        val easyRead = preferences.data.map { it.benflixPreferences.easyRead }

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
            }
        }

        fun next() {
            val steps = WelcomeStep.entries
            val index = steps.indexOf(_step.value)
            if (index < steps.lastIndex) {
                _step.value = steps[index + 1]
            } else {
                finish()
            }
        }

        fun back(): Boolean {
            val index = WelcomeStep.entries.indexOf(_step.value)
            if (index <= 0) return false
            _step.value = WelcomeStep.entries[index - 1]
            return true
        }

        fun setAccent(accent: BenflixAccent) {
            viewModelScope.launch { preferences.updateData { it.updateBenflixPreferences { this.accent = accent } } }
        }

        fun setEasyRead(on: Boolean) {
            viewModelScope.launch { preferences.updateData { it.updateBenflixPreferences { easyRead = on } } }
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
            _picked.value = if (poster.id in now) now - poster.id else now + poster.id
        }

        /** Done (or Skip): remember it on the server, save the 👍 likes and go to Home */
        fun finish() {
            val id = userId
            val likes = _picked.value
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
                likes.forEach { itemId ->
                    try {
                        favoriteWatchManager.setLikes(itemId, true)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Welcome: couldn't save a like")
                    }
                }
                withContext(WholphinDispatchers.Main) { navigationManager.reloadHome() }
            }
        }

        private suspend fun loadPosters(): List<WelcomePoster> =
            api.itemsApi
                .getItems(
                    GetItemsRequest(
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        recursive = true,
                        imageTypes = listOf(ImageType.PRIMARY),
                        sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        limit = POSTER_POOL,
                    ),
                ).content.items
                .shuffled()
                .take(POSTERS_SHOWN)
                .map {
                    WelcomePoster(
                        id = it.id,
                        name = it.name.orEmpty(),
                        imageUrl = imageUrlService.getItemImageUrl(itemId = it.id, imageType = ImageType.PRIMARY, maxWidth = 300),
                    )
                }

        companion object {
            private const val POSTER_POOL = 80
            private const val POSTERS_SHOWN = 24
            const val KEY = "benflix.welcomed"

            suspend fun markWelcomed(
                displayPreferencesService: DisplayPreferencesService,
                userId: UUID,
            ) = displayPreferencesService.updateDisplayPreferences(userId) { put(KEY, "1") }
        }
    }

/**
 * Decides once per sign-in whether to open the welcome setup. People who have already watched
 * something on Benflix (everyone from before this update) skip it.
 */
@HiltViewModel
class WelcomeGateViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val displayPreferencesService: DisplayPreferencesService,
        private val settingsSync: BenflixSettingsSync,
        private val navigationManager: NavigationManager,
    ) : ViewModel() {
        fun check() {
            if (KidsMode.isActive) return
            val userId = serverRepository.currentUser?.id ?: return
            if (userId in checked) return
            checked += userId
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    // Wait for this person's saved settings to load first
                    withTimeoutOrNull(SYNC_WAIT_MS) { settingsSync.ready.first { it == userId } }
                    val prefs = displayPreferencesService.getDisplayPreferences(userId).customPrefs
                    if (prefs[WelcomeViewModel.KEY] != null) return@launch
                    val hasWatched =
                        api.itemsApi
                            .getItems(
                                GetItemsRequest(userId = userId, recursive = true, isPlayed = true, limit = 1),
                            ).content.items
                            .isNotEmpty()
                    if (hasWatched) {
                        WelcomeViewModel.markWelcomed(displayPreferencesService, userId)
                    } else {
                        withContext(WholphinDispatchers.Main) { navigationManager.navigateTo(Destination.Welcome) }
                    }
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
    val isTv = rememberIsTv()
    BackHandler { if (!viewModel.back()) viewModel.finish() }
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier =
            modifier
                .fillMaxSize()
                .background(Color(0xFF0B0B0D))
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
                Text(
                    text = "Step ${step.ordinal + 1} of ${WelcomeStep.entries.size}",
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = .7f),
                )
            }
            when (step) {
                WelcomeStep.HELLO -> HelloStep(viewModel)
                WelcomeStep.PICTURE -> PictureStep(viewModel)
                WelcomeStep.SUBTITLES -> SubtitlesStep(viewModel)
                WelcomeStep.EASY_READ -> EasyReadStep(viewModel)
                WelcomeStep.FAVORITES -> FavoritesStep(viewModel, isTv)
            }
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
        Text(subtitle, fontSize = 18.sp, color = Color.White.copy(alpha = .8f), textAlign = TextAlign.Center)
    }
}

/** Continue and Skip, at the bottom of each step */
@Composable
private fun StepButtons(
    viewModel: WelcomeViewModel,
    nextLabel: String = "Next",
    focus: FocusRequester? = null,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
        Button(onClick = viewModel::next, modifier = if (focus != null) Modifier.focusRequester(focus) else Modifier) {
            Text(nextLabel)
        }
        OutlinedButton(onClick = viewModel::finish) {
            Text("Skip")
        }
    }
}

@Composable
private fun HelloStep(viewModel: WelcomeViewModel) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    Box(Modifier.height(24.dp))
    StepTitle(
        title = "Hi ${viewModel.userName.ifBlank { "there" }}! Let's make Benflix yours.",
        subtitle = "${WelcomeStep.entries.size - 1} quick questions. You can change any of it later in Settings.",
    )
    StepButtons(viewModel, nextLabel = "Let's go", focus = focus)
}

@Composable
private fun PictureStep(viewModel: WelcomeViewModel) {
    val userId = viewModel.userId ?: return
    AvatarChoices.init(LocalContext.current)
    val chosen = AvatarChoices.get(userId)
    val accent by viewModel.accent.collectAsState(BenflixAccent.ACCENT_RED)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    StepTitle("Pick your picture and colour")
    Text("Your picture", fontSize = 18.sp, color = Color.White)
    BenflixAvatars.chunked(AVATARS_PER_ROW).forEachIndexed { rowIndex, row ->
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            row.forEachIndexed { col, res ->
                val index = rowIndex * AVATARS_PER_ROW + col
                Surface(
                    onClick = { AvatarChoices.set(userId, index) },
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    border =
                        ClickableSurfaceDefaults.border(
                            border = Border(BorderStroke(3.dp, if (index == chosen) accent.toColor() else Color.Transparent)),
                            focusedBorder = Border(BorderStroke(3.dp, Color.White)),
                        ),
                    modifier =
                        Modifier
                            .size(64.dp)
                            .then(if (index == 0) Modifier.focusRequester(focus) else Modifier),
                ) {
                    Image(
                        painter = painterResource(res),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                }
            }
        }
    }
    Text("Your colour", fontSize = 18.sp, color = Color.White, modifier = Modifier.padding(top = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        BenflixAccent.entries.filter { it != BenflixAccent.UNRECOGNIZED }.forEach { color ->
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
    StepButtons(viewModel)
}

@Composable
private fun SubtitlesStep(viewModel: WelcomeViewModel) {
    val chosen by viewModel.subtitles.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    StepTitle("Do you like subtitles?", "This is for movies and shows in your own language. Anime always gets English subtitles.")
    val options =
        listOf(
            "Off" to SubtitleModePreference.NONE,
            "Always on" to SubtitleModePreference.ALWAYS,
            "Only for other languages" to SubtitleModePreference.SMART,
        )
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        options.forEachIndexed { i, (label, mode) ->
            val selected = chosen == mode
            Button(
                onClick = {
                    viewModel.setSubtitles(mode)
                    viewModel.next()
                },
                modifier = if (i == 0) Modifier.focusRequester(focus) else Modifier,
            ) {
                Text(if (selected) "✓ $label" else label)
            }
        }
    }
    // A sample of how subtitles look
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier =
            Modifier
                .fillMaxWidth(.7f)
                .aspectRatio(16f / 7f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF26323C)),
    ) {
        Text(
            text = "This is how your subtitles will look.",
            fontSize = 22.sp,
            color = Color.White,
            modifier =
                Modifier
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = .55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
    OutlinedButton(onClick = viewModel::finish) { Text("Skip") }
}

@Composable
private fun EasyReadStep(viewModel: WelcomeViewModel) {
    val easyRead by viewModel.easyRead.collectAsState(false)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    StepTitle("Is this easy to read?", if (easyRead) "This is the bigger text. You can switch back if you like." else null)
    // A small sample of what Home looks like
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .fillMaxWidth(.8f)
                .background(Color(0xFF16161A), RoundedCornerShape(12.dp))
                .padding(16.dp),
    ) {
        Text("Continue watching", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Heat" to "32 min left", "Severance" to "S2:E4", "Up" to "1 hr 10 min left").forEach { (name, detail) ->
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .background(Color(0xFF2A2A30), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                ) {
                    Text(name, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(
            onClick = {
                viewModel.setEasyRead(false)
                viewModel.next()
            },
            modifier = Modifier.focusRequester(focus),
        ) { Text(if (easyRead) "Use normal text" else "Looks good") }
        Button(
            onClick = {
                if (easyRead) viewModel.next() else viewModel.setEasyRead(true)
            },
        ) { Text(if (easyRead) "Keep it bigger" else "Make it bigger") }
    }
    OutlinedButton(onClick = viewModel::finish) { Text("Skip") }
}

@Composable
private fun FavoritesStep(
    viewModel: WelcomeViewModel,
    isTv: Boolean,
) {
    val posters by viewModel.posters.collectAsState()
    val picked by viewModel.picked.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(posters.isNotEmpty()) { if (posters.isNotEmpty()) focus.tryRequestFocus() }
    StepTitle(
        "Pick a few things you love",
        if (picked.isEmpty()) "Pick 3 or more and your Home fills up with things like them." else "${picked.size} picked",
    )
    if (posters.isEmpty()) {
        Text("Loading posters…", color = Color.White)
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (isTv) 120.dp else 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().height(if (isTv) 420.dp else 480.dp),
    ) {
        items(posters, key = { it.id }) { poster ->
            val selected = poster.id in picked
            Surface(
                onClick = { viewModel.togglePick(poster) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                border =
                    ClickableSurfaceDefaults.border(
                        border = Border(BorderStroke(3.dp, if (selected) BenflixRed else Color.Transparent)),
                        focusedBorder = Border(BorderStroke(3.dp, Color.White)),
                    ),
                modifier =
                    Modifier
                        .aspectRatio(2f / 3f)
                        .then(if (poster == posters.first()) Modifier.focusRequester(focus) else Modifier),
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
                        contentDescription = poster.name,
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
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(onClick = viewModel::finish) { Text(if (picked.isEmpty()) "Done" else "Done (${picked.size})") }
    }
}

private const val AVATARS_PER_ROW = 6
