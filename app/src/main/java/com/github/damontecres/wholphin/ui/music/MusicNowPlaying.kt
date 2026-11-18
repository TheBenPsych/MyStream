package com.github.damontecres.wholphin.ui.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.focus.onFocusChanged
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
import org.jellyfin.sdk.api.client.extensions.instantMixApi
import org.jellyfin.sdk.api.client.extensions.lyricsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.LyricDto
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

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
            similarFor = audio.id
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

        /** Music player 2026-11-17: the panel's tab (Lyrics, Up next, About, Songs like this) */
        val tab = MutableStateFlow(PlayerTab.LYRICS)

        /** The panel moved to About by itself because the song has no words (it goes back to Lyrics when a song has them) */
        var tabMovedForNoWords = false

        /** Which song the Songs like this tab looked up */
        private var similarFor: UUID? = null

        fun showTab(t: PlayerTab) {
            tab.value = t
            tabMovedForNoWords = false
            loadTab(t)
        }

        /** The About and Songs like this tabs look things up the first time they show for a song */
        fun loadTab(t: PlayerTab) {
            val id =
                musicService.state.value.currentAudio
                    ?.id ?: return
            when (t) {
                PlayerTab.ABOUT -> if (_about.value?.songId != id) openAbout()
                PlayerTab.LIKE_THIS -> if (similarFor != id || _similar.value == null) openSimilar()
                else -> Unit
            }
        }

        /** Shuffle on the player: Off, then Shuffle, then Smart shuffle (songs like these from your music join in) */
        val shuffle =
            MutableStateFlow(
                when {
                    musicService.state.value.thenTitle
                        ?.contains("Smart shuffle") == true -> ShuffleMode.SMART

                    musicService.player.shuffleModeEnabled -> ShuffleMode.SHUFFLE

                    else -> ShuffleMode.OFF
                },
            )

        fun cycleShuffle() {
            when (shuffle.value) {
                ShuffleMode.OFF -> {
                    player.shuffleModeEnabled = true
                    shuffle.value = ShuffleMode.SHUFFLE
                    com.github.damontecres.wholphin.ui.components.BenflixNotices
                        .pill("Shuffle on")
                }

                ShuffleMode.SHUFFLE -> {
                    shuffle.value = ShuffleMode.SMART
                    smartShuffle()
                }

                ShuffleMode.SMART -> {
                    player.shuffleModeEnabled = false
                    shuffle.value = ShuffleMode.OFF
                    com.github.damontecres.wholphin.ui.components.BenflixNotices
                        .pill("Shuffle off")
                }
            }
        }

        /** Songs like the playing one, from your music, slipped into what's coming (one after every 3 songs) */
        private fun smartShuffle() {
            val audio = musicService.state.value.currentAudio ?: return
            viewModelScope.launchIO {
                val extra =
                    try {
                        val queued = musicService.state.value.queuedIds
                        api.instantMixApi
                            .getInstantMixFromItem(
                                userId = serverRepository.currentUser?.id,
                                itemId = audio.id,
                                limit = 12,
                                fields = com.github.damontecres.wholphin.ui.SongListFields,
                            ).content.items
                            .filter { it.type == org.jellyfin.sdk.model.api.BaseItemKind.AUDIO && it.id !in queued && it.id != audio.id }
                            .map { BaseItem(it, false) }
                    } catch (ex: kotlinx.coroutines.CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Smart shuffle on the player")
                        emptyList()
                    }
                var at = onMain { player.currentMediaItemIndex } + 1
                extra.forEach { song ->
                    at += 3
                    val count = onMain { player.mediaItemCount }
                    musicService.addToQueue(song, minOf(at, count))
                    at++
                }
                onMain {
                    player.shuffleModeEnabled = true
                    com.github.damontecres.wholphin.ui.components.BenflixNotices.pill(
                        if (extra.isEmpty()) "Shuffle on (no songs like these found yet)" else "Smart shuffle: songs like these join in",
                    )
                }
            }
        }

        /** Play the song at [index] in the queue (Up next) */
        fun playAt(index: Int) {
            viewModelScope.launch { musicService.playIndex(index) }
        }

        /** Jump the song to [ms] (a lyric line, or the progress line) */
        fun seekTo(ms: Long) {
            player.seekTo(ms.coerceAtLeast(0))
        }

        private val _lyrics = MutableStateFlow<Pair<UUID, LyricDto>?>(null)
        val lyrics: StateFlow<Pair<UUID, LyricDto>?> = _lyrics
        private val cache = mutableMapOf<UUID, LyricDto?>()

        /** The song whose words have been looked up (found or not), so "no words" can show instead of waiting */
        val lyricsChecked = MutableStateFlow<UUID?>(null)

        /** The words for [audio], if Jellyfin has them (Tower fetches missing ones every night) */
        fun loadLyrics(audio: AudioItem?) {
            if (audio == null) return
            if (_lyrics.value?.first == audio.id) return
            if (cache.containsKey(audio.id)) {
                _lyrics.value = cache[audio.id]?.let { audio.id to it }
                lyricsChecked.value = audio.id
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
                    lyricsChecked.value = audio.id
                }
            }
        }

        fun playPause() {
            if (player.isPlaying) player.pause() else player.play()
        }

        fun previous() = player.seekToPrevious()

        fun next() = musicService.skipToNext()

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
 * Benflix music: Now playing (music player redesign 2026-11-17, Ben's final sheet). The song side centred
 * (cover, name, the logo-square towers inside the progress line, Shuffle / Previous / Play / Next, then Like,
 * Play on, Stop and More), and one full-height panel with Lyrics, Up next, About and Songs like this, with the
 * next three songs along its bottom. Songs with no words open the panel on About. OK on a lyric line plays from
 * there. TV remote: starts on Play; Left/Right only move between buttons (Ben); on the progress line they jump
 * 10 seconds. A narrow window puts the panel under the song; phones held upright get [PhoneNowPlaying].
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
    val checked by viewModel.lyricsChecked.collectAsState()
    // The words were looked up and there are none (Ben: the panel opens on About instead of an empty space)
    val noLyrics = lyrics == null && checked == audio?.id && audio != null
    val tab by viewModel.tab.collectAsState()
    LaunchedEffect(audio?.id, noLyrics, lyrics != null) {
        if (noLyrics && viewModel.tab.value == PlayerTab.LYRICS) {
            viewModel.tab.value = PlayerTab.ABOUT
            viewModel.tabMovedForNoWords = true
        } else if (lyrics != null && viewModel.tabMovedForNoWords && viewModel.tab.value == PlayerTab.ABOUT) {
            viewModel.tab.value = PlayerTab.LYRICS
            viewModel.tabMovedForNoWords = false
        }
        viewModel.loadTab(viewModel.tab.value)
    }
    val shuffle by viewModel.shuffle.collectAsState()
    val coming = rememberComing(viewModel, music.queueVersion, music.currentIndex)
    val menuSong by viewModel.menuSong.collectAsState()
    menuSong?.let { song ->
        SongMenu(
            song = song,
            liked = music.liked,
            onDismiss = viewModel::closeMenu,
            // A song deleted from here (admins) is gone from Tower, so the next one plays
            onDeleted = { viewModel.next() },
            // Ben 2026-11-17: More has Songs like this too (it opens that tab)
            onSongsLikeThis =
                if (viewModel.canAsk && !easy) {
                    {
                        viewModel.closeMenu()
                        viewModel.showTab(PlayerTab.LIKE_THIS)
                    }
                } else {
                    null
                },
        )
    }
    val about by viewModel.about.collectAsState()
    val playOn by viewModel.playOnOpen.collectAsState()
    if (playOn) MusicPlayOnDialog(viewModel, onDismiss = { viewModel.playOnOpen.value = false })
    val similar by viewModel.similar.collectAsState()
    val similarPicked by viewModel.similarPicked.collectAsState()
    val similarNote by viewModel.similarNote.collectAsState()
    // A song picked in Songs like this: the usual Ask for it panel (this song, or the whole album)
    if (similarPicked != null) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.github.damontecres.wholphin.ui.discover.LocalSongPreview provides viewModel.preview,
        ) {
            SimilarSongsPanel(
                state = similar ?: SimilarState.Loading,
                admin = viewModel.isAdmin,
                picked = similarPicked,
                onPick = { viewModel.similarPicked.value = it },
                onAsk = viewModel::askSimilar,
                onDismiss = { viewModel.similarPicked.value = null },
                note = similarNote,
            )
        }
    }
    val tabs =
        remember(viewModel.canAsk, easy) {
            PlayerTab.entries.filter { it != PlayerTab.LIKE_THIS || (viewModel.canAsk && !easy) }
        }
    val tint = rememberCoverTint(audio?.imageUrl)

    // The panel's middle, per tab
    val panelBody: @Composable androidx.compose.foundation.layout.ColumnScope.(
        fontSize: Float,
        center: Boolean,
    ) -> Unit = { fontSize, center ->
        when (tab) {
            PlayerTab.LYRICS -> {
                if (lyrics != null) {
                    LyricsColumn(
                        lyrics,
                        lyricIndex,
                        Modifier.fillMaxSize(),
                        fontSize = fontSize,
                        center = center,
                        onJump = if (lyrics.isTimed()) viewModel::seekTo else null,
                    )
                } else if (noLyrics) {
                    PanelNote("No words for this song yet", "Glowfin looks for them every night, so they may turn up tomorrow.")
                } else {
                    Text("Looking for the words…", color = BenflixColors.Soft, fontSize = BenflixType.body)
                }
            }

            PlayerTab.UP_NEXT -> {
                UpNextTab(coming, onPlay = viewModel::playAt, onQueue = viewModel::openUpNext)
            }

            PlayerTab.ABOUT -> {
                AboutTab(about?.takeIf { it.songId == audio?.id }, onArtist = viewModel::goToArtist)
            }

            PlayerTab.LIKE_THIS -> {
                androidx.compose.runtime.CompositionLocalProvider(
                    com.github.damontecres.wholphin.ui.discover.LocalSongPreview provides viewModel.preview,
                ) {
                    LikeThisTab(similar, similarNote, onPick = { viewModel.similarPicked.value = it })
                }
            }
        }
    }
    val panelTabs: @Composable () -> Unit = {
        PlayerTabs(tab, tabs, noLyrics, isTv, easy, onTab = viewModel::showTab)
    }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .coverGlow(tint),
    ) {
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        // Ben 2026-11-17 (f-narrow): the panel only sits beside the song when there's room for both halves
        if (wide && boxWidth >= 720.dp) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp),
            ) {
                // The song side, centred top to bottom and left to right
                androidx.compose.foundation.layout.Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier =
                        Modifier
                            .width(songSideWidth(boxWidth - 80.dp))
                            .fillMaxHeight()
                            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                ) {
                    val coverSize = minOf(boxHeight * (if (easy) 0.3f else 0.36f), songSideWidth(boxWidth - 80.dp) * 0.62f)
                    CoverImage(audio?.imageUrl, Modifier.size(coverSize), shape = RoundedCornerShape(14.dp))
                    Spacer(Modifier.height(16.dp))
                    SongName(audio, music.thenTitle, easy, center = true)
                    Spacer(Modifier.height(8.dp))
                    PlayerProgress(position, duration, playing, barsOn, isTv, onSeek = viewModel::seekTo, towers = 56)
                    Spacer(Modifier.height(8.dp))
                    PlayerControls(viewModel, playing, shuffle, easy, playFocus, onPlayFocus = { playFocused.value = it })
                    Spacer(Modifier.height(14.dp))
                    PlayerRoundButtons(viewModel, music.liked, easy)
                }
                PlayerPanelFrame(
                    tabs = panelTabs,
                    upNext = { PlayerUpNextStrip(coming, onPlay = viewModel::playAt) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) { panelBody(if (easy) 30f else 26f, false) }
            }
        } else if (wide) {
            // A narrow window (or a small phone on its side): the panel goes under the song instead of squeezing
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                CoverImage(audio?.imageUrl, Modifier.size(minOf(240.dp, boxWidth * 0.5f)), shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.height(14.dp))
                SongName(audio, music.thenTitle, easy, center = true)
                Spacer(Modifier.height(8.dp))
                PlayerProgress(position, duration, playing, barsOn, isTv, onSeek = viewModel::seekTo, towers = 40)
                Spacer(Modifier.height(8.dp))
                PlayerControls(viewModel, playing, shuffle, easy, playFocus, onPlayFocus = { playFocused.value = it })
                Spacer(Modifier.height(12.dp))
                PlayerRoundButtons(viewModel, music.liked, easy)
                Spacer(Modifier.height(16.dp))
                PlayerPanelFrame(
                    tabs = panelTabs,
                    upNext = { PlayerUpNextStrip(coming, onPlay = viewModel::playAt) },
                    modifier = Modifier.fillMaxWidth().height(maxOf(360.dp, boxHeight * 0.7f)),
                ) { panelBody(if (easy) 24f else 20f, false) }
            }
        } else {
            PhoneNowPlaying(
                viewModel = viewModel,
                audio = audio,
                from = music.thenTitle,
                position = position,
                duration = duration,
                playing = playing,
                liked = music.liked,
                easy = easy,
                barsOn = barsOn,
                shuffle = shuffle,
                boxHeight = boxHeight,
                boxWidth = boxWidth,
                panelTabs = panelTabs,
                panelBody = { panelBody(if (easy) 20f else 17f, true) },
            )
        }
    }
}

/** "NOW PLAYING · FROM ...", the song's name, and who it's by */
@Composable
private fun SongName(
    audio: AudioItem?,
    from: String?,
    easy: Boolean,
    center: Boolean,
) {
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        MusicKicker(if (from.isNullOrBlank()) "Now playing" else "Now playing · from $from", color = BenflixColors.Music)
        Text(
            text = audio?.title.orEmpty(),
            color = BenflixColors.Text,
            fontSize = if (easy) 40.sp else 34.sp,
            lineHeight = if (easy) 44.sp else 38.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        val by = listOfNotNull(audio?.artistNames, audio?.albumTitle).filter { it.isNotBlank() }.joinToString(" · ")
        if (by.isNotBlank()) {
            Text(
                text = by,
                color = BenflixColors.Soft,
                fontSize = BenflixType.lead,
                fontWeight = FontWeight.SemiBold,
                textAlign = if (center) TextAlign.Center else TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
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

/** The song's words: the one being sung in white and a little bigger, the rest grey. [onJump]: OK/tap on a line plays from it */
@Composable
internal fun LyricsColumn(
    lyrics: LyricDto,
    index: Int?,
    modifier: Modifier = Modifier,
    fontSize: Float,
    center: Boolean = false,
    onJump: ((Long) -> Unit)? = null,
) {
    val lines = lyrics.lyrics
    val at = (index ?: 0).coerceIn(0, (lines.size - 1).coerceAtLeast(0))
    val isTv = rememberIsTv()
    // While the remote is on a line, the words stop gliding so you can pick one
    var picking by remember { mutableStateOf(false) }
    // Every line shows in full (long lines wrap). The list glides so the line being sung stays second from the
    // top, with the next lines ahead of it; whatever doesn't fit is simply further down, not cut off.
    val listState =
        androidx.compose.foundation.lazy
            .rememberLazyListState()
    LaunchedEffect(at, lines.size, picking) {
        if (lines.isNotEmpty() && !picking) listState.animateScrollToItem((at - 1).coerceAtLeast(0))
    }
    androidx.compose.foundation.lazy.LazyColumn(
        state = listState,
        userScrollEnabled = onJump != null && !isTv,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 200.dp),
        modifier =
            modifier
                .padding(top = 8.dp)
                .clipToBounds()
                .onFocusChanged { picking = it.hasFocus },
    ) {
        items(lines.size) { i ->
            LyricLine(
                text = lines[i].text,
                current = i == at,
                fontSize = fontSize,
                center = center,
                onJump = onJump?.let { jump -> lyrics.lineStartMs(i)?.let { ms -> { jump(ms) } } },
                isTv = isTv,
            )
        }
    }
}
