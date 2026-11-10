package com.github.damontecres.wholphin.ui.main

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.ui.door.DOOR_HOME
import com.github.damontecres.wholphin.ui.door.DoorFrame
import com.github.damontecres.wholphin.ui.door.DoorFrameState
import com.github.damontecres.wholphin.ui.door.DoorSection
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixPlayFilled
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.preferences.StringInput
import com.github.damontecres.wholphin.ui.preferences.StringInputDialog
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.PhonePullToRefresh
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.PlaystateCommand
import timber.log.Timber
import javax.inject.Inject

/** Benflix: the parts of the Server page, like the menu in Jellyfin's Dashboard */
enum class ServerSection(
    val title: String,
    val hint: String,
    val icon: ImageVector,
) {
    OVERVIEW("Overview", "How the server is doing, requests and problems that need you", BenflixIcon.HOME.vector),
    NOW("Watching", "Who's watching, and how hard the server works for it", BenflixPlayFilled),
    LIBRARY("Library", "Movie and show numbers, disk space, the library checkup and downloads", BenflixIcon.LIST.vector),
    PEOPLE("People", "Accounts for family and friends, and messages to them", BenflixIcon.PERSON.vector),
    TOOLS(
        "Tools",
        "Scan for new stuff, the server's chores, tidy track names, Home's weather town, and restarting Jellyfin",
        BenflixIcon.WRENCH.vector,
    ),
    ;

    /** Redesign 2: the key on the door-style rail (Overview is the rail's Home) */
    val key: String get() = if (this == OVERVIEW) DOOR_HOME else name.lowercase()

    val railIcon: BenflixIcon
        get() =
            when (this) {
                OVERVIEW -> BenflixIcon.HOME
                NOW -> BenflixIcon.PLAY
                LIBRARY -> BenflixIcon.FILM
                PEOPLE -> BenflixIcon.PERSON
                TOOLS -> BenflixIcon.WRENCH
            }

    fun railItem(light: Light?): DoorSection =
        DoorSection(key, title + if (light == Light.BAD || light == Light.WARN) " •" else "", railIcon)

    companion object {
        fun fromKey(key: String): ServerSection = entries.firstOrNull { it.key == key } ?: OVERVIEW
    }
}

/** Benflix: green, gold or red for the traffic lights */
internal enum class Light { GOOD, WARN, BAD, UNKNOWN }

@HiltViewModel
class ServerStatusViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        val serverRepository: ServerRepository,
        val data: ServerDashboardData,
        private val actions: BenflixServerActions,
        private val setupCheck: com.github.damontecres.wholphin.services.TowerSetupCheck,
        val navigationManager: com.github.damontecres.wholphin.services.NavigationManager,
    ) : ViewModel() {
        /** Redesign 2: Tower needs the setup command (null = up to date, or Tower couldn't be asked) */
        val setupNeed = setupCheck.need

        fun checkSetup(fresh: Boolean = false) {
            viewModelScope.launch(WholphinDispatchers.IO) { setupCheck.check(fresh) }
        }

        /** A short result to show in a pop-up (title to text) */
        private val _notice = MutableStateFlow<Pair<String, String>?>(null)
        val notice: StateFlow<Pair<String, String>?> = _notice

        private val _chores = MutableStateFlow<List<ServerChore>?>(null)
        val chores: StateFlow<List<ServerChore>?> = _chores

        private val _libraries = MutableStateFlow<List<BaseItemDto>?>(null)
        val libraries: StateFlow<List<BaseItemDto>?> = _libraries

        private var choreWatch: Job? = null

        fun clearNotice() {
            _notice.value = null
        }

        fun pause(w: WatchingNow) =
            act("Couldn't pause ${w.who}") {
                if (w.paused) actions.resume(w.sessionId) else actions.pause(w.sessionId)
                data.refreshWatchingSoon()
            }

        fun message(
            w: WatchingNow,
            text: String,
        ) {
            if (text.isBlank()) return
            act("Couldn't send the message") {
                actions.message(w.sessionId, text.trim())
                _notice.value = "Message sent" to "It popped up on ${w.who}'s ${w.device.ifBlank { "screen" }}."
            }
        }

        /** Opens the Tools section: the libraries (for Scan) and the chores */
        fun loadTools() {
            act(null) { if (_libraries.value == null) _libraries.value = actions.libraries() }
            refreshChores()
        }

        fun scan(library: BaseItemDto?) =
            act("Couldn't start the scan") {
                actions.scan(library?.id)
                _notice.value =
                    "Scanning" to
                    "Jellyfin is looking for new files in ${library?.name ?: "every library"}. " +
                    "New things show up on Home when it's done. You can watch it under Server chores."
                delay(1_000)
                refreshChores()
            }

        fun runChore(chore: ServerChore) =
            act("Couldn't start ${chore.name}") {
                actions.runChore(chore.id)
                delay(1_000)
                refreshChores()
            }

        /** Reads the chores, and keeps reading them every few seconds while one is running */
        private fun refreshChores() {
            choreWatch?.cancel()
            choreWatch =
                viewModelScope.launch(WholphinDispatchers.IO) {
                    repeat(CHORE_CHECKS) {
                        val list = safeCall { actions.chores() } ?: return@launch
                        _chores.value = list
                        if (list.none { it.running }) return@launch
                        delay(3_000)
                    }
                }
        }

        /** When a planned restart will happen (millis), or null */
        val plannedRestart: StateFlow<Long?> = actions.plannedRestart

        /**
         * Warns every screen first, then restarts Jellyfin after [minutes] (0 = now).
         * The wait happens app-wide, so leaving this page doesn't call the restart off.
         */
        fun restart(minutes: Int) =
            act("Couldn't restart Jellyfin") {
                if (minutes > 0) {
                    actions.messageEveryone(
                        "Benflix is restarting in $minutes minute" + (if (minutes == 1) "" else "s") +
                            ". It'll be back a minute later.",
                    )
                    actions.planRestart(minutes) { problem ->
                        if (problem != null) _notice.value = "Couldn't restart Jellyfin" to problem
                    }
                    _notice.value =
                        "Restart planned" to
                        "Everyone was warned. Jellyfin restarts in $minutes minute" +
                        (if (minutes == 1) "" else "s") +
                        ", even if you leave this page. Just keep Benflix open on this device until then. " +
                        "You can call it off under Tools."
                    return@act
                }
                actions.restartJellyfin()
                _notice.value = "Restarting" to "Jellyfin is restarting. It usually takes about a minute to come back."
            }

        fun cancelRestart() =
            act("Couldn't call off the restart") {
                if (actions.cancelPlannedRestart()) {
                    _notice.value = "Restart called off" to "Jellyfin won't restart. Everyone was told."
                }
            }

        private fun act(
            failTitle: String?,
            block: suspend () -> Unit,
        ) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    block()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Server page action failed")
                    if (failTitle != null) {
                        _notice.value = failTitle to (ex.message ?: "The server said no. Try again in a moment.")
                    }
                }
            }
        }

        private suspend fun <T> safeCall(block: suspend () -> T): T? =
            try {
                block()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Server page: chores could not load")
                null
            }

        fun stop(sessionId: String) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    api.sessionApi.sendPlaystateCommand(sessionId, PlaystateCommand.STOP)
                    data.refreshWatchingSoon()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not stop that stream")
                }
            }
        }
    }

/**
 * Benflix Server page (only for admins), split into sections like Jellyfin's Dashboard.
 * TV: the sections in a menu on the left (moving through it shows each one straight away).
 * Phone: the sections as tabs along the top, pull down to refresh.
 * Each part shows as soon as its own answer arrives; until then a grey "loading" box.
 */
@Composable
fun ServerStatusPage(
    modifier: Modifier = Modifier,
    viewModel: ServerStatusViewModel = hiltViewModel(),
    toolsViewModel: ServerToolsViewModel = hiltViewModel(),
    adminTitle: AdminTitleViewModel = hiltViewModel(),
    requestsViewModel: com.github.damontecres.wholphin.ui.discover.BenflixRequestsViewModel = hiltViewModel(),
    towerViewModel: ServerTowerViewModel = hiltViewModel(),
    speedCheck: ServerSpeedCheckViewModel = hiltViewModel(),
    care: ServerCareViewModel = hiltViewModel(),
) {
    val isTv = rememberIsTv()
    val data = viewModel.data
    val userDto by viewModel.serverRepository.currentUserDtoFlow.collectAsState(null)
    val isAdmin = userDto?.policy?.isAdministrator == true
    LaunchedEffect(isAdmin) { if (isAdmin) data.preload() }
    LaunchedEffect(Unit) {
        com.github.damontecres.wholphin.ui.components.ServerAlertNotice
            .clear()
    }
    // "Watching now" only keeps checking while this page is on screen
    LifecycleResumeEffect(isAdmin) {
        if (isAdmin) data.startWatching()
        onPauseOrDispose { data.stopWatching() }
    }
    // Redesign 2 (approved sv2-glance): the sections sit on the same rail as the doors (phones: chips).
    // Settings' "Find a setting" can open the page on a section and tool group.
    val jump = remember { ServerPageJump.take() }
    val frame =
        remember {
            DoorFrameState(
                door = com.github.damontecres.wholphin.ui.theme.BenflixDoor.MOVIES,
                start = (jump.first ?: ServerSection.OVERVIEW).key,
                jumps = false,
                pillColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.Text,
                onPillColor = com.github.damontecres.wholphin.ui.theme.BenflixColors.Tray,
            )
        }
    val section = ServerSection.fromKey(frame.section)
    // TV: start on the page itself (the rail opens with Left)
    LaunchedEffect(Unit) {
        if (!isTv) return@LaunchedEffect
        delay(FIRST_FOCUS_MS)
        frame.content.tryRequestFocus()
    }
    var toolGroup by rememberSaveable { mutableStateOf(jump.second) }
    LaunchedEffect(isAdmin) {
        if (isAdmin) {
            viewModel.checkSetup()
            // Overview's pills and Needs you card need Tower's answer too
            data.refreshTower(fresh = false)
        }
    }
    LaunchedEffect(section, toolGroup, isAdmin) {
        if (!isAdmin) return@LaunchedEffect
        when (section) {
            ServerSection.OVERVIEW -> {
                requestsViewModel.loadRequests(mineOnly = false)
                care.loadReport()
            }

            ServerSection.PEOPLE -> {
                toolsViewModel.loadPeople()
                data.loadHistory()
            }

            ServerSection.NOW -> {
                data.loadHistory()
            }

            ServerSection.TOOLS -> {
                when (toolGroup ?: ServerToolGroup.LIBRARY) {
                    ServerToolGroup.LIBRARY -> {
                        viewModel.loadTools()
                        care.loadLibrary()
                    }

                    ServerToolGroup.DOWNLOADS -> {
                        data.refreshTower()
                    }

                    ServerToolGroup.PEOPLE -> {
                        toolsViewModel.loadNotes()
                        toolsViewModel.loadVersions()
                    }

                    ServerToolGroup.FIXES -> {
                        towerViewModel.loadTracks()
                    }

                    ServerToolGroup.TOWER -> {
                        viewModel.loadTools()
                        data.refreshTower()
                        viewModel.checkSetup(fresh = true)
                    }
                }
            }

            ServerSection.LIBRARY -> {
                data.refreshTower()
            }
        }
    }

    val toolsState by toolsViewModel.state.collectAsState()
    var toolsDialog by remember { mutableStateOf<ServerToolsDialog?>(null) }
    val header by data.header.collectAsState()
    val watching by data.watching.collectAsState()
    val library by data.library.collectAsState()
    val problems by data.problems.collectAsState()
    val reports by data.problemReports.collectAsState()
    val disks by data.disks.collectAsState()
    val loadingParts by data.loading.collectAsState()
    val updatedAt by data.updatedAt.collectAsState()
    val waitingOn by data.waitingOn.collectAsState()
    var stopping by remember { mutableStateOf<WatchingNow?>(null) }
    var messaging by remember { mutableStateOf<WatchingNow?>(null) }
    var askRestart by remember { mutableStateOf(false) }
    val history by data.history.collectAsState()
    val careReport by care.report.collectAsState()
    val careReportProblem by care.reportProblem.collectAsState()
    val carePlaytest by care.playtest.collectAsState()
    val careTidy by care.tidy.collectAsState()
    val careTicked by care.ticked.collectAsState()
    val careBiggest by care.showBiggest.collectAsState()
    val careProblem by care.libraryProblem.collectAsState()
    val chores by viewModel.chores.collectAsState()
    val libraries by viewModel.libraries.collectAsState()
    val plannedRestart by viewModel.plannedRestart.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val requests by requestsViewModel.requests.collectAsState()
    val seerrActive by requestsViewModel.active.collectAsState(false)
    val lookYourself by com.github.damontecres.wholphin.ui.nav.RequestsWaiting.lookYourself
        .collectAsState()
    val tower by data.tower.collectAsState()
    val towerProblem by data.towerProblem.collectAsState()
    val tracks by towerViewModel.tracks.collectAsState()
    val speedState by speedCheck.state.collectAsState()
    val tracksProblem by towerViewModel.tracksProblem.collectAsState()
    val setupNeed by viewModel.setupNeed.collectAsState()

    val lights =
        remember(header, disks, problems, reports, watching, tower) {
            mapOf(
                ServerSection.OVERVIEW to
                    when {
                        header == null -> Light.UNKNOWN
                        header?.online == false -> Light.BAD
                        reports.orEmpty().isNotEmpty() -> Light.WARN
                        problems.orEmpty().sumOf { it.count } >= MANY_PROBLEMS -> Light.WARN
                        else -> Light.GOOD
                    },
                ServerSection.NOW to
                    when {
                        watching == null -> Light.UNKNOWN
                        watching.orEmpty().any { it.converting } -> Light.WARN
                        else -> Light.GOOD
                    },
                ServerSection.LIBRARY to
                    when {
                        disks == null && tower == null -> Light.UNKNOWN
                        brokenApps(tower).isNotEmpty() -> Light.BAD
                        disks.orEmpty().any { it.nearlyFull } -> Light.WARN
                        tower?.downloads.orEmpty().any { it.stuck } -> Light.WARN
                        else -> Light.GOOD
                    },
            )
        }
    val sectionTitle: @Composable (String) -> Unit = { SectionTitle(it) }
    val openTools: (ServerToolGroup) -> Unit = { group ->
        toolGroup = group
        frame.pick(ServerSection.TOOLS.key)
    }
    // Redesign 2 (approved sv2-glance): what needs you, with its buttons right on the card
    val pendingStatus = com.github.damontecres.wholphin.data.model.RequestStatus.PENDING
    val healthNeeds =
        remember(header, disks, problems, reports, tower, setupNeed) {
            buildList {
                if (header?.online == false) {
                    add(NeedLine("offline", "Benflix can't reach Jellyfin. Is Tower on?", Light.BAD))
                }
                brokenApps(tower).forEach { app ->
                    add(
                        NeedLine(
                            key = "app-${app.name}",
                            text = "${app.nice} isn't running properly",
                            light = Light.BAD,
                            supporting = app.status,
                            actions =
                                if (app.restartable) {
                                    listOf((if (app.running) "Restart" else "Start") to { towerViewModel.ask(TowerAsk.Restart(app)) })
                                } else {
                                    emptyList()
                                },
                        ),
                    )
                }
                tower?.downloads.orEmpty().filter { it.stuck }.forEach { d ->
                    add(
                        NeedLine(
                            key = "stuck-${d.app}-${d.id}",
                            text = "Stuck download: ${d.title}",
                            light = Light.WARN,
                            supporting = d.why ?: "It stopped moving",
                            actions = listOf("Find another" to { towerViewModel.ask(TowerAsk.Replace(d)) }),
                        ),
                    )
                }
                if (setupNeed != null) {
                    add(
                        NeedLine(
                            key = "setup",
                            text = "Tower needs the setup command",
                            light = Light.WARN,
                            supporting = "The yellow card above has it, with Copy",
                        ),
                    )
                }
                disks.orEmpty().filter { it.nearlyFull }.forEach {
                    add(
                        NeedLine(
                            key = "disk-${it.name}",
                            text = "The disk with ${it.name} is ${(it.usedShare * 100).toInt()}% full",
                            light = Light.WARN,
                            actions = listOf("Go" to { frame.pick(ServerSection.LIBRARY.key) }),
                        ),
                    )
                }
                reports.orEmpty().size.takeIf { it > 0 }?.let {
                    add(
                        NeedLine(
                            key = "reports",
                            text = if (it == 1) "1 problem reported by family" else "$it problems reported by family",
                            light = Light.WARN,
                            supporting = "Further down this page",
                        ),
                    )
                }
                problems.orEmpty().sumOf { it.count }.takeIf { it >= MANY_PROBLEMS }?.let {
                    add(NeedLine("problems", "$it problems on the server in the last day", Light.WARN, "Further down this page"))
                }
            }
        }
    val requestNeeds =
        remember(requests, lookYourself) {
            val waiting = requests.orEmpty().filter { it.status == pendingStatus }
            buildList {
                if (waiting.size > 1) {
                    add(
                        NeedLine(
                            key = "req-all",
                            text = "${waiting.size} requests are waiting for you",
                            light = Light.WARN,
                            actions = listOf("Yes to all" to requestsViewModel::approveAll),
                        ),
                    )
                }
                waiting.forEach { r ->
                    add(
                        NeedLine(
                            key = "req-${r.request.id}",
                            text = r.item?.title ?: "A request",
                            light = Light.WARN,
                            supporting = "Asked by ${r.who}" + (r.item?.releaseDate?.year?.let { " · $it" } ?: ""),
                            poster = r.item?.posterUrl,
                            actions =
                                listOf(
                                    "Yes" to { requestsViewModel.answer(r, true) },
                                    "No" to { requestsViewModel.answer(r, false) },
                                ),
                        ),
                    )
                }
                if (lookYourself.isNotEmpty()) {
                    val n = lookYourself.size
                    add(
                        NeedLine(
                            key = "look-yourself",
                            text =
                                if (n == 1) {
                                    "1 request has been searching for 2 weeks"
                                } else {
                                    "$n requests have been searching for 2 weeks"
                                },
                            light = Light.WARN,
                            supporting = "Look for it yourself in Requests",
                            actions =
                                listOf(
                                    "Go" to {
                                        viewModel.navigationManager.navigateTo(
                                            com.github.damontecres.wholphin.ui.nav.Destination.Requests,
                                        )
                                    },
                                ),
                        ),
                    )
                }
            }
        }
    val bannerLight =
        when {
            header == null -> Light.UNKNOWN
            healthNeeds.any { it.light == Light.BAD } -> Light.BAD
            healthNeeds.isNotEmpty() -> Light.WARN
            else -> Light.GOOD
        }
    val bannerReason =
        healthNeeds.firstOrNull()?.let { first ->
            first.text + if (healthNeeds.size > 1) " (and ${healthNeeds.size - 1} more below)" else ""
        }
    val stuckCount = tower?.downloads.orEmpty().count { it.stuck }
    val pills =
        listOf(
            (if (header?.online == false) "Jellyfin: not answering" else "Jellyfin") to
                when {
                    header == null -> Light.UNKNOWN
                    header?.online == true -> Light.GOOD
                    else -> Light.BAD
                },
            (if (brokenApps(tower).isNotEmpty()) "Tower: ${brokenApps(tower).size} app(s) down" else "Tower") to
                when {
                    tower == null -> Light.UNKNOWN
                    brokenApps(tower).isNotEmpty() -> Light.BAD
                    else -> Light.GOOD
                },
            (if (stuckCount > 0) "Downloads: $stuckCount stuck" else "Downloads") to
                when {
                    tower == null -> Light.UNKNOWN
                    stuckCount > 0 -> Light.WARN
                    else -> Light.GOOD
                },
        )
    val ago by produceState(agoText(updatedAt), updatedAt) {
        while (true) {
            value = agoText(updatedAt)
            delay(30_000)
        }
    }
    // Benflix: Refresh also tries People and Messages again if they couldn't be read
    val refreshAll: () -> Unit = {
        data.refresh()
        data.refreshTower()
        toolsViewModel.retryFailed()
        viewModel.checkSetup(fresh = true)
    }

    val content: LazyListScope.() -> Unit = content@{
        item(key = "server-header") {
            PageHeader(
                header = header,
                sectionTitle = if (isAdmin) section.title else "Server",
                loading = loadingParts > 0,
                ago = ago,
                waitingOn = waitingOn,
                onRefresh = if (isAdmin) refreshAll else null,
            )
        }
        if (!isAdmin) {
            item { Text("Only the server's admin can see this page.", fontSize = BenflixType.lead) }
            return@content
        }
        when (section) {
            ServerSection.OVERVIEW -> {
                item(key = "banner") { ServerBanner(bannerLight, bannerReason, pills, null) }
                setupNeed?.let { need -> item(key = "setup-card") { ServerSetupCard(need) } }
                item(key = "cards") {
                    ServerOverviewCards(
                        needs = healthNeeds + requestNeeds,
                        watching = watching,
                        onOpenWatching = { frame.pick(ServerSection.NOW.key) },
                    )
                }
                if (!seerrActive) {
                    item {
                        ServerActionRow(
                            headline = "Seerr isn't connected, so requests can't show here",
                            supporting = "Connect it in Settings > Behind the scenes.",
                        )
                    }
                }
                problemReportItems(
                    reports = reports,
                    viewModel = toolsViewModel,
                    showDialog = { toolsDialog = it },
                    sectionTitle = sectionTitle,
                    onFileDetails = adminTitle::fileDetails,
                    onFixMatch = adminTitle::openFixMatch,
                )
                serverProblemItems(problems)
                careReportItems(careReport, careReportProblem, care)
                item(key = "tools-title") { SectionTitle("Tools") }
                item(key = "tool-tiles") { ServerToolTiles(selected = null, onPick = openTools) }
            }

            ServerSection.NOW -> {
                uploadMeterItem(watching)
                towerBusyItems(tower)
                watchingItems(
                    watching = watching,
                    isTv = isTv,
                    onStop = { stopping = it },
                    onPause = viewModel::pause,
                    onMessage = { messaging = it },
                )
                historyItems(history)
            }

            ServerSection.LIBRARY -> {
                libraryNumberItems(library)
                diskItems(disks)
                towerOverviewItems(tower, towerProblem) { openTools(ServerToolGroup.TOWER) }
                item(key = "library-tools") {
                    ServerActionRow(
                        headline = "Library tools",
                        supporting = "Scan for new stuff, the library checkup, posters and space are under Tools",
                        onClick = { openTools(ServerToolGroup.LIBRARY) },
                        actions = listOf("Open" to { openTools(ServerToolGroup.LIBRARY) }),
                    )
                }
            }

            ServerSection.PEOPLE -> {
                peopleItems(toolsState, history, { toolsDialog = it }, sectionTitle)
                item(key = "people-tools") {
                    ServerActionRow(
                        headline = "Messages and Benflix versions",
                        supporting = "Send a note to someone, or see who needs to update",
                        onClick = { openTools(ServerToolGroup.PEOPLE) },
                        actions = listOf("Open" to { openTools(ServerToolGroup.PEOPLE) }),
                    )
                }
            }

            ServerSection.TOOLS -> {
                item(key = "tool-tiles") { ServerToolTiles(selected = toolGroup, onPick = { toolGroup = it }) }
                when (toolGroup ?: ServerToolGroup.LIBRARY) {
                    ServerToolGroup.LIBRARY -> {
                        toolItems(
                            libraries = libraries,
                            chores = chores,
                            onScan = viewModel::scan,
                            onChore = viewModel::runChore,
                            onRestart = { askRestart = true },
                            plannedRestart = plannedRestart,
                            onCancelRestart = viewModel::cancelRestart,
                            showRest = false,
                        )
                        item(key = "fix-posters") {
                            ServerActionRow(
                                headline = "Fix posters",
                                supporting =
                                    "Missing, blurry or wrong-shaped posters, one at a time. " +
                                        "Missing ones also get filled in every night.",
                                onClick = toolsViewModel::openPosterFixer,
                            )
                        }
                        checkupItems(toolsState, toolsViewModel, { toolsDialog = it }, sectionTitle) { title, found ->
                            when {
                                tower == null -> emptyList()
                                title == "Missing episodes" -> listOf("Search" to { towerViewModel.searchMissing(found) })
                                title.startsWith("Low quality") -> listOf("Find a better copy" to { towerViewModel.searchBetter(found) })
                                else -> emptyList()
                            }
                        }
                        spaceItems(toolsState, toolsViewModel, sectionTitle)
                        careLibraryItems(carePlaytest, careTidy, careTicked, careBiggest, careProblem, care)
                    }

                    ServerToolGroup.DOWNLOADS -> {
                        towerDownloadItems(tower, towerProblem, towerViewModel, showApps = false)
                    }

                    ServerToolGroup.PEOPLE -> {
                        versionItems(
                            versions = toolsState.versions,
                            failed = toolsState.versionsFailed,
                            onLoad = toolsViewModel::loadVersions,
                            onMessage = { ids, title, prefill -> toolsDialog = ServerToolsDialog.WritePopupToMany(ids, title, prefill) },
                            sectionTitle = sectionTitle,
                        )
                        messageItems(toolsState, { toolsDialog = it }, sectionTitle)
                        kidsRatingItems()
                    }

                    ServerToolGroup.FIXES -> {
                        trackTidyItems(tracks, tracksProblem, towerViewModel)
                        speedCheckItems(speedState, speedCheck)
                        startupSpeedItem(sectionTitle)
                    }

                    ServerToolGroup.TOWER -> {
                        item(key = "setup-title") { SectionTitle("Setup command") }
                        item(key = "setup-tools") {
                            val need = setupNeed
                            if (need != null) {
                                ServerSetupCard(need)
                            } else {
                                ServerActionRow(
                                    headline = "Tower is up to date",
                                    supporting = "When an update needs the setup command, a yellow card shows here and on Overview.",
                                    leading = { Dot(Light.GOOD) },
                                )
                            }
                        }
                        towerDownloadItems(tower, towerProblem, towerViewModel, showDownloads = false)
                        toolItems(
                            libraries = libraries,
                            chores = chores,
                            onScan = viewModel::scan,
                            onChore = viewModel::runChore,
                            onRestart = { askRestart = true },
                            plannedRestart = plannedRestart,
                            onCancelRestart = viewModel::cancelRestart,
                            showScan = false,
                        )
                        homeWeatherItems()
                        bookListItems()
                    }
                }
            }
        }
    }

    val edge = if (isTv) 0.dp else 12.dp
    DoorFrame(
        state = frame,
        sections = if (isAdmin) ServerSection.entries.map { it.railItem(lights[it]) } else emptyList(),
        modifier = modifier.fillMaxSize().serverLookABackground(),
    ) { inner ->
        if (isTv) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 24.dp, end = 40.dp, top = 20.dp, bottom = 48.dp),
                modifier = inner,
                content = content,
            )
        } else {
            PhonePullToRefresh(
                refreshing = loadingParts > 0,
                onRefresh = refreshAll,
                modifier = inner,
            ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = edge, end = edge, top = 8.dp, bottom = 96.dp),
                    modifier = Modifier.fillMaxSize(),
                    content = content,
                )
            }
        }
    }

    AdminTitleDialogs(adminTitle)
    ServerToolsDialogs(
        state = toolsState,
        viewModel = toolsViewModel,
        dialog = toolsDialog,
        onDismiss = { toolsDialog = null },
        showDialog = { toolsDialog = it },
    )
    messaging?.let { w ->
        StringInputDialog(
            input =
                StringInput(
                    title = "Message to ${w.who}",
                    value = "",
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                    onSubmit = {},
                    maxLines = 3,
                ),
            onSave = {
                messaging = null
                viewModel.message(w, it)
            },
            onDismissRequest = { messaging = null },
        )
    }
    if (askRestart) {
        DialogPopup(
            showDialog = true,
            title = "Restart Jellyfin?",
            dialogItems =
                listOf(
                    5 to "Warn everyone, restart in 5 minutes",
                    1 to "Warn everyone, restart in 1 minute",
                    0 to "Restart now",
                ).map { (minutes, label) ->
                    DialogItem(
                        headlineContent = { Text(label) },
                        supportingContent =
                            if (minutes == 0) {
                                { Text("Anyone watching gets stopped for about a minute") }
                            } else {
                                { Text("A pop-up warns every screen first. You can leave this page.") }
                            },
                        onClick = {
                            askRestart = false
                            viewModel.restart(minutes)
                        },
                    )
                },
            onDismissRequest = { askRestart = false },
            dismissOnClick = false,
            waitToLoad = false,
        )
    }
    notice?.let { (title, body) ->
        ConfirmDialog(
            title = title,
            body = body,
            onCancel = viewModel::clearNotice,
            onConfirm = viewModel::clearNotice,
        )
    }
    ServerTowerDialogs(towerViewModel)
    ServerCareDialogs(care)
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

private const val MANY_PROBLEMS = 3
private const val FIRST_FOCUS_MS = 200L
private const val CHORE_CHECKS = 200

/** Benflix: the section's name big, the server's name small, how fresh the numbers are, and Refresh */
@Composable
private fun PageHeader(
    header: ServerHeader?,
    sectionTitle: String,
    loading: Boolean,
    ago: String?,
    waitingOn: Set<String>,
    onRefresh: (() -> Unit)?,
) {
    val isTv = rememberIsTv()
    val name = header?.serverName.orEmpty().ifBlank { "Benflix" }
    val version = header?.version?.takeIf { it.isNotBlank() }?.let { " · Jellyfin $it" } ?: ""
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Server · $name$version",
                fontSize = BenflixType.small,
                fontWeight = FontWeight.SemiBold,
                color = BenflixColors.Muted,
                maxLines = 1,
            )
            Text(
                text = sectionTitle,
                fontSize = BenflixType.hero,
                lineHeight = if (isTv) 42.sp else 36.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            val status =
                when {
                    loading -> "Loading new numbers…"
                    waitingOn.isNotEmpty() -> "Still checking: " + waitingOn.joinToString(", ").lowercase() + "…"
                    else -> ago
                }
            status?.let { Text(text = it, fontSize = BenflixType.small, color = BenflixColors.Muted, maxLines = 1) }
        }
        if (onRefresh != null) SmallButton("Refresh", onClick = onRefresh)
    }
}

private fun agoText(at: Long): String? {
    if (at <= 0) return null
    val minutes = ((System.currentTimeMillis() - at) / 60_000).toInt()
    return when {
        minutes < 1 -> "Updated just now"
        minutes == 1 -> "Updated 1 min ago"
        minutes < 60 -> "Updated $minutes min ago"
        minutes < 120 -> "Updated 1 hour ago"
        minutes < 60 * 24 -> "Updated ${minutes / 60} hours ago"
        else -> "Updated ${minutes / (60 * 24)} days ago"
    }
}

/** Benflix look A: a soft blue band across the top of the Server page, fading into the dark */
@Composable
private fun Modifier.serverLookABackground(): Modifier =
    this
        .background(MaterialTheme.colorScheme.background)
        .background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to Color(0xFF12406B).copy(alpha = .45f),
                .4f to Color.Transparent,
            ),
        )

@Composable
internal fun Dot(
    light: Light,
    size: androidx.compose.ui.unit.Dp = 12.dp,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    when (light) {
                        Light.GOOD -> GoodGreen
                        Light.WARN -> WarnGold
                        Light.BAD -> BadRed
                        Light.UNKNOWN -> Color.Gray
                    },
                ),
    )
}

/** Benflix: who's watching, with posters, how far in they are, their app version and a Stop button */
private fun LazyListScope.watchingItems(
    watching: List<WatchingNow>?,
    isTv: Boolean,
    onStop: (WatchingNow) -> Unit,
    onPause: (WatchingNow) -> Unit,
    onMessage: (WatchingNow) -> Unit,
) {
    item { SectionTitle(if (watching == null) "Watching now" else "Watching now (${watching.size})") }
    when {
        watching == null -> {
            item { LoadingBox("Seeing who's watching…") }
        }

        watching.isEmpty() -> {
            item { ServerActionRow(headline = "Nobody is watching right now") }
        }

        else -> {
            items(watching, key = { it.sessionId }) { w ->
                ServerActionRow(
                    headline = "${w.who} · ${w.what}" + if (w.paused) " (paused)" else "",
                    supporting =
                        listOf(
                            w.device,
                            w.app,
                            w.timeLeft.orEmpty(),
                            w.mbps?.let { "%.1f Mbps".format(it) }.orEmpty(),
                            if (w.away) "away from home" else "",
                        ).filter { it.isNotBlank() }
                            .joinToString(" · "),
                    extra = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                            w.progress?.let { ProgressLine(it) }
                            // Phones: the long "how it plays" note goes under, so it never gets cut off
                            if (!isTv) StatusChip(text = w.howItPlays, color = if (w.converting) WarnGold else GoodGreen)
                            w.why?.let { Text(text = "Why: $it", fontSize = BenflixType.small, color = WarnGold) }
                        }
                    },
                    leading = {
                        AsyncImage(
                            model = w.posterUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .width(54.dp)
                                    .height(80.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    },
                    trailing =
                        if (isTv) {
                            { StatusChip(text = w.howItPlays, color = if (w.converting) WarnGold else GoodGreen) }
                        } else {
                            null
                        },
                    onClick = { onPause(w) },
                    actions =
                        listOf(
                            (if (w.paused) "Resume" else "Pause") to { onPause(w) },
                            "Message" to { onMessage(w) },
                            "Stop" to { onStop(w) },
                        ),
                )
            }
        }
    }
}

/** Benflix: how much of the home internet all the streams use together */
private fun LazyListScope.uploadMeterItem(watching: List<WatchingNow>?) {
    val list = watching.orEmpty()
    if (list.isEmpty()) return
    item {
        val total = list.sumOf { it.mbps ?: 0.0 }
        val awayTotal = list.filter { it.away }.sumOf { it.mbps ?: 0.0 }
        val awayCount = list.count { it.away }
        ServerActionRow(
            headline = "Using about %.0f Mbps in total".format(total),
            supporting =
                if (awayCount == 0) {
                    "Everyone is at home, so your internet upload isn't used"
                } else {
                    "%d away from home, using about %.0f Mbps of your internet upload".format(awayCount, awayTotal)
                },
        )
    }
}

/** Benflix: what everyone started watching today and earlier this week */
private fun LazyListScope.historyItems(history: List<WatchedEntry>?) {
    // Benflix: "today" is the TV's own day; the log's times are in UTC
    val zone = java.time.ZoneId.systemDefault()
    val today = java.time.LocalDate.now(zone)
    val list =
        history ?: run {
            item { SectionTitle("Watched today") }
            item { LoadingBox("Reading what everyone watched…") }
            return
        }
    val (todays, earlier) =
        // The server's times already arrive in this time zone
        list.partition { it.at.toLocalDate() == today }
    item { SectionTitle("Watched today (${todays.size})") }
    if (todays.isEmpty()) item { ServerActionRow(headline = "Nothing yet today") }
    items(todays.take(HISTORY_SHOWN)) { e -> ServerActionRow(headline = e.text, supporting = e.time) }
    if (earlier.isNotEmpty()) {
        item { SectionTitle("Earlier this week (${earlier.size})") }
        items(earlier.take(HISTORY_SHOWN)) { e -> ServerActionRow(headline = e.text, supporting = e.time) }
    }
}

private const val HISTORY_SHOWN = 40

/** Benflix: Scan for new stuff, the server's chores and Restart */
private fun LazyListScope.toolItems(
    libraries: List<BaseItemDto>?,
    chores: List<ServerChore>?,
    onScan: (BaseItemDto?) -> Unit,
    onChore: (ServerChore) -> Unit,
    onRestart: () -> Unit,
    plannedRestart: Long?,
    onCancelRestart: () -> Unit,
    showScan: Boolean = true,
    showRest: Boolean = true,
) {
    if (showScan) scanItems(libraries, onScan)
    if (showRest) choreAndRestartItems(chores, onChore, onRestart, plannedRestart, onCancelRestart)
}

private fun LazyListScope.scanItems(
    libraries: List<BaseItemDto>?,
    onScan: (BaseItemDto?) -> Unit,
) {
    item { SectionTitle("Scan for new stuff") }
    item {
        ServerActionRow(
            headline = "Scan every library",
            supporting = "Makes Jellyfin look for new files now instead of waiting",
            onClick = { onScan(null) },
            actions = listOf("Scan" to { onScan(null) }),
        )
    }
    items(libraries.orEmpty(), key = { "lib-${it.id}" }) { lib ->
        ServerActionRow(
            headline = lib.name.orEmpty(),
            onClick = { onScan(lib) },
            actions = listOf("Scan" to { onScan(lib) }),
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

private fun LazyListScope.choreAndRestartItems(
    chores: List<ServerChore>?,
    onChore: (ServerChore) -> Unit,
    onRestart: () -> Unit,
    plannedRestart: Long?,
    onCancelRestart: () -> Unit,
) {
    item { SectionTitle("Server chores") }
    when {
        chores == null -> {
            item { LoadingBox("Reading the server's chores…") }
        }

        else -> {
            items(chores, key = { "chore-${it.id}" }) { c ->
                ServerActionRow(
                    headline = c.name,
                    supporting =
                        when {
                            c.running -> "Running now" + (c.progress?.let { " · $it%" } ?: "")
                            c.failed -> "Failed last time (${c.lastRun ?: "?"})" + (c.error?.let { ": $it" } ?: "")
                            c.lastRun != null -> "Last ran ${c.lastRun}"
                            else -> "Hasn't run yet"
                        },
                    extra = c.progress?.takeIf { c.running }?.let { { ProgressLine(it / 100f) } },
                    leading = { Dot(if (c.failed) Light.WARN else Light.GOOD) },
                    actions = if (c.running) emptyList() else listOf("Run now" to { onChore(c) }),
                )
            }
        }
    }
    item { SectionTitle("Restart") }
    if (plannedRestart != null) {
        item(key = "planned-restart") {
            val at =
                remember(plannedRestart) {
                    java.time.Instant
                        .ofEpochMilli(plannedRestart)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(
                            java.time.format.DateTimeFormatter
                                .ofPattern("h:mm a"),
                        )
                }
            ServerActionRow(
                headline = "Restart planned for $at",
                supporting = "Everyone was warned. Call it off if you changed your mind.",
                leading = { Dot(Light.WARN) },
                onClick = onCancelRestart,
                actions = listOf("Call it off" to onCancelRestart),
            )
        }
    }
    item {
        ServerActionRow(
            headline = "Restart Jellyfin",
            supporting = "Fixes most hiccups. You can warn everyone first.",
            onClick = onRestart,
            actions = listOf("Restart" to onRestart),
        )
    }
}

@Composable
internal fun ProgressLine(share: Float) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(50)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(share.coerceIn(0.02f, 1f))
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
        )
    }
}

private fun LazyListScope.libraryNumberItems(library: LibraryNumbers?) {
    item { SectionTitle("Library") }
    item {
        val lib = library
        val isTv = rememberIsTv()
        val numbers =
            listOf(
                (lib?.let { "${it.addedThisWeek}" }) to "added this week",
                (lib?.let { "%,d".format(it.movies) }) to "movies",
                (lib?.let { "%,d".format(it.shows) }) to "shows",
                (lib?.let { "%,d".format(it.episodes) }) to "episodes",
            )
        if (isTv) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                numbers.forEach { (value, label) -> BigNumber(value, label, Modifier.weight(1f)) }
            }
        } else {
            // Phones: two by two, so the numbers never get squashed
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                numbers.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        pair.forEach { (value, label) -> BigNumber(value, label, Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.diskItems(disks: List<ServerDisk>?) {
    when {
        disks == null -> {
            item { SectionTitle("Disk space") }
            item { LoadingBox("Checking the disks… (sleeping disks can take a few seconds to wake up)") }
        }

        disks.isNotEmpty() -> {
            item { SectionTitle("Disk space") }
            items(disks) { disk -> DiskBar(disk) }
        }
    }
}

/** Benflix: problems from the server's own log, in plain English, with repeats counted */
private fun LazyListScope.serverProblemItems(problems: List<ServerProblem>?) {
    item {
        SectionTitle(
            if (problems == null) "From the server, last day" else "From the server, last day (${problems.sumOf { it.count }})",
        )
    }
    when {
        problems == null -> {
            item { LoadingBox("Looking for problems…") }
        }

        problems.isEmpty() -> {
            item { ServerActionRow(headline = "No problems. All good!") }
        }

        else -> {
            items(problems) { p ->
                ServerActionRow(
                    headline = p.text + if (p.count > 1) "  ×${p.count}" else "",
                    supporting = listOfNotNull(p.time, p.tip).joinToString(" · "),
                )
            }
        }
    }
}

/** Benflix: a grey box with a gently pulsing line, shown where a section is still loading */
@Composable
internal fun LoadingBox(text: String) {
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
                .padding(vertical = 3.dp)
                .background(ServerCardColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = text,
            fontSize = BenflixType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { this.alpha = alpha },
        )
    }
}

private val GoodGreen = Color(0xFF5BD18B)
private val WarnGold = Color(0xFFE8D44D)
private val BadRed = Color(0xFFFF7A7A)

@Composable
internal fun SectionTitle(text: String) {
    // Benflix: a small grey heading over each group of cards, like the Settings pages
    Text(
        text = text.uppercase(),
        fontSize = BenflixType.small,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 4.dp),
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
        fontSize = BenflixType.small,
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
                .background(ServerCardColor, RoundedCornerShape(10.dp))
                .padding(12.dp),
    ) {
        // Benflix: a dash while the number is still loading
        Text(
            text = value ?: "–",
            fontWeight = FontWeight.Bold,
            fontSize = BenflixType.rowName,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
        )
        Text(text = label, fontSize = BenflixType.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                .background(ServerCardColor, RoundedCornerShape(10.dp))
                .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = disk.name,
                fontSize = BenflixType.body,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${sizeText(disk.freeBytes)} free of ${sizeText(disk.totalBytes)}",
                fontSize = BenflixType.small,
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
                fontSize = BenflixType.small,
                color = WarnGold,
            )
        }
    }
}

private fun sizeText(bytes: Long): String {
    val tb = bytes / 1_000_000_000_000.0
    return if (tb >= 1) "%.1f TB".format(tb) else "%.0f GB".format(bytes / 1_000_000_000.0)
}
