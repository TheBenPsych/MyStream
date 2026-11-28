package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixType

/**
 * A button on the movie page: the main one (Play / Resume) is white with dark words; the rest are dark
 * with white words and an icon. Focus = white outline.
 */
@Composable
fun HeroButton(
    label: String,
    icon: BenflixIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    big: Boolean = false,
) {
    val shape = BenflixShapes.Card
    var focused by remember { mutableStateOf(false) }
    val container = if (primary) BenflixColors.Text else BenflixColors.FocusRow.copy(alpha = .92f)
    val content = if (primary) BenflixColors.Tray else BenflixColors.Text
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = container,
                contentColor = content,
                focusedContainerColor = if (primary) BenflixColors.Text else BenflixColors.PanelHigh,
                focusedContentColor = content,
                pressedContainerColor = container,
                pressedContentColor = content,
            ),
        border = doorFocusBorder(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.onFocusChanged { focused = it.isFocused },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = if (big) 22.dp else 16.dp, vertical = if (big) 13.dp else 10.dp),
        ) {
            BenflixIconImage(icon, color = content, size = if (big) 22.dp else 18.dp)
            Text(
                text = label,
                color = content,
                fontSize = if (big) BenflixType.lead else BenflixType.body,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
