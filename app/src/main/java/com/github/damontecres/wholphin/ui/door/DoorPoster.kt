package com.github.damontecres.wholphin.ui.door

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.cards.doorColor
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.components.PosterHoldMenu
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixTitleFont
import com.github.damontecres.wholphin.ui.theme.BenflixType
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import java.time.LocalDateTime

/** Posters are 2:3 */
const val POSTER_RATIO = 2f / 3f

private val PosterShape = BenflixShapes.Card

/** The redesign's focus: a white outline only, no growing or dimming */
@Composable
fun doorFocusBorder(shape: androidx.compose.ui.graphics.Shape = PosterShape) =
    ClickableSurfaceDefaults.border(
        focusedBorder = Border(border = BorderStroke(3.dp, BenflixColors.Text), inset = 0.dp, shape = shape),
    )

/** How far through it is, 0 to 1 (null when not started or finished) */
fun BaseItem.progress(): Float? {
    // A watched film being watched again still shows how far through it is
    if (played && (data.userData?.playbackPositionTicks ?: 0L) <= 0L) return null
    val pct = data.userData?.playedPercentage ?: return null
    return (pct / 100.0).toFloat().takeIf { it > 0.01f && it < 0.99f }
}

/**
 * The white NEW pill: added in the last week and not watched yet. Shows with several new episodes
 * say how many ("3 NEW", "9+ NEW").
 */
fun BaseItem.newLabel(): String? {
    if (played) return null
    val weekAgo = LocalDateTime.now().minusDays(NEW_DAYS)
    return when (type) {
        BaseItemKind.SERIES -> {
            val added = data.dateLastMediaAdded ?: data.dateCreated ?: return null
            if (!added.isAfter(weekAgo)) return null
            val created = data.dateCreated
            if (created != null && created.isAfter(weekAgo)) return "NEW"
            val unplayed = data.userData?.unplayedItemCount ?: 0
            when {
                unplayed <= 0 -> null
                unplayed == 1 -> "NEW"
                unplayed > 9 -> "9+ NEW"
                else -> "$unplayed NEW"
            }
        }

        else -> {
            data.dateCreated?.takeIf { it.isAfter(weekAgo) }?.let { "NEW" }
        }
    }
}

private const val NEW_DAYS = 7L

/**
 * One poster (approved pc-clean + bd-few): on TV just the picture, nothing drawn over it except a
 * progress line, a small white tick when watched, and a white NEW pill. Phones add a dark strip
 * with the name and year under it. No picture = a dark card with the name in big white letters.
 */
@Composable
fun DoorPoster(
    item: BaseItem,
    width: Dp,
    showName: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
) {
    val height = width / POSTER_RATIO
    val imageUrlService = LocalImageUrlService.current
    val density = LocalDensity.current
    val url =
        remember(item.id, height) {
            imageUrlService.getItemImageUrl(
                item,
                ImageType.PRIMARY,
                fillHeight = with(density) { height.roundToPx() },
                useSeriesForPrimary = true,
            )
        }
    val progress = remember(item) { item.progress() }
    val newLabel = remember(item) { item.newLabel() }
    // Benflix: holding a poster (finger on phones, OK on TV) opens its menu, unless the page has its own
    var timesHeld by remember(item.id) { mutableIntStateOf(0) }
    if (onLongClick == null && timesHeld > 0) PosterHoldMenu(item, timesHeld)
    Column(modifier = modifier.width(width)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick ?: { timesHeld += 1 },
            shape = ClickableSurfaceDefaults.shape(PosterShape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = BenflixColors.Raised,
                    focusedContainerColor = BenflixColors.Raised,
                    pressedContainerColor = BenflixColors.Raised,
                ),
            border = doorFocusBorder(),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier =
                Modifier
                    .size(width, height)
                    .onFocusChanged { if (it.isFocused) onFocus?.invoke() },
        ) {
            Box(Modifier.fillMaxSize()) {
                // Redesign 2 (rc-p-load, rc-p-noart): dark first, the poster fades in; no poster = big white name
                com.github.damontecres.wholphin.ui.cards.CardPicture(
                    model = url,
                    name = item.title.orEmpty(),
                    door = item.doorColor(),
                    small = width <= 90.dp,
                    modifier = Modifier.fillMaxSize(),
                )
                if (newLabel != null) {
                    Text(
                        text = newLabel,
                        color = BenflixColors.Tray,
                        fontSize = BenflixType.tag,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .background(BenflixColors.Text, RoundedCornerShape(6.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                if (item.played && progress == null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(18.dp)
                                .background(BenflixColors.Text, CircleShape),
                    ) {
                        BenflixIconImage(BenflixIcon.CHECK, color = BenflixColors.Tray, size = 13.dp)
                    }
                }
                if (progress != null) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x99000000)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .height(4.dp)
                                .background(BenflixColors.Text),
                        )
                    }
                }
            }
        }
        if (showName) {
            // Redesign 2 (rc-p-phone): the name in up to 2 lines, then the year or episode in grey
            com.github.damontecres.wholphin.ui.cards.CardNameUnder(
                name = item.title.orEmpty(),
                line = yearText(item),
                strip = true,
            )
        }
    }
}

/** "2023", or for an episode "S2 E5" */
fun yearText(item: BaseItem): String? =
    when (item.type) {
        BaseItemKind.EPISODE -> {
            val s = item.data.parentIndexNumber
            val e = item.data.indexNumber
            if (s != null && e != null) "S$s E$e" else null
        }

        BaseItemKind.SERIES -> {
            item.data.productionYear?.toString()
        }

        else -> {
            item.data.productionYear?.toString()
        }
    }

/**
 * A Top 10 poster (approved t2-today): today's tall outlined number tucked half behind the poster.
 * The number turns white while you're on it. Numbers are spaced by their ink, so 9 to 10 looks the
 * same as 1 to 2.
 */
@Composable
fun DoorTop10Poster(
    rank: Int,
    item: BaseItem,
    width: Dp,
    showName: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val nameStrip =
        com.github.damontecres.wholphin.ui.cards
            .cardNameHeight(strip = true)
    val posterHeight = width / POSTER_RATIO
    val numberHeight = posterHeight * 0.92f
    val numberSize = with(LocalDensity.current) { numberHeight.toSp() }
    // Bebas Neue's "1" has wide side space: pull numbers starting with 1 in a little
    val pull = if (rank.toString().startsWith("1")) -numberHeight * 0.08f else 0.dp
    Row(verticalAlignment = Alignment.Bottom, modifier = modifier) {
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
            color = if (focused) BenflixColors.Text else TOP10_GREY,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .offset(x = pull, y = numberHeight * 0.07f)
                    .padding(bottom = if (showName) nameStrip else 0.dp),
        )
        DoorPoster(
            item = item,
            width = width,
            showName = showName,
            onClick = onClick,
            onFocus = onFocus,
            modifier =
                Modifier
                    .offset(x = pull - width * 0.24f)
                    .onFocusChanged { focused = it.hasFocus },
        )
    }
}

private val TOP10_GREY = Color(0xFF5A5E6A)

/** The tile at the end of a row that opens the whole list */
@Composable
fun SeeAllTile(
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(PosterShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.Raised,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border = doorFocusBorder(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.size(width, width / POSTER_RATIO),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            BenflixIconImage(BenflixIcon.ARROW, color = BenflixColors.Text, size = 26.dp)
            Text(
                text = "See all",
                color = BenflixColors.Text,
                fontSize = BenflixType.body,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** A dark "Browse by" tile with its name (approved gb-tiles) */
@Composable
fun BrowseTile(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    big: Boolean = false,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.Raised,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.height(if (big) 96.dp else 76.dp),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                text = title,
                color = BenflixColors.Text,
                fontSize = if (big) BenflixType.rowName else BenflixType.lead,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
