package com.github.damontecres.wholphin.services

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Updates that arrive already sped up (Ben 2026-11-18: "like games processing shaders before you play").
 *
 * After a normal update Android runs Benflix slowly until it has prepared it, which it only does while the TV
 * sleeps, so the app felt slower than stock Wholphin for most of a day after every update. This installs the update
 * together with its speed-up list (the profile that's already inside the update file), and Android prepares the app
 * during the install instead. The install screen takes a little longer; the app is fast from the first launch.
 *
 * Anything going wrong falls back to the usual install screen, and after one failed try it isn't used again.
 */
object SpeedUpInstall {
    private const val KEY_OFF = "benflix.speedUpInstall.off"
    private const val PROFILE = "assets/dexopt/baseline.prof"
    private const val PROFILE_META = "assets/dexopt/baseline.profm"

    /** The update file being installed, for the usual install screen if this way fails */
    @Volatile
    internal var fallbackUri: Uri? = null

    /** Android 9 and newer can take the speed-up list with an update; older TVs use the usual install screen */
    fun canUse(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            !PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_OFF, false)

    internal fun turnOff(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit { putBoolean(KEY_OFF, true) }
    }

    /**
     * Hands the update and its speed-up list to Android. True once Android has it (its install screen shows
     * next); false if it couldn't, and the usual install screen should open instead.
     */
    @SuppressLint("RequestInstallPackagesPolicy")
    suspend fun start(
        context: Context,
        apk: Uri,
    ): Boolean =
        withContext(WholphinDispatchers.IO) {
            if (!canUse(context)) return@withContext false
            val installer = context.packageManager.packageInstaller
            var sessionId = -1
            try {
                val dm = speedUpFile(context, apk)
                if (dm == null) {
                    Timber.i("Speed-up install: no speed-up list in this update, using the usual install")
                    return@withContext false
                }
                // Only one at a time: an earlier try that never finished is dropped
                installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
                val params =
                    PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                        setAppPackageName(context.packageName)
                    }
                sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    val input = context.contentResolver.openInputStream(apk) ?: return@withContext false
                    input.use { inp ->
                        session.openWrite("base.apk", 0, -1).use { out ->
                            inp.copyTo(out, 256 * 1024)
                            session.fsync(out)
                        }
                    }
                    // Same name as the update file, so Android knows the list belongs to it
                    session.openWrite("base.dm", 0, dm.size.toLong()).use { out ->
                        out.write(dm)
                        session.fsync(out)
                    }
                    fallbackUri = apk.takeIf { it.scheme != "file" }
                    val flags =
                        PendingIntent.FLAG_UPDATE_CURRENT or
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                    val done =
                        PendingIntent.getBroadcast(
                            context,
                            sessionId,
                            Intent(context, SpeedUpInstallReceiver::class.java).setPackage(context.packageName),
                            flags,
                        )
                    session.commit(done.intentSender)
                }
                Timber.i("Speed-up install: handed to Android")
                true
            } catch (ex: Exception) {
                Timber.w(ex, "Speed-up install didn't work, using the usual install")
                if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
                false
            }
        }

    /**
     * Ben 2026-11-18: after a fresh install (Downloader) Android hasn't prepared Benflix yet either. This puts the
     * installed copy back in, same version, together with its speed-up list, so Android prepares it now.
     */
    suspend fun speedUpNow(context: Context): Boolean {
        val apk = Uri.fromFile(java.io.File(context.applicationInfo.sourceDir))
        // Can't fall back to the usual install screen for the app's own copy, so don't switch this way off
        PreferenceManager.getDefaultSharedPreferences(context).edit { remove(KEY_OFF) }
        val handed = start(context, apk)
        fallbackUri = null
        return handed
    }

    /** The speed-up list taken out of the update file, packed the way Android reads it, or null if it has none */
    private fun speedUpFile(
        context: Context,
        apk: Uri,
    ): ByteArray? {
        var profile: ByteArray? = null
        var meta: ByteArray? = null
        context.contentResolver.openInputStream(apk)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when (entry.name) {
                        PROFILE -> profile = zip.readBytes()
                        PROFILE_META -> meta = zip.readBytes()
                    }
                    if (profile != null && meta != null) break
                }
            }
        }
        val prof = profile ?: return null
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("primary.prof"))
            zip.write(prof)
            zip.closeEntry()
            meta?.let {
                zip.putNextEntry(ZipEntry("primary.profm"))
                zip.write(it)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
}

/** Hears back from Android about a [SpeedUpInstall]: shows its install screen, or falls back to the usual one */
class SpeedUpInstallReceiver : BroadcastReceiver() {
    @SuppressLint("UnsafeIntentLaunch")
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }
                try {
                    // Coming back from the installer isn't "away" (no Who's watching)
                    ResumeTimer.expectedTrip = true
                    context.startActivity(confirm!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (ex: Exception) {
                    Timber.w(ex, "Speed-up install: couldn't show Android's install screen")
                    fallBack(context)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Timber.i("Speed-up install: installed")
            }

            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                // Cancelled on the install screen: same as cancelling the usual one
                Timber.i("Speed-up install: cancelled")
            }

            else -> {
                Timber.w(
                    "Speed-up install failed (%s): %s",
                    status,
                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
                )
                SpeedUpInstall.turnOff(context)
                fallBack(context)
            }
        }
    }

    /** The usual install screen for the same update file */
    private fun fallBack(context: Context) {
        val uri = SpeedUpInstall.fallbackUri ?: return
        try {
            ResumeTimer.expectedTrip = true
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (ex: Exception) {
            Timber.w(ex, "Speed-up install: couldn't open the usual install screen either")
        }
    }
}
