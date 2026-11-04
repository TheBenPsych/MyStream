package com.github.damontecres.wholphin.ui.games

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.games.DownloadedGame
import com.github.damontecres.wholphin.games.GameRef
import com.github.damontecres.wholphin.games.GameSaves
import com.github.damontecres.wholphin.games.LocalGames
import com.github.damontecres.wholphin.services.BenflixHighScores
import com.github.damontecres.wholphin.services.GameConsole
import com.github.damontecres.wholphin.services.GameLibraryClient
import com.github.damontecres.wholphin.services.GameLibraryException
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.HighScore
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.nav.Destination
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

        /** Favourite games (the heart), per account; Kids and Guest have their own, like scores */
        private val _favourites = MutableStateFlow<Set<Int>>(emptySet())
        val favourites: StateFlow<Set<Int>> = _favourites

        private fun favKey() = "fav_$profile"

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
            // Show the board from last time straight away, then the fresh one when it arrives
            val cacheKey = "board_cache_${user.id}"
            if (_board.value == null) {
                prefs.getString(cacheKey, null)?.let { _board.value = BenflixHighScores.decodeBoard(it) }
            }
            viewModelScope.launch {
                val fresh =
                    highScores.sync(user.id, user.name, isAdmin && !KidsMode.isActive && !GuestMode.isActive, mine) { bests ->
                        // Bring back best scores made on other devices (or before a reinstall)
                        val editor = prefs.edit()
                        bests.forEach { (game, score) ->
                            if (game in mine.keys && score > prefs.getInt("${user.id}_$game", 0)) editor.putInt("${user.id}_$game", score)
                        }
                        editor.apply()
                    }
                _board.value = fresh
                prefs.edit().putString(cacheKey, BenflixHighScores.encodeBoard(fresh)).apply()
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
            // Benflix speed: show the games from last time straight away (if there are any), then
            // swap in the fresh list when it arrives
            val hadGames = _library.value is LibraryState.Ready
            if (!hadGames) _library.value = LibraryState.Loading
            // Look at Tower's BIOS files again, and clear out downloads that never finished
            localGames.forgetBiosList()
            viewModelScope.launch { localGames.clearLeftovers() }
            viewModelScope.launch {
                var showingSaved = hadGames
                if (!hadGames) {
                    games.savedLibrary()?.let { saved ->
                        val savedState = stateFor(saved)
                        if (savedState is LibraryState.Ready && _library.value is LibraryState.Loading) {
                            _library.value = savedState
                            showingSaved = true
                        }
                    }
                }
                val fresh =
                    try {
                        stateFor(games.loadLibrary())
                    } catch (e: GameLibraryException) {
                        LibraryState.Failed(e.message ?: "Couldn't load your games.", e.askAddress, visibleDownloads())
                    } catch (e: Exception) {
                        Timber.w(e, "Loading the game library failed")
                        LibraryState.Failed(
                            "Couldn't load your games (${e.javaClass.simpleName}).",
                            askAddress = false,
                            visibleDownloads(),
                        )
                    }
                // Couldn't reach the helper but the saved list is on screen: keep showing it
                if (fresh is LibraryState.Ready || !showingSaved) _library.value = fresh
                (_library.value as? LibraryState.Ready)?.let { ready ->
                    // Get the emulators for this library's consoles ready in the background (Wi-Fi only)
                    viewModelScope.launch { localGames.warmCores(ready.consoles.map { it.slug }) }
                }
                if (_library.value is LibraryState.Ready && !KidsMode.isActive) {
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

        /** The page for [consoles]: Kids only get games rated for everyone (unrated ones are left out, like movies) */
        private fun stateFor(consoles: List<GameConsole>): LibraryState =
            if (KidsMode.isActive) {
                val kidsConsoles =
                    consoles
                        .map { c -> c.copy(games = c.games.filter { it.kids }) }
                        .filter { it.games.isNotEmpty() }
                if (kidsConsoles.isEmpty()) {
                    LibraryState.Failed(
                        "No games rated for everyone yet. A grown-up can run the Benflix setup command on Tower again " +
                            "to update it, and games rated E show up here.",
                        askAddress = false,
                        emptyList(),
                    )
                } else {
                    LibraryState.Ready(kidsConsoles)
                }
            } else {
                LibraryState.Ready(consoles)
            }

        /** Games downloaded on this device that this profile may see (Kids: only games rated for everyone) */
        fun visibleDownloads(): List<DownloadedGame> =
            localGames.downloaded().let { all -> if (KidsMode.isActive) all.filter { it.ref.kids } else all }

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

        /**
         * Benflix "Continue playing": the last few games played, newest first, as "id:time".
         * Kids and Guest have their own list, like scores
         */
        private fun recentKey() = "recent_$profile"

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
            get() =
                when {
                    // Guest scores are kept apart from the owner's, like the Movie riddle
                    GuestMode.isActive -> "guest"

                    KidsMode.isActive -> "kids"

                    else -> serverRepository.currentUser?.id?.toString() ?: "guest"
                }

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
        fun bestExtra(boardKey: String): Int {
            val owner = if (GuestMode.isActive) "guest" else serverRepository.currentUser?.id?.toString() ?: "guest"
            return prefs.getInt("${owner}_$boardKey", 0)
        }

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
                    com.github.damontecres.wholphin.ui.games.ember.EmberViewModel.BOARD_KEY to "Ember Road",
                )

            /** What each board ranks, shown under its name */
            val BOARD_RANKED =
                mapOf(
                    HigherLowerViewModel.BOARD_KEY to "best streak",
                    DailyRiddleViewModel.BOARD_KEY to "most points in a run",
                    com.github.damontecres.wholphin.ui.games.ember.EmberViewModel.BOARD_KEY to "most points in a journey",
                )
        }
    }
