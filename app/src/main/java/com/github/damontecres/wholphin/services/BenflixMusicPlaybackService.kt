package com.github.damontecres.wholphin.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Benflix music: keeps songs playing while the phone's screen is off or Benflix is in the background,
 * with the music controls on the lock screen (approved m4-lock: heart, previous, pause, next, stop).
 * It only runs while music plays; [MusicService] starts and stops it.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class BenflixMusicPlaybackService : MediaLibraryService() {
    @Inject
    lateinit var musicService: MusicService

    override fun onCreate() {
        super.onCreate()
        // Benflix: the session is made here too, so Android Auto can open Benflix music before anything plays
        addSession(musicService.ensureSession())
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        musicService.ensureSession() as? MediaLibrarySession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping Benflix away while paused ends the music (playing music keeps going)
        if (musicService.mediaSession?.player?.playWhenReady != true) {
            stopSelf()
        }
    }

    companion object {
        fun start(context: Context) {
            try {
                context.startService(Intent(context, BenflixMusicPlaybackService::class.java))
            } catch (ex: Exception) {
                // Android can refuse while Benflix is in the background; the music still plays, just without lock screen controls
                Timber.w(ex, "Couldn't start the music service")
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, BenflixMusicPlaybackService::class.java))
            } catch (ex: Exception) {
                Timber.w(ex, "Couldn't stop the music service")
            }
        }
    }
}

/** The lock screen's heart and stop buttons */
@OptIn(UnstableApi::class)
object MusicSessionButtons {
    val LIKE = SessionCommand("benflix.music.like", Bundle.EMPTY)
    val STOP = SessionCommand("benflix.music.stop", Bundle.EMPTY)

    fun buttons(liked: Boolean): List<CommandButton> =
        listOf(
            CommandButton
                .Builder(if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
                .setDisplayName(if (liked) "Liked" else "Like")
                .setSessionCommand(LIKE)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
            CommandButton
                .Builder(CommandButton.ICON_STOP)
                .setDisplayName("Stop")
                .setSessionCommand(STOP)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
        )
}

/**
 * Like [TrustAllControllersCallback] (every controller can play and pause), plus the heart and stop buttons, and the
 * music lists for Android Auto ([MusicCarLibrary])
 */
@OptIn(UnstableApi::class)
class MusicSessionCallback(
    private val onLike: () -> Unit,
    private val onStop: () -> Unit,
    private val car: MusicCarLibrary,
    private val playFromCar: suspend (MusicCarLibrary.ToPlay) -> List<MediaItem>,
) : MediaLibrarySession.Callback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult =
        MediaSession.ConnectionResult
            .AcceptedResultBuilder(session)
            .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
            .setAvailableSessionCommands(
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                    .buildUpon()
                    .add(MusicSessionButtons.LIKE)
                    .add(MusicSessionButtons.STOP)
                    .build(),
            ).build()

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        scope.launch {
            when (customCommand.customAction) {
                MusicSessionButtons.LIKE.customAction -> onLike()
                MusicSessionButtons.STOP.customAction -> onStop()
            }
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(LibraryResult.ofItem(car.root(), params))

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        scope.launch {
            try {
                val all = car.children(parentId)
                val from = (page.coerceAtLeast(0).toLong() * pageSize.coerceAtLeast(1)).coerceAtMost(all.size.toLong()).toInt()
                val to = (from + pageSize.coerceAtLeast(1)).coerceAtMost(all.size)
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params))
            } catch (e: Exception) {
                Timber.w(e, "Couldn't load music for the car: %s", parentId)
                future.set(LibraryResult.ofError(SessionError.ERROR_IO))
            }
        }
        return future
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val picked = mediaItems.getOrNull(startIndex.coerceAtLeast(0)) ?: mediaItems.firstOrNull()
        // Songs that already know where to play from (Benflix's own) go straight in
        if (picked == null || picked.localConfiguration != null) {
            return super.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
        }
        val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        scope.launch {
            try {
                val toPlay = car.toPlay(picked.mediaId)
                val items = toPlay?.let { playFromCar(it) }.orEmpty()
                if (items.isEmpty()) {
                    future.setException(UnsupportedOperationException("Nothing to play for ${picked.mediaId}"))
                } else {
                    future.set(MediaSession.MediaItemsWithStartPosition(items, 0, 0L))
                }
            } catch (e: Exception) {
                Timber.w(e, "Couldn't play music from the car")
                future.setException(e)
            }
        }
        return future
    }
}
