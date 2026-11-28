package com.github.damontecres.wholphin.ui.music

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.AudioItem
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixShapes
import com.github.damontecres.wholphin.ui.theme.BenflixType
import kotlin.math.abs

/**
 * Phone Now playing (music player redesign 2026-11-17): the same pieces as the TV, stacked for one hand.
 * The cover (swipe it left/right to skip), the name, the tower progress line (tap or drag it), Shuffle /
 * Previous / Play / Next, then Like, Play on, Stop and More. The panel (Lyrics, Up next, About, Songs like
 * this) peeks up from the bottom and fills the rest of the screen; tap its handle to pull it up full size.
 */
@Composable
internal fun PhoneNowPlaying(
    viewModel: MusicNowPlayingViewModel,
    audio: AudioItem?,
    from: String?,
    position: Long,
    duration: Long,
    playing: Boolean,
    liked: Boolean,
    barsOn: Boolean,
    shuffle: ShuffleMode,
    boxHeight: Dp,
    boxWidth: Dp,
    panelTabs: @Composable () -> Unit,
    panelBody: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = expanded) { expanded = false }
    val playFocus =
        remember {
            androidx.compose.ui.focus
                .FocusRequester()
        }
    Box(Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(top = 12.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 22.dp),
            ) {
                val coverSize = minOf(boxWidth - 44.dp, boxHeight * 0.3f)
                SwipeCover(audio, coverSize, onNext = viewModel::next, onPrevious = viewModel::previous)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = audio?.title.orEmpty(),
                    color = BenflixColors.Text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                val line = listOfNotNull(audio?.artistNames, audio?.albumTitle).filter { it.isNotBlank() }.joinToString(" · ")
                Text(
                    text = line,
                    color = BenflixColors.Soft,
                    fontSize = BenflixType.body,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
                if (!from.isNullOrBlank()) {
                    MusicKicker("From $from", Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(6.dp))
                PlayerProgress(
                    position,
                    duration,
                    playing,
                    barsOn,
                    isTv = false,
                    onSeek = viewModel::seekTo,
                    towers = 30,
                    height = 36.dp,
                )
                Spacer(Modifier.height(6.dp))
                PlayerControls(viewModel, playing, shuffle, playFocus, onPlayFocus = {}, big = true)
                Spacer(Modifier.height(10.dp))
                PlayerRoundButtons(viewModel, liked, size = 46.dp)
            }
            Spacer(Modifier.height(12.dp))
            // The panel peeks up from the bottom and takes whatever room is left (never an empty gap)
            PhonePanel(
                onHandle = { expanded = true },
                tabs = panelTabs,
                body = panelBody,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
        if (expanded) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(BenflixColors.Tray.copy(alpha = .7f))
                    .clickable(onClickLabel = "Close", onClick = { expanded = false }),
            )
            PhonePanel(
                onHandle = { expanded = false },
                tabs = panelTabs,
                body = panelBody,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f).align(Alignment.BottomCenter),
            )
        }
    }
}

/** The bottom sheet: a handle (tap to pull it up or let it down), the tabs, and the chosen tab */
@Composable
private fun PhonePanel(
    onHandle: () -> Unit,
    tabs: @Composable () -> Unit,
    body: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .background(
                    com.github.damontecres.wholphin.ui.theme.Glowfin.Deep,
                    RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                ).padding(horizontal = 14.dp)
                .padding(bottom = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .clickable(onClickLabel = "Pull up", onClick = onHandle),
        ) {
            Box(Modifier.size(width = 44.dp, height = 5.dp).background(BenflixColors.Muted, RoundedCornerShape(3.dp)))
        }
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), contentAlignment = Alignment.Center) { tabs() }
        Column(Modifier.fillMaxWidth().weight(1f)) { body() }
    }
}

/** Album cover you can swipe: left = next song, right = back a song. It follows the finger, then springs back. */
@Composable
private fun SwipeCover(
    audio: AudioItem?,
    size: Dp,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val density = LocalDensity.current
    val limit = with(density) { 80.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val shown by animateFloatAsState(if (dragging) drag else 0f, label = "cover")
    Box(
        Modifier
            .size(size)
            .systemGestureExclusion()
            .graphicsLayer {
                translationX = if (dragging) drag else shown
                alpha = 1f - (abs(translationX) / (size.toPx() * 1.4f)).coerceIn(0f, 0.6f)
            }.pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        drag = 0f
                    },
                    onDragEnd = {
                        when {
                            drag < -limit -> onNext()
                            drag > limit -> onPrevious()
                        }
                        dragging = false
                    },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        drag += amount
                    },
                )
            },
    ) {
        CoverImage(audio?.imageUrl, Modifier.fillMaxSize(), shape = BenflixShapes.Panel)
    }
}
