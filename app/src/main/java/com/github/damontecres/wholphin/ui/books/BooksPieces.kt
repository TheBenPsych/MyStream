package com.github.damontecres.wholphin.ui.books

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.services.BookTile
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixType

/** Book covers are a little taller than wide (2 by 3), like a paperback */
const val BOOK_RATIO = 2f / 3f

/** The cover picture, or (while it loads, or if there is none) the name on a plain panel */
@Composable
fun BookCoverImage(
    url: String?,
    name: String,
    modifier: Modifier = Modifier,
    audio: Boolean = false,
) {
    var failed by remember(url) { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .clip(BenflixShapes.Card)
                .background(BenflixColors.PanelHigh),
    ) {
        // Redesign 2: no cover = the dark card with the big white name and a lime line; covers fade in
        if (url == null || failed) {
            com.github.damontecres.wholphin.ui.cards.NoPictureCard(
                name = name,
                door = BenflixColors.Books,
                small = true,
            )
        }
        if (url != null && !failed) {
            AsyncImage(
                model =
                    com.github.damontecres.wholphin.ui.cards
                        .rememberFadeIn(url),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * One book in a row or grid: the cover (with a lime bar for how far through, "+2" for new volumes, a
 * headphones mark for audiobooks), then its name and a small line ("Vol 4 of 12", "40%", the author).
 */
@Composable
fun BookCard(
    tile: BookTile,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier.width(width)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            shape = ClickableSurfaceDefaults.shape(BenflixShapes.Card),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    pressedContainerColor = Color.Transparent,
                ),
            border = doorFocusBorder(BenflixShapes.Card),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(BOOK_RATIO)
                    .onFocusChanged { if (it.isFocused) onFocus?.invoke() },
        ) {
            Box(Modifier.fillMaxSize()) {
                BookCoverImage(tile.cover, tile.name, Modifier.fillMaxSize(), audio = tile.isAudio)
                if (tile.notHere) {
                    Box(Modifier.fillMaxSize().background(BenflixColors.Dim))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                ) {
                    when {
                        tile.newCount > 0 -> BookTag("+${tile.newCount}", BenflixColors.Books, BenflixColors.OnBooks)
                        tile.isNew -> BookTag("NEW", BenflixColors.Text, BenflixColors.Tray)
                    }
                    if (tile.sent) BookTag("ON PHONE", BenflixColors.Books, BenflixColors.OnBooks)
                }
                if (tile.isAudio) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .background(BenflixColors.Dim, RoundedCornerShape(50))
                                .padding(5.dp),
                    ) {
                        BenflixIconImage(BenflixIcon.HEADPHONES, color = BenflixColors.Text, size = 14.dp)
                    }
                }
                if (tile.status == "done") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .background(BenflixColors.Books, RoundedCornerShape(50))
                                .padding(4.dp),
                    ) {
                        BenflixIconImage(BenflixIcon.CHECK, color = BenflixColors.OnBooks, size = 14.dp)
                    }
                }
                if (tile.status == "reading" && tile.progress > 0.005f) {
                    ProgressStrip(tile.progress, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        // Redesign 2 (rc-family): on TV the name is on the cover, so none under it. Phones get the name in up
        // to 2 lines, then the author or "Vol 4 of 12" in grey.
        val isTv =
            com.github.damontecres.wholphin.ui.touch
                .rememberIsTv()
        if (!isTv) {
            com.github.damontecres.wholphin.ui.cards.CardNameUnder(
                name = tile.name,
                line = if (tile.notHere) "Not on Benflix yet" else tile.line,
            )
        }
    }
}

/** The thin lime bar along the bottom of a cover */
@Composable
fun ProgressStrip(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(5.dp)
            .background(Color(0x99000000)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0.03f, 1f))
                .height(5.dp)
                .background(BenflixColors.Books),
        )
    }
}

/** A small tag on a cover, like NEW or +2 */
@Composable
fun BookTag(
    text: String,
    bg: Color,
    fg: Color,
) {
    Text(
        text = text,
        color = fg,
        fontSize = BenflixType.tag,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.06.em,
        modifier =
            Modifier
                .background(bg, BenflixShapes.Small)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A chip: lime when picked, dark otherwise (Not started / In progress / Finished, and the phone's menu) */
@Composable
fun BookChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = BenflixShapes.Pill
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (selected) BenflixColors.Books else BenflixColors.Raised,
                contentColor = if (selected) BenflixColors.OnBooks else BenflixColors.Text,
                focusedContainerColor = if (selected) BenflixColors.Books else BenflixColors.FocusRow,
                focusedContentColor = if (selected) BenflixColors.OnBooks else BenflixColors.Text,
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.5.dp, BenflixColors.Text), shape = shape),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Text(
            text = label,
            fontSize = BenflixType.small,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

/** "4h 12m" */
fun hoursMinutes(seconds: Int): String {
    val m = (seconds + 30) / 60
    return when {
        m < 60 -> "${m}m"
        m % 60 == 0 -> "${m / 60}h"
        else -> "${m / 60}h ${m % 60}m"
    }
}

/** "1:02:05" or "12:05" for the audiobook player */
fun clockTime(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
