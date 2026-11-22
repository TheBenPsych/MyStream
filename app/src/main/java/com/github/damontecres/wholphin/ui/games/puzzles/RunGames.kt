package com.github.damontecres.wholphin.ui.games.puzzles

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
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
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.services.GameLibraryClient
import com.github.damontecres.wholphin.ui.games.tvPictureHeight
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/*
 * Ben's five new one-player movie games (picked 2026-11-22): Quote it, Close-up, Who played them?,
 * Which season? and 60-second blitz. The first four share one engine: a round has a clock, answering
 * early is worth more (100 to 500), and one wrong answer or running out of time ends the run.
 */

/** One round of a run game */
data class RunRound(
    val title: PuzzleTitle,
    val choices: List<String>,
    val answer: String,
    /** The question, like "Who played Ellen Ripley?" */
    val ask: String,
    /** Pictures for the round: a scene, a photo per choice, and so on */
    val pictures: List<String> = emptyList(),
    /** Lines of text for the round (Quote it) */
    val lines: List<String> = emptyList(),
    /** Used when Tower can't take a picture */
    val fallback: String? = null,
)

sealed interface RunState {
    data object Loading : RunState

    data class NotEnough(
        val message: String,
    ) : RunState

    data class Playing(
        val r: RunRound,
        val points: Int,
        val round: Int,
        val best: Int,
        /** Time left in the round, 1 = all of it */
        val left: Float = 1f,
        val won: Int? = null,
        /** The picture failed, use the fallback */
        val broken: Boolean = false,
    ) : RunState {
        val worth: Int get() = (100 + 400 * left).toInt()
    }

    data class Over(
        val points: Int,
        val rounds: Int,
        val best: Int,
        val newBest: Boolean,
        val why: String,
        val answer: PuzzleTitle,
        val run: List<RunPick>,
        /** The right answer, as words */
        val said: String,
    ) : RunState
}

/** The shared engine for the run games */
abstract class RunGameViewModel(
    protected val pool: PuzzlePool,
    val actions: PuzzleActions,
) : ViewModel() {
    abstract val boardKey: String

    /** How long a round lasts */
    open val roundMs: Long = 15_000L

    /** Loads what the game needs; returns why it can't play, or null when it can */
    protected abstract suspend fun setup(): String?

    /** The next round, or null when it can't make one */
    protected abstract suspend fun nextRound(): RunRound?

    private val _state = MutableStateFlow<RunState>(RunState.Loading)
    val state: StateFlow<RunState> = _state
    private var ticker: Job? = null
    private val picks = mutableListOf<RunPick>()

    fun start() {
        ticker?.cancel()
        picks.clear()
        _state.value = RunState.Loading
        viewModelScope.launch {
            try {
                val why = setup()
                if (why != null) {
                    _state.value = RunState.NotEnough(why)
                    return@launch
                }
                next(0, 0)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "$boardKey couldn't load")
                _state.value = RunState.NotEnough("Couldn't load from Tower. Try again in a moment.")
            }
        }
    }

    private suspend fun next(
        points: Int,
        round: Int,
    ) {
        val r =
            nextRound() ?: run {
                _state.value = RunState.NotEnough("Couldn't find more to ask from what's on Tower. Try again later.")
                return
            }
        pool.saw(boardKey, r.title.id)
        val playing = RunState.Playing(r, points, round + 1, pool.best(boardKey))
        _state.value = playing
        ticker?.cancel()
        ticker =
            viewModelScope.launch {
                val started = System.currentTimeMillis()
                while (true) {
                    delay(100)
                    val p = _state.value as? RunState.Playing ?: return@launch
                    if (p.won != null || p.r !== r) return@launch
                    val left = 1f - (System.currentTimeMillis() - started).toFloat() / roundMs
                    if (left <= 0f) {
                        over(p, "Out of time!")
                        return@launch
                    }
                    _state.value = p.copy(left = left)
                }
            }
    }

    fun pick(name: String) {
        val p = _state.value as? RunState.Playing ?: return
        if (p.won != null) return
        if (name == p.r.answer) {
            ticker?.cancel()
            val won = p.worth
            picks += RunPick(p.r.title, "+$won")
            _state.value = p.copy(won = won, points = p.points + won)
            viewModelScope.launch {
                delay(1_400)
                try {
                    next(p.points + won, p.round)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "$boardKey next round failed")
                    over(p.copy(points = p.points + won), "Tower stopped answering.")
                }
            }
        } else {
            over(p, "Not $name.")
        }
    }

    fun pictureFailed() {
        val p = _state.value as? RunState.Playing ?: return
        if (!p.broken) _state.value = p.copy(broken = true)
    }

    private fun over(
        p: RunState.Playing,
        why: String,
    ) {
        ticker?.cancel()
        val newBest = pool.submit(boardKey, p.points)
        _state.value =
            RunState.Over(
                p.points,
                p.round - 1,
                maxOf(p.best, p.points),
                newBest,
                why,
                p.r.title,
                picks.toList(),
                if (p.r.answer ==
                    p.r.title.name
                ) {
                    p.r.title.label
                } else {
                    p.r.answer
                },
            )
    }

    override fun onCleared() {
        ticker?.cancel()
        super.onCleared()
    }

    /** Six title names: the answer and five look-alikes */
    protected fun titleChoices(
        answer: PuzzleTitle,
        from: List<PuzzleTitle>,
        n: Int = 5,
    ): List<String> =
        (
            lookAlikes(answer, from, n, { it.name }, { it.genres }, { it.year }, { it.isSeries }, { it.anime })
                .map { it.name } + answer.name
        ).distinct().shuffled()
}

/** Asks Tower once whether it can take pictures and read subtitles (benflix_scenes.py) */
private suspend fun towerCan(
    helper: GameLibraryClient,
    what: String,
): Boolean =
    withTimeoutOrNull(4_000) {
        try {
            helper.getJson("/api/scenes/ok").let { it.optBoolean("ok") && (what == "ok" || it.optBoolean(what)) }
        } catch (ex: CancellationException) {
            throw ex
        } catch (_: Exception) {
            false
        }
    } == true

private fun sceneUrl(
    helper: GameLibraryClient,
    t: PuzzleTitle,
    file: String,
) = "${helper.address}/api/scenes/${t.id.toString().replace("-", "")}/$file?api_key=${Uri.encode(helper.token)}"

/** Hides the title's name inside a line ("I am ▇▇▇▇") */
internal fun hideName(
    name: String,
    text: String,
): String {
    var out = text
    val parts = listOf(name, name.substringBefore(":").trim()).filter { it.length > 3 }.distinct()
    parts.forEach { out = out.replace(it, "▇▇▇▇", ignoreCase = true) }
    return out
}

// ---------------- Quote it ----------------

/** "Quote it": three lines people say in a movie, from its subtitles. Name the movie. */
@HiltViewModel
class QuoteItViewModel
    @Inject
    constructor(
        pool: PuzzlePool,
        actions: PuzzleActions,
        private val helper: GameLibraryClient,
    ) : RunGameViewModel(pool, actions) {
        override val boardKey = BOARD_KEY
        override val roundMs = 20_000L
        private var titles: List<PuzzleTitle> = emptyList()
        private var deck = ArrayDeque<PuzzleTitle>()

        init {
            start()
        }

        override suspend fun setup(): String? {
            if (!towerCan(helper, "quotes")) return "Quote it reads subtitles on Tower. It arrives with Tower's next hourly update."
            titles = pool.titles().filter { !it.isSeries }
            if (titles.size < 8) return notEnough("Not enough movies to play.", pool.mode())
            deck = ArrayDeque(pool.freshFirst(BOARD_KEY, titles) { it.id })
            return null
        }

        override suspend fun nextRound(): RunRound? {
            repeat(8) {
                if (deck.isEmpty()) deck = ArrayDeque(pool.freshFirst(BOARD_KEY, titles) { it.id })
                val t = deck.removeFirst()
                val lines =
                    try {
                        val arr = helper.getJson("/api/scenes/${t.id.toString().replace("-", "")}/quotes").optJSONArray("lines")
                        (0 until (arr?.length() ?: 0)).map { arr!!.getString(it) }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (_: Exception) {
                        emptyList()
                    }
                if (lines.size >= 3) {
                    // Three lines from across the movie, in order, with the name hidden
                    val pick =
                        lines.indices
                            .shuffled()
                            .take(3)
                            .sorted()
                            .map { "“${hideName(t.name, lines[it])}”" }
                    return RunRound(t, titleChoices(t, titles), t.name, "Which movie says this?", lines = pick)
                }
                // No English text subtitles: don't pick it again soon
                pool.saw(BOARD_KEY, t.id)
            }
            return null
        }

        companion object {
            const val BOARD_KEY = "QUOTE_IT"
        }
    }

// ---------------- Close-up ----------------

/** "Close-up": a real scene starts zoomed right in and slowly zooms out. Name the movie or show. */
@HiltViewModel
class CloseUpViewModel
    @Inject
    constructor(
        pool: PuzzlePool,
        actions: PuzzleActions,
        private val helper: GameLibraryClient,
    ) : RunGameViewModel(pool, actions) {
        override val boardKey = BOARD_KEY
        private var titles: List<PuzzleTitle> = emptyList()
        private var deck = ArrayDeque<PuzzleTitle>()
        private var real = false

        init {
            start()
        }

        override suspend fun setup(): String? {
            real = towerCan(helper, "ok")
            titles = pool.titles().filter { real || it.stills.isNotEmpty() }
            if (titles.size < 8) return notEnough("Not enough movies with pictures to play.", pool.mode())
            deck = ArrayDeque(pool.freshFirst(BOARD_KEY, titles) { it.id })
            return null
        }

        override suspend fun nextRound(): RunRound? {
            if (deck.isEmpty()) deck = ArrayDeque(pool.freshFirst(BOARD_KEY, titles) { it.id })
            val t = deck.removeFirst()
            val pic = if (real) sceneUrl(helper, t, "${(0 until 6).random()}.jpg") else t.stills.first()
            return RunRound(
                t,
                titleChoices(t, titles),
                t.name,
                "What's this close-up from?",
                listOf(pic),
                fallback = t.stills.firstOrNull(),
            )
        }

        companion object {
            const val BOARD_KEY = "CLOSE_UP"
        }
    }

// ---------------- Who played them? ----------------

/** "Who played them?": a character's name and the movie. Pick who played them from 4 actor photos. */
@HiltViewModel
class WhoPlayedViewModel
    @Inject
    constructor(
        pool: PuzzlePool,
        actions: PuzzleActions,
    ) : RunGameViewModel(pool, actions) {
        override val boardKey = BOARD_KEY
        private var titles: List<PuzzleTitle> = emptyList()
        private var deck = ArrayDeque<PuzzleTitle>()

        init {
            start()
        }

        private fun roles(t: PuzzleTitle) = t.actors.filter { it.photo != null && cleanRole(it.role) != null }

        override suspend fun setup(): String? {
            titles = pool.titles().filter { roles(it).isNotEmpty() && it.actors.count { a -> a.photo != null } >= 2 }
            if (titles.size < 6) return notEnough("Not enough movies with character names to play.", pool.mode())
            deck = ArrayDeque(pool.freshFirst(BOARD_KEY, titles) { it.id })
            return null
        }

        override suspend fun nextRound(): RunRound? {
            if (deck.isEmpty()) deck = ArrayDeque(pool.freshFirst(BOARD_KEY, titles) { it.id })
            val t = deck.removeFirst()
            // Main characters first
            val who = roles(t).take(4).random()
            // The other choices are the same movie's cast first (much harder than random actors)
            val others =
                (
                    t.actors.filter { it.photo != null && it.name != who.name }.shuffled() +
                        titles.flatMap { it.actors }.filter { it.photo != null }.shuffled()
                ).distinctBy { it.name }
                    .filter { it.name != who.name }
                    .take(3)
            val people = (others + who).shuffled()
            return RunRound(
                t,
                people.map { it.name },
                who.name,
                "Who played ${cleanRole(who.role)} in ${t.label}?",
                people.map { it.photo ?: "" },
            )
        }

        companion object {
            const val BOARD_KEY = "WHO_PLAYED"
        }
    }

/** A character name worth asking about: not "Himself", not a voice-only note, not blank */
internal fun cleanRole(role: String?): String? {
    val r = role?.replace(Regex("\\s*\\((voice|uncredited|archive footage)\\)", RegexOption.IGNORE_CASE), "")?.trim() ?: return null
    if (r.length < 3 || r.length > 40) return null
    if (Regex("^(him|her|them)sel(f|ves)$|^self$|^narrator$|^additional voices$", RegexOption.IGNORE_CASE).containsMatchIn(r)) return null
    return r
}

// ---------------- Which season? ----------------

/** "Which season?": a real scene from a show. Which season is it from? */
@HiltViewModel
class WhichSeasonViewModel
    @Inject
    constructor(
        pool: PuzzlePool,
        actions: PuzzleActions,
        private val helper: GameLibraryClient,
    ) : RunGameViewModel(pool, actions) {
        override val boardKey = BOARD_KEY
        private var shows: List<PuzzleTitle> = emptyList()
        private var deck = ArrayDeque<PuzzleTitle>()

        init {
            start()
        }

        override suspend fun setup(): String? {
            if (!towerCan(helper, "seasons")) return "Which season? takes pictures on Tower. It arrives with Tower's next hourly update."
            shows = pool.titles().filter { it.isSeries }
            if (shows.size < 3) return notEnough("Not enough shows to play.", pool.mode())
            deck = ArrayDeque(pool.freshFirst(BOARD_KEY, shows) { it.id })
            return null
        }

        override suspend fun nextRound(): RunRound? {
            repeat(8) {
                if (deck.isEmpty()) deck = ArrayDeque(pool.freshFirst(BOARD_KEY, shows) { it.id })
                val t = deck.removeFirst()
                val seasons =
                    try {
                        val arr = helper.getJson("/api/scenes/${t.id.toString().replace("-", "")}/seasons").optJSONArray("seasons")
                        (0 until (arr?.length() ?: 0)).map { arr!!.getInt(it) }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (_: Exception) {
                        emptyList()
                    }
                if (seasons.size >= 2) {
                    val k = seasons.random()
                    // Up to 6 seasons to pick from, around the right one
                    val at = seasons.indexOf(k)
                    val from = (at - 2).coerceIn(0, (seasons.size - 6).coerceAtLeast(0))
                    val pick = seasons.subList(from, minOf(seasons.size, from + 6))
                    return RunRound(
                        t,
                        pick.map { "Season $it" },
                        "Season $k",
                        "Which season of ${t.name} is this from?",
                        listOf(sceneUrl(helper, t, "s$k-${(0..2).random()}.jpg")),
                    )
                }
                pool.saw(BOARD_KEY, t.id)
            }
            return null
        }

        companion object {
            const val BOARD_KEY = "WHICH_SEASON"
        }
    }

// ---------------- the screens ----------------

@Composable
fun QuoteItScreen(
    modifier: Modifier = Modifier,
    viewModel: QuoteItViewModel = hiltViewModel(),
) = RunGameScreen("Quote it", "Reading subtitles from Tower…", viewModel, modifier) { s, isTv ->
    // The lines show one by one as the clock runs
    val shown = if (s.won != null) 3 else 1 + ((1f - s.left) * 3).toInt().coerceAtMost(2)
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier
                .widthIn(max = 860.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PuzzlePanel)
                .timerRing(s.left, corner = 16.dp)
                .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        s.r.lines.take(shown).forEach {
            Text(
                it,
                fontSize = if (isTv) 21.sp else 17.sp,
                lineHeight = if (isTv) 29.sp else 24.sp,
                fontStyle = FontStyle.Italic,
                color = Color.White,
            )
        }
    }
}

@Composable
fun CloseUpScreen(
    modifier: Modifier = Modifier,
    viewModel: CloseUpViewModel = hiltViewModel(),
) = RunGameScreen("Close-up", "Taking pictures from your library…", viewModel, modifier) { s, isTv ->
    // Fully zoomed out with 30% of the time left; then it's a last chance
    val z = ((s.left - .3f) / .7f).coerceIn(0f, 1f)
    val zoom = if (s.won != null) 1f else 1f + 5f * z * z
    Box(
        modifier =
            (if (isTv) Modifier.width(tvPictureHeight(0.44f) * (16f / 9f)) else Modifier.fillMaxWidth())
                .timerRing(s.left, corner = 14.dp)
                .padding(6.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(PuzzlePanel),
    ) {
        AsyncImage(
            model = if (s.broken) s.r.fallback else s.r.pictures.first(),
            onError = { viewModel.pictureFailed() },
            contentDescription = "A close-up",
            contentScale = ContentScale.Crop,
            modifier =
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    transformOrigin = TransformOrigin(.5f, .45f)
                },
        )
    }
}

@Composable
fun WhichSeasonScreen(
    modifier: Modifier = Modifier,
    viewModel: WhichSeasonViewModel = hiltViewModel(),
) = RunGameScreen("Which season?", "Taking pictures from your shows…", viewModel, modifier) { s, isTv ->
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                (if (isTv) Modifier.width(tvPictureHeight(0.42f) * (16f / 9f)) else Modifier.weight(1f))
                    .timerRing(s.left, corner = 14.dp)
                    .padding(6.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PuzzlePanel),
        ) {
            AsyncImage(
                model =
                    if (s.broken) {
                        s.r.title.stills
                            .firstOrNull()
                    } else {
                        s.r.pictures.first()
                    },
                onError = { viewModel.pictureFailed() },
                contentDescription = "A scene",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (isTv) PuzzlePoster(s.r.title.poster, s.r.title.name, Modifier.width(tvPictureHeight(0.24f) * (2f / 3f)))
    }
}

@Composable
fun WhoPlayedScreen(
    modifier: Modifier = Modifier,
    viewModel: WhoPlayedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isTv = rememberIsTv()
    PuzzleScreen(isTv, modifier) {
        RunGameStates("Who played them?", "Finding characters in your library…", state, viewModel) { s ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
            ) {
                PuzzleHeader("Who played them?", "Round ${s.round}  ·  ${s.points} points  ·  Best ${s.best}", isTv)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    PuzzlePoster(s.r.title.poster, s.r.title.name, Modifier.width(if (isTv) 70.dp else 52.dp))
                    Text(
                        s.r.ask,
                        fontSize = if (isTv) 22.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = .1f)),
                ) {
                    Box(Modifier.fillMaxWidth(s.left).height(6.dp).background(PuzzleAqua))
                }
                AnswerLine(
                    text = if (s.won != null) "Right! ${s.r.answer}  +${s.won}" else "Pick the actor. Worth ${s.worth}",
                    good = if (s.won != null) true else null,
                )
                // Four actor photos with their names
                val first = remember(s.r) { FocusRequester() }
                LaunchedEffect(s.r) { first.tryRequestFocus() }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    s.r.choices.forEachIndexed { i, name ->
                        val isRight = s.won != null && name == s.r.answer
                        Button(
                            onClick = { viewModel.pick(name) },
                            shape = ButtonDefaults.shape(RoundedCornerShape(14.dp)),
                            colors =
                                ButtonDefaults.colors(
                                    containerColor = if (isRight) Color(0xFF2E8B57) else Color(0xFF1A1E38),
                                    contentColor = Color.White,
                                    focusedContainerColor = PuzzleGold,
                                    focusedContentColor = Color.Black,
                                ),
                            contentPadding =
                                androidx.compose.foundation.layout
                                    .PaddingValues(8.dp),
                            scale = ButtonDefaults.scale(focusedScale = 1.04f),
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                AsyncImage(
                                    model = s.r.pictures.getOrNull(i),
                                    contentDescription = name,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(3f / 4f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(PuzzlePanel),
                                )
                                Text(
                                    name,
                                    fontSize = if (isTv) 15.sp else 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    minLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The usual layout: scoreboard bar, the game's own picture or text, the answer line, then 6 choices */
@Composable
private fun RunGameScreen(
    title: String,
    loading: String,
    viewModel: RunGameViewModel,
    modifier: Modifier,
    stage: @Composable (RunState.Playing, Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val isTv = rememberIsTv()
    PuzzleScreen(isTv, modifier) {
        RunGameStates(title, loading, state, viewModel) { s ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isTv) 12.dp else 10.dp),
                modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
            ) {
                PuzzleHeader(title, "Round ${s.round}  ·  ${s.points} points  ·  Best ${s.best}", isTv)
                Text(s.r.ask, fontSize = if (isTv) 19.sp else 16.sp, color = Color.White.copy(alpha = .8f), textAlign = TextAlign.Center)
                Box(Modifier.heightIn(min = 40.dp), contentAlignment = Alignment.Center) { stage(s, isTv) }
                AnswerLine(
                    text = if (s.won != null) "Right! ${s.r.answer}  +${s.won}" else "Guess now for ${s.worth} points",
                    good = if (s.won != null) true else null,
                )
                ChoiceGrid(
                    choices = s.r.choices,
                    isTv = isTv,
                    focusKey = s.r,
                    onPick = viewModel::pick,
                    right = if (s.won != null) s.r.answer else null,
                    modifier = Modifier.padding(horizontal = if (isTv) 40.dp else 0.dp),
                )
            }
        }
    }
}

@Composable
private fun RunGameStates(
    title: String,
    loading: String,
    state: RunState,
    viewModel: RunGameViewModel,
    playing: @Composable (RunState.Playing) -> Unit,
) {
    val isTv = rememberIsTv()
    when (val s = state) {
        RunState.Loading -> {
            PuzzleMessage(loading)
        }

        is RunState.NotEnough -> {
            PuzzleOver(title, s.message, null, viewModel.actions, isTv, viewModel::start)
        }

        is RunState.Over -> {
            PuzzleOver(
                title = if (s.newBest) "New best: ${s.points} points!" else "Run over",
                body = "${s.why} It was ${s.said}.\nYou got ${s.rounds} right for ${s.points} points.\nYour best: ${s.best}",
                answer = s.answer,
                actions = viewModel.actions,
                isTv = isTv,
                onAgain = viewModel::start,
                run = s.run,
                score = s.points,
                newBest = s.newBest,
            )
        }

        is RunState.Playing -> {
            playing(s)
        }
    }
}
