package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.services.BenflixTower
import com.github.damontecres.wholphin.services.DriveHealth
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

/** What the Drive health list shows: null drives while looking */
data class DriveList(
    val setUp: Boolean,
    val drives: List<DriveHealth>,
)

/**
 * Benflix Server page (Tools > Tower > Drive health), Idea Box 24: every drive in Tower checks its own health, and the
 * Scrutiny app on Tower reads those checks and spots the warning signs of a drive wearing out. A drive with warning
 * signs also shows on Overview.
 */
@HiltViewModel
class DriveHealthViewModel
    @Inject
    constructor(
        private val tower: BenflixTower,
    ) : ViewModel() {
        private val _list = MutableStateFlow<DriveList?>(null)
        val list: StateFlow<DriveList?> = _list

        private val _failed = MutableStateFlow<String?>(null)
        val failed: StateFlow<String?> = _failed

        fun load(fresh: Boolean = false) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    val (setUp, drives) = tower.drives(fresh)
                    _list.value = DriveList(setUp, drives)
                    _failed.value = null
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Drive health")
                    _failed.value = ex.message ?: "Couldn't ask Tower."
                }
            }
        }
    }

internal fun LazyListScope.driveHealthItems() {
    item(key = "drives-title") { SectionTitle("Drive health") }
    item(key = "drives") { DriveRows() }
}

/** "Failing" is red, other warning signs yellow */
internal fun driveLight(d: DriveHealth): Light =
    when {
        d.ok -> Light.GOOD
        d.words.startsWith("Failing") -> Light.BAD
        else -> Light.WARN
    }

private fun sizeText(bytes: Long): String =
    when {
        bytes <= 0 -> ""
        bytes >= 1_000_000_000_000 -> String.format(Locale.US, "%.1f TB", bytes / 1e12)
        else -> "${bytes / 1_000_000_000} GB"
    }

private fun onText(hours: Long?): String =
    when {
        hours == null -> ""
        hours >= 8760 -> String.format(Locale.US, "%.1f years switched on", hours / 8760.0)
        else -> "${hours / 24} days switched on"
    }

@Composable
private fun DriveRows(viewModel: DriveHealthViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.load() }
    val list by viewModel.list.collectAsState()
    val failed by viewModel.failed.collectAsState()
    val now = list
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            now == null && failed != null -> {
                ServerActionRow(
                    headline = "Couldn't check the drives",
                    supporting = failed,
                    leading = { Dot(Light.UNKNOWN) },
                    onClick = { viewModel.load(true) },
                    actions = listOf("Try again" to { viewModel.load(true) }),
                )
            }

            now == null -> {
                LoadingBox("Checking the drives…")
            }

            !now.setUp -> {
                ServerActionRow(
                    headline = "Scrutiny isn't connected yet",
                    supporting =
                        "Install Scrutiny from Apps on Tower (search \"scrutiny\"), then run the setup command again. " +
                            "It warns you before a drive wears out.",
                    leading = { Dot(Light.UNKNOWN) },
                )
            }

            now.drives.isEmpty() -> {
                ServerActionRow(
                    headline = "No drives checked yet",
                    supporting = "Scrutiny checks every drive once a day.",
                    leading = { Dot(Light.UNKNOWN) },
                    actions = listOf("Check again" to { viewModel.load(true) }),
                )
            }

            else -> {
                now.drives.forEach { d ->
                    ServerActionRow(
                        headline = d.name,
                        supporting =
                            listOf(
                                d.words,
                                sizeText(d.size),
                                d.temp?.let { "$it°C" }.orEmpty(),
                                onText(d.hours),
                                d.where,
                            ).filter { it.isNotBlank() }
                                .joinToString(" · "),
                        leading = { Dot(driveLight(d)) },
                    )
                }
                ServerActionRow(
                    headline = "Check again now",
                    supporting = "Scrutiny checks every drive once a day by itself.",
                    leading = { Dot(Light.GOOD) },
                    onClick = { viewModel.load(true) },
                )
            }
        }
    }
}
