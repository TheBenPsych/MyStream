package com.github.damontecres.wholphin.ui.playback

import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

/**
 * Benflix: hides subtitle tracks nobody here can read from the subtitle menus (TV and phone).
 * Nothing is removed from the files; the setting "Only English subtitles in menus" turns it off.
 *
 * Always kept: English tracks, tracks with no language (often English with a missing label),
 * tracks in the same language as one of the video's sound tracks (anime releases often label the full
 * English subtitles "Japanese" because they go with the Japanese voices), subtitles downloaded from the
 * internet, and whatever track is playing right now.
 * A video with no English subtitles at all keeps every track, so it never ends up with none.
 */
object BenflixSubtitleFilter {
    @Volatile
    var englishOnly: Boolean = true

    private val ENGLISH = setOf("eng", "en", "english")
    private val JAPANESE = setOf("jpn", "ja", "jap", "japanese")
    private val UNKNOWN = setOf("", "und", "unk", "mis", "zxx")

    /** Subtitle formats that hold words (not pictures), the kind anime groups use for their translations */
    private val TEXT_CODECS = setOf("ass", "ssa", "subrip", "srt", "webvtt", "vtt", "mov_text", "text")

    /** Words in a track name that mean it is only signs and songs, not the full subtitles */
    private val SIGNS_WORDS = listOf("sign", "song", "s&s", "forced", "karaoke", "lyrics", "op/ed")

    /** Words in a track name that mean it is the full subtitles */
    private val FULL_WORDS = listOf("full", "dialog", "complete")

    /** Words in a track name that mean it really is in Japanese */
    private val REALLY_JAPANESE = listOf("japanese", "日本", "kanji", "jpn only", "jp only")

    private fun lang(stream: MediaStream?): String =
        stream
            ?.language
            ?.trim()
            ?.lowercase()
            .orEmpty()

    private fun name(stream: MediaStream) = listOfNotNull(stream.title, stream.displayTitle).joinToString(" ").lowercase()

    fun isEnglish(stream: MediaStream): Boolean {
        val lang = lang(stream)
        return lang in ENGLISH || lang.startsWith("en-")
    }

    private fun isUnlabelled(stream: MediaStream): Boolean = lang(stream) in UNKNOWN

    /** The same language, whether it is written "ja", "jpn" or "japanese" */
    private fun sameLanguage(
        a: String,
        b: String,
    ): Boolean = a.isNotEmpty() && (a == b || (a in JAPANESE && b in JAPANESE) || (a in ENGLISH && b in ENGLISH))

    fun isSignsAndSongs(stream: MediaStream): Boolean = stream.isForced || SIGNS_WORDS.any { name(stream).contains(it) }

    private fun saysFull(stream: MediaStream): Boolean = FULL_WORDS.any { name(stream).contains(it) }

    /**
     * True for a subtitle track that is labelled Japanese but is really the full English subtitles.
     * Anime groups often mark their translation "Japanese" because it goes with the Japanese voices
     * (Gantz: "Japanese - Default - ASS" is all the English dialogue, "English - ASS" is only signs and songs).
     * We take it as English when the video has Japanese voices, it is a words-based format, and the
     * group marked it as the default track or named it as the English/full one.
     */
    fun isEnglishLabelledJapanese(
        stream: MediaStream,
        allStreams: List<MediaStream>,
    ): Boolean {
        if (stream.type != MediaStreamType.SUBTITLE || stream.isExternal) return false
        if (lang(stream) !in JAPANESE) return false
        if (stream.codec?.lowercase() !in TEXT_CODECS) return false
        if (allStreams.none { it.type == MediaStreamType.AUDIO && lang(it) in JAPANESE }) return false
        val n = name(stream)
        val title = stream.title?.lowercase().orEmpty()
        if (REALLY_JAPANESE.any { title.contains(it) } || isSignsAndSongs(stream)) return false
        return stream.isDefault || saysFull(stream) || n.contains("english") || Regex("\\beng\\b").containsMatchIn(title)
    }

    /**
     * A friendly name for a subtitle track when the file's own name is confusing, or null to keep the file's name.
     * "Full English subtitles" for a mislabelled Japanese track, "Signs & songs" for the short English one.
     */
    fun friendlyName(
        stream: MediaStream,
        allStreams: List<MediaStream>,
    ): String? {
        if (stream.type != MediaStreamType.SUBTITLE) return null
        if (isEnglishLabelledJapanese(stream, allStreams)) return "Full English subtitles"
        if (!isEnglish(stream) && !isUnlabelled(stream)) return null
        if (isSignsAndSongs(stream)) return "Signs & songs"
        // Next to a mislabelled full track, a plain English track in the same file is the signs and songs one
        if (!saysFull(stream) && allStreams.any { it.index != stream.index && isEnglishLabelledJapanese(it, allStreams) }) {
            return "Signs & songs"
        }
        return null
    }

    /** Returns [streams] without the hidden subtitle tracks. Audio and video tracks are never touched. */
    fun visible(
        streams: List<MediaStream>,
        keepIndex: Int? = null,
    ): List<MediaStream> {
        if (!englishOnly) return streams
        val subtitles = streams.filter { it.type == MediaStreamType.SUBTITLE }
        if (subtitles.none(::isEnglish)) return streams
        val soundLanguages = streams.filter { it.type == MediaStreamType.AUDIO }.map(::lang).filter { it !in UNKNOWN }
        return streams.filter { stream ->
            stream.type != MediaStreamType.SUBTITLE ||
                isEnglish(stream) ||
                isUnlabelled(stream) ||
                stream.isExternal ||
                stream.index == keepIndex ||
                soundLanguages.any { sameLanguage(lang(stream), it) }
        }
    }
}
