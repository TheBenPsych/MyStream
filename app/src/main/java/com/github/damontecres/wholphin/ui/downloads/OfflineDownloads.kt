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
