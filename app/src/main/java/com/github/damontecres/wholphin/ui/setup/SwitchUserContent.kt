package com.github.damontecres.wholphin.ui.setup

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.SetupDestination
import com.github.damontecres.wholphin.services.holidayFor
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.EditTextBox
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.QrCode
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.dimAndBlur
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.serverSupportedMessage
import com.github.damontecres.wholphin.ui.theme.BenflixRed
import com.github.damontecres.wholphin.ui.touch.dialogWidth
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
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
    var username by remember(addUser) { mutableStateOf(addUser?.name ?: "") }

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
    ) {
        KidsMode.set(kids, bigKids)
        val result = viewModel.trySwitchUser(user)
        scope.launch {
            when (val r = result.await()) {
                is SwitchUserResult.Error -> {
                    Toast.makeText(context, r.errorMessage, Toast.LENGTH_LONG).show()
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
                    Toast.makeText(context, "Error: $msg", Toast.LENGTH_LONG).show()
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
            LoadingPage(modifier)
        }

        LoadingState.Success -> {
            // A drawn holiday scene on holidays, otherwise a slow wall of the server's posters
            val holiday = remember { holidayFor(LocalDate.now()) }
            Box(
                modifier =
                    modifier
                        .background(MaterialTheme.colorScheme.background)
                        .dimAndBlur(showAddUser || switchUserWithPin != null),
            ) {
                if (holiday != null) {
                    HolidayScene(holiday.effect, Modifier.fillMaxSize())
                } else {
                    PosterWall(state.posterWall, Modifier.fillMaxSize())
                }
                // Benflix: the profiles fill the space above the fun fact, so the two never overlap
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    // A soft dark glow behind the text and profiles, with no hard edges
                                    .drawBehind {
                                        val glow =
                                            Brush.radialGradient(
                                                0f to Color.Black.copy(alpha = .6f),
                                                .45f to Color.Black.copy(alpha = .4f),
                                                .72f to Color.Transparent,
                                                center = center,
                                                radius = size.width / 2,
                                            )
                                        scale(1f, (size.height * 1.5f / size.width).coerceAtMost(1f), pivot = center) {
                                            drawCircle(glow, radius = size.width / 2, center = center)
                                        }
                                    }.padding(16.dp),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // The holiday greeting, big and in the holiday's own lettering
                                holiday?.let {
                                    Text(
                                        text = it.text,
                                        fontFamily = holidayFont(it.effect),
                                        fontSize = 52.sp,
                                        color = Color(it.color),
                                        textAlign = TextAlign.Center,
                                        style =
                                            TextStyle(
                                                shadow = Shadow(Color(it.color).copy(alpha = .7f), blurRadius = 28f),
                                            ),
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.select_user),
                                    style = MaterialTheme.typography.displaySmall.copy(shadow = ReadableTextShadow),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = server.name ?: server.url,
                                    style = MaterialTheme.typography.titleLarge.copy(shadow = ReadableTextShadow),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
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
                                // Built-in Kids profile, using a saved grown-up sign-in on this TV
                                kidsUser =
                                    state.users
                                        .map { it.user }
                                        .takeIf { users -> users.none { it.name?.trim().equals("kids", ignoreCase = true) } }
                                        ?.firstOrNull { it.accessToken != null && !it.requireLogin },
                                onKids = { user -> trySwitchUser(user, kids = true) },
                                showBigKids = remember { KidsMode.bigKidsTile(context) },
                                onBigKids = { user -> trySwitchUser(user, kids = true, bigKids = true) },
                                onRemoveUser = { user ->
                                    viewModel.removeUser(user)
                                },
                                onSwitchServer = {
                                    viewModel.setupNavigationManager.navigateTo(
                                        SetupDestination.ServerList,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    // Benflix: a "Did you know?" fact at the bottom middle, below Switch server
                    // A fixed space for it, so the profiles don't jump when the fact fades in
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().height(120.dp).padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                    ) {
                        FunFactCard(fact = state.funFact)
                    }
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
        var useQuickConnect by remember { mutableStateOf(state.quickConnectEnabled) }
        LaunchedEffect(Unit) {
            viewModel.clearSwitchUserState()
            viewModel.resetAttempts()
            if (useQuickConnect) {
                viewModel.initiateQuickConnect(server, addUser)
            }
        }
        BasicDialog(
            onDismissRequest = {
                viewModel.cancelQuickConnect()
                hideAddUserDialog()
            },
            properties =
                DialogProperties(
                    usePlatformDefaultWidth = false,
                ),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .focusGroup()
                        .padding(16.dp)
                        .fillMaxWidth(dialogWidth(if (useQuickConnect) .6f else .4f)),
            ) {
                if (useQuickConnect) {
                    if (state.quickConnectStatus == null && state.switchUserState !is LoadingState.Error) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier =
                                Modifier
                                    .height(32.dp)
                                    .align(Alignment.CenterHorizontally),
                        ) {
                            CircularProgress(Modifier.size(20.dp))
                            Text(
                                text = "Waiting for Quick Connect code...",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier,
                            )
                        }
                    } else if (state.quickConnectStatus != null) {
                        // Benflix: "Sign in with your phone", with a QR code to scan on TVs
                        val isTv = rememberIsTv()
                        val code = state.quickConnectStatus?.code
                        Text(
                            text = "Sign in with your phone",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            if (isTv) {
                                QrCode(
                                    text = server.url.trimEnd('/') + "/web/#/quickconnect",
                                    modifier = Modifier.size(180.dp),
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (isTv) {
                                    Text(
                                        text = "1. Scan this with your phone's camera and sign in",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Text(
                                    text =
                                        (if (isTv) "2. " else "") +
                                            "Type this code in Quick Connect (or in Benflix on a phone that's signed in: Settings, Quick Connect)",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = code?.chunked(3)?.joinToString(" ") ?: "Failed to get code",
                                    style = MaterialTheme.typography.displayMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Waiting for your phone…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    UserStateError(state.switchUserState)
                    TextButton(
                        stringRes = R.string.username_or_password,
                        onClick = {
                            viewModel.cancelQuickConnect()
                            viewModel.clearSwitchUserState()
                            useQuickConnect = false
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                } else {
                    var password by remember { mutableStateOf("") }
                    val onSubmit = {
                        viewModel.login(
                            server,
                            addUser,
                            username,
                            password,
                        )
                    }
                    val focusRequester = remember { FocusRequester() }
                    val passwordFocusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) {
                        if (username.isBlank()) {
                            focusRequester.tryRequestFocus()
                        } else {
                            passwordFocusRequester.tryRequestFocus()
                        }
                    }
                    Text(
                        text = "Enter username/password to login to ${server.name ?: server.url}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    UserStateError(state.switchUserState)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            text = stringResource(R.string.username),
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        EditTextBox(
                            value = username,
                            onValueChange = { username = it },
                            keyboardOptions =
                                KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Next,
                                ),
                            keyboardActions =
                                KeyboardActions(
                                    onNext = {
                                        passwordFocusRequester.tryRequestFocus()
                                    },
                                ),
                            //                                onKeyboardAction = {
//                                    passwordFocusRequester.tryRequestFocus()
//                                },
                            isInputValid = { state.switchUserState !is LoadingState.Error },
                            modifier = Modifier.focusRequester(focusRequester),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            text = stringResource(R.string.password),
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        LaunchedEffect(password) {
                            viewModel.clearSwitchUserState()
                        }
                        EditTextBox(
                            value = password,
                            onValueChange = { password = it },
                            keyboardOptions =
                                KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Go,
                                ),
                            keyboardActions =
                                KeyboardActions(
                                    onGo = { onSubmit.invoke() },
                                ),
                            isInputValid = { state.switchUserState !is LoadingState.Error },
                            modifier = Modifier.focusRequester(passwordFocusRequester),
                        )
                    }
                    TextButton(
                        stringRes = R.string.login,
                        onClick = { onSubmit.invoke() },
                        enabled = username.isNotNullOrBlank(),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                if (state.loginAttempts > 2) {
                    Text(
                        text = "Trouble logging in?",
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    TextButton(
                        stringRes = R.string.show_debug_info,
                        onClick = {
                            viewModel.navigationManager.navigateTo(Destination.Debug)
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
    switchUserWithPin?.let { user ->
        PinEntryDialog(
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

@Composable
private fun UserStateError(
    userState: LoadingState,
    modifier: Modifier = Modifier,
) {
    when (val s = userState) {
        is LoadingState.Error -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = modifier,
            ) {
                s.message?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (s.exception != null) {
                    s.exception.localizedMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    s.exception.cause?.localizedMessage?.let {
                        Text(
                            text = "Cause: $it",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        else -> {}
    }
}

/** The "Did you know?" fact on the Who's watching screen. It fades in once it's ready. */
@Composable
private fun FunFactCard(
    fact: String?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = fact != null,
        enter = fadeIn(tween(1_200)),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .widthIn(max = 640.dp)
                    .background(Color.Black.copy(alpha = .55f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(
                text = "DID YOU KNOW?",
                color = BenflixRed,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Text(
                text = fact.orEmpty(),
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
            )
        }
    }
}
