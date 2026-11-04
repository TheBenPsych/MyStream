package com.github.damontecres.wholphin.services

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.ViewModel
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.hilt.IoCoroutineScope
import com.github.damontecres.wholphin.ui.detail.DebugViewModel.Companion.getLogCatLines
import com.github.damontecres.wholphin.ui.playback.SoundWatchdog
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.clientLogApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** One "Something's wrong" report, shown on the Server page */
data class ProblemReport(
    val at: Long,
    val userId: UUID,
    val userName: String,
    val itemId: UUID?,
    val title: String,
    val problem: String,
    val device: String,
)

/**
 * Benflix "Report a problem": family pick what's wrong (no sound, wrong subtitles...) and it's
 * saved on their own Jellyfin account (so Ben's Server page can list it) and the app's recent log
 * is sent to the server's log folder.
 */
@Singleton
class BenflixProblems
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val displayPreferencesService: DisplayPreferencesService,
        private val serverReportService: ServerReportService,
    ) {
        suspend fun report(
            itemId: UUID?,
            title: String,
            problem: String,
        ) = withContext(WholphinDispatchers.IO) {
            val user = serverRepository.currentUser ?: return@withContext
            val device = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            val line =
                listOf(
                    System.currentTimeMillis().toString(),
                    itemId?.toString().orEmpty(),
                    clean(title),
                    clean(problem),
                    clean(device),
                ).joinToString(FIELD)
            displayPreferencesService.updateDisplayPreferences(user.id) {
                val old = get(KEY).orEmpty().split(LINE).filter { it.isNotBlank() }
                put(KEY, (old + line).takeLast(MAX_PER_PERSON).joinToString(LINE))
            }
            try {
                val log = getLogCatLines().takeLast(LOG_LINES).joinToString("\n") { it.text }
                val header = serverReportService.buildLogHeader("Benflix problem report: $problem - $title")
                api.clientLogApi.logFile(
                    "$header\nuser=${user.name}\nitem=$itemId\nsoundCheck=${SoundWatchdog.lastReport}\n\n$log",
                )
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not send the log with the problem report")
            }
        }

        /** Everyone's reports, newest first (admins) */
        suspend fun all(): List<ProblemReport> =
            withContext(WholphinDispatchers.IO) {
                // Benflix: ask about everyone at the same time instead of one person after another
                val users = api.userApi.getUsers().content
                coroutineScope {
                    users
                        .map { user ->
                            async {
                                try {
                                    displayPreferencesService
                                        .getDisplayPreferences(user.id)
                                        .customPrefs[KEY]
                                        .orEmpty()
                                        .split(LINE)
                                        .mapNotNull { decode(it, user.id, user.name.orEmpty()) }
                                } catch (ex: CancellationException) {
                                    throw ex
                                } catch (ex: Exception) {
                                    emptyList()
                                }
                            }
                        }.awaitAll()
                        .flatten()
                        .sortedByDescending { it.at }
                }
            }

        /** Ben fixed it (or it's not a problem): take it off the list */
        suspend fun clear(report: ProblemReport) =
            withContext(WholphinDispatchers.IO) {
                displayPreferencesService.updateDisplayPreferences(report.userId) {
                    val left =
                        get(KEY)
                            .orEmpty()
                            .split(LINE)
                            .filter { it.isNotBlank() && !it.startsWith(report.at.toString() + FIELD) }
                    if (left.isEmpty()) remove(KEY) else put(KEY, left.joinToString(LINE))
                    // Benflix: tell them it's fixed, on their Home
                    val fixed = get(FIXED_KEY).orEmpty().split(LINE).filter { it.isNotBlank() }
                    put(FIXED_KEY, (fixed + (report.at.toString() + FIELD + clean(report.title))).takeLast(MAX_FIXED).joinToString(LINE))
                }
            }

        /** Benflix: "Ben fixed ..." notes waiting on this person's Home (time to title) */
        suspend fun fixedForMe(): List<Pair<Long, String>> =
            withContext(WholphinDispatchers.IO) {
                val user = serverRepository.currentUser ?: return@withContext emptyList()
                displayPreferencesService
                    .getDisplayPreferences(user.id)
                    .customPrefs[FIXED_KEY]
                    .orEmpty()
                    .split(LINE)
                    .mapNotNull { line ->
                        val at = line.substringBefore(FIELD).toLongOrNull() ?: return@mapNotNull null
                        at to line.substringAfter(FIELD)
                    }
            }

        /** Benflix: the person closed the "Ben fixed ..." card */
        suspend fun dismissFixed(at: Long) =
            withContext(WholphinDispatchers.IO) {
                val user = serverRepository.currentUser ?: return@withContext
                displayPreferencesService.updateDisplayPreferences(user.id) {
                    val left =
                        get(FIXED_KEY)
                            .orEmpty()
                            .split(LINE)
                            .filter { it.isNotBlank() && !it.startsWith(at.toString() + FIELD) }
                    if (left.isEmpty()) remove(FIXED_KEY) else put(FIXED_KEY, left.joinToString(LINE))
                }
            }

        private fun clean(text: String) = text.replace(FIELD, " ").replace(LINE, " ").trim()

        private fun decode(
            line: String,
            userId: UUID,
            userName: String,
        ): ProblemReport? {
            val parts = line.split(FIELD)
            if (parts.size < 5) return null
            return ProblemReport(
                at = parts[0].toLongOrNull() ?: return null,
                userId = userId,
                userName = userName,
                itemId = parts[1].toUUIDOrNull(),
                title = parts[2],
                problem = parts[3],
                device = parts[4],
            )
        }

        companion object {
            private const val KEY = "benflix.problems"
            private const val FIXED_KEY = "benflix.fixed"
            private const val MAX_FIXED = 5
            private const val FIELD = "|"
            private const val LINE = "\n"
            private const val MAX_PER_PERSON = 10
            private const val LOG_LINES = 400

            /** The choices on the "What's wrong?" list */
            val PROBLEMS =
                listOf(
                    "Won't play",
                    "No sound",
                    "Sound and picture out of sync",
                    "Wrong or missing subtitles",
                    "Bad picture quality",
                    "Keeps stopping to load",
                    "Wrong language",
                    "Wrong movie or episode",
                    "Something else",
                )
        }
    }

/** Benflix: sends a "Report a problem" pick from any page's menu */
@HiltViewModel
class ReportProblemViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:IoCoroutineScope private val ioScope: CoroutineScope,
        private val problems: BenflixProblems,
    ) : ViewModel() {
        /** Runs on the app-wide scope, so closing the menu doesn't stop it */
        fun report(
            itemId: UUID?,
            title: String,
            problem: String,
        ) {
            ioScope.launch {
                try {
                    problems.report(itemId, title, problem)
                    showToast(context, "Thanks! Ben got a fix request for $title", Toast.LENGTH_LONG)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not send the problem report")
                    showToast(context, "Couldn't send that. Try again in a moment.")
                }
            }
        }
    }
