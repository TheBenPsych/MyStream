package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.LiteMode
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.tryRequestFocus

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun <T> ItemRow(
    title: String,
    items: List<T?>,
    onClickItem: (Int, T) -> Unit,
    onLongClickItem: (Int, T) -> Unit,
    cardContent: @Composable (
        index: Int,
        item: T?,
        modifier: Modifier,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
    ) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    showViewMore: Boolean = false,
    viewMoreCardContent: @Composable (Modifier) -> Unit = {},
) {
    // Benflix: give each poster a fixed ID so the row doesn't redraw everything (and focus doesn't jump)
    // when it updates. The same title twice in one row falls back to its spot, so IDs never clash.
    val keys =
        remember(items) {
            val seen = HashSet<String>()
            items.mapIndexed { i, item ->
                val id = (item as? BaseItem)?.id?.toString()
                if (id != null && seen.add(id)) id else "i$i"
            }
        }
    // Lay out (and so start loading the posters of) about one more screen of cards ahead of where you are,
    // so scrolling along a row shows pictures right away. Low-memory TVs in Lite mode skip this.
    val context = LocalContext.current
    val lite = remember { LiteMode.isOn(context) }
    val state =
        if (lite) {
            rememberLazyListState()
        } else {
            rememberLazyListState(cacheWindow = LazyLayoutCacheWindow(aheadFraction = 1f, behindFraction = .3f))
        }
    val firstFocus = remember { FocusRequester() }
    val focusRequester = remember { FocusRequester() }
    var position by rememberInt()

    val currentOnClickItem by rememberUpdatedState(onClickItem)
    val currentOnLongClickItem by rememberUpdatedState(onLongClickItem)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier.focusProperties {
                onEnter = {
                    focusRequester.tryRequestFocus()
                }
            },
    ) {
        ItemRowTitle(title)

        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(horizontalPadding),
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusGroup()
                    .focusRestorer(firstFocus)
                    .focusRequester(focusRequester),
        ) {
            itemsIndexed(items, key = { index, _ -> keys.getOrElse(index) { "i$index" } }) { index, item ->
                val cardModifier =
                    remember(index, position) {
                        if (index == position) {
                            Modifier.focusRequester(firstFocus)
                        } else {
                            Modifier
                        }
                    }

                val onClick =
                    remember(index, item) {
                        {
                            position = index
                            if (item != null) currentOnClickItem(index, item)
                        }
                    }

                val onLongClick =
                    remember(index, item) {
                        {
                            position = index
                            if (item != null) currentOnLongClickItem(index, item)
                        }
                    }

                cardContent.invoke(
                    index,
                    item,
                    cardModifier,
                    onClick,
                    onLongClick,
                )
            }
            if (showViewMore) {
                item {
                    val cardModifier =
                        remember(items.size, position) {
                            if (position == items.size) {
                                Modifier.focusRequester(firstFocus)
                            } else {
                                Modifier
                            }
                        }
                    viewMoreCardContent.invoke(cardModifier)
                }
            }
        }
    }
}

@Composable
@NonRestartableComposable
fun ItemRowTitle(
    title: String,
    modifier: Modifier = Modifier,
) = Text(
    text = title,
    style = MaterialTheme.typography.titleLarge,
    color = MaterialTheme.colorScheme.onBackground,
    modifier = modifier.padding(start = 8.dp),
)
