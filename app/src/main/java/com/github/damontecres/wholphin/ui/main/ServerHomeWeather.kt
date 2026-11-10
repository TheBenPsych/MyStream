package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.services.BenflixTower
import com.github.damontecres.wholphin.services.HomeTown
import com.github.damontecres.wholphin.ui.preferences.StringInput
import com.github.damontecres.wholphin.ui.preferences.StringInputDialog
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Benflix Server page (Tools): the town for Home's weather line. Ben types it once; Tower looks it up and
 * checks that town's weather every hour, so no TV or phone ever uses its own location (Ben keeps it off).
 */
@HiltViewModel
class HomeWeatherViewModel
    @Inject
    constructor(
        private val tower: BenflixTower,
    ) : ViewModel() {
        private val _town = MutableStateFlow<HomeTown?>(null)
        val town: StateFlow<HomeTown?> = _town

        private val _problem = MutableStateFlow<String?>(null)
        val problem: StateFlow<String?> = _problem

        private val _busy = MutableStateFlow(false)
        val busy: StateFlow<Boolean> = _busy

        fun load() {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    _town.value = tower.homeTown()
                    _problem.value = null
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    _problem.value = ex.message ?: "Couldn't reach Tower."
                }
            }
        }

        fun save(
            town: String,
            celsius: Boolean,
            onDone: (String) -> Unit,
        ) {
            _busy.value = true
            viewModelScope.launch(WholphinDispatchers.IO) {
                val words =
                    try {
                        val (message, now) = tower.setHomeTown(town, celsius)
                        _town.value = now
                        _problem.value = null
                        message
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        ex.message ?: "Couldn't reach Tower."
                    } finally {
                        _busy.value = false
                    }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDone(words) }
            }
        }
    }

internal fun LazyListScope.homeWeatherItems() {
    item { SectionTitle("Home weather") }
    item(key = "home-weather") { HomeWeatherRow() }
}

@Composable
private fun HomeWeatherRow(viewModel: HomeWeatherViewModel = hiltViewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.load() }
    val town by viewModel.town.collectAsState()
    val problem by viewModel.problem.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var typing by remember { mutableStateOf(false) }
    val done: (String) -> Unit = {
        com.github.damontecres.wholphin.ui.components.BenflixNotices
            .pill(it)
    }
    val current = town
    when {
        current == null && problem != null -> {
            ServerActionRow(
                headline = "Couldn't open the weather setting",
                supporting = problem,
                leading = { Dot(Light.UNKNOWN) },
                onClick = { viewModel.load() },
                actions = listOf("Try again" to { viewModel.load() }),
            )
        }

        current == null -> {
            LoadingBox("Asking Tower…")
        }

        current.town.isBlank() -> {
            ServerActionRow(
                headline = "Weather is off",
                supporting =
                    "Type your town once and Home shows the weather under the greeting on every TV, phone and browser. " +
                        "Tower checks it every hour. No TV or phone uses its own location.",
                onClick = { typing = true },
                actions = listOf("Type your town" to { typing = true }),
            )
        }

        else -> {
            val units = if (current.celsius) "°C" else "°F"
            ServerActionRow(
                headline = current.place.ifBlank { current.town },
                supporting =
                    listOfNotNull(
                        current.now?.let { "Right now: $it" },
                        "In $units. Tower checks it every hour.",
                    ).joinToString(" · "),
                leading = { Dot(if (current.now != null) Light.GOOD else Light.UNKNOWN) },
                onClick = { typing = true },
                actions =
                    if (busy) {
                        emptyList()
                    } else {
                        listOf(
                            "Change town" to { typing = true },
                            (if (current.celsius) "Use °F" else "Use °C") to { viewModel.save(current.town, !current.celsius, done) },
                            "Turn off" to { viewModel.save("", current.celsius, done) },
                        )
                    },
            )
        }
    }
    if (typing) {
        StringInputDialog(
            input =
                StringInput(
                    title = "Your town (add the state or country after a comma if needed)",
                    value = current?.town.orEmpty(),
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done,
                        ),
                    onSubmit = {},
                ),
            onSave = { typed ->
                typing = false
                viewModel.save(typed.trim(), current?.celsius ?: false, done)
            },
            onDismissRequest = { typing = false },
        )
    }
}
