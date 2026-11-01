package com.github.damontecres.wholphin.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.TitleOrLogo
import com.github.damontecres.wholphin.ui.components.rememberLogoUrl
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.ImageType

/**
 * Trailer shuffle on the TV: one trailer from your own library after another, full screen with sound.
 * Down or Right = next trailer, Up or Left = the one before, OK = watch it, Back = leave.
 * The title and buttons fade away after a few seconds and come back on any press.
 */
@Composable
fun TrailerShufflePage(
    modifier: Modifier = Modifier,
    viewModel: ClipsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val focus = remember { FocusRequester() }
    var index by rememberSaveable { mutableIntStateOf(0) }
    // Bumped on every press so the title card shows again, then fades
    var pokes by remember { mutableIntStateOf(0) }
    var showInfo by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(50)
        focus.tryRequestFocus()
    }
    LaunchedEffect(pokes, index) {
        showInfo = true
        delay(INFO_SHOW_MS)
        showInfo = false
    }
    val clip = state.clips.getOrNull(index)
    LifecycleResumeEffect(clip?.trailerId) {
        viewModel.play(clip)
        viewModel.preload(state.clips.getOrNull(index + 1))
        onPauseOrDispose { viewModel.pause() }
    }
    DisposableEffect(Unit) { onDispose { viewModel.pause() } }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val count = state.clips.size
                    when (event.key) {
                        Key.DirectionDown, Key.DirectionRight, Key.MediaNext, Key.ChannelDown -> {
                            if (count > 0) index = (index + 1).coerceAtMost(count - 1)
                            pokes++
                            true
                        }

                        Key.DirectionUp, Key.DirectionLeft, Key.MediaPrevious, Key.ChannelUp -> {
                            index = (index - 1).coerceAtLeast(0)
                            pokes++
                            true
                        }

                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            clip?.let { viewModel.playMovie(it) }
                            true
                        }

                        Key.Menu, Key.Info -> {
                            clip?.let { viewModel.openDetails(it) }
                            true
                        }

                        else -> {
                            pokes++
                            false
                        }
                    }
                }.focusable(),
    ) {
        if (clip == null) {
            Text(
                text = stringResource(if (state.loading) R.string.loading else R.string.clips_none),
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }
        val imageUrlService = LocalImageUrlService.current
        val backdrop =
            remember(clip.item.id) {
                imageUrlService.getItemImageUrl(clip.item, ImageType.BACKDROP, fillWidth = 1920)
            }
        // The backdrop shows until the trailer starts
        AsyncImage(
            model = backdrop,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        viewModel.player?.let { player ->
            PlayerSurface(
                player = player,
                surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                modifier = Modifier.fillMaxSize(),
            )
        }
        AnimatedVisibility(
            visible = showInfo,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.5f to Color.Transparent,
                                1f to Color.Black.copy(alpha = .9f),
                            ),
                        ),
            ) {
                Text(
                    text = "TRAILER SHUFFLE  ·  ${index + 1} OF ${state.clips.size}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                    color = Color.White.copy(alpha = .8f),
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(32.dp),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(start = 48.dp, end = 48.dp, bottom = 40.dp),
                ) {
                    TitleOrLogo(
                        title = clip.item.title,
                        logoImageUrl = rememberLogoUrl(clip.item),
                        showLogo = true,
                        logoHeight = 80.dp,
                        logoMaxWidth = 360.dp,
                    )
                    clip.item.data.overview?.let {
                        Text(
                            text = it,
                            color = Color.White.copy(alpha = .85f),
                            fontSize = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 720.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Hint("OK", "Watch")
                        Hint("▼ ▶", "Next")
                        Hint("▲ ◀", "Previous")
                        Hint("☰", "More info")
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(
    key: String,
    label: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = key,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier =
                Modifier
                    .background(Color.White, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Text(text = label, fontSize = 14.sp, color = Color.White)
    }
}

private const val INFO_SHOW_MS = 4_000L
