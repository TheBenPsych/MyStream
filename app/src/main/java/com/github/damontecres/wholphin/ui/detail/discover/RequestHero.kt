package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShade
import com.github.damontecres.wholphin.ui.touch.rememberIsTv

/**
 * Benflix redesign: a Requests title page looks like a Movies page. On TV and wide screens the
 * picture fills the page and the name, facts and white Ask for it sit at the bottom left; scrolling
 * down to the rows dims the picture. Phones get the picture across the top, then the words.
 *
 * [content] gets the modifier for the top block (page height on wide screens) and whether the
 * screen is wide.
 */
@Composable
fun RequestHeroLayout(
    backdropUrl: String?,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    content: @Composable (heroModifier: Modifier, wide: Boolean) -> Unit,
) {
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(BenflixColors.Tray)) {
        val pageHeight: Dp = maxHeight
        if (wide) {
            RequestHeroPicture(
                url = backdropUrl,
                dim = {
                    if (listState.firstVisibleItemIndex > 0) {
                        1f
                    } else {
                        (listState.firstVisibleItemScrollOffset / (pageHeight.value * 2f)).coerceIn(0f, 1f)
                    }
                },
            )
        }
        content(if (wide) Modifier.heightIn(min = pageHeight - 24.dp) else Modifier, wide)
    }
}

/** Phones: the picture across the top of the page */
@Composable
fun RequestPhonePicture(
    url: String?,
    modifier: Modifier = Modifier,
) {
    if (url == null) return
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(10.dp)),
    ) {
        RequestHeroPicture(url = url, dim = { 0f }, side = false)
    }
}

@Composable
private fun RequestHeroPicture(
    url: String?,
    dim: () -> Float,
    side: Boolean = true,
) {
    Box(Modifier.fillMaxSize()) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (side) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        BenflixShade.heroSide,
                    ),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    BenflixShade.bottom(0.35f),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = dim() }
                .background(BenflixColors.Tray.copy(alpha = .9f)),
        )
    }
}
