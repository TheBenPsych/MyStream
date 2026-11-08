package com.github.damontecres.wholphin.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.preferences.BenflixAccent
import com.github.damontecres.wholphin.ui.theme.colors.BlueThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.BoldBlueThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.BrownThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.GreenThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.MyStreamThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.OledThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.OrangeThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.PurpleThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.RedThemeColors

val LocalTheme =
    compositionLocalOf<AppThemeColors> { AppThemeColors.PURPLE }

fun getThemeColors(appThemeColors: AppThemeColors): ThemeColors =
    when (appThemeColors) {
        AppThemeColors.PURPLE -> PurpleThemeColors
        AppThemeColors.BLUE -> BlueThemeColors
        AppThemeColors.GREEN -> GreenThemeColors
        AppThemeColors.ORANGE -> OrangeThemeColors
        AppThemeColors.OLED_BLACK -> OledThemeColors
        AppThemeColors.BOLD_BLUE -> BoldBlueThemeColors
        AppThemeColors.RED -> RedThemeColors
        AppThemeColors.BROWN -> BrownThemeColors
        AppThemeColors.MYSTREAM -> MyStreamThemeColors
        AppThemeColors.UNRECOGNIZED -> PurpleThemeColors
    }

@Composable
fun WholphinTheme(
    darkTheme: Boolean = true,
    appThemeColors: AppThemeColors = AppThemeColors.PURPLE,
    accent: BenflixAccent = BenflixAccent.ACCENT_RED,
    easyRead: Boolean = false,
    content: @Composable () -> Unit,
) {
    val themeColors = getThemeColors(appThemeColors)

    val baseScheme =
        when {
            darkTheme -> themeColors.darkScheme
            else -> themeColors.lightScheme
        }
    // Benflix redesign: one look for everyone (no profile colours, and Kids look the same as everyone
    // else, just bigger). Buttons, progress lines and highlights are white on the dark tray.
    val colorScheme = baseScheme
    val accentColor = BenflixRedBase
    // Benflix Easy-read mode: everything written gets 25% bigger and grey text gets brighter
    val density = LocalDensity.current
    val readDensity =
        if (easyRead) Density(density.density, (density.fontScale * EASY_READ_SCALE).coerceAtMost(EASY_READ_MAX_SCALE)) else density
    val isTv =
        com.github.damontecres.wholphin.ui.touch
            .rememberIsTv()
    val tvTypography = androidx.compose.runtime.remember(isTv) { appTypography() }
    val materialTypography = androidx.compose.runtime.remember(isTv) { appMaterialTypography() }
    val finalScheme = if (easyRead) colorScheme.copy(onSurfaceVariant = Color(0xFFE8E8EA)) else colorScheme
    CompositionLocalProvider(
        LocalTheme provides appThemeColors,
        LocalBenflixAccent provides accentColor,
        LocalDensity provides readDensity,
    ) {
        androidx.compose.material3.MaterialTheme(
            colorScheme =
                if (darkTheme) themeColors.darkSchemeMaterial else themeColors.lightSchemeMaterial,
            typography = materialTypography,
        ) {
            MaterialTheme(
                colorScheme = finalScheme,
                typography = tvTypography,
                content = content,
            )
        }
    }
}

private const val EASY_READ_SCALE = 1.15f

/** Never bigger than this, even if the TV's own text size is already big, so nothing gets cut off */
private const val EASY_READ_MAX_SCALE = 1.3f

/**
 * The Benflix highlight colour. In the redesign this is white for everyone (it used to be red, or the
 * profile's own colour). The name stayed so the many progress lines and highlights keep working.
 */
val BenflixRedBase = BenflixColors.Text

/** What to write on top of [BenflixRed] (dark, since the highlight is white) */
val OnBenflixRed = BenflixColors.Tray

/** The highlight colour (white) */
val LocalBenflixAccent = staticCompositionLocalOf { BenflixRedBase }

/** Used for watch progress bars everywhere (cards, banners, player). White in the redesign. */
val BenflixRed: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalBenflixAccent.current

fun BenflixAccent.toColor(): Color =
    when (this) {
        BenflixAccent.ACCENT_RED, BenflixAccent.UNRECOGNIZED -> BenflixRedBase
        BenflixAccent.ACCENT_BLUE -> Color(0xFF2F7DF6)
        BenflixAccent.ACCENT_GREEN -> Color(0xFF2FB862)
        BenflixAccent.ACCENT_GOLD -> Color(0xFFE8A92B)
        BenflixAccent.ACCENT_PURPLE -> Color(0xFF8B5CF6)
        BenflixAccent.ACCENT_PINK -> Color(0xFFE5488F)
        BenflixAccent.ACCENT_TEAL -> Color(0xFF14A8A0)
        BenflixAccent.ACCENT_ORANGE -> Color(0xFFF26B1D)
        BenflixAccent.ACCENT_MONO -> Color(0xFF9A9AA2)
    }
