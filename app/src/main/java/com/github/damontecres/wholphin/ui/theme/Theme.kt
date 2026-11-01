package com.github.damontecres.wholphin.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.preferences.BenflixAccent
import com.github.damontecres.wholphin.services.KidsMode
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
    content: @Composable () -> Unit,
) {
    val themeColors = getThemeColors(appThemeColors)

    val kids by KidsMode.active.collectAsState()
    val baseScheme =
        when {
            darkTheme -> themeColors.darkScheme
            else -> themeColors.lightScheme
        }
    // Benflix Kids: bright, friendly colors instead of the dark grown-up look
    val colorScheme =
        if (kids) {
            baseScheme.copy(
                primary = KidsYellow,
                onPrimary = Color.Black,
                border = KidsYellow,
                background = KidsBlue,
                surface = KidsBlueLight,
                surfaceVariant = KidsBlueLight,
                onBackground = Color.White,
                onSurface = Color.White,
                onSurfaceVariant = Color(0xFFDDE6FF),
            )
        } else if (accent != BenflixAccent.ACCENT_RED && accent != BenflixAccent.UNRECOGNIZED) {
            // Benflix profile colour: buttons and highlights use the person's own colour
            baseScheme.copy(primary = accent.toColor(), onPrimary = Color.White)
        } else {
            baseScheme
        }
    val accentColor = if (kids) KidsYellow else accent.toColor()
    CompositionLocalProvider(
        LocalTheme provides appThemeColors,
        LocalBenflixAccent provides accentColor,
    ) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = if (darkTheme) themeColors.darkSchemeMaterial else themeColors.lightSchemeMaterial,
            typography = AppMaterialTypography,
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = AppTypography,
                content = content,
            )
        }
    }
}

private val KidsYellow = Color(0xFFFFC400)
private val KidsBlue = Color(0xFF0E2A63)
private val KidsBlueLight = Color(0xFF1B3F8A)

/** Benflix red, the default profile colour */
val BenflixRedBase = Color(0xFFE50914)

/** The current profile's colour (Benflix red unless they picked another one) */
val LocalBenflixAccent = staticCompositionLocalOf { BenflixRedBase }

/** Used for watch progress bars everywhere (cards, banners, player). Follows the profile colour. */
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
    }
