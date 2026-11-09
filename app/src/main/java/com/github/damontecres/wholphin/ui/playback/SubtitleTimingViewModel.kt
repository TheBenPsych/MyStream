package com.github.damontecres.wholphin.ui.playback

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.PlaylistItem
import com.github.damontecres.wholphin.services.GameLibraryClient
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.onMain
import com.github.damontecres.wholphin.ui.showToast
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Benflix: "Subtitles are early or late" in the subtitles menu. Tower (the Benflix helper) listens to the
 * voices in the video and moves the subtitles so they line up, then the player picks them up again.
 */
@HiltViewModel
class SubtitleTimingViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val helper: GameLibraryClient,
    ) : ViewModel() {
        private var job: Job? = null

        fun fix(
            itemId: UUID,
            subtitleIndex: Int,
            onFixed: () -> Unit,
        ) {
            if (job?.isActive == true) {
                viewModelScope.launch { showToast(context, "Tower is already lining these subtitles up.", Toast.LENGTH_SHORT) }
                return
            }
            job =
                viewModelScope.launch {
                    val id = itemId.toString().replace("-", "")
                    try {
                        var answer = helper.postJson("/api/subsync/$id", JSONObject().put("index", subtitleIndex))
                        showToast(context, answer.optString("message", "Tower is lining the subtitles up."), Toast.LENGTH_LONG)
                        repeat(90) {
                            val state = answer.optString("state")
                            if (state == "done" || state == "failed") return@repeat
                            delay(4_000)
                            answer =
                                try {
                                    helper.getJson("/api/subsync/$id")
                                } catch (ex: CancellationException) {
                                    throw ex
                                } catch (ex: Exception) {
                                    answer
                                }
                        }
                        showToast(context, answer.optString("message", "Done."), Toast.LENGTH_LONG)
                        if (answer.optString("state") == "done" && answer.optBoolean("changed")) onFixed()
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.w(ex, "Subtitle timing fix")
                        showToast(context, ex.message ?: "Couldn't reach Tower to fix the subtitles.", Toast.LENGTH_LONG)
                    }
                }
        }
    }

/**
 * After Tower fixed the subtitles: load the item again. Subtitles that were inside the video come back as a
 * new subtitle file, so switch to that one; a fixed subtitle file is picked again so the player reads it fresh.
 */
fun PlaybackViewModel.afterSubtitleFix() {
    viewModelScope.launchIO {
        try {
            val currentPlayback = state.value.currentPlayback ?: return@launchIO
            val before =
                currentPlayback.mediaSourceInfo.mediaStreams
                    ?.filter { it.type == MediaStreamType.SUBTITLE }
                    .orEmpty()
            val oldPaths = before.map { it.path }
            val oldLanguage = before.firstOrNull { it.index == currentPlayback.subtitleIndex }?.language
            val base = BaseItem(api.userLibraryApi.getItem(itemId = currentPlayback.itemId).content)
            currentItem =
                when (currentItem) {
                    is PlaylistItem.Intro -> PlaylistItem.Intro(base)
                    is PlaylistItem.Media -> PlaylistItem.Media(base)
                }
            val mediaSource =
                base.data.mediaSources?.firstOrNull { it.id?.toUUIDOrNull() == currentPlayback.sourceId }
                    ?: base.data.mediaSources?.firstOrNull()
                    ?: return@launchIO
            val subs = mediaSource.mediaStreams?.filter { it.type == MediaStreamType.SUBTITLE }.orEmpty()
            val fresh =
                subs.lastOrNull { it.isExternal && it.path !in oldPaths && it.language == oldLanguage }
                    ?: subs.lastOrNull { it.isExternal && it.path !in oldPaths }
            var audioIndex = currentPlayback.audioIndex
            val subtitleIndex =
                if (fresh != null) {
                    if (audioIndex >= 0 && audioIndex >= fresh.index && subs.size > before.size) audioIndex += 1
                    saveTrackSelection(fresh.index, MediaStreamType.SUBTITLE)
                    fresh.index
                } else {
                    currentPlayback.subtitleIndex
                }
            updateCurrentMedia {
                it.copy(
                    sourceId = mediaSource.id,
                    audioStreams = getAudioStreams(mediaSource),
                    subtitleStreams = getSubtitleStreams(mediaSource),
                )
            }
            updateCurrentPlayback {
                it?.copy(
                    item = currentItem.item,
                    mediaSourceInfo = mediaSource,
                    audioIndex = audioIndex,
                    subtitleIndex = subtitleIndex,
                )
            }
            changeStreams(
                item = currentItem.item,
                sourceId = currentPlayback.mediaSourceInfo.id,
                audioIndex = audioIndex,
                subtitleIndex = subtitleIndex,
                positionMs = onMain { player.currentPosition },
                enableDirectPlay = true,
            )
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Timber.w(ex, "Reloading subtitles after Tower fixed them")
        }
    }
}
