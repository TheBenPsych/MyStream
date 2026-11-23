package com.github.damontecres.wholphin.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.services.CantFindBlocked
import com.github.damontecres.wholphin.ui.cards.AdultContentBanner
import com.github.damontecres.wholphin.ui.detail.discover.AdultContentFlags
import com.github.damontecres.wholphin.ui.door.POSTER_RATIO
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.util.scrollAsOneBlock

/*
 * Benflix redesign (approved 2026-11-07): the Requests rows use the same plain posters as the doors
 * (TV: just the picture; phone: a dark name strip under it). The top that describes the poster you're on is in
 * RequestLook.kt (Glowfin new look).
 */

/** One row of request posters: a medium white name, then the posters */
@Composable
fun RequestDoorRow(
    title: String,
    items: List<DiscoverItem>,
    isTv: Boolean,
    onClick: (DiscoverItem) -> Unit,
    onLongClick: (DiscoverItem) -> Unit,
    onFocus: (DiscoverItem) -> Unit,
    modifier: Modifier = Modifier,
    cornerNoteFor: (DiscoverItem) -> String? = { null },
    subtitleFor: (DiscoverItem) -> String? = { null },
    ranked: Boolean = false,
    /** Anything after the posters, like "3 more are already on Glowfin" */
    trailing: (androidx.compose.foundation.lazy.LazyListScope.() -> Unit)? = null,
    /** Phones: names under the posters (TV shows the name in the top area) */
    namesOnTv: Boolean = false,
) {
    val first = remember { FocusRequester() }
    Column(modifier = modifier.fillMaxWidth().scrollAsOneBlock()) {
        Text(
            text = title,
            color = BenflixColors.Text,
            fontFamily = com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont,
            fontSize = BenflixType.lead,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = SIDE_ROW, end = SIDE_ROW, bottom = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = SIDE_ROW),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.focusRestorer(first),
        ) {
            itemsIndexed(items, key = { _, it -> it.requestKey() }) { index, item ->
                // Phones have nothing to focus, so details (scores) load as posters come on screen
                if (!isTv) LaunchedEffect(item.requestKey()) { onFocus(item) }
                val poster: @Composable (Modifier) -> Unit = { m ->
                    RequestPoster(
                        item = item,
                        width = if (isTv) 120.dp else 104.dp,
                        showName = !isTv || namesOnTv,
                        subtitle = subtitleFor(item),
                        cornerNote = cornerNoteFor(item),
                        onClick = { onClick(item) },
                        onLongClick = { onLongClick(item) },
                        onFocus = { onFocus(item) },
                        modifier = m.then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                    )
                }
                if (ranked) {
                    RankedPoster(index + 1, if (isTv) 120.dp else 104.dp, poster)
                } else {
                    poster(Modifier)
                }
            }
            trailing?.invoke(this)
        }
    }
}

/** Top 10 (Glowfin T1): the glowing number fully beside the poster, in an equal-width box */
@Composable
private fun RankedPoster(
    rank: Int,
    width: Dp,
    poster: @Composable (Modifier) -> Unit,
) {
    com.github.damontecres.wholphin.ui.components.GlowRanked(rank = rank, posterHeight = width / POSTER_RATIO) {
        poster(Modifier)
    }
}

/** A plain poster like the doors', with a small white word for its request state */
@Composable
fun RequestPoster(
    item: DiscoverItem,
    width: Dp,
    showName: Boolean,
    subtitle: String?,
    cornerNote: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val height = width / POSTER_RATIO
    var failed by remember(item.posterUrl) { mutableStateOf(false) }
    val adultFlags by AdultContentFlags.flagged.collectAsState()
    val cantFindKeys by CantFindBlocked.keys.collectAsState()
    val key = item.requestKey()
    val adult = key in adultFlags
    val cantFind = !adult && key in cantFindKeys
    Column(modifier = modifier.width(width)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            shape = ClickableSurfaceDefaults.shape(POSTER_SHAPE),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = BenflixColors.Raised,
                    focusedContainerColor = BenflixColors.Raised,
                    pressedContainerColor = BenflixColors.Raised,
                ),
            border = doorFocusBorder(POSTER_SHAPE),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier =
                Modifier
                    .size(width, height)
                    .onFocusChanged { if (it.isFocused) onFocus() },
        ) {
            Box(Modifier.fillMaxSize()) {
                if (item.posterUrl != null && !failed) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        onError = { failed = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = item.title.orEmpty(),
                        color = BenflixColors.Text,
                        fontSize = BenflixType.body,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .padding(10.dp),
                    )
                }
                // Glowfin new look: a small coloured dot on covers already asked for (amber waiting, aqua on its
                // way, green here, red couldn't find or missing episodes)
                item.rqDot(cantFind)?.let { dot ->
                    RqCoverDot(dot, Modifier.align(Alignment.TopEnd).padding(6.dp))
                }
                if (adult) {
                    AdultContentBanner(modifier = Modifier.align(Alignment.BottomCenter))
                } else if (cantFind) {
                    CantFindStrip(modifier = Modifier.align(Alignment.BottomCenter))
                }
                if (cornerNote != null) {
                    Text(
                        text = cornerNote,
                        color = BenflixColors.Text,
                        fontSize = BenflixType.tag,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 6.dp, end = 6.dp, bottom = if (adult || cantFind) 26.dp else 6.dp)
                                .background(Color.Black.copy(alpha = .75f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
        if (showName) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .background(BenflixColors.Panel, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Text(
                    text = item.title.orEmpty(),
                    color = BenflixColors.Text,
                    fontSize = BenflixType.tag,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                (subtitle ?: item.releaseDate?.year?.toString())?.let {
                    Text(text = it, color = BenflixColors.Muted, fontSize = BenflixType.tag, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Rows in the approved order; anything else (like Popular) goes after */
fun orderRequestRows(rows: List<FindRow>): List<FindRow> =
    rows.sortedBy { row ->
        val t = row.title
        when {
            t.startsWith("Trending") -> 0
            t.startsWith("Coming soon") -> 1
            t.startsWith("Because") -> 2
            t.startsWith("Finish") -> 3
            t.startsWith("Top rated") -> 4
            else -> 5
        }
    }

private val POSTER_SHAPE = com.github.damontecres.wholphin.ui.theme.BenflixShapes.Card
private val SIDE_ROW = 16.dp
