package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.BenflixLibraryCheckup
import com.github.damontecres.wholphin.services.BenflixNote
import com.github.damontecres.wholphin.services.BenflixNotes
import com.github.damontecres.wholphin.services.BenflixPeople
import com.github.damontecres.wholphin.services.CheckupItem
import com.github.damontecres.wholphin.services.CheckupSection
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.NoteKind
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.preferences.StringInput
import com.github.damontecres.wholphin.ui.preferences.StringInputDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.UserDto
import timber.log.Timber
import javax.inject.Inject

/** What the Server page's tools show: the note, people and the library checkup */
data class ServerToolsState(
    val note: BenflixNote? = null,
    val welcome: BenflixNote? = null,
    val people: List<UserDto> = emptyList(),
    val checkup: List<CheckupSection>? = null,
    val checkupHidden: Int = 0,
    /** A message to show in a pop-up, like a new password */
    val message: Pair<String, String>? = null,
    val busy: Boolean = false,
)

@HiltViewModel
class ServerToolsViewModel
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val notes: BenflixNotes,
        private val people: BenflixPeople,
        private val checkup: BenflixLibraryCheckup,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ServerToolsState())
        val state: StateFlow<ServerToolsState> = _state

        private val userId get() = serverRepository.currentUser?.id

        fun load() {
            val id = userId ?: return
            work(showBusy = false) {
                _state.value =
                    _state.value.copy(
                        note = notes.read(id),
                        welcome = notes.read(id, NoteKind.WELCOME),
                        people = people.list(),
                    )
            }
        }

        fun publishNote(text: String) =
            work {
                val count = notes.publish(text)
                val id = userId
                _state.value =
                    _state.value.copy(
                        note = id?.let { notes.read(it) },
                        message =
                            if (text.isBlank()) {
                                "Note removed" to "It won't show on Home any more."
                            } else {
                                "Note sent" to "It will show at the top of Home for $count people."
                            },
                    )
            }

        fun publishWelcome(text: String) =
            work {
                notes.publish(text, NoteKind.WELCOME)
                val id = userId
                _state.value =
                    _state.value.copy(
                        welcome = id?.let { notes.read(it, NoteKind.WELCOME) },
                        message =
                            if (text.isBlank()) {
                                "Welcome note removed" to "New people won't see a welcome note."
                            } else {
                                "Welcome note saved" to "Everyone sees it once, on their first Home screen."
                            },
                    )
            }

        fun addPerson(name: String) {
            if (name.isBlank()) return
            work {
                val password = people.add(name)
                _state.value =
                    _state.value.copy(
                        people = people.list(),
                        message =
                            "${name.trim()} can now sign in" to
                                "Name: ${name.trim()}\nPassword: $password\n\n" +
                                "On their TV, open the Downloader app and type $INSTALL_CODE to get Benflix. " +
                                "They can change the password later in Jellyfin.",
                    )
            }
        }

        fun resetPassword(user: UserDto) =
            work {
                val password = people.resetPassword(user.id)
                _state.value =
                    _state.value.copy(message = "New password for ${user.name}" to "Password: $password")
            }

        fun removePerson(user: UserDto) =
            work {
                people.remove(user.id)
                _state.value =
                    _state.value.copy(
                        people = people.list(),
                        message = "${user.name} was removed" to "Their account is gone from the server.",
                    )
            }

        fun runCheckup() {
            val id = userId ?: return
            work {
                _state.value = _state.value.copy(checkup = checkup.run(id), checkupHidden = checkup.hiddenCount())
            }
        }

        fun hide(item: CheckupItem) {
            checkup.hide(item)
            _state.value =
                _state.value.copy(
                    checkup =
                        _state.value.checkup?.map { section ->
                            section.copy(items = section.items.filterNot { it.key == item.key })
                        },
                    checkupHidden = checkup.hiddenCount(),
                )
        }

        fun unhideAll() {
            checkup.unhideAll()
            runCheckup()
        }

        fun open(item: CheckupItem) = navigationManager.navigateTo(Destination.MediaItem(item.itemId, item.type))

        fun clearMessage() {
            _state.value = _state.value.copy(message = null)
        }

        private fun work(
            showBusy: Boolean = true,
            block: suspend () -> Unit,
        ) {
            viewModelScope.launch {
                if (showBusy) _state.value = _state.value.copy(busy = true)
                try {
                    block()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Server tools: something went wrong")
                    _state.value =
                        _state.value.copy(message = "That didn't work" to (ex.message ?: "The server said no. Try again in a moment."))
                } finally {
                    _state.value = _state.value.copy(busy = false)
                }
            }
        }

        companion object {
            const val INSTALL_CODE = "2488986"
        }
    }

/** Which pop-up the Server tools are showing */
sealed interface ServerToolsDialog {
    data object WriteNote : ServerToolsDialog

    data object WriteWelcome : ServerToolsDialog

    data object AddPerson : ServerToolsDialog

    data class Person(
        val user: UserDto,
    ) : ServerToolsDialog

    data class RemovePerson(
        val user: UserDto,
    ) : ServerToolsDialog

    data class Hide(
        val item: CheckupItem,
    ) : ServerToolsDialog
}

/** The Notes, People and Library checkup sections of the Server page */
fun LazyListScope.serverToolsItems(
    state: ServerToolsState,
    viewModel: ServerToolsViewModel,
    showDialog: (ServerToolsDialog) -> Unit,
    sectionTitle: @Composable (String) -> Unit,
) {
    // Notes from Ben
    item { sectionTitle("Note on everyone's Home") }
    item {
        ListItem(
            selected = false,
            onClick = { showDialog(ServerToolsDialog.WriteNote) },
            headlineContent = { Text(state.note?.text ?: "Write a note") },
            supportingContent = {
                Text(
                    if (state.note == null) {
                        "Like \"New Marvel movies added!\" It shows at the top of Home for everyone until they close it."
                    } else {
                        "Showing on Home now. Click to change it, or save it empty to remove it."
                    },
                )
            },
        )
    }

    // The welcome note (shown once to each person)
    item {
        ListItem(
            selected = false,
            onClick = { showDialog(ServerToolsDialog.WriteWelcome) },
            headlineContent = { Text(state.welcome?.text ?: "Write a welcome note") },
            supportingContent = {
                Text(
                    if (state.welcome == null) {
                        "Everyone sees it once, on their first Home screen. Like \"Welcome to Benflix! Ask me if you can't find something.\""
                    } else {
                        "Welcome note. Click to change it, or save it empty to remove it."
                    },
                )
            },
        )
    }

    // Add a person
    item { sectionTitle("People (${state.people.size})") }
    item {
        ListItem(
            selected = false,
            onClick = { showDialog(ServerToolsDialog.AddPerson) },
            headlineContent = { Text("Add a person") },
            supportingContent = { Text("Makes their account and gives you a password and install code to send them") },
        )
    }
    items(state.people, key = { "person-${it.id}" }) { user ->
        ListItem(
            selected = false,
            onClick = { showDialog(ServerToolsDialog.Person(user)) },
            onLongClick = {
                if (user.policy?.isAdministrator != true) showDialog(ServerToolsDialog.RemovePerson(user))
            },
            headlineContent = { Text(user.name.orEmpty()) },
            supportingContent = {
                Text(
                    listOfNotNull(
                        if (user.policy?.isAdministrator == true) "Admin" else null,
                        if (user.policy?.isDisabled == true) "Turned off" else null,
                        user.lastActivityDate?.let { "Last seen ${it.toLocalDate()}" } ?: "Never signed in",
                        if (user.policy?.isAdministrator == true) null else "click for a new password, hold to remove",
                    ).joinToString(" · "),
                )
            },
        )
    }

    // Library checkup
    item { sectionTitle("Library checkup") }
    val checkup = state.checkup
    if (checkup == null) {
        item {
            ListItem(
                selected = false,
                onClick = { viewModel.runCheckup() },
                headlineContent = { Text(if (state.busy) "Checking…" else "Run a checkup") },
                supportingContent = {
                    Text(
                        "Finds missing episodes, movies with no trailer, anime with no subtitles and low quality movies",
                    )
                },
            )
        }
    } else {
        checkup.forEach { section ->
            item(key = "checkup-${section.title}") {
                ListItem(
                    selected = false,
                    onClick = {},
                    headlineContent = { Text("${section.title} (${section.items.size})") },
                    supportingContent = { if (section.items.isEmpty()) Text("Nothing found. Nice!") },
                )
            }
            items(section.items, key = { "check-${it.key}" }) { found ->
                ListItem(
                    selected = false,
                    onClick = { viewModel.open(found) },
                    onLongClick = { showDialog(ServerToolsDialog.Hide(found)) },
                    headlineContent = { Text("    ${found.title}") },
                    supportingContent = { Text("    ${found.detail} · hold to hide") },
                )
            }
        }
        if (state.checkupHidden > 0) {
            item {
                ListItem(
                    selected = false,
                    onClick = { viewModel.unhideAll() },
                    headlineContent = { Text("Show ${state.checkupHidden} hidden item(s) again") },
                )
            }
        }
    }
}

/** The pop-ups for the Server tools */
@Composable
fun ServerToolsDialogs(
    state: ServerToolsState,
    viewModel: ServerToolsViewModel,
    dialog: ServerToolsDialog?,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        ServerToolsDialog.WriteNote -> {
            StringInputDialog(
                input =
                    StringInput(
                        title = "Note on everyone's Home",
                        value = state.note?.text,
                        keyboardOptions =
                            KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done,
                            ),
                        onSubmit = {},
                        maxLines = 3,
                    ),
                onSave = {
                    onDismiss()
                    viewModel.publishNote(it)
                },
                onDismissRequest = onDismiss,
            )
        }

        ServerToolsDialog.WriteWelcome -> {
            StringInputDialog(
                input =
                    StringInput(
                        title = "Welcome note (shown once to each person)",
                        value = state.welcome?.text,
                        keyboardOptions =
                            KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done,
                            ),
                        onSubmit = {},
                        maxLines = 3,
                    ),
                onSave = {
                    onDismiss()
                    viewModel.publishWelcome(it)
                },
                onDismissRequest = onDismiss,
            )
        }

        ServerToolsDialog.AddPerson -> {
            StringInputDialog(
                input =
                    StringInput(
                        title = "Their name (this is what they sign in with)",
                        value = "",
                        keyboardOptions =
                            KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Done,
                            ),
                        onSubmit = {},
                    ),
                onSave = {
                    onDismiss()
                    viewModel.addPerson(it)
                },
                onDismissRequest = onDismiss,
            )
        }

        is ServerToolsDialog.Person -> {
            ConfirmDialog(
                title = dialog.user.name.orEmpty(),
                body = "Give ${dialog.user.name} a new password? You'll see it on the next screen so you can send it to them.",
                onCancel = onDismiss,
                onConfirm = {
                    onDismiss()
                    viewModel.resetPassword(dialog.user)
                },
            )
        }

        is ServerToolsDialog.RemovePerson -> {
            ConfirmDialog(
                title = "Remove ${dialog.user.name}?",
                body = "Their account, watch history and lists are deleted from the server. This can't be undone.",
                onCancel = onDismiss,
                onConfirm = {
                    onDismiss()
                    viewModel.removePerson(dialog.user)
                },
            )
        }

        is ServerToolsDialog.Hide -> {
            ConfirmDialog(
                title = "Hide \"${dialog.item.title}\"?",
                body = "It won't show in the checkup any more. You can show hidden items again at the bottom.",
                onCancel = onDismiss,
                onConfirm = {
                    onDismiss()
                    viewModel.hide(dialog.item)
                },
            )
        }

        null -> {}
    }
    state.message?.let { (title, body) ->
        ConfirmDialog(
            title = title,
            body = body,
            onCancel = viewModel::clearMessage,
            onConfirm = viewModel::clearMessage,
        )
    }
}
