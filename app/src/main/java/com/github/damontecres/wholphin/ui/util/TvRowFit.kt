package com.github.damontecres.wholphin.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * Ben 2026-11-20: on smaller TVs the Games and Books rows were cut off at the bottom. TVs don't all give the app
 * the same amount of room: most report 960x540 "dp" (Android's screen-size units), but some smaller or older TVs
 * report less height, and a bigger text size in the TV's settings makes the row names taller too. So on TV the
 * pages work out how tall the cards can be from the room that's really there, so the row you're on (its name and
 * the cards) always shows whole under the top area, with the top of the next row peeking in.
 *
 * Unspecified means "no limit" (phones, browsers and pages that don't set it).
 */
val LocalTvRowFit = staticCompositionLocalOf { Dp.Unspecified }

/** How much of the next row peeks in under the row you're on */
private val PEEK = 28.dp

/** Room for one line of a row name, as a share of its size (Outfit sits a little tall) */
private const val NAME_LINE = 1.4f

/** Smallest a card gets, so it's still easy to read on tiny screens (the page scrolls if even that won't fit) */
private val MIN_CARD = 88.dp

/**
 * The tallest a card can be on this TV.
 *
 * @param rowsHeight the height left for the rows (screen height minus the header, tabs and top area)
 * @param rowName the row name's text size (the TV's text size setting is counted in)
 * @param around everything else in one row besides the name and the card: gaps and padding
 */
@Composable
fun tvRowCardFit(
    rowsHeight: Dp,
    rowName: TextUnit,
    around: Dp,
): Dp {
    val name = with(LocalDensity.current) { rowName.toDp() } * NAME_LINE
    return (rowsHeight - name - around - PEEK).coerceAtLeast(MIN_CARD)
}

/** This height, made no taller than [fit] when a fit is set */
fun Dp.fitTo(fit: Dp): Dp = if (fit == Dp.Unspecified) this else coerceAtMost(fit)
