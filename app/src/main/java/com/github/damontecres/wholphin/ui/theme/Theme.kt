package com.github.damontecres.wholphin.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.preferences.BenflixAccent
import com.github.damontecres.wholphin.ui.theme.colors.MyStreamThemeColors

@Composable
fun WholphinTheme(
    darkTheme: Boolean = true,
    accent: BenflixAccent = BenflixAccent.ACCENT_RED,
    content: @Composable () -> Unit,
) {
    // Glowfin has one look. The eight old Wholphin colour themes were removed (Ben 2026-11-21).
    val themeColors = MyStreamThemeColors
    KidsLookWatcher()

    val baseScheme =
        when {
            darkTheme -> themeColors.darkScheme
            else -> themeColors.lightScheme
        }
    // Benflix redesign: one look for grown-ups (no profile colours). Buttons, progress lines and highlights
    // are white on the dark tray.
    // Kids (Ben's pick K1): Glowfin for Kids, or Night Light near bedtime. Reading KidsLook here redraws
    // everything when Kids is switched on or off.
    val kidsPalette = KidsLook.palette
    val look = GlowfinTheme.look
    val colorScheme =
        when {
            kidsPalette != null -> baseScheme.withKids(kidsPalette)
            look !== BluePurpleLook -> baseScheme.withLook(look)
            else -> baseScheme
        }
    val accentColor = kidsPalette?.accent ?: BenflixRedBase
    val isTv =
        com.github.damontecres.wholphin.ui.touch
            .rememberIsTv()
    val kidsFont = KidsLook.on
    val tvTypography = androidx.compose.runtime.remember(isTv, kidsFont) { appTypography() }
    val materialTypography = androidx.compose.runtime.remember(isTv, kidsFont) { appMaterialTypography() }
    CompositionLocalProvider(LocalBenflixAccent provides accentColor) {
        androidx.compose.material3.MaterialTheme(
            colorScheme =
                (if (darkTheme) themeColors.darkSchemeMaterial else themeColors.lightSchemeMaterial).let { scheme ->
                    when {
                        kidsPalette != null -> scheme.withKids(kidsPalette)
                        look !== BluePurpleLook -> scheme.withLook(look)
                        else -> scheme
                    }
                },
            typography = materialTypography,
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = tvTypography,
                content = content,
            )
        }
    }
}

/**
 * The highlight colour. Glowfin: the logo's aqua for everyone (it used to be red, then white). The name
 * stayed so the many progress lines, Play buttons and highlights keep working.
 */
val BenflixRedBase: Color get() = GlowfinTheme.look.focus

/** What to write on top of [BenflixRed] (dark, since aqua is bright) */
val OnBenflixRed: Color get() = Glowfin.Ink

/** The highlight colour (Glowfin aqua) */
val LocalBenflixAccent = staticCompositionLocalOf { BluePurpleLook.focus }

/** Used for watch progress bars everywhere (cards, banners, player). Glowfin aqua. */
val BenflixRed: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalBenflixAccent.current

/** Glowfin: each profile colour is one of the 8 sea-friend picture colours (its main end) */
fun BenflixAccent.toColor(): Color =
    com.github.damontecres.wholphin.ui.setup.SeaColours[themeIndex()]
        .first

/** A theme's colours for the TV library's own pieces (Blue-purple keeps the built-in set untouched) */
private fun ColorScheme.withLook(l: ThemeLook): ColorScheme =
    copy(
        primary = l.text,
        onPrimary = l.ink,
        primaryContainer = l.focusRow,
        onPrimaryContainer = l.text,
        secondary = l.soft,
        onSecondary = l.ink,
        secondaryContainer = l.raise,
        onSecondaryContainer = l.text,
        border = l.dim,
        borderVariant = l.line,
        background = l.ink,
        onBackground = l.text,
        surface = l.ink,
        onSurface = l.text,
        surfaceVariant = l.raise,
        onSurfaceVariant = l.dim,
        inverseSurface = l.text,
        inverseOnSurface = l.ink,
    )

/** The same for the phone-style (Material 3) pieces */
private fun androidx.compose.material3.ColorScheme.withLook(l: ThemeLook): androidx.compose.material3.ColorScheme =
    copy(
        primary = l.text,
        onPrimary = l.ink,
        primaryContainer = l.focusRow,
        onPrimaryContainer = l.text,
        secondary = l.soft,
        onSecondary = l.ink,
        secondaryContainer = l.raise,
        onSecondaryContainer = l.text,
        outline = l.dim,
        outlineVariant = l.line,
        background = l.ink,
        onBackground = l.text,
        surface = l.ink,
        onSurface = l.text,
        surfaceVariant = l.raise,
        onSurfaceVariant = l.dim,
        inverseSurface = l.text,
        inverseOnSurface = l.ink,
        surfaceContainerLowest = l.ink,
        surfaceContainerLow = l.ink,
        surfaceContainer = l.deep,
        surfaceContainerHigh = l.raise,
        surfaceContainerHighest = l.focusRow,
    )

/** The Kids colours for the TV parts of the app */
private fun ColorScheme.withKids(k: KidsPalette): ColorScheme =
    copy(
        primary = k.accent,
        onPrimary = k.onAccent,
        border = k.accent,
        background = k.tray,
        onBackground = k.text,
        surface = k.tray,
        onSurface = k.text,
        surfaceVariant = k.panel,
        onSurfaceVariant = k.soft,
    )

/** The Kids colours for the phone-style (Material 3) parts of the app */
private fun androidx.compose.material3.ColorScheme.withKids(k: KidsPalette): androidx.compose.material3.ColorScheme =
    copy(
        primary = k.accent,
        onPrimary = k.onAccent,
        background = k.tray,
        onBackground = k.text,
        surface = k.tray,
        onSurface = k.text,
        surfaceVariant = k.panel,
        onSurfaceVariant = k.soft,
        surfaceContainer = k.panel,
        surfaceContainerHigh = k.panelHigh,
        surfaceContainerLow = k.tray,
        surfaceContainerHighest = k.panelHigh,
        surfaceContainerLowest = k.tray,
    )
