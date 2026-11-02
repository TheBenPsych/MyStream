package com.github.damontecres.wholphin.ui.games

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.theme.BenflixRed
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import javax.inject.Inject

/** A movie in the game, with its critics score (Rotten Tomatoes, out of 100) */
data class ScoredMovie(
    val name: String,
    val year: Int?,
    val score: Int,
    val poster: String?,
)

sealed interface HigherLowerState {
    data object Loading : HigherLowerState

    data class NotEnough(
        val message: String,
    ) : HigherLowerState

    data class Playing(
        val known: ScoredMovie,
        val guess: ScoredMovie,
        /** Which round this is, from 0 */
        val round: Int,
        val rounds: Int,
        val score: Int,
        /** null until they answer; then whether they were right */
        val right: Boolean? = null,
    ) : HigherLowerState

    data class Over(
        val score: Int,
        val rounds: Int,
        val best: Int,
        val newBest: Boolean,
        val last: Pair<ScoredMovie, ScoredMovie>,
    ) : HigherLowerState
}

/**
 * Benflix "Higher or Lower": two posters from your library. You see the first one's critics
 * score; guess whether the second scored higher or lower. Five rounds, one point for each right
 * answer, then "Play again" deals new movies.
 */
@HiltViewModel
class HigherLowerViewModel
    @Inject
    constructor(
        @param:ApplicationContext context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val imageUrlService: ImageUrlService,
    ) : ViewModel() {
        private val prefs = context.getSharedPreferences("benflix_games", Context.MODE_PRIVATE)
        private val _state = MutableStateFlow<HigherLowerState>(HigherLowerState.Loading)
        val state: StateFlow<HigherLowerState> = _state

        private var deck: ArrayDeque<ScoredMovie> = ArrayDeque()
        private var pool: List<ScoredMovie> = emptyList()

        init {
            start()
        }

        fun start() {
            _state.value = HigherLowerState.Loading
            viewModelScope.launch {
                try {
                    // Reuse the movies already loaded; "Play again" just deals a new shuffle
                    val movies = pool.ifEmpty { withContext(WholphinDispatchers.IO) { loadMovies() } }
                    pool = movies
                    if (movies.size < MIN_MOVIES) {
                        _state.value =
                            HigherLowerState.NotEnough("You need at least $MIN_MOVIES movies with critics scores to play.")
                        return@launch
                    }
                    deck = ArrayDeque(movies.shuffled())
                    _state.value = HigherLowerState.Playing(deck.removeFirst(), deck.removeFirst(), 0, ROUNDS, 0)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Higher or Lower couldn't load")
                    _state.value = HigherLowerState.NotEnough("Couldn't load movies from the server. Try again in a moment.")
                }
            }
        }

        fun answer(higher: Boolean) {
            val playing = _state.value as? HigherLowerState.Playing ?: return
            if (playing.right != null) return
            val diff = playing.guess.score - playing.known.score
            // A tie counts as right either way
            val right = diff == 0 || (diff > 0) == higher
            val score = if (right) playing.score + 1 else playing.score
            _state.value = playing.copy(right = right, score = score)
            viewModelScope.launch {
                delay(REVEAL_MS)
                if (playing.round + 1 < playing.rounds && deck.isNotEmpty()) {
                    _state.value = HigherLowerState.Playing(playing.guess, deck.removeFirst(), playing.round + 1, playing.rounds, score)
                } else {
                    val best = prefs.getInt(bestKey(), 0)
                    val newBest = score > best
                    if (newBest) prefs.edit { putInt(bestKey(), score) }
                    _state.value =
                        HigherLowerState.Over(score, playing.round + 1, maxOf(best, score), newBest, playing.known to playing.guess)
                }
            }
        }

        fun best(): Int = prefs.getInt(bestKey(), 0)

        private fun bestKey(): String {
            val profile = if (KidsMode.isActive) "kids" else serverRepository.currentUser?.id?.toString() ?: "guest"
            // Best score out of 5 (the old key held an endless streak, so it's a new key)
            return "${profile}_HIGHER_LOWER_5"
        }

        private suspend fun loadMovies(): List<ScoredMovie> =
            api.itemsApi
                .getItems(
                    GetItemsRequest(
                        userId = serverRepository.currentUser?.id,
                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                        recursive = true,
                        imageTypes = listOf(ImageType.PRIMARY),
                        sortBy = listOf(ItemSortBy.RANDOM),
                        limit = POOL_SIZE,
                    ),
                ).content
                .items
                .mapNotNull { dto ->
                    val score = dto.criticRating?.toInt()?.takeIf { it > 0 } ?: return@mapNotNull null
                    ScoredMovie(
                        name = dto.name ?: return@mapNotNull null,
                        year = dto.productionYear,
                        score = score,
                        poster =
                            imageUrlService.getItemImageUrl(
                                itemId = dto.id,
                                imageType = ImageType.PRIMARY,
                                maxWidth = POSTER_WIDTH,
                                tag = dto.imageTags?.get(ImageType.PRIMARY),
                            ),
                    )
                }.distinctBy { it.name }

        companion object {
            private const val POOL_SIZE = 200
            private const val MIN_MOVIES = 6
            private const val ROUNDS = 5
            private const val REVEAL_MS = 1_600L
            private const val POSTER_WIDTH = 420
        }
    }

@Composable
fun HigherLowerPage(
    modifier: Modifier = Modifier,
    viewModel: HigherLowerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isTv = rememberIsTv()
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier =
            modifier
                .fillMaxSize()
                .background(Color(0xFF0B0B0D))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isTv) 48.dp else 16.dp, vertical = if (isTv) 24.dp else 12.dp),
    ) {
        when (val s = state) {
            HigherLowerState.Loading -> {
                Text("Picking movies from your library…", fontSize = 20.sp, color = Color.White)
            }

            is HigherLowerState.NotEnough -> {
                HigherLowerEnd(title = "Higher or Lower", body = s.message, button = "Try again", onClick = viewModel::start)
            }

            is HigherLowerState.Playing -> {
                HigherLowerRound(s, isTv, viewModel::answer)
            }

            is HigherLowerState.Over -> {
                val (a, b) = s.last
                HigherLowerEnd(
                    title = if (s.newBest) "New best score!" else "You scored ${s.score} of ${s.rounds}",
                    body =
                        (if (s.newBest) "You scored ${s.score} of ${s.rounds}.\n" else "") +
                            "Last one: ${b.name} has ${b.score}%, ${a.name} has ${a.score}%.\n" +
                            "Your best: ${s.best} of ${s.rounds}",
                    button = "Play again with new movies",
                    onClick = viewModel::start,
                )
            }
        }
    }
}

@Composable
private fun HigherLowerRound(
    state: HigherLowerState.Playing,
    isTv: Boolean,
    onAnswer: (Boolean) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (isTv) 16.dp else 12.dp),
        modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
    ) {
        Text(
            text = "HIGHER OR LOWER",
            fontFamily = BenflixTitleFont,
            fontSize = if (isTv) 34.sp else 26.sp,
            color = Color.White,
        )
        Text(
            text = "Round ${state.round + 1} of ${state.rounds}  ·  Score ${state.score}",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFFFC94D),
        )
        Text(
            text = "Did critics score the second movie higher or lower?",
            fontSize = if (isTv) 20.sp else 16.sp,
            color = Color.White.copy(alpha = .8f),
            textAlign = TextAlign.Center,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 32.dp else 12.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(if (isTv) 0.7f else 1f),
        ) {
            PosterSide(state.known, showScore = true, highlight = null, modifier = Modifier.weight(1f))
            PosterSide(
                state.guess,
                showScore = state.right != null,
                highlight = state.right,
                modifier = Modifier.weight(1f),
            )
        }
        val first = remember(state.guess.name) { FocusRequester() }
        LaunchedEffect(state.guess.name) { first.tryRequestFocus() }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(if (isTv) 0.5f else 1f),
        ) {
            listOf(true to "▲ Higher", false to "▼ Lower").forEachIndexed { i, (higher, label) ->
                Button(
                    onClick = { onAnswer(higher) },
                    colors =
                        ButtonDefaults.colors(
                            containerColor = Color(0xFF2A2A2E),
                            contentColor = Color.White,
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black,
                        ),
                    modifier =
                        Modifier
                            .weight(1f)
                            .then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                ) {
                    Text(label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        state.right?.let { right ->
            Text(
                text = if (right) "Right! ${state.guess.score}%" else "Nope, ${state.guess.score}%",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (right) Color(0xFF46D37A) else BenflixRed,
            )
        }
    }
}

@Composable
private fun PosterSide(
    movie: ScoredMovie,
    showScore: Boolean,
    highlight: Boolean?,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1C1C1F)),
        ) {
            AsyncImage(
                model = movie.poster,
                contentDescription = movie.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                text = if (showScore) "🍅 ${movie.score}%" else "🍅 ?",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(10.dp)
                        .background(
                            when (highlight) {
                                true -> Color(0xFF2E8B57)
                                false -> BenflixRed
                                null -> Color.Black.copy(alpha = .7f)
                            },
                            RoundedCornerShape(8.dp),
                        ).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        Text(
            text = movie.name + (movie.year?.let { " ($it)" } ?: ""),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HigherLowerEnd(
    title: String,
    body: String,
    button: String,
    onClick: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(top = 48.dp),
    ) {
        Text(title, fontFamily = BenflixTitleFont, fontSize = 40.sp, color = Color.White, textAlign = TextAlign.Center)
        Text(body, fontSize = 20.sp, color = Color.White.copy(alpha = .85f), textAlign = TextAlign.Center)
        Button(onClick = onClick, modifier = Modifier.focusRequester(focus)) {
            Text(button)
        }
        Text(
            "Press Back to leave",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = .6f),
        )
    }
}
