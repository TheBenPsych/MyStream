package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.HomeItemFields
import com.github.damontecres.wholphin.ui.cards.GridCard
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import javax.inject.Inject

/** An answer to the group quiz's first question, and the server genres it means */
enum class QuizMood(
    val title: String,
    val genres: List<String>,
) {
    FUNNY("Funny", listOf("Comedy")),
    EXCITING("Exciting", listOf("Action", "Adventure", "Thriller")),
    SCARY("Scary", listOf("Horror")),
    FEEL_GOOD("Feel-good", listOf("Family", "Romance", "Music")),
    MYSTERY("Mystery", listOf("Mystery", "Crime")),
    ROMANTIC("Romantic", listOf("Romance")),
    ANIMATED("Animated", listOf("Animation", "Anime")),
    MIND_BENDING("Mind-bending", listOf("Science Fiction", "Fantasy")),
    SURPRISE("Surprise us", emptyList()),
}

/** An answer to the second question: how long and what kind */
enum class QuizKind(
    val title: String,
) {
    SHORT("Under 30 min"),
    UNDER_TWO("Under 2 hours"),
    LONG("Long one"),
    MOVIE("Movie"),
    SHOW("Show"),
    ANY("Any"),
}

/** One result, and how many of the chosen moods it matches */
data class QuizResult(
    val item: BaseItem,
    val matches: Int,
    val matchesAll: Boolean,
)

@HiltViewModel
class MoodQuizViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        /** null while loading */
        private val _results = MutableStateFlow<List<QuizResult>?>(null)
        val results: StateFlow<List<QuizResult>?> = _results

        fun find(
            moods: Set<QuizMood>,
            kinds: Set<QuizKind>,
        ) {
            _results.value = null
            viewModelScope.launch(WholphinDispatchers.IO) {
                _results.value =
                    try {
                        val genres = moods.flatMap { it.genres }.distinct()
                        val wantMovie = QuizKind.MOVIE in kinds
                        val wantShow = QuizKind.SHOW in kinds
                        val types =
                            when {
                                wantMovie && !wantShow -> listOf(BaseItemKind.MOVIE)
                                wantShow && !wantMovie -> listOf(BaseItemKind.SERIES)
                                QuizKind.SHORT in kinds && !wantMovie -> listOf(BaseItemKind.SERIES)
                                else -> listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)
                            }
                        val items =
                            api.itemsApi
                                .getItems(
                                    GetItemsRequest(
                                        userId = serverRepository.currentUser?.id,
                                        includeItemTypes = types,
                                        recursive = true,
                                        genres = genres.ifEmpty { null },
                                        sortBy = listOf(ItemSortBy.RANDOM),
                                        limit = QUIZ_FETCH,
                                        fields = HomeItemFields + listOf(org.jellyfin.sdk.model.api.ItemFields.GENRES),
                                    ),
                                ).content.items
                                .map { BaseItem(it) }
                        val realMoods = moods.filter { it.genres.isNotEmpty() }
                        items
                            .filter { fitsTime(it, kinds) }
                            .map { item ->
                                val itemGenres =
                                    item.data.genres
                                        .orEmpty()
                                        .toSet()
                                val matches = realMoods.count { mood -> mood.genres.any { it in itemGenres } }
                                QuizResult(item, matches, realMoods.size > 1 && matches == realMoods.size)
                            }.sortedWith(
                                compareByDescending<QuizResult> { it.matches }
                                    .thenByDescending {
                                        it.item.data.userData
                                            ?.played != true
                                    }.thenByDescending { it.item.data.communityRating ?: 0f },
                            ).take(QUIZ_MAX)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.e(ex, "Mood quiz failed")
                        emptyList()
                    }
            }
        }

        private fun fitsTime(
            item: BaseItem,
            kinds: Set<QuizKind>,
        ): Boolean {
            val minutes = (item.data.runTimeTicks ?: 0L) / 600_000_000L
            if (minutes <= 0) return true
            val time = kinds.filter { it == QuizKind.SHORT || it == QuizKind.UNDER_TWO || it == QuizKind.LONG }
            if (time.isEmpty()) return true
            return time.any {
                when (it) {
                    QuizKind.SHORT -> minutes <= 30
                    QuizKind.UNDER_TWO -> minutes < 120
                    else -> minutes >= 120
                }
            }
        }
    }

/**
 * "What's the group in the mood for?" Two quick questions (pick as many answers as you like),
 * then the best matches for everyone
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoodQuizPage(
    modifier: Modifier = Modifier,
    viewModel: MoodQuizViewModel = hiltViewModel(),
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val moods = remember { mutableStateListOf<QuizMood>() }
    val kinds = remember { mutableStateListOf<QuizKind>() }
    val firstFocus = remember(step) { FocusRequester() }
    LaunchedEffect(step) {
        delay(80)
        firstFocus.tryRequestFocus()
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.padding(top = 16.dp, start = 8.dp),
    ) {
        when (step) {
            0, 1 -> {
                Text(
                    text = "QUESTION ${step + 1} OF 2",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                    color = Color(0xFFA69C9D),
                )
                Text(
                    text = if (step == 0) "WHAT'S THE GROUP IN THE MOOD FOR?" else "HOW MUCH TIME, AND WHAT KIND?",
                    fontFamily = BenflixTitleFont,
                    fontSize = 48.sp,
                    letterSpacing = 1.sp,
                )
                Text("Pick as many as you like.", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(.8f),
                ) {
                    if (step == 0) {
                        QuizMood.entries.forEachIndexed { i, mood ->
                            QuizChip(
                                text = mood.title,
                                on = mood in moods,
                                onClick = { if (mood in moods) moods.remove(mood) else moods.add(mood) },
                                modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            )
                        }
                    } else {
                        QuizKind.entries.forEachIndexed { i, kind ->
                            QuizChip(
                                text = kind.title,
                                on = kind in kinds,
                                onClick = { if (kind in kinds) kinds.remove(kind) else kinds.add(kind) },
                                modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            )
                        }
                    }
                }
                val count = if (step == 0) moods.size else kinds.size
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            if (step == 0) {
                                step = 1
                            } else {
                                viewModel.find(moods.toSet(), kinds.toSet())
                                step = 2
                            }
                        },
                    ) {
                        Text(if (step == 0) "Next" else "Show picks")
                    }
                    if (step == 1) {
                        Button(onClick = { step = 0 }) { Text("Back") }
                    }
                    Text(
                        text = if (count == 0) "Nothing picked: anything goes" else "$count picked",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            else -> {
                QuizResults(
                    title =
                        moods
                            .filter { it != QuizMood.SURPRISE }
                            .joinToString(" or ") { it.title.lowercase() }
                            .ifEmpty { "Something for everyone" },
                    viewModel = viewModel,
                    firstFocus = firstFocus,
                    onChangeAnswers = { step = 0 },
                )
            }
        }
    }
}

@Composable
private fun QuizResults(
    title: String,
    viewModel: MoodQuizViewModel,
    firstFocus: FocusRequester,
    onChangeAnswers: () -> Unit,
) {
    val results by viewModel.results.collectAsState()
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = title.uppercase(),
            fontFamily = BenflixTitleFont,
            fontSize = 44.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onChangeAnswers) { Text("Change answers") }
    }
    when (val list = results) {
        null -> {
            LoadingPage(Modifier.fillMaxSize())
        }

        else -> {
            if (list.isEmpty()) {
                Text("Nothing matched. Try fewer answers.", fontSize = 18.sp)
            } else {
                LaunchedEffect(list) {
                    delay(80)
                    firstFocus.tryRequestFocus()
                }
                Text(
                    text = "Best matches first. Ones that fit every mood say so.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.item.id }) { result ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            GridCard(
                                item = result.item,
                                onClick = { viewModel.navigationManager.navigateTo(result.item.destination()) },
                                onLongClick = {},
                                showTitle = false,
                                modifier = if (result == list.first()) Modifier.focusRequester(firstFocus) else Modifier,
                            )
                            if (result.matchesAll) {
                                Text(
                                    text = "Fits every mood",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizChip(
    text: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        colors =
            ButtonDefaults.colors(
                containerColor = if (on) Color(0xFF2A1416) else Color(0xFF1A1616),
                contentColor = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        border =
            ButtonDefaults.border(
                border =
                    if (on) {
                        androidx.tv.material3.Border(
                            androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFE5303A)),
                            shape = RoundedCornerShape(8.dp),
                        )
                    } else {
                        androidx.tv.material3.Border.None
                    },
            ),
        modifier = modifier.width(180.dp),
    ) {
        Text(text = if (on) "✓  $text" else text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

private const val QUIZ_FETCH = 300
private const val QUIZ_MAX = 48
