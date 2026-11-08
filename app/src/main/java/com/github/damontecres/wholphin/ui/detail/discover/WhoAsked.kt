package com.github.damontecres.wholphin.ui.detail.discover

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.api.seerr.RequestApi
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.discover.CantFindDialog
import com.github.damontecres.wholphin.ui.discover.CantFindViewModel
import com.github.damontecres.wholphin.ui.discover.cantFindKey
import com.github.damontecres.wholphin.ui.nav.RequestsWaiting
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import timber.log.Timber
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** One request for this title: who asked, when, and where it's at */
data class AskedBy(
    val requestId: Int,
    val who: String,
    val whenAsked: String?,
    val status: RequestStatus,
    // Benflix: Seerr user id, so "Couldn't find it" can tell them
    val seerrUserId: Int? = null,
)

/** Benflix: tells an open title page ("true-123" movie / "false-456" show) that one of its requests was answered */
object RequestAnswered {
    val events = MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun key(
        isMovie: Boolean,
        tmdbId: Int,
    ) = "$isMovie-$tmdbId"
}

/**
 * Benflix (admins): on a movie or show you could ask for, show who already asked for it and when,
 * with Approve and Decline right there.
 */
@HiltViewModel
class WhoAskedViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val seerrService: SeerrService,
        private val serverRepository: ServerRepository,
    ) : ViewModel() {
        private val _asked = MutableStateFlow<List<AskedBy>>(emptyList())
        val asked: StateFlow<List<AskedBy>> = _asked
        private var loadedFor: String? = null
        private var title: Pair<Boolean, Int>? = null

        val isAdmin: Boolean get() = serverRepository.currentUserDto?.policy?.isAdministrator == true

        fun load(
            isMovie: Boolean,
            tmdbId: Int,
        ) {
            val key = RequestAnswered.key(isMovie, tmdbId)
            if (loadedFor == key || !isAdmin) return
            loadedFor = key
            title = isMovie to tmdbId
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    _asked.value = fetch(isMovie, tmdbId)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not look up who asked for %s", key)
                }
            }
        }

        private suspend fun fetch(
            isMovie: Boolean,
            tmdbId: Int,
        ): List<AskedBy> {
            val raw = seerrService.api.getRaw(if (isMovie) "/movie/$tmdbId" else "/tv/$tmdbId")
            val media = raw?.let { Json.parseToJsonElement(it).jsonObject["mediaInfo"] as? JsonObject }
            return (media?.get("requests") as? JsonArray)
                .orEmpty()
                .mapNotNull { it as? JsonObject }
                .mapNotNull { request ->
                    val id = request["id"].str()?.toIntOrNull() ?: return@mapNotNull null
                    val user = request["requestedBy"] as? JsonObject
                    AskedBy(
                        requestId = id,
                        who =
                            user?.get("displayName").str()
                                ?: user?.get("username").str()
                                ?: user?.get("email").str()
                                ?: "Someone",
                        whenAsked = ago(request["createdAt"].str()),
                        status = RequestStatus.from(request["status"].str()?.toIntOrNull()),
                        seerrUserId = user?.get("id").str()?.toIntOrNull(),
                    )
                }
        }

        fun answer(
            asked: AskedBy,
            approve: Boolean,
        ) {
            // Benflix: update the box right away, then check with Seerr what really happened
            val newStatus = if (approve) RequestStatus.APPROVED else RequestStatus.DECLINED
            val wasWaiting = asked.status == RequestStatus.PENDING
            _asked.update { list -> list.map { if (it.requestId == asked.requestId) it.copy(status = newStatus) else it } }
            if (wasWaiting) RequestsWaiting.set((RequestsWaiting.count.value - 1).coerceAtLeast(0))
            val forTitle = title
            viewModelScope.launch(WholphinDispatchers.IO) {
                val error =
                    try {
                        seerrService.api.requestApi.requestRequestIdStatusPost(
                            requestId = asked.requestId.toString(),
                            status =
                                if (approve) {
                                    RequestApi.StatusRequestRequestIdStatusPost.APPROVE
                                } else {
                                    RequestApi.StatusRequestRequestIdStatusPost.DECLINE
                                },
                        )
                        null
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not answer request %s", asked.requestId)
                        ex
                    }
                // Jellyfin Enhanced's reply can look like an error even when it worked, so ask Seerr again
                val fresh =
                    forTitle?.let { (isMovie, tmdbId) ->
                        try {
                            fetch(isMovie, tmdbId)
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            Timber.w(ex, "Could not re-check request %s", asked.requestId)
                            null
                        }
                    }
                val now = fresh?.firstOrNull { it.requestId == asked.requestId }?.status
                val worked = if (now != null) now != RequestStatus.PENDING else error == null
                if (fresh != null) {
                    _asked.value = fresh
                } else if (!worked) {
                    _asked.update { list -> list.map { if (it.requestId == asked.requestId) it.copy(status = asked.status) else it } }
                }
                if (worked) {
                    // The title page refreshes its buttons and status
                    forTitle?.let { (isMovie, tmdbId) -> RequestAnswered.events.tryEmit(RequestAnswered.key(isMovie, tmdbId)) }
                } else {
                    if (wasWaiting) RequestsWaiting.set(RequestsWaiting.count.value + 1)
                    showToast(context, if (approve) "Couldn't approve it. Try again." else "Couldn't decline it. Try again.")
                }
            }
        }

        private fun ago(createdAt: String?): String? =
            try {
                createdAt?.let {
                    val days = ChronoUnit.DAYS.between(OffsetDateTime.parse(it), OffsetDateTime.now())
                    when {
                        days < 1 -> "today"
                        days == 1L -> "yesterday"
                        days < 30 -> "$days days ago"
                        days < 60 -> "a month ago"
                        else -> "${days / 30} months ago"
                    }
                }
            } catch (ex: Exception) {
                null
            }
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhoAskedBlock(
    isMovie: Boolean,
    tmdbId: Int,
    modifier: Modifier = Modifier,
    title: String? = null,
    available: Boolean = false,
    viewModel: WhoAskedViewModel = hiltViewModel(key = "who-asked-$isMovie-$tmdbId"),
    cantFindViewModel: CantFindViewModel = hiltViewModel(),
) {
    LaunchedEffect(isMovie, tmdbId) { viewModel.load(isMovie, tmdbId) }
    val asked by viewModel.asked.collectAsState()
    val cantFind by cantFindViewModel.entries.collectAsState()
    var askCantFind by remember { mutableStateOf(false) }
    val isTv = rememberIsTv()
    if (asked.isEmpty()) return
    val key = cantFindKey(isMovie, tmdbId)
    // Benflix: approved but nothing turned up, so Ben can tell whoever asked
    val canMarkCantFind =
        viewModel.isAdmin && !available && asked.any { it.status == RequestStatus.APPROVED } &&
            // An automatic mark only labels it, so Ben can still mark it by hand to stop the search
            (key !in cantFind || cantFind[key]?.auto == true)
    if (askCantFind) {
        CantFindDialog(
            title = title,
            onDismiss = { askCantFind = false },
            onMark = { note ->
                askCantFind = false
                cantFindViewModel.mark(key, title, note, asked.mapNotNull { it.seerrUserId })
            },
        )
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        asked.forEach { entry ->
            val state =
                when (entry.status) {
                    RequestStatus.PENDING -> "waiting for you"
                    RequestStatus.APPROVED -> "approved"
                    RequestStatus.DECLINED -> "declined"
                    RequestStatus.COMPLETED -> "done"
                    else -> null
                }
            val boxModifier =
                Modifier
                    .background(Color(0x22FFFFFF), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            val label: @Composable () -> Unit = {
                Text(
                    text = listOfNotNull("Asked by ${entry.who}", entry.whenAsked, state).joinToString(" · "),
                    fontSize = BenflixType.small,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            val buttons: @Composable () -> Unit = {
                if (entry.status == RequestStatus.PENDING) {
                    Button(onClick = { viewModel.answer(entry, true) }, scale = ButtonDefaults.scale(focusedScale = 1f)) { Text("Approve") }
                    Button(
                        onClick = { viewModel.answer(entry, false) },
                        scale = ButtonDefaults.scale(focusedScale = 1f),
                    ) { Text("Decline") }
                }
            }
            if (isTv) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = boxModifier,
                ) {
                    label()
                    buttons()
                }
            } else {
                // Phones are narrow: the buttons go under the text so they never get pushed off screen
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = boxModifier,
                ) {
                    label()
                    if (entry.status == RequestStatus.PENDING) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            buttons()
                        }
                    }
                }
            }
        }
        if (canMarkCantFind) {
            Button(
                onClick = { askCantFind = true },
                scale = ButtonDefaults.scale(focusedScale = 1f),
            ) { Text("Can't find it", maxLines = 1) }
        }
    }
}
