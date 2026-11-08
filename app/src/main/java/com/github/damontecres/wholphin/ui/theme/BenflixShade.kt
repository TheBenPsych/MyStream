package com.github.damontecres.wholphin.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.github.damontecres.wholphin.ui.touch.isTvKnown

/**
 * Approved polish idea "one shade rule so words never sit on a bright picture": every big picture gets
 * the same dark fade on the side where the words are, a little stronger on TV (read from across the
 * room), and the same fade into the page at the bottom.
 */
object BenflixShade {
    /** A picture filling the whole width with words on the left (movie, show, request and game pages) */
    val heroSide: Brush
        get() =
            if (isTvKnown) {
                Brush.horizontalGradient(
                    0f to BenflixColors.Tray.copy(alpha = .96f),
                    0.4f to BenflixColors.Tray.copy(alpha = .78f),
                    0.75f to BenflixColors.Tray.copy(alpha = .2f),
                    1f to Color.Transparent,
                )
            } else {
                Brush.horizontalGradient(
                    0f to BenflixColors.Tray.copy(alpha = .92f),
                    0.4f to BenflixColors.Tray.copy(alpha = .68f),
                    0.75f to BenflixColors.Tray.copy(alpha = .14f),
                    1f to Color.Transparent,
                )
            }

    /** A picture on the right of a top area, with the words beside it (door pages, Requests, Games) */
    val topAreaSide: Brush
        get() =
            Brush.horizontalGradient(
                0f to BenflixColors.Tray,
                0.3f to BenflixColors.Tray.copy(alpha = if (isTvKnown) .9f else .85f),
                0.7f to Color.Transparent,
            )

    /** The fade into the page at the bottom of any big picture, starting [from] of the way down */
    fun bottom(from: Float = 0.4f): Brush =
        Brush.verticalGradient(
            from to Color.Transparent,
            1f to BenflixColors.Tray,
        )
}
