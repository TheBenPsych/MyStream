package com.github.damontecres.wholphin.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.services.holidayFor
import com.github.damontecres.wholphin.ui.BenflixIntroState
import com.github.damontecres.wholphin.ui.components.BenflixWordLogo
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.LoadingSpinner
import com.github.damontecres.wholphin.ui.dimAndBlur
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.serverSupportedMessage
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SwitchUserContent(
    server: JellyfinServer,
    modifier: Modifier = Modifier,
    viewModel: SwitchUserViewModel =
        hiltViewModel<SwitchUserViewModel, SwitchUserViewModel.Factory>(
            creationCallback = { it.create(server) },
        ),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        viewModel.init()
    }

    val state by viewModel.state.collectAsState()

    val currentUser by viewModel.serverRepository.currentUserFlow.collectAsState(null)
    var showAddUser by remember { mutableStateOf(false) }
    var addUser by remember(server) { mutableStateOf<JellyfinUser?>(null) }

    fun showAddUserDialog(user: JellyfinUser?) {
        addUser = user
        showAddUser = true
    }

    fun hideAddUserDialog() {
        addUser = null
        showAddUser = false
    }

    fun trySwitchUser(
        user: JellyfinUser,
        kids: Boolean = false,
        bigKids: Boolean = false,
        guest: Boolean = false,
    ) {
        // Benflix redesign: after picking someone, Home's doors grow out of the logo once
        BenflixIntroState.doorsPending = true
        // Kids / Guest are set by the view model, and only kept if the switch works
        val result = viewModel.trySwitchUser(user, kids, bigKids, guest)
        scope.launch {
            when (val r = result.await()) {
                is SwitchUserResult.Error -> {
                    com.github.damontecres.wholphin.ui.components.BenflixNotices
                        .pill(r.errorMessage)
                    if (r.showLogin) {
                        showAddUserDialog(user)
                    }
                }

                SwitchUserResult.Success -> {
                    // no-op, view model will navigate
                }
            }
        }
    }

    LaunchedEffect(state.switchUserState) {
        if (!showAddUser) {
            when (val s = state.switchUserState) {
                is LoadingState.Error -> {
                    val msg = s.message ?: s.exception?.localizedMessage
                    com.github.damontecres.wholphin.ui.components.BenflixNotices
                        .pill("Error: $msg")
                }

                else -> {}
            }
        }
    }
    var switchUserWithPin by remember { mutableStateOf<JellyfinUser?>(null) }

    when (val st = state.loading) {
        is LoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingSpinner(modifier)
        }

        LoadingState.Success -> {
            // Benflix redesign (w-row): a plain dark page, the logo on top, "Who's watching?" and one
            // centred row of round pictures with names under them. No poster wall, no fun facts.
            val introRunning by BenflixIntroState.running.collectAsState()
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose {
                    BenflixIntroState.whoMark = null
                    BenflixIntroState.whoLogo = null
                }
            }
            val isTvLayout = rememberIsTv()
            Box(
                modifier =
                    modifier
                        .background(BenflixColors.Tray)
                        .dimAndBlur(showAddUser || switchUserWithPin != null),
            ) {
                // Ben: the logo sits top left, exactly where the top bar's logo is, so after you pick a
                // profile the doors grow out of it in place. The intro's logo glides into this spot,
                // so it stays hidden until it lands
                BenflixWordLogo(
                    height = if (isTvLayout) 26.dp else 22.dp,
                    markModifier =
                        Modifier.onGloballyPositioned { BenflixIntroState.whoMark = it.boundsInWindow() },
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = if (isTvLayout) 40.dp else 16.dp, top = if (isTvLayout) 27.dp else 17.dp)
                            .onGloballyPositioned { BenflixIntroState.whoLogo = it.boundsInWindow() }
                            .graphicsLayer { alpha = if (introRunning) 0f else 1f },
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = if (isTvLayout) 40.dp else 32.dp),
                ) {
                    androidx.compose.foundation.layout
                        .Spacer(Modifier.weight(1f))
                    val holiday = remember { holidayFor(LocalDate.now()) }
                    Text(
                        text = holiday?.text ?: stringResource(R.string.select_user),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = BenflixColors.Text,
                        textAlign = TextAlign.Center,
                    )
                    androidx.compose.foundation.layout
                        .Spacer(Modifier.height(36.dp))
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalProfileTileSize provides if (isTvLayout) 120.dp else 88.dp,
                    ) {
                        UserList(
                            users = state.users,
                            currentUser = currentUser,
                            onSwitchUser = { user ->
                                if (user.accessToken == null || user.requireLogin) {
                                    showAddUserDialog(user)
                                } else if (user.hasPin) {
                                    switchUserWithPin = user
                                } else {
                                    trySwitchUser(user)
                                }
                            },
                            onAddUser = {
                                showAddUserDialog(null)
                            },
                            // Built-in Kids profile, using a saved grown-up sign-in on this TV. The parent PIN,
                            // bedtime and limit belong to the whole TV (see BenflixParentPin), not this sign-in
                            kidsUser =
                                state.users
                                    .map { it.user }
                                    .takeIf { users -> users.none { it.name?.trim().equals("kids", ignoreCase = true) } }
                                    ?.firstOrNull { it.accessToken != null && !it.requireLogin },
                            onKids = { user -> trySwitchUser(user, kids = true) },
                            showBigKids = remember { KidsMode.bigKidsTile(context) },
                            onBigKids = { user -> trySwitchUser(user, kids = true, bigKids = true) },
                            showGuest = remember { GuestMode.tileOn(context) },
                            onGuest = { user -> trySwitchUser(user, guest = true) },
                            onRemoveUser = { user ->
                                viewModel.removeUser(user)
                            },
                            onPickPicture = { user, index -> viewModel.pickPicture(user, index) },
                            onSwitchServer = {
                                viewModel.setupNavigationManager.navigateTo(
                                    SetupDestination.ServerList,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    androidx.compose.foundation.layout
                        .Spacer(Modifier.weight(1.3f))
                }
                val resources = LocalResources.current
                val message =
                    remember(resources, state.serverVersionSupported) {
                        resources.serverSupportedMessage(
                            state.serverVersionSupported,
                            state.serverVersion,
                        )
                    }
                SwitchErrorMessage(message)
            }
        }
    }

    if (showAddUser) {
        LaunchedEffect(Unit) {
            viewModel.clearSwitchUserState()
            viewModel.resetAttempts()
        }
        // Benflix redesign: one question per screen with the keyboard underneath (replaces the old pop-up)
        val error =
            (state.switchUserState as? LoadingState.Error)?.let { e ->
                e.message ?: e.exception?.localizedMessage ?: "That didn't work. Check and try again."
            }
        SignInScreens(
            serverName = server.name ?: server.url,
            serverUrl = server.url,
            knownName = addUser?.name,
            phoneAllowed = state.quickConnectEnabled,
            phoneCode = state.quickConnectStatus?.code,
            error = error,
            attempts = state.loginAttempts,
            actions =
                SignInActions(
                    login = { name, password -> viewModel.login(server, addUser, name, password) },
                    startPhone = { viewModel.initiateQuickConnect(server, addUser) },
                    stopPhone = { viewModel.cancelQuickConnect() },
                    clearError = { viewModel.clearSwitchUserState() },
                    openDebug = { viewModel.navigationManager.navigateTo(Destination.Debug) },
                    close = {
                        viewModel.cancelQuickConnect()
                        hideAddUserDialog()
                    },
                ),
        )
    }
    switchUserWithPin?.let { user ->
        PinEntryDialog(
            heading =
                if (com.github.damontecres.wholphin.services.KidsMode.isActive) {
                    "Leaving ${com.github.damontecres.wholphin.services.KidsMode.displayName} for ${user.name ?: "a grown-up"}"
                } else {
                    null
                },
            onDismissRequest = { switchUserWithPin = null },
            onClickServerAuth = {
                showAddUserDialog(user)
                switchUserWithPin = null
            },
            onTextChange = {
                if (it == user.pin) {
                    trySwitchUser(user)
                }
            },
        )
    }
}

/** Screens shorter than this (in dp) get the compact Who's watching layout */
private const val SHORT_SCREEN_DP = 600

/** The smallest the Who's watching tiles get */
private const val MIN_TILE_DP = 72f
