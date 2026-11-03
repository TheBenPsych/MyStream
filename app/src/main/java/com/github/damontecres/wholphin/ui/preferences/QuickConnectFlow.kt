package com.github.damontecres.wholphin.ui.preferences

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Inject

/** Benflix: signs another TV or phone in with the 6-digit Quick Connect code it shows */
@HiltViewModel
class QuickConnectViewModel
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
    ) : ViewModel() {
        private val _status = MutableStateFlow<LoadingState>(LoadingState.Pending)
        val status: StateFlow<LoadingState> = _status

        fun reset() {
            _status.value = LoadingState.Pending
        }

        fun authorize(code: String) {
            viewModelScope.launchIO {
                _status.value = LoadingState.Loading
                try {
                    val ok = serverRepository.authorizeQuickConnect(code)
                    _status.value = if (ok) LoadingState.Success else LoadingState.Error("failed")
                } catch (e: Exception) {
                    Timber.w(e, "Quick Connect failed")
                    _status.value = LoadingState.Error(e)
                }
            }
        }
    }

/**
 * The Quick Connect pop-up: type the code, the other device signs in as you.
 * Used by Settings (TV and phone) and the round button on the phone My Benflix page.
 */
@Composable
fun BenflixQuickConnect(
    onDismiss: () -> Unit,
    viewModel: QuickConnectViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsState()
    val successMessage = stringResource(R.string.quick_connect_success)
    LaunchedEffect(Unit) { viewModel.reset() }
    LaunchedEffect(status) {
        if (status == LoadingState.Success) {
            Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
            viewModel.reset()
            onDismiss()
        }
    }
    QuickConnectDialog(
        onSubmit = { viewModel.authorize(it) },
        onDismissRequest = {
            viewModel.reset()
            onDismiss()
        },
        serverError = if (status is LoadingState.Error) stringResource(R.string.quick_connect_failed) else null,
        busy = status == LoadingState.Loading,
    )
}
