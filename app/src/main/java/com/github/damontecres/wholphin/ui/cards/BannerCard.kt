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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.enableMarquee
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import java.time.LocalDateTime
import java.time.ZoneOffset

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
    val imageUrl =
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
    var imageError by remember(imageUrl) { mutableStateOf(false) }

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
                .bringIntoViewRequester(bringIntoView)
                .size(cardWidth, cardHeight),
        onClick = { currentOnClick() },
        onLongClick = { currentOnLongClick() },
        interactionSource = interactionSource,
        colors =
            CardDefaults.colors(
//                containerColor = Color.Transparent,
            ),
        // Benflix: the tile you're on grows a little and gets a thin white outline
        scale = CardDefaults.scale(focusedScale = 1.06f),
        shape = CardDefaults.shape(Cards.focusShape),
        border = Cards.focusBorder(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize(),
//                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (!imageError && imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = imageContentScale,
                    onError = remember { { imageError = true } },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = name ?: "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .padding(16.dp)
                            .align(Alignment.Center),
                )
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
                        WatchedIcon(Modifier.size(24.dp))
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
            if (favorite) {
                Text(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                    color = colorResource(android.R.color.holo_red_light),
                    text = stringResource(R.string.fa_heart),
                    fontSize = 16.sp,
                    fontFamily = FontAwesome,
                )
            }
            if (item?.isNew == true) {
                Text(
                    text = stringResource(if (item.type == BaseItemKind.SERIES) R.string.new_episode else R.string.new_badge),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (playPercent > 0 && playPercent < 100) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .background(
                                MaterialTheme.colorScheme.tertiary,
                            ).clip(RectangleShape)
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
    val focused by interactionSource.collectIsFocusedAsState()
    val grown by rememberGrowOnFocus(interactionSource, growOnFocus && canGrow(item, aspectRatio))
    val isTv =
        com.github.damontecres.wholphin.ui.touch
            .rememberIsTv()
    // Benflix "clean tiles": the art already shows the name, so on TV the title only appears on the
    // tile you're on, and on phones rows show only artwork
    val titleAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if ((!titleOnlyWhenFocused || focused) && !grown) 1f else 0f,
        animationSpec =
            androidx.compose.animation.core
                .tween(150),
        label = "titleAlpha",
    )
    val showText = !titleOnlyWhenFocused || isTv
    val focusedAfterDelay by rememberFocusedAfterDelay(interactionSource)
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
            SlidingCardText(focused, modifier = Modifier.alpha(titleAlpha)) {
                Text(
                    text = title ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .enableMarquee(focusedAfterDelay),
                )
                Text(
                    text = subtitle ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .enableMarquee(focusedAfterDelay),
                )
            }
        }
    }
}

private const val NEW_DAYS = 7L

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
    val state = remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(focused, enabled, isTv) {
        if (!focused || !enabled || !isTv) {
            state.value = false
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(GROW_DELAY_MS)
        state.value = true
    }
    return state
}

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
                    color = Color.White.copy(alpha = .85f),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Whether to show a "New" badge: a series with unwatched episodes added recently,
 * or an unwatched episode added recently
 */
private val BaseItem.isNew: Boolean
    get() {
        val cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(NEW_DAYS)
        return when (type) {
            BaseItemKind.SERIES -> {
                (data.userData?.unplayedItemCount ?: 0) > 0 &&
                    data.dateLastMediaAdded?.isAfter(cutoff) == true
            }

            BaseItemKind.EPISODE -> {
                data.userData?.played != true && data.dateCreated?.isAfter(cutoff) == true
            }

            else -> {
                false
            }
        }
    }
