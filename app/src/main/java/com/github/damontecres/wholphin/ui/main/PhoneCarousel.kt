package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.touch.Haptics
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import kotlin.math.absoluteValue

/**
 * Benflix on phones (Home look C): the banner is a big poster carousel you swipe. The poster in the
 * middle is full size, the ones beside it peek in smaller, and the page's glow follows the middle one.
 */
@Composable
fun PhonePosterCarousel(
    featured: List<FeaturedItem>,
    index: Int,
    onIndexChange: (Int) -> Unit,
    onClick: (FeaturedItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (featured.isEmpty()) return
    val pagerState = rememberPagerState(initialPage = index.coerceIn(0, featured.lastIndex)) { featured.size }
    // Swiping picks the slide (and the glow); the 7-second turn moves the carousel along
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { onIndexChange(it) }
    }
    LaunchedEffect(index) {
        val target = index.coerceIn(0, featured.lastIndex)
        if (target != pagerState.settledPage) pagerState.animateScrollToPage(target)
    }
    val haptics = Haptics.remember()
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { haptics.tick() }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        featured.getOrNull(pagerState.currentPage)?.let { FeaturedReasonLabel(featured = it) }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // The middle poster takes about 60% of the width, so the ones beside it show a little
            val posterWidth = maxWidth * .6f
            val side = (maxWidth - posterWidth) / 2
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = side),
                pageSpacing = 12.dp,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val item = featured[page].item
                val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier.graphicsLayer {
                            val scale = lerp(1f, .86f, offset)
                            scaleX = scale
                            scaleY = scale
                            alpha = lerp(1f, .55f, offset)
                        },
                ) {
                    // Episodes (e.g. "Pick up where you left off") use the show's tall poster. Their own
                    // picture is a wide still, which got zoomed in and cropped to fit the tall frame.
                    val imageUrlService = LocalImageUrlService.current
                    val posterUrl =
                        remember(item) {
                            imageUrlService.getItemImageUrl(
                                item,
                                ImageType.PRIMARY,
                                useSeriesForPrimary = item.type == BaseItemKind.EPISODE || item.type == BaseItemKind.SEASON,
                            )
                        }
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .shadow(16.dp, RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF1C1C22))
                                .clickable {
                                    haptics.tap()
                                    onClick(featured[page])
                                },
                    )
                    Text(
                        text = item.title.orEmpty(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer { alpha = 1f - offset },
                    )
                    if (item.type == BaseItemKind.EPISODE) {
                        // Which episode, under the show's name (e.g. "S1:E4 - The Dragon")
                        item.subtitle?.takeIf { it.isNotBlank() }?.let { episodeLine ->
                            Text(
                                text = episodeLine,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = .7f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.graphicsLayer { alpha = 1f - offset },
                            )
                        }
                    }
                }
            }
        }
    }
}
