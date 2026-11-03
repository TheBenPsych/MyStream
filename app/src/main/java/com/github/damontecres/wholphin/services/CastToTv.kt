package com.github.damontecres.wholphin.services

import android.content.Context
import android.net.Uri
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.github.damontecres.wholphin.MainActivity
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.touch.isTvDevice
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

/** A Chromecast or TV with Chromecast built in, found on the home Wi-Fi */
data class CastScreen(
    val id: String,
    val name: String,
)

/** What's casting right now, for the "Casting to…" line */
data class CastingNow(
    val screen: String,
    val title: String,
)

/**
 * Benflix "Cast": sends a movie or episode from the phone to any TV with Chromecast built in, even one
 * without Benflix on it (a hotel TV, grandma's TV). Uses Google's own receiver on the TV, so nothing has
 * to be installed there. Tower converts the video to a format every Chromecast plays (H.264 + AAC).
 *
 * The phone tells Jellyfin how far along the TV is, so Continue watching stays right. The pause / stop
 * controls are in the phone's notification and on its lock screen (Google's cast notification).
 *
 * Phones only: TVs and phones without Google Play services just don't show cast screens.
 */
@Singleton
class CastToTv
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val imageUrlService: ImageUrlService,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + WholphinDispatchers.IO)
        private var castContext: CastContext? = null
        private val selector by lazy {
            MediaRouteSelector
                .Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                .build()
        }
        private val router by lazy { MediaRouter.getInstance(context) }

        private val _screens = MutableStateFlow<List<CastScreen>>(emptyList())

        /** Cast screens found so far (only while [startLooking] is on) */
        val screens: StateFlow<List<CastScreen>> = _screens

        private val _casting = MutableStateFlow<CastingNow?>(null)
        val casting: StateFlow<CastingNow?> = _casting

        /** What to play once the TV has connected */
        private var waiting: Pending? = null

        private data class Pending(
            val item: BaseItem,
            val position: Duration,
            val screen: String,
        )

        /** What's playing, for telling Jellyfin how far along it is */
        private var playing: Playing? = null

        private data class Playing(
            val itemId: UUID,
            val playSessionId: String,
            var positionMs: Long = 0,
            var paused: Boolean = false,
        )

        private val routeCallback =
            object : MediaRouter.Callback() {
                override fun onRouteAdded(
                    router: MediaRouter,
                    route: MediaRouter.RouteInfo,
                ) = refresh()

                override fun onRouteRemoved(
                    router: MediaRouter,
                    route: MediaRouter.RouteInfo,
                ) = refresh()

                override fun onRouteChanged(
                    router: MediaRouter,
                    route: MediaRouter.RouteInfo,
                ) = refresh()
            }

        /** True on phones with Google Play services; set up the first time it's asked */
        val available: Boolean
            get() = ensureCastContext() != null

        private fun ensureCastContext(): CastContext? {
            castContext?.let { return it }
            if (isTvDevice(context)) return null
            return try {
                @Suppress("DEPRECATION")
                CastContext.getSharedInstance(context).also {
                    castContext = it
                    it.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
                }
            } catch (e: Throwable) {
                // No Google Play services (or an old version): no casting on this phone
                Timber.i("Casting not available: %s", e.javaClass.simpleName)
                null
            }
        }

        /** Starts looking for cast screens on the Wi-Fi. Call [stopLooking] when the list closes. Main thread. */
        fun startLooking() {
            if (ensureCastContext() == null) return
            try {
                router.addCallback(selector, routeCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
                refresh()
            } catch (e: Exception) {
                Timber.w(e, "Couldn't look for cast screens")
            }
        }

        fun stopLooking() {
            try {
                router.removeCallback(routeCallback)
            } catch (_: Exception) {
            }
        }

        private fun refresh() {
            _screens.value =
                router.routes
                    .filter { !it.isDefault && it.isEnabled && it.matchesSelector(selector) }
                    .map { CastScreen(it.id, it.name) }
        }

        /** Connects to [screen] and plays [item] there from [position]. Main thread. */
        fun play(
            screen: CastScreen,
            item: BaseItem,
            position: Duration,
        ): Boolean {
            val cast = ensureCastContext() ?: return false
            val route = router.routes.firstOrNull { it.id == screen.id } ?: return false
            val session = cast.sessionManager.currentCastSession
            return if (session != null && session.isConnected && router.selectedRoute.id == route.id) {
                load(session, item, position, screen.name)
                true
            } else {
                waiting = Pending(item, position, screen.name)
                router.selectRoute(route)
                true
            }
        }

        /** Stops casting and lets the TV go back to what it was doing */
        fun stop() {
            try {
                castContext?.sessionManager?.endCurrentSession(true)
            } catch (e: Exception) {
                Timber.w(e, "Couldn't stop casting")
            }
        }

        private fun load(
            session: CastSession,
            item: BaseItem,
            position: Duration,
            screenName: String,
        ) {
            val client = session.remoteMediaClient ?: return
            val base = serverRepository.currentServer?.url?.trimEnd('/') ?: return
            val token = serverRepository.currentUser?.accessToken ?: return
            val playSessionId = UUID.randomUUID().toString().replace("-", "")
            val itemId = item.id.toString().replace("-", "")
            val url =
                Uri
                    .parse("$base/Videos/$itemId/master.m3u8")
                    .buildUpon()
                    .appendQueryParameter("MediaSourceId", itemId)
                    .appendQueryParameter("DeviceId", "${api.deviceInfo.id}-cast")
                    .appendQueryParameter("PlaySessionId", playSessionId)
                    .appendQueryParameter("VideoCodec", "h264")
                    .appendQueryParameter("AudioCodec", "aac,mp3")
                    .appendQueryParameter("TranscodingMaxAudioChannels", "2")
                    .appendQueryParameter("MaxStreamingBitrate", MAX_BITRATE.toString())
                    .appendQueryParameter("SegmentContainer", "ts")
                    .appendQueryParameter("BreakOnNonKeyFrames", "true")
                    .appendQueryParameter("ApiKey", token)
                    .build()
                    .toString()
            val isEpisode = item.type == BaseItemKind.EPISODE
            val metadata =
                MediaMetadata(if (isEpisode) MediaMetadata.MEDIA_TYPE_TV_SHOW else MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                    putString(MediaMetadata.KEY_TITLE, item.name ?: "Benflix")
                    if (isEpisode) item.data.seriesName?.let { putString(MediaMetadata.KEY_SERIES_TITLE, it) }
                    imageUrlService.getItemImageUrl(item, ImageType.BACKDROP)?.let { addImage(WebImage(Uri.parse(it))) }
                }
            val info =
                MediaInfo
                    .Builder(url)
                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                    .setContentType("application/x-mpegURL")
                    .setMetadata(metadata)
                    .build()
            val request =
                MediaLoadRequestData
                    .Builder()
                    .setMediaInfo(info)
                    .setAutoplay(true)
                    .setCurrentTime(position.inWholeMilliseconds)
                    .build()
            client.load(request)
            client.registerCallback(mediaCallback)
            client.addProgressListener(progressListener, REPORT_EVERY_MS)
            val now = Playing(item.id, playSessionId, positionMs = position.inWholeMilliseconds)
            playing = now
            _casting.value = CastingNow(screenName, item.name ?: "")
            scope.launch {
                try {
                    api.playStateApi.reportPlaybackStart(
                        PlaybackStartInfo(
                            canSeek = true,
                            itemId = now.itemId,
                            isPaused = false,
                            isMuted = false,
                            playMethod = PlayMethod.TRANSCODE,
                            repeatMode = RepeatMode.REPEAT_NONE,
                            playbackOrder = PlaybackOrder.DEFAULT,
                            playSessionId = now.playSessionId,
                            positionTicks = now.positionMs * 10_000,
                        ),
                    )
                } catch (e: Exception) {
                    Timber.w(e, "Couldn't tell Jellyfin casting started")
                }
            }
        }

        private val progressListener =
            RemoteMediaClient.ProgressListener { progressMs, _ ->
                val now = playing ?: return@ProgressListener
                if (progressMs <= 0) return@ProgressListener
                now.positionMs = progressMs
                scope.launch {
                    try {
                        api.playStateApi.reportPlaybackProgress(
                            PlaybackProgressInfo(
                                canSeek = true,
                                itemId = now.itemId,
                                isPaused = now.paused,
                                isMuted = false,
                                playMethod = PlayMethod.TRANSCODE,
                                repeatMode = RepeatMode.REPEAT_NONE,
                                playbackOrder = PlaybackOrder.DEFAULT,
                                playSessionId = now.playSessionId,
                                positionTicks = progressMs * 10_000,
                            ),
                        )
                    } catch (e: Exception) {
                        Timber.i("Casting progress not sent: %s", e.javaClass.simpleName)
                    }
                }
            }

        private val mediaCallback =
            object : RemoteMediaClient.Callback() {
                override fun onStatusUpdated() {
                    val client = castContext?.sessionManager?.currentCastSession?.remoteMediaClient ?: return
                    playing?.paused = client.isPaused
                }
            }

        private fun reportStopped() {
            val now = playing ?: return
            playing = null
            _casting.value = null
            scope.launch {
                try {
                    api.playStateApi.reportPlaybackStopped(
                        PlaybackStopInfo(
                            itemId = now.itemId,
                            failed = false,
                            playSessionId = now.playSessionId,
                            positionTicks = now.positionMs * 10_000,
                        ),
                    )
                } catch (e: Exception) {
                    Timber.w(e, "Couldn't tell Jellyfin casting stopped")
                }
            }
        }

        private val sessionListener =
            object : SessionManagerListener<CastSession> {
                override fun onSessionStarted(
                    session: CastSession,
                    sessionId: String,
                ) {
                    val next = waiting ?: return
                    waiting = null
                    load(session, next.item, next.position, next.screen)
                }

                override fun onSessionResumed(
                    session: CastSession,
                    wasSuspended: Boolean,
                ) {
                    val next = waiting ?: return
                    waiting = null
                    load(session, next.item, next.position, next.screen)
                }

                override fun onSessionEnded(
                    session: CastSession,
                    error: Int,
                ) = reportStopped()

                override fun onSessionStartFailed(
                    session: CastSession,
                    error: Int,
                ) {
                    Timber.w("Casting couldn't start (%s)", error)
                    waiting = null
                }

                override fun onSessionStarting(session: CastSession) {}

                override fun onSessionEnding(session: CastSession) {}

                override fun onSessionResuming(
                    session: CastSession,
                    sessionId: String,
                ) {}

                override fun onSessionResumeFailed(
                    session: CastSession,
                    error: Int,
                ) {}

                override fun onSessionSuspended(
                    session: CastSession,
                    reason: Int,
                ) {}
            }

        companion object {
            /** Plenty for 1080p on a Chromecast, and fits a normal home upload when casting away from home */
            private const val MAX_BITRATE = 10_000_000
            private const val REPORT_EVERY_MS = 10_000L
        }
    }

/** Google's cast settings for Benflix: the standard receiver, and the notification opens Benflix */
class BenflixCastOptions : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions
            .Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setCastMediaOptions(
                CastMediaOptions
                    .Builder()
                    .setNotificationOptions(
                        NotificationOptions
                            .Builder()
                            .setTargetActivityClassName(MainActivity::class.java.name)
                            .build(),
                    ).build(),
            ).build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
