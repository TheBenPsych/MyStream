package com.github.damontecres.wholphin.ui.detail.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.formatSeasonNumber
import org.jellyfin.sdk.model.api.ImageType

/**
 * Benflix look A: the season's own poster on the season page. In long anime (One Piece and
 * the like) each season's art often shows the arc, so you can tell where you are at a glance.
 * Falls back to the show's poster when the season has none.
 */
@Composable
fun SeasonPoster(
    season: BaseItem?,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val imageUrlService = LocalImageUrlService.current
    val url =
        remember(season?.id, height) {
            imageUrlService.getItemImageUrl(
                season,
                ImageType.PRIMARY,
                fillHeight = (height.value * 2).toInt(),
                useSeriesForPrimary = true,
            )
        }
    AsyncImage(
        model = url,
        contentDescription = season?.name,
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .height(height)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF22222C)),
    )
}

/**
 * Benflix look A: "Season 2 · East Blue · 8 of 12 watched" under the show's title.
 * The arc name only shows when the season has a real name (not just "Season 2").
 */
@Composable
fun SeasonLine(
    season: BaseItem?,
    episodes: List<BaseItem?>?,
    modifier: Modifier = Modifier,
) {
    if (season == null) return
    val number = season.data.indexNumber
    val numberText = number?.takeIf { it > 0 }?.let { formatSeasonNumber(it) }
    val name = season.name?.trim().orEmpty()
    val arc = name.takeIf { it.isNotBlank() && !it.equals(numberText, ignoreCase = true) }
    val total = episodes?.size ?: 0
    val watched = episodes?.count { it?.data?.userData?.played == true } ?: 0
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        val main = listOfNotNull(numberText, arc).joinToString(" · ")
        if (main.isNotBlank()) {
            Text(
                text = main,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (total > 0) {
            val allWatched = watched == total
            Text(
                text = stringResource(R.string.benflix_episodes_watched, watched, total),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (allWatched || watched > 0) Color(0xFF86EFAC) else MaterialTheme.colorScheme.onSurface.copy(alpha = .7f),
                maxLines = 1,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x2E22C55E))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
