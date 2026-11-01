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
    ) : ViewModel() {
        private val _note = MutableStateFlow<BenflixNote?>(null)
        val note: StateFlow<BenflixNote?> = _note

        init {
            viewModelScope.launch {
                val userId = serverRepository.currentUser?.id ?: return@launch
                _note.value = notes.read(userId)?.takeUnless { notes.isDismissed(userId, it) }
            }
        }

        fun dismiss() {
            val userId = serverRepository.currentUser?.id ?: return
            _note.value?.let { notes.dismiss(userId, it) }
            _note.value = null
        }
    }

/** "A note from Ben" card near the top of Home. Shows until the person closes it. */
@Composable
fun HomeNoteCard(
    modifier: Modifier = Modifier,
    viewModel: HomeNoteViewModel = hiltViewModel(),
) {
    val note by viewModel.note.collectAsState()
    val current = note ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "A note from Ben",
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
        Button(onClick = viewModel::dismiss) {
            Text("Got it")
        }
    }
}
