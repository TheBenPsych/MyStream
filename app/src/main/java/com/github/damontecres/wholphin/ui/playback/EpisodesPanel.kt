package com.github.damontecres.wholphin.ui.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.ItemRowFields
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.cards.EpisodeStandIn
import com.github.damontecres.wholphin.ui.cards.hasOwnPicture
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

data class PlayerEpisodesState(
    val seasons: List<BaseItem> = emptyList(),
    val seasonId: UUID? = null,
    val episodes: List<BaseItem> = emptyList(),
    val loading: Boolean = true,
)

/**
 * Benflix: the "Episodes" button in the player. Shows the show's seasons and episodes over the
 * video, so you can jump to another episode without leaving the player.
 */
@HiltViewModel
class PlayerEpisodesViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(PlayerEpisodesState())
        val state: StateFlow<PlayerEpisodesState> = _state
        private var loadedFor: UUID? = null

        fun load(episode: BaseItem) {
            val seriesId = episode.data.seriesId ?: return
            if (loadedFor == episode.id) return
            loadedFor = episode.id
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    val seasons =
                        api.tvShowsApi
                            .getSeasons(
                                seriesId = seriesId,
                                userId = serverRepository.currentUser?.id,
                            ).content.items
                            .map { BaseItem(it, true) }
                    val seasonId = episode.data.seasonId ?: seasons.firstOrNull()?.id
                    _state.update { it.copy(seasons = seasons, seasonId = seasonId) }
                    seasonId?.let { loadSeason(seriesId, it) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Couldn't load the seasons for the Episodes panel")
                    _state.update { it.copy(loading = false) }
                }
            }
        }

        fun pickSeason(
            seriesId: UUID,
            seasonId: UUID,
        ) {
            if (_state.value.seasonId == seasonId) return
            _state.update { it.copy(seasonId = seasonId, episodes = emptyList(), loading = true) }
            viewModelScope.launch(WholphinDispatchers.IO) { loadSeason(seriesId, seasonId) }
        }

        private suspend fun loadSeason(
            seriesId: UUID,
            seasonId: UUID,
        ) {
            try {
                val episodes =
                    api.tvShowsApi
                        .getEpisodes(
                            GetEpisodesRequest(
                                seriesId = seriesId,
                                seasonId = seasonId,
                                userId = serverRepository.currentUser?.id,
                                fields = ItemRowFields,
                                enableUserData = true,
                            ),
                        ).content.items
                        .map { BaseItem(it, true) }
                if (_state.value.seasonId == seasonId) {
                    _state.update { it.copy(episodes = episodes, loading = false) }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Couldn't load the episodes for the Episodes panel")
                _state.update { it.copy(loading = false) }
            }
        }

        /** Swap the player over to another episode; Back still goes where it went before */
        fun play(episode: BaseItem) {
            navigationManager.goBack()
            navigationManager.navigateTo(Destination.Playback(episode))
        }
    }

/**
 * The panel itself: season buttons on top, a row of episode pictures underneath. Works with a
 * TV remote (left/right through episodes, up to the seasons) and by tapping and swiping on phones.
 */
@Composable
fun EpisodesPanel(
    current: BaseItem,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerEpisodesViewModel = hiltViewModel(),
) {
    LaunchedEffect(current.id) { viewModel.load(current) }
    val state by viewModel.state.collectAsState()
    val seriesId = current.data.seriesId

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = .85f), Color.Black.copy(alpha = .95f)),
                    ),
                ).padding(top = 48.dp, bottom = 24.dp),
    ) {
        Text(
            text = current.data.seriesName ?: stringResource(R.string.player_episodes),
            color = Color.White,
            fontSize = BenflixType.rowName,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        if (state.seasons.size > 1 && seriesId != null) {
            val seasonListState = rememberLazyListState()
            LazyRow(
                state = seasonListState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 32.dp),
            ) {
                itemsIndexed(state.seasons, key = { _, s -> s.id }) { _, season ->
                    val selected = season.id == state.seasonId
                    Button(
                        onClick = { viewModel.pickSeason(seriesId, season.id) },
                        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                        colors =
                            ButtonDefaults.colors(
                                containerColor = if (selected) Color.White.copy(alpha = .85f) else Color.White.copy(alpha = .12f),
                                contentColor = if (selected) Color.Black else Color.White,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black,
                            ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.height(40.dp),
                        scale = ButtonDefaults.scale(focusedScale = 1f),
                    ) {
                        Text(
                            text = season.name ?: "",
                            fontSize = BenflixType.body,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        val listState = rememberLazyListState()
        val focusRequester = remember { FocusRequester() }
        val startIndex = state.episodes.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        LaunchedEffect(state.episodes) {
            if (state.episodes.isNotEmpty()) {
                listState.scrollToItem(startIndex)
                focusRequester.tryRequestFocus()
            }
        }
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 32.dp),
            modifier = Modifier.height(EPISODE_CARD_HEIGHT),
        ) {
            itemsIndexed(state.episodes, key = { _, e -> e.id }) { index, episode ->
                EpisodePanelCard(
                    episode = episode,
                    nowPlaying = episode.id == current.id,
                    onClick = {
                        if (episode.id == current.id) onDismiss() else viewModel.play(episode)
                    },
                    modifier = if (index == startIndex) Modifier.focusRequester(focusRequester) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun EpisodePanelCard(
    episode: BaseItem,
    nowPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = .08f),
                focusedContainerColor = Color.White.copy(alpha = .22f),
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder =
                    androidx.tv.material3.Border(
                        border =
                            androidx.compose.foundation
                                .BorderStroke(2.dp, Color.White),
                        shape = RoundedCornerShape(10.dp),
                    ),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .width(EPISODE_CARD_WIDTH)
                .fillMaxHeight(),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(Color.DarkGray),
            ) {
                if (episode.hasOwnPicture()) {
                    AsyncImage(
                        model = LocalImageUrlService.current.rememberImageUrl(episode),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    )
                } else {
                    EpisodeStandIn(episode, EPISODE_CARD_WIDTH)
                }
                if (nowPlaying) {
                    Text(
                        text = stringResource(R.string.player_now_playing),
                        color = Color(0xFF0B0C10),
                        fontSize = BenflixType.tag,
                        fontWeight = FontWeight.Bold,
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .background(Color.White, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                // How much of it you've watched
                val progress =
                    when {
                        episode.played -> {
                            1f
                        }

                        else -> {
                            (
                                episode.data.userData
                                    ?.playedPercentage
                                    ?.toFloat() ?: 0f
                            ) / 100f
                        }
                    }
                if (progress > 0f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.White.copy(alpha = .3f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .background(Color.White),
                        )
                    }
                }
            }
            Text(
                text = listOfNotNull(episode.data.indexNumber?.let { "$it." }, episode.name).joinToString(" "),
                color = Color.White,
                fontSize = BenflixType.body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp),
            )
            Text(
                text = episode.data.overview ?: "",
                color = BenflixColors.Soft,
                fontSize = BenflixType.tag,
                lineHeight = BenflixType.tag * 1.3f,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

private val EPISODE_CARD_WIDTH = 240.dp
private val EPISODE_CARD_HEIGHT = 230.dp
