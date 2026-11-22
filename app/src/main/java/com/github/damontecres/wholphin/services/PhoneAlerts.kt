package com.github.damontecres.wholphin.services

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.github.damontecres.wholphin.MainActivity
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.RestoredSession
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.ui.touch.isTvDevice
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Glowfin phone notifications (Idea Box 26, "notify"): a phone notification when something you asked for
 * arrives, or a new episode of a show you watch lands. They only tell you, like the corner card inside
 * Glowfin. Phones only (TVs have the corner card). Each person picks which ones they get in
 * Settings > Phones and TVs > Phone notifications. Kids never get request ones.
 */
object PhoneAlerts {
    private const val PREFS = "benflix_phone_alerts"
    private const val CHANNEL_REQUESTS = "glowfin_requests"
    private const val CHANNEL_EPISODES = "glowfin_episodes"
    private const val WORK_NAME = "glowfin-phone-alerts"
    private const val ASK_CODE = 4127
    private const val MAX_IDS = 300

    /** What a person gets */
    enum class Choice(
        val words: String,
    ) {
        BOTH("My requests and new episodes"),
        REQUESTS("Only my requests"),
        EPISODES("Only new episodes"),
        OFF("Off"),
        ;

        val requests: Boolean get() = this == BOTH || this == REQUESTS
        val episodes: Boolean get() = this == BOTH || this == EPISODES
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun choice(
        context: Context,
        userId: UUID?,
    ): Choice {
        val saved = prefs(context).getString("choice-$userId", null)
        return Choice.entries.firstOrNull { it.name == saved } ?: Choice.BOTH
    }

    fun setChoice(
        context: Context,
        userId: UUID?,
        choice: Choice,
    ) {
        prefs(context).edit().putString("choice-$userId", choice.name).apply()
    }

    /** The next choice when the setting is pressed (Kids only get new episodes or off) */
    fun next(
        current: Choice,
        kids: Boolean,
    ): Choice =
        if (kids) {
            if (current == Choice.OFF) Choice.EPISODES else Choice.OFF
        } else {
            Choice.entries[(current.ordinal + 1) % Choice.entries.size]
        }

    /** Whether Android lets Glowfin show notifications at all */
    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Asks Android for permission (Android 13 and newer only shows the question once or twice, then stays quiet) */
    fun ask(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !allowed(activity)) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), ASK_CODE)
        }
    }

    /** On a phone: starts the half-hourly look, and asks for permission once per install */
    fun start(activity: Activity) {
        if (isTvDevice(activity)) return
        try {
            val request =
                PeriodicWorkRequestBuilder<PhoneAlertsWorker>(30, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager
                .getInstance(activity)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        } catch (ex: Exception) {
            Timber.w(ex, "Could not start phone notifications")
        }
        val p = prefs(activity)
        if (!p.getBoolean("asked", false)) {
            p.edit().putBoolean("asked", true).apply()
            ask(activity)
        }
    }

    // ---- Requests: the app writes down what you're waiting for, the phone check sees when it lands ----

    /** Called by the Requests check while Glowfin is open: your requests that aren't on Glowfin yet ("MOVIE-123") */
    fun saveWaiting(
        context: Context,
        userId: UUID?,
        keys: List<String>,
    ) {
        if (userId == null) return
        prefs(context).edit().putString("waiting-$userId", keys.take(60).joinToString(",")).apply()
    }

    private fun waiting(
        context: Context,
        userId: UUID,
    ): List<String> =
        prefs(context)
            .getString("waiting-$userId", null)
            .orEmpty()
            .split(",")
            .filter { it.isNotBlank() }

    // ---- Episodes: episode ids already looked at, so each one tells you once ----

    private fun seenEpisodes(
        context: Context,
        userId: UUID,
    ): List<String>? = prefs(context).getString("eps-$userId", null)?.split(",")?.filter { it.isNotBlank() }

    private fun saveSeenEpisodes(
        context: Context,
        userId: UUID,
        ids: List<String>,
    ) {
        prefs(context).edit().putString("eps-$userId", ids.takeLast(MAX_IDS).joinToString(",")).apply()
    }

    private fun channels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REQUESTS, "Your requests", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When something you asked for is on Glowfin"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_EPISODES, "New episodes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a new episode of a show you watch is on Glowfin"
            },
        )
    }

    private fun show(
        context: Context,
        channel: String,
        id: Int,
        title: String,
        line: String,
    ) {
        if (!allowed(context)) return
        channels(context)
        // Tapping it just opens Glowfin
        val open =
            android.app.PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat
                .Builder(context, channel)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(line)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (ex: SecurityException) {
            Timber.w(ex, "Not allowed to show a notification")
        }
    }

    /** One look: arrived requests and new episodes. Returns quietly on any trouble. */
    internal suspend fun check(
        context: Context,
        api: ApiClient,
        userId: UUID,
        library: BenflixLibraryTmdb,
        kids: Boolean,
    ) {
        val choice = choice(context, userId)
        if (choice == Choice.OFF || !allowed(context)) return
        if (choice.requests && !kids) {
            val wait = waiting(context, userId)
            if (wait.isNotEmpty()) {
                val here = library.keys()
                val arrived = wait.filter { it in here }
                arrived.forEach { key ->
                    val name =
                        here[key]?.let { id ->
                            runCatching {
                                api.userLibraryApi
                                    .getItem(id)
                                    .content.name
                            }.getOrNull()
                        } ?: "Something you asked for"
                    show(context, CHANNEL_REQUESTS, key.hashCode(), "$name is ready", "You asked for it, and it's on Glowfin now")
                }
                if (arrived.isNotEmpty()) saveWaiting(context, userId, wait - arrived.toSet())
            }
        }
        if (choice.episodes) {
            val latest =
                api.userLibraryApi
                    .getLatestMedia(
                        GetLatestMediaRequest(
                            includeItemTypes = listOf(BaseItemKind.EPISODE),
                            groupItems = false,
                            limit = 40,
                            enableImages = false,
                        ),
                    ).content
            val ids = latest.map { it.id.toString() }
            val seen = seenEpisodes(context, userId)
            saveSeenEpisodes(context, userId, (seen.orEmpty() + ids).distinct())
            // The first look only writes down what's already there, so nobody gets a pile of old episodes
            if (seen == null) return
            val fresh = latest.filter { it.id.toString() !in seen && it.userData?.played != true && it.seriesId != null }
            fresh
                .groupBy { it.seriesId!! }
                .forEach { (seriesId, eps) ->
                    if (watchesShow(api, userId, seriesId)) {
                        val show = eps.first().seriesName ?: "A show you watch"
                        val line =
                            if (eps.size == 1) {
                                episodeWords(eps.first())
                            } else {
                                "${eps.size} new episodes"
                            }
                        show(context, CHANNEL_EPISODES, seriesId.hashCode(), "New on Glowfin: $show", line)
                    }
                }
        }
    }

    private fun episodeWords(ep: BaseItemDto): String {
        val number =
            if (ep.parentIndexNumber != null && ep.indexNumber != null) "S${ep.parentIndexNumber} E${ep.indexNumber}" else null
        return listOfNotNull(number, ep.name).joinToString(" · ").ifBlank { "A new episode" }
    }

    /** A show you watch = you've watched at least one of its episodes */
    private suspend fun watchesShow(
        api: ApiClient,
        userId: UUID,
        seriesId: UUID,
    ): Boolean =
        try {
            (
                api.itemsApi
                    .getItems(
                        GetItemsRequest(
                            userId = userId,
                            parentId = seriesId,
                            recursive = true,
                            includeItemTypes = listOf(BaseItemKind.EPISODE),
                            isPlayed = true,
                            limit = 1,
                            enableImages = false,
                            enableTotalRecordCount = true,
                        ),
                    ).content.totalRecordCount
            ) > 0
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            false
        }
}

/** The half-hourly look on phones (see [PhoneAlerts]) */
@HiltWorker
class PhoneAlertsWorker
    @AssistedInject
    constructor(
        @Assisted private val context: Context,
        @Assisted workerParams: WorkerParameters,
        private val serverRepository: ServerRepository,
        private val api: ApiClient,
        private val library: BenflixLibraryTmdb,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            try {
                if (api.baseUrl.isNullOrBlank() || api.accessToken.isNullOrBlank()) {
                    if (serverRepository.restoreLastSession() !is RestoredSession.Success) return Result.success()
                }
                val userId = serverRepository.currentUser?.id ?: return Result.success()
                // Kids is kept on this device (see ProfileModes); no request notifications for Kids
                ProfileModes.restoreEarly(context)
                PhoneAlerts.check(context, api, userId, library, kids = KidsMode.isActive)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Phone notifications check")
            }
            return Result.success()
        }
    }
