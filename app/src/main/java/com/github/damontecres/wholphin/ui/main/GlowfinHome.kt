package com.github.damontecres.wholphin.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.door.DOOR_HOME
import com.github.damontecres.wholphin.ui.door.DoorFrameState
import com.github.damontecres.wholphin.ui.door.DoorViewModel
import com.github.damontecres.wholphin.ui.door.EmptyDoor
import com.github.damontecres.wholphin.ui.door.PageRows
import com.github.damontecres.wholphin.ui.door.PhoneDoor
import com.github.damontecres.wholphin.ui.door.WideDoor
import com.github.damontecres.wholphin.ui.nav.LocalTopBarOverlap
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.touch.rememberIsTv

/**
 * Glowfin Home (ui-6, Ben 2026-11-18): the same calm layout as Movies and Shows. The top area describes the
 * poster you're on (it replaces the sliding spotlight), then Continue watching, Next up and the rows this person
 * picked in Settings > Look > Home rows (Recently added, Top 10, Because you watched, Trending not on Tower, Anime
 * airing, Watch it again). No news row (Ben 2026-11-18). No greeting or clock line,
 * no zooming pictures, nothing that keeps moving, so it stays smooth on the weaker TCL TVs.
 */
@Composable
fun GlowfinHome(
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
    if (state.loaded && state.rows.isEmpty()) {
        EmptyDoor("Glowfin", state.offline, viewModel::retry, modifier.padding(top = LocalTopBarOverlap.current))
        return
    }
    if (wide) {
        WideDoor(state, easy = false, isTv = isTv, viewModel = viewModel, frame = frame, onClick = onClick, onSeeAll = {
        }, modifier = modifier)
    } else {
        PhoneDoor(
            state,
            easy = false,
            frame = frame,
            onClick = onClick,
            onSeeAll = {},
            onOpen = { viewModel.navigationManager.navigateTo(it) },
            modifier = modifier,
        )
    }
}
