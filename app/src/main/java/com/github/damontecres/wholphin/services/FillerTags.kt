package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** What kind of episode this is, as animefillerlist.com sorts them */
enum class FillerKind(
    val label: String,
    val color: Color,
) {
    FILLER("FILLER", Color(0xFFB45309)),
    MIXED("MIXED", Color(0xFF6D5A8C)),
}

/**
 * Benflix: tags anime episodes as FILLER or MIXED (part story, part filler) on their cards.
 *
 * The list comes from animefillerlist.com, which counts episodes from 1 across the whole show.
 * Jellyfin often splits a show into seasons, so each episode's number across the whole show is
 * worked out from the seasons before it.
 */
object FillerTags {
    private val _tags = MutableStateFlow<Map<UUID, FillerKind>>(emptyMap())
    val tags: StateFlow<Map<UUID, FillerKind>> = _tags

    internal fun add(found: Map<UUID, FillerKind>) {
        if (found.isNotEmpty()) _tags.update { it + found }
    }
}

/** The FILLER / MIXED tag for this episode, or null */
@Composable
fun fillerTagFor(item: BaseItem?): FillerKind? {
    if (item?.type != BaseItemKind.EPISODE) return null
    return FillerTags.tags.collectAsState().value[item.id]
}

@Singleton
class FillerService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:StandardOkHttpClient private val http: OkHttpClient,
    ) {
        private val done = mutableSetOf<UUID>()

        private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /** Looks up filler episodes for this show (only anime), once per app run */
        suspend fun load(
            api: ApiClient,
            series: BaseItem,
            userId: UUID?,
        ) {
            if (!done.add(series.id)) return
            withContext(WholphinDispatchers.IO) {
                try {
                    if (!isAnime(api, series, userId)) return@withContext
                    val names = listOfNotNull(series.name, series.data.originalTitle).distinct()
                    val list = names.firstNotNullOfOrNull { listFor(slug(it)) } ?: return@withContext
                    if (list.isEmpty()) return@withContext
                    val episodes =
                        api.tvShowsApi
                            .getEpisodes(
                                GetEpisodesRequest(
                                    seriesId = series.id,
                                    userId = userId,
                                    enableImages = false,
                                    enableUserData = false,
                                ),
                            ).content
                            .items
                            .filter { (it.parentIndexNumber ?: 1) > 0 && it.indexNumber != null }
                    // Episodes before each season: the highest episode number in every earlier season
                    val lastInSeason =
                        episodes
                            .groupBy { it.parentIndexNumber ?: 1 }
                            .mapValues { (_, eps) -> eps.maxOf { it.indexNumber ?: 0 } }
                            .toSortedMap()
                    val before = mutableMapOf<Int, Int>()
                    var total = 0
                    lastInSeason.forEach { (season, last) ->
                        before[season] = total
                        total += last
                    }
                    val found =
                        episodes
                            .mapNotNull { ep ->
                                val first = (before[ep.parentIndexNumber ?: 1] ?: 0) + (ep.indexNumber ?: return@mapNotNull null)
                                val last = first + ((ep.indexNumberEnd ?: ep.indexNumber!!) - ep.indexNumber!!)
                                val kinds = (first..last).mapNotNull { list[it] }
                                when {
                                    kinds.isEmpty() -> null
                                    kinds.size == last - first + 1 && kinds.all { it == FillerKind.FILLER } -> ep.id to FillerKind.FILLER
                                    else -> ep.id to FillerKind.MIXED
                                }
                            }.toMap()
                    FillerTags.add(found)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    done.remove(series.id)
                    Timber.w(ex, "Could not look up filler episodes for %s", series.name)
                }
            }
        }

        private suspend fun isAnime(
            api: ApiClient,
            series: BaseItem,
            userId: UUID?,
        ): Boolean {
            val genres = series.data.genres.orEmpty()
            if (genres.any { it.equals("anime", ignoreCase = true) }) return true
            return try {
                api.libraryApi
                    .getAncestors(series.id, userId)
                    .content
                    .any { it.name?.contains("anime", ignoreCase = true) == true }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                false
            }
        }

        /** Episode number across the whole show -> its kind. Saved for a week; null if the site has no page */
        private fun listFor(slug: String): Map<Int, FillerKind>? {
            if (slug.isBlank()) return null
            val saved = prefs().getString(slug, null)
            val savedAt = prefs().getLong("$slug-at", 0L)
            if (saved != null && System.currentTimeMillis() - savedAt < WEEK_MS) {
                return if (saved == NONE) null else decode(saved)
            }
            val html =
                try {
                    http
                        .newCall(
                            Request
                                .Builder()
                                .url("https://www.animefillerlist.com/shows/$slug")
                                .header("User-Agent", "Mozilla/5.0 (Linux; Android) Benflix")
                                .build(),
                        ).execute()
                        .use { response ->
                            when {
                                response.isSuccessful -> response.body.string()
                                response.code == 404 -> ""
                                else -> return saved?.takeIf { it != NONE }?.let { decode(it) }
                            }
                        }
                } catch (ex: Exception) {
                    Timber.w(ex, "Filler list not reachable for %s", slug)
                    return saved?.takeIf { it != NONE }?.let { decode(it) }
                }
            val list = parse(html)
            prefs().edit {
                putString(slug, if (list.isEmpty()) NONE else encode(list))
                putLong("$slug-at", System.currentTimeMillis())
            }
            return list.takeIf { it.isNotEmpty() }
        }

        private fun encode(list: Map<Int, FillerKind>) = list.entries.joinToString(",") { "${it.key}:${it.value.name.first()}" }

        private fun decode(saved: String): Map<Int, FillerKind> =
            saved
                .split(",")
                .mapNotNull { part ->
                    val (num, kind) = part.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
                    val k = if (kind == "F") FillerKind.FILLER else FillerKind.MIXED
                    num.toIntOrNull()?.let { it to k }
                }.toMap()

        companion object {
            private const val PREFS = "benflix_filler"
            private const val NONE = "-"
            private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

            /** "Naruto Shippūden" -> "naruto-shippuden", the way the site names its pages */
            fun slug(name: String): String =
                java.text.Normalizer
                    .normalize(name, java.text.Normalizer.Form.NFD)
                    .replace(Regex("\\p{M}+"), "")
                    .lowercase()
                    .replace("'", "")
                    .replace("&", "and")
                    .replace(Regex("[^a-z0-9]+"), "-")
                    .trim('-')

            /**
             * Reads the "Quick List" at the top of a show's page, e.g.
             * "Filler Episodes: 26, 97, 101-106" and "Mixed Canon/Filler Episodes: 7, 9".
             */
            fun parse(html: String): Map<Int, FillerKind> {
                if (html.isBlank()) return emptyMap()
                val text =
                    html
                        .replace(Regex("<[^>]+>"), " ")
                        .replace("&nbsp;", " ")
                        .replace(Regex("\\s+"), " ")
                val out = mutableMapOf<Int, FillerKind>()
                val label = Regex("(Manga Canon|Anime Canon|Mixed Canon/Filler|Filler) Episodes:", RegexOption.IGNORE_CASE)
                val marks = label.findAll(text).toList()
                marks.forEachIndexed { i, mark ->
                    val kind =
                        when (mark.groupValues[1].lowercase()) {
                            "filler" -> FillerKind.FILLER
                            "mixed canon/filler" -> FillerKind.MIXED
                            else -> null
                        } ?: return@forEachIndexed
                    val end = marks.getOrNull(i + 1)?.range?.first ?: minOf(text.length, mark.range.last + 4000)
                    val body = text.substring(mark.range.last + 1, end)
                    // Stop at the first thing that isn't a list of numbers
                    val numbers = Regex("^[\\s0-9,\\-–]+").find(body)?.value.orEmpty()
                    Regex("(\\d+)\\s*[-–]\\s*(\\d+)|(\\d+)").findAll(numbers).forEach { m ->
                        val from = (m.groupValues[1].ifEmpty { m.groupValues[3] }).toIntOrNull() ?: return@forEach
                        val to = m.groupValues[2].toIntOrNull() ?: from
                        if (to - from in 0..3000) (from..to).forEach { out[it] = kind }
                    }
                }
                return out
            }
        }
    }
