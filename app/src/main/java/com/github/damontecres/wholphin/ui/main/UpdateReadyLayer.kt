package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.UpdateChecker
import com.github.damontecres.wholphin.ui.components.BenflixMark
import com.github.damontecres.wholphin.ui.components.BenflixPopups
import com.github.damontecres.wholphin.ui.components.UpdateNotice
import com.github.damontecres.wholphin.ui.components.popupTurn
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.Dialog
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Benflix: once a TV or phone is [DAYS_BEFORE_ASKING] days behind, a full screen "Benflix got better"
 * shows on Home (right after the logo and Who's watching), never during a show. "Update now" goes
 * straight to the install; the update is usually already downloaded, so it's quick.
 * "Not now" waits a day, and can be pressed twice; after that only "Update now" is offered
 * (Back still closes it until Benflix is opened again). Never on Kids or Guest.
 */
@HiltViewModel
class UpdateReadyViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val updater: UpdateChecker,
        private val navigationManager: NavigationManager,
    ) : ViewModel() {
        val release = updater.prompt

        private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private val installed = updater.getInstalledVersion().toString()

        private val _closed = MutableStateFlow(closedThisRun)

        /** Closed for now (Update now, Not now or Back) */
        val closed: StateFlow<Boolean> = _closed

        /** "Not now" presses since this version was installed */
        fun notNowCount(): Int = if (prefs.getString(KEY_FOR, null) == installed) prefs.getInt(KEY_COUNT, 0) else 0

        fun snoozed(): Boolean = prefs.getString(KEY_FOR, null) == installed && System.currentTimeMillis() < prefs.getLong(KEY_UNTIL, 0)

        /** A short "what's new" list: the newest changes since the installed version */
        fun whatsNew(): List<String> {
            val r = release.value ?: return emptyList()
            return r
                .changesSince(updater.getInstalledVersion())
                .flatMap { it.titles }
                .distinct()
                .take(WHATS_NEW_LINES)
        }

        fun isReady(): Boolean = release.value?.let { updater.readyDownload(it.version) } != null

        fun updateNow() {
            close()
            updater.installWhenOpened = true
            navigationManager.navigateTo(Destination.UpdateApp)
        }

        fun notNow() {
            val count = notNowCount() + 1
            prefs.edit {
                putString(KEY_FOR, installed)
                putInt(KEY_COUNT, count)
                putLong(KEY_UNTIL, System.currentTimeMillis() + SNOOZE_MS)
            }
            close()
        }

        fun close() {
            closedThisRun = true
            _closed.value = true
        }

        companion object {
            private const val PREFS = "benflix_update_ready"
            private const val KEY_FOR = "for"
            private const val KEY_COUNT = "not-now"
            private const val KEY_UNTIL = "until"

            /** Shown at most once each time Benflix is opened */
            @Volatile
            private var closedThisRun = false
        }
    }

/** How many days behind before the update screen asks */
const val DAYS_BEFORE_ASKING = 3

/** How many times "Not now" can be pressed before only "Update now" is offered */
private const val NOT_NOW_LIMIT = 2

private const val SNOOZE_MS = 24L * 60 * 60 * 1000

private const val WHATS_NEW_LINES = 3

@Composable
fun UpdateReadyLayer(
    currentDestination: Destination?,
    viewModel: UpdateReadyViewModel = hiltViewModel(),
) {
    val release by viewModel.release.collectAsState()
    val days by UpdateNotice.daysBehind.collectAsState()
    val closed by viewModel.closed.collectAsState()
    val kids by KidsMode.active.collectAsState()
    val guest by GuestMode.active.collectAsState()
    val wants =
        release != null &&
            days >= DAYS_BEFORE_ASKING &&
            !closed &&
            !kids &&
            !guest &&
            currentDestination is Destination.Home &&
            !viewModel.snoozed()
    if (!popupTurn("update-ready", BenflixPopups.UPDATE, wants)) return
    val isTv = rememberIsTv()
    val focus = remember { FocusRequester() }
    val lines = remember(release) { viewModel.whatsNew() }
    val ready = remember(release) { viewModel.isReady() }
    val canWait = remember { viewModel.notNowCount() < NOT_NOW_LIMIT }
    Dialog(
        onDismissRequest = viewModel::close,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(BenflixColors.Tray),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.widthIn(max = 620.dp).padding(24.dp),
            ) {
                BenflixMark(size = if (isTv) 64.dp else 56.dp)
                Text(
                    "Benflix got better",
                    fontSize = BenflixType.hero,
                    color = BenflixColors.Text,
                    textAlign = TextAlign.Center,
                )
                Text(
                    if (ready) {
                        "The update is already downloaded. It only takes a few seconds."
                    } else {
                        "The update takes about a minute."
                    },
                    fontSize = BenflixType.lead,
                    color = BenflixColors.Soft,
                    textAlign = TextAlign.Center,
                )
                if (lines.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        lines.forEach {
                            Text(
                                "•  $it",
                                fontSize = BenflixType.body,
                                color = BenflixColors.Text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 10.dp)) {
                    Button(
                        onClick = viewModel::updateNow,
                        modifier = Modifier.focusRequester(focus),
                        scale = ButtonDefaults.scale(focusedScale = 1f),
                    ) {
                        Text("Update now")
                    }
                    if (canWait) {
                        OutlinedButton(
                            onClick = viewModel::notNow,
                            scale = OutlinedButtonDefaults.scale(focusedScale = 1f),
                        ) { Text("Not now") }
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            delay(200)
            focus.tryRequestFocus()
        }
    }
}
