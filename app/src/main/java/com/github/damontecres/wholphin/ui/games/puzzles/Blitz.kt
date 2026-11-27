package com.github.damontecres.wholphin.ui.games.puzzles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.games.tvPictureHeight
import com.github.damontecres.wholphin.ui.theme.Button
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** One true-or-false line about a title */
data class BlitzFact(
    val title: PuzzleTitle,
    val text: String,
    val isTrue: Boolean,
)

sealed interface BlitzState {
    data object Loading : BlitzState

    data class NotEnough(
        val message: String,
    ) : BlitzState

    data class Playing(
        val fact: BlitzFact,
        val right: Int,
        val wrong: Int,
        val msLeft: Long,
        val best: Int,
        /** null until answered; then whether they were right (shown for a moment) */
        val was: Boolean? = null,
    ) : BlitzState

    data class Over(
        val right: Int,
        val wrong: Int,
        val best: Int,
        val newBest: Boolean,
        val run: List<RunPick>,
    ) : BlitzState
}

/**
 * "60-second blitz" (Ben's pick 2026-11-22): true or false lines about what's on Tower, as many as you
 * can in 60 seconds. A wrong answer costs 3 seconds. Board = most right in one blitz.
 */
@HiltViewModel
class BlitzViewModel
    @Inject
    constructor(
        private val pool: PuzzlePool,
        val actions: PuzzleActions,
    ) : ViewModel() {
        private val _state = MutableStateFlow<BlitzState>(BlitzState.Loading)
        val state: StateFlow<BlitzState> = _state
        private var titles: List<PuzzleTitle> = emptyList()
        private var deck = ArrayDeque<PuzzleTitle>()
        private var clock: Job? = null
        private var endsAt = 0L
        private val picks = mutableListOf<RunPick>()

        init {
            start()
        }

        fun start() {
            clock?.cancel()
            picks.clear()
            _state.value = BlitzState.Loading
            viewModelScope.launch {
                try {
                    titles = pool.titles().filter { it.year != null }
                    if (titles.size < 12) {
                        _state.value = BlitzState.NotEnough(notEnough("Not enough movies to play.", pool.mode()))
                        return@launch
                    }
                    deck = ArrayDeque(pool.freshFirst(BOARD_KEY, titles) { it.id })
                    endsAt = System.currentTimeMillis() + TOTAL_MS
                    _state.value = BlitzState.Playing(fact(), 0, 0, TOTAL_MS, pool.best(BOARD_KEY))
                    clock =
                        viewModelScope.launch {
                            while (true) {
                                delay(100)
                                val p = _state.value as? BlitzState.Playing ?: return@launch
                                val left = endsAt - System.currentTimeMillis()
                                if (left <= 0) {
                                    finish(p)
                                    return@launch
                                }
                                _state.value = p.copy(msLeft = left)
                            }
                        }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Blitz couldn't load")
                    _state.value = BlitzState.NotEnough("Couldn't load movies from Tower. Try again in a moment.")
                }
            }
        }

        private fun nextTitle(): PuzzleTitle {
            if (deck.isEmpty()) deck = ArrayDeque(pool.freshFirst(BOARD_KEY, titles) { it.id })
            return deck.removeFirst().also { pool.saw(BOARD_KEY, it.id) }
        }

        /** A line that's true half the time; the false ones are close enough to make you think */
        private fun fact(): BlitzFact {
            repeat(6) {
                val t = nextTitle()
                val yes = Math.random() < .5
                val kinds = mutableListOf("year")
                if (t.actors.isNotEmpty()) kinds += "actor"
                if (t.rating > 0) kinds += "rating"
                if (!t.isSeries && (t.runtimeMin ?: 0) > 0) kinds += "length"
                if (t.genres.isNotEmpty()) kinds += "genre"
                val made =
                    when (kinds.random()) {
                        "actor" -> {
                            val other =
                                titles
                                    .filter { it.id != t.id && it.genres.any { g -> g in t.genres } }
                                    .flatMap { it.actors.take(5) }
                                    .map { it.name }
                                    .filter { n -> t.actors.none { it.name == n } }
                                    .randomOrNull()
                            val name =
                                if (yes || other == null) {
                                    t.actors
                                        .take(5)
                                        .random()
                                        .name
                                } else {
                                    other
                                }
                            BlitzFact(t, "$name is in ${t.name}", yes || other == null)
                        }

                        "rating" -> {
                            val line = listOf(60, 65, 70, 75, 80).random()
                            BlitzFact(t, "${t.name} is rated ${line / 10}.${line % 10} or more on IMDb", t.rating >= line)
                        }

                        "length" -> {
                            val mins = t.runtimeMin ?: 0
                            BlitzFact(t, "${t.name} is longer than 2 hours", mins > 120)
                        }

                        "genre" -> {
                            val g =
                                if (yes) {
                                    t.genres.random()
                                } else {
                                    titles
                                        .flatMap { it.genres }
                                        .distinct()
                                        .filter { it !in t.genres }
                                        .randomOrNull() ?: t.genres.random()
                                }
                            BlitzFact(
                                t,
                                "${t.name} is ${article(g)} ${g.lowercase()} ${if (t.isSeries) "show" else "movie"}",
                                g in t.genres,
                            )
                        }

                        else -> {
                            val y = t.year ?: 2000
                            val shown = if (yes) y else y + listOf(-3, -2, -1, 1, 2, 3).random()
                            BlitzFact(t, "${t.name} came out in $shown", shown == y)
                        }
                    }
                return made
            }
            val t = nextTitle()
            return BlitzFact(t, "${t.name} came out in ${t.year}", true)
        }

        private fun article(word: String) = if (word.firstOrNull()?.lowercaseChar() in setOf('a', 'e', 'i', 'o', 'u')) "an" else "a"

        fun answer(saysTrue: Boolean) {
            val p = _state.value as? BlitzState.Playing ?: return
            if (p.was != null) return
            val right = saysTrue == p.fact.isTrue
            if (!right) endsAt -= WRONG_COST_MS
            if (right) picks += RunPick(p.fact.title, "✓")
            val now = p.copy(right = p.right + if (right) 1 else 0, wrong = p.wrong + if (right) 0 else 1, was = right)
            _state.value = now
            viewModelScope.launch {
                delay(if (right) 350 else 900)
                val q = _state.value as? BlitzState.Playing ?: return@launch
                _state.value = q.copy(fact = fact(), was = null)
            }
        }

        private fun finish(p: BlitzState.Playing) {
            clock?.cancel()
            val newBest = pool.submit(BOARD_KEY, p.right)
            _state.value = BlitzState.Over(p.right, p.wrong, maxOf(p.best, p.right), newBest, picks.toList())
        }

        override fun onCleared() {
            clock?.cancel()
            super.onCleared()
        }

        companion object {
            const val BOARD_KEY = "BLITZ"
            private const val TOTAL_MS = 60_000L
            private const val WRONG_COST_MS = 3_000L
        }
    }

@Composable
fun BlitzScreen(
    modifier: Modifier = Modifier,
    viewModel: BlitzViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isTv = rememberIsTv()
    PuzzleScreen(isTv, modifier) {
        when (val s = state) {
            BlitzState.Loading -> {
                PuzzleMessage("Picking movies from your library…")
            }

            is BlitzState.NotEnough -> {
                PuzzleNotEnough("60-second blitz", s.message, isTv, viewModel::start)
            }

            is BlitzState.Over -> {
                PuzzleOver(
                    title = if (s.newBest) "New best: ${s.right} right!" else "Time's up!",
                    body = "${s.right} right and ${s.wrong} wrong in 60 seconds.\nYour best: ${s.best}",
                    answer = null,
                    actions = viewModel.actions,
                    isTv = isTv,
                    onAgain = viewModel::start,
                    run = s.run,
                    score = s.right,
                    newBest = s.newBest,
                )
            }

            is BlitzState.Playing -> {
                BlitzPlay(s, isTv, viewModel::answer)
            }
        }
    }
}

@Composable
private fun BlitzPlay(
    s: BlitzState.Playing,
    isTv: Boolean,
    onAnswer: (Boolean) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
    ) {
        PuzzleHeader("60-second blitz", "${(s.msLeft + 999) / 1000}s left  ·  ${s.right} right  ·  Best ${s.best}", isTv)
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = .1f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((s.msLeft / 60_000f).coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(if (s.msLeft < 10_000) Color(0xFFFF7A7A) else PuzzleAqua),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            PuzzlePoster(s.fact.title.poster, s.fact.title.name, Modifier.width(if (isTv) tvPictureHeight(0.36f) * (2f / 3f) else 110.dp))
            Text(
                s.fact.text,
                fontSize = if (isTv) 28.sp else 21.sp,
                lineHeight = if (isTv) 36.sp else 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        AnswerLine(
            text =
                when (s.was) {
                    true -> "Right!"
                    false -> if (s.fact.isTrue) "It was true. −3 seconds" else "It was false. −3 seconds"
                    null -> "True or false?"
                },
            good = s.was,
        )
        val first = remember(s.fact) { FocusRequester() }
        LaunchedEffect(s.fact) { first.tryRequestFocus() }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            listOf(true to "✓ True", false to "✗ False").forEachIndexed { i, (yes, label) ->
                Button(
                    onClick = { onAnswer(yes) },
                    colors =
                        ButtonDefaults.colors(
                            containerColor = if (yes) Color(0xFF14532D) else Color(0xFF7F1D1D),
                            contentColor = Color.White,
                            focusedContainerColor = PuzzleGold,
                            focusedContentColor = Color.Black,
                        ),
                    scale = ButtonDefaults.scale(focusedScale = 1f),
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(if (isTv) 56.dp else 50.dp)
                            .then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                ) {
                    Text(
                        label,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
