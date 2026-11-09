package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.AudioItem
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.enabled
import com.github.damontecres.wholphin.preferences.get
import com.github.damontecres.wholphin.services.hilt.DefaultCoroutineScope
import com.github.damontecres.wholphin.ui.DetailItemFields
import com.github.damontecres.wholphin.ui.gt
import com.github.damontecres.wholphin.ui.main.settings.MoveDirection
import com.github.damontecres.wholphin.ui.onMain
import com.github.damontecres.wholphin.ui.seekBack
import com.github.damontecres.wholphin.ui.seekForward
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.util.BlockingList
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.PlaybackItemState
import com.github.damontecres.wholphin.util.TrackActivityPlaybackListener
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.profile.Codec
import com.github.damontecres.wholphin.util.profile.supportedAudioCodecs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.instantMixApi
import org.jellyfin.sdk.api.client.extensions.universalAudioApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.PlaystateMessage
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Manage the global state for playing music
 *
 * Has functions for modifying the queue
 */
@OptIn(UnstableApi::class)
@Singleton
class MusicService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:DefaultCoroutineScope private val defaultScope: CoroutineScope,
        private val api: ApiClient,
        private val playerFactory: PlayerFactory,
        private val serverRepository: ServerRepository,
        private val imageUrlService: ImageUrlService,
        private val userPreferencesService: UserPreferencesService,
        private val downloadService: DownloadService,
    ) {
        private val _state = MutableStateFlow(MusicServiceState.EMPTY)
        val state: StateFlow<MusicServiceState> = _state

        private val audioFormats by lazy { listOf(*supportedAudioCodecs) }

        val player: Player by lazy {
            val disableAudioOffload =
                runBlocking {
                    userPreferencesService
                        .getCurrent()
                        .appPreferences.experimentalPreferences
                        .get { disableAudioOffload } ?: false
                }
            playerFactory
                .createAudioPlayer(disableAudioOffload = disableAudioOffload)
                .also {
                    it.addListener(MusicPlayerListener(it, _state))
                    it.addListener(BenflixMusicListener())
                }
        }

        /**
         * Benflix: the songs waiting in Up next that the person added themselves (Play next / Add to Up next),
         * by [AudioItem.key]. Everything else after the playing song is the list or mix it came from ("Then plays").
         */
        private val added = java.util.Collections.synchronizedSet(mutableSetOf<Long>())

        /** True for a queued song the person added themselves */
        fun isAdded(item: AudioItem): Boolean = item.key in added

        /** Benflix: what the queue came from, so it can keep going with a matching mix when it ends */
        private var sourceId: UUID? = null

        @Volatile
        private var extending = false

        /** Goes up whenever a new list starts or music stops, so a slow "keep going" mix can't land in the wrong queue */
        @Volatile
        private var generation = 0

        private inner class BenflixMusicListener : Player.Listener {
            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                val audio = mediaItem?.localConfiguration?.tag as? AudioItem
                if (audio != null) added.remove(audio.key)
                _state.update { it.copy(currentAudio = audio, liked = audio?.let { a -> a.id in likedIds } ?: false) }
                audio?.let { checkLiked(it.id) }
                // The last song has started: keep going with a matching mix (Ben: albums and playlists don't just stop)
                if (player.mediaItemCount > 0 && player.currentMediaItemIndex == player.mediaItemCount - 1) {
                    keepGoing(audio)
                }
            }
        }

        /** Songs liked this session, so the heart shows straight away */
        private val likedIds = java.util.Collections.synchronizedSet(mutableSetOf<UUID>())

        private fun checkLiked(id: UUID) {
            defaultScope.launch(WholphinDispatchers.IO) {
                try {
                    val liked =
                        api.userLibraryApi
                            .getItem(id)
                            .content.userData
                            ?.isFavorite == true
                    if (liked) likedIds.add(id) else likedIds.remove(id)
                    _state.update { if (it.currentAudio?.id == id) it.copy(liked = liked) else it }
                    onMain { mediaSession?.setCustomLayout(MusicSessionButtons.buttons(liked)) }
                } catch (ex: Exception) {
                    Timber.w(ex, "Couldn't check if %s is liked", id)
                }
            }
        }

        /** The heart: like or unlike a song (liked songs go in Liked songs) */
        suspend fun setLiked(
            id: UUID,
            liked: Boolean,
        ) {
            try {
                if (liked) {
                    api.userLibraryApi.markFavoriteItem(id)
                    likedIds.add(id)
                } else {
                    api.userLibraryApi.unmarkFavoriteItem(id)
                    likedIds.remove(id)
                }
                _state.update { if (it.currentAudio?.id == id) it.copy(liked = liked) else it }
                onMain { mediaSession?.setCustomLayout(MusicSessionButtons.buttons(liked)) }
            } catch (ex: Exception) {
                Timber.w(ex, "Couldn't change the like for %s", id)
            }
        }

        fun isLikedNow(id: UUID): Boolean = id in likedIds

        private fun keepGoing(last: AudioItem?) {
            if (extending) return
            val from = sourceId ?: last?.id ?: return
            extending = true
            val gen = generation
            defaultScope.launch(WholphinDispatchers.IO) {
                try {
                    val queued = state.value.queuedIds
                    val items =
                        api.instantMixApi
                            .getInstantMixFromItem(
                                userId = serverRepository.currentUser?.id,
                                itemId = from,
                                limit = 60,
                                fields = DetailItemFields,
                            ).content.items
                            .map { BaseItem(it, false) }
                            .filter { it.id !in queued }
                            .take(40)
                    if (items.isNotEmpty()) {
                        val ac3 = preferAc3Surround()
                        val mediaItems = items.filter { it.type == BaseItemKind.AUDIO }.map { convert(it, ac3) }
                        if (gen != generation) return@launch
                        onMain { if (gen == generation) player.addMediaItems(mediaItems) }
                        updateQueueSize()
                        _state.update { it.copy(thenTitle = "More like ${last?.title ?: "this"}") }
                    }
                    // The mix itself keeps going from its own last song next time
                    if (gen == generation) sourceId = null
                } catch (ex: Exception) {
                    Timber.w(ex, "Couldn't keep the music going")
                } finally {
                    extending = false
                }
            }
        }

        /**
         * Benflix: play a list of songs (an album, playlist, mix or Liked songs) from [startIndex]. Songs the
         * person added to Up next stay, right after the first song (Ben: a new album only replaces "Then plays").
         */
        suspend fun playList(
            items: List<BaseItem>,
            startIndex: Int,
            shuffled: Boolean,
            sourceId: UUID?,
            sourceName: String?,
        ) {
            val songs = items.filter { it.type == BaseItemKind.AUDIO }
            if (songs.isEmpty()) return
            val ac3 = preferAc3Surround()
            val start = startIndex.coerceIn(0, songs.size - 1)
            val ordered =
                if (shuffled) {
                    val first = if (startIndex > 0) songs[start] else songs.random()
                    listOf(first) + (songs - first).shuffled()
                } else {
                    songs.drop(start)
                }
            val mediaItems = ordered.map { convert(it, ac3) }
            generation++
            this.sourceId = sourceId
            withContext(WholphinDispatchers.Main) {
                // The person's own Up next songs that haven't played yet
                val keep =
                    (player.currentMediaItemIndex + 1 until player.mediaItemCount)
                        .map { player.getMediaItemAt(it) }
                        .filter { (it.localConfiguration?.tag as? AudioItem)?.key in added }
                added.retainAll(keep.mapNotNull { (it.localConfiguration?.tag as? AudioItem)?.key }.toSet())
                player.shuffleModeEnabled = false
                player.setMediaItems(listOf(mediaItems.first()) + keep + mediaItems.drop(1), 0, 0L)
                _state.update { it.copy(thenTitle = sourceName) }
                updateQueueSize()
                start()
            }
        }

        /**
         * Benflix on phones: play songs saved in Downloads, even with no internet, starting at [startIndex].
         * Replaces the whole queue (nothing is fetched from Tower).
         */
        suspend fun playDownloaded(
            songs: List<DownloadedItem>,
            startIndex: Int,
        ) {
            val mediaItems =
                songs.drop(startIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0))).mapNotNull { d ->
                    val f = downloadService.file(d)
                    if (!f.exists()) return@mapNotNull null
                    val poster = downloadService.posterFile(d.itemId)
                    val artist = d.subtitle?.substringBefore(" · ")
                    MediaItem
                        .Builder()
                        .setUri(android.net.Uri.fromFile(f))
                        .setMediaId(d.itemId.toServerString())
                        .setTag(
                            AudioItem(
                                id = d.itemId,
                                albumId = d.albumId,
                                artistId = null,
                                title = d.title,
                                albumTitle = d.subtitle?.substringAfter(" · ", "")?.ifBlank { null },
                                artistNames = artist,
                                runtime = d.runtimeMs.takeIf { it > 0 }?.milliseconds,
                                imageUrl =
                                    if (poster.exists()) {
                                        android.net.Uri
                                            .fromFile(poster)
                                            .toString()
                                    } else {
                                        null
                                    },
                                hasLyrics = false,
                            ),
                        ).build()
                }
            if (mediaItems.isEmpty()) return
            generation++
            sourceId = null
            withContext(WholphinDispatchers.Main) {
                added.clear()
                player.shuffleModeEnabled = false
                player.setMediaItems(mediaItems, 0, 0L)
                _state.update { it.copy(thenTitle = "Your downloads") }
                updateQueueSize()
                start()
            }
        }

        /** Add to Up next: after the songs you already added, before the rest of the list */
        suspend fun addToUpNext(song: BaseItem) = addAllToUpNext(listOf(song))

        /** Several songs to Up next at once, in their own order (a whole album or playlist) */
        suspend fun addAllToUpNext(songs: List<BaseItem>) {
            val ac3 = preferAc3Surround()
            val mediaItems = songs.filter { it.type == BaseItemKind.AUDIO }.map { convert(it, ac3) }
            if (mediaItems.isEmpty()) return
            mediaItems.forEach { m -> (m.localConfiguration?.tag as? AudioItem)?.let { added.add(it.key) } }
            onMain {
                if (player.mediaItemCount == 0) {
                    player.setMediaItems(mediaItems)
                    start()
                } else {
                    var at = player.currentMediaItemIndex + 1
                    while (at < player.mediaItemCount &&
                        (player.getMediaItemAt(at).localConfiguration?.tag as? AudioItem)?.key in added
                    ) {
                        at++
                    }
                    player.addMediaItems(at, mediaItems)
                }
            }
            updateQueueSize()
        }

        /** Play next: straight after the playing song */
        suspend fun playNextSong(song: BaseItem) {
            if (song.type != BaseItemKind.AUDIO) return
            val mediaItem = convert(song, preferAc3Surround())
            (mediaItem.localConfiguration?.tag as? AudioItem)?.let { added.add(it.key) }
            onMain {
                if (player.mediaItemCount == 0) {
                    player.setMediaItems(listOf(mediaItem))
                    start()
                } else {
                    player.addMediaItem(player.currentMediaItemIndex + 1, mediaItem)
                }
            }
            updateQueueSize()
        }

        private val mutex = Mutex()
        var mediaSession: MediaSession? = null
            private set
        private var activityTracker: TrackActivityPlaybackListener? = null
        private var websocketJob: Job? = null

        private suspend fun preferAc3Surround() =
            userPreferencesService
                .getCurrent()
                .appPreferences.experimentalPreferences
                .enabled { preferAc3Surround }

        /**
         * Start music playback
         *
         * Sets up the media session, activity tracking, and actual playback
         */
        suspend fun start() {
            if (mediaSession == null) {
                mutex.withLock {
                    if (mediaSession == null) {
                        Timber.i("Starting music MediaSession")
                        mediaSession =
                            MediaSession
                                .Builder(context, player)
                                .setId("benflix-music")
                                .setCallback(
                                    MusicSessionCallback(
                                        onLike = {
                                            state.value.currentAudio?.let { audio ->
                                                defaultScope.launch { setLiked(audio.id, !state.value.liked) }
                                            }
                                        },
                                        onStop = { defaultScope.launch { stop() } },
                                    ),
                                ).setCustomLayout(MusicSessionButtons.buttons(false))
                                .build()
                        // The lock screen controls, and music that keeps going with the screen off
                        onMain { BenflixMusicPlaybackService.start(context) }
                        activityTracker =
                            // Music time counts for My watching (this week's bar and the Music card), not the movie hours
                            TrackActivityPlaybackListener(api, player, watchDoor = "music") {
                                state.value.currentItemId?.let { itemId ->
                                    PlaybackItemState(
                                        itemId = itemId,
                                        playMethod = PlayMethod.DIRECT_PLAY,
//                                        playSessionId = mediaSession?.id,
                                    )
                                }
                            }.also { player.addListener(it) }
                        websocketJob = subscribe()
                    }
                }
            }
            onMain {
                player.prepare()
                player.play()
                // The lock screen controls come back too if Android stopped the service while paused
                BenflixMusicPlaybackService.start(context)
            }
        }

        /**
         * Stop music playback
         */
        suspend fun stop() {
            generation++
            // Clear what shows on screen first: a Stop from the server runs inside the socket job that this cancels
            added.clear()
            sourceId = null
            _state.update { MusicServiceState.EMPTY }
            likedIds.clear()
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { stopNow() }
        }

        private suspend fun stopNow() {
            mutex.withLock {
                Timber.i("Stopping music")
                if (mediaSession == null) {
                    Timber.w("Stopping but no MediaSession")
                }
                mediaSession?.release()
                mediaSession = null
                onMain { BenflixMusicPlaybackService.stop(context) }
                onMain {
                    websocketJob?.cancel()
                    websocketJob = null
                    activityTracker?.let {
                        it.release()
                        player.removeListener(it)
                    }
                    activityTracker = null
                    player.stop()
                    player.setMediaItems(emptyList())
                }
                added.clear()
                sourceId = null
                _state.update {
                    MusicServiceState.EMPTY
                }
            }
        }

        /**
         * Fetches instant mix items, replaces the queue, and begins playback
         */
        suspend fun startInstantMix(itemId: UUID) =
            loading {
                val items =
                    api.instantMixApi
                        .getInstantMixFromItem(
                            userId = serverRepository.currentUser?.id,
                            itemId = itemId,
                            limit = 200,
                            fields = DetailItemFields,
                        ).content.items
                        .map { BaseItem(it, false) }
                setQueue(items, false)
            }

        /**
         * Replace the queue with the given list and starting playing the song as startIndex as soon as its ready
         *
         * Fetches each item in a blocking way and adds to the queue
         */
        suspend fun setQueue(
            items: BlockingList<BaseItem?>,
            startIndex: Int,
            shuffled: Boolean,
        ) = withContext(WholphinDispatchers.IO) {
            Timber.d("setQueue: %s items, startIndex=%s, shuffled=%s", items.size, startIndex, shuffled)
            withContext(WholphinDispatchers.Main) {
                player.setMediaItems(emptyList())
                player.shuffleModeEnabled = shuffled
            }
            start()
            addAllToQueue(items, startIndex)
        }

        /**
         * Replace the queue with the given items
         */
        suspend fun setQueue(
            items: List<BaseItem>,
            shuffled: Boolean,
        ) {
            Timber.d("setQueue: %s items, shuffled=%s", items.size, shuffled)
            val preferAc3Surround = preferAc3Surround()
            val mediaItems =
                items
                    .filter { it.type == BaseItemKind.AUDIO }
                    .map { convert(it, preferAc3Surround) }
            withContext(WholphinDispatchers.Main) {
                player.setMediaItems(mediaItems)
                player.shuffleModeEnabled = shuffled
                updateQueueSize()
                start()
            }
        }

        /**
         * Add an item to the specified index of the queue. If no index specified, it will be added to the end.
         */
        suspend fun addToQueue(
            item: BaseItem,
            index: Int? = null,
        ) {
            if (item.type == BaseItemKind.AUDIO) {
                val preferAc3Surround = preferAc3Surround()
                val mediaItem = convert(item, preferAc3Surround)
                withContext(WholphinDispatchers.Main) {
                    if (index != null) {
                        player.addMediaItem(index, mediaItem)
                    } else {
                        player.addMediaItem(mediaItem)
                    }
                    updateQueueSize()
                    if (player.mediaItemCount == 1) {
                        // Start playing if this was the first time added
                        start()
                    }
                }
            } else {
                Timber.w("Tried to add non-audio type %s: %s", item.type, item.id)
            }
        }

        /**
         *  Add all the items in teh list to end of the queue
         *
         *  @param startIndex The index to start from within the source list
         */
        suspend fun addAllToQueue(
            list: BlockingList<BaseItem?>,
            startIndex: Int,
        ) = loading {
            val preferAc3Surround = preferAc3Surround()
            var remaining = startIndex
            list.indices
                .chunked(25)
                .forEach {
                    val mediaItems =
                        it.mapNotNull {
                            if (remaining == 0) {
                                list
                                    .getBlocking(it)
                                    ?.takeIf { it.type == BaseItemKind.AUDIO }
                                    ?.let { convert(it, preferAc3Surround) }
                            } else {
                                Timber.v("Skipping $remaining")
                                remaining--
                                null
                            }
                        }
                    onMain { player.addMediaItems(mediaItems) }
                }
            updateQueueSize()
            start()
        }

        /**
         * Converts a [BaseItem] into a [MediaItem] setting an [AudioItem] as its tag
         */
        private fun convert(
            audio: BaseItem,
            preferAc3Surround: Boolean,
        ): MediaItem {
            val needsAc3Transcode =
                preferAc3Surround &&
                    audio.data.mediaSources
                        ?.firstOrNull()
                        ?.mediaStreams
                        ?.any { stream ->
                            stream.type == MediaStreamType.AUDIO &&
                                stream.channels.gt(2) &&
                                stream.codec != Codec.Audio.AC3
                        } == true
            val url =
                if (needsAc3Transcode) {
                    api.universalAudioApi.getUniversalAudioStreamUrl(
                        itemId = audio.id,
                        container = listOf("mka"),
                        transcodingContainer = "mka",
                        maxAudioChannels = 6,
                        transcodingAudioChannels = 6,
                        audioCodec = Codec.Audio.AC3,
                    )
                } else {
                    api.universalAudioApi.getUniversalAudioStreamUrl(
                        itemId = audio.id,
                        container = audioFormats,
                    )
                }
            // Benflix: a song saved on this phone plays from the saved copy (works with no internet)
            val local = downloadService.localFile(audio.id)
            Timber.i("url=%s local=%s", url, local != null)
            val imageUrl =
                audio.data.albumId?.let { albumId ->
                    imageUrlService.getItemImageUrl(
                        itemId = albumId,
                        imageType = ImageType.PRIMARY,
                        tag = audio.data.albumPrimaryImageTag,
                    )
                }
            return MediaItem
                .Builder()
                .setUri(local?.let { android.net.Uri.fromFile(it) } ?: android.net.Uri.parse(url))
                .setMediaId(audio.id.toServerString())
                .setTag(AudioItem.from(audio, imageUrl))
                .build()
        }

        /**
         * Updates the state for when the queue changes
         */
        private suspend fun updateQueueSize() {
//            val ids =
//                withContext(WholphinDispatchers.Default) {
//                    (0..<player.mediaItemCount).map { player.getMediaItemAt(it).mediaId.toUUID() }
//                }
            val timeline = onMain { player.currentTimeline }
            val window = Timeline.Window()
            val ids =
                (0..<timeline.windowCount)
                    .map {
                        timeline.getWindow(it, window)
                        window.mediaItem.mediaId.toUUID()
                    }.toSet()
            withContext(WholphinDispatchers.Main) {
                _state.update {
                    it.copy(
                        queueVersion = it.queueVersion + 1,
                        queueSize = player.mediaItemCount,
                        queuedIds = ids,
                    )
                }
            }
        }

        /**
         * Move an item within the queue
         */
        suspend fun moveQueue(
            index: Int,
            direction: MoveDirection,
        ) = withContext(WholphinDispatchers.Main) {
            player.moveMediaItem(index, if (direction == MoveDirection.UP) index - 1 else index + 1)
            updateQueueSize()
        }

        /**
         * Move an item within the queue
         */
        suspend fun moveQueue(
            index: Int,
            newIndex: Int,
        ) = withContext(WholphinDispatchers.Main) {
            player.moveMediaItem(index, newIndex)
            updateQueueSize()
        }

        /**
         * Start playback at the given index of the queue
         */
        suspend fun playIndex(index: Int) {
            onMain {
                player.seekTo(index, 0L)
                player.play()
            }
            // MusicPlayerListener will update state
        }

        /**
         * Play this item next after the current, ie add the item as the next index in the queue
         */
        suspend fun playNext(song: BaseItem) {
            val mediaItem = convert(song, preferAc3Surround())
            onMain {
                player.addMediaItem(state.value.currentIndex + 1, mediaItem)
                if (player.mediaItemCount == 1) {
                    start()
                }
            }
            updateQueueSize()
        }

        /**
         * From the item at the given index from the queue
         */
        suspend fun removeFromQueue(index: Int) {
            onMain { player.removeMediaItem(index) }
            updateQueueSize()
        }

        private suspend fun <T> loading(block: suspend () -> T): T {
            _state.update { it.copy(loadingState = LoadingState.Loading) }
            val result = block.invoke()
            _state.update { it.copy(loadingState = LoadingState.Success) }
            return result
        }

        /**
         * Subscribes to the server websocket to receive playback commands
         */
        private fun subscribe(): Job =
            api.webSocket
                .subscribe<PlaystateMessage>()
                .onEach { message ->
                    message.data?.let {
                        withContext(WholphinDispatchers.Main) {
                            when (it.command) {
                                PlaystateCommand.STOP -> {
                                    stop()
                                }

                                PlaystateCommand.PAUSE -> {
                                    player.pause()
                                }

                                PlaystateCommand.UNPAUSE -> {
                                    player.play()
                                }

                                PlaystateCommand.NEXT_TRACK -> {
                                    player.seekToNext()
                                }

                                PlaystateCommand.PREVIOUS_TRACK -> {
                                    player.seekToPrevious()
                                }

                                PlaystateCommand.SEEK -> {
                                    it.seekPositionTicks?.ticks?.let {
                                        player.seekTo(
                                            it.inWholeMilliseconds,
                                        )
                                    }
                                }

                                PlaystateCommand.REWIND -> {
                                    player.seekBack(10.seconds)
                                }

                                PlaystateCommand.FAST_FORWARD -> {
                                    player.seekForward(30.seconds)
                                }

                                PlaystateCommand.PLAY_PAUSE -> {
                                    if (player.isPlaying) player.pause() else player.play()
                                }
                            }
                        }
                    }
                }.catch { ex ->
                    Timber.e(ex, "Error in websocket subscription")
                }.launchIn(defaultScope)
    }

@Stable
data class MusicServiceState(
    val queueVersion: Long,
    val queueSize: Int,
    val currentIndex: Int,
    val currentItemId: UUID?,
    val status: NowPlayingStatus,
    val currentItemTitle: String?,
    val loadingState: LoadingState = LoadingState.Pending,
    val queuedIds: Set<UUID>,
    /** Benflix: the song playing now */
    val currentAudio: AudioItem? = null,
    /** Benflix: whether the playing song is in Liked songs */
    val liked: Boolean = false,
    /** Benflix: the name of what plays after your Up next songs, like "Night Drive" */
    val thenTitle: String? = null,
) {
    companion object {
        val EMPTY =
            MusicServiceState(
                0L,
                0,
                0,
                null,
                NowPlayingStatus.IDLE,
                null,
                LoadingState.Pending,
                emptySet(),
            )
    }
}

enum class NowPlayingStatus {
    PLAYING,
    PAUSED,
    IDLE,
}

/**
 * Listens to [Player] events and updates the [StateFlow]
 */
private class MusicPlayerListener(
    private val player: Player,
    private val state: MutableStateFlow<MusicServiceState>,
) : Player.Listener {
    init {
        Timber.v("MusicPlayerListener init")
        state.update {
            it.copy(
                queueSize = player.mediaItemCount,
                currentIndex = player.currentMediaItemIndex,
                status = if (player.isPlaying) NowPlayingStatus.PLAYING else NowPlayingStatus.IDLE,
            )
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        Timber.v("MusicPlayerListener onIsPlayingChanged")
        state.update {
            it.copy(
                status = if (isPlaying) NowPlayingStatus.PLAYING else NowPlayingStatus.PAUSED,
            )
        }
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        Timber.v("MusicPlayerListener onMediaItemTransition")
        updateCurrentIndex()
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
//        Timber.v("MusicPlayerListener onTimelineChanged")
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
            updateCurrentIndex()
        }
    }

    private fun updateCurrentIndex() {
        state.update { state ->
            player.currentMediaItemIndex.takeIf { it >= 0 }?.let { currentMediaItemIndex ->
                if (currentMediaItemIndex in (0..<player.mediaItemCount)) {
                    val item =
                        player.getMediaItemAt(currentMediaItemIndex).localConfiguration?.tag as? AudioItem
                    state.copy(
                        currentIndex = currentMediaItemIndex,
                        currentItemId = player.getMediaItemAt(currentMediaItemIndex).mediaId.toUUIDOrNull(),
                        currentItemTitle = item?.title,
                    )
                } else {
                    state
                }
            } ?: state
        }
    }
}

/**
 * Remember the queue currently playing
 *
 * @see MusicServiceState
 */
@Composable
fun rememberQueue(
    player: Player,
    queueVersion: Long,
    queueSize: Int,
): List<AudioItem> =
    remember(queueVersion, queueSize) {
        object : AbstractList<AudioItem>() {
            override val size: Int
                get() = player.mediaItemCount

            override fun get(index: Int): AudioItem = player.getMediaItemAt(index).localConfiguration?.tag as AudioItem
        }
    }
