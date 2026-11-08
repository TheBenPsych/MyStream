package com.github.damontecres.wholphin.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.ItemRowFields
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.cards.SeasonCard
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.LoadingRow
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.discover.DiscoverRow
import com.github.damontecres.wholphin.ui.discover.DiscoverRowData
import com.github.damontecres.wholphin.ui.endDay
import com.github.damontecres.wholphin.ui.formatDate
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.premiereDay
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.ResStringProvider
import com.github.damontecres.wholphin.ui.util.scrollAsOneBlock
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.RowLoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.LocalDate
import java.util.UUID

@HiltViewModel(assistedFactory = PersonViewModel.Factory::class)
class PersonViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        val navigationManager: NavigationManager,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val seerrService: SeerrService,
        @Assisted val itemId: UUID,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(itemId: UUID): PersonViewModel
        }

        private val _state = MutableStateFlow(PersonState())
        val state: StateFlow<PersonState> = _state

        private suspend fun updatePerson(): BaseItem {
            val person =
                api.userLibraryApi
                    .getItem(itemId)
                    .content
                    .let { BaseItem(it) }
            _state.update {
                it.copy(
                    person = DataLoadingState.Success(person),
                )
            }
            return person
        }

        init {
            viewModelScope.launchIO {
                try {
                    val person = updatePerson()
                    val dto = person.data
                    if ((dto.movieCount ?: 0) > 0) {
                        fetchRow(person.id, BaseItemKind.MOVIE) { state, row ->
                            state.copy(movies = row)
                        }
                    } else {
                        _state.update { it.copy(movies = RowLoadingState.Success(emptyList())) }
                    }
                    if ((dto.seriesCount ?: 0) > 0) {
                        fetchRow(person.id, BaseItemKind.SERIES) { state, row ->
                            state.copy(series = row)
                        }
                    } else {
                        _state.update { it.copy(series = RowLoadingState.Success(emptyList())) }
                    }
                    if ((dto.episodeCount ?: 0) > 0) {
                        fetchRow(person.id, BaseItemKind.EPISODE) { state, row ->
                            state.copy(episodes = row)
                        }
                    } else {
                        _state.update { it.copy(episodes = RowLoadingState.Success(emptyList())) }
                    }
                    viewModelScope.launchIO {
                        val results = seerrService.similar(person).orEmpty()
                        _state.update {
                            it.copy(
                                discovered = results,
                                discoverPerson = seerrService.discoverPerson(person),
                            )
                        }
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching person %s", itemId)
                    _state.update { it.copy(person = DataLoadingState.Error(ex)) }
                }
            }
        }

        private fun fetchRow(
            itemId: UUID,
            type: BaseItemKind,
            update: (PersonState, RowLoadingState) -> PersonState,
        ) {
            viewModelScope.launchIO {
                _state.update {
                    update.invoke(it, RowLoadingState.Loading)
                }
                try {
                    val request =
                        GetItemsRequest(
                            personIds = listOf(itemId),
                            includeItemTypes = listOf(type),
                            fields = ItemRowFields,
                            recursive = true,
                            // Benflix: what you've already seen them in comes first
                            sortBy =
                                listOf(
                                    ItemSortBy.IS_PLAYED,
                                    ItemSortBy.PREMIERE_DATE,
                                    ItemSortBy.PRODUCTION_YEAR,
                                    ItemSortBy.SORT_NAME,
                                ),
                            sortOrder =
                                listOf(
                                    SortOrder.DESCENDING,
                                    SortOrder.DESCENDING,
                                    SortOrder.DESCENDING,
                                    SortOrder.ASCENDING,
                                ),
                        )
                    val pager =
                        ApiRequestPager(
                            api,
                            request,
                            GetItemsRequestHandler,
                            viewModelScope,
                            // Benflix: enough for a whole door column and its "See all" in one go
                            pageSize = 60,
                            useSeriesForPrimary = false,
                        ).init()
                    _state.update {
                        update.invoke(it, RowLoadingState.Success(pager))
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching $type for $itemId")
                    _state.update {
                        update.invoke(it, RowLoadingState.Error(ex))
                    }
                }
            }
        }

        fun setFavorite(favorite: Boolean) {
            viewModelScope.launchIO {
                favoriteWatchManager.setFavorite(itemId, favorite)
                updatePerson()
            }
        }
    }

data class PersonState(
    val person: DataLoadingState<BaseItem> = DataLoadingState.Pending,
    val movies: RowLoadingState = RowLoadingState.Pending,
    val series: RowLoadingState = RowLoadingState.Pending,
    val episodes: RowLoadingState = RowLoadingState.Pending,
    val discovered: List<DiscoverItem> = emptyList(),
    val discoverPerson: DiscoverItem? = null,
)

@Composable
fun PersonPage(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: PersonViewModel =
        hiltViewModel<PersonViewModel, PersonViewModel.Factory>(
            creationCallback = { it.create(destination.itemId) },
        ),
) {
    val state by viewModel.state.collectAsState()
    when (val st = state.person) {
        is DataLoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        is DataLoadingState.Success<BaseItem> -> {
            val person = st.data
            var showOverviewDialog by remember { mutableStateOf(false) }
            val name = remember(person) { person.name ?: person.id.toString() }
            val imageUrlService = LocalImageUrlService.current
            val imageUrl =
                remember {
                    imageUrlService.getItemImageUrl(
                        itemId = person.id,
                        imageType = ImageType.PRIMARY,
                        tag = person.data.imageTags?.get(ImageType.PRIMARY),
                    )
                }
            // A long filmography is hard to navigate as a row, so it is cut at the same
            // limit as the home page rows and the rest is left to the full grid
            val maxItems = preferences.appPreferences.homePagePreferences.maxItemsPerRow
            val discovered =
                remember(state.discovered, maxItems) { state.discovered.take(maxItems) }

            PersonPageContent(
                preferences = preferences,
                name = name,
                overview = person.data.overview,
                imageUrl = imageUrl,
                birthdate = person.data.premiereDay,
                deathdate = person.data.endDay,
                birthPlace = person.data.productionLocations?.firstOrNull(),
                favorite = person.favorite,
                movies = state.movies,
                series = state.series,
                episodes = state.episodes,
                onClickItem = { index, item ->
                    viewModel.navigationManager.navigateTo(item.destination())
                },
                overviewOnClick = { showOverviewDialog = true },
                favoriteOnClick = {
                    viewModel.setFavorite(!person.favorite)
                },
                discovered = discovered,
                onClickDiscover = { index, item ->
                    viewModel.navigationManager.navigateTo(item.destination)
                },
                onClickViewMoreDiscover = {
                    state.discoverPerson?.let {
                        viewModel.navigationManager.navigateTo(
                            Destination.DiscoveredItem(it, maxItems),
                        )
                    }
                },
                enableViewMoreDiscover = state.discovered.size > discovered.size,
                modifier = modifier,
            )
            AnimatedVisibility(showOverviewDialog) {
                ItemDetailsDialog(
                    info =
                        ItemDetailsDialogInfo(
                            title = name,
                            overview = person.data.overview,
                            genres = listOf(),
                            files = listOf(),
                        ),
                    showFilePath = false,
                    onDismissRequest = { showOverviewDialog = false },
                )
            }
        }
    }
}

private const val HEADER_ROW = 0
private const val MOVIE_ROW = 1
private const val DISCOVER_ROW = 4

@Composable
fun PersonPageContent(
    preferences: UserPreferences,
    name: String,
    overview: String?,
    imageUrl: String?,
    birthdate: LocalDate?,
    deathdate: LocalDate?,
    birthPlace: String?,
    favorite: Boolean,
    movies: RowLoadingState,
    series: RowLoadingState,
    episodes: RowLoadingState,
    discovered: List<DiscoverItem>,
    onClickItem: (Int, BaseItem) -> Unit,
    overviewOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    onClickDiscover: (Int, DiscoverItem) -> Unit,
    onClickViewMoreDiscover: () -> Unit,
    enableViewMoreDiscover: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val headerFocusRequester = remember { FocusRequester() }
    var position by rememberPosition()
    LaunchedEffect(Unit) {
        if (position.row > HEADER_ROW) {
            focusRequester.tryRequestFocus()
        } else {
            headerFocusRequester.tryRequestFocus()
        }
    }
    var focusedOnHeader by remember { mutableStateOf(true) }
    val isTv = rememberIsTv()
    // The poster you last opened, so Back lands on it again
    var focusKey by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = !focusedOnHeader,
        modifier = modifier,
    ) {
        item {
            PersonHeader(
                name = name,
                overview = overview,
                imageUrl = imageUrl,
                birthdate = birthdate,
                birthPlace = birthPlace,
                deathdate = deathdate,
                favorite = favorite,
                overviewOnClick = overviewOnClick,
                favoriteOnClick = favoriteOnClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .padding(top = 16.dp, bottom = 40.dp)
                        .focusRequester(headerFocusRequester)
                        .onFocusChanged {
                            focusedOnHeader = it.hasFocus
                        },
            )
        }
        // Approved actor page: their work split by door (Movies, Shows, Not on Benflix)
        val doors =
            listOfNotNull(
                (movies as? RowLoadingState.Success)
                    ?.items
                    ?.filterNotNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { PersonDoor.Library("movies", "Movies", BenflixColors.Movies, it) },
                (series as? RowLoadingState.Success)
                    ?.items
                    ?.filterNotNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { PersonDoor.Library("shows", "Shows", BenflixColors.Shows, it) },
                discovered.takeIf { it.isNotEmpty() }?.let { PersonDoor.Ask(it) },
            )
        val onItem = { key: String, item: BaseItem ->
            focusKey = key
            position = RowColumn(MOVIE_ROW, 0)
            onClickItem(0, item)
        }
        val onAsk = { key: String, item: DiscoverItem ->
            focusKey = key
            position = RowColumn(DISCOVER_ROW, 0)
            onClickDiscover(0, item)
        }
        if (isTv) {
            item(key = "doors") {
                PersonDoorColumns(
                    doors = doors,
                    focusKey = focusKey,
                    focusRequester = focusRequester,
                    onClickItem = onItem,
                    onClickAsk = onAsk,
                    modifier = Modifier.padding(bottom = 32.dp),
                )
            }
        } else {
            doors.forEach { door ->
                item(key = "door-${door.key}") {
                    PersonDoorRow(
                        door = door,
                        focusKey = focusKey,
                        focusRequester = focusRequester,
                        onClickItem = onItem,
                        onClickAsk = onAsk,
                        posterWidth = 108.dp,
                    )
                }
            }
        }
        if (enableViewMoreDiscover) {
            item(key = "ask-more") {
                androidx.tv.material3.Surface(
                    onClick = onClickViewMoreDiscover,
                    shape =
                        androidx.tv.material3.ClickableSurfaceDefaults
                            .shape(RoundedCornerShape(50)),
                    colors =
                        androidx.tv.material3.ClickableSurfaceDefaults.colors(
                            containerColor = BenflixColors.Raised,
                            contentColor = BenflixColors.Soft,
                            focusedContainerColor = BenflixColors.Text,
                            focusedContentColor = BenflixColors.Tray,
                        ),
                    scale =
                        androidx.tv.material3.ClickableSurfaceDefaults
                            .scale(focusedScale = 1f),
                    modifier = Modifier.padding(horizontal = if (isTv) BenflixSpace.tvEdge else BenflixSpace.phoneEdge),
                ) {
                    Text(
                        text = "More to ask for  →",
                        fontSize = BenflixType.small,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

fun rowTitle(
    prefix: String,
    state: RowLoadingState,
): String =
    if (state is RowLoadingState.Success) {
        "$prefix (${state.items.size})"
    } else {
        prefix
    }

@Composable
fun PersonHeader(
    name: String,
    overview: String?,
    imageUrl: String?,
    birthdate: LocalDate?,
    deathdate: LocalDate?,
    birthPlace: String?,
    favorite: Boolean,
    overviewOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        modifier = modifier.scrollAsOneBlock().bringIntoViewRequester(bringIntoViewRequester),
    ) {
        // A picture-sized card with their initials underneath, so someone with no photo on the
        // server still has something there (it used to be a big empty gap)
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .weight(1f)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(10))
                    .background(com.github.damontecres.wholphin.ui.theme.BenflixColors.PanelHigh),
        ) {
            Text(
                text = remember(name) { initialsOf(name) },
                color = com.github.damontecres.wholphin.ui.theme.BenflixColors.Muted,
                fontSize = BenflixType.hero,
                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
            )
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
//                    .fillMaxWidth(.7f)
                    .weight(3f)
                    // Approved redesign (person page): words straight on the dark page, no raised card
                    .padding(top = 16.dp, end = 32.dp),
        ) {
            Text(
                text = name,
                color = com.github.damontecres.wholphin.ui.theme.BenflixColors.Text,
                fontSize = BenflixType.hero,
                lineHeight = BenflixType.hero * 1.1f,
                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            birthdate?.let {
                val age = if (deathdate == null) birthdate.until(LocalDate.now())?.years else null
                val text =
                    if (age != null) {
                        stringResource(R.string.born) + ": ${formatDate(it)} (${
                            pluralStringResource(
                                R.plurals.years_old,
                                age,
                                age,
                            )
                        })"
                    } else {
                        stringResource(R.string.born) + ": ${formatDate(it)}"
                    }
                Text(
                    text = text,
                    color = com.github.damontecres.wholphin.ui.theme.BenflixColors.Muted,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (birthPlace.isNotNullOrBlank()) {
                Text(
                    text = stringResource(R.string.birthplace) + ": $birthPlace",
                    color = com.github.damontecres.wholphin.ui.theme.BenflixColors.Muted,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            deathdate?.let {
                val age = birthdate?.until(it)?.years
                val text =
                    if (age != null) {
                        stringResource(R.string.died) + ": ${formatDate(it)} (${
                            pluralStringResource(
                                R.plurals.years_old,
                                age,
                                age,
                            )
                        })"
                    } else {
                        stringResource(R.string.died) + ": ${formatDate(it)}"
                    }
                Text(
                    text = text,
                    color = com.github.damontecres.wholphin.ui.theme.BenflixColors.Muted,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val interactionSource = remember { MutableInteractionSource() }
            val buttonInteractionSource = remember { MutableInteractionSource() }
            val focused =
                interactionSource.collectIsFocusedAsState().value ||
                    buttonInteractionSource.collectIsFocusedAsState().value
            LaunchedEffect(focused) {
                if (focused) {
                    bringIntoViewRequester.bringIntoView()
                }
            }
            OverviewText(
                overview = overview ?: "",
                maxLines = 3,
                onClick = overviewOnClick,
                interactionSource = interactionSource,
                modifier = Modifier.padding(top = 8.dp),
            )
// Benflix look A: flat buttons
            androidx.compose.runtime.CompositionLocalProvider(
                com.github.damontecres.wholphin.ui.components.LocalFlatButtons provides true,
            ) {
                ExpandableFaButton(
                    title = if (favorite) R.string.remove_favorite else R.string.add_favorite,
                    iconStringRes = R.string.fa_heart,
                    onClick = favoriteOnClick,
                    iconColor = if (favorite) Color.White else Color.Unspecified,
                    interactionSource = buttonInteractionSource,
                    modifier = Modifier,
                )
            }
        }
    }
}

@PreviewTvSpec
@Composable
private fun PersonPreview() {
    WholphinTheme {
        PersonHeader(
            name = "John Smith",
            overview = "John Smith is an actor",
            imageUrl = null,
            birthdate = LocalDate.of(1975, 3, 22),
            birthPlace = "Phoenix, Arizona, USA",
            deathdate = LocalDate.of(2025, 2, 1),
            overviewOnClick = {},
            favorite = true,
            favoriteOnClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** "Humphrey Bogart" -> "HB" */
private fun initialsOf(name: String): String =
    name
        .split(' ')
        .filter { it.isNotBlank() }
        .let { if (it.size > 1) listOf(it.first(), it.last()) else it }
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
