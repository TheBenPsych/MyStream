package com.github.damontecres.wholphin.ui.touch

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Benflix on phones: the screens are designed for a TV, so on a phone everything is
 * scaled to the phone's width so it fits, and kept clear of the
 * status bar / notch. TVs are left untouched.
 */
@Composable
fun PhoneFit(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isTv =
        remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
    val smallest = LocalConfiguration.current.smallestScreenWidthDp
    if (isTv || smallest <= 0 || smallest >= PHONE_DESIGN_DP) {
        content()
        return
    }
    val density = LocalDensity.current
    // Scales with the phone: a bigger phone gets bigger text and buttons
    val scale = smallest.toFloat() / PHONE_DESIGN_DP
    CompositionLocalProvider(
        LocalDensity provides Density(density.density * scale, density.fontScale),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            content()
        }
    }
}

/** The phone width the layout is sized for; smaller phones shrink it a little, bigger ones don't */
private const val PHONE_DESIGN_DP = 440

/** Dialog width: [tvFraction] of the screen normally, but nearly full width on a tall (portrait) phone. */
@Composable
fun dialogWidth(tvFraction: Float): Float {
    val config = LocalConfiguration.current
    return if (config.screenWidthDp < config.screenHeightDp) PORTRAIT_FRACTION else tvFraction
}

private const val PORTRAIT_FRACTION = .92f

/** True on Android TV / Google TV / Fire TV, false on phones and tablets */
@Composable
fun rememberIsTv(): Boolean {
    val context = LocalContext.current
    return remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
}

/** How much of the width a details page's text uses: a narrow column on TV, the full width on phones */
@Composable
fun detailsTextFraction(): Float = if (rememberIsTv()) .6f else 1f
