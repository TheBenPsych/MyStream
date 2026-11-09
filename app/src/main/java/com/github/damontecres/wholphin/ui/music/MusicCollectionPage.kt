package com.github.damontecres.wholphin.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.NowPlayingStatus
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.detail.PlaylistDetails
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.LocalTopBarOverlap
import com.github.damontecres.wholphin.ui.preferences.StringInput
import com.github.damontecres.wholphin.ui.preferences.StringInputDialog
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemKind
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Benflix music: an album, playlist, made-for-you mix or Liked songs (approved al-hero + pl-same + pl-own).
 * Like the movie page: the big cover on the right fading into the dark, the words and buttons on the left
 * (Play, Shuffle, heart, More), and the songs start right under the buttons. Focus starts on Play.
 * Albums end with More by the artist and Similar albums. Easy read: big buttons and big rows (al-big).
 */
@Composable
fun MusicCollectionPage(
    kind: CollectionKind,
    id: String?,
    title: String,
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: MusicCollectionViewModel = hiltViewModel(key = "music-$kind-$id"),
    actions: MusicActionsViewModel = hiltViewModel(),
) {
    LaunchedEffect(kind, id) { viewModel.load(kind, id, title) }
    val state by viewModel.state.collectAsState()
    val music by actions.musicService.state.collectAsState()
    val easy = preferences.appPreferences.benflixPreferences.easyRead
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp || config.smallestScreenWidthDp >= 600
    val overlap = LocalTopBarOverlap.current
    val side = if (wide) BenflixSpace.tvEdge else BenflixSpace.phoneEdge

    if (state.notMusic) {
        val item = state.item
        if (item != null) {
            PlaylistDetails(preferences, Destination.MediaItem(item), modifier.padding(start = 40.dp, end = 16.dp, top = overlap))
        }
        return
    }
    if (state.loaded && state.failed) {
        Column(
            modifier = modifier.fillMaxSize().padding(start = side, top = overlap + 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (kind == CollectionKind.MIX) "This mix has changed" else "Couldn't load this",
                color = BenflixColors.Text,
                fontSize = BenflixType.pageTitle,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text =
                    if (kind ==
                        CollectionKind.MIX
                    ) {
                        "Made for you mixes are new every morning. Go back to Music for today's."
                    } else {
                        "Check the TV is online, then try again."
                    },
                color = BenflixColors.Soft,
                fontSize = BenflixType.body,
            )
            val retry = remember { FocusRequester() }
            com.github.damontecres.wholphin.ui.nav
                .RegisterTopBarDown(retry)
            MusicButton(
                "Try again",
                BenflixIcon.REFRESH,
                onClick = viewModel::reload,
                primary = true,
                modifier = Modifier.focusRequester(retry),
            )
            LaunchedEffect(Unit) {
                delay(80)
                if (isTv) retry.tryRequestFocus()
            }
        }
        return
    }

    val songs = state.songs
    val withCover = kind != CollectionKind.ALBUM
    val discs = remember(songs) { songs.mapNotNull { it.data.parentIndexNumber }.distinct().size > 1 }
    val playFocus = remember { FocusRequester() }
    // Down from the top bar comes back to Play (Ben got stuck in the top bar on a Daily mix)
    com.github.damontecres.wholphin.ui.nav
        .RegisterTopBarDown(playFocus)
    LaunchedEffect(state.loaded) {
        if (state.loaded && isTv) {
            delay(80)
            playFocus.tryRequestFocus()
        }
    }
    var menuFor by remember { mutableStateOf<Int?>(null) }
    var pageMenu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val currentId = music.currentAudio?.id
    val playing = music.status == NowPlayingStatus.PLAYING
    val sourceName = state.title
    val play: (Int, Boolean) -> Unit = { index, shuffle ->
        actions.playList(songs, index, shuffle, state.item, sourceName)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val heroHeight = if (wide) ((maxHeight - overlap) * 0.46f).coerceIn(200.dp, 340.dp) + overlap else 0.dp
        LazyColumn(
            contentPadding = PaddingValues(bottom = 48.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "hero") {
                CollectionHero(
                    state = state,
                    wide = wide,
                    easy = easy,
                    height = heroHeight,
                    overlap = overlap,
                    side = side,
                    playFocus = playFocus,
                    // Ben: Play or Shuffle on any album, playlist or mix goes straight to Now playing
                    onPlay = { actions.playList(songs, 0, false, state.item, sourceName, openNow = true) },
                    onShuffle = { actions.playList(songs, 0, true, state.item, sourceName, openNow = true) },
                    onHeart = viewModel::toggleAlbumFavourite,
                    onMore = { pageMenu = true },
                    editing = editing,
                )
            }
            if (songs.isEmpty() && state.loaded) {
                item(key = "empty") {
                    Text(
                        text =
                            when (kind) {
                                CollectionKind.LIKED -> "Press the heart on a song and it lands here."
                                CollectionKind.PLAYLIST -> "No songs in this playlist yet. Use a song's More menu to add some."
                                else -> "No songs here yet."
                            },
                        color = BenflixColors.Soft,
                        fontSize = BenflixType.body,
                        modifier = Modifier.padding(start = side, top = 16.dp),
                    )
                }
            }
            var lastDisc: Int? = null
            songs.forEachIndexed { index, song ->
                val disc = song.data.parentIndexNumber
                if (discs && disc != lastDisc) {
                    lastDisc = disc
                    item(key = "disc-$disc-$index") {
                        Text(
                            text = "Disc ${disc ?: 1}",
                            color = BenflixColors.Soft,
                            fontSize = BenflixType.small,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(start = side + 10.dp, top = 14.dp, bottom = 4.dp),
                        )
                    }
                }
                // A playlist entry keeps its key when it moves, so focus stays on it while editing
                item(key = "song-" + (song.data.playlistItemId ?: "$index-${song.id}")) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier =
                            Modifier
                                .padding(horizontal = side - 10.dp)
                                .widthIn(max = if (wide) 980.dp else 2000.dp),
                    ) {
                        SongRow(
                            song = song,
                            number = if (withCover) index + 1 else (song.data.indexNumber ?: (index + 1)),
                            withCover = withCover,
                            isCurrent = song.id == currentId,
                            isPlaying = playing,
                            liked = song.id in state.liked,
                            big = easy,
                            onClick = { if (!editing) play(index, false) },
                            onMore = { if (!editing) menuFor = index },
                            modifier = Modifier.weight(1f),
                        )
                        if (editing) {
                            MusicButton(null, BenflixIcon.DOWN, onClick = { viewModel.move(index, 1) })
                            MusicButton(null, BenflixIcon.DOWN, onClick = { viewModel.move(index, -1) }, modifier = Modifier.rotate(180f))
                            MusicButton(null, BenflixIcon.CLOSE, onClick = { viewModel.removeFromPlaylist(index) })
                        } else if (!isTv) {
                            MusicButton(null, BenflixIcon.MORE_VERT, onClick = { menuFor = index })
                        }
                    }
                }
            }
            if (state.moreByArtist.isNotEmpty()) {
                item(key = "more") {
                    AlbumRow(
                        title = "More by ${state.item?.data?.albumArtist ?: "this artist"}",
                        albums = state.moreByArtist,
                        side = side,
                        size = if (wide) 132.dp else 116.dp,
                        onClick = { actions.navigationManager.navigateTo(it.destination()) },
                    )
                }
            }
            if (state.similar.isNotEmpty()) {
                item(key = "similar") {
                    AlbumRow(
                        title = "Similar albums on Benflix",
                        albums = state.similar,
                        side = side,
                        size = if (wide) 132.dp else 116.dp,
                        onClick = { actions.navigationManager.navigateTo(it.destination()) },
                    )
                }
            }
        }
    }

    menuFor?.let { index ->
        songs.getOrNull(index)?.let { song ->
            SongMenu(
                song = song,
                liked = song.id in state.liked,
                onDismiss = { menuFor = null },
                onLikedChanged = { viewModel.likedChanged(song.id, it) },
                onRemoveFromPlaylist = if (state.canEdit) ({ viewModel.removeFromPlaylist(index) }) else null,
                actions = actions,
            )
        }
    }
    if (pageMenu) {
        val item = state.item
        val entries =
            buildList {
                if (state.canEdit) {
                    add(
                        DialogItem(headlineContent = { Text(if (editing) "Done editing" else "Edit (move or remove songs)") }, onClick = {
                            editing = !editing
                            pageMenu = false
                        }),
                    )
                    add(
                        DialogItem(headlineContent = { Text("Rename") }, onClick = {
                            renaming = true
                            pageMenu = false
                        }),
                    )
                }
                if (item?.type == BaseItemKind.MUSIC_ALBUM) {
                    item.data.albumArtists?.firstOrNull()?.let { artist ->
                        add(
                            DialogItem(headlineContent = { Text("Go to ${artist.name ?: "artist"}") }, onClick = {
                                pageMenu = false
                                actions.navigationManager.navigateTo(Destination.MediaItem(artist.id, BaseItemKind.MUSIC_ARTIST))
                            }),
                        )
                    }
                }
                if (songs.isNotEmpty() && !isTv) {
                    add(
                        DialogItem(headlineContent = { Text("Download all to this phone") }, onClick = {
                            pageMenu = false
                            actions.download(songs)
                        }),
                    )
                }
                if (songs.isNotEmpty()) {
                    add(
                        DialogItem(headlineContent = { Text("Add all to Up next") }, onClick = {
                            pageMenu = false
                            actions.addAllToUpNext(songs)
                        }),
                    )
                }
            }
        if (entries.isEmpty()) {
            pageMenu = false
        } else {
            DialogPopup(
                showDialog = true,
                title = state.title,
                dialogItems = entries,
                onDismissRequest = { pageMenu = false },
                dismissOnClick = false,
            )
        }
    }
    if (renaming) {
        StringInputDialog(
            input =
                StringInput(title = "Rename playlist", value = state.title, keyboardOptions = KeyboardOptions.Default, onSubmit = {
                    viewModel.rename(it)
                }),
            onSave = {
                viewModel.rename(it)
                renaming = false
            },
            onDismissRequest = { renaming = false },
        )
    }
}

@Composable
private fun CollectionHero(
    state: MusicCollectionState,
    wide: Boolean,
    easy: Boolean,
    height: Dp,
    overlap: Dp,
    side: Dp,
    playFocus: FocusRequester,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onHeart: () -> Unit,
    onMore: () -> Unit,
    editing: Boolean,
) {
    val imageUrlService = LocalImageUrlService.current
    val density = LocalDensity.current
    val coverPx = with(density) { (if (wide) height else 280.dp).roundToPx() }
    val item = state.item
    val kicker =
        when (state.kind) {
            CollectionKind.ALBUM -> if (item?.data?.childCount == 1 || state.songs.size == 1) "Single" else "Album"
            CollectionKind.PLAYLIST -> "Playlist"
            CollectionKind.MIX -> "Made for you · new every morning"
            CollectionKind.LIKED -> "Your playlist"
            CollectionKind.SONGS -> "Every song on Benflix"
        }
    val line =
        listOfNotNull(
            when (state.kind) {
                CollectionKind.ALBUM -> item?.data?.albumArtist ?: item?.data?.artists?.firstOrNull()
                CollectionKind.MIX -> state.mixLine.takeIf { it.isNotBlank() }
                else -> null
            },
            if (state.kind == CollectionKind.ALBUM) item?.data?.productionYear?.toString() else null,
            if (state.songs.isNotEmpty()) songCount(state.songs.size) else null,
            if (state.songs.isNotEmpty()) totalLength(state.songs) else null,
        ).joinToString(" · ")
    val quiet =
        if (state.kind == CollectionKind.ALBUM && item != null) {
            listOfNotNull(
                item.data.genres?.firstOrNull(),
                item.data.dateCreated?.let { "Added ${ago(it)}" },
                item.data.userData
                    ?.playCount
                    ?.takeIf { it > 0 }
                    ?.let { if (it == 1) "Played once" else "Played $it times" },
            ).joinToString(" · ")
        } else {
            ""
        }
    val cover: @Composable (Modifier) -> Unit = { m ->
        when {
            state.kind == CollectionKind.LIKED -> {
                LikedCover(m)
            }

            state.kind == CollectionKind.ALBUM && item != null -> {
                CoverImage(
                    remember(item.id) {
                        imageUrlService.musicCover(item, coverPx)
                    },
                    m,
                    shape = if (wide) RectangleShape else com.github.damontecres.wholphin.ui.theme.BenflixShapes.Card,
                )
            }

            item?.type == BaseItemKind.PLAYLIST && state.collage.size < 4 -> {
                CoverImage(remember(item.id) { imageUrlService.musicCover(item, coverPx) }, m)
            }

            else -> {
                MixCover(
                    name = state.title,
                    coverUrls = remember(state.collage) { state.collage.map { imageUrlService.albumCover(it, coverPx / 2) } },
                    size = if (wide) height else 280.dp,
                    modifier = m,
                )
            }
        }
    }
    val buttons: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            MusicButton(
                label = "Play",
                icon = null,
                filledPlay = true,
                primary = true,
                big = easy,
                onClick = onPlay,
                modifier = Modifier.focusRequester(playFocus),
            )
            MusicButton(if (easy) "Shuffle" else null, BenflixIcon.SHUFFLE, onClick = onShuffle, big = easy)
            if (state.kind == CollectionKind.ALBUM && item != null) {
                MusicButton(
                    label = if (easy) (if (item.favorite) "Liked" else "Like") else null,
                    icon = BenflixIcon.HEART,
                    filledHeart = item.favorite,
                    onClick = onHeart,
                    big = easy,
                )
            }
            if (state.canEdit || state.kind == CollectionKind.ALBUM || state.songs.isNotEmpty()) {
                MusicButton(
                    if (editing) {
                        "Done"
                    } else if (easy) {
                        "More"
                    } else {
                        null
                    },
                    if (editing) BenflixIcon.CHECK else BenflixIcon.MORE,
                    onClick = onMore,
                    big = easy,
                )
            }
        }
    }
    if (wide) {
        // The words never go up under the top bar: the area grows taller when they need more room (Ben's photo)
        Box(Modifier.fillMaxWidth().heightIn(min = height)) {
            Box(Modifier.align(Alignment.TopEnd).height(height).aspectRatio(1f)) {
                cover(Modifier.fillMaxSize())
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to BenflixColors.Tray,
                                0.5f to BenflixColors.Tray.copy(alpha = .35f),
                                1f to Color.Transparent,
                            ),
                        ),
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to BenflixColors.Tray)))
            }
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = side, end = side, top = overlap + 20.dp, bottom = 12.dp)
                        .widthIn(max = 640.dp),
            ) {
                MusicKicker(
                    kicker,
                    color =
                        if (state.kind == CollectionKind.MIX ||
                            state.kind == CollectionKind.LIKED ||
                            state.kind == CollectionKind.SONGS
                        ) {
                            BenflixColors.Music
                        } else {
                            BenflixColors.Soft
                        },
                )
                Text(
                    text = state.title,
                    color = BenflixColors.Text,
                    fontSize = if (easy) 44.sp else BenflixType.hero,
                    lineHeight = if (easy) 48.sp else 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (line.isNotBlank()) {
                    Text(
                        line,
                        color = BenflixColors.Soft,
                        fontSize = BenflixType.lead,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (quiet.isNotBlank() && !easy) {
                    Text(
                        quiet,
                        color = BenflixColors.Muted,
                        fontSize = BenflixType.small,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Box(Modifier.padding(top = 16.dp)) { buttons() }
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = side, vertical = 8.dp),
        ) {
            cover(Modifier.size(260.dp))
            MusicKicker(
                kicker,
                Modifier.padding(top = 14.dp),
                color =
                    if (state.kind ==
                        CollectionKind.MIX
                    ) {
                        BenflixColors.Music
                    } else {
                        BenflixColors.Soft
                    },
            )
            Text(
                text = state.title,
                color = BenflixColors.Text,
                fontSize = BenflixType.hero,
                lineHeight = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (line.isNotBlank()) {
                Text(
                    line,
                    color = BenflixColors.Soft,
                    fontSize = BenflixType.small,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Box(Modifier.padding(top = 14.dp, bottom = 8.dp)) { buttons() }
        }
    }
}

@Composable
fun AlbumRow(
    title: String,
    albums: List<BaseItem>,
    side: Dp,
    size: Dp,
    onClick: (BaseItem) -> Unit,
) {
    Column(Modifier.padding(top = 28.dp)) {
        MusicRowName(title, Modifier.padding(start = side, bottom = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = side), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(albums, key = { it.id.toString() }) { album ->
                MusicCard(item = album, size = size, onClick = { onClick(album) })
            }
        }
    }
}

/** "3 days ago" */
fun ago(time: LocalDateTime): String {
    val days = ChronoUnit.DAYS.between(time, LocalDateTime.now())
    return when {
        days <= 0 -> "today"
        days == 1L -> "yesterday"
        days < 14 -> "$days days ago"
        days < 60 -> "${days / 7} weeks ago"
        days < 730 -> "${days / 30} months ago"
        else -> "${days / 365} years ago"
    }
}
