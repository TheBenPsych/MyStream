package com.github.damontecres.wholphin.ui.cards

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.FillerKind
import com.github.damontecres.wholphin.services.animeEpisodeLabel
import com.github.damontecres.wholphin.services.fillerTagFor
import com.github.damontecres.wholphin.services.finaleTagFor
import com.github.damontecres.wholphin.services.subOnlyFor
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.enableMarquee
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.ImageType

/**
 * A Card for a TV Show Season, but can generally show most items
 */
@Composable
fun SeasonCard(
    item: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    imageHeight: Dp,
    modifier: Modifier = Modifier,
    imageWidth: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    showImageOverlay: Boolean = false,
    aspectRatio: Float = item?.aspectRatio ?: AspectRatios.TALL,
) {
    val imageUrl = rememberImageUrl(item, imageHeight, imageWidth)
    val isTv =
        com.github.damontecres.wholphin.ui.touch
            .rememberIsTv()
    // Redesign 2 (rc-family): season posters on TV keep the art clean, with "Season 2" in a small pill on it
    val showName = familyShowsName(isTv, item, aspectRatio)
    SeasonCard(
        title = item?.title,
        // Benflix: anime episodes also show their number across the whole show (Episode 135 · S3 E5)
        subtitle = animeEpisodeLabel(item)?.let { label -> listOfNotNull(label, item?.subtitle).joinToString(" · ") } ?: item?.subtitle,
        name = item?.name,
        imageUrl = imageUrl,
        isFavorite = item?.data?.userData?.isFavorite ?: false,
        isPlayed = item?.data?.userData?.played ?: false,
        unplayedItemCount = item?.data?.userData?.unplayedItemCount ?: 0,
        playedPercentage = item?.data?.userData?.playedPercentage ?: 0.0,
        numberOfVersions = item?.data?.mediaSourceCount ?: 0,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        imageHeight = imageHeight,
        imageWidth = imageWidth,
        interactionSource = interactionSource,
        showImageOverlay = showImageOverlay,
        aspectRatio = aspectRatio,
        fillerTag = fillerTagFor(item),
        finaleTag = finaleTagFor(item),
        subOnly = subOnlyFor(item),
        showName = showName,
        nameOnCard = if (!showName && item?.type == org.jellyfin.sdk.model.api.BaseItemKind.SEASON) item?.title else null,
        door = item.doorColor(),
    )
}

@Composable
fun rememberImageUrl(
    item: BaseItem?,
    imageHeight: Dp,
    imageWidth: Dp = Dp.Unspecified,
): String? {
    val density = LocalDensity.current
    val imageUrlService = LocalImageUrlService.current
    return remember(item, imageHeight, imageWidth, density) {
        if (item != null) {
            val fillHeight =
                if (imageHeight.isSpecified) {
                    with(density) {
                        imageHeight.roundToPx()
                    }
                } else {
                    null
                }
            val fillWidth =
                if (imageWidth.isSpecified) {
                    with(density) {
                        imageWidth.roundToPx()
                    }
                } else {
                    null
                }
            imageUrlService.getItemImageUrl(
                item,
                ImageType.PRIMARY,
                fillWidth = fillWidth,
                fillHeight = fillHeight,
            )
        } else {
            null
        }
    }
}

/**
 * A Card for a TV Show Season, but can generically show most items
 */
@Composable
fun SeasonCard(
    title: String?,
    subtitle: String?,
    name: String?,
    imageUrl: String?,
    isFavorite: Boolean,
    isPlayed: Boolean,
    unplayedItemCount: Int,
    playedPercentage: Double,
    numberOfVersions: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageHeight: Dp = Dp.Unspecified,
    imageWidth: Dp = Dp.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    showImageOverlay: Boolean = false,
    aspectRatio: Float = AspectRatios.TALL,
    // Benflix: FILLER / MIXED on anime episodes
    fillerTag: FillerKind? = null,
    // Benflix: SEASON FINALE / SERIES FINALE
    finaleTag: String? = null,
    // Benflix: an anime episode with no English voices, when others in the show have them
    subOnly: Boolean = false,
    // Redesign 2: whether the name goes under the card, or (for season posters on TV) a pill on it
    showName: Boolean = true,
    nameOnCard: String? = null,
    door: androidx.compose.ui.graphics.Color = com.github.damontecres.wholphin.ui.theme.BenflixColors.Shows,
) {
    val aspectRationToUse = aspectRatio.coerceAtLeast(AspectRatios.MIN)
    val width = imageHeight * aspectRationToUse
    val height = imageWidth * (1f / aspectRationToUse)

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp), // Fixed base spacing
        modifier = modifier.size(width, height),
    ) {
        Card(
            modifier =
                Modifier
                    .size(imageWidth, imageHeight)
                    .aspectRatio(aspectRationToUse),
            onClick = onClick,
            onLongClick = onLongClick,
            interactionSource = interactionSource,
            colors =
                CardDefaults.colors(
                    containerColor = Color.Transparent,
                ),
            scale = CardDefaults.scale(focusedScale = 1f),
            shape = CardDefaults.shape(Cards.focusShape),
            border = Cards.focusBorder(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize(),
            ) {
                ItemCardImage(
                    imageUrl = imageUrl,
                    name = name,
                    showOverlay = showImageOverlay,
                    favorite = isFavorite,
                    watched = isPlayed,
                    unwatchedCount = unplayedItemCount,
                    watchedPercent = playedPercentage,
                    numberOfVersions = numberOfVersions,
                    useFallbackText = false,
                    door = door,
                    modifier =
                        Modifier
                            .fillMaxSize(),
                )
                if (nameOnCard != null) {
                    CardBadge(
                        text = nameOnCard,
                        background = Color.Black.copy(alpha = .78f),
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp),
                    )
                }
                if (finaleTag != null) {
                    CardBadge(
                        text = finaleTag,
                        background = FINALE_COLOR,
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp),
                    )
                }
                if (subOnly) {
                    CardBadge(
                        text = "SUB ONLY",
                        background = SUB_ONLY_COLOR,
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp),
                    )
                }
                if (fillerTag != null) {
                    CardBadge(
                        text = fillerTag.label,
                        background = fillerTag.color,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp),
                    )
                }
            }
        }
        if (showName) {
            // Redesign 2 (rc-p-phone): up to 2 lines, then a small grey line
            CardNameUnder(name = title.orEmpty(), line = subtitle)
        }
    }
}

/**
 * Returns a [androidx.compose.runtime.State] which represents if the item has been focused for a while
 */
@Composable
fun rememberFocusedAfterDelay(interactionSource: MutableInteractionSource): androidx.compose.runtime.State<Boolean> {
    val focused by interactionSource.collectIsFocusedAsState()
    val state = remember { mutableStateOf(false) }

    LaunchedEffect(focused) {
        if (!focused) {
            state.value = false
            return@LaunchedEffect
        }
        delay(500L)
        state.value = true
    }
    return state
}

/** Benflix: the colour of the SEASON FINALE / SERIES FINALE tag */
val FINALE_COLOR =
    androidx.compose.ui.graphics
        .Color(0xFF8C1D18)

/** Benflix: the SUB ONLY badge's colour */
private val SUB_ONLY_COLOR = Color(0xFF37474F)
