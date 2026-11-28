package com.github.damontecres.wholphin.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Typography
import com.github.damontecres.wholphin.R

/**
 * Glowfin's letters (Ben 2026-11-27): Lexend everywhere, for reading and for titles, in every theme, because
 * it's much easier to read from the couch. Kids keep the rounder Fredoka (Ben: kc-p-letters). The logo keeps
 * its own letters (BenflixLogoMark).
 */
val BenflixFont: FontFamily get() = if (KidsLook.on) KidsFont else LexendFont

/** Page titles, row names and big names: the same Lexend (kept as its own name so a theme could differ later) */
val GlowfinDisplayFont: FontFamily get() = BenflixFont

private val LexendFont =
    FontFamily(
        Font(R.font.glowfin_lexend_light, FontWeight.Light),
        Font(R.font.glowfin_lexend_regular, FontWeight.Normal),
        Font(R.font.glowfin_lexend_medium, FontWeight.Medium),
        Font(R.font.glowfin_lexend_semibold, FontWeight.SemiBold),
        Font(R.font.glowfin_lexend_bold, FontWeight.Bold),
        Font(R.font.glowfin_lexend_bold, FontWeight.ExtraBold),
    )

/** Round, friendly letters for Kids (Fredoka; it has no extra-bold, so that uses bold) */
private val KidsFont =
    FontFamily(
        Font(R.font.kids_fredoka_regular, FontWeight.Normal),
        Font(R.font.kids_fredoka_medium, FontWeight.Medium),
        Font(R.font.kids_fredoka_semibold, FontWeight.SemiBold),
        Font(R.font.kids_fredoka_bold, FontWeight.Bold),
        Font(R.font.kids_fredoka_bold, FontWeight.ExtraBold),
    )

private fun TextStyle.benflix() = copy(fontFamily = BenflixFont)

/** Lexend at one of the shared Benflix sizes ([BenflixType]), with comfortable line spacing */
private fun TextStyle.benflix(size: androidx.compose.ui.unit.TextUnit) =
    copy(fontFamily = BenflixFont, fontSize = size, lineHeight = size * 1.25f)

/** Lexend at one of the shared sizes, for titles and row names */
private fun TextStyle.display(size: androidx.compose.ui.unit.TextUnit) =
    copy(fontFamily = GlowfinDisplayFont, fontSize = size, lineHeight = size * 1.2f)

private fun TextStyle.display() = copy(fontFamily = GlowfinDisplayFont)

/** Built when the theme starts (not at load), so TV or phone sizes are already known */
fun appTypography() =
    Typography().let {
        Typography(
            displayLarge = it.displayLarge.display(),
            displayMedium = it.displayMedium.display(),
            displaySmall = it.displaySmall.display(BenflixType.hero),
            headlineLarge = it.headlineLarge.display(BenflixType.hero),
            headlineMedium = it.headlineMedium.display(BenflixType.pageTitle),
            headlineSmall = it.headlineSmall.display(BenflixType.pageTitle),
            titleLarge = it.titleLarge.display(BenflixType.rowName),
            titleMedium = it.titleMedium.benflix(BenflixType.lead),
            titleSmall = it.titleSmall.benflix(BenflixType.body),
            bodyLarge = it.bodyLarge.benflix(BenflixType.body),
            bodyMedium = it.bodyMedium.benflix(BenflixType.small),
            bodySmall = it.bodySmall.benflix(BenflixType.tag),
            labelLarge = it.labelLarge.benflix(BenflixType.small),
            labelMedium = it.labelMedium.benflix(BenflixType.tag),
            labelSmall = it.labelSmall.benflix(BenflixType.tag),
        )
    }

/** The same font for the phone-style (Material 3) parts of the app */
fun appMaterialTypography() =
    androidx.compose.material3.Typography().let {
        androidx.compose.material3.Typography(
            displayLarge = it.displayLarge.display(),
            displayMedium = it.displayMedium.display(),
            displaySmall = it.displaySmall.display(BenflixType.hero),
            headlineLarge = it.headlineLarge.display(BenflixType.hero),
            headlineMedium = it.headlineMedium.display(BenflixType.pageTitle),
            headlineSmall = it.headlineSmall.display(BenflixType.pageTitle),
            titleLarge = it.titleLarge.display(BenflixType.rowName),
            titleMedium = it.titleMedium.benflix(BenflixType.lead),
            titleSmall = it.titleSmall.benflix(BenflixType.body),
            bodyLarge = it.bodyLarge.benflix(BenflixType.body),
            bodyMedium = it.bodyMedium.benflix(BenflixType.small),
            bodySmall = it.bodySmall.benflix(BenflixType.tag),
            labelLarge = it.labelLarge.benflix(BenflixType.small),
            labelMedium = it.labelMedium.benflix(BenflixType.tag),
            labelSmall = it.labelSmall.benflix(BenflixType.tag),
        )
    }
