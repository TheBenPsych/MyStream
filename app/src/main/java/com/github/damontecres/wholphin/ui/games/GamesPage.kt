package com.github.damontecres.wholphin.ui.games

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.GameConsole
import com.github.damontecres.wholphin.services.GameLibraryClient
import com.github.damontecres.wholphin.services.GameLibraryException
import com.github.damontecres.wholphin.services.GameStreamReturn
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PlayTarget
import com.github.damontecres.wholphin.services.ServerGame
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.preferences.StringInput
import com.github.damontecres.wholphin.ui.preferences.StringInputDialog
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * A built-in game shown on the Games page
 */
enum class BuiltInGame(
    val title: String,
    val tagline: String,
    val howTo: String,
    val remoteOk: Boolean,
    /** What a finger on a phone screen does in this game */
    val touch: TouchMode,
    val colors: List<Color>,
    val create: () -> MiniGame,
) {
    POPCORN_POP(
        title = "Popcorn Pop",
        tagline = "Bounce a popcorn kernel up a tower of soda cups and film reels. Grab the tickets!",
        howTo = "Remote or controller: ◀ ▶ to steer · Phone: hold left or right side",
        remoteOk = true,
        touch = TouchMode.STEER,
        colors = listOf(Color(0xFFE50914), Color(0xFF5C0A10)),
        create = { PopcornPop() },
    ),
    REEL_RUSH(
        title = "Reel Rush",
        tagline = "Fly the Benflix B through the gaps in the film strips.",
        howTo = "Press OK on the remote or A on a controller to flap · Phone: tap anywhere",
        remoteOk = true,
        touch = TouchMode.BUTTON,
        colors = listOf(Color(0xFF1B3F8A), Color(0xFF0D1B2A)),
        create = { ReelRush() },
    ),
    SNACK_CATCH(
        title = "Snack Catch",
        tagline = "Catch the falling snacks in your popcorn bucket. Watch out for spilled sodas!",
        howTo = "Remote or controller: ◀ ▶ to move · Phone: hold left or right side",
        remoteOk = true,
        touch = TouchMode.STEER,
        colors = listOf(Color(0xFF7B1FA2), Color(0xFF2B1036)),
        create = { SnackCatch() },
    ),
    USHER_DASH(
        title = "Usher Dash",
        tagline = "Run down the aisle and jump over popcorn spills and lost shoes.",
        howTo = "Press OK or A to jump (hold for a higher jump) · Phone: tap anywhere",
        remoteOk = true,
        touch = TouchMode.BUTTON,
        colors = listOf(Color(0xFFB0070F), Color(0xFF101828)),
        create = { UsherDash() },
    ),
    FILM_CAN_STACK(
        title = "Film Can Stack",
        tagline = "Drop sliding film cans to build the tallest tower. Line them up perfectly!",
        howTo = "Press OK or A to drop the can · Phone: tap anywhere",
        remoteOk = true,
        touch = TouchMode.BUTTON,
        colors = listOf(Color(0xFF00838F), Color(0xFF1C1424)),
        create = { FilmCanStack() },
    ),
    MATINEE_MATCH(
        title = "Matinee Match",
        tagline = "Flip the cards and find the matching movie pairs before time runs out.",
        howTo = "Arrows to move, OK or A to flip a card · Phone: tap a card",
        remoteOk = true,
        touch = TouchMode.TAP,
        colors = listOf(Color(0xFFF9A825), Color(0xFF5C3A00)),
        create = { MatineeMatch() },
    ),
}

/** What a finger on a phone screen does in a built-in game */
enum class TouchMode {
    /** Hold the left or right half of the screen to steer */
    STEER,

    /** Anywhere on the screen is the one button */
    BUTTON,

    /** Tap the exact thing you want (like a card) */
    TAP,
}

/** What the "Your games" part of the Games tab is showing */
sealed interface LibraryState {
    /** Kids profile, or not the server admin: streamed games are admin-only for now (home upload speed) */
    data object Hidden : LibraryState

    data object Loading : LibraryState

    data class Failed(
        val message: String,
        val askAddress: Boolean,
    ) : LibraryState

    data class Ready(
        val consoles: List<GameConsole>,
    ) : LibraryState
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
        private val games: GameLibraryClient,
    ) : ViewModel() {
        private val prefs = context.getSharedPreferences("benflix_games", Context.MODE_PRIVATE)

        private val _library = MutableStateFlow<LibraryState>(LibraryState.Loading)
        val library: StateFlow<LibraryState> = _library

        val gamesAddress: String get() = games.customAddress.ifBlank { games.address }
        val gamesToken: String get() = games.token

        /** The game being started right now, so a second press doesn't start two */
        private val _starting = MutableStateFlow<Int?>(null)
        val starting: StateFlow<Int?> = _starting

        init {
            reload()
        }

        fun reload() {
            val isAdmin = serverRepository.currentUserDto?.policy?.isAdministrator == true
            if (KidsMode.isActive || !isAdmin) {
                _library.value = LibraryState.Hidden
                return
            }
            _library.value = LibraryState.Loading
            viewModelScope.launch {
                _library.value =
                    try {
                        LibraryState.Ready(games.loadLibrary())
                    } catch (e: GameLibraryException) {
                        LibraryState.Failed(e.message ?: "Couldn't load your games.", e.askAddress)
                    } catch (e: Exception) {
                        Timber.w(e, "Loading the game library failed")
                        LibraryState.Failed("Couldn't load your games (${e.javaClass.simpleName}).", askAddress = false)
                    }
            }
        }

        fun setAddress(address: String) {
            games.saveAddress(address)
            reload()
        }

        /** Tells the server which game to start, then opens it in Moonlight */
        fun play(
            context: Context,
            game: ServerGame,
        ) {
            if (_starting.value != null) return
            _starting.value = game.id
            viewModelScope.launch {
                try {
                    openGameStream(context, game, games.prepare(game))
                } catch (e: GameLibraryException) {
                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Timber.w(e, "Starting a game failed")
                    Toast.makeText(context, "Couldn't start ${game.name}.", Toast.LENGTH_LONG).show()
                } finally {
                    _starting.value = null
                }
            }
        }

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
 * The Games tab: quick built-in games, plus your own game collection from RomM on the server
 */
@Composable
fun GamesPage(
    modifier: Modifier = Modifier,
    viewModel: GamesViewModel = hiltViewModel(),
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    val isTv = rememberIsTv()
    val context = LocalContext.current
    val library by viewModel.library.collectAsState()
    val starting by viewModel.starting.collectAsState()
    var askAddress by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = if (isTv) 48.dp else 0.dp,
                    end = if (isTv) 48.dp else 8.dp,
                    top = if (isTv) 24.dp else 8.dp,
                    bottom = 24.dp,
                ),
    ) {
        Text(
            text = "Benflix Games",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            if (isTv) "Quick games you can play with your TV remote." else "Quick games you can play by tapping.",
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

        // Your own games, from the RomM game library on the server (admin only for now)
        if (library != LibraryState.Hidden) {
            Text(
                text = "Your games",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp),
            )
            when (val state = library) {
                LibraryState.Hidden -> {}

                LibraryState.Loading -> {
                    InfoText("Loading your games…")
                }

                is LibraryState.Failed -> {
                    InfoText(state.message)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ActionTile(title = "Try again", subtitle = "Reload your games", onClick = { viewModel.reload() })
                        if (state.askAddress) {
                            ActionTile(title = "Set server address", subtitle = "Only if it can't find it", onClick = { askAddress = true })
                        }
                    }
                }

                is LibraryState.Ready -> {
                    if (state.consoles.isEmpty()) {
                        InfoText("Your game library is connected but has no games yet. Add some in RomM and run a scan.")
                    }
                    InfoText("Press a game to play it. Start + Select together quits and saves your spot. Hold Start for the game menu.")
                    state.consoles.forEach { console ->
                        Text(
                            text = "${console.name} (${console.games.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                        ) {
                            items(console.games, key = { it.id }) { game ->
                                ServerGameCard(
                                    game = game,
                                    token = viewModel.gamesToken,
                                    starting = starting == game.id,
                                    onClick = { viewModel.play(context, game) },
                                    modifier = Modifier.size(width = if (isTv) 150.dp else 110.dp, height = if (isTv) 210.dp else 154.dp),
                                )
                            }
                        }
                    }
                    ActionTile(title = "Reload games", subtitle = "After adding games in RomM", onClick = { viewModel.reload() })
                }
            }
        }
    }

    if (askAddress) {
        StringInputDialog(
            input =
                StringInput(
                    title = "Games server address (for example 10.0.0.58). Leave empty for automatic.",
                    value = viewModel.gamesAddress.removePrefix("http://"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    onSubmit = {},
                ),
            onSave = {
                askAddress = false
                viewModel.setAddress(it)
            },
            onDismissRequest = { askAddress = false },
        )
    }
}

@Composable
private fun InfoText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A simple wide button-like tile, e.g. "Connect game library" */
@Composable
private fun ActionTile(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
            ),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF2A2A2A), focusedContainerColor = Color(0xFF3A3A3A)),
        modifier = Modifier.size(width = 280.dp, height = 84.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = .8f))
        }
    }
}

/** A box art card for one of your own games */
@Composable
private fun ServerGameCard(
    game: ServerGame,
    token: String,
    starting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val context = LocalContext.current
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
            ),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1F1F1F), focusedContainerColor = Color(0xFF1F1F1F)),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize()) {
            // The name sits underneath, so it shows when there's no box art
            Text(
                text = game.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(10.dp),
            )
            if (game.coverUrl != null) {
                AsyncImage(
                    model =
                        remember(game.coverUrl) {
                            ImageRequest
                                .Builder(context)
                                .data(game.coverUrl)
                                .httpHeaders(NetworkHeaders.Builder().set("X-Benflix-Token", token).build())
                                .build()
                        },
                    contentDescription = game.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (starting || !game.playable) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = .65f)),
                ) {
                    Text(
                        text = if (starting) "Starting…" else "Can't play here yet",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

/**
 * Opens the game in Moonlight, which streams it from the server. Moonlight goes straight to the "Benflix" app
 * there, which starts the game the server was just told about. If Moonlight isn't installed, opens its store page.
 */
private fun openGameStream(
    context: Context,
    game: ServerGame,
    target: PlayTarget,
) {
    val direct =
        Intent()
            .setClassName(MOONLIGHT_PACKAGE, "$MOONLIGHT_PACKAGE.ShortcutTrampoline")
            .putExtra("AppName", target.appName)
            // A fresh Moonlight task holding only the game, so when the game ends you land back in Benflix
            // (not on Moonlight's own screens left over from opening Moonlight by hand)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    target.computerUuid?.let { direct.putExtra("UUID", it) }
    target.computerName?.let { direct.putExtra("Name", it) }
    try {
        GameStreamReturn.pending = true
        context.startActivity(direct)
        return
    } catch (e: Exception) {
        GameStreamReturn.pending = false
        Timber.w(e, "Couldn't open Moonlight directly")
    }
    val pm = context.packageManager
    val launch = pm.getLeanbackLaunchIntentForPackage(MOONLIGHT_PACKAGE) ?: pm.getLaunchIntentForPackage(MOONLIGHT_PACKAGE)
    if (launch != null) {
        Toast.makeText(context, "Opening ${game.name}: choose the Benflix app in Moonlight", Toast.LENGTH_LONG).show()
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return
    }
    Toast.makeText(context, "Install the free Moonlight app to play your games", Toast.LENGTH_LONG).show()
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$MOONLIGHT_PACKAGE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: Exception) {
        // No app store on this device; the message above explains what to do
    }
}

private const val MOONLIGHT_PACKAGE = "com.limelight"

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
        val textMeasurer = rememberTextMeasurer()
        // A still picture of the game, drawn by the game itself
        val preview = remember(game) { game.create() }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(game.colors)),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                preview.preparePreview(size.width, size.height)
                with(preview) { draw(textMeasurer) }
            }
            // Fade the picture on the left so the words stay easy to read
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to game.colors[1].copy(alpha = .92f),
                            0.5f to game.colors[1].copy(alpha = .55f),
                            1f to Color.Transparent,
                        ),
                    ),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (game.remoteOk) Badge(PlayWith.REMOTE, "Remote")
                    Badge(PlayWith.CONTROLLER, "Controller")
                    Badge(PlayWith.PHONE, "Phone")
                }
                Text(game.title, fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
                // At most two lines, so the score at the bottom never gets cut off
                Text(
                    game.tagline,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = .9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(.85f),
                )
                Box(Modifier.weight(1f))
                Text(
                    text = if (best > 0) "Your best: $best" else "No score yet",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier =
                        Modifier
                            .background(Color.Black.copy(alpha = .55f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
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
            fontSize = 40.sp,
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
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = .7f),
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
private fun GameCard(
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
                .background(Color.Black.copy(alpha = .7f), RoundedCornerShape(16.dp))
                .padding(horizontal = 40.dp, vertical = 24.dp),
    ) {
        Text(text = title, fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center)
        lines.forEach {
            Text(it, fontSize = 20.sp, color = if (highlight) Color(0xFFFFD54F) else Color.White, textAlign = TextAlign.Center)
        }
        Text(howTo, fontSize = 15.sp, color = Color.White.copy(alpha = .85f), textAlign = TextAlign.Center)
        Text(
            text = prompt,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE50914),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Plays the little game sound effects. They're short, so they're all loaded up front.
 */
private class GameSounds(
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
