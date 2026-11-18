package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.components.BenflixPlayFilled
import com.github.damontecres.wholphin.ui.door.DoorDataService
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.LocalTopBarOverlap
import com.github.damontecres.wholphin.ui.nav.focusRing
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.rememberAmbientClock
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** One pick in Home's carousel */
data class HomePick(
    val id: UUID,
    val type: BaseItemKind,
    val name: String,
    /** "Because you watched Interstellar", "New on Benflix", "Picked for you" */
    val reason: String,
    /** "2016 · PG-13 · 1h 56m · Sci-fi" */
    val line: String,
    val overview: String,
    val backdrop: String,
    /** The same picture, tiny: shown blurred-looking first so the screen is never empty */
    val thumb: String,
    val resumeMs: Long = 0L,
)

@Serializable
private data class SavedPick(
    val id: String,
    val type: String,
    val name: String,
    val reason: String,
    val line: String,
    val overview: String,
    val backdrop: String,
    val thumb: String,
    val resumeMs: Long = 0L,
)

@Serializable
private data class SavedPicks(
    val at: Long,
    val picks: List<SavedPick>,
)

/**
 * Navigation redesign (approved hm-spot + hc-dots): Home's carousel of 6 picks for this profile, each with a
 * reason ("Because you watched…") and a description. Like what you liked first, then what's new. Picks are
 * saved on this TV or phone, so Home shows the last ones instantly and finds fresh ones every few hours.
 * Kids get picks only from the titles they may see (and Ben's own "Picked for you" first).
 */
@Singleton
class HomePicksService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val imageUrlService: ImageUrlService,
        private val doorData: DoorDataService,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /** The picks saved last time and when they were found, or null */
        fun saved(key: String): Pair<Long, List<HomePick>>? =
            try {
                prefs.getString(key, null)?.let { text ->
                    val saved = json.decodeFromString(SavedPicks.serializer(), text)
                    saved.at to
                        saved.picks.mapNotNull { p ->
                            val type = BaseItemKind.entries.firstOrNull { it.name == p.type } ?: return@mapNotNull null
                            HomePick(UUID.fromString(p.id), type, p.name, p.reason, p.line, p.overview, p.backdrop, p.thumb, p.resumeMs)
                        }
                }
            } catch (ex: Exception) {
                Timber.w(ex, "Home picks: saved ones unreadable")
                null
            }

        fun save(
            key: String,
            picks: List<HomePick>,
        ) {
            try {
                val saved =
                    SavedPicks(
                        System.currentTimeMillis(),
                        picks.map {
                            SavedPick(
                                it.id.toString(),
                                it.type.name,
                                it.name,
                                it.reason,
                                it.line,
                                it.overview,
                                it.backdrop,
                                it.thumb,
                                it.resumeMs,
                            )
                        },
                    )
                prefs.edit().putString(key, json.encodeToString(SavedPicks.serializer(), saved)).apply()
            } catch (ex: Exception) {
                Timber.w(ex, "Home picks: could not save")
            }
        }

        suspend fun build(
            userId: UUID,
            kids: Boolean,
        ): List<HomePick> {
            val disliked = doorData.disliked(userId)
            val (ben, movies, shows, fresh) =
                coroutineScope {
                    val ben = async { if (kids) doorData.kidsPicks(userId) else emptyList() }
                    val movies = async { doorData.because(userId, BaseItemKind.MOVIE, disliked) }
                    val shows = async { doorData.because(userId, BaseItemKind.SERIES, disliked) }
                    val fresh = async { newest(userId) }
                    listOf(
                        ben.await().shuffled().map { it to "Picked for you" },
                        movies.await()?.let { (name, items) -> items.shuffled().map { it to "Because you watched $name" } }.orEmpty(),
                        shows.await()?.let { (name, items) -> items.shuffled().map { it to "Because you watched $name" } }.orEmpty(),
                        fresh.await().shuffled().map { it to "New on Glowfin" },
                    )
                }
            // Ben's picks first (Kids), then movies and shows taking turns, then what's new
            val liked =
                buildList {
                    val a = movies.iterator()
                    val b = shows.iterator()
                    while (a.hasNext() || b.hasNext()) {
                        if (a.hasNext()) add(a.next())
                        if (b.hasNext()) add(b.next())
                    }
                }
            return (ben.take(2) + liked.take(4) + fresh + liked.drop(4) + ben.drop(2))
                .filter { (item, _) ->
                    item.id !in disliked &&
                        item.data.backdropImageTags?.isNotEmpty() == true &&
                        item.data.userData?.played != true
                }.distinctBy { it.first.id }
                .take(PICKS)
                .map { (item, reason) -> toPick(item, reason) }
        }

        private suspend fun newest(userId: UUID): List<BaseItem> =
            try {
                api.itemsApi
                    .getItems(
                        GetItemsRequest(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            recursive = true,
                            isPlayed = false,
                            imageTypes = listOf(ImageType.BACKDROP),
                            sortBy = listOf(ItemSortBy.DATE_CREATED),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            fields = SlimItemFields,
                            enableUserData = true,
                            limit = NEWEST,
                            enableTotalRecordCount = false,
                        ),
                    ).content.items
                    .map { BaseItem(it) }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Home picks: newest")
                emptyList()
            }

        private fun toPick(
            item: BaseItem,
            reason: String,
        ): HomePick {
            val d = item.data
            val runtime =
                d.runTimeTicks?.let { ticks ->
                    val minutes = ticks / 600_000_000L
                    if (minutes <= 0) {
                        null
                    } else if (minutes >= 60) {
                        "${minutes / 60}h ${minutes % 60}m"
                    } else {
                        "${minutes}m"
                    }
                }
            val seasons =
                if (item.type ==
                    BaseItemKind.SERIES
                ) {
                    d.childCount?.takeIf { it > 0 }?.let { if (it == 1) "1 season" else "$it seasons" }
                } else {
                    null
                }
            val line =
                listOfNotNull(
                    d.productionYear?.toString(),
                    d.officialRating,
                    if (item.type == BaseItemKind.SERIES) seasons else runtime,
                    d.genres?.firstOrNull(),
                ).joinToString(" · ")
            return HomePick(
                id = item.id,
                type = item.type,
                name = item.name.orEmpty(),
                reason = reason,
                line = line,
                overview = d.overview.orEmpty(),
                backdrop = imageUrlService.getItemImageUrl(item, ImageType.BACKDROP, fillWidth = BACKDROP_WIDTH).orEmpty(),
                thumb = imageUrlService.getItemImageUrl(item, ImageType.BACKDROP, fillWidth = THUMB_WIDTH).orEmpty(),
                resumeMs = item.resumeMs,
            )
        }

        private companion object {
            const val PREFS = "benflix_home_picks"
            const val PICKS = 6
            const val NEWEST = 24
            const val BACKDROP_WIDTH = 1280
            const val THUMB_WIDTH = 64
        }
    }

@HiltViewModel
class HomePicksViewModel
    @Inject
    constructor(
        private val service: HomePicksService,
        private val serverRepository: ServerRepository,
        private val api: ApiClient,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _picks = MutableStateFlow<List<HomePick>>(emptyList())
        val picks: StateFlow<List<HomePick>> = _picks

        /** The picks are in (saved or fresh), or there are none to find */
        private val _ready = MutableStateFlow(false)
        val ready: StateFlow<Boolean> = _ready

        private fun key(): String =
            "picks-${serverRepository.currentUser?.id}-${KidsMode.level}-${if (GuestMode.isActive) "guest" else "me"}"

        fun load() {
            val key = key()
            val saved = service.saved(key)
            if (saved != null && saved.second.isNotEmpty()) {
                _picks.value = saved.second
                _ready.value = true
                if (System.currentTimeMillis() - saved.first < FRESH_MS) return
            }
            val userId = serverRepository.currentUser?.id
            if (userId == null) {
                _ready.value = true
                return
            }
            viewModelScope.launchIO {
                try {
                    val fresh = service.build(userId, KidsMode.isActive)
                    if (fresh.isNotEmpty()) {
                        service.save(key, fresh)
                        // A carousel that's already showing keeps its first picks until Home opens again
                        if (_picks.value.isEmpty()) _picks.value = fresh
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Home picks")
                } finally {
                    _ready.value = true
                }
            }
        }

        fun open(pick: HomePick) = navigationManager.navigateTo(Destination.MediaItem(pick.id, pick.type))

        /** Play: a movie starts (from where you left off); a show starts its next episode */
        fun play(pick: HomePick) {
            if (pick.type != BaseItemKind.SERIES) {
                navigationManager.navigateTo(Destination.Playback(pick.id, pick.resumeMs))
                return
            }
            viewModelScope.launchIO {
                val next =
                    try {
                        com.github.damontecres.wholphin.services.NextEpisode
                            .find(api, pick.id)
                            ?: api.tvShowsApi
                                .getNextUp(
                                    GetNextUpRequest(
                                        seriesId = pick.id,
                                        userId = serverRepository.currentUser?.id,
                                        limit = 1,
                                        enableResumable = true,
                                    ),
                                ).content.items
                                .firstOrNull()
                            ?: api.tvShowsApi
                                .getEpisodes(
                                    GetEpisodesRequest(
                                        seriesId = pick.id,
                                        limit = 1,
                                        enableImages = false,
                                    ),
                                ).content.items
                                .firstOrNull()
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Home pick: no episode for %s", pick.id)
                        null
                    }
                withContext(Dispatchers.Main) {
                    if (next != null) {
                        val ticks = next.userData?.playbackPositionTicks ?: 0L
                        navigationManager.navigateTo(Destination.Playback(next.id, ticks / 10_000L))
                    } else {
                        open(pick)
                    }
                }
            }
        }

        private companion object {
            /** Fresh picks every few hours (approved hc-q-change) */
            const val FRESH_MS = 3 * 60 * 60 * 1000L
        }
    }

/** One floating card under the carousel: a Today news line, or (Kids, Guest) a title to keep watching */
class TodayCard(
    val id: String,
    /** The small coloured line, like "NEW EPISODE · S3 E12" */
    val kind: String,
    val kindColor: Color,
    val title: String,
    val image: String?,
    val square: Boolean,
    val onClick: () -> Unit,
)

/**
 * The new Home (approved hm-spot, hc-dots and fl-cards): your greeting, a big picture carousel of 6 picks that
 * slides by itself every 10 seconds (dots underneath, the picture slowly zooming), and the Today cards floating
 * a little above the bottom edge. The remote starts on Play. While the remote is on the carousel it keeps
 * sliding until you press any button; OK in the first second after a slide plays the pick you were looking at.
 * Right past More info goes to the next pick. Phones: the picture on top (swipe for the next pick), then the cards.
 */
@Composable
fun HomeSpotlight(
    greeting: String,
    weather: String?,
    picks: List<HomePick>,
    cards: List<TodayCard>,
    wide: Boolean,
    kids: Boolean,
    bloom: () -> Float,
    playFocus: FocusRequester,
    onPlay: (HomePick) -> Unit,
    onInfo: (HomePick) -> Unit,
    onSeeAll: (() -> Unit)?,
    onCardsSeen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var index by rememberSaveable { mutableIntStateOf(0) }
    var previous by remember { mutableIntStateOf(0) }
    var slidAt by remember { mutableLongStateOf(0L) }
    var carouselFocused by remember { mutableStateOf(false) }
    var stopped by remember { mutableStateOf(false) }
    var lastPress by remember { mutableLongStateOf(0L) }
    val count = picks.size
    LaunchedEffect(count) { if (count > 0 && index >= count) index = 0 }
    val pick = picks.getOrNull(index.coerceIn(0, (count - 1).coerceAtLeast(0)))

    fun go(to: Int) {
        if (count == 0) return
        previous = index
        index = ((to % count) + count) % count
        slidAt = System.currentTimeMillis()
    }

    // Slides by itself every 10 seconds (approved hc-dots; cf-q-land A)
    LaunchedEffect(count) {
        if (count < 2) return@LaunchedEffect
        while (true) {
            delay(SLIDE_MS)
            val quiet = System.currentTimeMillis() - lastPress >= SLIDE_MS
            if (stopped && (carouselFocused || !quiet)) continue
            stopped = false
            go(index + 1)
        }
    }
    // Only the next picture is fetched ahead, so it's there when the carousel slides
    LaunchedEffect(index, count) {
        val next = picks.getOrNull((index + 1) % count.coerceAtLeast(1)) ?: return@LaunchedEffect
        try {
            SingletonImageLoader.get(context).enqueue(ImageRequest.Builder(context).data(next.backdrop).build())
        } catch (ex: Exception) {
            Timber.d(ex, "Home: next picture")
        }
    }
    val playTarget: () -> Unit = {
        // OK in the first second after a slide plays the one you were looking at
        val p = if (System.currentTimeMillis() - slidAt < OK_GRACE_MS) picks.getOrNull(previous) else pick
        if (p != null) onPlay(p)
    }
    val scale = if (kids) KIDS_SCALE else 1f
    val keys =
        Modifier.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown) {
                lastPress = System.currentTimeMillis()
                if (carouselFocused) stopped = true
            }
            false
        }
    val buttons: @Composable (Modifier) -> Unit = { m ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = m.onFocusChanged { carouselFocused = it.hasFocus },
        ) {
            SpotButton(
                text = "Play",
                filled = true,
                onClick = playTarget,
                scale = scale,
                modifier = Modifier.focusRequester(playFocus),
            )
            SpotButton(
                text = "More info",
                filled = false,
                onClick = { pick?.let(onInfo) },
                scale = scale,
                modifier =
                    Modifier.onPreviewKeyEvent { event ->
                        // Right past More info: the next pick
                        if (event.key == Key.DirectionRight && count > 1) {
                            if (event.type == KeyEventType.KeyDown) go(index + 1)
                            true
                        } else {
                            false
                        }
                    },
            )
        }
    }
    if (wide) {
        BoxWithConstraints(modifier.fillMaxSize().then(keys)) {
            val h = maxHeight
            // The picture across the top 80%, fading into the page on the left and at the bottom
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(h * PICTURE_HEIGHT)
                    .graphicsLayer { alpha = bloom() },
            ) {
                SpotPicture(pick)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            0f to BenflixColors.Tray.copy(alpha = .94f),
                            0.38f to BenflixColors.Tray.copy(alpha = .6f),
                            0.7f to Color.Transparent,
                        ),
                    ),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.5f to Color.Transparent, 1f to BenflixColors.Tray),
                    ),
                )
            }
            // Ben 2026-11-17: the greeting sits top left beside the rail, on the same line as the clock
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier =
                    Modifier
                        .padding(start = EDGE, top = HEADER_TOP)
                        .height((LocalTopBarOverlap.current - HEADER_TOP - 6.dp).coerceAtLeast(44.dp))
                        .widthIn(max = maxWidth * 0.7f),
            ) {
                Greeting(greeting, weather, tv = true, inline = true)
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier =
                    Modifier
                        .padding(start = EDGE, top = h * INFO_TOP)
                        .widthIn(max = maxWidth * 0.46f)
                        .graphicsLayer { alpha = bloom() },
            ) {
                PickWords(pick, big = true, scale = scale)
                buttons(Modifier.padding(top = 6.dp))
                Dots(count, index)
            }
            // Approved fl-cards: up to four cards, each floating on its own a little above the bottom edge
            if (cards.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(start = EDGE, end = 40.dp, bottom = h * CARDS_BOTTOM)
                            .onFocusChanged { if (it.hasFocus) onCardsSeen() },
                ) {
                    cards.take(MAX_CARDS).forEach { card ->
                        FloatingCard(card, scale, Modifier.weight(1f))
                    }
                    if (onSeeAll != null) {
                        SeeAllCard(onSeeAll, scale)
                    }
                }
            }
        }
    } else {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(PHONE_PICTURE_RATIO)
                    .graphicsLayer { alpha = bloom() }
                    .pointerInput(count) {
                        var dx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dx = 0f },
                            onDragEnd = {
                                lastPress = System.currentTimeMillis()
                                stopped = true
                                if (dx < -SWIPE_PX) {
                                    go(index + 1)
                                } else if (dx > SWIPE_PX) {
                                    go(index - 1)
                                }
                            },
                        ) { _, amount -> dx += amount }
                    },
            ) {
                SpotPicture(pick)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to BenflixColors.Tray.copy(alpha = .55f),
                            0.3f to Color.Transparent,
                            0.65f to Color.Transparent,
                            1f to BenflixColors.Tray,
                        ),
                    ),
                )
                Box(Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp)) {
                    Greeting(greeting, weather, tv = false)
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp)
                        .graphicsLayer { alpha = bloom() },
            ) {
                PickWords(pick, big = false, scale = scale)
                buttons(Modifier)
                Dots(count, index)
            }
            if (cards.isNotEmpty()) {
                LaunchedEffect(cards.size) { onCardsSeen() }
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp),
                ) {
                    cards.take(MAX_CARDS).forEach { card -> FloatingCard(card, scale, Modifier.fillMaxWidth()) }
                    if (onSeeAll != null) {
                        SpotButton(text = "See all", filled = false, onClick = onSeeAll, scale = scale)
                    }
                }
            }
        }
    }
}

/** The pick's picture: a tiny copy first, then the sharp one, slowly zooming in (approved hc-q-trailer A) */
@Composable
private fun SpotPicture(pick: HomePick?) {
    Crossfade(targetState = pick, animationSpec = tween(FADE_MS), label = "spotPicture") { p ->
        // Each picture zooms in one way at a steady speed from when it shows (it used to turn round and bounce).
        // Speed check (Ben 2026-11-17): the zoom runs from the shared slow clock (about 20 steps a second, holding
        // still while you press the remote) instead of 60 a second, so the TV isn't redrawing the whole of Home
        // all the time. It's so slow that it looks just the same. Read only while drawing, so it never rebuilds the screen.
        val clock = rememberAmbientClock()
        val shownAt = remember { Snapshot.withoutReadObservation { clock.floatValue } }
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(0.dp))
                .graphicsLayer {
                    val z = 1f + (ZOOM - 1f) * ((clock.floatValue - shownAt) * 1000f / ZOOM_MS).coerceIn(0f, 1f)
                    scaleX = z
                    scaleY = z
                },
        ) {
            if (p == null) {
                // No picks yet (a brand-new server): a soft glow instead
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Color(0xFF24324F), Color(0xFF5B2A3A), BenflixColors.Tray)),
                    ),
                )
            } else {
                // The tiny copy only until the sharp one is in: two full-screen pictures on top of each other
                // were drawn every frame before
                var sharp by remember { mutableStateOf(false) }
                if (!sharp) {
                    AsyncImage(
                        model = p.thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                AsyncImage(
                    model = p.backdrop,
                    contentDescription = p.name,
                    contentScale = ContentScale.Crop,
                    onSuccess = { sharp = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** The reason, the name, the short line and the description */
@Composable
private fun PickWords(
    pick: HomePick?,
    big: Boolean,
    scale: Float,
) {
    Crossfade(targetState = pick, animationSpec = tween(FADE_MS), label = "spotWords") { p ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (p == null) {
                Text(
                    text = "Finding something for you…",
                    color = BenflixColors.Soft,
                    fontSize = BenflixType.lead * scale,
                )
                return@Column
            }
            Text(
                text = p.reason,
                color = REASON_GOLD,
                fontSize = (if (big) 15.sp else 13.sp) * scale,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = p.name,
                color = BenflixColors.Text,
                fontSize = (if (big) BenflixType.hero else 28.sp) * scale,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = (if (big) 44.sp else 31.sp) * scale,
            )
            if (p.line.isNotBlank()) {
                Text(
                    text = p.line,
                    color = BenflixColors.Soft,
                    fontSize = BenflixType.small * scale,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (p.overview.isNotBlank()) {
                Text(
                    text = p.overview,
                    color = BenflixColors.Soft,
                    fontSize = BenflixType.body * scale,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp * scale,
                )
            }
        }
    }
}

@Composable
private fun SpotButton(
    text: String,
    filled: Boolean,
    onClick: () -> Unit,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (filled) Color.White else BenflixColors.Raised.copy(alpha = .9f),
                contentColor = if (filled) Color(0xFF111111) else BenflixColors.Text,
                focusedContainerColor = if (filled) Color.White else BenflixColors.FocusRow,
                focusedContentColor = if (filled) Color(0xFF111111) else BenflixColors.Text,
            ),
        border = focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 22.dp * scale, vertical = 10.dp * scale),
        ) {
            if (filled) {
                Image(
                    imageVector = BenflixPlayFilled,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color(0xFF111111)),
                    modifier = Modifier.size(18.dp * scale),
                )
            }
            Text(text = text, fontSize = BenflixType.body * scale, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** The six dots under the carousel; the one showing is a longer white pill */
@Composable
private fun Dots(
    count: Int,
    index: Int,
) {
    if (count < 2) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (i == index) 20.dp else 6.dp)
                    .background(if (i == index) Color.White else Color.White.copy(alpha = .35f), RoundedCornerShape(50)),
            )
        }
    }
}

/** A Today card floating with a soft shadow under it (approved fl-cards) */
@Composable
internal fun FloatingCard(
    card: TodayCard,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = card.onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.Panel.copy(alpha = .94f),
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border = focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.height(CARD_HEIGHT * scale).shadow(14.dp, shape, clip = false),
    ) {
        // Ben 2026-11-17: the cover art fills almost the card's whole height so you see all of it, the words are
        // smaller, and a Did you know shows the fact itself so you can read it at a glance
        val fact = card.kind.startsWith("Did you know")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(7.dp * scale)
                    .padding(start = if (card.image == null) 9.dp * scale else 0.dp),
        ) {
            // Cards with no picture (holiday countdown, Tower needs the setup command, update) leave out the
            // picture box instead of showing an empty grey square, so their words get the whole card
            if (card.image != null) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(if (card.square) 1f else 2f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BenflixColors.Raised),
                ) {
                    AsyncImage(
                        model = card.image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = card.kind.uppercase(),
                    color = card.kindColor,
                    fontSize = 10.sp * scale,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = card.title,
                    color = BenflixColors.Text,
                    fontSize = (if (fact) 12.sp else 13.sp) * scale,
                    fontWeight = if (fact) FontWeight.Medium else FontWeight.SemiBold,
                    maxLines = if (fact) 5 else 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = (if (fact) 15.sp else 16.sp) * scale,
                )
            }
        }
    }
}

@Composable
internal fun SeeAllCard(
    onClick: () -> Unit,
    scale: Float,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.Panel.copy(alpha = .94f),
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border = focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier.shadow(14.dp, shape, clip = false),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.height(CARD_HEIGHT * scale).padding(horizontal = 20.dp)) {
            Text("See all", fontSize = 14.sp * scale, fontWeight = FontWeight.Bold)
        }
    }
}

/** Puts the remote on Play (after the picks are in) */
suspend fun focusPlay(focus: FocusRequester) {
    repeat(FOCUS_TRIES) {
        if (focus.tryRequestFocus()) return
        delay(FOCUS_RETRY_MS)
    }
}

private val REASON_GOLD = Color(0xFFFFD27A)
private val EDGE = 40.dp
private const val PICTURE_HEIGHT = 0.8f
private const val INFO_TOP = 0.25f

/** Where the header's row starts (PageHeader's top padding), so the greeting lines up with the clock */
private val HEADER_TOP = 18.dp
private const val CARDS_BOTTOM = 0.04f
private const val MAX_CARDS = 4

/** The Today cards' height on TV: tall enough for a whole poster and a 5-line fact */
private val CARD_HEIGHT = 104.dp
private const val PHONE_PICTURE_RATIO = 1.25f
private const val KIDS_SCALE = 1.15f
private const val SLIDE_MS = 10_000L
private const val OK_GRACE_MS = 1_000L
private const val FADE_MS = 600
private const val ZOOM = 1.06f
private const val ZOOM_MS = 12_000
private const val SWIPE_PX = 80f
private const val FOCUS_TRIES = 10
private const val FOCUS_RETRY_MS = 60L
