package com.github.damontecres.wholphin.ui.setup

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.components.BenflixWordLogo
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.delay

/*
 * Benflix redesign (approved 2026-11-07): on a brand-new device with no server yet, look for one on
 * the home Wi-Fi first and ask "Is this your server?". If none turns up, go straight to typing the
 * address, with the same keyboard as signing in.
 */

private enum class FirstServerStep { LOOKING, FOUND, TYPE }

@Composable
fun FirstServerScreen(
    state: SwitchServerState,
    viewModel: SwitchServerViewModel,
    modifier: Modifier = Modifier,
) {
    val isTv = rememberIsTv()
    var step by rememberSaveable { mutableStateOf(FirstServerStep.LOOKING) }
    var address by rememberSaveable { mutableStateOf("") }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.discoverServers() else step = FirstServerStep.TYPE
        }
    LaunchedEffect(Unit) {
        viewModel.clearAddServerState()
        if (step != FirstServerStep.LOOKING) return@LaunchedEffect
        if (viewModel.hasPermission) {
            viewModel.discoverServers()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            permission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
        delay(LOOK_MS)
        if (step == FirstServerStep.LOOKING) step = FirstServerStep.TYPE
    }
    LaunchedEffect(state.discoveredServers.isNotEmpty()) {
        if (state.discoveredServers.isNotEmpty() && step == FirstServerStep.LOOKING) step = FirstServerStep.FOUND
    }
    val connecting = state.addServerState is LoadingState.Loading
    val error =
        (state.addServerState as? LoadingState.Error)?.let {
            "Couldn't reach that server. Check the address and try again."
        }
    Box(
        modifier
            .fillMaxSize()
            .background(BenflixColors.Tray),
    ) {
        BenflixWordLogo(
            height = if (isTv) 26.dp else 22.dp,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = if (isTv) BenflixSpace.tvEdge else BenflixSpace.phoneEdge, top = if (isTv) 27.dp else 17.dp),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = if (isTv) 72.dp else 64.dp, bottom = 24.dp, start = 16.dp, end = 16.dp),
        ) {
            when (step) {
                FirstServerStep.LOOKING -> {
                    Question("Looking for your server…", "Checking your home Wi-Fi")
                }

                FirstServerStep.FOUND -> {
                    Question("Is this your server?", "We found it on your Wi-Fi")
                    val first = remember { FocusRequester() }
                    LaunchedEffect(Unit) { first.tryRequestFocus() }
                    state.discoveredServers.take(MAX_FOUND).forEachIndexed { i, server ->
                        SignInButton(
                            label = "Yes, ${server.name ?: server.url}",
                            white = true,
                            big = true,
                            enabled = !connecting,
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                        ) { viewModel.addServer(server.url, showToast = false) }
                        Text(text = server.url, color = BenflixColors.Muted, fontSize = BenflixType.small)
                    }
                    SignInButton("No, type the address", enabled = !connecting) {
                        viewModel.clearAddServerState()
                        step = FirstServerStep.TYPE
                    }
                }

                FirstServerStep.TYPE -> {
                    Question("What's your server address?", "Like 10.0.0.58:8096 or myserver.duckdns.org")
                    Answer(
                        value = address,
                        onValue = {
                            address = it
                            viewModel.clearAddServerState()
                        },
                        password = false,
                        error = error,
                        isTv = isTv,
                        onDone = { if (address.isNotBlank() && !connecting) viewModel.addServer(address.trim(), showToast = false) },
                        fieldTag = "server_url_text",
                    )
                    SignInButton(
                        label = "Next",
                        white = true,
                        enabled = address.isNotBlank() && !connecting,
                        modifier = Modifier.padding(top = 6.dp),
                    ) { viewModel.addServer(address.trim(), showToast = false) }
                }
            }
            if (connecting) {
                Text(text = "Connecting…", color = BenflixColors.Soft, fontSize = BenflixType.body)
            }
            if (step == FirstServerStep.FOUND && error != null) {
                Text(text = error, color = BenflixColors.Soft, fontSize = BenflixType.small)
            }
        }
    }
}

private const val LOOK_MS = 4000L
private const val MAX_FOUND = 3
