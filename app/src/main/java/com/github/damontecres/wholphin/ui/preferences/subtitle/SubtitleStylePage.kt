package com.github.damontecres.wholphin.ui.preferences.subtitle

import android.content.pm.ActivityInfo
import android.os.Build
import androidx.annotation.Dimension
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.SubtitlePreferences
import com.github.damontecres.wholphin.preferences.resetSubtitles
import com.github.damontecres.wholphin.preferences.updateInterfacePreferences
import com.github.damontecres.wholphin.ui.findActivity
import com.github.damontecres.wholphin.ui.preferences.PreferenceGroup
import com.github.damontecres.wholphin.ui.preferences.PreferencesViewModel
import com.github.damontecres.wholphin.ui.preferences.subtitle.SubtitleSettings.calculateEdgeSize
import com.github.damontecres.wholphin.ui.preferences.subtitle.SubtitleSettings.toSubtitleStyle
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.util.Media3SubtitleOverride
import timber.log.Timber

@OptIn(UnstableApi::class)
@Composable
fun SubtitleStylePage(
    initialPreferences: AppPreferences,
    isPageForHdrSettings: Boolean,
    modifier: Modifier = Modifier,
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var preferences by remember { mutableStateOf(initialPreferences) }
    LaunchedEffect(Unit) {
        viewModel.preferenceDataStore.data.collect {
            preferences = it
        }
    }
    val display = LocalView.current.display

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        DisposableEffect(context) {
            if (isPageForHdrSettings) {
                Timber.v("Switching color mode to HDR")
                context.findActivity()?.window?.colorMode = ActivityInfo.COLOR_MODE_HDR
            }
            onDispose {
                context.findActivity()?.window?.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
            }
        }
    }
    val prefs =
        if (isPageForHdrSettings) {
            preferences.interfacePreferences.hdrSubtitlesPreferences
        } else {
            preferences.interfacePreferences.subtitlesPreferences
        }
    var focusedOnMargin by remember { mutableStateOf(false) }
    var focusedOnImageOpacity by remember { mutableStateOf(false) }

    // Benflix: TV shows the live preview with the settings beside it; phones put the preview on
    // top and the settings below at full width (the old narrow side panel didn't fit a phone)
    val isTv = rememberIsTv()
    val preview: @Composable (Modifier) -> Unit = { previewModifier ->
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = previewModifier,
        ) {
            Image(
                painter = painterResource(R.mipmap.eclipse),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier =
                    Modifier
                        .fillMaxSize(),
            )
            if (!focusedOnMargin && !focusedOnImageOpacity) {
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier =
                        Modifier
                            .padding(16.dp)
                            .fillMaxSize(),
                ) {
                    val examples =
                        mapOf(
                            "Subtitles will look like this" to 48.dp,
                            "This is another example" to 24.dp,
                            "Longer multi line subtitles will\nlook like this" to 0.dp,
                        )
                    examples.forEach { (text, padding) ->
                        AndroidView(
                            factory = { context ->
                                SubtitleView(context)
                            },
                            update = {
                                it.setStyle(prefs.toSubtitleStyle())
                                it.setFixedTextSize(Dimension.SP, prefs.fontSize.toFloat())
                                it.setCues(
                                    listOf(
                                        Cue.Builder().setText(text).build(),
                                    ),
                                )
                                Media3SubtitleOverride(prefs.calculateEdgeSize(density)).apply(it)
                            },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(bottom = padding),
                        )
                    }
                }
            } else if (focusedOnMargin) {
                // Margin
                AndroidView(
                    factory = { context ->
                        SubtitleView(context)
                    },
                    update = {
                        it.setStyle(prefs.toSubtitleStyle())
                        it.setFixedTextSize(Dimension.SP, prefs.fontSize.toFloat())
                        it.setCues(
                            listOf(
                                Cue.Builder().setText("Subtitles margin below here").build(),
                            ),
                        )
                        it.setBottomPaddingFraction(prefs.margin.toFloat() / 100f)
                    },
                    modifier =
                        Modifier
                            .fillMaxSize(),
                )
            } else if (focusedOnImageOpacity) {
                AndroidView(
                    factory = { context ->
                        SubtitleView(context)
                    },
                    update = {
                        it.setStyle(
                            SubtitlePreferences
                                .newBuilder()
                                .apply {
                                    resetSubtitles()
                                }.build()
                                .toSubtitleStyle(),
                        )
                        it.setCues(
                            listOf(
                                Cue
                                    .Builder()
                                    .setText("ExoPlayer only:\nImage based subtitles can be dimmed.")
                                    .build(),
                            ),
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .alpha(prefs.imageSubtitleOpacity / 100f),
                )
            }
        }
    }
    // Approved redesign: Font / Colour / Outline / Box / Size pills pick which settings show
    var pill by remember { mutableStateOf(SubtitlePill.SIZE) }
    val settings: @Composable (Modifier) -> Unit = { settingsModifier ->
        val display = LocalView.current.display
        val prefList =
            remember(isPageForHdrSettings, display, pill) {
                val group = listOf(PreferenceGroup(title = pill.groupTitle, preferences = pill.prefs))
                if (pill == SubtitlePill.MORE && !isPageForHdrSettings && SubtitleSettings.shouldShowHdr(display)) {
                    // If not on HDR page and display is HDR capable, then show the HDR button
                    group + SubtitleSettings.hdrPreferenceGroup
                } else {
                    group
                }
            }
        Column(settingsModifier) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                SubtitlePill.entries.forEach { p ->
                    SubtitlePillChip(p.label, selected = p == pill) { pill = p }
                }
            }
            androidx.compose.runtime.key(pill) {
                SubtitlePreferencesContent(
                    title =
                        if (isPageForHdrSettings) {
                            stringResource(R.string.hdr_subtitle_style)
                        } else {
                            stringResource(R.string.subtitle_style)
                        },
                    preferences =
                        if (isPageForHdrSettings) {
                            preferences.interfacePreferences.hdrSubtitlesPreferences
                        } else {
                            preferences.interfacePreferences.subtitlesPreferences
                        },
                    prefList = prefList,
                    onPreferenceChange = { newSubtitlePrefs ->
                        viewModel.preferenceDataStore.updateData {
                            it.updateInterfacePreferences {
                                if (isPageForHdrSettings) {
                                    hdrSubtitlesPreferences = newSubtitlePrefs
                                } else {
                                    subtitlesPreferences = newSubtitlePrefs
                                }
                            }
                        }
                    },
                    onFocus = { groupIndex, prefIndex ->
                        val focusedPref =
                            prefList
                                .getOrNull(groupIndex)
                                ?.preferences
                                ?.getOrNull(prefIndex)
                        focusedOnMargin = focusedPref == SubtitleSettings.Margin
                        focusedOnImageOpacity = focusedPref == SubtitleSettings.ImageOpacity
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (isTv) {
        Row(modifier = modifier) {
            preview(
                Modifier
                    .fillMaxSize()
                    .weight(1f),
            )
            settings(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(.32f),
            )
        }
    } else {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
        ) {
            preview(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )
            settings(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

/** The subtitle look pills (approved redesign) and which settings each one shows */
private enum class SubtitlePill(
    val label: String,
    val groupTitle: Int,
    val prefs: List<com.github.damontecres.wholphin.preferences.AppPreference<SubtitlePreferences, out Any?>>,
) {
    SIZE("Size", R.string.font_size, listOf(SubtitleSettings.FontSize, SubtitleSettings.Margin)),
    FONT("Font", R.string.font, listOf(SubtitleSettings.FontBold, SubtitleSettings.FontItalic)),
    COLOUR("Colour", R.string.font_color, listOf(SubtitleSettings.FontColor, SubtitleSettings.FontOpacity)),
    OUTLINE(
        "Outline",
        R.string.edge_style,
        listOf(SubtitleSettings.EdgeStylePref, SubtitleSettings.EdgeColor, SubtitleSettings.EdgeThickness),
    ),
    BOX(
        "Box",
        R.string.background,
        listOf(SubtitleSettings.BackgroundStylePref, SubtitleSettings.BackgroundColor, SubtitleSettings.BackgroundOpacity),
    ),
    MORE("More", R.string.more, listOf(SubtitleSettings.ImageOpacity, SubtitleSettings.Reset)),
}

@Composable
private fun SubtitlePillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape =
        androidx.compose.foundation.shape
            .RoundedCornerShape(50)
    androidx.tv.material3.Surface(
        onClick = onClick,
        shape =
            androidx.tv.material3.ClickableSurfaceDefaults
                .shape(shape),
        scale =
            androidx.tv.material3.ClickableSurfaceDefaults
                .scale(focusedScale = 1f),
        border =
            androidx.tv.material3.ClickableSurfaceDefaults.border(
                focusedBorder =
                    androidx.tv.material3.Border(
                        androidx.compose.foundation.BorderStroke(2.dp, androidx.compose.ui.graphics.Color.White),
                        shape = shape,
                    ),
            ),
        colors =
            androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = if (selected) BenflixColors.Text else BenflixColors.Raised,
                contentColor = if (selected) BenflixColors.Tray else BenflixColors.Text,
                focusedContainerColor = if (selected) BenflixColors.Text else BenflixColors.Raised,
                focusedContentColor = if (selected) BenflixColors.Tray else BenflixColors.Text,
            ),
    ) {
        androidx.tv.material3.Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}
