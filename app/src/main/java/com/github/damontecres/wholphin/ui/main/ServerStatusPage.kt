package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.activityLogApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.LogLevel
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** One person watching something right now */
data class WatchingNow(
    val sessionId: String,
    val who: String,
    val what: String,
    val device: String,
    val howItPlays: String,
    val converting: Boolean,
    val paused: Boolean,
)

data class ServerProblem(
    val text: String,
    val time: String,
)

data class ServerStatus(
    val serverName: String = "",
    val version: String = "",
    val online: Boolean = false,
    val watching: List<WatchingNow> = emptyList(),
    val addedThisWeek: Int = 0,
    val movies: Int = 0,
    val shows: Int = 0,
    val episodes: Int = 0,
    val problems: List<ServerProblem> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class ServerStatusViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        val serverRepository: ServerRepository,
    ) : ViewModel() {
        private val _status = MutableStateFlow(ServerStatus())
        val status: StateFlow<ServerStatus> = _status

        fun refresh() {
            viewModelScope.launch {
                _status.value = withContext(WholphinDispatchers.IO) { load() }
            }
        }

        /** Keeps the "Watching now" list fresh while the page is open */
        fun keepRefreshing() {
            viewModelScope.launch {
                while (true) {
                    _status.value = withContext(WholphinDispatchers.IO) { load() }
                    delay(REFRESH_MS)
                }
            }
        }

        fun stop(sessionId: String) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    api.sessionApi.sendPlaystateCommand(sessionId, PlaystateCommand.STOP)
                    delay(1_500)
                    _status.value = load()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not stop that stream")
                }
            }
        }

        private suspend fun <T> safe(block: suspend () -> T): T? =
            try {
                block()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Server page: a part could not load")
                null
            }

        private suspend fun load(): ServerStatus =
            withContext(WholphinDispatchers.IO) {
                val info = async { safe { api.systemApi.getSystemInfo().content } }
                val sessions = async { safe { api.sessionApi.getSessions(null, null, ACTIVE_WITHIN_SECONDS).content } }
                val counts = async { safe { api.libraryApi.getItemCounts().content } }
                val weekAgo = LocalDateTime.now(ZoneOffset.UTC).minusDays(7)
                val recent =
                    async {
                        safe {
                            api.itemsApi
                                .getItems(
                                    GetItemsRequest(
                                        userId = serverRepository.currentUser?.id,
                                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                                        recursive = true,
                                        sortBy = listOf(ItemSortBy.DATE_CREATED),
                                        sortOrder = listOf(SortOrder.DESCENDING),
                                        fields = listOf(ItemFields.DATE_CREATED),
                                        limit = RECENT_LIMIT,
                                    ),
                                ).content.items
                        }
                    }
                val log =
                    async {
                        safe {
                            api.activityLogApi
                                .getLogEntries(0, LOG_LIMIT, LocalDateTime.now(ZoneOffset.UTC).minusDays(1))
                                .content.items
                        }
                    }
                val timeFormat = DateTimeFormatter.ofPattern("h:mm a")
                val sysInfo = info.await()
                val c = counts.await()
                ServerStatus(
                    serverName = sysInfo?.serverName.orEmpty(),
                    version = sysInfo?.version.orEmpty(),
                    online = sysInfo != null,
                    watching =
                        sessions
                            .await()
                            .orEmpty()
                            .filter { it.nowPlayingItem != null }
                            .map { s ->
                                val item = s.nowPlayingItem!!
                                val method = s.playState?.playMethod
                                val transcode = s.transcodingInfo
                                val converting = method == PlayMethod.TRANSCODE
                                val title =
                                    if (item.type == BaseItemKind.EPISODE) {
                                        listOfNotNull(item.seriesName, item.name).joinToString(" · ")
                                    } else {
                                        item.name.orEmpty()
                                    }
                                WatchingNow(
                                    sessionId = s.id.orEmpty(),
                                    who = s.userName ?: "Someone",
                                    what = title,
                                    device = listOfNotNull(s.deviceName, s.client).distinct().joinToString(" · "),
                                    howItPlays =
                                        when {
                                            converting && transcode?.videoCodec != null -> {
                                                "Converting to ${transcode.videoCodec?.uppercase()} (server is working hard)"
                                            }

                                            converting -> {
                                                "Converting (server is working hard)"
                                            }

                                            method == PlayMethod.DIRECT_STREAM -> {
                                                "Direct stream (light work)"
                                            }

                                            else -> {
                                                "Direct play (no work for the server)"
                                            }
                                        },
                                    converting = converting,
                                    paused = s.playState?.isPaused == true,
                                )
                            },
                    addedThisWeek =
                        recent.await().orEmpty().count { item ->
                            item.dateCreated?.isAfter(weekAgo) == true
                        },
                    movies = c?.movieCount ?: 0,
                    shows = c?.seriesCount ?: 0,
                    episodes = c?.episodeCount ?: 0,
                    problems =
                        log
                            .await()
                            .orEmpty()
                            .filter { it.severity == LogLevel.ERROR || it.severity == LogLevel.CRITICAL }
                            .map {
                                ServerProblem(
                                    text = it.name,
                                    time =
                                        it.date
                                            .atOffset(ZoneOffset.UTC)
                                            .atZoneSameInstant(java.time.ZoneId.systemDefault())
                                            .format(timeFormat),
                                )
                            },
                    loaded = true,
                )
            }

        companion object {
            private const val ACTIVE_WITHIN_SECONDS = 960
            private const val RECENT_LIMIT = 300
            private const val LOG_LIMIT = 40
            private const val REFRESH_MS = 15_000L
        }
    }

/**
 * Benflix Server page (only for admins): who's watching right now, whether the server has to
 * convert their video, what was added this week, and any problems in the last day.
 */
@Composable
fun ServerStatusPage(
    modifier: Modifier = Modifier,
    viewModel: ServerStatusViewModel = hiltViewModel(),
) {
    val userDto by viewModel.serverRepository.currentUserDtoFlow.collectAsState(null)
    val isAdmin = userDto?.policy?.isAdministrator == true
    LaunchedEffect(isAdmin) { if (isAdmin) viewModel.keepRefreshing() }
    val status by viewModel.status.collectAsState()
    var stopping by remember { mutableStateOf<WatchingNow?>(null) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(status.loaded) { if (status.loaded) firstFocus.tryRequestFocus() }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp, start = 8.dp, end = 8.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = (status.serverName.ifBlank { "SERVER" }).uppercase(),
                    fontFamily = BenflixTitleFont,
                    fontSize = 44.sp,
                    letterSpacing = 1.sp,
                )
                if (status.loaded) {
                    StatusChip(
                        text = if (status.online) "Online · Jellyfin ${status.version}" else "Can't reach the server",
                        color = if (status.online) GoodGreen else WarnGold,
                    )
                }
            }
        }
        if (!isAdmin) {
            item { Text("Only the server's admin can see this page.", fontSize = 18.sp) }
            return@LazyColumn
        }
        if (!status.loaded) {
            item { Text("Checking the server…", fontSize = 18.sp) }
            return@LazyColumn
        }
        item { SectionTitle("Watching now (${status.watching.size})") }
        if (status.watching.isEmpty()) {
            item {
                ListItem(
                    selected = false,
                    onClick = {},
                    headlineContent = { Text("Nobody is watching right now") },
                    modifier = Modifier.focusRequester(firstFocus),
                )
            }
        }
        items(status.watching, key = { it.sessionId }) { w ->
            ListItem(
                selected = false,
                onClick = { stopping = w },
                headlineContent = {
                    Text(
                        text = "${w.who} · ${w.what}" + if (w.paused) " (paused)" else "",
                        fontWeight = FontWeight.Medium,
                    )
                },
                supportingContent = { Text(w.device) },
                trailingContent = {
                    StatusChip(text = w.howItPlays, color = if (w.converting) WarnGold else GoodGreen)
                },
                modifier = if (w == status.watching.first()) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
        item { SectionTitle("Library") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                BigNumber("${status.addedThisWeek}", "added this week", Modifier.weight(1f))
                BigNumber("%,d".format(status.movies), "movies", Modifier.weight(1f))
                BigNumber("%,d".format(status.shows), "shows", Modifier.weight(1f))
                BigNumber("%,d".format(status.episodes), "episodes", Modifier.weight(1f))
            }
        }
        item { SectionTitle("Problems in the last day (${status.problems.size})") }
        if (status.problems.isEmpty()) {
            item {
                ListItem(
                    selected = false,
                    onClick = {},
                    headlineContent = { Text("No problems. All good!") },
                )
            }
        }
        items(status.problems) { p ->
            ListItem(
                selected = false,
                onClick = {},
                headlineContent = { Text(p.text) },
                supportingContent = { Text(p.time) },
            )
        }
    }
    stopping?.let { w ->
        ConfirmDialog(
            title = "Stop ${w.who}'s stream?",
            body = "This stops \"${w.what}\" on ${w.device.ifBlank { "their device" }}. Their spot is saved.",
            onCancel = { stopping = null },
            onConfirm = {
                viewModel.stop(w.sessionId)
                stopping = null
            },
        )
    }
}

private val GoodGreen = Color(0xFF46D37A)
private val WarnGold = Color(0xFFF2B84B)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun StatusChip(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier =
            Modifier
                .background(color.copy(alpha = 0.16f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun BigNumber(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(12.dp),
    ) {
        Text(text = value, fontFamily = BenflixTitleFont, fontSize = 34.sp)
        Text(text = label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
