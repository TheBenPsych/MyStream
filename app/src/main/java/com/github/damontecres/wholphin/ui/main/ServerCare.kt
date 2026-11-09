package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.services.BenflixTower
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Benflix: the last 7 days on Tower (the Sunday report card) */
data class CareReport(
    val from: Long,
    val to: Long,
    val addedMovies: Int,
    val addedEpisodes: Int,
    val movieNames: List<String>,
    val showNames: List<String>,
    /** name to how many things they played */
    val watchers: List<Pair<String, Int>>,
    val checked: Int,
    val broken: Int,
    val subtitlesFixed: Int,
    val removed: Int,
    val freeBytes: Long?,
    val needs: List<String>,
)

/** One movie or show on the tidy-up lists */
data class TidyRow(
    val app: String,
    val id: Int,
    val title: String,
    val year: Int?,
    val kind: String,
    val size: Long,
    val added: Long,
    /** Seconds since 1970 someone last watched it; 1 = watched, but not when; 0 = never */
    val watched: Long,
) {
    val key get() = "$app:$id"
}

data class TidyLists(
    val working: Boolean,
    val problem: String?,
    val old: List<TidyRow>,
    val oldCount: Int,
    val oldTotal: Long,
    val big: List<TidyRow>,
)

/** One file the night-time play test flagged */
data class PlaytestRow(
    val id: String,
    val name: String,
    val problems: List<String>,
    val notes: List<String>,
    val at: Long,
)

data class PlaytestState(
    val on: Boolean,
    val checkedThisWeek: Int,
    val broken: List<PlaytestRow>,
    val look: List<PlaytestRow>,
    val last: Long?,
)

/**
 * Benflix Server page: the weekly report card (Overview), and the tidy-up helper and night-time play
 * test (Library). All of it comes from the Benflix helper on Tower (benflix_care.py).
 */
@HiltViewModel
class ServerCareViewModel
    @Inject
    constructor(
        private val tower: BenflixTower,
    ) : ViewModel() {
        private val _report = MutableStateFlow<CareReport?>(null)
        val report: StateFlow<CareReport?> = _report
        private val _reportProblem = MutableStateFlow<String?>(null)
        val reportProblem: StateFlow<String?> = _reportProblem

        private val _tidy = MutableStateFlow<TidyLists?>(null)
        val tidy: StateFlow<TidyLists?> = _tidy
        private val _ticked = MutableStateFlow<Map<String, TidyRow>>(emptyMap())
        val ticked: StateFlow<Map<String, TidyRow>> = _ticked
        private val _showBiggest = MutableStateFlow(false)
        val showBiggest: StateFlow<Boolean> = _showBiggest

        private val _playtest = MutableStateFlow<PlaytestState?>(null)
        val playtest: StateFlow<PlaytestState?> = _playtest
        private val _libraryProblem = MutableStateFlow<String?>(null)
        val libraryProblem: StateFlow<String?> = _libraryProblem

        private val _askRemove = MutableStateFlow(false)
        val askRemove: StateFlow<Boolean> = _askRemove
        private val _notice = MutableStateFlow<String?>(null)
        val notice: StateFlow<String?> = _notice

        private var tidyJob: Job? = null

        fun loadReport(fresh: Boolean = false) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    _report.value = parseReport(tower.care("report" + if (fresh) "?fresh=1" else ""))
                    _reportProblem.value = null
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Server page: report card")
                    _reportProblem.value = ex.message ?: "Couldn't reach Tower."
                }
            }
        }

        fun loadLibrary() {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    _playtest.value = parsePlaytest(tower.care("playtest"))
                    _libraryProblem.value = null
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Server page: play test")
                    _libraryProblem.value = ex.message ?: "Couldn't reach Tower."
                }
            }
            loadTidy(false)
        }

        private fun loadTidy(fresh: Boolean) {
            tidyJob?.cancel()
            tidyJob =
                viewModelScope.launch(WholphinDispatchers.IO) {
                    try {
                        // Tower looks through everything in the background; ask again every few seconds
                        repeat(60) { i ->
                            val lists = parseTidy(tower.care("tidy" + if (fresh && i == 0) "?fresh=1" else ""))
                            if (!lists.working) {
                                _tidy.value = lists
                                return@launch
                            }
                            if (_tidy.value == null) _tidy.value = lists
                            delay(5_000)
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Server page: tidy-up")
                        _libraryProblem.value = ex.message ?: "Couldn't reach Tower."
                    }
                }
        }

        fun toggle(row: TidyRow) {
            _ticked.value = _ticked.value.toMutableMap().apply { if (remove(row.key) == null) put(row.key, row) }
        }

        fun showBiggest(on: Boolean) {
            _showBiggest.value = on
        }

        fun askRemove() {
            if (_ticked.value.isNotEmpty()) _askRemove.value = true
        }

        fun cancelRemove() {
            _askRemove.value = false
        }

        fun clearNotice() {
            _notice.value = null
        }

        fun removeTicked() {
            _askRemove.value = false
            val picks = _ticked.value.values.toList()
            if (picks.isEmpty()) return
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    val items = JSONArray()
                    picks.forEach { items.put(JSONObject().put("app", it.app).put("id", it.id).put("title", it.title)) }
                    val answer = tower.carePost("tidy/remove", JSONObject().put("sure", true).put("items", items))
                    _ticked.value = emptyMap()
                    _notice.value = answer.optString("message", "Done.")
                    _tidy.value = null
                    loadTidy(true)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    _notice.value = ex.message ?: "Couldn't remove them."
                }
            }
        }

        fun playtestOk(row: PlaytestRow) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    _playtest.value = parsePlaytest(tower.carePost("playtest/${row.id}/ok"))
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    _notice.value = ex.message
                }
            }
        }

        fun playtestAgain(row: PlaytestRow) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    _notice.value = tower.carePost("playtest/${row.id}/again").optString("message", "Checking it again.")
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    _notice.value = ex.message
                }
            }
        }
    }

private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }

private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun parseReport(j: JSONObject): CareReport {
    val disks = j.optJSONArray("disks").objects()
    return CareReport(
        from = j.optDouble("from", 0.0).toLong(),
        to = j.optDouble("to", 0.0).toLong(),
        addedMovies = j.optInt("added_movies"),
        addedEpisodes = j.optInt("added_episodes"),
        movieNames = j.optJSONArray("movie_names").strings(),
        showNames = j.optJSONArray("show_names").strings(),
        watchers = j.optJSONArray("watchers").objects().map { it.optString("name") to it.optInt("plays") },
        checked = j.optInt("checked"),
        broken = j.optInt("broken"),
        subtitlesFixed = j.optInt("subtitles_fixed"),
        removed = j.optInt("removed"),
        freeBytes = disks.firstOrNull()?.optLong("free"),
        needs = j.optJSONArray("needs").strings(),
    )
}

private fun parseTidyRow(o: JSONObject) =
    TidyRow(
        app = o.optString("app"),
        id = o.optInt("id"),
        title = o.optString("title"),
        year = if (o.isNull("year")) null else o.optInt("year").takeIf { it > 0 },
        kind = o.optString("kind"),
        size = o.optLong("size"),
        added = o.optDouble("added", 0.0).toLong(),
        watched = o.optDouble("watched", 0.0).toLong(),
    )

private fun parseTidy(j: JSONObject) =
    TidyLists(
        working = j.optBoolean("working"),
        problem = if (j.isNull("problem")) null else j.optString("problem").takeIf { it.isNotBlank() },
        old = j.optJSONArray("old").objects().map(::parseTidyRow),
        oldCount = j.optInt("old_count"),
        oldTotal = j.optLong("old_total"),
        big = j.optJSONArray("big").objects().map(::parseTidyRow),
    )

private fun parsePlaytestRow(o: JSONObject) =
    PlaytestRow(
        id = o.optString("id"),
        name = o.optString("name"),
        problems = o.optJSONArray("problems").strings(),
        notes = o.optJSONArray("notes").strings(),
        at = o.optDouble("at", 0.0).toLong(),
    )

private fun parsePlaytest(j: JSONObject) =
    PlaytestState(
        on = j.optBoolean("on"),
        checkedThisWeek = j.optInt("checked_this_week"),
        broken = j.optJSONArray("broken").objects().map(::parsePlaytestRow),
        look = j.optJSONArray("look").objects().map(::parsePlaytestRow),
        last = if (j.isNull("last")) null else j.optDouble("last", 0.0).toLong().takeIf { it > 0 },
    )

private fun day(seconds: Long): String = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(seconds * 1000))

private fun monthYear(seconds: Long): String = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(seconds * 1000))

/** "1.4 GB" */
internal fun careBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var n = bytes.toDouble()
    var u = 0
    while (n >= 1024 && u < units.size - 1) {
        n /= 1024
        u++
    }
    return if (n >= 100 || u == 0) "${n.toLong()} ${units[u]}" else String.format(Locale.getDefault(), "%.1f %s", n, units[u])
}

private fun plural(
    n: Int,
    word: String,
) = "$n $word${if (n == 1) "" else "s"}"

/** Overview: "This week" (the Sunday report card) */
internal fun LazyListScope.careReportItems(
    report: CareReport?,
    problem: String?,
    viewModel: ServerCareViewModel,
) {
    val sunday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
    item(key = "care-title") { SectionTitle(if (sunday) "Your Sunday report card" else "This week") }
    if (report == null) {
        item(key = "care-loading") {
            if (problem != null) {
                ServerActionRow(headline = "The report card isn't ready", supporting = problem, leading = { Dot(Light.UNKNOWN) })
            } else {
                LoadingBox("Putting the week together…")
            }
        }
        return
    }
    item(key = "care-added") {
        ServerActionRow(
            headline = "${plural(report.addedMovies, "movie")} and ${plural(report.addedEpisodes, "episode")} added",
            supporting =
                listOf(
                    "${day(report.from)} to ${day(report.to)}",
                    (report.movieNames + report.showNames).take(6).joinToString(", "),
                ).filter { it.isNotBlank() }.joinToString(" · "),
            leading = { Dot(Light.GOOD) },
            actions = listOf("Check again" to { viewModel.loadReport(fresh = true) }),
        )
    }
    item(key = "care-watchers") {
        val top = report.watchers.firstOrNull()
        ServerActionRow(
            headline =
                if (top ==
                    null
                ) {
                    "Nobody watched anything this week"
                } else {
                    "${top.first} watched the most (${plural(top.second, "thing")})"
                },
            supporting =
                report.watchers
                    .drop(1)
                    .take(4)
                    .joinToString(", ") { "${it.first} ${it.second}" }
                    .ifBlank { null },
            leading = { Dot(Light.GOOD) },
        )
    }
    item(key = "care-fixed") {
        ServerActionRow(
            headline =
                "${plural(report.checked, "file")} play-tested, ${plural(report.subtitlesFixed, "subtitle file")} lined up",
            supporting =
                listOfNotNull(
                    "${plural(report.broken, "file")} didn't pass".takeIf { report.broken > 0 },
                    "${plural(report.removed, "thing")} tidied away".takeIf { report.removed > 0 },
                    report.freeBytes?.let { "${careBytes(it)} of space left" },
                ).joinToString(" · ").ifBlank { null },
            leading = { Dot(if (report.broken > 0) Light.WARN else Light.GOOD) },
        )
    }
    report.needs.forEach { text ->
        item(key = "care-need-$text") { ServerActionRow(headline = text, leading = { Dot(Light.WARN) }) }
    }
}

/** Library: the night-time play test and the tidy-up helper */
internal fun LazyListScope.careLibraryItems(
    playtest: PlaytestState?,
    tidy: TidyLists?,
    ticked: Map<String, TidyRow>,
    showBiggest: Boolean,
    problem: String?,
    viewModel: ServerCareViewModel,
) {
    item(key = "pt-title") { SectionTitle("Night-time play test") }
    if (playtest == null) {
        item(key = "pt-loading") {
            if (problem != null) {
                ServerActionRow(headline = "Tower's play test isn't set up yet", supporting = problem, leading = { Dot(Light.UNKNOWN) })
            } else {
                LoadingBox("Asking Tower…")
            }
        }
    } else {
        item(key = "pt-summary") {
            ServerActionRow(
                headline =
                    if (!playtest.on) {
                        "The play test isn't on Tower yet"
                    } else if (playtest.broken.isEmpty()) {
                        "Every new file played fine"
                    } else {
                        "${plural(playtest.broken.size, "file")} didn't pass"
                    },
                supporting =
                    if (!playtest.on) {
                        "Run the Benflix setup command on Tower again."
                    } else {
                        "${plural(playtest.checkedThisWeek, "new file")} checked this week" +
                            (playtest.last?.let { " · last check ${day(it)}" } ?: "")
                    },
                leading = {
                    Dot(
                        if (!playtest.on) {
                            Light.UNKNOWN
                        } else if (playtest.broken.isEmpty()) {
                            Light.GOOD
                        } else {
                            Light.BAD
                        },
                    )
                },
            )
        }
        items(playtest.broken, key = { "pt-bad-${it.id}" }) { row ->
            ServerActionRow(
                headline = row.name,
                supporting = row.problems.joinToString(" · "),
                leading = { Dot(Light.BAD) },
                actions = listOf("Check again" to { viewModel.playtestAgain(row) }, "It's fine" to { viewModel.playtestOk(row) }),
            )
        }
        items(playtest.look.take(10), key = { "pt-look-${it.id}" }) { row ->
            ServerActionRow(
                headline = row.name,
                supporting = row.notes.joinToString(" · "),
                leading = { Dot(Light.WARN) },
                actions = listOf("It's fine" to { viewModel.playtestOk(row) }),
            )
        }
    }

    item(key = "tidy-title") { SectionTitle("Tidy-up") }
    if (tidy == null || (tidy.working && tidy.old.isEmpty() && tidy.big.isEmpty())) {
        item(key = "tidy-loading") {
            if (problem != null && tidy == null) {
                ServerActionRow(headline = "Tidy-up isn't set up yet", supporting = problem, leading = { Dot(Light.UNKNOWN) })
            } else {
                LoadingBox("Looking through the whole library… this can take a minute.")
            }
        }
        return
    }
    if (tidy.problem != null) {
        item(key = "tidy-problem") { ServerActionRow(headline = tidy.problem, leading = { Dot(Light.UNKNOWN) }) }
        return
    }
    item(key = "tidy-switch") {
        ServerActionRow(
            headline =
                if (showBiggest) {
                    "The biggest movies and shows"
                } else {
                    "${plural(tidy.oldCount, "thing")} nobody watched in over a year (${careBytes(tidy.oldTotal)})"
                },
            supporting = "Nothing is removed unless you tick it. Removing goes through Radarr or Sonarr, so they won't download it again.",
            leading = { Dot(Light.GOOD) },
            actions =
                listOf((if (showBiggest) "Show not watched in a year" else "Show the biggest") to { viewModel.showBiggest(!showBiggest) }),
        )
    }
    val rows = if (showBiggest) tidy.big else tidy.old
    if (rows.isEmpty()) {
        item(key = "tidy-empty") { ServerActionRow(headline = "Everything has been watched in the last year.") }
    }
    items(rows, key = { "tidy-${it.key}" }) { row ->
        val on = ticked.containsKey(row.key)
        val seen =
            when {
                row.watched > 1 -> "last watched ${monthYear(row.watched)}"
                row.watched == 1L -> "watched once, a long time ago"
                else -> "never watched"
            }
        ServerActionRow(
            headline = (if (on) "☑ " else "☐ ") + row.title + (row.year?.let { " ($it)" } ?: ""),
            supporting = "${row.kind} · ${careBytes(row.size)} · $seen · added ${monthYear(row.added)}",
            onClick = { viewModel.toggle(row) },
            leading = { Dot(if (on) Light.BAD else Light.UNKNOWN) },
            actions = listOf((if (on) "Untick" else "Tick") to { viewModel.toggle(row) }),
        )
    }
    if (ticked.isNotEmpty()) {
        item(key = "tidy-remove") {
            ServerActionRow(
                headline = "Remove ${ticked.size} ticked (frees ${careBytes(ticked.values.sumOf { it.size })})",
                leading = { Dot(Light.BAD) },
                onClick = viewModel::askRemove,
                actions = listOf("Remove" to viewModel::askRemove),
            )
        }
    }
}

@Composable
fun ServerCareDialogs(viewModel: ServerCareViewModel) {
    val ask by viewModel.askRemove.collectAsState()
    val ticked by viewModel.ticked.collectAsState()
    val notice by viewModel.notice.collectAsState()
    if (ask) {
        val names = ticked.values.take(6).joinToString(", ") { it.title } + if (ticked.size > 6) " and ${ticked.size - 6} more" else ""
        ConfirmDialog(
            title = "Remove ${ticked.size} for good?",
            body = "$names. The files are deleted from Tower and Radarr/Sonarr forget them. This can't be undone.",
            onCancel = viewModel::cancelRemove,
            onConfirm = viewModel::removeTicked,
        )
    } else {
        notice?.let {
            ConfirmDialog(title = "Tidy-up", body = it, onCancel = viewModel::clearNotice, onConfirm = viewModel::clearNotice)
        }
    }
}
