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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.github.damontecres.wholphin.ui.theme.ChristmasSeason
import com.github.damontecres.wholphin.ui.theme.EarthDaySeason
import com.github.damontecres.wholphin.ui.theme.Friday13
import com.github.damontecres.wholphin.ui.theme.HalloweenSeason
import com.github.damontecres.wholphin.ui.theme.JulySeason
import com.github.damontecres.wholphin.ui.theme.NewYearSeason
import com.github.damontecres.wholphin.ui.theme.PopcornSeason
import com.github.damontecres.wholphin.ui.theme.StPatricksSeason
import com.github.damontecres.wholphin.ui.theme.StarWarsDay
import com.github.damontecres.wholphin.ui.theme.StarWarsFont
import com.github.damontecres.wholphin.ui.theme.ThanksgivingSeason
import com.github.damontecres.wholphin.ui.theme.ValentineSeason
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

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
    // Friday the 13th: the sleeping cat wakes up and yawns when someone picks a profile
    val f13Room =
        remember {
            com.github.damontecres.wholphin.ui.theme.Friday13
                .on()
        }
    var catWaking by remember { mutableStateOf(false) }
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
        scope.launch {
            // Friday the 13th: give the cat a moment to open its eyes and yawn before Home opens
            if (f13Room) {
                // A second press while the cat is waking up does nothing
                if (catWaking) return@launch
                catWaking = true
                kotlinx.coroutines.delay(F13Videos.WAKE_MS)
            }
            // Kids / Guest are set by the view model, and only kept if the switch works
            val result = viewModel.trySwitchUser(user, kids, bigKids, guest)
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
            if (result.await() is SwitchUserResult.Error) catWaking = false
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
            // Benflix redesign 2 (su-family, 2026-11-10): the dim wall of posters is back behind (one still
            // picture, fast on old TVs), the grown-ups are big round pictures in one row, and Kids, Big kids
            // and Guest sit a little smaller at the end with the names lined up. No fun facts.
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
                // The profiles show first; the wall fills in behind them once its pictures are ready
                // Christmas (December until New Year's, unless switched off in Settings): the cosy room with the tree,
                // the train and the parade past the window, instead of the wall
                val christmas = ChristmasSeason.on()
                // Friday the 13th (that day only, unless switched off): the room with the sleeping black cat. It takes
                // over any season room that day (ChristmasSeason and ThanksgivingSeason are off on a Friday the 13th)
                val f13 = f13Room
                // Thanksgiving (16 to 30 Nov, unless switched off): the feast table room, with the parade past the window
                val feast = !christmas && ThanksgivingSeason.on()
                // Valentine's Day (1 to 14 Feb, unless switched off): the table set for two, one chocolate gone each
                // day, and rose petals falling all day on the 14th
                val valentine = !christmas && !feast && ValentineSeason.on()
                // New Year's (27 Dec to 1 Jan, unless switched off): the party room. The clock is watched so the
                // countdown runs in the last 10 seconds of the year and the room pops open at midnight
                val newYear = !christmas && !feast && NewYearSeason.on()
                // 4th of July (1 to 4 Jul, unless switched off): the cookout room. The clock is watched so the
                // fireworks show starts at 9 PM on the 4th
                val july = !christmas && !feast && !newYear && JulySeason.on()
                var julyNow by remember { mutableStateOf(LocalDateTime.now()) }
                if (july) {
                    LaunchedEffect(Unit) {
                        while (true) {
                            julyNow = LocalDateTime.now()
                            kotlinx.coroutines.delay(20_000L)
                        }
                    }
                }
                // Popcorn Day (19 Jan, unless switched off): the popcorn cart room, the heap growing through the day
                val popcorn = !christmas && !feast && !newYear && !valentine && PopcornSeason.on()
                // St. Patrick's Day (10 to 17 Mar, unless switched off): the rainy Irish room, with a rainbow on the 17th
                val stPatricks = !christmas && !feast && !newYear && !valentine && !popcorn && !f13Room && StPatricksSeason.on()
                // Halloween (1 to 31 Oct, unless switched off): the spooky room. The pumpkins get carved as October
                // goes on, and at 5 PM on the 31st the bats come out, so the clock is checked now and then
                val halloween = !christmas && !feast && !newYear && HalloweenSeason.on()
                var hwNow by remember { mutableStateOf(LocalDateTime.now()) }
                if (halloween) {
                    LaunchedEffect(Unit) {
                        while (true) {
                            kotlinx.coroutines.delay(30_000L)
                            hwNow = LocalDateTime.now()
                        }
                    }
                }
                var nyNow by remember { mutableStateOf(LocalDateTime.now()) }
                if (newYear) {
                    LaunchedEffect(Unit) {
                        while (true) {
                            val n = LocalDateTime.now()
                            nyNow = n
                            val nearMidnight = n.monthValue == 12 && n.dayOfMonth == 31 && n.hour == 23 && n.minute >= 58
                            kotlinx.coroutines.delay(if (nearMidnight) 250L else 20_000L)
                        }
                    }
                }
                // Earth week (unless switched off): the plant corner with the turning globe and the spring window
                val earth = !christmas && !feast && !newYear && EarthDaySeason.on()
                // Star Wars Day (4 May, unless switched off): the desert home, with a laser sword in the colour
                // of the side of the profile you're on
                val starWars = !christmas && !feast && StarWarsDay.on()
                if (f13) {
                    Friday13Room(waking = catWaking, modifier = Modifier.fillMaxSize())
                } else if (christmas) {
                    ChristmasRoom(modifier = Modifier.fillMaxSize())
                } else if (starWars) {
                    StarWarsRoom(modifier = Modifier.fillMaxSize())
                } else if (feast) {
                    FeastRoom(modifier = Modifier.fillMaxSize())
                } else if (halloween) {
                    HalloweenRoom(stage = HalloweenSeason.stage(hwNow), modifier = Modifier.fillMaxSize())
                } else if (newYear) {
                    val day = nyNow.toLocalDate()
                    NewYearRoom(
                        variant = NewYearSeason.variant(day),
                        year = NewYearSeason.year(day),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (july) {
                    JulyRoom(variant = JulySeason.variant(julyNow), modifier = Modifier.fillMaxSize())
                } else if (valentine) {
                    ValentineRoom(modifier = Modifier.fillMaxSize())
                } else if (earth) {
                    EarthRoom(modifier = Modifier.fillMaxSize())
                } else if (popcorn) {
                    PopcornRoom(modifier = Modifier.fillMaxSize())
                } else if (stPatricks) {
                    StPatricksRoom(modifier = Modifier.fillMaxSize())
                } else {
                    PosterWall(posters = state.posterWall, modifier = Modifier.fillMaxSize())
                }
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
                    if (f13) {
                        Text(
                            text = Friday13.GREETING,
                            fontFamily = XmasFont,
                            fontSize = if (isTvLayout) 54.sp else 40.sp,
                            color = BenflixColors.Text,
                            textAlign = TextAlign.Center,
                            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 3f), 14f)),
                        )
                        Text(
                            text = Friday13.LINE,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFCFC6E0),
                            textAlign = TextAlign.Center,
                            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 2f), 10f)),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    } else if (christmas) {
                        val today = remember { LocalDate.now() }
                        Text(
                            text = ChristmasSeason.greeting(today),
                            fontFamily = XmasFont,
                            fontSize = if (isTvLayout) 54.sp else 40.sp,
                            color = BenflixColors.Text,
                            textAlign = TextAlign.Center,
                            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 3f), 14f)),
                        )
                        ChristmasSeason.countdown(today)?.let {
                            Text(
                                text = it,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF2D27E),
                                textAlign = TextAlign.Center,
                                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 2f), 10f)),
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    } else if (halloween) {
                        // "Happy Halloween" with the countdown under it; "Happy Halloween!" on Halloween night
                        Text(
                            text = HalloweenSeason.whoGreeting(hwNow),
                            fontFamily = XmasFont,
                            fontSize = if (isTvLayout) 54.sp else 40.sp,
                            color = BenflixColors.Text,
                            textAlign = TextAlign.Center,
                            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 3f), 14f)),
                        )
                        HalloweenSeason.countdown(hwNow)?.let {
                            Text(
                                text = it,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD6C8DA),
                                textAlign = TextAlign.Center,
                                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 2f), 10f)),
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    } else if (newYear) {
                        // "Happy New Year", then 10 to 1 in the last seconds of the year, then "Happy 2027!" all of New Year's Day
                        Text(
                            text = NewYearSeason.whoGreeting(nyNow),
                            fontFamily = XmasFont,
                            fontSize = if (isTvLayout) 54.sp else 40.sp,
                            color = BenflixColors.Text,
                            textAlign = TextAlign.Center,
                            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 3f), 14f)),
                        )
                        NewYearSeason.countdown(nyNow)?.let {
                            Text(
                                text = it,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC9D0E0),
                                textAlign = TextAlign.Center,
                                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 2f), 10f)),
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    } else if (stPatricks) {
                        // "Who's watching?" with "3 days to St. Patrick's Day" under it, then "Happy St. Patrick's Day!" on the 17th
                        val today = remember { LocalDate.now() }
                        Text(
                            text = StPatricksSeason.whoGreeting(today),
                            fontFamily = XmasFont,
                            fontSize = if (isTvLayout) 54.sp else 40.sp,
                            color = BenflixColors.Text,
                            textAlign = TextAlign.Center,
                            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 3f), 14f)),
                        )
                        StPatricksSeason.countdown(today)?.let {
                            Text(
                                text = it,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC9D8CC),
                                textAlign = TextAlign.Center,
                                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 2f), 10f)),
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    } else if (july) {
                        // "Happy 4th of July", with "2 days to the Fourth" (then "The fireworks are on!") under it
                        Text(
                            text = JulySeason.GREETING,
                            fontFamily = XmasFont,
                            fontSize = if (isTvLayout) 54.sp else 40.sp,
                            color = BenflixColors.Text,
                            textAlign = TextAlign.Center,
                            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 3f), 14f)),
                        )
                        JulySeason.countdown(julyNow)?.let {
                            Text(
                                text = it,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC9D0E0),
                                textAlign = TextAlign.Center,
                                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 2f), 10f)),
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    } else if (starWars) {
                        Text(
                            text = StarWarsDay.GREETING,
                            fontFamily = StarWarsFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isTvLayout) 54.sp else 34.sp,
                            color = StarWarsDay.CrawlYellow,
                            textAlign = TextAlign.Center,
                            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 3f), 14f)),
                        )
                    } else if (feast) {
                        Text(
                            text = ThanksgivingSeason.GREETING,
                            fontFamily = XmasFont,
                            fontSize = if (isTvLayout) 54.sp else 40.sp,
                            color = BenflixColors.Text,
                            textAlign = TextAlign.Center,
                            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 3f), 14f)),
                        )
                    } else if (popcorn) {
                        Text(
                            text = PopcornSeason.GREETING,
                            fontFamily = XmasFont,
                            fontSize = if (isTvLayout) 54.sp else 40.sp,
                            color = BenflixColors.Text,
                            textAlign = TextAlign.Center,
                            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 3f), 14f)),
                        )
                        Text(
                            text = PopcornSeason.WHO_LINE,
                            fontSize = if (isTvLayout) 20.sp else 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE0CFC0),
                            textAlign = TextAlign.Center,
                            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 2f), 10f)),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    } else if (valentine) {
                        // "Who's watching?" in the curly script, then "Happy Valentine's Day" on the 14th, and the countdown
                        val today = remember { LocalDate.now() }
                        Text(
                            text = ValentineSeason.whoGreeting(today) ?: stringResource(R.string.select_user),
                            fontFamily = XmasFont,
                            fontSize = if (isTvLayout) 54.sp else 40.sp,
                            color = BenflixColors.Text,
                            textAlign = TextAlign.Center,
                            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 3f), 14f)),
                        )
                        ValentineSeason.countdown(today)?.let {
                            Text(
                                text = it,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE0C8D0),
                                textAlign = TextAlign.Center,
                                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 2f), 10f)),
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    } else if (earth) {
                        Text(
                            text = EarthDaySeason.GREETING,
                            fontFamily = XmasFont,
                            fontSize = if (isTvLayout) 54.sp else 40.sp,
                            color = BenflixColors.Text,
                            textAlign = TextAlign.Center,
                            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 3f), 14f)),
                        )
                        // "Earth week, day 3" under the greeting
                        EarthDaySeason.line()?.let {
                            Text(
                                text = it,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC9D6C8),
                                textAlign = TextAlign.Center,
                                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 2f), 10f)),
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    } else {
                        Text(
                            text = holiday?.text ?: stringResource(R.string.select_user),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = BenflixColors.Text,
                            textAlign = TextAlign.Center,
                        )
                    }
                    androidx.compose.foundation.layout
                        .Spacer(Modifier.height(36.dp))
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalProfileTileSize provides if (isTvLayout) 132.dp else 92.dp,
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
        SignInScreens(
            serverName = server.name ?: server.url,
            serverUrl = server.url,
            knownName = addUser?.name,
            phoneAllowed = state.quickConnectEnabled,
            phoneCode = state.quickConnectStatus?.code,
            phoneProblem = state.quickConnectError,
            error = state.switchUserState as? LoadingState.Error,
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

/** Benflix Christmas greeting font (Berkshire Swash, OFL) */
private val XmasFont = FontFamily(Font(R.font.holiday_berkshireswash))
