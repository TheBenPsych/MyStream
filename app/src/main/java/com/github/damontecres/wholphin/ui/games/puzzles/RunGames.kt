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
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.services.GameLibraryClient
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
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/*
 * Ben's new one-player movie games (picked 2026-11-22; Close-up removed 2026-11-23): Quote it, Who played them?,
 * Which season? and 60-second blitz. The first three share one engine: a round has a clock, answering
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
    /** Shown partway through the round (Quote it: who says the line) */
    val hint: String? = null,
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

/**
 * "Quote it": a famous line from a movie or show (Ben 2026-11-23: well-known quotes, not random subtitle
 * lines). The lines live in res/raw/movie_quotes.json (the same list as the browser's movie-quotes.json);
 * only lines from titles that are on Tower are used. Who says it shows partway through as a hint.
 */
@HiltViewModel
class QuoteItViewModel
    @Inject
    constructor(
        @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
        pool: PuzzlePool,
        actions: PuzzleActions,
    ) : RunGameViewModel(pool, actions) {
        override val boardKey = BOARD_KEY
        override val roundMs = 20_000L
        private var titles: List<PuzzleTitle> = emptyList()
        private var quotes: List<Pair<PuzzleTitle, org.json.JSONObject>> = emptyList()
        private var deck = ArrayDeque<Pair<PuzzleTitle, org.json.JSONObject>>()

        init {
            start()
        }

        override suspend fun setup(): String? {
            titles = pool.titles()
            val all =
                context.resources
                    .openRawResource(com.github.damontecres.wholphin.R.raw.movie_quotes)
                    .bufferedReader()
                    .use { org.json.JSONObject(it.readText()) }
                    .getJSONArray("quotes")
            val byName = titles.groupBy { quoteKey(it.name) }
            quotes =
                (0 until all.length()).mapNotNull { i ->
                    val q = all.getJSONObject(i)
                    val year = q.optInt("y")
                    val t =
                        byName[quoteKey(q.getString("t"))]?.firstOrNull {
                            it.year == null || year == 0 ||
                                kotlin.math.abs(it.year - year) <= 1
                        }
                    t?.let { it to q }
                }
            if (quotes.size < 5) {
                return notEnough(
                    "Quote it needs a few well-known movies or shows on Tower (it found ${quotes.size} famous lines).",
                    pool.mode(),
                )
            }
            deck = ArrayDeque(pool.freshFirst(BOARD_KEY, quotes) { it.first.id })
            return null
        }

        override suspend fun nextRound(): RunRound? {
            if (deck.isEmpty()) deck = ArrayDeque(pool.freshFirst(BOARD_KEY, quotes) { it.first.id })
            val (t, q) = deck.removeFirst()
            return RunRound(
                t,
                titleChoices(t, titles),
                t.name,
                "Which movie or show is this line from?",
                lines = listOf("“${q.getString("q")}”"),
                hint = q.optString("who").takeIf { it.isNotBlank() }?.let { "Said by $it" },
            )
        }

        companion object {
            const val BOARD_KEY = "QUOTE_IT"
        }
    }

/** A name in a form that matches however Tower spells it ("The Lord of the Rings: The Two Towers" = "lord of the rings the two towers") */
internal fun quoteKey(name: String): String =
    name
        .lowercase()
        .replace("&", "and")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .removePrefix("the ")

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
) = RunGameScreen("Quote it", "Finding famous lines from your library…", viewModel, modifier) { s, isTv ->
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .widthIn(max = 860.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PuzzlePanel)
                .timerRing(s.left, corner = 16.dp)
                .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        s.r.lines.forEach {
            Text(
                it,
                fontSize = if (isTv) 28.sp else 21.sp,
                lineHeight = if (isTv) 38.sp else 29.sp,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
        // Who says it shows once 40% of the time has gone (or once it's answered)
        if (s.r.hint != null && (s.won != null || s.left < .6f)) HintChips(listOf(s.r.hint))
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
                            border =
                                com.github.damontecres.wholphin.ui.theme
                                    .glowfinButtonBorder(RoundedCornerShape(14.dp)),
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
                            scale = ButtonDefaults.scale(focusedScale = 1f),
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
