package com.github.damontecres.wholphin.ui.games

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.StatFs
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.font.FontFamily
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
import com.github.damontecres.wholphin.games.DevicePower
import com.github.damontecres.wholphin.games.DownloadedGame
import com.github.damontecres.wholphin.games.GameRef
import com.github.damontecres.wholphin.games.GameSaves
import com.github.damontecres.wholphin.games.LocalConsole
import com.github.damontecres.wholphin.games.LocalGames
import com.github.damontecres.wholphin.games.PhonePadActivity
import com.github.damontecres.wholphin.services.BenflixHighScores
import com.github.damontecres.wholphin.services.GameConsole
import com.github.damontecres.wholphin.services.GameLibraryClient
import com.github.damontecres.wholphin.services.GameLibraryException
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.HighScore
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
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
import java.time.LocalDate
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
    /** Kids profile: your own games aren't shown there */
    data object Hidden : LibraryState

    data object Loading : LibraryState

    /** Tower couldn't be reached. Games already downloaded on this device can still be played. */
    data class Failed(
        val message: String,
        val askAddress: Boolean,
        val downloaded: List<DownloadedGame> = emptyList(),
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
        private val highScores: BenflixHighScores,
        val localGames: LocalGames,
    ) : ViewModel() {
        private val prefs = context.getSharedPreferences("benflix_games", Context.MODE_PRIVATE)

        private val _library = MutableStateFlow<LibraryState>(LibraryState.Loading)
        val library: StateFlow<LibraryState> = _library

        val gamesAddress: String get() = games.customAddress.ifBlank { games.address }
        val gamesToken: String get() = games.token

        /** The game being started right now, so a second press doesn't start two */
        private val _starting = MutableStateFlow<Int?>(null)
        val starting: StateFlow<Int?> = _starting

        /** The game whose Play / Resume choices are showing */
        private val _choice = MutableStateFlow<GameChoice?>(null)
        val choice: StateFlow<GameChoice?> = _choice

        /** "Highest scores": game id to every account's best, highest first (null while loading) */
        private val _board = MutableStateFlow<Map<String, List<HighScore>>?>(null)
        val board: StateFlow<Map<String, List<HighScore>>?> = _board

        /** Games with a save on Tower (played on any TV or phone), as (game id, when) pairs */
        private val _towerRecent = MutableStateFlow<List<Pair<Int, Long>>>(emptyList())
        val towerRecent: StateFlow<List<Pair<Int, Long>>> = _towerRecent

        /** Favourite games (the heart), per account */
        private val _favourites = MutableStateFlow<Set<Int>>(emptySet())
        val favourites: StateFlow<Set<Int>> = _favourites

        private fun favKey() = "fav_${serverRepository.currentUser?.id ?: "guest"}"

        fun toggleFavourite(id: Int) {
            val now = if (id in _favourites.value) _favourites.value - id else _favourites.value + id
            _favourites.value = now
            prefs.edit().putString(favKey(), now.joinToString(",")).apply()
        }

        /** The details page's story, year, players… for the game that's open */
        private val _info = MutableStateFlow<Pair<Int, GameInfo>?>(null)
        val info: StateFlow<Pair<Int, GameInfo>?> = _info
        private val infoCache = mutableMapOf<Int, GameInfo>()

        private fun loadInfo(id: Int) {
            infoCache[id]?.let {
                _info.value = id to it
                return
            }
            _info.value = null
            viewModelScope.launch {
                val got =
                    try {
                        val j = games.getJson("/api/info/$id")

                        fun list(name: String): List<String> {
                            val a = j.optJSONArray(name) ?: return emptyList()
                            return (0 until a.length()).map { a.optString(it) }
                        }
                        GameInfo(
                            summary = j.optString("summary"),
                            year = j.optInt("year"),
                            genres = list("genres"),
                            players = j.optString("players"),
                            companies = list("companies"),
                        )
                    } catch (e: Exception) {
                        Timber.i(e, "No details for game $id")
                        GameInfo()
                    }
                infoCache[id] = got
                if (_choice.value?.ref?.id == id) _info.value = id to got
            }
        }

        /** The picture of where you last stopped: on this device, or on Tower if it was played somewhere else */
        fun lastPicture(id: Int): Any? {
            val local = GameSaves.file(localGames.saveDir(id), "png")
            if (local.exists()) return local
            val time = _towerRecent.value.firstOrNull { it.first == id }?.second ?: return null
            return "$gamesAddress/api/save/$id/png?t=$time"
        }

        /** Where you last stopped plus the three slots, with pictures */
        fun saveSpots(id: Int): List<SaveSpot> {
            val dir = localGames.saveDir(id)
            val spots = mutableListOf<SaveSpot>()
            val state = GameSaves.file(dir, "state")
            if (state.exists()) {
                spots += SaveSpot("Where you stopped", GameSaves.file(dir, "png").takeIf { it.exists() }, state.lastModified())
            } else {
                lastPicture(id)?.let { pic ->
                    spots += SaveSpot("Where you stopped", pic, _towerRecent.value.firstOrNull { it.first == id }?.second ?: 0L)
                }
            }
            for (n in 1..3) {
                val f = GameSaves.file(dir, "state$n")
                if (f.exists()) spots += SaveSpot("Slot $n", GameSaves.file(dir, "png$n").takeIf { it.exists() }, f.lastModified())
            }
            return spots
        }

        /** All-time play time on this device (the game player keeps it) */
        fun playTime(id: Int): Long =
            runCatching {
                java.io
                    .File(localGames.saveDir(id), "playtime")
                    .takeIf { it.exists() }
                    ?.readText()
                    ?.trim()
                    ?.toLongOrNull()
            }.getOrNull() ?: 0L

        init {
            reload()
        }

        /** Saves this account's bests to the server and fetches everyone's (Kids and Guest only look) */
        fun refreshBoard() {
            val user = serverRepository.currentUser ?: return
            val isAdmin = serverRepository.currentUserDto?.policy?.isAdministrator == true
            val mine =
                if (KidsMode.isActive || GuestMode.isActive) {
                    emptyMap()
                } else {
                    BuiltInGame.entries.associate { it.name to prefs.getInt("${user.id}_${it.name}", 0) } +
                        BOARD_EXTRA_GAMES.keys.associateWith { prefs.getInt("${user.id}_$it", 0) }
                }
            viewModelScope.launch {
                _board.value =
                    highScores.sync(user.id, user.name, isAdmin && !KidsMode.isActive && !GuestMode.isActive, mine) { bests ->
                        // Bring back best scores made on other devices (or before a reinstall)
                        val editor = prefs.edit()
                        bests.forEach { (game, score) ->
                            if (game in mine.keys && score > prefs.getInt("${user.id}_$game", 0)) editor.putInt("${user.id}_$game", score)
                        }
                        editor.apply()
                    }
            }
        }

        fun reload() {
            _favourites.value =
                prefs
                    .getString(favKey(), null)
                    .orEmpty()
                    .split(',')
                    .mapNotNull { it.toIntOrNull() }
                    .toSet()
            if (KidsMode.isActive) {
                _library.value = LibraryState.Hidden
                return
            }
            _library.value = LibraryState.Loading
            viewModelScope.launch {
                _library.value =
                    try {
                        LibraryState.Ready(games.loadLibrary())
                    } catch (e: GameLibraryException) {
                        LibraryState.Failed(e.message ?: "Couldn't load your games.", e.askAddress, localGames.downloaded())
                    } catch (e: Exception) {
                        Timber.w(e, "Loading the game library failed")
                        LibraryState.Failed(
                            "Couldn't load your games (${e.javaClass.simpleName}).",
                            askAddress = false,
                            localGames.downloaded(),
                        )
                    }
                if (_library.value is LibraryState.Ready) {
                    localGames.sendWaitingSaves()
                    _towerRecent.value =
                        try {
                            val arr = games.getJson("/api/saves/recent").optJSONArray("games")
                            (0 until (arr?.length() ?: 0)).map { i ->
                                arr!!.getJSONObject(i).let { it.getInt("id") to it.optLong("time") }
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Couldn't get recent saves from Tower")
                            emptyList()
                        }
                }
            }
        }

        /** Shows the Play / Resume choices for a game */
        fun choose(ref: GameRef) {
            if (_starting.value != null) return
            loadInfo(ref.id)
            val have = localGames.downloaded(ref.id)
            _choice.value =
                GameChoice(
                    ref = ref,
                    resume = localGames.hasLocalResume(ref.id),
                    downloaded = have != null,
                    pinned = have?.pinned == true,
                )
            viewModelScope.launch {
                val server = localGames.hasServerResume(ref.id)
                if (server == true) _choice.value = _choice.value?.takeIf { it.ref.id == ref.id }?.copy(resume = true)
            }
        }

        fun closeChoice() {
            _choice.value = null
        }

        /** Gets the game ready (downloads what's missing) and starts it on this device */
        fun playHere(
            context: Context,
            ref: GameRef,
            resume: Boolean,
            pin: Boolean? = null,
        ) {
            if (_starting.value != null) return
            _choice.value = null
            _starting.value = ref.id
            rememberPlayed(ref.id)
            viewModelScope.launch {
                try {
                    val game = localGames.prepare(ref, pin)
                    localGames.launch(context, game, resume)
                } catch (e: GameLibraryException) {
                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Timber.w(e, "Starting a game failed")
                    Toast.makeText(context, "Couldn't start ${ref.name}.", Toast.LENGTH_LONG).show()
                } finally {
                    _starting.value = null
                }
            }
        }

        /** "Keep for trips" on or off. Turning it on downloads the game now. */
        fun setKeep(
            context: Context,
            ref: GameRef,
            keep: Boolean,
        ) {
            _choice.value = null
            if (!keep) {
                localGames.setPinned(ref.id, false)
                Toast.makeText(context, "${ref.name} can be cleared out again when space runs low.", Toast.LENGTH_LONG).show()
                return
            }
            if (_starting.value != null) return
            _starting.value = ref.id
            viewModelScope.launch {
                try {
                    localGames.prepare(ref, pinned = true)
                    Toast.makeText(context, "${ref.name} is saved on this device. It plays without internet.", Toast.LENGTH_LONG).show()
                } catch (e: GameLibraryException) {
                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Timber.w(e, "Downloading a game failed")
                    Toast.makeText(context, "Couldn't download ${ref.name}.", Toast.LENGTH_LONG).show()
                } finally {
                    _starting.value = null
                }
            }
        }

        fun removeDownload(
            context: Context,
            ref: GameRef,
        ) {
            _choice.value = null
            localGames.remove(ref.id)
            Toast.makeText(context, "Removed ${ref.name} from this device. Your saves are kept.", Toast.LENGTH_LONG).show()
            reload()
        }

        fun setAddress(address: String) {
            games.saveAddress(address)
            reload()
        }

        /** Benflix "Continue playing": the last few games played, newest first, as "id:time" */
        private fun recentKey() = "recent_${serverRepository.currentUser?.id ?: "guest"}"

        private fun rememberPlayed(id: Int) {
            val now = System.currentTimeMillis()
            val list =
                listOf("$id:$now") +
                    localRecent().filter { it.first != id }.map { "${it.first}:${it.second}" }
            prefs.edit().putString(recentKey(), list.take(RECENT_GAMES).joinToString(",")).apply()
        }

        /** (game id, when it was last played) pairs, newest first: this device's plus saves on Tower from other devices */
        fun recentPlayed(): List<Pair<Int, Long>> =
            (localRecent() + _towerRecent.value)
                .groupBy({ it.first }, { it.second })
                .map { (id, times) -> id to times.max() }
                .sortedByDescending { it.second }
                .take(RECENT_GAMES)

        private fun localRecent(): List<Pair<Int, Long>> =
            prefs
                .getString(recentKey(), null)
                .orEmpty()
                .split(',')
                .mapNotNull { part ->
                    val id = part.substringBefore(':').toIntOrNull() ?: return@mapNotNull null
                    id to (part.substringAfter(':').toLongOrNull() ?: 0L)
                }

        private val profile: String
            get() = if (KidsMode.isActive) "kids" else serverRepository.currentUser?.id?.toString() ?: "guest"

        private fun key(game: BuiltInGame): String = "${profile}_${game.name}"

        /** The built-in game this profile played last, if any */
        fun lastPlayed(): BuiltInGame? =
            prefs.getString("last_$profile", null)?.let { name -> BuiltInGame.entries.firstOrNull { it.name == name } }

        /** The game on the big tile: the one you played last, or else a "game of the day" */
        fun featured(): BuiltInGame = lastPlayed() ?: BuiltInGame.entries[LocalDate.now().dayOfYear % BuiltInGame.entries.size]

        /** Opens a built-in game, remembering it for the big tile next time */
        fun open(game: BuiltInGame) {
            prefs.edit().putString("last_$profile", game.name).apply()
            navigationManager.navigateTo(Destination.PlayGame(game.name))
        }

        /** Opens a random game (not the one on the big tile, when there's a choice) */
        fun surprise(featured: BuiltInGame) {
            val choices = BuiltInGame.entries.filter { it != featured }.ifEmpty { BuiltInGame.entries }
            open(choices.random())
        }

        fun best(game: BuiltInGame): Int = prefs.getInt(key(game), 0)

        /** This account's best in a movie game (Higher or Lower, Movie riddle, Guess the movie) */
        fun bestExtra(boardKey: String): Int = prefs.getInt("${serverRepository.currentUser?.id ?: "guest"}_$boardKey", 0)

        /** Saves the score if it's a new best, and says whether it was */
        fun submit(
            game: BuiltInGame,
            score: Int,
        ): Boolean {
            if (score <= best(game)) return false
            prefs.edit().putInt(key(game), score).apply()
            return true
        }

        companion object {
            private const val RECENT_GAMES = 8

            /** The movie games that keep a best score too (their saved key to their name) */
            val BOARD_EXTRA_GAMES =
                linkedMapOf(
                    HigherLowerViewModel.BOARD_KEY to "Higher or Lower",
                    DailyRiddleViewModel.BOARD_KEY to "Movie riddle",
                    "GUESS_THE_MOVIE" to "Guess the movie",
                )

            /** What each board ranks, shown under its name */
            val BOARD_RANKED =
                mapOf(
                    HigherLowerViewModel.BOARD_KEY to "best streak",
                    DailyRiddleViewModel.BOARD_KEY to "most points in a run",
                )
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
    val towerRecentTop by viewModel.towerRecent.collectAsState()
    // The big "Continue playing" card: the last game played on any TV or phone that this device can play
    val banner =
        remember(library, towerRecentTop, starting) {
            val ready = library as? LibraryState.Ready ?: return@remember null
            val power = DevicePower.get(context)
            val refs =
                ready.consoles
                    .filter { c -> LocalConsole.forSlug(c.slug)?.let { power.fit(it.power) != DevicePower.Fit.TOO_MUCH } == true }
                    .flatMap { c -> c.games.map { it.id to GameRef(it.id, it.name, c.slug, c.name, it.coverUrl, it.size) } }
                    .toMap()
            viewModel.recentPlayed().firstNotNullOfOrNull { (id, time) -> refs[id]?.let { it to time } }
        }

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
        // One "works with" line for the whole page, instead of chips on every tile
        WorksWith(
            text = if (isTv) "Quick games you can play with your TV remote." else "Quick games you can play by tapping.",
            isTv = isTv,
        )
        // Leaderboards first: your bests and everyone's top scores, each card can take focus so the remote reaches them
        val board by viewModel.board.collectAsState()
        LaunchedEffect(Unit) { viewModel.refreshBoard() }
        Leaderboards(viewModel = viewModel, board = board, isTv = isTv)
        banner?.let { (ref, time) ->
            ContinueBanner(
                ref = ref,
                playedText = playedAgo(time),
                picture = remember(ref.id, time) { viewModel.lastPicture(ref.id) },
                token = viewModel.gamesToken,
                isTv = isTv,
                onClick = { viewModel.choose(ref) },
            )
        }
        ArcadeWall(viewModel = viewModel, isTv = isTv, focusRequester = focusRequester)

        // Benflix "Guess the movie" and the daily riddle, made from your own movies
        val movieGames =
            listOf<@Composable (Modifier) -> Unit>(
                { mod ->
                    MovieGameTile(
                        title = "Guess the movie",
                        subtitle = "A blurry picture gets sharper. Guess it fast for more points!",
                        icon = R.string.fa_eye,
                        colors = listOf(Color(0xFF0E7490), Color(0xFF1E3A8A)),
                        onClick = { viewModel.navigationManager.navigateTo(Destination.GuessTheMovie) },
                        isTv = isTv,
                        modifier = mod,
                    )
                },
                { mod ->
                    MovieGameTile(
                        title = "Daily movie riddle",
                        subtitle = "One riddle a day, the same for the whole family. Three clues, four choices.",
                        icon = R.string.fa_clock,
                        colors = listOf(Color(0xFFC2410C), Color(0xFF6D28D9)),
                        onClick = { viewModel.navigationManager.navigateTo(Destination.DailyRiddle) },
                        isTv = isTv,
                        modifier = mod,
                    )
                },
                { mod ->
                    MovieGameTile(
                        title = "Higher or Lower",
                        subtitle = "Two posters. Did critics score the second one higher or lower? Keep your streak going!",
                        icon = R.string.fa_arrow_up_long,
                        colors = listOf(Color(0xFF15803D), Color(0xFF7F1D1D)),
                        onClick = { viewModel.navigationManager.navigateTo(Destination.HigherLower) },
                        isTv = isTv,
                        modifier = mod,
                    )
                },
            )
        if (isTv) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.height(IntrinsicSize.Min),
            ) {
                movieGames.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
            }
        } else {
            movieGames.forEach { it(Modifier.fillMaxWidth()) }
        }

        // Your own games, from the RomM game library on Tower. They play right here on this TV or phone.
        if (library != LibraryState.Hidden) {
            Text(
                text = "Your games",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp),
            )
            val power = remember { DevicePower.get(context) }
            val prep by viewModel.localGames.prep.collectAsState()
            val cardSize =
                Modifier.size(
                    width = if (isTv) 150.dp else 110.dp,
                    height = if (isTv) 210.dp else 154.dp,
                )

            @Composable
            fun Card(ref: GameRef) {
                val status =
                    when {
                        prep?.gameId == ref.id -> prep?.text
                        starting == ref.id -> "Starting…"
                        else -> null
                    }
                ServerGameCard(
                    game = ServerGame(id = ref.id, name = ref.name, coverUrl = ref.coverUrl, playable = true, size = ref.size),
                    playWith =
                        remember(ref.slug) {
                            val local = LocalConsole.forSlug(ref.slug)
                            listOfNotNull(
                                if (local?.remoteFriendly == true) PlayWith.REMOTE else null,
                                PlayWith.CONTROLLER,
                                PlayWith.PHONE,
                            )
                        },
                    token = viewModel.gamesToken,
                    status = status,
                    progress = prep?.takeIf { it.gameId == ref.id }?.fraction,
                    onClick = { viewModel.choose(ref) },
                    modifier = cardSize,
                )
            }
            DeviceCard(power, isTv)
            StorageCard(
                games = remember(library) { viewModel.localGames.downloaded() },
                isTv = isTv,
                onRemove = { viewModel.removeDownload(context, it.ref) },
            )
            if (!isTv) {
                ActionTile(
                    title = "Use this phone as a controller",
                    subtitle = "For a game playing on a TV in the house",
                    onClick = { context.startActivity(Intent(context, PhonePadActivity::class.java)) },
                )
            }
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
                    if (state.downloaded.isNotEmpty()) {
                        Text(
                            text = "Saved on this device",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        InfoText("These play without Tower. Your saves are sent to Tower next time it can be reached.")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                        ) {
                            items(state.downloaded, key = { "dl-${it.ref.id}" }) { Card(it.ref) }
                        }
                    }
                }

                is LibraryState.Ready -> {
                    if (state.consoles.isEmpty()) {
                        InfoText("Your game library is connected but has no games yet. Add some in RomM and run a scan.")
                    }
                    // Only consoles this device can play (some may be a bit slow)
                    val playable =
                        remember(state) {
                            state.consoles.mapNotNull { c ->
                                val local = LocalConsole.forSlug(c.slug) ?: return@mapNotNull null
                                val fit = power.fit(local.power)
                                if (fit == DevicePower.Fit.TOO_MUCH) null else Triple(c, local, fit)
                            }
                        }
                    if (playable.isNotEmpty()) {
                        InfoText(
                            if (isTv) {
                                "Press a game to play it on this TV. Any Bluetooth controller works. Back opens the game menu."
                            } else {
                                "Tap a game to play it on this phone with on-screen buttons, or a Bluetooth controller."
                            },
                        )
                    }
                    val refs =
                        remember(state) {
                            playable
                                .flatMap { (c, _, _) ->
                                    c.games.map {
                                        it.id to GameRef(it.id, it.name, c.slug, c.name, it.coverUrl, it.size)
                                    }
                                }.toMap()
                        }
                    val towerRecent by viewModel.towerRecent.collectAsState()
                    val recent =
                        remember(state, starting, towerRecent) {
                            viewModel.recentPlayed().mapNotNull { (id, time) -> refs[id]?.let { it to time } }
                        }
                    val favourites by viewModel.favourites.collectAsState()
                    val favRefs = remember(refs, favourites) { favourites.mapNotNull { refs[it] }.sortedBy { it.name.lowercase() } }
                    if (favRefs.isNotEmpty()) {
                        Text(
                            text = "Favourite games",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                        ) {
                            items(favRefs, key = { "fav-${it.id}" }) { Card(it) }
                        }
                    }
                    if (recent.size > 1) {
                        Text(
                            text = "Played lately",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                        ) {
                            items(recent.drop(1), key = { "recent-${it.first.id}" }) { (ref, time) ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Card(ref)
                                    Text(
                                        text = "${ref.consoleName} · ${playedAgo(time)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        modifier = Modifier.width(if (isTv) 150.dp else 110.dp),
                                    )
                                }
                            }
                        }
                    }
                    var openSlug by rememberSaveable { mutableStateOf<String?>(null) }
                    val open = playable.firstOrNull { it.first.slug == openSlug }
                    BackHandler(enabled = open != null) { openSlug = null }
                    if (open == null) {
                        // A grid of consoles: photo, name and how many games. Press one to see its games.
                        if (playable.isNotEmpty()) {
                            Text(
                                text = "Consoles",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        ConsoleGrid(playable = playable, isTv = isTv, onOpen = { openSlug = it })
                    } else {
                        val (console, local, fit) = open
                        val notes =
                            listOfNotNull(
                                if (fit == DevicePower.Fit.MAY_BE_SLOW) "may be slow on this ${if (isTv) "TV" else "phone"}" else null,
                                if (isTv && local.remoteFriendly) "works with the remote" else null,
                            )
                        ActionTile(title = "← All consoles", subtitle = console.name, onClick = { openSlug = null })
                        Text(
                            text =
                                "${console.name} (${console.games.size})" +
                                    if (notes.isEmpty()) "" else "  ·  " + notes.joinToString(" · "),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        var sort by rememberSaveable { mutableStateOf(GameSort.AZ) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GameSort.entries.forEach { mode ->
                                SortChip(mode.label, selected = mode == sort) { sort = mode }
                            }
                        }
                        val rows =
                            remember(console, recent, favourites, sort) {
                                consoleRows(
                                    console,
                                    recent.map { it.first.id },
                                    viewModel.localGames.downloaded().map { it.ref.id },
                                    favourites,
                                    sort,
                                )
                            }
                        rows.forEach { (title, games) ->
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
                                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                            ) {
                                items(games, key = { "$title-${it.id}" }) { game -> refs[game.id]?.let { Card(it) } }
                            }
                        }
                    }
                    ActionTile(title = "Reload games", subtitle = "After adding games in RomM", onClick = { viewModel.reload() })
                }
            }
        }
    }

    val choice by viewModel.choice.collectAsState()
    val info by viewModel.info.collectAsState()
    val favourites by viewModel.favourites.collectAsState()
    choice?.let { c ->
        GameDetailsPage(
            choice = c,
            info = info?.takeIf { it.first == c.ref.id }?.second,
            saves = remember(c) { viewModel.saveSpots(c.ref.id) },
            playTimeMs = remember(c) { viewModel.playTime(c.ref.id) },
            favourite = c.ref.id in favourites,
            token = viewModel.gamesToken,
            isTv = isTv,
            onPlay = { resume -> viewModel.playHere(context, c.ref, resume) },
            onFavourite = { viewModel.toggleFavourite(c.ref.id) },
            onKeep = { keep -> viewModel.setKeep(context, c.ref, keep) },
            onRemove = { viewModel.removeDownload(context, c.ref) },
            onDismiss = { viewModel.closeChoice() },
        )
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
    status: String?,
    onClick: () -> Unit,
    progress: Float? = null,
    playWith: List<PlayWith> = emptyList(),
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
            // What it plays with: remote (simple games only), controller, phone
            if (playWith.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp),
                ) {
                    playWith.forEach { PlayIcon(it) }
                }
            }
            if (status != null || !game.playable) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = .65f)),
                ) {
                    if (progress != null) {
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(progress)
                                .height(6.dp)
                                .background(Color(0xFFE50914)),
                        )
                    }
                    Text(
                        text = status ?: "Can't play here yet",
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

/** "today", "yesterday", "3 days ago", "last week" or a date */
private fun playedAgo(time: Long): String {
    val days = ((System.currentTimeMillis() - time) / 86_400_000L).toInt()
    return when {
        days <= 0 -> {
            "today"
        }

        days == 1 -> {
            "yesterday"
        }

        days < 7 -> {
            "$days days ago"
        }

        days < 14 -> {
            "last week"
        }

        else -> {
            java.time.Instant
                .ofEpochMilli(time)
                .atZone(java.time.ZoneId.systemDefault())
                .format(
                    java.time.format.DateTimeFormatter
                        .ofPattern("MMM d"),
                )
        }
    }
}

/** One spot on the Arcade wall */
private sealed interface WallItem {
    data class Game(
        val game: BuiltInGame,
    ) : WallItem

    data object Surprise : WallItem
}

/**
 * The Arcade wall: every built-in game as a same-size tile (the one you played last comes first),
 * plus a "Surprise me" tile. TV: four tiles per row. Phone: two per row.
 */
@Composable
private fun ArcadeWall(
    viewModel: GamesViewModel,
    isTv: Boolean,
    focusRequester: FocusRequester,
) {
    val last = remember { viewModel.lastPlayed() }
    val tiles =
        remember(last) {
            val games = listOfNotNull(last) + BuiltInGame.entries.filter { it != last }
            games.map { WallItem.Game(it) } + WallItem.Surprise
        }
    val gap = if (isTv) 16.dp else 10.dp
    val columns = if (isTv) 4 else 2

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val unit = (maxWidth - gap * (columns - 1)) / columns
        val tileHeight = if (isTv) unit * .6f else 112.dp
        Column(
            verticalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier.padding(vertical = if (isTv) 12.dp else 4.dp),
        ) {
            tiles.chunked(columns).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEachIndexed { c, item ->
                        val mod =
                            Modifier
                                .size(width = unit, height = tileHeight)
                                .then(if (r == 0 && c == 0) Modifier.focusRequester(focusRequester) else Modifier)
                        when (item) {
                            is WallItem.Game -> {
                                GameTile(
                                    game = item.game,
                                    best = viewModel.best(item.game),
                                    isTv = isTv,
                                    onClick = { viewModel.open(item.game) },
                                    modifier = mod,
                                )
                            }

                            WallItem.Surprise -> {
                                SurpriseTile(isTv = isTv, onClick = { viewModel.surprise(viewModel.featured()) }, modifier = mod)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A built-in game's tile: the game's picture, its name and your best score */
@Composable
private fun GameTile(
    game: BuiltInGame,
    best: Int,
    isTv: Boolean,
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
            // Fade the picture behind the name so it stays easy to read
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to game.colors[1].copy(alpha = .95f),
                            0.45f to game.colors[1].copy(alpha = .6f),
                            0.75f to game.colors[1].copy(alpha = .15f),
                            1f to Color.Black.copy(alpha = .35f),
                        ),
                    ),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(if (isTv) 12.dp else 10.dp),
            ) {
                Text(
                    game.title,
                    fontSize = if (isTv) 20.sp else 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 2,
                    lineHeight = if (isTv) 22.sp else 19.sp,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(Modifier.weight(1f))
                Text(
                    text = if (best > 0) "Best $best" else "New",
                    fontSize = if (isTv) 12.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    modifier =
                        Modifier
                            .background(Color.Black.copy(alpha = .55f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** The "Surprise me" tile: starts a random built-in game */
@Composable
private fun SurpriseTile(
    isTv: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border =
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = .25f)), shape = shape),
                focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
            ),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1C1C21), focusedContainerColor = Color(0xFF2A2A31)),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),
        ) {
            Text(stringResource(R.string.fa_shuffle), fontFamily = FontAwesome, fontSize = 22.sp, color = Color(0xFFE50914))
            Text("Surprise me", fontSize = if (isTv) 16.sp else 17.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text("A random game", fontSize = 11.sp, color = Color.White.copy(alpha = .7f))
        }
    }
}

/**
 * "Your best scores": every built-in game with this profile's best, highest first.
 * TV: one slim strip across the page, a cell per game. Phone: a simple list.
 */
private enum class PlayWith { REMOTE, CONTROLLER, PHONE }

/** A small round icon on a game card: remote, controller or phone */
@Composable
private fun PlayIcon(kind: PlayWith) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(24.dp)
                .background(Color.Black.copy(alpha = .72f), RoundedCornerShape(50)),
    ) {
        when (kind) {
            PlayWith.REMOTE -> {
                RemoteIcon(Modifier.size(width = 6.dp, height = 12.dp))
            }

            PlayWith.CONTROLLER -> {
                Text(stringResource(R.string.fa_gamepad), fontFamily = FontAwesome, fontSize = 11.sp, color = Color.White)
            }

            PlayWith.PHONE -> {
                Text(stringResource(R.string.fa_mobile_screen), fontFamily = FontAwesome, fontSize = 11.sp, color = Color.White)
            }
        }
    }
}

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
                .background(Color.White.copy(alpha = .1f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
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
            maxLines = 1,
        )
    }
}

/**
 * The page's intro line plus one set of "works with" chips. Every game here works with a TV remote,
 * a controller, or a phone, so the chips live here once instead of on every tile.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorksWith(
    text: String,
    isTv: Boolean,
) {
    @Composable
    fun Chips() {
        Badge(PlayWith.REMOTE, "Remote")
        Badge(PlayWith.CONTROLLER, "Controller")
        Badge(PlayWith.PHONE, "Phone")
    }
    if (isTv) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text("Works with", fontSize = 12.sp, color = Color.White.copy(alpha = .6f))
            Chips()
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Chips()
            }
        }
    }
}

/**
 * A colourful tile for the movie games (Guess the movie, Daily riddle, Higher or Lower).
 * The whole title and description always show: nothing is cut off. On TV the icon sits next to the
 * title so the text gets the full width of the tile.
 */
@Composable
private fun MovieGameTile(
    title: String,
    subtitle: String,
    icon: Int,
    colors: List<Color>,
    onClick: () -> Unit,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
            ),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        modifier = modifier,
    ) {
        @Composable
        fun IconBox(boxSize: Int) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(boxSize.dp)
                        .background(Color.White.copy(alpha = .16f), RoundedCornerShape(10.dp)),
            ) {
                Text(stringResource(icon), fontFamily = FontAwesome, fontSize = (boxSize * .45f).sp, color = Color.White)
            }
        }
        if (isTv) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .heightIn(min = 120.dp)
                        .background(Brush.linearGradient(colors))
                        .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconBox(40)
                    Text(title, fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
                Text(subtitle, fontSize = 14.sp, lineHeight = 19.sp, color = Color.White.copy(alpha = .9f))
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 104.dp)
                        .background(Brush.linearGradient(colors))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                IconBox(48)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(title, fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text(subtitle, fontSize = 14.sp, lineHeight = 18.sp, color = Color.White.copy(alpha = .9f))
                }
            }
        }
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
                .widthIn(max = 560.dp)
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

/** Your best scores and everyone's highest, as a row of cards (each one focusable so the TV remote can reach it) */
@Composable
private fun Leaderboards(
    viewModel: GamesViewModel,
    board: Map<String, List<HighScore>>?,
    isTv: Boolean,
) {
    val games =
        BuiltInGame.entries.map { it.name to it.title } +
            GamesViewModel.BOARD_EXTRA_GAMES.entries.map { it.key to it.value }
    val mine =
        remember(board) {
            BuiltInGame.entries.associate { it.name to viewModel.best(it) } +
                GamesViewModel.BOARD_EXTRA_GAMES.keys.associateWith { viewModel.bestExtra(it) }
        }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🏆", fontSize = 26.sp)
            Column {
                Text("Leaderboards", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text(
                    if (board == null) "Loading everyone's scores…" else "Everyone's best on this server, and yours",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = .65f),
                )
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 16.dp else 12.dp),
            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 8.dp),
        ) {
            items(games, key = { "board-${it.first}" }) { (id, title) ->
                BoardCard(
                    title = title,
                    ranked = GamesViewModel.BOARD_RANKED[id],
                    top = board?.get(id).orEmpty().take(BOARD_ROWS),
                    mine = mine[id] ?: 0,
                    colour = BOARD_COLOURS[games.indexOfFirst { it.first == id } % BOARD_COLOURS.size],
                    isTv = isTv,
                )
            }
        }
    }
}

private val BOARD_COLOURS =
    listOf(
        Color(0xFF7C3AED),
        Color(0xFFDC2626),
        Color(0xFF0891B2),
        Color(0xFFEA580C),
        Color(0xFF16A34A),
        Color(0xFFDB2777),
        Color(0xFF2563EB),
        Color(0xFFCA8A04),
    )

@Composable
private fun BoardCard(
    title: String,
    ranked: String?,
    top: List<HighScore>,
    mine: Int,
    colour: Color,
    isTv: Boolean,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF16161A), focusedContainerColor = Color(0xFF16161A)),
        modifier = Modifier.width(if (isTv) 250.dp else 200.dp),
    ) {
        Column(
            Modifier
                .background(Brush.verticalGradient(listOf(colour.copy(alpha = .9f), colour.copy(alpha = .25f), Color(0xFF16161A))))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                ranked?.let { "Ranked by $it" } ?: "Ranked by score",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = .75f),
                maxLines = 1,
            )
            Box(
                Modifier
                    .padding(vertical = 6.dp)
                    .background(Color.Black.copy(alpha = .3f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    if (mine > 0) "Your best  ${"%,d".format(mine)}" else "No score from you yet",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                )
            }
            if (top.isEmpty()) {
                Text("No scores yet. Be the first!", fontSize = 13.sp, color = Color.White.copy(alpha = .6f))
            }
            top.forEachIndexed { i, entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (i) {
                            0 -> "🥇"
                            1 -> "🥈"
                            2 -> "🥉"
                            else -> "${i + 1}."
                        },
                        fontSize = 15.sp,
                        color = Color.White,
                        modifier = Modifier.width(30.dp),
                    )
                    Text(
                        entry.name,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (i == 0) Color(0xFFFFD54F) else Color.White.copy(alpha = .9f),
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "%,d".format(entry.score),
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (i == 0) Color(0xFFFFD54F) else Color.White.copy(alpha = .9f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** How many accounts show under each game on "Highest scores" */
private const val BOARD_ROWS = 5

/** What the Play / Resume choices for one game show */
data class GameChoice(
    val ref: GameRef,
    val resume: Boolean,
    val downloaded: Boolean,
    val pinned: Boolean,
)

/**
 * "This TV: MediaTek MT8696 · 4 cores…" and which consoles play well here. It can take focus, so the
 * remote can move onto it and the page scrolls to show it.
 */
@Composable
private fun DeviceCard(
    power: DevicePower,
    isTv: Boolean,
) {
    val good = LocalConsole.ALL.filter { power.fit(it.power) == DevicePower.Fit.GOOD }.map { it.title }
    val slow = LocalConsole.ALL.filter { power.fit(it.power) == DevicePower.Fit.MAY_BE_SLOW }.map { it.title }
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1C1C1C), focusedContainerColor = Color(0xFF262626)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = (if (isTv) "This TV: " else "This phone: ") + power.summary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text("Plays well: ${good.joinToString(", ")}", fontSize = 14.sp, color = Color.White.copy(alpha = .8f))
            if (slow.isNotEmpty()) Text("May be slow: ${slow.joinToString(", ")}", fontSize = 14.sp, color = Color.White.copy(alpha = .8f))
        }
    }
}

/** How the games inside a console are grouped after the top rows */
enum class GameSort(
    val label: String,
) {
    AZ("A to Z"),
    GENRE("Genre"),
    YEAR("Year"),
}

/** Rows inside one console: played lately, on this device, favourites, then all games by letter, genre or year */
private fun consoleRows(
    console: GameConsole,
    recentIds: List<Int>,
    downloadedIds: List<Int>,
    favouriteIds: Set<Int>,
    sort: GameSort,
): List<Pair<String, List<ServerGame>>> {
    val byId = console.games.associateBy { it.id }
    val rows = mutableListOf<Pair<String, List<ServerGame>>>()
    recentIds.mapNotNull { byId[it] }.takeIf { it.isNotEmpty() }?.let { rows += "Played lately" to it }
    downloadedIds.mapNotNull { byId[it] }.takeIf { it.isNotEmpty() }?.let { rows += "On this device" to it }
    console.games
        .filter { it.id in favouriteIds }
        .sortedBy { it.name.lowercase() }
        .takeIf { it.isNotEmpty() }
        ?.let { rows += "Favourites" to it }
    val sorted = console.games.sortedBy { it.name.lowercase() }
    when (sort) {
        GameSort.AZ -> {
            if (sorted.size <= 24) {
                rows += "All games" to sorted
            } else {
                sorted
                    .groupBy {
                        val c = it.name.firstOrNull()?.uppercaseChar() ?: '#'
                        if (c in 'A'..'Z') c.toString() else "#"
                    }.toSortedMap()
                    .forEach { (letter, games) -> rows += letter to games }
            }
        }

        GameSort.GENRE -> {
            val groups = sorted.groupBy { it.genres.firstOrNull() ?: "Other" }
            groups.keys
                .sortedWith(compareBy({ it == "Other" }, { -(groups[it]?.size ?: 0) }))
                .forEach { rows += it to groups.getValue(it) }
        }

        GameSort.YEAR -> {
            sorted
                .groupBy { if (it.year > 0) it.year.toString() else "Unknown year" }
                .toSortedMap(compareBy<String> { it == "Unknown year" }.thenByDescending { it })
                .forEach { (year, games) -> rows += year to games }
        }
    }
    return rows
}

/** Public domain console photos (by Evan Amos, Wikimedia Commons), by RomM console name */
private fun consolePhoto(slug: String): String? =
    CONSOLE_PHOTOS[slug]?.let { "https://commons.wikimedia.org/wiki/Special:FilePath/$it?width=480" }

private val CONSOLE_PHOTOS =
    mapOf(
        "nes" to "NES-Console-Set.jpg",
        "famicom" to "NES-Console-Set.jpg",
        "snes" to "SNES-Mod1-Console-Set.jpg",
        "sfam" to "SNES-Mod1-Console-Set.jpg",
        "genesis" to "Sega-Genesis-Mk2-6button.jpg",
        "genesis-slash-megadrive" to "Sega-Genesis-Mk2-6button.jpg",
        "megadrive" to "Sega-Genesis-Mk2-6button.jpg",
        "sms" to "Sega-Master-System-Set.jpg",
        "gamegear" to "Game-Gear-Handheld.jpg",
        "gb" to "Game-Boy-FL.jpg",
        "gbc" to "Nintendo-Game-Boy-Color-FL.jpg",
        "gba" to "Nintendo-Game-Boy-Advance-Purple-FL.jpg",
        "psx" to "PSX-Console-wController.jpg",
    )

@Composable
private fun SortChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border =
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, if (selected) Color.White else Color.Transparent), shape = shape),
                focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
            ),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (selected) Color.White.copy(alpha = .2f) else Color(0xFF242424),
                contentColor = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

/** The console tiles at the top of "Your games" */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConsoleGrid(
    playable: List<Triple<GameConsole, LocalConsole, DevicePower.Fit>>,
    isTv: Boolean,
    onOpen: (String) -> Unit,
) {
    val tileWidth = if (isTv) 220.dp else 160.dp
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        playable.forEach { (console, local, fit) ->
            val shape = RoundedCornerShape(14.dp)
            Surface(
                onClick = { onOpen(console.slug) },
                shape = ClickableSurfaceDefaults.shape(shape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = consoleColour(console.slug).copy(alpha = .35f),
                        focusedContainerColor = consoleColour(console.slug).copy(alpha = .55f),
                    ),
                modifier = Modifier.width(tileWidth),
            ) {
                Column {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(tileWidth * 0.62f)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(consoleColour(console.slug), consoleColour(console.slug).copy(alpha = .7f)),
                                    ),
                                ),
                    ) {
                        // The name shows until (or if) the photo loads over it
                        Text(local.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        consolePhoto(console.slug)?.let { photo ->
                            AsyncImage(
                                model = photo,
                                contentDescription = console.name,
                                contentScale = ContentScale.Fit,
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                            )
                        }
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            console.name,
                            fontSize = if (isTv) 17.sp else 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text =
                                "${console.games.size} ${if (console.games.size == 1) "game" else "games"}" +
                                    when {
                                        fit == DevicePower.Fit.MAY_BE_SLOW -> " · may be slow"
                                        isTv && local.remoteFriendly -> " · remote OK"
                                        else -> ""
                                    },
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = .75f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** How full this device is, with a bar, and a place to delete downloaded games (saves are kept) */
@Composable
private fun StorageCard(
    games: List<DownloadedGame>,
    isTv: Boolean,
    onRemove: (DownloadedGame) -> Unit,
) {
    val context = LocalContext.current
    val stats =
        remember(games) {
            try {
                val fs = StatFs(context.filesDir.path)
                fs.totalBytes to fs.availableBytes
            } catch (_: Exception) {
                0L to 0L
            }
        }
    val (total, free) = stats
    if (total <= 0) return
    val used = (total - free).toFloat() / total
    val gamesBytes = games.sumOf { it.bytes }
    val barColor =
        when {
            used > 0.9f -> Color(0xFFE5484D)
            used > 0.75f -> Color(0xFFF5A524)
            else -> Color(0xFF46A758)
        }
    var managing by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = { if (games.isNotEmpty()) managing = true },
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1C1C1C), focusedContainerColor = Color(0xFF262626)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = (if (isTv) "TV storage: " else "Phone storage: ") + "${LocalGames.mb(free)} free of ${LocalGames.mb(total)}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Color.White.copy(alpha = .15f), RoundedCornerShape(4.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(used.coerceIn(0.02f, 1f))
                        .height(8.dp)
                        .background(barColor, RoundedCornerShape(4.dp)),
                )
            }
            Text(
                text =
                    if (games.isEmpty()) {
                        "No games downloaded on this device yet"
                    } else {
                        "Games on this device: ${LocalGames.mb(gamesBytes)} (${games.size}). " +
                            (if (used > 0.9f) "Getting full! " else "") + "Select to delete some."
                    },
                fontSize = 14.sp,
                color = Color.White.copy(alpha = .8f),
            )
        }
    }
    if (managing) {
        val focus = remember { FocusRequester() }
        androidx.compose.ui.window.Dialog(onDismissRequest = { managing = false }) {
            LaunchedEffect(Unit) { focus.tryRequestFocus() }
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier =
                    Modifier
                        .widthIn(max = 460.dp)
                        .heightIn(max = 560.dp)
                        .background(Color(0xFF1C1C1C), RoundedCornerShape(16.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
            ) {
                Text("Games on this device", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "Deleting a game only frees up space. Your saves and saved spots are kept, and the game " +
                        "downloads again next time you play it.",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = .75f),
                )
                ChoiceButton("Done", "Close this list", Modifier.focusRequester(focus)) { managing = false }
                games.forEach { g ->
                    ChoiceButton(
                        "Delete ${g.ref.name}",
                        "${g.ref.consoleName} · ${LocalGames.mb(g.bytes)}" + if (g.pinned) " · kept for trips" else "",
                    ) {
                        onRemove(g)
                        if (games.size <= 1) managing = false
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceButton(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF2A2A2A), focusedContainerColor = Color(0xFF3A3A3A)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = .75f))
        }
    }
}
