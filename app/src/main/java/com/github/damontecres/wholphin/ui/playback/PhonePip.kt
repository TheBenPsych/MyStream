package com.github.damontecres.wholphin.ui.playback

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import timber.log.Timber

/**
 * Benflix: on phones, a movie or show keeps playing in a small window when you go to the home screen or another
 * app (picture-in-picture), like Netflix and the Jellyfin phone app. Never on TVs.
 *
 * The player says when it's ready ([wanted]); MainActivity asks for the small window when the person leaves.
 * Android 12 and up go into it smoothly by themselves.
 */
object PhonePip {
    /** True while a phone is playing a video that can go into the small window */
    @Volatile
    var wanted = false
        private set

    /** True while Benflix is in the small window (the player hides its buttons) */
    var active by mutableStateOf(false)

    private fun supported(activity: Activity): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** The player calls this when it starts or stops playing, with the video's shape */
    fun update(
        activity: Activity?,
        playing: Boolean,
        width: Int,
        height: Int,
    ) {
        wanted = playing
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !supported(activity)) return
        try {
            activity.setPictureInPictureParams(params(playing, width, height))
        } catch (e: Exception) {
            Timber.w(e, "Couldn't set up the small window")
        }
    }

    /** The player is closing: no small window any more */
    fun clear(activity: Activity?) = update(activity, false, 16, 9)

    /** MainActivity.onUserLeaveHint: older phones need to be asked for the small window by hand */
    fun onLeave(activity: Activity) {
        // Android 12 and up go into the small window by themselves
        if (!wanted || Build.VERSION.SDK_INT < Build.VERSION_CODES.O || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        if (!supported(activity)) return
        try {
            activity.enterPictureInPictureMode(params(true, 16, 9))
        } catch (e: Exception) {
            Timber.w(e, "Couldn't open the small window")
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun params(
        playing: Boolean,
        width: Int,
        height: Int,
    ): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        // Android only allows shapes between 1:2.39 and 2.39:1
        val w = if (width > 0) width else 16
        val h = if (height > 0) height else 9
        val ratio = (w.toDouble() / h).coerceIn(0.42, 2.39)
        builder.setAspectRatio(Rational((ratio * 1000).toInt(), 1000))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(playing)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }
}
