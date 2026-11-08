package com.github.damontecres.wholphin.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.ChannelPrefs
import com.github.damontecres.wholphin.services.ChannelSession
import com.github.damontecres.wholphin.services.ChannelTileOrder
import com.github.damontecres.wholphin.services.FAVOURITES_GROUP
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.group
import com.github.damontecres.wholphin.ui.components.SearchEditTextBox
import com.github.damontecres.wholphin.ui.theme.BenflixMotion
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import java.util.UUID

/**
 * The guide that slides in while watching a channel, so you can look for something else and keep
 * watching. On a TV it sits on the right; on a phone held upright it fills the bottom half.
 * Pick a channel to switch to it, Back closes it.
 */
@Composable
fun ChannelSideGuide(
    visible: Boolean,
    playingItemId: UUID?,
    navigationManager: NavigationManager,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = visible) { onClose() }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val upright = maxHeight > maxWidth
        AnimatedVisibility(
            visible = visible,
            enter = if (upright) slideInVertically { it } else slideInHorizontally { it },
            exit = fadeOut(BenflixMotion.exit()),
            modifier =
                if (upright) {
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.55f)
                } else {
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(if (maxWidth < 700.dp) .5f else .36f)
                },
        ) {
            SideGuideContent(playingItemId, navigationManager, onClose)
        }
    }
}

@Composable
private fun SideGuideContent(
    playingItemId: UUID?,
    navigationManager: NavigationManager,
    onClose: () -> Unit,
) {
    val lineup = remember { ChannelSession.lineup }
    val favourites by ChannelPrefs.favourites.collectAsState()
    val hidden by ChannelPrefs.hidden.collectAsState()
    val playingNumber = ChannelSession.lastNumber
    val playing = remember(lineup, playingNumber) { lineup.firstOrNull { it.number == playingNumber } }
    val groups =
        remember(lineup, hidden, favourites) {
            val present = lineup.map { it.group() }.distinct()
            listOfNotNull(FAVOURITES_GROUP.takeIf { favourites.isNotEmpty() }) +
                ChannelTileOrder.filter { it in present && it !in hidden } +
                present.filter { it !in ChannelTileOrder && it !in hidden }
        }
    var group by remember { mutableStateOf(playing?.group()?.takeIf { it in groups } ?: groups.firstOrNull()) }
    var query by remember { mutableStateOf("") }
    val shown =
        remember(lineup, group, query, favourites) {
            when {
                query.isNotBlank() -> searchChannels(lineup, query)
                group == FAVOURITES_GROUP -> lineup.filter { it.key in favourites }
                else -> lineup.filter { it.group() == group }
            }
        }
    val listState = rememberLazyListState()
    val playingFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        val index = shown.indexOfFirst { it.number == playingNumber }
        if (index > 0) listState.scrollToItem(index)
        delay(120)
        if (index >= 0) playingFocus.tryRequestFocus() else firstFocus.tryRequestFocus()
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xF00B0B0D))
                .padding(12.dp),
    ) {
        SearchEditTextBox(
            value = query,
            onValueChange = { query = it },
            onSearchClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
        if (query.isBlank()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(groups, key = { it }) { name ->
                    Surface(
                        onClick = { group = name },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        colors =
                            ClickableSurfaceDefaults.colors(
                                containerColor = if (name == group) Color(0xFF3A3A42) else Color(0xFF1C1C21),
                                contentColor = Color.White,
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black,
                            ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    ) {
                        Text(
                            text = groupLabel(name),
                            fontSize = BenflixType.tag,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            Text(
                if (query.isBlank()) "No channels here." else "No channel called \"${query.trim()}\"",
                fontSize = BenflixType.small,
                color = Color(0xFF8B8B94),
            )
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.key }) { schedule ->
                    val isPlaying = schedule.number == playingNumber && ChannelSession.isWatching(playingItemId)
                    ChannelRow(
                        schedule = schedule,
                        playing = isPlaying,
                        onClick = {
                            if (isPlaying) {
                                onClose()
                            } else {
                                onClose()
                                ChannelSession.tune(navigationManager, schedule, replacePlayer = true)
                            }
                        },
                        modifier =
                            when {
                                isPlaying -> Modifier.focusRequester(playingFocus)
                                schedule == shown.firstOrNull() -> Modifier.focusRequester(firstFocus)
                                else -> Modifier
                            },
                    )
                }
            }
        }
    }
}
