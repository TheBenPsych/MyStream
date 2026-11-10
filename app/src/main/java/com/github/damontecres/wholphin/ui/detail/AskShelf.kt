package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.ui.discover.RequestPoster
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType

/**
 * Benflix: the row under "More like this" on a movie or show page. Titles like this one that
 * aren't on Benflix yet; pressing one opens its Requests page with Ask for it. Hidden for Kids,
 * Guest and when Requests isn't connected (the list is empty then).
 */
@Composable
fun AskShelf(
    items: List<DiscoverItem>,
    side: Dp,
    showName: Boolean,
    onClick: (DiscoverItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(start = side, end = side, bottom = 8.dp),
        ) {
            Text(
                text = "Ask for more like this",
                color = BenflixColors.Text,
                fontSize = BenflixType.lead,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Not on Benflix yet",
                color = BenflixColors.Muted,
                fontSize = BenflixType.small,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(items, key = { _, it -> "${it.type}-${it.id}" }) { _, item ->
                RequestPoster(
                    item = item,
                    width = if (showName) 104.dp else 100.dp,
                    showName = showName,
                    subtitle = item.releaseDate?.year?.toString(),
                    cornerNote = null,
                    onClick = { onClick(item) },
                    onLongClick = {},
                    onFocus = {},
                )
            }
        }
    }
}
