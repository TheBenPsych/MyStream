package com.github.damontecres.wholphin.ui.playback

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.dot
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.formatTime
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.Clock
import com.github.damontecres.wholphin.ui.util.LocalClock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Benflix "Next episode" panel: a picture of the next episode, its name, and a big white
 * "Next episode" button that fills up as the countdown runs, plus "Watch credits" to stay.
 */
@Composable
fun NextUpEpisode(
    title: String?,
    description: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    timeLeft: Duration?,
    modifier: Modifier = Modifier,
    aspectRatio: Float = AspectRatios.WIDE,
    runtime: Duration? = null,
    totalCountdown: Duration? = null,
    onWatchCredits: (() -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(aspectRatio.coerceAtLeast(AspectRatios.MIN))
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier =
                Modifier
                    .fillMaxHeight()
                    .weight(1f),
        ) {
            Text(
                text = stringResource(R.string.player_next_episode).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = title ?: "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            runtime?.let {
                val context = LocalContext.current
                val resources = LocalResources.current
                val now by LocalClock.current.now
                val text =
                    remember(now, runtime) {
                        buildAnnotatedString {
                            append(resources.formatDuration(runtime))
                            dot()
                            val endsAt = now.plus(runtime.toJavaDuration())
                            val endTimeStr = formatTime(context, endsAt)
                            append(resources.getString(R.string.ends_at, endTimeStr))
                        }
                    }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val counting = timeLeft != null && timeLeft > Duration.ZERO
                val fraction =
                    if (counting && totalCountdown != null && totalCountdown > Duration.ZERO) {
                        1f - (timeLeft!!.inWholeSeconds.toFloat() / totalCountdown.inWholeSeconds)
                    } else {
                        0f
                    }
                val animatedFraction by animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = tween(1000, easing = LinearEasing),
                    label = "next_up_fill",
                )
                Button(
                    onClick = onClick,
                    shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                    contentPadding = PaddingValues(0.dp),
                    colors =
                        ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = .75f),
                            contentColor = Color.Black,
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black,
                        ),
                    // Takes whatever room is left after "Watch credits", so that button is never cut off
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .height(44.dp)
                            .focusRequester(focusRequester),
                ) {
                    Box(contentAlignment = Alignment.CenterStart) {
                        // Countdown fill
                        Box(Modifier.matchParentSize()) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(animatedFraction)
                                    .background(Color.Black.copy(alpha = .18f)),
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 14.dp, end = 20.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text =
                                    if (counting) {
                                        stringResource(R.string.player_next_episode_in, timeLeft!!.inWholeSeconds)
                                    } else {
                                        stringResource(R.string.player_next_episode)
                                    },
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (counting && onWatchCredits != null) {
                    Button(
                        onClick = onWatchCredits,
                        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                        colors =
                            ButtonDefaults.colors(
                                containerColor = Color.White.copy(alpha = .15f),
                                contentColor = Color.White,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black,
                            ),
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        modifier = Modifier.height(44.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.player_watch_credits),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NextUpCard(
    imageUrl: String?,
    onClick: () -> Unit,
    timeLeft: Duration?,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        interactionSource = interactionSource,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .background(
                            AppColors.TransparentBlack50,
                            shape = CircleShape,
                        ),
            ) {
                if (timeLeft != null && timeLeft > Duration.ZERO) {
                    Text(
                        text = timeLeft.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier =
                            Modifier
                                .size(60.dp)
                                .align(Alignment.Center),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalCoilApi::class)
@PreviewTvSpec
@Composable
private fun NextUpEpisodePreview() {
    WholphinTheme(true) {
        val theme = MaterialTheme.colorScheme
        val previewHandler =
            AsyncImagePreviewHandler {
                ColorImage(theme.surfaceVariant.toArgb())
            }
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
        ) {
            CompositionLocalProvider(
                LocalAsyncImagePreviewHandler provides previewHandler,
                LocalClock provides Clock(),
            ) {
                NextUpEpisode(
                    title = "Episode Title",
                    description =
                        "This is the description of the episode. It might be long. " +
                            "It might be very, very long and overflow the available space. " +
                            "But it just keeps going and going.",
                    imageUrl = "",
                    onClick = {},
                    aspectRatio = AspectRatios.WIDE,
                    timeLeft = 30.seconds,
                    runtime = 32.minutes + 20.seconds,
                    modifier =
                        Modifier
                            .padding(8.dp)
                            .fillMaxHeight(.4f)
                            .fillMaxWidth(.66f)
                            .align(Alignment.BottomCenter)
                            .background(
                                MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                                shape = RoundedCornerShape(8.dp),
                            ),
                )
            }
        }
    }
}
