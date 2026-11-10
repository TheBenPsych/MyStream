package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.holidayFor
import com.github.damontecres.wholphin.ui.BenflixIntroState
import com.github.damontecres.wholphin.ui.DOOR_NOTE_VOLUME
import com.github.damontecres.wholphin.ui.IntroPace
import com.github.damontecres.wholphin.ui.IntroSounds
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.components.UpdateNotice
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.runIntroTimeline
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.waitForSettled
import com.github.damontecres.wholphin.util.StartupTimer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

/**
 * One row of Home's list (approved f3-panel): your news first (new episode, new volume, request ready),
 * then On the way (your own requests), then Did you know. [dot] is the door colour (white = requests and app news).
 * [destination] is where pressing it goes (null = nowhere, just reading). News goes away once opened.
 */
data class HomeRow(
    val id: String,
    val title: String,
    val detail: String,
    val dot: Color,
    val image: String?,
    val destination: Destination?,
    /** A square picture (music) instead of a poster */
    val square: Boolean = false,
    val news: Boolean = false,
)

/**
 * Home's greeting, weather and list (approved f3-panel, 2026-11-10): up to 4 rows. News about new episodes of
 * shows you watch, a new season of a show you watched, new music from artists you follow, a new book volume,
 * a request that's ready, and an app update; then your own requests that are still on the way; then a
 * "Did you know" fact that changes every time Home opens. Nothing from Live TV or Games. A news row goes away
 * once opened, after 3 days, or when newer news of the same kind pushes it out. Each profile only sees its own.
 * The weather comes from Tower (a town typed once on the Server page), never from the TV's or phone's location.
 */
@HiltViewModel
class HomeDoorsViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val requestReadyService: RequestReadyService,
        private val books: com.github.damontecres.wholphin.services.BenflixBooks,
        private val helper: com.github.damontecres.wholphin.services.GameLibraryClient,
        private val funFacts: com.github.damontecres.wholphin.services.BenflixFunFacts,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _rows = MutableStateFlow<List<HomeRow>>(emptyList())
        val rows: StateFlow<List<HomeRow>> = _rows

        private val _weather = MutableStateFlow<String?>(null)

        /** "Tuesday · 52°, cloudy · rain after 6 pm", or null when Tower has no town set */
        val weather: StateFlow<String?> = _weather

        /** The list has been looked up (or there is none to look up), so Home's layout won't shift */
        private val _loaded = MutableStateFlow(false)
        val loaded: StateFlow<Boolean> = _loaded

        val userName: String
            get() =
                when {
                    KidsMode.isActive -> KidsMode.displayName
                    GuestMode.isActive -> "Guest"
                    else -> serverRepository.currentUser?.name.orEmpty()
                }

        private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun openedKey() = "opened-" + (serverRepository.currentUser?.id?.toString() ?: "none")

        // The fact titles shown last time, so the next Home open shows different ones
        private var lastFacts: List<String> = emptyList()

        fun load() {
            if (KidsMode.isActive || GuestMode.isActive) {
                _rows.value = emptyList()
                _weather.value = null
                _loaded.value = true
                return
            }
            // The last weather Tower gave shows straight away (if it's from the last 3 hours), so nothing pops in
            _weather.value = savedWeather()
            viewModelScope.launchIO {
                var news: List<HomeRow> = emptyList()
                var facts: List<HomeRow> = emptyList()
                try {
                    val opened = prefs.getStringSet(openedKey(), emptySet()).orEmpty()
                    // Benflix speed: everything is asked at the same time, so a slow Tower can't hold the doors up
                    coroutineScope {
                        val episodes = async { episodeNews() }
                        val music = async { musicNews() }
                        val bookRows = async { bookNews() }
                        val requests = async { requestNews() }
                        val fact = async { withTimeoutOrNull(FACT_WAIT_MS) { factRows() }.orEmpty() }
                        val weather = async { withTimeoutOrNull(WEATHER_WAIT_MS) { towerWeather() } }
                        news =
                            (episodes.await() + music.await() + bookRows.await() + requests.await() + updateNews())
                                .filter { it.id !in opened }
                                .take(MAX_NEWS)
                        facts = fact.await()
                        weather.await()?.let { _weather.value = it.ifBlank { null } }
                    }
                    _rows.value = arrangeHomeRows(news, onTheWayRows(), facts)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not load Home's list")
                } finally {
                    _loaded.value = true
                }
                // Then, quietly: a fresh look at your requests, so On the way is up to date (the list only
                // changes if something did)
                try {
                    requestReadyService.refresh(minGapMs = REQUESTS_GAP_MS)
                    val again = arrangeHomeRows(news, onTheWayRows(), facts)
                    if (again != _rows.value) _rows.value = again
                    if (_weather.value == null) towerWeather()?.let { _weather.value = it.ifBlank { null } }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.d(ex, "Home: second look")
                }
            }
        }

        fun open(row: HomeRow) {
            if (row.news) {
                val opened = prefs.getStringSet(openedKey(), emptySet()).orEmpty()
                // Keep the list short: only the newest 60 opened rows are remembered
                prefs.edit().putStringSet(openedKey(), (opened + row.id).toList().takeLast(60).toSet()).apply()
                _rows.value = _rows.value.filter { it.id != row.id }
            }
            row.destination?.let { navigationManager.navigateTo(it) }
        }

        private fun poster(id: java.util.UUID?): String? =
            id?.let {
                runCatching {
                    api.imageApi.getItemImageUrl(itemId = it, imageType = ImageType.PRIMARY, maxHeight = POSTER_HEIGHT)
                }.getOrNull()
            }

        /** A new episode (or a new season) of a show you're watching, added in the last 3 days */
        private suspend fun episodeNews(): List<HomeRow> {
            val cutoff = LocalDateTime.now().minusDays(NEWS_DAYS)
            val nextUp =
                try {
                    api.tvShowsApi
                        .getNextUp(
                            GetNextUpRequest(
                                userId = serverRepository.currentUser?.id,
                                fields = listOf(ItemFields.DATE_CREATED),
                                limit = 30,
                                enableResumable = false,
                                enableUserData = true,
                            ),
                        ).content.items
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Home news: next up")
                    emptyList()
                }
            return nextUp
                .filter { it.dateCreated?.isAfter(cutoff) == true }
                .sortedByDescending { it.dateCreated }
                .distinctBy { it.seriesId }
                .take(MAX_NEWS)
                .map { ep ->
                    val season = ep.parentIndexNumber
                    val newSeason = ep.indexNumber == 1 && (season ?: 1) > 1
                    HomeRow(
                        id = "ep-${ep.id}",
                        title = ep.seriesName ?: ep.name.orEmpty(),
                        detail =
                            when {
                                newSeason -> "Season $season is here"
                                season != null && ep.indexNumber != null -> "New episode: season $season, episode ${ep.indexNumber}"
                                else -> "New episode"
                            },
                        dot = BenflixDoor.SHOWS.color,
                        image = poster(ep.seriesId ?: ep.id),
                        destination = BaseItem(ep).destination(),
                        news = true,
                    )
                }
        }

        /** Benflix music: a new album or single from an artist you follow (Follow on the artist page) */
        private suspend fun musicNews(): List<HomeRow> {
            val cutoff = LocalDateTime.now().minusDays(NEWS_DAYS)
            return try {
                val followed =
                    api.itemsApi
                        .getItems(
                            org.jellyfin.sdk.model.api.request.GetItemsRequest(
                                userId = serverRepository.currentUser?.id,
                                includeItemTypes = listOf(org.jellyfin.sdk.model.api.BaseItemKind.MUSIC_ARTIST),
                                recursive = true,
                                filters = listOf(org.jellyfin.sdk.model.api.ItemFilter.IS_FAVORITE),
                                limit = 200,
                            ),
                        ).content.items
                        .map { it.id }
                if (followed.isEmpty()) return emptyList()
                api.itemsApi
                    .getItems(
                        org.jellyfin.sdk.model.api.request.GetItemsRequest(
                            userId = serverRepository.currentUser?.id,
                            includeItemTypes = listOf(org.jellyfin.sdk.model.api.BaseItemKind.MUSIC_ALBUM),
                            recursive = true,
                            albumArtistIds = followed,
                            sortBy = listOf(org.jellyfin.sdk.model.api.ItemSortBy.DATE_CREATED),
                            sortOrder = listOf(org.jellyfin.sdk.model.api.SortOrder.DESCENDING),
                            fields = listOf(ItemFields.DATE_CREATED),
                            limit = 10,
                        ),
                    ).content.items
                    .filter { it.dateCreated?.isAfter(cutoff) == true }
                    .distinctBy { it.albumArtist }
                    .take(2)
                    .map { album ->
                        HomeRow(
                            id = "music-${album.id}",
                            title = album.albumArtist ?: album.name.orEmpty(),
                            detail = "New music: ${album.name}",
                            dot = BenflixDoor.MUSIC.color,
                            image = poster(album.id),
                            destination = BaseItem(album).destination(),
                            square = true,
                            news = true,
                        )
                    }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Home news: music")
                emptyList()
            }
        }

        /** Books: a new volume of a manga you read, or a book you asked for is here (Tower works these out) */
        private suspend fun bookNews(): List<HomeRow> =
            try {
                withTimeoutOrNull(BOOK_NEWS_WAIT_MS) { books.news() }.orEmpty().take(2).map {
                    HomeRow(
                        id = "book-${it.id}",
                        title = it.name ?: it.text,
                        detail = it.line.orEmpty(),
                        dot = BenflixDoor.BOOKS.color,
                        image = books.url(it.cover),
                        destination = Destination.BookTitle(it.titleId),
                        news = true,
                    )
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                // Tower's helper isn't set up for books yet (or can't be reached): no book news
                Timber.d(ex, "Home news: books")
                emptyList()
            }

        /** Something you asked for in Requests is here */
        private suspend fun requestNews(): List<HomeRow> =
            requestReadyService.ready.value
                .mapNotNull { it.jellyfinId }
                .take(2)
                .mapNotNull { id ->
                    try {
                        val item = api.userLibraryApi.getItem(id).content
                        HomeRow(
                            id = "req-$id",
                            title = item.name.orEmpty(),
                            detail = "Your request is ready to watch",
                            dot = BenflixColors.Text,
                            image = poster(item.id),
                            destination = BaseItem(item).destination(),
                            news = true,
                        )
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        null
                    }
                }

        /**
         * A new Benflix version. The main profile sees it right away; everyone else once this TV or phone
         * has been a day behind (Kids and Guest never get news). After 3 days the update screen asks too.
         */
        private fun updateNews(): List<HomeRow> {
            val admin = serverRepository.currentUserDto?.policy?.isAdministrator == true
            val version = UpdateNotice.version.value ?: return emptyList()
            if (!admin && UpdateNotice.daysBehind.value < 1) return emptyList()
            return listOf(
                HomeRow(
                    id = "update-$version",
                    title = "Benflix update",
                    detail = "A new version is ready to install",
                    dot = BenflixColors.Text,
                    image = null,
                    destination = Destination.UpdateApp,
                    news = true,
                ),
            )
        }

        /** Your own requests still on the way (pressing one opens Requests) */
        private fun onTheWayRows(): List<HomeRow> =
            requestReadyService.onTheWay.value.map {
                HomeRow(
                    id = "way-${it.requestId}",
                    title = it.title,
                    detail = it.line,
                    dot = BenflixColors.Text,
                    image = it.posterUrl,
                    destination = Destination.Requests,
                )
            }

        /**
         * Two "Did you know" facts about movies or shows this person can watch (different every time Home opens).
         * Tower picks them; if it can't be reached, one of the built-in facts (not about one title) instead.
         */
        private suspend fun factRows(): List<HomeRow> {
            val fromTower =
                try {
                    val skip = lastFacts.joinToString(",")
                    helper
                        .getJson("/api/facts/home?n=2&max=$FACT_MAX_LENGTH&skip=$skip")
                        .optJSONArray("facts")
                        ?.let { list -> (0 until list.length()).mapNotNull { list.optJSONObject(it) } }
                        .orEmpty()
                        .mapNotNull { j ->
                            val id = j.optString("id").toUUIDOrNull() ?: return@mapNotNull null
                            val name = j.optString("name")
                            val show = j.optString("type") == "Series"
                            var fact = j.optString("fact").trim()
                            if (fact.isBlank()) return@mapNotNull null
                            // Say which title it's about when the fact doesn't
                            if (name.isNotBlank() && !fact.contains(name.substringBefore(":").trim(), ignoreCase = true)) {
                                fact = "$name: $fact"
                            }
                            HomeRow(
                                id = "fact-$id",
                                title = "Did you know?",
                                detail = fact,
                                dot = if (show) BenflixDoor.SHOWS.color else BenflixDoor.MOVIES.color,
                                image = poster(id),
                                destination = Destination.MediaItem(id, if (show) BaseItemKind.SERIES else BaseItemKind.MOVIE),
                            )
                        }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.d(ex, "Home: Tower facts")
                    emptyList()
                }
            if (fromTower.isNotEmpty()) {
                lastFacts = fromTower.map { it.id.removePrefix("fact-") }
                return fromTower
            }
            val userId = serverRepository.currentUser?.id ?: return emptyList()
            return listOfNotNull(
                funFacts.pick(api, userId)?.let {
                    HomeRow(
                        id = "fact-built-in",
                        title = "Did you know?",
                        detail = it,
                        dot = BenflixDoor.MOVIES.color,
                        image = null,
                        destination = null,
                    )
                },
            )
        }

        /** Tower's weather line with today's name in front ("" = no town set), saved for next time */
        private suspend fun towerWeather(): String? =
            try {
                val j = helper.getJson("/api/home/weather")
                val line =
                    if (j.optBoolean("ready")) {
                        java.time.LocalDate
                            .now()
                            .dayOfWeek
                            .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + " · " +
                            j.optString("line")
                    } else {
                        ""
                    }
                prefs
                    .edit()
                    .putString(WEATHER_KEY, line)
                    .putLong(WEATHER_AT_KEY, System.currentTimeMillis())
                    .apply()
                line
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                // An older Tower helper (no weather yet) or Tower can't be reached
                Timber.d(ex, "Home: weather")
                null
            }

        private fun savedWeather(): String? {
            val at = prefs.getLong(WEATHER_AT_KEY, 0L)
            if (System.currentTimeMillis() - at > WEATHER_KEEP_MS) return null
            val line = prefs.getString(WEATHER_KEY, null)?.takeIf { it.isNotBlank() } ?: return null
            // The day name is put in front when it's saved, so a line from yesterday evening isn't shown
            val today =
                java.time.LocalDate
                    .now()
                    .dayOfWeek
                    .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
            return line.takeIf { it.startsWith(today) }
        }

        companion object {
            private const val PREFS = "benflix_home_news"
            private const val WEATHER_KEY = "weather-line"
            private const val WEATHER_AT_KEY = "weather-at"
            private const val WEATHER_KEEP_MS = 3 * 3_600_000L
            private const val NEWS_DAYS = 3L
            private const val BOOK_NEWS_WAIT_MS = 1_200L
            private const val FACT_WAIT_MS = 1_200L
            private const val WEATHER_WAIT_MS = 1_200L
            private const val REQUESTS_GAP_MS = 5 * 60_000L
            private const val FACT_MAX_LENGTH = 130
            private const val POSTER_HEIGHT = 240
            const val MAX_NEWS = 3
        }
    }

/**
 * Home's list, at most 4 rows: news first, then On the way, then Did you know. One spot is kept for the fact
 * and one for On the way when there is one. The rows come in twos (2 per row of doors, approved), so when
 * there would be 3 (or 1), a second fact makes it even.
 */
fun arrangeHomeRows(
    news: List<HomeRow>,
    onTheWay: List<HomeRow>,
    facts: List<HomeRow>,
): List<HomeRow> {
    val out = mutableListOf<HomeRow>()
    val keepFact = if (facts.isNotEmpty()) 1 else 0
    val keepWay = if (onTheWay.isNotEmpty()) 1 else 0
    out += news.take(MAX_HOME_ROWS - keepFact - keepWay)
    out += onTheWay.take((MAX_HOME_ROWS - keepFact - out.size).coerceAtLeast(0))
    out += facts.take(1)
    if (out.size % 2 == 1 && facts.size > 1) out += facts[1]
    return out.take(MAX_HOME_ROWS)
}

const val MAX_HOME_ROWS = 4

/** "Good morning", "Good afternoon" or "Good evening", or the holiday on the day itself */
fun greetingFor(
    name: String,
    now: LocalTime = LocalTime.now(),
): String {
    val holiday = holidayFor(java.time.LocalDate.now())
    if (holiday != null) return holiday.text + if (name.isNotBlank()) ", $name" else ""
    val part =
        when (now.hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    return if (name.isBlank()) part else "$part, $name"
}

/**
 * The redesigned Home. Everyone but Kids and Easy mode gets the approved Today board (f3-panel, 2026-11-10):
 * the greeting with Tower's weather line under it, six square doors 3 by 2, and a soft list panel as tall as
 * the doors with up to 4 rows (news, On the way, Did you know). Phones put the list under the doors.
 * Kids and Easy mode keep the big doors: TV (and phones turned sideways, and tablets) get tall columns, phones
 * a grid, with bigger words and no descriptions or list. Focus always starts on Movies.
 *
 * Right after someone is picked on Who's watching, the doors grow out of the logo's squares, one
 * after another, with soft mallet notes; presses wait until they've landed. The list fades in as the last lands.
 */
@Composable
fun DoorsHomePage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: HomeDoorsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LifecycleStartEffect(Unit) {
        viewModel.load()
        onStopOrDispose { }
    }
    // Benflix: the admin's alert (new problem report, disk nearly full) only shows while Home is on screen
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            com.github.damontecres.wholphin.ui.components.ServerAlertNotice
                .clear()
        }
    }
    // The first-time setup for a new profile
    WelcomeGate()
    LaunchedEffect(Unit) {
        StartupTimer.mark(StartupTimer.HOME_ROWS)
        StartupTimer.finish(context)
    }
    val kids by KidsMode.active.collectAsState()
    val easy = kids || preferences.appPreferences.benflixPreferences.easyRead
    // Music is the fifth door, but not on Kids profiles (no music on Kids)
    val doors = remember(kids) { BenflixDoor.forHome(kids) }
    val lite =
        remember {
            com.github.damontecres.wholphin.services.LiteMode
                .isOn(context)
        }
    val liveRows by viewModel.rows.collectAsState()
    val liveWeather by viewModel.weather.collectAsState()
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    // Phones turned sideways and tablets use the TV layout
    val wide = isTv || config.screenWidthDp > config.screenHeightDp || config.smallestScreenWidthDp >= 600

    // The doors-grow moment, once, right after a profile is picked
    val grow = remember { BenflixIntroState.doorsPending }
    val progress = remember { BenflixDoor.entries.map { mutableFloatStateOf(if (grow) 0f else 1f) } }
    val doorRects = remember { mutableStateOf(arrayOfNulls<Rect>(BenflixDoor.entries.size)) }
    var animating by remember { mutableStateOf(grow) }
    // The doors' spots are only locked in once they've settled and the growing starts
    var started by remember { mutableStateOf(!grow) }
    // The list and weather as they were when the doors started growing (null = show the live ones)
    var frozenRows by remember { mutableStateOf<List<HomeRow>?>(null) }
    var frozenWeather by remember { mutableStateOf<String?>(null) }
    val moviesFocus = remember { FocusRequester() }
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (grow) {
            BenflixIntroState.doorsPending = false
            // Get everything ready before anything moves, so the doors grow smoothly and the notes
            // land with them on every TV: the notes in memory, Home's news in (so the doors don't
            // shift), and the doors and logo measured and settled for a few frames.
            IntroSounds.awaitReady(context)
            withTimeoutOrNull(WAIT_FOR_NEWS_MS) { viewModel.loaded.first { it } }
            waitForSettled(WAIT_FOR_DOORS_MS) {
                val rects = doors.map { doorRects.value[it.ordinal] }
                val mark = BenflixIntroState.barMark
                if (rects.any { it == null } || mark == null) {
                    null
                } else {
                    // One box that changes whenever any door or the logo moves
                    Rect(
                        rects.sumOf { it!!.left.toDouble() }.toFloat() + mark.left,
                        rects.sumOf { it!!.top.toDouble() }.toFloat() + mark.top,
                        rects.sumOf { it!!.right.toDouble() }.toFloat() + mark.right,
                        rects.sumOf { it!!.bottom.toDouble() }.toFloat() + mark.bottom,
                    )
                }
            }
            // Wake the speaker just before the first note (while the pace is measured): some TVs put the sound
            // output to sleep a few seconds after the notes loaded, and the first notes then came out late
            IntroSounds.warmUp(context)
            val pace = IntroPace.measure(context, frames = 6)
            val f = pace.stretch
            frozenRows = viewModel.rows.value
            frozenWeather = viewModel.weather.value
            started = true
            // Each door starts growing a moment after the one before, with its own note on that frame
            val stagger = DOOR_STAGGER_MS * f
            val growMs = DOOR_GROW_MS * f
            // Ben: the violet Music door grows out of the small gap in the middle of the logo, one beat
            // after Games, with its own (slightly louder) note
            val musicStart = MUSIC_DOOR_START_MS * f
            val musicGrow = MUSIC_DOOR_GROW_MS * f
            // Books grows out of the same middle spot one beat after Music (on Music's beat on Kids, which has no Music)
            val booksStart = if (BenflixDoor.MUSIC in doors) musicStart + stagger else musicStart
            val starts =
                doors.map {
                    when (it) {
                        BenflixDoor.MUSIC -> musicStart
                        BenflixDoor.BOOKS -> booksStart
                        else -> it.ordinal * stagger
                    }
                }
            val lengths = doors.map { if (it == BenflixDoor.MUSIC || it == BenflixDoor.BOOKS) musicGrow else growMs }
            val total = doors.indices.maxOf { starts[it] + lengths[it] }
            var played = 0
            runIntroTimeline(total, pace) { t ->
                while (played < doors.size && t >= starts[played]) {
                    if (doors[played] == BenflixDoor.MUSIC) {
                        IntroSounds.play(context, IntroSounds.MUSIC_DOOR, MUSIC_NOTE_VOLUME)
                    } else if (doors[played] == BenflixDoor.BOOKS) {
                        IntroSounds.play(context, IntroSounds.BOOKS_DOOR, MUSIC_NOTE_VOLUME)
                    } else {
                        IntroSounds.DOORS.getOrNull(played)?.let { IntroSounds.play(context, it, DOOR_NOTE_VOLUME) }
                    }
                    played++
                }
                doors.forEachIndexed { i, door ->
                    val p = ((t - starts[i]) / lengths[i]).coerceIn(0f, 1f)
                    progress[door.ordinal].floatValue = FastOutSlowInEasing.transform(p)
                }
            }
            animating = false
            frozenRows = null
            frozenWeather = null
        }
        if (!focusedOnce || grow) {
            focusedOnce = true
            moviesFocus.tryRequestFocus()
        }
    }

    val onDoor: (BenflixDoor) -> Unit = { door ->
        // Approved door colour fill: the door's colour grows to fill the screen, then the page opens
        DoorWipe.start(door.color, doorRects.value.getOrNull(door.ordinal)) {
            viewModel.navigationManager.navigateTo(
                when (door) {
                    BenflixDoor.MOVIES -> Destination.MoviesDoor
                    BenflixDoor.SHOWS -> Destination.ShowsDoor
                    BenflixDoor.LIVE_TV -> Destination.Channels
                    BenflixDoor.GAMES -> Destination.Games
                    BenflixDoor.MUSIC -> Destination.MusicDoor
                    BenflixDoor.BOOKS -> Destination.BooksDoor
                },
            )
        }
    }

    val doorModifier: (Int) -> Modifier = { i ->
        Modifier
            .onGloballyPositioned { coords ->
                if (!started || !animating) {
                    val now = coords.boundsInWindow()
                    if (doorRects.value[i] != now) {
                        val copy = doorRects.value.copyOf()
                        copy[i] = now
                        doorRects.value = copy
                    }
                }
            }.graphicsLayer {
                val p = progress[i].value
                if (p >= 1f) return@graphicsLayer
                if (lite) {
                    // Lite mode: a simple fade, nothing moves
                    alpha = p
                    return@graphicsLayer
                }
                val door = doorRects.value[i]
                val mark = BenflixIntroState.barMark
                if (door == null || mark == null || door.width <= 0f) {
                    alpha = 0f
                    return@graphicsLayer
                }
                // The matching logo square: top left Movies, top right Shows, bottom left Live TV, bottom right
                // Games. Music starts as a tiny square in the gap at the very middle of the logo.
                val gap = mark.width * 0.1f
                // Music and Books both grow out of the tiny gap in the middle of the logo
                val centre = i == BenflixDoor.MUSIC.ordinal || i == BenflixDoor.BOOKS.ordinal
                val sq = if (centre) gap else (mark.width - gap) / 2f
                val sx =
                    when {
                        centre -> mark.left + (mark.width - gap) / 2f
                        i % 2 == 0 -> mark.left
                        else -> mark.left + sq + gap
                    }
                val sy =
                    when {
                        centre -> mark.top + (mark.height - gap) / 2f
                        i < 2 -> mark.top
                        else -> mark.top + sq + gap
                    }
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = sq / door.width + (1f - sq / door.width) * p
                scaleY = sq / door.height + (1f - sq / door.height) * p
                translationX = (sx - door.left) * (1f - p)
                translationY = (sy - door.top) * (1f - p)
            }
    }
    // News that arrives while the doors are growing waits until they've landed, so nothing shifts under
    // the animation (a jump mid-grow was the stutter Ben saw)
    val rows = frozenRows ?: liveRows
    val weather = if (frozenRows != null) frozenWeather else liveWeather
    val labelAlpha: (Int) -> Float = { i -> ((progress[i].value - 0.75f) / 0.25f).coerceIn(0f, 1f) }
    // The list fades in as the last door lands
    val lastDoor = doors.last().ordinal
    val listAlpha: () -> Float = { ((progress[lastDoor].value - 0.6f) / 0.4f).coerceIn(0f, 1f) }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                // Presses wait until the doors have landed
                .onPreviewKeyEvent { animating }
                .pointerInput(animating) {
                    if (animating) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false).consume()
                        }
                    }
                },
    ) {
        if (!easy) {
            // The approved Today board (f3-panel): six square doors 3 by 2, the list beside them (TV) or under them (phone)
            HomeBoard(
                greeting = greetingFor(viewModel.userName),
                weather = weather,
                doors = doors,
                rows = rows,
                wide = wide,
                labelAlpha = labelAlpha,
                listAlpha = listAlpha,
                doorModifier = { door ->
                    doorModifier(door.ordinal).then(if (door.ordinal == 0) Modifier.focusRequester(moviesFocus) else Modifier)
                },
                onDoor = onDoor,
                onRow = viewModel::open,
            )
        } else if (wide) {
            // Kids and Easy mode keep the big doors (approved)
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(end = 24.dp, top = 12.dp, bottom = 34.dp),
            ) {
                Text(
                    text = greetingFor(viewModel.userName),
                    color = BenflixColors.Text,
                    fontSize = if (easy) BenflixType.hero else BenflixType.pageTitle,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(32.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                ) {
                    doors.forEach { door ->
                        val i = door.ordinal
                        DoorTile(
                            door = door,
                            easy = easy,
                            wide = true,
                            labelAlpha = labelAlpha(i),
                            onClick = { onDoor(door) },
                            modifier =
                                doorModifier(i)
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .then(if (i == 0) Modifier.focusRequester(moviesFocus) else Modifier),
                        )
                    }
                }
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(end = 8.dp, top = 8.dp, bottom = 24.dp),
            ) {
                Text(
                    text = greetingFor(viewModel.userName),
                    color = BenflixColors.Text,
                    fontSize = BenflixType.pageTitle,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(20.dp))
                // Phones: the doors two by two (six doors = three rows, approved h-door); a door left on its own is wide
                val pairs = doors.chunked(2)
                pairs.forEach { pair ->
                    if (pair.size == 1) {
                        val door = pair.first()
                        val i = door.ordinal
                        DoorTile(
                            door = door,
                            easy = easy,
                            wide = false,
                            labelAlpha = labelAlpha(i),
                            onClick = { onDoor(door) },
                            sideways = true,
                            modifier =
                                doorModifier(i)
                                    .fillMaxWidth()
                                    .height(if (easy) 112.dp else 96.dp),
                        )
                        return@forEach
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        pair.forEach { door ->
                            val i = door.ordinal
                            DoorTile(
                                door = door,
                                easy = easy,
                                wide = false,
                                labelAlpha = labelAlpha(i),
                                onClick = { onDoor(door) },
                                modifier =
                                    doorModifier(i)
                                        .weight(1f)
                                        .aspectRatio(if (pairs.size > 2) PHONE_DOOR_RATIO_SIX else PHONE_DOOR_RATIO)
                                        .then(if (i == 0) Modifier.focusRequester(moviesFocus) else Modifier),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One solid colour door: line icon at the top, name and description at the bottom. Focus = white outline only. */
@Composable
private fun DoorTile(
    door: BenflixDoor,
    easy: Boolean,
    wide: Boolean,
    labelAlpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sideways: Boolean = false,
) {
    val shape = RoundedCornerShape(if (wide) 14.dp else 18.dp)
    val icon =
        when (door) {
            BenflixDoor.MOVIES -> BenflixIcon.FILM
            BenflixDoor.SHOWS -> BenflixIcon.TV
            BenflixDoor.LIVE_TV -> BenflixIcon.LIVE
            BenflixDoor.GAMES -> BenflixIcon.PAD
            BenflixDoor.MUSIC -> BenflixIcon.NOTE
            BenflixDoor.BOOKS -> BenflixIcon.BOOK
        }
    val nameSize: TextUnit
    val descSize: TextUnit
    val iconSize: Dp
    val pad: Dp
    if (wide) {
        nameSize = if (easy) 46.sp else 38.sp
        descSize = 15.sp
        iconSize = 44.dp
        pad = 22.dp
    } else {
        nameSize = if (easy) 30.sp else 25.sp
        descSize = 12.sp
        iconSize = 36.dp
        pad = 16.dp
    }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = door.color,
                contentColor = door.onColor,
                focusedContainerColor = door.color,
                focusedContentColor = door.onColor,
                pressedContainerColor = door.color,
                pressedContentColor = door.onColor,
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder =
                    Border(
                        border = androidx.compose.foundation.BorderStroke(4.dp, BenflixColors.Text),
                        inset = 0.dp,
                        shape = shape,
                    ),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = labelAlpha }
                .padding(pad),
        ) {
            // Every door uses the same name size, small enough that the longest name ("Live TV") fits in full,
            // so five doors on a TV never cut a name short ("Movie", "Game") and all names stay level
            val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
            val fitSize = ((if (sideways) maxWidth - iconSize - 16.dp else maxWidth).value / 4.5f / fontScale).sp
            val nameFits = if (fitSize < nameSize) fitSize else nameSize
            // Same idea for the words under the name: one size for every door, small enough that the longest
            // ("Your songs and playlists") fits in two lines, so six doors on a TV never cut them short
            val measurer = rememberTextMeasurer()
            val textWidth =
                with(androidx.compose.ui.platform.LocalDensity.current) {
                    (if (sideways) maxWidth - iconSize - 16.dp else maxWidth).roundToPx().coerceAtLeast(1)
                }
            val descFits =
                remember(textWidth, descSize, fontScale) {
                    var size = descSize.value
                    while (size > 9f &&
                        BenflixDoor.entries.any { d ->
                            measurer
                                .measure(
                                    text = d.description,
                                    style =
                                        TextStyle(
                                            fontSize = size.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                    constraints = Constraints(maxWidth = textWidth),
                                    maxLines = 2,
                                ).hasVisualOverflow
                        }
                    ) {
                        size -= 0.5f
                    }
                    size.sp
                }
            BenflixIconImage(
                icon = icon,
                color = door.onColor,
                size = iconSize,
                modifier = Modifier.align(if (sideways) Alignment.CenterStart else Alignment.TopStart),
            )
            Column(
                modifier =
                    if (sideways) {
                        Modifier.align(Alignment.CenterStart).padding(start = iconSize + 16.dp)
                    } else {
                        Modifier.align(Alignment.BottomStart)
                    },
            ) {
                Text(
                    text = door.title,
                    color = door.onColor,
                    fontSize = nameFits,
                    lineHeight = nameFits,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                )
                if (!easy) {
                    Text(
                        text = door.description,
                        color = door.onColor.copy(alpha = .85f),
                        fontSize = descFits,
                        lineHeight = descFits * 1.25f,
                        fontWeight = FontWeight.SemiBold,
                        // Always two lines tall, so a door whose words fit on one line keeps its name level with the rest
                        minLines = if (sideways) 1 else 2,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** Phone doors: a little taller than wide */
private const val PHONE_DOOR_RATIO = 0.8f

/** Phones with six doors (Books): a little wider than tall, so all three rows fit */
private const val PHONE_DOOR_RATIO_SIX = 1.12f
private const val DOOR_STAGGER_MS = 70f
private const val DOOR_GROW_MS = 560f

/**
 * The Music door grows out of the middle of the logo right on the next beat after Games (same spacing as the
 * four, so its note keeps the rhythm; Ben heard a gap at 330 ms), and grows only a little slower than the others
 */
private const val MUSIC_DOOR_START_MS = 4 * DOOR_STAGGER_MS
private const val MUSIC_DOOR_GROW_MS = 640f

/** The Music door's note is a little louder than the other four */
private const val MUSIC_NOTE_VOLUME = 0.46f

/** The longest Home waits for its news before the doors grow (a slow server goes on anyway) */
private const val WAIT_FOR_NEWS_MS = 1500L

/** The longest Home waits for the doors to settle in place */
private const val WAIT_FOR_DOORS_MS = 1000L
