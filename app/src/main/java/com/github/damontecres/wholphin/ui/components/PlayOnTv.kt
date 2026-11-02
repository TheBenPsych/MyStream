package com.github.damontecres.wholphin.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ListItem
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.BACKSPACE
import com.github.damontecres.wholphin.services.BENFLIX_TYPE
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.model.api.GeneralCommand
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.PlayCommand
import org.jellyfin.sdk.model.api.PlaystateCommand
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration

/** Another Benflix (usually a TV) that's open right now and signed in as you */
data class BenflixScreen(
    val sessionId: String,
    val name: String,
)

/**
 * Benflix "Play on TV": the phone asks the server which other Benflix apps are open for this
 * person, and tells the one you pick to start playing. Benflix has to be open on the TV.
 */
@HiltViewModel
class PlayOnTvViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) : ViewModel() {
        /** null while looking */
        private val _screens = MutableStateFlow<List<BenflixScreen>?>(null)
        val screens: StateFlow<List<BenflixScreen>?> = _screens

        fun look() {
            _screens.value = null
            viewModelScope.launch {
                _screens.value =
                    try {
                        withContext(WholphinDispatchers.IO) {
                            val me = serverRepository.currentUser?.id
                            val myDevice = api.deviceInfo.id
                            api.sessionApi
                                .getSessions(controllableByUserId = me, activeWithinSeconds = ACTIVE_WITHIN_SECONDS)
                                .content
                                .filter {
                                    it.deviceId?.startsWith(BENFLIX_DEVICE_PREFIX) == true &&
                                        it.deviceId != myDevice &&
                                        it.supportsRemoteControl &&
                                        it.userId == me
                                }.distinctBy { it.deviceId }
                                .mapNotNull { s -> s.id?.let { BenflixScreen(it, s.deviceName ?: "Benflix") } }
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Play on TV: couldn't look for screens")
                        emptyList()
                    }
            }
        }

        suspend fun play(
            screen: BenflixScreen,
            item: BaseItem,
            position: Duration,
        ): Boolean =
            try {
                withContext(WholphinDispatchers.IO) {
                    api.sessionApi.play(
                        sessionId = screen.sessionId,
                        playCommand = PlayCommand.PLAY_NOW,
                        itemIds = listOf(item.id),
                        startPositionTicks = position.inWholeMilliseconds * TICKS_PER_MS,
                    )
                }
                true
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Play on TV: couldn't send it")
                false
            }

        /** Benflix phone remote: a remote button */
        fun press(
            screen: BenflixScreen,
            command: GeneralCommandType,
        ) = send { api.sessionApi.sendGeneralCommand(screen.sessionId, command) }

        /** Benflix phone remote: play/pause and skipping */
        fun playstate(
            screen: BenflixScreen,
            command: PlaystateCommand,
        ) = send { api.sessionApi.sendPlaystateCommand(screen.sessionId, command) }

        /** Benflix phone remote: types the text into the box that's selected on the TV */
        fun type(
            screen: BenflixScreen,
            text: String,
        ) = send {
            api.sessionApi.sendFullGeneralCommand(
                screen.sessionId,
                GeneralCommand(
                    name = GeneralCommandType.SEND_STRING,
                    controllingUserId = serverRepository.currentUser?.id,
                    arguments = mapOf("String" to text, BENFLIX_TYPE to "1"),
                ),
            )
        }

        private fun send(block: suspend () -> Unit) {
            viewModelScope.launch(WholphinDispatchers.IO) {
                try {
                    block()
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Remote: couldn't send")
                }
            }
        }

        companion object {
            private const val BENFLIX_DEVICE_PREFIX = "benflix-"
            private const val ACTIVE_WITHIN_SECONDS = 960
            private const val TICKS_PER_MS = 10_000L
        }
    }

/** The "Play on TV" button under Play on the phone's movie and episode pages */
@Composable
fun PlayOnTvButton(
    item: BaseItem,
    position: Duration,
    viewModel: PlayOnTvViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val screens by viewModel.screens.collectAsState()
    OutlinedButton(
        onClick = {
            viewModel.look()
            open = true
        },
        scale = OutlinedButtonDefaults.scale(focusedScale = 1f),
        shape = OutlinedButtonDefaults.shape(RoundedCornerShape(6.dp)),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text("Play on TV", fontWeight = FontWeight.Bold)
    }
    if (open) {
        BasicDialog(onDismissRequest = { open = false }) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.widthIn(min = 280.dp, max = 420.dp).padding(20.dp),
            ) {
                Text("Play on which TV?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                when (val list = screens) {
                    null -> {
                        Text("Looking for TVs…")
                    }

                    else -> {
                        if (list.isEmpty()) {
                            Text("No TV found. Open Benflix on the TV (signed in as you), then try again.")
                        }
                        list.forEach { screen ->
                            ListItem(
                                selected = false,
                                onClick = {
                                    viewModel.viewModelScope.launch {
                                        val sent = viewModel.play(screen, item, position)
                                        open = false
                                        val text = if (sent) "Playing on ${screen.name}" else "Couldn't reach ${screen.name}"
                                        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                headlineContent = { Text(screen.name) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Benflix "TV remote" on the phone: pick a TV, then big buttons that work like its remote.
 * Benflix has to be open on the TV.
 */
@Composable
fun PhoneRemoteDialog(
    onDismiss: () -> Unit,
    viewModel: PlayOnTvViewModel = hiltViewModel(),
) {
    val screens by viewModel.screens.collectAsState()
    var screen by remember { mutableStateOf<BenflixScreen?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.look() }
    BasicDialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth().padding(20.dp),
        ) {
            val tv = screen
            if (tv == null) {
                Text("Which TV?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                when (val list = screens) {
                    null -> {
                        Text("Looking for TVs…")
                    }

                    else -> {
                        if (list.isEmpty()) {
                            Text("No TV found. Open Benflix on the TV (signed in as you), then try again.")
                            RemoteKey("Look again") { viewModel.look() }
                        }
                        list.forEach { s -> RemoteKey(s.name, wide = true) { screen = s } }
                    }
                }
            } else {
                Text(tv.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                RemoteKey("▲") { viewModel.press(tv, GeneralCommandType.MOVE_UP) }
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    RemoteKey("◀") { viewModel.press(tv, GeneralCommandType.MOVE_LEFT) }
                    RemoteKey("OK", highlight = true) { viewModel.press(tv, GeneralCommandType.SELECT) }
                    RemoteKey("▶") { viewModel.press(tv, GeneralCommandType.MOVE_RIGHT) }
                }
                RemoteKey("▼") { viewModel.press(tv, GeneralCommandType.MOVE_DOWN) }
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    RemoteKey("Back") { viewModel.press(tv, GeneralCommandType.BACK) }
                    RemoteKey("Home") { viewModel.press(tv, GeneralCommandType.GO_HOME) }
                }
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    RemoteKey("−10s") { viewModel.playstate(tv, PlaystateCommand.REWIND) }
                    RemoteKey("⏯") { viewModel.playstate(tv, PlaystateCommand.PLAY_PAUSE) }
                    RemoteKey("+10s") { viewModel.playstate(tv, PlaystateCommand.FAST_FORWARD) }
                }
                var text by remember { mutableStateOf("") }
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle =
                        androidx.compose.ui.text
                            .TextStyle(color = androidx.compose.ui.graphics.Color.White, fontSize = 18.sp),
                    cursorBrush =
                        androidx.compose.ui.graphics
                            .SolidColor(androidx.compose.ui.graphics.Color.White),
                    decorationBox = { inner ->
                        androidx.compose.foundation.layout.Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(
                                        androidx.compose.ui.graphics
                                            .Color(0xFF26262B),
                                        RoundedCornerShape(8.dp),
                                    ).padding(12.dp),
                        ) {
                            if (text.isEmpty()) Text("Type for the TV's search box", color = androidx.compose.ui.graphics.Color.Gray)
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    RemoteKey("Send text") {
                        if (text.isNotBlank()) viewModel.type(tv, text)
                        text = ""
                    }
                    RemoteKey("⌫ Delete") { viewModel.type(tv, BACKSPACE) }
                }
            }
        }
    }
}

@Composable
private fun RemoteKey(
    label: String,
    wide: Boolean = false,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    androidx.compose.foundation.layout.Box(
        contentAlignment = androidx.compose.ui.Alignment.Center,
        modifier =
            Modifier
                .then(if (wide) Modifier.fillMaxWidth() else Modifier.widthIn(min = 76.dp))
                .background(
                    if (highlight) {
                        androidx.compose.ui.graphics
                            .Color(0xFFE50914)
                    } else {
                        androidx.compose.ui.graphics
                            .Color(0xFF26262B)
                    },
                    RoundedCornerShape(14.dp),
                ).clickable {
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onClick()
                }.padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
    }
}
