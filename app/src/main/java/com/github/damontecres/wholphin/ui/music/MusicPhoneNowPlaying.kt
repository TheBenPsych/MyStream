package com.github.damontecres.wholphin.ui.music

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
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
import com.github.damontecres.wholphin.ui.components.BenflixHeartFilled
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.components.BenflixIconImage
import com.github.damontecres.wholphin.ui.components.BenflixPlayFilled
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import org.jellyfin.sdk.model.api.LyricDto
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/**
 * Phone Now playing, built for one hand (Ben picked option B, plus lyrics and the music bars):
 * big album cover on top (swipe it left/right to skip), the song name centred, the words under it,
 * then a thick seek bar, the big back / play-pause / next trio in the middle, and heart / Up next / Stop
 * just below, all in the bottom half where the thumb rests. The bars bounce along the very bottom.
 */
@Composable
internal fun PhoneNowPlaying(
    viewModel: MusicNowPlayingViewModel,
    audio: AudioItem?,
    from: String?,
    lyrics: LyricDto?,
    lyricIndex: Int?,
    next: AudioItem?,
    position: Long,
    duration: Long,
    playing: Boolean,
    liked: Boolean,
    easy: Boolean,
    towersHeight: Dp,
    boxHeight: Dp,
    boxWidth: Dp,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 14.dp),
    ) {
        val coverSize = minOf(boxWidth - 48.dp, boxHeight * (if (lyrics != null) 0.31f else 0.42f))
        SwipeCover(audio, coverSize, onNext = viewModel::next, onPrevious = viewModel::previous)
        Spacer(Modifier.height(14.dp))
        Text(
            text = audio?.title.orEmpty(),
            color = BenflixColors.Text,
            fontSize = if (easy) 28.sp else 24.sp,
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
            MusicKicker("From $from", Modifier.padding(top = 6.dp))
        }
        Words(lyrics, lyricIndex, next, easy)
        SeekBar(position, duration) { viewModel.player.seekTo(it) }
        Spacer(Modifier.height(14.dp))
        // The big trio, centred, play/pause in the middle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            RoundButton(64.dp, BenflixIcon.PREVIOUS, "Previous song", onClick = viewModel::previous)
            RoundButton(
                size = 84.dp,
                icon = if (playing) BenflixIcon.PAUSE else null,
                description = if (playing) "Pause" else "Play",
                primary = true,
                filledPlay = !playing,
                onClick = viewModel::playPause,
            )
            RoundButton(64.dp, BenflixIcon.NEXT, "Next song", onClick = viewModel::next)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SmallButton(if (liked) "Liked" else "Like", BenflixIcon.HEART, easy, filledHeart = liked, onClick = viewModel::like)
            SmallButton("Up next", BenflixIcon.LIST, easy, onClick = viewModel::openUpNext)
            SmallButton("Stop", BenflixIcon.STOP, easy, onClick = viewModel::stop)
        }
        // The bars sit along the bottom; the small buttons overlap their tops a little
        Spacer(Modifier.height(towersHeight * 0.55f))
    }
}

/** The lyrics (centred, the line being sung in white), or the next song when there are none */
@Composable
private fun ColumnScope.Words(
    lyrics: LyricDto?,
    lyricIndex: Int?,
    next: AudioItem?,
    easy: Boolean,
) {
    if (lyrics != null) {
        LyricsColumn(
            lyrics,
            lyricIndex,
            Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp, bottom = 8.dp),
            fontSize = if (easy) 19f else 16f,
            center = true,
        )
    } else {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { UpNextLine(next) }
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
        CoverImage(audio?.imageUrl, Modifier.fillMaxSize(), shape = RoundedCornerShape(16.dp))
    }
}

/** Thick seek bar with a round handle: tap or drag anywhere on it to jump in the song */
@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
) {
    var dragAt by remember { mutableStateOf<Float?>(null) }
    val f = dragAt ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(duration) {
                    detectTapGestures { o ->
                        if (duration > 0) onSeek((o.x / size.width * duration).toLong().coerceIn(0L, duration))
                    }
                }.pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragAt = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragAt?.let { if (duration > 0) onSeek((it * duration).toLong()) }
                            dragAt = null
                        },
                        onDragCancel = { dragAt = null },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragAt = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val w = maxWidth
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .background(BenflixColors.Line, RoundedCornerShape(4.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(f)
                        .fillMaxHeight()
                        .background(BenflixColors.Music, RoundedCornerShape(4.dp)),
                )
            }
            Box(
                Modifier
                    .offset(x = (w - 18.dp) * f)
                    .size(18.dp)
                    .background(BenflixColors.Text, CircleShape),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            val shownAt = dragAt?.let { (it * duration).toLong() } ?: position
            Text(songLength(shownAt.milliseconds), color = BenflixColors.Muted, fontSize = BenflixType.small)
            Spacer(Modifier.weight(1f))
            Text(songLength(duration.milliseconds), color = BenflixColors.Muted, fontSize = BenflixType.small)
        }
    }
}

@Composable
private fun RoundButton(
    size: Dp,
    icon: BenflixIcon?,
    description: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    filledPlay: Boolean = false,
) {
    val fg = if (primary) BenflixColors.Tray else BenflixColors.Text
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (primary) BenflixColors.Text else BenflixColors.Raised)
                .clickable(onClickLabel = description, onClick = onClick),
    ) {
        val iconSize = size * 0.4f
        if (filledPlay) {
            Image(
                BenflixPlayFilled,
                contentDescription = description,
                colorFilter = ColorFilter.tint(fg),
                modifier = Modifier.size(iconSize),
            )
        } else if (icon != null) {
            BenflixIconImage(icon, color = fg, size = iconSize, contentDescription = description)
        }
    }
}

/** Heart / Up next / Stop: 52dp circles (a comfy thumb target), with their names under them in Easy read */
@Composable
private fun SmallButton(
    label: String,
    icon: BenflixIcon,
    easy: Boolean,
    onClick: () -> Unit,
    filledHeart: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = label, onClick = onClick).padding(4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(52.dp).background(BenflixColors.Raised, CircleShape),
        ) {
            if (filledHeart) {
                Image(
                    BenflixHeartFilled,
                    contentDescription = label,
                    colorFilter = ColorFilter.tint(BenflixColors.Music),
                    modifier = Modifier.size(22.dp),
                )
            } else {
                BenflixIconImage(icon, color = BenflixColors.Text, size = 22.dp, contentDescription = label)
            }
        }
        if (easy) {
            Text(label, color = BenflixColors.Soft, fontSize = BenflixType.small, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
