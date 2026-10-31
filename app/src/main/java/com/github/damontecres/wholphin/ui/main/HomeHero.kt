package com.github.damontecres.wholphin.ui.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton
import kotlin.time.Duration

enum class FeaturedReason {
    NEW,
    PICK,
}

/**
 * An item shown in the rotating banner at the top of the home page
 */
data class FeaturedItem(
    val item: BaseItem,
    val reason: FeaturedReason,
)

/**
 * The buttons & page indicator for the rotating home page banner.
 *
 * The banner's title, details, and description are drawn by the page header above this.
 */
@Composable
fun HomeHero(
    featured: List<FeaturedItem>,
    index: Int,
    onClickPlay: (BaseItem) -> Unit,
    onClickInfo: (BaseItem) -> Unit,
    onClickFavorite: (BaseItem) -> Unit,
    onClickSurprise: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val current = featured.getOrNull(index) ?: return
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .padding(start = 8.dp, bottom = 16.dp)
                .onFocusChanged { onFocusChanged(it.hasFocus) },
    ) {
        Text(
            text =
                stringResource(
                    when (current.reason) {
                        FeaturedReason.NEW -> R.string.featured_new
                        FeaturedReason.PICK -> R.string.featured_pick
                    },
                ).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.focusRestorer(focusRequester),
        ) {
            ExpandablePlayButton(
                title = R.string.play,
                resume = Duration.ZERO,
                icon = Icons.Default.PlayArrow,
                onClick = { onClickPlay(current.item) },
                modifier = Modifier.focusRequester(focusRequester),
            )
            ExpandableFaButton(
                title = R.string.more_info,
                iconStringRes = R.string.fa_circle_info,
                onClick = { onClickInfo(current.item) },
            )
            ExpandableFaButton(
                title = if (current.item.favorite) R.string.in_my_list else R.string.my_list,
                iconStringRes = if (current.item.favorite) R.string.fa_check else R.string.fa_plus,
                onClick = { onClickFavorite(current.item) },
            )
            ExpandableFaButton(
                title = R.string.surprise_me,
                iconStringRes = R.string.fa_dice,
                onClick = onClickSurprise,
            )
        }
        if (featured.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                featured.indices.forEach { i ->
                    val width by animateDpAsState(if (i == index) 28.dp else 10.dp)
                    Box(
                        modifier =
                            Modifier
                                .width(width)
                                .height(4.dp)
                                .background(
                                    if (i == index) Color.White else Color.White.copy(alpha = .35f),
                                    RoundedCornerShape(2.dp),
                                ),
                    )
                }
            }
        }
    }
}

/**
 * The muted trailer preview shown behind the banner, faded into the background on the left & bottom
 */
@Composable
fun HeroTrailer(
    player: Player,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier =
            modifier
                .fillMaxWidth(.7f)
                .aspectRatio(16f / 9f),
    ) {
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to background,
                            .35f to background.copy(alpha = .4f),
                            .6f to Color.Transparent,
                        ),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            .6f to Color.Transparent,
                            1f to background,
                        ),
                    ),
        )
    }
}
