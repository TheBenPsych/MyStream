package com.github.damontecres.wholphin.ui.playback

import android.media.MediaCodecList
import android.os.Build
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.Util
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.seekBack
import com.github.damontecres.wholphin.ui.seekForward
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.PlaystateRequest
import org.jellyfin.sdk.model.api.TrickplayInfo
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal suspend fun PlaybackViewModel.prefetchTrickplay(
    duration: Duration,
    trickplayInfo: TrickplayInfo,
    mediaSourceId: UUID?,
) {
    val tilesPerImage = trickplayInfo.tileWidth * trickplayInfo.tileHeight
    val totalCount =
        (duration.inWholeMilliseconds / trickplayInfo.interval).toInt() / tilesPerImage + 1
    (0..<totalCount).forEach {
        val url = getTrickplayUrl(it, trickplayInfo, mediaSourceId)
        context.imageLoader.enqueue(
            ImageRequest
                .Builder(context)
                .data(url)
                .size(Size.ORIGINAL)
                .build(),
        )
    }
}

/**
 * Cancels listening for segments and clears current segment state
 */
internal fun PlaybackViewModel.resetSegmentState() {
    segmentJob?.cancel()
    autoSkippedSegments.clear()
    outroShownSegments.clear()
    mutableState.update { it.copy(currentSegment = null) }
}

internal fun PlaybackViewModel.listenForTranscodeReason(): Job =
    viewModelScope.launchIO {
        state.map { it.currentPlayback }.collectLatest {
            if (it != null && it.playMethod == PlayMethod.TRANSCODE && it.transcodeInfo == null) {
                try {
                    var transcodeInfo = it.transcodeInfo
                    while (isActive && transcodeInfo == null) {
                        delay(2.seconds)
                        transcodeInfo =
                            api.sessionApi
                                .getSessions(deviceId = deviceInfo.id)
                                .content
                                .firstOrNull()
                                ?.transcodingInfo
                        if (transcodeInfo == null) delay(3.seconds)
                    }
                    Timber.v("transcodeInfo=$transcodeInfo")
                    updateCurrentPlayback { current ->
                        current?.copy(transcodeInfo = transcodeInfo)
                    }
                } catch (ex: Exception) {
                    if (ex !is CancellationException) {
                        Timber.w(ex, "Exception trying to get session info")
                        updateCurrentPlayback { current ->
                            current?.copy(transcodeInfo = null)
                        }
                    }
                }
            }
        }
    }

internal suspend fun PlaybackViewModel.handlePlaystateRequest(request: PlaystateRequest) =
    withContext(WholphinDispatchers.Main) {
        when (request.command) {
            PlaystateCommand.STOP -> {
                release()
                navigationManager.goBack()
            }

            PlaystateCommand.PAUSE -> {
                player.pause()
            }

            PlaystateCommand.UNPAUSE -> {
                player.play()
            }

            PlaystateCommand.NEXT_TRACK -> {
                playNextUp()
            }

            PlaystateCommand.PREVIOUS_TRACK -> {
                playPrevious()
            }

            PlaystateCommand.SEEK -> {
                request.seekPositionTicks?.ticks?.let {
                    player.seekTo(it.inWholeMilliseconds)
                }
            }

            PlaystateCommand.REWIND -> {
                player.seekBack(
                    preferences.appPreferences.playbackPreferences.skipBackMs.milliseconds,
                )
            }

            PlaystateCommand.FAST_FORWARD -> {
                player.seekForward(
                    preferences.appPreferences.playbackPreferences.skipForwardMs.milliseconds,
                )
            }

            PlaystateCommand.PLAY_PAUSE -> {
                Util.handlePlayPauseButtonAction(player)
            }
        }
    }

internal fun PlaybackViewModel.updateDecoder(
    decoderName: String,
    type: MediaType,
) {
    viewModelScope.launchDefault {
        val codecInfo =
            MediaCodecList(MediaCodecList.ALL_CODECS)
                .codecInfos
                .firstOrNull { !it.isEncoder && it.name == decoderName }
        val decoderString =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (codecInfo?.isHardwareAccelerated == true) {
                    "$decoderName (HW)"
                } else {
                    decoderName
                }
            } else {
                decoderName
            }
        updateCurrentPlayback {
            when (type) {
                MediaType.VIDEO -> it?.copy(videoDecoder = decoderString)
                MediaType.AUDIO -> it?.copy(audioDecoder = decoderString)
                else -> throw IllegalArgumentException("Unsupported type: $type")
            }
        }
    }
}
