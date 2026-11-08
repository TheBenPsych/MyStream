package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.ServerReportService
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ErrorViewModel
    @Inject
    constructor(
        private val serverReportService: ServerReportService,
    ) : ViewModel() {
        fun sendLogs() {
            viewModelScope.launchIO { serverReportService.sendAppLogs() }
        }
    }

/** A plain-words version of what went wrong, for the friendly error screen */
private data class FriendlyError(
    val title: String,
    val body: String,
)

private fun friendlyError(exception: Throwable?): FriendlyError {
    var e = exception
    var status: Int? = null
    while (e != null && status == null) {
        status = (e as? org.jellyfin.sdk.api.client.exception.InvalidStatusException)?.status
        e = e.cause
    }
    return when (status) {
        401, 403 -> FriendlyError("Your sign-in needs a refresh", "Switch profile or sign in again from the profile menu.")
        404 -> FriendlyError("We couldn't find that", "It may have been moved or removed from Tower.")
        in 500..599 -> FriendlyError("Tower had a problem", "Give it a minute, then go back and try again.")
        else -> FriendlyError("Something went wrong", "This page didn't load. Go back and try again, or send the details to Ben.")
    }
}

/**
 * Benflix's friendly error screen (polish round, Nov 2026): the logo squares, one plain sentence and
 * clear buttons. The technical details are still there behind "Details", so they can be sent along.
 */
@Composable
fun ErrorMessage(
    message: String?,
    exception: Throwable?,
    modifier: Modifier = Modifier,
    viewModel: ErrorViewModel = hiltViewModel(),
) {
    // Benflix: can't reach the server? Show a clean "You're offline" screen instead of a red error
    if (com.github.damontecres.wholphin.ui.downloads
            .isOfflineError(exception)
    ) {
        com.github.damontecres.wholphin.ui.downloads
            .OfflineScreen(exception, modifier)
        return
    }
    val friendly = remember(exception) { friendlyError(exception) }
    var showDetails by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    val back = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
    Box(modifier = modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.widthIn(max = 520.dp),
        ) {
            BenflixMark(size = 40.dp, modifier = Modifier.padding(bottom = 8.dp))
            Text(
                text = friendly.title,
                fontSize = BenflixType.pageTitle,
                fontWeight = FontWeight.Bold,
                color = BenflixColors.Text,
                textAlign = TextAlign.Center,
            )
            Text(
                text = friendly.body,
                fontSize = BenflixType.body,
                color = BenflixColors.Soft,
                textAlign = TextAlign.Center,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                if (back != null) {
                    TextButton(
                        onClick = { back.onBackPressedDispatcher.onBackPressed() },
                    ) { Text("Go back") }
                }
                TextButton(
                    onClick = {
                        viewModel.sendLogs()
                        sent = true
                    },
                ) { Text(if (sent) "Sent to Ben" else "Send details to Ben") }
                TextButton(onClick = { showDetails = !showDetails }) {
                    Text(if (showDetails) "Hide details" else "Details")
                }
            }
            if (showDetails) {
                val details =
                    buildList {
                        message?.let { add(it) }
                        exception?.localizedMessage?.let { add(it) }
                        var cause = exception?.cause
                        while (cause != null) {
                            cause.localizedMessage?.let { add("Caused by: $it") }
                            cause = cause.cause
                        }
                    }
                details.forEach {
                    Text(
                        text = it,
                        fontSize = BenflixType.small,
                        color = BenflixColors.Muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorMessage(
    error: LoadingState.Error,
    modifier: Modifier = Modifier,
) = ErrorMessage(
    message = error.message,
    exception = error.exception,
    modifier = modifier,
)

@Composable
fun ErrorMessage(
    error: DataLoadingState.Error,
    modifier: Modifier = Modifier,
) = ErrorMessage(
    message = error.message,
    exception = error.exception,
    modifier = modifier,
)
