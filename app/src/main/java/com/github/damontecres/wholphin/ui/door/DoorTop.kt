package com.github.damontecres.wholphin.ui.door

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.rememberLogoUrl
import com.github.damontecres.wholphin.ui.detail.title.BRIGHT_ART
import com.github.damontecres.wholphin.ui.detail.title.LocalTitleExtras
import com.github.damontecres.wholphin.ui.detail.title.ScorePill
import com.github.damontecres.wholphin.ui.detail.title.TitleBox
import com.github.damontecres.wholphin.ui.detail.title.TitleExtras
import com.github.damontecres.wholphin.ui.detail.title.rememberImageLight
import com.github.damontecres.wholphin.ui.detail.title.rememberScoreParts
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShade
import com.github.damontecres.wholphin.ui.theme.BenflixType
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.extensions.ticks
import java.time.LocalDateTime

/*
 * Glowfin new look, part 2 (Ben approved 2026-11-22): Home, Movies, Shows and Anime land straight on a row.
 * The top only describes the card you're on, in the same look as the details pages (part 1): the art fills
 * the whole screen behind everything, a fixed title box (logo or name), a short facts line, a small tag,
 * a 2-line story, and the score pill at the top right. No buttons and nothing rotating.
 * Made for the TCL TVs: one picture loaded at the screen's size, plain painted shades, no blur.
 */

/**
 * The art of the card you're on, filling the whole page. Only a soft shade on the left for the words and a
 * fade at the bottom for the rows; the rest stays bright. Very bright art is turned down a little.
 */
@Composable
internal fun DoorArt(
    item: BaseItem?,
    pageHeight: Dp,
    onBright: (Boolean) -> Unit = {},
) {
    val imageUrlService = LocalImageUrlService.current
    val density = LocalDensity.current
    // Loaded at the screen's own height (TV size), never bigger
    val url =
        remember(item?.id, pageHeight) {
            item?.let { imageUrlService.getItemImageUrl(it, ImageType.BACKDROP, fillHeight = with(density) { pageHeight.roundToPx() }) }
        }
    // Another episode of the same show has the same picture, so it just stays. A new picture: the old one
    // fades away, a short wait, then the new one fades in (no flashing while you move along a row).
    var shownUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(url) {
        if (url == shownUrl) return@LaunchedEffect
        if (shownUrl != null) {
            shownUrl = null
            delay(ART_GAP_MS)
        }
        shownUrl = url
    }
    val light = rememberImageLight(shownUrl)
    val bright = light != null && light > BRIGHT_ART
    LaunchedEffect(bright) { onBright(bright) }
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = shownUrl, animationSpec = tween(ART_FADE_MS), label = "doorArt") { picture ->
            if (picture != null) {
                AsyncImage(
                    model = picture,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (bright) Box(Modifier.fillMaxSize().background(BenflixColors.Tray.copy(alpha = .2f)))
        Box(Modifier.fillMaxSize().background(LEFT_SHADE))
        Box(Modifier.fillMaxSize().background(BenflixShade.bottom(.42f)))
    }
}

/**
 * The words about the card you're on: the title box (the logo when it's a good shape, else the name),
 * the facts line, a small aqua tag (time left, the episode, new on Glowfin) and the 2-line story.
 */
@Composable
internal fun DoorInfo(
    item: BaseItem,
    easy: Boolean,
    brightArt: Boolean,
    modifier: Modifier = Modifier,
) {
    val logoUrl = rememberLogoUrl(item)
    val extras = remember(logoUrl) { TitleExtras(logoUrl = logoUrl) }
    val edge = if (brightArt) WORD_EDGE else null
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        CompositionLocalProvider(LocalTitleExtras provides extras) {
            TitleBox(title = item.title.orEmpty(), wide = true, height = if (easy) 72.dp else 60.dp, width = 300.dp)
        }
        val facts = remember(item) { doorFacts(item) }
        if (facts.isNotEmpty()) {
            Text(
                text = facts,
                color = BenflixColors.Text,
                fontSize = BenflixType.body,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(shadow = edge ?: SOFT_EDGE),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val tag = remember(item) { doorTag(item) }
        if (tag != null) DoorTag(tag)
        val story = item.data.overview
        if (!easy && !story.isNullOrBlank()) {
            Text(
                text = story,
                color = BenflixColors.Text.copy(alpha = .92f),
                fontSize = BenflixType.small,
                lineHeight = BenflixType.small * 1.35f,
                style = TextStyle(shadow = edge ?: SOFT_EDGE),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 440.dp),
            )
        }
    }
}

/** The score pill for the card you're on (age rating and up to 3 scores). Episodes just show the rating. */
@Composable
internal fun DoorScorePill(
    item: BaseItem,
    modifier: Modifier = Modifier,
) {
    val rating = item.data.officialRating
    if (item.type == BaseItemKind.EPISODE) {
        ScorePill(rating = rating, scores = emptyList(), modifier = modifier)
    } else {
        ScorePill(rating = rating, scores = rememberScoreParts(item), modifier = modifier)
    }
}

/** The small aqua tag under the facts line: a dot and a few words */
@Composable
private fun DoorTag(text: String) {
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .background(BenflixColors.Tray.copy(alpha = .55f), shape)
                .background(TAG_AQUA.copy(alpha = .16f), shape)
                .border(1.dp, TAG_AQUA.copy(alpha = .5f), shape)
                .padding(horizontal = 9.dp, vertical = 2.dp),
    ) {
        Box(Modifier.size(6.dp).background(TAG_AQUA, CircleShape))
        Text(
            text = text,
            color = TAG_TEXT,
            fontSize = BenflixType.tag,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "2024 · 2h 46m · Science fiction" (shows: "2019 · 3 seasons · Drama"; episodes: "S2 E5 · Episode name") */
internal fun doorFacts(item: BaseItem): String {
    val d = item.data
    val parts =
        if (item.type == BaseItemKind.EPISODE) {
            listOfNotNull(yearText(item), d.name)
        } else {
            listOfNotNull(
                d.productionYear?.toString(),
                d.runTimeTicks
                    ?.ticks
                    ?.takeIf { item.type == BaseItemKind.MOVIE }
                    ?.let(::lengthText),
                d.childCount?.takeIf { item.type == BaseItemKind.SERIES && it > 0 }?.let { if (it == 1) "1 season" else "$it seasons" },
                d.genres?.firstOrNull(),
            )
        }
    return parts.filter { it.isNotBlank() }.joinToString(" · ")
}

/** The tag: how long is left on something you started, else "New on Glowfin" for things added this week */
internal fun doorTag(item: BaseItem): String? {
    if (item.progress() != null) {
        doorTimeLeft(item)?.let { return it }
    }
    val added = item.data.dateLastMediaAdded ?: item.data.dateCreated
    if (!item.played && added != null && added.isAfter(LocalDateTime.now().minusDays(NEW_DAYS))) {
        return if (item.type == BaseItemKind.SERIES) "New episodes on Glowfin" else "New on Glowfin"
    }
    return null
}

/** "1 h 12 min left" or "40 min left" */
private fun doorTimeLeft(item: BaseItem): String? {
    val total = item.data.runTimeTicks?.ticks ?: return null
    val minutes = (total - item.playbackPosition).inWholeMinutes
    return when {
        minutes <= 0 -> null
        minutes >= 60 -> "${minutes / 60} h ${minutes % 60} min left"
        else -> "$minutes min left"
    }
}

/** The soft shade on the left, only as wide as the words (the right side of the art stays bright) */
private val LEFT_SHADE =
    Brush.horizontalGradient(
        0f to BenflixColors.Tray.copy(alpha = .8f),
        .35f to BenflixColors.Tray.copy(alpha = .45f),
        .62f to Color.Transparent,
    )

/** A faint edge under the words so they stay clear on any art (stronger on very bright art) */
private val SOFT_EDGE = Shadow(color = Color.Black.copy(alpha = .5f), offset = Offset(0f, 1.5f), blurRadius = 4f)
private val WORD_EDGE = Shadow(color = Color.Black.copy(alpha = .7f), offset = Offset(0f, 1.5f), blurRadius = 4f)

private val TAG_AQUA = Color(0xFF3FE0E8)
private val TAG_TEXT = Color(0xFFC9FBFF)
private const val NEW_DAYS = 7L
private const val ART_FADE_MS = 400
private const val ART_GAP_MS = 150L
