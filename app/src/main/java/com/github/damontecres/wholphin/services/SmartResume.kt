package com.github.damontecres.wholphin.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.github.damontecres.wholphin.preferences.BenflixPreferences
import com.github.damontecres.wholphin.preferences.MobileDataQuality
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Benflix Smart resume: when you come back to something you stopped part way through,
 * start a little earlier so you can get back into it.
 * Less than a day away: 5 seconds. A day or more: 15 seconds. A week or more: 30 seconds.
 */
object SmartResume {
    /** @param lastPlayed when it was last played, in UTC (that's what Jellyfin sends) */
    fun rewindMs(
        lastPlayed: LocalDateTime?,
        now: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC),
    ): Long {
        if (lastPlayed == null) return 5_000L
        val away = Duration.between(lastPlayed, now)
        return when {
            away.isNegative -> 0L
            away.toMinutes() < 2 -> 0L
            away.toDays() >= 7 -> 30_000L
            away.toDays() >= 1 -> 15_000L
            else -> 5_000L
        }
    }
}

/**
 * Benflix "Quality on mobile data": when a phone is on mobile data (not Wi-Fi or a cable),
 * cap the streaming quality so it uses less data and buffers less.
 */
object BenflixMobileData {
    private const val SAVE_DATA_BITRATE = 1_500_000L
    private const val BALANCED_BITRATE = 3_000_000L

    /** True when the device is using mobile data right now */
    fun onMobileData(context: Context): Boolean =
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
            if (caps == null) {
                false
            } else {
                val wifiOrCable =
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                !wifiOrCable && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            }
        } catch (ex: Exception) {
            // Can't tell (for example no permission), so don't limit anything
            false
        }

    /** The highest bitrate to use right now, or null for no limit */
    fun bitrateCap(
        context: Context,
        prefs: BenflixPreferences,
    ): Long? {
        if (!onMobileData(context)) return null
        return when (prefs.mobileDataQuality) {
            MobileDataQuality.MOBILE_SAVE_DATA -> SAVE_DATA_BITRATE
            MobileDataQuality.MOBILE_BEST -> null
            else -> BALANCED_BITRATE
        }
    }
}
