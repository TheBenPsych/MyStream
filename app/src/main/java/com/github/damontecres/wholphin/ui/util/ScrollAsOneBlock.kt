package com.github.damontecres.wholphin.ui.util

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import com.github.damontecres.wholphin.ui.touch.rememberIsTv

/**
 * Benflix (TV): a page's top part (title, info, episodes, buttons) scrolls as one block.
 *
 * Before, moving along the buttons sent the page two different "show this" requests at once: the
 * focused button asked to be moved part way up the screen, while the page asked to show its whole top
 * part. The two fought, so the page jumped up and down with every press.
 *
 * Now every request from inside this block asks for the same thing: the block from its top (as much
 * as fits on screen, always including whatever is focused). Pressing left and right along a row in the
 * block leaves the page still. Phones scroll by touch, so nothing changes there.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.scrollAsOneBlock(): Modifier {
    if (!rememberIsTv()) return this
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val responder = remember(windowHeight) { OneBlockResponder(windowHeight * MAX_SHARE) }
    return this
        .onSizeChanged { responder.size = it }
        .bringIntoViewResponder(responder)
}

/** The most of the screen's height the block may ask for, leaving room for the top menu bar */
private const val MAX_SHARE = .7f

@OptIn(ExperimentalFoundationApi::class)
private class OneBlockResponder(
    private val maxHeightPx: Float,
) : BringIntoViewResponder {
    var size: IntSize = IntSize.Zero

    override fun calculateRectForParent(localRect: Rect): Rect {
        val height = size.height.toFloat()
        if (height <= 0f || maxHeightPx <= 0f) return localRect
        return if (localRect.bottom <= maxHeightPx) {
            // Everything up to the focused thing fits: always ask for the same top part of the block
            Rect(localRect.left, 0f, localRect.right, minOf(height, maxHeightPx))
        } else {
            // A very tall block: show the focused thing with as much of the block above it as fits
            Rect(localRect.left, localRect.bottom - maxHeightPx, localRect.right, localRect.bottom)
        }
    }

    override suspend fun bringChildIntoView(localRect: () -> Rect?) {
        // Nothing to scroll here: the page around the block does the scrolling
    }
}
