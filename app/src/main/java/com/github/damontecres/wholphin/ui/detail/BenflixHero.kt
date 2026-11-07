package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import org.jellyfin.sdk.model.api.ImageType

/**
 * The movie / show page picture (approved dt-hero): the wide picture fills the page, darkened on the
 * left and at the bottom so the words on top stay readable. [dim] (0 to 1) darkens it further once you
 * scroll down to the rows. No glow.
 */
@Composable
fun HeroBackground(
    item: BaseItem,
    dim: () -> Float,
    modifier: Modifier = Modifier,
) {
    val imageUrlService = LocalImageUrlService.current
    val width = LocalConfiguration.current.screenWidthDp
    val url =
        remember(item.id) {
            imageUrlService.getItemImageUrl(item, ImageType.BACKDROP, fillWidth = (width * 2).coerceAtMost(1920))
        }
    Box(modifier = modifier.fillMaxSize()) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to BenflixColors.Tray.copy(alpha = .94f),
                        0.4f to BenflixColors.Tray.copy(alpha = .7f),
                        0.75f to BenflixColors.Tray.copy(alpha = .15f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        1f to BenflixColors.Tray,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = dim() }
                .background(BenflixColors.Tray.copy(alpha = .9f)),
        )
    }
}

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
    val shape = RoundedCornerShape(10.dp)
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
                fontSize = if (big) 18.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

/** The quiet right column: small grey labels with the names under them (Director, Starring, Audio) */
@Composable
fun HeroSideColumn(
    lines: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    if (lines.isEmpty()) return
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.widthIn(max = 260.dp),
    ) {
        lines.forEach { (label, value) ->
            Column {
                Text(text = label, color = BenflixColors.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = value,
                    color = BenflixColors.Soft,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A small white "You are here" style tag */
@Composable
fun HeroTag(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = BenflixColors.Tray,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        modifier =
            modifier
                .background(BenflixColors.Text, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
