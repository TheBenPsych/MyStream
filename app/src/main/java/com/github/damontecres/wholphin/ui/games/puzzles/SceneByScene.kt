package com.github.damontecres.wholphin.ui.games.puzzles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.ui.games.tvPictureHeight
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface SceneState {
    data object Loading : SceneState

    data class NotEnough(
        val message: String,
    ) : SceneState

    data class Playing(
        val answer: PuzzleTitle,
        val scenes: List<String>,
        /** Which scene shows now (0 = the hardest) */
        val shown: Int,
        val choices: List<String>,
        val crossed: Set<String>,
        val points: Int,
        val round: Int,
        val best: Int,
        /** Set once they got it: the points it was worth */
        val won: Int? = null,
    ) : SceneState

    data class Over(
        val points: Int,
        val rounds: Int,
        val best: Int,
        val newBest: Boolean,
        val answer: PuzzleTitle,
    ) : SceneState
}

/**
 * "Scene by scene": guess the movie or show from a still picture taken from it. Each wrong guess
 * shows an easier scene. Endless: running out of scenes ends the run. Board = most points in a run.
 */
@HiltViewModel
class SceneBySceneViewModel
    @Inject
    constructor(
        private val pool: PuzzlePool,
        val actions: PuzzleActions,
    ) : ViewModel() {
        private val _state = MutableStateFlow<SceneState>(SceneState.Loading)
        val state: StateFlow<SceneState> = _state
        private var deck = ArrayDeque<PuzzleTitle>()
        private var titles: List<PuzzleTitle> = emptyList()

        init {
            start()
        }

        fun start() {
            _state.value = SceneState.Loading
            viewModelScope.launch {
                try {
                    titles = pool.titles()
                    val playable = titles.filter { it.stills.size >= 2 }
                    if (playable.size < 8) {
                        _state.value = SceneState.NotEnough("Not enough movies with pictures to play. Try the anime switch on the game's page.")
                        return@launch
                    }
                    deck = ArrayDeque(playable.shuffled())
                    next(0, 0)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Scene by scene couldn't load")
                    _state.value = SceneState.NotEnough("Couldn't load movies from the server. Try again in a moment.")
                }
            }
        }

        private fun next(
            points: Int,
            round: Int,
        ) {
            if (deck.isEmpty()) deck = ArrayDeque(titles.filter { it.stills.size >= 2 }.shuffled())
            val answer = deck.removeFirst()
            val others =
                titles
                    .filter { it.name != answer.name && it.isSeries == answer.isSeries }
                    .ifEmpty { titles.filter { it.name != answer.name } }
                    .shuffled()
                    .take(CHOICES - 1)
                    .map { it.name }
            _state.value =
                SceneState.Playing(
                    answer = answer,
                    scenes = answer.stills.take(POINTS.size),
                    shown = 0,
                    choices = (others + answer.name).shuffled(),
                    crossed = emptySet(),
                    points = points,
                    round = round + 1,
                    best = pool.best(BOARD_KEY),
                )
        }

        fun pick(name: String) {
            val p = _state.value as? SceneState.Playing ?: return
            if (p.won != null) return
            if (name == p.answer.name) {
                val won = POINTS[p.shown]
                _state.value = p.copy(won = won, points = p.points + won)
                viewModelScope.launch {
                    delay(1_400)
                    next(p.points + won, p.round)
                }
            } else if (p.shown + 1 >= p.scenes.size) {
                val newBest = pool.submit(BOARD_KEY, p.points)
                _state.value = SceneState.Over(p.points, p.round - 1, maxOf(p.best, p.points), newBest, p.answer)
            } else {
                _state.value = p.copy(shown = p.shown + 1, crossed = p.crossed + name)
            }
        }

        companion object {
            const val BOARD_KEY = "SCENE_BY_SCENE"
            private const val CHOICES = 6
            private val POINTS = listOf(400, 300, 200, 100)
        }
    }

@Composable
fun SceneBySceneScreen(
    modifier: Modifier = Modifier,
    viewModel: SceneBySceneViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isTv = rememberIsTv()
    PuzzleScreen(isTv, modifier) {
        when (val s = state) {
            SceneState.Loading -> {
                PuzzleMessage("Picking scenes from your library…")
            }

            is SceneState.NotEnough -> {
                PuzzleOver("Scene by scene", s.message, null, viewModel.actions, isTv, viewModel::start)
            }

            is SceneState.Over -> {
                PuzzleOver(
                    title = if (s.newBest) "New best: ${s.points} points!" else "Out of scenes!",
                    body = "It was ${s.answer.label}.\nYou got ${s.rounds} right for ${s.points} points.\nYour best: ${s.best}",
                    answer = s.answer,
                    actions = viewModel.actions,
                    isTv = isTv,
                    onAgain = viewModel::start,
                )
            }

            is SceneState.Playing -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (isTv) 12.dp else 10.dp),
                    modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
                ) {
                    PuzzleHeader("Scene by scene", "Round ${s.round}  ·  ${s.points} points  ·  Your best ${s.best}", isTv)
                    Box(
                        modifier =
                            (if (isTv) Modifier.width(tvPictureHeight(0.46f) * (16f / 9f)) else Modifier.fillMaxWidth())
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PuzzlePanel),
                    ) {
                        AsyncImage(
                            model = s.scenes[s.shown],
                            contentDescription = "A scene",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    // One dot per scene: filled ones are used up
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.scenes.indices.forEach { i ->
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (i <= s.shown) PuzzleGold else Color.White.copy(alpha = .2f)),
                            )
                        }
                    }
                    AnswerLine(
                        text =
                            when {
                                s.won != null -> "Right! ${s.answer.label}  +${s.won}"
                                s.crossed.isNotEmpty() -> "Not that one. Here's an easier scene."
                                else -> "Which movie or show is this? Worth ${listOf(400, 300, 200, 100)[s.shown]}"
                            },
                        good = if (s.won != null) true else if (s.crossed.isNotEmpty()) false else null,
                    )
                    ChoiceGrid(
                        choices = s.choices,
                        isTv = isTv,
                        focusKey = s.answer.id to s.shown,
                        onPick = viewModel::pick,
                        crossed = s.crossed,
                        right = if (s.won != null) s.answer.name else null,
                        modifier = Modifier.padding(horizontal = if (isTv) 40.dp else 0.dp),
                    )
                }
            }
        }
    }
}
