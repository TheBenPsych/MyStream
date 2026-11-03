package com.github.damontecres.wholphin.services

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.media3.common.MimeTypes
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.ScreensaverPreference
import com.github.damontecres.wholphin.preferences.updateScreensaverPreferences
import com.github.damontecres.wholphin.ui.touch.isTvDevice
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.profile.MediaCodecCapabilitiesTest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.ceil

/** What the Benflix device check found out about this TV or phone */
data class DeviceReport(
    val isTv: Boolean,
    /** Memory, rounded up to the next half GB (a "2 GB" TV reports a little under 2) */
    val memoryGb: Float,
    val lowRamDevice: Boolean,
    val cores: Int,
    val sdk: Int,
    val androidVersion: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val maxRefreshHz: Int,
    val hevc: Boolean,
    val av1: Boolean,
    val plays4k: Boolean,
    val hdr: Boolean,
) {
    /** Old or slow: under 2 GB memory, Android 9 or older, or no HEVC (most of a modern server needs it) */
    val weak: Boolean
        get() = lowRamDevice || memoryGb < WEAK_MEMORY_GB || sdk <= Build.VERSION_CODES.P || !hevc

    /** Lite mode keeps it smooth: weak devices, 2 GB ones and anything with under 4 cores */
    val wantsLite: Boolean
        get() = weak || memoryGb < LITE_MEMORY_GB || cores < MIN_CORES

    val deviceWord: String get() = if (isTv) "TV" else "phone"

    /** "2 GB memory, 4 cores, Android 9, a Full HD screen at 60 Hz" */
    fun hardwareLine(): String {
        val memory = if (memoryGb % 1f == 0f) "${memoryGb.toInt()} GB" else "$memoryGb GB"
        val screen =
            when {
                screenWidth >= 3840 || screenHeight >= 3840 -> "a 4K screen"
                screenWidth >= 1920 || screenHeight >= 1920 -> "a Full HD screen"
                screenWidth >= 1280 || screenHeight >= 1280 -> "an HD screen"
                screenWidth > 0 -> "a ${screenWidth}x$screenHeight screen"
                else -> null
            }
        val refresh = if (maxRefreshHz > 0) " at $maxRefreshHz Hz" else ""
        return listOfNotNull(
            "$memory memory",
            "$cores cores",
            "Android $androidVersion",
            screen?.let { it + refresh },
        ).joinToString(", ")
    }

    /** "Plays 4K, HDR and HEVC. Doesn't play AV1." */
    fun playsLine(): String {
        val all = listOf("4K" to plays4k, "HDR" to hdr, "HEVC" to hevc, "AV1" to av1)
        val yes = all.filter { it.second }.map { it.first }
        val no = all.filterNot { it.second }.map { it.first }
        return listOfNotNull(
            if (yes.isEmpty()) "Plays HD video" else "Plays ${yes.joinWords()}",
            if (no.isEmpty()) null else "Doesn't play ${no.joinWords()}",
        ).joinToString(". ") + "."
    }

    private fun List<String>.joinWords(): String = if (size <= 1) joinToString("") else dropLast(1).joinToString(", ") + " and " + last()

    companion object {
        const val WEAK_MEMORY_GB = 2f
        const val LITE_MEMORY_GB = 3f
        const val MIN_CORES = 4
    }
}

/** What the device check set (or left alone because the person picked it themselves) */
data class DeviceChoices(
    val lite: Boolean,
    val liteByYou: Boolean,
    val screensaverMotion: Boolean,
    val screensaverMotionByYou: Boolean,
)

/**
 * Benflix device check: looks at this TV or phone (memory, cores, Android version, screen, which
 * video it can play) and sets the per-device performance settings to suit it: Lite mode (no trailer
 * previews or clip preloading, still backgrounds) and the screensaver's slow motion.
 *
 * It runs in the welcome setup, and again quietly after every app update. A setting the person changed
 * themselves is never changed back: Lite mode remembers whether it was set by this check or by hand, and
 * the screensaver remembers the value this check last set (anything else means they changed it).
 * These are device settings, kept only on this device (BenflixSettingsSync never syncs them).
 */
object BenflixDeviceCheck {
    private const val PREFS = "benflix_device_check"
    private const val AUTO_MOTION = "auto_screensaver_animate"
    private const val USER_MOTION = "user_screensaver_animate"
    private const val GIB = 1024f * 1024f * 1024f

    /** Looks at this device. Takes a moment (it lists the video decoders), so call it off the main thread */
    fun detect(
        context: Context,
        codecs: MediaCodecCapabilitiesTest,
    ): DeviceReport {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(info)
        val memoryGb = ceil(info.totalMem / GIB * 2f) / 2f
        val display = ContextCompat.getDisplayOrDefault(context)
        val modes = display.supportedModes.orEmpty()
        val biggest = modes.maxByOrNull { it.physicalWidth.toLong() * it.physicalHeight }
        val maxHz = (modes.maxOfOrNull { it.refreshRate } ?: display.refreshRate).toInt()
        val displayHdr =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                @Suppress("DEPRECATION")
                display.hdrCapabilities
                    ?.supportedHdrTypes
                    ?.isNotEmpty() == true
            } else {
                false
            }
        val hevc = codecs.supportsHevc()
        val av1 = codecs.supportsAV1()
        val plays4k =
            listOf(MimeTypes.VIDEO_H265, MimeTypes.VIDEO_H264, MimeTypes.VIDEO_AV1).any {
                val size = codecs.getMaxResolution(it)
                maxOf(size.width, size.height) >= 3840 && minOf(size.width, size.height) >= 2160
            }
        val hdrDecoder = codecs.supportsHevcHDR10() || codecs.supportsAV1HDR10() || codecs.supportsHevcDolbyVision()
        return DeviceReport(
            isTv = isTvDevice(context),
            memoryGb = memoryGb,
            lowRamDevice = am?.isLowRamDevice == true,
            cores = Runtime.getRuntime().availableProcessors(),
            sdk = Build.VERSION.SDK_INT,
            androidVersion = Build.VERSION.RELEASE.orEmpty(),
            screenWidth = biggest?.physicalWidth ?: 0,
            screenHeight = biggest?.physicalHeight ?: 0,
            maxRefreshHz = maxHz,
            hevc = hevc,
            av1 = av1,
            plays4k = plays4k,
            hdr = displayHdr && hdrDecoder,
        )
    }

    /** Sets this device's performance settings for [report], leaving alone anything the person set themselves */
    suspend fun apply(
        context: Context,
        preferences: DataStore<AppPreferences>,
        report: DeviceReport,
    ): DeviceChoices {
        val lite = report.wantsLite
        val liteSet = LiteMode.setAuto(context, lite)
        val liteNow = LiteMode.isOn(context)

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current =
            preferences.data
                .first()
                .interfacePreferences.screensaverPreference.animate
        val wantedMotion = !lite
        val byYou =
            prefs.getBoolean(USER_MOTION, false) ||
                if (prefs.contains(AUTO_MOTION)) {
                    prefs.getBoolean(AUTO_MOTION, true) != current
                } else {
                    current != ScreensaverPreference.Animate.defaultValue
                }
        val motion =
            if (byYou) {
                prefs.edit { putBoolean(USER_MOTION, true) }
                current
            } else {
                if (current != wantedMotion) {
                    preferences.updateData { it.updateScreensaverPreferences { animate = wantedMotion } }
                }
                prefs.edit { putBoolean(AUTO_MOTION, wantedMotion) }
                wantedMotion
            }
        return DeviceChoices(
            lite = liteNow,
            liteByYou = !liteSet,
            screensaverMotion = motion,
            screensaverMotionByYou = byYou,
        )
    }

    /** After an app update: look again and re-apply, quietly (no screen) */
    suspend fun rerun(
        context: Context,
        preferences: DataStore<AppPreferences>,
        codecs: MediaCodecCapabilitiesTest,
    ) {
        try {
            withContext(WholphinDispatchers.IO) {
                val report = detect(context, codecs)
                val choices = apply(context, preferences, report)
                Timber.i("Device check after update: %s -> %s", report, choices)
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Timber.w(ex, "Device check after update failed")
        }
    }
}
