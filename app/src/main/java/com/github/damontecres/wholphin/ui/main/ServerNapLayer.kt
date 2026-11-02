package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.services.ServerNap
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.services.SetupNavigationManager
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.systemApi
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ServerNapViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val setupNavigationManager: SetupNavigationManager,
    ) : ViewModel() {
        /** Seconds until the next check, for "Checking again in 15 seconds" */
        private val _countdown = MutableStateFlow(CHECK_EVERY_S)
        val countdown: StateFlow<Int> = _countdown

        /** Hidden for a while after "Watch my downloads" */
        private val _hiddenUntil = MutableStateFlow(0L)
        val hiddenUntil: StateFlow<Long> = _hiddenUntil

        private var job: Job? = null

        /** Keeps checking while the server is down; when it's back, goes to Who's watching */
        fun watch() {
            if (job?.isActive == true) return
            job =
                viewModelScope.launch {
                    while (isActive && ServerNap.down.value) {
                        for (s in CHECK_EVERY_S downTo 1) {
                            _countdown.value = s
                            delay(1_000)
                            if (!ServerNap.down.value) break
                        }
                        if (ServerNap.down.value) checkNow()
                    }
                    backToProfiles()
                }
        }

        suspend fun checkNow() {
            try {
                withContext(WholphinDispatchers.IO) { api.systemApi.getPublicSystemInfo() }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.v("Server still napping")
            }
        }

        fun tryNow() {
            viewModelScope.launch { checkNow() }
        }

        fun hideForAWhile() {
            _hiddenUntil.value = System.currentTimeMillis() + HIDE_MS
        }

        private fun backToProfiles() {
            val server = serverRepository.current.value?.server ?: return
            setupNavigationManager.navigateTo(SetupDestination.UserList(server))
        }

        companion object {
            private const val CHECK_EVERY_S = 15
            private const val HIDE_MS = 10 * 60_000L
        }
    }

/**
 * Benflix: when the server can't be reached, a calm full-screen "taking a nap" message instead
 * of errors. It keeps checking, and when the server is back it goes to Who's watching.
 */
@Composable
fun ServerNapLayer(viewModel: ServerNapViewModel = hiltViewModel()) {
    val down by ServerNap.down.collectAsState()
    val hiddenUntil by viewModel.hiddenUntil.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(down) { if (down) viewModel.watch() }
    LaunchedEffect(hiddenUntil) {
        if (hiddenUntil > System.currentTimeMillis()) {
            delay(hiddenUntil - System.currentTimeMillis())
            tick++
        }
    }
    // tick changes when the "Watch my downloads" break is over, so this is checked again
    if (!down || (tick >= 0 && System.currentTimeMillis() < hiddenUntil)) return
    val countdown by viewModel.countdown.collectAsState()
    val isTv = rememberIsTv()
    val focus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(listOf(Color(0xFF1B1446), Color(0xFF050510)))),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(max = 620.dp).padding(24.dp),
            ) {
                Text("BENFLIX", fontFamily = BenflixTitleFont, fontSize = 56.sp, color = Color(0xFFE50914))
                Text(
                    "The server is taking a nap, or being worked on",
                    fontSize = 24.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Nothing is wrong with your TV. Benflix will keep checking and bring you back as soon as it's up.",
                    fontSize = 17.sp,
                    color = Color(0xFFD8D8F0),
                    textAlign = TextAlign.Center,
                )
                Text("Checking again in $countdown seconds…", fontSize = 15.sp, color = Color(0xFFA19D99))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = viewModel::tryNow, modifier = Modifier.focusRequester(focus)) { Text("Try now") }
                    if (!isTv) {
                        OutlinedButton(onClick = viewModel::hideForAWhile) { Text("Watch my downloads") }
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            delay(200)
            focus.tryRequestFocus()
        }
    }
}
