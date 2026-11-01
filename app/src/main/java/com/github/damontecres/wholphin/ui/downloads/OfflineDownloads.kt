package com.github.damontecres.wholphin.ui.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.JellyfinServerDao
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.services.DownloadService
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.setup.PinEntryDialog
import com.github.damontecres.wholphin.ui.setup.UserIconCardImage
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/** Whose downloads to show when there's no internet */
data class OfflineProfile(
    val userId: UUID?,
    val kids: Boolean,
)

@HiltViewModel
class OfflineDownloadsViewModel
    @Inject
    constructor(
        val service: DownloadService,
        private val serverDao: JellyfinServerDao,
    ) : ViewModel() {
        private val _users = MutableStateFlow<List<JellyfinUser>>(emptyList())

        /** Profiles saved on this phone that have downloads */
        val users: StateFlow<List<JellyfinUser>> = _users

        init {
            viewModelScope.launch {
                try {
                    val withDownloads =
                        service.items.value
                            .mapNotNull { it.userId }
                            .toSet()
                    _users.value =
                        withContext(WholphinDispatchers.IO) {
                            serverDao
                                .getServers()
                                .flatMap { it.users }
                                .filter { it.id in withDownloads }
                                .distinctBy { it.id }
                        }
                } catch (ex: Exception) {
                    Timber.e(ex, "Could not load profiles for offline downloads")
                }
            }
        }
    }

/**
 * Benflix on phones: a Downloads button on the start screens, so saved movies and episodes can be watched
 * even when the server can't be reached (no internet, on a plane). You still pick your profile first,
 * and profiles with a PIN still ask for it. Kids only sees what was downloaded in Kids.
 */
@Composable
fun OfflineDownloadsButton(
    modifier: Modifier = Modifier,
    showWhenEmpty: Boolean = false,
) {
    if (rememberIsTv()) return
    // The "You're offline" screen has its own big Downloads button
    if (OfflineScreenState.showing.value > 0) return
    val viewModel: OfflineDownloadsViewModel = hiltViewModel()
    val all by viewModel.service.items.collectAsState()
    if (all.isEmpty() && !showWhenEmpty) return
    var open by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .navigationBarsPadding()
                .padding(16.dp)
                .background(Color(0xF22B2B2B), RoundedCornerShape(24.dp))
                .clickable { open = true }
                .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(text = stringResource(R.string.fa_download), fontFamily = FontAwesome, fontSize = 16.sp, color = Color.White)
        Text(
            text = stringResource(R.string.downloads_title),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
    if (open) {
        Dialog(
            onDismissRequest = { open = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            OfflineDownloads(viewModel, onClose = { open = false })
        }
    }
}

@Composable
private fun OfflineDownloads(
    viewModel: OfflineDownloadsViewModel,
    onClose: () -> Unit,
) {
    val users by viewModel.users.collectAsState()
    val all by viewModel.service.items.collectAsState()
    val hasKids = all.any { it.kids }
    var profile by remember { mutableStateOf<OfflineProfile?>(null) }
    var askPinFor by remember { mutableStateOf<JellyfinUser?>(null) }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        val chosen = profile
        if (chosen != null) {
            DownloadsPage(
                onClose = { profile = null },
                offlineProfile = chosen,
                modifier = Modifier.fillMaxSize(),
            )
            return@Box
        }
        BackHandler(onBack = onClose)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.downloads_whos_watching),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 24.dp),
            )
            if (all.isEmpty()) {
                Text(
                    text = stringResource(R.string.downloads_none),
                    fontSize = 15.sp,
                    color = Color(0xFFB3B3B3),
                    textAlign = TextAlign.Center,
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(users, key = { it.id }) { user ->
                    ProfileTile(user.id, user.name.orEmpty()) {
                        if (user.hasPin) askPinFor = user else profile = OfflineProfile(user.id, false)
                    }
                }
                if (hasKids) {
                    item("kids") {
                        ProfileTile(KIDS_ID, stringResource(R.string.kids_profile)) {
                            profile = OfflineProfile(null, true)
                        }
                    }
                }
            }
        }
    }
    askPinFor?.let { user ->
        PinEntryDialog(
            onDismissRequest = { askPinFor = null },
            onClickServerAuth = { askPinFor = null },
            onTextChange = {
                if (it == user.pin) {
                    askPinFor = null
                    profile = OfflineProfile(user.id, false)
                }
            },
        )
    }
}

@Composable
private fun ProfileTile(
    id: UUID,
    name: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .clickable(onClick = onClick)
                .padding(8.dp),
    ) {
        UserIconCardImage(id = id, name = name, imageUrl = null, modifier = Modifier.size(96.dp))
        Text(
            text = name,
            fontSize = 16.sp,
            color = Color.White,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private val KIDS_ID = UUID(0L, 1L)

/** Counts how many "You're offline" screens are up, so the small floating Downloads button can hide */
object OfflineScreenState {
    val showing = mutableStateOf(0)
}

/** True when the error is just "can't reach the server" (no internet, server off, wrong address) */
fun isOfflineError(exception: Throwable?): Boolean {
    var e = exception
    while (e != null) {
        if (e is java.net.UnknownHostException ||
            e is java.net.ConnectException ||
            e is java.net.SocketTimeoutException ||
            e is java.net.NoRouteToHostException ||
            e.message?.contains("unreachable", ignoreCase = true) == true
        ) {
            return true
        }
        e = e.cause
    }
    return false
}

/**
 * Benflix: a clean "You're offline" screen instead of a red error, with the logo, a big Downloads button
 * (phones) and Try again. The technical details are tucked away behind "Details".
 */
@Composable
fun OfflineScreen(
    exception: Throwable?,
    modifier: Modifier = Modifier,
) {
    val isTv = rememberIsTv()
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        OfflineScreenState.showing.value++
        onDispose { OfflineScreenState.showing.value-- }
    }
    var openDownloads by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    val focus =
        remember {
            androidx.compose.ui.focus
                .FocusRequester()
        }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (_: Exception) {
        }
    }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
        ) {
            com.github.damontecres.wholphin.ui.nav.BenflixLogo(
                modifier = Modifier.padding(bottom = 24.dp),
            )
            Text(
                text = stringResource(R.string.fa_wifi),
                fontFamily = FontAwesome,
                fontSize = 44.sp,
                color = Color(0xFF6D6D6D),
            )
            Text(
                text = stringResource(R.string.offline_title),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(if (isTv) R.string.offline_body_tv else R.string.offline_body),
                fontSize = 16.sp,
                color = Color(0xFFB3B3B3),
                textAlign = TextAlign.Center,
            )
            if (!isTv) {
                OfflineButton(
                    icon = R.string.fa_download,
                    label = stringResource(R.string.offline_go_to_downloads),
                    primary = true,
                    onClick = { openDownloads = true },
                    modifier = Modifier.padding(top = 12.dp).focusRequester(focus),
                )
            }
            OfflineButton(
                icon = R.string.fa_rotate_right,
                label = stringResource(R.string.offline_try_again),
                primary = isTv,
                onClick = { (context as? android.app.Activity)?.recreate() },
                modifier = if (isTv) Modifier.padding(top = 12.dp).focusRequester(focus) else Modifier,
            )
            Text(
                text = stringResource(if (showDetails) R.string.offline_hide_details else R.string.offline_details),
                fontSize = 13.sp,
                color = Color(0xFF808080),
                modifier =
                    Modifier
                        .padding(top = 8.dp)
                        .clickable { showDetails = !showDetails }
                        .padding(8.dp),
            )
            if (showDetails) {
                Text(
                    text =
                        generateSequence(exception) { it.cause }
                            .mapNotNull { it.localizedMessage }
                            .distinct()
                            .joinToString("\n"),
                    fontSize = 12.sp,
                    color = Color(0xFF808080),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    if (openDownloads) {
        val viewModel: OfflineDownloadsViewModel = hiltViewModel()
        Dialog(
            onDismissRequest = { openDownloads = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            OfflineDownloads(viewModel, onClose = { openDownloads = false })
        }
    }
}

@Composable
private fun OfflineButton(
    icon: Int,
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.tv.material3.Button(
        onClick = onClick,
        shape =
            androidx.tv.material3.ButtonDefaults
                .shape(RoundedCornerShape(6.dp)),
        colors =
            androidx.tv.material3.ButtonDefaults.colors(
                containerColor = if (primary) BENFLIX_RED else Color(0xFF333333),
                contentColor = Color.White,
                focusedContainerColor = if (primary) Color(0xFFF6121D) else Color(0xFF4D4D4D),
                focusedContentColor = Color.White,
            ),
        modifier = modifier.widthIn(min = 240.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(icon), fontFamily = FontAwesome, fontSize = 16.sp)
            Text(text = label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private val BENFLIX_RED = Color(0xFFE50914)
