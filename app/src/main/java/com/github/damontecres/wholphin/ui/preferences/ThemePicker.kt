package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.preferences.GlowfinLook
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Button
import com.github.damontecres.wholphin.ui.theme.FocusRingWidth
import com.github.damontecres.wholphin.ui.theme.ThemeChoice
import com.github.damontecres.wholphin.ui.theme.ThemeChoices
import com.github.damontecres.wholphin.ui.theme.ThemeLook
import com.github.damontecres.wholphin.ui.tryRequestFocus

/**
 * Settings > Look > Theme (Ben 2026-11-27). Each theme shows a small picture of itself drawn in its own colours
 * and corners. Picking one changes the whole app straight away, behind this panel, so it is its own preview.
 * It only needs [picked] and [onPick], so the same list can open at first start-up later.
 */
@Composable
fun ThemePickerDialog(
    picked: GlowfinLook,
    onPick: (GlowfinLook) -> Unit,
    onDismiss: () -> Unit,
) {
    BasicDialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.widthIn(max = 520.dp).verticalScroll(rememberScrollState()),
        ) {
            Text("Theme", fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold, color = BenflixColors.Text)
            Text(
                "Pick a look for Glowfin. It changes right away, and it follows you to your other TVs and phones.",
                fontSize = BenflixType.small,
                color = BenflixColors.Muted,
            )
            ThemeList(picked = picked, onPick = onPick)
            Button(onClick = onDismiss) { Text("Done") }
        }
    }
}

/** The themes, one under the other, with the picked one marked "In use" */
@Composable
fun ThemeList(
    picked: GlowfinLook,
    onPick: (GlowfinLook) -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = remember { FocusRequester() }
    val pickedIndex = ThemeChoices.indexOfFirst { it.choice == picked }.coerceAtLeast(0)
    LaunchedEffect(Unit) { first.tryRequestFocus() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        ThemeChoices.forEachIndexed { index, theme ->
            ThemeRow(
                theme = theme,
                inUse = index == pickedIndex,
                onClick = { onPick(theme.choice) },
                modifier = if (index == pickedIndex) Modifier.focusRequester(first) else Modifier,
            )
        }
    }
}

@Composable
private fun ThemeRow(
    theme: ThemeChoice,
    inUse: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = BenflixShapes.Card
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = BenflixColors.Raised,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, if (inUse) BenflixColors.Focus else BenflixColors.Line), shape = shape),
                focusedBorder = Border(BorderStroke(FocusRingWidth, BenflixColors.Outline), shape = shape),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(10.dp),
        ) {
            ThemeThumb(theme.look)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(theme.title, fontSize = BenflixType.lead, fontWeight = FontWeight.SemiBold)
                Text(theme.line, fontSize = BenflixType.small, color = BenflixColors.Muted)
            }
            if (inUse) {
                Text(
                    "In use",
                    fontSize = BenflixType.tag,
                    fontWeight = FontWeight.SemiBold,
                    color = BenflixColors.OnFocusFill,
                    modifier =
                        Modifier
                            .background(BenflixColors.FocusFill, BenflixShapes.Small)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/**
 * A tiny Glowfin screen drawn with one theme's own colours and corners: the side menu, a row name, three
 * covers (the middle one lit) and a Play button. Plain boxes, so it costs nothing on an older TV.
 */
@Composable
private fun ThemeThumb(look: ThemeLook) {
    val glow = Brush.horizontalGradient(0f to look.glowA, 0.6f to look.glowB, 1f to look.glowC)
    val density = LocalDensity.current
    val coverShape = remember(look, density) { look.card.thumb(DpSize(26.dp, 30.dp), density) }
    val buttonShape = remember(look, density) { look.button.thumb(DpSize(30.dp, 9.dp), density) }
    Row(
        modifier =
            Modifier
                .size(width = 132.dp, height = 74.dp)
                .clip(BenflixShapes.Small)
                .background(look.ink)
                .border(1.dp, look.line, BenflixShapes.Small),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxHeight().width(16.dp).background(look.deep),
        ) {
            Box(Modifier.size(7.dp).background(glow, CircleShape))
            repeat(3) { Box(Modifier.size(6.dp).background(look.dim.copy(alpha = .6f), CircleShape)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 8.dp, top = 9.dp)) {
            Box(Modifier.size(width = 42.dp, height = 5.dp).background(look.text, CircleShape))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(3) { index ->
                    val cover = Modifier.size(width = 26.dp, height = 30.dp).background(look.raise, coverShape)
                    val frame = look.frame
                    Box(
                        when {
                            index == 1 -> cover.border(1.5.dp, look.outline, coverShape)
                            frame != null -> cover.border(1.dp, frame, coverShape)
                            else -> cover
                        },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 30.dp, height = 9.dp).background(glow, buttonShape))
                Box(Modifier.height(4.dp).width(40.dp).background(look.accent, CircleShape))
            }
        }
    }
}

/** A theme's corner, shrunk to suit the tiny picture: a round button stays round, a 10dp card corner becomes 3dp */
private fun RoundedCornerShape.thumb(
    box: DpSize,
    density: Density,
): RoundedCornerShape {
    val size = with(density) { Size(box.width.toPx(), box.height.toPx()) }
    val px = topStart.toPx(size, density)
    return if (px >= size.minDimension / 2f) RoundedCornerShape(percent = 50) else RoundedCornerShape(px * .3f)
}
