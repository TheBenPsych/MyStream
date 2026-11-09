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

    // A classic controller: rounded body with two grips, a plus pad on the left and two buttons on the right
    PAD(
        "M8 6h8a5.5 5.5 0 0 1 5.4 4.5l.8 4.6a2.8 2.8 0 0 1-5 2.2L15.5 15.5h-7l-1.7 1.8a2.8 2.8 0 0 1-5-2.2l.8-4.6A5.5 5.5 0 0 1 8 6z",
        "M8 9v3.5M6.25 10.75h3.5",
        "M16 9.5h.01M17.6 11.9h.01",
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

    // A screen with a play arrow: the film strip was too busy at button size
    TRAILER(
        "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z",
        "M10 9.2v5.6l4.6-2.8z",
    ),
    ARROW("M5 12h14M13 6l6 6-6 6"),
    PLUS("M12 5v14M5 12h14"),
    BELL("M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z", "M10 20a2 2 0 0 0 4 0"),
    MORE("M6 12h.01M12 12h.01M18 12h.01"),
    PHONE(
        "M8 2.5h8a1.5 1.5 0 0 1 1.5 1.5v16a1.5 1.5 0 0 1-1.5 1.5H8A1.5 1.5 0 0 1 6.5 20V4A1.5 1.5 0 0 1 8 2.5z",
        "M11 18.5h2",
    ),
    THUMB("M7 11v9H4v-9zM7 11l4-7a2 2 0 0 1 2.5 2.5L13 10h5.5a2 2 0 0 1 2 2.3l-1.2 6A2 2 0 0 1 17.3 20H7"),

    // Polish round (Nov 2026): the rest of the app's icons in the same rounded line style
    TRASH("M4 7h16M9 7V4.5h6V7", "M6 7l1 13h10l1-13", "M10 11v5M14 11v5"),
    MORE_VERT("M12 6h.01M12 12h.01M12 18h.01"),
    REFRESH("M19.5 12a7.5 7.5 0 1 1-2.2-5.3", "M19.5 4.5v4h-4"),
    BACK_ARROW("M19 12H5M11 6l-6 6 6 6"),
    WARNING("M12 4l9 16H3z", "M12 10v4M12 17h.01"),
    STAR("M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8-5.2-2.8-5.2 2.8 1-5.8-4.3-4.1 5.9-.9z"),
    PERSON("M8 8a4 4 0 1 0 8 0a4 4 0 1 0-8 0z", "M4.5 20a7.5 7.5 0 0 1 15 0"),
    INFO("M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0z", "M12 11v5M12 8h.01"),
    PENCIL("M4 20h4L19 9l-4-4L4 16z", "M13.5 6.5l4 4"),
    CLOSE("M6 6l12 12M18 6L6 18"),
    LIST("M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01"),
    CHEVRON("M9 6l6 6-6 6"),
    MENU("M4 7h16M4 12h16M4 17h16"),
    CALENDAR("M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z", "M4 10h16M8 3v4M16 3v4"),
    SHARE(
        "M4 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0z",
        "M16 6a2 2 0 1 0 4 0a2 2 0 1 0-4 0z",
        "M16 18a2 2 0 1 0 4 0a2 2 0 1 0-4 0z",
        "M7.8 11l8.4-4M7.8 13l8.4 4",
    ),
    LOCK("M6.5 11h11a1 1 0 0 1 1 1v7a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1v-7a1 1 0 0 1 1-1z", "M8.5 11V8a3.5 3.5 0 0 1 7 0v3"),
    HOME("M4 11l8-7 8 7v8.5a.5.5 0 0 1-.5.5H15v-6H9v6H4.5a.5.5 0 0 1-.5-.5z"),

    // Music (Nov 2026)
    NOTE("M9 17V6l11-2.5V15", "M4 17a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0z", "M15 15a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0z"),
    HEART("M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"),
    SHUFFLE(
        "M3 7h3c5 0 7 10 12 10h3M3 17h3c2 0 3.3-1.6 4.6-3.6M14 8.6C15 7.6 16.3 7 18 7h3",
        "M18 4l3 3-3 3M18 14l3 3-3 3",
    ),
    PAUSE("M8 5.5v13M16 5.5v13"),
    PREVIOUS("M6 5.5v13", "M18 5.5v13l-9-6.5z"),
    NEXT("M18 5.5v13", "M6 5.5v13l9-6.5z"),
    STOP("M7 6h10a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1z"),
    WRENCH("M14.5 4.5a4.5 4.5 0 0 0 5 5.9L11 19a2.1 2.1 0 0 1-3-3l8.6-8.5a4.5 4.5 0 0 0-2.1-3z"),
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

/**
 * The play arrow for Play buttons: the same rounded triangle as [BenflixIcon.PLAY], filled in, so it
 * still reads as "Play" from across the room
 */
val BenflixPlayFilled: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "PLAY_FILLED",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes("M8 5.5v13l10.5-6.5z"),
            fill = SolidColor(Color.White),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
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

/** A filled heart, for "Liked" */
val BenflixHeartFilled: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "HEART_FILLED",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes("M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"),
            fill = SolidColor(Color.White),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
}
