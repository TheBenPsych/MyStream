package com.github.damontecres.wholphin.ui.main

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
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
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixPlayFilled
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.door.DOOR_HOME
import com.github.damontecres.wholphin.ui.door.DoorFrame
import com.github.damontecres.wholphin.ui.door.DoorFrameState
import com.github.damontecres.wholphin.ui.door.DoorSection
import com.github.damontecres.wholphin.ui.nav.LocalTopBarOverlap
import com.github.damontecres.wholphin.ui.preferences.StringInput
import com.github.damontecres.wholphin.ui.preferences.StringInputDialog
import com.github.damontecres.wholphin.ui.preferences.glowfinDimmedPage
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
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
    HEALTH("Health", "Tower's parts, the black box before a crash, and the changes diary", BenflixIcon.HEART.vector),
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
                HEALTH -> BenflixIcon.HEART
                TOOLS -> BenflixIcon.WRENCH
            }

    internal fun railItem(light: Light?): DoorSection =
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
                        "Glowfin is restarting in $minutes minute" + (if (minutes == 1) "" else "s") +
                            ". It'll be back a minute later.",
                    )
                    actions.planRestart(minutes) { problem ->
                        if (problem != null) _notice.value = "Couldn't restart Jellyfin" to problem
                    }
                    _notice.value =
                        "Restart planned" to
                        "Everyone was warned. Jellyfin restarts in $minutes minute" +
                        (if (minutes == 1) "" else "s") +
                        ", even if you leave this page. Just keep Glowfin open on this device until then. " +
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
 * Redesign (Ben 2026-11-28): the sections are tabs along the top on every screen. Overview opens with one big
 * status line and five glance tiles; Tools is a row of tool tiles per group, and each tool opens on its own page.
 * Phones: pull down to refresh.
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
    cardsModel: TowerCardsViewModel = hiltViewModel(),
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
                start = (jump.first ?: if (jump.second != null) ServerSection.TOOLS else ServerSection.OVERVIEW).key,
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
    // Which Tools row to show first (from Settings' "Find a setting", or a "Go" button)
    var toolGroup by rememberSaveable { mutableStateOf(jump.second) }
    // Redesign (Ben 2026-11-28): one tool open on its own page, or null for the tab's tiles
    var openToolName by rememberSaveable { mutableStateOf<String?>(null) }
    val shownTool = openToolName?.let { n -> ServerTool.entries.firstOrNull { it.name == n } }?.takeIf { it.section == section }
    LaunchedEffect(section) { if (shownTool == null) openToolName = null }
    LaunchedEffect(isAdmin) {
        if (isAdmin) {
            viewModel.checkSetup()
            // Overview's pills and Needs you card need Tower's answer too
            data.refreshTower(fresh = false)
        }
    }
    LaunchedEffect(section, shownTool?.group, isAdmin) {
        if (!isAdmin) return@LaunchedEffect
        when (section) {
            ServerSection.OVERVIEW -> {
                requestsViewModel.loadRequests(mineOnly = false)
                care.loadReport()
                care.loadSignins()
            }

            ServerSection.PEOPLE -> {
                toolsViewModel.loadPeople()
                data.loadHistory()
            }

            ServerSection.NOW -> {
                data.loadHistory()
                care.loadActivity()
            }

            ServerSection.TOOLS -> {
                when (shownTool?.group) {
                    // The tiles: Tower's answer gives the Downloads, apps, drives and setup tiles their status
                    null -> {
                        data.refreshTower(fresh = false)
                    }

                    ServerToolGroup.LIBRARY -> {
                        viewModel.loadTools()
                        care.loadLibrary()
                    }

                    ServerToolGroup.DOWNLOADS -> {
                        data.refreshTower()
                    }

                    ServerToolGroup.PEOPLE -> {
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

            ServerSection.HEALTH -> {
                // Its Tower cards load themselves
                data.refreshTower(fresh = false)
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
    val activity by care.activity.collectAsState()
    val activityProblem by care.activityProblem.collectAsState()
    val signins by care.signins.collectAsState()
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
                        tower?.drivesBad.orEmpty().isNotEmpty() -> Light.WARN
                        disks.orEmpty().any { it.nearlyFull } -> Light.WARN
                        tower?.downloads.orEmpty().any { it.stuck } -> Light.WARN
                        else -> Light.GOOD
                    },
                ServerSection.HEALTH to
                    when (tower?.healthLevel) {
                        null -> Light.UNKNOWN
                        "bad" -> Light.BAD
                        "warn" -> Light.WARN
                        else -> Light.GOOD
                    },
            )
        }
    val sectionTitle: @Composable (String) -> Unit = { SectionTitle(it) }
    val openTools: (ServerToolGroup) -> Unit = { group ->
        toolGroup = group
        openToolName = null
        frame.pick(ServerSection.TOOLS.key)
    }
    val openTool: (ServerTool) -> Unit = { tool ->
        if (tool.section != section) frame.pick(tool.section.key)
        openToolName = tool.name
    }
    // Redesign 2 (approved sv2-glance): what needs you, with its buttons right on the card
    val pendingStatus = com.github.damontecres.wholphin.data.model.RequestStatus.PENDING
    val healthNeeds =
        remember(header, disks, problems, reports, tower, setupNeed) {
            buildList {
                if (header?.online == false) {
                    add(NeedLine("offline", "Glowfin can't reach Jellyfin. Is Tower on?", Light.BAD))
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
                tower?.takeIf { it.healthLevel == "warn" || it.healthLevel == "bad" }?.let { t ->
                    add(
                        NeedLine(
                            key = "health",
                            text = t.healthHeadline ?: "Tower's health needs a look",
                            light = if (t.healthLevel == "bad") Light.BAD else Light.WARN,
                            supporting = "Server > Health has the details",
                            actions = listOf("Go" to { frame.pick(ServerSection.HEALTH.key) }),
                        ),
                    )
                }
                tower?.drivesBad.orEmpty().forEach { d ->
                    add(
                        NeedLine(
                            key = "drive-${d.where}-${d.name}",
                            text = "Drive ${d.name}: ${d.words}",
                            light = driveLight(d),
                            supporting = d.where.ifBlank { null },
                            actions = listOf("Go" to { openTool(ServerTool.DRIVES) }),
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
    // Idea Box 25 "New sign-in alert"
    val signinNeeds =
        remember(signins) {
            signins.map { s ->
                NeedLine(
                    key = "signin-${s.id}",
                    text = "New sign-in on ${s.user}'s account",
                    light = Light.WARN,
                    supporting =
                        listOfNotNull(
                            s.device,
                            when (s.away) {
                                true -> "away from home"
                                false -> "at home"
                                null -> null
                            },
                            s.whenWords(),
                        ).joinToString(" · "),
                    actions =
                        listOf(
                            "That's fine" to { care.answerSignin(s, signOut = false) },
                            "Sign it out" to { care.answerSignin(s, signOut = true) },
                        ),
                )
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
                            supporting =
                                "Asked by ${r.who}" + (
                                    r.item
                                        ?.releaseDate
                                        ?.year
                                        ?.let { " · $it" } ?: ""
                                ),
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

    // Redesign (Ben 2026-11-28): each tile's short status, from what the page already knows
    val towerCards by cardsModel.cards.collectAsState()
    val toolStatus: (ServerTool) -> ToolStatus = { tool ->
        val t = tower
        when (tool) {
            ServerTool.DOWNLOADS -> {
                if (t == null) {
                    ToolStatus(tool.line, null)
                } else {
                    val going = t.downloads.size
                    val stuck = t.downloads.count { it.stuck }
                    when {
                        stuck > 0 -> ToolStatus("$going going · $stuck stuck", Light.WARN)
                        going == 0 -> ToolStatus("Nothing downloading", Light.GOOD)
                        else -> ToolStatus("$going going", Light.GOOD)
                    }
                }
            }

            ServerTool.SETUP -> {
                when {
                    setupNeed != null -> ToolStatus("Needs the setup command", Light.WARN)
                    t != null -> ToolStatus("Up to date", Light.GOOD)
                    else -> ToolStatus(tool.line, null)
                }
            }

            ServerTool.APPS -> {
                val broken = brokenApps(t)
                when {
                    t?.apps == null -> {
                        ToolStatus(tool.line, null)
                    }

                    broken.isNotEmpty() -> {
                        ToolStatus(
                            if (broken.size ==
                                1
                            ) {
                                "${broken[0].nice} isn't running"
                            } else {
                                "${broken.size} not running"
                            },
                            Light.BAD,
                        )
                    }

                    else -> {
                        ToolStatus("All running", Light.GOOD)
                    }
                }
            }

            ServerTool.DRIVES -> {
                when {
                    t == null -> ToolStatus(tool.line, null)
                    t.drivesBad.isNotEmpty() -> ToolStatus("${t.drivesBad.size} with warning signs", Light.WARN)
                    else -> ToolStatus("No warning signs", Light.GOOD)
                }
            }

            ServerTool.CHORES -> {
                val n = watching?.size
                when {
                    plannedRestart != null -> ToolStatus("A restart is planned", Light.WARN)
                    n == null -> ToolStatus(tool.line, null)
                    n == 0 -> ToolStatus("Nobody is watching", null)
                    else -> ToolStatus("$n watching now", null)
                }
            }

            else -> {
                tool.card?.let { towerCards[it].toolStatus(tool.line) } ?: ToolStatus(tool.line, null)
            }
        }
    }
    val pendingCount = requests.orEmpty().count { it.status == pendingStatus }
    val glance =
        run {
            val t = tower
            val broken = brokenApps(t)
            val used = disks.orEmpty().sumOf { it.usedBytes }
            val free = disks.orEmpty().sumOf { it.freeBytes }
            val going = t?.downloads?.size
            val stuck = t?.downloads.orEmpty().count { it.stuck }
            listOf(
                GlanceTile(
                    label = "Tower",
                    icon = BenflixIcon.SERVER,
                    value =
                        when {
                            header?.online == false -> "Off?"
                            t == null -> "…"
                            broken.isNotEmpty() || t.healthLevel == "bad" -> "Problem"
                            t.healthLevel == "warn" -> "Check"
                            else -> "OK"
                        },
                    valueLight =
                        when {
                            header?.online == false -> Light.BAD
                            t == null -> null
                            broken.isNotEmpty() || t.healthLevel == "bad" -> Light.BAD
                            t.healthLevel == "warn" -> Light.WARN
                            else -> Light.GOOD
                        },
                    line =
                        when {
                            broken.isNotEmpty() -> "${broken.size} app(s) down"
                            t?.cpu != null -> "Processor ${t.cpu}% busy"
                            else -> "Health and parts"
                        },
                    onClick = { frame.pick(ServerSection.HEALTH.key) },
                ),
                GlanceTile(
                    label = "Space",
                    icon = BenflixIcon.STATS,
                    value = if (used + free > 0) "${(used * 100 / (used + free)).toInt()}%" else "…",
                    valueLight = if (disks.orEmpty().any { it.nearlyFull }) Light.WARN else null,
                    line = if (used + free > 0) "${sizeText(free)} free" else "Disk space",
                    onClick = { frame.pick(ServerSection.LIBRARY.key) },
                ),
                GlanceTile(
                    label = "Watching",
                    icon = BenflixIcon.PLAY,
                    value = watching?.size?.toString() ?: "…",
                    line =
                        watching
                            ?.takeIf { it.isNotEmpty() }
                            ?.map { it.who }
                            ?.distinct()
                            ?.joinToString(", ") ?: "Nobody right now",
                    onClick = { frame.pick(ServerSection.NOW.key) },
                ),
                GlanceTile(
                    label = "Downloads",
                    icon = BenflixIcon.DOWNLOAD,
                    value = going?.toString() ?: "…",
                    line =
                        if (stuck > 0) {
                            "$stuck stuck"
                        } else if (going == 0) {
                            "Nothing going"
                        } else {
                            "None stuck"
                        },
                    lineLight = if (stuck > 0) Light.WARN else null,
                    onClick = { openTool(ServerTool.DOWNLOADS) },
                ),
                GlanceTile(
                    label = "Requests",
                    icon = BenflixIcon.REQUESTS,
                    value = if (seerrActive) pendingCount.toString() else "–",
                    line =
                        when {
                            !seerrActive -> "Seerr isn't connected"
                            pendingCount == 0 -> "None waiting"
                            else -> "Waiting for you"
                        },
                    lineLight = if (seerrActive && pendingCount > 0) Light.WARN else null,
                    onClick = {
                        viewModel.navigationManager.navigateTo(com.github.damontecres.wholphin.ui.nav.Destination.Requests)
                    },
                ),
            )
        }
    val statusWords =
        when {
            loadingParts > 0 -> "Loading new numbers…"
            waitingOn.isNotEmpty() -> "Still checking: " + waitingOn.joinToString(", ").lowercase() + "…"
            else -> ago
        }
    // Each tile's focus, so Back from a tool lands on the tile you opened
    val tileFocus = remember { mutableMapOf<ServerTool, FocusRequester>() }
    val focusOf: (ServerTool) -> FocusRequester = { tileFocus.getOrPut(it) { FocusRequester() } }
    val rowStates = remember { mutableMapOf<ServerToolGroup, LazyListState>() }
    val rowState: (ServerToolGroup) -> LazyListState = { rowStates.getOrPut(it) { LazyListState() } }
    val tabStates = remember { ServerSection.entries.associateWith { LazyListState() } }
    val toolListState = remember(shownTool) { LazyListState() }
    var lastTool by remember { mutableStateOf<ServerTool?>(null) }
    val closeTool: () -> Unit = {
        lastTool = shownTool
        openToolName = null
    }
    LaunchedEffect(shownTool) {
        if (!isTv) return@LaunchedEffect
        delay(TOOL_FOCUS_MS)
        if (shownTool != null) {
            frame.content.tryRequestFocus()
        } else {
            lastTool?.takeIf { it.section == section }?.let { focusOf(it).tryRequestFocus() }
        }
    }
    // A "Go" (or Settings' "Find a setting") on a Tools row: show that row first
    LaunchedEffect(section, toolGroup) {
        val group = toolGroup ?: return@LaunchedEffect
        if (section != ServerSection.TOOLS || shownTool != null) return@LaunchedEffect
        tabStates.getValue(ServerSection.TOOLS).scrollToItem(1 + ServerTool.rows.indexOf(group).coerceAtLeast(0) * 2)
        toolGroup = null
        if (isTv) {
            delay(TOOL_FOCUS_MS)
            ServerTool.inGroup(group).firstOrNull()?.let { focusOf(it).tryRequestFocus() }
        }
    }

    val toolPage: LazyListScope.(ServerTool) -> Unit = { tool ->
        item(key = "tool-header") { ToolPageHeader(tool, onBack = closeTool) }
        when (tool) {
            ServerTool.SCAN -> {
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
            }

            ServerTool.CHECKUP -> {
                checkupItems(toolsState, toolsViewModel, { toolsDialog = it }, sectionTitle) { title, found ->
                    when {
                        tower == null -> emptyList()
                        title == "Missing episodes" -> listOf("Search" to { towerViewModel.searchMissing(found) })
                        title.startsWith("Low quality") -> listOf("Find a better copy" to { towerViewModel.searchBetter(found) })
                        else -> emptyList()
                    }
                }
            }

            ServerTool.POSTERS -> {
                item(key = "fix-posters") {
                    ServerActionRow(
                        headline = "Fix posters",
                        supporting =
                            "Missing, blurry or wrong-shaped posters, one at a time. " +
                                "Missing ones also get filled in every night.",
                        onClick = toolsViewModel::openPosterFixer,
                        actions = listOf("Start" to { toolsViewModel.openPosterFixer() }),
                    )
                }
            }

            ServerTool.SPACE -> {
                spaceItems(toolsState, toolsViewModel, sectionTitle)
            }

            ServerTool.TIDY -> {
                careLibraryItems(carePlaytest, careTidy, careTicked, careBiggest, careProblem, care)
            }

            ServerTool.DOWNLOADS -> {
                towerDownloadItems(tower, towerProblem, towerViewModel, showApps = false)
            }

            ServerTool.TRACKS -> {
                trackTidyItems(tracks, tracksProblem, towerViewModel)
            }

            ServerTool.SPEED -> {
                speedCheckItems(speedState, speedCheck)
            }

            ServerTool.STARTUP -> {
                startupSpeedItem(sectionTitle)
            }

            ServerTool.GAMES -> {
                gameProblemItems()
            }

            ServerTool.VERSIONS -> {
                versionItems(
                    versions = toolsState.versions,
                    failed = toolsState.versionsFailed,
                    onLoad = toolsViewModel::loadVersions,
                    onMessage = { ids, title, prefill -> toolsDialog = ServerToolsDialog.WritePopupToMany(ids, title, prefill) },
                    sectionTitle = sectionTitle,
                )
            }

            ServerTool.MESSAGES -> {
                messageItems(toolsState, { toolsDialog = it }, sectionTitle)
            }

            ServerTool.KIDS_RATINGS -> {
                kidsRatingItems()
            }

            ServerTool.KIDS_GAMES -> {
                kidsGamesItems()
            }

            ServerTool.SETUP -> {
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
            }

            ServerTool.APPS -> {
                towerDownloadItems(tower, towerProblem, towerViewModel, showDownloads = false)
            }

            ServerTool.CHORES -> {
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
            }

            ServerTool.DRIVES -> {
                driveHealthItems()
            }

            ServerTool.BOOKS -> {
                bookListItems()
            }

            else -> {
                tool.card?.let { towerCardItems(it, tool.title) }
            }
        }
    }

    val content: LazyListScope.() -> Unit = content@{
        if (!isAdmin) {
            item(key = "server-header") {
                PageHeader(
                    header = header,
                    sectionTitle = "Server",
                    loading = loadingParts > 0,
                    ago = ago,
                    waitingOn = waitingOn,
                    onRefresh = null,
                )
            }
            item { Text("Only the server's admin can see this page.", fontSize = BenflixType.lead) }
            return@content
        }
        shownTool?.let { tool ->
            toolPage(tool)
            return@content
        }
        item(key = "server-top") {
            if (section == ServerSection.OVERVIEW) {
                ServerStatusLine(bannerLight, bannerReason, statusWords, refreshAll)
            } else {
                ServerTabLine(section.hint, statusWords, refreshAll)
            }
        }
        when (section) {
            ServerSection.OVERVIEW -> {
                item(key = "glance") { ServerGlanceTiles(glance) }
                setupNeed?.let { need -> item(key = "setup-card") { ServerSetupCard(need) } }
                needsYouItems(healthNeeds + signinNeeds + requestNeeds)
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
                towerCardItems("try", "Try these on a real screen", max = 4, hideWhenEmpty = true)
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
                activityItems(activity, activityProblem)
            }

            ServerSection.LIBRARY -> {
                libraryNumberItems(library)
                diskItems(disks)
                towerOverviewItems(tower, towerProblem) { openTool(ServerTool.APPS) }
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
                        headline = "Messages and Glowfin versions",
                        supporting = "Send a note to someone, or see who needs to update",
                        onClick = { openTools(ServerToolGroup.PEOPLE) },
                        actions = listOf("Open" to { openTools(ServerToolGroup.PEOPLE) }),
                    )
                }
            }

            ServerSection.HEALTH -> {
                // Ben's picks, 2026-11-22 (benflix_health.py on Tower): the six Health cards, each opens on its own
                item(key = "health-grid") { HealthCardGrid(toolStatus, focusOf, openTool) }
            }

            ServerSection.TOOLS -> {
                toolRowItems(toolStatus, rowState, focusOf, openTool)
            }
        }
    }

    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp || config.smallestScreenWidthDp >= WIDE_SMALLEST_DP
    val edge = if (isTv) 0.dp else 12.dp
    DoorFrame(
        state = frame,
        sections = if (isAdmin) ServerSection.entries.map { it.railItem(lights[it]) } else emptyList(),
        // Redesign (Ben 2026-11-28): the places are tabs along the top (like Movies), so the page gets the whole width
        modifier = if (isTv) modifier.fillMaxSize().glowfinDimmedPage() else modifier.fillMaxSize().serverLookABackground(),
        ownRail = true,
        tabs = true,
        tabsInHeader = true,
    ) { inner ->
        // Back closes an open tool first (back to its tile), before the tabs' own Back
        BackHandler(enabled = shownTool != null, onBack = closeTool)
        val top = if (wide) LocalTopBarOverlap.current else 0.dp
        val listState = if (shownTool != null) toolListState else tabStates.getValue(section)
        if (isTv) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = BenflixSpace.tvEdge, end = 28.dp, top = top + 8.dp, bottom = 48.dp),
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
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = edge, end = edge, top = top + 8.dp, bottom = 96.dp),
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
private const val TOOL_FOCUS_MS = 120L
private const val WIDE_SMALLEST_DP = 600
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
    val name = header?.serverName.orEmpty().ifBlank { "Glowfin" }
    val version = header?.version?.takeIf { it.isNotBlank() }?.let { " · Jellyfin $it" } ?: ""
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Server · $name$version",
                fontSize = BenflixType.small,
                fontWeight = FontWeight.SemiBold,
                color = BenflixColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = sectionTitle,
                fontSize = BenflixType.hero,
                lineHeight = if (isTv) 42.sp else 36.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val status =
                when {
                    loading -> "Loading new numbers…"
                    waitingOn.isNotEmpty() -> "Still checking: " + waitingOn.joinToString(", ").lowercase() + "…"
                    else -> ago
                }
            status?.let {
                Text(
                    text = it,
                    fontSize = BenflixType.small,
                    color = BenflixColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
private fun Modifier.serverLookABackground(): Modifier = this.background(com.github.damontecres.wholphin.ui.theme.Glowfin.Ink)

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
                overflow = TextOverflow.Ellipsis,
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
