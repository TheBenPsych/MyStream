package com.github.damontecres.wholphin.ui.playback

import android.content.res.Resources
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.util.StreamFormatting.mediaStreamDisplayTitle
import org.jellyfin.sdk.model.api.MediaStream

/**
 * A slimmer [MediaStream] with minimal data for UI purposes
 *
 * @see SimpleVideoStream
 */
data class SimpleMediaStream(
    val index: Int,
    val streamTitle: String?,
    val displayTitle: String,
    /** The file format, like "subrip", "pgssub" or "ass" (Benflix: styled ASS/SSA subtitles are never moved) */
    val codec: String? = null,
    /** Benflix: a "Signs & songs" / forced subtitle track, left out of the subtitle list unless it is playing */
    val hiddenSigns: Boolean = false,
) {
    companion object {
        fun from(
            resources: Resources,
            mediaStream: MediaStream,
            includeFlags: Boolean = true,
            /** Benflix: every track in the video, so a mislabelled subtitle track can get a clear name */
            allStreams: List<MediaStream>? = null,
        ): SimpleMediaStream =
            SimpleMediaStream(
                index = mediaStream.index,
                streamTitle =
                    allStreams?.let { BenflixSubtitleFilter.friendlyName(mediaStream, it) }
                        ?: mediaStream.title?.takeIf { it.isNotNullOrBlank() },
                displayTitle = mediaStreamDisplayTitle(resources, mediaStream, includeFlags),
                codec = mediaStream.codec?.lowercase(),
                hiddenSigns = allStreams?.let { BenflixSubtitleFilter.isHiddenSigns(mediaStream, it) } ?: false,
            )
    }
}

data class SimpleVideoStream(
    val index: Int,
    val hdr: Boolean,
    val is4k: Boolean,
)
