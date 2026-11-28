package com.github.damontecres.wholphin.ui.discover

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.ui.components.GlowRanked
import com.github.damontecres.wholphin.ui.door.CoverDoorTop
import com.github.damontecres.wholphin.ui.door.CoverTopWords
import com.github.damontecres.wholphin.ui.door.coverTopHeight
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.music.rememberCoverGlow
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont
import com.github.damontecres.wholphin.ui.util.ScrollToTopBringIntoViewSpec
import com.github.damontecres.wholphin.ui.util.scrollAsOneBlock
import kotlinx.coroutines.delay

/*
 * Glowfin new look for Requests > Music and Books (Ben approved 2026-11-22). Like the Music and Books tabs
 * themselves: the page lands straight on a row, and on TV the cover you're on stands at the top right with its own
 * colours glowing behind the page, its name, facts and status on the left. Music covers are square (artists round),
 * books are tall. Small dots on covers already asked for, like Movies and Shows.
 */

/** One cover in a Requests row */
internal class RqCoverCard(
    val key: String,
    val cover: String?,
    val name: String,
    val line: String?,
    val words: CoverTopWords,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
    /** Artists: a round picture */
    val round: Boolean = false,
    /** The small coloured dot at the top right (asked for, on its way, here) */
    val dot: Color? = null,
)

/** One row on the page: covers, or a row of pills (Genres, Bring in a playlist, Your asks) */
internal sealed class RqCoverRow(
    val key: String,
    val title: String,
) {
    class Covers(
        key: String,
        title: String,
        val cards: List<RqCoverCard>,
        val ranked: Boolean = false,
        val note: String? = null,
    ) : RqCoverRow(key, title)

    class Pills(
        key: String,
        title: String,
        val pills: List<RqPill>,
    ) : RqCoverRow(key, title)
}

internal class RqPill(
    val label: String,
    val onClick: () -> Unit,
    val count: Int? = null,
    val on: Boolean = false,
)

/**
 * The rows page. [ratio] is a cover's width for its height (1 for music, books are taller); [top] is how far the
 * header reaches down on TV (the top draws up behind it). [above] sits over the rows (notes, the phone search box).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RequestCoverRows(
    rows: List<RqCoverRow>,
    isTv: Boolean,
    ratio: Float,
    glowFallback: Color,
    top: Dp,
    modifier: Modifier = Modifier,
    loading: String? = null,
    above: @Composable () -> Unit = {},
) {
    var focused by remember { mutableStateOf<RqCoverCard?>(null) }
    var shown by remember { mutableStateOf<RqCoverCard?>(null) }
    LaunchedEffect(focused) {
        val target = focused ?: return@LaunchedEffect
        if (shown != null) delay(COVER_WAIT_MS)
        shown = target
    }
    val firstCard =
        rows
            .firstNotNullOfOrNull { (it as? RqCoverRow.Covers)?.cards?.firstOrNull() }
    val described = shown ?: firstCard
    Column(modifier.fillMaxSize().padding(top = if (isTv) 0.dp else top)) {
        if (isTv && described != null) {
            val glow = rememberCoverGlow(described.cover, glowFallback)
            CoverDoorTop(
                shown = described,
                words = described.words,
                glow = glow,
                height = coverTopHeight(LocalConfiguration.current.screenHeightDp.dp, top),
                overlap = top,
                coverRatio = ratio,
                edge = 16.dp,
            ) { c, m ->
                AsyncImage(
                    model = c.cover,
                    contentDescription = c.name,
                    contentScale = ContentScale.Crop,
                    modifier = m.clip(if (c.round) CircleShape else BenflixShapes.Card),
                )
            }
        } else if (isTv) {
            Box(Modifier.padding(top = top))
        }
        above()
        val defaultSpec = LocalBringIntoViewSpec.current
        val toTop = remember { ScrollToTopBringIntoViewSpec(0f) }
        CompositionLocalProvider(LocalBringIntoViewSpec provides if (isTv) toTop else defaultSpec) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(if (isTv) 18.dp else 14.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 48.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                if (loading != null) {
                    item(key = "rq-loading") {
                        Text(
                            loading,
                            color = BenflixColors.Muted,
                            fontSize = BenflixType.body,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
                items(rows, key = { "rqrow-${it.key}" }) { row ->
                    CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                        when (row) {
                            is RqCoverRow.Covers -> {
                                CoverRowView(row, isTv, ratio, onFocus = { focused = it })
                            }

                            is RqCoverRow.Pills -> {
                                RequestChipRow(row.title) {
                                    items(row.pills, key = { "pill-${row.key}-${it.label}" }) { p ->
                                        RequestsChip(p.label, p.on, p.onClick, count = p.count)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverRowView(
    row: RqCoverRow.Covers,
    isTv: Boolean,
    ratio: Float,
    onFocus: (RqCoverCard) -> Unit,
) {
    val width = if (isTv) (if (ratio >= 1f) 120.dp else 108.dp) else 100.dp
    Column(Modifier.fillMaxWidth().scrollAsOneBlock()) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        ) {
            Text(
                row.title,
                fontFamily = GlowfinDisplayFont,
                fontSize = BenflixType.lead,
                fontWeight = FontWeight.Bold,
                color = BenflixColors.Text,
                maxLines = 1,
            )
            row.note?.let {
                Text(
                    it,
                    fontSize = BenflixType.small,
                    color = BenflixColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            itemsIndexed(row.cards, key = { i, c -> "${row.key}-$i-${c.key}" }) { i, c ->
                if (row.ranked && i < 10) {
                    GlowRanked(rank = i + 1, posterHeight = width / ratio) { RqCover(c, width, ratio, onFocus) }
                } else {
                    RqCover(c, width, ratio, onFocus)
                }
            }
        }
    }
}

@Composable
private fun RqCover(
    c: RqCoverCard,
    width: Dp,
    ratio: Float,
    onFocus: (RqCoverCard) -> Unit,
) {
    val shape = if (c.round) CircleShape else BenflixShapes.Card
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.width(width)) {
        Box {
            Surface(
                onClick = c.onClick,
                onLongClick = c.onLongClick,
                shape = ClickableSurfaceDefaults.shape(shape),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = BenflixColors.Raised,
                        focusedContainerColor = BenflixColors.Raised,
                        pressedContainerColor = BenflixColors.Raised,
                    ),
                border = doorFocusBorder(shape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier =
                    Modifier
                        .size(width, if (c.round) width else width / ratio)
                        .onFocusChanged { if (it.isFocused) onFocus(c) },
            ) {
                if (c.cover != null) {
                    AsyncImage(
                        model = c.cover,
                        contentDescription = c.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            c.name,
                            color = BenflixColors.Text,
                            fontSize = BenflixType.small,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
            c.dot?.let { RqCoverDot(it, Modifier.align(Alignment.TopEnd).padding(6.dp)) }
        }
        Text(
            c.name,
            color = BenflixColors.Text,
            fontSize = BenflixType.small,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (c.round) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        c.line?.let {
            Text(
                it,
                color = BenflixColors.Muted,
                fontSize = BenflixType.tag,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (c.round) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val COVER_WAIT_MS = 450L

/** The cover's dot for a song, album or book: none until it's asked for, green once it's here */
internal fun askDot(
    asked: String?,
    have: Boolean,
    kind: AskKind,
): Color? {
    if (have) return RqTagKind.HERE.dot
    if (asked == null) return null
    return when (pillFromWords(asked, kind).state) {
        AskState.READY -> RqTagKind.HERE.dot
        AskState.GETTING -> RqTagKind.ON_WAY.dot
        AskState.CANT -> RqTagKind.PROBLEM.dot
        else -> RqTagKind.WAITING.dot
    }
}
