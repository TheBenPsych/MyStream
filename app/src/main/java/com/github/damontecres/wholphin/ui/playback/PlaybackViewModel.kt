package com.github.damontecres.wholphin.ui.playback

import android.content.Context
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.unit.Density
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.MediaSession
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ItemPlaybackDao
import com.github.damontecres.wholphin.data.ItemPlaybackRepository
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.data.model.Playlist
import com.github.damontecres.wholphin.data.model.PlaylistItem
import com.github.damontecres.wholphin.data.model.SeriesTrackChoiceType
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.mpv.MpvPlayer
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.preferences.ShowNextUpWhen
import com.github.damontecres.wholphin.preferences.SkipSegmentBehavior
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.AniSkipMatch
import com.github.damontecres.wholphin.services.AniSkipService
import com.github.damontecres.wholphin.services.BenflixBedtime
import com.github.damontecres.wholphin.services.BenflixKidsLog
import com.github.damontecres.wholphin.services.BenflixMobileData
import com.github.damontecres.wholphin.services.BenflixScreenTime
import com.github.damontecres.wholphin.services.DatePlayedService
import com.github.damontecres.wholphin.services.DeviceProfileService
import com.github.damontecres.wholphin.services.DisplayPreferencesService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.FillerService
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PlaybackPrep
import com.github.damontecres.wholphin.services.PlayerFactory
import com.github.damontecres.wholphin.services.PlaylistCreationResult
import com.github.damontecres.wholphin.services.PlaylistCreator
import com.github.damontecres.wholphin.services.RefreshRateService
import com.github.damontecres.wholphin.services.ScreensaverService
import com.github.damontecres.wholphin.services.SmartResume
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.ItemRowFields
import com.github.damontecres.wholphin.ui.formatBitrate
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.onMain
import com.github.damontecres.wholphin.ui.preferences.subtitle.SubtitleSettings.applyToMpv
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.PlaybackItemState
import com.github.damontecres.wholphin.util.TrackActivityPlaybackListener
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.checkForSupport
import com.github.damontecres.wholphin.util.mpv.mpvDeviceProfile
import com.github.damontecres.wholphin.util.profile.Codec
import com.github.damontecres.wholphin.util.subtitleMimeTypes
import com.github.damontecres.wholphin.util.supportItemKinds
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.mediaSegmentsApi
import org.jellyfin.sdk.api.client.extensions.trickplayApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaystateMessage
import org.jellyfin.sdk.model.api.TrickplayInfo
import org.jellyfin.sdk.model.api.VideoRange
import org.jellyfin.sdk.model.api.VideoRangeType
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import org.jellyfin.sdk.model.extensions.inWholeTicks
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * This [ViewModel] is responsible for playing media including moving through playlists (including next up episodes)
 */
@HiltViewModel(assistedFactory = PlaybackViewModel.Factory::class)
@OptIn(markerClass = [UnstableApi::class])
class PlaybackViewModel
    @AssistedInject
    constructor(
        @param:ApplicationContext internal val context: Context,
        internal val api: ApiClient,
        val navigationManager: NavigationManager,
        internal val playlistCreator: PlaylistCreator,
        internal val itemPlaybackDao: ItemPlaybackDao,
        internal val serverRepository: ServerRepository,
        internal val itemPlaybackRepository: ItemPlaybackRepository,
        internal val playerFactory: PlayerFactory,
        internal val datePlayedService: DatePlayedService,
        internal val deviceInfo: DeviceInfo,
        internal val deviceProfileService: DeviceProfileService,
        internal val refreshRateService: RefreshRateService,
        val streamChoiceService: StreamChoiceService,
        internal val userPreferencesService: UserPreferencesService,
        internal val imageUrlService: ImageUrlService,
        internal val screensaverService: ScreensaverService,
        internal val musicService: MusicService,
        @Assisted internal val destination: Destination,
    ) : ViewModel(),
        Player.Listener,
        AnalyticsListener {
        @AssistedFactory
        interface Factory {
            fun create(destination: Destination): PlaybackViewModel
        }

        val currentPlayer = MutableStateFlow<PlayerInstance?>(null)

        internal lateinit var player: Player

        /** Benflix: gets videos ready early (null in tests) */
        @JvmField
        @Inject
        var playbackPrep: PlaybackPrep? = null

        /** Benflix: hand-checked opening/ending times for anime (null in tests) */
        @JvmField
        @Inject
        var aniSkip: AniSkipService? = null

        /** Benflix: marks the episode you move on from as watched (null in tests) */
        @JvmField
        @Inject
        var favoriteWatchManager: FavoriteWatchManager? = null

        internal var mediaSession: MediaSession? = null

        // Mutex & Job for changing playlist index
        internal val playlistMutex = Mutex()
        internal var playlistJob: Job? = null

        val controllerViewState =
            ControllerViewState(
                AppPreference.ControllerTimeout.defaultValue,
                true,
            )

        internal val mutableState = MutableStateFlow(PlaybackState())
        val state: StateFlow<PlaybackState> = mutableState

        internal lateinit var preferences: UserPreferences
        internal lateinit var itemId: UUID

        /** Benflix: episodes this player moved on from with Next / autoplay */
        @Volatile internal var movedOnFrom: Set<UUID> = emptySet()
        internal lateinit var currentItem: PlaylistItem
        internal var forceTranscoding: Boolean = false
        internal var activityListener: TrackActivityPlaybackListener? = null
        internal var trackChangeListener: TracksChangedListener? = null
        internal val jobs = mutableListOf<Job>()
        internal var subscribeJob: Job? = null

        internal val isPlaylist = destination is Destination.PlaybackList

        val subtitleSearchState = MutableStateFlow(SubtitleSearchState())

        val currentUserDto = serverRepository.currentUserDtoFlow

        @VisibleForTesting
        val initJob: Job

        init {
            initJob =
                viewModelScope.launchIO {
                    addCloseable {
                        screensaverService.keepScreenOn(false)
                        disconnectPlayer()
                    }
                    init()
                }
        }

        internal fun disconnectPlayer() {
            if (this@PlaybackViewModel::player.isInitialized) {
                player.removeListener(this@PlaybackViewModel)
                (player as? ExoPlayer)?.removeAnalyticsListener(this@PlaybackViewModel)

                this@PlaybackViewModel.activityListener?.let {
                    it.release()
                    player.removeListener(it)
                }
                // Benflix: "stopped" was just sent, and this player is going away, so don't send it again
                this@PlaybackViewModel.activityListener = null
                this@PlaybackViewModel.trackChangeListener?.let {
                    player.removeListener(it)
                }
                player.release()
                mediaSession?.release()
            }
            jobs.forEach { it.cancel() }
        }

        /** Benflix: true while this video was moved to MPV for its styled subtitles */
        internal var autoMpvActive = false

        /** Benflix: which fallback step (see onPlayerError) was last used, and for which video */
        private var fallbackStepFor: UUID? = null
        private var fallbackStep = 0

        /** Benflix: restarts a video once if it starts with a low, jerky frame rate */
        internal val pictureWatchdog by lazy {
            PictureWatchdog(
                scope = viewModelScope,
                player = { if (this::player.isInitialized) player as? ExoPlayer else null },
                onNotice = { text ->
                    if (state.value.notice == null) showNotice(PlayerNotice(title = text, autoHideMs = 4_000L))
                },
                afterRestart = { soundWatchdog.check("picture restart") },
            )
        }

        /** Benflix: brings the sound back if it drops out (after a skip, a sound switch...) */
        internal val soundWatchdog by lazy {
            SoundWatchdog(
                scope = viewModelScope,
                player = { if (this::player.isInitialized) player else null },
                videoHasSound = {
                    state.value.currentPlayback
                        ?.mediaSourceInfo
                        ?.mediaStreams
                        ?.any { it.type == MediaStreamType.AUDIO } == true
                },
                onConvertSound = { fullConvert -> convertSoundOnServer(fullConvert) },
                onNotice = { text ->
                    if (state.value.notice == null) showNotice(PlayerNotice(title = text, autoHideMs = 5_000L))
                },
                fromServerStream = {
                    state.value.currentPlayback?.playMethod.let {
                        it != null && it != PlayMethod.DIRECT_PLAY
                    }
                },
                describePlayback = {
                    val playback = state.value.currentPlayback
                    val audio =
                        playback?.mediaSourceInfo?.mediaStreams?.firstOrNull {
                            it.type == MediaStreamType.AUDIO && it.index == playback?.audioIndex
                        }
                    listOf(
                        "play=${playback?.playMethod}",
                        "container=${playback?.mediaSourceInfo?.container}",
                        "fileSound=${audio?.codec} ${audio?.channels}ch",
                        "serverSound=${playback?.transcodeInfo?.audioCodec}",
                        "why=${playback?.transcodeInfo?.transcodeReasons?.joinToString("+")}",
                        "night=${NightMode.isOn(context)}",
                    ).joinToString(", ")
                },
            )
        }

        internal suspend fun createPlayer(
            isHdr: Boolean,
            is4k: Boolean,
            styledSubs: Boolean = false,
        ) {
            val softwareDecoding =
                !preferences.appPreferences.playbackPreferences.mpvOptions.enableHardwareDecoding
            val requestBackend =
                (destination as? Destination.Playback)?.backend
                    ?: preferences.appPreferences.playbackPreferences.playerBackend
            val playerBackend =
                when (requestBackend) {
                    PlayerBackend.UNRECOGNIZED,
                    PlayerBackend.EXO_PLAYER,
                    -> PlayerBackend.EXO_PLAYER

                    PlayerBackend.MPV -> PlayerBackend.MPV

                    PlayerBackend.PREFER_MPV -> if (isHdr || (is4k && softwareDecoding)) PlayerBackend.EXO_PLAYER else PlayerBackend.MPV

                    PlayerBackend.EXTERNAL_PLAYER -> throw IllegalStateException("Cannot use this for external playback")
                }.let { chosen ->
                    // Benflix (opt-in): anime-style ASS/SSA subtitles look right in MPV
                    val autoMpv =
                        chosen == PlayerBackend.EXO_PLAYER &&
                            styledSubs &&
                            !isHdr &&
                            (destination as? Destination.Playback)?.backend == null &&
                            preferences.appPreferences.benflixPreferences.autoMpvStyledSubs
                    autoMpvActive = autoMpv
                    if (autoMpv) PlayerBackend.MPV else chosen
                }

            Timber.d("Selected backend: %s", playerBackend)
            if (currentPlayer.value?.backend != playerBackend) {
                Timber.i("Switching player backend to %s", playerBackend)
                withContext(WholphinDispatchers.Main) {
                    disconnectPlayer()
                }

                val playerCreation =
                    playerFactory.createVideoPlayer(
                        playerBackend,
                        preferences.appPreferences,
                    )
                this.player = playerCreation.player
                currentPlayer.update {
                    PlayerInstance(playerCreation.player, playerBackend, playerCreation.assHandler)
                }
                configurePlayer()
            }
        }

        internal fun configurePlayer() {
            player.addListener(this)
            (player as? ExoPlayer)?.addAnalyticsListener(this)
            subscribeToWebSocket()
            jobs.add(listenForTranscodeReason())
            val sessionPlayer =
                MediaSessionPlayer(
                    player,
                    preferences.appPreferences.playbackPreferences,
                )
            mediaSession = playerFactory.createMediaSession(sessionPlayer)
        }

        /**
         * Initialize from the UI to start playback
         */
        internal suspend fun init() {
            musicService.stop()
            this.preferences = userPreferencesService.getCurrent()
            val settingsMaxBitrate =
                preferences.appPreferences.playbackPreferences.maxBitrate
                    .takeIf { it > 0 } ?: AppPreference.DEFAULT_BITRATE
            // Benflix: on mobile data (not Wi-Fi), use a lower quality to save data
            val mobileCap = BenflixMobileData.bitrateCap(context, preferences.appPreferences.benflixPreferences)
            val dataMaxBitrate =
                if (mobileCap != null && mobileCap < settingsMaxBitrate) {
                    showToast(context, context.getString(R.string.benflix_data_saver_toast), Toast.LENGTH_SHORT)
                    mobileCap
                } else {
                    settingsMaxBitrate
                }
            // Benflix: picked a lower quality today because the video kept stopping? Only lasts today
            val todaysCap = BenflixPlayerPrefs.todaysLowerQuality(context)
            val maxBitrate =
                if (todaysCap != null && todaysCap < dataMaxBitrate) todaysCap else dataMaxBitrate
            mutableState.update {
                it.copy(
                    nextUp = null,
                    maxBitrate = maxBitrate,
                )
            }
            if (preferences.appPreferences.playbackPreferences.refreshRateSwitching) {
                addCloseable { refreshRateService.resetRefreshRate() }
            }
            controllerViewState.hideMilliseconds =
                preferences.appPreferences.playbackPreferences.controllerTimeoutMs
            this.forceTranscoding =
                (destination as? Destination.Playback)?.forceTranscoding ?: false
            val positionMs: Long
            val forceTranscoding: Boolean

            val itemId =
                when (val d = destination) {
                    is Destination.Playback -> {
                        positionMs = d.positionMs
                        forceTranscoding = d.forceTranscoding
                        d.itemId
                    }

                    is Destination.PlaybackList -> {
                        positionMs = 0
                        forceTranscoding = false
                        d.itemId
                    }

                    else -> {
                        throw IllegalArgumentException("Destination not supported: $destination")
                    }
                }
            this.itemId = itemId
            // Benflix: the details page may already have it ready
            val queriedItem = playbackPrep?.take(itemId) ?: api.userLibraryApi.getItem(itemId).content
            // Benflix Smart resume: when coming back to something, start a few seconds early
            // Only when actually resuming the saved spot: not a chapter pick or other chosen start,
            // and not something that's never been played
            val savedPositionMs = (queriedItem.userData?.playbackPositionTicks ?: 0L) / 10_000L
            val lastPlayed = queriedItem.userData?.lastPlayedDate
            val isResume =
                positionMs > 0 &&
                    lastPlayed != null &&
                    abs(positionMs - savedPositionMs) <= SMART_RESUME_MATCH_MS
            val smartRewindMs =
                if (isResume && !preferences.appPreferences.benflixPreferences.smartResumeOff) {
                    SmartResume.rewindMs(lastPlayed)
                } else {
                    0L
                }
            val startPositionMs = (positionMs - smartRewindMs).coerceAtLeast(0L)
            if (smartRewindMs > 0 && startPositionMs > 0) {
                showToast(
                    context,
                    context.getString(R.string.benflix_smart_resume_toast, (smartRewindMs / 1000).toInt()),
                    Toast.LENGTH_SHORT,
                )
            }
            val playlistItem =
                if (queriedItem.type.playable) {
                    PlaylistItem.Media(BaseItem(queriedItem, false))
                } else {
                    // Benflix random episodes: keeps going until they back out
                    shuffleSession =
                        queriedItem.type == BaseItemKind.SERIES &&
                        (
                            (destination as? Destination.PlaybackList)?.shuffle == true ||
                                (destination as? Destination.Playback)?.shuffle == true
                        )
                    val playlistResult =
                        if (destination is Destination.PlaybackList) {
                            playlistCreator.createFrom(
                                item = queriedItem,
                                startIndex = destination.startIndex ?: 0,
                                sortAndDirection = destination.sortAndDirection,
                                shuffled = destination.shuffle,
                                recursive = destination.recursive,
                                filter = destination.filter,
                            )
                        } else {
                            val shuffled =
                                if (destination is Destination.Playback) {
                                    destination.shuffle
                                } else {
                                    false
                                }
                            // Try to create a playlist
                            playlistCreator.createFrom(
                                item = queriedItem,
                                recursive = true,
                                shuffled = shuffled,
                            )
                        }
                    when (val r = playlistResult) {
                        is PlaylistCreationResult.Error -> {
                            mutableState.update { it.copy(loading = LoadingState.Error(r.message, r.ex)) }
                            return
                        }

                        is PlaylistCreationResult.Success -> {
                            if (r.playlist.items.isEmpty()) {
                                showToast(context, "Playlist is empty", Toast.LENGTH_SHORT)
                                navigationManager.goBack()
                                return
                            }
                            mutableState.update {
                                it.copy(playlist = r.playlist)
                            }
                            r.playlist.items.first()
                        }
                    }
                }

            viewModelScope.launch(ExceptionHandler()) { controllerViewState.observe() }

            val intros =
                // If not resuming playback & cinema mode is enabled, get potential intros
                if (positionMs == 0L && preferences.appPreferences.playbackPreferences.cinemaMode) {
                    api.userLibraryApi
                        .getIntros(
                            itemId = playlistItem.id,
                            userId = serverRepository.currentUser?.id,
                        ).content.items
                        .map {
                            PlaylistItem.Intro(BaseItem(it))
                        }
                } else {
                    emptyList()
                }
            val firstItem =
                if (intros.isNotEmpty()) {
                    Timber.v("Got %s intros", intros.size)
                    mutableState.update {
                        it.copy(playlist = Playlist(intros + it.playlist.items))
                    }
                    intros.first()
                } else {
                    playlistItem
                }

            val played =
                play(
                    firstItem,
                    startPositionMs,
                    forceTranscoding,
                )
            if (!played) {
                goToNext(markLeftWatched = false)
            }

            if (!isPlaylist) {
                val result = playlistCreator.createFrom(queriedItem)
                if (result is PlaylistCreationResult.Success && result.playlist.items.isNotEmpty()) {
                    mutableState.update {
                        it.copy(
                            playlist = Playlist(it.playlist.items + result.playlist.items),
                        )
                    }
                }
            }
        }

        internal fun updateCurrentPlayback(block: (CurrentPlayback?) -> CurrentPlayback?) {
            mutableState.update {
                it.copy(currentPlayback = block.invoke(it.currentPlayback))
            }
        }

        /**
         * Play an item
         *
         * @param currentItem the item to play
         * @param positionMs the starting playback position in milliseconds
         * @param itemPlayback the parameters for playback such chosen subtitle or audio streams
         * @param forceTranscoding whether the user has requested to force playback via transcoding
         */
        internal suspend fun play(
            playlistItem: PlaylistItem,
            positionMs: Long,
            forceTranscoding: Boolean = this.forceTranscoding,
        ): Boolean =
            withContext(WholphinDispatchers.IO) {
                val item =
                    when (playlistItem) {
                        is PlaylistItem.Intro -> playlistItem.item
                        is PlaylistItem.Media -> playlistItem.item
                    }

                Timber.i("Playing ${item.id}")

                // New item, so we can clear the media segment tracker & subtitle cues
                resetSegmentState()
                mutableState.update { it.copy(subtitleCues = emptyList()) }

                viewModelScope.launchIO {
                    // Starting playback, so want to invalidate the last played timestamp for this item
                    datePlayedService.invalidate(item)
                }

                if (item.type !in supportItemKinds) {
                    showToast(
                        context,
                        "Unsupported type '${item.type}', skipping...",
                        Toast.LENGTH_SHORT,
                    )
                    return@withContext false
                }
                this@PlaybackViewModel.currentItem = playlistItem
                this@PlaybackViewModel.itemId = item.id

                val isLiveTv = item.type == BaseItemKind.TV_CHANNEL
                val base = item.data

                // Use the provided playback parameters or else check if the database has some
                val itemPlayback =
                    serverRepository.currentUser?.let { user ->
                        itemPlaybackDao.getItem(user, base.id)?.let {
                            Timber.v("Fetched itemPlayback from DB: %s", it)
                            if (it.sourceId != null) {
                                it
                            } else {
                                null
                            }
                        }
                    }
                val mediaSource =
                    if (!isLiveTv) {
                        streamChoiceService.chooseSource(base, itemPlayback)
                    } else {
                        null
                    }

                val audioStc =
                    streamChoiceService.getSeriesTrackChoices(
                        base,
                        SeriesTrackChoiceType.AUDIO,
                    )
                val subtitleStc =
                    streamChoiceService.getSeriesTrackChoices(
                        base,
                        SeriesTrackChoiceType.SUBTITLE,
                    )

                if (mediaSource == null && !isLiveTv) {
                    showToast(
                        context,
                        "Item has no media sources, skipping...",
                        Toast.LENGTH_SHORT,
                    )
                    return@withContext false
                }

                val videoStream =
                    mediaSource
                        ?.mediaStreams
                        ?.firstOrNull { it.type == MediaStreamType.VIDEO }
                        ?.let {
                            val isHdr =
                                it.videoRange == VideoRange.HDR ||
                                    (it.videoRangeType != VideoRangeType.SDR && it.videoRangeType != VideoRangeType.UNKNOWN)
                            // Often times 4k movies have a wider aspect ratio so the height is lower even though the width is still 3840
                            val is4k = (it.width ?: 0) > 2560 || (it.height ?: 0) > 1440
                            SimpleVideoStream(it.index, isHdr, is4k)
                        }

                val subtitleStreams = mediaSource?.let { getSubtitleStreams(mediaSource) }.orEmpty()
                val audioStreams = mediaSource?.let { getAudioStreams(mediaSource) }.orEmpty()
                val audioResult =
                    mediaSource?.let {
                        streamChoiceService
                            .chooseAudioStream(
                                source = mediaSource,
                                item = base,
                                itemPlayback = itemPlayback,
                                stc = audioStc,
                                prefs = preferences,
                            )
                    }
                val audioIndex = audioResult?.stream?.index

                val subtitleIndex =
                    mediaSource?.let {
                        streamChoiceService
                            .chooseSubtitleStream(
                                item = base,
                                source = mediaSource,
                                audioStream = audioResult?.stream,
                                itemPlayback = itemPlayback,
                                stc = subtitleStc,
                                prefs = preferences,
                            )?.stream
                            ?.index
                    }
                // Create the correct player for the media
                val styledSubs =
                    subtitleIndex != null &&
                        mediaSource
                            ?.mediaStreams
                            ?.firstOrNull { it.type == MediaStreamType.SUBTITLE && it.index == subtitleIndex }
                            ?.codec
                            ?.lowercase() in setOf("ass", "ssa")
                createPlayer(videoStream?.hdr == true, videoStream?.is4k == true, styledSubs)

                Timber.d(
                    "Selected mediaSource=%s, audioIndex=%s, subtitleIndex=%s",
                    mediaSource?.id,
                    audioIndex,
                    subtitleIndex,
                )

                val trickPlayInfo =
                    mediaSource?.id?.let { sourceId ->
                        item.data.trickplay
                            ?.get(sourceId)
                            ?.values
                            ?.firstOrNull()
                    }

                trickPlayInfo?.let { trickplayInfo ->
                    mediaSource.runTimeTicks?.ticks?.let { duration ->
                        viewModelScope.launchIO {
                            prefetchTrickplay(
                                duration,
                                trickplayInfo,
                                mediaSource.id?.toUUIDOrNull(),
                            )
                        }
                    }
                }

                val chapters = Chapter.fromDto(base)
                mutableState.update {
                    it.copy(
                        currentItemPlayback = itemPlayback,
                        currentPlayback = null,
                    )
                }
                updateCurrentMedia {
                    CurrentMediaInfo(
                        sourceId = mediaSource?.id,
                        videoStream = videoStream,
                        audioStreams = audioStreams,
                        subtitleStreams = subtitleStreams,
                        chapters = chapters,
                        trickPlayInfo = trickPlayInfo,
                    )
                }
                // Benflix: back after a while? Show where they left off before starting
                val recap = if (positionMs <= 0) findRecap(base) else null
                withContext(WholphinDispatchers.Main) {
                    changeStreams(
                        item = item,
                        audioIndex = audioIndex,
                        subtitleIndex = subtitleIndex,
                        positionMs = if (positionMs > 0) positionMs else C.TIME_UNSET,
                        sourceId = mediaSource?.id,
                        enableDirectPlay = !forceTranscoding,
                        enableDirectStream = !forceTranscoding,
                    )
                    player.prepare()
                    if (recap == null) player.play()
                }
                mutableState.update { it.copy(recap = recap) }
                listenForSegments(item.id)
                watchForAfterCreditsScene(item.id)
                watchForMovieNext(item)
                benflixStartChecks(item, positionMs)
                viewModelScope.launchIO { warmNextNearEnd(item.id) }
                // Benflix "What Kids watched this week"
                BenflixKidsLog.record(context, base)
                return@withContext true
            }

        /**
         * Change which streams (ie audio or subtitle) are active
         */
        @OptIn(UnstableApi::class)
        internal suspend fun changeStreams(
            item: BaseItem,
            sourceId: String?,
            audioIndex: Int?,
            subtitleIndex: Int?,
            positionMs: Long = 0,
            enableDirectPlay: Boolean = !this.forceTranscoding,
            enableDirectStream: Boolean = !this.forceTranscoding,
            maxBitrate: Long = this.state.value.maxBitrate,
            allowAudioStreamCopy: Boolean = enableDirectStream,
        ): Unit =
            withContext(WholphinDispatchers.IO) {
                val itemId = item.id

                trackChangeListener?.let { onMain { player.removeListener(it) } }
                trackChangeListener = null

                state.value.currentPlayback?.let { currentPlayback ->
                    if (currentPlayback.item.id == item.id &&
                        currentPlayback.playMethod == PlayMethod.DIRECT_PLAY &&
                        enableDirectPlay &&
                        state.value.maxBitrate == maxBitrate
                    ) {
                        val wasSuccessful =
                            changeStreamsDirectPlay(
                                currentPlayback = currentPlayback,
                                audioIndex = audioIndex,
                                subtitleIndex = subtitleIndex,
                            )
                        if (wasSuccessful) return@withContext
                    }
                }

                Timber.i(
                    "changeStreams (%s): audioIndex=%s, subtitleIndex=%s, enableDirectPlay=%s, enableDirectStream=%s, positionMs=%s, maxBitrate=%s",
                    itemId,
                    audioIndex,
                    subtitleIndex,
                    enableDirectPlay,
                    enableDirectStream,
                    positionMs,
                    maxBitrate,
                )
                mutableState.update { it.copy(maxBitrate = maxBitrate) }

                val response by
                    api.mediaInfoApi
                        .getPostedPlaybackInfo(
                            itemId,
                            PlaybackInfoDto(
                                startTimeTicks = null,
                                deviceProfile =
                                    if (currentPlayer.value!!.backend == PlayerBackend.EXO_PLAYER) {
                                        deviceProfileService.getOrCreateDeviceProfile(
                                            preferences.appPreferences,
                                            serverRepository.currentServer?.serverVersion,
                                        )
                                    } else {
                                        mpvDeviceProfile
                                    },
                                maxAudioChannels = null,
                                audioStreamIndex = audioIndex,
                                subtitleStreamIndex = subtitleIndex,
                                mediaSourceId = sourceId,
                                alwaysBurnInSubtitleWhenTranscoding = false,
                                maxStreamingBitrate = maxBitrate.toInt(),
                                enableDirectPlay = enableDirectPlay,
                                enableDirectStream = enableDirectStream,
                                allowVideoStreamCopy = enableDirectStream,
                                allowAudioStreamCopy = allowAudioStreamCopy,
                                enableTranscoding = true,
                                autoOpenLiveStream = true,
                            ),
                        )
                if (response.errorCode != null) {
                    Timber.e("Error in PostedPlaybackInfo: %s", response.errorCode)
                    mutableState.update { it.copy(loading = LoadingState.Error(response.errorCode?.serialName)) }
                    return@withContext
                }
                val source = response.mediaSources.firstOrNull()
                source?.let { source ->
                    val mediaUrl =
                        if (source.supportsDirectPlay) {
                            if (source.isRemote && source.path.isNotNullOrBlank()) {
                                Timber.i("Playback is remote for source: %s", source.id)
                                source.path
                            } else {
                                api.videosApi.getVideoStreamUrl(
                                    itemId = itemId,
                                    mediaSourceId = source.id,
                                    liveStreamId = source.liveStreamId,
                                    static = true,
                                    tag = source.eTag,
                                    playSessionId = response.playSessionId,
                                )
                            }
                        } else if (source.supportsDirectStream) {
                            source.transcodingUrl?.let(api::createUrl)
                        } else {
                            source.transcodingUrl?.let(api::createUrl)
                        }
                    if (mediaUrl.isNullOrBlank()) {
                        mutableState.update {
                            it.copy(
                                loading =
                                    LoadingState.Error(
                                        "Unable to get media URL from the server. Do you have permission to view and/or transcode?",
                                    ),
                            )
                        }
                        return@withContext
                    }
                    val transcodeType =
                        when {
//                        playerBackend == PlayerBackend.MPV -> PlayMethod.DIRECT_PLAY
                            source.supportsDirectPlay -> PlayMethod.DIRECT_PLAY

                            source.supportsDirectStream -> PlayMethod.DIRECT_STREAM

                            source.supportsTranscoding -> PlayMethod.TRANSCODE

                            else -> throw Exception("No supported playback method")
                        }
                    Timber.i("Playback decision for $itemId: $transcodeType")

                    val externalSubtitleCount = source.externalSubtitlesCount

                    val externalSubtitle =
                        source.findExternalSubtitle(subtitleIndex)?.let {
                            Timber.v("Using externally delivered subtitle tracks %s", subtitleIndex)
                            it.deliveryUrl?.let { deliveryUrl ->
                                var flags = 0
                                if (it.isForced) flags = flags.or(C.SELECTION_FLAG_FORCED)
                                if (it.isDefault) flags = flags.or(C.SELECTION_FLAG_DEFAULT)
                                MediaItem.SubtitleConfiguration
                                    .Builder(
                                        api.createUrl(deliveryUrl).toUri(),
                                    ).setId("e:${it.index}")
                                    .setMimeType(subtitleMimeTypes[it.codec])
                                    .setLanguage(it.language)
                                    .setLabel(it.title)
                                    .setSelectionFlags(flags)
                                    .build()
                            }
                        }

                    Timber.v(
                        "subtitleIndex=$subtitleIndex, externalSubtitleCount=$externalSubtitleCount, externalSubtitle=$externalSubtitle",
                    )

                    val mediaItem =
                        MediaItem
                            .Builder()
                            .setMediaId(itemId.toString())
                            .setMediaMetadata(
                                item.toMediaMetadata(
                                    imageUrlService.getItemImageUrl(
                                        item,
                                        ImageType.PRIMARY,
                                        useSeriesForPrimary = true,
                                    ),
                                ),
                            ).setUri(mediaUrl.toUri())
                            .setSubtitleConfigurations(listOfNotNull(externalSubtitle))
                            .apply {
                                when (source.container) {
                                    Codec.Container.HLS -> setMimeType(MimeTypes.APPLICATION_M3U8)
                                    Codec.Container.DASH -> setMimeType(MimeTypes.APPLICATION_MPD)
                                }
                            }.build()

                    val playback =
                        CurrentPlayback(
                            item = item,
                            tracks = listOf(),
                            backend = currentPlayer.value!!.backend,
                            playMethod = transcodeType,
                            playSessionId = response.playSessionId,
                            liveStreamId = source.liveStreamId,
                            mediaSourceInfo = source,
                            audioIndex = audioIndex ?: TrackIndex.DISABLED,
                            subtitleIndex = subtitleIndex ?: TrackIndex.DISABLED,
                        )

                    preferences.appPreferences.playbackPreferences.let { prefs ->
                        source.mediaStreams
                            ?.firstOrNull { it.type == MediaStreamType.VIDEO }
                            ?.let { stream ->
                                refreshRateService.changeRefreshRate(
                                    stream = stream,
                                    switchRefreshRate = prefs.refreshRateSwitching,
                                    switchResolution = prefs.resolutionSwitching,
                                )
                            }
                    }
                    withContext(WholphinDispatchers.Main) {
                        // TODO, don't need to release & recreate when switching streams
                        this@PlaybackViewModel.activityListener?.let {
                            it.release()
                            player.removeListener(it)
                        }

                        val playbackItemState = PlaybackItemState(playback)
                        val activityListener =
                            TrackActivityPlaybackListener(
                                api = api,
                                player = player,
                                getState = { playbackItemState },
                                countWatchTime = playback.item.type == BaseItemKind.MOVIE || playback.item.type == BaseItemKind.EPISODE,
                            )
                        player.addListener(activityListener)
                        this@PlaybackViewModel.activityListener = activityListener

                        mutableState.update {
                            it.copy(
                                loading = LoadingState.Success,
                                currentPlayback = playback,
                            )
                        }
                        player.setMediaItem(
                            mediaItem,
                            positionMs,
                        )
                        val onFailure: () -> Unit = {
                            if (player is MpvPlayer && externalSubtitle != null) {
                                // MpvPlayer may change tracks more than once to add external subtitles
                                trackChangeListener?.let { player.addListener(it) }
                            } else {
                                viewModelScope.launchIO {
                                    changeStreams(
                                        item = item,
                                        sourceId = sourceId,
                                        audioIndex = audioIndex,
                                        subtitleIndex = subtitleIndex,
                                        positionMs = positionMs,
                                        enableDirectPlay = false,
                                        enableDirectStream = true,
                                    )
                                }
                            }
                        }
                        val onTracksChangedListener =
                            if (transcodeType == PlayMethod.DIRECT_PLAY && (audioIndex != null || subtitleIndex != null)) {
                                TracksChangedListener(
                                    player = player,
                                    audioIndex = audioIndex,
                                    subtitleIndex = subtitleIndex,
                                    source = source,
                                    onFailure = onFailure,
                                )
                            } else if (externalSubtitle != null) {
                                TracksChangedListener(
                                    player = player,
                                    audioIndex = null, // Do not manually activate audio
                                    subtitleIndex = subtitleIndex,
                                    source = source,
                                    onFailure = onFailure,
                                )
                            } else {
                                null
                            }
                        onTracksChangedListener?.let {
                            player.addListener(it)
                            this@PlaybackViewModel.trackChangeListener = it
                        }

                        if (subtitleIndex != null) {
                            viewModelScope.launchIO { loadSubtitleDelay() }
                        }
                    }
                }
            }

        fun changeAudioStream(index: Int) {
            viewModelScope.launchIO {
                val currentPlayback = state.value.currentPlayback
                if (currentPlayback != null) {
                    Timber.d("Changing audio track to %s", index)
                    saveTrackSelection(index, MediaStreamType.AUDIO)
                    mutableState.update {
                        it.copy(
                            currentPlayback = it.currentPlayback?.copy(audioIndex = index),
                        )
                    }

                    // Resolve ONLY_FORCED to actual track based on new audio language
                    val resolvedSubtitleIndex =
                        streamChoiceService.resolveSubtitleIndex(
                            source = currentPlayback.mediaSourceInfo,
                            audioStreamIndex = index,
                            item = currentItem.item.data,
                            subtitleIndex = currentPlayback.subtitleIndex,
                            prefs = preferences,
                        )

                    changeStreams(
                        item = currentItem.item,
                        sourceId = currentPlayback.mediaSourceInfo.id,
                        audioIndex = index,
                        subtitleIndex = resolvedSubtitleIndex,
                        positionMs = onMain { player.currentPosition },
                        enableDirectPlay = true,
                    )
                    onMain { soundWatchdog.check("audio switch") }
                } else {
                    Timber.w("Trying to change audio, but currentPlayback is null")
                }
            }
        }

        internal var whatDidTheySayJob: Job? = null

        /**
         * Benflix "What did they say?": after two quick rewinds, if subtitles are off,
         * turn them on for a few seconds and then off again. Not saved as a choice.
         */
        fun whatDidTheySay() {
            val currentPlayback = state.value.currentPlayback ?: return
            if (currentPlayback.subtitleIndex >= 0 || whatDidTheySayJob?.isActive == true) return
            val subtitles =
                currentPlayback.mediaSourceInfo.mediaStreams
                    .orEmpty()
                    .filter { it.type == MediaStreamType.SUBTITLE && it.isForced != true }
            val pick =
                subtitles.firstOrNull { it.language?.lowercase() in listOf("eng", "en", "english") }
                    ?: subtitles.firstOrNull()
                    ?: return
            val offIndex = currentPlayback.subtitleIndex
            whatDidTheySayJob =
                viewModelScope.launchIO {
                    showToast(context, "Subtitles on for a moment", Toast.LENGTH_SHORT)
                    changeSubtitleStream(pick.index, remember = false).join()
                    delay(WHAT_DID_THEY_SAY_MS)
                    if (state.value.currentPlayback?.subtitleIndex == pick.index) {
                        changeSubtitleStream(offIndex, remember = false).join()
                    }
                }
        }

        fun changeSubtitleStream(
            index: Int,
            remember: Boolean = true,
        ): Job =
            viewModelScope.launchIO {
                val currentPlayback = state.value.currentPlayback
                if (currentPlayback != null) {
                    Timber.d("Changing subtitle track to %s", index)
                    if (remember) saveTrackSelection(index, MediaStreamType.SUBTITLE)
                    mutableState.update {
                        it.copy(
                            currentPlayback = it.currentPlayback?.copy(subtitleIndex = index),
                        )
                    }

                    // Resolve ONLY_FORCED to actual track index for playback
                    val resolvedIndex =
                        streamChoiceService.resolveSubtitleIndex(
                            source = currentPlayback.mediaSourceInfo,
                            audioStreamIndex = currentPlayback.audioIndex,
                            item = currentItem.item.data,
                            subtitleIndex = index,
                            prefs = preferences,
                        )

                    changeStreams(
                        item = currentItem.item,
                        sourceId = currentPlayback.mediaSourceInfo.id,
                        audioIndex = currentPlayback.audioIndex,
                        subtitleIndex = resolvedIndex,
                        positionMs = onMain { player.currentPosition },
                        enableDirectPlay = true,
                    )
                    onMain { soundWatchdog.check("subtitle switch") }
                } else {
                    Timber.w("Trying to change subtitle, but currentPlayback is null")
                }
            }

        fun changeMaxBitrate(newMaxBitrate: Long) {
            viewModelScope.launchIO {
                val currentPlayback = state.value.currentPlayback
                if (currentPlayback != null) {
                    Timber.d("Changing bitrate to %s", newMaxBitrate)
                    changeStreams(
                        item = currentItem.item,
                        sourceId = currentPlayback.mediaSourceInfo.id,
                        audioIndex = currentPlayback.audioIndex,
                        subtitleIndex = currentPlayback.subtitleIndex,
                        positionMs = onMain { player.currentPosition },
                        enableDirectPlay = true,
                        maxBitrate = newMaxBitrate,
                    )
                } else {
                    Timber.w("Trying to change bitrate, but currentPlayback is null")
                }
            }
        }

        fun getTrickplayUrl(
            index: Int,
            trickPlayInfo: TrickplayInfo? = state.value.currentMediaInfo.trickPlayInfo,
            mediaSourceId: UUID? =
                state.value.currentPlayback
                    ?.mediaSourceInfo
                    ?.id
                    ?.toUUIDOrNull(),
        ): String? =
            trickPlayInfo?.let {
                val itemId = currentItem.id
                return api.trickplayApi.getTrickplayTileImageUrl(
                    itemId,
                    trickPlayInfo.width,
                    index,
                    mediaSourceId,
                )
            }

        override fun onPlaybackStateChanged(playbackState: Int) {
            trackBuffering(playbackState)
            if (playbackState == Player.STATE_ENDED) {
                Timber.v("Playback state is STATE_ENDED")
                viewModelScope.launchDefault {
                    when (val nextItem = state.value.nextItem()) {
                        is PlaylistItem.Intro -> {
                            Timber.v("Next item is intro, so playing immediately")
                            goToNext(markLeftWatched = false)
                        }

                        is PlaylistItem.Media -> {
                            val prefs = preferences.appPreferences.playbackPreferences
                            when {
                                currentItem is PlaylistItem.Intro -> {
                                    Timber.v("Current item is intro, so playing next up immediately")
                                    goToNext(markLeftWatched = false)
                                }

                                prefs.showNextUpWhen == ShowNextUpWhen.NEXT_UP_NEVER && !prefs.autoPlayNext -> {
                                    Timber.v("Never show or auto play next up, returning")
                                    navigationManager.goBack()
                                }

                                prefs.showNextUpWhen != ShowNextUpWhen.NEXT_UP_NEVER -> {
                                    Timber.v("Setting next up to ${nextItem.id}")
                                    // Benflix: the video has ended, so there are no credits left to watch
                                    mutableState.update { it.copy(nextUp = nextItem.item, noCreditsLeft = true) }
                                }

                                else -> {
                                    controllerViewState.showControls()
                                }
                            }
                        }

                        null -> {
                            Timber.v("No next up")
                            // Benflix: the last episode of a show? Say so and offer something like it
                            if (!showFinishedShow()) navigationManager.goBack()
                        }
                    }
                }
            }
        }

        // Variables for tracking segment state
        @VisibleForTesting
        internal var segmentJob: Job? = null
        internal val autoSkippedSegments = mutableSetOf<UUID>()
        internal val outroShownSegments = mutableSetOf<UUID>()

        /** Benflix: the recap card was closed (or timed out), so start the episode */
        fun startAfterRecap() {
            if (state.value.recap == null) return
            mutableState.update { it.copy(recap = null) }
            player.play()
        }

        // Benflix: movies with a scene during or after the credits
        @Volatile internal var hasAfterCreditsScene = false

        /** Benflix: playing random episodes of a show, which keep going until Back */
        @Volatile internal var shuffleSession = false

        @Volatile internal var creditsReached = false
        internal var afterCreditsJob: Job? = null

        /** Benflix: the credits segment of this item has started */
        @Volatile internal var outroReached = false
        internal var movieNextJob: Job? = null

        fun dismissMovieNext() {
            mutableState.update { it.copy(movieNext = null) }
        }

        fun playMovieNext() {
            val next = state.value.movieNext ?: return
            mutableState.update { it.copy(movieNext = null) }
            // Swap this player for the new movie, so Back still goes to where you were before
            navigationManager.goBack()
            navigationManager.navigateTo(Destination.Playback(next.id, 0L))
        }

        // ---------------- Benflix player helpers ----------------

        /** Benflix: tags anime filler episodes (null in tests) */
        @JvmField
        @Inject
        var fillerService: FillerService? = null

        /** Benflix: sends "Report a problem" reports, here used for damaged files (null in tests) */
        @JvmField
        @Inject
        var benflixProblems: com.github.damontecres.wholphin.services.BenflixProblems? = null

        /** Benflix: per-profile settings stored on the server (null in tests) */
        @JvmField
        @Inject
        var displayPreferencesService: DisplayPreferencesService? = null

        /** Shows this profile always skips the intro of */
        internal val alwaysSkipIntroSeries: MutableSet<UUID> by lazy {
            java.util.concurrent.ConcurrentHashMap
                .newKeySet()
        }

        /** Shows this profile always skips the credits (outro) of */
        internal val alwaysSkipOutroSeries: MutableSet<UUID> by lazy {
            java.util.concurrent.ConcurrentHashMap
                .newKeySet()
        }

        @Volatile internal var alwaysSkipIntroLoaded = false
        internal var noticeJob: Job? = null
        internal var busyJob: Job? = null
        internal var subtitlesJob: Job? = null

        @Volatile internal var firstReadyAt = 0L

        @Volatile internal var lastSeekAt = 0L
        internal val bufferTimes by lazy { ArrayDeque<Long>() }

        @Volatile internal var stutterOffered = false

        internal fun currentSeriesId(): UUID? = if (this::currentItem.isInitialized) currentItem.item.data.seriesId else null

        /** Benflix: a button on a heads-up card was pressed */
        fun onNoticeAction(action: NoticeAction) {
            val notice = state.value.notice ?: return
            showNotice(null)
            when (action) {
                NoticeAction.DISMISS -> {}

                NoticeAction.WATCH_SKIPPED -> {
                    notice.targetId?.let { playInstead(it) }
                }

                NoticeAction.SKIP_FILLER, NoticeAction.ALWAYS_SKIP_FILLER -> {
                    if (action == NoticeAction.ALWAYS_SKIP_FILLER) {
                        BenflixPlayerPrefs.setAlwaysSkipFiller(context, true)
                        viewModelScope.launchIO { showToast(context, "Benflix will skip filler from now on", Toast.LENGTH_SHORT) }
                    }
                    viewModelScope.launchIO {
                        val (next, _) = nextStoryEpisode(currentItem.item) ?: return@launchIO
                        withContext(WholphinDispatchers.Main) { playInstead(next) }
                    }
                }

                NoticeAction.WATCH_FILLER_ANYWAY -> {
                    BenflixPlayerPrefs.fillerAllowed.addAll(notice.extraIds)
                    notice.targetId?.let { playInstead(it) }
                }

                NoticeAction.STOP_SKIPPING_FILLER -> {
                    BenflixPlayerPrefs.setAlwaysSkipFiller(context, false)
                    viewModelScope.launchIO { showToast(context, "Filler won't be skipped anymore", Toast.LENGTH_SHORT) }
                }

                NoticeAction.LOWER_QUALITY -> {
                    val actual =
                        minOf(
                            state.value.maxBitrate,
                            state.value.currentPlayback
                                ?.mediaSourceInfo
                                ?.bitrate
                                ?.toLong()
                                ?.takeIf { it > 0 } ?: Long.MAX_VALUE,
                        )
                    val lower = BenflixPlayerPrefs.lowerStep(actual)
                    BenflixPlayerPrefs.setTodaysLowerQuality(context, lower)
                    viewModelScope.launchIO {
                        showToast(
                            context,
                            "Lower quality for today. It's back to normal tomorrow",
                            Toast.LENGTH_SHORT,
                        )
                    }
                    changeMaxBitrate(lower)
                }

                NoticeAction.STOP_SKIPPING_INTRO -> {
                    currentSeriesId()?.let { setAlwaysSkipIntro(it, false) }
                }

                NoticeAction.STOP_SKIPPING_OUTRO -> {
                    currentSeriesId()?.let { setAlwaysSkipOutro(it, false) }
                }
            }
        }

        /** Benflix "Always for this show": skip this show's intro now and every time from now on */
        fun alwaysSkipIntroForThisShow(segmentId: UUID?) {
            val seriesId = currentSeriesId() ?: return
            setAlwaysSkipIntro(seriesId, true)
            updateSegment(segmentId, false)
            viewModelScope.launchIO { showToast(context, "Benflix will skip this show's intro from now on", Toast.LENGTH_SHORT) }
        }

        /** Benflix "Always for this show" on Skip credits: skip this show's credits now and every time from now on */
        fun alwaysSkipOutroForThisShow(segmentId: UUID?) {
            val seriesId = currentSeriesId() ?: return
            setAlwaysSkipOutro(seriesId, true)
            updateSegment(segmentId, false)
            viewModelScope.launchIO { showToast(context, "Benflix will skip this show's credits from now on", Toast.LENGTH_SHORT) }
        }

        /** Benflix: if the video hasn't started after a few seconds, say the server is busy */
        internal fun watchForBusy(itemId: UUID) {
            busyJob?.cancel()
            busyJob =
                viewModelScope.launchIO {
                    delay(BUSY_AFTER_MS)
                    val ready = onMain { player.playbackState == Player.STATE_READY }
                    if (!ready && firstReadyAt == 0L && this@PlaybackViewModel.itemId == itemId && state.value.notice == null) {
                        showNotice(
                            PlayerNotice(
                                title = "Benflix is busy right now",
                                message = "It may take a moment to start, and the picture may look a bit softer for a bit.",
                                autoHideMs = 9_000L,
                                busy = true,
                            ),
                        )
                    }
                }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                lastSeekAt = android.os.SystemClock.elapsedRealtime()
                soundWatchdog.check("seek")
            }
        }

        /** Benflix: the last episode of a show ended. True if the "You finished the show!" card is showing */
        internal suspend fun showFinishedShow(): Boolean {
            if (!this::currentItem.isInitialized) return false
            val item = (currentItem as? PlaylistItem.Media)?.item ?: return false
            if (item.type != BaseItemKind.EPISODE || shuffleSession) return false
            val seriesId = item.data.seriesId ?: return false
            if ((item.data.parentIndexNumber ?: 0) == 0) return false
            return try {
                withTimeoutOrNull(FINISHED_LOOKUP_MS) {
                    val episodes =
                        api.tvShowsApi
                            .getEpisodes(
                                GetEpisodesRequest(
                                    seriesId = seriesId,
                                    userId = serverRepository.currentUser?.id,
                                    adjacentTo = item.id,
                                ),
                            ).content.items
                            .filter { (it.parentIndexNumber ?: 0) != 0 }
                    if (episodes.lastOrNull()?.id != item.id) return@withTimeoutOrNull false
                    val series = api.userLibraryApi.getItem(seriesId).content
                    val similar =
                        try {
                            api.libraryApi
                                .getSimilarItems(
                                    GetSimilarItemsRequest(
                                        userId = serverRepository.currentUser?.id,
                                        itemId = seriesId,
                                        fields = ItemRowFields,
                                        limit = 15,
                                    ),
                                ).content.items
                                .filter { it.type == BaseItemKind.SERIES && it.userData?.played != true }
                                .take(3)
                                .map { BaseItem(it, true) }
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            emptyList()
                        }
                    mutableState.update {
                        it.copy(
                            notice = null,
                            showDone =
                                ShowDone(
                                    series = BaseItem(series, false),
                                    similar = similar,
                                    caughtUp = series.status.equals("Continuing", true),
                                ),
                        )
                    }
                    true
                } ?: false
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.v(ex, "Couldn't check for the end of the show")
                false
            }
        }

        fun closeShowDone() {
            mutableState.update { it.copy(showDone = null) }
            navigationManager.goBack()
        }

        fun openFromShowDone(item: BaseItem) {
            mutableState.update { it.copy(showDone = null) }
            navigationManager.goBack()
            navigationManager.navigateTo(Destination.MediaItem(item))
        }

        /**
         * This sets up a coroutine to periodically check whether the current playback progress is within a media segment (intro, outro, etc)
         */
        internal fun listenForSegments(itemId: UUID) {
            segmentJob?.cancel()
            segmentJob =
                viewModelScope.launchIO {
                    val prefs = preferences.appPreferences.playbackPreferences
                    val serverSegments =
                        try {
                            api.mediaSegmentsApi
                                .getItemSegments(itemId)
                                .content.items
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            Timber.w(ex, "Couldn't get media segments")
                            emptyList()
                        }
                    // Benflix: for anime, AniSkip's hand-checked times replace the server's guesses
                    val aniSkipSegments = aniSkip?.segmentsFor(api, itemId).orEmpty()
                    val segments = AniSkipMatch.merge(serverSegments, aniSkipSegments)
                    if (segments.isNotEmpty()) {
                        while (isActive) {
                            delay(500L)
                            val currentTicks =
                                onMain { player.currentPosition.milliseconds.inWholeTicks }
                            val currentSegment =
                                segments
                                    .firstOrNull {
                                        it.type != MediaSegmentType.UNKNOWN && currentTicks >= it.startTicks && currentTicks < it.endTicks
                                    }
                            if (currentSegment != null &&
                                currentSegment.itemId == this@PlaybackViewModel.itemId
                            ) {
                                if (currentSegment.id !=
                                    state.value.currentSegment
                                        ?.segment
                                        ?.id
                                ) {
                                    Timber.d(
                                        "Found media segment for %s: %s, %s",
                                        currentSegment.itemId,
                                        currentSegment.id,
                                        currentSegment.type,
                                    )
                                }
                                val state = state.value
                                if (currentSegment.type == MediaSegmentType.OUTRO) outroReached = true

                                if (currentSegment.type == MediaSegmentType.OUTRO && hasAfterCreditsScene) {
                                    // Benflix: there's a scene after the credits, so let the credits play
                                    creditsReached = true
                                    mutableState.update { it.copy(currentSegment = null) }
                                } else if (currentSegment.type == MediaSegmentType.OUTRO &&
                                    !alwaysSkipsOutro() &&
                                    prefs.showNextUpWhen == ShowNextUpWhen.DURING_CREDITS &&
                                    state.hasNext &&
                                    outroShownSegments.add(currentSegment.id)
                                ) {
                                    val nextItem = state.nextItem()
                                    if (nextItem is PlaylistItem.Media) {
                                        Timber.v("Setting next up during outro to ${nextItem?.id}")
                                        mutableState.update { it.copy(nextUp = nextItem.item) }
                                    }
                                } else {
                                    val behavior =
                                        when (currentSegment.type) {
                                            MediaSegmentType.COMMERCIAL -> {
                                                prefs.skipCommercials
                                            }

                                            MediaSegmentType.PREVIEW -> {
                                                prefs.skipPreviews
                                            }

                                            MediaSegmentType.RECAP -> {
                                                prefs.skipRecaps
                                            }

                                            MediaSegmentType.OUTRO -> {
                                                if (alwaysSkipsOutro()) {
                                                    SkipSegmentBehavior.AUTO_SKIP
                                                } else {
                                                    prefs.skipOutros
                                                }
                                            }

                                            MediaSegmentType.INTRO -> {
                                                if (alwaysSkipsIntro()) {
                                                    SkipSegmentBehavior.AUTO_SKIP
                                                } else {
                                                    prefs.skipIntros
                                                }
                                            }

                                            MediaSegmentType.UNKNOWN -> {
                                                SkipSegmentBehavior.IGNORE
                                            }
                                        }
                                    withContext(WholphinDispatchers.Main) {
                                        val newSegment =
                                            when (behavior) {
                                                SkipSegmentBehavior.AUTO_SKIP -> {
                                                    if (autoSkippedSegments.add(currentSegment.id)) {
                                                        onMain { player.seekTo(currentSegment.endTicks.ticks.inWholeMilliseconds + 1) }
                                                        if (currentSegment.type == MediaSegmentType.OUTRO) {
                                                            mutableState.update { it.copy(noCreditsLeft = true) }
                                                        }
                                                        if (currentSegment.type == MediaSegmentType.OUTRO &&
                                                            alwaysSkipsOutro() &&
                                                            prefs.skipOutros != SkipSegmentBehavior.AUTO_SKIP
                                                        ) {
                                                            showNotice(
                                                                PlayerNotice(
                                                                    title = "Skipped the credits",
                                                                    actions =
                                                                        listOf(
                                                                            "Stop skipping them for this show" to
                                                                                NoticeAction.STOP_SKIPPING_OUTRO,
                                                                        ),
                                                                    autoHideMs = 6_000L,
                                                                ),
                                                            )
                                                        }
                                                        if (currentSegment.type == MediaSegmentType.INTRO &&
                                                            alwaysSkipsIntro() &&
                                                            prefs.skipIntros != SkipSegmentBehavior.AUTO_SKIP
                                                        ) {
                                                            showNotice(
                                                                PlayerNotice(
                                                                    title = "Skipped the intro",
                                                                    actions =
                                                                        listOf(
                                                                            "Stop skipping it for this show" to
                                                                                NoticeAction.STOP_SKIPPING_INTRO,
                                                                        ),
                                                                    autoHideMs = 6_000L,
                                                                ),
                                                            )
                                                        }
                                                    }
                                                    MediaSegmentState(currentSegment, true)
                                                }

                                                SkipSegmentBehavior.ASK_TO_SKIP -> {
                                                    MediaSegmentState(
                                                        currentSegment,
                                                        autoSkippedSegments.contains(currentSegment.id),
                                                    )
                                                }

                                                else -> {
                                                    null
                                                }
                                            }
                                        mutableState.update { it.copy(currentSegment = newSegment) }
                                    }
                                }
                            } else if (currentSegment == null) {
                                mutableState.update { it.copy(currentSegment = null) }
                            }
                        }
                    }
                }
        }

        fun updateSegment(
            segmentId: UUID?,
            dismissed: Boolean,
        ) {
            viewModelScope.launchDefault {
                val segment = state.value.currentSegment?.segment
                if (segment != null && segment.id == segmentId) {
                    autoSkippedSegments.add(segment.id)
                    if (dismissed) {
                        mutableState.update { it.copy(currentSegment = it.currentSegment?.copy(interacted = true)) }
                    } else {
                        mutableState.update {
                            it.copy(
                                currentSegment = null,
                                noCreditsLeft = it.noCreditsLeft || segment.type == MediaSegmentType.OUTRO,
                            )
                        }
                        onMain { player.seekTo(segment.endTicks.ticks.inWholeMilliseconds + 1) }
                    }
                }
            }
        }

        internal var lastInteractionDate: Date = Date()

        /**
         * Tracks interactions with the UI for passout protection
         */
        fun reportInteraction() {
//            Timber.v("reportInteraction")
            lastInteractionDate = Date()
        }

        /**
         * Benflix: the age rating to show at the start. Episodes often have no rating of
         * their own, so they use their show's rating.
         */
        suspend fun ageRatingFor(item: BaseItem): String? =
            item.data.officialRating?.takeIf { it.isNotBlank() }
                ?: item.data.seriesId?.let { seriesId ->
                    try {
                        withContext(WholphinDispatchers.IO) {
                            api.userLibraryApi
                                .getItem(seriesId)
                                .content.officialRating
                                ?.takeIf { it.isNotBlank() }
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Age rating for show %s", seriesId)
                        null
                    }
                }

        fun shouldAutoPlayNextUp(): Boolean =
            preferences.appPreferences.playbackPreferences.let {
                // Benflix: at Kids bedtime, the next episode doesn't start by itself
                !BenflixBedtime.lockedNow() &&
                    !BenflixScreenTime.lockedNow() &&
                    it.autoPlayNext &&
                    if (it.passOutProtectionMs > 0 && !shuffleSession) {
                        (Date().time - lastInteractionDate.time) < it.passOutProtectionMs
                    } else {
                        true
                    }
            }

        /** Next / autoplay: moves on to the next item, marking the episode being left as watched */
        fun playNextUp() = goToNext(markLeftWatched = true)

        /**
         * Benflix: when someone moves on to the next episode (Next during the credits, or
         * autoplay), the one they were watching counts as watched, so it leaves Continue watching
         */
        private suspend fun markLeavingEpisodeWatched() {
            if (!this::currentItem.isInitialized) return
            val leaving = currentItem as? PlaylistItem.Media ?: return
            if (leaving.item.type != BaseItemKind.EPISODE) return
            if (leaving.item.data.userData
                    ?.played == true
            ) {
                return
            }
            val manager = favoriteWatchManager ?: return
            val leavingId = leaving.id
            val mark: suspend () -> Unit = {
                try {
                    manager.setWatched(leavingId, true)
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not mark %s as watched", leavingId)
                }
            }
            withContext(WholphinDispatchers.Main) {
                val listener = activityListener
                if (listener != null) {
                    // After "stopped" is sent, or the server would put the resume spot back
                    listener.runAfterStopped(mark)
                } else {
                    viewModelScope.launchIO { mark.invoke() }
                }
            }
        }

        private fun goToNext(markLeftWatched: Boolean) {
            if (BenflixBedtime.lockedNow() || BenflixScreenTime.lockedNow()) {
                // Benflix: it's Kids bedtime (or today's Kids time is used up), so no more episodes
                viewModelScope.launch(WholphinDispatchers.Main) { navigationManager.goBack() }
                return
            }
            // Benflix: don't ask "You haven't seen it yet" about the episode they're moving on from
            if (this::itemId.isInitialized) movedOnFrom = movedOnFrom + itemId
            viewModelScope.launchDefault {
                playlistMutex.withLock {
                    val state = state.value
                    if (state.hasNext) {
                        cancelUpNextEpisode()
                        val nextIndex = state.playlistIndex + 1
                        val item = state.playlist.items[nextIndex]
                        if (markLeftWatched) markLeavingEpisodeWatched()
                        mutableState.update { it.copy(playlistIndex = nextIndex) }
                        playlistJob?.cancel()
                        playlistJob =
                            viewModelScope.launchDefault {
                                val played = play(item, 0)
                                if (!played) {
                                    goToNext(markLeftWatched = false)
                                }
                            }
                    } else {
                        Timber.w("Attempting to play next, but there are no more items")
                        return@launchDefault
                    }
                }
            }
        }

        fun playPrevious() {
            viewModelScope.launchDefault {
                playlistMutex.withLock {
                    val state = state.value
                    if (state.hasPrevious) {
                        cancelUpNextEpisode()
                        val previousIndex = state.playlistIndex - 1
                        val item = state.playlist.items[previousIndex]
                        mutableState.update { it.copy(playlistIndex = previousIndex) }
                        playlistJob?.cancel()
                        playlistJob =
                            viewModelScope.launchDefault {
                                val played = play(item, 0)
                                if (!played) {
                                    playPrevious()
                                }
                            }
                    } else {
                        Timber.w("Attempting to play previous, but there is none")
                        return@launchDefault
                    }
                }
            }
        }

        suspend fun cancelUpNextEpisode() {
            mutableState.update { it.copy(nextUp = null) }
        }

        fun playItemInPlaylist(item: BaseItem) {
            viewModelScope.launchDefault {
                playlistMutex.withLock {
                    val state = state.value
                    val index = state.playlist.items.indexOfFirst { it.id == item.id }
                    if (index in state.playlist.items.indices) {
                        val toPlay = state.playlist.items[index]
                        mutableState.update { it.copy(playlistIndex = index) }
                        playlistJob?.cancel()
                        playlistJob =
                            viewModelScope.launchDefault {
                                val played = play(toPlay, 0)
                                if (!played) {
                                    goToNext(markLeftWatched = false)
                                }
                            }
                    } else {
                        Timber.w("Item not found in playlist %s", item.id)
                        return@launchDefault
                    }
                }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateCurrentPlayback {
                it?.copy(
                    tracks = checkForSupport(tracks),
                )
            }
        }

        override fun onCues(cueGroup: CueGroup) {
            mutableState.update { it.copy(subtitleCues = cueGroup.cues) }
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "Playback error")
            if (autoMpvActive) {
                fallBackFromAutoMpv()
                return
            }
            viewModelScope.launch(WholphinDispatchers.Main + ExceptionHandler()) {
                state.value.currentPlayback?.let {
                    val itemNow = it.item.id
                    // Benflix fallback steps when the TV can't play something:
                    //  1. play the file as is (direct play)
                    //  2. server repackages it: picture kept as is, sound turned into AAC. Light for
                    //     the server and the picture stays sharp. Fixes TVs that can't play the
                    //     file's sound (e.g. AC3 on some TCL TVs) or its container.
                    //  3. server converts everything
                    val step =
                        when {
                            it.playMethod == PlayMethod.DIRECT_PLAY -> 2
                            fallbackStepFor == itemNow && fallbackStep == 2 -> 3
                            it.playMethod == PlayMethod.DIRECT_STREAM -> 3
                            else -> 0
                        }
                    if (step == 0) {
                        mutableState.update {
                            it.copy(
                                loading =
                                    LoadingState.Error(
                                        // Benflix: both ways of playing it failed
                                        context.getString(R.string.benflix_content_issue),
                                        error,
                                    ),
                            )
                        }
                        return@launch
                    }
                    fallbackStepFor = itemNow
                    fallbackStep = step
                    if (step == 2) reportPossiblyDamagedFile(it, error)
                    Timber.w("Playback error during ${it.playMethod}, falling back to step $step")
                    changeStreams(
                        item = currentItem.item,
                        sourceId = it.mediaSourceInfo.id,
                        audioIndex = it.audioIndex,
                        subtitleIndex = it.subtitleIndex,
                        positionMs = player.currentPosition,
                        enableDirectPlay = false,
                        enableDirectStream = step == 2,
                        allowAudioStreamCopy = false,
                    )
                    withContext(WholphinDispatchers.Main) {
                        player.prepare()
                        player.play()
                        soundWatchdog.check("fallback")
                        pictureWatchdog.check(itemNow, "fallback")
                    }
                }
            }
        }

        /**
         * Benflix: the TV couldn't read the file itself (broken data, not just a format it can't
         * play), so tell Ben quietly: it shows up on his Server page under problem reports as
         * "Possibly damaged file", with the log. The viewer isn't bothered; playback carries on
         * through the server. Once per video per app start.
         */
        private fun reportPossiblyDamagedFile(
            playback: CurrentPlayback,
            error: PlaybackException,
        ) {
            if (error.errorCode !in DAMAGED_FILE_ERRORS) return
            val problems = benflixProblems ?: return
            if (!damagedReported.add(playback.itemId)) return
            val data = playback.item.data
            val title =
                if (data.seriesName != null) {
                    "${data.seriesName} S${data.parentIndexNumber ?: 0}E${data.indexNumber ?: 0} - ${data.name.orEmpty()}"
                } else {
                    data.name.orEmpty()
                }
            Timber.w("Possibly damaged file: %s (%s)", title, error.errorCodeName)
            viewModelScope.launchIO {
                try {
                    problems.report(playback.itemId, title, DAMAGED_FILE_PROBLEM)
                } catch (ex: kotlinx.coroutines.CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not report the damaged file")
                }
            }
        }

        fun release() {
            Timber.v("release")
            disconnectPlayer()
            activityListener = null
        }

        fun subscribeToWebSocket() {
            subscribeJob?.cancel()
            try {
                subscribeJob =
                    viewModelScope.launch {
                        coroutineScope {
                            api.webSocket
                                .subscribe<PlaystateMessage>()
                                .onEach { message ->
                                    message.data?.let { request ->
                                        Timber.v("Received playstate request: %s", request)
                                        withContext(WholphinDispatchers.Main) {
                                            handlePlaystateRequest(request)
                                        }
                                    }
                                }.catch { ex ->
                                    Timber.e(ex, "Error in websocket subscription")
                                }.launchIn(this)
                        }
                    }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Error in playback websocket subscription")
                if (viewModelScope.isActive) {
                    subscribeToWebSocket()
                }
            }
        }

        /**
         * Atomically update [currentMediaInfo]
         */
        internal suspend fun updateCurrentMedia(block: (CurrentMediaInfo) -> CurrentMediaInfo) =
            withContext(WholphinDispatchers.Default) {
                mutableState.update {
                    it.copy(currentMediaInfo = block.invoke(it.currentMediaInfo))
                }
            }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            Timber.v("onVideoDecoderInitialized: decoder=$decoderName")
            pictureWatchdog.decoderName = decoderName
            updateDecoder(decoderName, MediaType.VIDEO)
        }

        override fun onVideoDisabled(
            eventTime: AnalyticsListener.EventTime,
            decoderCounters: DecoderCounters,
        ) {
            Timber.d("onVideoDisabled")
            updateCurrentPlayback { it?.copy(videoDecoder = null) }
        }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            decoderReuseEvaluation?.let { decoder ->
                if (decoder.result != DecoderReuseEvaluation.REUSE_RESULT_NO) {
                    Timber.d("onVideoInputFormatChanged: decoder=${decoder.decoderName}")
                    updateDecoder(decoder.decoderName, MediaType.VIDEO)
                }
            }
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            Timber.d("decoder: onAudioDecoderInitialized: decoder=$decoderName")
            updateDecoder(decoderName, MediaType.AUDIO)
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            decoderReuseEvaluation?.let { decoder ->
                if (decoder.result != DecoderReuseEvaluation.REUSE_RESULT_NO) {
                    Timber.d("decoder: onAudioInputFormatChanged: decoder=${decoder.decoderName}")
                    updateDecoder(decoder.decoderName, MediaType.AUDIO)
                }
            }
        }

        override fun onAudioDisabled(
            eventTime: AnalyticsListener.EventTime,
            decoderCounters: DecoderCounters,
        ) {
            Timber.d("decoder: onAudioDisabled")
            updateCurrentPlayback { it?.copy(audioDecoder = null) }
        }

        internal var subtitleDelaySaveJob: Job? = null

        fun updateSubtitleDelay(delta: Duration) {
            subtitleDelaySaveJob?.cancel()
            updateCurrentPlayback {
                it?.let {
                    val newDelay = it.subtitleDelay + delta
                    val result = it.copy(subtitleDelay = it.subtitleDelay + delta)
                    subtitleDelaySaveJob =
                        viewModelScope.launchIO {
                            // Debounce & save
                            state.value.currentItemPlayback?.let { item ->
                                delay(1500)
                                itemPlaybackRepository.saveTrackModifications(
                                    item.itemId,
                                    item.subtitleIndex,
                                    newDelay,
                                )
                            }
                        }
                    result
                }
            }
        }

        suspend fun loadSubtitleDelay() {
            state.value.currentPlayback?.let {
                if (it.subtitleIndexEnabled) {
                    val result =
                        itemPlaybackRepository.getTrackModifications(it.item.id, it.subtitleIndex)
                    if (result != null) {
                        Timber.v(
                            "Loading subtitle delay %s for track=%s, itemId=%s",
                            result.delayMs,
                            it.subtitleIndex,
                            it.item.id,
                        )
                        updateCurrentPlayback { it?.copy(subtitleDelay = result.delayMs.milliseconds) }
                    }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            screensaverService.keepScreenOn(isPlaying)
        }

        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
            val player = this@PlaybackViewModel.player
            if (availableCommands.contains(Player.COMMAND_PREPARE) && player is MpvPlayer) {
                // MpvPlayer is initialized, so configure subtitles
                Timber.i("Applying subtitle config to MPV")
                viewModelScope.launchDefault {
                    val configuration = context.resources.configuration
                    val density = Density(context.resources.displayMetrics.density)
                    preferences.appPreferences.interfacePreferences.subtitlesPreferences.applyToMpv(
                        configuration,
                        density,
                    )
                }
            }
        }

        fun updateDensity(density: Density) {
            Timber.d("Density changed")
            viewModelScope.launchDefault {
                val availableCommands = onMain { player.availableCommands }
                if (availableCommands.contains(Player.COMMAND_PREPARE) && player is MpvPlayer) {
                    Timber.i("Applying density change for subtitle config to MPV")
                    val configuration = context.resources.configuration
                    preferences.appPreferences.interfacePreferences.subtitlesPreferences.applyToMpv(
                        configuration,
                        density,
                    )
                }
            }
        }

        override fun onBandwidthEstimate(
            eventTime: AnalyticsListener.EventTime,
            totalLoadTimeMs: Int,
            totalBytesLoaded: Long,
            bitrateEstimate: Long,
        ) {
            Timber.v(
                "onBandwidthEstimate: totalLoadTimeMs=%s, totalBytesLoaded=%s, bitrateEstimate=%s",
                totalLoadTimeMs,
                totalBytesLoaded,
                bitrateEstimate,
            )
            if (totalLoadTimeMs > 0 && totalBytesLoaded > 0) {
                mutableState.update {
                    it.copy(
                        analyticsState =
                            it.analyticsState.copy(
                                bitrate = formatBitrate((totalBytesLoaded.toDouble() / (totalLoadTimeMs / 1000.0) * 8).roundToInt()),
                                bitrateEstimate = formatBitrate(bitrateEstimate.toInt()),
                            ),
                    )
                }
            }
        }

        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long,
        ) {
            mutableState.update {
                it.copy(
                    analyticsState =
                        it.analyticsState.copy(
                            droppedFrames = it.analyticsState.droppedFrames + droppedFrames,
                        ),
                )
            }
        }
    }

internal const val WHAT_DID_THEY_SAY_MS = 12_000L

/** Benflix Smart resume: how close the start must be to the saved spot to count as resuming */
private const val SMART_RESUME_MATCH_MS = 2_000L

/** Show the after-credits notice this close to the end, if there are no credits marks */
internal const val AFTER_CREDITS_NOTICE_MS = 5 * 60_000L
internal const val NEXT_CHECK_MS = 15_000L
internal const val NEXT_PREP_MS = 3 * 60_000L
internal const val AFTER_CREDITS_SHOW_MS = 10_000L

/** Benflix: with no credits marker, offer "Watch next" this close to the end of a movie */
internal const val MOVIE_NEXT_NOTICE_MS = 4 * 60_000L

/** Benflix: per-profile list of shows whose intro is always skipped */
internal const val ALWAYS_SKIP_INTRO_KEY = "benflix.autoSkipIntro"

/** Benflix: per-profile list of shows whose credits (outro) are always skipped */
internal const val ALWAYS_SKIP_OUTRO_KEY = "benflix.autoSkipOutro"
internal const val BUSY_AFTER_MS = 6_000L
internal const val SEEK_GRACE_MS = 3_000L
internal const val STUTTER_WINDOW_MS = 120_000L
internal const val STUTTER_COUNT = 3
internal const val AUTO_SUBS_AFTER_MS = 4_000L
internal const val FINISHED_LOOKUP_MS = 5_000L

internal const val RECAP_LOOKUP_MS = 3_000L
internal const val RECAP_AFTER_DAYS = 14L

/** Benflix: player errors that mean the file's data is broken (not a format the TV can't play) */
private val DAMAGED_FILE_ERRORS =
    setOf(
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
    )

/** How it shows on Ben's Server page problem list */
private const val DAMAGED_FILE_PROBLEM = "Possibly damaged file (found automatically)"

/** Videos already reported since the app started, so one bad file doesn't flood the list */
private val damagedReported: MutableSet<UUID> =
    java.util.concurrent.ConcurrentHashMap
        .newKeySet()
