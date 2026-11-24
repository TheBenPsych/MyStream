package com.github.damontecres.wholphin.ui.door

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.draw.shadow
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
import com.github.damontecres.wholphin.ui.detail.title.BRIGHT_ART
import com.github.damontecres.wholphin.ui.detail.title.LocalTitleExtras
import com.github.damontecres.wholphin.ui.detail.title.TitleBox
import com.github.damontecres.wholphin.ui.detail.title.TitleExtras
import com.github.damontecres.wholphin.ui.detail.title.rememberImageLight
import com.github.damontecres.wholphin.ui.detail.title.ScoreIcons
import com.github.damontecres.wholphin.ui.detail.title.rememberScoreParts
import com.github.damontecres.wholphin.ui.music.CoverGlow
import com.github.damontecres.wholphin.ui.music.CoverGlowColors
import com.github.damontecres.wholphin.ui.nav.stayPut
import com.github.damontecres.wholphin.ui.nav.underRail
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShade
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
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
    DoorArtUrl(url = url, onBright = onBright)
}

/** [DoorArt] for a picture that isn't a library title (Search's Ask for it titles) */
@Composable
internal fun DoorArtUrl(
    url: String?,
    onBright: (Boolean) -> Unit = {},
) {
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
    // Side menu A (Ben 2026-11-24): the art stays put while the open menu pushes the words and rows right
    Box(Modifier.fillMaxSize().underRail().stayPut()) {
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
    // Ben 2026-11-23 (logo plan): browsing tops always show the name in white text, never the logo (it flickered
    // while the app decided if the logo was a good shape); the details pages show the full logo
    val edge = if (brightArt) WORD_EDGE else null
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        CompositionLocalProvider(LocalTitleExtras provides NO_LOGO) {
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
        // Ben 2026-11-24: the age rating and scores sit as bare icons right under the year and time
        if (!easy) DoorScoreIcons(item = item, modifier = Modifier.padding(top = 2.dp))
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

/** The same scores as bare icons in a row, for under the facts line (episodes use their show's scores) */
@Composable
internal fun DoorScoreIcons(
    item: BaseItem,
    modifier: Modifier = Modifier,
) {
    if (item.type == BaseItemKind.EPISODE) {
        val series = rememberSeriesOf(item)
        val rating = item.data.officialRating ?: series?.data?.officialRating
        if (series == null) {
            ScoreIcons(rating = rating, scores = emptyList(), modifier = modifier)
        } else {
            ScoreIcons(rating = rating, scores = rememberScoreParts(series), modifier = modifier)
        }
    } else {
        ScoreIcons(rating = item.data.officialRating, scores = rememberScoreParts(item), modifier = modifier)
    }
}

/** The small aqua tag under the facts line: a dot and a few words */
@Composable
internal fun DoorTag(
    text: String,
    /** Search: yellow for "Not on Glowfin yet" (things to ask for) */
    yellow: Boolean = false,
) {
    val shape = RoundedCornerShape(50)
    val dot = if (yellow) TAG_YELLOW else TAG_AQUA
    val words = if (yellow) TAG_YELLOW_TEXT else TAG_TEXT
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .background(BenflixColors.Tray.copy(alpha = .55f), shape)
                .background(dot.copy(alpha = .16f), shape)
                .border(1.dp, dot.copy(alpha = .5f), shape)
                .padding(horizontal = 9.dp, vertical = 2.dp),
    ) {
        Box(Modifier.size(6.dp).background(dot, CircleShape))
        Text(
            text = text,
            color = words,
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

/**
 * The shade on the left, only as wide as the words (the right side of the art stays bright). It starts under the side
 * menu at the screen's edge, so the menu needs no dark bar of its own. Glowfin Tint (Ben 2026-11-23, option C):
 * Glowfin's deep violet melting into navy instead of plain black, deep behind the menu and the words.
 */
private val LEFT_SHADE =
    Brush.horizontalGradient(
        0f to Color(22, 10, 58).copy(alpha = .96f),
        .18f to Color(20, 14, 66).copy(alpha = .88f),
        .38f to Color(14, 30, 78).copy(alpha = .62f),
        .56f to Color(10, 34, 80).copy(alpha = .28f),
        .72f to Color.Transparent,
    )

/** A faint edge under the words so they stay clear on any art (stronger on very bright art) */
private val SOFT_EDGE = Shadow(color = Color.Black.copy(alpha = .5f), offset = Offset(0f, 1.5f), blurRadius = 4f)
private val WORD_EDGE = Shadow(color = Color.Black.copy(alpha = .7f), offset = Offset(0f, 1.5f), blurRadius = 4f)

private val TAG_AQUA = Color(0xFF3FE0E8)
private val TAG_TEXT = Color(0xFFC9FBFF)
private val TAG_YELLOW = Color(0xFFF2B33D)
private val TAG_YELLOW_TEXT = Color(0xFFFFE7B5)
private const val NEW_DAYS = 7L
private const val ART_FADE_MS = 400
private const val ART_GAP_MS = 150L

/*
 * Glowfin new look, part 3 (Ben approved 2026-11-22): Music, Books and Games land straight on a row too. Their
 * covers are square or tall, so instead of art filling the screen the cover of the card you're on stands sharp at
 * the top right, with its own colours glowing softly behind the page. The words on the left are the same as Home and
 * Movies (part 2): a fixed title box, a "by" line, a short facts line, the aqua tag and a 2-line story.
 * Made for the TCL TVs: one small cover, two painted colour fades, no blur.
 */

/** What the cover top says about the card you're on */
internal data class CoverTopWords(
    val title: String,
    /** The artist or author, under the name */
    val by: String? = null,
    val facts: String = "",
    val tag: String? = null,
    val story: String? = null,
    /** One more small line (a game's top 3 scores) */
    val extra: String? = null,
)

/** How tall the cover top is (counting the header it sits under): about half the screen, like Home and Movies */
internal fun coverTopHeight(
    screenHeight: Dp,
    overlap: Dp,
): Dp = (screenHeight * TOP_SHARE).coerceIn(overlap + 180.dp, overlap + 230.dp)

/**
 * The top of the Music, Books and Games For you tabs on TV and wide screens. [shown] is the card you're on (it should
 * already wait until you stop moving); [cover] draws its cover, [coverRatio] is the cover's width for its height.
 */
@Composable
internal fun <T> CoverDoorTop(
    shown: T,
    words: CoverTopWords,
    glow: CoverGlowColors,
    height: Dp,
    overlap: Dp,
    coverRatio: Float,
    modifier: Modifier = Modifier,
    edge: Dp = BenflixSpace.tvEdge,
    /** Ben 2026-11-23: Books shows no cover at the top right (the same book is right there in the row) */
    showCover: Boolean = true,
    cover: @Composable (T, Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth().height(height)) {
        // The glow reaches up under the header and tabs, and a little way down behind the first row
        CoverGlow(
            glow,
            Modifier
                .fillMaxWidth()
                .underRail()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .height(height * GLOW_REACH),
        )
        val coverHeight = (height - overlap - COVER_TOP - COVER_BOTTOM).coerceAtLeast(60.dp)
        val coverWidth = if (showCover) coverHeight * coverRatio else 0.dp
        // The cover on the right, whole and sharp, like an album standing on a shelf
        if (showCover) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = overlap + COVER_TOP, end = edge)
                    .height(coverHeight)
                    .aspectRatio(coverRatio)
                    .shadow(18.dp, BenflixShapes.Card),
            ) {
                Crossfade(targetState = shown, animationSpec = tween(COVER_FADE_MS), label = "coverTop") { t ->
                    cover(t, Modifier.fillMaxSize())
                }
            }
        }
        val room = (maxWidth - coverWidth - edge * 2 - 32.dp).coerceAtLeast(200.dp)
        CoverWords(
            words,
            Modifier
                .align(Alignment.TopStart)
                .padding(start = edge, top = overlap + 2.dp)
                .width(room.coerceAtMost(560.dp)),
        )
    }
}

@Composable
private fun CoverWords(
    words: CoverTopWords,
    modifier: Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        CompositionLocalProvider(LocalTitleExtras provides NO_LOGO) {
            TitleBox(title = words.title, wide = true, height = 60.dp, width = 320.dp)
        }
        words.by?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                color = BenflixColors.Soft,
                fontSize = BenflixType.body,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(shadow = SOFT_EDGE),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (words.facts.isNotBlank()) {
            Text(
                text = words.facts,
                color = BenflixColors.Text,
                fontSize = BenflixType.body,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(shadow = SOFT_EDGE),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        words.tag?.takeIf { it.isNotBlank() }?.let { DoorTag(it) }
        words.story?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                color = BenflixColors.Text.copy(alpha = .92f),
                fontSize = BenflixType.small,
                lineHeight = BenflixType.small * 1.35f,
                style = TextStyle(shadow = SOFT_EDGE),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        words.extra?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                color = BenflixColors.Soft,
                fontSize = BenflixType.small,
                fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = SOFT_EDGE),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Music, Books and Games covers are never logos, so the title box always shows the name */
private val NO_LOGO = TitleExtras()
private const val TOP_SHARE = 0.52f
private const val GLOW_REACH = 1.7f
private val COVER_TOP = 4.dp
private val COVER_BOTTOM = 16.dp
private const val COVER_FADE_MS = 250
