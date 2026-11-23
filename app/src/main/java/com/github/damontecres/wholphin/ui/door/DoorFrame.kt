package com.github.damontecres.wholphin.ui.door

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.nav.LocalTopBarOverlap
import com.github.damontecres.wholphin.ui.nav.focusRing
import com.github.damontecres.wholphin.ui.preferences.glowfinDimmedPage
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.LastRemoteKey
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

/** One place in a door's side menu (Home, All movies, Genres, Consoles, Audiobooks…) */
data class DoorSection(
    val key: String,
    val label: String,
    val icon: BenflixIcon,
    /** Shown small beside the name when the menu is open (how many are in there) */
    val count: Int? = null,
)

/** Every door's menu starts with its own Home */
const val DOOR_HOME = "home"

fun doorHome(): DoorSection = DoorSection(DOOR_HOME, "Home", BenflixIcon.HOME)

/**
 * Redesign 2 (section 6, approved sm-rail): what the shared door frame knows about the door inside it.
 * [section] is the place you're on. A door whose sections are parts of one long page ([jumps]: Live TV,
 * Games) scrolls to them; the others swap the page for that section's page.
 */
@Stable
class DoorFrameState(
    val door: BenflixDoor,
    start: String,
    val jumps: Boolean,
    /** The pill's colour (the door's own colour unless a page that isn't a door, like Server, picks one) */
    val pillColor: Color = door.color,
    val onPillColor: Color = door.onColor,
) {
    var section by mutableStateOf(start)

    /** The door sets this: true while you're at the top of its Home (first row) */
    var atTop by mutableStateOf(true)

    /** The door sets this: scroll back to the top and onto the first card */
    var toTop: () -> Unit = {}

    /** Jump doors set this: scroll to a section of the long page and put focus on it */
    var onJump: (String) -> Unit = {}

    /** Counts up each time a section is picked, so the page can move focus onto it */
    var picks by mutableIntStateOf(0)
        internal set

    internal val content = FocusRequester()

    fun pick(key: String) {
        section = key
        picks++
        if (jumps) onJump(key)
    }
}

/** The frame's state for a door. Opening a door again within 10 minutes starts on the section you were on. */
@Composable
fun rememberDoorFrameState(
    door: BenflixDoor,
    jumps: Boolean = false,
): DoorFrameState = remember(door) { DoorFrameState(door, DoorMemory.recall(door)?.section ?: DOOR_HOME, jumps) }

/**
 * Ben (approved dr-p-return): a door you open again within 10 minutes comes back where you were
 * (the same section, row and card, or the same part of a long page). After that it starts at the top.
 * Kept in memory only, so restarting the app starts every door at the top.
 */
object DoorMemory {
    data class Spot(
        val section: String = DOOR_HOME,
        /** Movies, Shows and Music: the row you were on, and the card in it */
        val row: String? = null,
        val index: Int = 0,
        /** Long pages (Live TV, Games): how far down you were */
        val scroll: Int = 0,
        val scrollOffset: Int = 0,
        val at: Long = System.currentTimeMillis(),
    )

    private val spots = ConcurrentHashMap<BenflixDoor, Spot>()

    fun recall(door: BenflixDoor): Spot? = spots[door]?.takeIf { System.currentTimeMillis() - it.at < RETURN_MS }

    fun keep(
        door: BenflixDoor,
        spot: Spot,
    ) {
        spots[door] = spot.copy(at = System.currentTimeMillis())
    }

    fun forget(door: BenflixDoor) {
        spots.remove(door)
    }

    /** Switching profile starts every door at the top for the next person */
    fun clear() = spots.clear()

    private const val RETURN_MS = 10 * 60 * 1000L
}

/**
 * Redesign 2 (section 6): the one frame every door sits in (Movies, Shows, Live TV, Games, Music, Books).
 * TV: a slim icon rail on the left; the section you're on sits on a pill in the door's colour. Left from
 * the first card slides the rail open over the page with names and counts; Right (or Back) tucks it away.
 * Wide touch screens: the rail stays open. Phones: the same sections as chips along the top.
 * Back: an open section goes to the door's Home, then the top of Home, then out of the door.
 */
@Composable
fun DoorFrame(
    state: DoorFrameState,
    sections: List<DoorSection>,
    modifier: Modifier = Modifier,
    /** False while the door is still finding out which sections it has (a saved section isn't dropped yet) */
    ready: Boolean = true,
    /** Phones: give the page the usual side margins (doors that don't draw edge to edge themselves) */
    phoneInset: Boolean = false,
    /** Phones: the sections as chips along the top (Music has its own tabs along the bottom instead) */
    phoneChips: Boolean = true,
    /**
     * Pages that aren't doors (Server) keep their own side menu. A real door's places are in the Benflix rail's
     * slide-out panel (TV) or the places sheet (phones) instead (navigation redesign, Nov 2026).
     */
    ownRail: Boolean = false,
    /** Glowfin (ui-7): the places as word tabs along the page's top (Movies, Shows, Anime) */
    tabs: Boolean = false,
    /** Glowfin new look (Movies, Shows, Anime on TV): the tabs sit in the header's own row, where the name was */
    tabsInHeader: Boolean = false,
    /** Glowfin (S1): TV shows [ownRail]'s places as a plain list under this heading, like Settings (Server) */
    sideListTitle: String? = null,
    content: @Composable (Modifier) -> Unit,
) {
    val isTv = rememberIsTv()
    val context = androidx.compose.ui.platform.LocalContext.current
    if (!ownRail) {
        androidx.compose.runtime.DisposableEffect(state) {
            DoorPlaces.attach(state)
            onDispose { DoorPlaces.detach(state) }
        }
        LaunchedEffect(sections, ready) {
            if (ready) DoorPlaces.update(context, state.door, sections)
        }
        // A place picked in the rail's panel (or the phone's sheet) before this door was open
        LaunchedEffect(ready) {
            if (!ready) return@LaunchedEffect
            val key = DoorPlaces.takePending(state.door) ?: return@LaunchedEffect
            if (sections.none { it.key == key }) return@LaunchedEffect
            delay(PENDING_PICK_MS)
            if (key == DOOR_HOME) state.toTop() else state.pick(key)
        }
    }
    val config = LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp || config.smallestScreenWidthDp >= 600
    // A section that's gone (Kids, or a library that emptied) falls back to Home
    LaunchedEffect(sections.map { it.key }, ready) {
        if (ready && sections.isNotEmpty() && sections.none { it.key == state.section }) state.section = DOOR_HOME
    }
    // Ben: Back goes to the top of the door first, then Home. Inner Back steps (an open console, series
    // or author) are drawn later, so they come first.
    BackHandler(enabled = state.section != DOOR_HOME || !state.atTop) {
        if (state.section != DOOR_HOME && !state.jumps) {
            state.pick(DOOR_HOME)
        } else {
            state.section = DOOR_HOME
            state.toTop()
        }
    }
    // A section picked from the menu: focus moves onto its page (jump doors do that themselves)
    LaunchedEffect(state.picks) {
        // (Glowfin tabs keep the focus on the tab you picked, like Wholphin; Down goes into the page)
        if (state.picks == 0 || state.jumps || !isTv || tabs) return@LaunchedEffect
        delay(SECTION_FOCUS_MS)
        state.content.tryRequestFocus()
    }
    if (tabs && sections.size > 1) {
        val overlap = LocalTopBarOverlap.current
        val tabsHeight = doorTabsHeight(wide)
        if (!wide) {
            // Phones: the tabs, then the page under them
            Column(modifier.fillMaxSize()) {
                DoorTabs(state, sections, wide = false, modifier = Modifier.padding(top = overlap).height(tabsHeight))
                content(Modifier.weight(1f).fillMaxWidth())
            }
            return
        }
        // TV and wide screens (Glowfin new look): the tabs are see-through pills in the header's own row, where the
        // page's name used to be, so the page starts right under the header. The art goes up behind them.
        val inHeader = tabsInHeader && tabsFitInHeader(overlap)
        Box(modifier.fillMaxSize()) {
            androidx.compose.runtime.CompositionLocalProvider(LocalTopBarOverlap provides if (inHeader) overlap else overlap + tabsHeight) {
                content(
                    Modifier
                        .fillMaxSize()
                        .focusRequester(state.content)
                        .focusRestorer(),
                )
            }
            DoorTabs(
                state,
                sections,
                wide = true,
                modifier =
                    if (inHeader) {
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(top = HEADER_ROW_TOP)
                            .height(overlap - HEADER_ROW_GAP)
                    } else {
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(top = overlap)
                            .height(tabsHeight)
                    },
            )
        }
        return
    }
    if (!wide) {
        val chips = ownRail && sections.size > 1 && phoneChips
        Box(modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                if (chips) {
                    DoorChips(state, sections, Modifier.padding(top = LocalTopBarOverlap.current))
                }
                content(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(if (phoneInset) Modifier.padding(start = BenflixSpace.phoneEdge, end = 8.dp) else Modifier),
                )
            }
        }
        return
    }
    if (!ownRail) {
        // The Benflix rail sits to the left of every page now, so the door fills the rest
        Box(modifier.fillMaxSize()) {
            content(
                Modifier
                    .fillMaxSize()
                    .focusRequester(state.content)
                    .focusRestorer(),
            )
        }
        return
    }
    if (sideListTitle != null && sections.size > 1) {
        GlowfinSideListFrame(state, sections, sideListTitle, modifier, content)
        return
    }
    if (sections.size <= 1) {
        Box(modifier.fillMaxSize()) {
            content(Modifier.fillMaxSize().padding(start = if (isTv) RAIL_TUCKED - CONTENT_TUCK else 0.dp))
        }
        return
    }
    Box(modifier.fillMaxSize()) {
        content(
            Modifier
                .fillMaxSize()
                .padding(start = if (isTv) RAIL_TUCKED - CONTENT_TUCK else RAIL_OPEN)
                .focusRequester(state.content)
                .focusRestorer(),
        )
        DoorRail(state, sections, alwaysOpen = !isTv)
    }
}

/** Glowfin (S1): the places in a plain list on the left (always open, words only), the page on the right */
@Composable
private fun GlowfinSideListFrame(
    state: DoorFrameState,
    sections: List<DoorSection>,
    title: String,
    modifier: Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val active = remember { FocusRequester() }
    val overlap = LocalTopBarOverlap.current
    // New look: the same as Settings, pills on the left and the page in a tall right-hand panel
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        modifier = modifier.fillMaxSize().glowfinDimmedPage(),
    ) {
        com.github.damontecres.wholphin.ui.preferences.GlowfinSideListPanel {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .focusRestorer(active)
                        .verticalScroll(rememberScrollState())
                        .padding(top = overlap),
            ) {
                com.github.damontecres.wholphin.ui.preferences
                    .GlowfinPageHeading(title)
                sections.forEach { section ->
                    val on = section.key == state.section
                    com.github.damontecres.wholphin.ui.preferences.GlowfinListRow(
                        label = section.label,
                        selected = on,
                        onClick = { state.pick(section.key) },
                        modifier = if (on) Modifier.focusRequester(active) else Modifier,
                    )
                }
            }
        }
        com.github.damontecres.wholphin.ui.preferences.GlowfinRightPanel(Modifier.weight(1f)) {
            content(
                Modifier
                    .fillMaxSize()
                    .focusRequester(state.content)
                    .focusRestorer(),
            )
        }
    }
}

/** The rail on the left (TV and wide screens) */
@Composable
private fun DoorRail(
    state: DoorFrameState,
    sections: List<DoorSection>,
    alwaysOpen: Boolean,
) {
    var focused by remember { mutableStateOf(false) }
    val open = alwaysOpen || focused
    // Cheap on old TVs: only the width moves, and the names are only drawn while it's open
    val width by animateDpAsState(if (open) RAIL_OPEN else RAIL_TUCKED, tween(RAIL_MS), label = "doorRail")
    val active = remember { FocusRequester() }
    val overlap = LocalTopBarOverlap.current
    // Right or Back tucks the rail away again and goes back to the card you were on
    BackHandler(enabled = focused && !alwaysOpen) { state.content.tryRequestFocus() }
    // Ben: a few seconds after a door opened, the focus often jumped onto this menu by itself (rows swapping
    // in took away the card you were on, and the focus fell here). The menu only keeps the focus when you
    // moved onto it with the remote; otherwise it hands it straight back to the page.
    var strays by remember { mutableIntStateOf(0) }
    LaunchedEffect(strays) {
        if (strays == 0) return@LaunchedEffect
        repeat(STRAY_TRIES) {
            if (!focused || LastRemoteKey.movedRecently()) return@LaunchedEffect
            state.content.tryRequestFocus()
            delay(STRAY_RETRY_MS)
        }
    }
    Box(
        Modifier
            .fillMaxHeight()
            .width(width)
            .background(
                if (open) {
                    Brush.horizontalGradient(
                        0f to BenflixColors.Tray,
                        0.86f to BenflixColors.Tray.copy(alpha = .97f),
                        1f to BenflixColors.Tray.copy(alpha = 0f),
                    )
                } else {
                    Brush.horizontalGradient(0f to BenflixColors.Tray.copy(alpha = .75f), 1f to Color.Transparent)
                },
            ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier =
                Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, top = overlap + 10.dp, bottom = 24.dp)
                    .onFocusChanged {
                        val came = it.hasFocus && !focused
                        focused = it.hasFocus
                        if (came && !alwaysOpen && !LastRemoteKey.movedRecently()) strays++
                    }.focusRestorer(active)
                    .onPreviewKeyEvent { event ->
                        if (!alwaysOpen && event.key == Key.DirectionRight) {
                            if (event.type == KeyEventType.KeyDown) state.content.tryRequestFocus()
                            true
                        } else {
                            false
                        }
                    },
        ) {
            sections.forEach { section ->
                val on = section.key == state.section
                RailItem(
                    section = section,
                    pill = state.pillColor,
                    onPill = state.onPillColor,
                    on = on,
                    open = open,
                    onClick = { state.pick(section.key) },
                    modifier = if (on) Modifier.focusRequester(active) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun RailItem(
    section: DoorSection,
    pill: Color,
    onPill: Color,
    on: Boolean,
    open: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = BenflixShapes.Pill
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (on) pill else Color.Transparent,
                contentColor = if (on) onPill else BenflixColors.Soft,
                focusedContainerColor = if (on) pill else BenflixColors.FocusRow,
                focusedContentColor = if (on) onPill else BenflixColors.Text,
            ),
        border = focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .height(ITEM_HEIGHT)
                .then(if (open) Modifier.fillMaxWidth() else Modifier.width(ITEM_HEIGHT)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(start = 11.dp, end = 14.dp),
        ) {
            BenflixIconImage(section.icon, color = LocalContentColor.current, size = 20.dp, contentDescription = section.label)
            if (open) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = section.label,
                    fontSize = BenflixType.body,
                    fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                section.count?.takeIf { it > 0 }?.let {
                    Text(
                        text = "$it",
                        fontSize = BenflixType.tag,
                        fontWeight = FontWeight.SemiBold,
                        color = if (on) onPill else BenflixColors.Muted,
                    )
                }
            }
        }
    }
}

/** Phones (approved sm-q-phone): the same sections as chips under the door's name */
@Composable
private fun DoorChips(
    state: DoorFrameState,
    sections: List<DoorSection>,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = BenflixSpace.phoneEdge, vertical = 8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(sections, key = { it.key }) { section ->
            DoorChip(
                label = section.label,
                door = state.door,
                pill = state.pillColor,
                onPill = state.onPillColor,
                selected = section.key == state.section,
                onClick = { state.pick(section.key) },
            )
        }
    }
}

/** A chip in the door's colour when picked, dark otherwise */
@Composable
fun DoorChip(
    label: String,
    door: BenflixDoor,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pill: Color = door.color,
    onPill: Color = door.onColor,
) {
    val shape = BenflixShapes.Pill
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (selected) pill else BenflixColors.Raised,
                contentColor = if (selected) onPill else BenflixColors.Text,
                focusedContainerColor = if (selected) pill else BenflixColors.FocusRow,
                focusedContentColor = if (selected) onPill else BenflixColors.Text,
            ),
        border = focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Text(
            text = label,
            fontSize = BenflixType.small,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

/**
 * Ben (TV photo, My List): a section page that has no side margin of its own needs this much, or the
 * tucked side menu's icons sit on its first poster and its title. Lines it up with the door's Home rows.
 * Wide touch screens already sit right of the open menu, so they need none.
 */
@Composable
fun doorSectionStart(): Dp = if (rememberIsTv()) BenflixSpace.tvEdge else 0.dp

private val RAIL_TUCKED = 76.dp
private val RAIL_OPEN = 250.dp

/** The tucked rail sits over this much of the page's own left margin */
private val CONTENT_TUCK = 28.dp
private val ITEM_HEIGHT = 46.dp
private const val RAIL_MS = 160
private const val SECTION_FOCUS_MS = 220L

/** A place picked from the rail waits this long after the door opens, so its first rows are in */
private const val PENDING_PICK_MS = 120L

/** The menu keeps handing a focus that fell on it back to the page while the page is still filling in */
private const val STRAY_RETRY_MS = 150L
private const val STRAY_TRIES = 20

/** The tabs fit in the header's own row (true on TVs and wide screens with the normal header) */
internal fun tabsFitInHeader(overlap: Dp): Boolean = overlap >= HEADER_ROW_GAP + doorTabsHeight(true) / 2

/** Where a wide door's page starts: under the header, and under the tabs too when they can't sit in the header */
internal fun doorPageTop(
    overlap: Dp,
    tabsInHeader: Boolean,
): Dp = if (tabsInHeader && tabsFitInHeader(overlap)) overlap else overlap + doorTabsHeight(true)

/** The TV header's row: 18dp from the top, with 6dp under it (see BenflixPageHeader) */
private val HEADER_ROW_TOP = 18.dp
private val HEADER_ROW_GAP = 24.dp
