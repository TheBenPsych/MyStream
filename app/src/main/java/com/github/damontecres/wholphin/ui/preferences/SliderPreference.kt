package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.preferences.AppSliderPreference
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv

@Composable
fun SliderPreference(
    preference: AppSliderPreference<*>,
    title: String,
    summary: String?,
    value: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    summaryBelow: Boolean = false,
    explain: String? = null,
    additionalSummary: @Composable (ColumnScope.() -> Unit)? = null,
) {
    // Glowfin: each of − and + has its own focus, so only the one you're on lights up.
    // The row tells the page (scrolling, sounds) it has focus whenever either button does.
    val minusSource = remember { MutableInteractionSource() }
    val plusSource = remember { MutableInteractionSource() }
    var rowFocused by remember { mutableStateOf(false) }
    var focusInteraction by remember { mutableStateOf<FocusInteraction.Focus?>(null) }
    val isTv = rememberIsTv()
    // The row stays dark with a thin aqua outline; the button you're on is the bright thing
    val shape = RoundedCornerShape(12.dp)

    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        modifier =
            modifier
                .onFocusChanged { state ->
                    if (state.hasFocus != rowFocused) {
                        rowFocused = state.hasFocus
                        if (state.hasFocus) {
                            val f = FocusInteraction.Focus()
                            focusInteraction = f
                            interactionSource.tryEmit(f)
                        } else {
                            focusInteraction?.let { interactionSource.tryEmit(FocusInteraction.Unfocus(it)) }
                            focusInteraction = null
                        }
                    }
                }.defaultMinSize(minHeight = 72.dp)
                .fillMaxWidth()
                .background(
                    if (rowFocused) Color.White.copy(alpha = .10f) else Color.Transparent,
                    shape = shape,
                ).then(
                    if (rowFocused) {
                        Modifier.border(2.dp, BenflixColors.Focus.copy(alpha = .6f), shape)
                    } else {
                        Modifier
                    },
                ).padding(PaddingValues(horizontal = 16.dp, vertical = 12.dp)),
    ) {
        PreferenceTitle(title, color = BenflixColors.Text)
        if (explain != null && (rowFocused || !isTv)) {
            Text(
                text = explain,
                color = BenflixColors.Muted,
                fontSize = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.height(8.dp))

        ProvideTextStyle(PreferenceSummaryStyle.copy(color = BenflixColors.Text)) {
            additionalSummary?.invoke(this)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxSize(),
        ) {
            // Benflix redesign: numbers are − value +, saved as soon as they change
            val step = preference.interval.coerceAtLeast(1)
            StepButton("−", enabled = value > preference.min, interactionSource = minusSource) {
                onChange((value - step).coerceAtLeast(preference.min))
            }
            // The value sits between − and + (or the plain number when there's no summary)
            if (!summaryBelow) {
                PreferenceSummary(
                    summary ?: value.toString(),
                    color = if (rowFocused) BenflixColors.Focus else BenflixColors.Text,
                )
            }
            StepButton("+", enabled = value < preference.max, interactionSource = plusSource) {
                onChange((value + step).coerceAtMost(preference.max))
            }
            Spacer(Modifier.weight(1f))
        }
        if (summaryBelow) {
            PreferenceSummary(summary, color = BenflixColors.Text)
        }
    }
}

@Composable
private fun StepButton(
    label: String,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    // Glowfin: the button you're on is solid aqua with a white ring and grows a little;
    // the other is a plain outline; one that can't go further fades out
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.Focus,
                focusedContentColor = BenflixColors.OnFocusFill,
                pressedContainerColor = BenflixColors.Focus,
                pressedContentColor = BenflixColors.OnFocusFill,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = BenflixColors.Text.copy(alpha = .3f),
            ),
        border =
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(2.dp, BenflixColors.Text.copy(alpha = .35f)), shape = shape),
                focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape),
                disabledBorder = Border(BorderStroke(2.dp, BenflixColors.Text.copy(alpha = .12f)), shape = shape),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
        modifier = Modifier.size(44.dp),
    ) {
        Text(
            text = label,
            fontSize = BenflixType.rowName,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
