package com.github.damontecres.wholphin.ui.main

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
import androidx.compose.ui.res.stringResource
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.cards.BannerCard
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.cards.ItemRow

/**
 * The "Top 10" row: trending items that are on the server, with big rank numbers next to each poster
 */
@Composable
fun Top10Row(
    items: List<BaseItem>,
    title: String = stringResource(R.string.top_10_trending),
    onClickItem: (BaseItem) -> Unit,
    onFocusItem: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
    onLongClickItem: (BaseItem) -> Unit = {},
) {
    ItemRow(
        title = title,
        items = items,
        onClickItem = { _, item -> onClickItem(item) },
        onLongClickItem = { _, item -> onLongClickItem(item) },
        modifier = modifier,
        cardContent = { index, item, cardModifier, onClick, onLongClick ->
            // Ben's T1 pick: the glowing number in its own equal-width box, fully beside the poster
            // Ben's Glow outline: the number fills in while its poster has focus
            var lit by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.onFocusChanged { lit = it.hasFocus }) {
                com.github.damontecres.wholphin.ui.components.GlowRankNumber(
                    rank = index + 1,
                    posterHeight = Cards.height2x3,
                    lit = lit,
                    modifier = Modifier.padding(end = com.github.damontecres.wholphin.ui.components.RankGap),
                )
                BannerCard(
                    name = item?.name,
                    item = item,
                    aspectRatio = AspectRatios.TALL,
                    cardHeight = Cards.height2x3,
                    played = item?.data?.userData?.played ?: false,
                    favorite = item?.favorite ?: false,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    modifier =
                        cardModifier
                            .onFocusChanged {
                                if (it.isFocused && item != null) onFocusItem(item)
                            },
                )
            }
        },
    )
}

/**
 * A plain row of posters, used for the seasonal themed row
 */
@Composable
fun PosterRow(
    title: String,
    items: List<BaseItem>,
    onClickItem: (BaseItem) -> Unit,
    onFocusItem: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
    onLongClickItem: (BaseItem) -> Unit = {},
) {
    ItemRow(
        title = title,
        items = items,
        onClickItem = { _, item -> onClickItem(item) },
        onLongClickItem = { _, item -> onLongClickItem(item) },
        modifier = modifier,
        cardContent = { _, item, cardModifier, onClick, onLongClick ->
            BannerCard(
                name = item?.name,
                item = item,
                aspectRatio = AspectRatios.TALL,
                cardHeight = Cards.height2x3,
                played = item?.data?.userData?.played ?: false,
                favorite = item?.favorite ?: false,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier =
                    cardModifier
                        .onFocusChanged { if (it.isFocused && item != null) onFocusItem(item) },
            )
        },
    )
}

/**
 * Trending items that are not on the server yet. Clicking one opens its page where it can be requested.
 */
@Composable
fun TrendingMissingRow(
    items: List<DiscoverItem>,
    onClickItem: (DiscoverItem) -> Unit,
    onFocusItem: (DiscoverItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    ItemRow(
        title = stringResource(R.string.trending_not_on_server),
        items = items,
        onClickItem = { _, item -> onClickItem(item) },
        onLongClickItem = { _, _ -> },
        modifier = modifier,
        cardContent = { _, item, cardModifier, onClick, onLongClick ->
            DiscoverItemCard(
                item = item,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier =
                    cardModifier
                        .onFocusChanged { if (it.isFocused && item != null) onFocusItem(item) },
            )
        },
    )
}
