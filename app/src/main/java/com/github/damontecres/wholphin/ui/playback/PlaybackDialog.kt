package com.github.damontecres.wholphin.ui.playback

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.services.SkipFix
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.formatMaxBitrate
import com.github.damontecres.wholphin.ui.playback.overlay.PlaybackAction
import com.github.damontecres.wholphin.ui.touch.Dialog
import kotlin.time.Duration

enum class PlaybackDialogType {
    DEBUG,
    CAPTIONS,
    SETTINGS,
    AUDIO,
    PLAYBACK_SPEED,
    VIDEO_SCALE,
    SUBTITLE_DELAY,
    BITRATE,
    NIGHT_MODE,

    /** Benflix: surround sound passthrough on/off (see SurroundPassthrough) */
    SURROUND_PASSTHROUGH,

    /** Benflix: seasons and episodes over the video (shown by PlaybackPage, not here) */
    EPISODES,

    /** Benflix: mark where the intro or credits really are, when a skip was wrong */
    FIX_SKIPS,

    /** Benflix: "Report a problem" from the player's Fix a problem list */
    REPORT,
}

data class PlaybackSettings(
    val showDebugInfo: Boolean,
    val audioIndex: Int?,
    val audioStreams: List<SimpleMediaStream>,
    val subtitleIndex: Int?,
    val subtitleStreams: List<SimpleMediaStream>,
    val playbackSpeed: Float,
    val contentScale: ContentScale,
    val subtitleDelay: Duration,
    val hasSubtitleDownloadPermission: Boolean,
    val playbackSpeedEnabled: Boolean,
    val maxBitrate: Long,
    /** The name shown under "Settings" (the movie or episode) */
    val title: String? = null,
    /** "Skip at the wrong time" only shows when there's something to skip (not on live channels) */
    val canFixSkips: Boolean = true,
)

/**
 * Centralized UI component for displaying dialogs during playback
 *
 * Redesign 2 (approved pl-family): every list here is the one player side panel ([PlayerPanel]), in
 * words. The gear opens Settings: Subtitles, Audio, Subtitle timing and Quality first, then the "Fix a
 * problem" list (No sound, Subtitles wrong, Skip at the wrong time, Picture stutters, Report a problem),
 * then the rarely used extras.
 *
 * @see com.github.damontecres.wholphin.ui.playback.overlay.PlaybackAction
 */
@Composable
fun PlaybackDialog(
    enableSubtitleDelay: Boolean,
    enableVideoScale: Boolean,
    type: PlaybackDialogType,
    settings: PlaybackSettings,
    onDismissRequest: () -> Unit,
    onControllerInteraction: () -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onChangeSubtitleDelay: (Duration) -> Unit,
) {
    val context = LocalContext.current
    val dismissAndPulse = {
        onControllerInteraction.invoke()
        onDismissRequest.invoke()
    }
    val subtitlesOn = settings.subtitleIndex != null && settings.subtitleIndex >= 0
    when (type) {
        PlaybackDialogType.DEBUG -> {
            throw IllegalStateException("Should not open a dialog with " + PlaybackDialogType.DEBUG)
        }

        PlaybackDialogType.CAPTIONS -> {
            val pick = { index: Int -> onPlaybackActionClick.invoke(PlaybackAction.ToggleCaptions(index)) }
            val lines =
                buildList {
                    add(
                        PanelLine.Row(
                            label = "Off",
                            selected = settings.subtitleIndex == TrackIndex.DISABLED,
                            onClick = { pick(TrackIndex.DISABLED) },
                        ),
                    )
                    add(
                        PanelLine.Row(
                            label = "Only when people speak another language",
                            selected = settings.subtitleIndex == TrackIndex.ONLY_FORCED,
                            onClick = { pick(TrackIndex.ONLY_FORCED) },
                        ),
                    )
                    settings.subtitleStreams.forEach { choice ->
                        add(
                            PanelLine.Row(
                                label = choice.streamTitle ?: choice.displayTitle,
                                note = choice.displayTitle.takeIf { choice.streamTitle != null },
                                selected = choice.index == settings.subtitleIndex,
                                onClick = { pick(choice.index) },
                            ),
                        )
                    }
                    add(PanelLine.Heading("Something off?"))
                    if (subtitlesOn && enableSubtitleDelay) {
                        add(
                            PanelLine.Row(
                                label = "Subtitle timing",
                                value = delayWords(settings.subtitleDelay),
                                note = "Move the words earlier or later yourself",
                                closes = false,
                                onClick = { onClickPlaybackDialogType.invoke(PlaybackDialogType.SUBTITLE_DELAY) },
                            ),
                        )
                    }
                    if (subtitlesOn) {
                        add(
                            PanelLine.Row(
                                label = "Subtitles are early or late",
                                note = "Tower lines them up with the voices",
                                onClick = { onPlaybackActionClick.invoke(PlaybackAction.FixSubtitleTiming) },
                            ),
                        )
                        add(
                            PanelLine.Row(
                                label = "Find better subtitles",
                                note = "Bazarr swaps them for different ones",
                                onClick = { onPlaybackActionClick.invoke(PlaybackAction.FindBetterSubtitles) },
                            ),
                        )
                    }
                    add(
                        PanelLine.Row(
                            label = "Find other subtitles",
                            // Greyed-out rows stay reachable and say why (Redesign 2 ground rule 1)
                            note = if (settings.hasSubtitleDownloadPermission) "Search and download" else "Only Ben can download subtitles",
                            onClick = {
                                if (settings.hasSubtitleDownloadPermission) {
                                    onPlaybackActionClick.invoke(PlaybackAction.SearchCaptions)
                                } else {
                                    Toast
                                        .makeText(
                                            context,
                                            "Only Ben can download subtitles. Report a problem to ask him.",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                }
                            },
                        ),
                    )
                }
            PlayerPanel(title = "Subtitles", lines = lines, onDismissRequest = dismissAndPulse)
        }

        PlaybackDialogType.SETTINGS -> {
            val nightOn = NightMode.isOn(context)
            val subtitleName =
                when (settings.subtitleIndex) {
                    TrackIndex.ONLY_FORCED -> {
                        "Other languages only"
                    }

                    null, TrackIndex.DISABLED, TrackIndex.UNSPECIFIED -> {
                        "Off"
                    }

                    else -> {
                        settings.subtitleStreams
                            .firstOrNull { it.index == settings.subtitleIndex }
                            ?.let { it.streamTitle ?: it.displayTitle } ?: "On"
                    }
                }
            val audioName =
                settings.audioStreams.firstOrNull { it.index == settings.audioIndex }?.let { it.streamTitle ?: it.displayTitle }
            val lines =
                buildList {
                    add(
                        PanelLine.Row(
                            label = "Subtitles",
                            value = subtitleName,
                            closes = false,
                            onClick = { onClickPlaybackDialogType(PlaybackDialogType.CAPTIONS) },
                        ),
                    )
                    add(
                        PanelLine.Row(
                            label = "Audio",
                            value = audioName ?: "One sound track",
                            closes = false,
                            onClick = { onClickPlaybackDialogType(PlaybackDialogType.AUDIO) },
                        ),
                    )
                    if (enableSubtitleDelay) {
                        add(
                            PanelLine.Row(
                                label = "Subtitle timing",
                                value = if (subtitlesOn) delayWords(settings.subtitleDelay) else "Subtitles are off",
                                closes = false,
                                onClick = {
                                    if (subtitlesOn) {
                                        onClickPlaybackDialogType(PlaybackDialogType.SUBTITLE_DELAY)
                                    } else {
                                        Toast
                                            .makeText(
                                                context,
                                                "Turn subtitles on first (Subtitles, just above).",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    }
                                },
                            ),
                        )
                    }
                    add(
                        PanelLine.Row(
                            label = "Quality",
                            value = qualityWords(settings.maxBitrate),
                            closes = false,
                            onClick = { onClickPlaybackDialogType(PlaybackDialogType.BITRATE) },
                        ),
                    )
                    add(PanelLine.Heading("Fix a problem"))
                    add(
                        PanelLine.Row(
                            label = "No sound",
                            note = "Benflix brings the sound back",
                            icon = BenflixIcon.WARNING,
                            onClick = { onPlaybackActionClick(PlaybackAction.FixNoSound) },
                        ),
                    )
                    add(
                        PanelLine.Row(
                            label = "Subtitles wrong",
                            note = "Wrong language, early or late",
                            icon = BenflixIcon.WARNING,
                            closes = false,
                            onClick = { onClickPlaybackDialogType(PlaybackDialogType.CAPTIONS) },
                        ),
                    )
                    if (settings.canFixSkips) {
                        add(
                            PanelLine.Row(
                                label = "Skip at the wrong time",
                                note = "Mark where the intro or credits really are",
                                icon = BenflixIcon.WARNING,
                                closes = false,
                                onClick = { onClickPlaybackDialogType(PlaybackDialogType.FIX_SKIPS) },
                            ),
                        )
                    }
                    add(
                        PanelLine.Row(
                            label = "Picture stutters",
                            note = "Restarts the picture, then tries a lighter one",
                            icon = BenflixIcon.WARNING,
                            onClick = { onPlaybackActionClick(PlaybackAction.FixPicture) },
                        ),
                    )
                    add(
                        PanelLine.Row(
                            label = "Report a problem",
                            note = "Ben gets a note to fix it",
                            icon = BenflixIcon.FLAG,
                            closes = false,
                            onClick = { onClickPlaybackDialogType(PlaybackDialogType.REPORT) },
                        ),
                    )
                    add(PanelLine.Heading("More"))
                    add(
                        PanelLine.Row(
                            label = stringResource(if (nightOn) R.string.night_mode_on else R.string.night_mode_off),
                            note = stringResource(R.string.night_mode_summary),
                            onClick = { NightMode.toggle(context) },
                        ),
                    )
                    add(
                        PanelLine.Row(
                            label =
                                stringResource(
                                    if (SurroundPassthrough.isOn(context)) {
                                        R.string.surround_passthrough_on
                                    } else {
                                        R.string.surround_passthrough_off
                                    },
                                ),
                            note = stringResource(R.string.surround_passthrough_summary),
                            onClick = {
                                SurroundPassthrough.toggle(context)
                                com.github.damontecres.wholphin.ui.components.BenflixNotices
                                    .pill(R.string.surround_passthrough_next_video)
                            },
                        ),
                    )
                    add(
                        PanelLine.Row(
                            label = "Speed",
                            value = speedWords(settings.playbackSpeed),
                            note = if (settings.playbackSpeedEnabled) null else "Can't change while the sound goes to the soundbar",
                            closes = false,
                            onClick = {
                                if (settings.playbackSpeedEnabled) {
                                    onClickPlaybackDialogType(PlaybackDialogType.PLAYBACK_SPEED)
                                } else {
                                    Toast.makeText(context, "Turn Surround to soundbar off to change the speed.", Toast.LENGTH_LONG).show()
                                }
                            },
                        ),
                    )
                    if (enableVideoScale) {
                        add(
                            PanelLine.Row(
                                label = "Picture size",
                                value = playbackScaleOptions[settings.contentScale]?.let { stringResource(it) },
                                closes = false,
                                onClick = { onClickPlaybackDialogType(PlaybackDialogType.VIDEO_SCALE) },
                            ),
                        )
                    }
                    add(
                        PanelLine.Row(
                            label = stringResource(if (settings.showDebugInfo) R.string.hide_debug_info else R.string.show_debug_info),
                            onClick = { onPlaybackActionClick.invoke(PlaybackAction.ShowDebug) },
                        ),
                    )
                }
            PlayerPanel(title = "Settings", subtitle = settings.title, lines = lines, onDismissRequest = onDismissRequest)
        }

        PlaybackDialogType.AUDIO -> {
            val lines =
                settings.audioStreams.map { choice ->
                    PanelLine.Row(
                        label = choice.streamTitle ?: choice.displayTitle,
                        note = choice.displayTitle.takeIf { choice.streamTitle != null },
                        selected = choice.index == settings.audioIndex,
                        onClick = { onPlaybackActionClick.invoke(PlaybackAction.ToggleAudio(choice.index)) },
                    )
                }
            PlayerPanel(
                title = "Audio",
                subtitle = if (lines.size <= 1) "This one has one sound track" else null,
                lines = lines,
                onDismissRequest = dismissAndPulse,
            )
        }

        PlaybackDialogType.PLAYBACK_SPEED -> {
            PlayerPanel(
                title = "Speed",
                lines =
                    playbackSpeedOptions.map {
                        val value = it.toFloat()
                        PanelLine.Row(
                            label = speedWords(value),
                            selected = value == settings.playbackSpeed,
                            onClick = { onPlaybackActionClick.invoke(PlaybackAction.PlaybackSpeed(value)) },
                        )
                    },
                onDismissRequest = dismissAndPulse,
            )
        }

        PlaybackDialogType.VIDEO_SCALE -> {
            PlayerPanel(
                title = "Picture size",
                lines =
                    playbackScaleOptions.map { (scale, name) ->
                        PanelLine.Row(
                            label = stringResource(name),
                            selected = scale == settings.contentScale,
                            onClick = { onPlaybackActionClick.invoke(PlaybackAction.Scale(scale)) },
                        )
                    },
                onDismissRequest = dismissAndPulse,
            )
        }

        PlaybackDialogType.NIGHT_MODE -> {}

        PlaybackDialogType.SURROUND_PASSTHROUGH -> {}

        PlaybackDialogType.EPISODES -> {}

        PlaybackDialogType.FIX_SKIPS -> {
            // Benflix: each fix is saved on Tower, which finds the same spot in the show's other episodes
            PlayerPanel(
                title = "Skip at the wrong time?",
                subtitle = "Pause at the right spot, then press one. Tower fixes the show's other episodes too.",
                lines =
                    listOf(
                        SkipFix.INTRO_START to "Intro starts here",
                        SkipFix.INTRO_END to "Intro ends here",
                        SkipFix.CREDITS_START to "Credits start here",
                        SkipFix.CREDITS_END to "Credits end here",
                        SkipFix.NO_INTRO to "No intro in this episode",
                        SkipFix.NO_INTRO_SHOW to "No intro in this whole show",
                        SkipFix.UNDO to "Undo my fixes for this episode",
                    ).map { (fix, name) ->
                        PanelLine.Row(
                            label = name,
                            note = skipFixHint(fix),
                            onClick = { onPlaybackActionClick.invoke(PlaybackAction.FixSkip(fix)) },
                        )
                    },
                onDismissRequest = onDismissRequest,
            )
        }

        PlaybackDialogType.REPORT -> {
            PlayerPanel(
                title = "Report a problem",
                subtitle = "What's wrong? Ben gets a note with the details, so he can fix it.",
                lines =
                    REPORT_CHOICES.map { problem ->
                        PanelLine.Row(label = problem, onClick = { onPlaybackActionClick.invoke(PlaybackAction.ReportProblem(problem)) })
                    },
                onDismissRequest = dismissAndPulse,
            )
        }

        PlaybackDialogType.SUBTITLE_DELAY -> {
            // Stays a small card (not the side panel), so the words stay in sight while you move them
            Dialog(
                onDismissRequest = onDismissRequest,
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
                dialogWindowProvider?.window?.setDimAmount(0f)

                Box(
                    modifier =
                        Modifier
                            .wrapContentSize()
                            .background(
                                AppColors.TransparentBlack50,
                                shape = RoundedCornerShape(16.dp),
                            ),
                ) {
                    SubtitleDelay(
                        delay = settings.subtitleDelay,
                        onChangeDelay = onChangeSubtitleDelay,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }

        PlaybackDialogType.BITRATE -> {
            PlayerPanel(
                title = "Quality",
                subtitle = "Lower it if the picture keeps stopping to load",
                lines =
                    qualityChoices().map { bitrate ->
                        PanelLine.Row(
                            label = qualityWords(bitrate),
                            selected = if (isBestQuality(bitrate)) isBestQuality(settings.maxBitrate) else bitrate == settings.maxBitrate,
                            onClick = { onPlaybackActionClick.invoke(PlaybackAction.ChangeBitrate(bitrate)) },
                        )
                    },
                onDismissRequest = dismissAndPulse,
            )
        }
    }
}

/** What "Report a problem" offers in the player */
private val REPORT_CHOICES =
    listOf("No sound", "Subtitles wrong", "Picture stutters or freezes", "Skip at the wrong time", "Wrong video", "Something else")

/** "On time", "500ms later", "1s sooner" */
internal fun delayWords(delay: Duration): String =
    when {
        delay == Duration.ZERO -> "On time"
        delay.isPositive() -> "$delay later"
        else -> "${-delay} sooner"
    }

private fun speedWords(speed: Float): String = if (speed == 1f) "Normal" else "${speed.toString().removeSuffix(".0")}x"

/** Rates this high all play the original file, so they're one "Best" choice */
private const val BEST_QUALITY_MBPS = 60

internal fun isBestQuality(bitrate: Long): Boolean = bitrate / (1024.0 * 1024.0) >= BEST_QUALITY_MBPS

/**
 * The Quality menu's rows, highest first: one "Best" row (the top rate, so nothing is ever
 * squeezed), then each lower rate once. Before, every rate from 60 Mbps up showed as its own "Best".
 */
internal fun qualityChoices(): List<Long> {
    val all = AppPreference.bitrateValues.reversed()
    return listOfNotNull(all.firstOrNull()) + all.filterNot { isBestQuality(it) }
}

/** The quality in words: the top ones are "Best", the rest say about how sharp the picture is */
internal fun qualityWords(bitrate: Long): String {
    val mbps = bitrate / (1024.0 * 1024.0)
    return when {
        isBestQuality(bitrate) -> "Best"
        mbps >= 15 -> "Very sharp · ${formatMaxBitrate(bitrate)}"
        mbps >= 8 -> "Sharp (1080p) · ${formatMaxBitrate(bitrate)}"
        mbps >= 3 -> "Good (720p) · ${formatMaxBitrate(bitrate)}"
        else -> "Light · ${formatMaxBitrate(bitrate)}"
    }
}

/** Benflix: the small line under each "Fix skip times" choice */
private fun skipFixHint(fix: SkipFix): String? =
    when (fix) {
        SkipFix.INTRO_START -> "Press when the intro's first second plays"
        SkipFix.INTRO_END -> "Press right after the intro's last second"
        SkipFix.CREDITS_START -> "Press when the credits begin"
        SkipFix.CREDITS_END -> "Only if a scene or preview comes after the credits"
        SkipFix.NO_INTRO -> "Stops the Skip intro button on this episode"
        SkipFix.NO_INTRO_SHOW -> "Stops the Skip intro button on every episode"
        SkipFix.UNDO -> null
    }
