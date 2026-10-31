package com.github.damontecres.wholphin.ui.util

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.BringIntoViewSpec

/** How long rows and pages take to slide to the focused tile. Netflix feels about this quick. */
val SnappyScrollAnimationSpec: AnimationSpec<Float> = tween(durationMillis = 200, easing = FastOutSlowInEasing)

/**
 * Keeps the [base] scrolling position rules, but slides there quickly instead of with a slow, floaty spring
 */
class SnappyBringIntoViewSpec(
    private val base: BringIntoViewSpec,
) : BringIntoViewSpec {
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float>
        get() = SnappyScrollAnimationSpec

    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float = base.calculateScrollDistance(offset, size, containerSize)
}
