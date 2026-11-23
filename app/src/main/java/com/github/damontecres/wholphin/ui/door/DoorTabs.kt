package com.github.damontecres.wholphin.ui.door

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont

/**
 * Glowfin (ui-7, Wholphin-like): a page's places as tabs along its top (For you, All movies,
 * Collections, Genres, My List). Glowfin new look (Ben 2026-11-22): see-through pills over the art, the tab
 * you're on solid white; the one the remote is on turns white with the aqua ring (nothing grows). OK (or a tap) opens it; moving along the tabs only moves, so nothing loads as you pass.
 * Phones (Z1): shorter names (For you, All, Genres, My List) that scroll sideways when they don't fit.
 */
@Composable
fun DoorTabs(
    state: DoorFrameState,
    sections: List<DoorSection>,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    val picked = remember(sections) { sections.associate { it.key to FocusRequester() } }
    val on = sections.firstOrNull { it.key == state.section } ?: sections.firstOrNull()
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(if (wide) 8.dp else 6.dp),
        contentPadding = PaddingValues(horizontal = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .then(on?.let { picked[it.key] }?.let { Modifier.focusRestorer(it) } ?: Modifier),
    ) {
        itemsIndexed(sections, key = { _, it -> it.key }) { _, section ->
            DoorTab(
                label = if (wide) section.label else shortTabName(section.label),
                selected = section.key == state.section,
                wide = wide,
                onClick = {
                    if (section.key == state.section) {
                        if (section.key == DOOR_HOME) state.toTop()
                    } else {
                        state.pick(section.key)
                    }
                },
                modifier = picked[section.key]?.let { Modifier.focusRequester(it) } ?: Modifier,
            )
        }
    }
}

@Composable
internal fun DoorTab(
    label: String,
    selected: Boolean,
    wide: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                // Glowfin new look: see-through pills over the art, the open one solid white
                containerColor = if (selected) Color.White.copy(alpha = .92f) else Glowfin.Ink.copy(alpha = .35f),
                contentColor = if (selected) Glowfin.Ink else Glowfin.Text.copy(alpha = .88f),
                focusedContainerColor = Color.White,
                focusedContentColor = Glowfin.Ink,
                pressedContainerColor = Color.White,
                pressedContentColor = Glowfin.Ink,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0f else .14f)), shape = shape),
                focusedBorder = Border(BorderStroke(2.5.dp, BenflixColors.Focus), shape = shape),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Text(
            text = label,
            fontFamily = GlowfinDisplayFont,
            fontSize = if (wide) BenflixType.body else BenflixType.small,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = TAB_PAD, vertical = if (wide) 6.dp else 5.dp),
        )
    }
}

/** Phones (Z1): "All movies" and "All shows" become "All", "Genres and tags" becomes "Genres" */
fun shortTabName(label: String): String =
    when {
        label.startsWith("All ") -> "All"
        label.startsWith("Genres") -> "Genres"
        else -> label
    }

/** How tall the tab line is, so the page below starts under it */
fun doorTabsHeight(wide: Boolean): Dp = if (wide) 52.dp else 46.dp

private val TAB_PAD = 14.dp
