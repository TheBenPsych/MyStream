package com.github.damontecres.wholphin.ui.components

import com.github.damontecres.wholphin.services.BenflixAnimeTracks
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStreamType

/** Benflix: whether an anime has the original Japanese with subtitles, an English dub, or both */
object SubDub {
    private val ENGLISH = setOf("eng", "en", "english")

    /** "SUB", "DUB" or "SUB | DUB" for an anime movie or episode (with its media streams), else null */
    fun label(item: BaseItemDto?): String? {
        val streams = item?.mediaStreams.orEmpty()
        val audio = streams.filter { it.type == MediaStreamType.AUDIO }
        if (audio.isEmpty()) return null
        val sub =
            audio.any { BenflixAnimeTracks.isJapanese(it.language) } &&
                streams.any { it.type == MediaStreamType.SUBTITLE }
        val dub = audio.any { it.language?.lowercase() in ENGLISH }
        return when {
            sub && dub -> "SUB | DUB"
            dub -> "DUB"
            sub -> "SUB"
            else -> null
        }
    }
}
