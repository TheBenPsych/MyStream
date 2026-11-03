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
import org.jellyfin.sdk.model.api.BaseItemDto
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

/**
 * Benflix: each anime episode's number counted from the very first episode, like fans count
 * long shows (Naruto Shippuden episode 135 instead of S7 E5). Only for shows with more than one season.
 */
object AnimeEpisodeNumbers {
    private val _numbers = MutableStateFlow<Map<UUID, Int>>(emptyMap())
    val numbers: StateFlow<Map<UUID, Int>> = _numbers

    internal fun add(found: Map<UUID, Int>) {
        if (found.isNotEmpty()) _numbers.update { it + found }
    }

    /** Season 1 starts at 1, each later season after the highest episode number of the one before */
    fun workOut(episodes: List<BaseItemDto>): Map<UUID, Int> {
        val seasons = episodes.filter { it.indexNumber != null }.groupBy { it.parentIndexNumber ?: 1 }.toSortedMap()
        if (seasons.size < 2) return emptyMap()
        val out = mutableMapOf<UUID, Int>()
        var before = 0
        seasons.values.forEach { eps ->
            // Some libraries already number later seasons on from earlier ones
            val offset = if ((eps.minOf { it.indexNumber!! }) > before) 0 else before
            eps.forEach { out[it.id] = it.indexNumber!! + offset }
            before = maxOf(before, eps.maxOf { (it.indexNumberEnd ?: it.indexNumber!!) + offset })
        }
        return out
    }

    /** "Episode 135" for an anime episode whose number across the show is known, else null */
    fun label(item: BaseItem?): String? {
        if (item?.type != BaseItemKind.EPISODE) return null
        val number = _numbers.value[item.id] ?: return null
        if (number == item.indexNumber) return null
        return "Episode $number"
    }
}

/** "Episode 135" for this anime episode (number across the whole show), or null */
@Composable
fun animeEpisodeLabel(item: BaseItem?): String? {
    if (item?.type != BaseItemKind.EPISODE) return null
    AnimeEpisodeNumbers.numbers.collectAsState().value
    return AnimeEpisodeNumbers.label(item)
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
        // Benflix: touched from the screen and from background threads, so it must be thread-safe
        private val done: MutableSet<UUID> = java.util.Collections.synchronizedSet(mutableSetOf())

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
                    // Benflix: anime episodes also show their number across the whole show (Episode 135)
                    AnimeEpisodeNumbers.add(AnimeEpisodeNumbers.workOut(episodes))
                    val names = listOfNotNull(series.name, series.data.originalTitle).distinct()
                    val show = names.firstNotNullOfOrNull { showFor(slug(it)) } ?: return@withContext
                    if (show.kinds.isEmpty()) return@withContext
                    val numbers = siteNumbers(episodes, show.titles)
                    val found =
                        episodes
                            .mapNotNull { ep ->
                                val first = numbers[ep.id] ?: return@mapNotNull null
                                val last = first + ((ep.indexNumberEnd ?: ep.indexNumber!!) - ep.indexNumber!!)
                                val kinds = (first..last).mapNotNull { show.kinds[it] }
                                when {
                                    kinds.isEmpty() -> null
                                    kinds.size == last - first + 1 && kinds.all { it == FillerKind.FILLER } -> ep.id to FillerKind.FILLER
                                    else -> ep.id to FillerKind.MIXED
                                }
                            }.toMap()
                    Timber.i(
                        "Filler tags for %s: %s of %s episodes matched by title, %s tagged",
                        series.name,
                        numbers.matchedByTitle,
                        episodes.size,
                        found.size,
                    )
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

        /** The site's lists for one show: episode number -> kind, and episode number -> title */
        class Show(
            val kinds: Map<Int, FillerKind>,
            val titles: Map<Int, String>,
        )

        /** Each episode's number on the site, plus how many were matched by their title */
        class SiteNumbers(
            private val map: Map<UUID, Int>,
            val matchedByTitle: Int,
        ) {
            operator fun get(id: UUID) = map[id]
        }

        /**
         * Works out each episode's number on the site (which counts from 1 across the whole show).
         *
         * 1. An episode whose title matches exactly one title on the site gets that number. This
         *    still works when episodes are missing from the library or seasons are split differently.
         * 2. Other episodes use their season's most common gap between Jellyfin's number and the site's.
         * 3. Seasons with no title matches fall back to adding up the seasons before them.
         */
        private fun siteNumbers(
            episodes: List<BaseItemDto>,
            titles: Map<Int, String>,
        ): SiteNumbers {
            val byTitle =
                titles.entries
                    .groupBy({ cleanTitle(it.value) }, { it.key })
                    .filter { (title, nums) -> title.length >= MIN_TITLE && nums.size == 1 }
                    .mapValues { it.value.first() }
            val matched =
                episodes
                    .mapNotNull { ep -> byTitle[cleanTitle(ep.name.orEmpty())]?.let { ep.id to it } }
                    .toMap()
            val seasons = episodes.groupBy { it.parentIndexNumber ?: 1 }.toSortedMap()
            val out = mutableMapOf<UUID, Int>()
            var before = 0
            seasons.forEach { (_, eps) ->
                val offset =
                    eps
                        .mapNotNull { ep -> matched[ep.id]?.let { it - ep.indexNumber!! } }
                        .groupingBy { it }
                        .eachCount()
                        .maxByOrNull { it.value }
                        ?.key ?: before
                eps.forEach { ep -> out[ep.id] = matched[ep.id] ?: (ep.indexNumber!! + offset) }
                // The next season starts after this one's last episode on the site
                before = maxOf(before + eps.maxOf { it.indexNumber ?: 0 }, eps.maxOf { out[it.id] ?: 0 })
            }
            return SiteNumbers(out, matched.size)
        }

        /** Saved for a week; null if the site has no page for this show */
        private fun showFor(slug: String): Show? {
            if (slug.isBlank()) return null
            val saved = prefs().getString(slug, null)
            val savedTitles = prefs().getString("$slug-titles", "").orEmpty()
            val savedAt = prefs().getLong("$slug-at", 0L)

            fun savedShow() = saved?.takeIf { it != NONE }?.let { Show(decode(it), decodeTitles(savedTitles)) }
            if (saved != null && System.currentTimeMillis() - savedAt < WEEK_MS) {
                return savedShow()
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
                                else -> return savedShow()
                            }
                        }
                } catch (ex: Exception) {
                    Timber.w(ex, "Filler list not reachable for %s", slug)
                    return savedShow()
                }
            val kinds = parse(html)
            val titles = parseTitles(html)
            prefs().edit {
                putString(slug, if (kinds.isEmpty()) NONE else encode(kinds))
                putString("$slug-titles", encodeTitles(titles))
                putLong("$slug-at", System.currentTimeMillis())
            }
            return Show(kinds, titles).takeIf { kinds.isNotEmpty() }
        }

        private fun encodeTitles(titles: Map<Int, String>) =
            titles.entries.joinToString("\n") { "${it.key}\t${it.value.replace('\n', ' ').replace('\t', ' ')}" }

        private fun decodeTitles(saved: String): Map<Int, String> =
            saved
                .split("\n")
                .mapNotNull { line ->
                    val tab = line.indexOf('\t')
                    if (tab <= 0) return@mapNotNull null
                    line.substring(0, tab).toIntOrNull()?.let { it to line.substring(tab + 1) }
                }.toMap()

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
            // "2": saved lists now also keep episode titles, so older saves are fetched again
            private const val PREFS = "benflix_filler2"
            private const val MIN_TITLE = 4
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

            /** "The Fourth Hokage's Death Wish" -> "fourthhokagesdeathwish", for comparing titles */
            fun cleanTitle(title: String): String =
                java.text.Normalizer
                    .normalize(title, java.text.Normalizer.Form.NFD)
                    .replace(Regex("\\p{M}+"), "")
                    .lowercase()
                    .replace("&", "and")
                    .replace(Regex("\\b(the|a|an)\\b"), "")
                    .replace(Regex("[^a-z0-9]+"), "")

            /**
             * Reads the episode table on a show's page: each row starts with the episode number,
             * then its title.
             */
            fun parseTitles(html: String): Map<Int, String> {
                if (html.isBlank()) return emptyMap()
                val out = mutableMapOf<Int, String>()
                val row = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                val cell = Regex("<td[^>]*>(.*?)</td>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                row.findAll(html).forEach { r ->
                    val cells =
                        cell
                            .findAll(r.groupValues[1])
                            .map { c ->
                                c.groupValues[1]
                                    .replace(Regex("<[^>]+>"), " ")
                                    .replace("&amp;", "&")
                                    .replace("&#039;", "'")
                                    .replace("&quot;", "\"")
                                    .replace("&nbsp;", " ")
                                    .replace(Regex("\\s+"), " ")
                                    .trim()
                            }.toList()
                    val number = cells.getOrNull(0)?.toIntOrNull() ?: return@forEach
                    val title = cells.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@forEach
                    out[number] = title
                }
                return out
            }

            /**
             * Reads the "Quick List" at the top of a show's page, e.g.
             * "Filler Episodes: 26, 97, 101-106" and "Mixed Canon/Filler Episodes: 7, 9".
             */
            fun parse(html: String): Map<Int, FillerKind> {
                if (html.isBlank()) return emptyMap()
                // The quick lists come before the episode table, so the table's numbers can't leak in
                val text =
                    html
                        .substringBefore("<table")
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
