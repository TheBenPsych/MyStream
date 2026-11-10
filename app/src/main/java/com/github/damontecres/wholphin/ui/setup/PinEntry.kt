package com.github.damontecres.wholphin.ui.setup

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.playback.isEnterKey
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.touch.rememberIsTv

@Composable
fun PinEntry(
    onTextChange: (String) -> Unit,
    onClickServerAuth: () -> Unit,
    modifier: Modifier = Modifier,
    heading: String? = null,
) {
    var input by remember { mutableStateOf("") }
    val isTv = rememberIsTv()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        if (heading != null) {
            // Leaving Kids for a grown-up with a PIN: say what's happening (kd-p-leave)
            Text(
                text = heading,
                fontSize = BenflixType.tag,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        Text(
            text = stringResource(R.string.enter_pin),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        if (isTv) PinArrowRow(Modifier.align(Alignment.CenterHorizontally))
        PinEntryDots(input.length, Modifier.align(Alignment.CenterHorizontally))
        if (isTv) PinBackHint(input.isNotEmpty(), Modifier.align(Alignment.CenterHorizontally))
        if (!isTv) {
            // Phones have no remote, so the PIN is typed on these buttons
            PhonePinPad(
                onKey = { key ->
                    input = if (key == null) input.dropLast(1) else input + key
                    onTextChange.invoke(input)
                },
                onConfirm = null,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        TextButton(
            onClick = onClickServerAuth,
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .onKeyEvent {
                        if (isPinDeleteKey(it)) {
                            // Benflix: Back takes off the last press (and closes when nothing is entered)
                            if (input.isEmpty()) return@onKeyEvent false
                            if (it.type == KeyEventType.KeyUp) {
                                input = input.dropLast(1)
                                onTextChange.invoke(input)
                            }
                            return@onKeyEvent true
                        }
                        if (it.type == KeyEventType.KeyUp) {
                            var str = input
                            str +=
                                when (it.key) {
                                    Key.DirectionUp -> "U"
                                    Key.DirectionRight -> "R"
                                    Key.DirectionDown -> "D"
                                    Key.DirectionLeft -> "L"
                                    Key.Zero, Key.NumPad0 -> "0"
                                    Key.One, Key.NumPad1 -> "1"
                                    Key.Two, Key.NumPad2 -> "2"
                                    Key.Three, Key.NumPad3 -> "3"
                                    Key.Four, Key.NumPad4 -> "4"
                                    Key.Five, Key.NumPad5 -> "5"
                                    Key.Six, Key.NumPad6 -> "6"
                                    Key.Seven, Key.NumPad7 -> "7"
                                    Key.Eight, Key.NumPad8 -> "8"
                                    Key.Nine, Key.NumPad9 -> "9"
                                    else -> return@onKeyEvent false
                                }
                            onTextChange.invoke(str)
                            input = str
                            return@onKeyEvent true
                        } else {
                            return@onKeyEvent false
                        }
                    },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.use_server_credentials),
                )
                Text(
                    text = stringResource(R.string.will_remove_pin),
                    fontSize = BenflixType.tag,
                )
            }
        }
    }
}

@Composable
fun PinEntryCreate(
    @StringRes title: Int,
    onTextChange: (String) -> Unit,
    onConfirm: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    val isTv = rememberIsTv()

    // Benflix: the entered numbers are cleared after each try, so after "Incorrect" they can start again
    fun confirm() {
        val pin = input
        input = ""
        onTextChange.invoke(input)
        onConfirm?.invoke(pin)
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .onKeyEvent {
                    if (isEnterKey(it)) {
                        // Only once per press (not again when the button comes back up)
                        if (it.type == KeyEventType.KeyDown && it.nativeKeyEvent.repeatCount == 0) confirm()
                        return@onKeyEvent true
                    }
                    if (isPinDeleteKey(it)) {
                        // Benflix: Back takes off the last press (and closes when nothing is entered)
                        if (input.isEmpty()) return@onKeyEvent false
                        if (it.type == KeyEventType.KeyUp) {
                            input = input.dropLast(1)
                            onTextChange.invoke(input)
                        }
                        return@onKeyEvent true
                    }
                    if (it.type == KeyEventType.KeyUp) {
                        var str = input
                        str +=
                            when (it.key) {
                                Key.DirectionUp -> "U"
                                Key.DirectionRight -> "R"
                                Key.DirectionDown -> "D"
                                Key.DirectionLeft -> "L"
                                Key.Zero, Key.NumPad0 -> "0"
                                Key.One, Key.NumPad1 -> "1"
                                Key.Two, Key.NumPad2 -> "2"
                                Key.Three, Key.NumPad3 -> "3"
                                Key.Four, Key.NumPad4 -> "4"
                                Key.Five, Key.NumPad5 -> "5"
                                Key.Six, Key.NumPad6 -> "6"
                                Key.Seven, Key.NumPad7 -> "7"
                                Key.Eight, Key.NumPad8 -> "8"
                                Key.Nine, Key.NumPad9 -> "9"
                                else -> return@onKeyEvent false
                            }
                        onTextChange.invoke(str)
                        input = str
                        return@onKeyEvent true
                    } else {
                        return@onKeyEvent false
                    }
                }.focusable(),
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        if (isTv) PinArrowRow(Modifier.align(Alignment.CenterHorizontally))
        PinEntryDots(input.length, Modifier.align(Alignment.CenterHorizontally))
        if (isTv) PinBackHint(input.isNotEmpty(), Modifier.align(Alignment.CenterHorizontally))
        if (!isTv) {
            // Phones have no remote, so the PIN is typed on these buttons
            PhonePinPad(
                onKey = { key ->
                    input = if (key == null) input.dropLast(1) else input + key
                    onTextChange.invoke(input)
                },
                onConfirm = onConfirm?.let { { confirm() } },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        } else if (onConfirm != null) {
            Text(
                text = stringResource(R.string.press_enter_to_confirm),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/**
 * Benflix on phones: number buttons for the PIN, plus the four arrows so a PIN made with
 * the TV remote's arrows still works. [onKey] gets null for Delete.
 */
@Composable
private fun PhonePinPad(
    onKey: (String?) -> Unit,
    onConfirm: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val rows =
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("DEL", "0", if (onConfirm != null) "OK" else ""),
        )
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(top = 8.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    when (key) {
                        "" -> Box(Modifier.size(PAD_KEY))
                        "DEL" -> PadKey(label = "Delete", small = true) { onKey(null) }
                        "OK" -> PadKey(label = "OK", small = true, highlight = true) { onConfirm?.invoke() }
                        else -> PadKey(label = key) { onKey(key) }
                    }
                }
            }
        }
        // The TV remote's arrows, for a PIN that was made on the TV
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            listOf(
                R.string.fa_arrow_left_long to "L",
                R.string.fa_arrow_up_long to "U",
                R.string.fa_arrow_right_long to "R",
                R.string.fa_arrow_down_long to "D",
            ).forEach { (icon, key) ->
                PadKey(label = stringResource(icon), small = true, icon = true, size = PAD_ARROW) { onKey(key) }
            }
        }
    }
}

@Composable
private fun PadKey(
    label: String,
    small: Boolean = false,
    highlight: Boolean = false,
    icon: Boolean = false,
    size: androidx.compose.ui.unit.Dp = PAD_KEY,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(size)
                .clip(RoundedCornerShape(50))
                .background(if (highlight) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f))
                .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            fontSize = if (small) 16.sp else 26.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (icon) FontAwesome else null,
            color = if (highlight) MaterialTheme.colorScheme.onPrimary else Color.White,
        )
    }
}

/** Back on the remote (or Backspace / Delete on a keyboard) takes off the last press */
private fun isPinDeleteKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean =
    event.key == Key.Back || event.key == Key.Backspace || event.key == Key.Delete

/** On TV, a note that Back takes off the last press (kept in place so the dialog doesn't jump) */
@Composable
private fun PinBackHint(
    show: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = if (show) "Pressed the wrong one? Press Back to take it off" else " ",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

private val PAD_KEY = 64.dp
private val PAD_ARROW = 48.dp

@Composable
fun PinArrowRow(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        val arrows =
            listOf(R.string.fa_arrow_left_long, R.string.fa_arrow_up_long, R.string.fa_arrow_right_long, R.string.fa_arrow_down_long)
        arrows.forEach {
            Text(
                text = stringResource(it),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontAwesome,
            )
        }
        Text(
            text = stringResource(R.string.pin_digits_hint),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun PinEntryDots(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier =
            modifier
                .defaultMinSize(minWidth = 180.dp, minHeight = 40.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    shape = CircleShape,
                ).padding(vertical = 16.dp),
    ) {
        repeat(count) {
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = 8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 1f))
                        .size(8.dp),
            )
        }
    }
}

@Composable
fun PinEntryDialog(
    onDismissRequest: () -> Unit,
    onTextChange: (String) -> Unit,
    onClickServerAuth: () -> Unit,
    heading: String? = null,
) {
    // Benflix: the shared side panel on TV, a sheet on phones
    BasicDialog(
        onDismissRequest = onDismissRequest,
    ) {
        PinEntry(
            onTextChange = onTextChange,
            onClickServerAuth = onClickServerAuth,
            modifier = Modifier.padding(16.dp),
            heading = heading,
        )
    }
}

@PreviewTvSpec
@Composable
private fun PinEntryPreview() {
    WholphinTheme {
        PinEntry(
            onTextChange = {},
            onClickServerAuth = {},
            modifier = Modifier,
        )
    }
}
