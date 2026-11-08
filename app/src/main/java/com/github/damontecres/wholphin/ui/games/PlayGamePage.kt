package com.github.damontecres.wholphin.ui.games

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import timber.log.Timber

/**
 * Full screen game. Works with the TV remote, a controller, a keyboard, or touch.
 *
 * Back while playing pauses the game; Back again (or on the start / end card) leaves.
 */
@Composable
fun PlayGamePage(
    gameName: String,
    modifier: Modifier = Modifier,
    viewModel: GamesViewModel = hiltViewModel(),
) {
    val info = remember(gameName) { BuiltInGame.entries.firstOrNull { it.name == gameName } ?: BuiltInGame.POPCORN_POP }
    val game = remember(info) { info.create().apply { easy = KidsMode.isActive } }
    var best by remember { mutableIntStateOf(viewModel.best(info)) }
    var newBest by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    val context = LocalContext.current
    val view = LocalView.current
    val isTv = rememberIsTv()
    val sounds = remember { GameSounds(context) }
    DisposableEffect(sounds) { onDispose { sounds.release() } }

    // Pause when the app goes to the background (home button, a call, the TV's screensaver)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, game) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) game.pause()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Back mid-game pauses instead of leaving
    BackHandler(enabled = game.phase == GamePhase.PLAYING || game.phase == GamePhase.COUNTDOWN) { game.pause() }

    // Game loop: one step per screen frame
    LaunchedEffect(game) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) game.update(((now - last) / 1_000_000_000f).coerceAtMost(0.05f))
                last = now
            }
            if (game.sounds.isNotEmpty()) {
                game.sounds.forEach { sounds.play(it) }
                game.sounds.clear()
            }
            if (game.buzz) {
                game.buzz = false
                if (!isTv) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
        when (game.phase) {
            GamePhase.PLAYING -> {
                game.input(input, down)
            }

            // Steering can be held during the countdown so you're ready to go; the main button waits
            GamePhase.COUNTDOWN -> {
                if (input != GameInput.ACTION) game.input(input, down)
            }

            GamePhase.PAUSED -> {
                if (down && input == GameInput.ACTION) game.resume()
            }

            GamePhase.READY, GamePhase.OVER -> {
                // A short wait after game over, so the button you were mashing doesn't restart right away
                val ready = game.phase == GamePhase.READY || game.sinceOver > 0.7f
                if (down && ready && (input == GameInput.ACTION || info.touch == TouchMode.STEER)) {
                    newBest = false
                    game.start()
                    if (input != GameInput.ACTION) game.input(input, true)
                }
            }
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
                            Key.DirectionLeft, Key.A -> {
                                GameInput.LEFT
                            }

                            Key.DirectionRight, Key.D -> {
                                GameInput.RIGHT
                            }

                            Key.DirectionUp, Key.W -> {
                                if (game.upIsAction) GameInput.ACTION else GameInput.UP
                            }

                            Key.DirectionDown, Key.S -> {
                                GameInput.DOWN
                            }

                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar,
                            Key.ButtonA,
                            -> {
                                GameInput.ACTION
                            }

                            Key.MediaPlayPause, Key.MediaPause, Key.ButtonStart -> {
                                if (event.type == KeyEventType.KeyDown) {
                                    if (game.phase == GamePhase.PAUSED) game.resume() else game.pause()
                                }
                                return@onKeyEvent true
                            }

                            else -> {
                                null
                            }
                        } ?: return@onKeyEvent false
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            // Holding a button repeats key presses; only the first one counts as a press
                            if (event.nativeKeyEvent.repeatCount == 0 || input == GameInput.LEFT ||
                                input == GameInput.RIGHT
                            ) {
                                press(input, true)
                            }
                        }

                        KeyEventType.KeyUp -> {
                            press(input, false)
                        }
                    }
                    true
                }.pointerInput(info) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (info.touch == TouchMode.TAP && game.phase == GamePhase.PLAYING) {
                            game.tap(down.position.x, down.position.y)
                            waitForUpOrCancellation()
                            return@awaitEachGesture
                        }
                        val input =
                            if (info.touch != TouchMode.STEER) {
                                GameInput.ACTION
                            } else if (down.position.x < size.width / 2f) {
                                GameInput.LEFT
                            } else {
                                GameInput.RIGHT
                            }
                        // On the pause card a tap anywhere resumes
                        press(if (game.phase == GamePhase.PAUSED) GameInput.ACTION else input, true)
                        waitForUpOrCancellation()
                        press(input, false)
                    }
                },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            game.setSize(size.width, size.height)
            // Reading frame makes the canvas redraw every frame
            game.frame
            translate(game.shakeOffset.x, game.shakeOffset.y) {
                with(game) {
                    draw(textMeasurer)
                    drawEffects(textMeasurer)
                }
            }
        }
        // Score at the top
        Text(
            text = "${game.score}",
            fontSize = BenflixType.hero,
            fontWeight = FontWeight.Black,
            color = Color.White,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
        )
        if (game.easy) {
            Text(
                text = "Kids speed",
                fontSize = BenflixType.small,
                fontWeight = FontWeight.Bold,
                color = BenflixColors.Soft,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 20.dp, end = 20.dp),
            )
        }
        when (game.phase) {
            GamePhase.PLAYING -> {}

            GamePhase.COUNTDOWN -> {
                Text(
                    text = "${kotlin.math.ceil(game.countdown).toInt().coerceAtLeast(1)}",
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            GamePhase.PAUSED -> {
                GameCard(
                    title = "Paused",
                    lines = listOf("Score ${game.score}"),
                    howTo = info.howTo,
                    prompt = if (isTv) "Press OK to keep playing · Back to leave" else "Tap to keep playing · Back to leave",
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            GamePhase.READY, GamePhase.OVER -> {
                val over = game.phase == GamePhase.OVER
                GameCard(
                    title = if (over) "Game over" else info.title,
                    lines =
                        when {
                            over && newBest -> listOf("New best: ${game.score}! 🎉")
                            over -> listOf("Score ${game.score} · Best $best")
                            best > 0 -> listOf("Your best: $best")
                            else -> emptyList()
                        },
                    highlight = over && newBest,
                    howTo = info.howTo,
                    prompt =
                        if (isTv) {
                            if (over) "Press OK to play again · Back to leave" else "Press OK to start · Back to leave"
                        } else {
                            if (over) "Tap to play again · Back to leave" else "Tap to start · Back to leave"
                        },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

/** The dark card in the middle of the screen: start, paused, or game over */
@Composable
internal fun GameCard(
    title: String,
    lines: List<String>,
    howTo: String,
    prompt: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            modifier
                .padding(16.dp)
                .widthIn(max = 560.dp)
                .background(Color.Black.copy(alpha = .7f), RoundedCornerShape(16.dp))
                .padding(horizontal = 40.dp, vertical = 24.dp),
    ) {
        Text(text = title, fontSize = BenflixType.hero, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center)
        lines.forEach {
            Text(
                it,
                fontSize = BenflixType.rowName,
                color = if (highlight) Color(0xFFFFD54F) else Color.White,
                textAlign = TextAlign.Center,
            )
        }
        Text(howTo, fontSize = BenflixType.body, color = BenflixColors.Soft, textAlign = TextAlign.Center)
        Text(
            text = prompt,
            fontSize = BenflixType.body,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Plays the little game sound effects. They're short, so they're all loaded up front.
 */
internal class GameSounds(
    context: Context,
) {
    private val pool =
        SoundPool
            .Builder()
            .setMaxStreams(6)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            ).build()

    private val ids =
        mapOf(
            Sfx.POP to R.raw.game_pop,
            Sfx.BOOST to R.raw.game_boost,
            Sfx.FLAP to R.raw.game_flap,
            Sfx.POINT to R.raw.game_point,
            Sfx.COIN to R.raw.game_coin,
            Sfx.CRASH to R.raw.game_crash,
            Sfx.THUD to R.raw.game_thud,
            Sfx.FLIP to R.raw.game_flip,
            Sfx.TICK to R.raw.game_tick,
            Sfx.GO to R.raw.game_go,
            Sfx.MISS to R.raw.game_miss,
        ).mapValues { (_, res) -> pool.load(context, res, 1) }

    fun play(sfx: Sfx) {
        val id = ids[sfx] ?: return
        val volume = if (sfx == Sfx.FLAP || sfx == Sfx.POP) 0.45f else 0.7f
        try {
            pool.play(id, volume, volume, 1, 0, 1f)
        } catch (e: Exception) {
            Timber.w(e, "Couldn't play game sound")
        }
    }

    fun release() {
        pool.release()
    }
}
