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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
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
import com.github.damontecres.wholphin.ui.FontAwesome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel
    @Inject
    constructor(
        val service: DownloadService,
    ) : ViewModel()

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
    if (!service.canDownload(item)) return
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
    LaunchedEffect(starting) {
        if (starting) {
            service.start(item)
            starting = false
        }
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
                            starting = true
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
            fontSize = 15.sp,
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
) {
    val service = viewModel.service
    BackHandler(onBack = onClose)
    val all by service.items.collectAsState()
    val items = remember(all) { service.visibleItems(all) }
    var playing by remember { mutableStateOf<DownloadedItem?>(null) }
    var confirmDelete by remember { mutableStateOf<DownloadedItem?>(null) }
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
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(16.dp),
        )
        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.downloads_none),
                fontSize = 15.sp,
                color = Color(0xFFB3B3B3),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            return@Column
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
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
                                .clip(RoundedCornerShape(4.dp))
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
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        item.subtitle?.let {
                            Text(text = it, fontSize = 13.sp, color = Color(0xFFB3B3B3), maxLines = 1)
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
                            fontSize = 13.sp,
                            color = if (status == DownloadStatus.Failed) Color(0xFFE50914) else Color(0xFFB3B3B3),
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
            service.savePosition(item.itemId, if (duration > 0 && pos > duration - 60_000) 0L else pos)
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
