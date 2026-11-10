package com.github.damontecres.wholphin.ui.discover

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.services.CantFindBlocked
import com.github.damontecres.wholphin.ui.cards.AdultContentBanner
import com.github.damontecres.wholphin.ui.detail.discover.AdultContentFlags
import com.github.damontecres.wholphin.ui.door.POSTER_RATIO
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShade
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.util.scrollAsOneBlock
import kotlinx.coroutines.delay

/*
 * Benflix redesign (approved 2026-11-07): the Requests page looks like the Movies door. A top area
 * describes the poster you're on with its picture fading in from the right, and the rows use the
 * same plain posters (TV: just the picture; phone: a dark name strip under it).
 */

/** The words on a poster's top-left pill, or null for nothing */
private fun DiscoverItem.statusWord(): String? =
    when (availability) {
        SeerrAvailability.PENDING, SeerrAvailability.PROCESSING -> "ASKED FOR"
        SeerrAvailability.PARTIALLY_AVAILABLE -> "SOME ON BENFLIX"
        SeerrAvailability.AVAILABLE -> "ON BENFLIX"
        else -> null
    }

/** The top area, following [focused] once you've stopped on a poster for half a second */
@Composable
fun DelayedRequestPreview(
    focused: State<DiscoverItem?>,
    fallback: DiscoverItem?,
    scores: Map<String, String>,
    height: Dp,
) {
    var shown by remember { mutableStateOf<DiscoverItem?>(null) }
    val target = focused.value
    LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        if (shown != null) delay(PREVIEW_DELAY_MS)
        shown = target
    }
    val item = shown ?: fallback
    RequestPreview(
        item = item,
        score = item?.let { scores[it.requestKey()] ?: it.score?.let { s -> "★ ${"%.1f".format(s)}" } },
        height = height,
    )
}

/** The top area: the focused title's picture on the right, its name, year, score and story on the left */
@Composable
fun RequestPreview(
    item: DiscoverItem?,
    score: String?,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(PICTURE_WIDTH)
                .fillMaxHeight(),
        ) {
            Crossfade(targetState = item?.backDropUrl, animationSpec = tween(PICTURE_FADE_MS), label = "requestPicture") { url ->
                if (url != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        BenflixShade.topAreaSide,
                    ),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(BenflixShade.bottom(0.5f)),
            )
        }
        if (item != null) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(horizontal = SIDE_ROW)
                        .widthIn(max = 560.dp),
            ) {
                Text(
                    text = item.title.orEmpty(),
                    color = BenflixColors.Text,
                    fontSize = BenflixType.pageTitle,
                    lineHeight = BenflixType.pageTitle * 1.15f,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta =
                    listOfNotNull(
                        item.releaseDate?.year?.toString(),
                        when (item.type) {
                            SeerrItemType.MOVIE -> "Movie"
                            SeerrItemType.TV -> "Show"
                            else -> null
                        },
                        score,
                        item.statusWord()?.lowercase()?.replaceFirstChar { it.uppercase() },
                    ).joinToString("  ·  ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        color = BenflixColors.Soft,
                        fontSize = BenflixType.body,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                val story = item.overview
                if (!story.isNullOrBlank()) {
                    Text(
                        text = story,
                        color = BenflixColors.Soft,
                        fontSize = BenflixType.small,
                        lineHeight = BenflixType.small * 1.35f,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

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
) {
    val first = remember { FocusRequester() }
    Column(modifier = modifier.fillMaxWidth().scrollAsOneBlock()) {
        Text(
            text = title,
            color = BenflixColors.Text,
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
                        showName = !isTv,
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
        }
    }
}

/** Top 10: a tall outlined number tucked half behind the poster (like the Books page and the doors) */
@Composable
private fun RankedPoster(
    rank: Int,
    width: Dp,
    poster: @Composable (Modifier) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val posterHeight = width / POSTER_RATIO
    val numberHeight = posterHeight * 0.8f
    val numberSize = with(LocalDensity.current) { numberHeight.toSp() }
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = rank.toString(),
            style =
                TextStyle(
                    fontSize = numberSize,
                    fontFamily = BenflixTitleFont,
                    lineHeight = numberSize,
                    letterSpacing = (-1).sp,
                    drawStyle = Stroke(width = 5f, join = StrokeJoin.Round),
                ),
            color = if (focused) BenflixColors.Text else Color(0xFF5A5E6A),
            maxLines = 1,
            modifier = Modifier.offset(y = posterHeight * 0.2f),
        )
        Box(Modifier.offset(x = -width * 0.18f).onFocusChanged { focused = it.hasFocus }) {
            poster(Modifier)
        }
    }
}

/**
 * Benflix (compact Requests, 2026-11-10): one small line about the poster you're on, instead of the big
 * picture area. Only this line reads [focused], so moving along a row doesn't redraw the page.
 */
@Composable
fun FocusedRequestLine(
    focused: State<DiscoverItem?>,
    fallback: DiscoverItem?,
    scores: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val item = focused.value ?: fallback
    val score = item?.let { scores[it.requestKey()] ?: it.score?.let { s -> "★ ${"%.1f".format(s)}" } }
    val rest =
        listOfNotNull(
            item?.releaseDate?.year?.toString(),
            score,
            item?.statusWord()?.lowercase()?.replaceFirstChar { it.uppercase() },
            item?.overview?.takeIf { it.isNotBlank() },
        ).joinToString("  ·  ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().height(22.dp).padding(horizontal = SIDE_ROW),
    ) {
        if (item != null) {
            Text(
                text = item.title.orEmpty(),
                color = BenflixColors.Text,
                fontSize = BenflixType.body,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 360.dp),
            )
            if (rest.isNotEmpty()) {
                Text(
                    text = "  ·  $rest",
                    color = BenflixColors.Soft,
                    fontSize = BenflixType.small,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
                item.statusWord()?.let { word ->
                    Pill(word, Modifier.align(Alignment.TopStart).padding(6.dp))
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

@Composable
private fun Pill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = BenflixColors.Tray,
        fontSize = BenflixType.tag,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        modifier =
            modifier
                .background(BenflixColors.Text, RoundedCornerShape(6.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
    )
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
private const val PICTURE_WIDTH = 0.62f
private const val PICTURE_FADE_MS = 450
private const val PREVIEW_DELAY_MS = 500L
