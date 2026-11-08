package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.BenflixTower
import com.github.damontecres.wholphin.services.CheckupItem
import com.github.damontecres.wholphin.services.TowerApp
import com.github.damontecres.wholphin.services.TowerDownload
import com.github.damontecres.wholphin.services.TowerStatus
import com.github.damontecres.wholphin.services.TrackTidy
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import timber.log.Timber
import javax.inject.Inject
import com.github.damontecres.wholphin.ui.theme.BenflixType

/** Benflix: something on the Tower tools that asks "are you sure?" first */
sealed interface TowerAsk {
    val title: String
    val body: String

    data class Restart(
        val app: TowerApp,
    ) : TowerAsk {
        override val title get() = "Restart ${app.nice}?"
        override val body get() =
            if (app.name.contains("jellyfin", ignoreCase = true)) {
                "Anyone watching gets stopped for about a minute. (Tools > Restart Jellyfin can warn everyone first.)"
            } else {
                "It stops for about a minute and then starts again. Anything it was in the middle of carries on after."
            }
    }

    data class Replace(
        val download: TowerDownload,
    ) : TowerAsk {
        override val title get() =
            if (download.episodes > 1) "Remove the whole season pack?" else "Remove and search again?"
        override val body get() =
            if (download.episodes > 1) {
                "${download.title} is one download with all ${download.episodes} episodes in it. This deletes the " +
                    "whole pack, makes sure it's never picked again, and looks for another copy of each episode."
            } else {
                "This deletes the stuck download of ${download.title}, makes sure it's never picked again, " +
                    "and looks for a different copy."
            }
    }

    data class FixTracks(
        val folder: String,
    ) : TowerAsk {
        override val title get() = "Tidy the track names in $folder?"
        override val body get() =
            "This renames the sound and subtitle tracks and picks the ones that start by default. Nothing is " +
                "re-encoded, and Undo puts everything back. Tip: press Preview first to see what changes."
    }

    data class Undo(
        val file: String,
    ) : TowerAsk {
        override val title get() = "Undo the last track tidy?"
        override val body get() = "This puts every track name back the way it was before (${undoDate(file)})."
    }
}

/**
 * Benflix Server page tools that need Tower (the helper on port 8097): restart an app, fix stuck downloads,
 * ask Sonarr/Radarr to search, and the track tidy tool.
 */
@HiltViewModel
class ServerTowerViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val tower: BenflixTower,
        val data: ServerDashboardData,
    ) : ViewModel() {
        private val _notice = MutableStateFlow<Pair<String, String>?>(null)
        val notice: StateFlow<Pair<String, String>?> = _notice

        private val _ask = MutableStateFlow<TowerAsk?>(null)
        val ask: StateFlow<TowerAsk?> = _ask

        private val _tracks = MutableStateFlow<TrackTidy?>(null)
        val tracks: StateFlow<TrackTidy?> = _tracks

        private val _tracksProblem = MutableStateFlow<String?>(null)
        val tracksProblem: StateFlow<String?> = _tracksProblem

        private var tracksWatch: Job? = null

        fun ask(what: TowerAsk) {
            _ask.value = what
        }

        fun cancelAsk() {
            _ask.value = null
        }

        fun clearNotice() {
            _notice.value = null
        }

        /** "Yes" on the are-you-sure pop-up */
        fun confirm() {
            val what = _ask.value ?: return
            _ask.value = null
            when (what) {
                is TowerAsk.Restart -> {
                    act("Couldn't restart ${what.app.nice}") {
                        _notice.value = "Restarting" to "Restarting ${what.app.nice}…"
                        _notice.value = "Done" to tower.restartApp(what.app.name)
                        data.refreshTower()
                    }
                }

                is TowerAsk.Replace -> {
                    act("Couldn't remove it") {
                        _notice.value = "Done" to tower.replace(what.download)
                        data.refreshTower()
                    }
                }

                is TowerAsk.FixTracks -> {
                    startTracks("apply", folder = what.folder)
                }

                is TowerAsk.Undo -> {
                    startTracks("undo", undoFile = what.file)
                }
            }
        }

        fun checkAgain(download: TowerDownload) =
            act("Couldn't ask again") {
                _notice.value = "Checking" to tower.checkAgain(download)
                delay(3_000)
                data.refreshTower()
            }

        /** The checkup's "Search" button next to a show with missing episodes */
        fun searchMissing(found: CheckupItem) =
            act("Couldn't ask Sonarr") {
                val show = api.userLibraryApi.getItem(found.itemId, serverRepository.currentUser?.id).content
                _notice.value = "Searching" to tower.searchMissing(show)
            }

        /** The checkup's "Find a better copy" button next to a low quality movie */
        fun searchBetter(found: CheckupItem) =
            act("Couldn't ask Radarr") {
                val movie = api.userLibraryApi.getItem(found.itemId, serverRepository.currentUser?.id).content
                _notice.value = "Searching" to tower.searchBetter(movie)
            }

        /** Opens the track tidy tool (and keeps it fresh while it runs) */
        fun loadTracks() {
            watchTracks { tower.tracks() }
        }

        fun previewTracks(folder: String) = startTracks("preview", folder = folder)

        private fun startTracks(
            mode: String,
            folder: String? = null,
            undoFile: String? = null,
        ) {
            watchTracks { tower.startTracks(mode, folder, undoFile) }
        }

        private fun watchTracks(first: suspend () -> TrackTidy) {
            tracksWatch?.cancel()
            tracksWatch =
                viewModelScope.launch(WholphinDispatchers.IO) {
                    try {
                        var state = first()
                        _tracks.value = state
                        _tracksProblem.value = null
                        var checks = 0
                        while (state.running && checks++ < TRACK_CHECKS) {
                            delay(2_000)
                            state = tower.tracks()
                            _tracks.value = state
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Track tidy failed")
                        _tracksProblem.value = ex.message ?: "Couldn't reach the track tool."
                    }
                }
        }

        private fun act(
            failTitle: String,
            block: suspend () -> Unit,
        ) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    block()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Tower tool failed")
                    _notice.value = failTitle to (ex.message ?: "Tower said no. Try again in a moment.")
                }
            }
        }
    }

private const val TRACK_CHECKS = 1_800

/** "Are you sure?" and result pop-ups for the Tower tools */
@Composable
fun ServerTowerDialogs(viewModel: ServerTowerViewModel) {
    val ask by viewModel.ask.collectAsState()
    val notice by viewModel.notice.collectAsState()
    ask?.let {
        ConfirmDialog(
            title = it.title,
            body = it.body,
            onCancel = viewModel::cancelAsk,
            onConfirm = viewModel::confirm,
        )
    }
    if (ask == null) {
        notice?.let { (title, body) ->
            ConfirmDialog(
                title = title,
                body = body,
                onCancel = viewModel::clearNotice,
                onConfirm = viewModel::clearNotice,
            )
        }
    }
}

/** "3 days" from a number of seconds */
internal fun upText(seconds: Long): String {
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days >= 2 -> "$days days"
        days == 1L -> "1 day ${hours % 24} h"
        hours >= 1 -> "$hours h ${minutes % 60} min"
        else -> "$minutes min"
    }
}

private fun undoDate(file: String): String =
    Regex("""undo-(\d{4})-(\d{2})-(\d{2})_(\d{2})(\d{2})""").find(file)?.destructured?.let { (y, mo, d, h, mi) ->
        "from $d/$mo/$y $h:$mi"
    } ?: file

/**
 * The light for an app: green running, gold running but not answering, red when it keeps restarting or stopped
 * by itself with an error, grey when it was stopped on purpose
 */
internal fun appLight(app: TowerApp): Light =
    when {
        app.ok -> Light.GOOD
        app.running -> Light.WARN
        app.problem -> Light.BAD
        else -> Light.UNKNOWN
    }

/** Apps that need a look (shown on "Needs you"). Apps stopped on purpose aren't on it. */
internal fun brokenApps(status: TowerStatus?): List<TowerApp> = status?.apps.orEmpty().filter { it.problem && it.status != "Never started" }

/** Overview: "Tower has been on for 3 days" and whether the apps are fine */
internal fun LazyListScope.towerOverviewItems(
    status: TowerStatus?,
    problem: String?,
    onOpen: () -> Unit,
) {
    item { SectionTitle("Tower") }
    item {
        when {
            status != null -> {
                val broken = brokenApps(status)
                ServerActionRow(
                    headline = status.upSeconds?.let { "On for ${upText(it)}" } ?: "Tower is on",
                    supporting =
                        listOfNotNull(
                            status.cpu?.let { "Processor $it% busy" },
                            status.apps?.let {
                                val running = it.count { a -> a.running }
                                when {
                                    broken.isNotEmpty() -> "${broken.size} app(s) need a look"
                                    running == it.size -> "all ${it.size} apps running"
                                    else -> "$running of ${it.size} apps running"
                                }
                            },
                            status.downloads.size
                                .takeIf { it > 0 }
                                ?.let { "$it downloading" },
                        ).joinToString(" · "),
                    leading = { Dot(if (broken.isEmpty()) Light.GOOD else Light.BAD) },
                    onClick = onOpen,
                )
            }

            problem != null -> {
                ServerActionRow(headline = "Tower tools aren't set up yet", supporting = problem, leading = { Dot(Light.UNKNOWN) })
            }

            else -> {
                LoadingBox("Asking Tower…")
            }
        }
    }
}

/** Right now: how hard Tower is working */
internal fun LazyListScope.towerBusyItems(status: TowerStatus?) {
    if (status == null) return
    item { SectionTitle("How hard Tower is working") }
    listOfNotNull(
        status.cpu?.let { "Processor" to it },
        status.gpu?.let { "Graphics chip (converts video)" to it },
        status.memory?.let { "Memory" to it },
    ).forEach { (name, percent) ->
        item(key = "busy-$name") {
            ServerActionRow(
                headline = "$name: $percent%",
                supporting =
                    when {
                        percent >= 90 -> "Working flat out. Things may stutter."
                        percent >= 60 -> "Busy"
                        else -> null
                    },
                leading = {
                    Dot(
                        when {
                            percent >= 90 -> Light.BAD
                            percent >= 60 -> Light.WARN
                            else -> Light.GOOD
                        },
                    )
                },
                extra = { ProgressLine(percent / 100f) },
            )
        }
    }
}

/** Downloads: stuck ones first, then what's downloading, then the apps on Tower */
internal fun LazyListScope.towerDownloadItems(
    status: TowerStatus?,
    problem: String?,
    viewModel: ServerTowerViewModel,
) {
    if (status == null) {
        item {
            if (problem != null) {
                ServerActionRow(headline = "Tower tools aren't set up yet", supporting = problem, leading = { Dot(Light.UNKNOWN) })
            } else {
                LoadingBox("Asking Tower…")
            }
        }
        return
    }
    status.problems.forEach { text ->
        item(key = "tower-problem-$text") { ServerActionRow(headline = text, leading = { Dot(Light.WARN) }) }
    }
    if (!status.sonarr || !status.radarr) {
        item {
            ServerActionRow(
                headline =
                    listOfNotNull("Sonarr".takeIf { !status.sonarr }, "Radarr".takeIf { !status.radarr }).joinToString(" and ") +
                        " not connected",
                supporting = "Run the Benflix setup command on Tower again to connect it.",
                leading = { Dot(Light.UNKNOWN) },
            )
        }
    }
    val stuck = status.downloads.filter { it.stuck }
    val going = status.downloads.filter { !it.stuck }
    if (stuck.isNotEmpty()) {
        item { SectionTitle("Stuck downloads (${stuck.size})") }
        items(stuck, key = { "stuck-${it.app}-${it.id}" }) { d ->
            ServerActionRow(
                headline = d.title,
                supporting = listOfNotNull(d.why ?: "Stuck", appName(d)).joinToString(" · "),
                leading = { Dot(Light.WARN) },
                actions =
                    listOf(
                        "Check again" to { viewModel.checkAgain(d) },
                        (if (d.episodes > 1) "Remove the whole pack and search again" else "Remove and search again") to
                            { viewModel.ask(TowerAsk.Replace(d)) },
                    ),
            )
        }
    }
    item { SectionTitle("Downloading now") }
    if (going.isEmpty()) {
        item { ServerActionRow(headline = if (stuck.isEmpty()) "Nothing is downloading right now" else "Nothing else is downloading") }
    } else {
        items(going, key = { "dl-${it.app}-${it.id}" }) { d ->
            ServerActionRow(
                headline = d.title,
                supporting =
                    listOfNotNull(
                        when {
                            d.paused -> "Paused"
                            d.queued -> "Waiting in line"
                            else -> "${d.percent}%"
                        },
                        d.timeLeft?.takeIf { !d.paused && !d.queued },
                        appName(d),
                    ).joinToString(" · "),
                extra = { ProgressLine(d.percent / 100f) },
            )
        }
    }
    item { SectionTitle("Your apps on Tower") }
    val apps = status.apps
    if (apps == null) {
        item {
            ServerActionRow(
                headline = "The helper can't see your apps yet",
                supporting = "Run the Benflix setup command on Tower again.",
            )
        }
    } else {
        items(apps, key = { "app-${it.name}" }) { app ->
            ServerActionRow(
                headline = app.nice,
                supporting = if (app.nice == app.name) app.status else "${app.status} · ${app.name}",
                leading = { Dot(appLight(app)) },
                actions =
                    if (app.restartable) {
                        listOf((if (app.running) "Restart" else "Start") to { viewModel.ask(TowerAsk.Restart(app)) })
                    } else {
                        emptyList()
                    },
            )
        }
    }
}

private fun appName(d: TowerDownload) = if (d.app == "sonarr") "Sonarr" else "Radarr"

/** Tools: the track tidy tool, with Preview, Fix and Undo */
internal fun LazyListScope.trackTidyItems(
    tracks: TrackTidy?,
    problem: String?,
    viewModel: ServerTowerViewModel,
) {
    item { SectionTitle("Tidy sound and subtitle names") }
    item {
        ServerActionRow(
            headline = "Makes track names neat, like \"English 5.1\" and \"English - Signs & Songs\"",
            supporting = "And picks the right ones to start with. Nothing is re-encoded. Preview first, then Fix. Undo puts it back.",
        )
    }
    when {
        problem != null && tracks == null -> {
            item { ServerActionRow(headline = "Couldn't open the track tool", supporting = problem, leading = { Dot(Light.UNKNOWN) }) }
            return
        }

        tracks == null -> {
            item { LoadingBox("Opening the track tool…") }
            return
        }

        !tracks.ready -> {
            item {
                ServerActionRow(
                    headline = "Not set up on Tower yet",
                    supporting = tracks.problem ?: "Run the Benflix setup command on Tower again.",
                    leading = { Dot(Light.UNKNOWN) },
                )
            }
            return
        }
    }
    val state = tracks ?: return
    if (state.running || state.lines.isNotEmpty()) {
        item(key = "tracks-output") {
            val what =
                when (state.mode) {
                    "preview" -> "Preview of ${state.folder}"
                    "apply" -> "Fixing ${state.folder}"
                    else -> "Undo"
                }
            ServerActionRow(
                headline =
                    when {
                        state.running -> "$what: working…"
                        state.result == "failed" -> "$what: something went wrong"
                        else -> "$what: done"
                    },
                leading = {
                    Dot(
                        when {
                            state.running -> Light.UNKNOWN
                            state.result == "failed" -> Light.BAD
                            else -> Light.GOOD
                        },
                    )
                },
                extra = {
                    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text(
                            text = state.lines.takeLast(OUTPUT_LINES).joinToString("\n"),
                            fontSize = BenflixType.tag,
                            lineHeight = BenflixType.tag * 1.3f,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
    if (!state.running) {
        items(state.folders, key = { "tracks-$it" }) { folder ->
            ServerActionRow(
                headline = folder,
                onClick = { viewModel.previewTracks(folder) },
                actions =
                    listOf(
                        "Preview" to { viewModel.previewTracks(folder) },
                        "Fix" to { viewModel.ask(TowerAsk.FixTracks(folder)) },
                    ),
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        state.undoFiles.firstOrNull()?.let { file ->
            item(key = "tracks-undo") {
                ServerActionRow(
                    headline = "Undo the last fix",
                    supporting = "Puts the names back as they were ${undoDate(file)}",
                    actions = listOf("Undo" to { viewModel.ask(TowerAsk.Undo(file)) }),
                )
            }
        }
    }
}

private const val OUTPUT_LINES = 18
