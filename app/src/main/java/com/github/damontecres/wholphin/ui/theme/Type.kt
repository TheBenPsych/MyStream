package com.github.damontecres.wholphin.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Typography
import com.github.damontecres.wholphin.R

/** Benflix's clean, round font (Outfit), used for all text */
val BenflixFont =
    FontFamily(
        Font(R.font.outfit_regular, FontWeight.Normal),
        Font(R.font.outfit_medium, FontWeight.Medium),
        Font(R.font.outfit_semibold, FontWeight.SemiBold),
        Font(R.font.outfit_bold, FontWeight.Bold),
    )

/** Tall movie-poster style letters (Bebas Neue) for big titles */
val BenflixTitleFont = FontFamily(Font(R.font.bebas_neue, FontWeight.Normal))

private fun TextStyle.benflix() = copy(fontFamily = BenflixFont)

val AppTypography =
    Typography().let {
        Typography(
            displayLarge = it.displayLarge.benflix(),
            displayMedium = it.displayMedium.benflix(),
            displaySmall = it.displaySmall.benflix(),
            headlineLarge = it.headlineLarge.benflix(),
            headlineMedium = it.headlineMedium.benflix(),
            headlineSmall = it.headlineSmall.benflix(),
            titleLarge = it.titleLarge.benflix(),
            titleMedium = it.titleMedium.benflix(),
            titleSmall = it.titleSmall.benflix(),
            bodyLarge = it.bodyLarge.benflix(),
            bodyMedium = it.bodyMedium.benflix(),
            bodySmall = it.bodySmall.benflix(),
            labelLarge = it.labelLarge.benflix(),
            labelMedium = it.labelMedium.benflix(),
            labelSmall = it.labelSmall.benflix(),
        )
    }

/** The same font for the phone-style (Material 3) parts of the app */
val AppMaterialTypography =
    androidx.compose.material3.Typography().let {
        androidx.compose.material3.Typography(
            displayLarge = it.displayLarge.benflix(),
            displayMedium = it.displayMedium.benflix(),
            displaySmall = it.displaySmall.benflix(),
            headlineLarge = it.headlineLarge.benflix(),
            headlineMedium = it.headlineMedium.benflix(),
            headlineSmall = it.headlineSmall.benflix(),
            titleLarge = it.titleLarge.benflix(),
            titleMedium = it.titleMedium.benflix(),
            titleSmall = it.titleSmall.benflix(),
            bodyLarge = it.bodyLarge.benflix(),
            bodyMedium = it.bodyMedium.benflix(),
            bodySmall = it.bodySmall.benflix(),
            labelLarge = it.labelLarge.benflix(),
            labelMedium = it.labelMedium.benflix(),
            labelSmall = it.labelSmall.benflix(),
        )
    }
