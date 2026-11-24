@file:kotlin.OptIn(ExperimentalMaterial3Api::class)

package com.github.damontecres.wholphin.ui.playback.overlay

import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.services.SkipFix
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.components.GlowfinIcon
import com.github.damontecres.wholphin.ui.components.GlowfinIconImage
import com.github.damontecres.wholphin.ui.components.centreShiftY
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.formatTime
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.ui.playback.PlaybackDialogType
import com.github.damontecres.wholphin.ui.seekBack
import com.github.damontecres.wholphin.ui.seekForward
import com.github.damontecres.wholphin.ui.skipStringRes
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.extensions.ticks
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import com.github.damontecres.wholphin.ui.theme.Button as TvButton

/**
 * Possible actions the user can take during playback
 */
sealed interface PlaybackAction {
    data object ShowDebug : PlaybackAction

    data object ShowPlaylist : PlaybackAction

    data object ShowVideoFilterDialog : PlaybackAction

    data object SearchCaptions : PlaybackAction

    /** Benflix: "Subtitles are early or late" (Tower lines them up with the voices) */
    data object FixSubtitleTiming : PlaybackAction

    /** Benflix: "Find better subtitles" (Bazarr throws these out and fetches different ones) */
    data object FindBetterSubtitles : PlaybackAction

    data class ToggleCaptions(
        val index: Int,
    ) : PlaybackAction

    data class ToggleAudio(
        val index: Int,
    ) : PlaybackAction

    data class PlaybackSpeed(
        val value: Float,
    ) : PlaybackAction

    data class Scale(
        val scale: ContentScale,
    ) : PlaybackAction

    data object Previous : PlaybackAction

    /** Benflix: watch together, start a lobby with this movie or show */
    data object TogetherStart : PlaybackAction

    /** Benflix: watch together, join the lobby [code] */
    data class TogetherJoin(
        val code: String,
    ) : PlaybackAction

    /** Benflix: watch together, leave the lobby */
    data object TogetherLeave : PlaybackAction

    data object Next : PlaybackAction

    data class ChangeBitrate(
        val maxBitrate: Long,
    ) : PlaybackAction

    /** Benflix: a fix from "Fix skip times", saved at the current spot in the video */
    data class FixSkip(
        val fix: SkipFix,
    ) : PlaybackAction

    /** Benflix "Fix a problem": No sound (the same fixes the sound check uses, straight away) */
    data object FixNoSound : PlaybackAction

    /** Benflix "Fix a problem": Picture stutters (restart the picture, then a lighter quality) */
    data object FixPicture : PlaybackAction

    /** Benflix "Fix a problem" (Idea Box 29): This copy is bad, Tower throws the file away and finds a new one */
    data object BadCopy : PlaybackAction

    /** Benflix "Fix a problem": Report a problem, with what's wrong */
    data class ReportProblem(
        val problem: String,
    ) : PlaybackAction
}

/**
 * UI for actual playback controls such as seek bar, play/pause and seek buttons
 */
@OptIn(UnstableApi::class)
@Composable
fun PlaybackControls(
    player: Player,
    controllerViewState: ControllerViewState,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    onSeekProgress: (Long) -> Unit,
    showPlay: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    seekEnabled: Boolean,
    seekBarIntervals: Int,
    episodesEnabled: Boolean = false,
    seekBack: Duration,
    skipBackOnResume: Duration?,
    seekForward: Duration,
    currentSegment: MediaSegmentDto?,
    modifier: Modifier = Modifier,
    initialFocusRequester: FocusRequester = remember { FocusRequester() },
    seekBarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    chapters: List<Chapter> = emptyList(),
    live: LiveBarInfo? = null,
    /** The name on the line above the bar (show or movie) and the small words after it ("S2 E5 · Name") */
    title: String? = null,
    subtitle: String? = null,
) {
    val scope = rememberCoroutineScope()

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val onControllerInteraction = {
        scope.launch(ExceptionHandler()) {
            bringIntoViewRequester.bringIntoView()
        }
        controllerViewState.pulseControls()
    }
    LaunchedEffect(controllerViewState.controlsVisible) {
        if (controllerViewState.controlsVisible) {
            initialFocusRequester.tryRequestFocus()
        }
    }
    Column(
        modifier = modifier.bringIntoViewRequester(bringIntoViewRequester),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (live != null) {
            PlayerTitleLine(title = title, subtitle = subtitle, modifier = Modifier.fillMaxWidth(.95f))
            // Live TV: the programme's own line (when it started, when it ends) in place of chapters
            LiveLine(
                live = live,
                modifier = Modifier.fillMaxWidth(.95f),
            )
        } else {
            SeekBar(
                player = player,
                controllerViewState = controllerViewState,
                onSeekProgress = onSeekProgress,
                interactionSource = seekBarInteractionSource,
                isEnabled = seekEnabled,
                intervals = seekBarIntervals,
                seekBack = seekBack,
                seekForward = seekForward,
                chapters = chapters,
                title = title,
                subtitle = subtitle,
                modifier =
                    Modifier
                        .padding(vertical = 0.dp)
                        .fillMaxWidth(.95f),
            )
        }
        NetflixControlRow(
            player = player,
            initialFocusRequester = initialFocusRequester,
            onControllerInteraction = onControllerInteraction,
            onPlaybackActionClick = onPlaybackActionClick,
            onClickPlaybackDialogType = onClickPlaybackDialogType,
            showPlay = showPlay,
            previousEnabled = previousEnabled,
            nextEnabled = nextEnabled,
            episodesEnabled = episodesEnabled,
            // Ben: pressing Play puts the controls away quickly (just long enough to see it switch)
            onPlayStarted = { controllerViewState.pulseControls(PLAY_HIDE_MS) },
            seekBack = seekBack,
            seekForward = seekForward,
            skipBackOnResume = skipBackOnResume,
            currentSegment = currentSegment,
            live = live != null,
            modifier =
                Modifier
                    .fillMaxWidth(.95f)
                    .padding(top = 2.dp, bottom = 4.dp),
        )
    }
}

/** How long the controls stay after pressing Play before they slide away */
private const val PLAY_HIDE_MS = 600L

/**
 * Glowfin new look (Part 4, Ben 2026-11-22): round see-through buttons with their names under them, and Pause
 * (or Play) solid white like the main button on every page. Every button is kept: Previous, Back 10, Play/Pause,
 * Ahead 10 and Next on the left, then Subtitles, Audio, Episodes and Settings on the right. The Skip and LIVE
 * pills show just before Subtitles when they matter. No Volume button here: the TV remote and the phone's own
 * buttons do volume (Ben: Volume only on the browser and computer apps). Same on TV and phones (phones sideways
 * get slightly smaller buttons).
 */
@OptIn(UnstableApi::class)
@Composable
fun NetflixControlRow(
    player: Player,
    initialFocusRequester: FocusRequester,
    onControllerInteraction: () -> Unit,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    showPlay: Boolean,
    nextEnabled: Boolean,
    seekBack: Duration,
    seekForward: Duration,
    skipBackOnResume: Duration?,
    currentSegment: MediaSegmentDto?,
    modifier: Modifier = Modifier,
    previousEnabled: Boolean = false,
    episodesEnabled: Boolean = false,
    live: Boolean = false,
    onPlayStarted: () -> Unit = {},
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier = modifier.focusGroup()) {
            val compact = maxWidth < WIDE_CONTROLS_MIN_WIDTH
            val sizes = if (compact) PlayerButtonSizes.Compact else PlayerButtonSizes.Wide
            val gap = if (compact) 4.dp else 8.dp
            val open = { type: PlaybackDialogType ->
                onControllerInteraction.invoke()
                onClickPlaybackDialogType.invoke(type)
            }
            // Previous and Next show for episodes (and playlists), dimmed when there's none, so nothing moves
            val showEpisodeArrows = episodesEnabled || previousEnabled || nextEnabled
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Left: Previous, Back 10, Play/Pause, Ahead 10, Next
                if (showEpisodeArrows) {
                    PlayerGlowButton(
                        icon = GlowfinIcon.PREVIOUS,
                        number = null,
                        label = "Previous",
                        contentDescription = "Previous episode",
                        sizes = sizes,
                        enabled = previousEnabled,
                        onClick = {
                            onControllerInteraction.invoke()
                            onPlaybackActionClick.invoke(PlaybackAction.Previous)
                        },
                        onControllerInteraction = onControllerInteraction,
                    )
                }
                PlayerGlowButton(
                    icon = GlowfinIcon.BACK,
                    number = seekBack.inWholeSeconds.toString(),
                    label = "Back ${seekBack.inWholeSeconds}",
                    contentDescription = "Back ${seekBack.inWholeSeconds} seconds",
                    sizes = sizes,
                    onClick = {
                        onControllerInteraction.invoke()
                        player.seekBack(seekBack)
                    },
                    onControllerInteraction = onControllerInteraction,
                )
                PlayerGlowButton(
                    icon = if (showPlay) GlowfinIcon.PLAY else GlowfinIcon.PAUSE,
                    number = null,
                    label = if (showPlay) "Play" else "Pause",
                    sizes = sizes,
                    big = true,
                    solid = true,
                    onClick = {
                        onControllerInteraction.invoke()
                        if (showPlay) {
                            player.play()
                            skipBackOnResume?.let {
                                player.seekBack(it)
                            }
                            onPlayStarted.invoke()
                        } else {
                            player.pause()
                        }
                    },
                    onControllerInteraction = onControllerInteraction,
                    modifier = Modifier.focusRequester(initialFocusRequester),
                )
                PlayerGlowButton(
                    icon = GlowfinIcon.AHEAD,
                    number = seekForward.inWholeSeconds.toString(),
                    label = "Ahead ${seekForward.inWholeSeconds}",
                    contentDescription = "Ahead ${seekForward.inWholeSeconds} seconds",
                    sizes = sizes,
                    onClick = {
                        onControllerInteraction.invoke()
                        player.seekForward(seekForward)
                    },
                    onControllerInteraction = onControllerInteraction,
                )
                if (showEpisodeArrows) {
                    PlayerGlowButton(
                        icon = GlowfinIcon.NEXT,
                        number = null,
                        label = "Next",
                        contentDescription = "Next episode",
                        sizes = sizes,
                        enabled = nextEnabled,
                        onClick = {
                            onControllerInteraction.invoke()
                            onPlaybackActionClick.invoke(PlaybackAction.Next)
                        },
                        onControllerInteraction = onControllerInteraction,
                    )
                }
                // Right: the pills that only show when they matter, then Subtitles, Audio, Episodes and Settings
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gap, Alignment.End),
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.weight(1f),
                ) {
                    // The pills sit level with the round buttons' middles
                    val pillTop = (sizes.big - 42.dp).coerceAtLeast(0.dp) / 2
                    // Live TV: a LIVE pill where movies show "Ends at". Pressing it jumps back to live.
                    if (live) {
                        PlayerPillButton(
                            text = "● LIVE",
                            faIcon = null,
                            tint = Color(0xFFD93A3A),
                            onClick = {
                                onControllerInteraction.invoke()
                                player.seekToDefaultPosition()
                                player.play()
                            },
                            onControllerInteraction = onControllerInteraction,
                            modifier =
                                Modifier
                                    .padding(top = pillTop)
                                    .semantics { contentDescription = "Live. Press to jump to live" },
                        )
                    }
                    currentSegment?.let { segment ->
                        PlayerPillButton(
                            text = stringResource(segment.type.skipStringRes),
                            faIcon = R.string.fa_arrow_right_long,
                            onClick = {
                                player.seekTo(segment.endTicks.ticks.inWholeMilliseconds)
                            },
                            onControllerInteraction = onControllerInteraction,
                            highlighted = true,
                            modifier = Modifier.padding(top = pillTop),
                        )
                    }
                    PlayerGlowButton(
                        icon = GlowfinIcon.SUBTITLES,
                        number = null,
                        label = "Subtitles",
                        sizes = sizes,
                        onClick = { open(PlaybackDialogType.CAPTIONS) },
                        onControllerInteraction = onControllerInteraction,
                    )
                    PlayerGlowButton(
                        icon = GlowfinIcon.AUDIO,
                        number = null,
                        label = "Audio",
                        sizes = sizes,
                        onClick = { open(PlaybackDialogType.AUDIO) },
                        onControllerInteraction = onControllerInteraction,
                    )
                    if (episodesEnabled) {
                        PlayerGlowButton(
                            icon = GlowfinIcon.EPISODES,
                            number = null,
                            label = "Episodes",
                            sizes = sizes,
                            onClick = { open(PlaybackDialogType.EPISODES) },
                            onControllerInteraction = onControllerInteraction,
                        )
                    }
                    // Subtitle timing, Quality and the Fix a problem list all live behind the cog
                    PlayerGlowButton(
                        icon = GlowfinIcon.SETTINGS,
                        number = null,
                        label = "Settings",
                        sizes = sizes,
                        onClick = { open(PlaybackDialogType.SETTINGS) },
                        onControllerInteraction = onControllerInteraction,
                    )
                }
            }
        }
    }
}

/** Narrower than this (phones sideways), the player buttons get a little smaller */
private val WIDE_CONTROLS_MIN_WIDTH = 820.dp

/**
 * How big the player's round buttons are: [normal] ones, the [big] Play/Pause, the name under them, and the
 * [slot] each button and its name take (the same for every button, so the row never moves when Pause turns to Play)
 */
internal data class PlayerButtonSizes(
    val normal: Dp,
    val big: Dp,
    val labelSize: androidx.compose.ui.unit.TextUnit,
    val slot: Dp,
) {
    companion object {
        val Wide = PlayerButtonSizes(normal = 48.dp, big = 56.dp, labelSize = 13.sp, slot = 66.dp)
        val Compact = PlayerButtonSizes(normal = 40.dp, big = 48.dp, labelSize = 11.sp, slot = 54.dp)
    }
}

/**
 * One of the player's round buttons (new look): see-through until the remote is on it, then white with the Glowfin
 * glow ring (a thin dark gap, an aqua ring and a soft violet glow, drawn once, no blur, cheap on older TVs).
 * A [solid] button (Pause/Play) is always white. Every button sits in a box as tall as Play/Pause, so all their
 * middles line up, with its name below.
 */
@Composable
internal fun PlayerGlowButton(
    icon: GlowfinIcon,
    number: String?,
    label: String?,
    sizes: PlayerButtonSizes,
    onClick: () -> Unit,
    onControllerInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    /** Always solid white (Pause/Play, the main button) */
    solid: Boolean = false,
    enabled: Boolean = true,
    contentDescription: String? = label,
) {
    val size = if (big) sizes.big else sizes.normal
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(sizes.slot)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.height(sizes.big)) {
            TvButton(
                onClick = onClick,
                enabled = enabled,
                shape = ButtonDefaults.shape(CircleShape),
                contentPadding = PaddingValues(0.dp),
                colors =
                    ButtonDefaults.colors(
                        containerColor = if (solid) Color.White else Color.White.copy(alpha = .14f),
                        contentColor = if (solid) Glowfin.Ink else Color.White,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Glowfin.Ink,
                    ),
                border =
                    ButtonDefaults.border(
                        border =
                            if (solid) {
                                androidx.tv.material3.Border.None
                            } else {
                                androidx.tv.material3.Border(
                                    androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .22f)),
                                    shape = CircleShape,
                                )
                            },
                        focusedBorder = androidx.tv.material3.Border.None,
                    ),
                scale = ButtonDefaults.scale(focusedScale = 1f),
                modifier =
                    modifier
                        .playerGlowRing { focused }
                        .size(size)
                        .alpha(if (enabled) 1f else .35f)
                        .onFocusChanged {
                            focused = it.isFocused
                            onControllerInteraction.invoke()
                        }.semantics { contentDescription?.let { this.contentDescription = it } },
            ) {
                val tint = if (focused || solid) Glowfin.Ink else Color.White
                val iconSize = size * if (number != null) .62f else .5f
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    GlowfinIconImage(icon = icon, color = tint, size = iconSize)
                    number?.let {
                        // The number sits in the middle of the round arrow, which is moved down to look centred
                        Text(
                            text = it,
                            color = tint,
                            fontSize = (iconSize.value * .3f).sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.offset(y = iconSize * (icon.centreShiftY() / 24f)),
                        )
                    }
                }
            }
        }
        if (label != null) {
            Text(
                text = label,
                color = if (focused) Glowfin.Text else PlayerLabelColor,
                fontSize = sizes.labelSize,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}

private val PlayerLabelColor = Color(0xFFD6D9EC)

/**
 * The Glowfin glow ring round a round (or pill) player button while [focused] says so: a thin dark gap, an aqua
 * ring and a soft violet glow, all just outlines (no blur), so it costs almost nothing on older TVs.
 */
internal fun Modifier.playerGlowRing(
    corner: Dp? = null,
    focused: () -> Boolean,
): Modifier =
    drawBehind {
        if (!focused()) return@drawBehind
        val gap = 2.5.dp.toPx()
        val ring = 2.5.dp.toPx()
        val glow = 10.dp.toPx()
        val r = corner?.toPx()

        fun shape(
            grow: Float,
            color: Color,
            width: Float,
        ) {
            val stroke = Stroke(width = width)
            if (r == null) {
                drawCircle(color = color, radius = size.minDimension / 2 + grow, style = stroke)
            } else {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(-grow, -grow),
                    size = Size(size.width + grow * 2, size.height + grow * 2),
                    cornerRadius = CornerRadius(r + grow),
                    style = stroke,
                )
            }
        }
        for (i in 1..4) {
            shape(gap + ring + glow * i / 4f, Glowfin.Violet.copy(alpha = .3f * (1f - (i - 1) / 4f)), glow / 4f + 1f)
        }
        shape(gap / 2f, Color(0xFF05060D), gap)
        shape(gap + ring / 2f, Glowfin.Aqua, ring)
    }

/**
 * Round see-through button that turns white when selected, with an optional number (like "10") under the icon
 * (YouTube trailers). Same Glowfin glow ring as the video player's buttons.
 */
@Composable
fun PlayerCircleButton(
    @StringRes faIcon: Int,
    label: String?,
    size: Dp,
    onClick: () -> Unit,
    onControllerInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    TvButton(
        onClick = onClick,
        shape = ButtonDefaults.shape(CircleShape),
        contentPadding = PaddingValues(0.dp),
        colors =
            ButtonDefaults.colors(
                containerColor = Color.White.copy(alpha = .12f),
                contentColor = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Glowfin.Ink,
            ),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .playerGlowRing { focused }
                .size(size)
                .onFocusChanged {
                    focused = it.isFocused
                    onControllerInteraction.invoke()
                },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(
                text = stringResource(faIcon),
                fontFamily = FontAwesome,
                fontSize = (size.value * if (label == null) .42f else .36f).sp,
                textAlign = TextAlign.Center,
            )
            label?.let {
                Text(
                    text = it,
                    fontSize = BenflixType.tag,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Labeled button like "Skip intro" or "● LIVE": see-through until selected, then white with the Glowfin glow ring
 */
@Composable
fun PlayerPillButton(
    text: String?,
    @StringRes faIcon: Int?,
    onClick: () -> Unit,
    onControllerInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    /** A colour of its own (the red LIVE pill) */
    tint: Color? = null,
) {
    var focused by remember { mutableStateOf(false) }
    TvButton(
        onClick = onClick,
        shape = ButtonDefaults.shape(RoundedCornerShape(21.dp)),
        border =
            com.github.damontecres.wholphin.ui.theme
                .glowfinButtonBorder(RoundedCornerShape(21.dp)),
        contentPadding =
            if (text == null) {
                PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            } else {
                PaddingValues(start = 16.dp, end = 18.dp, top = 8.dp, bottom = 8.dp)
            },
        colors =
            ButtonDefaults.colors(
                containerColor = tint ?: if (highlighted) Color.White.copy(alpha = .9f) else Color.White.copy(alpha = .12f),
                contentColor = if (highlighted && tint == null) Glowfin.Ink else Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Glowfin.Ink,
            ),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .playerGlowRing(corner = 21.dp) { focused }
                .height(42.dp)
                .onFocusChanged {
                    focused = it.isFocused
                    onControllerInteraction.invoke()
                },
    ) {
        if (faIcon != null) {
            Text(
                text = stringResource(faIcon),
                fontFamily = FontAwesome,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
        text?.let {
            if (faIcon != null) Spacer(Modifier.width(8.dp))
            Text(
                text = it,
                fontSize = BenflixType.body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SeekBar(
    player: Player,
    isEnabled: Boolean,
    intervals: Int,
    controllerViewState: ControllerViewState,
    onSeekProgress: (Long) -> Unit,
    seekBack: Duration,
    seekForward: Duration,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    chapters: List<Chapter> = emptyList(),
    title: String? = null,
    subtitle: String? = null,
) {
    val resources = LocalResources.current
    var bufferedProgress by remember(player) { mutableFloatStateOf(player.bufferedPosition.toFloat() / player.duration) }
    var position by remember(player) { mutableLongStateOf(player.currentPosition) }
    var progress by remember(player) { mutableFloatStateOf(player.currentPosition.toFloat() / player.duration) }
    LaunchedEffect(player) {
        while (isActive) {
            bufferedProgress = player.bufferedPosition.toFloat() / player.duration
            position = player.currentPosition
            progress = player.currentPosition.toFloat() / player.duration
            delay(250L)
        }
    }
    val durationMs = player.duration
    // Where each chapter starts, as a share of the whole (the first one at 0 needs no cut)
    val chapterMarks =
        remember(chapters, durationMs) {
            if (durationMs <= 0 || chapters.size < 2) {
                emptyList()
            } else {
                chapters
                    .map { it.position.inWholeMilliseconds.toFloat() / durationMs }
                    .filter { it > 0.005f && it < 0.995f }
            }
        }
    // New look (Part 4): the logo (or name) and episode bottom left, the two times just above the bar's ends,
    // then the bar the whole width. "Ends at" sits under the clock at the top right (PlayerEndsAt).
    val positionSec = position / 1000
    val remainingSec = (durationMs - position) / 1000
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PlayerTitleLine(title = title, subtitle = subtitle, modifier = Modifier.fillMaxWidth())
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            // Design check (Ben 2026-11-24): a little room under the title block, so the time played sits level
            // with the time left at the bar's ends instead of squeezed under the year
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 10.dp),
            ) {
                Text(
                    text = remember(positionSec) { resources.formatDuration(positionSec.seconds) },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge.merge(TimeSlotStyle),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text =
                        remember(remainingSec) {
                            if (remainingSec < 0) "" else "−${resources.formatDuration(remainingSec.seconds)}"
                        },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge.merge(TimeSlotStyle),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            IntervalSeekBarImpl(
                chapterMarks = chapterMarks,
                progress = progress,
                bufferedProgress = bufferedProgress,
                onSeek = {
                    onSeekProgress(it)
                },
                controllerViewState = controllerViewState,
                modifier = Modifier.fillMaxWidth(),
                interactionSource = interactionSource,
                enabled = isEnabled,
                durationMs = player.duration,
                seekBack = seekBack,
                seekForward = seekForward,
            )
        }
    }
}

/**
 * "Ends at 10:14 PM" under the player's clock (new look). Worked out from the time left and the play speed,
 * checked every few seconds (cheap on older TVs). Nothing shows for live TV or before the length is known.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerEndsAt(
    player: Player,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var ends by remember(player) { mutableStateOf<String?>(null) }
    LaunchedEffect(player) {
        while (isActive) {
            val durationMs = player.duration
            val remainingSec = (durationMs - player.currentPosition) / 1000
            ends =
                if (durationMs <= 0 || remainingSec < 0 || player.isCurrentMediaItemLive) {
                    null
                } else {
                    val speed = player.playbackParameters.speed.coerceAtLeast(0.1f)
                    "Ends at " +
                        formatTime(
                            context,
                            java.time.LocalTime
                                .now()
                                .plusSeconds((remainingSec / speed).toLong()),
                        )
                }
            delay(5_000L)
        }
    }
    ends?.let {
        Text(
            text = it,
            color = Color(0xFFD6D9EC),
            fontSize = BenflixType.body,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = modifier,
        )
    }
}

/** Same-width digits, so "1" and "8" take the same room */
private val TimeSlotStyle = TextStyle(fontFeatureSettings = "tnum")

/**
 * The player's name block, bottom left (new look): the logo when it's a good shape (else the name, shrinking to
 * fit), then the small words under it ("S11 · E4 · Name", or the year and rating for a movie).
 */
@Composable
fun PlayerTitleLine(
    title: String?,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    if (title.isNullOrBlank() && subtitle.isNullOrBlank()) return
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
    ) {
        if (!title.isNullOrBlank()) {
            com.github.damontecres.wholphin.ui.detail.title.TitleBox(
                title = title,
                wide = true,
                height = 40.dp,
                width = 300.dp,
            )
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = Color(0xFFE6E8F5),
                fontSize = BenflixType.body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SeekTimecodes(
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
    // Benflix redesign: when set, the right side reads "-1:09:44 · ends 9:52 PM"
    endsAtSpeed: Float? = null,
) {
    val context = LocalContext.current
    val currentLayoutDirection = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val resources = LocalResources.current
            val positionSec = positionMs / 1000
            val remainingSec = (durationMs - positionMs) / 1000
            val positionText =
                remember(positionSec) { resources.formatDuration(positionSec.seconds) }
            val remainingText =
                remember(remainingSec, endsAtSpeed) {
                    val left = "−${resources.formatDuration(remainingSec.seconds)}"
                    if (endsAtSpeed == null || remainingSec < 0) {
                        left
                    } else {
                        val realSec = (remainingSec / endsAtSpeed.coerceAtLeast(0.1f)).toLong()
                        val ends =
                            formatTime(
                                context,
                                java.time.LocalTime
                                    .now()
                                    .plusSeconds(realSec),
                            )
                        "Ends at $ends · $left"
                    }
                }
            CompositionLocalProvider(LocalLayoutDirection provides currentLayoutDirection) {
                Text(
                    text = positionText,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    modifier =
                        Modifier
                            .padding(8.dp),
                )
            }
            CompositionLocalProvider(LocalLayoutDirection provides currentLayoutDirection) {
                Text(
                    text = remainingText,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    modifier =
                        Modifier
                            .padding(8.dp),
                )
            }
        }
    }
}

/**
 * Live TV in the one bar (approved pl-family): what's on now, when it started and when it ends. Times
 * are 0 when the channel has no TV guide; then the line is simply full and says "Live".
 */
data class LiveBarInfo(
    val programme: String?,
    val startMs: Long,
    val endMs: Long,
    val next: String? = null,
) {
    private val hasTimes get() = endMs > startMs

    /** The small grey line under the channel name: "Top Story tonight · 9:00 to 10:00 pm" */
    fun topLine(): String? =
        listOfNotNull(
            programme?.takeIf { it.isNotBlank() },
            if (hasTimes) "${clock(startMs)} to ${clock(endMs)}" else null,
        ).joinToString(" · ").ifBlank { null }

    internal fun progress(now: Long): Float = if (hasTimes) ((now - startMs).toFloat() / (endMs - startMs)).coerceIn(0f, 1f) else 1f

    internal fun startedWords(now: Long): String {
        if (!hasTimes) return "Live"
        val minutes = ((now - startMs) / 60_000L).coerceAtLeast(0L)
        return when {
            minutes < 1 -> "Just started"
            minutes < 60 -> "Started $minutes min ago"
            else -> "Started ${minutes / 60} h ${minutes % 60} min ago"
        }
    }

    internal companion object {
        fun clock(ms: Long): String =
            java.time.Instant
                .ofEpochMilli(ms)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalTime()
                .format(
                    java.time.format.DateTimeFormatter
                        .ofPattern("h:mm a"),
                ).lowercase()
    }
}

/** The live bar fills in the Glowfin colours, like the normal seek bar */
private val LIVE_BAR_FILL =
    androidx.compose.ui.graphics.Brush
        .horizontalGradient(listOf(Glowfin.Violet, Glowfin.Blue, Glowfin.Aqua))

/** The Live TV line: "Started 22 min ago" above it, the programme's start and end times under it */
@Composable
fun LiveLine(
    live: LiveBarInfo,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(live) {
        while (isActive) {
            now = System.currentTimeMillis()
            delay(15_000L)
        }
    }
    Column(modifier = modifier) {
        Text(
            text = live.startedWords(now) + (live.next?.let { "  ·  Next: $it" } ?: ""),
            color = BenflixColors.Soft,
            fontSize = BenflixType.small,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 6.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color.White.copy(alpha = .25f), RoundedCornerShape(3.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(live.progress(now))
                    .height(4.dp)
                    .background(LIVE_BAR_FILL, RoundedCornerShape(3.dp)),
            )
        }
        if (live.endMs > live.startMs) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text(
                    LiveBarInfo.clock(live.startMs),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    LiveBarInfo.clock(live.endMs),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        } else {
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun PlaybackButton(
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    onControllerInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val selectedColor = MaterialTheme.colorScheme.border
    Button(
        enabled = enabled,
        onClick = onClick,
//        shape = ButtonDefaults.shape(CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = AppColors.TransparentBlack25,
                focusedContainerColor = selectedColor,
            ),
        contentPadding = PaddingValues(4.dp),
        interactionSource = interactionSource,
        modifier =
            modifier
                .size(36.dp, 36.dp)
                .onFocusChanged { onControllerInteraction.invoke() },
    ) {
        Icon(
            modifier = Modifier.fillMaxSize(),
            painter = painterResource(iconRes),
            contentDescription = "",
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun PlaybackFaButton(
    @StringRes iconRes: Int,
    onClick: () -> Unit,
    onControllerInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    textColor: Color = Color.Unspecified,
) {
    val selectedColor = MaterialTheme.colorScheme.border
    Button(
        enabled = enabled,
        onClick = onClick,
//        shape = ButtonDefaults.shape(CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = AppColors.TransparentBlack25,
                focusedContainerColor = selectedColor,
            ),
        contentPadding = PaddingValues(4.dp),
        interactionSource = interactionSource,
        modifier =
            modifier
                .size(36.dp, 36.dp)
                .onFocusChanged { onControllerInteraction.invoke() },
    ) {
        Text(
            text = stringResource(iconRes),
            fontSize = 18.sp,
            fontFamily = FontAwesome,
            textAlign = TextAlign.Center,
            color =
                if (textColor.isSpecified) {
                    textColor
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterVertically),
        )
    }
}

@PreviewTvSpec
@Composable
private fun ButtonPreview() {
    WholphinTheme {
        Row(Modifier.background(Color.Red)) {
            PlaybackButton(
                iconRes = R.drawable.baseline_play_arrow_24,
                onClick = {},
                onControllerInteraction = {},
            )
            PlaybackFaButton(
                iconRes = R.string.fa_volume_high,
                onClick = {},
                onControllerInteraction = {},
                textColor = Color.Green,
            )
        }
    }
}
