package com.github.damontecres.wholphin.ui.playback

import androidx.annotation.OptIn
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.github.damontecres.wholphin.preferences.enabled
import com.github.damontecres.wholphin.ui.gt
import com.github.damontecres.wholphin.ui.onMain
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.checkForSupport
import com.github.damontecres.wholphin.util.profile.Codec
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber

/**
 * Benflix: the sound didn't come back by itself, so ask the server to send the sound in a
 * simple format (AAC). With [fullConvert] the picture is converted too.
 */
internal fun PlaybackViewModel.convertSoundOnServer(fullConvert: Boolean) {
    viewModelScope.launch(WholphinDispatchers.Main + ExceptionHandler()) {
        val currentPlayback = state.value.currentPlayback ?: return@launch
        Timber.w("No sound, asking the server to convert it (fullConvert=%s)", fullConvert)
        changeStreams(
            item = currentItem.item,
            sourceId = currentPlayback.mediaSourceInfo.id,
            audioIndex = currentPlayback.audioIndex.takeIf { it >= 0 },
            subtitleIndex = currentPlayback.subtitleIndex,
            positionMs = player.currentPosition,
            enableDirectPlay = false,
            enableDirectStream = !fullConvert,
            allowAudioStreamCopy = false,
        )
        withContext(WholphinDispatchers.Main) {
            player.prepare()
            player.play()
        }
        soundWatchdog.check("sound converted")
    }
}

/**
 * If direct playing, can try to switch tracks without playback restarting
 * Except for external subtitles
 */
@OptIn(UnstableApi::class)
internal suspend fun PlaybackViewModel.changeStreamsDirectPlay(
    currentPlayback: CurrentPlayback,
    audioIndex: Int?,
    subtitleIndex: Int?,
): Boolean =
    withContext(WholphinDispatchers.IO) {
        Timber.v("changeStreams direct play")

        // TODO Better way to handle unsupported types in general is needed
        // This is a workaround for switching to a non AC3 track when the user wants audio transcoded to AC3
        if (preferences.appPreferences.experimentalPreferences.enabled { preferAc3Surround } && audioIndex != null) {
            currentPlayback.mediaSourceInfo.mediaStreams
                .orEmpty()
                .firstOrNull { it.index == audioIndex }
                ?.let {
                    if (it.channels.gt(2) && it.codec != Codec.Audio.AC3) {
                        // User wants to transcode audio into AC3
                        return@withContext false
                    }
                }
        }

        val source = currentPlayback.mediaSourceInfo
        val externalSubtitle = source.findExternalSubtitle(subtitleIndex)

        if (externalSubtitle == null) {
            val result =
                withContext(WholphinDispatchers.Main) {
                    TrackSelectionUtils.createTrackSelections(
                        onMain { player.trackSelectionParameters },
                        onMain { player.currentTracks },
                        audioIndex,
                        subtitleIndex,
                        source,
                    )
                }
            if (result.bothSelected) {
                onMain { player.trackSelectionParameters = result.trackSelectionParameters }
                Timber.d("Changes tracks audio=$audioIndex, subtitle=$subtitleIndex")
                mutableState.update {
                    it.copy(
                        currentPlayback =
                            (it.currentPlayback ?: currentPlayback).copy(
                                tracks = checkForSupport(onMain { player.currentTracks }),
                            ),
                    )
                }
                loadSubtitleDelay()
                return@withContext true
            }
        } else {
            Timber.v("changeStreams direct play, external subtitle was requested")
        }
        return@withContext false
    }

internal suspend fun PlaybackViewModel.saveTrackSelection(
    trackIndex: Int,
    type: MediaStreamType,
) {
    val itemPlayback =
        itemPlaybackRepository.saveTrackSelection(
            item = currentItem.item,
            itemPlayback = state.value.currentItemPlayback,
            trackIndex = trackIndex,
            type = type,
        )
    mutableState.update {
        it.copy(
            currentItemPlayback = itemPlayback,
        )
    }
}
