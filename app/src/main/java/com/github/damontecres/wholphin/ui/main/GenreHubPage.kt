package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.HomeItemFields
import com.github.damontecres.wholphin.ui.cards.GridCard
import com.github.damontecres.wholphin.ui.cards.ItemRowTitle
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import javax.inject.Inject

/**
 * The genres shown as tiles (Search start page) with the colors of their tile.
 * [name] is the genre's name on the Jellyfin server, [title] what Benflix shows.
 */
enum class BenflixGenre(
    val title: String,
    val serverName: String,
    val colors: List<Color>,
) {
    ACTION("Action", "Action", listOf(Color(0xFF8A3A2A), Color(0xFF2A100C))),
    COMEDY("Comedy", "Comedy", listOf(Color(0xFFA07A2A), Color(0xFF2E220C))),
    ANIMATION("Animation", "Animation", listOf(Color(0xFF2A6A8A), Color(0xFF0C1F2A))),
    HORROR("Horror", "Horror", listOf(Color(0xFF2A5A3A), Color(0xFF0B1A10))),
    SCIFI("Sci-fi", "Science Fiction", listOf(Color(0xFF3A3A8A), Color(0xFF10102A))),
    DRAMA("Drama", "Drama", listOf(Color(0xFF6A2A4A), Color(0xFF1F0C16))),
    FAMILY("Family", "Family", listOf(Color(0xFF2A8A6A), Color(0xFF0C2A1F))),
    DOCUMENTARY("Documentary", "Documentary", listOf(Color(0xFF5A5A5A), Color(0xFF1A1A1A))),
    THRILLER("Thriller", "Thriller", listOf(Color(0xFF7A2A2A), Color(0xFF200A0A))),
    ROMANCE("Romance", "Romance", listOf(Color(0xFFA03A5A), Color(0xFF2A0F18))),
    FANTASY("Fantasy", "Fantasy", listOf(Color(0xFF5A3A8A), Color(0xFF180F2A))),
    CRIME("Crime", "Crime", listOf(Color(0xFF4A4A2A), Color(0xFF16160C))),
}

/** One row on a genre hub */
data class HubRow(
    val title: String,
    val items: List<BaseItem>,
)

@HiltViewModel
class GenreHubViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        /** null while loading */
        private val _rows = MutableStateFlow<List<HubRow>?>(null)
        val rows: StateFlow<List<HubRow>?> = _rows

        private var loaded: String? = null

        fun load(genre: String) {
            if (loaded == genre) return
            loaded = genre
            _rows.value = null
            viewModelScope.launch(WholphinDispatchers.IO) {
                val userId = serverRepository.currentUser?.id

                suspend fun fetch(
                    types: List<BaseItemKind>,
                    sortBy: List<ItemSortBy>,
                    sortOrder: SortOrder = SortOrder.DESCENDING,
                    minRating: Double? = null,
                    unwatched: Boolean = false,
                    limit: Int = HUB_ROW_MAX,
                ): List<BaseItem> =
                    try {
                        api.itemsApi
                            .getItems(
                                GetItemsRequest(
                                    userId = userId,
                                    includeItemTypes = types,
                                    recursive = true,
                                    genres = listOf(genre),
                                    sortBy = sortBy,
                                    sortOrder = listOf(sortOrder),
                                    minCommunityRating = minRating,
                                    isPlayed = if (unwatched) false else null,
                                    limit = limit,
                                    fields = HomeItemFields,
                                ),
                            ).content.items
                            .map { BaseItem(it) }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Hub row failed for %s", genre)
                        emptyList()
                    }
                val both = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
                val rows =
                    coroutineScope {
                        listOf(
                            async { HubRow("Top rated", fetch(both, listOf(ItemSortBy.COMMUNITY_RATING))) },
                            async {
                                HubRow(
                                    "Hidden gems you haven't seen",
                                    fetch(both, listOf(ItemSortBy.RANDOM), minRating = 7.0, unwatched = true),
                                )
                            },
                            async { HubRow("New on Benflix", fetch(both, listOf(ItemSortBy.DATE_CREATED))) },
                            async {
                                HubRow(
                                    "Movies under 2 hours",
                                    fetch(listOf(BaseItemKind.MOVIE), listOf(ItemSortBy.RANDOM), limit = HUB_ROW_MAX * 3)
                                        .filter { (it.data.runTimeTicks ?: 0L) in 1 until TWO_HOURS_TICKS }
                                        .take(HUB_ROW_MAX),
                                )
                            },
                            async { HubRow("Shows", fetch(listOf(BaseItemKind.SERIES), listOf(ItemSortBy.RANDOM))) },
                        ).awaitAll()
                    }
                _rows.value = rows.filter { it.items.size >= HUB_ROW_MIN }
            }
        }
    }

/**
 * A page for one genre: its best, its hidden gems, what's new, and shorter picks
 */
@Composable
fun GenreHubPage(
    genre: String,
    modifier: Modifier = Modifier,
    viewModel: GenreHubViewModel = hiltViewModel(),
) {
    LaunchedEffect(genre) { viewModel.load(genre) }
    val rows by viewModel.rows.collectAsState()
    val known = remember(genre) { BenflixGenre.entries.firstOrNull { it.serverName == genre } }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = (known?.title ?: genre).uppercase(),
            fontFamily = BenflixTitleFont,
            fontSize = 56.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 12.dp, start = 8.dp),
        )
        when (val list = rows) {
            null -> {
                LoadingPage(Modifier.fillMaxSize())
            }

            else -> {
                if (list.isEmpty()) {
                    Text(
                        text = "Nothing in $genre yet.",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(top = 32.dp, start = 8.dp),
                    )
                } else {
                    HubRows(
                        rows = list,
                        onClickItem = { viewModel.navigationManager.navigateTo(it.destination()) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/**
 * Rows of posters, used by the genre hubs and My Benflix. The first poster takes focus.
 */
@Composable
fun HubRows(
    rows: List<HubRow>,
    onClickItem: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(rows) {
        // Wait a frame so the first poster is on screen before it takes focus
        kotlinx.coroutines.delay(80)
        firstFocus.tryRequestFocus()
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 48.dp),
        modifier = modifier,
    ) {
        header?.let { item { it() } }
        rows.forEachIndexed { rowIndex, row ->
            item(key = row.title) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ItemRowTitle(row.title)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        items(row.items, key = { it.id }) { item ->
                            GridCard(
                                item = item,
                                onClick = { onClickItem(item) },
                                onLongClick = {},
                                showTitle = false,
                                modifier =
                                    Modifier
                                        .width(HUB_CARD_WIDTH)
                                        .then(
                                            if (rowIndex == 0 && item == row.items.first()) {
                                                Modifier.focusRequester(firstFocus)
                                            } else {
                                                Modifier
                                            },
                                        ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A colorful tile for one genre. Opens that genre's hub.
 */
@Composable
fun GenreTile(
    genre: BenflixGenre,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
            ),
        modifier = modifier.size(180.dp, 64.dp),
    ) {
        Box(
            contentAlignment = Alignment.BottomStart,
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(genre.colors)),
        ) {
            Text(
                text = genre.title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

/** Opens [genre]'s hub */
fun NavigationManager.openGenreHub(genre: BenflixGenre) = navigateTo(Destination.GenreHub(genre.serverName))

private val HUB_CARD_WIDTH = 120.dp
private const val HUB_ROW_MAX = 20
private const val HUB_ROW_MIN = 4
private const val TWO_HOURS_TICKS = 2L * 60 * 60 * 10_000_000
