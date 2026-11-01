package com.github.damontecres.wholphin.ui.util

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.BringIntoViewSpec

/** How long rows and pages take to slide to the focused tile. Netflix feels about this quick. */
val SnappyScrollAnimationSpec: AnimationSpec<Float> = tween(durationMillis = 200, easing = FastOutSlowInEasing)

/**
 * Keeps the [base] scrolling position rules, but slides there quickly instead of with a slow, floaty spring.
 *
 * On TV, pages that scroll up and down (Games, a show's details, collections, ...) also scroll early
 * and in one decisive step: once the selection leaves the comfortable middle of the screen, the page
 * moves so the selection sits about a third of the way down. Without this the page only crept along
 * once the selection had already reached the bottom edge, so it always felt a step behind.
 * Rows that scroll sideways (wider than [sidewaysFromPx]) keep the [base] rules.
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
        if (!isTv || containerSize <= 0f || containerSize >= sidewaysFromPx) {
            return base.calculateScrollDistance(offset, size, containerSize)
        }
        val comfortTop = containerSize * COMFORT_TOP
        val comfortBottom = containerSize * COMFORT_BOTTOM
        val end = offset + size
        // Already comfortably on screen: stay put
        if (offset >= comfortTop && end <= comfortBottom) return 0f
        // Something taller than most of the screen: line its top up with the top of the comfortable area
        if (size > containerSize * (COMFORT_BOTTOM - PIVOT)) return offset - comfortTop
        return offset - containerSize * PIVOT
    }

    private companion object {
        const val COMFORT_TOP = .1f
        const val COMFORT_BOTTOM = .85f
        const val PIVOT = .3f
    }
}
