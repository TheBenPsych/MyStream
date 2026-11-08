package com.github.damontecres.wholphin.ui.playback.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.skipStringRes
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import org.jellyfin.sdk.model.api.MediaSegmentType

/**
 * Benflix redesign (sk-words): one white pill that says what it skips, "Skip recap", "Skip intro" or
 * "Skip credits". No blinking. Focus = a white outline around it.
 */
@Composable
fun SkipSegmentButton(
    type: MediaSegmentType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, BenflixColors.Text), inset = (-4).dp, shape = shape),
            ),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFFF2F2F4),
                contentColor = Color(0xFF0B0C10),
                focusedContainerColor = Color.White,
                focusedContentColor = Color(0xFF0B0C10),
            ),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(type.skipStringRes),
            fontSize = BenflixType.lead,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
        )
    }
}
