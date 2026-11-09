package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import timber.log.Timber
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

/** One news pill under the greeting. [door] picks the dot colour (null = white, app news). */
data class HomeNews(
    val id: String,
    val text: String,
    val door: BenflixDoor?,
    val destination: Destination,
)

/**
 * Home's greeting and news (approved h2-pills): up to 3 pills about new episodes of shows you watch,
 * a new season of a show you watched, a request that's ready, and an app update (everyone, see updateNews).
 * Nothing from Live TV or Games. A pill goes away once opened, after 3 days, or when newer news of
 * the same kind pushes it out. Each profile only sees its own news.
 */
@HiltViewModel
class HomeDoorsViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val requestReadyService: RequestReadyService,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _news = MutableStateFlow<List<HomeNews>>(emptyList())
        val news: StateFlow<List<HomeNews>> = _news

        /** The news has been looked up (or there is none to look up), so Home's layout won't shift */
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

        fun load() {
            if (KidsMode.isActive || GuestMode.isActive) {
                _news.value = emptyList()
                _loaded.value = true
                return
            }
            viewModelScope.launchIO {
                try {
                    val opened = prefs.getStringSet(openedKey(), emptySet()).orEmpty()
                    val items = (episodeNews() + musicNews() + requestNews() + updateNews()).filter { it.id !in opened }
                    _news.value = items.take(MAX_NEWS)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not load Home news")
                } finally {
                    _loaded.value = true
                }
            }
        }

        fun open(news: HomeNews) {
            val opened = prefs.getStringSet(openedKey(), emptySet()).orEmpty()
            // Keep the list short: only the newest 60 opened pills are remembered
            prefs.edit().putStringSet(openedKey(), (opened + news.id).toList().takeLast(60).toSet()).apply()
            _news.value = _news.value.filter { it.id != news.id }
            navigationManager.navigateTo(news.destination)
        }

        /** A new episode (or a new season) of a show you're watching, added in the last 3 days */
        private suspend fun episodeNews(): List<HomeNews> {
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
                    val show = ep.seriesName ?: ep.name.orEmpty()
                    val newSeason = ep.indexNumber == 1 && (ep.parentIndexNumber ?: 1) > 1
                    HomeNews(
                        id = "ep-${ep.id}",
                        text =
                            if (newSeason) {
                                "$show season ${ep.parentIndexNumber} is here"
                            } else {
                                "$show has a new episode"
                            },
                        door = BenflixDoor.SHOWS,
                        destination = BaseItem(ep).destination(),
                    )
                }
        }

        /** Benflix music: a new album or single from an artist you follow (Follow on the artist page) */
        private suspend fun musicNews(): List<HomeNews> {
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
                        HomeNews(
                            id = "music-${album.id}",
                            text = "New music from ${album.albumArtist ?: album.name}",
                            door = BenflixDoor.MUSIC,
                            destination = BaseItem(album).destination(),
                        )
                    }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Home news: music")
                emptyList()
            }
        }

        /** Something you asked for in Requests is here */
        private suspend fun requestNews(): List<HomeNews> =
            requestReadyService.ready.value
                .mapNotNull { it.jellyfinId }
                .take(2)
                .mapNotNull { id ->
                    try {
                        val item = api.userLibraryApi.getItem(id).content
                        val movie = item.type == org.jellyfin.sdk.model.api.BaseItemKind.MOVIE
                        HomeNews(
                            id = "req-$id",
                            text = "${item.name} is ready to watch",
                            door = if (movie) BenflixDoor.MOVIES else BenflixDoor.SHOWS,
                            destination = BaseItem(item).destination(),
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
        private fun updateNews(): List<HomeNews> {
            val admin = serverRepository.currentUserDto?.policy?.isAdministrator == true
            val version = UpdateNotice.version.value ?: return emptyList()
            if (!admin && UpdateNotice.daysBehind.value < 1) return emptyList()
            return listOf(
                HomeNews(
                    id = "update-$version",
                    text = "A Benflix update is ready",
                    door = null,
                    destination = Destination.UpdateApp,
                ),
            )
        }

        companion object {
            private const val PREFS = "benflix_home_news"
            private const val NEWS_DAYS = 3L
            const val MAX_NEWS = 3
        }
    }

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
 * The redesigned Home (approved): a greeting, up to three news pills, and four big solid colour doors:
 * Movies, Shows, Live TV and Games. TV (and phones turned sideways, and tablets) get four tall
 * columns; phones get a 2 x 2 grid. Focus always starts on Movies. Easy mode (Kids, or anyone who
 * turns on bigger words) keeps the doors and greeting but drops the descriptions and news.
 *
 * Right after someone is picked on Who's watching, the doors grow out of the logo's four squares, one
 * after another, with four soft mallet notes; presses wait until they've landed.
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
    val news by viewModel.news.collectAsState()
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
            val pace = IntroPace.measure(context, frames = 6)
            val f = pace.stretch
            started = true
            // Each door starts growing a moment after the one before, with its own note on that frame
            val stagger = DOOR_STAGGER_MS * f
            val growMs = DOOR_GROW_MS * f
            // Ben: the violet Music door grows out of the small gap in the middle of the logo, a moment
            // after the four, with its own (slightly louder) note
            val musicStart = MUSIC_DOOR_START_MS * f
            val musicGrow = MUSIC_DOOR_GROW_MS * f
            val starts = doors.map { if (it == BenflixDoor.MUSIC) musicStart else it.ordinal * stagger }
            val lengths = doors.map { if (it == BenflixDoor.MUSIC) musicGrow else growMs }
            val total = doors.indices.maxOf { starts[it] + lengths[it] }
            var played = 0
            runIntroTimeline(total, pace) { t ->
                while (played < doors.size && t >= starts[played]) {
                    if (doors[played] == BenflixDoor.MUSIC) {
                        IntroSounds.play(context, IntroSounds.MUSIC_DOOR, MUSIC_NOTE_VOLUME)
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
                val sq = if (i == BenflixDoor.MUSIC.ordinal) gap else (mark.width - gap) / 2f
                val sx =
                    when {
                        i == BenflixDoor.MUSIC.ordinal -> mark.left + (mark.width - gap) / 2f
                        i % 2 == 0 -> mark.left
                        else -> mark.left + sq + gap
                    }
                val sy =
                    when {
                        i == BenflixDoor.MUSIC.ordinal -> mark.top + (mark.height - gap) / 2f
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
    val labelAlpha: (Int) -> Float = { i -> ((progress[i].value - 0.75f) / 0.25f).coerceIn(0f, 1f) }

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
        if (wide) {
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
                if (!easy && news.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 14.dp),
                    ) {
                        news.forEach { NewsPill(it, onClick = { viewModel.open(it) }) }
                    }
                }
                Spacer(Modifier.height(if (!easy && news.isNotEmpty()) 24.dp else 32.dp))
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
                if (!easy && news.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        news.forEach { NewsPill(it, onClick = { viewModel.open(it) }) }
                    }
                }
                Spacer(Modifier.height(20.dp))
                doors.filter { it != BenflixDoor.MUSIC }.chunked(2).forEach { pair ->
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
                                        .aspectRatio(PHONE_DOOR_RATIO)
                                        .then(if (i == 0) Modifier.focusRequester(moviesFocus) else Modifier),
                            )
                        }
                    }
                }
                // Phones: Music is a wide fifth door under the four
                if (BenflixDoor.MUSIC in doors) {
                    val i = BenflixDoor.MUSIC.ordinal
                    DoorTile(
                        door = BenflixDoor.MUSIC,
                        easy = easy,
                        wide = false,
                        labelAlpha = labelAlpha(i),
                        onClick = { onDoor(BenflixDoor.MUSIC) },
                        sideways = true,
                        modifier =
                            doorModifier(i)
                                .fillMaxWidth()
                                .height(if (easy) 112.dp else 96.dp),
                    )
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
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = labelAlpha }
                .padding(pad),
        ) {
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
                    fontSize = nameSize,
                    lineHeight = nameSize,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
                if (!easy) {
                    Text(
                        text = door.description,
                        color = door.onColor.copy(alpha = .85f),
                        fontSize = descSize,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** A rounded news pill with a dot in its door's colour (white for app news) */
@Composable
private fun NewsPill(
    news: HomeNews,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.Raised,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border =
            com.github.damontecres.wholphin.ui.nav
                .focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Box(
                Modifier
                    .size(9.dp)
                    .background(news.door?.color ?: BenflixColors.Text, CircleShape),
            )
            Text(text = news.text, fontSize = BenflixType.small, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** Phone doors: a little taller than wide */
private const val PHONE_DOOR_RATIO = 0.8f
private const val DOOR_STAGGER_MS = 70f
private const val DOOR_GROW_MS = 560f

/** The Music door starts growing out of the middle of the logo a moment after the four (Ben) */
private const val MUSIC_DOOR_START_MS = 330f
private const val MUSIC_DOOR_GROW_MS = 800f

/** The Music door's note is a little louder than the other four */
private const val MUSIC_NOTE_VOLUME = 0.46f

/** The longest Home waits for its news before the doors grow (a slow server goes on anyway) */
private const val WAIT_FOR_NEWS_MS = 1500L

/** The longest Home waits for the doors to settle in place */
private const val WAIT_FOR_DOORS_MS = 1000L
