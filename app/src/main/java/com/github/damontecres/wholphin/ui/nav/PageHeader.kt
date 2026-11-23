package com.github.damontecres.wholphin.ui.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixSpace

/** What the left side of a page's header shows */
sealed interface HeaderStart {
    /** Home draws its own greeting */
    data object None : HeaderStart

    /** A door or a top-level page: its name (a door's in its colour) */
    data class Title(
        val text: String,
        val color: androidx.compose.ui.graphics.Color,
    ) : HeaderStart

    /** Movies, Shows and Anime on TV: nothing here, the page's own tabs sit in this spot */
    data object Empty : HeaderStart

    /** Deeper pages: the named Back button ("‹ Action") and the where-am-I line */
    data class Back(
        val name: String,
    ) : HeaderStart
}

/** Movies, Shows and Anime: their tabs sit in the TV header's row (Glowfin new look, Ben 2026-11-22) */
fun Destination.tabsInHeader(): Boolean =
    this is Destination.MoviesDoor ||
        this is Destination.ShowsDoor ||
        this is Destination.AnimeDoor ||
        this is Destination.MusicDoor ||
        this is Destination.BooksDoor ||
        this is Destination.Games

fun headerStart(
    destination: Destination,
    stack: List<Destination>,
): HeaderStart {
    if (destination is Destination.Home) return HeaderStart.None
    // A movie, show, album or playlist page already shows its own name in big letters. Opened straight from
    // Home or Search it used to get that name again up here (Ben 2026-11-21: the name showed twice), so it
    // gets the named Back button like any deeper page.
    val showsOwnName = destination is Destination.MediaItem || destination is Destination.SeriesOverview
    if (showsOwnName && stack.size >= 2) return HeaderStart.Back(backName(stack) ?: "Back")
    if (stack.size <= 2 || destination.isDoorRoot()) {
        val door = destination.door()
        return when {
            door != null -> HeaderStart.Title(door.title, door.color)
            else -> WhereAmI.label(destination)?.let { HeaderStart.Title(it, BenflixColors.Text) } ?: HeaderStart.None
        }
    }
    return HeaderStart.Back(backName(stack) ?: "Back")
}

/**
 * Navigation redesign (approved tb-header): the page's own header replaces the old top bar. On the left the
 * door's name in its colour, or on deeper pages the named Back button and the where-am-I line. On the right the
 * music and audiobook pills and the big clock. Search and your picture live in the rail now.
 */
@Composable
fun BenflixPageHeader(
    destination: Destination,
    stack: List<Destination>,
    onBack: () -> Unit,
    onPick: (Int) -> Unit,
    showClock: Boolean,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    overPicture: Boolean = false,
    /** Pages under the header can name where Down goes (see [TopBarDown]) */
    downTarget: TopBarDown? = null,
) {
    val scale = if (big) 1.2f else 1f
    val down =
        Modifier.focusProperties {
            downTarget?.target?.let { down = it }
        }
    val start =
        headerStart(destination, stack).let {
            // Glowfin new look: Movies, Shows and Anime put their see-through tab pills in this row instead of the name
            if (it is HeaderStart.Title && destination.tabsInHeader()) HeaderStart.Empty else it
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = HEADER_START, end = BenflixSpace.tvOuterEdge, top = 18.dp, bottom = 6.dp)
                .height(TopNavBarHeight * scale)
                .focusGroup(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            // Home's left side is empty (Home draws its own greeting there), so no shadow: it showed as a wide
            // black oval across the top of Home that hid the greeting (Ben 2026-11-17)
            modifier =
                Modifier.weight(1f).then(
                    if (overPicture && start !is HeaderStart.None && start !is HeaderStart.Empty) Modifier.softShadow() else Modifier,
                ),
        ) {
            when (start) {
                is HeaderStart.Empty -> {}

                is HeaderStart.None -> {
                    com.github.damontecres.wholphin.ui.main
                        .ProfileBadge(scale = scale, showGuestLine = true)
                }

                is HeaderStart.Title -> {
                    Text(
                        text = start.text,
                        color = start.color,
                        fontSize = 24.sp * scale,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    com.github.damontecres.wholphin.ui.main
                        .ProfileBadge(scale = scale)
                }

                is HeaderStart.Back -> {
                    NamedBack(name = start.name, onClick = onBack, height = 38.dp * scale, modifier = down)
                    WhereAmILine(
                        stack = stack,
                        onPick = onPick,
                        fontSize = 14.sp * scale,
                        padded = false,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = if (overPicture) Modifier.softShadow() else Modifier,
        ) {
            // Benflix music: while music plays, a pill left of the clock on every page, Music too (OK opens
            // Now playing; Ben 2026-11-18 approved music-reach-1, the old strip at the bottom of Music pages is gone)
            com.github.damontecres.wholphin.ui.music
                .MusicNowPill(scale = scale, modifier = down)
            // Benflix Books: while an audiobook plays, a lime pill (OK opens the player)
            com.github.damontecres.wholphin.ui.books
                .AudiobookPill(scale = scale, modifier = down)
            if (showClock) BigClock(scale = scale)
        }
    }
}

/** The named Back button: "‹ Action" (approved pc-back) */
@Composable
fun NamedBack(
    name: String,
    onClick: () -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val shape = BenflixShapes.Pill
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.Raised.copy(alpha = .85f),
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border = focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.height(height).widthIn(max = 260.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 8.dp, end = 14.dp).height(height),
        ) {
            BenflixIconImage(BenflixIcon.BACK, color = BenflixColors.Text, size = height * 0.5f, contentDescription = "Back")
            Text(
                text = name,
                fontSize = (height.value * 0.4f).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The phone's header (navigation redesign: the old header with the logo, search and picture goes; those are in
 * the bottom bar now). A door's name in its colour (tap it for the door's places), or on deeper pages the named
 * Back button with the last two parts of the where-am-I line.
 */
@Composable
fun BenflixPhonePageHeader(
    destination: Destination,
    stack: List<Destination>,
    onBack: () -> Unit,
    onPick: (Int) -> Unit,
    onPlaces: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val start = headerStart(destination, stack)
    if (start is HeaderStart.None) {
        Row(modifier.padding(start = 16.dp, top = 8.dp)) {
            com.github.damontecres.wholphin.ui.main
                .ProfileBadge(scale = 0.9f)
        }
        return
    }
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (start) {
                is HeaderStart.Title -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            if (onPlaces != null) {
                                Modifier.clickable(onClickLabel = "Places in ${start.text}", onClick = onPlaces)
                            } else {
                                Modifier
                            },
                    ) {
                        Text(
                            text = start.text,
                            color = start.color,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (onPlaces != null) {
                            Spacer(Modifier.width(4.dp))
                            BenflixIconImage(BenflixIcon.DOWN, color = start.color, size = 20.dp, contentDescription = null)
                        }
                    }
                    com.github.damontecres.wholphin.ui.main
                        .ProfileBadge(scale = 0.9f)
                }

                is HeaderStart.Back -> {
                    NamedBack(name = start.name, onClick = onBack, height = 36.dp)
                }

                else -> {}
            }
        }
        if (start is HeaderStart.Back) {
            WhereAmILine(
                stack = stack,
                onPick = onPick,
                maxSteps = 2,
                padded = false,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp),
            )
        }
    }
}

/** The header starts this far right of the rail */
private val HEADER_START = 28.dp
