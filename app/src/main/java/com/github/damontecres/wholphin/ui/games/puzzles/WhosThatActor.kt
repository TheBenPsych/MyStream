package com.github.damontecres.wholphin.ui.games.puzzles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.ui.games.tvPictureHeight
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
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

/** An actor with a photo and the titles on Tower they're in */
data class KnownActor(
    val person: PuzzlePerson,
    val titles: List<PuzzleTitle>,
)

sealed interface ActorState {
    data object Loading : ActorState

    data class NotEnough(
        val message: String,
    ) : ActorState

    data class Playing(
        val actor: KnownActor,
        val choices: List<String>,
        val crossed: Set<String>,
        /** 0 = zoomed right in, 1 = the whole photo */
        val reveal: Float,
        val hints: List<String>,
        val points: Int,
        val round: Int,
        val best: Int,
        val won: Int? = null,
        /** Wrong names plus times the clock ran out */
        val misses: Int = 0,
        /** How long the whole photo has been showing (ms) */
        val outFor: Long = 0,
        /** The clock ran out once and used up a guess */
        val timedOut: Boolean = false,
    ) : ActorState {
        val worth: Int get() = (100 + 400 * (1f - reveal)).toInt()
        val guessesLeft: Int get() = (MAX_MISSES - misses).coerceAtLeast(0)
        val secondsLeft: Int? get() = if (reveal >= 1f) ((LAST_CHANCE_MS - outFor + 999) / 1000).toInt().coerceAtLeast(0) else null
    }

    data class Over(
        val points: Int,
        val rounds: Int,
        val best: Int,
        val newBest: Boolean,
        val actor: KnownActor,
        val why: String,
    ) : ActorState
}

/**
 * "Who's that actor?": an actor's photo zoomed right in on their eyes slowly zooms out. Each miss
 * zooms out a little and adds a hint (a title of theirs on Tower). Once the whole photo shows there
 * is a visible countdown; running out uses up a guess and starts a fresh countdown, and every wrong
 * name restarts it too, so the "guesses left" are always real. Three misses ends the run.
 * Board = most points in a run.
 */
@HiltViewModel
class WhosThatActorViewModel
    @Inject
    constructor(
        private val pool: PuzzlePool,
        val actions: PuzzleActions,
    ) : ViewModel() {
        private val _state = MutableStateFlow<ActorState>(ActorState.Loading)
        val state: StateFlow<ActorState> = _state
        private var actors: List<KnownActor> = emptyList()
        private var deck = ArrayDeque<KnownActor>()
        private var ticker: Job? = null

        init {
            start()
        }

        fun start() {
            ticker?.cancel()
            _state.value = ActorState.Loading
            viewModelScope.launch {
                try {
                    val titles = pool.titles()
                    actors =
                        titles
                            .flatMap { t -> t.actors.filter { it.photo != null }.map { it to t } }
                            .groupBy { it.first.id }
                            .map { (_, list) -> KnownActor(list.first().first, list.map { it.second }) }
                    // Prefer people in two or more titles: they're the ones you'll know
                    val known = actors.filter { it.titles.size >= 2 }.ifEmpty { actors }
                    if (known.size < 6) {
                        _state.value = ActorState.NotEnough("Not enough actor photos on Tower to play.")
                        return@launch
                    }
                    deck = ArrayDeque(pool.freshFirst(BOARD_KEY, known) { it.person.id })
                    next(0, 0)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Who's that actor couldn't load")
                    _state.value = ActorState.NotEnough("Couldn't load from Tower. Try again in a moment.")
                }
            }
        }

        private fun next(
            points: Int,
            round: Int,
        ) {
            if (deck.isEmpty()) {
                deck =
                    ArrayDeque(pool.freshFirst(BOARD_KEY, actors.filter { it.titles.size >= 2 }.ifEmpty { actors }) { it.person.id })
            }
            val actor = deck.removeFirst()
            pool.saw(BOARD_KEY, actor.person.id)
            val others =
                actors
                    .filter { it.person.name != actor.person.name }
                    .shuffled()
                    .take(5)
                    .map { it.person.name }
            _state.value =
                ActorState.Playing(
                    actor = actor,
                    choices = (others + actor.person.name).shuffled(),
                    crossed = emptySet(),
                    reveal = 0f,
                    hints = emptyList(),
                    points = points,
                    round = round + 1,
                    best = pool.best(BOARD_KEY),
                )
            ticker?.cancel()
            ticker =
                viewModelScope.launch {
                    while (true) {
                        delay(TICK_MS)
                        val p = _state.value as? ActorState.Playing ?: return@launch
                        if (p.won != null) return@launch
                        if (p.reveal >= 1f) {
                            val outFor = p.outFor + TICK_MS
                            if (outFor < LAST_CHANCE_MS) {
                                _state.value = p.copy(outFor = outFor)
                            } else if (p.misses + 1 >= MAX_MISSES) {
                                over(p, "Out of time!")
                                return@launch
                            } else {
                                // Time ran out: it costs one guess, not the whole run
                                _state.value = p.withHint().copy(misses = p.misses + 1, outFor = 0, timedOut = true)
                            }
                        } else {
                            _state.value = p.copy(reveal = (p.reveal + TICK_MS.toFloat() / ZOOM_MS).coerceAtMost(1f))
                        }
                    }
                }
        }

        fun pick(name: String) {
            val p = _state.value as? ActorState.Playing ?: return
            if (p.won != null) return
            if (name == p.actor.person.name) {
                ticker?.cancel()
                val won = p.worth
                _state.value = p.copy(won = won, points = p.points + won, reveal = 1f)
                viewModelScope.launch {
                    delay(1_400)
                    next(p.points + won, p.round)
                }
            } else if (name in p.crossed) {
                return
            } else if (p.misses + 1 >= MAX_MISSES) {
                over(p, "Not $name.")
            } else {
                // A wrong name zooms out a little, adds a hint and gives a full, fresh countdown
                _state.value =
                    p.withHint().copy(
                        crossed = p.crossed + name,
                        misses = p.misses + 1,
                        reveal = (p.reveal + MISS_ZOOM).coerceAtMost(1f),
                        outFor = 0,
                        timedOut = false,
                    )
            }
        }

        private fun ActorState.Playing.withHint(): ActorState.Playing {
            val titles = actor.titles.map { it.label }
            val hint = titles.getOrNull(hints.size)?.let { "Was in $it" } ?: "Has ${titles.size} titles on Tower"
            return if (hint in hints) this else copy(hints = hints + hint)
        }

        private fun over(
            p: ActorState.Playing,
            why: String,
        ) {
            ticker?.cancel()
            val newBest = pool.submit(BOARD_KEY, p.points)
            _state.value = ActorState.Over(p.points, p.round - 1, maxOf(p.best, p.points), newBest, p.actor, why)
        }

        override fun onCleared() {
            ticker?.cancel()
            super.onCleared()
        }

        companion object {
            const val BOARD_KEY = "WHOS_THAT_ACTOR"
            private const val TICK_MS = 100L
            private const val ZOOM_MS = 15_000f
            private const val MISS_ZOOM = 0.12f
        }
    }

/** Time to answer once the whole photo shows, fresh after every miss */
internal const val LAST_CHANCE_MS = 10_000L
internal const val MAX_MISSES = 3

@Composable
fun WhosThatActorScreen(
    modifier: Modifier = Modifier,
    viewModel: WhosThatActorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isTv = rememberIsTv()
    PuzzleScreen(isTv, modifier) {
        when (val s = state) {
            ActorState.Loading -> {
                PuzzleMessage("Finding actors in your library…")
            }

            is ActorState.NotEnough -> {
                PuzzleNotEnough("Who's that actor?", s.message, isTv, viewModel::start)
            }

            is ActorState.Over -> {
                PuzzleOver(
                    title = if (s.newBest) "New best: ${s.points} points!" else "Run over",
                    body =
                        "${s.why} It was ${s.actor.person.name}" +
                            (
                                s.actor.titles
                                    .firstOrNull()
                                    ?.let { ", from ${it.label}" } ?: ""
                            ) +
                            ".\nYou got ${s.rounds} right for ${s.points} points.\nYour best: ${s.best}",
                    answer = s.actor.titles.firstOrNull(),
                    actions = viewModel.actions,
                    isTv = isTv,
                    onAgain = viewModel::start,
                    score = s.points,
                    newBest = s.newBest,
                )
            }

            is ActorState.Playing -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
                ) {
                    PuzzleHeader(
                        "Who's that actor?",
                        "Round ${s.round}  ·  ${s.points} points  ·  Best ${s.best}",
                        isTv,
                        guessesLeft = s.guessesLeft,
                        guessesOf = MAX_MISSES,
                    )
                    val w = if (isTv) tvPictureHeight(0.42f) * (2f / 3f) else 190.dp
                    Box(
                        Modifier
                            .width(w)
                            .aspectRatio(2f / 3f)
                            // The ring empties as the photo zooms out, then counts down the last seconds
                            .timerRing(
                                if (s.secondsLeft != null) {
                                    1f - s.outFor.toFloat() / LAST_CHANCE_MS
                                } else {
                                    1f - s.reveal
                                },
                            ).padding(7.dp)
                            .clip(BenflixShapes.Card)
                            .background(PuzzlePanel),
                    ) {
                        val zoom = 1f + 3.5f * (1f - s.reveal)
                        AsyncImage(
                            model = s.actor.person.photo,
                            contentDescription = "Who is this?",
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = zoom
                                        scaleY = zoom
                                        // Zoom in where the eyes usually are
                                        transformOrigin = TransformOrigin(0.5f, 0.3f)
                                    },
                        )
                    }
                    HintChips(s.hints)
                    AnswerLine(
                        text =
                            when {
                                s.won != null -> "Right! ${s.actor.person.name}  +${s.won}"
                                else -> actorPrompt(s)
                            },
                        good =
                            if (s.won != null) {
                                true
                            } else if (s.misses > 0) {
                                false
                            } else {
                                null
                            },
                    )
                    ChoiceGrid(
                        choices = s.choices,
                        isTv = isTv,
                        focusKey = s.actor.person.id to s.misses,
                        onPick = viewModel::pick,
                        crossed = s.crossed,
                        right = if (s.won != null) s.actor.person.name else null,
                        modifier = if (isTv) Modifier.widthIn(max = 860.dp) else Modifier,
                    )
                }
            }
        }
    }
}

private fun actorPrompt(s: ActorState.Playing): String {
    val left = s.guessesLeft
    val guesses = if (left == 1) "1 guess left" else "$left guesses left"
    val clock = s.secondsLeft?.let { "  ·  ${it}s to answer" } ?: ""
    return when {
        s.timedOut -> "Time ran out, that used a guess. $guesses$clock"
        s.misses > 0 -> "Not them. $guesses$clock"
        else -> "Who is this? Guess now for ${s.worth} points$clock"
    }
}
