package com.github.damontecres.wholphin.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Typography
import com.github.damontecres.wholphin.R

/** Benflix's clean, round font (Outfit), used for all text; Kids get the rounder Fredoka (Ben: kc-p-letters) */
val BenflixFont: FontFamily get() = if (KidsLook.on) KidsFont else OutfitFont

private val OutfitFont =
    FontFamily(
        Font(R.font.outfit_regular, FontWeight.Normal),
        Font(R.font.outfit_medium, FontWeight.Medium),
        Font(R.font.outfit_semibold, FontWeight.SemiBold),
        Font(R.font.outfit_bold, FontWeight.Bold),
        Font(R.font.outfit_extrabold, FontWeight.ExtraBold),
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

/** Tall movie-poster letters (Bebas Neue). Only for the giant Top 10 numbers; everything else uses Outfit */
val BenflixTitleFont = FontFamily(Font(R.font.bebas_neue, FontWeight.Normal))

private fun TextStyle.benflix() = copy(fontFamily = BenflixFont)

/** Outfit at one of the shared Benflix sizes ([BenflixType]), with comfortable line spacing */
private fun TextStyle.benflix(size: androidx.compose.ui.unit.TextUnit) =
    copy(fontFamily = BenflixFont, fontSize = size, lineHeight = size * 1.25f)

/** Built when the theme starts (not at load), so TV or phone sizes are already known */
fun appTypography() =
    Typography().let {
        Typography(
            displayLarge = it.displayLarge.benflix(),
            displayMedium = it.displayMedium.benflix(),
            displaySmall = it.displaySmall.benflix(BenflixType.hero),
            headlineLarge = it.headlineLarge.benflix(BenflixType.hero),
            headlineMedium = it.headlineMedium.benflix(BenflixType.pageTitle),
            headlineSmall = it.headlineSmall.benflix(BenflixType.pageTitle),
            titleLarge = it.titleLarge.benflix(BenflixType.rowName),
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
            displayLarge = it.displayLarge.benflix(),
            displayMedium = it.displayMedium.benflix(),
            displaySmall = it.displaySmall.benflix(BenflixType.hero),
            headlineLarge = it.headlineLarge.benflix(BenflixType.hero),
            headlineMedium = it.headlineMedium.benflix(BenflixType.pageTitle),
            headlineSmall = it.headlineSmall.benflix(BenflixType.pageTitle),
            titleLarge = it.titleLarge.benflix(BenflixType.rowName),
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
