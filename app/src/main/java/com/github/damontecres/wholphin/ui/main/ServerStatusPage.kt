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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.ui.touch.PhonePullToRefresh
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.model.api.PlaystateCommand
import timber.log.Timber
import javax.inject.Inject

/** Benflix: the parts of the Server page, like the menu in Jellyfin's Dashboard */
enum class ServerSection(
    val title: String,
    val hint: String,
    val icon: ImageVector,
) {
    OVERVIEW("Overview", "How the server is doing, and anything that needs you", Icons.Default.Home),
    NOW("Right now", "Who's watching, and how hard the server works for it", Icons.Default.PlayArrow),
    LIBRARY("Library", "Movie and show numbers, disk space and the library checkup", Icons.AutoMirrored.Filled.List),
    PEOPLE("People", "Accounts for family and friends", Icons.Default.Person),
    PROBLEMS("Problems", "Problems people reported and problems from the server", Icons.Default.Warning),
    MESSAGES("Messages", "Notes on Home, the welcome note and pop-ups", Icons.Default.Edit),
}

/** Benflix: green, gold or red for the traffic lights */
private enum class Light { GOOD, WARN, BAD, UNKNOWN }

/** Benflix: one thing on the "Needs you" card */
private data class NeedsYou(
    val text: String,
    val light: Light,
    val section: ServerSection,
)

@HiltViewModel
class ServerStatusViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        val serverRepository: ServerRepository,
        val data: ServerDashboardData,
    ) : ViewModel() {
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
) {
    val isTv = rememberIsTv()
    val data = viewModel.data
    val userDto by viewModel.serverRepository.currentUserDtoFlow.collectAsState(null)
    val isAdmin = userDto?.policy?.isAdministrator == true
    LaunchedEffect(isAdmin) { if (isAdmin) data.preload() }
    // "Watching now" only keeps checking while this page is on screen
    LifecycleResumeEffect(isAdmin) {
        if (isAdmin) data.startWatching()
        onPauseOrDispose { data.stopWatching() }
    }
    var section by rememberSaveable { mutableStateOf(ServerSection.OVERVIEW) }
    LaunchedEffect(section, isAdmin) {
        if (!isAdmin) return@LaunchedEffect
        when (section) {
            ServerSection.PEOPLE -> {
                toolsViewModel.loadPeople()
            }

            ServerSection.MESSAGES -> {
                toolsViewModel.loadNotes()
            }

            else -> {}
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
    var stopping by remember { mutableStateOf<WatchingNow?>(null) }

    val lights =
        remember(header, disks, problems, reports, watching) {
            mapOf(
                ServerSection.OVERVIEW to
                    when {
                        header == null -> Light.UNKNOWN
                        header?.online == false -> Light.BAD
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
                        disks == null -> Light.UNKNOWN
                        disks.orEmpty().any { it.nearlyFull } -> Light.WARN
                        else -> Light.GOOD
                    },
                ServerSection.PROBLEMS to
                    when {
                        problems == null && reports == null -> Light.UNKNOWN
                        reports.orEmpty().isNotEmpty() -> Light.WARN
                        problems.orEmpty().sumOf { it.count } >= MANY_PROBLEMS -> Light.WARN
                        else -> Light.GOOD
                    },
            )
        }
    val needsYou =
        remember(header, disks, problems, reports) {
            buildList {
                if (header?.online == false) {
                    add(NeedsYou("Benflix can't reach the server. Is Tower on?", Light.BAD, ServerSection.OVERVIEW))
                }
                disks.orEmpty().filter { it.nearlyFull }.forEach {
                    add(NeedsYou("The disk with ${it.name} is ${(it.usedShare * 100).toInt()}% full", Light.WARN, ServerSection.LIBRARY))
                }
                reports.orEmpty().size.takeIf { it > 0 }?.let {
                    add(
                        NeedsYou(
                            if (it == 1) "1 problem reported by family" else "$it problems reported by family",
                            Light.WARN,
                            ServerSection.PROBLEMS,
                        ),
                    )
                }
                problems.orEmpty().sumOf { it.count }.takeIf { it >= MANY_PROBLEMS }?.let {
                    add(NeedsYou("$it problems on the server in the last day", Light.WARN, ServerSection.PROBLEMS))
                }
            }
        }

    val sectionTitle: @Composable (String) -> Unit = { SectionTitle(it) }
    val content: LazyListScope.() -> Unit = content@{
        if (!isAdmin) {
            item { Text("Only the server's admin can see this page.", fontSize = 18.sp) }
            return@content
        }
        when (section) {
            ServerSection.OVERVIEW -> {
                item {
                    GlanceRow(
                        lights = lights,
                        watchingCount = watching?.size,
                        onClick = { section = it },
                    )
                }
                item { SectionTitle("Needs you") }
                if (needsYou.isEmpty()) {
                    item { ServerActionRow(headline = "All good! Nothing needs you right now.") }
                } else {
                    items(needsYou) { need ->
                        ServerActionRow(
                            headline = need.text,
                            onClick = { section = need.section },
                            leading = { Dot(need.light) },
                            actions = listOf("Go" to { section = need.section }),
                        )
                    }
                }
                item { SectionTitle("Watching now") }
                item {
                    val list = watching
                    ServerActionRow(
                        headline =
                            when {
                                list == null -> "Checking…"
                                list.isEmpty() -> "Nobody is watching right now"
                                list.size == 1 -> "1 person is watching: ${list.first().who}"
                                else -> "${list.size} people are watching: " + list.joinToString(", ") { it.who }
                            },
                        onClick = { section = ServerSection.NOW },
                    )
                }
                libraryNumberItems(library)
                diskItems(disks)
                startupSpeedItem(sectionTitle)
            }

            ServerSection.NOW -> {
                watchingItems(watching, isTv) { stopping = it }
            }

            ServerSection.LIBRARY -> {
                libraryNumberItems(library)
                diskItems(disks)
                checkupItems(toolsState, toolsViewModel, { toolsDialog = it }, sectionTitle)
            }

            ServerSection.PEOPLE -> {
                peopleItems(toolsState, { toolsDialog = it }, sectionTitle)
            }

            ServerSection.PROBLEMS -> {
                problemReportItems(reports, toolsViewModel, { toolsDialog = it }, sectionTitle)
                serverProblemItems(problems)
            }

            ServerSection.MESSAGES -> {
                messageItems(toolsState, { toolsDialog = it }, sectionTitle)
            }
        }
    }

    val headerBar: @Composable () -> Unit = {
        PageHeader(
            header = header,
            isAdmin = isAdmin,
            loading = loadingParts > 0,
            updatedAt = updatedAt,
            onRefresh = data::refresh,
        )
    }

    if (isTv) {
        val firstFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            modifier =
                modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(start = 32.dp, end = 32.dp, top = 20.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier =
                    Modifier
                        .width(300.dp)
                        .fillMaxHeight()
                        .focusRestorer(firstFocus)
                        .verticalScroll(rememberScrollState()),
            ) {
                headerBar()
                Spacer(Modifier.height(8.dp))
                if (isAdmin) {
                    ServerSection.entries.forEach { s ->
                        ListItem(
                            selected = s == section,
                            onClick = { section = s },
                            headlineContent = {
                                Text(
                                    text = s.title,
                                    fontSize = 18.sp,
                                    fontWeight = if (s == section) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                )
                            },
                            leadingContent = { Icon(s.icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
                            trailingContent = lights[s]?.let { { Dot(it) } },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { if (it.isFocused) section = s }
                                    .then(if (s == section) Modifier.focusRequester(firstFocus) else Modifier),
                        )
                    }
                    Text(
                        text = section.hint,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 12.dp, end = 8.dp),
                    )
                    ListItem(
                        selected = false,
                        onClick = data::refresh,
                        headlineContent = { Text("Refresh", fontSize = 16.sp) },
                        leadingContent = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(22.dp)) },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 48.dp),
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                content = content,
            )
        }
    } else {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
        ) {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) { headerBar() }
            if (isAdmin) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    items(ServerSection.entries.toList()) { s ->
                        SectionChip(section = s, selected = s == section, light = lights[s]) { section = s }
                    }
                }
            }
            PhonePullToRefresh(
                refreshing = loadingParts > 0,
                onRefresh = data::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
                    modifier = Modifier.fillMaxSize(),
                    content = content,
                )
            }
        }
    }

    ServerToolsDialogs(
        state = toolsState,
        viewModel = toolsViewModel,
        dialog = toolsDialog,
        onDismiss = { toolsDialog = null },
        showDialog = { toolsDialog = it },
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

private const val MANY_PROBLEMS = 3

/** Benflix: the server's name, whether it answers, and how fresh the numbers are */
@Composable
private fun PageHeader(
    header: ServerHeader?,
    isAdmin: Boolean,
    loading: Boolean,
    updatedAt: Long,
    onRefresh: () -> Unit,
) {
    val isTv = rememberIsTv()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = (header?.serverName.orEmpty().ifBlank { "SERVER" }).uppercase(),
                fontFamily = BenflixTitleFont,
                fontSize = if (isTv) 40.sp else 36.sp,
                letterSpacing = 1.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (!isTv && isAdmin) {
                Spacer(Modifier.weight(1f))
                // Phones also get a button, for people who don't know about pulling down
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onRefresh)
                            .padding(8.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (header == null) {
                StatusChip(text = "Checking…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                StatusChip(
                    text = if (header.online) "Online · Jellyfin ${header.version}" else "Can't reach the server",
                    color = if (header.online) GoodGreen else BadRed,
                )
            }
        }
        if (isAdmin) {
            val ago by produceState(agoText(updatedAt), updatedAt) {
                while (true) {
                    value = agoText(updatedAt)
                    delay(30_000)
                }
            }
            AnimatedVisibility(visible = loading, enter = fadeIn(), exit = fadeOut()) {
                StatusChip(text = "Loading new stats…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!loading && ago != null) {
                Text(text = ago!!, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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

/** Benflix: the traffic lights at the top of Overview. Each one opens its section. */
@Composable
private fun GlanceRow(
    lights: Map<ServerSection, Light>,
    watchingCount: Int?,
    onClick: (ServerSection) -> Unit,
) {
    val labels =
        listOf(
            ServerSection.OVERVIEW to "Server",
            ServerSection.NOW to (watchingCount?.let { "$it watching" } ?: "Watching"),
            ServerSection.LIBRARY to "Disks",
            ServerSection.PROBLEMS to "Problems",
        )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items(labels) { (s, label) ->
            val light = lights[s] ?: Light.UNKNOWN
            ListItem(
                selected = false,
                onClick = { onClick(s) },
                headlineContent = { Text(label, fontSize = 15.sp, maxLines = 1) },
                leadingContent = { Dot(light) },
                modifier = Modifier.width(170.dp),
            )
        }
    }
}

/** Benflix: a section tab on phones */
@Composable
private fun SectionChip(
    section: ServerSection,
    selected: Boolean,
    light: Light?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                ).clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = section.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
        )
        if (light != null && light != Light.GOOD && light != Light.UNKNOWN) Dot(light, 8.dp)
    }
}

@Composable
private fun Dot(
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
                        listOf(w.device, w.app, w.timeLeft.orEmpty())
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                    extra = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                            w.progress?.let { ProgressLine(it) }
                            // Phones: the long "how it plays" note goes under, so it never gets cut off
                            if (!isTv) StatusChip(text = w.howItPlays, color = if (w.converting) WarnGold else GoodGreen)
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
                    onClick = { onStop(w) },
                    actions = listOf("Stop" to { onStop(w) }),
                )
            }
        }
    }
}

@Composable
private fun ProgressLine(share: Float) {
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
private val BadRed = Color(0xFFE5484D)

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
