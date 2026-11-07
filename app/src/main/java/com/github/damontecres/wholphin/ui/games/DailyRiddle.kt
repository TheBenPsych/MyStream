package com.github.damontecres.wholphin.ui.games

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.core.content.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.LibraryKind
import com.github.damontecres.wholphin.ui.theme.BenflixRed
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import javax.inject.Inject

sealed interface RiddleState {
    data object Loading : RiddleState

    data class Problem(
        val message: String,
    ) : RiddleState

    data class Playing(
        /** Riddles solved so far in this run */
        val streak: Int,
        /** Points so far in this run */
        val score: Int,
        /** This profile's best score before this run */
        val best: Int,
        /** Clues shown so far, hardest first (an easier one shows as time runs down) */
        val clues: List<String>,
        val choices: List<String>,
        /** Seconds left to answer */
        val secondsLeft: Int,
        val totalSeconds: Int,
        val answer: String,
        /** Set once this riddle is answered: true if right */
        val right: Boolean? = null,
        /** The wrong pick, if there was one */
        val picked: String? = null,
        /** Points won for this riddle, once answered right */
        val points: Int = 0,
    ) : RiddleState

    data class Over(
        val streak: Int,
        val score: Int,
        val best: Int,
        val newBest: Boolean,
        /** The riddle that ended the run, and whether time ran out */
        val answer: String,
        val timedOut: Boolean,
    ) : RiddleState
}

/**
 * Benflix "Movie riddle": movies from your own library, one after another. Each has three
 * clues that get easier as the clock runs down, and five titles to pick from. Pick right before
 * time runs out to go to the next riddle; one wrong pick (or running out of time) ends the run.
 * Faster answers win more points (waiting for easier clues costs points), and the
 * "Highest scores" board ranks the most points in one run.
 */
@HiltViewModel
class DailyRiddleViewModel
    @Inject
    constructor(
        @param:ApplicationContext context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val puzzlePool: com.github.damontecres.wholphin.ui.games.puzzles.PuzzlePool,
    ) : ViewModel() {
        /** Where the Games page reads each account's bests for the "Highest scores" board */
        private val prefs = context.getSharedPreferences("benflix_games", Context.MODE_PRIVATE)
        private val _state = MutableStateFlow<RiddleState>(RiddleState.Loading)
        val state: StateFlow<RiddleState> = _state

        private data class Riddle(
            val answer: String,
            val clues: List<String>,
            val choices: List<String>,
        )

        private var names: List<Pair<java.util.UUID, String>> = emptyList()
        private var deck: ArrayDeque<Pair<java.util.UUID, String>> = ArrayDeque()
        private var upcoming: Deferred<Riddle?>? = null
        private var timer: Job? = null

        /** Kids get more time to read */
        private val totalSeconds get() = if (KidsMode.isActive) KIDS_SECONDS else SECONDS

        init {
            load()
        }

        private fun bestKey(): String {
            val profile =
                when {
                    GuestMode.isActive -> "guest"
                    KidsMode.isActive -> "kids_${serverRepository.currentUser?.id}"
                    else -> serverRepository.currentUser?.id?.toString() ?: "guest"
                }
            return "${profile}_$BOARD_KEY"
        }

        fun best(): Int = prefs.getInt(bestKey(), 0)

        /** True while the screen isn't showing (the countdown waits) */
        private var paused = false

        fun setPaused(value: Boolean) {
            paused = value
        }

        /** Start a new run */
        fun load() {
            timer?.cancel()
            _state.value = RiddleState.Loading
            viewModelScope.launch {
                try {
                    if (names.isEmpty()) names = withContext(WholphinDispatchers.IO) { movieNames() }
                    if (names.size < CHOICES) {
                        _state.value = RiddleState.Problem("You need at least $CHOICES movies on the server to play.")
                        return@launch
                    }
                    deck = ArrayDeque(names.shuffled())
                    upcoming?.cancel()
                    upcoming = null
                    val riddle = nextRiddle()
                    if (riddle == null) {
                        _state.value = RiddleState.Problem("Couldn't find movies with enough details for riddles.")
                        return@launch
                    }
                    show(riddle, streak = 0, score = 0)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Movie riddle couldn't load")
                    _state.value = RiddleState.Problem("Couldn't reach the server. Try again in a moment.")
                }
            }
        }

        /** The next riddle: the one already being fetched in the background, or a fresh one */
        private suspend fun nextRiddle(): Riddle? = (upcoming ?: fetchAhead()).await().also { upcoming = null }

        private fun fetchAhead(): Deferred<Riddle?> =
            viewModelScope.async(WholphinDispatchers.IO) {
                // Try a few movies, since some don't have enough details for clues
                repeat(TRIES) {
                    if (deck.isEmpty()) deck = ArrayDeque(names.shuffled())
                    val (id, name) = deck.removeFirst()
                    val clues =
                        try {
                            clues(details(id))
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            Timber.w(ex, "Riddle details failed for %s", name)
                            emptyList()
                        }
                    if (clues.size >= 2) {
                        // Wrong choices never come from the same collection as the answer, so
                        // clues like the cast can't fit two of the choices
                        val others =
                            names
                                .map { it.second }
                                .filter { it != name && !sameSeries(it, name) }
                                .shuffled()
                                .take(CHOICES - 1)
                        return@async Riddle(name, clues, (others + name).shuffled())
                    }
                }
                null
            }

        private fun show(
            riddle: Riddle,
            streak: Int,
            score: Int,
        ) {
            val total = totalSeconds
            _state.value =
                RiddleState.Playing(
                    streak = streak,
                    score = score,
                    best = best(),
                    clues = riddle.clues.take(1),
                    choices = riddle.choices,
                    secondsLeft = total,
                    totalSeconds = total,
                    answer = riddle.answer,
                )
            // Get the next riddle ready while this one is on screen, so there's no wait
            upcoming = fetchAhead()
            timer?.cancel()
            timer =
                viewModelScope.launch {
                    var left = total
                    while (left > 0) {
                        delay(1_000)
                        // The clock stops while the app is hidden (Home button, screen off, another app on top)
                        if (paused) continue
                        left--
                        val playing = _state.value as? RiddleState.Playing ?: return@launch
                        if (playing.right != null) return@launch
                        // An easier clue shows each third of the time
                        val shown = (1 + (total - left) * riddle.clues.size / total).coerceAtMost(riddle.clues.size)
                        _state.value = playing.copy(secondsLeft = left, clues = riddle.clues.take(shown))
                    }
                    end(timedOut = true, picked = null)
                }
        }

        fun pick(choice: String) {
            val playing = _state.value as? RiddleState.Playing ?: return
            if (playing.right != null) return
            timer?.cancel()
            if (choice == playing.answer) {
                val points = points(playing.secondsLeft, playing.totalSeconds)
                val score = playing.score + points
                _state.value = playing.copy(right = true, streak = playing.streak + 1, score = score, points = points)
                viewModelScope.launch {
                    delay(REVEAL_MS)
                    val riddle =
                        try {
                            nextRiddle()
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            Timber.w(ex, "Next riddle failed")
                            null
                        }
                    if (riddle == null) {
                        // Couldn't get another one: count the run as finished, not lost
                        finish(playing.streak + 1, score, playing.answer, timedOut = false)
                    } else {
                        show(riddle, playing.streak + 1, score)
                    }
                }
            } else {
                end(timedOut = false, picked = choice)
            }
        }

        private fun end(
            timedOut: Boolean,
            picked: String?,
        ) {
            val playing = _state.value as? RiddleState.Playing ?: return
            _state.value = playing.copy(right = false, picked = picked)
            viewModelScope.launch {
                delay(REVEAL_MS)
                finish(playing.streak, playing.score, playing.answer, timedOut)
            }
        }

        private fun finish(
            streak: Int,
            score: Int,
            answer: String,
            timedOut: Boolean,
        ) {
            val best = best()
            val newBest = score > best
            if (newBest) prefs.edit { putInt(bestKey(), score) }
            _state.value = RiddleState.Over(streak, score, maxOf(best, score), newBest, answer, timedOut)
        }

        /** 100 points for a right answer, plus up to 400 more the faster you answer */
        private fun points(
            secondsLeft: Int,
            totalSeconds: Int,
        ): Int = MIN_POINTS + SPEED_POINTS * secondsLeft / totalSeconds

        override fun onCleared() {
            timer?.cancel()
            super.onCleared()
        }

        /**
         * Every real movie's id and name, from the movie libraries only: Stand up specials
         * (and any other non-movie library) are left out, and so are "collection" or
         * "trilogy" bundles, since a riddle should be about one movie.
         */
        private suspend fun movieNames(): List<Pair<java.util.UUID, String>> {
            val userId = serverRepository.currentUser?.id
            val libraries =
                api.userViewsApi
                    .getUserViews(userId = userId)
                    .content.items
                    .filter {
                        val kind = LibraryKind.from(it.name, it.collectionType)
                        kind == LibraryKind.MOVIES || kind == LibraryKind.ANIME_MOVIES
                    }.map { it.id }
            return libraries
                .flatMap { libraryId ->
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = userId,
                                parentId = libraryId,
                                includeItemTypes = listOf(BaseItemKind.MOVIE),
                                recursive = true,
                                sortBy = listOf(ItemSortBy.SORT_NAME),
                                enableImages = false,
                                enableUserData = false,
                            ),
                        ).content.items
                }.let { items ->
                    // The anime switch on the game's page
                    val keep = puzzlePool.animeKeeper()
                    items.filter { keep(it.id) }
                }.mapNotNull { item -> item.name?.takeIf { it.isNotBlank() }?.let { item.id to it } }
                .filterNot { (_, name) -> BUNDLE_WORDS.containsMatchIn(name) }
                .distinctBy { it.second }
        }

        /**
         * The start of a title that names its series, like "Harry Potter" in "Harry Potter and
         * the Goblet of Fire" or "Toy Story" in "Toy Story 3". Two movies with the same key are
         * from the same collection (or one key starts the other, like "Saw" and "Saw II"), so
         * they never show up as choices in the same riddle.
         */
        private fun sameSeries(
            a: String,
            b: String,
        ): Boolean {
            val x = seriesKey(a)
            val y = seriesKey(b)
            if (x.isEmpty() || y.isEmpty()) return false
            return x == y || x.startsWith("$y ") || y.startsWith("$x ")
        }

        private fun seriesKey(name: String): String {
            val words =
                name
                    .lowercase()
                    .substringBefore(":")
                    .replace(Regex("[^a-z0-9 ]"), " ")
                    .split(" ")
                    .filter { it.isNotBlank() && it !in SMALL_WORDS }
            return words.take(2).joinToString(" ")
        }

        private suspend fun details(id: java.util.UUID): BaseItemDto =
            api.itemsApi
                .getItems(
                    GetItemsRequest(
                        userId = serverRepository.currentUser?.id,
                        ids = listOf(id),
                        fields = listOf(ItemFields.OVERVIEW, ItemFields.PEOPLE, ItemFields.GENRES, ItemFields.TAGLINES),
                    ),
                ).content.items
                .first()

        /** Three clues, hardest first: the year and kind of movie, then the story, then who's in it */
        private fun clues(movie: BaseItemDto): List<String> {
            val name = movie.name.orEmpty()

            // Hide the title, and its series name too ("Harry Potter"), so a clue doesn't give it away
            val series = name.substringBefore(":").trim()

            fun hide(text: String): String {
                var out = if (name.length > 2) text.replace(name, "▇▇▇▇", ignoreCase = true) else text
                if (series.length > 3 && series != name) out = out.replace(series, "▇▇▇▇", ignoreCase = true)
                return out
            }
            val genres = movie.genres.orEmpty().take(2)
            val first =
                buildString {
                    append("A ")
                    movie.productionYear?.let { append("$it ") }
                    append(if (genres.isEmpty()) "movie" else genres.joinToString(" and ").lowercase() + " movie")
                    movie.taglines
                        ?.firstOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { append(":\n“${hide(it)}”") }
                }
            val plot =
                movie.overview
                    ?.split(Regex("(?<=[.!?])\\s+"))
                    ?.firstOrNull()
                    ?.takeIf { it.length > 20 }
                    ?.let { "“${hide(it)}”" }
            val cast =
                movie.people
                    .orEmpty()
                    .filter { it.type == PersonKind.ACTOR }
                    .mapNotNull { it.name }
                    .take(3)
                    .takeIf { it.isNotEmpty() }
                    ?.let { "Starring " + it.joinToString(", ") }
            return listOfNotNull(first, plot, cast)
        }

        companion object {
            /** Titles that are a bundle of movies rather than one movie */
            private val BUNDLE_WORDS =
                Regex("\\b(collection|trilogy|quadrilogy|box ?set|double feature)\\b", RegexOption.IGNORE_CASE)

            /** Words skipped when working out a title's series */
            private val SMALL_WORDS = setOf("the", "a", "an", "and", "of")
            private const val CHOICES = 5
            private const val SECONDS = 20
            private const val KIDS_SECONDS = 30
            private const val TRIES = 4
            private const val REVEAL_MS = 1_600L
            private const val MIN_POINTS = 100
            private const val SPEED_POINTS = 400

            /** The "Highest scores" board's key: most points in one run */
            const val BOARD_KEY = "MOVIE_RIDDLE_POINTS"
        }
    }

@Composable
fun DailyRiddlePage(
    modifier: Modifier = Modifier,
    viewModel: DailyRiddleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isTv = rememberIsTv()
    // Pause the 20-second clock while the app is hidden, and carry on when it's back
    LifecycleResumeEffect(viewModel) {
        viewModel.setPaused(false)
        onPauseOrDispose { viewModel.setPaused(true) }
    }
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier =
            modifier
                .fillMaxSize()
                .background(Color(0xFF0B0C10))
                .padding(horizontal = if (isTv) 48.dp else 16.dp, vertical = if (isTv) 24.dp else 12.dp),
    ) {
        FitScreen(isTv) { RiddleContent(state, isTv, viewModel) }
    }
}

@Composable
private fun RiddleContent(
    state: RiddleState,
    isTv: Boolean,
    viewModel: DailyRiddleViewModel,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
    ) {
        Text(
            text = "MOVIE RIDDLE",
            fontFamily = BenflixTitleFont,
            fontSize = if (isTv) 34.sp else 26.sp,
            color = Color.White,
        )
        when (val s = state) {
            RiddleState.Loading -> {
                Text("Getting the riddles ready…", fontSize = 20.sp, color = Color.White)
            }

            is RiddleState.Problem -> {
                Text(s.message, fontSize = 20.sp, color = Color.White, textAlign = TextAlign.Center)
                FocusedButton("Try again", viewModel::load)
            }

            is RiddleState.Playing -> {
                RiddleRound(s, isTv, viewModel::pick)
            }

            is RiddleState.Over -> {
                Text(
                    text = if (s.newBest) "New best score!" else "Run over: %,d points".format(s.score),
                    fontFamily = BenflixTitleFont,
                    fontSize = 40.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text =
                        (if (s.newBest) "%,d points. ".format(s.score) else "") +
                            "${s.streak} " + (if (s.streak == 1) "riddle" else "riddles") + " in a row.\n" +
                            (if (s.timedOut) "Time ran out. " else "") +
                            "It was ${s.answer}.\nYour best: %,d points".format(s.best),
                    fontSize = 20.sp,
                    color = Color.White.copy(alpha = .85f),
                    textAlign = TextAlign.Center,
                )
                FocusedButton("Play again", viewModel::load)
                Text("Press Back to leave", fontSize = 14.sp, color = Color.White.copy(alpha = .6f))
            }
        }
    }
}

@Composable
private fun RiddleRound(
    s: RiddleState.Playing,
    isTv: Boolean,
    onPick: (String) -> Unit,
) {
    Text(
        text = "Score %,d  ·  Streak ${s.streak}  ·  Best %,d".format(s.score, maxOf(s.best, s.score)),
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFFFFC94D),
        textAlign = TextAlign.Center,
    )
    // The clock: a bar that shrinks, plus the seconds left (turns red near the end)
    val low = s.secondsLeft <= 5
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(if (isTv) 0.7f else 1f),
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = .12f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(s.secondsLeft.toFloat() / s.totalSeconds)
                    .height(8.dp)
                    .background(if (low) BenflixRed else Color(0xFF46D37A)),
            )
        }
        Text(
            "${s.secondsLeft}s",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (low) BenflixRed else Color.White,
        )
    }
    Text(
        "Which movie is it? One guess. Answer fast for more points, or wait for easier clues.",
        fontSize = 16.sp,
        color = Color.White.copy(alpha = .75f),
        textAlign = TextAlign.Center,
    )
    s.clues.forEachIndexed { i, clue ->
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxWidth(if (isTv) 0.7f else 1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1C1C1F))
                    .padding(horizontal = 20.dp, vertical = if (isTv) 12.dp else 16.dp),
        ) {
            Text(
                text = "Clue ${i + 1}\n$clue",
                fontSize = if (isTv) 20.sp else 18.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
    // The answer shows above the choices so it's always on screen (space kept so nothing jumps)
    Text(
        text =
            when (s.right) {
                true -> "Got it! +${s.points} points"
                false -> if (s.picked == null) "Time's up! It was ${s.answer}" else "Nope, it was ${s.answer}"
                null -> " "
            },
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (s.right == true) Color(0xFF46D37A) else BenflixRed,
        textAlign = TextAlign.Center,
        maxLines = 2,
    )
    val firstFocus = remember(s.answer) { FocusRequester() }
    LaunchedEffect(s.answer) { firstFocus.tryRequestFocus() }
    // Five titles: rows of three on TV (3 + 2), one per row on phones
    s.choices.chunked(if (isTv) 3 else 1).forEachIndexed { r, row ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(if (isTv) 0.85f else 1f),
        ) {
            row.forEachIndexed { c, choice ->
                val isAnswer = s.right != null && choice == s.answer
                val wrong = choice == s.picked
                Button(
                    onClick = { onPick(choice) },
                    enabled = s.right == null,
                    colors =
                        ButtonDefaults.colors(
                            containerColor = Color(0xFF2A2A2E),
                            contentColor = Color.White,
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black,
                            disabledContainerColor =
                                when {
                                    isAnswer -> Color(0xFF2E8B57)
                                    wrong -> BenflixRed.copy(alpha = .6f)
                                    else -> Color(0xFF2A2A2E).copy(alpha = .5f)
                                },
                            disabledContentColor = if (isAnswer || wrong) Color.White else Color.White.copy(alpha = .5f),
                        ),
                    modifier =
                        Modifier
                            .weight(1f)
                            .then(if (r == 0 && c == 0) Modifier.focusRequester(firstFocus) else Modifier),
                ) {
                    Text(text = choice, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun FocusedButton(
    text: String,
    onClick: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    Button(onClick = onClick, modifier = Modifier.focusRequester(focus)) { Text(text) }
}
