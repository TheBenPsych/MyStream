package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.door.DoorDataService
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.focusRing
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.tryRequestFocus
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
        // Ben 2026-11-17: the cover art fills almost the card's whole height so you see all of it, the words are smaller
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
                    fontSize = 13.sp * scale,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp * scale,
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

/** The Today cards' height on TV: tall enough for a whole poster */
private val CARD_HEIGHT = 104.dp
private const val FOCUS_TRIES = 10
private const val FOCUS_RETRY_MS = 60L
