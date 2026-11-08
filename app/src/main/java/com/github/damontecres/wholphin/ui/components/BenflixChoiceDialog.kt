package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.Dialog
import com.github.damontecres.wholphin.ui.tryRequestFocus

/** One choice in a [BenflixChoiceDialog] */
data class BenflixChoice(
    val label: String,
    val onClick: () -> Unit,
    val icon: BenflixIcon? = null,
    val note: String? = null,
    /** Marks the current pick (a small tick on the right) */
    val selected: Boolean = false,
)

/**
 * The redesign's small pop-up list (More, Rate, Fix skip times...): a dark card with a title and big
 * rows. The focused row turns white. Back closes it. Picking a row closes it first.
 */
@Composable
fun BenflixChoiceDialog(
    title: String?,
    choices: List<BenflixChoice>,
    onDismissRequest: () -> Unit,
    subtitle: String? = null,
) {
    BenflixPanelDialog(
        onDismissRequest = onDismissRequest,
    ) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { first.tryRequestFocus() }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    color = BenflixColors.Text,
                    fontSize = BenflixType.rowName,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = if (subtitle == null) 8.dp else 2.dp),
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = BenflixColors.Muted,
                    fontSize = BenflixType.small,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                )
            }
            choices.forEachIndexed { index, choice ->
                ChoiceRow(
                    choice = choice,
                    onClick = {
                        onDismissRequest()
                        choice.onClick()
                    },
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    choice: BenflixChoice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val content = if (focused) BenflixColors.Tray else BenflixColors.Text
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.Text,
                focusedContentColor = BenflixColors.Tray,
                pressedContainerColor = BenflixColors.Text,
                pressedContentColor = BenflixColors.Tray,
            ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border.None),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier =
            modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            if (choice.icon != null) BenflixIconImage(choice.icon, color = content, size = 20.dp)
            Column(Modifier.weight(1f)) {
                Text(text = choice.label, color = content, fontSize = BenflixType.body, fontWeight = FontWeight.Bold)
                if (choice.note != null) {
                    Text(
                        text = choice.note,
                        color = if (focused) BenflixColors.Tray.copy(alpha = .7f) else BenflixColors.Muted,
                        fontSize = BenflixType.small,
                    )
                }
            }
            if (choice.selected) BenflixIconImage(BenflixIcon.CHECK, color = content, size = 18.dp)
        }
    }
}
