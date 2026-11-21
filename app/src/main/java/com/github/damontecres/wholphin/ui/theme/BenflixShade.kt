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
                0.3f to BenflixColors.Tray.copy(alpha = if (isTvKnown) .92f else .88f),
                // Overhaul 2026-11-20: the shade now reaches the end of the story lines (they ran onto bright pictures)
                0.5f to BenflixColors.Tray.copy(alpha = .7f),
                0.8f to Color.Transparent,
            )

    /**
     * Home / For you "Fade down" (Ben 2026-11-18): clear at the top, darker by the end of the top area ([brightUntil]
     * of the way down), then nearly dark (but never fully) to the bottom so the picture still shows faintly behind rows.
     */
    fun fadeDown(brightUntil: Float): Brush =
        Brush.verticalGradient(
            0f to Color.Transparent,
            brightUntil * 0.55f to BenflixColors.Tray.copy(alpha = .2f),
            brightUntil to BenflixColors.Tray.copy(alpha = .72f),
            (brightUntil + 0.25f).coerceAtMost(0.95f) to BenflixColors.Tray.copy(alpha = .85f),
            1f to BenflixColors.Tray.copy(alpha = .9f),
        )

    /** Wholphin-style top-right picture (Ben 2026-11-18): a strong fade from the left so the words and the menu stay clear */
    val portionSide: Brush
        get() =
            Brush.horizontalGradient(
                0f to BenflixColors.Tray,
                0.18f to BenflixColors.Tray.copy(alpha = .85f),
                0.45f to BenflixColors.Tray.copy(alpha = .25f),
                0.7f to Color.Transparent,
            )

    /** ...and its bottom edge melts into the page, so there's no hard line under it */
    val portionBottom: Brush
        get() =
            Brush.verticalGradient(
                0.5f to Color.Transparent,
                0.82f to BenflixColors.Tray.copy(alpha = .7f),
                1f to BenflixColors.Tray,
            )

    /** The fade into the page at the bottom of any big picture, starting [from] of the way down */
    fun bottom(from: Float = 0.4f): Brush =
        Brush.verticalGradient(
            from to Color.Transparent,
            1f to BenflixColors.Tray,
        )
}
