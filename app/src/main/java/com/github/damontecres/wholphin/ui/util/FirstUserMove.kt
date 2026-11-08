package com.github.damontecres.wholphin.ui.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Calls [onMove] when the person presses a remote button or touches the screen inside this page.
 * Doors use it to keep you on the first card of the top row while rows are still loading in
 * (Ben: doors opened on Top 10 or a lower row because rows arrived above the one that had focus),
 * and stop doing that once you've started moving around yourself.
 */
fun Modifier.onFirstUserMove(onMove: () -> Unit): Modifier =
    this
        .onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown) onMove()
            false
        }.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                onMove()
            }
        }
