package com.github.damontecres.wholphin.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/*
 * The background behind "Who's watching?": a still wall of the server's posters.
 */

// ---------------------------------------------------------------- poster wall

/**
 * A still wall of exactly 12 posters, dimmed so the profiles stand out.
 *
 * Benflix (Ben's pick, 2026-11-04): 12 posters and no repeats. TVs (wide) get 6 across and 2 down,
 * tall phone screens 3 across and 4 down, each poster filling its share of the screen. Nothing moves,
 * so the screen is quick on every TV. The profiles show first; the wall fills in behind them.
 */
@Composable
fun PosterWall(
    posters: List<String>,
    modifier: Modifier = Modifier,
) {
    if (posters.isEmpty()) return
    val wall = remember(posters) { posters.take(POSTER_WALL_MAX) }
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val wide = maxWidth > maxHeight
        val cols = if (wide) 6 else 3
        val rows = (POSTER_WALL_MAX + cols - 1) / cols
        val tileW = maxWidth / cols
        val tileH = maxHeight / rows
        Column(Modifier.fillMaxSize()) {
            repeat(rows) { r ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(cols) { c ->
                        val url = wall.getOrNull(r * cols + c)
                        Box(
                            Modifier
                                .size(tileW, tileH)
                                .background(com.github.damontecres.wholphin.ui.theme.Glowfin.Raise),
                        ) {
                            if (url != null) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }
        // Dim everything, darker toward the edges, so the profiles pop
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val brush =
                        Brush.radialGradient(
                            0f to Color.Black.copy(alpha = .72f),
                            1f to Color.Black.copy(alpha = .95f),
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.maxDimension * .62f,
                        )
                    onDrawBehind { drawRect(brush) }
                },
        )
    }
}

/** How many different posters the wall uses */
private const val POSTER_WALL_MAX = 12
