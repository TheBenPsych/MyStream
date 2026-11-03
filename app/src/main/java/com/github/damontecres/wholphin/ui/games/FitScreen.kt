package com.github.damontecres.wholphin.ui.games

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Makes a game screen always fit.
 *
 * TV: a remote can't scroll to text that's below the bottom of the screen, so if the screen's
 * content is taller than the TV, it's shrunk just enough to fit (never grown). Works on any TV size.
 * Phone: the content scrolls like a normal page.
 */
@Composable
fun FitScreen(
    isTv: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (isTv) {
        ScaleToFit(modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.fillMaxWidth()) { content() }
        }
    } else {
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            content()
        }
    }
}

/** Measures the content at its natural height and shrinks it (from the top centre) if it's taller than the space */
@Composable
private fun ScaleToFit(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeable =
            measurables.first().measure(
                constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
            )
        val bounded = constraints.hasBoundedHeight
        val scale =
            if (bounded && placeable.height > constraints.maxHeight && placeable.height > 0) {
                constraints.maxHeight.toFloat() / placeable.height
            } else {
                1f
            }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
        val height = if (bounded) constraints.maxHeight else placeable.height
        layout(width, height) {
            placeable.placeWithLayer(x = (width - placeable.width) / 2, y = 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
        }
    }
}

/**
 * How tall a picture can be on a TV game screen so the question, the answers and the
 * "Right!" line still fit underneath: a share of the screen's height.
 */
@Composable
fun tvPictureHeight(share: Float): Dp = (LocalConfiguration.current.screenHeightDp * share).dp
