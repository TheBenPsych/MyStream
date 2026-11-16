package com.github.damontecres.wholphin.ui.music

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.MusicMe
import com.github.damontecres.wholphin.services.MusicPerson
import com.github.damontecres.wholphin.services.MusicPlace
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.showToast
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Music redesign 3: Send to a profile. The song, album, playlist or artist shows in their Home news. */
@HiltViewModel
class MusicSendViewModel
    @Inject
    constructor(
        private val musicMe: MusicMe,
        @param:ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _people = MutableStateFlow<List<MusicPerson>?>(null)
        val people: StateFlow<List<MusicPerson>?> = _people

        fun look() =
            viewModelScope.launchIO {
                if (_people.value.isNullOrEmpty()) _people.value = musicMe.people()
            }

        fun send(
            to: MusicPerson,
            place: MusicPlace,
        ) = viewModelScope.launchIO {
            if (musicMe.send(to, place)) {
                showToast(context, "Sent to ${to.name}. It shows on their Home.")
            } else {
                showToast(context, "Couldn't send that right now. Try again in a minute.")
            }
        }
    }

/** Pick who to send [place] to (everyone on Benflix except you and Kids) */
@Composable
fun SendToDialog(
    place: MusicPlace,
    onDismiss: () -> Unit,
    viewModel: MusicSendViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.look() }
    val people by viewModel.people.collectAsState()
    val list = people
    val items =
        when {
            list == null -> {
                listOf(DialogItem(headlineContent = { Text("Looking for profiles...") }, onClick = {}, enabled = false))
            }

            list.isEmpty() -> {
                listOf(DialogItem(headlineContent = { Text("There's no one else to send music to yet.") }, onClick = onDismiss))
            }

            else -> {
                list.map { person ->
                    DialogItem(headlineContent = { Text(person.name) }, onClick = {
                        viewModel.send(person, place)
                        onDismiss()
                    })
                }
            }
        }
    DialogPopup(
        showDialog = true,
        title = "Send \"${place.name}\" to",
        dialogItems = items,
        onDismissRequest = onDismiss,
        dismissOnClick = false,
        waitToLoad = false,
    )
}
