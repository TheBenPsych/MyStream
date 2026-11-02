package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.api.seerr.RequestApi
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.nav.RequestsWaiting
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
)

/**
 * Benflix (admins): on a movie or show you could ask for, show who already asked for it and when,
 * with Approve and Decline right there.
 */
@HiltViewModel
class WhoAskedViewModel
    @Inject
    constructor(
        private val seerrService: SeerrService,
        private val serverRepository: ServerRepository,
    ) : ViewModel() {
        private val _asked = MutableStateFlow<List<AskedBy>>(emptyList())
        val asked: StateFlow<List<AskedBy>> = _asked
        private var loadedFor: String? = null

        val isAdmin: Boolean get() = serverRepository.currentUserDto?.policy?.isAdministrator == true

        fun load(
            isMovie: Boolean,
            tmdbId: Int,
        ) {
            val key = "$isMovie-$tmdbId"
            if (loadedFor == key || !isAdmin) return
            loadedFor = key
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    val raw = seerrService.api.getRaw(if (isMovie) "/movie/$tmdbId" else "/tv/$tmdbId")
                    val media = raw?.let { Json.parseToJsonElement(it).jsonObject["mediaInfo"] as? JsonObject }
                    _asked.value =
                        (media?.get("requests") as? JsonArray)
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
                                )
                            }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not look up who asked for %s", key)
                }
            }
        }

        fun answer(
            asked: AskedBy,
            approve: Boolean,
        ) {
            viewModelScope.launch(WholphinDispatchers.IO) {
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
                    val newStatus = if (approve) RequestStatus.APPROVED else RequestStatus.DECLINED
                    _asked.update { list -> list.map { if (it.requestId == asked.requestId) it.copy(status = newStatus) else it } }
                    RequestsWaiting.set(RequestsWaiting.count.value - 1)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not answer request %s", asked.requestId)
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
                        else -> "${days / 30} months ago"
                    }
                }
            } catch (ex: Exception) {
                null
            }
    }

@Composable
fun WhoAskedBlock(
    isMovie: Boolean,
    tmdbId: Int,
    modifier: Modifier = Modifier,
    viewModel: WhoAskedViewModel = hiltViewModel(key = "who-asked-$isMovie-$tmdbId"),
) {
    LaunchedEffect(isMovie, tmdbId) { viewModel.load(isMovie, tmdbId) }
    val asked by viewModel.asked.collectAsState()
    if (asked.isEmpty()) return
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        asked.forEach { entry ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .background(Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                val state =
                    when (entry.status) {
                        RequestStatus.PENDING -> "waiting for you"
                        RequestStatus.APPROVED -> "approved"
                        RequestStatus.DECLINED -> "declined"
                        RequestStatus.COMPLETED -> "done"
                        else -> null
                    }
                Text(
                    text = listOfNotNull("Asked by ${entry.who}", entry.whenAsked, state).joinToString(" · "),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                if (entry.status == RequestStatus.PENDING) {
                    Button(onClick = { viewModel.answer(entry, true) }) { Text("Approve") }
                    Button(onClick = { viewModel.answer(entry, false) }) { Text("Decline") }
                }
            }
        }
    }
}
