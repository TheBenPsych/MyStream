package com.github.damontecres.wholphin.ui.games

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.games.puzzles.LocalPuzzleAccent
import com.github.damontecres.wholphin.ui.games.puzzles.PuzzleHeader
import com.github.damontecres.wholphin.ui.games.puzzles.PuzzleScreen
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixFont
import com.github.damontecres.wholphin.ui.theme.BenflixRed
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import javax.inject.Inject

/**
 * What a round compares (Ben's pick: Higher or Lower modes). Each mode keeps its own best streak.
 * IMDb was the only one before, so it keeps the old best and is the one on the "Highest scores" board.
 */
enum class HigherLowerMode(
    val title: String,
    val question: String,
    val short: String,
) {
    IMDB("IMDb rating", "Is the second one rated higher or lower on IMDb?", "IMDb"),
    YEAR("Release year", "Did the second one come out later or earlier?", "Year"),
    LENGTH("Running time", "Is the second one longer or shorter?", "Length"),
    CRITICS("Rotten Tomatoes", "Did critics score the second one higher or lower?", "Critics"),
    MIX("Mix", "A different question every round.", "Mix"),
    ;

    /** The words on the two buttons */
    val up: String get() =
        when (this) {
            YEAR -> "▲ Later"
            LENGTH -> "▲ Longer"
            else -> "▲ Higher"
        }
    val down: String get() =
        when (this) {
            YEAR -> "▼ Earlier"
            LENGTH -> "▼ Shorter"
            else -> "▼ Lower"
        }
}

/**
 * A movie in the game. The IMDb rating ("community rating" in Jellyfin, from IMDb via the OMDb plugin)
 * is kept in tenths (7.4 = 74) so comparing is exact. [critics] is the Rotten Tomatoes score (0 to 100).
 */
data class ScoredMovie(
    val name: String,
    val year: Int?,
    val score: Int?,
    val poster: String?,
    val minutes: Int? = null,
    val critics: Int? = null,
    val backdrop: String? = null,
) {
    /** The rating as IMDb shows it, like "7.4" */
    val rating: String get() = score?.let { "${it / 10}.${it % 10}" } ?: "?"

    fun value(mode: HigherLowerMode): Int? =
        when (mode) {
            HigherLowerMode.IMDB -> score
            HigherLowerMode.YEAR -> year
            HigherLowerMode.LENGTH -> minutes
            HigherLowerMode.CRITICS -> critics
            HigherLowerMode.MIX -> null
        }

    /** The value as it shows on the poster, like "IMDb 7.4", "1999", "2h 16m" or "🍅 93%" */
    fun show(mode: HigherLowerMode): String =
        when (mode) {
            HigherLowerMode.IMDB -> "IMDb $rating"
            HigherLowerMode.YEAR -> "${year ?: "?"}"
            HigherLowerMode.LENGTH -> minutes?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m" } ?: "?"
            HigherLowerMode.CRITICS -> critics?.let { "🍅 $it%" } ?: "?"
            HigherLowerMode.MIX -> ""
        }

    /** What hides it before the answer */
    fun hidden(mode: HigherLowerMode): String =
        when (mode) {
            HigherLowerMode.IMDB -> "IMDb ?"
            HigherLowerMode.CRITICS -> "🍅 ?"
            else -> "?"
        }
}

sealed interface HigherLowerState {
    data object Loading : HigherLowerState

    data class NotEnough(
        val message: String,
    ) : HigherLowerState

    /** Picking a mode; [bests] is each mode's best streak, [counts] how many movies can play it */
    data class Choosing(
        val bests: Map<HigherLowerMode, Int>,
        val counts: Map<HigherLowerMode, Int>,
    ) : HigherLowerState

    data class Playing(
        val known: ScoredMovie,
        val guess: ScoredMovie,
        /** Right answers in a row so far */
        val streak: Int,
        /** This profile's best streak in this mode before this game */
        val best: Int,
        /** null until they answer; then whether they were right */
        val right: Boolean? = null,
        val mode: HigherLowerMode = HigherLowerMode.IMDB,
        /** What this round compares (in Mix it changes every round) */
        val stat: HigherLowerMode = HigherLowerMode.IMDB,
    ) : HigherLowerState

    data class Over(
        val streak: Int,
        val best: Int,
        val newBest: Boolean,
        val last: Pair<ScoredMovie, ScoredMovie>,
        val mode: HigherLowerMode = HigherLowerMode.IMDB,
        val stat: HigherLowerMode = HigherLowerMode.IMDB,
    ) : HigherLowerState
}

/**
 * Benflix "Higher or Lower": two movies face off. You see the first one's number (IMDb rating,
 * release year, running time or Rotten Tomatoes score); guess whether the second one's is higher or
 * lower. It keeps going until the first wrong guess, and each mode keeps its own best streak.
 */
@HiltViewModel
class HigherLowerViewModel
    @Inject
    constructor(
        @param:ApplicationContext context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val imageUrlService: ImageUrlService,
        private val puzzlePool: com.github.damontecres.wholphin.ui.games.puzzles.PuzzlePool,
    ) : ViewModel() {
        private val prefs = context.getSharedPreferences("benflix_games", Context.MODE_PRIVATE)
        private val _state = MutableStateFlow<HigherLowerState>(HigherLowerState.Loading)
        val state: StateFlow<HigherLowerState> = _state

        private var deck: ArrayDeque<ScoredMovie> = ArrayDeque()
        private var pool: List<ScoredMovie> = emptyList()
        private var mode = HigherLowerMode.IMDB

        init {
            start()
        }

        /** Back to the mode picker (loads the movies the first time) */
        fun start() {
            _state.value = HigherLowerState.Loading
            viewModelScope.launch {
                try {
                    // Reuse the movies already loaded; "Play again" just deals a new shuffle
                    val movies = pool.ifEmpty { withContext(WholphinDispatchers.IO) { loadMovies() } }
                    pool = movies
                    val counts = HigherLowerMode.entries.associateWith { m -> playable(m).size }
                    if (counts.values.none { it >= MIN_MOVIES }) {
                        _state.value =
                            HigherLowerState.NotEnough("You need at least $MIN_MOVIES movies with IMDb ratings to play.")
                        return@launch
                    }
                    _state.value = HigherLowerState.Choosing(HigherLowerMode.entries.associateWith { best(it) }, counts)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Higher or Lower couldn't load")
                    _state.value = HigherLowerState.NotEnough("Couldn't load movies from Tower. Try again in a moment.")
                }
            }
        }

        private fun playable(m: HigherLowerMode): List<ScoredMovie> =
            if (m == HigherLowerMode.MIX) {
                pool.filter { mv -> MIXED.count { mv.value(it) != null } >= 2 }
            } else {
                pool.filter { it.value(m) != null }
            }

        fun play(m: HigherLowerMode) {
            mode = m
            val movies = playable(m)
            if (movies.size < MIN_MOVIES) return
            deck = ArrayDeque(puzzlePool.freshFirst(deckKey(), movies) { it.name })
            val known = deal()
            val guess = deal()
            _state.value = HigherLowerState.Playing(known, guess, 0, best(m), mode = m, stat = statFor(known, guess))
        }

        /** Same mode again */
        fun again() = play(mode)

        /** What to compare: the mode itself, or in Mix a random one both movies have */
        private fun statFor(
            a: ScoredMovie,
            b: ScoredMovie,
        ): HigherLowerMode =
            if (mode != HigherLowerMode.MIX) {
                mode
            } else {
                MIXED.filter { a.value(it) != null && b.value(it) != null }.randomOrNull() ?: HigherLowerMode.YEAR
            }

        fun answer(higher: Boolean) {
            val playing = _state.value as? HigherLowerState.Playing ?: return
            if (playing.right != null) return
            val diff = (playing.guess.value(playing.stat) ?: 0) - (playing.known.value(playing.stat) ?: 0)
            // A tie counts as right either way
            val right = diff == 0 || (diff > 0) == higher
            val streak = if (right) playing.streak + 1 else playing.streak
            _state.value = playing.copy(right = right, streak = streak)
            viewModelScope.launch {
                delay(REVEAL_MS)
                if (right) {
                    // Endless: when every movie has been shown, shuffle them all back in
                    if (deck.isEmpty()) {
                        deck = ArrayDeque(puzzlePool.freshFirst(deckKey(), playable(mode).filter { it != playing.guess }) { it.name })
                    }
                    var next = deal()
                    // In a single mode both need the number; Mix only needs one they share
                    val stat = statFor(playing.guess, next)
                    if (playing.guess.value(stat) == null && deck.isNotEmpty()) next = deal()
                    _state.value =
                        HigherLowerState.Playing(
                            playing.guess,
                            next,
                            streak,
                            playing.best,
                            mode = mode,
                            stat = statFor(playing.guess, next),
                        )
                } else {
                    val best = best(mode)
                    val newBest = streak > best
                    if (newBest) prefs.edit { putInt(bestKey(mode), streak) }
                    _state.value =
                        HigherLowerState.Over(
                            streak,
                            maxOf(best, streak),
                            newBest,
                            playing.known to playing.guess,
                            mode,
                            playing.stat,
                        )
                }
            }
        }

        private fun deal(): ScoredMovie = deck.removeFirst().also { puzzlePool.saw(deckKey(), it.name) }

        private fun deckKey() = if (mode == HigherLowerMode.IMDB) BOARD_KEY else "${BOARD_KEY}_${mode.name}"

        fun best(m: HigherLowerMode = HigherLowerMode.IMDB): Int = prefs.getInt(bestKey(m), 0)

        private fun bestKey(m: HigherLowerMode): String {
            // Guest scores are kept apart from the owner's, like the Movie riddle
            val profile =
                when {
                    GuestMode.isActive -> "guest"
                    KidsMode.isActive -> "kids"
                    else -> serverRepository.currentUser?.id?.toString() ?: "guest"
                }
            // Best streak (right answers in a row). IMDb keeps the key it always had, so old bests stay.
            return if (m == HigherLowerMode.IMDB) "${profile}_$BOARD_KEY" else "${profile}_${BOARD_KEY}_${m.name}"
        }

        private suspend fun loadMovies(): List<ScoredMovie> =
            // "Anime only" loads straight from the anime libraries, so there's always enough to play
            (puzzlePool.animeParents() ?: listOf(null))
                .flatMap { parent ->
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = serverRepository.currentUser?.id,
                                parentId = parent,
                                includeItemTypes = puzzlePool.animeTypes(),
                                recursive = true,
                                imageTypes = listOf(ImageType.PRIMARY),
                                sortBy = listOf(ItemSortBy.RANDOM),
                                limit = POOL_SIZE,
                            ),
                        ).content
                        .items
                }.let { items ->
                    val keep = puzzlePool.animeKeeper()
                    items.filter { keep(it.id) }
                }.mapNotNull { dto ->
                    val score = dto.communityRating?.let { Math.round(it * 10) }?.takeIf { it > 0 }
                    val minutes = dto.runTimeTicks?.let { (it / 600_000_000L).toInt() }?.takeIf { it >= 20 }
                    val critics = dto.criticRating?.let { Math.round(it) }?.takeIf { it in 1..100 }
                    if (score == null && minutes == null && critics == null && dto.productionYear == null) return@mapNotNull null
                    ScoredMovie(
                        name = dto.name ?: return@mapNotNull null,
                        year = dto.productionYear,
                        score = score,
                        minutes = minutes,
                        critics = critics,
                        poster =
                            imageUrlService.getItemImageUrl(
                                itemId = dto.id,
                                imageType = ImageType.PRIMARY,
                                maxWidth = POSTER_WIDTH,
                                tag = dto.imageTags?.get(ImageType.PRIMARY),
                            ),
                        backdrop =
                            dto.backdropImageTags?.firstOrNull()?.let { tag ->
                                imageUrlService.getItemImageUrl(
                                    itemId = dto.id,
                                    imageType = ImageType.BACKDROP,
                                    maxWidth = BACKDROP_WIDTH,
                                    tag = tag,
                                )
                            },
                    )
                }.distinctBy { it.name }

        companion object {
            private const val POOL_SIZE = 200
            private const val MIN_MOVIES = 6

            /** The "Highest scores" board's key: best IMDb streak */
            const val BOARD_KEY = "HIGHER_LOWER_STREAK"
            private const val REVEAL_MS = 1_600L
            private const val POSTER_WIDTH = 420
            private const val BACKDROP_WIDTH = 960
            private val MIXED = listOf(HigherLowerMode.IMDB, HigherLowerMode.YEAR, HigherLowerMode.LENGTH, HigherLowerMode.CRITICS)
        }
    }

@Composable
fun HigherLowerPage(
    modifier: Modifier = Modifier,
    viewModel: HigherLowerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val isTv = rememberIsTv()
    CompositionLocalProvider(LocalPuzzleAccent provides MovieGame.HIGHER_LOWER.colors) {
        PuzzleScreen(isTv, modifier) { HigherLowerContent(state, isTv, viewModel) }
    }
}

@Composable
private fun HigherLowerContent(
    state: HigherLowerState,
    isTv: Boolean,
    viewModel: HigherLowerViewModel,
) {
    when (val s = state) {
        HigherLowerState.Loading -> {
            Text("Picking movies from your library…", fontSize = 20.sp, color = Color.White)
        }

        is HigherLowerState.NotEnough -> {
            HigherLowerEnd(title = "Higher or Lower", body = s.message, button = "Try again", onClick = viewModel::start)
        }

        is HigherLowerState.Choosing -> {
            HigherLowerModes(s, isTv, viewModel::play)
        }

        is HigherLowerState.Playing -> {
            HigherLowerRound(s, isTv, viewModel::answer)
        }

        is HigherLowerState.Over -> {
            val (a, b) = s.last
            HigherLowerEnd(
                title = if (s.newBest) "New best streak: ${s.streak}!" else "Streak over: ${s.streak} in a row",
                body =
                    "${b.name}: ${b.show(s.stat)}. ${a.name}: ${a.show(s.stat)}.\n" +
                        "Your best in ${s.mode.title}: ${s.best}",
                button = "Play again",
                onClick = viewModel::again,
                other = "Change mode",
                onOther = viewModel::start,
            )
        }
    }
}

/** The mode picker: one big button per mode, with its own best streak */
@Composable
private fun HigherLowerModes(
    s: HigherLowerState.Choosing,
    isTv: Boolean,
    onPick: (HigherLowerMode) -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.tryRequestFocus() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
    ) {
        PuzzleHeader("Higher or Lower", "Pick what to compare", isTv)
        HigherLowerMode.entries.forEachIndexed { i, m ->
            val enough = (s.counts[m] ?: 0) >= 6
            Button(
                onClick = { if (enough) onPick(m) },
                enabled = enough,
                colors =
                    ButtonDefaults.colors(
                        containerColor = Color(0xFF1A1E38),
                        contentColor = Color.White,
                        focusedContainerColor = BenflixColors.FocusFill,
                        focusedContentColor = BenflixColors.OnFocusFill,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                scale = ButtonDefaults.scale(focusedScale = 1f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(m.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (enough) m.question else "Not enough movies with this on Tower yet",
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text("Best ${s.bests[m] ?: 0}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFC94D))
                }
            }
        }
    }
}

@Composable
private fun HigherLowerRound(
    state: HigherLowerState.Playing,
    isTv: Boolean,
    onAnswer: (Boolean) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (isTv) 12.dp else 10.dp),
        modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(),
    ) {
        PuzzleHeader(
            "Higher or Lower",
            "${state.mode.short}  ·  Streak ${state.streak}  ·  Best ${maxOf(state.best, state.streak)}",
            isTv,
        )
        Text(
            text = state.stat.question,
            fontSize = if (isTv) 20.sp else 16.sp,
            color = BenflixColors.Soft,
            textAlign = TextAlign.Center,
        )
        // The face-off (Ben's pick): each movie on its own half with its background picture behind it
        // and a VS badge in the middle. Side by side on TV, one above the other on an upright phone.
        val fade = remember(state.guess.name) { Animatable(0f) }
        LaunchedEffect(state.guess.name) { fade.animateTo(1f, tween(GUESS_FADE_MS)) }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            if (isTv) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    FaceOffSide(state.known, state.stat, true, null, isTv, Modifier.weight(1f))
                    FaceOffSide(
                        state.guess,
                        state.stat,
                        state.right != null,
                        state.right,
                        isTv,
                        Modifier.weight(1f).graphicsLayer { alpha = fade.value },
                        fadeMs = GUESS_FADE_MS,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    FaceOffSide(state.known, state.stat, true, null, isTv, Modifier.fillMaxWidth())
                    FaceOffSide(
                        state.guess,
                        state.stat,
                        state.right != null,
                        state.right,
                        isTv,
                        Modifier.fillMaxWidth().graphicsLayer { alpha = fade.value },
                        fadeMs = GUESS_FADE_MS,
                    )
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(if (isTv) 64.dp else 48.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF22C55E), Color(0xFFEF4444))))
                        .border(3.dp, Color(0xFF0B0C10), CircleShape),
            ) {
                Text(
                    "VS",
                    fontFamily = BenflixFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isTv) 22.sp else 17.sp,
                    color = Color.White,
                )
            }
        }
        // The answer shows right above the buttons (the space is kept so nothing jumps)
        Text(
            text =
                when (state.right) {
                    // The name already shows on its side, so a long one can't push this off the line
                    true -> "Right! It's ${state.guess.show(state.stat)}"

                    false -> "Nope, it's ${state.guess.show(state.stat)}"

                    null -> " "
                },
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (state.right == true) Color(0xFF46D37A) else BenflixRed,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val first = remember(state.guess.name) { FocusRequester() }
        LaunchedEffect(state.guess.name) { first.tryRequestFocus() }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = if (isTv) Modifier.widthIn(max = 640.dp).fillMaxWidth() else Modifier.fillMaxWidth(),
        ) {
            listOf(true to state.stat.up, false to state.stat.down).forEachIndexed { i, (higher, label) ->
                Button(
                    onClick = { onAnswer(higher) },
                    colors =
                        ButtonDefaults.colors(
                            containerColor = if (higher) Color(0xFF14532D) else Color(0xFF7F1D1D),
                            contentColor = Color.White,
                            focusedContainerColor = BenflixColors.FocusFill,
                            focusedContentColor = BenflixColors.OnFocusFill,
                        ),
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(if (isTv) 52.dp else 48.dp)
                            .then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                    scale = ButtonDefaults.scale(focusedScale = 1f),
                ) {
                    Text(label, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** How long the new movie to guess takes to fade in (Ben asked for slower) */
private const val GUESS_FADE_MS = 1_200

/** One half of the face-off: the background picture dimmed, the poster, the name, year and number */
@Composable
private fun FaceOffSide(
    movie: ScoredMovie,
    stat: HigherLowerMode,
    showScore: Boolean,
    highlight: Boolean?,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    fadeMs: Int = 0,
) {
    val context = LocalContext.current
    val posterW = if (isTv) tvPictureHeight(0.34f) * (2f / 3f) else 78.dp
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1C1C1F)),
    ) {
        if (movie.backdrop != null) {
            AsyncImage(
                model = movie.backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().graphicsLayer { alpha = .45f },
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .25f), Color.Black.copy(alpha = .8f)))),
        )
        val poster: @Composable () -> Unit = {
            AsyncImage(
                model =
                    remember(movie.poster, fadeMs) {
                        ImageRequest
                            .Builder(context)
                            .data(movie.poster)
                            .crossfade(fadeMs)
                            .build()
                    },
                contentDescription = movie.name,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .width(posterW)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF26262B)),
            )
        }
        val words: @Composable (Alignment.Horizontal, TextAlign) -> Unit = { align, textAlign ->
            Column(horizontalAlignment = align, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Long names get smaller text and up to 3 lines so the whole name fits.
                val size = nameSize(movie.name)
                Text(
                    text = movie.name,
                    fontSize = size,
                    lineHeight = size * 1.2f,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    minLines = if (isTv) 2 else 1,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = textAlign,
                )
                if (stat != HigherLowerMode.YEAR) {
                    Text(text = movie.year?.toString() ?: " ", fontSize = 14.sp, color = BenflixColors.Soft, maxLines = 1)
                }
                Text(
                    text = if (showScore) movie.show(stat) else movie.hidden(stat),
                    fontSize = if (isTv) 26.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier =
                        Modifier
                            .background(
                                when (highlight) {
                                    true -> Color(0xFF2E8B57)
                                    false -> BenflixRed
                                    null -> Color.Black.copy(alpha = .55f)
                                },
                                RoundedCornerShape(10.dp),
                            ).padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }
        }
        if (isTv) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                poster()
                words(Alignment.CenterHorizontally, TextAlign.Center)
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) {
                poster()
                Box(Modifier.weight(1f)) { words(Alignment.Start, TextAlign.Start) }
            }
        }
    }
}

/** Shorter names stay big; long ones step down so they fit in three lines under a poster */
internal fun nameSize(name: String) =
    when {
        name.length <= 22 -> 17.sp
        name.length <= 38 -> 15.sp
        else -> 13.sp
    }

@Composable
private fun HigherLowerEnd(
    title: String,
    body: String,
    button: String,
    onClick: () -> Unit,
    other: String? = null,
    onOther: () -> Unit = {},
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(top = 24.dp),
    ) {
        Text(
            title,
            fontFamily = BenflixFont,
            fontWeight = FontWeight.Bold,
            fontSize = BenflixType.pageTitle,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(body, fontSize = 20.sp, color = BenflixColors.Soft, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onClick, modifier = Modifier.focusRequester(focus), scale = ButtonDefaults.scale(focusedScale = 1f)) {
                Text(button)
            }
            if (other != null) {
                Button(onClick = onOther, scale = ButtonDefaults.scale(focusedScale = 1f)) { Text(other) }
            }
        }
        Text(
            "Press Back to leave",
            style = MaterialTheme.typography.bodySmall,
            color = BenflixColors.Muted,
        )
    }
}
