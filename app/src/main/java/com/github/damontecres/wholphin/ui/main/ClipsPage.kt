package com.github.damontecres.wholphin.ui.main

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.DiskCache
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.KidsMode
import com.github.damontecres.wholphin.services.LiteMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PreviewPlayers
import com.github.damontecres.wholphin.services.SpeedStats
import com.github.damontecres.wholphin.ui.HomeItemFields
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/** One clip in the feed: a movie or show and its downloaded (Trailarr) trailer */
data class Clip(
    val item: BaseItem,
    val trailerId: UUID,
    val favorite: Boolean,
)

@Serializable
private data class SavedClip(
    val item: BaseItem,
    @Serializable(with = UUIDSerializer::class) val trailerId: UUID,
)

@Serializable
private data class SavedClips(
    val clips: List<SavedClip>,
)

data class ClipsState(
    val loading: Boolean = true,
    val clips: List<Clip> = emptyList(),
    // Clips always opens with sound on (the speaker button can still mute it)
    val muted: Boolean = false,
)

@HiltViewModel
class ClipsViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val navigationManager: NavigationManager,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val diskCache: DiskCache,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ClipsState())
        val state: StateFlow<ClipsState> = _state

        // The player for the clip on screen. Compose state, so the screen switches to it when it changes
        var player by mutableStateOf<ExoPlayer?>(null)
            private set
        private var playingId: UUID? = null

        // A second player that quietly loads the next clip while you watch this one
        private var nextPlayer: ExoPlayer? = null
        private var nextId: UUID? = null

        init {
            viewModelScope.launch(ExceptionHandler()) { load() }
        }

        private fun cacheName(userId: UUID?) = "clips-$userId-${KidsMode.level}-v1"

        private suspend fun load() {
            val userId = serverRepository.currentUser?.id
            val loadStart = System.currentTimeMillis()
            // Trailers found last time are saved on the device: start one of those right away,
            // then look for new ones in the background
            val saved =
                withContext(WholphinDispatchers.IO) {
                    diskCache.read(cacheName(userId), SavedClips.serializer(), CLIPS_CACHE_MAX_AGE_MS)
                }?.clips.orEmpty()
            if (saved.isNotEmpty()) {
                _state.update {
                    it.copy(
                        loading = false,
                        clips =
                            saved.shuffled().map { c ->
                                Clip(
                                    c.item,
                                    c.trailerId,
                                    c.item.data.userData
                                        ?.isFavorite == true,
                                )
                            },
                    )
                }
                SpeedStats.clipsFirstMs = System.currentTimeMillis() - loadStart
            }
            try {
                val items = withContext(WholphinDispatchers.IO) { findItemsWithTrailers(userId) }
                val known =
                    _state.value.clips
                        .map { it.item.id }
                        .toSet()
                val fresh = items.filter { it.id !in known }
                // Find the trailers for the first couple right away so the first clip starts fast,
                // then the rest in the background while you watch
                val first = if (known.isEmpty()) fresh.take(CLIPS_FIRST) else emptyList()
                val rest = fresh.drop(first.size)
                if (first.isNotEmpty()) {
                    val firstClips = withContext(WholphinDispatchers.IO) { resolveClips(first) }
                    _state.update { it.copy(loading = rest.isNotEmpty() && firstClips.isEmpty(), clips = it.clips + firstClips) }
                    SpeedStats.clipsFirstMs = System.currentTimeMillis() - loadStart
                }
                if (rest.isNotEmpty()) {
                    val restClips = withContext(WholphinDispatchers.IO) { resolveClips(rest) }
                    _state.update { it.copy(loading = false, clips = it.clips + restClips) }
                }
                _state.update { it.copy(loading = false) }
                // Remember what was found (new ones first) for next time
                val pool =
                    (_state.value.clips.map { SavedClip(it.item, it.trailerId) } + saved)
                        .distinctBy { it.trailerId }
                        .take(CLIPS_CACHE_MAX)
                withContext(WholphinDispatchers.IO) {
                    diskCache.write(cacheName(userId), SavedClips.serializer(), SavedClips(pool))
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Error loading clips")
                _state.update { it.copy(loading = false) }
            }
        }

        private var loadingMore = false
        private var emptyBatches = 0

        /**
         * Call as you move through the trailers: when only a few are left, quietly find another
         * batch, so the shuffle keeps going through your whole library
         */
        fun loadMoreIfNear(index: Int) {
            val clips = _state.value.clips
            if (loadingMore || _state.value.loading || emptyBatches >= 2 || index < clips.size - CLIPS_MORE_WHEN_LEFT) return
            loadingMore = true
            viewModelScope.launch(ExceptionHandler()) {
                try {
                    val userId = serverRepository.currentUser?.id
                    val known =
                        _state.value.clips
                            .map { it.item.id }
                            .toSet()
                    val more =
                        withContext(WholphinDispatchers.IO) {
                            resolveClips(findItemsWithTrailers(userId).filter { it.id !in known })
                        }
                    if (more.isEmpty()) emptyBatches++ else emptyBatches = 0
                    _state.update { st ->
                        val have = st.clips.map { it.trailerId }.toSet()
                        st.copy(clips = st.clips + more.filter { it.trailerId !in have })
                    }
                } finally {
                    loadingMore = false
                }
            }
        }

        /**
         * Random movies & shows that have a trailer. The server can be slow to answer the very
         * first time (it has to check the whole library), so try again a couple of times, and if it
         * still finds nothing, just take random titles and look for their trailers one by one.
         */
        private suspend fun findItemsWithTrailers(userId: UUID?): List<BaseItemDto> {
            fun request(onlyWithTrailers: Boolean) =
                GetItemsRequest(
                    userId = userId,
                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                    recursive = true,
                    hasTrailer = if (onlyWithTrailers) true else null,
                    sortBy = listOf(ItemSortBy.RANDOM),
                    limit = CLIPS_FETCH,
                    fields = HomeItemFields,
                )
            repeat(CLIPS_TRIES) { attempt ->
                try {
                    val found =
                        api.itemsApi
                            .getItems(request(true))
                            .content.items
                            .filter { (it.localTrailerCount ?: 1) > 0 }
                    if (found.isNotEmpty()) return found
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.w(ex, "Looking for trailers failed (try %d)", attempt + 1)
                }
                delay(CLIPS_RETRY_MS * (attempt + 1))
            }
            return api.itemsApi
                .getItems(request(false))
                .content.items
                .filter { (it.localTrailerCount ?: 1) > 0 }
        }

        /** Look up the downloaded trailer of each item, a few at a time */
        private suspend fun resolveClips(items: List<BaseItemDto>): List<Clip> =
            coroutineScope {
                val permits = Semaphore(CLIPS_PARALLEL)
                items
                    .map { dto ->
                        async {
                            permits.withPermit {
                                try {
                                    api.userLibraryApi
                                        .getLocalTrailers(dto.id)
                                        .content
                                        .firstOrNull()
                                        ?.let { trailer ->
                                            Clip(BaseItem(dto), trailer.id, dto.userData?.isFavorite == true)
                                        }
                                } catch (ex: CancellationException) {
                                    throw ex
                                } catch (ex: Exception) {
                                    null
                                }
                            }
                        }
                    }.awaitAll()
                    .filterNotNull()
            }

        private val boosts = mutableListOf<LoudnessEnhancer?>()

        private fun boost(audioSessionId: Int): LoudnessEnhancer? =
            try {
                LoudnessEnhancer(audioSessionId).apply {
                    setTargetGain(CLIPS_BOOST_MB)
                    enabled = true
                }
            } catch (ex: Exception) {
                Timber.w(ex, "No loudness boost for trailers")
                null
            }

        private fun newPlayer(): ExoPlayer =
            ExoPlayer
                .Builder(context)
                // Start playing after half a second of video instead of the usual 2.5 seconds
                .setLoadControl(
                    DefaultLoadControl
                        .Builder()
                        .setBufferDurationsMs(
                            CLIPS_MIN_BUFFER_MS,
                            CLIPS_MAX_BUFFER_MS,
                            CLIPS_START_BUFFER_MS,
                            CLIPS_REBUFFER_MS,
                        ).build(),
                ).build()
                .also { p ->
                    p.repeatMode = Player.REPEAT_MODE_ONE
                    // Trailers are often mixed for surround sound and come out quiet on TV speakers:
                    // give them a small boost so they play at a normal level
                    boosts += boost(p.audioSessionId)
                }

        private fun mediaItem(clip: Clip): MediaItem =
            MediaItem.fromUri(api.videosApi.getVideoStreamUrl(itemId = clip.trailerId, static = true))

        fun play(clip: Clip?) {
            if (clip == null) return
            if (playingId != clip.trailerId) {
                val ready = nextPlayer
                if (ready != null && nextId == clip.trailerId) {
                    // The next clip was already loading in the background: swap it in
                    val old = player
                    player = ready
                    playingId = nextId
                    nextPlayer = old
                    nextId = null
                    old?.let {
                        it.playWhenReady = false
                        it.stop()
                        it.clearMediaItems()
                    }
                } else {
                    val p = player ?: newPlayer().also { player = it }
                    playingId = clip.trailerId
                    p.setMediaItem(mediaItem(clip))
                    p.prepare()
                }
            }
            player?.let {
                it.volume = if (_state.value.muted) 0f else 1f
                it.playWhenReady = true
            }
        }

        /** Quietly start loading [clip] so it plays instantly when you swipe to it */
        fun preload(clip: Clip?) {
            if (clip == null || clip.trailerId == playingId || clip.trailerId == nextId || LiteMode.isOn(context)) return
            val p = nextPlayer ?: newPlayer().also { nextPlayer = it }
            nextId = clip.trailerId
            p.playWhenReady = false
            p.volume = 0f
            p.setMediaItem(mediaItem(clip))
            p.prepare()
        }

        fun pause() {
            player?.playWhenReady = false
        }

        /** Let go of the background player (TV Trailer shuffle plays one trailer at a time) */
        fun dropPreload() {
            nextPlayer?.release()
            nextPlayer = null
            nextId = null
        }

        fun toggleMute() {
            val muted = !_state.value.muted
            _state.update { it.copy(muted = muted) }
            player?.volume = if (muted) 0f else 1f
        }

        fun playMovie(clip: Clip) {
            pause()
            // Movies start right away; for a show, its page lets you pick where to start
            navigationManager.navigateTo(
                if (clip.item.type == BaseItemKind.MOVIE) Destination.Playback(clip.item) else clip.item.destination(),
            )
        }

        fun openDetails(clip: Clip) {
            pause()
            navigationManager.navigateTo(clip.item.destination())
        }

        fun toggleFavorite(clip: Clip) {
            viewModelScope.launch(ExceptionHandler()) {
                val favorite = !clip.favorite
                withContext(WholphinDispatchers.IO) { favoriteWatchManager.setFavorite(clip.item.id, favorite) }
                _state.update { s ->
                    s.copy(clips = s.clips.map { if (it.trailerId == clip.trailerId) it.copy(favorite = favorite) else it })
                }
            }
        }

        /** Benflix: let go of the video decoders when a movie starts, so it doesn't play slowly */
        private fun freePlayers() {
            boosts.forEach { it?.release() }
            boosts.clear()
            player?.release()
            player = null
            playingId = null
            nextPlayer?.release()
            nextPlayer = null
            nextId = null
        }

        private val unregisterFree = PreviewPlayers.register { freePlayers() }

        override fun onCleared() {
            unregisterFree()
            boosts.forEach { it?.release() }
            boosts.clear()
            player?.release()
            player = null
            nextPlayer?.release()
            nextPlayer = null
        }
    }

private const val CLIPS_FETCH = 80
private const val CLIPS_TRIES = 3
private const val CLIPS_RETRY_MS = 1_500L

// +5 dB
private const val CLIPS_BOOST_MB = 500

// A quick flick this fast moves to the next clip, or a slow drag past a quarter of the screen
private const val CLIPS_FLING_DP_PER_SEC = 300
private const val CLIPS_SWIPE_FRACTION = .25f
private const val CLIPS_FIRST = 2

// How many trailers to remember on the device, and when to fetch more (trailers left to go)
private const val CLIPS_CACHE_MAX = 600
private const val CLIPS_MORE_WHEN_LEFT = 10
private const val CLIPS_CACHE_MAX_AGE_MS = 14L * 24 * 3_600_000L
private const val CLIPS_PARALLEL = 8
private const val CLIPS_MIN_BUFFER_MS = 8_000
private const val CLIPS_MAX_BUFFER_MS = 30_000
private const val CLIPS_START_BUFFER_MS = 500
private const val CLIPS_REBUFFER_MS = 1_500
