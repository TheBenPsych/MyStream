package com.github.damontecres.wholphin.ui.games

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.theme.BenflixRed
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import kotlin.random.Random

sealed interface RiddleState {
    data object Loading : RiddleState

    data class Problem(
        val message: String,
    ) : RiddleState

    data class Playing(
        /** Which riddle this is, from 0 */
        val round: Int,
        val rounds: Int,
        /** Clues shown so far, hardest first */
        val clues: List<String>,
        val choices: List<String>,
        /** Wrong picks so far (greyed out) */
        val wrong: Set<String> = emptySet(),
        /** Stars won so far in this game */
        val score: Int,
        /** true for today's riddles (the same for the whole family), false for a bonus game */
        val daily: Boolean,
        /** Set once this riddle is over: stars won for it (0 if missed) */
        val result: Int? = null,
        val answer: String,
    ) : RiddleState

    data class Done(
        /** Each riddle's answer and the stars won for it */
        val results: List<Pair<String, Int>>,
        val daily: Boolean,
        val streak: Int,
    ) : RiddleState
}

/**
 * Benflix "Daily movie riddle": five movies from your own library. Each has three clues that get
 * easier and five titles to pick from; a wrong pick shows the next clue. Up to 3 stars per movie.
 * The first game each day is the same for everyone on the server; "Play again" deals a new set.
 */
@HiltViewModel
class DailyRiddleViewModel
    @Inject
    constructor(
        @param:ApplicationContext context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) : ViewModel() {
        private val prefs = context.getSharedPreferences("benflix_riddle", Context.MODE_PRIVATE)
        private val _state = MutableStateFlow<RiddleState>(RiddleState.Loading)
        val state: StateFlow<RiddleState> = _state

        private data class Riddle(
            val answer: String,
            val clues: List<String>,
            val choices: List<String>,
        )

        private var riddles: List<Riddle> = emptyList()
        private val results = mutableListOf<Pair<String, Int>>()
        private var daily = true
        private val today = LocalDate.now()

        init {
            load()
        }

        private fun profile(): String =
            when {
                GuestMode.isActive -> "guest"
                KidsMode.isActive -> "kids_${serverRepository.currentUser?.id}"
                else -> serverRepository.currentUser?.id?.toString() ?: "guest"
            }

        private fun dayKey(day: LocalDate) = "${profile()}_$day"

        /** Start a game: today's riddles if not played yet, otherwise a new random set */
        fun load() {
            _state.value = RiddleState.Loading
            viewModelScope.launch {
                try {
                    val names = withContext(WholphinDispatchers.IO) { movieNames() }
                    if (names.size < CHOICES) {
                        _state.value = RiddleState.Problem("You need at least $CHOICES movies on the server to play.")
                        return@launch
                    }
                    daily = prefs.getInt(dayKey(today), NOT_PLAYED) == NOT_PLAYED
                    // Today's set is the same for everyone: the date picks it. A bonus game is random.
                    val random = if (daily) Random(today.toEpochDay() * SEED_MIX) else Random(System.nanoTime())
                    val picks = names.shuffled(random).take(ROUNDS)
                    riddles =
                        picks.mapNotNull { (id, name) ->
                            val clues = withContext(WholphinDispatchers.IO) { clues(details(id)) }
                            if (clues.isEmpty()) return@mapNotNull null
                            val others =
                                names
                                    .map { it.second }
                                    .filter { it != name }
                                    .shuffled(random)
                                    .take(CHOICES - 1)
                            Riddle(name, clues, (others + name).shuffled(random))
                        }
                    results.clear()
                    showRiddle(0)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Daily riddle couldn't load")
                    _state.value = RiddleState.Problem("Couldn't reach the server. Try again in a moment.")
                }
            }
        }

        private fun showRiddle(round: Int) {
            val riddle = riddles.getOrNull(round) ?: return finishGame()
            _state.value =
                RiddleState.Playing(
                    round = round,
                    rounds = riddles.size,
                    clues = riddle.clues.take(1),
                    choices = riddle.choices,
                    score = results.sumOf { it.second },
                    daily = daily,
                    answer = riddle.answer,
                )
        }

        fun pick(choice: String) {
            val playing = _state.value as? RiddleState.Playing ?: return
            if (playing.result != null || choice in playing.wrong) return
            val riddle = riddles[playing.round]
            val stars =
                when {
                    choice == riddle.answer -> (riddle.clues.size - playing.clues.size + 1).coerceIn(1, MAX_STARS)
                    playing.clues.size >= riddle.clues.size -> 0
                    else -> null
                }
            if (stars == null) {
                _state.value =
                    playing.copy(clues = riddle.clues.take(playing.clues.size + 1), wrong = playing.wrong + choice)
            } else {
                results.add(riddle.answer to stars)
                _state.value =
                    playing.copy(
                        result = stars,
                        score = playing.score + stars,
                        wrong = if (stars == 0) playing.wrong + choice else playing.wrong,
                    )
            }
        }

        /** After a riddle is over: the next one, or the final score */
        fun next() {
            val playing = _state.value as? RiddleState.Playing ?: return
            if (playing.result == null) return
            showRiddle(playing.round + 1)
        }

        private fun finishGame() {
            if (daily) prefs.edit { putInt(dayKey(today), results.sumOf { it.second }) }
            _state.value = RiddleState.Done(results.toList(), daily, streak())
        }

        /** Days in a row with at least one star, counting back from today (or yesterday if today isn't played yet) */
        private fun streak(): Int {
            var day = if (prefs.getInt(dayKey(today), NOT_PLAYED) == NOT_PLAYED) today.minusDays(1) else today
            var count = 0
            while (prefs.getInt(dayKey(day), NOT_PLAYED) > 0) {
                count++
                day = day.minusDays(1)
            }
            return count
        }

        /** Every movie's id and name, in a fixed order so everyone gets the same pick */
        private suspend fun movieNames(): List<Pair<java.util.UUID, String>> =
            api.itemsApi
                .getItems(
                    GetItemsRequest(
                        userId = serverRepository.currentUser?.id,
                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                        recursive = true,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                        enableImages = false,
                        enableUserData = false,
                    ),
                ).content.items
                .mapNotNull { item -> item.name?.takeIf { it.isNotBlank() }?.let { item.id to it } }
                .distinctBy { it.second }

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

            fun hide(text: String) = if (name.length > 2) text.replace(name, "▇▇▇▇", ignoreCase = true) else text
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
            private const val CHOICES = 5
            const val ROUNDS = 5
            const val MAX_STARS = 3
            private const val NOT_PLAYED = -1
            private const val SEED_MIX = 7_919L
        }
    }

@Composable
fun DailyRiddlePage(
    modifier: Modifier = Modifier,
    viewModel: DailyRiddleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isTv = rememberIsTv()
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier =
            modifier
                .fillMaxSize()
                .background(Color(0xFF0B0B0D))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isTv) 48.dp else 16.dp, vertical = if (isTv) 24.dp else 12.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isTv) 16.dp else 12.dp),
            modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
        ) {
            Text(
                text = "DAILY MOVIE RIDDLE",
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
                    Text(
                        text =
                            (if (s.daily) "Today's riddles" else "Bonus round") +
                                "  ·  Movie ${s.round + 1} of ${s.rounds}  ·  ★ ${s.score}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFFC94D),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Which movie is it? A wrong pick shows an easier clue.",
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
                                    .padding(if (isTv) 20.dp else 16.dp),
                        ) {
                            Text(
                                text = "Clue ${i + 1}\n$clue",
                                fontSize = if (isTv) 22.sp else 18.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    val firstFocus = remember { FocusRequester() }
                    val nextFocus = remember { FocusRequester() }
                    LaunchedEffect(s.round, s.wrong.size, s.result) {
                        if (s.result == null) firstFocus.tryRequestFocus() else nextFocus.tryRequestFocus()
                    }
                    val open = s.choices.filterNot { it in s.wrong }
                    // Five titles: rows of three on TV (3 + 2), one per row on phones
                    s.choices.chunked(if (isTv) 3 else 1).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(if (isTv) 0.85f else 1f),
                        ) {
                            row.forEach { choice ->
                                val wrong = choice in s.wrong
                                val isAnswer = s.result != null && choice == s.answer
                                Button(
                                    onClick = { viewModel.pick(choice) },
                                    enabled = !wrong && s.result == null,
                                    colors =
                                        ButtonDefaults.colors(
                                            containerColor = Color(0xFF2A2A2E),
                                            contentColor = Color.White,
                                            focusedContainerColor = Color.White,
                                            focusedContentColor = Color.Black,
                                            disabledContainerColor =
                                                when {
                                                    isAnswer -> Color(0xFF2E8B57)
                                                    wrong -> BenflixRed.copy(alpha = .35f)
                                                    else -> Color(0xFF2A2A2E).copy(alpha = .5f)
                                                },
                                            disabledContentColor = if (isAnswer) Color.White else Color.White.copy(alpha = .5f),
                                        ),
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .then(if (choice == open.firstOrNull()) Modifier.focusRequester(firstFocus) else Modifier),
                                ) {
                                    Text(text = choice, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                    s.result?.let { stars ->
                        Text(
                            text =
                                if (stars > 0) {
                                    "Got it! " + "★".repeat(stars) + "☆".repeat(DailyRiddleViewModel.MAX_STARS - stars)
                                } else {
                                    "It was ${s.answer}"
                                },
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (stars > 0) Color(0xFF46D37A) else BenflixRed,
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = viewModel::next, modifier = Modifier.focusRequester(nextFocus)) {
                            Text(if (s.round + 1 < s.rounds) "Next movie" else "See my score")
                        }
                    }
                }

                is RiddleState.Done -> {
                    val stars = s.results.sumOf { it.second }
                    val guessed = s.results.count { it.second > 0 }
                    Text(
                        text = "★ $stars of ${s.results.size * DailyRiddleViewModel.MAX_STARS}",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFC94D),
                    )
                    Text(
                        text = "You guessed $guessed of ${s.results.size} movies",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth(if (isTv) 0.6f else 1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1C1C1F))
                                .padding(16.dp),
                    ) {
                        s.results.forEach { (name, won) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    name,
                                    fontSize = 17.sp,
                                    color = Color.White,
                                    maxLines = 2,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "★".repeat(won) + "☆".repeat(DailyRiddleViewModel.MAX_STARS - won),
                                    fontSize = 17.sp,
                                    color = Color(0xFFFFC94D),
                                )
                            }
                        }
                    }
                    if (s.daily && s.streak > 1) {
                        Text("${s.streak} days in a row!", fontSize = 18.sp, color = Color(0xFF46D37A))
                    }
                    if (s.daily) {
                        Text(
                            "Everyone gets the same first set each day, so ask the family how they did!",
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = .75f),
                            textAlign = TextAlign.Center,
                        )
                    }
                    FocusedButton("Play again with new movies", viewModel::load)
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
