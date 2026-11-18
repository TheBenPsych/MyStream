package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.services.AskKind
import com.github.damontecres.wholphin.ui.discover.RequestPoster

/**
 * Glowfin D1 (Ben 2026-11-18): "Ask for similar" as ONE row under More like this. The closest matches
 * first, then the popular ones sharing its genres. Each poster has a small "Ask" tag; pressing it opens
 * its Ask for it page. The title page draws the row's name.
 */
@Composable
fun AskForSimilarRow(
    best: List<DiscoverItem>,
    more: List<DiscoverItem>,
    side: Dp,
    showName: Boolean,
    onClick: (DiscoverItem) -> Unit,
) {
    val items = remember(best, more) { (best + more).distinctBy { "${it.type}-${it.id}" } }
    if (items.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = side),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        itemsIndexed(items, key = { _, it -> "${it.type}-${it.id}" }) { _, item ->
            RequestPoster(
                item = item,
                width = if (showName) 104.dp else 100.dp,
                showName = showName,
                subtitle = item.releaseDate?.year?.toString(),
                cornerNote = "Ask",
                onClick = { onClick(item) },
                onLongClick = {},
                onFocus = {},
            )
        }
    }
}

/** "Ask for similar movies" / "... shows" / "... anime" / "... cartoons" */
fun askForSimilarName(
    kind: AskKind,
    show: Boolean,
): String =
    when (kind) {
        AskKind.LIVE -> if (show) "Ask for similar shows" else "Ask for similar movies"
        else -> "Ask for similar ${kind.label}"
    }
