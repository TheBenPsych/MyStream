package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.fillerTagFor
import com.github.damontecres.wholphin.services.finaleTagFor
import com.github.damontecres.wholphin.services.seasonLeftFor
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.appendAnimeScores
import com.github.damontecres.wholphin.ui.enableMarquee
import com.github.damontecres.wholphin.ui.nav.playButtonPlays
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixRed
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

/**
 * Displays an image as a card. If no image is available, the name will be shown instead
 */
@Composable
fun BannerCard(
    name: String?,
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerText: String? = null,
    played: Boolean = false,
    favorite: Boolean = false,
    playPercent: Double = 0.0,
    cardHeight: Dp = 120.dp,
    aspectRatio: Float = AspectRatios.WIDE,
    interactionSource: MutableInteractionSource? = null,
    imageType: ImageType = ImageType.PRIMARY,
    imageContentScale: ContentScale = ContentScale.FillBounds,
    useSeriesForPrimary: Boolean = true,
    grown: Boolean = false,
    titleArt: Boolean = false,
) {
    val imageUrlService = LocalImageUrlService.current
    val density = LocalDensity.current
    val fillHeight =
        remember(cardHeight, density) {
            if (cardHeight.isSpecified) {
                with(density) {
                    cardHeight.roundToPx()
                }
            } else {
                null
            }
        }
    val plainImageUrl =
        remember(item, fillHeight, imageType, useSeriesForPrimary) {
            if (item != null) {
                item.imageUrlOverride
                    ?: imageUrlService.getItemImageUrl(
                        item,
                        imageType,
                        fillWidth = null,
                        fillHeight = fillHeight,
                        useSeriesForPrimary = useSeriesForPrimary,
                    )
            } else {
                null
            }
        }
    // Benflix title art: on wide tiles (like Continue watching) show a clean picture from the show with
    // its title-logo artwork on top, when Jellyfin has a logo. Otherwise the tile looks the same as before.
    val logoUrl =
        if (titleArt && cardHeight.isSpecified) {
            com.github.damontecres.wholphin.ui.components
                .rememberLogoUrl(item)
        } else {
            null
        }
    val artUrl =
        remember(item, logoUrl, fillHeight) {
            if (item != null && logoUrl != null && item.imageUrlOverride == null) {
                imageUrlService.getItemImageUrl(item, ImageType.BACKDROP, fillHeight = fillHeight)
            } else {
                null
            }
        }
    val showTitleArt = artUrl != null
    val imageUrl = artUrl ?: plainImageUrl
    var imageError by remember(imageUrl) { mutableStateOf(false) }
    val cardInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val focused by cardInteractionSource.collectIsFocusedAsState()

    // Stabilize callbacks to prevent AsyncImage from recomposing
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    // Benflix: a poster you rest on widens into a picture from the movie with its logo
    val cardWidth by androidx.compose.animation.core.animateDpAsState(
        targetValue = cardHeight * if (grown) AspectRatios.WIDE else aspectRatio,
        animationSpec =
            androidx.compose.animation.core
                .tween(GROW_MS),
        label = "cardWidth",
    )
    val bringIntoView =
        remember {
            androidx.compose.foundation.relocation
                .BringIntoViewRequester()
        }
    androidx.compose.runtime.LaunchedEffect(grown) {
        if (grown) {
            // Once it's wide, scroll the row a little if the bigger tile runs off the screen
            kotlinx.coroutines.delay(GROW_MS + 30L)
            bringIntoView.bringIntoView()
        }
    }
    Card(
        modifier =
            modifier
                .playButtonPlays(item)
                .bringIntoViewRequester(bringIntoView)
                .size(cardWidth, cardHeight),
        onClick = { currentOnClick() },
        onLongClick = { currentOnLongClick() },
        interactionSource = cardInteractionSource,
        colors =
            CardDefaults.colors(
                containerColor = BenflixColors.Raised,
            ),
        // Benflix redesign: the tile you're on only gets a white outline (no growing)
        scale = CardDefaults.scale(focusedScale = 1f),
        shape = CardDefaults.shape(Cards.focusShape),
        border = Cards.focusBorder(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize(),
//                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            // Redesign 2: the picture fades in over a dark card; no picture = the big white name
            val request = rememberFadeIn(imageUrl)
            if (!imageError && imageUrl != null) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = imageContentScale,
                    onError = remember { { imageError = true } },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                NoPictureCard(
                    name = name ?: item?.title.orEmpty(),
                    door = item.doorColor(),
                    small = cardHeight.isSpecified && cardHeight < 110.dp,
                )
            }
            if (showTitleArt && !grown) {
                TitleArt(title = name ?: item?.title, logoUrl = logoUrl, cardHeight = cardHeight)
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = grown && item != null,
                enter =
                    androidx.compose.animation
                        .fadeIn(
                            androidx.compose.animation.core
                                .tween(GROW_MS),
                        ),
                exit =
                    androidx.compose.animation
                        .fadeOut(
                            androidx.compose.animation.core
                                .tween(100),
                        ),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (item != null) GrownPreview(item, cardHeight)
            }
            if (played || cornerText != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                ) {
                    if (played && (playPercent <= 0 || playPercent >= 100)) {
                        CardWatchedTick()
                    }
                    if (cornerText != null) {
                        Box(
                            modifier =
                                Modifier
                                    .background(
                                        AppColors.TransparentBlack50,
                                        shape = RoundedCornerShape(25),
                                    ),
                        ) {
                            Text(
                                text = cornerText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(4.dp),
                            )
                        }
                    }
                }
            }
            if (!grown) {
                // One word badge in the top-left corner: "New episode" first, otherwise a
                // rating like "TOP RATED" (on TV only on the tile you're on)
                CardWordBadge(
                    item = item,
                    focused = focused,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                )
            }
            // Benflix: "3 left in season" on Continue watching, and finale tags
            val seasonLeft = seasonLeftFor(item)
            val finale = finaleTagFor(item)
            if (!grown && (seasonLeft != null || finale != null)) {
                CardBadge(
                    text =
                        finale ?: when (seasonLeft) {
                            1 -> "LAST IN SEASON"
                            else -> "$seasonLeft LEFT IN SEASON"
                        },
                    background = if (finale != null) FINALE_COLOR else Color.Black.copy(alpha = .78f),
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 6.dp, bottom = 8.dp),
                )
            }
            // Benflix: an anime's fan genres (Isekai, Shounen...) from AniList
            if (!grown && seasonLeft == null && finale == null) {
                CardAnimeTags(
                    item = item,
                    focused = focused,
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 6.dp, end = 6.dp, bottom = 8.dp),
                )
            }
            // Benflix: FILLER / MIXED on anime episodes
            val filler = fillerTagFor(item)
            if (filler != null && !grown) {
                CardBadge(
                    text = filler.label,
                    background = filler.color,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                )
            }
            if (playPercent > 0 && playPercent < 100) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .background(BenflixRed)
                            .clip(RectangleShape)
                            .height(Cards.playedPercentHeight)
                            .fillMaxWidth((playPercent / 100).toFloat()),
                )
            }
        }
    }
}

@Composable
fun BannerCardWithTitle(
    title: String?,
    subtitle: String?,
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerText: String? = null,
    played: Boolean = false,
    favorite: Boolean = false,
    playPercent: Double = 0.0,
    cardHeight: Dp = 120.dp,
    aspectRatio: Float = AspectRatios.WIDE,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    imageType: ImageType = ImageType.PRIMARY,
    imageContentScale: ContentScale = ContentScale.FillBounds,
    useSeriesForPrimary: Boolean = item?.useSeriesForPrimary ?: true,
    titleOnlyWhenFocused: Boolean = false,
    growOnFocus: Boolean = false,
) {
    val grown by rememberGrowOnFocus(interactionSource, growOnFocus && canGrow(item, aspectRatio))
    val isTv =
        com.github.damontecres.wholphin.ui.touch
            .rememberIsTv()
    // Redesign 2 (rc-family): on TV posters stay clean (the name is on the art); covers, episodes, channels
    // and people get their name under them. Phones put the name under every card.
    val showText = familyShowsName(isTv, item, aspectRatio)
    val aspectRationToUse = aspectRatio.coerceAtLeast(AspectRatios.MIN)
    val width by androidx.compose.animation.core.animateDpAsState(
        targetValue = cardHeight * if (grown) AspectRatios.WIDE else aspectRationToUse,
        animationSpec =
            androidx.compose.animation.core
                .tween(GROW_MS),
        label = "columnWidth",
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.width(width),
    ) {
        BannerCard(
            name = null,
            item = item,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier,
            cornerText = cornerText,
            played = played,
            favorite = favorite,
            playPercent = playPercent,
            cardHeight = cardHeight,
            aspectRatio = aspectRatio,
            interactionSource = interactionSource,
            imageType = imageType,
            imageContentScale = imageContentScale,
            useSeriesForPrimary = useSeriesForPrimary,
            grown = grown,
        )
        if (showText) {
            CardNameUnder(
                name = title.orEmpty(),
                line = subtitle,
                modifier = Modifier.alpha(if (grown) 0f else 1f),
            )
        }
    }
}

/** How long the grow animation takes */
private const val GROW_MS = 220

/** How long you rest on a poster before it grows */
private const val GROW_DELAY_MS = 900L

/** Only movie and show posters grow; tiles that are already wide stay as they are */
fun canGrow(
    item: BaseItem?,
    aspectRatio: Float,
): Boolean = item != null && aspectRatio < 1f && (item.type == BaseItemKind.MOVIE || item.type == BaseItemKind.SERIES)

/**
 * True once a tile has had focus for a moment (TV only). Moving on right away doesn't grow anything,
 * so scrolling quickly along a row stays smooth.
 */
@Composable
fun rememberGrowOnFocus(
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
): androidx.compose.runtime.State<Boolean> {
    val focused by interactionSource.collectIsFocusedAsState()
    val isTv =
        com.github.damontecres.wholphin.ui.touch
            .rememberIsTv()
    // Benflix: Lite mode (slower TVs) skips the widening, so resting on a poster loads no second picture
    val context = androidx.compose.ui.platform.LocalContext.current
    val lite =
        remember {
            com.github.damontecres.wholphin.services.LiteMode
                .isOn(context)
        }
    val state = remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(focused, enabled, isTv) {
        if (!focused || !enabled || !isTv || lite) {
            state.value = false
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(GROW_DELAY_MS)
        state.value = true
    }
    return state
}

/** The show's title-logo artwork over a soft dark fade at the bottom left of a wide tile */
@Composable
private fun TitleArt(
    title: String?,
    logoUrl: String?,
    cardHeight: Dp,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = .75f),
                    ),
                ),
    ) {
        com.github.damontecres.wholphin.ui.components.TitleOrLogo(
            title = title,
            logoImageUrl = logoUrl,
            showLogo = true,
            logoHeight = cardHeight * TITLE_ART_HEIGHT,
            logoMaxWidth = cardHeight * TITLE_ART_WIDTH,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 12.dp),
        )
    }
}

/** Title art size, compared with the tile's height */
private const val TITLE_ART_HEIGHT = 0.28f
private const val TITLE_ART_WIDTH = 0.9f

/** The inside of a grown tile: a wide picture from the movie, its logo, and year, rating and length */
@Composable
private fun GrownPreview(
    item: BaseItem,
    cardHeight: Dp,
) {
    val imageUrlService = LocalImageUrlService.current
    val backdrop =
        remember(item.id) {
            imageUrlService.getItemImageUrl(item, ImageType.BACKDROP, fillWidth = 780)
        }
    val details =
        remember(item.id) {
            listOfNotNull(
                item.data.productionYear?.toString(),
                item.data.officialRating,
                item.data.runTimeTicks
                    ?.takeIf { item.type == BaseItemKind.MOVIE }
                    ?.let { ticks ->
                        val minutes = ticks / 600_000_000L
                        if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
                    },
                item.data.childCount
                    ?.takeIf { item.type == BaseItemKind.SERIES && it > 0 }
                    ?.let { if (it == 1) "1 season" else "$it seasons" },
            ).joinToString("  ·  ")
        }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        AsyncImage(
            model = backdrop,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0.35f to Color.Transparent,
                            1f to Color.Black.copy(alpha = .85f),
                        ),
                    ),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            com.github.damontecres.wholphin.ui.components.TitleOrLogo(
                title = item.title,
                logoImageUrl =
                    com.github.damontecres.wholphin.ui.components
                        .rememberLogoUrl(item),
                showLogo = true,
                logoHeight = cardHeight * .3f,
                logoMaxWidth = cardHeight,
            )
            if (details.isNotEmpty()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.labelSmall,
                    color = BenflixColors.Soft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Benflix: anime show their AniList and MyAnimeList scores and fan genres too
            val aniList by com.github.damontecres.wholphin.services.AniListFacts.cards
                .collectAsState()
            val mal by com.github.damontecres.wholphin.services.MalFacts.scores
                .collectAsState()
            aniList[item.id]?.let { facts ->
                val line =
                    androidx.compose.ui.text.buildAnnotatedString {
                        val any = appendAnimeScores(facts, mal, separator = { append("  ·  ") })
                        val tags = facts.cardTags(3)
                        if (tags.isNotEmpty()) {
                            if (any) append("  ·  ")
                            append(tags.joinToString(" · "))
                        }
                    }
                if (line.isNotEmpty()) {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.labelSmall,
                        color = BenflixColors.Soft,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
