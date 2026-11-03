package com.github.damontecres.wholphin.util

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import com.github.damontecres.wholphin.BuildConfig
import timber.log.Timber
import kotlin.system.exitProcess

/**
 * Benflix: some older TVs (seen on a TCL with Android 9) don't close Benflix while an update installs.
 * The old copy keeps running but starts reading pictures and text from the new copy, which are in
 * different places, so it shows black bars and then crashes ("String resource ID not found"),
 * even after you open it again.
 *
 * This notices when the running app is older (or newer) than the installed one and starts a
 * fresh copy instead, quietly, without the crash report pop-up.
 */
object StaleProcessGuard {
    @Volatile
    private var resumed = 0

    @Volatile
    private var restarting = false

    fun install(app: Application) {
        // 1) every time a screen opens or comes back, check we're still the installed version
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) {
                    if (isStale(activity)) restart(activity, relaunch = true)
                }

                override fun onActivityResumed(activity: Activity) {
                    resumed++
                    if (isStale(activity)) restart(activity, relaunch = true)
                }

                override fun onActivityPaused(activity: Activity) {
                    resumed = (resumed - 1).coerceAtLeast(0)
                }

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )

        // 2) the update just finished installing while this old copy is still running
        try {
            val filter =
                IntentFilter(Intent.ACTION_PACKAGE_REPLACED).apply {
                    addDataScheme("package")
                }
            ContextCompat.registerReceiver(
                app,
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context,
                        intent: Intent,
                    ) {
                        if (intent.data?.schemeSpecificPart != context.packageName) return
                        if (isStale(context)) restart(context, relaunch = resumed > 0)
                    }
                },
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
        } catch (ex: Exception) {
            Timber.w(ex, "Could not watch for updates")
        }

        // 3) a crash in an old copy after an update: start fresh instead of showing the crash report
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val stale =
                try {
                    isStale(app)
                } catch (_: Throwable) {
                    false
                }
            if (stale) {
                Log.w("Benflix", "Old copy of Benflix crashed after an update, starting fresh", error)
                restart(app, relaunch = resumed > 0)
            } else {
                previous?.uncaughtException(thread, error)
            }
        }
    }

    /** true when the installed Benflix is a different version than the code that's running */
    fun isStale(context: Context): Boolean =
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            PackageInfoCompat.getLongVersionCode(info) != BuildConfig.VERSION_CODE.toLong()
        } catch (ex: Exception) {
            false
        }

    private fun restart(
        context: Context,
        relaunch: Boolean,
    ) {
        if (restarting) return
        restarting = true
        Log.i("Benflix", "Benflix was updated while open; starting the new version (relaunch=$relaunch)")
        if (relaunch) {
            try {
                val pm = context.packageManager
                val launch =
                    pm.getLeanbackLaunchIntentForPackage(context.packageName)
                        ?: pm.getLaunchIntentForPackage(context.packageName)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    context.startActivity(launch)
                }
            } catch (ex: Throwable) {
                Log.w("Benflix", "Could not reopen Benflix", ex)
            }
        }
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }
}
