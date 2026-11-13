package com.github.damontecres.wholphin.ui.games.puzzles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.ui.games.tvPictureHeight
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** One rule on the side of the grid, like "Has Keanu Reeves" or "Made in the 90s" */
data class GridRule(
    val kind: String,
    val label: String,
    val test: (PuzzleTitle) -> Boolean,
)

sealed interface GridState {
    data object Loading : GridState

    data class NotEnough(
        val message: String,
    ) : GridState

    data class Playing(
        val rows: List<GridRule>,
        val cols: List<GridRule>,
        /** Filled squares (row * 3 + col) and the title put there */
        val filled: Map<Int, PuzzleTitle>,
        /** The square being answered, or null while picking a square */
        val open: Int? = null,
        val choices: List<PuzzleTitle> = emptyList(),
        val lives: Int,
        val points: Int,
        val best: Int,
        val message: String? = null,
        val good: Boolean? = null,
    ) : GridState

    data class Over(
        val points: Int,
        val filled: Int,
        val best: Int,
        val newBest: Boolean,
        val last: PuzzleTitle?,
    ) : GridState
}

/**
 * "Movie grid": a 3 by 3 board. Every square needs a movie or show from Tower that fits both its
 * row rule and its column rule. Pick a square, then pick from 6 titles. 3 lives. Each title only
 * once. Board = most points (100 a square, 50 per life left, 200 for a full grid).
 */
@HiltViewModel
class MovieGridViewModel
    @Inject
    constructor(
        private val pool: PuzzlePool,
        val actions: PuzzleActions,
    ) : ViewModel() {
        private val _state = MutableStateFlow<GridState>(GridState.Loading)
        val state: StateFlow<GridState> = _state
        private var titles: List<PuzzleTitle> = emptyList()

        init {
            start()
        }

        fun start() {
            _state.value = GridState.Loading
            viewModelScope.launch {
                try {
                    titles = pool.titles()
                    val board = makeBoard(titles)
                    if (board == null) {
                        _state.value =
                            GridState.NotEnough("Couldn't make a grid from your library. Try the anime switch on the game's page.")
                        return@launch
                    }
                    _state.value =
                        GridState.Playing(board.first, board.second, emptyMap(), lives = 3, points = 0, best = pool.best(BOARD_KEY))
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Movie grid couldn't load")
                    _state.value = GridState.NotEnough("Couldn't load movies from the server. Try again in a moment.")
                }
            }
        }

        fun openSquare(square: Int) {
            val p = _state.value as? GridState.Playing ?: return
            if (square in p.filled) return
            val r = p.rows[square / 3]
            val c = p.cols[square % 3]
            val used =
                p.filled.values
                    .map { it.id }
                    .toSet()
            val fits = titles.filter { r.test(it) && c.test(it) && it.id !in used }
            val wrong = titles.filter { !(r.test(it) && c.test(it)) && it.id !in used }
            // Usually one right answer among the six, sometimes two
            val right = fits.shuffled().take(if (fits.size > 3 && Math.random() < .4) 2 else 1)
            // Wrong ones that fit one of the two rules make it trickier
            val tricky = wrong.filter { r.test(it) || c.test(it) }.shuffled().take(3)
            val rest = wrong.filter { it !in tricky }.shuffled().take(5 - right.size - tricky.size + 1)
            val choices = (right + tricky + rest).distinctBy { it.name }.take(6).shuffled()
            _state.value = p.copy(open = square, choices = choices, message = null, good = null)
        }

        fun close() {
            val p = _state.value as? GridState.Playing ?: return
            _state.value = p.copy(open = null, choices = emptyList())
        }

        fun pick(name: String) {
            val p = _state.value as? GridState.Playing ?: return
            val square = p.open ?: return
            val t = p.choices.firstOrNull { it.name == name } ?: return
            val r = p.rows[square / 3]
            val c = p.cols[square % 3]
            if (r.test(t) && c.test(t)) {
                val filled = p.filled + (square to t)
                val points = p.points + 100
                if (filled.size == 9) {
                    finish(points + 200 + p.lives * 50, filled.size, t)
                } else {
                    _state.value =
                        p.copy(
                            filled = filled,
                            points = points,
                            open = null,
                            choices = emptyList(),
                            message = "Right! ${t.label}",
                            good = true,
                        )
                }
            } else {
                val lives = p.lives - 1
                if (lives <= 0) {
                    finish(p.points, p.filled.size, t)
                } else {
                    val miss = if (!r.test(t)) r.label else c.label
                    _state.value =
                        p.copy(
                            lives = lives,
                            open = null,
                            choices = emptyList(),
                            message = "${t.name} doesn't fit \"$miss\". $lives ${if (lives == 1) "life" else "lives"} left.",
                            good = false,
                        )
                }
            }
        }

        private fun finish(
            points: Int,
            filled: Int,
            last: PuzzleTitle?,
        ) {
            val best = pool.best(BOARD_KEY)
            val newBest = pool.submit(BOARD_KEY, points)
            _state.value = GridState.Over(points, filled, maxOf(best, points), newBest, last)
        }

        companion object {
            const val BOARD_KEY = "MOVIE_GRID"

            /** Every rule the library can support (each needs a few titles) */
            fun allRules(titles: List<PuzzleTitle>): List<GridRule> {
                val rules = mutableListOf<GridRule>()
                titles
                    .flatMap { t -> t.actors.map { it.name } }
                    .groupingBy { it }
                    .eachCount()
                    .filter { it.value >= 3 }
                    .keys
                    .forEach { name -> rules += GridRule("actor", "Has $name") { t -> t.actors.any { it.name == name } } }
                titles
                    .flatMap { it.genres }
                    .groupingBy { it }
                    .eachCount()
                    .filter { it.value >= 4 }
                    .keys
                    .forEach { g -> rules += GridRule("genre", g) { t -> g in t.genres } }
                titles
                    .mapNotNull { it.year?.let { y -> y / 10 * 10 } }
                    .groupingBy { it }
                    .eachCount()
                    .filter { it.value >= 4 }
                    .keys
                    .forEach { d ->
                        val name = if (d >= 2000) "${d}s" else "${d % 100}s"
                        rules += GridRule("decade", "Made in the $name") { t -> t.year?.let { it / 10 * 10 } == d }
                    }
                titles
                    .flatMap { it.studios }
                    .groupingBy { it }
                    .eachCount()
                    .filter { it.value >= 4 }
                    .keys
                    .forEach { s -> rules += GridRule("studio", "By $s") { t -> s in t.studios } }
                rules += GridRule("score", "IMDb 7.5 or more") { t -> t.rating >= 75 }
                rules += GridRule("score", "IMDb under 6.0") { t -> t.rating in 1..59 }
                rules += GridRule("type", "A TV show") { t -> t.isSeries }
                rules += GridRule("length", "Over 2 hours") { t -> !t.isSeries && (t.runtimeMin ?: 0) > 120 }
                return rules
            }

            /** Tries random rules until every square has at least one answer (two when it can) */
            fun makeBoard(titles: List<PuzzleTitle>): Pair<List<GridRule>, List<GridRule>>? {
                val rules = allRules(titles)
                if (rules.size < 6) return null
                var fallback: Pair<List<GridRule>, List<GridRule>>? = null
                repeat(400) {
                    val shuffled = rules.shuffled()
                    // Rows and columns use different kinds of rules, so no square is impossible by design
                    val rowKinds =
                        shuffled
                            .map { it.kind }
                            .distinct()
                            .shuffled()
                            .take(2)
                            .toSet()
                    val rows = shuffled.filter { it.kind in rowKinds }.take(3)
                    val cols = shuffled.filter { it.kind !in rowKinds }.distinctBy { it.label }.take(3)
                    if (rows.size < 3 || cols.size < 3) return@repeat
                    val counts = rows.flatMap { r -> cols.map { c -> titles.count { r.test(it) && c.test(it) } } }
                    if (counts.all { it >= 2 }) return rows to cols
                    if (fallback == null && counts.all { it >= 1 }) fallback = rows to cols
                }
                return fallback
            }
        }
    }

@Composable
fun MovieGridScreen(
    modifier: Modifier = Modifier,
    viewModel: MovieGridViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isTv = rememberIsTv()
    PuzzleScreen(isTv, modifier) {
        when (val s = state) {
            GridState.Loading -> {
                PuzzleMessage("Making a grid from your library…")
            }

            is GridState.NotEnough -> {
                PuzzleOver("Movie grid", s.message, null, viewModel.actions, isTv, viewModel::start)
            }

            is GridState.Over -> {
                PuzzleOver(
                    title =
                        if (s.newBest) {
                            "New best: ${s.points} points!"
                        } else if (s.filled == 9) {
                            "Grid complete!"
                        } else {
                            "Out of lives"
                        },
                    body = "You filled ${s.filled} of 9 squares for ${s.points} points.\nYour best: ${s.best}",
                    answer = s.last,
                    actions = viewModel.actions,
                    isTv = isTv,
                    onAgain = viewModel::start,
                )
            }

            is GridState.Playing -> {
                androidx.activity.compose.BackHandler(enabled = s.open != null) { viewModel.close() }
                GridPlay(s, isTv, viewModel)
            }
        }
    }
}

@Composable
private fun GridPlay(
    s: GridState.Playing,
    isTv: Boolean,
    viewModel: MovieGridViewModel,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        PuzzleHeader("Movie grid", "${s.points} points  ·  ${"♥".repeat(s.lives)}  ·  Your best ${s.best}", isTv)
        val board: @Composable (Modifier) -> Unit = { mod -> GridBoard(s, isTv, viewModel::openSquare, mod) }
        val side: @Composable (Modifier) -> Unit = { mod ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = mod) {
                val open = s.open
                if (open == null) {
                    AnswerLine(s.message ?: "Pick a square. Its title must fit the rule on its row AND its column.", s.good)
                } else {
                    Text(
                        "${s.rows[open / 3].label}  +  ${s.cols[open % 3].label}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PuzzleGold,
                    )
                    Text("Which title fits both? (Back to pick another square)", fontSize = 15.sp, color = Color.White.copy(alpha = .75f))
                    ChoiceGrid(
                        choices = s.choices.map { it.name },
                        isTv = false,
                        focusKey = open,
                        onPick = viewModel::pick,
                    )
                }
            }
        }
        if (isTv) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.Top) {
                board(Modifier)
                side(Modifier.width(420.dp))
            }
        } else {
            board(Modifier)
            side(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun GridBoard(
    s: GridState.Playing,
    isTv: Boolean,
    onOpen: (Int) -> Unit,
    modifier: Modifier,
) {
    val cell: Dp = if (isTv) tvPictureHeight(0.17f) else 76.dp
    val label: Dp = if (isTv) 150.dp else 64.dp
    val focus = remember { FocusRequester() }
    LaunchedEffect(s.open == null, s.filled.size) { if (s.open == null) focus.tryRequestFocus() }
    val firstEmpty = (0 until 9).firstOrNull { it !in s.filled }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width(label))
            s.cols.forEach { RuleLabel(it.label, Modifier.width(cell).height(if (isTv) 48.dp else 40.dp)) }
        }
        s.rows.forEachIndexed { r, rule ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                RuleLabel(rule.label, Modifier.width(label).height(cell))
                repeat(3) { c ->
                    val i = r * 3 + c
                    val t = s.filled[i]
                    val shape = RoundedCornerShape(10.dp)
                    Surface(
                        onClick = { onOpen(i) },
                        shape = ClickableSurfaceDefaults.shape(shape),
                        border =
                            ClickableSurfaceDefaults.border(
                                focusedBorder = Border(androidx.compose.foundation.BorderStroke(3.dp, BenflixColors.Text), shape = shape),
                            ),
                        colors =
                            ClickableSurfaceDefaults.colors(
                                containerColor = if (i == s.open) Color(0xFF4A3B12) else PuzzlePanel,
                                focusedContainerColor = Color(0xFF3A3A40),
                            ),
                        modifier =
                            Modifier
                                .size(cell)
                                .then(if (i == firstEmpty) Modifier.focusRequester(focus) else Modifier),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    ) {
                        if (t != null) {
                            AsyncImage(
                                model = t.poster,
                                contentDescription = t.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text("?", fontSize = 26.sp, color = Color.White.copy(alpha = .4f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleLabel(
    text: String,
    modifier: Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.background(Color(0xFF26262B), RoundedCornerShape(10.dp)).padding(4.dp),
    ) {
        Text(
            text,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
