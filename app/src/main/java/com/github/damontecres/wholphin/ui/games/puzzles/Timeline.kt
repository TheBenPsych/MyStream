package com.github.damontecres.wholphin.ui.games.puzzles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.games.tvPictureHeight
import com.github.damontecres.wholphin.ui.theme.Button
import com.github.damontecres.wholphin.ui.theme.Glowfin
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
        /** The line they built, oldest first */
        val line: List<PuzzleTitle> = emptyList(),
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
                    // Names with a year in them ("Halloween 2018", "Doctor Who (2005)") would give the answer away
                    val titles = pool.titles().filter { it.year != null && it.poster != null && !it.nameHasYear }
                    if (titles.size < 8) {
                        _state.value =
                            TimelineState.NotEnough(notEnough("Not enough movies with release years to play.", pool.mode()))
                        return@launch
                    }
                    deck = ArrayDeque(pool.freshFirst(BOARD_KEY, titles) { it.id })
                    val first = deck.removeFirst()
                    _state.value = TimelineState.Playing(listOf(first), deal(), pool.best(BOARD_KEY))
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Timeline couldn't load")
                    _state.value = TimelineState.NotEnough("Couldn't load movies from Tower. Try again in a moment.")
                }
            }
        }

        private fun deal(): PuzzleTitle = deck.removeFirst().also { pool.saw(BOARD_KEY, it.id) }

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
                    _state.value = TimelineState.Playing(line, deal(), p.best)
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
            _state.value = TimelineState.Over(score, maxOf(p.best, score), newBest, p.card, between, p.line)
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
                PuzzleNotEnough("Timeline", s.message, isTv, viewModel::start)
            }

            is TimelineState.Over -> {
                PuzzleOver(
                    title = if (s.newBest) "New best: ${s.score} in a row!" else "Timeline over: ${s.score}",
                    body = "${s.card.label}. ${s.between}\nYour best line: ${s.best}",
                    answer = s.card,
                    actions = viewModel.actions,
                    isTv = isTv,
                    onAgain = viewModel::start,
                    run = s.line.map { RunPick(it, "${it.year}") },
                    score = s.score,
                    newBest = s.newBest,
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
        PuzzleHeader("Timeline", "${s.score} placed  ·  Best ${s.best}", isTv)
        val cardWidth = if (isTv) tvPictureHeight(0.24f) * (2f / 3f) else 110.dp
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
        // The line (Ben's pick: the new Timeline look): a real line with a tick and year for each placed
        // poster, the posters taking turns above and below it, and a round "+" spot on the line before,
        // between and after every one of them
        val small = if (isTv) tvPictureHeight(0.17f) * (2f / 3f) else 60.dp
        val half = small * 1.5f + 26.dp
        val listState = rememberLazyListState()
        val middle = s.line.size / 2
        val focus = remember(s.card.id) { FocusRequester() }
        LaunchedEffect(s.card.id) {
            listState.scrollToItem((middle * 2 - 2).coerceAtLeast(0))
            focus.tryRequestFocus()
        }
        val lineColor = Color.White.copy(alpha = .28f)
        LazyRow(
            state = listState,
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(half * 2 + 16.dp),
        ) {
            items(s.line.size * 2 + 1) { i ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .height(half * 2)
                            .drawBehind {
                                drawLine(lineColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3.dp.toPx())
                            },
                ) {
                    if (i % 2 == 0) {
                        val slot = i / 2
                        Button(
                            onClick = { onPlace(slot) },
                            shape = ButtonDefaults.shape(CircleShape),
                            colors =
                                ButtonDefaults.colors(
                                    containerColor = Glowfin.Raise,
                                    contentColor = PuzzleAqua,
                                    focusedContainerColor = PuzzleGold,
                                    focusedContentColor = Color.Black,
                                ),
                            contentPadding = PaddingValues(0.dp),
                            modifier =
                                Modifier
                                    .padding(horizontal = 6.dp)
                                    .size(if (isTv) 40.dp else 36.dp)
                                    .then(if (slot == middle) Modifier.focusRequester(focus) else Modifier),
                            scale = ButtonDefaults.scale(focusedScale = 1f),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        val idx = i / 2
                        val t = s.line[idx]
                        val up = idx % 2 == 0
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(small + 8.dp),
                        ) {
                            // Above the line: poster then year; below: year then poster
                            Box(Modifier.height(half), contentAlignment = Alignment.BottomCenter) {
                                if (up) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        PuzzlePoster(t.poster, t.name, Modifier.width(small))
                                        Text("${t.year}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PuzzleGold)
                                    }
                                }
                            }
                            Box(Modifier.height(half), contentAlignment = Alignment.TopCenter) {
                                if (!up) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${t.year}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PuzzleGold)
                                        PuzzlePoster(t.poster, t.name, Modifier.width(small))
                                    }
                                }
                            }
                        }
                        // The tick on the line
                        Box(
                            Modifier
                                .size(width = 3.dp, height = 18.dp)
                                .background(Color.White.copy(alpha = .7f)),
                        )
                    }
                }
            }
        }
    }
}
