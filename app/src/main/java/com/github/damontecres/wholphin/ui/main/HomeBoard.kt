package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.tryRequestFocus

/*
 * Benflix Home (Redesign 2, approved hm-tall 2026-11-10). Everything sits on one grid, measured in "u"
 * (on a TV, about 1.4% of the screen's width):
 *   doors  = six 13u squares, 3 by 2, 1.1u apart (bigger than before)
 *   news   = 2.4u to the right of the doors, a soft panel exactly as tall as them: Clear all at its top right,
 *            then up to 6 rows. Each row: a small square poster, a small coloured line saying what it is
 *            (NEW EPISODE · S3 E12), the name on up to 2 lines (a longer one slides by when you land on it), and an X.
 *            A busy day keeps the last line for "4 more" and See all, which opens the side panel.
 * Phones: the doors 3 by 2 across the screen, the panel (5 rows) underneath; See all opens a sheet from the bottom.
 */

private const val DOOR = 13f
private const val DOOR_GAP = 1.1f
private const val BLOCK_GAP = 2.4f
private const val BLOCK_HEIGHT = 2 * DOOR + DOOR_GAP
private const val LIST_WIDTH = 26f
private const val SCREEN_WIDTH = 3 * DOOR + 2 * DOOR_GAP + BLOCK_GAP + LIST_WIDTH

/** The soft dark panel behind the list (a step above the page, below the cards) */
private val PanelColor = Color(0xFF111319)
private val RowLine = Color(0xFF262A34)

@Composable
fun HomeBoard(
    greeting: String,
    weather: String?,
    doors: List<BenflixDoor>,
    rows: HomeParts,
    wide: Boolean,
    lite: Boolean,
    labelAlpha: (Int) -> Float,
    listAlpha: () -> Float,
    doorModifier: (BenflixDoor) -> Modifier,
    onDoor: (BenflixDoor) -> Unit,
    onRow: (HomeRow) -> Unit,
    onClear: (HomeRow) -> Unit,
    onClearAll: () -> Unit,
    onSeen: () -> Unit,
    focusDoors: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = remember(rows, wide) { arrangeHomeRows(rows, if (wide) MAX_HOME_ROWS else MAX_PHONE_ROWS) }
    // After an X or Clear all, the remote's focus moves to the next row's X (or back to the doors when the list is
    // empty), so it never gets lost on a TV
    val xFocus = remember { List(MAX_HOME_ROWS) { FocusRequester() } }
    var refocus by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(list) {
        val i = refocus ?: return@LaunchedEffect
        refocus = null
        if (list.rows.isEmpty()) focusDoors() else xFocus[minOf(i, list.rows.size - 1)].tryRequestFocus()
    }
    val clearRow: (HomeRow) -> Unit = { row ->
        refocus = list.rows.indexOf(row).coerceAtLeast(0)
        onClear(row)
    }
    val clearAll: () -> Unit = {
        refocus = 0
        onClearAll()
    }
    // See all: the side panel (TV) or the sheet from the bottom (phone) with everything new
    var seeAll by remember { mutableStateOf(false) }
    val seeAllFocus = remember { FocusRequester() }
    var backToSeeAll by remember { mutableStateOf(false) }
    LaunchedEffect(backToSeeAll) {
        if (!backToSeeAll) return@LaunchedEffect
        backToSeeAll = false
        kotlinx.coroutines.delay(150)
        if (!seeAllFocus.tryRequestFocus()) focusDoors()
    }
    if (seeAll) {
        LaunchedEffect(Unit) { onSeen() }
        SeeAllPanel(
            rows = rows.all,
            lite = lite,
            onRow = {
                seeAll = false
                onRow(it)
            },
            onClear = onClear,
            onClearAll = {
                seeAll = false
                clearAll()
            },
            onClose = {
                seeAll = false
                backToSeeAll = true
            },
        )
    }
    // Phones: the list is right there under the doors, so seeing Home counts as looking at it
    if (!wide) {
        LaunchedEffect(list) { if (list.rows.any { it.news }) onSeen() }
    }
    val rowWords = RowLook(onRow = onRow, onClear = clearRow, onFocus = onSeen, lite = lite)
    if (wide) {
        BoxWithConstraints(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(end = 24.dp, top = 12.dp, bottom = 34.dp),
        ) {
            // Room for the greeting and weather line above the doors
            val greetingRoom = 96.dp
            val u = minOf(maxWidth / SCREEN_WIDTH, (maxHeight - greetingRoom) / BLOCK_HEIGHT).coerceAtLeast(4.dp)
            Column(Modifier.fillMaxSize()) {
                Greeting(greeting, weather, tv = true)
                Spacer(Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(u * BLOCK_GAP),
                    modifier = Modifier.fillMaxWidth().height(u * BLOCK_HEIGHT),
                ) {
                    DoorGrid(doors, u, u * DOOR_GAP, labelAlpha, doorModifier, onDoor)
                    ListPanel(
                        list = list,
                        u = u,
                        look = rowWords,
                        xFocus = xFocus,
                        onClearAll = clearAll,
                        onSeeAll = { seeAll = true },
                        seeAllFocus = seeAllFocus,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .graphicsLayer { alpha = listAlpha() },
                    )
                }
            }
        }
    } else {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(end = 8.dp, top = 8.dp, bottom = 24.dp),
        ) {
            Greeting(greeting, weather, tv = false)
            Spacer(Modifier.height(18.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gap = 10.dp
                val u = (maxWidth - gap * 2) / 3 / DOOR
                DoorGrid(doors, u, gap, labelAlpha, doorModifier, onDoor)
            }
            if (list.rows.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                PhoneList(
                    list = list,
                    look = rowWords,
                    xFocus = xFocus,
                    onClearAll = clearAll,
                    onSeeAll = { seeAll = true },
                    seeAllFocus = seeAllFocus,
                    modifier = Modifier.graphicsLayer { alpha = listAlpha() },
                )
            }
        }
    }
}

/** What every row does, handed down to each one */
private class RowLook(
    val onRow: (HomeRow) -> Unit,
    val onClear: (HomeRow) -> Unit,
    val onFocus: () -> Unit,
    val lite: Boolean,
)

@Composable
private fun Greeting(
    greeting: String,
    weather: String?,
    tv: Boolean,
) {
    Column {
        Text(
            text = greeting,
            color = BenflixColors.Text,
            fontSize = BenflixType.pageTitle,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
        if (weather != null) {
            Text(
                text = weather,
                color = BenflixColors.Muted,
                fontSize = if (tv) BenflixType.body else BenflixType.small,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Word sizes shared by every door, so names line up */
private data class DoorWords(
    val name: TextUnit,
    val desc: TextUnit,
)

@Composable
private fun DoorGrid(
    doors: List<BenflixDoor>,
    u: Dp,
    gap: Dp,
    labelAlpha: (Int) -> Float,
    doorModifier: (BenflixDoor) -> Modifier,
    onDoor: (BenflixDoor) -> Unit,
) {
    val size = u * DOOR
    val pad = u * 0.85f
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val textWidth = with(density) { (size - pad * 2).roundToPx().coerceAtLeast(1) }
    val words =
        remember(textWidth, density.fontScale, doors) {
            val toSp = { d: Dp -> d.value / density.fontScale }
            // Start a little bigger than the sheet so the words are easy to read from the sofa, then shrink to fit
            var name = maxOf(toSp(u * 1.5f), 13f).coerceAtMost(30f)
            while (name > 9f && doors.any { !measurer.fits(it.title, name, FontWeight.ExtraBold, 1, textWidth) }) {
                name -= 0.5f
            }
            var desc = maxOf(toSp(u * 0.95f), 10.5f).coerceAtMost(17f)
            while (desc > 8f && doors.any { !measurer.fits(it.description, desc, FontWeight.SemiBold, 2, textWidth) }) {
                desc -= 0.5f
            }
            DoorWords(name.sp, desc.sp)
        }
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        doors.chunked(3).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                line.forEach { door ->
                    SquareDoor(
                        door = door,
                        u = u,
                        words = words,
                        // Read only while drawing, so the growing doors don't rebuild the page every frame
                        labelAlpha = { labelAlpha(door.ordinal) },
                        onClick = { onDoor(door) },
                        modifier = doorModifier(door).size(size),
                    )
                }
            }
        }
    }
}

/** One square door: line icon top left, name and two-line description bottom left. Focus = white outline only. */
@Composable
private fun SquareDoor(
    door: BenflixDoor,
    u: Dp,
    words: DoorWords,
    labelAlpha: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(u * 0.8f)
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
                focusedBorder = Border(border = BorderStroke(maxOf(3.dp, u * 0.22f), BenflixColors.Text), inset = 0.dp, shape = shape),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = labelAlpha() }
                .padding(u * 0.85f),
        ) {
            BenflixIconImage(icon = door.icon(), color = door.onColor, size = u * 1.8f, modifier = Modifier.align(Alignment.TopStart))
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(
                    text = door.title,
                    color = door.onColor,
                    fontSize = words.name,
                    lineHeight = words.name * 1.1f,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                )
                // Always two lines tall, so a door whose words fit on one line keeps its name level with the rest
                Text(
                    text = door.description,
                    color = door.onColor.copy(alpha = .85f),
                    fontSize = words.desc,
                    lineHeight = words.desc * 1.22f,
                    fontWeight = FontWeight.SemiBold,
                    minLines = 2,
                    maxLines = 2,
                    modifier = Modifier.padding(top = u * 0.2f),
                )
            }
        }
    }
}

private fun BenflixDoor.icon(): BenflixIcon =
    when (this) {
        BenflixDoor.MOVIES -> BenflixIcon.FILM
        BenflixDoor.SHOWS -> BenflixIcon.TV
        BenflixDoor.LIVE_TV -> BenflixIcon.LIVE
        BenflixDoor.GAMES -> BenflixIcon.PAD
        BenflixDoor.MUSIC -> BenflixIcon.NOTE
        BenflixDoor.BOOKS -> BenflixIcon.BOOK
    }

/** Does [text] fit in [lines] lines of [width] pixels at [sizeSp]? */
private fun TextMeasurer.fits(
    text: String,
    sizeSp: Float,
    weight: FontWeight,
    lines: Int,
    width: Int,
): Boolean {
    val result =
        measure(
            text = text,
            style = TextStyle(fontSize = sizeSp.sp, fontWeight = weight),
            constraints = Constraints(maxWidth = width),
            maxLines = lines,
            softWrap = lines > 1,
        )
    return !result.hasVisualOverflow
}

/** Word sizes shared by every list row, so they look even */
private data class RowWords(
    val kind: TextUnit,
    val title: TextUnit,
)

/** TV, tablets and phones turned sideways: the soft panel beside the doors, exactly as tall as them */
@Composable
private fun ListPanel(
    list: HomeList,
    u: Dp,
    look: RowLook,
    xFocus: List<FocusRequester>,
    onClearAll: () -> Unit,
    onSeeAll: () -> Unit,
    seeAllFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    if (list.rows.isEmpty()) {
        Box(modifier)
        return
    }
    val inset = u * 0.35f
    val header = u * 2.3f
    val slots = MAX_HOME_ROWS
    BoxWithConstraints(modifier) {
        // The thin lines between rows take 1 dp each
        val rowHeight = (maxHeight - inset * 2 - header - 1.dp * (slots - 1)) / slots
        val rowPad = u * 0.6f
        val clearSize = u * 2.1f
        val density = LocalDensity.current
        val words =
            remember(rowHeight, u, density.fontScale) {
                // Start at the sheet's sizes, then shrink both together until the small line and 2 lines of name fit
                val toSp = { d: Dp -> d.value / density.fontScale }
                var kind = toSp(u * 0.7f).coerceIn(9f, 16f)
                var title = toSp(u * 1.0f).coerceIn(11f, 22f)
                val room = (rowHeight - u * 0.5f).value
                while (title > 9f && (kind * 1.3f + title * 1.2f * 2) * density.fontScale > room) {
                    kind *= 0.95f
                    title *= 0.95f
                }
                RowWords(kind.sp, title.sp)
            }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(u * 0.9f))
                    .background(PanelColor)
                    .padding(inset),
        ) {
            // Clear all sits at the panel's top right (Up from the top row)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(header),
            ) {
                Spacer(Modifier.weight(1f))
                SmallButton("Clear all", onClearAll, tv = true)
            }
            list.rows.forEachIndexed { i, row ->
                if (i > 0) RowDivider(Modifier.padding(horizontal = rowPad))
                ListRow(
                    row = row,
                    look = look,
                    words = words,
                    gap = u * 0.8f,
                    thumb = rowHeight - u * 0.7f,
                    pad = rowPad,
                    corner = u * 0.6f,
                    clearSize = clearSize,
                    clearFocus = xFocus[i],
                    modifier = Modifier.fillMaxWidth().height(rowHeight),
                )
            }
            if (list.more > 0) {
                RowDivider(Modifier.padding(horizontal = rowPad))
                MoreLine(list.more, onSeeAll, seeAllFocus, tv = true, modifier = Modifier.height(rowHeight).padding(horizontal = rowPad))
            }
        }
    }
}

/** Phones: the same soft panel under the doors; rows are as tall as their words need */
@Composable
private fun PhoneList(
    list: HomeList,
    look: RowLook,
    xFocus: List<FocusRequester>,
    onClearAll: () -> Unit,
    onSeeAll: () -> Unit,
    seeAllFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PanelColor)
                .padding(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            SmallButton("Clear all", onClearAll, tv = false)
        }
        list.rows.forEachIndexed { i, row ->
            if (i > 0) RowDivider(Modifier.padding(horizontal = 12.dp))
            ListRow(
                row = row,
                look = look,
                words = RowWords(11.sp, 15.sp),
                gap = 12.dp,
                thumb = 46.dp,
                pad = 10.dp,
                corner = 11.dp,
                clearSize = 36.dp,
                clearFocus = xFocus[i],
                modifier = Modifier.fillMaxWidth(),
                vertical = 8.dp,
            )
        }
        if (list.more > 0) {
            RowDivider(Modifier.padding(horizontal = 12.dp))
            MoreLine(list.more, onSeeAll, seeAllFocus, tv = false, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
}

/** The bottom line on a busy day: "4 more" and See all */
@Composable
private fun MoreLine(
    more: Int,
    onSeeAll: () -> Unit,
    focus: FocusRequester,
    tv: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Text(
            text = "$more more",
            color = BenflixColors.Muted,
            fontSize = if (tv) BenflixType.body else BenflixType.small,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        SmallButton("See all ›", onSeeAll, tv = tv, modifier = Modifier.focusRequester(focus))
    }
}

/** See all: everything new in the side panel (TV and web) or a sheet from the bottom (phone), with Clear all */
@Composable
private fun SeeAllPanel(
    rows: List<HomeRow>,
    lite: Boolean,
    onRow: (HomeRow) -> Unit,
    onClear: (HomeRow) -> Unit,
    onClearAll: () -> Unit,
    onClose: () -> Unit,
) {
    val isTv =
        com.github.damontecres.wholphin.ui.touch
            .rememberIsTv()
    com.github.damontecres.wholphin.ui.components.BenflixPanelDialog(onDismissRequest = onClose) {
        val first = remember { FocusRequester() }
        val xs = remember(rows.size) { List(rows.size) { FocusRequester() } }
        val look = remember(lite) { RowLook(onRow = onRow, onClear = onClear, onFocus = {}, lite = lite) }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Text(
                    text = "Everything new",
                    color = BenflixColors.Text,
                    fontSize = BenflixType.pageTitle,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f),
                )
                SmallButton("Clear all", onClearAll, tv = isTv)
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (rows.isEmpty()) {
                    Text("Nothing new right now", color = BenflixColors.Muted, fontSize = BenflixType.body)
                    LaunchedEffect(Unit) { onClose() }
                }
                rows.forEachIndexed { i, row ->
                    ListRow(
                        row = row,
                        look = look,
                        words = if (isTv) RowWords(13.sp, 18.sp) else RowWords(11.sp, 15.sp),
                        gap = 12.dp,
                        thumb = if (isTv) 56.dp else 46.dp,
                        pad = 10.dp,
                        corner = 12.dp,
                        clearSize = 38.dp,
                        clearFocus = xs[i],
                        rowFocus = if (i == 0) first else null,
                        modifier = Modifier.fillMaxWidth(),
                        vertical = 8.dp,
                    )
                }
            }
        }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(150)
            first.tryRequestFocus()
        }
    }
}

@Composable
private fun RowDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(RowLine),
    )
}

/**
 * One news row: a small square poster, the small coloured line (what it is), and the name on up to 2 lines.
 * When you land on it, a name too long for 2 lines slides by on one line (not with Lite mode on).
 */
@Composable
private fun ListRow(
    row: HomeRow,
    look: RowLook,
    words: RowWords,
    gap: Dp,
    thumb: Dp,
    pad: Dp,
    corner: Dp,
    clearSize: Dp,
    clearFocus: FocusRequester,
    modifier: Modifier = Modifier,
    vertical: Dp = 0.dp,
    rowFocus: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier.fillMaxSize().padding(horizontal = pad, vertical = vertical),
        ) {
            Thumb(row, thumb.coerceAtLeast(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = row.kind.uppercase(),
                    color = row.kindColor,
                    fontSize = words.kind,
                    lineHeight = words.kind * 1.3f,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = words.kind * 0.06f,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val slide = focused && !look.lite
                Text(
                    text = row.title,
                    color = BenflixColors.Text,
                    fontSize = words.title,
                    lineHeight = words.title * 1.2f,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (slide) 1 else 2,
                    softWrap = !slide,
                    overflow = if (slide) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier =
                        if (slide) {
                            Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 700, repeatDelayMillis = 1_500)
                        } else {
                            Modifier
                        },
                )
            }
        }
    }
    val shape = RoundedCornerShape(corner)
    // Every row can be pressed (a row with nowhere to go opens a pop-up with all its words), and the X beside it
    // clears it (on a TV, press right to reach the X)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min),
    ) {
        RowSurface(
            onClick = { look.onRow(row) },
            shape = shape,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (rowFocus != null) Modifier.focusRequester(rowFocus) else Modifier)
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) look.onFocus()
                    },
            content = content,
        )
        ClearButton(
            { look.onClear(row) },
            clearSize,
            Modifier
                .padding(start = clearSize * 0.12f, end = clearSize * 0.05f)
                .focusRequester(clearFocus),
        )
    }
}

@Composable
private fun RowSurface(
    onClick: () -> Unit,
    shape: RoundedCornerShape,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
                pressedContainerColor = BenflixColors.FocusRow,
            ),
        border =
            com.github.damontecres.wholphin.ui.nav
                .focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        content()
    }
}

/** The small square poster (or music cover) on the left; the Benflix squares when there's no picture */
@Composable
private fun Thumb(
    row: HomeRow,
    size: Dp,
) {
    val shape = RoundedCornerShape(size * 0.14f)
    if (row.image == null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(size)
                    .clip(shape)
                    .background(BenflixColors.PanelHigh),
        ) {
            val sq = size * 0.22f
            Column(verticalArrangement = Arrangement.spacedBy(sq * 0.18f)) {
                listOf(BenflixColors.Movies to BenflixColors.Shows, BenflixColors.LiveTv to BenflixColors.Games).forEach { (a, b) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(sq * 0.18f)) {
                        Box(Modifier.size(sq).background(a, RoundedCornerShape(sq * 0.22f)))
                        Box(Modifier.size(sq).background(b, RoundedCornerShape(sq * 0.22f)))
                    }
                }
            }
        }
        return
    }
    AsyncImage(
        model = row.image,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            Modifier
                .size(size)
                .clip(shape)
                .background(BenflixColors.PanelHigh),
    )
}

/** The small X beside each row: clears that row for this profile */
@Composable
private fun ClearButton(
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = BenflixColors.Muted,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
                pressedContainerColor = BenflixColors.FocusRow,
            ),
        border =
            com.github.damontecres.wholphin.ui.nav
                .focusRing(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .size(size)
                .semantics { contentDescription = "Clear" },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BenflixIconImage(icon = BenflixIcon.CLOSE, color = BenflixColors.Muted, size = size * 0.45f)
        }
    }
}

/** A small word button in the panel: "Clear all" (the whole list goes for this profile) and "See all ›" */
@Composable
private fun SmallButton(
    label: String,
    onClick: () -> Unit,
    tv: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = BenflixColors.Muted,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
                pressedContainerColor = BenflixColors.FocusRow,
            ),
        border =
            com.github.damontecres.wholphin.ui.nav
                .focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Text(
            text = label,
            fontSize = if (tv) BenflixType.small else BenflixType.small,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

/** A note from Ben (or "Ben fixed it", or a fact) in full, with a button to clear it */
@Composable
fun HomeMessageDialog(
    row: HomeRow,
    onClear: () -> Unit,
    onGo: (com.github.damontecres.wholphin.ui.nav.Destination) -> Unit,
    onClose: () -> Unit,
) {
    com.github.damontecres.wholphin.ui.components.BasicDialog(onDismissRequest = onClose) {
        val focus = remember { FocusRequester() }
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(20.dp),
        ) {
            Text(
                text = row.kind,
                color = BenflixColors.Text,
                fontSize = BenflixType.lead,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = row.fullText ?: row.title,
                color = BenflixColors.Text,
                fontSize = BenflixType.body,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // A note about one part of Benflix: go there (and clear the note) in one press
                row.goTo?.let { (label, place) ->
                    androidx.tv.material3.Button(
                        onClick = { onGo(place) },
                        modifier = Modifier.focusRequester(focus),
                    ) { Text(label) }
                }
                androidx.tv.material3.Button(
                    onClick = onClear,
                    modifier = if (row.goTo == null) Modifier.focusRequester(focus) else Modifier,
                ) { Text("Got it, clear it") }
                androidx.tv.material3.OutlinedButton(onClick = onClose) { Text("Keep it") }
            }
        }
        LaunchedEffect(Unit) { focus.tryRequestFocus() }
    }
}
