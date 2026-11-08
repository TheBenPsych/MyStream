package com.github.damontecres.wholphin.ui.games

import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.BenflixHighScores
import com.github.damontecres.wholphin.services.HighScore
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.games.puzzles.AnimeMode
import com.github.damontecres.wholphin.ui.games.puzzles.PuzzleBg
import com.github.damontecres.wholphin.ui.games.puzzles.PuzzlePool
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What a game's own page shows, for a movie game or an arcade game */
data class GamePageInfo(
    val id: String,
    val title: String,
    val about: String,
    val steps: List<String>,
    val pills: List<String>,
    val boardKey: String?,
    val ranked: String,
    val colors: List<Color>,
    val movie: MovieGame?,
    val arcade: BuiltInGame?,
) {
    companion object {
        fun of(id: String): GamePageInfo? {
            val name = id.substringAfter(':')
            return if (id.startsWith("arcade:")) {
                val g = BuiltInGame.entries.firstOrNull { it.name == name } ?: return null
                GamePageInfo(
                    id = id,
                    title = g.title,
                    about = g.tagline,
                    steps = g.howTo.split(" · ").map { it.trim() },
                    pills =
                        listOfNotNull(
                            "1 player",
                            if (g.remoteOk) "Remote or Android phone" else "Controller or Android phone",
                            "About 2 min",
                        ),
                    boardKey = g.name,
                    ranked = "best score",
                    colors = g.colors,
                    movie = null,
                    arcade = g,
                )
            } else {
                val g = MovieGame.entries.firstOrNull { it.name == name } ?: return null
                GamePageInfo(
                    id = id,
                    title = g.title,
                    about = g.about,
                    steps = g.steps,
                    pills =
                        listOf(
                            g.players,
                            if (g.group == MovieGameGroup.PARTY) "Android phones join" else "Remote or Android phone",
                            g.minutes,
                        ),
                    boardKey = g.boardKey,
                    ranked = g.ranked,
                    colors = g.colors,
                    movie = g,
                    arcade = null,
                )
            }
        }
    }
}

@HiltViewModel
class GameInfoViewModel
    @Inject
    constructor(
        @param:ApplicationContext context: Context,
        private val pool: PuzzlePool,
        private val serverRepository: ServerRepository,
        private val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val prefs = context.getSharedPreferences("benflix_games", Context.MODE_PRIVATE)
        val anime: StateFlow<AnimeMode> = pool.anime
        private val _pictures = MutableStateFlow<List<String>>(emptyList())
        val pictures: StateFlow<List<String>> = _pictures

        fun load(info: GamePageInfo) {
            if (info.movie == null) return
            viewModelScope.launch {
                try {
                    val titles = pool.titles().shuffled()
                    _pictures.value =
                        when (info.movie) {
                            MovieGame.ACTOR -> {
                                titles.flatMap { t -> t.actors.mapNotNull { it.photo } }.distinct().take(6)
                            }

                            MovieGame.POSTER, MovieGame.TIMELINE, MovieGame.HIGHER_LOWER, MovieGame.CLOSEST -> {
                                titles
                                    .mapNotNull { it.poster }
                                    .take(
                                        6,
                                    )
                            }

                            else -> {
                                titles.mapNotNull { it.stills.lastOrNull() }.take(6)
                            }
                        }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (_: Exception) {
                }
            }
        }

        fun best(info: GamePageInfo): Int = info.boardKey?.let { prefs.getInt("${pool.profileKey()}_$it", 0) } ?: 0

        /** The top 3 from the last board this device saw */
        fun top(info: GamePageInfo): List<HighScore> {
            val key = info.boardKey ?: return emptyList()
            val user = serverRepository.currentUser ?: return emptyList()
            val board = prefs.getString("board_cache_${user.id}", null)?.let { BenflixHighScores.decodeBoard(it) } ?: return emptyList()
            return board[key].orEmpty().take(3)
        }

        fun nextAnime() = pool.setAnime(pool.anime.value.next())

        fun play(info: GamePageInfo) {
            info.arcade?.let { g ->
                prefs.edit().putString("last_${pool.profileKey()}", g.name).apply()
                navigationManager.navigateTo(Destination.PlayGame(g.name))
                return
            }
            val g = info.movie ?: return
            navigationManager.navigateTo(
                when (g) {
                    MovieGame.GUESS -> Destination.GuessTheMovie
                    MovieGame.RIDDLE -> Destination.DailyRiddle
                    MovieGame.HIGHER_LOWER -> Destination.HigherLower
                    else -> if (g.party != null) Destination.PartyHost(g.party.name) else Destination.MovieGamePlay(g.name)
                },
            )
        }

        fun join() = navigationManager.navigateTo(Destination.PartyJoin)
    }

/** A game's own page: a moving preview, what it is, how to play, the leaderboard and settings */
@Composable
fun GameInfoPage(
    id: String,
    modifier: Modifier = Modifier,
    viewModel: GameInfoViewModel = hiltViewModel(),
) {
    val info = remember(id) { GamePageInfo.of(id) } ?: return
    LaunchedEffect(info) { viewModel.load(info) }
    val isTv = rememberIsTv()
    val anime by viewModel.anime.collectAsState()
    val pictures by viewModel.pictures.collectAsState()
    val best = remember(info) { viewModel.best(info) }
    val top = remember(info) { viewModel.top(info) }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocus.tryRequestFocus() }

    val buttons: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { viewModel.play(info) },
                colors = ButtonDefaults.colors(containerColor = Color.White, contentColor = Color.Black),
                modifier = Modifier.focusRequester(playFocus),
                scale = ButtonDefaults.scale(focusedScale = 1f),
            ) {
                Text(
                    if (info.movie?.party != null) "▶ Host on this ${if (isTv) "TV" else "phone"}" else "▶ Play",
                    fontSize = BenflixType.lead,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (info.movie?.party != null && !isTv) {
                Button(onClick = {
                    viewModel.join()
                }, scale = ButtonDefaults.scale(focusedScale = 1f)) { Text("Join a TV's game", fontSize = BenflixType.lead) }
            }
        }
    }
    val sections =
        listOf<Pair<String, @Composable () -> Unit>>(
            "How to play" to {
                info.steps.forEachIndexed { i, s -> InfoLine("${i + 1}.  $s") }
            },
            (if (info.boardKey != null) "Leaderboard" else "Players") to {
                if (info.boardKey == null) {
                    InfoLine("Everyone joins with their own Android phone: Benflix › Games › Join party.")
                    InfoLine("iPhones can't join, because Benflix is only on Android.", dim = true)
                    InfoLine("Scores are kept for the game, then start fresh.")
                } else {
                    val medals = listOf("🥇", "🥈", "🥉")
                    if (top.isEmpty()) InfoLine("No scores yet. Be the first!")
                    top.forEachIndexed { i, h -> InfoPair("${medals[i]}  ${h.name}", "${h.score}") }
                    InfoPair("Your best", if (best > 0) "$best" else "None yet")
                    InfoLine("Ranked by ${info.ranked}", dim = true)
                }
            },
            "Settings" to {
                if (info.movie != null) {
                    if (KidsMode.isActive) {
                        InfoLine("Kids titles only")
                    } else {
                        Button(onClick = {
                            viewModel.nextAnime()
                        }, scale = ButtonDefaults.scale(focusedScale = 1f)) { Text(anime.label, fontSize = BenflixType.body) }
                        InfoLine("Uses everything on Tower. Press to change the anime mix.", dim = true)
                    }
                } else {
                    InfoLine(info.arcade?.howTo ?: "")
                }
            },
        )

    if (isTv) {
        // Benflix redesign (game page): like a movie's page. The picture fills the right of the screen
        // behind everything, the words and Play sit on the left, and the leaderboard and settings below.
        // The words are never squeezed into a fixed box, so Play can't be cut off; if it's all too tall
        // for the TV, FitScreen shrinks it.
        Box(modifier.fillMaxSize().background(PuzzleBg)) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.68f)
                    .align(Alignment.TopEnd)
                    .clipToBounds(),
            ) {
                Preview(info, pictures)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(0f to PuzzleBg, 0.45f to PuzzleBg.copy(alpha = .55f), 0.75f to Color.Transparent),
                    ),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.45f to Color.Transparent, 0.8f to PuzzleBg),
                    ),
                )
            }
            FitScreen(true) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 64.dp, end = 64.dp, top = 44.dp, bottom = 32.dp),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth(0.5f),
                    ) {
                        Text(
                            info.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = BenflixType.hero,
                            lineHeight = BenflixType.hero * 1.1f,
                            color = Color.White,
                        )
                        Pills(info.pills)
                        Text(
                            info.about,
                            fontSize = BenflixType.rowName,
                            lineHeight = BenflixType.rowName * 1.25f,
                            color = BenflixColors.Text,
                        )
                        // How to play as one short line under the description
                        if (info.steps.isNotEmpty()) {
                            Text(
                                info.steps.joinToString("  ·  "),
                                fontSize = BenflixType.body,
                                lineHeight = BenflixType.body * 1.35f,
                                color = BenflixColors.Soft,
                            )
                        }
                        buttons()
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    ) {
                        sections.drop(1).forEach { (title, body) -> Section(title, Modifier.weight(1f), body) }
                    }
                }
            }
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                modifier
                    .fillMaxSize()
                    .background(PuzzleBg)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clipToBounds()) {
                Preview(info, pictures)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to PuzzleBg)))
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(info.title, fontWeight = FontWeight.Bold, fontSize = BenflixType.pageTitle, color = Color.White)
                Pills(info.pills)
                Text(info.about, fontSize = BenflixType.lead, lineHeight = BenflixType.lead * 1.3f, color = BenflixColors.Text)
                buttons()
                sections.forEach { (title, body) -> Section(title, Modifier.fillMaxWidth(), body) }
            }
        }
    }
}

/** The moving preview: pictures from Tower drifting slowly and changing every few seconds (or the arcade game's own picture) */
@Composable
private fun Preview(
    info: GamePageInfo,
    pictures: List<String>,
) {
    val drift = rememberInfiniteTransition(label = "drift")
    val scale by drift.animateFloat(
        1f,
        1.12f,
        infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "scale",
    )
    val arcade = info.arcade
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(info.colors))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        if (arcade != null) {
            val textMeasurer = rememberTextMeasurer()
            val preview = remember(arcade) { arcade.create() }
            Canvas(Modifier.fillMaxSize()) {
                preview.preparePreview(size.width, size.height)
                with(preview) { draw(textMeasurer) }
            }
        } else if (pictures.isNotEmpty()) {
            var index by remember(pictures) { mutableIntStateOf(0) }
            LaunchedEffect(pictures) {
                while (true) {
                    delay(4_000)
                    index = (index + 1) % pictures.size
                }
            }
            Crossfade(targetState = pictures[index], animationSpec = tween(900), label = "preview") { url ->
                AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun Pills(pills: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pills.forEach {
            Text(
                it,
                fontSize = BenflixType.body,
                color = Color.White,
                modifier =
                    Modifier
                        .background(
                            Color.White.copy(alpha = .14f),
                            RoundedCornerShape(50),
                        ).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Section(
    title: String,
    modifier: Modifier,
    body: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            modifier
                .background(Color.White.copy(alpha = .06f), RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(title.uppercase(), fontSize = BenflixType.small, fontWeight = FontWeight.SemiBold, color = BenflixColors.Muted)
        body()
    }
}

@Composable
private fun InfoLine(
    text: String,
    dim: Boolean = false,
) {
    Text(
        text,
        fontSize = if (dim) BenflixType.small else BenflixType.lead,
        lineHeight = if (dim) 19.sp else 24.sp,
        color = Color.White.copy(alpha = if (dim) .6f else .92f),
    )
}

@Composable
private fun InfoPair(
    left: String,
    right: String,
) {
    Row(Modifier.fillMaxWidth()) {
        Text(left, fontSize = BenflixType.lead, color = Color.White, modifier = Modifier.weight(1f))
        Text(right, fontSize = BenflixType.lead, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
