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
import com.github.damontecres.wholphin.services.AskKind
import com.github.damontecres.wholphin.ui.discover.RequestPoster
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType

/**
 * Benflix (Ben, 2026-11-16): the "Ask for similar" tab on a movie or show page. Only titles of the same kind
 * (anime / cartoons / live action) that aren't on Benflix yet: the closest matches first, then more popular
 * ones sharing its genres. Pressing one opens its Requests page with Ask for it. Hidden for Kids, Guest,
 * Easy and when Requests isn't connected (both lists are empty then).
 */
@Composable
fun AskForSimilarTab(
    kind: AskKind,
    best: List<DiscoverItem>,
    more: List<DiscoverItem>,
    side: Dp,
    showName: Boolean,
    onClick: (DiscoverItem) -> Unit,
) {
    val note = "Only ${kind.label} · not on Benflix yet"
    Column(Modifier.fillMaxWidth()) {
        AskShelf(
            items = best,
            side = side,
            showName = showName,
            onClick = onClick,
            name = "Closest matches",
            note = note,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        AskShelf(
            items = more,
            side = side,
            showName = showName,
            onClick = onClick,
            name = "More ${kind.label} to ask for",
            note = if (best.isEmpty()) note else null,
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}

/** One row of titles to ask for, with its name and an optional grey note beside it */
@Composable
fun AskShelf(
    items: List<DiscoverItem>,
    side: Dp,
    showName: Boolean,
    onClick: (DiscoverItem) -> Unit,
    modifier: Modifier = Modifier,
    name: String = "Ask for more like this",
    note: String? = "Not on Benflix yet",
) {
    if (items.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(start = side, end = side, bottom = 8.dp),
        ) {
            Text(
                text = name,
                color = BenflixColors.Text,
                fontSize = BenflixType.lead,
                fontWeight = FontWeight.Bold,
            )
            if (note != null) {
                Text(
                    text = note,
                    color = BenflixColors.Muted,
                    fontSize = BenflixType.small,
                    fontWeight = FontWeight.SemiBold,
                )
            }
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
