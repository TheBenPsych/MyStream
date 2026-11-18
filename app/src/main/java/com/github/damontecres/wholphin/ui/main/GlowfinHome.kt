package com.github.damontecres.wholphin.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.door.DOOR_HOME
import com.github.damontecres.wholphin.ui.door.DoorFrameState
import com.github.damontecres.wholphin.ui.door.DoorViewModel
import com.github.damontecres.wholphin.ui.door.EmptyDoor
import com.github.damontecres.wholphin.ui.door.PageRows
import com.github.damontecres.wholphin.ui.door.PhoneDoor
import com.github.damontecres.wholphin.ui.door.WideDoor
import com.github.damontecres.wholphin.ui.nav.LocalTopBarOverlap
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont
import com.github.damontecres.wholphin.ui.touch.rememberIsTv

/**
 * Glowfin Home (ui-6, Ben 2026-11-18): the same calm layout as Movies and Shows. The top area describes the
 * poster you're on (it replaces the sliding spotlight), then Continue watching, Next up and the rows this person
 * picked in Settings > Look > Home rows (Recently added, Top 10, Because you watched, Trending not on Tower, Anime
 * airing, Watch it again). The Today board's news is one optional "What's new" row. No greeting or clock line,
 * no zooming pictures, nothing that keeps moving, so it stays smooth on the weaker TCL TVs.
 */
@Composable
fun GlowfinHome(
    news: List<TodayCard>,
    onSeeAllNews: (() -> Unit)?,
    onNewsSeen: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DoorViewModel = hiltViewModel(key = "glowfin-home"),
) {
    val changes = PageRows.changes.intValue
    LifecycleStartEffect(changes) {
        viewModel.startHome()
        onStopOrDispose { }
    }
    val state by viewModel.state.collectAsState()
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp || config.smallestScreenWidthDp >= 600
    // Home isn't a door, but it keeps the same "at the top" and "back to the top" as one
    val frame = remember { DoorFrameState(BenflixDoor.MOVIES, DOOR_HOME, jumps = false) }
    // Back first goes back to the top of Home
    BackHandler(enabled = !frame.atTop) { frame.toTop() }
    val onClick: (BaseItem) -> Unit = { viewModel.navigationManager.navigateTo(it.destination()) }
    if (state.loaded && state.rows.isEmpty() && news.isEmpty()) {
        EmptyDoor("Glowfin", state.offline, viewModel::retry, modifier.padding(top = LocalTopBarOverlap.current))
        return
    }
    val extra: Map<String, @Composable () -> Unit> =
        if (news.isEmpty()) {
            emptyMap()
        } else {
            mapOf(PageRows.WHATS_NEW to { WhatsNewRow(news, wide, onSeeAllNews, onNewsSeen) })
        }
    if (wide) {
        WideDoor(state, easy = false, isTv = isTv, viewModel = viewModel, frame = frame, onClick = onClick, onSeeAll = {
        }, modifier = modifier, extra = extra)
    } else {
        PhoneDoor(
            state,
            easy = false,
            frame = frame,
            onClick = onClick,
            onSeeAll = {},
            onOpen = { viewModel.navigationManager.navigateTo(it) },
            modifier = modifier,
            extra = extra,
        )
    }
}

/** "What's new": the Today cards (arrivals, notes, Tower news) in one row, with See all at the end */
@Composable
private fun WhatsNewRow(
    cards: List<TodayCard>,
    wide: Boolean,
    onSeeAll: (() -> Unit)?,
    onSeen: () -> Unit,
) {
    LaunchedEffect(cards.size) { onSeen() }
    val side = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "What's new",
            color = BenflixColors.Text,
            fontFamily = GlowfinDisplayFont,
            fontSize = BenflixType.lead,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = side, end = side, bottom = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = side),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cards, key = { it.id }) { card ->
                FloatingCard(card = card, scale = 1f, modifier = Modifier.width(if (wide) 300.dp else 250.dp))
            }
            if (onSeeAll != null) {
                item(key = "see-all") { SeeAllCard(onClick = onSeeAll, scale = 1f) }
            }
        }
    }
}
