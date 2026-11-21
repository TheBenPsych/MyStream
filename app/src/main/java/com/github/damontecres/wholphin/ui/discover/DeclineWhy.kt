package com.github.damontecres.wholphin.ui.discover

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.services.GameLibraryClient
import com.github.damontecres.wholphin.ui.components.BenflixChoice
import com.github.damontecres.wholphin.ui.components.BenflixChoiceDialog
import com.github.damontecres.wholphin.ui.preferences.StringInput
import com.github.damontecres.wholphin.ui.preferences.StringInputDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

/**
 * Why Ben said no (Ben 2026-11-21). Right after Ben declines a movie or show request he's asked
 * "Tell them why?": a ready-made reason, his own words, or skip. Tower keeps it, and whoever asked
 * sees it on their list instead of a bare "no".
 */
object DeclineWhy {
    val presets =
        listOf(
            "We already have it",
            "Not a good fit for Glowfin",
            "Too big for Tower right now",
            "It isn't out yet",
            "Ask me about this one",
        )

    private val _asking = MutableStateFlow<Int?>(null)

    /** The Seerr request Ben just said no to, while the "Tell them why?" box is up */
    val asking: StateFlow<Int?> = _asking

    private val _reasons = MutableStateFlow<Map<String, String>>(emptyMap())

    /** "seerr:123" -> "We already have it" */
    val reasons: StateFlow<Map<String, String>> = _reasons

    fun ask(requestId: Int) {
        _asking.value = requestId
    }

    fun done() {
        _asking.value = null
    }

    fun key(requestId: Int) = "seerr:$requestId"

    internal fun loaded(map: Map<String, String>) {
        _reasons.value = map
    }

    internal fun remember(
        requestId: Int,
        reason: String,
    ) {
        _reasons.update { it + (key(requestId) to reason) }
    }
}

@HiltViewModel
class DeclineWhyViewModel
    @Inject
    constructor(
        private val helper: GameLibraryClient,
    ) : ViewModel() {
        fun load() {
            viewModelScope.launch {
                try {
                    val j = helper.getJson("/api/asks/reasons").optJSONObject("reasons") ?: return@launch
                    val out = mutableMapOf<String, String>()
                    j.keys().forEach { k -> j.optString(k).takeIf { it.isNotBlank() }?.let { out[k] = it } }
                    DeclineWhy.loaded(out)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    // An older Tower without this yet: declines simply show no reason
                    Timber.w(ex, "Decline reasons: Tower didn't answer")
                }
            }
        }

        fun send(
            requestId: Int,
            reason: String,
        ) {
            val words = reason.trim().take(140)
            if (words.isEmpty()) return
            DeclineWhy.remember(requestId, words)
            viewModelScope.launch {
                try {
                    helper.postJson(
                        "/api/asks/reason",
                        JSONObject().put("key", DeclineWhy.key(requestId)).put("reason", words),
                    )
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Decline reasons: couldn't save the reason for %s", requestId)
                }
            }
        }
    }

/**
 * Sits on the pages where Ben can say no. Loads the saved reasons, and shows the "Tell them why?"
 * box whenever [DeclineWhy.ask] is called.
 */
@Composable
fun DeclineWhyHost(viewModel: DeclineWhyViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.load() }
    val asking by DeclineWhy.asking.collectAsState()
    var typing by remember { mutableStateOf<Int?>(null) }
    asking?.let { id ->
        BenflixChoiceDialog(
            title = "Tell them why?",
            subtitle = "They'll see this next to their request.",
            choices =
                DeclineWhy.presets.map { reason ->
                    BenflixChoice(label = reason, onClick = { viewModel.send(id, reason) })
                } +
                    BenflixChoice(label = "Type my own reason", onClick = { typing = id }) +
                    BenflixChoice(label = "Skip", onClick = {}),
            onDismissRequest = { DeclineWhy.done() },
        )
    }
    typing?.let { id ->
        StringInputDialog(
            input =
                StringInput(
                    title = "Why not?",
                    value = "",
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                    onSubmit = {},
                ),
            onSave = { typed ->
                typing = null
                viewModel.send(id, typed)
            },
            onDismissRequest = { typing = null },
        )
    }
}

/** One person in Ben's Auto-yes list */
data class AutoYesPerson(
    val id: String,
    val name: String,
    val on: Boolean,
    val weekly: Int,
    val used: Int,
)

/**
 * Auto-yes (Ben 2026-11-21, Ben only): Tower says yes by itself to movie and show requests from the
 * people Ben switches on, up to their number each week. Never Kids.
 */
@HiltViewModel
class AutoYesViewModel
    @Inject
    constructor(
        private val helper: GameLibraryClient,
    ) : ViewModel() {
        val people = MutableStateFlow<List<AutoYesPerson>?>(null)
        val problem = MutableStateFlow<String?>(null)

        fun load() {
            viewModelScope.launch {
                try {
                    val list = helper.getJson("/api/asks/trust").optJSONArray("people")
                    people.value =
                        (0 until (list?.length() ?: 0)).mapNotNull { i ->
                            val o = list?.optJSONObject(i) ?: return@mapNotNull null
                            AutoYesPerson(
                                id = o.optString("id"),
                                name = o.optString("name"),
                                on = o.optBoolean("on"),
                                weekly = o.optInt("weekly", 3),
                                used = o.optInt("used", 0),
                            )
                        }
                    problem.value = null
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Auto-yes: Tower didn't answer")
                    problem.value = "Tower didn't answer. It may still be updating itself, so try again in an hour."
                }
            }
        }

        fun set(
            person: AutoYesPerson,
            on: Boolean,
            weekly: Int,
        ) {
            people.update { list -> list?.map { if (it.id == person.id) it.copy(on = on, weekly = weekly) else it } }
            viewModelScope.launch {
                try {
                    helper.postJson(
                        "/api/asks/trust",
                        JSONObject().put("id", person.id).put("on", on).put("weekly", weekly),
                    )
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Auto-yes: couldn't save %s", person.name)
                    problem.value = "Couldn't save that. Try again."
                }
            }
        }
    }

/** Ben's Auto-yes box: pick a person, then Off or how many a week */
@Composable
fun AutoYesDialog(
    onDismiss: () -> Unit,
    viewModel: AutoYesViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val people by viewModel.people.collectAsState()
    val problem by viewModel.problem.collectAsState()
    var pickedId by remember { mutableStateOf<String?>(null) }
    // Picking a row closes the list first and then runs the row, so the box only really closes when
    // the list went away without a person being picked (Done or Back)
    var listOpen by remember { mutableStateOf(true) }
    LaunchedEffect(listOpen, pickedId) { if (!listOpen && pickedId == null) onDismiss() }
    val picked = people?.firstOrNull { it.id == pickedId }
    if (picked != null) {
        BenflixChoiceDialog(
            title = picked.name,
            subtitle = "How many movies and shows a week get a yes without waiting for you?",
            choices =
                listOf(
                    BenflixChoice(
                        label = "Off",
                        note = "Every request waits for you",
                        selected = !picked.on,
                        onClick = { viewModel.set(picked, false, picked.weekly) },
                    ),
                ) +
                    listOf(1, 3, 5, 10).map { n ->
                        BenflixChoice(
                            label = "$n a week",
                            selected = picked.on && picked.weekly == n,
                            onClick = { viewModel.set(picked, true, n) },
                        )
                    },
            onDismissRequest = {
                pickedId = null
                listOpen = true
            },
        )
    } else if (listOpen) {
        val list = people
        BenflixChoiceDialog(
            title = "Auto-yes",
            subtitle =
                problem
                    ?: when {
                        list == null -> "Asking Tower…"
                        list.isEmpty() -> "Nobody else has a profile yet."
                        else -> "Tower says yes for you, up to each person's number a week. Kids never get this."
                    },
            choices =
                list.orEmpty().map { p ->
                    BenflixChoice(
                        label = p.name,
                        note = if (p.on) "${p.used} of ${p.weekly} used this week" else null,
                        value = if (p.on) "${p.weekly} a week" else "Off",
                        onClick = { pickedId = p.id },
                    )
                } + BenflixChoice(label = "Done", onClick = {}),
            onDismissRequest = { listOpen = false },
        )
    }
}
