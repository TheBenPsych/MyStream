package com.github.damontecres.wholphin.ui.music

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.discover.RequestsJump
import com.github.damontecres.wholphin.ui.door.DOOR_HOME
import com.github.damontecres.wholphin.ui.door.DoorFrame
import com.github.damontecres.wholphin.ui.door.DoorMemory
import com.github.damontecres.wholphin.ui.door.DoorSection
import com.github.damontecres.wholphin.ui.door.doorHome
import com.github.damontecres.wholphin.ui.door.doorSectionStart
import com.github.damontecres.wholphin.ui.door.rememberDoorFrameState
import com.github.damontecres.wholphin.ui.main.DoorWipe
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.LocalTopBarOverlap
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Benflix music: inside the violet Music door (approved mp-shelf + m2-first). TV: a Movies-style top area
 * that describes the card you're on, with its cover fading in on the right; rows of square covers below.
 * Phones: the same rows. Easy read: just Made for you and Your playlists, bigger.
 * Redesign 2 (section 6): the door sits in the shared [DoorFrame]. Songs, Albums, Artists, Genres, Playlists
 * and Liked songs are in its side menu (chips on phones), so the old "Browse by" tiles are gone.
 */
@Composable
fun MusicDoorPage(
    preferences: UserPreferences,
    onClearBackdrop: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MusicDoorViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { onClearBackdrop() }
    LifecycleStartEffect(Unit) {
        viewModel.start()
        onStopOrDispose { }
    }
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.loaded || state.rows.isNotEmpty()) {
        if (state.loaded || state.rows.isNotEmpty()) DoorWipe.ready()
    }
    val easy = preferences.appPreferences.benflixPreferences.easyRead
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp || config.smallestScreenWidthDp >= 600
    val onTile: (MusicTile) -> Unit = { tile ->
        val nav = viewModel.navigationManager
        when (tile) {
            is MusicTile.Mix -> {
                nav.navigateTo(Destination.MusicList("mix", tile.mix.key, tile.mix.name))
            }

            is MusicTile.Item -> {
                nav.navigateTo(tile.item.destination())
            }

            is MusicTile.Liked -> {
                nav.navigateTo(Destination.MusicList("liked", title = "Liked songs"))
            }

            MusicTile.Ask -> {
                RequestsJump.music = true
                nav.navigateTo(Destination.Requests)
            }

            is MusicTile.SeeAll -> {
                nav.navigateTo(Destination.MusicList(tile.kind, title = tile.title))
            }

            is MusicTile.Browse -> {
                nav.navigateTo(Destination.MusicList("themes", tile.group, tile.title))
            }

            MusicTile.AllSongs -> {
                nav.navigateTo(Destination.MusicList("songs", title = "All songs"))
            }
        }
    }
    val overlap = LocalTopBarOverlap.current
    val frame = rememberDoorFrameState(BenflixDoor.MUSIC)
    // Ben: opened again within 10 minutes, the door comes back on the card you were on
    remember(viewModel) {
        DoorMemory.recall(BenflixDoor.MUSIC)?.let { spot ->
            spot.row?.toIntOrNull()?.let { viewModel.lastRow = it.coerceAtLeast(-1) }
            viewModel.lastIndex = spot.index
        }
        true
    }
    DisposableEffect(Unit) {
        onDispose {
            DoorMemory.keep(
                BenflixDoor.MUSIC,
                DoorMemory.Spot(section = frame.section, row = viewModel.lastRow.toString(), index = viewModel.lastIndex),
            )
        }
    }
    val sections = remember(easy) { musicSections(easy) }

    if (state.loaded && state.rows.isEmpty()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxSize().padding(start = side(wide), end = side(wide), top = overlap + 40.dp),
        ) {
            Text(
                text =
                    when {
                        state.noMusic -> "No music on Glowfin yet"
                        else -> "Couldn't reach Glowfin"
                    },
                color = BenflixColors.Text,
                fontSize = BenflixType.pageTitle,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text =
                    if (state.noMusic) {
                        "Once Tower has a Music library, your songs and playlists show up here."
                    } else {
                        "Check the TV is online, then try again."
                    },
                color = BenflixColors.Soft,
                fontSize = BenflixType.body,
            )
            if (!state.noMusic) {
                val retry = remember { FocusRequester() }
                MusicButton(
                    "Try again",
                    BenflixIcon.REFRESH,
                    onClick = viewModel::retry,
                    primary = true,
                    modifier = Modifier.focusRequester(retry),
                )
                LaunchedEffect(Unit) {
                    delay(80)
                    if (isTv) retry.tryRequestFocus()
                }
            }
        }
        return
    }

    val rows = if (easy) state.rows.filter { it.key == "mixes" || it.key == "playlists" || it.key == "jump" } else state.rows
    val cardSize =
        if (easy) {
            168.dp
        } else if (wide) {
            136.dp
        } else {
            118.dp
        }
    val quick = state.quick
    val firstFocus = remember { FocusRequester() }
    // True while focus is somewhere in the page (not the top bar or the side menu)
    var rowsFocused by remember { mutableStateOf(false) }
    // Music redesign 3 (approved x-filter chips on Home): All, Mixes, Playlists, Albums, Artists, New to you
    var chip by remember { mutableStateOf(viewModel.lastChip) }
    LaunchedEffect(chip) { viewModel.lastChip = chip }
    val ready = quick.isNotEmpty() || rows.isNotEmpty()
    LaunchedEffect(ready, frame.section) {
        // Ben (approved g2-home): focus starts on Liked songs, the first quick pick; coming Back lands on the
        // card you were on. The cards can take a moment to appear (and the top bar may grab focus first), so
        // keep asking for a little while until the card really has it.
        if (isTv && ready && !rowsFocused && frame.section == DOOR_HOME) {
            var steady = 0
            repeat(40) {
                delay(60)
                if (rowsFocused) {
                    if (++steady >= 4) return@LaunchedEffect
                } else {
                    steady = 0
                    firstFocus.tryRequestFocus()
                }
            }
        }
    }

    // Back (approved dr-p-back): first up to Liked songs, then out of the door
    val listState = rememberLazyListState()
    var topRequest by remember { mutableIntStateOf(0) }
    // Where focus goes when the page appears: the card you were on (Liked songs after Back)
    var target by remember { mutableStateOf(viewModel.lastRow to viewModel.lastIndex) }
    LaunchedEffect(Unit) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 }.collect { frame.atTop = it && viewModel.lastRow < 0 && chip == "all" }
    }
    frame.toTop = {
        viewModel.lastRow = -1
        viewModel.lastIndex = 0
        target = -1 to 0
        chip = "all"
        frame.atTop = true
        topRequest++
    }
    LaunchedEffect(topRequest) {
        if (topRequest == 0) return@LaunchedEffect
        listState.scrollToItem(0)
        delay(160)
        if (isTv) firstFocus.tryRequestFocus()
    }
    // The phone tabs and the browser's left side pick a place from outside this page
    DisposableEffect(frame) {
        MusicPlaceNav.frame = frame
        onDispose { if (MusicPlaceNav.frame === frame) MusicPlaceNav.frame = null }
    }
    val pending = MusicPlaceNav.pending
    LaunchedEffect(pending) {
        if (pending != null) {
            MusicPlaceNav.pending = null
            frame.pick(pending)
        }
    }
    LaunchedEffect(frame.section) { MusicPlaceNav.place = frame.section }

    fun isTarget(
        row: Int,
        index: Int,
        size: Int,
    ): Boolean {
        val wantRow =
            if (target.first < 0 ||
                quick.isEmpty()
            ) {
                (if (quick.isEmpty()) 0 else -1)
            } else {
                target.first.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
            }
        return row == wantRow && index == target.second.coerceIn(0, (size - 1).coerceAtLeast(0))
    }
    DoorFrame(frame, sections, modifier, phoneChips = false) { inner ->
        if (frame.section != DOOR_HOME) {
            MusicSectionPage(frame.section, wide, inner)
        } else {
            Column(inner.fillMaxSize().onFocusChanged { rowsFocused = it.hasFocus }) {
                Spacer(Modifier.height(if (wide) overlap + 8.dp else 4.dp))
                Text(
                    text = state.greeting.ifBlank { "Music" },
                    color = BenflixColors.Text,
                    fontSize = if (easy) BenflixType.hero else BenflixType.pageTitle,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = side(wide), end = side(wide)),
                )
                if (!easy) {
                    MusicHomeChips(
                        chip = chip,
                        onChip = { chip = it },
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                        start = side(wide),
                    )
                }
                if (chip != "all") {
                    MusicChipGrid(
                        chip = chip,
                        state = state,
                        wide = wide,
                        onTile = onTile,
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(start = side(wide), end = 8.dp, top = 8.dp),
                    )
                    return@Column
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 10.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(if (wide) 26.dp else 20.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    if (quick.isNotEmpty()) {
                        item(key = "quick") {
                            QuickPicks(
                                tiles = quick,
                                across = if (wide) 4 else 2,
                                big = easy,
                                onTile = onTile,
                                onFocus = { i ->
                                    viewModel.lastRow = -1
                                    viewModel.lastIndex = i
                                    frame.atTop = listState.firstVisibleItemIndex == 0
                                },
                                focusFor = { i -> if (isTarget(-1, i, quick.size)) Modifier.focusRequester(firstFocus) else Modifier },
                                modifier = Modifier.padding(horizontal = side(wide)),
                            )
                        }
                    }
                    rows.forEachIndexed { rowIndex, row ->
                        item(key = row.key) {
                            Column {
                                MusicRowName(row.title, Modifier.padding(start = side(wide), bottom = 8.dp))
                                val rowState = rememberLazyListState()
                                if (rowIndex == 0) {
                                    LaunchedEffect(topRequest) { if (topRequest > 0) rowState.scrollToItem(0) }
                                }
                                LazyRow(
                                    state = rowState,
                                    contentPadding = PaddingValues(horizontal = side(wide)),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    itemsIndexed(row.tiles, key = { _, t -> t.key }) { i, tile ->
                                        MusicTileCard(
                                            tile = tile,
                                            size = cardSize,
                                            onClick = { onTile(tile) },
                                            onFocus = {
                                                viewModel.lastRow = rowIndex
                                                viewModel.lastIndex = i
                                                frame.atTop = false
                                            },
                                            modifier =
                                                if (isTarget(rowIndex, i, row.tiles.size)) {
                                                    Modifier.focusRequester(firstFocus)
                                                } else {
                                                    Modifier
                                                },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Music redesign 3 (approved g2-places): the same four places everywhere, in the same order: Home, Search, Your
 * library and Ask for music (TV side menu, phone tabs along the bottom, the browser's left side).
 */
private fun musicSections(
    @Suppress("UNUSED_PARAMETER") easy: Boolean,
): List<DoorSection> =
    listOf(
        doorHome(),
        DoorSection(MUSIC_SEARCH, "Search", BenflixIcon.SEARCH),
        DoorSection(MUSIC_LIBRARY, "Your library", BenflixIcon.LIST),
        DoorSection(MUSIC_ASK, "Ask for music", BenflixIcon.PLUS),
    )

/** A place from the Music menu (Search, Your library, Ask for music), in place of the door's Home */
@Composable
private fun MusicSectionPage(
    key: String,
    wide: Boolean,
    modifier: Modifier,
) {
    val inner =
        modifier.padding(
            top = if (wide) LocalTopBarOverlap.current else 0.dp,
            // The places need a side margin to clear the side menu
            start = if (wide) doorSectionStart() else 0.dp,
        )
    when (key) {
        MUSIC_SEARCH -> {
            MusicSearchPlace(wide = wide, modifier = inner)
        }

        MUSIC_LIBRARY -> {
            MusicLibraryPlace(wide = wide, modifier = inner)
        }

        MUSIC_ASK -> {
            // Requests > Music, right inside the Music door (every profile but Kids; Ben okays the others' asks)
            Box(inner.fillMaxSize()) {
                com.github.damontecres.wholphin.ui.discover
                    .MusicShelf()
            }
        }
    }
}

private fun side(wide: Boolean): Dp = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge

/** One tile on the Music page, whatever kind it is */
@Composable
fun MusicTileCard(
    tile: MusicTile,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: (() -> Unit)? = null,
) {
    val imageUrlService = LocalImageUrlService.current
    val px = with(LocalDensity.current) { (size / 2).roundToPx() }
    when (tile) {
        is MusicTile.Item -> {
            MusicCard(item = tile.item, size = size, onClick = onClick, onFocus = onFocus, modifier = modifier)
        }

        is MusicTile.Mix -> {
            val urls = remember(tile.mix.key, px) { tile.mix.coverAlbumIds.map { imageUrlService.albumCover(it, px) } }
            MusicCardFrame(
                name = tile.mix.name,
                line = tile.mix.line,
                size = size,
                onClick = onClick,
                onFocus = onFocus,
                modifier = modifier,
            ) {
                MixCover(name = tile.mix.name, coverUrls = urls, size = size, modifier = Modifier.fillMaxSize())
            }
        }

        is MusicTile.Liked -> {
            MusicCardFrame(
                name = "Liked songs",
                line = songCount(tile.count),
                size = size,
                onClick = onClick,
                onFocus = onFocus,
                modifier = modifier,
            ) { LikedCover(Modifier.fillMaxSize()) }
        }

        MusicTile.Ask -> {
            MusicCardFrame(
                name = "Ask for music",
                line = "Songs, albums, playlists",
                size = size,
                onClick = onClick,
                onFocus = onFocus,
                modifier = modifier,
            ) {
                ActionCover("Ask for music", BenflixIcon.PLUS, Modifier.fillMaxSize())
            }
        }

        is MusicTile.SeeAll -> {
            MusicCardFrame(name = "See all", line = null, size = size, onClick = onClick, onFocus = onFocus, modifier = modifier) {
                ActionCover(tile.title, BenflixIcon.ARROW, Modifier.fillMaxSize())
            }
        }

        is MusicTile.Browse -> {
            MusicCardFrame(
                name = tile.title,
                line = browseLine(tile.group),
                size = size,
                onClick = onClick,
                onFocus = onFocus,
                modifier = modifier,
            ) { BrowseCover(tile, size, Modifier.fillMaxSize()) }
        }

        MusicTile.AllSongs -> {
            MusicCardFrame(name = "All songs", line = "A to Z", size = size, onClick = onClick, onFocus = onFocus, modifier = modifier) {
                ActionCover("All songs", BenflixIcon.HEADPHONES, Modifier.fillMaxSize())
            }
        }
    }
}

/** "Static Bloom · 2024 · 10 songs" */
fun albumLine(item: BaseItem): String =
    listOfNotNull(
        item.data.albumArtist ?: item.data.artists?.firstOrNull(),
        item.data.productionYear?.toString(),
        item.data.childCount?.let { songCount(it) },
    ).joinToString(" · ")
