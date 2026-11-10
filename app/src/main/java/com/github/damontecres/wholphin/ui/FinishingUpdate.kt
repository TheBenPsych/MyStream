package com.github.damontecres.wholphin.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.BuildConfig
import com.github.damontecres.wholphin.ui.components.BenflixWordLogo
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Benflix redesign 2 (su-p-update, 2026-11-10): the first time Benflix opens after an update, a short
 * "Finishing the update" screen with the logo shows for about two seconds, then the intro and Who's
 * watching. Benflix starts clean that time (no jumping back into the last page), which keeps the old
 * TCL TVs from tripping over leftovers of the old version.
 */
object UpdateFinish {
    private const val PREFS = "benflix_update_finish"
    private const val KEY = "last_version"

    /**
     * True on the first open of this version after an update (not after a fresh install). Remembers
     * this version, so it's only true once. One small saved number: quick enough for the first screen.
     */
    fun firstOpenAfterUpdate(context: Context): Boolean =
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = BuildConfig.VERSION_CODE.toLong()
            val last = prefs.getLong(KEY, NONE)
            if (last == now) {
                false
            } else {
                prefs.edit { putLong(KEY, now) }
                if (last != NONE) {
                    true
                } else {
                    // Versions before this didn't save a number: it's an update if Benflix was installed earlier
                    val info = context.packageManager.getPackageInfo(context.packageName, 0)
                    info.lastUpdateTime > info.firstInstallTime + SAME_INSTALL_MS &&
                        PackageInfoCompat.getLongVersionCode(info) == now
                }
            }
        } catch (ex: Exception) {
            Timber.w(ex, "Couldn't check for a finished update")
            false
        }

    private const val NONE = -1L

    /** Install and update times within this of each other are the same (first) install */
    private const val SAME_INSTALL_MS = 60_000L
}

/** The logo and "Finishing the update…" for about two seconds, then [onDone] */
@Composable
fun FinishingUpdateScreen(onDone: () -> Unit) {
    val isTv = rememberIsTv()
    LaunchedEffect(Unit) {
        delay(FINISHING_MS)
        onDone()
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .fillMaxSize()
                .background(BenflixColors.Tray),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            BenflixWordLogo(height = if (isTv) 44.dp else 36.dp)
            Text("Finishing the update…", color = BenflixColors.Muted, fontSize = BenflixType.lead)
        }
    }
}

private const val FINISHING_MS = 2_000L
