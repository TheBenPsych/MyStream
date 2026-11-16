package com.github.damontecres.wholphin.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
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
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.GuestMode
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.MusicSong
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
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.lyricsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
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
        private val music: com.github.damontecres.wholphin.services.BenflixMusic,
        private val serverRepository: com.github.damontecres.wholphin.data.ServerRepository,
        /** 30-second tastes in Similar songs (Ben 2026-11-16); the playing song pauses while one plays */
        val preview: com.github.damontecres.wholphin.services.SongPreview,
        arrivals: com.github.damontecres.wholphin.services.MusicArrivals,
    ) : ViewModel() {
        init {
            // Music redesign 3: a song you asked for just came in? A corner card offers to play it next (once)
            arrivals.check()
        }

        /** Ben, or a grown-up family profile (their asks wait for Ben's yes). Never Kids or Guest. */
        val canAsk: Boolean get() = !KidsMode.isActive && !GuestMode.isActive

        val isAdmin: Boolean get() = serverRepository.currentUserDto?.policy?.isAdministrator == true

        /** Similar songs (null = the panel is closed) */
        private val _similar = MutableStateFlow<SimilarState?>(null)
        val similar: StateFlow<SimilarState?> = _similar
        val similarPicked = MutableStateFlow<MusicSong?>(null)
        val similarNote = MutableStateFlow<String?>(null)

        fun openSimilar() {
            val audio = musicService.state.value.currentAudio ?: return
            val artist =
                audio.artistNames
                    .orEmpty()
                    .substringBefore(",")
                    .trim()
            val title = audio.title.orEmpty()
            similarNote.value = null
            similarPicked.value = null
            _similar.value = SimilarState.Loading
            viewModelScope.launchIO {
                _similar.value =
                    try {
                        SimilarState.Songs(title, music.similar(artist, title))
                    } catch (ex: kotlinx.coroutines.CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Similar songs")
                        SimilarState.Problem(ex.message ?: "Couldn't reach Tower.")
                    }
            }
        }

        fun closeSimilar() {
            preview.stop()
            _similar.value = null
            similarPicked.value = null
        }

        /** [kind] is "song" or "album" */
        fun askSimilar(
            kind: String,
            id: Long,
            name: String,
        ) {
            similarPicked.value = null
            preview.stop()
            viewModelScope.launchIO {
                try {
                    music.ask(kind, id)
                    similarNote.value = if (isAdmin) "Asked for $name." else "Asked Ben for $name. He says yes before it downloads."
                    val now = _similar.value
                    if (now is SimilarState.Songs) {
                        _similar.value =
                            now.copy(
                                songs =
                                    now.songs.map { s ->
                                        if (s.id == id ||
                                            (kind == "album" && s.albumId == id)
                                        ) {
                                            s.copy(asked = "Asked for")
                                        } else {
                                            s
                                        }
                                    },
                            )
                    }
                } catch (ex: kotlinx.coroutines.CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Ask for a similar song")
                    similarNote.value = ex.message ?: "Couldn't ask Tower for it."
                }
            }
        }

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

        /** The playing song as a full item, for its More menu (null while the menu is closed) */
        private val _menuSong = MutableStateFlow<BaseItem?>(null)
        val menuSong: StateFlow<BaseItem?> = _menuSong

        fun openMenu() {
            val audio = musicService.state.value.currentAudio ?: return
            viewModelScope.launchIO {
                val item =
                    try {
                        BaseItem(api.userLibraryApi.getItem(audio.id).content, false)
                    } catch (ex: Exception) {
                        Timber.w(ex, "Song for the More menu")
                        null
                    }
                _menuSong.value = item
            }
        }

        fun closeMenu() {
            _menuSong.value = null
        }

        /** Music redesign 3: the Play on picker is open */
        val playOnOpen = MutableStateFlow(false)

        /** Music redesign 3: the About cards (null = closed) */
        private val _about = MutableStateFlow<AboutSong?>(null)
        val about: StateFlow<AboutSong?> = _about

        fun openAbout() {
            val audio = musicService.state.value.currentAudio ?: return
            _about.value = AboutSong(audio.id, loading = true)
            viewModelScope.launchIO {
                _about.value =
                    try {
                        val song = api.userLibraryApi.getItem(audio.id).content
                        val artistRef = song.albumArtists?.firstOrNull() ?: song.artistItems?.firstOrNull()
                        val artist =
                            artistRef?.let {
                                try {
                                    api.userLibraryApi.getItem(it.id).content
                                } catch (ex: Exception) {
                                    Timber.d(ex, "About: the artist")
                                    null
                                }
                            }
                        val credits =
                            buildList {
                                song.artists?.takeIf { it.isNotEmpty() }?.let { add("Performed by" to it.joinToString(", ")) }
                                song.people
                                    ?.groupBy { it.type }
                                    ?.forEach { (type, people) ->
                                        val role =
                                            when (type) {
                                                org.jellyfin.sdk.model.api.PersonKind.COMPOSER -> "Written by"
                                                org.jellyfin.sdk.model.api.PersonKind.LYRICIST -> "Words by"
                                                org.jellyfin.sdk.model.api.PersonKind.PRODUCER -> "Produced by"
                                                org.jellyfin.sdk.model.api.PersonKind.ARRANGER -> "Arranged by"
                                                org.jellyfin.sdk.model.api.PersonKind.CONDUCTOR -> "Conducted by"
                                                else -> null
                                            }
                                        if (role != null) add(role to people.mapNotNull { it.name }.distinct().joinToString(", "))
                                    }
                                song.album?.let { add("Album" to listOfNotNull(it, song.productionYear?.toString()).joinToString(" · ")) }
                                song.genres?.takeIf { it.isNotEmpty() }?.let { add("Style" to it.take(3).joinToString(", ")) }
                            }
                        AboutSong(
                            songId = audio.id,
                            artistId = artistRef?.id,
                            artistName = artistRef?.name ?: audio.artistNames,
                            artistBio = artist?.overview?.trim()?.ifBlank { null },
                            artistImage =
                                artistRef?.id?.let {
                                    runCatching {
                                        api.imageApi.getItemImageUrl(
                                            itemId = it,
                                            imageType = org.jellyfin.sdk.model.api.ImageType.PRIMARY,
                                            maxHeight = 400,
                                        )
                                    }.getOrNull()
                                },
                            credits = credits,
                        )
                    } catch (ex: kotlinx.coroutines.CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "About this song")
                        AboutSong(audio.id, artistName = audio.artistNames)
                    }
            }
        }

        fun closeAbout() {
            _about.value = null
        }

        fun goToArtist(id: UUID) {
            _about.value = null
            navigationManager.navigateTo(Destination.MediaItem(id, org.jellyfin.sdk.model.api.BaseItemKind.MUSIC_ARTIST))
        }

        /** The songs from the playing one to the end of the queue (for Play on) */
        fun queueFromHere(): List<UUID> {
            val p = player
            val from = p.currentMediaItemIndex.coerceAtLeast(0)
            return (from until p.mediaItemCount)
                .mapNotNull { (p.getMediaItemAt(it).localConfiguration?.tag as? AudioItem)?.id }
                .take(100)
        }
    }

/** What the About cards show for the playing song */
data class AboutSong(
    val songId: UUID,
    val loading: Boolean = false,
    val artistId: UUID? = null,
    val artistName: String? = null,
    val artistBio: String? = null,
    val artistImage: String? = null,
    val credits: List<Pair<String, String>> = emptyList(),
)

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
            // A song the server converts on the fly may not know its length yet: use the library's length
            duration =
                viewModel.player.duration.takeIf { it > 0 }
                    ?: (music.currentAudio ?: audio)?.runtime?.inWholeMilliseconds ?: 0L
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
    // Ben 2026-11-15: the first open after an update can be slow to draw, and one try at 60ms missed, so nothing
    // had focus until Down was pressed. Keep trying for a few seconds until Play really has it.
    val playFocused = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!wide) return@LaunchedEffect
        repeat(30) {
            delay(if (it == 0) 60L else 100L)
            if (playFocused.value) return@LaunchedEffect
            playFocus.tryRequestFocus()
        }
    }
    val lyricIndex = lyrics?.let { currentLine(it, position, duration) }
    val menuSong by viewModel.menuSong.collectAsState()
    menuSong?.let { song ->
        SongMenu(song = song, liked = music.liked, onDismiss = viewModel::closeMenu)
    }
    val about by viewModel.about.collectAsState()
    about?.let { AboutPanel(it, viewModel, next = remember(music.queueVersion, music.currentIndex) { comingUp(viewModel) }) }
    val playOn by viewModel.playOnOpen.collectAsState()
    if (playOn) MusicPlayOnDialog(viewModel, onDismiss = { viewModel.playOnOpen.value = false })
    val similar by viewModel.similar.collectAsState()
    val similarPicked by viewModel.similarPicked.collectAsState()
    val similarNote by viewModel.similarNote.collectAsState()
    similar?.let { state ->
        androidx.compose.runtime.CompositionLocalProvider(
            com.github.damontecres.wholphin.ui.discover.LocalSongPreview provides viewModel.preview,
        ) {
            SimilarSongsPanel(
                state = state,
                admin = viewModel.isAdmin,
                picked = similarPicked,
                onPick = { viewModel.similarPicked.value = it },
                onAsk = viewModel::askSimilar,
                onDismiss = viewModel::closeSimilar,
                note = similarNote,
            )
        }
    }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(BenflixColors.Tray),
    ) {
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        val towersHeight = boxHeight * (if (wide) 0.34f else 0.11f)
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
            // Redesign 2 (pl-family): the same frame as the audiobook player
            NowPlayingWideFrame(
                reservedBottom = reserved,
                cover = { size -> CoverImage(audio?.imageUrl, Modifier.size(size)) },
                middle = {
                    SongWords(audio, music.thenTitle, easy, big = true)
                    Spacer(Modifier.height(12.dp))
                    ProgressLine(position, duration)
                    Spacer(Modifier.height(14.dp))
                    Controls(viewModel, playing, music.liked, easy, isTv, playFocus, onPlayFocus = { playFocused.value = it })
                },
                underCover = { UpNextLine(next, stacked = true) },
                side =
                    if (lyrics != null) {
                        { mod -> LyricsColumn(lyrics, lyricIndex, mod, fontSize = if (easy) 24f else 20f) }
                    } else {
                        null
                    },
                modifier = Modifier.fillMaxSize(),
            )
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

/** The next few songs in the queue (the Next in queue card) */
private fun comingUp(viewModel: MusicNowPlayingViewModel): List<AudioItem> {
    val p = viewModel.player
    val from = p.currentMediaItemIndex + 1
    return (from until minOf(p.mediaItemCount, from + 3)).mapNotNull { p.getMediaItemAt(it).localConfiguration?.tag as? AudioItem }
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
    NowPlayingTitle(
        kicker = if (from.isNullOrBlank()) "Now playing" else "Now playing · from $from",
        title = audio?.title.orEmpty(),
        byLine = listOfNotNull(audio?.artistNames, audio?.albumTitle).filter { it.isNotBlank() }.joinToString(" · "),
        color = BenflixColors.Music,
        big = big,
        easy = easy,
    )
}

@Composable
private fun ProgressLine(
    position: Long,
    duration: Long,
) {
    NowPlayingLine(
        progress = if (duration > 0) position.toFloat() / duration else 0f,
        leftText = songLength(position.milliseconds),
        rightText = songLength(duration.milliseconds),
        color = BenflixColors.Music,
    )
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
    onPlayFocus: (Boolean) -> Unit = {},
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
                    .onFocusChanged { if (it.isFocused) onPlayFocus(true) }
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
    }
    if (!isTv) {
        // Phones held sideways: the round buttons on their own line under Play (wrapping when narrow)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            ExtraButtons(viewModel, liked, easy)
        }
    } else if (easy) {
        // Easy read: the buttons keep their names, wrapping onto a second line when the middle is narrow
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            ExtraButtons(viewModel, liked, easy, labels = true)
        }
    } else {
        // TV: Left/Right on Play change the song, so the heart, Up next, Similar songs, Stop and More sit one row
        // down. Ben 2026-11-15: as named pills they wrapped onto two lines and got cut off, so they're round
        // buttons on one line, and the name of the one you're on shows beside them.
        var focusedName by remember { mutableStateOf<String?>(null) }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            ExtraButtons(viewModel, liked, easy, labels = false, onFocusName = { n, f ->
                if (f) {
                    focusedName = n
                } else if (focusedName ==
                    n
                ) {
                    focusedName = null
                }
            })
            Text(
                text = focusedName.orEmpty(),
                color = BenflixColors.Soft,
                fontSize = BenflixType.body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun ExtraButtons(
    viewModel: MusicNowPlayingViewModel,
    liked: Boolean,
    easy: Boolean,
    labels: Boolean = easy,
    onFocusName: ((name: String, focused: Boolean) -> Unit)? = null,
) {
    val named: (String) -> Modifier = { name ->
        if (onFocusName == null) Modifier else Modifier.onFocusChanged { onFocusName(name, it.isFocused) }
    }
    val likeName = if (liked) "Liked" else "Like"
    MusicButton(
        if (labels) likeName else null,
        BenflixIcon.HEART,
        filledHeart = liked,
        onClick = viewModel::like,
        big = easy,
        modifier = named(likeName),
    )
    MusicButton(if (labels) "Up next" else null, BenflixIcon.LIST, onClick = viewModel::openUpNext, big = easy, modifier = named("Up next"))
    // Music redesign 3: About the artist, Credits, Next in queue and Songs like this to ask for, as cards
    MusicButton(if (labels) "About" else null, BenflixIcon.INFO, onClick = viewModel::openAbout, big = easy, modifier = named("About"))
    MusicButton(if (labels) "Play on" else null, BenflixIcon.TV, onClick = {
        viewModel.playOnOpen.value = true
    }, big = easy, modifier = named("Play on"))
    MusicButton(if (labels) "Stop" else null, BenflixIcon.STOP, onClick = viewModel::stop, big = easy, modifier = named("Stop"))
    // Play next, Add to a playlist, Go to album ... and, on Ben's profile, Delete and find again
    MusicButton(if (labels) "More" else null, BenflixIcon.MORE_VERT, onClick = viewModel::openMenu, big = easy, modifier = named("More"))
}

private const val SEEK_STEP_MS = 5_000L

/** "UP NEXT" with the next song's small cover, name and artist (always there) */
@Composable
internal fun UpNextLine(
    next: AudioItem?,
    stacked: Boolean = false,
) {
    if (stacked) {
        // Under the cover: UP NEXT on its own line, then the next song's cover, name and artist
        androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MusicKicker("Up next", color = BenflixColors.Muted)
            if (next != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CoverImage(next.imageUrl, Modifier.size(44.dp), shape = RoundedCornerShape(6.dp))
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(
                            text = next.title.orEmpty(),
                            color = BenflixColors.Text,
                            fontSize = BenflixType.body,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = next.artistNames.orEmpty(),
                            color = BenflixColors.Soft,
                            fontSize = BenflixType.small,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                Text("A mix like this one", color = BenflixColors.Muted, fontSize = BenflixType.small)
            }
        }
        return
    }
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
