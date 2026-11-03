package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.BenflixMessage
import com.github.damontecres.wholphin.services.BenflixMessages
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.BenflixPopups
import com.github.damontecres.wholphin.ui.components.popupTurn
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixRed
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Benflix: pop-up messages from Ben, live ones and ones saved for the next time you open Benflix */
@HiltViewModel
class MessageNoticeViewModel
    @Inject
    constructor(
        private val messages: BenflixMessages,
        serverRepository: ServerRepository,
    ) : ViewModel() {
        val waiting: StateFlow<List<BenflixMessage>> = messages.waiting

        init {
            viewModelScope.launch(WholphinDispatchers.IO) {
                serverRepository.current
                    .map { it?.user?.id }
                    .distinctUntilChanged()
                    .collectLatest { userId ->
                        if (userId != null) {
                            // Let Home settle first
                            delay(4_000)
                            messages.checkSaved(userId)
                        }
                    }
            }
        }

        fun done(message: BenflixMessage) = messages.done(message)
    }

/** Sits on top of every page. Live messages show even while watching; saved ones wait until playback stops. */
@Composable
fun MessageLayer(
    currentDestination: Destination?,
    viewModel: MessageNoticeViewModel = hiltViewModel(),
) {
    val waiting by viewModel.waiting.collectAsState()
    val playing = currentDestination is Destination.Playback || currentDestination is Destination.PlaybackList
    val message = waiting.firstOrNull { it.live || !playing }
    if (!popupTurn("ben-message", BenflixPopups.MESSAGE, message != null)) return
    message ?: return
    val focus = remember(message.id) { FocusRequester() }
    val isTv = rememberIsTv()
    BasicDialog(onDismissRequest = { viewModel.done(message) }) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .widthIn(max = 560.dp)
                    .padding(if (isTv) 28.dp else 20.dp),
        ) {
            Text(
                text = message.header.uppercase(),
                color = BenflixRed,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontSize = 14.sp,
            )
            Text(
                text = message.text,
                color = Color.White,
                fontSize = if (isTv) 22.sp else 18.sp,
                lineHeight = if (isTv) 30.sp else 25.sp,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis,
            )
            if (!message.live) {
                Text(
                    text = "Sent while you were away",
                    color = Color(0xFF9A9AA3),
                    fontSize = 13.sp,
                )
            }
            Button(
                onClick = { viewModel.done(message) },
                modifier = Modifier.padding(top = 6.dp).focusRequester(focus),
            ) {
                Text("Got it")
            }
        }
    }
    LaunchedEffect(message.id) {
        delay(200)
        try {
            focus.requestFocus()
        } catch (_: IllegalStateException) {
        }
        // A live message closes by itself after a while, so a TV nobody is looking at doesn't keep it up forever
        if (message.live) {
            delay(LIVE_SHOW_MS)
            viewModel.done(message)
        }
    }
}

private const val LIVE_SHOW_MS = 60_000L
