package com.github.damontecres.wholphin.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.BenflixIntroState
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.components.BenflixMark
import com.github.damontecres.wholphin.ui.components.GlowfinIcon
import com.github.damontecres.wholphin.ui.components.GlowfinIconImage
import com.github.damontecres.wholphin.ui.door.DOOR_HOME
import com.github.damontecres.wholphin.ui.door.DoorPlaces
import com.github.damontecres.wholphin.ui.door.DoorSection
import com.github.damontecres.wholphin.ui.setup.UserIconCardImage
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont
import com.github.damontecres.wholphin.ui.theme.glowFocus
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.LastRemoteKey
import kotlinx.coroutines.delay

/** The door each rail icon draws */
fun BenflixDoor.railIcon(): BenflixIcon =
    when (this) {
        BenflixDoor.MOVIES -> BenflixIcon.FILM
        BenflixDoor.SHOWS -> BenflixIcon.TV
        BenflixDoor.LIVE_TV -> BenflixIcon.LIVE
        BenflixDoor.GAMES -> BenflixIcon.PAD
        BenflixDoor.MUSIC -> BenflixIcon.NOTE
        BenflixDoor.BOOKS -> BenflixIcon.BOOK
        BenflixDoor.ANIME -> BenflixIcon.STAR
    }

/** Glowfin's two-tone menu icon for each door */
fun BenflixDoor.glowIcon(): GlowfinIcon =
    when (this) {
        BenflixDoor.MOVIES -> GlowfinIcon.MOVIES
        BenflixDoor.SHOWS -> GlowfinIcon.SHOWS
        BenflixDoor.LIVE_TV -> GlowfinIcon.LIVE
        BenflixDoor.GAMES -> GlowfinIcon.GAMES
        BenflixDoor.MUSIC -> GlowfinIcon.MUSIC
        BenflixDoor.BOOKS -> GlowfinIcon.BOOKS
        BenflixDoor.ANIME -> GlowfinIcon.ANIME
    }

/**
 * Glowfin (ui-7): these pages show their places as tabs along their top, so the menu opens them straight away.
 * Ben 2026-11-18: every page now (Live TV, Music, Games and Books too), so no page opens a slide-out menu.
 */
@Suppress("UnusedReceiverParameter")
fun BenflixDoor.hasPageTabs(): Boolean = true

/** Glowfin menu order (ui-2): Movies, Shows, (Anime), Live TV, Music, Games, Books */
private val GlowfinDoorOrder =
    listOf(BenflixDoor.MOVIES, BenflixDoor.SHOWS, BenflixDoor.LIVE_TV, BenflixDoor.MUSIC, BenflixDoor.GAMES, BenflixDoor.BOOKS)

/** Where each door's own page is */
fun BenflixDoor.destination(): Destination =
    when (this) {
        BenflixDoor.MOVIES -> Destination.MoviesDoor
        BenflixDoor.SHOWS -> Destination.ShowsDoor
        BenflixDoor.LIVE_TV -> Destination.Channels
        BenflixDoor.GAMES -> Destination.Games
        BenflixDoor.MUSIC -> Destination.MusicDoor
        BenflixDoor.BOOKS -> Destination.BooksDoor
        BenflixDoor.ANIME -> Destination.AnimeDoor
    }

/** The door's own first page (not a page inside it) */
fun Destination.isDoorRoot(): Boolean =
    this is Destination.MoviesDoor ||
        this is Destination.ShowsDoor ||
        this is Destination.AnimeDoor ||
        this is Destination.Channels ||
        this is Destination.Games ||
        this is Destination.MusicDoor ||
        this is Destination.BooksDoor ||
        this is Destination.BooksKids

/**
 * The start-up light-up (approved in-light): right after a profile is picked the rail is already there but grey.
 * Top to bottom, each door icon lights up in its colour with a little glow on its own note (the same six notes
 * the doors had), and then the icons settle back to their usual look. Home drives it (see DoorsHomePage).
 */
object RailIntro {
    /** True from the moment a profile is picked until the light-up is over */
    var active by mutableStateOf(false)

    /** How lit each door's icon is (0 grey, 1 its colour), by [BenflixDoor.ordinal] */
    val lit = BenflixDoor.entries.map { mutableFloatStateOf(0f) }

    /** The glow behind each icon as its note plays */
    val glow = BenflixDoor.entries.map { mutableFloatStateOf(0f) }

    /** At the end: 1 = still in the door colours, 0 = back to the usual soft white */
    val settle = mutableFloatStateOf(1f)

    /** Grey icons before the light-up starts */
    fun prepare() {
        lit.forEach { it.floatValue = 0f }
        glow.forEach { it.floatValue = 0f }
        settle.floatValue = 1f
        active = true
    }

    fun finish() {
        lit.forEach { it.floatValue = 1f }
        glow.forEach { it.floatValue = 0f }
        settle.floatValue = 0f
        active = false
    }

    /** The rail is waiting for (or doing) the light-up */
    val waiting: Boolean get() = active || BenflixIntroState.doorsPending
}

/** The rail holds the focus only while a newly picked page loads: it shows no focus glow then */
private val LocalRailQuiet = androidx.compose.runtime.compositionLocalOf { false }

/** Lets the page around the rail open it (the remote's ☰ Menu key) or its profile panel (holding ☰) */
class RailControl {
    var openRequests by mutableIntStateOf(0)
        private set
    var profileRequests by mutableIntStateOf(0)
        private set

    fun open() {
        openRequests++
    }

    fun openProfile() {
        profileRequests++
    }
}

/** Everything the rail shows and does */
class RailModel(
    val destination: Destination,
    val doors: List<BenflixDoor>,
    val showSearch: Boolean,
    val showRequests: Boolean,
    /** Glowfin K1 (ui-17): Kids has no Settings in the menu */
    val showSettings: Boolean = true,
    val requestsCount: Int,
    val user: JellyfinUser,
    val userImageUrl: String?,
    val big: Boolean,
    /** Wide touch screens: no remote to open it, so it stays open with names */
    val alwaysOpen: Boolean,
    /** The profile panel's lines; null = the picture goes straight to Who's watching (Kids, Guest, Easy) */
    val profileItems: List<ProfileMenuItem>?,
    val minutesWatched: Long?,
    val onHome: () -> Unit,
    val onSearch: () -> Unit,
    val onRequests: () -> Unit,
    val onPlace: (BenflixDoor, String) -> Unit,
    val onItem: (BaseItem) -> Unit,
    val onProfile: () -> Unit,
    val onSwitchProfile: () -> Unit,
    /** Glowfin: Anime gets its own spot under Shows */
    val showAnime: Boolean = false,
    val onAnime: () -> Unit = {},
    /** Ben 2026-11-18: your picture opens the full Me page (Settings lives there, no second Settings spot) */
    val onMe: () -> Unit = {},
    /** Ben 2026-11-18 (approved update-button-1): a glowing Update button above your picture, only when one is ready */
    val updateReady: Boolean = false,
    val onUpdate: () -> Unit = {},
)

private enum class Spot { HOME, SEARCH, ANIME, REQUESTS, PROFILE, SETTINGS }

/**
 * Navigation redesign (Nov 2026, approved nv-rail + s3-pop + s4-panel + pa-strip): one slim column of line
 * icons down the left of every page (TV, and wide touch screens). Logo, Home, Search, the doors, then
 * Requests and your picture at the bottom. The door you're in glows in its colour. Moving onto it (Left from
 * a page's first item, or ☰ Menu) glides it open with every name while the page dims; Right or Back tucks it
 * away again. OK on a door slides out a full-height panel with a picture strip, that door's places (the first
 * line is the door's home) and Jump back in.
 */
@Composable
fun BenflixRail(
    model: RailModel,
    contentFocus: FocusRequester,
    control: RailControl,
    modifier: Modifier = Modifier,
) {
    val scale = if (model.big) BIG_SCALE else 1f
    var railFocused by remember { mutableStateOf(false) }
    var panelDoor by remember { mutableStateOf<BenflixDoor?>(null) }
    var profileOpen by remember { mutableStateOf(false) }
    val panelOpen = panelDoor != null || profileOpen
    // Ben 2026-11-18: picking a page tucks the menu away and it stays tucked while the new page loads. If the
    // focus falls back onto the menu by itself meanwhile (the old page's cards went away), the menu stays
    // closed and hands the focus to the new page, which puts it on the first card of its first row.
    // [quiet] = the menu has the focus but nobody asked for it to open (no Left/Up/Down or ☰ Menu press).
    var quiet by remember { mutableStateOf(false) }
    var invitedAt by remember { mutableLongStateOf(0L) }
    var leftAt by remember { mutableLongStateOf(0L) }
    var strays by remember { mutableIntStateOf(0) }
    val open = model.alwaysOpen || (railFocused && !quiet) || panelOpen
    // Speed check (Ben 2026-11-17): the glide is read only while laying out, so opening the rail no longer
    // rebuilds every icon and name on each step of it
    val width =
        animateDpAsState(
            if (open) RAIL_OPEN * scale else RAIL_TUCKED * scale,
            tween(RAIL_MS),
            label = "benflixRail",
        )
    val doorFocus = remember(model.doors) { model.doors.associateWith { FocusRequester() } }
    val spotFocus = remember { Spot.entries.associateWith { FocusRequester() } }
    val current = model.destination.door()
    val currentSpot =
        when (model.destination) {
            is Destination.Home -> Spot.HOME
            is Destination.Search -> if (model.showSearch) Spot.SEARCH else null
            is Destination.Requests -> if (model.showRequests) Spot.REQUESTS else null
            is Destination.Me -> Spot.PROFILE
            else -> null
        }
    val panelFocus = LocalFocusManager.current

    fun focusCurrent() {
        quiet = false
        invitedAt = android.os.SystemClock.uptimeMillis()
        val target =
            current?.let { doorFocus[it] }
                ?: currentSpot?.let { spotFocus[it] }
                ?: spotFocus[Spot.HOME]
        target?.tryRequestFocus()
    }

    fun closePanel() {
        invitedAt = android.os.SystemClock.uptimeMillis()
        val door = panelDoor
        panelDoor = null
        profileOpen = false
        if (door != null) doorFocus[door]?.tryRequestFocus() else spotFocus[Spot.PROFILE]?.tryRequestFocus()
    }

    /** A page was picked: tuck the menu away now and give the focus to the page (TV) */
    fun leaveFor(go: () -> Unit) {
        panelDoor = null
        profileOpen = false
        if (!model.alwaysOpen) {
            quiet = true
            leftAt = android.os.SystemClock.uptimeMillis()
            contentFocus.tryRequestFocus()
        }
        go()
        if (!model.alwaysOpen && railFocused) strays++
    }

    fun openProfilePanel() {
        if (model.profileItems == null) {
            model.onProfile()
            return
        }
        // Ben 2026-11-18 (approved profile-page-1): a full page like every other spot, no slide-out
        leaveFor(model.onMe)
    }

    // ☰ Menu opens the rail on the place you're in; holding ☰ opens your profile panel
    LaunchedEffect(control.openRequests) {
        if (control.openRequests > 0) focusCurrent()
    }
    LaunchedEffect(control.profileRequests) {
        if (control.profileRequests > 0) openProfilePanel()
    }
    // Back closes the panel first, then tucks the rail away
    BackHandler(enabled = panelOpen) { closePanel() }
    BackHandler(enabled = railFocused && !panelOpen && !model.alwaysOpen) { contentFocus.tryRequestFocus() }

    // Like the door menus before it: when a page swaps its rows in, the focus can fall onto the rail by itself.
    // The rail only keeps the focus when the remote moved onto it; otherwise it goes straight back to the page.
    LaunchedEffect(strays) {
        if (strays == 0) return@LaunchedEffect
        repeat(STRAY_TRIES) {
            if (!railFocused || !quiet || panelOpen) return@LaunchedEffect
            contentFocus.tryRequestFocus()
            delay(STRAY_RETRY_MS)
        }
    }

    Box(modifier.fillMaxSize()) {
        // The page dims while the rail is open (never on wide touch screens, where it's always open)
        if (open && !model.alwaysOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Glowfin.Ink.copy(alpha = DIM))
                    .focusProperties { canFocus = false }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        panelDoor = null
                        profileOpen = false
                        contentFocus.tryRequestFocus()
                    },
            )
        } else if (panelOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Glowfin.Ink.copy(alpha = DIM))
                    .focusProperties { canFocus = false }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        panelDoor = null
                        profileOpen = false
                    },
            )
        }
        val entryFocus =
            current?.let { doorFocus[it] }
                ?: currentSpot?.let { spotFocus[it] }
                ?: spotFocus.getValue(Spot.HOME)
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .fillMaxHeight()
                .layout { measurable, constraints ->
                    val w = width.value.roundToPx().coerceIn(constraints.minWidth, constraints.maxWidth)
                    val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }.clipToBounds()
                .background(
                    if (open) {
                        // Glowfin: the open menu is one calm deep panel with a thin line on its edge
                        Brush.horizontalGradient(
                            0f to Glowfin.Deep,
                            1f to Glowfin.Deep,
                        )
                    } else {
                        // Ben 2026-11-18: the tucked menu has no background of its own, the icons sit on the page
                        SolidColor(Color.Transparent)
                    },
                ).then(
                    if (open) {
                        Modifier.drawBehind {
                            drawLine(
                                color = Glowfin.Line,
                                start =
                                    androidx.compose.ui.geometry
                                        .Offset(size.width - 1f, 0f),
                                end =
                                    androidx.compose.ui.geometry
                                        .Offset(size.width - 1f, size.height),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }
                    } else {
                        Modifier
                    },
                ).layout { measurable, constraints ->
                    // The icons and names are laid out once at the open width and the glide just shows more of
                    // them, so nothing inside has to be measured again on every step
                    val full = (RAIL_OPEN * scale).roundToPx().coerceAtLeast(constraints.maxWidth)
                    val placeable = measurable.measure(constraints.copy(minWidth = full, maxWidth = full))
                    layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
                },
        ) {
            androidx.compose.runtime.CompositionLocalProvider(LocalRailQuiet provides (quiet && !model.alwaysOpen)) {
                // Scrolls only when the screen is too short for every icon (Easy's bigger rail, phones turned sideways)
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp * scale),
                    modifier =
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = maxHeight)
                            .padding(horizontal = 10.dp * scale, vertical = 14.dp * scale)
                            .onFocusChanged {
                                val came = it.hasFocus && !railFocused
                                railFocused = it.hasFocus
                                if (!it.hasFocus) {
                                    quiet = false
                                } else if (came && !model.alwaysOpen) {
                                    // (an arrow pressed just before picking the page doesn't count: only one after it)
                                    val now = android.os.SystemClock.uptimeMillis()
                                    val asked =
                                        if (now - leftAt < SETTLE_MS) {
                                            // Ben 2026-11-18: the menu sometimes stayed open after picking a page. While
                                            // the new page is still loading, the focus has nowhere to go, so pressing
                                            // Down (to reach the first row) or a ☰ Menu press from just before the pick
                                            // landed it on the menu, which then opened. Now only Left, or ☰ Menu pressed
                                            // after the pick, opens it while the page loads.
                                            (now - LastRemoteKey.leftAt < INVITE_MS && LastRemoteKey.leftAt > leftAt) ||
                                                (invitedAt > leftAt && now - invitedAt < INVITE_MS)
                                        } else {
                                            (LastRemoteKey.movedRecently() && LastRemoteKey.moveAt > leftAt) ||
                                                now - invitedAt < INVITE_MS
                                        }
                                    quiet = !asked
                                    if (!asked) strays++
                                }
                            }.focusRestorer(entryFocus)
                            .focusProperties {
                                onExit = {
                                    if (requestedFocusDirection == FocusDirection.Up || requestedFocusDirection == FocusDirection.Down) {
                                        cancelFocusChange()
                                    }
                                }
                            }.onPreviewKeyEvent { event ->
                                if (!model.alwaysOpen && !panelOpen && event.key == Key.DirectionRight) {
                                    if (event.type == KeyEventType.KeyDown) contentFocus.tryRequestFocus()
                                    true
                                } else if (quiet && !model.alwaysOpen) {
                                    // The menu is tucked away but holding the focus while a page loads: Left opens
                                    // it on the page you're in; Up, Down and OK go to the page instead (Ben
                                    // 2026-11-18: pressing Down to reach the first row opened the menu)
                                    if (event.type == KeyEventType.KeyDown) {
                                        when (event.key) {
                                            Key.DirectionLeft -> focusCurrent()
                                            else -> contentFocus.tryRequestFocus()
                                        }
                                    }
                                    event.key != Key.Back
                                } else {
                                    false
                                }
                            },
                ) {
                    // The logo goes Home too (a tap; the remote uses Home just below it)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .height(ITEM * scale)
                                .padding(start = 9.dp * scale)
                                .focusProperties { canFocus = false }
                                .clickable(onClickLabel = "Home", onClick = model.onHome),
                    ) {
                        BenflixMark(size = 30.dp * scale)
                        if (open) {
                            Spacer(Modifier.width(12.dp))
                            com.github.damontecres.wholphin.ui.components
                                .GlowfinWord(fontSize = 19.sp * scale)
                        }
                    }
                    Spacer(Modifier.height(4.dp * scale))
                    // Glowfin menu (ui-2): Search, Home, Movies, Shows, Anime, Live TV, Music, Games, Books, Requests,
                    // then your picture at the bottom (it opens the Me page, where Settings lives)
                    if (model.showSearch) {
                        RailButton(
                            icon = GlowfinIcon.SEARCH,
                            label = "Search",
                            open = open,
                            scale = scale,
                            on = currentSpot == Spot.SEARCH,
                            onClick = { leaveFor(model.onSearch) },
                            modifier = Modifier.focusRequester(spotFocus.getValue(Spot.SEARCH)),
                        )
                    }
                    RailButton(
                        icon = GlowfinIcon.HOME,
                        label = "Home",
                        open = open,
                        scale = scale,
                        on = currentSpot == Spot.HOME,
                        onClick = { leaveFor(model.onHome) },
                        modifier = Modifier.focusRequester(spotFocus.getValue(Spot.HOME)),
                    )
                    GlowfinDoorOrder.filter { it in model.doors }.forEach { door ->
                        RailButton(
                            icon = door.glowIcon(),
                            label = door.title,
                            open = open,
                            scale = scale,
                            on = door == current,
                            highlighted = panelDoor == door,
                            onClick = {
                                profileOpen = false
                                if (door.hasPageTabs()) {
                                    // Glowfin (ui-7): every page has its places as tabs on the page, no panel
                                    leaveFor { model.onPlace(door, com.github.damontecres.wholphin.ui.door.DOOR_HOME) }
                                } else {
                                    panelDoor = door
                                }
                            },
                            modifier = Modifier.focusRequester(doorFocus.getValue(door)),
                        )
                        if (door == BenflixDoor.SHOWS && model.showAnime) {
                            RailButton(
                                icon = GlowfinIcon.ANIME,
                                label = "Anime",
                                open = open,
                                scale = scale,
                                on = current == BenflixDoor.ANIME,
                                onClick = { leaveFor(model.onAnime) },
                                modifier = Modifier.focusRequester(spotFocus.getValue(Spot.ANIME)),
                            )
                        }
                    }
                    if (model.showRequests) {
                        RailButton(
                            icon = GlowfinIcon.REQUESTS,
                            label = "Requests",
                            open = open,
                            scale = scale,
                            on = currentSpot == Spot.REQUESTS,
                            count = model.requestsCount,
                            onClick = { leaveFor(model.onRequests) },
                            modifier = Modifier.focusRequester(spotFocus.getValue(Spot.REQUESTS)),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (model.updateReady) {
                        UpdateRailButton(
                            open = open,
                            scale = scale,
                            onClick = { leaveFor(model.onUpdate) },
                        )
                    }
                    ProfileRailButton(
                        model = model,
                        open = open,
                        scale = scale,
                        highlighted = profileOpen || currentSpot == Spot.PROFILE,
                        onClick = { openProfilePanel() },
                        modifier = Modifier.focusRequester(spotFocus.getValue(Spot.PROFILE)),
                    )
                }
            }
        }
        // The full-height panel slides out beside the open rail
        AnimatedVisibility(
            visible = panelOpen,
            enter = slideInHorizontally(tween(PANEL_MS)) { -it / 3 } + fadeIn(tween(PANEL_MS)),
            exit = slideOutHorizontally(tween(PANEL_OUT_MS)) { -it / 4 } + fadeOut(tween(PANEL_OUT_MS)),
            modifier = Modifier.offset { IntOffset(width.value.roundToPx(), 0) },
        ) {
            val shape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp)
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(PANEL_WIDTH * scale)
                    .background(Glowfin.Deep, shape)
                    .clip(shape)
                    .focusProperties {
                        // Arrow keys never move the focus out of the panel by themselves: Left at the panel's edge closes it (below).
                        // (Picking something or closing still moves the focus on, since those aren't arrow moves.)
                        onExit = {
                            if (requestedFocusDirection == FocusDirection.Up ||
                                requestedFocusDirection == FocusDirection.Down ||
                                requestedFocusDirection == FocusDirection.Left ||
                                requestedFocusDirection == FocusDirection.Right
                            ) {
                                cancelFocusChange()
                            }
                        }
                    }.focusGroup()
                    .onPreviewKeyEvent { event ->
                        when (event.key) {
                            // Left and Right move along a row (like Jump back in) first.
                            // Only when nothing is further left does Left close the panel and go back to the rail.
                            Key.DirectionLeft -> {
                                if (event.type == KeyEventType.KeyDown && !panelFocus.moveFocus(FocusDirection.Left)) closePanel()
                                true
                            }

                            Key.DirectionRight -> {
                                if (event.type == KeyEventType.KeyDown) panelFocus.moveFocus(FocusDirection.Right)
                                true
                            }

                            else -> {
                                false
                            }
                        }
                    },
            ) {
                val door = panelDoor
                if (door != null) {
                    DoorPanel(
                        door = door,
                        places = DoorPlaces.of(door),
                        scale = scale,
                        onPlace = { key -> leaveFor { model.onPlace(door, key) } },
                    )
                }
            }
        }
        // Approved rx-tip: the first time each profile sees it on a TV, a small bubble points at the rail
        if (!model.alwaysOpen) RailTip(model.user, scale)
    }
}

/**
 * Ben 2026-11-18 (approved update-button-1): an aqua ring with a down arrow, see-through inside, and
 * "Update / New version ready" when the menu is open. Only shown when a new version is ready.
 */
@Composable
private fun UpdateRailButton(
    open: Boolean,
    scale: Float,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    val quiet = LocalRailQuiet.current
    var hasFocus by remember { mutableStateOf(false) }
    val focused = hasFocus && !quiet
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = BenflixColors.Text,
                focusedContainerColor = Color.Transparent,
                focusedContentColor = Color.White,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            Modifier
                .then(if (quiet) Modifier else Modifier.glowFocus(shape, spread = 8.dp))
                .height(ITEM * scale)
                .then(if (open) Modifier.fillMaxWidth() else Modifier.width(ITEM * scale))
                .onFocusChanged { hasFocus = it.isFocused },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (focused) Modifier.background(Glowfin.GlowAcross, shape) else Modifier),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // Closed: the ring sits exactly in the middle ((42 - 28) / 2 = 7 each side)
                modifier = Modifier.fillMaxSize().padding(start = 7.dp * scale, end = if (open) 12.dp * scale else 0.dp),
            ) {
                val tint = if (focused) Color.White else Glowfin.Aqua
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(PICTURE * scale)
                            .border(2.dp, tint, CircleShape),
                ) {
                    BenflixIconImage(BenflixIcon.DOWNLOAD, color = tint, size = 16.dp * scale)
                }
                if (open) {
                    Spacer(Modifier.width(12.dp * scale))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Update",
                            color = if (focused) Color.White else BenflixColors.Text,
                            fontSize = BenflixType.body * scale,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            text = "New version ready",
                            color = tint,
                            fontSize = 11.sp * scale,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One spot in the Glowfin menu (ui-2). Closed: just the two-tone icon; the one the remote is on sits in a
 * round glow. Open: the name beside it, and the glow stretches into a pill. The page you're on has its icon
 * in the Glowfin blue (Ben 2026-11-18, instead of the old small dot). Nothing moves or keeps running, so it stays smooth on older TVs.
 */
@Composable
private fun RailButton(
    icon: GlowfinIcon,
    label: String,
    open: Boolean,
    scale: Float,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int = 0,
    highlighted: Boolean = false,
) {
    val shape = RoundedCornerShape(percent = 50)
    // Ben 2026-11-18: no glow on the tucked-away menu while it only holds the focus for a page that's loading
    val quiet = LocalRailQuiet.current
    var hasFocus by remember { mutableStateOf(false) }
    val focused = hasFocus && !quiet
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (highlighted) BenflixColors.FocusRow else Color.Transparent,
                contentColor = BenflixColors.Text,
                focusedContainerColor = Color.Transparent,
                focusedContentColor = Color.White,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .then(if (quiet) Modifier else Modifier.glowFocus(shape, spread = 8.dp))
                .height(ITEM * scale)
                .then(if (open) Modifier.fillMaxWidth() else Modifier.width(ITEM * scale))
                .onFocusChanged { hasFocus = it.isFocused },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (focused) Modifier.background(Glowfin.GlowAcross, shape) else Modifier),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // Closed: the icon sits exactly in the middle of the round glow ((42 - 22) / 2 = 10 each side)
                modifier = Modifier.fillMaxSize().padding(start = 10.dp * scale, end = if (open) 12.dp * scale else 0.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ICON * scale)) {
                    GlowfinIconImage(
                        icon,
                        color =
                            when {
                                focused -> Color.White
                                on -> BenflixColors.Shows
                                else -> Glowfin.Dim
                            },
                        size = ICON * scale,
                    )
                    if (count > 0 && !open) {
                        CountDot(count, scale, Modifier.align(Alignment.TopEnd).offset(x = 7.dp * scale, y = (-6).dp * scale))
                    }
                }
                if (open) {
                    Spacer(Modifier.width(13.dp * scale))
                    Text(
                        text = label,
                        color =
                            when {
                                focused -> Color.White
                                on -> BenflixColors.Text
                                else -> Glowfin.Dim
                            },
                        fontSize = BenflixType.body * scale,
                        fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (count > 0) CountDot(count, scale)
                }
            }
        }
    }
}

/** Requests' small teal number (approved ix-badges) */
@Composable
private fun CountDot(
    count: Int,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    Text(
        text = if (count > 99) "99+" else "$count",
        color = BenflixColors.OnLiveTv,
        fontSize = 10.sp * scale,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier =
            modifier
                .background(BenflixColors.LiveTv, RoundedCornerShape(50))
                .padding(horizontal = 5.dp * scale, vertical = 1.dp),
    )
}

/** Your picture at the bottom of the rail, with the bigger white dot when there's news */
@Composable
private fun ProfileRailButton(
    model: RailModel,
    open: Boolean,
    scale: Float,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kids by KidsMode.active.collectAsState()
    val guest by GuestMode.active.collectAsState()
    val shape = RoundedCornerShape(percent = 50)
    val name =
        when {
            kids -> KidsMode.displayName
            guest -> "Guest"
            else -> model.user.name.orEmpty()
        }
    // Ben 2026-11-18: no glow on the tucked-away menu while it only holds the focus for a page that's loading
    val quiet = LocalRailQuiet.current
    var hasFocus by remember { mutableStateOf(false) }
    val focused = hasFocus && !quiet
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (highlighted) BenflixColors.FocusRow else Color.Transparent,
                contentColor = BenflixColors.Text,
                focusedContainerColor = Color.Transparent,
                focusedContentColor = Color.White,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .then(if (quiet) Modifier else Modifier.glowFocus(shape, spread = 8.dp))
                .height(ITEM * scale)
                .then(if (open) Modifier.fillMaxWidth() else Modifier.width(ITEM * scale))
                .onFocusChanged { hasFocus = it.isFocused }
                .then(if (focused) Modifier.background(Glowfin.GlowAcross, shape) else Modifier),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // Closed: your picture sits exactly in the middle of the round glow ((42 - 28) / 2 = 7 each side)
            modifier = Modifier.fillMaxSize().padding(start = 7.dp * scale, end = if (open) 10.dp * scale else 0.dp),
        ) {
            Box {
                UserIconCardImage(
                    id = model.user.id,
                    name = name,
                    imageUrl = if (kids) null else model.userImageUrl,
                    modifier = Modifier.size(PICTURE * scale),
                )
            }
            if (open) {
                Spacer(Modifier.width(11.dp * scale))
                Text(
                    text = name,
                    color = BenflixColors.Text,
                    fontSize = BenflixType.body * scale,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * A door's slide-out panel: the door's name and its places (Glowfin: the picture strip and Jump back in are
 * gone, so it opens instantly and loads nothing). The Movies, Shows and Anime pages get their own tabs next.
 */
@Composable
private fun DoorPanel(
    door: BenflixDoor,
    places: List<DoorSection>,
    scale: Float,
    onPlace: (String) -> Unit,
) {
    val first = remember(door) { FocusRequester() }
    LaunchedEffect(door) {
        delay(PANEL_FOCUS_MS)
        first.tryRequestFocus()
    }
    Column(Modifier.fillMaxSize()) {
        Text(
            text = door.title,
            color = BenflixColors.Text,
            fontFamily = GlowfinDisplayFont,
            fontSize = 24.sp * scale,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(start = 22.dp * scale, top = 26.dp * scale, bottom = 12.dp * scale),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp * scale, vertical = 6.dp),
        ) {
            places.forEachIndexed { i, place ->
                PanelRow(
                    icon = place.icon,
                    label = if (place.key == DOOR_HOME) "${door.title} home" else place.label,
                    note = place.count?.takeIf { it > 0 }?.toString(),
                    scale = scale,
                    onClick = { onPlace(place.key) },
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun PanelRow(
    icon: BenflixIcon?,
    label: String,
    note: String?,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    picture: (@Composable () -> Unit)? = null,
    muted: Boolean = false,
) {
    val shape = RoundedCornerShape(10.dp)
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border = focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp * scale),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp * scale),
        ) {
            if (picture != null) {
                picture()
            } else if (icon != null) {
                BenflixIconImage(icon, color = if (muted) BenflixColors.Muted else BenflixColors.Soft, size = 20.dp * scale)
            }
            Text(
                text = label,
                color = if (muted) BenflixColors.Soft else BenflixColors.Text,
                fontSize = BenflixType.body * scale,
                fontWeight = if (focused) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            note?.let {
                Text(
                    text = it,
                    color = BenflixColors.Muted,
                    fontSize = BenflixType.tag * scale,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

/** "6 h 20 m watched this month" */
internal fun watchedWords(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return (if (h > 0) "$h h $m m" else "$m m") + " watched this month"
}

/** Approved rx-tip: "Press Left any time for the menu", once per profile on this TV */
@Composable
private fun RailTip(
    user: JellyfinUser,
    scale: Float,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(user.id) {
        val prefs = context.getSharedPreferences(TIP_PREFS, android.content.Context.MODE_PRIVATE)
        val key = "tip-" + user.id
        if (prefs.getBoolean(key, false)) return@LaunchedEffect
        // Not during the start-up light-up
        delay(TIP_DELAY_MS)
        while (RailIntro.waiting) delay(200)
        prefs.edit().putBoolean(key, true).apply()
        show = true
        delay(TIP_SHOW_MS)
        show = false
    }
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(300)),
        modifier = Modifier.offset(x = RAIL_TUCKED * scale + 6.dp, y = 96.dp * scale),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .background(BenflixColors.Text, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            BenflixIconImage(BenflixIcon.BACK, color = BenflixColors.Tray, size = 16.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Press Left any time for the menu",
                color = BenflixColors.Tray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** How wide the tucked rail is (the page starts right of it) */
fun railTuckedWidth(big: Boolean): Dp = RAIL_TUCKED * (if (big) BIG_SCALE else 1f)

/** How wide the open rail is (wide touch screens keep it open) */
fun railOpenWidth(big: Boolean): Dp = RAIL_OPEN * (if (big) BIG_SCALE else 1f)

private val RAIL_TUCKED = 66.dp
private val RAIL_OPEN = 228.dp
private val ITEM = 42.dp
private val ICON = 22.dp
private val PICTURE = 28.dp
private val PANEL_WIDTH = 300.dp
private const val BIG_SCALE = 1.2f
private const val RAIL_MS = 170
private const val PANEL_MS = 200
private const val PANEL_OUT_MS = 140
private const val PANEL_FOCUS_MS = 40L
private const val DIM = .66f
private const val STRAY_RETRY_MS = 150L
private const val STRAY_TRIES = 54

/** How long after ☰ Menu (or the menu's own focus calls) the focus arriving on the menu counts as wanted */
private const val INVITE_MS = 900L

/** After picking a page, how long the menu treats focus landing on it as "fell here" unless Left was pressed */
private const val SETTLE_MS = 8_000L
private const val TIP_PREFS = "benflix_rail_tip"
private const val TIP_DELAY_MS = 2500L
private const val TIP_SHOW_MS = 7000L
