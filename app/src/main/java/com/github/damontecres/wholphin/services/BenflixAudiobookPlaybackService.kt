package com.github.damontecres.wholphin.services

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Benflix Books: keeps an audiobook playing with the phone's screen off or Benflix in the background, with
 * the controls on the lock screen. It runs while an audiobook plays ([AudiobookService] starts and stops it), and
 * when a car (Android Auto) asks for the audiobook list (Idea Box 29).
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class BenflixAudiobookPlaybackService : MediaLibraryService() {
    @Inject
    lateinit var audiobooks: AudiobookService

    override fun onCreate() {
        super.onCreate()
        addSession(audiobooks.ensureSession())
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        audiobooks.ensureSession() as? MediaLibrarySession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping Benflix away while paused ends the audiobook (a playing one keeps going)
        if (audiobooks.mediaSession?.player?.playWhenReady != true) {
            stopSelf()
        }
    }

    companion object {
        fun start(context: Context) {
            try {
                context.startService(Intent(context, BenflixAudiobookPlaybackService::class.java))
            } catch (ex: Exception) {
                Timber.w(ex, "Couldn't start the audiobook service")
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, BenflixAudiobookPlaybackService::class.java))
            } catch (ex: Exception) {
                Timber.w(ex, "Couldn't stop the audiobook service")
            }
        }
    }
}
