package com.github.damontecres.wholphin.ui.playback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

/**
 * Benflix ambient mode: after the video has been paused for a while, the movie's artwork slowly drifts
 * across the screen with its name and description. Any button press brings back the video.
 */
@Composable
fun PauseAmbient(
    visible: Boolean,
    item: BaseItem?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && item != null,
        enter = fadeIn(tween(1500)),
        exit = fadeOut(tween(300)),
        modifier = modifier,
    ) {
        if (item == null) return@AnimatedVisibility
        val imageUrl = LocalImageUrlService.current.rememberImageUrl(item, ImageType.BACKDROP)
        val transition = rememberInfiniteTransition(label = "ambient")
        val scale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(tween(25_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "ambient_scale",
        )
        val drift by transition.animateFloat(
            initialValue = -30f,
            targetValue = 30f,
            animationSpec = infiniteRepeatable(tween(31_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "ambient_drift",
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = drift
                            alpha = .8f
                        },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = .9f),
                            .55f to Color.Black.copy(alpha = .35f),
                            1f to Color.Transparent,
                        ),
                    ),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(.5f)
                        .padding(start = 64.dp),
            ) {
                Text(
                    text = stringResource(R.string.ambient_paused),
                    color = Color.White.copy(alpha = .7f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = item.title ?: item.name ?: "",
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.type == BaseItemKind.EPISODE) {
                    Text(
                        text = item.subtitle ?: "",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                item.data.overview?.let {
                    Text(
                        text = it,
                        color = Color.White.copy(alpha = .85f),
                        fontSize = 16.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
