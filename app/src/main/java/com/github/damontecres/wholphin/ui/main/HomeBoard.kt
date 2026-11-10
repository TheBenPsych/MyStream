package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
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
 * Benflix Home, the approved Today board (f3-panel, 2026-11-10).
 *
 * Everything sits on one grid, measured in "u" (on a TV, about 1.45% of the screen's width):
 *   doors  = six 12u squares, 3 by 2, 1.1u apart (so the door block is 38.2u wide and 25.2u tall)
 *   list   = 2.4u to the right of the doors, a soft panel exactly as tall as the doors, 4 rows
 *            (2 rows for each row of doors). A quiet day (2 rows) is one door row tall, level with the bottom row.
 * Phones: the doors 3 by 2 across the screen, the list underneath.
 * Every door uses the same word sizes, picked so the longest name fits on one line and the longest description
 * on two, so all names sit level. List words are picked the same way so nothing is ever cut short.
 */

private const val DOOR = 12f
private const val DOOR_GAP = 1.1f
private const val BLOCK_GAP = 2.4f
private const val BLOCK_HEIGHT = 2 * DOOR + DOOR_GAP
private const val SCREEN_WIDTH = 64.2f

/** The soft dark panel behind the list (a step above the page, below the cards) */
private val PanelColor = Color(0xFF111319)
private val RowLine = Color(0xFF262A34)

@Composable
fun HomeBoard(
    greeting: String,
    weather: String?,
    doors: List<BenflixDoor>,
    rows: List<HomeRow>,
    wide: Boolean,
    labelAlpha: (Int) -> Float,
    listAlpha: () -> Float,
    doorModifier: (BenflixDoor) -> Modifier,
    onDoor: (BenflixDoor) -> Unit,
    onRow: (HomeRow) -> Unit,
    onClear: (HomeRow) -> Unit,
    onClearAll: () -> Unit,
    focusDoors: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // After an X or Clear all, the remote's focus moves to the next row's X (or back to the doors when the list is
    // empty), so it never gets lost on a TV
    val xFocus = remember { List(MAX_HOME_ROWS) { FocusRequester() } }
    var refocus by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(rows) {
        val i = refocus ?: return@LaunchedEffect
        refocus = null
        if (rows.isEmpty()) focusDoors() else xFocus[minOf(i, rows.size - 1)].tryRequestFocus()
    }
    val clearRow: (HomeRow) -> Unit = { row ->
        refocus = rows.indexOf(row).coerceAtLeast(0)
        onClear(row)
    }
    val clearAll: () -> Unit = {
        refocus = 0
        onClearAll()
    }
    if (wide) {
        BoxWithConstraints(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(end = 24.dp, top = 12.dp, bottom = 34.dp),
        ) {
            // Room for the greeting and weather line above the doors
            val greetingRoom = 104.dp
            val u = minOf(maxWidth / SCREEN_WIDTH, (maxHeight - greetingRoom) / BLOCK_HEIGHT).coerceAtLeast(4.dp)
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { Greeting(greeting, weather, tv = true) }
                    // Clear all sits just above the list's right edge (D-pad up from the top row)
                    if (rows.isNotEmpty()) {
                        ClearAllButton(clearAll, tv = true, modifier = Modifier.graphicsLayer { alpha = listAlpha() })
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(u * BLOCK_GAP),
                    modifier = Modifier.fillMaxWidth().height(u * BLOCK_HEIGHT),
                ) {
                    DoorGrid(doors, u, u * DOOR_GAP, labelAlpha, doorModifier, onDoor)
                    ListPanel(
                        rows = rows,
                        u = u,
                        onRow = onRow,
                        onClear = clearRow,
                        xFocus = xFocus,
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
            if (rows.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                PhoneList(rows, onRow, clearRow, xFocus, Modifier.graphicsLayer { alpha = listAlpha() })
                ClearAllButton(
                    clearAll,
                    tv = false,
                    modifier =
                        Modifier
                            .align(Alignment.End)
                            .padding(top = 6.dp)
                            .graphicsLayer { alpha = listAlpha() },
                )
            }
        }
    }
}

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

/** How tall a list row's words are (in pixels) at these sizes, wrapping as much as they need */
private fun TextMeasurer.rowHeight(
    row: HomeRow,
    titleSp: Float,
    detailSp: Float,
    gapPx: Int,
    width: Int,
): Int {
    val title =
        measure(
            text = row.title,
            style = TextStyle(fontSize = titleSp.sp, fontWeight = FontWeight.Bold, lineHeight = (titleSp * 1.2f).sp),
            constraints = Constraints(maxWidth = width),
        ).size.height
    if (row.detail.isBlank()) return title
    val detail =
        measure(
            text = row.detail,
            style = TextStyle(fontSize = detailSp.sp, fontWeight = FontWeight.Medium, lineHeight = (detailSp * 1.3f).sp),
            constraints = Constraints(maxWidth = width),
        ).size.height
    return title + gapPx + detail
}

/** Word sizes shared by every list row, so they look even */
private data class RowWords(
    val title: TextUnit,
    val detail: TextUnit,
)

/** TV, tablets and phones turned sideways: the soft panel beside the doors */
@Composable
private fun ListPanel(
    rows: List<HomeRow>,
    u: Dp,
    onRow: (HomeRow) -> Unit,
    onClear: (HomeRow) -> Unit,
    xFocus: List<FocusRequester>,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) {
        Box(modifier)
        return
    }
    // A quiet day: two rows, one door row tall, level with the bottom row of doors
    val quiet = rows.size <= 2
    val slots = if (quiet) 2 else rows.size
    val inset = u * 0.35f
    BoxWithConstraints(modifier, contentAlignment = Alignment.BottomStart) {
        val panelHeight = if (quiet) u * DOOR else maxHeight
        // The thin lines between rows take 1 dp each
        val rowHeight = (panelHeight - inset * 2 - 1.dp * (slots - 1)) / slots
        val rowPad = u * 0.85f
        val thumbWidth = u * 2.6f
        val clearSize = u * 2.2f
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        // The words' width: the row less its padding, the dot, the two gaps, the picture and the X
        val textWidth =
            with(density) {
                (maxWidth - inset * 2 - rowPad * 2 - u * 0.6f - u * 1.8f - thumbWidth - clearSize - u * 0.3f)
                    .roundToPx()
                    .coerceAtLeast(1)
            }
        val textRoom = with(density) { (rowHeight - u * 1.0f).roundToPx() }
        val words =
            remember(rows, textWidth, textRoom, density.fontScale) {
                val toSp = { d: Dp -> d.value / density.fontScale }
                val title0 = toSp(u * 1.08f).coerceIn(10f, 24f)
                val detail0 = toSp(u * 0.84f).coerceIn(9f, 19f)
                val gapPx = with(density) { (u * 0.15f).roundToPx() }
                var scale = 1f
                while (scale > 0.62f && rows.any { measurer.rowHeight(it, title0 * scale, detail0 * scale, gapPx, textWidth) > textRoom }) {
                    scale -= 0.04f
                }
                RowWords((title0 * scale).sp, (detail0 * scale).sp)
            }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(panelHeight)
                    .clip(RoundedCornerShape(u * 0.9f))
                    .background(PanelColor)
                    .padding(inset),
        ) {
            rows.forEachIndexed { i, row ->
                if (i > 0) RowDivider(Modifier.padding(horizontal = rowPad))
                ListRow(
                    row = row,
                    words = words,
                    dot = u * 0.6f,
                    gap = u * 0.9f,
                    thumbWidth = thumbWidth,
                    pad = rowPad,
                    corner = u * 0.6f,
                    onClick = { onRow(row) },
                    onClear = { onClear(row) },
                    clearSize = clearSize,
                    clearFocus = xFocus[i],
                    modifier = Modifier.fillMaxWidth().height(rowHeight),
                )
            }
        }
    }
}

/** Phones: the list in the same soft panel, under the doors; rows are as tall as their words need */
@Composable
private fun PhoneList(
    rows: List<HomeRow>,
    onRow: (HomeRow) -> Unit,
    onClear: (HomeRow) -> Unit,
    xFocus: List<FocusRequester>,
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
        rows.forEachIndexed { i, row ->
            if (i > 0) RowDivider(Modifier.padding(horizontal = 12.dp))
            ListRow(
                row = row,
                words = RowWords(15.sp, 13.sp),
                dot = 8.dp,
                gap = 12.dp,
                thumbWidth = 40.dp,
                pad = 12.dp,
                corner = 11.dp,
                onClick = { onRow(row) },
                onClear = { onClear(row) },
                clearSize = 36.dp,
                clearFocus = xFocus[i],
                modifier = Modifier.fillMaxWidth(),
                vertical = 11.dp,
            )
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

/** One list row: a dot in its door's colour, the title and words under it (wrapping, never cut), and a small picture */
@Composable
private fun ListRow(
    row: HomeRow,
    words: RowWords,
    dot: Dp,
    gap: Dp,
    thumbWidth: Dp,
    pad: Dp,
    corner: Dp,
    onClick: () -> Unit,
    onClear: () -> Unit,
    clearSize: Dp,
    clearFocus: FocusRequester,
    modifier: Modifier = Modifier,
    vertical: Dp = 0.dp,
) {
    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier.fillMaxSize().padding(horizontal = pad, vertical = vertical),
        ) {
            Box(
                Modifier
                    .size(dot)
                    .background(row.dot, CircleShape),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = row.title,
                    color = BenflixColors.Text,
                    fontSize = words.title,
                    lineHeight = words.title * 1.2f,
                    fontWeight = FontWeight.Bold,
                )
                if (row.detail.isNotBlank()) {
                    Text(
                        text = row.detail,
                        color = BenflixColors.Muted,
                        fontSize = words.detail,
                        lineHeight = words.detail * 1.3f,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Thumb(row, thumbWidth)
        }
    }
    val shape = RoundedCornerShape(corner)
    // Every row can be pressed (a row with nowhere to go opens a pop-up with all its words), and the X beside it
    // clears it (on a TV, press right to reach the X)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min),
    ) {
        RowSurface(onClick, shape, Modifier.weight(1f).fillMaxHeight(), content)
        ClearButton(
            onClear,
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

/** The small poster (or square music cover) on the right; the Benflix squares when there's no picture */
@Composable
private fun Thumb(
    row: HomeRow,
    width: Dp,
) {
    val height = if (row.square) width else width * 1.5f
    val shape = RoundedCornerShape(width * 0.12f)
    if (row.image == null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .width(width)
                    .height(width * 1.5f)
                    .clip(shape)
                    .background(BenflixColors.PanelHigh),
        ) {
            val sq = width * 0.26f
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
                .width(width)
                .height(height)
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

/** "Clear all": the whole list goes away for this profile (new news still shows when it comes) */
@Composable
private fun ClearAllButton(
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
            text = "Clear all",
            fontSize = if (tv) BenflixType.body else BenflixType.small,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
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
                text = row.title,
                color = BenflixColors.Text,
                fontSize = BenflixType.lead,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = row.fullText ?: row.detail,
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
