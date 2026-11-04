package com.github.damontecres.wholphin.ui.detail.series

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.AniListCharacter
import com.github.damontecres.wholphin.services.AniListInfo
import com.github.damontecres.wholphin.services.AniListService
import com.github.damontecres.wholphin.services.AnimeEpisodeNumbers
import com.github.damontecres.wholphin.services.BenflixAnimeTracks
import com.github.damontecres.wholphin.services.BenflixDubChoice
import com.github.damontecres.wholphin.services.DubCoverage
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.util.StringStringProvider
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.personsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/** A movie that goes with this show, and where it fits ("after Season 1") */
data class WatchOrderMovie(
    val item: BaseItem,
    val where: String,
)

data class AnimeExtrasState(
    val isAnime: Boolean = false,
    val info: AniListInfo? = null,
    val dubLine: String? = null,
    val movies: List<WatchOrderMovie> = emptyList(),
)

/**
 * Benflix: the anime extras on a show's page: AniList fan score, next episode countdown, fan
 * genres, characters and voices, how far the English dub goes, and which movies go where.
 */
@HiltViewModel
class AnimeExtrasViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val aniList: AniListService,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(AnimeExtrasState())
        val state: StateFlow<AnimeExtrasState> = _state

        private var series: BaseItem? = null
        private var library: BaseItemDto? = null

        fun load(series: BaseItem) {
            if (this.series != null) return
            this.series = series
            viewModelScope.launchIO {
                try {
                    val ancestors =
                        api.libraryApi
                            .getAncestors(series.id, serverRepository.currentUser?.id)
                            .content
                    library =
                        ancestors.firstOrNull { it.collectionType == CollectionType.TVSHOWS || it.type == BaseItemKind.COLLECTION_FOLDER }
                    val anime =
                        BenflixAnimeTracks.isAnime(series.data) ||
                            ancestors.any { it.name?.contains("anime", ignoreCase = true) == true }
                    if (!anime) return@launchIO
                    _state.update { it.copy(isAnime = true) }
                    val show = series.type == BaseItemKind.SERIES
                    if (show) viewModelScope.launchIO { loadDubLine(series) }
                    val info = aniList.infoFor(series.data) ?: return@launchIO
                    _state.update { it.copy(info = info) }
                    if (show) loadMovies(series, info)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not load anime extras for %s", series.name)
                }
            }
        }

        /** "English voices: episodes 1 to 24", and SUB ONLY badges on the rest */
        private suspend fun loadDubLine(series: BaseItem) {
            try {
                val episodes =
                    api.tvShowsApi
                        .getEpisodes(
                            GetEpisodesRequest(
                                seriesId = series.id,
                                userId = serverRepository.currentUser?.id,
                                fields = listOf(ItemFields.MEDIA_STREAMS),
                                enableImages = false,
                                enableUserData = false,
                            ),
                        ).content.items
                        .filter { (it.parentIndexNumber ?: 1) > 0 && it.indexNumber != null }
                val across = AnimeEpisodeNumbers.workOut(episodes)
                val oneSeason = episodes.map { it.parentIndexNumber ?: 1 }.distinct().size <= 1
                val line =
                    DubCoverage.workOut(episodes) { ep ->
                        across[ep.id] ?: if (oneSeason) ep.indexNumber else null
                    }
                _state.update { it.copy(dubLine = line) }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not work out the dub for %s", series.name)
            }
        }

        /**
         * Movies that go with this show: specials Jellyfin already places between seasons (long ones,
         * so movies), plus movies AniList links to this show that are on Benflix.
         */
        private suspend fun loadMovies(
            series: BaseItem,
            info: AniListInfo,
        ) {
            val userId = serverRepository.currentUser?.id
            val out = mutableListOf<WatchOrderMovie>()
            val seasons =
                api.itemsApi
                    .getItems(
                        GetItemsRequest(
                            userId = userId,
                            parentId = series.id,
                            includeItemTypes = listOf(BaseItemKind.SEASON),
                            sortBy = listOf(ItemSortBy.INDEX_NUMBER),
                            enableImages = false,
                        ),
                    ).content.items
                    .filter { (it.indexNumber ?: 0) > 0 }
            // 1. Jellyfin's own placing of specials ("airs before season 2")
            val specials =
                seasons
                    .firstOrNull()
                    ?.let {
                        api.tvShowsApi
                            .getEpisodes(
                                GetEpisodesRequest(
                                    seriesId = series.id,
                                    userId = userId,
                                    season = 0,
                                    fields = SlimItemFields,
                                ),
                            ).content.items
                    }.orEmpty()
            specials
                .filter { (it.runTimeTicks ?: 0) >= MOVIE_TICKS }
                .forEach { sp ->
                    val where =
                        sp.airsAfterSeasonNumber?.let { "after Season $it" }
                            ?: sp.airsBeforeSeasonNumber?.let { if (it <= 1) "before Season 1" else "after Season ${it - 1}" }
                            ?: return@forEach
                    out += WatchOrderMovie(BaseItem(sp), where)
                }
            // 2. Movies AniList links to the show, found by name in the anime movie libraries
            val have =
                out
                    .map {
                        it.item.name
                            .orEmpty()
                            .lowercase()
                    }.toMutableSet()
            info.related
                .filter { it.format == "MOVIE" }
                .take(MAX_MOVIES)
                .forEach { rel ->
                    val found = findMovie(rel.titles, rel.year) ?: return@forEach
                    if (!have.add(found.name.orEmpty().lowercase())) return@forEach
                    val date = (rel.year ?: return@forEach) * 100 + (rel.month ?: 6)
                    val before =
                        seasons.lastOrNull { s ->
                            s.premiereDate?.let { it.year * 100 + it.monthValue }?.let { it <= date } == true
                        }
                    val where =
                        when {
                            before == null -> "before Season 1"
                            before == seasons.last() && seasons.size > 1 -> "after Season ${before.indexNumber}"
                            seasons.size == 1 -> "after the show"
                            else -> "after Season ${before.indexNumber}"
                        }
                    out += WatchOrderMovie(BaseItem(found), where)
                }
            if (out.isNotEmpty()) _state.update { it.copy(movies = out) }
        }

        private suspend fun findMovie(
            titles: List<String>,
            year: Int?,
        ): BaseItemDto? {
            for (title in titles.take(2)) {
                val wanted = AniListService.clean(title)
                val hits =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = serverRepository.currentUser?.id,
                                searchTerm = title,
                                includeItemTypes = listOf(BaseItemKind.MOVIE),
                                recursive = true,
                                fields = SlimItemFields,
                                limit = 5,
                            ),
                        ).content.items
                hits
                    .firstOrNull { dto ->
                        val sameName = listOfNotNull(dto.name, dto.originalTitle).any { AniListService.clean(it) == wanted }
                        val sameYear = year == null || dto.productionYear == null || kotlin.math.abs(dto.productionYear!! - year) <= 1
                        sameName && sameYear
                    }?.let { return it }
            }
            return null
        }

        /** All the shows in this library with a fan genre, like Isekai */
        fun openTag(tag: String) {
            val lib = library ?: return
            viewModelScope.launchIO {
                try {
                    val index =
                        aniList.savedIndex(lib.id) ?: run {
                            withContext(WholphinDispatchers.Main) { showToast(context, "Finding every $tag show…") }
                            val shows =
                                api.itemsApi
                                    .getItems(
                                        GetItemsRequest(
                                            userId = serverRepository.currentUser?.id,
                                            parentId = lib.id,
                                            includeItemTypes = listOf(BaseItemKind.SERIES),
                                            recursive = true,
                                            enableImages = false,
                                            enableUserData = false,
                                        ),
                                    ).content.items
                            aniList.index(lib.id, shows)
                        }
                    val ids =
                        index
                            .filter { (_, entry) -> entry.tags.any { it.equals(tag, ignoreCase = true) } }
                            .keys
                            .plus(series?.id)
                            .filterNotNull()
                            .distinct()
                            .take(MAX_TAG_SHOWS)
                    withContext(WholphinDispatchers.Main) {
                        navigationManager.navigateTo(
                            Destination.ItemGrid(
                                title = StringStringProvider("$tag anime"),
                                request =
                                    GetItemsRequest(
                                        ids = ids,
                                        fields = SlimItemFields,
                                        enableUserData = true,
                                    ),
                                requestHandler = GetItemsRequestHandler,
                            ),
                        )
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not open the %s genre", tag)
                    withContext(WholphinDispatchers.Main) { showToast(context, "Couldn't open $tag") }
                }
            }
        }

        /** Opens the voice actor's page if they're in anything on Benflix */
        fun openCharacter(character: AniListCharacter) {
            val dub = series?.data?.let { BenflixDubChoice.modeFor(it) } == BenflixDubChoice.Mode.DUB
            val voices = listOfNotNull(if (dub) character.english else character.japanese, character.japanese, character.english).distinct()
            if (voices.isEmpty()) return
            viewModelScope.launchIO {
                try {
                    val person =
                        voices.firstNotNullOfOrNull { voice ->
                            api.personsApi
                                .getPersons(searchTerm = voice.name, limit = 3)
                                .content.items
                                .firstOrNull { samePerson(it.name, voice.name) }
                                ?: reversedName(voice.name)?.let { flipped ->
                                    api.personsApi
                                        .getPersons(searchTerm = flipped, limit = 3)
                                        .content.items
                                        .firstOrNull { samePerson(it.name, flipped) }
                                }
                        }
                    withContext(WholphinDispatchers.Main) {
                        if (person != null) {
                            navigationManager.navigateTo(BaseItem(person).destination())
                        } else {
                            showToast(context, "${voices.first().name} isn't in anything else on Benflix yet")
                        }
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not find %s", character.name)
                }
            }
        }

        private fun samePerson(
            a: String?,
            b: String,
        ) = a != null && AniListService.clean(a) == AniListService.clean(b)

        /** Japanese names are sometimes written family name first */
        private fun reversedName(name: String): String? =
            name
                .split(" ")
                .takeIf { it.size == 2 }
                ?.reversed()
                ?.joinToString(" ")

        fun openMovie(movie: WatchOrderMovie) {
            navigationManager.navigateTo(movie.item.destination())
        }

        companion object {
            /** About an hour, so a special that long is a movie */
            private const val MOVIE_TICKS = 55L * 60 * 10_000_000
            private const val MAX_MOVIES = 8
            private const val MAX_TAG_SHOWS = 150
        }
    }

@Composable
private fun animeExtras(series: BaseItem): AnimeExtrasViewModel {
    val viewModel: AnimeExtrasViewModel = hiltViewModel(key = "${series.id}-anime-extras")
    LaunchedEffect(series.id) { viewModel.load(series) }
    return viewModel
}

/** The next episode countdown and dub line, under an anime show's or movie's title */
@Composable
fun AnimeFacts(
    series: BaseItem,
    modifier: Modifier = Modifier,
) {
    val viewModel = animeExtras(series)
    val state by viewModel.state.collectAsState()
    if (!state.isAnime) return
    val info = state.info
    val countdown = countdownText(info)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        // The AniList score sits next to the stars on the line above (QuickDetails)
        countdown?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = COUNTDOWN_COLOR,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        state.dubLine?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = .8f),
            )
        }
    }
}

/** "Episode 8 out Saturday 5:30 PM · in 2 days", ticking down; null when nothing is coming */
@Composable
private fun countdownText(info: AniListInfo?): String? {
    val at = info?.nextAiringAt ?: return null
    val now by produceState(System.currentTimeMillis() / 1000, at) {
        while (true) {
            value = System.currentTimeMillis() / 1000
            delay(30_000)
        }
    }
    val left = at - now
    if (left <= 0) return null
    val time = Instant.ofEpochSecond(at).atZone(ZoneId.systemDefault())
    val day =
        when {
            left < 20 * 3600 && time.toLocalDate() == java.time.LocalDate.now() -> "today"

            time.toLocalDate() ==
                java.time.LocalDate
                    .now()
                    .plusDays(1)
            -> "tomorrow"

            else -> time.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        }
    val clock =
        time.format(
            java.time.format.DateTimeFormatter
                .ofLocalizedTime(java.time.format.FormatStyle.SHORT),
        )
    val inWords =
        when {
            left < 3600 -> "in ${left / 60 + 1} min"
            left < 24 * 3600 -> "in ${left / 3600} h ${(left % 3600) / 60} min"
            else -> "in ${left / 86400} days ${(left % 86400) / 3600} h".replace("in 1 days", "in 1 day")
        }
    val which = info.nextTitle?.let { "$it, " }.orEmpty()
    return "${which}Episode ${info.nextEpisode} out $day $clock · $inWords"
}

/** Fan genres, characters and voices, and movies that go with the show, as rows on its page */
@Composable
fun AnimeRows(
    series: BaseItem,
    modifier: Modifier = Modifier,
) {
    val viewModel = animeExtras(series)
    val state by viewModel.state.collectAsState()
    if (!state.isAnime) return
    val info = state.info
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = modifier) {
        if (state.movies.isNotEmpty()) {
            ItemRow(
                title = "Watch order: movies",
                items = state.movies,
                onClickItem = { _, movie -> viewModel.openMovie(movie) },
                onLongClickItem = { _, _ -> },
                cardContent = { _, movie, mod, onClick, onLongClick ->
                    if (movie != null) MovieOrderCard(movie, onClick, onLongClick, mod)
                },
            )
        }
        val tags = info?.tags.orEmpty().take(MAX_TAGS)
        if (tags.isNotEmpty()) {
            ItemRow(
                title = "Fan genres",
                items = tags,
                onClickItem = { _, tag -> viewModel.openTag(tag) },
                onLongClickItem = { _, _ -> },
                cardContent = { _, tag, mod, onClick, onLongClick ->
                    if (tag != null) TagChip(tag, onClick, onLongClick, mod)
                },
            )
        }
        val characters = info?.characters.orEmpty()
        if (characters.isNotEmpty()) {
            ItemRow(
                title = "Characters and voices",
                items = characters,
                onClickItem = { _, c -> viewModel.openCharacter(c) },
                onLongClickItem = { _, _ -> },
                cardContent = { _, c, mod, onClick, onLongClick ->
                    if (c != null) CharacterCard(c, onClick, onLongClick, mod)
                },
            )
        }
    }
}

@Composable
private fun TagChip(
    tag: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape =
            androidx.tv.material3.ClickableSurfaceDefaults
                .shape(RoundedCornerShape(50)),
        colors =
            androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = .12f),
                contentColor = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        modifier = modifier,
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun CharacterCard(
    character: AniListCharacter,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        colors = CardDefaults.colors(containerColor = Color.White.copy(alpha = .06f)),
        modifier = modifier.width(CHARACTER_WIDTH),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Face(character.image)
                (character.japanese ?: character.english)?.image?.let { Face(it) }
            }
            Text(
                text = character.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            character.japanese?.let { VoiceLine("JP", it.name) }
            character.english?.let { VoiceLine("EN", it.name) }
        }
    }
}

@Composable
private fun Face(url: String?) {
    Surface(
        shape = CircleShape,
        colors = SurfaceDefaults.colors(containerColor = Color.White.copy(alpha = .1f)),
        modifier = Modifier.size(FACE),
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(FACE).clip(CircleShape),
            )
        }
    }
}

@Composable
private fun VoiceLine(
    language: String,
    name: String,
) {
    Text(
        text = "$language  $name",
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = .75f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MovieOrderCard(
    movie: WatchOrderMovie,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        colors = CardDefaults.colors(containerColor = Color.White.copy(alpha = .06f)),
        modifier = modifier.width(MOVIE_WIDTH),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = movie.item.name.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Watch ${movie.where}",
                style = MaterialTheme.typography.bodySmall,
                color = COUNTDOWN_COLOR,
            )
        }
    }
}

private val COUNTDOWN_COLOR = Color(0xFFFFB74D)
private val CHARACTER_WIDTH = 150.dp
private val MOVIE_WIDTH = 220.dp
private val FACE = 64.dp
private const val MAX_TAGS = 12
