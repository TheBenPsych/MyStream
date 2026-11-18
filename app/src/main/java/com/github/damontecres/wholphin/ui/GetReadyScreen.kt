package com.github.damontecres.wholphin.ui

import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.SpeedUpInstall
import com.github.damontecres.wholphin.ui.components.BenflixWordLogo
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Ben 2026-11-18: "a button that's forced first" after a fresh install. Android only prepares a sideloaded app for
 * the TV while the TV sleeps, so a fresh install (or an update from outside Benflix) runs slowly for the first day.
 * The very first screen asks for one press: Benflix puts itself back in, same version, with its speed-up list, and
 * Android prepares it during that install. Asked once per install; it never shows when Benflix is already prepared.
 */
object GetReady {
    private const val PREFS = "benflix_get_ready"

    /** True when the Get ready screen should show now (checks files, so call it off the main thread) */
    fun needed(context: Context): Boolean =
        try {
            if (!SpeedUpInstall.canUse(context) || isEmulator()) {
                false
            } else if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(installKey(context), false)) {
                false
            } else {
                val status =
                    androidx.profileinstaller.ProfileVerifier
                        .getCompilationStatusAsync()
                        .get()
                !status.isCompiledWithProfile
            }
        } catch (ex: Exception) {
            Timber.w(ex, "Get ready: couldn't check")
            false
        }

    /** Asked for this install, whatever the answer */
    fun asked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(installKey(context), true) }
    }

    private fun installKey(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return "asked-${info.firstInstallTime}"
    }

    /** The test robots run on emulators, where this screen would only get in the way */
    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.PRODUCT.contains("sdk")
}

/** The logo, one line on what's happening and a Get ready button. [onDone] carries on to the app. */
@Composable
fun GetReadyScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val isTv = rememberIsTv()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var working by remember { mutableStateOf(false) }
    // After one try (cancelled on Android's screen, or it couldn't start) a Not now shows as well, so nobody is stuck
    var tried by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { focus.requestFocus() }
    }
    // It comes first: Back doesn't skip it until it's been tried once (then Not now shows)
    BackHandler(enabled = !tried) { }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .fillMaxSize()
                .background(BenflixColors.Tray)
                // Taps and the remote stay on this screen, never on the sign-in underneath
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(24.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier =
                Modifier
                    .widthIn(max = 620.dp)
                    .focusProperties { onExit = { cancelFocusChange() } }
                    .focusGroup(),
        ) {
            BenflixWordLogo(height = if (isTv) 44.dp else 36.dp)
            Text(
                "One step before you start",
                color = BenflixColors.Text,
                fontSize = BenflixType.pageTitle,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Text(
                if (working) {
                    "Getting ready… Press Install on the next screen. It takes a moment, then Benflix opens again."
                } else {
                    "Benflix needs to get ready for this ${if (isTv) "TV" else "phone"} so menus are smooth from the start. " +
                        "Press Get ready, then Install on the next screen."
                },
                color = BenflixColors.Soft,
                fontSize = BenflixType.body,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = {
                    if (working) return@Button
                    working = true
                    GetReady.asked(context)
                    scope.launch {
                        val handed = withContext(WholphinDispatchers.IO) { SpeedUpInstall.speedUpNow(context) }
                        if (!handed) {
                            // Couldn't start it here: carry on, Android still does it while the TV sleeps
                            onDone()
                        } else {
                            // Android's screen shows next; if it's cancelled, Benflix comes back here
                            delay(SHOW_NOT_NOW_MS)
                            working = false
                            tried = true
                        }
                    }
                },
                colors =
                    ButtonDefaults.colors(
                        containerColor = BenflixColors.FocusFill,
                        contentColor = BenflixColors.OnFocusFill,
                        focusedContainerColor = BenflixColors.FocusFill,
                        focusedContentColor = BenflixColors.OnFocusFill,
                    ),
                modifier = Modifier.focusRequester(focus),
            ) {
                Text(if (tried) "Try again" else "Get ready", fontWeight = FontWeight.Bold)
            }
            if (tried) {
                Button(
                    onClick = onDone,
                    colors =
                        ButtonDefaults.colors(
                            containerColor = BenflixColors.Raised,
                            contentColor = BenflixColors.Text,
                            focusedContainerColor = BenflixColors.FocusRow,
                            focusedContentColor = BenflixColors.Text,
                        ),
                ) {
                    Text("Not now")
                }
            }
        }
    }
}

/** How long after handing it to Android before Try again / Not now show (Android's screen covers this one) */
private const val SHOW_NOT_NOW_MS = 4_000L
