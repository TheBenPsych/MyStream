package com.github.damontecres.wholphin.ui.main

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.BenflixIntroState
import com.github.damontecres.wholphin.ui.IntroPace
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.destination
import com.github.damontecres.wholphin.ui.runIntroTimeline
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.util.StartupTimer
import dagger.hilt.android.lifecycle.HiltViewModel
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
import org.jellyfin.sdk.model.api.ImageType
import timber.log.Timber
import java.time.LocalTime
import javax.inject.Inject

/**
 * Home's data. Grown-ups get the Glowfin Home (its rows come from DoorViewModel). Kids and Guest get their own
 * smaller Home, and this looks up what goes on it. Home has no news list any more (Ben 2026-11-18).
 */
@HiltViewModel
class HomeDoorsViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val helper: com.github.damontecres.wholphin.services.GameLibraryClient,
        private val doorData: com.github.damontecres.wholphin.ui.door.DoorDataService,
        private val localGames: com.github.damontecres.wholphin.games.LocalGames,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private fun familyKey(kind: FamilyKind) = "${serverRepository.currentUser?.id}-${KidsMode.level}-${kind.name}"

        private val _family = MutableStateFlow(FamilyHomeData())

        /** What Kids, Easy mode and Guest show under their doors. Each profile has its own (never another's rows). */
        val family: StateFlow<FamilyHomeData> = _family

        /** The smaller Homes: Keep watching (Kids, Easy mode) or Popular here this week (Guest), and the Kids door lines */
        fun loadFamily(kind: FamilyKind) {
            val key = familyKey(kind)
            // Last time's rows for this same profile show at once; anyone else's are never shown
            _family.value = familyMemory[key] ?: FamilyHomeData(key = key)
            val userId = serverRepository.currentUser?.id ?: return
            viewModelScope.launchIO {
                try {
                    val cards =
                        if (kind == FamilyKind.GUEST) popularCards(userId) else keepWatchingCards(userId)
                    val lines = if (kind == FamilyKind.KIDS) kidsDoorLines(userId) else emptyMap()
                    val picks = if (kind == FamilyKind.KIDS) doorData.kidsPicks(userId).map { card(it) } else emptyList()
                    val recent =
                        if (kind == FamilyKind.GUEST) {
                            doorData
                                .recentlyAdded(userId)
                                .map { card(it) }
                                .distinctBy { it.id }
                                .take(FAMILY_ROW)
                        } else {
                            emptyList()
                        }
                    // Only if this profile is still the one on screen
                    if (familyKey(kind) != key) return@launchIO
                    val data = FamilyHomeData(key = key, cards = cards, picks = picks, recent = recent, lines = lines, loaded = true)
                    familyMemory[key] = data
                    _family.value = data
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Home: family rows")
                    if (familyKey(kind) == key) _family.value = _family.value.copy(loaded = true)
                }
            }
        }

        private fun card(item: BaseItem): FamilyCard {
            val showId = if (item.type == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE) item.data.seriesId else null
            return FamilyCard(
                id = (showId ?: item.id).toString(),
                title = item.title ?: item.name.orEmpty(),
                image = poster(showId ?: item.id),
                destination = item.destination(),
            )
        }

        /** Movies and shows started but not finished, newest first (Kids only ever gets kids titles back) */
        private suspend fun keepWatchingCards(userId: java.util.UUID): List<FamilyCard> =
            coroutineScope {
                val movies = async { doorData.resumeMovies(userId) }
                val episodes = async { doorData.keepWatchingEpisodes(userId) }
                (movies.await() + episodes.await())
                    .sortedByDescending { it.data.userData?.lastPlayedDate }
                    .map { card(it) }
                    .distinctBy { it.id }
                    .take(FAMILY_ROW)
            }

        /** Popular here this week: what the household watched most (from Tower), or else what's trending on Benflix */
        private suspend fun popularCards(userId: java.util.UUID): List<FamilyCard> {
            val household =
                try {
                    val answer = helper.getJson("/api/popular").optJSONArray("items")
                    (0 until (answer?.length() ?: 0)).mapNotNull {
                        answer?.optJSONObject(it)?.optString("id")?.let { id -> runCatching { java.util.UUID.fromString(id) }.getOrNull() }
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.i("Guest popular: %s", ex.message)
                    emptyList()
                }
            val picks = doorData.byIds(userId, household)
            val items =
                if (picks.size >= POPULAR_MIN) {
                    picks
                } else {
                    coroutineScope {
                        val movies =
                            async { doorData.top10(userId, org.jellyfin.sdk.model.api.BaseItemKind.MOVIE, emptySet(), anime = false) }
                        val shows =
                            async { doorData.top10(userId, org.jellyfin.sdk.model.api.BaseItemKind.SERIES, emptySet(), anime = false) }
                        val m = movies.await()
                        val s = shows.await()
                        (0 until maxOf(m.size, s.size)).flatMap { listOfNotNull(m.getOrNull(it), s.getOrNull(it)) }
                    }
                }
            return items.map { card(it) }.distinctBy { it.id }.take(FAMILY_ROW)
        }

        /** Kids doors name a couple of titles from this profile: "Bluey, Frozen and more" */
        private suspend fun kidsDoorLines(userId: java.util.UUID): Map<BenflixDoor, String> =
            coroutineScope {
                fun words(names: List<String>): String? =
                    names
                        .filter { it.isNotBlank() }
                        .distinct()
                        .take(
                            2,
                        ).takeIf { it.isNotEmpty() }
                        ?.joinToString(", ")
                        ?.let { "$it and more" }

                suspend fun newest(type: org.jellyfin.sdk.model.api.BaseItemKind): List<String> =
                    try {
                        api.itemsApi
                            .getItems(
                                org.jellyfin.sdk.model.api.request.GetItemsRequest(
                                    userId = userId,
                                    includeItemTypes = listOf(type),
                                    recursive = true,
                                    sortBy =
                                        listOf(
                                            org.jellyfin.sdk.model.api.ItemSortBy.DATE_PLAYED,
                                            org.jellyfin.sdk.model.api.ItemSortBy.DATE_CREATED,
                                        ),
                                    sortOrder = listOf(org.jellyfin.sdk.model.api.SortOrder.DESCENDING),
                                    enableImages = false,
                                    enableTotalRecordCount = false,
                                    limit = 4,
                                ),
                            ).content.items
                            .mapNotNull { it.name }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.d(ex, "Kids door line")
                        emptyList()
                    }
                val movies = async { newest(org.jellyfin.sdk.model.api.BaseItemKind.MOVIE) }
                val shows = async { newest(org.jellyfin.sdk.model.api.BaseItemKind.SERIES) }
                val games =
                    try {
                        localGames.downloaded().filter { it.ref.kids }.map { it.ref.name }
                    } catch (ex: Exception) {
                        emptyList()
                    }
                listOfNotNull(
                    words(movies.await())?.let { BenflixDoor.MOVIES to it },
                    words(shows.await())?.let { BenflixDoor.SHOWS to it },
                    (words(games) ?: "Games to play on the TV").let { BenflixDoor.GAMES to it },
                ).toMap()
            }

        /** This profile's id (Easy mode's door order is kept per profile) */
        val userId: java.util.UUID? get() = serverRepository.currentUser?.id

        private fun poster(id: java.util.UUID?): String? =
            id?.let {
                runCatching {
                    api.imageApi.getItemImageUrl(itemId = it, imageType = ImageType.PRIMARY, maxHeight = POSTER_HEIGHT)
                }.getOrNull()
            }

        companion object {
            /** The smaller Homes' rows, kept per profile while Benflix is open */
            private val familyMemory = java.util.concurrent.ConcurrentHashMap<String, FamilyHomeData>()
            private const val FAMILY_ROW = 12
            private const val POPULAR_MIN = 5
            private const val POSTER_HEIGHT = 240
        }
    }

/** "Good morning", "Good afternoon" or "Good evening" */
fun greetingFor(
    name: String,
    now: LocalTime = LocalTime.now(),
): String {
    val part =
        when (now.hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    return if (name.isBlank()) part else "$part, $name"
}

/**
 * Glowfin Home: grown-ups get the top area + rows Home ([GlowfinHome]); Kids and Guest get their own smaller Home.
 * The doors live in the rail on the left of every page.
 *
 * Right after someone is picked on Who's watching, the rail's door icons light up one after another with the
 * same soft mallet notes the doors had; presses wait until they're done. The first pick blooms in on the last note.
 */
@Composable
fun DoorsHomePage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: HomeDoorsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val kids by KidsMode.active.collectAsState()
    val guestOn by GuestMode.active.collectAsState()
    // Kids, Big kids and Guest get their own Home (Glowfin K1, ui-17). Easy mode is gone.
    val familyKind = remember(kids, guestOn) { familyKindNow() }
    val familyData by viewModel.family.collectAsState()
    // Kids and Guest get their own smaller Home
    val smallHome = familyKind == FamilyKind.KIDS || familyKind == FamilyKind.GUEST
    LifecycleStartEffect(familyKind) {
        if (smallHome) familyKind?.let { viewModel.loadFamily(it) }
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
    // The rail's doors that light up at start-up
    val doors =
        when (familyKind) {
            FamilyKind.KIDS -> KidsDoors
            FamilyKind.GUEST -> listOf(BenflixDoor.MOVIES, BenflixDoor.SHOWS)
            else -> BenflixDoor.entries.filter { it != BenflixDoor.ANIME }
        }
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    // Phones turned sideways and tablets use the TV layout
    val wide = isTv || config.screenWidthDp > config.screenHeightDp || config.smallestScreenWidthDp >= 600

    // The start-up light-up (approved in-light), once, right after a profile is picked: the rail's door icons
    // light up in their colours top to bottom, one after another, and the
    // first pick blooms in after the last one
    val lightUp = remember { BenflixIntroState.doorsPending }
    val bloom = remember { mutableFloatStateOf(if (lightUp) 0f else 1f) }
    var animating by remember { mutableStateOf(lightUp) }
    val playFocus = remember { FocusRequester() }
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (lightUp) {
            com.github.damontecres.wholphin.ui.nav.RailIntro
                .prepare()
            BenflixIntroState.doorsPending = false
            try {
                // Everything ready before anything moves (no sound: Ben wants it clean and plain)
                if (smallHome) withTimeoutOrNull(WAIT_FOR_ROWS_MS) { viewModel.family.first { it.loaded } }
                val pace = IntroPace.measure(context, frames = 6)
                val f = pace.stretch
                val stagger = DOOR_STAGGER_MS * f
                val light = LIGHT_MS * f
                val musicStart = MUSIC_DOOR_START_MS * f
                val booksStart = if (BenflixDoor.MUSIC in doors) musicStart + stagger else musicStart
                val starts =
                    doors.map {
                        when (it) {
                            BenflixDoor.MUSIC -> musicStart
                            BenflixDoor.BOOKS -> booksStart
                            else -> it.ordinal * stagger
                        }
                    }
                val lastStart = starts.max()
                val bloomMs = BLOOM_MS * f
                val total = maxOf(lastStart + light, lastStart + bloomMs)
                val intro = com.github.damontecres.wholphin.ui.nav.RailIntro
                runIntroTimeline(total, pace) { t ->
                    doors.forEachIndexed { i, door ->
                        val p = ((t - starts[i]) / light).coerceIn(0f, 1f)
                        intro.lit[door.ordinal].floatValue = FastOutSlowInEasing.transform(p)
                        intro.glow[door.ordinal].floatValue = kotlin.math.sin(p * Math.PI).toFloat()
                    }
                    bloom.floatValue = FastOutSlowInEasing.transform(((t - lastStart) / bloomMs).coerceIn(0f, 1f))
                }
                // Then the icons settle back to their usual look
                val settle = SETTLE_MS * f
                runIntroTimeline(settle, pace) { t -> intro.settle.floatValue = 1f - t / settle }
            } finally {
                com.github.damontecres.wholphin.ui.nav.RailIntro
                    .finish()
                bloom.floatValue = 1f
                animating = false
            }
        }
        if (!focusedOnce || lightUp) {
            focusedOnce = true
            focusPlay(playFocus)
        }
    }

    val family = familyKind == FamilyKind.KIDS || familyKind == FamilyKind.GUEST
    Box(
        modifier =
            modifier
                .fillMaxSize()
                // Presses wait until the light-up is over
                .onPreviewKeyEvent { animating }
                .pointerInput(animating) {
                    if (animating) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false).consume()
                        }
                    }
                },
    ) {
        if (!family) {
            // Glowfin (ui-6): the top area + rows Home
            GlowfinHome()
            return@Box
        }
        // Glowfin K1 (ui-17): the Kids Home and the Guest Home
        val data = if (familyData.key.endsWith("-" + familyKind?.name)) familyData else FamilyHomeData()
        val openCard: (FamilyCard) -> Unit = { viewModel.navigationManager.navigateTo(it.destination) }
        if (familyKind == FamilyKind.KIDS) {
            KidsHome(
                data = data,
                wide = wide,
                firstFocus = playFocus,
                onDoor = { door ->
                    viewModel.navigationManager.navigateTo(door.destination())
                },
                onCard = openCard,
                modifier = Modifier.graphicsLayer { alpha = bloom.floatValue },
            )
        } else {
            GuestHome(
                data = data,
                wide = wide,
                firstFocus = playFocus,
                onCard = openCard,
                modifier = Modifier.graphicsLayer { alpha = bloom.floatValue },
            )
        }
    }
}

/** How long each rail icon takes to light up, and the first pick's bloom on the last note */
private const val LIGHT_MS = 320f
private const val BLOOM_MS = 520f
private const val SETTLE_MS = 600f

private const val DOOR_STAGGER_MS = 70f

/**
 * The Music door grows out of the middle of the logo right on the next beat after Games (same spacing as the
 * four, so its note keeps the rhythm; Ben heard a gap at 330 ms), and grows only a little slower than the others
 */
private const val MUSIC_DOOR_START_MS = 4 * DOOR_STAGGER_MS

/** The longest the Kids and Guest Home wait for their rows before the light-up (a slow server goes on anyway) */
private const val WAIT_FOR_ROWS_MS = 1500L
