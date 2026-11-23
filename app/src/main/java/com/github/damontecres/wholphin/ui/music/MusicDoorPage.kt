package com.github.damontecres.wholphin.ui.music

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.cards.LocalCardNamesOff
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.door.CoverDoorTop
import com.github.damontecres.wholphin.ui.door.CoverTopWords
import com.github.damontecres.wholphin.ui.door.DOOR_HOME
import com.github.damontecres.wholphin.ui.door.DoorFrame
import com.github.damontecres.wholphin.ui.door.DoorMemory
import com.github.damontecres.wholphin.ui.door.DoorSection
import com.github.damontecres.wholphin.ui.door.coverTopHeight
import com.github.damontecres.wholphin.ui.door.doorTag
import com.github.damontecres.wholphin.ui.door.rememberDoorFrameState
import com.github.damontecres.wholphin.ui.main.DoorWipe
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.LocalTopBarOverlap
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.theme.Glowfin
import com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.ScrollToTopBringIntoViewSpec
import com.github.damontecres.wholphin.ui.util.scrollAsOneBlock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUIDOrNull

/**
 * Glowfin Music (Ben's M1 pick, Nov 2026): the same word tabs as Movies and Shows along the top: For you,
 * Albums, Artists, Playlists, Songs. For you: on TV and wide screens a top area that describes the card you're
 * on (what the lit mix is), with its cover fading in on the right; then Made for you, Your playlists (Liked
 * songs first, Ask for music and Import at the end) and a few more rows. Phones: the same tabs and rows.
 * Easy read: just Made for you and Your playlists, bigger. Ask for music opens over the page; Back closes it.
 */
@OptIn(ExperimentalFoundationApi::class)
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
                MusicPlaceNav.openAsk()
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

    // Ask for music (or Import a playlist) sits over the Music page; Back goes back to the tabs
    val ask = MusicPlaceNav.ask
    if (ask != null) {
        BackHandler { MusicPlaceNav.ask = null }
        Box(
            modifier
                .fillMaxSize()
                .padding(top = if (wide) overlap else 0.dp, start = if (wide) side(true) else 0.dp),
        ) {
            com.github.damontecres.wholphin.ui.discover
                .MusicShelf()
        }
        return
    }

    val frame = rememberDoorFrameState(BenflixDoor.MUSIC)
    // Ben: opened again within 10 minutes, the door comes back on the card you were on
    remember(viewModel) {
        DoorMemory.recall(BenflixDoor.MUSIC)?.let { spot ->
            spot.row?.toIntOrNull()?.let { viewModel.lastRow = it.coerceAtLeast(0) }
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
    val sections = remember { musicSections() }

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
                fontFamily = GlowfinDisplayFont,
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

    // For you (M1): Made for you, Your playlists, then the rest (Artists has its own tab now)
    val rows =
        remember(state.rows, viewModel.isAdmin) {
            val kept = state.rows.filter { it.key != "artists" }
            val order = listOf("mixes", "playlists")
            val sorted = kept.sortedBy { r -> order.indexOf(r.key).let { if (it < 0) order.size else it } }
            sorted.map { r ->
                if (r.key == "playlists") {
                    // Ask for music and Import a playlist (Ben only, like Requests) at the end of Your playlists
                    val import: List<MusicTile> = if (viewModel.isAdmin) listOf(MusicTile.SeeAll(IMPORT_KIND, "Import")) else emptyList()
                    r.copy(tiles = r.tiles + MusicTile.Ask + import)
                } else {
                    r
                }
            }
        }
    val cardSize = if (wide) 136.dp else 118.dp
    val firstFocus = remember { FocusRequester() }
    // True while focus is somewhere in the page (not the tabs or the menu)
    var rowsFocused by remember { mutableStateOf(false) }
    val ready = rows.isNotEmpty()
    LaunchedEffect(ready, frame.section) {
        // Focus starts on the first mix; coming Back lands on the card you were on. The cards can take a moment
        // to appear (and the menu may grab focus first), so keep asking for a little while until a card has it.
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

    // Back (approved dr-p-back): first up to the first mix, then out of Music
    val listState = rememberLazyListState()
    var topRequest by remember { mutableIntStateOf(0) }
    var target by remember { mutableStateOf(viewModel.lastRow.coerceAtLeast(0) to viewModel.lastIndex) }
    LaunchedEffect(Unit) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 }.collect {
            frame.atTop =
                it && viewModel.lastRow <= 0 && viewModel.lastIndex == 0
        }
    }
    frame.toTop = {
        viewModel.lastRow = 0
        viewModel.lastIndex = 0
        target = 0 to 0
        frame.atTop = true
        topRequest++
    }
    LaunchedEffect(topRequest) {
        if (topRequest == 0) return@LaunchedEffect
        listState.scrollToItem(0)
        delay(160)
        if (isTv) firstFocus.tryRequestFocus()
    }
    // Anything outside this page (an artist page's Ask, the browser) can pick a tab
    DisposableEffect(frame) {
        MusicPlaceNav.frame = frame
        onDispose { if (MusicPlaceNav.frame === frame) MusicPlaceNav.frame = null }
    }
    val pending = MusicPlaceNav.pending
    LaunchedEffect(pending) {
        if (pending != null) {
            MusicPlaceNav.pending = null
            if (sections.any { it.key == pending }) frame.pick(pending)
        }
    }
    LaunchedEffect(frame.section) { MusicPlaceNav.place = frame.section }

    fun isTarget(
        row: Int,
        index: Int,
        size: Int,
    ): Boolean {
        val wantRow = target.first.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
        return row == wantRow && index == target.second.coerceIn(0, (size - 1).coerceAtLeast(0))
    }
    // The card the remote is on: only the top area reads it, so moving along a row doesn't rebuild the page
    val lit = remember { mutableStateOf<MusicTile?>(null) }
    DoorFrame(frame, sections, modifier, ready = state.loaded || ready, tabs = true, tabsInHeader = true) { inner ->
        if (frame.section != DOOR_HOME) {
            MusicSectionPage(frame.section, wide, preferences, inner)
        } else {
            Column(inner.fillMaxSize().onFocusChanged { rowsFocused = it.hasFocus }) {
                if (wide) {
                    MusicTopArea(
                        lit = lit,
                        first = rows.firstOrNull()?.tiles?.firstOrNull(),
                        overlap = LocalTopBarOverlap.current,
                    )
                }
                // Ben 2026-11-21: like Movies, Shows and Books on TV, the row you move to jumps up whole (name and
                // all) right under the top area, so its name is never cut off. Rows still slide sideways as before.
                val defaultSpec = LocalBringIntoViewSpec.current
                val toTop = remember { ScrollToTopBringIntoViewSpec(0f) }
                CompositionLocalProvider(
                    LocalBringIntoViewSpec provides if (isTv) toTop else defaultSpec,
                    // Glowfin new look: cover art only in the rows (the top says what the card is)
                    LocalCardNamesOff provides true,
                ) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = if (wide) 4.dp else 10.dp, bottom = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(if (wide) 26.dp else 20.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        rows.forEachIndexed { rowIndex, row ->
                            item(key = row.key) {
                                CompositionLocalProvider(LocalBringIntoViewSpec provides defaultSpec) {
                                    Column(Modifier.scrollAsOneBlock()) {
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
                                                    onClick = {
                                                        if (tile is MusicTile.SeeAll && tile.kind == IMPORT_KIND) {
                                                            MusicPlaceNav.openAsk(import = true)
                                                        } else {
                                                            onTile(tile)
                                                        }
                                                    },
                                                    onFocus = {
                                                        viewModel.lastRow = rowIndex
                                                        viewModel.lastIndex = i
                                                        lit.value = tile
                                                        frame.atTop = rowIndex == 0 && i == 0
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
    }
}

/** The Import tile at the end of Your playlists (Ben only): opens Import a playlist on Ask for music */
private const val IMPORT_KIND = "import"

/** Glowfin (M1): the Music tabs, the same everywhere (TV, phones, the browser) */
private fun musicSections(): List<DoorSection> =
    listOf(
        DoorSection(DOOR_HOME, "For you", BenflixIcon.HOME),
        DoorSection(MUSIC_ALBUMS, "Albums", BenflixIcon.NOTE),
        DoorSection(MUSIC_ARTISTS, "Artists", BenflixIcon.PERSON),
        DoorSection(MUSIC_PLAYLISTS, "Playlists", BenflixIcon.LIST),
        DoorSection(MUSIC_SONGS, "Songs", BenflixIcon.HEADPHONES),
    )

/** A tab other than For you: Albums, Artists, Playlists (Your library) or Songs (every song, A to Z) */
@Composable
private fun MusicSectionPage(
    key: String,
    wide: Boolean,
    preferences: UserPreferences,
    modifier: Modifier,
) {
    when (key) {
        MUSIC_SONGS -> {
            MusicCollectionPage(
                kind = CollectionKind.SONGS,
                id = null,
                title = "All songs",
                preferences = preferences,
                modifier = modifier,
            )
        }

        else -> {
            MusicLibraryPlace(
                wide = wide,
                tab = key,
                modifier =
                    modifier.padding(
                        top = if (wide) LocalTopBarOverlap.current else 0.dp,
                        start = if (wide) side(true) else 0.dp,
                    ),
            )
        }
    }
}

/**
 * Glowfin new look (part 3, Ben approved 2026-11-22) on TV and wide screens: the cover of the card you're on stands
 * at the top right with its own colours glowing behind the page, and the words on the left say what it is (name,
 * artist, facts, a small tag), like Home and Movies. It waits half a second after you stop moving, so passing along
 * a row stays smooth on older TVs. Phones have no top: the rows start right under the tabs.
 */
@Composable
private fun MusicTopArea(
    lit: androidx.compose.runtime.State<MusicTile?>,
    first: MusicTile?,
    overlap: Dp,
) {
    var shown by remember { mutableStateOf<MusicTile?>(null) }
    LaunchedEffect(Unit) {
        snapshotFlow { lit.value }.collectLatest { tile ->
            if (tile == null) return@collectLatest
            if (shown != null) delay(TOP_AREA_WAIT_MS)
            shown = tile
        }
    }
    val tile = shown ?: first ?: return
    val glow = rememberCoverGlow(rememberTileGlowUrl(tile), BenflixColors.Music)
    val height = coverTopHeight(LocalConfiguration.current.screenHeightDp.dp, overlap)
    val words = remember(tile) { musicTopWords(tile) }
    CoverDoorTop(
        shown = tile,
        words = words,
        glow = glow,
        height = height,
        overlap = overlap,
        coverRatio = 1f,
        edge = side(true),
    ) { t, m -> TileCover(t, height, m) }
}

/** What the top says: a mix's name and who's in it, an album's name, artist, year, songs and length */
private fun musicTopWords(tile: MusicTile): CoverTopWords {
    if (tile is MusicTile.Item) {
        val item = tile.item
        val d = item.data
        return when (item.type) {
            BaseItemKind.MUSIC_ALBUM -> {
                CoverTopWords(
                    title = tileName(tile),
                    by = d.albumArtist ?: d.artists?.firstOrNull(),
                    facts =
                        listOfNotNull(
                            "Album",
                            d.productionYear?.toString(),
                            d.childCount?.let { songCount(it) },
                            d.runTimeTicks
                                ?.ticks
                                ?.inWholeMinutes
                                ?.takeIf { it > 0 }
                                ?.let { if (it >= 60) "${it / 60} h ${it % 60} min" else "$it min" },
                        ).joinToString(" · "),
                    tag = doorTag(item),
                )
            }

            else -> {
                CoverTopWords(
                    title = tileName(tile),
                    facts = listOfNotNull(topKicker(tile), musicCardLine(item)).joinToString(" · "),
                    tag = doorTag(item),
                )
            }
        }
    }
    return CoverTopWords(
        title = tileName(tile),
        facts = topLine(tile).orEmpty(),
        tag = topKicker(tile),
    )
}

/** A small copy of the tile's cover to take the glow colours from (a mix uses its first album) */
@Composable
private fun rememberTileGlowUrl(tile: MusicTile): String? {
    val imageUrlService = LocalImageUrlService.current
    return remember(tile) {
        when (tile) {
            is MusicTile.Item -> {
                imageUrlService.musicCover(tile.item, 64)
            }

            is MusicTile.Mix -> {
                tile.mix.coverAlbumIds
                    .firstOrNull()
                    ?.let { imageUrlService.albumCover(it, 64) }
            }

            is MusicTile.Browse -> {
                tile.coverAlbumIds.firstNotNullOfOrNull { it.toUUIDOrNull() }?.let { imageUrlService.albumCover(it, 64) }
            }

            else -> {
                null
            }
        }
    }
}

/** The small grey line over the name in the top area */
private fun topKicker(tile: MusicTile): String =
    when (tile) {
        is MusicTile.Mix -> {
            if (tile.mix.group.isNotEmpty()) "Playlist by theme · new every morning" else "Made for you · new every morning"
        }

        is MusicTile.Liked -> {
            "Your playlist"
        }

        is MusicTile.Item -> {
            when (tile.item.type) {
                BaseItemKind.MUSIC_ALBUM -> "Album"
                BaseItemKind.MUSIC_ARTIST -> "Artist"
                BaseItemKind.PLAYLIST -> "Your playlist"
                else -> "Music"
            }
        }

        MusicTile.Ask -> {
            "Requests"
        }

        is MusicTile.SeeAll -> {
            if (tile.kind == IMPORT_KIND) "Ben only" else "Music"
        }

        is MusicTile.Browse -> {
            "Browse"
        }

        MusicTile.AllSongs -> {
            "Every song on Glowfin"
        }
    }

/** The line under the name in the top area: who's in a mix, an album's artist and year, how many songs */
private fun topLine(tile: MusicTile): String? =
    when (tile) {
        is MusicTile.Mix -> tile.mix.line
        is MusicTile.Liked -> songCount(tile.count)
        is MusicTile.Item -> if (tile.item.type == BaseItemKind.MUSIC_ALBUM) albumLine(tile.item) else musicCardLine(tile.item)
        MusicTile.Ask -> "Songs, albums and playlists Glowfin doesn't have yet"
        is MusicTile.SeeAll -> if (tile.kind == IMPORT_KIND) "Paste a Spotify, Apple Music or YouTube playlist link" else null
        is MusicTile.Browse -> browseLine(tile.group)
        MusicTile.AllSongs -> "A to Z"
    }

private const val TOP_AREA_WAIT_MS = 500L

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
            if (tile.kind == IMPORT_KIND) {
                MusicCardFrame(
                    name = "Import",
                    line = "A playlist from a link",
                    size = size,
                    onClick = onClick,
                    onFocus = onFocus,
                    modifier = modifier,
                ) {
                    ActionCover("Import", BenflixIcon.DOWNLOAD, Modifier.fillMaxSize())
                }
            } else {
                MusicCardFrame(name = "See all", line = null, size = size, onClick = onClick, onFocus = onFocus, modifier = modifier) {
                    ActionCover(tile.title, BenflixIcon.ARROW, Modifier.fillMaxSize())
                }
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
