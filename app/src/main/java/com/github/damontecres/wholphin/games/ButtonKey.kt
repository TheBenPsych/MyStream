package com.github.damontecres.wholphin.games

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
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

/** How big a show is next to a game */
enum class ShowSize(
    val label: String,
) {
    SIDE("Show: small at the side"),
    HALF("Show: half and half"),
    BIG("Show: big"),
    CORNER("Show: tiny corner"),
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
                add(KeyLine("☰", PadGrey, true, name(RetroButton.START) ?: "Start"))
                name(RetroButton.SELECT)?.let { add(KeyLine("⧉", PadGrey, true, it)) }
                add(KeyLine("Ⓧ", PadGrey, true, "Menu"))
            }

            KeyFor.REMOTE -> {
                add(KeyLine("▲▼", PadGrey, false, "Move"))
                if (console.touch == TouchLayout.ONE_BUTTON) {
                    // One-button consoles: OK fires (Back still works too)
                    name(RetroButton.B)?.let { add(KeyLine("OK", PadGrey, true, it)) }
                } else {
                    name(RetroButton.A)?.let { add(KeyLine("OK", PadGrey, true, it)) }
                    name(RetroButton.B)?.let { add(KeyLine("Back", PadGrey, false, it)) }
                }
                add(KeyLine("⏯", PadGrey, true, name(RetroButton.START) ?: "Start"))
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PadIcon(pad, Modifier.size(if (compact) 20.dp else 26.dp))
            Text(
                text = if (pad == KeyFor.REMOTE) "Remote" else "Controller",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = text,
                fontWeight = FontWeight.Bold,
            )
        }
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
                        overflow = TextOverflow.Ellipsis,
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

/** A little drawing of a TV remote or a game controller */
@Composable
fun PadIcon(
    pad: KeyFor,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val c = Color.White.copy(alpha = 0.85f)
        val stroke = Stroke(width = size.minDimension * 0.08f)
        if (pad == KeyFor.REMOTE) {
            val bw = size.width * 0.42f
            drawRoundRect(
                c,
                Offset((size.width - bw) / 2, size.height * 0.04f),
                Size(bw, size.height * 0.92f),
                CornerRadius(bw / 2),
                style = stroke,
            )
            drawCircle(c, bw * 0.28f, Offset(size.width / 2, size.height * 0.3f), style = stroke)
            drawCircle(c, bw * 0.08f, Offset(size.width / 2, size.height * 0.62f))
            drawCircle(c, bw * 0.08f, Offset(size.width / 2, size.height * 0.76f))
        } else {
            val h = size.height * 0.6f
            drawRoundRect(
                c,
                Offset(size.width * 0.02f, (size.height - h) / 2),
                Size(size.width * 0.96f, h),
                CornerRadius(h / 2),
                style = stroke,
            )
            val cy = size.height / 2
            drawLine(c, Offset(size.width * 0.2f, cy), Offset(size.width * 0.36f, cy), stroke.width)
            drawLine(c, Offset(size.width * 0.28f, cy - h * 0.18f), Offset(size.width * 0.28f, cy + h * 0.18f), stroke.width)
            drawCircle(c, h * 0.08f, Offset(size.width * 0.66f, cy + h * 0.1f))
            drawCircle(c, h * 0.08f, Offset(size.width * 0.78f, cy - h * 0.1f))
        }
    }
}

/** "Phone as a controller" tip */
@Composable
fun PhoneTip(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
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

/**
 * The key on the side of the game: the pad you last pressed (remote or controller), switching by
 * itself, plus how to use a phone as a controller.
 */
@Composable
fun SmartKey(
    console: LocalConsole,
    pad: KeyFor,
    modifier: Modifier = Modifier,
    showPhoneTip: Boolean = true,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        ButtonKey(console, pad, compact = true)
        if (showPhoneTip) {
            Text(
                if (pad == KeyFor.REMOTE) "Using a controller? Press any button on it." else "Using the remote? Press any button on it.",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
            )
            PhoneTip()
        }
    }
}
