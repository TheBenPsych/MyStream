package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.gt
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.logCoilError
import com.github.damontecres.wholphin.ui.theme.BenflixRed
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.util.BlurHash
import org.jellyfin.sdk.model.api.ImageType

/**
 * Display an image for an item with optional overlay data
 *
 * This will fetch the image using fillWidth/fillHeight based on the layout size
 */
@Composable
fun ItemCardImage(
    item: BaseItem?,
    name: String?,
    showOverlay: Boolean,
    favorite: Boolean,
    watched: Boolean,
    unwatchedCount: Int,
    watchedPercent: Double?,
    numberOfVersions: Int,
    modifier: Modifier = Modifier,
    imageType: ImageType = ImageType.PRIMARY,
    useFallbackText: Boolean = true,
    contentScale: ContentScale = ContentScale.Fit,
    fillWidth: Int? = null,
    fillHeight: Int? = null,
    focused: Boolean = false,
) {
    val imageUrlService = LocalImageUrlService.current
    val imageUrl =
        remember(item, imageType, fillWidth, fillHeight) {
            if (item != null && (fillWidth.gt(0) || fillHeight.gt(0))) {
                imageUrlService.getItemImageUrl(
                    item,
                    imageType,
                    fillWidth = fillWidth,
                    fillHeight = fillHeight,
                )
            } else {
                null
            }
        }
    ItemCardImage(
        imageUrl = imageUrl,
        name = name,
        showOverlay = showOverlay,
        favorite = favorite,
        watched = watched,
        unwatchedCount = unwatchedCount,
        watchedPercent = watchedPercent,
        numberOfVersions = numberOfVersions,
        modifier = modifier,
        useFallbackText = useFallbackText,
        contentScale = contentScale,
        badgeItem = item,
        focused = focused,
    )
}

@Composable
fun ItemCardImage(
    imageUrl: String?,
    name: String?,
    showOverlay: Boolean,
    favorite: Boolean,
    watched: Boolean,
    unwatchedCount: Int,
    watchedPercent: Double?,
    numberOfVersions: Int,
    modifier: Modifier = Modifier,
    useFallbackText: Boolean = true,
    contentScale: ContentScale = ContentScale.Fit,
    blurHash: String? = null,
    badgeItem: BaseItem? = null,
    focused: Boolean = false,
    door: androidx.compose.ui.graphics.Color = badgeItem.doorColor(),
    fallback: @Composable BoxScope.() -> Unit = {
        ItemCardImageFallback(
            name = name,
            useFallbackText = useFallbackText,
            modifier = Modifier,
            door = door,
        )
    },
) {
    var imageError by remember(imageUrl) { mutableStateOf(false) }
    // Redesign 2 (rc-p-load): the card shows dark straight away and the picture fades in. (The old blurry
    // preview cost the slow TVs a decode per card, so it's gone.)
    val request = rememberFadeIn(imageUrl?.takeIf { it.isNotBlank() })
    Box(
        modifier = modifier.background(com.github.damontecres.wholphin.ui.theme.BenflixColors.Raised),
    ) {
        if (!imageError && imageUrl.isNotNullOrBlank()) {
            AsyncImage(
                model = request,
                contentDescription = name,
                contentScale = contentScale,
                alignment = Alignment.Center,
                onError = {
                    logCoilError(imageUrl, it.result)
                    imageError = true
                },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .align(Alignment.TopCenter),
            )
        } else {
            fallback.invoke(this)
        }
        if (showOverlay) {
            ItemCardImageOverlay(
                favorite = favorite,
                watched = watched,
                unwatchedCount = unwatchedCount,
                watchedPercent = watchedPercent,
                numberOfVersions = numberOfVersions,
                modifier = Modifier,
                badgeItem = badgeItem,
                focused = focused,
            )
        }
    }
}

@Composable
fun BoxScope.ItemCardImageFallback(
    name: String?,
    useFallbackText: Boolean,
    modifier: Modifier = Modifier,
    door: androidx.compose.ui.graphics.Color = com.github.damontecres.wholphin.ui.theme.BenflixColors.Shows,
) {
    // Redesign 2 (rc-p-noart): no picture = a dark card with the big white name and a short door-colour line
    if (name.isNotNullOrBlank()) {
        NoPictureCard(name = name, door = door, modifier = modifier.align(Alignment.TopCenter))
        return
    }
    Box(
        modifier =
            modifier
                .background(com.github.damontecres.wholphin.ui.theme.BenflixColors.Raised)
                .fillMaxSize()
                .align(Alignment.TopCenter),
    ) {
        Image(
            painter = painterResource(id = R.drawable.video_solid),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter =
                ColorFilter.tint(
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    BlendMode.SrcIn,
                ),
            modifier =
                Modifier
                    .fillMaxSize(.4f)
                    .align(Alignment.Center),
        )
    }
}

/**
 * Benflix: the same badges as the Home rows, so every card looks alike. One word badge
 * top-left ("New episode" or a rating), a small white tick top-right when watched, and a red
 * bar along the bottom when part-watched. The heart, versions count and unwatched count are
 * no longer shown (Ben's picks, 2026-11-02); those parameters are kept so callers don't change.
 */
@Composable
fun ItemCardImageOverlay(
    favorite: Boolean,
    watched: Boolean,
    unwatchedCount: Int,
    watchedPercent: Double?,
    numberOfVersions: Int,
    modifier: Modifier = Modifier,
    badgeItem: BaseItem? = null,
    focused: Boolean = false,
) {
    Box(modifier = modifier.fillMaxSize()) {
        CardWordBadge(
            item = badgeItem,
            focused = focused,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
        )
        // Benflix: an anime's fan genres (Isekai, Shounen...) from AniList
        CardAnimeTags(
            item = badgeItem,
            focused = focused,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, end = 6.dp, bottom = 8.dp),
        )
        if (watched && (watchedPercent == null || watchedPercent <= 0.0 || watchedPercent >= 100.0)) {
            CardWatchedTick(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            )
        }

        if (watchedPercent != null && watchedPercent > 0 && watchedPercent < 100) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .background(BenflixRed)
                        .clip(RectangleShape)
                        .height(Cards.playedPercentHeight)
                        .fillMaxWidth((watchedPercent / 100.0).toFloat()),
            )
        }
    }
}

@Composable
fun FavoriteIndicator(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 20.sp,
) = Text(
    color = colorResource(android.R.color.holo_red_light),
    text = stringResource(R.string.fa_heart),
    fontSize = fontSize,
    fontFamily = FontAwesome,
    modifier = modifier,
)
