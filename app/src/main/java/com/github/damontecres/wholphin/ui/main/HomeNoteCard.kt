package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.BenflixNote
import com.github.damontecres.wholphin.services.BenflixNotes
import com.github.damontecres.wholphin.services.BenflixServerHealth
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.NoteKind
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixRed
import com.github.damontecres.wholphin.ui.theme.BenflixType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeNoteViewModel
    @Inject
    constructor(
        @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
        private val notes: BenflixNotes,
        private val serverRepository: ServerRepository,
        private val health: BenflixServerHealth,
        val navigationManager: NavigationManager,
        private val problems: com.github.damontecres.wholphin.services.BenflixProblems,
    ) : ViewModel() {
        /** Benflix: "Ben fixed The Office. Try it again!" after Ben marks a report fixed */
        private val _fixed = MutableStateFlow<Pair<Long, String>?>(null)
        val fixed: StateFlow<Pair<Long, String>?> = _fixed

        fun dismissFixed() {
            val (at, _) = _fixed.value ?: return
            _fixed.value = null
            viewModelScope.launch {
                try {
                    problems.dismissFixed(at)
                    _fixed.value = problems.fixedForMe().firstOrNull()
                } catch (ex: kotlinx.coroutines.CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    timber.log.Timber.w(ex, "Could not close the fixed note")
                }
            }
        }

        /** Benflix: a heads-up for the admin when the server needs attention */
        private val _headsUp = MutableStateFlow<String?>(null)
        val headsUp: StateFlow<String?> = _headsUp

        private val _note = MutableStateFlow<Pair<BenflixNote, NoteKind>?>(null)
        val note: StateFlow<Pair<BenflixNote, NoteKind>?> = _note

        init {
            viewModelScope.launch {
                if (KidsMode.isActive || GuestMode.isActive) return@launch
                try {
                    _fixed.value = problems.fixedForMe().firstOrNull()
                } catch (ex: kotlinx.coroutines.CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    timber.log.Timber.w(ex, "Could not read fixed notes")
                }
            }
            viewModelScope.launch {
                if (KidsMode.isActive || GuestMode.isActive) return@launch
                if (serverRepository.currentUserDto?.policy?.isAdministrator == true) {
                    _headsUp.value = health.headsUp()
                    checkServerAlert()
                }
            }
            viewModelScope.launch {
                val userId = serverRepository.currentUser?.id ?: return@launch
                if (KidsMode.isActive) return@launch
                // The welcome note comes first (only once per person), then the everyday note
                _note.value =
                    notes
                        .read(userId, NoteKind.WELCOME)
                        ?.takeUnless { notes.isDismissed(userId, it, NoteKind.WELCOME) }
                        ?.let { it to NoteKind.WELCOME }
                        ?: notes
                            .read(userId)
                            ?.takeUnless { notes.isDismissed(userId, it) }
                            ?.let { it to NoteKind.NOTE }
            }
        }

        /**
         * Benflix: the admin's alert (red "!" + tip bubble, like updates) when a new problem report arrived
         * since last time, or a disk is nearly full (once a day). Only while Home is on screen.
         */
        private suspend fun checkServerAlert() {
            val prefs = context.getSharedPreferences(ALERT_PREFS, android.content.Context.MODE_PRIVATE)
            try {
                val newest = problems.all().firstOrNull()
                val seen = prefs.getLong(SEEN_REPORT, 0L)
                if (newest != null && newest.at > seen) {
                    prefs.edit().putLong(SEEN_REPORT, newest.at).apply()
                    // The very first time, don't alert about old reports
                    if (seen > 0L) {
                        com.github.damontecres.wholphin.ui.components.ServerAlertNotice
                            .show("${newest.userName} reported a problem: ${newest.problem} (${newest.title}).")
                        return
                    }
                }
                val today =
                    java.time.LocalDate
                        .now()
                        .toString()
                val full = health.readDisks().filter { it.nearlyFull }.maxByOrNull { it.usedShare }
                if (full != null && prefs.getString(DISK_DAY, null) != today) {
                    prefs.edit().putString(DISK_DAY, today).apply()
                    com.github.damontecres.wholphin.ui.components.ServerAlertNotice
                        .show("The disk with ${full.name} is ${(full.usedShare * 100).toInt()}% full.")
                }
            } catch (ex: kotlinx.coroutines.CancellationException) {
                throw ex
            } catch (ex: Exception) {
                timber.log.Timber.w(ex, "Could not check for server alerts")
            }
        }

        fun dismiss() {
            val userId = serverRepository.currentUser?.id ?: return
            val (note, kind) = _note.value ?: return
            notes.dismiss(userId, note, kind)
            _note.value = null
            // After the welcome note, show the everyday note if there is one
            if (kind == NoteKind.WELCOME) {
                viewModelScope.launch {
                    _note.value = notes.read(userId)?.takeUnless { notes.isDismissed(userId, it) }?.let { it to NoteKind.NOTE }
                }
            }
        }
    }

private const val ALERT_PREFS = "benflix_server_alert"
private const val SEEN_REPORT = "seen_report_at"
private const val DISK_DAY = "disk_alert_day"

/** "A note from Ben" card near the top of Home. Shows until the person closes it. */
@Composable
fun HomeNoteCard(
    modifier: Modifier = Modifier,
    viewModel: HomeNoteViewModel = hiltViewModel(),
) {
    val note by viewModel.note.collectAsState()
    val headsUp by viewModel.headsUp.collectAsState()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        headsUp?.let { text ->
            ServerHeadsUp(text = text, onClick = { viewModel.navigationManager.navigateTo(Destination.ServerStatus) })
        }
        note?.let { (current, kind) -> NoteCard(current, kind, viewModel::dismiss) }
        val fixed by viewModel.fixed.collectAsState()
        fixed?.let { (at, title) ->
            NoteCard(BenflixNote("fixed-$at", "Ben fixed $title. Try it again!"), NoteKind.NOTE, viewModel::dismissFixed)
        }
        KidsClock()
    }
}

/** Benflix Kids: "45 minutes left today" when a parent set a daily screen time */
@Composable
private fun KidsClock() {
    val kids by com.github.damontecres.wholphin.services.KidsMode.active
        .collectAsState()
    if (!kids) return
    val context = androidx.compose.ui.platform.LocalContext.current
    var left by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<Int?>(null)
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.github.damontecres.wholphin.services.BenflixScreenTime
            .load(context)
        while (true) {
            left =
                com.github.damontecres.wholphin.services.BenflixScreenTime
                    .minutesLeftToday()
            kotlinx.coroutines.delay(30_000L)
        }
    }
    val minutes = left ?: return
    val words =
        when {
            minutes <= 0 -> "No watching time left today"
            minutes < 60 -> "$minutes minutes left today"
            minutes % 60 == 0 -> if (minutes == 60) "1 hour left today" else "${minutes / 60} hours left today"
            else -> "${minutes / 60} h ${minutes % 60} min left today"
        }
    Text(
        text = "⏰ $words",
        color = Color.White,
        fontSize = BenflixType.body,
        fontWeight = FontWeight.SemiBold,
        modifier =
            Modifier
                .background(Color(0xFF2B5DA8).copy(alpha = .55f), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Benflix: the gold "server needs you" line, only on the admin's Home */
@Composable
private fun ServerHeadsUp(
    text: String,
    onClick: () -> Unit,
) {
    androidx.tv.material3.Surface(
        onClick = onClick,
        shape =
            androidx.tv.material3.ClickableSurfaceDefaults
                .shape(RoundedCornerShape(10.dp)),
        colors =
            androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = WarnGold.copy(alpha = 0.14f),
                focusedContainerColor = WarnGold.copy(alpha = 0.3f),
            ),
        scale =
            androidx.tv.material3.ClickableSurfaceDefaults
                .scale(focusedScale = 1f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "⚠ $text · Open the Server page ›",
            color = WarnGold,
            fontSize = BenflixType.body,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

private val WarnGold =
    androidx.compose.ui.graphics
        .Color(0xFFF2B84B)

@Composable
private fun NoteCard(
    current: BenflixNote,
    kind: NoteKind,
    onDismiss: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (kind == NoteKind.WELCOME) "Welcome from Ben" else "A note from Ben",
                color = BenflixRed,
                fontSize = BenflixType.small,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = current.text,
                fontSize = BenflixType.lead,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Button(onClick = onDismiss, scale = ButtonDefaults.scale(focusedScale = 1f)) {
            Text(if (kind == NoteKind.WELCOME) "Start watching" else "Got it")
        }
    }
}
