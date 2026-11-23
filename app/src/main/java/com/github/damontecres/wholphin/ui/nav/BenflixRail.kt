package com.github.damontecres.wholphin.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
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
import com.github.damontecres.wholphin.ui.preferences.GlowfinRing
import com.github.damontecres.wholphin.ui.preferences.OnWhiteText
import com.github.damontecres.wholphin.ui.setup.UserIconCardImage
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont
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

    /** The page next to the rail has the focus (set by the page's box) */
    var contentFocused by mutableStateOf(false)

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
    /** False once another page was opened on top of this one (this rail's page is going away) */
    val isTop: () -> Boolean = { true },
)

/** When a page was last picked in the side menu (shared by every page's own menu) */
internal object RailPick {
    @Volatile
    var at: Long = 0L
}

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
    // Ben 2026-11-19, the Wholphin way: the menu is open only because you opened it, never because the focus
    // happened to land on it. [opened] is switched on by exactly two things, Left from a page's first item (the
    // focus entering the menu moving Left) and ☰ Menu, and switched off by Right, Back, picking a page or the page
    // taking the focus. Every other way the focus gets here (a new page still loading, the old page's cards going
    // away, Down pressed while nothing had the focus) leaves the menu tucked ([quiet]) and hands the focus to the
    // page. The old guesswork (timers for when Left/Up/Down were last pressed) lived inside each page's own menu,
    // so a brand-new page's menu forgot a page had just been picked and could open by itself.
    var opened by remember { mutableStateOf(false) }
    val quiet = railFocused && !opened && !panelOpen && !model.alwaysOpen
    var handoffs by remember { mutableIntStateOf(0) }
    // (it stays drawn open through a short focus blink, see below)
    val open = model.alwaysOpen || opened || panelOpen
    // Glowfin new look (Ben approved 2026-11-22): the strip doesn't slide open any more. Its fade stretches wider
    // and the names fade in (Right / Back fades them away again). The fade is only read while drawing, so
    // opening and closing the menu never rebuilds the icons on each step (light on the weak TCL TVs).
    val fade = animateFloatAsState(if (open) 1f else 0f, tween(FADE_MS), label = "glowfinMenu")
    val fading by remember { derivedStateOf { fade.value > 0f } }
    // Names stay drawn while they fade away
    val wide = open || fading
    val railWidth = if (wide) RAIL_OPEN * scale else RAIL_TUCKED * scale
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

    // Ben 2026-11-19: opening the menu from a page always starts on Home
    fun focusCurrent() {
        opened = true
        spotFocus[Spot.HOME]?.tryRequestFocus()
    }

    fun closePanel() {
        opened = true
        val door = panelDoor
        panelDoor = null
        profileOpen = false
        if (door != null) doorFocus[door]?.tryRequestFocus() else spotFocus[Spot.PROFILE]?.tryRequestFocus()
    }

    /**
     * A page was picked: tuck the menu away now. Like Wholphin, the focus stays on the picked icon (the menu
     * looks closed) until the new page has something to land on, then the page takes it (see [handoffs]).
     * Pushing the focus at a page that wasn't drawn yet is what made the menu close and pop open again.
     */
    fun leaveFor(go: () -> Unit) {
        panelDoor = null
        profileOpen = false
        if (!model.alwaysOpen) {
            opened = false
            RailPick.at = android.os.SystemClock.uptimeMillis()
        }
        go()
        if (!model.alwaysOpen) handoffs++
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
    BackHandler(enabled = railFocused && !panelOpen && !model.alwaysOpen) {
        opened = false
        contentFocus.tryRequestFocus()
    }

    // The one hand-off from the menu to the page (Ben 2026-11-18, the Wholphin way): while the tucked menu holds
    // the focus quietly (a page was just picked, or the focus fell onto it when a page swapped its rows), keep
    // offering the focus to the page until the page takes it. It runs again for each new page, so it never lands
    // on the page that is going away. Left (or ☰ Menu) at any time opens the menu instead and stops it.
    LaunchedEffect(handoffs) {
        if (handoffs == 0) return@LaunchedEffect
        repeat(HANDOFF_TRIES) {
            delay(HANDOFF_STEP_MS)
            if (!railFocused || opened || panelOpen) return@LaunchedEffect
            // A page was picked: this menu belongs to the page that is fading away, so it never hands the focus
            // back to that page (the new page's own menu does the hand-off below)
            if (!model.isTop()) return@LaunchedEffect
            contentFocus.tryRequestFocus()
        }
    }
    // Every page has its own menu. Right after a pick, the new page's menu keeps offering the focus to the new
    // page until the page has it (or you open the menu with Left / ☰ Menu), so the focus can't end up nowhere
    // and then fall onto the menu.
    LaunchedEffect(Unit) {
        if (model.alwaysOpen) return@LaunchedEffect
        if (android.os.SystemClock.uptimeMillis() - RailPick.at > PICK_FRESH_MS) return@LaunchedEffect
        repeat(HANDOFF_TRIES) {
            delay(HANDOFF_STEP_MS)
            if (control.contentFocused || opened || panelOpen) return@LaunchedEffect
            if (!model.isTop()) return@LaunchedEffect
            contentFocus.tryRequestFocus()
        }
    }
    // The open menu let go of the focus without Right, Back or a pick (it can blink off for a moment while you
    // move Up/Down in it): it stays open unless the focus doesn't come back to it soon
    LaunchedEffect(railFocused) {
        if (railFocused || !opened) return@LaunchedEffect
        delay(BLINK_MS)
        if (!railFocused) opened = false
    }

    Box(modifier.fillMaxSize()) {
        // The page's words dim to about 30% while the menu is open (never on wide touch screens, where it's always
        // open). One painted shade that fades in, no blur.
        if (wide && !model.alwaysOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(Glowfin.Ink.copy(alpha = DIM * fade.value)) }
                    .focusProperties { canFocus = false }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        panelDoor = null
                        profileOpen = false
                        opened = false
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
        // The strip's own soft fade (Ben 2026-11-22): ink 70% to 25% behind the icons, then it melts into the art
        // with no hard edge. Opening the menu just stretches the very same fade wider, with a soft edge.
        Box(
            Modifier
                .fillMaxHeight()
                .width(RAIL_OPEN * scale + FADE_EDGE_OPEN * scale)
                .drawBehind {
                    val f = fade.value
                    val solid = (RAIL_TUCKED + (RAIL_OPEN - RAIL_TUCKED) * f).toPx() * scale
                    val edge = (FADE_EDGE_TUCKED + (FADE_EDGE_OPEN - FADE_EDGE_TUCKED) * f).toPx() * scale
                    val end = solid + edge
                    drawRect(
                        brush =
                            Brush.horizontalGradient(
                                0f to Glowfin.Ink.copy(alpha = FADE_FROM),
                                solid / end to Glowfin.Ink.copy(alpha = FADE_TO),
                                1f to Color.Transparent,
                                startX = 0f,
                                endX = end,
                            ),
                        size =
                            androidx.compose.ui.geometry
                                .Size(end, size.height),
                    )
                },
        )
        val entryFocus = spotFocus.getValue(Spot.HOME)
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .fillMaxHeight()
                .layout { measurable, constraints ->
                    val w = railWidth.roundToPx().coerceIn(constraints.minWidth, constraints.maxWidth)
                    val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }.clipToBounds()
                .layout { measurable, constraints ->
                    // The icons and names are laid out once at the open width and the glide just shows more of
                    // them, so nothing inside has to be measured again on every step
                    val full = (RAIL_OPEN * scale).roundToPx().coerceAtLeast(constraints.maxWidth)
                    val placeable = measurable.measure(constraints.copy(minWidth = full, maxWidth = full))
                    layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
                },
        ) {
            androidx.compose.runtime.CompositionLocalProvider(LocalRailQuiet provides quiet, LocalRailNames provides fade) {
                // Scrolls only when the screen is too short for every icon (Easy's bigger rail, phones turned sideways)
                Column(
                    // Ben 2026-11-18 (approved music-reach-1): squeezed so your picture fits on a TV screen again
                    verticalArrangement = Arrangement.spacedBy(1.dp * scale),
                    modifier =
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = maxHeight)
                            .padding(horizontal = 14.dp * scale, vertical = 8.dp * scale)
                            .onFocusChanged {
                                val came = it.hasFocus && !railFocused
                                railFocused = it.hasFocus
                                // Ben 2026-11-19: one Left press from the first card of a row opens the menu (it
                                // used to need holding Left). The check on the way in below doesn't always see the
                                // Left, so a Left press that just moved the focus here counts too. It's one global
                                // key time, never a timer kept by this page's own menu.
                                if (came && !opened && !panelOpen && !model.alwaysOpen &&
                                    android.os.SystemClock.uptimeMillis() - LastRemoteKey.leftAt < LEFT_FRESH_MS
                                ) {
                                    opened = true
                                    entryFocus.tryRequestFocus()
                                }
                                // The focus got here without you opening the menu: it stays tucked and the
                                // page gets the focus back
                                if (came && !opened && !panelOpen && !model.alwaysOpen) handoffs++
                            }.focusGroup()
                            .focusProperties {
                                // Ben 2026-11-19: moving into the menu from a page always starts on Home (it
                                // used to come back to the last icon you were on, like Requests)
                                onEnter = {
                                    // Left from the page is the one arrow that opens the menu
                                    if (requestedFocusDirection == FocusDirection.Left && !model.alwaysOpen) opened = true
                                    if (requestedFocusDirection == FocusDirection.Left ||
                                        requestedFocusDirection == FocusDirection.Right ||
                                        requestedFocusDirection == FocusDirection.Up ||
                                        requestedFocusDirection == FocusDirection.Down
                                    ) {
                                        entryFocus.tryRequestFocus()
                                    }
                                }
                                onExit = {
                                    if (requestedFocusDirection == FocusDirection.Up || requestedFocusDirection == FocusDirection.Down) {
                                        cancelFocusChange()
                                    }
                                }
                            }.onPreviewKeyEvent { event ->
                                if (!model.alwaysOpen && !panelOpen && event.key == Key.DirectionRight) {
                                    if (event.type == KeyEventType.KeyDown) {
                                        opened = false
                                        contentFocus.tryRequestFocus()
                                    }
                                    true
                                } else if (quiet) {
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
                                .padding(start = 4.dp * scale)
                                .focusProperties { canFocus = false }
                                .clickable(onClickLabel = "Home", onClick = model.onHome),
                    ) {
                        BenflixMark(size = 26.dp * scale)
                        if (wide) {
                            Spacer(Modifier.width(12.dp))
                            Box(Modifier.graphicsLayer { alpha = fade.value }) {
                                com.github.damontecres.wholphin.ui.components
                                    .GlowfinWord(fontSize = 19.sp * scale)
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp * scale))
                    // Ben 2026-11-18 (approved music-reach-1): while music or an audiobook plays, its cover sits
                    // right under the logo; OK opens Now playing (or the audiobook player)
                    NowPlayingRailButton(
                        open = wide,
                        scale = scale,
                        destination = model.destination,
                        onGo = { go -> leaveFor(go) },
                    )
                    // Glowfin menu (ui-2): Search, Home, Movies, Shows, Anime, Live TV, Music, Games, Books, Requests,
                    // then your picture at the bottom (it opens the Me page, where Settings lives)
                    if (model.showSearch) {
                        RailButton(
                            icon = GlowfinIcon.SEARCH,
                            label = "Search",
                            open = wide,
                            scale = scale,
                            on = currentSpot == Spot.SEARCH,
                            onClick = { leaveFor(model.onSearch) },
                            modifier = Modifier.focusRequester(spotFocus.getValue(Spot.SEARCH)),
                        )
                    }
                    RailButton(
                        icon = GlowfinIcon.HOME,
                        label = "Home",
                        open = wide,
                        scale = scale,
                        on = currentSpot == Spot.HOME,
                        onClick = { leaveFor(model.onHome) },
                        modifier = Modifier.focusRequester(spotFocus.getValue(Spot.HOME)),
                    )
                    GlowfinDoorOrder.filter { it in model.doors }.forEach { door ->
                        RailButton(
                            icon = door.glowIcon(),
                            label = door.title,
                            open = wide,
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
                                open = wide,
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
                            open = wide,
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
                            open = wide,
                            scale = scale,
                            onClick = { leaveFor(model.onUpdate) },
                        )
                    }
                    ProfileRailButton(
                        model = model,
                        open = wide,
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
            modifier = Modifier.offset { IntOffset(railWidth.roundToPx(), 0) },
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
                focusedContentColor = OnWhiteText,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            Modifier
                .height(ITEM * scale)
                .then(if (open) Modifier.fillMaxWidth() else Modifier.width(ITEM * scale))
                .onFocusChanged { hasFocus = it.isFocused },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .railLit(focused, shape),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // Closed: the ring sits exactly in the middle ((34 - 28) / 2 = 3 each side)
                modifier = Modifier.fillMaxSize().padding(start = 3.dp * scale, end = if (open) 12.dp * scale else 0.dp),
            ) {
                val tint = if (focused) OnWhiteText else Glowfin.Aqua
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
                    Column(Modifier.weight(1f).railNameFade()) {
                        Text(
                            text = "Update",
                            color = if (focused) OnWhiteText else BenflixColors.Text,
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
                focusedContentColor = OnWhiteText,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .height(ITEM * scale)
                .then(if (open) Modifier.fillMaxWidth() else Modifier.width(ITEM * scale))
                .onFocusChanged { hasFocus = it.isFocused },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .railLit(focused, shape),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // Closed: the icon sits exactly in the middle of the round glow ((34 - 22) / 2 = 6 each side)
                modifier = Modifier.fillMaxSize().padding(start = 6.dp * scale, end = if (open) 12.dp * scale else 0.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ICON * scale)) {
                    GlowfinIconImage(
                        icon,
                        // Glowfin new look (Ben 2026-11-22): the page you're on stays aqua, the lit place is dark
                        // on its white pill
                        color =
                            when {
                                focused -> OnWhiteText
                                on -> Glowfin.Aqua
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
                                focused -> OnWhiteText
                                on -> Glowfin.Aqua
                                else -> NameColor
                            },
                        fontSize = BenflixType.body * scale,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).railNameFade(),
                    )
                    if (count > 0) CountDot(count, scale, Modifier.railNameFade())
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
                focusedContentColor = OnWhiteText,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .height(ITEM * scale)
                .then(if (open) Modifier.fillMaxWidth() else Modifier.width(ITEM * scale))
                .onFocusChanged { hasFocus = it.isFocused }
                .railLit(focused, shape),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // Closed: your picture sits exactly in the middle of the round glow ((34 - 28) / 2 = 3 each side)
            modifier = Modifier.fillMaxSize().padding(start = 3.dp * scale, end = if (open) 10.dp * scale else 0.dp),
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
                    color = if (focused) OnWhiteText else BenflixColors.Text,
                    fontSize = BenflixType.body * scale,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).railNameFade(),
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

/**
 * Ben 2026-11-18 (approved music-reach-1): the Now playing spot right under the logo, only while music or an
 * audiobook is on. Closed: the cover with small violet bars moving on it. Open: the song (or book), the
 * artist (or author) and a thin line for how far through it is. OK opens Now playing (or the audiobook player).
 */
@Composable
private fun NowPlayingRailButton(
    open: Boolean,
    scale: Float,
    destination: Destination,
    onGo: (() -> Unit) -> Unit,
    music: com.github.damontecres.wholphin.ui.music.MusicPillViewModel =
        androidx.hilt.lifecycle.viewmodel.compose
            .hiltViewModel(),
    books: com.github.damontecres.wholphin.ui.books.AudiobookViewModel =
        androidx.hilt.lifecycle.viewmodel.compose
            .hiltViewModel(),
) {
    val song by music.musicService.state.collectAsState()
    val book by books.audiobooks.now.collectAsState()
    val audio = song.currentAudio?.takeIf { song.queueSize > 0 }
    val songPlaying = audio != null && song.status == com.github.damontecres.wholphin.services.NowPlayingStatus.PLAYING
    val b = book
    // Music first, unless only the audiobook is actually playing
    val showBook = b != null && (audio == null || (b.playing && !songPlaying))
    if (audio == null && !showBook) return
    val playing = if (showBook) b!!.playing else songPlaying
    val title = if (showBook) b!!.name else audio!!.title.orEmpty()
    val line = if (showBook) b!!.author else audio!!.artistNames.orEmpty()
    val on = if (showBook) destination is Destination.AudiobookPlayer else destination is Destination.NowPlaying
    var progress by remember { mutableFloatStateOf(0f) }
    // Only while the menu is open (nobody sees the line when it's tucked away), so weak TVs do no extra work
    LaunchedEffect(open, showBook, audio?.key, b?.id) {
        while (open) {
            progress =
                if (showBook) {
                    val n = books.audiobooks.now.value
                    if (n != null && n.total > 0) (n.position / n.total).toFloat().coerceIn(0f, 1f) else 0f
                } else {
                    val p = music.musicService.player
                    val d = p.duration.takeIf { it > 0 } ?: audio?.runtime?.inWholeMilliseconds ?: 0L
                    if (d > 0) (p.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
                }
            delay(NOW_TICK_MS)
        }
    }
    val shape = RoundedCornerShape(percent = 50)
    val quiet = LocalRailQuiet.current
    var hasFocus by remember { mutableStateOf(false) }
    val focused = hasFocus && !quiet
    Surface(
        onClick = { onGo { if (showBook) books.open() else music.open() } },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = BenflixColors.Text,
                focusedContainerColor = Color.Transparent,
                focusedContentColor = OnWhiteText,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            Modifier
                .padding(bottom = 3.dp * scale)
                .height(ITEM * scale)
                .then(if (open) Modifier.fillMaxWidth() else Modifier.width(ITEM * scale))
                .onFocusChanged { hasFocus = it.isFocused },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .railLit(focused, shape),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // Closed: the cover sits exactly in the middle ((34 - 28) / 2 = 3 each side)
                modifier = Modifier.fillMaxSize().padding(start = 3.dp * scale, end = if (open) 12.dp * scale else 0.dp),
            ) {
                val coverShape = RoundedCornerShape(6.dp * scale)
                Box(
                    contentAlignment = Alignment.BottomCenter,
                    modifier =
                        Modifier
                            .size(PICTURE * scale)
                            .border(
                                1.5.dp,
                                if (focused) {
                                    OnWhiteText
                                } else if (on) {
                                    Glowfin.Aqua
                                } else {
                                    Glowfin.Violet
                                },
                                coverShape,
                            ).clip(coverShape),
                ) {
                    if (showBook) {
                        com.github.damontecres.wholphin.ui.books
                            .BookCoverImage(b!!.cover, b.name, Modifier.fillMaxSize(), audio = true)
                    } else {
                        com.github.damontecres.wholphin.ui.music
                            .CoverImage(audio!!.imageUrl, Modifier.fillMaxSize(), shape = coverShape)
                    }
                    // The little moving bars, on a soft dark strip so they show on any cover
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(12.dp * scale)
                                .background(Glowfin.Ink.copy(alpha = .55f)),
                    ) {
                        com.github.damontecres.wholphin.ui.music.SmallTowerLine(
                            1f,
                            playing,
                            Modifier.size(width = 20.dp * scale, height = 9.dp * scale),
                            towers = 4,
                        )
                    }
                }
                if (open) {
                    Spacer(Modifier.width(12.dp * scale))
                    Column(Modifier.weight(1f).railNameFade()) {
                        Text(
                            text = title,
                            color = if (focused) OnWhiteText else BenflixColors.Text,
                            fontSize = 13.sp * scale,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (line.isNotBlank()) {
                            Text(
                                text = line,
                                color = if (focused) OnWhiteDim else Glowfin.Dim,
                                fontSize = 10.sp * scale,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(
                            Modifier
                                .padding(top = 2.dp * scale)
                                .fillMaxWidth()
                                .height(2.dp * scale)
                                .background(Glowfin.Line, RoundedCornerShape(50)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress)
                                    .background(Glowfin.GlowAcross, RoundedCornerShape(50)),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** How wide the tucked rail is (the page starts right of it) */
fun railTuckedWidth(big: Boolean): Dp = RAIL_TUCKED * (if (big) BIG_SCALE else 1f)

/** How wide the open rail is (wide touch screens keep it open) */
fun railOpenWidth(big: Boolean): Dp = RAIL_OPEN * (if (big) BIG_SCALE else 1f)

private val RAIL_TUCKED = 66.dp

// Glowfin new look: just wide enough for the longest name, so the open menu covers less of the art
private val RAIL_OPEN = 200.dp

// Ben 2026-11-18 (approved music-reach-1): 42 -> 34 so every spot and your picture fit on a TV screen
private val ITEM = 34.dp
private val ICON = 22.dp
private val PICTURE = 28.dp
private val PANEL_WIDTH = 300.dp
private const val BIG_SCALE = 1.2f
private const val RAIL_MS = 170
private const val NOW_TICK_MS = 1000L
private const val PANEL_MS = 200
private const val PANEL_OUT_MS = 140
private const val PANEL_FOCUS_MS = 40L

/** How dark the page gets while the menu is open: its words show at about 30% (Ben 2026-11-22) */
private const val DIM = .7f

/** The names and the wider fade come in (and go away) this fast */
private const val FADE_MS = 220

/** The strip's own fade (Ben 2026-11-22): ink 70% at the screen edge to 25% at the end of the strip... */
private const val FADE_FROM = .7f
private const val FADE_TO = .25f

/** ...then it melts into the art over this much more, so there's never a hard edge */
private val FADE_EDGE_TUCKED = 44.dp
private val FADE_EDGE_OPEN = 56.dp

/** Names that aren't lit (soft white) */
private val NameColor = Glowfin.Text.copy(alpha = .9f)

/** Small words on the white pill */
private val OnWhiteDim = Color(0xFF4A4F72)

/** How far the names have faded in (0 = tucked away, 1 = open) */
private val LocalRailNames = androidx.compose.runtime.compositionLocalOf<State<Float>> { mutableFloatStateOf(1f) }

/** The names fade with the menu (read only while drawing, so nothing is rebuilt as they fade) */
private fun Modifier.railNameFade(): Modifier =
    composed {
        val names = LocalRailNames.current
        graphicsLayer { alpha = names.value }
    }

/**
 * Glowfin new look (Ben 2026-11-22): the place the remote is on is a solid white pill with the soft aqua ring,
 * the same width for every place, like every other focus in the app. Nothing grows.
 */
private fun Modifier.railLit(
    focused: Boolean,
    shape: Shape,
): Modifier = if (focused) background(Color.White, shape).border(3.dp, GlowfinRing, shape) else this

/** The page gets the focus offered every 100 ms for up to 15 s (a slow server on a weak TV) */
private const val HANDOFF_STEP_MS = 100L
private const val HANDOFF_TRIES = 150

/** A focus blink off the open menu shorter than this doesn't close it */
private const val BLINK_MS = 600L

/** A Left press this recent is the one that moved the focus onto the menu */
private const val LEFT_FRESH_MS = 400L

/** A new page's menu helps the focus onto the page only if a page was picked this recently */
private const val PICK_FRESH_MS = 3000L

private const val TIP_PREFS = "benflix_rail_tip"
private const val TIP_DELAY_MS = 2500L
private const val TIP_SHOW_MS = 7000L
