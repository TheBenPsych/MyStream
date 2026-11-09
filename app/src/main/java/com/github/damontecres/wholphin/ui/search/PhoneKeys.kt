package com.github.damontecres.wholphin.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.RemoteKeys
import com.github.damontecres.wholphin.services.RemoteKeysRequest
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import javax.inject.Inject

/** Benflix "Type on your phone for the TV" (see [RemoteKeys]) */
@HiltViewModel
class PhoneKeysViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) : ViewModel() {
        private var sendJob: Job? = null

        /** TV: the search keyboard opened or closed */
        fun announce(
            open: Boolean,
            text: String = "",
        ) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                withContext(NonCancellable) { RemoteKeys.announce(api, serverRepository.currentUser?.id, open, text) }
            }
        }

        /** Phone: send what's typed (a moment after typing stops, so a fast typist doesn't flood the TV) */
        fun send(
            request: RemoteKeysRequest,
            text: String,
        ) {
            sendJob?.cancel()
            sendJob =
                viewModelScope.launch(WholphinDispatchers.IO) {
                    delay(SEND_DELAY_MS)
                    RemoteKeys.send(api, serverRepository.currentUser?.id, request, text)
                }
        }

        companion object {
            private const val SEND_DELAY_MS = 250L
        }
    }

/**
 * TV: while the search keyboard shows, phones on this account offer to type, and what they type
 * lands in [onQueryChange].
 */
@Composable
fun PhoneKeysForTv(
    query: String,
    onQueryChange: (String) -> Unit,
    viewModel: PhoneKeysViewModel = hiltViewModel(),
) {
    val latest by rememberUpdatedState(onQueryChange)
    DisposableEffect(Unit) {
        viewModel.announce(true, query)
        onDispose { viewModel.announce(false) }
    }
    LaunchedEffect(Unit) {
        RemoteKeys.incoming.collect { latest(it) }
    }
}

/** Phone: the keyboard pop-up, shown anywhere in the app while a TV is waiting for typing */
@Composable
fun PhoneKeysPrompt(viewModel: PhoneKeysViewModel = hiltViewModel()) {
    if (rememberIsTv()) return
    val request by RemoteKeys.request.collectAsState()
    val req = request ?: return
    var text by remember(req.tvSessionId) { mutableStateOf(req.text) }
    val field = remember { FocusRequester() }
    LaunchedEffect(req.tvSessionId) {
        delay(150)
        field.tryRequestFocus()
    }
    BasicDialog(onDismissRequest = { RemoteKeys.dismiss() }) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(20.dp),
        ) {
            Text(
                text = "Type for ${req.tvName}",
                color = BenflixColors.Text,
                fontSize = BenflixType.rowName,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "The TV is on Search. What you type shows up there.",
                color = BenflixColors.Muted,
                fontSize = BenflixType.small,
            )
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                    viewModel.send(req, it)
                },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = BenflixType.lead),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { RemoteKeys.dismiss() }),
                decorationBox = { inner ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF26262B), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                    ) {
                        if (text.isEmpty()) Text("Movie, show or actor", color = Color.Gray)
                        inner()
                    }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(field),
            )
            Button(onClick = { RemoteKeys.dismiss() }) { Text("Done") }
        }
    }
}
