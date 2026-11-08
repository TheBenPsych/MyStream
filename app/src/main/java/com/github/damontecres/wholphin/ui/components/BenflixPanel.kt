package com.github.damontecres.wholphin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixMotion
import com.github.damontecres.wholphin.ui.touch.Dialog
import com.github.damontecres.wholphin.ui.touch.rememberIsTv

/** Where a Benflix panel sits: a side panel on the right on TV, a sheet from the bottom on phones */
private val TvPanelShape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
private val PhoneSheetShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

/**
 * Every Benflix pop-up uses this (polish round, Nov 2026). On TV it slides in from the right as a full
 * height panel with the page dimmed behind it (no blur, so it stays quick on older TVs). On phones it
 * slides up from the bottom with a grab handle; swipe it down or tap outside to close.
 *
 * The caller's [properties] are kept (Back to close and so on); only the size rules change so the
 * panel can sit at the edge of the screen.
 */
@Composable
fun BenflixPanelDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val isTv = rememberIsTv()
    Dialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(
                dismissOnBackPress = properties.dismissOnBackPress,
                dismissOnClickOutside = properties.dismissOnClickOutside,
                securePolicy = properties.securePolicy,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = properties.decorFitsSystemWindows,
            ),
    ) {
        val shown = remember { MutableTransitionState(false).apply { targetState = true } }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Tapping the dimmed page closes the panel (phones). It must never take the remote's
            // focus: a menu opened by holding OK has its rows switched off for a moment, so focus
            // could land here and letting go of OK would close the menu straight away.
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (properties.dismissOnClickOutside) {
                            Modifier
                                .focusProperties { canFocus = false }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onDismissRequest,
                                )
                        } else {
                            Modifier
                        },
                    ),
            )
            if (isTv) {
                AnimatedVisibility(
                    visibleState = shown,
                    enter = slideInHorizontally(tween(BenflixMotion.IN_MS, easing = BenflixMotion.Ease)) { it / 3 },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Box(
                        modifier =
                            modifier
                                .fillMaxHeight()
                                .widthIn(min = 420.dp, max = maxWidth * .5f)
                                .background(BenflixColors.Panel, TvPanelShape)
                                .padding(horizontal = 32.dp, vertical = 36.dp),
                    ) {
                        content()
                    }
                }
            } else {
                var dragged by remember { mutableFloatStateOf(0f) }
                AnimatedVisibility(
                    visibleState = shown,
                    enter = slideInVertically(tween(BenflixMotion.IN_MS, easing = BenflixMotion.Ease)) { it / 2 },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                            modifier
                                .fillMaxWidth()
                                .heightIn(max = maxHeight * .9f)
                                .background(BenflixColors.Panel, PhoneSheetShape)
                                .draggable(
                                    orientation = Orientation.Vertical,
                                    state = rememberDraggableState { delta -> dragged = (dragged + delta).coerceAtLeast(0f) },
                                    onDragStopped = {
                                        if (dragged > 160f) onDismissRequest()
                                        dragged = 0f
                                    },
                                ).navigationBarsPadding()
                                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp),
                    ) {
                        // Grab handle
                        Box(
                            Modifier
                                .padding(bottom = 14.dp)
                                .size(width = 36.dp, height = 4.dp)
                                .background(BenflixColors.Line, RoundedCornerShape(2.dp)),
                        )
                        Box(Modifier.fillMaxWidth()) { content() }
                    }
                }
            }
        }
    }
}
