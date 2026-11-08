package com.github.damontecres.wholphin.ui.detail.series

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.animeEpisodeLabel
import com.github.damontecres.wholphin.services.fillerTagFor
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.formatSeasonNumber
import com.github.damontecres.wholphin.ui.playback.isPlayKeyUp
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixRed
import com.github.damontecres.wholphin.ui.theme.BenflixType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.extensions.ticks

private val WatchedGreen = Color(0xFF86EFAC)
private val FillerYellow = Color(0xFFFCD34D)

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
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF22222C)),
    )
}

/** The season's name: "Season 2", or the arc name when the season has a real name */
private fun seasonTitles(season: BaseItem): Pair<String, String?> {
    val number = season.data.indexNumber
    val numberText = number?.takeIf { it > 0 }?.let { formatSeasonNumber(it) }
    val name = season.name?.trim().orEmpty()
    return when {
        numberText == null -> (name.ifBlank { "Specials" }) to null
        name.isBlank() || name.equals(numberText, ignoreCase = true) -> numberText to null
        else -> numberText to name
    }
}

/**
 * Benflix look A: the top of the season page. Poster on the left; show name, "8 OF 12 WATCHED",
 * the big season name and the arc name next to it; then the year, episode count and a progress bar.
 */
@Composable
fun SeasonInfoBlock(
    series: BaseItem,
    season: BaseItem?,
    episodes: List<BaseItem?>?,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val total = episodes?.size ?: 0
    val watched = episodes?.count { it?.data?.userData?.played == true } ?: 0
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 18.dp else 14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            SeasonPoster(season = season, height = if (isTv) 150.dp else 126.dp)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = series.title ?: series.name ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (total > 0) {
                    Text(
                        text = stringResource(R.string.benflix_episodes_watched, watched, total).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = if (watched > 0) WatchedGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = .8f),
                        maxLines = 1,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (watched > 0) Color(0x2E22C55E) else Color.White.copy(alpha = .12f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                if (season != null) {
                    val (main, arc) = seasonTitles(season)
                    Text(
                        text = main,
                        fontSize = if (isTv) BenflixType.hero else BenflixType.pageTitle,
                        lineHeight = if (isTv) 36.sp else 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    arc?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (total > 0) {
            val year = season?.data?.productionYear ?: season?.data?.premiereDate?.year
            val minutes =
                episodes
                    ?.sumOf { it?.data?.runTimeTicks ?: 0L }
                    ?.ticks
                    ?.inWholeMinutes ?: 0L
            val runtime =
                when {
                    minutes <= 0 -> null
                    minutes < 60 -> "${minutes}m"
                    else -> "${minutes / 60}h ${minutes % 60}m"
                }
            Text(
                text =
                    listOfNotNull(
                        year?.toString(),
                        if (total == 1) "1 episode" else "$total episodes",
                        runtime,
                    ).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .85f),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(if (isTv) 200.dp else 160.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = .2f)),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(watched.toFloat() / total)
                                .height(5.dp)
                                .background(BenflixRed),
                    )
                }
                val left = total - watched
                Text(
                    text =
                        when (left) {
                            0 -> "All watched"
                            1 -> "1 episode left"
                            else -> "$left episodes left"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .85f),
                )
            }
        }
    }
}

/**
 * Benflix look A: season pills (Season 1, Season 2, Specials...). Scrolls sideways for shows
 * with lots of seasons and keeps the one you're on in view.
 */
@Composable
fun SeasonPills(
    seasons: List<BaseItem?>,
    selectedIndex: Int,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (seasons.size <= 1) return
    val state = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        if (selectedIndex in seasons.indices) state.animateScrollToItem((selectedIndex - 1).coerceAtLeast(0))
    }
    LazyRow(
        state = state,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp),
        modifier = modifier,
    ) {
        itemsIndexed(seasons) { index, season ->
            val selected = index == selectedIndex
            val label =
                season?.let {
                    val number = it.data.indexNumber
                    if (number != null && number > 0) {
                        formatSeasonNumber(number)
                    } else {
                        it.name ?: "Specials"
                    }
                } ?: ""
            Surface(
                onClick = { onClick.invoke(index) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = if (selected) Color.White else Color.White.copy(alpha = .12f),
                        contentColor = if (selected) Color.Black else Color.White,
                        focusedContainerColor = if (selected) Color.White else Color.White.copy(alpha = .3f),
                        focusedContentColor = if (selected) Color.Black else Color.White,
                    ),
                border =
                    ClickableSurfaceDefaults.border(
                        focusedBorder = Border(BorderStroke(3.dp, BenflixColors.Text), shape = RoundedCornerShape(20.dp)),
                    ),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * Benflix look A: one episode in the season page's list. Thumbnail, "9 · Title", then
 * watched / time left / FILLER / up next. The episode you're on also shows its story.
 */
@Composable
fun EpisodeListRow(
    episode: BaseItem?,
    selected: Boolean,
    upNext: Boolean,
    isTv: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageUrlService = LocalImageUrlService.current
    val url =
        remember(episode?.id) {
            imageUrlService.getItemImageUrl(episode, ImageType.PRIMARY, fillWidth = 320, useSeriesForPrimary = false)
        }
    val data = episode?.data
    val played = data?.userData?.played == true
    val percent = data?.userData?.playedPercentage ?: 0.0
    val number = data?.indexNumber
    val anime = animeEpisodeLabel(episode)
    val filler = fillerTagFor(episode)
    val shape = RoundedCornerShape(10.dp)
    // Phones have no focus, so the picked episode gets the white ring instead
    val showRing = !isTv && selected
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (showRing) Color.White.copy(alpha = .14f) else Color.White.copy(alpha = .05f),
                contentColor = Color.White,
                focusedContainerColor = Color.White.copy(alpha = .14f),
                focusedContentColor = Color.White,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border = if (showRing) Border(BorderStroke(2.dp, Color.White), shape = shape) else Border.None,
                focusedBorder = Border(BorderStroke(3.dp, BenflixColors.Text), shape = shape),
            ),
        modifier =
            modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) onFocused.invoke() }
                .onKeyEvent {
                    if (isPlayKeyUp(it)) {
                        onPlayKey.invoke()
                        true
                    } else {
                        false
                    }
                },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(if (isTv) 150.dp else 128.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2A2A35)),
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .alpha(if (played && !selected) .55f else 1f),
                )
                if (!played && percent > 0) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(Color.Black.copy(alpha = .5f)),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth((percent / 100.0).toFloat().coerceIn(0f, 1f))
                                    .height(4.dp)
                                    .background(BenflixRed),
                        )
                    }
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f),
            ) {
                val numberText = anime?.removePrefix("Episode ") ?: number?.toString()
                Text(
                    text = listOfNotNull(numberText, episode?.name).joinToString(" · "),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (played && !selected) Color.White.copy(alpha = .65f) else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val minutes = episode?.timeRemainingOrRuntime?.inWholeMinutes
                val status =
                    when {
                        played -> "✓ Watched"
                        percent > 0 && minutes != null -> "${minutes}m left"
                        else -> listOfNotNull(minutes?.let { "${it}m" }, if (upNext) "Up next" else null).joinToString(" · ")
                    }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    filler?.let {
                        Text(
                            text = it.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = FillerYellow,
                        )
                    }
                    if (status.isNotBlank()) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (played) WatchedGreen else Color.White.copy(alpha = .8f),
                            maxLines = 1,
                        )
                    }
                }
                if (selected) {
                    data?.overview?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = BenflixColors.Soft,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isTv) {
                        Text(
                            text = "Hold OK: mark watched, mark up to here, more",
                            style = MaterialTheme.typography.labelSmall,
                            color = BenflixColors.Muted,
                        )
                    }
                }
            }
        }
    }
}
