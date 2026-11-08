package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun CircularProgress(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        CircularProgressIndicator(
            color = BenflixColors.Text,
            modifier =
                Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
        )
    }
}

/**
 * Fill the space with a loading indicator and take focus
 */
@Composable
fun LoadingPage(
    modifier: Modifier = Modifier,
    focusEnabled: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }
    if (focusEnabled) {
        LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable(focusEnabled),
    ) {
        StillPosters()
    }
}

/**
 * Benflix loading (polish round, Nov 2026): plain grey poster shapes where the page will be, with no
 * moving parts, so the page feels ready straight away and stays quick on older TVs.
 */
@Composable
fun StillPosters(modifier: Modifier = Modifier) {
    val isTv = rememberIsTv()
    val posterWidth = if (isTv) 120.dp else 100.dp
    val gap = if (isTv) 16.dp else 10.dp
    val shape = BenflixShapes.Card
    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        val count = ((maxWidth / (posterWidth + gap)).toInt() + 1).coerceIn(1, 12)
        Column(
            verticalArrangement = Arrangement.spacedBy(if (isTv) BenflixSpace.tvRowGap else BenflixSpace.phoneRowGap),
            modifier =
                Modifier.padding(
                    start = if (isTv) BenflixSpace.tvEdge else BenflixSpace.phoneEdge,
                    top = if (isTv) 40.dp else 20.dp,
                ),
        ) {
            repeat(3) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .size(width = if (isTv) 180.dp else 140.dp, height = 18.dp)
                            .background(BenflixColors.Raised, BenflixShapes.Small),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        repeat(count) {
                            Box(
                                Modifier
                                    .size(width = posterWidth, height = posterWidth * 1.5f)
                                    .background(BenflixColors.Raised, shape),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fill the space and take focus, showing a loading indicator after the specified delay
 */
@Composable
fun DelayedLoadingPage(
    modifier: Modifier = Modifier,
    focusEnabled: Boolean = true,
    delay: Duration = 300.milliseconds,
) {
    val focusRequester = remember { FocusRequester() }
    if (focusEnabled) {
        LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    }
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delay)
        show = true
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable(focusEnabled),
    ) {
        if (show) {
            StillPosters()
        }
    }
}

/**
 * A small white spinner in the middle that takes focus. For places where grey posters would look
 * odd: the player, setup screens, settings and pop-ups.
 */
@Composable
fun LoadingSpinner(
    modifier: Modifier = Modifier,
    focusEnabled: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }
    if (focusEnabled) {
        LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable(focusEnabled),
    ) {
        CircularProgressIndicator(
            color = BenflixColors.Text,
            strokeWidth = 3.dp,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(36.dp),
        )
    }
}
