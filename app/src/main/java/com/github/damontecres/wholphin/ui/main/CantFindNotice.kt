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
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.services.BenflixCantFind
import com.github.damontecres.wholphin.services.CantFindEntry
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.BenflixPopups
import com.github.damontecres.wholphin.ui.components.popupTurn
import com.github.damontecres.wholphin.ui.discover.CANT_FIND_COLOR
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
 * Benflix: "Sorry, Ben couldn't find it." When Ben marks something you asked for as "Couldn't find it",
 * you get a pop-up once (per device) the next time you open Benflix, with Ben's note.
 */
@HiltViewModel
class CantFindNoticeViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val cantFind: BenflixCantFind,
        private val seerrService: SeerrService,
        private val seerrServerRepository: SeerrServerRepository,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _notices = MutableStateFlow<List<CantFindNotice>>(emptyList())
        val notices: StateFlow<List<CantFindNotice>> = _notices

        init {
            viewModelScope.launch(WholphinDispatchers.IO) {
                seerrServerRepository.currentUserId
                    .combine(KidsMode.active) { id, kids -> if (kids) null else id }
                    .collectLatest { seerrUserId ->
                        _notices.value = emptyList()
                        // Ben marks these himself, so he doesn't need telling. His device does the
                        // week-old "Still looking" check instead, so people hear even if he never opens Requests.
                        val admin = serverRepository.currentUserDto?.policy?.isAdministrator == true
                        if (seerrUserId != null && admin && seerrService.active.first()) {
                            delay(20_000)
                            cantFind.autoCheck()
                        } else if (seerrUserId != null && seerrService.active.first()) {
                            // After Home and the "Your request is here!" check have settled
                            delay(6_000)
                            check(seerrUserId)
                        }
                    }
            }
        }

        private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun key(): String = "told-" + (serverRepository.currentUser?.id?.toString() ?: "none")

        private suspend fun check(seerrUserId: Int) {
            try {
                cantFind.refresh(force = true)
                val told = prefs().getStringSet(key(), emptySet()).orEmpty()
                val mine =
                    cantFind.entries.value.values
                        .filter { seerrUserId in it.askedBy && it.tell() !in told }
                        .sortedByDescending { it.date }
                val notices =
                    mine.take(3).mapNotNull { entry ->
                        val item = lookUp(entry)
                        // Arrived in the meantime: nothing to say sorry about
                        if (item?.availability == SeerrAvailability.AVAILABLE) {
                            markTold(listOf(entry))
                            return@mapNotNull null
                        }
                        CantFindNotice(entry = entry, item = item)
                    }
                markTold(mine.drop(3))
                _notices.value = notices
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not check for can't-find notes")
            }
        }

        private suspend fun lookUp(entry: CantFindEntry): DiscoverItem? {
            val tmdbId = entry.key.substringAfter('-').toIntOrNull() ?: return null
            return try {
                if (entry.key.startsWith("MOVIE")) {
                    seerrService.createDiscoverItem(seerrService.api.moviesApi.movieMovieIdGet(movieId = tmdbId))
                } else {
                    seerrService.createDiscoverItem(seerrService.api.tvApi.tvTvIdGet(tvId = tmdbId))
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not look up %s", entry.key)
                null
            }
        }

        /** Marking again later (new date) tells them again */
        private fun CantFindEntry.tell() = "$key|$date"

        private fun markTold(entries: List<CantFindEntry>) {
            if (entries.isEmpty()) return
            val told = prefs().getStringSet(key(), emptySet()).orEmpty() + entries.map { it.tell() }
            prefs().edit().putStringSet(key(), told.toList().takeLast(MAX_TOLD).toSet()).apply()
        }

        fun done(
            notice: CantFindNotice,
            open: Boolean,
        ) {
            markTold(listOf(notice.entry))
            _notices.update { list -> list.filter { it.entry.key != notice.entry.key } }
            if (open) notice.item?.let { navigationManager.navigateTo(it.destination) }
        }

        companion object {
            private const val PREFS = "benflix_cant_find"
            private const val MAX_TOLD = 300
        }
    }

data class CantFindNotice(
    val entry: CantFindEntry,
    val item: DiscoverItem?,
)

/** Sits on top of every page, like "Your request is here!". Waits while something is playing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CantFindLayer(
    currentDestination: Destination?,
    viewModel: CantFindNoticeViewModel = hiltViewModel(),
) {
    val notices by viewModel.notices.collectAsState()
    val playing = currentDestination is Destination.Playback || currentDestination is Destination.PlaybackList
    val notice = notices.firstOrNull()
    if (!popupTurn("cant-find", BenflixPopups.CANT_FIND, notice != null && !playing)) return
    notice ?: return
    val focus = remember(notice.entry.key) { FocusRequester() }
    // Phones are narrow: a smaller poster, and the buttons drop to their own line if they don't fit
    val isTv = rememberIsTv()
    BasicDialog(onDismissRequest = { viewModel.done(notice, open = false) }) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .widthIn(max = 620.dp)
                    .padding(if (isTv) 24.dp else 16.dp),
        ) {
            notice.item?.posterUrl?.let { poster ->
                AsyncImage(
                    model = poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .width(if (isTv) 120.dp else 84.dp)
                            .height(if (isTv) 180.dp else 126.dp)
                            .clip(RoundedCornerShape(10.dp)),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "SORRY, WE COULDN'T FIND IT",
                    color = CANT_FIND_COLOR,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    fontSize = 14.sp,
                )
                Text(
                    text = notice.item?.title ?: notice.entry.title ?: "Something you asked for",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isTv) 24.sp else 20.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        if (notice.entry.auto) {
                            "We looked for this one for a week but couldn't find a copy yet. " +
                                "If one turns up, it'll come to Benflix by itself."
                        } else {
                            "Ben looked for this one but couldn't find a copy yet. If one turns up, it'll come to Benflix by itself."
                        },
                    color = Color(0xFFCFCFD6),
                    fontSize = 16.sp,
                )
                notice.entry.note?.let { note ->
                    Text(
                        text = "Ben's note: “$note”",
                        color = Color(0xFFE8B79C),
                        fontSize = 15.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Button(
                        onClick = { viewModel.done(notice, open = false) },
                        modifier = Modifier.focusRequester(focus),
                    ) {
                        Text("OK")
                    }
                    if (notice.item != null) {
                        Button(onClick = { viewModel.done(notice, open = true) }) {
                            Text("See details")
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(notice.entry.key) {
        delay(200)
        try {
            focus.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }
}
