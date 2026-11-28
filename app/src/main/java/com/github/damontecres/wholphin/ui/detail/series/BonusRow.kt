package com.github.damontecres.wholphin.ui.detail.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.data.ExtrasItem
import com.github.damontecres.wholphin.ui.detail.title.TitleTabScope
import com.github.damontecres.wholphin.ui.door.doorFocusBorder
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType

/**
 * Glowfin Specials & bonus (Ben 2026-11-27): the show's Bonus row. One wide tile per kind (Deleted scenes,
 * Behind the scenes, Interviews...) with how many there are, or the video itself when there's only one.
 * Filled from Jellyfin's extras folders, which Tower fills from season and complete-series packs.
 */
@Composable
fun BonusRow(
    scope: TitleTabScope,
    extras: List<ExtrasItem>,
    onOpen: (Destination) -> Unit,
) {
    if (extras.isEmpty()) return
    val shape = RoundedCornerShape(8.dp)
    LazyRow(
        contentPadding = PaddingValues(horizontal = scope.side),
        horizontalArrangement = Arrangement.spacedBy(if (scope.wide) 14.dp else 10.dp),
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        itemsIndexed(extras, key = { i, it -> "$i-${it.type}-${it.title}" }) { _, x ->
            Surface(
                onClick = { onOpen(x.destination) },
                shape = ClickableSurfaceDefaults.shape(shape),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = BenflixColors.Raised,
                        contentColor = BenflixColors.Text,
                        focusedContainerColor = BenflixColors.FocusRow,
                        focusedContentColor = BenflixColors.Text,
                        pressedContainerColor = BenflixColors.FocusRow,
                        pressedContentColor = BenflixColors.Text,
                    ),
                border = doorFocusBorder(shape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier = Modifier.width(if (scope.wide) 200.dp else 168.dp).aspectRatio(16f / 9f),
            ) {
                Box(Modifier.fillMaxSize()) {
                    x.imageUrl?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = x.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Column(
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .background(BenflixColors.Tray, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = x.title,
                            color = BenflixColors.Text,
                            fontSize = BenflixType.small,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        x.subtitle?.let {
                            Text(
                                text = it,
                                color = BenflixColors.Muted,
                                fontSize = BenflixType.tag,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
