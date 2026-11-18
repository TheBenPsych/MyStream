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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixPlayFilled
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv

/*
 * Benflix: the shared look for every settings page, so the pages you open from Settings
 * (Advanced, player options, screensaver, profile settings...) match the main Settings page:
 * a dark page, big Benflix-font title, each setting on its own rounded card, small grey
 * headings between groups, and on TV a "where you are" panel on the left.
 */

/** Glowfin (S1): settings sit straight on the page (the row you're on lifts itself), so this is only spacing */
@Composable
fun benflixSettingCard(): Modifier = Modifier.padding(vertical = 1.dp)

/**
 * Benflix look A: a soft band of the profile colour across the top of the page, fading into
 * the dark background, like the details pages.
 */
@Composable
fun Modifier.benflixLookABackground(): Modifier = this.background(com.github.damontecres.wholphin.ui.theme.Glowfin.Ink)

/** Glowfin (S1): the page's name in Sora at the top of a settings page */
@Composable
fun BenflixSettingsTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    GlowfinPageHeading(text = text, modifier = modifier.padding(start = 8.dp, top = 14.dp))
}

/** A small grey heading between groups of settings */
@Composable
fun BenflixSettingsHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        fontSize = BenflixType.small,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        color = com.github.damontecres.wholphin.ui.theme.Glowfin.Dim,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 18.dp, bottom = 6.dp),
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
            BenflixSettingsPageInfo(R.string.settings, R.string.benflix_settings_hint_help, BenflixIcon.GEAR.vector)
        }

        PreferenceScreenOption.ADVANCED -> {
            BenflixSettingsPageInfo(
                R.string.benflix_settings_advanced,
                R.string.benflix_settings_hint_advanced,
                BenflixIcon.WRENCH.vector,
            )
        }

        PreferenceScreenOption.EXO_PLAYER -> {
            BenflixSettingsPageInfo(
                R.string.exoplayer_options,
                R.string.benflix_settings_hint_exoplayer,
                BenflixPlayFilled,
            )
        }

        PreferenceScreenOption.MPV -> {
            BenflixSettingsPageInfo(R.string.mpv_options, R.string.benflix_settings_hint_mpv, BenflixPlayFilled)
        }

        PreferenceScreenOption.SCREENSAVER -> {
            BenflixSettingsPageInfo(
                R.string.screensaver_settings,
                R.string.benflix_settings_hint_screensaver,
                BenflixIcon.STAR.vector,
            )
        }

        PreferenceScreenOption.SKIP_SEGMENTS -> {
            BenflixSettingsPageInfo(
                R.string.skip_behavior,
                R.string.benflix_settings_hint_skip,
                BenflixPlayFilled,
            )
        }

        PreferenceScreenOption.EXPERIMENTAL -> {
            BenflixSettingsPageInfo(
                R.string.experimental_settings,
                R.string.benflix_settings_hint_experimental,
                BenflixIcon.WARNING.vector,
            )
        }

        PreferenceScreenOption.HDR_FORMATS -> {
            BenflixSettingsPageInfo(R.string.hdr_formats, R.string.benflix_settings_hint_hdr, BenflixIcon.GEAR.vector)
        }
    }

val BenflixProfileSettingsInfo =
    BenflixSettingsPageInfo(
        R.string.profile_specific_settings,
        R.string.benflix_settings_hint_profile_server,
        BenflixIcon.PERSON.vector,
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
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            modifier =
                modifier
                    .fillMaxSize()
                    .benflixLookABackground()
                    .padding(end = 32.dp),
        ) {
            WhereYouArePanel(
                info = info,
                modifier =
                    Modifier
                        .width(300.dp)
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
                    .benflixLookABackground(),
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
    // Glowfin (S1): the same left list as Settings, showing the page you're on lit
    Column(
        modifier =
            modifier
                .background(com.github.damontecres.wholphin.ui.theme.Glowfin.Deep)
                .padding(start = 14.dp, end = 14.dp, top = 20.dp),
    ) {
        GlowfinPageHeading(stringResource(R.string.settings))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(com.github.damontecres.wholphin.ui.theme.Glowfin.Raise)
                    .padding(start = 0.dp),
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(44.dp)
                    .background(com.github.damontecres.wholphin.ui.theme.Glowfin.Aqua),
            )
            Text(
                text = stringResource(info.title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = com.github.damontecres.wholphin.ui.theme.Glowfin.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp),
            )
        }
        Text(
            text = stringResource(info.hint),
            fontSize = 14.sp,
            color = com.github.damontecres.wholphin.ui.theme.Glowfin.Dim,
            modifier = Modifier.padding(start = 8.dp, top = 12.dp, end = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.benflix_settings_back_hint),
            fontSize = 12.sp,
            color =
                com.github.damontecres.wholphin.ui.theme.Glowfin.Dim
                    .copy(alpha = 0.8f),
            modifier = Modifier.padding(start = 8.dp, end = 8.dp),
        )
    }
}
