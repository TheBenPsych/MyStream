package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay

/**
 * While the buttons at the top of a details page have focus, the page stays scrolled right to the
 * top so the name shows. Pressing Up quickly used to stop the scroll part way, leaving the name
 * hidden under the top bar; this tries again whenever the page comes to rest short of the top.
 */
@Composable
fun KeepHeroAtTop(
    listState: LazyListState,
    heroFocused: Boolean,
) {
    LaunchedEffect(heroFocused) {
        if (!heroFocused) return@LaunchedEffect
        snapshotFlow {
            !listState.isScrollInProgress &&
                (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)
        }.collect { shortOfTop ->
            if (shortOfTop) {
                delay(60)
                listState.animateScrollToItem(0)
            }
        }
    }
}
