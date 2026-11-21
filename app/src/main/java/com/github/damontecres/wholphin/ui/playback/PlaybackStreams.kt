package com.github.damontecres.wholphin.ui.playback

import androidx.annotation.OptIn
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.preferences.enabled
import com.github.damontecres.wholphin.services.isReleased
import com.github.damontecres.wholphin.ui.gt
import com.github.damontecres.wholphin.ui.onMain
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.checkForSupport
import com.github.damontecres.wholphin.util.profile.Codec
import kotlinx.coroutines.delay
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

/** How the sound-switch check went */
enum class AudioSwitchCheck {
    /** The picked sound is the one playing (or it can't be told, so nothing is done) */
    FINE,

    /** The player is still on another sound track */
    WRONG_TRACK,
}

/**
 * Pure decision, so it can be unit tested.
 *
 * @param directPlay the TV plays the file itself (it has every sound track and picks one)
 * @param pickedTrackFound the player lists the picked sound track
 * @param pickedTrackOn the picked sound track is the one switched on
 * @param streamUrl the address being played, when the server sends a stream
 */
fun decideAudioSwitch(
    wanted: Int,
    directPlay: Boolean,
    pickedTrackFound: Boolean,
    pickedTrackOn: Boolean,
    streamUrl: String?,
): AudioSwitchCheck =
    if (directPlay) {
        if (pickedTrackFound && !pickedTrackOn) AudioSwitchCheck.WRONG_TRACK else AudioSwitchCheck.FINE
    } else {
        // A stream from the server has one sound track, named in its address
        val inUrl =
            streamUrl?.let { Regex("[?&]AudioStreamIndex=(-?\\d+)", RegexOption.IGNORE_CASE).find(it) }
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
        if (inUrl != null && inUrl != wanted) AudioSwitchCheck.WRONG_TRACK else AudioSwitchCheck.FINE
    }

/**
 * Benflix: after a sound track is picked, look at what the player is really playing. If it is
 * still on the old sound (Ben, 2026-11-21: the list showed the new one, but it only switched
 * when he picked it a second time), do that second pick for them: once more on the TV itself,
 * and if that doesn't take either, ask the server for a stream that has only the picked sound.
 */
@OptIn(UnstableApi::class)
internal suspend fun PlaybackViewModel.confirmAudioSwitch(wanted: Int) {
    for (attempt in 1..2) {
        // Wait for the player to have loaded and settled on its tracks
        var waited = 0L
        while (true) {
            delay(AUDIO_CHECK_POLL_MS)
            waited += AUDIO_CHECK_POLL_MS
            val settled =
                onMain {
                    !player.isReleased && player.playbackState == androidx.media3.common.Player.STATE_READY &&
                        player.currentTracks.groups.isNotEmpty()
                }
            if (settled && waited >= AUDIO_CHECK_SETTLE_MS) break
            if (waited >= AUDIO_CHECK_MAX_WAIT_MS) return
        }
        val playback = state.value.currentPlayback ?: return
        // Something else was picked or started meanwhile
        if (playback.item.id != currentItem.item.id || playback.audioIndex != wanted) return
        if (playback.backend != PlayerBackend.EXO_PLAYER) return
        val directPlay = playback.playMethod == org.jellyfin.sdk.model.api.PlayMethod.DIRECT_PLAY
        val group =
            if (directPlay) {
                onMain { TrackSelectionUtils.findAudioGroup(player.currentTracks, wanted, playback.mediaSourceInfo) }
            } else {
                null
            }
        val url =
            onMain {
                player.currentMediaItem
                    ?.localConfiguration
                    ?.uri
                    ?.toString()
            }
        val check =
            decideAudioSwitch(
                wanted = wanted,
                directPlay = directPlay,
                pickedTrackFound = group != null,
                pickedTrackOn = group?.isSelected == true,
                streamUrl = url,
            )
        if (check == AudioSwitchCheck.FINE) {
            Timber.d("Sound switch check ok: track=%s, directPlay=%s, attempt=%s", wanted, directPlay, attempt)
            return
        }
        Timber.w("Sound switch didn't take (track=%s, directPlay=%s, attempt=%s), doing it again", wanted, directPlay, attempt)
        val subtitleIndex =
            streamChoiceService.resolveSubtitleIndex(
                source = playback.mediaSourceInfo,
                audioStreamIndex = wanted,
                item = currentItem.item.data,
                subtitleIndex = playback.subtitleIndex,
                prefs = preferences,
            )
        changeStreams(
            item = currentItem.item,
            sourceId = playback.mediaSourceInfo.id,
            audioIndex = wanted,
            subtitleIndex = subtitleIndex,
            positionMs = onMain { player.currentPosition },
            // First the same way again (what picking it a second time does), then through the server
            enableDirectPlay = attempt == 1,
        )
    }
}

private const val AUDIO_CHECK_POLL_MS = 500L
private const val AUDIO_CHECK_SETTLE_MS = 2_000L
private const val AUDIO_CHECK_MAX_WAIT_MS = 20_000L

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
