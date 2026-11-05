package com.github.damontecres.wholphin.ui.games

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.GuestMode
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

/**
 * A movie in the game, with its IMDb rating (out of 10). Jellyfin's "community rating" comes
 * from IMDb via the OMDb plugin. It's kept in tenths (7.4 = 74) so comparing is exact.
 */
data class ScoredMovie(
    val name: String,
    val year: Int?,
    val score: Int,
    val poster: String?,
) {
    /** The rating as IMDb shows it, like "7.4" */
    val rating: String get() = "${score / 10}.${score % 10}"
}

sealed interface HigherLowerState {
    data object Loading : HigherLowerState

    data class NotEnough(
        val message: String,
    ) : HigherLowerState

    data class Playing(
        val known: ScoredMovie,
        val guess: ScoredMovie,
        /** Right answers in a row so far */
        val streak: Int,
        /** This profile's best streak before this game */
        val best: Int,
        /** null until they answer; then whether they were right */
        val right: Boolean? = null,
    ) : HigherLowerState

    data class Over(
        val streak: Int,
        val best: Int,
        val newBest: Boolean,
        val last: Pair<ScoredMovie, ScoredMovie>,
    ) : HigherLowerState
}

/**
 * Benflix "Higher or Lower": two posters from your library. You see the first one's
 * IMDb rating; guess whether the second scored higher or lower. It keeps going until the first wrong
 * guess, and the "Highest scores" board ranks the best streak (right answers in a row).
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
                            HigherLowerState.NotEnough("You need at least $MIN_MOVIES movies with IMDb ratings to play.")
                        return@launch
                    }
                    deck = ArrayDeque(movies.shuffled())
                    _state.value = HigherLowerState.Playing(deck.removeFirst(), deck.removeFirst(), 0, best())
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
            val streak = if (right) playing.streak + 1 else playing.streak
            _state.value = playing.copy(right = right, streak = streak)
            viewModelScope.launch {
                delay(REVEAL_MS)
                if (right) {
                    // Endless: when every movie has been shown, shuffle them all back in
                    if (deck.isEmpty()) deck = ArrayDeque(pool.filter { it != playing.guess }.shuffled())
                    _state.value = HigherLowerState.Playing(playing.guess, deck.removeFirst(), streak, playing.best)
                } else {
                    val best = prefs.getInt(bestKey(), 0)
                    val newBest = streak > best
                    if (newBest) prefs.edit { putInt(bestKey(), streak) }
                    _state.value = HigherLowerState.Over(streak, maxOf(best, streak), newBest, playing.known to playing.guess)
                }
            }
        }

        fun best(): Int = prefs.getInt(bestKey(), 0)

        private fun bestKey(): String {
            // Guest scores are kept apart from the owner's, like the Movie riddle
            val profile =
                when {
                    GuestMode.isActive -> "guest"
                    KidsMode.isActive -> "kids"
                    else -> serverRepository.currentUser?.id?.toString() ?: "guest"
                }
            // Best streak (right answers in a row). The 5-round scores had their own key, so they don't mix in.
            return "${profile}_$BOARD_KEY"
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
                    val score =
                        dto.communityRating?.let { Math.round(it * 10) }?.takeIf { it > 0 } ?: return@mapNotNull null
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

            /** The "Highest scores" board's key: best streak */
            const val BOARD_KEY = "HIGHER_LOWER_STREAK"
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
                .padding(horizontal = if (isTv) 48.dp else 16.dp, vertical = if (isTv) 24.dp else 12.dp),
    ) {
        FitScreen(isTv) { HigherLowerContent(state, isTv, viewModel) }
    }
}

@Composable
private fun HigherLowerContent(
    state: HigherLowerState,
    isTv: Boolean,
    viewModel: HigherLowerViewModel,
) {
    run {
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
                    title = if (s.newBest) "New best streak!" else "Streak over: ${s.streak} in a row",
                    body =
                        (if (s.newBest) "${s.streak} right in a row.\n" else "") +
                            "${b.name} is rated ${b.rating}, ${a.name} is rated ${a.rating} on IMDb.\n" +
                            "Your best streak: ${s.best}",
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
            text = "Streak ${state.streak}  ·  Your best ${maxOf(state.best, state.streak)}",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFFFC94D),
        )
        Text(
            text = "Is the second movie rated higher or lower on IMDb?",
            fontSize = if (isTv) 20.sp else 16.sp,
            color = Color.White.copy(alpha = .8f),
            textAlign = TextAlign.Center,
        )
        // On TV the posters are sized from the screen's height so the buttons and the answer always fit below
        val tvPosterWidth = tvPictureHeight(0.42f) * (2f / 3f)
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 32.dp else 12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) {
            PosterSide(
                state.known,
                showScore = true,
                highlight = null,
                modifier = if (isTv) Modifier.width(tvPosterWidth) else Modifier.weight(1f),
            )
            // The new movie to guess fades in slowly each round
            val fade = remember(state.guess.name) { Animatable(0f) }
            LaunchedEffect(state.guess.name) { fade.animateTo(1f, tween(GUESS_FADE_MS)) }
            PosterSide(
                state.guess,
                showScore = state.right != null,
                highlight = state.right,
                fadeMs = GUESS_FADE_MS,
                modifier =
                    (if (isTv) Modifier.width(tvPosterWidth) else Modifier.weight(1f))
                        .graphicsLayer { alpha = fade.value },
            )
        }
        // The answer shows right above the buttons (the space is kept so nothing jumps)
        Text(
            text =
                when (state.right) {
                    true -> "Right! ${state.guess.name} is rated ${state.guess.rating}"
                    false -> "Nope, ${state.guess.name} is rated ${state.guess.rating}"
                    null -> " "
                },
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (state.right == true) Color(0xFF46D37A) else BenflixRed,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        val first = remember(state.guess.name) { FocusRequester() }
        LaunchedEffect(state.guess.name) { first.tryRequestFocus() }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = if (isTv) Modifier.width(tvPosterWidth * 2 + 32.dp) else Modifier.fillMaxWidth(),
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
    }
}

/** How long the new movie to guess takes to fade in (Ben asked for slower) */
private const val GUESS_FADE_MS = 1_200

@Composable
private fun PosterSide(
    movie: ScoredMovie,
    showScore: Boolean,
    highlight: Boolean?,
    modifier: Modifier = Modifier,
    fadeMs: Int = 0,
) {
    val context = LocalContext.current
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
                model =
                    remember(movie.poster, fadeMs) {
                        ImageRequest
                            .Builder(context)
                            .data(movie.poster)
                            .crossfade(fadeMs)
                            .build()
                    },
                contentDescription = movie.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                text = if (showScore) "IMDb ${movie.rating}" else "IMDb ?",
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
        modifier = Modifier.padding(top = 24.dp),
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
