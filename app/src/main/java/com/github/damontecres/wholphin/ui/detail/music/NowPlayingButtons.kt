package com.github.damontecres.wholphin.ui.detail.music

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.PreviewInteractionSource
import com.github.damontecres.wholphin.ui.theme.WholphinTheme

@Composable
fun RepeatButton(
    repeatMode: Int,
    onClick: () -> Unit,
    onControllerInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val selectedColor = MaterialTheme.colorScheme.border
    val focused by interactionSource.collectIsFocusedAsState()
    Button(
        enabled = enabled,
        onClick = onClick,
//        shape = ButtonDefaults.shape(CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = AppColors.TransparentBlack25,
                focusedContainerColor = selectedColor,
            ),
        contentPadding = PaddingValues(4.dp),
        interactionSource = interactionSource,
        modifier =
            modifier
                .size(36.dp, 36.dp)
                .onFocusChanged { onControllerInteraction.invoke() },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.CenterVertically),
        ) {
            Text(
                text = stringResource(R.string.fa_repeat),
                fontSize = 18.sp,
                fontFamily = FontAwesome,
                textAlign = TextAlign.Center,
                color =
                    if (focused) {
                        when (repeatMode) {
                            Player.REPEAT_MODE_ALL -> MaterialTheme.colorScheme.onSurface
                            Player.REPEAT_MODE_ONE -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.surface
                        }
                    } else {
                        when (repeatMode) {
                            Player.REPEAT_MODE_ALL -> MaterialTheme.colorScheme.onSurface
                            Player.REPEAT_MODE_ONE -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)
                        }
                    },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center),
            )
            if (repeatMode == Player.REPEAT_MODE_ONE) {
                Text(
                    text = "1",
                    fontSize = BenflixType.tag,
                    color = MaterialTheme.colorScheme.surface,
                    modifier =
                        Modifier
                            .offset(x = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = RoundedCornerShape(1.dp),
                            ).align(Alignment.BottomStart),
                )
            }
        }
    }
}

@PreviewTvSpec
@Composable
fun ShuffleButtonPreview() {
    val source = remember { PreviewInteractionSource() }
    WholphinTheme {
        Column {
            Row {
                RepeatButton(
                    repeatMode = Player.REPEAT_MODE_OFF,
                    onClick = {},
                    onControllerInteraction = {},
                )
                RepeatButton(
                    repeatMode = Player.REPEAT_MODE_ALL,
                    onClick = {},
                    onControllerInteraction = {},
                )
                RepeatButton(
                    repeatMode = Player.REPEAT_MODE_ONE,
                    onClick = {},
                    onControllerInteraction = {},
                )
            }
            Row {
                RepeatButton(
                    repeatMode = Player.REPEAT_MODE_OFF,
                    onClick = {},
                    onControllerInteraction = {},
                    interactionSource = source,
                )
                RepeatButton(
                    repeatMode = Player.REPEAT_MODE_ALL,
                    onClick = {},
                    onControllerInteraction = {},
                    interactionSource = source,
                )
                RepeatButton(
                    repeatMode = Player.REPEAT_MODE_ONE,
                    onClick = {},
                    onControllerInteraction = {},
                    interactionSource = source,
                )
            }
        }
    }
}
