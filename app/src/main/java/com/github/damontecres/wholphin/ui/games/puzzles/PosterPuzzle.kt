package com.github.damontecres.wholphin.ui.games.puzzles

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.ui.games.tvPictureHeight
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface PosterState {
    data object Loading : PosterState

    data class NotEnough(
        val message: String,
    ) : PosterState

    data class Playing(
        val answer: PuzzleTitle,
        /** Tiles (0-15) still covering the poster */
        val covered: Set<Int>,
        val choices: List<String>,
        val points: Int,
        val round: Int,
        val best: Int,
        val won: Int? = null,
    ) : PosterState {
        val worth: Int get() = BASE + covered.size * PER_TILE
    }

    data class Over(
        val points: Int,
        val rounds: Int,
        val best: Int,
        val newBest: Boolean,
        val answer: PuzzleTitle,
        val why: String,
    ) : PosterState
}

private const val TILES = 16
private const val BASE = 100
private const val PER_TILE = 25

/**
 * "Poster puzzle": a poster hidden under 16 tiles that flip away one by one. Guess whenever you
 * know it: the more tiles still covering it, the more points. One wrong guess (or waiting too long
 * once it's all showing) ends the run. Board = most points in a run.
 */
@HiltViewModel
class PosterPuzzleViewModel
    @Inject
    constructor(
        private val pool: PuzzlePool,
        val actions: PuzzleActions,
    ) : ViewModel() {
        private val _state = MutableStateFlow<PosterState>(PosterState.Loading)
        val state: StateFlow<PosterState> = _state
        private var deck = ArrayDeque<PuzzleTitle>()
        private var titles: List<PuzzleTitle> = emptyList()
        private var ticker: Job? = null

        init {
            start()
        }

        fun start() {
            ticker?.cancel()
            _state.value = PosterState.Loading
            viewModelScope.launch {
                try {
                    titles = pool.titles().filter { it.poster != null }
                    if (titles.size < 8) {
                        _state.value = PosterState.NotEnough("Not enough posters to play. Try the anime switch on the game's page.")
                        return@launch
                    }
                    deck = ArrayDeque(titles.shuffled())
                    next(0, 0)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Poster puzzle couldn't load")
                    _state.value = PosterState.NotEnough("Couldn't load movies from the server. Try again in a moment.")
                }
            }
        }

        private fun next(
            points: Int,
            round: Int,
        ) {
            if (deck.isEmpty()) deck = ArrayDeque(titles.shuffled())
            val answer = deck.removeFirst()
            val others =
                titles
                    .filter { it.name != answer.name }
                    .shuffled()
                    .take(5)
                    .map { it.name }
            val playing =
                PosterState.Playing(
                    answer = answer,
                    covered = (0 until TILES).toSet(),
                    choices = (others + answer.name).shuffled(),
                    points = points,
                    round = round + 1,
                    best = pool.best(BOARD_KEY),
                )
            _state.value = playing
            ticker?.cancel()
            ticker =
                viewModelScope.launch {
                    // Tiles flip faster as the run goes on
                    val step = (FLIP_MS - round * 30L).coerceAtLeast(450L)
                    val order = (0 until TILES).shuffled()
                    for (tile in order) {
                        delay(step)
                        val p = _state.value as? PosterState.Playing ?: return@launch
                        if (p.won != null) return@launch
                        _state.value = p.copy(covered = p.covered - tile)
                    }
                    delay(LAST_CHANCE_MS)
                    val p = _state.value as? PosterState.Playing ?: return@launch
                    if (p.won == null) over(p, "Too slow! The whole poster was showing.")
                }
        }

        fun pick(name: String) {
            val p = _state.value as? PosterState.Playing ?: return
            if (p.won != null) return
            if (name == p.answer.name) {
                ticker?.cancel()
                val won = p.worth
                _state.value = p.copy(won = won, points = p.points + won, covered = emptySet())
                viewModelScope.launch {
                    delay(1_400)
                    next(p.points + won, p.round)
                }
            } else {
                over(p, "Not $name.")
            }
        }

        private fun over(
            p: PosterState.Playing,
            why: String,
        ) {
            ticker?.cancel()
            val newBest = pool.submit(BOARD_KEY, p.points)
            _state.value = PosterState.Over(p.points, p.round - 1, maxOf(p.best, p.points), newBest, p.answer, why)
        }

        override fun onCleared() {
            ticker?.cancel()
            super.onCleared()
        }

        companion object {
            const val BOARD_KEY = "POSTER_PUZZLE"
            private const val FLIP_MS = 900L
            private const val LAST_CHANCE_MS = 4_000L
        }
    }

@Composable
fun PosterPuzzleScreen(
    modifier: Modifier = Modifier,
    viewModel: PosterPuzzleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isTv = rememberIsTv()
    PuzzleScreen(isTv, modifier) {
        when (val s = state) {
            PosterState.Loading -> {
                PuzzleMessage("Picking posters from your library…")
            }

            is PosterState.NotEnough -> {
                PuzzleOver("Poster puzzle", s.message, null, viewModel.actions, isTv, viewModel::start)
            }

            is PosterState.Over -> {
                PuzzleOver(
                    title = if (s.newBest) "New best: ${s.points} points!" else "Run over",
                    body = "${s.why} It was ${s.answer.label}.\nYou got ${s.rounds} right for ${s.points} points.\nYour best: ${s.best}",
                    answer = s.answer,
                    actions = viewModel.actions,
                    isTv = isTv,
                    onAgain = viewModel::start,
                )
            }

            is PosterState.Playing -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
                ) {
                    PuzzleHeader("Poster puzzle", "Round ${s.round}  ·  ${s.points} points  ·  Your best ${s.best}", isTv)
                    val posterMod = if (isTv) Modifier.width(tvPictureHeight(0.48f) * (2f / 3f)) else Modifier.width(210.dp)
                    Box(posterMod) {
                        PuzzlePoster(s.answer.poster, "Hidden poster", Modifier.fillMaxWidth())
                        // The tiles on top, 4 by 4
                        Column(Modifier.matchParentSize()) {
                            repeat(4) { r ->
                                Row(Modifier.weight(1f).fillMaxWidth()) {
                                    repeat(4) { c ->
                                        val i = r * 4 + c
                                        val color by animateColorAsState(
                                            if (i in s.covered) Color(if ((r + c) % 2 == 0) 0xFF2B2B33 else 0xFF23232A) else Color.Transparent,
                                            tween(250),
                                            label = "tile",
                                        )
                                        Box(
                                            Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .background(color)
                                                .then(
                                                    if (i in s.covered) Modifier.border(1.dp, Color.Black.copy(alpha = .5f)) else Modifier,
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    AnswerLine(
                        text = if (s.won != null) "Right! ${s.answer.label}  +${s.won}" else "Guess now for ${s.worth} points",
                        good = if (s.won != null) true else null,
                    )
                    ChoiceGrid(
                        choices = s.choices,
                        isTv = isTv,
                        focusKey = s.answer.id,
                        onPick = viewModel::pick,
                        right = if (s.won != null) s.answer.name else null,
                        modifier = if (isTv) Modifier.widthIn(max = 860.dp) else Modifier,
                    )
                }
            }
        }
    }
}

