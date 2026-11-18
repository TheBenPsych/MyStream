package com.github.damontecres.wholphin.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.BenflixMusic
import com.github.damontecres.wholphin.services.DownloadService
import com.github.damontecres.wholphin.services.DownloadedItem
import com.github.damontecres.wholphin.services.MusicLibrary
import com.github.damontecres.wholphin.services.MusicMe
import com.github.damontecres.wholphin.services.MusicPlace
import com.github.damontecres.wholphin.services.MusicPlaylist
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PlaylistCreator
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.components.BenflixNotices
import com.github.damontecres.wholphin.ui.components.EditTextBox
import com.github.damontecres.wholphin.ui.door.DoorChip
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.focusRing
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixDoor
import com.github.damontecres.wholphin.ui.theme.BenflixSpace
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** One thing in Your library: a playlist, mix, album, artist, Liked songs or All songs */
data class LibraryEntry(
    val tile: MusicTile,
    /** The same thing as Tower knows it (Recently played and pins) */
    val place: MusicPlace,
    /** "Playlist · 412 songs", "Made for you", "Album · The Weeknd", "Artist" */
    val line: String,
    /** When it came into Benflix (milliseconds), for Recently added */
    val addedAt: Long = 0L,
    /** 0 = made for you by Tower, 1 = yours, 2 = albums and artists (Made by) */
    val madeBy: Int = 2,
)

data class MusicLibraryState(
    val all: List<LibraryEntry> = emptyList(),
    val albums: List<LibraryEntry>? = null,
    val artists: List<LibraryEntry>? = null,
    val beingFound: List<MusicPlaylist> = emptyList(),
    val loaded: Boolean = false,
)

/**
 * Music redesign 3 (approved g2-library, x-recent, x-offline): Your library. Liked songs is always pinned
 * first, then up to 4 pins of your own, then everything else sorted by Recently played (or Recently added,
 * A to Z, Made by). Chips: Playlists, Albums, Artists, Recently played, Being found, and Downloaded on phones.
 */
@HiltViewModel
class MusicLibraryViewModel
    @Inject
    constructor(
        private val library: MusicLibrary,
        val musicMe: MusicMe,
        private val benflixMusic: BenflixMusic,
        val downloads: DownloadService,
        private val musicService: MusicService,
        private val playlistCreator: PlaylistCreator,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow(MusicLibraryState())
        val state: StateFlow<MusicLibraryState> = _state

        fun load() {
            viewModelScope.launchIO {
                try {
                    coroutineScope {
                        val playlists = async { safe { library.playlists() }.orEmpty() }
                        val mixes = async { safe { library.mixes() }.orEmpty() }
                        val liked = async { safe { library.likedCount() } ?: 0 }
                        val albums = async { safe { library.likedOf(BaseItemKind.MUSIC_ALBUM) }.orEmpty() }
                        val artists = async { safe { library.likedOf(BaseItemKind.MUSIC_ARTIST) }.orEmpty() }
                        val found = async { safe { benflixMusic.status().playlists.filter { !it.done } }.orEmpty() }
                        safe { musicMe.load() }
                        val me = musicMe.state.value
                        val known = (playlists.await() + albums.await() + artists.await()).map { it.id.toString() }.toSet()
                        // Albums, artists and playlists you played or pinned that aren't liked or yours
                        val extraIds =
                            (me.pins + me.recents)
                                .filter { it.kind == "album" || it.kind == "artist" || it.kind == "playlist" }
                                .map { it.id }
                                .filter { it !in known }
                                .distinct()
                                .take(EXTRA)
                                .mapNotNull { it.toUUIDOrNull() }
                        val extras = if (extraIds.isEmpty()) emptyList() else safe { library.byIds(extraIds) }.orEmpty()
                        val entries =
                            buildList {
                                add(likedEntry(liked.await()))
                                mixes.await().forEach { m ->
                                    add(LibraryEntry(MusicTile.Mix(m), MusicPlace("mix", m.key, m.name), "Made for you", madeBy = 0))
                                }
                                playlists.await().forEach { add(entryOf(it, madeBy = 1)) }
                                (albums.await() + artists.await() + extras).forEach { add(entryOf(it)) }
                                add(
                                    LibraryEntry(
                                        MusicTile.AllSongs,
                                        MusicPlace("songs", "", "All songs"),
                                        "Every song, A to Z",
                                        madeBy = 1,
                                    ),
                                )
                            }.distinctBy { it.place.key }
                        _state.update { it.copy(all = entries, beingFound = found.await(), loaded = true) }
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "Couldn't load Your library")
                    _state.update { it.copy(loaded = true) }
                }
            }
        }

        /** The Albums and Artists chips show every one on Benflix, loaded the first time you pick them */
        fun loadAll(chip: String) {
            if (chip == "albums" && _state.value.albums == null) {
                viewModelScope.launchIO {
                    val list = safe { library.albums(ItemSortBy.SORT_NAME, limit = MAX) }.orEmpty()
                    _state.update { it.copy(albums = list.map { a -> entryOf(a) }) }
                }
            }
            if (chip == "artists" && _state.value.artists == null) {
                viewModelScope.launchIO {
                    val list = safe { library.artists(limit = MAX) }.orEmpty()
                    _state.update { it.copy(artists = list.map { a -> entryOf(a) }) }
                }
            }
        }

        fun pin(entry: LibraryEntry) {
            if (entry.place.kind == "liked") {
                BenflixNotices.pill("Liked songs is always pinned first")
                return
            }
            val on = !musicMe.isPinned(entry.place)
            viewModelScope.launchIO {
                val problem = musicMe.pin(entry.place, on)
                BenflixNotices.pill(problem ?: if (on) "Pinned ${entry.place.name}" else "Unpinned ${entry.place.name}")
            }
        }

        fun newPlaylist(name: String) {
            if (name.isBlank()) return
            viewModelScope.launchIO {
                val id = safe { playlistCreator.createServerPlaylist(name.trim(), emptyList()) }
                if (id == null) {
                    BenflixNotices.pill("Couldn't make that playlist. Try again in a minute.")
                } else {
                    BenflixNotices.pill("Made ${name.trim()}")
                    load()
                }
            }
        }

        fun playDownloaded(
            songs: List<DownloadedItem>,
            index: Int,
        ) = viewModelScope.launchIO { musicService.playDownloaded(songs, index) }

        private fun likedEntry(count: Int) =
            LibraryEntry(MusicTile.Liked(count), MusicPlace("liked", "", "Liked songs"), "Playlist · ${songCount(count)}", madeBy = 1)

        private fun entryOf(
            item: BaseItem,
            madeBy: Int = 2,
        ): LibraryEntry {
            val line =
                when (item.type) {
                    BaseItemKind.PLAYLIST -> {
                        listOfNotNull("Playlist", item.data.childCount?.let { songCount(it) }).joinToString(" · ")
                    }

                    BaseItemKind.MUSIC_ALBUM -> {
                        listOfNotNull(
                            "Album",
                            item.data.albumArtist ?: item.data.artists?.firstOrNull(),
                        ).joinToString(" · ")
                    }

                    BaseItemKind.MUSIC_ARTIST -> {
                        "Artist"
                    }

                    else -> {
                        ""
                    }
                }
            val added =
                item.data.dateCreated
                    ?.atZone(ZoneId.systemDefault())
                    ?.toInstant()
                    ?.toEpochMilli() ?: 0L
            val place = musicPlaceOf(item) ?: MusicPlace("album", item.id.toString(), item.title.orEmpty())
            return LibraryEntry(MusicTile.Item(item), place, line, added, madeBy)
        }

        private suspend fun <T> safe(block: suspend () -> T): T? =
            try {
                block()
            } catch (ex: Exception) {
                Timber.w(ex, "Your library: something didn't load")
                null
            }

        companion object {
            private const val MAX = 3000
            private const val EXTRA = 60
        }
    }

private val LIBRARY_SORTS = listOf("Recently played", "Recently added", "A to Z", "Made by")

/**
 * Your library. Glowfin (M1): it's what the Albums, Artists and Playlists tabs show ([tab] is "albums",
 * "artists" or "playlists"); Playlists keeps New playlist, Import and the Recently played, Being found and
 * Downloaded chips. "all" is the old everything-in-one list.
 */
@Composable
fun MusicLibraryPlace(
    wide: Boolean,
    modifier: Modifier = Modifier,
    tab: String = "all",
    viewModel: MusicLibraryViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsState()
    val me by viewModel.musicMe.state.collectAsState()
    val isTv = rememberIsTv()
    var chip by rememberSaveable(tab) { mutableStateOf(tab) }
    val playlistsTab = tab == "all" || tab == "playlists"
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    LaunchedEffect(chip) { viewModel.loadAll(chip) }
    val side = if (wide) 0.dp else BenflixSpace.phoneEdge
    val first = remember { FocusRequester() }
    LaunchedEffect(state.loaded) {
        if (state.loaded && isTv) {
            delay(80)
            first.tryRequestFocus()
        }
    }
    val open: (LibraryEntry) -> Unit = { e -> openTile(e.tile, viewModel.navigationManager) }
    val downloaded =
        if (isTv) {
            emptyList()
        } else {
            val all by viewModel.downloads.items.collectAsState()
            remember(all) { viewModel.downloads.visibleItems(all).filter { it.music } }
        }

    if (naming) {
        NewPlaylistDialog(
            onDismiss = { naming = false },
            onCreate = {
                naming = false
                viewModel.newPlaylist(it)
            },
        )
    }

    Column(modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(start = side, end = 8.dp, top = 8.dp),
        ) {
            if (tab == "all") {
                Text(
                    "Your library",
                    color = BenflixColors.Text,
                    fontFamily = com.github.damontecres.wholphin.ui.theme.GlowfinDisplayFont,
                    fontSize = BenflixType.pageTitle,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            } else {
                // (the tab above already names the page)
                Box(Modifier.weight(1f))
            }
            if (playlistsTab) {
                MusicButton(if (wide) "New playlist" else null, BenflixIcon.PLUS, onClick = { naming = true })
                MusicButton(
                    if (wide) "Import" else null,
                    BenflixIcon.DOWNLOAD,
                    onClick = { MusicPlaceNav.openAsk(import = true) },
                )
            }
            MusicButton(null, if (grid) BenflixIcon.LIST else BenflixIcon.IMAGE, onClick = { grid = !grid })
        }
        val chips =
            buildList {
                if (tab == "all") {
                    add("playlists" to "Playlists")
                    add("albums" to "Albums")
                    add("artists" to "Artists")
                }
                if (playlistsTab) {
                    add("recent" to "Recently played")
                    if (state.beingFound.isNotEmpty()) add("found" to "Being found")
                    if (!isTv) add("downloaded" to "Downloaded")
                }
            }
        if (chips.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = side, end = 8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) {
                items(chips, key = { it.first }) { (key, label) ->
                    DoorChip(
                        label = label,
                        door = BenflixDoor.MUSIC,
                        selected = chip == key,
                        // Picking the chip you're on goes back to the whole tab
                        onClick = { chip = if (chip == key) tab else key },
                    )
                }
            }
        }
        if (chip != "recent" && chip != "found" && chip != "downloaded") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = side, top = 6.dp),
            ) {
                LibraryTextButton("Sort: ${LIBRARY_SORTS[sort]}", onClick = { sort = (sort + 1) % LIBRARY_SORTS.size })
                Text(
                    if (isTv) "Hold OK to pin" else "Hold to pin",
                    color = BenflixColors.Muted,
                    fontSize = BenflixType.tag,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
        val body = Modifier.fillMaxWidth().weight(1f)
        when (chip) {
            "recent" -> {
                RecentlyPlayedList(
                    recents = me.recents,
                    entries = state.all,
                    side = side,
                    onOpen = open,
                    first = first,
                    modifier = body,
                )
            }

            "found" -> {
                LazyColumn(contentPadding = PaddingValues(start = side, end = 8.dp, top = 8.dp, bottom = 48.dp), modifier = body) {
                    itemsIndexed(state.beingFound, key = { _, p -> p.id }) { i, p ->
                        LibraryRow(
                            title = p.name,
                            line = p.words,
                            cover = { ActionCover(p.name, BenflixIcon.DOWNLOAD, Modifier.fillMaxSize()) },
                            pinned = false,
                            onClick = { MusicPlaceNav.openAsk() },
                            onLongClick = null,
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
            }

            "downloaded" -> {
                if (downloaded.isEmpty()) {
                    MusicEmptyLine(
                        "Nothing downloaded yet. A song's More menu has Download, and so do albums and playlists.",
                        Modifier.padding(horizontal = side),
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(start = side, end = 8.dp, top = 8.dp, bottom = 48.dp), modifier = body) {
                        itemsIndexed(downloaded, key = { _, d -> d.itemId.toString() }) { i, d ->
                            LibraryRow(
                                title = d.title,
                                line = d.subtitle.orEmpty(),
                                cover = { ActionCover(d.title, BenflixIcon.DOWNLOAD, Modifier.fillMaxSize()) },
                                pinned = false,
                                onClick = { viewModel.playDownloaded(downloaded, i) },
                                onLongClick = null,
                            )
                        }
                    }
                }
            }

            else -> {
                val source =
                    when (chip) {
                        "albums" -> {
                            state.albums
                        }

                        "artists" -> {
                            state.artists
                        }

                        "playlists" -> {
                            state.all.filter {
                                it.place.kind == "playlist" || it.place.kind == "mix" || it.place.kind == "liked"
                            }
                        }

                        else -> {
                            state.all
                        }
                    }
                if (source == null || (!state.loaded && source.isEmpty())) {
                    MusicEmptyLine("Getting your music…", Modifier.padding(horizontal = side))
                    return@Column
                }
                val list =
                    remember(source, sort, me.pins, me.recents) {
                        libraryOrder(source, LIBRARY_SORTS[sort], me.pins, me.recents)
                    }
                val pinned = remember(me.pins) { me.pins.map { it.key }.toSet() }
                if (grid) {
                    val size = if (wide) 132.dp else 104.dp
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(size + 14.dp),
                        contentPadding = PaddingValues(start = side, end = 8.dp, top = 8.dp, bottom = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = body,
                    ) {
                        items(list, key = { it.place.key }) { e ->
                            MusicTileCard(
                                tile = e.tile,
                                size = size,
                                onClick = { open(e) },
                                modifier = if (e === list.firstOrNull()) Modifier.focusRequester(first) else Modifier,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(start = side, end = 8.dp, top = 6.dp, bottom = 48.dp),
                        modifier = body,
                    ) {
                        itemsIndexed(list, key = { _, e -> e.place.key }) { i, e ->
                            LibraryRow(
                                title = tileName(e.tile),
                                line = e.line,
                                cover = { TileCover(e.tile, 56.dp, Modifier.fillMaxSize()) },
                                pinned = e.place.kind == "liked" || e.place.key in pinned,
                                onClick = { open(e) },
                                onLongClick = { viewModel.pin(e) },
                                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Liked songs first, then your pins in the order you pinned them, then the rest in the sort you picked */
private fun libraryOrder(
    source: List<LibraryEntry>,
    sort: String,
    pins: List<MusicPlace>,
    recents: List<MusicPlace>,
): List<LibraryEntry> {
    val liked = source.filter { it.place.kind == "liked" }
    val byKey = source.associateBy { it.place.key }
    val pinned = pins.mapNotNull { byKey[it.key] }.filter { it.place.kind != "liked" }
    val pinnedKeys = pinned.map { it.place.key }.toSet()
    val rest = source.filter { it.place.kind != "liked" && it.place.key !in pinnedKeys }
    val played = recents.withIndex().associate { (i, r) -> r.key to i }
    val sorted =
        when (sort) {
            "Recently added" -> rest.sortedByDescending { it.addedAt }

            "A to Z" -> rest.sortedBy { tileName(it.tile).lowercase() }

            "Made by" -> rest.sortedWith(compareBy<LibraryEntry> { it.madeBy }.thenBy { tileName(it.tile).lowercase() })

            // Recently played: what you played lately first (newest first), then the rest as they came
            else -> rest.sortedBy { played[it.place.key] ?: Int.MAX_VALUE }
        }
    // (one row per album or playlist: a doubled one would close the page on some TVs)
    return (liked + pinned + sorted).distinctBy { it.place.key }
}

/** Recently played (approved x-recent): every album, playlist and mix you played in the last 3 months, by day */
@Composable
private fun RecentlyPlayedList(
    recents: List<MusicPlace>,
    entries: List<LibraryEntry>,
    side: androidx.compose.ui.unit.Dp,
    onOpen: (LibraryEntry) -> Unit,
    first: FocusRequester,
    modifier: Modifier = Modifier,
) {
    if (recents.isEmpty()) {
        MusicEmptyLine("Nothing played yet. Everything you play shows up here for 3 months.", Modifier.padding(horizontal = side))
        return
    }
    val byKey = remember(entries) { entries.associateBy { it.place.key } }
    val days =
        remember(recents, byKey) {
            val today = LocalDate.now()
            recents
                .groupBy {
                    java.time.Instant
                        .ofEpochMilli((it.at * 1000).toLong())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                }.map { (day, list) ->
                    val name =
                        when (day) {
                            today -> "Today"
                            today.minusDays(1) -> "Yesterday"
                            else -> day.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
                        }
                    name to list
                }
        }
    val firstKey = days.firstOrNull()?.let { (day, list) -> "$day-${list.firstOrNull()?.key}" }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(start = side, end = 8.dp, top = 8.dp, bottom = 48.dp),
        modifier = modifier,
    ) {
        days.forEach { (day, list) ->
            item(key = "day-$day") {
                Text(
                    day,
                    color = BenflixColors.Text,
                    fontSize = BenflixType.rowName,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            items(list, key = { "$day-${it.key}" }) { r ->
                val e = byKey[r.key]
                val mod = if ("$day-${r.key}" == firstKey) Modifier.focusRequester(first) else Modifier
                LibraryRow(
                    title = e?.let { tileName(it.tile) } ?: r.name,
                    line = e?.line ?: placeLine(r.kind),
                    cover = {
                        if (e != null) {
                            TileCover(e.tile, 56.dp, Modifier.fillMaxSize())
                        } else {
                            ActionCover(r.name, BenflixIcon.HEADPHONES, Modifier.fillMaxSize())
                        }
                    },
                    pinned = false,
                    onClick = { if (e != null) onOpen(e) else MusicPlaceNav.openAsk() },
                    onLongClick = null,
                    modifier = mod,
                )
            }
        }
    }
}

private fun placeLine(kind: String): String =
    when (kind) {
        "album" -> "Album"
        "artist" -> "Artist"
        "playlist" -> "Playlist"
        "mix" -> "Made for you"
        else -> ""
    }

/** One line in Your library: a small cover, the name and what it is, and a violet dot when it's pinned */
@Composable
private fun LibraryRow(
    title: String,
    line: String,
    cover: @Composable () -> Unit,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border = focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(6.dp),
        ) {
            Box(Modifier.size(56.dp)) { cover() }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = BenflixColors.Text,
                    fontSize = BenflixType.body,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pinned) {
                        Box(
                            Modifier
                                .padding(end = 6.dp)
                                .size(8.dp)
                                .background(BenflixColors.Music, RoundedCornerShape(50)),
                        )
                    }
                    Text(
                        line,
                        color = BenflixColors.Muted,
                        fontSize = BenflixType.small,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryTextButton(
    label: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = BenflixColors.Soft,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border = focusRing(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            BenflixIconImage(BenflixIcon.MENU, color = BenflixColors.Soft, size = 16.dp, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = BenflixType.small, fontWeight = FontWeight.Bold)
        }
    }
}

/** New playlist: just its name */
@Composable
private fun NewPlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    com.github.damontecres.wholphin.ui.touch.Dialog(onDismissRequest = onDismiss) {
        var name by rememberSaveable { mutableStateOf("") }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.tryRequestFocus() }
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .background(BenflixColors.PanelHigh, RoundedCornerShape(16.dp))
                    .padding(20.dp)
                    .width(360.dp),
        ) {
            Text("New playlist", color = BenflixColors.Text, fontSize = BenflixType.rowName, fontWeight = FontWeight.ExtraBold)
            EditTextBox(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MusicButton("Make it", BenflixIcon.CHECK, primary = true, onClick = { onCreate(name) })
                MusicButton("Cancel", null, onClick = onDismiss)
            }
        }
    }
}

/** Opens a tile's page (the same as on Music Home) */
fun openTile(
    tile: MusicTile,
    nav: NavigationManager,
) {
    when (tile) {
        is MusicTile.Mix -> nav.navigateTo(Destination.MusicList("mix", tile.mix.key, tile.mix.name))
        is MusicTile.Item -> nav.navigateTo(tile.item.destination())
        is MusicTile.Liked -> nav.navigateTo(Destination.MusicList("liked", title = "Liked songs"))
        MusicTile.Ask -> MusicPlaceNav.openAsk()
        is MusicTile.SeeAll -> nav.navigateTo(Destination.MusicList(tile.kind, title = tile.title))
        is MusicTile.Browse -> nav.navigateTo(Destination.MusicList("themes", tile.group, tile.title))
        MusicTile.AllSongs -> nav.navigateTo(Destination.MusicList("songs", title = "All songs"))
    }
}
