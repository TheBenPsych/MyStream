package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.core.content.edit
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.BenflixProblems
import com.github.damontecres.wholphin.services.BenflixServerHealth
import com.github.damontecres.wholphin.services.ProblemReport
import com.github.damontecres.wholphin.services.hilt.IoCoroutineScope
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.activityLogApi
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.LogLevel
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** One person watching something right now */
data class WatchingNow(
    val sessionId: String,
    val who: String,
    val what: String,
    val device: String,
    /** Benflix: which app and version they watch with, like "Benflix 1.0.180" */
    val app: String,
    val howItPlays: String,
    val converting: Boolean,
    val paused: Boolean,
    /** Benflix: the poster (the show's poster for an episode) */
    val posterUrl: String?,
    /** Benflix: how far in they are, 0 to 1 (null when the length isn't known) */
    val progress: Float?,
    /** Benflix: like "38 min left" */
    val timeLeft: String?,
    /** Benflix: why the server has to convert it, in plain words (null when it doesn't) */
    val why: String? = null,
    /** Benflix: how much of the internet connection it uses, in Mbps (null when unknown) */
    val mbps: Double? = null,
    /** Benflix: watching from outside the home network */
    val away: Boolean = false,
)

/** Benflix: one thing someone started watching (from Jellyfin's activity log) */
data class WatchedEntry(
    val text: String,
    val userId: java.util.UUID,
    val at: LocalDateTime,
    val time: String,
)

/** Benflix: one server problem from Jellyfin's log, in plain English, with repeats counted */
@Serializable
data class ServerProblem(
    val text: String,
    val time: String,
    val tip: String? = null,
    val count: Int = 1,
)

/** The top of the page: the server's name and whether it answers */
@Serializable
data class ServerHeader(
    val serverName: String = "",
    val version: String = "",
    val online: Boolean = false,
)

/** The Library numbers */
@Serializable
data class LibraryNumbers(
    val addedThisWeek: Int = 0,
    val movies: Int = 0,
    val shows: Int = 0,
    val episodes: Int = 0,
)

/** Benflix: one disk (or group of disks) the server keeps movies & shows on */
@Serializable
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

/** Benflix: what's saved on the device, so the Server page opens full even right after a restart */
@Serializable
private data class SavedServerPage(
    val header: ServerHeader? = null,
    val library: LibraryNumbers? = null,
    val problems: List<ServerProblem>? = null,
    val disks: List<ServerDisk>? = null,
    val updatedAt: Long = 0,
)

/**
 * Benflix: everything the Server page shows, kept for the whole app (not just while the page is
 * open). That way:
 * - the page opens straight away with the last numbers (also saved on the device between starts),
 * - the numbers can start loading before the admin even taps Server (from My Benflix or Settings),
 * - "Watching now" only keeps checking while the page is on screen.
 * Each part loads on its own and shows as soon as its answer arrives.
 */
@Singleton
class ServerDashboardData
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val health: BenflixServerHealth,
        private val reports: BenflixProblems,
        private val towerTools: com.github.damontecres.wholphin.services.BenflixTower,
        @param:IoCoroutineScope private val scope: CoroutineScope,
    ) {
        private val json = Json { ignoreUnknownKeys = true }
        private var savedFor: String? = null

        private val _header = MutableStateFlow<ServerHeader?>(null)
        val header: StateFlow<ServerHeader?> = _header

        private val _watching = MutableStateFlow<List<WatchingNow>?>(null)
        val watching: StateFlow<List<WatchingNow>?> = _watching

        private val _library = MutableStateFlow<LibraryNumbers?>(null)
        val library: StateFlow<LibraryNumbers?> = _library

        private val _problems = MutableStateFlow<List<ServerProblem>?>(null)
        val problems: StateFlow<List<ServerProblem>?> = _problems

        private val _disks = MutableStateFlow<List<ServerDisk>?>(null)
        val disks: StateFlow<List<ServerDisk>?> = _disks

        /** Problems people reported with "Report a problem" */
        private val _problemReports = MutableStateFlow<List<ProblemReport>?>(null)
        val problemReports: StateFlow<List<ProblemReport>?> = _problemReports

        /** Benflix: what everyone started watching this week, newest first (null until asked) */
        private val _history = MutableStateFlow<List<WatchedEntry>?>(null)
        val history: StateFlow<List<WatchedEntry>?> = _history

        /** Benflix: what the helper on Tower says (apps, downloads, how busy), null until it answers */
        private val _tower = MutableStateFlow<com.github.damontecres.wholphin.services.TowerStatus?>(null)
        val tower: StateFlow<com.github.damontecres.wholphin.services.TowerStatus?> = _tower

        /** Why the helper on Tower couldn't answer (not set up yet, not reachable), null when it's fine */
        private val _towerProblem = MutableStateFlow<String?>(null)
        val towerProblem: StateFlow<String?> = _towerProblem

        /** Asks the helper on Tower again (after restarting an app or fixing a download) */
        fun refreshTower(fresh: Boolean = true) {
            scope.launch(WholphinDispatchers.IO) { loadTower(fresh) }
        }

        private suspend fun loadTower(fresh: Boolean) {
            try {
                _tower.value = towerTools.status(fresh)
                _towerProblem.value = null
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: com.github.damontecres.wholphin.services.GameLibraryException) {
                _towerProblem.value = ex.message
            } catch (ex: Exception) {
                Timber.w(ex, "Server page: Tower tools could not load")
                _towerProblem.value = "Couldn't read Tower's tools. Try again in a moment."
            }
        }

        /** How many parts are still fetching fresh numbers (the "Loading new stats" note) */
        private val _loading = MutableStateFlow(0)
        val loading: StateFlow<Int> = _loading

        /** Benflix: the parts still loading, like "Disk space" (shown while it's slow) */
        private val _waitingOn = MutableStateFlow<Set<String>>(emptySet())
        val waitingOn: StateFlow<Set<String>> = _waitingOn

        /** When the numbers were last fully fresh (0 = never) */
        private val _updatedAt = MutableStateFlow(0L)
        val updatedAt: StateFlow<Long> = _updatedAt

        @Volatile private var lastRefresh = 0L
        private var watchJob: Job? = null

        /** Shows what was saved last time (once per server), before anything new arrives */
        private fun restoreSaved() {
            val key = serverKey() ?: return
            if (savedFor == key) return
            savedFor = key
            val saved =
                try {
                    prefs().getString(key, null)?.let { json.decodeFromString<SavedServerPage>(it) }
                } catch (ex: Exception) {
                    Timber.w(ex, "Server page: saved numbers unreadable")
                    null
                } ?: return
            if (_header.value == null) _header.value = saved.header
            if (_library.value == null) _library.value = saved.library
            if (_problems.value == null) _problems.value = saved.problems
            if (_disks.value == null) _disks.value = saved.disks ?: health.lastDisks
            if (_updatedAt.value == 0L) _updatedAt.value = saved.updatedAt
        }

        private fun save() {
            val key = serverKey() ?: return
            try {
                val saved =
                    SavedServerPage(
                        header = _header.value,
                        library = _library.value,
                        problems = _problems.value,
                        disks = _disks.value,
                        updatedAt = _updatedAt.value,
                    )
                prefs().edit { putString(key, json.encodeToString(saved)) }
            } catch (ex: Exception) {
                Timber.w(ex, "Server page: could not save numbers")
            }
        }

        /**
         * Starts loading everything quietly, unless it was loaded in the last minute. Called when
         * the admin opens My Benflix or Settings, so the Server page is ready by the time they tap it.
         */
        fun preload() {
            restoreSaved()
            if (System.currentTimeMillis() - lastRefresh < FRESH_MS) return
            refresh()
        }

        /** Loads every part again now (the refresh button and pull-to-refresh) */
        fun refresh() {
            restoreSaved()
            lastRefresh = System.currentTimeMillis()
            var left = 5
            val done = {
                synchronized(this) {
                    left--
                    if (left == 0) {
                        _updatedAt.value = System.currentTimeMillis()
                        save()
                    }
                }
            }
            loadPart("Server", done) {
                val h = safe { loadHeader() }
                _header.value = h ?: (_header.value?.copy(online = false) ?: ServerHeader(online = false))
            }
            loadPart("Watching now", done) {
                val list = safe { loadWatching() }
                _watching.value = list ?: _watching.value ?: emptyList()
            }
            loadPart("Library numbers", done) { safe { loadLibrary() }?.let { _library.value = it } }
            loadPart("Server problems", done) { _problems.value = safe { loadProblems() } ?: _problems.value.orEmpty() }
            loadPart("Disk space", done) { _disks.value = safe { health.readDisks() } ?: _disks.value.orEmpty() }
            loadPart("Problem reports", {}) { _problemReports.value = safe { reports.all() } ?: _problemReports.value.orEmpty() }
            loadPart("Tower", {}) { loadTower(fresh = true) }
        }

        /** Keeps "Watching now" fresh every 15 seconds, only while the Server page is on screen */
        fun startWatching() {
            if (watchJob?.isActive == true) return
            watchJob =
                scope.launch(WholphinDispatchers.IO) {
                    while (isActive) {
                        delay(REFRESH_MS)
                        safe { loadWatching() }?.let { _watching.value = it }
                        loadTower(fresh = false)
                    }
                }
        }

        fun stopWatching() {
            watchJob?.cancel()
            watchJob = null
        }

        /** Asks "Watching now" again straight away (after stopping or pausing someone) */
        fun refreshWatchingSoon() {
            scope.launch(WholphinDispatchers.IO) {
                delay(1_500)
                safe { loadWatching() }?.let { _watching.value = it }
            }
        }

        /** Benflix: loads "Watched today / this week" (only when Right now or People is opened) */
        fun loadHistory(force: Boolean = false) {
            if (!force && _history.value != null) return
            scope.launch(WholphinDispatchers.IO) {
                safe { readHistory() }?.let { _history.value = it }
            }
        }

        private suspend fun readHistory(): List<WatchedEntry> {
            val dayFormat = DateTimeFormatter.ofPattern("EEE h:mm a")
            return api.activityLogApi
                .getLogEntries(0, HISTORY_LIMIT, LocalDateTime.now(ZoneOffset.UTC).minusDays(7), true)
                .content.items
                .filter { it.type.equals("VideoPlayback", ignoreCase = true) }
                .sortedByDescending { it.date }
                .map {
                    WatchedEntry(
                        text = it.name,
                        userId = it.userId,
                        at = it.date,
                        time =
                            it.date
                                .atOffset(ZoneOffset.UTC)
                                .atZoneSameInstant(ZoneId.systemDefault())
                                .format(dayFormat),
                    )
                }
        }

        /** Benflix: how big each playing file is per second, so direct plays also show their Mbps */
        private val bitrates = java.util.concurrent.ConcurrentHashMap<String, Int>()

        private suspend fun directBitrate(
            itemId: java.util.UUID,
            mediaSourceId: String?,
        ): Int? {
            val key = "$itemId/$mediaSourceId"
            bitrates[key]?.let { return it }
            val item = safe { api.userLibraryApi.getItem(itemId, serverRepository.currentUser?.id).content } ?: return null
            val source = item.mediaSources?.firstOrNull { it.id == mediaSourceId } ?: item.mediaSources?.firstOrNull()
            return source?.bitrate?.also { bitrates[key] = it }
        }

        fun removeReport(report: ProblemReport) {
            _problemReports.update { it?.minus(report) }
        }

        /**
         * Loads one part on its own. The "Loading new stats" note waits for it at most [PART_WAIT_MS]:
         * a slow part (like sleeping disks waking up) keeps loading quietly and shows up when it's ready,
         * but never holds the rest of the page up.
         */
        private fun loadPart(
            name: String,
            done: () -> Unit,
            block: suspend () -> Unit,
        ) {
            // Still loading from last time (a slow part): don't ask again on top of it
            if (name in _waitingOn.value) {
                done()
                return
            }
            _loading.update { it + 1 }
            _waitingOn.update { it + name }
            val started = System.currentTimeMillis()
            val job =
                scope.launch(WholphinDispatchers.IO) {
                    try {
                        block()
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Server page: %s could not load", name)
                    } finally {
                        Timber.i("Server page: %s took %d ms", name, System.currentTimeMillis() - started)
                        _waitingOn.update { it - name }
                    }
                }
            scope.launch(WholphinDispatchers.IO) {
                try {
                    withTimeoutOrNull(PART_WAIT_MS) { job.join() }
                } finally {
                    _loading.update { it - 1 }
                    done()
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

        private fun serverKey(): String? = api.baseUrl?.trimEnd('/')?.let { "page_$it" }

        private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
                    val directMbps =
                        if (s.playState?.playMethod != PlayMethod.TRANSCODE) {
                            directBitrate(s.nowPlayingItem!!.id, s.playState?.mediaSourceId)
                        } else {
                            null
                        }
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
                    val position = s.playState?.positionTicks
                    val length = item.runTimeTicks
                    val progress =
                        if (position != null && length != null && length > 0) {
                            (position.toFloat() / length).coerceIn(0f, 1f)
                        } else {
                            null
                        }
                    val minutesLeft =
                        if (position != null && length != null && length > position) {
                            ((length - position) / TICKS_PER_MINUTE).toInt()
                        } else {
                            null
                        }
                    WatchingNow(
                        sessionId = s.id.orEmpty(),
                        who = s.userName ?: "Someone",
                        what = title,
                        device = s.deviceName.orEmpty(),
                        app = listOfNotNull(s.client, s.applicationVersion).joinToString(" "),
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
                        why = if (converting) transcode?.let { whyConverting(it) } else null,
                        mbps = ((if (converting) transcode?.bitrate else directMbps) ?: 0).takeIf { it > 0 }?.let { it / 1_000_000.0 },
                        away = isAwayFromHome(s.remoteEndPoint),
                        posterUrl =
                            api.imageApi.getItemImageUrl(
                                itemId = if (item.type == BaseItemKind.EPISODE) item.seriesId ?: item.id else item.id,
                                imageType = ImageType.PRIMARY,
                                maxHeight = POSTER_HEIGHT,
                            ),
                        progress = progress,
                        timeLeft =
                            minutesLeft?.let {
                                when {
                                    it < 1 -> "almost done"
                                    it < 60 -> "$it min left"
                                    else -> "${it / 60} h ${it % 60} min left"
                                }
                            },
                    )
                }

        /**
         * Totals, and "added this week". The newest items are asked for a page at a time, and it
         * stops as soon as one is older than a week, so a normal week takes one small question
         * instead of fetching 300 items.
         */
        private suspend fun loadLibrary(): LibraryNumbers =
            coroutineScope {
                val counts = async { safe { api.libraryApi.getItemCounts().content } }
                val recent = async { safe { countAddedThisWeek() } }
                val c = counts.await()
                LibraryNumbers(
                    addedThisWeek = recent.await() ?: _library.value?.addedThisWeek ?: 0,
                    movies = c?.movieCount ?: 0,
                    shows = c?.seriesCount ?: 0,
                    episodes = c?.episodeCount ?: 0,
                )
            }

        private suspend fun countAddedThisWeek(): Int {
            val weekAgo = LocalDateTime.now(ZoneOffset.UTC).minusDays(7)
            var count = 0
            var start = 0
            while (start < MAX_RECENT) {
                val items =
                    api.itemsApi
                        .getItems(
                            GetItemsRequest(
                                userId = serverRepository.currentUser?.id,
                                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                                recursive = true,
                                sortBy = listOf(ItemSortBy.DATE_CREATED),
                                sortOrder = listOf(SortOrder.DESCENDING),
                                fields = listOf(ItemFields.DATE_CREATED),
                                startIndex = start,
                                limit = RECENT_PAGE,
                                enableImages = false,
                                enableUserData = false,
                                enableTotalRecordCount = false,
                            ),
                        ).content.items
                val thisWeek = items.count { it.dateCreated?.isAfter(weekAgo) == true }
                count += thisWeek
                if (items.size < RECENT_PAGE || thisWeek < items.size) break
                start += RECENT_PAGE
            }
            return count
        }

        private suspend fun loadProblems(): List<ServerProblem> {
            val timeFormat = DateTimeFormatter.ofPattern("h:mm a")
            val entries =
                api.activityLogApi
                    .getLogEntries(0, LOG_LIMIT, LocalDateTime.now(ZoneOffset.UTC).minusDays(1))
                    .content.items
                    .filter { it.severity == LogLevel.ERROR || it.severity == LogLevel.CRITICAL }
            // Newest first; the same problem many times shows once with "×N"
            return entries
                .map { it to plainProblem(it.name, it.type, it.shortOverview) }
                .groupBy { it.second.first }
                .map { (text, list) ->
                    val newest = list.maxBy { it.first.date }.first
                    ServerProblem(
                        text = text,
                        time =
                            newest.date
                                .atOffset(ZoneOffset.UTC)
                                .atZoneSameInstant(ZoneId.systemDefault())
                                .format(timeFormat),
                        tip = list.first().second.second,
                        count = list.size,
                    ) to newest.date
                }.sortedByDescending { it.second }
                .map { it.first }
        }

        companion object {
            private const val PREFS = "benflix_server_page"
            private const val ACTIVE_WITHIN_SECONDS = 960
            private const val RECENT_PAGE = 500
            private const val MAX_RECENT = 3_000
            private const val PART_WAIT_MS = 10_000L
            private const val LOG_LIMIT = 60
            private const val REFRESH_MS = 15_000L
            private const val FRESH_MS = 60_000L
            private const val POSTER_HEIGHT = 240
            private const val TICKS_PER_MINUTE = 600_000_000L
            private const val HISTORY_LIMIT = 300
        }
    }

/**
 * Benflix: turns one of Jellyfin's log lines into a plain sentence and a tip.
 * Returns (sentence, tip). Unknown kinds keep Jellyfin's own words.
 */
internal fun plainProblem(
    name: String,
    type: String?,
    detail: String?,
): Pair<String, String?> {
    val t = type.orEmpty().lowercase()
    val n = name.trim()
    return when {
        t.contains("authenticationfailed") || n.contains("failed login", true) -> {
            "Someone typed a wrong password" + loginWho(n) to
                "Fine if it was a typo. Lots in a row from away from home could be a stranger guessing."
        }

        t.contains("userlockedout") || n.contains("locked out", true) -> {
            n to "Too many wrong passwords. Give them a new password in People to let them back in."
        }

        t.contains("subtitledownloadfailure") || n.contains("subtitles failed", true) -> {
            "Couldn't download subtitles" + forWhat(n) to
                "Usually nothing to worry about. The subtitle site may be busy or have none for it."
        }

        t.contains("scheduledtaskfailed") || (n.contains("failed", true) && n.contains("task", true)) -> {
            "A server chore didn't finish: " + n.substringBefore(" failed").trim() to
                "Jellyfin tries again on its next run. If it keeps failing, restart Jellyfin."
        }

        t.contains("plugin") -> {
            "A plugin had a problem: $n" to "Check the plugin in Jellyfin's Dashboard > Plugins."
        }

        n.contains("playback", true) || n.contains("transcod", true) -> {
            "A video wouldn't play: $n" to "Ask who was watching, or open Problems reported for their report."
        }

        else -> {
            n to detail?.takeIf { it.isNotBlank() && it != n }
        }
    }
}

private fun loginWho(name: String): String {
    // Jellyfin writes "Failed login try from Ben" or "Failed login attempt from ..."
    val from = name.substringAfter(" from ", "").trim()
    return if (from.isBlank()) "" else " ($from)"
}

private fun forWhat(name: String): String {
    val forPart = name.substringAfter(" for ", "").trim()
    return if (forPart.isBlank()) "" else " for $forPart"
}

/** Benflix: lets any screen reach the Server page data without its own view model */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface ServerDashboardEntryPoint {
    fun serverDashboardData(): ServerDashboardData
}

/**
 * Benflix: put this on screens the admin passes on the way to the Server page (My Benflix,
 * Settings). It starts loading the Server numbers quietly, so the page is ready when they tap it.
 */
@androidx.compose.runtime.Composable
fun PreloadServerPage(isAdmin: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(isAdmin) {
        if (!isAdmin) return@LaunchedEffect
        // A short wait, so the screen itself loads first
        delay(1_500)
        dagger.hilt.android.EntryPointAccessors
            .fromApplication(context.applicationContext, ServerDashboardEntryPoint::class.java)
            .serverDashboardData()
            .preload()
    }
}

/**
 * Benflix: why Jellyfin has to convert a stream, in plain words with what usually fixes it.
 * Jellyfin's reasons are read by name so new ones don't break anything.
 */
internal fun whyConverting(info: org.jellyfin.sdk.model.api.TranscodingInfo): String {
    val reasons = info.transcodeReasons.map { it.name }
    return when {
        reasons.any { it.startsWith("SUBTITLE") } -> {
            "Subtitles have to be burned into the picture. Tip: pick a different subtitle (SRT), or turn subtitles off."
        }

        reasons.any { it.contains("BITRATE") } -> {
            "The file is bigger than their quality limit (often the away-from-home limit). Tip: raise it in People."
        }

        reasons.any { it.startsWith("AUDIO") || it.contains("SECONDARY_AUDIO") } && info.isVideoDirect -> {
            "Their device can't play this sound format, so only the sound is converted (light work). " +
                "Tip: pick another audio track."
        }

        reasons.any { it.contains("RANGE") || it.contains("BIT_DEPTH") } -> {
            "Their screen can't show this HDR video, so colours are converted. Tip: an SDR copy saves the work."
        }

        reasons.any { it.startsWith("VIDEO") || it.contains("CONTAINER") || it.contains("REFFRAMES") } -> {
            "Their device can't play this video format. Tip: a more common copy (H.264/HEVC MKV) plays without converting."
        }

        reasons.isEmpty() -> {
            "Jellyfin didn't say why."
        }

        else -> {
            "Jellyfin says: " + reasons.joinToString(", ") { it.lowercase().replace('_', ' ') }
        }
    }
}

/** Benflix: true when the address is outside the home network */
internal fun isAwayFromHome(address: String?): Boolean {
    val ip = address?.trim()?.removePrefix("::ffff:")?.substringBefore('%') ?: return false
    if (ip.isBlank()) return false
    if (ip == "::1" || ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.")) return false
    if (ip.startsWith("fe80", true) || ip.startsWith("fd", true) || ip.startsWith("fc", true)) return false
    if (ip.startsWith("100.")) {
        // Tailscale and similar home VPNs use 100.64.0.0/10
        val second = ip.split('.').getOrNull(1)?.toIntOrNull()
        if (second != null && second in 64..127) return false
    }
    if (ip.startsWith("172.")) {
        val second = ip.split('.').getOrNull(1)?.toIntOrNull()
        if (second != null && second in 16..31) return false
    }
    return true
}
