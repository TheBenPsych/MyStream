package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.components.SearchEditTextBox
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixRed
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.scrollAsOneBlock
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
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

/** A poster on the "Pick a few things you love" wall */
data class LovePoster(
    val id: UUID,
    val name: String,
    val imageUrl: String?,
)

/**
 * Settings > You > Things you love: pick a few favourites (a 👍 like each) and your rows fill up with things
 * like them. It used to be a step of the first-time setup, which is gone (Ben 2026-11-18).
 */
@HiltViewModel
class ThingsYouLoveViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val imageUrlService: ImageUrlService,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val userId: UUID? get() = serverRepository.currentUser?.id

        private val _posters = MutableStateFlow<List<LovePoster>>(emptyList())
        val posters: StateFlow<List<LovePoster>> = _posters

        /** True once the poster wall has loaded (or failed to) */
        private val _postersLoaded = MutableStateFlow(false)
        val postersLoaded: StateFlow<Boolean> = _postersLoaded

        /** Picked favourites, in the order they were picked */
        private val _picked = MutableStateFlow<Map<UUID, LovePoster>>(emptyMap())
        val picked: StateFlow<Map<UUID, LovePoster>> = _picked

        private val _searchQuery = MutableStateFlow("")
        val searchQuery: StateFlow<String> = _searchQuery

        private val _searchResults = MutableStateFlow<List<LovePoster>>(emptyList())
        val searchResults: StateFlow<List<LovePoster>> = _searchResults
        private var searchJob: Job? = null

        init {
            viewModelScope.launch {
                try {
                    _posters.value = withContext(WholphinDispatchers.IO) { loadPosters() }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Things you love: couldn't load posters")
                }
                _postersLoaded.value = true
            }
        }

        fun togglePick(poster: LovePoster) {
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
                        Timber.w(ex, "Things you love: search failed")
                    }
                }
        }

        /** Done: a 👍 like for each pick, then back to Settings */
        fun done() {
            val picks = _picked.value.keys
            if (picks.isNotEmpty()) {
                viewModelScope.launch(WholphinDispatchers.IO) {
                    picks.forEach {
                        try {
                            favoriteWatchManager.setLikes(it, true)
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            Timber.w(ex, "Things you love: couldn't save a like")
                        }
                    }
                }
            }
            navigationManager.goBack()
        }

        private fun BaseItemDto.toPoster() =
            LovePoster(
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
                Timber.w(ex, "Things you love: couldn't load some posters")
                emptyList()
            }

        /** A mix of movies, shows and anime (from Anime libraries, or the Anime genre), taking turns */
        private suspend fun loadPosters(): List<LovePoster> {
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
                    Timber.w(ex, "Things you love: couldn't list libraries")
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

        companion object {
            private const val POOL_EACH = 30
            private const val POSTERS_SHOWN = 24
            private const val SEARCH_LIMIT = 18
            private const val SEARCH_DELAY_MS = 350L
        }
    }

@Composable
fun ThingsYouLovePage(
    modifier: Modifier = Modifier,
    viewModel: ThingsYouLoveViewModel = hiltViewModel(),
) {
    val isTv = rememberIsTv()
    val posters by viewModel.posters.collectAsState()
    val loaded by viewModel.postersLoaded.collectAsState()
    val picked by viewModel.picked.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    // Start on the search box (TV), so the cursor doesn't jump down into the posters. Press down to reach them.
    // On a phone nothing is focused, so the keyboard doesn't pop up by itself.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (isTv) focus.tryRequestFocus() }
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier =
            modifier
                .fillMaxSize()
                .background(BenflixColors.Tray)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isTv) 48.dp else 16.dp, vertical = if (isTv) 32.dp else 16.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth(),
        ) {
            Text(
                "Pick a few things you love",
                fontSize = BenflixType.pageTitle,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                if (picked.isEmpty()) "About 3 is plenty. Your rows fill up with things like them." else "${picked.size} picked",
                fontSize = BenflixType.lead,
                color = BenflixColors.Soft,
                textAlign = TextAlign.Center,
            )
            LoveSearchBox(query = query, onQueryChange = viewModel::search, isTv = isTv, focus = focus)
            val showing = if (query.isBlank()) posters else results
            if (!loaded) {
                Text("Loading posters…", color = Color.White)
            } else if (query.isNotBlank() && results.isEmpty()) {
                Text("Nothing found yet. Try another title.", color = BenflixColors.Soft)
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isTv) 120.dp else 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                // TV: the poster wall scrolls inside itself, and the page around it only ever asks to show the
                // whole wall, so the page doesn't jump up and down on each press
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .scrollAsOneBlock()
                        .height(if (isTv) 360.dp else 460.dp),
            ) {
                items(showing, key = { it.id }) { poster ->
                    LovePosterTile(
                        poster = poster,
                        selected = poster.id in picked,
                        onClick = { viewModel.togglePick(poster) },
                    )
                }
            }
            Button(
                onClick = viewModel::done,
                scale = ButtonDefaults.scale(focusedScale = 1f),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun LoveSearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    isTv: Boolean,
    focus: FocusRequester,
) {
    var active by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    Text("Or find a title you love", fontSize = BenflixType.body, color = BenflixColors.Soft)
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
private fun LovePosterTile(
    poster: LovePoster,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border =
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(3.dp, if (selected) BenflixRed else Color.Transparent)),
                focusedBorder = Border(BorderStroke(3.dp, BenflixColors.Focus)),
            ),
        modifier =
            modifier
                .aspectRatio(2f / 3f)
                .semantics { contentDescription = "Poster: ${poster.name}" + if (selected) " (picked)" else "" },
    ) {
        Box(Modifier.fillMaxSize()) {
            Text(
                poster.name,
                fontSize = BenflixType.small,
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
                    fontSize = BenflixType.rowName,
                    fontWeight = FontWeight.Bold,
                    color = com.github.damontecres.wholphin.ui.theme.OnBenflixRed,
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
