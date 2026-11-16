package com.github.damontecres.wholphin.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults

/**
 * Benflix's one "you are here" look for the remote (Nov 2026, Ben: "the highlight is barely visible so you
 * don't know where you are"): a bold 3dp ring in the focus colour (white, or sunny yellow on Kids).
 * Buttons and chips also fill white when the remote is on them; pictures and cards get the ring.
 */
val FocusRingWidth = 3.dp

private fun ring(shape: Shape) = Border(border = BorderStroke(FocusRingWidth, BenflixColors.Focus), inset = 0.dp, shape = shape)

/** The ring for a clickable Surface (posters, cards, rows) */
@Composable
fun benflixFocusBorder(shape: Shape) = ClickableSurfaceDefaults.border(focusedBorder = ring(shape))

/** The ring for a tv Card */
@Composable
fun benflixCardFocusBorder(shape: Shape) = CardDefaults.border(focusedBorder = ring(shape))

/** The ring for a tv Button */
@Composable
fun benflixButtonFocusBorder(shape: Shape) = ButtonDefaults.border(focusedBorder = ring(shape))

/**
 * A ring drawn a little outside, with a dark band between, for things that are already white before the remote
 * is on them (picked chips, white buttons) or pictures shown over a bright background (the "all caught up"
 * screen over a paused show). The dark band keeps the white ring readable whatever is behind it.
 */
fun Modifier.benflixOuterFocusRing(
    shape: Shape,
    gap: Dp = 3.dp,
    width: Dp = FocusRingWidth,
): Modifier =
    composed {
        var focused by remember { mutableStateOf(false) }
        val color = BenflixColors.Focus
        this
            .onFocusChanged { focused = it.hasFocus }
            .drawWithContent {
                drawContent()
                if (focused) {
                    val g = gap.toPx()
                    val w = width.toPx()

                    fun ring(
                        out: Float,
                        stroke: Float,
                        c: Color,
                    ) {
                        val outline =
                            shape.createOutline(Size(size.width + out * 2, size.height + out * 2), layoutDirection, this)
                        translate(-out, -out) { drawOutline(outline, c, style = Stroke(width = stroke)) }
                    }
                    ring(g / 2f, g + 1f, FocusHalo)
                    ring(g + w / 2f, w, color)
                }
            }
    }

/** The dark band between a thing and its outer focus ring */
private val FocusHalo = Color(0xE6000000)

/** The shape of a tv Card's corners, for [benflixCardFocusBorder] */
val CardRing = RoundedCornerShape(8.dp)
