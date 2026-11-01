package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.LiteMode
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SpeedStats
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.HomeItemFields
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.TitleOrLogo
import com.github.damontecres.wholphin.ui.components.rememberLogoUrl
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/** One clip in the feed: a movie or show and its downloaded (Trailarr) trailer */
data class Clip(
    val item: BaseItem,
    val trailerId: UUID,
    val favorite: Boolean,
)

data class ClipsState(
    val loading: Boolean = true,
    val clips: List<Clip> = emptyList(),
    // Clips always opens with sound on (the speaker button can still mute it)
    val muted: Boolean = false,
)

/**
 * Benflix on phones: Clips, a full-screen feed of trailers from your own library.
 * Swipe up for the next one. Each trailer plays as soon as it's on screen.
 */
@Composable
fun ClipsPage(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClipsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    BackHandler(onBack = onClose)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        if (state.clips.isEmpty()) {
            Text(
                text = stringResource(if (state.loading) R.string.loading else R.string.clips_none),
                color = Color.White,
                fontSize = 16.sp,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
            )
            return@Box
        }
        val pagerState = rememberPagerState { state.clips.size }
        val current = pagerState.currentPage
        // Play the clip that's on screen; stop while the app is in the background
        LifecycleResumeEffect(current, state.clips.size) {
            viewModel.play(state.clips.getOrNull(current))
            // Start loading the next trailer now, so swiping to it plays right away
            viewModel.preload(state.clips.getOrNull(current + 1))
            onPauseOrDispose { viewModel.pause() }
        }
        DisposableEffect(Unit) { onDispose { viewModel.pause() } }
        VerticalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val clip = state.clips[page]
            ClipCard(
                clip = clip,
                player = if (page == current) viewModel.player else null,
                muted = state.muted,
                onToggleMute = viewModel::toggleMute,
                onPlay = { viewModel.playMovie(clip) },
                onDetails = { viewModel.openDetails(clip) },
                onFavorite = { viewModel.toggleFavorite(clip) },
            )
        }
    }
}

@Composable
private fun ClipCard(
    clip: Clip,
    player: Player?,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    onFavorite: () -> Unit,
) {
    val imageUrlService = LocalImageUrlService.current
    val backdrop =
        remember(clip.item.id) {
            imageUrlService.getItemImageUrl(clip.item, ImageType.BACKDROP, fillWidth = 1280)
        }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clickable(onClick = onToggleMute),
    ) {
        // The poster picture shows until the trailer starts
        AsyncImage(
            model = backdrop,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (player != null) {
            PlayerSurface(
                player = player,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .align(Alignment.Center),
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = .9f),
                        ),
                    ),
        )
        // Sound on/off
        Text(
            text = stringResource(if (muted) R.string.fa_volume_xmark else R.string.fa_volume_high),
            fontFamily = FontAwesome,
            fontSize = 20.sp,
            color = Color.White,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = .5f), CircleShape)
                    .padding(10.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            TitleOrLogo(
                title = clip.item.title,
                logoImageUrl = rememberLogoUrl(clip.item),
                showLogo = true,
                logoHeight = 64.dp,
                logoMaxWidth = 260.dp,
            )
            clip.item.data.overview?.let {
                Text(
                    text = it,
                    color = Color.White.copy(alpha = .85f),
                    fontSize = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ClipButton(stringResource(R.string.fa_play), stringResource(R.string.play), true, onPlay)
                ClipButton(
                    stringResource(if (clip.favorite) R.string.fa_check else R.string.fa_plus),
                    stringResource(R.string.my_list),
                    false,
                    onFavorite,
                )
                ClipButton(stringResource(R.string.fa_circle_info), stringResource(R.string.more_info), false, onDetails)
            }
        }
    }
}

@Composable
private fun ClipButton(
    icon: String,
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .background(
                    if (primary) Color.White else Color(0x99787882),
                    RoundedCornerShape(6.dp),
                ).clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        val color = if (primary) Color.Black else Color.White
        Text(text = icon, fontFamily = FontAwesome, fontSize = 14.sp, color = color)
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@HiltViewModel
class ClipsViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val navigationManager: NavigationManager,
        private val favoriteWatchManager: FavoriteWatchManager,
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

        private suspend fun load() {
            val userId = serverRepository.currentUser?.id
            val loadStart = System.currentTimeMillis()
            try {
                val items =
                    withContext(WholphinDispatchers.IO) {
                        api.itemsApi
                            .getItems(
                                GetItemsRequest(
                                    userId = userId,
                                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                    recursive = true,
                                    hasTrailer = true,
                                    sortBy = listOf(ItemSortBy.RANDOM),
                                    limit = CLIPS_FETCH,
                                    fields = HomeItemFields,
                                ),
                            ).content.items
                            .filter { (it.localTrailerCount ?: 1) > 0 }
                    }
                // Find the trailers for the first few right away so the first clip starts fast,
                // then the rest in the background while you watch
                val first = items.take(CLIPS_FIRST)
                val rest = items.drop(CLIPS_FIRST)
                val firstClips = withContext(WholphinDispatchers.IO) { resolveClips(first) }
                _state.update { it.copy(loading = rest.isNotEmpty() && firstClips.isEmpty(), clips = firstClips) }
                SpeedStats.clipsFirstMs = System.currentTimeMillis() - loadStart
                if (rest.isNotEmpty()) {
                    val restClips = withContext(WholphinDispatchers.IO) { resolveClips(rest) }
                    _state.update { it.copy(loading = false, clips = it.clips + restClips) }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Error loading clips")
                _state.update { it.copy(loading = false) }
            }
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
                .also { it.repeatMode = Player.REPEAT_MODE_ONE }

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

        override fun onCleared() {
            player?.release()
            player = null
            nextPlayer?.release()
            nextPlayer = null
        }
    }

private const val CLIPS_FETCH = 80
private const val CLIPS_FIRST = 4
private const val CLIPS_PARALLEL = 8
private const val CLIPS_MIN_BUFFER_MS = 8_000
private const val CLIPS_MAX_BUFFER_MS = 30_000
private const val CLIPS_START_BUFFER_MS = 500
private const val CLIPS_REBUFFER_MS = 1_500
