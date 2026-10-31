package com.github.damontecres.wholphin.ui.games

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * A built-in game shown on the Games page
 */
enum class BuiltInGame(
    val title: String,
    val tagline: String,
    val howTo: String,
    val remoteOk: Boolean,
    /** On a phone, tapping the left or right half steers (instead of any tap = button) */
    val touchSteers: Boolean,
    val colors: List<Color>,
    val create: () -> MiniGame,
) {
    POPCORN_POP(
        title = "Popcorn Pop",
        tagline = "Bounce a popcorn kernel up a tower of soda cups and film reels.",
        howTo = "Remote or controller: ◀ ▶ to steer · Phone: hold left or right side",
        remoteOk = true,
        touchSteers = true,
        colors = listOf(Color(0xFFE50914), Color(0xFF5C0A10)),
        create = { PopcornPop() },
    ),
    REEL_RUSH(
        title = "Reel Rush",
        tagline = "Fly the Benflix B through the gaps in the film strips.",
        howTo = "Press OK on the remote or A on a controller to flap · Phone: tap anywhere",
        remoteOk = true,
        touchSteers = false,
        colors = listOf(Color(0xFF1B3F8A), Color(0xFF0D1B2A)),
        create = { ReelRush() },
    ),
}

/**
 * Best scores, kept on this device for each profile (the Kids tile has its own)
 */
@HiltViewModel
class GamesViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val prefs = context.getSharedPreferences("benflix_games", Context.MODE_PRIVATE)

        private fun key(game: BuiltInGame): String {
            val profile = if (KidsMode.isActive) "kids" else serverRepository.currentUser?.id?.toString() ?: "guest"
            return "${profile}_${game.name}"
        }

        fun best(game: BuiltInGame): Int = prefs.getInt(key(game), 0)

        /** Saves the score if it's a new best, and says whether it was */
        fun submit(
            game: BuiltInGame,
            score: Int,
        ): Boolean {
            if (score <= best(game)) return false
            prefs.edit().putInt(key(game), score).apply()
            return true
        }
    }

/**
 * The Games tab: built-in games for now, ROMs from the server later
 */
@Composable
fun GamesPage(
    modifier: Modifier = Modifier,
    viewModel: GamesViewModel = hiltViewModel(),
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    val isTv = rememberIsTv()
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.padding(start = if (isTv) 48.dp else 0.dp, end = if (isTv) 48.dp else 8.dp, top = if (isTv) 24.dp else 8.dp),
    ) {
        Text(
            text = "Benflix Games",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            if (isTv) {
                "Quick games you can play with your TV remote. Your own game collection is coming here later."
            } else {
                "Quick games you can play by tapping. Your own game collection is coming here later."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isTv) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp),
                modifier = Modifier.focusRequester(focusRequester),
            ) {
                items(BuiltInGame.entries) { game ->
                    GameTile(
                        game = game,
                        best = viewModel.best(game),
                        onClick = { viewModel.navigationManager.navigateTo(Destination.PlayGame(game.name)) },
                        modifier = Modifier.size(width = 320.dp, height = 190.dp),
                    )
                }
            }
        } else {
            // Phones: one big tile per game, stacked
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
            ) {
                BuiltInGame.entries.forEach { game ->
                    GameTile(
                        game = game,
                        best = viewModel.best(game),
                        onClick = { viewModel.navigationManager.navigateTo(Destination.PlayGame(game.name)) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GameTile(
    game: BuiltInGame,
    best: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
            ),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(game.colors))
                    .padding(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (game.remoteOk) Badge(PlayWith.REMOTE, "Remote")
                Badge(PlayWith.CONTROLLER, "Controller")
                Badge(PlayWith.PHONE, "Phone")
            }
            Text(game.title, fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text(game.tagline, fontSize = 13.sp, color = Color.White.copy(alpha = .9f))
            Box(Modifier.weight(1f))
            Text(
                text = if (best > 0) "Your best: $best" else "No score yet",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

private enum class PlayWith { REMOTE, CONTROLLER, PHONE }

/**
 * A small "works with" chip: an icon plus a word, e.g. a controller icon with "Controller"
 */
@Composable
private fun Badge(
    kind: PlayWith,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier =
            Modifier
                .background(Color.Black.copy(alpha = .55f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        when (kind) {
            PlayWith.REMOTE -> {
                RemoteIcon(Modifier.size(width = 8.dp, height = 15.dp))
            }

            PlayWith.CONTROLLER -> {
                Text(stringResource(R.string.fa_gamepad), fontFamily = FontAwesome, fontSize = 13.sp, color = Color.White)
            }

            PlayWith.PHONE -> {
                Text(stringResource(R.string.fa_mobile_screen), fontFamily = FontAwesome, fontSize = 13.sp, color = Color.White)
            }
        }
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

/**
 * A simple TV remote drawing: a rounded stick with a round OK button and two small buttons
 */
@Composable
private fun RemoteIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(Color.White, cornerRadius = CornerRadius(w * .35f, w * .35f))
        drawCircle(Color.Black, radius = w * .3f, center = Offset(w / 2, h * .27f))
        drawCircle(Color.Black, radius = w * .12f, center = Offset(w / 2, h * .6f))
        drawCircle(Color.Black, radius = w * .12f, center = Offset(w / 2, h * .8f))
    }
}

/**
 * Full screen game. Works with the TV remote, a controller, a keyboard, or touch.
 */
@Composable
fun PlayGamePage(
    gameName: String,
    modifier: Modifier = Modifier,
    viewModel: GamesViewModel = hiltViewModel(),
) {
    val info = remember(gameName) { BuiltInGame.entries.firstOrNull { it.name == gameName } ?: BuiltInGame.POPCORN_POP }
    val game = remember(info) { info.create() }
    var best by remember { mutableIntStateOf(viewModel.best(info)) }
    var newBest by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }

    // Game loop: one step per screen frame
    LaunchedEffect(game) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) game.update(((now - last) / 1_000_000_000f).coerceAtMost(0.05f))
                last = now
            }
        }
    }
    LaunchedEffect(game.phase) {
        if (game.phase == GamePhase.OVER) {
            newBest = viewModel.submit(info, game.score)
            best = viewModel.best(info)
        }
    }

    fun press(
        input: GameInput,
        down: Boolean,
    ) {
        if (game.phase != GamePhase.PLAYING) {
            if (down && (input == GameInput.ACTION || info.touchSteers)) {
                newBest = false
                game.start()
                if (input != GameInput.ACTION) game.input(input, true)
            }
        } else {
            game.input(input, down)
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    val input =
                        when (event.key) {
                            Key.DirectionLeft, Key.A -> GameInput.LEFT

                            Key.DirectionRight, Key.D -> GameInput.RIGHT

                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar,
                            Key.ButtonA, Key.DirectionUp, Key.W,
                            -> GameInput.ACTION

                            else -> null
                        } ?: return@onKeyEvent false
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            // Holding a button repeats key presses; only the first one counts as a flap
                            if (event.nativeKeyEvent.repeatCount == 0 || input != GameInput.ACTION) press(input, true)
                        }

                        KeyEventType.KeyUp -> {
                            press(input, false)
                        }
                    }
                    true
                }.pointerInput(info) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val input =
                            if (!info.touchSteers) {
                                GameInput.ACTION
                            } else if (down.position.x < size.width / 2f) {
                                GameInput.LEFT
                            } else {
                                GameInput.RIGHT
                            }
                        press(input, true)
                        waitForUpOrCancellation()
                        press(input, false)
                    }
                },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            game.setSize(size.width, size.height)
            // Reading frame makes the canvas redraw every frame
            game.frame
            with(game) { draw(textMeasurer) }
        }
        // Score in the corner
        Text(
            text = "${game.score}",
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
        )
        if (game.phase != GamePhase.PLAYING) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = .7f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 40.dp, vertical = 24.dp),
            ) {
                Text(
                    text = if (game.phase == GamePhase.OVER) "Game over" else info.title,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
                if (game.phase == GamePhase.OVER) {
                    Text(
                        text = if (newBest) "New best: ${game.score}! 🎉" else "Score ${game.score} · Best $best",
                        fontSize = 20.sp,
                        color = if (newBest) Color(0xFFFFD54F) else Color.White,
                    )
                } else if (best > 0) {
                    Text("Your best: $best", fontSize = 18.sp, color = Color.White)
                }
                Text(info.howTo, fontSize = 15.sp, color = Color.White.copy(alpha = .85f), textAlign = TextAlign.Center)
                Text(
                    text =
                        if (game.phase ==
                            GamePhase.OVER
                        ) {
                            "Press OK to play again · Back to leave"
                        } else {
                            "Press OK to start · Back to leave"
                        },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE50914),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
