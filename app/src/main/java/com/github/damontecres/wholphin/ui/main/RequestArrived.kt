package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.api.seerr.model.MediaRequest
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.services.requestedArrived
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.BenflixPopups
import com.github.damontecres.wholphin.ui.components.popupTurn
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Benflix: "Your request is here!" When something a person asked for in Requests has arrived on Benflix,
 * they get a pop-up the next time they open Benflix, with a button to go straight to it.
 * Each request is announced once per device. The very first check only remembers what has already
 * arrived, so nobody gets a pile of old pop-ups.
 */
@HiltViewModel
class RequestArrivedViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val seerrService: SeerrService,
        private val seerrServerRepository: SeerrServerRepository,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _arrived = MutableStateFlow<List<Arrival>>(emptyList())
        val arrived: StateFlow<List<Arrival>> = _arrived

        init {
            viewModelScope.launch(WholphinDispatchers.IO) {
                seerrServerRepository.currentUserId
                    .combine(KidsMode.active) { id, kids -> if (kids) null else id }
                    .collectLatest { seerrUserId ->
                        _arrived.value = emptyList()
                        if (seerrUserId != null && seerrService.active.first()) {
                            // Let Home settle first
                            delay(4_000)
                            check(seerrUserId)
                        }
                    }
            }
        }

        private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun key(): String = "seen-" + (serverRepository.currentUser?.id?.toString() ?: "none")

        private suspend fun check(seerrUserId: Int) {
            try {
                val mine =
                    seerrService
                        .allRequests(requestedBy = seerrUserId, max = 200)
                        .filter { it.requestedBy?.id == seerrUserId && it.id != null }
                val prefs = prefs()
                val firstTime = !OrderedSeen.has(prefs, key())
                val seen = OrderedSeen.read(prefs, key()).toSet()
                // Benflix: a show counts once the seasons asked for are in, even if other seasons are still missing
                val here =
                    mine.filter { request ->
                        when {
                            request.media?.status == SeerrAvailability.AVAILABLE.status -> {
                                true
                            }

                            request.id.toString() in seen -> {
                                false
                            }

                            request.media?.status == SeerrAvailability.PARTIALLY_AVAILABLE.status -> {
                                requestedSeasonsHere(request)
                            }

                            else -> {
                                false
                            }
                        }
                    }
                val fresh = here.filter { it.id.toString() !in seen }
                if (firstTime) {
                    markSeen(here.mapNotNull { it.id })
                    return
                }
                val arrivals =
                    fresh.take(3).mapNotNull { request ->
                        val tmdbId = request.media?.tmdbId ?: return@mapNotNull null
                        val item =
                            try {
                                if (SeerrItemType.fromString(request.type) == SeerrItemType.MOVIE) {
                                    seerrService.createDiscoverItem(seerrService.api.moviesApi.movieMovieIdGet(movieId = tmdbId))
                                } else {
                                    seerrService.createDiscoverItem(seerrService.api.tvApi.tvTvIdGet(tvId = tmdbId))
                                }
                            } catch (ex: CancellationException) {
                                throw ex
                            } catch (ex: Exception) {
                                Timber.w(ex, "Could not look up arrived request %s", request.id)
                                null
                            } ?: return@mapNotNull null
                        Arrival(requestId = request.id!!, item = item)
                    }
                // Anything older than the 3 shown is just remembered
                markSeen(fresh.drop(3).mapNotNull { it.id })
                _arrived.value = arrivals
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not check for arrived requests")
            }
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

        private fun markSeen(ids: List<Int>) {
            // Benflix: kept in order, so only the oldest are dropped
            OrderedSeen.add(prefs(), key(), ids.map { it.toString() }, MAX_SEEN)
        }

        fun done(
            arrival: Arrival,
            open: Boolean,
        ) {
            markSeen(listOf(arrival.requestId))
            _arrived.update { list -> list.filter { it.requestId != arrival.requestId } }
            if (open) navigationManager.navigateTo(arrival.item.destination)
        }

        companion object {
            private const val PREFS = "benflix_request_arrived"
            private const val MAX_SEEN = 300
        }
    }

data class Arrival(
    val requestId: Int,
    val item: DiscoverItem,
)

/** Sits on top of every page, like the bedtime screen. Waits while something is playing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RequestArrivedLayer(
    currentDestination: Destination?,
    viewModel: RequestArrivedViewModel = hiltViewModel(),
) {
    val arrived by viewModel.arrived.collectAsState()
    val playing = currentDestination is Destination.Playback || currentDestination is Destination.PlaybackList
    val arrival = arrived.firstOrNull()
    if (!popupTurn("request-arrived", BenflixPopups.REQUEST_ARRIVED, arrival != null && !playing)) return
    arrival ?: return
    val focus = remember(arrival.requestId) { FocusRequester() }
    // Phones are narrow: a smaller poster, and the buttons drop to their own line if they don't fit
    val isTv = rememberIsTv()
    BasicDialog(onDismissRequest = { viewModel.done(arrival, open = false) }) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .widthIn(max = 620.dp)
                    .padding(if (isTv) 24.dp else 16.dp),
        ) {
            AsyncImage(
                model = arrival.item.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .width(if (isTv) 120.dp else 84.dp)
                        .height(if (isTv) 180.dp else 126.dp)
                        .clip(RoundedCornerShape(10.dp)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "YOUR REQUEST IS HERE!",
                    color = Color(0xFFE5484D),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    fontSize = 14.sp,
                )
                Text(
                    text = arrival.item.title ?: "Something you asked for",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isTv) 24.sp else 20.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "You asked for it, and now it's on Benflix. Enjoy!",
                    color = Color(0xFFCFCFD6),
                    fontSize = 16.sp,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Button(
                        onClick = { viewModel.done(arrival, open = true) },
                        modifier = Modifier.focusRequester(focus),
                    ) {
                        Text("Watch now")
                    }
                    Button(onClick = { viewModel.done(arrival, open = false) }) {
                        Text("Later")
                    }
                }
            }
        }
    }
    LaunchedEffect(arrival.requestId) {
        delay(200)
        try {
            focus.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }
}
