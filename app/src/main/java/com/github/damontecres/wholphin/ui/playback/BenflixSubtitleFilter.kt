package com.github.damontecres.wholphin.ui.playback

import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

/**
 * Benflix: hides non-English subtitle tracks from the subtitle menus (TV and phone).
 * Nothing is removed from the files; the setting "Only English subtitles in menus" turns it off.
 *
 * Always kept: English tracks, tracks with no language (often English with a missing label),
 * subtitles downloaded from the internet, and whatever track is playing right now.
 * A video with no English subtitles at all keeps every track, so it never ends up with none.
 */
object BenflixSubtitleFilter {
    @Volatile
    var englishOnly: Boolean = true

    private val ENGLISH = setOf("eng", "en", "english")
    private val UNKNOWN = setOf("", "und", "unk", "mis", "zxx")

    fun isEnglish(stream: MediaStream): Boolean {
        val lang = stream.language?.trim()?.lowercase() ?: return false
        return lang in ENGLISH || lang.startsWith("en-")
    }

    private fun isUnlabelled(stream: MediaStream): Boolean = (stream.language?.trim()?.lowercase() ?: "") in UNKNOWN

    /** Returns [streams] without the hidden subtitle tracks. Audio and video tracks are never touched. */
    fun visible(
        streams: List<MediaStream>,
        keepIndex: Int? = null,
    ): List<MediaStream> {
        if (!englishOnly) return streams
        val subtitles = streams.filter { it.type == MediaStreamType.SUBTITLE }
        if (subtitles.none(::isEnglish)) return streams
        return streams.filter {
            it.type != MediaStreamType.SUBTITLE ||
                isEnglish(it) ||
                isUnlabelled(it) ||
                it.isExternal ||
                it.index == keepIndex
        }
    }
}
