package com.github.damontecres.wholphin.ui.playback.overlay

/*
 * Modified from https://github.com/android/tv-samples
 *
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.ui.playback.calculateSeekAccelerationMultiplier
import com.github.damontecres.wholphin.ui.playback.isDpadLeft
import com.github.damontecres.wholphin.ui.playback.isDpadRight
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.time.Duration

/**
 * This is a seek bar which seeks by a percentage of the duration instead of a fixed amount of time
 *
 * For example, if [intervals] is 10, then each seek will be 10% of total media duration
 */
@Composable
fun SteppedSeekBarImpl(
    progress: Float,
    durationMs: Long,
    bufferedProgress: Float,
    onSeek: (Long) -> Unit,
    controllerViewState: ControllerViewState,
    modifier: Modifier = Modifier,
    intervals: Int = 10,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    var hasSeeked by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(progress) }
    val progressToUse = if (isFocused && hasSeeked) seekProgress else progress
    LaunchedEffect(isFocused) {
        if (!isFocused) hasSeeked = false
    }

    val offset = 1f / intervals

    val seek = { percent: Float ->
        onSeek((percent * durationMs).toLong())
    }

    SeekBarDisplay(
        enabled = enabled,
        progress = progressToUse,
        bufferedProgress = bufferedProgress,
        durationMs = durationMs,
        onLeft = { multiplier ->
            controllerViewState.pulseControls()
            seekProgress = (progressToUse - offset * multiplier).coerceAtLeast(0f)
            hasSeeked = true
            seek(seekProgress)
        },
        onRight = { multiplier ->
            controllerViewState.pulseControls()
            seekProgress = (progressToUse + offset * multiplier).coerceAtMost(1f)
            hasSeeked = true
            seek(seekProgress)
        },
        onTouchSeek = { percent ->
            controllerViewState.pulseControls()
            seek(percent)
        },
        interactionSource = interactionSource,
        modifier = modifier,
    )
}

/**
 * A read-only seek bar for showing progress without taking focus or handling D-Pad input.
 *
 * This is used by overlays where seeking is handled elsewhere and the bar is only a visual indicator.
 */
@Composable
fun StaticSeekBarImpl(
    progress: Float,
    durationMs: Long,
    bufferedProgress: Float,
    modifier: Modifier = Modifier,
    chapterMarks: List<Float> = emptyList(),
) {
    SeekBarDisplay(
        enabled = false,
        chapterMarks = chapterMarks,
        progress = progress,
        bufferedProgress = bufferedProgress,
        durationMs = durationMs,
        onLeft = {},
        onRight = {},
        interactionSource = remember { MutableInteractionSource() },
        modifier = modifier,
    )
}

/**
 * A seek (or scrubber) bar which seeks forward or back a fixed amount of time per move
 */
@OptIn(FlowPreview::class)
@Composable
fun IntervalSeekBarImpl(
    progress: Float,
    durationMs: Long,
    bufferedProgress: Float,
    onSeek: (Long) -> Unit,
    controllerViewState: ControllerViewState,
    seekBack: Duration,
    seekForward: Duration,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
    chapterMarks: List<Float> = emptyList(),
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    var hasSeeked by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableLongStateOf((progress * durationMs).toLong()) }
//    val progressToUse by remember { derivedStateOf { if (isFocused && hasSeeked) seekPositionMs else (progress * durationMs).toLong() } }
    val progressToUse =
        if (isFocused && hasSeeked) seekPositionMs else (progress * durationMs).toLong()

    LaunchedEffect(isFocused) {
        if (!isFocused) hasSeeked = false
    }

    SeekBarDisplay(
        enabled = enabled,
        progress = (progressToUse.toDouble() / durationMs).toFloat(),
        bufferedProgress = bufferedProgress,
        durationMs = durationMs,
        onLeft = { multiplier ->
            controllerViewState.pulseControls()
            seekPositionMs =
                (progressToUse - seekBack.inWholeMilliseconds * multiplier).coerceAtLeast(0L)
            hasSeeked = true
            onSeek(seekPositionMs)
        },
        onRight = { multiplier ->
            controllerViewState.pulseControls()
            seekPositionMs =
                (progressToUse + seekForward.inWholeMilliseconds * multiplier)
                    .coerceAtMost(durationMs)
            hasSeeked = true
            onSeek(seekPositionMs)
        },
        onTouchSeek = { percent ->
            controllerViewState.pulseControls()
            onSeek((percent * durationMs).toLong())
        },
        interactionSource = interactionSource,
        modifier = modifier,
        chapterMarks = chapterMarks,
    )
}

/**
 * Actually renders the seek bar. It has callbacks for when the user moves to the left or right
 *
 * @see IntervalSeekBarImpl
 * @see SteppedSeekBarImpl
 */
@Composable
private fun SeekBarDisplay(
    progress: Float,
    bufferedProgress: Float,
    durationMs: Long,
    onLeft: (Int) -> Unit,
    onRight: (Int) -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onTouchSeek: ((Float) -> Unit)? = null,
    chapterMarks: List<Float> = emptyList(),
) {
    val onSurface = MaterialTheme.colorScheme.onSurface

    val isFocused by interactionSource.collectIsFocusedAsState()
    var leftHandledByRepeat by remember { mutableStateOf(false) }
    var rightHandledByRepeat by remember { mutableStateOf(false) }
    val animatedIndicatorHeight by animateDpAsState(
        targetValue = 6.dp.times((if (isFocused) 2f else 1f)),
    )

    // Benflix on phones: tap or drag along the bar to jump to that spot.
    // While the finger is down (and shortly after, until the player catches up) the bar shows the finger's spot.
    val isTv = rememberIsTv()
    val touchEnabled = !isTv && enabled && onTouchSeek != null && durationMs > 0
    var touchProgress by remember { mutableStateOf<Float?>(null) }
    var touchReleases by remember { mutableIntStateOf(0) }
    LaunchedEffect(touchReleases) {
        if (touchReleases > 0) {
            delay(1500)
            touchProgress = null
        }
    }
    val shownProgress = touchProgress ?: progress
    val dragging = touchProgress != null

    Column(
        modifier =
            modifier.then(
                if (!touchEnabled) {
                    Modifier
                } else {
                    Modifier
                        .pointerInput(durationMs) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                val width = size.width.toFloat().coerceAtLeast(1f)
                                touchProgress = (down.position.x / width).coerceIn(0f, 1f)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    touchProgress = (change.position.x / width).coerceIn(0f, 1f)
                                    if (!change.pressed) break
                                }
                                touchProgress?.let { onTouchSeek?.invoke(it) }
                                touchReleases++
                            }
                        }
                        // A bigger area for the finger than the thin bar itself
                        .padding(vertical = 12.dp)
                },
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val barHeight = if (touchEnabled && dragging) 12.dp else animatedIndicatorHeight
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .padding(horizontal = 4.dp)
                    .onPreviewKeyEvent { event ->
                        if (!isDpadLeft(event) && !isDpadRight(event)) {
                            Timber.v("Ignoring %s", event)
                            return@onPreviewKeyEvent false
                        }
                        if (isDpadLeft(event)) {
                            when (event.type) {
                                KeyEventType.KeyDown -> {
                                    val repeatCount = event.nativeKeyEvent.repeatCount
                                    if (repeatCount > 0) {
                                        leftHandledByRepeat = true
                                        onLeft.invoke(
                                            calculateSeekAccelerationMultiplier(
                                                repeatCount = repeatCount,
                                                durationMs = durationMs,
                                            ),
                                        )
                                    } else {
                                        leftHandledByRepeat = false
                                    }
                                }

                                KeyEventType.KeyUp -> {
                                    if (!leftHandledByRepeat) {
                                        onLeft.invoke(1)
                                    }
                                    leftHandledByRepeat = false
                                }

                                else -> {
                                    return@onPreviewKeyEvent false
                                }
                            }
                            return@onPreviewKeyEvent true
                        } else {
                            when (event.type) {
                                KeyEventType.KeyDown -> {
                                    val repeatCount = event.nativeKeyEvent.repeatCount
                                    if (repeatCount > 0) {
                                        rightHandledByRepeat = true
                                        onRight.invoke(
                                            calculateSeekAccelerationMultiplier(
                                                repeatCount = repeatCount,
                                                durationMs = durationMs,
                                            ),
                                        )
                                    } else {
                                        rightHandledByRepeat = false
                                    }
                                }

                                KeyEventType.KeyUp -> {
                                    if (!rightHandledByRepeat) {
                                        onRight.invoke(1)
                                    }
                                    rightHandledByRepeat = false
                                }

                                else -> {
                                    return@onPreviewKeyEvent false
                                }
                            }
                            return@onPreviewKeyEvent true
                        }
                    }.focusable(enabled = enabled, interactionSource = interactionSource),
            onDraw = {
                // Simple player (Ben 2026-11-27): a plain white line with a white dot. Chapters show as small
                // gaps that cut the line into pieces. No colours, no glow, no blur.
                val yOffset = size.height.div(2)
                val gap = if (chapterMarks.isEmpty()) 0f else 3.dp.toPx()
                val corner = CornerRadius(size.height / 2)
                val playedTo = size.width * shownProgress.coerceIn(0f, 1f)
                val bufferedTo = size.width * bufferedProgress.coerceIn(0f, 1f)
                var from = 0f
                for (i in 0..chapterMarks.size) {
                    val to = if (i < chapterMarks.size) chapterMarks[i] else 1f
                    val x0 = size.width * from + if (i > 0) gap / 2 else 0f
                    val x1 = size.width * to - if (i < chapterMarks.size) gap / 2 else 0f
                    from = to
                    if (x1 <= x0) continue

                    fun piece(
                        until: Float,
                        color: Color,
                    ) {
                        val end = until.coerceAtMost(x1)
                        if (end > x0) {
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(x0, 0f),
                                size = Size(end - x0, size.height),
                                cornerRadius = corner,
                            )
                        }
                    }
                    piece(x1, onSurface.copy(alpha = .28f))
                    piece(bufferedTo, onSurface.copy(alpha = .3f))
                    piece(playedTo, Color.White)
                }
                // The dot: plain white, with a thin dark edge so it shows on the white line
                val knob = Offset(x = playedTo, y = yOffset)
                val knobRadius = size.height / 2 + 3.5.dp.toPx()
                drawCircle(color = Color(0x5906070E), radius = knobRadius + 1.dp.toPx(), center = knob)
                drawCircle(color = Color.White, radius = knobRadius, center = knob)
            },
        )
    }
}

/** Where each chapter starts, as a share of the whole video (the first one at 0 needs no cut) */
fun chapterMarksFor(
    chapters: List<com.github.damontecres.wholphin.data.model.Chapter>,
    durationMs: Long,
): List<Float> =
    if (durationMs <= 0 || chapters.size < 2) {
        emptyList()
    } else {
        chapters
            .map { it.position.inWholeMilliseconds.toFloat() / durationMs }
            .filter { it > 0.005f && it < 0.995f }
            .sorted()
    }

/** The name of the chapter playing at [positionMs], or null when the video has no named chapters */
fun chapterNameAt(
    chapters: List<com.github.damontecres.wholphin.data.model.Chapter>,
    positionMs: Long,
): String? =
    if (chapters.size < 2) {
        null
    } else {
        chapters
            .filter { it.position.inWholeMilliseconds <= positionMs }
            .maxByOrNull { it.position }
            ?.name
            ?.takeIf { it.isNotBlank() }
    }
