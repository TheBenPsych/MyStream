package com.github.damontecres.wholphin.ui.music

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import com.github.damontecres.wholphin.ui.door.BrowseTile
import com.github.damontecres.wholphin.ui.main.DoorWipe
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.LocalTopBarOverlap
import com.github.damontecres.wholphin.ui.theme.BenflixColors
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
        }
    }
    val browse =
        listOf(
            "Songs" to Destination.MusicList("songs", title = "All songs"),
            "Albums" to Destination.MusicList("albums", title = "Albums"),
            "Artists" to Destination.MusicList("artists", title = "Artists"),
            "Genres" to Destination.MusicList("genres", title = "Genres"),
            "Playlists" to Destination.MusicList("playlists", title = "Playlists"),
        )
    val overlap = LocalTopBarOverlap.current

    if (state.loaded && state.rows.isEmpty()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxSize().padding(start = side(wide), end = side(wide), top = overlap + 40.dp),
        ) {
            Text(
                text =
                    when {
                        state.noMusic -> "No music on Benflix yet"
                        else -> "Couldn't reach Benflix"
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

    val rows = if (easy) state.rows.filter { it.key == "mixes" || it.key == "playlists" } else state.rows
    val cardSize =
        if (easy) {
            168.dp
        } else if (wide) {
            136.dp
        } else {
            118.dp
        }
    var focused by remember { mutableStateOf<MusicTile?>(null) }
    var shown by remember { mutableStateOf<MusicTile?>(null) }
    LaunchedEffect(focused) {
        val t = focused ?: return@LaunchedEffect
        if (shown != null) delay(PREVIEW_DELAY_MS)
        shown = t
    }
    val firstFocus = remember { FocusRequester() }
    val firstKey = rows.firstOrNull()?.key
    // True while focus is somewhere in the rows (not the top bar)
    var rowsFocused by remember { mutableStateOf(false) }
    LaunchedEffect(firstKey) {
        // Ben: start on the first made-for-you playlist (people start a song or playlist over); coming Back
        // lands on the card you were on. The cards can take a moment to appear (and the top bar may grab focus
        // first), so keep asking for a little while until the card really has it.
        if (isTv && firstKey != null && !rowsFocused) {
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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val topHeight = if (wide) ((maxHeight - overlap) * 0.34f).coerceIn(150.dp, 280.dp) + overlap else 0.dp
        Column(Modifier.fillMaxSize()) {
            if (wide && !easy) {
                MusicTopArea(
                    tile = shown ?: rows.firstOrNull()?.tiles?.firstOrNull(),
                    height = topHeight,
                    overlap = overlap,
                )
            } else {
                Spacer(Modifier.height(if (wide) overlap + 16.dp else 8.dp))
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(if (wide) 26.dp else 20.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onFocusChanged { rowsFocused = it.hasFocus },
            ) {
                rows.forEachIndexed { rowIndex, row ->
                    item(key = row.key) {
                        Column {
                            MusicRowName(row.title, Modifier.padding(start = side(wide), bottom = 8.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = side(wide)),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                itemsIndexed(row.tiles, key = { _, t -> t.key }) { i, tile ->
                                    MusicTileCard(
                                        tile = tile,
                                        size = cardSize,
                                        onClick = { onTile(tile) },
                                        onFocus = {
                                            focused = tile
                                            viewModel.lastRow = rowIndex
                                            viewModel.lastIndex = i
                                        },
                                        modifier =
                                            if (rowIndex == viewModel.lastRow.coerceIn(0, rows.size - 1) &&
                                                i == viewModel.lastIndex.coerceIn(0, row.tiles.size - 1)
                                            ) {
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
                if (!easy) {
                    item(key = "browse") {
                        Column(Modifier.padding(horizontal = side(wide))) {
                            MusicRowName("Browse by", Modifier.padding(bottom = 8.dp))
                            browse.chunked(if (wide) 4 else 2).forEach { line ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                ) {
                                    line.forEach { (name, dest) ->
                                        BrowseTile(
                                            title = name,
                                            onClick = { viewModel.navigationManager.navigateTo(dest) },
                                            modifier = Modifier.weight(1f),
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
    }
}

/**
 * The top area (approved m2-q-top): the card's name, what's inside, how many songs and how long, with its
 * picture on the right fading into the dark.
 */
@Composable
private fun MusicTopArea(
    tile: MusicTile?,
    height: Dp,
    overlap: Dp,
) {
    val imageUrlService = LocalImageUrlService.current
    val density = LocalDensity.current
    val px = with(density) { height.roundToPx() }
    Box(Modifier.fillMaxWidth().heightIn(min = height)) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .height(height)
                .aspectRatio(1f),
        ) {
            val key = tile?.key
            Crossfade(targetState = key, animationSpec = tween(400), label = "musicTop") { k ->
                val t = if (k == tile?.key) tile else null
                when (t) {
                    is MusicTile.Item -> {
                        CoverImage(
                            imageUrlService.musicCover(t.item, px),
                            Modifier.fillMaxSize(),
                            shape = androidx.compose.ui.graphics.RectangleShape,
                        )
                    }

                    is MusicTile.Mix -> {
                        MixCover(
                            t.mix.name,
                            t.mix.coverAlbumIds.map { imageUrlService.albumCover(it, px / 2) },
                            Modifier.fillMaxSize(),
                            height,
                        )
                    }

                    is MusicTile.Liked -> {
                        LikedCover(Modifier.fillMaxSize())
                    }

                    else -> {
                        Unit
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to BenflixColors.Tray,
                            0.45f to BenflixColors.Tray.copy(alpha = .45f),
                            1f to Color.Transparent,
                        ),
                    ),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to BenflixColors.Tray)),
            )
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = BenflixSpace.tvEdge, end = BenflixSpace.tvEdge, top = overlap + 16.dp, bottom = 8.dp)
                    .widthIn(max = 620.dp),
        ) {
            val (kicker, name, line) = topWords(tile)
            if (kicker != null) MusicKicker(kicker)
            Text(
                text = name,
                color = BenflixColors.Text,
                fontSize = BenflixType.pageTitle,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (line.isNotBlank()) {
                Text(
                    text = line,
                    color = BenflixColors.Soft,
                    fontSize = BenflixType.body,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

private fun topWords(tile: MusicTile?): Triple<String?, String, String> =
    when (tile) {
        is MusicTile.Mix -> {
            Triple(
                "Made for you · new every morning",
                tile.mix.name,
                listOf(tile.mix.line, songCount(tile.mix.songIds.size))
                    .filter {
                        it.isNotBlank()
                    }.joinToString(" · "),
            )
        }

        is MusicTile.Liked -> {
            Triple("Your playlist", "Liked songs", songCount(tile.count))
        }

        is MusicTile.Item -> {
            val item = tile.item
            when (item.type) {
                BaseItemKind.MUSIC_ALBUM -> {
                    Triple("Album", item.title.orEmpty(), albumLine(item))
                }

                BaseItemKind.MUSIC_ARTIST -> {
                    Triple("Artist", item.title.orEmpty(), "")
                }

                BaseItemKind.PLAYLIST -> {
                    Triple(
                        "Playlist",
                        item.title.orEmpty(),
                        item.data.childCount
                            ?.let { songCount(it) }
                            .orEmpty(),
                    )
                }

                else -> {
                    Triple(null, item.title.orEmpty(), "")
                }
            }
        }

        MusicTile.Ask -> {
            Triple(null, "Ask for music", "Find a song, an album or a playlist and Tower gets it for you")
        }

        is MusicTile.SeeAll -> {
            Triple(null, tile.title, "Everything, as a grid")
        }

        null -> {
            Triple(null, "Music", "Your songs and playlists")
        }
    }

/** "Static Bloom · 2024 · 10 songs" */
fun albumLine(item: BaseItem): String =
    listOfNotNull(
        item.data.albumArtist ?: item.data.artists?.firstOrNull(),
        item.data.productionYear?.toString(),
        item.data.childCount?.let { songCount(it) },
    ).joinToString(" · ")

private const val PREVIEW_DELAY_MS = 350L
