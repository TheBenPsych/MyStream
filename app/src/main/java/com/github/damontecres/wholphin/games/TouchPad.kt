package com.github.damontecres.wholphin.games

import android.graphics.Paint
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** One on-screen button: a circle or a pill, and which libretro button it presses */
private data class PadButton(
    val label: String,
    val button: Int,
    val center: Offset,
    val size: Size,
    val round: Boolean,
)

private data class PadLayout(
    val dpadCenter: Offset,
    val dpadRadius: Float,
    val buttons: List<PadButton>,
    val menuCenter: Offset,
    val menuRadius: Float,
)

/** Special "button" number for the pause menu circle at the top */
private const val MENU = -1

private fun layoutFor(
    touch: TouchLayout,
    w: Float,
    h: Float,
    margin: Float,
): PadLayout {
    val u = min(w, h)
    val r = u * 0.19f
    val cy = h - margin - r
    val dpad = Offset(margin + r, cy)
    val face = Offset(w - margin - r, cy)
    val b = u * 0.085f
    val round = Size(b * 2, b * 2)

    fun at(
        dx: Float,
        dy: Float,
    ) = Offset(face.x + dx * r, face.y + dy * r)
    val faceButtons =
        when (touch) {
            TouchLayout.TWO_BUTTONS, TouchLayout.GBA -> {
                listOf(
                    PadButton("B", RetroButton.B, at(-0.55f, 0.3f), round, true),
                    PadButton("A", RetroButton.A, at(0.55f, -0.3f), round, true),
                )
            }

            TouchLayout.SNES -> {
                listOf(
                    PadButton("X", RetroButton.X, at(0f, -0.62f), round, true),
                    PadButton("Y", RetroButton.Y, at(-0.62f, 0f), round, true),
                    PadButton("A", RetroButton.A, at(0.62f, 0f), round, true),
                    PadButton("B", RetroButton.B, at(0f, 0.62f), round, true),
                )
            }

            TouchLayout.PLAYSTATION -> {
                listOf(
                    PadButton("△", RetroButton.X, at(0f, -0.62f), round, true),
                    PadButton("□", RetroButton.Y, at(-0.62f, 0f), round, true),
                    PadButton("○", RetroButton.A, at(0.62f, 0f), round, true),
                    PadButton("✕", RetroButton.B, at(0f, 0.62f), round, true),
                )
            }

            TouchLayout.GENESIS -> {
                listOf(
                    PadButton("A", RetroButton.Y, at(-0.7f, 0.25f), round, true),
                    PadButton("B", RetroButton.B, at(0f, 0f), round, true),
                    PadButton("C", RetroButton.A, at(0.7f, -0.25f), round, true),
                )
            }
        }
    val pill = Size(u * 0.26f, u * 0.1f)
    val shoulders =
        when (touch) {
            TouchLayout.GBA, TouchLayout.SNES -> {
                listOf(
                    PadButton("L", RetroButton.L, Offset(margin + pill.width / 2, margin + pill.height / 2), pill, false),
                    PadButton("R", RetroButton.R, Offset(w - margin - pill.width / 2, margin + pill.height / 2), pill, false),
                )
            }

            TouchLayout.PLAYSTATION -> {
                val y1 = margin + pill.height / 2
                val y2 = y1 + pill.height * 1.3f
                listOf(
                    PadButton("L1", RetroButton.L, Offset(margin + pill.width / 2, y2), pill, false),
                    PadButton("R1", RetroButton.R, Offset(w - margin - pill.width / 2, y2), pill, false),
                    PadButton("L2", RetroButton.L2, Offset(margin + pill.width / 2, y1), pill, false),
                    PadButton("R2", RetroButton.R2, Offset(w - margin - pill.width / 2, y1), pill, false),
                )
            }

            else -> {
                emptyList()
            }
        }
    val small = Size(u * 0.2f, u * 0.08f)
    val sy = h - margin - small.height / 2
    val middle =
        buildList {
            val selectAt = Offset(w / 2 - small.width * 0.65f, sy)
            if (touch != TouchLayout.GENESIS) add(PadButton("SELECT", RetroButton.SELECT, selectAt, small, false))
            add(
                PadButton(
                    "START",
                    RetroButton.START,
                    Offset(if (touch == TouchLayout.GENESIS) w / 2 else w / 2 + small.width * 0.65f, sy),
                    small,
                    false,
                ),
            )
        }
    return PadLayout(
        dpadCenter = dpad,
        dpadRadius = r,
        buttons = faceButtons + shoulders + middle,
        menuCenter = Offset(w / 2, margin + u * 0.06f),
        menuRadius = u * 0.06f,
    )
}

/** Which buttons one finger at [p] is pressing */
private fun hit(
    layout: PadLayout,
    p: Offset,
): Int {
    var mask = 0
    val dx = p.x - layout.dpadCenter.x
    val dy = p.y - layout.dpadCenter.y
    val dist = hypot(dx, dy)
    if (dist < layout.dpadRadius * 1.35f) {
        if (dist > layout.dpadRadius * 0.2f) {
            val nx = dx / dist
            val ny = dy / dist
            if (nx > 0.38f) mask = mask or RetroButton.bit(RetroButton.RIGHT)
            if (nx < -0.38f) mask = mask or RetroButton.bit(RetroButton.LEFT)
            if (ny > 0.38f) mask = mask or RetroButton.bit(RetroButton.DOWN)
            if (ny < -0.38f) mask = mask or RetroButton.bit(RetroButton.UP)
        }
        return mask
    }
    for (b in layout.buttons) {
        val inside =
            if (b.round) {
                hypot(p.x - b.center.x, p.y - b.center.y) < b.size.width / 2 * 1.3f
            } else {
                abs(p.x - b.center.x) < b.size.width / 2 * 1.2f && abs(p.y - b.center.y) < b.size.height / 2 * 1.5f
            }
        if (inside) mask = mask or RetroButton.bit(b.button)
    }
    return mask
}

/**
 * See-through game buttons for phones, laid out for each console. Several fingers at once work
 * (e.g. hold right and press jump). A little buzz on each press. The circle at the top opens the menu.
 */
@Composable
fun TouchPad(
    touch: TouchLayout,
    onButtons: (Int) -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val margin = with(LocalDensity.current) { 20.dp.toPx() }
    var pressed by remember { mutableIntStateOf(0) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val layout = remember(touch, w, h) { layoutFor(touch, w, h, margin) }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(layout) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            var mask = 0
                            for (c in event.changes) {
                                if (!c.pressed) {
                                    if (c.previousPressed &&
                                        hypot(c.position.x - layout.menuCenter.x, c.position.y - layout.menuCenter.y) <
                                        layout.menuRadius * 1.6f
                                    ) {
                                        onMenu()
                                    }
                                    continue
                                }
                                mask = mask or hit(layout, c.position)
                                c.consume()
                            }
                            if (mask and pressed.inv() != 0) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            if (mask != pressed) {
                                pressed = mask
                                onButtons(mask)
                            }
                        }
                    }
                },
        ) {
            drawPad(layout, pressed)
        }
    }
}

private val labelPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(220, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

private fun DrawScope.drawPad(
    layout: PadLayout,
    pressed: Int,
) {
    val fill = Color.White.copy(alpha = 0.14f)
    val fillOn = Color.White.copy(alpha = 0.38f)
    val edge = Color.White.copy(alpha = 0.35f)
    val stroke = Stroke(width = 2.dp.toPx())
    // D-pad: a soft circle with four arrows
    val c = layout.dpadCenter
    val r = layout.dpadRadius
    drawCircle(fill, r, c)
    drawCircle(edge, r, c, style = stroke)
    val dirs =
        listOf(
            RetroButton.UP to Offset(0f, -1f),
            RetroButton.DOWN to Offset(0f, 1f),
            RetroButton.LEFT to Offset(-1f, 0f),
            RetroButton.RIGHT to Offset(1f, 0f),
        )
    for ((button, d) in dirs) {
        val on = pressed and RetroButton.bit(button) != 0
        drawCircle(if (on) fillOn else fill, r * 0.24f, Offset(c.x + d.x * r * 0.62f, c.y + d.y * r * 0.62f))
    }
    for (b in layout.buttons) {
        val on = pressed and RetroButton.bit(b.button) != 0
        if (b.round) {
            drawCircle(if (on) fillOn else fill, b.size.width / 2, b.center)
            drawCircle(edge, b.size.width / 2, b.center, style = stroke)
        } else {
            val tl = Offset(b.center.x - b.size.width / 2, b.center.y - b.size.height / 2)
            val corner = CornerRadius(b.size.height / 2)
            drawRoundRect(if (on) fillOn else fill, tl, b.size, corner)
            drawRoundRect(edge, tl, b.size, corner, style = stroke)
        }
        labelPaint.textSize = if (b.round) b.size.width * 0.42f else b.size.height * 0.5f
        drawContext.canvas.nativeCanvas.drawText(
            b.label,
            b.center.x,
            b.center.y - (labelPaint.descent() + labelPaint.ascent()) / 2,
            labelPaint,
        )
    }
    // Menu
    drawCircle(fill, layout.menuRadius, layout.menuCenter)
    drawCircle(edge, layout.menuRadius, layout.menuCenter, style = stroke)
    val m = layout.menuCenter
    val lw = layout.menuRadius * 0.9f
    for (i in -1..1) {
        drawLine(
            Color.White.copy(alpha = 0.8f),
            Offset(m.x - lw / 2, m.y + i * layout.menuRadius * 0.3f),
            Offset(m.x + lw / 2, m.y + i * layout.menuRadius * 0.3f),
            strokeWidth = 2.dp.toPx(),
        )
    }
}
