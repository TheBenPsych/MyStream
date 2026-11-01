package com.github.damontecres.wholphin.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.Release
import com.github.damontecres.wholphin.services.UpdateChecker
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.touch.dialogWidth
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class UpdatePromptViewModel
    @Inject
    constructor(
        val updater: UpdateChecker,
        private val navigationManager: NavigationManager,
    ) : ViewModel() {
        fun update() {
            updater.installWhenOpened = true
            updater.clearPrompt()
            navigationManager.navigateTo(Destination.UpdateApp)
        }

        fun notNow(release: Release) = updater.snooze(release)
    }

/**
 * "A new version of Benflix is ready" pop-up with an easy list of what changed, and Update / Not now buttons.
 * Grown-up profiles only, so kids don't get asked.
 */
@Composable
fun UpdatePrompt(viewModel: UpdatePromptViewModel = hiltViewModel()) {
    val release by viewModel.updater.prompt.collectAsState()
    val kids by KidsMode.active.collectAsState()
    val current = release ?: return
    if (kids) return
    val updateFocus = remember { FocusRequester() }
    BasicDialog(
        onDismissRequest = { viewModel.notNow(current) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Runs inside the pop-up, once its buttons exist. Asking for focus from outside ran too early and
        // quietly failed, so the first OK press on the remote only selected "Update" and a second press was needed.
        LaunchedEffect(current) { updateFocus.requestFocusWhenReady() }
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .fillMaxWidth(dialogWidth(.45f))
                    .padding(24.dp),
        ) {
            Text(
                text = "A new version of Benflix is ready",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Version ${current.version}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (current.notes.isNotEmpty()) {
                Text(text = "What's new", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier =
                        Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
                    current.notes.forEach { note ->
                        Text(text = "•  $note", fontSize = 15.sp)
                    }
                }
            }
            Text(
                text = "Updating takes about a minute. Your profiles and settings stay as they are.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Button(
                    onClick = viewModel::update,
                    modifier = Modifier.focusRequester(updateFocus),
                ) {
                    Text("Update")
                }
                Button(
                    onClick = { viewModel.notNow(current) },
                    colors = ButtonDefaults.colors(),
                ) {
                    Text("Not now")
                }
            }
        }
    }
}

/**
 * Focus a button that may not be on screen yet (for example inside a pop-up that is still opening).
 * Tries for about half a second, so the first OK press on the remote presses the button instead of only selecting it.
 */
suspend fun FocusRequester.requestFocusWhenReady() {
    repeat(10) {
        if (tryRequestFocus()) return
        delay(50)
    }
}
