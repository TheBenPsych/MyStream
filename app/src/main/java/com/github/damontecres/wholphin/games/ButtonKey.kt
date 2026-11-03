package com.github.damontecres.wholphin.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Which pad the button key is drawn for */
enum class KeyFor {
    REMOTE,
    CONTROLLER,
}

/** One line of the key: the button you press (drawn like it looks) and what it does in the game */
private data class KeyLine(
    val press: String,
    val color: Color,
    val round: Boolean,
    val does: String,
    val textColor: Color = Color.White,
)

private val XboxGreen = Color(0xFF5DC21E)
private val XboxRed = Color(0xFFE0332F)
private val XboxBlue = Color(0xFF2F6FE0)
private val XboxYellow = Color(0xFFF2C21B)
private val PadGrey = Color(0xFF3A3A3A)

private fun keyLines(
    console: LocalConsole,
    pad: KeyFor,
): List<KeyLine> {
    val names = buttonLabels(console.touch)

    fun name(button: Int) = names[button]

    return buildList {
        when (pad) {
            KeyFor.CONTROLLER -> {
                add(KeyLine("✚", PadGrey, false, "Move"))
                // Xbox-style pads: the game's buttons sit in the same spots as on the real console
                name(RetroButton.B)?.let { add(KeyLine("A", XboxGreen, true, it, Color.Black)) }
                name(RetroButton.A)?.let { add(KeyLine("B", XboxRed, true, it)) }
                name(RetroButton.Y)?.let { add(KeyLine("X", XboxBlue, true, it)) }
                name(RetroButton.X)?.let { add(KeyLine("Y", XboxYellow, true, it, Color.Black)) }
                name(RetroButton.L)?.let { add(KeyLine("LB", PadGrey, false, it)) }
                name(RetroButton.R)?.let { add(KeyLine("RB", PadGrey, false, it)) }
                name(RetroButton.L2)?.let { add(KeyLine("LT", PadGrey, false, it)) }
                name(RetroButton.R2)?.let { add(KeyLine("RT", PadGrey, false, it)) }
                add(KeyLine("☰", PadGrey, true, "Start"))
                name(RetroButton.SELECT)?.let { add(KeyLine("⧉", PadGrey, true, it)) }
                add(KeyLine("Ⓧ", PadGrey, true, "Menu"))
            }

            KeyFor.REMOTE -> {
                add(KeyLine("▲▼", PadGrey, false, "Move"))
                name(RetroButton.A)?.let { add(KeyLine("OK", PadGrey, true, it)) }
                name(RetroButton.B)?.let { add(KeyLine("Back", PadGrey, false, it)) }
                add(KeyLine("⏯", PadGrey, true, "Start"))
                name(RetroButton.SELECT)?.let { add(KeyLine("≡", PadGrey, true, it)) }
                name(RetroButton.Y)?.let { add(KeyLine("⏪", PadGrey, true, it)) }
                name(RetroButton.X)?.let { add(KeyLine("⏩", PadGrey, true, it)) }
                name(RetroButton.L)?.let { add(KeyLine("CH−", PadGrey, false, it)) }
                name(RetroButton.R)?.let { add(KeyLine("CH+", PadGrey, false, it)) }
                add(KeyLine("Hold Back", PadGrey, false, "Menu"))
            }
        }
    }
}

/**
 * The button key: a picture of each button on the remote or the (Xbox-style) controller, and which
 * button of the real console it presses. Shown on the side of the game and on the Buttons page.
 */
@Composable
fun ButtonKey(
    console: LocalConsole,
    pad: KeyFor,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val chip: Dp = if (compact) 22.dp else 34.dp
    val text = if (compact) 12.sp else 16.sp
    val pillPad = if (compact) 5.dp else 8.dp
    Column(
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 9.dp),
        modifier = modifier,
    ) {
        Text(
            text = if (pad == KeyFor.REMOTE) "Remote" else "Controller",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = text,
            fontWeight = FontWeight.Bold,
        )
        keyLines(console, pad).forEach { line ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
            ) {
                val shape: Shape = if (line.round && line.press.length <= 2) CircleShape else RoundedCornerShape(chip / 2)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .then(if (line.round && line.press.length <= 2) Modifier.size(chip) else Modifier.widthIn(min = chip * 1.6f))
                            .background(line.color, shape)
                            .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
                            .padding(horizontal = if (line.round && line.press.length <= 2) 0.dp else pillPad, vertical = 2.dp),
                ) {
                    Text(
                        line.press,
                        color = line.textColor,
                        fontSize = if (line.press.length > 3) text * 0.85f else text,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Text(
                    line.does,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (pad == KeyFor.REMOTE && !compact) {
            Text(
                "Not every remote has ⏯ ≡ ⏪ ⏩ or CH buttons. No ⏯? The menu has Press Start and Press Select.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
            )
        }
    }
}

/**
 * The key on the side of the game (TV): the remote and the controller next to each other, and how to
 * use a phone as a controller.
 */
@Composable
fun SideKeys(
    console: LocalConsole,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ButtonKey(console, KeyFor.REMOTE, Modifier.weight(1f), compact = true)
            ButtonKey(console, KeyFor.CONTROLLER, Modifier.weight(1f), compact = true)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(22.dp)
                        .background(PadGrey, RoundedCornerShape(6.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(6.dp)),
            ) {
                Text("📱", fontSize = 11.sp)
            }
            Text(
                "Phone as a controller: on your phone open Benflix > Games > Use this phone as a controller",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
            )
        }
    }
}
