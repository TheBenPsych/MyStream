package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.ui.components.GlowfinIcon
import com.github.damontecres.wholphin.ui.components.GlowfinIconImage
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont
import com.github.damontecres.wholphin.ui.theme.KidsLook
import com.github.damontecres.wholphin.ui.theme.glowFocus

/**
 * Glowfin Kids and Guest Homes (Ben's pick K1, ui-17, 18 Nov 2026). Easy mode is gone.
 * - Kids: "Hi there! What do you want to watch?" (no names: Kids is shared by every kid on the TV), three big
 *   round buttons (Movies lilac, Shows sky blue, Games mint), then one Keep watching row ("Picked for you" first).
 * - Guest: the normal Glowfin look, "Welcome!", a Guest tag, "Nothing you watch is saved", then Popular here
 *   and Recently added. No Keep watching, news, requests or My List.
 */
enum class FamilyKind { KIDS, GUEST }

/** Which smaller Home this profile gets, or null for the grown-up Home */
fun familyKindNow(): FamilyKind? =
    when {
        KidsMode.isActive -> FamilyKind.KIDS
        GuestMode.isActive -> FamilyKind.GUEST
        else -> null
    }

/** One picture in a Kids or Guest row: the name always shows under it */
data class FamilyCard(
    val id: String,
    val title: String,
    val image: String?,
    val destination: Destination,
    /** The title itself, so the TV Home can show its art and words at the top (new look) */
    val item: com.github.damontecres.wholphin.data.model.BaseItem? = null,
)

/** What the Kids and Guest Homes show, for one profile ([key] says whose) */
data class FamilyHomeData(
    val key: String = "",
    val cards: List<FamilyCard> = emptyList(),
    /** Kids only (Idea Box 25): titles Ben put on the Kids home, shown first as "Picked for you" */
    val picks: List<FamilyCard> = emptyList(),
    /** Guest only (ui-17): the newest movies and shows, under Popular here */
    val recent: List<FamilyCard> = emptyList(),
    val lines: Map<BenflixDoor, String> = emptyMap(),
    val loaded: Boolean = false,
)

/** The Kids Home's three big buttons */
val KidsDoors = listOf(BenflixDoor.MOVIES, BenflixDoor.SHOWS, BenflixDoor.GAMES)

private fun kidsIcon(door: BenflixDoor): GlowfinIcon =
    when (door) {
        BenflixDoor.SHOWS -> GlowfinIcon.SHOWS
        BenflixDoor.GAMES -> GlowfinIcon.GAMES
        else -> GlowfinIcon.MOVIES
    }

/** The Kids Home (K1). [firstFocus] goes on the Movies button, where the remote starts. */
@Composable
fun KidsHome(
    data: FamilyHomeData,
    wide: Boolean,
    firstFocus: FocusRequester,
    onDoor: (BenflixDoor) -> Unit,
    onCard: (FamilyCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val focus = rememberFamilyFocus()
    FamilyArt(wide = wide, focus = focus)
    Column(
        verticalArrangement = Arrangement.spacedBy(if (wide) 22.dp else 16.dp),
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(
                    start = if (wide) 8.dp else 0.dp,
                    end = if (wide) 24.dp else 8.dp,
                    top = if (wide) 24.dp else 8.dp,
                    bottom = 24.dp,
                ),
    ) {
        FamilyTop(wide = wide, focus = focus) {
            FamilyHeader(title = "Hi there!", line = "What do you want to watch?", wide = wide)
        }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
                KidsDoors.forEachIndexed { i, door ->
                    KidsButton(
                        door = door,
                        wide = true,
                        onClick = { onDoor(door) },
                        modifier =
                            Modifier
                                .onFocusChanged { if (it.isFocused) focus.raw = null }
                                .weight(1f)
                                .widthIn(max = 300.dp)
                                .height(112.dp)
                                .then(if (i == 0) Modifier.focusRequester(firstFocus) else Modifier),
                    )
                }
            }
        } else {
            KidsDoors.forEachIndexed { i, door ->
                KidsButton(
                    door = door,
                    wide = false,
                    onClick = { onDoor(door) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .then(if (i == 0) Modifier.focusRequester(firstFocus) else Modifier),
                )
            }
        }
        val pickIds = data.picks.map { it.id }.toSet()
        val rest = data.cards.filter { it.id !in pickIds }
        if (data.picks.isNotEmpty()) {
            FamilyRow(
                title = "Picked for you",
                cards = data.picks,
                posterHeight = if (wide) 150.dp else 140.dp,
                onCard = onCard,
                onFocus = { focus.raw = it },
            )
        }
        if (rest.isNotEmpty() || data.picks.isEmpty()) {
            FamilyRow(
                title = "Keep watching",
                cards = rest,
                empty = if (data.loaded) "Nothing started yet. Pick Movies or Shows to find something fun!" else null,
                posterHeight = if (wide) 150.dp else 140.dp,
                onCard = onCard,
                onFocus = { focus.raw = it },
            )
        }
    }
}

/** The Guest Home (ui-17): normal look, nothing saved. [firstFocus] goes on the first poster. */
@Composable
fun GuestHome(
    data: FamilyHomeData,
    wide: Boolean,
    firstFocus: FocusRequester,
    onCard: (FamilyCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = rememberFamilyFocus()
    FamilyArt(wide = wide, focus = focus)
    Column(
        verticalArrangement = Arrangement.spacedBy(if (wide) 22.dp else 16.dp),
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = if (wide) 8.dp else 0.dp,
                    end = if (wide) 24.dp else 8.dp,
                    top = if (wide) 24.dp else 8.dp,
                    bottom = 24.dp,
                ),
    ) {
        FamilyTop(wide = wide, focus = focus) {
            FamilyHeader(
                title = "Welcome!",
                line = "Watch anything here. Nothing you watch is saved.",
                wide = wide,
                tag = "Guest",
            )
        }
        val height = if (wide) 165.dp else 140.dp
        FamilyRow(
            title = "Popular here",
            cards = data.cards,
            empty = if (data.loaded) "Pick Movies or Shows to find something to watch." else null,
            posterHeight = height,
            firstFocus = firstFocus,
            onCard = onCard,
            onFocus = { focus.raw = it },
        )
        if (data.recent.isNotEmpty()) {
            FamilyRow(
                title = "Recently added",
                cards = data.recent,
                posterHeight = height,
                firstFocus = if (data.cards.isEmpty()) firstFocus else null,
                onCard = onCard,
                onFocus = { focus.raw = it },
            )
        }
    }
}

@Composable
private fun FamilyHeader(
    title: String,
    line: String,
    wide: Boolean,
    tag: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = title,
                color = BenflixColors.Text,
                fontFamily = GlowfinDisplayFont,
                fontSize = if (wide) 40.sp else 30.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (tag != null) {
                Text(
                    text = tag,
                    color = BenflixColors.Soft,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier =
                        Modifier
                            .background(BenflixColors.Line, RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
        Text(
            text = line,
            color = BenflixColors.Soft,
            fontSize = if (wide) BenflixType.lead else BenflixType.body,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One big round Kids button in its own colour: the icon, then the name. The remote's button gets a white ring
 * and a soft mint glow, and grows a little (Ben: kc-p-glow, ring + bigger).
 */
@Composable
private fun KidsButton(
    door: BenflixDoor,
    wide: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // New look: round buttons, in the Kids colours
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = door.color,
                contentColor = door.onColor,
                focusedContainerColor = door.color,
                focusedContentColor = door.onColor,
                pressedContainerColor = door.color,
                pressedContentColor = door.onColor,
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(border = BorderStroke(5.dp, Color.White), inset = 0.dp, shape = shape),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.glowFocus(shape = shape, spread = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (wide) 16.dp else 14.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = if (wide) 26.dp else 22.dp),
        ) {
            GlowfinIconImage(icon = kidsIcon(door), color = door.onColor, size = if (wide) 48.dp else 38.dp)
            Text(
                text = door.title,
                color = door.onColor,
                fontFamily = GlowfinDisplayFont,
                fontSize = if (wide) 32.sp else 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
    }
}

/** A titled row of posters with names under them */
@Composable
private fun FamilyRow(
    title: String,
    cards: List<FamilyCard>,
    posterHeight: Dp,
    onCard: (FamilyCard) -> Unit,
    empty: String? = null,
    firstFocus: FocusRequester? = null,
    onFocus: (FamilyCard) -> Unit = {},
) {
    val isTv =
        com.github.damontecres.wholphin.ui.touch
            .rememberIsTv()
    Column {
        Text(
            text = title,
            color = BenflixColors.Text,
            fontSize = BenflixType.rowName,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        if (cards.isEmpty()) {
            if (empty != null) Text(text = empty, color = BenflixColors.Soft, fontSize = BenflixType.body)
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(start = 4.dp, end = 24.dp, top = 6.dp, bottom = 6.dp),
            ) {
                items(cards, key = { it.id }) { card ->
                    FamilyPoster(
                        card = card,
                        height = posterHeight,
                        scale = 1f,
                        onClick = { onCard(card) },
                        // New look: cover art only on TV (Ben: "just cover art"); phones keep the names
                        nameUnder = !isTv,
                        modifier =
                            Modifier
                                .onFocusChanged { if (it.isFocused) onFocus(card) }
                                .then(
                                    if (firstFocus != null &&
                                        card.id == cards.first().id
                                    ) {
                                        Modifier.focusRequester(firstFocus)
                                    } else {
                                        Modifier
                                    },
                                ),
                    )
                }
            }
        }
    }
}

/** A poster with its name underneath (2 lines at most). Focus = the glow ring on the picture. */
@Composable
private fun FamilyPoster(
    card: FamilyCard,
    height: Dp,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    nameUnder: Boolean = true,
) {
    val shape = RoundedCornerShape(10.dp)
    val width = height * 2f / 3f
    Column(Modifier.width(width)) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(shape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = BenflixColors.PanelHigh,
                    focusedContainerColor = BenflixColors.PanelHigh,
                ),
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder = Border(border = BorderStroke(3.dp, BenflixColors.Outline), inset = 0.dp, shape = shape),
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier = modifier.glowFocus(shape = shape).width(width).height(height),
        ) {
            Box(Modifier.fillMaxSize()) {
                // No picture: the dark card with the name on it, like every other row
                Text(
                    text = card.title,
                    color = BenflixColors.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                )
                if (card.image != null) {
                    AsyncImage(
                        model = card.image,
                        contentDescription = card.title,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(shape)
                                .background(BenflixColors.PanelHigh),
                    )
                }
            }
        }
        if (nameUnder) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = card.title,
                color = BenflixColors.Soft,
                fontSize = BenflixType.small * scale,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = BenflixType.small * scale * 1.2f,
            )
        }
    }
}

/** The Kids / Big kids / Guest badge in the top bar */
@Composable
fun ProfileBadge(
    scale: Float = 1f,
    showGuestLine: Boolean = false,
) {
    val kids by KidsMode.active.collectAsState()
    val guest by GuestMode.active.collectAsState()
    if (!kids && !guest) return
    val (text, bg, fg) =
        if (kids) {
            // Kids look: the badge is sunny yellow (amber on the grown-up look, if ever shown there)
            Triple(KidsMode.displayName, KidsLook.palette?.accent ?: KIDS_BADGE, KidsLook.palette?.onAccent ?: Color(0xFF0B0C10))
        } else {
            Triple("Guest", BenflixColors.Line, BenflixColors.Soft)
        }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = text,
            color = fg,
            fontSize = 14.sp * scale,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .background(bg, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp * scale, vertical = 4.dp * scale),
        )
        if (guest && showGuestLine) {
            Text(
                text = "Nothing you watch is saved",
                color = BenflixColors.Muted,
                fontSize = 13.sp * scale,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The Kids badge colour (warm amber, as on the sheet) */
private val KIDS_BADGE = Color(0xFFFFB547)

/** Which card the remote is on, on the Kids and Guest Homes ([shown] waits until you stop moving) */
@androidx.compose.runtime.Stable
internal class FamilyFocus {
    var raw by androidx.compose.runtime.mutableStateOf<FamilyCard?>(null)
    var shown by androidx.compose.runtime.mutableStateOf<FamilyCard?>(null)
}

@Composable
internal fun rememberFamilyFocus(): FamilyFocus {
    val focus = androidx.compose.runtime.remember { FamilyFocus() }
    androidx.compose.runtime.LaunchedEffect(focus.raw) {
        // Like Home: the top only changes once you stop moving, so nothing flashes along a row
        kotlinx.coroutines.delay(if (focus.raw == null) 0L else FAMILY_SETTLE_MS)
        focus.shown = focus.raw
    }
    return focus
}

/**
 * Glowfin new look "Kids pages in the same layout" (Ben 2026-11-22): on a TV the Kids (and Guest) Home gets the
 * big picture of the card you're on behind everything, like the grown-up Home, in the Kids colours.
 */
@Composable
internal fun FamilyArt(
    wide: Boolean,
    focus: FamilyFocus,
) {
    if (!wide) return
    val height = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    com.github.damontecres.wholphin.ui.door
        .DoorArt(item = focus.shown?.item, pageHeight = height)
}

/**
 * The top of the Kids and Guest Homes on a TV: the greeting, or (once you're on a card) its logo or name and a
 * few facts, like the grown-up Home's top (the easy version: no story, no scores). The same height either way, so
 * the buttons and rows never jump.
 */
@Composable
internal fun FamilyTop(
    wide: Boolean,
    focus: FamilyFocus,
    greeting: @Composable () -> Unit,
) {
    if (!wide) {
        greeting()
        return
    }
    Box(contentAlignment = Alignment.BottomStart, modifier = Modifier.fillMaxWidth().height(FAMILY_TOP)) {
        val item = focus.shown?.item
        if (item == null) {
            greeting()
        } else {
            com.github.damontecres.wholphin.ui.door.DoorInfo(
                item = item,
                easy = true,
                brightArt = false,
                modifier = Modifier.widthIn(max = 560.dp),
            )
        }
    }
}

private const val FAMILY_SETTLE_MS = 350L
private val FAMILY_TOP = 120.dp
