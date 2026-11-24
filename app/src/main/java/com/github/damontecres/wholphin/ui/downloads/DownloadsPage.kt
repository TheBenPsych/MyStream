package com.github.damontecres.wholphin.ui.downloads

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.DownloadService
import com.github.damontecres.wholphin.services.DownloadStatus
import com.github.damontecres.wholphin.services.DownloadedItem
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NowPlayingStatus
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.music.MusicButton
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.Dialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel
    @Inject
    constructor(
        val service: DownloadService,
        val musicService: MusicService,
        val manga: com.github.damontecres.wholphin.services.MangaOffline,
        private val serverRepository: com.github.damontecres.wholphin.data.ServerRepository,
    ) : ViewModel() {
        /** The manga chapters saved on this phone that this profile may see (never Kids) */
        fun mangaFor(
            all: List<com.github.damontecres.wholphin.services.MangaSaved>,
            profile: OfflineProfile?,
        ): List<com.github.damontecres.wholphin.services.MangaSaved> {
            val kids = profile?.kids ?: com.github.damontecres.wholphin.services.KidsMode.isActive
            if (kids) return emptyList()
            val userId = (profile?.userId ?: serverRepository.currentUser?.id)?.toString() ?: return emptyList()
            return all.filter { it.userId == userId }
        }

        fun playMusic(
            songs: List<DownloadedItem>,
            index: Int,
        ) = viewModelScope.launchIO { musicService.playDownloaded(songs, index) }

        fun playPause() {
            val p = musicService.player
            if (p.isPlaying) p.pause() else p.play()
        }
    }

/**
 * Benflix on phones: the Download button under Play on a movie or episode page.
 * Shows how far along a download is, and once it's done, tapping it plays the saved copy.
 */
@Composable
fun DownloadButton(
    item: BaseItem,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val service = viewModel.service
    // Songs are saved from their More menu instead
    if (!service.canDownload(item) || item.type == org.jellyfin.sdk.model.api.BaseItemKind.AUDIO) return
    val all by service.items.collectAsState()
    val downloaded = all.firstOrNull { it.itemId == item.id }
    var status by remember { mutableStateOf<DownloadStatus>(DownloadStatus.None) }
    LaunchedEffect(downloaded) {
        while (true) {
            status = downloaded?.let { service.status(it) } ?: DownloadStatus.None
            if (status !is DownloadStatus.Downloading) break
            delay(1000)
        }
    }
    var starting by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<DownloadedItem?>(null) }
    // Download size: asked the first time, then remembered (change it on the Downloads page)
    var askSize by remember { mutableStateOf(false) }
    LaunchedEffect(starting) {
        if (starting) {
            service.start(item, smaller = service.smallerDownloads == true)
            starting = false
        }
    }
    if (askSize) {
        val runtimeMs = (item.data.runTimeTicks ?: 0L) / 10_000L
        val full =
            item.data.mediaSources
                ?.firstOrNull()
                ?.size
        com.github.damontecres.wholphin.ui.components.BenflixChoiceDialog(
            title = "Download size",
            subtitle = "Glowfin remembers this. You can change it on the Downloads page.",
            choices =
                listOf(
                    com.github.damontecres.wholphin.ui.components.BenflixChoice(
                        label = "Best picture",
                        note = listOfNotNull("The file as it is", full?.let { "about ${sizeWords(it)}" }).joinToString(", "),
                        onClick = {
                            service.smallerDownloads = false
                            starting = true
                        },
                    ),
                    com.github.damontecres.wholphin.ui.components.BenflixChoice(
                        label = "Smaller",
                        note =
                            listOfNotNull(
                                "Fits more on the phone",
                                runtimeMs.takeIf { it > 0 }?.let { "about ${sizeWords(service.smallerBytes(it))}" },
                            ).joinToString(", "),
                        onClick = {
                            service.smallerDownloads = true
                            starting = true
                        },
                    ),
                ),
            onDismissRequest = { askSize = false },
        )
    }
    val (icon, label) =
        when (val s = status) {
            DownloadStatus.None -> {
                R.string.fa_download to stringResource(R.string.download)
            }

            is DownloadStatus.Downloading -> {
                R.string.fa_download to
                    if (s.waitingForWifi) {
                        stringResource(R.string.download_waiting_wifi)
                    } else {
                        stringResource(R.string.downloading_percent, s.percent)
                    }
            }

            DownloadStatus.Done -> {
                R.string.fa_circle_check to stringResource(R.string.downloaded_play)
            }

            DownloadStatus.Failed -> {
                R.string.fa_rotate_right to stringResource(R.string.download_failed)
            }
        }
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF2B2B2B))
                .clickable {
                    when (status) {
                        DownloadStatus.None -> {
                            if (service.smallerDownloads == null) askSize = true else starting = true
                        }

                        DownloadStatus.Done -> {
                            playing = downloaded
                        }

                        DownloadStatus.Failed -> {
                            service.delete(item.id)
                            starting = true
                        }

                        is DownloadStatus.Downloading -> {}
                    }
                }.padding(vertical = 10.dp),
    ) {
        Text(text = stringResource(icon), fontFamily = FontAwesome, fontSize = 16.sp, color = Color.White)
        Text(
            text = label,
            fontSize = BenflixType.body,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    playing?.let { LocalPlayer(it, service) { playing = null } }
}

/**
 * Benflix on phones: everything saved on this phone. Tap one to watch it, even with no internet.
 */
@Composable
fun DownloadsPage(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
    offlineProfile: OfflineProfile? = null,
) {
    val service = viewModel.service
    BackHandler(onBack = onClose)
    val all by service.items.collectAsState()
    val visible =
        remember(all, offlineProfile) {
            if (offlineProfile == null) {
                service.visibleItems(all)
            } else {
                service.visibleItems(all, offlineProfile.userId, offlineProfile.kids)
            }
        }
    val items = remember(visible) { visible.filterNot { it.music } }
    // Benflix music: saved songs, oldest first so an album plays in order (a song saved twice shows once; two
    // with the same id crashed the list)
    val songs = remember(visible) { visible.filter { it.music }.sortedBy { it.addedAt }.distinctBy { it.itemId } }
    var playing by remember { mutableStateOf<DownloadedItem?>(null) }
    var confirmDelete by remember { mutableStateOf<DownloadedItem?>(null) }
    // Manga chapters saved on this phone (Ben 2026-11-21): tap a series to read it, even with no internet
    val mangaAll by viewModel.manga.items.collectAsState()
    val manga = remember(mangaAll, offlineProfile) { viewModel.mangaFor(mangaAll, offlineProfile) }
    var readingManga by remember { mutableStateOf<String?>(null) }
    // Refresh progress once a second while anything is still downloading
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(items) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        Text(
            text = stringResource(R.string.downloads_title),
            fontSize = BenflixType.pageTitle,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(16.dp),
        )
        if (items.isEmpty() && songs.isEmpty() && manga.isEmpty()) {
            Text(
                text = stringResource(R.string.downloads_none),
                fontSize = BenflixType.body,
                color = Color(0xFFB3B3B3),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            return@Column
        }
        // Free up space (Ben 2026-11-21): how much room the downloads take, and one press to delete what's been watched
        val used = remember(visible, manga, tick / 5) { service.bytesUsed(visible) + viewModel.manga.bytesUsed(manga) }
        val watched = remember(items, tick / 5) { items.filter { service.isWatched(it.itemId) } }
        var smaller by remember { mutableStateOf(service.smallerDownloads == true) }
        var confirmWatched by remember { mutableStateOf(false) }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        ) {
            Text(
                text = "Using ${sizeWords(used)} on this phone",
                fontSize = BenflixType.small,
                color = Color(0xFFB3B3B3),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (watched.isNotEmpty()) {
                    Text(
                        text = "Delete watched (${watched.size})",
                        fontSize = BenflixType.small,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF2B2B2B))
                                .clickable { confirmWatched = true }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                Text(
                    text = if (smaller) "New downloads: Smaller" else "New downloads: Best picture",
                    fontSize = BenflixType.small,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF2B2B2B))
                            .clickable {
                                smaller = !smaller
                                service.smallerDownloads = smaller
                            }.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        if (confirmWatched) {
            com.github.damontecres.wholphin.ui.components.BenflixChoiceDialog(
                title = "Delete ${watched.size} watched download${if (watched.size == 1) "" else "s"}?",
                subtitle = "They stay on Glowfin. Only the copies on this phone go.",
                choices =
                    listOf(
                        com.github.damontecres.wholphin.ui.components.BenflixChoice(
                            label = "Delete them",
                            onClick = { watched.forEach { service.delete(it.itemId) } },
                        ),
                        com.github.damontecres.wholphin.ui.components
                            .BenflixChoice(label = "Keep them", onClick = {}),
                    ),
                onDismissRequest = { confirmWatched = false },
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            if (manga.isNotEmpty()) {
                item(key = "manga") {
                    MangaDownloads(
                        saved = manga,
                        coverOf = { viewModel.manga.coverFile(it) },
                        onRead = { readingManga = it },
                        onDelete = { viewModel.manga.deleteSeries(it) },
                    )
                }
            }
            if (songs.isNotEmpty()) {
                item(key = "music-title") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Text(
                            "Music · ${songs.size} songs",
                            fontSize = BenflixType.body,
                            fontWeight = FontWeight.Bold,
                            color = BenflixColors.Music,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "Play all",
                            fontSize = BenflixType.small,
                            fontWeight = FontWeight.Bold,
                            color = BenflixColors.OnMusic,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(BenflixColors.Music)
                                    .clickable { viewModel.playMusic(songs.filter { service.status(it) == DownloadStatus.Done }, 0) }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
                items(songs, key = { "song-${it.itemId}" }) { song ->
                    val status = remember(song, tick) { service.status(song) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (status == DownloadStatus.Done) {
                                        val ready = songs.filter { service.status(it) == DownloadStatus.Done }
                                        viewModel.playMusic(ready, ready.indexOf(song).coerceAtLeast(0))
                                    }
                                }.padding(horizontal = 16.dp, vertical = 2.dp),
                    ) {
                        AsyncImage(
                            model = service.posterFile(song.itemId),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF2B2B2B)),
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(
                                song.title,
                                fontSize = BenflixType.body,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                when (status) {
                                    DownloadStatus.Done -> {
                                        song.subtitle.orEmpty()
                                    }

                                    is DownloadStatus.Downloading -> {
                                        if (status.waitingForWifi) {
                                            stringResource(
                                                R.string.download_waiting_wifi,
                                            )
                                        } else {
                                            stringResource(R.string.downloading_percent, status.percent)
                                        }
                                    }

                                    else -> {
                                        stringResource(R.string.download_failed_short)
                                    }
                                },
                                fontSize = BenflixType.small,
                                color = if (status == DownloadStatus.Failed) Color(0xFFFF8A8A) else Color(0xFFB3B3B3),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = stringResource(R.string.fa_trash_can),
                            fontFamily = FontAwesome,
                            fontSize = 18.sp,
                            color = Color(0xFFB3B3B3),
                            modifier = Modifier.clickable { confirmDelete = song }.padding(12.dp),
                        )
                    }
                }
                if (items.isNotEmpty()) {
                    item(key = "video-title") {
                        Text(
                            "Movies and shows",
                            fontSize = BenflixType.body,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                        )
                    }
                }
            }
            items(items, key = { it.itemId }) { item ->
                val status = remember(item, tick) { service.status(item) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { if (status == DownloadStatus.Done) playing = item }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    AsyncImage(
                        model = service.posterFile(item.itemId),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .width(64.dp)
                                .height(96.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF2B2B2B)),
                    )
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                    ) {
                        Text(
                            text = item.title,
                            fontSize = BenflixType.body,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        item.subtitle?.let {
                            Text(
                                text = it,
                                fontSize = BenflixType.small,
                                color = Color(0xFFB3B3B3),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text =
                                when (status) {
                                    DownloadStatus.Done -> {
                                        val size = service.file(item).length() / (1024 * 1024)
                                        if (size >= 1024) "%.1f GB".format(size / 1024f) else "$size MB"
                                    }

                                    is DownloadStatus.Downloading -> {
                                        if (status.waitingForWifi) {
                                            stringResource(R.string.download_waiting_wifi)
                                        } else {
                                            stringResource(R.string.downloading_percent, status.percent)
                                        }
                                    }

                                    else -> {
                                        stringResource(R.string.download_failed_short)
                                    }
                                },
                            fontSize = BenflixType.small,
                            color = if (status == DownloadStatus.Failed) Color(0xFFFF8A8A) else Color(0xFFB3B3B3),
                        )
                    }
                    Text(
                        text = stringResource(R.string.fa_trash_can),
                        fontFamily = FontAwesome,
                        fontSize = 18.sp,
                        color = Color(0xFFB3B3B3),
                        modifier =
                            Modifier
                                .clickable { confirmDelete = item }
                                .padding(12.dp),
                    )
                }
            }
        }
        DownloadsMusicBar(viewModel)
    }
    confirmDelete?.let { item ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { androidx.compose.material3.Text(stringResource(R.string.download_delete_title)) },
            text = { androidx.compose.material3.Text(item.title) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    service.delete(item.itemId)
                    confirmDelete = null
                }) { androidx.compose.material3.Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = null }) {
                    androidx.compose.material3.Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    playing?.let { LocalPlayer(it, service) { playing = null } }
    readingManga?.let { volId ->
        // The reader on top of Downloads: Back comes back here, and the next saved chapter opens in place
        Dialog(
            onDismissRequest = { readingManga = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            androidx.compose.runtime.key(volId) {
                com.github.damontecres.wholphin.ui.books.PageReaderPage(
                    id = volId,
                    book = false,
                    onClose = { readingManga = null },
                    onOpen = { readingManga = it },
                )
            }
        }
    }
}

/**
 * Manga saved on this phone: one row per series (cover, "12 saved" or how far the download is), tap it to read
 * the chapter you're on, the saved chapters as chips, and a bin to remove them from the phone.
 */
@Composable
private fun MangaDownloads(
    saved: List<com.github.damontecres.wholphin.services.MangaSaved>,
    coverOf: (String) -> java.io.File,
    onRead: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val series = remember(saved) { saved.groupBy { it.titleId }.values.map { list -> list.sortedBy { it.num } } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            "Manga",
            fontSize = BenflixType.body,
            fontWeight = FontWeight.Bold,
            color = BenflixColors.Books,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        series.forEach { chapters ->
            val first = chapters.first()
            val ready = chapters.filter { it.done }
            val coming = chapters.count { !it.done && !it.failed }
            val failed = chapters.count { it.failed }
            // Tapping the series opens the first saved chapter you haven't finished (or the first one)
            val next = ready.firstOrNull { it.page < it.pages - 1 } ?: ready.firstOrNull()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = next != null) { next?.let { onRead(it.volId) } }
                            .padding(horizontal = 16.dp),
                ) {
                    AsyncImage(
                        model = coverOf(first.titleId),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(width = 48.dp, height = 72.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF2B2B2B)),
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            first.series,
                            fontSize = BenflixType.body,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(
                                "${ready.size} saved".takeIf { ready.isNotEmpty() },
                                "$coming still coming".takeIf { coming > 0 },
                                "$failed couldn't be saved (open the manga and press Download again)".takeIf { failed > 0 },
                            ).joinToString(" · "),
                            fontSize = BenflixType.small,
                            color = if (failed > 0) Color(0xFFFF8A8A) else Color(0xFFB3B3B3),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = stringResource(R.string.fa_trash_can),
                        fontFamily = FontAwesome,
                        fontSize = 18.sp,
                        color = Color(0xFFB3B3B3),
                        modifier =
                            Modifier
                                .clickable { onDelete(first.titleId) }
                                .padding(12.dp),
                    )
                }
                if (ready.isNotEmpty()) {
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding =
                            androidx.compose.foundation.layout
                                .PaddingValues(horizontal = 16.dp),
                    ) {
                        items(ready, key = { "manga-${it.volId}" }) { c ->
                            Text(
                                text = c.name,
                                fontSize = BenflixType.small,
                                fontWeight = FontWeight.Bold,
                                color = if (c == next) BenflixColors.OnBooks else Color.White,
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (c == next) BenflixColors.Books else Color(0xFF2B2B2B))
                                        .clickable { onRead(c.volId) }
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Plays a downloaded file full screen, sideways, picking up where you left off */
@Composable
fun LocalPlayer(
    item: DownloadedItem,
    service: DownloadService,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val player =
        remember(item.itemId) {
            // Use the extra decoders (AV1, DTS and others) when this build has them
            val renderers =
                androidx.media3.exoplayer
                    .DefaultRenderersFactory(context)
                    .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            ExoPlayer.Builder(context, renderers).build().apply {
                setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(service.file(item))))
                seekTo(service.position(item.itemId))
                prepare()
                playWhenReady = true
            }
        }
    DisposableEffect(item.itemId) {
        val activity = context as? Activity
        val before = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            val pos = player.currentPosition
            val duration = player.duration
            // Finished (or nearly)? Start from the beginning next time
            val finished = duration > 0 && pos > duration - 60_000
            if (finished) service.markWatched(item.itemId)
            service.savePosition(item.itemId, if (finished) 0L else pos)
            player.release()
            if (before != null) activity.requestedOrientation = before
        }
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        setShowSubtitleButton(true)
                        keepScreenOn = true
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                text = stringResource(R.string.fa_xmark),
                fontFamily = FontAwesome,
                fontSize = 22.sp,
                color = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .clickable(onClick = onClose)
                        .padding(20.dp)
                        .size(28.dp),
            )
        }
    }
}

/**
 * Benflix music in Downloads: what's playing from your saved songs, with previous, play/pause and next.
 * Works with no internet (the normal Now playing needs Tower).
 */
@Composable
private fun DownloadsMusicBar(viewModel: DownloadsViewModel) {
    val music by viewModel.musicService.state.collectAsState()
    val audio = music.currentAudio ?: return
    if (music.queueSize == 0) return
    val playing = music.status == NowPlayingStatus.PLAYING
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BenflixColors.PanelHigh)
                .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        AsyncImage(
            model = audio.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF2B2B2B)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                audio.title.orEmpty(),
                fontSize = BenflixType.small,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                audio.artistNames.orEmpty(),
                fontSize = BenflixType.tag,
                color = Color(0xFFB3B3B3),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MusicButton(null, BenflixIcon.PREVIOUS, onClick = { viewModel.musicService.player.seekToPreviousMediaItem() })
        MusicButton(null, if (playing) BenflixIcon.PAUSE else null, filledPlay = !playing, onClick = viewModel::playPause)
        MusicButton(null, BenflixIcon.NEXT, onClick = { viewModel.musicService.player.seekToNextMediaItem() })
    }
}

/** "1.4 GB" / "320 MB" */
internal fun sizeWords(bytes: Long): String =
    if (bytes >= 1_000_000_000L) {
        String.format(java.util.Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
    } else {
        "${bytes / 1_000_000L} MB"
    }
