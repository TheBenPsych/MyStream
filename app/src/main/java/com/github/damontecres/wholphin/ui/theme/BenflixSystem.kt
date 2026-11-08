package com.github.damontecres.wholphin.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.damontecres.wholphin.ui.touch.isTvKnown

/**
 * The Benflix building blocks (polish round, Nov 2026). Every screen picks from these so the app
 * looks like one thing: 7 text sizes, 3 corner sizes plus pills, one speed for movement, and the
 * same page edges. TV sizes are a bit bigger, and nothing on a TV is smaller than 14.
 */
object BenflixType {
    /** Big name on a hero page (TV 40, phone 30) */
    val hero: TextUnit get() = if (isTvKnown) 40.sp else 30.sp

    /** Page titles like "Movies" (TV 30, phone 24) */
    val pageTitle: TextUnit get() = if (isTvKnown) 30.sp else 24.sp

    /** Row names like "Keep watching" (TV 22, phone 18) */
    val rowName: TextUnit get() = if (isTvKnown) 22.sp else 18.sp

    /** Lead lines like "1999 · 2h 16m · R" (TV 18, phone 16) */
    val lead: TextUnit get() = if (isTvKnown) 18.sp else 16.sp

    /** Normal reading text (TV 16, phone 15) */
    val body: TextUnit get() = if (isTvKnown) 16.sp else 15.sp

    /** Small helper text (TV 15, phone 13) */
    val small: TextUnit get() = if (isTvKnown) 15.sp else 13.sp

    /** Tags and tiny labels like "NEW" (TV 14, phone 12) */
    val tag: TextUnit get() = if (isTvKnown) 14.sp else 12.sp
}

/** The only corner sizes Benflix uses */
object BenflixShapes {
    /** Tags, chips, small bars */
    val Small = RoundedCornerShape(6.dp)

    /** Posters, tiles, cards */
    val Card = RoundedCornerShape(10.dp)

    /** Menus, side panels, phone sheets */
    val Panel = RoundedCornerShape(16.dp)

    /** Buttons */
    val Pill = RoundedCornerShape(percent = 50)
}

/** One speed for all movement: arrive in 0.18 s, leave in 0.12 s, same smooth ease */
object BenflixMotion {
    const val IN_MS = 180
    const val OUT_MS = 120
    val Ease = CubicBezierEasing(.2f, 0f, 0f, 1f)

    /** How everything arrives: 0.18 seconds, smooth ease */
    fun <T> enter(): FiniteAnimationSpec<T> = tween(IN_MS, easing = Ease)

    /** How everything leaves: 0.12 seconds, same ease */
    fun <T> exit(): FiniteAnimationSpec<T> = tween(OUT_MS, easing = Ease)
}

/** The same page edges everywhere. TV keeps 48 from the edge because some older TVs cut edges off. */
object BenflixSpace {
    val tvEdge = 48.dp
    val phoneEdge = 16.dp
    val tvRowGap = 30.dp
    val phoneRowGap = 22.dp
}
