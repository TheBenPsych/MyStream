package com.github.damontecres.wholphin.ui.games

import android.content.Context
import androidx.compose.animation.Crossfade
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import javax.inject.Inject

/** How a round gives its clue */
enum class ClueKind { PICTURE, PLOT, CAST }

data class GuessRound(
    val number: Int,
    val kind: ClueKind,
    /** Pictures from very blurry to sharp (PICTURE rounds) */
    val pictures: List<String> = emptyList(),
    val text: String = "",
    val choices: List<String>,
    val answer: String,
)

sealed interface GuessState {
    data object Loading : GuessState

    data class NotEnough(
        val message: String,
    ) : GuessState

    data class Playing(
        val round: GuessRound,
        val score: Int,
        /** The choice picked this round, once the person picked */
        val picked: String? = null,
        val pointsThisRound: Int = 0,
    ) : GuessState

    data class Finished(
        val score: Int,
        val best: Int,
        val newBest: Boolean,
        val correct: Int,
    ) : GuessState
}

/**
 * Benflix "Guess the movie": a picture from your own library starts very blurry and gets sharper,
 * and you pick the right title from four. Other rounds show a line of the plot or the cast.
 * Kids only gets Kids titles (the Kids filter already covers every list from the server).
 */
@HiltViewModel
class GuessTheMovieViewModel
    @Inject
    constructor(
        @param:ApplicationContext context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val imageUrlService: ImageUrlService,
    ) : ViewModel() {
        private val prefs = context.getSharedPreferences("benflix_games", Context.MODE_PRIVATE)

        private val _state = MutableStateFlow<GuessState>(GuessState.Loading)
        val state: StateFlow<GuessState> = _state

        private var pool: List<BaseItemDto> = emptyList()
        private var rounds: List<GuessRound> = emptyList()
        private var index = 0
        private var score = 0
        private var correct = 0
        private var roundStarted = 0L

        init {
            start()
        }

        fun start() {
            _state.value = GuessState.Loading
            viewModelScope.launch {
                try {
                    pool = withContext(WholphinDispatchers.IO) { loadMovies() }
                    rounds = buildRounds(pool)
                    if (rounds.size < MIN_ROUNDS) {
                        _state.value = GuessState.NotEnough("You need at least 4 movies with pictures to play.")
                        return@launch
                    }
                    index = 0
                    score = 0
                    correct = 0
                    showRound()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Guess the movie couldn't load")
                    _state.value = GuessState.NotEnough("Couldn't load movies from the server. Try again in a moment.")
                }
            }
        }

        private fun showRound() {
            roundStarted = System.currentTimeMillis()
            _state.value = GuessState.Playing(rounds[index], score)
        }

        fun pick(choice: String) {
            val playing = _state.value as? GuessState.Playing ?: return
            if (playing.picked != null) return
            val elapsed = System.currentTimeMillis() - roundStarted
            val points =
                if (choice == playing.round.answer) {
                    correct++
                    (MAX_POINTS - elapsed / POINTS_DROP_MS).toInt().coerceAtLeast(MIN_POINTS)
                } else {
                    0
                }
            score += points
            _state.value = playing.copy(picked = choice, score = score, pointsThisRound = points)
            viewModelScope.launch {
                delay(REVEAL_MS)
                next()
            }
        }

        private fun next() {
            index++
            if (index < rounds.size) {
                showRound()
            } else {
                val best = prefs.getInt(bestKey(), 0)
                val newBest = score > best
                if (newBest) prefs.edit().putInt(bestKey(), score).apply()
                _state.value = GuessState.Finished(score, maxOf(best, score), newBest, correct)
            }
        }

        fun best(): Int = prefs.getInt(bestKey(), 0)

        private fun bestKey(): String {
            val profile = if (KidsMode.isActive) "kids" else serverRepository.currentUser?.id?.toString() ?: "guest"
            return "${profile}_GUESS_THE_MOVIE"
        }

        private suspend fun loadMovies(): List<BaseItemDto> =
            api.itemsApi
                .getItems(
                    GetItemsRequest(
                        userId = serverRepository.currentUser?.id,
                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                        recursive = true,
                        imageTypes = listOf(ImageType.BACKDROP),
                        sortBy = listOf(ItemSortBy.RANDOM),
                        fields = listOf(ItemFields.OVERVIEW, ItemFields.PEOPLE),
                        limit = POOL_SIZE,
                    ),
                ).content.items
                .filter { !it.name.isNullOrBlank() }
                .distinctBy { it.name }

        private fun buildRounds(movies: List<BaseItemDto>): List<GuessRound> {
            if (movies.size < CHOICES) return emptyList()
            val names = movies.mapNotNull { it.name }
            return movies.shuffled().take(ROUNDS).mapIndexedNotNull { i, movie ->
                val answer = movie.name ?: return@mapIndexedNotNull null
                val others = names.filter { it != answer }.shuffled().take(CHOICES - 1)
                val choices = (others + answer).shuffled()
                val plot = plotClue(movie)
                val cast =
                    movie.people
                        .orEmpty()
                        .filter { it.type == PersonKind.ACTOR }
                        .mapNotNull { it.name }
                        .take(CAST_SHOWN)
                // Mostly pictures, with a plot or cast round mixed in now and then
                val kind =
                    when {
                        i % 3 == 1 && plot != null -> ClueKind.PLOT
                        i % 3 == 2 && cast.size >= 2 -> ClueKind.CAST
                        else -> ClueKind.PICTURE
                    }
                GuessRound(
                    number = i + 1,
                    kind = kind,
                    pictures =
                        if (kind == ClueKind.PICTURE) {
                            BLUR_STEPS.mapNotNull { blur ->
                                imageUrlService.getItemImageUrl(
                                    itemId = movie.id,
                                    imageType = ImageType.BACKDROP,
                                    maxWidth = PICTURE_WIDTH,
                                    blur = blur.takeIf { it > 0 },
                                    tag = movie.backdropImageTags?.firstOrNull(),
                                    imageIndex = 0,
                                )
                            }
                        } else {
                            emptyList()
                        },
                    text =
                        when (kind) {
                            ClueKind.PLOT -> plot.orEmpty()
                            ClueKind.CAST -> "Starring " + cast.joinToString(", ")
                            ClueKind.PICTURE -> ""
                        },
                    choices = choices,
                    answer = answer,
                )
            }
        }

        /** The first sentence of the plot, with the movie's own name hidden */
        private fun plotClue(movie: BaseItemDto): String? {
            val overview = movie.overview?.takeIf { it.length > 30 } ?: return null
            val first = overview.split(Regex("(?<=[.!?])\\s+")).first()
            val name = movie.name ?: return first
            return first.replace(name, "▇▇▇▇", ignoreCase = true)
        }

        companion object {
            private const val POOL_SIZE = 60
            private const val ROUNDS = 10
            private const val MIN_ROUNDS = 4
            private const val CHOICES = 4
            private const val CAST_SHOWN = 3
            private const val MAX_POINTS = 1_000L
            private const val MIN_POINTS = 100
            private const val POINTS_DROP_MS = 15L
            private const val REVEAL_MS = 1_800L
            private const val PICTURE_WIDTH = 960

            /** How blurry the picture is at each step (0 = sharp) */
            val BLUR_STEPS = listOf(60, 35, 20, 10, 4, 0)

            /** How long each blur step stays on screen */
            const val STEP_MS = 2_000L
        }
    }

@Composable
fun GuessTheMoviePage(
    modifier: Modifier = Modifier,
    viewModel: GuessTheMovieViewModel = hiltViewModel(),
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
            GuessState.Loading -> {
                Text("Picking movies from your library…", fontSize = 20.sp, color = Color.White)
            }

            is GuessState.NotEnough -> {
                EndCard(title = "Guess the movie", body = s.message, button = "Try again", onClick = viewModel::start)
            }

            is GuessState.Playing -> {
                RoundContent(s, isTv, viewModel::pick)
            }

            is GuessState.Finished -> {
                EndCard(
                    title = if (s.newBest) "New best score!" else "Nice game!",
                    body = "You got ${s.correct} of 10 right and scored %,d points.\nYour best: %,d".format(s.score, s.best),
                    button = "Play again",
                    onClick = viewModel::start,
                )
            }
        }
    }
}

@Composable
private fun RoundContent(
    state: GuessState.Playing,
    isTv: Boolean,
    onPick: (String) -> Unit,
) {
    val round = state.round
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (isTv) 16.dp else 12.dp),
        modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "GUESS THE MOVIE",
                fontFamily = BenflixTitleFont,
                fontSize = if (isTv) 34.sp else 26.sp,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Round ${round.number}/10  ·  ★ %,d".format(state.score),
                fontSize = 18.sp,
                color = Color.White.copy(alpha = .85f),
            )
        }
        when (round.kind) {
            ClueKind.PICTURE -> {
                // The picture gets sharper every couple of seconds, and sharp right away once you pick
                var step by remember(round.number) { mutableIntStateOf(0) }
                LaunchedEffect(round.number) {
                    while (step < round.pictures.lastIndex) {
                        delay(GuessTheMovieViewModel.STEP_MS)
                        step++
                    }
                }
                val shown = if (state.picked != null) round.pictures.lastIndex else step
                Crossfade(
                    targetState = round.pictures.getOrNull(shown),
                    label = "guess-picture",
                    modifier =
                        Modifier
                            .fillMaxWidth(if (isTv) 0.62f else 1f)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1C1C1F)),
                ) { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            ClueKind.PLOT, ClueKind.CAST -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth(if (isTv) 0.62f else 1f)
                            .aspectRatio(16f / 7f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1C1C1F))
                            .padding(24.dp),
                ) {
                    Text(
                        text = (if (round.kind == ClueKind.PLOT) "“${round.text}”" else round.text),
                        fontSize = if (isTv) 24.sp else 19.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        val firstFocus = remember(round.number) { FocusRequester() }
        LaunchedEffect(round.number) { firstFocus.tryRequestFocus() }
        // Two by two on TV, one per line on phones
        round.choices.chunked(if (isTv) 2 else 1).forEachIndexed { rowIndex, row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(if (isTv) 0.62f else 1f),
            ) {
                row.forEachIndexed { i, choice ->
                    val picked = state.picked
                    val color =
                        when {
                            picked == null -> Color(0xFF2A2A2E)
                            choice == round.answer -> Color(0xFF2E8B57)
                            choice == picked -> BenflixRed
                            else -> Color(0xFF2A2A2E)
                        }
                    Button(
                        onClick = { onPick(choice) },
                        colors =
                            ButtonDefaults.colors(
                                containerColor = color,
                                contentColor = Color.White,
                                focusedContainerColor = if (picked == null) Color.White else color,
                                focusedContentColor = if (picked == null) Color.Black else Color.White,
                            ),
                        modifier =
                            Modifier
                                .weight(1f)
                                .then(if (rowIndex == 0 && i == 0) Modifier.focusRequester(firstFocus) else Modifier),
                    ) {
                        Text(text = choice, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        state.picked?.let { picked ->
            Text(
                text = if (picked == round.answer) "Right! +${state.pointsThisRound}" else "It was ${round.answer}",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (picked == round.answer) Color(0xFF46D37A) else Color.White,
            )
        }
    }
}

@Composable
private fun EndCard(
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
        Text(title, fontFamily = BenflixTitleFont, fontSize = 40.sp, color = Color.White)
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
