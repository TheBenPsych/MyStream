package com.github.damontecres.wholphin.ui.playback.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.components.TimeDisplay
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.playback.AnalyticsState
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.ui.playback.CurrentPlayback
import com.github.damontecres.wholphin.ui.playback.PlaybackDialogType
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixMotion
import com.github.damontecres.wholphin.ui.theme.BenflixType
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.TrickplayInfo
import kotlin.time.Duration

/**
 * The overlay during playback showing controls, seek preview image, debug info, etc
 */
@Composable
fun PlaybackOverlay(
    item: BaseItem?,
    player: Player,
    controllerViewState: ControllerViewState,
    showPlay: Boolean,
    showClock: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    episodesEnabled: Boolean = false,
    seekEnabled: Boolean,
    seekBack: Duration,
    skipBackOnResume: Duration?,
    seekForward: Duration,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    onSeekBarChange: (Long) -> Unit,
    showDebugInfo: Boolean,
    currentPlayback: CurrentPlayback?,
    currentSegment: MediaSegmentDto?,
    analyticsState: AnalyticsState,
    modifier: Modifier = Modifier,
    trickplayInfo: TrickplayInfo? = null,
    trickplayUrlFor: (Int) -> String? = { null },
    seekBarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    chapters: List<Chapter> = emptyList(),
    /** Live TV: the same bar with a LIVE pill and the programme's times instead of chapters */
    live: LiveBarInfo? = null,
) {
    val seekBarFocused by seekBarInteractionSource.collectIsFocusedAsState()
    // Will be used for preview/trick play images
    var seekProgressMs by remember(seekBarFocused) { mutableLongStateOf(player.currentPosition) }
    var seekProgressPercent = (seekProgressMs.toDouble() / player.duration).toFloat()

    val density = LocalDensity.current

    // This will be calculated after composition
    var controllerHeight by remember { mutableStateOf(0.dp) }
    var state by remember(controllerViewState.controlsVisible) {
        mutableStateOf(if (controllerViewState.controlsVisible) OverlayViewState.CONTROLLER else OverlayViewState.HIDDEN)
    }
    val onChangeState = { newState: OverlayViewState ->
        state = newState
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = controllerViewState.controlsVisible && !showDebugInfo,
            enter = fadeIn(BenflixMotion.enter()),
            exit = fadeOut(BenflixMotion.exit()),
            modifier = Modifier.matchParentSize(),
        ) {
            // Background scrim for OSD readability
            val scrimBrush =
                remember {
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.80f),
                            ),
                    )
                }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(scrimBrush),
            )
        }

        AnimatedContent(
            targetState = state,
            label = "controls transition",
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    // Moving down, so move content up
                    (slideInVertically { it / 2 } + fadeIn(BenflixMotion.enter()))
                        .togetherWith(slideOutVertically { -it / 2 } + fadeOut(BenflixMotion.exit()))
                } else {
                    // Moving up
                    (slideInVertically { -it / 2 } + fadeIn(BenflixMotion.enter()))
                        .togetherWith(slideOutVertically { it / 2 } + fadeOut(BenflixMotion.exit()))
                }
            },
        ) { targetState ->
            when (targetState) {
                OverlayViewState.HIDDEN -> {
                    // Necessary so the bounds for animation are full width
                    Box(Modifier.fillMaxWidth())
                }

                OverlayViewState.CONTROLLER -> {
                    if (seekBarFocused) {
                        LaunchedEffect(Unit) {
                            seekProgressPercent =
                                (player.currentPosition.toFloat() / player.duration)
                        }
                    }
                    PlaybackController(
                        item = item,
                        player = player,
                        controllerViewState = controllerViewState,
                        showPlay = showPlay,
                        previousEnabled = previousEnabled,
                        nextEnabled = nextEnabled,
                        episodesEnabled = episodesEnabled,
                        seekEnabled = seekEnabled,
                        seekBack = seekBack,
                        skipBackOnResume = skipBackOnResume,
                        seekForward = seekForward,
                        onPlaybackActionClick = onPlaybackActionClick,
                        onClickPlaybackDialogType = onClickPlaybackDialogType,
                        onSeekBarChange = {
                            onSeekBarChange(it)
                            seekProgressMs = it
                        },
                        currentSegment = currentSegment,
                        onChangeState = onChangeState,
                        modifier =
                            Modifier
                                .padding(bottom = 8.dp)
                                .onGloballyPositioned {
                                    controllerHeight = with(density) { it.size.height.toDp() }
                                },
                        seekBarInteractionSource = seekBarInteractionSource,
                        chapters = chapters,
                        live = live,
                    )
                }
            }
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            // Trickplay
            AnimatedVisibility(
                visible = controllerViewState.controlsVisible && seekProgressPercent >= 0 && seekBarFocused,
                enter =
                    expandVertically(
                        spring(
                            stiffness = Spring.StiffnessMedium,
                            visibilityThreshold = IntSize.VisibilityThreshold,
                        ),
                    ) + fadeIn(BenflixMotion.enter()),
                exit = shrinkVertically(BenflixMotion.exit()) + fadeOut(BenflixMotion.exit()),
            ) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(.95f),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .offsetByPercent(
                                    xPercentage = seekProgressPercent.coerceIn(0f, 1f),
                                ).padding(bottom = controllerHeight + 12.dp),
                    ) {
                        TrickplayPreview(
                            seekProgressMs = seekProgressMs,
                            trickplayInfo = trickplayInfo,
                            trickplayUrlFor = trickplayUrlFor,
                        )
                    }
                }
            }
        }

        // Top left: the name in plain words (approved redesign, same on TV and phone).
        // Movies: name + "2023 · PG-13". Episodes: "Show · S2 E5 · Episode" on one line.
        val topTitle =
            remember(item) {
                if (item?.type == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE) {
                    listOfNotNull(
                        item.data.seriesName,
                        item.data.parentIndexNumber?.let { s -> item.data.indexNumber?.let { e -> "S$s E$e" } },
                        item.data.name,
                    ).joinToString(" · ")
                } else {
                    item?.title
                }
            }
        val topLine =
            remember(item, live) {
                if (live != null) {
                    live.topLine()
                } else if (item?.type == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE) {
                    null
                } else {
                    listOfNotNull(
                        item?.data?.productionYear?.toString(),
                        item?.data?.officialRating?.takeIf { it.isNotBlank() },
                    ).joinToString(" · ").ifBlank { null }
                }
            }
        AnimatedVisibility(
            visible = !showDebugInfo && topTitle.isNotNullOrBlank() && controllerViewState.controlsVisible,
            enter = fadeIn(BenflixMotion.enter()),
            exit = fadeOut(BenflixMotion.exit()),
            modifier =
                Modifier
                    .align(Alignment.TopStart),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(topScrim),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth(.7f)
                            .padding(start = 28.dp, top = 22.dp, bottom = 40.dp),
                ) {
                    Text(
                        text = topTitle.orEmpty(),
                        color = Color.White,
                        fontSize = BenflixType.rowName,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    topLine?.let {
                        Text(
                            text = it,
                            color = BenflixColors.Soft,
                            fontSize = BenflixType.small,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = !showDebugInfo && showClock && controllerViewState.controlsVisible,
            enter = fadeIn(BenflixMotion.enter()),
            exit = fadeOut(BenflixMotion.exit()),
            modifier =
                Modifier
                    .align(Alignment.TopEnd),
        ) {
            TimeDisplay()
        }

        // Debug overlay
        AnimatedVisibility(
            visible = showDebugInfo && controllerViewState.controlsVisible,
            enter = slideInVertically(BenflixMotion.enter()) + fadeIn(BenflixMotion.enter()),
            exit = slideOutVertically(BenflixMotion.exit()) + fadeOut(BenflixMotion.exit()),
            modifier =
                Modifier
                    .align(Alignment.TopStart),
        ) {
            val configuration = LocalConfiguration.current
            val height =
                remember(
                    configuration,
                    controllerHeight,
                ) { configuration.screenHeightDp.dp - controllerHeight }
            PlaybackDebugOverlay(
                analyticsState = analyticsState,
                currentPlayback = currentPlayback,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .heightIn(max = height)
                        .padding(bottom = 16.dp)
                        .background(AppColors.TransparentBlack50)
                        .padding(8.dp)
                        .onFocusChanged {
                            if (it.hasFocus) {
                                // If Debug overlay gains focus, do not hide the controls
                                controllerViewState.pulseControls(Long.MAX_VALUE)
                            } else {
                                controllerViewState.pulseControls()
                            }
                        },
            )
        }
    }
}

/** Soft shade at the top so the name stays readable over bright pictures */
private val topScrim =
    Brush.verticalGradient(
        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
    )

/**
 * The view state of the overlay
 */
enum class OverlayViewState {
    HIDDEN,
    CONTROLLER,
}
