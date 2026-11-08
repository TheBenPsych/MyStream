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
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import timber.log.Timber
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
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
 * a new season of a show you watched, a request that's ready, and an app update (main profile).
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
                    val items = (episodeNews() + requestNews() + updateNews()).filter { it.id !in opened }
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
            val cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(NEWS_DAYS)
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

        /** A new Benflix version, only for the main profile */
        private fun updateNews(): List<HomeNews> {
            val admin = serverRepository.currentUserDto?.policy?.isAdministrator == true
            val version = UpdateNotice.version.value ?: return emptyList()
            if (!admin) return emptyList()
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
                val rects = doorRects.value
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
            var played = 0
            runIntroTimeline(stagger * (progress.size - 1) + growMs, pace) { t ->
                while (played < progress.size && t >= played * stagger) {
                    IntroSounds.play(context, IntroSounds.DOORS[played], DOOR_NOTE_VOLUME)
                    played++
                }
                progress.forEachIndexed { i, state ->
                    val p = ((t - i * stagger) / growMs).coerceIn(0f, 1f)
                    state.floatValue = FastOutSlowInEasing.transform(p)
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
        viewModel.navigationManager.navigateTo(
            when (door) {
                BenflixDoor.MOVIES -> Destination.MoviesDoor
                BenflixDoor.SHOWS -> Destination.ShowsDoor
                BenflixDoor.LIVE_TV -> Destination.Channels
                BenflixDoor.GAMES -> Destination.Games
            },
        )
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
                val door = doorRects.value[i]
                val mark = BenflixIntroState.barMark
                if (door == null || mark == null || door.width <= 0f) {
                    alpha = 0f
                    return@graphicsLayer
                }
                // The matching logo square: top left Movies, top right Shows, bottom left Live TV, bottom right Games
                val gap = mark.width * 0.1f
                val sq = (mark.width - gap) / 2f
                val sx = mark.left + if (i % 2 == 0) 0f else sq + gap
                val sy = mark.top + if (i < 2) 0f else sq + gap
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
                    BenflixDoor.entries.forEachIndexed { i, door ->
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
                BenflixDoor.entries.chunked(2).forEachIndexed { rowIndex, pair ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        pair.forEachIndexed { j, door ->
                            val i = rowIndex * 2 + j
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
) {
    val shape = RoundedCornerShape(if (wide) 14.dp else 18.dp)
    val icon =
        when (door) {
            BenflixDoor.MOVIES -> BenflixIcon.FILM
            BenflixDoor.SHOWS -> BenflixIcon.TV
            BenflixDoor.LIVE_TV -> BenflixIcon.LIVE
            BenflixDoor.GAMES -> BenflixIcon.PAD
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
            BenflixIconImage(icon = icon, color = door.onColor, size = iconSize, modifier = Modifier.align(Alignment.TopStart))
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
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

/** The longest Home waits for its news before the doors grow (a slow server goes on anyway) */
private const val WAIT_FOR_NEWS_MS = 1500L

/** The longest Home waits for the doors to settle in place */
private const val WAIT_FOR_DOORS_MS = 1000L
