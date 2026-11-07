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
 * The redesign's simple line icons (24 x 24, rounded 2.2 strokes), drawn from the approved sheets.
 * They take the colour they're given, so the same icon works on a bright door and on the dark tray.
 */
enum class BenflixIcon(
    vararg val paths: String,
) {
    FILM(
        "M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
        "M7 4v16M17 4v16M3 9h4M3 15h4M17 9h4M17 15h4",
    ),
    TV(
        "M5 5h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z",
        "M8 21h8M12 17v4",
    ),
    LIVE(
        "M10 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0z",
        "M7.8 7.8a6 6 0 0 0 0 8.4M16.2 7.8a6 6 0 0 1 0 8.4M4.9 4.9a10 10 0 0 0 0 14.2M19.1 4.9a10 10 0 0 1 0 14.2",
    ),
    PAD(
        "M7 8h10a4 4 0 0 1 4 4v1.5a3 3 0 0 1-5.4 1.8L14.5 14h-5l-1.1 1.3A3 3 0 0 1 3 13.5V12a4 4 0 0 1 4-4z",
        "M8 10.5v3M6.5 12h3",
    ),
    SEARCH("M4 10.5a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0-13 0z", "M16 16l5 5"),
    DOWN("M6 9l6 6 6-6"),
    REQUESTS("M4 13l2-8h12l2 8v6H4z", "M4 13h5l1 2h4l1-2h5"),
    STATS("M5 20V11M12 20V5M19 20v-6"),
    GEAR(
        "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0z",
        "M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1",
    ),
    SERVER(
        "M5.5 4h13a1.5 1.5 0 0 1 1.5 1.5v4a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 9.5v-4A1.5 1.5 0 0 1 5.5 4z",
        "M5.5 13h13a1.5 1.5 0 0 1 1.5 1.5v4a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5v-4A1.5 1.5 0 0 1 5.5 13z",
        "M8 7.5h.01M8 16.5h.01",
    ),
    HELP("M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0z", "M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.6M12 17h.01"),
    SIGN_OUT("M10 4H5v16h5M15 8l4 4-4 4M19 12H9"),
    SWITCH("M16 3l4 4-4 4M20 7H8M8 21l-4-4 4-4M4 17h12"),
    DOWNLOAD("M12 4v11M7 10l5 5 5-5M5 20h14"),
    FLAG("M5 21V4M5 4h11l-2 4 2 4H5"),
    BACK("M15 6l-6 6 6 6"),
    CHECK("M5 12.5l4.5 4.5L19 7"),
    PLAY("M8 5.5v13l10.5-6.5z"),
    ARROW("M5 12h14M13 6l6 6-6 6"),
    PLUS("M12 5v14M5 12h14"),
    MORE("M6 12h.01M12 12h.01M18 12h.01"),
    THUMB("M7 11v9H4v-9zM7 11l4-7a2 2 0 0 1 2.5 2.5L13 10h5.5a2 2 0 0 1 2 2.3l-1.2 6A2 2 0 0 1 17.3 20H7"),
    ;

    /** Built once per icon */
    val vector: ImageVector by lazy {
        val builder =
            ImageVector.Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
        paths.forEach { data ->
            builder.addPath(
                pathData = addPathNodes(data),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        builder.build()
    }
}

/** Draws a [BenflixIcon] in [color] */
@Composable
fun BenflixIconImage(
    icon: BenflixIcon,
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
