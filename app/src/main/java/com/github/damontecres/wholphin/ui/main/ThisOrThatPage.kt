package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.HomeItemFields
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.games.FitScreen
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import javax.inject.Inject

/**
 * Benflix "This or that": two posters, pick one. The winner stays and meets a new one,
 * for 8 rounds. Whatever's left at the end is tonight's pick.
 */
data class ThisOrThatState(
    /** null while loading */
    val pool: List<BaseItem>? = null,
    val champion: BaseItem? = null,
    val challengerIndex: Int = 1,
    val round: Int = 1,
    val done: Boolean = false,
)

@HiltViewModel
class ThisOrThatViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ThisOrThatState())
        val state: StateFlow<ThisOrThatState> = _state

        init {
            start()
        }

        fun start() {
            _state.value = ThisOrThatState()
            viewModelScope.launch(WholphinDispatchers.IO) {
                val pool =
                    try {
                        api.itemsApi
                            .getItems(
                                GetItemsRequest(
                                    userId = serverRepository.currentUser?.id,
                                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                    recursive = true,
                                    isPlayed = false,
                                    minCommunityRating = 6.0,
                                    sortBy = listOf(ItemSortBy.RANDOM),
                                    limit = ROUNDS + 1,
                                    fields = HomeItemFields,
                                ),
                            ).content.items
                            .map { BaseItem(it) }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "This or that")
                        emptyList()
                    }
                _state.value = ThisOrThatState(pool = pool, champion = pool.firstOrNull(), done = pool.size < 2)
            }
        }

        /** [keepChampion] = the one that was already there won */
        fun pick(keepChampion: Boolean) {
            _state.update { s ->
                val pool = s.pool ?: return@update s
                val challenger = pool.getOrNull(s.challengerIndex) ?: return@update s.copy(done = true)
                val champion = if (keepChampion) s.champion else challenger
                val next = s.challengerIndex + 1
                if (next >= pool.size || s.round >= ROUNDS) {
                    s.copy(champion = champion, done = true)
                } else {
                    s.copy(champion = champion, challengerIndex = next, round = s.round + 1)
                }
            }
        }

        companion object {
            const val ROUNDS = 8
        }
    }

@Composable
fun ThisOrThatPage(
    modifier: Modifier = Modifier,
    viewModel: ThisOrThatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isTv = rememberIsTv()
    val pool = state.pool
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .fillMaxSize()
                .padding(top = if (isTv) 16.dp else 8.dp, start = 8.dp, end = 8.dp),
    ) {
        Text(
            text = "THIS OR THAT",
            fontFamily = BenflixTitleFont,
            fontSize = if (isTv) 48.sp else 36.sp,
            letterSpacing = 1.sp,
        )
        when {
            pool == null -> {
                LoadingPage(Modifier.fillMaxSize())
            }

            state.done -> {
                // Shrinks to fit on any TV so the buttons are never below the screen
                FitScreen(isTv, Modifier.weight(1f)) {
                    Winner(
                        item = state.champion,
                        isTv = isTv,
                        onPlay = { viewModel.navigationManager.navigateTo(Destination.MediaItem(it)) },
                        onAgain = viewModel::start,
                    )
                }
            }

            else -> {
                val challenger = pool.getOrNull(state.challengerIndex)
                Text(
                    text = "ROUND ${state.round} OF ${ThisOrThatViewModel.ROUNDS} · WHICH ONE TONIGHT?",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                    color = Color(0xFFA69C9D),
                )
                BoxWithConstraints(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    // Two posters side by side that always fit: on a phone they share the width,
                    // on a TV the height decides
                    val posterWidth = min(maxWidth * .42f, (maxHeight * .78f) * (2f / 3f))
                    val firstFocus = remember { FocusRequester() }
                    LaunchedEffect(state.round) {
                        delay(60)
                        firstFocus.tryRequestFocus()
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(if (isTv) 40.dp else 12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        PickPoster(
                            item = state.champion,
                            width = posterWidth,
                            onClick = { viewModel.pick(true) },
                            modifier = Modifier.focusRequester(firstFocus),
                        )
                        Text(
                            text = "or",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = posterWidth * .7f),
                        )
                        PickPoster(
                            item = challenger,
                            width = posterWidth,
                            onClick = { viewModel.pick(false) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickPoster(
    item: BaseItem?,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(width),
    ) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            modifier = modifier.fillMaxWidth().aspectRatio(2f / 3f),
        ) {
            AsyncImage(
                model = LocalImageUrlService.current.rememberImageUrl(item),
                contentDescription = item?.name,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.DarkGray),
            )
        }
        Text(
            text = listOfNotNull(item?.name, item?.data?.productionYear?.toString()).joinToString(" · "),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Winner(
    item: BaseItem?,
    isTv: Boolean,
    onPlay: (BaseItem) -> Unit,
    onAgain: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(item) {
        delay(60)
        focus.tryRequestFocus()
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 16.dp),
    ) {
        if (item == null) {
            Text("Not enough to pick from yet. Try again later.", fontSize = 16.sp)
        } else {
            Text(
                text = "TONIGHT'S PICK",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.3.sp,
                color = Color(0xFFE50914),
            )
            AsyncImage(
                model = LocalImageUrlService.current.rememberImageUrl(item),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .width(if (isTv) 200.dp else 160.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.DarkGray),
            )
            Text(
                text = item.name.orEmpty(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (item != null) {
                Button(onClick = { onPlay(item) }, modifier = Modifier.focusRequester(focus)) {
                    Text("Let's watch it")
                }
            }
            Button(onClick = onAgain, modifier = if (item == null) Modifier.focusRequester(focus) else Modifier) {
                Text("Play again")
            }
        }
    }
}
