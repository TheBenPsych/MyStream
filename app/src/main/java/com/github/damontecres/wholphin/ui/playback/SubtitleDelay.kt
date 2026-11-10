package com.github.damontecres.wholphin.ui.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// Benflix: half a second and two seconds, so five buttons fit across a phone too
private val delayIncrements = listOf(500.milliseconds, 2.seconds)

/**
 * Shows buttons for adding to or subtracting from the given [Duration], ie subtitle delay
 */
@Composable
fun SubtitleDelay(
    delay: Duration,
    onChangeDelay: (Duration) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Text(
            text =
                stringResource(R.string.subtitle_delay) + ": " +
                    when {
                        delay == Duration.ZERO -> "not moved"
                        delay.isPositive() -> "$delay later"
                        else -> "${-delay} sooner"
                    },
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Words show up before people talk? Press +",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            delayIncrements.reversed().forEach {
                SubtitleDelayButton(
                    text = "-$it",
                    onClick = { onChangeDelay.invoke(-it) },
                    modifier = Modifier,
                )
            }
            SubtitleDelayButton(
                text = stringResource(R.string.reset),
                onClick = { onChangeDelay.invoke(-delay) },
                modifier = Modifier.focusRequester(focusRequester),
            )
            delayIncrements.forEach {
                SubtitleDelayButton(
                    text = "+$it",
                    onClick = { onChangeDelay.invoke(it) },
                    modifier = Modifier,
                )
            }
        }
    }
}

@Composable
fun SubtitleDelayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.width(60.dp),
        shape =
            ClickableSurfaceDefaults.shape(
                shape = RoundedCornerShape(33),
            ),
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewTvSpec
@Composable
private fun SubtitleDelayPreview() {
    WholphinTheme {
        SubtitleDelay(
            delay = 1.5.seconds,
            onChangeDelay = {},
            modifier = Modifier,
        )
    }
}
