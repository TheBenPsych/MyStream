package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.OmdbScores
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OmdbKeyViewModel
    @Inject
    constructor(
        val omdbScores: OmdbScores,
    ) : ViewModel() {
        fun refresh() {
            viewModelScope.launch { omdbScores.loadKey() }
        }

        fun save(
            key: String,
            onDone: (String?) -> Unit,
        ) {
            viewModelScope.launch { onDone(omdbScores.saveKey(key)) }
        }
    }

/**
 * Benflix (admins, Settings > More): type the free OMDb key once. It's checked, then saved to every account,
 * so IMDb and Rotten Tomatoes scores show on the Requests pages on every device.
 */
@Composable
fun OmdbKeySetting(
    title: String,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    viewModel: OmdbKeyViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.refresh() }
    val key by viewModel.omdbScores.key.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    ClickPreference(
        title = title,
        onClick = { showDialog = true },
        modifier = modifier,
        summary =
            stringResource(if (key == null) R.string.benflix_omdb_key_off else R.string.benflix_omdb_key_on),
        onLongClick = null,
        interactionSource = interactionSource,
    )
    if (showDialog) {
        StringInputDialog(
            input =
                StringInput(
                    title = "OMDb key (from the OMDb email)",
                    value = key.orEmpty(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done,
                        ),
                    onSubmit = {},
                ),
            onSave = { typed ->
                showDialog = false
                viewModel.save(typed) { problem ->
                    val message =
                        problem
                            ?: if (typed.isBlank()) "OMDb key removed" else "Key works! Scores will fill in on the Requests pages."
                    com.github.damontecres.wholphin.ui.components.BenflixNotices
                        .pill(message)
                }
            },
            onDismissRequest = { showDialog = false },
        )
    }
}
