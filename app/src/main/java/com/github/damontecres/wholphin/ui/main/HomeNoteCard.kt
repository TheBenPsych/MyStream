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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeNoteViewModel
    @Inject
    constructor(
        private val notes: BenflixNotes,
        private val serverRepository: ServerRepository,
        private val health: BenflixServerHealth,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        /** Benflix: a heads-up for the admin when the server needs attention */
        private val _headsUp = MutableStateFlow<String?>(null)
        val headsUp: StateFlow<String?> = _headsUp

        private val _note = MutableStateFlow<Pair<BenflixNote, NoteKind>?>(null)
        val note: StateFlow<Pair<BenflixNote, NoteKind>?> = _note

        init {
            viewModelScope.launch {
                if (KidsMode.isActive || GuestMode.isActive) return@launch
                if (serverRepository.currentUserDto?.policy?.isAdministrator == true) {
                    _headsUp.value = health.headsUp()
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
    }
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
            fontSize = 16.sp,
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
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = current.text,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Button(onClick = onDismiss) {
            Text(if (kind == NoteKind.WELCOME) "Start watching" else "Got it")
        }
    }
}
