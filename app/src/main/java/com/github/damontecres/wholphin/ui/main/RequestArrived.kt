package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.BenflixPopups
import com.github.damontecres.wholphin.ui.components.popupTurn
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
                    seerrService.api.requestApi
                        .requestGet(take = 40, skip = 0, requestedBy = seerrUserId)
                        .results
                        .orEmpty()
                        .filter { it.requestedBy?.id == seerrUserId }
                val here = mine.filter { it.media?.status == SeerrAvailability.AVAILABLE.status && it.id != null }
                val prefs = prefs()
                val firstTime = !prefs.contains(key())
                val seen = prefs.getStringSet(key(), emptySet()).orEmpty()
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

        private fun markSeen(ids: List<Int>) {
            val prefs = prefs()
            val seen = prefs.getStringSet(key(), emptySet()).orEmpty() + ids.map { it.toString() }
            prefs.edit().putStringSet(key(), seen.toList().takeLast(MAX_SEEN).toSet()).apply()
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
    BasicDialog(onDismissRequest = { viewModel.done(arrival, open = false) }) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.widthIn(max = 620.dp).padding(24.dp),
        ) {
            AsyncImage(
                model = arrival.item.posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .width(120.dp)
                        .height(180.dp)
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
                    fontSize = 24.sp,
                )
                Text(
                    text = "You asked for it, and now it's on Benflix. Enjoy!",
                    color = Color(0xFFCFCFD6),
                    fontSize = 16.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
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
