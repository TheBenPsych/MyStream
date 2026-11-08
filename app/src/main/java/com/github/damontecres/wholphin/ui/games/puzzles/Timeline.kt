package com.github.damontecres.wholphin.ui.games.puzzles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.games.tvPictureHeight
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface TimelineState {
    data object Loading : TimelineState

    data class NotEnough(
        val message: String,
    ) : TimelineState

    data class Playing(
        /** Placed so far, oldest first */
        val line: List<PuzzleTitle>,
        val card: PuzzleTitle,
        val best: Int,
        /** true right after a right placement (shown for a moment) */
        val justRight: Boolean = false,
    ) : TimelineState {
        val score: Int get() = line.size - 1
    }

    data class Over(
        val score: Int,
        val best: Int,
        val newBest: Boolean,
        val card: PuzzleTitle,
        val between: String,
    ) : TimelineState
}

/**
 * "Timeline": posters arrive one at a time, slot each one into the right place on a growing line of
 * release years. Endless: one wrong spot ends the run. Board = the longest line.
 */
@HiltViewModel
class TimelineViewModel
    @Inject
    constructor(
        private val pool: PuzzlePool,
        val actions: PuzzleActions,
    ) : ViewModel() {
        private val _state = MutableStateFlow<TimelineState>(TimelineState.Loading)
        val state: StateFlow<TimelineState> = _state
        private var deck = ArrayDeque<PuzzleTitle>()

        init {
            start()
        }

        fun start() {
            _state.value = TimelineState.Loading
            viewModelScope.launch {
                try {
                    val titles = pool.titles().filter { it.year != null && it.poster != null }
                    if (titles.size < 8) {
                        _state.value =
                            TimelineState.NotEnough(
                                "Not enough movies with release years to play. Try the anime switch on the game's page.",
                            )
                        return@launch
                    }
                    deck = ArrayDeque(titles.shuffled())
                    val first = deck.removeFirst()
                    _state.value = TimelineState.Playing(listOf(first), deck.removeFirst(), pool.best(BOARD_KEY))
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Timeline couldn't load")
                    _state.value = TimelineState.NotEnough("Couldn't load movies from the server. Try again in a moment.")
                }
            }
        }

        /** Put the card in slot [slot] (0 = before everything, line.size = after everything) */
        fun place(slot: Int) {
            val p = _state.value as? TimelineState.Playing ?: return
            if (p.justRight) return
            val year = p.card.year ?: return
            val before = p.line.getOrNull(slot - 1)?.year ?: Int.MIN_VALUE
            val after = p.line.getOrNull(slot)?.year ?: Int.MAX_VALUE
            // Same year as a neighbour counts as right on either side
            if (year in before..after) {
                val line = p.line.toMutableList().apply { add(slot, p.card) }
                if (deck.isEmpty()) {
                    // Everything placed: a perfect run
                    finish(p.copy(line = line), "You placed every title!")
                    return
                }
                _state.value = p.copy(line = line, justRight = true)
                viewModelScope.launch {
                    delay(900)
                    _state.value = TimelineState.Playing(line, deck.removeFirst(), p.best)
                }
            } else {
                val right = p.line.indexOfFirst { (it.year ?: 0) > year }.let { if (it < 0) p.line.size else it }
                val a = p.line.getOrNull(right - 1)
                val b = p.line.getOrNull(right)
                val between =
                    when {
                        a == null -> "before ${b?.label}"
                        b == null -> "after ${a.label}"
                        else -> "between ${a.label} and ${b.label}"
                    }
                finish(p, "It goes $between.")
            }
        }

        private fun finish(
            p: TimelineState.Playing,
            between: String,
        ) {
            val score = p.line.size - 1
            val newBest = pool.submit(BOARD_KEY, score)
            _state.value = TimelineState.Over(score, maxOf(p.best, score), newBest, p.card, between)
        }

        companion object {
            const val BOARD_KEY = "TIMELINE"
        }
    }

@Composable
fun TimelineScreen(
    modifier: Modifier = Modifier,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isTv = rememberIsTv()
    PuzzleScreen(isTv, modifier) {
        when (val s = state) {
            TimelineState.Loading -> {
                PuzzleMessage("Picking movies from your library…")
            }

            is TimelineState.NotEnough -> {
                PuzzleOver("Timeline", s.message, null, viewModel.actions, isTv, viewModel::start)
            }

            is TimelineState.Over -> {
                PuzzleOver(
                    title = if (s.newBest) "New best: ${s.score} in a row!" else "Timeline over: ${s.score}",
                    body = "${s.card.label}. ${s.between}\nYour best line: ${s.best}",
                    answer = s.card,
                    actions = viewModel.actions,
                    isTv = isTv,
                    onAgain = viewModel::start,
                )
            }

            is TimelineState.Playing -> {
                TimelinePlay(s, isTv, viewModel::place)
            }
        }
    }
}

@Composable
private fun TimelinePlay(
    s: TimelineState.Playing,
    isTv: Boolean,
    onPlace: (Int) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        PuzzleHeader("Timeline", "${s.score} placed  ·  Your best ${s.best}", isTv)
        val cardWidth = if (isTv) tvPictureHeight(0.30f) * (2f / 3f) else 120.dp
        PuzzlePoster(s.card.poster, s.card.name, Modifier.width(cardWidth))
        Text(
            s.card.name,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AnswerLine(
            text = if (s.justRight) "Right! ${s.card.label}" else "Where does it go? Older on the left, newer on the right.",
            good = if (s.justRight) true else null,
        )
        // The line: a "Here" button before, between and after every placed poster
        val small = if (isTv) tvPictureHeight(0.20f) * (2f / 3f) else 72.dp
        val listState = rememberLazyListState()
        val middle = s.line.size / 2
        val focus = remember(s.card.id) { FocusRequester() }
        LaunchedEffect(s.card.id) {
            listState.scrollToItem((middle * 2 - 2).coerceAtLeast(0))
            focus.tryRequestFocus()
        }
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(s.line.size * 2 + 1) { i ->
                if (i % 2 == 0) {
                    val slot = i / 2
                    Button(
                        onClick = { onPlace(slot) },
                        colors =
                            ButtonDefaults.colors(
                                containerColor = Color(0xFF2A2A2E),
                                contentColor = Color.White,
                                focusedContainerColor = PuzzleGold,
                                focusedContentColor = Color.Black,
                            ),
                        modifier =
                            Modifier
                                .height(small * 1.5f)
                                .then(if (slot == middle) Modifier.focusRequester(focus) else Modifier),
                        scale = ButtonDefaults.scale(focusedScale = 1f),
                    ) {
                        Text("Here", textAlign = TextAlign.Center)
                    }
                } else {
                    val t = s.line[i / 2]
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PuzzlePoster(t.poster, t.name, Modifier.width(small))
                        Text("${t.year}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PuzzleGold)
                    }
                }
            }
        }
    }
}
