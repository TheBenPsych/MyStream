package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** A title worked out from something shared to Benflix */
data class SharedTitle(
    val title: String,
    val year: Int?,
)

/**
 * Benflix "Share a link to Benflix": a YouTube, TikTok, IMDb, Letterboxd (or any) link shared from
 * another app is turned into a movie or show name. If Benflix has it, its page opens (Play is right
 * there); if not, Search opens with that name, where "Ask for it" shows it.
 */
@Singleton
class ShareLookup
    @Inject
    constructor(
        @param:StandardOkHttpClient private val http: OkHttpClient,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        /** Where to go for shared [text] (and the sharing app's [subject], if it gave one) */
        suspend fun destinationFor(
            text: String?,
            subject: String?,
        ): Destination? =
            withContext(WholphinDispatchers.IO) {
                val shared = titleFrom(text, subject) ?: return@withContext null
                Timber.i("Shared title: %s (%s)", shared.title, shared.year)
                findOnBenflix(shared)?.destination() ?: Destination.Search(shared.title)
            }

        private fun titleFrom(
            text: String?,
            subject: String?,
        ): SharedTitle? {
            val url = text?.let { URL_REGEX.find(it)?.value }
            // Some apps send the name as the subject ("Watch "Dune: Part Two | Official Trailer" on YouTube")
            subject?.let { cleanTitle(it) }?.let { return it }
            if (url == null) return text?.let { cleanTitle(it) }
            val fromPage =
                when {
                    url.contains("youtube.com") || url.contains("youtu.be") -> {
                        oEmbedTitle("https://www.youtube.com/oembed?format=json&url=", url)
                    }

                    url.contains("tiktok.com") -> {
                        oEmbedTitle("https://www.tiktok.com/oembed?url=", url)
                    }

                    else -> {
                        pageTitle(url)
                    }
                }
            fromPage?.let { cleanTitle(it) }?.let { return it }
            // Letterboxd, TMDB and the like have the name in the link: /film/dune-part-two/
            slugTitle(url)?.let { return it }
            // Text around the link
            return text?.replace(url, " ")?.takeIf { it.isNotBlank() }?.let { cleanTitle(it) }
        }

        private fun oEmbedTitle(
            base: String,
            url: String,
        ): String? =
            try {
                val request = Request.Builder().url(base + java.net.URLEncoder.encode(url, "UTF-8")).build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return null
                    val obj = json.parseToJsonElement(response.body.string()) as? JsonObject
                    (obj?.get("title") as? JsonPrimitive)?.contentOrNull
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "oEmbed lookup failed for %s", url)
                null
            }

        /** The page's og:title (or <title>) */
        private fun pageTitle(url: String): String? =
            try {
                val parsed = url.toHttpUrlOrNull() ?: return null
                val request =
                    Request
                        .Builder()
                        .url(parsed)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) Benflix")
                        .header("Accept-Language", "en")
                        .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return null
                    // The head is enough
                    val head = response.body.source().let { src -> src.request(MAX_PAGE_BYTES); src.buffer.snapshot().utf8() }
                    OG_TITLE.find(head)?.groupValues?.get(1)
                        ?: TITLE_TAG.find(head)?.groupValues?.get(1)
                }?.let { decodeHtml(it) }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not read the shared page %s", url)
                null
            }

        private suspend fun findOnBenflix(shared: SharedTitle): BaseItem? =
            try {
                val wanted = simple(shared.title)
                api.itemsApi
                    .getItems(
                        GetItemsRequest(
                            userId = serverRepository.currentUser?.id,
                            searchTerm = shared.title,
                            recursive = true,
                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            fields = SlimItemFields,
                            limit = 10,
                        ),
                    ).content.items
                    .filter { dto -> listOfNotNull(dto.name, dto.originalTitle).any { simple(it) == wanted } }
                    .sortedBy { dto ->
                        val year = dto.productionYear
                        if (shared.year == null || year == null) 1 else kotlin.math.abs(year - shared.year)
                    }.firstOrNull { dto ->
                        shared.year == null || dto.productionYear == null || kotlin.math.abs(dto.productionYear!! - shared.year) <= 1
                    }?.let { BaseItem(it) }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Could not look up shared title %s", shared.title)
                null
            }

        companion object {
            private const val MAX_PAGE_BYTES = 200_000L
            private val URL_REGEX = Regex("https?://\\S+")
            private val OG_TITLE =
                Regex("<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            private val TITLE_TAG = Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE)
            private val YEAR = Regex("\\((19\\d{2}|20\\d{2})\\)|\\b(19\\d{2}|20\\d{2})\\b")

            /** Words that are about the video, not the movie */
            private val NOISE =
                Regex(
                    "\\b(official|final|teaser|trailers?|clip|scene|featurette|hd|4k|uhd|imax|red band|exclusive|" +
                        "first look|sneak peek|tv spot|full movie|netflix|prime video|hbo max|" +
                        "in theaters|now playing|on youtube|tiktok|imdb|letterboxd|rotten tomatoes|" +
                        "#\\d+|no\\.? ?\\d|\\d+ ?/ ?\\d+|season \\d+|part \\d+ of \\d+)\\b",
                    RegexOption.IGNORE_CASE,
                )

            internal fun cleanTitle(raw: String): SharedTitle? {
                var t =
                    raw
                        .replace(URL_REGEX, " ")
                        .replace(Regex("^watch\\s+[\"“]", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("[\"”]\\s+on\\s+youtube.*$", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("#\\w+"), " ")
                val year =
                    YEAR.find(t)?.let { m -> (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull() }
                // Keep the part before " | ", " - " or " — " (the rest is usually the channel or site)
                t = t.split(" | ", " - ", " — ", " – ", " · ").firstOrNull { it.isNotBlank() } ?: t
                t =
                    t
                        .replace(YEAR, " ")
                        .replace(NOISE, " ")
                        .replace(Regex("[\\[\\](){}|]"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .trim(':', '-', ',', '.', '!')
                        .trim()
                return t.takeIf { it.length >= 2 && it.length <= 80 }?.let { SharedTitle(it, year) }
            }

            internal fun slugTitle(url: String): SharedTitle? {
                val path = url.toHttpUrlOrNull()?.pathSegments ?: return null
                val slug =
                    path
                        .lastOrNull { seg -> seg.length > 2 && seg.any { it.isLetter() } && seg.contains('-') }
                        ?: return null
                val words = slug.replace(Regex("^\\d+-"), "").replace('-', ' ')
                return cleanTitle(words)
            }

            private fun simple(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

            private fun decodeHtml(s: String): String =
                s
                    .replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&#x27;", "'")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
        }
    }
