@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.github.damontecres.wholphin.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.AudioItem
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import org.jellyfin.sdk.model.api.LyricDto
import org.jellyfin.sdk.model.extensions.ticks
import kotlin.time.Duration.Companion.milliseconds

/*
 * Music player 2026-11-17 (thread "Redesign the music player", Ben's final sheet): the song side centred
 * (cover, name, the tower progress line, Shuffle / Previous / Play / Next, then Like, Play on, Stop and More
 * as circles with a word under each), and one full-height panel with four tabs (Lyrics, Up next, About,
 * Songs like this) and the next three songs along its bottom. Same pieces on TV, phones and browser.
 */

/** The four tabs of the player's panel */
enum class PlayerTab(
    val label: String,
) {
    LYRICS("Lyrics"),
    UP_NEXT("Up next"),
    ABOUT("About"),
    LIKE_THIS("Songs like this"),
}

/** Shuffle on the player: one button that goes Off, then Shuffle, then Smart shuffle */
enum class ShuffleMode { OFF, SHUFFLE, SMART }

private const val SEEK_JUMP_MS = 10_000L

/**
 * The progress line made of towers, with the time either side. TV: select it and Left/Right jump 10 seconds.
 * Touch: tap or drag along it.
 */
@Composable
fun PlayerProgress(
    position: Long,
    duration: Long,
    playing: Boolean,
    barsOn: Boolean,
    isTv: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    towers: Int = 56,
    height: Dp = 40.dp,
) {
    var dragAt by remember { mutableStateOf<Float?>(null) }
    var focused by remember { mutableStateOf(false) }
    val f = dragAt ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shape = RoundedCornerShape(10.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        val shownAt = dragAt?.let { (it * duration).toLong() } ?: position
        Text(songLength(shownAt.milliseconds), color = BenflixColors.Muted, fontSize = BenflixType.small)
        Box(
            Modifier
                .weight(1f)
                .height(height + 10.dp)
                .then(
                    if (isTv) {
                        Modifier
                            .onFocusChanged { focused = it.isFocused }
                            .onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown || duration <= 0) return@onPreviewKeyEvent false
                                when (e.key) {
                                    Key.DirectionLeft -> {
                                        onSeek((position - SEEK_JUMP_MS).coerceAtLeast(0))
                                        true
                                    }

                                    Key.DirectionRight -> {
                                        onSeek((position + SEEK_JUMP_MS).coerceAtMost(duration))
                                        true
                                    }

                                    else -> {
                                        false
                                    }
                                }
                            }.focusable()
                            .border(if (focused) 3.dp else 0.dp, if (focused) BenflixColors.Focus else Color.Transparent, shape)
                    } else {
                        Modifier
                            .systemGestureExclusion()
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
                            }
                    },
                ).padding(horizontal = 6.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (barsOn) {
                TowerLine(progress = f, playing = playing, towers = towers, modifier = Modifier.fillMaxSize())
            } else {
                // Bars turned off in Settings: a plain line
                NowPlayingLine(progress = f, leftText = "", rightText = "", color = BenflixColors.Music)
            }
        }
        Text(songLength(duration.milliseconds), color = BenflixColors.Muted, fontSize = BenflixType.small)
    }
    if (isTv && focused) {
        Text(
            "Left and Right jump 10 seconds",
            color = BenflixColors.Muted,
            fontSize = BenflixType.tag,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A round button with its name under it (Ben's pick: circles with a small word). Always a perfect circle. */
@Composable
fun RoundWordButton(
    label: String,
    icon: BenflixIcon?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filledHeart: Boolean = false,
    lit: Boolean = false,
    size: Dp = 48.dp,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = size + 16.dp)) {
        val shape = CircleShape
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(shape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = if (lit) BenflixColors.Music.copy(alpha = .25f) else BenflixColors.Raised,
                    contentColor = if (lit) BenflixColors.Music else BenflixColors.Text,
                    focusedContainerColor = BenflixColors.Text,
                    focusedContentColor = BenflixColors.Tray,
                ),
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder =
                        Border(
                            androidx.compose.foundation.BorderStroke(3.dp, BenflixColors.Focus),
                            inset = 3.dp,
                            shape = shape,
                        ),
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier = modifier.size(size),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (filledHeart) {
                    androidx.compose.foundation.Image(
                        com.github.damontecres.wholphin.ui.components.BenflixHeartFilled,
                        contentDescription = label,
                        colorFilter =
                            androidx.compose.ui.graphics.ColorFilter
                                .tint(BenflixColors.Music),
                        modifier = Modifier.size(size * 0.44f),
                    )
                } else if (icon != null) {
                    com.github.damontecres.wholphin.ui.components.BenflixIconImage(
                        icon,
                        color =
                            androidx.tv.material3.LocalContentColor.current,
                        size = size * 0.42f,
                        contentDescription = label,
                    )
                }
            }
        }
        Text(
            label,
            color = if (lit) BenflixColors.Music else BenflixColors.Soft,
            fontSize = BenflixType.tag,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

/**
 * Shuffle, Previous, Play/Pause, Next. Ben: on the TV remote Left/Right only move between these (they don't
 * skip songs), so Previous and Next are real buttons. Easy mode: big pills with words.
 */
@Composable
fun PlayerControls(
    viewModel: MusicNowPlayingViewModel,
    playing: Boolean,
    shuffle: ShuffleMode,
    easy: Boolean,
    playFocus: FocusRequester,
    onPlayFocus: (Boolean) -> Unit,
    big: Boolean = false,
) {
    if (easy) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            MusicButton("Previous", BenflixIcon.PREVIOUS, onClick = viewModel::previous, big = true)
            MusicButton(
                if (playing) "Pause" else "Play",
                if (playing) BenflixIcon.PAUSE else null,
                filledPlay = !playing,
                primary = true,
                big = true,
                onClick = viewModel::playPause,
                modifier = Modifier.focusRequester(playFocus).onFocusChanged { if (it.isFocused) onPlayFocus(true) },
            )
            MusicButton("Next song", BenflixIcon.NEXT, onClick = viewModel::next, big = true)
        }
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (big) 18.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shuffleName =
            when (shuffle) {
                ShuffleMode.OFF -> "Shuffle is off"
                ShuffleMode.SHUFFLE -> "Shuffle is on"
                ShuffleMode.SMART -> "Smart shuffle is on"
            }
        PlainCircle(
            BenflixIcon.SHUFFLE,
            shuffleName,
            onClick = viewModel::cycleShuffle,
            size = if (big) 48.dp else 42.dp,
            lit = shuffle != ShuffleMode.OFF,
            sparkle = shuffle == ShuffleMode.SMART,
        )
        PlainCircle(BenflixIcon.PREVIOUS, "Previous song", onClick = viewModel::previous, size = if (big) 60.dp else 52.dp)
        PlainCircle(
            if (playing) BenflixIcon.PAUSE else BenflixIcon.PLAY,
            if (playing) "Pause" else "Play",
            onClick = viewModel::playPause,
            size = if (big) 80.dp else 68.dp,
            primary = true,
            modifier = Modifier.focusRequester(playFocus).onFocusChanged { if (it.isFocused) onPlayFocus(true) },
        )
        PlainCircle(BenflixIcon.NEXT, "Next song", onClick = viewModel::next, size = if (big) 60.dp else 52.dp)
        // Keeps Play in the middle: an empty space the size of Shuffle on the other side
        Spacer(Modifier.size(if (big) 48.dp else 42.dp))
    }
}

@Composable
private fun PlainCircle(
    icon: BenflixIcon,
    description: String,
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    lit: Boolean = false,
    sparkle: Boolean = false,
) {
    val shape = CircleShape
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (primary) BenflixColors.Text else Color.Transparent,
                contentColor =
                    if (primary) {
                        BenflixColors.Tray
                    } else if (lit) {
                        BenflixColors.Music
                    } else {
                        BenflixColors.Text
                    },
                focusedContainerColor = if (primary) BenflixColors.Text else BenflixColors.FocusRow,
                focusedContentColor =
                    if (primary) {
                        BenflixColors.Tray
                    } else if (lit) {
                        BenflixColors.Music
                    } else {
                        BenflixColors.Text
                    },
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(androidx.compose.foundation.BorderStroke(3.dp, BenflixColors.Focus), inset = 3.dp, shape = shape),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.size(size),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            com.github.damontecres.wholphin.ui.components.BenflixIconImage(
                icon,
                color = androidx.tv.material3.LocalContentColor.current,
                size = size * 0.42f,
                contentDescription = description,
            )
            if (sparkle) {
                Text(
                    "✦",
                    color = BenflixColors.Music,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 4.dp),
                )
            }
        }
    }
}

/** Like, Play on, Stop, More (Ben's picks). Easy mode: pills with words. */
@Composable
fun PlayerRoundButtons(
    viewModel: MusicNowPlayingViewModel,
    liked: Boolean,
    easy: Boolean,
    size: Dp = 48.dp,
) {
    if (easy) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            MusicButton(if (liked) "Liked" else "Like", BenflixIcon.HEART, filledHeart = liked, onClick = viewModel::like)
            MusicButton("Play on", BenflixIcon.TV, onClick = { viewModel.playOnOpen.value = true })
            MusicButton("Stop", BenflixIcon.STOP, onClick = viewModel::stop)
            MusicButton("More", BenflixIcon.MORE_VERT, onClick = viewModel::openMenu)
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        RoundWordButton(if (liked) "Liked" else "Like", BenflixIcon.HEART, onClick = viewModel::like, filledHeart = liked, size = size)
        RoundWordButton("Play on", BenflixIcon.TV, onClick = { viewModel.playOnOpen.value = true }, size = size)
        RoundWordButton("Stop", BenflixIcon.STOP, onClick = viewModel::stop, size = size)
        RoundWordButton("More", BenflixIcon.MORE_VERT, onClick = viewModel::openMenu, size = size)
    }
}

/** The panel's four tabs. TV: like title pages, the tab switches as soon as the remote lands on it. */
@Composable
fun PlayerTabs(
    tab: PlayerTab,
    tabs: List<PlayerTab>,
    noLyrics: Boolean,
    isTv: Boolean,
    easy: Boolean,
    onTab: (PlayerTab) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        tabs.forEach { t ->
            val on = t == tab
            val faded = t == PlayerTab.LYRICS && noLyrics
            val shape = RoundedCornerShape(50)
            Surface(
                onClick = { onTab(t) },
                shape = ClickableSurfaceDefaults.shape(shape),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = if (on) BenflixColors.Text else Color.Transparent,
                        contentColor = if (on) BenflixColors.Tray else BenflixColors.Soft,
                        focusedContainerColor = BenflixColors.Text,
                        focusedContentColor = BenflixColors.Tray,
                    ),
                border =
                    ClickableSurfaceDefaults.border(
                        focusedBorder =
                            Border(
                                androidx.compose.foundation.BorderStroke(3.dp, BenflixColors.Focus),
                                inset = 3.dp,
                                shape = shape,
                            ),
                    ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier = if (isTv) Modifier.onFocusChanged { if (it.isFocused) onTab(t) } else Modifier,
            ) {
                Text(
                    t.label,
                    fontSize = if (easy) BenflixType.lead else BenflixType.small,
                    fontWeight = FontWeight.Bold,
                    color = if (faded && !on) BenflixColors.Muted.copy(alpha = .6f) else androidx.tv.material3.LocalContentColor.current,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/** Is this song's words timed (so OK on a line can jump there)? */
fun LyricDto.isTimed(): Boolean = lyrics.any { it.start != null && it.start!! > 0 }

/** Where line [i] starts, in ms (null for untimed words) */
fun LyricDto.lineStartMs(i: Int): Long? {
    val start = lyrics.getOrNull(i)?.start ?: return null
    val offset = metadata.offset?.ticks?.inWholeMilliseconds ?: 0L
    return (start.ticks.inWholeMilliseconds - offset).coerceAtLeast(0)
}

/** "UP NEXT" along the bottom of the panel: the next three songs; OK on one plays it */
@Composable
fun PlayerUpNextStrip(
    coming: List<Pair<Int, AudioItem>>,
    onPlay: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(BenflixColors.Line))
        MusicKicker("Up next", color = BenflixColors.Muted)
        if (coming.isEmpty()) {
            Text("A mix like this one starts by itself.", color = BenflixColors.Muted, fontSize = BenflixType.small)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            coming.take(3).forEach { (index, song) ->
                SongChip(song, onClick = { onPlay(index) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SongChip(
    song: AudioItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArtist: Boolean = false,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = BenflixColors.Text,
                focusedContainerColor = BenflixColors.FocusRow,
                focusedContentColor = BenflixColors.Text,
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder = Border(androidx.compose.foundation.BorderStroke(3.dp, BenflixColors.Focus), inset = 3.dp, shape = shape),
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(5.dp),
        ) {
            CoverImage(song.imageUrl, Modifier.size(36.dp), shape = RoundedCornerShape(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title.orEmpty(),
                    fontSize = BenflixType.small,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showArtist) {
                    Text(
                        song.artistNames.orEmpty(),
                        fontSize = BenflixType.tag,
                        color = BenflixColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The full-height panel: tabs on top, the chosen tab in the middle, Up next along the bottom.
 * [content] draws the middle (it differs per tab and lives with the player's state).
 */
@Composable
fun PlayerPanelFrame(
    tabs: @Composable () -> Unit,
    upNext: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .background(Color(0x99161224), RoundedCornerShape(20.dp))
                .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        tabs()
        Column(Modifier.weight(1f).fillMaxWidth()) { content() }
        upNext?.invoke()
    }
}

/** A message in the middle of the panel (no words, nothing found ...) */
@Composable
fun PanelNote(
    title: String,
    words: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 12.dp)) {
        Text(title, color = BenflixColors.Text, fontSize = BenflixType.lead, fontWeight = FontWeight.Bold)
        Text(words, color = BenflixColors.Soft, fontSize = BenflixType.body)
    }
}

/** The About tab: the artist (photo, name, a few lines, Go to artist) and the credits */
@Composable
fun AboutTab(
    about: AboutSong?,
    onArtist: (java.util.UUID) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        if (about == null || about.loading) {
            Text("Looking it up…", fontSize = BenflixType.body, color = BenflixColors.Soft)
            return@Column
        }
        MusicKicker("About the artist")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(about.artistImage, Modifier.size(72.dp), shape = CircleShape)
            Column(Modifier.weight(1f)) {
                Text(
                    about.artistName.orEmpty(),
                    fontSize = BenflixType.rowName,
                    fontWeight = FontWeight.ExtraBold,
                    color = BenflixColors.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            about.artistId?.let { id -> MusicButton("Go to artist", BenflixIcon.PERSON, onClick = { onArtist(id) }) }
        }
        about.artistBio?.let {
            Text(it, fontSize = BenflixType.small, color = BenflixColors.Soft, maxLines = 5, overflow = TextOverflow.Ellipsis)
        }
        if (about.credits.isNotEmpty()) {
            MusicKicker("Credits", Modifier.padding(top = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                about.credits.forEach { (role, names) ->
                    Column(Modifier.widthIn(max = 260.dp)) {
                        Text(role, fontSize = BenflixType.tag, color = BenflixColors.Muted)
                        Text(
                            names,
                            fontSize = BenflixType.small,
                            color = BenflixColors.Text,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** The Up next tab: the next songs (OK plays one) and a way into the full queue (drag to move songs) */
@Composable
fun UpNextTab(
    coming: List<Pair<Int, AudioItem>>,
    onPlay: (Int) -> Unit,
    onQueue: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        if (coming.isEmpty()) {
            PanelNote("Nothing waiting", "When this song ends, a mix like it starts by itself.")
        }
        coming.forEach { (index, song) ->
            SongChip(song, onClick = { onPlay(index) }, modifier = Modifier.fillMaxWidth(), showArtist = true)
        }
        Spacer(Modifier.height(6.dp))
        MusicButton("Open the queue", BenflixIcon.LIST, onClick = onQueue)
    }
}

/** The Songs like this tab: songs like this one that aren't on Benflix yet, 30-second tastes, Ask for it */
@Composable
fun LikeThisTab(
    state: SimilarState?,
    note: String?,
    onPick: (com.github.damontecres.wholphin.services.MusicSong) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        when (state) {
            null, SimilarState.Loading -> {
                Text("Looking for songs like this one…", fontSize = BenflixType.body, color = BenflixColors.Soft)
            }

            is SimilarState.Problem -> {
                PanelNote("Couldn't look right now", state.words)
            }

            is SimilarState.Songs -> {
                Text(
                    "Like ${state.playing}, and not on Benflix yet. The round button plays 30 seconds. Pick a song to ask for it.",
                    fontSize = BenflixType.small,
                    color = BenflixColors.Muted,
                )
                if (!note.isNullOrBlank()) Text(note, fontSize = BenflixType.small, color = BenflixColors.Music)
                if (state.songs.isEmpty()) {
                    PanelNote("Nothing new right now", "There's nothing like this one to ask for yet. Try again on another song.")
                }
                state.songs.forEach { song ->
                    SimilarLine(song = song, onClick = { if (song.asked == null && !song.have) onPick(song) })
                }
            }
        }
    }
}

/** The tower progress line for the small players (no times, no seeking) */
@Composable
fun SmallTowerLine(
    progress: Float,
    playing: Boolean,
    modifier: Modifier = Modifier,
    towers: Int = 40,
) {
    TowerLine(progress = progress, playing = playing, towers = towers, modifier = modifier, measure = false)
}

/** One line of the words: OK (or a tap) plays from there when the words are timed */
@Composable
fun LyricLine(
    text: String,
    current: Boolean,
    fontSize: Float,
    center: Boolean,
    onJump: (() -> Unit)?,
    isTv: Boolean,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (center) Arrangement.Center else Arrangement.Start,
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (onJump != null) {
                        Modifier
                            .onFocusChanged { focused = it.isFocused }
                            .border(if (focused) 2.dp else 0.dp, if (focused) BenflixColors.Focus else Color.Transparent, shape)
                            .clickable(onClickLabel = "Play from here", onClick = onJump)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    } else {
                        Modifier
                    },
                ),
    ) {
        Text(
            text = text.ifBlank { "♪" },
            color = if (current || focused) BenflixColors.Text else BenflixColors.Muted,
            fontSize = if (current) (fontSize * 1.15f).sp else fontSize.sp,
            lineHeight = (fontSize * 1.3f).sp,
            fontWeight = if (current) FontWeight.ExtraBold else FontWeight.SemiBold,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (focused && isTv) {
            Text(
                "OK: play from here",
                color = BenflixColors.Soft,
                fontSize = BenflixType.tag,
                maxLines = 1,
                modifier =
                    Modifier
                        .padding(start = 10.dp)
                        .background(BenflixColors.Raised, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

/** Width of the song side next to the panel */
fun songSideWidth(total: Dp): Dp = (total * 0.42f).coerceAtLeast(300.dp)

/** Keeps the playing-song list cheap to read */
@Composable
fun rememberComing(
    viewModel: MusicNowPlayingViewModel,
    version: Long,
    index: Int,
    count: Int = 12,
): List<Pair<Int, AudioItem>> =
    remember(version, index) {
        val p = viewModel.player
        val from = p.currentMediaItemIndex + 1
        (from until minOf(p.mediaItemCount, from + count)).mapNotNull { i ->
            (p.getMediaItemAt(i).localConfiguration?.tag as? AudioItem)?.let { i to it }
        }
    }

/** The player background: dark, with a soft glow from the cover's colours (Ben's pick) */
fun Modifier.coverGlow(tint: Color?): Modifier =
    this.background(BenflixColors.Tray).then(
        if (tint == null) {
            Modifier
        } else {
            Modifier.background(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    0f to tint.copy(alpha = .38f),
                    1f to Color.Transparent,
                    center =
                        androidx.compose.ui.geometry
                            .Offset(0f, 0f),
                    radius = 1400f,
                ),
            )
        },
    )
