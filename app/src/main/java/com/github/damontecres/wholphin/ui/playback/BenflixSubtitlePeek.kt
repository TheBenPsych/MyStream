package com.github.damontecres.wholphin.ui.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Benflix: some anime files label the full English subtitles "Japanese" and mark the short
 * signs-and-songs English track as the default (Gantz, after Tower's track fixer moved the mark).
 * The names and marks can't tell them apart, so this looks inside: it asks Jellyfin for the words in
 * the "Japanese" track and in the English ones, and if the "Japanese" one is written in English letters
 * and has far more lines, it is the full English subtitles.
 *
 * Only runs for Japanese voices with a "Japanese" words-based subtitle track next to it, once per release
 * layout (later episodes with the same tracks reuse the answer), and gives up after a few seconds.
 */
object BenflixSubtitlePeek {
    private val JAPANESE = setOf("jpn", "ja", "jap", "japanese")
    private val ENGLISH = setOf("eng", "en", "english")
    private val UNKNOWN = setOf("", "und", "unk", "mis", "zxx")
    private val TEXT_CODECS = setOf("ass", "ssa", "subrip", "srt", "webvtt", "vtt", "mov_text", "text")

    /** Layouts already looked at (whatever the answer), so we never wait twice for the same kind of file */
    private val checked: MutableSet<String> =
        java.util.Collections.synchronizedSet(mutableSetOf())

    private fun lang(stream: MediaStream) =
        stream.language
            ?.trim()
            ?.lowercase()
            .orEmpty()

    private fun isText(stream: MediaStream) = !stream.isExternal && stream.codec?.lowercase() in TEXT_CODECS

    /** True when looking inside could change the pick */
    fun worthChecking(allStreams: List<MediaStream>): Boolean {
        if (allStreams.none { it.type == MediaStreamType.AUDIO && lang(it) in JAPANESE }) return false
        val subs = allStreams.filter { it.type == MediaStreamType.SUBTITLE }
        val japanese = subs.filter { lang(it) in JAPANESE && isText(it) }
        if (japanese.isEmpty()) return false
        if (japanese.any { BenflixSubtitleFilter.isEnglishLabelledJapanese(it, allStreams) }) return false
        return BenflixSubtitleFilter.layoutKey(allStreams) !in checked
    }

    /**
     * Looks inside the tracks. When a "Japanese" track turns out to be the full English subtitles,
     * it is remembered in [BenflixSubtitleFilter] (so menus call it "Full English subtitles") and returned.
     */
    suspend fun findFullEnglish(
        baseUrl: String?,
        token: String?,
        itemId: String,
        sourceId: String?,
        allStreams: List<MediaStream>,
    ): MediaStream? {
        if (baseUrl.isNullOrBlank() || token.isNullOrBlank() || sourceId.isNullOrBlank()) return null
        if (!worthChecking(allStreams)) return null
        val key = BenflixSubtitleFilter.layoutKey(allStreams)
        val subs = allStreams.filter { it.type == MediaStreamType.SUBTITLE && isText(it) }
        val japanese = subs.filter { lang(it) in JAPANESE }
        val english = subs.filter { lang(it) in ENGLISH || lang(it) in UNKNOWN || lang(it).startsWith("en-") }
        val result =
            withTimeoutOrNull(8_000) {
                coroutineScope {
                    val jp = japanese.map { s -> async { s to lines(baseUrl, token, itemId, sourceId, s.index) } }
                    val en = english.map { s -> async { lines(baseUrl, token, itemId, sourceId, s.index) } }
                    val jpLines = jp.awaitAll()
                    val enCount = en.awaitAll().maxOfOrNull { it?.size ?: 0 } ?: 0
                    jpLines
                        .filter { (_, words) ->
                            words != null && words.size >= 40 && words.size >= 2 * enCount &&
                                mostlyEnglishLetters(words)
                        }.maxByOrNull { (_, words) -> words!!.size }
                        ?.first
                }
            }
        checked.add(key)
        if (result != null) {
            Timber.i("Benflix: subtitle track %s is labelled Japanese but holds the full English subtitles", result.index)
            BenflixSubtitleFilter.rememberFullEnglish(allStreams, result.index)
        }
        return result
    }

    /** The different lines of words in one subtitle track (styling codes taken out), or null if Jellyfin didn't answer */
    private suspend fun lines(
        baseUrl: String,
        token: String,
        itemId: String,
        sourceId: String,
        index: Int,
    ): Set<String>? =
        withContext(Dispatchers.IO) {
            try {
                val url =
                    URL(
                        baseUrl.trimEnd('/') + "/Videos/" + itemId + "/" + URLEncoder.encode(sourceId, "UTF-8") +
                            "/Subtitles/" + index + "/0/Stream.js?api_key=" + URLEncoder.encode(token, "UTF-8"),
                    )
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3_000
                conn.readTimeout = 7_000
                try {
                    if (conn.responseCode !in 200..299) return@withContext null
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val events = JSONObject(body).optJSONArray("TrackEvents") ?: return@withContext null
                    buildSet {
                        for (i in 0 until events.length()) {
                            val text =
                                events
                                    .optJSONObject(i)
                                    ?.optString("Text")
                                    .orEmpty()
                                    .replace(Regex("\\{[^}]*\\}"), "")
                                    .replace("\\N", " ")
                                    .replace("\\n", " ")
                                    .trim()
                            if (text.length >= 2) add(text)
                        }
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (ex: Exception) {
                Timber.w(ex, "Benflix: could not look inside subtitle track %s", index)
                null
            }
        }

    private fun mostlyEnglishLetters(lines: Set<String>): Boolean {
        var latin = 0
        var japanese = 0
        lines.forEach { line ->
            line.forEach { c ->
                when {
                    c in 'a'..'z' || c in 'A'..'Z' -> latin++
                    c in '぀'..'ヿ' || c in '一'..'鿿' -> japanese++
                }
            }
        }
        return latin > 0 && japanese * 10 < latin
    }
}
