package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.BenflixLibraryCheckup
import com.github.damontecres.wholphin.services.BenflixMessages
import com.github.damontecres.wholphin.services.BenflixNote
import com.github.damontecres.wholphin.services.BenflixNotes
import com.github.damontecres.wholphin.services.BenflixPeople
import com.github.damontecres.wholphin.services.BenflixProblems
import com.github.damontecres.wholphin.services.CheckupItem
import com.github.damontecres.wholphin.services.CheckupSection
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.NoteKind
import com.github.damontecres.wholphin.services.ProblemReport
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.preferences.StringInput
import com.github.damontecres.wholphin.ui.preferences.StringInputDialog
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.util.StartupTimer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.MessageCommand
import org.jellyfin.sdk.model.api.UserDto
import timber.log.Timber
import javax.inject.Inject

/** What the Server page's tools show: the note, people and the library checkup */
data class ServerToolsState(
    val note: BenflixNote? = null,
    val welcome: BenflixNote? = null,
    /** Benflix: null until the People section is first opened */
    val people: List<UserDto>? = null,
    val checkup: List<CheckupSection>? = null,
    val checkupHidden: Int = 0,
    /** A message to show in a pop-up, like a new password */
    val message: Pair<String, String>? = null,
    val busy: Boolean = false,
    /** Benflix: true once the notes were read (they load when Messages is first opened) */
    val notesLoaded: Boolean = false,
    /** Benflix: the server's libraries, for "Which libraries they see" */
    val libraries: List<org.jellyfin.sdk.model.api.BaseItemDto>? = null,
    /** Benflix: "What takes the most space" (null until asked) */
    val space: List<CheckupSection>? = null,
    val spaceBusy: Boolean = false,
    /** Benflix: the people list couldn't be read (Refresh tries again) */
    val peopleFailed: Boolean = false,
    /** Benflix: the notes couldn't be read (Refresh tries again) */
    val notesFailed: Boolean = false,
    /** Benflix: this device can't hold a live connection to the server, so live pop-ups can't get through */
    val liveBlocked: Boolean = false,
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
        private val api: ApiClient,
        private val problems: BenflixProblems,
        private val dashboard: ServerDashboardData,
        private val actions: BenflixServerActions,
        private val benflixMessages: BenflixMessages,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ServerToolsState())
        val state: StateFlow<ServerToolsState> = _state

        private val userId get() = serverRepository.currentUser?.id

        private var notesAsked = false
        private var peopleAsked = false

        init {
            viewModelScope.launch {
                benflixMessages.liveBlocked.collect { blocked -> _state.update { it.copy(liveBlocked = blocked) } }
            }
        }

        /** Benflix: the notes only load when the Messages section is first opened */
        fun loadNotes() {
            if (notesAsked) return
            val id = userId ?: return
            notesAsked = true
            _state.value = _state.value.copy(notesFailed = false)
            viewModelScope.launch {
                try {
                    // Benflix: a note sent to only some people isn't in Ben's own copy, so look at theirs too
                    val note = notes.readSent(id)
                    val welcome = notes.read(id, NoteKind.WELCOME)
                    _state.value = _state.value.copy(note = note, welcome = welcome, notesLoaded = true)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Server tools: could not read the notes")
                    notesAsked = false
                    _state.value = _state.value.copy(notesFailed = true)
                }
            }
        }

        /** Benflix: the people list only loads when the People section is first opened */
        fun loadPeople() {
            if (peopleAsked) return
            peopleAsked = true
            _state.value = _state.value.copy(peopleFailed = false)
            viewModelScope.launch {
                try {
                    val list = people.list()
                    _state.value = _state.value.copy(people = list)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Server tools: could not read the people")
                    peopleAsked = false
                    _state.value = _state.value.copy(peopleFailed = true)
                }
            }
            work(showBusy = false) {
                _state.value = _state.value.copy(libraries = actions.libraries())
            }
        }

        private suspend fun readSentOrNull(id: java.util.UUID): BenflixNote? =
            try {
                notes.readSent(id)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Server tools: could not read the note back")
                null
            }

        /** Benflix: Refresh (and pull to refresh) tries again whatever couldn't be read the first time */
        fun retryFailed() {
            if (_state.value.peopleFailed) loadPeople()
            if (_state.value.notesFailed) loadNotes()
        }

        /** Benflix: how many screens they can watch on at the same time (0 = no limit) */
        fun setScreens(
            user: UserDto,
            screens: Int,
        ) = work {
            actions.setScreens(user, screens)
            _state.value =
                _state.value.copy(
                    people = people.list(),
                    message =
                        "Saved" to
                            if (screens == 0) {
                                "${user.name} can watch on as many screens as they like."
                            } else {
                                "${user.name} can watch on $screens screen" + (if (screens == 1) "" else "s") + " at the same time."
                            },
                )
        }

        /** Benflix: ticks or unticks one library for a person ([libraryId] null = all libraries) */
        fun toggleLibrary(
            user: UserDto,
            libraryId: java.util.UUID?,
        ) = work(showBusy = false) {
            val current = _state.value.people?.firstOrNull { it.id == user.id } ?: user
            val all =
                _state.value.libraries
                    .orEmpty()
                    .map { it.id }
            val policy = current.policy
            val seeing = if (policy == null || policy.enableAllFolders) all else policy.enabledFolders.orEmpty()
            val next =
                when {
                    libraryId == null -> null
                    libraryId in seeing -> seeing - libraryId
                    else -> seeing + libraryId
                }
            actions.setLibraries(current, next?.takeIf { it.toSet() != all.toSet() })
            _state.value = _state.value.copy(people = people.list())
        }

        /** Benflix: signs them out on every device (not this one) */
        fun signOutEverywhere(user: UserDto) =
            work {
                val (count, skipped) = actions.signOutEverywhere(user, api.deviceInfo.id)
                val skippedText =
                    if (skipped == 0) {
                        ""
                    } else {
                        " $skipped device" + (if (skipped == 1) " was" else "s were") +
                            " left alone because other people are signed in on " + (if (skipped == 1) "it" else "them") + " too."
                    }
                _state.value =
                    _state.value.copy(
                        message =
                            "Signed out" to
                                if (count == 0) {
                                    if (skipped == 0) {
                                        "${user.name} wasn't signed in anywhere else."
                                    } else {
                                        "${user.name} wasn't signed out anywhere." + skippedText
                                    }
                                } else {
                                    "${user.name} was signed out on $count device" + (if (count == 1) "" else "s") +
                                        ". They'll need their password to sign in again." + skippedText
                                },
                    )
            }

        fun clearProblem(report: ProblemReport) =
            work(showBusy = false) {
                problems.clear(report)
                dashboard.removeReport(report)
            }

        fun openProblem(report: ProblemReport) {
            val id = report.itemId ?: return
            work(showBusy = false) {
                val item = api.userLibraryApi.getItem(id, userId).content
                navigationManager.navigateTo(Destination.MediaItem(item.id, item.type))
            }
        }

        fun publishNote(
            text: String,
            expiresAt: Long? = null,
            onlyFor: Set<java.util.UUID>? = null,
        ) = work {
            val count = notes.publish(text, NoteKind.NOTE, expiresAt, onlyFor)
            val id = userId
            _state.value =
                _state.value.copy(
                    note = id?.let { readSentOrNull(it) },
                    message =
                        if (text.isBlank()) {
                            "Note removed" to "It won't show on Home any more."
                        } else {
                            "Note sent" to "It will show at the top of Home for $count " +
                                (if (count == 1) "person" else "people") +
                                (expiresAt?.let { " until ${untilText(it)}" } ?: "") + "."
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

        /** Benflix: the most this person can stream away from home (0 = no limit), using Jellyfin's own setting */
        fun setAwayQuality(
            user: UserDto,
            bitsPerSecond: Int,
        ) = work {
            // Benflix: read their policy fresh so other settings changed since the list loaded aren't undone
            val policy =
                api.userApi
                    .getUserById(user.id)
                    .content.policy ?: return@work
            api.userApi.updateUserPolicy(user.id, policy.copy(remoteClientBitrateLimit = bitsPerSecond))
            _state.value =
                _state.value.copy(
                    people = people.list(),
                    message =
                        "Saved" to
                            "${user.name} can stream up to ${awayQualityLabel(bitsPerSecond).lowercase()} when away from home.",
                )
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
            if (_state.value.busy) return
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

        /** Benflix: the biggest movies and shows, and what nobody watched in a year */
        fun runSpace() {
            val id = userId ?: return
            if (_state.value.spaceBusy) return
            _state.value = _state.value.copy(spaceBusy = true)
            work(showBusy = false) {
                try {
                    _state.value = _state.value.copy(space = checkup.space(id))
                } finally {
                    _state.value = _state.value.copy(spaceBusy = false)
                }
            }
        }

        fun unhideAll() {
            checkup.unhideAll()
            runCheckup()
        }

        fun open(item: CheckupItem) = navigationManager.navigateTo(Destination.MediaItem(item.itemId, item.type))

        /**
         * Benflix: pops a message up right now on every screen that has Benflix (or Jellyfin) open.
         * When Ben picked people, it's also saved for each of them first, so anyone whose Benflix isn't really open
         * (in the background, or closed) sees it the next time they open it. A screen that shows it live takes the
         * saved copy off, so it only shows once.
         */
        fun sendPopup(
            text: String,
            onlyFor: Set<java.util.UUID>? = null,
        ) {
            if (text.isBlank()) return
            work {
                val header = "Message from Ben"
                val blocked = benflixMessages.liveBlocked.value
                // Benflix: so this screen (the sender's) doesn't pop it up or pick up the saved copy itself
                benflixMessages.rememberSent(text)
                val names =
                    if (onlyFor != null || blocked) {
                        (_state.value.people ?: people.list()).associate { it.id to it.name.orEmpty() }
                    } else {
                        emptyMap()
                    }
                // Benflix: when live pop-ups can't get through, save it for everyone too, so the regular check
                // on each screen still picks it up within a couple of minutes
                val saveFor = onlyFor ?: if (blocked) names.keys.toSet() else null
                // Benflix: save first, so a screen that shows it live can take the saved copy off straight away
                val savedFor = mutableSetOf<java.util.UUID>()
                saveFor?.forEach { id ->
                    try {
                        benflixMessages.saveFor(id, header, text)
                        savedFor += id
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not save the message for %s", id)
                    }
                }
                val myDevice = api.deviceInfo.id
                val sessions =
                    api.sessionApi
                        .getSessions(activeWithinSeconds = 960)
                        .content
                        .filter { it.supportsRemoteControl && it.id != null }
                        // Benflix: not this phone/TV itself (Ben sending to his own profile means his other screens)
                        .filter { it.deviceId != myDevice }
                        // Benflix: only the people Ben picked
                        .filter { onlyFor == null || it.userId in onlyFor }
                var sent = 0
                val reached = mutableSetOf<java.util.UUID>()
                sessions.forEach { session ->
                    try {
                        api.sessionApi.sendMessageCommand(
                            sessionId = session.id!!,
                            data = MessageCommand(header = header, text = text, timeoutMs = 15_000L),
                        )
                        // Benflix: only a Benflix app that's connected right now really shows it. The server only
                        // marks a session as remote-controllable while its app is connected, and Benflix drops
                        // that connection when it goes to the background.
                        if (session.deviceId?.startsWith(BENFLIX_DEVICE_PREFIX) == true) {
                            sent++
                            session.userId?.let { reached += it }
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not send the pop-up to %s", session.deviceName)
                    }
                }
                _state.value =
                    _state.value.copy(
                        message =
                            when {
                                // Benflix: the server's address won't take live connections, so we can't tell who
                                // saw it live. Every Benflix screen checks for saved messages every minute or so.
                                blocked && savedFor.isNotEmpty() -> {
                                    "Message saved" to
                                        "Open Benflix screens will show it within about 2 minutes, even during a show. " +
                                        "It can't pop up instantly right now: $LIVE_BLOCKED_HELP"
                                }

                                blocked -> {
                                    "That didn't work" to "The message couldn't be saved. Try again in a moment."
                                }

                                else -> {
                                    val now =
                                        if (sent == 0) {
                                            null
                                        } else {
                                            "It showed on $sent Benflix screen" + (if (sent == 1) "" else "s") + " right away."
                                        }
                                    // Benflix: picked people who aren't on Benflix right now see it next time they open it
                                    val later = (savedFor - reached).map { id -> names[id] ?: "Someone" }
                                    val saved =
                                        if (later.isEmpty()) {
                                            null
                                        } else {
                                            listNames(later) + (if (later.size == 1) " isn't" else " aren't") +
                                                " on Benflix right now, so it will pop up within a couple of minutes " +
                                                "if Benflix is open, or the next time they open it."
                                        }
                                    if (now == null && saved == null) {
                                        "Nobody to send to" to "No Benflix screens are open right now."
                                    } else {
                                        (if (now != null) "Pop-up sent" else "Message saved") to
                                            listOfNotNull(now, saved).joinToString(" ")
                                    }
                                }
                            },
                    )
            }
        }

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

    data object WritePopup : ServerToolsDialog

    /** Benflix: a pop-up message for one person (from their People menu) */
    data class WritePopupTo(
        val user: UserDto,
    ) : ServerToolsDialog

    data object AddPerson : ServerToolsDialog

    data class Person(
        val user: UserDto,
    ) : ServerToolsDialog

    /** Benflix: what you can do for a person (new password, quality away from home, remove) */
    data class PersonMenu(
        val user: UserDto,
    ) : ServerToolsDialog

    /** Benflix: the most a person can stream when they're away from home */
    data class AwayQuality(
        val user: UserDto,
    ) : ServerToolsDialog

    data class RemovePerson(
        val user: UserDto,
    ) : ServerToolsDialog

    /** Benflix: how long a Home note shows */
    data class NoteHowLong(
        val text: String,
    ) : ServerToolsDialog

    /** Benflix: who gets the Home note or pop-up (everyone, or picked people) */
    data class SendTo(
        val text: String,
        val popup: Boolean,
        val expiresAt: Long? = null,
        val picked: Set<java.util.UUID> = emptySet(),
    ) : ServerToolsDialog

    /** Benflix: how many screens at once */
    data class Screens(
        val user: UserDto,
    ) : ServerToolsDialog

    /** Benflix: which libraries they see */
    data class Libraries(
        val user: UserDto,
    ) : ServerToolsDialog

    /** Benflix: sign out on every device */
    data class SignOut(
        val user: UserDto,
    ) : ServerToolsDialog

    data class Hide(
        val item: CheckupItem,
    ) : ServerToolsDialog

    data class Problem(
        val report: ProblemReport,
    ) : ServerToolsDialog
}

/**
 * Benflix: one row on the Server page with its actions as real buttons (no hidden "hold OK").
 * TV: the buttons sit to the right of the row, so pressing right reaches them.
 * Phone: the buttons sit under the row so nothing gets squashed or cut off.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ServerActionRow(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: () -> Unit = {},
    /** Extra things under the supporting line, like a progress bar */
    extra: (@Composable () -> Unit)? = null,
    leading: (@Composable BoxScope.() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    actions: List<Pair<String, () -> Unit>> = emptyList(),
) {
    val isTv = rememberIsTv()
    val row: @Composable (Modifier) -> Unit = { m ->
        ListItem(
            selected = false,
            onClick = onClick,
            headlineContent = { Text(headline) },
            supportingContent =
                if (supporting != null || extra != null) {
                    {
                        Column {
                            supporting?.let { Text(it) }
                            extra?.invoke()
                        }
                    }
                } else {
                    null
                },
            leadingContent = leading,
            trailingContent = trailing,
            modifier = m,
            scale = ListItemDefaults.scale(focusedScale = 1f),
        )
    }
    // Benflix: every row sits on its own dark rounded card, like the Settings pages, so the page
    // isn't a wall of floating text on black
    val card =
        modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ServerCardColor)
    if (actions.isEmpty()) {
        row(card)
    } else if (isTv) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = card.padding(end = 12.dp),
        ) {
            row(Modifier.weight(1f))
            actions.forEach { (label, action) ->
                Button(onClick = action, scale = ButtonDefaults.scale(focusedScale = 1f)) { Text(label, fontSize = BenflixType.small) }
            }
        }
    } else {
        Column(modifier = card) {
            row(Modifier.fillMaxWidth())
            // Buttons wrap onto a second line when they don't fit
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 16.dp, end = 12.dp, bottom = 12.dp),
            ) {
                actions.forEach { (label, action) ->
                    Button(onClick = action, scale = ButtonDefaults.scale(focusedScale = 1f)) { Text(label, fontSize = BenflixType.small) }
                }
            }
        }
    }
}

/** Benflix: the Server page's card colour (neutral graphite, no red tint) */
internal val ServerCardColor =
    androidx.compose.ui.graphics
        .Color(0xFF1C1C21)

/** Benflix: problems people sent with "Report a problem" */
fun LazyListScope.problemReportItems(
    reports: List<ProblemReport>?,
    viewModel: ServerToolsViewModel,
    showDialog: (ServerToolsDialog) -> Unit,
    sectionTitle: @Composable (String) -> Unit,
    onFileDetails: (java.util.UUID) -> Unit = {},
    onFixMatch: (java.util.UUID) -> Unit = {},
) {
    item { sectionTitle(if (reports == null) "Problems reported" else "Problems reported (${reports.size})") }
    if (reports.isNullOrEmpty()) {
        item {
            ServerActionRow(headline = if (reports == null) "Checking…" else "Nobody reported a problem. Nice!")
        }
        return
    }
    items(reports, key = { "problem-${it.userId}-${it.at}" }) { report ->
        val day =
            java.time.Instant
                .ofEpochMilli(report.at)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
        ServerActionRow(
            headline = "${report.problem}: ${report.title}",
            supporting = "${report.userName} · ${report.device} · $day",
            onClick = { viewModel.openProblem(report) },
            actions =
                buildList {
                    val id = report.itemId
                    if (id != null) {
                        add("Open" to { viewModel.openProblem(report) })
                        // Benflix: the tool that usually fixes this kind of problem
                        if (report.problem == "Wrong movie or episode") {
                            add("Fix match" to { onFixMatch(id) })
                        } else {
                            add("File details" to { onFileDetails(id) })
                        }
                    }
                    add("Fixed" to { showDialog(ServerToolsDialog.Problem(report)) })
                },
        )
    }
}

/** Benflix: the note on Home, the welcome note and the pop-up */
fun LazyListScope.messageItems(
    state: ServerToolsState,
    showDialog: (ServerToolsDialog) -> Unit,
    sectionTitle: @Composable (String) -> Unit,
) {
    item { sectionTitle("Note on everyone's Home") }
    item {
        ServerActionRow(
            headline =
                when {
                    state.notesLoaded -> state.note?.text ?: "Write a note"
                    state.notesFailed -> NOTES_FAILED
                    else -> "Checking…"
                },
            supporting =
                if (state.note == null) {
                    "Like \"New Marvel movies added!\" It shows at the top of Home for everyone until they close it."
                } else {
                    "Showing on Home now" + (state.note.expiresAt?.let { " until ${untilText(it)}" } ?: "") +
                        ". Click to change it, or save it empty to remove it."
                },
            onClick = { showDialog(ServerToolsDialog.WriteNote) },
        )
    }
    item { sectionTitle("Welcome note") }
    item {
        ServerActionRow(
            headline =
                when {
                    state.notesLoaded -> state.welcome?.text ?: "Write a welcome note"
                    state.notesFailed -> NOTES_FAILED
                    else -> "Checking…"
                },
            supporting =
                if (state.welcome == null) {
                    "Everyone sees it once, on their first Home screen. Like \"Welcome to Benflix! Ask me if you can't find something.\""
                } else {
                    "Welcome note. Click to change it, or save it empty to remove it."
                },
            onClick = { showDialog(ServerToolsDialog.WriteWelcome) },
        )
    }
    item { sectionTitle("Pop-up") }
    if (state.liveBlocked) {
        item {
            Text(
                text = "Pop-ups can't arrive instantly right now. They still arrive within about 2 minutes. $LIVE_BLOCKED_HELP",
                color = Color(0xFFE5B567),
                fontSize = BenflixType.small,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
    item {
        ServerActionRow(
            headline = "Send a pop-up message",
            supporting =
                "To everyone, or tick just the people you want. It pops up right away. " +
                    "Someone you picked who isn't on Benflix sees it the next time they open it.",
            onClick = { showDialog(ServerToolsDialog.WritePopup) },
        )
    }
}

/** Benflix: what to fix on the server when live connections are blocked */
private const val LIVE_BLOCKED_HELP =
    "Your server's web address isn't letting live connections through. In Nginx Proxy Manager, edit the " +
        "Jellyfin proxy host and turn on \"Websockets Support\"."

/** Benflix: the device id every Benflix app has, so other Jellyfin apps aren't counted as reached */
private const val BENFLIX_DEVICE_PREFIX = "benflix-"

private const val NOTES_FAILED = "Couldn't read the notes. Press Refresh to try again."

/** Benflix: how long this device took to open Benflix last time */
fun LazyListScope.startupSpeedItem(sectionTitle: @Composable (String) -> Unit) {
    item {
        val context = LocalContext.current
        val last = remember { StartupTimer.last(context) }
        if (last != null) {
            Column {
                sectionTitle("This device")
                ServerActionRow(headline = "Start-up speed on this device", supporting = last)
            }
        }
    }
}

/** Benflix: everyone with an account, and Add a person */
fun LazyListScope.peopleItems(
    state: ServerToolsState,
    history: List<WatchedEntry>?,
    showDialog: (ServerToolsDialog) -> Unit,
    sectionTitle: @Composable (String) -> Unit,
) {
    val people = state.people
    item { sectionTitle(if (people == null) "People" else "People (${people.size})") }
    item {
        ServerActionRow(
            headline = "Add a person",
            supporting = "Makes their account and gives you a password and install code to send them",
            onClick = { showDialog(ServerToolsDialog.AddPerson) },
        )
    }
    if (people == null) {
        item {
            ServerActionRow(
                headline = if (state.peopleFailed) "Couldn't read the people list. Press Refresh to try again." else "Checking…",
            )
        }
        return
    }
    items(people, key = { "person-${it.id}" }) { user ->
        ServerActionRow(
            headline = user.name.orEmpty(),
            supporting =
                listOfNotNull(
                    if (user.policy?.isAdministrator == true) "Admin" else null,
                    if (user.policy?.isDisabled == true) "Turned off" else null,
                    user.lastActivityDate?.let { "Last seen ${it.toLocalDate()}" } ?: "Never signed in",
                    awayQualityLabel(user.policy?.remoteClientBitrateLimit)
                        .takeIf { (user.policy?.remoteClientBitrateLimit ?: 0) > 0 }
                        ?.let { "Away from home: $it" },
                    (user.policy?.maxActiveSessions ?: 0).takeIf { it > 0 }?.let { screensLabel(it) },
                    history?.let { list ->
                        val theirs = list.filter { it.userId == user.id }
                        if (theirs.isEmpty()) "Nothing watched this week" else "${theirs.size} watched this week"
                    },
                ).joinToString(" · ") +
                    (history?.firstOrNull { it.userId == user.id }?.let { "\nLast: ${lastWatched(it.text)}" } ?: ""),
            onClick = { showDialog(ServerToolsDialog.PersonMenu(user)) },
            actions = listOf("Options" to { showDialog(ServerToolsDialog.PersonMenu(user)) }),
        )
    }
}

/** Benflix: the Library checkup */
fun LazyListScope.checkupItems(
    state: ServerToolsState,
    viewModel: ServerToolsViewModel,
    showDialog: (ServerToolsDialog) -> Unit,
    sectionTitle: @Composable (String) -> Unit,
    /** Benflix: more buttons for some found things, like "Search" for missing episodes (Tower tools) */
    moreActions: (section: String, found: CheckupItem) -> List<Pair<String, () -> Unit>> = { _, _ -> emptyList() },
) {
    item { sectionTitle("Library checkup") }
    val checkup = state.checkup
    if (checkup == null) {
        item {
            ServerActionRow(
                headline = if (state.busy) "Checking…" else "Run a checkup",
                supporting = "Finds missing episodes, movies with no trailer, anime with no subtitles and low quality movies",
                onClick = { viewModel.runCheckup() },
            )
        }
        return
    }
    checkup.forEach { section ->
        item(key = "checkup-${section.title}") {
            ServerActionRow(
                headline = "${section.title} (${section.items.size})",
                supporting = if (section.items.isEmpty()) "Nothing found. Nice!" else null,
            )
        }
        items(section.items, key = { "check-${it.key}" }) { found ->
            ServerActionRow(
                headline = found.title,
                supporting = found.detail,
                onClick = { viewModel.open(found) },
                actions =
                    listOf("Open" to { viewModel.open(found) }) +
                        moreActions(section.title, found) +
                        listOf("Hide" to { showDialog(ServerToolsDialog.Hide(found)) }),
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
    if (state.checkupHidden > 0) {
        item {
            ServerActionRow(
                headline = "Show ${state.checkupHidden} hidden item(s) again",
                onClick = { viewModel.unhideAll() },
            )
        }
    }
    item {
        ServerActionRow(headline = "Run the checkup again", onClick = { viewModel.runCheckup() })
    }
}

/** The pop-ups for the Server tools */
@Composable
fun ServerToolsDialogs(
    state: ServerToolsState,
    viewModel: ServerToolsViewModel,
    dialog: ServerToolsDialog?,
    onDismiss: () -> Unit,
    showDialog: (ServerToolsDialog) -> Unit = {},
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
                    if (it.isBlank()) {
                        onDismiss()
                        viewModel.publishNote(it)
                    } else {
                        // Benflix: next, how long it shows and who gets it
                        showDialog(ServerToolsDialog.NoteHowLong(it))
                    }
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

        ServerToolsDialog.WritePopup -> {
            StringInputDialog(
                input =
                    StringInput(
                        title = "Send a pop-up message",
                        value = "",
                        keyboardOptions =
                            KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done,
                            ),
                        onSubmit = {},
                        maxLines = 3,
                    ),
                onSave = {
                    if (it.isBlank()) onDismiss() else showDialog(ServerToolsDialog.SendTo(it, popup = true))
                },
                onDismissRequest = onDismiss,
            )
        }

        is ServerToolsDialog.WritePopupTo -> {
            StringInputDialog(
                input =
                    StringInput(
                        title = "Message to ${dialog.user.name.orEmpty()}",
                        value = "",
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
                    if (it.isNotBlank()) viewModel.sendPopup(it, setOf(dialog.user.id))
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

        is ServerToolsDialog.PersonMenu -> {
            val user = dialog.user
            DialogPopup(
                showDialog = true,
                title = user.name.orEmpty(),
                dialogItems =
                    buildList {
                        add(
                            DialogItem(
                                headlineContent = { Text("Send a message") },
                                supportingContent = {
                                    Text("Pops up on their screen now, or the next time they open Benflix")
                                },
                                onClick = { showDialog(ServerToolsDialog.WritePopupTo(user)) },
                            ),
                        )
                        add(
                            DialogItem(
                                headlineContent = { Text("New password") },
                                supportingContent = { Text("Gives them a fresh password to sign in with") },
                                onClick = { showDialog(ServerToolsDialog.Person(user)) },
                            ),
                        )
                        add(
                            DialogItem(
                                headlineContent = {
                                    Text(
                                        "Quality away from home: ${awayQualityLabel(user.policy?.remoteClientBitrateLimit)}",
                                    )
                                },
                                supportingContent = { Text("The most they can stream when they're not on your home Wi-Fi") },
                                onClick = { showDialog(ServerToolsDialog.AwayQuality(user)) },
                            ),
                        )
                        add(
                            DialogItem(
                                headlineContent = { Text("Screens at once: ${screensLabel(user.policy?.maxActiveSessions)}") },
                                supportingContent = { Text("How many TVs or phones they can watch on at the same time") },
                                onClick = { showDialog(ServerToolsDialog.Screens(user)) },
                            ),
                        )
                        add(
                            DialogItem(
                                headlineContent = {
                                    Text(
                                        if (user.policy?.enableAllFolders != false) {
                                            "Libraries: all"
                                        } else {
                                            "Libraries: ${user.policy?.enabledFolders.orEmpty().size} picked"
                                        },
                                    )
                                },
                                supportingContent = { Text("Which libraries they can see") },
                                onClick = { showDialog(ServerToolsDialog.Libraries(user)) },
                            ),
                        )
                        add(
                            DialogItem(
                                headlineContent = { Text("Sign out everywhere") },
                                supportingContent = { Text("Like if they lost a phone or hit their screen limit") },
                                onClick = { showDialog(ServerToolsDialog.SignOut(user)) },
                            ),
                        )
                        if (user.policy?.isAdministrator != true) {
                            add(
                                DialogItem(
                                    headlineContent = { Text("Remove ${user.name}") },
                                    onClick = { showDialog(ServerToolsDialog.RemovePerson(user)) },
                                ),
                            )
                        }
                    },
                onDismissRequest = onDismiss,
                dismissOnClick = false,
                waitToLoad = false,
            )
        }

        is ServerToolsDialog.AwayQuality -> {
            val user = dialog.user
            val current = user.policy?.remoteClientBitrateLimit ?: 0
            DialogPopup(
                showDialog = true,
                title = "${user.name}: quality away from home",
                dialogItems =
                    AWAY_QUALITY_CHOICES.map { bps ->
                        DialogItem(
                            headlineContent = { Text(awayQualityLabel(bps)) },
                            supportingContent =
                                when (bps) {
                                    0 -> {
                                        { Text("Same as at home") }
                                    }

                                    4_000_000 -> {
                                        { Text("Good for phones, saves your upload") }
                                    }

                                    else -> {
                                        null
                                    }
                                },
                            selected = bps == current,
                            onClick = {
                                onDismiss()
                                viewModel.setAwayQuality(user, bps)
                            },
                        )
                    },
                onDismissRequest = onDismiss,
                dismissOnClick = false,
                waitToLoad = false,
            )
        }

        is ServerToolsDialog.NoteHowLong -> {
            val day = 24 * 60 * 60 * 1000L
            DialogPopup(
                showDialog = true,
                title = "Show the note until…",
                dialogItems =
                    listOf(
                        null to "I take it down",
                        day to "Tomorrow (1 day)",
                        3 * day to "3 days",
                        7 * day to "1 week",
                    ).map { (length, label) ->
                        DialogItem(
                            headlineContent = { Text(label) },
                            onClick = {
                                showDialog(
                                    ServerToolsDialog.SendTo(
                                        text = dialog.text,
                                        popup = false,
                                        expiresAt = length?.let { System.currentTimeMillis() + it },
                                    ),
                                )
                            },
                        )
                    },
                onDismissRequest = onDismiss,
                dismissOnClick = false,
                waitToLoad = false,
            )
        }

        is ServerToolsDialog.SendTo -> {
            val send: (Set<java.util.UUID>?) -> Unit = { who ->
                onDismiss()
                if (dialog.popup) {
                    viewModel.sendPopup(dialog.text, who)
                } else {
                    viewModel.publishNote(dialog.text, dialog.expiresAt, who)
                }
            }
            DialogPopup(
                showDialog = true,
                title = if (dialog.popup) "Pop it up for…" else "Put the note on…",
                dialogItems =
                    buildList {
                        add(
                            DialogItem(
                                headlineContent = {
                                    Text(
                                        if (dialog.picked.isEmpty()) "Everyone" else "Send to ${dialog.picked.size} picked",
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        if (dialog.picked.isEmpty()) {
                                            "Or tick people below first"
                                        } else {
                                            "Only the people ticked below. Anyone not on now sees it next time."
                                        },
                                    )
                                },
                                onClick = { send(dialog.picked.ifEmpty { null }) },
                            ),
                        )
                        state.people.orEmpty().forEach { user ->
                            add(
                                DialogItem(
                                    headlineContent = { Text(user.name.orEmpty()) },
                                    selected = user.id in dialog.picked,
                                    onClick = {
                                        val next = if (user.id in dialog.picked) dialog.picked - user.id else dialog.picked + user.id
                                        showDialog(dialog.copy(picked = next))
                                    },
                                ),
                            )
                        }
                    },
                onDismissRequest = onDismiss,
                dismissOnClick = false,
                waitToLoad = false,
            )
        }

        is ServerToolsDialog.Screens -> {
            val user = dialog.user
            val current = user.policy?.maxActiveSessions ?: 0
            DialogPopup(
                showDialog = true,
                title = "${user.name}: screens at once",
                dialogItems =
                    listOf(0, 1, 2, 3, 4, 5).map { n ->
                        DialogItem(
                            headlineContent = { Text(screensLabel(n)) },
                            selected = n == current,
                            onClick = {
                                onDismiss()
                                viewModel.setScreens(user, n)
                            },
                        )
                    },
                onDismissRequest = onDismiss,
                dismissOnClick = false,
                waitToLoad = false,
            )
        }

        is ServerToolsDialog.Libraries -> {
            // Read the person again each time, so ticks show straight after a change
            val user = state.people?.firstOrNull { it.id == dialog.user.id } ?: dialog.user
            val all = user.policy?.enableAllFolders != false
            val picked = user.policy?.enabledFolders.orEmpty()
            DialogPopup(
                showDialog = true,
                title = "${user.name} can see",
                dialogItems =
                    buildList {
                        add(
                            DialogItem(
                                headlineContent = { Text("Everything") },
                                supportingContent = { Text("Every library, including new ones you add later") },
                                selected = all,
                                onClick = { viewModel.toggleLibrary(user, null) },
                            ),
                        )
                        state.libraries.orEmpty().forEach { lib ->
                            add(
                                DialogItem(
                                    headlineContent = { Text(lib.name.orEmpty()) },
                                    selected = all || lib.id in picked,
                                    onClick = { viewModel.toggleLibrary(user, lib.id) },
                                ),
                            )
                        }
                    },
                onDismissRequest = onDismiss,
                dismissOnClick = false,
                waitToLoad = false,
            )
        }

        is ServerToolsDialog.SignOut -> {
            ConfirmDialog(
                title = "Sign ${dialog.user.name} out everywhere?",
                body = "They're signed out on every TV and phone (not this one). They'll need their password to sign in again.",
                onCancel = onDismiss,
                onConfirm = {
                    onDismiss()
                    viewModel.signOutEverywhere(dialog.user)
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

        is ServerToolsDialog.Problem -> {
            ConfirmDialog(
                title = "Mark \"${dialog.report.title}\" fixed?",
                body =
                    "${dialog.report.problem}, reported by ${dialog.report.userName} on ${dialog.report.device}. " +
                        "Their app log was sent to the server's log folder. This takes it off the list, " +
                        "and ${dialog.report.userName} sees \"Ben fixed ${dialog.report.title}\" on their Home.",
                onCancel = onDismiss,
                onConfirm = {
                    onDismiss()
                    viewModel.clearProblem(dialog.report)
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

fun screensLabel(screens: Int?): String =
    when {
        screens == null || screens <= 0 -> "No limit"
        screens == 1 -> "1 screen"
        else -> "$screens screens"
    }

/** Benflix: the choices for quality away from home, in bits per second (0 = no limit) */
val AWAY_QUALITY_CHOICES = listOf(0, 20_000_000, 10_000_000, 8_000_000, 4_000_000, 2_000_000)

fun awayQualityLabel(bitsPerSecond: Int?): String =
    when {
        bitsPerSecond == null || bitsPerSecond <= 0 -> "No limit"
        bitsPerSecond >= 20_000_000 -> "Best (20 Mbps)"
        bitsPerSecond >= 10_000_000 -> "High (10 Mbps)"
        bitsPerSecond >= 8_000_000 -> "HD (8 Mbps)"
        bitsPerSecond >= 4_000_000 -> "Data saver (4 Mbps)"
        else -> "Low (${bitsPerSecond / 1_000_000} Mbps)"
    }

/** Benflix: "Ben is playing The Office - S1E1 on Living Room TV" becomes "The Office - S1E1" */
internal fun lastWatched(logLine: String): String {
    val playing = logLine.substringAfter(" is playing ", logLine)
    return playing.substringBeforeLast(" on ").ifBlank { playing }
}

/** "Sam", "Sam and Alex", "Sam, Alex and Jo" */
internal fun listNames(names: List<String>): String =
    when (names.size) {
        0 -> ""
        1 -> names[0]
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }

/** Benflix: like "Wed 9:00 PM" */
internal fun untilText(at: Long): String =
    java.time.Instant
        .ofEpochMilli(at)
        .atZone(java.time.ZoneId.systemDefault())
        .format(
            java.time.format.DateTimeFormatter
                .ofPattern("EEE h:mm a"),
        )

/** Benflix: "What takes the most space" in the Library section */
fun LazyListScope.spaceItems(
    state: ServerToolsState,
    viewModel: ServerToolsViewModel,
    sectionTitle: @Composable (String) -> Unit,
) {
    item { sectionTitle("What takes the most space") }
    val space = state.space
    if (space == null) {
        item {
            ServerActionRow(
                headline = if (state.spaceBusy) "Measuring… (this can take a minute)" else "Find the biggest movies and shows",
                supporting = "Also lists things nobody has watched in a year, so you know what to delete when space runs low",
                onClick = { viewModel.runSpace() },
                actions = if (state.spaceBusy) emptyList() else listOf("Find" to { viewModel.runSpace() }),
            )
        }
        return
    }
    space.forEach { section ->
        item(key = "space-${section.title}") {
            ServerActionRow(
                headline = "${section.title} (${section.items.size})",
                supporting = if (section.items.isEmpty()) "Nothing found" else null,
            )
        }
        items(section.items, key = { "space-${section.title}-${it.key}" }) { found ->
            ServerActionRow(
                headline = found.title,
                supporting = found.detail,
                onClick = { viewModel.open(found) },
                actions = listOf("Open" to { viewModel.open(found) }),
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
    item {
        ServerActionRow(
            headline = if (state.spaceBusy) "Measuring…" else "Measure again",
            onClick = { viewModel.runSpace() },
        )
    }
}
