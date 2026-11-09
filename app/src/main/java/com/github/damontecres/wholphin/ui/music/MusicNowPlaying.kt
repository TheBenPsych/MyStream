package com.github.damontecres.wholphin.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.AudioItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.NowPlayingStatus
import com.github.damontecres.wholphin.ui.components.BenflixIcon
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.onMain
import com.github.damontecres.wholphin.ui.theme.BenflixColors
import com.github.damontecres.wholphin.ui.theme.BenflixType
import com.github.damontecres.wholphin.ui.touch.rememberIsTv
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.lyricsApi
import org.jellyfin.sdk.model.api.LyricDto
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class MusicNowPlayingViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        val musicService: MusicService,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        val player get() = musicService.player
        private val _lyrics = MutableStateFlow<Pair<UUID, LyricDto>?>(null)
        val lyrics: StateFlow<Pair<UUID, LyricDto>?> = _lyrics
        private val cache = mutableMapOf<UUID, LyricDto?>()

        /** The words for [audio], if Jellyfin has them (Tower fetches missing ones every night) */
        fun loadLyrics(audio: AudioItem?) {
            if (audio == null) return
            if (_lyrics.value?.first == audio.id) return
            if (cache.containsKey(audio.id)) {
                _lyrics.value = cache[audio.id]?.let { audio.id to it }
                return
            }
            _lyrics.value = null
            viewModelScope.launchIO {
                val dto =
                    try {
                        api.lyricsApi
                            .getLyrics(audio.id)
                            .content
                            .takeIf { it.lyrics.any { line -> line.text.isNotBlank() } }
                    } catch (ex: Exception) {
                        // Most songs without words just answer "not found"
                        Timber.v(ex, "No lyrics for %s", audio.id)
                        null
                    }
                cache[audio.id] = dto
                if (musicService.state.value.currentAudio
                        ?.id == audio.id
                ) {
                    _lyrics.value = dto?.let { audio.id to it }
                }
            }
        }

        fun playPause() {
            if (player.isPlaying) player.pause() else player.play()
        }

        fun previous() = player.seekToPrevious()

        fun next() = player.seekToNext()

        fun seekBy(ms: Long) {
            val d = player.duration.takeIf { it > 0 } ?: return
            player.seekTo((player.currentPosition + ms).coerceIn(0, d))
        }

        fun like() {
            val audio = musicService.state.value.currentAudio ?: return
            viewModelScope.launch { musicService.setLiked(audio.id, !musicService.state.value.liked) }
        }

        /** Stop was pressed here: this page leaves by itself, so the "nothing playing" check mustn't leave too */
        @Volatile
        var stopping = false

        fun stop() {
            stopping = true
            viewModelScope.launch {
                musicService.stop()
                onMain { navigationManager.goBack() }
            }
        }

        fun openUpNext() = navigationManager.navigateTo(Destination.MusicUpNext)
    }

/**
 * Benflix music: Now playing (approved np-cover + w-column + v-blocks). The cover on the left, the name
 * and controls in the middle (always on screen), the song's words in a column on the right (5 lines, the
 * line being sung in white), and the logo-square towers along the bottom. Songs with no words keep the same
 * screen without the column. Up next sits under the controls with a small cover.
 * TV remote: Left/Right on Play = previous/next song, holding them = rewind/fast forward.
 */
@Composable
fun MusicNowPlayingPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: MusicNowPlayingViewModel = hiltViewModel(),
) {
    val music by viewModel.musicService.state.collectAsState()
    val audio =
        music.currentAudio ?: (
            viewModel.player.currentMediaItem
                ?.localConfiguration
                ?.tag as? AudioItem
        )
    LaunchedEffect(audio?.id) { viewModel.loadLyrics(audio) }
    val lyricsPair by viewModel.lyrics.collectAsState()
    val lyrics = lyricsPair?.takeIf { it.first == audio?.id }?.second
    val easy = preferences.appPreferences.benflixPreferences.easyRead
    val barsOn = !preferences.appPreferences.benflixPreferences.musicBarsOff
    val isTv = rememberIsTv()
    val config = LocalConfiguration.current
    val wide = isTv || config.screenWidthDp > config.screenHeightDp
    val playing = music.status == NowPlayingStatus.PLAYING
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            position = viewModel.player.currentPosition
            duration = viewModel.player.duration.coerceAtLeast(0L)
            delay(250)
        }
    }
    // Nothing playing any more (stopped from the lock screen, say): leave
    LaunchedEffect(music.status, music.queueSize) {
        if (music.status == NowPlayingStatus.IDLE && music.queueSize == 0) {
            delay(400)
            if (viewModel.musicService.state.value.queueSize == 0 && !viewModel.stopping) viewModel.navigationManager.goBack()
        }
    }
    val next = remember(music.queueVersion, music.currentIndex) { nextSong(viewModel) }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        playFocus.tryRequestFocus()
    }
    val lyricIndex = lyrics?.let { currentLine(it, position, duration) }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(BenflixColors.Tray),
    ) {
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        val towersHeight = boxHeight * (if (wide) 0.34f else 0.13f)
        if (barsOn) {
            MusicTowers(
                playing = playing,
                towers = if (wide) 30 else 16,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(towersHeight)
                        .padding(horizontal = if (wide) 40.dp else 16.dp),
            )
            // The words and controls stay easy to read over tall towers
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(towersHeight * 1.25f)
                    .background(Brush.verticalGradient(0f to BenflixColors.Tray, 0.25f to BenflixColors.Tray.copy(alpha = .0f))),
            )
        }
        // Room kept at the bottom for the bars (none when they're turned off)
        val reserved = if (barsOn) towersHeight else 0.dp
        if (wide) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, end = 48.dp, top = 40.dp)
                        .height(boxHeight - reserved - 40.dp),
            ) {
                val coverSize = (boxWidth * 0.22f).coerceAtMost(boxHeight - reserved - 60.dp)
                CoverImage(audio?.imageUrl, Modifier.size(coverSize))
                // Scrolls (focus brings the button into view) so Like / Up next / Stop are never squashed away
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                ) {
                    SongWords(audio, music.thenTitle, easy, big = true)
                    Spacer(Modifier.height(14.dp))
                    ProgressLine(position, duration)
                    Spacer(Modifier.height(16.dp))
                    Controls(viewModel, playing, music.liked, easy, isTv, playFocus)
                    Spacer(Modifier.height(14.dp))
                    UpNextLine(next)
                }
                if (lyrics != null) {
                    LyricsColumn(lyrics, lyricIndex, Modifier.width(boxWidth * 0.27f).fillMaxHeight(), fontSize = if (easy) 24f else 20f)
                }
            }
        } else {
            PhoneNowPlaying(
                viewModel = viewModel,
                audio = audio,
                from = music.thenTitle,
                lyrics = lyrics,
                lyricIndex = lyricIndex,
                next = next,
                position = position,
                duration = duration,
                playing = playing,
                liked = music.liked,
                easy = easy,
                towersHeight = towersHeight,
                boxHeight = boxHeight,
                boxWidth = boxWidth,
            )
        }
    }
}

private fun nextSong(viewModel: MusicNowPlayingViewModel): AudioItem? {
    val p = viewModel.player
    val i = p.nextMediaItemIndex
    if (i < 0 || i >= p.mediaItemCount) return null
    return p.getMediaItemAt(i).localConfiguration?.tag as? AudioItem
}

/** Which line is being sung: by its time, or (no times) spread evenly over the song (Ben: a slow scroll) */
private fun currentLine(
    lyrics: LyricDto,
    position: Long,
    duration: Long,
): Int {
    val lines = lyrics.lyrics
    if (lines.isEmpty()) return 0
    val timed = lines.any { it.start != null && it.start!! > 0 }
    return if (timed) {
        val offset =
            lyrics.metadata.offset
                ?.ticks
                ?.inWholeMilliseconds ?: 0L
        val at = position + offset
        lines.indexOfLast { (it.start?.ticks?.inWholeMilliseconds ?: Long.MAX_VALUE) <= at }.coerceAtLeast(0)
    } else {
        if (duration <= 0) 0 else ((position.toDouble() / duration) * lines.size).toInt().coerceIn(0, lines.size - 1)
    }
}

@Composable
private fun SongWords(
    audio: AudioItem?,
    from: String?,
    easy: Boolean,
    big: Boolean,
) {
    MusicKicker(if (from.isNullOrBlank()) "Now playing" else "Now playing · from $from")
    Text(
        text = audio?.title.orEmpty(),
        color = BenflixColors.Text,
        fontSize = if (big) (if (easy) 46.sp else 40.sp) else 26.sp,
        lineHeight = if (big) 46.sp else 30.sp,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 6.dp),
    )
    val line = listOfNotNull(audio?.artistNames, audio?.albumTitle).filter { it.isNotBlank() }.joinToString(" · ")
    Text(
        text = line,
        color = BenflixColors.Soft,
        fontSize = if (big) BenflixType.rowName else BenflixType.body,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ProgressLine(
    position: Long,
    duration: Long,
) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(BenflixColors.Line, RoundedCornerShape(3.dp)),
        ) {
            val f = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
            Box(
                Modifier
                    .fillMaxWidth(f)
                    .fillMaxHeight()
                    .background(BenflixColors.Music, RoundedCornerShape(3.dp)),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(songLength(position.milliseconds), color = BenflixColors.Muted, fontSize = BenflixType.small)
            Spacer(Modifier.weight(1f))
            Text(songLength(duration.milliseconds), color = BenflixColors.Muted, fontSize = BenflixType.small)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Controls(
    viewModel: MusicNowPlayingViewModel,
    playing: Boolean,
    liked: Boolean,
    easy: Boolean,
    isTv: Boolean,
    playFocus: FocusRequester,
) {
    // True while Left or Right is held down (rewinding or fast forwarding)
    var held by remember { mutableStateOf(false) }

    // Only a press that started on Play counts (moving onto Play from Previous/Next must not change the song)
    var downOnPlay by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        // TV: Left/Right on Play already change the song, so these two are just there to see (and tap on phones)
        val tvSkip = Modifier.focusProperties { canFocus = !isTv }
        MusicButton(if (easy) "Previous" else null, BenflixIcon.PREVIOUS, onClick = viewModel::previous, big = easy, modifier = tvSkip)
        MusicButton(
            label = if (easy) (if (playing) "Pause" else "Play") else null,
            icon = if (playing) BenflixIcon.PAUSE else null,
            filledPlay = !playing,
            primary = true,
            big = true,
            onClick = viewModel::playPause,
            modifier =
                Modifier
                    .focusRequester(playFocus)
                    .onPreviewKeyEvent { e ->
                        // Ben: on the TV remote, Left/Right = previous/next song; holding = rewind/fast forward
                        if (!isTv) return@onPreviewKeyEvent false
                        val left = e.key == Key.DirectionLeft
                        val right = e.key == Key.DirectionRight
                        if (!left && !right) return@onPreviewKeyEvent false
                        if (e.type == KeyEventType.KeyDown) {
                            if (e.nativeKeyEvent.repeatCount == 0) {
                                downOnPlay = true
                            } else if (downOnPlay) {
                                held = true
                                viewModel.seekBy(if (left) -SEEK_STEP_MS else SEEK_STEP_MS)
                            }
                        } else if (e.type == KeyEventType.KeyUp) {
                            if (downOnPlay && !held) {
                                if (left) viewModel.previous() else viewModel.next()
                            }
                            held = false
                            downOnPlay = false
                        }
                        true
                    },
        )
        MusicButton(if (easy) "Next song" else null, BenflixIcon.NEXT, onClick = viewModel::next, big = easy, modifier = tvSkip)
        if (!isTv) {
            Spacer(Modifier.width(4.dp))
            ExtraButtons(viewModel, liked, easy)
        }
    }
    if (isTv) {
        // TV: Left/Right on Play change the song, so the heart, Up next and Stop sit one row down
        // Wraps onto a second line when the middle is narrow, so no button is ever cut off
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            ExtraButtons(viewModel, liked, easy, labels = true)
        }
    }
}

@Composable
private fun ExtraButtons(
    viewModel: MusicNowPlayingViewModel,
    liked: Boolean,
    easy: Boolean,
    labels: Boolean = easy,
) {
    MusicButton(
        if (labels) (if (liked) "Liked" else "Like") else null,
        BenflixIcon.HEART,
        filledHeart = liked,
        onClick = viewModel::like,
        big = easy,
    )
    MusicButton(if (labels) "Up next" else null, BenflixIcon.LIST, onClick = viewModel::openUpNext, big = easy)
    MusicButton(if (labels) "Stop" else null, BenflixIcon.STOP, onClick = viewModel::stop, big = easy)
}

private const val SEEK_STEP_MS = 5_000L

/** "UP NEXT" with the next song's small cover, name and artist (always there) */
@Composable
internal fun UpNextLine(next: AudioItem?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MusicKicker("Up next", color = BenflixColors.Muted)
        if (next != null) {
            CoverImage(next.imageUrl, Modifier.size(28.dp), shape = RoundedCornerShape(5.dp))
            Text(
                text = listOfNotNull(next.title, next.artistNames).joinToString(" · "),
                color = BenflixColors.Soft,
                fontSize = BenflixType.small,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text("A mix like this one", color = BenflixColors.Muted, fontSize = BenflixType.small)
        }
    }
}

/** The song's words: 5 lines, the one being sung in white and a little bigger, the rest grey */
@Composable
internal fun LyricsColumn(
    lyrics: LyricDto,
    index: Int?,
    modifier: Modifier = Modifier,
    fontSize: Float,
    center: Boolean = false,
) {
    val lines = lyrics.lyrics
    val at = (index ?: 0).coerceIn(0, (lines.size - 1).coerceAtLeast(0))
    // Every line shows in full (long lines wrap). The list glides so the line being sung stays second from the
    // top, with the next lines ahead of it; whatever doesn't fit is simply further down, not cut off.
    val listState =
        androidx.compose.foundation.lazy
            .rememberLazyListState()
    LaunchedEffect(at, lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem((at - 1).coerceAtLeast(0))
    }
    androidx.compose.foundation.lazy.LazyColumn(
        state = listState,
        userScrollEnabled = false,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 200.dp),
        modifier = modifier.padding(top = 8.dp).clipToBounds(),
    ) {
        items(lines.size) { i ->
            val current = i == at
            Text(
                text = lines[i].text.ifBlank { "♪" },
                color = if (current) BenflixColors.Text else BenflixColors.Muted,
                fontSize = if (current) (fontSize * 1.15f).sp else fontSize.sp,
                lineHeight = (fontSize * 1.3f).sp,
                fontWeight = if (current) FontWeight.ExtraBold else FontWeight.SemiBold,
                textAlign = if (center) TextAlign.Center else TextAlign.Start,
                modifier = if (center) Modifier.fillMaxWidth() else Modifier,
            )
        }
    }
}
