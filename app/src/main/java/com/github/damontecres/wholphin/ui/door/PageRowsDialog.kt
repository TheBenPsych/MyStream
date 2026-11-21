package com.github.damontecres.wholphin.ui.door

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.nav.focusRing
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.tryRequestFocus
import java.util.UUID

/**
 * Settings > Look > Home rows (Glowfin, ui-7): pick a page (Home, Movies, Shows, Anime), then switch each of its
 * rows on or off and move it up or down. Saved straight away; the page loads its new rows next time it's opened.
 * Continue watching (and Next up) always stay at the top.
 */
@Composable
fun PageRowsDialog(
    userId: UUID,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(PageRows.Page.HOME) }
    var list by remember(page) { mutableStateOf(PageRows.choices(context, userId, page)) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.tryRequestFocus() }

    fun keep(next: List<PageRows.Choice>) {
        list = next
        PageRows.save(context, userId, page, next)
    }
    BasicDialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier =
                Modifier
                    .widthIn(max = 640.dp)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
        ) {
            Text("Home rows", fontSize = BenflixType.rowName, fontWeight = FontWeight.Bold, color = Glowfin.Text)
            Text(
                "Switch rows on or off and move them up or down. Keep watching always stays at the top.",
                color = Glowfin.Dim,
                fontSize = BenflixType.small,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PageRows.Page.entries.forEachIndexed { i, p ->
                    SmallChoice(
                        label = p.title,
                        on = p == page,
                        onClick = { page = p },
                        modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
            list.forEachIndexed { index, choice ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = choice.label,
                        color = if (choice.on) Glowfin.Text else Glowfin.Dim,
                        fontSize = BenflixType.body,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    SmallChoice(
                        label = if (choice.on) "On" else "Off",
                        on = choice.on,
                        onClick = { keep(list.toMutableList().also { it[index] = choice.copy(on = !choice.on) }) },
                    )
                    SmallChoice(
                        label = "Up",
                        on = false,
                        enabled = index > 0,
                        onClick = { keep(list.toMutableList().also { it.add(index - 1, it.removeAt(index)) }) },
                    )
                    SmallChoice(
                        label = "Down",
                        on = false,
                        enabled = index < list.size - 1,
                        onClick = { keep(list.toMutableList().also { it.add(index + 1, it.removeAt(index)) }) },
                    )
                }
            }
            Button(onClick = onDismiss, scale = ButtonDefaults.scale(focusedScale = 1f)) { Text("Done") }
        }
    }
}

@Composable
private fun SmallChoice(
    label: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (on) Glowfin.FillMid else Glowfin.Raise,
                contentColor = if (enabled) Glowfin.Text else Glowfin.Dim.copy(alpha = .5f),
                focusedContainerColor = if (on) Glowfin.FillMid else Glowfin.FocusRow,
                focusedContentColor = Glowfin.Text,
                disabledContainerColor = Glowfin.Raise.copy(alpha = .5f),
                disabledContentColor = Glowfin.Dim.copy(alpha = .5f),
            ),
        border = focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Text(
            text = label,
            fontSize = BenflixType.small,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}
