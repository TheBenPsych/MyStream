package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.services.jellyfinId
import com.github.damontecres.wholphin.services.requestedArrived
import com.github.damontecres.wholphin.ui.components.RequestReadyNotice
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.ImageType
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benflix: "Your request is here!" When something a person asked for in Requests has arrived on Benflix,
 * they get a quiet notice (Ben asked 2026-11-03, it used to be a pop-up): a red number on More (TV) or
 * My Benflix (phone), a 7-second tip bubble for each one (one after another), and a
 * "Your requests are here" row on My Benflix. The number clears when My Benflix is opened.
 * Each request gets its bubble once per device. The very first check only remembers what has already
 * arrived, so nobody gets a pile of old bubbles (they still show in the My Benflix row).
 */
@Singleton
class RequestReadyService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val seerrService: SeerrService,
        private val serverRepository: ServerRepository,
        private val showArrival: com.github.damontecres.wholphin.services.ShowArrival,
    ) {
        /** One arrived request, newest first */
        data class Ready(
            val requestId: Int,
            val jellyfinId: UUID?,
            val tmdbId: Int,
            val isMovie: Boolean,
        )

        /** One of your requests that isn't here yet, for Home's "On the way" row */
        data class OnTheWay(
            val requestId: Int,
            val title: String,
            /** Plain words, like "On the way, about 20 minutes left" or "Waiting for a yes" */
            val line: String,
            val posterUrl: String?,
        )

        private val _ready = MutableStateFlow<List<Ready>>(emptyList())

        private val _onTheWay = MutableStateFlow<List<OnTheWay>>(emptyList())

        /** Your requests still on the way, newest first (empty for Kids and Guest) */
        val onTheWay: StateFlow<List<OnTheWay>> = _onTheWay

        /** Your arrived requests, newest first (empty for Kids and Guest) */
        val ready: StateFlow<List<Ready>> = _ready

        private val lock = Mutex()
        private var seerrUserId: Int? = null
        private var lastCheck = 0L

        init {
            RequestReadyNotice.onTipShown = { tip -> markTipped(listOf(tip.requestId)) }
        }

        private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun user(): String = serverRepository.currentUser?.id?.toString() ?: "none"

        // "seen" = looked at in My Benflix (no more red number). "tipped" = had its bubble.
        private fun seenKey(): String = "seen-" + user()

        private fun tippedKey(): String = "tipped-" + user()

        /** A new person or profile: forget the last one's notices */
        suspend fun switchTo(id: Int?) {
            lock.withLock {
                seerrUserId = id
                lastCheck = 0L
                _ready.value = emptyList()
                _onTheWay.value = emptyList()
                hereIds = emptyList()
                RequestReadyNotice.clear()
            }
        }

        /** Looks again, unless it looked in the last [minGapMs] */
        suspend fun refresh(minGapMs: Long = 60_000L) {
            val id = seerrUserId ?: return
            if (KidsMode.isActive || GuestMode.isActive) return
            lock.withLock {
                if (System.currentTimeMillis() - lastCheck < minGapMs) return
                if (!seerrService.active.first()) return
                check(id)
                lastCheck = System.currentTimeMillis()
            }
        }

        // Every arrived request, not just the newest few in the row, so marking them seen clears the dot fully
        private var hereIds: List<Int> = emptyList()

        /** Requests or My watching was opened (or "Mark all as seen"): every arrival counts as seen, so the dot goes */
        fun markAllSeen() {
            val ids = hereIds.ifEmpty { _ready.value.map { it.requestId } }
            if (ids.isNotEmpty()) OrderedSeen.add(prefs(), seenKey(), ids.map { it.toString() }, MAX_SEEN)
            RequestReadyNotice.setCount(0)
        }

        private fun markTipped(ids: List<Int>) {
            OrderedSeen.add(prefs(), tippedKey(), ids.map { it.toString() }, MAX_SEEN)
        }

        private suspend fun check(seerrUserId: Int) {
            try {
                val mine =
                    seerrService
                        .allRequests(requestedBy = seerrUserId, max = 200)
                        .filter { it.requestedBy?.id == seerrUserId && it.id != null }
                val prefs = prefs()
                // Before this version only one list was kept; it means the same as "had its bubble"
                val oldKey = "seen-" + user()
                val firstTime = !OrderedSeen.has(prefs, tippedKey()) && !OrderedSeen.has(prefs, oldKey)
                val seen = OrderedSeen.read(prefs, seenKey()).toSet()
                val tipped = OrderedSeen.read(prefs, tippedKey()).toSet() + seen
                // Benflix: a show counts once the seasons asked for are in, even if other seasons are still missing
                val here =
                    mine
                        .filter { request ->
                            when (request.media?.status) {
                                SeerrAvailability.AVAILABLE.status -> {
                                    true
                                }

                                // Once known to be here it stays here (no need to look up the seasons again)
                                SeerrAvailability.PARTIALLY_AVAILABLE.status -> {
                                    request.id.toString() in tipped || requestedSeasonsHere(request) ||
                                        showArrival.arrived(request)
                                }

                                // Benflix: Seerr can lag behind Jellyfin, or wait on episodes that haven't aired
                                SeerrAvailability.PROCESSING.status -> {
                                    request.id.toString() in tipped ||
                                        (
                                            SeerrItemType.fromString(request.type) != SeerrItemType.MOVIE &&
                                                showArrival.arrived(request)
                                        )
                                }

                                else -> {
                                    false
                                }
                            }
                        }.sortedByDescending { it.media?.updatedAt ?: it.updatedAt ?: "" }
                if (firstTime) {
                    // The old pop-up already told them about these, so no bubbles and no red number
                    val ids = here.mapNotNull { it.id }
                    markTipped(ids)
                    OrderedSeen.add(prefs, seenKey(), ids.map { it.toString() }, MAX_SEEN)
                }
                val seenNow = OrderedSeen.read(prefs, seenKey()).toSet()
                val tippedNow = OrderedSeen.read(prefs, tippedKey()).toSet() + seenNow
                _ready.value =
                    here.take(MAX_ROW).mapNotNull { request ->
                        val tmdbId = request.media?.tmdbId ?: return@mapNotNull null
                        Ready(
                            requestId = request.id!!,
                            jellyfinId = request.media?.jellyfinId,
                            tmdbId = tmdbId,
                            isMovie = SeerrItemType.fromString(request.type) == SeerrItemType.MOVIE,
                        )
                    }
                hereIds = here.mapNotNull { it.id }
                // Benflix Home: your own requests that aren't here yet (newest first), with how far along they are
                val hereSet = hereIds.toSet()
                _onTheWay.value =
                    mine
                        .filter { r ->
                            r.id !in hereSet &&
                                r.media?.status != SeerrAvailability.AVAILABLE.status &&
                                RequestStatus.from(r.status).let { it == RequestStatus.PENDING || it == RequestStatus.APPROVED }
                        }.sortedByDescending { it.createdAt ?: "" }
                        .take(MAX_ON_THE_WAY)
                        .mapNotNull { onTheWayOf(it) }
                RequestReadyNotice.setCount(here.count { it.id.toString() !in seenNow })
                // Bubbles in the order they arrived: oldest first. Only the newest few, the rest are just remembered.
                val fresh = here.filter { it.id.toString() !in tippedNow }
                val bubbles = fresh.take(MAX_TIPS).reversed()
                markTipped(fresh.drop(MAX_TIPS).mapNotNull { it.id })
                RequestReadyNotice.addTips(
                    bubbles.mapNotNull { request ->
                        val tmdbId = request.media?.tmdbId ?: return@mapNotNull null
                        val title = titleOf(request, tmdbId) ?: return@mapNotNull null
                        RequestReadyNotice.Tip(
                            requestId = request.id!!,
                            title = title,
                            posterUrl =
                                request.media?.jellyfinId?.let {
                                    runCatching {
                                        api.imageApi.getItemImageUrl(itemId = it, imageType = ImageType.PRIMARY, maxHeight = 150)
                                    }.getOrNull()
                                },
                        )
                    },
                )
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not check for arrived requests")
            }
        }

        private fun onTheWayOf(request: MediaRequest): OnTheWay? {
            val tmdbId = request.media?.tmdbId ?: return null
            val movie = SeerrItemType.fromString(request.type) == SeerrItemType.MOVIE
            return try {
                val json =
                    seerrService.api
                        .getRaw("/${if (movie) "movie" else "tv"}/$tmdbId")
                        ?.let { kotlinx.serialization.json.Json.parseToJsonElement(it).jsonObject }
                        ?: return null
                val title = (json["title"] ?: json["name"])?.jsonPrimitive?.contentOrNull ?: return null
                val poster = json["posterPath"]?.jsonPrimitive?.contentOrNull?.let { "https://image.tmdb.org/t/p/w185$it" }
                val downloads =
                    (json["mediaInfo"] as? JsonObject)
                        ?.get("downloadStatus")
                        ?.let { it as? JsonArray }
                        .orEmpty()
                        .mapNotNull { it as? JsonObject }
                OnTheWay(request.id!!, title, onTheWayLine(RequestStatus.from(request.status), downloads), poster)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.v(ex, "Could not look up request %s for Home", request.id)
                null
            }
        }

        private suspend fun titleOf(
            request: MediaRequest,
            tmdbId: Int,
        ): String? =
            try {
                if (SeerrItemType.fromString(request.type) == SeerrItemType.MOVIE) {
                    seerrService.api.moviesApi
                        .movieMovieIdGet(movieId = tmdbId)
                        .title
                } else {
                    seerrService.api.tvApi
                        .tvTvIdGet(tvId = tmdbId)
                        .name
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not look up arrived request %s", request.id)
                null
            }

        /** For a show asked for by season that's partly here: are the seasons asked for all in? */
        private suspend fun requestedSeasonsHere(request: MediaRequest): Boolean {
            val tmdbId = request.media?.tmdbId ?: return false
            if (SeerrItemType.fromString(request.type) == SeerrItemType.MOVIE) return false
            return try {
                request.requestedArrived(
                    seerrService.api.tvApi
                        .tvTvIdGet(tvId = tmdbId)
                        .mediaInfo
                        ?.seasons,
                )
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not look up the seasons of %s", tmdbId)
                false
            }
        }

        companion object {
            private const val PREFS = "benflix_request_arrived"
            private const val MAX_SEEN = 300
            private const val MAX_TIPS = 5
            private const val MAX_ROW = 20
            private const val MAX_ON_THE_WAY = 3
        }
    }

/** Home's words for a request that isn't here yet: how long is left when it's downloading */
internal fun onTheWayLine(
    status: RequestStatus,
    downloads: List<JsonObject>,
    now: java.time.Instant = java.time.Instant.now(),
): String {
    if (status == RequestStatus.PENDING) return "Asked for, waiting for a yes"
    if (downloads.isEmpty()) return "On the way, looking for a copy"
    val ends =
        downloads.mapNotNull { d ->
            d["estimatedCompletionTime"]?.jsonPrimitive?.contentOrNull?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }
        }
    val last = ends.maxOrNull()
    if (last != null) {
        val minutes = java.time.Duration.between(now, last).toMinutes()
        return when {
            minutes < 2 -> "On the way, almost here"
            minutes < 60 -> "On the way, about $minutes minutes left"
            minutes < 120 -> "On the way, about an hour left"
            minutes < 48 * 60 -> "On the way, about ${minutes / 60} hours left"
            else -> "On the way, downloading"
        }
    }
    val size = downloads.sumOf { it["size"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0 }
    val left = downloads.sumOf { it["sizeLeft"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0 }
    return if (size > 0) "On the way, ${(((size - left) / size) * 100).toInt().coerceIn(0, 99)}% downloaded" else "On the way, downloading"
}

/** Keeps an eye out for arrived requests while Benflix is open: soon after start, then every half hour */
@HiltViewModel
class RequestArrivedViewModel
    @Inject
    constructor(
        private val service: RequestReadyService,
        private val seerrServerRepository: SeerrServerRepository,
    ) : ViewModel() {
        init {
            viewModelScope.launch(WholphinDispatchers.IO) {
                seerrServerRepository.currentUserId
                    .combine(KidsMode.active) { id, kids -> if (kids) null else id }
                    .collectLatest { seerrUserId ->
                        service.switchTo(seerrUserId)
                        if (seerrUserId != null) {
                            // Let Home settle first
                            delay(4_000)
                            while (true) {
                                service.refresh(minGapMs = 0L)
                                delay(30 * 60_000L)
                            }
                        }
                    }
            }
        }
    }

/** No pop-up any more: this only starts the watching. The notice itself lives in the top bar / phone menu. */
@Composable
fun RequestArrivedLayer(
    @Suppress("UNUSED_PARAMETER") currentDestination: Destination?,
    viewModel: RequestArrivedViewModel = hiltViewModel(),
) {
    viewModel.hashCode()
}
