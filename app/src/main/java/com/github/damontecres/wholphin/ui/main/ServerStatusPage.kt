package com.github.damontecres.wholphin.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.github.damontecres.wholphin.services.BenflixServerHealth
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

/** The top of the page: the server's name and whether it answers */
data class ServerHeader(
    val serverName: String = "",
    val version: String = "",
    val online: Boolean = false,
)

/** The Library numbers */
data class LibraryNumbers(
    val addedThisWeek: Int = 0,
    val movies: Int = 0,
    val shows: Int = 0,
    val episodes: Int = 0,
)

/** Benflix: one disk (or group of disks) the server keeps movies & shows on */
data class ServerDisk(
    val name: String,
    val usedBytes: Long,
    val freeBytes: Long,
) {
    val totalBytes: Long get() = usedBytes + freeBytes
    val usedShare: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
    val nearlyFull: Boolean get() = usedShare >= NEARLY_FULL

    companion object {
        const val NEARLY_FULL = 0.9f
    }
}

/**
 * Benflix: what the Server page showed last time, kept while the app is open, so opening the page
 * again shows the old numbers straight away while fresh ones load.
 */
private object ServerPageMemory {
    @Volatile var header: ServerHeader? = null

    @Volatile var watching: List<WatchingNow>? = null

    @Volatile var library: LibraryNumbers? = null

    @Volatile var problems: List<ServerProblem>? = null
}

/**
 * Server page data. Every section (header, watching now, library, problems, disks) loads on its
 * own and shows as soon as its answer arrives, so one slow question to the server (often the
 * disk check, when Unraid has to wake sleeping disks) no longer holds up the whole page.
 * Each value is null while it's still loading the first time.
 */
@HiltViewModel
class ServerStatusViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        val serverRepository: ServerRepository,
        private val health: BenflixServerHealth,
    ) : ViewModel() {
        private val _header = MutableStateFlow(ServerPageMemory.header)
        val header: StateFlow<ServerHeader?> = _header

        private val _watching = MutableStateFlow(ServerPageMemory.watching)
        val watching: StateFlow<List<WatchingNow>?> = _watching

        private val _library = MutableStateFlow(ServerPageMemory.library)
        val library: StateFlow<LibraryNumbers?> = _library

        private val _problems = MutableStateFlow(ServerPageMemory.problems)
        val problems: StateFlow<List<ServerProblem>?> = _problems

        /** Benflix: disk space (null while loading) */
        private val _disks = MutableStateFlow(health.lastDisks)
        val disks: StateFlow<List<ServerDisk>?> = _disks

        private var started = false

        /** Benflix: how many parts are still fetching fresh numbers (the "Loading new stats" note) */
        private val _loading = MutableStateFlow(0)
        val loading: StateFlow<Int> = _loading

        private fun loadPart(block: suspend () -> Unit) {
            _loading.update { it + 1 }
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    block()
                } finally {
                    _loading.update { it - 1 }
                }
            }
        }

        /** Starts every section loading at once. "Watching now" then keeps itself fresh. */
        fun start() {
            if (started) return
            started = true
            loadPart {
                val h = safe { loadHeader() }
                if (h != null) {
                    set(h)
                } else if (_header.value == null) {
                    _header.value = ServerHeader(online = false)
                }
            }
            _loading.update { it + 1 }
            viewModelScope.launch(WholphinDispatchers.IO) {
                var first = true
                while (true) {
                    val list = safe { loadWatching() }
                    if (list != null) {
                        setWatching(list)
                    } else if (_watching.value == null) {
                        setWatching(emptyList())
                    }
                    if (first) {
                        first = false
                        _loading.update { it - 1 }
                    }
                    delay(REFRESH_MS)
                }
            }
            loadPart {
                val numbers = loadLibrary()
                _library.value = numbers
                ServerPageMemory.library = numbers
            }
            loadPart {
                val list = safe { loadProblems() }.orEmpty()
                _problems.value = list
                ServerPageMemory.problems = list
            }
            loadPart {
                _disks.value = safe { health.readDisks() } ?: _disks.value.orEmpty()
            }
        }

        fun stop(sessionId: String) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    api.sessionApi.sendPlaystateCommand(sessionId, PlaystateCommand.STOP)
                    delay(1_500)
                    setWatching(loadWatching())
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not stop that stream")
                }
            }
        }

        private fun set(header: ServerHeader) {
            _header.value = header
            ServerPageMemory.header = header
        }

        private fun setWatching(list: List<WatchingNow>) {
            _watching.value = list
            ServerPageMemory.watching = list
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

        private suspend fun loadHeader(): ServerHeader {
            val info = api.systemApi.getSystemInfo().content
            return ServerHeader(serverName = info.serverName.orEmpty(), version = info.version.orEmpty(), online = true)
        }

        private suspend fun loadWatching(): List<WatchingNow> =
            api.sessionApi
                .getSessions(null, null, ACTIVE_WITHIN_SECONDS)
                .content
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
                }

        /** Totals and "added this week" are asked for at the same time */
        private suspend fun loadLibrary(): LibraryNumbers =
            coroutineScope {
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
                                        // Benflix: only the dates are needed, so skip pictures,
                                        // watched info and the slow "how many in total" count
                                        enableImages = false,
                                        enableUserData = false,
                                        enableTotalRecordCount = false,
                                    ),
                                ).content.items
                        }
                    }
                val c = counts.await()
                LibraryNumbers(
                    addedThisWeek = recent.await().orEmpty().count { it.dateCreated?.isAfter(weekAgo) == true },
                    movies = c?.movieCount ?: 0,
                    shows = c?.seriesCount ?: 0,
                    episodes = c?.episodeCount ?: 0,
                )
            }

        private suspend fun loadProblems(): List<ServerProblem> {
            val timeFormat = DateTimeFormatter.ofPattern("h:mm a")
            return api.activityLogApi
                .getLogEntries(0, LOG_LIMIT, LocalDateTime.now(ZoneOffset.UTC).minusDays(1))
                .content.items
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
                }
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
 * convert their video, what was added this week, and any problems in the last day. Each section
 * appears as soon as its own answer arrives; until then it shows a grey "loading" box.
 */
@Composable
fun ServerStatusPage(
    modifier: Modifier = Modifier,
    viewModel: ServerStatusViewModel = hiltViewModel(),
    toolsViewModel: ServerToolsViewModel = hiltViewModel(),
) {
    val userDto by viewModel.serverRepository.currentUserDtoFlow.collectAsState(null)
    val isAdmin = userDto?.policy?.isAdministrator == true
    LaunchedEffect(isAdmin) {
        if (isAdmin) {
            viewModel.start()
            toolsViewModel.load()
        }
    }
    val toolsState by toolsViewModel.state.collectAsState()
    var toolsDialog by remember { mutableStateOf<ServerToolsDialog?>(null) }
    val header by viewModel.header.collectAsState()
    val watching by viewModel.watching.collectAsState()
    val library by viewModel.library.collectAsState()
    val problems by viewModel.problems.collectAsState()
    val disks by viewModel.disks.collectAsState()
    val loadingParts by viewModel.loading.collectAsState()
    var stopping by remember { mutableStateOf<WatchingNow?>(null) }
    val firstFocus = remember { FocusRequester() }
    val watchingReady = watching != null
    LaunchedEffect(watchingReady) { if (watchingReady) firstFocus.tryRequestFocus() }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp, start = 8.dp, end = 8.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = (header?.serverName.orEmpty().ifBlank { "SERVER" }).uppercase(),
                    fontFamily = BenflixTitleFont,
                    fontSize = 44.sp,
                    letterSpacing = 1.sp,
                )
                val h = header
                if (h == null) {
                    StatusChip(text = "Checking…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    StatusChip(
                        text = if (h.online) "Online · Jellyfin ${h.version}" else "Can't reach the server",
                        color = if (h.online) GoodGreen else WarnGold,
                    )
                }
                // Benflix: the numbers below may be from last time until the fresh ones arrive
                AnimatedVisibility(visible = isAdmin && loadingParts > 0, enter = fadeIn(), exit = fadeOut()) {
                    StatusChip(text = "Loading new stats…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!isAdmin) {
            item { Text("Only the server's admin can see this page.", fontSize = 18.sp) }
            return@LazyColumn
        }
        val watchList = watching
        item { SectionTitle(if (watchList == null) "Watching now" else "Watching now (${watchList.size})") }
        when {
            watchList == null -> {
                item { LoadingBox("Seeing who's watching…") }
            }

            watchList.isEmpty() -> {
                item {
                    ListItem(
                        selected = false,
                        onClick = {},
                        headlineContent = { Text("Nobody is watching right now") },
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                }
            }

            else -> {
                items(watchList, key = { it.sessionId }) { w ->
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
                        modifier = if (w == watchList.first()) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
            }
        }
        item { SectionTitle("Library") }
        item {
            val lib = library
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                BigNumber(lib?.let { "${it.addedThisWeek}" }, "added this week", Modifier.weight(1f))
                BigNumber(lib?.let { "%,d".format(it.movies) }, "movies", Modifier.weight(1f))
                BigNumber(lib?.let { "%,d".format(it.shows) }, "shows", Modifier.weight(1f))
                BigNumber(lib?.let { "%,d".format(it.episodes) }, "episodes", Modifier.weight(1f))
            }
        }
        // Benflix: how full the disks are
        val diskList = disks
        when {
            diskList == null -> {
                item { SectionTitle("Disk space") }
                item { LoadingBox("Checking the disks… (sleeping disks can take a few seconds to wake up)") }
            }

            diskList.isNotEmpty() -> {
                item { SectionTitle("Disk space") }
                items(diskList) { disk -> DiskBar(disk) }
            }
        }
        val problemList = problems
        item {
            SectionTitle(if (problemList == null) "Problems in the last day" else "Problems in the last day (${problemList.size})")
        }
        when {
            problemList == null -> {
                item { LoadingBox("Looking for problems…") }
            }

            problemList.isEmpty() -> {
                item {
                    ListItem(
                        selected = false,
                        onClick = {},
                        headlineContent = { Text("No problems. All good!") },
                    )
                }
            }

            else -> {
                items(problemList) { p ->
                    ListItem(
                        selected = false,
                        onClick = {},
                        headlineContent = { Text(p.text) },
                        supportingContent = { Text(p.time) },
                    )
                }
            }
        }
        serverToolsItems(
            state = toolsState,
            viewModel = toolsViewModel,
            showDialog = { toolsDialog = it },
            sectionTitle = { SectionTitle(it) },
        )
    }
    ServerToolsDialogs(
        state = toolsState,
        viewModel = toolsViewModel,
        dialog = toolsDialog,
        onDismiss = { toolsDialog = null },
    )
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

/** Benflix: a grey box with a gently pulsing line, shown where a section is still loading */
@Composable
private fun LoadingBox(text: String) {
    val pulse = rememberInfiniteTransition(label = "loading")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "loadingAlpha",
    )
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { this.alpha = alpha },
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
    value: String?,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(12.dp),
    ) {
        // Benflix: a dash while the number is still loading
        Text(
            text = value ?: "–",
            fontFamily = BenflixTitleFont,
            fontSize = 34.sp,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
        )
        Text(text = label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Benflix: a bar showing how full a disk is, gold with a warning when it's nearly full */
@Composable
private fun DiskBar(disk: ServerDisk) {
    val color = if (disk.nearlyFull) WarnGold else GoodGreen
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = disk.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
            Text(
                text = "${sizeText(disk.freeBytes)} free of ${sizeText(disk.totalBytes)}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(disk.usedShare.coerceIn(0.02f, 1f))
                        .height(10.dp)
                        .background(color, RoundedCornerShape(50)),
            )
        }
        if (disk.nearlyFull) {
            Text(
                text = "Almost full! New downloads may stop soon. Delete a few things or add a disk.",
                fontSize = 14.sp,
                color = WarnGold,
            )
        }
    }
}

private fun sizeText(bytes: Long): String {
    val tb = bytes / 1_000_000_000_000.0
    return if (tb >= 1) "%.1f TB".format(tb) else "%.0f GB".format(bytes / 1_000_000_000.0)
}
