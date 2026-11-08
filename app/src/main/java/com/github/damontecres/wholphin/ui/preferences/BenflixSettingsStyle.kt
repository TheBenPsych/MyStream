package com.github.damontecres.wholphin.ui.preferences

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.touch.rememberIsTv

/*
 * Benflix: the shared look for every settings page, so the pages you open from Settings
 * (Advanced, player options, screensaver, profile settings...) match the main Settings page:
 * a dark page, big Benflix-font title, each setting on its own rounded card, small grey
 * headings between groups, and on TV a "where you are" panel on the left.
 */

/** Each setting sits on its own dark rounded card */
@Composable
fun benflixSettingCard(): Modifier =
    Modifier
        .padding(vertical = 4.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))

/**
 * Benflix look A: a soft band of the profile colour across the top of the page, fading into
 * the dark background, like the details pages.
 */
@Composable
fun Modifier.benflixLookABackground(): Modifier {
    val accent = com.github.damontecres.wholphin.ui.theme.BenflixRed
    val base = MaterialTheme.colorScheme.background
    return this
        .background(base)
        .background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to accent.copy(alpha = .22f),
                .4f to Color.Transparent,
            ),
        )
}

/** Benflix look A: the small "SETTINGS" style label above a big page title */
@Composable
fun BenflixLookABadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFE4E4E7),
    background: Color = Color.White.copy(alpha = .12f),
) {
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        color = color,
        maxLines = 1,
        modifier =
            modifier
                .clip(RoundedCornerShape(6.dp))
                .background(background)
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** The big title at the top of a settings page (Benflix look A: a small SETTINGS label above it) */
@Composable
fun BenflixSettingsTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
    ) {
        BenflixLookABadge(stringResource(R.string.settings))
        Text(
            text = text,
            fontSize = if (rememberIsTv()) 40.sp else 34.sp,
            lineHeight = if (rememberIsTv()) 42.sp else 36.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A small grey heading between groups of settings */
@Composable
fun BenflixSettingsHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** What the left panel shows for one settings page */
data class BenflixSettingsPageInfo(
    @param:StringRes val title: Int,
    @param:StringRes val hint: Int,
    val icon: ImageVector,
)

fun PreferenceScreenOption.benflixPageInfo(): BenflixSettingsPageInfo =
    when (this) {
        PreferenceScreenOption.BASIC -> {
            BenflixSettingsPageInfo(R.string.settings, R.string.benflix_settings_hint_help, Icons.Default.Settings)
        }

        PreferenceScreenOption.ADVANCED -> {
            BenflixSettingsPageInfo(
                R.string.benflix_settings_advanced,
                R.string.benflix_settings_hint_advanced,
                Icons.Default.Build,
            )
        }

        PreferenceScreenOption.EXO_PLAYER -> {
            BenflixSettingsPageInfo(
                R.string.exoplayer_options,
                R.string.benflix_settings_hint_exoplayer,
                Icons.Default.PlayArrow,
            )
        }

        PreferenceScreenOption.MPV -> {
            BenflixSettingsPageInfo(R.string.mpv_options, R.string.benflix_settings_hint_mpv, Icons.Default.PlayArrow)
        }

        PreferenceScreenOption.SCREENSAVER -> {
            BenflixSettingsPageInfo(
                R.string.screensaver_settings,
                R.string.benflix_settings_hint_screensaver,
                Icons.Default.Star,
            )
        }

        PreferenceScreenOption.SKIP_SEGMENTS -> {
            BenflixSettingsPageInfo(
                R.string.skip_behavior,
                R.string.benflix_settings_hint_skip,
                Icons.Default.PlayArrow,
            )
        }

        PreferenceScreenOption.EXPERIMENTAL -> {
            BenflixSettingsPageInfo(
                R.string.experimental_settings,
                R.string.benflix_settings_hint_experimental,
                Icons.Default.Warning,
            )
        }

        PreferenceScreenOption.HDR_FORMATS -> {
            BenflixSettingsPageInfo(R.string.hdr_formats, R.string.benflix_settings_hint_hdr, Icons.Default.Settings)
        }
    }

val BenflixProfileSettingsInfo =
    BenflixSettingsPageInfo(
        R.string.profile_specific_settings,
        R.string.benflix_settings_hint_profile_server,
        Icons.Default.Person,
    )

/**
 * A full settings page in the Benflix look. TV: a panel on the left says where you are (like the
 * menu on the main Settings page), the settings fill the rest. Phone: the settings fill the screen.
 */
@Composable
fun BenflixSettingsFrame(
    info: BenflixSettingsPageInfo,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val isTv = rememberIsTv()
    if (isTv) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            modifier =
                modifier
                    .fillMaxSize()
                    .benflixLookABackground()
                    .padding(start = 32.dp, end = 32.dp, top = 24.dp),
        ) {
            WhereYouArePanel(
                info = info,
                modifier =
                    Modifier
                        .width(320.dp)
                        .fillMaxHeight(),
            )
            content(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    } else {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
        ) {
            content(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun WhereYouArePanel(
    info: BenflixSettingsPageInfo,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Same header as the main Settings page
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                text = stringResource(R.string.settings),
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        // The page you are on, drawn like a picked row in the Settings menu
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = info.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(info.title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        Text(
            text = stringResource(info.hint),
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 12.dp, end = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.benflix_settings_back_hint),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 8.dp, end = 8.dp),
        )
    }
}
