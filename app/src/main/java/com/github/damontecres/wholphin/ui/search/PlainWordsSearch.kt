package com.github.damontecres.wholphin.ui.search

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.SlimItemFields
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.personsApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.time.LocalDate
import java.util.UUID

/**
 * Benflix "Search in plain words": turns things like "funny 90s movie under 2 hours" or
 * "anime with dragons" into the right filters (genre, decade, length, actor, kind of title).
 */
data class PlainWordsQuery(
    val genres: List<String> = emptyList(),
    val types: List<BaseItemKind> = emptyList(),
    val anime: Boolean = false,
    val years: IntRange? = null,
    val maxMinutes: Int? = null,
    val minMinutes: Int? = null,
    val newest: Boolean = false,
    /** Words left over: an actor's name, or a topic like "dragons" */
    val rest: List<String> = emptyList(),
    /** What was understood, in plain words, like "Comedy · 1990s · Movies · under 2 hours" */
    val label: String = "",
) {
    /** True when at least one filter was understood (so it is worth searching this way) */
    val understood: Boolean
        get() = genres.isNotEmpty() || types.isNotEmpty() || anime || years != null || maxMinutes != null || minMinutes != null || newest

    companion object {
        private val GENRE_WORDS =
            mapOf(
                "funny" to "Comedy",
                "comedy" to "Comedy",
                "comedies" to "Comedy",
                "hilarious" to "Comedy",
                "scary" to "Horror",
                "horror" to "Horror",
                "spooky" to "Horror",
                "romantic" to "Romance",
                "romance" to "Romance",
                "action" to "Action",
                "animated" to "Animation",
                "cartoon" to "Animation",
                "cartoons" to "Animation",
                "family" to "Family",
                "kids" to "Family",
                "scifi" to "Science Fiction",
                "sci-fi" to "Science Fiction",
                "space" to "Science Fiction",
                "fantasy" to "Fantasy",
                "magic" to "Fantasy",
                "mystery" to "Mystery",
                "detective" to "Mystery",
                "crime" to "Crime",
                "war" to "War",
                "western" to "Western",
                "westerns" to "Western",
                "documentary" to "Documentary",
                "documentaries" to "Documentary",
                "thriller" to "Thriller",
                "thrillers" to "Thriller",
                "suspense" to "Thriller",
                "drama" to "Drama",
                "sad" to "Drama",
                "adventure" to "Adventure",
                "musical" to "Music",
                "history" to "History",
                "historical" to "History",
            )

        private val MOVIE_WORDS = setOf("movie", "movies", "film", "films")
        private val SHOW_WORDS = setOf("show", "shows", "series", "tv")
        private val ANIME_WORDS = setOf("anime", "animes")
        private val NEW_WORDS = setOf("new", "newest", "recent", "latest")
        private val OLD_WORDS = setOf("old", "classic", "classics")
        private val FILLER =
            setOf(
                "a",
                "an",
                "the",
                "with",
                "about",
                "from",
                "in",
                "of",
                "and",
                "or",
                "some",
                "something",
                "me",
                "show me",
                "find",
                "i",
                "want",
                "to",
                "watch",
                "good",
                "best",
                "great",
                "that",
                "is",
                "are",
                "for",
                "like",
                "starring",
                "stars",
                "featuring",
                "by",
                "where",
                "has",
                "have",
                "any",
                "one",
                "please",
                "really",
                "very",
                "set",
                "on",
            )
        private val NUMBER_WORDS =
            mapOf("one" to 1, "two" to 2, "three" to 3, "an" to 1, "a" to 1)
        private val DECADE_WORDS =
            mapOf(
                "fifties" to 1950,
                "sixties" to 1960,
                "seventies" to 1970,
                "eighties" to 1980,
                "nineties" to 1990,
            )

        fun parse(text: String): PlainWordsQuery {
            val words =
                text
                    .lowercase()
                    .replace("’", "'")
                    .replace(Regex("[^a-z0-9' -]"), " ")
                    .split(Regex("\\s+"))
                    .filter { it.isNotBlank() }
                    .toMutableList()
            if (words.size < 2) return PlainWordsQuery()
            val genres = mutableListOf<String>()
            val types = mutableListOf<BaseItemKind>()
            var anime = false
            var years: IntRange? = null
            var maxMinutes: Int? = null
            var minMinutes: Int? = null
            var newest = false
            val labels = mutableListOf<String>()
            val rest = mutableListOf<String>()
            var i = 0
            while (i < words.size) {
                val w = words[i]
                val next = words.getOrNull(i + 1)
                // "under 2 hours", "less than 90 minutes", "over 2 hours"
                if (w == "under" || w == "less" || w == "shorter" || w == "over" || w == "longer" || w == "more") {
                    var j = i + 1
                    if (words.getOrNull(j) == "than") j++
                    val amount = words.getOrNull(j)?.let { it.toIntOrNull() ?: NUMBER_WORDS[it] }
                    val unit = words.getOrNull(j + 1)
                    if (amount != null && unit != null && (unit.startsWith("hour") || unit.startsWith("min"))) {
                        val minutes = if (unit.startsWith("hour")) amount * 60 else amount
                        if (w == "under" || w == "less" || w == "shorter") {
                            maxMinutes = minutes
                            labels += "under ${lengthWords(minutes)}"
                        } else {
                            minMinutes = minutes
                            labels += "over ${lengthWords(minutes)}"
                        }
                        i = j + 2
                        continue
                    }
                }
                if (w == "short") {
                    maxMinutes = 95
                    labels += "short"
                    i++
                    continue
                }
                if (w == "long") {
                    minMinutes = 150
                    labels += "long"
                    i++
                    continue
                }
                // "90s", "1990s", "'90s", "nineties", "2015"
                val decade =
                    Regex("^'?(\\d{2}|\\d{4})'?s$").find(w)?.groupValues?.get(1)?.toIntOrNull()?.let { n ->
                        when {
                            n >= 1900 -> n - n % 10
                            n >= 30 -> 1900 + n - n % 10
                            else -> 2000 + n - n % 10
                        }
                    } ?: DECADE_WORDS[w]
                if (decade != null) {
                    years = decade..(decade + 9)
                    labels += "${decade}s"
                    i++
                    continue
                }
                val year = w.toIntOrNull()?.takeIf { it in 1920..(LocalDate.now().year + 1) }
                if (year != null) {
                    years = year..year
                    labels += "$year"
                    i++
                    continue
                }
                if (w in NEW_WORDS) {
                    newest = true
                    val now = LocalDate.now().year
                    years = (now - 2)..now
                    labels += "new"
                    i++
                    continue
                }
                if (w in OLD_WORDS) {
                    years = 1920..1979
                    labels += "older"
                    i++
                    continue
                }
                if (w in ANIME_WORDS) {
                    anime = true
                    labels += "Anime"
                    i++
                    continue
                }
                if (w in MOVIE_WORDS) {
                    if (BaseItemKind.MOVIE !in types) types += BaseItemKind.MOVIE
                    i++
                    continue
                }
                if (w in SHOW_WORDS && !(w == "show" && next == "me")) {
                    if (BaseItemKind.SERIES !in types) types += BaseItemKind.SERIES
                    i++
                    continue
                }
                if (w == "sci" && next == "fi") {
                    genres += "Science Fiction"
                    i += 2
                    continue
                }
                val genre = GENRE_WORDS[w]
                if (genre != null) {
                    if (genre !in genres) genres += genre
                } else if (w !in FILLER) {
                    rest += w
                }
                i++
            }
            val typeLabel =
                when {
                    types.size == 1 && types[0] == BaseItemKind.MOVIE -> "Movies"
                    types.size == 1 -> "Shows"
                    else -> null
                }
            val label = (genres + listOfNotNull(typeLabel) + labels).joinToString(" · ")
            return PlainWordsQuery(genres, types, anime, years, maxMinutes, minMinutes, newest, rest, label)
        }

        private fun lengthWords(minutes: Int): String =
            when {
                minutes % 60 == 0 && minutes >= 60 -> if (minutes == 60) "1 hour" else "${minutes / 60} hours"
                else -> "$minutes minutes"
            }

        /** Runs [query] against the server; empty when nothing matches */
        suspend fun search(
            api: ApiClient,
            userId: UUID?,
            query: PlainWordsQuery,
        ): List<BaseItem> {
            if (!query.understood) return emptyList()
            return try {
                val types = query.types.ifEmpty { listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES) }
                // Anime: everything in the libraries with "anime" in their name
                val parents: List<UUID?> =
                    if (query.anime) {
                        api.userViewsApi
                            .getUserViews(userId = userId)
                            .content.items
                            .filter { it.name?.contains("anime", ignoreCase = true) == true }
                            .map { it.id }
                            .ifEmpty { return emptyList() }
                    } else {
                        listOf(null)
                    }
                // Left-over words: an actor ("with tom hanks") or a topic ("dragons")
                val personId =
                    if (query.rest.size >= 2) {
                        val name = query.rest.joinToString(" ")
                        api.personsApi
                            .getPersons(searchTerm = name, limit = 3)
                            .content.items
                            .firstOrNull { it.name?.equals(name, ignoreCase = true) == true }
                            ?.id
                    } else {
                        null
                    }
                val tags =
                    if (personId == null && query.rest.isNotEmpty()) {
                        query.rest.flatMap { listOf(it, it.removeSuffix("s"), it.replaceFirstChar(Char::titlecase)) }.distinct()
                    } else {
                        null
                    }
                val years = query.years?.toList()
                parents
                    .flatMap { parent ->
                        api.itemsApi
                            .getItems(
                                GetItemsRequest(
                                    userId = userId,
                                    parentId = parent,
                                    recursive = true,
                                    includeItemTypes = types,
                                    genres = query.genres.takeIf { it.isNotEmpty() },
                                    years = years,
                                    personIds = personId?.let { listOf(it) },
                                    tags = tags,
                                    sortBy =
                                        if (query.newest) {
                                            listOf(ItemSortBy.PREMIERE_DATE)
                                        } else {
                                            listOf(ItemSortBy.COMMUNITY_RATING, ItemSortBy.SORT_NAME)
                                        },
                                    sortOrder = listOf(SortOrder.DESCENDING, SortOrder.ASCENDING),
                                    fields = SlimItemFields,
                                    limit = LIMIT,
                                    enableTotalRecordCount = false,
                                ),
                            ).content.items
                    }.filter { dto ->
                        // Length is checked here (the server has no "shorter than" filter). Shows go by episode length,
                        // so the length words only make sense for movies.
                        val minutes = dto.runTimeTicks?.let { (it / 600_000_000L).toInt() }
                        val lengthOk =
                            (query.maxMinutes == null && query.minMinutes == null) ||
                                dto.type != BaseItemKind.MOVIE ||
                                (
                                    minutes != null &&
                                        (query.maxMinutes == null || minutes <= query.maxMinutes) &&
                                        (query.minMinutes == null || minutes >= query.minMinutes)
                                )
                        lengthOk
                    }.distinctBy { it.id }
                    .take(LIMIT)
                    .map { BaseItem(it, false) }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.w(ex, "Plain words search failed")
                emptyList()
            }
        }

        private const val LIMIT = 60
    }
}
