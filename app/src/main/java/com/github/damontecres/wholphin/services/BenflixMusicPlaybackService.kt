package com.github.damontecres.wholphin.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
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
class BenflixMusicPlaybackService : MediaSessionService() {
    @Inject
    lateinit var musicService: MusicService

    override fun onCreate() {
        super.onCreate()
        val session = musicService.mediaSession
        if (session == null) {
            stopSelf()
        } else {
            addSession(session)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = musicService.mediaSession

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
 * Like [TrustAllControllersCallback] (every controller can play and pause), plus the heart and stop buttons
 */
@OptIn(UnstableApi::class)
class MusicSessionCallback(
    private val onLike: () -> Unit,
    private val onStop: () -> Unit,
) : MediaSession.Callback {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult =
        MediaSession.ConnectionResult
            .AcceptedResultBuilder(session)
            .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
            .setAvailableSessionCommands(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
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
}
