package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** What every row does, handed down to each one */
private class RowLook(
    val onRow: (HomeRow) -> Unit,
    val onClear: (HomeRow) -> Unit,
    val onFocus: () -> Unit,
    val lite: Boolean,
)

private fun BenflixDoor.icon(): BenflixIcon =
    when (this) {
        BenflixDoor.MOVIES -> BenflixIcon.FILM
        BenflixDoor.SHOWS -> BenflixIcon.TV
        BenflixDoor.LIVE_TV -> BenflixIcon.LIVE
        BenflixDoor.GAMES -> BenflixIcon.PAD
        BenflixDoor.MUSIC -> BenflixIcon.NOTE
        BenflixDoor.BOOKS -> BenflixIcon.BOOK
        BenflixDoor.ANIME -> BenflixIcon.STAR
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

/** See all: everything new in the side panel (TV and web) or a sheet from the bottom (phone), with Clear all */
@Composable
internal fun SeeAllPanel(
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

/** The small coloured line saying what a news row is ("New episode · S3 E12") */
@Composable
private fun KindLine(
    row: HomeRow,
    words: RowWords,
) {
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
            Box {
                Thumb(row, thumb.coerceAtLeast(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                KindLine(row, words)
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
