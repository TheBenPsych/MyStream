package com.github.damontecres.wholphin.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import kotlin.concurrent.thread

/**
 * Android tells the app the moment an update finished installing. The downloaded update file isn't needed any
 * more, so it's deleted right then, instead of sitting in Downloads until the app is next opened.
 */
@AndroidEntryPoint
class UpdateInstalledReceiver : BroadcastReceiver() {
    @Inject
    lateinit var updateChecker: UpdateChecker

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        thread(name = "update-cleanup") {
            try {
                updateChecker.cleanupLeftoverDownload()
            } catch (ex: Exception) {
                Timber.w(ex, "Could not delete the installed update file")
            } finally {
                pending.finish()
            }
        }
    }
}
