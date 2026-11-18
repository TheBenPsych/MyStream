package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Glowfin's menu icons (Ben's pick "Two-tone", ui-2): an outline with a soft see-through fill, on a 24 x 24
 * grid. [body] shapes get the soft fill and the outline, [lines] and [cuts] are outline only.
 */
enum class GlowfinIcon(
    val title: String,
    val body: List<String>,
    val lines: List<String> = emptyList(),
) {
    SEARCH("Search", listOf("M10.8 4.2a6.6 6.6 0 1 0 0 13.2a6.6 6.6 0 1 0 0-13.2z"), listOf("M15.7 15.7l4.6 4.6")),
    HOME("Home", listOf("M4 10.6 12 4l8 6.6V19a1 1 0 0 1-1 1h-4.6v-5.4H9.6V20H5a1 1 0 0 1-1-1z")),
    MOVIES(
        "Movies",
        listOf("M4 10.2h16v8.3a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5z", "M3.7 6.9 18.6 3.3l.9 3.5-14.9 3.6z"),
        listOf("M8.3 5.8l1.8 2.9", "M13 4.7l1.8 2.9"),
    ),
    SHOWS(
        "Shows",
        listOf("M5 7.6h14a2 2 0 0 1 2 2v7.6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9.6a2 2 0 0 1 2-2z"),
        listOf("M8.6 3.6 12 7l3.4-3.4"),
    ),

    /** A cherry blossom (ui-7): five round petals and a little middle */
    ANIME(
        "Anime",
        listOf(
            "M8.10 7.80a3.9 3.9 0 1 0 7.8 0a3.9 3.9 0 1 0 -7.8 0z",
            "M12.47 10.98a3.9 3.9 0 1 0 7.8 0a3.9 3.9 0 1 0 -7.8 0z",
            "M10.80 16.12a3.9 3.9 0 1 0 7.8 0a3.9 3.9 0 1 0 -7.8 0z",
            "M5.40 16.12a3.9 3.9 0 1 0 7.8 0a3.9 3.9 0 1 0 -7.8 0z",
            "M3.73 10.98a3.9 3.9 0 1 0 7.8 0a3.9 3.9 0 1 0 -7.8 0z",
        ),
        listOf("M12 10.6a1.8 1.8 0 1 0 0 3.6a1.8 1.8 0 1 0 0-3.6z"),
    ),
    LIVE(
        "Live TV",
        listOf("M12 9.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z"),
        listOf("M8 8a5.7 5.7 0 0 0 0 8", "M16 8a5.7 5.7 0 0 1 0 8", "M5.2 5.2a9.6 9.6 0 0 0 0 13.6", "M18.8 5.2a9.6 9.6 0 0 1 0 13.6"),
    ),
    MUSIC(
        "Music",
        listOf("M6.6 14.8a2.7 2.7 0 1 0 0 5.4a2.7 2.7 0 1 0 0-5.4z", "M16.6 12.8a2.7 2.7 0 1 0 0 5.4a2.7 2.7 0 1 0 0-5.4z"),
        listOf("M9.3 17.5V6.4l10-2.2v11.3"),
    ),
    GAMES(
        "Games",
        listOf(
            "M7.6 7.4h8.8a4.5 4.5 0 0 1 4.4 3.6l.9 4.7a2.4 2.4 0 0 1-4.1 2.1L15.6 16H8.4l-2 1.8a2.4 2.4 0 0 1-4.1-2.1l.9-4.7a4.5 4.5 0 0 1 4.4-3.6z",
        ),
        listOf("M8 10.4v3.4", "M6.3 12.1h3.4", "M15.6 11.1h.01", "M17.6 13.1h.01"),
    ),
    BOOKS(
        "Books",
        listOf("M3.4 5.4h5.6a3 3 0 0 1 3 3V20a2.2 2.2 0 0 0-2.2-2H3.4z", "M20.6 5.4H15a3 3 0 0 0-3 3V20a2.2 2.2 0 0 1 2.2-2h6.4z"),
    ),
    REQUESTS("Requests", listOf("M12 3.5a8.5 8.5 0 1 0 0 17a8.5 8.5 0 1 0 0-17z"), listOf("M12 8.3v7.4", "M8.3 12h7.4")),
    SETTINGS(
        "Settings",
        listOf(
            "M12.74 2.83L14.83 3.25L15.19 5.77L16.54 6.67L19.00 6.04L20.19 7.81L18.66 9.85L18.98 11.44L21.17 12.74L20.75 14.83" +
                "L18.23 15.19L17.33 16.54L17.96 19.00L16.19 20.19L14.15 18.66L12.56 18.98L11.26 21.17L9.17 20.75L8.81 18.23L7.46 17.33" +
                "L5.00 17.96L3.81 16.19L5.34 14.15L5.02 12.56L2.83 11.26L3.25 9.17L5.77 8.81L6.67 7.46L6.04 5.00L7.81 3.81L9.85 5.34" +
                "L11.44 5.02Z",
        ),
        listOf("M12 9.3a2.7 2.7 0 1 0 0 5.4a2.7 2.7 0 1 0 0-5.4z"),
    ),

    /** Phone bar: the rest of the menu */
    MORE("More", emptyList(), listOf("M5 12h.01", "M12 12h.01", "M19 12h.01")),
    ;

    /** Built once, white, so any colour can tint it (the soft fill keeps its see-through look) */
    val vector: ImageVector by lazy {
        val builder =
            ImageVector.Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
        // Each shape is moved so its outline sits exactly in the middle of the 24 x 24 box, so it looks centred
        // inside the round glow and the phone bar's pill (Ben, 2026-11-18)
        val (dx, dy) = CentreShift[this] ?: (0f to 0f)
        builder.addGroup(translationX = dx, translationY = dy)
        body.forEach { d ->
            builder.addPath(pathData = addPathNodes(d), fill = SolidColor(Color.White), fillAlpha = FILL_ALPHA)
        }
        (body + lines).forEach { d ->
            builder.addPath(
                pathData = addPathNodes(d),
                stroke = SolidColor(Color.White),
                strokeLineWidth = if (this == MORE) DOT_WIDTH else LINE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        builder.clearGroup()
        builder.build()
    }
}

/** How far each icon's drawing is off the middle of its box (measured from its outline), to move it back */
private val CentreShift: Map<GlowfinIcon, Pair<Float, Float>> =
    mapOf(
        GlowfinIcon.SEARCH to (-.25f to -.25f),
        GlowfinIcon.MOVIES to (.15f to .35f),
        GlowfinIcon.SHOWS to (0f to .6f),
        GlowfinIcon.MUSIC to (.4f to -.2f),
        GlowfinIcon.GAMES to (0f to -.97f),
        GlowfinIcon.BOOKS to (0f to -.7f),
    )

private const val FILL_ALPHA = .28f
private const val LINE_WIDTH = 1.8f
private const val DOT_WIDTH = 3.6f

/** Draws a [GlowfinIcon] in [color] */
@Composable
fun GlowfinIconImage(
    icon: GlowfinIcon,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val filter = remember(color) { ColorFilter.tint(color) }
    Image(
        imageVector = icon.vector,
        contentDescription = contentDescription,
        colorFilter = filter,
        modifier = modifier.size(size),
    )
}
