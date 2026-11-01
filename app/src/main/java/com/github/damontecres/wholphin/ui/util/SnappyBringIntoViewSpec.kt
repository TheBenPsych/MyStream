package com.github.damontecres.wholphin.ui.util

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * How rows and pages slide to the focused tile: quick, with no bounce.
 *
 * This must be a spring. Compose re-starts the scroll animation from where it is on every frame, which
 * suits a spring, but turns a timed animation (a "tween") into a slow crawl that eases in over and over
 * again. That crawl was the slow scrolling between rows.
 */
val SnappyScrollAnimationSpec: AnimationSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 3000f)

/**
 * Keeps the [base] scrolling position rules, but slides there quickly instead of with a slow, floaty spring.
 *
 * On TV, pages that scroll up and down (Games, a movie's or show's page, collections, ...) move like
 * Home: when you go down to another row of tiles, that row slides up to the same spot near the top of
 * the page every time, instead of the page only creeping along once the selection reaches the bottom.
 * Small things like buttons and list lines keep the [base] rules, and rows that scroll sideways
 * (wider than [sidewaysFromPx]) are left alone.
 */
class SnappyBringIntoViewSpec(
    private val base: BringIntoViewSpec,
    private val isTv: Boolean = false,
    private val sidewaysFromPx: Float = Float.MAX_VALUE,
) : BringIntoViewSpec {
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float>
        get() = SnappyScrollAnimationSpec

    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        if (!isTv || containerSize <= 0f || containerSize >= sidewaysFromPx || size < containerSize * TILE_MIN) {
            return base.calculateScrollDistance(offset, size, containerSize)
        }
        // A tile (or a whole row of them): line it up just below the row's title, like Home
        return offset - containerSize * SPACE_ABOVE
    }

    private companion object {
        /** Anything at least this tall (as a share of the page) counts as a tile */
        const val TILE_MIN = .14f

        /** Room left above the tile for its row's title */
        const val SPACE_ABOVE = .1f
    }
}
