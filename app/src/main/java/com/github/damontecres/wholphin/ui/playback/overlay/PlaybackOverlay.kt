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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import com.github.damontecres.wholphin.ui.playback.AnalyticsState
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.ui.playback.CurrentPlayback
import com.github.damontecres.wholphin.ui.playback.PlaybackDialogType
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

// The name in plain words, on the one line above the bar (clean-up v4, same on TV and phone).
    // Movies: name, then "2023 · PG-13". Episodes: the show's name, then 'S2 E5 · "Episode"'.
    val topTitle =
        remember(item) {
            if (item?.type == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE) {
                item.data.seriesName?.takeIf { it.isNotBlank() } ?: item.title
            } else {
                item?.title
            }
        }
    val topLine =
        remember(item, live) {
            if (live != null) {
                live.topLine()
            } else if (item?.type == org.jellyfin.sdk.model.api.BaseItemKind.EPISODE) {
                listOfNotNull(
                    item.data.parentIndexNumber?.let { s -> item.data.indexNumber?.let { e -> "S$s E$e" } },
                    item.data.name
                        ?.takeIf { it.isNotBlank() && it != item.data.seriesName }
                        ?.let { "\u201C$it\u201D" },
                ).joinToString(" · ").ifBlank { null }
            } else {
                listOfNotNull(
                    item?.data?.productionYear?.toString(),
                    item?.data?.officialRating?.takeIf { it.isNotBlank() },
                ).joinToString(" · ").ifBlank { null }
            }
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
            // Simple player (Ben 2026-11-27): a dark strip behind the controls that is solid by the time it
            // reaches them, so styled anime subtitles (which never move) can't show through the buttons. It is
            // sized from the controls' real height, with a short fade above. A soft shade at the top sits behind
            // the clock. Plain gradients, no blur.
            val stripHeight = if (controllerHeight > 0.dp) controllerHeight * STRIP_OVER_CONTROLS else 160.dp
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(.2f)
                            .background(TOP_SHADE),
                )
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(stripHeight)
                            .background(BOTTOM_STRIP),
                )
            }
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
                        title = topTitle,
                        subtitle = topLine,
                        modifier =
                            Modifier
                                .padding(bottom = 8.dp)
                                .onGloballyPositioned {
                                    controllerHeight = with(density) { it.size.height.toDp() }
                                    controllerViewState.controlsHeightPx = it.size.height
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
                            .fillMaxWidth(.95f)
                            // the bar sits between the two times, so the strip lines up with the dot
                            .padding(horizontal = playerTimeSlotWidth(player.duration) + 4.dp),
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
                            // The chapter's name shows only while you move along the bar
                            chapterName = chapterNameAt(chapters, seekProgressMs),
                        )
                    }
                }
            }
        }

        // The clock top right with "Ends at" under it (new look; Ends at shows even when the clock is turned off)
        AnimatedVisibility(
            visible = !showDebugInfo && controllerViewState.controlsVisible,
            enter = fadeIn(BenflixMotion.enter()),
            exit = fadeOut(BenflixMotion.exit()),
            modifier =
                Modifier
                    .align(Alignment.TopEnd),
        ) {
            Column(horizontalAlignment = Alignment.End) {
                if (showClock) {
                    Box { TimeDisplay() }
                }
                if (live == null) {
                    PlayerEndsAt(
                        player = player,
                        modifier =
                            Modifier
                                .padding(top = if (showClock) 0.dp else 16.dp, end = 24.dp)
                                .offset(y = if (showClock) (-10).dp else 0.dp),
                    )
                }
            }
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

/** The soft shade at the top of the player, behind the clock and Ends at */
private val TOP_SHADE =
    Brush.verticalGradient(
        listOf(
            com.github.damontecres.wholphin.ui.theme.Glowfin.Ink
                .copy(alpha = .7f),
            Color.Transparent,
        ),
    )

/** How much taller than the controls the dark strip is (the part above them is its fade) */
private const val STRIP_OVER_CONTROLS = 1.9f

/** The dark strip behind the controls: clear at its top, nearly solid 40% down and solid from halfway */
private val BOTTOM_STRIP =
    Brush.verticalGradient(
        0f to Color(0x0006070E),
        .4f to Color(0xE606070E),
        .5f to Color(0xFF06070E),
        1f to Color(0xFF06070E),
    )

/**
 * The view state of the overlay
 */
enum class OverlayViewState {
    HIDDEN,
    CONTROLLER,
}
